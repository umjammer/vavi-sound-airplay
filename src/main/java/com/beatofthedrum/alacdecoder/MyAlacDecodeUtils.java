
package com.beatofthedrum.alacdecoder;

public class MyAlacDecodeUtils {

    public static MyAlacFile createAlac(int samplesize, int numchannels) {
        AlacFile newfile = new AlacFile();

        newfile.samplesize = samplesize;
        newfile.numchannels = numchannels;
        newfile.bytespersample = (samplesize / 8) * numchannels;

        return new MyAlacFile(newfile);
    }

    public static int decodeFrame(MyAlacFile alac, byte[] inbuffer, int[] outbuffer, int outputsize) {
        return AlacDecodeUtils.decode_frame(alac.alacFile, inbuffer, outbuffer, outputsize);
    }
}
