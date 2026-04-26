package com.example.budg_v2;

import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.dao.ChangeRequestHistoryDAO;
import com.example.budg_v2.dao.CRStakeholderDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.model.ChangeRequest;
import com.example.budg_v2.model.ChangeRequestValue;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.WorkflowNotificationService;
import com.example.budg_v2.util.UserContextUtil;
import com.example.budg_v2.dao.WorkflowInstanceDAO;
import com.example.budg_v2.dao.WorkflowTaskDAO;
import com.example.budg_v2.model.WorkflowInstance;
import com.example.budg_v2.util.BpmnParser;
import com.example.budg_v2.util.BpmnFileManager;
import com.example.budg_v2.dao.ChangeRequestResolutionDAO;
import com.example.budg_v2.model.ChangeRequestResolution;
import com.example.budg_v2.service.DFCRService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.time.format.DateTimeFormatter;
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
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import com.example.budg_v2.database.DatabaseConnection;

/**
 * Servlet for Change Request operations
 * Endpoints:
 * GET /api/changerequests - List all change requests
 * GET /api/changerequests/{id} - Get change request by ID
 * POST /api/changerequests - Create new change request
 * PUT /api/changerequests/{id} - Update change request
 * DELETE /api/changerequests/{id} - Delete change request
 */
@WebServlet("/api/changerequests/*")
public class ChangeRequestServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ChangeRequestServlet.class);
    
    // Gson instance configured with LocalDateTime adapter
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    
    private final ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
    private final CRStakeholderDAO crStakeholderDAO = new CRStakeholderDAO();
    private final SegmentDAO segmentDAO = new SegmentDAO();
    private final WorkflowNotificationService notificationService = new WorkflowNotificationService();
    // LocalDateTime adapter for Gson
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String dateTimeString = in.nextString();
            return LocalDateTime.parse(dateTimeString, formatter);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            String reference = request.getParameter("reference");
            String stakeholderParam = request.getParameter("stakeholder");
            String contributingParam = request.getParameter("contributing");
            
            if (pathInfo == null || pathInfo.equals("/")) {
                List<ChangeRequest> changeRequests;
                
                if (stakeholderParam != null && !stakeholderParam.trim().isEmpty()) {
                    // Get change requests by stakeholder person ID
                    // V-01: WebUsers must also have segment access to the CR (not just stakeholder status)
                    try {
                        int personId = Integer.parseInt(stakeholderParam.trim());
                        int requestingUserId = UserContextUtil.getCurrentUserId(request);
                        String userRole = (String) request.getAttribute("userRole");
                        boolean isAdminOrSuperAdmin = userRole != null &&
                                (userRole.trim().toLowerCase().contains("admin"));

                        logger.info("Searching for change requests where person {} is a stakeholder", personId);
                        if (isAdminOrSuperAdmin) {
                            // Admin/SuperAdmin see all stakeholder CRs without segment restriction
                            changeRequests = changeRequestDAO.getChangeRequestsByStakeholder(personId);
                        } else {
                            // WebUser: must also have segment access to each CR (V-01 fix)
                            changeRequests = changeRequestDAO.getChangeRequestsByStakeholderWithSegmentCheck(personId, requestingUserId);
                        }
                        logger.info("Found {} change requests for stakeholder {}", changeRequests.size(), personId);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid stakeholder parameter: {}", stakeholderParam);
                        changeRequests = new ArrayList<>();
                    }
                } else if (contributingParam != null && !contributingParam.trim().isEmpty()) {
                    // CRs where this person has an active workflow task/step but did not raise the CR
                    try {
                        int personId = Integer.parseInt(contributingParam.trim());
                        int requestingUserId = UserContextUtil.getCurrentUserId(request);
                        String userRole = (String) request.getAttribute("userRole");
                        boolean isAdminOrSuperAdmin = userRole != null
                                && userRole.trim().toLowerCase().contains("admin");
                        if (!isAdminOrSuperAdmin && personId != requestingUserId) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            JsonObject error = new JsonObject();
                            error.addProperty("error", "Not allowed to view contributing change requests for another user");
                            response.getWriter().write(gson.toJson(error));
                            return;
                        }
                        changeRequests = loadContributingChangeRequests(personId);
                        logger.info("Found {} contributing change requests for person {}", changeRequests.size(), personId);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid contributing parameter: {}", contributingParam);
                        changeRequests = new ArrayList<>();
                    } catch (SQLException sqlEx) {
                        logger.error("Database error loading contributing change requests", sqlEx);
                        changeRequests = new ArrayList<>();
                    }
                } else if (reference != null && !reference.trim().isEmpty()) {
                    // Get change requests by reference (for parent dropdown)
                    String trimmedReference = reference.trim();
                    logger.info("Searching for change requests with reference: '{}'", trimmedReference);
                    changeRequests = changeRequestDAO.getChangeRequestsByReference(trimmedReference);
                    logger.info("Found {} change requests for reference: '{}'", changeRequests.size(), trimmedReference);
                    
                    if (changeRequests.isEmpty()) {
                        logger.debug("No change requests found. Checking if any change requests exist in database...");
                        List<ChangeRequest> allCRs = changeRequestDAO.getAllChangeRequests();
                        logger.debug("Total change requests in database: {}", allCRs.size());
                        if (!allCRs.isEmpty()) {
                            logger.debug("Sample reference from existing CR: '{}'", allCRs.get(0).getReference());
                        }
                    }
                } else {
                    // Get all change requests
                    changeRequests = changeRequestDAO.getAllChangeRequests();
                }
                
                // Load related data (typeName, statusName) for all change requests
                try (Connection conn = DatabaseConnection.getConnection()) {
                    for (ChangeRequest cr : changeRequests) {
                        changeRequestDAO.loadRelatedDataForList(cr, conn);
                    }
                } catch (SQLException e) {
                    logger.warn("Error loading related data for change requests: {}", e.getMessage());
                }
                
                // Check if each CR is blocked (has older incomplete CRs created by the same user)
                Integer completedStatusId = getStatusIdByName("Completed");
                Integer cancelledStatusId = getStatusIdByName("Cancelled");
                for (ChangeRequest cr : changeRequests) {
                    if (cr.getReference() != null && !cr.getReference().trim().isEmpty() && cr.getCreatedBy() != null) {
                        try {
                            List<Integer> olderIncompleteCRs = changeRequestDAO.getOlderIncompleteCRsBySameUser(
                                cr.getId(), cr.getReference(), cr.getCreatedBy(), completedStatusId, cancelledStatusId);
                            cr.setIsBlocked(!olderIncompleteCRs.isEmpty());
                        } catch (SQLException e) {
                            logger.warn("Error checking if CR {} is blocked: {}", cr.getId(), e.getMessage());
                            cr.setIsBlocked(false);
                        }
                    } else {
                        cr.setIsBlocked(false);
                    }
                }
                
                response.getWriter().write(gson.toJson(changeRequests));
            } else {
                // Check for history endpoint: /api/changerequests/{id}/history
                String[] pathParts = pathInfo.substring(1).split("/");
                if (pathParts.length >= 2 && "history".equals(pathParts[1])) {
                    try {
                        int crId = Integer.parseInt(pathParts[0]);
                        int page = 1;
                        int limit = 100;
                        String pageParam = request.getParameter("page");
                        String limitParam = request.getParameter("limit");
                        if (pageParam != null && !pageParam.trim().isEmpty()) {
                            page = Math.max(1, Integer.parseInt(pageParam.trim()));
                        }
                        if (limitParam != null && !limitParam.trim().isEmpty()) {
                            limit = Math.min(1000, Math.max(1, Integer.parseInt(limitParam.trim())));
                        }
                        ChangeRequestHistoryDAO historyDAO = new ChangeRequestHistoryDAO();
                        List<ChangeRequestHistoryDAO.ChangeRequestHistoryRecord> historyList =
                                historyDAO.getHistoryByChangeRequestId(crId, page, limit);
                        int totalCount = historyDAO.getHistoryCount(crId);
                        JsonObject result = new JsonObject();
                        result.addProperty("success", true);
                        result.addProperty("count", totalCount);
                        result.addProperty("page", page);
                        result.addProperty("limit", limit);
                        result.add("data", gson.toJsonTree(historyList));
                        response.getWriter().write(gson.toJson(result));
                        return;
                    } catch (NumberFormatException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Invalid change request ID");
                        response.getWriter().write(gson.toJson(error));
                        return;
                    } catch (SQLException e) {
                        logger.error("Error fetching change request history: {}", e.getMessage());
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Failed to load history");
                        response.getWriter().write(gson.toJson(error));
                        return;
                    }
                }

                // Get change request by ID
                if (pathParts.length > 0) {
                    try {
                        int id = Integer.parseInt(pathParts[0]);
                        logger.info("Fetching change request with ID: {}", id);
                        ChangeRequest changeRequest = changeRequestDAO.getChangeRequestById(id);
                        
                        if (changeRequest != null) {
                            logger.info("Found change request: {}", changeRequest.getPrimaryName());
                            
                            // ⚠️ AUTO-SYNC: Check if CR status is Completed/Cancelled and apply/discard changes if needed
                            // This handles the case when CR status is changed directly in the database
                            try {
                                Integer currentStatusId = changeRequest.getCrStatusId();
                                String statusName = changeRequest.getStatusName();
                                
                                if (currentStatusId != null) {
                                    Integer completedStatusId = changeRequestDAO.getStatusIdByName("Completed");
                                    Integer cancelledStatusId = changeRequestDAO.getStatusIdByName("Cancelled");
                                    if (cancelledStatusId == null) {
                                        cancelledStatusId = changeRequestDAO.getStatusIdByName("Canceled"); // Alternative spelling
                                    }
                                    Integer deletedStatusId = changeRequestDAO.getStatusIdByName("Deleted");
                                    
                                    // Check if there are pending changes
                                    FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
                                    Map<String, Map<Integer, Map<String, List<Integer>>>> pendingMappings = 
                                        facetChangesDAO.getAllMappingsByChangeRequest(id);
                                    boolean hasPendingChanges = !pendingMappings.isEmpty();
                                    
                                    if (hasPendingChanges) {
                                        // Check if status is Completed - apply changes
                                        if (completedStatusId != null && currentStatusId.equals(completedStatusId)) {
                                            logger.info("═══════════════════════════════════════════════════════════════");
                                            logger.info("🔄 [AUTO-SYNC] CR {} status is 'Completed' with pending changes - applying automatically", id);
                                            logger.info("═══════════════════════════════════════════════════════════════");
                                            try {
                                                com.example.budg_v2.service.FacetChangesService facetChangesService = 
                                                    new com.example.budg_v2.service.FacetChangesService();
                                                facetChangesService.applyChangesForCR(id);
                                                logger.info("✅ [AUTO-SYNC] Successfully applied pending changes for CR {}", id);
                                            } catch (Exception e) {
                                                logger.error("❌ [AUTO-SYNC] Error applying pending changes for CR {}: {}", id, e.getMessage(), e);
                                                // Continue - don't block the GET request
                                            }
                                        }
                                        // Check if status is Cancelled or Deleted - discard changes
                                        else if ((cancelledStatusId != null && currentStatusId.equals(cancelledStatusId)) ||
                                                 (deletedStatusId != null && currentStatusId.equals(deletedStatusId))) {
                                            logger.info("═══════════════════════════════════════════════════════════════");
                                            logger.info("🔄 [AUTO-SYNC] CR {} status is '{}' with pending changes - discarding automatically", id, statusName);
                                            logger.info("═══════════════════════════════════════════════════════════════");
                                            try {
                                                com.example.budg_v2.service.FacetChangesService facetChangesService = 
                                                    new com.example.budg_v2.service.FacetChangesService();
                                                facetChangesService.discardChangesForCR(id);
                                                logger.info("✅ [AUTO-SYNC] Successfully discarded pending changes for CR {}", id);
                                            } catch (Exception e) {
                                                logger.error("❌ [AUTO-SYNC] Error discarding pending changes for CR {}: {}", id, e.getMessage(), e);
                                                // Continue - don't block the GET request
                                            }
                                        }
                                    } else {
                                        logger.debug("ℹ️  [AUTO-SYNC] CR {} has no pending changes - no action needed", id);
                                    }
                                }
                            } catch (Exception e) {
                                logger.warn("⚠️  [AUTO-SYNC] Error checking/processing CR {} status sync: {}", id, e.getMessage());
                                // Continue - don't block the GET request
                            }
                            
                            // Load analysis and resolution data
                            try {
                                com.example.budg_v2.dao.ChangeRequestAnalysisDAO analysisDAO = 
                                    new com.example.budg_v2.dao.ChangeRequestAnalysisDAO();
                                com.example.budg_v2.dao.ChangeRequestResolutionDAO resolutionDAO = 
                                    new com.example.budg_v2.dao.ChangeRequestResolutionDAO();
                                
                                // Get latest analysis and resolution
                                java.util.List<com.example.budg_v2.model.ChangeRequestAnalysis> analyses = 
                                    analysisDAO.getAnalysisByChangeRequestId(id);
                                java.util.List<com.example.budg_v2.model.ChangeRequestResolution> resolutions = 
                                    resolutionDAO.getResolutionsByChangeRequestId(id);
                                
                                // Create JSON object from change request
                                JsonObject crJson = gson.toJsonTree(changeRequest).getAsJsonObject();
                                addReferenceObjectDisplayName(crJson, changeRequest);
                                
                                // Add analysis if exists
                                if (!analyses.isEmpty()) {
                                    crJson.addProperty("analysis", analyses.get(0).getAnalysis());
                                }
                                
                                // Add resolution if exists
                                if (!resolutions.isEmpty()) {
                                    crJson.addProperty("resolution", resolutions.get(0).getDescription());
                                    if (resolutions.get(0).getResolutionStatusId() != null) {
                                        crJson.addProperty("resolutionStatusId", resolutions.get(0).getResolutionStatusId());
                                    }
                                    if (resolutions.get(0).getStatusName() != null) {
                                        crJson.addProperty("resolutionStatusName", resolutions.get(0).getStatusName());
                                    }
                                }
                                
                                response.getWriter().write(gson.toJson(crJson));
                            } catch (SQLException e) {
                                logger.warn("Error loading analysis/resolution for CR {}: {}", id, e.getMessage());
                                // Continue without analysis/resolution
                                JsonObject crJsonFallback = gson.toJsonTree(changeRequest).getAsJsonObject();
                                addReferenceObjectDisplayName(crJsonFallback, changeRequest);
                                response.getWriter().write(gson.toJson(crJsonFallback));
                            }
                        } else {
                            logger.warn("Change request not found for ID: {}", id);
                            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                            JsonObject error = new JsonObject();
                            error.addProperty("error", "Change request not found");
                            response.getWriter().write(gson.toJson(error));
                        }
                    } catch (SQLException e) {
                        logger.error("Database error fetching change request {}: {}", pathParts[0], e.getMessage(), e);
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Database error: " + e.getMessage());
                        response.getWriter().write(gson.toJson(error));
                    } catch (NumberFormatException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Invalid change request ID");
                        response.getWriter().write(gson.toJson(error));
                    }
                }
            }

        } catch (SQLException e) {
            logger.error("Database error in GET /api/changerequests", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Get current user ID - IMPORTANT: This must be the actual user who is creating the manual CR
            int userId = UserContextUtil.getCurrentUserId(request);
            logger.info("[ChangeRequestServlet POST] UserContextUtil.getCurrentUserId returned: {}", userId);
            if (userId <= 0) {
                logger.warn("[ChangeRequestServlet POST] User not authenticated (userId: {}), rejecting CR creation", userId);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject error = new JsonObject();
                error.addProperty("error", "User not authenticated");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            logger.info("[ChangeRequestServlet POST] User authenticated, proceeding with CR creation for userId: {}", userId);

            // Check for cascade-delete endpoint
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && pathInfo.equals("/cascade-delete")) {
                handleCascadeDelete(request, response, userId);
                return;
            }
            
            // Check for cascade-restore endpoint
            if (pathInfo != null && pathInfo.equals("/cascade-restore")) {
                handleCascadeRestore(request, response, userId);
                return;
            }
            
            // Check for complete endpoint: /api/changerequests/{id}/complete
            if (pathInfo != null && pathInfo.matches("/\\d+/complete")) {
                logger.info("🔵 [ROUTING] Matched complete endpoint, pathInfo: {}", pathInfo);
                logger.info("🔵 [ROUTING] Calling handleCompleteChangeRequest for path: {}", pathInfo);
                try {
                    handleCompleteChangeRequest(request, response, userId);
                } catch (Exception e) {
                    logger.error("🔴 [ROUTING] Exception in handleCompleteChangeRequest: {}", e.getMessage(), e);
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Internal server error: " + e.getMessage());
                    response.getWriter().write(gson.toJson(error));
                }
                return;
            }
            
            // Check for cancel endpoint: /api/changerequests/{id}/cancel
            if (pathInfo != null && pathInfo.matches("/\\d+/cancel")) {
                handleCancelChangeRequest(request, response, userId);
                return;
            }

            // Read request body
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject requestData = JsonParser.parseString(sb.toString()).getAsJsonObject();

            // Validate required fields
            if (!requestData.has("title") || !requestData.has("summary") || 
                !requestData.has("type") || !requestData.has("severity") || 
                !requestData.has("urgency")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing required fields: title, summary, type, severity, urgency");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Create change request object
            ChangeRequest changeRequest = new ChangeRequest();
            changeRequest.setPrimaryName(requestData.get("title").getAsString());
            changeRequest.setSummary(requestData.get("summary").getAsString());
            changeRequest.setCrTypeId(requestData.get("type").getAsInt());
            changeRequest.setCrSeverityId(requestData.get("severity").getAsInt());
            changeRequest.setCrUrgencyId(requestData.get("urgency").getAsInt());
            // Set default status to "Pending Start" (dynamic lookup)
            Integer defaultStatusId = changeRequestDAO.getStatusIdByName("Pending Start");
            if (defaultStatusId != null) {
                changeRequest.setCrStatusId(defaultStatusId);
            }
            // IMPORTANT: Use the actual user ID from the request, not a default value
            // This ensures the manual CR is created by the user who actually created it
            logger.info("[ChangeRequestServlet POST] Creating manual CR with userId: {} (from UserContextUtil)", userId);
            changeRequest.setCreatedBy(userId);
            changeRequest.setLastUserChange(userId);
            changeRequest.setCreatedAt(LocalDateTime.now());
            changeRequest.setUpdatedAt(LocalDateTime.now());

            // Handle optional fields
            if (requestData.has("parent") && !requestData.get("parent").isJsonNull()) {
                changeRequest.setParentId(requestData.get("parent").getAsInt());
            }

            // Build reference string from facet information
            String facetType = null;
            Integer facetIdInt = null;
            if (requestData.has("facetType") && requestData.has("facetId") && requestData.has("facetName")) {
                facetType = requestData.get("facetType").getAsString();
                
                // Validate: Cannot raise CR for restricted facets
                String[] restrictedFacets = {"regulatory-theme", "geography", "regulator", "people", "legal-entity", "org-unit"};
                for (String restricted : restrictedFacets) {
                    if (restricted.equals(facetType)) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Change requests cannot be raised for " + capitalizeFirst(facetType.replace("-", " ")) + " facets");
                        response.getWriter().write(gson.toJson(error));
                        logger.warn("Attempted to create change request for restricted facet: {}", facetType);
                        return;
                    }
                }
                
                String facetId = requestData.get("facetId").getAsString();
                requestData.get("facetName").getAsString(); // consumed for request validation
                facetIdInt = Integer.parseInt(facetId);
                
                // Format: "FacetType FacetId" (e.g., "Glossary 5")
                String reference = capitalizeFirst(facetType.replace("-", " ")) + " " + facetId;
                changeRequest.setReference(reference);
            }

            // Handle estimated benefit
            Integer benefitId = null;
            if (requestData.has("estimatedBenefit") && !requestData.get("estimatedBenefit").isJsonNull()) {
                float benefitValue = requestData.get("estimatedBenefit").getAsFloat();
                String benefitCurrency = requestData.has("estimatedBenefitCurrency") ? 
                    requestData.get("estimatedBenefitCurrency").getAsString() : "GBP";
                
                Integer currencyId = changeRequestDAO.getCurrencyIdByCode(benefitCurrency);
                if (currencyId == null) {
                    // Try to get GBP as fallback
                    currencyId = changeRequestDAO.getCurrencyIdByCode("GBP");
                    if (currencyId == null) {
                        logger.warn("Currency not found for benefit: " + benefitCurrency + ", proceeding without currency ID");
                    }
                }
                
                ChangeRequestValue benefitValueObj = new ChangeRequestValue(benefitValue, currencyId, userId);
                // Set type to "Beneift" (note: database has typo in enum)
                benefitValueObj.setType("Beneift");
                benefitId = changeRequestDAO.createChangeRequestValue(benefitValueObj);
                changeRequest.setEstimatedBenefitId(benefitId);
            }

            // Handle estimated cost
            Integer costId = null;
            if (requestData.has("estimatedCost") && !requestData.get("estimatedCost").isJsonNull()) {
                float costValue = requestData.get("estimatedCost").getAsFloat();
                String costCurrency = requestData.has("estimatedCostCurrency") ? 
                    requestData.get("estimatedCostCurrency").getAsString() : "GBP";
                
                Integer currencyId = changeRequestDAO.getCurrencyIdByCode(costCurrency);
                if (currencyId == null) {
                    // Try to get GBP as fallback
                    currencyId = changeRequestDAO.getCurrencyIdByCode("GBP");
                    if (currencyId == null) {
                        logger.warn("Currency not found for cost: " + costCurrency + ", proceeding without currency ID");
                    }
                }
                
                ChangeRequestValue costValueObj = new ChangeRequestValue(costValue, currencyId, userId);
                // Set type to "Cost" to distinguish from benefit
                costValueObj.setType("Cost");
                costId = changeRequestDAO.createChangeRequestValue(costValueObj);
                changeRequest.setEstimatedCostId(costId);
            }

            // Create change request
            Integer changeRequestId = changeRequestDAO.createChangeRequest(changeRequest);

            // Assign change request to segment
            // IMPORTANT: If CR was raised upon an object, it should belong to the same segment as that object
            Integer segmentId = null;
            if (facetType != null && facetIdInt != null) {
                try {
                    // Convert facetType to objectType (e.g., "dataset" -> "Dataset", "system" -> "System")
                    String objectType = facetTypeToObjectType(facetType);
                    if (objectType != null) {
                        segmentId = segmentDAO.getObjectSegmentId(facetIdInt, objectType);
                        logger.info("🔍 Found segment {} for {} {} (objectType: {})", segmentId, facetType, facetIdInt, objectType);
                    } else {
                        logger.warn("⚠️ Could not map facetType '{}' to objectType, using default segment", facetType);
                    }
                } catch (Exception e) {
                    logger.warn("❌ Error getting segment from source object {} {}: {}", facetType, facetIdInt, e.getMessage());
                }
            }
            
            // Fallback: use segmentId from request or default to Enterprise (1)
            if (segmentId == null || segmentId <= 0) {
                segmentId = requestData.has("segmentId") && !requestData.get("segmentId").isJsonNull()
                        ? requestData.get("segmentId").getAsInt() : 1; // Default to Enterprise segment
            }
            
            try {
                segmentDAO.assignObjectToSegment(segmentId, changeRequestId, "ChangeRequest", userId);
                logger.info("✅ ChangeRequest " + changeRequestId + " assigned to segment " + segmentId + 
                           (facetType != null ? " (inherited from " + facetType + " " + facetIdInt + ")" : ""));
            } catch (Exception e) {
                logger.warn("❌ Error assigning change request to segment: " + e.getMessage());
            }

            // Copy stakeholders from source object to change request
            int stakeholdersCopied = 0;
            if (facetType != null && facetIdInt != null) {
                // Use already extracted facetType and facetIdInt from above
                try {
                    stakeholdersCopied = crStakeholderDAO.copyStakeholdersToChangeRequest(
                        facetType, facetIdInt, changeRequestId, userId);
                    logger.info("Copied {} stakeholders to change request {}", stakeholdersCopied, changeRequestId);
                } catch (SQLException e) {
                    // Log but don't fail the request - stakeholders are supplementary
                    logger.warn("Failed to copy stakeholders to change request {}: {}", changeRequestId, e.getMessage());
                }
            }

            // Send notification to the raiser and all stakeholders
            try {
                notificationService.sendCrRaisedNotification(changeRequestId, userId);
            } catch (Exception e) {
                logger.warn("Failed to send CR raised notification for CR {}: {}", changeRequestId, e.getMessage());
            }

            // Return success response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("id", changeRequestId);
            responseData.put("message", "Change request created successfully");
            responseData.put("stakeholdersCopied", stakeholdersCopied);
            
            if (benefitId != null) {
                responseData.put("benefitId", benefitId);
            }
            if (costId != null) {
                responseData.put("costId", costId);
            }

            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(gson.toJson(responseData));

        } catch (SQLException e) {
            logger.error("Database error in POST /api/changerequests", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error in POST /api/changerequests", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Get current user ID
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject error = new JsonObject();
                error.addProperty("error", "User authentication required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request ID is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");
            int changeRequestId;
            try {
                changeRequestId = Integer.parseInt(pathParts[0]);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid change request ID");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Get existing change request
            ChangeRequest existingCR = changeRequestDAO.getChangeRequestById(changeRequestId);
            if (existingCR == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Check if CR is cancelled or completed - prevent editing
            String statusName = existingCR.getStatusName();
            if (statusName == null) {
                statusName = changeRequestDAO.getStatusNameById(existingCR.getCrStatusId());
            }
            if (statusName != null) {
                String statusLower = statusName.toLowerCase();
                if (statusLower.contains("cancelled") || statusLower.contains("canceled")) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Cannot edit a cancelled change request");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
                // Check if CR is completed - prevent any edits
                if (statusLower.contains("completed")) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Cannot edit a completed change request");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }
            
            // Also check by status ID for "Completed" status
            Integer completedStatusId = getStatusIdByName("Completed");
            if (completedStatusId != null && existingCR.getCrStatusId() != null && 
                existingCR.getCrStatusId().equals(completedStatusId)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Cannot edit a completed change request");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Check edit permission: 
            // Allowed: super admin, admin, requester (creator),
            //          OR web user with edit permission on Change Requests facet + stakeholder on this CR
            if (userId > 0) {
                boolean canEdit = false;
                String reason = "";
                
                // 1. Check if user is super admin or admin
                boolean isSuperAdmin = false;
                boolean isAdmin = false;
                try {
                    isSuperAdmin = SegmentAccessService.isSuperAdmin(userId);
                    if (!isSuperAdmin) {
                        // Check if user is admin (not super admin)
                        isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                    }
                } catch (SQLException e) {
                    logger.warn("Error checking admin status: {}", e.getMessage());
                }
                
                if (isSuperAdmin || isAdmin) {
                    canEdit = true;
                    reason = isSuperAdmin ? "super admin" : "admin";
                } else {
                    // 2. Check if user is the requester (creator)
                    Integer createdBy = existingCR.getCreatedBy();
                    boolean isCreator = (createdBy != null && createdBy.equals(userId));
                    
                    if (isCreator) {
                        canEdit = true;
                        reason = "requester (creator)";
                    } else {
                        // 3. Check if user is a stakeholder with edit permission on Change Requests
                        String reference = existingCR.getReference();
                        if (reference != null && !reference.trim().isEmpty()) {
                            try {
                                CRStakeholderDAO stakeholderDAO = new CRStakeholderDAO();
                                List<Map<String, Object>> stakeholders = stakeholderDAO.getStakeholdersFromSourceObject(reference);
                                
                                boolean isStakeholder = false;
                                for (Map<String, Object> stakeholder : stakeholders) {
                                    Integer stakeholderUserId = null;
                                    if (stakeholder.containsKey("personId")) {
                                        Object userIdObj = stakeholder.get("personId");
                                        if (userIdObj instanceof Number) {
                                            stakeholderUserId = ((Number) userIdObj).intValue();
                                        }
                                    } else if (stakeholder.containsKey("userId")) {
                                        Object userIdObj = stakeholder.get("userId");
                                        if (userIdObj instanceof Number) {
                                            stakeholderUserId = ((Number) userIdObj).intValue();
                                        }
                                    }
                                    
                                    if (stakeholderUserId != null && stakeholderUserId.equals(userId)) {
                                        isStakeholder = true;
                                        break;
                                    }
                                }
                                
                                if (isStakeholder) {
                                    // Check edit permission on Change Requests module
                                    com.example.budg_v2.service.PermissionService permissionService = 
                                        new com.example.budg_v2.service.PermissionService();
                                    boolean hasEditPermission = permissionService.canEdit(userId, "Change Requests");
                                    
                                    if (hasEditPermission) {
                                        canEdit = true;
                                        reason = "stakeholder with edit permission on Change Requests";
                                    }
                                }
                            } catch (SQLException e) {
                                logger.warn("Error checking stakeholder status or permissions: {}", e.getMessage());
                            }
                        }
                    }
                }
                
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Access denied. Only super admin, admin, requester, or stakeholder with edit permission can edit this change request.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
                
                logger.info("User {} authorized to edit CR {} (reason: {})", userId, changeRequestId, reason);
            }

            // Store original values to detect changes
            ChangeRequest originalCR = new ChangeRequest();
            originalCR.setPrimaryName(existingCR.getPrimaryName());
            originalCR.setSummary(existingCR.getSummary());
            originalCR.setCrTypeId(existingCR.getCrTypeId());
            originalCR.setCrSeverityId(existingCR.getCrSeverityId());
            originalCR.setCrUrgencyId(existingCR.getCrUrgencyId());
            originalCR.setParentId(existingCR.getParentId());
            originalCR.setCrStatusId(existingCR.getCrStatusId());
            originalCR.setEstimatedBenefitId(existingCR.getEstimatedBenefitId());
            originalCR.setEstimatedCostId(existingCR.getEstimatedCostId());

            // Read request body
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject requestData = JsonParser.parseString(sb.toString()).getAsJsonObject();

            // Update fields - support both old and new field names
            if (requestData.has("primaryName")) {
                existingCR.setPrimaryName(requestData.get("primaryName").getAsString());
            } else if (requestData.has("title")) {
                existingCR.setPrimaryName(requestData.get("title").getAsString());
            }
            if (requestData.has("summary")) {
                existingCR.setSummary(requestData.get("summary").getAsString());
            }
            if (requestData.has("crTypeId")) {
                existingCR.setCrTypeId(requestData.get("crTypeId").getAsInt());
            } else if (requestData.has("type")) {
                existingCR.setCrTypeId(requestData.get("type").getAsInt());
            }
            if (requestData.has("crSeverityId")) {
                existingCR.setCrSeverityId(requestData.get("crSeverityId").getAsInt());
            } else if (requestData.has("severity")) {
                existingCR.setCrSeverityId(requestData.get("severity").getAsInt());
            }
            if (requestData.has("crUrgencyId")) {
                existingCR.setCrUrgencyId(requestData.get("crUrgencyId").getAsInt());
            } else if (requestData.has("urgency")) {
                existingCR.setCrUrgencyId(requestData.get("urgency").getAsInt());
            }
            if (requestData.has("parentId")) {
                if (requestData.get("parentId").isJsonNull()) {
                    existingCR.setParentId(null);
                } else {
                    existingCR.setParentId(requestData.get("parentId").getAsInt());
                }
            } else if (requestData.has("parent")) {
                if (requestData.get("parent").isJsonNull()) {
                    existingCR.setParentId(null);
                } else {
                    existingCR.setParentId(requestData.get("parent").getAsInt());
                }
            }

            // Handle estimated benefit
            if (requestData.has("estimatedBenefit") && !requestData.get("estimatedBenefit").isJsonNull()) {
                float benefitValue = requestData.get("estimatedBenefit").getAsFloat();
                String benefitCurrency = requestData.has("estimatedBenefitCurrency") ? 
                    requestData.get("estimatedBenefitCurrency").getAsString() : "GBP";
                
                Integer currencyId = changeRequestDAO.getCurrencyIdByCode(benefitCurrency);
                if (currencyId == null) {
                    // Try to get GBP as fallback
                    currencyId = changeRequestDAO.getCurrencyIdByCode("GBP");
                    if (currencyId == null) {
                        logger.warn("Currency not found for benefit: " + benefitCurrency + ", proceeding without currency ID");
                    }
                }
                
                ChangeRequestValue benefitValueObj = new ChangeRequestValue(benefitValue, currencyId, userId);
                // Set type to "Beneift" (note: database has typo in enum)
                benefitValueObj.setType("Beneift");
                
                // If there's an existing benefit value, update it; otherwise create new one
                if (existingCR.getEstimatedBenefitId() != null) {
                    // Update existing value
                    benefitValueObj.setId(existingCR.getEstimatedBenefitId());
                    changeRequestDAO.updateChangeRequestValue(benefitValueObj);
                    logger.info("Updated estimated benefit value {} for CR {}", existingCR.getEstimatedBenefitId(), changeRequestId);
                } else {
                    // Create new value
                    Integer benefitId = changeRequestDAO.createChangeRequestValue(benefitValueObj);
                    existingCR.setEstimatedBenefitId(benefitId);
                    logger.info("Created new estimated benefit value {} for CR {}", benefitId, changeRequestId);
                }
            } else if (requestData.has("estimatedBenefit") && requestData.get("estimatedBenefit").isJsonNull()) {
                // Clear estimated benefit if explicitly set to null
                if (existingCR.getEstimatedBenefitId() != null) {
                    changeRequestDAO.deleteChangeRequestValue(existingCR.getEstimatedBenefitId());
                    existingCR.setEstimatedBenefitId(null);
                    logger.info("Cleared estimated benefit for CR {}", changeRequestId);
                }
            }

            // Handle estimated cost
            if (requestData.has("estimatedCost") && !requestData.get("estimatedCost").isJsonNull()) {
                float costValue = requestData.get("estimatedCost").getAsFloat();
                String costCurrency = requestData.has("estimatedCostCurrency") ? 
                    requestData.get("estimatedCostCurrency").getAsString() : "GBP";
                
                Integer currencyId = changeRequestDAO.getCurrencyIdByCode(costCurrency);
                if (currencyId == null) {
                    // Try to get GBP as fallback
                    currencyId = changeRequestDAO.getCurrencyIdByCode("GBP");
                    if (currencyId == null) {
                        logger.warn("Currency not found for cost: " + costCurrency + ", proceeding without currency ID");
                    }
                }
                
                ChangeRequestValue costValueObj = new ChangeRequestValue(costValue, currencyId, userId);
                // Set type to "Cost" to distinguish from benefit
                costValueObj.setType("Cost");
                
                // If there's an existing cost value, update it; otherwise create new one
                if (existingCR.getEstimatedCostId() != null) {
                    // Update existing value
                    costValueObj.setId(existingCR.getEstimatedCostId());
                    changeRequestDAO.updateChangeRequestValue(costValueObj);
                    logger.info("Updated estimated cost value {} for CR {}", existingCR.getEstimatedCostId(), changeRequestId);
                } else {
                    // Create new value
                    Integer costId = changeRequestDAO.createChangeRequestValue(costValueObj);
                    existingCR.setEstimatedCostId(costId);
                    logger.info("Created new estimated cost value {} for CR {}", costId, changeRequestId);
                }
            } else if (requestData.has("estimatedCost") && requestData.get("estimatedCost").isJsonNull()) {
                // Clear estimated cost if explicitly set to null
                if (existingCR.getEstimatedCostId() != null) {
                    changeRequestDAO.deleteChangeRequestValue(existingCR.getEstimatedCostId());
                    existingCR.setEstimatedCostId(null);
                    logger.info("Cleared estimated cost for CR {}", changeRequestId);
                }
            }

            // Check for status change
            Integer oldStatusId = existingCR.getCrStatusId();
            Integer newStatusId = null;
            
            if (requestData.has("crStatusId")) {
                newStatusId = requestData.get("crStatusId").getAsInt();
                existingCR.setCrStatusId(newStatusId);
            } else if (requestData.has("status")) {
                newStatusId = requestData.get("status").getAsInt();
                existingCR.setCrStatusId(newStatusId);
            }
            
            // PROTECT auto-created CRs: prevent status changes from "Pending Start" unless going to Completed/Cancelled
            if (newStatusId != null && !newStatusId.equals(oldStatusId)) {
                // Check if this is an auto-created CR (has mandatory_workflow = true)
                if (existingCR.getMandatoryWorkflow() != null && existingCR.getMandatoryWorkflow()) {
                    // For auto-created CRs, only allow status changes to Completed/Cancelled
                    // Use the completedStatusId already defined above, or get it if not defined
                    if (completedStatusId == null) {
                        completedStatusId = getStatusIdByName("Completed");
                    }
                    Integer cancelledStatusId = getStatusIdByName("Cancelled");
                    if (cancelledStatusId == null) {
                        cancelledStatusId = getStatusIdByName("Canceled"); // Alternative spelling
                    }
                    
                    // Block any status change that's NOT to Completed/Cancelled
                    boolean isAllowedStatusChange = (completedStatusId != null && newStatusId.equals(completedStatusId)) ||
                                                  (cancelledStatusId != null && newStatusId.equals(cancelledStatusId));
                    
                    if (!isAllowedStatusChange) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Auto-created change requests can only be moved to 'Completed' or 'Cancelled' status.");
                        error.addProperty("currentStatusId", oldStatusId);
                        error.addProperty("newStatusId", newStatusId);
                        error.addProperty("allowedStatuses", "Completed, Cancelled");
                        logger.warn("Blocked status change for auto-created CR {}: {} -> {} (only Completed/Cancelled allowed)", 
                            changeRequestId, oldStatusId, newStatusId);
                        response.getWriter().write(gson.toJson(error));
                        return;
                    }
                }
                
                // Check for blocking logic: prevent status changes if older incomplete CRs exist
                String reference = existingCR.getReference();
                if (reference != null && !reference.trim().isEmpty()) {
                    // Use the completedStatusId already defined above, or get it if not defined
                    if (completedStatusId == null) {
                        completedStatusId = getStatusIdByName("Completed");
                    }
                    Integer cancelledStatusId = getStatusIdByName("Cancelled");
                    List<Integer> olderIncompleteCRs = changeRequestDAO.getOlderIncompleteCRs(
                        changeRequestId, reference, completedStatusId, cancelledStatusId);
                    
                    if (!olderIncompleteCRs.isEmpty()) {
                        // Block ANY status change (not just completion) if older incomplete CRs exist
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Cannot change status of this change request. There are older incomplete change requests that must be completed or cancelled first.");
                        error.addProperty("olderCRIds", gson.toJson(olderIncompleteCRs));
                        error.addProperty("message", "Complete or cancel the older change requests (IDs: " + olderIncompleteCRs + ") before modifying this one.");
                        response.getWriter().write(gson.toJson(error));
                        return;
                    }
                }
            }
            
            // ⚠️ NEW: Handle status change to "Deleted" - discard pending changes
            if (newStatusId != null && !newStatusId.equals(oldStatusId)) {
                Integer deletedStatusId = getStatusIdByName("Deleted");
                if (deletedStatusId != null && newStatusId.equals(deletedStatusId)) {
                    try {
                        com.example.budg_v2.service.FacetChangesService facetChangesService = 
                            new com.example.budg_v2.service.FacetChangesService();
                        facetChangesService.discardChangesForCR(changeRequestId);
                        logger.info("Discarded all pending changes for CR {} (status changed to Deleted)", changeRequestId);
                    } catch (Exception e) {
                        logger.error("Error discarding pending changes for CR {}: {}", changeRequestId, e.getMessage(), e);
                        // Continue with status update even if discarding changes fails
                    }
                }
                
                // ⚠️ NEW: Handle status change to "Cancelled" - discard pending changes
                // This is critical: when CR is cancelled, all pending changes must be discarded
                Integer cancelledStatusId = getStatusIdByName("Cancelled");
                if (cancelledStatusId == null) {
                    cancelledStatusId = getStatusIdByName("Canceled"); // Alternative spelling
                }
                if (cancelledStatusId != null && newStatusId.equals(cancelledStatusId)) {
                    try {
                        com.example.budg_v2.service.FacetChangesService facetChangesService = 
                            new com.example.budg_v2.service.FacetChangesService();
                        facetChangesService.discardChangesForCR(changeRequestId);
                        logger.info("Discarded all pending changes for CR {} (status changed to Cancelled)", changeRequestId);
                    } catch (Exception e) {
                        logger.error("Error discarding pending changes for CR {}: {}", changeRequestId, e.getMessage(), e);
                        // Continue with status update even if discarding changes fails
                    }
                    
                    // ⚠️ CRITICAL: Invalidate CREATE CR cache when CR is cancelled
                    // This ensures locks are refreshed immediately after CREATE CR cancellation
                    // so that status/lifecycle fields are unlocked if no other active CREATE CRs exist
                    if (existingCR.getReference() != null) {
                        com.example.budg_v2.service.DFCRService.invalidateCreateCRCacheByReference(existingCR.getReference());
                        logger.info("Invalidated CREATE CR cache for reference: {} (CR {} cancelled)", existingCR.getReference(), changeRequestId);
                    }
                }
                
                // ⚠️ NEW: Handle status change to "Completed" - apply pending changes
                // Use the completedStatusId already defined above, or get it if not defined
                if (completedStatusId == null) {
                    completedStatusId = getStatusIdByName("Completed");
                }
                if (completedStatusId != null && newStatusId.equals(completedStatusId)) {
                    try {
                        com.example.budg_v2.service.FacetChangesService facetChangesService = 
                            new com.example.budg_v2.service.FacetChangesService();
                        facetChangesService.applyChangesForCR(changeRequestId);
                        logger.info("Applied all pending changes for CR {} (status changed to Completed)", changeRequestId);
                    } catch (Exception e) {
                        logger.error("Error applying pending changes for CR {}: {}", changeRequestId, e.getMessage(), e);
                        // Continue with status update even if applying changes fails
                    }
                    
                    // ⚠️ CRITICAL: Invalidate CREATE CR cache when CR is completed
                    // This ensures locks are refreshed immediately after CREATE CR completion
                    // so that status/lifecycle fields are unlocked if no other active CREATE CRs exist
                    if (existingCR.getReference() != null) {
                        com.example.budg_v2.service.DFCRService.invalidateCreateCRCacheByReference(existingCR.getReference());
                        logger.info("Invalidated CREATE CR cache for reference: {} (CR {} completed)", existingCR.getReference(), changeRequestId);
                    }
                }
            }
            
            // Check if any changes were made by comparing with original values
            boolean hasChanges = (originalCR.getPrimaryName() == null ? existingCR.getPrimaryName() != null :
                                 !originalCR.getPrimaryName().equals(existingCR.getPrimaryName())) ||
                                (originalCR.getSummary() == null ? existingCR.getSummary() != null :
                                 !originalCR.getSummary().equals(existingCR.getSummary())) ||
                                (originalCR.getCrTypeId() == null ? existingCR.getCrTypeId() != null :
                                 !originalCR.getCrTypeId().equals(existingCR.getCrTypeId())) ||
                                (originalCR.getCrSeverityId() == null ? existingCR.getCrSeverityId() != null :
                                 !originalCR.getCrSeverityId().equals(existingCR.getCrSeverityId())) ||
                                (originalCR.getCrUrgencyId() == null ? existingCR.getCrUrgencyId() != null :
                                 !originalCR.getCrUrgencyId().equals(existingCR.getCrUrgencyId())) ||
                                (originalCR.getParentId() == null ? existingCR.getParentId() != null : 
                                 !originalCR.getParentId().equals(existingCR.getParentId())) ||
                                (originalCR.getCrStatusId() == null ? existingCR.getCrStatusId() != null :
                                 !originalCR.getCrStatusId().equals(existingCR.getCrStatusId())) ||
                                (originalCR.getEstimatedBenefitId() == null ? existingCR.getEstimatedBenefitId() != null :
                                 !originalCR.getEstimatedBenefitId().equals(existingCR.getEstimatedBenefitId())) ||
                                (originalCR.getEstimatedCostId() == null ? existingCR.getEstimatedCostId() != null :
                                 !originalCR.getEstimatedCostId().equals(existingCR.getEstimatedCostId()));
            
            if (hasChanges) {
                // Real changes detected - use current user and current time
                existingCR.setLastUserChange(userId);
                existingCR.setUpdatedAt(LocalDateTime.now());
            } else {
                // No changes - set Last Updated By = Created By and Last Updated Date = Created Date
                if (existingCR.getCreatedBy() != null) {
                    existingCR.setLastUserChange(existingCR.getCreatedBy());
                }
                if (existingCR.getCreatedAt() != null) {
                    existingCR.setUpdatedAt(existingCR.getCreatedAt());
                }
            }

            // Handle analysis and resolution updates
            try {
                com.example.budg_v2.dao.ChangeRequestAnalysisDAO analysisDAO = new com.example.budg_v2.dao.ChangeRequestAnalysisDAO();
                com.example.budg_v2.dao.ChangeRequestResolutionDAO resolutionDAO = new com.example.budg_v2.dao.ChangeRequestResolutionDAO();
                
                // Handle analysis
                if (requestData.has("analysis")) {
                    String analysisText = requestData.get("analysis").getAsString();
                    if (analysisText != null && !analysisText.trim().isEmpty()) {
                        // Check if analysis already exists
                        boolean hasAnalysis = analysisDAO.hasAnalysisForChangeRequest(changeRequestId);
                        if (hasAnalysis) {
                            // Update existing analysis (get the latest one)
                            java.util.List<com.example.budg_v2.model.ChangeRequestAnalysis> existingAnalyses = 
                                analysisDAO.getAnalysisByChangeRequestId(changeRequestId);
                            if (!existingAnalyses.isEmpty()) {
                                com.example.budg_v2.model.ChangeRequestAnalysis analysis = existingAnalyses.get(0);
                                analysis.setAnalysis(analysisText);
                                analysis.setLastUserChange(userId);
                                analysisDAO.updateAnalysis(analysis);
                                logger.info("Updated analysis for CR {}", changeRequestId);
                            }
                        } else {
                            // Create new analysis
                            com.example.budg_v2.model.ChangeRequestAnalysis analysis = new com.example.budg_v2.model.ChangeRequestAnalysis();
                            analysis.setChangeRequestId(changeRequestId);
                            analysis.setAnalysis(analysisText);
                            analysis.setLastUserChange(userId);
                            java.time.LocalDateTime now = java.time.LocalDateTime.now();
                            analysis.setCreatedAt(now);
                            analysis.setUpdatedAt(now);
                            analysisDAO.createAnalysis(analysis);
                            logger.info("Created analysis for CR {}", changeRequestId);
                        }
                    }
                }
                
                // Handle resolution
                if (requestData.has("resolution") || requestData.has("resolutionStatusId")) {
                    String resolutionText = requestData.has("resolution") ? 
                        requestData.get("resolution").getAsString() : null;
                    Integer resolutionStatusId = requestData.has("resolutionStatusId") && 
                        !requestData.get("resolutionStatusId").isJsonNull() ?
                        requestData.get("resolutionStatusId").getAsInt() : null;
                    
                    // Only process if we have resolution text or status
                    if ((resolutionText != null && !resolutionText.trim().isEmpty()) || resolutionStatusId != null) {
                        // Check if resolution already exists
                        boolean hasResolution = resolutionDAO.hasResolutionForChangeRequest(changeRequestId);
                        if (hasResolution) {
                            // Update existing resolution (get the latest one)
                            java.util.List<com.example.budg_v2.model.ChangeRequestResolution> existingResolutions = 
                                resolutionDAO.getResolutionsByChangeRequestId(changeRequestId);
                            if (!existingResolutions.isEmpty()) {
                                com.example.budg_v2.model.ChangeRequestResolution resolution = existingResolutions.get(0);
                                if (resolutionText != null && !resolutionText.trim().isEmpty()) {
                                    resolution.setDescription(resolutionText);
                                }
                                if (resolutionStatusId != null) {
                                    resolution.setResolutionStatusId(resolutionStatusId);
                                }
                                resolution.setLastUserChange(userId);
                                resolutionDAO.updateResolution(resolution);
                                logger.info("Updated resolution for CR {}", changeRequestId);
                            }
                        } else {
                            // Create new resolution (description is required)
                            if (resolutionText != null && !resolutionText.trim().isEmpty()) {
                                com.example.budg_v2.model.ChangeRequestResolution resolution = 
                                    new com.example.budg_v2.model.ChangeRequestResolution();
                                resolution.setChangeRequestId(changeRequestId);
                                resolution.setDescription(resolutionText);
                                resolution.setResolutionStatusId(resolutionStatusId);
                                resolution.setLastUserChange(userId);
                                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                                resolution.setCreatedAt(now);
                                resolution.setUpdatedAt(now);
                                resolutionDAO.createResolution(resolution);
                                logger.info("Created resolution for CR {}", changeRequestId);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Error updating analysis/resolution for CR {}: {}", changeRequestId, e.getMessage(), e);
                // Don't fail the entire update if analysis/resolution update fails
            }

            // Update change request
            boolean updated = changeRequestDAO.updateChangeRequest(existingCR);

            if (updated) {
                // Update segment assignment if provided
                if (requestData.has("segmentId") && !requestData.get("segmentId").isJsonNull()) {
                    Integer segmentId = requestData.get("segmentId").getAsInt();
                    try {
                        int currentSegmentId = segmentDAO.getObjectSegmentId(changeRequestId, "ChangeRequest");
                        if (currentSegmentId != segmentId) {
                            if (currentSegmentId > 0) {
                                segmentDAO.removeObjectFromSegment(currentSegmentId, changeRequestId, "ChangeRequest", userId);
                            }
                            segmentDAO.assignObjectToSegment(segmentId, changeRequestId, "ChangeRequest", userId);
                            logger.info("✅ ChangeRequest " + changeRequestId + " segment changed from " + currentSegmentId + " to " + segmentId);
                        }
                    } catch (Exception e) {
                        logger.warn("❌ Error updating change request segment: " + e.getMessage());
                    }
                }
                
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("success", true);
                responseData.put("message", "Change request updated successfully");
                response.getWriter().write(gson.toJson(responseData));
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Failed to update change request");
                response.getWriter().write(gson.toJson(error));
            }

        } catch (SQLException e) {
            logger.error("Database error in PUT /api/changerequests", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error in PUT /api/changerequests", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Get current user ID
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject error = new JsonObject();
                error.addProperty("error", "User not authenticated");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request ID is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");
            int changeRequestId;
            try {
                changeRequestId = Integer.parseInt(pathParts[0]);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid change request ID");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Discard pending changes before deleting (if any exist)
            try {
                com.example.budg_v2.service.FacetChangesService facetChangesService = 
                    new com.example.budg_v2.service.FacetChangesService();
                facetChangesService.discardChangesForCR(changeRequestId);
                logger.info("Discarded all pending changes for CR {} (before soft delete)", changeRequestId);
            } catch (Exception e) {
                logger.error("Error discarding pending changes for CR {}: {}", changeRequestId, e.getMessage(), e);
                // Continue with delete even if discarding changes fails
            }

            // Soft delete change request
            boolean deleted = changeRequestDAO.deleteChangeRequest(changeRequestId, userId);

            if (deleted) {
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("success", true);
                responseData.put("message", "Change request deleted successfully");
                response.getWriter().write(gson.toJson(responseData));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request not found or already deleted");
                response.getWriter().write(gson.toJson(error));
            }

        } catch (SQLException e) {
            logger.error("Database error in DELETE /api/changerequests", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error in DELETE /api/changerequests", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Change requests where the person has an active workflow task (assignee or matching CR role)
     * but is not the user who raised the change request.
     */
    private List<ChangeRequest> loadContributingChangeRequests(int personId) throws SQLException {
        WorkflowTaskDAO taskDAO = new WorkflowTaskDAO();
        List<Map<String, Object>> tasks = taskDAO.findActiveTasksForUser(personId);
        LinkedHashSet<Integer> orderedCrIds = new LinkedHashSet<>();
        for (Map<String, Object> t : tasks) {
            Object crIdObj = t.get("changeRequestId");
            if (!(crIdObj instanceof Integer)) {
                continue;
            }
            int crId = (Integer) crIdObj;
            if (crId <= 0) {
                continue;
            }
            Object createdByObj = t.get("crCreatedBy");
            if (createdByObj instanceof Integer) {
                int createdBy = (Integer) createdByObj;
                if (createdBy == personId) {
                    continue;
                }
            }
            orderedCrIds.add(crId);
        }
        List<ChangeRequest> result = new ArrayList<>();
        for (Integer crId : orderedCrIds) {
            ChangeRequest cr = changeRequestDAO.getChangeRequestById(crId);
            if (cr != null && cr.getDeletedAt() == null) {
                result.add(cr);
            }
        }
        return result;
    }

    /**
     * Capitalize first letter of each word
     */
    /**
     * Convert facetType (e.g., "dataset", "system") to objectType (e.g., "Dataset", "System")
     * for segment assignment lookup
     */
    private String facetTypeToObjectType(String facetType) {
        if (facetType == null || facetType.trim().isEmpty()) {
            return null;
        }
        
        String normalized = facetType.toLowerCase().trim().replace("-", "").replace("_", "");
        
        return switch (normalized) {
            case "dataset", "datasets" -> "Dataset";
            case "system", "systems" -> "System";
            case "glossary", "glossaries" -> "Glossary";
            case "process", "processes" -> "Process";
            case "project", "projects" -> "Project";
            case "product", "products" -> "Product";
            case "policy", "policies" -> "Policy";
            case "attribute", "attributes" -> "Dataset"; // Attributes inherit dataset segment
            case "interface", "interfaces", "systeminterface" -> "SystemInterface";
            case "capability", "capabilities" -> "Capability";
            case "client", "clients" -> "Client";
            case "committee", "committees" -> "Committee";
            case "legalentity", "legal" -> "LegalEntity";
            case "businessarea" -> "BusinessArea";
            case "regulation", "regulations" -> "Regulation";
            case "regulator", "regulators" -> "Regulator";
            case "regulatorytheme" -> "RegulatoryTheme";
            default -> null;
        };
    }
    
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        String[] words = str.split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            if (words[i].length() > 0) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1).toLowerCase());
                }
            }
        }
        
        return result.toString();
    }

    /**
     * Handle cascade soft delete for all change requests of an object
     * POST /api/changerequests/cascade-delete
     * Body: { "facetType": "System", "facetId": 63 }
     * OR Body: { "reference": "System 63" }
     */
    private void handleCascadeDelete(HttpServletRequest request, HttpServletResponse response, int userId)
            throws IOException {
        try {
            // Read request body
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject requestData = JsonParser.parseString(sb.toString()).getAsJsonObject();
            int deletedCount = 0;

            if (requestData.has("reference") && !requestData.get("reference").isJsonNull()) {
                // Delete by reference string
                String reference = requestData.get("reference").getAsString();
                logger.info("Cascade soft deleting change requests by reference: {}", reference);
                deletedCount = changeRequestDAO.cascadeSoftDeleteByReference(reference, userId);
            } else if (requestData.has("facetType") && requestData.has("facetId")) {
                // Delete by facet type and ID
                String facetType = requestData.get("facetType").getAsString();
                int facetId = requestData.get("facetId").getAsInt();
                logger.info("Cascade soft deleting change requests for {} {}", facetType, facetId);
                deletedCount = changeRequestDAO.cascadeSoftDeleteByFacet(facetType, facetId, userId);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing required fields: either 'reference' or both 'facetType' and 'facetId'");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("deletedCount", deletedCount);
            responseData.put("message", "Cascade soft deleted " + deletedCount + " change request(s)");
            response.getWriter().write(gson.toJson(responseData));

        } catch (SQLException e) {
            logger.error("Database error in cascade delete", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error in cascade delete", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Handle cascade restore for all change requests of an object
     * POST /api/changerequests/cascade-restore
     * Body: { "facetType": "System", "facetId": 63 }
     * OR Body: { "reference": "System 63" }
     */
    private void handleCascadeRestore(HttpServletRequest request, HttpServletResponse response, int userId)
            throws IOException {
        try {
            // Read request body
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject requestData = JsonParser.parseString(sb.toString()).getAsJsonObject();
            int restoredCount = 0;

            if (requestData.has("reference") && !requestData.get("reference").isJsonNull()) {
                // Restore by reference string
                String reference = requestData.get("reference").getAsString();
                logger.info("Cascade restoring change requests by reference: {}", reference);
                restoredCount = changeRequestDAO.cascadeRestoreByReference(reference, userId);
            } else if (requestData.has("facetType") && requestData.has("facetId")) {
                // Restore by facet type and ID
                String facetType = requestData.get("facetType").getAsString();
                int facetId = requestData.get("facetId").getAsInt();
                logger.info("Cascade restoring change requests for {} {}", facetType, facetId);
                restoredCount = changeRequestDAO.cascadeRestoreByFacet(facetType, facetId, userId);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing required fields: either 'reference' or both 'facetType' and 'facetId'");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("restoredCount", restoredCount);
            responseData.put("message", "Cascade restored " + restoredCount + " change request(s)");
            response.getWriter().write(gson.toJson(responseData));

        } catch (SQLException e) {
            logger.error("Database error in cascade restore", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error in cascade restore", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Handle Complete Change Request endpoint
     * POST /api/changerequests/{id}/complete
     */
    private void handleCompleteChangeRequest(HttpServletRequest request, HttpServletResponse response, int userId)
            throws IOException {
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("🎯 [COMPLETE BUTTON CLICKED] Starting handleCompleteChangeRequest");
        logger.info("═══════════════════════════════════════════════════════════════");
        try {
            // Extract change request ID from path
            String pathInfo = request.getPathInfo();
            logger.info("📋 PathInfo: {}", pathInfo);
            String[] pathParts = pathInfo.substring(1).split("/");
            int changeRequestId = Integer.parseInt(pathParts[0]);

            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("🎯 [COMPLETE CR] User {} clicked Complete button for CR {}", userId, changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");

            // Get change request
            ChangeRequest cr = changeRequestDAO.getChangeRequestById(changeRequestId);
            if (cr == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // STEP 1: Check authorization: super admin, admin, or stakeholder
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 1] Checking authorization for user {} to complete CR {}", userId, changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            boolean isAuthorized = checkUserCanCompleteCR(request, userId, changeRequestId);
            if (!isAuthorized) {
                logger.warn("❌ [STEP 1] User {} is not authorized to complete CR {}", userId, changeRequestId);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                JsonObject error = new JsonObject();
                error.addProperty("error", "User not authorized to complete this change request");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            logger.info("✅ [STEP 1] User {} is authorized to complete CR {}", userId, changeRequestId);

            // STEP 2: Check for pending changes first - if there are pending changes, we should apply them
            // even if the CR is already completed (in case it was completed before changes were applied)
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 2] Checking for pending changes for CR {}", changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            boolean hasPendingChanges = false;
            try {
                FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
                Map<String, Map<Integer, Map<String, List<Integer>>>> pendingMappings = 
                    facetChangesDAO.getAllMappingsByChangeRequest(changeRequestId);
                hasPendingChanges = !pendingMappings.isEmpty();
                logger.info("✅ [STEP 2] Pending changes check: CR {} has {} pending change(s)", 
                    changeRequestId, hasPendingChanges ? pendingMappings.size() : 0);
            } catch (Exception e) {
                logger.warn("⚠️  [STEP 2] Could not check for pending changes: {}", e.getMessage());
            }
            
            // Verify CR status is Running or Paused
            // Exception: If CR is already completed but has pending changes, allow applying them
            String statusName = cr.getStatusName();
            if (statusName == null) {
                // Try to get status name from status ID
                statusName = changeRequestDAO.getStatusNameById(cr.getCrStatusId());
            }
            
            boolean isRunning = statusName != null && statusName.toLowerCase().contains("running");
            boolean isPaused = statusName != null && statusName.toLowerCase().contains("paused");
            boolean isCompleted = statusName != null && statusName.toLowerCase().contains("completed");
            
            if (isCompleted && !hasPendingChanges) {
                // Already completed with no pending changes - nothing to do
                logger.warn("⚠️  CR {} is already completed with no pending changes", changeRequestId);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request is already completed and has no pending changes");
                response.getWriter().write(gson.toJson(error));
                return;
            } else if (!isRunning && !isPaused && !(isCompleted && hasPendingChanges)) {
                // Not in a valid state to complete (unless it's completed with pending changes)
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request must be in Running or Paused status to complete");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            if (isCompleted && hasPendingChanges) {
                logger.info("⚠️  CR {} is already completed but has pending changes. Applying changes...", changeRequestId);
            }

            // Get "Completed" status ID
            Integer completedStatusId = changeRequestDAO.getStatusIdByName("Completed");
            if (completedStatusId == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Completed status not found in database");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // STEP 3: Load workflow instance and BPMN to extract End event properties
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 3] Checking workflow end events for CR {}", changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            WorkflowInstanceDAO instanceDAO = new WorkflowInstanceDAO();
            WorkflowInstance instance = instanceDAO.findByChangeRequestId(changeRequestId);
            
            Integer statusId = null;
            Integer lifecycleId = null;
            
            if (instance != null) {
                try {
                    logger.info("🔄 [STEP 3.1] Loading BPMN for workflow instance {}", instance.getId());
                    // Load BPMN XML
                    String bpmnXml = BpmnFileManager.loadBpmnFile(instance.getProcessDefinitionId());
                    if (bpmnXml != null) {
                        Document doc = BpmnParser.parseXml(bpmnXml);
                        
                        // Find End event(s) - we need to find which one was reached
                        // For now, find the first End event (in a real scenario, we'd track which End was reached)
                        org.w3c.dom.NodeList endEvents = doc.getElementsByTagNameNS("*", "endEvent");
                        if (endEvents.getLength() > 0) {
                            Element endEvent = (Element) endEvents.item(0);
                            Map<String, String> endProperties = BpmnParser.extractEndEventProperties(endEvent);
                            
                            if (endProperties.containsKey("status") && !endProperties.get("status").isEmpty()) {
                                try {
                                    statusId = Integer.parseInt(endProperties.get("status"));
                                    logger.info("✅ [STEP 3.2] Found status ID {} in End event", statusId);
                                } catch (NumberFormatException e) {
                                    logger.warn("⚠️  [STEP 3] Invalid status ID in End event: {}", endProperties.get("status"));
                                }
                            }
                            
                            if (endProperties.containsKey("lifecycle") && !endProperties.get("lifecycle").isEmpty()) {
                                try {
                                    lifecycleId = Integer.parseInt(endProperties.get("lifecycle"));
                                    logger.info("✅ [STEP 3.3] Found lifecycle ID {} in End event", lifecycleId);
                                } catch (NumberFormatException e) {
                                    logger.warn("⚠️  [STEP 3] Invalid lifecycle ID in End event: {}", endProperties.get("lifecycle"));
                                }
                            }
                        } else {
                            logger.info("ℹ️  [STEP 3] No End events found in BPMN");
                        }
                    }
                } catch (Exception e) {
                    logger.warn("⚠️  [STEP 3] Error extracting End event properties: {}", e.getMessage());
                    // Continue without updating object - CR completion should still proceed
                }
            } else {
                logger.info("ℹ️  [STEP 3] No workflow instance found for CR {}", changeRequestId);
            }

            // STEP 4: Update referenced object if Status/Lifecycle exist
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 4] Updating referenced object for CR {}", changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            if ((statusId != null || lifecycleId != null) && cr.getReference() != null) {
                try {
                    // Parse reference: "FacetName ObjectId" (e.g., "Dataset 53")
                    String[] refParts = cr.getReference().split("\\s+");
                    if (refParts.length >= 2) {
                        String facetName = refParts[0];
                        int objectId = Integer.parseInt(refParts[refParts.length - 1]);
                        
                        logger.info("🔄 [STEP 4.1] Updating object {} {} with status={}, lifecycle={}", facetName, objectId, statusId, lifecycleId);
                        DFCRService dfcrService = new DFCRService();
                        dfcrService.updateObjectStatusAndLifecycle(facetName, objectId, statusId, lifecycleId);
                        logger.info("✅ [STEP 4.2] Successfully updated object {} {} with status={}, lifecycle={}", facetName, objectId, statusId, lifecycleId);
                    }
                } catch (Exception e) {
                    logger.warn("⚠️  [STEP 4] Error updating referenced object: {}", e.getMessage());
                    // Continue - CR completion should still proceed
                }
            } else {
                logger.info("ℹ️  [STEP 4] No status/lifecycle to update or no reference found for CR {}", changeRequestId);
            }

            // STEP 5: Apply pending changes from facet_changes (nobject_id -> object_id)
            // This must happen BEFORE status update to ensure data consistency
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 5] Applying pending changes for CR {}", changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            if (hasPendingChanges) {
                try {
                    logger.info("🔄 [STEP 5.1] Calling FacetChangesService.applyChangesForCR({})...", changeRequestId);
                    com.example.budg_v2.service.FacetChangesService facetChangesService = 
                        new com.example.budg_v2.service.FacetChangesService();
                    facetChangesService.applyChangesForCR(changeRequestId);
                    logger.info("✅ [STEP 5.2] FacetChangesService completed successfully for CR {}", changeRequestId);
                } catch (Exception e) {
                    logger.error("═══════════════════════════════════════════════════════════════");
                    logger.error("❌ [STEP 5 ERROR] Error applying pending changes for CR {}: {}", changeRequestId, e.getMessage(), e);
                    logger.error("⚠️  [STEP 5] Continuing with CR completion despite errors (status will still be updated)...");
                    logger.error("═══════════════════════════════════════════════════════════════");
                    // Continue with completion even if applying changes fails
                }
            } else {
                logger.info("ℹ️  [STEP 5] No pending changes to apply for CR {} - skipping applyChangesForCR", changeRequestId);
            }

            // STEP 6: Update CR status to Completed
            // This is the final step and must execute even if previous steps had errors
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 6] Updating CR {} status to Completed", changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            try {
                cr.setCrStatusId(completedStatusId);
                cr.setLastUserChange(userId);
                cr.setUpdatedAt(LocalDateTime.now());
                boolean statusUpdated = changeRequestDAO.updateChangeRequest(cr);
                if (statusUpdated) {
                    logger.info("✅ [STEP 6] Successfully updated CR {} status to Completed (status ID: {})", changeRequestId, completedStatusId);
                } else {
                    logger.error("❌ [STEP 6 ERROR] Failed to update CR {} status. Update returned false.", changeRequestId);
                    throw new SQLException("Failed to update change request status to Completed");
                }
            } catch (Exception e) {
                logger.error("═══════════════════════════════════════════════════════════════");
                logger.error("❌ [STEP 6 CRITICAL ERROR] Failed to update CR {} status to Completed: {}", changeRequestId, e.getMessage(), e);
                logger.error("═══════════════════════════════════════════════════════════════");
                throw e; // Re-throw to be caught by outer catch block
            }

            // Return success response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Change request completed successfully");
            response.getWriter().write(gson.toJson(responseData));

        } catch (SQLException e) {
            logger.error("═══════════════════════════════════════════════════════════════");
            logger.error("❌ [SQL ERROR] Database error completing change request", e);
            logger.error("SQL State: {}, Error Code: {}", e.getSQLState(), e.getErrorCode());
            logger.error("═══════════════════════════════════════════════════════════════");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (NumberFormatException e) {
            logger.error("═══════════════════════════════════════════════════════════════");
            logger.error("❌ [ERROR] Invalid change request ID format", e);
            logger.error("═══════════════════════════════════════════════════════════════");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid change request ID: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("═══════════════════════════════════════════════════════════════");
            logger.error("❌ [ERROR] Error completing change request", e);
            logger.error("Exception type: {}, Message: {}", e.getClass().getName(), e.getMessage());
            logger.error("═══════════════════════════════════════════════════════════════");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Handle Cancel Change Request endpoint
     * POST /api/changerequests/{id}/cancel
     */
    private void handleCancelChangeRequest(HttpServletRequest request, HttpServletResponse response, int userId)
            throws IOException {
        try {
            // Extract change request ID from path
            String pathInfo = request.getPathInfo();
            String[] pathParts = pathInfo.substring(1).split("/");
            int changeRequestId = Integer.parseInt(pathParts[0]);

            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("🎯 [CANCEL BUTTON CLICKED] Starting handleCancelChangeRequest");
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("Cancelling change request {} by user {}", changeRequestId, userId);

            // Get change request
            ChangeRequest cr = changeRequestDAO.getChangeRequestById(changeRequestId);
            if (cr == null) {
                logger.error("❌ Change request {} not found", changeRequestId);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // STEP 1: Check if CR is already cancelled - if so, don't allow cancelling again
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 1] Checking if CR {} is already cancelled", changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            String statusName = cr.getStatusName();
            if (statusName == null) {
                statusName = changeRequestDAO.getStatusNameById(cr.getCrStatusId());
            }
            if (statusName != null) {
                String statusLower = statusName.toLowerCase();
                if (statusLower.contains("cancelled") || statusLower.contains("canceled")) {
                    logger.warn("❌ [STEP 1] CR {} is already cancelled (status: {})", changeRequestId, statusName);
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Change request is already cancelled");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }
            
            // STEP 2: Check authorization - only stakeholders and creator can cancel
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 2] Checking authorization for user {} to cancel CR {}", userId, changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            boolean canCancel = canCancelChangeRequest(userId, changeRequestId, cr);
            if (!canCancel) {
                logger.warn("❌ [STEP 2] User {} is not authorized to cancel CR {}", userId, changeRequestId);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                JsonObject error = new JsonObject();
                error.addProperty("error", "You are not authorized to cancel this change request. Only stakeholders and the creator can cancel.");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            logger.info("✅ [STEP 2] User {} is authorized to cancel CR {}", userId, changeRequestId);
            
            // Allow cancellation from any status (except already cancelled)
            logger.info("✅ [STEP 1] CR {} is not cancelled. Current status: {}. Proceeding with cancellation...", changeRequestId, statusName);

            // Get current user info
            String userName = (String) request.getAttribute("userName");
            String userEmail = (String) request.getAttribute("userEmail");
            if (userName == null || userName.trim().isEmpty()) {
                userName = "Unknown User";
            }
            if (userEmail == null || userEmail.trim().isEmpty()) {
                userEmail = "unknown@example.com";
            }

            // Create resolution entry: "Cancelled by {Username} ({Email})"
            String resolutionText = "Cancelled by " + userName + " (" + userEmail + ")";
            
            ChangeRequestResolutionDAO resolutionDAO = new ChangeRequestResolutionDAO();
            
            // Get cancel status ID from CR_Resolution_Status table
            // First, try to find a resolution status with "Cancel" in the name
            Integer cancelStatusId = resolutionDAO.getResolutionStatusIdByName("Cancel");
            if (cancelStatusId == null) {
                // Try "Cancelled"
                cancelStatusId = resolutionDAO.getResolutionStatusIdByName("Cancelled");
            }
            
            if (cancelStatusId == null) {
                logger.warn("Cancel resolution status not found, proceeding without status ID");
            }

            // STEP 2: Create resolution entry
            // This must happen before discarding changes to maintain audit trail
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 2] Creating resolution entry for cancelled CR {}", changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            ChangeRequestResolution resolution = new ChangeRequestResolution();
            resolution.setChangeRequestId(changeRequestId);
            if (cancelStatusId != null) {
                resolution.setResolutionStatusId(cancelStatusId);
            }
            resolution.setDescription(resolutionText);
            resolution.setLastUserChange(userId);
            resolution.setCreatedAt(LocalDateTime.now());
            resolution.setUpdatedAt(LocalDateTime.now());
            
            Integer resolutionId = null;
            try {
                resolutionId = resolutionDAO.createResolution(resolution);
                logger.info("✅ [STEP 2] Created resolution {} for cancelled CR {}", resolutionId, changeRequestId);
            } catch (Exception e) {
                logger.error("❌ [STEP 2 ERROR] Failed to create resolution entry for CR {}: {}", changeRequestId, e.getMessage(), e);
                logger.warn("⚠️  [STEP 2] Continuing with cancellation despite resolution creation failure...");
                // Continue with cancellation even if resolution creation fails
            }

            // STEP 3: Snapshot all pending changes to cr_changes_review BEFORE discarding
            // This ensures all changes are persisted for display after cancel
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 3] Snapshotting pending changes to cr_changes_review for CR {}", changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            try {
                PendingChangesServlet pendingChangesServlet = new PendingChangesServlet();
                pendingChangesServlet.calculateAndSaveChanges(changeRequestId);
                logger.info("✅ [STEP 3] Successfully snapshotted pending changes for CR {}", changeRequestId);
            } catch (Exception e) {
                logger.error("❌ [STEP 3 ERROR] Error snapshotting pending changes for CR {}: {}", changeRequestId, e.getMessage(), e);
                logger.warn("⚠️  [STEP 3] Continuing with cancellation despite snapshot failure...");
            }

            // STEP 4: Discard pending changes: delete nobject_id data without copying to object_id
            // This is the opposite of complete - we keep object_id and delete nobject_id
            // This must happen before status update to ensure data consistency
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 4] Discarding pending changes for cancelled CR {}", changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            try {
                logger.info("🔄 [STEP 4.1] Calling FacetChangesService.discardChangesForCR({})...", changeRequestId);
                com.example.budg_v2.service.FacetChangesService facetChangesService = 
                    new com.example.budg_v2.service.FacetChangesService();
                facetChangesService.discardChangesForCR(changeRequestId);
                logger.info("✅ [STEP 4.2] Successfully discarded pending changes for CR {}", changeRequestId);
            } catch (Exception e) {
                logger.error("❌ [STEP 4 ERROR] Error discarding pending changes for CR {}: {}", changeRequestId, e.getMessage(), e);
                logger.warn("⚠️  [STEP 4] Continuing with cancellation despite discard failure (status will still be updated)...");
                // Continue with cancellation even if discarding changes fails
            }

            // STEP 5: Update CR status to cancelled status
            // This is the final step and must execute even if previous steps had errors
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("📋 [STEP 5] Updating CR {} status to Cancelled", changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            // Try to find "Cancelled" or "Canceled" status in changerequeststatus table
            Integer cancelledStatusId = changeRequestDAO.getStatusIdByName("Cancelled");
            if (cancelledStatusId == null) {
                // Try alternative spelling
                cancelledStatusId = changeRequestDAO.getStatusIdByName("Canceled");
            }
            if (cancelledStatusId != null) {
                try {
                    cr.setCrStatusId(cancelledStatusId);
                    cr.setLastUserChange(userId);
                    cr.setUpdatedAt(LocalDateTime.now());
                    boolean updated = changeRequestDAO.updateChangeRequest(cr);
                    if (updated) {
                        logger.info("✅ [STEP 4] Successfully updated CR {} status to cancelled (status ID: {})", changeRequestId, cancelledStatusId);
                    } else {
                        logger.error("❌ [STEP 4 ERROR] Failed to update CR {} status to cancelled. Update returned false.", changeRequestId);
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Failed to update change request status to cancelled");
                        response.getWriter().write(gson.toJson(error));
                        return;
                    }
                } catch (Exception e) {
                    logger.error("❌ [STEP 4 CRITICAL ERROR] Exception updating CR {} status: {}", changeRequestId, e.getMessage(), e);
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Failed to update change request status: " + e.getMessage());
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            } else {
                logger.error("❌ [STEP 4 CRITICAL ERROR] Cancelled/Canceled status not found in changerequeststatus table. CR {} status was not updated.", changeRequestId);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Cancelled status not found in database. Please contact administrator.");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Return success response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Change request cancelled successfully");
            response.getWriter().write(gson.toJson(responseData));

        } catch (SQLException e) {
            logger.error("Database error cancelling change request", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error cancelling change request", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Check if user can complete CR (super admin, admin, or stakeholder)
     */
    /**
     * Adds resolved primary/display name for the CR's referenced object (e.g. process title for "Process 89").
     */
    private void addReferenceObjectDisplayName(JsonObject crJson, ChangeRequest changeRequest) {
        if (changeRequest == null || crJson == null) {
            return;
        }
        String ref = changeRequest.getReference();
        if (ref == null || ref.trim().isEmpty()) {
            return;
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            String displayName = WorkflowTaskDAO.resolveDisplayNameForCrReference(conn, ref);
            if (displayName != null && !displayName.trim().isEmpty()) {
                crJson.addProperty("referenceObjectName", displayName.trim());
            }
        } catch (Exception e) {
            logger.debug("Could not resolve referenceObjectName for '{}': {}", ref, e.getMessage());
        }
    }

    private boolean checkUserCanCompleteCR(HttpServletRequest request, int userId, int changeRequestId) {
        try {
            // Check if user is admin or super admin
            if (UserContextUtil.isCurrentUserAdmin(request)) {
                logger.info("User {} is admin/super admin, authorized to complete CR {}", userId, changeRequestId);
                return true;
            }
            
            // Get change request to check if user is the creator (requestor)
            ChangeRequest cr = changeRequestDAO.getChangeRequestById(changeRequestId);
            if (cr == null) {
                logger.warn("Change request {} not found", changeRequestId);
                return false;
            }
            
            // Exclude the requestor (creator) - only stakeholders (not creator) can complete
            Integer createdBy = cr.getCreatedBy();
            if (createdBy != null && createdBy == userId) {
                logger.info("User {} is the requestor (creator) of CR {}, not authorized to complete (only stakeholders can complete)", userId, changeRequestId);
                return false;
            }
            
            boolean isAutoCR = cr.getMandatoryWorkflow() != null && cr.getMandatoryWorkflow();
            List<Map<String, Object>> stakeholders;
            if (isAutoCR) {
                String ref = cr.getReference();
                stakeholders = (ref != null && !ref.trim().isEmpty())
                        ? crStakeholderDAO.getStakeholdersFromSourceObject(ref)
                        : new java.util.ArrayList<>();
            } else {
                stakeholders = crStakeholderDAO.getStakeholdersForManualChangeRequest(changeRequestId, cr.getReference());
            }
            for (Map<String, Object> stakeholder : stakeholders) {
                Integer stakeholderUserId = extractStakeholderUserId(stakeholder);
                if (stakeholderUserId != null && stakeholderUserId == userId) {
                    logger.info("User {} is stakeholder (not requestor), authorized to complete CR {}", userId, changeRequestId);
                    return true;
                }
            }
            
            logger.warn("User {} is not authorized to complete CR {} (not admin, not stakeholder, or is requestor)", userId, changeRequestId);
            return false;
        } catch (SQLException e) {
            logger.error("Error checking user authorization", e);
            return false;
        }
    }

    /**
     * Get user role from request
     */
    @SuppressWarnings("unused")
    private String getUserRole(HttpServletRequest request) {
        Object roleObj = request.getAttribute("userRole");
        if (roleObj instanceof String) {
            return (String) roleObj;
        }
        // Try session
        if (request.getSession(false) != null) {
            Object sessionRole = request.getSession().getAttribute("userRole");
            if (sessionRole instanceof String) {
                return (String) sessionRole;
            }
        }
        return null;
    }

    /**
     * Check if user is a stakeholder of the change request
     */
    @SuppressWarnings("unused")
    private boolean checkUserIsStakeholder(int changeRequestId, int userId) {
        try {
            List<Map<String, Object>> stakeholders = crStakeholderDAO.getStakeholdersForChangeRequest(changeRequestId);
            for (Map<String, Object> stakeholder : stakeholders) {
                Integer stakeholderUserId = (Integer) stakeholder.get("User_ID");
                if (stakeholderUserId != null && stakeholderUserId == userId) {
                    logger.info("User {} is a stakeholder of CR {}", userId, changeRequestId);
                    return true;
                }
            }
            logger.debug("User {} is not a stakeholder of CR {}", userId, changeRequestId);
            return false;
        } catch (SQLException e) {
            logger.error("Error checking if user is stakeholder", e);
            return false;
        }
    }

    /**
     * Check if user can cancel CR
     * Requester can cancel only if CR status is NOT RUNNING
     * Stakeholders can cancel even if CR status is RUNNING (including auto CRs)
     */
    private boolean canCancelChangeRequest(int userId, int changeRequestId, ChangeRequest cr) {
        try {
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("🔐 [AUTHORIZATION CHECK] Starting canCancelChangeRequest");
            logger.info("🔐 [AUTHORIZATION CHECK] userId: {}, changeRequestId: {}", userId, changeRequestId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            // Check if this is an auto CR (mandatory workflow)
            Boolean mandatoryWorkflow = cr.getMandatoryWorkflow();
            boolean isAutoCR = (mandatoryWorkflow != null && mandatoryWorkflow);
            
            logger.info("🔐 [AUTHORIZATION CHECK] Checking cancel authorization for user {} on CR {} (isAutoCR: {}, mandatoryWorkflow: {})", 
                userId, changeRequestId, isAutoCR, mandatoryWorkflow);
            
            String statusName = cr.getStatusName();
            if (statusName == null) {
                statusName = changeRequestDAO.getStatusNameById(cr.getCrStatusId());
            }
            String statusLower = (statusName != null) ? statusName.toLowerCase() : "";
            boolean isRunning = statusLower.contains("running") || statusLower.contains("in progress");
            
            // Check if user is the creator
            boolean isCreator = (cr.getCreatedBy() != null && cr.getCreatedBy() == userId);
            
            // Check if user is a stakeholder - this check is needed for both Running and non-Running statuses
            boolean isStakeholder = false;
            try {
                List<Map<String, Object>> stakeholders;
                
                // For auto CRs, get stakeholders from the source object (e.g., System)
                // For manual CRs, get stakeholders from cr_stakeholders table
                if (isAutoCR) {
                    String reference = cr.getReference();
                    if (reference != null && !reference.trim().isEmpty()) {
                        logger.info("🔍 [STAKEHOLDER CHECK] Auto CR detected - fetching stakeholders from source object: {}", reference);
                        stakeholders = crStakeholderDAO.getStakeholdersFromSourceObject(reference);
                    } else {
                        logger.warn("⚠️ [STAKEHOLDER CHECK] Auto CR has no reference - cannot fetch stakeholders from source object");
                        stakeholders = new ArrayList<>();
                    }
                } else {
                    logger.info("🔍 [STAKEHOLDER CHECK] Manual CR — cr_stakeholders plus live source object");
                    stakeholders = crStakeholderDAO.getStakeholdersForManualChangeRequest(changeRequestId, cr.getReference());
                }
                
                logger.info("🔍 [STAKEHOLDER CHECK] Found {} stakeholders for CR {} (isAutoCR: {}, checking userId: {})", 
                    stakeholders.size(), changeRequestId, isAutoCR, userId);
                
                if (stakeholders.isEmpty()) {
                    logger.warn("⚠️ [STAKEHOLDER CHECK] No stakeholders found for CR {} (isAutoCR: {})", changeRequestId, isAutoCR);
                }
                
                // Log all stakeholder user IDs for debugging
                List<Integer> stakeholderUserIds = new ArrayList<>();
                for (Map<String, Object> stakeholder : stakeholders) {
                    // Try multiple keys: userId, User_ID, user_id, personId
                    Object userIdObj = stakeholder.get("userId");
                    if (userIdObj == null) {
                        userIdObj = stakeholder.get("User_ID");
                    }
                    if (userIdObj == null) {
                        userIdObj = stakeholder.get("user_id");
                    }
                    if (userIdObj == null) {
                        userIdObj = stakeholder.get("personId"); // For auto CRs from source object
                    }
                    
                    if (userIdObj != null) {
                        try {
                            int stakeholderUserId = 0;
                            if (userIdObj instanceof Integer) {
                                stakeholderUserId = (Integer) userIdObj;
                            } else if (userIdObj instanceof Long) {
                                stakeholderUserId = ((Long) userIdObj).intValue();
                            } else if (userIdObj instanceof Number) {
                                stakeholderUserId = ((Number) userIdObj).intValue();
                            } else if (userIdObj instanceof String) {
                                stakeholderUserId = Integer.parseInt((String) userIdObj);
                            }
                            stakeholderUserIds.add(stakeholderUserId);
                        } catch (Exception e) {
                            logger.warn("Could not extract userId from stakeholder: {}", userIdObj);
                        }
                    }
                }
                logger.info("🔍 [STAKEHOLDER CHECK] Stakeholder user IDs: {}", stakeholderUserIds);
                
                for (Map<String, Object> stakeholder : stakeholders) {
                    // Try both "userId" (camelCase from DAO) and "User_ID" (snake_case) for compatibility
                    Object userIdObj = stakeholder.get("userId");
                    if (userIdObj == null) {
                        userIdObj = stakeholder.get("User_ID");
                    }
                    if (userIdObj == null) {
                        userIdObj = stakeholder.get("user_id");
                    }
                    if (userIdObj == null) {
                        userIdObj = stakeholder.get("personId"); // Some methods use personId
                    }
                    
                    if (userIdObj != null) {
                        // Handle different data types (Integer, Long, String, etc.)
                        int stakeholderUserId = 0;
                        if (userIdObj instanceof Integer) {
                            stakeholderUserId = (Integer) userIdObj;
                        } else if (userIdObj instanceof Long) {
                            stakeholderUserId = ((Long) userIdObj).intValue();
                        } else if (userIdObj instanceof Number) {
                            stakeholderUserId = ((Number) userIdObj).intValue();
                        } else if (userIdObj instanceof String) {
                            try {
                                stakeholderUserId = Integer.parseInt((String) userIdObj);
                            } catch (NumberFormatException e) {
                                logger.warn("Invalid userId format in stakeholder: {}", userIdObj);
                                continue;
                            }
                        } else {
                            logger.warn("Unexpected userId type in stakeholder: {}", userIdObj.getClass().getName());
                            continue;
                        }
                        
                        logger.info("🔍 [STAKEHOLDER CHECK] Comparing stakeholder userId {} with current userId {}", stakeholderUserId, userId);
                        if (stakeholderUserId == userId) {
                            isStakeholder = true;
                            logger.info("✅ [STAKEHOLDER CHECK] User {} is a stakeholder of CR {}", userId, changeRequestId);
                            break;
                        }
                    } else {
                        logger.warn("⚠️ [STAKEHOLDER CHECK] Stakeholder entry has no userId/userId/User_ID/personId. Map keys: {}", stakeholder.keySet());
                        logger.warn("⚠️ [STAKEHOLDER CHECK] Full stakeholder map: {}", stakeholder);
                    }
                }
                
                if (!isStakeholder) {
                    logger.warn("❌ [STAKEHOLDER CHECK] User {} is not found in the {} stakeholders for CR {} (isAutoCR: {})", userId, stakeholders.size(), changeRequestId, isAutoCR);
                    logger.warn("❌ [STAKEHOLDER CHECK] Expected userId: {}, Found stakeholder userIds: {}", userId, stakeholderUserIds);
                }
            } catch (Exception e) {
                logger.error("❌ [STAKEHOLDER CHECK] Error checking stakeholders for CR {} (isAutoCR: {}): {}", changeRequestId, isAutoCR, e.getMessage(), e);
                e.printStackTrace();
                // Don't fail completely - continue to check other conditions
            }
            
            // NEW LOGIC: If CR status is RUNNING, only stakeholders can cancel
            if (isRunning) {
                if (isStakeholder) {
                    logger.info("✅ [AUTHORIZATION] User {} is a stakeholder of CR {} with RUNNING status - authorized to cancel", userId, changeRequestId);
                    return true;
                } else {
                    // Even if user is creator, they cannot cancel if CR is RUNNING (unless they are also stakeholder)
                    logger.warn("❌ [AUTHORIZATION] User {} is not authorized to cancel CR {} with RUNNING status. Only stakeholders can cancel RUNNING CRs.", userId, changeRequestId);
                    if (isCreator) {
                        logger.info("ℹ️ [AUTHORIZATION] User {} is the creator but not a stakeholder - cannot cancel RUNNING CR", userId);
                    }
                    return false;
                }
            }
            
            // If CR status is NOT RUNNING: creator OR stakeholder can cancel
            if (isCreator) {
                logger.info("✅ [AUTHORIZATION] User {} is the creator of CR {} (not RUNNING) - authorized to cancel", userId, changeRequestId);
                return true;
            }
            
            if (isStakeholder) {
                logger.info("✅ [AUTHORIZATION] User {} is a stakeholder of CR {} (not RUNNING) - authorized to cancel", userId, changeRequestId);
                return true;
            }
            
            logger.warn("❌ [AUTHORIZATION] User {} is not authorized to cancel CR {} (not creator and not stakeholder)", userId, changeRequestId);
            return false;
        } catch (Exception e) {
            logger.error("Error checking if user can cancel CR {}: {}", changeRequestId, e.getMessage(), e);
            return false;
        }
    }
    private static Integer extractStakeholderUserId(Map<String, Object> stakeholder) {
        Object userIdObj = stakeholder.get("userId");
        if (userIdObj == null) {
            userIdObj = stakeholder.get("User_ID");
        }
        if (userIdObj == null) {
            userIdObj = stakeholder.get("user_id");
        }
        if (userIdObj == null) {
            userIdObj = stakeholder.get("personId");
        }
        if (userIdObj == null) {
            return null;
        }
        if (userIdObj instanceof Integer) {
            return (Integer) userIdObj;
        }
        if (userIdObj instanceof Long) {
            return ((Long) userIdObj).intValue();
        }
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).intValue();
        }
        if (userIdObj instanceof String) {
            try {
                return Integer.parseInt((String) userIdObj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get status ID by status name from changerequeststatus table
     */
    private Integer getStatusIdByName(String statusName) {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            String sql = "SELECT ID FROM changerequeststatus WHERE PrimaryName = ?";
            try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, statusName);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("ID");
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error getting status ID for '{}': {}", statusName, e.getMessage());
        }
        return null;
    }
}
