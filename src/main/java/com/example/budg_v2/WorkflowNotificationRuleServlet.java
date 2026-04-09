package com.example.budg_v2;

import com.example.budg_v2.dao.ProcessDefinitionDAO;
import com.example.budg_v2.model.ProcessDefinition;
import com.example.budg_v2.model.WorkflowNotificationRule;
import com.example.budg_v2.service.WorkflowNotificationRuleService;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet for workflow notification rule CRUD operations
 * Endpoints:
 * GET /api/notification-rules - List all rules
 * GET /api/notification-rules/{id} - Get single rule
 * GET /api/notification-rules/modules - Get available modules (workflows + "*")
 * GET /api/notification-channels - Get available channels
 * POST /api/notification-rules - Create rule
 * PUT /api/notification-rules/{id} - Update rule
 * DELETE /api/notification-rules/{id} - Delete rule
 */
@WebServlet({"/api/notification-rules/*", "/api/notification-channels"})
public class WorkflowNotificationRuleServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowNotificationRuleServlet.class);
    private final Gson gson = new Gson();
    private final WorkflowNotificationRuleService ruleService = new WorkflowNotificationRuleService();
    private final ProcessDefinitionDAO processDefinitionDAO = new ProcessDefinitionDAO();

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

        String pathInfo = request.getPathInfo();
        String requestURI = request.getRequestURI();

        try {
            // Handle /api/notification-channels endpoint
            if (requestURI.endsWith("/notification-channels")) {
                List<String> channels = new ArrayList<>();
                channels.add("email");
                channels.add("ui");
                channels.add("sms");
                response.getWriter().write(gson.toJson(channels));
                return;
            }

            if (pathInfo == null || pathInfo.equals("/")) {
                // List all rules
                List<WorkflowNotificationRule> rules = ruleService.getAllRules();
                response.getWriter().write(gson.toJson(rules));

            } else if (pathInfo.equals("/modules")) {
                // Get available modules (workflows + "*")
                List<Map<String, String>> modules = new ArrayList<>();
                
                // Add wildcard option first
                Map<String, String> wildcard = new HashMap<>();
                wildcard.put("name", "*");
                wildcard.put("displayName", "* (All Workflows)");
                modules.add(wildcard);
                
                // Add all workflows
                List<ProcessDefinition> workflows = processDefinitionDAO.findAll();
                for (ProcessDefinition pd : workflows) {
                    Map<String, String> module = new HashMap<>();
                    module.put("name", pd.getPrimaryName());
                    module.put("displayName", pd.getPrimaryName());
                    modules.add(module);
                }
                
                response.getWriter().write(gson.toJson(modules));

            } else if (pathInfo.equals("/channels")) {
                // Get available channels
                List<String> channels = new ArrayList<>();
                channels.add("email");
                channels.add("ui");
                channels.add("sms");
                response.getWriter().write(gson.toJson(channels));

            } else {
                // Get single rule by ID
                Long id = Long.parseLong(pathInfo.substring(1));
                List<WorkflowNotificationRule> allRules = ruleService.getAllRules();
                WorkflowNotificationRule rule = allRules.stream()
                        .filter(r -> r.getId().equals(id))
                        .findFirst()
                        .orElse(null);

                if (rule == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Notification rule not found");
                    response.getWriter().write(gson.toJson(error));
                } else {
                    response.getWriter().write(gson.toJson(rule));
                }
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

        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }

            WorkflowNotificationRule rule = gson.fromJson(jsonBody.toString(), WorkflowNotificationRule.class);

            // Validate
            ruleService.validateRule(rule);

            // Check for duplicates
            if (ruleService.isDuplicate(rule, null)) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                JsonObject error = new JsonObject();
                error.addProperty("error", "A similar notification rule already exists");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            Long id = ruleService.createRule(rule);
            rule.setId(id);

            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(gson.toJson(rule));

        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
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
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Rule ID is required");
            response.getWriter().write(gson.toJson(error));
            return;
        }

        try {
            Long id = Long.parseLong(pathInfo.substring(1));

            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }

            WorkflowNotificationRule rule = gson.fromJson(jsonBody.toString(), WorkflowNotificationRule.class);

            // Validate
            ruleService.validateRule(rule);

            // Check for duplicates (excluding current rule)
            if (ruleService.isDuplicate(rule, id)) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                JsonObject error = new JsonObject();
                error.addProperty("error", "A similar notification rule already exists");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            ruleService.updateRule(id, rule);
            rule.setId(id);

            response.getWriter().write(gson.toJson(rule));

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid ID format");
            response.getWriter().write(gson.toJson(error));

        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
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
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Rule ID is required");
            response.getWriter().write(gson.toJson(error));
            return;
        }

        try {
            Long id = Long.parseLong(pathInfo.substring(1));
            ruleService.deleteRule(id);

            response.setStatus(HttpServletResponse.SC_NO_CONTENT);

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
}

