package com.example.budg_v2;

import com.example.budg_v2.dao.WorkflowInstanceDAO;
import com.example.budg_v2.model.WorkflowInstance;
import com.example.budg_v2.model.WorkflowTask;
import com.example.budg_v2.dao.WorkflowTaskDAO;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.example.budg_v2.util.WorkflowAuthorizationUtil;
import com.example.budg_v2.util.WorkflowEngine;
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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * Servlet for workflow task operations
 * Endpoints:
 * POST /api/workflow_tasks/{taskId}/complete - Complete task
 */
@WebServlet("/api/workflow_tasks/*")
public class WorkflowTaskServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskServlet.class);
    private final Gson gson = new Gson();
    private final WorkflowEngine engine = new WorkflowEngine();

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
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        JsonObject error = new JsonObject();
        error.addProperty("error", "Method not allowed. Use POST instead.");
        response.getWriter().write(gson.toJson(error));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Extract task ID from path: /api/workflow_tasks/{taskId}/complete OR
            // /api/workflow_tasks/{taskId}/comments
            String pathInfo = request.getPathInfo();

            if (pathInfo == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Check if it's a comment request
            if (pathInfo.endsWith("/comments")) {
                handleCommentRequest(request, response, pathInfo);
                return;
            }

            // pathInfo will be like "/7/complete" for URL "/api/workflow_tasks/7/complete"
            if (!pathInfo.endsWith("/complete")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid path format. Expected: /complete or /comments");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Extract task ID: remove "/complete" suffix and leading "/"
            String taskIdStr = pathInfo.substring(1, pathInfo.length() - "/complete".length());
            int taskId = Integer.parseInt(taskIdStr);

            // ... (rest of completion logic) ...
            handleCompletionRequest(request, response, taskId);

        } catch (Exception e) {
            logger.error("Error processing request", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void handleCommentRequest(HttpServletRequest request, HttpServletResponse response, String pathInfo)
            throws IOException {
        try {
            // Extract task ID: remove "/comments" suffix and leading "/"
            String taskIdStr = pathInfo.substring(1, pathInfo.length() - "/comments".length());
            int taskId = Integer.parseInt(taskIdStr);

            // Parse request body
            JsonObject requestBody = gson.fromJson(request.getReader(), JsonObject.class);
            String comment = null;
            if (requestBody.has("comment") && !requestBody.get("comment").isJsonNull()) {
                comment = requestBody.get("comment").getAsString();
            }

            if (comment == null || comment.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Comment text is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            Integer userId = getUserIdFromRequest(request);
            if (userId == null || userId == 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            // Security check: User must be able to view/comment on task
            // Ideally check instance ID -> CR -> Stakeholder?
            // For now, simpler check or assume if logged in they can comment (like GitHub)

            engine.addTaskComment(taskId, comment, userId);

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Comment added successfully");
            response.getWriter().write(gson.toJson(responseJson));

        } catch (Exception e) {
            logger.error("Error adding comment", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error adding comment: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    private void handleCompletionRequest(HttpServletRequest request, HttpServletResponse response, int taskId)
            throws IOException {
        try {
            // Parse request body
            JsonObject requestBody = gson.fromJson(request.getReader(), JsonObject.class);
            String decision = "complete"; // Default
            if (requestBody.has("decision") && !requestBody.get("decision").isJsonNull()) {
                decision = requestBody.get("decision").getAsString();
            }

            // Extract comment (optional)
            String comment = null;
            if (requestBody.has("comment") && !requestBody.get("comment").isJsonNull()) {
                String commentValue = requestBody.get("comment").getAsString();
                if (commentValue != null && !commentValue.trim().isEmpty()) {
                    comment = commentValue.trim();
                }
            }

            // Get user ID from request (set by AuthFilter or JWT token)
            Integer userId = getUserIdFromRequest(request);

            if (userId == null || userId == 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject error = new JsonObject();
                error.addProperty("error", "User not authenticated");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Load task to get instance ID and role
            WorkflowTask task = new WorkflowTaskDAO().findById(taskId);
            if (task == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Task not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Load instance to get change request ID
            WorkflowInstance instance = new WorkflowInstanceDAO().findById(task.getWorkflowInstanceId());
            if (instance == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Workflow instance not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Check if user is Super Admin - Super Admins can complete any task
            boolean isSuperAdmin = isSuperAdminUser(request);

            // Authorization check: verify user has matching stakeholder role (unless Super
            // Admin)
            if (!isSuperAdmin && instance.getChangeRequestId() != null && task.getRoleName() != null) {
                boolean hasRole = WorkflowAuthorizationUtil.checkUserHasRoleForTask(
                        userId, instance.getChangeRequestId(), task.getRoleName());

                if (!hasRole) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "User does not have required role to complete this task");
                    response.getWriter().write(gson.toJson(error));
                    logger.warn("User {} attempted to complete task {} without required role {}",
                            userId, taskId, task.getRoleName());
                    return;
                }
            } else if (isSuperAdmin) {
                logger.info("Super Admin {} bypassing role check for task {}", userId, taskId);
            }

            // Complete task
            logger.info("═══════════════════════════════════════════════════════════════");
            logger.info("🎯 [TASK SERVLET] Completing task {} with decision: {} by user: {}", taskId, decision, userId);
            logger.info("═══════════════════════════════════════════════════════════════");
            
            Map<String, Object> result = engine.completeTask(
                    task.getWorkflowInstanceId(), taskId, decision, comment, userId);

            // Build response
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("workflowCompleted", (Boolean) result.get("workflowCompleted"));

            if (result.containsKey("nextTask")) {
                Object nextTaskObj = result.get("nextTask");
                if (nextTaskObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nextTaskInfo = (Map<String, Object>) nextTaskObj;
                    responseJson.add("nextTask", gson.toJsonTree(nextTaskInfo));
                    logger.info("✅ [TASK SERVLET] Next task added to response: {}", nextTaskInfo);
                } else {
                    responseJson.add("nextTask", gson.toJsonTree(nextTaskObj));
                    logger.info("✅ [TASK SERVLET] Next task added to response (non-map format)");
                }
            } else {
                logger.info("⚠️ [TASK SERVLET] No next task info in result");
            }

            responseJson.addProperty("message", "Task completed successfully");

            response.getWriter().write(gson.toJson(responseJson));

            logger.info("✅ [TASK SERVLET] Task {} completed successfully. Response sent to client.", taskId);

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid ID format");
            response.getWriter().write(gson.toJson(error));

        } catch (SQLException e) {
            logger.error("Database error completing task", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));

        } catch (Exception e) {
            logger.error("Error completing task", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error completing task: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
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

    /**
     * Check if user is Super Admin
     */
    private boolean isSuperAdminUser(HttpServletRequest request) {
        Object roleObj = request.getAttribute("userRole");
        if (roleObj == null) {
            return false;
        }
        return AppRoleNames.isSuperAdminName(roleObj.toString());
    }
}
