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
 * Dashboard Set Default Servlet
 * PUT /api/dashboard/set-default - Set a dashboard as default
 */
@WebServlet(name = "DashboardSetDefaultServlet", urlPatterns = "/api/dashboard/set-default")
public class DashboardSetDefaultServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
            Integer dashboardId = null;
            if (requestBody.get("dashboardId") instanceof Number) {
                dashboardId = ((Number) requestBody.get("dashboardId")).intValue();
            }

            if (dashboardId == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Dashboard ID is required");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            // Verify dashboard belongs to user
            if (!dashboardBelongsToUser(dashboardId, userId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                Map<String, String> error = new HashMap<>();
                error.put("error", "You don't have access to this dashboard");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            // Check if already default
            if (isDefaultDashboard(dashboardId)) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Dashboard is already set as default");
                objectMapper.writeValue(response.getWriter(), result);
                return;
            }

            // Set as default
            setDashboardAsDefault(dashboardId, userId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Dashboard set as default successfully");
            objectMapper.writeValue(response.getWriter(), result);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private boolean dashboardBelongsToUser(Integer dashboardId, Integer userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT 1 FROM dashboards
                WHERE ID = ? AND Created_By = ?
                LIMIT 1
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, dashboardId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    private boolean isDefaultDashboard(Integer dashboardId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT Is_Default FROM dashboards
                WHERE ID = ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, dashboardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("Is_Default") == 1;
                    }
                }
            }
        }
        return false;
    }

    private void setDashboardAsDefault(Integer dashboardId, Integer userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Set this dashboard as default
                // Note: isDefault is independent for each dashboard - it only controls tab visibility after reload
                // Setting one dashboard as default does not affect other dashboards
                String setSql = """
                    UPDATE dashboards
                    SET Is_Default = 1
                    WHERE ID = ? AND Created_By = ?
                """;
                try (PreparedStatement ps = conn.prepareStatement(setSql)) {
                    ps.setInt(1, dashboardId);
                    ps.setInt(2, userId);
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

