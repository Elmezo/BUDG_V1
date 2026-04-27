package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.GlossaryImpactService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/glossary-impact/*")
public class GlossaryImpactServlet extends HttpServlet {
    private static final int GLOSSARY_FACET_TYPE = 12;
    private static final Logger logger = LoggerFactory.getLogger(GlossaryImpactServlet.class);
    private final GlossaryImpactService glossaryImpactService;
    private final Gson gson = new Gson();

    public GlossaryImpactServlet() {
        this.glossaryImpactService = new GlossaryImpactService();
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
            
            // Check for reverse lookup first: /api/glossary-impact/{entityType}s/{entityId}/glossaries
            if (parts.length >= 3) {
                if ("businessareas".equals(parts[0])) {
                    try {
                        int businessAreaId = Integer.parseInt(parts[1]);
                        if ("glossaries".equals(parts[2])) {
                            handleGetGlossaryRelationshipsByBusinessAreaId(businessAreaId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid business area ID: " + parts[1], 400);
                        return;
                    }
                } else if ("processes".equals(parts[0])) {
                    try {
                        int processId = Integer.parseInt(parts[1]);
                        if ("glossaries".equals(parts[2])) {
                            handleGetGlossaryRelationshipsByProcessId(processId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid process ID: " + parts[1], 400);
                        return;
                    }
                } else if ("policies".equals(parts[0])) {
                    try {
                        int policyId = Integer.parseInt(parts[1]);
                        if ("glossaries".equals(parts[2])) {
                            handleGetGlossaryRelationshipsByPolicyId(policyId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid policy ID: " + parts[1], 400);
                        return;
                    }
                } else if ("capabilities".equals(parts[0])) {
                    try {
                        int capabilityId = Integer.parseInt(parts[1]);
                        if ("glossaries".equals(parts[2])) {
                            handleGetGlossaryRelationshipsByCapabilityId(capabilityId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid capability ID: " + parts[1], 400);
                        return;
                    }
                } else if ("clients".equals(parts[0])) {
                    try {
                        int clientId = Integer.parseInt(parts[1]);
                        if ("glossaries".equals(parts[2])) {
                            handleGetGlossaryRelationshipsByClientId(clientId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid client ID: " + parts[1], 400);
                        return;
                    }
                } else if ("products".equals(parts[0])) {
                    try {
                        int productId = Integer.parseInt(parts[1]);
                        if ("glossaries".equals(parts[2])) {
                            handleGetGlossaryRelationshipsByProductId(productId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid product ID: " + parts[1], 400);
                        return;
                    }
                }
            }
            
            if ("product-relation-types".equals(parts[0])) {
                handleGetProductRelationTypes(response);
            } else if ("product-owner".equals(parts[0]) && parts.length >= 2) {
                int productId = Integer.parseInt(parts[1]);
                handleGetProductOwner(productId, response);
            } else if ("client-relation-types".equals(parts[0])) {
                handleGetClientRelationTypes(response);
            } else if ("client-owner".equals(parts[0]) && parts.length >= 2) {
                int clientId = Integer.parseInt(parts[1]);
                handleGetClientOwner(clientId, response);
            } else if (parts.length >= 2) {
                int glossaryId = Integer.parseInt(parts[0]);
                
                if ("products".equals(parts[1])) {
                    handleGetProductRelationships(glossaryId, response, request);
                } else if ("clients".equals(parts[1])) {
                    handleGetClientRelationships(glossaryId, response, request);
                } else {
                    sendError(response, "Invalid endpoint", 400);
                }
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid glossary ID", 400);
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
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATIONSHIP HANDLERS =====
    
    private void handleGetProductRelationships(int glossaryId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            // Check if viewing changes (pending changes mode)
            String view = request.getParameter("view");
            int glossaryIdToLoad = glossaryId;
            
            if ("changes".equals(view)) {
                try {
                    com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
                    // Only check for automatic CRs for pending changes
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_TYPE, glossaryId);
                    if (activeCrId != null) {
                        // Check for impact-specific mapping first
                        Integer nObjectId = facetChangesDAO.getNObjectId("glossary", glossaryId, "impact#glossary_X_product", activeCrId);
                        // If no impact-specific mapping, fall back to summary mapping (cloned glossary ID)
                        if (nObjectId == null) {
                            nObjectId = facetChangesDAO.getNObjectId("glossary", glossaryId, "summary", activeCrId);
                        }
                        if (nObjectId != null) {
                            glossaryIdToLoad = nObjectId;
                            //system.out.println("[GlossaryImpactServlet] Using cloned glossary ID " + nObjectId + " for product relationships (view=changes)");
                        }
                    }
                } catch (Exception e) {
                    // Fall back to original ID if there's an error
                    System.err.println("Error getting nobject_id for view=changes: " + e.getMessage());
                }
            }
            
            List<Map<String, Object>> relationships = glossaryImpactService.getProductRelationshipsByGlossaryId(glossaryIdToLoad);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientRelationships(int glossaryId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            // Check if viewing changes (pending changes mode)
            String view = request.getParameter("view");
            int glossaryIdToLoad = glossaryId;
            
            if ("changes".equals(view)) {
                try {
                    com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
                    // Only check for automatic CRs for pending changes
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_TYPE, glossaryId);
                    if (activeCrId != null) {
                        // Check for impact-specific mapping first
                        Integer nObjectId = facetChangesDAO.getNObjectId("glossary", glossaryId, "impact#glossary_X_client", activeCrId);
                        // If no impact-specific mapping, fall back to summary mapping (cloned glossary ID)
                        if (nObjectId == null) {
                            nObjectId = facetChangesDAO.getNObjectId("glossary", glossaryId, "summary", activeCrId);
                        }
                        if (nObjectId != null) {
                            glossaryIdToLoad = nObjectId;
                            //system.out.println("[GlossaryImpactServlet] Using cloned glossary ID " + nObjectId + " for client relationships (view=changes)");
                        }
                    }
                } catch (Exception e) {
                    // Fall back to original ID if there's an error
                    System.err.println("Error getting nobject_id for view=changes: " + e.getMessage());
                }
            }
            
            List<Map<String, Object>> relationships = glossaryImpactService.getClientRelationshipsByGlossaryId(glossaryIdToLoad);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Client", "clientId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("GlossaryImpactServlet: Error in handleGetClientRelationships for glossaryId " + glossaryId);
            System.err.println("Error message: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Caused by: " + e.getCause().getMessage());
            }
            sendError(response, "Failed to retrieve client relationships: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATION TYPE HANDLERS =====
    
    private void handleGetProductRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = glossaryImpactService.getProductRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = glossaryImpactService.getClientRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client relation types: " + e.getMessage(), 500);
        }
    }

    // ===== GET OWNER HANDLERS =====
    
    private void handleGetProductOwner(int productId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = glossaryImpactService.getProductOwnersString(productId);
            String ownerEmail = glossaryImpactService.getProductOwnersEmail(productId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("ownerName", ownerName != null ? ownerName : "No owner");
            result.put("productOwnerName", ownerName != null ? ownerName : "No owner");
            result.put("ownerEmail", ownerEmail);
            result.put("productOwnerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product owner: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientOwner(int clientId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = glossaryImpactService.getClientOwnersString(clientId);
            String ownerEmail = glossaryImpactService.getClientOwnersEmail(clientId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("ownerName", ownerName != null ? ownerName : "No owner");
            result.put("clientOwnerName", ownerName != null ? ownerName : "No owner");
            result.put("ownerEmail", ownerEmail);
            result.put("clientOwnerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client owner: " + e.getMessage(), 500);
        }
    }

    // ===== SAVE HANDLERS =====
    
    private void handleSaveProductRelationships(HttpServletRequest request, HttpServletResponse response) 
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
            int originalGlossaryId = jsonObject.get("glossaryId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Glossary", originalGlossaryId)) {
                return; // Response already sent
            }

            if (userId > 0) {
                try {
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                    new DFCRService().ensureEditAutoCrIfMissing("Glossary", GLOSSARY_FACET_TYPE, originalGlossaryId, null, userId, isAdmin);
                } catch (Exception e) {
                    logger.warn("[GlossaryImpact] DFCR ensure before product impact save: {}", e.getMessage());
                }
            }
            
            JsonElement relationshipsElement = jsonObject.get("relationships");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relationships = new ArrayList<>();
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                relationships = gson.fromJson(relationshipsElement, List.class);
            }
            
            // Check if this object has an active auto-created CR (only automatic CRs use pending changes)
            com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_TYPE, originalGlossaryId);
            int glossaryIdToUse = originalGlossaryId;
            
            if (activeCrId != null) {
                // Object is under revision - use cloned glossary ID
                try {
                    // Get or create mapping for 'summary' area (this creates the cloned glossary if needed)
                    Integer nobjectId = facetChangesDAO.getNObjectId("glossary", originalGlossaryId, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // No summary mapping exists yet - need to clone the glossary row first
                        // This should have been done when summary was edited, but handle it here just in case
                        nobjectId = cloneGlossaryRow(originalGlossaryId);
                        if (nobjectId != null) {
                            facetChangesDAO.saveMapping("glossary", originalGlossaryId, nobjectId, "summary", activeCrId);
                        }
                    }
                    
                    if (nobjectId != null) {
                        glossaryIdToUse = nobjectId;
                        // Create mapping for impact#glossary_X_product area
                        facetChangesDAO.saveMapping("glossary", originalGlossaryId, nobjectId, "impact#glossary_X_product", activeCrId);
                        logger.info("🔗 [RELATIONSHIPS] Saving product relationships to cloned glossary ID {} (original: {}) for CR {}", nobjectId, originalGlossaryId, activeCrId);
                    } else {
                        logger.warn("⚠️  [RELATIONSHIPS] Could not get/create cloned glossary for product relationships, using original ID {}", originalGlossaryId);
                    }
                } catch (Exception e) {
                    System.err.println("[GlossaryImpactServlet] Error handling pending changes for product relationships: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            logger.info("🔗 [RELATIONSHIPS] [GlossaryImpactServlet] About to save product relationships: glossaryIdToUse={}, originalGlossaryId={}, activeCrId={}, relationshipsCount={}", 
                glossaryIdToUse, originalGlossaryId, activeCrId, relationships != null ? relationships.size() : 0);
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    glossaryIdToUse, "Glossary", relationships, "Product", "productId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }
            boolean success = glossaryImpactService.saveProductRelationships(glossaryIdToUse, relationships, userId);
            logger.info("🔗 [RELATIONSHIPS] [GlossaryImpactServlet] Product relationships save result: success={}, glossaryIdToUse={}", success, glossaryIdToUse);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (activeCrId != null) {
                result.put("message", "Product relationships saved as pending. They will apply when the Change Request is completed.");
                result.put("pendingChanges", true);
                result.put("changeRequestId", activeCrId);
            } else {
                result.put("message", success ? "Product relationships saved successfully" : "Failed to save product relationships");
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
            int originalGlossaryId = jsonObject.get("glossaryId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Glossary", originalGlossaryId)) {
                return; // Response already sent
            }

            if (userId > 0) {
                try {
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                    new DFCRService().ensureEditAutoCrIfMissing("Glossary", GLOSSARY_FACET_TYPE, originalGlossaryId, null, userId, isAdmin);
                } catch (Exception e) {
                    logger.warn("[GlossaryImpact] DFCR ensure before client impact save: {}", e.getMessage());
                }
            }
            
            JsonElement relationshipsElement = jsonObject.get("relationships");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relationships = new ArrayList<>();
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                relationships = gson.fromJson(relationshipsElement, List.class);
            }
            
            // Check if this object has an active auto-created CR (only automatic CRs use pending changes)
            com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_TYPE, originalGlossaryId);
            int glossaryIdToUse = originalGlossaryId;
            
            if (activeCrId != null) {
                // Object is under revision - use cloned glossary ID
                try {
                    // Get or create mapping for 'summary' area (this creates the cloned glossary if needed)
                    Integer nobjectId = facetChangesDAO.getNObjectId("glossary", originalGlossaryId, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // No summary mapping exists yet - need to clone the glossary row first
                        // This should have been done when summary was edited, but handle it here just in case
                        nobjectId = cloneGlossaryRow(originalGlossaryId);
                        if (nobjectId != null) {
                            facetChangesDAO.saveMapping("glossary", originalGlossaryId, nobjectId, "summary", activeCrId);
                        }
                    }
                    
                    if (nobjectId != null) {
                        glossaryIdToUse = nobjectId;
                        // Create mapping for impact#glossary_X_client area
                        facetChangesDAO.saveMapping("glossary", originalGlossaryId, nobjectId, "impact#glossary_X_client", activeCrId);
                        logger.info("🔗 [RELATIONSHIPS] Saving client relationships to cloned glossary ID {} (original: {}) for CR {}", nobjectId, originalGlossaryId, activeCrId);
                    } else {
                        logger.warn("⚠️  [RELATIONSHIPS] Could not get/create cloned glossary for client relationships, using original ID {}", originalGlossaryId);
                    }
                } catch (Exception e) {
                    System.err.println("[GlossaryImpactServlet] Error handling pending changes for client relationships: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            logger.info("🔗 [RELATIONSHIPS] [GlossaryImpactServlet] About to save client relationships: glossaryIdToUse={}, originalGlossaryId={}, activeCrId={}, relationshipsCount={}", 
                glossaryIdToUse, originalGlossaryId, activeCrId, relationships != null ? relationships.size() : 0);
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    glossaryIdToUse, "Glossary", relationships, "Client", "clientId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }
            boolean success = glossaryImpactService.saveClientRelationships(glossaryIdToUse, relationships, userId);
            logger.info("🔗 [RELATIONSHIPS] [GlossaryImpactServlet] Client relationships save result: success={}, glossaryIdToUse={}", success, glossaryIdToUse);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (activeCrId != null) {
                result.put("message", "Client relationships saved as pending. They will apply when the Change Request is completed.");
                result.put("pendingChanges", true);
                result.put("changeRequestId", activeCrId);
            } else {
                result.put("message", success ? "Client relationships saved successfully" : "Failed to save client relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save client relationships: " + e.getMessage(), 500);
        }
    }

    // ===== REVERSE LOOKUP HANDLERS =====
    
    private void handleGetGlossaryRelationshipsByBusinessAreaId(int businessAreaId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = glossaryImpactService.getGlossaryRelationshipsByBusinessAreaId(businessAreaId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Glossary", "glossaryId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetGlossaryRelationshipsByProcessId(int processId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = glossaryImpactService.getGlossaryRelationshipsByProcessId(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Glossary", "glossaryId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetGlossaryRelationshipsByPolicyId(int policyId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = glossaryImpactService.getGlossaryRelationshipsByPolicyId(policyId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Glossary", "glossaryId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetGlossaryRelationshipsByCapabilityId(int capabilityId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = glossaryImpactService.getGlossaryRelationshipsByCapabilityId(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Glossary", "glossaryId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetGlossaryRelationshipsByClientId(int clientId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = glossaryImpactService.getGlossaryRelationshipsByClientId(clientId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Glossary", "glossaryId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetGlossaryRelationshipsByProductId(int productId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = glossaryImpactService.getGlossaryRelationshipsByProductId(productId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Glossary", "glossaryId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary relationships: " + e.getMessage(), 500);
        }
    }
    
    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        response.getWriter().write(gson.toJson(error));
    }
    
    /**
     * Clone a glossary row for pending changes
     * Returns the ID of the cloned row, or null on error
     */
    private Integer cloneGlossaryRow(int originalId) throws SQLException {
        String sql = "INSERT INTO glossary (" +
                "Name, Description, Format, LDM, Business_Logic, Examples, Ref_Number, " +
                "Format_type, Parent_ID, Status, Lifecycle, Is_Public, Type, " +
                "Security_Classification, KDE, Confidentiality_Rating, Integrity_Rating, " +
                "Availability_Rating, CreatedBy_ID, Created_Datetime, Last_updated_userID, Last_Updated_Datetime" +
                ") SELECT " +
                "Name, Description, Format, LDM, Business_Logic, Examples, Ref_Number, " +
                "Format_type, Parent_ID, Status, Lifecycle, Is_Public, Type, " +
                "Security_Classification, KDE, Confidentiality_Rating, Integrity_Rating, " +
                "Availability_Rating, CreatedBy_ID, Created_Datetime, Last_updated_userID, Last_Updated_Datetime " +
                "FROM glossary WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

