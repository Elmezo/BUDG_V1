package com.example.budg_v2;

import com.example.budg_v2.service.LegalImpactService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
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

@WebServlet("/api/legal-impact/*")
public class LegalImpactServlet extends HttpServlet {
    private final LegalImpactService legalImpactService;
    private final SegmentValidationService segmentValidationService;
    private final Gson gson = new Gson();

    public LegalImpactServlet() {
        this.legalImpactService = new LegalImpactService();
        this.segmentValidationService = new SegmentValidationService();
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
        //system.out.println("LegalImpactServlet: doGet called with pathInfo: " + pathInfo);
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                sendError(response, "Invalid endpoint", 400);
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");
            //system.out.println("LegalImpactServlet: Path parts: " + java.util.Arrays.toString(parts));
            
            // Check for reverse lookup first: /api/legal-impact/{entityType}s/{entityId}/legal-entities
            if (parts.length >= 3) {
                if ("geographies".equals(parts[0])) {
                    try {
                        int geographyId = Integer.parseInt(parts[1]);
                        if ("legal-entities".equals(parts[2]) || "legals".equals(parts[2])) {
                            //system.out.println("LegalImpactServlet: Handling reverse lookup for geographyId: " + geographyId);
                            handleGetLegalEntityRelationshipsByGeographyId(geographyId, response, request);
                            return;
                        } else {
                            sendError(response, "Invalid endpoint. Expected 'legal-entities' but got: " + parts[2], 400);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid geography ID: " + parts[1], 400);
                        return;
                    }
                } else if ("capabilities".equals(parts[0])) {
                    try {
                        int capabilityId = Integer.parseInt(parts[1]);
                        if ("legal-entities".equals(parts[2]) || "legals".equals(parts[2])) {
                            handleGetLegalEntityRelationshipsByCapabilityId(capabilityId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid capability ID: " + parts[1], 400);
                        return;
                    }
                }
            }
            
            // Check for relation types endpoint
            if ("geography-relation-types".equals(parts[0])) {
                handleGetGeographyRelationTypes(response);
                return;
            }
            
            // Forward lookup: /api/legal-impact/{legalId}/geographies
            if (parts.length >= 2) {
                try {
                    int legalId = Integer.parseInt(parts[0]);
                    
                    if ("geographies".equals(parts[1])) {
                        //system.out.println("LegalImpactServlet: Handling forward lookup for legalId: " + legalId);
                        handleGetGeographyRelationships(legalId, response, request);
                        return;
                    } else {
                        sendError(response, "Invalid endpoint. Expected 'geographies' but got: " + parts[1], 400);
                        return;
                    }
                } catch (NumberFormatException e) {
                    sendError(response, "Invalid legal ID: " + parts[0], 400);
                    return;
                }
            }
            
            // If we get here, no valid endpoint matched
            sendError(response, "Invalid endpoint. Path: " + pathInfo, 400);
            
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
            
            if ("geographies".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveGeographyRelationships(request, response);
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATIONSHIP HANDLERS =====
    
    private void handleGetGeographyRelationships(int legalId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = legalImpactService.getGeographyRelationshipsByLegalId(legalId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Geography", "geographyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve geography relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetLegalEntityRelationshipsByGeographyId(int geographyId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = legalImpactService.getLegalEntityRelationshipsByGeographyId(geographyId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "LegalEntity", "legalId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal entity relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetLegalEntityRelationshipsByCapabilityId(int capabilityId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            com.example.budg_v2.service.CapabilityImpactService capabilityImpactService = new com.example.budg_v2.service.CapabilityImpactService();
            List<Map<String, Object>> relationships = capabilityImpactService.getLegalRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "LegalEntity", "legalId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal entity relationships: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATION TYPE HANDLERS =====
    
    private void handleGetGeographyRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = legalImpactService.getGeographyRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve geography relation types: " + e.getMessage(), 500);
        }
    }

    // ===== SAVE HANDLERS =====
    
    private void handleSaveGeographyRelationships(HttpServletRequest request, HttpServletResponse response) 
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
            int legalId = jsonObject.get("legalId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Legal Entity", legalId)) {
                return; // Response already sent
            }
            
            JsonElement relationshipsElement = jsonObject.get("relationships");
            
            List<Map<String, Object>> relationships = new ArrayList<>();
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                relationships = gson.fromJson(relationshipsElement, new TypeToken<List<Map<String, Object>>>(){}.getType());
            }
            
            // Validate: Enterprise relationships are allowed, but different private segments are not
            for (Map<String, Object> rel : relationships) {
                Object geographyIdObj = rel.get("geographyId");
                if (geographyIdObj == null) continue;
                int geographyId = ((Number) geographyIdObj).intValue();
                if (geographyId <= 0) continue;
                SegmentValidationService.ValidationResult validationResult =
                        segmentValidationService.validateCrossSegmentRelationship(
                                legalId, "LegalEntity", geographyId, "Geography");
                if (!validationResult.isValid) {
                    sendError(response, validationResult.message, 400);
                    return;
                }
            }
            
            boolean success = legalImpactService.saveGeographyRelationships(legalId, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "Geography relationships saved successfully" : "Failed to save geography relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save geography relationships: " + e.getMessage(), 500);
        }
    }

    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        response.getWriter().write(gson.toJson(error));
    }
}

