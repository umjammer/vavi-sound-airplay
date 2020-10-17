
package vavi.apps.rplay;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.beatofthedrum.alacdecoder.MyAlacDecodeUtils;


/**
 * A ring buffer where every frame is decrypted, decoded and stored Basically,
 * you can put and get packets
 *
 * @author bencall
 */
public class RAOPBuffer {

    private static Logger logger = Logger.getLogger(RAOPBuffer.class.getName());

    private static class AudioData {
        boolean ready;
        int[] data;
    }

    // Constants - Should be somewhere else

    /** Total buffer size (number of frame) */
    public static final int BUFFER_FRAMES = 512;
    /** Alac will wait till there are START_FILL frames in buffer */
    public static final int START_FILL = 282;
    /** Also in UDPListener (possible to merge it in one place?) */
    public static final int MAX_PACKET = 2048;

    /** The lock for writing/reading concurrency */
    private final Lock lock = new ReentrantLock();
    /** The array that represents the buffer */
    private AudioData[] audioBuffer;
    /** Can we read in buffer? */
    private boolean synced = false;
    /** Audio infos (rate, etc...) */
    private RAOPPacket session;
    /** The seqnos at which we read and write */
    private int readIndex;
    private int writeIndex;
    /** The number of packet in buffer */
    private int actualBufSize;

    /**
     * The decoder stops 'cause the isn't enough packet. Waits till buffer is ok
     */
    private boolean isDecoderStopped = false;

    /** RSA-AES decryption infos */
    private static class Crypto {
        Cipher cipher;
        Crypto(byte[] aes, byte[] iv) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
            SecretKeySpec keySpec = new SecretKeySpec(aes, "AES");
            cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        }
        int decrypt(byte[] array, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException {
            return cipher.update(array, inputOffset, inputLen, output, outputOffset);
        }
    }

    Crypto crypto;

    /** Needed for asking missing packets */
    private BiConsumer<Integer, Integer> resender;

    /**
     * Instantiate the buffer
     *
     * @param session audio infos
     * @param resender where to ask for resending missing packets
     */
    public RAOPBuffer(RAOPPacket session, BiConsumer<Integer, Integer> resender) {
        this.session = session;
        this.resender = resender;

        audioBuffer = new AudioData[BUFFER_FRAMES];
        for (int i = 0; i < BUFFER_FRAMES; i++) {
            audioBuffer[i] = new AudioData();
            // OUTFRAME_BYTES = 4(frameSize + 3)
            audioBuffer[i].data = new int[session.getOutFrameBytes()];
        }
    }

    /**
     * Sets the packets as not ready. Audio thread will only listen to ready
     * packets. No audio more.
     */
    public void flush() {
        for (int i = 0; i < BUFFER_FRAMES; i++) {
            audioBuffer[i].ready = false;
            synced = false;
        }
    }

    /**
     * Returns the next ready frame. If none, waiting for one
     * 
     * @return
     */
    public int[] getNextFrame() {
        synchronized (lock) {
            actualBufSize = writeIndex - readIndex; // Packets in buffer
            if (actualBufSize < 0) { // If loop
                actualBufSize = 65536 - readIndex + writeIndex;
            }

            if (actualBufSize < 1 || !synced) {
                // If no packets more or Not synced (flush: pause)
                if (synced) {
                    // If it' because there is not enough packets
logger.info("Underrun!!! Not enough frames in buffer!");
                }

                try {
                    // We say the decoder is stopped and we wait for signal
logger.info("Waiting");
                    isDecoderStopped = true;
                    lock.wait();
                    isDecoderStopped = false;
logger.info("re-starting");
                    readIndex++; // We read next packet

                    // Underrun: stream reset
                    session.resetFilter();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                return null;
            }

            // Overrunning. Restart at a sane distance
            if (actualBufSize >= BUFFER_FRAMES) {
                // overrunning! uh-oh. restart at a sane distance
logger.info("Overrun!!! Too much frames in buffer!");
                readIndex = writeIndex - START_FILL;
            }
            // we get the value before the unlock ;-)
            int read = readIndex;
            readIndex++;

            // If loop
            actualBufSize = writeIndex - readIndex;
            if (actualBufSize < 0) {
                actualBufSize = 65536 - readIndex + writeIndex;
            }

            session.updateFilter(actualBufSize);

            AudioData buf = audioBuffer[read % BUFFER_FRAMES];

            if (!buf.ready) {
logger.info("Missing Frame!");
                // Set to zero then
                for (int i = 0; i < buf.data.length; i++) {
                    buf.data[i] = 0;
                }
            }
            buf.ready = false;

            // SEQNO is stored in a short an come back to 0 when equal to 65536
            // (2 bytes)
            if (readIndex == 65536) {
                readIndex = 0;
            }

            return buf.data;
        }
    }

    /**
     * Adds packet into the buffer
     *
     * @param seqno seqno of the given packet. Used as index
     * @param data
     */
    public void putPacketInBuffer(int seqno, byte[] data) {
        // Ring buffer may be implemented in a Hashtable in java (simplier), but
        // is it fast enough?
        // We lock the thread
        synchronized (lock) {

            if (!synced) {
                writeIndex = seqno;
                readIndex = seqno;
                synced = true;
            }

            @SuppressWarnings("unused")
            int outputSize = 0;
            if (seqno == writeIndex) { // Packet we expected
                // With (seqno % BUFFER_FRAMES) we loop from 0 to BUFFER_FRAMES
                outputSize = decodeAlac(data, audioBuffer[(seqno % BUFFER_FRAMES)].data);
                audioBuffer[(seqno % BUFFER_FRAMES)].ready = true;
                writeIndex++;
            } else if (seqno > writeIndex) {
                // Too early, did we miss some packet between writeIndex and
                // seqno?
                resender.accept(writeIndex, seqno);
                outputSize = decodeAlac(data, audioBuffer[(seqno % BUFFER_FRAMES)].data);
                audioBuffer[(seqno % BUFFER_FRAMES)].ready = true;
                writeIndex = seqno + 1;
            } else if (seqno > readIndex) {
                // readIndex < seqno < writeIndex not yet played but too late.
                // Still ok
                outputSize = decodeAlac(data, audioBuffer[(seqno % BUFFER_FRAMES)].data);
                audioBuffer[(seqno % BUFFER_FRAMES)].ready = true;
            } else {
                // Really to late
logger.info("Late packet with seq. numb.: " + seqno);
            }

            // The number of packet in buffer
            actualBufSize = writeIndex - readIndex;
            if (actualBufSize < 0) {
                actualBufSize = 65536 - readIndex + writeIndex;
            }

            if (isDecoderStopped && actualBufSize > START_FILL) {
                lock.notify();
            }

            // SEQNO is stored in a short an come back to 0 when equal to 65536
            // (2 bytes)
            if (writeIndex == 65536) {
                writeIndex = 0;
            }
        }
    }

    /**
     * Decrypt and decode the packet.
     *
     * @param data
     * @param outbuffer the result
     * @return
     */
    private int decodeAlac(byte[] data, int[] outbuffer) {
        byte[] packet = new byte[MAX_PACKET];

        // Init AES
        initAES();

        int i;
        for (i = 0; i + 16 <= data.length; i += 16) {
            // Decrypt
            this.decryptAES(data, i, 16, packet, i);
        }

        // The rest of the packet is unencrypted
        for (int k = 0; k < (data.length % 16); k++) {
            packet[i + k] = data[i + k];
        }

        int outputsize = 0;
        outputsize = MyAlacDecodeUtils.decodeFrame(session.getAlac(), packet, outbuffer, outputsize);

        assert outputsize == session.getFrameSize() * 4; // FRAME_BYTES length

        return outputsize;
    }

    /**
     * Initiate the cipher
     */
    private void initAES() {
        // Init AES encryption
        try {
            crypto = new Crypto(session.getAESKEY(), session.getAESIV());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Decrypt array from input offset with a length of inputLen and puts it in
     * output at outputOffset
     *
     * @param array
     * @param inputOffset
     * @param inputLen
     * @param output
     * @param outputOffset
     * @return
     */
    private int decryptAES(byte[] array, int inputOffset, int inputLen, byte[] output, int outputOffset) {
        try {
            return crypto.decrypt(array, inputOffset, inputLen, output, outputOffset);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}
