package skipnode;
/* -------------------------------------------------------- */
/**
 File name : DHTManagerCreator.java
 Rev. history : 2021-06-14
 Version : 1.1.6
 Added this class.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */


import java.util.ArrayList;

public class DHTManagerCreater {
    private static final int MAX_DHT_MNG_COUNT = 10;
    private static ArrayList<DHTManagerThread> dhtMngThrLst = null;
    private static boolean logging = false;
    DHTManagerCreater () {

    }

    public static void main(String[] args) {
        if (args.length == 5 || (args.length == 6 && args[5].equals("logging"))) {
            if (args[5].equals("logging")) {
                logging = true;
            }
            dhtMngThrLst = new ArrayList<DHTManagerThread>();
            if (MAX_DHT_MNG_COUNT > 0) {
                if (args[0].equals("0")) { // INITIAL_NODE = true;
                    String[] args2;
                    if (args.length == 5) { // w/o "logging"
                        args2 = new String[3];
                    }
                    else { // w/ "logging"
                        args2 = new String[4];
                        args2[3] = args[5];
                    }
                    args2[0] = args[0];
                    args2[1] = args[1];
                    args2[2] = args[2];
                    DHTManagerThread dhtManager = new DHTManagerThread(args2);
                    dhtManager.start();
                    if (logging)System.out.println("First DHT manager thread #0 is started.");
                    dhtMngThrLst.add(dhtManager);
                } else { // INITIAL_NODE = false;
                    DHTManagerThread dhtManager = new DHTManagerThread(args);
                    dhtManager.start();
                    if (logging)System.out.println("First DHT manager thread #0 is started.");
                    dhtMngThrLst.add(dhtManager);
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (MAX_DHT_MNG_COUNT > 1) {
                int dhtNum = Integer.parseInt(args[2]) + 1;
                for (int i = 1 ; i < MAX_DHT_MNG_COUNT ; i++) {
                    args[2] = dhtNum+"";
                    DHTManagerThread dhtManager = new DHTManagerThread(args);
                    dhtManager.start();
                    if (logging)System.out.println("DHT manager thread #"+i+" is started.");
                    dhtMngThrLst.add(dhtManager);
                    dhtNum++;
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        else{
            if(logging){
                System.out.println("Ambiguous Input.");
                System.out.println("Usage: java DHTManager [switch num] [locality id] [DHT node num] [introducer ip] [introducer port] ");
                System.out.println("Usage: java DHTManager [switch num] [locality id] [DHT node num] [introducer ip] [introducer port] logging");
                System.out.println("0 <= DHT node num < 50");
            }
            return;
        }
    }
}
