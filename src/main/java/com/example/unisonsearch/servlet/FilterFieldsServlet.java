package com.example.unisonsearch.servlet;

import com.example.budg_v2.util.CorsUtil;
import com.example.unisonsearch.config.FilterMetadataConfig;
import com.example.unisonsearch.model.FilterField;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Servlet to return available filter fields for a given facet.
 * Endpoint: /UnisonSearch/api/filter-fields?facetId=DATASET
 */
@WebServlet(name = "FilterFieldsServlet", urlPatterns = {"/UnisonSearch/api/filter-fields"})
public class FilterFieldsServlet extends HttpServlet {
    
    private final Gson gson = new Gson();
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);
        
        String facetId = req.getParameter("facetId");
        
        if (facetId == null || facetId.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "facetId parameter is required");
            resp.getWriter().write(gson.toJson(error));
            return;
        }
        
        try {
            List<FilterField> filterFields = FilterMetadataConfig.getFiltersForFacet(facetId);
            
            JsonObject response = new JsonObject();
            response.addProperty("facetId", facetId.toUpperCase());
            
            JsonArray fieldsArray = new JsonArray();
            for (FilterField field : filterFields) {
                JsonObject fieldObj = new JsonObject();
                fieldObj.addProperty("id", field.getId());
                fieldObj.addProperty("name", field.getName());
                fieldObj.addProperty("type", field.getType().toString());
                fieldObj.addProperty("multiSelect", field.isMultiSelect());
                
                if (field.getLookupTable() != null) {
                    fieldObj.addProperty("lookupTable", field.getLookupTable());
                    // Add endpoint for fetching values
                    fieldObj.addProperty("valuesEndpoint", 
                        "/UnisonSearch/api/filter-values/" + field.getId() + "?facetId=" + facetId);
                }
                
                if (field.getFieldName() != null) {
                    fieldObj.addProperty("fieldName", field.getFieldName());
                }
                
                fieldsArray.add(fieldObj);
            }
            
            response.add("filterFields", fieldsArray);
            
            resp.getWriter().write(gson.toJson(response));
            
        } catch (Exception e) {
            System.err.println("[FilterFieldsServlet] ERROR: " + e.getMessage());
            e.printStackTrace();
            
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to fetch filter fields: " + e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
}
