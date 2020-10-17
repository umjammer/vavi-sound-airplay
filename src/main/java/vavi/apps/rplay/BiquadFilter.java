
package vavi.apps.rplay;


/**
 * Implementation of the biquad filter. Not sure it is correct
 *
 * @author bencall
 */
public class BiquadFilter {

    private static class Biquad {
        double[] hist = new double[2];
        double[] a = new double[2];
        double[] b = new double[3];

        Biquad(double freq, double Q, double samplingRate, double frameSize) {
            double w0 = 2 * Math.PI * freq / (samplingRate / frameSize);
            double alpha = Math.sin(w0) / (2.0 * Q);

            double a_0 = 1.0 + alpha;
            this.b[0] = (1.0 - Math.cos(w0)) / (2.0 * a_0);
            this.b[1] = (1.0 - Math.cos(w0)) / a_0;
            this.b[2] = this.b[0];
            this.a[0] = -2.0 * Math.cos(w0) / a_0;
            this.a[1] = (1 - alpha) / a_0;
        }

        double filter(double in) {
            double w = in - this.a[0] * this.hist[0] - this.a[1] * this.hist[1];
//          double out  = this.b[1] * this.hist[0] + this.b[2] * this.hist[1] + this.b[0] * w;
            this.hist[1] = this.hist[0];
            this.hist[0] = w;
            return w;
        }
    }

    private  double playbackRate = 1.0;
    // local clock is slower by
    private double estDrift = 0.0;
    private Biquad driftLpf;
    private double estErr = 0.0, lastErr;
    private Biquad errLpf, errDerivLpf;
    private double desiredFill;
    private int fillCount;
    private static final double CONTROL_A = 1e-4;
    private static final double CONTROL_B = 1e-1;

    public BiquadFilter(int samplingRate, int frameSize) {
        driftLpf = new Biquad(1.0 / 180.0, 0.3, samplingRate, frameSize);
        errLpf = new Biquad(1.0 / 10.0, 0.25, samplingRate, frameSize);
        errDerivLpf = new Biquad(1.0 / 2.0, 0.2, samplingRate, frameSize);
        fillCount = 0;
        playbackRate = 1.0;
        estErr = 0;
        lastErr = 0;
        desiredFill = 0;
        fillCount = 0;
    }

    public void update(int fill) {
        if (fillCount < 1000) {
            desiredFill += fill / 1000.0;
            fillCount++;
            return;
        }

        double buf_delta = fill - desiredFill;
        estErr = errLpf.filter(buf_delta);
        double err_deriv = errDerivLpf.filter(estErr - lastErr);

        estDrift = driftLpf.filter(CONTROL_B * (estErr * CONTROL_A + err_deriv) + estDrift);

        playbackRate = 1.0 + CONTROL_A * estErr + estDrift;
        lastErr = estErr;
    }

    public double getPlaybackRate() {
        return playbackRate;
    }
}
