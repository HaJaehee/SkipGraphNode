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
 Added getResourceByNumID(), getNumIDSetByNameID(), storeResourceByNumID(), and storeResourceByNameID().
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-03-23
 Version : 1.0.2
 Implemented storeResourceByNumID(), storeResourceByResourceKey(), storeResourceByNameID(), and storeResourceReplicationsByNameID().
 Implemented handleResourceByNumID().
 Added bytesToHex() and sha256().
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-03-25
 Version : 1.0.3
 Added getNodeListAtHighestLevel(), getFirstNodeAtHighestLevel(), and getNodeListByNameID().
 Added getLeftNodeAndAddNodeAtHighestLevel() and getRightNodeAndAddNodeAtHighestLevel().
 Implemented getResource() and storeResource().
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-03-26
 Version : 1.0.4
 Removed the member variable that is resourceQueryResult.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-03-27
 Version : 1.1.0
 Implemented replication of resource into nodes having common name ID prefix
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 //TODO Are we need to delete a resource?
 */
/* -------------------------------------------------------- */


import lookup.LookupTable;
import middlelayer.MiddleLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    public SkipNode(SkipNodeIdentity snID, LookupTable lookupTable, boolean isUsingRedis) {
        this.address = snID.getAddress();
        this.port = snID.getPort();
        this.numID = snID.getNumID();
        this.nameID = snID.getNameID();
        this.lookupTable = lookupTable;
        this.storagePath = snID.getStoragePath();
        this.isUsingRedis = isUsingRedis;
        if (isUsingRedis) {
            this.redisPoolConfig = null;
            this.redisAddress = "127.0.0.1";
            this.redisPort = 6379;
            this.redisTimeout = 1000;
            this.redisPassword = "winslab";
        }
        else {
            this.redisPoolConfig = null;
            this.redisAddress = null;
            this.redisPort = 0;
            this.redisTimeout = 0;
            this.redisPassword = null;
        }
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

    //TODO: skip node identity는 serializable 해야 한다.
    //TODO: identity에 redis get 값을 넣으면 되는 거 아닌가?
    //TODO: 그럼 member variable에는 redis에 접속할 수 있는 정보를 넣으면 되는건가?
    @Override
    public SkipNodeIdentity getIdentity(String resourceQueryResult) {
        return new SkipNodeIdentity(nameID, numID, address, port, version, storagePath, resourceQueryResult);
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
            logger.debug(getNumID().toString(16) + " was inserted!");
            lookupTable.addNodeIntoListAtHighestLevel(getIdentity(null));
            inserted = true;
            insertionLock.endInsertion();
            return;
        }
        // Try to acquire the locks from all of my neighbors.
        while(true) {
            SkipNodeIdentity left = null;
            SkipNodeIdentity right = null;
            logger.debug(getNumID().toString(16) + " searches for its 0-level neighbors...");
            // First, find my 0-level neighbor by making a num-id search through the introducer.

            SkipNodeIdentity searchResult = middleLayer.handleResourceByNumID(introducerAddress, introducerPort, numID, false, false, null);

            // Get my 0-level left and right neighbors.
            //getNumID() < searchResult.getNumID()
            if(getNumID().compareTo(searchResult.getNumID()) == -1) {
                right = searchResult;
                left = middleLayer.getLeftNode(right.getAddress(), right.getPort(), 0);
            } else {
                left = searchResult;
                right = middleLayer.getRightNode(left.getAddress(), left.getPort(), 0);
            }
            logger.debug(getNumID().toString(16) + " found its 0-level neighbors: " + left.getNumID().toString(16) + ", " + right.getNumID().toString(16));
            if(acquireNeighborLocks(left, right)) break;
            // When we fail, backoff for a random interval before trying again.
            logger.debug(getNumID().toString(16) + " could not acquire the locks. Backing off...");
            int sleepTime = (int)(Math.random() * 2000);
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                logger.error("[SkipNode.insert] Could not backoff.");
                e.printStackTrace();
            }
        }
        logger.debug(getNumID().toString(16) + " has acquired all the locks: ");
        ownedLocks.forEach(n -> logger.debug(n.node.getNumID().toString(16) + ", "));
        logger.debug("");
        // At this point, we should have acquired all of our neighbors. Now, it is time to add them.
        for(InsertionLock.NeighborInstance n : ownedLocks) {
            // Insert the neighbor into my own table.
            insertIntoTable(n.node, n.minLevel);
            // Let the neighbor insert me in its table.
            middleLayer.announceNeighbor(n.node.getAddress(), n.node.getPort(), getIdentity(null), n.minLevel);
        }
        // Now, we release all of the locks.
        List<InsertionLock.NeighborInstance> toRelease = new ArrayList<>();
        ownedLocks.drainTo(toRelease);
        // Release the locks.
        toRelease.forEach(n -> {
            middleLayer.unlock(n.node.getAddress(), n.node.getPort(), getIdentity(null));
        });
        // Complete the insertion.
        inserted = true;
        logger.debug(getNumID().toString(16) + " was inserted!");

        lookupTable.setNodeListAtHighestLevel(getNodeListFromNeighborAtHighestLevel());
        lookupTable.addNodeIntoListAtHighestLevel(getIdentity(null));
        addNodeIntoListAtHighestLevelRecursively();
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
                logger.debug(getNumID().toString(16) + " is trying to acquire a lock from " + leftNeighbor.getNumID());
                boolean acquired = middleLayer.tryAcquire(leftNeighbor.getAddress(), leftNeighbor.getPort(),
                        getIdentity(null), leftNeighbor.version);
                if(!acquired) {
                    allAcquired = false;
                    break;
                }
                // Add the new lock to our list of locks.
                ownedLocks.add(new InsertionLock.NeighborInstance(leftNeighbor, level));
            }
            if(newRightNeighbor && !rightNeighbor.equals(LookupTable.EMPTY_NODE)) {
                logger.debug(getNumID().toString(16) + " is trying to acquire a lock from " + rightNeighbor.getNumID());
                // Try to acquire the lock for the right neighbor.
                boolean acquired = middleLayer.tryAcquire(rightNeighbor.getAddress(), rightNeighbor.getPort(),
                        getIdentity(null), rightNeighbor.version);
                if(!acquired) {
                    allAcquired = false;
                    break;
                }
                // Add the new lock to our list of locks.
                ownedLocks.add(new InsertionLock.NeighborInstance(rightNeighbor, level));
            }
            logger.debug(getNumID().toString(16) + " is climbing up.");
            // Acquire the ladders (i.e., the neighbors at the upper level) and check if they are new neighbors
            // or not. If they are not, we won't need to request a lock from them.
            logger.debug(getNumID().toString(16) + " is sending findLadder request to " + leftNeighbor.getNumID().toString(16));
            SkipNodeIdentity leftLadder = (leftNeighbor.equals(LookupTable.EMPTY_NODE)) ? LookupTable.EMPTY_NODE
                    : middleLayer.findLadder(leftNeighbor.getAddress(), leftNeighbor.getPort(), level, 0, getNameID());
            newLeftNeighbor = !leftLadder.equals(leftNeighbor);
            logger.debug(getNumID().toString(16) + " is sending findLadder request to " + rightNeighbor.getNumID().toString(16));
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
            logger.debug(getNumID().toString(16) + " has climbed up.");
        }
        logger.debug(getNumID().toString(16) + " completed proposal phase.");
        // If we were not able to acquire all the locks, then release the locks that were acquired.
        if(!allAcquired) {
            List<InsertionLock.NeighborInstance> toRelease = new ArrayList<>();
            ownedLocks.drainTo(toRelease);
            // Release the locks.
            toRelease.forEach(n -> {
                middleLayer.unlock(n.node.getAddress(), n.node.getPort(), getIdentity(null));
            });
        }
        return allAcquired;
    }

    @Override
    public boolean tryAcquire(SkipNodeIdentity requester, int version) {
        // Naively try to acquire the lock.
        if(!insertionLock.tryAcquire(requester)) {
            logger.debug(getNumID().toString(16) + " did not hand over the lock to " + requester.getNumID().toString(16)
                    + " because it is already given to " + ((insertionLock.owner == null) ? "itself" : insertionLock.owner.getNumID().toString(16)));
            return false;
        }
        // After acquiring the lock, make sure that the versions match.
        if(version != this.version) {
            // Otherwise, immediately release and return false.
            insertionLock.unlockOwned(requester);
            return false;
        }
        logger.debug(getNumID().toString(16) + " is being locked by " + requester.getNumID().toString(16) + " with provided version " + version);
        return true;
    }

    @Override
    public boolean unlock(SkipNodeIdentity owner) {
        boolean unlocked = insertionLock.unlockOwned(owner);
        logger.debug(getNumID().toString(16) + " has released the lock from " + owner.getNumID().toString(16) + ": " + unlocked);
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
        logger.debug(getNumID().toString(16) + " has received a findLadder request.");
        if(level >= lookupTable.getNumLevels() || level < 0) {
            logger.debug(getNumID().toString(16) + " is returning a findLadder response.");
            return LookupTable.EMPTY_NODE;
        }
        // If the current node and the inserted node have common bits more than the current level,
        // then this node is the neighbor so we return it
        if(SkipNodeIdentity.commonBits(target, getNameID()) > level) {
            logger.debug(getNumID().toString(16) + " is returning a findLadder response.");
            return getIdentity(null);
        }
        SkipNodeIdentity curr = (direction == 0) ? getLeftNode(level) : getRightNode(level);
        while(!curr.equals(LookupTable.EMPTY_NODE) && SkipNodeIdentity.commonBits(target, curr.getNameID()) <= level) {
            logger.debug(getNumID().toString(16) + " is in findLadder loop at level " + level + " with " + curr.getNumID());
            // Try to find a new neighbor, but immediately return if the neighbor is locked.
            curr = (direction == 0) ? middleLayer.getLeftNode(false, curr.getAddress(), curr.getPort(), level)
                    : middleLayer.getRightNode(false, curr.getAddress(), curr.getPort(), level);
            // If the potential neighbor is locked, we will get an invalid identity. We should directly return it in
            // that case.
            if(curr.equals(LookupTable.INVALID_NODE)) return curr;
        }
        logger.debug(getNumID().toString(16) + " is returning a findLadder response.");
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
        logger.debug(getNumID().toString(16) + " has updated its table.");
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
     * Get a resource from Redis system by using a resource key.
     * @param resourceKey
     * @return The string is the member variable resourceQueryResult of this node.
     */
    @Override
    public String getResource(String resourceKey) {
        String resourceQueryResult = handleJedisWithResourceKey(resourceKey, true, false, null);
        return resourceQueryResult;
    }

    /**
     * Store a resource into Redis system by using a resource key and a resource value.
     * @param resourceKey
     * @param resourceValue
     */
    @Override
    public SkipNodeIdentity storeResource(String resourceKey, String resourceValue) {
        setJedisPool();
        Jedis jedis = jedisPool.getResource();
        jedis.set(resourceKey, resourceValue);
        logger.debug("Resource Key: \""+ resourceKey +"\", value: \"" + resourceValue +"\" is stored into node ID: " + this.numID.toString(16));
        jedis.close();
        return getIdentity(null);
    }

    /**
     * Get a resource by using a number ID from the node that have numerically closest number ID.
     * The resource stored in the Redis system MUST have same key with the number ID.
     * @param numID
     * @return The resource value
     */
    @Override
    public String getResourceByNumID(BigInteger numID) {
        return handleResourceByNumID(numID, true, false, null).getResourceQueryResult();
    }

    /**
     * Get a resource by using a resource key from the node that have the numerically closest number ID.
     * The resource stored in the Redis system MUST have same key with the resource key.
     * @param resourceKey
     * @return The resource value
     */
    @Override
    public String getResourceByResourceKey(String resourceKey) throws NumberFormatException{
        BigInteger intResourceKey = new BigInteger(resourceKey, 16);
        return handleResourceByNumID(intResourceKey, true, false, null).getResourceQueryResult();
    }

    /**
     * Handle resource by a number ID (or a resource key).
     * Find node which have the numerically closest number ID from the parameter number ID.
     * @param numID
     * @param isGettingResource
     * @param isSettingResource
     * @param resourceValue
     * @return SkipNodeIdentity
     */
    public SkipNodeIdentity handleResourceByNumID(BigInteger numID, boolean isGettingResource, boolean isSettingResource, String resourceValue) {
        // If this is the node the search request is looking for, return its identity
        logger.debug("in "+ this.numID.toString(16)+" handler, key: " + numID.toString(16));
        if (numID.equals(this.numID)) {
            return getIdentity(handleJedisWithNumID(numID, isGettingResource, isSettingResource, resourceValue));
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

                return getIdentity(handleJedisWithNumID(numID, isGettingResource, isSettingResource, resourceValue));
            }
            // Else, delegate the search to that node on the right
            SkipNodeIdentity delegateNode = lookupTable.getRight(level);
            return middleLayer.handleResourceByNumID(delegateNode.getAddress(), delegateNode.getPort(), numID, isGettingResource, isSettingResource, resourceValue);
        }
        else { // this.numID > numID
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
            // If the level is less than zero, then this node is the closest node to the numID being searched for from the left. Return.
            if (level < 0) {

                return getIdentity(handleJedisWithNumID(numID, isGettingResource, isSettingResource, resourceValue));
            }
            // Else, delegate the search to that node on the right
            SkipNodeIdentity delegateNode = lookupTable.getLeft(level);
            return middleLayer.handleResourceByNumID(delegateNode.getAddress(), delegateNode.getPort(), numID, isGettingResource, isSettingResource, resourceValue);
        }
    }

    /**
     * Handle the Redis system by facilitating the Jedis library with a number ID.
     * @param numID
     * @param isGettingResource
     * @param isSettingResource
     * @param resourceValue
     */
    private String handleJedisWithNumID (BigInteger numID, boolean isGettingResource, boolean isSettingResource, String resourceValue) {
        String returnResourceQueryResult = null;
        if (isGettingResource && isUsingRedis) {
            setJedisPool();
            Jedis jedis = jedisPool.getResource();
            returnResourceQueryResult = jedis.get(numID.toString(16));
            jedis.close();
        }
        else if (isSettingResource && resourceValue != null && isUsingRedis) {
            for (SkipNodeIdentity i : lookupTable.getNodeListAtHighestLevel()) {
                if (i.getNumID().compareTo(this.numID) == 0) {
                    SkipNodeIdentity response = storeResource(numID.toString(16), resourceValue);
                    //TODO response is not used in this version.
                }
                else {
                    SkipNodeIdentity response = middleLayer.storeResource(i.getAddress(), i.getPort(), numID, resourceValue);
                    //TODO response is not used in this version.
                }
            }
            returnResourceQueryResult = null;
        }
        return returnResourceQueryResult;
    }

    /**
     * Handle the Redis system by facilitating the Jedis library with a resource key.
     * @param resourceKey
     * @param isGettingResource
     * @param isSettingResource
     * @param resourceValue
     * @return The String
     */
    private String handleJedisWithResourceKey (String resourceKey, boolean isGettingResource, boolean isSettingResource, String resourceValue) {
        return handleJedisWithNumID(new BigInteger(resourceKey, 16),isGettingResource,isSettingResource,resourceValue);
    }

    /**
     * Store a resource by using a number ID into the node that have the numerically closest number ID.
     * @param numID
     * @param resourceValue
     * @return The SkipNodeIdentity of the node which have the resource.
     */
    @Override
    public SkipNodeIdentity storeResourceByNumID(BigInteger numID, String resourceValue) throws NumberFormatException{
        return handleResourceByNumID(numID, false, true, resourceValue);
    }

    /**
     * Store a resource by using a resource key into the node that have the numerically closest number ID.
     * @param resourceKey
     * @param resourceValue
     * @return The SkipNodeIdentity of the node which have the resource.
     */
    @Override
    public SkipNodeIdentity storeResourceByResourceKey(String resourceKey, String resourceValue) throws NumberFormatException{
        BigInteger intResourceKey = new BigInteger(resourceKey, 16);
        return handleResourceByNumID(intResourceKey, false, true, resourceValue);
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
        return handleResourceByNumID(numID, false, false, null);
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
     * Get a node list in which nodes have longest common prefix at the highest level.
     * @param targetNameID
     * @return The list of SkipNodeIdentity
     */
    @Override
    public ArrayList<SkipNodeIdentity> getNodeListByNameID(String targetNameID){
        SearchResult targetResult = searchByNameID(targetNameID);
        ArrayList<SkipNodeIdentity> cumulativeNodeList = new ArrayList<SkipNodeIdentity>();
        int highestLevel = this.lookupTable.getNumLevels() - 1 ;

        SkipNodeIdentity left = middleLayer.getLeftNode(targetResult.result.getAddress(), targetResult.result.getPort(), highestLevel);
        while (!left.equals(LookupTable.EMPTY_NODE)) {
            cumulativeNodeList.add(0, left);
            left = middleLayer.getLeftNode(left.getAddress(), left.getPort(), highestLevel);
        }

        cumulativeNodeList.add(getIdentity(null));

        SkipNodeIdentity right = middleLayer.getRightNode(targetResult.result.getAddress(), targetResult.result.getPort(), highestLevel);
        while (!right.equals(LookupTable.EMPTY_NODE)) {
            cumulativeNodeList.add(right);
            right = middleLayer.getRightNode(right.getAddress(), right.getPort(), highestLevel);
        }

        return cumulativeNodeList;
    }

    private SearchResult handleResourceByNameID(String targetNameID, boolean isGettingResource, boolean isSettingResource, String resourceKey, String resourceValue) {

        if(nameID.equals(targetNameID)) {
            return new SearchResult(getIdentity(handleJedisWithNameID(isGettingResource, isSettingResource, resourceKey, resourceValue)));
        }
        // If the node is not completely inserted yet, return a tentative identity.
        if(!isAvailable()) {
            return new SearchResult(unavailableIdentity);
        }
        // Find the level in which the search should be started from.
        int level = SkipNodeIdentity.commonBits(nameID, targetNameID);
        if(level < 0) {
            return new SearchResult(getIdentity(handleJedisWithNameID(isGettingResource, isSettingResource, resourceKey, resourceValue)));
        }
        // Initiate the search.
        return middleLayer.searchByNameIDRecursive(address, port, targetNameID, level, isGettingResource, isSettingResource, resourceKey, resourceValue);
    }

    /**
     * Store a resource by using a name ID into the node that have the longest common prefix name ID.
     * @param targetNameID
     * @param resourceKey
     * @param resourceValue
     * @return The SkipNodeIdentity
     */
    @Override
    public SearchResult storeResourceByNameID(String targetNameID, String resourceKey, String resourceValue){
        return handleResourceByNameID(targetNameID, false, true, resourceKey, resourceValue);
    }

    /**
     * TODO
     * @param targetNameID
     * @param resourceKey
     * @param resourceValue
     * @return The SkipNodeIdentity
     */
    @Override
    public SearchResult storeResourceReplicationsByNameID(String targetNameID, String resourceKey, String resourceValue){
        //TODO
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
        return handleResourceByNameID(targetNameID, false, false, null, null);
    }

    /**
     * Get a resource by using a name ID from the node that have the longest common prefix name ID, and resource key.
     * @param targetNameID the target name ID.
     * @return the node with the name ID most similar to the target name ID.
     */
    @Override
    public String getResourceByNameID(String targetNameID, String resourceKey) {
        return handleResourceByNameID(targetNameID, true, false, resourceKey, null).result.getResourceQueryResult();
    }

    /**
     * Implements the recursive search by name ID procedure.
     * @param targetNameID the target name ID.
     * @param level the current level.
     * @param isGettingResource
     * @param isSettingResource
     * @param resourceKey
     * @param resourceValue
     * @return the SkipNodeIdentity of the closest SkipNode which has the common prefix length larger than `level`.
     */
    @Override
    public SearchResult searchByNameIDRecursive(String targetNameID, int level, boolean isGettingResource, boolean isSettingResource, String resourceKey, String resourceValue) {
        if(nameID.equals(targetNameID)) {
            return new SearchResult(getIdentity(handleJedisWithNameID(isGettingResource, isSettingResource, resourceKey, resourceValue)));
        }
        // Buffer contains the `most similar node` to return in case we cannot climb up anymore. At first, we try to set this to the
        // non null potential ladder.
        SkipNodeIdentity potentialLeftLadder = getIdentity(null);
        SkipNodeIdentity potentialRightLadder = getIdentity(null);
        SkipNodeIdentity buffer = (!potentialLeftLadder.equals(LookupTable.EMPTY_NODE)) ? potentialLeftLadder : potentialRightLadder;
        // This loop will execute and we expand our search window until a ladder is found either on the right or the left.
        while(SkipNodeIdentity.commonBits(targetNameID, potentialLeftLadder.getNameID()) <= level
                && SkipNodeIdentity.commonBits(targetNameID, potentialRightLadder.getNameID()) <= level) {
            // Return the potential ladder as the result if it is the result we are looking for.
            if(potentialLeftLadder.getNameID().equals(targetNameID)) {
                //TODO
                return new SearchResult(potentialLeftLadder);
            }
            if(potentialRightLadder.getNameID().equals(targetNameID)) {
                //TODO
                return new SearchResult(potentialRightLadder);
            }
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
                return middleLayer.searchByNameIDRecursive(potentialRightLadder.getAddress(), potentialRightLadder.getPort(), targetNameID, level, isGettingResource, isSettingResource, resourceKey, resourceValue);
            } else if(SkipNodeIdentity.commonBits(targetNameID, potentialLeftLadder.getNameID()) > level) {
                level = SkipNodeIdentity.commonBits(targetNameID, potentialLeftLadder.getNameID());
                return middleLayer.searchByNameIDRecursive(potentialLeftLadder.getAddress(), potentialLeftLadder.getPort(), targetNameID, level, isGettingResource, isSettingResource, resourceKey, resourceValue);
            }
            // If we have expanded more than the length of the level, then return the most similar node (buffer).
            if(potentialLeftLadder.equals(LookupTable.EMPTY_NODE) && potentialRightLadder.equals(LookupTable.EMPTY_NODE)) {
                //TODO
                return new SearchResult(buffer);
            }
        }
        //TODO
        return new SearchResult(buffer);
    }

    private String handleJedisWithNameID(boolean isGettingResource, boolean isSettingResource, String resourceKey, String resourceValue) {
        String returnResourceQueryResult = null;
        if (isGettingResource && resourceKey != null && isUsingRedis) {
            setJedisPool();
            Jedis jedis = jedisPool.getResource();
            returnResourceQueryResult = jedis.get(resourceKey);
            jedis.close();
        }
        else if (isSettingResource && resourceKey != null && resourceValue != null && isUsingRedis) {
            for (SkipNodeIdentity i : lookupTable.getNodeListAtHighestLevel()) {
                if (i.getNumID().compareTo(this.numID) == 0) {
                    SkipNodeIdentity response = storeResource(resourceKey, resourceValue);
                    //TODO response is not used in this version.
                }
                else {
                    SkipNodeIdentity response = middleLayer.storeResource(i.getAddress(), i.getPort(), new BigInteger(resourceKey, 16), resourceValue);
                    //TODO response is not used in this version.
                }
            }
            returnResourceQueryResult = null;
        }
        return  returnResourceQueryResult;
    }


    @Override
    public ArrayList<SkipNodeIdentity> getNodeListAtHighestLevel() {
        return lookupTable.getNodeListAtHighestLevel();
    }

    private void addNodeIntoListAtHighestLevelRecursively() {
        int highestLevel = this.lookupTable.getNumLevels() - 1 ;

        SkipNodeIdentity left = getLeftNode(highestLevel);
        while (!left.equals(LookupTable.EMPTY_NODE)) {
            left = middleLayer.getLeftNodeAndAddNodeAtHighestLevel(left.getAddress(), left.getPort(), highestLevel, getIdentity(null));
        }

        SkipNodeIdentity right = getRightNode(highestLevel);
        while (!right.equals(LookupTable.EMPTY_NODE)) {
            right = middleLayer.getRightNodeAndAddNodeAtHighestLevel(right.getAddress(), right.getPort(), highestLevel, getIdentity(null));
        }
    }

    private ArrayList<SkipNodeIdentity> getNodeListFromNeighborAtHighestLevel() {
        ArrayList<SkipNodeIdentity> nodeList = new ArrayList<SkipNodeIdentity>();
        int highestLevel = this.lookupTable.getNumLevels() - 1 ;

        SkipNodeIdentity left = getLeftNode(highestLevel);
        SkipNodeIdentity right = getRightNode(highestLevel);
        if (!left.equals(LookupTable.EMPTY_NODE)) {
            nodeList = middleLayer.getNodeListAtHighestLevel(left.getAddress(), left.getPort());
        }
        else if (!right.equals(LookupTable.EMPTY_NODE)) {
            nodeList = middleLayer.getNodeListAtHighestLevel(right.getAddress(), right.getPort());
        }
        return nodeList;
    }
    /**
     * Get a node list at highest level recursively.
     * This is not efficient method.
     * @return The arraylist of nodes at highest level.
     */
    private ArrayList<SkipNodeIdentity> getNodeListAtHighestLevelRecursively() {

        ArrayList<SkipNodeIdentity> cumulativeNodeList = new ArrayList<SkipNodeIdentity>();
        int highestLevel = this.lookupTable.getNumLevels() - 1 ;

        SkipNodeIdentity left = getLeftNode(highestLevel);
        while (!left.equals(LookupTable.EMPTY_NODE)) {
            cumulativeNodeList.add(0, left);
            left = middleLayer.getLeftNode(left.getAddress(), left.getPort(), highestLevel);
        }

        cumulativeNodeList.add(getIdentity(null));

        SkipNodeIdentity right = getRightNode(highestLevel);
        while (!right.equals(LookupTable.EMPTY_NODE)) {
            cumulativeNodeList.add(right);
            right = middleLayer.getRightNode(right.getAddress(), right.getPort(), highestLevel);
        }

        return cumulativeNodeList;
    }

    /**
     * TODO
     * @return The SkipNodeIdentity.
     */
    @Override
    public SkipNodeIdentity getFirstNodeAtHighestLevel() {
        int highestLevel = this.lookupTable.getNumLevels() - 1 ;

        SkipNodeIdentity left = getLeftNode(highestLevel);
        SkipNodeIdentity buffer = getIdentity(null);
        while (!left.equals(LookupTable.EMPTY_NODE)) {
            buffer = left;
            left = middleLayer.getLeftNode(left.getAddress(), left.getPort(), highestLevel);
        }

        return buffer;
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
    public SkipNodeIdentity getRightNodeAndAddNodeAtHighestLevel(int level, SkipNodeIdentity newNodeId) {
        lookupTable.addNodeIntoListAtHighestLevel(newNodeId);
        return getRightNode(level);
    }

    @Override
    public SkipNodeIdentity getRightNode(int level) {
        logger.debug(getNumID().toString(16) + " has received a getRightNode request.");
        SkipNodeIdentity right = lookupTable.getRight(level);
        SkipNodeIdentity r = (right.equals(LookupTable.EMPTY_NODE)) ? right
                : middleLayer.getIdentity(right.getAddress(), right.getPort());
        logger.debug(getNumID().toString(16) + " is returning a getRightNode response.");
        return r;
    }

    @Override
    public SkipNodeIdentity getLeftNodeAndAddNodeAtHighestLevel(int level, SkipNodeIdentity newNodeId) {
        lookupTable.addNodeIntoListAtHighestLevel(newNodeId);
        return getLeftNode(level);
    }

    @Override
    public SkipNodeIdentity getLeftNode(int level) {
        logger.debug(getNumID().toString(16) + " has received a getLeftNode request.");
        SkipNodeIdentity left = lookupTable.getLeft(level);
        SkipNodeIdentity r = (left.equals(LookupTable.EMPTY_NODE)) ? left
                : middleLayer.getIdentity(left.getAddress(), left.getPort());
        logger.debug(getNumID().toString(16) + " is returning a getLeftNode response.");
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

    public static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b: bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
    public static byte[] sha256(String msg) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(msg.getBytes());

        return md.digest();
    }
}
