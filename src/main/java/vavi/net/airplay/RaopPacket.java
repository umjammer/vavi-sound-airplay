/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;


/**
 * @author bencall
 */
public class RaopPacket {
    private byte[] aesIv;
    private byte[] aesKey;
    private int controlPort;
    @SuppressWarnings("unused")
    private int timingPort;
    private int frameSize;
    private int sampleSize;
    private BiquadFilter filter;
    private int[] fmtp;

    /**
     * @throws IllegalArgumentException when sample size is not 16
     */
    public RaopPacket(byte[] aesIv, byte[] aesKey, int[] fmtp, int controlPort, int timingPort) {
        // KEYS
        this.aesIv = aesIv;
        this.aesKey = aesKey;

        // PORTS
        this.controlPort = controlPort;
        this.timingPort = timingPort;

        // FMTP
        this.fmtp = fmtp;

        this.frameSize = fmtp[1];
        this.sampleSize = fmtp[3];
        if (this.sampleSize != 16) {
            throw new IllegalArgumentException("ERROR: 16 bits only!!!");
        }
    }

    public int getOutFrameBytes() {
        return 4 * (this.frameSize + 3);
    }

    public int[] getFmtp() {
        return fmtp;
    }

    public void resetFilter() {
        filter = new BiquadFilter(this.sampleSize, this.frameSize);
    }

    public void updateFilter(int size) {
        filter.update(size);
    }

    public BiquadFilter getFilter() {
        return filter;
    }

    public byte[] getAesIv() {
        return this.aesIv;
    }

    public byte[] getAesKey() {
        return this.aesKey;
    }

    public int getControlPort() {
        return this.controlPort;
    }

    public int getFrameSize() {
        return this.frameSize;
    }

    public int getSampleSize() {
        return this.sampleSize;
    }
}
