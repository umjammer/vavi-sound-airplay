
package vavi.net.airplay;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;


public class PCMPlayer implements RaopSink.Buffer {

    private AudioFormat audioFormat;
    private Info info;
    private SourceDataLine dataLine;

    public PCMPlayer() {
        try {
            audioFormat = new AudioFormat(44100, 16, 2, true, true);
            info = new DataLine.Info(SourceDataLine.class, audioFormat);
            dataLine = (SourceDataLine) AudioSystem.getLine(info);
            dataLine.open(audioFormat);
            dataLine.start();

        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int write(byte[] b, int ofs, int len) {
        return dataLine.write(b, ofs, len);
    }
}
