package com.example.budg_v2.dashboard;

import com.example.budg_v2.database.DatabaseConnection;
import com.fasterxml.jackson.databind.JsonNode;
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
 * GET/PUT /api/dashboard/layout-prefs?key=main|{dashboardId}
 * Body (PUT): {"widgetOrder":["objectCounts",...],"hiddenWidgets":["pendingTasks",...]}
 */
@WebServlet(name = "DashboardLayoutPrefsServlet", urlPatterns = "/api/dashboard/layout-prefs")
public class DashboardLayoutPrefsServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeError(response, "User not authenticated");
            return;
        }

        String key = request.getParameter("key");
        if (key == null || key.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "Missing key parameter (main or dashboard id)");
            return;
        }
        key = key.trim();
        if (!isValidDashboardKey(key)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "Invalid key");
            return;
        }

        try {
            String json = loadLayoutJson(userId, key);
            if (json == null || json.isBlank()) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(json);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(response, "Database error");
            e.printStackTrace();
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeError(response, "User not authenticated");
            return;
        }

        String key = request.getParameter("key");
        if (key == null || key.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "Missing key parameter");
            return;
        }
        key = key.trim();
        if (!isValidDashboardKey(key)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "Invalid key");
            return;
        }

        JsonNode body;
        try {
            body = objectMapper.readTree(request.getInputStream());
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "Invalid JSON body");
            return;
        }

        if (!body.has("widgetOrder") || !body.has("hiddenWidgets")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "Body must include widgetOrder and hiddenWidgets arrays");
            return;
        }

        if (!body.get("widgetOrder").isArray() || !body.get("hiddenWidgets").isArray()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "widgetOrder and hiddenWidgets must be arrays");
            return;
        }

        String layoutJson = objectMapper.writeValueAsString(body);

        try {
            upsertLayout(userId, key, layoutJson);
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), ok);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(response, "Database error");
            e.printStackTrace();
        }
    }

    private static boolean isValidDashboardKey(String key) {
        if ("main".equalsIgnoreCase(key)) {
            return true;
        }
        try {
            int id = Integer.parseInt(key);
            return id > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String loadLayoutJson(int userId, String dashboardKey) throws SQLException {
        String sql = "SELECT Layout_JSON FROM user_dashboard_layout_prefs WHERE User_ID = ? AND Dashboard_Key = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, dashboardKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Layout_JSON");
                }
            }
        }
        return null;
    }

    private void upsertLayout(int userId, String dashboardKey, String layoutJson) throws SQLException {
        String sql = """
            INSERT INTO user_dashboard_layout_prefs (User_ID, Dashboard_Key, Layout_JSON)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE Layout_JSON = VALUES(Layout_JSON), Updated_At = CURRENT_TIMESTAMP
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, dashboardKey);
            ps.setString(3, layoutJson);
            ps.executeUpdate();
        }
    }

    private void writeError(HttpServletResponse response, String message) throws IOException {
        Map<String, String> err = new HashMap<>();
        err.put("error", message);
        objectMapper.writeValue(response.getWriter(), err);
    }
}
