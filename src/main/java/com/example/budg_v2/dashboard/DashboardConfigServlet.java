package com.example.budg_v2.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Servlet for dashboard configuration
 * GET: Retrieve user's dashboard configuration
 * POST: Save user's dashboard configuration
 */
@WebServlet(name = "DashboardConfigServlet", urlPatterns = "/api/dashboard/config")
public class DashboardConfigServlet extends HttpServlet {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            objectMapper.writeValue(response.getWriter(), Collections.singletonMap("error", "User not authenticated"));
            return;
        }
        
        try {
            Map<String, Object> config = DashboardService.getUserDashboardConfig(userId);
            objectMapper.writeValue(response.getWriter(), config);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Collections.singletonMap("error", "Database error: " + e.getMessage()));
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            objectMapper.writeValue(response.getWriter(), Collections.singletonMap("error", "User not authenticated"));
            return;
        }
        
        try {
            Map<String, Object> requestBody = objectMapper.readValue(request.getInputStream(), Map.class);
            List<Map<String, Object>> widgets = (List<Map<String, Object>>) requestBody.get("widgets");
            
            if (widgets == null || widgets.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Collections.singletonMap("error", "No widget configuration provided"));
                return;
            }
            
            boolean success = DashboardService.saveUserDashboardConfig(userId, widgets);
            
            if (success) {
                objectMapper.writeValue(response.getWriter(), Collections.singletonMap("success", true));
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                objectMapper.writeValue(response.getWriter(), Collections.singletonMap("error", "Failed to save dashboard configuration"));
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Collections.singletonMap("error", "Server error: " + e.getMessage()));
        }
    }
}
