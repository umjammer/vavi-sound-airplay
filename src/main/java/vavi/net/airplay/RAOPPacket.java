
package vavi.net.airplay;

import com.beatofthedrum.alacdecoder.MyAlacDecodeUtils;
import com.beatofthedrum.alacdecoder.MyAlacFile;


public class RAOPPacket {
    private MyAlacFile alac;
    private byte[] aesiv;
    private byte[] aeskey;
    private int controlPort;
    private int timingPort;
    private int frameSize;
    private int sampleSize;
    private int _7a;
    private int rice_historymult;
    private int rice_initialhistory;
    private int rice_kmodifier;
    private int _7f;
    private int _80;
    private int _82;
    private int _86;
    private int _8a_rate;
    private BiquadFilter bFilter;

    public RAOPPacket(byte[] aesiv, byte[] aeskey, int[] fmtp, int controlPort, int timingPort) {
        // KEYS
        this.aesiv = aesiv;
        this.aeskey = aeskey;

        // PORTS
        this.controlPort = controlPort;
        this.timingPort = timingPort;

        // FMTP
        frameSize = fmtp[1];
        _7a = fmtp[2];
        sampleSize = fmtp[3];
        rice_historymult = fmtp[4];
        rice_initialhistory = fmtp[5];
        rice_kmodifier = fmtp[6];
        _7f = fmtp[7];
        _80 = fmtp[8];
        _82 = fmtp[9];
        _86 = fmtp[10];
        _8a_rate = fmtp[11];

        initDecoder();
    }

    /**
     * Initiate the decoder
     *
     * @throws IllegalArgumentException when sample size is not 16
     */
    private void initDecoder() {

        if (this.getSampleSize() != 16) {
            throw new IllegalArgumentException("ERROR: 16 bits only!!!");
        }

        alac = MyAlacDecodeUtils.createAlac(this.getSampleSize(), 2);
        alac.setMaxSamplesPerFrame(this.getFrameSize());
        alac.set7a(this.get_7a());
        alac.setSamplesSize(this.getSampleSize());
        alac.setRiceHistoryMult(this.getRiceHistoryMult());
        alac.setRiceInitialHistory(this.getRiceInitialhistory());
        alac.setRiceKModifier(this.getRiceKModifier());
        alac.set7f(this.get_7f());
        alac.set80(this.get_80());
        alac.set82(this.get_82());
        alac.set86(this.get_86());
        alac.set8aRate(this.get_8a_rate());
    }

    public int getOutFrameBytes() {
        return 4 * (this.getFrameSize() + 3);
    }

    public MyAlacFile getAlac() {
        return alac;
    }

    public void resetFilter() {
        bFilter = new BiquadFilter(this.getSampleSize(), this.getFrameSize());
    }

    public void updateFilter(int size) {
        bFilter.update(size);
    }

    public BiquadFilter getFilter() {
        return bFilter;
    }

    public byte[] getAESIV() {
        return this.aesiv;
    }

    public byte[] getAESKEY() {
        return this.aeskey;
    }

    public int getControlPort() {
        return this.controlPort;
    }

    public int getTimingPort() {
        return this.timingPort;
    }

    public int getFrameSize() {
        return this.frameSize;
    }

    public int getSampleSize() {
        return this.sampleSize;
    }

    public int get_7a() {
        return this._7a;
    }

    public int getRiceHistoryMult() {
        return this.rice_historymult;
    }

    public int getRiceInitialhistory() {
        return this.rice_initialhistory;
    }

    public int get_8a_rate() {
        return this._8a_rate;
    }

    public int get_86() {
        return this._86;
    }

    public int get_82() {
        return this._82;
    }

    public int get_80() {
        return this._80;
    }

    public int get_7f() {
        return this._7f;
    }

    public int getRiceKModifier() {
        return this.rice_kmodifier;
    }
}
