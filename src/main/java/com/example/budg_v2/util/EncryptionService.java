package com.example.budg_v2.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encryption service for sensitive data using AES-256-GCM
 * 
 * Master Key Management (BUDG-style):
 * - Uses LDAP_ENCRYPTION_KEY if available
 * - Falls back to JWT_SECRET_KEY if LDAP_ENCRYPTION_KEY is not set
 */
public class EncryptionService {
    
    private static final Logger logger = LoggerFactory.getLogger(EncryptionService.class);
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits
    private static final int KEY_LENGTH = 256; // 256 bits
    
    private static final String MASKED_PASSWORD = "********";
    
    private static EncryptionService instance;
    private final SecretKey secretKey;
    
    private EncryptionService() {
        this.secretKey = initializeSecretKey();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized EncryptionService getInstance() {
        if (instance == null) {
            instance = new EncryptionService();
        }
        return instance;
    }
    
    /**
     * Initialize secret key from environment variables
     * Master Key Management (BUDG-style): LDAP_ENCRYPTION_KEY with fallback to JWT_SECRET_KEY
     */
    private SecretKey initializeSecretKey() {
        try {
            String masterKey = System.getenv("LDAP_ENCRYPTION_KEY");
            if (masterKey == null || masterKey.trim().isEmpty()) {
                masterKey = System.getenv("JWT_SECRET_KEY");
                if (masterKey == null || masterKey.trim().isEmpty()) {
                    logger.warn("Neither LDAP_ENCRYPTION_KEY nor JWT_SECRET_KEY found. Using generated key (not recommended for production)");
                    return generateKey();
                }
                logger.info("Using JWT_SECRET_KEY as encryption key (fallback)");
            } else {
                logger.info("Using LDAP_ENCRYPTION_KEY as encryption key");
            }
            
            // Derive a 256-bit key from the master key using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(masterKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, ALGORITHM);
            
        } catch (Exception e) {
                logger.error("Failed to initialize encryption key", e);
                // Fallback to generated key
                return generateKey();
            }
    }
    
    /**
     * Generate a random key (for development/testing only)
     */
    private SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            logger.error("Failed to generate encryption key", e);
            throw new RuntimeException("Failed to initialize encryption service", e);
        }
    }
    
    /**
     * Encrypt plaintext string
     * 
     * @param plaintext The plaintext to encrypt
     * @return Base64-encoded encrypted string (IV + ciphertext + tag)
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            
            // Initialize cipher for encryption
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);
            
            // Return Base64-encoded result
            return Base64.getEncoder().encodeToString(byteBuffer.array());
            
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypt encrypted string
     * 
     * @param encrypted The Base64-encoded encrypted string
     * @return Decrypted plaintext
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return null;
        }
        
        try {
            // Decode Base64
            byte[] encryptedBytes;
            try {
                encryptedBytes = Base64.getDecoder().decode(encrypted);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid Base64 encoded data", e);
                throw new RuntimeException("Invalid encrypted data format (not valid Base64)", e);
            }
            
            // Check minimum size: IV (12 bytes) + tag (16 bytes) = 28 bytes minimum
            if (encryptedBytes.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
                logger.error("Encrypted data too short: {} bytes (minimum {} bytes required)", 
                    encryptedBytes.length, GCM_IV_LENGTH + GCM_TAG_LENGTH);
                throw new RuntimeException("Encrypted data is corrupted or incomplete");
            }
            
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
            
            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            
            // Extract ciphertext (includes tag)
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);
            
            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);
            
            return new String(plaintext, StandardCharsets.UTF_8);
            
        } catch (java.nio.BufferUnderflowException e) {
            // Data is corrupted - log at debug level to avoid noise in logs
            logger.debug("Decryption failed: encrypted data is corrupted or incomplete (BufferUnderflowException)", e);
            throw new RuntimeException("Encrypted data is corrupted or incomplete. Please re-enter the password.", e);
        } catch (javax.crypto.AEADBadTagException e) {
            // Key mismatch is expected when encryption key changes - log at debug level to avoid noise
            logger.debug("Decryption failed: encryption key mismatch (AEADBadTagException). This is expected when the encryption key has changed.", e);
            throw new RuntimeException("Encryption key mismatch. The data was encrypted with a different key.", e);
        } catch (Exception e) {
            // Other decryption errors - log at debug level
            logger.debug("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt data: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get masked password for API responses
     * 
     * @param hasPassword Whether password exists
     * @return "********" if password exists, null otherwise
     */
    public static String getMaskedPassword(boolean hasPassword) {
        return hasPassword ? MASKED_PASSWORD : null;
    }
}

