package com.example.budg_v2;

import com.example.budg_v2.service.RegulationImpactService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ImpactSegmentValidationUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/regulation-impact/*")
public class RegulationImpactServlet extends HttpServlet {
    private final RegulationImpactService regulationImpactService;
    private final Gson gson = new Gson();

    public RegulationImpactServlet() {
        this.regulationImpactService = new RegulationImpactService();
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
            
            if ("health".equals(parts[0])) {
                handleHealthCheck(response);
            } else if ("product-relation-types".equals(parts[0])) {
                handleGetProductRelationTypes(response);
            } else if ("policy-relation-types".equals(parts[0])) {
                handleGetPolicyRelationTypes(response);
            } else if ("project-relation-types".equals(parts[0])) {
                handleGetProjectRelationTypes(response);
            } else if ("regulatorytheme-relation-types".equals(parts[0])) {
                handleGetRegulatoryThemeRelationTypes(response);
            } else if ("product-owner".equals(parts[0]) && parts.length >= 2) {
                int productId = Integer.parseInt(parts[1]);
                handleGetProductOwner(productId, response);
            } else if ("policy-owner".equals(parts[0]) && parts.length >= 2) {
                int policyId = Integer.parseInt(parts[1]);
                handleGetPolicyOwner(policyId, response);
            } else if ("project-owner".equals(parts[0]) && parts.length >= 2) {
                int projectId = Integer.parseInt(parts[1]);
                handleGetProjectOwner(projectId, response);
            } else if ("products".equals(parts[0]) && parts.length >= 3 && "regulations".equals(parts[2])) {
                // Reverse lookup: /api/regulation-impact/products/{productId}/regulations
                int productId = Integer.parseInt(parts[1]);
                handleGetRegulationRelationshipsByProductId(productId, request, response);
            } else if ("policies".equals(parts[0]) && parts.length >= 3 && "regulations".equals(parts[2])) {
                // Reverse lookup: /api/regulation-impact/policies/{policyId}/regulations
                int policyId = Integer.parseInt(parts[1]);
                handleGetRegulationRelationshipsByPolicyId(policyId, request, response);
            } else if ("projects".equals(parts[0]) && parts.length >= 3 && "regulations".equals(parts[2])) {
                // Reverse lookup: /api/regulation-impact/projects/{projectId}/regulations
                int projectId = Integer.parseInt(parts[1]);
                handleGetRegulationRelationshipsByProjectId(projectId, request, response);
            } else if (parts.length >= 2) {
                int regulationId = Integer.parseInt(parts[0]);
                
                if ("products".equals(parts[1])) {
                    handleGetProductRelationships(regulationId, request, response);
                } else if ("policies".equals(parts[1])) {
                    handleGetPolicyRelationships(regulationId, request, response);
                } else if ("projects".equals(parts[1])) {
                    handleGetProjectRelationships(regulationId, request, response);
                } else if ("regulatorythemes".equals(parts[1])) {
                    handleGetRegulatoryThemeRelationships(regulationId, request, response);
                } else {
                    sendError(response, "Invalid endpoint", 400);
                }
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid regulation ID", 400);
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
            
            if ("products".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveProductRelationships(request, response);
            } else if ("policies".equals(parts[0]) && "save".equals(parts[1])) {
                handleSavePolicyRelationships(request, response);
            } else if ("projects".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveProjectRelationships(request, response);
            } else if ("regulatorythemes".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveRegulatoryThemeRelationships(request, response);
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }
    
    // ===== GET RELATIONSHIP HANDLERS =====
    
    private void handleGetProductRelationships(int regulationId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = regulationImpactService.getProductRelationshipsByRegulationId(regulationId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyRelationships(int regulationId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = regulationImpactService.getPolicyRelationshipsByRegulationId(regulationId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProjectRelationships(int regulationId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = regulationImpactService.getProjectRelationshipsByRegulationId(regulationId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Project", "projectId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve project relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetRegulatoryThemeRelationships(int regulationId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = regulationImpactService.getRegulatoryThemeRelationshipsByRegulationId(regulationId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "RegulatoryTheme", "regulatoryThemeId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve regulatory theme relationships: " + e.getMessage(), 500);
        }
    }
    
    // Reverse lookup handlers
    private void handleGetRegulationRelationshipsByProductId(int productId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = regulationImpactService.getRegulationRelationshipsByProductId(productId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Regulation", "regulationId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve regulation relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetRegulationRelationshipsByPolicyId(int policyId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = regulationImpactService.getRegulationRelationshipsByPolicyId(policyId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Regulation", "regulationId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve regulation relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetRegulationRelationshipsByProjectId(int projectId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = regulationImpactService.getRegulationRelationshipsByProjectId(projectId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Regulation", "regulationId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve regulation relationships: " + e.getMessage(), 500);
        }
    }
    
    // ===== GET RELATION TYPE HANDLERS =====
    
    private void handleGetProductRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = regulationImpactService.getProductRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = regulationImpactService.getPolicyRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProjectRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = regulationImpactService.getProjectRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve project relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetRegulatoryThemeRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = regulationImpactService.getRegulatoryThemeRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve regulatory theme relation types: " + e.getMessage(), 500);
        }
    }
    
    // ===== GET OWNER HANDLERS =====
    
    private void handleGetProductOwner(int productId, HttpServletResponse response) throws IOException {
        try {
            Map<String, Object> owner = new HashMap<>();
            String ownerName = regulationImpactService.getProductOwnersString(productId);
            String ownerEmail = regulationImpactService.getProductOwnersEmail(productId);
            owner.put("ownerName", ownerName);
            owner.put("ownerEmail", ownerEmail);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product owner: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyOwner(int policyId, HttpServletResponse response) throws IOException {
        try {
            Map<String, Object> owner = new HashMap<>();
            String ownerName = regulationImpactService.getPolicyOwnersString(policyId);
            String ownerEmail = regulationImpactService.getPolicyOwnersEmail(policyId);
            owner.put("ownerName", ownerName);
            owner.put("ownerEmail", ownerEmail);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy owner: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProjectOwner(int projectId, HttpServletResponse response) throws IOException {
        try {
            Map<String, Object> owner = new HashMap<>();
            String ownerName = regulationImpactService.getProjectOwnersString(projectId);
            String ownerEmail = regulationImpactService.getProjectOwnersEmail(projectId);
            owner.put("ownerName", ownerName);
            owner.put("ownerEmail", ownerEmail);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve project owner: " + e.getMessage(), 500);
        }
    }
    
    // ===== SAVE RELATIONSHIP HANDLERS =====
    
    private void handleSaveProductRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            
            int regulationId = jsonObject.get("regulationId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Regulation", regulationId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships").getAsJsonArray(), 
                new TypeToken<List<Map<String, Object>>>(){}.getType()
            );
            
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    regulationId, "Regulation", relationships, "Product", "productId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = regulationImpactService.saveProductRelationships(regulationId, relationships, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Product relationships saved successfully" : "Failed to save product relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Error saving product relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleSavePolicyRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            
            int regulationId = jsonObject.get("regulationId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Regulation", regulationId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships").getAsJsonArray(), 
                new TypeToken<List<Map<String, Object>>>(){}.getType()
            );
            
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    regulationId, "Regulation", relationships, "Policy", "policyId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = regulationImpactService.savePolicyRelationships(regulationId, relationships, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Policy relationships saved successfully" : "Failed to save policy relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Error saving policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleSaveProjectRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            
            int regulationId = jsonObject.get("regulationId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Regulation", regulationId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships").getAsJsonArray(), 
                new TypeToken<List<Map<String, Object>>>(){}.getType()
            );
            
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    regulationId, "Regulation", relationships, "Project", "projectId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = regulationImpactService.saveProjectRelationships(regulationId, relationships, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Project relationships saved successfully" : "Failed to save project relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Error saving project relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleSaveRegulatoryThemeRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            
            int regulationId = jsonObject.get("regulationId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Regulation", regulationId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships").getAsJsonArray(), 
                new TypeToken<List<Map<String, Object>>>(){}.getType()
            );
            
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    regulationId, "Regulation", relationships, "RegulatoryTheme", "regulatoryThemeId", "regulatorythemeId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = regulationImpactService.saveRegulatoryThemeRelationships(regulationId, relationships, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Regulatory theme relationships saved successfully" : "Failed to save regulatory theme relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Error saving regulatory theme relationships: " + e.getMessage(), 500);
        }
    }
    
    // ===== UTILITY METHODS =====
    
    private int getUserIdFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Integer) session.getAttribute("userId");
        }
        return 1; // Default user ID if session is not available
    }
    
    private void handleHealthCheck(HttpServletResponse response) throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("status", "healthy");
        response.getWriter().write(gson.toJson(result));
    }
    
    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        response.getWriter().write(gson.toJson(error));
    }
}

