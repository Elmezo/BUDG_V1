package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.model.RegulatoryTheme;
import com.example.budg_v2.service.RegulatoryThemeService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RequestedSegmentFilterUtil;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
import java.util.Map;

@WebServlet("/api/regulatory-theme/*")
public class RegulatoryThemeServlet extends HttpServlet {
    private RegulatoryThemeService regulatoryThemeService;
    private SegmentDAO segmentDAO;
    private Gson gson;
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();

    @Override
    public void init() throws ServletException {
        super.init();
        regulatoryThemeService = new RegulatoryThemeService();
        segmentDAO = new SegmentDAO();
        gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        //system.out.println("RegulatoryThemeServlet: doGet called with pathInfo: " + pathInfo);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            
            if (pathInfo == null || pathInfo.equals("/")) {
                // Get all regulatory themes filtered by segment access
                List<RegulatoryTheme> themes = (userId > 0)
                    ? regulatoryThemeService.getAllRegulatoryThemes(userId)
                    : regulatoryThemeService.getAllRegulatoryThemes();
                themes = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        themes,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "RegulatoryTheme",
                        RegulatoryTheme::getId);
                response.getWriter().write(gson.toJson(themes));
            } else if (pathInfo.equals("/list")) {
                // Get regulatory themes for dropdown
                List<RegulatoryTheme> themes = (userId > 0)
                    ? regulatoryThemeService.getRegulatoryThemesForDropdown(userId)
                    : regulatoryThemeService.getRegulatoryThemesForDropdown();
                themes = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        themes,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "RegulatoryTheme",
                        RegulatoryTheme::getId);
                //system.out.println("RegulatoryThemeServlet /list - themes count: " + themes.size());

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (RegulatoryTheme t : themes) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", t.getId());
                    o.addProperty("primaryname", t.getPrimaryName());
                    o.addProperty("description", t.getDescription());
                    o.addProperty("refnumber", t.getRefNumber());
                    arr.add(o);
                    //system.out.println("RegulatoryThemeServlet: Added theme to response: " + t.getPrimaryName());
                }
                response.getWriter().write(arr.toString());
            } else if (pathInfo.equals("/parent-picker")) {
                // Get regulatory themes for parent picker (no exclusion)
                List<RegulatoryTheme> themes = (userId > 0)
                    ? regulatoryThemeService.getRegulatoryThemesForDropdown(userId)
                    : regulatoryThemeService.getRegulatoryThemesForDropdown();
                themes = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        themes,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "RegulatoryTheme",
                        RegulatoryTheme::getId);
                //system.out.println("RegulatoryThemeServlet /parent-picker - themes count: " + themes.size());

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (RegulatoryTheme t : themes) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", t.getId());
                    o.addProperty("primaryname", t.getPrimaryName());
                    o.addProperty("description", t.getDescription());
                    o.addProperty("refnumber", t.getRefNumber());
                    arr.add(o);
                    //system.out.println("RegulatoryThemeServlet: Added theme to parent picker: " + t.getPrimaryName());
                }
                response.getWriter().write(arr.toString());
            } else if (pathInfo.startsWith("/parent-picker/")) {
                // Get regulatory themes for parent picker, excluding specified ID
                String idStr = pathInfo.substring(15); // Remove "/parent-picker/" prefix
                try {
                    int excludeId = Integer.parseInt(idStr);
                    List<RegulatoryTheme> themes = (userId > 0)
                        ? regulatoryThemeService.getRegulatoryThemesForParentPicker(excludeId, userId)
                        : regulatoryThemeService.getRegulatoryThemesForParentPicker(excludeId);
                    themes = RequestedSegmentFilterUtil.filterByRequestedSegment(
                            themes,
                            RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                            "RegulatoryTheme",
                            RegulatoryTheme::getId);
                    //system.out.println("RegulatoryThemeServlet /parent-picker/" + excludeId + " - themes count: " + themes.size());

                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    for (RegulatoryTheme t : themes) {
                        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                        o.addProperty("id", t.getId());
                        o.addProperty("primaryName", t.getPrimaryName());
                        o.addProperty("description", t.getDescription());
                        o.addProperty("refNumber", t.getRefNumber());
                        o.addProperty("parentId", t.getParentId());
                        arr.add(o);
                        //system.out.println("RegulatoryThemeServlet: Added theme to parent picker: " + t.getPrimaryName());
                    }
                    response.getWriter().write(arr.toString());
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid regulatory theme ID for parent picker");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.equals("/hierarchy")) {
                // Hierarchy view returns the full tree so the relationship UI can
                // structurally show every node; access-restricted private nodes
                // are then masked (xxxx + lock) by HierarchyAccessMasker below.
                List<RegulatoryTheme> themes = regulatoryThemeService.getAllRegulatoryThemes();

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (RegulatoryTheme t : themes) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", t.getId());
                    o.addProperty("primaryName", t.getPrimaryName());
                    o.addProperty("description", t.getDescription());
                    o.addProperty("refNumber", t.getRefNumber());
                    o.addProperty("shortName", t.getShortName());
                    o.addProperty("parentId", t.getParentId());
                    o.addProperty("lastUpdateDatetime", t.getLastUpdateDateTime() != null ? t.getLastUpdateDateTime().toString() : "");
                    arr.add(o);
                }
                int hierarchyUserId = UserContextUtil.getCurrentUserId(request);
                com.example.budg_v2.util.HierarchyAccessMasker.mask(arr, "RegulatoryTheme", hierarchyUserId);
                response.getWriter().write(arr.toString());
            } else if (pathInfo.matches("/\\d+")) {
                // Get regulatory theme by ID
                try {
                    int id = Integer.parseInt(pathInfo.substring(1)); // Remove leading "/"
                    RegulatoryTheme theme = regulatoryThemeService.getRegulatoryThemeById(id);
                    if (theme != null) {
                        if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(theme.getStatusName())) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"error\":\"This object is not available.\"}");
                            return;
                        }
                        // Get segment info and add to response
                        JsonObject themeJson = gson.toJsonTree(theme).getAsJsonObject();
                        int segmentId = segmentDAO.getObjectSegmentId(id, "RegulatoryTheme");
                        if (segmentId > 0) {
                            themeJson.addProperty("segmentId", segmentId);
                            try {
                                Map<String, Object> segment = segmentDAO.getSegmentById(segmentId);
                                if (segment != null && segment.get("name") != null) {
                                    themeJson.addProperty("segmentName", (String) segment.get("name"));
                                } else {
                                    themeJson.addProperty("segmentName", segmentId == 1 ? "Enterprise" : "Not specified");
                                }
                            } catch (SQLException e) {
                                themeJson.addProperty("segmentName", segmentId == 1 ? "Enterprise" : "Not specified");
                            }
                        } else {
                            themeJson.addProperty("segmentId", (Integer) null);
                            themeJson.addProperty("segmentName", "Not specified");
                        }
                        response.getWriter().write(gson.toJson(themeJson));
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Regulatory theme not found");
                        response.getWriter().write(gson.toJson(error));
                    }
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid regulatory theme ID");
                    response.getWriter().write(gson.toJson(error));
                }
            } else if (pathInfo.startsWith("/search/")) {
                // Search regulatory themes
                String searchTerm = pathInfo.substring(8); // Remove "/search/" prefix
                List<RegulatoryTheme> themes = (userId > 0)
                    ? regulatoryThemeService.searchRegulatoryThemes(searchTerm, userId)
                    : regulatoryThemeService.searchRegulatoryThemes(searchTerm);
                response.getWriter().write(gson.toJson(themes));
            } else if (pathInfo.startsWith("/")) {
                // Get regulatory theme by ID
                try {
                    int id = Integer.parseInt(pathInfo.substring(1));
                    RegulatoryTheme theme = regulatoryThemeService.getRegulatoryThemeById(id);
                    if (theme != null) {
                        if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(theme.getStatusName())) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"error\":\"This object is not available.\"}");
                            return;
                        }
                        // Get segment info and add to response
                        JsonObject themeJson = gson.toJsonTree(theme).getAsJsonObject();
                        int segmentId = segmentDAO.getObjectSegmentId(id, "RegulatoryTheme");
                        if (segmentId > 0) {
                            themeJson.addProperty("segmentId", segmentId);
                            try {
                                Map<String, Object> segment = segmentDAO.getSegmentById(segmentId);
                                if (segment != null && segment.get("name") != null) {
                                    themeJson.addProperty("segmentName", (String) segment.get("name"));
                                } else {
                                    themeJson.addProperty("segmentName", segmentId == 1 ? "Enterprise" : "Not specified");
                                }
                            } catch (SQLException e) {
                                themeJson.addProperty("segmentName", segmentId == 1 ? "Enterprise" : "Not specified");
                            }
                        } else {
                            themeJson.addProperty("segmentId", (Integer) null);
                            themeJson.addProperty("segmentName", "Not specified");
                        }
                        response.getWriter().write(gson.toJson(themeJson));
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", "Regulatory theme not found");
                        response.getWriter().write(gson.toJson(error));
                    }
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid regulatory theme ID");
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

        // Check create permission for new regulatory theme creation
        if (!PermissionCheckUtil.checkCreatePermission(request, response, "Regulatory Theme")) {
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
            //system.out.println("RegulatoryThemeServlet POST - Received JSON: " + jsonData);
            //system.out.println("RegulatoryThemeServlet POST - JSON length: " + jsonData.length());
            //system.out.println("RegulatoryThemeServlet POST - JSON is empty: " + jsonData.trim().isEmpty());

            if (jsonData == null || jsonData.trim().isEmpty()) {
                //system.out.println("RegulatoryThemeServlet POST - Empty JSON data received");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Empty JSON data received");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            RegulatoryTheme theme;
            try {
                theme = gson.fromJson(jsonData, RegulatoryTheme.class);
                //system.out.println("RegulatoryThemeServlet POST - Successfully parsed RegulatoryTheme object");
            } catch (Exception e) {
                //system.out.println("RegulatoryThemeServlet POST - JSON parsing error: " + e.getMessage());
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid JSON format: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
                return;
            }

            //system.out.println("RegulatoryThemeServlet POST - Parsed RegulatoryTheme:");
            //system.out.println("- PrimaryName: " + theme.getPrimaryName());
            //system.out.println("- Description: " + theme.getDescription());
            //system.out.println("- ParentId: " + theme.getParentId());
            //system.out.println("- StatusId: " + theme.getStatusId());
            //system.out.println("- RefNumber: " + theme.getRefNumber());
            //system.out.println("- ShortName: " + theme.getShortName());

            // Set lastUpdateUserId to current user on creation (Last Updated By = Created By)
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId <= 0) {
                JsonUtil.sendErrorResponse(response.getWriter(), "User authentication required", 401);
                return;
            }
            theme.setLastUpdateUserId(currentUserId);
            long segmentIdForName = 1L;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    segmentIdForName = jsonObj.get("segmentId").getAsLong();
                }
            } catch (Exception ignored) {
            }
            try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "RegulatoryTheme", theme.getPrimaryName(), segmentIdForName,
                        null)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "A regulatory theme with this name already exists in this segment.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            // Validate segment hierarchy before creating regulatory theme
            Integer segmentId = 1;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    segmentId = jsonObj.get("segmentId").getAsInt();
                }
            } catch (Exception ignored) {}
            Integer themeParentId = theme.getParentId();
            if (themeParentId != null && themeParentId > 0) {
                try {
                    var hierarchyResult = segmentValidationService.validateParentChildSegment(themeParentId, segmentId, "RegulatoryTheme");
                    if (!hierarchyResult.isValid) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        JsonObject error = new JsonObject();
                        error.addProperty("error", hierarchyResult.message);
                        response.getWriter().write(gson.toJson(error));
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Error validating regulatory theme hierarchy: " + e.getMessage());
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Error validating segment hierarchy: " + e.getMessage());
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            int themeId = regulatoryThemeService.createRegulatoryTheme(theme, request);
            //system.out.println("RegulatoryThemeServlet POST - Created regulatory theme with ID: " + themeId);
            int userId = UserContextUtil.getCurrentUserId(request);
            try {
                segmentDAO.assignObjectToSegment(segmentId, themeId, "RegulatoryTheme", userId > 0 ? userId : 1);
                //system.out.println("✅ RegulatoryTheme " + themeId + " assigned to segment " + segmentId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning regulatory theme to segment: " + e.getMessage());
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Regulatory theme created successfully");
            responseJson.addProperty("id", themeId);

            response.getWriter().write(gson.toJson(responseJson));
        } catch (IllegalArgumentException e) {
            //system.out.println("RegulatoryThemeServlet POST - Validation Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            //system.out.println("RegulatoryThemeServlet POST - SQL Error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error creating regulatory theme: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            //system.out.println("RegulatoryThemeServlet POST - General Error: " + e.getMessage());
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
        if (!PermissionCheckUtil.checkEditPermission(request, response, "Regulatory Theme")) {
            return; // Response already sent
        }

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Regulatory theme ID required for update");
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

            RegulatoryTheme theme = gson.fromJson(jsonData, RegulatoryTheme.class);
            theme.setId(id);

            // Get current user ID from request (like DatasetServlet does)
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId <= 0) {
                JsonUtil.sendErrorResponse(response.getWriter(), "User authentication required", 401);
                return;
            }
            theme.setLastUpdateUserId(currentUserId);
            long effectiveSegmentId = 1L;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    effectiveSegmentId = jsonObj.get("segmentId").getAsLong();
                } else {
                    int cur = segmentDAO.getObjectSegmentId(id, "RegulatoryTheme");
                    if (cur > 0) {
                        effectiveSegmentId = cur;
                    }
                }
            } catch (Exception ignored) {
            }
            try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "RegulatoryTheme", theme.getPrimaryName(),
                        effectiveSegmentId, id)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "A regulatory theme with this name already exists in this segment.");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            }

            // Check segment-based edit permission
            boolean canEdit = SegmentAccessService.canEditObject(currentUserId, id, "RegulatoryTheme");
            if (!canEdit) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Access denied. You don't have permission to edit this regulatory theme.");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            boolean success = regulatoryThemeService.updateRegulatoryTheme(theme, request);

            boolean segmentUpdated = false;
            try {
                JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonData).getAsJsonObject();
                if (jsonObj.has("segmentId") && !jsonObj.get("segmentId").isJsonNull()) {
                    Integer segmentId = jsonObj.get("segmentId").getAsInt();
                    int curSegmentId = segmentDAO.getObjectSegmentId(id, "RegulatoryTheme");
                    if (curSegmentId != segmentId) {
                        SegmentValidationService.ValidationResult validationResult =
                            segmentValidationService.validateSegmentMove(id, segmentId, "RegulatoryTheme", null);
                        if (!validationResult.isValid) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            JsonObject error = new JsonObject();
                            error.addProperty("success", false);
                            error.addProperty("error", validationResult.message);
                            response.getWriter().write(gson.toJson(error));
                            return;
                        }

                        if (curSegmentId > 0) {
                            segmentDAO.removeObjectFromSegment(curSegmentId, id, "RegulatoryTheme", currentUserId);
                        }
                        segmentDAO.assignObjectToSegment(segmentId, id, "RegulatoryTheme", currentUserId);
                        segmentUpdated = true;
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Error updating regulatory theme segment: " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("error", "Error updating regulatory theme segment: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
                return;
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success || segmentUpdated);
            responseJson.addProperty("message", (success || segmentUpdated) ? "Regulatory theme updated successfully" : "Failed to update regulatory theme");

            response.getWriter().write(gson.toJson(responseJson));
        } catch (IllegalArgumentException e) {
            //system.out.println("RegulatoryThemeServlet PUT - Validation Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error updating regulatory theme: " + e.getMessage());
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
                error.addProperty("error", "Regulatory theme ID required for deletion");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int id = Integer.parseInt(pathInfo.substring(1));

            boolean success = regulatoryThemeService.deleteRegulatoryTheme(id, request);

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", success);
            responseJson.addProperty("message", success ? "Regulatory theme deleted successfully" : "Failed to delete regulatory theme");

            response.getWriter().write(gson.toJson(responseJson));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error deleting regulatory theme: " + e.getMessage());
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
