package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
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
 * Servlet for retrieving follow types.
 */
@WebServlet("/api/follow-types")
public class FollowTypeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            List<Map<String, Object>> followTypes = getFollowTypes();
            
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), followTypes);

        } catch (Exception ex) {
            ex.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = Map.of("error", "Internal server error: " + ex.getMessage());
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private List<Map<String, Object>> getFollowTypes() throws SQLException {
        List<Map<String, Object>> followTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT id, Name, Description FROM follow_type ORDER BY id";
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> followType = new HashMap<>();
                        followType.put("id", rs.getInt("id"));
                        followType.put("name", rs.getString("Name"));
                        followType.put("description", rs.getString("Description"));
                        followTypes.add(followType);
                    }
                }
            }
            
            if (followTypes.isEmpty()) {
                throw new SQLException("No follow types found in database. Please check if the follow_type table has data.");
            }
        }
        
        return followTypes;
    }
    
}

