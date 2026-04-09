package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.model.Geography;
import com.example.budg_v2.service.GeographyService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.UserContextUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/api/geography/*")
public class GeographyServlet extends HttpServlet {
    private GeographyService geographyService;
    private SegmentDAO segmentDAO;
    private Gson gson;
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();

    @Override
    public void init() throws ServletException {
        super.init();
        geographyService = new GeographyService();
        segmentDAO = new SegmentDAO();
        gson = new GsonBuilder().serializeNulls().create();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        //system.out.println("GeographyServlet: doGet called with pathInfo: " + pathInfo);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            
            if (pathInfo == null || pathInfo.equals("/")) {
                // Get all geographies filtered by segment access
                List<Geography> geographies = (userId > 0) 
                    ? geographyService.getAllGeographies(userId) 
                    : geographyService.getAllGeographies();
                response.getWriter().write(gson.toJson(geographies));
            } else if (pathInfo.equals("/list")) {
                // Get geographies for dropdown - optionally filtered by segment (e.g. for legal entity impact)
                String segmentIdParam = request.getParameter("segmentId");
                List<Geography> geographies;
                if (segmentIdParam != null && !segmentIdParam.isEmpty()) {
                    try {
                        int segmentId = Integer.parseInt(segmentIdParam);
                        geographies = geographyService.getGeographiesForDropdownBySegment(segmentId);
                        if (userId > 0 && !geographies.isEmpty()) {
                            java.util.List<Integer> geographyIds = geographies.stream()
                                .map(Geography::getId)
                                .collect(java.util.stream.Collectors.toList());
                            java.util.Set<Integer> accessibleIds =
                                SegmentAccessService.filterBySelectedSegments(userId, "Geography", geographyIds);
                            geographies = geographies.stream()
                                .filter(g -> accessibleIds.contains(g.getId()))
                                .collect(java.util.stream.Collectors.toList());
                        }
                    } catch (NumberFormatException e) {
                        geographies = (userId > 0)
                            ? geographyService.getGeographiesForDropdown(userId)
                            : geographyService.getGeographiesForDropdown();
                    }
                } else {
                    geographies = (userId > 0)
                        ? geographyService.getGeographiesForDropdown(userId)
                        : geographyService.getGeographiesForDropdown();
                }
                response.getWriter().write(gson.toJson(geographies));
            } else if (pathInfo.equals("/parent-picker")) {
                // Get geographies for parent picker - use standard serialization for consistency
                List<Geography> geographies = (userId > 0)
                    ? geographyService.getGeographiesForDropdown(userId)
                    : geographyService.getGeographiesForDropdown();
                //system.out.println("GeographyServlet /parent-picker - geographies count: " + geographies.size());
                response.getWriter().write(gson.toJson(geographies));
            } else if (pathInfo.matches("/\\d+")) {
                // Get geography by ID
                try {
                    int id = Integer.parseInt(pathInfo.substring(1)); // Remove leading "/"
                Geography geography = geographyService.getGeographyById(id);
                if (geography != null) {
                    // Debug logging
                    System.out.println("[GeographyServlet] Geography ID: " + id + 
                                     ", LastUpdate_UserID: " + geography.getLastUpdateUserId() + 
                                     ", lastUpdatedByName: " + geography.getLastUpdatedByName());
                    
                    JsonObject geographyJson = JsonParser.parseString(gson.toJson(geography)).getAsJsonObject();
                    
                    // Debug: Check if fields are in JSON
                    System.out.println("[GeographyServlet] JSON contains lastUpdateUserId: " + geographyJson.has("lastUpdateUserId"));
                    System.out.println("[GeographyServlet] JSON contains lastUpdatedByName: " + geographyJson.has("lastUpdatedByName"));
                    if (geographyJson.has("lastUpdateUserId")) {
                        System.out.println("[GeographyServlet] JSON lastUpdateUserId value: " + geographyJson.get("lastUpdateUserId"));
                    }
                    if (geographyJson.has("lastUpdatedByName")) {
                        System.out.println("[GeographyServlet] JSON lastUpdatedByName value: " + geographyJson.get("lastUpdatedByName"));
                    }
                    
                    SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "Geography");
                    SegmentResponseUtil.applySegmentInfo(geographyJson, segmentInfo, request);
                    response.getWriter().write(geographyJson.toString());
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Geography not found");
                        response.getWriter().write(gson.toJson(error));
                    }
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid geography ID");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.startsWith("/search/")) {
                // Search geographies
                String searchTerm = pathInfo.substring(8); // Remove "/search/" prefix
                List<Geography> geographies = (userId > 0)
                    ? geographyService.searchGeographies(searchTerm, userId)
                    : geographyService.searchGeographies(searchTerm);
                response.getWriter().write(gson.toJson(geographies));
            } else if (pathInfo.startsWith("/")) {
                // Get geography by ID
                try {
                    int id = Integer.parseInt(pathInfo.substring(1));
                Geography geography = geographyService.getGeographyById(id);
                if (geography != null) {
                    JsonObject geographyJson = JsonParser.parseString(gson.toJson(geography)).getAsJsonObject();
                    SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "Geography");
                    SegmentResponseUtil.applySegmentInfo(geographyJson, segmentInfo, request);
                    response.getWriter().write(geographyJson.toString());
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Geography not found");
                        response.getWriter().write(gson.toJson(error));
                    }
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid geography ID");
                    response.getWriter().write(gson.toJson(error));
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        // Check create permission for new geography creation
        if (!PermissionCheckUtil.checkCreatePermission(request, response, "Geography")) {
            return; // Response already sent
        }

        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuffer.append(line);
            }

            String jsonData = jsonBuffer.toString();
            //system.out.println("GeographyServlet POST - Received JSON: " + jsonData);
            //system.out.println("GeographyServlet POST - JSON length: " + jsonData.length());
            //system.out.println("GeographyServlet POST - JSON is empty: " + jsonData.trim().isEmpty());

            if (jsonData == null || jsonData.trim().isEmpty()) {
                //system.out.println("GeographyServlet POST - Empty JSON data received");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Empty JSON data received");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            Geography geography;
            try {
                geography = gson.fromJson(jsonData, Geography.class);
                //system.out.println("GeographyServlet POST - Successfully parsed Geography object");
            } catch (Exception e) {
                //system.out.println("GeographyServlet POST - JSON parsing error: " + e.getMessage());
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid JSON format: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
                return;
            }

            //system.out.println("GeographyServlet POST - Parsed Geography:");
            //system.out.println("- PrimaryName: " + geography.getPrimaryName());
            //system.out.println("- Description: " + geography.getDescription());
            //system.out.println("- ParentId: " + geography.getParentId());
            long segmentIdForName = 1L;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    segmentIdForName = jsonObj.get("segmentId").getAsLong();
                }
            } catch (Exception ignored) {
            }
            try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Geography", geography.getPrimaryName(), segmentIdForName,
                        null)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "A geography with this name already exists in this segment.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            int geographyId = geographyService.createGeography(geography, request);
            //system.out.println("GeographyServlet POST - Created geography with ID: " + geographyId);

            // Assign geography to segment - parse segmentId from JSON
            Integer segmentId = 1; // Default to Enterprise segment
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    segmentId = jsonObj.get("segmentId").getAsInt();
                }
            } catch (Exception ignored) {}
            int userId = UserContextUtil.getCurrentUserId(request);
            try {
                segmentDAO.assignObjectToSegment(segmentId, geographyId, "Geography", userId > 0 ? userId : 1);
                //system.out.println("✅ Geography " + geographyId + " assigned to segment " + segmentId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning geography to segment: " + e.getMessage());
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Geography created successfully");
            responseJson.addProperty("id", geographyId);

            response.getWriter().write(gson.toJson(responseJson));
        } catch (IllegalArgumentException e) {
            //system.out.println("GeographyServlet POST - Validation Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            //system.out.println("GeographyServlet POST - SQL Error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error creating geography: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            //system.out.println("GeographyServlet POST - General Error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid request data: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        // Check role-based edit permission first
        if (!PermissionCheckUtil.checkEditPermission(request, response, "Geography")) {
            return; // Response already sent
        }

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Geography ID required for update");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int id = Integer.parseInt(pathInfo.substring(1));
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuffer.append(line);
            }

            String jsonData = jsonBuffer.toString();
            //system.out.println("Received JSON data: " + jsonData);

            Geography geography = gson.fromJson(jsonData, Geography.class);
            geography.setId(id);
            long effectiveSegmentId = 1L;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    effectiveSegmentId = jsonObj.get("segmentId").getAsLong();
                } else {
                    int cur = segmentDAO.getObjectSegmentId(id, "Geography");
                    if (cur > 0) {
                        effectiveSegmentId = cur;
                    }
                }
            } catch (Exception ignored) {
            }
            try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Geography", geography.getPrimaryName(), effectiveSegmentId,
                        id)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "A geography with this name already exists in this segment.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            // Check segment-based edit permission
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(currentUserId, id, "Geography");
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Access denied. You don't have permission to edit this geography.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
                
                // Set LastUpdate_UserID from current user if not provided in request
                if (geography.getLastUpdateUserId() == null || geography.getLastUpdateUserId() <= 0) {
                    geography.setLastUpdateUserId(currentUserId);
                    System.out.println("[GeographyServlet] Set LastUpdate_UserID to current user: " + currentUserId);
                }
            }

            boolean success = geographyService.updateGeography(geography, request);

            // Update segment assignment if provided - even if the base update returned false.
            // This fixes the case where the user only changes the segment field.
            boolean segmentUpdated = false;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    Integer segmentId = jsonObj.get("segmentId").getAsInt();
                    int curSegmentId = segmentDAO.getObjectSegmentId(id, "Geography");
                    if (curSegmentId != segmentId) {
                        SegmentValidationService.ValidationResult validationResult =
                            segmentValidationService.validateSegmentMove(id, segmentId, "Geography", null);
                        if (!validationResult.isValid) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            JsonObject error = new JsonObject();
                            error.addProperty("success", false);
                            error.addProperty("error", validationResult.message);
                            response.getWriter().write(gson.toJson(error));
                            return;
                        }

                        if (curSegmentId > 0) {
                            segmentDAO.removeObjectFromSegment(curSegmentId, id, "Geography", currentUserId > 0 ? currentUserId : 1);
                        }
                        segmentDAO.assignObjectToSegment(segmentId, id, "Geography", currentUserId > 0 ? currentUserId : 1);
                        System.out.println("✅ Geography " + id + " segment changed from " + curSegmentId + " to " + segmentId);
                        segmentUpdated = true;
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Error updating geography segment: " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("error", "Error updating geography segment: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
                return;
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success || segmentUpdated);
            responseJson.addProperty("message", (success || segmentUpdated) ? "Geography updated successfully" : "Failed to update geography");

            response.getWriter().write(gson.toJson(responseJson));
        } catch (IllegalArgumentException e) {
            //system.out.println("GeographyServlet PUT - Validation Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error updating geography: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            //system.out.println("Exception in doPut: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid request data: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Geography ID required for deletion");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int id = Integer.parseInt(pathInfo.substring(1));

            boolean success = geographyService.deleteGeography(id, request);

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success);
            responseJson.addProperty("message", success ? "Geography deleted successfully" : "Failed to delete geography");

            response.getWriter().write(gson.toJson(responseJson));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error deleting geography: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid request data: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
