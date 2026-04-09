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
 * Create Dashboard Servlet
 * POST /api/dashboard/create - Create a new dashboard
 */
@WebServlet(name = "CreateDashboardServlet", urlPatterns = "/api/dashboard/create")
public class CreateDashboardServlet extends HttpServlet {

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
            String title = (String) requestBody.get("name");
            String description = (String) requestBody.get("description");
            Boolean setAsDefault = (Boolean) requestBody.get("setAsDefault");
            
            if (title == null || title.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Title is required");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            if (description == null || description.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Description is required");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            // Create dashboard
            Integer dashboardId = createDashboard(userId, title, description, setAsDefault != null && setAsDefault);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("dashboardId", dashboardId);
            result.put("message", "Dashboard created successfully");
            objectMapper.writeValue(response.getWriter(), result);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private Integer createDashboard(Integer userId, String title, String description, boolean setAsDefault) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Insert new dashboard
                // Note: isDefault is independent for each dashboard - it only controls tab visibility after reload
                // Setting one dashboard as default does not affect other dashboards
                String insertSql = """
                    INSERT INTO dashboards (Title, Description, Is_Public, Is_Default, Created_By, Layout_Type)
                    VALUES (?, ?, 0, ?, ?, 'grid')
                """;
                
                Integer dashboardId = null;
                try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, title);
                    ps.setString(2, description);
                    ps.setInt(3, setAsDefault ? 1 : 0);
                    ps.setInt(4, userId);
                    ps.executeUpdate();
                    
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            dashboardId = rs.getInt(1);
                        }
                    }
                }

                // Do NOT insert into dashboard_x_user when creating dashboard
                // The dashboard_x_user table is only used when sharing with other users
                // Ownership is tracked via Created_By in dashboards table

                conn.commit();
                return dashboardId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}

