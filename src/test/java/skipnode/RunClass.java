package skipnode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Map;

import lookup.ConcurrentLookupTable;
import lookup.LookupTableFactory;
import middlelayer.MiddleLayer;
import org.yaml.snakeyaml.Yaml;
import underlay.Underlay;
import underlay.udp.UDPUnderlay;

public class RunClass {

    public static int LEVEL = 4;

    public static SkipNode createNodeTest(String fileName) {

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
            nameId = yamlMaps.get("name_id_value_self_assigned").toString();
        }
        else {
            //TODO: TBD
        }

        String numIdAssignProto = yamlMaps.get("numerical_id_assignment_protocol").toString();
        int numId = 0;
        if (numIdAssignProto.equals("none")) {
            numId = Integer.parseInt(yamlMaps.get("numerical_id_value_self_assigned").toString());
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
        int port = 0;
        try {
            port = Integer.parseInt( yamlMaps.get("port").toString());
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return null;
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

        System.out.println(node.getNumID());

        return node;
    }

    public static void main(String[] args) {

        SkipNode node1 = createNodeTest("config1.yml");
        if (node1 == null) {
            return;
        }
        SkipNode node2 = createNodeTest("config2.yml");
        if (node2 == null) {
            return;
        }
        SkipNode node3 = createNodeTest("config3.yml");
        if (node3 == null) {
            return;
        }

        System.out.println((node3.searchByNameID("1103")).result.getNameID());
        System.out.println((node2.searchByNumID(3456).getNumID()));
    }
}
