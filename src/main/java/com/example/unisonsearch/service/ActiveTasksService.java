package com.example.unisonsearch.service;

import com.example.unisonsearch.model.ActiveTask;
import com.example.unisonsearch.repository.TaskRepository;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for retrieving active tasks for objects.
 */
public class ActiveTasksService {
    
    private final TaskRepository taskRepository;
    
    public ActiveTasksService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
    
    /**
     * Get active tasks for an object (workflow + direct tasks).
     */
    public List<ActiveTask> getActiveTasksForObject(String objectType, int objectId) throws SQLException {
        List<ActiveTask> tasks = new ArrayList<>();
        
        // 1. Workflow Tasks from Change Requests
        tasks.addAll(getWorkflowTasks(objectType, objectId));
        
        // 2. Direct Tasks
        tasks.addAll(getDirectTasks(objectType, objectId));
        
        return tasks;
    }
    
    /**
     * Get workflow tasks for an object.
     */
    public List<ActiveTask> getWorkflowTasks(String objectType, int objectId) throws SQLException {
        List<ActiveTask> workflowTasks = new ArrayList<>();
        
        // Find all Change Requests targeting this object
        List<Integer> crIds = taskRepository.findChangeRequestsForObject(objectType, objectId);
        
        for (Integer crId : crIds) {
            // Get incomplete workflow tasks for this CR
            List<Map<String, Object>> wfTasks = taskRepository.getIncompleteWorkflowTasks(crId);
            
            for (Map<String, Object> wfTask : wfTasks) {
                ActiveTask activeTask = createActiveTaskFromWorkflow(wfTask, crId, objectType, objectId);
                if (activeTask != null) {
                    workflowTasks.add(activeTask);
                }
            }
        }
        
        return workflowTasks;
    }
    
    /**
     * Get direct tasks for an object.
     */
    public List<ActiveTask> getDirectTasks(String objectType, int objectId) throws SQLException {
        return taskRepository.findActiveTasksByObject(objectType, objectId);
    }
    
    /**
     * Create ActiveTask from workflow task data.
     */
    private ActiveTask createActiveTaskFromWorkflow(Map<String, Object> wfTask, int changeRequestId, 
                                                    String objectType, int objectId) {
        try {
            ActiveTask task = new ActiveTask();
            
            // ID
            Object idObj = wfTask.get("ID");
            if (idObj instanceof Number) {
                task.setId(((Number) idObj).intValue());
            }
            
            // Name
            task.setName((String) wfTask.getOrDefault("Name", "Workflow Task"));
            
            // Object info
            task.setObjectType(objectType);
            task.setObjectId(objectId);
            task.setObjectName(getObjectName(objectType, objectId));
            
            // Due Date - calculate from workflow
            LocalDateTime dueDate = calculateDueDate(wfTask, changeRequestId);
            task.setDueDate(dueDate);
            
            // Assign Date - from Change Request creation
            LocalDateTime assignDate = getChangeRequestCreatedAt(changeRequestId);
            task.setAssignDate(assignDate);
            
            // Owner - find based on Role
            String roleName = (String) wfTask.get("Role_Name");
            ActiveTask.Person owner = findOwnerForRole(roleName, changeRequestId);
            task.setOwner(owner);
            
            // Status
            String statusStr = (String) wfTask.getOrDefault("Status", "Pending");
            task.setStatus(mapStatus(statusStr));
            
            // Overdue
            if (dueDate != null) {
                task.setOverdue(dueDate.isBefore(LocalDateTime.now()) && 
                               !task.getStatus().equals(ActiveTask.TaskStatus.COMPLETED));
            }
            
            // Change Request ID
            task.setChangeRequestId(changeRequestId);
            
            // Workflow Step
            Object orderObj = wfTask.get("Order_Number");
            if (orderObj != null) {
                task.setWorkflowStep("Step " + orderObj);
            } else {
                task.setWorkflowStep("Workflow Task");
            }
            
            return task;
        } catch (Exception e) {
            System.err.println("Error creating ActiveTask from workflow: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Calculate due date for workflow task.
     */
    private LocalDateTime calculateDueDate(Map<String, Object> wfTask, int changeRequestId) {
        // Try to get Due_At or Due_Date from task
        Object dueAt = wfTask.get("Due_At");
        Object dueDate = wfTask.get("Due_Date");
        
        if (dueAt instanceof Timestamp) {
            return ((Timestamp) dueAt).toLocalDateTime();
        }
        if (dueDate instanceof Timestamp) {
            return ((Timestamp) dueDate).toLocalDateTime();
        }
        
        // If not set, calculate from Change Request creation + default days
        LocalDateTime crCreatedAt = getChangeRequestCreatedAt(changeRequestId);
        if (crCreatedAt != null) {
            // Default: 7 days from CR creation
            return crCreatedAt.plusDays(7);
        }
        
        return null;
    }
    
    /**
     * Get Change Request creation date.
     */
    private LocalDateTime getChangeRequestCreatedAt(int changeRequestId) {
        // TODO: Use DatabaseHelper or proper DAO to get actual creation date
        // For now, return current time
        return LocalDateTime.now();
    }
    
    /**
     * Find owner for a role in a Change Request.
     */
    private ActiveTask.Person findOwnerForRole(String roleName, int changeRequestId) {
        // TODO: Implement role-based owner lookup
        // For now, return null (unassigned)
        return null;
    }
    
    /**
     * Get object name.
     */
    private String getObjectName(String objectType, int objectId) {
        // TODO: Implement object name lookup
        return objectType + " #" + objectId;
    }
    
    /**
     * Map workflow status to ActiveTask status.
     */
    private ActiveTask.TaskStatus mapStatus(String statusStr) {
        if (statusStr == null) return ActiveTask.TaskStatus.PENDING_START;
        
        return switch (statusStr.toUpperCase()) {
            case "PENDING" -> ActiveTask.TaskStatus.PENDING_START;
            case "INPROGRESS", "IN_PROGRESS" -> ActiveTask.TaskStatus.IN_PROGRESS;
            case "RUNNING" -> ActiveTask.TaskStatus.RUNNING;
            case "COMPLETED" -> ActiveTask.TaskStatus.COMPLETED;
            case "CANCELLED" -> ActiveTask.TaskStatus.CANCELLED;
            default -> ActiveTask.TaskStatus.PENDING_START;
        };
    }
}

