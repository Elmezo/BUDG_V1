package com.example.unisonsearch.servlet;

import com.example.unisonsearch.model.SearchParams;
import com.example.unisonsearch.repository.DatabaseHelper;
import com.example.unisonsearch.repository.QueryBuilder;
import com.example.unisonsearch.service.ConfigurationService;
import com.example.unisonsearch.service.RelationshipManager;
import com.example.unisonsearch.service.SearchService;
import com.google.gson.Gson;
import com.example.budg_v2.util.CorsUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet adapter that preserves endpoints and behavior, delegating to
 * services.
 */
@WebServlet(name = "UnisonSearchServlet", urlPatterns = { "/UnisonSearch/*" })
public class UnisonSearchServlet extends HttpServlet {

    private final Gson gson = new Gson();
    private final ConfigurationService configurationService = new ConfigurationService();
    private final RelationshipManager relationshipManager = new RelationshipManager();
    private final QueryBuilder queryBuilder = new QueryBuilder(configurationService, relationshipManager);
    private final DatabaseHelper databaseHelper = new DatabaseHelper();
    private final SearchService searchService = new SearchService(queryBuilder, databaseHelper);

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        // Parse search params

        SearchParams params = SearchParams.fromRequest(request);

        if (params.module == null || "/".equals(request.getPathInfo())) {
            System.err.println("[UnisonSearchServlet] Module is null or pathInfo is '/'. Returning 400.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Module not specified\",\"pathInfo\":\"" + request.getPathInfo()
                    + "\",\"requestURI\":\"" + request.getRequestURI() + "\"}");
            return;
        }

        String[] parts = params.pathParts;
        if ("config".equals(params.module) && (parts.length >= 3 ? "fuzzy-search".equals(parts[2]) : false)) {
            handleFuzzySearchConfig(response);
            return;
        }

        // Special handling for Active Tasks
        if ("activeTasks".equals(params.module) || "active-tasks".equals(params.module)) {
            try {
                com.example.budg_v2.dao.WorkflowTaskDAO taskDAO = new com.example.budg_v2.dao.WorkflowTaskDAO();
                // Get userId from request (similar to ActiveTasksServlet)
                Integer userId = getUserIdFromRequest(request);
                if (userId == null || userId == 0) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"User not authenticated\"}");
                    return;
                }

                List<Map<String, Object>> tasks = taskDAO.findActiveTasksForUser(userId);

                // Apply search query if provided
                String query = request.getParameter("q");
                if (query != null && !query.trim().isEmpty()) {
                    String lowerQuery = query.toLowerCase();
                    tasks = tasks.stream()
                            .filter(task -> {
                                String name = task.get("name") != null ? task.get("name").toString().toLowerCase() : "";
                                String title = task.get("title") != null ? task.get("title").toString().toLowerCase()
                                        : "";
                                String objectType = task.get("objectType") != null
                                        ? task.get("objectType").toString().toLowerCase()
                                        : "";
                                String object = task.get("object") != null ? task.get("object").toString().toLowerCase()
                                        : "";
                                String owner = task.get("owner") != null ? task.get("owner").toString().toLowerCase()
                                        : "";
                                return name.contains(lowerQuery) || title.contains(lowerQuery) ||
                                        objectType.contains(lowerQuery) || object.contains(lowerQuery) ||
                                        owner.contains(lowerQuery);
                            })
                            .collect(java.util.stream.Collectors.toList());
                }

                response.getWriter().write(gson.toJson(tasks));
                return;
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                System.err.println("UnisonSearchServlet: Error searching Active Tasks: " + e.getMessage());
                e.printStackTrace();
                response.getWriter().write("{\"error\":\"Error searching Active Tasks: " + e.getMessage() + "\"}");
                return;
            }
        }

        try {
            List<Map<String, Object>> rows = searchService.search(params);
            response.getWriter().write(gson.toJson(rows));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            System.err
                    .println("UnisonSearchServlet: Database error for module " + params.module + ": " + e.getMessage());
            e.printStackTrace();
            response.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            System.err.println(
                    "UnisonSearchServlet: Unexpected error for module " + params.module + ": " + e.getMessage());
            e.printStackTrace();
            response.getWriter().write("{\"error\":\"Unexpected error: " + e.getMessage() + "\"}");
        }
    }

    private void handleFuzzySearchConfig(HttpServletResponse response) throws IOException {
        try {
            boolean enabled = configurationService.getFuzzySearchConfig();
            Map<String, Object> result = new HashMap<>();
            result.put("enabled", enabled);
            result.put("config_key", "UNISON_FUZZY_DEFAULT");
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Database error");
            error.put("enabled", false);
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Get user ID from request (similar to ActiveTasksServlet)
     */
    private Integer getUserIdFromRequest(HttpServletRequest request) {
        // Try to get from request attributes (set by AuthFilter)
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj instanceof Integer) {
            return (Integer) userIdObj;
        }

        // Fallback: parse ACCESS_TOKEN cookie
        try {
            jakarta.servlet.http.Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie cookie : cookies) {
                    if ("ACCESS_TOKEN".equals(cookie.getName())) {
                        String token = cookie.getValue();
                        if (token != null) {
                            Integer userId = com.example.budg_v2.util.JwtUtil.getUserIdFromToken(token);
                            if (userId != null && userId > 0) {
                                return userId;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired
            System.err.println("Failed to parse token from cookie: " + e.getMessage());
        }

        return null;
    }

}
