package com.example.budg_v2;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.dao.SystemDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.LockUtil;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.UserContextUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WebServlet(name = "SystemServlet", urlPatterns = {"/api/system/*", "/api/view/system/*", "/api/create/system/*", "/api/system/save", "/api/create/system/save"})
public class SystemServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(SystemServlet.class);
    private static final int SYSTEM_FACET_ID = 13; // System facet ID in module table
    private final SystemDAO systemDAO = new SystemDAO();
    private final DFCRService dfcrService = new DFCRService();
	private final SegmentDAO segmentDAO = new SegmentDAO();
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    // Configure Gson to serialize null values so Type and Classification are always included
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        // Supports:
        //   GET /api/system/{id}                   -> system details
        //   GET /api/system/{id}/stakeholders      -> direct stakeholders list
        //   GET /api/system/{id}/interfaces        -> interfaces list (from system)
        //   GET /api/system/{id}/data-content      -> glossary links (Data Content Summary)
        //   GET /api/system/relation-types         -> data content relation types (TypeID=1)
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing system id\"}");
            return;
        }
        String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        if ("relation-types".equalsIgnoreCase(trimmed)) {
            try {
                var list = systemDAO.listRelationTypesForDataContent();
                resp.getWriter().write(gson.toJson(list));
                return;
            } catch (SQLException e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error\"}");
                return;
            }
        }
        String[] parts = trimmed.split("/");
        String idStr = parts[0];
        try {
            int id = Integer.parseInt(idStr);
            // Check if viewing changes (pending changes mode) - applies to ALL sub-resources too
            String view = req.getParameter("view");
            int systemIdToLoad = id;
            if ("changes".equals(view)) {
                try {
                    // Only check for automatic CRs for pending changes view
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(SYSTEM_FACET_ID, id);
                    if (activeCrId != null) {
                        Integer nobjectId = facetChangesDAO.getNObjectId("system", id, "summary", activeCrId);
                        if (nobjectId != null) {
                            systemIdToLoad = nobjectId;
                        }
                    }
                } catch (SQLException e) {
                    // If error getting mapping, fall back to original id
                    logger.warn("Error getting nobject_id for view=changes: {}", e.getMessage());
                }
            }

            if (parts.length > 1 && "stakeholders".equalsIgnoreCase(parts[1])) {
                var list = systemDAO.getDirectStakeholdersForSystem(systemIdToLoad);
                resp.getWriter().write(gson.toJson(list));
            } else if (parts.length > 1 && "hierarchy".equalsIgnoreCase(parts[1])) {
                var list = systemDAO.getSystemHierarchy(systemIdToLoad);
                int hierarchyUserId = UserContextUtil.getCurrentUserId(req);
                com.example.budg_v2.util.HierarchyAccessMasker.mask(list, "System", hierarchyUserId);
                resp.getWriter().write(gson.toJson(list));
            } else if (parts.length > 1 && "interfaces".equalsIgnoreCase(parts[1])) {
                var list = systemDAO.getInterfacesForSystem(systemIdToLoad);
                resp.getWriter().write(gson.toJson(list));
            } else if (parts.length > 2 && "data-content".equalsIgnoreCase(parts[1]) && "excluded-glossary-ids".equalsIgnoreCase(parts[2])) {
                // Use original system id: strategic source links are for the system being edited, not the clone
                var list = systemDAO.getGlossaryIdsExcludedFromDataContent(id);
                resp.getWriter().write(gson.toJson(list));
            } else if (parts.length > 1 && "data-content".equalsIgnoreCase(parts[1])) {
                var list = systemDAO.getDataContentSummaryForSystem(systemIdToLoad);
                resp.getWriter().write(gson.toJson(list));
            } else if (parts.length > 1 && "roles".equalsIgnoreCase(parts[1])) {
                var list = systemDAO.getRolesForSystem(id);
                resp.getWriter().write(gson.toJson(list));
            } else if (parts.length > 1 && "statuses".equalsIgnoreCase(parts[1])) {
                var list = systemDAO.getStatusesForSystem(id);
                resp.getWriter().write(gson.toJson(list));
            } else if (parts.length > 2 && "users".equalsIgnoreCase(parts[1])) {
                int roleId = Integer.parseInt(parts[2]);
                var list = systemDAO.getUsersByRole(id, roleId);
                resp.getWriter().write(gson.toJson(list));
            } else {
                var result = systemDAO.getSystemDetailsById(systemIdToLoad);
                if (result == null) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write("{\"error\":\"System not found\"}");
                    return;
                }
                String statusName = result.get("statusName") != null ? String.valueOf(result.get("statusName")) : null;
                if (!UserContextUtil.isCurrentUserAdmin(req) && "Deleted".equalsIgnoreCase(statusName)) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.getWriter().write("{\"error\":\"This object is not available.\"}");
                    return;
                }

                // Check access control for all users (including guests)
                int userId = UserContextUtil.getCurrentUserId(req);

                // GUEST ACCESS CHECK: Only allow public objects in Enterprise segment
                if (userId <= 0) {
                    // Guest user - check if object is public and in Enterprise segment
                    try {
                        boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "System");
                        if (!canAccess) {
                            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            resp.getWriter().write("{\"error\":\"Access denied. This resource is not publicly accessible.\"}");
                            return;
                        }
                    } catch (SQLException e) {
                        // On error, deny access for safety
                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        resp.getWriter().write("{\"error\":\"Access denied. Unable to verify access permissions.\"}");
                        return;
                    }
                }

                // Authenticated user access check
                if (userId > 0) {
                    boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "System");
                    if (!canAccess) {
                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        resp.getWriter().write("{\"error\":\"Access denied. You don't have permission to view this system.\"}");
                        return;
                    }
                }

                // Add segment information
                SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, systemIdToLoad, "System");
                SegmentResponseUtil.applySegmentInfo(result, segmentInfo, req);
                resp.getWriter().write(gson.toJson(result));
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid system id\"}");
        } catch (SQLException e) {
            // Return empty array for stakeholders path on SQL error to avoid 500 surfacing in UI
            String pi = req.getPathInfo();
            String tr = pi != null && pi.startsWith("/") ? pi.substring(1) : pi;
            if (tr != null && tr.contains("/stakeholders")) {
                resp.getWriter().write("[]");
                return;
            }
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);
        
        // Check delete permission - Only Admin/Super Admin can delete
        if (!PermissionCheckUtil.checkDeletePermission(req, resp, "System")) {
            return; // Response already sent
        }
        
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing system id\"}");
            return;
        }
        String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] parts = trimmed.split("/");
        String idStr = parts[0];
        try {
            int id = Integer.parseInt(idStr);
            // Guard against FK violations: interfaces pointing to this system
            int linked = systemDAO.countLinkedInterfaces(id);
            if (linked > 0) {
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
                String msg = "Cannot delete this System because there are interfaces linked to it. " +
                        "Please delete those interfaces or reassign them so this System is no longer set as a Source or Target, then try again.";
                resp.getWriter().write('{'+"\"success\":false,"+"\"message\":\""+ msg.replace("\"","\\\"") +"\""+'}');
                return;
            }
            
            // Get username from request
            String userName = getUserName(req);
            
            boolean ok = systemDAO.deleteSystemWithAudit(id, userName);
            if (ok) {
                resp.getWriter().write("{\"success\":true}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"System not found\"}");
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid system id\"}");
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        String servletPath = req.getServletPath();

        // Check if this is a save request (new system creation)
        // Handle both exact mapping (/api/system/save) and wildcard mapping (/api/system/* with pathInfo=/save)
        if ((servletPath != null && servletPath.endsWith("/save")) || (pathInfo != null && "/save".equals(pathInfo))) {
            // Check create permission for new system creation
            if (!PermissionCheckUtil.checkCreatePermission(req, resp, "System")) {
                return; // Response already sent
            }
            handleSystemSave(req, resp);
            return;
        }

        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing system id\"}");
            return;
        }

        String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] parts = trimmed.split("/");
        String idStr = parts[0];

        try {
            int id = Integer.parseInt(idStr);

            if (parts.length > 1 && "stakeholders".equalsIgnoreCase(parts[1])) {
                // Accept payload: { inserts: [...], updates: [...], deletes: [...] }
                StringBuilder jsonBuilder = new StringBuilder();
                String line;
                while ((line = req.getReader().readLine()) != null) {
                    jsonBuilder.append(line);
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> payload = gson.fromJson(jsonBuilder.toString(), Map.class);

                // Basic validation
                if (payload == null) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"success\":false,\"error\":\"Invalid JSON payload\"}");
                    return;
                }

                try {
                    int currentUserId = com.example.budg_v2.util.UserContextUtil.getCurrentUserId(req);
                    java.util.Set<Integer> deleteIds = com.example.budg_v2.util.DefaultStakeholderUtil.extractObjectXPeopleIdsFromDeletes(payload);
                    try (Connection conn = DatabaseConnection.getConnection()) {
                        com.example.budg_v2.util.DefaultStakeholderUtil.ValidationResult vr =
                                com.example.budg_v2.util.DefaultStakeholderUtil.validateCurrentUserCanSaveStakeholders(
                                        conn, "System", id, currentUserId, deleteIds);
                        if (!vr.isValid()) {
                            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            resp.getWriter().write("{\"success\":false,\"error\":\"" + (vr.getWarningMessage() != null ? vr.getWarningMessage().replace("\"", "\\\"") : "") + "\"}");
                            return;
                        }
                    }
                    systemDAO.saveStakeholdersChanges(id, payload, currentUserId);
                    resp.getWriter().write("{\"success\":true,\"message\":\"Saved successfully\"}");
                } catch (IllegalArgumentException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"success\":false,\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
                } catch (SQLException e) {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.getWriter().write("{\"success\":false,\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Database error") + "\"}");
                }
            } else if (parts.length > 1 && "data-content".equalsIgnoreCase(parts[1])) {
                // Save Data Content rows (insert/update/delete)
                // Check if system has active auto CR - if so, save to cloned system and track in system_changes
                Integer activeCrId = null;
                int systemIdToUse = id; // Default to original ID
                
                try {
                    activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(SYSTEM_FACET_ID, id);
                    if (activeCrId != null) {
                        logger.info("Active CR {} found for system {}. Saving data content as pending change.", activeCrId, id);
                        
                        // Get or create cloned system (like Impact tab does with cloned process)
                        try (Connection conn = DatabaseConnection.getConnection()) {
                            Integer nobjectId = facetChangesDAO.getNObjectId("system", id, "summary", activeCrId);
                            
                            if (nobjectId == null) {
                                // No summary mapping exists - clone the system
                                nobjectId = cloneSystemRow(id);
                                if (nobjectId != null) {
                                    facetChangesDAO.saveMapping("system", id, nobjectId, "summary", activeCrId);
                                    logger.info("Cloned system {} to {} for data content pending changes", id, nobjectId);
                                }
                            }
                            
                            if (nobjectId != null) {
                                systemIdToUse = nobjectId;
                                logger.info("Using cloned system ID {} (original: {}) for data content", nobjectId, id);
                            } else {
                                logger.warn("Could not get/create cloned system for data content, using original ID {}", id);
                            }
                        } catch (SQLException e) {
                            logger.error("Error getting/cloning system for data content: {}", e.getMessage());
                            // Continue with original ID
                        }
                    } else {
                        logger.debug("No active CR found for system {}", id);
                    }
                } catch (SQLException e) {
                    logger.error("Error checking for active CR for system {}: {}", id, e.getMessage());
                    // Continue with original ID
                }

                StringBuilder jsonBuilder = new StringBuilder();
                String line2;
                while ((line2 = req.getReader().readLine()) != null) jsonBuilder.append(line2);
                com.google.gson.JsonObject body = com.google.gson.JsonParser.parseString(jsonBuilder.toString()).getAsJsonObject();

                java.util.List<java.util.Map<String, Object>> inserts = new java.util.ArrayList<>();
                java.util.List<java.util.Map<String, Object>> updates = new java.util.ArrayList<>();
                java.util.List<Integer> deletes = new java.util.ArrayList<>();

                if (body.has("inserts") && body.get("inserts").isJsonArray()) {
                    var arr = body.getAsJsonArray("inserts");
                    for (var el : arr) {
                        var o = el.getAsJsonObject();
                        java.util.Map<String, Object> m = new java.util.HashMap<>();
                        if (o.has("glossaryId") && !o.get("glossaryId").isJsonNull()) m.put("glossaryId", o.get("glossaryId").getAsInt());
                        if (o.has("relationTypeId") && !o.get("relationTypeId").isJsonNull()) m.put("relationTypeId", o.get("relationTypeId").getAsInt());
                        inserts.add(m);
                    }
                }
                if (body.has("updates") && body.get("updates").isJsonArray()) {
                    var arr = body.getAsJsonArray("updates");
                    for (var el : arr) {
                        var o = el.getAsJsonObject();
                        java.util.Map<String, Object> m = new java.util.HashMap<>();
                        if (o.has("id") && !o.get("id").isJsonNull()) m.put("id", o.get("id").getAsInt());
                        if (o.has("glossaryId") && !o.get("glossaryId").isJsonNull()) m.put("glossaryId", o.get("glossaryId").getAsInt());
                        if (o.has("relationTypeId") && !o.get("relationTypeId").isJsonNull()) m.put("relationTypeId", o.get("relationTypeId").getAsInt());
                        updates.add(m);
                    }
                }
                if (body.has("deletes") && body.get("deletes").isJsonArray()) {
                    var arr = body.getAsJsonArray("deletes");
                    for (var el : arr) {
                        deletes.add(el.getAsInt());
                    }
                }

                // Segment validation: reject inserts/updates where glossary is in a different private segment
                for (java.util.Map<String, Object> ins : inserts) {
                    Object gidObj = ins.get("glossaryId");
                    if (gidObj != null) {
                        int gid = gidObj instanceof Number ? ((Number) gidObj).intValue() : Integer.parseInt(gidObj.toString());
                        if (gid > 0) {
                            var segValResult = segmentValidationService.validateCrossSegmentRelationship(id, "System", gid, "Glossary");
                            if (!segValResult.isValid) {
                                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                resp.getWriter().write("{\"error\":\"" + segValResult.message.replace("\"", "\\\"") + "\"}");
                                return;
                            }
                        }
                    }
                }
                for (java.util.Map<String, Object> upd : updates) {
                    Object gidObj = upd.get("glossaryId");
                    if (gidObj != null) {
                        int gid = gidObj instanceof Number ? ((Number) gidObj).intValue() : Integer.parseInt(gidObj.toString());
                        if (gid > 0) {
                            var segValResult = segmentValidationService.validateCrossSegmentRelationship(id, "System", gid, "Glossary");
                            if (!segValResult.isValid) {
                                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                resp.getWriter().write("{\"error\":\"" + segValResult.message.replace("\"", "\\\"") + "\"}");
                                return;
                            }
                        }
                    }
                }

                // Mutual exclusion: reject inserts for glossaries already linked from Strategic Source (Link_Source = 'glossary')
                java.util.List<Integer> excludedGlossaryIds = systemDAO.getGlossaryIdsExcludedFromDataContent(systemIdToUse);
                for (java.util.Map<String, Object> ins : inserts) {
                    Object gidObj = ins.get("glossaryId");
                    if (gidObj != null && excludedGlossaryIds.contains(gidObj instanceof Number ? ((Number) gidObj).intValue() : Integer.parseInt(gidObj.toString()))) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        resp.getWriter().write("{\"error\":\"This glossary is already linked from the Glossary's Strategic Source. It cannot also be added to Data Content Summary.\"}");
                        return;
                    }
                }
                
                // Apply changes using systemIdToUse (cloned if CR active, original otherwise)
                // Collect IDs of newly inserted relationships for mapping
                java.util.List<Integer> insertedIds = new java.util.ArrayList<>();
                systemDAO.applyDataContentChanges(systemIdToUse, inserts, updates, deletes, insertedIds);
                
                // If there's an active CR, save the mapping for each relationship ID - THIS IS CRITICAL
                if (activeCrId != null) {
                    String areaKey = "data-content";
                    try {
                        // Save mapping for each newly inserted relationship
                        for (Integer relationshipId : insertedIds) {
                            facetChangesDAO.saveMapping("system", id, relationshipId, areaKey, activeCrId);
                            logger.info("✅ Saved data content mapping: system={}, relationshipId={}, areaKey={}, crId={}", 
                                        id, relationshipId, areaKey, activeCrId);
                        }
                        
                        // Save mapping for each updated relationship
                        for (java.util.Map<String, Object> update : updates) {
                            Object idObj = update.get("id");
                            Integer relationshipId = null;
                            if (idObj instanceof Number) {
                                relationshipId = ((Number) idObj).intValue();
                            } else if (idObj != null) {
                                try {
                                    relationshipId = Integer.parseInt(idObj.toString());
                                } catch (NumberFormatException e) {
                                    // Ignore
                                }
                            }
                            if (relationshipId != null) {
                                facetChangesDAO.saveMapping("system", id, relationshipId, areaKey, activeCrId);
                                logger.info("✅ Saved data content update mapping: system={}, relationshipId={}, areaKey={}, crId={}", 
                                            id, relationshipId, areaKey, activeCrId);
                            }
                        }
                        
                        // For deletes, we need to track them differently since the relationship is deleted
                        // We'll save a mapping with the deleted ID to indicate it was deleted
                        for (Integer deletedId : deletes) {
                            if (deletedId != null) {
                                facetChangesDAO.saveMapping("system", id, deletedId, areaKey, activeCrId);
                                logger.info("✅ Saved data content delete mapping: system={}, relationshipId={}, areaKey={}, crId={}", 
                                            id, deletedId, areaKey, activeCrId);
                            }
                        }
                        
                        com.google.gson.JsonObject responseJson = new com.google.gson.JsonObject();
                        responseJson.addProperty("success", true);
                        responseJson.addProperty("pending", true);
                        responseJson.addProperty("changeRequestId", activeCrId);
                        responseJson.addProperty("message", "Data content saved as pending change");
                        resp.getWriter().write(responseJson.toString());
                        return;
                    } catch (SQLException e) {
                        logger.error("❌ CRITICAL: Error saving data content mapping for system {}: {}", id, e.getMessage(), e);
                        logger.error("   Data content was saved but mapping to system_changes FAILED!");
                        logger.error("   This means the data content will not appear in pending changes!");
                        // Return error - mapping save is critical for pending changes tracking
                        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        resp.getWriter().write("{\"error\":\"Data content saved but failed to track as pending change. Please contact support.\"}");
                        return;
                    }
                } else {
                    // No active CR - normal save
                    resp.getWriter().write("{\"success\":true}");
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Invalid endpoint\"}");
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid system id\"}");
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing system id\"}");
            return;
        }
        String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] parts = trimmed.split("/");
        String idStr = parts[0];
        try {
            int id = Integer.parseInt(idStr);
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(req, resp, "System", id)) {
                return; // Response already sent
            }
            
            // Check if system is locked before allowing update
            if (!LockUtil.checkLockBeforeEdit(req, resp, "system", id)) {
                return; // Lock check failed, response already sent
            }
            
            // Parse JSON body
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = req.getReader().readLine()) != null) jsonBuilder.append(line);
            com.google.gson.JsonObject body = com.google.gson.JsonParser.parseString(jsonBuilder.toString()).getAsJsonObject();

            // Extract fields (nullable)
            Integer parentId = getIntOrNull(body, "parent_id");
            String name = getStringOrNull(body, "name");
            String description = getStringOrNull(body, "description");
            // Get Type field - Frontend sends Type as ID directly
            Integer typeId = getIntOrNull(body, "Type");
            if (typeId == null) {
                // Fallback: try to get by name if Type field is not provided
                String typeName = getStringOrNull(body, "type");
                if (typeName != null) {
                    typeId = systemDAO.findSystemTypeIdByName(typeName);
                }
            }
            Integer external = getExternalFromBody(body);
            String longName = getStringOrNull(body, "long_name");
            String url = getStringOrNull(body, "url");
            Integer status = getIntOrNull(body, "status");
            Integer lifecycle = getIntOrNull(body, "lifecycle");
            Integer isPublic = getIntOrNull(body, "is_public");
            Integer conf = getIntOrNull(body, "confidentiality_rating");
            Integer integ = getIntOrNull(body, "integrity_rating");
            Integer avail = getIntOrNull(body, "availability_rating");
            String assetId = getStringOrNull(body, "asset_id");
            Integer classification = getIntOrNull(body, "classification");
            Integer dqAutomation = getIntOrNull(body, "dq_automation");

            Integer nobjectIdToExclude = null;
            try {
                Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(SYSTEM_FACET_ID, id);
                if (activeCrId != null) {
                    nobjectIdToExclude = facetChangesDAO.getNObjectId("system", id, "summary", activeCrId);
                }
            } catch (SQLException e) {
                logger.debug("Could not get nobjectId for duplicate check: {}", e.getMessage());
            }

            List<Integer> excludeIds = new ArrayList<>();
            excludeIds.add(id);
            if (nobjectIdToExclude != null) {
                excludeIds.add(nobjectIdToExclude);
            }

            long segmentForName = 1L;
            try {
                if (body.has("segmentId") && !body.get("segmentId").isJsonNull()) {
                    segmentForName = body.get("segmentId").getAsInt();
                } else {
                    int curSeg = segmentDAO.getObjectSegmentId(id, "System");
                    if (curSeg > 0) {
                        segmentForName = curSeg;
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not resolve segment for system name check: {}", e.getMessage());
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                if (name != null && !name.isBlank()) {
                    if (SegmentScopedPrimaryNameCheck.existsExcluding(conn, "System", name.trim(), segmentForName,
                            excludeIds)) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        com.google.gson.JsonObject err = new com.google.gson.JsonObject();
                        err.addProperty("error", "Short Name already exists in this segment");
                        err.addProperty("status", 400);
                        err.addProperty("field", "name");
                        resp.getWriter().write(err.toString());
                        return;
                    }
                }
                if (longName != null && !longName.isBlank()) {
                    if (SegmentScopedPrimaryNameCheck.existsExcluding(conn, "SystemLongName", longName.trim(),
                            segmentForName, excludeIds)) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        com.google.gson.JsonObject err = new com.google.gson.JsonObject();
                        err.addProperty("error", "Long Name already exists in this segment");
                        err.addProperty("status", 400);
                        err.addProperty("field", "long_name");
                        resp.getWriter().write(err.toString());
                        return;
                    }
                }
            } catch (SQLException e) {
                logger.warn("Segment-scoped system name check failed: {}", e.getMessage());
            }

            // Get user ID from request attributes (set by AuthFilter), fallback to session
            Integer userId = null;
            Object uidAttr = req.getAttribute("userId");
            if (uidAttr instanceof Integer) {
                userId = (Integer) uidAttr;
            } else if (uidAttr != null) {
                try { userId = Integer.parseInt(String.valueOf(uidAttr)); } catch (Exception ignore) {}
            }
            if (userId == null) {
                Object ses = req.getSession().getAttribute("userId");
                if (ses instanceof Integer) userId = (Integer) ses;
                else if (ses != null) {
                    try { userId = Integer.parseInt(String.valueOf(ses)); } catch (Exception ignore) {}
                }
            }
            // Final fallback: read ACCESS_TOKEN cookie and parse
            if (userId == null) {
                try {
                    String token = getCookie(req, "ACCESS_TOKEN");
                    if (token != null && !token.isEmpty()) {
                        userId = JwtUtil.getUserIdFromToken(token);
                    }
                } catch (Exception __) { /* ignore */ }
            }

            // Check segment-based edit permission
            if (userId != null && userId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(userId, id, "System");
                if (!canEdit) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.getWriter().write("{\"error\":\"Access denied. You don't have permission to edit this system.\"}");
                    return;
                }
            }

            // Check if this object has an active auto-created CR (only automatic CRs use pending changes)
            // If so, clone the row and update the clone instead of updating directly
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(SYSTEM_FACET_ID, id);
            logger.info("[SystemServlet UPDATE] Checking for active CR for system {} (facetId={})", id, SYSTEM_FACET_ID);
            logger.info("[SystemServlet UPDATE] Active CR result: {}", activeCrId);
            
            int currentUserId = userId != null ? userId : 1;
            
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
                                if (crCreatedBy == currentUserId) {
                                    // Same user - use existing CR, don't create new one
                                    logger.info("[SystemServlet UPDATE] Active CR {} belongs to current user {}, using existing CR", activeCrId, currentUserId);
                                    shouldCheckForNewCR = false;
                                } else {
                                    // Different user - need to create new CR
                                    logger.info("[SystemServlet UPDATE] Active CR {} belongs to different user {} (current: {}), will create new CR", 
                                        activeCrId, crCreatedBy, currentUserId);
                                    activeCrId = null; // Reset to allow new CR creation
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("[SystemServlet UPDATE] Error checking CR owner: {}", e.getMessage());
                    // Continue with check
                }
            }
            
            // If no CR exists OR existing CR belongs to different user, check if DFCR edit workflow is enabled and auto-create CR
            if (shouldCheckForNewCR) {
                try {
                    logger.info("[SystemServlet UPDATE] Checking DFCR settings for new CR...");
                    // Check if user is admin for bypass logic
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
                    logger.info("[SystemServlet UPDATE] User isAdmin: {}", isAdmin);
                    
                    // Get system type for type-specific workflow settings
                    Integer systemType = typeId; // Use the type from the form submission
                    
                    Integer autoCrId = dfcrService.applyDefaultsOnEdit("System", id, systemType, currentUserId, isAdmin);
                    if (autoCrId != null) {
                        logger.info("[SystemServlet UPDATE] DFCR auto-created CR: {}", autoCrId);
                        activeCrId = autoCrId;
                    } else {
                        logger.info("[SystemServlet UPDATE] DFCR did not create CR (workflow not enabled or admin bypass)");
                    }
                } catch (Exception e) {
                    logger.error("[SystemServlet UPDATE] Error checking/creating DFCR CR: {}", e.getMessage(), e);
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
                                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                        resp.getWriter().write("{\"error\":\"Cannot edit this object. There is an active Change Request (status: " + 
                                            statusName.replace("\"", "\\\"") + 
                                            ") that must be completed or cancelled before editing is allowed.\"}");
                                        logger.warn("System {} edit blocked - active CR {} with status: {}", id, activeCrId, statusName);
                                        return;
                                    }
                                    // If status is "Pending Start", allow editing (workflow hasn't started yet)
                                    logger.info("System {} edit allowed - CR {} is in Pending Start status, workflow hasn't started yet", id, activeCrId);
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error checking CR status: {}", e.getMessage());
                    // Continue - don't block if we can't check status
                }
                
                // Object is under revision - clone row and update clone
                logger.info("System {} has active CR {} - cloning and updating", id, activeCrId);
                
                try {
                    // Get or create mapping for 'summary' area
                    Integer nobjectId = facetChangesDAO.getNObjectId("system", id, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // First edit - clone the row
                        nobjectId = cloneSystemRow(id);
                        if (nobjectId == null) {
                            throw new SQLException("Failed to clone system row");
                        }
                        // Create mapping
                        facetChangesDAO.saveMapping("system", id, nobjectId, "summary", activeCrId);
                    }

                    // AssetID must be unique — check before updating clone row (exclude only the row we update)
                    if (assetId != null && !assetId.isBlank()
                            && systemDAO.existsSystemWithAssetIdExcluding(assetId.trim(), nobjectId, null)) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        com.google.gson.JsonObject err = new com.google.gson.JsonObject();
                        err.addProperty("error",
                                "Reference (Asset ID) already exists. It must be unique for each system.");
                        err.addProperty("status", 400);
                        err.addProperty("field", "asset_id");
                        resp.getWriter().write(err.toString());
                        return;
                    }
                    
                    // Update the cloned row
                    boolean ok = systemDAO.updateSystem(nobjectId, name, parentId, description, typeId, external, longName, url,
                            status, lifecycle, isPublic, conf, integ, avail, assetId, classification, dqAutomation, userId);
                    if (!ok) {
                        throw new SQLException("Update failed");
                    }
                    
                    // Return success but indicate changes are pending
                    com.google.gson.JsonObject respJson = new com.google.gson.JsonObject();
                    respJson.addProperty("success", true);
                    respJson.addProperty("id", id);
                    respJson.addProperty("message", "Changes saved as pending. They will apply when the Change Request is completed.");
                    respJson.addProperty("pendingChanges", true);
                    respJson.addProperty("changeRequestId", activeCrId);
                    resp.getWriter().write(respJson.toString());
                    return;
                } catch (SQLException e) {
                    logger.error("Error handling pending changes: {}", e.getMessage());
                    if (isDuplicateKeyException(e)) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        resp.getWriter().write(
                                "{\"error\":\"Reference (Asset ID) already exists. It must be unique for each system.\",\"field\":\"asset_id\",\"status\":400}");
                    } else {
                        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage().replace("\"","\\\"") + "\"}");
                    }
                    return;
                }
            }

            // AssetID must be unique before direct update
            if (assetId != null && !assetId.isBlank()
                    && systemDAO.existsSystemWithAssetIdExcluding(assetId.trim(), id, null)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                com.google.gson.JsonObject err = new com.google.gson.JsonObject();
                err.addProperty("error", "Reference (Asset ID) already exists. It must be unique for each system.");
                err.addProperty("status", 400);
                err.addProperty("field", "asset_id");
                resp.getWriter().write(err.toString());
                return;
            }
            
            // No active CR - apply changes directly to database
            boolean ok = systemDAO.updateSystem(id, name, parentId, description, typeId, external, longName, url,
                    status, lifecycle, isPublic, conf, integ, avail, assetId, classification, dqAutomation, userId);
            if (!ok) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"System not found\"}");
                return;
            }

            // Update segment assignment if provided
            Integer segmentId = getIntOrNull(body, "segmentId");
            if (segmentId != null) {
                try {
                    int currentSegmentId = segmentDAO.getObjectSegmentId(id, "System");
                    if (currentSegmentId != segmentId) {
                        // Validate hierarchy and segment move (includes parent, children, and stakeholder checks)
                        var validationResult = segmentValidationService.validateSegmentMove(id, segmentId, "System", parentId);
                        if (!validationResult.isValid) {
                            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            com.google.gson.JsonObject err = new com.google.gson.JsonObject();
                            err.addProperty("error", validationResult.message);
                            err.addProperty("status", 400);
                            resp.getWriter().write(err.toString());
                            return;
                        }
                        
                        if (currentSegmentId > 0) {
                            segmentDAO.removeObjectFromSegment(currentSegmentId, id, "System", userId != null ? userId : 1);
                        }
                        segmentDAO.assignObjectToSegment(segmentId, id, "System", userId != null ? userId : 1);
                        //system.out.println("✅ System " + id + " segment changed from " + currentSegmentId + " to " + segmentId);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error updating system segment: " + e.getMessage());
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    com.google.gson.JsonObject err = new com.google.gson.JsonObject();
                    err.addProperty("error", "Error updating system segment: " + e.getMessage());
                    err.addProperty("status", 500);
                    resp.getWriter().write(err.toString());
                    return;
                }
            }

            resp.getWriter().write("{\"success\":true}");
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid system id\"}");
        } catch (Exception e) {
            logger.error("System update failed: {}", e.getMessage(), e);
            if (isDuplicateKeyException(e)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                try {
                    resp.getWriter().write(
                            "{\"error\":\"Reference (Asset ID) already exists. It must be unique for each system.\",\"field\":\"asset_id\",\"status\":400}");
                } catch (IOException io) {
                    logger.error("Failed to write error response", io);
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                String msg = e.getMessage() != null ? e.getMessage() : "Database error";
                msg = msg.replace("\"","\\\"");
                resp.getWriter().write("{\"error\":\"Database error: " + msg + "\"}");
            }
        }
    }

    /** MySQL duplicate entry / SQLState 23000 */
    private static boolean isDuplicateKeyException(Throwable e) {
        while (e != null) {
            if (e instanceof java.sql.SQLException) {
                java.sql.SQLException se = (java.sql.SQLException) e;
                if ("23000".equals(se.getSQLState()))
                    return true;
                String m = se.getMessage();
                if (m != null && m.toLowerCase().contains("duplicate"))
                    return true;
            }
            e = e.getCause();
        }
        return false;
    }

    private static Integer getIntOrNull(com.google.gson.JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : null;
    }

    /** Parse JSON "external" (0/1, boolean, or string) for system Internal/External. */
    private static Integer getExternalFromBody(com.google.gson.JsonObject body) {
        if (body == null || !body.has("external") || body.get("external").isJsonNull()) {
            return null;
        }
        com.google.gson.JsonElement e = body.get("external");
        if (!e.isJsonPrimitive()) {
            return null;
        }
        com.google.gson.JsonPrimitive p = e.getAsJsonPrimitive();
        try {
            if (p.isBoolean()) {
                return p.getAsBoolean() ? 1 : 0;
            }
            if (p.isNumber()) {
                return p.getAsInt();
            }
            if (p.isString()) {
                String s = p.getAsString().trim();
                if ("1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s)) {
                    return 1;
                }
                if ("0".equals(s) || "false".equalsIgnoreCase(s) || "no".equalsIgnoreCase(s)) {
                    return 0;
                }
                return Integer.parseInt(s);
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }
    private static String getStringOrNull(com.google.gson.JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private String getRequestBody(jakarta.servlet.http.HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String getCookie(jakarta.servlet.http.HttpServletRequest request, String name) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (jakarta.servlet.http.Cookie c : cookies) {
            if (c != null && name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    // Handle system save (creation of new system)
    private void handleSystemSave(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            com.google.gson.JsonObject body = com.google.gson.JsonParser.parseString(getRequestBody(req)).getAsJsonObject();

            String name = getStringOrNull(body, "name");
            if (name == null || name.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Missing required field: name\",\"status\":400}");
                return;
            }

            Integer parentId = getIntOrNull(body, "parent_id");
            String description = getStringOrNull(body, "description");

            // Get Type field - Frontend sends Type as ID directly
            Integer typeId = getIntOrNull(body, "Type");
            if (typeId == null) {
                // Fallback: try to get by name if Type field is not provided
                String typeName = getStringOrNull(body, "type");
                if (typeName != null) {
                    typeId = systemDAO.findSystemTypeIdByName(typeName);
                }
            }

            Integer external = getExternalFromBody(body);
            String longName = getStringOrNull(body, "long_name");
            String url = getStringOrNull(body, "url");
            Integer status = getIntOrNull(body, "status");
            Integer lifecycle = getIntOrNull(body, "lifecycle");
            Integer isPublic = getIntOrNull(body, "is_public");
            Integer conf = getIntOrNull(body, "confidentiality_rating");
            Integer integ = getIntOrNull(body, "integrity_rating");
            Integer avail = getIntOrNull(body, "availability_rating");
            String assetId = getStringOrNull(body, "asset_id");
            Integer classification = getIntOrNull(body, "classification");
            Integer dqAutomation = getIntOrNull(body, "dq_automation");

            // Apply DF_CR defaults if workflow is enabled for System
            boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
            int userId = UserContextUtil.getCurrentUserId(req);
            DFCRService.LockedFieldsInfo lockedFields = dfcrService.getLockedFieldsInfo("System", isAdmin, userId);
            
            if (lockedFields.isWorkflowEnabled()) {
                // Override status with default if locked
                if (lockedFields.isStatusLocked() && lockedFields.getDefaultStatusId() != null) {
                    status = lockedFields.getDefaultStatusId();
                    logger.info("Applying DF_CR default status {} for System", status);
                }
                // Override lifecycle with default if locked
                if (lockedFields.isLifecycleLocked() && lockedFields.getDefaultLifecycleId() != null) {
                    lifecycle = lockedFields.getDefaultLifecycleId();
                    logger.info("Applying DF_CR default lifecycle {} for System", lifecycle);
                }
            }

            long segmentIdForName = 1L;
            Integer segIncoming = getIntOrNull(body, "segmentId");
            if (segIncoming != null) {
                segmentIdForName = segIncoming.longValue();
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "System", name.trim(), segmentIdForName, null)) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    com.google.gson.JsonObject err = new com.google.gson.JsonObject();
                    err.addProperty("error", "Short Name already exists in this segment");
                    err.addProperty("status", 400);
                    err.addProperty("field", "name");
                    resp.getWriter().write(err.toString());
                    return;
                }
                if (longName != null && !longName.isBlank()) {
                    if (SegmentScopedPrimaryNameCheck.exists(conn, "SystemLongName", longName.trim(), segmentIdForName,
                            null)) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        com.google.gson.JsonObject err = new com.google.gson.JsonObject();
                        err.addProperty("error", "Long Name already exists in this segment");
                        err.addProperty("status", 400);
                        err.addProperty("field", "long_name");
                        resp.getWriter().write(err.toString());
                        return;
                    }
                }
            } catch (SQLException e) {
                logger.warn("Segment-scoped system name check failed: {}", e.getMessage());
            }

            // userId is already defined above (line 626) from UserContextUtil.getCurrentUserId(req)
            // Apply fallback logic if userId is invalid (<= 0)
            if (userId <= 0) {
                // Fallback: try to get from request attributes
                Object uidAttr = req.getAttribute("userId");
                if (uidAttr instanceof Integer) {
                    userId = (Integer) uidAttr;
                } else if (uidAttr != null) {
                    try { userId = Integer.parseInt(String.valueOf(uidAttr)); } catch (Exception ignore) {}
                }
                // Fallback: try session
                if (userId <= 0) {
                    Object ses = req.getSession().getAttribute("userId");
                    if (ses instanceof Integer) userId = (Integer) ses;
                    else if (ses != null) {
                        try { userId = Integer.parseInt(String.valueOf(ses)); } catch (Exception ignore) {}
                    }
                }
                // Final fallback: read ACCESS_TOKEN cookie and parse
                if (userId <= 0) {
                    try {
                        String token = getCookie(req, "ACCESS_TOKEN");
                        if (token != null && !token.isEmpty()) {
                            userId = JwtUtil.getUserIdFromToken(token);
                        }
                    } catch (Exception __) { /* ignore */ }
                }
                // If still invalid, accept CreatedBy_ID from request body (frontend may provide it)
                if (userId <= 0) {
                    try {
                        if (body.has("CreatedBy_ID") && !body.get("CreatedBy_ID").isJsonNull()) {
                            userId = body.get("CreatedBy_ID").getAsInt();
                        }
                    } catch (Exception ignore) { /* keep as is if invalid */ }
                }
            }
            ////system.out.println("🔍 Resolved userId: " + userId);

            // Validate segment hierarchy before insert
            Integer segmentId = getIntOrNull(body, "segmentId");
            if (segmentId == null) segmentId = 1;
            if (parentId != null && parentId > 0) {
                try {
                    var hierarchyValidation = segmentValidationService.validateParentChildSegment(parentId, segmentId, "System");
                    if (!hierarchyValidation.isValid) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        com.google.gson.JsonObject err = new com.google.gson.JsonObject();
                        err.addProperty("error", hierarchyValidation.message);
                        err.addProperty("status", 400);
                        resp.getWriter().write(err.toString());
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Error validating system hierarchy: " + e.getMessage());
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    com.google.gson.JsonObject err = new com.google.gson.JsonObject();
                    err.addProperty("error", "Error validating segment hierarchy: " + e.getMessage());
                    err.addProperty("status", 500);
                    resp.getWriter().write(err.toString());
                    return;
                }
            }

            int newId = systemDAO.insertSystem(
                    name,
                    parentId,
                    description,
                    typeId,
                    external,
                    longName,
                    url,
                    status,
                    lifecycle,
                    isPublic,
                    conf,
                    integ,
                    avail,
                    assetId,
                    classification,
                    dqAutomation,
                    userId
            );

            if (newId <= 0) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Failed to save system\",\"status\":500}");
                return;
            }

            // Assign system to segment
            try {
                segmentDAO.assignObjectToSegment(segmentId, newId, "System", userId > 0 ? userId : 1);
                //system.out.println("✅ System " + newId + " assigned to segment " + segmentId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning system to segment: " + e.getMessage());
                // Continue - don't fail the entire save
            }

            // Assign creator role
            if (userId > 0) {
                try {
                    assignCreatorRole(newId, userId);
                } catch (Exception e) {
                    System.err.println("❌ Error assigning creator role: " + e.getMessage());
                    e.printStackTrace();
                    // Continue - don't fail the entire save
                }
            } else {
                System.err.println("⚠️ No userId in session, skipping role assignment");
            }

            // Create audit records for the new system
            try {
                String userName = getCurrentUserName(req);
                systemDAO.createSystemAuditRecords(newId, userName);
                ////system.out.println("✅ Audit records created for system ID: " + newId);
            } catch (Exception e) {
                System.err.println("❌ Error creating audit records: " + e.getMessage());
                e.printStackTrace();
                // Continue - don't fail the entire save operation
            }

            // Create system audit record
            try {
                systemDAO.createSystemAuditRecord(newId);
                ////system.out.println("✅ System audit record created for system ID: " + newId);
            } catch (Exception e) {
                System.err.println("❌ Error creating system audit record: " + e.getMessage());
                e.printStackTrace();
                // Continue - don't fail the entire save operation
            }

            // Auto-create change request if DF_CR workflow is enabled
            Integer changeRequestId = null;
            try {
                int finalUserId = userId > 0 ? userId : 0;
                // typeId is already defined above (line 306), reuse it
                changeRequestId = dfcrService.applyDefaultsOnCreate("System", newId, typeId, finalUserId, isAdmin);
                if (changeRequestId != null) {
                    logger.info("Auto-created change request {} for new System {} (typeId: {})", changeRequestId, newId, typeId);
                }
            } catch (Exception e) {
                logger.error("Error auto-creating change request for System {}: {}", newId, e.getMessage());
                // Don't fail the main operation if CR creation fails
            }

            com.google.gson.JsonObject ok = new com.google.gson.JsonObject();
            ok.addProperty("status", "success");
            ok.addProperty("message", "System saved successfully");
            ok.addProperty("systemId", newId);
            ok.addProperty("id", newId);
            if (changeRequestId != null) {
                ok.addProperty("changeRequestId", changeRequestId);
                ok.addProperty("workflowEnabled", true);
            }
            resp.getWriter().write(gson.toJson(ok));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\",\"status\":500}");
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid request body\",\"status\":400}");
        }
    }

    private void assignCreatorRole(int systemId, int userId) throws SQLException {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int moduleId = com.example.budg_v2.util.DefaultStakeholderUtil.getModuleId(conn, "System");
                java.util.List<Integer> rolesToAssign = com.example.budg_v2.util.DefaultStakeholderUtil.getDefaultRolesForCreator(conn, moduleId, userId);

                if (rolesToAssign.isEmpty()) {
                    conn.commit();
                    return;
                }

                String userFullName = getUserFullName(userId);

                for (Integer roleId : rolesToAssign) {
                    try {
                        String insertOXP = """
                                INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                                VALUES (NULL, ?, ?, 2, 1, ?)
                                """;
                        int objectXPeopleId;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertOXP, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setInt(1, userId);
                            stmt.setInt(2, roleId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) {
                                throw new java.sql.SQLException("Failed to insert into object_x_people");
                            }
                            try (java.sql.ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                                if (generatedKeys.next()) {
                                    objectXPeopleId = generatedKeys.getInt(1);
                                } else {
                                    throw new java.sql.SQLException("Failed to get generated key for object_x_people");
                                }
                            }
                        }

                        String insertSXOP = """
                                INSERT INTO system_x_objectxpeople (Object_x_ipid, SystemID, Last_UpdateUser_ID)
                                VALUES (?, ?, ?)
                                """;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertSXOP)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, systemId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) {
                                throw new java.sql.SQLException("Failed to insert into system_x_objectxpeople");
                            }
                        }

                        com.example.budg_v2.util.RoleNotificationHelper.createNotificationAfterCreatorRoleAssigned(
                                "System", systemId, userId, roleId, objectXPeopleId, conn);

                        if (userFullName != null) {
                            systemDAO.createStakeholderAuditRecords(systemId, userFullName, userFullName, roleId);
                        }
                    } catch (java.sql.SQLException e) {
                        System.err.println("❌ Error assigning default role " + roleId + " to creator: " + e.getMessage());
                    }
                }

                conn.commit();
            } catch (java.sql.SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

  

    /**
     * الحصول على اسم المستخدم الحالي
     */
    private String getCurrentUserName(HttpServletRequest req) {
        try {
            // محاولة الحصول على اسم المستخدم من session أو attributes
            Object userAttr = req.getAttribute("userName");
            if (userAttr != null) {
                return userAttr.toString();
            }
            
            // محاولة الحصول من userId من attributes أو session
            Integer userId = null;
            Object uidAttr = req.getAttribute("userId");
            if (uidAttr instanceof Integer) {
                userId = (Integer) uidAttr;
            } else if (uidAttr != null) {
                try { userId = Integer.parseInt(String.valueOf(uidAttr)); } catch (Exception ignore) {}
            }
            
            // إذا لم نجد userId في attributes، جرب UserContextUtil
            if (userId == null) {
                try {
                    userId = com.example.budg_v2.util.UserContextUtil.getCurrentUserId(req);
                } catch (Exception e) {
                    System.err.println("Error getting userId from UserContextUtil: " + e.getMessage());
                }
            }
            
            // إذا لم نجد userId في session أيضاً
            if (userId == null) {
                Object ses = req.getSession().getAttribute("userId");
                if (ses instanceof Integer) userId = (Integer) ses;
                else if (ses != null) {
                    try { userId = Integer.parseInt(String.valueOf(ses)); } catch (Exception ignore) {}
                }
            }
            
            // إذا وجدنا userId، احصل على اسم المستخدم من قاعدة البيانات
            if (userId != null) {
                String fullName = getUserFullName(userId);
                if (fullName != null && !fullName.equals("Unknown User")) {
                    return fullName;
                }
                return "User ID: " + userId;
            }
            
            return "System"; // Fallback if no user found
        } catch (Exception e) {
            System.err.println("Error getting current user name: " + e.getMessage());
            return "System";
        }
    }

    private String getUserName(HttpServletRequest req) {
        try {
            String userJson = (String) req.getAttribute("user");
            if (userJson != null && userJson.contains("\"username\":")) {
                int start = userJson.indexOf("\"username\":\"") + 12;
                int end = userJson.indexOf("\"", start);
                if (end > start) {
                    return userJson.substring(start, end);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting username: " + e.getMessage());
        }
        return "Unknown User";
    }

    private String getUserFullName(int userId) {
        try {
            String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
            try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("fullName");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting user full name from database: " + e.getMessage());
        }
        return "Unknown User";
    }
    
    /**
     * Clone a system row for pending changes
     * Returns the ID of the cloned row, or null on error
     */
    private Integer cloneSystemRow(int originalId) throws SQLException {
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
        
        try (java.sql.Connection conn = DatabaseConnection.getConnection();
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


