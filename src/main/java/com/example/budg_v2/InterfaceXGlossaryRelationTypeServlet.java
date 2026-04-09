package com.example.budg_v2;
import com.example.budg_v2.database.DatabaseConnection;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(
    name = "InterfaceXGlossaryRelationTypeServlet",
    urlPatterns = {"/api/interface-x-glossary-relationtype/*"})
public class InterfaceXGlossaryRelationTypeServlet extends HttpServlet {
    
    private final Gson gson = new Gson();
    
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
        
        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/list")) {
                // Get all relation types
                List<Map<String, Object>> relationTypes = getRelationTypes();
                resp.getWriter().write(gson.toJson(relationTypes));
                
            } else {
                // Get relation type by ID
                String idStr = pathInfo.substring(1);
                try {
                    int id = Integer.parseInt(idStr);
                    Map<String, Object> relationType = getRelationTypeById(id);
                    if (relationType != null) {
                        resp.getWriter().write(gson.toJson(relationType));
                    } else {
                        JsonUtil.sendErrorResponse(resp.getWriter(), "Relation type not found", 404);
                    }
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid ID format", 400);
                }
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
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
                    addRelationType(requestBody, resp);
                    break;
                case "update":
                    updateRelationType(requestBody, resp);
                    break;
                case "delete":
                    deleteRelationType(requestBody, resp);
                    break;
                default:
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid action", 400);
            }
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error processing request: " + e.getMessage(), 500);
        }
    }
    
    private List<Map<String, Object>> getRelationTypes() throws SQLException {
        String sql = "SELECT ID, PrimaryName, Description FROM interface_x_glossary_relationtype ORDER BY PrimaryName";
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("name", rs.getString("PrimaryName"));
                row.put("description", rs.getString("Description"));
                results.add(row);
            }
        }
        return results;
    }
    
    private Map<String, Object> getRelationTypeById(int id) throws SQLException {
        String sql = "SELECT ID, PrimaryName, Description FROM interface_x_glossary_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("PrimaryName"));
                    row.put("description", rs.getString("Description"));
                    return row;
                }
            }
        }
        return null;
    }
    
    private void addRelationType(JsonObject requestBody, HttpServletResponse resp) 
            throws IOException, SQLException {
        String name = requestBody.get("name").getAsString();
        String description = requestBody.has("description") ? requestBody.get("description").getAsString() : "";
        
        String sql = "INSERT INTO interface_x_glossary_relationtype (PrimaryName, Description) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            
            int affectedRows = ps.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newId = generatedKeys.getInt(1);
                        JsonObject response = new JsonObject();
                        response.addProperty("success", true);
                        response.addProperty("id", newId);
                        response.addProperty("message", "Relation type added successfully");
                        resp.getWriter().write(gson.toJson(response));
                    }
                }
            } else {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Failed to add relation type", 500);
            }
        }
    }
    
    private void updateRelationType(JsonObject requestBody, HttpServletResponse resp) 
            throws IOException, SQLException {
        int id = requestBody.get("id").getAsInt();
        String name = requestBody.get("name").getAsString();
        String description = requestBody.has("description") ? requestBody.get("description").getAsString() : "";
        
        String sql = "UPDATE interface_x_glossary_relationtype SET PrimaryName = ?, Description = ? WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setInt(3, id);
            
            int affectedRows = ps.executeUpdate();
            if (affectedRows > 0) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Relation type updated successfully");
                resp.getWriter().write(gson.toJson(response));
            } else {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Relation type not found or update failed", 404);
            }
        }
    }
    
    private void deleteRelationType(JsonObject requestBody, HttpServletResponse resp) 
            throws IOException, SQLException {
        int id = requestBody.get("id").getAsInt();
        
        String sql = "DELETE FROM interface_x_glossary_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            
            int affectedRows = ps.executeUpdate();
            if (affectedRows > 0) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Relation type deleted successfully");
                resp.getWriter().write(gson.toJson(response));
            } else {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Relation type not found or delete failed", 404);
            }
        }
    }
}
