package com.example.budg_v2.filter;

import com.example.budg_v2.util.JwtUtil;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Guards access to HTML pages. Allows public pages and blocks all other HTML
 * unless the user is authenticated. Non-HTML assets are not affected.
 */
@WebFilter("*.html")
public class PageAccessFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();

        // Always allow the permission page itself to avoid loops
        if (uri != null && (uri.startsWith("/error/permission.html") || uri.contains("/error/permission.html"))) {
            chain.doFilter(request, response);
            return;
        }

        // Allowlist of public HTML pages
        if (isPublicHtml(uri)) {
            chain.doFilter(request, response);
            return;
        }

        // For other HTML pages, require a valid AUTH cookie (JWT)
        String role = getUserRole(httpRequest);
        if (role != null) {
            // User is authenticated — enforce role-based access for specific pages
            if (isAdminOnlyPage(uri) && !isAdmin(role)) {
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                RequestDispatcher rd = httpRequest.getRequestDispatcher("/error/permission.html");
                rd.forward(request, response);
                return;
            }

            chain.doFilter(request, response);
        } else {
            // User is NOT authenticated — redirect to login page instead of showing error
            // Preserve the originally requested URL so we can redirect back after login
            String redirectTo = httpRequest.getRequestURI();
            String query = httpRequest.getQueryString();
            if (query != null && !query.isEmpty()) {
                redirectTo += "?" + query;
            }
            String loginUrl = httpRequest.getContextPath() + "/login.html?redirect=" +
                    java.net.URLEncoder.encode(redirectTo, "UTF-8");
            httpResponse.sendRedirect(loginUrl);
        }
    }

    @Override
    public void destroy() { }

    private boolean isPublicHtml(String uri) {
        if (uri == null) return false;
        String u = uri.trim().toLowerCase();
        // Root welcome or index (public — guest sees hero section, no dashboard/create controls shown via JS)
        if (u.equals("/") || u.equals("/index.html")) return true;
        // Login page
        if (u.equals("/login.html")) return true;
        
        // Search page (kept public for UX)
        if (u.equals("/search.html")) return true;
        // Any view sub-pages
        if (u.startsWith("/view/") || u.contains("/view/")) return true;
        // Shared partials (header, etc.)
        if (u.startsWith("/partials/")) return true;
        // Error pages must remain accessible
        if (u.startsWith("/error/")) return true;
        return false;
    }

    

    private String getUserRole(HttpServletRequest request) {
        String token = getTokenFromCookie(request);
        if (token == null || token.isEmpty()) return null;
        try {
            Object roleObj = JwtUtil.validateToken(token).get("role");
            return roleObj != null ? roleObj.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAdmin(String role) {
        if (role == null) return false;
        String r = role.trim().toLowerCase().replace('_', ' ').replace('-', ' ');
        // Treat any role containing "admin" as admin, including common typos like "suber admin"
        return r.contains("admin");
    }

    private boolean isAdminOnlyPage(String uri) {
        if (uri == null) return false;
        String u = uri.trim().toLowerCase();
        // Admin panel
        if (u.equals("/admin-panel.html")) return true;
        // Create pages - removed from admin-only since role-based permissions are now enforced
        // Permission checks are now handled by PermissionService and servlets
        // if (u.equals("/dataset.html")) return true;
        // if (u.equals("/system.html")) return true;
        // if (u.equals("/system-interface.html")) return true;
        // if (u.equals("/glossary.html")) return true;
        // Add other create pages if needed
        return false;
    }

    private String getTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("ACCESS_TOKEN".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
            for (Cookie cookie : request.getCookies()) {
                if ("AUTH".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}


