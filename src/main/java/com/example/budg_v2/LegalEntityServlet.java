package com.example.budg_v2;

import com.example.budg_v2.dao.LegalDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.model.Legal;
import com.example.budg_v2.service.LegalService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RequestedSegmentFilterUtil;
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
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

@WebServlet(name = "LegalEntityServlet", urlPatterns = {"/api/LegalEntity/*"})
public class LegalEntityServlet extends HttpServlet {

    private final LegalService legalService;
    private final LegalDAO legalDAO;
    private final SegmentDAO segmentDAO;
    private final SegmentValidationService segmentValidationService;

    public LegalEntityServlet() {
        this.legalService = new LegalService();
        this.legalDAO = new LegalDAO();
        this.segmentDAO = new SegmentDAO();
        this.segmentValidationService = new SegmentValidationService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            String pathInfo = request.getPathInfo();

            int userId = UserContextUtil.getCurrentUserId(request);
            
            if ("/search".equals(pathInfo)) {
                String searchQuery = request.getParameter("q");
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    searchLegals(response, searchQuery.trim());
                } else {
                    getAllLegals(request, response);
                }
            } else if ("/hierarchy".equals(pathInfo)) {
                // Hierarchy view returns the full tree so the relationship UI can
                // structurally show every node; access-restricted private nodes
                // are then masked (xxxx + lock) by HierarchyAccessMasker below.
                List<Legal> legalEntities = legalService.getAllLegalsForHierarchy();
                legalEntities = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        legalEntities,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "LegalEntity",
                        Legal::getId);

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Legal le : legalEntities) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", le.getId());
                    o.addProperty("longName", le.getLongName());
                    o.addProperty("description", le.getDescription());
                    o.addProperty("parentId", le.getParentId());
                    arr.add(o);
                }
                com.example.budg_v2.util.HierarchyAccessMasker.mask(arr, "LegalEntity", userId);
                response.getWriter().write(arr.toString());
            } else if (pathInfo != null && pathInfo.matches("/\\d+/stakeholders")) {
                String[] parts = pathInfo.split("/");
                int id = Integer.parseInt(parts[1]);
                Integer moduleId = null;
                try {
                    String moduleParam = request.getParameter("moduleId");
                    if (moduleParam != null && !moduleParam.isBlank()) moduleId = Integer.parseInt(moduleParam);
                } catch (Exception ignored) {}

                List<java.util.Map<String,Object>> list = getLegalStakeholders(id, moduleId);
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("success", true);
                jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(list)));
                response.getWriter().write(jsonResponse.toString());
            } else if (pathInfo != null && pathInfo.length() > 1) {
                String idParam = pathInfo.substring(1);
                if (idParam.matches("\\d+")) {
                    getLegalById(request, response, Integer.parseInt(idParam));
                } else {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                }
            } else {
                getAllLegals(request, response);
            }
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error: " + e.getMessage(), 500);
        }
    }

    private List<java.util.Map<String,Object>> getLegalStakeholders(int legalId, Integer moduleId) throws SQLException {
        StringBuilder q = new StringBuilder()
                .append("SELECT \n")
                .append("    orl.PrimaryName AS Role,\n")
                .append("    CONCAT(p.First_Name, ' ', p.Last_Name) AS Name,\n")
                .append("    ou.Name AS OrgUnit,\n")
                .append("    ra.Message AS RoleAccepted,\n")
                .append("    oxp.isdelegateof AS isDelegateOf\n")
                .append("FROM object_role orl\n")
                .append("JOIN object_x_people oxp ON oxp.RoleID = orl.ID\n")
                .append("JOIN legal_x_objectxpeople lxop ON lxop.Object_X_IP = oxp.ID\n")
                .append("JOIN people p ON p.ID = oxp.iPID\n")
                .append("JOIN org_unit ou ON ou.ID = p.Org_Unit_ID\n")
                .append("LEFT JOIN roleaccepted ra ON ra.ID = oxp.AcceptedID\n")
                .append("WHERE lxop.Legal_ID = ?\n");

        List<Object> params = new java.util.ArrayList<>();
        params.add(legalId);
        if (moduleId != null) {
            q.append("  AND orl.module = ?\n");
            params.add(moduleId);
        }

        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(q.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("role", rs.getString("Role"));
                    row.put("name", rs.getString("Name"));
                    row.put("orgUnit", rs.getString("OrgUnit"));
                    row.put("roleAccepted", rs.getString("RoleAccepted"));
                    row.put("isDelegateOf", rs.getObject("isDelegateOf"));
                    list.add(row);
                }
                return list;
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        // Check create permission for new legal entity creation
        if (!PermissionCheckUtil.checkCreatePermission(request, response, "Legal Entity")) {
            return; // Response already sent
        }

        try {
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String longName = JsonUtil.getJsonString(jsonData, "longname");
            String shortName = JsonUtil.getJsonString(jsonData, "shortname");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer parentId = JsonUtil.getJsonInt(jsonData, "parent_id");
            Integer status = JsonUtil.getJsonInt(jsonData, "status");
            Integer isPublic = JsonUtil.getJsonInt(jsonData, "is_public");
            Integer lastUpdateUserId = UserContextUtil.getCurrentUserId(request);

            // Validate mandatory fields
            if (longName == null || longName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Long Name is required", 400);
                return;
            }
            if (shortName == null || shortName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Short Name is required", 400);
                return;
            }
            if (status == null) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Status is required", 400);
                return;
            }
            if (isPublic == null) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Is Public is required", 400);
                return;
            }

            // Validate segment hierarchy before creating legal entity
            Integer segmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segmentId == null) segmentId = 1;
            if (parentId != null && parentId > 0) {
                try {
                    var hierarchyResult = segmentValidationService.validateParentChildSegment(parentId, segmentId, "LegalEntity");
                    if (!hierarchyResult.isValid) {
                        JsonUtil.sendErrorResponse(response.getWriter(), hierarchyResult.message, 400);
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Error validating legal entity hierarchy: " + e.getMessage());
                    JsonUtil.sendErrorResponse(response.getWriter(), "Error validating segment hierarchy: " + e.getMessage(), 500);
                    return;
                }
            }

            Legal newLegal = legalService.createLegal(
                longName.trim(), 
                shortName.trim(), 
                description, 
                parentId, 
                status, 
                isPublic, 
                lastUpdateUserId
            );
            try {
                segmentDAO.assignObjectToSegment(segmentId, newLegal.getId(), "LegalEntity", lastUpdateUserId > 0 ? lastUpdateUserId : 1);
                //system.out.println("✅ LegalEntity " + newLegal.getId() + " assigned to segment " + segmentId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning legal entity to segment: " + e.getMessage());
            }

            // Assign creator role to the user for the new legal entity
            if (lastUpdateUserId > 0) {
                //system.out.println("✅ Calling assignCreatorRole...");
                try {
                    assignCreatorRole(newLegal.getId(), lastUpdateUserId);
                } catch (Exception e) {
                    System.err.println("❌ Error assigning creator role: " + e.getMessage());
                    e.printStackTrace();
                    // Continue - don't fail the entire save
                }
            } else {
                System.err.println("⚠️ No valid userId in session, skipping role assignment");
            }

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Legal entity created successfully");
            successResponse.add("data", JsonParser.parseString(JsonUtil.toJson(newLegal)));

            response.getWriter().write(successResponse.toString());

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error creating legal entity: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        // Check role-based edit permission first (skip for stakeholders updates)
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.matches("/\\d+/stakeholders")) {
            if (!PermissionCheckUtil.checkEditPermission(request, response, "Legal Entity")) {
                return; // Response already sent
            }
        }

        try {
            // Handle stakeholders update
            if (pathInfo != null && pathInfo.matches("/\\d+/stakeholders")) {
                // For now, just return success (stakeholders update not implemented yet)
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Stakeholders updated successfully");
                response.getWriter().write(successResponse.toString());
                return;
            }
            
            if (pathInfo == null || pathInfo.length() <= 1) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Legal entity ID is required for update", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int legalId = Integer.parseInt(idParam);

            // Check segment-based edit permission
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(currentUserId, legalId, "LegalEntity");
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonUtil.sendErrorResponse(response.getWriter(), "Access denied. You don't have permission to edit this legal entity.", 403);
                    return;
                }
            }

            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String longName = JsonUtil.getJsonString(jsonData, "longname");
            String shortName = JsonUtil.getJsonString(jsonData, "shortname");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer parentId = JsonUtil.getJsonInt(jsonData, "parent_id");
            Integer status = JsonUtil.getJsonInt(jsonData, "status");
            Integer isPublic = JsonUtil.getJsonInt(jsonData, "is_public");
            Integer lastUpdateUserId = UserContextUtil.getCurrentUserId(request);

            // Validate mandatory fields
            if (longName == null || longName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Long Name is required", 400);
                return;
            }
            if (shortName == null || shortName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Short Name is required", 400);
                return;
            }
            if (status == null) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Status is required", 400);
                return;
            }
            if (isPublic == null) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Is Public is required", 400);
                return;
            }

            // Check if data has actually changed
            Legal existingLegal = legalService.getLegalById(legalId);
            if (existingLegal != null) {
                // Segment change should be considered a valid change even if the core record is unchanged
                Integer desiredSegmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
                if (desiredSegmentId == null) desiredSegmentId = JsonUtil.getJsonInt(jsonData, "segment_id");

                boolean segmentChanged = false;
                if (desiredSegmentId != null) {
                    try {
                        int curSeg = segmentDAO.getObjectSegmentId(legalId, "LegalEntity");
                        segmentChanged = curSeg != desiredSegmentId;
                    } catch (Exception ignored) {
                        // If we can't resolve current segment, don't block the save
                        segmentChanged = true;
                    }
                }

                boolean hasChanged = !longName.trim().equals(existingLegal.getLongName()) ||
                                   !shortName.trim().equals(existingLegal.getShortName()) ||
                                   !(description != null ? description.trim() : "").equals(existingLegal.getDescription() != null ? existingLegal.getDescription() : "") ||
                                   !status.equals(existingLegal.getStatus()) ||
                                   !isPublic.equals(existingLegal.getIsPublic()) ||
                                   !Objects.equals(parentId, existingLegal.getParentId()) ||
                                   segmentChanged; // Include segment changes in the check
                
                if (!hasChanged) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "No changes detected. The data is identical to the existing record.", 400);
                    return;
                }
            }

            // Update core record. If only segment changed, this may return false; we still allow segment update.
            boolean updated = legalService.updateLegal(
                    legalId,
                    longName.trim(),
                    shortName.trim(),
                    description,
                    parentId,
                    status,
                    isPublic,
                    lastUpdateUserId
            );

            // Update segment assignment if provided (accept both segmentId and segment_id) regardless of core-update result
            Integer segmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segmentId == null) segmentId = JsonUtil.getJsonInt(jsonData, "segment_id");
            System.out.println("🔍 DEBUG doPut - LegalEntity ID=" + legalId + ", received segmentId=" + segmentId);
            if (segmentId != null) {
                try {
                    int curSegmentId = segmentDAO.getObjectSegmentId(legalId, "LegalEntity");
                    System.out.println("🔍 DEBUG doPut - Current segment in DB: " + curSegmentId);
                    if (curSegmentId != segmentId) {
                        com.example.budg_v2.service.SegmentValidationService validationService = new com.example.budg_v2.service.SegmentValidationService();
                        var validationResult = validationService.validateSegmentMove(legalId, segmentId, "LegalEntity", parentId);
                        if (!validationResult.isValid) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            JsonUtil.sendErrorResponse(response.getWriter(), validationResult.message, 400);
                            return;
                        }
                        if (curSegmentId > 0) {
                            segmentDAO.removeObjectFromSegment(curSegmentId, legalId, "LegalEntity",
                                    lastUpdateUserId > 0 ? lastUpdateUserId : 1);
                        }
                        segmentDAO.assignObjectToSegment(segmentId, legalId, "LegalEntity",
                                lastUpdateUserId > 0 ? lastUpdateUserId : 1);
                        System.out.println("✅ LegalEntity " + legalId + " segment changed from " + curSegmentId + " to " + segmentId);
                        // Verify
                        int verifySegment = segmentDAO.getObjectSegmentId(legalId, "LegalEntity");
                        System.out.println("🔍 DEBUG doPut - Segment after save: " + verifySegment);
                    } else {
                        System.out.println("⏭️ Segment unchanged (already " + segmentId + ")");
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error updating legal entity segment: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // If neither the core record updated nor a segment change was requested, treat as failure
            if (!updated && segmentId == null) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Legal entity not found or update failed", 404);
                return;
            }

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Legal entity updated successfully");
            response.getWriter().write(successResponse.toString());

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error updating legal entity: " + e.getMessage(), 500);
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
                JsonUtil.sendErrorResponse(response.getWriter(), "Legal entity ID is required for deletion", 400);
                return;
            }

            String idParam = pathInfo.substring(1);
            if (!idParam.matches("\\d+")) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
                return;
            }

            int legalId = Integer.parseInt(idParam);
            boolean deleted = legalService.deleteLegal(legalId, request);

            if (deleted) {
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Legal entity deleted successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Legal entity not found or deletion failed", 404);
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Invalid ID format", 400);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error deleting legal entity: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllLegals(HttpServletRequest request, HttpServletResponse response) throws IOException, SQLException {
        int userId = UserContextUtil.getCurrentUserId(request);
        List<Legal> legals = userId > 0 ? legalService.getAllLegals(userId) : legalService.getAllLegals();
        legals = RequestedSegmentFilterUtil.filterByRequestedSegment(
                legals,
                RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                "LegalEntity",
                Legal::getId);
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", legals.size());
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(legals)));
        response.getWriter().write(jsonResponse.toString());
    }

    private void getLegalById(HttpServletRequest request, HttpServletResponse response, int id) throws IOException, SQLException {
        Legal legal = legalService.getLegalById(id);
        if (legal != null) {
            if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(legal.getBUDGStatus())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"This object is not available.\"}");
                return;
            }
            // Check access control for all users (including guests)
            int userId = UserContextUtil.getCurrentUserId(request);

            // GUEST ACCESS CHECK: Only allow public objects in Enterprise segment
            if (userId <= 0) {
                // Guest user - check if object is public and in Enterprise segment
                try {
                    boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "Legal");
                    if (!canAccess) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("{\"error\":\"Access denied. This resource is not publicly accessible.\"}");
                        return;
                    }
                } catch (SQLException e) {
                    // On error, deny access for safety
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. Unable to verify access permissions.\"}");
                    return;
                }
            }

            // Authenticated user access check
            if (userId > 0) {
                boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "LegalEntity");
                if (!canAccess) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. You don't have permission to view this legal entity.\"}");
                    return;
                }
            }

            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", true);

            JsonObject legalJson = JsonParser.parseString(JsonUtil.toJson(legal)).getAsJsonObject();
            SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "LegalEntity");
            System.out.println("🔍 DEBUG doGet - LegalEntity ID=" + id + ", resolved segmentId=" + segmentInfo.id() + ", segmentName=" + segmentInfo.name());
            SegmentResponseUtil.applySegmentInfo(legalJson, segmentInfo, request);

            jsonResponse.add("data", legalJson);
            response.getWriter().write(jsonResponse.toString());
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Legal entity not found", 404);
        }
    }

    private void searchLegals(HttpServletResponse response, String searchQuery) throws IOException, SQLException {
        List<Legal> legals = legalService.searchLegals(searchQuery);
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("success", true);
        jsonResponse.addProperty("count", legals.size());
        jsonResponse.addProperty("searchQuery", searchQuery);
        jsonResponse.add("data", JsonParser.parseString(JsonUtil.toJson(legals)));
        response.getWriter().write(jsonResponse.toString());
    }

    private void assignCreatorRole(int legalId, int userId) throws SQLException {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int moduleId = com.example.budg_v2.util.DefaultStakeholderUtil.getModuleId(conn, "Legal Entity");
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

                        String insertLegalX = """
                                INSERT INTO legal_x_objectxpeople (Object_X_IP, Legal_ID, LastUpdate_UserID)
                                VALUES (?, ?, ?)
                                """;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertLegalX)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, legalId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) throw new java.sql.SQLException("Failed to insert into legal_x_objectxpeople");
                        }

                        com.example.budg_v2.util.RoleNotificationHelper.createNotificationAfterCreatorRoleAssigned(
                                "Legal Entity", legalId, userId, roleId, objectXPeopleId, conn);

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
        return 1; // fallback
    }

    private String getUserFullName(int userId) {
        try {
            String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
            try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("fullName");
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting user full name: " + e.getMessage());
        }
        return null;
    }
}
