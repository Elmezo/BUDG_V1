package com.example.budg_v2;

import com.example.budg_v2.service.CommitteeImpactService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ImpactSegmentValidationUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/committee-impact/*")
public class CommitteeImpactServlet extends HttpServlet {
    private final CommitteeImpactService committeeImpactService;
    private final Gson gson = new Gson();

    public CommitteeImpactServlet() {
        this.committeeImpactService = new CommitteeImpactService();
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                sendError(response, "Invalid endpoint", 400);
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");
            
            // Check for reverse lookup first: /api/committee-impact/capabilities/{capabilityId}/committees
            if (parts.length >= 3 && "capabilities".equals(parts[0])) {
                try {
                    int capabilityId = Integer.parseInt(parts[1]);
                    if ("committees".equals(parts[2])) {
                        handleGetCommitteeRelationshipsByCapabilityId(capabilityId, request, response);
                        return;
                    }
                } catch (NumberFormatException e) {
                    sendError(response, "Invalid capability ID: " + parts[1], 400);
                    return;
                }
            }
            
            if ("capability-relation-types".equals(parts[0])) {
                handleGetCapabilityRelationTypes(response);
            } else if ("capability-owner".equals(parts[0]) && parts.length >= 2) {
                int capabilityId = Integer.parseInt(parts[1]);
                handleGetCapabilityOwner(capabilityId, response);
            } else if (parts.length >= 2) {
                int committeeId = Integer.parseInt(parts[0]);
                
                if ("capabilities".equals(parts[1])) {
                    handleGetCapabilityRelationships(committeeId, request, response);
                } else {
                    sendError(response, "Invalid endpoint", 400);
                }
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid committee ID", 400);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                sendError(response, "Invalid endpoint", 400);
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");
            
            if ("capabilities".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveCapabilityRelationships(request, response);
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATIONSHIP HANDLERS =====
    
    private void handleGetCapabilityRelationships(int committeeId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = committeeImpactService.getCapabilityRelationshipsByCommitteeId(committeeId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Capability", "capabilityId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve capability relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetCommitteeRelationshipsByCapabilityId(int capabilityId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = committeeImpactService.getCommitteeRelationshipsByCapabilityId(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Committee", "committeeId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve committee relationships: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATION TYPE HANDLERS =====
    
    private void handleGetCapabilityRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = committeeImpactService.getCapabilityRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve capability relation types: " + e.getMessage(), 500);
        }
    }

    // ===== GET OWNER HANDLERS =====
    
    private void handleGetCapabilityOwner(int capabilityId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = committeeImpactService.getCapabilityOwnersString(capabilityId);
            String ownerEmail = committeeImpactService.getCapabilityOwnersEmail(capabilityId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("ownerName", ownerName != null ? ownerName : "No owner");
            result.put("capabilityOwnerName", ownerName != null ? ownerName : "No owner");
            result.put("ownerEmail", ownerEmail);
            result.put("capabilityOwnerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve capability owner: " + e.getMessage(), 500);
        }
    }

    // ===== SAVE HANDLERS =====
    
    private void handleSaveCapabilityRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User authentication required\"}");
                return;
            }
            
            StringBuilder jsonBody = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBody.append(line);
                }
            }
            
            JsonObject jsonObject = gson.fromJson(jsonBody.toString(), JsonObject.class);
            int committeeId = jsonObject.get("committeeId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Committee", committeeId)) {
                return; // Response already sent
            }
            
            JsonElement relationshipsElement = jsonObject.get("relationships");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relationships = relationshipsElement != null && relationshipsElement.isJsonArray()
                ? gson.fromJson(relationshipsElement, List.class)
                : new ArrayList<>();

            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    committeeId, "Committee", relationships, "Capability", "capabilityId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }
            
            boolean success = committeeImpactService.saveCapabilityRelationships(committeeId, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "Capability relationships saved successfully" : "Failed to save capability relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save capability relationships: " + e.getMessage(), 500);
        }
    }

    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        response.getWriter().write(gson.toJson(error));
    }
}

