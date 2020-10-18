/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import vavi.net.airplay.RtspServer.RtspListener.EventType;
import vavi.net.airplay.RtspServer.RtspListener.RtspEvent;
import vavi.util.ByteUtil;


/**
 * starts services
 *
 * @author bencall
 */
public class RtspServer {

    private static Logger logger = Logger.getLogger(RtspServer.class.getName());

    private Bonjour mdns;
    private ServerSocket serverSocket = null;
    private String name;
    private int port;
    private transient String password;
    private boolean stopThread = false;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface RtspListener {
        class RtspEvent extends EventObject {
            private Type type;
            public RtspEvent(Object source, Type type) {
                super(source);
                this.type = type;
            }
            public Type getType() {
                return type;
            }
        }
        interface Type {}
        enum EventType implements Type {
            MDNS_ANNOUNCED,
            SERVER_CLOSE,
            CONNECTION_ENDED;
        }
        enum RequestType implements Type {
            ANNOUNCE,
            SETUP,
            RECORD,
            PAUSE,
            FLUSH,
            TEARDOWN,
            OPTIONS,
            GET_PARAMETER,
            SET_PARAMETER;
            public static String list() {
                return String.join(", ", Arrays.stream(values()).map(e -> e.toString()).toArray(String[]::new));
            }
        }
        void update(RtspEvent request);
    }

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
    }

    /** */
    public RtspServer(String name) {
        this(name, null);
    }

    /** */
    public RtspServer(String name, String password) {
        this(name, 5000, password);
    }

    /** */
    public RtspServer(String name, int port, String password) {
        this.name = name;
        this.port = port;
        this.password = password;
    }

    private byte[] getHardwareAdress() {
        byte[] hwAddr = null;
        InetAddress local;

        try {
            local = InetAddress.getLocalHost();
            NetworkInterface ni = NetworkInterface.getByInetAddress(local);

            if (ni != null) {
                hwAddr = ni.getHardwareAddress();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return hwAddr;
    }

    public void start() {
        executor.submit(this::run);
    }

    private void run() {
logger.fine("starting service...");

        // Setup safe shutdown
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
System.err.println("shutting down...");

                RtspServer.this.stop();

                try {
                    RtspServer.this.mdns.stop();
                    RtspServer.this.serverSocket.close();

System.err.println("service stopped.");
                } catch (IOException e) {
                    //
                }
            }
        });

        try {
            // DNS Emitter (Bonjour)
            byte[] hwAddr = getHardwareAdress();

            // Check if password is set
            mdns = new Bonjour(name, ByteUtil.toHexString(hwAddr), port, password != null);

logger.fine("announced [" + name + "@" + ByteUtil.toHexString(hwAddr) + "]");
            fireUpdate(new RtspEvent(this, EventType.MDNS_ANNOUNCED));

            // We listen for new connections
            try {
                serverSocket = new ServerSocket(port);
            } catch (IOException e) {
logger.info("port busy, using default.");
                serverSocket = new ServerSocket();
            }

            serverSocket.setSoTimeout(1000);

logger.fine("service started.");

            while (!stopThread) {
                try {
                    Socket socket = serverSocket.accept();
logger.info("accepted connection from " + socket.toString());

                    // Check if password is set
                    RtspHandler handler;
                    if (password == null)
                        handler = new RtspHandler(hwAddr, socket);
                    else
                        handler = new RtspHandler(hwAddr, socket, password);
                    handler.setRaopSink(sink);
                    listeners.forEach(handler::addRtspListener);
                    handler.start();
                } catch (SocketTimeoutException e) {
                    //
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);

        } finally {
            try {
                mdns.stop();
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        stopThread = true;
        executor.shutdown();
    }
}
