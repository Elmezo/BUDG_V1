package com.example.budg_v2.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

public class CorsUtil {

    private static final List<String> DEFAULT_METHODS = Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS");
    private static final List<String> DEFAULT_HEADERS = Arrays.asList(
            "Content-Type", "Accept", "Authorization",
            CsrfTokenUtil.CSRF_HEADER_PRIMARY, CsrfTokenUtil.CSRF_HEADER_ALT);

    /**
     * Set standard CORS headers with default methods and headers
     * Uses request origin if available, otherwise allows all origins
     */
    public static void setCorsHeaders(HttpServletResponse response) {
        setCorsHeaders(response, DEFAULT_METHODS, DEFAULT_HEADERS, "*", 3600);
    }
    
    /**
     * Set standard CORS headers using request origin
     */
    public static void setCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isEmpty() && AllowedOriginsUtil.isAllowedOrigin(origin)) {
            setCorsHeaders(response, DEFAULT_METHODS, DEFAULT_HEADERS, origin.trim(), 3600);
        } else {
            setCorsHeaders(response, DEFAULT_METHODS, DEFAULT_HEADERS, "*", 3600);
        }
    }

    /**
     * Set CORS headers with custom methods
     */
    public static void setCorsHeaders(HttpServletResponse response, List<String> methods) {
        setCorsHeaders(response, methods, DEFAULT_HEADERS, "*", 3600);
    }

    /**
     * Set full CORS headers with all custom options
     */
    public static void setCorsHeaders(HttpServletResponse response, List<String> methods, List<String> headers,
                                      String origin, int maxAgeSeconds) {
        // If origin is "*", we can't use credentials
        // But CORSFilter should have already set a specific origin, so we respect what's passed
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Methods", String.join(", ", methods));
        response.setHeader("Access-Control-Allow-Headers", String.join(", ", headers));
        response.setHeader("Access-Control-Max-Age", String.valueOf(maxAgeSeconds));
        // Only set credentials if origin is not "*"
        if (!"*".equals(origin)) {
            response.setHeader("Access-Control-Allow-Credentials", "true");
        }
    }

    /**
     * Handle OPTIONS preflight request
     */
    public static void handlePreflight(HttpServletResponse response) {
        setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
