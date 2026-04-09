package com.example.budg_v2.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import com.example.budg_v2.util.BudgLogger;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;

/**
 * First filter in the chain. Sets up the per-request MDC context used by every
 * subsequent filter, servlet, and service log call:
 *
 * <ul>
 *   <li>{@code request_id} / {@code correlation_id} — UUID trace ID</li>
 *   <li>{@code client_ip}   — real client IP (honours {@code X-Forwarded-For})</li>
 *   <li>{@code user_agent}  — browser / API client identifier</li>
 *   <li>{@code duration_ms} — total request time (set in finally block)</li>
 *   <li>{@code environment} — production / staging / development</li>
 *   <li>{@code source}      — server hostname</li>
 * </ul>
 *
 * <p>MDC is always cleared in the {@code finally} block to prevent
 * memory leaks and cross-request contamination across thread-pool threads.
 */
@WebFilter(urlPatterns = "/*")
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_KEY = "correlation_id";
    private static final String REQUEST_ID_KEY     = "request_id";
    private static final String DURATION_MS_KEY    = "duration_ms";
    private static final String CLIENT_IP_KEY      = "client_ip";
    private static final String USER_AGENT_KEY     = "user_agent";
    private static final String ENVIRONMENT_KEY    = "environment";
    private static final String SOURCE_KEY         = "source";

    /** Requests slower than this threshold trigger a WARN log entry. */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 5_000L;
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization not needed
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        long   requestStartMs = System.currentTimeMillis();
        String requestUri     = null;

        try {
            // Single UUID — both correlation_id (internal) and request_id (user-facing)
            String requestId = UUID.randomUUID().toString();
            MDC.put(CORRELATION_ID_KEY, requestId);
            MDC.put(REQUEST_ID_KEY,     requestId);

            // Security context — client IP + browser / API client identity
            if (request instanceof HttpServletRequest httpReq) {
                requestUri = httpReq.getRequestURI();

                // Honour reverse-proxy header; fall back to socket address
                String xff      = httpReq.getHeader("X-Forwarded-For");
                String clientIp = (xff != null && !xff.isBlank())
                        ? xff.split(",")[0].trim()
                        : request.getRemoteAddr();
                MDC.put(CLIENT_IP_KEY, clientIp);

                String ua = httpReq.getHeader("User-Agent");
                MDC.put(USER_AGENT_KEY, ua != null ? ua : "unknown");
            }

            // Environment tag
            String environment = detectEnvironment();
            if (environment != null && MDC.get(ENVIRONMENT_KEY) == null) {
                MDC.put(ENVIRONMENT_KEY, environment);
            }

            // Server identity
            String hostname = getHostname();
            if (hostname != null) {
                MDC.put(SOURCE_KEY, hostname);
            }

            chain.doFilter(request, response);

        } finally {
            long duration = System.currentTimeMillis() - requestStartMs;
            MDC.put(DURATION_MS_KEY, String.valueOf(duration));

            // Slow-request detection — log a warning so monitoring tools can alert
            if (duration > SLOW_REQUEST_THRESHOLD_MS) {
                String userId = MDC.get("user_id");
                BudgLogger.logWarn(
                    "TOMCAT",
                    "REQUEST",
                    userId != null ? userId : "unknown",
                    "Slow request: " + duration + "ms — " + requestUri
                );
            }

            // Always clear MDC — prevents memory leaks on pooled threads
            MDC.clear();
        }
    }
    
    /**
     * Detect environment from system properties or hostname.
     * Priority:
     * 1. System property "app.environment"
     * 2. MDC key "environment" (if already set)
     * 3. Hostname-based detection
     * 
     * @return Environment name (production, development, staging, test) or null
     */
    private String detectEnvironment() {
        // Priority 1: System property
        String env = System.getProperty("app.environment");
        if (env != null && !env.trim().isEmpty()) {
            return normalizeEnvironment(env.trim());
        }
        
        // Priority 2: MDC (if already set by another component)
        env = MDC.get(ENVIRONMENT_KEY);
        if (env != null && !env.trim().isEmpty()) {
            return normalizeEnvironment(env.trim());
        }
        
        // Priority 3: Hostname-based detection
        try {
            String hostname = getHostname();
            if (hostname != null) {
                String lowerHostname = hostname.toLowerCase();
                if (lowerHostname.contains("localhost") || 
                    lowerHostname.contains("127.0.0.1") ||
                    lowerHostname.startsWith("dev") ||
                    lowerHostname.startsWith("local")) {
                    return "development";
                } else if (lowerHostname.contains("staging") || lowerHostname.contains("stage")) {
                    return "staging";
                } else if (lowerHostname.contains("test")) {
                    return "test";
                } else {
                    return "production";
                }
            }
        } catch (Exception e) {
            // Ignore errors in environment detection
        }
        
        // Default fallback
        return "development";
    }
    
    /**
     * Normalize environment name to valid values.
     * 
     * @param env Environment name
     * @return Normalized environment name
     */
    private String normalizeEnvironment(String env) {
        if (env == null) {
            return "development";
        }
        
        String normalized = env.toLowerCase().trim();
        if (normalized.equals("prod") || normalized.equals("production")) {
            return "production";
        } else if (normalized.equals("dev") || normalized.equals("development")) {
            return "development";
        } else if (normalized.equals("staging") || normalized.equals("stage")) {
            return "staging";
        } else if (normalized.equals("test") || normalized.equals("testing")) {
            return "test";
        }
        
        return normalized; // Return as-is if not recognized
    }
    
    /**
     * Get server hostname.
     * 
     * @return Hostname or null if unavailable
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    @Override
    public void destroy() {
        // Cleanup not needed
    }
}

