package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.DefaultStakeholderUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
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
import java.util.List;
import java.util.Map;

/**
 * Product Stakeholder Servlet for handling stakeholder CRUD operations
 * Endpoints: /api/product/{id}/stakeholders, /api/product/stakeholder/lookup
 */
@WebServlet({"/api/product-stakeholder/*", "/api/product/stakeholder/lookup"})
public class ProductStakeholderServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(ProductStakeholderServlet.class);
    private Gson gson = new Gson();
    @SuppressWarnings("unused") // reserved for future change-request integration
    private ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
    private FacetChangesDAO facetChangesDAO = new FacetChangesDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String pathInfo = request.getPathInfo();
            String requestURI = request.getRequestURI();

            //system.out.println("ProductStakeholderServlet - Request URI: " + requestURI);
            //system.out.println("ProductStakeholderServlet - Path Info: " + pathInfo);

            // Handle stakeholder lookup requests
            if (requestURI.contains("/stakeholder/lookup")) {
                handleLookupRequest(request, response, conn, out);
                return;
            }

            // Handle stakeholders view and edit requests
            if (pathInfo != null && (pathInfo.matches("/\\d+/stakeholders(/edit)?") || pathInfo.equals("/1/stakeholders") || pathInfo.equals("/1/stakeholders/edit"))) {
                //system.out.println("Pattern matched! Processing stakeholders request...");
                int productId = extractProductId(requestURI);
                //system.out.println("Extracted product ID: " + productId);

                if (productId > 0) {
                    List<JsonObject> stakeholders = getProductStakeholders(conn, productId);
                    //system.out.println("Found " + stakeholders.size() + " stakeholders");
                    out.print(gson.toJson(stakeholders));
                } else {
                    //system.out.println("Could not extract product ID from URI: " + requestURI);
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"Invalid product ID\"}");
                }
            } else {
                //system.out.println("No matching pattern for pathInfo: " + pathInfo);
                //system.out.println("Expected pattern: /\\d+/stakeholders or /\\d+/stakeholders/edit");
                //system.out.println("Pattern test result: " + (pathInfo != null ? pathInfo.matches("/\\d+/stakeholders(/edit)?") : "pathInfo is null"));
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
                int productId = extractProductId(request.getRequestURI());

                if (productId <= 0) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"Invalid product ID\"}");
                    return;
                }

                // Parse JSON request body
                String requestBody = getRequestBody(request);
                JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();

                // Get current user ID (from authentication)
                int currentUserId = getCurrentUserId(request);
                
                // Check Edit permission + stakeholder status for Product
                if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Product", productId)) {
                    return; // Response already sent
                }

                // Block if current user has a default-only role on this object (cannot save until they remove it or admin assigns them)
                DefaultStakeholderUtil.ValidationResult canSave = DefaultStakeholderUtil.validateCurrentUserCanSaveStakeholders(
                        conn, "Product", productId, currentUserId, java.util.Collections.emptySet());
                if (!canSave.isValid()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"" + (canSave.getWarningMessage() != null ? canSave.getWarningMessage().replace("\"", "\\\"") : "") + "\"}");
                    return;
                }

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
                }

                // Validate stakeholder has access to object's segment
                if (jsonObject.has("ipid")) {
                    int personId = jsonObject.get("ipid").getAsInt();
                    SegmentValidationService segmentValidationService = new SegmentValidationService();
                    SegmentValidationService.ValidationResult segmentValidation =
                        segmentValidationService.validateStakeholderCanBeAdded(personId, productId, "Product");
                    if (!segmentValidation.isValid) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        out.print("{\"error\": " + gson.toJson(segmentValidation.message) + "}");
                        return;
                    }
                }
                
                // Add stakeholder
                boolean success = addStakeholder(conn, productId, jsonObject, currentUserId, request);

                if (success) {
                    out.print("{\"success\": true, \"message\": \"Stakeholder added successfully\"}");
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
                int productId = extractProductId(request.getRequestURI());

                if (productId <= 0) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"Invalid product ID\"}");
                    return;
                }

                // Parse JSON request body
                String requestBody = getRequestBody(request);
                JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();

                // Get current user ID (from authentication)
                int currentUserId = getCurrentUserId(request);

                // Block if current user has a default-only role on this object (cannot save until they remove it or admin assigns them)
                DefaultStakeholderUtil.ValidationResult canSave = DefaultStakeholderUtil.validateCurrentUserCanSaveStakeholders(
                        conn, "Product", productId, currentUserId, java.util.Collections.emptySet());
                if (!canSave.isValid()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"" + (canSave.getWarningMessage() != null ? canSave.getWarningMessage().replace("\"", "\\\"") : "") + "\"}");
                    return;
                }

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
                
                // Update stakeholder
                boolean success = updateStakeholder(conn, jsonObject, currentUserId);

                if (success) {
                    out.print("{\"success\": true, \"message\": \"Stakeholder updated successfully\"}");
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

            // Delete stakeholder
            boolean success = deleteStakeholder(conn, oxpId);

            if (success) {
                out.print("{\"success\": true, \"message\": \"Stakeholder deleted successfully\"}");
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
                int productId = extractProductId(requestURI);
                //system.out.println("PATCH - Extracted product ID: " + productId);

                if (productId <= 0) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\": \"Invalid product ID\"}");
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

        if (type == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\": \"Type parameter is required\"}");
            return;
        }

        List<JsonObject> results = new ArrayList<>();

        switch (type.toLowerCase()) {
            case "roles":
                results = getProductRoles(conn);
                break;
            case "rolestatus":
                results = getRoleStatuses(conn);
                break;
            case "people":
                String roleId = request.getParameter("roleId");
                String objectIdParam = request.getParameter("objectId");
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
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid type parameter\"}");
                return;
        }

        out.print(gson.toJson(results));
    }

    // Get product stakeholders (works for both VIEW and EDIT modes)
    private List<JsonObject> getProductStakeholders(Connection conn, int productId) throws SQLException {
        String sql = """
            SELECT 
                oxp.id AS ObjectXPeopleID,
                oxp.AcceptedID AS AcceptedID,
                oxp.isDelegateOF AS DelegateOfId,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS PersonName,
                orl.primaryname AS RoleName,
                orl.id AS RoleID,
                ips.primaryname AS StatusName,
                ips.id AS StatusID,
                p.ID AS PeopleID,
                p.Email AS PersonEmail,
                COALESCE(ou.Name, 'No Department') AS OrgUnit,
                ou.ID AS OrgUnitID,
                ra.Message AS RoleAccepted,
                CONCAT(delegate_p.First_Name, ' ', delegate_p.Last_Name) AS DelegateOf
            FROM product_x_objectxpeople pxop
            JOIN object_x_people oxp 
                ON oxp.id = pxop.object_x_ip
            JOIN people p 
                ON p.ID = oxp.ipid
            JOIN object_role orl 
                ON orl.id = oxp.roleID
            LEFT JOIN object_x_ip_status ips 
                ON ips.id = oxp.statusID
            LEFT JOIN org_unit ou 
                ON ou.ID = p.Org_Unit_ID
            LEFT JOIN roleaccepted ra 
                ON ra.ID = oxp.AcceptedID
            LEFT JOIN object_x_people d_oxp ON oxp.isDelegateOF = d_oxp.ID
            LEFT JOIN people delegate_p ON d_oxp.ipid = delegate_p.ID
            WHERE pxop.product_id = ?
            ORDER BY orl.primaryname, p.First_Name, p.Last_Name
            """;

        List<JsonObject> stakeholders = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);

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
                    // Handle StatusID - can be NULL
                    int statusID = rs.getInt("StatusID");
                    if (rs.wasNull()) {
                        stakeholder.add("StatusID", null);
                    } else {
                        stakeholder.addProperty("StatusID", statusID);
                    }
                    stakeholder.addProperty("PeopleID", peopleId);
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
                               //      ", AcceptedID: " + acceptedID +
                                //     ", RoleAccepted (ra.Message): " + roleAccepted +
                                 //    (roleAccepted == null ? " [NULL]" : " [NOT NULL]"));
                    
                    if (roleAccepted != null && !roleAccepted.trim().isEmpty()) {
                        stakeholder.addProperty("RoleAccepted", roleAccepted);
                    } else {
                        stakeholder.addProperty("RoleAccepted", ""); // Empty string instead of null
                        //system.out.println("Warning: RoleAccepted is null or empty for AcceptedID: " + acceptedID);
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

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, moduleName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }

        throw new SQLException("Module not found: " + moduleName);
    }

    // Get product roles
    private List<JsonObject> getProductRoles(Connection conn) throws SQLException {
        String sql = """
            SELECT 
                orl.id AS RoleID,
                orl.primaryname AS Role
            FROM object_role orl
            WHERE orl.module = ?
            ORDER BY orl.primaryname
            """;

        List<JsonObject> roles = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, getModuleId(conn, "Product"));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject role = new JsonObject();
                    role.addProperty("RoleID", rs.getInt("RoleID"));
                    role.addProperty("Role", rs.getString("Role"));
                    roles.add(role);
                }
            }
        }

        return roles;
    }

    // Get role statuses (from object_x_ip_status table for edit mode)
    private List<JsonObject> getRoleStatuses(Connection conn) throws SQLException {
        String sql = "SELECT id, primaryname FROM object_x_ip_status ORDER BY primaryname";

        List<JsonObject> statuses = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject status = new JsonObject();
                    status.addProperty("ID", rs.getInt("id"));
                    status.addProperty("PrimaryName", rs.getString("primaryname"));
                    statuses.add(status);
                }
            }
        }

        return statuses;
    }

    // Get people by role
    private List<JsonObject> getPeopleByRole(Connection conn, int roleId) throws SQLException {
        String sql = """
            SELECT
                p.ID AS PeopleID,
                CONCAT(p.First_Name, ' ', p.Last_Name, ' (', p.Email, ')') AS Name
            FROM role_assignment ra
            JOIN people p
                ON REPLACE(REPLACE(REPLACE(ra.users, '[', ''), ']', ''), ' ', '')
                   REGEXP CONCAT('(^|,)', p.ID, '(,|$)')
            WHERE ra.objectroleid = ?
            AND p.Deleted_date IS NULL
            ORDER BY p.First_Name, p.Last_Name
            """;

        List<JsonObject> people = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, roleId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject person = new JsonObject();
                    person.addProperty("PeopleID", rs.getInt("PeopleID"));
                    person.addProperty("Name", rs.getString("Name"));
                    people.add(person);
                }
            }
        }

        return people;
    }

    // Get people by role with segment filtering
    private List<JsonObject> getPeopleByRoleWithSegmentFilter(Connection conn, int roleId, int objectId) throws SQLException {
        List<JsonObject> people = new ArrayList<>();
        
        try {
            List<Map<String, Object>> users = SegmentAccessService.getUsersForStakeholderSelection(objectId, "Product", roleId);
            
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
    private boolean addStakeholder(Connection conn, int productId, JsonObject data, int currentUserId, HttpServletRequest request) throws SQLException {
        int ipid = data.get("ipid").getAsInt();
        int roleID = data.get("roleID").getAsInt();
        
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
                stmt.setInt(2, ipid); // ipid
                stmt.setInt(3, roleID); // RoleID
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

            // Insert into product_x_objectxpeople
            String insertPXOP = """
                INSERT INTO product_x_objectxpeople (object_x_ip, product_id, lastupdate_userid)
                VALUES (?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(insertPXOP)) {
                stmt.setInt(1, objectXPeopleId);
                stmt.setInt(2, productId);
                stmt.setInt(3, currentUserId);

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("Failed to insert product_x_objectxpeople");
                }
            }

            // Insert audit records BEFORE commit
            String userName = getCurrentUserName(request);
            String fullName = getUserFullName(ipid);
            String roleName = getRoleNameById(conn, roleID);
            String statusName = getStatusNameById(conn, data.get("statusID").getAsInt());
            insertStakeholderAudit(conn, productId, "Name", null, fullName, "link", "Added", userName);
            insertStakeholderAudit(conn, productId, "Role", null, roleName, "link", "Added", userName);
            insertStakeholderAudit(conn, productId, "Role Status", null, statusName, "link", "Added", userName);
            
            conn.commit();
            
            // Create role notification after successful commit
            com.example.budg_v2.util.RoleNotificationHelper.createNotificationAfterStakeholderAdded(
                "Product", productId, data.get("ipid").getAsInt(), 
                data.get("roleID").getAsInt(), objectXPeopleId, conn
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
        int productId = getProductIdForStakeholder(conn, objectXPeopleId);
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
                insertStakeholderAudit(conn, productId, "Name", oldData.fullName, newData.fullName, "edit", "Updated", userName);
            if (!safeEquals(oldData.roleName, newData.roleName))
                insertStakeholderAudit(conn, productId, "Role", oldData.roleName, newData.roleName, "edit", "Updated", userName);
            if (!safeEquals(oldData.statusName, newData.statusName))
                insertStakeholderAudit(conn, productId, "Role Status", oldData.statusName, newData.statusName, "edit", "Updated", userName);
        }
        return true;
    }

    // Delete stakeholder
    private boolean deleteStakeholder(Connection conn, int objectXPeopleId) throws SQLException {
        int productId = getProductIdForStakeholder(conn, objectXPeopleId);
        
        // Check if object has CRs that block stakeholder deletion
        // Auto CRs with "Pending Start" status allow stakeholder modification
        Integer facetTypeId = facetChangesDAO.getFacetId("product");
        if (facetTypeId != null && facetChangesDAO.hasActiveCRsBlockingStakeholderDeletion(facetTypeId, productId)) {
            throw new SQLException("Cannot delete stakeholder: The object has a Running or Pending Start change request. Please complete or cancel the change request first.");
        }
        
        StakeholderSnapshot oldData = getStakeholderInfo(conn, objectXPeopleId);
        String userName = "System";
        try { userName = getCurrentUserNameAttribute(conn); } catch (Exception ignore) {}
        
        conn.setAutoCommit(false);

        try {
            // Delete from product_x_objectxpeople first (foreign key constraint)
            String deletePXOP = "DELETE FROM product_x_objectxpeople WHERE object_x_ip = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deletePXOP)) {
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
                insertStakeholderAudit(conn, productId, "Name", oldData.fullName, null, "delete", "Deleted", userName);
                insertStakeholderAudit(conn, productId, "Role", oldData.roleName, null, "delete", "Deleted", userName);
                insertStakeholderAudit(conn, productId, "Role Status", oldData.statusName, null, "delete", "Deleted", userName);
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

    // Extract product ID from request URI
    private int extractProductId(String requestURI) {
        String[] uriParts = requestURI.split("/");
        for (int i = 0; i < uriParts.length; i++) {
            if ("product-stakeholder".equals(uriParts[i]) && i + 1 < uriParts.length) {
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

    // Get current user name from request
    private String getCurrentUserName(HttpServletRequest request) {
        Object userNameObj = request.getAttribute("userName");
        if (userNameObj != null) {
            return userNameObj.toString();
        }
        return "System"; // Fallback if no user name found
    }

    // Helper method to get user full name
    private String getUserFullName(int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return null;
    }

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

    private int getProductIdForStakeholder(Connection conn, int objectXPeopleId) throws SQLException {
        String sql = "SELECT product_id FROM product_x_objectxpeople WHERE object_x_ip = ? ORDER BY id DESC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectXPeopleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("product_id");
            }
        }
        return -1;
    }

    /**
     * Build reference string in the format "FacetName ObjectId" (e.g., "Product 5")
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

    private void insertStakeholderAudit(Connection conn, int productId, String field, String oldValue, String newValue, String event, String updateType, String userName) throws SQLException {
        String sql = "INSERT INTO product_audit_history (id, object, event, updateType, field, `from`, `to`, author) VALUES (?, 'Stakeholder', ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
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

        int productId = getProductIdForStakeholder(conn, objectXPeopleId);
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
                insertStakeholderAudit(conn, productId, "Role Status", oldData.statusName, "Yes", "status", "Accepted", userName);
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
}


