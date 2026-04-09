package com.example.unisonsearch.repository;

import com.example.unisonsearch.model.ActiveTask;
import com.example.budg_v2.database.DatabaseConnection;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Repository for task-related database operations.
 */
public class TaskRepository {
    
    /**
     * Find active tasks by object type and ID.
     */
    public List<ActiveTask> findActiveTasksByObject(String objectType, int objectId) throws SQLException {
        List<ActiveTask> tasks = new ArrayList<>();
        
        // Map object type to table and column names
        String objectTable = getObjectTableName(objectType);
        String objectIdColumn = getObjectIdColumn(objectType);
        
        if (objectTable == null || objectIdColumn == null) {
            return tasks;
        }
        
        // Query for direct tasks
        String sql = "SELECT t.*, " +
                    "p.ID as OwnerID, p.First_Name as OwnerFirstName, p.Last_Name as OwnerLastName, " +
                    "ot.Name as ObjectTypeName, " +
                    "obj.PrimaryName as ObjectName " +
                    "FROM task t " +
                    "LEFT JOIN people p ON t.Owner_ID = p.ID " +
                    "INNER JOIN object_type ot ON t.Object_Type_ID = ot.ID " +
                    "LEFT JOIN " + objectTable + " obj ON t.Object_ID = obj." + objectIdColumn + " " +
                    "WHERE t.Object_Type_ID = (SELECT ID FROM object_type WHERE Name = ?) " +
                    "AND t.Object_ID = ? " +
                    "AND t.Status IN ('PENDING_START', 'RUNNING')";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, objectType);
            ps.setInt(2, objectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ActiveTask task = mapRowToActiveTask(rs, objectType);
                    tasks.add(task);
                }
            }
        }
        
        return tasks;
    }
    
    /**
     * Get incomplete workflow tasks for a change request.
     */
    public List<Map<String, Object>> getIncompleteWorkflowTasks(int changeRequestId) throws SQLException {
        List<Map<String, Object>> tasks = new ArrayList<>();
        
        String sql = "SELECT wt.*, wi.ID as WorkflowInstanceID " +
                    "FROM workflow_instance_task wt " +
                    "INNER JOIN workflow_instance wi ON wt.Workflow_Instance_ID = wi.ID " +
                    "WHERE wi.ChangeRequest_ID = ? " +
                    "AND wt.Status IN ('Pending', 'InProgress') " +
                    "AND wt.ID NOT IN (" +
                    "    SELECT Workflow_Task_ID " +
                    "    FROM cr_workflow_completion " +
                    "    WHERE CR_ID = ?" +
                    ") " +
                    "ORDER BY wt.Order_Number";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, changeRequestId);
            ps.setInt(2, changeRequestId);
            
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                while (rs.next()) {
                    Map<String, Object> task = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        task.put(columnName, rs.getObject(i));
                    }
                    tasks.add(task);
                }
            }
        }
        
        return tasks;
    }
    
    /**
     * Find change requests for an object.
     */
    public List<Integer> findChangeRequestsForObject(String objectType, int objectId) throws SQLException {
        List<Integer> crIds = new ArrayList<>();
        
        // Query changerequest table for target object
        String sql = "SELECT ID FROM changerequest " +
                    "WHERE Target_Object_Type = ? AND Target_Object_ID = ? " +
                    "AND DeletedAt IS NULL";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, objectType);
            ps.setInt(2, objectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    crIds.add(rs.getInt("ID"));
                }
            }
        }
        
        return crIds;
    }
    
    /**
     * Map ResultSet row to ActiveTask.
     */
    private ActiveTask mapRowToActiveTask(ResultSet rs, String objectType) throws SQLException {
        ActiveTask task = new ActiveTask();
        
        task.setId(rs.getInt("ID"));
        task.setName(rs.getString("Name"));
        task.setObjectType(objectType);
        task.setObjectId(rs.getInt("Object_ID"));
        task.setObjectName(rs.getString("ObjectName"));
        
        Timestamp dueDate = rs.getTimestamp("Due_Date");
        if (dueDate != null) {
            task.setDueDate(dueDate.toLocalDateTime());
        }
        
        Timestamp assignDate = rs.getTimestamp("Assign_Date");
        if (assignDate != null) {
            task.setAssignDate(assignDate.toLocalDateTime());
        }
        
        // Owner
        Integer ownerId = rs.getObject("OwnerID", Integer.class);
        if (ownerId != null) {
            ActiveTask.Person owner = new ActiveTask.Person();
            owner.setId(ownerId);
            owner.setFirstName(rs.getString("OwnerFirstName"));
            owner.setLastName(rs.getString("OwnerLastName"));
            task.setOwner(owner);
        }
        
        // Status
        String statusStr = rs.getString("Status");
        if (statusStr != null) {
            try {
                task.setStatus(ActiveTask.TaskStatus.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                task.setStatus(ActiveTask.TaskStatus.PENDING_START);
            }
        }
        
        // Overdue
        if (dueDate != null) {
            task.setOverdue(dueDate.toLocalDateTime().isBefore(LocalDateTime.now()) && 
                           !task.getStatus().equals(ActiveTask.TaskStatus.COMPLETED));
        }
        
        return task;
    }
    
    /**
     * Get object table name from object type.
     */
    private String getObjectTableName(String objectType) {
        return switch (objectType.toLowerCase()) {
            case "dataset" -> "dataset";
            case "attribute" -> "attribute";
            case "system" -> "system";
            case "glossary" -> "glossary";
            case "interface" -> "interface";
            case "process" -> "process";
            case "project" -> "project";
            case "policy" -> "policy";
            default -> null;
        };
    }
    
    /**
     * Get object ID column name from object type.
     */
    private String getObjectIdColumn(String objectType) {
        return switch (objectType.toLowerCase()) {
            case "dataset", "attribute", "glossary", "policy" -> "ID";
            case "system", "interface", "process", "project" -> "id";
            default -> "ID";
        };
    }
}

