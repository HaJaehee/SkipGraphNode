package skipnode;

import java.util.ArrayList;

public class DHTManagerCreater {
    public static final int MAX_DHT_MNG_COUNT = 10;
    public static boolean INITIAL_NODE = false;
    DHTManagerCreater () {

    }

    public static void main(String[] args) {
        if (args.length == 5 || (args.length == 6 && args[5].equals("logging"))) {
            if (args[0].equals("0")) {
                INITIAL_NODE = true;
            }
            ArrayList<DHTManagerThread> dhtManagerThrLst = new ArrayList<DHTManagerThread>();
            if (MAX_DHT_MNG_COUNT > 0) {
                if (INITIAL_NODE) {

                    if (args.length == 5) {
                        String[] args2 = {"0", args[1], args[2]};
                    }
                    else { 

                    }
                    DHTManagerThread dhtManager = new DHTManagerThread(args2);
                    dhtManager.start();

                } else {

                }
            }
            if (MAX_DHT_MNG_COUNT > 1) {

            }
        }
        else{
            if(DHTManager.logging){
                System.out.println("Ambiguous Input.");
                System.out.println("Usage: java DHTManager [switch num] [locality id] [DHT node num] [introducer ip] [introducer port] ");
                System.out.println("Usage: java DHTManager [switch num] [locality id] [DHT node num] [introducer ip] [introducer port] logging");
                System.out.println("0 <= DHT node num < 50");
            }
            return;
        }
    }
}
