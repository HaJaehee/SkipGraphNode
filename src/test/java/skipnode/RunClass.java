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

 Rev. history : 2021-03-22
 Version : 1.0.1
 Modified Jedis features as a key-value storage system.
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Map;

public class RunClass {

    public static int LEVEL = 32;
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
        boolean isUsingRedis = false;

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
            isUsingRedis = true;
        }

        SkipNodeIdentity identity = new SkipNodeIdentity(nameId, numId, address, port,null, null);

        SkipNode node = new SkipNode(identity, table, isUsingRedis);

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

    public static void main(String[] args) throws NoSuchAlgorithmException {

        String exampleHash = "05e0233cfa62dce67e36240f67f90f0c472a80f199599f65e7fcf97c08eb9a97";

        ArrayList<SkipNode> nodeList = new ArrayList<>();
        nodeList.add(createNodeTest("config1.yml"));
        for (int inc = 1 ; inc < 20 ; inc++) {
            nodeList.add(createNodeTest("config2.yml",inc));
//            System.out.println(nodeList.get(inc).getNodeListAtHighestLevel().size());
        }

//        System.out.println(nodeList.get(0).getResourceByNumID(new BigInteger(exampleHash, 16)));
//        System.out.println((nodeList.get(19).getNameID()));
//        System.out.println((nodeList.get(0).searchByNameID("110100000")).result.getNameID());
//        System.out.println((nodeList.get(0).searchByNameID("110100000")).result.getNameID().length());
//        System.out.println((nodeList.get(19).searchByNameID("11010000000000000000000000000000")).result.getNameID().length());
//        System.out.println((nodeList.get(0).searchByNumID(BigInteger.valueOf(3456)).getNumID().toString(16)));
//        System.out.println((nodeList.get(19).getNumID().toString(16)));
//        System.out.println(nodeList.get(10).getResourceByNumID(new BigInteger("5e0233cfa62dce67e36240f67f90f0c472a80f199599f65e7fcf97c08eb9a97", 16)));
//        System.out.println(nodeList.get(10).searchByNumID(new BigInteger("5e0233cfa62dce67e36240f67f90f0c472a80f199599f65e7fcf97c08eb9a97", 16)).getNumID().toString(16));

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String hello = "hello";
        byte[] helloBytes = SkipNode.sha256(hello);
        System.out.println(SkipNode.bytesToHex(helloBytes));
        nodeList.get(0).storeResourceByResourceKey(SkipNode.bytesToHex(helloBytes),"hello");

        System.out.println("how are you? "+ nodeList.get(0).getResourceByResourceKey(SkipNode.bytesToHex(helloBytes)));

//        ArrayList<SkipNodeIdentity> list = nodeList.get(10).getNodeListByNameID("00000101111000000010001100111100");
//        list = nodeList.get(19).getNodeListAtHighestLevel();
//        for (SkipNodeIdentity s: list) {
//            System.out.println(s.getNameID());
//        }
//
//        System.out.println(nodeList.get(19).getFirstNodeAtHighestLevel().getNumID().toString(16));
//        for (SkipNode s : nodeList) {
//            System.out.println(s.getNodeListAtHighestLevel().size());
//        }
    }
}
