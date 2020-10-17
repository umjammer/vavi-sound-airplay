
package vavi.net.airplay;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import vavi.net.airplay.RTSPServer.RTSPListener;
import vavi.util.ByteUtil;


/**
 * An primitive RTSP responder for replying iTunes
 *
 * @author bencall
 */
public class RTSPHandler extends Thread {

    private static Logger logger = Logger.getLogger(RTSPHandler.class.getName());

    private List<RTSPListener> listeners = new ArrayList<>();

    protected void fireUpdate(RTSPRequest request) {
        listeners.forEach(l -> l.requestHappend(request));
    }

    public void addRTSPListener(RTSPListener listener) {
        listeners.add(listener);
    }

    private RAOPSink.Sink sink;

    public void setRAOPSink(RAOPSink.Sink sink) {
        this.sink = sink;
//Debug.println("rtsp sink set: " + sink + "@" + this.hashCode());
    }

    // Connected socket
    private Socket socket;
    private int[] fmtp;
    // ANNOUNCE request infos
    private byte[] aesiv, aeskey;
    // Audio listener
    private RAOPHandler raopHandler;
    byte[] hwAddr;
    private BufferedReader in;
    private String password;
    private RTSPResponse response;
    // Pre-define patterns
    private static final Pattern authPattern = Pattern
            .compile("Digest username=\"(.*)\", realm=\"(.*)\", nonce=\"(.*)\", uri=\"(.*)\", response=\"(.*)\"");
    private static final Pattern completedPacket = Pattern.compile("(.*)\r\n\r\n");
    private AirPlayCrypto crypto;

    public RTSPHandler(byte[] hwAddr, Socket socket) throws IOException {
        this(hwAddr, socket, null);
    }

    public RTSPHandler(byte[] hwAddr, Socket socket, String pass) throws IOException {
        this.hwAddr = hwAddr;
        this.socket = socket;
        this.password = pass;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        try {
            crypto = new AirPlayCrypto();
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new IllegalStateException(e);
        }
    }

    public RTSPResponse handlePacket(RTSPRequest packet) {

        if (password == null) {
            // No pass = ok!
            response = new RTSPResponse("RTSP/1.0 200 OK");
            response.append("Audio-Jack-Status", "connected; type=analog");
            response.append("CSeq", packet.valueOfHeader("CSeq"));
        } else {
            // Default response (deny, deny, deny!)
            response = new RTSPResponse("RTSP/1.0 401 UNAUTHORIZED");
            response.append("WWW-Authenticate", "Digest realm=\"*\" nonce=\"*\"");
            response.append("Method", "DENIED");

            String authRaw = packet.valueOfHeader("Authorization");

            // If supplied, check response
            if (authRaw != null) {
                Matcher auth = authPattern.matcher(authRaw);

                if (auth.find()) {
                    String username = auth.group(1);
                    String realm = auth.group(2);
                    String nonce = auth.group(3);
                    String uri = auth.group(4);
                    String resp = auth.group(5);
                    String method = packet.getReq();

                    String hash1 = md5Hash(username + ":" + realm + ":" + password).toUpperCase();
                    String hash2 = md5Hash(method + ":" + uri).toUpperCase();
                    String hash = md5Hash(hash1 + ":" + nonce + ":" + hash2).toUpperCase();

                    // Check against password
                    if (hash.equals(resp)) {
                        // Success!
                        response = new RTSPResponse("RTSP/1.0 200 OK");
                        response.append("Audio-Jack-Status", "connected; type=analog");
                        response.append("CSeq", packet.valueOfHeader("CSeq"));
                    }
                }
            }
        }

        // Apple Challenge-Response field if needed
        String challenge = packet.valueOfHeader("Apple-Challenge");
        if (challenge != null) {

            // IP byte array
            // byte[] ip = socket.getLocalAddress().getAddress();
            SocketAddress localAddress = socket.getLocalSocketAddress(); // .getRemoteSocketAddress();

            byte[] ip = ((InetSocketAddress) localAddress).getAddress().getAddress();

logger.info("challenge: " + ByteUtil.toHexString(ip) + ", " + ByteUtil.toHexString(hwAddr));
            // Write
            response.append("Apple-Response", getChallengeResponce(challenge, ip, hwAddr));
//        } else {
//logger.info("challenge is null");
        }

        // Paquet request
        String REQ = packet.getReq();
        if (REQ.contentEquals("OPTIONS")) {
            // The response field
            response.append("Public", "ANNOUNCE, SETUP, RECORD, PAUSE, FLUSH, TEARDOWN, OPTIONS, GET_PARAMETER, SET_PARAMETER");

        } else if (REQ.contentEquals("ANNOUNCE")) {
            // Nothing to do here. Juste get the keys and values
            Pattern p = Pattern.compile("^a=([^:]+):(.+)", Pattern.MULTILINE);
            Matcher m = p.matcher(packet.getContent());
            while (m.find()) {
                if (m.group(1).contentEquals("fmtp")) {
                    // Parse FMTP as array
                    String[] temp = m.group(2).split(" ");
                    fmtp = new int[temp.length];
                    for (int i = 0; i < temp.length; i++) {
                        fmtp[i] = Integer.valueOf(temp[i]);
                    }

                } else if (m.group(1).contentEquals("rsaaeskey")) {
                    aeskey = this.decryptRSA(Base64.getDecoder().decode(m.group(2)));
                } else if (m.group(1).contentEquals("aesiv")) {
                    aesiv = Base64.getDecoder().decode(m.group(2));
                }
            }

        } else if (REQ.contentEquals("SETUP")) {
            int controlPort = 0;
            int timingPort = 0;

            String value = packet.valueOfHeader("Transport");

            // Control port
            Pattern p = Pattern.compile(";control_port=(\\d+)");
            Matcher m = p.matcher(value);
            if (m.find()) {
                controlPort = Integer.valueOf(m.group(1));
            }

            // Timing port
            p = Pattern.compile(";timing_port=(\\d+)");
            m = p.matcher(value);
            if (m.find()) {
                timingPort = Integer.valueOf(m.group(1));
            }

            // Launching audioserver
//Debug.println("sink: " + sink + "@" + this.hashCode());
            raopHandler = new RAOPHandler(new RAOPPacket(aesiv, aeskey, fmtp, controlPort, timingPort), sink);

            response.append("Transport", packet.valueOfHeader("Transport") + ";server_port=" + raopHandler.getServerPort());

            // ??? Why ???
            response.append("Session", "DEADBEEF");
        } else if (REQ.contentEquals("RECORD")) {
            // Headers
            // Range: ntp=0-
            // RTP-Info: seq={Note 1};rtptime={Note 2}
            // Note 1: Initial value for the RTP Sequence Number, random 16 bit value
            // Note 2: Initial value for the RTP Timestamps, random 32 bit value

        } else if (REQ.contentEquals("FLUSH")) {
            raopHandler.flush();

        } else if (REQ.contentEquals("TEARDOWN")) {
            response.append("Connection", "close");

        } else if (REQ.contentEquals("SET_PARAMETER")) {
            // Timing port
            Pattern p = Pattern.compile("volume: (.+)");
            Matcher m = p.matcher(packet.getContent());
            if (m.find()) {
                double volume = Math.pow(10.0, 0.05 * Double.parseDouble(m.group(1)));
                raopHandler.setVolume(65536.0 * volume);
            }

        } else {
            logger.warning("REQUEST(" + REQ + "): Not Supported Yet!");
            logger.warning(packet.getRawPacket());
        }

        fireUpdate(packet);

        // We close the response
        response.finalize();
        return response;
    }

    /**
     * Generates md5 hash of a string.
     *
     * @param plaintext string
     * @return hash string
     */
    private static String md5Hash(String plaintext) {
        String hashtext = "";

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plaintext.getBytes());
            byte[] digest = md.digest();

            BigInteger bigInt = new BigInteger(1, digest);
            hashtext = bigInt.toString(16);

            // Now we need to zero pad it if you actually want the full 32
            // chars.
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return hashtext;
    }

    /** */
    private String getChallengeResponce(String challenge, byte[] ip, byte[] hwAddr) {
        try {
            return crypto.getChallengeResponce(challenge, ip, hwAddr);
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
e.printStackTrace();
            return null;
        }
    }

    /**
     * Decrypts with RSA priv key.
     *
     * @param array
     * @return
     */
    private byte[] decryptRSA(byte[] array) {
        try {
            // Encrypt
            return crypto.decrypt(array);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Thread to listen packets
     */
    public void run() {
        try {
            do {
logger.info("listening packets ... ");
                // feed buffer until packet completed
                StringBuffer packet = new StringBuffer();
                int ret = 0;
                do {
                    char[] buffer = new char[4096];
                    ret = in.read(buffer);
                    packet.append(new String(buffer));
                } while (ret != -1 && !completedPacket.matcher(packet.toString()).find());

                if (ret != -1) {
                    // We handle the packet
                    RTSPRequest request = new RTSPRequest(packet.toString());
                    RTSPResponse response = this.handlePacket(request);
System.out.println(request.toString());
System.out.println(response.toString());

                    // Write the response to the wire
                    try {
                        BufferedWriter oStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        oStream.write(response.getRawPacket());
                        oStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if ("TEARDOWN".equals(request.getReq())) {
                        socket.close();
                        socket = null;
                    }
                } else {
                    socket.close();
                    socket = null;
                }
            } while (socket != null);

        } catch (IOException e) {
            e.printStackTrace();

        } finally {
            try {
                if (in != null)
                    in.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (socket != null)
                        socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
logger.info("connection ended.");
    }
}
