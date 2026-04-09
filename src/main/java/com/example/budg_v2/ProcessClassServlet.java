package com.example.budg_v2;

import com.example.budg_v2.model.ProcessClass;
import com.example.budg_v2.service.ProcessClassService;
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

@WebServlet(name = "ProcessClassServlet", urlPatterns = {"/api/process/class", "/api/process/class/*"})
public class ProcessClassServlet extends HttpServlet {

    private final ProcessClassService processClassService;

    public ProcessClassServlet() {
        this.processClassService = new ProcessClassService();
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
                List<ProcessClass> classes = processClassService.getAllProcessClassesForDropdown();
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (ProcessClass c : classes) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", c.getId());
                    o.addProperty("primaryname", c.getPrimaryName());
                    arr.add(o);
                }
                response.getWriter().write(arr.toString());
            } else if ("/search".equals(pathInfo)) {
                String searchQuery = request.getParameter("q");
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    searchProcessClasses(response, searchQuery.trim());
                } else {
                    getAllProcessClasses(response);
                }
            } else if (pathInfo != null && pathInfo.length() > 1) {
                String idParam = pathInfo.substring(1);
                if (idParam.matches("\\d+")) {
                    getProcessClassById(response, Integer.parseInt(idParam));
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                }
            } else {
                getAllProcessClasses(response);
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
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastupdateuser_id");

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            ProcessClass newClass = processClassService.createProcessClass(
                    primaryName.trim(), description, lastUpdateUserId);

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Process class created successfully");
            successResponse.add("data", JsonParser.parseString(JsonUtil.toJson(newClass)));

            response.getWriter().write(successResponse.toString());

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error creating process class: " + e.getMessage(), 500);
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
                JsonUtil.sendErrorResponse(response.getWriter(), "Process class ID is required for update", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int classId = Integer.parseInt(idParam);
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryname");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastupdateuser_id");

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            boolean updated = processClassService.updateProcessClass(
                    classId, primaryName.trim(), description, lastUpdateUserId);

            if (updated) {
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Process class updated successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Process class not found or update failed", 404);
            }

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error updating process class: " + e.getMessage(), 500);
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
                JsonUtil.sendErrorResponse(response.getWriter(), "Process class ID is required for deletion", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int classId = Integer.parseInt(idParam);
            boolean deleted = processClassService.deleteProcessClass(classId);

            if (deleted) {
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Process class deleted successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Process class not found or deletion failed", 404);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error deleting process class: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllProcessClasses(HttpServletResponse response) throws IOException, SQLException {
        List<ProcessClass> classes = processClassService.getAllProcessClasses();
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", classes.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(classes)));
        response.getWriter().write(jsonResponse.toString());
    }

    private void getProcessClassById(HttpServletResponse response, int id) throws IOException, SQLException {
        ProcessClass processClass = processClassService.getProcessClassById(id);
        if (processClass != null) {
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", true);
            jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(processClass)));
            response.getWriter().write(jsonResponse.toString());
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Process class not found", 404);
        }
    }

    private void searchProcessClasses(HttpServletResponse response, String searchQuery) throws IOException, SQLException {
        List<ProcessClass> classes = processClassService.searchProcessClasses(searchQuery);
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", classes.size());
        jsonResponse.addProperty("searchQuery", searchQuery);
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(classes)));
        response.getWriter().write(jsonResponse.toString());
    }
}
