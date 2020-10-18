/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.logging.Logger;

import vavi.net.airplay.UdpServer.UdpHandler;


/**
 * Listens for new packets.
 *
 * @author bencall
 */
public class RaopHandler implements UdpHandler {

    static Logger logger = Logger.getLogger(RaopHandler.class.getName());

    // Constants

    /** Total buffer size (number of frame) */
    public static final int BUFFER_FRAMES = 512;
    /** ALAC will wait till there are START_FILL frames in buffer */
    public static final int START_FILL = 282;
    /** Also in UDPListener (possible to merge it in one place?) */
    public static final int MAX_PACKET = 2048;

    /** Sockets */
    private DatagramSocket socket, controlSocket;
    private UdpServer server;
    /** client address */
    private InetAddress rtpClient;
    /** Audio infos and datas */
    private RaopPacket packet;
    private RaopBuffer audioBuffer;
    /** The audio player */
    private RaopSink sink;

    /**
     * Constructor.
     */
    public RaopHandler(RaopPacket packet, RaopSink.Buffer sinkBuffer) {
        // Init instance var
        this.packet = packet;

        // Init functions
        audioBuffer = new RaopBuffer(packet, this::resendRequest);
        this.initRtp();
        sink = new RaopSink(packet, audioBuffer, sinkBuffer);
    }

    public void stop() {
        sink.stop();
        server.stop();
        // l2.stopThread();
        synchronized (socket) {
            socket.close();
        }
        controlSocket.close();
    }

    public void setVolume(double vol) {
        sink.setVolume(vol);
    }

    /**
     * Return the server port for the bonjour service
     */
    public int getServerPort() {
        return socket.getLocalPort();
    }

    /**
     * Opens the sockets and begin listening
     */
    private void initRtp() {
        int port = 6000;
        while (true) {
            try {
                socket = new DatagramSocket(port);
                controlSocket = new DatagramSocket(port + 1);
            } catch (IOException e) {
                port = port + 2;
                continue;
            }
            break;
        }

        server = new UdpServer(socket, this);
    }

    /**
     * When udpListener gets a packet
     */
    public void packetReceived(DatagramSocket socket, DatagramPacket packet) {
        this.rtpClient = packet.getAddress(); // The client address

        int type = packet.getData()[1] & ~0x80;
        if (type == 0x60 || type == 0x56) { // audio data / resend
            // Shift of 4 additional bytes
            int off = 0;
            if (type == 0x56) {
                off = 4;
            }

            // seqno is on two byte
            int seqno = (packet.getData()[2 + off] & 0xff) * 256 + (packet.getData()[3 + off] & 0xff);

            // + les 12 (cfr. RFC RTP: champs a ignorer)
            byte[] pktp = new byte[packet.getLength() - off - 12];
            for (int i = 0; i < pktp.length; i++) {
                pktp[i] = packet.getData()[i + 12 + off];
            }

            audioBuffer.putPacketInBuffer(seqno, pktp);
        }
    }

    /**
     * Ask iTunes to resend packet FUNCTIONAL??? NO PROOFS
     *
     * @param first
     * @param last
     */
    private void resendRequest(int first, int last) {
logger.info("Resend Request: " + first + "::" + last);
        if (last < first) {
            return;
        }

        int len = last - first + 1;
        byte[] request = {
            (byte) 0x80, (byte) (0x55 | 0x80), 0x01, 0x00, (byte) ((first & 0xFF00) >> 8), (byte) (first & 0xFF),
            (byte) ((len & 0xFF00) >> 8), (byte) (len & 0xFF)
        };

        try {
            DatagramPacket temp = new DatagramPacket(request, request.length, rtpClient, packet.getControlPort());
            controlSocket.send(temp);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Flush the audioBuffer
     */
    public void flush() {
        audioBuffer.flush();
    }
}
