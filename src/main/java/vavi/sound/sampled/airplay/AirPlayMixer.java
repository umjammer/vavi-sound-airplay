/*
 * Copyright (c) 2022 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.airplay;

import javax.sound.sampled.Control;
import javax.sound.sampled.Control.Type;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;


/**
 * AirPlayMixer.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2022/02/21 umjammer initial version <br>
 */
public class AirPlayMixer implements Mixer {

    public static Mixer.Info mixerInfo = new Mixer.Info(
        "AirPlay Mixer",
        "vavi",
        "Mixer for AirPlay",
        "0.0.5") {};

    @Override
    public javax.sound.sampled.Mixer.Info getMixerInfo() {
        return mixerInfo;
    }

    @Override
    public javax.sound.sampled.Line.Info[] getSourceLineInfo() {
        return new javax.sound.sampled.Line.Info[0];
    }

    @Override
    public javax.sound.sampled.Line.Info[] getTargetLineInfo() {
        return new javax.sound.sampled.Line.Info[] {
            line.getLineInfo()
        };
    }

    @Override
    public javax.sound.sampled.Line.Info[] getSourceLineInfo(javax.sound.sampled.Line.Info info) {
        return getSourceLineInfo();
    }

    @Override
    public javax.sound.sampled.Line.Info[] getTargetLineInfo(javax.sound.sampled.Line.Info info) {
        if (info == line.getLineInfo()) {
            return getTargetLineInfo();
        } else {
            return new javax.sound.sampled.Line.Info[0];
        }
    }

    @Override
    public boolean isLineSupported(javax.sound.sampled.Line.Info info) {
        return info == line.getLineInfo();
    }

    @Override
    public Line getLine(javax.sound.sampled.Line.Info info) throws LineUnavailableException {
        if (info == line.getLineInfo()) {
            return line;
        } else {
            return null;
        }
    }

    @Override
    public int getMaxLines(javax.sound.sampled.Line.Info info) {
        if (info == line.getLineInfo()) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public Line[] getSourceLines() {
        // TODO should return default source data line?
        return new Line[0];
    }

    @Override
    public Line[] getTargetLines() {
        if (isOpen()) {
            return new Line[] {
                line
            };
        } else {
            return new Line[0];
        }
    }

    /** TODO how about multiple lines */
    private AirPlayTargetDataLine line = new AirPlayTargetDataLine();

    @Override
    public void open() throws LineUnavailableException {
        line.open();
    }

    @Override
    public void close() {
        line.close();
    }

    @Override
    public boolean isOpen() {
        return line.isOpen();
    }

    @Override
    public Control[] getControls() {
        return line.getControls();
    }

    @Override
    public boolean isControlSupported(Type control) {
        return line.isControlSupported(control);
    }

    @Override
    public Control getControl(Type control) {
        return line.getControl(control);
    }

    @Override
    public void addLineListener(LineListener listener) {
        line.addLineListener(listener);
    }

    @Override
    public void removeLineListener(LineListener listener) {
        line.removeLineListener(listener);
    }

    @Override
    public void synchronize(Line[] lines, boolean maintainSync) {
        // TODO Auto-generated method stub

    }

    @Override
    public void unsynchronize(Line[] lines) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isSynchronizationSupported(Line[] lines, boolean maintainSync) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public javax.sound.sampled.Line.Info getLineInfo() {
        return line.getLineInfo();
    }
}

/* */
