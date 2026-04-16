package com.example.budg_v2.filter;

import com.example.budg_v2.util.CsrfTokenUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;

/**
 * Validates double-submit CSRF token for state-changing HTTP methods on API and admin routes.
 * Login endpoints are exempt (no CSRF cookie exists yet).
 */
public class CsrfFilter implements Filter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String method = req.getMethod();
        if (SAFE_METHODS.contains(method.toUpperCase())) {
            // Ensure authenticated clients receive an XSRF cookie for subsequent mutating requests
            if (hasAuthCookie(req) && CsrfTokenUtil.readCookie(req, CsrfTokenUtil.CSRF_COOKIE_NAME) == null) {
                CsrfTokenUtil.setCsrfCookie(req, resp, CsrfTokenUtil.newToken());
            }
            chain.doFilter(request, response);
            return;
        }

        String path = normalizedPath(req);
        if (isExempt(path, method)) {
            chain.doFilter(request, response);
            return;
        }

        String cookieToken = CsrfTokenUtil.readCookie(req, CsrfTokenUtil.CSRF_COOKIE_NAME);
        String headerToken = CsrfTokenUtil.readHeaderToken(req);
        if (cookieToken == null || headerToken == null || !constantTimeEquals(cookieToken, headerToken)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Invalid or missing CSRF token\",\"code\":\"CSRF_FAILED\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private static String normalizedPath(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        if (uri.isEmpty()) {
            return "/";
        }
        return uri;
    }

    /**
     * Login and public auth entry points must not require a prior CSRF cookie.
     */
    private static boolean isExempt(String path, String method) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        if ("/auth/login".equals(path) || "/login".equals(path)) {
            return true;
        }
        // Refresh uses HttpOnly refresh cookie; exempt so clients can rotate without a prior GET
        return "/auth/refresh".equals(path) || "/api/refresh".equals(path);
    }

    private static boolean hasAuthCookie(HttpServletRequest req) {
        if (req.getCookies() == null) {
            return false;
        }
        for (jakarta.servlet.http.Cookie c : req.getCookies()) {
            if ("ACCESS_TOKEN".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aa = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (aa.length != bb.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < aa.length; i++) {
            r |= aa[i] ^ bb[i];
        }
        return r == 0;
    }

    @Override
    public void destroy() {
        // no-op
    }
}
