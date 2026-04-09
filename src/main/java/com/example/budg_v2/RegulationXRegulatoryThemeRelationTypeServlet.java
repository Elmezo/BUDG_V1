package com.example.budg_v2;

import com.example.budg_v2.model.RegulationXRegulatoryThemeRelationType;
import com.example.budg_v2.service.RegulationXRegulatoryThemeRelationTypeService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/api/regulation-x-regulatorytheme-relationtype/*")
public class RegulationXRegulatoryThemeRelationTypeServlet extends HttpServlet {

    private RegulationXRegulatoryThemeRelationTypeService relationTypeService;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        this.relationTypeService = new RegulationXRegulatoryThemeRelationTypeService();
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            
            if (pathInfo != null && pathInfo.equals("/list")) {
                // Get relation types for dropdown
                List<RegulationXRegulatoryThemeRelationType> relationTypes = relationTypeService.getAllRelationTypesForDropdown();
                
                JsonArray jsonArray = new JsonArray();
                for (RegulationXRegulatoryThemeRelationType relationType : relationTypes) {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("id", relationType.getId());
                    jsonObject.addProperty("primaryName", relationType.getPrimaryName());
                    jsonObject.addProperty("description", relationType.getDescription());
                    jsonArray.add(jsonObject);
                }
                
                response.getWriter().write(jsonArray.toString());
            } else if (pathInfo != null && pathInfo.length() > 1) {
                // Get specific relation type by ID
                String idStr = pathInfo.substring(1);
                try {
                    int id = Integer.parseInt(idStr);
                    RegulationXRegulatoryThemeRelationType relationType = relationTypeService.getRelationTypeById(id);
                    if (relationType != null) {
                        response.getWriter().write(gson.toJson(relationType));
                    } else {
                        JsonUtil.sendErrorResponse(response.getWriter(), "Relation type not found", 404);
                    }
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                }
            } else {
                // Get all relation types
                List<RegulationXRegulatoryThemeRelationType> relationTypes = relationTypeService.getAllRelationTypes();
                response.getWriter().write(gson.toJson(relationTypes));
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
}
