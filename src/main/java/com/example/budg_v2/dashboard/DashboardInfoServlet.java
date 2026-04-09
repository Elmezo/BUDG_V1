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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard Info Servlet
 * GET /api/dashboard/info - Get current user's default dashboard info
 * PUT /api/dashboard/info - Update dashboard info (title, description, isDefault)
 */
@WebServlet(name = "DashboardInfoServlet", urlPatterns = "/api/dashboard/info")
public class DashboardInfoServlet extends HttpServlet {

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
            String dashboardIdParam = request.getParameter("id");
            if (dashboardIdParam != null && !dashboardIdParam.isEmpty()) {
                try {
                    dashboardId = Integer.parseInt(dashboardIdParam);
                } catch (NumberFormatException e) {
                    // Invalid dashboard ID, use default
                }
            }
            
            Map<String, Object> dashboardInfo;
            if (dashboardId != null) {
                dashboardInfo = getDashboardInfo(dashboardId, userId);
            } else {
                dashboardInfo = getDefaultDashboardInfo(userId);
            }
            objectMapper.writeValue(response.getWriter(), dashboardInfo);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
            String title = (String) requestBody.get("title");
            String description = (String) requestBody.get("description");
            Boolean setAsDefault = (Boolean) requestBody.get("setAsDefault");
            Integer dashboardId = null;
            
            // Get dashboard ID from request body if provided, otherwise use default
            Object dashboardIdObj = requestBody.get("dashboardId");
            if (dashboardIdObj != null) {
                if (dashboardIdObj instanceof Integer) {
                    dashboardId = (Integer) dashboardIdObj;
                } else if (dashboardIdObj instanceof Number) {
                    dashboardId = ((Number) dashboardIdObj).intValue();
                }
            }
            
            if (title == null || title.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Title is required");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            // If no dashboard ID provided, get default dashboard ID for user
            if (dashboardId == null) {
                dashboardId = getDefaultDashboardId(userId);
                if (dashboardId == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Dashboard not found");
                    objectMapper.writeValue(response.getWriter(), error);
                    return;
                }
            } else {
                // Verify user has access to this dashboard
                Map<String, Object> dashboardInfo = getDashboardInfo(dashboardId, userId);
                if (dashboardInfo.get("id") == null) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "You do not have access to this dashboard");
                    objectMapper.writeValue(response.getWriter(), error);
                    return;
                }
            }

            // Update dashboard
            updateDashboard(dashboardId, userId, title, description, setAsDefault);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Dashboard updated successfully");
            objectMapper.writeValue(response.getWriter(), result);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private Map<String, Object> getDashboardInfo(Integer dashboardId, Integer userId) throws SQLException {
        Map<String, Object> info = new HashMap<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if user has access to this dashboard:
            // 1. Created by user (owner)
            // 2. Public dashboard (Is_Public = 1)
            // 3. Shared with user via dashboard_x_user
            String sql = """
                SELECT d.ID, d.Title, d.Description, d.Is_Default, d.Is_Public, d.Created_By,
                       CONCAT(p.First_Name, ' ', p.Last_Name) as Creator_Name
                FROM dashboards d
                LEFT JOIN dashboard_x_user dxu ON dxu.Dashboard_ID = d.ID AND dxu.User_ID = ?
                LEFT JOIN people p ON d.Created_By = p.ID
                WHERE d.ID = ? AND (
                    d.Created_By = ? 
                    OR d.Is_Public = 1 
                    OR dxu.User_ID IS NOT NULL
                )
                LIMIT 1
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setInt(2, dashboardId);
                ps.setInt(3, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        info.put("id", rs.getInt("ID"));
                        info.put("title", rs.getString("Title"));
                        info.put("description", rs.getString("Description"));
                        info.put("isDefault", rs.getInt("Is_Default") == 1);
                        info.put("isPublic", rs.getInt("Is_Public") == 1);
                        info.put("createdBy", rs.getInt("Created_By"));
                        info.put("creatorName", rs.getString("Creator_Name"));
                        
                        // Get shared users
                        List<Map<String, Object>> sharedUsers = getSharedUsers(conn, dashboardId);
                        info.put("sharedWith", sharedUsers);
                    } else {
                        // Dashboard not found or no access
                        info.put("id", null);
                        info.put("title", "Dashboard");
                        info.put("description", "");
                        info.put("isDefault", false);
                        info.put("creatorName", null);
                        info.put("sharedWith", new ArrayList<>());
                    }
                }
            }
        }
        return info;
    }

    private Map<String, Object> getDefaultDashboardInfo(Integer userId) throws SQLException {
        Map<String, Object> info = new HashMap<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT d.ID, d.Title, d.Description, d.Is_Default, d.Is_Public, d.Created_By,
                       CONCAT(p.First_Name, ' ', p.Last_Name) as Creator_Name
                FROM dashboards d
                LEFT JOIN people p ON d.Created_By = p.ID
                WHERE d.Created_By = ? AND d.Is_Default = 1
                LIMIT 1
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Integer dashboardId = rs.getInt("ID");
                        info.put("id", dashboardId);
                        info.put("title", rs.getString("Title"));
                        info.put("description", rs.getString("Description"));
                        info.put("isDefault", rs.getInt("Is_Default") == 1);
                        info.put("isPublic", rs.getInt("Is_Public") == 1);
                        info.put("createdBy", rs.getInt("Created_By"));
                        info.put("creatorName", rs.getString("Creator_Name"));
                        
                        // Default dashboard (home dashboard) is never shared - always return empty list
                        // This is the user's personal default dashboard, not meant to be shared
                        info.put("sharedWith", new ArrayList<>());
                    } else {
                        // Return default values if no dashboard found
                        info.put("id", null);
                        info.put("title", "My Dashboard");
                        info.put("description", "");
                        info.put("isDefault", false);
                        info.put("isPublic", false);
                        info.put("createdBy", null);
                        info.put("creatorName", null);
                        info.put("sharedWith", new ArrayList<>());
                    }
                }
            }
        }
        return info;
    }

    private Integer getDefaultDashboardId(Integer userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT d.ID
                FROM dashboards d
                WHERE d.Created_By = ? AND d.Is_Default = 1
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
        }
        return null;
    }

    /**
     * Get list of users the dashboard is shared with
     * @param conn Database connection
     * @param dashboardId Dashboard ID
     * @return List of shared users with their names
     */
    private List<Map<String, Object>> getSharedUsers(Connection conn, Integer dashboardId) throws SQLException {
        List<Map<String, Object>> users = new ArrayList<>();
        
        // User_ID = user being shared with (for primary key), sharing = owner's ID (who shared it)
        String sql = """
            SELECT p.ID, CONCAT(p.First_Name, ' ', p.Last_Name) as name
            FROM dashboard_x_user dxu
            JOIN people p ON dxu.User_ID = p.ID
            WHERE dxu.Dashboard_ID = ?
            ORDER BY p.Last_Name, p.First_Name
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dashboardId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("id", rs.getInt("ID"));
                    user.put("name", rs.getString("name"));
                    users.add(user);
                }
            }
        }
        
        return users;
    }

    private void updateDashboard(Integer dashboardId, Integer userId, String title, String description, Boolean setAsDefault) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Update dashboard
                // Note: isDefault is independent for each dashboard - it only controls tab visibility after reload
                // Setting one dashboard as default does not affect other dashboards
                String updateSql = """
                    UPDATE dashboards
                    SET Title = ?, Description = ?, Is_Default = ?
                    WHERE ID = ? AND Created_By = ?
                """;
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, title);
                    ps.setString(2, description != null ? description : "");
                    ps.setInt(3, (setAsDefault != null && setAsDefault) ? 1 : 0);
                    ps.setInt(4, dashboardId);
                    ps.setInt(5, userId);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}

