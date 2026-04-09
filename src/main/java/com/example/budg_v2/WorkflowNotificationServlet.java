package com.example.budg_v2;

import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Servlet for workflow notifications (UI notifications)
 * Endpoints:
 * GET /api/notifications - Get user notifications (query params: category, unread)
 * GET /api/notifications/unread-count - Get unread counts by category
 * PUT /api/notifications/{id}/read - Mark notification as read
 * PUT /api/notifications/read-all - Mark all notifications as read (query param: category)
 * DELETE /api/notifications/{id} - Delete notification
 * DELETE /api/notifications/delete-all - Delete all notifications for user and category (query param: category)
 */
@WebServlet({"/api/notifications", "/api/notifications/*"})
public class WorkflowNotificationServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowNotificationServlet.class);
    private final Gson gson = new Gson();
    private final WorkflowNotificationDAO notificationDAO = new WorkflowNotificationDAO();

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
            if (pathInfo != null && pathInfo.equals("/unread-count")) {
                // Get unread counts by category
                Map<String, Integer> counts = notificationDAO.getUnreadCountsByCategory(userId);
                response.getWriter().write(gson.toJson(counts));

            } else {
                // Get notifications
                String category = request.getParameter("category");
                if (category == null || category.isEmpty()) {
                    category = "workflow"; // Default category
                }

                // Check if unread filter is requested
                String unreadParam = request.getParameter("unread");
                boolean unreadOnly = "true".equalsIgnoreCase(unreadParam);
                
                List<WorkflowNotification> notifications;
                if (unreadOnly) {
                    notifications = notificationDAO.findUnreadByUserId(userId, category);
                } else {
                    notifications = notificationDAO.findByUserId(userId, category);
                }

                response.getWriter().write(gson.toJson(notifications));
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
            if (pathInfo != null && pathInfo.equals("/read-all")) {
                // Mark all as read
                String category = request.getParameter("category");
                if (category == null || category.isEmpty()) {
                    category = "workflow";
                }
                notificationDAO.markAllAsRead(userId, category);

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "All notifications marked as read");
                response.getWriter().write(gson.toJson(result));

            } else if (pathInfo != null && pathInfo.startsWith("/") && pathInfo.endsWith("/read")) {
                // Mark single notification as read
                String idStr = pathInfo.substring(1, pathInfo.length() - 5); // Remove "/" and "/read"
                Long notificationId = Long.parseLong(idStr);
                notificationDAO.markAsRead(notificationId);

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "Notification marked as read");
                response.getWriter().write(gson.toJson(result));

            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid endpoint");
                response.getWriter().write(gson.toJson(error));
            }

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid ID format");
            response.getWriter().write(gson.toJson(error));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
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
            if (pathInfo != null && pathInfo.equals("/delete-all")) {
                // Delete all notifications for user and category
                String category = request.getParameter("category");
                if (category == null || category.isEmpty()) {
                    category = "workflow";
                }
                
                int deletedCount = notificationDAO.deleteAllByUserIdAndCategory(userId, category);
                
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "All notifications deleted");
                result.addProperty("deletedCount", deletedCount);
                response.getWriter().write(gson.toJson(result));
                return;
            }

            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Notification ID is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            Long notificationId = Long.parseLong(pathInfo.substring(1));
            notificationDAO.delete(notificationId);

            response.setStatus(HttpServletResponse.SC_NO_CONTENT);

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid ID format");
            response.getWriter().write(gson.toJson(error));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
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
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                userId = JwtUtil.getUserIdFromToken(token);
                if (userId != null && userId > 0) {
                    // Set attributes for future use
                    request.setAttribute("userId", userId);
                    request.setAttribute("userEmail", claims.getStringClaim("email"));
                    request.setAttribute("userName", (claims.getStringClaim("firstName") + " " + claims.getStringClaim("lastName")).trim());
                    request.setAttribute("userRole", claims.getStringClaim("role"));
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

