package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
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

@WebServlet(name = "AdminSavedSearchServlet", urlPatterns = {"/admin/api/savedsearches"})
public class AdminSavedSearchServlet extends HttpServlet {

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

        // Returns is_public, share_type, and user_reference so the Quick Links UI
        // can show sharing badges and apply the correct confirmation modal logic.
        // share_type values:
        //   'public'         - is_public = 1
        //   'selected_users' - is_public = 0 AND rows exist in user_x_search
        //   'private'        - is_public = 0 AND no rows in user_x_search
        String sql = "SELECT " +
                "us.id, " +
                "us.name, " +
                "us.description, " +
                "DATE_FORMAT(us.created_at, '%d-%b-%Y') AS created_on, " +
                "COALESCE(CONCAT(p.First_Name, ' ', p.Last_Name, ' (', p.Email, ')'), 'Unknown') AS created_by, " +
                "us.user_reference, " +
                "us.is_public, " +
                "CASE " +
                "  WHEN us.is_public = 1 THEN 'public' " +
                "  WHEN us.is_public = 0 " +
                "       AND EXISTS (SELECT 1 FROM user_x_search x WHERE x.search_id = us.id) THEN 'selected_users' " +
                "  ELSE 'private' " +
                "END AS share_type " +
                "FROM user_search us " +
                "LEFT JOIN people p ON p.ID = us.user_reference " +
                "ORDER BY us.created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Map<String, Object>> savedSearches = new ArrayList<>();

            while (rs.next()) {
                Map<String, Object> search = new HashMap<>();
                search.put("id", rs.getInt("id"));
                search.put("name", rs.getString("name"));
                search.put("description", rs.getString("description"));
                search.put("created_on", rs.getString("created_on"));
                search.put("created_by", rs.getString("created_by"));
                search.put("user_reference", rs.getInt("user_reference"));
                search.put("is_public", rs.getBoolean("is_public"));
                search.put("share_type", rs.getString("share_type"));
                savedSearches.add(search);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", savedSearches);
            resp.getWriter().write(gson.toJson(result));

        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }
}
