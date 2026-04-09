package com.example.unisonsearch.servlet;

import com.example.budg_v2.util.CorsUtil;
import com.example.unisonsearch.model.*;
import com.example.unisonsearch.repository.DatabaseHelper;
import com.example.unisonsearch.repository.TaskRepository;
import com.example.unisonsearch.service.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Unified Search API Servlet - New endpoint with correct relationship logic.
 * 
 * Endpoint: POST /api/unified/search
 */
@WebServlet(name = "UnifiedSearchApiServlet", urlPatterns = { "/api/unified/search" })
public class UnifiedSearchApiServlet extends HttpServlet {
    
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private UnifiedSearchService unifiedSearchService;
    private ResultFilter resultFilter;
    private SearchLimiter searchLimiter;
    
    @Override
    public void init() {
        try {
            // Initialize services
            DatabaseHelper databaseHelper = new DatabaseHelper();
            RelationshipService relationshipService = new RelationshipServiceImpl(databaseHelper);
            DedupManager dedupManager = new DedupManager();
            RelationshipManager relationshipManager = new RelationshipManager();
            TaskRepository taskRepository = new TaskRepository();
            ActiveTasksService activeTasksService = new ActiveTasksService(taskRepository);
            PermissionService permissionService = new PermissionService();
            
            unifiedSearchService = new UnifiedSearchService(
                relationshipService,
                dedupManager,
                relationshipManager,
                activeTasksService
            );
            
            resultFilter = new ResultFilter(permissionService);
            searchLimiter = new SearchLimiter();
        } catch (Exception e) {
            System.err.println("Error initializing UnifiedSearchApiServlet: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(request, response);
        
        if ("OPTIONS".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        
        try {
            // Parse request
            SearchRequest searchRequest = parseRequest(request);
            if (searchRequest == null) {
                sendError(response, "Invalid request", 400);
                return;
            }
            
            // Get user from session
            HttpSession session = request.getSession(false);
            Integer userId = null;
            if (session != null) {
                Object userIdObj = session.getAttribute("userId");
                if (userIdObj instanceof Integer) {
                    userId = (Integer) userIdObj;
                }
            }
            searchRequest.setUserId(userId);
            
            // Execute search
            SearchResponse searchResponse = unifiedSearchService.executeSearch(searchRequest);
            
            // Apply filters
            if (userId != null) {
                searchResponse = resultFilter.filterResults(searchResponse, userId);
            }
            
            // Apply limits
            searchResponse = searchLimiter.applyLimits(searchResponse);
            
            // Send response
            sendResponse(response, searchResponse);
            
        } catch (SQLException e) {
            System.err.println("Database error in UnifiedSearchApiServlet: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            System.err.println("Error in UnifiedSearchApiServlet: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Internal server error: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Parse request JSON to SearchRequest.
     */
    private SearchRequest parseRequest(HttpServletRequest request) throws IOException {
        StringBuilder json = new StringBuilder();
        String line;
        while ((line = request.getReader().readLine()) != null) {
            json.append(line);
        }
        
        if (json.length() == 0) {
            return null;
        }
        
        try {
            SearchRequest searchRequest = gson.fromJson(json.toString(), SearchRequest.class);
            return searchRequest;
        } catch (Exception e) {
            System.err.println("Error parsing request: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Send JSON response.
     */
    private void sendResponse(HttpServletResponse response, SearchResponse searchResponse) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(gson.toJson(searchResponse));
    }
    
    /**
     * Send error response.
     */
    private void sendError(HttpServletResponse response, String message, int status) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status);
        SearchResponse errorResponse = SearchResponse.error(message);
        response.getWriter().write(gson.toJson(errorResponse));
    }
}

