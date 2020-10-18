/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.airplay;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;


/**
 * AppleCrypto.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/16 umjammer initial version <br>
 */
public class Crypto {

    private static Key key;
    private Cipher encryptionCipher;
    private Cipher decryptionCipher;

    static {
        try {
            // The RSA key
            DataInputStream is = new DataInputStream(RtspHandler.class.getResourceAsStream("/key.pk8"));
            byte[] buf = new byte[is.available()];
            is.readFully(buf);
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(buf));
        } catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Crypto() throws NoSuchAlgorithmException, NoSuchPaddingException {
        // bc: "RSA/NONE/PKCS1Padding"
        encryptionCipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING");
        // bc: "RSA/NONE/OAEPPadding"
        decryptionCipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA1ANDMGF1PADDING");
    }

    /** */
    public String getChallengeResponce(String challenge, byte[] ip, byte[] hwAddr) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        // BASE64 DECODE
        byte[] decoded = Base64.getDecoder().decode(challenge);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Challenge
        try {
            out.write(decoded);
            // IP-Address
            out.write(ip);
            // HW-Addr
            out.write(hwAddr);

            // Pad to 32 Bytes
            int padLen = 32 - out.size();
            for (int i = 0; i < padLen; ++i) {
                out.write(0x00);
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // RSA
        byte[] crypted = encrypt(out.toByteArray());

        // Encode64
        String ret = Base64.getEncoder().encodeToString(crypted);

        // On retire les ==
        return ret.replace("=", "").replace("\r", "").replace("\n", "");
    }

    /* */
    public byte[] encrypt(byte[] array) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        encryptionCipher.init(Cipher.ENCRYPT_MODE, key);
        return encryptionCipher.doFinal(array);
    }

    /** */
    public byte[] decrypt(byte[] array) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        decryptionCipher.init(Cipher.DECRYPT_MODE, key);
        return decryptionCipher.doFinal(array);
    }
}

/* */
