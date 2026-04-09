package com.example.budg_v2.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT utility class for token generation and validation.
 * 
 * SECURITY NOTE: The JWT secret key is stored in memory as byte[].
 * While Java doesn't provide perfect memory protection, we take precautions:
 * - Never log the secret key
 * - Convert String to byte[] immediately and clear reference
 * - Use SecureRandom for any key generation
 * - Keep key in static final to minimize copies
 * 
 * For production, consider:
 * - Using hardware security modules (HSM)
 * - Key rotation mechanisms
 * - Memory encryption (OS-level)
 */
public class JwtUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private static final String ISSUER = "BUDG-Platform";
    private static volatile byte[] SECRET_BYTES = null;
    
    /**
     * Get the secret key bytes, initializing if necessary.
     * Uses lazy initialization to allow ApplicationInitializer to set the key first.
     */
    private static byte[] getSecretBytes() {
        if (SECRET_BYTES == null) {
            synchronized (JwtUtil.class) {
                if (SECRET_BYTES == null) {
                    SECRET_BYTES = getSecretKeyBytes();
                }
            }
        }
        return SECRET_BYTES;
    }
    
    /**
     * Get JWT secret key bytes from environment variable or system property.
     * Checks System.getenv() first, then System.getProperty() as fallback.
     * SECURITY: Attempts to minimize memory exposure by:
     * - Converting String to byte[] immediately
     * - Not storing the String reference
     * - Never logging the actual key value
     * 
     * @return Secret key as byte array
     */
    private static byte[] getSecretKeyBytes() {
        String envSecret = System.getenv("JWT_SECRET_KEY");
        boolean usingDevKey = false;
        
        // Fallback to system property if environment variable not found
        if (envSecret == null || envSecret.trim().isEmpty()) {
            envSecret = System.getProperty("JWT_SECRET_KEY");
        }
        
        if (envSecret == null || envSecret.trim().isEmpty()) {
            String errorMsg = 
                "JWT_SECRET_KEY is not configured! " +
                "The application should have failed to start if this error occurs. " +
                "Please ensure JWT_SECRET_KEY is set in .env file or as environment variable.";
            logger.error(errorMsg);
            throw new IllegalStateException("JWT_SECRET_KEY is required but not found. Application should not have started without it.");
        }
        
        // Convert to byte[] immediately to minimize String lifetime
        byte[] key = envSecret.getBytes(StandardCharsets.UTF_8);
        
        // Clear the String reference (though GC may not clear it immediately)
        envSecret = null;
        
        if (key.length < 32) {
            // Clear key array before throwing exception
            Arrays.fill(key, (byte) 0);
            throw new IllegalStateException("JWT_SECRET_KEY must be at least 256 bits (32 bytes)");
        }
        
        // Log only that key was loaded (not the actual value)
        if (!usingDevKey) {
            logger.info("JWT secret key loaded successfully (length: {} bytes)", key.length);
        }
        
        return key;
    }

    // New API per requirements
    public static String generateAccessToken(int userId, String email, String firstName, String lastName,
                                             String role, String avatarPath, String sessionId, long validitySeconds) {
        return generateToken(userId, email, firstName, lastName, role, avatarPath, sessionId, validitySeconds, "ACCESS");
    }

    public static String generateRefreshToken(int userId, String email, String firstName, String lastName,
                                              String role, String avatarPath, String sessionId, long validitySeconds) {
        return generateToken(userId, email, firstName, lastName, role, avatarPath, sessionId, validitySeconds, "REFRESH");
    }

    public static JWTClaimsSet parseAndValidate(String token) throws ParseException, JOSEException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        boolean verified = signedJWT.verify(new MACVerifier(getSecretBytes()));
        if (!verified) {
            throw new JOSEException("Invalid JWT signature");
        }
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        Date exp = claims.getExpirationTime();
        if (exp == null || exp.before(new Date())) {
            throw new JOSEException("JWT expired");
        }
        return claims;
    }

    private static String generateToken(int userId, String email, String firstName, String lastName,
                                        String role, String avatarPath, String sessionId,
                                        long validitySeconds, String type) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(Math.max(1, validitySeconds));

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(userId))
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .claim("sub", userId)
                .claim("email", email)
                .claim("firstName", firstName)
                .claim("lastName", lastName)
                .claim("role", role)
                .claim("avatarPath", avatarPath)
                .claim("sessionId", sessionId)
                .claim("type", type)
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            signedJWT.sign(new MACSigner(getSecretBytes()));
        } catch (JOSEException e) {
            // SECURITY: Never log the secret key in exception messages
            throw new RuntimeException("Failed to sign JWT", e);
        }
        return signedJWT.serialize();
    }

    // Backward-compat methods to keep current code compiling during refactor
    public static String generateToken(int userId, String firstName, String lastName, String role, String avatarPath) {
        // Temporary: 2h validity, no sessionId/email
        return generateToken(userId, null, firstName, lastName, role, avatarPath, null, 2 * 60 * 60, "ACCESS");
    }

    public static Map<String, Object> validateToken(String token) {
        try {
            JWTClaimsSet claims = parseAndValidate(token);
            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", parseIntClaim(claims.getClaim("sub"), claims.getSubject()));
            userData.put("email", claims.getStringClaim("email"));
            userData.put("firstName", claims.getStringClaim("firstName"));
            userData.put("lastName", claims.getStringClaim("lastName"));
            userData.put("role", claims.getStringClaim("role"));
            userData.put("avatarPath", claims.getStringClaim("avatarPath"));
            userData.put("sessionId", claims.getStringClaim("sessionId"));
            userData.put("jti", claims.getJWTID());
            userData.put("exp", claims.getExpirationTime());
            return userData;
        } catch (Exception e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }
    
    public static boolean isTokenExpired(String token) {
        try {
            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            return exp == null || exp.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
    
    public static int getUserIdFromToken(String token) {
        try {
            JWTClaimsSet claims = parseAndValidate(token);
            return parseIntClaim(claims.getClaim("sub"), claims.getSubject());
        } catch (Exception e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    private static int parseIntClaim(Object subClaim, String fallback) {
        if (subClaim instanceof Number) {
            return ((Number) subClaim).intValue();
        }
        if (subClaim instanceof String) {
            try { return Integer.parseInt((String) subClaim); } catch (NumberFormatException ignored) {}
        }
        if (fallback != null) {
            try { return Integer.parseInt(fallback); } catch (NumberFormatException ignored) {}
        }
        throw new IllegalArgumentException("Invalid subject claim");
    }
}
