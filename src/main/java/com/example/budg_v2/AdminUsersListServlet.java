package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
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
 * GET /admin/api/users/list
 *
 * Returns active, non-deleted users for the Quick Links assign-mode picker.
 *
 * Super Admin: receives all active users (all roles).
 * Admin: receives only WebUsers (filtered server-side).
 *
 * Each user entry includes the role so the frontend can display
 * "First Last (Role)" in the dropdown.
 */
@WebServlet(name = "AdminUsersListServlet", urlPatterns = {"/admin/api/users/list"})
public class AdminUsersListServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String callerRole = (String) req.getAttribute("userRole");
        boolean isSuperAdmin = AppRoleNames.isSuperAdminName(callerRole);

        // Base query — Super Admin gets all roles; Admin gets WebUsers only
        String sql = "SELECT p.ID, p.First_Name, p.Last_Name, p.Email, r.primaryname AS role " +
                "FROM people p " +
                "JOIN role r ON p.System_Role = r.id " +
                "JOIN i_user iu ON iu.reference = p.ID " +
                "WHERE iu.active = 1 " +
                "  AND p.Deleted_date IS NULL" +
                (isSuperAdmin ? "" : " AND r.primaryname = 'WebUser'") +
                " ORDER BY p.First_Name, p.Last_Name, p.ID";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Map<String, Object>> users = new ArrayList<>();

            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getInt("ID"));
                user.put("firstName", rs.getString("First_Name"));
                user.put("lastName", rs.getString("Last_Name"));
                user.put("email", rs.getString("Email"));
                user.put("role", rs.getString("role"));
                users.add(user);
            }

            resp.getWriter().write(gson.toJson(users));

        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }
}
