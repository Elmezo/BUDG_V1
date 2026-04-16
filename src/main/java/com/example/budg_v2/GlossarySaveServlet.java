package com.example.budg_v2;

import com.example.budg_v2.dao.GlossaryDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.ReferenceNumberGenerator;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;

@WebServlet(name = "GlossarySaveServlet", urlPatterns = {"/api/glossary/save", "/api/create/glossary/save"})
public class GlossarySaveServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(GlossarySaveServlet.class);
    private final Gson gson = new Gson();
    private final GlossaryDAO glossaryDAO = new GlossaryDAO();
    private final DFCRService dfcrService = new DFCRService();
	private final SegmentDAO segmentDAO = new SegmentDAO();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try {
            JsonObject body = JsonUtil.parseJsonFromRequest(req.getReader());
            String name = JsonUtil.getJsonString(body, "name");
            String description = JsonUtil.getJsonString(body, "description");
            String format = JsonUtil.getJsonString(body, "format");
            String ldm = JsonUtil.getJsonString(body, "ldm");
            String businessLogic = JsonUtil.getJsonString(body, "business_logic");
            String examples = JsonUtil.getJsonString(body, "examples");
            String refNumber = JsonUtil.getJsonString(body, "ref_number");

            Integer confidentiality = JsonUtil.getJsonInt(body, "confidentiality_rating");
            Integer integrity = JsonUtil.getJsonInt(body, "integrity_rating");
            Integer availability = JsonUtil.getJsonInt(body, "availability_rating");
            Integer status = JsonUtil.getJsonInt(body, "status");
            Integer lifecycle = JsonUtil.getJsonInt(body, "lifecycle");
            Integer isPublic = JsonUtil.getJsonInt(body, "is_public");
            Integer kde = JsonUtil.getJsonInt(body, "kde");
            Integer type = JsonUtil.getJsonInt(body, "type");
            Integer security = JsonUtil.getJsonInt(body, "security_classification");
            Integer formatType = JsonUtil.getJsonInt(body, "format_type");
            Integer parentId = JsonUtil.getJsonInt(body, "parent_id");

            Integer segmentId = JsonUtil.getJsonInt(body, "segmentId");
			if (segmentId == null) segmentId = 1; // Default to Enterprise segment
            // Apply DF_CR defaults if workflow is enabled for Glossary
            boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
            int userId = UserContextUtil.getCurrentUserId(req);
            DFCRService.LockedFieldsInfo lockedFields = dfcrService.getLockedFieldsInfo("Glossary", isAdmin, userId);
            
            if (lockedFields.isWorkflowEnabled()) {
                // Override status with default if locked
                if (lockedFields.isStatusLocked() && lockedFields.getDefaultStatusId() != null) {
                    status = lockedFields.getDefaultStatusId();
                    logger.info("Applying DF_CR default status {} for Glossary", status);
                }
                // Override lifecycle with default if locked
                if (lockedFields.isLifecycleLocked() && lockedFields.getDefaultLifecycleId() != null) {
                    lifecycle = lockedFields.getDefaultLifecycleId();
                    logger.info("Applying DF_CR default lifecycle {} for Glossary", lifecycle);
                }
            }

            JsonArray aliases = body.has("aliases") && body.get("aliases").isJsonArray() ? body.getAsJsonArray("aliases") : null;

            if (name == null || name.trim().isEmpty() || description == null || description.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Name and Description are required");
                err.addProperty("status", 400);
                err.addProperty("field", name == null || name.trim().isEmpty() ? "name" : "description");
                resp.getWriter().println(err.toString());
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false);
                
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Glossary", name.trim(), segmentId.longValue(), null)) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject err = new JsonObject();
                    err.addProperty("message", "Name already exists in this segment");
                    err.addProperty("status", 400);
                    err.addProperty("field", "name");
                    resp.getWriter().write(gson.toJson(err));
                    conn.rollback();
                    return;
                }
                
                // Handle Ref_Number: auto-generate if empty, validate if provided
                if (ReferenceNumberGenerator.isEmpty(refNumber)) {
                    // Auto-generate inside transaction to ensure uniqueness
                    refNumber = generateUniqueGlossaryRefNumber(conn);
                } else {
                    // User provided a manual reference - check if it already exists
                    try (PreparedStatement chk = conn.prepareStatement("SELECT 1 FROM glossary WHERE LOWER(Ref_Number)=LOWER(?) LIMIT 1")) {
                        chk.setString(1, refNumber.trim());
                        try (ResultSet rs = chk.executeQuery()) {
                            if (rs.next()) {
                                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                JsonObject err = new JsonObject();
                                err.addProperty("message", "Reference already exists");
                                err.addProperty("status", 400);
                                err.addProperty("field", "ref_number");
                                resp.getWriter().write(gson.toJson(err));
                                conn.rollback();
                                return;
                            }
                        }
                    }
                }
                
                int glossaryId;
                // userId is already defined above (line 71)
                if (userId <= 0) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    JsonObject err = new JsonObject();
                    err.addProperty("error", "User authentication required");
                    err.addProperty("status", 401);
                    resp.getWriter().write(gson.toJson(err));
                    return;
                }

                String insertSql = "INSERT INTO glossary (Parent_ID, Is_Public, Status, Lifecycle, Format_type, KDE, Security_Classification, Type, Confidentiality_Rating, Integrity_Rating, Availability_Rating, Name, Description, Ref_Number, Examples, Business_Logic, Format, LDM, Created_Datetime, Last_Updated_Datetime, CreatedBy_ID, Last_updated_userID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW(),?,?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    // Parent_ID
                    if (parentId == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, parentId);
                    // Is_Public
                    if (isPublic == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, isPublic);
                    // Status
                    if (status == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, status);
                    // Lifecycle
                    if (lifecycle == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, lifecycle);
                    // Format_type
                    if (formatType == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, formatType);
                    // KDE
                    if (kde == null) ps.setNull(6, Types.INTEGER); else ps.setInt(6, kde);
                    // Security_Classification
                    if (security == null) ps.setNull(7, Types.INTEGER); else ps.setInt(7, security);
                    // Type
                    if (type == null) ps.setNull(8, Types.INTEGER); else ps.setInt(8, type);
                    // Confidentiality_Rating
                    if (confidentiality == null) ps.setNull(9, Types.INTEGER); else ps.setInt(9, confidentiality);
                    // Integrity_Rating
                    if (integrity == null) ps.setNull(10, Types.INTEGER); else ps.setInt(10, integrity);
                    // Availability_Rating
                    if (availability == null) ps.setNull(11, Types.INTEGER); else ps.setInt(11, availability);
                    // Name
                    ps.setString(12, name.trim());
                    // Description
                    ps.setString(13, description.trim());
                    // Ref_Number
                    ps.setString(14, refNumber);
                    // Examples
                    ps.setString(15, examples);
                    // Business_Logic
                    ps.setString(16, businessLogic);
                    // Format
                    ps.setString(17, format);
                    // LDM
                    ps.setString(18, ldm);
                    // CreatedBy_ID
                    ps.setInt(19, userId);
                    // Last_updated_userID - set to creator so "Last Updated" shows until first edit
                    ps.setInt(20, userId);

                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            glossaryId = rs.getInt(1);
                        } else {
                            conn.rollback();
                            JsonUtil.sendErrorResponse(resp.getWriter(), "Failed to create glossary", 500);
                            return;
                        }
                    }
                }

                if (aliases != null) {
                    String aliasSql = "INSERT INTO glossary_alias_names (Glossary_id, Name, Last_updated_Datetime, Last_updated_UserID) VALUES (?,?,NOW(),?)";
                    try (PreparedStatement ps = conn.prepareStatement(aliasSql)) {
                        for (int i = 0; i < aliases.size(); i++) {
                            String alias = aliases.get(i).getAsString();
                            if (alias == null || alias.trim().isEmpty()) continue;
                            ps.setInt(1, glossaryId);
                            ps.setString(2, alias.trim());
                            ps.setInt(3, userId);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                // Validate segment hierarchy before commit
                if (parentId != null && parentId > 0) {
                    try {
                        SegmentValidationService validationService = new SegmentValidationService();
                        var hierarchyValidation = validationService.validateParentChildSegment(parentId, segmentId, "Glossary");
                        if (!hierarchyValidation.isValid) {
                            conn.rollback();
                            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            JsonObject err = new JsonObject();
                            err.addProperty("error", hierarchyValidation.message);
                            err.addProperty("status", 400);
                            resp.getWriter().write(gson.toJson(err));
                            return;
                        }
                    } catch (Exception e) {
                        conn.rollback();
                        System.err.println("Error validating glossary hierarchy: " + e.getMessage());
                        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        JsonObject err = new JsonObject();
                        err.addProperty("error", "Error validating segment hierarchy: " + e.getMessage());
                        err.addProperty("status", 500);
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                }

                conn.commit();

                // Assign glossary to segment
                try {
                    segmentDAO.assignObjectToSegment(segmentId, glossaryId, "Glossary", userId);
                    //system.out.println("✅ Glossary " + glossaryId + " assigned to segment " + segmentId);
                } catch (Exception e) {
                    System.err.println("❌ Error assigning glossary to segment: " + e.getMessage());
                    // Continue - don't fail the entire save
                }

                // Assign creator role to the user for the new glossary
                if (userId > 0) {
                    //system.out.println("✅ Calling assignCreatorRole...");
                    try {
                        assignCreatorRole(glossaryId, userId);
                    } catch (Exception e) {
                        System.err.println("❌ Error assigning creator role: " + e.getMessage());
                        e.printStackTrace();
                        // Continue - don't fail the entire save
                    }
                } else {
                    System.err.println("⚠️ No valid userId in session, skipping role assignment");
                }

                // Create glossary audit records
                try {
                    String userName = "System"; // Default fallback
                    if (userId > 0) {
                        String fullName = getUserFullName(userId);
                        if (fullName != null && !fullName.trim().isEmpty() && !fullName.equals("Unknown User")) {
                            userName = fullName;
                        } else {
                            // إذا لم نجد الاسم، استخدم User ID كبديل أفضل من "System"
                            userName = "User ID: " + userId;
                        }
                    }
                    glossaryDAO.createGlossaryAuditRecords(glossaryId, userName);
                    //system.out.println("✅ Glossary audit records created for ID: " + glossaryId + " with author: " + userName);
                } catch (Exception e) {
                    System.err.println("❌ Error creating glossary audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the main operation if audit fails
                }

                // Create glossary audit snapshot (main audit table)
                try {
                    glossaryDAO.createGlossaryAuditRecord(glossaryId);
                    //system.out.println("✅ GlossarySaveServlet: glossary_audit snapshot created for ID: " + glossaryId);
                } catch (Exception e) {
                    System.err.println("❌ Error creating glossary_audit snapshot: " + e.getMessage());
                    e.printStackTrace();
                }

                // Auto-create change request if DF_CR workflow is enabled
                Integer changeRequestId = null;
                try {
                    // Get the type ID from the request body
                    Integer typeId = JsonUtil.getJsonInt(body, "type");
                    changeRequestId = dfcrService.applyDefaultsOnCreate("Glossary", glossaryId, typeId, userId, isAdmin);
                    if (changeRequestId != null) {
                        logger.info("Auto-created change request {} for new Glossary {} (typeId: {})", changeRequestId, glossaryId, typeId);
                    }
                } catch (Exception e) {
                    logger.error("Error auto-creating change request for Glossary {}: {}", glossaryId, e.getMessage());
                    // Don't fail the main operation if CR creation fails
                }

                JsonObject ok = new JsonObject();
                ok.addProperty("status", "success");
                ok.addProperty("message", "Glossary saved successfully");
                ok.addProperty("glossaryId", glossaryId);
                if (changeRequestId != null) {
                    ok.addProperty("changeRequestId", changeRequestId);
                    ok.addProperty("workflowEnabled", true);
                }
                resp.getWriter().write(gson.toJson(ok));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error: " + e.getMessage(), 500);
        }
    }

    private void assignCreatorRole(int glossaryId, int userId) throws SQLException {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int moduleId = com.example.budg_v2.util.DefaultStakeholderUtil.getModuleId(conn, "Glossary");
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

                        String insertGlossaryX = """
                                INSERT INTO glossary_x_objectxpeople (Object_x_ipid, GlossaryID, Last_UpdateUser_ID)
                                VALUES (?, ?, ?)
                                """;
                        try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertGlossaryX)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, glossaryId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) throw new java.sql.SQLException("Failed to insert into glossary_x_objectxpeople");
                        }

                        com.example.budg_v2.util.RoleNotificationHelper.createNotificationAfterCreatorRoleAssigned(
                                "Glossary", glossaryId, userId, roleId, objectXPeopleId, conn);

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

    private String getUserFullName(int userId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return null;
    }

    /**
     * Generates a unique glossary reference number within the transaction context.
     * يولد رقم مرجعي فريد ويتأكد أنه غير موجود في قاعدة البيانات
     * Format: GL + 3-digit sequence number (e.g., GL001, GL002, GL003)
     */
    private String generateUniqueGlossaryRefNumber(Connection conn) throws SQLException {
        String prefix = "GL";
        int maxAttempts = 1000;
        
        // ابحث عن أكبر رقم تسلسلي موجود يبدأ بـ GL متبوعاً بأرقام فقط
        String sql = "SELECT Ref_Number FROM glossary WHERE Ref_Number REGEXP '^GL[0-9]+$' ORDER BY CAST(SUBSTRING(Ref_Number, 3) AS UNSIGNED) DESC LIMIT 1";
        
        int nextSequence = 1;
        
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            if (rs.next()) {
                String lastRef = rs.getString("Ref_Number");
                if (lastRef != null && lastRef.length() > 2) {
                    try {
                        String numericPart = lastRef.substring(2);
                        nextSequence = Integer.parseInt(numericPart) + 1;
                    } catch (NumberFormatException e) {
                        nextSequence = 1;
                    }
                }
            }
        }
        
        // جرب إيجاد رقم مرجعي متاح (تحقق من عدم وجوده في قاعدة البيانات)
        String checkSql = "SELECT 1 FROM glossary WHERE LOWER(Ref_Number) = LOWER(?) LIMIT 1";
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidate = prefix + String.format("%03d", nextSequence);
            
            // افحص هل الرقم المرشح موجود بالفعل
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setString(1, candidate);
                try (ResultSet checkRs = checkPs.executeQuery()) {
                    if (!checkRs.next()) {
                        // وجدنا رقم فريد غير موجود في قاعدة البيانات
                        //system.out.println("✅ Generated unique reference: " + candidate);
                        return candidate;
                    } else {
                        // الرقم موجود بالفعل، جرب الرقم التالي
                        //system.out.println("⚠️ Reference " + candidate + " already exists, trying next...");
                    }
                }
            }
            
            nextSequence++;
        }
        
        // إذا فشلت جميع المحاولات
        throw new SQLException("Unable to generate unique reference number after " + maxAttempts + " attempts");
    }
}


