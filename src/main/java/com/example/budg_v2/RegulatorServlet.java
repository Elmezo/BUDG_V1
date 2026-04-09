package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.model.Regulator;
import com.example.budg_v2.service.RegulatorService;
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

@WebServlet("/api/regulator/*")
public class RegulatorServlet extends HttpServlet {
    private RegulatorService regulatorService;
    private SegmentDAO segmentDAO;
    private Gson gson;
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();

    @Override
    public void init() throws ServletException {
        super.init();
        regulatorService = new RegulatorService();
        segmentDAO = new SegmentDAO();
        gson = new GsonBuilder().serializeNulls().create();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        //system.out.println("RegulatorServlet: doGet called with pathInfo: " + pathInfo);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            
            if (pathInfo == null || pathInfo.equals("/")) {
                // Get all regulators filtered by segment access
                List<Regulator> regulators = (userId > 0)
                    ? regulatorService.getAllRegulators(userId)
                    : regulatorService.getAllRegulators();
                response.getWriter().write(gson.toJson(regulators));
            } else if (pathInfo.equals("/list")) {
                // Get regulators for dropdown
                List<Regulator> regulators = (userId > 0)
                    ? regulatorService.getRegulatorsForDropdown(userId)
                    : regulatorService.getRegulatorsForDropdown();
                //system.out.println("RegulatorServlet /list - regulators count: " + regulators.size());

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Regulator r : regulators) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", r.getId());
                    o.addProperty("primaryname", r.getPrimaryName());
                    o.addProperty("shortname", r.getShortName());
                    o.addProperty("description", r.getDescription());
                    arr.add(o);
                    //system.out.println("RegulatorServlet: Added regulator to response: " + r.getPrimaryName());
                }
                response.getWriter().write(arr.toString());
            } else if (pathInfo.matches("/\\d+")) {
                // Get regulator by ID
                try {
                    int id = Integer.parseInt(pathInfo.substring(1)); // Remove leading "/"
                    Regulator regulator = regulatorService.getRegulatorById(id);
                    if (regulator != null) {
                        // Debug logging
                        System.out.println("[RegulatorServlet] Regulator ID: " + id + 
                                         ", LastUpdate_UserID: " + regulator.getLastUpdateUserId() + 
                                         ", lastUpdatedByName: " + regulator.getLastUpdatedByName());
                        
                        JsonObject regulatorJson = JsonParser.parseString(gson.toJson(regulator)).getAsJsonObject();
                        
                        // Debug: Check if fields are in JSON
                        System.out.println("[RegulatorServlet] JSON contains lastUpdateUserId: " + regulatorJson.has("lastUpdateUserId"));
                        System.out.println("[RegulatorServlet] JSON contains lastUpdatedByName: " + regulatorJson.has("lastUpdatedByName"));
                        if (regulatorJson.has("lastUpdateUserId")) {
                            System.out.println("[RegulatorServlet] JSON lastUpdateUserId value: " + regulatorJson.get("lastUpdateUserId"));
                        }
                        if (regulatorJson.has("lastUpdatedByName")) {
                            System.out.println("[RegulatorServlet] JSON lastUpdatedByName value: " + regulatorJson.get("lastUpdatedByName"));
                        }
                        
                        SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "Regulator");
                        SegmentResponseUtil.applySegmentInfo(regulatorJson, segmentInfo, request);
                        response.getWriter().write(regulatorJson.toString());
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Regulator not found");
                        response.getWriter().write(gson.toJson(error));
                    }
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid regulator ID");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.startsWith("/search/")) {
                // Search regulators
                String searchTerm = pathInfo.substring(8); // Remove "/search/" prefix
                List<Regulator> regulators = (userId > 0)
                    ? regulatorService.searchRegulators(searchTerm, userId)
                    : regulatorService.searchRegulators(searchTerm);
                response.getWriter().write(gson.toJson(regulators));
            } else if (pathInfo.startsWith("/")) {
                // Get regulator by ID
                try {
                    int id = Integer.parseInt(pathInfo.substring(1));
                    Regulator regulator = regulatorService.getRegulatorById(id);
                    if (regulator != null) {
                        // Debug logging
                        System.out.println("[RegulatorServlet] Regulator ID: " + id + 
                                         ", LastUpdate_UserID: " + regulator.getLastUpdateUserId() + 
                                         ", lastUpdatedByName: " + regulator.getLastUpdatedByName());
                        
                        JsonObject regulatorJson = JsonParser.parseString(gson.toJson(regulator)).getAsJsonObject();
                        
                        // Debug: Check if fields are in JSON
                        System.out.println("[RegulatorServlet] JSON contains lastUpdateUserId: " + regulatorJson.has("lastUpdateUserId"));
                        System.out.println("[RegulatorServlet] JSON contains lastUpdatedByName: " + regulatorJson.has("lastUpdatedByName"));
                        if (regulatorJson.has("lastUpdateUserId")) {
                            System.out.println("[RegulatorServlet] JSON lastUpdateUserId value: " + regulatorJson.get("lastUpdateUserId"));
                        }
                        if (regulatorJson.has("lastUpdatedByName")) {
                            System.out.println("[RegulatorServlet] JSON lastUpdatedByName value: " + regulatorJson.get("lastUpdatedByName"));
                        }
                        
                        SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "Regulator");
                        SegmentResponseUtil.applySegmentInfo(regulatorJson, segmentInfo, request);
                        response.getWriter().write(regulatorJson.toString());
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Regulator not found");
                        response.getWriter().write(gson.toJson(error));
                    }
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid regulator ID");
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

        // Check create permission for new regulator creation
        if (!PermissionCheckUtil.checkCreatePermission(request, response, "Regulator")) {
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
            //system.out.println("RegulatorServlet POST - Received JSON: " + jsonData);
            //system.out.println("RegulatorServlet POST - JSON length: " + jsonData.length());
            //system.out.println("RegulatorServlet POST - JSON is empty: " + jsonData.trim().isEmpty());

            if (jsonData == null || jsonData.trim().isEmpty()) {
                //system.out.println("RegulatorServlet POST - Empty JSON data received");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Empty JSON data received");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            Regulator regulator;
            try {
                regulator = gson.fromJson(jsonData, Regulator.class);
                //system.out.println("RegulatorServlet POST - Successfully parsed Regulator object");
            } catch (Exception e) {
                //system.out.println("RegulatorServlet POST - JSON parsing error: " + e.getMessage());
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid JSON format: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
                return;
            }

            //system.out.println("RegulatorServlet POST - Parsed Regulator:");
            //system.out.println("- PrimaryName: " + regulator.getPrimaryName());
            //system.out.println("- ShortName: " + regulator.getShortName());
            //system.out.println("- Description: " + regulator.getDescription());
            long segmentIdForName = 1L;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    segmentIdForName = jsonObj.get("segmentId").getAsLong();
                }
            } catch (Exception ignored) {
            }
            try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Regulator", regulator.getPrimaryName(), segmentIdForName,
                        null)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "A regulator with this name already exists in this segment.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            int regulatorId = regulatorService.createRegulator(regulator, request);
            //system.out.println("RegulatorServlet POST - Created regulator with ID: " + regulatorId);

            // Assign regulator to segment - parse from JSON
            Integer segmentId = 1; // Default to Enterprise segment
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    segmentId = jsonObj.get("segmentId").getAsInt();
                }
            } catch (Exception ignored) {}
            int userId = UserContextUtil.getCurrentUserId(request);
            try {
                segmentDAO.assignObjectToSegment(segmentId, regulatorId, "Regulator", userId > 0 ? userId : 1);
                //system.out.println("✅ Regulator " + regulatorId + " assigned to segment " + segmentId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning regulator to segment: " + e.getMessage());
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Regulator created successfully");
            responseJson.addProperty("id", regulatorId);

            response.getWriter().write(gson.toJson(responseJson));
        } catch (IllegalArgumentException e) {
            //system.out.println("RegulatorServlet POST - Validation Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            //system.out.println("RegulatorServlet POST - SQL Error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error creating regulator: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            //system.out.println("RegulatorServlet POST - General Error: " + e.getMessage());
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
        if (!PermissionCheckUtil.checkEditPermission(request, response, "Regulator")) {
            return; // Response already sent
        }

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Regulator ID required for update");
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

            Regulator regulator = gson.fromJson(jsonData, Regulator.class);
            regulator.setId(id);
            long effectiveSegmentId = 1L;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    effectiveSegmentId = jsonObj.get("segmentId").getAsLong();
                } else {
                    int cur = segmentDAO.getObjectSegmentId(id, "Regulator");
                    if (cur > 0) {
                        effectiveSegmentId = cur;
                    }
                }
            } catch (Exception ignored) {
            }
            try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Regulator", regulator.getPrimaryName(), effectiveSegmentId,
                        id)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "A regulator with this name already exists in this segment.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            // Check segment-based edit permission
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(currentUserId, id, "Regulator");
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Access denied. You don't have permission to edit this regulator.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
                
                // Set LastUpdate_UserID from current user if not provided in request
                if (regulator.getLastUpdateUserId() == null || regulator.getLastUpdateUserId() <= 0) {
                    regulator.setLastUpdateUserId(currentUserId);
                    System.out.println("[RegulatorServlet] Set LastUpdate_UserID to current user: " + currentUserId);
                }
            }

            boolean success = regulatorService.updateRegulator(regulator, request);

            // Update segment assignment if provided - even if the base update returned false.
            // This fixes the case where the user only changes the segment field.
            boolean segmentUpdated = false;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    Integer segmentId = jsonObj.get("segmentId").getAsInt();
                    int curSegmentId = segmentDAO.getObjectSegmentId(id, "Regulator");
                    if (curSegmentId != segmentId) {
                        SegmentValidationService.ValidationResult validationResult =
                            segmentValidationService.validateSegmentMove(id, segmentId, "Regulator", null);
                        if (!validationResult.isValid) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            JsonObject error = new JsonObject();
                            error.addProperty("success", false);
                            error.addProperty("error", validationResult.message);
                            response.getWriter().write(gson.toJson(error));
                            return;
                        }

                        if (curSegmentId > 0) {
                            segmentDAO.removeObjectFromSegment(curSegmentId, id, "Regulator", currentUserId > 0 ? currentUserId : 1);
                        }
                        segmentDAO.assignObjectToSegment(segmentId, id, "Regulator", currentUserId > 0 ? currentUserId : 1);
                        System.out.println("✅ Regulator " + id + " segment changed from " + curSegmentId + " to " + segmentId);
                        segmentUpdated = true;
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Error updating regulator segment: " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("error", "Error updating regulator segment: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
                return;
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success || segmentUpdated);
            responseJson.addProperty("message", (success || segmentUpdated) ? "Regulator updated successfully" : "Failed to update regulator");

            response.getWriter().write(gson.toJson(responseJson));
        } catch (IllegalArgumentException e) {
            //system.out.println("RegulatorServlet PUT - Validation Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error updating regulator: " + e.getMessage());
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
                error.addProperty("error", "Regulator ID required for deletion");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int id = Integer.parseInt(pathInfo.substring(1));

            boolean success = regulatorService.deleteRegulator(id, request);

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success);
            responseJson.addProperty("message", success ? "Regulator deleted successfully" : "Failed to delete regulator");

            response.getWriter().write(gson.toJson(responseJson));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error deleting regulator: " + e.getMessage());
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
