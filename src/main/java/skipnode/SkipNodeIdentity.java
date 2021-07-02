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

 Rev. history : 2021-03-26
 Version : 1.0.4
 Removed unused Jedis features.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-07-02
 Version : 1.2.1
 commonBits() is modified. 
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
    private final String resourceQueryResult;

    // Denotes the lookup table version.
    public int version;

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, int version, String storagePath, String resourceQueryResult) {
        this.nameID = nameID;
        this.numID = numID;
        this.address = address;
        this.port = port;
        this.version = version;
        this.storagePath = storagePath;
        this.resourceQueryResult = resourceQueryResult;
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port, String storagePath, String resourceQueryResult){
        this(nameID, numID, address, port, 0, storagePath, resourceQueryResult);
    }

    public SkipNodeIdentity(String nameID, BigInteger numID, String address, int port){
        this(nameID, numID, address, port, 0, null, null);
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

    public String getStoragePath() {
        return storagePath;
    }

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

//        if(name1.length() != name2.length())
//            return -1;
        if(name1.length() < name2.length())
            return -1;

        int i = 0;
        while(i < name2.length() && name1.charAt(i) == name2.charAt(i)) i++;
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
