package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.WorkflowTask;
import com.example.budg_v2.service.SegmentAccessService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * DAO for workflow_instance_task table operations
 */
public class WorkflowTaskDAO {

    /**
     * Create a new workflow task
     */
    public int create(WorkflowTask task) throws SQLException {
        // Try full insert first (Phase 4 schema)
        String sql = "INSERT INTO workflow_instance_task (Workflow_Instance_ID, Bpmn_Node_Id, Parent_Gateway_ID, Name, "
                +
                "Status, Role_Name, Assigned_At, Assigned_To, Due_Date, Started_At, Due_At, Is_Overdue, Escalated_At) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, task.getWorkflowInstanceId());
            stmt.setString(2, task.getBpmnNodeId());

            if (task.getParentGatewayId() != null) {
                stmt.setString(3, task.getParentGatewayId());
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }

            stmt.setString(4, task.getName());
            stmt.setString(5, task.getStatus());

            if (task.getRoleName() != null) {
                stmt.setString(6, task.getRoleName());
            } else {
                stmt.setNull(6, Types.VARCHAR);
            }

            if (task.getAssignedAt() != null) {
                stmt.setTimestamp(7, task.getAssignedAt());
            } else {
                stmt.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            }

            if (task.getAssignedTo() != null) {
                stmt.setInt(8, task.getAssignedTo());
            } else {
                stmt.setNull(8, Types.INTEGER);
            }

            if (task.getDueDate() != null) {
                stmt.setTimestamp(9, task.getDueDate());
            } else {
                stmt.setNull(9, Types.TIMESTAMP);
            }

            if (task.getStartedAt() != null) {
                stmt.setTimestamp(10, task.getStartedAt());
            } else {
                stmt.setNull(10, Types.TIMESTAMP);
            }

            // SLA columns
            if (task.getDueAt() != null) {
                stmt.setTimestamp(11, task.getDueAt());
            } else {
                stmt.setNull(11, Types.TIMESTAMP);
            }

            if (task.getIsOverdue() != null) {
                stmt.setInt(12, task.getIsOverdue() ? 1 : 0);
            } else {
                stmt.setInt(12, 0); // Default to 0
            }

            if (task.getEscalatedAt() != null) {
                stmt.setTimestamp(13, task.getEscalatedAt());
            } else {
                stmt.setNull(13, Types.TIMESTAMP);
            }

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            // Fallback: Try legacy insert (older schema without Role_Name,
            // Parent_Gateway_ID, SLA columns)
            // Assuming this might be the cause of failure
            return createLegacy(task, e);
        }
        throw new SQLException("Failed to create workflow task, no ID obtained");
    }

    private int createLegacy(WorkflowTask task, SQLException originalError) throws SQLException {
        // Fallback for older schema
        String sql = "INSERT INTO workflow_instance_task (Workflow_Instance_ID, Bpmn_Node_Id, Name, " +
                "Status, Assigned_At, Assigned_To, Due_Date, Started_At) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, task.getWorkflowInstanceId());
            stmt.setString(2, task.getBpmnNodeId());
            stmt.setString(3, task.getName());
            stmt.setString(4, task.getStatus());

            if (task.getAssignedAt() != null) {
                stmt.setTimestamp(5, task.getAssignedAt());
            } else {
                stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            }

            if (task.getAssignedTo() != null) {
                stmt.setInt(6, task.getAssignedTo());
            } else {
                stmt.setNull(6, Types.INTEGER);
            }

            if (task.getDueDate() != null) {
                stmt.setTimestamp(7, task.getDueDate());
            } else {
                stmt.setNull(7, Types.TIMESTAMP);
            }

            if (task.getStartedAt() != null) {
                stmt.setTimestamp(8, task.getStartedAt());
            } else {
                stmt.setNull(8, Types.TIMESTAMP);
            }

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException legacyEx) {
            // If legacy also fails, throw original error
            throw new SQLException("Failed to create workflow task (Full and Legacy modes failed). Original error: "
                    + originalError.getMessage(), originalError);
        }
        throw new SQLException("Failed to create workflow task (legacy), no ID obtained");
    }

    /**
     * Update workflow task
     */
    public void update(WorkflowTask task) throws SQLException {
        String sql = "UPDATE workflow_instance_task SET Status = ?, Assigned_To = ?, " +
                "Started_At = ?, Completed_At = ?, Completed_By = ?, Decision = ?, " +
                "Due_At = ?, Is_Overdue = ?, Escalated_At = ? WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, task.getStatus());

            if (task.getAssignedTo() != null) {
                stmt.setInt(2, task.getAssignedTo());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }

            if (task.getStartedAt() != null) {
                stmt.setTimestamp(3, task.getStartedAt());
            } else {
                stmt.setNull(3, Types.TIMESTAMP);
            }

            if (task.getCompletedAt() != null) {
                stmt.setTimestamp(4, task.getCompletedAt());
            } else {
                stmt.setNull(4, Types.TIMESTAMP);
            }

            if (task.getCompletedBy() != null) {
                stmt.setInt(5, task.getCompletedBy());
            } else {
                stmt.setNull(5, Types.INTEGER);
            }

            if (task.getDecision() != null) {
                stmt.setString(6, task.getDecision());
            } else {
                stmt.setNull(6, Types.VARCHAR);
            }

            // SLA columns
            if (task.getDueAt() != null) {
                stmt.setTimestamp(7, task.getDueAt());
            } else {
                stmt.setNull(7, Types.TIMESTAMP);
            }

            if (task.getIsOverdue() != null) {
                stmt.setInt(8, task.getIsOverdue() ? 1 : 0);
            } else {
                stmt.setInt(8, 0); // Default to 0
            }

            if (task.getEscalatedAt() != null) {
                stmt.setTimestamp(9, task.getEscalatedAt());
            } else {
                stmt.setNull(9, Types.TIMESTAMP);
            }

            stmt.setInt(10, task.getId());

            stmt.executeUpdate();
        }
    }

    /**
     * Find task by ID
     */
    public WorkflowTask findById(int id) throws SQLException {
        String sql = "SELECT * FROM workflow_instance_task WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find all tasks for a workflow instance
     */
    public List<WorkflowTask> findByInstanceId(int instanceId) throws SQLException {
        String sql = "SELECT * FROM workflow_instance_task WHERE Workflow_Instance_ID = ? ORDER BY ID";
        List<WorkflowTask> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, instanceId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Find pending tasks for a workflow instance
     */
    public List<WorkflowTask> findPendingByInstanceId(int instanceId) throws SQLException {
        String sql = "SELECT * FROM workflow_instance_task WHERE Workflow_Instance_ID = ? " +
                "AND Status IN ('Pending', 'InProgress') ORDER BY ID";
        List<WorkflowTask> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, instanceId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Find the first active (pending) task for a workflow instance
     */
    public WorkflowTask findActiveTaskByInstanceId(int instanceId) throws SQLException {
        String sql = "SELECT * FROM workflow_instance_task WHERE Workflow_Instance_ID = ? " +
                "AND Status IN ('Pending', 'InProgress') ORDER BY ID LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, instanceId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find tasks by instance ID and role name
     */
    public List<WorkflowTask> findByInstanceIdAndRole(int instanceId, String roleName) throws SQLException {
        String sql = "SELECT * FROM workflow_instance_task WHERE Workflow_Instance_ID = ? " +
                "AND Role_Name = ? ORDER BY ID";
        List<WorkflowTask> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, instanceId);
            stmt.setString(2, roleName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Map ResultSet to WorkflowTask object
     */
    private WorkflowTask mapResultSet(ResultSet rs) throws SQLException {
        WorkflowTask task = new WorkflowTask();
        task.setId(rs.getInt("ID"));
        task.setWorkflowInstanceId(rs.getInt("Workflow_Instance_ID"));
        task.setBpmnNodeId(rs.getString("Bpmn_Node_Id"));

        // Handle Parent_Gateway_ID (nullable, may not exist in older schemas)
        try {
            task.setParentGatewayId(rs.getString("Parent_Gateway_ID"));
        } catch (SQLException e) {
            // Column might not exist in older schemas
            task.setParentGatewayId(null);
        }

        task.setName(rs.getString("Name"));
        task.setStatus(rs.getString("Status"));

        // Handle nullable columns
        try {
            task.setRoleName(rs.getString("Role_Name"));
        } catch (SQLException e) {
            // Column might not exist in older schemas
            task.setRoleName(null);
        }

        int assignedTo = rs.getInt("Assigned_To");
        if (!rs.wasNull()) {
            task.setAssignedTo(assignedTo);
        }

        task.setDueDate(rs.getTimestamp("Due_Date"));
        task.setStartedAt(rs.getTimestamp("Started_At"));
        task.setCompletedAt(rs.getTimestamp("Completed_At"));

        try {
            task.setAssignedAt(rs.getTimestamp("Assigned_At"));
        } catch (SQLException e) {
            // Column might not exist in older schemas
            task.setAssignedAt(null);
        }

        try {
            int completedBy = rs.getInt("Completed_By");
            if (!rs.wasNull()) {
                task.setCompletedBy(completedBy);
            }
        } catch (SQLException e) {
            // Column might not exist in older schemas
            task.setCompletedBy(null);
        }

        try {
            task.setDecision(rs.getString("Decision"));
        } catch (SQLException e) {
            // Column might not exist in older schemas
            task.setDecision(null);
        }

        // SLA columns
        try {
            task.setDueAt(rs.getTimestamp("Due_At"));
        } catch (SQLException e) {
            // Column might not exist in older schemas
            task.setDueAt(null);
        }

        try {
            int isOverdue = rs.getInt("Is_Overdue");
            if (!rs.wasNull()) {
                task.setIsOverdue(isOverdue == 1);
            } else {
                task.setIsOverdue(false);
            }
        } catch (SQLException e) {
            // Column might not exist in older schemas
            task.setIsOverdue(false);
        }

        try {
            task.setEscalatedAt(rs.getTimestamp("Escalated_At"));
        } catch (SQLException e) {
            // Column might not exist in older schemas
            task.setEscalatedAt(null);
        }

        return task;
    }

    /**
     * Find all active tasks for SLA evaluation
     */
    public List<WorkflowTask> findActiveTasksForSlaEvaluation() throws SQLException {
        String sql = "SELECT * FROM workflow_instance_task " +
                "WHERE Status IN ('Pending', 'InProgress') " +
                "AND Due_At IS NOT NULL " +
                "ORDER BY Due_At ASC";
        List<WorkflowTask> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Bulk update SLA status for multiple tasks
     */
    public void updateSlaStatus(int taskId, boolean isOverdue, Timestamp escalatedAt) throws SQLException {
        String sql = "UPDATE workflow_instance_task SET Is_Overdue = ?, Escalated_At = ? WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, isOverdue ? 1 : 0);
            if (escalatedAt != null) {
                stmt.setTimestamp(2, escalatedAt);
            } else {
                stmt.setNull(2, Types.TIMESTAMP);
            }
            stmt.setInt(3, taskId);

            stmt.executeUpdate();
        }
    }

    /**
     * Find all tasks created by a specific parallel gateway
     * 
     * @param instanceId Workflow instance ID
     * @param gatewayId  BPMN node ID of the parallel gateway
     * @return List of tasks created by the gateway
     */
    public List<WorkflowTask> findByParentGatewayId(int instanceId, String gatewayId) throws SQLException {
        String sql = "SELECT * FROM workflow_instance_task WHERE Workflow_Instance_ID = ? " +
                "AND Parent_Gateway_ID = ? ORDER BY ID";
        List<WorkflowTask> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, instanceId);
            stmt.setString(2, gatewayId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Count pending/in-progress tasks for a parallel gateway
     * 
     * @param instanceId Workflow instance ID
     * @param gatewayId  BPMN node ID of the parallel gateway
     * @return Count of pending or in-progress tasks
     */
    public int countPendingTasksByParentGatewayId(int instanceId, String gatewayId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM workflow_instance_task " +
                "WHERE Workflow_Instance_ID = ? AND Parent_Gateway_ID = ? " +
                "AND Status IN ('Pending', 'InProgress')";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, instanceId);
            stmt.setString(2, gatewayId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Find all active tasks visible to a user: pending/in-progress tasks whose segment the user can access
     * (see {@link SegmentAccessService#hasSegmentAccess(int, int)}), plus all tasks for Super Admins.
     * Segment is resolved from the Change Request, then from the source object, matching CR creation rules.
     *
     * @param userId User ID to filter tasks for
     * @return List of task data maps with all required fields for Active Tasks view
     */
    public List<java.util.Map<String, Object>> findActiveTasksForUser(int userId) throws SQLException {
        List<java.util.Map<String, Object>> tasks = new ArrayList<>();

        // Fetch active tasks joined with workflow instance, CR, CR type, and assignee people.
        // cr.Created_At is used as assignDate (date CR was raised, per spec).
        // DATEDIFF on COALESCE(Due_At, Due_Date) gives Due Days.
        String sql = "SELECT " +
                "t.ID as taskId, " +
                "t.Name as taskName, " +
                "t.Status as taskStatus, " +
                "t.Role_Name as roleName, " +
                "t.Assigned_To as assignedTo, " +
                "t.Assigned_At as assignedAt, " +
                "t.Due_At as dueAt, " +
                "t.Due_Date as dueDate, " +
                "t.Bpmn_Node_Id as bpmnNodeId, " +
                "t.Workflow_Instance_ID as workflowInstanceId, " +
                "wi.ChangeRequest_ID as changeRequestId, " +
                "cr.PrimaryName as crTitle, " +
                "cr.Reference as crReference, " +
                "cr.Created_At as crCreatedAt, " +
                "cr.Created_By as crCreatedBy, " +
                "crt.PrimaryName as crType, " +
                "p.First_Name as ownerFirstName, " +
                "p.Last_Name as ownerLastName, " +
                "DATEDIFF(COALESCE(t.Due_At, t.Due_Date), CURDATE()) as dueInDays " +
                "FROM workflow_instance_task t " +
                "JOIN workflow_instance wi ON t.Workflow_Instance_ID = wi.ID " +
                "LEFT JOIN changerequest cr ON wi.ChangeRequest_ID = cr.ID " +
                "LEFT JOIN changerequeststatus cs ON cr.CR_StatusID = cs.ID " +
                "LEFT JOIN changerequest_type crt ON cr.CR_TypeID = crt.ID " +
                "LEFT JOIN people p ON t.Assigned_To = p.ID " +
                "WHERE t.Status IN ('Pending', 'InProgress') " +
                "AND (cr.ID IS NULL " +
                "     OR (cr.Deleted_At IS NULL " +
                "         AND (cs.PrimaryName IS NULL " +
                "              OR (UPPER(cs.PrimaryName) NOT LIKE '%COMPLETED%' " +
                "                  AND UPPER(cs.PrimaryName) NOT LIKE '%CANCELLED%' " +
                "                  AND UPPER(cs.PrimaryName) NOT LIKE '%CANCELED%')))) " +
                // Do not list gateway "Decision" tasks (WorkflowRuntimeService creates them with Name='Decision' and Bpmn_Node_Id = exclusive gateway)
                "AND NOT (t.Name = 'Decision' AND t.Bpmn_Node_Id IS NOT NULL) " +
                "ORDER BY COALESCE(t.Due_At, t.Due_Date) ASC";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            SegmentDAO segmentDAO = new SegmentDAO();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> taskData = new java.util.HashMap<>();

                    // Basic task info
                    taskData.put("id", rs.getString("taskId"));
                    taskData.put("taskId", rs.getInt("taskId"));
                    taskData.put("name", rs.getString("taskName"));
                    taskData.put("status", rs.getString("taskStatus"));
                    taskData.put("roleName", rs.getString("roleName"));
                    taskData.put("bpmnNodeId", rs.getString("bpmnNodeId"));
                    taskData.put("workflowInstanceId", rs.getInt("workflowInstanceId"));

                    // Assignment info
                    int assignedTo = rs.getInt("assignedTo");
                    boolean assignedToPresent = !rs.wasNull() && assignedTo > 0;
                    if (assignedToPresent) {
                        taskData.put("assignedTo", assignedTo);
                    }

                    // Due date info
                    Timestamp dueAt = rs.getTimestamp("dueAt");
                    Timestamp dueDate = rs.getTimestamp("dueDate");
                    Timestamp dueTimestamp = dueAt != null ? dueAt : dueDate;
                    if (dueTimestamp != null) {
                        taskData.put("dueDate", dueTimestamp);
                    }

                    // Due In Days calculation
                    int dueInDays = rs.getInt("dueInDays");
                    if (!rs.wasNull()) {
                        taskData.put("dueInDays", dueInDays);
                        taskData.put("isOverdue", dueInDays < 0);
                    } else {
                        taskData.put("dueInDays", null);
                        taskData.put("isOverdue", false);
                    }

                    // Change Request info
                    int changeRequestId = rs.getInt("changeRequestId");
                    boolean hasChangeRequestId = !rs.wasNull() && changeRequestId > 0;
                    if (hasChangeRequestId) {
                        taskData.put("changeRequestId", changeRequestId);
                        taskData.put("title", rs.getString("crTitle"));
                        taskData.put("crReference", rs.getString("crReference"));

                        // assignDate = date the CR was raised (per spec)
                        Timestamp crCreatedAt = rs.getTimestamp("crCreatedAt");
                        if (crCreatedAt != null) {
                            taskData.put("assignDate", crCreatedAt);
                        }

                        int crCreatedByCol = rs.getInt("crCreatedBy");
                        if (!rs.wasNull()) {
                            taskData.put("crCreatedBy", crCreatedByCol);
                        }

                        // Resolve Object Type and actual Object name from CR Reference.
                        // Reference format: "FacetType ObjectId" (e.g., "Dataset 47", "System 5")
                        String crReference = rs.getString("crReference");
                        if (crReference != null && !crReference.isEmpty()) {
                            Pattern pattern = Pattern.compile("^(.+?)\\s+(\\d+)$");
                            Matcher matcher = pattern.matcher(crReference.trim());

                            if (matcher.matches()) {
                                String facetRaw = matcher.group(1).trim();
                                int objectId = Integer.parseInt(matcher.group(2));

                                String displayType = toDisplayObjectType(facetRaw);
                                String normalizedType = facetRaw.toLowerCase()
                                        .replace(" ", "-").replace("_", "-");

                                taskData.put("crFacetType", facetRaw);
                                taskData.put("objectType", displayType);
                                taskData.put("objectId", objectId);
                                taskData.put("objectTypeNormalized", normalizedType);

                                // Resolve actual object name from the database
                                String objectName = resolveObjectName(conn, facetRaw, objectId);
                                taskData.put("object", objectName != null ? objectName : crReference);
                            } else {
                                taskData.put("objectType", "Change Request");
                                taskData.put("object", crReference);
                            }
                        } else {
                            taskData.put("objectType", "Change Request");
                            taskData.put("object", "N/A");
                        }
                    }

                    // CR Type
                    String crType = rs.getString("crType");
                    if (crType != null) {
                        taskData.put("crType", crType);
                    }

                    // Owner info
                    String ownerFirstName = rs.getString("ownerFirstName");
                    String ownerLastName = rs.getString("ownerLastName");
                    if (ownerFirstName != null || ownerLastName != null) {
                        String ownerName = (ownerFirstName != null ? ownerFirstName : "") +
                                (ownerLastName != null ? " " + ownerLastName : "");
                        taskData.put("owner", ownerName.trim());
                    } else {
                        taskData.put("owner", "Unassigned");
                    }

                    // Segments: prefer Change Request's segment, then source object's segment (same as CR creation)
                    int resolvedSegmentId = -1;
                    if (hasChangeRequestId) {
                        try {
                            resolvedSegmentId = segmentDAO.getObjectSegmentId(changeRequestId, "ChangeRequest", conn);
                        } catch (Exception e) {
                            System.err.println("[WorkflowTaskDAO] Error resolving CR segment for task " + taskData.get("taskId") + ": " + e.getMessage());
                        }
                    }
                    if (resolvedSegmentId <= 0 && taskData.containsKey("objectId") && taskData.containsKey("crFacetType")) {
                        String objectTypeForSeg = facetTypeToObjectTypeForSegment((String) taskData.get("crFacetType"));
                        if (objectTypeForSeg != null) {
                            int oid = ((Number) taskData.get("objectId")).intValue();
                            try {
                                resolvedSegmentId = segmentDAO.getObjectSegmentId(oid, objectTypeForSeg, conn);
                            } catch (Exception e) {
                                System.err.println("[WorkflowTaskDAO] Error resolving object segment for task " + taskData.get("taskId") + ": " + e.getMessage());
                            }
                        }
                    }

                    String segmentLabel;
                    int accessSegmentId;
                    if (resolvedSegmentId > 0) {
                        try {
                            segmentLabel = segmentDAO.getSegmentNameById(resolvedSegmentId, conn);
                        } catch (Exception e) {
                            segmentLabel = "Not Assigned";
                            System.err.println("[WorkflowTaskDAO] getSegmentNameById failed: " + e.getMessage());
                        }
                        if (segmentLabel == null) {
                            segmentLabel = "Not Assigned";
                        }
                        accessSegmentId = resolvedSegmentId;
                    } else {
                        segmentLabel = "Not Assigned";
                        // No segment on record: treat as Enterprise for ACL (same as default CR segment)
                        accessSegmentId = 1;
                    }
                    taskData.put("segmentId", resolvedSegmentId > 0 ? resolvedSegmentId : null);
                    taskData.put("segments", segmentLabel);

                    // Filter: user must have access to the task's segment (or be Super Admin)
                    boolean shouldInclude;
                    try {
                        if (SegmentAccessService.isSuperAdmin(userId)) {
                            shouldInclude = true;
                        } else {
                            shouldInclude = SegmentAccessService.hasSegmentAccess(userId, accessSegmentId);
                        }
                    } catch (SQLException e) {
                        shouldInclude = false;
                        System.err.println("[WorkflowTaskDAO] segment access check failed for user " + userId + ": " + e.getMessage());
                    }

                    if (shouldInclude) {
                        tasks.add(taskData);
                    }
                }
            }
        }

        return tasks;
    }

    /**
     * Map a raw facet type string (from CR Reference) to a clean display label.
     */
    /**
     * Map facet type from CR reference (e.g. "dataset", "System") to {@link SegmentDAO} object type.
     * Aligns with {@code ChangeRequestServlet#facetTypeToObjectType} for segment resolution.
     */
    private String facetTypeToObjectTypeForSegment(String facetType) {
        if (facetType == null || facetType.trim().isEmpty()) {
            return null;
        }
        String normalized = facetType.toLowerCase().trim()
                .replace("-", "").replace("_", "").replace(" ", "");
        return switch (normalized) {
            case "dataset", "datasets" -> "Dataset";
            case "system", "systems" -> "System";
            case "glossary", "glossaries" -> "Glossary";
            case "process", "processes" -> "Process";
            case "project", "projects" -> "Project";
            case "product", "products" -> "Product";
            case "policy", "policies" -> "Policy";
            case "attribute", "attributes" -> "Dataset";
            case "interface", "interfaces", "systeminterface" -> "SystemInterface";
            case "capability", "capabilities" -> "Capability";
            case "client", "clients" -> "Client";
            case "committee", "committees" -> "Committee";
            case "legalentity", "legal" -> "LegalEntity";
            case "businessarea" -> "BusinessArea";
            case "regulation", "regulations" -> "Regulation";
            case "regulator", "regulators" -> "Regulator";
            case "regulatorytheme" -> "RegulatoryTheme";
            case "geography", "geographies" -> "Geography";
            case "orgunit" -> "OrgUnit";
            case "people", "person" -> "People";
            default -> null;
        };
    }

    private String toDisplayObjectType(String facetRaw) {
        if (facetRaw == null) return "Unknown";
        return switch (facetRaw.toLowerCase().trim()) {
            case "dataset", "data set", "data sets" -> "Data Set";
            case "system", "systems" -> "System";
            case "glossary", "glossaries" -> "Glossary";
            case "attribute", "attributes" -> "Attribute";
            case "process", "processes" -> "Process";
            case "project", "projects" -> "Project";
            case "policy", "policies" -> "Policy";
            case "capability", "capabilities" -> "Capability";
            case "legal_entity", "legalentity", "legal entity" -> "Legal Entity";
            case "business_area", "businessarea", "business area" -> "Business Area";
            case "committee", "committees" -> "Committee";
            case "client", "clients" -> "Client";
            case "geography", "geographies" -> "Geography";
            case "orgunit", "org_unit", "org unit" -> "Org Unit";
            case "regulation", "regulations" -> "Regulation";
            case "regulator", "regulators" -> "Regulator";
            case "regulatory_theme", "regulatorytheme", "regulatory theme" -> "Regulatory Theme";
            case "interface", "interfaces" -> "Interface";
            case "people", "person" -> "People";
            case "product", "products" -> "Product";
            default -> facetRaw;
        };
    }

    /**
     * Resolve the display name of an object given its facet type and ID.
     * Returns null if the name cannot be resolved.
     */
    private String resolveObjectName(java.sql.Connection conn, String facetRaw, int objectId) {
        if (facetRaw == null || objectId <= 0) return null;
        String facetKey = facetRaw.toLowerCase().trim();
        try {
            // system uses Name + id, not PrimaryName + ID (matches UnisonSearchService.fetchObjectName)
            if ("system".equals(facetKey) || "systems".equals(facetKey)) {
                String nameSql = "SELECT Name FROM `system` WHERE id = ? AND Deleted_datetime IS NULL LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(nameSql)) {
                    ps.setInt(1, objectId);
                    try (ResultSet nr = ps.executeQuery()) {
                        if (nr.next()) {
                            return nr.getString(1);
                        }
                    }
                }
                return null;
            }

            String table;
            String nameCol;
            String idCol = "ID";
            switch (facetKey) {
                case "dataset", "data set", "data sets" -> { table = "dataset"; nameCol = "PrimaryName"; }
                case "glossary", "glossaries" -> { table = "glossary"; nameCol = "Name"; }
                case "attribute", "attributes" -> { table = "attribute"; nameCol = "PrimaryName"; }
                case "process", "processes" -> { table = "process"; nameCol = "PrimaryName"; }
                case "project", "projects" -> { table = "project"; nameCol = "PrimaryName"; }
                case "policy", "policies" -> { table = "policy"; nameCol = "PrimaryName"; }
                case "capability", "capabilities" -> { table = "capability"; nameCol = "PrimaryName"; }
                case "legal_entity", "legalentity", "legal entity" -> { table = "legalentity"; nameCol = "ShortName"; }
                case "business_area", "businessarea", "business area" -> { table = "business_area"; nameCol = "PrimaryName"; }
                case "committee", "committees" -> { table = "committee"; nameCol = "PrimaryName"; }
                case "client", "clients" -> { table = "client"; nameCol = "PrimaryName"; }
                case "geography", "geographies" -> { table = "geography"; nameCol = "PrimaryName"; }
                case "orgunit", "org_unit", "org unit" -> { table = "orgunit"; nameCol = "PrimaryName"; }
                case "regulation", "regulations" -> { table = "regulation"; nameCol = "PrimaryName"; }
                case "regulator", "regulators" -> { table = "regulator"; nameCol = "PrimaryName"; }
                case "regulatory_theme", "regulatorytheme", "regulatory theme" -> { table = "regulatorytheme"; nameCol = "PrimaryName"; }
                case "interface", "interfaces" -> { table = "interface"; nameCol = "PrimaryName"; }
                case "people", "person" -> { table = "people"; nameCol = "First_Name"; idCol = "ID"; }
                case "product", "products" -> { table = "product"; nameCol = "PrimaryName"; }
                default -> { return null; }
            }
            String nameSql = "SELECT " + nameCol + " FROM `" + table + "` WHERE `" + idCol + "` = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(nameSql)) {
                ps.setInt(1, objectId);
                try (ResultSet nr = ps.executeQuery()) {
                    if (nr.next()) {
                        return nr.getString(1);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[WorkflowTaskDAO] resolveObjectName failed for " + facetRaw + " #" + objectId + ": " + e.getMessage());
        }
        return null;
    }
}
