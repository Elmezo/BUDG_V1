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
 * Dashboard widget: Change Requests
 * Returns change requests by age for current user (contributing or raised by me)
 * Note: This is a placeholder implementation as the change request table structure is not fully defined
 */
@WebServlet(name = "DashboardChangeRequestsServlet", urlPatterns = "/api/dashboard/change-requests")
public class DashboardChangeRequestsServlet extends HttpServlet {

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

        String type = request.getParameter("type");
        if (type == null || type.isEmpty()) {
            type = "contributing";
        }

        try {
            Map<String, Object> result = getChangeRequests(userId, type);
            objectMapper.writeValue(response.getWriter(), result);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private Map<String, Object> getChangeRequests(int userId, String type) throws SQLException {
        List<Map<String, Object>> ageGroups = new ArrayList<>();
        
        // Initialize age groups with zero counts
        Map<String, Object> lessThan7 = new HashMap<>();
        lessThan7.put("ageRange", "Less than 7 days");
        lessThan7.put("count", 0);
        ageGroups.add(lessThan7);
        
        Map<String, Object> between7And30 = new HashMap<>();
        between7And30.put("ageRange", "7 to 30 days");
        between7And30.put("count", 0);
        ageGroups.add(between7And30);
        
        Map<String, Object> moreThan30 = new HashMap<>();
        moreThan30.put("ageRange", "More than 30 days");
        moreThan30.put("count", 0);
        ageGroups.add(moreThan30);
        
        String sql;
        if ("raised".equals(type)) {
            // Change requests raised by the user
            sql = """
                SELECT 
                    CASE 
                        WHEN DATEDIFF(NOW(), cr.Created_At) < 7 THEN 'Less than 7 days'
                        WHEN DATEDIFF(NOW(), cr.Created_At) BETWEEN 7 AND 30 THEN '7 to 30 days'
                        ELSE 'More than 30 days'
                    END AS age_range,
                    COUNT(*) AS count
                FROM changerequest cr
                WHERE cr.Created_By = ? AND cr.Deleted_At IS NULL
                GROUP BY age_range
            """;
        } else {
            // Change requests where user is a stakeholder (contributing)
            sql = """
                SELECT 
                    CASE 
                        WHEN DATEDIFF(NOW(), cr.Created_At) < 7 THEN 'Less than 7 days'
                        WHEN DATEDIFF(NOW(), cr.Created_At) BETWEEN 7 AND 30 THEN '7 to 30 days'
                        ELSE 'More than 30 days'
                    END AS age_range,
                    COUNT(DISTINCT cr.ID) AS count
                FROM changerequest cr
                INNER JOIN cr_stakeholders crs ON cr.ID = crs.CR_ID
                WHERE crs.User_ID = ? AND cr.Deleted_At IS NULL
                GROUP BY age_range
            """;
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, userId);
            
            try (ResultSet rs = ps.executeQuery()) {
                int total = 0;
                while (rs.next()) {
                    String ageRange = rs.getString("age_range");
                    int count = rs.getInt("count");
                    total += count;
                    
                    // Update the corresponding age group
                    for (Map<String, Object> ageGroup : ageGroups) {
                        if (ageRange.equals(ageGroup.get("ageRange"))) {
                            ageGroup.put("count", count);
                            break;
                        }
                    }
                }
                
                Map<String, Object> result = new HashMap<>();
                result.put("ageGroups", ageGroups);
                result.put("total", total);
                result.put("type", type);
                
                return result;
            }
        }
    }
}

