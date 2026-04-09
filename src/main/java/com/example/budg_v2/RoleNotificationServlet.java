package com.example.budg_v2;

import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Servlet for role notification actions
 * Endpoints:
 * PUT /api/role-notifications/accept-all - Accept all unaccepted roles for current user
 * GET /api/role-notifications/unaccepted-count - Get count of unaccepted roles
 */
@WebServlet({"/api/role-notifications/*"})
public class RoleNotificationServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(RoleNotificationServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.handlePreflight(response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        Integer userId = getUserIdFromRequest(request);

        if (userId == null || userId == 0) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            JsonObject error = new JsonObject();
            error.addProperty("error", "User not authenticated");
            response.getWriter().write(gson.toJson(error));
            return;
        }

        try {
            if (pathInfo != null && pathInfo.equals("/unaccepted-count")) {
                // Get count of unaccepted roles
                int count = getUnacceptedRoleCount(userId);
                JsonObject result = new JsonObject();
                result.addProperty("count", count);
                response.getWriter().write(gson.toJson(result));
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid endpoint");
                response.getWriter().write(gson.toJson(error));
            }

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        Integer userId = getUserIdFromRequest(request);

        if (userId == null || userId == 0) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            JsonObject error = new JsonObject();
            error.addProperty("error", "User not authenticated");
            response.getWriter().write(gson.toJson(error));
            return;
        }

        try {
            if (pathInfo != null && pathInfo.equals("/accept-all")) {
                // Accept all unaccepted roles for the current user
                int acceptedCount = acceptAllRoles(userId);
                
                // Delete all role notifications from database
                WorkflowNotificationDAO notificationDAO = new WorkflowNotificationDAO();
                int deletedCount = notificationDAO.deleteAllByUserIdAndCategory(userId, "roles");
                logger.info("Deleted {} role notifications for user {}", deletedCount, userId);

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "All roles accepted and notifications cleared");
                result.addProperty("acceptedCount", acceptedCount);
                result.addProperty("deletedNotificationsCount", deletedCount);
                response.getWriter().write(gson.toJson(result));
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid endpoint");
                response.getWriter().write(gson.toJson(error));
            }

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Accept all unaccepted roles for a user
     * Updates AcceptedID from 2 (No) to 1 (Yes) for all roles where user hasn't accepted
     */
    private int acceptAllRoles(int userId) throws SQLException {
        String sql = """
            UPDATE object_x_people 
            SET AcceptedID = 1, lastupdatedatetime = NOW(), lastupdateuser_id = ?
            WHERE ipid = ? AND AcceptedID = 2
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            
            int rowsAffected = stmt.executeUpdate();
            logger.info("Accepted {} roles for user {}", rowsAffected, userId);
            return rowsAffected;
        }
    }

    /**
     * Get count of unaccepted roles for a user
     */
    private int getUnacceptedRoleCount(int userId) throws SQLException {
        String sql = """
            SELECT COUNT(*) 
            FROM object_x_people 
            WHERE ipid = ? AND AcceptedID = 2
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Get user ID from request with fallback to cookie parsing
     */
    private Integer getUserIdFromRequest(HttpServletRequest request) {
        // First, try to get from request attributes (set by AuthFilter)
        Integer userId = UserContextUtil.getCurrentUserIdOrNull(request);
        
        if (userId != null && userId > 0) {
            return userId;
        }
        
        // Fallback: parse ACCESS_TOKEN cookie directly if filter didn't set attributes
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                userId = JwtUtil.getUserIdFromToken(token);
                if (userId != null && userId > 0) {
                    return userId;
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired, return null
            logger.debug("Failed to parse token from cookie: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Get cookie value from request
     */
    private String getCookie(HttpServletRequest request, String name) {
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
}
