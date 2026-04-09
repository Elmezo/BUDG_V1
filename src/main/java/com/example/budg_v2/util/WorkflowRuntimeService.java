package com.example.budg_v2.util;

import com.example.budg_v2.dao.*;
import com.example.budg_v2.model.*;
import com.example.budg_v2.service.WorkflowNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core workflow runtime execution service
 * Handles task creation, completion, and gateway evaluation
 * Extracted from WorkflowEngine for better separation of concerns
 */
public class WorkflowRuntimeService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowRuntimeService.class);

    private final WorkflowInstanceDAO instanceDAO;
    private final WorkflowTaskDAO taskDAO;
    private final WorkflowTaskCommentDAO commentDAO;
    private final WorkflowNotificationService notificationService;

    public WorkflowRuntimeService() {
        this.instanceDAO = new WorkflowInstanceDAO();
        this.taskDAO = new WorkflowTaskDAO();
        this.commentDAO = new WorkflowTaskCommentDAO();
        this.notificationService = new WorkflowNotificationService();
    }

    /**
     * Result object for task completion operation
     */
    public static class TaskCompletionResult {
        private boolean workflowCompleted;
        private List<WorkflowTask> nextTasks;
        private boolean parallelGatewayPending;
        private int pendingTaskCount;
        private String gatewayId;

        public TaskCompletionResult() {
            this.nextTasks = new ArrayList<>();
            this.parallelGatewayPending = false;
        }

        public boolean isWorkflowCompleted() {
            return workflowCompleted;
        }

        public void setWorkflowCompleted(boolean workflowCompleted) {
            this.workflowCompleted = workflowCompleted;
        }

        public List<WorkflowTask> getNextTasks() {
            return nextTasks;
        }

        public void setNextTasks(List<WorkflowTask> nextTasks) {
            this.nextTasks = nextTasks;
        }

        public boolean isParallelGatewayPending() {
            return parallelGatewayPending;
        }

        public void setParallelGatewayPending(boolean parallelGatewayPending) {
            this.parallelGatewayPending = parallelGatewayPending;
        }

        public int getPendingTaskCount() {
            return pendingTaskCount;
        }

        public void setPendingTaskCount(int pendingTaskCount) {
            this.pendingTaskCount = pendingTaskCount;
        }

        public String getGatewayId() {
            return gatewayId;
        }

        public void setGatewayId(String gatewayId) {
            this.gatewayId = gatewayId;
        }
    }

    /**
     * Create tasks for user task nodes
     * 
     * @param doc             BPMN document
     * @param instance        Workflow instance
     * @param nodeIds         List of node IDs to create tasks for
     * @param parentGatewayId Optional BPMN node ID of the parallel gateway that
     *                        created these tasks (null if not from parallel
     *                        gateway)
     * @param notifiedUsers   Set of user IDs who have been notified (to avoid duplicates)
     * @param decision        Optional decision for gateway evaluation (null if not applicable)
     * @param variables       Optional workflow variables for condition evaluation (null if not applicable)
     * @return List of created tasks
     */
    public List<WorkflowTask> createTasks(Document doc, WorkflowInstance instance,
            List<String> nodeIds, String parentGatewayId, java.util.Set<Integer> notifiedUsers,
            String decision, Map<String, String> variables) throws SQLException {
        List<WorkflowTask> createdTasks = new ArrayList<>();

        for (String nodeId : nodeIds) {
            Element element = BpmnParser.findElementById(doc, nodeId);
            if (element == null)
                continue;

            // Only create tasks for user tasks
            if (BpmnParser.isUserTask(element)) {
                Map<String, String> properties = BpmnParser.extractTaskProperties(element);

                WorkflowTask task = new WorkflowTask();
                task.setWorkflowInstanceId(instance.getId());
                task.setBpmnNodeId(nodeId);
                task.setParentGatewayId(parentGatewayId); // Set parent gateway ID if from parallel gateway
                task.setName(properties.getOrDefault("name", "Unnamed Task"));
                task.setStatus("Pending");

                // Extract role from BPMN lane
                String roleName = BpmnParser.getRoleForTask(doc, nodeId);

                if (roleName == null || roleName.isEmpty()) {
                    // Fallback to role from task properties if no lane role found
                    String taskRole = properties.get("role");
                    if (taskRole != null && !taskRole.trim().isEmpty()) {
                        roleName = BpmnParser.normalizeRoleName(taskRole);
                    }
                }
                task.setRoleName(roleName);

                // Set assigned at timestamp
                Timestamp assignedAt = new Timestamp(System.currentTimeMillis());
                task.setAssignedAt(assignedAt);

                // Calculate due date using SLA parser
                Integer dueDays = BpmnParser.getTaskDueDays(element);
                if (dueDays == null) {
                    // Use default from config
                    dueDays = WorkflowSlaConfig.getDefaultDueDays();
                    logger.warn("Task {} has no SLA definition, using default dueDays: {}", nodeId, dueDays);
                }

                // Calculate Due_At = Assigned_At + dueDays
                long dueTime = assignedAt.getTime() + (dueDays * 24L * 60 * 60 * 1000);
                Timestamp dueAt = new Timestamp(dueTime);
                task.setDueAt(dueAt);
                task.setDueDate(dueAt); // Keep for backward compatibility

                // Get escalation days (optional)
                Integer escalationDays = BpmnParser.getTaskEscalationDays(element);
                if (escalationDays == null) {
                    escalationDays = WorkflowSlaConfig.getDefaultEscalationDays();
                }

                // Initialize SLA fields
                task.setIsOverdue(false);
                task.setEscalatedAt(null);

                // Note: escalationDays is not stored per task in Phase 4
                // Scheduler will use config default for escalation evaluation

                int taskId = taskDAO.create(task);
                task.setId(taskId);
                createdTasks.add(task);

                // Send task assignment notification
                List<Integer> recipients = notificationService.sendTaskAssignmentNotification(task);
                if (notifiedUsers != null && recipients != null) {
                    notifiedUsers.addAll(recipients);
                }

                logger.info("Created task: {} for node: {} with role: {}, due date: {}, parentGateway: {}",
                        taskId, nodeId, roleName, task.getDueAt(), parentGatewayId != null ? parentGatewayId : "none");
            } else if (BpmnParser.isGateway(element)) {
                // For gateways, recursively find next nodes
                // This handles gateways that come after start event
                // Use provided variables or empty map, and decision for exclusive gateways
                Map<String, String> gatewayVariables = (variables != null) ? variables : new HashMap<>();
                String gatewayDecision = null;
                
                // For exclusive gateways, use the provided decision
                if (BpmnParser.isExclusiveGateway(element)) {
                    gatewayDecision = decision;
                    logger.info("Evaluating exclusive gateway {} with decision: {}", 
                            element.getAttribute("id"), gatewayDecision);
                }
                // For parallel gateways, decision is not needed (all paths execute)
                
                List<String> nextNodes = findNextNodes(doc, element, gatewayVariables, gatewayDecision);
                // If this is a parallel gateway, pass its ID as parentGatewayId
                String gatewayId = BpmnParser.isParallelGateway(element) ? element.getAttribute("id") : null;
                createdTasks.addAll(createTasks(doc, instance, nextNodes, gatewayId, notifiedUsers, 
                        decision, variables));
            }
        }

        return createdTasks;
    }

    /**
     * Find next nodes from current element with decision-aware gateway evaluation
     * Priority-based matching: name → condition → id (for gateways)
     * Single-path enforcement: take first flow for non-gateway nodes
     * 
     * @param doc            BPMN document
     * @param currentElement Current BPMN element
     * @param variables      Workflow variables for condition evaluation
     * @param decision       Decision made (for exclusive gateway routing)
     * @return List of next node IDs
     */
    public List<String> findNextNodes(Document doc, Element currentElement,
            Map<String, String> variables, String decision) {
        List<String> nextNodes = new ArrayList<>();

        // Get outgoing flows
        List<String> outgoingFlows = BpmnParser.getOutgoingFlows(currentElement);

        // If no flows, return empty
        if (outgoingFlows.isEmpty()) {
            return nextNodes;
        }

        // If only one flow, take it
        if (outgoingFlows.size() == 1) {
            String targetId = BpmnParser.getSequenceFlowTarget(doc, outgoingFlows.get(0));
            if (targetId != null) {
                nextNodes.add(targetId);
            }
            return nextNodes;
        }

        // Check if this is a gateway
        boolean isExclusiveGateway = BpmnParser.isExclusiveGateway(currentElement);
        boolean isParallelGateway = BpmnParser.isParallelGateway(currentElement);

        if (isParallelGateway) {
            // Parallel Gateway: Return ALL outgoing target nodes (all paths execute
            // simultaneously)
            if (outgoingFlows.isEmpty()) {
                throw new IllegalStateException(
                        "Parallel gateway " + currentElement.getAttribute("id") + " has no outgoing flows");
            }

            for (String flowId : outgoingFlows) {
                String targetId = BpmnParser.getSequenceFlowTarget(doc, flowId);
                if (targetId != null) {
                    nextNodes.add(targetId);
                }
            }
            logger.info("Parallel gateway detected: {} - creating tasks for all {} outgoing flows",
                    currentElement.getAttribute("id"), outgoingFlows.size());
        } else if (isExclusiveGateway) {
            // Gateway: Must use flow matching (name → condition → id), never default to
            // first flow
            logger.info("🔀 [GATEWAY EVALUATION] Evaluating exclusive gateway: {} with decision: {}", 
                currentElement.getAttribute("id"), decision != null ? decision : "null");
            logger.info("🔀 [GATEWAY EVALUATION] Variables: {}", variables);
            logger.info("🔀 [GATEWAY EVALUATION] Found {} outgoing flow(s)", outgoingFlows.size());
            
            Element matchedFlow = null;
            Element defaultFlow = null; // Flow with no attributes (name/condition/id)

            for (String flowId : outgoingFlows) {
                Element flow = BpmnParser.findElementById(doc, flowId);
                if (flow == null)
                    continue;

                boolean flowMatches = false;

                // Priority 1: SequenceFlow.name matching (case-insensitive)
                String flowName = BpmnParser.getSequenceFlowName(flow);
                if (flowName != null && !flowName.trim().isEmpty() && decision != null) {
                    if (flowName.equalsIgnoreCase(decision)) {
                        flowMatches = true;
                        matchedFlow = flow;
                        break; // Exact match found, use this flow
                    }
                }

                // Priority 2: ConditionExpression evaluation
                if (!flowMatches) {
                    String condition = BpmnParser.getFlowCondition(flow);
                    if (condition != null && !condition.trim().isEmpty()) {
                        flowMatches = BpmnParser.evaluateCondition(condition, variables);
                        if (flowMatches) {
                            matchedFlow = flow;
                            break; // Condition matched, use this flow
                        }
                    }
                }

                // Priority 3: SequenceFlow.id fallback (internal only)
                if (!flowMatches && decision != null) {
                    String flowIdAttr = flow.getAttribute("id");
                    if (flowIdAttr != null && !flowIdAttr.trim().isEmpty()) {
                        if (flowIdAttr.equalsIgnoreCase(decision)) {
                            flowMatches = true;
                            matchedFlow = flow;
                            break; // ID match found, use this flow
                        }
                    }
                }

                // Track default flow: Only if no name, no condition, and no ID exists
                if (!flowMatches) {
                    String condition = BpmnParser.getFlowCondition(flow);
                    String flowIdAttr = flow.getAttribute("id");
                    if (flowName == null && condition == null && (flowIdAttr == null || flowIdAttr.trim().isEmpty())) {
                        // Truly no attributes - this could be a default flow
                        if (defaultFlow == null) {
                            defaultFlow = flow; // First flow with no attributes
                        }
                    }
                }
            }

            // Use matched flow, or default flow only if no decision provided and default
            // exists
            if (matchedFlow != null) {
                String flowId = matchedFlow.getAttribute("id");
                String flowName = BpmnParser.getSequenceFlowName(matchedFlow);
                String targetId = BpmnParser.getSequenceFlowTarget(doc, flowId);
                logger.info("✅ [GATEWAY EVALUATION] Matched flow: {} (name: {}) -> target: {}", 
                    flowId, flowName != null ? flowName : "unnamed", targetId);
                if (targetId != null) {
                    nextNodes.add(targetId);
                }
            } else if (defaultFlow != null) {
                // Default flow exists (no attributes) - use it if no match found
                // This applies whether decision is null or decision didn't match
                String flowId = defaultFlow.getAttribute("id");
                String targetId = BpmnParser.getSequenceFlowTarget(doc, flowId);
                if (targetId != null) {
                    nextNodes.add(targetId);
                    if (decision != null) {
                        logger.info(
                                "Decision '{}' did not match any flow, using default flow (no attributes) for gateway: {}",
                                decision, currentElement.getAttribute("id"));
                    }
                }
            } else if (decision != null) {
                // Decision provided but no flow matched and no default flow - this is an error
                logger.warn("No flow matched decision '{}' for gateway node: {}", decision,
                        currentElement.getAttribute("id"));
                throw new IllegalStateException(
                        "Invalid decision '" + decision + "' for gateway. No matching flow found.");
            } else {
                // No decision and no default flow - this is an error
                logger.warn("No decision provided and no default flow found for gateway node: {}",
                        currentElement.getAttribute("id"));
                throw new IllegalStateException("No decision provided for gateway and no default flow available.");
            }

        } else {
            // Non-gateway node (start event or task):
            // If multiple outgoing flows, check if they lead to parallel execution
            // For start events with multiple flows, treat as implicit parallel gateway
            boolean isStartEvent = "startEvent".equals(currentElement.getLocalName());

            if (outgoingFlows.size() > 1) {
                if (isStartEvent) {
                    // Start event with multiple flows: treat as parallel execution
                    logger.info(
                            "Start event with {} outgoing flows detected: {} - creating tasks for all flows (implicit parallel)",
                            outgoingFlows.size(), currentElement.getAttribute("id"));
                    for (String flowId : outgoingFlows) {
                        String targetId = BpmnParser.getSequenceFlowTarget(doc, flowId);
                        if (targetId != null) {
                            nextNodes.add(targetId);
                        }
                    }
                } else {
                    // Task with multiple flows: take first flow only (legacy behavior)
                    logger.warn("Multiple outgoing flows detected from task node: {}, taking first flow only",
                            currentElement.getAttribute("id"));
                    String firstFlowId = outgoingFlows.get(0);
                    Element flow = BpmnParser.findElementById(doc, firstFlowId);
                    if (flow != null) {
                        String targetId = BpmnParser.getSequenceFlowTarget(doc, firstFlowId);
                        if (targetId != null) {
                            nextNodes.add(targetId);
                        }
                    }
                }
            } else {
                // Single flow: take it
                String firstFlowId = outgoingFlows.get(0);
                Element flow = BpmnParser.findElementById(doc, firstFlowId);
                if (flow != null) {
                    String targetId = BpmnParser.getSequenceFlowTarget(doc, firstFlowId);
                    if (targetId != null) {
                        nextNodes.add(targetId);
                    }
                }
            }
        }

        return nextNodes;
    }

    /**
     * Complete a task and advance workflow
     * Core runtime logic for task completion
     * 
     * @param task      Task to complete
     * @param instance  Workflow instance
     * @param doc       BPMN document
     * @param variables Workflow variables
     * @param decision  Decision made: "approve", "reject", "complete", "rework"
     * @param comment   Optional comment
     * @param userId    User ID who completed the task
     * @return TaskCompletionResult with workflow state and next tasks
     */
    public TaskCompletionResult completeTask(WorkflowTask task, WorkflowInstance instance,
            Document doc, Map<String, String> variables, String decision,
            String comment, int userId) throws SQLException {

        // Mark task as completed
        task.setStatus("Completed");
        task.setCompletedAt(new Timestamp(System.currentTimeMillis()));
        task.setCompletedBy(userId);
        task.setDecision(decision);
        
        logger.info("💾 [TASK COMPLETION] Saving task {} with decision: {}", task.getId(), decision);
        taskDAO.update(task);
        logger.info("✅ [TASK COMPLETION] Task {} saved successfully with decision: {}", task.getId(), decision);

        // Notify creator of completion
        try {
            notificationService.sendStepCompletionNotification(task, decision, userId);
        } catch (Exception e) {
            logger.error("Failed to send step completion notification", e);
        }

        // Persist comment if provided (non-blocking)
        if (comment != null && !comment.trim().isEmpty()) {
            try {
                WorkflowTaskComment taskComment = new WorkflowTaskComment();
                taskComment.setWorkflowTaskId(task.getId());
                taskComment.setCommentText(comment.trim());
                taskComment.setCreatedBy(userId);
                taskComment.setCreatedAt(new Timestamp(System.currentTimeMillis()));
                commentDAO.create(taskComment);
                logger.info("Comment persisted for task: {}", task.getId());

                // Parse mentions and notify
                List<String> mentionedNames = extractMentions(comment.trim());
                if (!mentionedNames.isEmpty()) {
                    notificationService.sendMentionNotification(taskComment, mentionedNames);
                }
            } catch (SQLException e) {
                // Non-blocking: Log error but continue workflow progression
                logger.error("Failed to persist comment for task: {}", task.getId(), e);
            }
        }

        // Find current element
        // Check if this is a Decision task (bpmnNodeId is a gateway ID)
        Element currentElement = BpmnParser.findElementById(doc, task.getBpmnNodeId());
        if (currentElement == null) {
            throw new IllegalStateException("BPMN element not found: " + task.getBpmnNodeId());
        }
        
        // Check if this is a Decision task (bpmnNodeId points to a gateway)
        if (BpmnParser.isExclusiveGateway(currentElement)) {
            // This is a Decision task - the bpmnNodeId points to a gateway
            logger.info("🔀 [TASK COMPLETION] Task {} is a Decision task for gateway: {}", 
                task.getId(), task.getBpmnNodeId());
            
            // For Decision tasks, decision is required (not "complete")
            if (decision == null || "complete".equalsIgnoreCase(decision)) {
                // Get available flow names for better error message
                List<String> gatewayFlows = BpmnParser.getOutgoingFlows(currentElement);
                List<String> availableDecisions = new ArrayList<>();
                for (String gfId : gatewayFlows) {
                    Element gf = BpmnParser.findElementById(doc, gfId);
                    if (gf != null) {
                        String flowName = BpmnParser.getSequenceFlowName(gf);
                        if (flowName != null && !flowName.trim().isEmpty()) {
                            availableDecisions.add(flowName);
                        }
                    }
                }
                String errorMsg = String.format(
                        "Decision task requires a decision but invalid decision '%s' was provided. " +
                        "Please select one of the available decisions: %s",
                        decision != null ? decision : "null",
                        availableDecisions.isEmpty() ? "No decisions available" : String.join(", ", availableDecisions));
                logger.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
        }

        TaskCompletionResult result = new TaskCompletionResult();

        // Check if this task is part of a parallel gateway group
        String taskParentGatewayId = task.getParentGatewayId();
        if (taskParentGatewayId != null && !taskParentGatewayId.trim().isEmpty()) {
            // Task is from a parallel gateway - check if all parallel tasks are completed
            int pendingCount = taskDAO.countPendingTasksByParentGatewayId(instance.getId(), taskParentGatewayId);
            if (pendingCount > 0) {
                // Not all tasks are completed yet - wait for remaining tasks
                logger.info("Task {} completed, but {} tasks still pending for parallel gateway: {}",
                        task.getId(), pendingCount, taskParentGatewayId);

                result.setWorkflowCompleted(false);
                result.setNextTasks(new ArrayList<>());
                result.setParallelGatewayPending(true);
                result.setPendingTaskCount(pendingCount);
                result.setGatewayId(taskParentGatewayId);

                logger.info("Parallel gateway {} not yet complete. Waiting for {} more tasks.",
                        taskParentGatewayId, pendingCount);
                return result;
            } else {
                // All parallel tasks are completed - proceed to next nodes
                logger.info("All tasks completed for parallel gateway: {} - proceeding to next nodes",
                        taskParentGatewayId);
            }
        }

        // Check if this task leads to an exclusive gateway
        // If it does, we'll create a Decision task instead of requiring decision immediately
        List<String> outgoingFlows = BpmnParser.getOutgoingFlows(currentElement);
        Element nextGateway = null;
        String gatewayId = null;
        if (!outgoingFlows.isEmpty()) {
            String firstFlowId = outgoingFlows.get(0);
            String targetId = BpmnParser.getSequenceFlowTarget(doc, firstFlowId);
            if (targetId != null) {
                Element nextElement = BpmnParser.findElementById(doc, targetId);
                if (nextElement != null && BpmnParser.isExclusiveGateway(nextElement)) {
                    nextGateway = nextElement;
                    gatewayId = nextElement.getAttribute("id");
                    logger.info("🔀 [TASK COMPLETION] Task {} leads to exclusive gateway: {} - will create Decision task", 
                        task.getId(), gatewayId);
                    // For tasks leading to gateway, decision should be "complete" (normal completion)
                    // The Decision task will handle the actual decision
                    if (decision != null && !"complete".equalsIgnoreCase(decision)) {
                        logger.warn("⚠️ [TASK COMPLETION] Task {} leads to gateway but received decision '{}' - ignoring, will use 'complete'", 
                            task.getId(), decision);
                        decision = "complete"; // Override to "complete" for normal task completion
                    }
                }
            }
        }

        // Find next nodes (with decision-aware gateway evaluation)
        
        // If task leads to gateway, create a Decision task instead of going directly to gateway
        boolean decisionTaskCreated = false;
        List<String> nextNodeIds;
        if (nextGateway != null && gatewayId != null) {
            // Task leads to gateway - create a Decision task for the gateway
            logger.info("Creating Decision task for gateway {} after completing task {}", 
                gatewayId, task.getId());
            
            // Create a Decision task for this gateway
            WorkflowTask decisionTask = new WorkflowTask();
            decisionTask.setWorkflowInstanceId(instance.getId());
            decisionTask.setBpmnNodeId(gatewayId); // Use gateway ID as node ID for Decision task
            decisionTask.setName("Decision");
            decisionTask.setStatus("Pending");
            
            // Use the same role as the completed task
            String roleName = task.getRoleName();
            if (roleName == null || roleName.isEmpty()) {
                // Fallback: try to get role from BPMN
                roleName = BpmnParser.getRoleForTask(doc, task.getBpmnNodeId());
            }
            decisionTask.setRoleName(roleName);
            
            // Set assigned at timestamp
            Timestamp assignedAt = new Timestamp(System.currentTimeMillis());
            decisionTask.setAssignedAt(assignedAt);
            
            // Calculate due date (use default SLA)
            Integer dueDays = WorkflowSlaConfig.getDefaultDueDays();
            long dueTime = assignedAt.getTime() + (dueDays * 24L * 60 * 60 * 1000);
            Timestamp dueAt = new Timestamp(dueTime);
            decisionTask.setDueAt(dueAt);
            decisionTask.setDueDate(dueAt);
            
            // Initialize SLA fields
            decisionTask.setIsOverdue(false);
            decisionTask.setEscalatedAt(null);
            
            int decisionTaskId = taskDAO.create(decisionTask);
            decisionTask.setId(decisionTaskId);
            
            logger.info("Created Decision task: ID={}, Role={}, Gateway={}", 
                decisionTaskId, roleName, gatewayId);
            
            // Mark that a Decision task was created
            decisionTaskCreated = true;
            
            // Send task assignment notification
            try {
                notificationService.sendTaskAssignmentNotification(decisionTask);
            } catch (Exception e) {
                logger.error("Failed to send notification for Decision task {}", decisionTaskId, e);
            }
            
            // Return empty list - the Decision task will handle the gateway evaluation
            nextNodeIds = new ArrayList<>();
        } else {
            // Normal flow - find next nodes
            nextNodeIds = findNextNodes(doc, currentElement, variables, decision);
        }
        

        // Check if any next node is an end event
        boolean workflowCompleted = false;
        boolean changesAlreadyApplied = false; // Track if changes were already applied by end event commitChanges
        for (String nodeId : nextNodeIds) {
            Element nextElement = BpmnParser.findElementById(doc, nodeId);
            if (nextElement != null) {
                logger.info("Checking next node {}: type={}, isEndEvent={}", 
                    nodeId, nextElement.getLocalName(), BpmnParser.isEndEvent(nextElement));
            }
            if (nextElement != null && BpmnParser.isEndEvent(nextElement)) {
                workflowCompleted = true;
                logger.info("End event detected: {} - marking workflow as completed", nodeId);

                // Check for commit changes property
                String commitChanges = nextElement.getAttribute("commitChanges");
                if (commitChanges == null || commitChanges.isEmpty()) {
                    commitChanges = nextElement.getAttribute("camunda:commitChanges");
                }

                // Also check extension properties if not found in attributes
                if (commitChanges == null || commitChanges.isEmpty()) {
                    Map<String, String> properties = BpmnParser.extractEndEventProperties(nextElement);
                    commitChanges = properties.get("commitChanges");
                }

                if ("true".equalsIgnoreCase(commitChanges) || "1".equals(commitChanges)) {
                    logger.info("End event {} requests to commit changes. Applying pending changes for CR {}", nodeId,
                            instance.getChangeRequestId());
                    try {
                        // Snapshot all pending changes to cr_changes_review BEFORE applying
                        // This ensures all changes are persisted for display after complete
                        try {
                            com.example.budg_v2.PendingChangesServlet pendingChangesServlet = new com.example.budg_v2.PendingChangesServlet();
                            pendingChangesServlet.calculateAndSaveChanges(instance.getChangeRequestId());
                            logger.info("Successfully snapshotted pending changes for CR {}", instance.getChangeRequestId());
                        } catch (Exception snapshotEx) {
                            logger.warn("Failed to snapshot pending changes for CR {}: {}", instance.getChangeRequestId(), snapshotEx.getMessage());
                        }

                        // Apply pending changes from facet_changes table
                        com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
                        java.util.Map<String, java.util.Map<Integer, java.util.Map<String, java.util.List<Integer>>>> pendingMappings = facetChangesDAO
                                .getAllMappingsByChangeRequest(instance.getChangeRequestId());

                        if (!pendingMappings.isEmpty()) {
                            logger.info("Applying {} pending change mappings for CR {}",
                                    pendingMappings.size(), instance.getChangeRequestId());
                            com.example.budg_v2.service.FacetChangesService facetChangesService = new com.example.budg_v2.service.FacetChangesService();
                            facetChangesService.applyChangesForCR(instance.getChangeRequestId());
                            logger.info("Successfully applied pending changes for CR {}",
                                    instance.getChangeRequestId());
                        } else {
                            logger.info("No pending changes to apply for CR {}", instance.getChangeRequestId());
                        }
                        changesAlreadyApplied = true; // Mark that changes were already snapshotted and applied
                    } catch (Exception e) {
                        logger.error("Failed to apply pending changes for CR: {}", instance.getChangeRequestId(), e);
                        // Don't fail workflow completion if applying changes fails
                    }
                }

                break;
            }
        }

        // Determine parentGatewayId for new tasks
        // Check if the current task leads to an exclusive gateway that created multiple tasks
        String newTasksParentGatewayId = null;
        
        // First, check if next node is a parallel gateway
        for (String nodeId : nextNodeIds) {
            Element nextElement = BpmnParser.findElementById(doc, nodeId);
            if (nextElement != null && BpmnParser.isParallelGateway(nextElement)) {
                newTasksParentGatewayId = nextElement.getAttribute("id");
                logger.info("Next node is a parallel gateway: {} - new tasks will be grouped under it",
                        newTasksParentGatewayId);
                break; // Use first parallel gateway found
            }
        }
        

        // Create tasks for next nodes (if not end events)
        // Pass decision and variables for gateway evaluation
        List<WorkflowTask> newTasks = createTasks(doc, instance, nextNodeIds, newTasksParentGatewayId,
                new java.util.HashSet<>(), decision, variables);
        
        if (newTasks.isEmpty() && !nextNodeIds.isEmpty()) {
            logger.warn("No new tasks were created. Next nodes: {}", nextNodeIds);
        }
        
        // If no next nodes found and no new tasks created, and this is not a parallel gateway case,
        // the workflow should be considered complete (task has no outgoing flows = end of workflow)
        // Also check if all next nodes are end events (workflow should be completed)
        // BUT: if a Decision task was just created, don't mark workflow as completed yet
        if (!workflowCompleted) {
            if (nextNodeIds.isEmpty() && newTasks.isEmpty() && !decisionTaskCreated) {
                logger.info("No next nodes found and no new tasks created for task {} - marking workflow as completed", 
                    task.getId());
                workflowCompleted = true;
            } else if (!nextNodeIds.isEmpty() && newTasks.isEmpty()) {
                // Check if all next nodes are end events
                boolean allEndEvents = true;
                for (String nodeId : nextNodeIds) {
                    Element nextElement = BpmnParser.findElementById(doc, nodeId);
                    if (nextElement == null || !BpmnParser.isEndEvent(nextElement)) {
                        allEndEvents = false;
                        break;
                    }
                }
                if (allEndEvents) {
                    logger.info("All next nodes are end events for task {} - marking workflow as completed", 
                        task.getId());
                    workflowCompleted = true;
                }
            }
        }

        // Update instance
        if (workflowCompleted) {
            // Try to set status to 'Completed' if enum supports it, otherwise use
            // 'Disabled'
            try {
                instance.setStatus("Completed");
            } catch (Exception e) {
                // If 'Completed' status not supported, fall back to 'Disabled'
                logger.debug("Status 'Completed' not supported, using 'Disabled'", e);
                instance.setStatus("Disabled");
            }
            instance.setEndedAt(new Timestamp(System.currentTimeMillis()));
            instance.setCurrentBpmnNodeId(null);

            // Send completion notification
            try {
                notificationService.sendCrCompletedNotification(instance);
            } catch (Exception e) {
                logger.error("Failed to send completion notification", e);
            }

            // Auto-complete change request if setting is enabled
            if (instance.getChangeRequestId() != null) {
                try {
                    logger.info("═══════════════════════════════════════════════════════════════");
                    logger.info("🔄 [AUTO-COMPLETE] Checking auto-complete setting for CR {}", instance.getChangeRequestId());
                    logger.info("═══════════════════════════════════════════════════════════════");
                    
                    com.example.budg_v2.dao.SystemSettingsDAO systemSettingsDAO = new com.example.budg_v2.dao.SystemSettingsDAO();
                    com.example.budg_v2.model.SystemSettings autoCompleteSetting = systemSettingsDAO
                            .getSetting("Change Requests", "auto_complete_change_requests");

                    logger.info("📋 [AUTO-COMPLETE] Setting found: {}", autoCompleteSetting != null ? "Yes" : "No");
                    if (autoCompleteSetting != null) {
                        logger.info("📋 [AUTO-COMPLETE] Setting value: {}", autoCompleteSetting.getSettingValue());
                    }

                    if (autoCompleteSetting != null &&
                            ("true".equalsIgnoreCase(autoCompleteSetting.getSettingValue()) ||
                                    Boolean.parseBoolean(autoCompleteSetting.getSettingValue()))) {

                        logger.info("✅ [AUTO-COMPLETE] Auto-complete is ENABLED - starting auto-completion for CR {}",
                                instance.getChangeRequestId());

                        // Get the change request DAO
                        com.example.budg_v2.dao.ChangeRequestDAO changeRequestDAO = new com.example.budg_v2.dao.ChangeRequestDAO();
                        com.example.budg_v2.model.ChangeRequest cr = changeRequestDAO
                                .getChangeRequestById(instance.getChangeRequestId());

                        if (cr != null) {
                            // Skip snapshot and apply if already done by end event commitChanges path
                            if (changesAlreadyApplied) {
                                logger.info("ℹ️ [AUTO-COMPLETE] Changes already snapshotted and applied by end event commitChanges for CR {} - skipping duplicate snapshot/apply",
                                        instance.getChangeRequestId());
                            } else {
                                // Check for pending changes and apply them (same as Complete button)
                                try {
                                    // Snapshot all pending changes to cr_changes_review BEFORE applying
                                    try {
                                        com.example.budg_v2.PendingChangesServlet pendingChangesServlet = new com.example.budg_v2.PendingChangesServlet();
                                        pendingChangesServlet.calculateAndSaveChanges(instance.getChangeRequestId());
                                        logger.info("Successfully snapshotted pending changes for auto-completed CR {}", instance.getChangeRequestId());
                                    } catch (Exception snapshotEx) {
                                        logger.warn("Failed to snapshot pending changes for auto-completed CR {}: {}", instance.getChangeRequestId(), snapshotEx.getMessage());
                                    }

                                    com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
                                    java.util.Map<String, java.util.Map<Integer, java.util.Map<String, java.util.List<Integer>>>> pendingMappings = facetChangesDAO
                                            .getAllMappingsByChangeRequest(instance.getChangeRequestId());
                                    boolean hasPendingChanges = !pendingMappings.isEmpty();

                                    if (hasPendingChanges) {
                                        logger.info("Applying pending changes for auto-completed CR {}",
                                                instance.getChangeRequestId());
                                        com.example.budg_v2.service.FacetChangesService facetChangesService = new com.example.budg_v2.service.FacetChangesService();
                                        facetChangesService.applyChangesForCR(instance.getChangeRequestId());
                                        logger.info("Successfully applied pending changes for CR {}",
                                                instance.getChangeRequestId());
                                    } else {
                                        logger.info("No pending changes to apply for CR {}", instance.getChangeRequestId());
                                    }
                                } catch (Exception e) {
                                    logger.error("Error applying pending changes for auto-completed CR {}: {}",
                                            instance.getChangeRequestId(), e.getMessage(), e);
                                    // Continue with completion even if applying changes fails
                                }
                            }

                            // Update CR status to Completed
                            logger.info("🔄 [AUTO-COMPLETE] Updating CR {} status to Completed", instance.getChangeRequestId());
                            Integer completedStatusId = changeRequestDAO.getStatusIdByName("Completed");
                            if (completedStatusId != null) {
                                cr.setCrStatusId(completedStatusId);
                                // Use the userId from the method parameter (the user who completed the workflow
                                // task)
                                cr.setLastUserChange(userId);
                                cr.setUpdatedAt(java.time.LocalDateTime.now());
                                boolean updated = changeRequestDAO.updateChangeRequest(cr);
                                if (updated) {
                                    logger.info("✅ [AUTO-COMPLETE] Successfully auto-completed change request {} (status ID: {})",
                                            instance.getChangeRequestId(), completedStatusId);
                                } else {
                                    logger.error("❌ [AUTO-COMPLETE] Failed to update CR {} status - update returned false",
                                            instance.getChangeRequestId());
                                }
                            } else {
                                logger.error("❌ [AUTO-COMPLETE] Completed status not found in database for CR {}",
                                        instance.getChangeRequestId());
                            }
                        } else {
                            logger.warn("⚠️ [AUTO-COMPLETE] Change request {} not found in database",
                                    instance.getChangeRequestId());
                        }
                    } else {
                        logger.info("ℹ️ [AUTO-COMPLETE] Auto-complete is DISABLED - CR {} will not be auto-completed",
                                instance.getChangeRequestId());
                        
                        // Even if auto-complete is disabled, update CR status to "Completed" when workflow completes
                        // (but don't apply pending changes - user must click Complete button for that)
                        try {
                            logger.info("🔄 [WORKFLOW COMPLETED] Updating CR {} status to Completed (auto-complete disabled, pending changes will be applied manually)",
                                    instance.getChangeRequestId());
                            
                            com.example.budg_v2.dao.ChangeRequestDAO changeRequestDAO = new com.example.budg_v2.dao.ChangeRequestDAO();
                            com.example.budg_v2.model.ChangeRequest cr = changeRequestDAO
                                    .getChangeRequestById(instance.getChangeRequestId());
                            
                            if (cr != null) {
                                Integer completedStatusId = changeRequestDAO.getStatusIdByName("Completed");
                                if (completedStatusId != null) {
                                    cr.setCrStatusId(completedStatusId);
                                    cr.setLastUserChange(userId);
                                    cr.setUpdatedAt(java.time.LocalDateTime.now());
                                    boolean updated = changeRequestDAO.updateChangeRequest(cr);
                                    if (updated) {
                                        logger.info("✅ [WORKFLOW COMPLETED] Successfully updated CR {} status to Completed (status ID: {}) - pending changes will be applied when user clicks Complete button",
                                                instance.getChangeRequestId(), completedStatusId);
                                    } else {
                                        logger.error("❌ [WORKFLOW COMPLETED] Failed to update CR {} status - update returned false",
                                                instance.getChangeRequestId());
                                    }
                                } else {
                                    logger.error("❌ [WORKFLOW COMPLETED] Completed status not found in database for CR {}",
                                            instance.getChangeRequestId());
                                }
                            } else {
                                logger.warn("⚠️ [WORKFLOW COMPLETED] Change request {} not found in database",
                                        instance.getChangeRequestId());
                            }
                        } catch (Exception e) {
                            logger.error("❌ [WORKFLOW COMPLETED] Error updating CR {} status: {}",
                                    instance.getChangeRequestId(), e.getMessage(), e);
                            // Don't fail workflow completion if status update fails
                        }
                    }
                } catch (Exception e) {
                    logger.error("❌ [AUTO-COMPLETE] Error auto-completing change request {}: {}",
                            instance.getChangeRequestId(), e.getMessage(), e);
                    logger.error("❌ [AUTO-COMPLETE] Stack trace:", e);
                    // Don't fail workflow completion if auto-complete fails
                }
            } else {
                logger.warn("⚠️ [AUTO-COMPLETE] Workflow instance {} has no change request ID - skipping auto-complete",
                        instance.getId());
            }
        } else if (!newTasks.isEmpty()) {
            instance.setCurrentBpmnNodeId(newTasks.get(0).getBpmnNodeId());
        }
        instanceDAO.update(instance);

        // Set result
        result.setWorkflowCompleted(workflowCompleted);
        result.setNextTasks(newTasks);

        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("📊 Workflow completed: {}", workflowCompleted);
        logger.info("📊 Next tasks created: {}", newTasks.size());
        logger.info("═══════════════════════════════════════════════════════════════");
        return result;
    }

    /**
     * Add a comment to a task and notify mentioned users
     */
    public void addComment(WorkflowTask task, String comment, int userId) throws SQLException {
        WorkflowTaskComment taskComment = new WorkflowTaskComment();
        taskComment.setWorkflowTaskId(task.getId());
        taskComment.setCommentText(comment.trim());
        taskComment.setCreatedBy(userId);
        taskComment.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        commentDAO.create(taskComment);
        logger.info("Comment persisted for task: {}", task.getId());

        // Parse mentions and notify
        List<String> mentionedNames = extractMentions(comment.trim());
        if (!mentionedNames.isEmpty()) {
            notificationService.sendMentionNotification(taskComment, mentionedNames);
        }
    }

    // Helper to extract keys after @
    private List<String> extractMentions(String text) {
        List<String> mentions = new ArrayList<>();
        if (text == null)
            return mentions;

        // Regex to match @Name Name or @Name
        // Matches @ followed by word chars, optional space, and more word chars
        // Stops at non-word chars (like punctuation)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("@([\\w]+(?: [\\w]+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String name = matcher.group(1);
            if (!mentions.contains(name)) {
                mentions.add(name);
            }
        }
        return mentions;
    }
}
