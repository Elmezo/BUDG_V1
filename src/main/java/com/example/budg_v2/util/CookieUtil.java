package com.example.budg_v2.util;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for handling cookies with SameSite attribute support.
 * Jakarta Servlet API doesn't directly support SameSite, so we need to
 * manually construct the Set-Cookie header.
 */
public class CookieUtil {
    
    /**
     * Add a cookie with SameSite attribute to the response.
     * 
     * @param response The HTTP response
     * @param name Cookie name
     * @param value Cookie value
     * @param maxAge Max age in seconds (0 to delete)
     * @param path Cookie path
     * @param secure Whether cookie is secure (HTTPS only)
     * @param httpOnly Whether cookie is HTTP-only
     * @param sameSite SameSite value ("Strict", "Lax", "None", or null)
     */
    public static void addCookie(HttpServletResponse response, String name, String value,
                                 int maxAge, String path, boolean secure, boolean httpOnly,
                                 String sameSite) {
        // Build the Set-Cookie header manually
        StringBuilder cookieHeader = new StringBuilder();
        
        // Name and value
        cookieHeader.append(name).append("=");
        if (value != null && !value.isEmpty()) {
            cookieHeader.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        
        // Max-Age
        if (maxAge >= 0) {
            cookieHeader.append("; Max-Age=").append(maxAge);
        }
        
        // Path
        if (path != null && !path.isEmpty()) {
            cookieHeader.append("; Path=").append(path);
        }
        
        // Secure
        if (secure) {
            cookieHeader.append("; Secure");
        }
        
        // HttpOnly
        if (httpOnly) {
            cookieHeader.append("; HttpOnly");
        }
        
        // SameSite
        if (sameSite != null && !sameSite.isEmpty()) {
            cookieHeader.append("; SameSite=").append(sameSite);
        }
        
        // Add the header to response
        response.addHeader("Set-Cookie", cookieHeader.toString());
    }
    
    /**
     * Convenience method to add a cookie with common defaults.
     */
    public static void addCookie(HttpServletResponse response, String name, String value,
                                 int maxAge, boolean isHttps, String sameSite) {
        addCookie(response, name, value, maxAge, "/", isHttps, true, sameSite);
    }
}

