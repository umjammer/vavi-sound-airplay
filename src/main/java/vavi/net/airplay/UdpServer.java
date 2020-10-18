/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;


/**
 * Listen on a given socket
 *
 * @author bencall
 */
public class UdpServer extends Thread {
    // Constantes
    public static final int MAX_PACKET = 2048;
    // Variables d'instances
    private DatagramSocket socket;
    private UdpHandler handler;
    private boolean stopThread = false;

    public UdpServer(DatagramSocket socket, UdpHandler handler) {
        this.socket = socket;
        this.handler = handler;
        this.start();
    }

    public void run() {
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

    public synchronized void stopThread() {
        this.stopThread = true;
    }
}
