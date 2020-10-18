
package vavi.net.airplay;

/**
 * Main class
 *
 * <li> 201015 works
 *
 * @author bencall
 */
public class RPlay {

    /**
     * @param args
     */
    public static void main(String[] args) {
        RtspServer server = new RtspServer("RPlay");
        server.setRaopSink(new PCMPlayer());
        server.start();
    }
}
