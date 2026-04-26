package com.example.budg_v2;

import com.example.budg_v2.dao.CRChangesReviewDAO;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Servlet for Pending Changes API (new model using facet-specific changes tables).
 * 
 * Endpoints:
 * GET /api/pending-changes/status/{facetType}/{objectId}
 *     - Check if object has active CR and pending changes
 *     - Returns: { underRevision: boolean, changeRequestId: int|null, hasPendingChanges: boolean }
 * 
 * GET /api/pending-changes/mappings/{facetType}/{objectId}
 *     - Get all area mappings for an object (which areas have changes and their nobject_ids)
 *     - Returns: { success: true, mappings: { area_key: nobject_id, ... } }
 * 
 * GET /api/pending-changes/cr/{crId}
 *     - Get all pending changes for a specific CR (for CHANGES TO REVIEW section)
 *     - Returns: { success: true, changes: [ { facetType, objectId, objectName, tabName, operation, fieldName, oldValue, newValue, userName } ] }
 * 
 * POST /api/pending-changes/apply/{crId}
 *     - Apply all pending changes for a CR (copy from nobject_id to object_id, then cleanup)
 *     - Returns: { success: true, appliedCount: number }
 * 
 * POST /api/pending-changes/discard/{crId}
 *     - Discard all pending changes for a CR (delete mappings and cloned rows)
 *     - Returns: { success: true, discardedCount: number }
 */
@WebServlet("/api/pending-changes/*")
public class PendingChangesServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(PendingChangesServlet.class);
    
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private final CRChangesReviewDAO crChangesReviewDAO = new CRChangesReviewDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        logger.debug("PendingChangesServlet GET: {}", pathInfo);

        if (pathInfo == null || pathInfo.equals("/")) {
            sendError(resp, "Invalid endpoint", 400);
            return;
        }

        String[] parts = pathInfo.split("/");
        
        try {
            // GET /api/pending-changes/status/{facetType}/{objectId}
            if (parts.length >= 4 && "status".equals(parts[1])) {
                String facetType = parts[2];
                int objectId = Integer.parseInt(parts[3]);
                handleGetStatus(resp, facetType, objectId);
            }
            // GET /api/pending-changes/mappings/{facetType}/{objectId}
            else if (parts.length >= 4 && "mappings".equals(parts[1])) {
                String facetType = parts[2];
                int objectId = Integer.parseInt(parts[3]);
                handleGetMappings(resp, facetType, objectId);
            }
            // GET /api/pending-changes/cr/{crId}
            else if (parts.length >= 3 && "cr".equals(parts[1])) {
                int crId = Integer.parseInt(parts[2]);
                handleGetChangesForCR(resp, crId);
            }
            // GET /api/pending-changes/stakeholder-edit-permission/{facetType}/{objectId}
            else if (parts.length >= 4 && "stakeholder-edit-permission".equals(parts[1])) {
                String facetType = parts[2];
                int objectId = Integer.parseInt(parts[3]);
                handleGetStakeholderEditPermission(resp, facetType, objectId);
            }
            else {
                sendError(resp, "Invalid endpoint", 400);
            }

        } catch (NumberFormatException e) {
            logger.error("Invalid ID format", e);
            sendError(resp, "Invalid ID format", 400);
        } catch (SQLException e) {
            logger.error("Database error in PendingChangesServlet", e);
            // If table doesn't exist, return empty status instead of 500
            try {
                if (pathInfo != null && pathInfo.contains("/status/")) {
                    if (parts.length >= 4) {
                        String facetType = parts[2];
                        int objectId = Integer.parseInt(parts[3]);
                        JsonObject response = new JsonObject();
                        response.addProperty("success", true);
                        response.addProperty("facetType", facetType);
                        response.addProperty("objectId", objectId);
                        response.addProperty("underRevision", false);
                        response.addProperty("hasPendingChanges", false);
                        response.addProperty("error", "Table not initialized: " + e.getMessage());
                        resp.getWriter().write(gson.toJson(response));
                        return;
                    }
                }
            } catch (Exception ex) {
                logger.error("Error creating fallback response", ex);
            }
            sendError(resp, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.error("Unexpected error in PendingChangesServlet", e);
            sendError(resp, "Internal server error: " + e.getMessage(), 500);
        }
    }

    /**
     * GET /api/pending-changes/status/{facetType}/{objectId}
     * Check if object is under revision (has active auto-created CR)
     */
    private void handleGetStatus(HttpServletResponse resp, String facetTypeName, int objectId) throws IOException, SQLException {
        logger.info("GET /api/pending-changes/status/{}/{}", facetTypeName, objectId);
        //system.out.println("[PendingChangesServlet] handleGetStatus called: facetType=" + facetTypeName + ", objectId=" + objectId);
        
        Integer facetId = facetChangesDAO.getFacetId(facetTypeName);
        //system.out.println("[PendingChangesServlet] facetId for " + facetTypeName + ": " + facetId);
        if (facetId == null) {
            sendError(resp, "Unknown facet type: " + facetTypeName, 400);
            return;
        }

        // Check for active automatic CR only (pending changes only work with automatic CRs)
        Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(facetId, objectId);
        //system.out.println("[PendingChangesServlet] activeCrId for " + facetTypeName + " " + objectId + ": " + activeCrId);
        
        // ⚠️ NEW: Get CR ownership information
        Integer crCreatedBy = null;
        boolean isAutoCR = false;
        String crStatusName = null;
        if (activeCrId != null) {
            try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                String sql = "SELECT cr.Created_By, cr.mandatory_workflow, cr.PrimaryName as CRName, cr.Summary, cr.CR_StatusID, crs.PrimaryName as StatusName " +
                            "FROM changerequest cr " +
                            "LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID " +
                            "WHERE cr.ID = ? AND cr.Deleted_At IS NULL";
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, activeCrId);
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            crCreatedBy = rs.getInt("Created_By");
                            boolean mandatoryWorkflow = rs.getBoolean("mandatory_workflow");
                            String primaryName = rs.getString("CRName");
                            String summary = rs.getString("Summary");
                            crStatusName = rs.getString("StatusName");
                            
                            // Check if it's an auto-created CR
                            isAutoCR = mandatoryWorkflow || 
                                       (primaryName != null && primaryName.toLowerCase().contains("auto-generated cr for")) ||
                                       (summary != null && summary.toLowerCase().contains("auto-generated cr for"));
                            
                            logger.info("[PendingChangesServlet] CR {} ownership: createdBy={}, isAutoCR={}, statusName={}, primaryName={}, mandatoryWorkflow={}", 
                                activeCrId, crCreatedBy, isAutoCR, crStatusName, primaryName, mandatoryWorkflow);
                            
                            // ⚠️ SAFETY CHECK: If CR status is actually completed/cancelled/rejected/closed,
                            // treat it as if there's no active CR (release the object from restrictions)
                            if (crStatusName != null) {
                                String statusLower = crStatusName.toLowerCase();
                                if (statusLower.contains("complete") || statusLower.contains("cancelled") || 
                                    statusLower.contains("canceled") || statusLower.contains("reject") || 
                                    statusLower.contains("closed")) {
                                    logger.info("[PendingChangesServlet] CR {} is finished (status={}), releasing object from restrictions", 
                                        activeCrId, crStatusName);
                                    activeCrId = null; // Treat as no active CR
                                }
                            }
                        }
                    }
                }
            } catch (java.sql.SQLException e) {
                logger.warn("Error getting CR ownership info: {}", e.getMessage());
            }
        }
        
        // Check if there are any pending changes (any area has a mapping)
        // Exclude stakeholders mappings - stakeholders are excluded from pending changes
        boolean hasPendingChanges = false;
        if (activeCrId != null) {
            Map<String, Integer> allMappings = facetChangesDAO.getAllMappings(facetTypeName, objectId, activeCrId);
            // Filter out stakeholders mappings
            Map<String, Integer> mappings = new HashMap<>();
            for (Map.Entry<String, Integer> entry : allMappings.entrySet()) {
                String areaKey = entry.getKey();
                if (!areaKey.startsWith("stakeholders#")) {
                    mappings.put(entry.getKey(), entry.getValue());
                }
            }
            hasPendingChanges = !mappings.isEmpty();
            //system.out.println("[PendingChangesServlet] mappings found: " + mappings.size() + ", hasPendingChanges: " + hasPendingChanges);
        }
        
        boolean underRevision = (activeCrId != null);
        //system.out.println("[PendingChangesServlet] returning: underRevision=" + underRevision + ", hasPendingChanges=" + hasPendingChanges);
        
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("facetType", facetTypeName);
        response.addProperty("objectId", objectId);
        response.addProperty("underRevision", underRevision);
        response.addProperty("hasPendingChanges", hasPendingChanges);
        if (activeCrId != null) {
            response.addProperty("changeRequestId", activeCrId);
            // ⚠️ NEW: Add CR ownership info
            if (crCreatedBy != null) {
                response.addProperty("crCreatedBy", crCreatedBy);
            }
            response.addProperty("isAutoCR", isAutoCR);
            if (crStatusName != null) {
                response.addProperty("crStatusName", crStatusName);
            }
        }

        resp.getWriter().write(gson.toJson(response));
    }

    /**
     * GET /api/pending-changes/stakeholder-edit-permission/{facetType}/{objectId}
     * Check if stakeholders can be edited for this object.
     * Stakeholders can ALWAYS be edited, regardless of CR status.
     * This allows stakeholders to be edited even when Auto CR is in Running status.
     */
    private void handleGetStakeholderEditPermission(HttpServletResponse resp, String facetTypeName, int objectId) throws IOException, SQLException {
        logger.info("GET /api/pending-changes/stakeholder-edit-permission/{}/{}", facetTypeName, objectId);
        
        Integer facetId = facetChangesDAO.getFacetId(facetTypeName);
        if (facetId == null) {
            sendError(resp, "Unknown facet type: " + facetTypeName, 400);
            return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("facetType", facetTypeName);
        response.addProperty("objectId", objectId);
        
        // Check for active CR (for informational purposes only)
        Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(facetId, objectId);
        
        if (activeCrId != null) {
            response.addProperty("changeRequestId", activeCrId);
            
            // Get CR details for informational purposes
            try (Connection conn = DatabaseConnection.getConnection()) {
                String sql = "SELECT cr.CR_StatusID, crs.PrimaryName as StatusName, " +
                            "wi.Status as WorkflowStatus " +
                            "FROM changerequest cr " +
                            "LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID " +
                            "LEFT JOIN workflow_instance wi ON cr.Process_InstanceID = wi.ID " +
                            "WHERE cr.ID = ? AND cr.Deleted_At IS NULL";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, activeCrId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String crStatusName = rs.getString("StatusName");
                            String workflowStatus = rs.getString("WorkflowStatus");
                            
                            response.addProperty("crStatus", crStatusName != null ? crStatusName : "Unknown");
                            response.addProperty("workflowStatus", workflowStatus != null ? workflowStatus : "Unknown");
                        }
                    }
                }
            } catch (SQLException e) {
                logger.warn("Error fetching CR details for stakeholder permission (non-critical): {}", e.getMessage());
            }
        }
        
        // Stakeholders can ALWAYS be edited, regardless of CR status
        response.addProperty("canEdit", true);
        response.addProperty("reason", "Stakeholders can always be edited, even when Auto CR is running");
        resp.getWriter().write(gson.toJson(response));
    }
    
    /**
     * Check if there is an active task that leads to an end event with commitChanges=true
     */
    @SuppressWarnings("unused")
    private boolean checkForCommitChangesTask(Connection conn, int workflowInstanceId, Integer processDefId) throws SQLException {
        if (processDefId == null) {
            return false;
        }
        
        // Get BPMN XML for the workflow
        String bpmnXml = null;
        String getBpmnSql = "SELECT bpmn_xml FROM workflow_process_definition WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(getBpmnSql)) {
            stmt.setInt(1, processDefId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    bpmnXml = rs.getString("bpmn_xml");
                }
            }
        }
        
        if (bpmnXml == null || bpmnXml.isEmpty()) {
            return false;
        }
        
        // Get current active tasks
        String getTasksSql = "SELECT BPMN_Node_ID FROM workflow_task WHERE Workflow_Instance_ID = ? AND Status = 'PENDING'";
        List<String> activeNodeIds = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(getTasksSql)) {
            stmt.setInt(1, workflowInstanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String nodeId = rs.getString("BPMN_Node_ID");
                    if (nodeId != null) {
                        activeNodeIds.add(nodeId);
                    }
                }
            }
        }
        
        if (activeNodeIds.isEmpty()) {
            return false;
        }
        
        // Parse BPMN and check if any active task leads to an end event with commitChanges=true
        try {
            org.w3c.dom.Document doc = com.example.budg_v2.util.BpmnParser.parseXml(bpmnXml);
            
            for (String nodeId : activeNodeIds) {
                org.w3c.dom.Element taskElement = com.example.budg_v2.util.BpmnParser.findElementById(doc, nodeId);
                if (taskElement == null) continue;
                
                // Get outgoing sequence flows from the task element
                List<String> outgoingFlows = com.example.budg_v2.util.BpmnParser.getOutgoingFlows(taskElement);
                
                for (String flowId : outgoingFlows) {
                    // Get target of this flow
                    org.w3c.dom.Element flowElement = com.example.budg_v2.util.BpmnParser.findElementById(doc, flowId);
                    if (flowElement == null) continue;
                    
                    String targetRef = flowElement.getAttribute("targetRef");
                    if (targetRef == null || targetRef.isEmpty()) continue;
                    
                    org.w3c.dom.Element targetElement = com.example.budg_v2.util.BpmnParser.findElementById(doc, targetRef);
                    if (targetElement == null) continue;
                    
                    // Check if target is an end event
                    if (com.example.budg_v2.util.BpmnParser.isEndEvent(targetElement)) {
                        // Check commitChanges property
                        String commitChanges = targetElement.getAttribute("commitChanges");
                        if (commitChanges == null || commitChanges.isEmpty()) {
                            commitChanges = targetElement.getAttribute("camunda:commitChanges");
                        }
                        if (commitChanges == null || commitChanges.isEmpty()) {
                            Map<String, String> props = com.example.budg_v2.util.BpmnParser.extractEndEventProperties(targetElement);
                            commitChanges = props.get("commitChanges");
                        }
                        
                        if ("true".equalsIgnoreCase(commitChanges) || "1".equals(commitChanges)) {
                            logger.info("Found end event {} with commitChanges=true reachable from task {}", targetRef, nodeId);
                            return true;
                        }
                    }
                    
                    // Also check if target is a gateway leading to an end event with commitChanges
                    if (com.example.budg_v2.util.BpmnParser.isExclusiveGateway(targetElement)) {
                        // Check all outgoing paths from the gateway
                        List<String> gatewayOutflows = com.example.budg_v2.util.BpmnParser.getOutgoingFlows(targetElement);
                        for (String gwFlowId : gatewayOutflows) {
                            org.w3c.dom.Element gwFlowElement = com.example.budg_v2.util.BpmnParser.findElementById(doc, gwFlowId);
                            if (gwFlowElement == null) continue;
                            
                            String gwTargetRef = gwFlowElement.getAttribute("targetRef");
                            if (gwTargetRef == null || gwTargetRef.isEmpty()) continue;
                            
                            org.w3c.dom.Element gwTargetElement = com.example.budg_v2.util.BpmnParser.findElementById(doc, gwTargetRef);
                            if (gwTargetElement != null && com.example.budg_v2.util.BpmnParser.isEndEvent(gwTargetElement)) {
                                String commitChanges = gwTargetElement.getAttribute("commitChanges");
                                if (commitChanges == null || commitChanges.isEmpty()) {
                                    commitChanges = gwTargetElement.getAttribute("camunda:commitChanges");
                                }
                                if (commitChanges == null || commitChanges.isEmpty()) {
                                    Map<String, String> props = com.example.budg_v2.util.BpmnParser.extractEndEventProperties(gwTargetElement);
                                    commitChanges = props.get("commitChanges");
                                }
                                
                                if ("true".equalsIgnoreCase(commitChanges) || "1".equals(commitChanges)) {
                                    logger.info("Found end event {} with commitChanges=true reachable via gateway from task {}", gwTargetRef, nodeId);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing BPMN to check for commitChanges", e);
        }
        
        return false;
    }

    /**
     * GET /api/pending-changes/mappings/{facetType}/{objectId}
     * Get all area mappings for an object
     */
    private void handleGetMappings(HttpServletResponse resp, String facetTypeName, int objectId) throws IOException, SQLException {
        logger.info("GET /api/pending-changes/mappings/{}/{}", facetTypeName, objectId);
        //system.out.println("[PendingChangesServlet] handleGetMappings called: facetType=" + facetTypeName + ", objectId=" + objectId);
        
        Integer facetId = facetChangesDAO.getFacetId(facetTypeName);
        //system.out.println("[PendingChangesServlet] facetId resolved to: " + facetId);
        if (facetId == null) {
            sendError(resp, "Unknown facet type: " + facetTypeName, 400);
            return;
        }

        // Get active automatic CR to filter mappings (pending changes only work with automatic CRs)
        Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(facetId, objectId);
        //system.out.println("[PendingChangesServlet] activeCrId for " + facetTypeName + " " + objectId + ": " + activeCrId);
        
        Map<String, Integer> allMappings = facetChangesDAO.getAllMappings(facetTypeName, objectId, activeCrId);

        // Filter out stakeholders mappings - stakeholders are excluded from pending changes
        // Stakeholders changes are applied immediately even with active CR
        Map<String, Integer> mappings = new HashMap<>();
        for (Map.Entry<String, Integer> entry : allMappings.entrySet()) {
            String areaKey = entry.getKey();
            // Exclude stakeholders mappings (e.g., "stakeholders#glossary_stakeholder", "stakeholders#system_stakeholder", etc.)
            if (!areaKey.startsWith("stakeholders#")) {
                mappings.put(entry.getKey(), entry.getValue());
            }
        }

        // Process facet: if under revision but no mappings yet, auto-create the summary clone mapping.
        // This makes "View Changes" work immediately (like Glossary/System) even before the first explicit edit save.
        //system.out.println("[PendingChangesServlet] Checking auto-clone: activeCrId=" + activeCrId + 
         //   ", mappings.isEmpty=" + mappings.isEmpty() + ", facetType=" + facetTypeName);
        if (activeCrId != null && mappings.isEmpty() && "process".equalsIgnoreCase(facetTypeName)) {
            //system.out.println("[PendingChangesServlet] Auto-cloning process " + objectId + " for CR " + activeCrId);
            try (Connection conn = DatabaseConnection.getConnection()) {
                Integer existingSummary = facetChangesDAO.getNObjectId("process", objectId, "summary", activeCrId);
                //system.out.println("[PendingChangesServlet] Existing summary mapping: " + existingSummary);
                if (existingSummary == null) {
                    Integer clonedId = cloneProcessRow(conn, objectId);
                    //system.out.println("[PendingChangesServlet] Cloned process row, new ID: " + clonedId);
                    if (clonedId != null) {
                        facetChangesDAO.saveMapping("process", objectId, clonedId, "summary", activeCrId);
                        //system.out.println("[PendingChangesServlet] Saved mapping: object_id=" + objectId + ", nobject_id=" + clonedId + ", area_key=summary");
                        logger.info("[PendingChangesServlet] Auto-created process summary mapping: {} -> {} (CR {})", objectId, clonedId, activeCrId);
                    } else {
                        System.err.println("[PendingChangesServlet] ERROR: cloneProcessRow returned null!");
                    }
                }
            } catch (Exception e) {
                System.err.println("[PendingChangesServlet] ERROR auto-cloning: " + e.getMessage());
                e.printStackTrace();
                logger.warn("[PendingChangesServlet] Failed to auto-create process summary mapping for {}: {}", objectId, e.getMessage());
            }

            // Re-fetch mappings after potential creation and filter again
            allMappings = facetChangesDAO.getAllMappings(facetTypeName, objectId, activeCrId);
            mappings.clear();
            for (Map.Entry<String, Integer> entry : allMappings.entrySet()) {
                String areaKey = entry.getKey();
                if (!areaKey.startsWith("stakeholders#")) {
                    mappings.put(entry.getKey(), entry.getValue());
                }
            }
            //system.out.println("[PendingChangesServlet] Re-fetched mappings after clone: " + mappings);
        }

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("facetType", facetTypeName);
        response.addProperty("objectId", objectId);
        
        JsonObject mappingsJson = new JsonObject();
        for (Map.Entry<String, Integer> entry : mappings.entrySet()) {
            mappingsJson.addProperty(entry.getKey(), entry.getValue());
        }
        response.add("mappings", mappingsJson);
        response.addProperty("count", mappings.size());

        resp.getWriter().write(gson.toJson(response));
    }

    /**
     * Clone a process row for pending changes (same as ProcessServlet.cloneProcessRow) using an existing connection.
     */
    private Integer cloneProcessRow(Connection conn, int originalId) throws SQLException {
        String sql = "INSERT INTO process (" +
                "primaryname, description, parentid, ispublic, status, type, duration_type, duration, " +
                "lifecycle_status, processclass_id, processautomation_id, refnumber, " +
                "input_description, output_description, step_type, " +
                "cancreate, canread, canupdate, candelete, canarchive, " +
                "createdby_id, createdatetime, lastupdatedatetime, lastupdateuser_id" +
                ") SELECT " +
                "primaryname, description, parentid, ispublic, status, type, duration_type, duration, " +
                "lifecycle_status, processclass_id, processautomation_id, refnumber, " +
                "input_description, output_description, step_type, " +
                "cancreate, canread, canupdate, candelete, canarchive, " +
                "createdby_id, createdatetime, lastupdatedatetime, lastupdateuser_id " +
                "FROM process WHERE id = ?";

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

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        logger.info("PendingChangesServlet POST: {}", pathInfo);

        if (pathInfo == null || pathInfo.equals("/")) {
            sendError(resp, "Invalid endpoint", 400);
            return;
        }

        String[] parts = pathInfo.split("/");

        try {
            // POST /api/pending-changes/apply/{crId}
            if (parts.length >= 3 && "apply".equals(parts[1])) {
                int crId = Integer.parseInt(parts[2]);
                handleApplyChanges(resp, crId);
            }
            // POST /api/pending-changes/discard/{crId}
            else if (parts.length >= 3 && "discard".equals(parts[1])) {
                int crId = Integer.parseInt(parts[2]);
                handleDiscardChanges(resp, crId);
            }
            // POST /api/pending-changes/sync-status/{crId}
            // Sync CR status: check current status and apply/discard changes accordingly
            // Useful when CR status is changed directly in the database
            else if (parts.length >= 3 && "sync-status".equals(parts[1])) {
                int crId = Integer.parseInt(parts[2]);
                handleSyncStatus(resp, crId);
            }
            else {
                sendError(resp, "Invalid endpoint", 400);
            }

        } catch (NumberFormatException e) {
            logger.error("Invalid ID format", e);
            sendError(resp, "Invalid ID format", 400);
        } catch (SQLException e) {
            logger.error("Database error in PendingChangesServlet POST", e);
            sendError(resp, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.error("Unexpected error in PendingChangesServlet POST", e);
            sendError(resp, "Internal server error: " + e.getMessage(), 500);
        }
    }

    /**
     * POST /api/pending-changes/apply/{crId}
     * Apply all pending changes for a CR (copy from nobject_id to object_id, then cleanup)
     * This endpoint can be called manually after changing CR status directly in the database
     */
    private void handleApplyChanges(HttpServletResponse resp, int crId) throws IOException, SQLException {
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("🔄 [MANUAL APPLY] Applying pending changes for CR {} via API endpoint", crId);
        logger.info("═══════════════════════════════════════════════════════════════");
        
        try {
            com.example.budg_v2.service.FacetChangesService facetChangesService = 
                new com.example.budg_v2.service.FacetChangesService();
            facetChangesService.applyChangesForCR(crId);
            
            logger.info("✅ [MANUAL APPLY] Successfully applied all pending changes for CR {}", crId);
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Pending changes applied successfully");
            response.addProperty("changeRequestId", crId);
            
            resp.getWriter().write(gson.toJson(response));
        } catch (Exception e) {
            logger.error("❌ [MANUAL APPLY] Error applying pending changes for CR {}: {}", crId, e.getMessage(), e);
            
            JsonObject response = new JsonObject();
            response.addProperty("success", false);
            response.addProperty("message", "Error applying pending changes: " + e.getMessage());
            response.addProperty("changeRequestId", crId);
            
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(response));
        }
    }

    /**
     * POST /api/pending-changes/discard/{crId}
     * Discard all pending changes for a CR (delete mappings and cloned rows)
     * This endpoint can be called manually after changing CR status directly in the database
     */
    private void handleDiscardChanges(HttpServletResponse resp, int crId) throws IOException, SQLException {
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("🗑️  [MANUAL DISCARD] Discarding pending changes for CR {} via API endpoint", crId);
        logger.info("═══════════════════════════════════════════════════════════════");
        
        try {
            com.example.budg_v2.service.FacetChangesService facetChangesService = 
                new com.example.budg_v2.service.FacetChangesService();
            facetChangesService.discardChangesForCR(crId);
            
            logger.info("✅ [MANUAL DISCARD] Successfully discarded all pending changes for CR {}", crId);
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Pending changes discarded successfully");
            response.addProperty("changeRequestId", crId);
            
            resp.getWriter().write(gson.toJson(response));
        } catch (Exception e) {
            logger.error("❌ [MANUAL DISCARD] Error discarding pending changes for CR {}: {}", crId, e.getMessage(), e);
            
            JsonObject response = new JsonObject();
            response.addProperty("success", false);
            response.addProperty("message", "Error discarding pending changes: " + e.getMessage());
            response.addProperty("changeRequestId", crId);
            
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(response));
        }
    }

    /**
     * POST /api/pending-changes/sync-status/{crId}
     * Sync CR status: check current status and apply/discard changes accordingly
     * This endpoint is useful when CR status is changed directly in the database
     * It will automatically call applyChangesForCR or discardChangesForCR based on the current status
     */
    private void handleSyncStatus(HttpServletResponse resp, int crId) throws IOException, SQLException {
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("🔄 [SYNC STATUS] Syncing status for CR {} - checking current status and applying/discarding changes", crId);
        logger.info("═══════════════════════════════════════════════════════════════");
        
        try {
            // Get current CR status
            Integer completedStatusId = getStatusIdByName("Completed");
            Integer cancelledStatusId = getStatusIdByName("Cancelled");
            if (cancelledStatusId == null) {
                cancelledStatusId = getStatusIdByName("Canceled"); // Alternative spelling
            }
            Integer deletedStatusId = getStatusIdByName("Deleted");
            
            // Get CR current status
            Integer currentStatusId = null;
            String statusName = null;
            try (Connection conn = DatabaseConnection.getConnection()) {
                String sql = "SELECT cr.CR_StatusID, cs.PrimaryName as StatusName " +
                           "FROM changerequest cr " +
                           "LEFT JOIN changerequeststatus cs ON cr.CR_StatusID = cs.ID " +
                           "WHERE cr.ID = ? AND cr.Deleted_At IS NULL";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, crId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            currentStatusId = rs.getObject("CR_StatusID", Integer.class);
                            statusName = rs.getString("StatusName");
                        } else {
                            logger.warn("⚠️  [SYNC STATUS] CR {} not found or deleted", crId);
                            JsonObject response = new JsonObject();
                            response.addProperty("success", false);
                            response.addProperty("message", "CR not found or deleted");
                            response.addProperty("changeRequestId", crId);
                            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                            resp.getWriter().write(gson.toJson(response));
                            return;
                        }
                    }
                }
            }
            
            logger.info("   📋 CR {} current status: ID={}, Name={}", crId, currentStatusId, statusName);
            
            com.example.budg_v2.service.FacetChangesService facetChangesService = 
                new com.example.budg_v2.service.FacetChangesService();
            
            // Check if status is Completed
            if (completedStatusId != null && currentStatusId != null && currentStatusId.equals(completedStatusId)) {
                logger.info("   ✅ Status is Completed - applying pending changes...");
                facetChangesService.applyChangesForCR(crId);
                logger.info("✅ [SYNC STATUS] Successfully applied all pending changes for CR {}", crId);
                
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "CR status is Completed - pending changes applied successfully");
                response.addProperty("changeRequestId", crId);
                response.addProperty("action", "applied");
                response.addProperty("statusId", currentStatusId);
                response.addProperty("statusName", statusName);
                resp.getWriter().write(gson.toJson(response));
                return;
            }
            
            // Check if status is Cancelled or Deleted
            if ((cancelledStatusId != null && currentStatusId != null && currentStatusId.equals(cancelledStatusId)) ||
                (deletedStatusId != null && currentStatusId != null && currentStatusId.equals(deletedStatusId))) {
                logger.info("   ❌ Status is {} - discarding pending changes...", statusName);
                facetChangesService.discardChangesForCR(crId);
                logger.info("✅ [SYNC STATUS] Successfully discarded all pending changes for CR {}", crId);
                
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "CR status is " + statusName + " - pending changes discarded successfully");
                response.addProperty("changeRequestId", crId);
                response.addProperty("action", "discarded");
                response.addProperty("statusId", currentStatusId);
                response.addProperty("statusName", statusName);
                resp.getWriter().write(gson.toJson(response));
                return;
            }
            
            // Status is neither Completed nor Cancelled/Deleted
            logger.info("   ℹ️  Status is {} - no action needed (CR is still active)", statusName);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "CR status is " + (statusName != null ? statusName : "active") + " - no action needed");
            response.addProperty("changeRequestId", crId);
            response.addProperty("action", "none");
            response.addProperty("statusId", currentStatusId);
            response.addProperty("statusName", statusName);
            resp.getWriter().write(gson.toJson(response));
            
        } catch (Exception e) {
            logger.error("❌ [SYNC STATUS] Error syncing status for CR {}: {}", crId, e.getMessage(), e);
            
            JsonObject response = new JsonObject();
            response.addProperty("success", false);
            response.addProperty("message", "Error syncing status: " + e.getMessage());
            response.addProperty("changeRequestId", crId);
            
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(response));
        }
    }
    
    /**
     * Helper method to get status ID by name
     */
    private Integer getStatusIdByName(String statusName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT ID FROM changerequeststatus WHERE PrimaryName LIKE ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "%" + statusName + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("ID");
                    }
                }
            }
        }
        return null;
    }

    /**
     * GET /api/pending-changes/cr/{crId}
     * Get all pending changes for a specific CR to display in CHANGES TO REVIEW section.
     * First checks if changes are stored in cr_changes_review table.
     * If found, returns them directly. Otherwise, calculates from mappings and saves to review table.
     */
    /**
     * Calculate all pending changes for a CR and save them to cr_changes_review.
     * This is a public method that can be called from other servlets (e.g., before cancel/complete)
     * to ensure all changes are persisted in cr_changes_review before cloned rows are deleted.
     */
    public void calculateAndSaveChanges(int crId) throws IOException, SQLException {
        handleGetChangesForCRInternal(null, crId);
    }

    private void handleGetChangesForCR(HttpServletResponse resp, int crId) throws IOException, SQLException {
        handleGetChangesForCRInternal(resp, crId);
    }

    private void handleGetChangesForCRInternal(HttpServletResponse resp, int crId) throws IOException, SQLException {
        logger.info("Getting pending changes for CR {}", crId);
        
        // IMPORTANT: Always recalculate changes from mappings to get the latest values
        // The saveChangeReview method will update existing records (preserving original old_value)
        // or create new ones as needed
        logger.info("Recalculating changes from mappings for CR {} (will update existing records in cr_changes_review)", crId);
        
        JsonArray changesArray = new JsonArray();
        
        // Get CR info (author from Created_By, reference, type - to determine if this is CREATE or EDIT)
        String crAuthor = "Unknown";
        boolean isCreateCR = false;
        int authorId = 0;
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get author from Created_By field
            String crSql = "SELECT cr.Reference, cr.PrimaryName, cr.Created_By " +
                          "FROM changerequest cr WHERE cr.ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(crSql)) {
                stmt.setInt(1, crId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String primaryName = rs.getString("PrimaryName");
                        Integer createdBy = rs.getObject("Created_By", Integer.class);
                        authorId = (createdBy != null) ? createdBy : 0;
                        
                        // Get author name from Created_By
                        if (createdBy != null) {
                            String authorSql = "SELECT CONCAT(First_Name, ' ', Last_Name) as FullName FROM people WHERE ID = ?";
                            try (PreparedStatement authorStmt = conn.prepareStatement(authorSql)) {
                                authorStmt.setInt(1, createdBy);
                                try (ResultSet authorRs = authorStmt.executeQuery()) {
                                    if (authorRs.next()) {
                                        crAuthor = authorRs.getString("FullName");
                                    }
                                }
                            }
                        }
                        if (crAuthor == null || crAuthor.isEmpty()) crAuthor = "Unknown";
                        
                        // Check if this is a CREATE CR (primaryName contains "CREATE" but not "EDIT")
                        if (primaryName != null) {
                            String upperName = primaryName.toUpperCase();
                            if (upperName.contains("CREATE") && !upperName.contains("EDIT")) {
                                isCreateCR = true;
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[PendingChangesServlet] Error getting CR info: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Store final values for use in inner scope
        final boolean isCreateCRFinal = isCreateCR;
        final String crAuthorFinal = crAuthor;
        final int authorIdFinal = authorId;
        
        // Check CR status to determine if we should use snapshot
        boolean isCRCompletedOrCancelled = false;
        try (Connection conn = DatabaseConnection.getConnection()) {
            String statusSql = "SELECT cs.PrimaryName as StatusName FROM changerequest cr " +
                              "LEFT JOIN changerequeststatus cs ON cr.CR_StatusID = cs.ID " +
                              "WHERE cr.ID = ? AND cr.Deleted_At IS NULL";
            try (PreparedStatement stmt = conn.prepareStatement(statusSql)) {
                stmt.setInt(1, crId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String statusName = rs.getString("StatusName");
                        if (statusName != null) {
                            String upperStatus = statusName.toUpperCase();
                            // Check if CR is completed or cancelled
                            isCRCompletedOrCancelled = upperStatus.contains("COMPLETED") || 
                                                       upperStatus.contains("CANCELLED") ||
                                                       upperStatus.contains("CANCELED");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error checking CR status: {}", e.getMessage());
        }
        
        // Define facet configurations for comparison (for EDIT CRs)
        String[][] facetConfigs = {
            // {facetName, tableName, changesTableName, displayName, nameColumn}
            {"glossary", "glossary", "glossary_changes", "Glossary", "Name"},
            {"dataset", "dataset", "dataset_changes", "Data Set", "PrimaryName"},
            {"system", "system", "system_changes", "System", "Name"},
            {"process", "process", "process_changes", "Process", "PrimaryName"}
        };
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (String[] config : facetConfigs) {
                String facetName = config[0];
                String tableName = config[1];
                String changesTableName = config[2];
                String displayName = config[3];
                String nameColumn = config[4];
                
                // Get all mappings for this CR in this facet
                // Exclude stakeholders mappings - stakeholders are excluded from pending changes
                String mappingSql = "SELECT object_id, nobject_id, area_key FROM " + changesTableName + 
                                   " WHERE change_request_id = ? AND area_key NOT LIKE 'stakeholders#%'";
                
                // Track processed (objectId, nobjectId, areaKey) to avoid duplicate processing
                java.util.Set<String> processedMappings = new java.util.HashSet<>();
                
                try (PreparedStatement mappingStmt = conn.prepareStatement(mappingSql)) {
                    mappingStmt.setInt(1, crId);
                    try (ResultSet mappingRs = mappingStmt.executeQuery()) {
                        while (mappingRs.next()) {
                            int objectId = mappingRs.getInt("object_id");
                            int nobjectId = mappingRs.getInt("nobject_id");
                            String areaKey = mappingRs.getString("area_key");
                            
                            // Exclude stakeholders mappings - stakeholders are excluded from pending changes
                            if (areaKey != null && areaKey.startsWith("stakeholders#")) {
                                logger.debug("Skipping stakeholders mapping: {} {} {}", facetName, objectId, areaKey);
                                continue;
                            }
                            
                            // Deduplicate: skip if we've already processed this exact mapping
                            String mappingKey = objectId + "|" + nobjectId + "|" + areaKey;
                            if (processedMappings.contains(mappingKey)) {
                                logger.debug("Skipping duplicate mapping: {} {} {} {}", facetName, objectId, nobjectId, areaKey);
                                continue;
                            }
                            processedMappings.add(mappingKey);
                            
                            logger.debug("Found mapping: facet={}, object_id={}, nobject_id={}, area_key={}", 
                                facetName, objectId, nobjectId, areaKey);
                            
                            // Check if cloned row still exists (for active CRs, cloned rows should exist)
                            boolean clonedRowExists = checkIfClonedRowExists(conn, facetName, areaKey, nobjectId);
                            
                            if (!clonedRowExists && !isCRCompletedOrCancelled) {
                                // Cloned row doesn't exist and CR is not completed/cancelled - skip this mapping
                                logger.debug("Cloned row does not exist for {} {} {}, skipping", facetName, objectId, areaKey);
                                continue;
                            }
                            
                            // If CR is completed/cancelled and cloned row doesn't exist, use cached data from cr_changes_review
                            if (isCRCompletedOrCancelled && !clonedRowExists) {
                                logger.debug("CR is completed/cancelled and cloned row doesn't exist for {} {} {}, using cached data from cr_changes_review", 
                                    facetName, objectId, areaKey);
                                
                                // Load changes from cr_changes_review for this specific mapping
                                String reviewSql = "SELECT * FROM cr_changes_review " +
                                                  "WHERE change_request_id = ? AND facet_id = ? AND object_id = ? " +
                                                  "AND (area_key = ? OR (area_key IS NULL AND ? IS NULL)) " +
                                                  "ORDER BY created_at ASC";
                                
                                Integer facetId = facetChangesDAO.getFacetId(facetName);
                                if (facetId != null) {
                                    try (PreparedStatement reviewStmt = conn.prepareStatement(reviewSql)) {
                                        reviewStmt.setInt(1, crId);
                                        reviewStmt.setInt(2, facetId);
                                        reviewStmt.setInt(3, objectId);
                                        reviewStmt.setString(4, areaKey);
                                        reviewStmt.setString(5, areaKey);
                                        
                                        try (ResultSet reviewRs = reviewStmt.executeQuery()) {
                                            while (reviewRs.next()) {
                                                String fieldName = reviewRs.getString("field_name");
                                                String oldValue = reviewRs.getString("old_value");
                                                String newValue = reviewRs.getString("new_value");
                                                String operation = reviewRs.getString("operation");
                                                String tabName = reviewRs.getString("tab_name");
                                                String objectName = reviewRs.getString("object_name");
                                                String authorName = reviewRs.getString("author_name");
                                                Integer reviewNobjectId = reviewRs.getObject("nobject_id", Integer.class);
                                                String areaKeyFromDb = reviewRs.getString("area_key");
                                                String relatedName = reviewRs.getString("related_name");
                                                String relationTypeName = reviewRs.getString("relation_type_name");
                                                
                                                // Convert values if needed (for KDE, Security Classification, etc.)
                                                // Use resolveForeignKeyValue for proper conversion
                                                String convertedOldValue = oldValue;
                                                String convertedNewValue = newValue;
                                                if (fieldName != null && !fieldName.isEmpty()) {
                                                    String normalizedFieldName = fieldName.toLowerCase();
                                                    if (oldValue != null && !oldValue.isEmpty()) {
                                                        convertedOldValue = resolveForeignKeyValue(conn, facetName, normalizedFieldName, oldValue);
                                                    }
                                                    if (newValue != null && !newValue.isEmpty()) {
                                                        convertedNewValue = resolveForeignKeyValue(conn, facetName, normalizedFieldName, newValue);
                                                    }
                                                }
                                                
                                                JsonObject changeObj = new JsonObject();
                                                changeObj.addProperty("facetType", displayName);
                                                changeObj.addProperty("objectId", objectId);
                                                changeObj.addProperty("objectName", objectName != null ? objectName : "");
                                                changeObj.addProperty("tabName", tabName != null ? tabName : getTabNameFromAreaKey(areaKey));
                                                changeObj.addProperty("operation", operation != null ? operation : "Updated");
                                                changeObj.addProperty("fieldName", fieldName);
                                                changeObj.addProperty("oldValue", convertedOldValue != null ? convertedOldValue : "");
                                                changeObj.addProperty("newValue", convertedNewValue != null ? convertedNewValue : "");
                                                changeObj.addProperty("userName", authorName != null ? authorName : crAuthorFinal);
                                                if (reviewNobjectId != null) {
                                                    changeObj.addProperty("nobjectId", reviewNobjectId);
                                                }
                                                if (areaKeyFromDb != null) {
                                                    changeObj.addProperty("areaKey", areaKeyFromDb);
                                                }
                                                if (relatedName != null) {
                                                    changeObj.addProperty("relatedName", relatedName);
                                                }
                                                if (relationTypeName != null) {
                                                    changeObj.addProperty("relationTypeName", relationTypeName);
                                                }
                                                
                                                changesArray.add(changeObj);
                                            }
                                        }
                                    } catch (SQLException e) {
                                        logger.warn("Error loading from cr_changes_review for {} {} {}: {}", 
                                                   facetName, objectId, areaKey, e.getMessage());
                                    }
                                }
                                
                                // Continue to next mapping (we've already loaded from cache)
                                continue;
                            }
                            
                            // Determine tab name from area_key
                            String tabName = getTabNameFromAreaKey(areaKey);
                            // Ensure summary always has "Summary" tab name, not "Documents"
                            if ("summary".equals(areaKey)) {
                                tabName = "Summary";
                            }
                            
                            // Get the object name - use nobjectId if objectId is 0 or same as nobjectId
                            String objectName;
                            if (objectId == 0 || objectId == nobjectId) {
                                objectName = getObjectName(conn, tableName, nobjectId, nameColumn);
                            } else {
                                objectName = getObjectName(conn, tableName, objectId, nameColumn);
                            }
                            
                            // Determine if this is a create or edit based on object_id
                            // For CREATE: object_id is 0, or object_id == nobject_id, or isCreateCR flag is true with no original
                            boolean isActualCreate = isCreateCRFinal && (objectId == 0 || objectId == nobjectId);
                            
                            // Compare original vs cloned data
                            if ("summary".equals(areaKey)) {
                                List<Map<String, String>> fieldChanges;
                                String operation;
                                
                                if (isActualCreate) {
                                    // For CREATE CRs, show all fields from the created object (nobject_id)
                                    fieldChanges = getObjectFields(conn, tableName, nobjectId, facetName);
                                    operation = "Created";
                                    //system.out.println("[PendingChangesServlet] CREATE CR - showing " + fieldChanges.size() + " fields from nobject " + nobjectId);
                                    
                                    for (Map<String, String> field : fieldChanges) {
                                        JsonObject changeObj = new JsonObject();
                                        changeObj.addProperty("facetType", displayName);
                                        changeObj.addProperty("objectId", objectId);
                                        changeObj.addProperty("objectName", objectName);
                                        changeObj.addProperty("tabName", tabName);
                                        changeObj.addProperty("operation", operation);
                                        changeObj.addProperty("fieldName", field.get("fieldName"));
                                        changeObj.addProperty("oldValue", "");
                                        changeObj.addProperty("newValue", field.get("value"));
                                        changeObj.addProperty("userName", crAuthorFinal);
                                        // Add metadata for saving to review table
                                        changeObj.addProperty("_facetName", facetName);
                                        changeObj.addProperty("_nobjectId", nobjectId);
                                        changeObj.addProperty("_areaKey", areaKey);
                                        changesArray.add(changeObj);
                                    }
                                } else {
                                    // For EDIT CRs, compare original vs cloned
                                    fieldChanges = compareObjects(conn, tableName, objectId, nobjectId, facetName);
                                    operation = "Updated";
                                    //system.out.println("[PendingChangesServlet] EDIT CR - found " + fieldChanges.size() + " field changes");
                                    
                                    for (Map<String, String> change : fieldChanges) {
                                        JsonObject changeObj = new JsonObject();
                                        changeObj.addProperty("facetType", displayName);
                                        changeObj.addProperty("objectId", objectId);
                                        changeObj.addProperty("objectName", objectName);
                                        changeObj.addProperty("tabName", tabName);
                                        changeObj.addProperty("operation", operation);
                                        changeObj.addProperty("fieldName", change.get("fieldName"));
                                        changeObj.addProperty("oldValue", change.get("oldValue"));
                                        changeObj.addProperty("newValue", change.get("newValue"));
                                        changeObj.addProperty("userName", crAuthorFinal);
                                        // Add metadata for saving to review table
                                        changeObj.addProperty("_facetName", facetName);
                                        changeObj.addProperty("_nobjectId", nobjectId);
                                        changeObj.addProperty("_areaKey", areaKey);
                                        changesArray.add(changeObj);
                                    }
                                }
                            } else if (areaKey != null && areaKey.startsWith("impact#")) {
                                // Handle impact relationships - compare original vs cloned
                                String relationType = areaKey.substring("impact#".length());
                                //system.out.println("[PendingChangesServlet] Processing impact: " + relationType + 
                                  //  " for originalId=" + objectId + ", clonedId=" + nobjectId);
                                
                                List<Map<String, String>> impactChanges = compareImpactRelationships(conn, facetName, objectId, nobjectId, relationType);
                                //system.out.println("[PendingChangesServlet] Impact changes found: " + impactChanges.size());
                                
                                // Only add entries if there are actual changes
                                // IMPORTANT: Impact changes already include separate "Relationship Type" field entries
                                // from compareImpactRelationships. Do NOT add relationTypeName metadata to avoid
                                // the frontend creating duplicate "Relationship Type" rows in the details modal.
                                for (Map<String, String> change : impactChanges) {
                                    JsonObject changeObj = new JsonObject();
                                    changeObj.addProperty("facetType", displayName + " X " + change.get("relatedType"));
                                    changeObj.addProperty("objectId", objectId);
                                    changeObj.addProperty("objectName", objectName + " X " + change.get("relatedName"));
                                    // Use unique tabName for each impact type to ensure separate rows
                                    changeObj.addProperty("tabName", "Impact - " + change.get("relatedType"));
                                    changeObj.addProperty("operation", change.get("operation"));
                                    changeObj.addProperty("fieldName", change.get("fieldName"));
                                    changeObj.addProperty("oldValue", change.get("oldValue"));
                                    changeObj.addProperty("newValue", change.get("newValue"));
                                    changeObj.addProperty("userName", crAuthorFinal);
                                    changeObj.addProperty("relatedType", change.get("relatedType"));
                                    changeObj.addProperty("relatedName", change.get("relatedName"));
                                    // Do NOT add relationTypeName here - it's already a separate field entry
                                    // Adding it would cause the frontend to render duplicate "Relationship Type" rows
                                    // Add metadata for saving to review table
                                    changeObj.addProperty("_facetName", facetName);
                                    changeObj.addProperty("_nobjectId", nobjectId);
                                    changeObj.addProperty("_areaKey", areaKey);
                                    changesArray.add(changeObj);
                                }
                            } else if (areaKey != null && areaKey.startsWith("stakeholders#")) {
                                // Handle stakeholder changes - compare original vs cloned
                                //system.out.println("[PendingChangesServlet] Processing stakeholders for originalId=" + objectId + ", clonedId=" + nobjectId);
                                
                                List<Map<String, String>> stakeholderChanges = compareStakeholders(conn, facetName, objectId, nobjectId);
                                //system.out.println("[PendingChangesServlet] Stakeholder changes found: " + stakeholderChanges.size());
                                
                                // Only add entries if there are actual changes
                                for (Map<String, String> change : stakeholderChanges) {
                                    JsonObject changeObj = new JsonObject();
                                    changeObj.addProperty("facetType", displayName + " Stakeholder");
                                    changeObj.addProperty("objectId", objectId);
                                    changeObj.addProperty("objectName", objectName);
                                    changeObj.addProperty("tabName", "Stakeholders");
                                    changeObj.addProperty("operation", change.get("operation"));
                                    changeObj.addProperty("fieldName", change.get("fieldName"));
                                    changeObj.addProperty("oldValue", change.get("oldValue"));
                                    changeObj.addProperty("newValue", change.get("newValue"));
                                    changeObj.addProperty("userName", crAuthorFinal);
                                    // Add metadata for saving to review table
                                    changeObj.addProperty("_facetName", facetName);
                                    changeObj.addProperty("_nobjectId", nobjectId);
                                    changeObj.addProperty("_areaKey", areaKey);
                                    changesArray.add(changeObj);
                                }
                            } else if ("data-content".equals(areaKey) && "system".equals(facetName)) {
                                // Handle data content changes for System facet (glossary_x_system from System perspective)
                                // nobjectId is the ID of the glossary_x_system relationship record
                                // We need to compare the relationship in original system vs cloned system
                                
                                // Get cloned system ID
                                Integer clonedSystemId = facetChangesDAO.getNObjectId("system", objectId, "summary", crId);
                                if (clonedSystemId == null) {
                                    logger.debug("No cloned system found for {} in CR {}, skipping data-content", objectId, crId);
                                    continue;
                                }
                                
                                // Get the relationship record to find GlossaryID
                                String relSql = "SELECT gxs.ID, gxs.GlossaryID, gxs.SystemID, gxs.Strategic_DatasetID, gxs.Relation_TypeID, " +
                                              "g.Name as glossary_name, s.Name as system_name, d.PrimaryName as dataset_name, " +
                                              "rt.PrimaryName as relation_type_name " +
                                              "FROM glossary_x_system gxs " +
                                              "LEFT JOIN glossary g ON gxs.GlossaryID = g.ID " +
                                              "LEFT JOIN system s ON gxs.SystemID = s.id " +
                                              "LEFT JOIN dataset d ON gxs.Strategic_DatasetID = d.ID " +
                                              "LEFT JOIN glossary_x_system_relationtype rt ON gxs.Relation_TypeID = rt.ID " +
                                              "WHERE gxs.ID = ?";
                                
                                Integer glossaryId = null;
                                Integer strategicDatasetId = null;
                                Integer relationTypeId = null;
                                String glossaryName = null;
                                String datasetName = null;
                                String relationTypeName = null;
                                
                                try (PreparedStatement relStmt = conn.prepareStatement(relSql)) {
                                    relStmt.setInt(1, nobjectId);
                                    try (ResultSet relRs = relStmt.executeQuery()) {
                                        if (relRs.next()) {
                                            glossaryId = relRs.getObject("GlossaryID", Integer.class);
                                            strategicDatasetId = relRs.getObject("Strategic_DatasetID", Integer.class);
                                            relationTypeId = relRs.getObject("Relation_TypeID", Integer.class);
                                            glossaryName = relRs.getString("glossary_name");
                                            datasetName = relRs.getString("dataset_name");
                                            relationTypeName = relRs.getString("relation_type_name");
                                        }
                                    }
                                }
                                
                                if (glossaryId == null) {
                                    logger.debug("Could not find glossary_x_system record with ID {}", nobjectId);
                                    continue;
                                }
                                
                                // Check if this relationship exists in original system
                                String checkOriginalSql = "SELECT gxs.ID, gxs.GlossaryID, gxs.SystemID, gxs.Strategic_DatasetID, gxs.Relation_TypeID, " +
                                                         "g.Name as glossary_name, d.PrimaryName as dataset_name, " +
                                                         "rt.PrimaryName as relation_type_name " +
                                                         "FROM glossary_x_system gxs " +
                                                         "LEFT JOIN glossary g ON gxs.GlossaryID = g.ID " +
                                                         "LEFT JOIN dataset d ON gxs.Strategic_DatasetID = d.ID " +
                                                         "LEFT JOIN glossary_x_system_relationtype rt ON gxs.Relation_TypeID = rt.ID " +
                                                         "WHERE gxs.SystemID = ? AND gxs.GlossaryID = ?";
                                
                                boolean isNewRelationship = true;
                                String oldDatasetName = null;
                                String oldRelationTypeName = null;
                                Integer oldStrategicDatasetId = null;
                                Integer oldRelationTypeId = null;
                                
                                try (PreparedStatement checkStmt = conn.prepareStatement(checkOriginalSql)) {
                                    checkStmt.setInt(1, objectId);
                                    checkStmt.setInt(2, glossaryId);
                                    try (ResultSet checkRs = checkStmt.executeQuery()) {
                                        if (checkRs.next()) {
                                            isNewRelationship = false;
                                            oldStrategicDatasetId = checkRs.getObject("Strategic_DatasetID", Integer.class);
                                            oldRelationTypeId = checkRs.getObject("Relation_TypeID", Integer.class);
                                            oldDatasetName = checkRs.getString("dataset_name");
                                            oldRelationTypeName = checkRs.getString("relation_type_name");
                                        }
                                    }
                                }
                                
                                String operation = isNewRelationship ? "Inserted" : "Updated";
                                
                                // Create change entries for each field that changed
                                if (isNewRelationship) {
                                    // New relationship - show Glossary name
                                    JsonObject changeObj = new JsonObject();
                                    changeObj.addProperty("facetType", displayName + " X Glossary");
                                    changeObj.addProperty("objectId", objectId);
                                    changeObj.addProperty("objectName", objectName + " X " + (glossaryName != null ? glossaryName : "Glossary"));
                                    changeObj.addProperty("tabName", "Data Content");
                                    changeObj.addProperty("operation", operation);
                                    changeObj.addProperty("fieldName", "Glossary");
                                    changeObj.addProperty("oldValue", "");
                                    changeObj.addProperty("newValue", glossaryName != null ? glossaryName : "");
                                    changeObj.addProperty("userName", crAuthorFinal);
                                    changeObj.addProperty("relatedType", "Glossary");
                                    changeObj.addProperty("relatedName", glossaryName);
                                    changeObj.addProperty("_facetName", facetName);
                                    changeObj.addProperty("_nobjectId", nobjectId);
                                    changeObj.addProperty("_areaKey", areaKey);
                                    changesArray.add(changeObj);
                                    
                                    // Add Relationship Type as separate field entry (for details modal)
                                    if (relationTypeName != null && !relationTypeName.isEmpty()) {
                                        JsonObject rtChangeObj = new JsonObject();
                                        rtChangeObj.addProperty("facetType", displayName + " X Glossary");
                                        rtChangeObj.addProperty("objectId", objectId);
                                        rtChangeObj.addProperty("objectName", objectName + " X " + (glossaryName != null ? glossaryName : "Glossary"));
                                        rtChangeObj.addProperty("tabName", "Data Content");
                                        rtChangeObj.addProperty("operation", operation);
                                        rtChangeObj.addProperty("fieldName", "Relationship Type");
                                        rtChangeObj.addProperty("oldValue", "");
                                        rtChangeObj.addProperty("newValue", relationTypeName);
                                        rtChangeObj.addProperty("userName", crAuthorFinal);
                                        rtChangeObj.addProperty("relatedType", "Glossary");
                                        rtChangeObj.addProperty("relatedName", glossaryName);
                                        rtChangeObj.addProperty("_facetName", facetName);
                                        rtChangeObj.addProperty("_nobjectId", nobjectId);
                                        rtChangeObj.addProperty("_areaKey", areaKey);
                                        changesArray.add(rtChangeObj);
                                    }
                                    
                                    // Add Strategic Dataset if present
                                    if (strategicDatasetId != null && datasetName != null) {
                                        JsonObject datasetChangeObj = new JsonObject();
                                        datasetChangeObj.addProperty("facetType", displayName + " X Glossary");
                                        datasetChangeObj.addProperty("objectId", objectId);
                                        datasetChangeObj.addProperty("objectName", objectName + " X " + (glossaryName != null ? glossaryName : "Glossary"));
                                        datasetChangeObj.addProperty("tabName", "Data Content");
                                        datasetChangeObj.addProperty("operation", operation);
                                        datasetChangeObj.addProperty("fieldName", "Strategic Dataset");
                                        datasetChangeObj.addProperty("oldValue", "");
                                        datasetChangeObj.addProperty("newValue", datasetName);
                                        datasetChangeObj.addProperty("userName", crAuthorFinal);
                                        datasetChangeObj.addProperty("relatedType", "Glossary");
                                        datasetChangeObj.addProperty("relatedName", glossaryName);
                                        datasetChangeObj.addProperty("_facetName", facetName);
                                        datasetChangeObj.addProperty("_nobjectId", nobjectId);
                                        datasetChangeObj.addProperty("_areaKey", areaKey);
                                        changesArray.add(datasetChangeObj);
                                    }
                                } else {
                                    // Updated relationship - compare fields
                                    // Compare Strategic Dataset
                                    if (!Objects.equals(oldStrategicDatasetId, strategicDatasetId)) {
                                        JsonObject datasetChangeObj = new JsonObject();
                                        datasetChangeObj.addProperty("facetType", displayName + " X Glossary");
                                        datasetChangeObj.addProperty("objectId", objectId);
                                        datasetChangeObj.addProperty("objectName", objectName + " X " + (glossaryName != null ? glossaryName : "Glossary"));
                                        datasetChangeObj.addProperty("tabName", "Data Content");
                                        datasetChangeObj.addProperty("operation", operation);
                                        datasetChangeObj.addProperty("fieldName", "Strategic Dataset");
                                        datasetChangeObj.addProperty("oldValue", oldDatasetName != null ? oldDatasetName : "");
                                        datasetChangeObj.addProperty("newValue", datasetName != null ? datasetName : "");
                                        datasetChangeObj.addProperty("userName", crAuthorFinal);
                                        datasetChangeObj.addProperty("relatedType", "Glossary");
                                        datasetChangeObj.addProperty("relatedName", glossaryName);
                                        datasetChangeObj.addProperty("_facetName", facetName);
                                        datasetChangeObj.addProperty("_nobjectId", nobjectId);
                                        datasetChangeObj.addProperty("_areaKey", areaKey);
                                        changesArray.add(datasetChangeObj);
                                    }
                                    
                                    // Compare Relation Type
                                    if (!Objects.equals(oldRelationTypeId, relationTypeId)) {
                                        JsonObject typeChangeObj = new JsonObject();
                                        typeChangeObj.addProperty("facetType", displayName + " X Glossary");
                                        typeChangeObj.addProperty("objectId", objectId);
                                        typeChangeObj.addProperty("objectName", objectName + " X " + (glossaryName != null ? glossaryName : "Glossary"));
                                        typeChangeObj.addProperty("tabName", "Data Content");
                                        typeChangeObj.addProperty("operation", operation);
                                        typeChangeObj.addProperty("fieldName", "Relation Type");
                                        typeChangeObj.addProperty("oldValue", oldRelationTypeName != null ? oldRelationTypeName : "");
                                        typeChangeObj.addProperty("newValue", relationTypeName != null ? relationTypeName : "");
                                        typeChangeObj.addProperty("userName", crAuthorFinal);
                                        typeChangeObj.addProperty("relatedType", "Glossary");
                                        typeChangeObj.addProperty("relatedName", glossaryName);
                                        if (relationTypeName != null) {
                                            typeChangeObj.addProperty("relationTypeName", relationTypeName);
                                        }
                                        typeChangeObj.addProperty("_facetName", facetName);
                                        typeChangeObj.addProperty("_nobjectId", nobjectId);
                                        typeChangeObj.addProperty("_areaKey", areaKey);
                                        changesArray.add(typeChangeObj);
                                    }
                                }
                            } else if (areaKey != null && areaKey.startsWith("relationships#")) {
                                // Handle relationship changes (process_x_process, glossary_x_glossary, attribute_x_attribute, etc.)
                                String relTable = areaKey.substring("relationships#".length());
                                List<Map<String, String>> relChanges = compareRelationshipChanges(conn, facetName, objectId, nobjectId, relTable);
                                
                                // Determine component name based on relationship table
                                String componentName = getComponentNameForRelationship(relTable);
                                
                                // For attribute_x_attribute, we need to group field changes into a single table row
                                // but keep individual field changes for the details modal
                                if ("attribute_x_attribute".equals(relTable) && !relChanges.isEmpty()) {
                                    // Get the display name from the first change (all changes should have the same displayName)
                                    String relationshipDisplayName = relChanges.get(0).get("displayName");
                                    if (relationshipDisplayName == null || relationshipDisplayName.isEmpty()) {
                                        // Fallback: build from field values
                                        String sourceAttr = null;
                                        String targetAttr = null;
                                        for (Map<String, String> change : relChanges) {
                                            String fieldName = change.get("fieldName");
                                            if ("Attribute".equals(fieldName)) {
                                                sourceAttr = change.get("newValue");
                                            } else if ("Related Attributes".equals(fieldName)) {
                                                targetAttr = change.get("newValue");
                                            }
                                        }
                                        relationshipDisplayName = (sourceAttr != null ? sourceAttr : "") + " X " + (targetAttr != null ? targetAttr : "");
                                    }
                                    
                                    // Extract relatedType, relatedName, and relationTypeName from first change (all should have same values)
                                    String relatedType = relChanges.get(0).get("relatedType");
                                    String relatedName = relChanges.get(0).get("relatedName");
                                    String relationTypeName = relChanges.get(0).get("relationTypeName");
                                    
                                    // Create a single entry for the table row
                                    JsonObject mainChangeObj = new JsonObject();
                                    mainChangeObj.addProperty("facetType", componentName);
                                    mainChangeObj.addProperty("objectId", objectId);
                                    mainChangeObj.addProperty("objectName", relationshipDisplayName != null ? relationshipDisplayName : "");
                                    mainChangeObj.addProperty("tabName", tabName);
                                    mainChangeObj.addProperty("operation", "Inserted");
                                    mainChangeObj.addProperty("fieldName", "Relationship");
                                    mainChangeObj.addProperty("oldValue", "");
                                    mainChangeObj.addProperty("newValue", relationshipDisplayName != null ? relationshipDisplayName : "");
                                    mainChangeObj.addProperty("userName", crAuthorFinal);
                                    mainChangeObj.addProperty("componentName", componentName);
                                    if (relatedType != null) {
                                        mainChangeObj.addProperty("relatedType", relatedType);
                                    }
                                    if (relatedName != null) {
                                        mainChangeObj.addProperty("relatedName", relatedName);
                                    }
                                    if (relationTypeName != null) {
                                        mainChangeObj.addProperty("relationTypeName", relationTypeName);
                                    }
                                    // Add metadata for saving to review table
                                    mainChangeObj.addProperty("_facetName", facetName);
                                    mainChangeObj.addProperty("_nobjectId", nobjectId);
                                    mainChangeObj.addProperty("_areaKey", areaKey);
                                    changesArray.add(mainChangeObj);
                                    
                                    // Add individual field changes for the details modal
                                    for (Map<String, String> change : relChanges) {
                                        JsonObject fieldChangeObj = new JsonObject();
                                        fieldChangeObj.addProperty("facetType", componentName);
                                        fieldChangeObj.addProperty("objectId", objectId);
                                        fieldChangeObj.addProperty("objectName", relationshipDisplayName != null ? relationshipDisplayName : "");
                                        fieldChangeObj.addProperty("tabName", tabName);
                                        fieldChangeObj.addProperty("operation", "Inserted");
                                        fieldChangeObj.addProperty("fieldName", change.get("fieldName"));
                                        fieldChangeObj.addProperty("oldValue", change.get("oldValue"));
                                        fieldChangeObj.addProperty("newValue", change.get("newValue"));
                                        fieldChangeObj.addProperty("userName", crAuthorFinal);
                                        fieldChangeObj.addProperty("componentName", componentName);
                                        if (change.get("relatedType") != null) {
                                            fieldChangeObj.addProperty("relatedType", change.get("relatedType"));
                                        }
                                        if (change.get("relatedName") != null) {
                                            fieldChangeObj.addProperty("relatedName", change.get("relatedName"));
                                        }
                                        if (change.get("relationTypeName") != null) {
                                            fieldChangeObj.addProperty("relationTypeName", change.get("relationTypeName"));
                                        }
                                        // Add metadata for saving to review table
                                        fieldChangeObj.addProperty("_facetName", facetName);
                                        fieldChangeObj.addProperty("_nobjectId", nobjectId);
                                        fieldChangeObj.addProperty("_areaKey", areaKey);
                                        changesArray.add(fieldChangeObj);
                                    }
                                } else {
                                    // For other relationship types, add all changes as-is
                                    for (Map<String, String> change : relChanges) {
                                        JsonObject changeObj = new JsonObject();
                                        // Use component name (e.g., "Attribute X Attribute") instead of facet display name
                                        changeObj.addProperty("facetType", componentName);
                                        changeObj.addProperty("objectId", objectId);
                                        changeObj.addProperty("objectName", change.get("displayName"));
                                        changeObj.addProperty("tabName", change.get("tabName"));
                                        changeObj.addProperty("operation", change.get("operation"));
                                        changeObj.addProperty("fieldName", change.get("fieldName"));
                                        changeObj.addProperty("oldValue", change.get("oldValue"));
                                        changeObj.addProperty("newValue", change.get("newValue"));
                                        changeObj.addProperty("userName", crAuthorFinal);
                                        changeObj.addProperty("componentName", componentName);
                                        
                                        // Add relatedType and relatedName if available (like impact relationships)
                                        if (change.get("relatedType") != null) {
                                            changeObj.addProperty("relatedType", change.get("relatedType"));
                                        }
                                        if (change.get("relatedName") != null) {
                                            changeObj.addProperty("relatedName", change.get("relatedName"));
                                        }
                                        // Add relationTypeName if available (for relationship type display)
                                        if (change.get("relationTypeName") != null) {
                                            changeObj.addProperty("relationTypeName", change.get("relationTypeName"));
                                        }
                                        
                                        // Add metadata for saving to review table
                                        changeObj.addProperty("_facetName", facetName);
                                        changeObj.addProperty("_nobjectId", nobjectId);
                                        changeObj.addProperty("_areaKey", areaKey);
                                        changesArray.add(changeObj);
                                    }
                                }
                            } else if (areaKey.equals("summary#attribute")) {
                                // Handle dataset attribute changes
                                // Similar to documents - use same objectName for all attributes, distinguish by nobjectId
                                logger.debug("Processing attribute change: datasetId={}, attrId={}", objectId, nobjectId);
                                List<Map<String, String>> attrChanges = compareAttributeChanges(conn, objectId, nobjectId);
                                logger.debug("Found {} attribute field changes for attribute {}", attrChanges.size(), nobjectId);
                                
                                for (Map<String, String> change : attrChanges) {
                                    JsonObject changeObj = new JsonObject();
                                    changeObj.addProperty("facetType", displayName);
                                    changeObj.addProperty("objectId", objectId);
                                    // Use attribute name as objectName (like documents use document name)
                                    // This ensures each attribute gets its own row in change review table
                                    String attributeName = change.get("attributeName");
                                    changeObj.addProperty("objectName", attributeName != null ? attributeName : "Attribute " + nobjectId);
                                    changeObj.addProperty("tabName", "Attributes");
                                    changeObj.addProperty("operation", change.get("operation"));
                                    changeObj.addProperty("fieldName", change.get("fieldName"));
                                    changeObj.addProperty("oldValue", change.get("oldValue"));
                                    changeObj.addProperty("newValue", change.get("newValue"));
                                    changeObj.addProperty("userName", crAuthorFinal);
                                    // Add nobjectId as regular property (not metadata) so it's available in frontend
                                    // This is needed for grouping attributes as separate rows
                                    changeObj.addProperty("nobjectId", nobjectId);
                                    // Add metadata for saving to review table
                                    changeObj.addProperty("_facetName", facetName);
                                    changeObj.addProperty("_nobjectId", nobjectId);
                                    changeObj.addProperty("_areaKey", areaKey);
                                    changesArray.add(changeObj);
                                }
                            } else if (areaKey.equals("summary#dataset_value_info")) {
                                // Handle dataset values metadata changes
                                List<Map<String, String>> valueInfoChanges = compareValueInfoChanges(conn, objectId, nobjectId);
                                
                                for (Map<String, String> change : valueInfoChanges) {
                                    JsonObject changeObj = new JsonObject();
                                    changeObj.addProperty("facetType", displayName);
                                    changeObj.addProperty("objectId", objectId);
                                    changeObj.addProperty("objectName", objectName);
                                    changeObj.addProperty("tabName", "Values");
                                    changeObj.addProperty("operation", change.get("operation"));
                                    changeObj.addProperty("fieldName", change.get("fieldName"));
                                    changeObj.addProperty("oldValue", change.get("oldValue"));
                                    changeObj.addProperty("newValue", change.get("newValue"));
                                    changeObj.addProperty("userName", crAuthorFinal);
                                    // Add metadata for saving to review table
                                    changeObj.addProperty("_facetName", facetName);
                                    changeObj.addProperty("_nobjectId", nobjectId);
                                    changeObj.addProperty("_areaKey", areaKey);
                                    changesArray.add(changeObj);
                                }
                            } else if (areaKey.startsWith("documents#")) {
                                // Handle document changes - nobjectId is the document ID
                                // Get document details
                                String documentTableName = areaKey.substring("documents#".length());
                                List<Map<String, String>> docChanges = getDocumentDetails(conn, documentTableName, nobjectId);
                                
                                for (Map<String, String> change : docChanges) {
                                    JsonObject changeObj = new JsonObject();
                                    changeObj.addProperty("facetType", "Documents");
                                    changeObj.addProperty("objectId", objectId);
                                    // Use document name as object name
                                    String docName = change.get("documentName");
                                    changeObj.addProperty("objectName", docName != null ? docName : "Document " + nobjectId);
                                    changeObj.addProperty("tabName", "Documents"); // Changed from "Summary" to "Documents" to separate from summary
                                    changeObj.addProperty("operation", "Inserted");
                                    changeObj.addProperty("fieldName", change.get("fieldName"));
                                    changeObj.addProperty("oldValue", "");
                                    changeObj.addProperty("newValue", change.get("newValue"));
                                    changeObj.addProperty("userName", crAuthorFinal);
                                    
                                    // Add file info for hyperlink (for File field)
                                    if ("File".equals(change.get("fieldName"))) {
                                        changeObj.addProperty("filePath", change.get("filePath"));
                                        changeObj.addProperty("isUrl", change.get("isUrl"));
                                        changeObj.addProperty("documentId", change.get("documentId"));
                                        changeObj.addProperty("isFileLink", true);
                                    }
                                    
                                    // Add metadata for saving to review table
                                    changeObj.addProperty("_facetName", facetName);
                                    changeObj.addProperty("_nobjectId", nobjectId);
                                    changeObj.addProperty("_areaKey", areaKey);
                                    changesArray.add(changeObj);
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Table might not exist - skip this facet
                    //system.out.println("[PendingChangesServlet] Could not query " + changesTableName + ": " + e.getMessage());
                    logger.debug("Could not query {} for CR {}: {}", changesTableName, crId, e.getMessage());
                }
            }
            
            // If CR is completed/cancelled and we have no changes from mappings, load all from cr_changes_review
            if (isCRCompletedOrCancelled && changesArray.size() == 0) {
                logger.info("CR is completed/cancelled with no mappings found, loading all changes from cr_changes_review");
                
                String reviewSql = "SELECT * FROM cr_changes_review WHERE change_request_id = ? ORDER BY created_at ASC";
                try (PreparedStatement reviewStmt = conn.prepareStatement(reviewSql)) {
                    reviewStmt.setInt(1, crId);
                    try (ResultSet reviewRs = reviewStmt.executeQuery()) {
                        while (reviewRs.next()) {
                            int facetId = reviewRs.getInt("facet_id");
                            // Map facet ID to name
                            String facetNameFromId = null;
                            String facetNameLower = "";
                            switch (facetId) {
                                case 12: facetNameFromId = "Glossary"; facetNameLower = "glossary"; break;
                                case 11: facetNameFromId = "Data Set"; facetNameLower = "dataset"; break;
                                case 13: facetNameFromId = "System"; facetNameLower = "system"; break;
                                case 4: facetNameFromId = "Process"; facetNameLower = "process"; break;
                                default: facetNameFromId = "Unknown"; break;
                            }
                            
                            String fieldName = reviewRs.getString("field_name");
                            String oldValue = reviewRs.getString("old_value");
                            String newValue = reviewRs.getString("new_value");
                            String operation = reviewRs.getString("operation");
                            String tabName = reviewRs.getString("tab_name");
                            String objectName = reviewRs.getString("object_name");
                            String authorName = reviewRs.getString("author_name");
                            int objectId = reviewRs.getInt("object_id");
                            Integer nobjectId = reviewRs.getObject("nobject_id", Integer.class);
                            String areaKeyFromDb = reviewRs.getString("area_key");
                            String relatedName = reviewRs.getString("related_name");
                            String relationTypeName = reviewRs.getString("relation_type_name");
                            
                            // Convert values if needed (for KDE, Security Classification, etc.)
                            String convertedOldValue = oldValue;
                            String convertedNewValue = newValue;
                            if (fieldName != null && !fieldName.isEmpty()) {
                                String normalizedFieldName = fieldName.toLowerCase();
                                if (oldValue != null && !oldValue.isEmpty()) {
                                    convertedOldValue = resolveForeignKeyValue(conn, facetNameLower, normalizedFieldName, oldValue);
                                }
                                if (newValue != null && !newValue.isEmpty()) {
                                    convertedNewValue = resolveForeignKeyValue(conn, facetNameLower, normalizedFieldName, newValue);
                                }
                            }
                            
                            JsonObject changeObj = new JsonObject();
                            changeObj.addProperty("facetType", facetNameFromId != null ? facetNameFromId : "Unknown");
                            changeObj.addProperty("objectId", objectId);
                            changeObj.addProperty("objectName", objectName != null ? objectName : "");
                            changeObj.addProperty("tabName", tabName != null ? tabName : "Summary");
                            changeObj.addProperty("operation", operation != null ? operation : "Updated");
                            changeObj.addProperty("fieldName", fieldName);
                            changeObj.addProperty("oldValue", convertedOldValue != null ? convertedOldValue : "");
                            changeObj.addProperty("newValue", convertedNewValue != null ? convertedNewValue : "");
                            changeObj.addProperty("userName", authorName != null ? authorName : crAuthorFinal);
                            if (nobjectId != null) {
                                changeObj.addProperty("nobjectId", nobjectId);
                            }
                            if (areaKeyFromDb != null) {
                                changeObj.addProperty("areaKey", areaKeyFromDb);
                            }
                            if (relatedName != null) {
                                changeObj.addProperty("relatedName", relatedName);
                            }
                            if (relationTypeName != null) {
                                changeObj.addProperty("relationTypeName", relationTypeName);
                            }
                            
                            changesArray.add(changeObj);
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error loading all changes from cr_changes_review for CR {}: {}", crId, e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("[PendingChangesServlet] Database error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        
        //system.out.println("[PendingChangesServlet] Returning " + changesArray.size() + " changes for CR " + crId);
        
        // Save all changes to cr_changes_review for future reference
        // CRITICAL: When CR is completed/cancelled, NEVER delete and re-save cr_changes_review.
        // The snapshot was already saved before the complete/cancel action.
        // Re-saving would lose data because some cloned rows may have been deleted,
        // causing a mix of metadata (from live comparison) and non-metadata (from cr_changes_review cache) entries.
        // Only the non-metadata entries would be lost during re-save.
        if (isCRCompletedOrCancelled) {
            // CR is completed/cancelled - use existing cr_changes_review data as-is
            logger.info("CR {} is completed/cancelled - using existing entries from cr_changes_review (no re-save needed, {} changes loaded)", crId, changesArray.size());
            // Clean up metadata from any entries that were computed from live comparison (e.g., documents still existing)
            for (int i = 0; i < changesArray.size(); i++) {
                JsonObject changeObj = changesArray.get(i).getAsJsonObject();
                changeObj.remove("_facetName");
                changeObj.remove("_nobjectId");
                changeObj.remove("_areaKey");
            }
        } else {
            // CR is active (not completed/cancelled) - save fresh data from mappings comparison
            boolean hasMetadata = changesArray.size() > 0 && 
                changesArray.get(0).getAsJsonObject().has("_facetName");
            
            if (hasMetadata) {
                // Fresh data from mappings comparison - delete old and save new
                try {
                    crChangesReviewDAO.deleteChangesForCR(crId);
                    logger.info("Cleared existing cr_changes_review entries for CR {} before re-saving", crId);
                    
                    for (int i = 0; i < changesArray.size(); i++) {
                        JsonObject changeObj = changesArray.get(i).getAsJsonObject();
                        if (changeObj.has("_facetName") && changeObj.has("_nobjectId") && changeObj.has("_areaKey")) {
                            String facetNameForSave = changeObj.get("_facetName").getAsString();
                            int objectIdForSave = changeObj.has("objectId") ? changeObj.get("objectId").getAsInt() : 0;
                            int nobjectIdForSave = changeObj.get("_nobjectId").getAsInt();
                            String areaKeyForSave = changeObj.get("_areaKey").getAsString();
                            
                            saveChangeToReviewTable(crId, changeObj, facetNameForSave, objectIdForSave, nobjectIdForSave, areaKeyForSave, authorIdFinal, crAuthorFinal);
                        }
                        
                        // Remove metadata before returning (clean up)
                        changeObj.remove("_facetName");
                        changeObj.remove("_nobjectId");
                        changeObj.remove("_areaKey");
                    }
                    logger.info("Saved {} changes to cr_changes_review for CR {}", changesArray.size(), crId);
                } catch (SQLException e) {
                    logger.warn("Error saving changes to cr_changes_review: {}", e.getMessage());
                }
            } else {
                logger.info("CR {} has no fresh metadata to save ({} changes in array)", crId, changesArray.size());
            }
        }
        
        // Only write HTTP response if resp is not null (called from HTTP handler)
        if (resp != null) {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("changeRequestId", crId);
            response.add("changes", changesArray);
            
            resp.getWriter().write(gson.toJson(response));
        }
    }
    
    /**
     * Helper method to save a change to cr_changes_review table
     */
    private void saveChangeToReviewTable(int changeRequestId, JsonObject changeObj, String facetName, 
                                         int objectId, int nobjectId, String areaKey, int authorId, String authorName) 
                                         throws SQLException {
        String tabName = changeObj.has("tabName") ? changeObj.get("tabName").getAsString() : "";
        String componentName = changeObj.has("componentName") ? changeObj.get("componentName").getAsString() : null;
        String objectName = changeObj.has("objectName") ? changeObj.get("objectName").getAsString() : null;
        String operation = changeObj.has("operation") ? changeObj.get("operation").getAsString() : "";
        String fieldName = changeObj.has("fieldName") ? changeObj.get("fieldName").getAsString() : null;
        String oldValue = changeObj.has("oldValue") ? changeObj.get("oldValue").getAsString() : null;
        String newValue = changeObj.has("newValue") ? changeObj.get("newValue").getAsString() : null;
        String relatedType = changeObj.has("relatedType") ? changeObj.get("relatedType").getAsString() : null;
        String relatedName = changeObj.has("relatedName") ? changeObj.get("relatedName").getAsString() : null;
        String relationTypeName = changeObj.has("relationTypeName") ? changeObj.get("relationTypeName").getAsString() : null;
        
        // If componentName is null, try to extract from facetType
        if (componentName == null && changeObj.has("facetType")) {
            String facetType = changeObj.get("facetType").getAsString();
            if (facetType.contains(" X ")) {
                componentName = facetType;
            }
        }
        
        crChangesReviewDAO.saveChangeReview(
            changeRequestId, facetName, objectId, nobjectId, areaKey, tabName, 
            componentName, objectName, operation, fieldName, oldValue, newValue,
            relatedType, relatedName, relationTypeName, authorId, authorName, null
        );
    }
    
    /**
     * Parse facet type from CR reference (e.g., "Glossary 36" -> "glossary")
     */
    @SuppressWarnings("unused")
    private String parseFactetTypeFromReference(String reference) {
        if (reference == null) return null;
        String lower = reference.toLowerCase();
        if (lower.contains("glossary")) return "glossary";
        if (lower.contains("data set") || lower.contains("dataset")) return "dataset";
        if (lower.contains("system")) return "system";
        if (lower.contains("process")) return "process";
        return null;
    }
    
    /**
     * Parse object ID from CR reference (e.g., "Glossary 36" -> 36)
     */
    @SuppressWarnings("unused")
    private int parseObjectIdFromReference(String reference) {
        if (reference == null) return 0;
        // Extract last number from the reference
        String[] parts = reference.split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            try {
                return Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                // Continue to next part
            }
        }
        return 0;
    }
    
    /**
     * Get table name from facet type
     */
    private String getTableName(String facetType) {
        if (facetType == null) return null;
        switch (facetType.toLowerCase()) {
            case "glossary": return "glossary";
            case "dataset": return "dataset";
            case "system": return "system";
            case "process": return "process";
            default: return null;
        }
    }
    
    /**
     * Get display name from facet type
     */
    @SuppressWarnings("unused")
    private String getDisplayName(String facetType) {
        if (facetType == null) return "Unknown";
        switch (facetType.toLowerCase()) {
            case "glossary": return "Glossary";
            case "dataset": return "Data Set";
            case "system": return "System";
            case "process": return "Process";
            default: return facetType;
        }
    }
    
    /**
     * Get all fields of an object for CREATE CR display
     */
    private List<Map<String, String>> getObjectFields(Connection conn, String tableName, int objectId, String facetName) throws SQLException {
        List<Map<String, String>> fields = new ArrayList<>();
        String[] columnsToCompare = getColumnsToCompare(facetName);
        
        if (columnsToCompare.length == 0) {
            //system.out.println("[PendingChangesServlet] No columns defined for facet: " + facetName);
            return fields;
        }
        
        // Query each column individually to handle missing columns gracefully
        for (String col : columnsToCompare) {
            try {
                String sql = "SELECT " + col + " FROM " + tableName + " WHERE ID = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, objectId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Object val = rs.getObject(1);
                            String displayVal = null;
                            
                            // Handle boolean fields specially (DQ_Automation, permissions)
                            if (col.equalsIgnoreCase("DQ_Automation") || 
                                col.equalsIgnoreCase("cancreate") || col.equalsIgnoreCase("canread") ||
                                col.equalsIgnoreCase("canupdate") || col.equalsIgnoreCase("candelete") ||
                                col.equalsIgnoreCase("canarchive")) {
                                if (val == null) {
                                    displayVal = "false";
                                } else {
                                    int intVal = rs.getInt(1);
                                    displayVal = (intVal == 1) ? "true" : "false";
                                }
                            } else {
                                // For other fields, only include if not null/empty
                                if (val != null && !val.toString().isEmpty()) {
                                    // Normalize column name to lowercase for resolveForeignKeyValue (it expects lowercase)
                                    String normalizedCol = col != null ? col.toLowerCase() : "";
                                    // Resolve FK values to display names
                                    displayVal = resolveForeignKeyValue(conn, facetName, normalizedCol, val.toString());
                                }
                            }
                            
                            // Add field if we have a display value (always for boolean fields)
                            if (displayVal != null) {
                                Map<String, String> field = new HashMap<>();
                                field.put("fieldName", formatFieldName(col));
                                field.put("value", displayVal);
                                fields.add(field);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                // Column might not exist in this table, skip it
                //system.out.println("[PendingChangesServlet] Column " + col + " not found in " + tableName + ": " + e.getMessage());
            }
        }
        
        //system.out.println("[PendingChangesServlet] getObjectFields returning " + fields.size() + " fields for " + tableName + " ID " + objectId);
        return fields;
    }
    
    /**
     * Resolve foreign key values to their display names
     */
    private String resolveForeignKeyValue(Connection conn, String facetName, String columnName, String value) {
        if (value == null || value.isEmpty()) return value;
        
        String col = columnName.toLowerCase();
        
        // Handle boolean fields (DQ_Automation) - convert 0/1 to false/true
        if (col.equals("dq_automation") || col.equals("dqautomation")) {
            try {
                int intVal = Integer.parseInt(value);
                return (intVal == 1) ? "true" : "false";
            } catch (NumberFormatException e) {
                // If not a number, check if it's already "true"/"false"
                if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
                    return "true";
                }
                return "false";
            }
        }
        
        try {
            int id = Integer.parseInt(value);
            if (id <= 0) return value;
            
            // Status field - lookup from status table (lowercase primaryname)
            if (col.equals("status")) {
                return lookupValue(conn, "status", id, "primaryname", value);
            }
            
            // Lifecycle field - lookup based on facet
            if (col.equals("lifecycle") || col.equals("lifecycle_status")) {
                if ("dataset".equals(facetName)) {
                    return lookupValue(conn, "dataset_lifecycle", id, "PrimaryName", value);
                } else if ("glossary".equals(facetName)) {
                    return lookupValue(conn, "glossary_lifecycle", id, "PrimaryName", value);
                } else if ("system".equals(facetName)) {
                    return lookupValue(conn, "system_lifecycle", id, "Name", value);
                } else if ("process".equals(facetName)) {
                    return lookupValue(conn, "process_lifecycle_status", id, "PrimaryName", value);
                }
                return value;
            }
            
            // Dataset Type - lookup from dataset_type table
            if (col.equals("datasettype")) {
                return lookupValue(conn, "dataset_type", id, "PrimaryName", value);
            }
            
            // Access Control Type (Dataset) - lookup from viewing table (FK to viewing.id)
            if (col.equals("accesscontroltype") && "dataset".equals(facetName)) {
                return lookupValue(conn, "viewing", id, "Name", value);
            }
            
            // Master Source - this is a FK to system table
            if (col.equals("mastersource")) {
                return lookupValue(conn, "system", id, "Name", value);
            }
            
            // Glossary reference - lookup from glossary table
            if (col.equals("glossary")) {
                return lookupValue(conn, "glossary", id, "Name", value);
            }
            
            // Parent ID - lookup from the same facet table
            if (col.equals("parent_id") || col.equals("parentid")) {
                String parentTable = facetName.toLowerCase();
                String nameCol;
                if ("dataset".equals(parentTable)) {
                    nameCol = "PrimaryName";
                } else if ("process".equals(parentTable)) {
                    nameCol = "primaryname";
                } else if ("system".equals(parentTable) || "glossary".equals(parentTable)) {
                    nameCol = "Name";
                } else {
                    nameCol = "Name";
                }
                return lookupValue(conn, parentTable, id, nameCol, value);
            }
            
            // Type field for system/glossary
            if (col.equals("type") && "system".equals(facetName)) {
                return lookupValue(conn, "system_type", id, "Name", value);
            }
            if (col.equals("type") && "glossary".equals(facetName)) {
                return lookupValue(conn, "glossary_type", id, "Name", value);
            }
            
            // Process Type
            if (col.equals("type") && "process".equals(facetName)) {
                return lookupValue(conn, "process_type", id, "PrimaryName", value);
            }
            
            // Format Type (Glossary) - column is Format_type, uses Name column
            if (col.equals("format_type") && "glossary".equals(facetName)) {
                return lookupValue(conn, "glossary_format_type", id, "Name", value);
            }
            
            // Classification (System) - uses Name column
            if (col.equals("classification") && "system".equals(facetName)) {
                return lookupValue(conn, "system_classification", id, "Name", value);
            }
            
            // Duration Type (Process) - column is duration_type
            if (col.equals("duration_type") && "process".equals(facetName)) {
                return lookupValue(conn, "process_duration_type", id, "PrimaryName", value);
            }
            
            // Process Automation (Process) - column is processautomation_id
            if ((col.equals("processautomation_id") || col.equals("processautomation")) && "process".equals(facetName)) {
                return lookupValue(conn, "process_automation", id, "primaryname", value);
            }
            
            // Process Classification (Process) - column is processclass_id
            if ((col.equals("processclass_id") || col.equals("processclass")) && "process".equals(facetName)) {
                return lookupValue(conn, "process_class", id, "primaryname", value);
            }
            
            // Is_Public / is_Public / ispublic - lookup from viewing table
            // Handle both Glossary (Is_Public) and System (is_Public) and Process (ispublic)
            if (col.equals("is_public") || col.equals("ispublic")) {
                return lookupValue(conn, "viewing", id, "Name", value);
            }
            
            // KDE (Glossary) - lookup from glossary_kde_type table
            // Handle both "kde" and "KDE" column names
            String colLower = col != null ? col.toLowerCase() : "";
            if (colLower.equals("kde") && "glossary".equals(facetName)) {
                return lookupValue(conn, "glossary_kde_type", id, "Name", value);
            }
            
            // Security Classification (Glossary) - lookup from security_classification table
            // Handle "security_classification", "Security_Classification", "securityclassification", etc.
            if ((colLower.equals("security_classification") || colLower.equals("securityclassification")) && "glossary".equals(facetName)) {
                return lookupValue(conn, "security_classification", id, "Name", value);
            }
            
            // CIA Ratings (Glossary and System) - lookup from cia_rating table (uses "Values" column)
            if (col.equals("confidentiality_rating")) {
                if ("glossary".equals(facetName) || "system".equals(facetName)) {
                    return lookupValue(conn, "cia_rating", id, "Values", value);
                }
            }
            if (col.equals("integrity_rating")) {
                if ("glossary".equals(facetName) || "system".equals(facetName)) {
                    return lookupValue(conn, "cia_rating", id, "Values", value);
                }
            }
            if (col.equals("availability_rating")) {
                if ("glossary".equals(facetName) || "system".equals(facetName)) {
                    return lookupValue(conn, "cia_rating", id, "Values", value);
                }
            }
            
            // External (System) - this is a boolean (tinyint), convert to Yes/No
            if (col.equals("external") && "system".equals(facetName)) {
                if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
                    return "Yes";
                } else if ("0".equals(value) || "false".equalsIgnoreCase(value)) {
                    return "No";
                }
                return value;
            }
            
        } catch (NumberFormatException e) {
            // Not a number, return as-is (it's already a text value)
        }
        
        return value;
    }
    
    /**
     * Lookup a value from a reference table
     * Handles both ID (uppercase) and id (lowercase) column names
     */
    private String lookupValue(Connection conn, String tableName, int id, String nameColumn, String defaultValue) {
        try {
            // Try ID (uppercase) first, then id (lowercase) for tables like cia_rating
            String sql = "SELECT " + nameColumn + " FROM " + tableName + " WHERE ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.isEmpty()) {
                            return name;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            // If ID (uppercase) fails, try id (lowercase) for tables like cia_rating
            try {
                String sql = "SELECT " + nameColumn + " FROM " + tableName + " WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String name = rs.getString(1);
                            if (name != null && !name.isEmpty()) {
                                return name;
                            }
                        }
                    }
                }
            } catch (SQLException e2) {
                //system.out.println("[PendingChangesServlet] Could not lookup " + tableName + " ID " + id + ": " + e2.getMessage());
            }
        }
        return defaultValue;
    }
    
    /**
     * Get tab name from area_key
     * For impact relationships, include the relation type to ensure separate rows for each impact type
     */
    private String getTabNameFromAreaKey(String areaKey) {
        if (areaKey == null) return "Summary";
        if ("summary".equals(areaKey)) return "Summary";
        if (areaKey.startsWith("documents#")) return "Documents"; // Separate documents from summary
        if (areaKey.startsWith("impact#")) {
            // Extract the relation type from the area key (e.g., "impact#process_X_system" -> "System")
            String relationType = areaKey.substring("impact#".length());
            String relatedTypeName = extractRelatedTypeName(relationType);
            return "Impact - " + relatedTypeName;  // Make each impact type unique
        }
        if (areaKey.startsWith("stakeholders#")) return "Stakeholders";
        if (areaKey.startsWith("relationships#")) {
            String relTable = areaKey.substring("relationships#".length());
            switch (relTable) {
                case "process_x_process": return "Predecessors";
                case "glossary_x_glossary": return "Relationships";
                case "glossary_x_system": return "Strategic Source";
                case "attribute_x_attribute": return "Relationships"; // Changed from "Attribute Relationships" to "Relationships" for dataset
                case "dataset_mastersource": return "Data Content";
                default: return "Relationships";
            }
        }
        if (areaKey.equals("summary#attribute")) return "Attributes";
        if (areaKey.equals("summary#dataset_value_info")) return "Values";
        return "Summary";
    }
    
    /**
     * Get component name for relationship table (for CHANGES TO REVIEW table Component column)
     */
    private String getComponentNameForRelationship(String relTable) {
        switch (relTable) {
            case "process_x_process": return "Process X Process";
            case "glossary_x_glossary": return "Glossary X Glossary";
            case "glossary_x_system": return "Glossary X System";
            case "attribute_x_attribute": return "Attribute X Attribute";
            case "dataset_mastersource": return "System X Dataset";
            default: return "Relationship";
        }
    }
    
    /**
     * Get object name from the main table
     */
    private String getObjectName(Connection conn, String tableName, int objectId, String nameColumn) throws SQLException {
        String sql = "SELECT " + nameColumn + " FROM " + tableName + " WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return tableName + " " + objectId;
    }
    
    /**
     * Compare two objects and return the field differences
     */
    private List<Map<String, String>> compareObjects(Connection conn, String tableName, int originalId, int clonedId, String facetName) throws SQLException {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Get columns to compare based on facet type
        String[] columnsToCompare = getColumnsToCompare(facetName);
        if (columnsToCompare.length == 0) {
            //system.out.println("[PendingChangesServlet] No columns defined for compare: " + facetName);
            return changes;
        }
        
        //system.out.println("[PendingChangesServlet] compareObjects: " + tableName + " originalId=" + originalId + " clonedId=" + clonedId);
        
        // Compare each column individually to handle missing columns gracefully
        for (String col : columnsToCompare) {
            try {
                String sql = "SELECT " + col + " FROM " + tableName + " WHERE ID = ?";
                String originalVal = null;
                String clonedVal = null;
                
                // Get original value
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, originalId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            // Handle boolean fields specially (DQ_Automation, permissions)
                            if (col.equalsIgnoreCase("DQ_Automation") || 
                                col.equalsIgnoreCase("cancreate") || col.equalsIgnoreCase("canread") ||
                                col.equalsIgnoreCase("canupdate") || col.equalsIgnoreCase("candelete") ||
                                col.equalsIgnoreCase("canarchive")) {
                                Object obj = rs.getObject(1);
                                if (obj == null) {
                                    originalVal = "false";
                                } else {
                                    int intVal = rs.getInt(1);
                                    originalVal = (intVal == 1) ? "true" : "false";
                                }
                            } else {
                                // Use getString which handles null properly and returns null for NULL database values
                                originalVal = rs.getString(1);
                            }
                        }
                    }
                }
                
                // Get cloned value
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, clonedId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            // Handle boolean fields specially (DQ_Automation, permissions)
                            if (col.equalsIgnoreCase("DQ_Automation") || 
                                col.equalsIgnoreCase("cancreate") || col.equalsIgnoreCase("canread") ||
                                col.equalsIgnoreCase("canupdate") || col.equalsIgnoreCase("candelete") ||
                                col.equalsIgnoreCase("canarchive")) {
                                Object obj = rs.getObject(1);
                                if (obj == null) {
                                    clonedVal = "false";
                                } else {
                                    int intVal = rs.getInt(1);
                                    clonedVal = (intVal == 1) ? "true" : "false";
                                }
                            } else {
                                // Use getString which handles null properly and returns null for NULL database values
                                clonedVal = rs.getString(1);
                            }
                        }
                    }
                }
                
                // Normalize null and empty strings to be treated the same
                // For boolean fields, we already converted null to "false", so no need to normalize
                String normalizedOriginal = (originalVal == null || originalVal.trim().isEmpty()) ? null : originalVal.trim();
                String normalizedCloned = (clonedVal == null || clonedVal.trim().isEmpty()) ? null : clonedVal.trim();
                
                // For boolean fields (DQ_Automation, permissions), always show the value even if both are false
                boolean isBooleanField = col.equalsIgnoreCase("DQ_Automation") || 
                                       col.equalsIgnoreCase("cancreate") || col.equalsIgnoreCase("canread") ||
                                       col.equalsIgnoreCase("canupdate") || col.equalsIgnoreCase("candelete") ||
                                       col.equalsIgnoreCase("canarchive");
                
                // Only report changes if there's an actual meaningful difference
                // Skip if both are null/empty (no change), except for boolean fields
                if (!isBooleanField && normalizedOriginal == null && normalizedCloned == null) {
                    // Both are null/empty, no change
                    continue;
                }
                
                // For boolean fields, ensure we have values (default to "false" if null)
                if (isBooleanField) {
                    if (normalizedOriginal == null) normalizedOriginal = "false";
                    if (normalizedCloned == null) normalizedCloned = "false";
                }
                
                // Compare normalized values
                // Show field if values are different
                if (!Objects.equals(normalizedOriginal, normalizedCloned)) {
                    // Normalize column name to lowercase for resolveForeignKeyValue (it expects lowercase)
                    String normalizedCol = col != null ? col.toLowerCase() : "";
                    
                    // For Name and Description fields, they are direct text values, not Foreign Keys
                    // Skip resolveForeignKeyValue for these fields to avoid any potential issues
                    boolean isDirectTextField = (col != null && 
                        (col.equalsIgnoreCase("Name") || col.equalsIgnoreCase("Description") || 
                         col.equalsIgnoreCase("Long_Name") || col.equalsIgnoreCase("PrimaryName") ||
                         col.equalsIgnoreCase("definition") || col.equalsIgnoreCase("RefNumber") ||
                         col.equalsIgnoreCase("Ref_Number") || col.equalsIgnoreCase("refnumber") ||
                         col.equalsIgnoreCase("URL") || col.equalsIgnoreCase("Examples") ||
                         col.equalsIgnoreCase("Business_Logic") || col.equalsIgnoreCase("Usage") ||
                         col.equalsIgnoreCase("input_description") || col.equalsIgnoreCase("output_description")));
                    
                    // Resolve FK values to display names (skip for boolean fields and direct text fields)
                    String displayOldVal = isBooleanField ? normalizedOriginal : 
                        (isDirectTextField ? normalizedOriginal : resolveForeignKeyValue(conn, facetName, normalizedCol, normalizedOriginal));
                    String displayNewVal = isBooleanField ? normalizedCloned : 
                        (isDirectTextField ? normalizedCloned : resolveForeignKeyValue(conn, facetName, normalizedCol, normalizedCloned));
                    
                    // Normalize display values (handle null and empty strings)
                    String normalizedDisplayOld = (displayOldVal == null || displayOldVal.trim().isEmpty()) ? null : displayOldVal.trim();
                    String normalizedDisplayNew = (displayNewVal == null || displayNewVal.trim().isEmpty()) ? null : displayNewVal.trim();
                    
                    // CRITICAL: Compare display values after resolution
                    // If display values are the same, skip this change (even if raw values were different)
                    // This handles cases where NULL vs empty string, or different representations of the same value
                    if (Objects.equals(normalizedDisplayOld, normalizedDisplayNew)) {
                        // Display values are the same, no actual change
                        continue;
                    }
                    
                    // Only add if there's a meaningful difference (not both empty/null)
                    // Skip if both are null/empty after resolution
                    if (normalizedDisplayOld == null && normalizedDisplayNew == null) {
                        // Both are null/empty after resolution, skip this change
                        continue;
                    }
                    
                    // Only add if at least one value is non-empty
                    if (normalizedDisplayOld != null || normalizedDisplayNew != null) {
                        Map<String, String> change = new HashMap<>();
                        change.put("fieldName", formatFieldName(col));
                        change.put("oldValue", normalizedDisplayOld != null ? normalizedDisplayOld : "");
                        change.put("newValue", normalizedDisplayNew != null ? normalizedDisplayNew : "");
                        changes.add(change);
                        logger.debug("[compareObjects] Field changed: {} '{}' -> '{}'", col, normalizedDisplayOld, normalizedDisplayNew);
                    }
                }
            } catch (SQLException e) {
                // Column might not exist, skip
                //system.out.println("[PendingChangesServlet] Column " + col + " error: " + e.getMessage());
            }
        }

        if ("glossary".equalsIgnoreCase(facetName)) {
            appendGlossaryAliasChanges(conn, originalId, clonedId, changes);
        }

        //system.out.println("[PendingChangesServlet] compareObjects returning " + changes.size() + " changes");
        return changes;
    }

    /**
     * Aliases live in glossary_alias_names, not on the glossary row — include them in View Changes / changes table.
     */
    private void appendGlossaryAliasChanges(Connection conn, int originalId, int clonedId,
                                            List<Map<String, String>> changes) throws SQLException {
        String oldJoined = loadGlossaryAliasesJoined(conn, originalId);
        String newJoined = loadGlossaryAliasesJoined(conn, clonedId);
        if (Objects.equals(oldJoined, newJoined)) {
            return;
        }
        Map<String, String> change = new HashMap<>();
        change.put("fieldName", "Aliases");
        change.put("oldValue", oldJoined != null ? oldJoined : "");
        change.put("newValue", newJoined != null ? newJoined : "");
        changes.add(change);
    }

    private String loadGlossaryAliasesJoined(Connection conn, int glossaryId) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT Name FROM glossary_alias_names WHERE Glossary_id = ? ORDER BY Name";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, glossaryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String n = rs.getString(1);
                    if (n != null && !n.trim().isEmpty()) {
                        names.add(n.trim());
                    }
                }
            }
        }
        return String.join(", ", names);
    }
    
    /**
     * Get columns to compare based on facet type
     * Note: Column names must match exactly as they appear in the database schema
     */
    private String[] getColumnsToCompare(String facetName) {
        switch (facetName.toLowerCase()) {
            case "glossary":
                // ⚠️ FIX: Add "Format" (format description) to the list
                return new String[]{"Name", "Description", "Format", "Format_type", "Business_Logic", "Examples", 
                                   "Ref_Number", "Parent_ID", "Status", "Lifecycle", "Is_Public", "Type",
                                   "KDE", "Security_Classification", "Confidentiality_Rating", 
                                   "Integrity_Rating", "Availability_Rating"};
            case "dataset":
                return new String[]{"PrimaryName", "RefNumber", "definition", "Usage", 
                                   "status", "lifecycle", "DatasetType", "AccessControlType", "MasterSource", "glossary"};
            case "system":
                return new String[]{"Name", "Long_Name", "Description", "Type", "External", "URL",
                                   "status", "Lifecycle", "is_Public", "Classification",
                                   "parent_id", "Confidentiality_Rating", "Integrity_Rating", "Availability_Rating",
                                   "DQ_Automation"};
            case "process":
                return new String[]{"primaryname", "refnumber", "description", "input_description", "output_description",
                                   "status", "lifecycle_status", "type", "duration", "duration_type", "parentid", "ispublic",
                                   "processautomation_id", "processclass_id", 
                                   "cancreate", "canread", "canupdate", "candelete", "canarchive"};
            default:
                return new String[]{};
        }
    }
    
    /**
     * Format column name for display (CamelCase or snake_case to Title Case)
     * Also handles special cases for proper field name display
     */
    private String formatFieldName(String columnName) {
        if (columnName == null) return "";
        
        // Special mappings for common field names to ensure correct display
        String colLower = columnName.toLowerCase();
        if (colLower.equals("accesscontroltype")) {
            return "Viewing";
        }
        if (colLower.equals("format_type")) {
            return "Format Type";
        }
        if (colLower.equals("format")) {
            return "Format Description";
        }
        if (colLower.equals("lifecycle_status")) {
            return "Lifecycle";
        }
        if (colLower.equals("duration_type")) {
            return "Duration Type";
        }
        if (colLower.equals("is_public") || colLower.equals("ispublic")) {
            return "Is Public";
        }
        if (colLower.equals("input_description")) {
            return "Input Description";
        }
        if (colLower.equals("output_description")) {
            return "Output Description";
        }
        if (colLower.equals("long_name")) {
            return "Long Name";
        }
        if (colLower.equals("ref_number") || colLower.equals("refnumber")) {
            return "Ref Number";
        }
        if (colLower.equals("business_logic")) {
            return "Business Logic";
        }
        if (colLower.equals("parent_id") || colLower.equals("parentid")) {
            return "Parent";
        }
        if (colLower.equals("master_source") || colLower.equals("mastersource")) {
            return "Master Source";
        }
        if (colLower.equals("dataset_type") || colLower.equals("datasettype")) {
            return "Type";
        }
        if (colLower.equals("kde")) {
            return "KDE";
        }
        if (colLower.equals("security_classification") || colLower.equals("securityclassification")) {
            return "Security Classification";
        }
        if (colLower.equals("confidentiality_rating") || colLower.equals("confidentialityrating")) {
            return "Confidentiality Rating";
        }
        if (colLower.equals("integrity_rating") || colLower.equals("integrityrating")) {
            return "Integrity Rating";
        }
        if (colLower.equals("availability_rating") || colLower.equals("availabilityrating")) {
            return "Availability Rating";
        }
        if (colLower.equals("classification")) {
            return "Classification";
        }
        if (colLower.equals("lifecycle")) {
            return "Lifecycle";
        }
        if (colLower.equals("dq_automation") || colLower.equals("dqautomation")) {
            return "Automatically Control Local Rules";
        }
        if (colLower.equals("processautomation_id") || colLower.equals("processautomation")) {
            return "Automation";
        }
        if (colLower.equals("processclass_id") || colLower.equals("processclass")) {
            return "Classification";
        }
        if (colLower.equals("cancreate")) {
            return "Can Create";
        }
        if (colLower.equals("canread")) {
            return "Can Read";
        }
        if (colLower.equals("canupdate")) {
            return "Can Update";
        }
        if (colLower.equals("candelete")) {
            return "Can Delete";
        }
        if (colLower.equals("canarchive")) {
            return "Can Archive";
        }
        
        // Handle common column name patterns
        String formatted = columnName
            .replace("_ID", "")
            .replace("_Field", "")
            .replace("_", " ");
        
        // Insert space before capitals in camelCase
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(formatted.charAt(i-1))) {
                result.append(' ');
            }
            result.append(c);
        }
        
        // Capitalize first letter of each word
        String[] words = result.toString().split(" ");
        StringBuilder finalResult = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                if (finalResult.length() > 0) finalResult.append(' ');
                finalResult.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    finalResult.append(word.substring(1).toLowerCase());
                }
            }
        }
        
        return finalResult.toString();
    }
    
    /**
     * Compare impact relationships between original and cloned object
     * Only returns actual differences (added, removed, modified relationships)
     */
    private List<Map<String, String>> compareImpactRelationships(Connection conn, String facetName, 
            int originalId, int clonedId, String relationType) throws SQLException {
        List<Map<String, String>> changes = new ArrayList<>();
        
        //system.out.println("[PendingChangesServlet] compareImpactRelationships: facet=" + facetName + 
          //  ", originalId=" + originalId + ", clonedId=" + clonedId + ", relationType=" + relationType);
        
        // Determine the relationship table based on facet and relation type
        String relationTable = getRelationshipTable(facetName, relationType);
        if (relationTable == null) {
            //system.out.println("[PendingChangesServlet] Could not determine relationship table for: " + relationType);
            return changes;
        }
        
        @SuppressWarnings("unused")
        String fkColumn = getForeignKeyColumn(facetName);
        String relatedTypeName = extractRelatedTypeName(relationType);
        //system.out.println("[PendingChangesServlet] Using table: " + relationTable + ", FK: " + fkColumn);
        
        // Get relationships from original object
        List<Map<String, String>> originalRels = getImpactRelationships(conn, facetName, relationType, originalId);
        //system.out.println("[PendingChangesServlet] Original relationships: " + originalRels.size());
        
        // Get relationships from cloned object
        List<Map<String, String>> clonedRels = getImpactRelationships(conn, facetName, relationType, clonedId);
        //system.out.println("[PendingChangesServlet] Cloned relationships: " + clonedRels.size());
        
        // Build sets for comparison using related object ID as key
        Map<String, Map<String, String>> originalMap = new HashMap<>();
        for (Map<String, String> rel : originalRels) {
            String key = rel.get("relatedId");
            if (key != null) originalMap.put(key, rel);
        }
        
        Map<String, Map<String, String>> clonedMap = new HashMap<>();
        for (Map<String, String> rel : clonedRels) {
            String key = rel.get("relatedId");
            if (key != null) clonedMap.put(key, rel);
        }
        
        // Build lists for pairing removed and added as "Updated"
        List<Map<String, String>> removedRels = new ArrayList<>();
        List<Map<String, String>> addedRels = new ArrayList<>();
        
        // Find added relationships (in cloned but not in original)
        for (String key : clonedMap.keySet()) {
            if (!originalMap.containsKey(key)) {
                addedRels.add(clonedMap.get(key));
            }
        }
        
        // Find removed relationships (in original but not in cloned)
        for (String key : originalMap.keySet()) {
            if (!clonedMap.containsKey(key)) {
                removedRels.add(originalMap.get(key));
            }
        }
        
        // Pair removed and added as "Updated" entries
        int pairCount = Math.min(removedRels.size(), addedRels.size());
        for (int i = 0; i < pairCount; i++) {
            Map<String, String> oldRel = removedRels.get(i);
            Map<String, String> newRel = addedRels.get(i);
            
            Map<String, String> change = new HashMap<>();
            change.put("operation", "Updated");
            change.put("relatedType", relatedTypeName);
            change.put("relatedName", newRel.get("relatedName"));
            change.put("fieldName", relatedTypeName);
            change.put("oldValue", oldRel.get("relatedName"));
            change.put("newValue", newRel.get("relatedName"));
            // Add relationTypeName from newRel if available
            String newRelType = newRel.get("relationTypeName");
            if (newRelType != null && !newRelType.isEmpty()) {
                change.put("relationTypeName", newRelType);
            }
            changes.add(change);
            
            // Also add relationship type change if both have types
            String oldRelType = oldRel.get("relationTypeName");
            if (oldRelType != null || newRelType != null) {
                Map<String, String> rtChange = new HashMap<>();
                rtChange.put("operation", "Updated");
                rtChange.put("relatedType", relatedTypeName);
                rtChange.put("relatedName", newRel.get("relatedName"));
                rtChange.put("fieldName", "Relationship Type");
                rtChange.put("oldValue", oldRelType != null ? oldRelType : "");
                rtChange.put("newValue", newRelType != null ? newRelType : "");
                if (newRelType != null && !newRelType.isEmpty()) {
                    rtChange.put("relationTypeName", newRelType);
                }
                changes.add(rtChange);
            }
        }
        
        // Handle remaining removals (pure deletions)
        for (int i = pairCount; i < removedRels.size(); i++) {
            Map<String, String> oldRel = removedRels.get(i);
            Map<String, String> change = new HashMap<>();
            change.put("operation", "Deleted");
            change.put("relatedType", relatedTypeName);
            change.put("relatedName", oldRel.get("relatedName"));
            change.put("fieldName", relatedTypeName);
            change.put("oldValue", oldRel.get("relatedName"));
            change.put("newValue", "");
            changes.add(change);
        }
        
        // Handle remaining additions (pure insertions)
        for (int i = pairCount; i < addedRels.size(); i++) {
            Map<String, String> newRel = addedRels.get(i);
            Map<String, String> change = new HashMap<>();
            change.put("operation", "Inserted");
            change.put("relatedType", relatedTypeName);
            change.put("relatedName", newRel.get("relatedName"));
            change.put("fieldName", relatedTypeName);
            change.put("oldValue", "");
            change.put("newValue", newRel.get("relatedName"));
            // Add relationTypeName from newRel if available
            String newRelType = newRel.get("relationTypeName");
            if (newRelType != null && !newRelType.isEmpty()) {
                change.put("relationTypeName", newRelType);
            }
            changes.add(change);
            
            // Also add relationship type if available
            if (newRelType != null && !newRelType.isEmpty()) {
                Map<String, String> rtChange = new HashMap<>();
                rtChange.put("operation", "Inserted");
                rtChange.put("relatedType", relatedTypeName);
                rtChange.put("relatedName", newRel.get("relatedName"));
                rtChange.put("fieldName", "Relationship Type");
                rtChange.put("oldValue", "");
                rtChange.put("newValue", newRelType);
                rtChange.put("relationTypeName", newRelType);
                changes.add(rtChange);
            }
        }
        
        // Find modified relationships (same related object but different relationship type)
        for (String key : clonedMap.keySet()) {
            if (originalMap.containsKey(key)) {
                Map<String, String> oldRel = originalMap.get(key);
                Map<String, String> newRel = clonedMap.get(key);
                
                String oldRelType = oldRel.get("relationTypeName");
                String newRelType = newRel.get("relationTypeName");
                
                if (oldRelType != null && newRelType != null && !oldRelType.equals(newRelType)) {
                    Map<String, String> change = new HashMap<>();
                    change.put("operation", "Updated");
                    change.put("relatedType", relatedTypeName);
                    change.put("relatedName", newRel.get("relatedName"));
                    change.put("fieldName", "Relationship Type");
                    change.put("oldValue", oldRelType);
                    change.put("newValue", newRelType);
                    change.put("relationTypeName", newRelType);
                    changes.add(change);
                }
            }
        }
        
        //system.out.println("[PendingChangesServlet] Total impact changes: " + changes.size());
        return changes;
    }
    
    /**
     * Get impact relationships for a specific object
     */
    private List<Map<String, String>> getImpactRelationships(Connection conn, String facetName, 
            String relationType, int objectId) {
        List<Map<String, String>> relationships = new ArrayList<>();
        
        String sql = buildImpactRelationshipQuery(facetName, relationType, null, null);
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> rel = new HashMap<>();
                    
                    // Get related object ID
                    String relatedId = null;
                    try { relatedId = String.valueOf(rs.getInt("relatedId")); } 
                    catch (SQLException e) { 
                        // Try alternative column names (both uppercase and lowercase variants)
                        try { relatedId = String.valueOf(rs.getInt("Product_ID")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("productid")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("Client_ID")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("Legal_ID")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("System_ID")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("system_id")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("Process_ID")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("process_id")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("Glossary_ID")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("projectid")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("policy_id")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("interface_id")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("datasetid")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("attributeid")); } catch (SQLException e2) {}
                    }
                    rel.put("relatedId", relatedId);
                    
                    // Get related object name
                    String relatedName = null;
                    try { relatedName = rs.getString("relatedName"); } catch (SQLException e) {}
                    rel.put("relatedName", relatedName != null ? relatedName : "Unknown");
                    
                    // Get relationship type name
                    String relationTypeName = null;
                    try { relationTypeName = rs.getString("relationTypeName"); } catch (SQLException e) {}
                    rel.put("relationTypeName", relationTypeName);
                    
                    relationships.add(rel);
                }
            }
        } catch (SQLException e) {
            System.err.println("[PendingChangesServlet] Error getting impact relationships: " + e.getMessage());
        }
        
        return relationships;
    }
    
    /**
     * Build SQL query for impact relationships based on facet and relation type
     */
    private String buildImpactRelationshipQuery(String facetName, String relationType, String relationTable, String fkColumn) {
        String lower = relationType.toLowerCase();
        String prefix = facetName.toLowerCase();
        
        // Dataset impact relationships
        if ("dataset".equals(prefix)) {
            if (lower.contains("product")) {
                return "SELECT pxd.Product_ID as relatedId, p.primaryname as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM product_x_dataset pxd " +
                       "LEFT JOIN product p ON pxd.Product_ID = p.id " +
                       "LEFT JOIN product_x_dataset_relationtype rt ON pxd.Product_Dataset_Relation_Type = rt.ID " +
                       "WHERE pxd.Dataset_ID = ?";
            } else if (lower.contains("client")) {
                return "SELECT cxd.Client_ID as relatedId, c.PrimaryName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM client_x_dataset cxd " +
                       "LEFT JOIN client c ON cxd.Client_ID = c.ID " +
                       "LEFT JOIN client_x_dataset_relationtype rt ON cxd.RelationType = rt.ID " +
                       "WHERE cxd.Dataset_ID = ?";
            } else if (lower.contains("legal")) {
                return "SELECT dxl.Legal_ID as relatedId, l.ShortName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM dataset_x_legal dxl " +
                       "LEFT JOIN legal l ON dxl.Legal_ID = l.ID " +
                       "LEFT JOIN dataset_x_legal_relationtype rt ON dxl.RelationType = rt.ID " +
                       "WHERE dxl.Dataset_ID = ?";
            }
        }
        
        // Process impact relationships
        if ("process".equals(prefix)) {
            if (lower.contains("system")) {
                return "SELECT pxs.system_id as relatedId, s.Name as relatedName, rt.primaryname as relationTypeName " +
                       "FROM process_x_system pxs " +
                       "LEFT JOIN system s ON pxs.system_id = s.ID " +
                       "LEFT JOIN process_x_system_relationtype rt ON pxs.relationtype = rt.id " +
                       "WHERE pxs.process_id = ?";
            } else if (lower.contains("product")) {
                return "SELECT pxp.productid as relatedId, p.primaryname as relatedName, rt.primaryname as relationTypeName " +
                       "FROM product_x_process pxp " +
                       "LEFT JOIN product p ON pxp.productid = p.id " +
                       "LEFT JOIN product_x_process_relationtype rt ON pxp.relationtype = rt.id " +
                       "WHERE pxp.processid = ?";
            } else if (lower.contains("client")) {
                return "SELECT cxp.Client_ID as relatedId, c.PrimaryName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM client_x_process cxp " +
                       "LEFT JOIN client c ON cxp.Client_ID = c.ID " +
                       "LEFT JOIN client_x_process_relationtype rt ON cxp.RelationType = rt.ID " +
                       "WHERE cxp.Process_ID = ?";
            } else if (lower.contains("glossary")) {
                return "SELECT gxp.Glossary_ID as relatedId, g.Name as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM glossary_x_process gxp " +
                       "LEFT JOIN glossary g ON gxp.Glossary_ID = g.ID " +
                       "LEFT JOIN glossary_x_process_relationtype rt ON gxp.RelationType = rt.ID " +
                       "WHERE gxp.Process_ID = ?";
            } else if (lower.contains("project")) {
                return "SELECT pxp.projectid as relatedId, p.primaryname as relatedName, rt.primaryname as relationTypeName " +
                       "FROM project_x_process pxp " +
                       "LEFT JOIN project p ON pxp.projectid = p.id " +
                       "LEFT JOIN project_x_process_relationtype rt ON pxp.relationtype = rt.id " +
                       "WHERE pxp.process_id = ?";
            } else if (lower.contains("policy")) {
                return "SELECT pxp.policy_id as relatedId, p.PrimaryName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM policy_x_process pxp " +
                       "LEFT JOIN policy p ON pxp.policy_id = p.ID " +
                       "LEFT JOIN policy_x_process_relationtype rt ON pxp.relation_type = rt.ID " +
                       "WHERE pxp.process_id = ?";
            } else if (lower.contains("interface")) {
                return "SELECT pxi.interface_id as relatedId, i.Name as relatedName, rt.primaryname as relationTypeName " +
                       "FROM process_x_interface pxi " +
                       "LEFT JOIN interface i ON pxi.interface_id = i.id " +
                       "LEFT JOIN process_x_interface_relationtype rt ON pxi.relationtype = rt.id " +
                       "WHERE pxi.process_id = ?";
            } else if (lower.contains("legal")) {
                return "SELECT pxl.Legal_ID as relatedId, l.ShortName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM process_x_legal pxl " +
                       "LEFT JOIN legal l ON pxl.Legal_ID = l.ID " +
                       "LEFT JOIN process_x_legal_relationtype rt ON pxl.RelationType = rt.ID " +
                       "WHERE pxl.Process_ID = ?";
            } else if (lower.contains("dataset")) {
                return "SELECT pxd.datasetid as relatedId, d.PrimaryName as relatedName, rt.primaryname as relationTypeName " +
                       "FROM process_x_dataset pxd " +
                       "LEFT JOIN dataset d ON pxd.datasetid = d.ID " +
                       "LEFT JOIN process_x_dataset_relationtype rt ON pxd.relation_type = rt.id " +
                       "WHERE pxd.processid = ?";
            } else if (lower.contains("attribute")) {
                return "SELECT pxa.attributeid as relatedId, a.PrimaryName as relatedName, rt.primaryname as relationTypeName " +
                       "FROM process_x_attribute pxa " +
                       "LEFT JOIN attribute a ON pxa.attributeid = a.ID " +
                       "LEFT JOIN process_x_attribute_relationtype rt ON pxa.relation_type = rt.id " +
                       "WHERE pxa.processid = ?";
            }
        }
        
        // Glossary impact relationships
        if ("glossary".equals(prefix)) {
            if (lower.contains("product")) {
                return "SELECT pxg.productid as relatedId, p.PrimaryName as relatedName, rt.primaryname as relationTypeName " +
                       "FROM product_x_glossary pxg " +
                       "LEFT JOIN product p ON pxg.productid = p.id " +
                       "LEFT JOIN product_x_glossary_relationtype rt ON pxg.relationtype = rt.id " +
                       "WHERE pxg.glossaryid = ?";
            } else if (lower.contains("client")) {
                return "SELECT cxg.Client_ID as relatedId, c.PrimaryName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM client_x_glossary cxg " +
                       "LEFT JOIN client c ON cxg.Client_ID = c.ID " +
                       "LEFT JOIN client_x_glossary_relationtype rt ON cxg.RelationType = rt.ID " +
                       "WHERE cxg.Glossary_ID = ?";
            } else if (lower.contains("system")) {
                return "SELECT gxs.System_ID as relatedId, s.Name as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM glossary_x_system gxs " +
                       "LEFT JOIN system s ON gxs.System_ID = s.ID " +
                       "LEFT JOIN glossary_x_system_relationtype rt ON gxs.Relation_TypeID = rt.ID " +
                       "WHERE gxs.Glossary_ID = ?";
            }
        }
        
        // System impact relationships
        if ("system".equals(prefix)) {
            if (lower.contains("product")) {
                return "SELECT pxs.Product_ID as relatedId, p.primaryname as relatedName, prt.PrimaryName as relationTypeName " +
                       "FROM product_x_system pxs " +
                       "LEFT JOIN product p ON pxs.Product_ID = p.id " +
                       "LEFT JOIN product_x_system_relationtype prt ON pxs.Product_System_Relation_Type = prt.ID " +
                       "WHERE pxs.System_ID = ?";
            } else if (lower.contains("client")) {
                return "SELECT cxs.Client_ID as relatedId, c.PrimaryName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM client_x_system cxs " +
                       "LEFT JOIN client c ON cxs.Client_ID = c.ID " +
                       "LEFT JOIN client_x_system_relationtype rt ON cxs.RelationType = rt.ID " +
                       "WHERE cxs.System_ID = ?";
            } else if (lower.contains("legal")) {
                return "SELECT sxl.Legal_ID as relatedId, l.ShortName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM system_x_legal sxl " +
                       "LEFT JOIN legal l ON sxl.Legal_ID = l.ID " +
                       "LEFT JOIN system_x_legal_relationtype rt ON sxl.RelationType = rt.ID " +
                       "WHERE sxl.System_ID = ?";
            } else if (lower.contains("process")) {
                return "SELECT sxp.Process_ID as relatedId, p.PrimaryName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM system_x_process sxp " +
                       "LEFT JOIN process p ON sxp.Process_ID = p.ID " +
                       "LEFT JOIN system_x_process_relationtype rt ON sxp.RelationType = rt.ID " +
                       "WHERE sxp.System_ID = ?";
            }
        }
        
        // Fallback: generic query (might not work well for comparison)
        String fallbackTable = relationTable != null ? relationTable : facetName.toLowerCase() + "_x_" + extractRelatedTypeName(relationType).toLowerCase();
        String fallbackFk = fkColumn != null ? fkColumn : getForeignKeyColumn(facetName);
        return "SELECT * FROM " + fallbackTable + " WHERE " + fallbackFk + " = ?";
    }
    
    /**
     * Get related object name from query result
     */
    @SuppressWarnings("unused")
    private String getRelatedObjectNameFromQuery(ResultSet rs, String relationType) {
        try {
            String name = rs.getString("relatedName");
            if (name != null && !name.isEmpty()) return name;
        } catch (SQLException e) { /* column might not exist */ }
        
        // Fallback to extracting from the result set
        return extractRelatedTypeName(relationType);
    }
    
    /**
     * Extract related type name from relationType (e.g., "dataset_X_product" -> "Product")
     */
    private String extractRelatedTypeName(String relationType) {
        if (relationType == null) return "Unknown";
        String lower = relationType.toLowerCase();
        
        // Check for more specific types first (before checking for "process" which is too generic)
        if (lower.contains("product")) return "Product";
        if (lower.contains("client")) return "Client";
        if (lower.contains("legal") || lower.contains("regulation")) return "Legal";
        if (lower.contains("system")) return "System";
        if (lower.contains("glossary")) return "Glossary";
        if (lower.contains("dataset")) return "Data Set";
        if (lower.contains("project")) return "Project";
        if (lower.contains("policy")) return "Policy";
        if (lower.contains("interface")) return "Interface";
        if (lower.contains("attribute")) return "Attribute";
        // Check for "process" last, as it's more generic and might match "process_x_policy", "process_x_project", etc.
        if (lower.contains("process")) return "Process";
        
        return formatRelationType(relationType);
    }
    
    /**
     * Get relationship table name based on facet and relation type
     * Handles patterns like "dataset_X_product", "impact#dataset_X_client", etc.
     * Note: Table naming conventions vary - some are {facet}_x_{related}, others are {related}_x_{facet}
     */
    private String getRelationshipTable(String facetName, String relationType) {
        String prefix = facetName.toLowerCase();
        String lower = relationType.toLowerCase();
        
        // Dataset has special table names
        if ("dataset".equals(prefix)) {
            if (lower.contains("product")) return "product_x_dataset"; // Product comes first
            if (lower.contains("client")) return "client_x_dataset";   // Client comes first
            if (lower.contains("legal")) return "dataset_x_legal";     // Dataset comes first for legal
        }
        
        // Glossary has special table names
        if ("glossary".equals(prefix)) {
            if (lower.contains("product")) return "product_x_glossary"; // Product comes first
            if (lower.contains("client")) return "client_x_glossary";    // Client comes first
            if (lower.contains("system")) return "glossary_x_system";   // Glossary comes first
        }
        
        // System has special table names
        if ("system".equals(prefix)) {
            if (lower.contains("product")) return "product_x_system";   // Product comes first
            if (lower.contains("client")) return "client_x_system";    // Client comes first
            if (lower.contains("legal")) return "system_x_legal";       // System comes first
            if (lower.contains("process")) return "system_x_process";  // System comes first
        }
        
        // Process has special table names
        if ("process".equals(prefix)) {
            if (lower.contains("system")) return "process_x_system";
            if (lower.contains("product")) return "product_x_process";   // Product comes first
            if (lower.contains("client")) return "client_x_process";     // Client comes first
            if (lower.contains("glossary")) return "glossary_x_process";  // Glossary comes first
            if (lower.contains("project")) return "project_x_process";    // Project comes first
            if (lower.contains("policy")) return "policy_x_process";      // Policy comes first
            if (lower.contains("interface")) return "process_x_interface";
            if (lower.contains("legal")) return "process_x_legal";
            if (lower.contains("dataset")) return "process_x_dataset";
            if (lower.contains("attribute")) return "process_x_attribute";
        }
        
        // For other facets, try the standard pattern
        if (lower.contains("product")) return prefix + "_x_product";
        if (lower.contains("client")) return prefix + "_x_client";
        if (lower.contains("legal") || lower.contains("regulation")) return prefix + "_x_legal";
        if (lower.contains("system")) return prefix + "_x_system";
        if (lower.contains("process")) return prefix + "_x_process";
        if (lower.contains("glossary")) return prefix + "_x_glossary";
        if (lower.contains("dataset")) return prefix + "_x_dataset";
        if (lower.contains("project")) return prefix + "_x_project";
        if (lower.contains("policy")) return prefix + "_x_policy";
        if (lower.contains("interface")) return prefix + "_x_interface";
        if (lower.contains("attribute")) return prefix + "_x_attribute";
        
        return null;
    }
    
    /**
     * Get foreign key column name for the facet
     */
    private String getForeignKeyColumn(String facetName) {
        switch (facetName.toLowerCase()) {
            case "dataset": return "Dataset_ID";
            case "glossary": return "Glossary_ID";
            case "system": return "System_ID";
            case "process": return "process_id";  // Process uses lowercase process_id
            default:
                return facetName.substring(0, 1).toUpperCase() + facetName.substring(1) + "_ID";
        }
    }
    
    /**
     * Format relation type for display
     */
    private String formatRelationType(String relationType) {
        if (relationType == null) return "";
        return relationType.replace("_", " ")
                          .replace("x", "X")
                          .replaceAll("([a-z])([A-Z])", "$1 $2");
    }
    
    /**
     * Get related object name from a relationship row
     */
    @SuppressWarnings("unused")
    private String getRelatedObjectName(Connection conn, ResultSet rs, String relationType) {
        String type = relationType.toLowerCase();
        
        // For dataset impacts, the related entity names are in different columns
        try {
            if (type.contains("product")) {
                int relatedId = rs.getInt("Product_ID");
                if (!rs.wasNull() && relatedId > 0) {
                    // Product uses 'primaryname' (lowercase in some tables)
                    String name = getObjectName(conn, "product", relatedId, "primaryname");
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (SQLException e) { /* column might not exist */ }
        
        try {
            if (type.contains("client")) {
                int relatedId = rs.getInt("Client_ID");
                if (!rs.wasNull() && relatedId > 0) {
                    // Client uses 'PrimaryName'
                    String name = getObjectName(conn, "client", relatedId, "PrimaryName");
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (SQLException e) { /* column might not exist */ }
        
        try {
            if (type.contains("legal") || type.contains("regulation")) {
                int relatedId = rs.getInt("Legal_ID");
                if (!rs.wasNull() && relatedId > 0) {
                    // Legal uses 'ShortName'
                    String name = getObjectName(conn, "legal", relatedId, "ShortName");
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (SQLException e) { /* column might not exist */ }
        
        try {
            if (type.contains("system")) {
                int relatedId = rs.getInt("System_ID");
                if (!rs.wasNull() && relatedId > 0) {
                    String name = getObjectName(conn, "system", relatedId, "Name");
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (SQLException e) { /* column might not exist */ }
        
        try {
            if (type.contains("process")) {
                int relatedId = rs.getInt("Process_ID");
                if (!rs.wasNull() && relatedId > 0) {
                    String name = getObjectName(conn, "process", relatedId, "PrimaryName");
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (SQLException e) { /* column might not exist */ }
        
        try {
            if (type.contains("glossary")) {
                int relatedId = rs.getInt("Glossary_ID");
                if (!rs.wasNull() && relatedId > 0) {
                    String name = getObjectName(conn, "glossary", relatedId, "Name");
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (SQLException e) { /* column might not exist */ }
        
        try {
            if (type.contains("dataset")) {
                int relatedId = rs.getInt("Dataset_ID");
                if (!rs.wasNull() && relatedId > 0) {
                    String name = getObjectName(conn, "dataset", relatedId, "PrimaryName");
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (SQLException e) { /* column might not exist */ }
        
        return extractRelatedTypeName(relationType);
    }
    
    /**
     * Check if a row exists in a table by ID
     * For relationships and other area types, checks the appropriate table
     */
    private boolean checkIfRowExists(Connection conn, String tableName, int rowId) throws SQLException {
        // For main tables (summary), check the main table
        String sql = "SELECT 1 FROM `" + tableName + "` WHERE `id` = ? OR `ID` = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, rowId);
            stmt.setInt(2, rowId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return true;
                }
            }
        } catch (SQLException e) {
            // Table might not exist or row doesn't exist in main table
            // For relationships, impact, etc., the row might be in a different table
            // Return false - let the snapshot logic handle it
        }
        return false;
    }
    
    /**
     * Check if a relationship/cloned row exists for a specific area_key
     * This is more accurate than just checking the main table
     */
    private boolean checkIfClonedRowExists(Connection conn, String facetName, String areaKey, int nobjectId) throws SQLException {
        // For data-content in System facet, nobjectId is the glossary_x_system relationship ID
        // Check if the relationship record exists
        if ("data-content".equals(areaKey) && "system".equals(facetName)) {
            String sql = "SELECT 1 FROM glossary_x_system WHERE ID = ? LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, nobjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                return false;
            }
        }
        
        // For summary, check main table
        if ("summary".equals(areaKey)) {
            String tableName = getTableName(facetName);
            if (tableName != null) {
                return checkIfRowExists(conn, tableName, nobjectId);
            }
        }
        
        // For relationships, check the relationship table
        if (areaKey.startsWith("relationships#")) {
            String relTable = areaKey.substring("relationships#".length());
            String sql = "SELECT 1 FROM `" + relTable + "` WHERE `id` = ? OR `ID` = ? LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, nobjectId);
                stmt.setInt(2, nobjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                return false;
            }
        }
        
        // For documents, check the document relationship table
        // areaKey format: "documents#system_x_document", "documents#glossary_x_document", etc.
        if (areaKey.startsWith("documents#")) {
            String docTable = areaKey.substring("documents#".length());
            String sql = "SELECT 1 FROM `" + docTable + "` WHERE `ID` = ? LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, nobjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                return false;
            }
        }
        
        // For attributes (summary#attribute), nobjectId is the attribute ID - check attribute table
        if ("summary#attribute".equals(areaKey)) {
            String sql = "SELECT 1 FROM attribute WHERE ID = ? LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, nobjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                return false;
            }
        }
        
        // For dataset value info (summary#dataset_value_info), nobjectId is the values_datastore ID
        if ("summary#dataset_value_info".equals(areaKey)) {
            String sql = "SELECT 1 FROM values_datastore WHERE id = ? LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, nobjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                return false;
            }
        }
        
        // For impact, stakeholders - check main table
        // (they are stored as part of the cloned main object)
        String tableName = getTableName(facetName);
        if (tableName != null) {
            return checkIfRowExists(conn, tableName, nobjectId);
        }
        
        return false;
    }
    
    /**
     * Compare stakeholders between original and cloned object
     * Handles different stakeholder table structures for each facet
     * Shows changes as Updated with old/new values side by side
     */
    private List<Map<String, String>> compareStakeholders(Connection conn, String facetName, 
            int originalId, int clonedId) throws SQLException {
        List<Map<String, String>> changes = new ArrayList<>();
        
        //system.out.println("[PendingChangesServlet] compareStakeholders: facet=" + facetName + 
          //  ", originalId=" + originalId + ", clonedId=" + clonedId);
        
        // Get stakeholders for original object
        List<Map<String, String>> originalStakeholders = getStakeholders(conn, facetName, originalId);
        //system.out.println("[PendingChangesServlet] Original stakeholders: " + originalStakeholders.size());
        
        // Get stakeholders for cloned object
        List<Map<String, String>> clonedStakeholders = getStakeholders(conn, facetName, clonedId);
        //system.out.println("[PendingChangesServlet] Cloned stakeholders: " + clonedStakeholders.size());
        
        // Build lists of stakeholder display strings
        List<String> originalList = new ArrayList<>();
        for (Map<String, String> sh : originalStakeholders) {
            originalList.add(sh.get("personName") + " (" + sh.get("roleName") + ")");
        }
        
        List<String> clonedList = new ArrayList<>();
        for (Map<String, String> sh : clonedStakeholders) {
            clonedList.add(sh.get("personName") + " (" + sh.get("roleName") + ")");
        }
        
        // Find removed stakeholders (in original but not in cloned)
        List<String> removedStakeholders = new ArrayList<>();
        for (String s : originalList) {
            if (!clonedList.contains(s)) {
                removedStakeholders.add(s);
            }
        }
        
        // Find added stakeholders (in cloned but not in original)
        List<String> addedStakeholders = new ArrayList<>();
        for (String s : clonedList) {
            if (!originalList.contains(s)) {
                addedStakeholders.add(s);
            }
        }
        
        // If there are both additions and removals, pair them as "Updated"
        int pairCount = Math.min(removedStakeholders.size(), addedStakeholders.size());
        for (int i = 0; i < pairCount; i++) {
            Map<String, String> change = new HashMap<>();
            change.put("operation", "Updated");
            change.put("fieldName", "Stakeholder");
            change.put("oldValue", removedStakeholders.get(i));
            change.put("newValue", addedStakeholders.get(i));
            changes.add(change);
        }
        
        // Handle remaining removals (pure deletions)
        for (int i = pairCount; i < removedStakeholders.size(); i++) {
            Map<String, String> change = new HashMap<>();
            change.put("operation", "Deleted");
            change.put("fieldName", "Stakeholder");
            change.put("oldValue", removedStakeholders.get(i));
            change.put("newValue", "");
            changes.add(change);
        }
        
        // Handle remaining additions (pure insertions)
        for (int i = pairCount; i < addedStakeholders.size(); i++) {
            Map<String, String> change = new HashMap<>();
            change.put("operation", "Inserted");
            change.put("fieldName", "Stakeholder");
            change.put("oldValue", "");
            change.put("newValue", addedStakeholders.get(i));
            changes.add(change);
        }
        
        //system.out.println("[PendingChangesServlet] Total stakeholder changes: " + changes.size());
        return changes;
    }
    
    /**
     * Get stakeholders for a specific object
     */
    private List<Map<String, String>> getStakeholders(Connection conn, String facetName, int objectId) {
        List<Map<String, String>> stakeholders = new ArrayList<>();
        
        String sql;
        
        // Dataset uses a different structure: dataset_x_objectxpeople -> object_x_people -> people
        if ("dataset".equalsIgnoreCase(facetName)) {
            sql = "SELECT oxp.ipid as personId, CONCAT(p.First_Name, ' ', p.Last_Name) as personName, r.PrimaryName as roleName " +
                  "FROM dataset_x_objectxpeople dx " +
                  "JOIN object_x_people oxp ON dx.Object_x_ipid = oxp.ID " +
                  "JOIN object_role r ON oxp.roleID = r.ID " +
                  "JOIN people p ON oxp.ipid = p.ID " +
                  "WHERE dx.Dataset_ID = ?";
        } else if ("glossary".equalsIgnoreCase(facetName)) {
            sql = "SELECT oxp.ipid as personId, CONCAT(p.First_Name, ' ', p.Last_Name) as personName, r.PrimaryName as roleName " +
                  "FROM glossary_x_objectxpeople gx " +
                  "JOIN object_x_people oxp ON gx.Object_x_ipid = oxp.ID " +
                  "JOIN object_role r ON oxp.roleID = r.ID " +
                  "JOIN people p ON oxp.ipid = p.ID " +
                  "WHERE gx.GlossaryID = ?";
        } else if ("system".equalsIgnoreCase(facetName)) {
            sql = "SELECT oxp.ipid as personId, CONCAT(p.First_Name, ' ', p.Last_Name) as personName, r.PrimaryName as roleName " +
                  "FROM system_x_objectxpeople sx " +
                  "JOIN object_x_people oxp ON sx.Object_x_ipid = oxp.ID " +
                  "JOIN object_role r ON oxp.roleID = r.ID " +
                  "JOIN people p ON oxp.ipid = p.ID " +
                  "WHERE sx.SystemID = ?";
        } else if ("process".equalsIgnoreCase(facetName)) {
            sql = "SELECT oxp.ipid as personId, CONCAT(p.First_Name, ' ', p.Last_Name) as personName, r.PrimaryName as roleName " +
                  "FROM process_x_objectxpeople px " +
                  "JOIN object_x_people oxp ON px.object_x_ip = oxp.id " +
                  "JOIN object_role r ON oxp.roleID = r.ID " +
                  "JOIN people p ON oxp.ipid = p.ID " +
                  "WHERE px.process_id = ?";
        } else {
            // Generic fallback
            String stakeholderTable = facetName.toLowerCase() + "_x_objectxpeople";
            String fkColumn = getForeignKeyColumn(facetName);
            sql = "SELECT oxp.ipid as personId, CONCAT(p.First_Name, ' ', p.Last_Name) as personName, r.PrimaryName as roleName " +
                  "FROM " + stakeholderTable + " fx " +
                  "JOIN object_x_people oxp ON fx.Object_x_ipid = oxp.ID " +
                  "JOIN object_role r ON oxp.RoleID = r.ID " +
                  "JOIN people p ON oxp.ipid = p.ID " +
                  "WHERE fx." + fkColumn + " = ?";
        }
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> sh = new HashMap<>();
                    sh.put("personId", String.valueOf(rs.getInt("personId")));
                    sh.put("personName", rs.getString("personName"));
                    sh.put("roleName", rs.getString("roleName"));
                    stakeholders.add(sh);
                }
            }
        } catch (SQLException e) {
            System.err.println("[PendingChangesServlet] Error getting stakeholders: " + e.getMessage());
        }
        
        return stakeholders;
    }

    private void sendError(HttpServletResponse resp, String message, int status) throws IOException {
        resp.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        resp.getWriter().write(gson.toJson(error));
    }
    
    /**
     * Compare relationship changes for a given relationship table.
     * Formats display as "Object1 X Object2" for relationship-type area keys.
     * 
     * NOTE: For relationship changes, the mapping stores:
     * - object_id = parent object ID (e.g., glossary ID, process ID)
     * - nobject_id = the ID of the relationship record that was added/modified
     * 
     * So we need to fetch the specific relationship record by its ID (nobjectId)
     * rather than comparing all relationships of a parent object.
     */
    private List<Map<String, String>> compareRelationshipChanges(Connection conn, String facetName, int objectId, int nobjectId, String relTable) throws SQLException {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Configuration for each relationship table
        String sqlById = null;  // SQL to get relationship by its ID
        String displayFormat = null;
        String tabName = null;
        
        switch (relTable) {
            case "process_x_process":
                // Process predecessors - nobjectId is the ID of the process_x_process record
                sqlById = "SELECT pxp.ID, pxp.sourceprocess_id, pxp.targetprocess_id, pxp.relationtype, pxp.annotations, " +
                          "sp.primaryname as source_name, tp.primaryname as target_name, " +
                          "rt.PrimaryName as relation_type_name " +
                          "FROM process_x_process pxp " +
                          "LEFT JOIN process sp ON pxp.sourceprocess_id = sp.id " +
                          "LEFT JOIN process tp ON pxp.targetprocess_id = tp.id " +
                          "LEFT JOIN process_x_process_relationtype rt ON pxp.relationtype = rt.id " +
                          "WHERE pxp.ID = ?";
                displayFormat = "{source_name} X {target_name}";
                tabName = "Predecessors";
                break;
                
            case "glossary_x_glossary":
                sqlById = "SELECT gxg.ID, gxg.SourceGlossaryID, gxg.TargetGlossaryID, gxg.RelationType, " +
                          "sg.Name as source_name, tg.Name as target_name, " +
                          "rt.PrimaryName as relation_type_name " +
                          "FROM glossary_x_glossary gxg " +
                          "LEFT JOIN glossary sg ON gxg.SourceGlossaryID = sg.ID " +
                          "LEFT JOIN glossary tg ON gxg.TargetGlossaryID = tg.ID " +
                          "LEFT JOIN glossary_x_glossary_reltype rt ON gxg.RelationType = rt.ID " +
                          "WHERE gxg.ID = ?";
                displayFormat = "{source_name} X {target_name}";
                tabName = "Relationships";
                break;
                
            case "glossary_x_system":
                sqlById = "SELECT gxs.ID, gxs.GlossaryID, gxs.SystemID, gxs.Strategic_DatasetID, " +
                          "g.Name as glossary_name, s.Name as system_name, d.PrimaryName as dataset_name, " +
                          "rt.PrimaryName as relation_type_name " +
                          "FROM glossary_x_system gxs " +
                          "LEFT JOIN glossary g ON gxs.GlossaryID = g.ID " +
                          "LEFT JOIN system s ON gxs.SystemID = s.id " +
                          "LEFT JOIN dataset d ON gxs.Strategic_DatasetID = d.ID " +
                          "LEFT JOIN glossary_x_system_relationtype rt ON gxs.Relation_TypeID = rt.ID " +
                          "WHERE gxs.ID = ?";
                displayFormat = "{glossary_name} X {system_name}";
                // Tab name depends on context - will be set by caller based on area_key
                tabName = "Strategic Source"; // Default, but can be "Data Content" for System facet
                break;
                
            case "attribute_x_attribute":
                sqlById = "SELECT axa.ID, axa.Source_AttributeID, axa.Target_AttributeID, axa.Relation_Type, axa.Relation_Scope, " +
                          "axa.Relation_Method, axa.Sourcing_Logic, axa.Review_Status, " +
                          "sa.PrimaryName as source_name, sa.RefNumber as source_ref, " +
                          "ta.PrimaryName as target_name, ta.RefNumber as target_ref, " +
                          "rt.PrimaryName as relation_type_name, " +
                          "rs.PrimaryName as relation_scope_name, " +
                          "td.ID as target_dataset_id, td.PrimaryName as target_dataset_name, td.RefNumber as target_dataset_ref, " +
                          "s.id as system_id, s.Name as system_name, " +
                          "i.id as interface_id, i.Name as interface_name, i.Ref_number as interface_ref " +
                          "FROM attribute_x_attribute axa " +
                          "LEFT JOIN attribute sa ON axa.Source_AttributeID = sa.ID " +
                          "LEFT JOIN attribute ta ON axa.Target_AttributeID = ta.ID " +
                          "LEFT JOIN dataset td ON td.ID = ta.Dataset_ID " +
                          "LEFT JOIN system s ON s.id = td.MasterSource " +
                          "LEFT JOIN interface i ON i.id = axa.Relation_Method " +
                          "LEFT JOIN attribute_x_attribute_relationtype rt ON axa.Relation_Type = rt.ID " +
                          "LEFT JOIN attribute_x_attribute_relationscope rs ON axa.Relation_Scope = rs.ID " +
                          "WHERE axa.ID = ?";
                displayFormat = "{source_name} X {target_name}";
                tabName = "Relationships"; // Changed from "Attribute Relationships" to "Relationships" for dataset
                break;
                
            case "dataset_mastersource":
                // Datasets linked to system via MasterSource - nobjectId is the dataset ID
                sqlById = "SELECT d.ID as dataset_id, d.PrimaryName as dataset_name, " +
                          "s.Name as system_name, d.MasterSource " +
                          "FROM dataset d " +
                          "LEFT JOIN system s ON d.MasterSource = s.id " +
                          "WHERE d.ID = ?";
                displayFormat = "{system_name} X {dataset_name}";
                tabName = "Data Content";
                break;
                
            default:
                logger.warn("Unknown relationship table: {}", relTable);
                return changes;
        }
        
        // Get the specific relationship record that was added/modified
        // nobjectId is the ID of the relationship record itself
        // First, check if the relationship still exists (if not, it was deleted)
        boolean relationshipExists = false;
        try (PreparedStatement checkStmt = conn.prepareStatement(sqlById)) {
            checkStmt.setInt(1, nobjectId);
            try (ResultSet checkRs = checkStmt.executeQuery()) {
                relationshipExists = checkRs.next();
            }
        }
        
        if (!relationshipExists) {
            // Relationship was deleted - try to get its info from a backup or history
            // For now, we'll query the relationship table with a different approach
            // or use a fallback query that might still have the data
            // Since the relationship is deleted, we need to reconstruct its info
            // For attribute_x_attribute, we can try to get info from related attributes
            if ("attribute_x_attribute".equals(relTable)) {
                // Try to get relationship info from attribute history or use a generic approach
                // For deleted relationships, we'll create a change entry with "Deleted" operation
                Map<String, String> deletedChange = new HashMap<>();
                deletedChange.put("operation", "Deleted");
                deletedChange.put("displayName", "Relationship ID " + nobjectId + " (deleted)");
                deletedChange.put("tabName", tabName);
                deletedChange.put("fieldName", "Relationship");
                deletedChange.put("oldValue", "Relationship ID " + nobjectId);
                deletedChange.put("newValue", "");
                changes.add(deletedChange);
                return changes;
            } else {
                // For other relationship types, create a generic deleted entry
                Map<String, String> change = new HashMap<>();
                change.put("operation", "Deleted");
                change.put("displayName", "Relationship ID " + nobjectId + " (deleted)");
                change.put("tabName", tabName);
                change.put("fieldName", "Relationship");
                change.put("oldValue", "Relationship ID " + nobjectId);
                change.put("newValue", "");
                changes.add(change);
                return changes;
            }
        }
        
        // Relationship exists - it's either inserted or updated
        try (PreparedStatement stmt = conn.prepareStatement(sqlById)) {
            stmt.setInt(1, nobjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                    }
                    
                    // Check if this is a new relationship (inserted) or an existing one (updated)
                    // For relationships, if nobjectId exists in the original object's relationships, it's updated
                    // Otherwise, it's inserted
                    boolean isNewRelationship = true;
                    if ("attribute_x_attribute".equals(relTable)) {
                        // Check if relationship exists in original dataset
                        String checkSql = "SELECT COUNT(*) as count FROM attribute_x_attribute axa " +
                                        "INNER JOIN attribute sa ON axa.Source_AttributeID = sa.ID " +
                                        "WHERE axa.ID = ? AND sa.Dataset_ID = ?";
                        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                            checkStmt.setInt(1, nobjectId);
                            checkStmt.setInt(2, objectId);
                            try (ResultSet checkRs = checkStmt.executeQuery()) {
                                if (checkRs.next()) {
                                    // If count > 0, relationship exists in original, so it might be updated
                                    // But since we're tracking new inserts, if it's in dataset_changes, it's likely new
                                    // We'll assume it's inserted if it's in the changes table
                                    isNewRelationship = true; // Relationships in changes table are typically new
                                }
                            }
                        } catch (SQLException e) {
                            // If check fails, assume it's new
                            isNewRelationship = true;
                        }
                    }
                    
                    String operation = isNewRelationship ? "Inserted" : "Updated";
                    
                    // This is a new/modified relationship
                    // For attribute_x_attribute, create separate change entries for each field to show in details modal
                    if ("attribute_x_attribute".equals(relTable)) {
                        // Build display name for the relationship (e.g., "att1 X STG_HASH")
                        String sourceAttr = getAttributeDisplayName(row, "source_name", "source_ref");
                        String targetAttr = getAttributeDisplayName(row, "target_name", "target_ref");
                        String relationshipDisplayName = (sourceAttr != null ? sourceAttr : "") + " X " + (targetAttr != null ? targetAttr : "");
                        
                        // Add relatedType and relatedName for attribute relationships
                        String relatedType = "Attribute";
                        String relatedName = targetAttr;
                        
                        // Add individual field changes for detailed display in modal
                        // These will be grouped by the frontend to show in the details modal
                        Map<String, String> attrChange = createFieldChange("Attribute", "", sourceAttr);
                        attrChange.put("displayName", relationshipDisplayName);
                        attrChange.put("operation", operation);
                        attrChange.put("relatedType", relatedType);
                        attrChange.put("relatedName", relatedName);
                        changes.add(attrChange);
                        
                        Map<String, String> relatedAttrChange = createFieldChange("Related Attributes", "", targetAttr);
                        relatedAttrChange.put("displayName", relationshipDisplayName);
                        relatedAttrChange.put("operation", operation);
                        relatedAttrChange.put("relatedType", relatedType);
                        relatedAttrChange.put("relatedName", relatedName);
                        changes.add(relatedAttrChange);
                        
                        Map<String, String> typeChange = createFieldChange("Type", "", getStringValue(row, "relation_type_name"));
                        typeChange.put("displayName", relationshipDisplayName);
                        typeChange.put("operation", operation);
                        typeChange.put("relatedType", relatedType);
                        typeChange.put("relatedName", relatedName);
                        String relationTypeName = getStringValue(row, "relation_type_name");
                        if (relationTypeName != null && !relationTypeName.isEmpty()) {
                            typeChange.put("relationTypeName", relationTypeName);
                        }
                        changes.add(typeChange);
                        
                        Map<String, String> scopeChange = createFieldChange("Scope of Data", "", getStringValue(row, "relation_scope_name"));
                        scopeChange.put("displayName", relationshipDisplayName);
                        scopeChange.put("operation", operation);
                        scopeChange.put("relatedType", relatedType);
                        scopeChange.put("relatedName", relatedName);
                        changes.add(scopeChange);
                        
                        Map<String, String> systemChange = createFieldChange("Source System", "", getStringValue(row, "system_name"));
                        systemChange.put("displayName", relationshipDisplayName);
                        systemChange.put("operation", operation);
                        systemChange.put("relatedType", relatedType);
                        systemChange.put("relatedName", relatedName);
                        changes.add(systemChange);
                        
                        String datasetDisplay = getDatasetDisplayName(row, "target_dataset_ref", "target_dataset_name");
                        Map<String, String> datasetChange = createFieldChange("Related Data set", "", datasetDisplay);
                        datasetChange.put("displayName", relationshipDisplayName);
                        datasetChange.put("operation", operation);
                        datasetChange.put("relatedType", relatedType);
                        datasetChange.put("relatedName", relatedName);
                        changes.add(datasetChange);
                    } else {
                        // For other relationship types, use simple format
                        Map<String, String> change = new HashMap<>();
                        change.put("operation", operation);
                        change.put("displayName", formatRelationshipDisplay(displayFormat, row));
                        change.put("tabName", tabName);
                        change.put("fieldName", "Relationship");
                        change.put("oldValue", "");
                        change.put("newValue", formatRelationshipDisplay(displayFormat, row));
                        
                        // Add relation type name if available
                        String relationTypeName = getStringValue(row, "relation_type_name");
                        if (relationTypeName != null && !relationTypeName.isEmpty()) {
                            change.put("relationTypeName", relationTypeName);
                        }
                        
                        // Add related type and name based on relationship table
                        String relatedType = getRelatedTypeForRelationshipTable(relTable);
                        String relatedName = getRelatedNameForRelationshipTable(relTable, row);
                        if (relatedType != null) {
                            change.put("relatedType", relatedType);
                        }
                        if (relatedName != null) {
                            change.put("relatedName", relatedName);
                        }
                        
                        changes.add(change);
                    }
                }
            }
        }
        
        return changes;
    }
    
    /**
     * Legacy method for comparing all relationships of a parent object (not used for new pending changes)
     */
    @SuppressWarnings("unused")
    private List<Map<String, String>> compareAllRelationshipChanges(Connection conn, String facetName, int objectId, int nobjectId, String relTable) throws SQLException {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Configuration for each relationship table
        String sql = null;
        String displayFormat = null;
        String tabName = null;
        
        switch (relTable) {
            case "process_x_process":
                // Process predecessors
                sql = "SELECT pxp.sourceprocess_id, pxp.targetprocess_id, pxp.relationtype, pxp.annotations, " +
                      "sp.primaryname as source_name, tp.primaryname as target_name, " +
                      "rt.PrimaryName as relation_type_name " +
                      "FROM process_x_process pxp " +
                      "LEFT JOIN process sp ON pxp.sourceprocess_id = sp.id " +
                      "LEFT JOIN process tp ON pxp.targetprocess_id = tp.id " +
                      "LEFT JOIN process_x_process_relationtype rt ON pxp.relationtype = rt.id " +
                      "WHERE pxp.sourceprocess_id = ?";
                displayFormat = "{source_name} X {target_name}";
                tabName = "Predecessors";
                break;
                
            case "glossary_x_glossary":
                sql = "SELECT gxg.SourceGlossaryID, gxg.TargetGlossaryID, gxg.RelationType, " +
                      "sg.Name as source_name, tg.Name as target_name, " +
                      "rt.PrimaryName as relation_type_name " +
                      "FROM glossary_x_glossary gxg " +
                      "LEFT JOIN glossary sg ON gxg.SourceGlossaryID = sg.ID " +
                      "LEFT JOIN glossary tg ON gxg.TargetGlossaryID = tg.ID " +
                      "LEFT JOIN glossary_x_glossary_reltype rt ON gxg.RelationType = rt.ID " +
                      "WHERE gxg.SourceGlossaryID = ?";
                displayFormat = "{source_name} X {target_name}";
                tabName = "Relationships";
                break;
                
            case "glossary_x_system":
                sql = "SELECT gxs.GlossaryID, gxs.SystemID, gxs.Strategic_DatasetID, " +
                      "g.Name as glossary_name, s.Name as system_name, d.PrimaryName as dataset_name " +
                      "FROM glossary_x_system gxs " +
                      "LEFT JOIN glossary g ON gxs.GlossaryID = g.ID " +
                      "LEFT JOIN system s ON gxs.SystemID = s.id " +
                      "LEFT JOIN dataset d ON gxs.Strategic_DatasetID = d.ID " +
                      "WHERE gxs.GlossaryID = ?";
                displayFormat = "{glossary_name} X {system_name}";
                tabName = "Strategic Source";
                break;
                
            case "attribute_x_attribute":
                sql = "SELECT axa.Source_AttributeID, axa.Target_AttributeID, axa.Relation_Type, axa.Relation_Scope, " +
                      "sa.PrimaryName as source_name, ta.PrimaryName as target_name, " +
                      "rt.PrimaryName as relation_type_name " +
                      "FROM attribute_x_attribute axa " +
                      "LEFT JOIN attribute sa ON axa.Source_AttributeID = sa.ID " +
                      "LEFT JOIN attribute ta ON axa.Target_AttributeID = ta.ID " +
                      "LEFT JOIN attribute_x_attribute_relationtype rt ON axa.Relation_Type = rt.ID " +
                      "WHERE sa.Dataset_ID = ? OR ta.Dataset_ID = ?";
                displayFormat = "{source_name} X {target_name}";
                tabName = "Attribute Relationships";
                break;
                
            case "dataset_mastersource":
                // Datasets linked to system via MasterSource
                sql = "SELECT d.ID as dataset_id, d.PrimaryName as dataset_name, " +
                      "s.Name as system_name, d.MasterSource " +
                      "FROM dataset d " +
                      "LEFT JOIN system s ON d.MasterSource = s.id " +
                      "WHERE d.MasterSource = ?";
                displayFormat = "{system_name} X {dataset_name}";
                tabName = "Data Content";
                break;
                
            default:
                logger.warn("Unknown relationship table: {}", relTable);
                return changes;
        }
        
        // For attribute_x_attribute, use two parameters
        boolean useTwoParams = "attribute_x_attribute".equals(relTable);
        
        // Get original relationships
        List<Map<String, Object>> originalRels = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            if (useTwoParams) {
                stmt.setInt(2, objectId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                    }
                    originalRels.add(row);
                }
            }
        }
        
        // Get cloned/new relationships
        List<Map<String, Object>> clonedRels = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, nobjectId);
            if (useTwoParams) {
                stmt.setInt(2, nobjectId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                    }
                    clonedRels.add(row);
                }
            }
        }
        
        // Compare - find added relationships in cloned
        for (Map<String, Object> clonedRel : clonedRels) {
            boolean found = false;
            for (Map<String, Object> origRel : originalRels) {
                if (relationshipMatches(relTable, origRel, clonedRel)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                // This is a new relationship
                Map<String, String> change = new HashMap<>();
                change.put("operation", "Added");
                change.put("displayName", formatRelationshipDisplay(displayFormat, clonedRel));
                change.put("tabName", tabName);
                change.put("fieldName", "Relationship");
                change.put("oldValue", "");
                change.put("newValue", formatRelationshipDisplay(displayFormat, clonedRel));
                changes.add(change);
            }
        }
        
        // Find removed relationships
        for (Map<String, Object> origRel : originalRels) {
            boolean found = false;
            for (Map<String, Object> clonedRel : clonedRels) {
                if (relationshipMatches(relTable, origRel, clonedRel)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                // This relationship was removed
                Map<String, String> change = new HashMap<>();
                change.put("operation", "Removed");
                change.put("displayName", formatRelationshipDisplay(displayFormat, origRel));
                change.put("tabName", tabName);
                change.put("fieldName", "Relationship");
                change.put("oldValue", formatRelationshipDisplay(displayFormat, origRel));
                change.put("newValue", "");
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    /**
     * Check if two relationship records match (same source and target)
     */
    private boolean relationshipMatches(String relTable, Map<String, Object> rel1, Map<String, Object> rel2) {
        switch (relTable) {
            case "process_x_process":
                return Objects.equals(rel1.get("targetprocess_id"), rel2.get("targetprocess_id"));
            case "glossary_x_glossary":
                return Objects.equals(rel1.get("targetglossaryid"), rel2.get("targetglossaryid"));
            case "glossary_x_system":
                return Objects.equals(rel1.get("systemid"), rel2.get("systemid")) &&
                       Objects.equals(rel1.get("strategic_datasetid"), rel2.get("strategic_datasetid"));
            case "attribute_x_attribute":
                return Objects.equals(rel1.get("source_attributeid"), rel2.get("source_attributeid")) &&
                       Objects.equals(rel1.get("target_attributeid"), rel2.get("target_attributeid"));
            case "dataset_mastersource":
                return Objects.equals(rel1.get("dataset_id"), rel2.get("dataset_id"));
            default:
                return false;
        }
    }
    
    /**
     * Format relationship display string (e.g., "Object1 X Object2")
     */
    private String formatRelationshipDisplay(String format, Map<String, Object> rel) {
        String result = format;
        for (Map.Entry<String, Object> entry : rel.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
    
    /**
     * Helper method to create a field change entry
     */
    private Map<String, String> createFieldChange(String fieldName, String oldValue, String newValue) {
        Map<String, String> change = new HashMap<>();
        change.put("fieldName", fieldName);
        change.put("oldValue", oldValue != null ? oldValue : "");
        change.put("newValue", newValue != null ? newValue : "");
        return change;
    }
    
    /**
     * Get related type name for a relationship table (e.g., "Glossary", "System", "Process")
     */
    private String getRelatedTypeForRelationshipTable(String relTable) {
        switch (relTable) {
            case "glossary_x_glossary":
                return "Glossary";
            case "glossary_x_system":
                return "System";
            case "process_x_process":
                return "Process";
            case "attribute_x_attribute":
                return "Attribute";
            case "dataset_mastersource":
                return "System";
            default:
                return null;
        }
    }
    
    /**
     * Get related name for a relationship table from row data
     */
    private String getRelatedNameForRelationshipTable(String relTable, Map<String, Object> row) {
        switch (relTable) {
            case "glossary_x_glossary":
                // Return target glossary name
                return getStringValue(row, "target_name");
            case "glossary_x_system":
                // Return system name
                return getStringValue(row, "system_name");
            case "process_x_process":
                // Return target process name
                return getStringValue(row, "target_name");
            case "attribute_x_attribute":
                // Return target attribute name
                return getAttributeDisplayName(row, "target_name", "target_ref");
            case "dataset_mastersource":
                // Return system name
                return getStringValue(row, "system_name");
            default:
                return null;
        }
    }
    
    /**
     * Helper method to get attribute display name (with ref number if available)
     */
    private String getAttributeDisplayName(Map<String, Object> row, String nameKey, String refKey) {
        String name = getStringValue(row, nameKey);
        String ref = getStringValue(row, refKey);
        if (ref != null && !ref.isEmpty()) {
            return ref + ": " + name;
        }
        return name != null ? name : "";
    }
    
    /**
     * Helper method to get dataset display name (with ref number if available)
     */
    private String getDatasetDisplayName(Map<String, Object> row, String refKey, String nameKey) {
        String ref = getStringValue(row, refKey);
        String name = getStringValue(row, nameKey);
        if (ref != null && !ref.isEmpty() && name != null && !name.isEmpty()) {
            return ref + ": " + name;
        }
        return name != null ? name : "";
    }
    
    /**
     * Helper method to get string value from map (case-insensitive)
     */
    private String getStringValue(Map<String, Object> row, String key) {
        // Try lowercase key first (as stored in row)
        Object value = row.get(key.toLowerCase());
        if (value == null) {
            // Try original key
            value = row.get(key);
        }
        return value != null ? value.toString() : "";
    }
    
    /**
     * Compare attribute changes for dataset
     * If attribute doesn't exist in original dataset, it's "Inserted", otherwise "Updated"
     * 
     * IMPORTANT: attrId is the attribute ID from the cloned dataset (nobjectId from dataset_changes)
     * datasetId is the original dataset ID (objectId from dataset_changes)
     * 
     * We read the attribute directly using attrId (which is in cloned dataset).
     * To determine if it's new or updated, we check if the same attribute ID exists in original dataset.
     */
    private List<Map<String, String>> compareAttributeChanges(Connection conn, int datasetId, int attrId) throws SQLException {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Check if attribute exists in original dataset
        // attrId is from cloned dataset, so if it also exists in original dataset (same ID), it's an update
        // Otherwise, it's a new attribute (inserted)
        String checkOriginalSql = "SELECT COUNT(*) as count FROM attribute WHERE ID = ? AND Dataset_ID = ?";
        boolean isNewAttribute = true;
        try (PreparedStatement checkStmt = conn.prepareStatement(checkOriginalSql)) {
            checkStmt.setInt(1, attrId);
            checkStmt.setInt(2, datasetId);
            try (ResultSet checkRs = checkStmt.executeQuery()) {
                if (checkRs.next()) {
                    // If count > 0, attribute exists in original dataset with same ID, so it's an update
                    // If count = 0, attribute doesn't exist in original dataset, so it's new (inserted)
                    isNewAttribute = (checkRs.getInt("count") == 0);
                }
            }
        }
        
        // Get all attribute fields for display in change review
        // Similar to how documents are handled - each field is a separate record
        String sql = "SELECT a.ID, a.PrimaryName, a.Definition, a.Is_PrimaryKey, a.RefNumber, " +
                     "dt.PrimaryName as data_type, a.DataLength, a.Is_Mandatory, " +
                     "ao.PrimaryName as origin, g.Name as glossary_name, " +
                     "(SELECT aan.Name FROM attribute_alias_name aan " +
                     " WHERE aan.AttributeID = a.ID AND aan.Name_Type = 1 LIMIT 1) as db_field_name, " +
                     "ae.PrimaryName as editability, aer.PrimaryName as editability_role, " +
                     "r.PrimaryName as requirement, a.Business_Logic as business_logic " +
                     "FROM attribute a " +
                     "LEFT JOIN attribute_datatype dt ON a.Data_type_ID = dt.ID " +
                     "LEFT JOIN attribute_origination ao ON a.Origination = ao.ID " +
                     "LEFT JOIN glossary g ON a.Glossary_ID = g.ID " +
                     "LEFT JOIN attribute_editability ae ON a.Editability = ae.ID " +
                     "LEFT JOIN attribute_edit_role aer ON a.Editability_role = aer.ID " +
                     "LEFT JOIN requirement r ON a.Requirement_ID = r.ID " +
                     "WHERE a.ID = ?";
        
        String operation = isNewAttribute ? "Inserted" : "Updated";
        String attributeName = null;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, attrId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    logger.warn("Attribute {} not found in database - cannot generate changes", attrId);
                    return changes; // Return empty list if attribute not found
                }
                attributeName = rs.getString("PrimaryName");
                logger.debug("Found attribute: ID={}, Name={}, isNew={}", attrId, attributeName, isNewAttribute);
                
                // Key (Is_PrimaryKey)
                Map<String, String> keyChange = new HashMap<>();
                keyChange.put("attributeName", attributeName);
                keyChange.put("operation", operation);
                keyChange.put("fieldName", "Key");
                keyChange.put("oldValue", "");
                keyChange.put("newValue", rs.getBoolean("Is_PrimaryKey") ? "Yes" : "No");
                changes.add(keyChange);
                
                // Name (PrimaryName)
                Map<String, String> nameChange = new HashMap<>();
                nameChange.put("attributeName", attributeName);
                nameChange.put("operation", operation);
                nameChange.put("fieldName", "Name");
                nameChange.put("oldValue", "");
                nameChange.put("newValue", attributeName != null ? attributeName : "");
                changes.add(nameChange);
                
                // Definition
                String definition = rs.getString("Definition");
                if (definition != null && !definition.trim().isEmpty()) {
                    Map<String, String> defChange = new HashMap<>();
                    defChange.put("attributeName", attributeName);
                    defChange.put("operation", operation);
                    defChange.put("fieldName", "Definition");
                    defChange.put("oldValue", "");
                    defChange.put("newValue", definition);
                    changes.add(defChange);
                }
                
                // Origin
                String origin = rs.getString("origin");
                if (origin != null && !origin.trim().isEmpty()) {
                    Map<String, String> originChange = new HashMap<>();
                    originChange.put("attributeName", attributeName);
                    originChange.put("operation", operation);
                    originChange.put("fieldName", "Origin");
                    originChange.put("oldValue", "");
                    originChange.put("newValue", origin);
                    changes.add(originChange);
                }
                
                // Glossary Name
                String glossaryName = rs.getString("glossary_name");
                if (glossaryName != null && !glossaryName.trim().isEmpty()) {
                    Map<String, String> glossaryChange = new HashMap<>();
                    glossaryChange.put("attributeName", attributeName);
                    glossaryChange.put("operation", operation);
                    glossaryChange.put("fieldName", "Glossary Name");
                    glossaryChange.put("oldValue", "");
                    glossaryChange.put("newValue", glossaryName);
                    changes.add(glossaryChange);
                }
                
                // DB Field Name
                String dbFieldName = rs.getString("db_field_name");
                if (dbFieldName != null && !dbFieldName.trim().isEmpty()) {
                    Map<String, String> dbFieldChange = new HashMap<>();
                    dbFieldChange.put("attributeName", attributeName);
                    dbFieldChange.put("operation", operation);
                    dbFieldChange.put("fieldName", "DB Field Name");
                    dbFieldChange.put("oldValue", "");
                    dbFieldChange.put("newValue", dbFieldName);
                    changes.add(dbFieldChange);
                }
                
                // Data Type
                String dataType = rs.getString("data_type");
                if (dataType != null && !dataType.trim().isEmpty()) {
                    Map<String, String> dataTypeChange = new HashMap<>();
                    dataTypeChange.put("attributeName", attributeName);
                    dataTypeChange.put("operation", operation);
                    dataTypeChange.put("fieldName", "Data Type");
                    dataTypeChange.put("oldValue", "");
                    String dataLength = rs.getString("DataLength");
                    String dataTypeValue = dataType;
                    if (dataLength != null && !dataLength.trim().isEmpty()) {
                        dataTypeValue += " (" + dataLength + ")";
                    }
                    dataTypeChange.put("newValue", dataTypeValue);
                    changes.add(dataTypeChange);
                }
                
                // Editability
                String editability = rs.getString("editability");
                if (editability != null && !editability.trim().isEmpty()) {
                    Map<String, String> editabilityChange = new HashMap<>();
                    editabilityChange.put("attributeName", attributeName);
                    editabilityChange.put("operation", operation);
                    editabilityChange.put("fieldName", "Editability");
                    editabilityChange.put("oldValue", "");
                    editabilityChange.put("newValue", editability);
                    changes.add(editabilityChange);
                }
                
                // Editability Role
                String editabilityRole = rs.getString("editability_role");
                if (editabilityRole != null && !editabilityRole.trim().isEmpty()) {
                    Map<String, String> editabilityRoleChange = new HashMap<>();
                    editabilityRoleChange.put("attributeName", attributeName);
                    editabilityRoleChange.put("operation", operation);
                    editabilityRoleChange.put("fieldName", "Editability Role");
                    editabilityRoleChange.put("oldValue", "");
                    editabilityRoleChange.put("newValue", editabilityRole);
                    changes.add(editabilityRoleChange);
                }
                
                // Requirement
                String requirement = rs.getString("requirement");
                if (requirement != null && !requirement.trim().isEmpty()) {
                    Map<String, String> requirementChange = new HashMap<>();
                    requirementChange.put("attributeName", attributeName);
                    requirementChange.put("operation", operation);
                    requirementChange.put("fieldName", "Requirement");
                    requirementChange.put("oldValue", "");
                    requirementChange.put("newValue", requirement);
                    changes.add(requirementChange);
                }
                
                // Business Logic
                String businessLogic = rs.getString("business_logic");
                if (businessLogic != null && !businessLogic.trim().isEmpty()) {
                    Map<String, String> businessLogicChange = new HashMap<>();
                    businessLogicChange.put("attributeName", attributeName);
                    businessLogicChange.put("operation", operation);
                    businessLogicChange.put("fieldName", "Business Logic");
                    businessLogicChange.put("oldValue", "");
                    businessLogicChange.put("newValue", businessLogic);
                    changes.add(businessLogicChange);
                }
            }
        }
        
        return changes;
    }
    
    /**
     * Compare dataset value info (metadata) changes
     * If values_datastore doesn't exist in original dataset, it's "Inserted", otherwise "Updated"
     */
    private List<Map<String, String>> compareValueInfoChanges(Connection conn, int datasetId, int datastoreId) throws SQLException {
        List<Map<String, String>> changes = new ArrayList<>();
        
        // Check if values_datastore exists in original dataset
        String checkOriginalSql = "SELECT COUNT(*) as count FROM values_datastore WHERE id = ? AND dataset_id = ?";
        boolean isNewValueInfo = true;
        try (PreparedStatement checkStmt = conn.prepareStatement(checkOriginalSql)) {
            checkStmt.setInt(1, datastoreId);
            checkStmt.setInt(2, datasetId);
            try (ResultSet checkRs = checkStmt.executeQuery()) {
                if (checkRs.next()) {
                    // If count > 0, value info exists in original, so it's an update
                    // If count = 0, value info is new, so it's an insert
                    isNewValueInfo = (checkRs.getInt("count") == 0);
                }
            }
        }
        
        String sql = "SELECT frequency, frequency_comments, availability, availability_comments, values_in_axon " +
                     "FROM values_datastore WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datastoreId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // Use "Inserted" for new value info, "Updated" for existing ones
                    String operation = isNewValueInfo ? "Inserted" : "Updated";
                    
                    // Add changes for each non-null field
                    String frequency = rs.getString("frequency");
                    if (frequency != null && !frequency.isEmpty()) {
                        Map<String, String> change = new HashMap<>();
                        change.put("operation", operation);
                        change.put("fieldName", "Value Update Frequency");
                        change.put("oldValue", "");
                        change.put("newValue", frequency);
                        changes.add(change);
                    }
                    
                    String availability = rs.getString("availability");
                    if (availability != null && !availability.isEmpty()) {
                        Map<String, String> change = new HashMap<>();
                        change.put("operation", operation);
                        change.put("fieldName", "Availability");
                        change.put("oldValue", "");
                        change.put("newValue", availability);
                        changes.add(change);
                    }
                    
                    Boolean valuesInAxon = rs.getObject("values_in_axon", Boolean.class);
                    if (valuesInAxon != null) {
                        Map<String, String> change = new HashMap<>();
                        change.put("operation", operation);
                        change.put("fieldName", "Upload Type");
                        change.put("oldValue", "");
                        change.put("newValue", valuesInAxon ? "Sample Set" : "No");
                        changes.add(change);
                    }
                }
            }
        }
        
        return changes;
    }
    
    /**
     * Get document details for display in CHANGES TO REVIEW
     * Returns document fields: Name, Description, Type, File
     */
    private List<Map<String, String>> getDocumentDetails(Connection conn, String documentTableName, int documentId) throws SQLException {
        List<Map<String, String>> changes = new ArrayList<>();
        
        String sql = "SELECT d.Name, d.Description, d.File_Name, d.File_Path, d.Is_URL, " +
                     "dt.PrimaryName as DocumentType " +
                     "FROM " + documentTableName + " d " +
                     "LEFT JOIN document dt ON d.Document_Type_ID = dt.ID " +
                     "WHERE d.ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, documentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String docName = rs.getString("Name");
                    String docDescription = rs.getString("Description");
                    String docType = rs.getString("DocumentType");
                    String fileName = rs.getString("File_Name");
                    boolean isUrl = rs.getBoolean("Is_URL");
                    
                    // Add Name field
                    if (docName != null && !docName.isEmpty()) {
                        Map<String, String> change = new HashMap<>();
                        change.put("documentName", docName);
                        change.put("fieldName", "Name");
                        change.put("newValue", docName);
                        changes.add(change);
                    }
                    
                    // Add Description field
                    if (docDescription != null && !docDescription.isEmpty()) {
                        Map<String, String> change = new HashMap<>();
                        change.put("documentName", docName);
                        change.put("fieldName", "Description");
                        change.put("newValue", docDescription);
                        changes.add(change);
                    }
                    
                    // Add Type field
                    if (docType != null && !docType.isEmpty()) {
                        Map<String, String> change = new HashMap<>();
                        change.put("documentName", docName);
                        change.put("fieldName", "Type");
                        change.put("newValue", docType);
                        changes.add(change);
                    }
                    
                    // Add File field (filename or URL) with file path for hyperlink
                    if (fileName != null && !fileName.isEmpty()) {
                        Map<String, String> change = new HashMap<>();
                        change.put("documentName", docName);
                        change.put("fieldName", "File");
                        // Include file path for hyperlink - format: fileName|filePath|isUrl
                        String filePath = rs.getString("File_Path");
                        change.put("newValue", fileName);
                        change.put("filePath", filePath != null ? filePath : "");
                        change.put("isUrl", String.valueOf(isUrl));
                        change.put("documentId", String.valueOf(documentId));
                        changes.add(change);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error getting document details for ID {}: {}", documentId, e.getMessage());
        }
        
        return changes;
    }
}
