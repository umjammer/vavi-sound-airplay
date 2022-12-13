/*
 * https://github.com/bencall/RPlay
 */

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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import vavi.net.airplay.RtspServer.RtspListener;
import vavi.net.airplay.RtspServer.RtspListener.RtspEvent;
import vavi.net.airplay.RtspServer.RtspListener.EventType;
import vavi.net.airplay.RtspServer.RtspListener.RequestType;
import vavi.util.ByteUtil;


/**
 * An primitive RTSP responder for replying iTunes
 *
 * @author bencall
 */
public class RtspHandler implements Runnable {

    private static Logger logger = Logger.getLogger(RtspHandler.class.getName());

    private List<RtspListener> listeners = new ArrayList<>();

    protected void fireUpdate(RtspEvent request) {
        listeners.forEach(l -> l.update(request));
    }

    public void addRtspListener(RtspListener listener) {
        listeners.add(listener);
    }

    private RaopSink.Buffer sink;

    public void setRaopSink(RaopSink.Buffer sink) {
        this.sink = sink;
//Debug.println("rtsp sink set: " + sink + "@" + this.hashCode());
    }

    // Connected socket
    private Socket socket;

    private int[] fmtp;
    // ANNOUNCE request infos
    private byte[] aesIv, aesKey;

    // Audio listener
    private RaopHandler raopHandler;
    private byte[] hwAddr;
    private BufferedReader in;
    private transient String password;
    private RtspResponse response;

    // Pre-define patterns
    private static final Pattern authPattern = Pattern
            .compile("Digest username=\"(.*)\", realm=\"(.*)\", nonce=\"(.*)\", uri=\"(.*)\", response=\"(.*)\"");
    private static final Pattern completedPacket = Pattern.compile("(.*)\r\n\r\n");
    private static final Pattern announcePattern = Pattern.compile("^a=([^:]+):(.+)", Pattern.MULTILINE);
    private static final Pattern controlPattern = Pattern.compile(";control_port=(\\d+)");
    private static final Pattern timingPattern = Pattern.compile(";timing_port=(\\d+)");
    private static final Pattern volumePattern = Pattern.compile("volume: (.+)");

    private Crypto crypto;

    public RtspHandler(byte[] hwAddr, Socket socket) throws IOException {
        this(hwAddr, socket, null);
    }

    public RtspHandler(byte[] hwAddr, Socket socket, String password) throws IOException {
        this.hwAddr = hwAddr;
        this.socket = socket;
        this.password = password;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        try {
            crypto = new Crypto();
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new IllegalStateException(e);
        }
    }

    public RtspResponse handleRequest(RtspRequest request) {

        if (password == null) {
            // No pass = ok!
            response = new RtspResponse("RTSP/1.0 200 OK");
            response.addHeader("Audio-Jack-Status", "connected; type=analog");
            response.addHeader("CSeq", request.getHeader("CSeq"));
        } else {
            // Default response (deny, deny, deny!)
            response = new RtspResponse("RTSP/1.0 401 UNAUTHORIZED");
            response.addHeader("WWW-Authenticate", "Digest realm=\"*\" nonce=\"*\"");
            response.addHeader("Method", "DENIED");

            String authRaw = request.getHeader("Authorization");

            // If supplied, check response
            if (authRaw != null) {
                Matcher auth = authPattern.matcher(authRaw);

                if (auth.find()) {
                    String username = auth.group(1);
                    String realm = auth.group(2);
                    String nonce = auth.group(3);
                    String uri = auth.group(4);
                    String resp = auth.group(5);
                    String method = request.getMethod();

                    String hash1 = md5Hash(username + ":" + realm + ":" + password).toUpperCase();
                    String hash2 = md5Hash(method + ":" + uri).toUpperCase();
                    String hash = md5Hash(hash1 + ":" + nonce + ":" + hash2).toUpperCase();

                    // Check against password
                    if (hash.equals(resp)) {
                        // Success!
                        response = new RtspResponse("RTSP/1.0 200 OK");
                        response.addHeader("Audio-Jack-Status", "connected; type=analog");
                        response.addHeader("CSeq", request.getHeader("CSeq"));
                    }
                }
            }
        }

        // Apple Challenge-Response field if needed
        String challenge = request.getHeader("Apple-Challenge");
        if (challenge != null) {

            // IP byte array
            // byte[] ip = socket.getLocalAddress().getAddress();
            SocketAddress localAddress = socket.getLocalSocketAddress(); // .getRemoteSocketAddress();

            byte[] ip = ((InetSocketAddress) localAddress).getAddress().getAddress();

logger.fine("challenge: " + ByteUtil.toHexString(ip) + ", " + ByteUtil.toHexString(hwAddr));
            // Write
            response.addHeader("Apple-Response", getChallengeResponce(challenge, ip, hwAddr));
//        } else {
//logger.info("challenge is null");
        }

try {
        // packet request
        RequestType method = RequestType.valueOf(request.getMethod());
        switch (method) {
        case OPTIONS:
            // The response field
            response.addHeader("Public", RequestType.list());
            break;
        case ANNOUNCE:
            // Nothing to do here. Juste get the keys and values
            Matcher m = announcePattern.matcher(request.getContent());
            while (m.find()) {
                if (m.group(1).contentEquals("fmtp")) {
                    // Parse FMTP as array
                    String[] temp = m.group(2).split(" ");
                    fmtp = new int[temp.length];
                    for (int i = 0; i < temp.length; i++) {
                        fmtp[i] = Integer.parseInt(temp[i]);
                    }

                } else if (m.group(1).contentEquals("rsaaeskey")) {
                    aesKey = this.decryptRSA(Base64.getDecoder().decode(m.group(2)));
                } else if (m.group(1).contentEquals("aesiv")) {
                    aesIv = Base64.getDecoder().decode(m.group(2));
                }
            }
            break;
        case SETUP:
            int controlPort = 0;
            int timingPort = 0;

            String value = request.getHeader("Transport");

            // Control port
            m = controlPattern.matcher(value);
            if (m.find()) {
                controlPort = Integer.parseInt(m.group(1));
            }

            // Timing port
            m = timingPattern.matcher(value);
            if (m.find()) {
                timingPort = Integer.parseInt(m.group(1));
            }

            // Launching audioserver
//Debug.println("sink: " + sink + "@" + this.hashCode());
            raopHandler = new RaopHandler(new RaopPacket(aesIv, aesKey, fmtp, controlPort, timingPort), sink);

            response.addHeader("Transport", request.getHeader("Transport") + ";server_port=" + raopHandler.getServerPort());

            // ??? Why ???
            response.addHeader("Session", "DEADBEEF");
            break;
        case RECORD:
            // Headers
            // Range: ntp=0-
            // RTP-Info: seq={Note 1};rtptime={Note 2}
            // Note 1: Initial value for the RTP Sequence Number, random 16 bit value
            // Note 2: Initial value for the RTP Timestamps, random 32 bit value

            break;
        case FLUSH:
            raopHandler.flush();

            break;
        case TEARDOWN:
            response.addHeader("Connection", "close");

            break;
        case SET_PARAMETER:
            // Timing port
            m = volumePattern.matcher(request.getContent());
            if (m.find()) {
                double volume = Math.pow(10.0, 0.05 * Double.parseDouble(m.group(1)));
                raopHandler.setVolume(65536.0 * volume);
            }

            break;
        default:
            logger.warning("REQUEST(" + method + "): Not Supported Yet!");
            logger.warning(request.toString());
            break;
        }

        fireUpdate(new RtspEvent(this, method));

} catch (IllegalArgumentException e) {
 logger.warning(e.getMessage());
}

        // We close the response
        response.flush();
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
logger.fine("listening packets ... ");
                // feed buffer until packet completed
                StringBuilder packet = new StringBuilder();
                int ret;
                do {
                    char[] buffer = new char[4096];
                    ret = in.read(buffer);
                    packet.append(new String(buffer));
                } while (ret != -1 && !completedPacket.matcher(packet.toString()).find());

                if (ret != -1) {
                    // We handle the packet
                    RtspRequest request = new RtspRequest(packet.toString());
                    RtspResponse response = this.handleRequest(request);
if (logger.isLoggable(Level.FINE)) {
 System.out.println(request.toDebugString());
 System.out.println(response.toDebugString());
}
                    // Write the response to the wire
                    try {
                        BufferedWriter oStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        oStream.write(response.toString());
                        oStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if ("TEARDOWN".equals(request.getMethod())) {
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

        fireUpdate(new RtspEvent(this, EventType.CONNECTION_ENDED));
logger.fine("connection ended.");
    }
}
