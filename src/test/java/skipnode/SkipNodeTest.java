package skipnode;

import lookup.LookupTable;
import middlelayer.MiddleLayer;
import misc.LocalSkipGraph;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import underlay.Underlay;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contains the skip-node tests.
 */
class SkipNodeTest {

    static int STARTING_PORT = 8080;
    static int NODES = 16;

     /*In this test I call the increment a lot of times through different threads
     This tests whether all messages are in face received or not*/
/*     @Test
    void concurrentIncrements() {
        // First, construct the underlays.
        List<Underlay> underlays = new ArrayList<>(NODES);
        for(int i = 0; i < NODES; i++) {
            Underlay underlay = Underlay.newDefaultUnderlay();
            underlay.initialize(STARTING_PORT + i);
            underlays.add(underlay);
        }
        // Then, construct the local skip graph without manually constructing the lookup tables.
        LocalSkipGraph g = new LocalSkipGraph(NODES, underlays.get(0).getAddress(), STARTING_PORT, false);
        // Create the middle layers.
        for(int i = 0; i < NODES; i++) {
            MiddleLayer middleLayer = new MiddleLayer(underlays.get(i), g.getNodes().get(i));
            // Assign the middle layer to the underlay & overlay.
            underlays.get(i).setMiddleLayer(middleLayer);
            g.getNodes().get(i).setMiddleLayer(middleLayer);
        }
//        // We expect the lookup tables to converge to a correct state after SEARCH_THRESHOLD many searches.
//        for(int k = 0; k < SEARCH_THRESHOLD; k++) {
//            final SkipNode initiator = g.getNodes().get((int)(Math.random() * NODES));
//            final SkipNode target = g.getNodes().get((int)(Math.random() * NODES));
//            initiator.searchByNameID(target.getNameID());
//        }
        // Construct the search threads.
        int SEARCH_THREADS = 1;
        Thread[] searchThreads = new Thread[SEARCH_THREADS];
        final SkipNode target = g.getNodes().get((int)(Math.random() * NODES));
        for(int i = 0; i < searchThreads.length; i++) {
            // Choose two random nodes.
            final SkipNode initiator = g.getNodes().get((int)(Math.random() * NODES));
            searchThreads[i] = new Thread(() -> {
//                SearchResult res = initiator.searchByNameID(target.getNameID());
                initiator.increment(target.getIdentity(), 0);
                initiator.increment(target.getIdentity(), 0);
//                Assertions.assertEquals(target.getNameID(), res.result.getNameID());
            });
        }
        // Start the search threads.
        for(Thread t : searchThreads) t.start();
        // Complete the threads.
        try {
            for(Thread t : searchThreads) t.join();
        } catch(InterruptedException e) {
            System.err.println("Could not join the thread.");
            e.printStackTrace();
        }
        int sum = 0;
        // One should be 2 * NUMTHREADS, rest should be 0
        for (SkipNode node : g.getNodes()){
            System.out.println(node.i);
            sum+=node.i.get();
        }
        // This should be 2 * NUMTHREADS
        System.out.println(sum);
    }*/


    @Test
    void concurrentInsertionsAndSearches() {
        // First, construct the underlays.
        List<Underlay> underlays = new ArrayList<>(NODES);
        for(int i = 0; i < NODES; i++) {
            Underlay underlay = Underlay.newDefaultUnderlay();
            underlay.initialize(STARTING_PORT + i + NODES);
            underlays.add(underlay);
        }
        // Then, construct the local skip graph without manually constructing the lookup tables.
        LocalSkipGraph g = new LocalSkipGraph(NODES, underlays.get(0).getAddress(), STARTING_PORT + NODES, false);
        // Create the middle layers.
        for(int i = 0; i < NODES; i++) {
            MiddleLayer middleLayer = new MiddleLayer(underlays.get(i), g.getNodes().get(i));
            // Assign the middle layer to the underlay & overlay.
            underlays.get(i).setMiddleLayer(middleLayer);
            g.getNodes().get(i).setMiddleLayer(middleLayer);
        }
        // Insert the first node.
        g.getNodes().get(0).insert(null, -1);
        // Construct the threads.
        Thread[] insertionThreads = new Thread[NODES-1];
        for(int i = 1; i <= insertionThreads.length; i++) {
            // Choose the previous node as the introducer.
            final SkipNode introducer = g.getNodes().get(i-1);
            final SkipNode node = g.getNodes().get(i);
            insertionThreads[i-1] = new Thread(() -> {
                node.insert(introducer.getIdentity(null).getAddress(), introducer.getIdentity(null).getPort());
            });
        }

        // Start the insertion threads.
        for(Thread t : insertionThreads) t.start();
        // Wait for them to complete.
        for(Thread t : insertionThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // First, check the correctness and consistency of the lookup tables.
        // Create a map of num ids to their corresponding lookup tables.
        Map<BigInteger, LookupTable> tableMap = g.getNodes().stream()
                .collect(Collectors.toMap(SkipNode::getNumID, SkipNode::getLookupTable));
        // Check the correctness & consistency of the tables.
        for(SkipNode n : g.getNodes()) {
            tableCorrectnessCheck(n.getNumID(), n.getNameID(), n.getLookupTable());
            tableConsistencyCheck(tableMap, n);
        }
        System.out.println("INSERTIONS COMPLETE.");
        StringBuilder sb = new StringBuilder();
        for(SkipNode nd : g.getNodes()){
            sb.append(nd.getIdentity(null) + " 's Backup Table\n");
//            System.out.println(nd.getIdentity() + " 's Backup Table");
//            System.out.println(nd.getLookupTable());
            sb.append(nd.getLookupTable()+"\n");
        }
        final String excp = sb.toString();
        sb = new StringBuilder();
        for(SkipNode nd : g.getNodes()){
            sb.append(nd.getIdentity(null) + " 's Backup Table AFTER insertion\n");
//            System.out.println(nd.getIdentity() + " 's Backup Table");
//            System.out.println(nd.getLookupTable());
            sb.append(nd.getLookupTable()+"\n");
        }
        final String fnl = sb.toString();
        // Construct the search threads we will perform searches from each node to every node.
        Thread[] searchThreads = new Thread[NODES * NODES];
        for(int i = 0; i < NODES; i++) {
            // Choose the initiator.
            final SkipNode initiator = g.getNodes().get(i);
            for(int j = 0; j < NODES; j++) {
                // Choose the target.
                final SkipNode target = g.getNodes().get(j);
                searchThreads[i + NODES * j] = new Thread(() -> {
                    // Wait for at most 5 seconds to avoid congestion.
                    try {
                        Thread.sleep((int)(Math.random() * 5000));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    SearchResult res = initiator.searchByNameID(target.getNameID());
                    Assertions.assertEquals(target.getNameID(), res.result.getNameID(), "Source: " + initiator.getNumID() + " Target: " + target.getNameID() + " " + excp + "\n" + fnl);
                });
            }
        }
        // Start the search threads.
        for(Thread t : searchThreads) {
            t.start();
        }
        // Complete the threads.
        try {
            for(Thread t : searchThreads) t.join();
        } catch(InterruptedException e) {
            System.err.println("Could not join the thread.");
            e.printStackTrace();
        }
    }

    @Test
    void concurrentInsertions() {
        // First, construct the underlays.
        List<Underlay> underlays = new ArrayList<>(NODES);
        for(int i = 0; i < NODES; i++) {
            Underlay underlay = Underlay.newDefaultUnderlay();
            underlay.initialize(STARTING_PORT + i + 2 * NODES);
            underlays.add(underlay);
        }
        // Then, construct the local skip graph without manually constructing the lookup tables.
        LocalSkipGraph g = new LocalSkipGraph(NODES, underlays.get(0).getAddress(), STARTING_PORT+ NODES*2, false);
        // Create the middle layers.
        for(int i = 0; i < NODES; i++) {
            MiddleLayer middleLayer = new MiddleLayer(underlays.get(i), g.getNodes().get(i));
            // Assign the middle layer to the underlay & overlay.
            underlays.get(i).setMiddleLayer(middleLayer);
            g.getNodes().get(i).setMiddleLayer(middleLayer);
        }
        // Insert the first node.
        g.getNodes().get(0).insert(null, -1);
        Thread[] threads = new Thread[NODES-1];
        // Construct the threads.
        for(int i = 1; i <= threads.length; i++) {
            // Choose an already inserted introducer.
            final SkipNode introducer = g.getNodes().get((int)(Math.random() * i));
            final SkipNode node = g.getNodes().get(i);
            threads[i-1] = new Thread(() -> {
                node.insert(introducer.getIdentity(null).getAddress(), introducer.getIdentity(null).getPort());
            });
        }
        // Initiate the insertions.
        for(Thread t : threads) t.start();
        // Wait for the insertions to complete.
        for(Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.err.println("Could not join the thread.");
                e.printStackTrace();
            }
        }
        // Create a map of num ids to their corresponding lookup tables.
        Map<BigInteger, LookupTable> tableMap = g.getNodes().stream()
                .collect(Collectors.toMap(SkipNode::getNumID, SkipNode::getLookupTable));
        // Check the correctness & consistency of the tables.
        for(SkipNode n : g.getNodes()) {
            tableCorrectnessCheck(n.getNumID(), n.getNameID(), n.getLookupTable());
            tableConsistencyCheck(tableMap, n);
        }
    }

    @Test
    void insert() {
        // First, construct the underlays.
        List<Underlay> underlays = new ArrayList<>(NODES);
        for(int i = 0; i < NODES; i++) {
            Underlay underlay = Underlay.newDefaultUnderlay();
            underlay.initialize(STARTING_PORT + i + 3 * NODES);
            underlays.add(underlay);
        }
        // Then, construct the local skip graph without manually constructing the lookup tables.
        LocalSkipGraph g = new LocalSkipGraph(NODES, underlays.get(0).getAddress(), STARTING_PORT+ NODES*3, false);
        // Create the middle layers.
        for(int i = 0; i < NODES; i++) {
            MiddleLayer middleLayer = new MiddleLayer(underlays.get(i), g.getNodes().get(i));
            // Assign the middle layer to the underlay & overlay.
            underlays.get(i).setMiddleLayer(middleLayer);
            g.getNodes().get(i).setMiddleLayer(middleLayer);
        }
        // Now, insert every node in a randomized order.
        g.insertAllRandomized();
        // Create a map of num ids to their corresponding lookup tables.
        Map<BigInteger, LookupTable> tableMap = g.getNodes().stream()
                .collect(Collectors.toMap(SkipNode::getNumID, SkipNode::getLookupTable));
        // Check the correctness of the tables.
        for(SkipNode n : g.getNodes()) {
            tableCorrectnessCheck(n.getNumID(), n.getNameID(), n.getLookupTable());
            tableConsistencyCheck(tableMap, n);
        }
        underlays.forEach(Underlay::terminate);
    }

    @Test
    void searchByNameID() {
        // First, construct the underlays.
        List<Underlay> underlays = new ArrayList<>(NODES);
        for(int i = 0; i < NODES; i++) {
            Underlay underlay = Underlay.newDefaultUnderlay();
            underlay.initialize(STARTING_PORT + i + 4 * NODES);
            underlays.add(underlay);
        }
        // Then, construct the local skip graph.
        LocalSkipGraph g = new LocalSkipGraph(NODES, underlays.get(0).getAddress(), STARTING_PORT+ NODES*4, true);
        // Create the middle layers.
        for(int i = 0; i < NODES; i++) {
            MiddleLayer middleLayer = new MiddleLayer(underlays.get(i), g.getNodes().get(i));
            // Assign the middle layer to the underlay & overlay.
            underlays.get(i).setMiddleLayer(middleLayer);
            g.getNodes().get(i).setMiddleLayer(middleLayer);
        }
        // We will now perform name ID searches for every node from each node in the skip graph.
        for(int i = 0; i < NODES; i++) {
            SkipNode initiator = g.getNodes().get(i);
            for(int j = 0; j < NODES; j++) {
                SkipNode target = g.getNodes().get(j);
                SearchResult result = initiator.searchByNameID(target.getNameID());
                if(!result.result.equals(target.getIdentity(null))) {
                    initiator.searchByNameID(target.getNameID());
                }
                Assertions.assertEquals(target.getIdentity(null), result.result);
            }
        }
        underlays.forEach(Underlay::terminate);
    }

    @Test
    void searchByNumID() {
        // First, construct the underlays.
        List<Underlay> underlays = new ArrayList<>(NODES);
        for(int i = 0; i < NODES; i++) {
            Underlay underlay = Underlay.newDefaultUnderlay();
            underlay.initialize(STARTING_PORT - NODES + i);
            underlays.add(underlay);
        }
        // Then, construct the local skip graph.
        LocalSkipGraph g = new LocalSkipGraph(NODES, underlays.get(0).getAddress(), STARTING_PORT- NODES, true);
        // Create the middle layers.
        for(int i = 0; i < NODES; i++) {
            MiddleLayer middleLayer = new MiddleLayer(underlays.get(i), g.getNodes().get(i));
            // Assign the middle layer to the underlay & overlay.
            underlays.get(i).setMiddleLayer(middleLayer);
            g.getNodes().get(i).setMiddleLayer(middleLayer);
        }

        // We will now perform name ID searches for every node from each node in the skip graph.
        for(int i = 0; i < NODES; i++) {
            SkipNode initiator = g.getNodes().get(i);
            for(int j = 0; j < NODES; j++) {
                SkipNode target = g.getNodes().get(j);
                SkipNodeIdentity result = initiator.searchByNumID(target.getNumID());
                Assertions.assertEquals(target.getIdentity(null), result);
            }
        }
        underlays.forEach(Underlay::terminate);
    }

    // Checks the correctness of a lookup table owned by the node with the given identity parameters.
    static void tableCorrectnessCheck(BigInteger numID, String nameID, LookupTable table) {
        for(int i = 0; i < table.getNumLevels(); i++) {
            List<SkipNodeIdentity> lefts = table.getLefts(i);
            List<SkipNodeIdentity> rights = table.getRights(i);
            for(SkipNodeIdentity l : lefts) {
                //l.getNumID() < numID
                Assertions.assertTrue(l.getNumID().compareTo(numID) == -1);
                Assertions.assertTrue(SkipNodeIdentity.commonBits(l.getNameID(), nameID) >= i);
            }
            for(SkipNodeIdentity r : rights) {
                //r.getNumID() > numID
                Assertions.assertTrue(r.getNumID().compareTo(numID) == 1);
                Assertions.assertTrue(SkipNodeIdentity.commonBits(r.getNameID(), nameID) >= i);
            }
        }
    }

    // Checks the consistency of a lookup table. In other words, we assert that if x is a neighbor of y at level l,
    // then y is a neighbor of x at level l (in opposite directions).
    static void tableConsistencyCheck(Map<BigInteger, LookupTable> tableMap, SkipNode node) {
        LookupTable table = node.getLookupTable();
        for(int i = 0; i < table.getNumLevels(); i++) {
            List<SkipNodeIdentity> lefts = table.getLefts(i);
            List<SkipNodeIdentity> rights = table.getRights(i);
            // Check whether the neighbors agree on the neighborship relationships.
            for(SkipNodeIdentity l : lefts) {
                LookupTable neighborMap = tableMap.get(l.getNumID());
                Assertions.assertTrue(neighborMap.isRightNeighbor(node.getIdentity(null), i));
            }
            for(SkipNodeIdentity r : rights) {
                LookupTable neighborMap = tableMap.get(r.getNumID());
                Assertions.assertTrue(neighborMap.isLeftNeighbor(node.getIdentity(null), i));
            }
        }
    }
}