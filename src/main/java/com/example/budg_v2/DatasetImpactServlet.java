package com.example.budg_v2;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.service.DatasetImpactService;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.budg_v2.database.DatabaseConnection;

@WebServlet("/api/dataset-impact/*")
public class DatasetImpactServlet extends HttpServlet {
    private static final int DATASET_FACET_ID = 11;
    private final DatasetImpactService datasetImpactService;
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private final Gson gson = new Gson();

    public DatasetImpactServlet() {
        this.datasetImpactService = new DatasetImpactService();
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
            
            // Check for reverse lookup first: /api/dataset-impact/{entityType}s/{entityId}/datasets
            if (parts.length >= 3) {
                if ("products".equals(parts[0])) {
                    try {
                        int productId = Integer.parseInt(parts[1]);
                        if ("datasets".equals(parts[2])) {
                            handleGetDatasetRelationshipsByProductId(productId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid product ID: " + parts[1], 400);
                        return;
                    }
                } else if ("clients".equals(parts[0])) {
                    try {
                        int clientId = Integer.parseInt(parts[1]);
                        if ("datasets".equals(parts[2])) {
                            handleGetDatasetRelationshipsByClientId(clientId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid client ID: " + parts[1], 400);
                        return;
                    }
                } else if ("legals".equals(parts[0]) || "legal-entities".equals(parts[0])) {
                    try {
                        int legalId = Integer.parseInt(parts[1]);
                        if ("datasets".equals(parts[2])) {
                            handleGetDatasetRelationshipsByLegalId(legalId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid legal ID: " + parts[1], 400);
                        return;
                    }
                }
            }
            
            if ("product-relation-types".equals(parts[0])) {
                handleGetProductRelationTypes(response);
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
                int datasetId = Integer.parseInt(parts[0]);
                
                if ("products".equals(parts[1])) {
                    handleGetProductRelationships(datasetId, response, request);
                } else if ("clients".equals(parts[1])) {
                    handleGetClientRelationships(datasetId, response, request);
                } else if ("legals".equals(parts[1]) || "legal-entities".equals(parts[1])) {
                    handleGetLegalRelationships(datasetId, response, request);
                } else {
                    sendError(response, "Invalid endpoint", 400);
                }
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid dataset ID", 400);
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
    
    private void handleGetProductRelationships(int datasetId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            // Check if viewing changes (pending changes mode)
            String view = request.getParameter("view");
            int datasetIdToLoad = datasetId;
            
            if ("changes".equals(view)) {
                try {
                    // ⚠️ CRITICAL: Only check for automatic CRs (mandatory_workflow = true)
                    // Manual CRs should NOT trigger pending changes logic
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_ID, datasetId);
                    if (activeCrId != null) {
                        Integer nObjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "impact#dataset_X_product", activeCrId);
                        if (nObjectId == null) {
                            nObjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                        }
                        if (nObjectId != null) {
                            datasetIdToLoad = nObjectId;
                            //system.out.println("[DatasetImpactServlet] Using cloned dataset ID " + nObjectId + " for product relationships (view=changes)");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error getting nobject_id for view=changes: " + e.getMessage());
                }
            }
            
            List<Map<String, Object>> relationships = datasetImpactService.getProductRelationshipsByDatasetId(datasetIdToLoad);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientRelationships(int datasetId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            // Check if viewing changes (pending changes mode)
            String view = request.getParameter("view");
            int datasetIdToLoad = datasetId;
            
            if ("changes".equals(view)) {
                try {
                    // ⚠️ CRITICAL: Only check for automatic CRs (mandatory_workflow = true)
                    // Manual CRs should NOT trigger pending changes logic
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_ID, datasetId);
                    if (activeCrId != null) {
                        Integer nObjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "impact#dataset_X_client", activeCrId);
                        if (nObjectId == null) {
                            nObjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                        }
                        if (nObjectId != null) {
                            datasetIdToLoad = nObjectId;
                            //system.out.println("[DatasetImpactServlet] Using cloned dataset ID " + nObjectId + " for client relationships (view=changes)");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error getting nobject_id for view=changes: " + e.getMessage());
                }
            }
            
            List<Map<String, Object>> relationships = datasetImpactService.getClientRelationshipsByDatasetId(datasetIdToLoad);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Client", "clientId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetLegalRelationships(int datasetId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            // Check if viewing changes (pending changes mode)
            String view = request.getParameter("view");
            int datasetIdToLoad = datasetId;
            
            if ("changes".equals(view)) {
                try {
                    // ⚠️ CRITICAL: Only check for automatic CRs (mandatory_workflow = true)
                    // Manual CRs should NOT trigger pending changes logic
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_ID, datasetId);
                    if (activeCrId != null) {
                        Integer nObjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "impact#dataset_X_legal", activeCrId);
                        if (nObjectId == null) {
                            nObjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                        }
                        if (nObjectId != null) {
                            datasetIdToLoad = nObjectId;
                            //system.out.println("[DatasetImpactServlet] Using cloned dataset ID " + nObjectId + " for legal relationships (view=changes)");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error getting nobject_id for view=changes: " + e.getMessage());
                }
            }
            
            List<Map<String, Object>> relationships = datasetImpactService.getLegalRelationshipsByDatasetId(datasetIdToLoad);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "LegalEntity", "legalId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal relationships: " + e.getMessage(), 500);
        }
    }
    
    // ===== GET RELATION TYPE HANDLERS =====
    
    private void handleGetProductRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = datasetImpactService.getProductRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = datasetImpactService.getClientRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetLegalRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = datasetImpactService.getLegalRelationTypes();
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
            owner.put("ownerName", datasetImpactService.getProductOwnersString(productId));
            owner.put("ownerEmail", datasetImpactService.getProductOwnersEmail(productId));
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
            owner.put("ownerName", datasetImpactService.getClientOwnersString(clientId));
            owner.put("ownerEmail", datasetImpactService.getClientOwnersEmail(clientId));
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
            owner.put("ownerName", datasetImpactService.getLegalOwnersString(legalId));
            owner.put("ownerEmail", datasetImpactService.getLegalOwnersEmail(legalId));
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal owner: " + e.getMessage(), 500);
        }
    }
    
    // ===== SAVE RELATIONSHIP HANDLERS =====
    
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
            int datasetId = jsonData.get("datasetId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Data Sets", datasetId)) {
                return; // Response already sent
            }
            
            JsonElement relationshipsElement = jsonData.get("relationships");
            List<Map<String, Object>> relationships = new ArrayList<>();
            
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                for (JsonElement element : relationshipsElement.getAsJsonArray()) {
                    JsonObject rel = element.getAsJsonObject();
                    Map<String, Object> relationshipMap = new HashMap<>();
                    
                    if (rel.has("productId") && !rel.get("productId").isJsonNull()) {
                        relationshipMap.put("productId", rel.get("productId").getAsInt());
                    }
                    if (rel.has("relationType") && !rel.get("relationType").isJsonNull()) {
                        relationshipMap.put("relationType", rel.get("relationType").getAsInt());
                    }
                    if (rel.has("description")) {
                        relationshipMap.put("description", rel.get("description").isJsonNull() ? null : rel.get("description").getAsString());
                    }
                    
                    relationships.add(relationshipMap);
                }
            }
            
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User authentication required\"}");
                return;
            }
            
            // ⚠️ CRITICAL: Only check for automatic CRs (mandatory_workflow = true)
            // Manual CRs should NOT trigger pending changes logic
            // Check if dataset is under revision (has active auto-created CR)
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_ID, datasetId);
            
            // If no CR exists, check if DFCR edit workflow is enabled and auto-create CR
            if (activeCrId == null) {
                try {
                    boolean isAdmin = com.example.budg_v2.util.UserContextUtil.isCurrentUserAdmin(request);
                    Integer datasetType = getDatasetType(datasetId);
                    com.example.budg_v2.service.DFCRService dfcrService = new com.example.budg_v2.service.DFCRService();
                    Integer autoCrId = dfcrService.applyDefaultsOnEdit("Data Set", datasetId, datasetType, userId, isAdmin);
                    if (autoCrId != null) {
                        //system.out.println("[DatasetImpactServlet] DFCR auto-created CR: " + autoCrId);
                        activeCrId = autoCrId;
                    }
                } catch (Exception e) {
                    System.err.println("[DatasetImpactServlet] Error checking/creating DFCR CR: " + e.getMessage());
                }
            }
            
            int datasetIdToUse = datasetId;
            
            if (activeCrId != null) {
                // Object is under revision - use cloned dataset ID
                try {
                    // Get or create mapping for 'summary' area (this creates the cloned dataset if needed)
                    Integer nobjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // No summary mapping exists yet - need to clone the dataset row first
                        // This should have been done when summary was edited, but handle it here just in case
                        nobjectId = cloneDatasetRow(datasetId);
                        if (nobjectId != null) {
                            facetChangesDAO.saveMapping("dataset", datasetId, nobjectId, "summary", activeCrId);
                        }
                    }
                    
                    if (nobjectId != null) {
                        datasetIdToUse = nobjectId;
                        // Create mapping for impact#dataset_X_product area
                        facetChangesDAO.saveMapping("dataset", datasetId, nobjectId, "impact#dataset_X_product", activeCrId);
                        //system.out.println("[DatasetImpactServlet] Saving product relationships to cloned dataset ID " + nobjectId + " (original: " + datasetId + ")");
                    }
                } catch (Exception e) {
                    System.err.println("[DatasetImpactServlet] Error handling pending changes for product relationships: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    datasetIdToUse, "Dataset", relationships, "Product", "productId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = datasetImpactService.saveProductRelationships(datasetIdToUse, relationships, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.addProperty("message", "Product relationships saved as pending. They will apply when the Change Request is completed.");
                    result.addProperty("pendingChanges", true);
                    result.addProperty("changeRequestId", activeCrId);
                } else {
                    result.addProperty("message", "Product relationships saved successfully");
                }
            } else {
                result.addProperty("message", "Failed to save product relationships");
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
            int datasetId = jsonData.get("datasetId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Data Sets", datasetId)) {
                return; // Response already sent
            }
            
            JsonElement relationshipsElement = jsonData.get("relationships");
            List<Map<String, Object>> relationships = new ArrayList<>();
            
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                for (JsonElement element : relationshipsElement.getAsJsonArray()) {
                    JsonObject rel = element.getAsJsonObject();
                    Map<String, Object> relationshipMap = new HashMap<>();
                    
                    if (rel.has("clientId") && !rel.get("clientId").isJsonNull()) {
                        relationshipMap.put("clientId", rel.get("clientId").getAsInt());
                    }
                    if (rel.has("relationType") && !rel.get("relationType").isJsonNull()) {
                        relationshipMap.put("relationType", rel.get("relationType").getAsInt());
                    }
                    if (rel.has("description")) {
                        relationshipMap.put("description", rel.get("description").isJsonNull() ? null : rel.get("description").getAsString());
                    }
                    
                    relationships.add(relationshipMap);
                }
            }
            
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User authentication required\"}");
                return;
            }
            
            // ⚠️ CRITICAL: Only check for automatic CRs (mandatory_workflow = true)
            // Manual CRs should NOT trigger pending changes logic
            // Check if dataset is under revision (has active auto-created CR)
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_ID, datasetId);
            
            // If no CR exists, check if DFCR edit workflow is enabled and auto-create CR
            if (activeCrId == null) {
                try {
                    boolean isAdmin = com.example.budg_v2.util.UserContextUtil.isCurrentUserAdmin(request);
                    Integer datasetType = getDatasetType(datasetId);
                    com.example.budg_v2.service.DFCRService dfcrService = new com.example.budg_v2.service.DFCRService();
                    Integer autoCrId = dfcrService.applyDefaultsOnEdit("Data Set", datasetId, datasetType, userId, isAdmin);
                    if (autoCrId != null) {
                        //system.out.println("[DatasetImpactServlet] DFCR auto-created CR: " + autoCrId);
                        activeCrId = autoCrId;
                    }
                } catch (Exception e) {
                    System.err.println("[DatasetImpactServlet] Error checking/creating DFCR CR: " + e.getMessage());
                }
            }
            
            int datasetIdToUse = datasetId;
            
            if (activeCrId != null) {
                // Object is under revision - use cloned dataset ID
                try {
                    // Get or create mapping for 'summary' area (this creates the cloned dataset if needed)
                    Integer nobjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // No summary mapping exists yet - need to clone the dataset row first
                        // This should have been done when summary was edited, but handle it here just in case
                        nobjectId = cloneDatasetRow(datasetId);
                        if (nobjectId != null) {
                            facetChangesDAO.saveMapping("dataset", datasetId, nobjectId, "summary", activeCrId);
                        }
                    }
                    
                    if (nobjectId != null) {
                        datasetIdToUse = nobjectId;
                        // Create mapping for impact#dataset_X_client area
                        facetChangesDAO.saveMapping("dataset", datasetId, nobjectId, "impact#dataset_X_client", activeCrId);
                        //system.out.println("[DatasetImpactServlet] Saving client relationships to cloned dataset ID " + nobjectId + " (original: " + datasetId + ")");
                    }
                } catch (Exception e) {
                    System.err.println("[DatasetImpactServlet] Error handling pending changes for client relationships: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    datasetIdToUse, "Dataset", relationships, "Client", "clientId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = datasetImpactService.saveClientRelationships(datasetIdToUse, relationships, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.addProperty("message", "Client relationships saved as pending. They will apply when the Change Request is completed.");
                    result.addProperty("pendingChanges", true);
                    result.addProperty("changeRequestId", activeCrId);
                } else {
                    result.addProperty("message", "Client relationships saved successfully");
                }
            } else {
                result.addProperty("message", "Failed to save client relationships");
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
            int datasetId = jsonData.get("datasetId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Data Sets", datasetId)) {
                return; // Response already sent
            }
            
            JsonElement relationshipsElement = jsonData.get("relationships");
            List<Map<String, Object>> relationships = new ArrayList<>();
            
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                for (JsonElement element : relationshipsElement.getAsJsonArray()) {
                    JsonObject rel = element.getAsJsonObject();
                    Map<String, Object> relationshipMap = new HashMap<>();
                    
                    if (rel.has("legalId") && !rel.get("legalId").isJsonNull()) {
                        relationshipMap.put("legalId", rel.get("legalId").getAsInt());
                    }
                    if (rel.has("relationType") && !rel.get("relationType").isJsonNull()) {
                        relationshipMap.put("relationType", rel.get("relationType").getAsInt());
                    }
                    if (rel.has("description")) {
                        relationshipMap.put("description", rel.get("description").isJsonNull() ? null : rel.get("description").getAsString());
                    }
                    
                    relationships.add(relationshipMap);
                }
            }
            
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User authentication required\"}");
                return;
            }
            
            // ⚠️ CRITICAL: Only check for automatic CRs (mandatory_workflow = true)
            // Manual CRs should NOT trigger pending changes logic
            // Check if dataset is under revision (has active auto-created CR)
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_ID, datasetId);
            
            // If no CR exists, check if DFCR edit workflow is enabled and auto-create CR
            if (activeCrId == null) {
                try {
                    boolean isAdmin = com.example.budg_v2.util.UserContextUtil.isCurrentUserAdmin(request);
                    Integer datasetType = getDatasetType(datasetId);
                    com.example.budg_v2.service.DFCRService dfcrService = new com.example.budg_v2.service.DFCRService();
                    Integer autoCrId = dfcrService.applyDefaultsOnEdit("Data Set", datasetId, datasetType, userId, isAdmin);
                    if (autoCrId != null) {
                        //system.out.println("[DatasetImpactServlet] DFCR auto-created CR: " + autoCrId);
                        activeCrId = autoCrId;
                    }
                } catch (Exception e) {
                    System.err.println("[DatasetImpactServlet] Error checking/creating DFCR CR: " + e.getMessage());
                }
            }
            
            int datasetIdToUse = datasetId;
            
            if (activeCrId != null) {
                // Object is under revision - use cloned dataset ID
                try {
                    // Get or create mapping for 'summary' area (this creates the cloned dataset if needed)
                    Integer nobjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // No summary mapping exists yet - need to clone the dataset row first
                        // This should have been done when summary was edited, but handle it here just in case
                        nobjectId = cloneDatasetRow(datasetId);
                        if (nobjectId != null) {
                            facetChangesDAO.saveMapping("dataset", datasetId, nobjectId, "summary", activeCrId);
                        }
                    }
                    
                    if (nobjectId != null) {
                        datasetIdToUse = nobjectId;
                        // Create mapping for impact#dataset_X_legal area
                        facetChangesDAO.saveMapping("dataset", datasetId, nobjectId, "impact#dataset_X_legal", activeCrId);
                        //system.out.println("[DatasetImpactServlet] Saving legal relationships to cloned dataset ID " + nobjectId + " (original: " + datasetId + ")");
                    }
                } catch (Exception e) {
                    System.err.println("[DatasetImpactServlet] Error handling pending changes for legal relationships: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    datasetIdToUse, "Dataset", relationships, "LegalEntity", "legalId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = datasetImpactService.saveLegalRelationships(datasetIdToUse, relationships, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.addProperty("message", "Legal relationships saved as pending. They will apply when the Change Request is completed.");
                    result.addProperty("pendingChanges", true);
                    result.addProperty("changeRequestId", activeCrId);
                } else {
                    result.addProperty("message", "Legal relationships saved successfully");
                }
            } else {
                result.addProperty("message", "Failed to save legal relationships");
            }
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save legal relationships: " + e.getMessage(), 500);
        }
    }
    
    // ===== REVERSE LOOKUP HANDLERS =====
    
    private void handleGetDatasetRelationshipsByProductId(int productId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = datasetImpactService.getDatasetRelationshipsByProductId(productId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Dataset", "datasetId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve dataset relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetDatasetRelationshipsByClientId(int clientId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = datasetImpactService.getDatasetRelationshipsByClientId(clientId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Dataset", "datasetId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve dataset relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetDatasetRelationshipsByLegalId(int legalId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = datasetImpactService.getDatasetRelationshipsByLegalId(legalId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Dataset", "datasetId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve dataset relationships: " + e.getMessage(), 500);
        }
    }
    
    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        response.getWriter().write(gson.toJson(error));
    }
    
    /**
     * Clone a dataset row for pending changes workflow.
     * Returns the ID of the newly created cloned row.
     */
    private Integer cloneDatasetRow(int originalId) throws SQLException {
        String sql = "INSERT INTO dataset (" +
                "RefNumber, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                "status, DatasetType, AccessControlType, lifecycle, " +
                "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID" +
                ") SELECT " +
                "RefNumber, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                "status, DatasetType, AccessControlType, lifecycle, " +
                "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID " +
                "FROM dataset WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Get dataset type ID for a given dataset
     */
    private Integer getDatasetType(int datasetId) {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            String sql = "SELECT DatasetType FROM dataset WHERE ID = ?";
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, datasetId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int typeId = rs.getInt("DatasetType");
                        return rs.wasNull() ? null : typeId;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting dataset type: " + e.getMessage());
        }
        return null;
    }
}

