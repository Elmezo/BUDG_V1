package com.example.budg_v2;
import com.example.budg_v2.model.RegulationXRegulator;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.service.RegulationXRegulatorService;
import com.example.budg_v2.util.JsonUtil;
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

@WebServlet("/api/regulation-x-regulator/*")
public class RegulationXRegulatorServlet extends HttpServlet {
    private final RegulationXRegulatorService service;
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();
    private final Gson gson = new Gson();
    
    public RegulationXRegulatorServlet() {
        this.service = new RegulationXRegulatorService();
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo != null && pathInfo.matches("/\\d+")) {
                // GET /api/regulation-x-regulator/{regulationId}
                int regulationId = Integer.parseInt(pathInfo.substring(1));
                //system.out.println("=== GET REGULATORS (NO INHERITANCE) ===");
                //system.out.println("Regulation ID: " + regulationId);
                List<Map<String, Object>> regulators = service.getRegulatorsByRegulationId(regulationId);
                //system.out.println("Found " + regulators.size() + " regulators");
                if (regulators.size() > 0) {
                    //system.out.println("First regulator: " + regulators.get(0));
                }
                response.getWriter().write(JsonUtil.toJson(regulators));
            } else if (pathInfo != null && pathInfo.startsWith("/regulation/")) {
                // GET /api/regulation-x-regulator/regulation/{regulationId}
                // This endpoint returns regulators WITH inheritance from parent regulations
                String[] pathParts = pathInfo.substring(1).split("/");
                if (pathParts.length > 1) {
                    int regulationId = Integer.parseInt(pathParts[1]);
                    List<Map<String, Object>> regulators = service.getRegulatorsByRegulationIdWithInheritance(regulationId);
                    response.getWriter().write(JsonUtil.toJson(regulators));
                } else {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"error\":\"Invalid endpoint\"}");
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Invalid endpoint\"}");
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid regulation ID\"}");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal server error: " + e.getMessage() + "\"}");
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
            
            if (pathInfo == null || !pathInfo.startsWith("/regulation/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Missing regulation ID\"}");
                return;
            }
            
            String[] pathParts = pathInfo.substring(1).split("/");
            if (pathParts.length < 2) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid endpoint\"}");
                return;
            }
            
            // PUT /api/regulation-x-regulator/regulation/{regulationId}
            int regulationId = Integer.parseInt(pathParts[1]);
            
            // Get username for audit tracking
            String userName = getCurrentUserName(request);
            
            JsonObject jsonObject = gson.fromJson(request.getReader(), JsonObject.class);
            JsonArray inserts = jsonObject.getAsJsonArray("inserts");
            JsonArray updates = jsonObject.getAsJsonArray("updates");
            JsonArray deletes = jsonObject.getAsJsonArray("deletes");
            
            //system.out.println("=== REGULATION X REGULATOR PUT REQUEST ===");
            //system.out.println("Regulation ID: " + regulationId);
            //system.out.println("Inserts: " + (inserts != null ? inserts.size() : 0));
            //system.out.println("Updates: " + (updates != null ? updates.size() : 0));
            //system.out.println("Deletes: " + (deletes != null ? deletes.size() : 0));
            
            // Process inserts
            if (inserts != null && inserts.size() > 0) {
                //system.out.println("Processing " + inserts.size() + " inserts...");
                for (JsonElement element : inserts) {
                    JsonObject insertObj = element.getAsJsonObject();
                    //system.out.println("Insert data: " + insertObj.toString());
                    RegulationXRegulator regulator = new RegulationXRegulator();
                    regulator.setRegulationId(regulationId);
                    regulator.setRegulatorId(insertObj.get("regulatorId").getAsInt());
                    regulator.setRelationType(insertObj.get("relationType").getAsInt());
                    regulator.setDescription(insertObj.has("description") ? insertObj.get("description").getAsString() : null);

                    if (!validateRegulationRegulatorRelationship(regulator, response, out)) {
                        return;
                    }

                    int createdId = service.createRegulationXRegulatorWithAudit(regulator, userName);
                    //system.out.println("Created regulation_x_regulator with ID: " + createdId);
                }
            }
            
            // Process updates
            if (updates != null) {
                for (JsonElement element : updates) {
                    JsonObject updateObj = element.getAsJsonObject();
                    RegulationXRegulator regulator = new RegulationXRegulator();
                    regulator.setId(updateObj.get("id").getAsInt());
                    regulator.setRegulationId(regulationId);
                    regulator.setRegulatorId(updateObj.get("regulatorId").getAsInt());
                    regulator.setRelationType(updateObj.get("relationType").getAsInt());
                    regulator.setDescription(updateObj.has("description") ? updateObj.get("description").getAsString() : null);

                    if (!validateRegulationRegulatorRelationship(regulator, response, out)) {
                        return;
                    }

                    service.updateRegulationXRegulatorWithAudit(regulator, userName);
                }
            }
            
            // Process deletes
            if (deletes != null) {
                for (JsonElement element : deletes) {
                    int id = element.getAsInt();
                    service.deleteRegulationXRegulatorWithAudit(id, userName);
                }
            }
            
            out.print("{\"success\": true}");
            
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\": \"Invalid regulation ID\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
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

    private boolean validateRegulationRegulatorRelationship(
            RegulationXRegulator regulator, HttpServletResponse response, PrintWriter out) throws SQLException {
        SegmentValidationService.ValidationResult validationResult =
                segmentValidationService.validateCrossSegmentRelationship(
                        regulator.getRegulationId(), "Regulation",
                        regulator.getRegulatorId(), "Regulator");
        if (!validationResult.isValid) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\": \"" + validationResult.message.replace("\"", "\\\"") + "\"}");
            return false;
        }
        return true;
    }
}
