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
 */
/* -------------------------------------------------------- */

import lookup.ConcurrentLookupTable;
import lookup.LookupTableFactory;
import middlelayer.MiddleLayer;
import org.yaml.snakeyaml.Yaml;
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

    public static int LEVEL = 32;

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

        String nameIdAssignProto = yamlMaps.get("name_id_assignment_protocol").toString();
        String nameId = null;
        if (nameIdAssignProto.equals("none")) {
            nameId = new BigInteger(yamlMaps.get("name_id_value_self_assigned").toString(), 16).toString(2);
        }
        else if (nameIdAssignProto.equals("incremental")){
            BigInteger bigInt = new BigInteger("99f3e5a40283578314474594a3d58d4aa53d62feb72ea57acdcb2431180f32fe18410fbc943dd5c8bdf2463e01ac7cfa320120863fca0c0439b9a68b5ca7dfdf24b5e22d2323944aebcefd18ed2e8aad8d933e200207f79cc2841edaa03ece36d38e5d4a6bd63827649f1db5495af13c73e3c5889c27cb47fa720f55cc28c0a13b1d33ceeaaba01541aa65d0925a16173a09b629b4949ebb17f702ddf9237baddb9fa51c83f00a89d95347ee2138c7f45b17f47ffefe0f261e5b2c5b0327ccd9bfc1edec840fb3f510a237cfb17b634a5120c67a1977eec3e258518183441c0d576838481f66c789e84c2ec84d7714fc6173b2552fa56f50a0db52a920cfb3cf5a199d1cf83286f4ab5f3c88b2d61d8a8dadedefcbc7bbcba71ab8b166fbcedec3b3cfa8b8beba1e70187bf3969750379a39d998ed46d86487ed6e01fecd1a11", 16);
            bigInt = bigInt.add(BigInteger.valueOf(increment));
            nameId = bigInt.toString(2);
            //nameId = nameId + String.format("%256s", binary).replaceAll(" ", "0");
        }
        else {
            //TODO: TBD
        }

        String numIdAssignProto = yamlMaps.get("numerical_id_assignment_protocol").toString();
        BigInteger numId = BigInteger.valueOf(0);
        if (numIdAssignProto.equals("none")) {
            numId = new BigInteger(yamlMaps.get("numerical_id_value_self_assigned").toString(), 16);
        }
        else if (numIdAssignProto.equals("incremental")){
            numId = new BigInteger("99f3e5a40283578314474594a3d58d4aa53d62feb72ea57acdcb2431180f32fe18410fbc943dd5c8bdf2463e01ac7cfa320120863fca0c0439b9a68b5ca7dfdf24b5e22d2323944aebcefd18ed2e8aad8d933e200207f79cc2841edaa03ece36d38e5d4a6bd63827649f1db5495af13c73e3c5889c27cb47fa720f55cc28c0a13b1d33ceeaaba01541aa65d0925a16173a09b629b4949ebb17f702ddf9237baddb9fa51c83f00a89d95347ee2138c7f45b17f47ffefe0f261e5b2c5b0327ccd9bfc1edec840fb3f510a237cfb17b634a5120c67a1977eec3e258518183441c0d576838481f66c789e84c2ec84d7714fc6173b2552fa56f50a0db52a920cfb3cf5a199d1cf83286f4ab5f3c88b2d61d8a8dadedefcbc7bbcba71ab8b166fbcedec3b3cfa8b8beba1e70187bf3969750379a39d998ed46d86487ed6e01fecd1a11", 16);
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


        SkipNodeIdentity identity = new SkipNodeIdentity(nameId, numId, address, port);


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

        ArrayList<SkipNode> nodeList = new ArrayList<>();
        nodeList.add(createNodeTest("config1.yml"));
        for (int inc = 1 ; inc < 20 ; inc++) {
            nodeList.add(createNodeTest("config2.yml",inc));
        }

        System.out.println((nodeList.get(19).getNameID()).toString());
        System.out.println((nodeList.get(19).searchByNameID("110100000")).result.getNameID());
        System.out.println((nodeList.get(19).searchByNameID("11010000000000000000000000000000")).result.getNameID());
        System.out.println((nodeList.get(0).searchByNumID(BigInteger.valueOf(3456)).getNumID().toString(16)));
    }
}
