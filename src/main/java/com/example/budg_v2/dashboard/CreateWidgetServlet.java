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
import java.util.*;

/**
 * Create Widget Servlet
 * Handles creation of new dashboard widgets
 * POST /api/dashboard/create-widget
 */
@WebServlet(name = "CreateWidgetServlet", urlPatterns = "/api/dashboard/create-widget")
public class CreateWidgetServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User not authenticated");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> requestBody = objectMapper.readValue(request.getInputStream(), Map.class);
            
            // Get dashboard ID from request body
            Integer dashboardId = null;
            Object dashboardIdObj = requestBody.get("dashboardId");
            if (dashboardIdObj != null) {
                if (dashboardIdObj instanceof Integer) {
                    dashboardId = (Integer) dashboardIdObj;
                } else if (dashboardIdObj instanceof String) {
                    String dashboardIdStr = (String) dashboardIdObj;
                    if (!dashboardIdStr.isEmpty()) {
                        try {
                            dashboardId = Integer.parseInt(dashboardIdStr);
                        } catch (NumberFormatException e) {
                            // Invalid format
                        }
                    }
                }
            }
            
            // If dashboardId is null, use default dashboard for main dashboard
            // If dashboardId is provided, validate it belongs to the user
            if (dashboardId == null) {
                // Main dashboard - use default dashboard
                dashboardId = getOrCreateUserDashboard(userId);
            } else {
                // Validate that the dashboard belongs to the user or user has access
                if (!userHasAccessToDashboard(dashboardId, userId)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "You don't have access to this dashboard");
                    objectMapper.writeValue(response.getWriter(), error);
                    return;
                }
            }
            
            if (dashboardId == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Failed to get or create dashboard");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            // Create widget
            Integer widgetId = createWidget(dashboardId, requestBody, userId);
            
            if (widgetId != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("widgetId", widgetId);
                result.put("message", "Widget created successfully");
                objectMapper.writeValue(response.getWriter(), result);
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Failed to create widget");
                objectMapper.writeValue(response.getWriter(), error);
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    /**
     * Get or create default dashboard for user
     */
    private Integer getOrCreateUserDashboard(int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if dashboard tables exist
            if (!tablesExist(conn)) {
                return null;
            }

            // Try to get existing default dashboard
            String selectSql = """
                SELECT ID FROM dashboards 
                WHERE Created_By = ? AND Is_Default = 1
                LIMIT 1
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("ID");
                    }
                }
            }

            // Create new default dashboard
            String insertSql = """
                INSERT INTO dashboards (Title, Description, Is_Public, Is_Default, Created_By, Layout_Type)
                VALUES (?, ?, 0, 1, ?, 'grid')
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "My Dashboard");
                ps.setString(2, "Default dashboard");
                ps.setInt(3, userId);
                ps.executeUpdate();
                
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int dashboardId = rs.getInt(1);
                        
                        // Do NOT insert into dashboard_x_user when creating widget
                        // The dashboard_x_user table is only used when sharing with other users
                        // Ownership is tracked via Created_By in dashboards table
                        
                        return dashboardId;
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Create widget in database
     */
    private Integer createWidget(int dashboardId, Map<String, Object> widgetData, int userId) throws SQLException {
        String widgetSource = (String) widgetData.get("widgetSource");
        String title = (String) widgetData.get("title");
        String description = (String) widgetData.get("description");
        
        // Get next position (add to end)
        int nextPositionY = getNextPositionY(dashboardId);
        int nextOrder = getNextOrder(dashboardId);
        
        // Determine widget type and config
        String widgetType;
        String widgetConfigJson;
        
        if ("text".equals(widgetSource)) {
            // Text widget
            widgetType = "text";
            String textContent = (String) widgetData.get("textContent");
            if (textContent == null) {
                textContent = "";
            }
            
            Map<String, Object> config = new HashMap<>();
            config.put("content", textContent);
            config.put("contentType", "html");
            
            try {
                widgetConfigJson = objectMapper.writeValueAsString(config);
            } catch (Exception e) {
                widgetConfigJson = "{\"content\":\"\",\"contentType\":\"html\"}";
            }
        } else if ("savedSearch".equals(widgetSource)) {
            // Saved search widget - save like text widget but with saved search config
            Object sourceObj = widgetData.get("source");
            Integer searchId = null;
            if (sourceObj instanceof Number) {
                searchId = ((Number) sourceObj).intValue();
            } else if (sourceObj instanceof String) {
                try {
                    searchId = Integer.parseInt((String) sourceObj);
                } catch (NumberFormatException e) {
                    // Invalid search ID
                }
            }
            
            String facetName = (String) widgetData.get("focus");
            String visualizeAs = (String) widgetData.get("visualizeAs");
            String visualizeBy = (String) widgetData.get("visualizeBy");
            @SuppressWarnings("unchecked")
            List<String> displayColumns = (List<String>) widgetData.get("display");
            
            widgetType = "saved_search";
            
            // Build widget config similar to text widget structure
            Map<String, Object> config = new HashMap<>();
            config.put("widgetSource", "savedSearch");
            config.put("source", searchId != null ? searchId : 0);
            config.put("focus", facetName != null ? facetName : "");
            config.put("visualizeAs", visualizeAs != null ? visualizeAs : "table");
            config.put("visualizeBy", visualizeBy != null ? visualizeBy : "");
            config.put("display", displayColumns != null ? displayColumns : List.of());
            
            // Also include dataSource for backward compatibility
            config.put("dataSource", Map.of(
                "type", "saved_search",
                "searchId", searchId != null ? searchId : 0
            ));
            config.put("facet", facetName);
            config.put("visualization", Map.of(
                "type", visualizeAs != null ? visualizeAs : "table",
                "visualizeBy", visualizeBy != null ? visualizeBy : "",
                "displayColumns", displayColumns != null ? displayColumns : List.of()
            ));
            
            try {
                widgetConfigJson = objectMapper.writeValueAsString(config);
            } catch (Exception e) {
                widgetConfigJson = "{}";
            }
        } else {
            // Unknown widget source - default to custom
            widgetType = "custom";
            widgetConfigJson = "{}";
        }
        
        // Insert widget
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                INSERT INTO dashboard_widgets (
                    Dashboard_ID, Widget_Type, Widget_Template_ID, Title, Description,
                    Position_X, Position_Y, Width, Height, Widget_Order,
                    Is_Visible, Widget_Config
                ) VALUES (?, ?, NULL, ?, ?, 0, ?, 2, 1, ?, 1, ?)
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, dashboardId);
                ps.setString(2, widgetType);
                ps.setString(3, title);
                ps.setString(4, description != null ? description : "");
                ps.setInt(5, nextPositionY);
                ps.setInt(6, nextOrder);
                ps.setString(7, widgetConfigJson);
                
                ps.executeUpdate();
                
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Get next Y position for widget (add to bottom)
     */
    private int getNextPositionY(int dashboardId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT COALESCE(MAX(Position_Y), -1) + 1 AS next_y
                FROM dashboard_widgets
                WHERE Dashboard_ID = ?
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, dashboardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("next_y");
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Check if user has access to dashboard
     */
    private boolean userHasAccessToDashboard(Integer dashboardId, Integer userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT 1
                FROM dashboards d
                LEFT JOIN dashboard_x_user dxu ON dxu.Dashboard_ID = d.ID AND dxu.User_ID = ?
                WHERE d.ID = ? AND (d.Created_By = ? OR dxu.User_ID IS NOT NULL)
                LIMIT 1
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setInt(2, dashboardId);
                ps.setInt(3, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    /**
     * Get next order for widget
     */
    private int getNextOrder(int dashboardId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT COALESCE(MAX(Widget_Order), 0) + 1 AS next_order
                FROM dashboard_widgets
                WHERE Dashboard_ID = ?
            """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, dashboardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("next_order");
                    }
                }
            }
        }
        return 1;
    }

    /**
     * Check if dashboard tables exist
     */
    private boolean tablesExist(Connection conn) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "dashboards", null)) {
            boolean dashboardsExists = rs.next();
            
            try (ResultSet rs2 = conn.getMetaData().getTables(null, null, "dashboard_widgets", null)) {
                boolean widgetsExists = rs2.next();
                
                return dashboardsExists && widgetsExists;
            }
        }
    }
}

