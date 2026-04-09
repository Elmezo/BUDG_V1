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
 * Dashboard List Servlet
 * GET /api/dashboard/list - Get all dashboards created by the current user
 */
@WebServlet(name = "DashboardListServlet", urlPatterns = "/api/dashboard/list")
public class DashboardListServlet extends HttpServlet {

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
            List<Map<String, Object>> dashboards = getDashboardsByUser(userId);
            objectMapper.writeValue(response.getWriter(), dashboards);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private List<Map<String, Object>> getDashboardsByUser(Integer userId) throws SQLException {
        List<Map<String, Object>> dashboards = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get dashboards that are:
            // 1. Created by the user (owned)
            // 2. Public (Is_Public = 1) - visible to all users
            // 3. Shared with the user via dashboard_x_user table
            String sql = """
                SELECT DISTINCT d.ID, d.Title, d.Description, d.Is_Default, d.Is_Public, d.Created_By
                FROM dashboards d
                LEFT JOIN dashboard_x_user dxu ON d.ID = dxu.Dashboard_ID AND dxu.User_ID = ?
                WHERE d.Created_By = ? 
                   OR d.Is_Public = 1 
                   OR dxu.Dashboard_ID IS NOT NULL
                ORDER BY d.Is_Default DESC, d.Title ASC
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> dashboard = new HashMap<>();
                        dashboard.put("id", rs.getInt("ID"));
                        dashboard.put("title", rs.getString("Title"));
                        dashboard.put("description", rs.getString("Description"));
                        dashboard.put("isDefault", rs.getInt("Is_Default") == 1);
                        dashboard.put("isPublic", rs.getInt("Is_Public") == 1);
                        dashboard.put("createdBy", rs.getInt("Created_By"));
                        dashboard.put("isOwner", rs.getInt("Created_By") == userId);
                        dashboards.add(dashboard);
                    }
                }
            }
        }
        return dashboards;
    }
}

