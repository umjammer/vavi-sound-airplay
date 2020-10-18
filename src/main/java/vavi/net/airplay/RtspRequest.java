/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Extract informations from RTSP Header
 *
 * @author bencall
 */
public class RtspRequest {

    private String req;
    private String directory;
    private String rtspVersion;
    private String content;
    private List<String> headers;
    private List<String> headerContent;
    private String rawPacket;

    private static final Pattern firstLinePattern = Pattern.compile("^(\\w+)\\W(.+)\\WRTSP/(.+)\r\n");
    private static final Pattern headerPattern = Pattern.compile("^([\\w-]+):\\W(.+)\r\n", Pattern.MULTILINE);
    private static final Pattern contentPattern = Pattern.compile("\r\n\r\n(.+)", Pattern.DOTALL);

    public RtspRequest(String packet) {
        // Init arrays
        headers = new ArrayList<>();
        headerContent = new ArrayList<>();
        rawPacket = packet;

        // If packet completed
        // First line
        Matcher m = firstLinePattern.matcher(packet);
        if (m.find()) {
            req = m.group(1);
            directory = m.group(2);
            rtspVersion = m.group(3);
        }

        // Header fields
        m = headerPattern.matcher(packet);
        while (m.find()) {
            headers.add(m.group(1));
            headerContent.add(m.group(2));
        }

        // Content if present or null if not
        m = contentPattern.matcher(packet);
        if (m.find()) {
            content = m.group(1).trim();
            if (content.isEmpty()) {
                content = null;
            }
        }
    }

    @Override
    public String toString() {
        return rawPacket;
    }

    public String getContent() {
        return content;
    }

    public String getMethod() {
        return req;
    }

    public String getVersion() {
        return rtspVersion;
    }

    public String getDirectory() {
        return directory;
    }

    public int getCode() {
        return 200;
    }

    public String getHeader(String headerName) {
        int i = headers.indexOf(headerName);
        if (i == -1) {
            return null;
        }
        return headerContent.get(i);
    }

    public String toDebugString() {
        return " <- " + rawPacket.replaceAll("\r\n", "\r\n <- ");
    }
}
