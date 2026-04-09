package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.AuthConfigUtil;
import com.example.budg_v2.util.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Servlet for managing user API tokens (refresh tokens)
 * Security: Each user can only view and manage their own tokens
 */
@WebServlet(urlPatterns = {"/api/user/tokens", "/api/user/tokens/*"})
public class UserTokensServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(UserTokensServlet.class);
    private static final int MAX_TOKENS_PER_HOUR = 5;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final long RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000; // 1 hour
    private static final int MAX_TOKEN_ID_LENGTH = 256;
    
    // Store rate limiting data: userId -> List of generation timestamps
    private static final Map<Integer, List<Long>> tokenGenerationAttempts = new HashMap<>();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // SECURITY: Get authenticated user ID from request attributes (set by AuthFilter)
        int userId = getAuthenticatedUserId(request);
        if (userId <= 0) {
            logger.warn("Unauthorized token access attempt - no valid user ID");
            sendErrorResponse(response, "Unauthorized", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        
        String clientIp = getClientIpAddress(request);
        int limit = parsePositiveIntOrDefault(request.getParameter("limit"), DEFAULT_LIMIT);
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        int offset = parseNonNegativeIntOrDefault(request.getParameter("offset"), 0);
        logger.info("User {} requesting tokens list from IP: {} (limit: {}, offset: {})", userId, clientIp, limit, offset);
        
        try {
            List<Map<String, Object>> tokens = getUserTokens(userId, limit, offset);
            int total = getUserTokensCount(userId);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("tokens", tokens);
            responseData.put("limit", limit);
            responseData.put("offset", offset);
            responseData.put("total", total);
            responseData.put("has_more", (offset + tokens.size()) < total);
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(new Gson().toJson(responseData));
            
            logger.info("Successfully retrieved {} tokens for user {} (total: {})", tokens.size(), userId, total);
            
        } catch (Exception e) {
            logger.error("Error retrieving tokens for user {}: {}", userId, e.getMessage(), e);
            sendErrorResponse(response, "Failed to retrieve tokens", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // SECURITY: Get authenticated user ID
        int userId = getAuthenticatedUserId(request);
        if (userId <= 0) {
            logger.warn("Unauthorized token generation attempt - no valid user ID");
            sendErrorResponse(response, "Unauthorized", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        
        String clientIp = getClientIpAddress(request);
        
        // SECURITY: Rate limiting - check if user exceeded token generation limit
        if (!checkRateLimit(userId)) {
            logger.warn("Rate limit exceeded for user {} from IP: {}", userId, clientIp);
            sendErrorResponse(response, "Too many token generation requests. Please try again later.", 
                429); // 429 Too Many Requests
            return;
        }
        
        try {
            // Get user details for token generation
            Map<String, String> userDetails = getUserDetails(userId);
            if (userDetails == null) {
                logger.error("User {} not found in database", userId);
                sendErrorResponse(response, "User not found", HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            
            // Generate new refresh token
            AuthConfigUtil.AuthConfig cfg = AuthConfigUtil.getConfig();
            String sessionId = UUID.randomUUID().toString(); // Generate new session ID for API token
            
            String newRefreshToken = JwtUtil.generateRefreshToken(
                userId,
                userDetails.get("email"),
                userDetails.get("firstName"),
                userDetails.get("lastName"),
                userDetails.get("role"),
                userDetails.get("avatarPath"),
                sessionId,
                cfg.refreshValiditySeconds
            );
            
            // Persist the new token
            String tokenId = persistRefreshToken(userId, newRefreshToken, sessionId, cfg.refreshValiditySeconds);
            
            // Create session entry
            SessionManager.registerSession(userId, clientIp, request.getHeader("User-Agent"));
            
            // Record the generation attempt for rate limiting
            recordTokenGeneration(userId);
            
            // Get token details for response
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("token_id", tokenId);
            tokenData.put("token_value", newRefreshToken); // Only return on creation
            tokenData.put("issued_at", new Date());
            tokenData.put("expires_at", new Date(System.currentTimeMillis() + (cfg.refreshValiditySeconds * 1000)));
            tokenData.put("is_revoked", false);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Token generated successfully");
            responseData.put("token", tokenData);
            
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(new Gson().toJson(responseData));
            
            logger.info("Successfully generated new token {} for user {} from IP: {}", tokenId, userId, clientIp);
            
        } catch (Exception e) {
            logger.error("Error generating token for user {}: {}", userId, e.getMessage(), e);
            sendErrorResponse(response, "Failed to generate token", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // SECURITY: Get authenticated user ID
        int userId = getAuthenticatedUserId(request);
        if (userId <= 0) {
            logger.warn("Unauthorized token deletion attempt - no valid user ID");
            sendErrorResponse(response, "Unauthorized", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        
        String clientIp = getClientIpAddress(request);
        String pathInfo = request.getPathInfo();
        
        try {
            // Handle different deletion endpoints
            if (pathInfo != null && pathInfo.equals("/expired")) {
                // Delete all expired tokens
                handleDeleteExpired(userId, clientIp, response);
            } else if (pathInfo != null && pathInfo.equals("/selected")) {
                // Delete multiple selected tokens
                handleDeleteSelected(userId, clientIp, request, response);
            } else if (pathInfo != null && pathInfo.length() > 1) {
                // Delete specific token by ID
                String tokenId = pathInfo.substring(1); // Remove leading "/"
                handleDeleteSingle(userId, tokenId, clientIp, response);
            } else {
                sendErrorResponse(response, "Invalid deletion request", HttpServletResponse.SC_BAD_REQUEST);
            }
            
        } catch (Exception e) {
            logger.error("Error deleting tokens for user {}: {}", userId, e.getMessage(), e);
            sendErrorResponse(response, "Failed to delete tokens", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Delete all expired tokens for the authenticated user
     */
    private void handleDeleteExpired(int userId, String clientIp, HttpServletResponse response) 
            throws IOException {
        
        // UI marks both time-expired and revoked tokens as "Expired",
        // so this endpoint should clear both categories for consistency.
        String sql = "DELETE FROM auth_tokens " +
                     "WHERE user_id = ? AND type = 'REFRESH' " +
                     "AND (expires_at < CURRENT_TIMESTAMP OR is_revoked = TRUE)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, userId);
            int deletedCount = ps.executeUpdate();
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Expired tokens deleted successfully");
            responseData.put("deleted_count", deletedCount);
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(new Gson().toJson(responseData));
            
            logger.info("Deleted {} expired tokens for user {} from IP: {}", deletedCount, userId, clientIp);
            
        } catch (SQLException e) {
            logger.error("Database error deleting expired tokens for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Database error", e);
        }
    }
    
    /**
     * Delete multiple selected tokens by their IDs
     */
    private void handleDeleteSelected(int userId, String clientIp, HttpServletRequest request, 
                                     HttpServletResponse response) throws IOException {
        
        // Parse request body to get token IDs
        String requestBody = getRequestBody(request);
        if (requestBody == null || requestBody.trim().isEmpty()) {
            sendErrorResponse(response, "Request body is required", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        
        try {
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
            JsonArray tokenIdsArray = jsonObject.getAsJsonArray("token_ids");
            
            if (tokenIdsArray == null || tokenIdsArray.size() == 0) {
                sendErrorResponse(response, "No token IDs provided", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            
            List<String> tokenIds = new ArrayList<>();
            for (int i = 0; i < tokenIdsArray.size(); i++) {
                String tokenId = tokenIdsArray.get(i).getAsString();
                // SECURITY: Validate token ID format
                if (isValidTokenId(tokenId)) {
                    tokenIds.add(tokenId);
                } else {
                    logger.warn("Invalid token ID format in deletion request: {}", tokenId);
                }
            }
            
            if (tokenIds.isEmpty()) {
                sendErrorResponse(response, "No valid token IDs provided", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            
            int deletedCount = deleteMultipleTokens(userId, tokenIds);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Selected tokens deleted successfully");
            responseData.put("deleted_count", deletedCount);
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(new Gson().toJson(responseData));
            
            logger.info("Deleted {} selected tokens for user {} from IP: {}", deletedCount, userId, clientIp);
            
        } catch (Exception e) {
            logger.error("Error parsing deletion request for user {}: {}", userId, e.getMessage(), e);
            sendErrorResponse(response, "Invalid request format", HttpServletResponse.SC_BAD_REQUEST);
        }
    }
    
    /**
     * Delete a single token by its ID
     */
    private void handleDeleteSingle(int userId, String tokenId, String clientIp, HttpServletResponse response) 
            throws IOException {
        
        // SECURITY: Validate token ID format
        if (!isValidTokenId(tokenId)) {
            logger.warn("Invalid token ID format in deletion request: {}", tokenId);
            sendErrorResponse(response, "Invalid token ID", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        
        // SECURITY: Ensure token belongs to authenticated user before deletion
        String sql = "DELETE FROM auth_tokens WHERE token_id = ? AND user_id = ? AND type = 'REFRESH'";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, tokenId);
            ps.setInt(2, userId);
            
            int rowsDeleted = ps.executeUpdate();
            
            if (rowsDeleted == 0) {
                logger.warn("Token {} not found or unauthorized for user {} from IP: {}", tokenId, userId, clientIp);
                sendErrorResponse(response, "Token not found or unauthorized", HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Token deleted successfully");
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(new Gson().toJson(responseData));
            
            logger.info("Successfully deleted token {} for user {} from IP: {}", tokenId, userId, clientIp);
            
        } catch (SQLException e) {
            logger.error("Database error deleting token {} for user {}: {}", tokenId, userId, e.getMessage(), e);
            throw new RuntimeException("Database error", e);
        }
    }
    
    /**
     * Get all refresh tokens for a specific user
     * SECURITY: Only returns tokens for the specified user
     * Ordered by: Active tokens first (by issued_at DESC), then expired/revoked tokens last
     */
    private List<Map<String, Object>> getUserTokens(int userId, int limit, int offset) throws SQLException {
        String sql = "SELECT token_id, issued_at, expires_at, is_revoked, session_id " +
                     "FROM auth_tokens " +
                     "WHERE user_id = ? AND type = 'REFRESH' " +
                     "ORDER BY " +
                     "  CASE " +
                     "    WHEN is_revoked = TRUE OR expires_at < CURRENT_TIMESTAMP THEN 1 " +
                     "    ELSE 0 " +
                     "  END, " +
                     "  issued_at DESC " +
                     "LIMIT ? OFFSET ?";
        
        List<Map<String, Object>> tokens = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> token = new HashMap<>();
                    token.put("token_id", rs.getString("token_id"));
                    token.put("issued_at", rs.getTimestamp("issued_at"));
                    token.put("expires_at", rs.getTimestamp("expires_at"));
                    token.put("is_revoked", rs.getBoolean("is_revoked"));
                    token.put("session_id", rs.getString("session_id"));
                    tokens.add(token);
                }
            }
        }
        
        return tokens;
    }

    /**
     * Count all refresh tokens for user (for pagination UI).
     */
    private int getUserTokensCount(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM auth_tokens WHERE user_id = ? AND type = 'REFRESH'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        }
        return 0;
    }
    
    /**
     * Get user details for token generation
     */
    private Map<String, String> getUserDetails(int userId) throws SQLException {
        String sql = "SELECT Email, First_Name, Last_Name, Role, Avatar_Path FROM people WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, userId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, String> details = new HashMap<>();
                    details.put("email", rs.getString("Email"));
                    details.put("firstName", rs.getString("First_Name"));
                    details.put("lastName", rs.getString("Last_Name"));
                    details.put("role", rs.getString("Role"));
                    details.put("avatarPath", rs.getString("Avatar_Path"));
                    return details;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Persist a new refresh token to the database
     */
    private String persistRefreshToken(int userId, String refreshToken, String sessionId, long validitySeconds) 
            throws SQLException {
        
        String jti;
        Date exp;
        
        try {
            com.nimbusds.jwt.SignedJWT jwt = com.nimbusds.jwt.SignedJWT.parse(refreshToken);
            jti = jwt.getJWTClaimsSet().getJWTID();
            exp = jwt.getJWTClaimsSet().getExpirationTime();
        } catch (java.text.ParseException e) {
            throw new RuntimeException("Failed to parse refresh token", e);
        }
        
        String sql = "INSERT INTO auth_tokens (token_id, user_id, type, is_revoked, issued_at, expires_at, session_id) " +
                     "VALUES (?, ?, 'REFRESH', FALSE, CURRENT_TIMESTAMP, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, jti);
            ps.setInt(2, userId);
            ps.setTimestamp(3, new Timestamp(exp.getTime()));
            ps.setString(4, sessionId);
            ps.executeUpdate();
            
            return jti;
        }
    }
    
    /**
     * Delete multiple tokens by their IDs
     * SECURITY: Only deletes tokens belonging to the authenticated user
     */
    private int deleteMultipleTokens(int userId, List<String> tokenIds) throws SQLException {
        if (tokenIds.isEmpty()) {
            return 0;
        }
        
        // Build parameterized query with placeholders
        StringBuilder sql = new StringBuilder("DELETE FROM auth_tokens WHERE user_id = ? AND type = 'REFRESH' AND token_id IN (");
        for (int i = 0; i < tokenIds.size(); i++) {
            sql.append("?");
            if (i < tokenIds.size() - 1) {
                sql.append(",");
            }
        }
        sql.append(")");
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            
            ps.setInt(1, userId);
            for (int i = 0; i < tokenIds.size(); i++) {
                ps.setString(i + 2, tokenIds.get(i));
            }
            
            return ps.executeUpdate();
        }
    }
    
    /**
     * SECURITY: Get authenticated user ID from request attributes
     * Returns -1 if no authenticated user found
     */
    private int getAuthenticatedUserId(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj instanceof Integer) {
            return (Integer) userIdObj;
        }
        return -1;
    }
    
    /**
     * SECURITY: Validate token ID format (alphanumeric and reasonable length)
     */
    private boolean isValidTokenId(String tokenId) {
        if (tokenId == null || tokenId.isEmpty() || tokenId.length() > MAX_TOKEN_ID_LENGTH) {
            return false;
        }
        // Allow alphanumeric characters and hyphens (common in JTI)
        return tokenId.matches("^[a-zA-Z0-9\\-_]+$");
    }
    
    /**
     * SECURITY: Rate limiting check
     * Returns true if user is within rate limit, false otherwise
     */
    private synchronized boolean checkRateLimit(int userId) {
        long currentTime = System.currentTimeMillis();
        
        // Get or create attempt list for this user
        List<Long> attempts = tokenGenerationAttempts.computeIfAbsent(userId, k -> new ArrayList<>());
        
        // Remove attempts outside the time window
        attempts.removeIf(timestamp -> (currentTime - timestamp) > RATE_LIMIT_WINDOW_MS);
        
        // Check if user exceeded limit
        if (attempts.size() >= MAX_TOKENS_PER_HOUR) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Record a token generation attempt for rate limiting
     */
    private synchronized void recordTokenGeneration(int userId) {
        long currentTime = System.currentTimeMillis();
        List<Long> attempts = tokenGenerationAttempts.computeIfAbsent(userId, k -> new ArrayList<>());
        attempts.add(currentTime);
    }
    
    /**
     * Get client IP address (considering proxy headers)
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Take first IP if multiple
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    /**
     * Get request body as string
     */
    private String getRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private int parsePositiveIntOrDefault(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int parseNonNegativeIntOrDefault(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
    
    /**
     * Send error response to client
     */
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", message);
        response.getWriter().write(new Gson().toJson(errorResponse));
    }
}

