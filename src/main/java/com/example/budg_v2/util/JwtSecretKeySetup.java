package com.example.budg_v2.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class to generate and add JWT_SECRET_KEY to .env file
 * Can be run as a standalone program or called from other code
 */
public class JwtSecretKeySetup {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtSecretKeySetup.class);
    
    /**
     * Generate a secure random JWT secret key
     * @param lengthBytes Length in bytes (default 64 bytes = 512 bits recommended)
     * @return Base64-encoded secret key
     */
    public static String generateSecretKey(int lengthBytes) {
        SecureRandom random = new SecureRandom();
        byte[] keyBytes = new byte[lengthBytes];
        random.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
    
    /**
     * Generate and add JWT_SECRET_KEY to .env file
     * @param keyLengthBytes Length of key in bytes (default 64 = 512 bits)
     * @return true if successful, false otherwise
     */
    public static boolean setupJwtSecretKey(int keyLengthBytes) {
        logger.info("Generating secure JWT secret key ({} bytes)...", keyLengthBytes);
        String secretKey = generateSecretKey(keyLengthBytes);
        
        logger.info("Adding JWT_SECRET_KEY to .env file...");
        boolean success = EnvFileUpdater.updateEnvVariable("JWT_SECRET_KEY", secretKey);
        
        if (success) {
            logger.info("✓ JWT_SECRET_KEY successfully added to .env file");
            logger.info("Note: Keep this key secret and never commit it to version control");
        } else {
            logger.error("✗ Failed to add JWT_SECRET_KEY to .env file");
            logger.error("Please ensure .env file exists and is writable");
        }
        
        return success;
    }
    
    /**
     * Generate and add JWT_SECRET_KEY to .env file with default length (64 bytes)
     * @return true if successful, false otherwise
     */
    public static boolean setupJwtSecretKey() {
        return setupJwtSecretKey(64); // 64 bytes = 512 bits (recommended)
    }
    
    /**
     * Main method to run as standalone program
     * Usage: java -cp ... com.example.budg_v2.util.JwtSecretKeySetup
     */
    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("BUDG Platform - JWT Secret Key Setup");
        System.out.println("=========================================");
        System.out.println();
        
        int keyLength = 64; // Default: 64 bytes = 512 bits
        if (args.length > 0) {
            try {
                keyLength = Integer.parseInt(args[0]);
                if (keyLength < 32) {
                    System.err.println("Warning: Key length should be at least 32 bytes (256 bits) for security");
                    System.err.println("Using minimum length of 32 bytes");
                    keyLength = 32;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid key length argument: " + args[0]);
                System.err.println("Using default length: 64 bytes");
            }
        }
        
        boolean success = setupJwtSecretKey(keyLength);
        
        if (success) {
            System.out.println();
            System.out.println("Setup completed successfully!");
            System.out.println("You may need to restart your application server for the changes to take effect.");
            System.exit(0);
        } else {
            System.err.println();
            System.err.println("Setup failed!");
            System.err.println("Please check the logs above for details.");
            System.exit(1);
        }
    }
}

