package com.example.budg_v2.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Filter to add security headers to all responses
 */
public class SecurityHeadersFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityHeadersFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Initializing SecurityHeadersFilter...");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Add security headers
        addSecurityHeaders(httpRequest, httpResponse);

        // Log security events
        logSecurityEvent(httpRequest);

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        logger.info("Destroying SecurityHeadersFilter...");
    }

    /**
     * Add security headers to response
     */
    private void addSecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
        // Prevent clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // Prevent MIME type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Enable XSS protection
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Strict Transport Security (HTTPS only)
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // Content Security Policy
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; "
                        +
                        "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net https://fonts.googleapis.com; "
                        +
                        "img-src 'self' data: blob:; " +
                        "font-src 'self' data: https://fonts.gstatic.com https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; "
                        +
                        "connect-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; " +
                        "frame-ancestors 'none'; " +
                        "base-uri 'self'; " +
                        "form-action 'self'");

        // Referrer Policy
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions Policy
        response.setHeader("Permissions-Policy",
                "geolocation=(), " +
                        "microphone=(), " +
                        "camera=(), " +
                        "payment=(), " +
                        "usb=(), " +
                        "magnetometer=(), " +
                        "gyroscope=()");

        // Remove server information
        response.setHeader("Server", "");

        // Cache control for sensitive pages
        if (isSensitivePage(request)) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }
    }

    /**
     * Log security events
     */
    private void logSecurityEvent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");

        // Log suspicious activity
        if (isSuspiciousUserAgent(userAgent)) {
            logger.warn("Suspicious User-Agent detected: {} from IP: {}",
                    userAgent, getClientIp(request));
        }

        if (isSuspiciousReferer(referer)) {
            logger.warn("Suspicious Referer detected: {} from IP: {}",
                    referer, getClientIp(request));
        }
    }

    /**
     * Check if page contains sensitive information
     */
    private boolean isSensitivePage(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.contains("/login") ||
                uri.contains("/api/me") ||
                uri.contains("/admin") ||
                uri.contains("/profile");
    }

    /**
     * Check for suspicious User-Agent
     */
    private boolean isSuspiciousUserAgent(String userAgent) {
        if (userAgent == null)
            return true;

        String lowerUA = userAgent.toLowerCase();
        return lowerUA.contains("bot") ||
                lowerUA.contains("crawler") ||
                lowerUA.contains("scanner") ||
                lowerUA.contains("sqlmap") ||
                lowerUA.contains("nikto") ||
                lowerUA.contains("nmap");
    }

    /**
     * Check for suspicious Referer
     */
    private boolean isSuspiciousReferer(String referer) {
        if (referer == null)
            return false;

        String lowerReferer = referer.toLowerCase();
        return lowerReferer.contains("malicious") ||
                lowerReferer.contains("phishing") ||
                lowerReferer.contains("hack");
    }

    /**
     * Get client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
