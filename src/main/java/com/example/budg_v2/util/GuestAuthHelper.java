package com.example.budg_v2.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Helper for issuing guest sessions/tokens so unauthenticated users can
 * access public GET endpoints without hitting auth errors.
 */
public class GuestAuthHelper {

    private static final Logger logger = LoggerFactory.getLogger(GuestAuthHelper.class);

    // Guest identity constants
    private static final int GUEST_USER_ID = -1;
    private static final String GUEST_EMAIL = "guest@budg.local";
    private static final String GUEST_FIRST = "Guest";
    private static final String GUEST_LAST = "User";
    private static final String GUEST_ROLE = "guest";
    private static final String GUEST_AVATAR = "";
    private static final String ACCESS_COOKIE = "ACCESS_TOKEN";
    private static final String REFRESH_COOKIE = "REFRESH_TOKEN";

    public static class GuestAuthResult {
        public final String accessToken;
        public final String refreshToken;
        public final String sessionId;
        public GuestAuthResult(String accessToken, String refreshToken, String sessionId) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.sessionId = sessionId;
        }
    }

    /**
    * Issue guest tokens and set cookies. Also attaches user attributes to the request.
    */
    public static GuestAuthResult issueGuestTokens(HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthConfigUtil.AuthConfig cfg = AuthConfigUtil.getConfig();
            String clientIp = getClientIpAddress(request);

            // Use an in-memory guest session ID (no DB persistence to avoid FK issues)
            String sessionId = UUID.randomUUID().toString().replace("-", "");

            // Generate tokens
            String accessToken = JwtUtil.generateAccessToken(
                GUEST_USER_ID,
                GUEST_EMAIL,
                GUEST_FIRST,
                GUEST_LAST,
                GUEST_ROLE,
                GUEST_AVATAR,
                sessionId,
                cfg.jwtValiditySeconds
            );
            String refreshToken = JwtUtil.generateRefreshToken(
                GUEST_USER_ID,
                GUEST_EMAIL,
                GUEST_FIRST,
                GUEST_LAST,
                GUEST_ROLE,
                GUEST_AVATAR,
                sessionId,
                cfg.refreshValiditySeconds
            );

            boolean isHttps = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
            addCookie(response, ACCESS_COOKIE, accessToken, isHttps, (int) cfg.jwtValiditySeconds);
            addCookie(response, REFRESH_COOKIE, refreshToken, isHttps, (int) cfg.refreshValiditySeconds);

            attachGuestAttributes(request, sessionId);
            logger.info("Issued guest tokens for IP {}", clientIp);
            return new GuestAuthResult(accessToken, refreshToken, sessionId);
        } catch (Exception e) {
            logger.error("Failed to issue guest tokens", e);
            return null;
        }
    }

    private static void addCookie(HttpServletResponse response, String name, String value, boolean isHttps, int maxAgeSeconds) {
        // Use CookieUtil to support SameSite attribute
        // ACCESS_TOKEN uses Strict, REFRESH_TOKEN uses Lax
        String sameSite = ACCESS_COOKIE.equals(name) ? "Strict" : "Lax";
        CookieUtil.addCookie(response, name, value, maxAgeSeconds, isHttps, sameSite);
    }

    private static void attachGuestAttributes(HttpServletRequest request, String sessionId) {
        request.setAttribute("userEmail", GUEST_EMAIL);
        request.setAttribute("userName", (GUEST_FIRST + " " + GUEST_LAST).trim());
        request.setAttribute("userRole", GUEST_ROLE);
        request.setAttribute("userAvatar", GUEST_AVATAR);
        request.setAttribute("userId", GUEST_USER_ID);
        request.setAttribute("sessionId", sessionId);
    }

    private static String getClientIpAddress(HttpServletRequest request) {
        String[] headers = { "X-Forwarded-For", "X-Real-IP" };
        for (String h : headers) {
            String value = request.getHeader(h);
            if (value != null && !value.isEmpty()) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    public static boolean isGuestRole(String role) {
        if (role == null) return false;
        String norm = role.trim().toLowerCase();
        return "guest".equals(norm);
    }
}

