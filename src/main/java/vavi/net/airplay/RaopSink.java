/*
 * https://github.com/bencall/RPlay
 */

package vavi.net.airplay;


/**
 * Will create a new thread and play packets added to the ring buffer and set as
 * ready
 *
 * @author bencall
 */
public class RaopSink extends Thread {

    private RaopPacket session;
    private volatile long fix_volume = 0x10000;
    private short rand_a, rand_b;
    private RaopBuffer audioBuf;
    private boolean stopThread = false;
    private Sink sink;

    public interface Sink {
        int write(byte[] b, int ofs, int len);
    }

    public RaopSink(RaopPacket session, RaopBuffer audioBuf, Sink sink) {
        this.session = session;
        this.audioBuf = audioBuf;
        this.sink = sink;
    }

    public void run() {
        boolean fin = stopThread;

        while (!fin) {
            int[] buf = audioBuf.getNextFrame();
            if (buf == null) {
                continue;
            }

            int[] outbuf = new int[session.getOutFrameBytes()];
            int k = stuffBuffer(session.getFilter().getPlaybackRate(), buf, outbuf);

            byte[] input = new byte[outbuf.length * 2];

            int j = 0;
            for (int i = 0; i < outbuf.length; i++) {
                input[j++] = (byte) (outbuf[i] >> 8);
                input[j++] = (byte) (outbuf[i]);
            }

            sink.write(input, 0, k * 4);

            // Stop
            synchronized (this) {
                Thread.yield();
                fin = this.stopThread;
            }
        }
    }

    public synchronized void stopThread() {
        this.stopThread = true;
    }

    private int stuffBuffer(double playback_rate, int[] input, int[] output) {
        int stuffsamp = session.getFrameSize();
        int stuff = 0;
        double p_stuff;

        p_stuff = 1.0 - Math.pow(1.0 - Math.abs(playback_rate - 1.0), session.getFrameSize());

        if (Math.random() < p_stuff) {
            stuff = playback_rate > 1.0 ? -1 : 1;
            stuffsamp = (int) (Math.random() * (session.getFrameSize() - 2));
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
            for (int i = stuffsamp; i < session.getFrameSize() + stuff; i++) {
                output[j++] = ditheredVolume(input[l++]);
                output[j++] = ditheredVolume(input[l++]);
            }
        }
        return session.getFrameSize() + stuff;
    }

    public void setVolume(double vol) {
        fix_volume = (long) vol;
    }

    private short ditheredVolume(int sample) {
        long out;
        rand_b = rand_a;
        rand_a = (short) (Math.random() * 65535);

        out = sample * fix_volume;
        if (fix_volume < 0x10000) {
            out += rand_a;
            out -= rand_b;
        }
        return (short) (out >> 16);
    }
}
