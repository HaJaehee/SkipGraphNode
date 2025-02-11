package skipnode;

import lookup.ConcurrentBackupTable;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Lock;

/**
 *
 */
public class NodeStashProcessor implements Runnable {

    private final LinkedBlockingDeque<SkipNodeIdentity> nodeStashRef;
    private final ConcurrentBackupTable backupTableRef;
    private final SkipNodeIdentity ownIdentity;
    private final Lock nodeStashLock;

    public boolean running = true;

    public NodeStashProcessor(LinkedBlockingDeque<SkipNodeIdentity> nodeStash, ConcurrentBackupTable backupTableRef,
                              SkipNodeIdentity ownIdentity, Lock nodeStashLock) {
        this.nodeStashRef = nodeStash;
        this.backupTableRef = backupTableRef;
        this.ownIdentity = ownIdentity;
        this.nodeStashLock = nodeStashLock;
    }

    @Override
    public void run() {
        while(running) {
            SkipNodeIdentity n = null;
            try {
                n = nodeStashRef.take();
            } catch (InterruptedException e) {
                System.err.println("[NodeStashProcessor.run] Could not take.");
                e.printStackTrace();
                continue;
            }
            if (n.equals(ownIdentity)) {
                continue;
            }
            int level = SkipNodeIdentity.commonBits(n.getNameID(), ownIdentity.getNameID());
            //n.getNumID() < ownIdentity.getNumID()
            if (n.getNumID().compareTo(ownIdentity.getNumID()) == -1
                    && !backupTableRef.getLefts(level).contains(n)) {
                for (int j = level; j >= 0; j--) {
                    backupTableRef.addLeftNode(n, j);
                }
            } //n.getNumID() >= ownIdentity.getNumID()
            else if ((n.getNumID().compareTo(ownIdentity.getNumID()) == 0 || n.getNumID().compareTo(ownIdentity.getNumID()) == 1)
                    && !backupTableRef.getRights(level).contains(n)) {
                for (int j = level; j >= 0; j--) {
                    backupTableRef.addRightNode(n, j);
                }
            }
        }
    }
}
