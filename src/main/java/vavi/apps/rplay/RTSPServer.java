
package vavi.apps.rplay;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import vavi.util.ByteUtil;


/**
 * starts services
 *
 * @author bencall
 */
public class RTSPServer extends Thread {

    private static Logger logger = Logger.getLogger(RTSPServer.class.getName());

    private Bonjour mdns;
    private ServerSocket servSock = null;
    private String name;
    private String password;
    private boolean stopThread = false;

    interface RTSPListener {
        void requestHappend(RTSPRequest request);
    }

    private List<RTSPListener> listeners = new ArrayList<>();

    public void addRTSPListener(RTSPListener listener) {
        listeners.add(listener);
    }

    private RAOPSink.Sink sink;

    public void setRAOPSink(RAOPSink.Sink sink) {
        this.sink = sink;
    }

    /** */
    public RTSPServer(String name) {
        this.name = name;
    }

    /** */
    public RTSPServer(String name, String pass) {
        this.name = name;
        this.password = pass;
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
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        return hwAddr;
    }

    public void run() {
logger.info("starting service...");

        // Setup safe shutdown
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
System.err.println("shutting down...");

                RTSPServer.this.stopThread();

                try {
                    RTSPServer.this.mdns.stop();
                    RTSPServer.this.servSock.close();

System.err.println("service stopped.");
                } catch (IOException e) {
                    //
                }
            }
        });

        int port = 5002;

        try {
            // DNS Emitter (Bonjour)
            byte[] hwAddr = getHardwareAdress();

            // Check if password is set
            if (password == null)
                mdns = new Bonjour(name, ByteUtil.toHexString(hwAddr), port, false);
            else
                mdns = new Bonjour(name, ByteUtil.toHexString(hwAddr), port, true);

logger.info("announced [" + name + " @ " + ByteUtil.toHexString(hwAddr) + "]");

            // We listen for new connections
            try {
                servSock = new ServerSocket(port);
            } catch (IOException e) {
logger.info("port busy, using default.");
                servSock = new ServerSocket();
            }

            servSock.setSoTimeout(1000);

logger.info("service started.");

            while (!stopThread) {
                try {
                    Socket socket = servSock.accept();
logger.info("accepted connection from " + socket.toString());

                    // Check if password is set
                    RTSPHandler handler;
                    if (password == null)
                        handler = new RTSPHandler(hwAddr, socket);
                    else
                        handler = new RTSPHandler(hwAddr, socket, password);
                    handler.setRAOPSink(sink);
                    listeners.forEach(handler::addRTSPListener);
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
                servSock.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void stopThread() {
        stopThread = true;
    }
}
