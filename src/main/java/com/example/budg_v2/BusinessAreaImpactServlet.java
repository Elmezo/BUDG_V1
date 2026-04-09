package com.example.budg_v2;

import com.example.budg_v2.service.BusinessAreaImpactService;
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

@WebServlet("/api/businessarea-impact/*")
public class BusinessAreaImpactServlet extends HttpServlet {
    private final BusinessAreaImpactService businessAreaImpactService;
    private final Gson gson = new Gson();

    public BusinessAreaImpactServlet() {
        this.businessAreaImpactService = new BusinessAreaImpactService();
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
            
            // Check for reverse lookup first: /api/businessarea-impact/{entityType}s/{entityId}/businessareas
            if (parts.length >= 3) {
                if ("glossaries".equals(parts[0])) {
                    try {
                        int glossaryId = Integer.parseInt(parts[1]);
                        if ("businessareas".equals(parts[2])) {
                            handleGetBusinessAreaRelationshipsByGlossaryId(glossaryId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid glossary ID: " + parts[1], 400);
                        return;
                    }
                } else if ("systems".equals(parts[0])) {
                    try {
                        int systemId = Integer.parseInt(parts[1]);
                        if ("businessareas".equals(parts[2])) {
                            handleGetBusinessAreaRelationshipsBySystemId(systemId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid system ID: " + parts[1], 400);
                        return;
                    }
                } else if ("processes".equals(parts[0])) {
                    try {
                        int processId = Integer.parseInt(parts[1]);
                        if ("businessareas".equals(parts[2])) {
                            handleGetBusinessAreaRelationshipsByProcessId(processId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid process ID: " + parts[1], 400);
                        return;
                    }
                } else if ("capabilities".equals(parts[0])) {
                    try {
                        int capabilityId = Integer.parseInt(parts[1]);
                        if ("businessareas".equals(parts[2])) {
                            handleGetBusinessAreaRelationshipsByCapabilityId(capabilityId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid capability ID: " + parts[1], 400);
                        return;
                    }
                }
            }
            
            if ("glossary-relation-types".equals(parts[0])) {
                handleGetGlossaryRelationTypes(response);
            } else if ("system-relation-types".equals(parts[0])) {
                handleGetSystemRelationTypes(response);
            } else if ("process-relation-types".equals(parts[0])) {
                handleGetProcessRelationTypes(response);
            } else if ("glossary-owner".equals(parts[0]) && parts.length >= 2) {
                int glossaryId = Integer.parseInt(parts[1]);
                handleGetGlossaryOwner(glossaryId, response);
            } else if ("system-owner".equals(parts[0]) && parts.length >= 2) {
                int systemId = Integer.parseInt(parts[1]);
                handleGetSystemOwner(systemId, response);
            } else if ("process-owner".equals(parts[0]) && parts.length >= 2) {
                int processId = Integer.parseInt(parts[1]);
                handleGetProcessOwner(processId, response);
            } else if (parts.length >= 2) {
                int businessAreaId = Integer.parseInt(parts[0]);
                
                if ("glossaries".equals(parts[1])) {
                    handleGetGlossaryRelationships(businessAreaId, request, response);
                } else if ("systems".equals(parts[1])) {
                    handleGetSystemRelationships(businessAreaId, request, response);
                } else if ("processes".equals(parts[1])) {
                    handleGetProcessRelationships(businessAreaId, request, response);
                } else {
                    sendError(response, "Invalid endpoint", 400);
                }
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid business area ID", 400);
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
            
            if ("glossaries".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveGlossaryRelationships(request, response);
            } else if ("systems".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveSystemRelationships(request, response);
            } else if ("processes".equals(parts[0]) && "save".equals(parts[1])) {
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
    
    private void handleGetGlossaryRelationships(int businessAreaId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = businessAreaImpactService.getGlossaryRelationshipsByBusinessAreaId(businessAreaId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Glossary", "glossaryId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetSystemRelationships(int businessAreaId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = businessAreaImpactService.getSystemRelationshipsByBusinessAreaId(businessAreaId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "System", "systemId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve system relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProcessRelationships(int businessAreaId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = businessAreaImpactService.getProcessRelationshipsByBusinessAreaId(businessAreaId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relationships: " + e.getMessage(), 500);
        }
    }
    
    // ===== GET RELATION TYPE HANDLERS =====
    
    private void handleGetGlossaryRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = businessAreaImpactService.getGlossaryRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetSystemRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = businessAreaImpactService.getSystemRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve system relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProcessRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = businessAreaImpactService.getProcessRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relation types: " + e.getMessage(), 500);
        }
    }
    
    // ===== GET OWNER HANDLERS =====
    
    private void handleGetGlossaryOwner(int glossaryId, HttpServletResponse response) 
            throws IOException {
        try {
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", businessAreaImpactService.getGlossaryOwnersString(glossaryId));
            owner.put("ownerEmail", businessAreaImpactService.getGlossaryOwnersEmail(glossaryId));
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary owner: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetSystemOwner(int systemId, HttpServletResponse response) 
            throws IOException {
        try {
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", businessAreaImpactService.getSystemOwnersString(systemId));
            owner.put("ownerEmail", businessAreaImpactService.getSystemOwnersEmail(systemId));
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve system owner: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProcessOwner(int processId, HttpServletResponse response) 
            throws IOException {
        try {
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", businessAreaImpactService.getProcessOwnersString(processId));
            owner.put("ownerEmail", businessAreaImpactService.getProcessOwnersEmail(processId));
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process owner: " + e.getMessage(), 500);
        }
    }
    
    // ===== SAVE RELATIONSHIP HANDLERS =====
    
    private void handleSaveGlossaryRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            JsonObject jsonData = gson.fromJson(jsonBody.toString(), JsonObject.class);
            int businessAreaId = jsonData.get("businessAreaId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Business Area", businessAreaId)) {
                return; // Response already sent
            }
            
            JsonElement relationshipsElement = jsonData.get("relationships");
            List<Map<String, Object>> relationships = new ArrayList<>();
            
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                for (JsonElement element : relationshipsElement.getAsJsonArray()) {
                    JsonObject rel = element.getAsJsonObject();
                    Map<String, Object> relationshipMap = new HashMap<>();
                    
                    if (rel.has("glossaryId") && !rel.get("glossaryId").isJsonNull()) {
                        relationshipMap.put("glossaryId", rel.get("glossaryId").getAsInt());
                    }
                    if (rel.has("relationType") && !rel.get("relationType").isJsonNull()) {
                        relationshipMap.put("relationType", rel.get("relationType").getAsInt());
                    }
                    if (rel.has("description")) {
                        relationshipMap.put("description", rel.get("description").isJsonNull() ? null : rel.get("description").getAsString());
                    }
                    
                    relationships.add(relationshipMap);
                }
            }
            
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User authentication required\"}");
                return;
            }

            var glossaryValidation = ImpactSegmentValidationUtil.validateRelationships(
                    businessAreaId, "BusinessArea", jsonData, "Glossary", "glossaryId");
            if (!glossaryValidation.isValid) {
                sendError(response, glossaryValidation.message, 400);
                return;
            }
            
            boolean success = businessAreaImpactService.saveGlossaryRelationships(businessAreaId, relationships, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Glossary relationships saved successfully" : "Failed to save glossary relationships");
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save glossary relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleSaveSystemRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            JsonObject jsonData = gson.fromJson(jsonBody.toString(), JsonObject.class);
            int businessAreaId = jsonData.get("businessAreaId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Business Area", businessAreaId)) {
                return; // Response already sent
            }
            
            JsonElement relationshipsElement = jsonData.get("relationships");
            List<Map<String, Object>> relationships = new ArrayList<>();
            
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                for (JsonElement element : relationshipsElement.getAsJsonArray()) {
                    JsonObject rel = element.getAsJsonObject();
                    Map<String, Object> relationshipMap = new HashMap<>();
                    
                    if (rel.has("systemId") && !rel.get("systemId").isJsonNull()) {
                        relationshipMap.put("systemId", rel.get("systemId").getAsInt());
                    }
                    if (rel.has("relationType") && !rel.get("relationType").isJsonNull()) {
                        relationshipMap.put("relationType", rel.get("relationType").getAsInt());
                    }
                    if (rel.has("description")) {
                        relationshipMap.put("description", rel.get("description").isJsonNull() ? null : rel.get("description").getAsString());
                    }
                    
                    relationships.add(relationshipMap);
                }
            }
            
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User authentication required\"}");
                return;
            }

            var systemValidation = ImpactSegmentValidationUtil.validateRelationships(
                    businessAreaId, "BusinessArea", jsonData, "System", "systemId");
            if (!systemValidation.isValid) {
                sendError(response, systemValidation.message, 400);
                return;
            }
            
            boolean success = businessAreaImpactService.saveSystemRelationships(businessAreaId, relationships, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "System relationships saved successfully" : "Failed to save system relationships");
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save system relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleSaveProcessRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            JsonObject jsonData = gson.fromJson(jsonBody.toString(), JsonObject.class);
            int businessAreaId = jsonData.get("businessAreaId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Business Area", businessAreaId)) {
                return; // Response already sent
            }
            
            JsonElement relationshipsElement = jsonData.get("relationships");
            List<Map<String, Object>> relationships = new ArrayList<>();
            
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                for (JsonElement element : relationshipsElement.getAsJsonArray()) {
                    JsonObject rel = element.getAsJsonObject();
                    Map<String, Object> relationshipMap = new HashMap<>();
                    
                    if (rel.has("processId") && !rel.get("processId").isJsonNull()) {
                        relationshipMap.put("processId", rel.get("processId").getAsInt());
                    }
                    if (rel.has("relationType") && !rel.get("relationType").isJsonNull()) {
                        relationshipMap.put("relationType", rel.get("relationType").getAsInt());
                    }
                    if (rel.has("description")) {
                        relationshipMap.put("description", rel.get("description").isJsonNull() ? null : rel.get("description").getAsString());
                    }
                    
                    relationships.add(relationshipMap);
                }
            }
            
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User authentication required\"}");
                return;
            }

            var processValidation = ImpactSegmentValidationUtil.validateRelationships(
                    businessAreaId, "BusinessArea", jsonData, "Process", "processId");
            if (!processValidation.isValid) {
                sendError(response, processValidation.message, 400);
                return;
            }
            
            boolean success = businessAreaImpactService.saveProcessRelationships(businessAreaId, relationships, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Process relationships saved successfully" : "Failed to save process relationships");
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save process relationships: " + e.getMessage(), 500);
        }
    }
    
    // ===== REVERSE LOOKUP HANDLERS =====
    
    private void handleGetBusinessAreaRelationshipsByGlossaryId(int glossaryId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = businessAreaImpactService.getBusinessAreaRelationshipsByGlossaryId(glossaryId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "BusinessArea", "businessAreaId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve business area relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetBusinessAreaRelationshipsBySystemId(int systemId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = businessAreaImpactService.getBusinessAreaRelationshipsBySystemId(systemId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "BusinessArea", "businessAreaId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve business area relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetBusinessAreaRelationshipsByCapabilityId(int capabilityId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            com.example.budg_v2.service.CapabilityImpactService capabilityImpactService = new com.example.budg_v2.service.CapabilityImpactService();
            List<Map<String, Object>> relationships = capabilityImpactService.getBusinessAreaRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "BusinessArea", "businessAreaId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve business area relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetBusinessAreaRelationshipsByProcessId(int processId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = businessAreaImpactService.getBusinessAreaRelationshipsByProcessId(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "BusinessArea", "businessAreaId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve business area relationships: " + e.getMessage(), 500);
        }
    }
    
    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        response.getWriter().write(gson.toJson(error));
    }
}

