package com.example.budg_v2;

import com.example.budg_v2.service.InterfaceImpactService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ImpactSegmentValidationUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/interface-impact/*")
public class InterfaceImpactServlet extends HttpServlet {
    private final InterfaceImpactService interfaceImpactService;
    private final Gson gson = new Gson();

    public InterfaceImpactServlet() {
        this.interfaceImpactService = new InterfaceImpactService();
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
            
            if ("process-relation-types".equals(parts[0])) {
                handleGetProcessRelationTypes(response);
            } else if ("process-owner".equals(parts[0]) && parts.length >= 2) {
                int processId = Integer.parseInt(parts[1]);
                handleGetProcessOwner(processId, response);
            } else if (parts.length >= 2) {
                int interfaceId = Integer.parseInt(parts[0]);
                
                if ("processes".equals(parts[1])) {
                    handleGetProcessRelationships(interfaceId, request, response);
                } else {
                    sendError(response, "Invalid endpoint", 400);
                }
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid interface ID", 400);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
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
            
            if ("processes".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveProcessRelationships(request, response);
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATIONSHIP HANDLERS =====
    
    private void handleGetProcessRelationships(int interfaceId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = interfaceImpactService.getProcessRelationshipsByInterfaceId(interfaceId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relationships: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATION TYPE HANDLERS =====
    
    private void handleGetProcessRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = interfaceImpactService.getProcessRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relation types: " + e.getMessage(), 500);
        }
    }

    // ===== GET OWNER HANDLERS =====
    
    private void handleGetProcessOwner(int processId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = interfaceImpactService.getProcessOwnersString(processId);
            String ownerEmail = interfaceImpactService.getProcessOwnersEmail(processId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("ownerName", ownerName != null ? ownerName : "No owner");
            result.put("processOwnerName", ownerName != null ? ownerName : "No owner");
            result.put("ownerEmail", ownerEmail);
            result.put("processOwnerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process owner: " + e.getMessage(), 500);
        }
    }

    // ===== SAVE HANDLERS =====
    
    private void handleSaveProcessRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User authentication required\"}");
                return;
            }
            
            StringBuilder jsonBody = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBody.append(line);
                }
            }
            
            JsonObject jsonObject = gson.fromJson(jsonBody.toString(), JsonObject.class);
            int interfaceId = jsonObject.get("interfaceId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Interface", interfaceId)) {
                return; // Response already sent
            }
            
            JsonElement relationshipsElement = jsonObject.get("relationships");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relationships = new ArrayList<>();
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                relationships = gson.fromJson(relationshipsElement, List.class);
            }

            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    interfaceId, "Interface", relationships, "Process", "processId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }
            
            boolean success = interfaceImpactService.saveProcessRelationships(interfaceId, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "Process relationships saved successfully" : "Failed to save process relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save process relationships: " + e.getMessage(), 500);
        }
    }

    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        response.getWriter().write(gson.toJson(error));
    }
}

