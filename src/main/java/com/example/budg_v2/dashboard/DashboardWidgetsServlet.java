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
 * Dashboard Widgets Servlet
 * Loads custom widgets from database for the current user
 * GET /api/dashboard/widgets
 */
@WebServlet(name = "DashboardWidgetsServlet", urlPatterns = "/api/dashboard/widgets")
public class DashboardWidgetsServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
            // Get dashboard ID from request parameter or use default
            Integer dashboardId = null;
            String dashboardIdParam = request.getParameter("dashboardId");
            if (dashboardIdParam != null && !dashboardIdParam.isEmpty()) {
                try {
                    dashboardId = Integer.parseInt(dashboardIdParam);
                } catch (NumberFormatException e) {
                    // Invalid dashboard ID, use default
                }
            }
            
            List<Map<String, Object>> widgets = getCustomWidgets(userId, dashboardId);
            objectMapper.writeValue(response.getWriter(), widgets);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    /**
     * Get custom widgets for user's dashboard
     */
    private List<Map<String, Object>> getCustomWidgets(int userId, Integer dashboardId) throws SQLException {
        List<Map<String, Object>> widgets = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if tables exist
            if (!tablesExist(conn)) {
                return widgets;
            }

            // If no dashboard ID provided, use user's default dashboard
            if (dashboardId == null) {
                dashboardId = getUserDashboard(conn, userId);
            }
            
            if (dashboardId == null) {
                return widgets;
            }

            // Get widgets for this dashboard
            String sql = """
                SELECT 
                    dw.ID,
                    dw.Widget_Type,
                    dw.Title,
                    dw.Description,
                    dw.Position_X,
                    dw.Position_Y,
                    dw.Width,
                    dw.Height,
                    dw.Widget_Order,
                    dw.Is_Visible,
                    dw.Widget_Config
                FROM dashboard_widgets dw
                WHERE dw.Dashboard_ID = ?
                    AND dw.Is_Visible = 1
                ORDER BY dw.Widget_Order
            """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, dashboardId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> widget = new HashMap<>();
                        widget.put("id", rs.getInt("ID"));
                        widget.put("type", rs.getString("Widget_Type"));
                        widget.put("title", rs.getString("Title"));
                        
                        // Get description from Description column, fallback to config for backward compatibility
                        String description = rs.getString("Description");
                        if (description == null || description.trim().isEmpty()) {
                            // Try to get from config for backward compatibility
                            try {
                                String configJson = rs.getString("Widget_Config");
                                if (configJson != null && !configJson.isEmpty()) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> config = objectMapper.readValue(configJson, Map.class);
                                    if (config != null && config.containsKey("description")) {
                                        description = (String) config.get("description");
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore parsing errors
                            }
                        }
                        widget.put("description", description != null ? description : "");
                        
                        widget.put("positionX", rs.getInt("Position_X"));
                        widget.put("positionY", rs.getInt("Position_Y"));
                        widget.put("width", rs.getInt("Width"));
                        widget.put("height", rs.getInt("Height"));
                        widget.put("order", rs.getInt("Widget_Order"));

                        // Parse widget config JSON
                        String configJson = rs.getString("Widget_Config");
                        if (configJson != null && !configJson.isEmpty()) {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> config = objectMapper.readValue(configJson, Map.class);
                                widget.put("config", config);
                            } catch (Exception e) {
                                widget.put("config", new HashMap<>());
                            }
                        } else {
                            widget.put("config", new HashMap<>());
                        }

                        widgets.add(widget);
                    }
                }
            }
        }

        return widgets;
    }

    /**
     * Get user's default dashboard ID
     */
    private Integer getUserDashboard(Connection conn, int userId) throws SQLException {
        String sql = """
            SELECT ID FROM dashboards 
            WHERE Created_By = ? AND Is_Default = 1
            LIMIT 1
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }
        return null;
    }

    /**
     * Check if dashboard tables exist
     */
    private boolean tablesExist(Connection conn) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "dashboards", null)) {
            return rs.next();
        }
    }
}

