package com.example.budg_v2;

import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.AuthConfigUtil;
import com.example.budg_v2.util.SessionManager;
import com.example.budg_v2.util.GuestAuthHelper;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.CookieUtil;
import com.example.budg_v2.util.CsrfTokenUtil;
import com.example.budg_v2.database.DatabaseConnection;
import com.nimbusds.jwt.JWTClaimsSet;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Servlet for refreshing JWT tokens
 */
@WebServlet(urlPatterns = {"/api/refresh", "/auth/refresh"})
public class RefreshTokenServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenServlet.class);
    private static final String ACCESS_COOKIE = "ACCESS_TOKEN";
    private static final String REFRESH_COOKIE = "REFRESH_TOKEN";

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        try {
            // Get REFRESH token from cookie
            String token = getTokenFromCookie(request, REFRESH_COOKIE);
            
            if (token == null) {
                sendNotLoggedIn(response);
                return;
            }
            
            // Validate refresh token and ensure not revoked
            JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
            String oldJti = claims.getJWTID();
            String sessionId = claims.getStringClaim("sessionId");
            int userId = Integer.parseInt(claims.getSubject());
            boolean isGuest = GuestAuthHelper.isGuestRole(claims.getStringClaim("role"));
            boolean sessionMissing = sessionId == null || !SessionManager.exists(sessionId);
            if (!isGuest && isRefreshRevokedOrExpired(oldJti)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid refresh token\"}");
                return;
            }
            if (!isGuest && sessionMissing) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid refresh token\"}");
                return;
            }
            if (isGuest && sessionMissing) {
                sessionId = UUID.randomUUID().toString().replace("-", "");
            }

            // TOKEN REFRESH ROTATION: Revoke the old refresh token before generating new ones
            if (!isGuest) {
                revokeRefreshToken(oldJti);
                logger.info("Revoked old refresh token (jti: {}) for user: {} as part of rotation", oldJti, userId);
            }

            // Generate new access token
            AuthConfigUtil.AuthConfig cfg = AuthConfigUtil.getConfig();
            String newAccess = JwtUtil.generateAccessToken(
                userId,
                claims.getStringClaim("email"),
                claims.getStringClaim("firstName"),
                claims.getStringClaim("lastName"),
                claims.getStringClaim("role"),
                claims.getStringClaim("avatarPath"),
                sessionId,
                cfg.jwtValiditySeconds
            );

            // TOKEN REFRESH ROTATION: Generate new refresh token
            String newRefresh = JwtUtil.generateRefreshToken(
                userId,
                claims.getStringClaim("email"),
                claims.getStringClaim("firstName"),
                claims.getStringClaim("lastName"),
                claims.getStringClaim("role"),
                claims.getStringClaim("avatarPath"),
                sessionId,
                cfg.refreshValiditySeconds
            );

            // TOKEN REFRESH ROTATION: Persist the new refresh token
            if (!isGuest) {
                persistRefreshToken(userId, newRefresh, sessionId, cfg.refreshValiditySeconds);
            }

            boolean isHttps = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
            
            // Set new access token cookie
            CookieUtil.addCookie(response, ACCESS_COOKIE, newAccess, 
                                (int) cfg.jwtValiditySeconds, isHttps, "Strict");

            // TOKEN REFRESH ROTATION: Set new refresh token cookie
            CookieUtil.addCookie(response, REFRESH_COOKIE, newRefresh, 
                                (int) cfg.refreshValiditySeconds, isHttps, "Lax");

            CsrfTokenUtil.setCsrfCookie(request, response, CsrfTokenUtil.newToken());
            
            // Return success response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Token refreshed successfully");
            
            response.getWriter().write(new Gson().toJson(responseData));
            logger.info("Tokens refreshed successfully with rotation for user: {} (session: {})", userId, sessionId);
            
        } catch (Exception e) {
            logger.error("Error refreshing token", e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid token\"}");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        sendNotLoggedIn(response);
    }
    
    private String getTokenFromCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private boolean isRefreshRevokedOrExpired(String jti) {
        String sql = "SELECT is_revoked, expires_at < CURRENT_TIMESTAMP FROM auth_tokens WHERE token_id = ? AND type = 'REFRESH'";
        try (java.sql.Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jti);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return true;
                boolean revoked = rs.getBoolean(1);
                boolean expired = rs.getBoolean(2);
                return revoked || expired;
            }
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Revoke a refresh token by marking it as revoked in the database
     * Part of token refresh rotation security mechanism
     */
    private void revokeRefreshToken(String jti) {
        String sql = "UPDATE auth_tokens SET is_revoked = TRUE WHERE token_id = ? AND type = 'REFRESH'";
        try (java.sql.Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jti);
            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated == 0) {
                logger.warn("Refresh token (jti: {}) not found for revocation", jti);
            }
        } catch (java.sql.SQLException e) {
            logger.error("Failed to revoke refresh token (jti: {})", jti, e);
            throw new RuntimeException("Failed to revoke refresh token", e);
        }
    }

    /**
     * Persist a new refresh token to the database
     * Part of token refresh rotation mechanism
     */
    private void persistRefreshToken(int userId, String refreshToken, String sessionId, long validitySeconds) throws java.sql.SQLException {
        String jti;
        java.util.Date exp;
        try {
            com.nimbusds.jwt.SignedJWT jwt = com.nimbusds.jwt.SignedJWT.parse(refreshToken);
            jti = jwt.getJWTClaimsSet().getJWTID();
            exp = jwt.getJWTClaimsSet().getExpirationTime();
        } catch (java.text.ParseException e) {
            throw new RuntimeException("Failed to parse refresh token", e);
        }
        String sql = "INSERT INTO auth_tokens (token_id, user_id, type, is_revoked, issued_at, expires_at, session_id) VALUES (?,?, 'REFRESH', FALSE, CURRENT_TIMESTAMP, ?, ?)";
        try (java.sql.Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jti);
            ps.setInt(2, userId);
            ps.setTimestamp(3, new java.sql.Timestamp(exp.getTime()));
            ps.setString(4, sessionId);
            ps.executeUpdate();
            logger.debug("Persisted new refresh token (jti: {}) for user: {} session: {}", jti, userId, sessionId);
        }
    }

    private void sendNotLoggedIn(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("message", "Not logged in");
        response.getWriter().write(new Gson().toJson(resp));
    }

    @SuppressWarnings("unused")
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = { "X-Forwarded-For", "X-Real-IP" };
        for (String h : headers) {
            String value = request.getHeader(h);
            if (value != null && !value.isEmpty()) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
