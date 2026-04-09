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
 * Dashboard widget: Saved Searches
 * Returns recent and most viewed saved searches
 */
@WebServlet(name = "DashboardSavedSearchesServlet", urlPatterns = "/api/dashboard/saved-searches")
public class DashboardSavedSearchesServlet extends HttpServlet {

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
            Map<String, Object> result = getSavedSearches(userId);
            objectMapper.writeValue(response.getWriter(), result);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            try {
                objectMapper.writeValue(response.getWriter(), error);
            } catch (IOException ioException) {
                response.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error: " + e.getMessage());
            try {
                objectMapper.writeValue(response.getWriter(), error);
            } catch (IOException ioException) {
                response.getWriter().write("{\"error\":\"Error: " + e.getMessage() + "\"}");
            }
        }
    }

    private Map<String, Object> getSavedSearches(int userId) throws SQLException {
        List<Map<String, Object>> recent = new ArrayList<>();
        List<Map<String, Object>> mostViewed = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get recent searches from user_search_usage joined with user_search
            // Group by search ID to get the most recent visit for each search
            String recentSql = """
                SELECT 
                    us.id,
                    us.name,
                    us.description,
                    DATE_FORMAT(MAX(usu.visited_at), '%d-%b-%Y') AS visited_on,
                    MAX(usu.visited_at) AS visited_at
                FROM user_search_usage usu
                INNER JOIN user_search us ON usu.search_reference = us.id
                WHERE (
                    us.user_reference = ? 
                    OR us.is_public = 1 
                    OR EXISTS (
                        SELECT 1 FROM user_x_search uxs 
                        WHERE uxs.search_id = us.id AND uxs.user_reference = ?
                    )
                )
                AND usu.user_reference = ?
                GROUP BY us.id, us.name, us.description
                ORDER BY MAX(usu.visited_at) DESC
                LIMIT 5
            """;

            try (PreparedStatement ps = conn.prepareStatement(recentSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                ps.setInt(3, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> search = new HashMap<>();
                        search.put("id", rs.getInt("id"));
                        search.put("name", rs.getString("name"));
                        search.put("description", rs.getString("description"));
                        search.put("visitedOn", rs.getString("visited_on"));
                        recent.add(search);
                    }
                }
            }

            // Get most viewed searches ordered by hitcount
            String mostViewedSql = """
                SELECT 
                    us.id,
                    us.name,
                    us.description,
                    COALESCE(us.hitcount, 0) AS hitcount,
                    DATE_FORMAT(us.created_at, '%d-%b-%Y') AS created_on
                FROM user_search us
                WHERE (
                    us.user_reference = ? 
                    OR us.is_public = 1 
                    OR EXISTS (
                        SELECT 1 FROM user_x_search uxs 
                        WHERE uxs.search_id = us.id AND uxs.user_reference = ?
                    )
                )
                ORDER BY COALESCE(us.hitcount, 0) DESC
                LIMIT 5
            """;

            try (PreparedStatement ps = conn.prepareStatement(mostViewedSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> search = new HashMap<>();
                        search.put("id", rs.getInt("id"));
                        search.put("name", rs.getString("name"));
                        search.put("description", rs.getString("description"));
                        search.put("hitcount", rs.getInt("hitcount"));
                        search.put("createdOn", rs.getString("created_on"));
                        mostViewed.add(search);
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("recent", recent);
        result.put("mostViewed", mostViewed);
        
        return result;
    }

}


