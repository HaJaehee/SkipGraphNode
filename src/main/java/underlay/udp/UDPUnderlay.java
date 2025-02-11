package underlay.udp;

import underlay.Underlay;
import underlay.packets.Request;
import underlay.packets.Response;

import java.io.IOException;
import java.net.*;

/**
 * UDP Underlay implementation.
 */
public class UDPUnderlay extends Underlay {

    /**
     * The nature of UDP requires us to predefine the maximum size of a packet that could be transferred. This parameter
     * defines the maximum size of a packet in bytes.
     */
    public static final int MAX_PACKET_SIZE = 10000;

    // The thread that continuously listens for incoming connection in the background. As opposed to TCP, both requests
    // and responses will be received by this thread.
    private Thread listenerThread;
    // The local UDP socket that can accept incoming UDP connections.
    private DatagramSocket udpSocket;
    // This object will be used to transfer the responses from the listener thread to the thread that the `sendMessage`
    // was called from.
    private final UDPResponseLock responseLock = new UDPResponseLock();

    /**
     * Creates a UDP socket at the given port and starts listening it.
     * @param port the port that the underlay should be bound to.
     * @return whether the initialization was successful.
     */
    @Override
    protected boolean initUnderlay(int port) {
        // Bind to the given port.
        try {
            udpSocket = new DatagramSocket(port);
        } catch (SocketException e) {
            System.err.println("[UDPUnderlay] Could not initialize at the given port.");
            e.printStackTrace();
            return false;
        }
        // Create the listener thread that will continuously listen to the UDP packets.
        listenerThread = new Thread(new UDPListener(udpSocket, this, responseLock));
        listenerThread.start();
        return true;
    }

    /**
     * Sends an UDP request the given address. The size of the request in bytes cannot exceed the size
     * defined in `UDPUtils.MAX_PACKET_SIZE`.
     * @param address address of the remote server.
     * @param port port of the remote server.
     * @param request request to send.
     * @return the response emitted by the server.
     */
    @Override
    public Response sendMessage(String address, int port, Request request) {
        // Convert a string address to an actual address to be used for UDP.
        InetAddress destAddress;
        try {
            destAddress = Inet4Address.getByName(address);
        } catch (UnknownHostException e) {
            System.err.println("[UDPUnderlay] Could not find the host with the address " + address);
            e.printStackTrace();
            return null;
        }
        // Serialize the request.
        byte[] requestBytes = UDPUtils.serialize(request);
        if(requestBytes == null) {
            System.err.println("[UDPUnderlay] Invalid request.");
            return null;
        }
        // Then, send the request.
        DatagramPacket requestPacket = new DatagramPacket(requestBytes, requestBytes.length, destAddress, port);
        try {
            udpSocket.send(requestPacket);
        } catch (IOException e) {
            System.err.println("[UDPUnderlay] Could not send the request.");
            e.printStackTrace();
            return null;
        }
        // Now, wait for the response.
        Response response = responseLock.waitForResponse();
        if(response == null) {
            System.err.println("[UDPUnderlay] Could not get the response.");
            return null;
        }
        return response;
    }

    /**
     * Terminates the underlay by unbinding the listener from the port.
     * @return whether the termination was successful.
     */
    @Override
    public boolean terminate() {
        try {
            // Unbind from the local port.
            udpSocket.close();
            // Close the listener thread.
            listenerThread.join();
        } catch (InterruptedException e) {
            System.err.println("[UDPUnderlay] Could not terminate.");
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
