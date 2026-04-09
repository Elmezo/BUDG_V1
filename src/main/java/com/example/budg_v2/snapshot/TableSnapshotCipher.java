package com.example.budg_v2.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Wraps the inner ZIP snapshot in AES-256-GCM so extracting the downloaded file with normal tools
 * does not reveal JSON/JSONL contents (binary ciphertext only).
 * <p>
 * Key: {@link #EMBEDDED_TABLE_SNAPSHOT_SECRET} is stretched with PBKDF2-HMAC-SHA256 (fixed salt, 65536 iterations)
 * to produce the AES-256 key (change secret and/or salt for your deployment).
 */
public final class TableSnapshotCipher {

    private static final Logger logger = LoggerFactory.getLogger(TableSnapshotCipher.class);

    /**
     * Embedded material for snapshot encryption. Replace with your own long random value before production.
     */
    private static final String EMBEDDED_TABLE_SNAPSHOT_SECRET =
            "BUDG_TABLE_SNAPSHOT_V1_EMBEDDED_KEY_CHANGE_THIS_TO_A_LONG_RANDOM_SECRET_AT_LEAST_48_CHARS";

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;

    private static final int PBKDF2_ITERATIONS = 65_536;
    /** Fixed application salt so export and import derive the same key; change with the secret if you rotate crypto. */
    private static final byte[] PBKDF2_SALT =
            "BUDG_TABLE_SNAPSHOT_PBKDF2_SALT_V1_CHANGE_IF_ROTATING".getBytes(StandardCharsets.UTF_8);

    /** File prefix: "BDG2" + version byte 1 */
    private static final byte[] MAGIC = {'B', 'D', 'G', '2'};
    private static final int VERSION = 1;
    private static final int HEADER_LENGTH = MAGIC.length + 1 + IV_LENGTH;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final SecretKey SNAPSHOT_AES_KEY = deriveAesKeyPbkdf2(EMBEDDED_TABLE_SNAPSHOT_SECRET);

    private TableSnapshotCipher() {
    }

    public static SecretKey resolveKey() {
        return SNAPSHOT_AES_KEY;
    }

    private static SecretKey deriveAesKeyPbkdf2(String raw) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), PBKDF2_SALT, PBKDF2_ITERATIONS, 256);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static boolean looksEncrypted(byte[] filePrefix) {
        if (filePrefix == null || filePrefix.length < MAGIC.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(filePrefix, 0, MAGIC.length), MAGIC);
    }

    /**
     * Writes magic, version, IV, then encrypts everything written to the returned stream (the inner ZIP).
     */
    public static CipherOutputStream encryptingZipSink(OutputStream responseOut, SecretKey key) throws Exception {
        responseOut.write(MAGIC);
        responseOut.write(VERSION);
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        responseOut.write(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new CipherOutputStream(responseOut, cipher);
    }

    /**
     * If payload starts with {@link #MAGIC}, decrypts the remainder after the header; otherwise returns payload as-is (legacy plain ZIP).
     */
    public static byte[] unwrapToZipBytes(byte[] payload, SecretKey key) throws Exception {
        if (payload == null || payload.length < HEADER_LENGTH) {
            return payload;
        }
        if (!looksEncrypted(payload)) {
            return payload;
        }
        int ver = payload[MAGIC.length] & 0xff;
        if (ver != VERSION) {
            throw new IOException("Unsupported encrypted snapshot format version: " + ver);
        }
        byte[] iv = Arrays.copyOfRange(payload, MAGIC.length + 1, HEADER_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(payload, HEADER_LENGTH, payload.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plain = cipher.doFinal(ciphertext);
        logger.debug("Decrypted table snapshot wrapper: {} bytes -> {} bytes ZIP", payload.length, plain.length);
        return plain;
    }
}
