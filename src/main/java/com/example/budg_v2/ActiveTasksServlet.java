package com.example.budg_v2;

import com.example.budg_v2.dao.BpmnContentDAO;
import com.example.budg_v2.dao.WorkflowInstanceDAO;
import com.example.budg_v2.dao.WorkflowTaskDAO;
import com.example.budg_v2.model.WorkflowInstance;
import com.example.budg_v2.util.BpmnParser;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servlet for Active Tasks operations
 * Endpoints:
 * GET /api/active-tasks - Get all active tasks for current user
 */
@WebServlet("/api/active-tasks")
public class ActiveTasksServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ActiveTasksServlet.class);
    private final Gson gson = new Gson();
    private final WorkflowTaskDAO taskDAO = new WorkflowTaskDAO();
    private final WorkflowInstanceDAO instanceDAO = new WorkflowInstanceDAO();
    private final BpmnContentDAO bpmnDAO = new BpmnContentDAO();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy");

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.handlePreflight(response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Get user ID from request
            Integer userId = getUserIdFromRequest(request);

            if (userId == null || userId <= 0) {
                // Guest/anonymous: return empty list so UI does not show 401
                response.getWriter().write("[]");
                return;
            }

            // Get active tasks for user
            List<Map<String, Object>> tasks = taskDAO.findActiveTasksForUser(userId);

            // Enrich tasks with decision options from BPMN
            List<Map<String, Object>> enrichedTasks = new ArrayList<>();
            for (Map<String, Object> task : tasks) {
                Map<String, Object> enrichedTask = new java.util.HashMap<>(task);
                
                // Get decision options from BPMN
                try {
                    Integer workflowInstanceId = (Integer) task.get("workflowInstanceId");
                    String bpmnNodeId = (String) task.get("bpmnNodeId");
                    
                    if (workflowInstanceId != null && bpmnNodeId != null) {
                        WorkflowInstance instance = instanceDAO.findById(workflowInstanceId);
                        if (instance != null) {
                            String bpmnXml = bpmnDAO.getXmlContent(instance.getProcessDefinitionId());
                            if (bpmnXml != null) {
                                Document doc = BpmnParser.parseXml(bpmnXml);
                                Element taskElement = BpmnParser.findElementById(doc, bpmnNodeId);
                                
                                if (taskElement != null) {
                                    List<String> decisionOptions = getDecisionOptions(doc, taskElement);
                                    enrichedTask.put("decisionOptions", decisionOptions);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to get decision options for task {}: {}", task.get("taskId"), e.getMessage());
                    // Default to complete option
                    enrichedTask.put("decisionOptions", List.of("complete"));
                }
                
                // Format dates
                if (task.get("assignDate") != null) {
                    enrichedTask.put("assignDate", dateFormat.format((Timestamp) task.get("assignDate")));
                }
                if (task.get("dueDate") != null) {
                    enrichedTask.put("dueDate", dateFormat.format((Timestamp) task.get("dueDate")));
                }
                
                enrichedTasks.add(enrichedTask);
            }

            // Return tasks as JSON
            response.getWriter().write(gson.toJson(enrichedTasks));

        } catch (SQLException e) {
            logger.error("Database error fetching active tasks", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error fetching active tasks", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error fetching active tasks: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Get decision options from BPMN for a task
     */
    private List<String> getDecisionOptions(Document doc, Element taskElement) {
        List<String> decisionOptions = new ArrayList<>();
        
        try {
            // Check if task has outgoing flows to exclusive gateway
            List<String> outgoingFlows = BpmnParser.getOutgoingFlows(taskElement);
            
            for (String flowId : outgoingFlows) {
                String targetId = BpmnParser.getSequenceFlowTarget(doc, flowId);
                if (targetId != null) {
                    Element nextElement = BpmnParser.findElementById(doc, targetId);
                    if (nextElement != null && BpmnParser.isExclusiveGateway(nextElement)) {
                        // Task flows to exclusive gateway - get flow names as decision options
                        List<String> gatewayFlows = BpmnParser.getOutgoingFlows(nextElement);
                        for (String gfId : gatewayFlows) {
                            Element gf = BpmnParser.findElementById(doc, gfId);
                            if (gf != null) {
                                String flowName = BpmnParser.getSequenceFlowName(gf);
                                if (flowName != null && !flowName.isEmpty()) {
                                    // Normalize flow name to decision option
                                    String option = flowName.toLowerCase().trim();
                                    if (option.contains("approve")) {
                                        decisionOptions.add("approve");
                                    } else if (option.contains("reject")) {
                                        decisionOptions.add("reject");
                                    } else if (option.contains("complete")) {
                                        decisionOptions.add("complete");
                                    } else {
                                        decisionOptions.add(option);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // If no decision options found, default to complete
            if (decisionOptions.isEmpty()) {
                decisionOptions.add("complete");
            }
        } catch (Exception e) {
            logger.warn("Error extracting decision options: {}", e.getMessage());
            decisionOptions.add("complete");
        }
        
        return decisionOptions;
    }

    /**
     * Get user ID from request with fallback to cookie parsing
     */
    private Integer getUserIdFromRequest(HttpServletRequest request) {
        // First, try to get from request attributes (set by AuthFilter)
        Integer userId = UserContextUtil.getCurrentUserIdOrNull(request);

        if (userId != null && userId > 0) {
            return userId;
        }

        // Fallback: parse ACCESS_TOKEN cookie directly if filter didn't set attributes
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                userId = JwtUtil.getUserIdFromToken(token);
                if (userId != null && userId > 0) {
                    // Set attributes for future use
                    request.setAttribute("userId", userId);
                    request.setAttribute("userEmail", claims.getStringClaim("email"));
                    request.setAttribute("userName",
                            (claims.getStringClaim("firstName") + " " + claims.getStringClaim("lastName")).trim());
                    request.setAttribute("userRole", claims.getStringClaim("role"));
                    return userId;
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired, return null
            logger.debug("Failed to parse token from cookie: " + e.getMessage());
        }

        return null;
    }

    /**
     * Get cookie value from request
     */
    private String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}

