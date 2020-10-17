/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.airplay;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Control;
import javax.sound.sampled.Control.Type;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import vavi.net.airplay.RAOPSink.Sink;
import vavi.net.airplay.RTSPServer;
import vavi.util.Debug;


/**
 * AirPlayTargetDataLine.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/16 umjammer initial version <br>
 */
public class AirPlayTargetDataLine implements TargetDataLine {

    private AudioFormat audioFormat;

    private List<LineListener> listeners = new ArrayList<>();

    protected void fireUpdate(LineEvent event) {
        listeners.forEach(l -> l.update(event));
    }

    public AirPlayTargetDataLine() {
        audioFormat = new AudioFormat(44100, 16, 2, true, true);
    }

    private int available = -1;

    private int bufferSize = 8196;

    // 8196 is the best
    private BlockingDeque<Byte> buffer = new LinkedBlockingDeque<>(bufferSize);

    private Sink sink = new Sink() {
        @Override
        public int write(byte[] b, int ofs, int len) {
            int i = ofs;
            try {
                for (; i < len; i++) {
                    buffer.put(b[i]);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//Debug.println("incoming: " + i);
            return i - ofs;
        }
    };

    @Override
    public void drain() {
        // TODO Auto-generated method stub
    }

    @Override
    public void flush() {
        // TODO Auto-generated method stub
    }

    boolean isRunning;

    @Override
    public void start() {
        while (!isOpen) {
            try { Thread.sleep(5000); } catch (InterruptedException e) { e.printStackTrace(); }
Debug.println("waiting for server open...");
        }
        isRunning = true;
        fireUpdate(new LineEvent(this, javax.sound.sampled.LineEvent.Type.START, -1));
    }

    @Override
    public void stop() {
        isRunning = false;
        fireUpdate(new LineEvent(this, javax.sound.sampled.LineEvent.Type.STOP, -1));
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public boolean isActive() {
        return isRunning;
    }

    @Override
    public AudioFormat getFormat() {
        return audioFormat;
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public int available() {
        return available;
    }

    /* @see javax.sound.sampled.DataLine#getFramePosition() */
    @Override
    public int getFramePosition() {
        // TODO Auto-generated method stub
        return 0;
    }

    /* @see javax.sound.sampled.DataLine#getLongFramePosition() */
    @Override
    public long getLongFramePosition() {
        // TODO Auto-generated method stub
        return 0;
    }

    /* @see javax.sound.sampled.DataLine#getMicrosecondPosition() */
    @Override
    public long getMicrosecondPosition() {
        // TODO Auto-generated method stub
        return 0;
    }

    /* @see javax.sound.sampled.DataLine#getLevel() */
    @Override
    public float getLevel() {
        // TODO Auto-generated method stub
        return 0;
    }

    private static javax.sound.sampled.Line.Info info = 
            new javax.sound.sampled.Line.Info(AirPlayTargetDataLine.class);

    @Override
    public javax.sound.sampled.Line.Info getLineInfo() {
        return info;
    }

    private boolean isOpen;

    private RTSPServer server;

    @Override
    public void open() throws LineUnavailableException {
        server = new RTSPServer("AirPlayTargetDataLine");
        server.addRTSPListener(r -> {
            if (!isOpen && r.getReq().equals("SETUP")) {
                isOpen = true;
                fireUpdate(new LineEvent(AirPlayTargetDataLine.this, javax.sound.sampled.LineEvent.Type.OPEN, -1));
Debug.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ line open.");
            }
        });
        server.setRAOPSink(sink);
Debug.println("rtsp sink set: " + sink);
        server.start();
    }

    /* @see javax.sound.sampled.Line#close() */
    @Override
    public void close() {
        server.stopThread();
        fireUpdate(new LineEvent(this, javax.sound.sampled.LineEvent.Type.CLOSE, -1));

        isOpen = false;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public Control[] getControls() {
        return null;
    }

    @Override
    public boolean isControlSupported(Type control) {
        return false;
    }

    @Override
    public Control getControl(Type control) {
        return null;
    }

    @Override
    public void addLineListener(LineListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeLineListener(LineListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void open(AudioFormat format, int bufferSize) throws LineUnavailableException {
        this.bufferSize = bufferSize;
        open(format);
    }

    @Override
    public void open(AudioFormat format) throws LineUnavailableException {
        if (!audioFormat.matches(format)) {
            throw new LineUnavailableException("unsupported: " + format);
        }
        open();
    }

    @Override
    public int read(byte[] b, int off, int len) {
        int i = off;
        try {
            for (; i < len; i++) {
                b[i] = buffer.take();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return i - off;
    }
}

/* */
