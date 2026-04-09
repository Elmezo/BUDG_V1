package com.example.budg_v2;

import com.example.budg_v2.util.SessionManager;
import com.example.budg_v2.util.CookieUtil;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet(urlPatterns = {"/logout", "/auth/logout"})
public class LogoutServlet extends HttpServlet {
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            // Try to get user ID and sessionId from request attributes (normally set by filter)
            Integer userId = (Integer) request.getAttribute("userId");
            String sessionIdAttr = (String) request.getAttribute("sessionId");

            // If not available (e.g., filter skipped /logout), extract from JWT cookie
            if (userId == null || sessionIdAttr == null) {
                String token = getCookie(request, "ACCESS_TOKEN");
                if (token != null) {
                    try {
                        com.nimbusds.jwt.JWTClaimsSet claims = com.nimbusds.jwt.SignedJWT.parse(token).getJWTClaimsSet();
                        userId = Integer.parseInt(claims.getSubject());
                        sessionIdAttr = claims.getStringClaim("sessionId");
                    } catch (Exception ignored) {
                        // Invalid token; proceed to clear cookie anyway
                    }
                }
            }

            // Invalidate session if we have a sessionId; otherwise fallback to all user sessions
            if (sessionIdAttr != null) {
                SessionManager.invalidate(sessionIdAttr);
            } else if (userId != null) {
                SessionManager.invalidateAllUserSessions(userId);
            }

            // Clear both cookies with environment-aware flags
            boolean isHttps = request.isSecure() ||
                    "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
            
            CookieUtil.addCookie(response, "ACCESS_TOKEN", "", 0, isHttps, isHttps ? "Strict" : "Lax");
            CookieUtil.addCookie(response, "REFRESH_TOKEN", "", 0, isHttps, "Lax");
            
            // Prepare response data
            Map<String, String> responseData = new HashMap<>();
            responseData.put("status", "logged_out");
            
            // Send success response
            response.setStatus(200);
            response.getWriter().write(new Gson().toJson(responseData));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(response, "Internal server error", 500);
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // Allow GET requests for logout as well
        doPost(request, response);
    }
    
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        response.setStatus(statusCode);
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", message);
        response.getWriter().write(new Gson().toJson(errorResponse));
    }

    private String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
