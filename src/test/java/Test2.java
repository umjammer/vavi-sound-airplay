/*
 * Copyright (c) 2012 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import vavi.apps.rplay.AirPlayTargetDataLine;
import vavi.util.Debug;


/**
 * line. (airplay -> speaker)
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2012/06/11 umjammer initial version <br>
 */
public class Test2 {

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        // speaker
        AudioFormat airplayFormat = new AudioFormat(44100, 16, 2, true, true);
//        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, targetFormat);
//Debug.println(targetInfo);
//        TargetDataLine target = (TargetDataLine) AudioSystem.getLine(targetInfo);
        TargetDataLine airplay = new AirPlayTargetDataLine();
        airplay.open(airplayFormat);
        airplay.start();
        AudioInputStream stream = new AudioInputStream(airplay);

        // microphone
        AudioFormat speakerFormat = airplay.getFormat();
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, speakerFormat);
Debug.println(speakerInfo);
        SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
        speaker.open(speakerFormat);
//FloatControl gainControl = (FloatControl) speaker.getControl(FloatControl.Type.MASTER_GAIN);
//double gain = .2d; // number between 0 and 1 (loudest)
//float dB = (float) (Math.log(gain) / Math.log(10.0) * 20.0);
//gainControl.setValue(dB);
        speaker.start();

        byte[] buf = new byte[8196];
        while (true) {
            int r = stream.read(buf);
            if (r < 0) {
Debug.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX stream end.");
                break;
            }
//Debug.println("outgoing buffer: " + r);
            speaker.write(buf, 0, r);
        }

        speaker.drain();
        speaker.stop();
        speaker.close();

        airplay.close();
    }
}

/* */
