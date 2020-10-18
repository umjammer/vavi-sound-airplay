/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;


/**
 * @author bencall
 */
public class RtspResponse {

    private StringBuilder response = new StringBuilder();

    public RtspResponse(String firstLine) {
        response.append(firstLine + "\r\n");
    }

    public void addHeader(String key, String value) {
        response.append(key + ": " + value + "\r\n");
    }

    /**
     * close the response
     */
    public void flush() {
        response.append("\r\n");
    }

    @Override
    public String toString() {
        return response.toString();
    }

    public String toDebugString() {
        return " -> " + response.toString().replaceAll("\r\n", "\r\n -> ");
    }
}
