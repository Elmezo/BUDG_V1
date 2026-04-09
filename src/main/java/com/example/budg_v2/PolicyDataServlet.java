package com.example.budg_v2;

import com.example.budg_v2.service.PolicyDataService;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/policy-data/*")
public class PolicyDataServlet extends HttpServlet {
    private final PolicyDataService policyDataService;
    private final Gson gson = new Gson();

    public PolicyDataServlet() {
        this.policyDataService = new PolicyDataService();
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                sendError(response, "Invalid endpoint", 400);
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");
            
            if (parts.length < 2) {
                sendError(response, "Invalid endpoint. Expected /api/policy-data/{policyId}/datasets or /api/policy-data/{policyId}/attributes", 400);
                return;
            }
            
            int policyId = Integer.parseInt(parts[0]);
            String resource = parts[1];
            
            if ("datasets".equals(resource)) {
                handleGetDatasets(policyId, response);
            } else if ("attributes".equals(resource)) {
                handleGetAttributes(policyId, response);
            } else {
                sendError(response, "Invalid resource. Use 'datasets' or 'attributes'", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid policy ID", 400);
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    /**
     * Handle GET request for datasets
     */
    private void handleGetDatasets(int policyId, HttpServletResponse response) 
            throws SQLException, IOException {
        List<Map<String, Object>> datasets = policyDataService.getPolicyDatasets(policyId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", datasets);
        
        response.getWriter().write(gson.toJson(result));
    }

    /**
     * Handle GET request for attributes
     */
    private void handleGetAttributes(int policyId, HttpServletResponse response) 
            throws SQLException, IOException {
        List<Map<String, Object>> attributes = policyDataService.getPolicyAttributes(policyId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", attributes);
        
        response.getWriter().write(gson.toJson(result));
    }

    /**
     * Send error response
     */
    private void sendError(HttpServletResponse response, String message, int status) 
            throws IOException {
        response.setStatus(status);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        response.getWriter().write(gson.toJson(error));
    }
}

