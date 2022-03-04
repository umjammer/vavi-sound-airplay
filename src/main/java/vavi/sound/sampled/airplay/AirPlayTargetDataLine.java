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
import java.util.logging.Level;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Control;
import javax.sound.sampled.Control.Type;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import vavi.net.airplay.RaopSink.Buffer;
import vavi.net.airplay.RtspServer;
import vavi.net.airplay.RtspServer.RtspListener.EventType;
import vavi.net.airplay.RtspServer.RtspListener.RequestType;
import vavi.util.Debug;


/**
 * AirPlayTargetDataLine.
 *
 * TODO connect default source data line automatically?
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/16 umjammer initial version <br>
 */
public class AirPlayTargetDataLine implements TargetDataLine {

    public static javax.sound.sampled.DataLine.Info info =
            new javax.sound.sampled.DataLine.Info(AirPlayTargetDataLine.class,
                                                  new AudioFormat(44100, 16, 2, true, true));

    private List<LineListener> listeners = new ArrayList<>();

    protected void fireUpdate(LineEvent event) {
        listeners.forEach(l -> l.update(event));
    }

    private boolean isOpen;

    private boolean isAnnounced;

    private RtspServer server;

    private boolean isRunning;

    private int available = -1;

    // 8196 is the best
    private int bufferSize = 8196;

    /** the thread which executes buffer#take() */
    private Thread blockingDequeThread;

    private BlockingDeque<Byte> buffer;

    private Buffer sink = new Buffer() {
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

    @Override
    public void start() {
        while (!isOpen) {
            try { Thread.sleep(5000); } catch (InterruptedException e) { e.printStackTrace(); }
if (!isAnnounced) {
 Debug.println("waiting for server open...");
} else {
 Debug.println("connect me from iTunes...");
}
        }
        isRunning = true;
        fireUpdate(new LineEvent(this, javax.sound.sampled.LineEvent.Type.START, -1));
    }

    @Override
    public void stop() {
        // TODO stop something
        blockingDequeThread.interrupt();

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
        return info.getFormats()[0];
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public int available() {
        return available;
    }

    @Override
    public int getFramePosition() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getLongFramePosition() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getMicrosecondPosition() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getLevel() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public javax.sound.sampled.Line.Info getLineInfo() {
        return info;
    }

    @Override
    public void open() throws LineUnavailableException {
        if (server != null) {
Debug.println("server is already running");
            return;
        }
        buffer = new LinkedBlockingDeque<>(bufferSize);

        server = new RtspServer("AirPlayTargetDataLine");
        server.addRtspListener(r -> {
Debug.println(Level.FINE, "rtsp event type: " + r.getType());
            if (r.getType() == RequestType.SETUP) {
                if (!isOpen) {
                    isOpen = true;
                    fireUpdate(new LineEvent(AirPlayTargetDataLine.this, javax.sound.sampled.LineEvent.Type.OPEN, -1));
                }
            } else if (r.getType() == EventType.MDNS_ANNOUNCED) {
                isAnnounced = true;
            } else if (r.getType() == EventType.CONNECTION_ENDED) {
                isRunning = false;
                fireUpdate(new LineEvent(AirPlayTargetDataLine.this, javax.sound.sampled.LineEvent.Type.STOP, -1));
            }
        });
        server.setRaopSink(sink);
Debug.println(Level.FINE, "rtsp sink set: " + sink.getClass().getName());
        server.start();

        blockingDequeThread = Thread.currentThread();
    }

    /* @see javax.sound.sampled.Line#close() */
    @Override
    public void close() {
        server.stop();
        fireUpdate(new LineEvent(this, javax.sound.sampled.LineEvent.Type.CLOSE, -1));

        isOpen = false;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public Control[] getControls() {
        return new Control[0];
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

    /**
     * @param bufferSize changing buffer size is not recommended
     */
    @Override
    public void open(AudioFormat format, int bufferSize) throws LineUnavailableException {
        this.bufferSize = bufferSize;
        open(format);
    }

    @Override
    public void open(AudioFormat format) throws LineUnavailableException {
        if (!info.getFormats()[0].matches(format)) {
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
Debug.println("BlockingDeque#take() interrupted");
        }
        return i - off;
    }
}

/* */
