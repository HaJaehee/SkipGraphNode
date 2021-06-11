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

 //TODO
 //store(put), get(search),
 */
/* -------------------------------------------------------- */

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
import org.yaml.snakeyaml.Yaml;
import redis.clients.jedis.Jedis;
import underlay.Underlay;
import underlay.tcp.TCPUnderlay;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;


public final class DHTManager {

    //210611 disabled

    public static String[] swIPAddrList = {"10.0.10.1","10.0.20.1","10.0.30.1","10.0.40.1","10.0.50.1","10.64.0.1"};
    public static short edgeSWList[] = {1,2,3,0,0,6};
    public static int swCount = swIPAddrList.length;


    //public static Channel clientCh;
    public static int PORT;
    public static int nodeIndex;
    public static boolean justReset = false;
    public static DHTServer skipGraphServer = null;
    public static boolean logging = false;
    public static boolean logFileOut = false;
    public static String[] input = null;
    //public static int skipGraphServerPort = 8468;
    public static int ovsPort = 9999;
    public static Bootstrap bClient;
    public static final int DEFAULT_DHT_PORT = 21099;
    public static final int DEFAULT_DHT_MNG_PORT = 30001;
    public static int edgeNum = 0;
    public static int dhtNum = 0;
    public static String localityID = null;

    public static HashMap<String, String> kvMap= null;

    public static void main(String[] args) throws Exception {

        //input = new String[] {"0"}; // For test

        String interfaceName = "eth0";
        NetworkInterface networkInterface = NetworkInterface.getByName(interfaceName);
        Enumeration<InetAddress> inetAddress = networkInterface.getInetAddresses();
        InetAddress currentAddress;
        String ip = null;
        while(inetAddress.hasMoreElements())
        {
            currentAddress = inetAddress.nextElement();
            if(currentAddress instanceof Inet4Address && !currentAddress.isLoopbackAddress())
            {
                ip = currentAddress.toString().replaceAll("/", "");

                break;
            }
        }

        //ip = "172.30.1.41";

        kvMap = new HashMap<String, String>();

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

                skipGraphServer = new DHTServer(ip, DEFAULT_DHT_PORT+(edgeNum*100)+dhtNum, localityID, kvMap);
                System.out.println("Insert is done.");

            }
            else if ((args.length == 2 && args[1].equals("reset")) ||
                    (args.length == 3 && args[1].equals("reset") && args[2].equals("logging"))) {
                if(args.length == 3 && args[2].equals("logging")) {
                    logging = true;
                }
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
                skipGraphServer = new DHTServer(introducerIP, introducerPort, ip, DEFAULT_DHT_PORT+(100*edgeNum)+dhtNum, localityID, kvMap);
                System.out.println("Bootstrap is done.");

            }
            else{
                if(DHTManager.logging){
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
            int edgeNum = Integer.parseInt(args[0]);
            int dhtNum = Integer.parseInt(args[2]);
            nodeIndex = (100*edgeNum) + dhtNum;
        }
//        else { // input != null, only for test
//            if (input.length == 1) {
//                if(DHTManager.logging)System.out.println("The First node begins.");
//                System.out.println("The First node begins.");
//                //First node constructor requires its eth0 IP address, port number, and Locality ID.
//                //Also, first node constructor requires STATIC key-value Map object.
//                //TODO
//                skipGraphServer = new DHTServer(ip, 11099, null, kvMap);
//                if(DHTManager.logging)System.out.println("Bootstrap is done.");
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

        EventLoopGroup groupClient = new NioEventLoopGroup();
        bClient = new Bootstrap();
        bClient.group(groupClient)
                .channel(NioDatagramChannel.class)
                .handler(new ClientHandler());

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
                    .handler(new DHTManagerHandler(nodeIndex, justReset, logging));
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

    private static final int LM_HDR_LENGTH = 32;

    public DHTManagerHandler(int nodeIndex, boolean justReset, boolean logging) throws InterruptedException{
        super();

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
            if (DHTManager.edgeSWList[i] != 0) {
                byte[] swBytes = ByteBuffer.allocate(2).putShort((short) (DHTManager.edgeSWList[i] - 1)).array();
                sendBuf[6 + i * 2] = swBytes[0];
                sendBuf[6 + i * 2 + 1] = swBytes[1];
            }
        }



        if (DHTManager.logging) {
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
        System.out.println("Port "+DHTManager.PORT+" is opened.");
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
        //if(DHTManager.logging)System.err.println(packet.content().toString(CharsetUtil.UTF_8));
        ByteBuf payload = packet.content();

        //if(DHTManager.logging)System.out.println("[Node "+DHTManager.nodeNum+"] Received Message: "+payload.toString());

        byte opCode = payload.readByte();
        byte switchNum = payload.readByte();
        byte switchIndex = (byte)(switchNum-1);
        //nHost is originally 4byte value
        long nHost = ntohl(payload.readUnsignedInt());
        int nHostInt = (int)nHost & 0xFFFFFFFF;
        byte[] byteHostIP = ByteBuffer.allocate(4).putInt(nHostInt).array();

        String strIP = String.format("%d.%d.%d.%d",(nHost & 0xFF), (nHost >> 8 & 0xFF), (nHost >> 16 & 0xFF), (nHost >> 24 & 0xFF));
        if(DHTManager.logging)System.out.printf("[Node %d] Received Message: opCode=%d,  switchNum=%d, IP=%s, SWIP = %s\n",DHTManager.nodeIndex, opCode, switchNum, strIP, "127.0.0.1");

        if (opCode == OPCODE_BOOTUP){
            if(DHTManager.logging)System.out.println("opCode 0: show edge switchs list.");
            if(DHTManager.logging)System.out.println("opCode 0 will be supported soon.");

        } /*else if (opCode == OPCODE_GET_HASH){  //deprecated by jaehee 170414
        	if(DHTManager.logging)System.out.println("opCode 1: get Object ID from DHT server with digested IP");
        	//make Object ID and query to DHT table
			MessageDigest mDigest = MessageDigest.getInstance("SHA-256");
			byte[] strDig = mDigest.digest(strIP.getBytes());
			if(DHTManager.logging)System.out.println("Hashed IP: "+strDig+", length: "+strDig.length);
			DHTManager.skipGraphServer.get(opCode, strIP, switchNum, byteHostIP, strDig);
			//---------------client example
//			String opCode = OPCODE_GET_HASH;
//			String swNum = "02";
//			String hostIP = "0a000a03";
//			String strInput = opCode+swNum+hostIP;
        }*/ else if (opCode == OPCODE_GET_IP){
            if(DHTManager.logging)System.out.println("opCode 2: get Host IP from DHT server with Object ID.");
            //copy payload(Object ID) to strDig byte array and query to DHT table
            byte[] strDig = new byte[LM_HDR_LENGTH]; //Jaehee modified 160720

            for (int i = 0;i < LM_HDR_LENGTH;i++){ //Jaehee modified 160720
                strDig[i] = payload.readByte();

            }
            if(DHTManager.logging){
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


            DHTManager.skipGraphServer.get(opCode, sb.toString(), switchNum, byteHostIP, strDig);

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
            if(DHTManager.logging)System.out.println("opCode 3: store DHT server.");

            //byte[] byte_switch_ip = new byte[4];
            String strSWIP = DHTManager.swIPAddrList[switchIndex];
            int nSwitchIP = iptoint(strSWIP) & 0xFFFFFFFF;
            byte[] reverseByteSwitchIP =  ByteBuffer.allocate(4).putInt(nSwitchIP).array();
            byte[] byteSwitchIP = new byte[4];
            for (int i = 0;i < 4;i++) {
                byteSwitchIP[3-i] = reverseByteSwitchIP[i];
            }

            if(DHTManager.logging)System.out.printf("[Node %d] Storing the pair: (Host IP=%s, Switch IP=%s)\n", DHTManager.nodeIndex,strIP,strSWIP);

            DHTManager.skipGraphServer.store(strIP, byteHostIP, byteSwitchIP);

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

            if(DHTManager.logging){
                System.out.println("Object ID: ");

                for (int i = 0;i < LM_HDR_LENGTH;i++){
                    System.out.printf("%02x",(strDig[i]&0xff));
                }
                System.out.println();
            }

            // Update the cache of the edges in the edge list.
            for (int i = 0;i < DHTManager.swCount;i++){
                if (DHTManager.edgeSWList[i] != 0 && DHTManager.edgeSWList[i] != switchNum){

                    byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList[i]).array();
                    sendBuf[1] = swByte[1];
                    int swListIndex = DHTManager.edgeSWList[i]-1;


                    Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
                    clientCh.writeAndFlush(
                            new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList[swListIndex],DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
                }
            }
            byte[] swByte = ByteBuffer.allocate(2).putShort(switchNum).array();
            sendBuf[1] = swByte[1];
            if(DHTManager.logging)System.out.printf("receiving SW IP="+"127.0.0.1"+",port="+DHTManager.ovsPort+".\n");

            Channel clientCh = DHTManager.bClient.bind(0).sync().channel();

            clientCh.writeAndFlush(
                    new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress("127.0.0.1",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);


//			String opCode = OPCODE_INFORM_CONNECTION;
//			String swNum = "02";
//			String hostIP = "0a000a02";
//			String swIP = "0a000a01";
//			String strInput = opCode+swNum+hostIP+swIP;

        } else if (opCode == OPCODE_APP_MOBILITY){
            if(DHTManager.logging)System.out.println("opCode 4: application mobility.");

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

            if(DHTManager.logging){

                String strVisitingESIP = String.format("%d.%d.%d.%d",(nVisitingESIP & 0xFF), (nVisitingESIP >> 8 & 0xFF), (nVisitingESIP >> 16 & 0xFF), (nVisitingESIP >> 24 & 0xFF));


                System.out.printf("[Node %d] Storing the pair: (Original Host IP=%s, Port Number=%s, New Host IP=%s, New Edge Switch IP=%s)\n", DHTManager.nodeIndex, strHomeTargetHostIP, strPortNumber, strVisitingTargetHostIP, strVisitingESIP);
            }

            DHTManager.skipGraphServer.store(strHomeTargetHostIP+strPortNumber, strVisitingTargetHostIP+strPortNumber, byteVisitingTargetHostIP, byteVisitingESIP, byteHomeTargetHostIP);

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

            if(DHTManager.logging){
                System.out.println("Object ID: ");

                for (int i = 0;i < LM_HDR_LENGTH;i++){
                    System.out.printf("%02x",(strDig[i]&0xff));
                }
                System.out.println();
            }

            for (int i = 0;i < DHTManager.swCount;i++){
                if (DHTManager.edgeSWList[i] != 0 && DHTManager.edgeSWList[i] != switchNum){

                    byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList[i]).array();
                    sendBuf[1] = swByte[1];
                    int swListIndex = DHTManager.edgeSWList[i]-1;
                    if(DHTManager.logging)System.out.printf("receiving SW IP="+DHTManager.swIPAddrList[swListIndex]+",port="+DHTManager.ovsPort+".\n");


                    Channel clientCh = DHTManager.bClient.bind(0).sync().channel();

                    clientCh.writeAndFlush(
                            new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList[swListIndex],DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
                }
            }
            byte[] swByte = ByteBuffer.allocate(2).putShort(switchNum).array();
            sendBuf[1] = swByte[1];
            if(DHTManager.logging)System.out.printf("receiving SW IP="+"127.0.0.1"+",port="+DHTManager.ovsPort+".\n");


            Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
            clientCh.writeAndFlush(
                    new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress("127.0.0.1",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);

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
				byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList[i]).array();
				sendBuf[1] = swByte[1];
				int swListIndex = DHTManager.edgeSWList[i]-1;
		        Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
                clientCh.writeAndFlush(
    	                        new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList[swListIndex],DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
			}*/

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
            if(DHTManager.logging)System.out.println("opCode 5: container mobility.");

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

            if(DHTManager.logging){

                String strVisitingESIP = String.format("%d.%d.%d.%d",(nVisitingESIP & 0xFF), (nVisitingESIP >> 8 & 0xFF), (nVisitingESIP >> 16 & 0xFF), (nVisitingESIP >> 24 & 0xFF));
                String strVisitingTargetHostIP = String.format("%d.%d.%d.%d",(nVisitingTargetHost & 0xFF), (nVisitingTargetHost >> 8 & 0xFF), (nVisitingTargetHost >> 16 & 0xFF), (nVisitingTargetHost >> 24 & 0xFF));

                System.out.printf("[Node %d] Storing the pair: (Original Cnt IP=%s, New Ctn IP=%s, New Host IP=%s, New Edge Switch IP=%s)\n", DHTManager.nodeIndex, strHomeCTIP, strVisitingCTIP, strVisitingTargetHostIP, strVisitingESIP);
            }
            DHTManager.skipGraphServer.store(strHomeCTIP, strVisitingCTIP, byteVisitingCTIP, byteVisitingESIP, byteVisitingTargetHostIP, byteHomeCTIP);

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
            if(DHTManager.logging){
                System.out.println("Object ID: ");

                for (int i = 0;i < LM_HDR_LENGTH;i++){
                    System.out.printf("%02x",(strDig[i]&0xff));
                }
                System.out.println();
            }

            for (int i = 0;i < DHTManager.swCount;i++){
                if (DHTManager.edgeSWList[i] != 0 && DHTManager.edgeSWList[i] != switchNum){

                    byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList[i]).array();
                    sendBuf[1] = swByte[1];
                    int swListIndex = DHTManager.edgeSWList[i]-1;
                    if(DHTManager.logging)System.out.printf("receiving SW IP="+DHTManager.swIPAddrList[swListIndex]+",port="+DHTManager.ovsPort+".\n");

                    Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
                    clientCh.writeAndFlush(
                            new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList[swListIndex],DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
                }
            }
            byte[] swByte = ByteBuffer.allocate(2).putShort(switchNum).array();
            sendBuf[1] = swByte[1];
            if(DHTManager.logging)System.out.printf("receiving SW IP="+"127.0.0.1"+",port="+DHTManager.ovsPort+".\n");


            Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
            clientCh.writeAndFlush(
                    new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress("127.0.0.1",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);


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
				byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList[i]).array();
				sendBuf[1] = swByte[1];
				int swListIndex = DHTManager.edgeSWList[i]-1;
		        Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
                clientCh.writeAndFlush(
    	                        new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList[swListIndex],DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
			}*/

            //---------------client example
//			String opCode = OPCODE_CTN_MOBILITY;
//			String swNum = "01";
//			String homeCtnIP = "0a000a03";
//			String newCtnIP = "0a000a03";
//			String newESIP = "0a001401";
//			String newHostIP = "0a001402";
//			String strInput = opCode+swNum+homeCtnIP+newCtnIP+newESIP+newHostIP;

        } else if (opCode == OPCODE_GET_HASH){
            if(DHTManager.logging)System.out.println("opCode 1: get Object ID from DHT server with digested IP");
            //if(DHTManager.logging)System.out.println("opCode 6: get ip:port");


            byte[] bytePortNumber = {payload.readByte(),payload.readByte()};
            String strPortNumber = ":"+((bytePortNumber[1] & 0xFF) + ((bytePortNumber[0] & 0xFF)*0x100));

            MessageDigest mDigest = MessageDigest.getInstance("SHA-256");
            byte[] strDig = mDigest.digest((strIP).getBytes());

            if(DHTManager.logging)System.out.printf("[Node %d] Getting the pair: (Host IP:Port Number=%s%s)\n", DHTManager.nodeIndex, strIP, strPortNumber);

            DHTManager.skipGraphServer.get(OPCODE_GET_HASH, strIP+strPortNumber, switchNum, byteHostIP, strDig);


            //---------------client example
//			String opCode = OPCODE_GET_HASH;
//			String swNum = "01";
//			String homeTargetHostIP = "0a000a02";
//			String portNumber = "0050";
//			String strInput = opCode+swNum+homeTargetHostIP+portNumber;

        } /*else if (opCode == OPCODE_TOGGLE_LOGGING){
        	if(DHTManager.logging)System.out.println("opCode 101: Toggle ovs logging");
        	byte[] sendBuf = new byte[2];
            sendBuf[0] = OPCODE_TOGGLE_LOGGING;
            for (int i = 0;i < DHTManager.swCount;i++){
				if (DHTManager.edgeSWList[i] != 0 && DHTManager.edgeSWList[i] != switchNum){
                    byte[] swByte = ByteBuffer.allocate(2).putShort(DHTManager.edgeSWList[i]).array();
				    sendBuf[1] = swByte[1];
				    int swListIndex = DHTManager.edgeSWList[i]-1;
			        Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
				    clientCh.writeAndFlush(
    	                        new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress(DHTManager.swIPAddrList[swListIndex],DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
                }
			}
			byte[] swByte = ByteBuffer.allocate(2).putShort(switchNum).array();
			sendBuf[1] = swByte[1];
			if(DHTManager.logging)System.out.printf("receiving SW IP="+"127.0.0.1"+",port="+DHTManager.ovsPort+".\n");
	        Channel clientCh = DHTManager.bClient.bind(0).sync().channel();
			clientCh.writeAndFlush(
    	         new DatagramPacket(Unpooled.copiedBuffer(sendBuf), new InetSocketAddress("127.0.0.1",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);
            //---------------client example
//			String opCode = "OPCODE_TOGGLE_LOGGING";
//			String swNum = "01";
        }*/
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
        if(DHTManager.logging)System.out.println("Must not be executed.");
    }
}

class DHTServer {
    private static int LEVEL = 5;
    private static Jedis jedis = null;

    private SkipNode ipAddressAwareNode = null;
    private SkipNode localityAwareNode = null;

    private static String localityID = null;

    private static final byte OPCODE_BOOTUP = 0;
    private static final byte OPCODE_GET_HASH = 1;
    private static final byte OPCODE_GET_IP = 2;
    private static final byte OPCODE_INFORM_CONNECTION = 3;
    private static final byte OPCODE_APP_MOBILITY = 4;
    private static final byte OPCODE_CTN_MOBILITY = 5;
    private static final byte OPCODE_GET_IPPORT = 6;

    private static final byte OPCODE_QUERIED_HASH = 1;
    private static final byte OPCODE_QUERIED_IP = 2;
    private static final byte OPCODE_UPDATE_IP = 3;
    private static final byte OPCODE_NEW_APP = 4;
    private static final byte OPCODE_NEW_CTN = 5;


    private static final int VISITING_IP = 1;
    private static final int ES_IP = 2;
    private static final int VISITING_TARGET_HOST = 3;
    private static final int HOME_TARGET_HOST = 4;
    private static final int HOME_IP = 5;

    private static final int LM_HDR_LENGTH = 32;

    public void createIPAddressAwareNode (String ip, int portNumber, HashMap kvMap) {
        createIPAddressAwareNode(null, 0, ip, portNumber, kvMap);
    }

    //backlog
    //DUPLICATION CHECK
    public void createIPAddressAwareNode (String introducerIP, int introducerPortNumber, String ip, int portNumber, HashMap kvMap) {
        LookupTableFactory factory = new LookupTableFactory();
        ConcurrentLookupTable table = (ConcurrentLookupTable) factory.createDefaultLookupTable(LEVEL);

        BigInteger numId = null;
        try {
            numId = new BigInteger(sha256(ip+":"+portNumber), 16);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        String[] ipSplit = ip.split("\\.");
        String nameId = "";
        //ONLY USE THIRD IP FIELD 8 bits FOR Prefix
        Integer item = Integer.parseInt(ipSplit[2]);
        nameId = "1" + String.format("%8s", Integer.toBinaryString(item)).replaceAll(" ", "0").substring(1);

        //name ID body
        item = Integer.parseInt(ipSplit[3]);
        nameId = nameId + String.format("%8s", Integer.toBinaryString(item)).replaceAll(" ", "0");

        //Supposed to randomly generated.
        item = (100*DHTManager.edgeNum)+DHTManager.dhtNum;
        nameId = nameId + String.format("%16s", Integer.toBinaryString(item)).replaceAll(" ", "0");
        //Length of nameID is finally 32.

        System.out.println("IA name ID: " + nameId + " len: " + nameId.length());
        System.out.println("IA port: "+portNumber);

        SkipNodeIdentity identity = new SkipNodeIdentity(nameId, numId, ip, portNumber,null, null);

        ipAddressAwareNode = new SkipNode(identity, table, false, kvMap);

        Underlay underlay = new TCPUnderlay();
        underlay.initialize(portNumber);
        MiddleLayer middleLayer = new MiddleLayer(underlay, ipAddressAwareNode);
        ipAddressAwareNode.setMiddleLayer(middleLayer);
        underlay.setMiddleLayer(middleLayer);

        ipAddressAwareNode.insert(introducerIP, introducerPortNumber);
    }

    //backlog
    //DUPLICATION CHECK
    public void createLocalityAwareNode (String introducerIP, int introducerPortNumber, String ip, int portNumber, String localityID, HashMap kvMap) {
        LookupTableFactory factory = new LookupTableFactory();
        ConcurrentLookupTable table = (ConcurrentLookupTable) factory.createDefaultLookupTable(LEVEL);

        String nameId = "";

        nameId = localityID;
        //Supposed to randomly generated.
        int item = (100*DHTManager.edgeNum)+DHTManager.dhtNum;

        nameId = nameId + "000" + String.format("%16s", Integer.toBinaryString(item)).replaceAll(" ", "0");
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

        localityAwareNode = new SkipNode(identity, table, false, kvMap);

        Underlay underlay = new TCPUnderlay();
        underlay.initialize(portNumber);
        MiddleLayer middleLayer = new MiddleLayer(underlay, localityAwareNode);
        localityAwareNode.setMiddleLayer(middleLayer);
        underlay.setMiddleLayer(middleLayer);

        System.out.println("introducer IP: " + introducerIP + " introducer port: " + introducerPortNumber);
        localityAwareNode.insert(introducerIP, introducerPortNumber);
    }

    public void createLocalityAwareNode (String ip, int portNumber, String localityID, HashMap kvMap) {
        createLocalityAwareNode(null, 0, ip, portNumber, localityID, kvMap);
    }

    public DHTServer(String ip, int portNumber, String localityID, HashMap kvMap) throws Exception {

        createIPAddressAwareNode(ip, portNumber, kvMap);
        if (localityID != null) {
            this.localityID = localityID;
            //Insert this node into DHT network.
            createLocalityAwareNode(ip, portNumber, ip, portNumber, localityID, kvMap);
        }
    }

    public DHTServer(String introducerIP, int introducerPortNumber, String ip, int portNumber, String localityID, HashMap kvMap) throws Exception {
        createIPAddressAwareNode(introducerIP, introducerPortNumber, ip, portNumber, kvMap);
        if (localityID != null) {
            this.localityID = localityID;
            createLocalityAwareNode(introducerIP, introducerPortNumber, ip, portNumber, localityID, kvMap);
        }
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

    //TODO
    public void get(final int opCode, final String input, final byte switchNum, final byte[] byteHostIP, final byte[] hashedIP) throws ClassNotFoundException, IOException, NoSuchAlgorithmException {

        final Date date = new Date();
        final long starttime = date.getTime();

        //MUST TODO

        if(opCode == OPCODE_GET_HASH){
            //In this case, input is a string of hostIP:port
            byte lswitchNum = switchNum;
            byte[] lbyteHostIP = byteHostIP.clone();
            byte[] lhashedIP = hashedIP.clone();

            String strIP = input.split(":")[0];
            String firstSHA = sha256(strIP);
            boolean isLocalityIdSame = false;
            if (localityID != null && localityAwareNode != null) {
                //Search entry with MOID on Edge's locality site
                String valueLA = localityAwareNode.getResourceByNameID(localityID, firstSHA);
                if (valueLA != null) { //Hit
                    //TODO
                    //Revise entry's locator from prior Edge to recent Edge
                    //Jaehyun implements sending UDP packet to OVS
                    if(DHTManager.logging)System.out.println("OpCode = "+OPCODE_GET_HASH+", " + valueLA);
                    String foundData = valueLA;

                    JsonParser parser = new JsonParser();
                    JsonObject jobj = new JsonObject();
                    jobj = (JsonObject) parser.parse(foundData);

                    String recvData = jobj.get(VISITING_IP+"").getAsString() + jobj.get(ES_IP+"").getAsString();

                    byte[] sendData = new byte[43];//Jaehee modified 160720

                    sendData[0] = OPCODE_QUERIED_HASH;
                    sendData[1] = lswitchNum;
                    for (int i = 0; i < 4;i++){
                        sendData[2+(3-i)] = (byte) ((Character.digit(recvData.charAt(i*2), 16) << 4) + Character.digit(recvData.charAt(i*2+1), 16));
                        sendData[6+LM_HDR_LENGTH+(3-i)] = (byte) ((Character.digit(recvData.charAt((i+4)*2), 16) << 4) + Character.digit(recvData.charAt((i+4)*2+1), 16));
                    }//Jaehee modified 160720
                    for (int i = 0; i < LM_HDR_LENGTH;i++){//Jaehee modified 160720
                        sendData[6+i]=  lhashedIP[i];
                    }
                    sendData[42]='\0';


                    Channel clientCh = null;
                    try {
                        clientCh = DHTManager.bClient.bind(0).sync().channel();
                        clientCh.writeAndFlush(
                                new DatagramPacket(Unpooled.copiedBuffer(sendData), new InetSocketAddress("localhost",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if(DHTManager.logging) {
                        System.out.println("send oid:");
                        for (int i = 0 ; i < LM_HDR_LENGTH ; i++) {
                            System.out.printf("%02x",sendData[6+i]);
                        }
                        System.out.println();
                    }
                    if(DHTManager.logFileOut)
                    {
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
                    //GOTO (2)
                }
                else { // Fail
                    //Search entry with network address of IP address on IP address approach
                    String[] ipSplit = input.split("\\.");
                    String nameId = "";
                    //ONLY USE THIRD IP FIELD 8 bits FOR Prefix
                    Integer item = Integer.parseInt(ipSplit[2]);
                    nameId = "1" + String.format("%8s", Integer.toBinaryString(item)).replaceAll(" ", "0").substring(1);
                    String valueIA = ipAddressAwareNode.getResourceByNameID(nameId, firstSHA);
                    if (valueIA != null) { //Hit
                        //Revise entry's locator from prior Edge to recent Edge
                        //GOTO (2)

                    }
                    else { // Fail
                        // Search entry with MOID on DHT network
                        String valueHash = ipAddressAwareNode.getResource(firstSHA);
                        if (valueHash != null) { //Hit
                            //GOTO (3)
                        }
                        else { // Fail
                            // Write new entry, home site is recent Edge
                            /*
                            if(DHTManager.logging)System.out.println("Get Failed.");

                            firstSHA = sha256(input).getBytes();
                            DHTManager.skipGraphServer.get(OPCODE_GET_IPPORT, input, switchNum, lbyteHostIP, firstSHA);

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
                    }
                }
                if (isLocalityIdSame) { //(2) is entry's locality ID same to this locality ID?
                    //(4) Update entry on this locality site and replicate it
                }
                else { // !isLocalityIdSame
                    //(3) Keep prior locality ID and revise entry's locality ID
                    //Update entry on this locality site
                    //Update entry on prior locality site
                }
                // Return entry to OvS kernal module

            }

        }
        else if(opCode == OPCODE_GET_IP){
            //In this case, input is an objectKey
            String firstSHA = input;
            boolean isLocalityIdSame = false;
            if (localityID != null && localityAwareNode != null) {
                //Search entry with MOID on Edge's locality site
                String valueLA = localityAwareNode.getResourceByNameID(localityID, firstSHA);
                if (valueLA != null) { //Hit
                    //TODO
                    //Revise entry's locator from prior Edge to recent Edge


                    //Jaehyun needs to implement sending UDP packet to OVS
                    if(DHTManager.logging)System.out.println("OpCode = "+OPCODE_GET_IP+", " + valueLA);
                    String foundData = valueLA;

                    JsonObject jobj = new JsonObject();
                    jobj = (JsonObject) new JsonParser().parse(foundData);

                    String recvData = jobj.get(VISITING_IP+"").getAsString() + jobj.get(ES_IP+"").getAsString();

                    byte[] sendData = new byte[43];//Jaehee modified 160720

                    sendData[0] = OPCODE_QUERIED_IP;
                    sendData[1] = switchNum;
                    for (int i = 0; i < 4;i++){
                        sendData[2+(3-i)] = (byte) ((Character.digit(recvData.charAt(i*2), 16) << 4) + Character.digit(recvData.charAt(i*2+1), 16));
                        sendData[6+LM_HDR_LENGTH+(3-i)] = (byte) ((Character.digit(recvData.charAt((i+4)*2), 16) << 4) + Character.digit(recvData.charAt((i+4)*2+1), 16));

                    }//Jaehee modified 160720
                    for (int i = 0; i < LM_HDR_LENGTH;i++){//Jaehee modified 160720
                        sendData[6+i]=  hashedIP[i];
                    }

                    sendData[42]= '\0';

                    Channel clientCh = null;
                    try {
                        clientCh = DHTManager.bClient.bind(0).sync().channel();
                        clientCh.writeAndFlush(
                                new DatagramPacket(Unpooled.copiedBuffer(sendData), new InetSocketAddress("localhost",DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if(DHTManager.logging) {
                        System.out.println("send oid:");
                        for (int i = 0 ; i < LM_HDR_LENGTH ; i++) {
                            System.out.printf("%02x",sendData[6+i]);
                        }
                        System.out.println();
                    }
                    if (DHTManager.logFileOut) {
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
                    //GOTO (2)
                } else { // Fail
                    //Search entry with network address of IP address on IP address approach
                    // Search entry with MOID on DHT network
                    String valueHash = ipAddressAwareNode.getResource(firstSHA);
                    if (valueHash != null) { //Hit
                        //GOTO (3)
                    } else { // Fail
                        // Write new entry, home site is recent Edge
                        /*
                        if(DHTManager.logging)System.out.println("Get Failed.");

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

                        if(DHTManager.logging) {
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
                        }

                         */

                        //GOTO (4)
                    }

                }
                if (isLocalityIdSame) { //(2) is entry's locality ID same to this locality ID?
                    //(4) Update entry on this locality site and replicate it
                } else { // !isLocalityIdSame
                    //(3) Keep prior locality ID and revise entry's locality ID
                    //Update entry on this locality site
                    //Update entry on prior locality site
                }
                // Return entry to OvS kernal module
            }

        }
        else if(opCode == OPCODE_GET_IPPORT){
            //In this case, input is a string of hostIP:Port Number
            byte lswitchNum = switchNum;
            byte[] lbyteHostIP = byteHostIP.clone();
            byte[] lhashedIP = hashedIP.clone();
            String firstSHA = sha256(input);
            boolean isLocalityIdSame = false;
            if (localityID != null && localityAwareNode != null) {
                //Search entry with MOID on Edge's locality site
                String valueLA = localityAwareNode.getResourceByNameID(localityID, firstSHA);
                if (valueLA != null) { //Hit
                    //TODO
                    //Revise entry's locator from prior Edge to recent Edge
                    //Jaehyun implements sending UDP packet to OVS
                    if (DHTManager.logging)
                        System.out.println("OpCode = " + OPCODE_GET_IPPORT + ", " + valueLA);
                    String foundData = valueLA;
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
                    JsonObject jobj = new JsonObject();


                    jobj = (JsonObject) parser.parse(foundData);
                    String recvData = jobj.get(HOME_TARGET_HOST + "").getAsString() + jobj.get(ES_IP + "").getAsString() + jobj.get(VISITING_IP + "").getAsString() + strPort;

                    byte[] sendData = new byte[49];//Jaehee modified 160720

                    sendData[0] = OPCODE_NEW_APP;
                    sendData[1] = lswitchNum;
                    for (int i = 0; i < 4; i++) {
                        sendData[2 + (3 - i)] = (byte) ((Character.digit(recvData.charAt(i * 2), 16) << 4) + Character.digit(recvData.charAt(i * 2 + 1), 16)); //HOME_TARGET_HOST
                        sendData[6 + (3 - i)] = (byte) ((Character.digit(recvData.charAt((i + 4) * 2), 16) << 4) + Character.digit(recvData.charAt((i + 4) * 2 + 1), 16)); //ES_IP
                        sendData[10 + (3 - i)] = (byte) ((Character.digit(recvData.charAt((i + 8) * 2), 16) << 4) + Character.digit(recvData.charAt((i + 8) * 2 + 1), 16)); //VISITING_IP
                    }//Jaehee modified 160720

                    for (int i = 0; i < 2; i++) {
                        sendData[14 + i] = (byte) ((Character.digit(recvData.charAt((i + 12) * 2), 16) << 4) + Character.digit(recvData.charAt((i + 12) * 2 + 1), 16)); //strPort
                    }
                    for (int i = 0; i < LM_HDR_LENGTH; i++) {//Jaehee modified 160720
                        sendData[16 + i] = lhashedIP[i];
                    }

                    sendData[48] = '\0';

                    Channel clientCh = null;
                    try {
                        clientCh = DHTManager.bClient.bind(0).sync().channel();
                        clientCh.writeAndFlush(
                                new DatagramPacket(Unpooled.copiedBuffer(sendData), new InetSocketAddress("localhost", DHTManager.ovsPort))).addListener(ChannelFutureListener.CLOSE);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (DHTManager.logging) {
                        System.out.println("send oid:");
                        for (int i = 0; i < LM_HDR_LENGTH; i++) {
                            System.out.printf("%02x", sendData[16 + i]);
                        }
                        System.out.println();
                    }
                    if (DHTManager.logFileOut) {
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
                    }

                    //GOTO (2)
                } else { // Fail
                    //Search entry with network address of IP address on IP address approach
                    String[] ipSplit = input.split("\\.");
                    String nameId = "";
                    //ONLY USE THIRD IP FIELD 8 bits FOR Prefix
                    Integer item = Integer.parseInt(ipSplit[2]);
                    nameId = "1" + String.format("%8s", Integer.toBinaryString(item)).replaceAll(" ", "0").substring(1);
                    String valueIA = ipAddressAwareNode.getResourceByNameID(nameId, firstSHA);
                    if (valueIA != null) { //Hit
                        //Revise entry's locator from prior Edge to recent Edge
                        //GOTO (2)

                    } else { // Fail
                        // Search entry with MOID on DHT network
                        String valueHash = ipAddressAwareNode.getResource(firstSHA);
                        if (valueHash != null) { //Hit
                            //GOTO (3)
                        } else { // Fail
                            // Write new entry, home site is recent Edge
                            /*
                            if(DHTManager.logging)System.out.println("Get Failed.");

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



                            if(DHTManager.logging) {
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
                    }
                }
                if (isLocalityIdSame) { //(2) is entry's locality ID same to this locality ID?
                    //(4) Update entry on this locality site and replicate it
                } else { // !isLocalityIdSame
                    //(3) Keep prior locality ID and revise entry's locality ID
                    //Update entry on this locality site
                    //Update entry on prior locality site
                }
                // Return entry to OvS kernal module

                //NOT TODO
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
//						if(DHTManager.logging)System.out.println("Get Failed");
//					}
            }
        }
    }

    //TODO
    public void store(String strHostIP, byte[] hostIP, byte[] switchIP) throws IOException, NoSuchAlgorithmException {
        //opCode == OPCODE_INFORM_CONNECTION

        StringBuilder jsonString = new StringBuilder();
        String strData = "";
        for(int i=0; i < hostIP.length ;i++)
            strData += String.format("%02x", hostIP[i]);


        jsonString.append("{ \"" + VISITING_IP + "\" : \"" + strData + "\",");


        strData = "";
        for(int i=0; i < switchIP.length; i++)
            strData += String.format("%02x", switchIP[i]);

        jsonString.append("\""+ES_IP+"\" : \""+ strData +"\" }");


        String firstSHA = sha256(strHostIP);

        //TODO
        //if(DHTManager.logging)System.out.println("key: "+Number160.createHash(firstSHA)+", data: "+jsonString.toString());
        //peer.put(Number160.createHash(firstSHA)).setData(new Data(jsonString.toString())).start();
    }

    public void store(String originalHostIPPort, String visitingTargetHostIPPort, byte[] visitingTargetHostIP, byte[] switchIP, byte[] homeTargetHostIP) throws IOException, NoSuchAlgorithmException {
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
        jsonString.append("\""+HOME_TARGET_HOST+"\" : \""+ strData +"\" }");


        String firstSHA = sha256(originalHostIPPort);

        //TODO
//        if(DHTManager.logging)System.out.println("key: "+Number160.createHash(firstSHA)+", data: "+jsonString.toString());
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
//        if(DHTManager.logging)System.out.println("key: "+Number160.createHash(firstSHA_2)+", data: "+jsonString.toString());
//        peer.put(Number160.createHash(firstSHA_2)).setData(new Data(jsonString.toString())).start();
        jsonString.setLength(0);
        //jsonString2.setLength(0);
    }

    public void store(String strHomeCTIP, String strSwitchedIP, byte[] visitingCtnIP, byte[] switchIP, byte[] visitingTargetHostIP, byte[] homeCtnIP) throws IOException, NoSuchAlgorithmException {
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
        jsonString.append("\""+VISITING_TARGET_HOST+"\" : \""+ strData +"\" }");


        String firstSHA = sha256(strHomeCTIP);

        //TODO
//        if(DHTManager.logging)System.out.println("key: "+Number160.createHash(firstSHA)+", data: "+jsonString.toString());
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
//        if(DHTManager.logging)System.out.println("key: "+Number160.createHash(firstSHA_2)+", data: "+jsonString.toString());
//        peer.put(Number160.createHash(firstSHA_2)).setData(new Data(jsonString.toString())).start();
        jsonString.setLength(0);
    }
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
				private byte lswitchNum = switchNum;
				private byte[] lbyte_host_ip = byte_host_ip.clone();
				private byte[] lhashedIP = hashedIP.clone();
				@Override
				public void operationComplete(FutureDHT future)
						throws Exception {
					if (future.isSuccess()) {
						//Jaehyun implements sending UDP packet to OVS
						if(DHTManager.logging)System.out.println("OpCode = "+DHTManagerHandler.OPCODE_GET_HASH+", " + future.getData().getObject().toString());
						String recv_data = future.getData().getObject().toString();
						byte[] send_data = new byte[42];//Jaehee modified 160720

						send_data[0] = 0x01; //OPCODE_GET_HASH
						send_data[1] = lswitchNum;
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


						if(DHTManager.logging)System.out.println("Get Failed.");

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
						if(DHTManager.logging)System.out.println("OpCode = "+DHTManagerHandler.OPCODE_GET_IP+", " + future.getData().getObject().toString());
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


						if(DHTManager.logging)System.out.println("Get Failed.");
					}

				}
			});
		} else {
			if(DHTManager.logging)System.out.println("Logical Error.");
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

    public static SkipNode createNodeTest(String fileName, int increment) {

        Yaml yparser = new Yaml();
        Reader yamlFile = null;
        try {
            yamlFile = new FileReader(fileName);
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
            System.out.println("My local address is "+address);
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

        SkipNode node = new SkipNode(identity, table, true);

        Underlay underlay = new TCPUnderlay();
        underlay.initialize(port);
        MiddleLayer middleLayer = new MiddleLayer(underlay, node);
        node.setMiddleLayer(middleLayer);
        underlay.setMiddleLayer(middleLayer);

        node.insert(introducerAddress, introducerPort);

        return node;
    }
}