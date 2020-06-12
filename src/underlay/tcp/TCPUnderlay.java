package underlay.tcp;

import underlay.Underlay;
import underlay.packets.RequestParameters;
import underlay.packets.RequestType;
import underlay.packets.ResponseParameters;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TCP underlay implementation.
 */
public class TCPUnderlay extends Underlay {

    // The thread that continuously listens for incoming connection in the background.
    private Thread listenerThread;
    // The local TCP socket that can accept incoming TCP connections.
    private ServerSocket serverSocket;

    /**
     *
     * @param port the port that the underlay should be bound to.
     * @return true iff initialization is successful.
     */
    @Override
    protected boolean initUnderlay(int port) {
        try {
            // Create the TCP socket at the given port.
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.err.println("[TCPUnderlay] Could not initialize at the given port.");
            e.printStackTrace();
            return false;
        }
        // Create & start the listening thread which will continuously listen for incoming connections
        // and handle the requests as implemented in the `RequestHandler` class.
        listenerThread = new Thread(new TCPListener(serverSocket, this));
        listenerThread.start();
        return true;
    }

    /**
     *
     * @param address address of the remote server.
     * @param port port of the remote serve.r
     * @param t type of the request.
     * @param p parameters of the request.
     * @return the response emitted by the remote server.
     */
    @Override
    public ResponseParameters sendMessage(String address, int port, RequestType t, RequestParameters p) {
        Socket remote;
        ObjectOutputStream requestStream;
        ObjectInputStream responseStream;
        // Connect to the remote TCP server.
        try {
            remote = new Socket(address, port);
        } catch (IOException e) {
            System.err.println("[TCPUnderlay] Could not connect to the address: " + address + ":" + port);
            e.printStackTrace();
            return null;
        }
        // Send the request.
        try {
            requestStream = new ObjectOutputStream(remote.getOutputStream());
            TCPRequest request = new TCPRequest(t, p);
            requestStream.writeObject(request);
        } catch(IOException e) {
            System.err.println("[TCPUnderlay] Could not send the request.");
            e.printStackTrace();
            return null;
        }
        // Receive the response.
        ResponseParameters responseParameters;
        try {
            responseStream = new ObjectInputStream(remote.getInputStream());
            TCPResponse response = (TCPResponse) responseStream.readObject();
            responseParameters = response.parameters;
        } catch(IOException | ClassNotFoundException e) {
            System.err.println("[TCPUnderlay] Could not receive the response.");
            e.printStackTrace();
            return null;
        }
        // Close the connection & streams.
        try {
            requestStream.close();
            responseStream.close();
            remote.close();
        } catch (IOException e) {
            System.err.println("[TCPUnderlay] Could not close the outgoing connection.");
            e.printStackTrace();
        }
        return responseParameters;
    }

    /**
     * Terminates the underlay by unbinding the listener from the port.
     */
    @Override
    public void terminate() {
        try {
            // Unbind from the local port.
            serverSocket.close();
            // Terminate the listener thread.
            listenerThread.join();
        } catch (Exception e) {
            System.err.println("[TCPUnderlay] Could not terminate.");
            e.printStackTrace();
        }
    }
}
