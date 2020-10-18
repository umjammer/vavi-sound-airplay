/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;

public class RtspResponse {

    private StringBuilder response = new StringBuilder();

    public RtspResponse(String header) {
        response.append(header + "\r\n");
    }

    public void append(String key, String value) {
        response.append(key + ": " + value + "\r\n");
    }

    /**
     * close the response
     */
    public void finalize() {
        response.append("\r\n");
    }

    public String getRawPacket() {
        return response.toString();
    }

    @Override
    public String toString() {
        return " -> " + response.toString().replaceAll("\r\n", "\r\n -> ");
    }
}
