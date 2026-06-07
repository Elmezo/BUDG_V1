package com.example.budg_v2;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.dao.GlossaryXGlossaryDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.GlossaryXGlossary;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.UserContextUtil;
import com.example.budg_v2.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@WebServlet("/api/glossary-x-glossary/*")
public class GlossaryXGlossaryServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(GlossaryXGlossaryServlet.class);
    private static final int GLOSSARY_FACET_TYPE = 12;
    
    private GlossaryXGlossaryDAO dao = new GlossaryXGlossaryDAO();
    private FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private SegmentValidationService segmentValidationService = new SegmentValidationService();
    @SuppressWarnings("unused")
    private Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        String pathInfo = req.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // Get all relationships
                List<Map<String, Object>> relationships = dao.getAllRelationships();
                JsonUtil.sendJsonResponse(resp.getWriter(), relationships);
                
            } else if (pathInfo.startsWith("/source/")) {
                // Get relationships by source glossary ID
                String sourceIdStr = pathInfo.substring(8);
                try {
                    int sourceId = Integer.parseInt(sourceIdStr);
                    
                    // Check if viewing changes - use cloned glossary ID like Impact tab
                    String view = req.getParameter("view");
                    int sourceIdToLoad = sourceId; // Default to original ID
                    
                    if (view != null && "changes".equals(view.trim())) {
                        // Like Impact tab: use cloned glossary ID for view=changes
                        try {
                            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_TYPE, sourceId);
                            if (activeCrId != null) {
                                Integer nobjectId = facetChangesDAO.getNObjectId("glossary", sourceId, "summary", activeCrId);
                                if (nobjectId == null) {
                                    // Clone glossary if needed
                                    try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                                        nobjectId = cloneGlossaryRow(sourceId);
                                        if (nobjectId != null) {
                                            facetChangesDAO.saveMapping("glossary", sourceId, nobjectId, "summary", activeCrId);
                                            logger.info("Cloned glossary {} to {} for relationships view=changes", sourceId, nobjectId);
                                        }
                                    }
                                }
                                if (nobjectId != null) {
                                    sourceIdToLoad = nobjectId;
                                    logger.info("Using cloned glossary ID {} (original: {}) for relationships view=changes", nobjectId, sourceId);
                                }
                            }
                        } catch (SQLException e) {
                            logger.error("Error getting cloned glossary for view=changes: {}", e.getMessage());
                        }
                    }
                    
                    // Load relationships using sourceIdToLoad (cloned if view=changes, original otherwise)
                    List<Map<String, Object>> relationships = dao.getRelationshipsBySourceId(sourceIdToLoad);
                    JsonUtil.sendJsonResponse(resp.getWriter(), relationships);
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid source ID format", 400);
                }
                
            } else if (pathInfo.startsWith("/target/")) {
                // Get relationships by target glossary ID
                String targetIdStr = pathInfo.substring(8);
                try {
                    int targetId = Integer.parseInt(targetIdStr);
                    List<Map<String, Object>> relationships = dao.getRelationshipsByTargetId(targetId);
                    JsonUtil.sendJsonResponse(resp.getWriter(), relationships);
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid target ID format", 400);
                }
                
            } else if (pathInfo.startsWith("/bidirectional/")) {
                // Get bidirectional relationships (both as source and target)
                String glossaryIdStr = pathInfo.substring(15);
                try {
                    int glossaryId = Integer.parseInt(glossaryIdStr);
                    
                    // Check if viewing changes - use cloned glossary ID like Impact tab
                    String view = req.getParameter("view");
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
                                            logger.info("Cloned glossary {} to {} for bidirectional relationships view=changes", glossaryId, nobjectId);
                                        }
                                    }
                                }
                                if (nobjectId != null) {
                                    glossaryIdToLoad = nobjectId;
                                    logger.info("Using cloned glossary ID {} (original: {}) for bidirectional relationships view=changes", nobjectId, glossaryId);
                                }
                            }
                        } catch (SQLException e) {
                            logger.error("Error getting cloned glossary for view=changes: {}", e.getMessage());
                        }
                    }
                    
                    // Load bidirectional relationships using glossaryIdToLoad (cloned if view=changes, original otherwise)
                    List<Map<String, Object>> relationships = dao.getBidirectionalRelationships(glossaryIdToLoad);
                    JsonUtil.sendJsonResponse(resp.getWriter(), relationships);
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid glossary ID format", 400);
                }
                
            } else if (pathInfo.startsWith("/relation-types")) {
                // Get all relation types
                List<Map<String, Object>> relationTypes = dao.getRelationTypes();
                JsonUtil.sendJsonResponse(resp.getWriter(), relationTypes);
                
            } else {
                // Get relationship by ID
                String idStr = pathInfo.substring(1);
                try {
                    int id = Integer.parseInt(idStr);
                    GlossaryXGlossary relationship = dao.getById(id);
                    if (relationship != null) {
                        JsonUtil.sendJsonResponse(resp.getWriter(), relationship);
                    } else {
                        JsonUtil.sendErrorResponse(resp.getWriter(), "Relationship not found", 404);
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
        
        try {
            JsonObject body = JsonParser.parseReader(req.getReader()).getAsJsonObject();
            
            // Validate required fields
            if (!body.has("sourceGlossaryId") || !body.has("targetGlossaryId") || !body.has("relationType")) {
                JsonUtil.sendErrorResponse(resp.getWriter(), 
                    "Missing required fields: sourceGlossaryId, targetGlossaryId, relationType", 400);
                return;
            }
            
            int sourceGlossaryId = body.get("sourceGlossaryId").getAsInt();
            int targetGlossaryId = body.get("targetGlossaryId").getAsInt();
            int relationType = body.get("relationType").getAsInt();
            
            // Prevent self-relationship
            if (sourceGlossaryId == targetGlossaryId) {
                JsonUtil.sendErrorResponse(resp.getWriter(),
                        "This relationship is not allowed: a glossary cannot relate to itself.", 400);
                return;
            }
            
            // Validate cross-segment relationship
            try {
                var validationResult = segmentValidationService.validateCrossSegmentRelationship(
                    sourceGlossaryId, "Glossary", targetGlossaryId, "Glossary");
                if (!validationResult.isValid) {
                    JsonUtil.sendErrorResponse(resp.getWriter(), validationResult.message, 400);
                    return;
                }
            } catch (SQLException e) {
                logger.error("Error validating cross-segment relationship: {}", e.getMessage());
                JsonUtil.sendErrorResponse(resp.getWriter(), 
                    "Error validating relationship: " + e.getMessage(), 500);
                return;
            }
            
            // Check if relationship already exists
            if (dao.relationshipExists(sourceGlossaryId, targetGlossaryId, relationType)) {
                JsonUtil.sendErrorResponse(resp.getWriter(), 
                    "Relationship already exists", 409);
                return;
            }
            
            // Get user ID from session
            int userId = 1;
            if (req.getSession(false) != null && req.getSession().getAttribute("userId") != null) {
                userId = (Integer) req.getSession().getAttribute("userId");
            }
            int authUserId = UserContextUtil.getCurrentUserId(req);
            if (authUserId > 0) {
                userId = authUserId;
            }

            if (userId > 0) {
                try {
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
                    new DFCRService().ensureEditAutoCrIfMissing("Glossary", GLOSSARY_FACET_TYPE, sourceGlossaryId, null, userId, isAdmin);
                } catch (Exception e) {
                    logger.warn("[GlossaryXGlossary] DFCR ensure before relationship create: {}", e.getMessage());
                }
            }
            
            // Check for active CR and get cloned glossary ID (like Impact tab)
            Integer activeCrId = null;
            int sourceGlossaryIdToUse = sourceGlossaryId; // Default to original ID
            
            try {
                activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_TYPE, sourceGlossaryId);
                if (activeCrId != null) {
                    logger.info("Active CR {} found for glossary {}. Creating relationship as pending change.", activeCrId, sourceGlossaryId);
                    
                    // Get or create cloned glossary (like Impact tab does with cloned process)
                    try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                        Integer nobjectId = facetChangesDAO.getNObjectId("glossary", sourceGlossaryId, "summary", activeCrId);
                        
                        if (nobjectId == null) {
                            // No summary mapping exists - clone the glossary
                            nobjectId = cloneGlossaryRow(sourceGlossaryId);
                            if (nobjectId != null) {
                                facetChangesDAO.saveMapping("glossary", sourceGlossaryId, nobjectId, "summary", activeCrId);
                                logger.info("Cloned glossary {} to {} for relationships pending changes", sourceGlossaryId, nobjectId);
                            }
                        }
                        
                        if (nobjectId != null) {
                            sourceGlossaryIdToUse = nobjectId;
                            logger.info("Using cloned glossary ID {} (original: {}) for relationship", nobjectId, sourceGlossaryId);
                        } else {
                            logger.warn("Could not get/create cloned glossary for relationship, using original ID {}", sourceGlossaryId);
                        }
                    } catch (SQLException e) {
                        logger.error("Error getting/cloning glossary for relationship: {}", e.getMessage());
                        // Continue with original ID
                    }
                }
            } catch (SQLException e) {
                logger.warn("Error checking for active CR: {}", e.getMessage());
            }
            
            GlossaryXGlossary relationship = new GlossaryXGlossary();
            relationship.setSourceGlossaryId(sourceGlossaryIdToUse); // Use cloned ID if CR active
            relationship.setTargetGlossaryId(targetGlossaryId);
            relationship.setRelationType(relationType);
            relationship.setCreateDatetime(LocalDateTime.now());
            relationship.setLastUpdateDatetime(LocalDateTime.now());
            relationship.setLastUpdateUserId(userId);
            
            // Create the relationship row
            int newId = dao.create(relationship);
            
            if (activeCrId != null) {
                // Create mapping for this relationship
                try {
                    String areaKey = "relationships#glossary_x_glossary";
                    facetChangesDAO.saveMapping("glossary", sourceGlossaryId, newId, areaKey, activeCrId);
                    
                    JsonObject response = new JsonObject();
                    response.addProperty("id", newId);
                    response.addProperty("pending", true);
                    response.addProperty("changeRequestId", activeCrId);
                    response.addProperty("message", "Relationship change saved as pending (CR is active)");
                    
                    JsonUtil.sendSuccessResponse(resp.getWriter(), "Relationship change saved as pending", response);
                    return;
                } catch (SQLException e) {
                    logger.error("Error saving pending relationship mapping: {}", e.getMessage());
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Error saving pending relationship: " + e.getMessage(), 500);
                    return;
                }
            }
            
            // No active CR - save completed normally; write audit history rows
            dao.logRelationshipAdded(sourceGlossaryId, targetGlossaryId, relationType, userId);

            JsonObject response = new JsonObject();
            response.addProperty("id", newId);
            response.addProperty("pending", false);
            response.addProperty("message", "Glossary relationship created successfully");
            
            JsonUtil.sendSuccessResponse(resp.getWriter(), "Glossary relationship created successfully", response);
            
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error creating relationship: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "ID is required for update", 400);
            return;
        }
        
        try {
            String idStr = pathInfo.substring(1);
            int id = Integer.parseInt(idStr);
            
            JsonObject body = JsonParser.parseReader(req.getReader()).getAsJsonObject();
            
            // Get existing relationship
            GlossaryXGlossary existingRelationship = dao.getById(id);
            if (existingRelationship == null) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Relationship not found", 404);
                return;
            }
            
            // Update fields if provided
            if (body.has("sourceGlossaryId")) {
                existingRelationship.setSourceGlossaryId(body.get("sourceGlossaryId").getAsInt());
            }
            if (body.has("targetGlossaryId")) {
                existingRelationship.setTargetGlossaryId(body.get("targetGlossaryId").getAsInt());
            }
            if (body.has("relationType")) {
                existingRelationship.setRelationType(body.get("relationType").getAsInt());
            }
            if (body.has("lastUpdateUserId")) {
                existingRelationship.setLastUpdateUserId(body.get("lastUpdateUserId").getAsInt());
            }
            
            // Prevent self-relationship on update
            if (existingRelationship.getSourceGlossaryId() == existingRelationship.getTargetGlossaryId()) {
                JsonUtil.sendErrorResponse(resp.getWriter(),
                        "Update would create an invalid relationship: a glossary cannot relate to itself.", 400);
                return;
            }

            int sourceGlossaryIdForCr = existingRelationship.getSourceGlossaryId();
            try {
                Integer originalSource = facetChangesDAO.getOriginalObjectId("glossary", sourceGlossaryIdForCr);
                if (originalSource != null) {
                    sourceGlossaryIdForCr = originalSource;
                }
            } catch (SQLException e) {
                logger.warn("Error resolving original glossary for CR on update: {}", e.getMessage());
            }
            int userIdPut = UserContextUtil.getCurrentUserId(req);
            if (userIdPut > 0) {
                try {
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
                    new DFCRService().ensureEditAutoCrIfMissing("Glossary", GLOSSARY_FACET_TYPE, sourceGlossaryIdForCr, null, userIdPut, isAdmin);
                } catch (Exception e) {
                    logger.warn("[GlossaryXGlossary] DFCR ensure before relationship update: {}", e.getMessage());
                }
            }
            
            existingRelationship.setLastUpdateDatetime(LocalDateTime.now());

            // Snapshot the existing relationship for the audit diff before applying the update
            GlossaryXGlossary previous = dao.getById(id);

            boolean updated = dao.update(existingRelationship);
            if (updated) {
                if (previous != null) {
                    dao.logRelationshipUpdated(sourceGlossaryIdForCr,
                            previous.getSourceGlossaryId(), existingRelationship.getSourceGlossaryId(),
                            previous.getTargetGlossaryId(), existingRelationship.getTargetGlossaryId(),
                            previous.getRelationType(), existingRelationship.getRelationType(),
                            userIdPut);
                }
                JsonObject response = new JsonObject();
                response.addProperty("message", "Glossary relationship updated successfully");
                JsonUtil.sendSuccessResponse(resp.getWriter(), "Glossary relationship updated successfully", response);
            } else {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Failed to update relationship", 500);
            }
            
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error updating relationship: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "ID is required for deletion", 400);
            return;
        }
        
        try {
            String idStr = pathInfo.substring(1);
            int id = Integer.parseInt(idStr);
            
            // Get the relationship to check source glossary ID
            GlossaryXGlossary relationship = dao.getById(id);
            if (relationship == null) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Relationship not found", 404);
                return;
            }

            int sourceGlossaryIdForCr = relationship.getSourceGlossaryId();
            try {
                Integer originalSource = facetChangesDAO.getOriginalObjectId("glossary", sourceGlossaryIdForCr);
                if (originalSource != null) {
                    sourceGlossaryIdForCr = originalSource;
                }
            } catch (SQLException e) {
                logger.warn("Error resolving original glossary for CR: {}", e.getMessage());
            }

            int userId = UserContextUtil.getCurrentUserId(req);
            if (userId > 0) {
                try {
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
                    new DFCRService().ensureEditAutoCrIfMissing("Glossary", GLOSSARY_FACET_TYPE, sourceGlossaryIdForCr, null, userId, isAdmin);
                } catch (Exception e) {
                    logger.warn("[GlossaryXGlossary] DFCR ensure before relationship delete: {}", e.getMessage());
                }
            }
            
            // Check if source glossary has an active automatic CR (pending changes only work with automatic CRs)
            Integer activeCrId = null;
            try {
                activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_TYPE, sourceGlossaryIdForCr);
            } catch (SQLException e) {
                logger.warn("Error checking for active CR: {}", e.getMessage());
            }
            
            if (activeCrId != null) {
                // Active CR - check if this is a cloned relationship (pending change)
                // If so, just delete it. If it's original, we might want to mark it for deletion
                // For now, we'll delete it directly if it's in the mapping
                try {
                    String areaKey = "relationships#glossary_x_glossary";
                    Integer mappedId = facetChangesDAO.getNObjectId("glossary", sourceGlossaryIdForCr, areaKey, activeCrId);
                    
                    if (mappedId != null && mappedId == id) {
                        // This is a pending relationship - delete it
                        boolean deleted = dao.delete(id);
                        if (deleted) {
                            // Remove from mapping (would need a method to delete specific mapping)
                            JsonObject response = new JsonObject();
                            response.addProperty("pending", true);
                            response.addProperty("changeRequestId", activeCrId);
                            response.addProperty("message", "Pending relationship deleted");
                            JsonUtil.sendSuccessResponse(resp.getWriter(), "Pending relationship deleted", response);
                            return;
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error checking mapping: {}", e.getMessage());
                }
            }
            
            // No active CR or not a pending relationship - delete directly from database
            boolean deleted = dao.delete(id);
            if (deleted) {
                dao.logRelationshipDeleted(relationship.getSourceGlossaryId(),
                        relationship.getTargetGlossaryId(),
                        relationship.getRelationType(),
                        userId);
                JsonObject response = new JsonObject();
                response.addProperty("pending", false);
                response.addProperty("message", "Glossary relationship deleted successfully");
                JsonUtil.sendSuccessResponse(resp.getWriter(), "Glossary relationship deleted successfully", response);
            } else {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Could not delete relationship", 500);
            }
            
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error deleting relationship: " + e.getMessage(), 500);
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
    
    /**
     * Get a single relationship with all names (targetGlossaryName, relationTypeName, targetGlossaryType)
     * This is used for pending relationships to ensure proper display
     */
    @SuppressWarnings("unused")
    private Map<String, Object> getRelationshipWithNames(int relationshipId) throws SQLException {
        String sql = "SELECT gxg.ID, gxg.SourceGlossaryID, gxg.TargetGlossaryID, gxg.RelationType, " +
                "gxg.CreateDatetime, gxg.LastUpdateDatetime, gxg.LastUpdateUser_ID, " +
                "rt.PrimaryName AS relationTypeName, " +
                "tg.Name AS targetGlossaryName, " +
                "gt.Name AS targetGlossaryType " +
                "FROM glossary_x_glossary gxg " +
                "LEFT JOIN glossary_x_glossary_reltype rt ON rt.ID = gxg.RelationType " +
                "LEFT JOIN glossary tg ON tg.ID = gxg.TargetGlossaryID " +
                "LEFT JOIN glossary_type gt ON gt.ID = tg.Type " +
                "WHERE gxg.ID = ?";
        
        try (java.sql.Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationshipId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", rs.getInt("ID"));
                map.put("sourceGlossaryId", rs.getInt("SourceGlossaryID"));
                map.put("targetGlossaryId", rs.getInt("TargetGlossaryID"));
                map.put("relationType", rs.getInt("RelationType"));
                map.put("relationTypeName", rs.getString("relationTypeName"));
                map.put("targetGlossaryName", rs.getString("targetGlossaryName"));
                map.put("targetGlossaryType", rs.getString("targetGlossaryType"));
                map.put("createDatetime", rs.getTimestamp("CreateDatetime"));
                map.put("lastUpdateDatetime", rs.getTimestamp("LastUpdateDatetime"));
                map.put("lastUpdateUserId", rs.getInt("LastUpdateUser_ID"));
                return map;
            }
        }
    }
}
