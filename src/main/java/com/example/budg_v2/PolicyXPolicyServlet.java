package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.PolicyService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
import java.util.List;
import java.util.Map;

@WebServlet("/api/policy-x-policy/*")
public class PolicyXPolicyServlet extends HttpServlet {
    
    private PolicyService policyService;
    private SegmentValidationService segmentValidationService;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        policyService = new PolicyService();
        segmentValidationService = new SegmentValidationService();
        gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        //system.out.println("PolicyXPolicyServlet: doGet called with pathInfo: " + pathInfo);

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing parameters");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            if (pathInfo.startsWith("/source/")) {
                // Get relationships by source ID
                int sourceId = Integer.parseInt(pathInfo.substring(8));
                //system.out.println("PolicyXPolicyServlet: Getting relationships for source ID: " + sourceId);
                List<Map<String, Object>> relationships = policyService.getPolicyRelationshipsBySourceId(sourceId);
                //system.out.println("PolicyXPolicyServlet: Found " + relationships.size() + " relationships");
                response.getWriter().write(gson.toJson(relationships));
            } else if (pathInfo.startsWith("/target/")) {
      
                // For now, return empty list - can be implemented later
                response.getWriter().write(gson.toJson(new java.util.ArrayList<>()));
            } else if (pathInfo.equals("/relation-types")) {
                // Get relationship types
                List<Map<String, Object>> relationTypes = policyService.getPolicyRelationTypes();
                response.getWriter().write(gson.toJson(relationTypes));
            } else if (pathInfo.length() > 1) {
            
                // For now, return empty - can be implemented later
                response.getWriter().write(gson.toJson(new java.util.HashMap<>()));
            } else {
                //system.out.println("PolicyXPolicyServlet: No matching endpoint found for pathInfo: " + pathInfo);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Endpoint not found");
                response.getWriter().write(gson.toJson(error));
            }
        } catch (NumberFormatException e) {
            //system.out.println("PolicyXPolicyServlet: NumberFormatException: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid ID format");
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            //system.out.println("PolicyXPolicyServlet: SQLException: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            //system.out.println("PolicyXPolicyServlet: Exception: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            // Parse JSON request body
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }
            
            JsonObject jsonData = gson.fromJson(jsonBuffer.toString(), JsonObject.class);
            //system.out.println("PolicyXPolicyServlet: Received JSON data: " + jsonData.toString());
            
            int sourceId = jsonData.get("sourceId").getAsInt();
            int targetId = jsonData.get("targetId").getAsInt();
            int relationType = jsonData.get("relationType").getAsInt();
            String description = jsonData.has("description") ? jsonData.get("description").getAsString() : "";
            Integer userId = null;
            if (jsonData.has("userId") && !jsonData.get("userId").isJsonNull()) {
                userId = jsonData.get("userId").getAsInt();
            }
            
            //system.out.println("PolicyXPolicyServlet: Parsed values - sourceId: " + sourceId + 
                        //     ", targetId: " + targetId + ", relationType: " + relationType +
                          //   ", description: " + description + ", userId: " + userId);
            
            // Validate cross-segment relationship
            var validationResult = segmentValidationService.validateCrossSegmentRelationship(sourceId, "Policy", targetId, "Policy");
            if (!validationResult.isValid) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("error", validationResult.message);
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            boolean success = policyService.createPolicyRelationship(sourceId, targetId, relationType, description, userId);
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success);
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (Exception e) {
            //system.out.println("PolicyXPolicyServlet: POST Exception: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.length() <= 1) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing relationship ID");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int relationshipId = Integer.parseInt(pathInfo.substring(1));
            
            // Parse JSON request body
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }
            
            JsonObject jsonData = gson.fromJson(jsonBuffer.toString(), JsonObject.class);
            
            int relationType = jsonData.get("relationType").getAsInt();
            int targetPolicyId = jsonData.get("targetPolicyId").getAsInt();
            String description = jsonData.has("description") ? jsonData.get("description").getAsString() : "";
            Integer userId = null;
            if (jsonData.has("userId") && !jsonData.get("userId").isJsonNull()) {
                userId = jsonData.get("userId").getAsInt();
            }

            int sourcePolicyId = getSourcePolicyId(relationshipId);
            if (sourcePolicyId > 0) {
                var validationResult = segmentValidationService.validateCrossSegmentRelationship(
                        sourcePolicyId, "Policy", targetPolicyId, "Policy");
                if (!validationResult.isValid) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("success", false);
                    error.addProperty("error", validationResult.message);
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            boolean success = policyService.updatePolicyRelationship(relationshipId, relationType, targetPolicyId, description, userId);
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success);
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (Exception e) {
            //system.out.println("PolicyXPolicyServlet: PUT Exception: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.length() <= 1) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing relationship ID");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int relationshipId = Integer.parseInt(pathInfo.substring(1));
            boolean success = policyService.deletePolicyRelationship(relationshipId);
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success);
            response.getWriter().write(gson.toJson(responseJson));
            
        } catch (Exception e) {
            //system.out.println("PolicyXPolicyServlet: DELETE Exception: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private int getSourcePolicyId(int relationshipId) {
        String sql = "SELECT sourceid FROM policy_x_policy WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, relationshipId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("sourceid");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error looking up source policy for relationship " + relationshipId + ": " + e.getMessage());
        }
        return -1;
    }
}
