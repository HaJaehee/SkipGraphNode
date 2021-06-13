package skipnode;

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
