package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.UserContextUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/team/*")
public class PersonTeamServlet extends HttpServlet {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Helper method to parse integer values from JSON
     */
    private Integer parseInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            // Extract person ID from URL path
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || !pathInfo.startsWith("/")) {
                sendErrorResponse(response, "Invalid person ID", 400);
                return;
            }
            
            // Path format: /{personId}
            String personIdStr = pathInfo.substring(1);
            if (personIdStr.isEmpty()) {
                sendErrorResponse(response, "Person ID is required", 400);
                return;
            }
            
            int personId = Integer.parseInt(personIdStr);
            
            // Get team data for the person
            Map<String, Object> teamData = getPersonTeamData(personId);
            
            // Send response
            response.setStatus(200);
            objectMapper.writeValue(response.getWriter(), teamData);
            
        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid person ID format", 400);
        } catch (Exception e) {
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            // Parse request body
            Map<String, Object> requestData = objectMapper.readValue(request.getReader(), new TypeReference<Map<String, Object>>() {});
            
            String pathInfo = request.getPathInfo();
            if (pathInfo == null) {
                sendErrorResponse(response, "Invalid endpoint", 400);
                return;
            }
            
            // Route based on path
            if (pathInfo.equals("/management")) {
                addManagementRelationship(request, requestData, response);
            } else if (pathInfo.equals("/reports")) {
                addReportsRelationship(request, requestData, response);
            } else {
                sendErrorResponse(response, "Invalid endpoint", 400);
            }
            
        } catch (Exception e) {
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
            e.printStackTrace();
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            // Parse request body
            Map<String, Object> requestData = objectMapper.readValue(request.getReader(), new TypeReference<Map<String, Object>>() {});
            
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || !pathInfo.startsWith("/relationship/")) {
                sendErrorResponse(response, "Invalid endpoint", 400);
                return;
            }
            
            // Extract relationship ID from path: /relationship/{relationshipId}
            String relationshipIdStr = pathInfo.substring("/relationship/".length());
            int relationshipId = Integer.parseInt(relationshipIdStr);
            
            updateTeamRelationship(request, relationshipId, requestData, response);
            
        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid relationship ID format", 400);
        } catch (Exception e) {
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
            e.printStackTrace();
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || !pathInfo.startsWith("/relationship/")) {
                sendErrorResponse(response, "Invalid endpoint", 400);
                return;
            }
            
            // Extract relationship ID from path: /relationship/{relationshipId}
            String relationshipIdStr = pathInfo.substring("/relationship/".length());
            int relationshipId = Integer.parseInt(relationshipIdStr);
            
            removeTeamRelationship(request, relationshipId, response);
            
        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid relationship ID format", 400);
        } catch (Exception e) {
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
            e.printStackTrace();
        }
    }
    
    /**
     * Get team data for a person (management, peers, reports)
     */
    private Map<String, Object> getPersonTeamData(int personId) throws SQLException {
        Map<String, Object> teamData = new HashMap<>();
        
        // Get management (who this person reports to)
        List<Map<String, Object>> management = getManagement(personId);
        
        // Get peers (people who report to the same managers)
        List<Map<String, Object>> peers = getPeers(personId);
        
        // Get reports (who reports to this person)
        List<Map<String, Object>> reports = getReports(personId);
        
        teamData.put("management", management);
        teamData.put("peers", peers);
        teamData.put("reports", reports);
        
        return teamData;
    }
    
    /**
     * Get management for a person (who they report to)
     */
    private List<Map<String, Object>> getManagement(int personId) throws SQLException {
        List<Map<String, Object>> management = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT
                pxp.ID AS Relationship_ID,
                p.ID,
                p.First_Name,
                p.Last_Name,
                p.Email,
                pd.office_telephone AS Telephone,
                pd.mobile_telephone AS Mobile,
                p.Function_Name,
                p.Org_Unit_ID,
                ou.Name AS Org_Unit_Name,
                prt.primaryname AS Relation_Type
            FROM people_x_people pxp
            LEFT JOIN people p ON pxp.Manager = p.ID
            LEFT JOIN people_details pd ON p.ip_details = pd.id
            LEFT JOIN people_x_people_relationtype prt ON pxp.ipXip_RelationType = prt.ID
            LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
            WHERE pxp.Employee = ?
            AND p.Deleted_date IS NULL
            ORDER BY p.First_Name, p.Last_Name;
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, personId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> manager = new HashMap<>();
                    String relationType = rs.getString("Relation_Type");
                    manager.put("relationshipId", rs.getInt("Relationship_ID"));
                    manager.put("id", rs.getInt("ID"));
                    manager.put("firstName", rs.getString("First_Name"));
                    manager.put("lastName", rs.getString("Last_Name"));
                    manager.put("email", rs.getString("Email"));
                    manager.put("telephone", rs.getString("Telephone"));
                    manager.put("mobile", rs.getString("Mobile"));
                    manager.put("functionName", rs.getString("Function_Name"));
                    manager.put("orgUnitId", rs.getInt("Org_Unit_ID"));
                    manager.put("orgUnitName", rs.getString("Org_Unit_Name"));
                    manager.put("relationType", relationType);
                    manager.put("type", relationType != null ? relationType : "Direct report");
                    
                    management.add(manager);
                }
            }
        }
        
        return management;
    }
    
    /**
     * Get peers for a person (people who report to the same managers)
     */
    private List<Map<String, Object>> getPeers(int personId) throws SQLException {
        List<Map<String, Object>> peers = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT
                p2.ID,
                p2.First_Name,
                p2.Last_Name,
                p2.Email,
                pd2.office_telephone AS Telephone,
                pd2.mobile_telephone AS Mobile,
                p2.Function_Name,
                p2.Org_Unit_ID,
                ou.Name AS Org_Unit_Name
            FROM people_x_people pxp1
            LEFT JOIN people_x_people pxp2 ON pxp1.Manager = pxp2.Manager 
                AND pxp1.ipXip_RelationType = pxp2.ipXip_RelationType
            LEFT JOIN people p2 ON pxp2.Employee = p2.ID
            LEFT JOIN people_details pd2 ON p2.ID = pd2.id
            LEFT JOIN people_x_people_relationtype prt ON pxp1.ipXip_RelationType = prt.ID
            LEFT JOIN org_unit ou ON p2.Org_Unit_ID = ou.ID
            WHERE pxp1.Employee = ?
            AND p2.ID != ?
            AND p2.Deleted_date IS NULL
            ORDER BY p2.First_Name, p2.Last_Name;
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
                    peer.put("telephone", rs.getString("Telephone"));
                    peer.put("mobile", rs.getString("Mobile"));
                    peer.put("functionName", rs.getString("Function_Name"));
                    peer.put("orgUnitId", rs.getInt("Org_Unit_ID"));
                    peer.put("orgUnitName", rs.getString("Org_Unit_Name"));
                    
                    peers.add(peer);
                }
            }
        }
        
        return peers;
    }
    
    /**
     * Get reports for a person (who reports to this person)
     */
    private List<Map<String, Object>> getReports(int personId) throws SQLException {
        List<Map<String, Object>> reports = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT
                pxp.ID AS Relationship_ID,
                p.ID,
                p.First_Name,
                p.Last_Name,
                p.Email,
                pd.office_telephone AS Telephone,
                pd.mobile_telephone AS Mobile,
                p.Function_Name,
                p.Org_Unit_ID,
                ou.Name AS Org_Unit_Name,
                prt.primaryname AS Relation_Type
            FROM people_x_people pxp
            LEFT JOIN people p ON pxp.Employee = p.ID
            LEFT JOIN people_details pd ON p.ip_details = pd.id
            LEFT JOIN people_x_people_relationtype prt ON pxp.ipXip_RelationType = prt.ID
            LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
            WHERE pxp.Manager = ?
            AND p.Deleted_date IS NULL
            ORDER BY p.First_Name, p.Last_Name;
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, personId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> report = new HashMap<>();
                    String relationType = rs.getString("Relation_Type");
                    report.put("relationshipId", rs.getInt("Relationship_ID"));
                    report.put("id", rs.getInt("ID"));
                    report.put("firstName", rs.getString("First_Name"));
                    report.put("lastName", rs.getString("Last_Name"));
                    report.put("email", rs.getString("Email"));
                    report.put("telephone", rs.getString("Telephone"));
                    report.put("mobile", rs.getString("Mobile"));
                    report.put("functionName", rs.getString("Function_Name"));
                    report.put("orgUnitId", rs.getInt("Org_Unit_ID"));
                    report.put("orgUnitName", rs.getString("Org_Unit_Name"));
                    report.put("relationType", relationType);
                    report.put("type", relationType != null ? relationType : "Direct report");
                    
                    reports.add(report);
                }
            }
        }
        
        return reports;
    }
    
    /**
     * Add management relationship (person reports to manager)
     */
    private void addManagementRelationship(HttpServletRequest request, Map<String, Object> requestData, HttpServletResponse response) 
            throws IOException, SQLException {
        
        // Debug: Log received data
        //system.out.println("Received management relationship data: " + requestData);
        
        // Extract data from request
        Integer personId = parseInteger(requestData.get("person_id"));
        Integer managerId = parseInteger(requestData.get("manager_id"));
        String relationshipType = (String) requestData.get("relationship_type");
        String description = (String) requestData.get("description");
        
        //system.out.println("Parsed data - personId: " + personId + ", managerId: " + managerId + ", relationshipType: " + relationshipType);
        
        // Validate required fields
        if (personId == null || managerId == null) {
            sendErrorResponse(response, "person_id and manager_id are required", 400);
            return;
        }
        
        // Get current user ID
        int currentUserId = UserContextUtil.getCurrentUserId(request);
        
        // Get relation type ID
        int relationTypeId = getRelationTypeId(relationshipType);
        
        // Insert management relationship
        String sql = """
            INSERT INTO people_x_people (Employee, Manager, Description, ipXip_RelationType, Created_Datetime, Last_Update_UserID)
            VALUES (?, ?, ?, ?, NOW(), ?)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, personId);
            pstmt.setInt(2, managerId);
            pstmt.setString(3, description != null ? description : "");
            pstmt.setInt(4, relationTypeId);
            pstmt.setInt(5, currentUserId);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Management relationship added successfully");
                response.setStatus(201);
                objectMapper.writeValue(response.getWriter(), result);
            } else {
                sendErrorResponse(response, "Failed to add management relationship", 500);
            }
        }
    }
    
    /**
     * Add reports relationship (manager has direct report)
     */
    private void addReportsRelationship(HttpServletRequest request, Map<String, Object> requestData, HttpServletResponse response) 
            throws IOException, SQLException {
        
        // Debug: Log received data
        //system.out.println("Received reports relationship data: " + requestData);
        
        // Extract data from request
        Integer managerId = parseInteger(requestData.get("manager_id"));
        Integer reportId = parseInteger(requestData.get("report_id"));
        String relationshipType = (String) requestData.get("relationship_type");
        String description = (String) requestData.get("description");
        
        //system.out.println("Parsed data - managerId: " + managerId + ", reportId: " + reportId + ", relationshipType: " + relationshipType);
        
        // Validate required fields
        if (managerId == null || reportId == null) {
            sendErrorResponse(response, "manager_id and report_id are required", 400);
            return;
        }
        
        // Get current user ID
        int currentUserId = UserContextUtil.getCurrentUserId(request);
        
        // Get relation type ID
        int relationTypeId = getRelationTypeId(relationshipType);
        
        // Insert reports relationship
        String sql = """
            INSERT INTO people_x_people (Employee, Manager, Description, ipXip_RelationType, Created_Datetime, Last_Update_UserID)
            VALUES (?, ?, ?, ?, NOW(), ?)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, reportId);  // Employee (who reports)
            pstmt.setInt(2, managerId); // Manager (who is reported to)
            pstmt.setString(3, description != null ? description : "");
            pstmt.setInt(4, relationTypeId);
            pstmt.setInt(5, currentUserId);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Reports relationship added successfully");
                response.setStatus(201);
                objectMapper.writeValue(response.getWriter(), result);
            } else {
                sendErrorResponse(response, "Failed to add reports relationship", 500);
            }
        }
    }
    
    /**
     * Update team relationship
     */
    private void updateTeamRelationship(HttpServletRequest request, int relationshipId, Map<String, Object> requestData, HttpServletResponse response) 
            throws IOException, SQLException {
        
        String relationshipType = (String) requestData.get("relationship_type");
        
        if (relationshipType == null) {
            sendErrorResponse(response, "relationship_type is required", 400);
            return;
        }
        
        // Get current user ID
        int currentUserId = UserContextUtil.getCurrentUserId(request);
        
        // Get relation type ID
        int relationTypeId = getRelationTypeId(relationshipType);
        
        // Update relationship
        String sql = """
            UPDATE people_x_people 
            SET ipXip_RelationType = ?, Last_Update_Datetime = NOW(), Last_Update_UserID = ?
            WHERE ID = ?
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, relationTypeId);
            pstmt.setInt(2, currentUserId);
            pstmt.setInt(3, relationshipId);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Team relationship updated successfully");
                response.setStatus(200);
                objectMapper.writeValue(response.getWriter(), result);
            } else {
                sendErrorResponse(response, "Relationship not found or update failed", 404);
            }
        }
    }
    
    /**
     * Remove team relationship
     */
    private void removeTeamRelationship(HttpServletRequest request, int relationshipId, HttpServletResponse response) 
            throws IOException, SQLException {
  
        // Hard delete relationship
        String sql = """
            DELETE FROM people_x_people 
            WHERE ID = ?
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, relationshipId);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Team relationship removed successfully");
                response.setStatus(200);
                objectMapper.writeValue(response.getWriter(), result);
            } else {
                sendErrorResponse(response, "Relationship not found", 404);
            }
        }
    }
    
    /**
     * Get relation type ID by name
     */
    private int getRelationTypeId(String relationshipType) throws SQLException {
        String sql = "SELECT ID FROM people_x_people_relationtype WHERE primaryname = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, relationshipType);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                } else {
                    // Default to "Direct report" if not found
                    return getRelationTypeId("Direct report");
                }
            }
        }
    }
    

    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("status", statusCode);
        
        objectMapper.writeValue(response.getWriter(), error);
    }
}
