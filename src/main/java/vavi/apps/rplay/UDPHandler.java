
package vavi.apps.rplay;

import java.net.DatagramPacket;
import java.net.DatagramSocket;


/**
 * Interface for receiving packets
 *
 * @author bencall
 */
public interface UDPHandler {
    public void packetReceived(DatagramSocket socket, DatagramPacket packet);
}
