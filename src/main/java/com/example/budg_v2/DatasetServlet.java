package com.example.budg_v2;

import com.example.budg_v2.dao.AttributeDAO;
import com.example.budg_v2.dao.DatasetDAO;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Dataset;
import com.example.budg_v2.service.DatasetService;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.AxonLogger;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.DefaultStakeholderUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.LockUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.ReferenceNumberGenerator;
import com.example.budg_v2.util.RoleNotificationHelper;
import com.example.budg_v2.util.UserContextUtil;
import static com.example.budg_v2.util.AxonLogger.kv;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

@WebServlet(name = "DatasetServlet", urlPatterns = { "/api/dataset/*", "/api/dataset/create", "/api/view/dataset/*",
        "/api/create/dataset", "/api/create/dataset/*" })
public class DatasetServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DatasetServlet.class);
    private static final int DATASET_FACET_ID = 11; // Dataset facet ID in module table
    private final DatasetDAO datasetDAO = new DatasetDAO();
    private final DatasetService datasetService = new DatasetService();
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();
    private final SegmentDAO segmentDAO = new SegmentDAO();
    private final AttributeDAO attributeDAO = new AttributeDAO();
    private final DFCRService dfcrService = new DFCRService();
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.handlePreflight(response);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        String idStr;
        if (pathInfo != null && !"/".equals(pathInfo)) {
            idStr = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            if (idStr.endsWith("/")) {
                idStr = idStr.substring(0, idStr.length() - 1);
            }
        } else {
            idStr = req.getParameter("id");
        }
        if (idStr == null || idStr.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing dataset id\"}");
            return;
        }

        try {
            // Set request and user data for logging context
            Integer currentUserId = UserContextUtil.getCurrentUserIdOrNull(req);
            String userEmail = (String) req.getAttribute("userEmail");
            AxonLogger.setRequestData(req);
            AxonLogger.setUserData(currentUserId, userEmail);
            
            String[] parts = idStr.split("/");
            if (parts.length > 0 && "glossary".equalsIgnoreCase(parts[0])) {
                if (parts.length < 2 || parts[1].isBlank()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Missing glossary id\"}");
                    return;
                }
                int glossaryId = Integer.parseInt(parts[1]);
                var list = datasetDAO.getDatasetsByGlossaryId(glossaryId);
                
                // Example: Log successful dataset loading
                AxonLogger.logInfo("Dataset loaded successfully",
                    kv("component", "BUDG-core"),
                    kv("module", "Dataset"),
                    kv("action", "loadDatasetsByGlossary"),
                    kv("glossary_id", glossaryId),
                    kv("dataset_count", list.size()),
                    kv("user_id", currentUserId)
                );
                
                resp.getWriter().write(gson.toJson(list));
                return;
            }

            int id = Integer.parseInt(parts[0]);
            if (parts.length > 1 && "stakeholders".equalsIgnoreCase(parts[1])) {
                var list = datasetDAO.getDirectStakeholdersForDataset(id);
                resp.getWriter().write(gson.toJson(list));
                return;
            } else if (parts.length > 1 && "attributes".equalsIgnoreCase(parts[1])) {
                // Check if viewing changes - delegate to AttributeServlet logic
                String view = req.getParameter("view");
                java.util.List<java.util.Map<String, Object>> list;
                
                if ("changes".equals(view)) {
                    // Get original + pending attributes for View Changes mode
                    list = getAttributesWithPendingChanges(id);
                } else {
                    // Get only original attributes (exclude pending) for View Original mode
                    list = getOriginalAttributes(id);
                }
                
                resp.getWriter().write(gson.toJson(list));
                return;
            } else if (parts.length > 1 && "roles".equalsIgnoreCase(parts[1])) {
                var list = datasetDAO.getRolesForDataset(id);
                resp.getWriter().write(gson.toJson(list));
                return;
            } else if (parts.length > 1 && "statuses".equalsIgnoreCase(parts[1])) {
                var list = datasetDAO.getStatusesForDataset(id);
                resp.getWriter().write(gson.toJson(list));
                return;
            } else if (parts.length > 2 && "users".equalsIgnoreCase(parts[1])) {
                int roleId = Integer.parseInt(parts[2]);
                var list = datasetDAO.getUsersByRole(id, roleId);
                resp.getWriter().write(gson.toJson(list));
                return;
            }

            // Check if viewing changes (pending changes mode)
            String view = req.getParameter("view");
            int datasetIdToLoad = id;
            
            if ("changes".equals(view)) {
                try {
                    // ⚠️ CRITICAL: Only check for automatic CRs (mandatory_workflow = true)
                    // Manual CRs should NOT trigger pending changes logic
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_ID, id);
                    if (activeCrId != null) {
                        Integer nobjectId = facetChangesDAO.getNObjectId("dataset", id, "summary", activeCrId);
                        if (nobjectId != null) {
                            datasetIdToLoad = nobjectId;
                        }
                    }
                } catch (SQLException e) {
                    // If error getting mapping, fall back to original id
                    logger.warn("Error getting nobject_id for view=changes: {}", e.getMessage());
                }
            }
            
            var result = datasetDAO.getById(datasetIdToLoad);
            if (result == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Dataset not found\"}");
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
            
            // GUEST ACCESS CHECK: Only allow public datasets in Enterprise segment
            if (userId <= 0) {
                // Guest user - check if dataset is public and in Enterprise segment
                try {
                    boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "Dataset");
                    if (!canAccess) {
                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        resp.getWriter().write("{\"error\":\"Access denied. This resource is not publicly accessible.\"}");
                        return;
                    }
                    // Guest can never edit
                    result.put("canEdit", false);
                } catch (SQLException e) {
                    // On error, deny access for safety
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.getWriter().write("{\"error\":\"Access denied. Unable to verify access permissions.\"}");
                    return;
                }
            }
            
            // Authenticated user access check
            if (userId > 0) {
                boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "Dataset");
                if (!canAccess) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.getWriter()
                            .write("{\"error\":\"Access denied. You don't have permission to view this dataset.\"}");
                    return;
                }

                // Add edit permission info to response
                boolean canEdit = SegmentAccessService.canEditObject(userId, id, "Dataset");
                result.put("canEdit", canEdit);
            }

            resp.getWriter().write(gson.toJson(result));
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid dataset id\"}");
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Database error";
            resp.getWriter().write("{\"error\":\"" + msg + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        
        // Handle clone endpoint: POST /api/dataset/{id}/clone
        if (pathInfo != null && pathInfo.matches("^/\\d+/clone/?$")) {
            handleCloneDataset(request, response);
            return;
        }
        
        if (pathInfo != null && pathInfo.matches("^/\\d+/stakeholders/?$")) {
            try {
                String[] parts = pathInfo.replaceFirst("^/", "").split("/");
                int datasetId = Integer.parseInt(parts[0]);

                JsonObject body = JsonUtil.parseJsonFromRequest(request.getReader());
                java.util.Map<String, Object> changes = new java.util.HashMap<>();
                changes.put("inserts", com.example.budg_v2.util.JsonUtil.getAsListOfMaps(body, "inserts"));
                changes.put("updates", com.example.budg_v2.util.JsonUtil.getAsListOfMaps(body, "updates"));
                changes.put("deletes", com.example.budg_v2.util.JsonUtil.getAsListOfMaps(body, "deletes"));

                int currentUserId = com.example.budg_v2.util.UserContextUtil.getCurrentUserId(request);
                java.util.Set<Integer> deleteIds = com.example.budg_v2.util.DefaultStakeholderUtil.extractObjectXPeopleIdsFromDeletes(changes);
                try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                    com.example.budg_v2.util.DefaultStakeholderUtil.ValidationResult vr =
                            com.example.budg_v2.util.DefaultStakeholderUtil.validateCurrentUserCanSaveStakeholders(
                                    conn, "Dataset", datasetId, currentUserId, deleteIds);
                    if (!vr.isValid()) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("{\"success\":false,\"error\":\"" + (vr.getWarningMessage() != null ? vr.getWarningMessage().replace("\"", "\\\"") : "") + "\"}");
                        return;
                    }
                }
                datasetDAO.saveStakeholdersChanges(datasetId, changes, currentUserId);

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                response.getWriter().write(resp.toString());
                return;
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\":false,\"error\":\"Invalid dataset id\"}");
                return;
            } catch (SQLException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                String msg = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Database error";
                response.getWriter().write("{\"success\":false,\"error\":\"" + msg + "\"}");
                return;
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"success\":false,\"error\":\"Unexpected error\"}");
                return;
            }
        }

        // Check create permission for new dataset creation
        // Skip permission check for clone and stakeholders endpoints
        if (pathInfo == null || pathInfo.equals("/") || (!pathInfo.matches("^/\\d+/clone/?$") && !pathInfo.matches("^/\\d+/stakeholders/?$"))) {
            if (!PermissionCheckUtil.checkCreatePermission(request, response, "Data Sets")) {
                return; // Response already sent
            }
        }

        try {
            JsonObject json = JsonUtil.parseJsonFromRequest(request.getReader());

            Dataset d = new Dataset();
            d.setPrimaryName(JsonUtil.getJsonString(json, "primaryName"));
            d.setMasterSource(JsonUtil.getJsonInt(json, "masterSource"));
            d.setRefNumber(JsonUtil.getJsonString(json, "refNumber"));
            d.setDefinition(JsonUtil.getJsonString(json, "definition"));
            d.setGlossary(JsonUtil.getJsonInt(json, "glossary"));
            d.setUsage(JsonUtil.getJsonString(json, "usage"));
            d.setStatus(JsonUtil.getJsonInt(json, "status"));
            d.setDatasetType(JsonUtil.getJsonInt(json, "datasetType"));
            d.setAccessControlType(JsonUtil.getJsonInt(json, "accessControlType"));
            d.setLifecycle(JsonUtil.getJsonInt(json, "lifecycle"));

            // Apply DF_CR defaults if workflow is enabled for Data Set
            boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
            int userId = UserContextUtil.getCurrentUserId(request);
            DFCRService.LockedFieldsInfo lockedFields = dfcrService.getLockedFieldsInfo("Data Set", isAdmin, userId);

            if (lockedFields.isWorkflowEnabled()) {
                // Override status with default if locked
                if (lockedFields.isStatusLocked() && lockedFields.getDefaultStatusId() != null) {
                    d.setStatus(lockedFields.getDefaultStatusId());
                    logger.info("Applying DF_CR default status {} for Data Set", d.getStatus());
                }
                // Override lifecycle with default if locked
                if (lockedFields.isLifecycleLocked() && lockedFields.getDefaultLifecycleId() != null) {
                    d.setLifecycle(lockedFields.getDefaultLifecycleId());
                    logger.info("Applying DF_CR default lifecycle {} for Data Set", d.getLifecycle());
                }
            }

            // Required field validation with field keys for frontend mapping
            if (d.getPrimaryName() == null || d.getPrimaryName().isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Name is required");
                err.addProperty("status", 400);
                err.addProperty("field", "primaryName");
                response.getWriter().write(err.toString());
                return;
            }
            if (d.getMasterSource() == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "System Short Name is required");
                err.addProperty("status", 400);
                err.addProperty("field", "masterSource");
                response.getWriter().write(err.toString());
                return;
            }
            if (d.getDefinition() == null || d.getDefinition().isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Definition is required");
                err.addProperty("status", 400);
                err.addProperty("field", "definition");
                response.getWriter().write(err.toString());
                return;
            }
            if (d.getGlossary() == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Glossary Name is required");
                err.addProperty("status", 400);
                err.addProperty("field", "glossary");
                response.getWriter().write(err.toString());
                return;
            }
            if (d.getStatus() == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "BUDG Status is required");
                err.addProperty("status", 400);
                err.addProperty("field", "status");
                response.getWriter().write(err.toString());
                return;
            }
            if (d.getDatasetType() == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Type is required");
                err.addProperty("status", 400);
                err.addProperty("field", "datasetType");
                response.getWriter().write(err.toString());
                return;
            }
            if (d.getAccessControlType() == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "BUDG Viewing is required");
                err.addProperty("status", 400);
                err.addProperty("field", "accessControlType");
                response.getWriter().write(err.toString());
                return;
            }
            if (d.getLifecycle() == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Lifecycle is required");
                err.addProperty("status", 400);
                err.addProperty("field", "lifecycle");
                response.getWriter().write(err.toString());
                return;
            }

            // Uniqueness: name and refNumber
            try {
                try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                    // Validate name uniqueness per system
                    // If MasterSource is NULL: Check global uniqueness
                    // If MasterSource is set: Check uniqueness only within the same system
                    String nameCheckSql;
                    java.sql.PreparedStatement ps;
                    if (d.getMasterSource() == null) {
                        // NULL MasterSource: Check global uniqueness
                        nameCheckSql = "SELECT 1 FROM dataset WHERE LOWER(PrimaryName) = LOWER(?) AND DeletedDatetime IS NULL LIMIT 1";
                        ps = conn.prepareStatement(nameCheckSql);
                        ps.setString(1, d.getPrimaryName());
                    } else {
                        // MasterSource set: Check uniqueness within the same system
                        nameCheckSql = "SELECT 1 FROM dataset WHERE LOWER(PrimaryName) = LOWER(?) AND MasterSource = ? AND DeletedDatetime IS NULL LIMIT 1";
                        ps = conn.prepareStatement(nameCheckSql);
                        ps.setString(1, d.getPrimaryName());
                        ps.setInt(2, d.getMasterSource());
                    }
                    try (ps) {
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                JsonObject err = new JsonObject();
                                err.addProperty("error", "Name already exists");
                                err.addProperty("status", 400);
                                err.addProperty("field", "primaryName");
                                response.getWriter().write(err.toString());
                                return;
                            }
                        }
                    }
                    // Validate RefNumber uniqueness for create - use centralized RefNumberValidator
                    // Rule: Cannot repeat ref for different objects in the same facet
                    if (d.getRefNumber() != null && !d.getRefNumber().isBlank()) {
                        try {
                            boolean isUnique = com.example.budg_v2.util.RefNumberValidator.isRefNumberUnique("Dataset", d.getRefNumber());
                            if (!isUnique) {
                                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                JsonObject err = new JsonObject();
                                err.addProperty("error", "This reference number is already in use. Please enter a unique reference number.");
                                err.addProperty("status", 400);
                                err.addProperty("field", "refNumber");
                                response.getWriter().write(err.toString());
                                return;
                            }
                        } catch (SQLException e) {
                            // Log error but don't block operation
                            logger.error("Error validating RefNumber uniqueness in DatasetServlet: " + e.getMessage());
                        }
                    }
                }
            } catch (SQLException ignore) {
            }

            // userId is already defined above (line 247) from UserContextUtil.getCurrentUserId(request)

            // Check if dataset is public (AccessControlType == 1)
            boolean datasetIsPublic = d.getAccessControlType() != null && d.getAccessControlType() == 1;

            // Determine desired segment: If glossary is selected, derive segment from glossary
            Integer segmentId = JsonUtil.getJsonInt(json, "segmentId");
            if (d.getGlossary() != null && d.getGlossary() > 0) {
                try {
                    Integer requiredSegment = segmentValidationService.getRequiredDatasetSegmentFromGlossary(d.getGlossary(), datasetIsPublic);
                    // If glossary requires a specific segment, use it (unless user explicitly set a different one)
                    if (requiredSegment != null) {
                        // If user didn't specify a segment, use the one from glossary
                        if (segmentId == null) {
                            segmentId = requiredSegment;
                        }
                        // If user specified a segment, validate it matches glossary requirement
                        else if (segmentId != requiredSegment) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            JsonObject err = new JsonObject();
                            err.addProperty("status", "error");
                            String glossarySegName = segmentValidationService.getSegmentName(requiredSegment);
                            err.addProperty("message", "The dataset segment must match the glossary segment. The selected glossary is in segment '" + glossarySegName + "', so the dataset must also be in segment '" + glossarySegName + "'.");
                            response.getWriter().write(err.toString());
                            return;
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Error getting required segment from glossary: " + e.getMessage());
                }
            }
            
            // Default to Enterprise if no segment determined
            if (segmentId == null) segmentId = 1;

            // Enforce Dataset/System segment rule (with user access validation)
            var dsSys = segmentValidationService.validateDatasetSystemSegment(d.getMasterSource(), segmentId, userId);
            if (!dsSys.isValid) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("status", "error");
                err.addProperty("message", dsSys.message);
                response.getWriter().write(err.toString());
                return;
            }

            // Enforce Dataset/Glossary segment rule (with public/private check and user access validation)
            var dsGlo = segmentValidationService.validateDatasetGlossarySegment(d.getGlossary(), segmentId, datasetIsPublic, userId);
            if (!dsGlo.isValid) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("status", "error");
                err.addProperty("message", dsGlo.message);
                response.getWriter().write(err.toString());
                return;
            }

            // Visibility rule: REMOVED - datasets can now be public regardless of system segment
            // Previously: dataset cannot be Public if system is private segment
            // if (d.getMasterSource() != null && d.getMasterSource() > 0) {
            //     var vis = segmentValidationService.validateDatasetVisibility(0, d.getMasterSource(), datasetIsPublic);
            //     if (!vis.isValid) {
            //         response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            //         JsonObject err = new JsonObject();
            //         err.addProperty("status", "error");
            //         err.addProperty("message", vis.message);
            //         response.getWriter().write(err.toString());
            //         return;
            //     }
            // }

            int newId = datasetService.createDataset(d, userId);

            // Assign dataset to segment (default to Enterprise if not specified)
            try {
                segmentDAO.assignObjectToSegment(segmentId, newId, "Dataset", userId);
                //system.out.println("✅ Dataset " + newId + " assigned to segment " + segmentId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning dataset to segment: " + e.getMessage());
                // Continue - don't fail the entire save
            }

            // Assign creator role to the user for the new dataset
            if (userId > 0) {
                //system.out.println("✅ Calling assignCreatorRole for dataset ID: " + newId + ", userId: " + userId);
                try {
                    assignCreatorRole(newId, userId);
                    //system.out.println("✅ assignCreatorRole completed successfully");
                } catch (Exception e) {
                    System.err.println("❌ Error assigning creator role: " + e.getMessage());
                    e.printStackTrace();
                    // Continue - don't fail the entire save
                }
            } else {
                System.err.println("⚠️ No valid userId in session, skipping role assignment");
                System.err.println("⚠️ UserContextUtil.getCurrentUserId returned: " + userId);
            }


            // Auto-create change request if DF_CR workflow is enabled
            Integer changeRequestId = null;
            try {
                // Get the dataset type ID
                Integer typeId = d.getDatasetType();
                changeRequestId = dfcrService.applyDefaultsOnCreate("Data Set", newId, typeId, userId, isAdmin);
                if (changeRequestId != null) {
                    logger.info("Auto-created change request {} for new Data Set {} (typeId: {})", changeRequestId, newId, typeId);
                }
            } catch (Exception e) {
                // Example: Log error with full context using AxonLogger (BUDG logging utility)
                AxonLogger.logError("Error auto-creating change request for Data Set", e,
                    kv("component", "BUDG-core"),
                    kv("module", "Dataset"),
                    kv("action", "createChangeRequest"),
                    kv("dataset_id", newId),
                    kv("type_id", d.getDatasetType()),
                    kv("user_id", userId)
                );
                // Don't fail the main operation if CR creation fails
            }
            
            // Example: Audit log for dataset creation
            AxonLogger.logAudit("Dataset created",
                kv("component", "BUDG-core"),
                kv("module", "Dataset"),
                kv("action", "createDataset"),
                kv("dataset_id", newId),
                kv("dataset_name", d.getPrimaryName()),
                kv("user_id", userId),
                kv("change_request_id", changeRequestId)
            );

            JsonObject resp = new JsonObject();
            resp.addProperty("status", "success");
            resp.addProperty("message", "Dataset created successfully");
            resp.addProperty("datasetId", newId);
            if (changeRequestId != null) {
                resp.addProperty("changeRequestId", changeRequestId);
                resp.addProperty("workflowEnabled", true);
            }
            response.getWriter().write(resp.toString());
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", e.getMessage());
            response.getWriter().write(err.toString());
        } catch (SQLException e) {
            // Example: Log database error with context
            int userId = UserContextUtil.getCurrentUserId(request);
            String userEmail = (String) request.getAttribute("userEmail");
            AxonLogger.setRequestData(request);
            AxonLogger.setUserData(userId, userEmail);
            
            AxonLogger.logError("Database error while creating dataset", e,
                kv("component", "BUDG-core"),
                kv("module", "Dataset"),
                kv("action", "createDataset"),
                kv("user_id", userId),
                kv("sql_state", e.getSQLState())
            );
            
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            String message = e.getMessage();
            err.addProperty("message", message != null ? message : "Database constraint violation or insert failed");
            response.getWriter().write(err.toString());
        } catch (Exception e) {
            // Example: Log unexpected error
            int userId = UserContextUtil.getCurrentUserId(request);
            String userEmail = (String) request.getAttribute("userEmail");
            AxonLogger.setRequestData(request);
            AxonLogger.setUserData(userId, userEmail);
            
            AxonLogger.logError("Unexpected error while creating dataset", e,
                kv("component", "BUDG-core"),
                kv("module", "Dataset"),
                kv("action", "createDataset"),
                kv("user_id", userId)
            );
            
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Unexpected error");
            response.getWriter().write(err.toString());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.info("[DatasetServlet] ========== doPut START ==========");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        logger.info("[DatasetServlet] doPut called with pathInfo: {}", pathInfo);
        if (pathInfo == null || !pathInfo.matches("^/\\d+/?$")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid dataset id\"}");
            return;
        }
        int id = Integer.parseInt(pathInfo.replace("/", ""));
        try {
            // Check role-based edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Data Sets", id)) {
                return; // Response already sent
            }
            // Check if dataset is locked before allowing update
            if (!LockUtil.checkLockBeforeEdit(request, response, "dataset", id)) {
                return; // Lock check failed, response already sent
            }

            int userId = UserContextUtil.getCurrentUserId(request);

            // Check segment-based edit permission
            if (userId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(userId, id, "Dataset");
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write(
                            "{\"success\":false,\"message\":\"Access denied. You don't have permission to edit this dataset.\"}");
                    return;
                }
            }

            JsonObject json = JsonUtil.parseJsonFromRequest(request.getReader());
            Dataset d = new Dataset();
            d.setId(id);
            d.setPrimaryName(JsonUtil.getJsonString(json, "primaryName"));
            d.setMasterSource(JsonUtil.getJsonInt(json, "masterSource"));
            d.setRefNumber(JsonUtil.getJsonString(json, "refNumber"));
            d.setDefinition(JsonUtil.getJsonString(json, "definition"));
            d.setGlossary(JsonUtil.getJsonInt(json, "glossary"));
            d.setUsage(JsonUtil.getJsonString(json, "usage"));
            d.setStatus(JsonUtil.getJsonInt(json, "status"));
            d.setDatasetType(JsonUtil.getJsonInt(json, "datasetType"));
            d.setAccessControlType(JsonUtil.getJsonInt(json, "accessControlType"));
            d.setLifecycle(JsonUtil.getJsonInt(json, "lifecycle"));

            // Validate RefNumber uniqueness for update (exclude current ID)
            if (d.getRefNumber() != null && !d.getRefNumber().trim().isEmpty()) {
                try (Connection conn = DatabaseConnection.getConnection()) {
                    DatasetDAO datasetDAO = new DatasetDAO();
                    if (!datasetDAO.isRefNumberUniqueForUpdate(d.getRefNumber(), id)) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        JsonObject err = new JsonObject();
                                    err.addProperty("error", "This reference number is already in use. Please enter a unique reference number.");
                        err.addProperty("status", 400);
                        err.addProperty("field", "refNumber");
                        response.getWriter().write(err.toString());
                        return;
                    }
                } catch (SQLException e) {
                    logger.error("Error validating RefNumber uniqueness", e);
                    // Continue with update if validation fails (don't block update)
                }
            }

            // Primary name: unique per system (same as create); allow identical names on different systems
            if (d.getPrimaryName() != null && !d.getPrimaryName().isBlank()) {
                try (Connection conn = DatabaseConnection.getConnection()) {
                    String nameCheckSql;
                    java.sql.PreparedStatement ps;
                    if (d.getMasterSource() == null) {
                        nameCheckSql = "SELECT 1 FROM dataset WHERE LOWER(PrimaryName) = LOWER(?) AND DeletedDatetime IS NULL AND ID != ? AND MasterSource IS NULL LIMIT 1";
                        ps = conn.prepareStatement(nameCheckSql);
                        ps.setString(1, d.getPrimaryName());
                        ps.setInt(2, id);
                    } else {
                        nameCheckSql = "SELECT 1 FROM dataset WHERE LOWER(PrimaryName) = LOWER(?) AND MasterSource = ? AND DeletedDatetime IS NULL AND ID != ? LIMIT 1";
                        ps = conn.prepareStatement(nameCheckSql);
                        ps.setString(1, d.getPrimaryName());
                        ps.setInt(2, d.getMasterSource());
                        ps.setInt(3, id);
                    }
                    try (ps) {
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                JsonObject err = new JsonObject();
                                err.addProperty("error", "Name already exists");
                                err.addProperty("status", 400);
                                err.addProperty("field", "primaryName");
                                response.getWriter().write(err.toString());
                                return;
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.error("Error validating dataset name uniqueness on update", e);
                }
            }

            // ⚠️ CRITICAL: Only check for automatic CRs (mandatory_workflow = true)
            // Manual CRs should NOT trigger pending changes logic - they work like other facets
            // Check if this object has an active auto-created CR
            // If so, clone the row and update the clone instead of updating directly
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_ID, id);
            logger.info("[DatasetServlet UPDATE] Checking for active CR for dataset {} (facetId={})", id, DATASET_FACET_ID);
            logger.info("[DatasetServlet UPDATE] Active CR result: {}", activeCrId);
            
            // Check if we need to create a new CR for this user
            boolean shouldCheckForNewCR = true;
            if (activeCrId != null) {
                // Check if the existing CR was created by the current user
                try (Connection conn = DatabaseConnection.getConnection()) {
                    String checkSql = "SELECT Created_By FROM changerequest WHERE ID = ?";
                    try (java.sql.PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                        stmt.setInt(1, activeCrId);
                        try (java.sql.ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                int crCreatedBy = rs.getInt("Created_By");
                                if (crCreatedBy == userId) {
                                    // Same user - use existing CR, don't create new one
                                    logger.info("[DatasetServlet UPDATE] Active CR {} belongs to current user {}, using existing CR", activeCrId, userId);
                                    shouldCheckForNewCR = false;
                                } else {
                                    // Different user - need to create new CR
                                    logger.info("[DatasetServlet UPDATE] Active CR {} belongs to different user {} (current: {}), will create new CR", 
                                        activeCrId, crCreatedBy, userId);
                                    activeCrId = null; // Reset to allow new CR creation
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("[DatasetServlet UPDATE] Error checking CR owner: {}", e.getMessage());
                    // Continue with check
                }
            }
            
            // If no CR exists OR existing CR belongs to different user, check if DFCR edit workflow is enabled and auto-create CR
            if (shouldCheckForNewCR) {
                try {
                    logger.info("[DatasetServlet UPDATE] Checking DFCR settings for new CR...");
                    // Check if user is admin for bypass logic
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                    logger.info("[DatasetServlet UPDATE] User isAdmin: {}", isAdmin);
                    
                    // Get dataset type for type-specific workflow settings
                    Integer datasetType = d.getDatasetType(); // Use the type from the form submission
                    
                    Integer autoCrId = dfcrService.applyDefaultsOnEdit("Data Set", id, datasetType, userId, isAdmin);
                    if (autoCrId != null) {
                        logger.info("[DatasetServlet UPDATE] DFCR auto-created CR: {}", autoCrId);
                        activeCrId = autoCrId;
                    } else {
                        logger.info("[DatasetServlet UPDATE] DFCR did not create CR (workflow not enabled or admin bypass)");
                    }
                } catch (Exception e) {
                    logger.error("[DatasetServlet UPDATE] Error checking/creating DFCR CR: {}", e.getMessage(), e);
                    // Continue without CR - will update directly
                }
            }
            
            if (activeCrId != null) {
                // Check CR status - if it's Pending Start or Running, prevent editing
                try (Connection conn = DatabaseConnection.getConnection()) {
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
                                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                        JsonObject error = new JsonObject();
                                        error.addProperty("success", false);
                                        error.addProperty("message", 
                                            "Cannot edit this object. There is an active Change Request (status: " + statusName + 
                                            ") that must be completed or cancelled before editing is allowed.");
                                        error.addProperty("changeRequestId", activeCrId);
                                        error.addProperty("locked", true);
                                        response.getWriter().write(error.toString());
                                        logger.warn("Dataset {} edit blocked - active CR {} with status: {}", id, activeCrId, statusName);
                                        return;
                                    }
                                    // If status is "Pending Start", allow editing (workflow hasn't started yet)
                                    logger.info("Dataset {} edit allowed - CR {} is in Pending Start status, workflow hasn't started yet", id, activeCrId);
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error checking CR status: {}", e.getMessage());
                    // Continue - don't block if we can't check status
                }
                
                // Object is under revision - clone row and update clone
                logger.info("Dataset {} has active CR {} - cloning and updating", id, activeCrId);
                
                try {
                    // Get or create mapping for 'summary' area
                    Integer nobjectId = facetChangesDAO.getNObjectId("dataset", id, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // First edit - clone the row
                        nobjectId = cloneDatasetRow(id);
                        if (nobjectId == null) {
                            throw new SQLException("Failed to clone dataset row");
                        }
                        // Create mapping
                        facetChangesDAO.saveMapping("dataset", id, nobjectId, "summary", activeCrId);
                    }
                    
                    // Update the cloned row
                    d.setId(nobjectId);
                    boolean ok = datasetService.updateDataset(d, userId);
                    if (!ok) throw new SQLException("Update failed");
                    
                    // Return success but indicate changes are pending
                    JsonObject resp = new JsonObject();
                    resp.addProperty("success", true);
                    resp.addProperty("id", id);
                    resp.addProperty("message", "Changes saved as pending. They will apply when the Change Request is completed.");
                    resp.addProperty("pendingChanges", true);
                    resp.addProperty("changeRequestId", activeCrId);
                    response.getWriter().write(resp.toString());
                    return;
                } catch (SQLException e) {
                    logger.error("Error handling pending changes: {}", e.getMessage());
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.getWriter().write("{\"success\":false,\"message\":\"Database error: " + e.getMessage().replace("\"","\\\"") + "\"}");
                    return;
                }
            }
            
            // No active CR - apply changes directly to database
            // Check if dataset is public (AccessControlType == 1)
            boolean datasetIsPublic = d.getAccessControlType() != null && d.getAccessControlType() == 1;

            // Determine desired segment: If glossary is selected, derive segment from glossary
            Integer desiredSegmentId = JsonUtil.getJsonInt(json, "segmentId");
            if (d.getGlossary() != null && d.getGlossary() > 0) {
                try {
                    Integer requiredSegment = segmentValidationService.getRequiredDatasetSegmentFromGlossary(d.getGlossary(), datasetIsPublic);
                    // If glossary requires a specific segment, use it (unless user explicitly set a different one)
                    if (requiredSegment != null) {
                        // If user didn't specify a segment, use the one from glossary
                        if (desiredSegmentId == null) {
                            desiredSegmentId = requiredSegment;
                        }
                        // If user specified a segment, validate it matches glossary requirement
                        else if (desiredSegmentId != requiredSegment) {
                            String glossarySegName = segmentValidationService.getSegmentName(requiredSegment);
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            response.getWriter().write("{\"success\":false,\"message\":\"The dataset segment must match the glossary segment. The selected glossary is in segment '" + glossarySegName + "', so the dataset must also be in segment '" + glossarySegName + "'.\"}");
                            return;
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Error getting required segment from glossary: " + e.getMessage());
                }
            }
            
            // Use current segment if not provided
            if (desiredSegmentId == null) {
                desiredSegmentId = segmentDAO.getObjectSegmentId(id, "Dataset");
            }

            // Enforce Dataset/System segment rule (with user access validation)
            var dsSys = segmentValidationService.validateDatasetSystemSegment(d.getMasterSource(), desiredSegmentId, userId);
            if (!dsSys.isValid) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\":false,\"message\":\"" + dsSys.message.replace("\"","\\\"") + "\"}");
                return;
            }

            // Enforce Dataset/Glossary segment rule (with public/private check and user access validation)
            var dsGlo = segmentValidationService.validateDatasetGlossarySegment(d.getGlossary(), desiredSegmentId, datasetIsPublic, userId);
            if (!dsGlo.isValid) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\":false,\"message\":\"" + dsGlo.message.replace("\"","\\\"") + "\"}");
                return;
            }

            // Visibility rule: REMOVED - datasets can now be public regardless of system segment
            // Previously: dataset cannot be Public if system is private segment
            // if (d.getMasterSource() != null && d.getMasterSource() > 0) {
            //     var vis = segmentValidationService.validateDatasetVisibility(id, d.getMasterSource(), datasetIsPublic);
            //     if (!vis.isValid) {
            //         response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            //         response.getWriter().write("{\"success\":false,\"message\":\"" + vis.message.replace("\"","\\\"") + "\"}");
            //         return;
            //     }
            // }

            boolean ok = datasetService.updateDataset(d, userId);
            if (!ok) throw new SQLException("Update failed");
            
            // Track MasterSource (system link) changes for system_changes pending tracking
            if (d.getMasterSource() != null && d.getMasterSource() > 0) {
                int systemId = d.getMasterSource();
                try {
                    // Check for active automatic CR on the linked system
                    Integer systemActiveCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(3, systemId); // 3 = SYSTEM_FACET_ID
                    if (systemActiveCrId != null) {
                        // Save mapping for this dataset->system relationship
                        String areaKey = "relationships#dataset_mastersource";
                        facetChangesDAO.saveMapping("system", systemId, id, areaKey, systemActiveCrId);
                    }
                } catch (SQLException e) {
                    System.err.println("Error saving dataset MasterSource mapping for pending changes: " + e.getMessage());
                }
            }
            
            // Update segment assignment if provided
            Integer segmentId = JsonUtil.getJsonInt(json, "segmentId");
            if (segmentId != null) {
                try {
                    // Get current segment
                    int currentSegmentId = segmentDAO.getObjectSegmentId(id, "Dataset");

                    // If segment changed, validate full segment-move rules before changing
                    if (currentSegmentId != segmentId) {
                        var validationResult = segmentValidationService.validateSegmentMove(id, segmentId, "Dataset", null);
                        if (!validationResult.isValid) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            response.getWriter().write("{\"success\":false,\"error\":\"" + validationResult.message.replace("\"","\\\"") + "\"}");
                            return;
                        }
                        
                        if (currentSegmentId > 0) {
                            segmentDAO.removeObjectFromSegment(currentSegmentId, id, "Dataset", userId);
                        }
                        segmentDAO.assignObjectToSegment(segmentId, id, "Dataset", userId);
                        System.out.println(
                                "✅ Dataset " + id + " segment changed from " + currentSegmentId + " to " + segmentId);

                        // Enforce: all Attributes under this Dataset must inherit the Dataset segment
                        try {
                            java.util.List<Integer> attrIds = attributeDAO.getAttributeIdsByDatasetId(id);
                            for (Integer attrId : attrIds) {
                                if (attrId == null) continue;
                                int currentAttrSeg = segmentDAO.getObjectSegmentId(attrId, "Attribute");
                                if (currentAttrSeg != segmentId) {
                                    if (currentAttrSeg > 0) {
                                        segmentDAO.removeObjectFromSegment(currentAttrSeg, attrId, "Attribute", userId);
                                    }
                                    segmentDAO.assignObjectToSegment(segmentId, attrId, "Attribute", userId);
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("❌ Error syncing Attribute segments to Dataset segment: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error updating dataset segment: " + e.getMessage());
                    // Continue - don't fail the entire save
                }
            }

            JsonObject resp = new JsonObject();
            resp.addProperty("success", true);
            response.getWriter().write(resp.toString());
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter()
                    .write("{\"success\":false,\"message\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            String msg = e.getMessage() != null ? e.getMessage() : "Database error";
            response.getWriter().write("{\"success\":false,\"message\":\"" + msg.replace("\"", "\\\"") + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false,\"message\":\"Unexpected error\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.matches("^/\\d+/?$")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid dataset id\"}");
            return;
        }
        int id = Integer.parseInt(pathInfo.replace("/", ""));
        try {
            boolean ok = datasetService.deleteDataset(id, request);
            JsonObject resp = new JsonObject();
            resp.addProperty("success", ok);
            response.getWriter().write(resp.toString());
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            String msg = e.getMessage() != null ? e.getMessage() : "Database error";
            response.getWriter().write("{\"success\":false,\"message\":\"" + msg.replace("\"", "\\\"") + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false,\"message\":\"Unexpected error\"}");
        }
    }

    private String getUserFullName(int userId) {
        try {
            String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
            try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                    java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        return rs.getString("fullName");
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting user full name: " + e.getMessage());
        }
        return null;
    }

    private void assignCreatorRole(int datasetId, int userId) throws SQLException {
        try (java.sql.Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int moduleId = DefaultStakeholderUtil.getModuleId(conn, "Data Sets");
                java.util.List<Integer> rolesToAssign = DefaultStakeholderUtil.getDefaultRolesForCreator(conn, moduleId, userId);

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
                                throw new SQLException("Failed to insert into object_x_people");
                            }
                            try (java.sql.ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                                if (generatedKeys.next()) {
                                    objectXPeopleId = generatedKeys.getInt(1);
                                } else {
                                    throw new SQLException("Failed to get generated key for object_x_people");
                                }
                            }
                        }

                        String insertDXOP = """
                                INSERT INTO dataset_x_objectxpeople (Object_x_ipid, Dataset_ID, Last_UpdateUser_ID)
                                VALUES (?, ?, ?)
                                """;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertDXOP)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, datasetId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) {
                                throw new SQLException("Failed to insert into dataset_x_objectxpeople");
                            }
                        }

                        RoleNotificationHelper.createNotificationAfterCreatorRoleAssigned(
                                "Data Set", datasetId, userId, roleId, objectXPeopleId, conn);

                        if (userFullName != null) {
                            datasetDAO.createStakeholderAuditRecord(datasetId, userFullName, userFullName, roleId);
                        }
                    } catch (SQLException e) {
                        System.err.println("❌ Error assigning default role " + roleId + " to creator: " + e.getMessage());
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error in assignCreatorRole: " + e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unused")
    private Integer getNextObjectXPeopleId(java.sql.Connection conn) throws SQLException {
        String query = "SELECT COALESCE(MAX(ID), 0) + 1 FROM object_x_people";
        try (java.sql.PreparedStatement stmt = conn.prepareStatement(query);
                java.sql.ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 1; // fallback
    }
    
    /**
     * Handle clone dataset request
     * POST /api/dataset/{id}/clone
     */
    private void handleCloneDataset(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.matches("^/\\d+/clone/?$")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid clone endpoint\"}");
            return;
        }
        
        // Extract dataset ID from path: /{id}/clone
        String idStr = pathInfo.replace("/clone", "").replace("/", "");
        if (idStr.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Missing dataset id\"}");
            return;
        }
        
        try {
            int originalId = Integer.parseInt(idStr);
            int userId = UserContextUtil.getCurrentUserId(request);
            
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\"}");
                return;
            }
            
            // Check segment-based access
            boolean canAccess = SegmentAccessService.canAccessObject(userId, originalId, "Dataset");
            if (!canAccess) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"success\":false,\"message\":\"Access denied\"}");
                return;
            }
            
            // Clone the dataset
            Integer clonedId = cloneDatasetWithAttributes(originalId, userId);
            
            if (clonedId == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"success\":false,\"message\":\"Failed to clone dataset\"}");
                return;
            }
            
            JsonObject resp = new JsonObject();
            resp.addProperty("success", true);
            resp.addProperty("id", clonedId);
            resp.addProperty("message", "Dataset cloned successfully");
            response.getWriter().write(resp.toString());
            
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid dataset id\"}");
        } catch (SQLException e) {
            logger.error("Error cloning dataset: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String errorMsg = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Database error";
            response.getWriter().write("{\"success\":false,\"message\":\"" + errorMsg + "\"}");
        } catch (Exception e) {
            logger.error("Unexpected error cloning dataset: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false,\"message\":\"Unexpected error\"}");
        }
    }

    /**
     * Clone a dataset with all its attributes
     * Returns the ID of the cloned dataset, or null on error
     */
    private Integer cloneDatasetWithAttributes(int originalId, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Step 1: Generate a new unique reference number for the cloned dataset
            String newRefNumber = ReferenceNumberGenerator.generateDatasetRefNumber();
            
            // Step 2: Clone the dataset row using the same connection with new reference number
            String cloneSql = "INSERT INTO dataset (" +
                    "RefNumber, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                    "status, DatasetType, AccessControlType, lifecycle, " +
                    "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID" +
                    ") SELECT " +
                    "?, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                    "status, DatasetType, AccessControlType, lifecycle, " +
                    "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID " +
                    "FROM dataset WHERE ID = ?";
            
            Integer clonedDatasetId = null;
            try (java.sql.PreparedStatement ps = conn.prepareStatement(cloneSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, newRefNumber);
                ps.setInt(2, originalId);
                int rowsAffected = ps.executeUpdate();
                
                if (rowsAffected > 0) {
                    try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            clonedDatasetId = rs.getInt(1);
                        }
                    }
                }
            }
            
            if (clonedDatasetId == null) {
                throw new SQLException("Failed to clone dataset row");
            }
            
            // Step 3: Update the cloned dataset name to add "(clone)" suffix
            java.util.Map<String, Object> originalDataset = datasetDAO.getById(originalId);
            if (originalDataset != null) {
                String originalName = (String) originalDataset.get("name");
                if (originalName == null) {
                    originalName = (String) originalDataset.get("primaryName");
                }
                if (originalName == null) {
                    originalName = (String) originalDataset.get("PrimaryName");
                }
                if (originalName != null) {
                    String clonedName = originalName + " (clone)";
                    
                    // Update the cloned dataset name
                    String updateSql = "UPDATE dataset SET PrimaryName = ?, LastUpdateUser_ID = ?, LastUpdateDatetime = NOW() WHERE ID = ?";
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, clonedName);
                        ps.setInt(2, userId);
                        ps.setInt(3, clonedDatasetId);
                        ps.executeUpdate();
                    }
                }
            }
            
            // Step 4: Clone VALUE UPDATES & AVAILABILITY (values_datastore) from original dataset
            String getValuesDatastoreSql = "SELECT frequency, frequency_comments, availability, availability_comments, values_in_axon " +
                    "FROM values_datastore WHERE dataset_id = ? LIMIT 1";
            try (java.sql.PreparedStatement getValuesPs = conn.prepareStatement(getValuesDatastoreSql)) {
                getValuesPs.setInt(1, originalId);
                try (java.sql.ResultSet valuesRs = getValuesPs.executeQuery()) {
                    if (valuesRs.next()) {
                        // Insert cloned values_datastore record
                        String insertValuesSql = "INSERT INTO values_datastore (dataset_id, frequency, frequency_comments, availability, availability_comments, values_in_axon, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())";
                        try (java.sql.PreparedStatement insertValuesPs = conn.prepareStatement(insertValuesSql)) {
                            insertValuesPs.setInt(1, clonedDatasetId);
                            insertValuesPs.setString(2, valuesRs.getString("frequency"));
                            insertValuesPs.setString(3, valuesRs.getString("frequency_comments"));
                            insertValuesPs.setString(4, valuesRs.getString("availability"));
                            insertValuesPs.setString(5, valuesRs.getString("availability_comments"));
                            Object viaObj = valuesRs.getObject("values_in_axon");
                            if (viaObj != null) {
                                insertValuesPs.setBoolean(6, valuesRs.getBoolean("values_in_axon"));
                            } else {
                                insertValuesPs.setNull(6, java.sql.Types.BOOLEAN);
                            }
                            insertValuesPs.executeUpdate();
                        }
                    }
                }
            }
            
            // Step 5: Clone all attributes from original dataset to cloned dataset
            // Get all attribute IDs for the original dataset (using correct column name: DeletedDatetime)
            java.util.List<Integer> attributeIds = new java.util.ArrayList<>();
            String getAttrIdsSql = "SELECT ID FROM attribute WHERE Dataset_ID = ? AND DeletedDatetime IS NULL";
            try (java.sql.PreparedStatement getIdsPs = conn.prepareStatement(getAttrIdsSql)) {
                getIdsPs.setInt(1, originalId);
                try (java.sql.ResultSet rs = getIdsPs.executeQuery()) {
                    while (rs.next()) {
                        attributeIds.add(rs.getInt("ID"));
                    }
                }
            }
            
            // Insert attributes directly using the same connection to maintain transaction
            String insertAttrSql = """
                INSERT INTO attribute (
                    Is_PrimaryKey, Requirement_ID, Business_Logic, RefNumber, PrimaryName, 
                    Definition, Glossary_ID, Origination, Editability, Editability_role, 
                    Data_type_ID, DataLength, Dataset_ID, CreatedBy, CreatedDatetime, Last_UpdateDatetime
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NULL)
                """;
            
            for (Integer attrId : attributeIds) {
                // Get full attribute data from database
                String getAttrSql = """
                    SELECT Is_PrimaryKey, Requirement_ID, Business_Logic, RefNumber, PrimaryName, 
                           Definition, Glossary_ID, Origination, Editability, Editability_role, 
                           Data_type_ID, DataLength, Confidence_score
                    FROM attribute 
                    WHERE ID = ? AND DeletedDatetime IS NULL
                    """;
                
                try (java.sql.PreparedStatement getPs = conn.prepareStatement(getAttrSql)) {
                    getPs.setInt(1, attrId);
                    try (java.sql.ResultSet rs = getPs.executeQuery()) {
                        if (rs.next()) {
                            // Insert the cloned attribute using the same connection
                            try (java.sql.PreparedStatement insertPs = conn.prepareStatement(insertAttrSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                                setNullableInt(insertPs, 1, rs.getObject("Is_PrimaryKey"));
                                setNullableInt(insertPs, 2, rs.getObject("Requirement_ID"));
                                setNullableString(insertPs, 3, rs.getString("Business_Logic"));
                                setNullableString(insertPs, 4, rs.getString("RefNumber"));
                                setNullableString(insertPs, 5, rs.getString("PrimaryName"));
                                setNullableString(insertPs, 6, rs.getString("Definition"));
                                setNullableInt(insertPs, 7, rs.getObject("Glossary_ID"));
                                setNullableInt(insertPs, 8, rs.getObject("Origination"));
                                setNullableInt(insertPs, 9, rs.getObject("Editability"));
                                setNullableInt(insertPs, 10, rs.getObject("Editability_role"));
                                setNullableInt(insertPs, 11, rs.getObject("Data_type_ID"));
                                setNullableInt(insertPs, 12, rs.getObject("DataLength"));
                                insertPs.setInt(13, clonedDatasetId);
                                insertPs.setInt(14, userId);
                                
                                insertPs.executeUpdate();
                            }
                        }
                    }
                }
            }
            
            // Step 6: Assign segment to cloned dataset (same as original)
            try {
                Integer originalSegmentId = segmentDAO.getObjectSegmentId(originalId, "Dataset");
                if (originalSegmentId != null) {
                    segmentDAO.assignObjectToSegment(originalSegmentId, clonedDatasetId, "Dataset", userId);
                }
            } catch (Exception e) {
                logger.warn("Failed to assign segment to cloned dataset: {}", e.getMessage());
                // Continue - segment assignment is not critical
            }
            
            conn.commit();
            return clonedDatasetId;
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Helper method to set nullable integer in PreparedStatement
     */
    private void setNullableInt(java.sql.PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else if (value instanceof Number) {
            ps.setInt(index, ((Number) value).intValue());
        } else {
            try {
                ps.setInt(index, Integer.parseInt(value.toString()));
            } catch (NumberFormatException e) {
                ps.setNull(index, java.sql.Types.INTEGER);
            }
        }
    }

    /**
     * Helper method to set nullable string in PreparedStatement
     */
    private void setNullableString(java.sql.PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    /**
     * Clone a dataset row for pending changes
     * Returns the ID of the cloned row, or null on error
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
     * Get attributes with pending changes for View Changes mode
     * Returns original attributes + pending ones (marked with pending: true)
     */
    private java.util.List<java.util.Map<String, Object>> getAttributesWithPendingChanges(int datasetId) throws SQLException {
        // Only check for automatic CRs for pending changes
        Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(DATASET_FACET_ID, datasetId);
        if (activeCrId == null) {
            return attributeDAO.getAttributesByDatasetId(datasetId);
        }
        
        // Get all pending (newly added) attribute IDs from the mapping
        String areaKey = "summary#attribute";
        java.util.List<Integer> pendingIds = facetChangesDAO.getAllNObjectIds("dataset", datasetId, areaKey, activeCrId);
        java.util.Set<Integer> pendingIdSet = new java.util.HashSet<>(pendingIds);
        
        // Start with original attributes (exclude pending ones that were added during CR)
        java.util.List<java.util.Map<String, Object>> originalAttributes = attributeDAO.getAttributesByDatasetId(datasetId);
        java.util.List<java.util.Map<String, Object>> allAttributes = new java.util.ArrayList<>();
        
        // Add original attributes (those not in pendingIds are truly original)
        for (java.util.Map<String, Object> attr : originalAttributes) {
            // Try both "ID" (uppercase) and "id" (lowercase) for compatibility
            Object idObj = attr.get("ID");
            if (idObj == null) {
                idObj = attr.get("id");
            }
            if (idObj != null) {
                int attrId = ((Number) idObj).intValue();
                if (!pendingIdSet.contains(attrId)) {
                    // This is an original attribute (existed before CR)
                    allAttributes.add(attr);
                }
            } else {
                allAttributes.add(attr);
            }
        }
        
        // Add pending attributes (newly added during CR)
        for (Integer pendingId : pendingIds) {
            java.util.Map<String, Object> pending = attributeDAO.getAttributeMapById(pendingId);
            if (pending != null) {
                pending.put("pending", true); // Mark as pending change
                allAttributes.add(pending);
            }
        }
        
        return allAttributes;
    }
    
    /**
     * Get only original attributes (exclude pending changes) for View Original mode.
     * Shows all attributes that belong to the original dataset (Dataset_ID = datasetId).
     * 
     * Newly added attributes during an active EDIT CR are automatically excluded because
     * they are inserted with Dataset_ID = clonedDatasetId, so getAttributesByDatasetId(originalDatasetId)
     * won't return them.
     * 
     * Updated attributes (edited in-place during EDIT CR) still have Dataset_ID = originalDatasetId,
     * so they correctly appear in View Original.
     */
    private java.util.List<java.util.Map<String, Object>> getOriginalAttributes(int datasetId) throws SQLException {
        return attributeDAO.getAttributesByDatasetId(datasetId);
    }
}


