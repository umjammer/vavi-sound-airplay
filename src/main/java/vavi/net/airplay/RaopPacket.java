/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;

import com.beatofthedrum.alacdecoder.MyAlacDecodeUtils;
import com.beatofthedrum.alacdecoder.MyAlacFile;


/**
 * @author bencall
 */
public class RaopPacket {
    private MyAlacFile alac;
    private byte[] aesIv;
    private byte[] aesKey;
    private int controlPort;
    @SuppressWarnings("unused")
    private int timingPort;
    private int frameSize;
    private int sampleSize;
    private int _7a;
    private int riceHistoryMult;
    private int riceInitialHistory;
    private int riceKModifier;
    private int _7f;
    private int _80;
    private int _82;
    private int _86;
    private int _8a_rate;
    private BiquadFilter filter;

    public RaopPacket(byte[] aesIv, byte[] aesKey, int[] fmtp, int controlPort, int timingPort) {
        // KEYS
        this.aesIv = aesIv;
        this.aesKey = aesKey;

        // PORTS
        this.controlPort = controlPort;
        this.timingPort = timingPort;

        // FMTP
        frameSize = fmtp[1];
        _7a = fmtp[2];
        sampleSize = fmtp[3];
        riceHistoryMult = fmtp[4];
        riceInitialHistory = fmtp[5];
        riceKModifier = fmtp[6];
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

        alac = MyAlacDecodeUtils.createAlac(this.sampleSize, 2);
        alac.setMaxSamplesPerFrame(this.frameSize);
        alac.set7a(this._7a);
        alac.setSamplesSize(this.sampleSize);
        alac.setRiceHistoryMult(this.riceHistoryMult);
        alac.setRiceInitialHistory(this.riceInitialHistory);
        alac.setRiceKModifier(this.riceKModifier);
        alac.set7f(this._7f);
        alac.set80(this._80);
        alac.set82(this._82);
        alac.set86(this._86);
        alac.set8aRate(this._8a_rate);
    }

    public int getOutFrameBytes() {
        return 4 * (this.frameSize + 3);
    }

    public MyAlacFile getAlac() {
        return alac;
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
