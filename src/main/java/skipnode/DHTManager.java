package skipnode;

/*
 * Update 2020/03/04
 *              Update history: LM-MEC(2019) v1.3.9
 *			Switch IPs are rollback.
 *
 * Update 2020/03/13
 *              Update history: LM-MEC(2019) v2.0.0
 *			Added log on/off and DHT on/off.
 */

/* -------------------------------------------------------- */
/**
 File name : DHTManager.java
 Rev. history : 2021-04-02
 Version : 1.1.1
 Added this class.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-06-01
 Version : 1.1.1
 Modified to use SkipGraph.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-06-10
 Version : 1.1.2
 First prototype implementation.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-06-11
 Version : 1.1.3
 Bootstrap implementation is done.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)


 Rev. history : 2021-06-11
 Version : 1.1.4
 Functions, such as store()(put()) and get()(search()), implementation is done.
 Testing store() is done.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)


 Rev. history : 2021-06-13
 Version : 1.1.5
 Testing simple locality-aware approach is done.
 Operations OPCODE_INFORM_CONNECTION, OPCODE_GET_HASH tests are passed.
 Switch number and ip list including function is implemented.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-06-14
 Version : 1.1.6
 Removed useless static variables.
 Simple 80 virtual node test is passed.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-06-19
 Version : 1.1.7
 Every node MUST be specified by port number. Not Address and port.
 Added DHTManager initialization lock.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 Rev. history : 2021-07-01
 Version : 1.2.0
 Separated IP address aware DHT network and locality aware DHT network.
 Implemented recursively search algorithm by shortening tails of locality ID prefix.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 TODO
 store(put), get(search),
 More sophisticated implementation of get()
 More sophisticated implementation of store()
    - Where is the data being stored?
 AR_LIST is actually needed?
 OPCODE_INFORM_CONNECTION에서 자기의 locality에 저장하는 건 okay.
 OPCODE_GET_HASH, OPCODE_GET_IP, OPCODE_GET_IPPORT 이 때도 query를 수행한 locality에 복제해야 하는데
 이 부분을 구현해야겠구만.
 get 했을 때 LID_LIST에 복제해서 저장해야 하는 이슈 ---- 누가??
 store 할 때 한 번 get하고 LID_LIST에 복제해서 저장해야 하는 이슈 ---- 누가??

 */
/* -------------------------------------------------------- */

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lookup.ConcurrentLookupTable;
import lookup.LookupTableFactory;
import middlelayer.MiddleLayer;
import underlay.Underlay;
import underlay.tcp.TCPUnderlay;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;


public final class DHTManager {

    //210614 removed static
    public  ArrayList<String> swIPAddrList = new ArrayList<String>(Arrays.asList("10.0.10.1","10.0.20.1","10.0.30.1","10.0.40.1","10.0.50.1","10.64.0.1"));
    public  Short[] edgeSWNum = {1,2,3,0,0,6};
    public  ArrayList<Short> edgeSWList = new ArrayList<Short>(Arrays.asList(edgeSWNum));
    public  int swCount = swIPAddrList.size();

    //public static Channel clientCh;

    //210614 removed static
    public  int PORT;
    public  int nodeIndex;
    public  boolean justReset = false;
    public  DHTServer skipGraphServer = null;
    public  boolean logging = false;
    public  boolean logFileOut = true;
    public  boolean onEmulator = false;
    public  String[] input = null;
    //public static int skipGraphServerPort = 8468;
    public  int ovsPort = 9999;
    public  Bootstrap bClient;
    public  final int DEFAULT_DHT_PORT = 21100;
    public  final int DEFAULT_DHT_MNG_PORT = 40001;
    public  int edgeNum = 0;
    public  int dhtNum = 0;
    public  String localityID = null;

    public static String IPADDR = null;

    public  HashMap<String, String> kvMap= null;

    private String[] args = null;

    private volatile boolean serverInitiationLock = false;


    public DHTManager (String[] args){
        this.args = args;
        serverInitiationLock = true;
    }

    public boolean isLogging() {
        return logging;
    }

    public boolean isLocked() { return serverInitiationLock; }

    private String getLocalIP (String interfaceName) throws Exception{
        String ip = null;
        NetworkInterface networkInterface = NetworkInterface.getByName(interfaceName);
        if (networkInterface != null) {
            Enumeration<InetAddress> inetAddress = networkInterface.getInetAddresses();
            InetAddress currentAddress;
            while(inetAddress != null && inetAddress.hasMoreElements()) {
                currentAddress = inetAddress.nextElement();
                if (currentAddress instanceof Inet4Address && !currentAddress.isLoopbackAddress()) {
                    ip = currentAddress.toString().replaceAll("/", "");
                    break;
                }
            }
        }
        return ip;
    }

    public void run() throws Exception {

        //input = new String[] {"0"}; // For test

        String ip = getLocalIP("eth0");
        if (ip == null) {
            ip = getLocalIP("enp6s0");
        }
        if (ip == null) {
            ip = "192.168.0.4";
        }

        this.IPADDR = ip;


        kvMap = new HashMap<String, String>();
        EventLoopGroup groupClient = new NioEventLoopGroup();
        bClient = new Bootstrap();
        bClient.group(groupClient)
                .channel(NioDatagramChannel.class)
                .handler(new ClientHandler());

        if (input == null) {
            if ((args.length == 3) ||
                    (args.length == 4 && args[3].equals("logging"))){
                if(args.length == 4 && args[3].equals("logging")) {
                    logging = true;
                }
                System.out.println("The First node begins.");
                //First node constructor requires its eth0 IP address, port number, and Locality ID.
                //Also, first node constructor requires STATIC key-value Map object.
                //TODO
                edgeNum = Integer.parseInt(args[0]);
                dhtNum = Integer.parseInt(args[2]);
                localityID = args[1];
                if (localityID.equals("none"))
                    localityID = null;
                System.out.println("IP Addr: "+this.IPADDR);
                skipGraphServer = new DHTServer(ip, DEFAULT_DHT_PORT+(edgeNum*100)+dhtNum, localityID, kvMap,
                        logging, logFileOut, edgeNum, dhtNum, bClient);
                System.out.println("Insertion is done.");

            }
            else if ((args.length == 2 && args[1].equals("reset")) ||
                    (args.length == 3 && args[1].equals("reset") && args[2].equals("logging"))) {
                if(args.length == 3 && args[2].equals("logging")) {
                    logging = true;
                }
                edgeNum = Integer.parseInt(args[0]);
                dhtNum = 0;
                System.out.println("Reset switch.");
                justReset = true;

            }
            else if ((args.length == 5) ||
                    (args.length == 6 && args[5].equals("logging"))) {
                if(args.length == 6 && args[5].equals("logging")) {
                    logging = true;
                }
                System.out.println("Connect to introducer node.");
                //The others node constructor requires the introducer's IP address, port number, and its eth0 IP address, port number, and Locality ID.
                //Also, the others node constructor requires STATIC key-value Map object.
                //TODO

                String introducerIP = args[3];
                int introducerPort = Integer.parseInt(args[4]);
                edgeNum = Integer.parseInt(args[0]);
                dhtNum = Integer.parseInt(args[2]);
                localityID = args[1];
                if (localityID.equals("none"))
                    localityID = null;
                System.out.println("IP Addr: "+this.IPADDR);
                skipGraphServer = new DHTServer(introducerIP, introducerPort, ip, DEFAULT_DHT_PORT+(100*edgeNum)+dhtNum,
                        localityID, kvMap, logging, logFileOut, edgeNum, dhtNum, bClient);
                System.out.println("Bootstrap is done.");

            }
            else{
                if(logging){
                    System.out.println("Ambiguous Input.");
                    System.out.println("Usage: JAVA DHTManager [switch num] [locality id] [DHT node num]");
                    System.out.println("Usage: JAVA DHTManager [switch num] [locality id] [DHT node num] logging");
                    System.out.println("Usage: java DHTManager [switch num] [locality id] [DHT node num] [introducer ip] [introducer port] ");
                    System.out.println("Usage: java DHTManager [switch num] [locality id] [DHT node num] [introducer ip] [introducer port] logging");
                    System.out.println("Usage: java DHTManager [switch num] reset");
                    System.out.println("Usage: java DHTManager [switch num] reset logging");
                    System.out.println("0 <= DHT node num < 50");
                }
                return;
            }
            nodeIndex = (100*edgeNum) + dhtNum;
        }
//        else { // input != null, only for test
//            if (input.length == 1) {
//                if(logging)System.out.println("The First node begins.");
//                System.out.println("The First node begins.");
//                //First node constructor requires its eth0 IP address, port number, and Locality ID.
//                //Also, first node constructor requires STATIC key-value Map object.
//                //TODO
//                skipGraphServer = new DHTServer(ip, 11099, null, kvMap);
//                if(logging)System.out.println("Bootstrap is done.");
//            }
//            else if (input.length == 3) {
//                System.out.println("Connect to introducer node.");
//                //The others node constructor requires the introducer's IP address, port number, and its eth0 IP address, port number, and Locality ID.
//                //Also, the others node constructor requires STATIC key-value Map object.
//                //TODO
//                skipGraphServer = new DHTServer(args[1], Integer.parseInt(args[2]), ip, 11099 + Integer.parseInt(args[0]) - 1, null, kvMap);
//                System.out.println("Bootstrap is done.");
//            }
//            nodeIndex = Integer.parseInt(input[0]) - 1;
//        }

        if (onEmulator) {
            String dir = System.getProperty("user.dir");
            if (dir.contains("target")) {
                dir = dir+"/..";
            }
            File file = new File(dir + "/src/main/java/skipnode/korea-100-router-node-num-ip-list.txt");

            swIPAddrList = new ArrayList<String>();
            edgeSWList = new ArrayList<Short>();
            swCount = 0;

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] numIP = line.split(",");
                    edgeSWList.add(Short.parseShort(numIP[0]));
                    swIPAddrList.add(numIP[1]);
                }
                if (swIPAddrList.size() > 0 && edgeSWList.size() > 0) {
                    swCount = swIPAddrList.size();
                    if (logging)
                        System.out.println("Edge node number and IP is loaded. First node: (" + edgeSWList.get(0) + ", " + swIPAddrList.get(0) + ")");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Thread.sleep(1000);

        //clientCh = bClient.bind(0).sync().channel();

        // This port number is used for an UDP socket server.
        // DHTManagerHandler extends SimpleChannelInboundHandler which is used for an UDP socket server.
        // DHTManagerHandler communicates with its OvS kernel module.
        PORT = DEFAULT_DHT_MNG_PORT + nodeIndex;
        System.out.println("DHT Manager listen: "+PORT);

        Bootstrap b = new Bootstrap();
        EventLoopGroup group = new NioEventLoopGroup();
        try{
            b.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new DHTManagerHandler(nodeIndex, justReset, logging, logFileOut, swIPAddrList, edgeSWList, swCount, PORT, bClient, skipGraphServer));
            serverInitiationLock = false;
            b.bind(PORT).sync().channel().closeFuture().await();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally{
            group.shutdownGracefully();
        }


    }
}


class DHTManagerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final boolean logging;
    private final boolean logFileOut;
    private final Random rand = new Random();

    private static final byte OPCODE_BOOTUP = 0;
    private static final byte OPCODE_GET_HASH = 1;
    private static final byte OPCODE_GET_IP = 2;
    private static final byte OPCODE_INFORM_CONNECTION = 3;
    private static final byte OPCODE_APP_MOBILITY = 4;
    private static final byte OPCODE_CTN_MOBILITY = 5;
    private static final byte OPCODE_GET_IPPORT = 6;
    //private static final byte OPCODE_TOGGLE_LOGGING = 12;
    private static final byte OPCODE_RESET_SWITCH = 13;

    private static final byte OPCODE_SET_SWTYPE = 0;
    private static final byte OPCODE_QUERIED_HASH = 1;
    private static final byte OPCODE_QUERIED_IP = 2;
    private static final byte OPCODE_UPDATE_IP = 3;
    private static final byte OPCODE_NEW_APP = 4;
    private static final byte OPCODE_NEW_CTN = 5;
    private static final byte OPCODE_DUMP_MOBIL_INFO = 102;

    private static final int LM_HDR_LENGTH = 32;

    private static final int DUMP_NUM = 1000000;

    private final ArrayList<String> swIPAddrList;
    private final ArrayList<Short> edgeSWList;
    private final int swCount;
    private final int PORT;
    private final Bootstrap bClient;
    private final int nodeIndex;
    private final DHTServer skipGraphServer;


    public DHTManagerHandler(int nodeIndex, boolean justReset, boolean logging, boolean logFileOut, ArrayList<String> swIPAddrList, ArrayList<Short> edgeSWList, int swCount, int PORT, Bootstrap bClient, DHTServer skipGraphServer) throws InterruptedException{
        super();
        this.logging = logging;
        this.logFileOut = logFileOut;
        this.PORT = PORT;
        this.swIPAddrList = swIPAddrList;
        this.edgeSWList = edgeSWList;
        this.swCount = swCount;
        this.bClient = bClient;
        this.nodeIndex = nodeIndex;
        this.skipGraphServer = skipGraphServer;



        //210611 disabled
        /*
        int sendBufLength = 6 + 2*DHTManager.swCount ;
        byte[] sendBuf = new byte[sendBufLength];
        if (justReset) {
            sendBuf[0] = OPCODE_RESET_SWITCH;
        } else {
            sendBuf[0] = OPCODE_SET_SWTYPE;
        }
        sendBuf[1] = (byte) DHTManager.edgeSWList[nodeIndex];//switch num
        sendBuf[2] = (byte) 0x02;//switch type

        byte[] lengthBytes = ByteBuffer.allocate(2).putShort((short) DHTManager.swCount).array();
        sendBuf[3] = lengthBytes[0];
        sendBuf[4] = lengthBytes[1];
        if (logging) {
            sendBuf[5] = (byte)1;
        } else {
            sendBuf[5] = (byte)0;
        }
        //Appends edge switch numbers to the byte array

        for (int i = 0; i < DHTManager.swCount; i++) {
            if (DHTManager.edgeSWList.get(i) != 0) {
                byte[] swBytes = ByteBuffer.allocate(2).putShort((short) (DHTManager.edgeSWList.get(i) - 1)).array();
                sendBuf[6 + i * 2] = swBytes[0];
                sendBuf[6 + i * 2 + 1] = swBytes[1];
            }
        }



        if (logging) {
            System.out.printf("wake up signal to ovs:");

            for (int i = 0; i < sendBufLength; i++) {
                System.out.printf("%02x", sendBuf[i]);
            }
            System.out.println();
        }

        Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
        //wake up signal to ovs
        clientCh.writeAndFlush(
                new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress("localhost", DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);

        if (justReset) {
            Thread.sleep(2000);
            System.exit(0);
        }
        */
        System.out.println("Port "+PORT+" is opened.");
    }

    int fromByteArray(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }
    int iptoint(String ipAddr) throws UnknownHostException{
        int result = 0;
        for (byte b: ipStringToByte(ipAddr))
        {
            result = result << 8 | (b & 0xFF);
        }
        return result;
    }
    byte[] ipStringToByte(String ipAddr) throws UnknownHostException{
        InetAddress ipAddress= null;
        ipAddress= InetAddress.getByName(ipAddr);
        return ipAddress.getAddress();
    }
    long ntohl(long network){
        network = network & 0xFFFFFFFF;
        long b1 = network & 0xFF000000;
        long b2 = network & 0x00FF0000;
        long b3 = network & 0x0000FF00;
        long b4 = network & 0x000000FF;
        network = b4 << 24 | b3 << 8 | b2 >> 8 | b1 >> 24;
        return network;
    }
    int ntohs(int network){
        network = network & 0xFFFF;
        int b1 = network & 0xFF00;
        int b2 = network & 0x00FF;
        network = b2 << 8 | b1 >> 8;
        return network;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws UnknownHostException, InterruptedException, Exception {
        //if(logging)System.err.println(packet.content().toString(CharsetUtil.UTF_8));
        ByteBuf payload = packet.content();

        //if(logging)System.out.println("[Node "+DHTManager.nodeNum+"] Received Message: "+payload.toString());

        String sessionID = "";
        StringBuffer log = new StringBuffer();


        if(logFileOut) {
            // log file append
            File file = new File("log-"+DHTManager.IPADDR+".csv");
            if(!file.exists()) {
                FileWriter fw = null;
                try {
                    fw = new FileWriter("log-" + DHTManager.IPADDR + ".csv", true);
                    fw.write("sessionID,opCode,startTime,endTime,diff\n");
                    fw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        byte opCode = payload.readByte();
        byte switchNum = payload.readByte();
        byte switchIndex = (byte)(switchNum-1);
        //nHost is originally 4byte value
        long nHost = ntohl(payload.readUnsignedInt());
        int nHostInt = (int)nHost & 0xFFFFFFFF;
        byte[] byteHostIP = ByteBuffer.allocate(4).putInt(nHostInt).array();

        String strIP = String.format("%d.%d.%d.%d",(nHost & 0xFF), (nHost >> 8 & 0xFF), (nHost >> 16 & 0xFF), (nHost >> 24 & 0xFF));
        if(logging)System.out.printf("[Node %d] Received Message: opCode=%d,  switchNum=%d, IP=%s, SWIP = %s\n",nodeIndex, opCode, switchNum, strIP, "127.0.0.1");

        if (opCode == OPCODE_BOOTUP){
            if(logging)System.out.println("opCode 0: show edge switchs list.");
            if(logging)System.out.println("opCode 0 will be supported soon.");

        } else if (opCode == OPCODE_DUMP_MOBIL_INFO){
            if(logging)System.out.println("opCode 102: Dump many Host IPs into DHT server with Object IDs.");
            String ipField[] = {"10","0","0","0"};
            int inc = 0;
            for (int ip1 = switchNum*16 ; ip1 < (switchNum+1)*16 && inc < DUMP_NUM ; ip1++) {
                ipField[1] = ip1+"";
                for (int ip2 = 0 ; ip2 < 256 && inc < DUMP_NUM  ; ip2++) {
                    ipField[2] = ip2+"";
                    for (int ip3 = 3 ; ip3 < 256 && inc < DUMP_NUM ; ip3++, inc++) {
                        ipField[3] = ip3+"";

                        StringBuilder jsonString = new StringBuilder();
                        String strData = "";
                        for (int i = 0; i < ipField.length; i++)
                            strData += String.format("%02x", Integer.parseInt(ipField[i]));


                        jsonString.append("{ \"" + this.skipGraphServer.VISITING_IP + "\" : \"" + strData + "\",");


                        strData = "";
                        String[] switchIP = DHTManager.IPADDR.split("\\.");
                        for (int i = 0; i < switchIP.length; i++)
                            strData += String.format("%02x", Integer.parseInt(switchIP[i]));

                        jsonString.append("\"" + this.skipGraphServer.ES_IP + "\" : \"" + strData + "\"");

                        if (this.skipGraphServer.localityID != null) {
                            jsonString.append(", \"" + this.skipGraphServer.LOCALITY_ID + "\" : \"" + this.skipGraphServer.localityID + "\",");
                            jsonString.append(", \"" + this.skipGraphServer.LID_LIST + "\":[\"" + this.skipGraphServer.localityID + "\"]}");
                        }
                        else {
                            jsonString.append("}");
                        }

                        //TODO LID_LIST
                        String firstSHA = this.skipGraphServer.sha256(ipField[0]+"."+ipField[1]+"."+ipField[2]+"."+ipField[3]);

                        //TODO
                        if (logging) System.out.println("key: " + firstSHA + ", data: " + jsonString.toString());
                        if (this.skipGraphServer.localityAwareNode != null && this.skipGraphServer.localityID != null) {
                            this.skipGraphServer.localityAwareNode.storeResource(firstSHA, jsonString.toString());
                        }
                        if (this.skipGraphServer.ipAddressAwareNode != null) {
                            this.skipGraphServer.ipAddressAwareNode.storeResource(firstSHA, jsonString.toString());
                        }
                    }
                }
            }
        }


        /*else if (opCode == OPCODE_GET_HASH){  //deprecated by jaehee 170414
        	if(logging)System.out.println("opCode 1: get Object ID from DHT server with digested IP");
        	//make Object ID and query to DHT table
			MessageDigest mDigest = MessageDigest.getInstance("SHA-256");
			byte[] strDig = mDigest.digest(strIP.getBytes());
			if(logging)System.out.println("Hashed IP: "+strDig+", length: "+strDig.length);
			DHTManager.skipGraphServer.get(opCode, strIP, switchNum, byteHostIP, strDig);
			//---------------client example
//			String opCode = OPCODE_GET_HASH;
//			String swNum = "02";
//			String hostIP = "0a000a03";
//			String strInput = opCode+swNum+hostIP;
        }*/ else if (opCode == OPCODE_GET_IP){
            if(logging)System.out.println("opCode 2: get Host IP from DHT server with Object ID.");

            //copy payload(Object ID) to strDig byte array and query to DHT table
            byte[] strDig = new byte[LM_HDR_LENGTH]; //Jaehee modified 160720

            for (int i = 0;i < LM_HDR_LENGTH;i++){ //Jaehee modified 160720
                strDig[i] = payload.readByte();

            }
            if(logging){
                System.out.println("Object ID: ");

                for (int i = 0;i < LM_HDR_LENGTH;i++){
                    System.out.printf("%02x",(strDig[i]&0xff));
                }
                System.out.println();
            }
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < strDig.length; i++) {
                sb.append(Integer.toString((strDig[i] & 0xff) + 0x100, 16)
                        .substring(1));
            }

            long startTime = 0;
            long endTime = 0;
            if(logFileOut) {
                //TODO session ID is weired
                sessionID = String.format("%08x",rand.nextInt(2147483647));
                if(logging) System.out.println("New session "+sessionID);
                log.append(sessionID+",");
                startTime = System.currentTimeMillis();
                log.append("OPCODE_GET_IP,"+startTime+",");
            }
            skipGraphServer.get(opCode, sb.toString(), switchNum, byteHostIP, strDig);
            if(logFileOut) {
                endTime = System.currentTimeMillis();
                log.append(endTime+","+(endTime-startTime)+"\n");
            }

            //---------------client example
//			String opCode = OPCODE_GET_IP;
//			String swNum = "02";
//			String esIP = "0a001401";
//			String hash = "";
//			try {
//				hash = sha256("10.0.10.3");
//			} catch (NoSuchAlgorithmException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			String strInput = opCode+swNum+esIP+hash;



        } else if (opCode == OPCODE_INFORM_CONNECTION){
            if(logging)System.out.println("opCode 3: store DHT server.");

            //byte[] byte_switch_ip = new byte[4];
            String strSWIP = swIPAddrList.get(switchIndex);
            int nSwitchIP = iptoint(strSWIP) & 0xFFFFFFFF;
            byte[] reverseByteSwitchIP =  ByteBuffer.allocate(4).putInt(nSwitchIP).array();
            byte[] byteSwitchIP = new byte[4];
            for (int i = 0;i < 4;i++) {
                byteSwitchIP[3-i] = reverseByteSwitchIP[i];
            }

            if(logging)System.out.printf("[Node %d] Storing the pair: (Host IP=%s, Switch IP=%s)\n", nodeIndex,strIP,strSWIP);

            long startTime = 0;
            long endTime = 0;
            if(logFileOut) {
                //TODO session ID is weired
                sessionID = String.format("%08x",rand.nextInt(2147483647));
                if(logging) System.out.println("New session "+sessionID);
                log.append(sessionID+",");
                startTime = System.currentTimeMillis();
                log.append("OPCODE_INFORM_CONNECTION,"+startTime+",");
            }
            JsonObject jobj = skipGraphServer.get(strIP);
            skipGraphServer.store(strIP, byteHostIP, byteSwitchIP, jobj);
            if(logFileOut) {
                endTime = System.currentTimeMillis();
                log.append(endTime+","+(endTime-startTime)+"\n");
            }

            byte[] sendBuf = new byte[42];
            sendBuf[0] = OPCODE_UPDATE_IP;
            for (int i = 0;i < 4;i++){
                sendBuf[2 + (3-i)] = byteHostIP[i];
                sendBuf[6 + (3-i)] = byteSwitchIP[i];
            }

            MessageDigest mDigest = MessageDigest.getInstance("SHA-256");
            byte[] strDig = mDigest.digest(strIP.getBytes());

            for (int i = 0; i < LM_HDR_LENGTH;i++){//Jaehee modified 170329
                sendBuf[10+i]=  strDig[i];
            }

            if(logging){
                System.out.println("Object ID: ");

                for (int i = 0;i < LM_HDR_LENGTH;i++){
                    System.out.printf("%02x",(strDig[i]&0xff));
                }
                System.out.println();
            }

            if(logging)System.out.println("Suppose to update the caches.");
            //210613 update the cache function is disabled.
            // Update the cache of the edges in the edge list.
//            for (int i = 0;i < DHTManager.swCount;i++){
//                if (DHTManager.edgeSWList.get(i) != 0 && DHTManager.edgeSWList.get(i) != switchNum){
//
//                    byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList.get(i)).array();
//                    sendBuf[1] = swByte[1];
//                    int swListIndex = DHTManager.edgeSWList.get(i)-1;
//
//
//                    Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
//                    clientCh.writeAndFlush(
//                            new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList.get(swListIndex),DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
//                }
//            }
//            byte[] swByte = ByteBuffer.allocate(2).putShort(switchNum).array();
//            sendBuf[1] = swByte[1];
//            if(logging)System.out.printf("receiving SW IP="+"127.0.0.1"+",port="+DHTManager.ovsPort+".\n");
//
//            Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
//
//            clientCh.writeAndFlush(
//                    new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress("127.0.0.1",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);


//			String opCode = OPCODE_INFORM_CONNECTION;
//			String swNum = "02";
//			String hostIP = "0a000a02";
//			String swIP = "0a000a01";
//			String strInput = opCode+swNum+hostIP+swIP;

        } else if (opCode == OPCODE_APP_MOBILITY){
            if(logging)System.out.println("opCode 4: application mobility.");

            byte[] byteHomeTargetHostIP = byteHostIP;
            String strHomeTargetHostIP = strIP;

            byte[] bytePortNumber = {payload.readByte(),payload.readByte()};
            String strPortNumber = ":"+((bytePortNumber[1] & 0xFF) + ((bytePortNumber[0] & 0xFF)*0x100));

            long nVisitingESIP = ntohl(payload.readUnsignedInt());
            int nVisitingESIPInt = (int)nVisitingESIP & 0xFFFFFFFF;
            byte[] byteVisitingESIP = ByteBuffer.allocate(4).putInt(nVisitingESIPInt).array();

            long nVisitingTargetHost = ntohl(payload.readUnsignedInt());
            int nVisitingTargetHostInt = (int)nVisitingTargetHost & 0xFFFFFFFF;
            byte[] byteVisitingTargetHostIP = ByteBuffer.allocate(4).putInt(nVisitingTargetHostInt).array();
            String strVisitingTargetHostIP = String.format("%d.%d.%d.%d",(nVisitingTargetHost & 0xFF), (nVisitingTargetHost >> 8 & 0xFF), (nVisitingTargetHost >> 16 & 0xFF), (nVisitingTargetHost >> 24 & 0xFF));

            if(logging) {
                String strVisitingESIP = String.format("%d.%d.%d.%d",(nVisitingESIP & 0xFF), (nVisitingESIP >> 8 & 0xFF), (nVisitingESIP >> 16 & 0xFF), (nVisitingESIP >> 24 & 0xFF));
                System.out.printf("[Node %d] Storing the pair: (Original Host IP=%s, Port Number=%s, New Host IP=%s, New Edge Switch IP=%s)\n", nodeIndex, strHomeTargetHostIP, strPortNumber, strVisitingTargetHostIP, strVisitingESIP);
            }


            long startTime = 0;
            long endTime = 0;
            if(logFileOut) {
                //TODO session ID is weired
                sessionID = String.format("%08x",rand.nextInt(2147483647));
                if(logging) System.out.println("New session "+sessionID);
                log.append(sessionID+",");
                startTime = System.currentTimeMillis();
                log.append("OPCODE_APP_MOBILITY,"+startTime+",");
            }
            JsonObject jobj = skipGraphServer.get(strHomeTargetHostIP);
            skipGraphServer.store(strHomeTargetHostIP+strPortNumber, strVisitingTargetHostIP+strPortNumber, byteVisitingTargetHostIP, byteVisitingESIP, byteHomeTargetHostIP, jobj);
            if(logFileOut) {
                endTime = System.currentTimeMillis();
                log.append(endTime+","+(endTime-startTime)+"\n");
            }

            byte[] sendBuf = new byte[48];

            sendBuf[0] = OPCODE_NEW_APP;
            for (int i = 0;i < 4;i++){
                sendBuf[2 + (3-i)] = byteHomeTargetHostIP[i];
                sendBuf[6 + (3-i)] = byteVisitingESIP[i];
                sendBuf[10 + (3-i)] = byteVisitingTargetHostIP[i];
            }
            for (int i = 0;i < 2;i++){
                sendBuf[14 + i] = bytePortNumber[i];
            }

            MessageDigest mDigest = MessageDigest.getInstance("SHA-256");
            byte[] strDig = mDigest.digest((strHomeTargetHostIP+strPortNumber).getBytes());

            for (int i = 0; i < LM_HDR_LENGTH;i++){//Jaehee modified 170329
                sendBuf[16+i]=  strDig[i];
            }

            if(logging){
                System.out.println("Object ID: ");

                for (int i = 0;i < LM_HDR_LENGTH;i++){
                    System.out.printf("%02x",(strDig[i]&0xff));
                }
                System.out.println();
            }

            if(logging)System.out.println("Suppose to update the caches.");
            //210613 update the cache function is disabled.
            // Update the cache of the edges in the edge list.
//            for (int i = 0;i < DHTManager.swCount;i++){
//                if (DHTManager.edgeSWList.get(i) != 0 && DHTManager.edgeSWList.get(i) != switchNum){
//
//                    byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList.get(i)).array();
//                    sendBuf[1] = swByte[1];
//                    int swListIndex = DHTManager.edgeSWList.get(i)-1;
//                    if(logging)System.out.printf("receiving SW IP="+DHTManager.swIPAddrList.get(swListIndex)+",port="+DHTManager.ovsPort+".\n");
//
//
//                    Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
//
//                    clientCh.writeAndFlush(
//                            new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList.get(swListIndex),DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
//                }
//            }
//            byte[] swByte = ByteBuffer.allocate(2).putShort(switchNum).array();
//            sendBuf[1] = swByte[1];
//            if(logging)System.out.printf("receiving SW IP="+"127.0.0.1"+",port="+DHTManager.ovsPort+".\n");
//
//
//            Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
//            clientCh.writeAndFlush(
//                    new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress("127.0.0.1",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);

            //old code
			/*
			for (int i = 0;i < 4;i++){
                sendBuf[2 + (3-i)] = byteVisitingTargetHostIP[i];
                sendBuf[6 + (3-i)] = byteVisitingESIP[i];
                sendBuf[10 + (3-i)] = byteHomeTargetHostIP[i];
            }
			strDig = mDigest.digest((strVisitingTargetHostIP+strPortNumber).getBytes());
			for (int i = 0; i < LM_HDR_LENGTH;i++){//Jaehee modified 170329
				sendBuf[16+i]=  strDig[i];
			}
			for (int i = 0;i < DHTManager.swCount;i++){
				byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList.get(i)).array();
				sendBuf[1] = swByte[1];
				int swListIndex = DHTManager.edgeSWList.get(i)-1;
		        Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
                clientCh.writeAndFlush(
    	                        new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList.get(swListIndex),DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
			}*/
            //old code ends

            //---------------client example
//			String opCode = OPCODE_APP_MOBILITY;
//			String swNum = "02";
//			String homeTargetHostIP = "0a000a01";
//			String portNumber = "0050";
//			String esIP = "0a001401";
//			String newHostIP = "0a001402";
//			String strInput = opCode+swNum+homeTargetHostIP+portNumber+esIP+newHostIP;


            //if ("Quote".equals(packet.content().toString(CharsetUtil.UTF_8))) {
            //    ctx.write(new DatagramPacket(Unpooled.copiedBuffer("Quote" + nextQuote(), CharsetUtil.UTF_8), packet.sender()));
            //}

        } else if (opCode == OPCODE_CTN_MOBILITY){
            if(logging)System.out.println("opCode 5: container mobility.");

            byte[] byteHomeCTIP = byteHostIP;
            String strHomeCTIP = strIP;

            long nVisitingCT = ntohl(payload.readUnsignedInt());
            int nVisitingCTInt = (int)nVisitingCT & 0xFFFFFFFF;
            byte[] byteVisitingCTIP = ByteBuffer.allocate(4).putInt(nVisitingCTInt).array();
            String strVisitingCTIP = String.format("%d.%d.%d.%d",(nVisitingCT & 0xFF), (nVisitingCT >> 8 & 0xFF), (nVisitingCT >> 16 & 0xFF), (nVisitingCT >> 24 & 0xFF));

            long nVisitingESIP = ntohl(payload.readUnsignedInt());
            int nVisitingESIPInt = (int)nVisitingESIP & 0xFFFFFFFF;
            byte[] byteVisitingESIP = ByteBuffer.allocate(4).putInt(nVisitingESIPInt).array();

            long nVisitingTargetHost = ntohl(payload.readUnsignedInt());
            int nVisitingTargetHostInt = (int)nVisitingTargetHost & 0xFFFFFFFF;
            byte[] byteVisitingTargetHostIP = ByteBuffer.allocate(4).putInt(nVisitingTargetHostInt).array();

            if(logging){

                String strVisitingESIP = String.format("%d.%d.%d.%d",(nVisitingESIP & 0xFF), (nVisitingESIP >> 8 & 0xFF), (nVisitingESIP >> 16 & 0xFF), (nVisitingESIP >> 24 & 0xFF));
                String strVisitingTargetHostIP = String.format("%d.%d.%d.%d",(nVisitingTargetHost & 0xFF), (nVisitingTargetHost >> 8 & 0xFF), (nVisitingTargetHost >> 16 & 0xFF), (nVisitingTargetHost >> 24 & 0xFF));

                System.out.printf("[Node %d] Storing the pair: (Original Cnt IP=%s, New Ctn IP=%s, New Host IP=%s, New Edge Switch IP=%s)\n", nodeIndex, strHomeCTIP, strVisitingCTIP, strVisitingTargetHostIP, strVisitingESIP);
            }

            long startTime = 0;
            long endTime = 0;
            if(logFileOut) {
                //TODO session ID is weired
                sessionID = String.format("%08x",rand.nextInt(2147483647));
                if(logging) System.out.println("New session "+sessionID);
                log.append(sessionID+",");
                startTime = System.currentTimeMillis();
                log.append("OPCODE_CTN_MOBILITY,"+startTime+",");
            }
            JsonObject jobj = skipGraphServer.get(strHomeCTIP);
            skipGraphServer.store(strHomeCTIP, strVisitingCTIP, byteVisitingCTIP, byteVisitingESIP, byteVisitingTargetHostIP, byteHomeCTIP, jobj);
            if(logFileOut) {
                endTime = System.currentTimeMillis();
                log.append(endTime+","+(endTime-startTime)+"\n");
            }


            byte[] sendBuf = new byte[48];

            sendBuf[0] = OPCODE_NEW_APP;
            for (int i = 0;i < 4;i++){
                sendBuf[2 + (3-i)] = byteHomeCTIP[i];
                sendBuf[6 + (3-i)] = byteVisitingESIP[i];
                sendBuf[10 + (3-i)] = byteVisitingCTIP[i];
            }
            for (int i = 0;i < 2;i++){
                sendBuf[14 + i] = (byte)0x00;
            }

            MessageDigest mDigest = MessageDigest.getInstance("SHA-256");
            byte[] strDig = mDigest.digest(strHomeCTIP.getBytes());

            for (int i = 0; i < LM_HDR_LENGTH;i++){//Jaehee modified 170329
                sendBuf[16+i]=  strDig[i];
            }
            if(logging){
                System.out.println("Object ID: ");

                for (int i = 0;i < LM_HDR_LENGTH;i++){
                    System.out.printf("%02x",(strDig[i]&0xff));
                }
                System.out.println();
            }

            if(logging)System.out.println("Suppose to update the caches.");
            //210613 update the cache function is disabled.
            // Update the cache of the edges in the edge list.
//            for (int i = 0;i < DHTManager.swCount;i++){
//                if (DHTManager.edgeSWList.get(i) != 0 && DHTManager.edgeSWList.get(i) != switchNum){
//
//                    byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList.get(i)).array();
//                    sendBuf[1] = swByte[1];
//                    int swListIndex = DHTManager.edgeSWList.get(i)-1;
//                    if(logging)System.out.printf("receiving SW IP="+DHTManager.swIPAddrList.get(swListIndex)+",port="+DHTManager.ovsPort+".\n");
//
//                    Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
//                    clientCh.writeAndFlush(
//                            new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList.get(swListIndex),DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
//                }
//            }
//            byte[] swByte = ByteBuffer.allocate(2).putShort(switchNum).array();
//            sendBuf[1] = swByte[1];
//            if(logging)System.out.printf("receiving SW IP="+"127.0.0.1"+",port="+DHTManager.ovsPort+".\n");
//
//
//            Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
//            clientCh.writeAndFlush(
//                    new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress("127.0.0.1",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);


            //old code
			/*
			for (int i = 0;i < 4;i++){
                sendBuf[2 + (3-i)] = byteVisitingCTIP[i];
                sendBuf[6 + (3-i)] = byteVisitingESIP[i];
                sendBuf[10 + (3-i)] = byteHomeCTIP[i];
            }
			strDig = mDigest.digest(strVisitingCTIP.getBytes());
			for (int i = 0; i < LM_HDR_LENGTH;i++){//Jaehee modified 170329
				sendBuf[16+i]=  strDig[i];
			}
			for (int i = 0;i < DHTManager.swCount;i++){
				byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList.get(i)).array();
				sendBuf[1] = swByte[1];
				int swListIndex = DHTManager.edgeSWList.get(i)-1;
		        Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
                clientCh.writeAndFlush(
    	                        new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList.get(swListIndex),DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
			}*/
            //old code ends

            //---------------client example
//			String opCode = OPCODE_CTN_MOBILITY;
//			String swNum = "01";
//			String homeCtnIP = "0a000a03";
//			String newCtnIP = "0a000a03";
//			String newESIP = "0a001401";
//			String newHostIP = "0a001402";
//			String strInput = opCode+swNum+homeCtnIP+newCtnIP+newESIP+newHostIP;

        } else if (opCode == OPCODE_GET_HASH){
            if(logging)System.out.println("opCode 1: get Object ID from DHT server with digested IP");
            //if(logging)System.out.println("opCode 6: get ip:port");


            byte[] bytePortNumber = {payload.readByte(),payload.readByte()};
            String strPortNumber = ":"+((bytePortNumber[1] & 0xFF) + ((bytePortNumber[0] & 0xFF)*0x100));

            MessageDigest mDigest = MessageDigest.getInstance("SHA-256");
            byte[] strDig = mDigest.digest((strIP).getBytes());

            if(logging)System.out.printf("[Node %d] Getting the pair: (Host IP:Port Number=%s%s)\n", nodeIndex, strIP, strPortNumber);


            long startTime = 0;
            long endTime = 0;
            if(logFileOut) {
                //TODO session ID is weired
                sessionID = String.format("%08x",rand.nextInt(2147483647));
                if(logging) System.out.println("New session "+sessionID);
                log.append(sessionID+",");
                startTime = System.currentTimeMillis();
                log.append("OPCODE_GET_HASH,"+startTime+",");
            }
            if (strPortNumber.equals(":0")) {
                skipGraphServer.get(OPCODE_GET_HASH, strIP, switchNum, byteHostIP, strDig);
            }
            else {
                skipGraphServer.get(OPCODE_GET_HASH, strIP+strPortNumber, switchNum, byteHostIP, strDig);
            }
            if(logFileOut) {
                endTime = System.currentTimeMillis();
                log.append(endTime+","+(endTime-startTime)+"\n");
            }


            //---------------client example
//			String opCode = OPCODE_GET_HASH;
//			String swNum = "01";
//			String homeTargetHostIP = "0a000a02";
//			String portNumber = "0050";
//			String strInput = opCode+swNum+homeTargetHostIP+portNumber;

        }

        if(logFileOut) {
            // log file append
            FileWriter fw = new FileWriter("log-"+DHTManager.IPADDR+".csv", true);
            fw.write(log.toString());
            fw.close();
        }

        //old code
        /*else if (opCode == OPCODE_TOGGLE_LOGGING){
        	if(logging)System.out.println("opCode 101: Toggle ovs logging");
        	byte[] sendBuf = new byte[2];
            sendBuf[0] = OPCODE_TOGGLE_LOGGING;
            for (int i = 0;i < DHTManager.swCount;i++){
				if (DHTManager.edgeSWList.get(i) != 0 && DHTManager.edgeSWList.get(i) != switchNum){
                    byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList.get(i)).array();
				    sendBuf[1] = swByte[1];
				    int swListIndex = DHTManager.edgeSWList.get(i)-1;
			        Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
				    clientCh.writeAndFlush(
    	                        new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList.get(swListIndex),DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
                }
			}
			byte[] swByte = ByteBuffer.allocate(2).putShort(switchNum).array();
			sendBuf[1] = swByte[1];
			if(logging)System.out.printf("receiving SW IP="+"127.0.0.1"+",port="+DHTManager.ovsPort+".\n");
	        Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
			clientCh.writeAndFlush(
    	         new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress("127.0.0.1",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
            //---------------client example
//			String opCode = "OPCODE_TOGGLE_LOGGING";
//			String swNum = "01";
        }*/
        //old code ends
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

}

@ChannelHandler.Sharable
class ClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    @Override
    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
        System.out.println("Must not be executed.");
    }
}

class DHTServer {
    private static final int LOCALITY_AWARE_LEVEL = DHTManagerCreator.LOCALITY_AWARE_LEVEL;
    private static final int NETWORK_ADDRESS_LEVEL = 16;

    public final SkipNode ipAddressAwareNode;
    public final SkipNode localityAwareNode;

    public final String localityID;

    public static final byte OPCODE_BOOTUP = 0;
    public static final byte OPCODE_GET_HASH = 1;
    public static final byte OPCODE_GET_IP = 2;
    public static final byte OPCODE_INFORM_CONNECTION = 3;
    public static final byte OPCODE_APP_MOBILITY = 4;
    public static final byte OPCODE_CTN_MOBILITY = 5;
    public static final byte OPCODE_GET_IPPORT = 6;

    public static final byte OPCODE_QUERIED_HASH = 1;
    public static final byte OPCODE_QUERIED_IP = 2;
    public static final byte OPCODE_UPDATE_IP = 3;
    public static final byte OPCODE_NEW_APP = 4;
    public static final byte OPCODE_NEW_CTN = 5;


    public static final int VISITING_IP = 1;
    public static final int ES_IP = 2;
    public static final int VISITING_TARGET_HOST = 3;
    public static final int HOME_TARGET_HOST = 4;
    public static final int HOME_IP = 5;
    public static final int LOCALITY_ID = 6;
    public static final int LID_LIST = 7;

    private static final int LM_HDR_LENGTH = 32;

    private final int edgeNum;
    private final boolean logging;
    private final boolean logFileOut;
    private final int dhtNum;
    private final Bootstrap bClient;
    private final int ovsPort = 9999;

    private final boolean isIpAddrAwareAvailable = false;

    public DHTServer(String ip, int portNumber, String localityID, HashMap kvMap, boolean logging, boolean logFileOut,
                     int edgeNum, int dhtNum, Bootstrap bClient) throws Exception {
        this.logging = logging;
        this.edgeNum = edgeNum;
        this.dhtNum = dhtNum;
        this.bClient = bClient;
        this.logFileOut = logFileOut;

        if (isIpAddrAwareAvailable) {
            this.ipAddressAwareNode = createIPAddressAwareNode(ip, portNumber, kvMap);
        }
        else {
            this.ipAddressAwareNode = null;
        }
        if (localityID != null) {
            this.localityID = localityID;
            this.localityAwareNode = createLocalityAwareNode(ip, portNumber, localityID, kvMap);
        }
        else {
            this.localityID = null;
            this.localityAwareNode = null;
        }

    }

    public DHTServer(String introducerIP, int introducerPortNumber, String ip, int portNumber, String localityID,
                     HashMap kvMap, boolean logging, boolean logFileOut, int edgeNum, int dhtNum, Bootstrap bClient)
            throws Exception {
        this.logging = logging;
        this.edgeNum = edgeNum;
        this.dhtNum = dhtNum;
        this.bClient = bClient;
        this.logFileOut = logFileOut;
        if (isIpAddrAwareAvailable) {
            //Insert this node into DHT network by being introduced by IP address approach node.
            this.ipAddressAwareNode = createIPAddressAwareNode(introducerIP, introducerPortNumber, ip, portNumber, kvMap);
        }
        else {
            this.ipAddressAwareNode = null;
        }
        if (localityID != null) {
            this.localityID = localityID;
            //Insert this node into DHT network by being introduced by IP address approach node.
            this.localityAwareNode = createLocalityAwareNode(introducerIP, introducerPortNumber, ip, portNumber, localityID, kvMap);
        }
        else {
            this.localityID = null;
            this.localityAwareNode = null;
        }
    }


    public SkipNode createIPAddressAwareNode (String ip, int portNumber, HashMap kvMap) {
        return createIPAddressAwareNode(null, 0, ip, portNumber, kvMap);
    }

    //backlog
    //DUPLICATION CHECK
    public SkipNode createIPAddressAwareNode (String introducerIP, int introducerPortNumber, String ip, int portNumber, HashMap kvMap) {
        LookupTableFactory factory = new LookupTableFactory();
        ConcurrentLookupTable table = (ConcurrentLookupTable) factory.createDefaultLookupTable(NETWORK_ADDRESS_LEVEL);

        BigInteger numId = null;
        try {
            numId = new BigInteger(sha256(ip+":"+portNumber), 16);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        String[] ipSplit = ip.split("\\.");
        String nameId = "";
        //USE SECOND IP FIELD 8 bits FOR Prefix
        if(logging) System.out.println(ipSplit[1]);
        Integer item = Integer.parseInt(ipSplit[1]);
        nameId = String.format("%8s", Integer.toBinaryString(item)).replaceAll(" ", "0");

        //USE THIRD IP FIELD 8 bits FOR Prefix

        //TODO "+dhtNum" is used for local experiment.
        //TODO "+dhtNum" is must be removed when executing on emulator
        // ******************************************
        if(logging) System.out.println((ipSplit[2]+dhtNum)+"");
        item = Integer.parseInt(ipSplit[2])+dhtNum;
        // ******************************************
        nameId = nameId + String.format("%8s", Integer.toBinaryString(item)).replaceAll(" ", "0");

        //Supposed to randomly generated.
        item = (100*edgeNum)+dhtNum;
        int idLength = 32 - nameId.length();
        nameId = nameId + String.format("%"+idLength+"s", Integer.toBinaryString(item)).replaceAll(" ", "0");
        //Length of nameID is finally 32.

        System.out.println("IA name ID: " + nameId + " len: " + nameId.length());
        System.out.println("IA port: "+portNumber);

        SkipNodeIdentity identity = new SkipNodeIdentity(nameId, numId, ip, portNumber,null, null);

        SkipNode ipAddressAwareNode = new SkipNode(identity, table, false, kvMap);

        Underlay underlay = new TCPUnderlay();
        underlay.initialize(portNumber);
        MiddleLayer middleLayer = new MiddleLayer(underlay, ipAddressAwareNode);
        ipAddressAwareNode.setMiddleLayer(middleLayer);
        underlay.setMiddleLayer(middleLayer);

        if(logging){
            System.out.println("introducer IP: " + introducerIP + " introducer port: " + introducerPortNumber);
        }
        ipAddressAwareNode.insert(introducerIP, introducerPortNumber);

        return ipAddressAwareNode;
    }

    //backlog
    //DUPLICATION CHECK
    public SkipNode createLocalityAwareNode (String introducerIP, int introducerPortNumber, String ip,
                                             int portNumber, String localityID, HashMap kvMap) {
        LookupTableFactory factory = new LookupTableFactory();
        ConcurrentLookupTable table = (ConcurrentLookupTable) factory.createDefaultLookupTable(LOCALITY_AWARE_LEVEL);

        String nameId = "";

        nameId = localityID;
        //Supposed to randomly generated.
        int item = (100*edgeNum)+dhtNum;

        int idLength = 32 - localityID.length();
        nameId = nameId + String.format("%"+idLength+"s", Integer.toBinaryString(item)).replaceAll(" ", "0");
        //Length of nameID is finally 32.
        System.out.println("LA name ID: " + nameId + " len: " + nameId.length());

        BigInteger numId = null;

        try {
            numId = new BigInteger(sha256(nameId), 16);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        //Port number MUST not be duplicated with IP address aware approach
        //Therefore we added 50 to portNumber
        portNumber += 50;
        System.out.println("LA port: " + portNumber);
        SkipNodeIdentity identity = new SkipNodeIdentity(nameId, numId, ip, portNumber,null, null);

        SkipNode localityAwareNode = new SkipNode(identity, table, false, kvMap);

        Underlay underlay = new TCPUnderlay();
        underlay.initialize(portNumber);
        MiddleLayer middleLayer = new MiddleLayer(underlay, localityAwareNode);
        localityAwareNode.setMiddleLayer(middleLayer);
        underlay.setMiddleLayer(middleLayer);

        introducerPortNumber += 50;
        if(logging){
            System.out.println("introducer IP: " + introducerIP + " introducer port: " + introducerPortNumber);
        }
        localityAwareNode.insert(introducerIP, introducerPortNumber);


        return localityAwareNode;
    }

    public SkipNode createLocalityAwareNode (String ip, int portNumber, String localityID, HashMap kvMap) {
        return createLocalityAwareNode(null, 0, ip, portNumber, localityID, kvMap);
    }


    static String sha256(String input) throws NoSuchAlgorithmException {
        MessageDigest mDigest = MessageDigest.getInstance("SHA-256");
        byte[] result = mDigest.digest(input.getBytes());
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < result.length; i++) {
            sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return sb.toString();
    }

    public String buildJsonDHTEntry (JsonObject jobj) {
        String returnValue = null;
        JsonElement lEsIP = jobj.get(ES_IP+"");
        JsonElement lVisitingIP = jobj.get(VISITING_IP+"");
        JsonElement lHomeTargetHost = jobj.get(HOME_TARGET_HOST+"");
        JsonElement lVisitingTargetHost = jobj.get(VISITING_TARGET_HOST+"");
        JsonElement lLocalityID = jobj.get(LOCALITY_ID + "");
        returnValue = "{";
        if (lEsIP != null) {
            returnValue = returnValue + "\"" + ES_IP + "\":\"" + lEsIP.getAsString() + "\",";
        }
        if (lVisitingIP != null) {
            returnValue = returnValue + "\"" + VISITING_IP + "\":\"" + lVisitingIP.getAsString() + "\",";
        }
        if (lHomeTargetHost != null) {
            returnValue = returnValue + "\"" + HOME_TARGET_HOST + "\":\"" + lHomeTargetHost.getAsString() + "\",";
        }
        if (lVisitingTargetHost != null) {
            returnValue = returnValue + "\"" + VISITING_TARGET_HOST + "\":\"" + lVisitingTargetHost.getAsString() +"\",";
        }
        if (lLocalityID != null) {
            returnValue = returnValue + "\"" + LOCALITY_ID + "\":\"" + lLocalityID.getAsString() + "\",";
        }
        if (localityID != null) {
            JsonArray jarray = jobj.getAsJsonArray(LID_LIST + "");
            if (jarray != null) {
                returnValue = returnValue + "\"" + LID_LIST + "\":" + extendLidList(jobj);
            }
        }
        else {
            JsonArray jarray = jobj.getAsJsonArray(LID_LIST + "");
            if (jarray != null) {
                returnValue = returnValue + "\"" + LID_LIST + "\":" + jarray.toString();
            }
        }
        if (returnValue.endsWith(",")) {
            returnValue = returnValue.substring(0, returnValue.length() - 1) + "}";
        }
        else {
            returnValue = returnValue + "}";
        }
        return returnValue;
    }

    public void replicateData(JsonObject jobj, String firstSHA) {
        String dataOwnerLocalityId = jobj.get(LOCALITY_ID+"").toString();
        if (localityAwareNode != null && dataOwnerLocalityId != null) {
            String revisedEntry = buildJsonDHTEntry(jobj);
            localityAwareNode.storeResourceByNameID(dataOwnerLocalityId, firstSHA, revisedEntry);
            localityAwareNode.storeResourceByNameID(localityID, firstSHA, revisedEntry);
            JsonArray jarray = jobj.getAsJsonArray(LID_LIST+"");
            if (jarray != null) {
                for (int i = 0 ; i < jarray.size() ; i++){
                    if (!jarray.get(i).getAsString().equals(localityID)) {
                        localityAwareNode.storeResourceByNameID(jarray.get(i).toString(), firstSHA, revisedEntry);
                    }
                }
            }
        }
    }

    public String search (String hashed, String input) {
        boolean isHit = false;
        String searchResult = null;
        SkipNodeIdentity nodeIdentity = null;
        if (localityID != null && localityAwareNode != null) {
            //Search entry with MOID on Edge's locality site
            if(logging){
                System.out.println("Locality-aware approach searching");
                System.out.println("Locality ID: "+localityID);
                System.out.println("Key: "+hashed);
            }
            String valueLA = localityAwareNode.getResourceByNameID(localityID, hashed); //Search its locality.
            if (valueLA != null && !valueLA.equals("")) { //Hit
                isHit = true;
                searchResult = valueLA;
            }

            // Deprecated recursive search by expanding locality regions.
            /*
            else { //Failed
                int targetPrefixLength = LOCALITY_AWARE_LEVEL;
                while ((valueLA == null || valueLA.equals("")) && targetPrefixLength>=0) {
                    int prefixRemainder = LOCALITY_AWARE_LEVEL - targetPrefixLength;
                    if (prefixRemainder > 0) {
                        for (int incremental = (int) Math.pow(2, prefixRemainder - 1); incremental < Math.pow(2, prefixRemainder); incremental++) {
                            String targetLocalityID = localityID.substring(0, targetPrefixLength) + String.format("%" + prefixRemainder + "s", Integer.toBinaryString(incremental)).replaceAll(" ", "0");

                            if (!targetLocalityID.equals(localityID)) {
                                if (logging) {
                                    System.out.println("Locality-aware approach searching");
                                    System.out.println("Locality ID: " + targetLocalityID);
                                    System.out.println("Key: " + hashed);
                                }
                                valueLA = localityAwareNode.getResourceByNameID(targetLocalityID, hashed);
                            }
                            if (valueLA != null && !valueLA.equals("")) { //Hit
                                isHit = true;
                                searchResult = valueLA;
                                break;
                            }
                        }
                    }
                    if (valueLA != null && !valueLA.equals("")) { //Hit
                        isHit = true;
                        searchResult = valueLA;
                        break;
                    }
                    if (targetPrefixLength == 0) {
                        break;
                    }
                    targetPrefixLength --;
                    if (localityID.charAt(targetPrefixLength) == '1') {
                        for (int innerInc = 0; innerInc < Math.pow(2, prefixRemainder); innerInc++) {
                            String targetLocalityID = localityID.substring(0, targetPrefixLength) + "0" + String.format("%" + prefixRemainder + "s", Integer.toBinaryString(innerInc)).replaceAll(" ", "0");

                            if (!targetLocalityID.equals(localityID)) {
                                if (logging) {
                                    System.out.println("Locality-aware approach searching");
                                    System.out.println("Locality ID: " + targetLocalityID);
                                    System.out.println("Key: " + hashed);
                                }
                                valueLA = localityAwareNode.getResourceByNameID(targetLocalityID, hashed);
                            }
                            if (valueLA != null && !valueLA.equals("")) { //Hit
                                isHit = true;
                                searchResult = valueLA;
                                break;
                            }
                        }
                        targetPrefixLength --;
                    }
                }
            }*/
        }
        if (!isHit && !hashed.equals(input) && ipAddressAwareNode != null) { //Locality-aware approach searching failed
            //Search entry with network address of IP address on IP address approach
            String[] ipSplit = input.split("\\.");
            String nameId = "";
            //ONLY USE THIRD IP FIELD 8 bits FOR Prefix

            Integer item = Integer.parseInt(ipSplit[1]);
            nameId = String.format("%8s", Integer.toBinaryString(item)).replaceAll(" ", "0");
            item = Integer.parseInt(ipSplit[2]);
            nameId = nameId + String.format("%8s", Integer.toBinaryString(item)).replaceAll(" ", "0");
            if(logging) {
                System.out.println("IP address-aware approach searching");
                System.out.println("IP based name ID: "+nameId);
                System.out.println("Key: "+hashed);
            }
            String valueIA = ipAddressAwareNode.getResourceByNameID(nameId, hashed);
            if (valueIA != null && !valueIA.equals("")) { //Hit
                //Revise entry's locator from prior Edge to recent Edge
                //GOTO (2)
                isHit = true;
                searchResult = valueIA;
            }
        }
        if (!isHit) { //IP address-approach searching failed
            if(logging) {
                System.out.println("Hash approach searching");
                System.out.println("Key: "+hashed);
            }
            String valueHash = null;
            if (ipAddressAwareNode != null) {
                valueHash = ipAddressAwareNode.getResourceByResourceKey(hashed);
            }
            if (valueHash != null && !valueHash.equals("")) { //Hit
                isHit = true;
                searchResult = valueHash;
            }
            if (!isHit && localityID != null && localityAwareNode != null) {
                valueHash = localityAwareNode.getResourceByResourceKey(hashed);
            }
            if (valueHash != null && !valueHash.equals("")) { //Hit
                isHit = true;
                searchResult = valueHash;
            }
        }

        return searchResult;
    }

    //TODO
    //Data Requester에서 동작하는 알고리즘
    //LID_LIST update는 어떻게 수행해야 하는가?
    //Data Owner에게 전달해야 하는가?
    //Data Owner의 locality로 알려주면 되는가?
    public void get(final int opCode, final String input, final byte switchNum, final byte[] byteHostIP,
                    final byte[] hashedIP) throws ClassNotFoundException, IOException, NoSuchAlgorithmException {

        final Date date = new Date();
        final long starttime = date.getTime();

        //MUST TODO

        if (opCode == OPCODE_GET_HASH) {
            //In this case, input is a string of hostIP:port
            byte lSwitchNum = switchNum;
            byte[] lByteHostIP = byteHostIP.clone();
            byte[] lHashedIP = hashedIP.clone();

            String strIP = input.split(":")[0];
            String firstSHA = sha256(strIP);
            if (logging){
                System.out.println("Key: " + firstSHA);
            }

            String searchResult = search(firstSHA, input);

            if (searchResult != null) { //Hit
                //TODO
                //Jaehyun implements sending UDP packet to OVS
                if (logging) System.out.println("OpCode == OPCODE_GET_HASH, " + searchResult);
                String foundData = searchResult;
                JsonParser parser = new JsonParser();
                JsonObject jobj = (JsonObject) parser.parse(foundData);

                String recvData = jobj.get(VISITING_IP + "").getAsString() + jobj.get(ES_IP + "").getAsString();

                byte[] sendData = new byte[43];//Jaehee modified 160720

                sendData[0] = OPCODE_QUERIED_HASH;
                sendData[1] = lSwitchNum;
                for (int i = 0; i < 4; i++) {
                    sendData[2 + (3 - i)] = (byte) ((Character.digit(recvData.charAt(i * 2), 16) << 4) + Character.digit(recvData.charAt(i * 2 + 1), 16));
                    sendData[6 + LM_HDR_LENGTH + (3 - i)] = (byte) ((Character.digit(recvData.charAt((i + 4) * 2), 16) << 4) + Character.digit(recvData.charAt((i + 4) * 2 + 1), 16));
                }//Jaehee modified 160720
                for (int i = 0; i < LM_HDR_LENGTH; i++) {//Jaehee modified 160720
                    sendData[6 + i] = lHashedIP[i];
                }
                sendData[42] = '\0';

                /*
                // Return entry to OvS kernal module
                Channel clientCh = null;
                try {
                    clientCh = bClient.bind(0).sync().channel();
                    clientCh.writeAndFlush(
                            new DatagramPacket(Unpooled.copiedBuffer(sendData), new InetSocketAddress("localhost", ovsPort))).addListener(ChannelFutureListener.CLOSE);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (logging) {
                    System.out.println("send oid:");
                    for (int i = 0; i < LM_HDR_LENGTH; i++) {
                        System.out.printf("%02x", sendData[6 + i]);
                    }
                    System.out.println();
                }
                */

                if (localityID != null && localityAwareNode != null) {
                    replicateData(jobj, firstSHA);
                }

                /*
                if (logFileOut) {
                    Date enddate = new Date();
                    long endtime = enddate.getTime();
                    long diff = endtime - starttime;

                    String fileName = "/DHTGetDelay.log";
                    fileName = System.getProperty("user.dir") + fileName.trim();
                    File file = new File(fileName);

                    FileWriter fw = null;
                    BufferedWriter bw = null;
                    PrintWriter out = null;
                    try {
                        fw = new FileWriter(file, true);
                        bw = new BufferedWriter(fw);
                        out = new PrintWriter(bw);

                        out.println(diff);

                    } catch (IOException e) {
                        //exception handling left as an exercise for the reader
                    } finally {
                        if (out != null) {
                            out.close();
                        }
                        if (bw != null) {
                            bw.close();
                        }
                        if (fw != null) {
                            fw.close();
                        }
                    }
                }*/
                //GOTO (2)
            }   else { // Fail
                    // Write new entry, home site is recent Edge

                    //TODO
                    /*
                    if(logging)System.out.println("Get Failed.");

                    firstSHA = sha256(input).getBytes();
                    DHTManager.skipGraphServer.get(OPCODE_GET_IPPORT, input, switchNum, lByteHostIP, firstSHA);

                    if(DHTManager.logFileOut) {

                        Date enddate = new Date();
                        long endtime = enddate.getTime();
                        long diff = endtime-starttime;

                        String fileName = "/DHTGetDelay.log";
                        fileName = System.getProperty("user.dir")+fileName.trim();
                        File file = new File (fileName);

                        FileWriter fw = null;
                        BufferedWriter bw = null;
                        PrintWriter out = null;
                        try{
                            fw = new FileWriter(file, true);
                            bw = new BufferedWriter(fw);
                            out = new PrintWriter(bw);

                            out.println(diff);

                        } catch (IOException e) {
                            //exception handling left as an exercise for the reader
                        } finally {
                            if (out != null) {
                                out.close();
                            }
                            if (bw != null) {
                                bw.close();
                            }
                            if (fw != null) {
                                fw.close();
                            }
                        }
                    }
                    */

                    //GOTO (4)
                }
//                else { // !isLocalityIdSame
//                    //(3) Keep prior locality ID and revise entry's locality ID
//                    //Update entry on this locality site
//                    //Update entry on prior locality site
//                }

        } else if (opCode == OPCODE_GET_IP) {
            //In this case, input is an objectKey
            String firstSHA = input;

            String searchResult = search(firstSHA, input);

            if (searchResult != null) { //Locality-aware approach searching failed
                //Jaehyun needs to implement sending UDP packet to OVS
                if (logging) System.out.println("OpCode == OPCODE_GET_IP, " + searchResult);
                String foundData = searchResult;

                JsonObject jobj = (JsonObject) new JsonParser().parse(foundData);

                String recvData = jobj.get(VISITING_IP + "").getAsString() + jobj.get(ES_IP + "").getAsString();

                byte[] sendData = new byte[43];//Jaehee modified 160720

                sendData[0] = OPCODE_QUERIED_IP;
                sendData[1] = switchNum;
                for (int i = 0; i < 4; i++) {
                    sendData[2 + (3 - i)] = (byte) ((Character.digit(recvData.charAt(i * 2), 16) << 4) + Character.digit(recvData.charAt(i * 2 + 1), 16));
                    sendData[6 + LM_HDR_LENGTH + (3 - i)] = (byte) ((Character.digit(recvData.charAt((i + 4) * 2), 16) << 4) + Character.digit(recvData.charAt((i + 4) * 2 + 1), 16));

                }//Jaehee modified 160720
                for (int i = 0; i < LM_HDR_LENGTH; i++) {//Jaehee modified 160720
                    sendData[6 + i] = hashedIP[i];
                }

                sendData[42] = '\0';

                // Return entry to OvS kernal module
                Channel clientCh = null;
                try {
                    clientCh = bClient.bind(0).sync().channel();
                    clientCh.writeAndFlush(
                            new DatagramPacket(Unpooled.copiedBuffer(sendData), new InetSocketAddress("localhost", ovsPort))).addListener(ChannelFutureListener.CLOSE);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (logging) {
                    System.out.println("send oid:");
                    for (int i = 0; i < LM_HDR_LENGTH; i++) {
                        System.out.printf("%02x", sendData[6 + i]);
                    }
                    System.out.println();
                }

                if (localityID != null && localityAwareNode != null) {
                    replicateData(jobj, firstSHA);
                }

                /*
                if (logFileOut) {
                    Date enddate = new Date();
                    long endtime = enddate.getTime();
                    long diff = endtime - starttime;

                    String fileName = "/DHTGetDelay.log";
                    fileName = System.getProperty("user.dir") + fileName.trim();
                    File file = new File(fileName);

                    FileWriter fw = null;
                    BufferedWriter bw = null;
                    PrintWriter out = null;
                    try {
                        fw = new FileWriter(file, true);
                        bw = new BufferedWriter(fw);
                        out = new PrintWriter(bw);

                        out.println(diff);

                    } catch (IOException e) {
                        //exception handling left as an exercise for the reader
                    } finally {
                        if (out != null) {
                            out.close();
                        }
                        if (bw != null) {
                            bw.close();
                        }
                        if (fw != null) {
                            fw.close();
                        }
                    }
                }*/
                //GOTO (2)
            } else { // Fail

                // Write new entry, home site is recent Edge
                //TODO
                    /*
                    if(logging)System.out.println("Get Failed.");

                    byte[] sendData = new byte[43];//Jaehee modified 160720

                    sendData[0] = OPCODE_QUERIED_IP;
                    sendData[1] = switchNum;
                    for (int i = 0; i < 4;i++){
                        sendData[2+(3-i)] = byteHostIP[i];
                        sendData[6+LM_HDR_LENGTH+i] = 0x00;
                    }//Jaehee modified 160720
                    for (int i = 0; i < LM_HDR_LENGTH;i++){ //Jaehee modified 160720
                        sendData[6+i]=  hashedIP[i];
                    }

                    sendData[42]='\0';


                    Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
                    clientCh.writeAndFlush(
                            new DatagramPacket(Unpooled.copiedBuffer(sendData), new InetSocketAddress("localhost",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);

                    if(logging) {
                        System.out.println("send oid:");
                        for (int i = 0 ; i < LM_HDR_LENGTH ; i++) {
                            System.out.printf("%02x",sendData[6+i]);
                        }
                        System.out.println();

                    }
                    if(DHTManager.logFileOut) {
                        Date enddate = new Date();
                        long endtime = enddate.getTime();
                        long diff = endtime-starttime;

                        String fileName = "/DHTGetDelay.log";
                        fileName = System.getProperty("user.dir")+fileName.trim();
                        File file = new File (fileName);

                        FileWriter fw = null;
                        BufferedWriter bw = null;
                        PrintWriter out = null;
                        try{
                            fw = new FileWriter(file, true);
                            bw = new BufferedWriter(fw);
                            out = new PrintWriter(bw);

                            out.println(diff);

                        } catch (IOException e) {
                            //exception handling left as an exercise for the reader
                        } finally {
                            if (out != null) {
                                out.close();
                            }
                            if (bw != null) {
                                bw.close();
                            }
                            if (fw != null) {
                                fw.close();
                            }
                        }
                    }*/
                //GOTO (4)
            }
        }

//                else { // !isLocalityIdSame
//                    //(3) Keep prior locality ID and revise entry's locality ID
//                    //Update entry on this locality site
//                    //Update entry on prior locality site
//                }

        else if(opCode == OPCODE_GET_IPPORT){
            //In this case, input is a string of hostIP:Port Number
            byte lSwitchNum = switchNum;
            byte[] lByteHostIP = byteHostIP.clone();
            byte[] lHashedIP = hashedIP.clone();
            String firstSHA = sha256(input);


            String searchResult = search(firstSHA, input);

            if (searchResult != null) {
                //TODO
                //Revise entry's locator from prior Edge to recent Edge
                //Jaehyun implements sending UDP packet to OVS
                if (logging)
                    System.out.println("OpCode == OPCODE_GET_IPPORT, " + searchResult);
                String foundData = searchResult;
                int nPort = Integer.parseInt(input.split(":")[1]);
                String strPort = Integer.toHexString(nPort);

                switch (4 - strPort.length()) {
                    case 4:
                        strPort = "0000";
                        break;
                    case 3:
                        strPort = "000" + strPort;
                        break;
                    case 2:
                        strPort = "00" + strPort;
                        break;
                    case 1:
                        strPort = "0" + strPort;
                        break;
                }

                JsonParser parser = new JsonParser();
                JsonObject jobj = (JsonObject) parser.parse(foundData);
                String recvData = jobj.get(HOME_TARGET_HOST + "").getAsString() + jobj.get(ES_IP + "").getAsString() + jobj.get(VISITING_IP + "").getAsString() + strPort;

                byte[] sendData = new byte[49];//Jaehee modified 160720

                sendData[0] = OPCODE_NEW_APP;
                sendData[1] = lSwitchNum;
                for (int i = 0; i < 4; i++) {
                    sendData[2 + (3 - i)] = (byte) ((Character.digit(recvData.charAt(i * 2), 16) << 4) + Character.digit(recvData.charAt(i * 2 + 1), 16)); //HOME_TARGET_HOST
                    sendData[6 + (3 - i)] = (byte) ((Character.digit(recvData.charAt((i + 4) * 2), 16) << 4) + Character.digit(recvData.charAt((i + 4) * 2 + 1), 16)); //ES_IP
                    sendData[10 + (3 - i)] = (byte) ((Character.digit(recvData.charAt((i + 8) * 2), 16) << 4) + Character.digit(recvData.charAt((i + 8) * 2 + 1), 16)); //VISITING_IP
                }//Jaehee modified 160720

                for (int i = 0; i < 2; i++) {
                    sendData[14 + i] = (byte) ((Character.digit(recvData.charAt((i + 12) * 2), 16) << 4) + Character.digit(recvData.charAt((i + 12) * 2 + 1), 16)); //strPort
                }
                for (int i = 0; i < LM_HDR_LENGTH; i++) {//Jaehee modified 160720
                    sendData[16 + i] = lHashedIP[i];
                }

                sendData[48] = '\0';

                // Return entry to OvS kernal module
                Channel clientCh = null;
                try {
                    clientCh = bClient.bind(0).sync().channel();
                    clientCh.writeAndFlush(
                            new DatagramPacket(Unpooled.copiedBuffer(sendData),
                                    new InetSocketAddress("localhost", ovsPort)))
                            .addListener(ChannelFutureListener.CLOSE);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (logging) {
                    System.out.println("send oid:");
                    for (int i = 0; i < LM_HDR_LENGTH; i++) {
                        System.out.printf("%02x", sendData[16 + i]);
                    }
                    System.out.println();
                }

                if (localityID != null && localityAwareNode != null) {
                    replicateData(jobj, firstSHA);
                }

                /*
                if (logFileOut) {
                    Date enddate = new Date();
                    long endtime = enddate.getTime();
                    long diff = endtime - starttime;

                    String fileName = "/DHTGetDelay.log";
                    fileName = System.getProperty("user.dir") + fileName.trim();
                    File file = new File(fileName);

                    FileWriter fw = null;
                    BufferedWriter bw = null;
                    PrintWriter out = null;
                    try {
                        fw = new FileWriter(file, true);
                        bw = new BufferedWriter(fw);
                        out = new PrintWriter(bw);

                        out.println(diff);

                    } catch (IOException e) {
                        //exception handling left as an exercise for the reader
                    } finally {
                        if (out != null) {
                            out.close();
                        }
                        if (bw != null) {
                            bw.close();
                        }
                        if (fw != null) {
                            fw.close();
                        }
                    }
                }*/
            }
            else { //Failed
            }

            //GOTO (2)
                // Write new entry, home site is recent Edge
                //TODO
                /*
                if(logging)System.out.println("Get Failed.");

                int nPort = Integer.parseInt(input.split(":")[1]);
                String strPort = Integer.toHexString(nPort);

                switch (4-strPort.length()){
                    case 4 : strPort = "0000";
                        break;
                    case 3 : strPort = "000"+strPort;
                        break;
                    case 2 : strPort = "00"+strPort;
                        break;
                    case 1 : strPort = "0"+strPort;
                        break;
                }
                byte[] sendData = new byte[49];//Jaehee modified 160720

                sendData[0] = OPCODE_NEW_APP;
                sendData[1] = switchNum;
                for (int i = 0; i < 4;i++){
                    sendData[2+(3-i)] = byteHostIP[i];
                    sendData[6+i] = 0x00;
                    sendData[10+(3-i)] = byteHostIP[i];
                }//Jaehee modified 170329
                for (int i = 0; i < 2;i++){
                    sendData[14+i] = (byte) ((Character.digit(strPort.charAt(i*2), 16) << 4) + Character.digit(strPort.charAt(i*2+1), 16)); //strPort

                }

                for (int i = 0; i < LM_HDR_LENGTH;i++){ //Jaehee modified 160720
                    sendData[16+i]=  hashedIP[i];
                }

                sendData[48]='\0';

                Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
                clientCh.writeAndFlush(
                        new DatagramPacket(Unpooled.copiedBuffer(sendData), new InetSocketAddress("localhost",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);



                if(logging) {
                    System.out.println("send oid:");
                    for (int i = 0 ; i < LM_HDR_LENGTH ; i++) {
                        System.out.printf("%02x",sendData[16+i]);
                    }
                    System.out.println();
                }
                if(DHTManager.logFileOut) {
                    Date enddate = new Date();
                    long endtime = enddate.getTime();
                    long diff = endtime-starttime;

                    String fileName = "/DHTGetDelay.log";
                    fileName = System.getProperty("user.dir")+fileName.trim();
                    File file = new File (fileName);

                    FileWriter fw = null;
                    BufferedWriter bw = null;
                    PrintWriter out = null;
                    try{
                        fw = new FileWriter(file, true);
                        bw = new BufferedWriter(fw);
                        out = new PrintWriter(bw);

                        out.println(diff);

                    } catch (IOException e) {
                        //exception handling left as an exercise for the reader
                    } finally {
                        if (out != null) {
                            out.close();
                        }
                        if (bw != null) {
                            bw.close();
                        }
                        if (fw != null) {
                            fw.close();
                        }
                    }
                }
                */
            //GOTO (4)
        }

//                } else { // !isLocalityIdSame
//                    //(3) Keep prior locality ID and revise entry's locality ID
//                    //Update entry on this locality site
//                    //Update entry on prior locality site
//                }


                //NOT TODO
                //old code
//                else {
//
//						byte[] sendData = new byte[42];//Jaehee modified 160720
//
//						sendData[0] = OPCODE_QUERIED_HASH;
//						sendData[1] = switchNum;
//						for (int i = 0; i < 4;i++){
//							sendData[2+(3-i)] = byteHostIP[i];
//							sendData[38+i] = 0x00;
//						}//Jaehee modified 160720
//						for (int i = 0; i < LM_HDR_LENGTH;i++){//Jaehee modified 160720
//							sendData[6+i]=  hashedIP[i];
//						}
//
//				        Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
//						clientCh.writeAndFlush(
//				                        new DatagramPacket(Unpooled.copiedBuffer(sendData), new InetSocketAddress("localhost",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
//
//						if(logging)System.out.println("Get Failed");
//					}
                //old code ends
    }

    //TODO
    //Data Owner에서 동작하는 알고리즘
    public JsonObject get(String moidSource) throws NoSuchAlgorithmException{
        //opCode == OPCODE_INFORM_CONNECTION or OPCODE_APP_MOBILITY or OPCODE_CTN_MOBILITY
        String firstSHA = sha256(moidSource);
        JsonArray jarray = null;

        String searchResult = search(firstSHA, moidSource);

        if (searchResult != null) {
            //TODO
            if (logging)
                System.out.println("OpCode == OPCODE_INFORM_CONNECTION or OPCODE_APP_MOBILITY or OPCODE_CTN_MOBILITY, " + searchResult);
            JsonParser jparser = new JsonParser();
            JsonObject jobj = (JsonObject) jparser.parse(searchResult);
            return jobj;
        }
        else { // Failed
            return null;
        }
    }

    public String extendLidList(JsonObject jobj) {
        JsonArray jarray = jobj.getAsJsonArray(LID_LIST+"");
        if (jarray != null) {
            boolean isContainingSameLID = false;
            for (int i = 0 ; i < jarray.size() ; i++){
                if (jarray.get(i).getAsString().equals(localityID)) {
                    isContainingSameLID = true;
                    break;
                }
            }
            if (isContainingSameLID) {
                return jarray.toString();
            }
            else {
                jarray.add(localityID);
                return jarray.toString();
            }
        }
        else {
            return "[\""+localityID+"\"]";
        }
    }

    //TODO
    //Data Owner에서 동작하는 알고리즘
    public void store(String strHostIP, byte[] hostIP, byte[] switchIP, JsonObject jobj) throws IOException, NoSuchAlgorithmException {
        //opCode == OPCODE_INFORM_CONNECTION

        StringBuilder jsonString = new StringBuilder();
        String strData = "";
        for(int i=0; i < hostIP.length ;i++)
            strData += String.format("%02x", hostIP[i]);


        jsonString.append("{ \"" + VISITING_IP + "\" : \"" + strData + "\",");


        strData = "";
        for(int i=0; i < switchIP.length; i++)
            strData += String.format("%02x", switchIP[i]);

        jsonString.append("\""+ES_IP+"\" : \""+ strData +"\"");

        String[] lidList = null;
        if (localityID != null) {
            jsonString.append(", \"" + LOCALITY_ID + "\" : \"" + localityID + "\", ");

            lidList = new String[]{localityID};
            if (jobj != null) {
                String lidJArr = extendLidList(jobj);
                jsonString.append("\"" + LID_LIST + "\":" + lidJArr + "}");
                lidList = lidJArr.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\"", "").split(",");
            } else {
                jsonString.append("\"" + LID_LIST + "\":[\"" + localityID + "\"]}");
            }
        }
        else {
            jsonString.append("}");
        }

        //TODO LID_LIST

        String firstSHA = sha256(strHostIP);


        //TODO
        if(logging)System.out.println("key: "+firstSHA+", data: "+jsonString.toString());
        if(localityAwareNode != null && localityID != null) {
            for (String locID : lidList) {
                localityAwareNode.storeResourceByNameID(locID, firstSHA, jsonString.toString());
            }
            localityAwareNode.storeResourceByNumID(new BigInteger(firstSHA, 16), jsonString.toString());
        }
        if(ipAddressAwareNode != null) {
            ipAddressAwareNode.storeResource(firstSHA,jsonString.toString());
            ipAddressAwareNode.storeResourceByResourceKey(firstSHA, jsonString.toString());
        }


        //peer.put(Number160.createHash(firstSHA)).setData(new Data(jsonString.toString())).start();
    }

    public void store(String originalHostIPPort, String visitingTargetHostIPPort, byte[] visitingTargetHostIP, byte[] switchIP, byte[] homeTargetHostIP, JsonObject jobj) throws IOException, NoSuchAlgorithmException {
        //opCode == OPCODE_APP_MOBILITY

        StringBuilder jsonString = new StringBuilder();

        String strData = "";
        for(int i=0; i < visitingTargetHostIP.length ;i++)
            strData += String.format("%02x", visitingTargetHostIP[i]);

        jsonString.append("{ \"" + VISITING_IP + "\" : \"" + strData + "\",");

        strData = "";
        for(int i=0; i < switchIP.length ;i++)
            strData += String.format("%02x", switchIP[i]);
        jsonString.append("\""+ES_IP+"\" : \""+ strData +"\",");


        strData = "";
        for(int i=0; i < homeTargetHostIP.length ;i++)
            strData += String.format("%02x", homeTargetHostIP[i]);
        jsonString.append("\""+HOME_TARGET_HOST+"\" : \""+ strData +"\"");


        String[] lidList = null;
        if (localityID != null) {
            jsonString.append(", \"" + LOCALITY_ID + "\" : \"" + localityID + "\", ");

            lidList = new String[]{localityID};
            if (jobj != null) {
                String lidJArr = extendLidList(jobj);
                jsonString.append("\"" + LID_LIST + "\":" + lidJArr + "}");
                lidList = lidJArr.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\"", "").split(",");
            } else {
                jsonString.append("\"" + LID_LIST + "\":[\"" + localityID + "\"]}");
            }
        }
        else {
            jsonString.append("}");
        }


        String firstSHA = sha256(originalHostIPPort);

        //TODO
       if(logging)System.out.println("key: "+firstSHA+", data: "+jsonString.toString());
        if(localityAwareNode != null && localityID != null) {
            for (String locID : lidList) {
                localityAwareNode.storeResourceByNameID(locID, firstSHA, jsonString.toString());
            }
            localityAwareNode.storeResourceByNumID(new BigInteger(firstSHA, 16), jsonString.toString());
        }
        if(ipAddressAwareNode != null) {
            ipAddressAwareNode.storeResource(firstSHA,jsonString.toString());
            ipAddressAwareNode.storeResourceByResourceKey(firstSHA, jsonString.toString());
        }
//        peer.put(Number160.createHash(firstSHA)).setData(new Data(jsonString.toString())).start();


        String firstSHA_2 = sha256(visitingTargetHostIPPort);
		/*StringBuilder jsonString2 = new StringBuilder();


		strData = "";
		for(int i=0; i < visitingTargetHostIP.length ;i++)
			strData += String.format("%02x", visitingTargetHostIP[i]);
		jsonString2.append("{ \"" + VISITING_IP + "\" : \"" + strData + "\",");

		strData = "";
		for(int i=0; i < switchIP.length ;i++)
			strData += String.format("%02x", switchIP[i]);
		jsonString2.append("\""+ES_IP+"\" : \""+ strData +"\",");

		strData = "";
		for(int i=0; i < homeTargetHostIP.length ;i++)
			strData += String.format("%02x", homeTargetHostIP[i]);
		jsonString2.append("\""+HOME_TARGET_HOST+"\" : \""+ strData +"\" }");*/

        //TODO
//        if(logging)System.out.println("key: "+Number160.createHash(firstSHA_2)+", data: "+jsonString.toString());
//        peer.put(Number160.createHash(firstSHA_2)).setData(new Data(jsonString.toString())).start();
        jsonString.setLength(0);
        //jsonString2.setLength(0);
    }

    public void store(String strHomeCTIP, String strSwitchedIP, byte[] visitingCtnIP, byte[] switchIP, byte[] visitingTargetHostIP, byte[] homeCtnIP, JsonObject jobj) throws IOException, NoSuchAlgorithmException {
        //opCode == OPCODE_CTN_MOBILITY

        StringBuilder jsonString = new StringBuilder();

        String strData = "";
        for(int i=0; i < visitingCtnIP.length ;i++)
            strData += String.format("%02x", visitingCtnIP[i]);
        jsonString.append("{ \"" + VISITING_IP + "\" : \"" + strData + "\",");

        strData = "";
        for(int i=0; i < switchIP.length ;i++)
            strData += String.format("%02x", switchIP[i]);
        jsonString.append("\""+ES_IP+"\" : \""+ strData +"\",");

        strData = "";
        for(int i=0; i < homeCtnIP.length ;i++)
            strData += String.format("%02x", homeCtnIP[i]);
        jsonString.append("\""+HOME_IP+"\" : \""+ strData +"\",");

        strData = "";
        for(int i=0; i < visitingTargetHostIP.length ;i++)
            strData += String.format("%02x", visitingTargetHostIP[i]);
        jsonString.append("\""+VISITING_TARGET_HOST+"\" : \""+ strData +"\"");

        String[] lidList = null;
        if (localityID != null) {
            jsonString.append(", \"" + LOCALITY_ID + "\" : \"" + localityID + "\", ");

            lidList = new String[]{localityID};
            if (jobj != null) {
                String lidJArr = extendLidList(jobj);
                jsonString.append("\"" + LID_LIST + "\":" + lidJArr + "}");
                lidList = lidJArr.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\"", "").split(",");
            } else {
                jsonString.append("\"" + LID_LIST + "\":[\"" + localityID + "\"]}");
            }
        }
        else {
            jsonString.append("}");
        }

        String firstSHA = sha256(strHomeCTIP);

        //TODO
        if(logging)System.out.println("key: "+firstSHA+", data: "+jsonString.toString());
        if(localityAwareNode != null && localityID != null) {
            for (String locID : lidList) {
                localityAwareNode.storeResourceByNameID(locID, firstSHA, jsonString.toString());
            }
            localityAwareNode.storeResourceByNumID(new BigInteger(firstSHA, 16), jsonString.toString());
        }
        if (ipAddressAwareNode != null) {
            ipAddressAwareNode.storeResource(firstSHA,jsonString.toString());
            ipAddressAwareNode.storeResourceByResourceKey(firstSHA, jsonString.toString());
        }
//        peer.put(Number160.createHash(firstSHA)).setData(new Data(jsonString.toString())).start();



		/*strData = "";
		for(int i=0; i < visitingCtnIP.length ;i++)
			strData += String.format("%02x", visitingCtnIP[i]);
		jobj2.put(VISITING_IP+"", strData);
		strData = "";
		for(int i=0; i < switchIP.length ;i++)
			strData += String.format("%02x", switchIP[i]);
		jobj2.put(ES_IP+"", strData);
		strData = "";
		for(int i=0; i < homeCtnIP.length ;i++)
			strData += String.format("%02x", homeCtnIP[i]);
		jobj2.put(HOME_IP+"", strData);
		strData = "";
		for(int i=0; i < visitingTargetHostIP.length ;i++)
			strData += String.format("%02x", visitingTargetHostIP[i]);
		jobj2.put(VISITING_TARGET_HOST+"", strData);*/

        String firstSHA_2 = sha256(strSwitchedIP);

        //TODO
//        if(logging)System.out.println("key: "+Number160.createHash(firstSHA_2)+", data: "+jsonString.toString());
//        peer.put(Number160.createHash(firstSHA_2)).setData(new Data(jsonString.toString())).start();
        jsonString.setLength(0);
    }
    // backup
	/*
	public void store(String strHostIP, byte[] hostIP, byte[] switchIP) throws IOException, NoSuchAlgorithmException {
		//opCode == OPCODE_APP_MOBILITY
		String strData = "";
		for(int i=0; i < hostIP.length ;i++)
			strData = strData + String.format("%02x", hostIP[i]);
		for(int i=0; i < switchIP.length; i++)
			strData = strData + String.format("%02x", switchIP[i]);
		String firstSHA = sha256(strHostIP);
		peer.put(Number160.createHash(firstSHA)).setData(new Data(strData)).start();
	}

	public void store(String strHostIP, byte[] hostIP, byte[] switchIP) throws IOException, NoSuchAlgorithmException {
		//opCode == OPCODE_CTN_MOBILITY
		String strData = "";
		for(int i=0; i < hostIP.length ;i++)
			strData = strData + String.format("%02x", hostIP[i]);
		for(int i=0; i < switchIP.length; i++)
			strData = strData + String.format("%02x", switchIP[i]);
		String firstSHA = sha256(strHostIP);
		peer.put(Number160.createHash(firstSHA)).setData(new Data(strData)).start();
	}*/

    //backup
	/*
	public void get(final int opCode, final String input, final byte switchNum, final byte[] byte_host_ip, final byte[] hashedIP) throws ClassNotFoundException, IOException, NoSuchAlgorithmException {



		if(opCode == DHTManagerHandler.OPCODE_GET_HASH){
			//In this case, input is a string of hostIP
			String firstSHA = sha256(input);
			FutureDHT futureDHT = peer.get(Number160.createHash(firstSHA)).start();
			futureDHT.addListener(new BaseFutureAdapter<FutureDHT>() {
				private byte lSwitchNum = switchNum;
				private byte[] lHashedIP = hashedIP.clone();
				@Override
				public void operationComplete(FutureDHT future)
						throws Exception {
					if (future.isSuccess()) {
						//Jaehyun implements sending UDP packet to OVS
						if(logging)System.out.println("OpCode = "+DHTManagerHandler.OPCODE_GET_HASH+", " + future.getData().getObject().toString());
						String recv_data = future.getData().getObject().toString();
						byte[] send_data = new byte[42];//Jaehee modified 160720

						send_data[0] = 0x01; //OPCODE_GET_HASH
						send_data[1] = lSwitchNum;
						for (int i = 0; i < 4;i++){
							send_data[2+(3-i)] = (byte) ((Character.digit(recv_data.charAt(i*2), 16) << 4) + Character.digit(recv_data.charAt(i*2+1), 16));
							send_data[26+i] = (byte) ((Character.digit(recv_data.charAt((i+4)*2), 16) << 4) + Character.digit(recv_data.charAt((i+4)*2+1), 16));
						}
						for (int i = 0; i < LM_HDR_LENGTH;i++){//Jaehee modified 160720
							send_data[6+i]=  hashedIP[i];
						}


						DHTManager.client_ch.writeAndFlush(
							new DatagramPacket(Unpooled.copiedBuffer(send_data), new InetSocketAddress("localhost",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);

					} else {
						byte[] send_data = new byte[42];//Jaehee modified 160720

						send_data[0] = 0x01;
						send_data[1] = switchNum;
						for (int i = 0; i < 4;i++){
							send_data[2+(3-i)] = byte_host_ip[i];
							send_data[26+i] = 0x00;
						}
						for (int i = 0; i < LM_HDR_LENGTH;i++){//Jaehee modified 160720
							send_data[6+i]=  hashedIP[i];
						}
						DHTManager.client_ch.writeAndFlush(
				                        new DatagramPacket(Unpooled.copiedBuffer(send_data), new InetSocketAddress("localhost",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);


						if(logging)System.out.println("Get Failed.");

					}

				}
			});
		} else if(opCode == DHTManagerHandler.OPCODE_GET_IP){
			//In this case, input is an objectKey
			FutureDHT futureDHT = peer.get(Number160.createHash(input)).start();
			futureDHT.addListener(new BaseFutureAdapter<FutureDHT>() {
				@Override
				public void operationComplete(FutureDHT future)
						throws Exception {
					if (future.isSuccess()) {
						//Jaehyun needs to implement sending UDP packet to OVS
						if(logging)System.out.println("OpCode = "+DHTManagerHandler.OPCODE_GET_IP+", " + future.getData().getObject().toString());
						String recv_data = future.getData().getObject().toString();
						byte[] send_data = new byte[42];//Jaehee modified 160720

						send_data[0] = 0x02; //OPCODE_GET_IP
						send_data[1] = switchNum;
						for (int i = 0; i < 4;i++){
							send_data[2+(3-i)] = (byte) ((Character.digit(recv_data.charAt(i*2), 16) << 4) + Character.digit(recv_data.charAt(i*2+1), 16));
							send_data[26+i] = (byte) ((Character.digit(recv_data.charAt((i+4)*2), 16) << 4) + Character.digit(recv_data.charAt((i+4)*2+1), 16));

						}
						for (int i = 0; i < LM_HDR_LENGTH;i++){//Jaehee modified 160720
							send_data[6+i]=  hashedIP[i];
						}

						DHTManager.client_ch.writeAndFlush(
							new DatagramPacket(Unpooled.copiedBuffer(send_data), new InetSocketAddress("localhost",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);

					} else {
						byte[] send_data = new byte[42];//Jaehee modified 160720

						send_data[0] = 0x02;
						send_data[1] = switchNum;
						for (int i = 0; i < 4;i++){
							send_data[2+(3-i)] = byte_host_ip[i];
							send_data[26+i] = 0x00;
						}
						for (int i = 0; i < LM_HDR_LENGTH;i++){ //Jaehee modified 160720
							send_data[6+i]=  hashedIP[i];
						}
						DHTManager.client_ch.writeAndFlush(
				                        new DatagramPacket(Unpooled.copiedBuffer(send_data), new InetSocketAddress("localhost",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);


						if(logging)System.out.println("Get Failed.");
					}

				}
			});
		} else {
			if(logging)System.out.println("Logical Error.");
		}
	}*/

    //backup
	/*
	public void store(String strHostIP, byte[] hostIP, byte[] switchIP) throws IOException, NoSuchAlgorithmException {
		//opCode == OPCODE_INFORM_CONNECTION
		String strData = "";
		for(int i=0; i < hostIP.length ;i++)
			strData = strData + String.format("%02x", hostIP[i]);
		for(int i=0; i < switchIP.length; i++)
			strData = strData + String.format("%02x", switchIP[i]);
		String firstSHA = sha256(strHostIP);
		peer.put(Number160.createHash(firstSHA)).setData(new Data(strData)).start();
	}*/
}