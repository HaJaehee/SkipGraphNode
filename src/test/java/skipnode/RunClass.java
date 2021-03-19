package skipnode;

/* -------------------------------------------------------- */
/**
 File name : RunClass.java
 It is used as a main thread.
 Author : Jaehee Ha (jaehee.ha@kaist.ac.kr)
 Creation Date : 2021-03-12
 Version : 0.0.1

 Rev. history : 2021-03-17
 Version : 0.0.2
 Added a function createNodeTest() which contains YAML parser.
 Added logback.
 Added testcode with incremental features.
 Added hex id to decimal id features.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-03-19
 Version : 1.0.0
 Added Jedis features as a key-value storage system.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */

import lookup.ConcurrentLookupTable;
import lookup.LookupTableFactory;
import middlelayer.MiddleLayer;
import org.yaml.snakeyaml.Yaml;
import redis.clients.jedis.Jedis;
import underlay.Underlay;
import underlay.udp.UDPUnderlay;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Map;

public class RunClass {

    public static int LEVEL = 256;
    public static Jedis jedis = null;

    public static SkipNode createNodeTest(String fileName, int increment) {

        Yaml yparser = new Yaml();
        Reader yamlFile = null;
        try {
            yamlFile = new FileReader("C:\\Users\\loves\\Documents\\GitHub\\SkipGraphNode\\"+fileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        Map<String, Object> yamlMaps = yparser.load(yamlFile);
        //System.out.println(yamlMaps.get("underlay"));

        LookupTableFactory factory = new LookupTableFactory();
        ConcurrentLookupTable table = (ConcurrentLookupTable) factory.createDefaultLookupTable(LEVEL);

        String exampleHash = "05e0233cfa62dce67e36240f67f90f0c472a80f199599f65e7fcf97c08eb9a97";

        String nameIdAssignProto = yamlMaps.get("name_id_assignment_protocol").toString();
        String nameId = null;
        if (nameIdAssignProto.equals("none")) {
            nameId = new BigInteger(yamlMaps.get("name_id_value_self_assigned").toString(), 16).toString(2);
        }
        else if (nameIdAssignProto.equals("incremental")){
            BigInteger bigInt = new BigInteger(exampleHash, 16);
            bigInt = bigInt.add(BigInteger.valueOf(increment));
            nameId = bigInt.toString(2);
        }
        else {
            //TODO: TBD
        }
        nameId = String.format("%256s", nameId).replaceAll(" ", "0");


        String numIdAssignProto = yamlMaps.get("numerical_id_assignment_protocol").toString();
        BigInteger numId = BigInteger.valueOf(0);
        if (numIdAssignProto.equals("none")) {
            numId = new BigInteger(yamlMaps.get("numerical_id_value_self_assigned").toString(), 16);
        }
        else if (numIdAssignProto.equals("incremental")){
            numId = new BigInteger(exampleHash, 16);
            numId = numId.add(BigInteger.valueOf(increment));
        }
        else {
            //TODO: TBD
        }

        String address = null;
        try {
            address = Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        }
        String portAssignProto = yamlMaps.get("port_assignment_protocol").toString();
        int port = 0;
        if (portAssignProto.equals("none")){
            try {
                port = Integer.parseInt(yamlMaps.get("port_value_self_assigned").toString());
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return null;
            }
        }
        else if (portAssignProto.equals("incremental")) {
            port = 11099 + increment;
        }
        else {
            //TODO: TBD
        }

        String introducerAddress = yamlMaps.get("introducer_address").toString();
        if (introducerAddress.equals("none")) {
            introducerAddress = null;
        }

        int introducerPort = 0;
        try {
            introducerPort = Integer.parseInt(yamlMaps.get("introducer_port").toString());
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return null;
        }

        String storageType = yamlMaps.get("storage_type").toString();
        String storagePath = null;
        String redisAddress = null;
        int redisPort = 0;
        int redisTimeout = 0;
        String redisPassword = null;
        String redisPoolConfig = null;

        if (storageType.equals("none"))
        {
            // Do nothing.
        }
        else if (storageType.equals("path"))
        {
            storagePath = yamlMaps.get("storage_path").toString();
        }
        else if (storageType.equals("redis"))
        {
            redisPoolConfig = yamlMaps.get("redis_pool_config").toString();
            if (redisPoolConfig.equals("none")) {
                redisPoolConfig = null;
            }
            else {
                //TODO
            }
            redisAddress = yamlMaps.get("redis_address").toString();
            redisPort = Integer.parseInt(yamlMaps.get("redis_port").toString());
            redisTimeout = Integer.parseInt(yamlMaps.get("redis_timeout").toString());
            redisPassword = yamlMaps.get("redis_password").toString();
        }

        SkipNodeIdentity identity = new SkipNodeIdentity(nameId, numId, address, port, redisPoolConfig, redisAddress, redisPort, redisTimeout, redisPassword, null);

        SkipNode node = new SkipNode(identity, table);

        Underlay underlay = new UDPUnderlay();
        underlay.initialize(port);
        MiddleLayer middleLayer = new MiddleLayer(underlay, node);
        node.setMiddleLayer(middleLayer);
        underlay.setMiddleLayer(middleLayer);

        node.insert(introducerAddress, introducerPort);

        return node;
    }

    public static SkipNode createNodeTest(String fileName) {
        return createNodeTest(fileName, 0);
    }

    public static void main(String[] args) {

        String exampleHash = "05e0233cfa62dce67e36240f67f90f0c472a80f199599f65e7fcf97c08eb9a97";

        ArrayList<SkipNode> nodeList = new ArrayList<>();
        nodeList.add(createNodeTest("config1.yml"));
        for (int inc = 1 ; inc < 20 ; inc++) {
            nodeList.add(createNodeTest("config2.yml",inc));
        }

        System.out.println(nodeList.get(0).searchByNumID(new BigInteger(exampleHash, 16)).getRedisResult());
        System.out.println((nodeList.get(19).getNameID()));
        System.out.println((nodeList.get(0).searchByNameID("110100000")).result.getNameID());
        System.out.println((nodeList.get(0).searchByNameID("110100000")).result.getNameID().length());
        System.out.println((nodeList.get(19).searchByNameID("11010000000000000000000000000000")).result.getNameID().length());
        System.out.println((nodeList.get(0).searchByNumID(BigInteger.valueOf(3456)).getNumID().toString(16)));
        System.out.println((nodeList.get(19).searchByNameID("0000010111100000001000110011110011111010011000101101110011100110011111100011011000100100000011110110011111111001000011110000110001000111001010101000000011110001100110010101100110011111011001011110011111111100111110010111110000001000111010111001101010010111").result.getNumID().toString(16)));
        System.out.println((nodeList.get(0).searchByNumID(new BigInteger("5e0233cfa62dce67e36240f67f90f0c472a80f199599f65e7fcf97c08eb9a97", 16)).getRedisResult()));
    }
}
