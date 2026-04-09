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
 * Clone Dashboard Servlet
 * POST /api/dashboard/clone - Clone a dashboard with all its widgets
 */
@WebServlet(name = "CloneDashboardServlet", urlPatterns = "/api/dashboard/clone")
public class CloneDashboardServlet extends HttpServlet {

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
            // Parse request body
            @SuppressWarnings("unchecked")
            Map<String, Object> requestBody = objectMapper.readValue(request.getReader(), Map.class);
            Integer sourceDashboardId = null;
            if (requestBody.get("sourceDashboardId") instanceof Number) {
                sourceDashboardId = ((Number) requestBody.get("sourceDashboardId")).intValue();
            }
            String title = (String) requestBody.get("name");
            String description = (String) requestBody.get("description");
            Boolean setAsDefault = (Boolean) requestBody.get("setAsDefault");
            
            if (sourceDashboardId == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Source dashboard ID is required");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            if (title == null || title.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Title is required");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            if (description == null) {
                description = "";
            }

            // Verify user owns the source dashboard
            if (!userOwnsDashboard(sourceDashboardId, userId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                Map<String, String> error = new HashMap<>();
                error.put("error", "You do not have permission to clone this dashboard");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            // Clone dashboard with all widgets
            Integer newDashboardId = cloneDashboard(sourceDashboardId, userId, title, description, 
                    setAsDefault != null && setAsDefault);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("dashboardId", newDashboardId);
            result.put("message", "Dashboard cloned successfully");
            objectMapper.writeValue(response.getWriter(), result);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private boolean userOwnsDashboard(Integer dashboardId, Integer userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT COUNT(*) as count
                FROM dashboards
                WHERE ID = ? AND Created_By = ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, dashboardId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("count") > 0;
                    }
                }
            }
        }
        return false;
    }

    private Integer cloneDashboard(Integer sourceDashboardId, Integer userId, String title, 
            String description, boolean setAsDefault) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Get source dashboard info
                String getSourceSql = """
                    SELECT Title, Description, Layout_Type
                    FROM dashboards
                    WHERE ID = ? AND Created_By = ?
                """;
                String layoutType = "grid";
                
                try (PreparedStatement ps = conn.prepareStatement(getSourceSql)) {
                    ps.setInt(1, sourceDashboardId);
                    ps.setInt(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            rs.getString("Title");
                            rs.getString("Description");
                            layoutType = rs.getString("Layout_Type");
                            if (layoutType == null) {
                                layoutType = "grid";
                            }
                        }
                    }
                }

                // Create new dashboard
                String insertSql = """
                    INSERT INTO dashboards (Title, Description, Is_Public, Is_Default, Created_By, Layout_Type)
                    VALUES (?, ?, 0, ?, ?, ?)
                """;
                
                Integer newDashboardId = null;
                try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, title);
                    ps.setString(2, description);
                    ps.setInt(3, setAsDefault ? 1 : 0);
                    ps.setInt(4, userId);
                    ps.setString(5, layoutType);
                    ps.executeUpdate();
                    
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            newDashboardId = rs.getInt(1);
                        }
                    }
                }

                if (newDashboardId == null) {
                    throw new SQLException("Failed to create cloned dashboard");
                }

                // Do NOT insert into dashboard_x_user when cloning dashboard
                // The dashboard_x_user table is only used when sharing with other users
                // Ownership is tracked via Created_By in dashboards table

                // Clone all widgets from source dashboard
                String getWidgetsSql = """
                    SELECT 
                        Widget_Type,
                        Title,
                        Description,
                        Position_X,
                        Position_Y,
                        Width,
                        Height,
                        Widget_Order,
                        Is_Visible,
                        Widget_Config
                    FROM dashboard_widgets
                    WHERE Dashboard_ID = ?
                    ORDER BY Widget_Order
                """;
                
                String insertWidgetSql = """
                    INSERT INTO dashboard_widgets 
                        (Dashboard_ID, Widget_Type, Title, Description, Position_X, Position_Y, 
                         Width, Height, Widget_Order, Is_Visible, Widget_Config)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
                
                try (PreparedStatement getWidgetsPs = conn.prepareStatement(getWidgetsSql)) {
                    getWidgetsPs.setInt(1, sourceDashboardId);
                    try (ResultSet widgetsRs = getWidgetsPs.executeQuery()) {
                        try (PreparedStatement insertWidgetPs = conn.prepareStatement(insertWidgetSql)) {
                            while (widgetsRs.next()) {
                                insertWidgetPs.setInt(1, newDashboardId);
                                insertWidgetPs.setString(2, widgetsRs.getString("Widget_Type"));
                                insertWidgetPs.setString(3, widgetsRs.getString("Title"));
                                insertWidgetPs.setString(4, widgetsRs.getString("Description"));
                                insertWidgetPs.setInt(5, widgetsRs.getInt("Position_X"));
                                insertWidgetPs.setInt(6, widgetsRs.getInt("Position_Y"));
                                insertWidgetPs.setInt(7, widgetsRs.getInt("Width"));
                                insertWidgetPs.setInt(8, widgetsRs.getInt("Height"));
                                insertWidgetPs.setInt(9, widgetsRs.getInt("Widget_Order"));
                                insertWidgetPs.setInt(10, widgetsRs.getInt("Is_Visible"));
                                insertWidgetPs.setString(11, widgetsRs.getString("Widget_Config"));
                                insertWidgetPs.executeUpdate();
                            }
                        }
                    }
                }

                conn.commit();
                return newDashboardId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}

