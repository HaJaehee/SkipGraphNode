package skipnode;
/* -------------------------------------------------------- */
/**
 File name : SkipNodeIdentity.java
 Rev. history : 2021-03-19
 Version : 1.0.0
 Added storage path and jedis.
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
    private final String redisPoolConfig;
    private final String redisAddress;
    private final int redisPort;
    private final String redisPassword;
    private final int redisTimeout;
    private final String redisResult;

    // Denotes the lookup table version.
    public int version;

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, int version, String storagePath, String redisPoolConfig, String redisAddress, int redisPort, int redisTimeout, String redisPassword, String redisResult) {
        this.nameID = nameID;
        this.numID = numID;
        this.address = address;
        this.port = port;
        this.version = version;
        this.storagePath = storagePath;
        this.redisPoolConfig = redisPoolConfig;
        this.redisAddress = redisAddress;
        this.redisPort = redisPort;
        this.redisTimeout = redisTimeout;
        this.redisPassword = redisPassword;
        this.redisResult = redisResult;
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port) {
        this(nameID, numID, address, port, 0, null, null, null, 0, 0, null, null);
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, int version, String storagePath) {
        this(nameID, numID, address, port, version, storagePath, null, null, 0, 0, null, null);
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, String storagePath) {
        this(nameID, numID, address, port, 0, storagePath);
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, int version, String redisPoolConfig, String redisAddress, int redisPort, int redisTimeout, String redisPassword, String redisResult) {
        this(nameID, numID, address, port, version, null, redisPoolConfig, redisAddress, redisPort, redisTimeout, redisPassword, redisResult);
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, String redisPoolConfig, String redisAddress, int redisPort, int redisTimeout, String redisPassword, String redisResult) {
        this(nameID, numID, address, port, 0, redisPoolConfig, redisAddress, redisPort, redisTimeout, redisPassword, redisResult);
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

    public String getRedisPoolConfig() { return redisPoolConfig; }

    public String getRedisAddress() { return redisAddress; }

    public int getRedisPort() { return redisPort; }

    public int getRedisTimeout() { return redisTimeout; }

    public String getRedisPassword() { return redisPassword; }

    public String getRedisResult() {
        if (redisResult == null) {
            return "";
        }
        else {
            return redisResult;
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
