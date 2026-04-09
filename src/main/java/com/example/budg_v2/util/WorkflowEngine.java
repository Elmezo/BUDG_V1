package com.example.budg_v2.util;

import com.example.budg_v2.dao.*;
import com.example.budg_v2.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.sql.SQLException;
import java.util.*;
import java.util.Map;

/**
 * Workflow execution engine
 * Handles workflow instance creation, task progression, and gateway evaluation
 */
public class WorkflowEngine {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowInstanceDAO instanceDAO;
    private final WorkflowTaskDAO taskDAO;
    private final BpmnContentDAO bpmnDAO;
    private final ChangeRequestDAO changeRequestDAO;
    private final WorkflowRuntimeService runtimeService;
    private final com.example.budg_v2.service.WorkflowNotificationService notificationService;
    private final ProcessDefinitionDAO processDefinitionDAO;

    public WorkflowEngine() {
        this.instanceDAO = new WorkflowInstanceDAO();
        this.taskDAO = new WorkflowTaskDAO();
        this.bpmnDAO = new BpmnContentDAO();
        this.changeRequestDAO = new ChangeRequestDAO();
        this.runtimeService = new WorkflowRuntimeService();
        this.notificationService = new com.example.budg_v2.service.WorkflowNotificationService();
        this.processDefinitionDAO = new ProcessDefinitionDAO();
    }

    /**
     * Start a new workflow instance
     * Creates instance record and initial tasks
     */
    public int startWorkflow(int processDefinitionId, Integer changeRequestId, int startedBy)
            throws Exception {

        logger.info("Starting workflow for process definition: {}, CR: {}", processDefinitionId, changeRequestId);

        // Load BPMN XML
        String bpmnXml = bpmnDAO.getXmlContent(processDefinitionId);
        if (bpmnXml == null) {
            // Try loading from file
            bpmnXml = BpmnFileManager.loadBpmnFile(processDefinitionId);
            if (bpmnXml == null) {
                throw new IllegalStateException("BPMN definition not found for process: " + processDefinitionId);
            }
        }

        Document doc = BpmnParser.parseXml(bpmnXml);

        // Find start event
        Element startEvent = BpmnParser.findStartEvent(doc);
        if (startEvent == null) {
            throw new IllegalStateException("No start event found in BPMN");
        }

        // Create workflow instance
        WorkflowInstance instance = new WorkflowInstance(processDefinitionId, changeRequestId);
        instance.setCurrentBpmnNodeId(startEvent.getAttribute("id"));
        instance.setStatus("Enabled");

        int instanceId = instanceDAO.create(instance);
        instance.setId(instanceId);

        if (changeRequestId != null) {
            // Check if CR already has a workflow instance
            ChangeRequest cr = changeRequestDAO.getChangeRequestById(changeRequestId);
            if (cr != null && cr.getProcessInstanceId() != null && cr.getProcessInstanceId() > 0) {
                // Check if this instance actually exists in workflow_instance table (integrity
                // check)
                WorkflowInstance existing = instanceDAO.findById(cr.getProcessInstanceId());
                if (existing != null) {
                    // Check if it's a zombie instance (no tasks)
                    List<WorkflowTask> existingTasks = taskDAO.findByInstanceId(existing.getId());
                    if (existingTasks.isEmpty()) {
                        logger.warn("Found zombie workflow instance {} (no tasks). Auto-cleaning up to allow restart.",
                                existing.getId());
                        instanceDAO.delete(existing.getId());
                        // Proceed to start new workflow...
                    } else {
                        logger.warn("CR {} already has active workflow instance: {}", changeRequestId,
                                cr.getProcessInstanceId());
                        throw new IllegalStateException("Workflow already started for this change request (Instance "
                                + cr.getProcessInstanceId() + ")");
                    }
                }
            }

            try {
                // Update CR: Link Instance, Set Status 'Running', Set Mandatory=1
                boolean updated = changeRequestDAO.updateChangeRequestOnWorkflowStart(changeRequestId, instanceId);
                if (!updated) {
                    logger.error("❌ [CRITICAL] Failed to update CR {} status to Running after workflow start. Rows updated: 0", changeRequestId);
                    throw new SQLException("Failed to update CR status to Running - no rows were updated");
                } else {
                    logger.info("✅ Successfully updated CR {} status to Running and linked workflow instance {}", changeRequestId, instanceId);
                }
            } catch (SQLException e) {
                logger.error("❌ [CRITICAL ERROR] Could not update CR {} details after workflow start: {}", changeRequestId, e.getMessage(), e);
                // Re-throw to prevent silent failure - workflow instance was created but CR status wasn't updated
                throw new Exception("Workflow instance created but failed to update CR status: " + e.getMessage(), e);
            }
        }

        try {
            // Find and create initial tasks using runtime service
            List<String> nextNodeIds = runtimeService.findNextNodes(doc, startEvent, new HashMap<>(), null);
            // If start event has multiple outgoing flows, treat as implicit parallel
            // gateway
            String parentGatewayId = null;
            List<String> outgoingFlows = BpmnParser.getOutgoingFlows(startEvent);
            if (outgoingFlows.size() > 1) {
                // Use start event ID as parent gateway ID for implicit parallel execution
                parentGatewayId = startEvent.getAttribute("id");
                logger.info("Start event has {} outgoing flows - treating as implicit parallel gateway: {}",
                        outgoingFlows.size(), parentGatewayId);
            }
            java.util.Set<Integer> notifiedUsers = new java.util.HashSet<>();
            // Workflow start doesn't have a decision - pass null for decision and empty map for variables
            List<WorkflowTask> createdTasks = runtimeService.createTasks(doc, instance, nextNodeIds, parentGatewayId,
                    notifiedUsers, null, new HashMap<>());

            // Update instance with first task's node ID if tasks were created
            if (!createdTasks.isEmpty()) {
                instance.setCurrentBpmnNodeId(createdTasks.get(0).getBpmnNodeId());
                instanceDAO.update(instance);
            } else {
                logger.warn("No initial tasks created for workflow instance {}", instanceId);
            }

            logger.info("Workflow instance created: {} with {} initial tasks", instanceId, createdTasks.size());

            // Send CR Start Notification
            try {
                notificationService.sendCrStartNotification(instance, startedBy, new ArrayList<>(notifiedUsers));
            } catch (Exception e) {
                logger.error("Failed to send CR start notification", e);
            }
        } catch (Exception e) {
            logger.error("Failed to start workflow instance {}. Rolling back.", instanceId, e);
            try {
                // Rollback: Delete the zombie instance
                instanceDAO.delete(instanceId);
                // Revert CR update if possible
                if (changeRequestId != null) {
                    ChangeRequest cr = changeRequestDAO.getChangeRequestById(changeRequestId);
                    if (cr != null && cr.getProcessInstanceId() != null && cr.getProcessInstanceId() == instanceId) {
                        // Reset CR workflow fields: clear instance ID, set status back to previous,
                        // clear mandatory flag
                        changeRequestDAO.resetWorkflowFields(changeRequestId);
                        logger.info("Reverted CR {} workflow fields after failed instance creation", changeRequestId);
                    }
                }
            } catch (Exception deleteEx) {
                logger.error("Failed to rollback workflow instance {}", instanceId, deleteEx);
            }
            throw e;
        }

        return instanceId;
    }

    /**
     * Complete a task and advance workflow
     * 
     * @param instanceId Workflow instance ID
     * @param taskId     Task ID to complete
     * @param decision   Decision made: "approve", "reject", "complete", "rework"
     * @param comment    Optional comment
     * @param userId     User ID who completed the task
     */
    public Map<String, Object> completeTask(int instanceId, int taskId, String decision,
            String comment, int userId) throws Exception {

        logger.info("Completing task: {} for instance: {}, decision: {}", taskId, instanceId, decision);

        // Load task
        WorkflowTask task = taskDAO.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        if (!"Pending".equals(task.getStatus()) && !"InProgress".equals(task.getStatus())) {
            throw new IllegalStateException("Task is not in a completable state: " + task.getStatus());
        }

        // Load instance
        WorkflowInstance instance = instanceDAO.findById(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("Workflow instance not found: " + instanceId);
        }

        // Load BPMN
        String bpmnXml = bpmnDAO.getXmlContent(instance.getProcessDefinitionId());
        if (bpmnXml == null) {
            bpmnXml = BpmnFileManager.loadBpmnFile(instance.getProcessDefinitionId());
        }
        Document doc = BpmnParser.parseXml(bpmnXml);

        // Store decision as workflow variable for gateway evaluation
        Map<String, String> variables = new HashMap<>();
        variables.put("last_decision", decision);
        variables.put("last_approval", decision); // For backward compatibility
        variables.put("last_user", String.valueOf(userId));

        // Delegate to runtime service for core completion logic
        WorkflowRuntimeService.TaskCompletionResult completionResult = runtimeService.completeTask(task, instance, doc,
                variables, decision, comment, userId);

        // Format response for API compatibility
        Map<String, Object> result = new HashMap<>();
        result.put("workflowCompleted", completionResult.isWorkflowCompleted());
        result.put("nextTasks", completionResult.getNextTasks());

        // Handle parallel gateway pending case
        if (completionResult.isParallelGatewayPending()) {
            result.put("parallelGatewayPending", true);
            result.put("pendingTaskCount", completionResult.getPendingTaskCount());
            result.put("gatewayId", completionResult.getGatewayId());
        }

        // Add nextTask info if tasks were created
        if (!completionResult.getNextTasks().isEmpty()) {
            Map<String, Object> nextTaskInfo = new HashMap<>();
            WorkflowTask firstTask = completionResult.getNextTasks().get(0);
            nextTaskInfo.put("taskId", firstTask.getId());
            nextTaskInfo.put("taskName", firstTask.getName());
            nextTaskInfo.put("role", firstTask.getRoleName());
            result.put("nextTask", nextTaskInfo);
            logger.info("✅ [WORKFLOW ENGINE] Next task info added to response: ID={}, Name={}, Role={}", 
                firstTask.getId(), firstTask.getName(), firstTask.getRoleName());
        } else {
            logger.info("⚠️ [WORKFLOW ENGINE] No next tasks created - workflow may be completed or no next nodes");
        }

        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("✅ [WORKFLOW ENGINE] Task {} completion finished", taskId);
        logger.info("📊 Workflow completed: {}", completionResult.isWorkflowCompleted());
        logger.info("📊 Next tasks created: {}", completionResult.getNextTasks().size());
        logger.info("═══════════════════════════════════════════════════════════════");
        return result;
    }

    /**
     * Get workflow instance with tasks for a change request
     * Includes process definition information to detect if workflow was updated after instance started
     */
    public Map<String, Object> getWorkflowStatus(int changeRequestId) throws SQLException {
        WorkflowInstance instance = instanceDAO.findByChangeRequestId(changeRequestId);
        if (instance == null) {
            return null;
        }

        List<WorkflowTask> tasks = taskDAO.findByInstanceId(instance.getId());

        // Fetch process definition to include updatedAt timestamp
        ProcessDefinition processDefinition = null;
        try {
            processDefinition = processDefinitionDAO.findById(instance.getProcessDefinitionId());
        } catch (SQLException e) {
            logger.warn("Failed to fetch process definition for workflow status: {}", e.getMessage());
            // Continue without process definition - frontend will handle gracefully
        }

        Map<String, Object> result = new HashMap<>();
        result.put("instance", instance);
        result.put("tasks", tasks);
        
        // Include process definition info if available
        if (processDefinition != null) {
            Map<String, Object> processDefInfo = new HashMap<>();
            processDefInfo.put("id", processDefinition.getId());
            processDefInfo.put("name", processDefinition.getPrimaryName());
            processDefInfo.put("updatedAt", processDefinition.getUpdatedAt());
            result.put("processDefinition", processDefInfo);
        }

        return result;
    }

    /**
     * Add a comment to a task without completing it
     */
    public void addTaskComment(int taskId, String comment, int userId) throws SQLException {
        if (comment == null || comment.trim().isEmpty()) {
            return;
        }

        WorkflowTask task = taskDAO.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        runtimeService.addComment(task, comment, userId);
    }

    /**
     * Get the first active (pending) task for a workflow instance
     */
    public WorkflowTask getActiveTask(int instanceId) throws SQLException {
        return taskDAO.findActiveTaskByInstanceId(instanceId);
    }
}
