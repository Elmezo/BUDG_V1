package com.example.budg_v2;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Process;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.ProcessService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RequestedSegmentFilterUtil;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet(name = "ProcessServlet", urlPatterns = {"/api/process", "/api/process/*"})
public class ProcessServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ProcessServlet.class);
    private static final int PROCESS_FACET_ID = 4; // Process facet ID in module table
    private final ProcessService processService;
    private final SegmentDAO segmentDAO;
    private final FacetChangesDAO facetChangesDAO;
    private final DFCRService dfcrService;
    private final SegmentValidationService segmentValidationService;

    public ProcessServlet() {
        this.processService = new ProcessService();
        this.segmentDAO = new SegmentDAO();
        this.segmentValidationService = new SegmentValidationService();
        this.facetChangesDAO = new FacetChangesDAO();
        this.dfcrService = new DFCRService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();

            if ("/list".equals(pathInfo)) {
                // Return simplified list with id, primaryname and description for dropdown
                //system.out.println("ProcessServlet: Getting process list for dropdown");
                int userId = UserContextUtil.getCurrentUserId(request);
                List<Process> processes = userId > 0 ? 
                    processService.getAllProcessesForDropdown(userId) : 
                    processService.getAllProcessesForDropdown();
                processes = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        processes,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Process",
                        Process::getId);
                //system.out.println("ProcessServlet: Retrieved " + processes.size() + " processes (userId: " + userId + ")");
                
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Process p : processes) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", p.getId());
                    o.addProperty("primaryname", p.getPrimaryName());
                    o.addProperty("description", p.getDescription());
                    arr.add(o);
                    //system.out.println("ProcessServlet: Added process to response: " + p.getPrimaryName());
                }
                response.getWriter().write(arr.toString());
            } else if ("/next-ref".equals(pathInfo)) {
                JsonObject refResponse = new JsonObject();
                refResponse.addProperty("success", true);
                refResponse.addProperty("refnumber", processService.getNextProcessRefNumber());
                response.getWriter().write(refResponse.toString());
            } else if ("/parent-picker".equals(pathInfo)) {
                // Return processes for parent picker (excluding current process)
                String excludeIdParam = request.getParameter("excludeId");
                int excludeId = excludeIdParam != null ? Integer.parseInt(excludeIdParam) : 0;
                int userId = UserContextUtil.getCurrentUserId(request);
                
                //system.out.println("ProcessServlet: Getting parent picker processes, excludeId: " + excludeId + ", userId: " + userId);
                List<Process> processes = userId > 0 ? 
                    processService.getProcessesForParentPicker(excludeId, userId) : 
                    processService.getProcessesForParentPicker(excludeId);
                processes = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        processes,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Process",
                        Process::getId);
                //system.out.println("ProcessServlet: Retrieved " + processes.size() + " parent processes");
                
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Process p : processes) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", p.getId());
                    o.addProperty("primaryname", p.getPrimaryName());
                    o.addProperty("description", p.getDescription());
                    arr.add(o);
                    //system.out.println("ProcessServlet: Added parent process to response: " + p.getPrimaryName());
                }
                response.getWriter().write(arr.toString());
            } else if ("/search".equals(pathInfo)) {
                String searchQuery = request.getParameter("q");
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    searchProcesses(response, searchQuery.trim());
                } else {
                    getAllProcesses(response);
                }
            } else if (pathInfo != null && pathInfo.startsWith("/hierarchy/")) {
                // Get process hierarchy
                String idParam = pathInfo.substring("/hierarchy/".length());
                if (idParam.matches("\\d+")) {
                    getProcessHierarchy(request, response, Integer.parseInt(idParam));
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                }
            } else if (pathInfo != null && pathInfo.length() > 1) {
                String idParam = pathInfo.substring(1);
                if (idParam.matches("\\d+")) {
                    String view = request.getParameter("view");
                    getProcessById(request, response, Integer.parseInt(idParam), view);
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                }
            } else {
                getAllProcesses(request, response);
            }
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        // Check create permission for new process creation
        if (!PermissionCheckUtil.checkCreatePermission(request, response, "Process")) {
            return; // Response already sent
        }

        try {
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryname");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer parentId = JsonUtil.getJsonInt(jsonData, "parent_id");
            // Convert 0 to null (0 means no parent in database)
            if (parentId != null && parentId == 0) {
                parentId = null;
            }
            Integer status = JsonUtil.getJsonInt(jsonData, "status");
            Integer type = JsonUtil.getJsonInt(jsonData, "type");
            Integer durationType = JsonUtil.getJsonInt(jsonData, "duration_type");
            Integer duration = JsonUtil.getJsonInt(jsonData, "duration");
            Integer lifecycleStatus = JsonUtil.getJsonInt(jsonData, "lifecycle_status");
            Integer processClassId = JsonUtil.getJsonInt(jsonData, "processclass_id");
            Integer processAutomationId = JsonUtil.getJsonInt(jsonData, "processautomation_id");
            String refNumber = JsonUtil.getJsonString(jsonData, "refnumber");
            String inputDescription = JsonUtil.getJsonString(jsonData, "input_description");
            String outputDescription = JsonUtil.getJsonString(jsonData, "output_description");
            Integer stepType = JsonUtil.getJsonInt(jsonData, "step_type");
            Integer isPublic = JsonUtil.getJsonInt(jsonData, "ispublic");
            Integer canCreate = JsonUtil.getJsonInt(jsonData, "cancreate");
            Integer canRead = JsonUtil.getJsonInt(jsonData, "canread");
            Integer canUpdate = JsonUtil.getJsonInt(jsonData, "canupdate");
            Integer canDelete = JsonUtil.getJsonInt(jsonData, "candelete");
            Integer canArchive = JsonUtil.getJsonInt(jsonData, "canarchive");
            
            // Try both field name variations for user ID
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastupdateuser_id");
            if (lastUpdateUserId == null) {
                lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastupdate_userid");
            }
            
            // If still null, get from request context (from JWT token or session)
            if (lastUpdateUserId == null) {
                int userIdFromContext = UserContextUtil.getCurrentUserId(request);
                if (userIdFromContext > 0) {
                    lastUpdateUserId = userIdFromContext;
                    //system.out.println("ProcessServlet (CREATE): Using user ID from request context = " + lastUpdateUserId);
                } else {
                    //system.out.println("ProcessServlet (CREATE): ERROR - No authenticated user found");
                    JsonUtil.sendErrorResponse(response.getWriter(), "User authentication required", 401);
                    return;
                }
            }

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            Integer segmentIdForName = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segmentIdForName == null) {
                segmentIdForName = 1;
            }
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Process", primaryName.trim(),
                        segmentIdForName.longValue(), null)) {
                    JsonUtil.sendErrorResponse(response.getWriter(),
                            "A process with this name already exists in the selected segment.", 400);
                    return;
                }
            }

            // Validate segment hierarchy before creating process
            Integer segmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segmentId == null) segmentId = 1;
            if (parentId != null && parentId > 0) {
                try {
                    var hierarchyResult = segmentValidationService.validateParentChildSegment(parentId, segmentId, "Process");
                    if (!hierarchyResult.isValid) {
                        JsonUtil.sendErrorResponse(response.getWriter(), hierarchyResult.message, 400);
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Error validating process hierarchy: " + e.getMessage());
                    JsonUtil.sendErrorResponse(response.getWriter(), "Error validating segment hierarchy: " + e.getMessage(), 500);
                    return;
                }
            }

            Process newProcess = processService.createProcess(
                    primaryName.trim(), description, parentId, status, type, durationType, duration,
                    lifecycleStatus, processClassId, processAutomationId, refNumber,
                    inputDescription, outputDescription, stepType, isPublic, 
                    canCreate, canRead, canUpdate, canDelete, canArchive, lastUpdateUserId, request);
            try {
                segmentDAO.assignObjectToSegment(segmentId, newProcess.getId(), "Process", lastUpdateUserId != null ? lastUpdateUserId : 1);
                //system.out.println("✅ Process " + newProcess.getId() + " assigned to segment " + segmentId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning process to segment: " + e.getMessage());
            }

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Process created successfully");
            successResponse.add("data", JsonParser.parseString(JsonUtil.toJson(newProcess)));

            response.getWriter().write(successResponse.toString());

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error creating process: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Process ID is required for update", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int processId = Integer.parseInt(idParam);
            
            // Check role-based edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryname");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer parentId = JsonUtil.getJsonInt(jsonData, "parent_id");
            logger.info("[ProcessServlet UPDATE] Received parent_id from JSON: {}", parentId);
            // Convert 0 to null (0 means no parent in database)
            if (parentId != null && parentId == 0) {
                parentId = null;
                logger.info("[ProcessServlet UPDATE] Converted parent_id 0 to null");
            }
            logger.info("[ProcessServlet UPDATE] Final parentId value: {}", parentId);
            Integer status = JsonUtil.getJsonInt(jsonData, "status");
            Integer type = JsonUtil.getJsonInt(jsonData, "type");
            Integer durationType = JsonUtil.getJsonInt(jsonData, "duration_type");
            Integer duration = JsonUtil.getJsonInt(jsonData, "duration");
            Integer lifecycleStatus = JsonUtil.getJsonInt(jsonData, "lifecycle_status");
            Integer processClassId = JsonUtil.getJsonInt(jsonData, "processclass_id");
            Integer processAutomationId = JsonUtil.getJsonInt(jsonData, "processautomation_id");
            String refNumber = JsonUtil.getJsonString(jsonData, "refnumber");
            String inputDescription = JsonUtil.getJsonString(jsonData, "input_description");
            String outputDescription = JsonUtil.getJsonString(jsonData, "output_description");
            Integer stepType = JsonUtil.getJsonInt(jsonData, "step_type");
            Integer isPublic = JsonUtil.getJsonInt(jsonData, "ispublic");
            Integer canCreate = JsonUtil.getJsonInt(jsonData, "cancreate");
            Integer canRead = JsonUtil.getJsonInt(jsonData, "canread");
            Integer canUpdate = JsonUtil.getJsonInt(jsonData, "canupdate");
            Integer canDelete = JsonUtil.getJsonInt(jsonData, "candelete");
            Integer canArchive = JsonUtil.getJsonInt(jsonData, "canarchive");
            
            // Get current user ID from session (like PolicyServlet does)
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            Integer lastUpdateUserId = currentUserId > 0 ? currentUserId : 1; // Use current user or default

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            // Check segment-based edit permission
            if (currentUserId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(currentUserId, processId, "Process");
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonUtil.sendErrorResponse(response.getWriter(), "Access denied. You don't have permission to edit this process.", 403);
                    return;
                }
            }

            Integer reqSeg = JsonUtil.getJsonInt(jsonData, "segmentId");
            int curSeg = segmentDAO.getObjectSegmentId(processId, "Process");
            long effSeg = (reqSeg != null) ? reqSeg.longValue() : (curSeg > 0 ? curSeg : 1L);

            // Check if this object has an active auto-created CR (only automatic CRs use pending changes)
            // If so, clone the row and update the clone instead of updating directly
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(PROCESS_FACET_ID, processId);
            
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
                                if (crCreatedBy == lastUpdateUserId) {
                                    // Same user - use existing CR, don't create new one
                                    //system.out.println("[ProcessServlet UPDATE] Active CR " + activeCrId + " belongs to current user " + lastUpdateUserId + ", using existing CR");
                                    shouldCheckForNewCR = false;
                                } else {
                                    // Different user - need to create new CR
                                    //system.out.println("[ProcessServlet UPDATE] Active CR " + activeCrId + " belongs to different user " + crCreatedBy + " (current: " + lastUpdateUserId + "), will create new CR");
                                    activeCrId = null; // Reset to allow new CR creation
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("[ProcessServlet UPDATE] Error checking CR owner: " + e.getMessage());
                    // Continue with check
                }
            }
            
            // If no CR exists OR existing CR belongs to different user, check if DFCR edit workflow is enabled and auto-create CR
            if (shouldCheckForNewCR) {
                try {
                    //system.out.println("[ProcessServlet UPDATE] Checking DFCR settings for new CR...");
                    // Check if user is admin for bypass logic
                    boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                    //system.out.println("[ProcessServlet UPDATE] User isAdmin: " + isAdmin);
                    
                    // Get process type for type-specific workflow settings
                    Integer processType = type; // Use the type from the form submission
                    
                    Integer autoCrId = dfcrService.applyDefaultsOnEdit("Process", processId, processType, lastUpdateUserId, isAdmin);
                    if (autoCrId != null) {
                        //system.out.println("[ProcessServlet UPDATE] DFCR auto-created CR: " + autoCrId);
                        activeCrId = autoCrId;
                    } else {
                        //system.out.println("[ProcessServlet UPDATE] DFCR did not create CR (workflow not enabled or admin bypass)");
                    }
                } catch (Exception e) {
                    System.err.println("[ProcessServlet UPDATE] Error checking/creating DFCR CR: " + e.getMessage());
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
                                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                        JsonUtil.sendErrorResponse(response.getWriter(), 
                                            "Cannot edit this object. There is an active Change Request (status: " + statusName + 
                                            ") that must be completed or cancelled before editing is allowed.", 403);
                                        logger.warn("Process {} edit blocked - active CR {} with status: {}", processId, activeCrId, statusName);
                                        return;
                                    }
                                    // If status is "Pending Start", allow editing (workflow hasn't started yet)
                                    logger.info("Process {} edit allowed - CR {} is in Pending Start status, workflow hasn't started yet", processId, activeCrId);
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error checking CR status: {}", e.getMessage());
                    // Continue - don't block if we can't check status
                }
                
                // Object is under revision - clone row and update clone
                //system.out.println("Process " + processId + " has active CR " + activeCrId + " - cloning and updating");
                
                try {
                    // Get or create mapping for 'summary' area
                    Integer nobjectId = facetChangesDAO.getNObjectId("process", processId, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // First edit - clone the row
                        nobjectId = cloneProcessRow(processId);
                        if (nobjectId == null) {
                            throw new SQLException("Failed to clone process row");
                        }
                        // Create mapping
                        facetChangesDAO.saveMapping("process", processId, nobjectId, "summary", activeCrId);
                    }

                    try (Connection conn = DatabaseConnection.getConnection()) {
                        if (SegmentScopedPrimaryNameCheck.exists(conn, "Process", primaryName.trim(), effSeg, nobjectId)) {
                            JsonUtil.sendErrorResponse(response.getWriter(),
                                    "A process with this name already exists in this segment.", 400);
                            return;
                        }
                    }
                    
                    // Update the cloned row
                    boolean updated = processService.updateProcess(
                            nobjectId, primaryName.trim(), description, parentId, status, type, durationType, duration,
                            lifecycleStatus, processClassId, processAutomationId, refNumber,
                            inputDescription, outputDescription, stepType, isPublic, 
                            canCreate, canRead, canUpdate, canDelete, canArchive, lastUpdateUserId);
                    
                    if (!updated) {
                        throw new SQLException("Update failed");
                    }
                    
                    // Return success but indicate changes are pending
                    JsonObject resp = new JsonObject();
                    resp.addProperty("success", true);
                    resp.addProperty("id", processId);
                    resp.addProperty("message", "Changes saved as pending. They will apply when the Change Request is completed.");
                    resp.addProperty("pendingChanges", true);
                    resp.addProperty("changeRequestId", activeCrId);
                    response.getWriter().write(resp.toString());
                    return;
                } catch (SQLException e) {
                    System.err.println("Error handling pending changes: " + e.getMessage());
                    JsonUtil.sendErrorResponse(response.getWriter(), "Database error: " + e.getMessage(), 500);
                    return;
                }
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Process", primaryName.trim(), effSeg, processId)) {
                    JsonUtil.sendErrorResponse(response.getWriter(),
                            "A process with this name already exists in this segment.", 400);
                    return;
                }
            }
            
            // No active CR - apply changes directly to database
            boolean updated = processService.updateProcess(
                    processId, primaryName.trim(), description, parentId, status, type, durationType, duration,
                    lifecycleStatus, processClassId, processAutomationId, refNumber,
                    inputDescription, outputDescription, stepType, isPublic, 
                    canCreate, canRead, canUpdate, canDelete, canArchive, lastUpdateUserId);

            if (updated) {
                // Update segment assignment if provided
                Integer segmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
                if (segmentId != null) {
                    try {
                        int currentSegmentId = segmentDAO.getObjectSegmentId(processId, "Process");
                        if (currentSegmentId != segmentId) {
                            // Validate full segment move rules before changing segment
                            com.example.budg_v2.service.SegmentValidationService validationService = new com.example.budg_v2.service.SegmentValidationService();
                            var validationResult = validationService.validateSegmentMove(
                                processId,
                                segmentId,
                                "Process",
                                parentId
                            );
                            if (!validationResult.isValid) {
                                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                JsonObject error = new JsonObject();
                                error.addProperty("success", false);
                                error.addProperty("error", validationResult.message);
                                response.getWriter().write(error.toString());
                                return;
                            }
                            
                            if (currentSegmentId > 0) {
                                segmentDAO.removeObjectFromSegment(currentSegmentId, processId, "Process", lastUpdateUserId);
                            }
                            segmentDAO.assignObjectToSegment(segmentId, processId, "Process", lastUpdateUserId);
                            //system.out.println("✅ Process " + processId + " segment changed from " + currentSegmentId + " to " + segmentId);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error updating process segment: " + e.getMessage());
                    }
                }

                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Process updated successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Process not found or update failed", 404);
            }

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error updating process: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Process ID is required for deletion", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int processId = Integer.parseInt(idParam);
            boolean deleted = processService.deleteProcess(processId, request);

            if (deleted) {
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Process deleted successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Process not found or deletion failed", 404);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error deleting process: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllProcesses(HttpServletResponse response) throws IOException, SQLException {
        List<Process> processes = processService.getAllProcesses();
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", processes.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(processes)));
        response.getWriter().write(jsonResponse.toString());
    }

    private void getAllProcesses(HttpServletRequest request, HttpServletResponse response) throws IOException, SQLException {
        int userId = UserContextUtil.getCurrentUserId(request);
        List<Process> processes = userId > 0 ? processService.getAllProcesses(userId) : processService.getAllProcesses();
        processes = RequestedSegmentFilterUtil.filterByRequestedSegment(
                processes,
                RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                "Process",
                Process::getId);
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", processes.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(processes)));
        response.getWriter().write(jsonResponse.toString());
    }

    private void getProcessById(HttpServletRequest request, HttpServletResponse response, int id, String view) throws IOException, SQLException {
        // Check if viewing changes (pending changes mode)
        int processIdToLoad = id;
        
        //system.out.println("[ProcessServlet] getProcessById called: id=" + id + ", view=" + view);
        
        if ("changes".equals(view)) {
            try {
                // Only check for automatic CRs for pending changes view
                Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(PROCESS_FACET_ID, id);
                //system.out.println("[ProcessServlet] Active CR for process " + id + ": " + activeCrId);
                
                if (activeCrId != null) {
                    Integer nobjectId = facetChangesDAO.getNObjectId("process", id, "summary", activeCrId);
                    //system.out.println("[ProcessServlet] Existing nobject_id for summary: " + nobjectId);
                    
                    if (nobjectId == null) {
                        // First time switching to changes for this process: clone + create mapping
                        //system.out.println("[ProcessServlet] No mapping found, cloning process row...");
                        nobjectId = cloneProcessRow(id);
                        //system.out.println("[ProcessServlet] Cloned row ID: " + nobjectId);
                        
                        if (nobjectId != null) {
                            facetChangesDAO.saveMapping("process", id, nobjectId, "summary", activeCrId);
                            //system.out.println("[ProcessServlet] Saved mapping: object_id=" + id + ", nobject_id=" + nobjectId + ", area_key=summary, cr_id=" + activeCrId);
                        } else {
                            System.err.println("[ProcessServlet] ERROR: cloneProcessRow returned null!");
                        }
                    }
                    if (nobjectId != null) {
                        processIdToLoad = nobjectId;
                        //system.out.println("[ProcessServlet] Will load process from cloned ID: " + processIdToLoad);
                    }
                } else {
                    //system.out.println("[ProcessServlet] No active CR found for process " + id + " - loading original");
                }
            } catch (SQLException e) {
                // If error getting mapping, fall back to original id
                System.err.println("[ProcessServlet] Error getting nobject_id for view=changes: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        Process process = processService.getProcessById(processIdToLoad);
        if (process != null) {
            if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(process.getStatusName())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"This object is not available.\"}");
                return;
            }
            // Check access control for all users (including guests)
            int userId = UserContextUtil.getCurrentUserId(request);

            // GUEST ACCESS CHECK: Only allow public objects in Enterprise segment
            if (userId <= 0) {
                // Guest user - check if object is public and in Enterprise segment
                try {
                    boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "Process");
                    if (!canAccess) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("{\"error\":\"Access denied. This resource is not publicly accessible.\"}");
                        return;
                    }
                } catch (SQLException e) {
                    // On error, deny access for safety
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. Unable to verify access permissions.\"}");
                    return;
                }
            }

            // Authenticated user access check
            if (userId > 0) {
                boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "Process");
                if (!canAccess) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. You don't have permission to view this process.\"}");
                    return;
                }
            }

            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", true);
            JsonObject dataJson = JsonParser.parseString(JsonUtil.toJson(process)).getAsJsonObject();
            // Add segment information
            SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, processIdToLoad, "Process");
            SegmentResponseUtil.applySegmentInfo(dataJson, segmentInfo, request);
            jsonResponse.add("data", dataJson);
            response.getWriter().write(jsonResponse.toString());
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Process not found", 404);
        }
    }

    private void getProcessHierarchy(HttpServletRequest request, HttpServletResponse response, int processId) throws IOException, SQLException {
        List<Process> hierarchy = processService.getProcessHierarchy(processId);
        com.google.gson.JsonArray dataArr = JsonParser.parseString(JsonUtil.toJson(hierarchy)).getAsJsonArray();
        int userId = UserContextUtil.getCurrentUserId(request);
        com.example.budg_v2.util.HierarchyAccessMasker.mask(dataArr, "Process", userId);
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", hierarchy.size());
        jsonResponse.add("data", dataArr);
        response.getWriter().write(jsonResponse.toString());
    }

    private void searchProcesses(HttpServletResponse response, String searchQuery) throws IOException, SQLException {
        List<Process> processes = processService.searchProcesses(searchQuery);
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", processes.size());
        jsonResponse.addProperty("searchQuery", searchQuery);
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(processes)));
        response.getWriter().write(jsonResponse.toString());
    }
    
    /**
     * Clone a process row for pending changes
     * Returns the ID of the cloned row, or null on error
     */
    private Integer cloneProcessRow(int originalId) throws SQLException {
        String sql = "INSERT INTO process (" +
                "primaryname, description, parentid, status, type, duration_type, duration, " +
                "lifecycle_status, processclass_id, processautomation_id, refnumber, " +
                "input_description, output_description, step_type, ispublic, " +
                "cancreate, canread, canupdate, candelete, canarchive, " +
                "createdby_id, createdatetime, lastupdatedatetime, lastupdateuser_id" +
                ") SELECT " +
                "primaryname, description, parentid, status, type, duration_type, duration, " +
                "lifecycle_status, processclass_id, processautomation_id, refnumber, " +
                "input_description, output_description, step_type, ispublic, " +
                "cancreate, canread, canupdate, candelete, canarchive, " +
                "createdby_id, createdatetime, lastupdatedatetime, lastupdateuser_id " +
                "FROM process WHERE id = ?";
        
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
