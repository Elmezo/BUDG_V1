package com.example.budg_v2;

import com.example.budg_v2.model.PeopleLifecycleStatus;
import com.example.budg_v2.service.PeopleLifecycleStatusService;
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

@WebServlet(name = "PeopleLifecycleStatusServlet", urlPatterns = {"/api/people/lifecycle", "/api/people/lifecycle/*"})
public class PeopleLifecycleStatusServlet extends HttpServlet {

    private final PeopleLifecycleStatusService peopleLifecycleStatusService;

    public PeopleLifecycleStatusServlet() {
        this.peopleLifecycleStatusService = new PeopleLifecycleStatusService();
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
                List<PeopleLifecycleStatus> statuses = peopleLifecycleStatusService.getAllPeopleLifecycleStatusesForDropdown();
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (PeopleLifecycleStatus status : statuses) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", status.getId());
                    o.addProperty("name", status.getPrimaryName());
                    o.addProperty("primaryname", status.getPrimaryName());
                    o.addProperty("Primary_Name", status.getPrimaryName());
                    arr.add(o);
                }
                response.getWriter().write(arr.toString());
            } else if ("/search".equals(pathInfo)) {
                String searchQuery = request.getParameter("q");
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    searchPeopleLifecycleStatuses(response, searchQuery.trim());
                } else {
                    getAllPeopleLifecycleStatuses(response);
                }
            } else if (pathInfo != null && pathInfo.length() > 1) {
                String idParam = pathInfo.substring(1);
                if (idParam.matches("\\d+")) {
                    getPeopleLifecycleStatusById(response, Integer.parseInt(idParam));
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                }
            } else {
                getAllPeopleLifecycleStatuses(response);
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
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer userId = JsonUtil.getJsonInt(jsonData, "userId");

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            PeopleLifecycleStatus status = peopleLifecycleStatusService.createPeopleLifecycleStatus(
                primaryName, 
                description != null ? description : "", 
                userId != null ? userId : 1
            );
            response.getWriter().write(new com.google.gson.GsonBuilder().create().toJson(status));
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error creating people lifecycle status: " + e.getMessage(), 500);
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
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer userId = JsonUtil.getJsonInt(jsonData, "userId");

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            boolean updated = peopleLifecycleStatusService.updatePeopleLifecycleStatus(
                id, 
                primaryName, 
                description != null ? description : "", 
                userId != null ? userId : 1
            );
            if (updated) {
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "People lifecycle status updated successfully");
                response.getWriter().write(result.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "People lifecycle status not found or update failed", 404);
            }
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error updating people lifecycle status: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllPeopleLifecycleStatuses(HttpServletResponse response) throws IOException, SQLException {
        List<PeopleLifecycleStatus> statuses = peopleLifecycleStatusService.getAllPeopleLifecycleStatuses();
        response.getWriter().write(new com.google.gson.GsonBuilder().create().toJson(statuses));
    }

    private void getPeopleLifecycleStatusById(HttpServletResponse response, int id) throws IOException, SQLException {
        PeopleLifecycleStatus status = peopleLifecycleStatusService.getPeopleLifecycleStatusById(id);
        if (status != null) {
            response.getWriter().write(new com.google.gson.GsonBuilder().create().toJson(status));
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "People lifecycle status not found", 404);
        }
    }

    private void searchPeopleLifecycleStatuses(HttpServletResponse response, String searchQuery) throws IOException, SQLException {
        List<PeopleLifecycleStatus> statuses = peopleLifecycleStatusService.searchPeopleLifecycleStatuses(searchQuery);
        response.getWriter().write(new com.google.gson.GsonBuilder().create().toJson(statuses));
    }
}
