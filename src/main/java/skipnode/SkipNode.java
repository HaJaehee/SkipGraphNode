package skipnode;

/* -------------------------------------------------------- */
/**
 File name : SkipNode.java
 Rev. history : 2021-03-17
 Version : 0.0.2
 Added a logger.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-03-19
 Version : 1.0.0
 Added Jedis features as a key-value storage system.
 Added storage path features as a file storage system.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-03-22
 Version : 1.0.1
 Modified Jedis features as a key-value storage system.
 Added  Added getResourceByNumID(), getResourceByNameID(), storeResourceByNumID(), and storeResourceByNameID().
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 //TODO get a set by name ID prefix
 //TODO store resource by num ID or resource key
 //TODO remove get resource by name ID
 //TODO replicate resource into nodes having common name ID prefix
 */
/* -------------------------------------------------------- */


import lookup.LookupTable;
import middlelayer.MiddleLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class SkipNode implements SkipNodeInterface {
    /**
     * Attributes
     */
    private final String address;
    private final int port;
    private final BigInteger numID;
    private final String nameID;
    private final LookupTable lookupTable;
    private final String storagePath;
    private final boolean isUsingRedis;
    private final String redisPoolConfig;
    private final String redisAddress;
    private final int redisPort;
    private final String redisPassword;
    private final int redisTimeout;
    private String resourceQueryResult;
    private JedisPool jedisPool;

    private MiddleLayer middleLayer;

    private boolean inserted = false;
    private final InsertionLock insertionLock = new InsertionLock();
    private final LinkedBlockingDeque<InsertionLock.NeighborInstance> ownedLocks = new LinkedBlockingDeque<>();
    // Incremented after each lookup table update.
    private int version = 0;

    // The identity to be returned in case the node is currently unreachable (i.e., being inserted.)
    private static final SkipNodeIdentity unavailableIdentity = LookupTable.EMPTY_NODE;

    private static final Logger logger = LoggerFactory.getLogger(SkipNode.class);

    public SkipNode(SkipNodeIdentity snID, LookupTable lookupTable) {
        this.address = snID.getAddress();
        this.port = snID.getPort();
        this.numID = snID.getNumID();
        this.nameID = snID.getNameID();
        this.lookupTable = lookupTable;
        this.storagePath = snID.getStoragePath();
        this.isUsingRedis = snID.isUsingRedis();
        this.redisPoolConfig = snID.getRedisPoolConfig();
        this.redisAddress = snID.getRedisAddress();
        this.redisPort = snID.getRedisPort();
        this.redisTimeout = snID.getRedisTimeout();
        this.redisPassword = snID.getRedisPassword();
        this.resourceQueryResult = snID.getResourceQueryResult();
        this.jedisPool = null;
        insertionLock.startInsertion();
    }

    public BigInteger getNumID() {
        return numID;
    }

    public String getNameID() { return nameID; }

    public LookupTable getLookupTable() {
        return lookupTable;
    }

    public String getStoragePath() { return storagePath; }

    public void setJedisPool() {
        if (jedisPool == null && this.redisAddress != null) {
            JedisFactoryTest jft = new JedisFactoryTest(this.redisPoolConfig, this.redisAddress, this.redisPort, this.redisTimeout, this.redisPassword, false);
            jedisPool = jft.getJedisPoolInstance();
        }
    }

    private void setResourceValueByKey(String resourceKey, String resourceQueryResult) {
        this.resourceQueryResult = resourceQueryResult;
    }

    //TODO: skip node identity는 serializable 해야 한다.
    //TODO: identity에 redis get 값을 넣으면 되는 거 아닌가?
    //TODO: 그럼 member variable에는 redis에 접속할 수 있는 정보를 넣으면 되는건가?
    public SkipNodeIdentity getIdentity() { return new SkipNodeIdentity(nameID, numID, address, port, version, storagePath, isUsingRedis, resourceQueryResult);
    }

    @Override
    public void setMiddleLayer(MiddleLayer middleLayer) {
        this.middleLayer = middleLayer;
    }

    /**
     * Inserts this SkipNode to the skip graph of the introducer.
     * @param introducerAddress the address of the introducer.
     * @param introducerPort the port of the introducer.
     */
    @Override
    public void insert(String introducerAddress, int introducerPort) {
        
        // Do not reinsert an already inserted node.
        if(inserted) return;
        // Trivially insert the first node of the skip graph.
        if(introducerAddress == null) {
            logger.debug(getNumID() + " was inserted!");
            inserted = true;
            insertionLock.endInsertion();
            return;
        }
        // Try to acquire the locks from all of my neighbors.
        while(true) {
            SkipNodeIdentity left = null;
            SkipNodeIdentity right = null;
            logger.debug(getNumID() + " searches for its 0-level neighbors...");
            // First, find my 0-level neighbor by making a num-id search through the introducer.
            SkipNodeIdentity searchResult = middleLayer.searchByNumID(introducerAddress, introducerPort, numID);
            // Get my 0-level left and right neighbors.
            //getNumID() < searchResult.getNumID()
            if(getNumID().compareTo(searchResult.getNumID()) == -1) {
                right = searchResult;
                left = middleLayer.getLeftNode(right.getAddress(), right.getPort(), 0);
            } else {
                left = searchResult;
                right = middleLayer.getRightNode(left.getAddress(), left.getPort(), 0);
            }
            logger.debug(getNumID() + " found its 0-level neighbors: " + left.getNumID() + ", " + right.getNumID());
            if(acquireNeighborLocks(left, right)) break;
            // When we fail, backoff for a random interval before trying again.
            logger.debug(getNumID() + " could not acquire the locks. Backing off...");
            int sleepTime = (int)(Math.random() * 2000);
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                logger.error("[SkipNode.insert] Could not backoff.");
                e.printStackTrace();
            }
        }
        logger.debug(getNumID() + " has acquired all the locks: ");
        ownedLocks.forEach(n -> logger.debug(n.node.getNumID() + ", "));
        logger.debug("");
        // At this point, we should have acquired all of our neighbors. Now, it is time to add them.
        for(InsertionLock.NeighborInstance n : ownedLocks) {
            // Insert the neighbor into my own table.
            insertIntoTable(n.node, n.minLevel);
            // Let the neighbor insert me in its table.
            middleLayer.announceNeighbor(n.node.getAddress(), n.node.getPort(), getIdentity(), n.minLevel);
        }
        // Now, we release all of the locks.
        List<InsertionLock.NeighborInstance> toRelease = new ArrayList<>();
        ownedLocks.drainTo(toRelease);
        // Release the locks.
        toRelease.forEach(n -> {
            middleLayer.unlock(n.node.getAddress(), n.node.getPort(), getIdentity());
        });
        // Complete the insertion.
        inserted = true;
        logger.debug(getNumID() + " was inserted!");
        insertionLock.endInsertion();
    }

    /**
     * ... If not all the locks are acquired, the acquired locks are released.
     * @param left 0th level left neighbor.
     * @param right 0th level right neighbor.
     * @return true iff all the locks were acquired.
     */
    public boolean acquireNeighborLocks(SkipNodeIdentity left, SkipNodeIdentity right) {
        // Try to acquire the locks for the left and right neighbors at all the levels.
        SkipNodeIdentity leftNeighbor = left;
        SkipNodeIdentity rightNeighbor = right;
        // This flag will be set to false when we cannot acquire a lock.
        boolean allAcquired = true;
        // These flags will be used to detect when a neighbor at an upper level is the same as the lower one.
        boolean newLeftNeighbor = true;
        boolean newRightNeighbor = true;
        // Climb up the levels and acquire the left and right neighbor locks.
        for(int level = 0; level < lookupTable.getNumLevels(); level++) {
            if(leftNeighbor.equals(LookupTable.EMPTY_NODE) && rightNeighbor.equals(LookupTable.EMPTY_NODE)) {
                break;
            }
            if(newLeftNeighbor && !leftNeighbor.equals(LookupTable.EMPTY_NODE)) {
                // Try to acquire the lock for the left neighbor.
                logger.debug(getNumID() + " is trying to acquire a lock from " + leftNeighbor.getNumID());
                boolean acquired = middleLayer.tryAcquire(leftNeighbor.getAddress(), leftNeighbor.getPort(),
                        getIdentity(), leftNeighbor.version);
                if(!acquired) {
                    allAcquired = false;
                    break;
                }
                // Add the new lock to our list of locks.
                ownedLocks.add(new InsertionLock.NeighborInstance(leftNeighbor, level));
            }
            if(newRightNeighbor && !rightNeighbor.equals(LookupTable.EMPTY_NODE)) {
                logger.debug(getNumID() + " is trying to acquire a lock from " + rightNeighbor.getNumID());
                // Try to acquire the lock for the right neighbor.
                boolean acquired = middleLayer.tryAcquire(rightNeighbor.getAddress(), rightNeighbor.getPort(),
                        getIdentity(), rightNeighbor.version);
                if(!acquired) {
                    allAcquired = false;
                    break;
                }
                // Add the new lock to our list of locks.
                ownedLocks.add(new InsertionLock.NeighborInstance(rightNeighbor, level));
            }
            logger.debug(getNumID() + " is climbing up.");
            // Acquire the ladders (i.e., the neighbors at the upper level) and check if they are new neighbors
            // or not. If they are not, we won't need to request a lock from them.
            logger.debug(getNumID() + " is sending findLadder request to " + leftNeighbor.getNumID());
            SkipNodeIdentity leftLadder = (leftNeighbor.equals(LookupTable.EMPTY_NODE)) ? LookupTable.EMPTY_NODE
                    : middleLayer.findLadder(leftNeighbor.getAddress(), leftNeighbor.getPort(), level, 0, getNameID());
            newLeftNeighbor = !leftLadder.equals(leftNeighbor);
            logger.debug(getNumID() + " is sending findLadder request to " + rightNeighbor.getNumID());
            SkipNodeIdentity rightLadder = (rightNeighbor.equals(LookupTable.EMPTY_NODE)) ? LookupTable.EMPTY_NODE
                    : middleLayer.findLadder(rightNeighbor.getAddress(), rightNeighbor.getPort(), level, 1, getNameID());
            newRightNeighbor = !rightLadder.equals(rightNeighbor);
            leftNeighbor = leftLadder;
            rightNeighbor = rightLadder;
            // It may be the case that we cannot possibly acquire a new neighbor because another concurrent insertion
            // is locking a potential neighbor. This means we should simply fail and let the insertion procedure backoff.
            if(leftLadder.equals(LookupTable.INVALID_NODE) || rightLadder.equals(LookupTable.INVALID_NODE)) {
                allAcquired = false;
                break;
            }
            logger.debug(getNumID() + " has climbed up.");
        }
        logger.debug(getNumID() + " completed proposal phase.");
        // If we were not able to acquire all the locks, then release the locks that were acquired.
        if(!allAcquired) {
            List<InsertionLock.NeighborInstance> toRelease = new ArrayList<>();
            ownedLocks.drainTo(toRelease);
            // Release the locks.
            toRelease.forEach(n -> {
                middleLayer.unlock(n.node.getAddress(), n.node.getPort(), getIdentity());
            });
        }
        return allAcquired;
    }

    @Override
    public boolean tryAcquire(SkipNodeIdentity requester, int version) {
        // Naively try to acquire the lock.
        if(!insertionLock.tryAcquire(requester)) {
            logger.debug(getNumID() + " did not hand over the lock to " + requester.getNumID()
                    + " because it is already given to " + ((insertionLock.owner == null) ? "itself" : insertionLock.owner.getNumID()));
            return false;
        }
        // After acquiring the lock, make sure that the versions match.
        if(version != this.version) {
            // Otherwise, immediately release and return false.
            insertionLock.unlockOwned(requester);
            return false;
        }
        logger.debug(getNumID() + " is being locked by " + requester.getNumID() + " with provided version " + version);
        return true;
    }

    @Override
    public boolean unlock(SkipNodeIdentity owner) {
        boolean unlocked = insertionLock.unlockOwned(owner);
        logger.debug(getNumID() + " has released the lock from " + owner.getNumID() + ": " + unlocked);
        return unlocked;
    }

    /**
     * Returns whether the node is available to be used as a router. If the node is still being inserted, or is a neighbor
     * of a node that is currently being inserted, this will return false.
     * @return whether the node is available for routing or not.
     */
    @Override
    public boolean isAvailable() {
        return inserted && !insertionLock.isLocked();
    }

    /**
     * Finds the `ladder`, i.e. the node that should be used to propagate a newly joined node to the upper layer. Only
     * used by the insertion protocol, and not by the name ID search protocol even though both of them makes use of ladders.
     * @return the `ladder` node information.
     */
    public SkipNodeIdentity findLadder(int level, int direction, String target) {
        logger.debug(getNumID() + " has received a findLadder request.");
        if(level >= lookupTable.getNumLevels() || level < 0) {
            logger.debug(getNumID() + " is returning a findLadder response.");
            return LookupTable.EMPTY_NODE;
        }
        // If the current node and the inserted node have common bits more than the current level,
        // then this node is the neighbor so we return it
        if(SkipNodeIdentity.commonBits(target, getNameID()) > level) {
            logger.debug(getNumID() + " is returning a findLadder response.");
            return getIdentity();
        }
        SkipNodeIdentity curr = (direction == 0) ? getLeftNode(level) : getRightNode(level);
        while(!curr.equals(LookupTable.EMPTY_NODE) && SkipNodeIdentity.commonBits(target, curr.getNameID()) <= level) {
            logger.debug(getNumID() + " is in findLadder loop at level " + level + " with " + curr.getNumID());
            // Try to find a new neighbor, but immediately return if the neighbor is locked.
            curr = (direction == 0) ? middleLayer.getLeftNode(false, curr.getAddress(), curr.getPort(), level)
                    : middleLayer.getRightNode(false, curr.getAddress(), curr.getPort(), level);
            // If the potential neighbor is locked, we will get an invalid identity. We should directly return it in
            // that case.
            if(curr.equals(LookupTable.INVALID_NODE)) return curr;
        }
        logger.debug(getNumID() + " is returning a findLadder response.");
        return curr;
    }

    /**
     * Given a new neighbor, inserts it to the appropriate levels according to the name ID of the new node.
     * @param newNeighbor the identity of the new neighbor.
     */
    @Override
    public void announceNeighbor(SkipNodeIdentity newNeighbor, int minLevel) {
        insertIntoTable(newNeighbor, minLevel);
    }

    /**
     * Puts the given node into every appropriate level & direction according to its name ID and numerical ID.
     * @param node the node to insert.
     */
    private void insertIntoTable(SkipNodeIdentity node, int minLevel) {
        logger.debug(getNumID() + " has updated its table.");
        version++;
        //node.getNumID() < getNumID()
        int direction = (node.getNumID().compareTo(getNumID()) == -1) ? 0 : 1;
        int maxLevel = SkipNodeIdentity.commonBits(getNameID(), node.getNameID());
        for(int i = minLevel; i <= maxLevel; i++) {
            if(direction == 0) updateLeftNode(node, i);
            else updateRightNode(node, i);
        }
    }

    @Override
    public boolean delete() {
        // TODO Implement
        return false;
    }

    /**
     * TODO
     * @param numID
     * @return The resource Value
     */
    @Override
    public String getResourceByNumID(BigInteger numID) {
        return getResourceByNumID(numID, true).getResourceQueryResult();
    }


    private SkipNodeIdentity getResourceByNumID(BigInteger numID, boolean isGettingResource) {
        // If this is the node the search request is looking for, return its identity
        if (numID.equals(this.numID)) {
            if (isGettingResource && isUsingRedis) {
                setJedisPool();
                Jedis jedis = jedisPool.getResource();
                resourceQueryResult = jedis.get(numID.toString(16));
                jedis.close();
            }
            return getIdentity();
        }
        // Initialize the level to begin looking at
        int level = lookupTable.getNumLevels();
        // If the target is greater than this node's numID, the search should continue to the right
        // this.numID < numID
        if (this.numID.compareTo(numID) == -1) {
            // Start from the top, while there is no right neighbor, or the right neighbor's num ID is greater than what we are searching for
            // keep going down
            while(level>=0) {
                //lookupTable.getRight(level).getNumID() > numID
                if (lookupTable.getRight(level)==LookupTable.EMPTY_NODE ||
                        lookupTable.getRight(level).getNumID().compareTo(numID) == 1){
                    level--;
                } else {
                    break;
                }
            }
            // If the level is less than zero, then this node is the closest node to the numID being searched for from the right. Return.
            if (level < 0) {
                return getIdentity();
            }
            // Else, delegate the search to that node on the right
            SkipNodeIdentity delegateNode = lookupTable.getRight(level);
            return middleLayer.searchByNumID(delegateNode.getAddress(), delegateNode.getPort(), numID);
        } else {
            // Start from the top, while there is no right neighbor, or the right neighbor's num ID is greater than what we are searching for
            // keep going down
            while(level>=0) {
                //lookupTable.getLeft(level).getNumID() < numID
                if (lookupTable.getLeft(level)==LookupTable.EMPTY_NODE ||
                        lookupTable.getLeft(level).getNumID().compareTo(numID) == -1){
                    level--;
                } else {
                    break;
                }
            }
            // If the level is less than zero, then this node is the closest node to the numID being searched for from the right. Return.
            if (level < 0) {
                if (isGettingResource && isUsingRedis) {
                    setJedisPool();
                    Jedis jedis = jedisPool.getResource();
                    resourceQueryResult = jedis.get(numID.toString(16));
                    jedis.close();
                }
                return getIdentity();
            }
            // Else, delegate the search to that node on the right
            SkipNodeIdentity delegateNode = lookupTable.getLeft(level);
            return middleLayer.searchByNumID(delegateNode.getAddress(), delegateNode.getPort(), numID);
        }
    }

    /**
     * TODO
     * @param numID
     * @param resourceKey
     * @param resourceValue
     * @return The SkipNodeIdentity
     */
    public SkipNodeIdentity storeResourceByNumID(BigInteger numID, String resourceKey, String resourceValue) {
        return searchByNumID(numID);//?????????????????????????????????????????????
    }

    /**
     * Search for the given numID
     * @param numID The numID to search for
     * @return The SkipNodeIdentity of the node with the given numID. If it does not exist, returns the SkipNodeIdentity of the SkipNode with NumID closest to the given
     * numID from the direction the search is initiated.
     * For example: Initiating a search for a SkipNode with NumID 50 from a SnipNode with NumID 10 will return the SkipNodeIdentity of the SnipNode with NumID 50 is it exists. If
     * no such SnipNode exists, the SkipNodeIdentity of the SnipNode whose NumID is closest to 50 among the nodes whose NumID is less than 50 is returned.
     */
    @Override
    public SkipNodeIdentity searchByNumID(BigInteger numID) {
        return getResourceByNumID(numID, false);
    }

    @Override
    public boolean isLocked() {
        return insertionLock.isLocked();
    }

    @Override
    public boolean isLockedBy(String address, int port) {
        return insertionLock.isLockedBy(address, port);
    }

    /**
     * TODO
     * @param targetNameID
     * @return The resource value
     */
    public String getResourceByNameID(String targetNameID){
        return getResourceByNameID(nameID, true).result.getResourceQueryResult();
    }

    private SearchResult getResourceByNameID(String targetNameID, boolean isGettingResource) {

        if(nameID.equals(targetNameID)) {
            if (isGettingResource && isUsingRedis) {
                setJedisPool();
                Jedis jedis = jedisPool.getResource();
                resourceQueryResult = jedis.get((new BigInteger(targetNameID,2)).toString(16));
                jedis.close();
            }
            return new SearchResult(getIdentity());
        }
        // If the node is not completely inserted yet, return a tentative identity.
        if(!isAvailable()) {
            return new SearchResult(unavailableIdentity);
        }
        // Find the level in which the search should be started from.
        int level = SkipNodeIdentity.commonBits(nameID, targetNameID);
        if(level < 0) {
            if (isGettingResource && isUsingRedis) {
                setJedisPool();
                Jedis jedis = jedisPool.getResource();
                resourceQueryResult = jedis.get((new BigInteger(targetNameID,2)).toString(16));
                jedis.close();
            }
            return new SearchResult(getIdentity());
        }
        // Initiate the search.
        return middleLayer.searchByNameIDRecursive(address, port, targetNameID, level);
    }

    /**
     * TODO
     * @param nameID
     * @param resourceKey
     * @param resourceValue
     * @return The SkipNodeIdentity
     */
    public SearchResult storeResourceByNameID(String nameID, String resourceKey, String resourceValue){
        return null;
    }

    /**
     * Performs a name ID lookup over the skip-graph. If the exact name ID is not found, the most similar one is
     * returned.
     * @param targetNameID the target name ID.
     * @return the node with the name ID most similar to the target name ID.
     */
    @Override
    public SearchResult searchByNameID(String targetNameID) {
        return getResourceByNameID(targetNameID, false);
    }

    /**
     * Implements the recursive search by name ID procedure.
     * @param targetNameID the target name ID.
     * @param level the current level.
     * @return the SkipNodeIdentity of the closest SkipNode which has the common prefix length larger than `level`.
     */
    @Override
    public SearchResult searchByNameIDRecursive(String targetNameID, int level) {
        if(nameID.equals(targetNameID)) return new SearchResult(getIdentity());
        // Buffer contains the `most similar node` to return in case we cannot climb up anymore. At first, we try to set this to the
        // non null potential ladder.
        SkipNodeIdentity potentialLeftLadder = getIdentity();
        SkipNodeIdentity potentialRightLadder = getIdentity();
        SkipNodeIdentity buffer = (!potentialLeftLadder.equals(LookupTable.EMPTY_NODE)) ? potentialLeftLadder : potentialRightLadder;
        // This loop will execute and we expand our search window until a ladder is found either on the right or the left.
        while(SkipNodeIdentity.commonBits(targetNameID, potentialLeftLadder.getNameID()) <= level
                && SkipNodeIdentity.commonBits(targetNameID, potentialRightLadder.getNameID()) <= level) {
            // Return the potential ladder as the result if it is the result we are looking for.
            if(potentialLeftLadder.getNameID().equals(targetNameID)) return new SearchResult(potentialLeftLadder);
            if(potentialRightLadder.getNameID().equals(targetNameID)) return new SearchResult(potentialRightLadder);
            // Expand the search window on the level.
            if(!potentialLeftLadder.equals(LookupTable.EMPTY_NODE)) {
                buffer = potentialLeftLadder;
                potentialLeftLadder = middleLayer.findLadder(potentialLeftLadder.getAddress(), potentialLeftLadder.getPort(),
                        level, 0, targetNameID);
            }
            if(!potentialRightLadder.equals(LookupTable.EMPTY_NODE)) {
                buffer = potentialRightLadder;
                potentialRightLadder = middleLayer.findLadder(potentialRightLadder.getAddress(), potentialRightLadder.getPort(),
                        level, 1, targetNameID);
            }
            // Try to climb up on the either ladder.
            if(SkipNodeIdentity.commonBits(targetNameID, potentialRightLadder.getNameID()) > level) {
                level = SkipNodeIdentity.commonBits(targetNameID, potentialRightLadder.getNameID());
                return middleLayer.searchByNameIDRecursive(potentialRightLadder.getAddress(), potentialRightLadder.getPort(), targetNameID, level);
            } else if(SkipNodeIdentity.commonBits(targetNameID, potentialLeftLadder.getNameID()) > level) {
                level = SkipNodeIdentity.commonBits(targetNameID, potentialLeftLadder.getNameID());
                return middleLayer.searchByNameIDRecursive(potentialLeftLadder.getAddress(), potentialLeftLadder.getPort(), targetNameID, level);
            }
            // If we have expanded more than the length of the level, then return the most similar node (buffer).
            if(potentialLeftLadder.equals(LookupTable.EMPTY_NODE) && potentialRightLadder.equals(LookupTable.EMPTY_NODE)) {
                return new SearchResult(buffer);
            }
        }
        return new SearchResult(buffer);
    }

    @Override
    public SkipNodeIdentity updateLeftNode(SkipNodeIdentity snId, int level) {
        return lookupTable.updateLeft(snId, level);
    }

    @Override
    public SkipNodeIdentity updateRightNode(SkipNodeIdentity snId, int level) {
        return lookupTable.updateRight(snId, level);
    }


    @Override
    public SkipNodeIdentity getRightNode(int level) {
        logger.debug(getNumID() + " has received a getRightNode request.");
        SkipNodeIdentity right = lookupTable.getRight(level);
        SkipNodeIdentity r = (right.equals(LookupTable.EMPTY_NODE)) ? right
                : middleLayer.getIdentity(right.getAddress(), right.getPort());
        logger.debug(getNumID() + " is returning a getRightNode response.");
        return r;
    }

    @Override
    public SkipNodeIdentity getLeftNode(int level) {
        logger.debug(getNumID() + " has received a getLeftNode request.");
        SkipNodeIdentity left = lookupTable.getLeft(level);
        SkipNodeIdentity r = (left.equals(LookupTable.EMPTY_NODE)) ? left
                : middleLayer.getIdentity(left.getAddress(), left.getPort());
        logger.debug(getNumID() + " is returning a getLeftNode response.");
        return r;
    }

    /*
    Test
     */
    AtomicInteger i = new AtomicInteger(0);
    @Override
    public SkipNodeIdentity increment(SkipNodeIdentity snId, int level) {
//        logger.debug(snId+" "+level+" "+i);
        if (level==0){
            return middleLayer.increment(snId.getAddress(), snId.getPort(), snId, 1);
        }else {
//            logger.debug("incrementing");
            i.addAndGet(1);//i += 1;
//            logger.debug(i);
            return new SkipNodeIdentity(""+i, BigInteger.valueOf(i.get()), ""+i,i.get());
        }
    }

    @Override
    public boolean inject(List<SkipNodeIdentity> injections){
//        nodeStashLock.lock();
        // nodeStash.addAll(injections);
        return true;
//        for(SkipNodeIdentity injection : injections){
//
//        }
//        nodeStashLock.unlock();
    }

    private void pushOutNodes(List<SkipNodeIdentity> lst){
        for (SkipNodeIdentity nd : lst){
            middleLayer.inject(nd.getAddress(), nd.getPort(), lst);
        }
    }
}
