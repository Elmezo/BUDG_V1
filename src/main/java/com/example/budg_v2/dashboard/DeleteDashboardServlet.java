package com.example.budg_v2.dashboard;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.WorkflowNotificationService;
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
 * Delete Dashboard Servlet
 * DELETE /api/dashboard/delete?id={dashboardId} - Delete a dashboard
 */
@WebServlet(name = "DeleteDashboardServlet", urlPatterns = "/api/dashboard/delete")
public class DeleteDashboardServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowNotificationService notificationService = new WorkflowNotificationService();

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
            // Get dashboard ID from request parameter
            String dashboardIdParam = request.getParameter("id");
            if (dashboardIdParam == null || dashboardIdParam.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Dashboard ID is required");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            Integer dashboardId;
            try {
                dashboardId = Integer.parseInt(dashboardIdParam);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid dashboard ID");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            // Verify user owns this dashboard
            if (!userOwnsDashboard(dashboardId, userId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                Map<String, String> error = new HashMap<>();
                error.put("error", "You do not have permission to delete this dashboard");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            // Collect sharing info before deletion so we can notify affected users
            String dashboardTitle = getDashboardTitle(dashboardId);
            List<Integer> sharedUserIds = getSharedUserIds(dashboardId);

            // Delete dashboard (allow deletion of default dashboards - they're just custom dashboards marked as default)
            deleteDashboard(dashboardId, userId);

            // Notify previously-shared users that the dashboard has been removed
            for (Integer sharedUserId : sharedUserIds) {
                try {
                    notificationService.sendDashboardRemovedNotification(dashboardId, dashboardTitle, sharedUserId);
                } catch (Exception e) {
                    System.err.println("[DeleteDashboard] Failed to send removal notification to user " + sharedUserId + ": " + e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Dashboard deleted successfully");
            objectMapper.writeValue(response.getWriter(), result);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private String getDashboardTitle(Integer dashboardId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT Title FROM dashboards WHERE ID = ?")) {
            ps.setInt(1, dashboardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String title = rs.getString("Title");
                    return title != null ? title : "Dashboard #" + dashboardId;
                }
            }
        } catch (Exception e) {
            System.err.println("[DeleteDashboard] Could not resolve dashboard title: " + e.getMessage());
        }
        return "Dashboard #" + dashboardId;
    }

    private List<Integer> getSharedUserIds(Integer dashboardId) {
        List<Integer> userIds = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT User_ID FROM dashboard_x_user WHERE Dashboard_ID = ?")) {
            ps.setInt(1, dashboardId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    userIds.add(rs.getInt("User_ID"));
                }
            }
        } catch (Exception e) {
            System.err.println("[DeleteDashboard] Could not fetch shared user IDs: " + e.getMessage());
        }
        return userIds;
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

    @SuppressWarnings("unused")
    private boolean isDefaultDashboard(Integer dashboardId, Integer userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT Is_Default
                FROM dashboards
                WHERE ID = ? AND Created_By = ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, dashboardId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("Is_Default") == 1;
                    }
                }
            }
        }
        return false;
    }

    private void deleteDashboard(Integer dashboardId, Integer userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete dashboard widgets
                String deleteWidgetsSql = """
                    DELETE FROM dashboard_widgets
                    WHERE Dashboard_ID = ?
                """;
                try (PreparedStatement ps = conn.prepareStatement(deleteWidgetsSql)) {
                    ps.setInt(1, dashboardId);
                    ps.executeUpdate();
                }

                // Delete dashboard user associations
                String deleteUserAssocSql = """
                    DELETE FROM dashboard_x_user
                    WHERE Dashboard_ID = ?
                """;
                try (PreparedStatement ps = conn.prepareStatement(deleteUserAssocSql)) {
                    ps.setInt(1, dashboardId);
                    ps.executeUpdate();
                }

                // Delete dashboard
                String deleteDashboardSql = """
                    DELETE FROM dashboards
                    WHERE ID = ? AND Created_By = ?
                """;
                try (PreparedStatement ps = conn.prepareStatement(deleteDashboardSql)) {
                    ps.setInt(1, dashboardId);
                    ps.setInt(2, userId);
                    int rowsAffected = ps.executeUpdate();
                    if (rowsAffected == 0) {
                        throw new SQLException("Dashboard not found or access denied");
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}

