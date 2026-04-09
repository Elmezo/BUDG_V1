package com.example.budg_v2;

import com.example.budg_v2.model.RegulationXRegulatoryTheme;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.RegulationXRegulatoryThemeService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/regulation-x-regulatorytheme/*")
public class RegulationXRegulatoryThemeServlet extends HttpServlet {

    private RegulationXRegulatoryThemeService regulationXRegulatoryThemeService;
    private SegmentValidationService segmentValidationService;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        this.regulationXRegulatoryThemeService = new RegulationXRegulatoryThemeService();
        this.segmentValidationService = new SegmentValidationService();
        this.gson = new Gson();
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        try {
        String pathInfo = request.getPathInfo();
        
            if (pathInfo != null && pathInfo.startsWith("/regulation/")) {
                // Get regulatory themes for a specific regulation
                String regulationIdStr = pathInfo.substring("/regulation/".length());
                try {
                    int regulationId = Integer.parseInt(regulationIdStr);
                    List<Map<String, Object>> regulatoryThemes = regulationXRegulatoryThemeService.getRegulatoryThemesByRegulationId(regulationId);
                    
                    JsonArray jsonArray = new JsonArray();
                    for (Map<String, Object> theme : regulatoryThemes) {
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty("id", (Integer) theme.get("id"));
                        jsonObject.addProperty("regulationId", (Integer) theme.get("regulationId"));
                        jsonObject.addProperty("regulatoryThemeId", (Integer) theme.get("regulatoryThemeId"));
                        jsonObject.addProperty("relationType", (Integer) theme.get("relationType"));
                        jsonObject.addProperty("description", (String) theme.get("description"));
                        jsonObject.addProperty("regulatoryTheme", (String) theme.get("regulatoryThemeName"));
                        jsonObject.addProperty("refNumber", (String) theme.get("regulatoryThemeRefNumber"));
                        
                        // Add properties expected by the frontend
                        jsonObject.addProperty("allRegulations", (String) theme.get("allRegulations"));
                        jsonObject.addProperty("regulationCount", (Integer) theme.get("regulationCount"));
                        jsonObject.addProperty("regulatoryThemeDescription", (String) theme.get("regulatoryThemeDescription"));
                        
                        jsonArray.add(jsonObject);
                    }
                    
                    response.getWriter().write(jsonArray.toString());
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid regulation ID format", 400);
                }
            } else if (pathInfo != null && pathInfo.startsWith("/regulatory-theme/")) {
                // Get regulations for a specific regulatory theme
                String regulatoryThemeIdStr = pathInfo.substring("/regulatory-theme/".length());
                try {
                    int regulatoryThemeId = Integer.parseInt(regulatoryThemeIdStr);
                    List<Map<String, Object>> regulations = regulationXRegulatoryThemeService.getRegulationsWithDetailsByRegulatoryThemeId(regulatoryThemeId);
                    
                    JsonArray jsonArray = new JsonArray();
                    for (Map<String, Object> regulation : regulations) {
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty("id", (Integer) regulation.get("id"));
                        jsonObject.addProperty("regulationId", (Integer) regulation.get("regulationId"));
                        jsonObject.addProperty("regulatoryThemeId", (Integer) regulation.get("regulatoryThemeId"));
                        jsonObject.addProperty("relationType", (Integer) regulation.get("relationType"));
                        jsonObject.addProperty("description", (String) regulation.get("description"));
                        jsonObject.addProperty("regulation", (String) regulation.get("regulationName"));
                        jsonObject.addProperty("refNumber", (String) regulation.get("regulationRefNumber"));
                        jsonObject.addProperty("parentRegulation", (String) regulation.get("parentRegulationName"));
                        jsonArray.add(jsonObject);
                    }
                    
                    response.getWriter().write(jsonArray.toString());
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid regulatory theme ID format", 400);
                }
            } else if (pathInfo != null && pathInfo.length() > 1) {
                // Get specific regulation relationship by ID
                String idStr = pathInfo.substring(1);
                try {
                    int id = Integer.parseInt(idStr);
                    RegulationXRegulatoryTheme regulation = regulationXRegulatoryThemeService.getRegulationXRegulatoryThemeById(id);
                    if (regulation != null) {
                        response.getWriter().write(gson.toJson(regulation));
            } else {
                        JsonUtil.sendErrorResponse(response.getWriter(), "Regulation relationship not found", 404);
            }
        } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                }
            } else {
                // Get all regulation relationships
                List<RegulationXRegulatoryTheme> regulations = regulationXRegulatoryThemeService.getAllRegulationXRegulatoryThemes();
                response.getWriter().write(gson.toJson(regulations));
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Internal server error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            
            if (pathInfo != null && pathInfo.startsWith("/regulatory-theme/")) {
                // Bulk operations for a specific regulatory theme
                String regulatoryThemeIdStr = pathInfo.substring("/regulatory-theme/".length());
                try {
                    int regulatoryThemeId = Integer.parseInt(regulatoryThemeIdStr);
                    
                    // Get username for audit tracking
                    String userName = getCurrentUserName(request);
                    
                    // Read request body
                    StringBuilder jsonBuilder = new StringBuilder();
                    String line;
                    while ((line = request.getReader().readLine()) != null) {
                        jsonBuilder.append(line);
                    }
                    
                    JsonObject requestBody = JsonParser.parseString(jsonBuilder.toString()).getAsJsonObject();
                    
                    // Process operations
                    boolean success = true;
                    
                    // Handle deletions
                    if (requestBody.has("deletions") && requestBody.get("deletions").isJsonArray()) {
                        JsonArray deletions = requestBody.getAsJsonArray("deletions");
                        for (int i = 0; i < deletions.size(); i++) {
                            int deleteId = deletions.get(i).getAsInt();
                            if (!regulationXRegulatoryThemeService.deleteRegulationXRegulatoryThemeWithAudit(deleteId, userName)) {
                                success = false;
                                break;
                            }
                        }
                    }
                    
                    // Handle updates
                    if (success && requestBody.has("updates") && requestBody.get("updates").isJsonArray()) {
                        JsonArray updates = requestBody.getAsJsonArray("updates");
                        for (int i = 0; i < updates.size(); i++) {
                            JsonObject updateObj = updates.get(i).getAsJsonObject();
                            RegulationXRegulatoryTheme regulation = new RegulationXRegulatoryTheme();
                            regulation.setId(updateObj.get("id").getAsInt());
                            regulation.setRegulationId(updateObj.get("regulationId").getAsInt());
                            regulation.setRegulatoryThemeId(regulatoryThemeId);
                            regulation.setRelationType(updateObj.get("relationType").getAsInt());
                            if (updateObj.has("description") && !updateObj.get("description").isJsonNull()) {
                                regulation.setDescription(updateObj.get("description").getAsString());
                            }

                            if (!validateRegulationThemeRelationship(regulation, response)) {
                                return;
                            }
                            
                            if (!regulationXRegulatoryThemeService.updateRegulationXRegulatoryThemeWithAudit(regulation, userName)) {
                                success = false;
                                break;
                            }
                        }
                    }
                    
                    // Handle insertions
                    if (success && requestBody.has("inserts") && requestBody.get("inserts").isJsonArray()) {
                        JsonArray inserts = requestBody.getAsJsonArray("inserts");
                        for (int i = 0; i < inserts.size(); i++) {
                            JsonObject insertObj = inserts.get(i).getAsJsonObject();
                            RegulationXRegulatoryTheme regulation = new RegulationXRegulatoryTheme();
                            regulation.setRegulationId(insertObj.get("regulationId").getAsInt());
                            regulation.setRegulatoryThemeId(regulatoryThemeId);
                            regulation.setRelationType(insertObj.get("relationType").getAsInt());
                            if (insertObj.has("description") && !insertObj.get("description").isJsonNull()) {
                                regulation.setDescription(insertObj.get("description").getAsString());
                            }

                            if (!validateRegulationThemeRelationship(regulation, response)) {
                                return;
                            }
                            
                            if (regulationXRegulatoryThemeService.createRegulationXRegulatoryThemeWithAudit(regulation, userName) == null) {
                                success = false;
                                break;
                            }
                        }
                    }
                    
                    if (success) {
                        JsonObject jsonResponse = new JsonObject();
                        jsonResponse.addProperty("success", true);
                        jsonResponse.addProperty("message", "Operations completed successfully");
                        response.getWriter().write(jsonResponse.toString());
                    } else {
                        JsonUtil.sendErrorResponse(response.getWriter(), "One or more operations failed", 500);
                    }
                    
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid regulatory theme ID format", 400);
                }
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid endpoint", 400);
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Internal server error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * استخراج اسم المستخدم من الـ request
     */
    private String getCurrentUserName(HttpServletRequest request) {
        try {
            Object userNameObj = request.getAttribute("userName");
            if (userNameObj != null) {
                return userNameObj.toString();
            }
            
            // Try to get from userId
            Integer userId = (Integer) request.getAttribute("userId");
            if (userId != null) {
                return "User ID: " + userId;
            }
            
            // Fallback to remote user
            String remoteUser = request.getRemoteUser();
            if (remoteUser != null) {
                return remoteUser;
            }
            
            return "Unknown User";
        } catch (Exception e) {
            return "Unknown User";
        }
    }

    private boolean validateRegulationThemeRelationship(
            RegulationXRegulatoryTheme regulation, HttpServletResponse response) throws IOException, SQLException {
        SegmentValidationService.ValidationResult validationResult =
                segmentValidationService.validateCrossSegmentRelationship(
                        regulation.getRegulationId(), "Regulation",
                        regulation.getRegulatoryThemeId(), "RegulatoryTheme");
        if (!validationResult.isValid) {
            JsonUtil.sendErrorResponse(response.getWriter(), validationResult.message, 400);
            return false;
        }
        return true;
    }
}