package com.example.budg_v2;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.SystemImpactService;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/system-impact/*")
public class SystemImpactServlet extends HttpServlet {
    private static final int SYSTEM_FACET_ID = 13;
    private final SystemImpactService systemImpactService;
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private final Gson gson = new Gson();

    public SystemImpactServlet() {
        this.systemImpactService = new SystemImpactService();
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
            
            // Check for reverse lookup first: /api/system-impact/{entityType}s/{entityId}/systems
            if (parts.length >= 3) {
                if ("products".equals(parts[0])) {
                    try {
                        int productId = Integer.parseInt(parts[1]);
                        if ("systems".equals(parts[2])) {
                            handleGetSystemRelationshipsByProductId(productId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid product ID: " + parts[1], 400);
                        return;
                    }
                } else if ("clients".equals(parts[0])) {
                    try {
                        int clientId = Integer.parseInt(parts[1]);
                        if ("systems".equals(parts[2])) {
                            handleGetSystemRelationshipsByClientId(clientId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid client ID: " + parts[1], 400);
                        return;
                    }
                } else if ("legals".equals(parts[0]) || "legal-entities".equals(parts[0])) {
                    try {
                        int legalId = Integer.parseInt(parts[1]);
                        if ("systems".equals(parts[2])) {
                            handleGetSystemRelationshipsByLegalId(legalId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid legal ID: " + parts[1], 400);
                        return;
                    }
                } else if ("capabilities".equals(parts[0])) {
                    try {
                        int capabilityId = Integer.parseInt(parts[1]);
                        if ("systems".equals(parts[2])) {
                            handleGetSystemRelationshipsByCapabilityId(capabilityId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid capability ID: " + parts[1], 400);
                        return;
                    }
                }
            }
            
            if ("product-system-relation-types".equals(parts[0])) {
                handleGetProductSystemRelationTypes(response);
            } else if ("product-legal-relation-types".equals(parts[0])) {
                handleGetProductLegalRelationTypes(response);
            } else if ("client-relation-types".equals(parts[0])) {
                handleGetClientRelationTypes(response);
            } else if ("legal-relation-types".equals(parts[0])) {
                handleGetLegalRelationTypes(response);
            } else if ("product-owner".equals(parts[0]) && parts.length >= 2) {
                int productId = Integer.parseInt(parts[1]);
                handleGetProductOwner(productId, response);
            } else if ("client-owner".equals(parts[0]) && parts.length >= 2) {
                int clientId = Integer.parseInt(parts[1]);
                handleGetClientOwner(clientId, response);
            } else if ("legal-owner".equals(parts[0]) && parts.length >= 2) {
                int legalId = Integer.parseInt(parts[1]);
                handleGetLegalOwner(legalId, response);
            } else if (parts.length >= 2) {
                int systemId = Integer.parseInt(parts[0]);
                
                // Resolve systemId for view=changes mode
                String view = request.getParameter("view");
                int systemIdToLoad = systemId;
                
                if ("changes".equals(view)) {
                    try {
                        // Only check for automatic CRs for pending changes
                        Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(SYSTEM_FACET_ID, systemId);
                        if (activeCrId != null) {
                            // Prefer impact-specific mapping for each impact table, then fall back to summary clone.
                            String impactAreaKey = null;
                            if ("products".equals(parts[1])) impactAreaKey = "impact#system_X_product";
                            if ("clients".equals(parts[1])) impactAreaKey = "impact#system_X_client";
                            if ("legals".equals(parts[1]) || "legal-entities".equals(parts[1])) impactAreaKey = "impact#system_X_legal";

                            Integer nObjectId = null;
                            if (impactAreaKey != null) {
                                nObjectId = facetChangesDAO.getNObjectId("system", systemId, impactAreaKey, activeCrId);
                            }
                            if (nObjectId == null) {
                                nObjectId = facetChangesDAO.getNObjectId("system", systemId, "summary", activeCrId);
                            }
                            if (nObjectId != null) {
                                systemIdToLoad = nObjectId;
                                //system.out.println("[SystemImpactServlet] Using cloned system ID " + nObjectId + " for relationships (view=changes)");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error getting nobject_id for view=changes: " + e.getMessage());
                    }
                }
                
                if ("products".equals(parts[1])) {
                    handleGetProductRelationships(systemIdToLoad, response, request);
                } else if ("clients".equals(parts[1])) {
                    handleGetClientRelationships(systemIdToLoad, response, request);
                } else if ("legals".equals(parts[1]) || "legal-entities".equals(parts[1])) {
                    handleGetLegalRelationships(systemIdToLoad, response, request);
                } else {
                    sendError(response, "Invalid endpoint", 400);
                }
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid system ID", 400);
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
            } else if ("clients".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveClientRelationships(request, response);
            } else if ("legals".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveLegalRelationships(request, response);
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATIONSHIP HANDLERS =====
    
    private void handleGetProductRelationships(int systemId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = systemImpactService.getProductRelationshipsBySystemId(systemId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientRelationships(int systemId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = systemImpactService.getClientRelationshipsBySystemId(systemId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Client", "clientId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetLegalRelationships(int systemId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = systemImpactService.getLegalRelationshipsBySystemId(systemId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "LegalEntity", "legalId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal relationships: " + e.getMessage(), 500);
        }
    }
    
    // ===== GET RELATION TYPE HANDLERS =====
    
    private void handleGetProductSystemRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = systemImpactService.getProductSystemRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product-system relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProductLegalRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = systemImpactService.getProductLegalRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product-legal relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = systemImpactService.getClientSystemRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetLegalRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = systemImpactService.getLegalRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal relation types: " + e.getMessage(), 500);
        }
    }
    
    // ===== GET OWNER HANDLERS =====
    
    private void handleGetProductOwner(int productId, HttpServletResponse response) 
            throws IOException {
        try {
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", systemImpactService.getProductOwnersString(productId));
            owner.put("ownerEmail", systemImpactService.getProductOwnersEmail(productId));
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product owner: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientOwner(int clientId, HttpServletResponse response) 
            throws IOException {
        try {
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", systemImpactService.getClientOwnersString(clientId));
            owner.put("ownerEmail", systemImpactService.getClientOwnersEmail(clientId));
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client owner: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetLegalOwner(int legalId, HttpServletResponse response) 
            throws IOException {
        try {
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", systemImpactService.getLegalOwnersString(legalId));
            owner.put("ownerEmail", systemImpactService.getLegalOwnersEmail(legalId));
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal owner: " + e.getMessage(), 500);
        }
    }
    
    // ===== SAVE RELATIONSHIP HANDLERS =====
    
    /**
     * Helper method to ensure nobjectId exists and save area key mapping.
     * Handles race conditions when multiple save operations happen simultaneously.
     * Uses retry logic to ensure we get the correct nobjectId even if another request creates it.
     */
    private Integer ensureNObjectIdAndSaveMapping(Connection conn, int systemId, Integer activeCrId, String areaKey) throws Exception {
        if (activeCrId == null) {
            return null;
        }
        
        // Try to get existing nobjectId - with retry to handle race conditions
        Integer nobjectId = facetChangesDAO.getNObjectId("system", systemId, "summary", activeCrId);
        
        if (nobjectId == null) {
            // Try to create it - another request might be doing the same thing simultaneously
            nobjectId = cloneSystemRow(conn, systemId);
            if (nobjectId != null) {
                // Save the summary mapping
                facetChangesDAO.saveMapping("system", systemId, nobjectId, "summary", activeCrId);
                
                // Double-check: if another request created a mapping while we were cloning,
                // we should use that one instead to avoid duplicate clones
                Integer existingNObjectId = facetChangesDAO.getNObjectId("system", systemId, "summary", activeCrId);
                if (existingNObjectId != null && !existingNObjectId.equals(nobjectId)) {
                    // Another request created the clone first - use that one instead
                    nobjectId = existingNObjectId;
                }
            } else {
                // Clone failed, but another request might have succeeded - retry getting it
                nobjectId = facetChangesDAO.getNObjectId("system", systemId, "summary", activeCrId);
            }
        }
        
        // Always save the area key mapping, even if nobjectId was already created by another request
        // This ensures all three area keys (product, client, legal) are saved regardless of which request runs first
        if (nobjectId != null && areaKey != null) {
            facetChangesDAO.saveMapping("system", systemId, nobjectId, areaKey, activeCrId);
        }
        
        return nobjectId;
    }

    private void handleSaveProductRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            JsonObject jsonData = gson.fromJson(jsonBody.toString(), JsonObject.class);
            
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                sendError(response, "User authentication required", 401);
                return;
            }
            
            int systemId = jsonData.get("systemId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "System", systemId)) {
                return; // Response already sent
            }

            if (userId > 0) {
                try {
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                    new DFCRService().ensureEditAutoCrIfMissing("System", SYSTEM_FACET_ID, systemId, getSystemType(systemId), userId, isAdmin);
                } catch (Exception e) {
                    System.err.println("[SystemImpactServlet] DFCR ensure before product impact save: " + e.getMessage());
                }
            }
            
            JsonElement relationshipsElement = jsonData.get("relationships");
            
            if (relationshipsElement == null || !relationshipsElement.isJsonArray()) {
                sendError(response, "Invalid request: relationships array is required", 400);
                return;
            }
            
            List<Map<String, Object>> relationships = new ArrayList<>();
            for (JsonElement element : relationshipsElement.getAsJsonArray()) {
                JsonObject rel = element.getAsJsonObject();
                Map<String, Object> relationship = new HashMap<>();
                
                if (rel.has("productId") && !rel.get("productId").isJsonNull()) {
                    relationship.put("productId", rel.get("productId").getAsInt());
                }
                if (rel.has("productSystemRelationType") && !rel.get("productSystemRelationType").isJsonNull()) {
                    relationship.put("productSystemRelationType", rel.get("productSystemRelationType").getAsInt());
                }
                if (rel.has("legalId") && !rel.get("legalId").isJsonNull()) {
                    relationship.put("legalId", rel.get("legalId").getAsInt());
                }
                if (rel.has("legalRelationType") && !rel.get("legalRelationType").isJsonNull()) {
                    relationship.put("legalRelationType", rel.get("legalRelationType").getAsInt());
                }
                
                relationships.add(relationship);
            }
            
            // If this system has an active CR, ensure we save to cloned system ID and write mapping in system_changes
            Integer activeCrId = null;
            int systemIdToUse = systemId;
            try (Connection conn = DatabaseConnection.getConnection()) {
                // Only check for automatic CRs for pending changes
                activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(SYSTEM_FACET_ID, systemId);
                if (activeCrId != null) {
                    Integer nobjectId = ensureNObjectIdAndSaveMapping(conn, systemId, activeCrId, "impact#system_X_product");
                    if (nobjectId != null) {
                        systemIdToUse = nobjectId;
                    }
                }
            }

            var productValidationResult = ImpactSegmentValidationUtil.validateRelationships(
                    systemIdToUse, "System", relationships, "Product", "productId");
            if (!productValidationResult.isValid) {
                sendError(response, productValidationResult.message, 400);
                return;
            }

            var legalValidationResult = ImpactSegmentValidationUtil.validateRelationships(
                    systemIdToUse, "System", relationships, "LegalEntity", "legalId");
            if (!legalValidationResult.isValid) {
                sendError(response, legalValidationResult.message, 400);
                return;
            }

            boolean success = systemImpactService.saveProductRelationships(systemIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                result.put("message", activeCrId != null
                        ? "Product relationships saved as pending. They will apply when the Change Request is completed."
                        : "Product relationships saved successfully");
                if (activeCrId != null) {
                    result.put("pending", true);
                    result.put("changeRequestId", activeCrId);
                }
            } else {
                result.put("message", "Failed to save product relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save product relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleSaveClientRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            JsonObject jsonData = gson.fromJson(jsonBody.toString(), JsonObject.class);
            
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                sendError(response, "User authentication required", 401);
                return;
            }
            
            int systemId = jsonData.get("systemId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "System", systemId)) {
                return; // Response already sent
            }

            if (userId > 0) {
                try {
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                    new DFCRService().ensureEditAutoCrIfMissing("System", SYSTEM_FACET_ID, systemId, getSystemType(systemId), userId, isAdmin);
                } catch (Exception e) {
                    System.err.println("[SystemImpactServlet] DFCR ensure before client impact save: " + e.getMessage());
                }
            }
            
            JsonElement relationshipsElement = jsonData.get("relationships");
            
            if (relationshipsElement == null || !relationshipsElement.isJsonArray()) {
                sendError(response, "Invalid request: relationships array is required", 400);
                return;
            }
            
            List<Map<String, Object>> relationships = new ArrayList<>();
            for (JsonElement element : relationshipsElement.getAsJsonArray()) {
                JsonObject rel = element.getAsJsonObject();
                Map<String, Object> relationship = new HashMap<>();
                
                if (rel.has("clientId") && !rel.get("clientId").isJsonNull()) {
                    relationship.put("clientId", rel.get("clientId").getAsInt());
                }
                if (rel.has("relationType") && !rel.get("relationType").isJsonNull()) {
                    relationship.put("relationType", rel.get("relationType").getAsInt());
                }
                
                relationships.add(relationship);
            }
            
            Integer activeCrId = null;
            int systemIdToUse = systemId;
            try (Connection conn = DatabaseConnection.getConnection()) {
                // Only check for automatic CRs for pending changes
                activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(SYSTEM_FACET_ID, systemId);
                if (activeCrId != null) {
                    Integer nobjectId = ensureNObjectIdAndSaveMapping(conn, systemId, activeCrId, "impact#system_X_client");
                    if (nobjectId != null) {
                        systemIdToUse = nobjectId;
                    }
                }
            }

            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    systemIdToUse, "System", relationships, "Client", "clientId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = systemImpactService.saveClientRelationships(systemIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                result.put("message", activeCrId != null
                        ? "Client relationships saved as pending. They will apply when the Change Request is completed."
                        : "Client relationships saved successfully");
                if (activeCrId != null) {
                    result.put("pending", true);
                    result.put("changeRequestId", activeCrId);
                }
            } else {
                result.put("message", "Failed to save client relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save client relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleSaveLegalRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            JsonObject jsonData = gson.fromJson(jsonBody.toString(), JsonObject.class);
            
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                sendError(response, "User authentication required", 401);
                return;
            }
            
            int systemId = jsonData.get("systemId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "System", systemId)) {
                return; // Response already sent
            }

            if (userId > 0) {
                try {
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                    new DFCRService().ensureEditAutoCrIfMissing("System", SYSTEM_FACET_ID, systemId, getSystemType(systemId), userId, isAdmin);
                } catch (Exception e) {
                    System.err.println("[SystemImpactServlet] DFCR ensure before legal impact save: " + e.getMessage());
                }
            }
            JsonElement relationshipsElement = jsonData.get("relationships");
            
            if (relationshipsElement == null || !relationshipsElement.isJsonArray()) {
                sendError(response, "Invalid request: relationships array is required", 400);
                return;
            }
            
            List<Map<String, Object>> relationships = new ArrayList<>();
            for (JsonElement element : relationshipsElement.getAsJsonArray()) {
                JsonObject rel = element.getAsJsonObject();
                Map<String, Object> relationship = new HashMap<>();
                
                if (rel.has("legalId") && !rel.get("legalId").isJsonNull()) {
                    relationship.put("legalId", rel.get("legalId").getAsInt());
                }
                if (rel.has("relationType") && !rel.get("relationType").isJsonNull()) {
                    relationship.put("relationType", rel.get("relationType").getAsInt());
                }
                
                relationships.add(relationship);
            }
            
            Integer activeCrId = null;
            int systemIdToUse = systemId;
            try (Connection conn = DatabaseConnection.getConnection()) {
                // Only check for automatic CRs for pending changes
                activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(SYSTEM_FACET_ID, systemId);
                if (activeCrId != null) {
                    Integer nobjectId = ensureNObjectIdAndSaveMapping(conn, systemId, activeCrId, "impact#system_X_legal");
                    if (nobjectId != null) {
                        systemIdToUse = nobjectId;
                    }
                }
            }

            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    systemIdToUse, "System", relationships, "LegalEntity", "legalId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = systemImpactService.saveLegalRelationships(systemIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                result.put("message", activeCrId != null
                        ? "Legal relationships saved as pending. They will apply when the Change Request is completed."
                        : "Legal relationships saved successfully");
                if (activeCrId != null) {
                    result.put("pending", true);
                    result.put("changeRequestId", activeCrId);
                }
            } else {
                result.put("message", "Failed to save legal relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save legal relationships: " + e.getMessage(), 500);
        }
    }
    
    // ===== REVERSE LOOKUP HANDLERS =====
    
    private void handleGetSystemRelationshipsByProductId(int productId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = systemImpactService.getSystemRelationshipsByProductId(productId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "System", "systemId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve system relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetSystemRelationshipsByClientId(int clientId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = systemImpactService.getSystemRelationshipsByClientId(clientId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "System", "systemId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve system relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetSystemRelationshipsByLegalId(int legalId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = systemImpactService.getSystemRelationshipsByLegalId(legalId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "System", "systemId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve system relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetSystemRelationshipsByCapabilityId(int capabilityId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            com.example.budg_v2.service.CapabilityImpactService capabilityImpactService = new com.example.budg_v2.service.CapabilityImpactService();
            List<Map<String, Object>> relationships = capabilityImpactService.getSystemRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "System", "systemId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve system relationships: " + e.getMessage(), 500);
        }
    }
    
    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        response.getWriter().write(gson.toJson(error));
    }

    private Integer getSystemType(int systemId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT Type FROM system WHERE ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, systemId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int type = rs.getInt("Type");
                        return rs.wasNull() ? null : type;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SystemImpactServlet] Error getting system type: " + e.getMessage());
        }
        return null;
    }

    /**
     * Clone a system row for pending changes (same as SystemServlet.cloneSystemRow).
     */
    private Integer cloneSystemRow(Connection conn, int originalId) throws Exception {
        String sql = "INSERT INTO system (" +
                "Name, Long_Name, Description, Type, External, URL, " +
                "Status, Lifecycle, Is_Public, Confidentiality_Rating, " +
                "Integrity_Rating, Availability_Rating, AssetID, Classification, " +
                "DQ_Automation, Parent_ID, CreatedBy_ID, Created_Datetime, " +
                "Last_Updated_Datetime, Last_updated_userID" +
                ") SELECT " +
                "Name, Long_Name, Description, Type, External, URL, " +
                "Status, Lifecycle, Is_Public, Confidentiality_Rating, " +
                "Integrity_Rating, Availability_Rating, AssetID, Classification, " +
                "DQ_Automation, Parent_ID, CreatedBy_ID, Created_Datetime, " +
                "Last_Updated_Datetime, Last_updated_userID " +
                "FROM system WHERE ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }
}

