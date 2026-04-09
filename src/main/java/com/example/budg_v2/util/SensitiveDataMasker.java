package com.example.budg_v2.util;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class to mask sensitive data in log messages and context.
 * Automatically detects and redacts passwords, tokens, API keys, and other sensitive information.
 */
public class SensitiveDataMasker {
    
    private static final String REDACTED_VALUE = "***REDACTED***";
    
    /**
     * Default patterns for sensitive field names (case-insensitive).
     */
    private static final Set<String> SENSITIVE_FIELD_PATTERNS = new HashSet<>(Arrays.asList(
        "password", "pwd", "passwd", "pass",
        "token", "access_token", "refresh_token", "api_token", "auth_token",
        "api_key", "apikey", "secret_key", "secret", "private_key",
        "ssn", "social_security_number", "social_security",
        "credit_card", "card_number", "cardnumber", "cvv", "cvc",
        "bank_account", "account_number", "routing_number"
    ));
    
    /**
     * Patterns to detect sensitive values in strings (even if field name is not sensitive).
     */
    private static final List<Pattern> SENSITIVE_VALUE_PATTERNS = Arrays.asList(
        // JWT tokens (eyJ...)
        Pattern.compile("eyJ[A-Za-z0-9_-]{20,}\\.eyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}"),
        // API keys (long alphanumeric strings)
        Pattern.compile("^[A-Za-z0-9]{32,}$"),
        // Credit card numbers (13-19 digits)
        Pattern.compile("\\b\\d{13,19}\\b"),
        // SSN (XXX-XX-XXXX)
        Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b")
    );
    
    /**
     * Mask sensitive data in a string value.
     * 
     * @param fieldName The name of the field (used to check if it's a sensitive field)
     * @param value The value to mask
     * @return Masked value if sensitive, original value otherwise
     */
    public static String maskValue(String fieldName, Object value) {
        if (value == null) {
            return null;
        }
        
        String strValue = value.toString();
        if (strValue.isEmpty()) {
            return strValue;
        }
        
        // Check if field name indicates sensitive data
        if (isSensitiveField(fieldName)) {
            return REDACTED_VALUE;
        }
        
        // Check if value itself looks sensitive
        if (isSensitiveValue(strValue)) {
            return REDACTED_VALUE;
        }
        
        return strValue;
    }
    
    /**
     * Mask sensitive data in a map (e.g., request parameters, context).
     * 
     * @param map The map to mask
     * @return New map with sensitive values masked
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> maskMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        
        Map<String, Object> masked = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                // Recursively mask nested maps
                masked.put(key, maskMap((Map<String, Object>) value));
            } else if (value instanceof List) {
                // Mask list items
                List<Object> maskedList = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        maskedList.add(maskMap((Map<String, Object>) item));
                    } else {
                        maskedList.add(maskValue(key, item));
                    }
                }
                masked.put(key, maskedList);
            } else {
                masked.put(key, maskValue(key, value));
            }
        }
        
        return masked;
    }
    
    /**
     * Mask sensitive data in a message string.
     * Attempts to detect and mask sensitive patterns in the message itself.
     * 
     * @param message The message to mask
     * @return Masked message
     */
    public static String maskMessage(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        
        String masked = message;
        
        // Replace sensitive patterns in message
        for (Pattern pattern : SENSITIVE_VALUE_PATTERNS) {
            masked = pattern.matcher(masked).replaceAll(REDACTED_VALUE);
        }
        
        // Mask common patterns like "password=xxx" or "token=xxx"
        masked = masked.replaceAll("(?i)(password|pwd|token|api[_-]?key|secret)\\s*[=:]\\s*[^\\s,}]+", 
            "$1=***REDACTED***");
        
        return masked;
    }
    
    /**
     * Check if a field name indicates sensitive data.
     * 
     * @param fieldName The field name to check
     * @return true if the field is sensitive
     */
    private static boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        return SENSITIVE_FIELD_PATTERNS.stream()
            .anyMatch(pattern -> lowerFieldName.contains(pattern.toLowerCase()));
    }
    
    /**
     * Check if a value looks like sensitive data.
     * 
     * @param value The value to check
     * @return true if the value looks sensitive
     */
    private static boolean isSensitiveValue(String value) {
        if (value == null || value.length() < 8) {
            // Too short to be sensitive
            return false;
        }
        
        // Check against sensitive value patterns
        return SENSITIVE_VALUE_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(value).find());
    }
    
    /**
     * Add a custom sensitive field pattern.
     * 
     * @param pattern The pattern to add
     */
    public static void addSensitiveFieldPattern(String pattern) {
        SENSITIVE_FIELD_PATTERNS.add(pattern.toLowerCase());
    }
}

