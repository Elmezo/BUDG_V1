package com.example.budg_v2;

import com.example.budg_v2.model.ProcessDurationType;
import com.example.budg_v2.service.ProcessDurationTypeService;
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

@WebServlet(name = "ProcessDurationTypeServlet", urlPatterns = {"/api/process/duration-type", "/api/process/duration-type/*"})
public class ProcessDurationTypeServlet extends HttpServlet {

    private final ProcessDurationTypeService processDurationTypeService;

    public ProcessDurationTypeServlet() {
        this.processDurationTypeService = new ProcessDurationTypeService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();

            if ("/list".equals(pathInfo)) {
                // Return simplified list with id and primaryname for dropdown
                List<ProcessDurationType> durationTypes = processDurationTypeService.getAllProcessDurationTypesForDropdown();
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (ProcessDurationType dt : durationTypes) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", dt.getId());
                    o.addProperty("primaryname", dt.getPrimaryName());
                    arr.add(o);
                }
                response.getWriter().write(arr.toString());
            } else if ("/search".equals(pathInfo)) {
                String searchQuery = request.getParameter("q");
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    searchProcessDurationTypes(response, searchQuery.trim());
                } else {
                    getAllProcessDurationTypes(response);
                }
            } else if (pathInfo != null && pathInfo.length() > 1) {
                String idParam = pathInfo.substring(1);
                if (idParam.matches("\\d+")) {
                    getProcessDurationTypeById(response, Integer.parseInt(idParam));
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                }
            } else {
                getAllProcessDurationTypes(response);
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
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryname");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer priority = JsonUtil.getJsonInt(jsonData, "priority");
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastupdateuser_id");

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            ProcessDurationType newDurationType = processDurationTypeService.createProcessDurationType(
                    primaryName.trim(), description, priority, lastUpdateUserId);

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Process duration type created successfully");
            successResponse.add("data", JsonParser.parseString(JsonUtil.toJson(newDurationType)));

            response.getWriter().write(successResponse.toString());

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error creating process duration type: " + e.getMessage(), 500);
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
                JsonUtil.sendErrorResponse(response.getWriter(), "Process duration type ID is required for update", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int durationTypeId = Integer.parseInt(idParam);
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryname");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer priority = JsonUtil.getJsonInt(jsonData, "priority");
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastupdateuser_id");

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            boolean updated = processDurationTypeService.updateProcessDurationType(
                    durationTypeId, primaryName.trim(), description, priority, lastUpdateUserId);

            if (updated) {
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Process duration type updated successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Process duration type not found or update failed", 404);
            }

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error updating process duration type: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Process duration type ID is required for deletion", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int durationTypeId = Integer.parseInt(idParam);
            boolean deleted = processDurationTypeService.deleteProcessDurationType(durationTypeId);

            if (deleted) {
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Process duration type deleted successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Process duration type not found or deletion failed", 404);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error deleting process duration type: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllProcessDurationTypes(HttpServletResponse response) throws IOException, SQLException {
        List<ProcessDurationType> durationTypes = processDurationTypeService.getAllProcessDurationTypes();
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", durationTypes.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(durationTypes)));
        response.getWriter().write(jsonResponse.toString());
    }

    private void getProcessDurationTypeById(HttpServletResponse response, int id) throws IOException, SQLException {
        ProcessDurationType durationType = processDurationTypeService.getProcessDurationTypeById(id);
        if (durationType != null) {
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", true);
            jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(durationType)));
            response.getWriter().write(jsonResponse.toString());
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Process duration type not found", 404);
        }
    }

    private void searchProcessDurationTypes(HttpServletResponse response, String searchQuery) throws IOException, SQLException {
        List<ProcessDurationType> durationTypes = processDurationTypeService.searchProcessDurationTypes(searchQuery);
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", durationTypes.size());
        jsonResponse.addProperty("searchQuery", searchQuery);
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(durationTypes)));
        response.getWriter().write(jsonResponse.toString());
    }
}
