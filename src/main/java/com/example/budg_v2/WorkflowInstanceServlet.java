package com.example.budg_v2;

import com.example.budg_v2.model.WorkflowInstance;
import com.example.budg_v2.model.WorkflowTask;
import com.example.budg_v2.model.WorkflowTaskComment;
import com.example.budg_v2.model.ChangeRequest;
import com.example.budg_v2.util.BpmnParser;
import com.example.budg_v2.util.WorkflowEngine;
import com.example.budg_v2.dao.WorkflowInstanceDAO;
import com.example.budg_v2.dao.WorkflowTaskDAO;
import com.example.budg_v2.dao.WorkflowTaskCommentDAO;
import com.example.budg_v2.dao.BpmnContentDAO;
import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.dao.CRStakeholderDAO;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.UserContextUtil;
import com.example.budg_v2.util.BpmnFileManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Servlet for workflow instance operations
 * Endpoints:
 * POST /api/workflow_instances - Start new workflow
 * GET /api/workflow_instances/by-cr/{crId} - Get instance for CR
 */
@WebServlet("/api/workflow_instances/*")
public class WorkflowInstanceServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowInstanceServlet.class);
    private final Gson gson = new Gson();
    private final WorkflowEngine engine = new WorkflowEngine();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.startsWith("/by-cr/")) {
                // Get workflow status for change request
                String crIdStr = pathInfo.substring(7);
                int crId = Integer.parseInt(crIdStr);

                Map<String, Object> status = engine.getWorkflowStatus(crId);

                if (status == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Workflow instance not found for this change request");
                    response.getWriter().write(gson.toJson(error));
                } else {
                    response.getWriter().write(gson.toJson(status));
                }

            } else if (pathInfo != null && pathInfo.matches("/\\d+/active-task")) {
                // GET /api/workflow_instances/{instanceId}/active-task
                String instanceIdStr = pathInfo.substring(1, pathInfo.indexOf("/active-task"));
                int instanceId = Integer.parseInt(instanceIdStr);

                WorkflowTask activeTask = engine.getActiveTask(instanceId);
                
                if (activeTask == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "No active task found for this workflow instance");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }

                // Get workflow instance to load BPMN for task description
                WorkflowInstance instance = new WorkflowInstanceDAO().findById(instanceId);
                if (instance == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Workflow instance not found");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }

                // Load BPMN to get task description and check for gateway
                String bpmnXml = new BpmnContentDAO().getXmlContent(instance.getProcessDefinitionId());
                if (bpmnXml == null) {
                    bpmnXml = BpmnFileManager.loadBpmnFile(instance.getProcessDefinitionId());
                }
                
                String taskDescription = "";
                List<String> decisionOptions = new ArrayList<>();
                List<Map<String, String>> decisionOptionsList = new ArrayList<>();
                boolean isGatewayTask = false;
                
                if (bpmnXml != null) {
                    try {
                        Document doc = BpmnParser.parseXml(bpmnXml);
                        Element taskElement = BpmnParser.findElementById(doc, activeTask.getBpmnNodeId());
                        if (taskElement != null) {
                            // Check if this is a Decision task (bpmnNodeId points to a gateway)
                            if (BpmnParser.isExclusiveGateway(taskElement)) {
                                // This is a Decision task - the bpmnNodeId is a gateway ID
                                isGatewayTask = true;
                                taskDescription = "Select the decision path for this workflow.";
                                
                                // Get decision options from gateway flows
                                List<String> gatewayFlows = BpmnParser.getOutgoingFlows(taskElement);
                                
                                for (String gfId : gatewayFlows) {
                                    Element gf = BpmnParser.findElementById(doc, gfId);
                                    if (gf != null) {
                                        // Priority 1: Extract flow name
                                        String flowName = BpmnParser.getSequenceFlowName(gf);
                                        if (flowName != null && !flowName.trim().isEmpty()) {
                                            Map<String, String> option = new java.util.HashMap<>();
                                            option.put("value", flowName);
                                            option.put("label", flowName);
                                            decisionOptionsList.add(option);
                                            decisionOptions.add(flowName); // Keep for backward compatibility
                                        } else {
                                            // Priority 2: Extract from condition expression
                                            String condition = BpmnParser.getFlowCondition(gf);
                                            if (condition != null && !condition.trim().isEmpty()) {
                                                // Try to extract value from condition like ${last_decision == 'Approved'}
                                                String extractedValue = extractValueFromCondition(condition);
                                                if (extractedValue != null && !extractedValue.isEmpty()) {
                                                    Map<String, String> option = new java.util.HashMap<>();
                                                    option.put("value", extractedValue);
                                                    option.put("label", extractedValue);
                                                    decisionOptionsList.add(option);
                                                    decisionOptions.add(extractedValue); // Keep for backward compatibility
                                                }
                                            }
                                            // Priority 3: Skip if only ID exists (not shown to users)
                                        }
                                    }
                                }
                                
                                // If no decision options found, add default
                                if (decisionOptionsList.isEmpty()) {
                                    Map<String, String> defaultOption = new java.util.HashMap<>();
                                    defaultOption.put("value", "complete");
                                    defaultOption.put("label", "Complete");
                                    decisionOptionsList.add(defaultOption);
                                    decisionOptions.add("complete"); // Keep for backward compatibility
                                }
                            } else {
                                // Normal task - get task description
                                taskDescription = taskElement.getAttribute("name");
                                if (taskDescription == null || taskDescription.isEmpty()) {
                                    taskDescription = activeTask.getName();
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing BPMN for task description", e);
                    }
                }
                
                if (taskDescription.isEmpty()) {
                    taskDescription = activeTask.getName();
                }
                
                if (decisionOptions.isEmpty()) {
                    decisionOptions.add("complete");
                }

                // Build response
                JsonObject result = new JsonObject();
                result.addProperty("taskId", activeTask.getId());
                result.addProperty("taskName", activeTask.getName());
                result.addProperty("taskDescription", taskDescription);
                result.addProperty("role", activeTask.getRoleName() != null ? activeTask.getRoleName() : "");
                result.addProperty("bpmnNodeId", activeTask.getBpmnNodeId());
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                if (activeTask.getAssignedAt() != null) {
                    result.addProperty("assignedAt", sdf.format(activeTask.getAssignedAt()));
                }
                if (activeTask.getDueDate() != null) {
                    result.addProperty("dueDate", sdf.format(activeTask.getDueDate()));
                }
                // SLA fields
                if (activeTask.getDueAt() != null) {
                    result.addProperty("dueAt", sdf.format(activeTask.getDueAt()));
                }
                result.addProperty("isOverdue", activeTask.getIsOverdue() != null && activeTask.getIsOverdue());
                if (activeTask.getEscalatedAt() != null) {
                    result.addProperty("escalatedAt", sdf.format(activeTask.getEscalatedAt()));
                }
                
                result.addProperty("isGatewayTask", isGatewayTask);
                
                // Add decision options (structured format if gateway task, otherwise simple list)
                if (isGatewayTask && !decisionOptionsList.isEmpty()) {
                    result.add("decisionOptions", gson.toJsonTree(decisionOptionsList));
                } else {
                    result.add("decisionOptions", gson.toJsonTree(decisionOptions));
                }

                response.getWriter().write(gson.toJson(result));

            } else if (pathInfo != null && pathInfo.matches("/\\d+/tasks")) {
                // GET /api/workflow_instances/{instanceId}/tasks
                String instanceIdStr = pathInfo.substring(1, pathInfo.indexOf("/tasks"));
                int instanceId = Integer.parseInt(instanceIdStr);

                WorkflowInstance instance = new WorkflowInstanceDAO().findById(instanceId);
                if (instance == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Workflow instance not found");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }

                // Get all tasks for this instance
                List<WorkflowTask> tasks = new WorkflowTaskDAO().findByInstanceId(instanceId);
                
                // Get BPMN XML for this instance to extract gateway/decision info
                String bpmnXml = null;
                Document bpmnDoc = null;
                try {
                    BpmnContentDAO bpmnDAO = new BpmnContentDAO();
                    bpmnXml = bpmnDAO.getXmlContent(instance.getProcessDefinitionId());
                    if (bpmnXml == null) {
                        bpmnXml = BpmnFileManager.loadBpmnFile(instance.getProcessDefinitionId());
                    }
                    if (bpmnXml != null) {
                        bpmnDoc = BpmnParser.parseXml(bpmnXml);
                    }
                } catch (Exception e) {
                    logger.warn("Could not load BPMN XML for instance {}: {}", instanceId, e.getMessage());
                }
                
                // Get all comments for this instance
                List<WorkflowTaskComment> allComments = new WorkflowTaskCommentDAO().findByInstanceId(instanceId);
                
                // Group comments by task ID
                Map<Integer, List<WorkflowTaskComment>> commentsByTask = new java.util.HashMap<>();
                for (WorkflowTaskComment comment : allComments) {
                    commentsByTask.computeIfAbsent(comment.getWorkflowTaskId(), k -> new ArrayList<>()).add(comment);
                }

                // Build response with tasks and their comments
                List<Map<String, Object>> taskList = new ArrayList<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                
                for (WorkflowTask task : tasks) {
                    Map<String, Object> taskMap = new java.util.HashMap<>();
                    taskMap.put("taskId", task.getId());
                    taskMap.put("taskName", task.getName());
                    taskMap.put("status", task.getStatus());
                    String decision = task.getDecision();
                    taskMap.put("decision", decision);
                    if (decision != null) {
                        logger.debug("Task {} has decision: {}", task.getId(), decision);
                    }
                    taskMap.put("role", task.getRoleName());
                    taskMap.put("bpmnNodeId", task.getBpmnNodeId());
                    taskMap.put("nodeId", task.getBpmnNodeId()); // Alias for compatibility
                    
                    if (task.getCompletedAt() != null) {
                        taskMap.put("completedAt", sdf.format(task.getCompletedAt()));
                    }
                    if (task.getCompletedBy() != null) {
                        taskMap.put("completedBy", task.getCompletedBy());
                        // TODO: Load user name if needed
                        taskMap.put("completedByName", "User " + task.getCompletedBy());
                    }
                    
                    // Assignment date
                    if (task.getAssignedAt() != null) {
                        taskMap.put("assignedAt", sdf.format(task.getAssignedAt()));
                    }
                    
                    // SLA fields
                    if (task.getDueAt() != null) {
                        taskMap.put("dueAt", sdf.format(task.getDueAt()));
                    }
                    taskMap.put("isOverdue", task.getIsOverdue() != null && task.getIsOverdue());
                    if (task.getEscalatedAt() != null) {
                        taskMap.put("escalatedAt", sdf.format(task.getEscalatedAt()));
                    }
                    
                    // Add comments for this task
                    List<Map<String, Object>> commentList = new ArrayList<>();
                    List<WorkflowTaskComment> taskComments = commentsByTask.getOrDefault(task.getId(), new ArrayList<>());
                    for (WorkflowTaskComment comment : taskComments) {
                        Map<String, Object> commentMap = new java.util.HashMap<>();
                        commentMap.put("text", comment.getCommentText());
                        commentMap.put("createdBy", comment.getCreatedBy());
                        if (comment.getCreatedAt() != null) {
                            commentMap.put("createdAt", sdf.format(comment.getCreatedAt()));
                        }
                        commentList.add(commentMap);
                    }
                    taskMap.put("comments", commentList);
                    
                    // Add gateway/decision task information from BPMN
                    if (bpmnDoc != null && task.getBpmnNodeId() != null) {
                        try {
                            Element taskElement = BpmnParser.findElementById(bpmnDoc, task.getBpmnNodeId());
                            if (taskElement != null) {
                                // Check if this is a gateway task
                                boolean isGatewayTask = BpmnParser.isExclusiveGateway(taskElement);
                                taskMap.put("isGatewayTask", isGatewayTask);
                                
                                if (isGatewayTask) {
                                    // Get decision options from gateway flows
                                    List<String> gatewayFlows = BpmnParser.getOutgoingFlows(taskElement);
                                    List<Map<String, String>> decisionOptionsList = new ArrayList<>();
                                    
                                    for (String flowId : gatewayFlows) {
                                        Element flowElement = BpmnParser.findElementById(bpmnDoc, flowId);
                                        if (flowElement != null) {
                                            // Priority 1: Extract flow name
                                            String flowName = BpmnParser.getSequenceFlowName(flowElement);
                                            if (flowName != null && !flowName.trim().isEmpty()) {
                                                Map<String, String> option = new java.util.HashMap<>();
                                                option.put("value", flowName.trim());
                                                option.put("label", flowName.trim());
                                                decisionOptionsList.add(option);
                                            } else {
                                                // Priority 2: Extract from condition expression
                                                String condition = BpmnParser.getFlowCondition(flowElement);
                                                if (condition != null && !condition.trim().isEmpty()) {
                                                    // Try to extract value from condition like ${last_decision == 'Approved'}
                                                    String extractedValue = extractValueFromCondition(condition);
                                                    if (extractedValue != null && !extractedValue.isEmpty()) {
                                                        Map<String, String> option = new java.util.HashMap<>();
                                                        option.put("value", extractedValue);
                                                        option.put("label", extractedValue);
                                                        decisionOptionsList.add(option);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (!decisionOptionsList.isEmpty()) {
                                        taskMap.put("decisionOptions", decisionOptionsList);
                                    } else {
                                        // Fallback to default decision options
                                        List<String> defaultOptions = new ArrayList<>();
                                        defaultOptions.add("Approved");
                                        defaultOptions.add("Rejected");
                                        defaultOptions.add("Rework");
                                        taskMap.put("decisionOptions", defaultOptions);
                                    }
                                } else {
                                    // Simple task - default to 'complete'
                                    taskMap.put("decisionOptions", new String[]{"complete"});
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("Error extracting gateway info for task {}: {}", task.getId(), e.getMessage());
                            // Fallback: assume simple task
                            taskMap.put("isGatewayTask", false);
                            taskMap.put("decisionOptions", new String[]{"complete"});
                        }
                    } else {
                        // No BPMN available - default to simple task
                        taskMap.put("isGatewayTask", false);
                        taskMap.put("decisionOptions", new String[]{"complete"});
                    }
                    
                    taskList.add(taskMap);
                }

                Map<String, Object> result = new java.util.HashMap<>();
                result.put("tasks", taskList);
                
                response.getWriter().write(gson.toJson(result));

            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid endpoint");
                response.getWriter().write(gson.toJson(error));
            }

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid ID format");
            response.getWriter().write(gson.toJson(error));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        //system.out.println("WorkflowInstanceServlet: Received POST request");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            JsonObject requestBody = gson.fromJson(request.getReader(), JsonObject.class);
            //system.out.println("WorkflowInstanceServlet: Request body: " + requestBody);

            int processDefinitionId = requestBody.get("processDefinitionId").getAsInt();
            Integer changeRequestId = null;
            if (requestBody.has("changeRequestId") && !requestBody.get("changeRequestId").isJsonNull()) {
                changeRequestId = requestBody.get("changeRequestId").getAsInt();
            }

            // Get user ID from request (set by AuthFilter) or session
            int userId = 0;
            if (request.getAttribute("userId") != null) {
                userId = (Integer) request.getAttribute("userId");
            } else {
                HttpSession session = request.getSession(false);
                if (session != null && session.getAttribute("userId") != null) {
                    userId = (Integer) session.getAttribute("userId");
                }
            }
            
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject error = new JsonObject();
                error.addProperty("error", "User not authenticated");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Check permission to start workflow if changeRequestId is provided
            if (changeRequestId != null) {
                try {
                    ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
                    ChangeRequest changeRequest = changeRequestDAO.getChangeRequestById(changeRequestId);
                    
                    if (changeRequest == null) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Change request not found");
                        response.getWriter().write(gson.toJson(error));
                        return;
                    }
                    
                    // Check start permission: 
                    // Allowed: super admin, admin, requester (creator), OR stakeholder on this CR
                    boolean canStart = false;
                    String reason = "";
                    
                    // 1. Check if user is super admin or admin
                    boolean isSuperAdmin = false;
                    boolean isAdmin = false;
                    try {
                        isSuperAdmin = SegmentAccessService.isSuperAdmin(userId);
                        if (!isSuperAdmin) {
                            isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                        }
                    } catch (SQLException e) {
                        logger.warn("Error checking admin status: {}", e.getMessage());
                    }
                    
                    if (isSuperAdmin || isAdmin) {
                        canStart = true;
                        reason = isSuperAdmin ? "super admin" : "admin";
                    } else {
                        // 2. Check if user is the requester (creator)
                        Integer createdBy = changeRequest.getCreatedBy();
                        boolean isCreator = (createdBy != null && createdBy.equals(userId));
                        
                        if (isCreator) {
                            canStart = true;
                            reason = "requester (creator)";
                        } else {
                            // 3. Check if user is a stakeholder on this CR AND has a role that matches workflow roles
                            String reference = changeRequest.getReference();
                            if (reference != null && !reference.trim().isEmpty()) {
                                try {
                                    CRStakeholderDAO stakeholderDAO = new CRStakeholderDAO();
                                    List<Map<String, Object>> stakeholders = stakeholderDAO.getStakeholdersFromSourceObject(reference);
                                    
                                    // Extract workflow roles from BPMN XML
                                    Set<String> workflowRoles = extractWorkflowRoles(processDefinitionId);
                                    
                                    if (workflowRoles.isEmpty()) {
                                        logger.warn("No roles found in workflow {} - allowing stakeholder to start (workflow has no role requirements)", processDefinitionId);
                                        // If workflow has no roles, allow any stakeholder to start
                                        for (Map<String, Object> stakeholder : stakeholders) {
                                            Integer stakeholderUserId = null;
                                            if (stakeholder.containsKey("personId")) {
                                                Object userIdObj = stakeholder.get("personId");
                                                if (userIdObj instanceof Number) {
                                                    stakeholderUserId = ((Number) userIdObj).intValue();
                                                }
                                            } else if (stakeholder.containsKey("userId")) {
                                                Object userIdObj = stakeholder.get("userId");
                                                if (userIdObj instanceof Number) {
                                                    stakeholderUserId = ((Number) userIdObj).intValue();
                                                }
                                            }
                                            
                                            if (stakeholderUserId != null && stakeholderUserId.equals(userId)) {
                                                canStart = true;
                                                reason = "stakeholder on change request (workflow has no role requirements)";
                                                break;
                                            }
                                        }
                                    } else {
                                        // Check if user is a stakeholder with a role that matches workflow roles
                                        for (Map<String, Object> stakeholder : stakeholders) {
                                            Integer stakeholderUserId = null;
                                            if (stakeholder.containsKey("personId")) {
                                                Object userIdObj = stakeholder.get("personId");
                                                if (userIdObj instanceof Number) {
                                                    stakeholderUserId = ((Number) userIdObj).intValue();
                                                }
                                            } else if (stakeholder.containsKey("userId")) {
                                                Object userIdObj = stakeholder.get("userId");
                                                if (userIdObj instanceof Number) {
                                                    stakeholderUserId = ((Number) userIdObj).intValue();
                                                }
                                            }
                                            
                                            if (stakeholderUserId != null && stakeholderUserId.equals(userId)) {
                                                // User is a stakeholder - check if their role matches any workflow role
                                                String stakeholderRoleName = (String) stakeholder.get("roleName");
                                                if (stakeholderRoleName != null && !stakeholderRoleName.trim().isEmpty()) {
                                                    String normalizedStakeholderRole = normalizeRoleName(stakeholderRoleName);
                                                    
                                                    // Check if stakeholder role matches any workflow role
                                                    for (String workflowRole : workflowRoles) {
                                                        String normalizedWorkflowRole = normalizeRoleName(workflowRole);
                                                        if (normalizedStakeholderRole.equals(normalizedWorkflowRole)) {
                                                            canStart = true;
                                                            reason = "stakeholder with matching workflow role: " + normalizedStakeholderRole;
                                                            logger.info("User {} authorized to start workflow - stakeholder role '{}' matches workflow role '{}'", 
                                                                userId, normalizedStakeholderRole, normalizedWorkflowRole);
                                                            break;
                                                        }
                                                    }
                                                    
                                                    if (canStart) {
                                                        break;
                                                    } else {
                                                        logger.warn("User {} is a stakeholder but role '{}' (normalized: '{}') does not match any workflow roles: {}", 
                                                            userId, stakeholderRoleName, normalizedStakeholderRole, workflowRoles);
                                                    }
                                                } else {
                                                    logger.warn("User {} is a stakeholder but has no role name", userId);
                                                }
                                            }
                                        }
                                    }
                                } catch (SQLException e) {
                                    logger.warn("Error checking stakeholder status: {}", e.getMessage());
                                } catch (Exception e) {
                                    logger.error("Error extracting workflow roles or checking stakeholder: {}", e.getMessage(), e);
                                }
                            }
                        }
                    }
                    
                    if (!canStart) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Access denied. Only super admin, admin, requester, or stakeholder can start workflow for this change request.");
                        response.getWriter().write(gson.toJson(error));
                        return;
                    }
                    
                    logger.info("User {} authorized to start workflow for CR {} (reason: {})", userId, changeRequestId, reason);
                } catch (SQLException e) {
                    logger.error("Database error checking permissions", e);
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Database error: " + e.getMessage());
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            // Start workflow
            //system.out.println("WorkflowInstanceServlet: Calling engine.startWorkflow...");
            int instanceId = engine.startWorkflow(processDefinitionId, changeRequestId, userId);
            //system.out.println("WorkflowInstanceServlet: Workflow started. Payload: " + instanceId);

            JsonObject result = new JsonObject();
            result.addProperty("workflowInstanceId", instanceId);
            result.addProperty("success", true);

            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(gson.toJson(result));

            logger.info("Started workflow instance: {} for process: {}", instanceId, processDefinitionId);

        } catch (Exception e) {
            logger.error("Error starting workflow", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error starting workflow: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Extract value from condition expression
     * Example: ${last_decision == 'Approved'} → "Approved"
     */
    private String extractValueFromCondition(String condition) {
        if (condition == null || condition.trim().isEmpty()) {
            return null;
        }
        
        // Remove ${ and }
        String cleaned = condition.trim();
        if (cleaned.startsWith("${") && cleaned.endsWith("}")) {
            cleaned = cleaned.substring(2, cleaned.length() - 1).trim();
        }
        
        // Parse equality: variable == 'value'
        if (cleaned.contains("==")) {
            String[] parts = cleaned.split("==");
            if (parts.length == 2) {
                String value = parts[1].trim().replace("'", "").replace("\"", "");
                return value;
            }
        }
        
        return null;
    }
    
    /**
     * Extract required roles from workflow BPMN XML
     * Roles are extracted from:
     * 1. User task properties (camunda:candidateGroups / role)
     * 2. BPMN Lanes (if task is inside a lane, the lane name is the role)
     */
    private Set<String> extractWorkflowRoles(int processDefinitionId) throws Exception {
        Set<String> roles = new HashSet<>();
        
        // Get BPMN XML content
        BpmnContentDAO bpmnDAO = new BpmnContentDAO();
        String bpmnXml = bpmnDAO.getXmlContent(processDefinitionId);
        
        // If not in DB, try file system
        if (bpmnXml == null) {
            bpmnXml = BpmnFileManager.loadBpmnFile(processDefinitionId);
        }
        
        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            logger.warn("BPMN XML not found for process definition {}", processDefinitionId);
            return roles;
        }
        
        // Parse BPMN XML
        Document doc = BpmnParser.parseXml(bpmnXml);
        
        // Map task IDs to Lane Roles
        Map<String, String> taskLaneRoles = new java.util.HashMap<>();
        List<Element> lanes = BpmnParser.findLanes(doc);
        logger.debug("Found {} lanes in BPMN for process definition {}", lanes.size(), processDefinitionId);
        
        for (Element lane : lanes) {
            String laneRole = BpmnParser.getLaneName(lane);
            if (laneRole != null && !laneRole.trim().isEmpty()) {
                List<String> nodeIds = BpmnParser.getFlowNodesInLane(lane);
                logger.debug("Lane '{}' contains nodes: {}", laneRole, nodeIds);
                for (String nodeId : nodeIds) {
                    taskLaneRoles.put(nodeId, laneRole.trim());
                }
            }
        }
        
        // Find all user tasks
        List<Element> userTasks = BpmnParser.findUserTasks(doc);
        logger.debug("Found {} user tasks in BPMN for process definition {}", userTasks.size(), processDefinitionId);
        
        for (Element userTask : userTasks) {
            String taskId = userTask.getAttribute("id");
            // Extract task properties
            Map<String, String> properties = BpmnParser.extractTaskProperties(userTask);
            String taskRole = properties.get("role"); // from camunda:candidateGroups
            
            if (taskRole != null && !taskRole.trim().isEmpty()) {
                // Priority 1: Explicit role on task
                String[] roleNames = taskRole.split(",");
                for (String roleName : roleNames) {
                    String trimmedRole = roleName.trim();
                    if (!trimmedRole.isEmpty()) {
                        roles.add(trimmedRole);
                    }
                }
            } else {
                // Priority 2: Role from Lane
                String laneRole = taskLaneRoles.get(taskId);
                logger.debug("Checking lane role for task {}: {}", taskId, laneRole);
                
                if (laneRole != null && !laneRole.isEmpty()) {
                    roles.add(laneRole);
                }
            }
        }
        
        logger.info("Extracted {} unique roles from workflow {}: {}", roles.size(), processDefinitionId, roles);
        return roles;
    }
    
    /**
     * Normalize role name: trim → remove ID prefix if present → uppercase → replace spaces with underscores
     * Examples: 
     *   "3:DATASET_OWNER" → "DATASET_OWNER"
     *   "Dataset Owner" → "DATASET_OWNER"
     *   "DATASET_OWNER" → "DATASET_OWNER"
     * @param roleName Original role name
     * @return Normalized role name
     */
    private String normalizeRoleName(String roleName) {
        if (roleName == null) {
            return null;
        }
        
        // Remove ID prefix if present (e.g., "3:DATASET_OWNER" -> "DATASET_OWNER")
        String cleanRole = roleName.trim();
        if (cleanRole.contains(":")) {
            int colonIndex = cleanRole.indexOf(':');
            cleanRole = cleanRole.substring(colonIndex + 1).trim();
        }
        
        return cleanRole.toUpperCase().replaceAll("\\s+", "_");
    }
}
