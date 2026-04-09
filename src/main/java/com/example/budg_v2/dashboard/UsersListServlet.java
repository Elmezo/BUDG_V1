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
 * Users List Servlet
 * GET /api/users/list - Get list of all users for sharing
 */
@WebServlet(name = "UsersListServlet", urlPatterns = "/api/users/list")
public class UsersListServlet extends HttpServlet {

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
            String searchTerm = request.getParameter("search");
            List<Map<String, Object>> users = getUsers(searchTerm, userId);
            objectMapper.writeValue(response.getWriter(), users);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Server error: " + e.getMessage());
            e.printStackTrace();
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private List<Map<String, Object>> getUsers(String searchTerm, int currentUserId) throws SQLException {
        List<Map<String, Object>> users = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            StringBuilder sql = new StringBuilder("""
                SELECT 
                    p.ID,
                    COALESCE(
                        NULLIF(TRIM(CONCAT(COALESCE(p.First_Name, ''), ' ', COALESCE(p.Last_Name, ''))), ''),
                        COALESCE(p.First_Name, p.Last_Name, 'Unknown User')
                    ) as name,
                    COALESCE(p.Email, '') as Email,
                    COALESCE(p.Function_Name, '') as Function_Name,
                    COALESCE(ou.Name, '') as org_unit
                FROM people p
                LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
                WHERE p.ID != ?
            """);
            
            List<Object> params = new ArrayList<>();
            params.add(currentUserId);
            
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                sql.append(" AND (")
                   .append("CONCAT(p.First_Name, ' ', p.Last_Name) LIKE ? OR ")
                   .append("p.Email LIKE ? OR ")
                   .append("p.Function_Name LIKE ? OR ")
                   .append("ou.Name LIKE ?")
                   .append(")");
                
                String searchPattern = "%" + searchTerm.trim() + "%";
                params.add(searchPattern);
                params.add(searchPattern);
                params.add(searchPattern);
                params.add(searchPattern);
            }
            
            sql.append(" ORDER BY p.Last_Name, p.First_Name LIMIT 50");
            
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("id", rs.getInt("ID"));
                        user.put("name", rs.getString("name"));
                        user.put("email", rs.getString("Email"));
                        user.put("function", rs.getString("Function_Name"));
                        user.put("orgUnit", rs.getString("org_unit"));
                        users.add(user);
                    }
                }
            }
        }
        
        return users;
    }
}
