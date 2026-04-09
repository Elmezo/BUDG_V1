package com.example.budg_v2.dashboard;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentAccessService;
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
 * Dashboard widget: Team
 * Returns team structure (management, peers, reportees) for current user
 */
@WebServlet(name = "DashboardTeamServlet", urlPatterns = "/api/dashboard/team")
public class DashboardTeamServlet extends HttpServlet {

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
            Map<String, Object> result = getTeamData(userId);
            objectMapper.writeValue(response.getWriter(), result);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private Map<String, Object> getTeamData(int personId) throws SQLException {
        Map<String, Object> teamData = new HashMap<>();
        
        teamData.put("management", getManagement(personId));
        teamData.put("peers", getPeers(personId));
        teamData.put("reportees", getReportees(personId));
        
        return teamData;
    }

    private List<Map<String, Object>> getManagement(int personId) throws SQLException {
        List<Map<String, Object>> management = new ArrayList<>();
        
        // Build segment filter condition
        String segmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(personId, "InvolvedParty", "p.ID");
        
        String sql = """
            SELECT DISTINCT
                p.ID,
                p.First_Name,
                p.Last_Name,
                p.Email,
                p.Function_Name
            FROM people_x_people pxp
            LEFT JOIN people p ON pxp.Manager = p.ID
            WHERE pxp.Employee = ?
            AND p.Deleted_date IS NULL
            """ + (segmentFilter != null && !segmentFilter.isEmpty() ? "AND " + segmentFilter : "") + """
            ORDER BY p.First_Name, p.Last_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, personId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> manager = new HashMap<>();
                    manager.put("id", rs.getInt("ID"));
                    manager.put("firstName", rs.getString("First_Name"));
                    manager.put("lastName", rs.getString("Last_Name"));
                    manager.put("email", rs.getString("Email"));
                    manager.put("functionName", rs.getString("Function_Name"));
                    management.add(manager);
                }
            }
        }
        
        return management;
    }

    private List<Map<String, Object>> getPeers(int personId) throws SQLException {
        List<Map<String, Object>> peers = new ArrayList<>();
        
        // Build segment filter condition
        String segmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(personId, "InvolvedParty", "p2.ID");
        
        String sql = """
            SELECT DISTINCT
                p2.ID,
                p2.First_Name,
                p2.Last_Name,
                p2.Email,
                p2.Function_Name
            FROM people_x_people pxp1
            LEFT JOIN people_x_people pxp2 ON pxp1.Manager = pxp2.Manager 
                AND pxp1.ipXip_RelationType = pxp2.ipXip_RelationType
            LEFT JOIN people p2 ON pxp2.Employee = p2.ID
            WHERE pxp1.Employee = ?
            AND p2.ID != ?
            AND p2.Deleted_date IS NULL
            """ + (segmentFilter != null && !segmentFilter.isEmpty() ? "AND " + segmentFilter : "") + """
            ORDER BY p2.First_Name, p2.Last_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, personId);
            pstmt.setInt(2, personId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> peer = new HashMap<>();
                    peer.put("id", rs.getInt("ID"));
                    peer.put("firstName", rs.getString("First_Name"));
                    peer.put("lastName", rs.getString("Last_Name"));
                    peer.put("email", rs.getString("Email"));
                    peer.put("functionName", rs.getString("Function_Name"));
                    peers.add(peer);
                }
            }
        }
        
        return peers;
    }

    private List<Map<String, Object>> getReportees(int personId) throws SQLException {
        List<Map<String, Object>> reportees = new ArrayList<>();
        
        // Build segment filter condition
        String segmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(personId, "InvolvedParty", "p.ID");
        
        String sql = """
            SELECT DISTINCT
                p.ID,
                p.First_Name,
                p.Last_Name,
                p.Email,
                p.Function_Name
            FROM people_x_people pxp
            LEFT JOIN people p ON pxp.Employee = p.ID
            WHERE pxp.Manager = ?
            AND p.Deleted_date IS NULL
            """ + (segmentFilter != null && !segmentFilter.isEmpty() ? "AND " + segmentFilter : "") + """
            ORDER BY p.First_Name, p.Last_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, personId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> reportee = new HashMap<>();
                    reportee.put("id", rs.getInt("ID"));
                    reportee.put("firstName", rs.getString("First_Name"));
                    reportee.put("lastName", rs.getString("Last_Name"));
                    reportee.put("email", rs.getString("Email"));
                    reportee.put("functionName", rs.getString("Function_Name"));
                    reportees.add(reportee);
                }
            }
        }
        
        return reportees;
    }
}

