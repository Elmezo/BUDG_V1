package com.example.budg_v2;

import com.example.budg_v2.model.RegulationXRegulation;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.RegulationXRegulationService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/regulation-x-regulation/*")
public class RegulationXRegulationServlet extends HttpServlet {
    
    private RegulationXRegulationService service = new RegulationXRegulationService();
    private SegmentValidationService segmentValidationService = new SegmentValidationService();
    private Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        try {
            String pathInfo = request.getPathInfo();
            
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Missing regulation ID\"}");
                return;
            }
            
            String[] pathParts = pathInfo.substring(1).split("/");
            String action = pathParts[0];
            
            if ("source".equals(action) && pathParts.length > 1) {
                // GET /api/regulation-x-regulation/source/{id}
                int sourceRegulationId = Integer.parseInt(pathParts[1]);
                List<Map<String, Object>> relations = service.getRelationsBySourceRegulationId(sourceRegulationId);
                out.print(gson.toJson(relations));
                
            } else if ("target".equals(action) && pathParts.length > 1) {
                // GET /api/regulation-x-regulation/target/{id}
                int targetRegulationId = Integer.parseInt(pathParts[1]);
                List<Map<String, Object>> relations = service.getRelationsByTargetRegulationId(targetRegulationId);
                out.print(gson.toJson(relations));
                
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid endpoint\"}");
            }
            
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\": \"Invalid regulation ID\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        try {
            JsonObject jsonObject = gson.fromJson(request.getReader(), JsonObject.class);
            
            RegulationXRegulation relation = new RegulationXRegulation();
            relation.setSourceRegulationId(jsonObject.get("sourceRegulationId").getAsInt());
            relation.setTargetRegulationId(jsonObject.get("targetRegulationId").getAsInt());
            relation.setRelationType(jsonObject.get("relationType").getAsInt());
            relation.setDescription(jsonObject.has("description") ? jsonObject.get("description").getAsString() : null);
            relation.setLastUpdateUserId(1); // TODO: Get from session

            if (!validateRegulationRelationship(relation, response, out)) {
                return;
            }
            
            int newId = service.createRegulationXRegulation(relation);
            
            if (newId > 0) {
                relation.setId(newId);
                out.print(gson.toJson(relation));
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"error\": \"Failed to create relation\"}");
            }
            
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        try {
            String pathInfo = request.getPathInfo();
            
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Missing regulation ID\"}");
                return;
            }
            
            String[] pathParts = pathInfo.substring(1).split("/");
            String action = pathParts[0];
            
            if ("batch".equals(action) && pathParts.length > 1) {
                // PUT /api/regulation-x-regulation/batch/{sourceRegulationId}
                int sourceRegulationId = Integer.parseInt(pathParts[1]);
                
                JsonObject jsonObject = gson.fromJson(request.getReader(), JsonObject.class);
                JsonArray inserts = jsonObject.getAsJsonArray("inserts");
                JsonArray updates = jsonObject.getAsJsonArray("updates");
                JsonArray deletes = jsonObject.getAsJsonArray("deletes");
                
                // Process inserts
                if (inserts != null) {
                    for (JsonElement element : inserts) {
                        JsonObject insertObj = element.getAsJsonObject();
                        RegulationXRegulation relation = new RegulationXRegulation();
                        relation.setSourceRegulationId(sourceRegulationId);
                        relation.setTargetRegulationId(insertObj.get("targetRegulationId").getAsInt());
                        relation.setRelationType(insertObj.get("relationType").getAsInt());
                        relation.setDescription(insertObj.has("description") ? insertObj.get("description").getAsString() : null);
                        relation.setLastUpdateUserId(1); // TODO: Get from session

                        if (!validateRegulationRelationship(relation, response, out)) {
                            return;
                        }

                        service.createRegulationXRegulation(relation);
                    }
                }
                
                // Process updates
                if (updates != null) {
                    for (JsonElement element : updates) {
                        JsonObject updateObj = element.getAsJsonObject();
                        RegulationXRegulation relation = new RegulationXRegulation();
                        relation.setId(updateObj.get("id").getAsInt());
                        relation.setSourceRegulationId(sourceRegulationId);
                        relation.setTargetRegulationId(updateObj.get("targetRegulationId").getAsInt());
                        relation.setRelationType(updateObj.get("relationType").getAsInt());
                        relation.setDescription(updateObj.has("description") ? updateObj.get("description").getAsString() : null);
                        relation.setLastUpdateUserId(1); // TODO: Get from session

                        if (!validateRegulationRelationship(relation, response, out)) {
                            return;
                        }

                        service.updateRegulationXRegulation(relation);
                    }
                }
                
                // Process deletes
                if (deletes != null) {
                    for (JsonElement element : deletes) {
                        int id = element.getAsInt();
                        service.deleteRegulationXRegulation(id);
                    }
                }
                
                out.print("{\"success\": true}");
                
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid endpoint\"}");
            }
            
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\": \"Invalid regulation ID\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        try {
            String pathInfo = request.getPathInfo();
            
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Missing relation ID\"}");
                return;
            }
            
            int relationId = Integer.parseInt(pathInfo.substring(1));
            boolean deleted = service.deleteRegulationXRegulation(relationId);
            
            if (deleted) {
                out.print("{\"success\": true}");
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"error\": \"Relation not found\"}");
            }
            
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\": \"Invalid relation ID\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
    }

    private boolean validateRegulationRelationship(RegulationXRegulation relation, HttpServletResponse response, PrintWriter out) throws SQLException {
        SegmentValidationService.ValidationResult validationResult =
                segmentValidationService.validateCrossSegmentRelationship(
                        relation.getSourceRegulationId(), "Regulation",
                        relation.getTargetRegulationId(), "Regulation");
        if (!validationResult.isValid) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\": \"" + validationResult.message.replace("\"", "\\\"") + "\"}");
            return false;
        }
        return true;
    }
}
