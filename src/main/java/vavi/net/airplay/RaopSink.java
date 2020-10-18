/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Will create a new thread and play packets added to the ring buffer and set as
 * ready
 *
 * @author bencall
 */
public class RaopSink {

    private RaopPacket packet;
    private volatile long fixVolume = 0x10000;
    private short randA, randB;
    private RaopBuffer audioBuffer;
    private boolean stopThread = false;
    private Buffer sinkBuffer;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Buffer {
        int write(byte[] b, int ofs, int len);
    }

    public RaopSink(RaopPacket packet, RaopBuffer audioBuffer, Buffer sinkBuffer) {
        this.packet = packet;
        this.audioBuffer = audioBuffer;
        this.sinkBuffer = sinkBuffer;
        executor.submit(this::run);
    }

    private void run() {
        boolean fin = stopThread;

        while (!fin) {
            int[] buf = audioBuffer.getNextFrame();
            if (buf == null) {
                continue;
            }

            int[] outbuf = new int[packet.getOutFrameBytes()];
            int k = stuffBuffer(packet.getFilter().getPlaybackRate(), buf, outbuf);

            byte[] input = new byte[outbuf.length * 2];

            int j = 0;
            for (int i = 0; i < outbuf.length; i++) {
                input[j++] = (byte) (outbuf[i] >> 8);
                input[j++] = (byte) (outbuf[i]);
            }

            sinkBuffer.write(input, 0, k * 4);

            // Stop
            synchronized (this) {
                Thread.yield();
                fin = this.stopThread;
            }
        }
    }

    public void stop() {
        this.stopThread = true;
        executor.shutdown();
    }

    private int stuffBuffer(double playbackRate, int[] input, int[] output) {
        int stuffsamp = packet.getFrameSize();
        int stuff = 0;
        double pStuff;

        pStuff = 1.0 - Math.pow(1.0 - Math.abs(playbackRate - 1.0), packet.getFrameSize());

        if (Math.random() < pStuff) {
            stuff = playbackRate > 1.0 ? -1 : 1;
            stuffsamp = (int) (Math.random() * (packet.getFrameSize() - 2));
        }

        int j = 0;
        int l = 0;
        for (int i = 0; i < stuffsamp; i++) { // the whole frame, if no stuffing
            output[j++] = ditheredVolume(input[l++]);
            output[j++] = ditheredVolume(input[l++]);
        }

        if (stuff != 0) {
            if (stuff == 1) {
                // interpolate one sample
                output[j++] = ditheredVolume((input[l - 2] + input[l]) >> 1);
                output[j++] = ditheredVolume((input[l - 1] + input[l + 1]) >> 1);
            } else if (stuff == -1) {
                l -= 2;
            }
            for (int i = stuffsamp; i < packet.getFrameSize() + stuff; i++) {
                output[j++] = ditheredVolume(input[l++]);
                output[j++] = ditheredVolume(input[l++]);
            }
        }
        return packet.getFrameSize() + stuff;
    }

    public void setVolume(double vol) {
        fixVolume = (long) vol;
    }

    private short ditheredVolume(int sample) {
        long out;
        randB = randA;
        randA = (short) (Math.random() * 65535);

        out = sample * fixVolume;
        if (fixVolume < 0x10000) {
            out += randA;
            out -= randB;
        }
        return (short) (out >> 16);
    }
}
