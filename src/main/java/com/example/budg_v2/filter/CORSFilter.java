package com.example.budg_v2.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CORSFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(CORSFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Initializing CORSFilter...");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Enhanced CORS security
        String origin = httpRequest.getHeader("Origin");
        if (isAllowedOrigin(origin)) {
            httpResponse.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            httpResponse.setHeader("Access-Control-Allow-Origin", "null");
        }
        
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        httpResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept, Authorization, X-Requested-With, X-CSRF-Token");
        httpResponse.setHeader("Access-Control-Max-Age", "3600");
        httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
        httpResponse.setHeader("Access-Control-Expose-Headers", "X-CSRF-Token");

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            logger.debug("Preflight OPTIONS request handled successfully.");
            return;
        }

        logger.debug("Processing request: {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        logger.info("Destroying CORSFilter...");
    }
    
    /**
     * Check if origin is allowed (for production, restrict to specific domains)
     */
    private boolean isAllowedOrigin(String origin) {
        if (origin == null) {
            return false;
        }
        
        // For development, allow localhost and local IPs
        if (origin.startsWith("http://localhost") || 
            origin.startsWith("https://localhost") ||
            origin.startsWith("http://127.0.0.1") ||
            origin.startsWith("https://127.0.0.1")) {
            return true;
        }
        
        // For production, add your domain here
        // return origin.equals("https://yourdomain.com");
        
        // For now, allow all origins (change in production)
        return true;
    }
}
