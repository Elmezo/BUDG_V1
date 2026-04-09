package com.example.budg_v2;

import com.example.budg_v2.model.Status;
import com.example.budg_v2.service.StatusService;
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

@WebServlet(name = "StatusServlet", urlPatterns = {"/api/status", "/api/status/*"})
public class StatusServlet extends HttpServlet {

    private final StatusService statusService;

    public StatusServlet() {
        this.statusService = new StatusService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();

            if ("/dropdown".equals(pathInfo)) {
                getAllStatusesForDropdown(response);
            } else if ("/list".equals(pathInfo)) {
                // Return simplified list with id and name
                List<Status> statuses = statusService.getAllStatusesForDropdown();
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Status s : statuses) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", s.getId());
                    o.addProperty("name", s.getPrimaryName());
                    arr.add(o);
                }
                com.google.gson.JsonArray result = arr;
                response.getWriter().write(result.toString());
            } else if ("/search".equals(pathInfo)) {
                String searchQuery = request.getParameter("q");
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    searchStatuses(response, searchQuery.trim());
                } else {
                    getAllStatuses(response);
                }
            } else if (pathInfo != null && pathInfo.length() > 1) {
                String idParam = pathInfo.substring(1);
                if (idParam.matches("\\d+")) {
                    getStatusById(response, Integer.parseInt(idParam));
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                }
            } else {
                getAllStatuses(response);
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

            Status newStatus = statusService.createStatus(primaryName.trim(), description, priority, lastUpdateUserId);

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Status created successfully");
            successResponse.add("data", JsonParser.parseString(JsonUtil.toJson(newStatus)));

            response.getWriter().write(successResponse.toString());

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error creating status: " + e.getMessage(), 500);
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
                JsonUtil.sendErrorResponse(response.getWriter(), "Status ID is required for update", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int statusId = Integer.parseInt(idParam);
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryname");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer priority = JsonUtil.getJsonInt(jsonData, "priority");
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastupdateuser_id");

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            boolean updated = statusService.updateStatus(statusId, primaryName.trim(), description, priority, lastUpdateUserId);

            if (updated) {
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Status updated successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Status not found or update failed", 404);
            }

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error updating status: " + e.getMessage(), 500);
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
                JsonUtil.sendErrorResponse(response.getWriter(), "Status ID is required for deletion", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int statusId = Integer.parseInt(idParam);
            boolean deleted = statusService.deleteStatus(statusId);

            if (deleted) {
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Status deleted successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Status not found or deletion failed", 404);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error deleting status: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllStatuses(HttpServletResponse response) throws IOException, SQLException {
        List<Status> statuses = statusService.getAllStatuses();
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", statuses.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(statuses)));
        response.getWriter().write(jsonResponse.toString());
    }

    private void getAllStatusesForDropdown(HttpServletResponse response) throws IOException, SQLException {
        List<Status> statuses = statusService.getAllStatusesForDropdown();
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", statuses.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(statuses)));
        response.getWriter().write(jsonResponse.toString());
    }

    private void getStatusById(HttpServletResponse response, int id) throws IOException, SQLException {
        Status status = statusService.getStatusById(id);
        if (status != null) {
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", true);
            jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(status)));
            response.getWriter().write(jsonResponse.toString());
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Status not found", 404);
        }
    }

    private void searchStatuses(HttpServletResponse response, String searchQuery) throws IOException, SQLException {
        List<Status> statuses = statusService.searchStatuses(searchQuery);
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", statuses.size());
        jsonResponse.addProperty("searchQuery", searchQuery);
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(statuses)));
        response.getWriter().write(jsonResponse.toString());
    }
}
