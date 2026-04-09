package com.example.budg_v2.dashboard;

import com.example.budg_v2.database.DatabaseConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet for deleting dashboard widgets
 * DELETE /api/dashboard/widgets/{id}
 */
@WebServlet(name = "DeleteWidgetServlet", urlPatterns = "/api/dashboard/widgets/*")
public class DeleteWidgetServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Check authentication
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User not authenticated");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        // Extract widget ID from URL path
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.startsWith("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Widget ID is required");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        String widgetIdStr = pathInfo.substring(1);
        int widgetId;
        try {
            widgetId = Integer.parseInt(widgetIdStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid widget ID");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            // First verify the widget belongs to this user
            if (!verifyWidgetOwnership(conn, widgetId, userId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                Map<String, String> error = new HashMap<>();
                error.put("error", "You don't have permission to delete this widget");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            // Delete the widget
            String sql = "DELETE FROM dashboard_widgets WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, widgetId);
                int rowsAffected = ps.executeUpdate();

                if (rowsAffected > 0) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "Widget deleted successfully");
                    objectMapper.writeValue(response.getWriter(), result);
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Widget not found");
                    objectMapper.writeValue(response.getWriter(), error);
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    /**
     * Get widget data for editing
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Check authentication
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User not authenticated");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        // Extract widget ID from URL path
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.startsWith("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Widget ID is required");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        String widgetIdStr = pathInfo.substring(1);
        int widgetId;
        try {
            widgetId = Integer.parseInt(widgetIdStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid widget ID");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            // First verify the widget belongs to this user
            if (!verifyWidgetOwnership(conn, widgetId, userId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                Map<String, String> error = new HashMap<>();
                error.put("error", "You don't have permission to access this widget");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            // Get the widget data
            String sql = "SELECT * FROM dashboard_widgets WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, widgetId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> widget = new HashMap<>();
                        widget.put("id", rs.getInt("ID"));
                        widget.put("title", rs.getString("Title"));
                        widget.put("type", rs.getString("Widget_Type"));
                        
                        // Get description from Description column, fallback to config for backward compatibility
                        String description = rs.getString("Description");
                        if (description == null || description.trim().isEmpty()) {
                            // Try to get from config for backward compatibility
                            try {
                                String configJson = rs.getString("Widget_Config");
                                if (configJson != null && !configJson.isEmpty()) {
                                    com.fasterxml.jackson.databind.JsonNode config = objectMapper.readTree(configJson);
                                    if (config.has("description")) {
                                        description = config.get("description").asText("");
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore parsing errors
                            }
                        }
                        widget.put("description", description != null ? description : "");
                        
                        widget.put("config", objectMapper.readTree(rs.getString("Widget_Config")));
                        widget.put("order", rs.getInt("Widget_Order"));
                        widget.put("isVisible", rs.getBoolean("Is_Visible"));
                        
                        objectMapper.writeValue(response.getWriter(), widget);
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Widget not found");
                        objectMapper.writeValue(response.getWriter(), error);
                    }
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    /**
     * Verify that the widget belongs to the user
     */
    private boolean verifyWidgetOwnership(Connection conn, int widgetId, int userId) throws SQLException {
        String sql = """
            SELECT dw.ID
            FROM dashboard_widgets dw
            JOIN dashboards d ON dw.Dashboard_ID = d.ID
            WHERE dw.ID = ? AND d.Created_By = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, widgetId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // Returns true if widget exists and belongs to user
            }
        }
    }
}
