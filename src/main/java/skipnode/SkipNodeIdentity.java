package skipnode;
/* -------------------------------------------------------- */
/**
 File name : SkipNodeIdentity.java
 Rev. history : 2021-03-19
 Version : 1.0.0
 Added storage path and jedis.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-03-22
 Version : 1.0.1
 Modified Jedis features as a key-value storage system.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */


import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;

// Basic skipnode.SkipNodeIdentity class
public class SkipNodeIdentity implements Serializable, Comparable<SkipNodeIdentity> {
    private final String nameID;
    private final BigInteger numID;
    private final String address;
    private final int port;
    private final String storagePath;
    private final boolean isUsingRedis;
    private final String redisPoolConfig;
    private final String redisAddress;
    private final int redisPort;
    private final String redisPassword;
    private final int redisTimeout;
    private final String resourceQueryResult;

    // Denotes the lookup table version.
    public int version;

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, int version, String storagePath, Boolean isUsingRedis, String resourceQueryResult) {
        this.nameID = nameID;
        this.numID = numID;
        this.address = address;
        this.port = port;
        this.version = version;
        this.storagePath = storagePath;
        this.isUsingRedis = isUsingRedis;
        if (isUsingRedis) {
            this.redisPoolConfig = null;
            this.redisAddress = "192.168.0.4";
            this.redisPort = 6379;
            this.redisTimeout = 1000;
            this.redisPassword = "winslab";
            this.resourceQueryResult = resourceQueryResult;
        }
        else {
            this.redisPoolConfig = null;
            this.redisAddress = null;
            this.redisPort = 0;
            this.redisTimeout = 0;
            this.redisPassword = null;
            this.resourceQueryResult = null;
        }
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port) {
        this(nameID, numID, address, port, 0, null, false, null);
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, int version, String storagePath) {
        this(nameID, numID, address, port, version, storagePath, false, null);
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, String storagePath) {
        this(nameID, numID, address, port, 0, storagePath);
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, int version, boolean isUsingRedis, String resourceQueryResult) {
        this(nameID, numID, address, port, version, null, isUsingRedis, resourceQueryResult);
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, boolean isUsingRedis, String resourceQueryResult) {
        this(nameID, numID, address, port, 0, isUsingRedis, resourceQueryResult);
    }


    public String getNameID() {
        return nameID;
    }

    public BigInteger getNumID() {
        return numID;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {return port;}

    public String getStoragePath() { return storagePath; }

    public boolean isUsingRedis() { return isUsingRedis; }

    public String getRedisPoolConfig() { return redisPoolConfig; }

    public String getRedisAddress() { return redisAddress; }

    public int getRedisPort() { return redisPort; }

    public int getRedisTimeout() { return redisTimeout; }

    public String getRedisPassword() { return redisPassword; }

    public String getResourceQueryResult() {
        if (resourceQueryResult == null) {
            return "";
        }
        else {
            return resourceQueryResult;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkipNodeIdentity that = (SkipNodeIdentity) o;
        return getNumID().equals(that.getNumID()) &&
                getNameID().equals(that.getNameID()) &&
                getAddress().equals(that.getAddress()) &&
                getPort() == that.getPort();
    }

    public static int commonBits(String name1, String name2) {
        if(name1 == null || name2 == null) {
            return -1;
        }
        if(name1.length() != name2.length())
            return -1;
        int i = 0;
        while(i < name1.length() && name1.charAt(i) == name2.charAt(i)) i++;
        return i;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNameID(), getNumID(), getAddress(), getPort());
    }

    @Override
    public String toString() {
        return "Name ID: "+nameID+"\tNum ID: "+numID.toString()+"\tAddress: "+address+"\tPort: "+port;
    }

    @Override
    public int compareTo(SkipNodeIdentity o) {
        return numID.compareTo(o.numID);
    }
}
