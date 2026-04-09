package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.OrgUnit;
import com.example.budg_v2.service.OrgUnitService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.function.Supplier;

@WebServlet(name = "OrgUnitServlet", urlPatterns = {"/api/org-units", "/api/org-units/*", "/api/view/org-units", "/api/view/org-units/*", "/api/create/org-units", "/api/create/org-units/*"})
public class OrgUnitServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(OrgUnitServlet.class.getName());

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String PATH_STATUSES = "/statuses";
    private static final String PATH_SEARCH = "/search";
    private static final String PARAM_QUERY = "q";
    private static final String ID_REGEX = "\\d+";
    private static final int MAX_REFERENCE_LENGTH = 50;

    private static final String ERROR_INVALID_ID = "Invalid ID format";
    private static final String ERROR_ID_REQUIRED = "ID parameter is required";
    private static final String ERROR_NAME_REQUIRED = "Name is required";
    private static final String ERROR_NOT_FOUND = "Organization unit not found";

    private final OrgUnitService orgUnitService;
    @SuppressWarnings("unused")
    private final SegmentDAO segmentDAO;

    public OrgUnitServlet() {
        this.orgUnitService = new OrgUnitService();
        this.segmentDAO = new SegmentDAO();
    }

    private void setResponseHeaders(HttpServletResponse response) {
        response.setContentType(CONTENT_TYPE_JSON);
        response.setCharacterEncoding(CHARSET_UTF8);
        CorsUtil.setCorsHeaders(response);
    }

    private int parseId(String pathInfo) {
        if (pathInfo == null || pathInfo.length() <= 1) throw new IllegalArgumentException(ERROR_ID_REQUIRED);
        String idParam = pathInfo.substring(1);
        if (!idParam.matches(ID_REGEX)) throw new IllegalArgumentException(ERROR_INVALID_ID);
        return Integer.parseInt(idParam);
    }

    private String sanitizeInput(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\"", "&quot;");
    }

    private void validateOrgUnitData(String name, String reference) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException(ERROR_NAME_REQUIRED);
        if (reference != null && reference.length() > MAX_REFERENCE_LENGTH)
            throw new IllegalArgumentException("Reference cannot exceed " + MAX_REFERENCE_LENGTH + " characters");
    }

    private JsonObject createOrgUnitJson(OrgUnit unit, boolean isNew) {
        JsonObject data = new JsonObject();
        data.addProperty("id", unit.getId());
        data.addProperty("reference", unit.getReference());
        data.addProperty("name", unit.getName());
        data.addProperty("description", unit.getDescription());
        data.addProperty("parent_id", unit.getParentId());
        data.addProperty("status_id", unit.getStatusId());
        if (isNew) {
            data.addProperty("created_date", unit.getCreatedDate() != null ? unit.getCreatedDate().toString() : null);
        } else {
            data.addProperty("last_updated_date", unit.getLastUpdatedDate() != null ? unit.getLastUpdatedDate().toString() : null);
        }
        return data;
    }

    private void handleRequest(HttpServletResponse response, Supplier<Object> action) throws IOException {
        setResponseHeaders(response);
        try {
            Object result = action.get();
            if (result instanceof JsonObject json) {
                JsonUtil.sendSuccessResponse(response.getWriter(), "Success", json);
            } else if (result != null) {
                JsonUtil.sendJsonResponse(response.getWriter(), result);
            } else {
                JsonUtil.sendSuccessResponse(response.getWriter(), "Operation completed successfully", null);
            }
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (SecurityException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 403);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error", e);
            JsonUtil.sendErrorResponse(response.getWriter(), "Internal server error", 500);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleRequest(response, () -> {
            String pathInfo = request.getPathInfo();
            int userId = UserContextUtil.getCurrentUserId(request);

            if (PATH_STATUSES.equals(pathInfo)) {
                try {
                    return orgUnitService.getAllStatuses();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            if (PATH_SEARCH.equals(pathInfo)) {
                String query = sanitizeInput(request.getParameter(PARAM_QUERY));
                try {
                    return (query != null && !query.isEmpty()) ? orgUnitService.searchOrgUnits(query) : 
                           (userId > 0 ? orgUnitService.getAllOrgUnits(userId) : orgUnitService.getAllOrgUnits());
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            if (pathInfo != null && pathInfo.length() > 1) {
                try {
                    return getOrgUnitByIdInternal(request, parseId(pathInfo));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                return userId > 0 ? orgUnitService.getAllOrgUnits(userId) : orgUnitService.getAllOrgUnits();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Check create permission for new org unit creation
        if (!PermissionCheckUtil.checkCreatePermission(request, response, "Org Unit")) {
            return; // Response already sent
        }
        
        handleRequest(response, () -> {
            JsonObject jsonData = null;
            try {
                jsonData = JsonUtil.parseJsonFromRequest(request.getReader());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String reference = sanitizeInput(JsonUtil.getJsonString(jsonData, "reference"));
            String name = sanitizeInput(JsonUtil.getJsonString(jsonData, "name"));
            String description = sanitizeInput(JsonUtil.getJsonString(jsonData, "description"));
            String parentName = sanitizeInput(JsonUtil.getJsonString(jsonData, "parent_name"));
            String statusName = sanitizeInput(JsonUtil.getJsonString(jsonData, "status_name"));
            Integer statusId = JsonUtil.getJsonInt(jsonData, "status_id");

            validateOrgUnitData(name, reference);
            Integer segId = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segId == null) {
                segId = 1;
            }
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "OrgUnit", name, segId.longValue(), null)) {
                    throw new IllegalArgumentException("Name already exists in this segment");
                }
                if (reference != null && !reference.isEmpty()) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT 1 FROM org_unit WHERE LOWER(Reference)=LOWER(?) AND (deleted_Date IS NULL OR deleted_Date='') LIMIT 1")) {
                        ps.setString(1, reference);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                throw new IllegalArgumentException("Reference already exists");
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            OrgUnit createdUnit = null;
            try {
                int userId = UserContextUtil.getCurrentUserId(request);
                createdUnit = orgUnitService.createOrgUnit(reference, name, description, parentName, statusId, statusName, userId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            return createOrgUnitJson(createdUnit, true);
        });
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Check role-based edit permission first
        if (!PermissionCheckUtil.checkEditPermission(request, response, "Org Unit")) {
            return; // Response already sent
        }
        
        handleRequest(response, () -> {
            int id = parseId(request.getPathInfo());
            JsonObject jsonData = null;
            try {
                jsonData = JsonUtil.parseJsonFromRequest(request.getReader());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String reference = sanitizeInput(JsonUtil.getJsonString(jsonData, "reference"));
            String name = sanitizeInput(JsonUtil.getJsonString(jsonData, "name"));
            String description = sanitizeInput(JsonUtil.getJsonString(jsonData, "description"));
            String parentName = sanitizeInput(JsonUtil.getJsonString(jsonData, "parent_name"));
            String statusName = sanitizeInput(JsonUtil.getJsonString(jsonData, "status_name"));
            Integer statusId = JsonUtil.getJsonInt(jsonData, "status_id");

            validateOrgUnitData(name, reference);

            Integer reqSeg = JsonUtil.getJsonInt(jsonData, "segmentId");
            int curSeg = -1;
            try {
                curSeg = segmentDAO.getObjectSegmentId(id, "OrgUnit");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            long effSeg = (reqSeg != null) ? reqSeg.longValue() : (curSeg > 0 ? curSeg : 1L);
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "OrgUnit", name, effSeg, id)) {
                    throw new IllegalArgumentException("Name already exists in this segment");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            
            // Check segment-based edit permission
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId > 0) {
                try {
                    boolean canEdit = SegmentAccessService.canEditObject(currentUserId, id, "OrgUnit");
                    if (!canEdit) {
                        throw new SecurityException("Access denied. You don't have permission to edit this organization unit.");
                    }
                } catch (java.sql.SQLException e) {
                    System.err.println("Error checking edit permission: " + e.getMessage());
                }
            }

            OrgUnit updatedUnit = null;
            try {
                int userId = UserContextUtil.getCurrentUserId(request);
                updatedUnit = orgUnitService.updateOrgUnit(id, reference, name, description, parentName, statusId, statusName, userId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            return createOrgUnitJson(updatedUnit, false);
        });
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleRequest(response, () -> {
            int id = parseId(request.getPathInfo());
            boolean deleted = false;
            try {
                deleted = orgUnitService.deleteOrgUnit(id, request);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (!deleted) throw new IllegalArgumentException(ERROR_NOT_FOUND);
            return null;
        });
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.handlePreflight(response);
    }

    private OrgUnit getOrgUnitByIdInternal(HttpServletRequest request, int id) throws SQLException {
        OrgUnit orgUnit = orgUnitService.getOrgUnitById(id);
        if (orgUnit == null) throw new IllegalArgumentException(ERROR_NOT_FOUND);
        if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(orgUnit.getStatusName())) {
            throw new SecurityException("This object is not available.");
        }
        return orgUnit;
    }
}
