package com.example.budg_v2.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parses {@code ALLOWED_ORIGINS} (comma-separated) and matches request {@code Origin} headers.
 * Falls back to localhost origins for development when the variable is unset or empty.
 */
public final class AllowedOriginsUtil {

    private static final String ENV_KEY = "ALLOWED_ORIGINS";

    private AllowedOriginsUtil() {
    }

    private static String resolveEnv(String key) {
        String v = System.getenv(key);
        if (v != null && !v.trim().isEmpty()) {
            return v.trim();
        }
        v = System.getProperty(key);
        if (v != null && !v.trim().isEmpty()) {
            return v.trim();
        }
        return null;
    }

    /**
     * Explicit allow-list from {@code ALLOWED_ORIGINS}; empty if unset (caller may add dev defaults).
     */
    public static List<String> configuredOrigins() {
        String raw = resolveEnv(ENV_KEY);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String o = part.trim();
            if (!o.isEmpty()) {
                out.add(o);
            }
        }
        return out;
    }

    /**
     * Origins allowed for CORS: configured list, or localhost/127.0.0.1 http(s) when nothing configured.
     */
    public static List<String> effectiveAllowedOrigins() {
        List<String> configured = configuredOrigins();
        if (!configured.isEmpty()) {
            return configured;
        }
        return List.of(
                "http://localhost:8080",
                "https://localhost:8080",
                "http://127.0.0.1:8080",
                "https://127.0.0.1:8080",
                "http://localhost",
                "https://localhost",
                "http://127.0.0.1",
                "https://127.0.0.1"
        );
    }

    public static boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        String normalized = origin.trim();
        for (String allowed : effectiveAllowedOrigins()) {
            if (normalized.equals(allowed)) {
                return true;
            }
        }
        // Optional: allow any localhost port when using dev fallback (no ALLOWED_ORIGINS set)
        if (configuredOrigins().isEmpty()) {
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (lower.startsWith("http://localhost:") || lower.startsWith("https://localhost:")
                    || lower.startsWith("http://127.0.0.1:") || lower.startsWith("https://127.0.0.1:")) {
                return true;
            }
        }
        return false;
    }
}
