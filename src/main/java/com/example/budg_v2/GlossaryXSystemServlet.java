package com.example.budg_v2;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.dao.GlossaryXSystemDAO;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.UserContextUtil;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/glossary-x-system/*")
public class GlossaryXSystemServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(GlossaryXSystemServlet.class);
    private static final int GLOSSARY_FACET_TYPE = 12;
    private final GlossaryXSystemDAO dao = new GlossaryXSystemDAO();
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Missing glossary ID", 400);
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");

            if (pathParts.length == 1) {
                int glossaryId = Integer.parseInt(pathParts[0]);
                
                // Check if viewing changes - use cloned glossary ID like Impact tab
                String view = req.getParameter("view");
                logger.debug("GET strategic sources for glossary {} with view parameter: '{}'", glossaryId, view);
                
                int glossaryIdToLoad = glossaryId; // Default to original ID
                
                if (view != null && "changes".equals(view.trim())) {
                    // Like Impact tab: use cloned glossary ID for view=changes
                    try {
                        Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_TYPE, glossaryId);
                        if (activeCrId != null) {
                            Integer nobjectId = facetChangesDAO.getNObjectId("glossary", glossaryId, "summary", activeCrId);
                            if (nobjectId == null) {
                                // Clone glossary if needed
                                try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                                    nobjectId = cloneGlossaryRow(glossaryId);
                                    if (nobjectId != null) {
                                        facetChangesDAO.saveMapping("glossary", glossaryId, nobjectId, "summary", activeCrId);
                                        logger.info("Cloned glossary {} to {} for strategic source view=changes", glossaryId, nobjectId);
                                    }
                                }
                            }
                            if (nobjectId != null) {
                                glossaryIdToLoad = nobjectId;
                                logger.info("Using cloned glossary ID {} (original: {}) for strategic source view=changes", nobjectId, glossaryId);
                            }
                        }
                    } catch (SQLException e) {
                        logger.error("Error getting cloned glossary for view=changes: {}", e.getMessage());
                    }
                }
                
                // Load strategic sources using glossaryIdToLoad (cloned if view=changes, original otherwise)
                List<Map<String, Object>> results = dao.getByGlossaryId(glossaryIdToLoad);
                logger.debug("Returning {} strategic sources for glossary {} (using glossaryIdToLoad: {})", results.size(), glossaryId, glossaryIdToLoad);
                // V-05: filter inaccessible related systems to hidden markers
                results = RelationshipAccessUtil.filterBySegmentAccess(results, req, "System", "systemId");
                JsonUtil.sendJsonResponse(resp.getWriter(), results);

            } else if (pathParts.length == 2) {
                int glossaryIdPath = Integer.parseInt(pathParts[0]);
                String action = pathParts[1];

                if ("relation-types".equals(action)) {
                    List<Map<String, Object>> results = dao.getRelationTypes();
                    JsonUtil.sendJsonResponse(resp.getWriter(), results);

                } else if ("systems".equals(action)) {
                    // Exclude systems already linked from Data Content Summary (Link_Source = 'system')
                    List<Map<String, Object>> results = dao.getSystemsForStrategicSource(glossaryIdPath);
                    JsonUtil.sendJsonResponse(resp.getWriter(), results);

                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid action: " + action, 400);
                }

            } else if (pathParts.length == 2) {
                String action = pathParts[1];
                
                if ("datasets".equals(action)) {
                    // Get all datasets
                    List<Map<String, Object>> results = dao.getAllDatasets();
                    JsonUtil.sendJsonResponse(resp.getWriter(), results);
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid action: " + action, 400);
                }
                
            } else if (pathParts.length == 3) {
                String action = pathParts[1];
                String subAction = pathParts[2];

                if ("datasets".equals(action) && subAction.matches("\\d+")) {
                    int systemId = Integer.parseInt(subAction);
                    List<Map<String, Object>> results = dao.getDatasetsBySystemId(systemId);
                    if (results.isEmpty()) {
                        results = dao.getAllDatasets();
                    }
                    JsonUtil.sendJsonResponse(resp.getWriter(), results);
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid path: " + action + "/" + subAction, 400);
                }
            } else {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid path", 400);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid ID format", 400);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Missing glossary ID", 400);
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");

            if (pathParts.length == 1) {
                int glossaryId = Integer.parseInt(pathParts[0]);
                logger.info("POST strategic source for glossary {}: Creating new strategic source relationship", glossaryId);

                JsonObject jsonBody = JsonUtil.parseJsonFromRequest(req.getReader());
                Integer systemId = JsonUtil.getJsonInt(jsonBody, "systemId");
                Integer datasetId = JsonUtil.getJsonInt(jsonBody, "datasetId");
                Integer relationTypeId = JsonUtil.getJsonInt(jsonBody, "relationTypeId");
                
                logger.debug("Strategic source data: glossaryId={}, systemId={}, datasetId={}, relationTypeId={}", 
                            glossaryId, systemId, datasetId, relationTypeId);

                int userId = UserContextUtil.getCurrentUserId(req);
                if (userId > 0) {
                    try {
                        boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
                        new DFCRService().ensureEditAutoCrIfMissing("Glossary", GLOSSARY_FACET_TYPE, glossaryId, null, userId, isAdmin);
                    } catch (Exception e) {
                        logger.warn("[GlossaryXSystem] DFCR ensure before strategic source POST: {}", e.getMessage());
                    }
                }

                // Check for active automatic CR and get cloned glossary ID (like Impact tab)
                Integer activeCrId = null;
                int glossaryIdToUse = glossaryId; // Default to original ID
                
                try {
                    activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_TYPE, glossaryId);
                    if (activeCrId != null) {
                        logger.info("Active CR {} found for glossary {}. Creating strategic source as pending change.", activeCrId, glossaryId);
                        
                        // Get or create cloned glossary (like Impact tab does with cloned process)
                        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                            Integer nobjectId = facetChangesDAO.getNObjectId("glossary", glossaryId, "summary", activeCrId);
                            
                            if (nobjectId == null) {
                                // No summary mapping exists - clone the glossary
                                nobjectId = cloneGlossaryRow(glossaryId);
                                if (nobjectId != null) {
                                    facetChangesDAO.saveMapping("glossary", glossaryId, nobjectId, "summary", activeCrId);
                                    logger.info("Cloned glossary {} to {} for strategic source pending changes", glossaryId, nobjectId);
                                }
                            }
                            
                            if (nobjectId != null) {
                                glossaryIdToUse = nobjectId;
                                logger.info("Using cloned glossary ID {} (original: {}) for strategic source", nobjectId, glossaryId);
                            } else {
                                logger.warn("Could not get/create cloned glossary for strategic source, using original ID {}", glossaryId);
                            }
                        } catch (SQLException e) {
                            logger.error("Error getting/cloning glossary for strategic source: {}", e.getMessage());
                            // Continue with original ID
                        }
                    } else {
                        logger.debug("No active CR found for glossary {}", glossaryId);
                    }
                } catch (SQLException e) {
                    logger.error("Error checking for active CR for glossary {}: {}", glossaryId, e.getMessage());
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Error checking for active change request: " + e.getMessage(), 500);
                    return;
                }

                if (!validateStrategicSourceSegments(glossaryIdToUse, systemId, datasetId, resp)) {
                    return;
                }

                // Mutual exclusion: reject if this system is already linked from Data Content Summary (Link_Source = 'system')
                try {
                    if (systemId != null && systemId > 0 && dao.existsWithLinkSourceSystem(glossaryId, systemId)) {
                        JsonUtil.sendErrorResponse(resp.getWriter(),
                            "This system is already linked in the System's Data Content Summary. It cannot also be added as a Strategic Source.", 400);
                        return;
                    }
                    // Also block exact duplicates within the strategic source for this glossary
                    if (dao.existsDuplicate(glossaryIdToUse, systemId, datasetId, relationTypeId)) {
                        JsonUtil.sendErrorResponse(resp.getWriter(),
                            "Duplicate relationship detected for this glossary: same System, Dataset, and Relation Type already exist.", 400);
                        return;
                    }
                } catch (SQLException e) {
                    logger.error("Error checking duplicates/link source for glossary {} system {}: {}", glossaryId, systemId, e.getMessage());
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Database error checking relationship.", 500);
                    return;
                }

                // Create the strategic source relationship using glossaryIdToUse (cloned if CR active, original otherwise)
                int newId = dao.insert(glossaryIdToUse, systemId, datasetId, relationTypeId);
                logger.info("Created strategic source relationship with ID {} for glossary {} (using glossaryIdToUse: {})", newId, glossaryId, glossaryIdToUse);
                logger.info("🔍 DEBUG: Relationship created - newId={}, glossaryId={}, glossaryIdToUse={}, activeCrId={}", 
                    newId, glossaryId, glossaryIdToUse, activeCrId);
                
                if (newId <= 0) {
                    logger.error("Failed to create strategic source relationship - insert returned ID: {}", newId);
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Failed to create strategic source relationship", 500);
                    return;
                }
                
                // If there's an active CR, save the mapping - THIS IS CRITICAL
                if (activeCrId != null) {
                    String areaKey = "relationships#glossary_x_system";
                    try {
                        // CRITICAL: Verify that newId is valid (not 0 or negative) and not equal to object_id
                        // This ensures we're saving the relationship ID, not the object_id
                        if (newId <= 0) {
                            logger.error("❌ CRITICAL: Invalid relationship ID {} for strategic source mapping. Cannot save mapping.", newId);
                            JsonUtil.sendErrorResponse(resp.getWriter(), 
                                "Failed to create strategic source relationship - invalid ID returned", 500);
                            return;
                        }
                        
                        if (newId == glossaryId) {
                            logger.error("❌ CRITICAL: Relationship ID {} equals object_id {}. This should not happen - relationship ID should be different from object_id.", 
                                newId, glossaryId);
                            JsonUtil.sendErrorResponse(resp.getWriter(), 
                                "Failed to create strategic source relationship - relationship ID equals object ID", 500);
                            return;
                        }
                        
                        logger.info("Attempting to save strategic source mapping: glossary={}, strategicSourceId={}, areaKey={}, crId={}", 
                                    glossaryId, newId, areaKey, activeCrId);
                        facetChangesDAO.saveMapping("glossary", glossaryId, newId, areaKey, activeCrId);
                        logger.info("✅ Successfully saved strategic source mapping: glossary={}, strategicSourceId={}, areaKey={}, crId={}", 
                                    glossaryId, newId, areaKey, activeCrId);
                        JsonUtil.sendJsonResponse(resp.getWriter(), Map.of(
                            "id", newId, 
                            "success", true,
                            "pending", true,
                            "changeRequestId", activeCrId,
                            "message", "Strategic source saved as pending change"
                        ));
                        return;
                    } catch (SQLException e) {
                        logger.error("❌ CRITICAL: Error saving strategic source mapping for glossary {}: {}", glossaryId, e.getMessage(), e);
                        logger.error("   Strategic source {} was created but mapping to glossary_changes FAILED!", newId);
                        logger.error("   This means the strategic source will not appear in pending changes!");
                        // Return error - mapping save is critical for pending changes tracking
                        JsonUtil.sendErrorResponse(resp.getWriter(), 
                            "Strategic source created but failed to track as pending change. Please contact support. Error: " + e.getMessage(), 500);
                        return;
                    }
                } else {
                    logger.warn("⚠️ No active CR found for glossary {} - strategic source {} saved without pending change tracking", glossaryId, newId);
                    logger.warn("   This may be expected if no CR is active, but if a CR should be active, this is a problem!");
                }
                
                // No active CR - save completed normally
                JsonUtil.sendJsonResponse(resp.getWriter(), Map.of("id", newId, "success", true));

            } else {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid path", 400);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid ID format", 400);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Missing ID", 400);
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");

            if (pathParts.length == 1) {
                int id = Integer.parseInt(pathParts[0]);
                logger.info("PUT strategic source {}: Updating strategic source relationship", id);

                JsonObject jsonBody = JsonUtil.parseJsonFromRequest(req.getReader());
                Integer systemId = JsonUtil.getJsonInt(jsonBody, "systemId");
                Integer datasetId = JsonUtil.getJsonInt(jsonBody, "datasetId");
                Integer relationTypeId = JsonUtil.getJsonInt(jsonBody, "relationTypeId");
                
                logger.debug("Strategic source update data: id={}, systemId={}, datasetId={}, relationTypeId={}", 
                            id, systemId, datasetId, relationTypeId);
                
                // Get glossaryId from the existing record to check for active CR
                Integer glossaryId = JsonUtil.getJsonInt(jsonBody, "glossaryId");
                
                if (glossaryId == null) {
                    // Try to get glossaryId from the existing record
                    logger.debug("glossaryId not in request body, fetching from existing record for strategic source {}", id);
                    try {
                        Map<String, Object> existing = dao.getById(id);
                        if (existing != null && existing.get("glossaryId") != null) {
                            glossaryId = ((Number) existing.get("glossaryId")).intValue();
                            logger.debug("Retrieved glossaryId {} from existing strategic source {}", glossaryId, id);
                        } else {
                            logger.warn("Could not retrieve glossaryId from existing strategic source {} - record may not exist", id);
                        }
                    } catch (SQLException e) {
                        logger.error("Error fetching existing strategic source {}: {}", id, e.getMessage());
                    }
                }
                
                // Check if glossaryId is a cloned row (nobject_id) and get the original object_id
                // This is important because strategic sources may be linked to cloned glossaries
                Integer originalGlossaryId = glossaryId;
                if (glossaryId != null) {
                    try {
                        Integer foundOriginal = facetChangesDAO.getOriginalObjectId("glossary", glossaryId);
                        if (foundOriginal != null) {
                            originalGlossaryId = foundOriginal;
                            logger.info("Strategic source {} is linked to cloned glossary {} (original: {})", id, glossaryId, originalGlossaryId);
                        }
                    } catch (SQLException e) {
                        logger.warn("Error checking for original glossary ID: {}", e.getMessage());
                    }
                }

                if (originalGlossaryId != null) {
                    int userId = UserContextUtil.getCurrentUserId(req);
                    if (userId > 0) {
                        try {
                            boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
                            new DFCRService().ensureEditAutoCrIfMissing("Glossary", GLOSSARY_FACET_TYPE, originalGlossaryId, null, userId, isAdmin);
                        } catch (Exception e) {
                            logger.warn("[GlossaryXSystem] DFCR ensure before strategic source PUT: {}", e.getMessage());
                        }
                    }
                }
                
                // Check for active automatic CR BEFORE updating using the ORIGINAL glossary ID
                Integer activeCrId = null;
                if (originalGlossaryId != null) {
                    try {
                        activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_TYPE, originalGlossaryId);
                        if (activeCrId != null) {
                            logger.info("Active CR {} found for glossary {} (original: {}). Updating strategic source {} as pending change.", 
                                       activeCrId, glossaryId, originalGlossaryId, id);
                        } else {
                            logger.debug("No active CR found for glossary {} (original: {})", glossaryId, originalGlossaryId);
                        }
                    } catch (SQLException e) {
                        logger.error("Error checking for active CR for glossary {}: {}", originalGlossaryId, e.getMessage());
                        JsonUtil.sendErrorResponse(resp.getWriter(), "Error checking for active change request: " + e.getMessage(), 500);
                        return;
                    }
                } else {
                    logger.warn("Cannot check for active CR - glossaryId is null for strategic source {}", id);
                }

                if (!validateStrategicSourceSegments(glossaryId, systemId, datasetId, resp)) {
                    return;
                }

                // Prevent duplicates BEFORE performing the update
                try {
                    // Determine glossaryId for this record if not provided
                    Integer gIdForDupCheck = glossaryId;
                    if (gIdForDupCheck == null) {
                        Map<String, Object> existing = dao.getById(id);
                        if (existing != null && existing.get("glossaryId") != null) {
                            gIdForDupCheck = ((Number) existing.get("glossaryId")).intValue();
                        }
                    }
                    if (gIdForDupCheck != null && dao.existsDuplicateExcludingId(id, gIdForDupCheck, systemId, datasetId, relationTypeId)) {
                        JsonUtil.sendErrorResponse(resp.getWriter(),
                                "Update would create a duplicate relationship for this glossary.", 400);
                        return;
                    }
                } catch (SQLException e) {
                    logger.error("Error checking duplicates on update for strategic source {}: {}", id, e.getMessage());
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Database error checking duplicates.", 500);
                    return;
                }

                boolean success = dao.update(id, systemId, datasetId, relationTypeId);
                logger.info("Strategic source {} update result: {}", id, success ? "success" : "failed");
                
                if (!success) {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Failed to update strategic source relationship", 500);
                    return;
                }
                
                // If there's an active CR, save the mapping - THIS IS CRITICAL
                // Use originalGlossaryId for the mapping (object_id in glossary_changes)
                if (activeCrId != null && originalGlossaryId != null) {
                    String areaKey = "relationships#glossary_x_system";
                    try {
                        logger.info("Attempting to save strategic source update mapping: originalGlossary={}, strategicSourceId={}, areaKey={}, crId={}", 
                                    originalGlossaryId, id, areaKey, activeCrId);
                        facetChangesDAO.saveMapping("glossary", originalGlossaryId, id, areaKey, activeCrId);
                        logger.info("✅ Successfully saved strategic source update mapping: originalGlossary={}, strategicSourceId={}, areaKey={}, crId={}", 
                                    originalGlossaryId, id, areaKey, activeCrId);
                        JsonUtil.sendJsonResponse(resp.getWriter(), Map.of(
                            "success", success,
                            "pending", true,
                            "changeRequestId", activeCrId,
                            "message", "Strategic source update saved as pending change"
                        ));
                        return;
                    } catch (SQLException e) {
                        logger.error("❌ CRITICAL: Error saving strategic source update mapping for glossary {}: {}", originalGlossaryId, e.getMessage(), e);
                        logger.error("   Strategic source {} was updated but mapping to glossary_changes FAILED!", id);
                        logger.error("   This means the update will not appear in pending changes!");
                        // Return error - mapping save is critical for pending changes tracking
                        JsonUtil.sendErrorResponse(resp.getWriter(), 
                            "Strategic source updated but failed to track as pending change. Please contact support. Error: " + e.getMessage(), 500);
                        return;
                    }
                } else {
                    if (originalGlossaryId == null) {
                        logger.warn("⚠️ Cannot save mapping - glossaryId is null for strategic source {}", id);
                    }
                    if (activeCrId == null) {
                        logger.warn("⚠️ No active CR found for glossary {} (original: {}) - strategic source {} updated without pending change tracking", 
                                   glossaryId != null ? glossaryId : "unknown", originalGlossaryId, id);
                    }
                }
                
                // No active CR - update completed normally
                JsonUtil.sendJsonResponse(resp.getWriter(), Map.of("success", success));

            } else {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid path", 400);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid ID format", 400);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }

    private boolean validateStrategicSourceSegments(Integer glossaryId, Integer systemId, Integer datasetId,
                                                    HttpServletResponse resp) throws IOException {
        if (glossaryId == null || glossaryId <= 0) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid glossary ID for segment validation", 400);
            return false;
        }
        try {
            if (systemId != null && systemId > 0) {
                SegmentValidationService.ValidationResult result =
                        segmentValidationService.validateCrossSegmentRelationship(
                                glossaryId, "Glossary", systemId, "System");
                if (!result.isValid) {
                    JsonUtil.sendErrorResponse(resp.getWriter(), result.message, 400);
                    return false;
                }
            }

            if (datasetId != null && datasetId > 0) {
                SegmentValidationService.ValidationResult result =
                        segmentValidationService.validateCrossSegmentRelationship(
                                glossaryId, "Glossary", datasetId, "Dataset");
                if (!result.isValid) {
                    JsonUtil.sendErrorResponse(resp.getWriter(), result.message, 400);
                    return false;
                }
            }
            return true;
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error validating relationship: " + e.getMessage(), 500);
            return false;
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Missing ID", 400);
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");

            if (pathParts.length == 1) {
                int id = Integer.parseInt(pathParts[0]);
                boolean success = dao.delete(id);
                JsonUtil.sendJsonResponse(resp.getWriter(), Map.of("success", success));

            } else {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid path", 400);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid ID format", 400);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Clone a glossary row for pending changes (same as GlossaryStakeholderServlet.cloneGlossaryRow)
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
        
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
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
}
