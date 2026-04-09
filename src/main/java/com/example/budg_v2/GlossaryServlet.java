package com.example.budg_v2;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.dao.GlossaryDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet(name = "GlossaryServlet", urlPatterns = {"/api/glossary/*", "/api/view/glossary/*", "/api/create/glossary/*"})
public class GlossaryServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(GlossaryServlet.class);
    private final GlossaryDAO glossaryDAO = new GlossaryDAO();
    private final SegmentDAO segmentDAO = new SegmentDAO();
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private final DFCRService dfcrService = new DFCRService();
    private final Gson gson = new Gson();
    
    // Facet ID for Glossary in module table
    private static final int GLOSSARY_FACET_ID = 12;


    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing glossary id");
            return;
        }

        String idSegment = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] parts = idSegment.split("/");
        try {
            int id = Integer.parseInt(parts[0]);
            
            // Check if this is a strategic-source update
            if (parts.length > 1 && "strategic-source".equals(parts[1])) {
                JsonObject body = JsonUtil.parseJsonFromRequest(req.getReader());
                JsonArray strategicSourceData = body.getAsJsonArray("strategicSourceData");
                
                List<Map<String, Object>> dataList = new ArrayList<>();
                if (strategicSourceData != null) {
                    for (int i = 0; i < strategicSourceData.size(); i++) {
                        JsonObject item = strategicSourceData.get(i).getAsJsonObject();
                        Map<String, Object> data = new HashMap<>();
                        data.put("systemId", JsonUtil.getJsonInt(item, "systemId"));
                        data.put("datasetId", JsonUtil.getJsonInt(item, "datasetId"));
                        data.put("relationTypeId", JsonUtil.getJsonInt(item, "relationTypeId"));
                        dataList.add(data);
                    }
                }
                
                boolean success = glossaryDAO.updateStrategicSource(id, dataList);
                if (success) {
                    resp.getWriter().write("{\"success\": true}");
                } else {
                    sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update strategic source");
                }
                return;
            }
            
            JsonObject body = JsonUtil.parseJsonFromRequest(req.getReader());

            String name = JsonUtil.getJsonString(body, "name");
            String description = JsonUtil.getJsonString(body, "description");
            String format = JsonUtil.getJsonString(body, "format");
            String ldm = JsonUtil.getJsonString(body, "ldm");
            String businessLogic = JsonUtil.getJsonString(body, "business_logic");
            String examples = JsonUtil.getJsonString(body, "examples");
            String refNumber = JsonUtil.getJsonString(body, "ref_number");
            Integer formatType = JsonUtil.getJsonInt(body, "format_type");
            Integer parentId = JsonUtil.getJsonInt(body, "parent_id");
            
            // Get additional fields
            Integer status = JsonUtil.getJsonInt(body, "status");
            Integer lifecycle = JsonUtil.getJsonInt(body, "lifecycle");
            Integer isPublic = JsonUtil.getJsonInt(body, "is_public");
            Integer type = JsonUtil.getJsonInt(body, "type");
            Integer securityClassification = JsonUtil.getJsonInt(body, "security_classification");
            Integer kde = JsonUtil.getJsonInt(body, "kde");
            Integer confidentialityRating = JsonUtil.getJsonInt(body, "confidentiality_rating");
            Integer integrityRating = JsonUtil.getJsonInt(body, "integrity_rating");
            Integer availabilityRating = JsonUtil.getJsonInt(body, "availability_rating");

            if (name == null || name.trim().isEmpty() || description == null || description.trim().isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Name and Description are required");
                return;
            }

            if (parentId != null) {
                String validationError = validateParentRelationship(id, parentId);
                if (validationError != null) {
                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST, validationError);
                    return;
                }
            }

            List<String> aliases = new ArrayList<>();
            if (body.has("aliases") && body.get("aliases").isJsonArray()) {
                JsonArray aliasArray = body.getAsJsonArray("aliases");
                for (int i = 0; i < aliasArray.size(); i++) {
                    aliases.add(aliasArray.get(i).getAsString());
                }
            }

            // Check role-based edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(req, resp, "Glossary", id)) {
                return; // Response already sent
            }

            // Get current user ID from request (like DatasetServlet does)
            int currentUserId = com.example.budg_v2.util.UserContextUtil.getCurrentUserId(req);
            if (currentUserId <= 0) {
                sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "User authentication required");
                return;
            }
            Integer userId = currentUserId;

            // Check segment-based edit permission
            if (userId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(userId, id, "Glossary");
                if (!canEdit) {
                    sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Access denied. You don't have permission to edit this glossary.");
                    return;
                }
            }

            // Check if this object has an active auto-created CR (only automatic CRs use pending changes)
            // If so, clone the row and update the clone instead of updating directly
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_ID, id);
            //system.out.println("[GlossaryServlet UPDATE] Checking for active CR for glossary " + id + " (facetId=" + GLOSSARY_FACET_ID + ")");
            //system.out.println("[GlossaryServlet UPDATE] Active CR result: " + activeCrId);
            
            // Check if we need to create a new CR for this user
            boolean shouldCheckForNewCR = true;
            if (activeCrId != null) {
                // Check if the existing CR was created by the current user
                try (Connection conn = DatabaseConnection.getConnection()) {
                    String checkSql = "SELECT Created_By FROM changerequest WHERE ID = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                        stmt.setInt(1, activeCrId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                int crCreatedBy = rs.getInt("Created_By");
                                if (crCreatedBy == userId) {
                                    // Same user - use existing CR, don't create new one
                                    //system.out.println("[GlossaryServlet UPDATE] Active CR " + activeCrId + " belongs to current user " + userId + ", using existing CR");
                                    shouldCheckForNewCR = false;
                                } else {
                                    // Different user - need to create new CR
                                    //system.out.println("[GlossaryServlet UPDATE] Active CR " + activeCrId + " belongs to different user " + crCreatedBy + " (current: " + userId + "), will create new CR");
                                    activeCrId = null; // Reset to allow new CR creation
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("[GlossaryServlet UPDATE] Error checking CR owner: " + e.getMessage());
                    // Continue with check
                }
            }
            
            // If no CR exists OR existing CR belongs to different user, check if DFCR edit workflow is enabled and auto-create CR
            if (shouldCheckForNewCR) {
                try {
                    //system.out.println("[GlossaryServlet UPDATE] Checking DFCR settings for new CR...");
                    // Check if user is admin for bypass logic
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
                    //system.out.println("[GlossaryServlet UPDATE] User isAdmin: " + isAdmin);
                    
                    // Get glossary type for type-specific workflow settings
                    Integer glossaryType = type; // Use the type from the form submission
                    
                    Integer autoCrId = dfcrService.applyDefaultsOnEdit("Glossary", id, glossaryType, userId, isAdmin);
                    if (autoCrId != null) {
                        //system.out.println("[GlossaryServlet UPDATE] DFCR auto-created CR: " + autoCrId);
                        activeCrId = autoCrId;
                    } else {
                        //system.out.println("[GlossaryServlet UPDATE] DFCR did not create CR (workflow not enabled or admin bypass)");
                    }
                } catch (Exception e) {
                    System.err.println("[GlossaryServlet UPDATE] Error checking/creating DFCR CR: " + e.getMessage());
                    e.printStackTrace();
                    // Continue without CR - will update directly
                }
            }
            
            if (activeCrId != null) {
                // Check CR status - if it's Pending Start or Running, prevent editing
                try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                    String statusSql = "SELECT crs.PrimaryName FROM changerequest cr " +
                                      "LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID " +
                                      "WHERE cr.ID = ?";
                    try (java.sql.PreparedStatement statusStmt = conn.prepareStatement(statusSql)) {
                        statusStmt.setInt(1, activeCrId);
                        try (java.sql.ResultSet statusRs = statusStmt.executeQuery()) {
                            if (statusRs.next()) {
                                String statusName = statusRs.getString("PrimaryName");
                                if (statusName != null) {
                                    String statusLower = statusName.toLowerCase();
                                    // Only prevent editing if CR is Running or In Progress (workflow has started)
                                    // Allow editing if CR is Pending Start (workflow hasn't started yet)
                                    if (statusLower.contains("running") || statusLower.contains("in progress")) {
                                        sendError(resp, HttpServletResponse.SC_FORBIDDEN, 
                                            "Cannot edit this object. There is an active Change Request (status: " + statusName + 
                                            ") that must be completed or cancelled before editing is allowed.");
                                        return;
                                    }
                                    // If status is "Pending Start", allow editing (workflow hasn't started yet)
                                    logger.info("Glossary {} edit allowed - CR {} is in Pending Start status, workflow hasn't started yet", id, activeCrId);
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error checking CR status: {}", e.getMessage());
                    // Continue - don't block if we can't check status
                }
                
                // Object is under revision - clone row and update clone
                //system.out.println("[GlossaryServlet] Object " + id + " has active CR " + activeCrId + " - cloning and updating");
                
                try {
                    // Get or create mapping for 'summary' area
                    Integer nobjectId = facetChangesDAO.getNObjectId("glossary", id, "summary", activeCrId);
                    //system.out.println("[GlossaryServlet] Retrieved nobjectId: " + nobjectId);
                    
                    if (nobjectId == null) {
                        // First edit - clone the row
                        //system.out.println("[GlossaryServlet] Cloning glossary row for ID: " + id);
                        nobjectId = cloneGlossaryRow(id);
                        if (nobjectId == null) {
                            System.err.println("[GlossaryServlet] Failed to clone glossary row for ID: " + id);
                            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to clone glossary row");
                            return;
                        }
                        //system.out.println("[GlossaryServlet] Cloned glossary row, new ID: " + nobjectId);
                        // Create mapping
                        facetChangesDAO.saveMapping("glossary", id, nobjectId, "summary", activeCrId);
                        //system.out.println("[GlossaryServlet] Created mapping: object_id=" + id + ", nobject_id=" + nobjectId + ", area_key=summary");
                    }
                    
                    // Update the cloned row
                    //system.out.println("[GlossaryServlet] Updating cloned glossary row with ID: " + nobjectId);
                    boolean success = glossaryDAO.update(nobjectId, name, description, format, ldm, businessLogic,
                            examples, refNumber, formatType, parentId, aliases, status, lifecycle, isPublic,
                            type, securityClassification, kde, confidentialityRating, integrityRating, availabilityRating, userId);
                    
                    if (!success) {
                        System.err.println("[GlossaryServlet] Failed to update cloned glossary row with ID: " + nobjectId);
                        sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update cloned glossary row");
                        return;
                    }
                    
                    //system.out.println("[GlossaryServlet] Successfully updated cloned glossary row");
                    // Return success but indicate changes are pending
                    JsonObject response = new JsonObject();
                    response.addProperty("success", true);
                    response.addProperty("id", id);
                    response.addProperty("message", "Changes saved as pending. They will apply when the Change Request is completed.");
                    response.addProperty("pendingChanges", true);
                    response.addProperty("changeRequestId", activeCrId);
                    resp.getWriter().write(gson.toJson(response));
                } catch (SQLException e) {
                    System.err.println("[GlossaryServlet] SQLException handling pending changes: " + e.getMessage());
                    e.printStackTrace();
                    sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("[GlossaryServlet] Exception handling pending changes: " + e.getMessage());
                    e.printStackTrace();
                    sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
                }
            } else {
                // No active CR - apply changes directly to database
                boolean success = glossaryDAO.update(id, name, description, format, ldm, businessLogic,
                        examples, refNumber, formatType, parentId, aliases, status, lifecycle, isPublic,
                        type, securityClassification, kde, confidentialityRating, integrityRating, availabilityRating, userId);

                if (success) {
                    Integer segmentId = JsonUtil.getJsonInt(body, "segmentId");
                    if (segmentId != null) {
                        try {
                            int currentSegmentId = segmentDAO.getObjectSegmentId(id, "Glossary");
                            if (currentSegmentId != segmentId) {
                                // Validate hierarchy and segment move (includes parent, children, and stakeholder checks)
                                com.example.budg_v2.service.SegmentValidationService validationService = new com.example.budg_v2.service.SegmentValidationService();
                                var validationResult = validationService.validateSegmentMove(id, segmentId, "Glossary", parentId);
                                if (!validationResult.isValid) {
                                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST, validationResult.message);
                                    return;
                                }
                                
                                if (currentSegmentId > 0) {
                                    segmentDAO.removeObjectFromSegment(currentSegmentId, id, "Glossary", userId);
                                }
                                segmentDAO.assignObjectToSegment(segmentId, id, "Glossary", userId);
                                //system.out.println("✅ Glossary " + id + " segment changed from " + currentSegmentId + " to " + segmentId);
                            }
                        } catch (Exception e) {
                            System.err.println("❌ Error updating glossary segment: " + e.getMessage());
                            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error updating glossary segment: " + e.getMessage());
                            return;
                        }
                    }
                    sendSuccess(resp, "Glossary updated successfully");
                } else {
                    sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Glossary not found");
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("[GlossaryServlet] NumberFormatException: " + e.getMessage());
            e.printStackTrace();
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid glossary id: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("[GlossaryServlet] SQLException in doPut: " + e.getMessage());
            e.printStackTrace();
            // Check if it's a REF uniqueness error
            if (e.getMessage() != null && e.getMessage().contains("Reference number must be unique")) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            } else {
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[GlossaryServlet] Unexpected exception in doPut: " + e.getMessage());
            e.printStackTrace();
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        String idSegment = null;
        if (pathInfo != null && !"/".equals(pathInfo)) {
            idSegment = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        } else {
            idSegment = req.getParameter("id");
        }

        if (idSegment == null || idSegment.isBlank()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing glossary id");
            return;
        }

        String[] parts = idSegment.split("/");
        try {
            int id = Integer.parseInt(parts[0]);

            if (parts.length > 1) {
                String action = parts[1].toLowerCase();
                switch (action) {
                    case "hierarchy":
                        try {
                            List<Map<String, Object>> hierarchy = glossaryDAO.getGlossaryHierarchy(id);
                            resp.getWriter().write(gson.toJson(hierarchy));
                        } catch (SQLException e) {
                            System.err.println("[GlossaryServlet] Error getting hierarchy for glossary " + id + ": " + e.getMessage());
                            e.printStackTrace();
                            // Return empty array on error
                            resp.getWriter().write("[]");
                        }
                        return;
                    case "strategic-source":
                        resp.getWriter().write(gson.toJson(glossaryDAO.getGlossaryStrategicSource(id)));
                        return;
                    case "stakeholders":
                        resp.getWriter().write(gson.toJson(glossaryDAO.getDirectStakeholdersForGlossary(id)));
                        return;
                    case "roles":
                        resp.getWriter().write(gson.toJson(glossaryDAO.getRolesForGlossary(id)));
                        return;
                    case "statuses":
                        resp.getWriter().write(gson.toJson(glossaryDAO.getStatusesForGlossary(id)));
                        return;
                    case "users":
                        if (parts.length < 3) {
                            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing role id");
                            return;
                        }
                        int roleId = Integer.parseInt(parts[2]);
                        resp.getWriter().write(gson.toJson(glossaryDAO.getUsersByRole(id, roleId)));
                        return;
                    default:
                        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid endpoint");
                        return;
                }
            }

            // Check if viewing changes (pending changes mode)
            String view = req.getParameter("view");
            int glossaryIdToLoad = id;
            
            if ("changes".equals(view)) {
                try {
                    // Only check for automatic CRs for pending changes view
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(GLOSSARY_FACET_ID, id);
                    if (activeCrId != null) {
                        Integer nobjectId = facetChangesDAO.getNObjectId("glossary", id, "summary", activeCrId);
                        if (nobjectId != null) {
                            glossaryIdToLoad = nobjectId;
                        }
                    }
                } catch (SQLException e) {
                    // If error getting mapping, fall back to original id
                    System.err.println("[GlossaryServlet] Error getting nobject_id for view=changes: " + e.getMessage());
                }
            }
            
            Map<String, Object> data = glossaryDAO.getById(glossaryIdToLoad);
            if (data == null) {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Glossary not found");
                return;
            }
            String statusName = data.get("statusName") != null ? String.valueOf(data.get("statusName")) : null;
            if (!UserContextUtil.isCurrentUserAdmin(req) && "Deleted".equalsIgnoreCase(statusName)) {
                sendError(resp, HttpServletResponse.SC_FORBIDDEN, "This object is not available.");
                return;
            }

            // Check access control for all users (including guests)
            int userId = UserContextUtil.getCurrentUserId(req);
            
            // GUEST ACCESS CHECK: Only allow public glossaries in Enterprise segment
            if (userId <= 0) {
                // Guest user - check if glossary is public and in Enterprise segment
                try {
                    boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "Glossary");
                    if (!canAccess) {
                        sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Access denied. This resource is not publicly accessible.");
                        return;
                    }
                } catch (SQLException e) {
                    // On error, deny access for safety
                    sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Access denied. Unable to verify access permissions.");
                    return;
                }
            }

            // Authenticated user access check
            if (userId > 0) {
                boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "Glossary");
                if (!canAccess) {
                    sendError(resp, HttpServletResponse.SC_FORBIDDEN,
                            "Access denied. You don't have permission to view this glossary.");
                    return;
                }
            }

            try {
                data.put("aliases", glossaryDAO.getGlossaryAliases(id));
            } catch (SQLException aliasError) {
                data.put("aliases", new ArrayList<>());
            }

            // Add segment information
            SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, glossaryIdToLoad, "Glossary");
            SegmentResponseUtil.applySegmentInfo(data, segmentInfo, req);

            resp.getWriter().write(gson.toJson(data));
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid glossary id");
        } catch (SQLException e) {
            if (pathInfo != null && pathInfo.contains("/hierarchy")) {
                resp.getWriter().write("[]");
            } else {
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing glossary id");
            return;
        }

        String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] parts = trimmed.split("/");
        try {
            int id = Integer.parseInt(parts[0]);
            if (parts.length > 1 && "stakeholders".equalsIgnoreCase(parts[1])) {
                StringBuilder jsonBuilder = new StringBuilder();
                String line;
                while ((line = req.getReader().readLine()) != null) {
                    jsonBuilder.append(line);
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> payload = gson.fromJson(jsonBuilder.toString(), Map.class);
                int currentUserId = com.example.budg_v2.util.UserContextUtil.getCurrentUserId(req);
                java.util.Set<Integer> deleteIds = com.example.budg_v2.util.DefaultStakeholderUtil.extractObjectXPeopleIdsFromDeletes(payload);
                try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                    com.example.budg_v2.util.DefaultStakeholderUtil.ValidationResult vr =
                            com.example.budg_v2.util.DefaultStakeholderUtil.validateCurrentUserCanSaveStakeholders(
                                    conn, "Glossary", id, currentUserId, deleteIds);
                    if (!vr.isValid()) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        JsonObject err = new JsonObject();
                        err.addProperty("success", false);
                        err.addProperty("error", vr.getWarningMessage() != null ? vr.getWarningMessage() : "");
                        resp.getWriter().write(err.toString());
                        return;
                    }
                }
                glossaryDAO.saveStakeholdersChanges(id, payload, currentUserId);

                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("message", "Saved successfully");
                resp.getWriter().write(responseJson.toString());
            } else {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid endpoint");
            }
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid glossary id");
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        resp.getWriter().write(obj.toString());
    }

    private void sendSuccess(HttpServletResponse resp, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        obj.addProperty("message", message);
        resp.getWriter().write(obj.toString());
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

    private String validateParentRelationship(int glossaryId, int parentId) {
        try {
            if (glossaryId == parentId) {
                return "Invalid parent selection: A glossary cannot be its own parent.";
            }
            if (isDescendantOf(parentId, glossaryId)) {
                return "Invalid parent selection: This would create a circular relationship.";
            }
            return null;
        } catch (SQLException e) {
            return "Validation failed: Unable to verify parent relationship due to database error.";
        }
    }

    private boolean isDescendantOf(int potentialDescendant, int ancestor) throws SQLException {
        return isDescendantOfRecursive(potentialDescendant, ancestor, new HashSet<>());
    }

    private boolean isDescendantOfRecursive(int currentId, int ancestor, Set<Integer> visited) throws SQLException {
        if (visited.contains(currentId)) {
            return false;
        }
        visited.add(currentId);
        Map<String, Object> current = glossaryDAO.getById(currentId);
        if (current == null) {
            return false;
        }
        Object parentIdObj = current.get("parentId");
        if (!(parentIdObj instanceof Integer)) {
            return false;
        }
        int parentId = (Integer) parentIdObj;
        if (parentId == ancestor) {
            return true;
        }
        return isDescendantOfRecursive(parentId, ancestor, visited);
    }
}