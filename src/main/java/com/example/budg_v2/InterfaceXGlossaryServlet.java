package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

@WebServlet(name = "InterfaceXGlossaryServlet", urlPatterns = {"/api/interface-x-glossary/*"})
public class InterfaceXGlossaryServlet extends HttpServlet {
    
    private final Gson gson = new Gson();
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        
        // Handle relation types endpoint
        String typeParam = req.getParameter("type");
        String relationTypesFlag = req.getParameter("relationTypes");
        if ("relation-types".equalsIgnoreCase(typeParam) || "true".equalsIgnoreCase(relationTypesFlag)) {
            try {
                List<Map<String, Object>> relationTypes = getRelationTypes();
                resp.getWriter().write(gson.toJson(relationTypes));
            } catch (SQLException e) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
            }
            return;
        }

        // Handle path-based requests (like /api/interface-x-glossary/123)
        if (pathInfo != null && !pathInfo.equals("/")) {
            try {
                String idStr = pathInfo.substring(1);
                int id = Integer.parseInt(idStr);
                // Get specific glossary item by ID
                Map<String, Object> item = getInterfaceXGlossaryItemById(id);
                if (item != null) {
                    JsonUtil.sendJsonResponse(resp.getWriter(), item);
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Glossary item not found", 404);
                }
            } catch (NumberFormatException e) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid ID format", 400);
            } catch (SQLException e) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
            }
            return;
        }

        // Handle interface-based requests (like /api/interface-x-glossary?interfaceId=123)
        String interfaceIdParam = req.getParameter("interfaceId");
        if (interfaceIdParam == null || interfaceIdParam.trim().isEmpty()) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Interface ID is required", 400);
            return;
        }

        try {
            int interfaceId = Integer.parseInt(interfaceIdParam);
            List<Map<String, Object>> data = getInterfaceXGlossaryData(interfaceId);
            JsonUtil.sendJsonResponse(resp.getWriter(), data);
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid Interface ID format", 400);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);
        
        try {
            JsonObject requestBody = JsonParser.parseReader(req.getReader()).getAsJsonObject();
            String action = requestBody.has("action") ? requestBody.get("action").getAsString() : "";
            
            switch (action) {
                case "add":
                    addInterfaceXGlossary(requestBody, resp);
                    break;
                case "update":
                    updateInterfaceXGlossary(requestBody, resp);
                    break;
                case "delete":
                    deleteInterfaceXGlossary(requestBody, resp);
                    break;
                default:
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid action", 400);
            }
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error processing request: " + e.getMessage(), 500);
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);
        
        try {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "ID is required for deletion", 400);
                return;
            }
            
            String idStr = pathInfo.substring(1);
            try {
                int id = Integer.parseInt(idStr);
                deleteInterfaceXGlossaryById(id, resp);
            } catch (NumberFormatException e) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid ID format", 400);
            }
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error processing delete request: " + e.getMessage(), 500);
        }
    }
    
    private List<Map<String, Object>> getRelationTypes() throws SQLException {
        String sql = "SELECT ID, PrimaryName FROM interface_x_glossary_relationtype ORDER BY PrimaryName";
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("name", rs.getString("PrimaryName"));
                results.add(row);
            }
        }
        return results;
    }
    
    private Map<String, Object> getInterfaceXGlossaryItemById(int id) throws SQLException {
        String sql = "SELECT " +
                "ixg.ID, " +
                "rt.PrimaryName AS relationshipType, " +
                "g.ID AS glossaryId, " +
                "g.Name AS glossaryName, " +
                "g.Description AS glossaryDefinition, " +
                "s.primaryname AS relationshipStatus " +
                "FROM interface_x_glossary ixg " +
                "LEFT JOIN interface_x_glossary_relationtype rt ON rt.ID = ixg.Glossary_RelationType " +
                "LEFT JOIN glossary g ON g.ID = ixg.Glossary " +
                "LEFT JOIN interface i ON i.id = ixg.Interface " +
                "LEFT JOIN status s ON s.ID = i.status_id " +
                "WHERE ixg.ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("relationshipType", rs.getString("relationshipType"));
                    Integer glossaryId = rs.getObject("glossaryId") != null ? rs.getInt("glossaryId") : null;
                    row.put("glossaryId", glossaryId);
                    row.put("glossaryName", rs.getString("glossaryName"));
                    row.put("glossaryDefinition", rs.getString("glossaryDefinition"));
                    row.put("relationshipStatus", rs.getString("relationshipStatus"));
                    return row;
                }
            }
        }
        return null;
    }
    
    private List<Map<String, Object>> getInterfaceXGlossaryData(int interfaceId) throws SQLException {
        String sql = "SELECT " +
                "ixg.ID, " +
                "rt.PrimaryName AS relationshipType, " +
                "g.ID AS glossaryId, " +
                "g.Name AS glossaryName, " +
                "g.Description AS glossaryDefinition, " +
                "s.primaryname AS relationshipStatus " +
                "FROM interface_x_glossary ixg " +
                "LEFT JOIN interface_x_glossary_relationtype rt ON rt.ID = ixg.Glossary_RelationType " +
                "LEFT JOIN glossary g ON g.ID = ixg.Glossary " +
                "LEFT JOIN interface i ON i.id = ixg.Interface " +
                "LEFT JOIN status s ON s.ID = i.status_id " +
                "WHERE ixg.Interface = ? " +
                "ORDER BY ixg.ID";
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, interfaceId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("relationshipType", rs.getString("relationshipType"));
                    Integer glossaryId = rs.getObject("glossaryId") != null ? rs.getInt("glossaryId") : null;
                    row.put("glossaryId", glossaryId);
                    row.put("glossaryName", rs.getString("glossaryName"));
                    row.put("glossaryDefinition", rs.getString("glossaryDefinition"));
                    row.put("relationshipStatus", rs.getString("relationshipStatus"));
                    results.add(row);
                }
            }
        }
        
        return results;
    }
    
    private void addInterfaceXGlossary(JsonObject requestBody, HttpServletResponse resp) throws IOException, SQLException {
        //system.out.println("addInterfaceXGlossary called with requestBody: " + requestBody.toString());
        
        if (!requestBody.has("interfaceId") || !requestBody.has("glossaryId") || !requestBody.has("relationTypeId")) {
            //system.out.println("Missing required fields in request");
            JsonUtil.sendErrorResponse(resp.getWriter(), "Missing required fields: interfaceId, glossaryId, relationTypeId", 400);
            return;
        }
        
        int interfaceId = requestBody.get("interfaceId").getAsInt();
        int glossaryId = requestBody.get("glossaryId").getAsInt();
        int relationTypeId = requestBody.get("relationTypeId").getAsInt();

        if (!validateCrossSegment(interfaceId, glossaryId, resp)) {
            return;
        }
        
        //system.out.println("Parsed values - interfaceId: " + interfaceId + ", glossaryId: " + glossaryId + ", relationTypeId: " + relationTypeId);
        
        // Get the next available ID
        String getNextIdSql = "SELECT COALESCE(MAX(ID), 0) + 1 as nextId FROM interface_x_glossary";
        int nextId = 1;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(getNextIdSql)) {
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nextId = rs.getInt("nextId");
                }
            }
        } catch (SQLException e) {
            //system.out.println("Error getting next ID: " + e.getMessage());
            // Use default ID if query fails
            nextId = 1;
        }
        
        //system.out.println("Next available ID: " + nextId);
        
        String sql = "INSERT INTO interface_x_glossary (ID, Glossary_RelationType, Glossary, Interface, CreateDatetime, LastUpdate_datetime, LastUpdate_UserID) VALUES (?, ?, ?, ?, NOW(), NOW(), 1)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            //system.out.println("Executing SQL: " + sql);
            //system.out.println("Setting parameters - ID: " + nextId + ", relationTypeId: " + relationTypeId + ", glossaryId: " + glossaryId + ", interfaceId: " + interfaceId);
            ps.setInt(1, nextId);
            ps.setInt(2, relationTypeId);
            ps.setInt(3, glossaryId);
            ps.setInt(4, interfaceId);
            
            int rowsAffected = ps.executeUpdate();
            //system.out.println("Rows affected: " + rowsAffected);
            
            if (rowsAffected > 0) {
                // Since we're manually setting the ID, we don't need generated keys
                //system.out.println("Record inserted successfully with ID: " + nextId);
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("id", nextId);
                response.addProperty("message", "Interface glossary relationship added successfully");
                resp.getWriter().write(gson.toJson(response));
            } else {
                //system.out.println("No rows affected - insert failed");
                JsonUtil.sendErrorResponse(resp.getWriter(), "Failed to add interface glossary relationship", 500);
            }
        } catch (SQLException e) {
            //system.out.println("SQL Exception: " + e.getMessage());
            //system.out.println("SQL State: " + e.getSQLState());
            //system.out.println("Error Code: " + e.getErrorCode());
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }
    
    private void updateInterfaceXGlossary(JsonObject requestBody, HttpServletResponse resp) throws IOException, SQLException {
        if (!requestBody.has("id") || !requestBody.has("interfaceId") || !requestBody.has("glossaryId") || !requestBody.has("relationTypeId")) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Missing required fields: id, interfaceId, glossaryId, relationTypeId", 400);
            return;
        }
        
        int id = requestBody.get("id").getAsInt();
        int interfaceId = requestBody.get("interfaceId").getAsInt();
        int glossaryId = requestBody.get("glossaryId").getAsInt();
        int relationTypeId = requestBody.get("relationTypeId").getAsInt();

        if (!validateCrossSegment(interfaceId, glossaryId, resp)) {
            return;
        }
        
        String sql = "UPDATE interface_x_glossary SET Glossary_RelationType = ?, Glossary = ?, LastUpdate_datetime = NOW(), LastUpdate_UserID = 1 WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, relationTypeId);
            ps.setInt(2, glossaryId);
            ps.setInt(3, id);
            
            int rowsAffected = ps.executeUpdate();
            
            JsonObject response = new JsonObject();
            if (rowsAffected > 0) {
                response.addProperty("success", true);
                response.addProperty("message", "Interface glossary relationship updated successfully");
            } else {
                response.addProperty("success", false);
                response.addProperty("message", "No relationship found with the given ID");
            }
            resp.getWriter().write(gson.toJson(response));
        }
    }
    
    private void deleteInterfaceXGlossary(JsonObject requestBody, HttpServletResponse resp) throws IOException, SQLException {
        if (!requestBody.has("id")) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Missing required field: id", 400);
            return;
        }
        
        int id = requestBody.get("id").getAsInt();
        String sql = "DELETE FROM interface_x_glossary WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            int rowsAffected = ps.executeUpdate();
            
            JsonObject response = new JsonObject();
            if (rowsAffected > 0) {
                response.addProperty("success", true);
                response.addProperty("message", "Interface glossary relationship deleted successfully");
            } else {
                response.addProperty("success", false);
                response.addProperty("message", "No relationship found with the given ID");
            }
            resp.getWriter().write(gson.toJson(response));
        }
    }
    
    private void deleteInterfaceXGlossaryById(int id, HttpServletResponse resp) 
            throws IOException, SQLException {
        
        //system.out.println("Attempting to delete interface_x_glossary with ID: " + id);
        
        // First, check if the record exists
        String checkSql = "SELECT COUNT(*) as count FROM interface_x_glossary WHERE ID = ?";
        int recordCount = 0;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            
            checkPs.setInt(1, id);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    recordCount = rs.getInt("count");
                }
            }
        }
        
        //system.out.println("Record count for ID " + id + ": " + recordCount);
        
        if (recordCount == 0) {
            // Record doesn't exist
            JsonObject response = new JsonObject();
            response.addProperty("success", false);
            response.addProperty("message", "No relationship found with the given ID: " + id);
            resp.getWriter().write(gson.toJson(response));
            return;
        }
        
        // Record exists, proceed with deletion
        String sql = "DELETE FROM interface_x_glossary WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            int rowsAffected = ps.executeUpdate();
            
            //system.out.println("Rows affected by DELETE: " + rowsAffected);
            
            JsonObject response = new JsonObject();
            if (rowsAffected > 0) {
                response.addProperty("success", true);
                response.addProperty("message", "Interface glossary relationship deleted successfully");
            } else {
                response.addProperty("success", false);
                response.addProperty("message", "Failed to delete relationship with ID: " + id);
            }
            resp.getWriter().write(gson.toJson(response));
        }
    }

    private boolean validateCrossSegment(int interfaceId, int glossaryId, HttpServletResponse resp) throws IOException {
        try {
            SegmentValidationService.ValidationResult result =
                    segmentValidationService.validateCrossSegmentRelationship(
                            interfaceId, "Interface", glossaryId, "Glossary");
            if (!result.isValid) {
                JsonUtil.sendErrorResponse(resp.getWriter(), result.message, 400);
                return false;
            }
            return true;
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error validating relationship: " + e.getMessage(), 500);
            return false;
        }
    }
}


