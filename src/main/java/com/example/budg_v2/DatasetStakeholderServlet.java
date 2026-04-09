package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.PermissionService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.DefaultStakeholderUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dataset Stakeholder Servlet for handling stakeholder CRUD operations
 * Endpoints: /api/dataset-stakeholder/{id}/stakeholders, /api/dataset-stakeholder/lookup
 */
@WebServlet("/api/dataset-stakeholder/*")
public class DatasetStakeholderServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(DatasetStakeholderServlet.class);
    @SuppressWarnings("unused")
    private static final int DATASET_FACET_ID = 11; // Dataset facet ID in module table
    
    private Gson gson = new Gson();
    private FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    @SuppressWarnings("unused")
    private DFCRService dfcrService = new DFCRService();
    @SuppressWarnings("unused")
    private ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
    private SegmentValidationService segmentValidationService = new SegmentValidationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String pathInfo = request.getPathInfo();
            String requestURI = request.getRequestURI();

            //system.out.println("DatasetStakeholderServlet - Request URI: " + requestURI);
            //system.out.println("DatasetStakeholderServlet - Path Info: " + pathInfo);

            // Handle stakeholder lookup requests
            if (requestURI.contains("/dataset-stakeholder/lookup")) {
                handleLookupRequest(request, response, conn, out);
                return;
            }

            // Handle stakeholders view requests (uses roleaccepted for status)
            if (pathInfo != null && pathInfo.matches("/\\d+/stakeholders$")) {
                //system.out.println("Pattern matched! Processing stakeholders view request...");
                int originalDatasetId = extractDatasetId(requestURI);
                //system.out.println("Extracted dataset ID: " + originalDatasetId);

                if (originalDatasetId > 0) {
                    // Stakeholders are excluded from pending changes - always load from original
                    // Changes to stakeholders are applied immediately even with active CR
                    List<JsonObject> stakeholders = getDatasetStakeholdersForView(conn, originalDatasetId);
                    //system.out.println("Found " + stakeholders.size() + " stakeholders for view");
                    out.print(gson.toJson(stakeholders));
                } else {
                    //system.out.println("Could not extract dataset ID from URI: " + requestURI);
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"Invalid dataset ID\"}");
                }
            } else if (pathInfo != null && pathInfo.matches("/\\d+/stakeholders/edit$")) {
                //system.out.println("Pattern matched! Processing stakeholders edit request...");
                int originalDatasetId = extractDatasetId(requestURI);
                //system.out.println("Extracted dataset ID: " + originalDatasetId);

                if (originalDatasetId > 0) {
                    // Stakeholders are excluded from pending changes - always load from original
                    // Changes to stakeholders are applied immediately even with active CR
                    List<JsonObject> stakeholders = getDatasetStakeholdersForEdit(conn, originalDatasetId);
                    //system.out.println("Found " + stakeholders.size() + " stakeholders for edit");
                    out.print(gson.toJson(stakeholders));
                } else {
                    //system.out.println("Could not extract dataset ID from URI: " + requestURI);
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"Invalid dataset ID\"}");
                }
            } else {
                //system.out.println("No matching pattern for pathInfo: " + pathInfo);
                //system.out.println("Expected pattern: /\\d+/stakeholders or /\\d+/stakeholders/edit");
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"error\": \"Invalid endpoint\"}");
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String pathInfo = request.getPathInfo();

            if (pathInfo != null && pathInfo.matches("/\\d+/stakeholders/edit")) {
                int datasetId = extractDatasetId(request.getRequestURI());

                if (datasetId <= 0) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"Invalid dataset ID\"}");
                    return;
                }

                // Parse JSON request body
                String requestBody = getRequestBody(request);
                JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();

                // Get current user ID (from authentication)
                int currentUserId = getCurrentUserId(request);
                if (currentUserId <= 0) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "User authentication required");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
                
                // Check Edit permission + stakeholder status for Data Sets
                if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Data Sets", datasetId)) {
                    return; // Response already sent
                }

                // Block if current user has a default-only role on this object (cannot save until they remove it or admin assigns them)
                DefaultStakeholderUtil.ValidationResult canSave = DefaultStakeholderUtil.validateCurrentUserCanSaveStakeholders(
                        conn, "Dataset", datasetId, currentUserId, java.util.Collections.emptySet());
                if (!canSave.isValid()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"" + (canSave.getWarningMessage() != null ? canSave.getWarningMessage().replace("\"", "\\\"") : "") + "\"}");
                    return;
                }

                // Stakeholders are excluded from pending changes - always save directly to original
                // Changes to stakeholders are applied immediately even with active CR
                
                // Validate role assignment before adding
                if (jsonObject.has("ipid") && jsonObject.has("roleID")) {
                    int userId = jsonObject.get("ipid").getAsInt();
                    int roleId = jsonObject.get("roleID").getAsInt();
                    
                    DefaultStakeholderUtil.ValidationResult validation = 
                        DefaultStakeholderUtil.validateStakeholderRoleAssignment(conn, userId, roleId);
                    
                    if (!validation.isValid()) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        out.print("{\"error\": \"" + validation.getWarningMessage() + "\"}");
                        return;
                    }
                    
                    // Validate user has access to object's segment before adding as stakeholder
                    try {
                        var segmentValidation = segmentValidationService.validateStakeholderCanBeAdded(userId, datasetId, "Dataset");
                        if (!segmentValidation.isValid) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            out.print("{\"error\": \"" + segmentValidation.message.replace("\"", "\\\"") + "\"}");
                            return;
                        }
                    } catch (SQLException e) {
                        logger.error("Error validating stakeholder segment access: {}", e.getMessage());
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        out.print("{\"error\": \"Error validating stakeholder access: " + e.getMessage().replace("\"", "\\\"") + "\"}");
                        return;
                    }
                }
                
                // Add stakeholder directly to original dataset (no pending changes for stakeholders)
                boolean success = addStakeholder(conn, datasetId, jsonObject, currentUserId);

                if (success) {
                        out.print("{\"success\": true, \"pending\": false, \"message\": \"Stakeholder added successfully\"}");
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print("{\"error\": \"Failed to add stakeholder\"}");
                }
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"error\": \"Invalid endpoint\"}");
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String pathInfo = request.getPathInfo();

            if (pathInfo != null && pathInfo.matches("/\\d+/stakeholders/edit")) {
                int datasetId = extractDatasetId(request.getRequestURI());

                if (datasetId <= 0) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"Invalid dataset ID\"}");
                    return;
                }

                // Parse JSON request body
                String requestBody = getRequestBody(request);
                JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();

                // Get current user ID (from authentication)
                int currentUserId = getCurrentUserId(request);
                if (currentUserId <= 0) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.print("{\"error\": \"User authentication required\"}");
                    return;
                }
                
                // Check Edit permission for Data Sets
                PermissionService permissionService = new PermissionService();
                if (!permissionService.canEdit(currentUserId, "Data Sets")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    out.print("{\"error\": \"You don't have permission to edit Data Sets\"}");
                    return;
                }

                // Block if current user has a default-only role on this object (cannot save until they remove it or admin assigns them)
                DefaultStakeholderUtil.ValidationResult canSave = DefaultStakeholderUtil.validateCurrentUserCanSaveStakeholders(
                        conn, "Dataset", datasetId, currentUserId, java.util.Collections.emptySet());
                if (!canSave.isValid()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"" + (canSave.getWarningMessage() != null ? canSave.getWarningMessage().replace("\"", "\\\"") : "") + "\"}");
                    return;
                }

                // Stakeholders are excluded from pending changes - always update directly
                // Changes to stakeholders are applied immediately even with active CR

                // Validate role assignment before updating
                if (jsonObject.has("ipid") && jsonObject.has("roleID")) {
                    int userId = jsonObject.get("ipid").getAsInt();
                    int roleId = jsonObject.get("roleID").getAsInt();
                    
                    DefaultStakeholderUtil.ValidationResult validation = 
                        DefaultStakeholderUtil.validateStakeholderRoleAssignment(conn, userId, roleId);
                    
                    if (!validation.isValid()) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        out.print("{\"error\": \"" + validation.getWarningMessage() + "\"}");
                        return;
                    }
                }
                
                // Update stakeholder directly (no pending changes for stakeholders)
                boolean success = updateStakeholder(conn, jsonObject, currentUserId);

                if (success) {
                        out.print("{\"success\": true, \"pending\": false, \"message\": \"Stakeholder updated successfully\"}");
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print("{\"error\": \"Failed to update stakeholder\"}");
                }
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"error\": \"Invalid endpoint\"}");
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String objectXPeopleId = request.getParameter("objectXPeopleId");

            if (objectXPeopleId == null || objectXPeopleId.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"ObjectXPeople ID is required for deletion\"}");
                return;
            }

            int oxpId = Integer.parseInt(objectXPeopleId);
            
            // Stakeholders are excluded from pending changes - delete directly
            // Changes to stakeholders are applied immediately even with active CR

            // Delete stakeholder
            boolean success = deleteStakeholder(conn, oxpId);

            if (success) {
                    out.print("{\"success\": true, \"pending\": false, \"message\": \"Stakeholder deleted successfully\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"error\": \"Failed to delete stakeholder\"}");
            }
        } catch (SQLException e) {
            // Check if this is our validation error for active CRs
            if (e.getMessage() != null && e.getMessage().contains("Cannot delete stakeholder")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"" + e.getMessage() + "\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            }
            e.printStackTrace();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String pathInfo = request.getPathInfo();
            String requestURI = request.getRequestURI();
            //system.out.println("PATCH request - Path Info: " + pathInfo);

            if (pathInfo != null && pathInfo.matches("/\\d+/stakeholders/accept")) {
                int datasetId = extractDatasetId(requestURI);
                //system.out.println("PATCH - Extracted dataset ID: " + datasetId);

                if (datasetId <= 0) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"Invalid dataset ID\"}");
                    return;
                }

                String requestBody = getRequestBody(request);
                //system.out.println("PATCH - Request body: " + requestBody);

                if (requestBody == null || requestBody.trim().isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"Request body is required\"}");
                    return;
                }

                JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
                //system.out.println("PATCH - Parsed JSON: " + jsonObject.toString());

                if (!jsonObject.has("objectXPeopleId")) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"Missing required field: objectXPeopleId is required\"}");
                    return;
                }

                int objectXPeopleId = jsonObject.get("objectXPeopleId").getAsInt();
                int currentUserId = getCurrentUserId(request);
                if (currentUserId <= 0) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.print("{\"error\": \"User not authenticated\"}");
                    return;
                }

                boolean success = acceptStakeholderStatus(conn, objectXPeopleId, currentUserId);

                if (success) {
                    //system.out.println("PATCH - Stakeholder status accepted successfully");
                    out.print("{\"success\": true, \"message\": \"Status accepted successfully\"}");
                } else {
                    //system.out.println("PATCH - Failed to accept stakeholder status");
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print("{\"error\": \"Failed to accept stakeholder status\"}");
                }
            } else {
                //system.out.println("PATCH - Invalid endpoint: " + pathInfo);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"error\": \"Invalid endpoint\"}");
            }
        } catch (SQLException e) {
            System.err.println("PATCH - Database error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            System.err.println("PATCH - Server error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
        }
    }

    // Handle lookup requests for dropdowns
    private void handleLookupRequest(HttpServletRequest request, HttpServletResponse response,
                                     Connection conn, PrintWriter out) throws SQLException {
        String type = request.getParameter("type");
        String roleId = request.getParameter("roleId");
        String datasetId = request.getParameter("datasetId");
        
        //system.out.println("DatasetStakeholderServlet - Lookup request type: " + type);
        //system.out.println("DatasetStakeholderServlet - Role ID parameter: " + roleId);
        //system.out.println("DatasetStakeholderServlet - Dataset ID parameter: " + datasetId);
        //system.out.println("DatasetStakeholderServlet - All parameters: " + request.getParameterMap());

        if (type == null) {
            //system.out.println("DatasetStakeholderServlet - Error: Type parameter is required");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\": \"Type parameter is required\"}");
            return;
        }

        List<JsonObject> results = new ArrayList<>();

        switch (type.toLowerCase()) {
            case "roles":
                //system.out.println("DatasetStakeholderServlet - Getting dataset roles...");
                results = getDatasetRoles(conn);
                break;
            case "rolestatus":
                //system.out.println("DatasetStakeholderServlet - Getting role statuses...");
                results = getRoleStatuses(conn);
                break;
            case "people":
              //  String roleId = request.getParameter("roleId");
                String objectIdParam = request.getParameter("objectId");
                //system.out.println("DatasetStakeholderServlet - Getting people for role ID: " + roleId);
                if (roleId != null) {
                    if (objectIdParam != null && !objectIdParam.isEmpty()) {
                        // Use segment filtering when objectId is provided
                        results = getPeopleByRoleWithSegmentFilter(conn, Integer.parseInt(roleId), Integer.parseInt(objectIdParam));
                    } else {
                        // Fallback to original behavior when objectId is not provided
                        results = getPeopleByRole(conn, Integer.parseInt(roleId));
                    }
                }
                break;
            default:
                //system.out.println("DatasetStakeholderServlet - Error: Invalid type parameter: " + type);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid type parameter\"}");
                return;
        }

        //system.out.println("DatasetStakeholderServlet - Returning " + results.size() + " results for type: " + type);
        out.print(gson.toJson(results));
    }

    // Get dataset stakeholders for VIEW mode (uses roleaccepted for status)
    private List<JsonObject> getDatasetStakeholdersForView(Connection conn, int datasetId) throws SQLException {
        String sql = """
            SELECT 
                oxp.ID AS ObjectXPeopleID,
                oxp.AcceptedID AS AcceptedID,
                oxp.isDelegateOF AS DelegateOfId,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS PersonName,
                orl.PrimaryName AS RoleName,
                orl.ID AS RoleID,
                ips.PrimaryName AS StatusName,
                ips.ID AS StatusID,
                p.ID AS PeopleID,
                p.Email AS PersonEmail,
                COALESCE(ou.Name, 'No Department') AS OrgUnit,
                ou.ID AS OrgUnitID,
                ra.Message AS RoleAccepted,
                CONCAT(delegate_p.First_Name, ' ', delegate_p.Last_Name) AS DelegateOf
            FROM dataset_x_objectxpeople dxop
            JOIN object_x_people oxp 
                ON oxp.ID = dxop.Object_x_ipid
            JOIN people p 
                ON p.ID = oxp.ipid
            JOIN object_role orl 
                ON orl.ID = oxp.RoleID
            LEFT JOIN object_x_ip_status ips 
                ON ips.ID = oxp.statusID
            LEFT JOIN roleaccepted ra 
                ON ra.ID = oxp.AcceptedID
            LEFT JOIN org_unit ou 
                ON ou.ID = p.Org_Unit_ID
            LEFT JOIN object_x_people d_oxp ON oxp.isDelegateOF = d_oxp.ID
            LEFT JOIN people delegate_p ON d_oxp.ipid = delegate_p.ID
            WHERE dxop.Dataset_ID = ?
            ORDER BY orl.PrimaryName, p.First_Name, p.Last_Name
            """;

        List<JsonObject> stakeholders = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject stakeholder = new JsonObject();
                    stakeholder.addProperty("ObjectXPeopleID", rs.getInt("ObjectXPeopleID"));
                    stakeholder.addProperty("PersonName", rs.getString("PersonName"));
                    stakeholder.addProperty("RoleName", rs.getString("RoleName"));
                    stakeholder.addProperty("RoleID", rs.getInt("RoleID"));
                    stakeholder.addProperty("StatusName", rs.getString("StatusName"));
                    // Handle StatusID - can be NULL
                    int statusID = rs.getInt("StatusID");
                    if (rs.wasNull()) {
                        stakeholder.add("StatusID", null);
                    } else {
                        stakeholder.addProperty("StatusID", statusID);
                    }
                    stakeholder.addProperty("PeopleID", rs.getInt("PeopleID"));
                    stakeholder.addProperty("PersonEmail", rs.getString("PersonEmail"));
                    stakeholder.addProperty("OrgUnit", rs.getString("OrgUnit"));
                    // Handle OrgUnitID - can be NULL
                    int orgUnitID = rs.getInt("OrgUnitID");
                    if (rs.wasNull()) {
                        stakeholder.add("OrgUnitID", null);
                    } else {
                        stakeholder.addProperty("OrgUnitID", orgUnitID);
                    }
                    // Handle DelegateOfId - can be NULL
                    int delegateOfId = rs.getInt("DelegateOfId");
                    if (rs.wasNull()) {
                        stakeholder.add("DelegateOfId", null);
                    } else {
                        stakeholder.addProperty("DelegateOfId", delegateOfId);
                    }
                    // Handle DelegateOf - can be NULL
                    String delegateOf = rs.getString("DelegateOf");
                    if (delegateOf != null) {
                        stakeholder.addProperty("DelegateOf", delegateOf);
                    } else {
                        stakeholder.add("DelegateOf", null);
                    }
                    // Handle RoleAccepted - get from roleaccepted.Message via AcceptedID
                    // Get AcceptedID first to debug
                    int acceptedID = rs.getInt("AcceptedID");
                    if (rs.wasNull()) {
                        acceptedID = 0; // NULL in database
                    }
                    String roleAccepted = rs.getString("RoleAccepted");
                    
                    // Debug: Log the values to understand what's coming from DB
                    //system.out.println("Stakeholder - PersonName: " + rs.getString("PersonName") + 
                                  //   ", AcceptedID: " + acceptedID +
                              //       ", RoleAccepted (ra.Message): " + roleAccepted +
                               //      (roleAccepted == null ? " [NULL]" : " [NOT NULL]"));
                    
                    if (roleAccepted != null && !roleAccepted.trim().isEmpty()) {
                        stakeholder.addProperty("RoleAccepted", roleAccepted);
                    } else {
                        stakeholder.addProperty("RoleAccepted", ""); // Empty string instead of null
                        //system.out.println("Warning: RoleAccepted is null or empty for AcceptedID");
                    }
                    stakeholders.add(stakeholder);
                }
            }
        }

        return stakeholders;
    }

    // Get dataset stakeholders for EDIT mode (uses object_x_ip_status for status - same as committee)
    private List<JsonObject> getDatasetStakeholdersForEdit(Connection conn, int datasetId) throws SQLException {
        String sql = """
            SELECT 
                oxp.id AS ObjectXPeopleID,
                oxp.isDelegateOF AS DelegateOfId,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS PersonName,
                orl.primaryname AS RoleName,
                orl.id AS RoleID,
                ips.primaryname AS StatusName,
                ips.id AS StatusID,
                p.ID AS PeopleID,
                p.Email AS PersonEmail,
                ou.Name AS Department,
                ou.ID AS OrgUnitID,
                CONCAT(delegate_p.First_Name, ' ', delegate_p.Last_Name) AS DelegateOf
            FROM dataset_x_objectxpeople dxop
            JOIN object_x_people oxp 
                ON oxp.id = dxop.Object_x_ipid
            JOIN people p 
                ON p.ID = oxp.ipid
            JOIN object_role orl 
                ON orl.id = oxp.roleID
            LEFT JOIN object_x_ip_status ips 
                ON ips.id = oxp.statusID
            LEFT JOIN org_unit ou 
                ON ou.ID = p.Org_Unit_ID
            LEFT JOIN object_x_people d_oxp ON oxp.isDelegateOF = d_oxp.ID
            LEFT JOIN people delegate_p ON d_oxp.ipid = delegate_p.ID
            WHERE dxop.Dataset_ID = ?
            ORDER BY orl.primaryname, p.First_Name, p.Last_Name
            """;

        List<JsonObject> stakeholders = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject stakeholder = new JsonObject();
                    int peopleId = rs.getInt("PeopleID");
                    int roleId = rs.getInt("RoleID");
                    
                    stakeholder.addProperty("ObjectXPeopleID", rs.getInt("ObjectXPeopleID"));
                    stakeholder.addProperty("PersonName", rs.getString("PersonName"));
                    stakeholder.addProperty("RoleName", rs.getString("RoleName"));
                    stakeholder.addProperty("RoleID", roleId);
                    stakeholder.addProperty("StatusName", rs.getString("StatusName"));
                    stakeholder.addProperty("StatusID", rs.getInt("StatusID"));
                    stakeholder.addProperty("PeopleID", peopleId);
                    stakeholder.addProperty("PersonEmail", rs.getString("PersonEmail"));
                    stakeholder.addProperty("Department", rs.getString("Department"));
                    // Handle OrgUnitID - can be NULL
                    int orgUnitID = rs.getInt("OrgUnitID");
                    if (rs.wasNull()) {
                        stakeholder.add("OrgUnitID", null);
                    } else {
                        stakeholder.addProperty("OrgUnitID", orgUnitID);
                    }
                    // Handle DelegateOfId - can be NULL
                    int delegateOfId = rs.getInt("DelegateOfId");
                    if (rs.wasNull()) {
                        stakeholder.add("DelegateOfId", null);
                    } else {
                        stakeholder.addProperty("DelegateOfId", delegateOfId);
                    }
                    // Handle DelegateOf - can be NULL
                    String delegateOf = rs.getString("DelegateOf");
                    if (delegateOf != null) {
                        stakeholder.addProperty("DelegateOf", delegateOf);
                    } else {
                        stakeholder.add("DelegateOf", null);
                    }
                    
                    // Validate role assignment for default roles
                    try {
                        DefaultStakeholderUtil.ValidationResult validation =
                            DefaultStakeholderUtil.validateStakeholderRoleAssignment(conn, peopleId, roleId);
                        stakeholder.addProperty("roleAssignmentValid", validation.isValid());
                        if (!validation.isValid() && validation.getWarningMessage() != null) {
                            stakeholder.addProperty("roleAssignmentWarning", validation.getWarningMessage());
                        }
                        stakeholder.addProperty("isDefaultOnlyAssignment", DefaultStakeholderUtil.isDefaultOnlyStakeholder(conn, roleId));
                    } catch (SQLException e) {
                        // If validation fails, assume valid to not break existing functionality
                        stakeholder.addProperty("roleAssignmentValid", true);
                        logger.warn("Error validating role assignment for user {} role {}: {}", 
                            peopleId, roleId, e.getMessage());
                    }
                    
                    stakeholders.add(stakeholder);
                }
            }
        }

        return stakeholders;
    }

    // Get module ID by module name
    private int getModuleId(Connection conn, String moduleName) throws SQLException {
        String sql = "SELECT id FROM module WHERE primaryname = ?";
        //system.out.println("DatasetStakeholderServlet - Looking for module: '" + moduleName + "'");

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, moduleName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int moduleId = rs.getInt("id");
                    //system.out.println("DatasetStakeholderServlet - Found module ID: " + moduleId + " for '" + moduleName + "'");
                    return moduleId;
                }
            }
        }

        //system.out.println("DatasetStakeholderServlet - Module not found: " + moduleName);
        throw new SQLException("Module not found: " + moduleName);
    }

    // Get dataset roles
    private List<JsonObject> getDatasetRoles(Connection conn) throws SQLException {
        String sql = """
            SELECT 
                orl.ID AS RoleID,
                orl.PrimaryName AS Role
            FROM object_role orl
            WHERE orl.module = ?
            ORDER BY orl.PrimaryName
            """;

        List<JsonObject> roles = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int moduleId = getModuleId(conn, "Data Sets");
            //system.out.println("DatasetStakeholderServlet - Module ID for 'Data Sets': " + moduleId);
            stmt.setInt(1, moduleId);

            try (ResultSet rs = stmt.executeQuery()) {
                //system.out.println("DatasetStakeholderServlet - Executing query for roles with module ID: " + moduleId);
                int count = 0;
                while (rs.next()) {
                    count++;
                    JsonObject role = new JsonObject();
                    role.addProperty("RoleID", rs.getInt("RoleID"));
                    role.addProperty("Role", rs.getString("Role"));
                    roles.add(role);
                    //system.out.println("DatasetStakeholderServlet - Found role: " + rs.getString("Role") + " (ID: " + rs.getInt("RoleID") + ")");
                }
                //system.out.println("DatasetStakeholderServlet - Total roles found: " + count);
            }
        }

        return roles;
    }

    // Get role statuses (from object_x_ip_status table for edit mode)
    private List<JsonObject> getRoleStatuses(Connection conn) throws SQLException {
        String sql = "SELECT ID, PrimaryName FROM object_x_ip_status ORDER BY PrimaryName";
        //system.out.println("DatasetStakeholderServlet - Getting role statuses...");

        List<JsonObject> statuses = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    JsonObject status = new JsonObject();
                    status.addProperty("ID", rs.getInt("ID"));
                    status.addProperty("PrimaryName", rs.getString("PrimaryName"));
                    statuses.add(status);
                    //system.out.println("DatasetStakeholderServlet - Found status: " + rs.getString("PrimaryName") + " (ID: " + rs.getInt("ID") + ")");
                }
                //system.out.println("DatasetStakeholderServlet - Total statuses found: " + count);
            }
        }

        return statuses;
    }

    // Get people by role - Enhanced version with better error handling
    private List<JsonObject> getPeopleByRole(Connection conn, int roleId) throws SQLException {
        //system.out.println("DatasetStakeholderServlet - Getting people for role ID: " + roleId);
        
        // First, verify that the role exists
        String checkRoleQuery = "SELECT id, primaryname, module FROM object_role WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(checkRoleQuery)) {
            stmt.setInt(1, roleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    //system.out.println("DatasetStakeholderServlet - Role ID " + roleId + " does not exist");
                    return new ArrayList<>();
                }
                //system.out.println("DatasetStakeholderServlet - Found role: " + rs.getString("primaryname") + " (Module ID: " + rs.getInt("module") + ")");
            }
        }
        
        // Check all role assignments for this role
        String checkAssignmentsQuery = "SELECT id, objectroleid, users FROM role_assignment WHERE objectroleid = ?";
        //system.out.println("DatasetStakeholderServlet - Checking role assignments for role ID: " + roleId);
        
        try (PreparedStatement stmt = conn.prepareStatement(checkAssignmentsQuery)) {
            stmt.setInt(1, roleId);
            try (ResultSet rs = stmt.executeQuery()) {
                boolean foundAssignment = false;
                while (rs.next()) {
                    foundAssignment = true;
                    //system.out.println("DatasetStakeholderServlet - Found assignment ID: " + rs.getInt("id") + 
                             //        ", Role ID: " + rs.getInt("objectroleid") +
                               //      ", Users: " + rs.getString("users"));
                }
                if (!foundAssignment) {
                    //system.out.println("DatasetStakeholderServlet - No role assignment found for role ID: " + roleId);
                    
                    // Let's also check what role assignments exist in general
                    //system.out.println("DatasetStakeholderServlet - Checking all role assignments...");
                    String allAssignmentsQuery = "SELECT id, objectroleid, users FROM role_assignment ORDER BY objectroleid";
                    try (PreparedStatement allStmt = conn.prepareStatement(allAssignmentsQuery);
                         ResultSet allRs = allStmt.executeQuery()) {
                        while (allRs.next()) {
                            //system.out.println("DatasetStakeholderServlet - Available assignment: ID=" + allRs.getInt("id") + 
                                        //     ", RoleID=" + allRs.getInt("objectroleid") +
                                      //       ", Users=" + allRs.getString("users"));
                        }
                    }
                    
                    return new ArrayList<>();
                }
            }
        }
        
        // Get the users JSON from role_assignment
        String getUsersQuery = "SELECT users FROM role_assignment WHERE objectroleid = ?";
        List<Integer> userIds = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(getUsersQuery)) {
            stmt.setInt(1, roleId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String usersJson = rs.getString("users");
                    //system.out.println("DatasetStakeholderServlet - Found users JSON: " + usersJson);
                    
                    if (usersJson != null && !usersJson.trim().isEmpty()) {
                        try {
                            usersJson = usersJson.trim();
                            // Remove brackets if present
                            if (usersJson.startsWith("[") && usersJson.endsWith("]")) {
                                usersJson = usersJson.substring(1, usersJson.length() - 1);
                            }
                            
                            if (!usersJson.isEmpty()) {
                                String[] userIdStrings = usersJson.split(",");
                                for (String userIdStr : userIdStrings) {
                                    try {
                                        int userId = Integer.parseInt(userIdStr.trim());
                                        userIds.add(userId);
                                        //system.out.println("DatasetStakeholderServlet - Added user ID: " + userId);
                                    } catch (NumberFormatException e) {
                                        //system.out.println("DatasetStakeholderServlet - Skipping invalid user ID: " + userIdStr);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            //system.out.println("DatasetStakeholderServlet - Error parsing users JSON: " + e.getMessage());
                        }
                    }
                } else {
                    //system.out.println("DatasetStakeholderServlet - No role assignment found for role ID: " + roleId);
                }
            }
        }
        
        if (userIds.isEmpty()) {
            //system.out.println("DatasetStakeholderServlet - No user IDs found for role ID: " + roleId);
            return new ArrayList<>();
        }
        
        // Now get the people details
        String sql = """
            SELECT
                p.ID AS PeopleID,
                CONCAT(p.First_Name, ' ', p.Last_Name, ' (', p.Email, ')') AS Name
            FROM people p
            WHERE p.ID IN (%s)
            AND p.Deleted_date IS NULL
            ORDER BY p.First_Name, p.Last_Name
            """;
        
        // Create placeholders for IN clause
        String placeholders = userIds.stream()
            .map(id -> "?")
            .reduce((a, b) -> a + "," + b)
            .orElse("");
        
        sql = String.format(sql, placeholders);
        //system.out.println("DatasetStakeholderServlet - Executing query: " + sql);
        //system.out.println("DatasetStakeholderServlet - With user IDs: " + userIds);

        List<JsonObject> people = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Set the user IDs as parameters
            for (int i = 0; i < userIds.size(); i++) {
                stmt.setInt(i + 1, userIds.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    JsonObject person = new JsonObject();
                    person.addProperty("PeopleID", rs.getInt("PeopleID"));
                    person.addProperty("Name", rs.getString("Name"));
                    people.add(person);
                    //system.out.println("DatasetStakeholderServlet - Found person: " + rs.getString("Name") + " (ID: " + rs.getInt("PeopleID") + ")");
                }
                //system.out.println("DatasetStakeholderServlet - Total people found: " + count);
            }
        }

        return people;
    }

    // Get people by role with segment filtering
    private List<JsonObject> getPeopleByRoleWithSegmentFilter(Connection conn, int roleId, int objectId) throws SQLException {
        List<JsonObject> people = new ArrayList<>();
        
        try {
            List<Map<String, Object>> users = SegmentAccessService.getUsersForStakeholderSelection(objectId, "Dataset", roleId);
            
            for (Map<String, Object> user : users) {
                JsonObject person = new JsonObject();
                person.addProperty("PeopleID", (Integer) user.get("PeopleID"));
                person.addProperty("Name", (String) user.get("Name"));
                people.add(person);
            }
        } catch (SQLException e) {
            logger.error("Error getting users for stakeholder selection: {}", e.getMessage(), e);
            // Fallback to original behavior on error
            return getPeopleByRole(conn, roleId);
        }
        
        return people;
    }

    // Add stakeholder
    private boolean addStakeholder(Connection conn, int datasetId, JsonObject data, int currentUserId) throws SQLException {
        conn.setAutoCommit(false);

        try {
            // Insert into object_x_people
            String insertOXP = """
                INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                VALUES (?, ?, ?, 2, 1, ?)
                """;

            int objectXPeopleId;
            try (PreparedStatement stmt = conn.prepareStatement(insertOXP, Statement.RETURN_GENERATED_KEYS)) {
                // Handle delegateIpId (delegateOfId) for new stakeholder
                Integer delegateOfId = null;
                if (data.has("delegateIpId") && !data.get("delegateIpId").isJsonNull()) {
                    delegateOfId = data.get("delegateIpId").getAsInt();
                }
                if (delegateOfId != null) {
                    stmt.setInt(1, delegateOfId);
                } else {
                    stmt.setNull(1, Types.INTEGER);
                }
                stmt.setInt(2, data.get("ipid").getAsInt()); // ipid
                stmt.setInt(3, data.get("roleID").getAsInt()); // RoleID
                stmt.setInt(4, currentUserId); // lastupdateuser_id

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("Failed to insert object_x_people");
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        objectXPeopleId = generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Failed to get generated key for object_x_people");
                    }
                }
            }

            // Insert into dataset_x_objectxpeople
            String insertDXOP = """
                INSERT INTO dataset_x_objectxpeople (Object_x_ipid, Dataset_ID, Last_UpdateUser_ID)
                VALUES (?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(insertDXOP)) {
                stmt.setInt(1, objectXPeopleId);
                stmt.setInt(2, datasetId);
                stmt.setInt(3, currentUserId);

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("Failed to insert dataset_x_objectxpeople");
                }
            }

            // Insert audit records BEFORE commit
            String userName = "System";
            String fullName = getUserFullName(data.get("ipid").getAsInt());
            String roleName = getRoleNameById(conn, data.get("roleID").getAsInt());
            String statusName = getStatusNameById(conn, data.get("statusID").getAsInt());
            insertStakeholderAudit(conn, datasetId, "Name", null, fullName, "link", "Added", userName);
            insertStakeholderAudit(conn, datasetId, "Role", null, roleName, "link", "Added", userName);
            insertStakeholderAudit(conn, datasetId, "Role Status", null, statusName, "link", "Added", userName);
            
            conn.commit();
            
            // Create role notification after successful commit using helper
            int userId = data.get("ipid").getAsInt();
            int roleId = data.get("roleID").getAsInt();
            com.example.budg_v2.util.RoleNotificationHelper.createNotificationAfterStakeholderAdded(
                "Data Set", datasetId, userId, roleId, objectXPeopleId, conn
            );
            
            return true;

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // Update stakeholder
    private boolean updateStakeholder(Connection conn, JsonObject data, int currentUserId) throws SQLException {
        int objectXPeopleId = data.get("objectXPeopleId").getAsInt();
        int datasetId = getDatasetIdForStakeholder(conn, objectXPeopleId);
        String userName = "System";
        try { userName = getCurrentUserNameAttribute(conn); } catch (Exception ignore) {}
        StakeholderSnapshot oldData = getStakeholderInfo(conn, objectXPeopleId);
        
        String sql = """
            UPDATE object_x_people 
            SET ipid = ?, roleID = ?, statusID = ?, isDelegateOF = ?, lastupdatedatetime = NOW(), lastupdateuser_id = ?
            WHERE id = ?
            """;
        
        int ipid = data.get("ipid").getAsInt();
        int roleID = data.get("roleID").getAsInt();
        int statusID = data.get("statusID").getAsInt();
        
        // Handle delegateIpId (delegateOfId)
        Integer delegateOfId = null;
        if (data.has("delegateIpId") && !data.get("delegateIpId").isJsonNull()) {
            delegateOfId = data.get("delegateIpId").getAsInt();
        }
        
        boolean changed = false;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ipid);
            stmt.setInt(2, roleID);
            stmt.setInt(3, statusID);
            if (delegateOfId != null) {
                stmt.setInt(4, delegateOfId);
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setInt(5, currentUserId);
            stmt.setInt(6, objectXPeopleId);

            int rows = stmt.executeUpdate();
            if (rows == 0) return false;
            changed = true;
        }
        
        StakeholderSnapshot newData = getStakeholderInfo(conn, objectXPeopleId);
        if (changed && oldData != null && newData != null) {
            if (!safeEquals(oldData.fullName, newData.fullName))
                insertStakeholderAudit(conn, datasetId, "Name", oldData.fullName, newData.fullName, "edit", "Updated", userName);
            if (!safeEquals(oldData.roleName, newData.roleName))
                insertStakeholderAudit(conn, datasetId, "Role", oldData.roleName, newData.roleName, "edit", "Updated", userName);
            if (!safeEquals(oldData.statusName, newData.statusName))
                insertStakeholderAudit(conn, datasetId, "Role Status", oldData.statusName, newData.statusName, "edit", "Updated", userName);
        }
        return true;
    }

    // Delete stakeholder
    private boolean deleteStakeholder(Connection conn, int objectXPeopleId) throws SQLException {
        int datasetId = getDatasetIdForStakeholder(conn, objectXPeopleId);
        
        // Check if object has CRs that block stakeholder deletion
        // Auto CRs with "Pending Start" status allow stakeholder modification
        Integer facetTypeId = facetChangesDAO.getFacetId("dataset");
        if (facetTypeId != null && facetChangesDAO.hasActiveCRsBlockingStakeholderDeletion(facetTypeId, datasetId)) {
            throw new SQLException("Cannot delete stakeholder: The object has a Running or Pending Start change request. Please complete or cancel the change request first.");
        }
        
        StakeholderSnapshot oldData = getStakeholderInfo(conn, objectXPeopleId);
        String userName = "System";
        try { userName = getCurrentUserNameAttribute(conn); } catch (Exception ignore) {}
        
        conn.setAutoCommit(false);

        try {
            // Delete from dataset_x_objectxpeople first (foreign key constraint)
            String deleteDXOP = "DELETE FROM dataset_x_objectxpeople WHERE Object_x_ipid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteDXOP)) {
                stmt.setInt(1, objectXPeopleId);
                stmt.executeUpdate();
            }

            // Delete from object_x_people
            String deleteOXP = "DELETE FROM object_x_people WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteOXP)) {
                stmt.setInt(1, objectXPeopleId);
                int rowsAffected = stmt.executeUpdate();

                if (rowsAffected == 0) {
                    throw new SQLException("No stakeholder found with the given ID");
                }
            }

            // Insert audit records for deletion
            if (oldData != null) {
                insertStakeholderAudit(conn, datasetId, "Name", oldData.fullName, null, "delete", "Deleted", userName);
                insertStakeholderAudit(conn, datasetId, "Role", oldData.roleName, null, "delete", "Deleted", userName);
                insertStakeholderAudit(conn, datasetId, "Role Status", oldData.statusName, null, "delete", "Deleted", userName);
            }
            
            conn.commit();
            return true;

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // Get request body as string
    private String getRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    // Extract dataset ID from request URI
    private int extractDatasetId(String requestURI) {
        String[] uriParts = requestURI.split("/");
        for (int i = 0; i < uriParts.length; i++) {
            if ("dataset-stakeholder".equals(uriParts[i]) && i + 1 < uriParts.length) {
                try {
                    return Integer.parseInt(uriParts[i + 1]);
                } catch (NumberFormatException e) {
                    // Continue searching
                }
            }
        }
        return -1;
    }

    // Get current user ID from session/authentication
    private int getCurrentUserId(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj instanceof Integer) {
            return (Integer) userIdObj;
        }
        return -1; // Fallback if no user ID found
    }

    private String getUserFullName(int userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("fullName");
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting user full name: " + e.getMessage());
        }
        return "Unknown User";
    }

    // ============= HELPER CLASSES/FUNCTIONS FOR AUDIT =============
    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equals(b.trim());
    }

    private StakeholderSnapshot getStakeholderInfo(Connection conn, int objectXPeopleId) throws SQLException {
        String sql = """
            SELECT oxp.id, ipid, CONCAT(p.First_Name, ' ', p.Last_Name) as fullName, 
              orl.primaryname as roleName, ips.primaryname as statusName
            FROM object_x_people oxp
            LEFT JOIN people p ON oxp.ipid = p.ID
            LEFT JOIN object_role orl ON oxp.roleID = orl.ID
            LEFT JOIN object_x_ip_status ips ON oxp.statusID = ips.ID
            WHERE oxp.id = ?
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectXPeopleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    StakeholderSnapshot s = new StakeholderSnapshot();
                    s.fullName = rs.getString("fullName");
                    s.roleName = rs.getString("roleName");
                    s.statusName = rs.getString("statusName");
                    return s;
                }
            }
        }
        return null;
    }

    private int getDatasetIdForStakeholder(Connection conn, int objectXPeopleId) throws SQLException {
        String sql = "SELECT Dataset_ID FROM dataset_x_objectxpeople WHERE Object_x_ipid = ? ORDER BY id DESC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectXPeopleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("Dataset_ID");
            }
        }
        return -1;
    }

    /**
     * Build reference string in the format "FacetName ObjectId" (e.g., "Dataset 15")
     */
    @SuppressWarnings("unused")
    private String buildReference(String facetType, int facetId) {
        String normalizedType = facetType.toLowerCase().replace("-", " ");
        String[] words = normalizedType.split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            if (words[i].length() > 0) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1).toLowerCase());
                }
            }
        }
        
        result.append(" ").append(facetId);
        return result.toString();
    }

    private String getCurrentUserNameAttribute(Connection conn) { 
        return "System"; 
    }

    private static class StakeholderSnapshot {
        String fullName; 
        String roleName; 
        String statusName;
    }

    private void insertStakeholderAudit(Connection conn, int datasetId, String field, String oldValue, String newValue, String event, String updateType, String userName) throws SQLException {
        String sql = "INSERT INTO dataset_audit_history (id, object, event, updateType, field, `from`, `to`, author) VALUES (?, 'Stakeholder', ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            stmt.setString(2, event);
            stmt.setString(3, updateType);
            stmt.setString(4, field);
            stmt.setString(5, oldValue);
            stmt.setString(6, newValue);
            stmt.setString(7, userName);
            stmt.executeUpdate();
        }
    }

    private String getRoleNameById(Connection conn, int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, roleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    private String getStatusNameById(Connection conn, int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM object_x_ip_status WHERE id=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, statusId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    private boolean acceptStakeholderStatus(Connection conn, int objectXPeopleId, int currentUserId) throws SQLException {
        //system.out.println("Accepting stakeholder status - objectXPeopleId: " + objectXPeopleId + ", currentUserId: " + currentUserId);
        
        String verifySql = "SELECT ipid FROM object_x_people WHERE id = ?";
        try (PreparedStatement verifyStmt = conn.prepareStatement(verifySql)) {
            verifyStmt.setInt(1, objectXPeopleId);
            try (ResultSet rs = verifyStmt.executeQuery()) {
                if (!rs.next()) {
                    //system.out.println("Stakeholder not found with objectXPeopleId: " + objectXPeopleId);
                    return false;
                }
                int stakeholderUserId = rs.getInt("ipid");
                if (stakeholderUserId != currentUserId) {
                    //system.out.println("Security violation: User " + currentUserId + " tried to update stakeholder for user " + stakeholderUserId);
                    throw new SQLException("Users can only update their own stakeholder status");
                }
            }
        }

        int datasetId = getDatasetIdForStakeholder(conn, objectXPeopleId);
        StakeholderSnapshot oldData = getStakeholderInfo(conn, objectXPeopleId);
        String userName = "System";
        try { userName = getCurrentUserNameAttribute(conn); } catch (Exception ignore) {}
        
        conn.setAutoCommit(false);

        try {
            String updateSql = """
                UPDATE object_x_people 
                SET AcceptedID = 1, lastupdatedatetime = NOW(), lastupdateuser_id = ?
                WHERE id = ? AND ipid = ?
                """;
            
            int rowsAffected;
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setInt(1, currentUserId);
                stmt.setInt(2, objectXPeopleId);
                stmt.setInt(3, currentUserId);
                
                rowsAffected = stmt.executeUpdate();
            }

            if (rowsAffected == 0) {
                throw new SQLException("No stakeholder updated with objectXPeopleId: " + objectXPeopleId);
            }

            if (oldData != null) {
                insertStakeholderAudit(conn, datasetId, "Role Status", oldData.statusName, "Yes", "status", "Accepted", userName);
            }
            
            conn.commit();
            //system.out.println("Stakeholder status accepted successfully");
            return true;

        } catch (SQLException e) {
            System.err.println("Error accepting stakeholder status: " + e.getMessage());
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * Initialize stakeholders in cloned dataset by copying all stakeholders from original dataset.
     * This is called when viewing/editing stakeholders in "changes" mode for the first time.
     */
    @SuppressWarnings("unused")
    private void initializeStakeholdersInClonedDataset(Connection conn, int originalDatasetId, int clonedDatasetId, int currentUserId) throws SQLException {
        // Get all stakeholders from original dataset
        String getOriginalSql = """
            SELECT oxp.id, oxp.ipid, oxp.roleID, oxp.AcceptedID, oxp.statusID
            FROM dataset_x_objectxpeople dxop
            JOIN object_x_people oxp ON oxp.id = dxop.Object_x_ipid
            WHERE dxop.Dataset_ID = ?
            """;
        
        List<Integer> originalStakeholderIds = new ArrayList<>();
        Map<Integer, StakeholderData> stakeholderDataMap = new HashMap<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(getOriginalSql)) {
            stmt.setInt(1, originalDatasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int oxpId = rs.getInt("id");
                    originalStakeholderIds.add(oxpId);
                    StakeholderData data = new StakeholderData();
                    data.ipid = rs.getInt("ipid");
                    data.roleID = rs.getInt("roleID");
                    data.acceptedID = rs.getInt("AcceptedID");
                    if (rs.wasNull()) data.acceptedID = 2;
                    data.statusID = rs.getInt("statusID");
                    if (rs.wasNull()) data.statusID = 1;
                    stakeholderDataMap.put(oxpId, data);
                }
            }
        }
        
        if (originalStakeholderIds.isEmpty()) {
            logger.info("No stakeholders to copy from original dataset {}", originalDatasetId);
            return;
        }
        
        conn.setAutoCommit(false);
        try {
            // Copy each stakeholder to cloned dataset
            for (Integer originalOxpId : originalStakeholderIds) {
                StakeholderData data = stakeholderDataMap.get(originalOxpId);
                
                // Insert into object_x_people
                String insertOXP = """
                    INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                    VALUES (NULL, ?, ?, ?, ?, ?)
                    """;
                
                int newObjectXPeopleId = -1;
                try (PreparedStatement stmt = conn.prepareStatement(insertOXP, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, data.ipid);
                    stmt.setInt(2, data.roleID);
                    stmt.setInt(3, data.acceptedID);
                    stmt.setInt(4, data.statusID);
                    stmt.setInt(5, currentUserId);
                    
                    stmt.executeUpdate();
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            newObjectXPeopleId = generatedKeys.getInt(1);
                        }
                    }
                }
                
                // Insert into dataset_x_objectxpeople with cloned dataset ID
                String insertDXOP = """
                    INSERT INTO dataset_x_objectxpeople (Object_x_ipid, Dataset_ID, Last_UpdateUser_ID)
                    VALUES (?, ?, ?)
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(insertDXOP)) {
                    stmt.setInt(1, newObjectXPeopleId);
                    stmt.setInt(2, clonedDatasetId);
                    stmt.setInt(3, currentUserId);
                    stmt.executeUpdate();
                }
            }
            
            conn.commit();
            logger.info("Copied {} stakeholders from dataset {} to cloned dataset {}", 
                       originalStakeholderIds.size(), originalDatasetId, clonedDatasetId);
        } catch (SQLException e) {
            conn.rollback();
            logger.error("Error copying stakeholders from dataset {} to cloned dataset {}: {}", 
                        originalDatasetId, clonedDatasetId, e.getMessage());
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Helper class to hold stakeholder data during initialization
     */
    private static class StakeholderData {
        int ipid;
        int roleID;
        int acceptedID;
        int statusID;
    }
    
    /**
     * Get dataset type ID for a given dataset
     */
    @SuppressWarnings("unused")
    private Integer getDatasetType(Connection conn, int datasetId) throws SQLException {
        String sql = "SELECT DatasetType FROM dataset WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int typeId = rs.getInt("DatasetType");
                    return rs.wasNull() ? null : typeId;
                }
            }
        }
        return null;
    }
    
    /**
     * Clone a dataset row for pending changes
     */
    @SuppressWarnings("unused")
    private Integer cloneDatasetRow(int originalId) throws SQLException {
        String sql = "INSERT INTO dataset (" +
                "RefNumber, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                "status, DatasetType, AccessControlType, lifecycle, " +
                "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID" +
                ") SELECT " +
                "RefNumber, PrimaryName, definition, MasterSource, glossary, `Usage`, " +
                "status, DatasetType, AccessControlType, lifecycle, " +
                "Createdby_ID, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID " +
                "FROM dataset WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Find if a stakeholder already exists in the cloned dataset
     */
    @SuppressWarnings("unused")
    private int findStakeholderInClonedDataset(Connection conn, int originalObjectXPeopleId, int clonedDatasetId) throws SQLException {
        String getOriginalSql = """
            SELECT oxp.ipid, oxp.roleID 
            FROM object_x_people oxp
            JOIN dataset_x_objectxpeople dxop ON dxop.Object_x_ipid = oxp.id
            WHERE oxp.id = ?
            """;
        
        int ipid = -1;
        int roleID = -1;
        try (PreparedStatement stmt = conn.prepareStatement(getOriginalSql)) {
            stmt.setInt(1, originalObjectXPeopleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ipid = rs.getInt("ipid");
                    roleID = rs.getInt("roleID");
                } else {
                    return -1;
                }
            }
        }
        
        String findSql = """
            SELECT oxp.id
            FROM object_x_people oxp
            JOIN dataset_x_objectxpeople dxop ON dxop.Object_x_ipid = oxp.id
            WHERE dxop.Dataset_ID = ? AND oxp.ipid = ? AND oxp.roleID = ?
            LIMIT 1
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(findSql)) {
            stmt.setInt(1, clonedDatasetId);
            stmt.setInt(2, ipid);
            stmt.setInt(3, roleID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }
    
    /**
     * Clone a stakeholder from original dataset to cloned dataset
     */
    @SuppressWarnings("unused")
    private int cloneStakeholderToDataset(Connection conn, int originalObjectXPeopleId, int clonedDatasetId, int currentUserId) throws SQLException {
        String getOriginalSql = """
            SELECT oxp.ipid, oxp.roleID, oxp.AcceptedID, oxp.statusID
            FROM object_x_people oxp
            WHERE oxp.id = ?
            """;
        
        int ipid = -1;
        int roleID = -1;
        int acceptedID = 2;
        int statusID = 1;
        
        try (PreparedStatement stmt = conn.prepareStatement(getOriginalSql)) {
            stmt.setInt(1, originalObjectXPeopleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ipid = rs.getInt("ipid");
                    roleID = rs.getInt("roleID");
                    acceptedID = rs.getInt("AcceptedID");
                    if (rs.wasNull()) acceptedID = 2;
                    statusID = rs.getInt("statusID");
                    if (rs.wasNull()) statusID = 1;
                } else {
                    return -1;
                }
            }
        }
        
        conn.setAutoCommit(false);
        try {
            String insertOXP = """
                INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                VALUES (NULL, ?, ?, ?, ?, ?)
                """;
            
            int newObjectXPeopleId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(insertOXP, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, ipid);
                stmt.setInt(2, roleID);
                stmt.setInt(3, acceptedID);
                stmt.setInt(4, statusID);
                stmt.setInt(5, currentUserId);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("Failed to insert cloned object_x_people");
                }
                
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        newObjectXPeopleId = generatedKeys.getInt(1);
                    }
                }
            }
            
            String insertDXOP = """
                INSERT INTO dataset_x_objectxpeople (Object_x_ipid, Dataset_ID, Last_UpdateUser_ID)
                VALUES (?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertDXOP)) {
                stmt.setInt(1, newObjectXPeopleId);
                stmt.setInt(2, clonedDatasetId);
                stmt.setInt(3, currentUserId);
                stmt.executeUpdate();
            }
            
            conn.commit();
            return newObjectXPeopleId;
            
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
}