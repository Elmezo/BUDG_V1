package com.example.budg_v2;

import com.example.budg_v2.dao.ClientDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.service.ClientService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RequestedSegmentFilterUtil;
import com.example.budg_v2.util.ResponseSanitizer;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "ClientServlet", urlPatterns = { "/api/client", "/api/client/*", "/client", "/client/*",
        "/api/view/client", "/api/view/client/*", "/api/create/client", "/api/create/client/*" })
public class ClientServlet extends HttpServlet {

    private final ClientService clientService = new ClientService();
    private final ClientDAO clientDAO = new ClientDAO();
    private final SegmentDAO segmentDAO = new SegmentDAO();
    private final SegmentValidationService segmentValidationService = new SegmentValidationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();
            int userId = UserContextUtil.getCurrentUserId(request);

            if (path == null || path.equals("/")) {
                List<Map<String, Object>> clients = userId > 0 ? clientService.getAllClients(userId) : clientService.getAllClients();
                clients = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        clients,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Client",
                        client -> ((Number) client.get("id")).intValue());
                sendJson(response, clients);
            } else if (path.equals("/status-list")) {
                sendJson(response, clientService.getStatusList());
            } else if (path.equals("/lifecycle-list")) {
                sendJson(response, clientService.getLifecycleList());
            } else if (path.equals("/viewing-list")) {
                sendJson(response, clientService.getViewingList());
            } else if (path.equals("/parent-clients")) {
                sendJson(response,
                        userId > 0 ? clientService.getParentClients(userId) : clientService.getParentClients());
            } else if (path.equals("/search")) {
                String q = request.getParameter("q");
                if (q != null && !q.trim().isEmpty()) {
                    sendJson(response, clientService.searchClients(q.trim()));
                } else {
                    sendError(response, "Search query parameter 'q' is required", 400);
                }
            } else if (path.equals("/hierarchy")) {
                // Get all clients for hierarchy display
                //system.out.println("ClientServlet /hierarchy - Starting hierarchy request (userId: " + userId + ")");
                List<Map<String, Object>> clients = userId > 0 ? clientService.getAllClients(userId) : clientService.getAllClients();
                clients = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        clients,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Client",
                        client -> ((Number) client.get("id")).intValue());
                //system.out.println("ClientServlet /hierarchy - clients count: " + clients.size());
                
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Map<String, Object> clientMap : clients) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", (Integer) clientMap.get("id"));
                    o.addProperty("primaryName", (String) clientMap.get("primary_name"));
                    o.addProperty("description", (String) clientMap.get("definition"));
                    o.addProperty("parentId", (Integer) clientMap.get("parent_id"));
                    arr.add(o);
                    // system.out.println("ClientServlet: Added client to hierarchy: " +
                    // clientMap.get("primary_name") + " (ID: " + clientMap.get("id") + ", Parent: "
                    // + clientMap.get("parent_id") + ")");
                }
                // system.out.println("ClientServlet /hierarchy - JSON response size: " +
                // arr.size());
                response.getWriter().write(arr.toString());
            } else if (path.matches("/\\d+/stakeholders")) {
                String[] parts = path.split("/");
                int id = Integer.parseInt(parts[1]);
                Integer moduleId = null;
                try {
                    String moduleParam = request.getParameter("moduleId");
                    if (moduleParam != null && !moduleParam.isBlank())
                        moduleId = Integer.parseInt(moduleParam);
                } catch (Exception ignored) {
                }
                sendJson(response, clientService.getClientStakeholders(id, moduleId));
            } else if (path.matches("/\\d+")) {
                int id = Integer.parseInt(path.substring(1));
                Map<String, Object> client = clientService.getClientById(id);
                if (client != null) {
                    String statusName = null;
                    if (client.get("BUDG_status") != null) {
                        statusName = String.valueOf(client.get("BUDG_status"));
                    } else if (client.get("statusName") != null) {
                        statusName = String.valueOf(client.get("statusName"));
                    }
                    if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(statusName)) {
                        sendError(response, "This object is not available.", 403);
                        return;
                    }
                    SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "Client");
                    SegmentResponseUtil.applySegmentInfo(client, segmentInfo, request);
                    client.put("segmentRestricted", ResponseSanitizer.isStakeholderOnly(request));
                    sendJson(response, client);
                } else {
                    sendError(response, "Client not found", 404);
                }
            } else {
                sendError(response, "Invalid endpoint", 404);
            }
        } catch (NumberFormatException e) {
            sendError(response, "Invalid ID format", 400);
        } catch (Exception e) {
            sendError(response, e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
                // Check create permission for new client creation
                if (!PermissionCheckUtil.checkCreatePermission(request, response, "Client")) {
                    return; // Response already sent
                }

                JsonObject data = parseClientFromRequest(request);
                Map<String, Object> clientData = toMap(data);

                // Validate mandatory fields
                validateMandatoryFields(clientData);

                Object pnObj = clientData.get("primary_name");
                if (pnObj != null) {
                    String primaryName = String.valueOf(pnObj).trim();
                    if (!primaryName.isEmpty()) {
                        Integer segmentIdForName = clientData.get("segmentId") != null
                                ? ((Number) clientData.get("segmentId")).intValue()
                                : 1;
                        try (Connection conn = DatabaseConnection.getConnection()) {
                            if (SegmentScopedPrimaryNameCheck.exists(conn, "Client", primaryName,
                                    segmentIdForName.longValue(), null)) {
                                sendError(response, "A client with this name already exists in this segment.", 400);
                                return;
                            }
                        }
                    }
                }

                int userId = UserContextUtil.getCurrentUserId(request);

                // Validate segment hierarchy before creating client
                Integer parentId = clientData.get("parent_id") != null
                        ? ((Number) clientData.get("parent_id")).intValue() : null;
                Integer segmentId = clientData.get("segmentId") != null
                        ? ((Number) clientData.get("segmentId")).intValue() : 1;
                if (parentId != null && parentId > 0) {
                    try {
                        var hierarchyResult = segmentValidationService.validateParentChildSegment(parentId, segmentId, "Client");
                        if (!hierarchyResult.isValid) {
                            sendError(response, hierarchyResult.message, 400);
                            return;
                        }
                    } catch (Exception e) {
                        System.err.println("Error validating client hierarchy: " + e.getMessage());
                        sendError(response, "Error validating segment hierarchy: " + e.getMessage(), 500);
                        return;
                    }
                }

                Map<String, Object> createdClient = clientService.createClient(clientData, userId);

                // Assign client to segment
                Integer clientId = (Integer) createdClient.get("id");
                if (clientId != null) {
                    try {
                        segmentDAO.assignObjectToSegment(segmentId, clientId, "Client", userId > 0 ? userId : 1);
                        // system.out.println("✅ Client " + clientId + " assigned to segment " +
                        // segmentId);
                    } catch (Exception e) {
                        System.err.println("❌ Error assigning client to segment: " + e.getMessage());
                    }
                }

                // Assign creator role to the user for the new client, then write stakeholder audit with actual role
                if (userId > 0 && clientId != null) {
                    try {
                        assignCreatorRole(clientId, userId);
                        // Create stakeholder audit records with actual role (after assignCreatorRole so role exists in DB)
                        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                            int moduleId = com.example.budg_v2.util.DefaultStakeholderUtil.getModuleId(conn, "Client");
                            java.util.List<Integer> rolesToAssign = com.example.budg_v2.util.DefaultStakeholderUtil
                                    .getDefaultRolesForCreator(conn, moduleId, userId);
                            if (!rolesToAssign.isEmpty()) {
                                int roleId = rolesToAssign.get(0);
                                String userFullName = clientDAO.getPersonFullNameForAudit(userId);
                                if (userFullName != null) {
                                    clientDAO.createStakeholderAuditRecords(clientId, userFullName, userFullName, roleId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error assigning creator role or creating stakeholder audit: " + e.getMessage());
                        e.printStackTrace();
                        // Continue - don't fail the entire save
                    }
                } else if (clientId != null) {
                    System.err.println("⚠️ No valid userId in session, skipping role assignment");
                }

                sendJson(response, createdClient);
            } else {
                sendError(response, "Invalid endpoint", 404);
            }
        } catch (Exception e) {
            sendError(response, e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);

        // Check role-based edit permission first (skip for stakeholders updates)
        String path = request.getPathInfo();
        if (path == null || !path.matches("/\\d+/stakeholders")) {
            if (!PermissionCheckUtil.checkEditPermission(request, response, "Client")) {
                return; // Response already sent
            }
        }

        try {
            if (path != null && path.matches("/\\d+/stakeholders")) {
                // For now, just return success (stakeholders update not implemented yet)
                sendSuccess(response, "Stakeholders updated successfully");
            } else if (path != null && path.matches("/\\d+")) {
                int id = Integer.parseInt(path.substring(1));

                // Check segment-based edit permission
                int userId = UserContextUtil.getCurrentUserId(request);
                if (userId > 0) {
                    boolean canEdit = SegmentAccessService.canEditObject(userId, id, "Client");
                    if (!canEdit) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        sendError(response, "Access denied. You don't have permission to edit this client.", 403);
                        return;
                    }
                }

                JsonObject data = parseClientFromRequest(request);
                Map<String, Object> clientData = toMap(data);
                clientData.put("id", id);

                // Detect existence early so segment-only updates don't look like "not found"
                Map<String, Object> existing = clientService.getClientById(id);
                if (existing == null) {
                    sendError(response, "Client not found", 404);
                    return;
                }

                // Desired segment (optional)
                Object segObj = clientData.get("segmentId") != null ? clientData.get("segmentId")
                        : clientData.get("segment_id");
                Integer desiredSegmentId = segObj instanceof Number ? ((Number) segObj).intValue() : null;
                boolean segmentChanged = false;
                if (desiredSegmentId != null) {
                    try {
                        int currentSegmentId = segmentDAO.getObjectSegmentId(id, "Client");
                        segmentChanged = currentSegmentId != desiredSegmentId;
                    } catch (Exception ignored) {
                        segmentChanged = true;
                    }
                }

                Object pnUpd = clientData.get("primary_name");
                if (pnUpd != null) {
                    String primaryNameUpd = String.valueOf(pnUpd).trim();
                    if (!primaryNameUpd.isEmpty()) {
                        int curSeg = segmentDAO.getObjectSegmentId(id, "Client");
                        long effSeg = desiredSegmentId != null ? desiredSegmentId.longValue()
                                : (curSeg > 0 ? curSeg : 1L);
                        try (Connection conn = DatabaseConnection.getConnection()) {
                            if (SegmentScopedPrimaryNameCheck.exists(conn, "Client", primaryNameUpd, effSeg, id)) {
                                sendError(response, "A client with this name already exists in this segment.", 400);
                                return;
                            }
                        }
                    }
                }

                boolean coreUpdated = clientService.updateClient(clientData, userId);

                // Apply segment update even if core update had no changes
                // Always process segment update if desiredSegmentId is provided, regardless of
                // segmentChanged flag
                System.out.println("🔍 DEBUG ClientServlet.doPut - About to process segment. desiredSegmentId="
                        + desiredSegmentId);
                if (desiredSegmentId != null) {
                    try {
                        int currentSegmentId = segmentDAO.getObjectSegmentId(id, "Client");
                        System.out.println("🔍 DEBUG ClientServlet.doPut - Client ID=" + id + ", currentSegmentId="
                                + currentSegmentId + ", desiredSegmentId=" + desiredSegmentId);

                        // Only update if segment actually changed
                        if (currentSegmentId != desiredSegmentId) {
                            System.out.println("🔍 Segment change detected, validating...");

                            // Validate full segment-move rules (parent/children/linked impacts/stakeholders)
                            var validationResult = segmentValidationService.validateSegmentMove(
                                    id,
                                    desiredSegmentId,
                                    "Client",
                                    null
                            );
                            if (!validationResult.isValid) {
                                sendError(response, validationResult.message, 400);
                                return;
                            }

                            // Remove from old segment if it was assigned
                            if (currentSegmentId > 0 && currentSegmentId != -1) {
                                System.out.println("🔍 Removing client from segment " + currentSegmentId);
                                segmentDAO.removeObjectFromSegment(currentSegmentId, id, "Client",
                                        userId > 0 ? userId : 1);
                            }

                            // Assign to new segment (even if currentSegmentId is -1, we still assign)
                            System.out.println("🔍 Assigning client to segment " + desiredSegmentId);
                            segmentDAO.assignObjectToSegment(desiredSegmentId, id, "Client", userId > 0 ? userId : 1);

                            // Verify the assignment
                            int verifySegmentId = segmentDAO.getObjectSegmentId(id, "Client");
                            System.out.println("✅ Client " + id + " segment changed from " + currentSegmentId + " to "
                                    + desiredSegmentId + " (verified: " + verifySegmentId + ")");

                            // Update segmentChanged flag for the final validation check
                            segmentChanged = true;
                        } else {
                            System.out.println("⏭️ Segment unchanged (already " + desiredSegmentId + ")");
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error updating client segment: " + e.getMessage());
                        e.printStackTrace();
                        sendError(response, "Error updating client segment: " + e.getMessage(), 500);
                        return;
                    }
                }

                // Include segment changes in the validation check
                if (!coreUpdated && !segmentChanged) {
                    sendError(response, "No changes detected", 400);
                    return;
                }
                sendSuccess(response, "Client updated successfully");
            } else {
                sendError(response, "Invalid endpoint", 404);
            }
        } catch (NumberFormatException e) {
            sendError(response, "Invalid ID format", 400);
        } catch (Exception e) {
            sendError(response, e.getMessage(), 500);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();
            if (path != null && path.matches("/\\d+")) {
                int id = Integer.parseInt(path.substring(1));
                if (clientService.deleteClient(id, request)) {
                    sendSuccess(response, "Client deleted successfully");
                } else {
                    sendError(response, "Client not found or deletion failed", 404);
                }
            } else {
                sendError(response, "Invalid endpoint", 404);
            }
        } catch (NumberFormatException e) {
            sendError(response, "Invalid ID format", 400);
        } catch (Exception e) {
            sendError(response, e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void setupResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
    }

    private JsonObject parseClientFromRequest(HttpServletRequest request) throws IOException {
        return JsonUtil.parseJsonFromRequest(request.getReader());
    }

    private Map<String, Object> toMap(JsonObject json) {
        Map<String, Object> map = new HashMap<>();
        map.put("parent_id", JsonUtil.getJsonInt(json, "parent_id"));
        map.put("lifecycle", JsonUtil.getJsonInt(json, "lifecycle"));
        map.put("status", JsonUtil.getJsonInt(json, "status"));
        map.put("is_public", JsonUtil.getJsonInt(json, "is_public"));
        map.put("primary_name", JsonUtil.getJsonString(json, "primary_name"));
        map.put("long_name", JsonUtil.getJsonString(json, "long_name"));
        map.put("description", JsonUtil.getJsonString(json, "description"));
        // Include segment fields - frontend sends both camelCase and snake_case
        Integer segmentId = JsonUtil.getJsonInt(json, "segmentId");
        System.out.println("🔍 DEBUG toMap - segmentId from 'segmentId': " + segmentId);
        if (segmentId == null) {
            segmentId = JsonUtil.getJsonInt(json, "segment_id");
            System.out.println("🔍 DEBUG toMap - segmentId from 'segment_id': " + segmentId);
        }
        if (segmentId != null) {
            map.put("segmentId", segmentId);
            System.out.println("🔍 DEBUG toMap - Added segmentId to map: " + segmentId);
        } else {
            System.out.println("🔍 DEBUG toMap - segmentId is null, NOT added to map");
        }
        return map;
    }

    private void validateMandatoryFields(Map<String, Object> clientData) throws IOException {
        if (clientData.get("description") == null || ((String) clientData.get("description")).trim().isEmpty()) {
            throw new IOException("Description is required");
        }
        if (clientData.get("primary_name") == null || ((String) clientData.get("primary_name")).trim().isEmpty()) {
            throw new IOException("Primary Name is required");
        }
        if (clientData.get("status") == null) {
            throw new IOException("BUDG Status is required");
        }
        if (clientData.get("lifecycle") == null) {
            throw new IOException("Lifecycle is required");
        }
        if (clientData.get("is_public") == null) {
            throw new IOException("BUDG Viewing is required");
        }
    }

    private void sendJson(HttpServletResponse response, Object data) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        obj.add("data", JsonParser.parseString(JsonUtil.toJson(data)));
        response.getWriter().write(obj.toString());
    }

    private void sendError(HttpServletResponse response, String message, int status) throws IOException {
        JsonUtil.sendErrorResponse(response.getWriter(), message, status);
    }

    private void sendSuccess(HttpServletResponse response, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        obj.addProperty("message", message);
        response.getWriter().write(obj.toString());
    }

    private void assignCreatorRole(int clientId, int userId) throws SQLException {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int moduleId = com.example.budg_v2.util.DefaultStakeholderUtil.getModuleId(conn, "Client");
                java.util.List<Integer> rolesToAssign = com.example.budg_v2.util.DefaultStakeholderUtil
                        .getDefaultRolesForCreator(conn, moduleId, userId);

                if (rolesToAssign.isEmpty()) {
                    conn.commit();
                    return;
                }

                for (Integer roleId : rolesToAssign) {
                    try {
                        String insertOXP = """
                                INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                                VALUES (NULL, ?, ?, 2, 1, ?)
                                """;
                        int objectXPeopleId;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertOXP,
                                java.sql.Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setInt(1, userId);
                            stmt.setInt(2, roleId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0)
                                throw new java.sql.SQLException("Failed to insert into object_x_people");
                            try (java.sql.ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                                if (generatedKeys.next()) {
                                    objectXPeopleId = generatedKeys.getInt(1);
                                } else {
                                    throw new java.sql.SQLException("Failed to get generated key for object_x_people");
                                }
                            }
                        }

                        String insertClientX = """
                                INSERT INTO client_x_objectxpeople (Object_x_ipid, ClientID, Last_UpdateUser_ID)
                                VALUES (?, ?, ?)
                                """;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertClientX)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, clientId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0)
                                throw new java.sql.SQLException("Failed to insert into client_x_objectxpeople");
                        }

                        com.example.budg_v2.util.RoleNotificationHelper.createNotificationAfterCreatorRoleAssigned(
                                "Client", clientId, userId, roleId, objectXPeopleId, conn);

                    } catch (java.sql.SQLException e) {
                        System.err
                                .println("❌ Error assigning default role " + roleId + " to creator: " + e.getMessage());
                    }
                }

                conn.commit();
            } catch (java.sql.SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (java.sql.SQLException e) {
            System.err.println("❌ Error in assignCreatorRole: " + e.getMessage());
            throw e;
        }
    }

}
