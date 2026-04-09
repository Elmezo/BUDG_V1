package com.example.budg_v2;

import com.example.budg_v2.model.RegulationXRegulatorXGeography;
import com.example.budg_v2.service.RegulationXRegulatorXGeographyService;
import com.example.budg_v2.service.RegulationGeographyService;
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

@WebServlet("/api/regulation-x-regulator-x-geography/*")
public class RegulationXRegulatorXGeographyServlet extends HttpServlet {
    
    private RegulationXRegulatorXGeographyService service = new RegulationXRegulatorXGeographyService();
    private RegulationGeographyService geographyService = new RegulationGeographyService();
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
            
            if ("regulation".equals(action) && pathParts.length > 1) {
                // GET /api/regulation-x-regulator-x-geography/regulation/{id}
                // GET /api/regulation-x-regulator-x-geography/regulation/{id}?edit=true
                // If edit=true, returns only this regulation's own data (no inheritance)
                // Otherwise, returns with inheritance from parent regulations
                int regulationId = Integer.parseInt(pathParts[1]);
                String editParam = request.getParameter("edit");
                boolean isEditMode = "true".equals(editParam);
                
                try {
                    List<Map<String, Object>> geographies;
                    if (isEditMode) {
                        // Edit mode: only return this regulation's own data
                        geographies = geographyService.getGeographiesByRegulationId(regulationId);
                    } else {
                        // View mode: include inheritance
                        geographies = geographyService.getGeographiesWithInheritance(regulationId);
                    }
                    out.print(gson.toJson(geographies));
                } catch (SQLException e) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
                }
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
            
            if ("regulation".equals(action) && pathParts.length > 1) {
                // PUT /api/regulation-x-regulator-x-geography/regulation/{regulationId}
                Integer.parseInt(pathParts[1]); // regulationId from path (validates format)
                
                JsonObject jsonObject = gson.fromJson(request.getReader(), JsonObject.class);
                JsonArray inserts = jsonObject.getAsJsonArray("inserts");
                JsonArray updates = jsonObject.getAsJsonArray("updates");
                JsonArray deletes = jsonObject.getAsJsonArray("deletes");
                
                //system.out.println("=== REGULATION X REGULATOR X GEOGRAPHY PUT REQUEST ===");
                //system.out.println("Regulation ID: " + regulationId);
                //system.out.println("Inserts: " + (inserts != null ? inserts.size() : 0));
                //system.out.println("Updates: " + (updates != null ? updates.size() : 0));
                //system.out.println("Deletes: " + (deletes != null ? deletes.size() : 0));
                
                // Process inserts
                if (inserts != null && inserts.size() > 0) {
                    //system.out.println("Processing " + inserts.size() + " geography inserts...");
                    for (JsonElement element : inserts) {
                        JsonObject insertObj = element.getAsJsonObject();
                        //system.out.println("Insert data: " + insertObj.toString());
                        RegulationXRegulatorXGeography geography = new RegulationXRegulatorXGeography();
                        geography.setRegulationXRegulatorId(insertObj.get("regulationXRegulatorId").getAsInt());
                        geography.setRegulatorXGeographyId(insertObj.get("regulatorXGeographyId").getAsInt());
                        geography.setRelationType(insertObj.has("relationType") ? insertObj.get("relationType").getAsInt() : 1);
                        geography.setDescription(insertObj.has("description") ? insertObj.get("description").getAsString() : null);
                        service.createRegulationXRegulatorXGeography(geography);
                    }
                }
                
                // Process updates
                if (updates != null) {
                    for (JsonElement element : updates) {
                        JsonObject updateObj = element.getAsJsonObject();
                        RegulationXRegulatorXGeography geography = new RegulationXRegulatorXGeography();
                        geography.setId(updateObj.get("id").getAsInt());
                        geography.setRegulationXRegulatorId(updateObj.get("regulationXRegulatorId").getAsInt());
                        geography.setRegulatorXGeographyId(updateObj.get("regulatorXGeographyId").getAsInt());
                        geography.setRelationType(updateObj.has("relationType") ? updateObj.get("relationType").getAsInt() : 1);
                        geography.setDescription(updateObj.has("description") ? updateObj.get("description").getAsString() : null);
                        service.updateRegulationXRegulatorXGeography(geography);
                    }
                }
                
                // Process deletes
                if (deletes != null) {
                    for (JsonElement element : deletes) {
                        int id = element.getAsInt();
                        service.deleteRegulationXRegulatorXGeography(id);
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
}
