
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
        RTSPServer server = new RTSPServer("RPlay");
        server.setRAOPSink(new PCMPlayer());
        server.start();
    }
}
