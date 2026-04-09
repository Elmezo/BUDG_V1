package com.example.budg_v2;

import com.example.budg_v2.model.Viewing;
import com.example.budg_v2.service.ViewingService;
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

@WebServlet(name = "ViewingServlet", urlPatterns = {"/api/viewing", "/api/viewing/*"})
public class ViewingServlet extends HttpServlet {

    private final ViewingService viewingService;

    public ViewingServlet() {
        this.viewingService = new ViewingService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();

            if ("/dropdown".equals(pathInfo)) {
                getAllViewingsForDropdown(response);
            } else if ("/list".equals(pathInfo)) {
                // Return simplified list with id and name
                //system.out.println("ViewingServlet: Getting viewing list...");
                List<Viewing> viewings = viewingService.getAllViewingsForDropdown();
                //system.out.println("ViewingServlet: Retrieved " + viewings.size() + " viewings");
                
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Viewing v : viewings) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", v.getId());
                    o.addProperty("name", v.getName());
                    arr.add(o);
                    //system.out.println("ViewingServlet: Added viewing to response: " + v.getName());
                }
                com.google.gson.JsonArray result = arr;
                //system.out.println("ViewingServlet: Response: " + result.toString());
                response.getWriter().write(result.toString());
            } else if ("/search".equals(pathInfo)) {
                String searchQuery = request.getParameter("q");
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    searchViewings(response, searchQuery.trim());
                } else {
                    getAllViewings(response);
                }
            } else if (pathInfo != null && pathInfo.length() > 1) {
                String idParam = pathInfo.substring(1);
                if (idParam.matches("\\d+")) {
                    getViewingById(response, Integer.parseInt(idParam));
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                }
            } else {
                getAllViewings(response);
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

            String name = JsonUtil.getJsonString(jsonData, "name");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastupdateuser_id");

            if (name == null || name.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Name is required", 400);
                return;
            }

            Viewing newViewing = viewingService.createViewing(name.trim(), description, lastUpdateUserId);

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Viewing created successfully");
            successResponse.add("data", JsonParser.parseString(JsonUtil.toJson(newViewing)));

            response.getWriter().write(successResponse.toString());

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error creating viewing: " + e.getMessage(), 500);
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
                JsonUtil.sendErrorResponse(response.getWriter(), "Viewing ID is required for update", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int viewingId = Integer.parseInt(idParam);
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String name = JsonUtil.getJsonString(jsonData, "name");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer lastUpdateUserId = JsonUtil.getJsonInt(jsonData, "lastupdateuser_id");

            if (name == null || name.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Name is required", 400);
                return;
            }

            boolean updated = viewingService.updateViewing(viewingId, name.trim(), description, lastUpdateUserId);

            if (updated) {
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Viewing updated successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Viewing not found or update failed", 404);
            }

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error updating viewing: " + e.getMessage(), 500);
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
                JsonUtil.sendErrorResponse(response.getWriter(), "Viewing ID is required for deletion", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int viewingId = Integer.parseInt(idParam);
            Integer lastUpdateUserId = null; // You might want to get this from request or session
            boolean deleted = viewingService.deleteViewing(viewingId, lastUpdateUserId);

            if (deleted) {
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Viewing deleted successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Viewing not found or deletion failed", 404);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error deleting viewing: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllViewings(HttpServletResponse response) throws IOException, SQLException {
        List<Viewing> viewings = viewingService.getAllViewings();
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", viewings.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(viewings)));
        response.getWriter().write(jsonResponse.toString());
    }

    private void getAllViewingsForDropdown(HttpServletResponse response) throws IOException, SQLException {
        List<Viewing> viewings = viewingService.getAllViewingsForDropdown();
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", viewings.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(viewings)));
        response.getWriter().write(jsonResponse.toString());
    }

    private void getViewingById(HttpServletResponse response, int id) throws IOException, SQLException {
        Viewing viewing = viewingService.getViewingById(id);
        if (viewing != null) {
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", true);
            jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(viewing)));
            response.getWriter().write(jsonResponse.toString());
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Viewing not found", 404);
        }
    }

    private void searchViewings(HttpServletResponse response, String searchQuery) throws IOException, SQLException {
        List<Viewing> viewings = viewingService.searchViewings(searchQuery);
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", viewings.size());
        jsonResponse.addProperty("searchQuery", searchQuery);
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(viewings)));
        response.getWriter().write(jsonResponse.toString());
    }
}
