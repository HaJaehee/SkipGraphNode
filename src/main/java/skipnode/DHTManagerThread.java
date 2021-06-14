package skipnode;
/* -------------------------------------------------------- */
/**
 File name : DHTManagerThread.java
 Rev. history : 2021-06-14
 Version : 1.1.6
 Added this class.
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */


public class DHTManagerThread extends Thread {
    DHTManager dhtManager = null;
    DHTManagerThread (String[] args){
        dhtManager = new DHTManager(args);
    }
    @Override
    public void run() {
        super.run();
        try {
            dhtManager.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
