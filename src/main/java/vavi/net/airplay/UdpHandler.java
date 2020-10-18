/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;

import java.net.DatagramPacket;
import java.net.DatagramSocket;


/**
 * Interface for receiving packets
 *
 * @author bencall
 */
public interface UdpHandler {
    public void packetReceived(DatagramSocket socket, DatagramPacket packet);
}
