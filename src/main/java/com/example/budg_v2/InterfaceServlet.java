package com.example.budg_v2;

import com.example.budg_v2.dao.InterfaceDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.model.Interface;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.ReferenceNumberGenerator;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Map;

@WebServlet(name = "InterfaceServlet", urlPatterns = {"/api/interface/*", "/api/interface/save", "/api/create/interface/save"})
public class InterfaceServlet extends HttpServlet {

    private final InterfaceDAO interfaceDAO = new InterfaceDAO();
    private final SegmentDAO segmentDAO = new SegmentDAO();
    private final Gson gson = new Gson();
    private static final int MAX_DB_ERROR_LEN = 400;

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        String idStr = null;
        if (pathInfo != null && !"/".equals(pathInfo)) {
            idStr = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            if (idStr.endsWith("/")) idStr = idStr.substring(0, idStr.length() - 1);
        } else {
            idStr = req.getParameter("id");
        }
        if (idStr == null || idStr.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing interface id\"}");
            return;
        }

        try {
            String[] parts = idStr.split("/");
            int id = Integer.parseInt(parts[0]);
        if (parts.length > 1 && "stakeholders".equalsIgnoreCase(parts[1])) {
            var list = interfaceDAO.getDirectStakeholdersForInterface(id);
            resp.getWriter().write(gson.toJson(list));
            return;
        } else if (parts.length > 1 && "roles".equalsIgnoreCase(parts[1])) {
            var list = interfaceDAO.getRolesForInterface(id);
            resp.getWriter().write(gson.toJson(list));
            return;
        } else if (parts.length > 1 && "statuses".equalsIgnoreCase(parts[1])) {
            var list = interfaceDAO.getStatusesForInterface(id);
            resp.getWriter().write(gson.toJson(list));
            return;
        } else if (parts.length > 2 && "users".equalsIgnoreCase(parts[1])) {
            int roleId = Integer.parseInt(parts[2]);
            var list = interfaceDAO.getUsersByRole(id, roleId);
            resp.getWriter().write(gson.toJson(list));
            return;
        }

            var data = interfaceDAO.getById(id);
            if (data == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Interface not found\"}");
                return;
            }
            String statusName = data.get("statusName") != null ? String.valueOf(data.get("statusName")) : null;
            if (!UserContextUtil.isCurrentUserAdmin(req) && "Deleted".equalsIgnoreCase(statusName)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().write("{\"error\":\"This object is not available.\"}");
                return;
            }

            enrichSegmentMetadata(data);
            resp.getWriter().write(gson.toJson(data));
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid interface id\"}");
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        //system.out.println("🚀 InterfaceServlet.doPost - START");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        
        // Handle /api/interface/save or /api/create/interface/save (Create/Update)
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/save")) {
            handleCreateOrUpdate(req, resp);
            return;
        }

        // Handle other POST endpoints like /api/interface/{id}/stakeholders
        String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] parts = trimmed.split("/");
        String idStr = parts[0];

        try {
            int id = Integer.parseInt(idStr);
            
            if (parts.length > 1 && "stakeholders".equalsIgnoreCase(parts[1])) {
                // Handle save stakeholders at /interface/{id}/stakeholders
                StringBuilder jsonBuilder = new StringBuilder();
                String line;
                while ((line = req.getReader().readLine()) != null) {
                    jsonBuilder.append(line);
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> payload = gson.fromJson(jsonBuilder.toString(), Map.class);

                try {
                    int currentUserId = com.example.budg_v2.util.UserContextUtil.getCurrentUserId(req);
                    java.util.Set<Integer> deleteIds = com.example.budg_v2.util.DefaultStakeholderUtil.extractObjectXPeopleIdsFromDeletes(payload);
                    try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
                        com.example.budg_v2.util.DefaultStakeholderUtil.ValidationResult vr =
                                com.example.budg_v2.util.DefaultStakeholderUtil.validateCurrentUserCanSaveStakeholders(
                                        conn, "Interface", id, currentUserId, deleteIds);
                        if (!vr.isValid()) {
                            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            resp.getWriter().write("{\"success\":false,\"error\":\"" + (vr.getWarningMessage() != null ? vr.getWarningMessage().replace("\"", "\\\"") : "") + "\"}");
                            return;
                        }
                    }
                    interfaceDAO.saveStakeholdersChanges(id, payload, currentUserId);
                    resp.getWriter().write("{\"success\":true,\"message\":\"Saved successfully\"}");
                } catch (IllegalArgumentException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"success\":false,\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
                } catch (java.sql.SQLException e) {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.getWriter().write("{\"success\":false,\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Database error") + "\"}");
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Invalid endpoint\"}");
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid interface id\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing interface id\"}");
            return;
        }

        String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] parts = trimmed.split("/");
        String idStr = parts[0];

        try {
            int id = Integer.parseInt(idStr);
            
            // Check role-based edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(req, resp, "Interface", id)) {
                return; // Response already sent
            }
            
            // Read JSON payload
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = req.getReader().readLine()) != null) {
                jsonBuilder.append(line);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = gson.fromJson(jsonBuilder.toString(), Map.class);

            // Convert payload to Interface object
            Interface interfaceObj = mapToInterface(payload);
            interfaceObj.setId(id);

            // Get current user ID
            int userId = UserContextUtil.getCurrentUserId(req);

            try {
                boolean success = interfaceDAO.updateInterface(interfaceObj, userId);
                if (success) {
                    // Interface segment is always inherited from the target system.
                    syncInterfaceSegment(id, interfaceObj.getTargetSystemId(), userId);
                    resp.getWriter().write("{\"success\":true,\"message\":\"Interface updated successfully\"}");
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write("{\"error\":\"Interface not found or no changes made\"}");
                }
            } catch (IllegalArgumentException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"success\":false,\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
            } catch (SQLException e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage().replace("\"","\\\"") + "\"}");
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid interface id\"}");
        }
    }

    /**
     * Helper method to convert Map payload to Interface object
     */
    private Interface mapToInterface(Map<String, Object> payload) {
        Interface interfaceObj = new Interface();
        
        interfaceObj.setName((String) payload.get("name"));
        interfaceObj.setRefNumber((String) payload.get("refNumber"));
        interfaceObj.setDescription((String) payload.get("description"));
        interfaceObj.setAssetId((String) payload.get("assetId"));
        interfaceObj.setSynchronisationControl((String) payload.get("synchronisationControl"));
        
        // Handle nullable integers
        interfaceObj.setClassificationId(getIntegerFromPayload(payload, "classificationId"));
        interfaceObj.setLifecycleId(getIntegerFromPayload(payload, "lifecycleId"));
        interfaceObj.setStatusId(getIntegerFromPayload(payload, "statusId"));
        interfaceObj.setSourceSystemId(getIntegerFromPayload(payload, "sourceSystemId"));
        interfaceObj.setTargetSystemId(getIntegerFromPayload(payload, "targetSystemId"));
        interfaceObj.setAutomationId(getIntegerFromPayload(payload, "automationId"));
        interfaceObj.setFrequencyId(getIntegerFromPayload(payload, "frequencyId"));
        interfaceObj.setTransferMethodId(getIntegerFromPayload(payload, "transferMethodId"));
        interfaceObj.setTransferFormatId(getIntegerFromPayload(payload, "transferFormatId"));
        interfaceObj.setIsPublic(getIntegerFromPayload(payload, "isPublic"));
        
        return interfaceObj;
    }

    /**
     * Helper method to safely extract integer values from payload
     */
    private Integer getIntegerFromPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Handle Create or Update Interface
     * Called from /api/interface/save or /api/create/interface/save
     */
    private void handleCreateOrUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            JsonObject body = JsonParser.parseReader(new InputStreamReader(req.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();

            Integer id = JsonUtil.getJsonInt(body, "id");
            
            // Check create permission for new interface creation (if id is null or 0)
            if (id == null || id == 0) {
                if (!PermissionCheckUtil.checkCreatePermission(req, resp, "Interface")) {
                    return; // Response already sent
                }
            }
            //system.out.println("📋 Interface ID from request: " + id + " (isUpdate: " + (id != null && id > 0) + ")");
            String name = JsonUtil.getJsonString(body, "name");
            String refNumber = JsonUtil.getJsonString(body, "ref_number");
            String description = JsonUtil.getJsonString(body, "description");
            String syncControl = JsonUtil.getJsonString(body, "synchronisation_control");
            String assetId = JsonUtil.getJsonString(body, "asset_id");

            Integer statusId = JsonUtil.getJsonInt(body, "status_id");
            Integer lifecycleId = JsonUtil.getJsonInt(body, "lifecycle_id");
            Integer isPublic = JsonUtil.getJsonInt(body, "is_public");
            Integer automationId = JsonUtil.getJsonInt(body, "automation_id");
            Integer frequencyId = JsonUtil.getJsonInt(body, "frequency_id");
            Integer transferMethodId = JsonUtil.getJsonInt(body, "transfer_method_id");
            Integer transferFormatId = JsonUtil.getJsonInt(body, "transfer_format_id");
            Integer classificationId = JsonUtil.getJsonInt(body, "classification_id");
            Integer sourceSystemId = JsonUtil.getJsonInt(body, "source_system_id");
            Integer targetSystemId = JsonUtil.getJsonInt(body, "target_system_id");

            if (name == null || name.isBlank()) {
                sendFieldError(resp, "name", "Name is required");
                return;
            }
            if (description == null || description.isBlank()) {
                sendFieldError(resp, "description", "Description is required");
                return;
            }
            if (statusId == null) { sendFieldError(resp, "status_id", "Status is required"); return; }
            if (lifecycleId == null) { sendFieldError(resp, "lifecycle_id", "Lifecycle is required"); return; }
            if (isPublic == null) { sendFieldError(resp, "is_public", "Viewing is required"); return; }
            if (automationId == null) { sendFieldError(resp, "automation_id", "Automation is required"); return; }
            if (sourceSystemId == null) { sendFieldError(resp, "source_system_id", "Source system is required"); return; }
            if (targetSystemId == null) { sendFieldError(resp, "target_system_id", "Target system is required"); return; }

            long nameSegmentId = resolveInterfaceNameSegment(targetSystemId);

            boolean isUpdate = id != null && id > 0;

            // Validate ref number uniqueness BEFORE auto-generating (for creation only) - use centralized RefNumberValidator
            if (!isUpdate && refNumber != null && !refNumber.isBlank()) {
                try {
                    boolean isUnique = com.example.budg_v2.util.RefNumberValidator.isRefNumberUnique("Interface", refNumber);
                    if (!isUnique) {
                        sendFieldError(resp, "ref_number", "This reference number is already in use. Please enter a unique reference number.");
                        return;
                    }
                } catch (SQLException e) {
                    // Log error but don't block operation
                    System.err.println("[InterfaceServlet] Error validating RefNumber uniqueness: " + e.getMessage());
                }
            }

            if (ReferenceNumberGenerator.isEmpty(refNumber)) {
                refNumber = generateUniqueInterfaceRef(conn, isUpdate ? id : null);
            }
            if (isUpdate) {
                //system.out.println("📝 Updating existing interface with ID: " + id);
                if (id == null) {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid interface id for update", 400);
                    return;
                }
                int interfaceId = id.intValue();
                
                // Check role-based edit permission for updates
                if (!PermissionCheckUtil.checkEditPermission(req, resp, "Interface")) {
                    return; // Response already sent
                }
                
                // Get current user ID
                int userId = UserContextUtil.getCurrentUserId(req);
                //system.out.println("📌 Updating interface, userId: " + userId);
                
                // Check unique constraints before update
                enforceUniqueConstraints(conn, name, refNumber, interfaceId, nameSegmentId);

                // Create Interface object for update
                Interface interfaceObj = new Interface();
                interfaceObj.setId(interfaceId);
                interfaceObj.setName(name);
                interfaceObj.setRefNumber(refNumber);
                interfaceObj.setDescription(description);
                interfaceObj.setTransferMethodId(transferMethodId);
                interfaceObj.setTransferFormatId(transferFormatId);
                interfaceObj.setClassificationId(classificationId);
                interfaceObj.setLifecycleId(lifecycleId);
                interfaceObj.setStatusId(statusId);
                interfaceObj.setSourceSystemId(sourceSystemId);
                interfaceObj.setTargetSystemId(targetSystemId);
                interfaceObj.setAutomationId(automationId);
                interfaceObj.setFrequencyId(frequencyId);
                interfaceObj.setIsPublic(isPublic);
                interfaceObj.setAssetId(assetId);
                interfaceObj.setSynchronisationControl(syncControl);

                try {
                    // Use InterfaceDAO.updateInterface() with audit tracking
                    boolean success = interfaceDAO.updateInterface(interfaceObj, userId);
                    
                    if (success) {
                        // Interface segment is always inherited from the target system.
                        syncInterfaceSegment(interfaceId, targetSystemId, userId);
                        //system.out.println("✅ Interface updated successfully with audit tracking");
                        JsonObject ok = new JsonObject();
                        ok.addProperty("status", "success");
                        ok.addProperty("message", "Interface updated successfully");
                        ok.addProperty("interfaceId", interfaceId);
                        resp.getWriter().write(gson.toJson(ok));
                        return;
                    } else {
                        JsonUtil.sendErrorResponse(resp.getWriter(), "Interface not found or no changes made", 404);
                        return;
                    }
                } catch (SQLException e) {
                    System.err.println("❌ Error updating interface: " + e.getMessage());
                    e.printStackTrace();
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Database error during update", 500);
                    return;
                }
            }

            enforceUniqueConstraints(conn, name, refNumber, null, nameSegmentId);

            int userId = UserContextUtil.getCurrentUserId(req);
            if (userId <= 0) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonUtil.sendErrorResponse(resp.getWriter(), "Unauthorized: missing user context", 401);
                return;
            }
            System.out.println("📌 Creating NEW interface, userId: " + userId);
            String sql = "INSERT INTO interface (Name, Ref_number, Description, Transfer_Method_ID, Transfer_Format_ID, Classification_id, Lifecycle_id, status_id, Source_systemID, Target_systemID, Automation_ID, Frequency_ID, is_public, Asset_ID, Synchronisation_Control, created_datetime, last_updatedtime, createdBy_ID, last_updateuser_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                if (ReferenceNumberGenerator.isEmpty(refNumber)) ps.setNull(2, Types.VARCHAR); else ps.setString(2, refNumber);
                ps.setString(3, description);
                if (transferMethodId == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, transferMethodId);
                if (transferFormatId == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, transferFormatId);
                if (classificationId == null) ps.setNull(6, Types.INTEGER); else ps.setInt(6, classificationId);
                ps.setInt(7, lifecycleId);
                ps.setInt(8, statusId);
                ps.setInt(9, sourceSystemId);
                ps.setInt(10, targetSystemId);
                ps.setInt(11, automationId);
                if (frequencyId == null) ps.setNull(12, Types.INTEGER); else ps.setInt(12, frequencyId);
                ps.setInt(13, isPublic);
                if (ReferenceNumberGenerator.isEmpty(assetId)) ps.setNull(14, Types.VARCHAR); else ps.setString(14, assetId);
                if (syncControl == null || syncControl.isBlank()) ps.setNull(15, Types.VARCHAR); else ps.setString(15, syncControl);
                ps.setInt(16, userId);
                ps.setInt(17, userId);

                ps.executeUpdate();
                //system.out.println("✅ Interface inserted successfully into database");
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int newId = rs.getInt(1);
                        //system.out.println("🆔 New Interface ID: " + newId);
                        // Interface segment is always inherited from the target system.
                        syncInterfaceSegment(newId, targetSystemId, userId);
                        
                        // Assign creator role after successful creation
                        if (userId > 0) {
                            //system.out.println("✅ Calling assignCreatorRole...");
                            try {
                                assignCreatorRole(newId, userId);
                            } catch (Exception e) {
                                System.err.println("❌ Error assigning creator role: " + e.getMessage());
                                e.printStackTrace();
                                // Continue - don't fail the entire save
                            }
                        } else {
                            System.err.println("⚠️ No userId available, skipping role assignment");
                        }

                        // Create interface audit records
                        try {
                            String userName = getUserFullName(userId);
                            if (userName != null) {
                                interfaceDAO.createInterfaceAuditRecords(newId, userName);
                                //system.out.println("✅ Interface audit records created for ID: " + newId);
                            }
                        } catch (Exception e) {
                            System.err.println("❌ Error creating interface audit records: " + e.getMessage());
                            e.printStackTrace();
                            // Don't fail the main operation if audit fails
                        }

                        // Create interface audit snapshot (main audit table)
                        try {
                            interfaceDAO.createInterfaceAuditRecord(newId);
                            //system.out.println("✅ InterfaceServlet: interface_audit snapshot created for ID: " + newId);
                        } catch (Exception e) {
                            System.err.println("❌ Error creating interface_audit snapshot: " + e.getMessage());
                            e.printStackTrace();
                        }

                        JsonObject ok = new JsonObject();
                        ok.addProperty("status", "success");
                        ok.addProperty("message", "Interface saved successfully");
                        ok.addProperty("interfaceId", newId);
                        resp.getWriter().write(gson.toJson(ok));
                        return;
                    }
                }
            }

            JsonUtil.sendErrorResponse(resp.getWriter(), "Failed to save interface", 500);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error", 500);
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject err = new JsonObject();
            String errorMsg = e.getMessage();
            // Check if it's a ref number error
            if (errorMsg != null && errorMsg.equals("REF_NUMBER_EXISTS")) {
                err.addProperty("error", "This reference number is already in use. Please enter a unique reference number.");
                err.addProperty("status", 400);
                err.addProperty("field", "ref_number");
            } else {
                err.addProperty("error", errorMsg);
                err.addProperty("status", 400);
                if (errorMsg != null && errorMsg.contains("Name already exists")) {
                    err.addProperty("field", "name");
                }
            }
            resp.getWriter().println(err.toString());
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid request body", 400);
        }
    }

    /**
     * Keep DB error messages helpful but safe-ish to display in the UI.
     * Trims, removes control chars, and truncates to a reasonable length.
     */
    @SuppressWarnings("unused")
    private static String safeDbMessage(SQLException e) {
        if (e == null) return "Unknown database error";
        String msg = e.getMessage();
        if (msg == null) return "Unknown database error";
        msg = msg.replaceAll("\\p{Cntrl}", " ").trim();
        if (msg.isBlank()) return "Unknown database error";
        if (msg.length() > MAX_DB_ERROR_LEN) {
            msg = msg.substring(0, MAX_DB_ERROR_LEN) + "...";
        }
        return msg;
    }

    private long resolveInterfaceNameSegment(Integer targetSystemId) throws SQLException {
        if (targetSystemId == null || targetSystemId <= 0) {
            return 1L;
        }
        int seg = segmentDAO.getObjectSegmentId(targetSystemId, "System");
        return seg > 0 ? seg : 1L;
    }

    private void enforceUniqueConstraints(Connection conn, String name, String refNumber, Integer excludeId, long nameSegmentId)
            throws SQLException {
        if (SegmentScopedPrimaryNameCheck.exists(conn, "Interface", name, nameSegmentId, excludeId)) {
            throw new IllegalArgumentException("Name already exists");
        }
        enforceRefUniqueOnly(conn, refNumber, excludeId);
    }

    private String generateUniqueInterfaceRef(Connection conn, Integer excludeId) throws SQLException {
        final int maxAttempts = 10;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidate = ReferenceNumberGenerator.generateInterfaceRefNumber(conn, null);
            try {
                enforceRefUniqueOnly(conn, candidate, excludeId);
                return candidate;
            } catch (IllegalArgumentException ex) {
                if (!"REF_NUMBER_EXISTS".equals(ex.getMessage())) {
                    throw ex;
                }
            }
        }
        throw new IllegalArgumentException("Unable to auto-generate a unique reference number. Please try again.");
    }

    private void enforceRefUniqueOnly(Connection conn, String refNumber, Integer excludeId) throws SQLException {
        if (refNumber == null || refNumber.isBlank()) {
            return;
        }
        String sql = "SELECT 1 FROM interface WHERE LOWER(Ref_number)=LOWER(?) AND (deleted_datetime IS NULL OR deleted_datetime = '')"
                + (excludeId != null ? " AND id <> ?" : "")
                + " LIMIT 1";
        try (PreparedStatement chkRef = conn.prepareStatement(sql)) {
            chkRef.setString(1, refNumber);
            if (excludeId != null) {
                chkRef.setInt(2, excludeId);
            }
            try (ResultSet rs = chkRef.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("REF_NUMBER_EXISTS");
                }
            }
        }
    }

    private void sendFieldError(HttpServletResponse resp, String field, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        err.addProperty("status", 400);
        err.addProperty("field", field);
        resp.getWriter().println(err.toString());
    }

    private void assignCreatorRole(int interfaceId, int userId) throws SQLException {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int moduleId = com.example.budg_v2.util.DefaultStakeholderUtil.getModuleId(conn, "Interface");
                java.util.List<Integer> rolesToAssign = com.example.budg_v2.util.DefaultStakeholderUtil.getDefaultRolesForCreator(conn, moduleId, userId);

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
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertOXP, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setInt(1, userId);
                            stmt.setInt(2, roleId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) throw new java.sql.SQLException("Failed to insert into object_x_people");
                            try (java.sql.ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                                if (generatedKeys.next()) {
                                    objectXPeopleId = generatedKeys.getInt(1);
                                } else {
                                    throw new java.sql.SQLException("Failed to get generated key for object_x_people");
                                }
                            }
                        }

                        String insertInterfaceX = """
                                INSERT INTO interface_x_objectxpeople (Object_x_ipid, InterfaceID, Last_UpdateUser_ID)
                                VALUES (?, ?, ?)
                                """;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertInterfaceX)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, interfaceId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) throw new java.sql.SQLException("Failed to insert into interface_x_objectxpeople");
                        }

                        com.example.budg_v2.util.RoleNotificationHelper.createNotificationAfterCreatorRoleAssigned(
                                "Interface", interfaceId, userId, roleId, objectXPeopleId, conn);

                    } catch (java.sql.SQLException e) {
                        System.err.println("❌ Error assigning default role " + roleId + " to creator: " + e.getMessage());
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


    @SuppressWarnings("unused")
    private Integer getNextObjectXPeopleId(java.sql.Connection conn) throws SQLException {
        String query = "SELECT COALESCE(MAX(ID), 0) + 1 FROM object_x_people";
        try (java.sql.PreparedStatement stmt = conn.prepareStatement(query);
             java.sql.ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 1; // Fallback
    }

    private String getUserFullName(int userId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return null;
    }

    private void enrichSegmentMetadata(Object payload) {
        if (!(payload instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) payload;
        Integer targetSystemId = coerceToInteger(map.get("target_system_id"), map.get("targetSystemId"));
        SegmentInfo segmentInfo = resolveTargetSegmentInfo(targetSystemId);
        map.put("segmentId", segmentInfo.id());
        map.put("segmentName", segmentInfo.name());
    }

    private SegmentInfo resolveTargetSegmentInfo(Integer targetSystemId) {
        if (targetSystemId == null || targetSystemId <= 0) {
            return new SegmentInfo(1, "Enterprise");
        }
        return SegmentResponseUtil.resolveSegmentInfo(segmentDAO, targetSystemId, "System");
    }

    @SuppressWarnings("unused")
    private void syncInterfaceSegment(int interfaceId, Integer targetSystemId, int userId) {
        SegmentInfo targetSegment = resolveTargetSegmentInfo(targetSystemId);
        int desiredSegmentId = targetSegment.id();
        int actorId = userId > 0 ? userId : 1;

        try {
            int currentSegmentId = segmentDAO.getObjectSegmentId(interfaceId, "Interface");
            if (currentSegmentId == desiredSegmentId && currentSegmentId > 0) {
                return;
            }
            if (currentSegmentId > 0 && currentSegmentId != desiredSegmentId) {
                segmentDAO.removeObjectFromSegment(currentSegmentId, interfaceId, "Interface", actorId);
            }
            segmentDAO.assignObjectToSegment(desiredSegmentId, interfaceId, "Interface", actorId);
            System.out.println("✅ Interface " + interfaceId + " linked to segment " + desiredSegmentId + " (derived from target system)");
        } catch (SQLException e) {
            System.err.println("❌ Unable to sync interface segment: " + e.getMessage());
        }
    }

    @SafeVarargs
    private final Integer coerceToInteger(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate == null) continue;
            if (candidate instanceof Number) {
                return ((Number) candidate).intValue();
            }
            if (candidate instanceof String) {
                try {
                    return Integer.parseInt(((String) candidate).trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}


