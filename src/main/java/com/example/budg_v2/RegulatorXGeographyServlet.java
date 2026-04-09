package com.example.budg_v2;

import com.example.budg_v2.dao.RegulatorXGeographyDAO;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/regulator-x-geography/*")
public class RegulatorXGeographyServlet extends HttpServlet {

    private final RegulatorXGeographyDAO dao = new RegulatorXGeographyDAO();
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Missing regulator ID", 400);
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");

            if (pathParts.length == 1) {
                // GET /api/regulator-x-geography/{regulatorId} - Get all geographies for a regulator
                int regulatorId = Integer.parseInt(pathParts[0]);
                List<Map<String, Object>> results = dao.getByRegulatorId(regulatorId);
                JsonUtil.sendJsonResponse(resp.getWriter(), results);
            } else {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid URL format", 400);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid regulator ID format", 400);
        } catch (SQLException e) {
          //  e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try {
            // Read JSON from request body
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = req.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> requestData = JsonUtil.fromJson(jsonBuffer.toString(), Map.class);

            assert requestData != null;
            int regulatorId = ((Number) requestData.get("regulatorId")).intValue();
            int geographyId = ((Number) requestData.get("geographyId")).intValue();
            String description = (String) requestData.get("description");
            Integer userId = requestData.get("userId") != null ? ((Number) requestData.get("userId")).intValue() : null;

            if (!validateRegulatorGeographyRelationship(regulatorId, geographyId, resp)) {
                return;
            }

            Map<String, Object> result = dao.create(regulatorId, geographyId, description, userId);
            JsonUtil.sendJsonResponse(resp.getWriter(), result);

        } catch (SQLException e) {
          //  e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
         //   e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid request data: " + e.getMessage(), 400);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //system.out.println("=== DELETE REQUEST RECEIVED ===");
        //system.out.println("Path Info: " + req.getPathInfo());
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                //system.out.println("ERROR: Missing relationship ID");
                JsonUtil.sendErrorResponse(resp.getWriter(), "Missing relationship ID", 400);
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");
            if (pathParts.length != 1) {
                //system.out.println("ERROR: Invalid URL format");
                JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid URL format", 400);
                return;
            }

            int relationshipId = Integer.parseInt(pathParts[0]);
            //system.out.println("Attempting to delete relationship ID: " + relationshipId);
            
            boolean deleted = dao.delete(relationshipId);
            //system.out.println("Delete result: " + deleted);

            if (deleted) {
                //system.out.println("Successfully deleted relationship ID: " + relationshipId);
                JsonUtil.sendJsonResponse(resp.getWriter(), Map.of("success", true));
            } else {
                //system.out.println("Failed to delete relationship ID: " + relationshipId + " (not found)");
                JsonUtil.sendErrorResponse(resp.getWriter(), "Relationship not found", 404);
            }

        } catch (NumberFormatException e) {
            //system.out.println("ERROR: Invalid relationship ID format: " + e.getMessage());
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid relationship ID format", 400);
        } catch (SQLException e) {
            //system.out.println("ERROR: Database error: " + e.getMessage());
           // e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try {
            // Expect path: /api/regulator-x-geography/{id}
            String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Missing relationship ID", 400);
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");
            if (pathParts.length != 1) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid URL format", 400);
                return;
            }

            int id = Integer.parseInt(pathParts[0]);

            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = req.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> requestData = JsonUtil.fromJson(jsonBuffer.toString(), Map.class);
            assert requestData != null;
            int regulatorId = ((Number) requestData.get("regulatorId")).intValue();
            int geographyId = ((Number) requestData.get("geographyId")).intValue();
            String description = (String) requestData.get("description");
            Integer userId = requestData.get("userId") != null ? ((Number) requestData.get("userId")).intValue() : null;

            if (!validateRegulatorGeographyRelationship(regulatorId, geographyId, resp)) {
                return;
            }

            boolean updated = dao.update(id, regulatorId, geographyId, description, userId);
            if (updated) {
                JsonUtil.sendJsonResponse(resp.getWriter(), Map.of("success", true));
            } else {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Relationship not found", 404);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid relationship ID format", 400);
        } catch (Exception e) {
           // e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid request data: " + e.getMessage(), 400);
        }
    }

    private boolean validateRegulatorGeographyRelationship(
            int regulatorId, int geographyId, HttpServletResponse resp) throws IOException, SQLException {
        SegmentValidationService.ValidationResult validationResult =
                segmentValidationService.validateCrossSegmentRelationship(
                        regulatorId, "Regulator", geographyId, "Geography");
        if (!validationResult.isValid) {
            JsonUtil.sendErrorResponse(resp.getWriter(), validationResult.message, 400);
            return false;
        }
        return true;
    }
}
