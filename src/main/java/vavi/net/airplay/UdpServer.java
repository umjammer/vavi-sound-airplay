/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Listen on a given socket
 *
 * @author bencall
 */
public class UdpServer {

    public interface UdpHandler {
        void packetReceived(DatagramSocket socket, DatagramPacket packet);
    }

    // Constantes
    public static final int MAX_PACKET = 2048;
    // Variables d'instances
    private DatagramSocket socket;
    private UdpHandler handler;
    private boolean stopThread = false;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public UdpServer(DatagramSocket socket, UdpHandler handler) {
        this.socket = socket;
        this.handler = handler;
        this.executor.submit(this::run);
    }

    private void run() {
        boolean fin = stopThread;
        while (!fin) {
            byte[] buffer = new byte[MAX_PACKET];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                synchronized (socket) {
                    if (socket != null) {
                        socket.receive(packet);
                        handler.packetReceived(socket, packet);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Stop
            synchronized (this) {
                fin = this.stopThread;
            }
        }
    }

    public void stop() {
        this.stopThread = true;
        this.executor.shutdown();
    }
}
