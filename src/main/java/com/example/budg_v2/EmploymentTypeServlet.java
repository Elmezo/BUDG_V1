package com.example.budg_v2;

import com.example.budg_v2.model.EmploymentType;
import com.example.budg_v2.service.EmploymentTypeService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet(name = "EmploymentTypeServlet", urlPatterns = {"/api/employment-type", "/api/employment-type/*"})
public class EmploymentTypeServlet extends HttpServlet {

    private final EmploymentTypeService employmentTypeService;

    public EmploymentTypeServlet() {
        this.employmentTypeService = new EmploymentTypeService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();

            if ("/list".equals(pathInfo)) {
                // Return simplified list with id and name for dropdown
                List<EmploymentType> employmentTypes = employmentTypeService.getAllEmploymentTypesForDropdown();
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (EmploymentType et : employmentTypes) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", et.getId());
                    o.addProperty("name", et.getPrimaryName());
                    o.addProperty("primaryname", et.getPrimaryName());
                    o.addProperty("primary_Name", et.getPrimaryName());
                    arr.add(o);
                }
                response.getWriter().write(arr.toString());
            } else if ("/search".equals(pathInfo)) {
                String searchQuery = request.getParameter("q");
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    searchEmploymentTypes(response, searchQuery.trim());
                } else {
                    getAllEmploymentTypes(response);
                }
            } else if (pathInfo != null && pathInfo.length() > 1) {
                String idParam = pathInfo.substring(1);
                if (idParam.matches("\\d+")) {
                    getEmploymentTypeById(response, Integer.parseInt(idParam));
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                }
            } else {
                getAllEmploymentTypes(response);
            }
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            JsonObject jsonData = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            String primaryName = JsonUtil.getJsonString(jsonData, "primaryName");
            Integer userId = JsonUtil.getJsonInt(jsonData, "userId");

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            EmploymentType employmentType = employmentTypeService.createEmploymentType(primaryName, userId != null ? userId : 1);
            response.getWriter().write(new com.google.gson.GsonBuilder().create().toJson(employmentType));
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error creating employment type: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                JsonUtil.sendErrorResponse(response.getWriter(), "ID is required for update", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int id = Integer.parseInt(idParam);
            JsonObject jsonData = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            String primaryName = JsonUtil.getJsonString(jsonData, "primaryName");
            Integer userId = JsonUtil.getJsonInt(jsonData, "userId");

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            boolean updated = employmentTypeService.updateEmploymentType(id, primaryName, userId != null ? userId : 1);
            if (updated) {
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "Employment type updated successfully");
                response.getWriter().write(result.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Employment type not found or update failed", 404);
            }
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error updating employment type: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllEmploymentTypes(HttpServletResponse response) throws IOException, SQLException {
        List<EmploymentType> employmentTypes = employmentTypeService.getAllEmploymentTypes();
        response.getWriter().write(new com.google.gson.GsonBuilder().create().toJson(employmentTypes));
    }

    private void getEmploymentTypeById(HttpServletResponse response, int id) throws IOException, SQLException {
        EmploymentType employmentType = employmentTypeService.getEmploymentTypeById(id);
        if (employmentType != null) {
            response.getWriter().write(new com.google.gson.GsonBuilder().create().toJson(employmentType));
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Employment type not found", 404);
        }
    }

    private void searchEmploymentTypes(HttpServletResponse response, String searchQuery) throws IOException, SQLException {
        List<EmploymentType> employmentTypes = employmentTypeService.searchEmploymentTypes(searchQuery);
        response.getWriter().write(new com.google.gson.GsonBuilder().create().toJson(employmentTypes));
    }
}
