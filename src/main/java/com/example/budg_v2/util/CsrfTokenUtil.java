package com.example.budg_v2.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Double-submit cookie CSRF protection: XSRF-TOKEN (non-HttpOnly) must match
 * X-XSRF-TOKEN or X-CSRF-Token on state-changing requests.
 */
public final class CsrfTokenUtil {

    public static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String CSRF_HEADER_PRIMARY = "X-XSRF-TOKEN";
    public static final String CSRF_HEADER_ALT = "X-CSRF-Token";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfTokenUtil() {
    }

    public static String newToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * Non-HttpOnly cookie so SPA can read the token and echo it in a header.
     */
    public static void setCsrfCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        boolean secure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        Cookie c = new Cookie(CSRF_COOKIE_NAME, token);
        c.setPath("/");
        c.setMaxAge(-1); // session
        c.setHttpOnly(false);
        c.setSecure(secure);
        // Servlet API 6 / Tomcat 10+: setAttribute for SameSite
        try {
            c.setAttribute("SameSite", "Lax");
        } catch (Exception ignored) {
            // older containers: omit SameSite on Cookie object
        }
        response.addCookie(c);
    }

    public static String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    public static String readHeaderToken(HttpServletRequest request) {
        String h = request.getHeader(CSRF_HEADER_PRIMARY);
        if (h != null && !h.isBlank()) {
            return h.trim();
        }
        h = request.getHeader(CSRF_HEADER_ALT);
        if (h != null && !h.isBlank()) {
            return h.trim();
        }
        return null;
    }
}
