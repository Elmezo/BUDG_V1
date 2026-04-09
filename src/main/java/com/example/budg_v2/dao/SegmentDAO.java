package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentValidationService;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SegmentDAO {

    // Admin Panel: Only show user-created segments (exclude Enterprise ID=1)
    // Enterprise is only shown in the Segments Cube Panel, not in admin management
    @SuppressWarnings("unused")
    private static final String SELECT_ALL = "SELECT s.*, " +
            "CONCAT(cb.First_Name, ' ', cb.Last_Name) AS created_by_name, " +
            "CONCAT(ub.First_Name, ' ', ub.Last_Name) AS updated_by_name, " +
            "CASE WHEN s.ID = 1 THEN 1 ELSE 0 END AS is_enterprise " +
            "FROM segment s " +
            "LEFT JOIN people cb ON cb.ID = s.CreatedBy " +
            "LEFT JOIN people ub ON ub.ID = s.Last_UpdatedBy " +
            "WHERE s.Deleted_At IS NULL AND s.ID != 1 " +
            "ORDER BY s.Name";

    // Get segment by ID - exclude Enterprise (ID=1) from direct access
    private static final String SELECT_BY_ID = "SELECT s.*, " +
            "CONCAT(cb.First_Name, ' ', cb.Last_Name) AS created_by_name, " +
            "CONCAT(ub.First_Name, ' ', ub.Last_Name) AS updated_by_name, " +
            "CASE WHEN s.ID = 1 THEN 1 ELSE 0 END AS is_enterprise " +
            "FROM segment s " +
            "LEFT JOIN people cb ON cb.ID = s.CreatedBy " +
            "LEFT JOIN people ub ON ub.ID = s.Last_UpdatedBy " +
            "WHERE s.ID = ? AND s.Deleted_At IS NULL AND s.ID != 1";

    private static final String INSERT = "INSERT INTO segment (Name, Description, CreatedBy, Created_At, Last_UpdatedAt, Last_UpdatedBy) "
            +
            "VALUES (?, ?, ?, NOW(), NOW(), ?)";

    private static final String UPDATE = "UPDATE segment SET Name = ?, Description = ?, Last_UpdatedAt = NOW(), Last_UpdatedBy = ? "
            +
            "WHERE ID = ? AND Deleted_At IS NULL";

    private static final String SOFT_DELETE = "UPDATE segment SET Deleted_At = NOW(), Last_UpdatedBy = ? WHERE ID = ?";

    public List<Map<String, Object>> getAllSegments(int userId, boolean isSuperAdmin) throws SQLException {
        List<Map<String, Object>> segments = new ArrayList<>();

        if (isSuperAdmin) {
            // Super Admin sees all segments (excluding Enterprise) for admin management
            String sql = """
                        SELECT s.*,
                               CONCAT(cb.First_Name, ' ', cb.Last_Name) AS created_by_name,
                               CONCAT(ub.First_Name, ' ', ub.Last_Name) AS updated_by_name,
                               CASE WHEN s.ID = 1 THEN 1 ELSE 0 END AS is_enterprise
                        FROM segment s
                        LEFT JOIN people cb ON cb.ID = s.CreatedBy
                        LEFT JOIN people ub ON ub.ID = s.Last_UpdatedBy
                        WHERE s.Deleted_At IS NULL AND s.ID != 1
                        ORDER BY s.Name
                    """;

            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    segments.add(mapResultSetToMap(rs));
                }
            }
        } else {
            // Non-super-admins: show segments they can access (same rule as "My Segments"
            // cube)
            String sql = """
                        SELECT DISTINCT s.*,
                               CONCAT(cb.First_Name, ' ', cb.Last_Name) AS created_by_name,
                               CONCAT(ub.First_Name, ' ', ub.Last_Name) AS updated_by_name,
                               CASE WHEN s.ID = 1 THEN 1 ELSE 0 END AS is_enterprise
                        FROM segment s
                        JOIN v_user_accessible_segments vas ON vas.segment_id = s.ID AND vas.user_id = ?
                        LEFT JOIN people cb ON cb.ID = s.CreatedBy
                        LEFT JOIN people ub ON ub.ID = s.Last_UpdatedBy
                        WHERE s.Deleted_At IS NULL AND s.ID != 1
                        ORDER BY s.Name
                    """;

            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        segments.add(mapResultSetToMap(rs));
                    }
                }
            }
        }
        return segments;
    }

    public Map<String, Object> getSegmentById(int id) throws SQLException {
        // Special handling for Enterprise segment (ID=1) - it's excluded from
        // SELECT_BY_ID
        if (id == 1) {
            Map<String, Object> enterprise = new HashMap<>();
            enterprise.put("id", 1);
            enterprise.put("name", "Enterprise");
            enterprise.put("description", "Default enterprise-wide segment accessible to all users");
            enterprise.put("isEnterprise", true);
            return enterprise;
        }

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToMap(rs);
                }
            }
        }
        return null;
    }

    public int createSegment(String name, String description, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, name);
            pstmt.setString(2, description);
            pstmt.setInt(3, userId);
            pstmt.setInt(4, userId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating segment failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating segment failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateSegment(int id, String name, String description, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, name);
            pstmt.setString(2, description);
            pstmt.setInt(3, userId);
            pstmt.setInt(4, id);

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteSegment(int id, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, userId);
            pstmt.setInt(2, id);

            return pstmt.executeUpdate() > 0;
        }
    }

    private Map<String, Object> mapResultSetToMap(ResultSet rs) throws SQLException {
        Map<String, Object> segment = new HashMap<>();
        int segmentId = rs.getInt("ID");
        segment.put("id", segmentId);
        segment.put("name", rs.getString("Name"));
        segment.put("description", rs.getString("Description"));
        segment.put("createdBy", rs.getObject("CreatedBy"));
        segment.put("createdAt", rs.getTimestamp("Created_At"));
        segment.put("lastUpdatedAt", rs.getTimestamp("Last_UpdatedAt"));
        segment.put("lastUpdatedBy", rs.getObject("Last_UpdatedBy"));
        segment.put("status", rs.getObject("Status"));
        segment.put("createdByName", rs.getString("created_by_name"));
        segment.put("updatedByName", rs.getString("updated_by_name"));
        // Mark Enterprise segment - it cannot be edited or deleted
        segment.put("isEnterprise", segmentId == 1);
        try {
            segment.put("isEnterprise", rs.getInt("is_enterprise") == 1);
        } catch (SQLException e) {
            // Column may not exist in all queries
            segment.put("isEnterprise", segmentId == 1);
        }
        return segment;
    }

    // Get or create object_reference for a people ID
    public int getOrCreateObjectReferenceForPeople(int peopleId) throws SQLException {
        // First, try to find existing object_reference for this people
        String findSql = "SELECT ID FROM object_reference WHERE Object_ID = ? AND Object_Type_ID = (SELECT ID FROM segment_object_type WHERE Type = 'People' LIMIT 1)";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(findSql)) {
            pstmt.setInt(1, peopleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }

        // If not found, create new object_reference
        // First, get or create the object type for People
        int objectTypeId = getOrCreateObjectType("People");

        String insertSql = "INSERT INTO object_reference (Object_ID, Object_Type_ID) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, peopleId);
            pstmt.setInt(2, objectTypeId);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create object_reference");
    }

    // Get or create object_reference for an org unit ID
    public int getOrCreateObjectReferenceForOrgUnit(int orgUnitId) throws SQLException {
        // First, try to find existing object_reference for this org unit
        // Note: Database uses 'OrgUnit' (no space) in segment_object_type
        String findSql = "SELECT ID FROM object_reference WHERE Object_ID = ? AND Object_Type_ID = (SELECT ID FROM segment_object_type WHERE Type = 'OrgUnit' LIMIT 1)";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(findSql)) {
            pstmt.setInt(1, orgUnitId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }

        // If not found, create new object_reference
        // Note: Database uses 'OrgUnit' (no space) in segment_object_type
        int objectTypeId = getOrCreateObjectType("OrgUnit");

        String insertSql = "INSERT INTO object_reference (Object_ID, Object_Type_ID) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, orgUnitId);
            pstmt.setInt(2, objectTypeId);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create object_reference");
    }

    // Get or create segment_object_type
    private int getOrCreateObjectType(String typeName) throws SQLException {
        String findSql = "SELECT ID FROM segment_object_type WHERE Type = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(findSql)) {
            pstmt.setString(1, typeName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }

        // Create new type
        String insertSql = "INSERT INTO segment_object_type (Type) VALUES (?)";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, typeName);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create segment_object_type");
    }

    // Get or create association_origin
    private int getOrCreateAssociationOrigin(String originName) throws SQLException {
        String findSql = "SELECT ID FROM association_origin WHERE Name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(findSql)) {
            pstmt.setString(1, originName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }

        // Create new origin
        String insertSql = "INSERT INTO association_origin (Name) VALUES (?)";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, originName);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create association_origin");
    }

    // Assign admin user to segment
    public void assignAdminUser(int segmentId, int peopleId, String assignmentType, int userId) throws SQLException {
        int objectRefId = getOrCreateObjectReferenceForPeople(peopleId);
        // Normalize assignment type
        String normalizedType = (assignmentType != null && assignmentType.equalsIgnoreCase("sso")) ? "SSO" : "Manual";
        int originId = getOrCreateAssociationOrigin(normalizedType);

        try (Connection conn = DatabaseConnection.getConnection()) {
            // If the exact active assignment already exists, do nothing (idempotent)
            String existsSql = "SELECT COUNT(*) FROM segment_x_identity " +
                    "WHERE Segment_ID = ? AND Object_Ref_ID = ? AND Role = 'admin' AND Origin_ID = ? AND Deleted_At IS NULL";
            try (PreparedStatement checkStmt = conn.prepareStatement(existsSql)) {
                checkStmt.setInt(1, segmentId);
                checkStmt.setInt(2, objectRefId);
                checkStmt.setInt(3, originId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        // Already assigned, but still invalidate cache to ensure consistency
                        com.example.budg_v2.service.SegmentAccessService.invalidateSegmentsCache(peopleId);
                        return;
                    }
                }
            }

            // Ensure only ONE row exists for (segment, user, role).
            // This avoids unique-constraint issues when older rows exist (even
            // soft-deleted).
            String deleteAllSql = "DELETE FROM segment_x_identity WHERE Segment_ID = ? AND Object_Ref_ID = ? AND Role = 'admin'";
            try (PreparedStatement del = conn.prepareStatement(deleteAllSql)) {
                del.setInt(1, segmentId);
                del.setInt(2, objectRefId);
                del.executeUpdate();
            }

            // Insert the new assignment
            String insertSql = "INSERT INTO segment_x_identity (Segment_ID, Object_Ref_ID, Role, CreatedBy, Created_At, Last_Updated_By, Last_Updated_At, Origin_ID) "
                    +
                    "VALUES (?, ?, 'admin', ?, NOW(), ?, NOW(), ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setInt(1, segmentId);
                pstmt.setInt(2, objectRefId);
                pstmt.setInt(3, userId);
                pstmt.setInt(4, userId);
                pstmt.setInt(5, originId);
                pstmt.executeUpdate();
            }
        }

        // Invalidate cache for this user so segment access is immediately updated
        com.example.budg_v2.service.SegmentAccessService.invalidateSegmentsCache(peopleId);
    }

    // Get segment admin users
    public List<Map<String, Object>> getSegmentAdminUsers(int segmentId) throws SQLException {
        List<Map<String, Object>> adminUsers = new ArrayList<>();
        String sql = """
                    SELECT DISTINCT
                        p.ID AS user_id,
                        p.ID AS id,
                        CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                        ou.Name AS org_unit,
                        r.primaryname AS profile,
                        p.Function_Name AS function,
                        p.Email AS email,
                        sxi.Role,
                        ao.Name AS assignment_type
                    FROM segment_x_identity sxi
                    JOIN object_reference or_ref ON sxi.Object_Ref_ID = or_ref.ID
                    JOIN people p ON or_ref.Object_ID = p.ID
                    LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
                    LEFT JOIN role r ON p.System_Role = r.id
                    LEFT JOIN association_origin ao ON sxi.Origin_ID = ao.ID
                    WHERE sxi.Segment_ID = ?
                    AND sxi.Role = 'admin'
                    AND sxi.Deleted_At IS NULL
                    AND or_ref.Object_Type_ID = (SELECT ID FROM segment_object_type WHERE Type = 'People' LIMIT 1)
                    ORDER BY p.Last_Name, p.First_Name
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("user_id", rs.getInt("user_id"));
                    user.put("id", rs.getInt("id"));
                    user.put("name", rs.getString("name"));
                    user.put("org_unit", rs.getString("org_unit"));
                    user.put("profile", rs.getString("profile"));
                    user.put("function", rs.getString("function"));
                    user.put("email", rs.getString("email"));
                    // Map assignment_type - convert null to "Manual"
                    String assignmentType = rs.getString("assignment_type");
                    user.put("assignment_type", assignmentType != null ? assignmentType : "Manual");
                    adminUsers.add(user);
                }
            }
        }
        return adminUsers;
    }

    /**
     * Check if a user is an admin of a specific segment
     */
    public boolean isSegmentAdmin(int segmentId, int peopleId) throws SQLException {
        String sql = """
                    SELECT COUNT(*)
                    FROM segment_x_identity sxi
                    JOIN object_reference or_ref ON sxi.Object_Ref_ID = or_ref.ID
                    WHERE sxi.Segment_ID = ?
                      AND or_ref.Object_ID = ?
                      AND sxi.Role = 'admin'
                      AND sxi.Deleted_At IS NULL
                      AND or_ref.Object_Type_ID = (SELECT ID FROM segment_object_type WHERE Type = 'People' LIMIT 1)
                """;
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            pstmt.setInt(2, peopleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    // Assign org unit to segment
    public void assignOrgUnit(int segmentId, int orgUnitId, int userId) throws SQLException {
        int objectRefId = getOrCreateObjectReferenceForOrgUnit(orgUnitId);

        // Check if assignment already exists
        String checkSql = "SELECT COUNT(*) FROM segment_x_resource WHERE Segment_ID = ? AND Object_Reference_ID = ? AND Deleted_At IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, segmentId);
            checkStmt.setInt(2, objectRefId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    System.out.println(
                            "✅ OrgUnit " + orgUnitId + " already assigned to segment " + segmentId + ", skipping");
                    // Still invalidate cache for all users in this org unit to ensure consistency
                    invalidateCacheForOrgUnitUsers(orgUnitId);
                    return; // Already assigned to this segment
                }
            }
        }

        // Hard delete any prior assignments (org unit can only be in one segment).
        // IMPORTANT: delete regardless of Deleted_At to avoid unique-constraint issues
        // from older soft-deleted rows.
        String deleteOtherSegmentsSql = "DELETE FROM segment_x_resource WHERE Object_Reference_ID = ? AND Segment_ID != ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(deleteOtherSegmentsSql)) {
            pstmt.setInt(1, objectRefId);
            pstmt.setInt(2, segmentId);
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                System.out.println("✅ Removed OrgUnit " + orgUnitId + " from " + deleted + " other segment(s)");
            }
        }

        // Also delete any existing row for this same segment/org unit (regardless of
        // Deleted_At) for idempotency.
        String deleteSameSegmentSql = "DELETE FROM segment_x_resource WHERE Segment_ID = ? AND Object_Reference_ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(deleteSameSegmentSql)) {
            pstmt.setInt(1, segmentId);
            pstmt.setInt(2, objectRefId);
            pstmt.executeUpdate();
        }
        // Insert new assignment
        String insertSql = "INSERT INTO segment_x_resource (Segment_ID, Object_Reference_ID, Created_By, Created_At, Last_UpdatedBy, Last_Updated_At) "
                +
                "VALUES (?, ?, ?, NOW(), ?, NOW())";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setInt(1, segmentId);
            pstmt.setInt(2, objectRefId);
            pstmt.setInt(3, userId);
            pstmt.setInt(4, userId);
            pstmt.executeUpdate();
            System.out.println("✅ OrgUnit " + orgUnitId + " assigned to segment " + segmentId);
        }

        // Invalidate cache for all users in this org unit so they immediately get
        // access to the segment
        invalidateCacheForOrgUnitUsers(orgUnitId);
    }

    /**
     * Invalidate segment cache for all users in an org unit
     * This ensures that when an org unit is assigned to a segment, all users in
     * that org unit
     * immediately get access to the segment (via v_user_accessible_segments view)
     */
    private void invalidateCacheForOrgUnitUsers(int orgUnitId) {
        try {
            String sql = "SELECT ID FROM people WHERE Org_Unit_ID = ? AND Deleted_date IS NULL";
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, orgUnitId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        int userId = rs.getInt("ID");
                        if (userId > 0) {
                            com.example.budg_v2.service.SegmentAccessService.invalidateSegmentsCache(userId);
                            count++;
                        }
                    }
                    System.out
                            .println("✅ Invalidated segment cache for " + count + " user(s) in org unit " + orgUnitId);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error invalidating cache for org unit users: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Get assigned org units
    public List<Map<String, Object>> getAssignedOrgUnits(int segmentId) throws SQLException {
        List<Map<String, Object>> orgUnits = new ArrayList<>();
        // Note: Database uses 'OrgUnit' (no space) in segment_object_type
        String sql = """
                    SELECT DISTINCT
                        ou.ID AS id,
                        ou.Name AS name,
                        ou.Description AS description,
                        parent_ou.Name AS parent
                    FROM segment_x_resource sxr
                    JOIN object_reference or_ref ON sxr.Object_Reference_ID = or_ref.ID
                    JOIN org_unit ou ON or_ref.Object_ID = ou.ID
                    LEFT JOIN org_unit parent_ou ON ou.Parent_ID = parent_ou.ID
                    WHERE sxr.Segment_ID = ?
                    AND sxr.Deleted_At IS NULL
                    AND or_ref.Object_Type_ID = (SELECT ID FROM segment_object_type WHERE Type = 'OrgUnit' LIMIT 1)
                    ORDER BY ou.Name
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> orgUnit = new HashMap<>();
                    orgUnit.put("id", rs.getInt("id"));
                    orgUnit.put("name", rs.getString("name"));
                    orgUnit.put("description", rs.getString("description"));
                    orgUnit.put("parent", rs.getString("parent"));
                    orgUnits.add(orgUnit);
                }
            }
        }
        return orgUnits;
    }

    // Assign user to segment (non-admin)
    public void assignUser(int segmentId, int peopleId, int userId) throws SQLException {
        int objectRefId = getOrCreateObjectReferenceForPeople(peopleId);
        int originId = getOrCreateAssociationOrigin("Manual");

        try (Connection conn = DatabaseConnection.getConnection()) {
            // If already assigned, do nothing (idempotent)
            String existsSql = "SELECT COUNT(*) FROM segment_x_identity " +
                    "WHERE Segment_ID = ? AND Object_Ref_ID = ? AND Role = 'user' AND Deleted_At IS NULL";
            try (PreparedStatement check = conn.prepareStatement(existsSql)) {
                check.setInt(1, segmentId);
                check.setInt(2, objectRefId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        // Already assigned, but still invalidate cache to ensure consistency
                        com.example.budg_v2.service.SegmentAccessService.invalidateSegmentsCache(peopleId);
                        return;
                    }
                }
            }

            // Ensure only ONE row exists for (segment, user, role) to avoid
            // unique-constraint issues.
            String deleteAllSql = "DELETE FROM segment_x_identity WHERE Segment_ID = ? AND Object_Ref_ID = ? AND Role = 'user'";
            try (PreparedStatement del = conn.prepareStatement(deleteAllSql)) {
                del.setInt(1, segmentId);
                del.setInt(2, objectRefId);
                del.executeUpdate();
            }

            // Insert new assignment
            String insertSql = "INSERT INTO segment_x_identity (Segment_ID, Object_Ref_ID, Role, CreatedBy, Created_At, Last_Updated_By, Last_Updated_At, Origin_ID) "
                    +
                    "VALUES (?, ?, 'user', ?, NOW(), ?, NOW(), ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setInt(1, segmentId);
                pstmt.setInt(2, objectRefId);
                pstmt.setInt(3, userId);
                pstmt.setInt(4, userId);
                pstmt.setInt(5, originId);
                pstmt.executeUpdate();
            }
        }

        // Invalidate cache for this user so segment access is immediately updated
        com.example.budg_v2.service.SegmentAccessService.invalidateSegmentsCache(peopleId);
    }

    // Get assigned users (non-admin)
    public List<Map<String, Object>> getAssignedUsers(int segmentId) throws SQLException {
        List<Map<String, Object>> users = new ArrayList<>();
        String sql = """
                    SELECT DISTINCT
                        p.ID AS id,
                        p.ID AS user_id,
                        CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                        p.Description AS description,
                        p.Email AS email,
                        ou.Name AS parent
                    FROM segment_x_identity sxi
                    JOIN object_reference or_ref ON sxi.Object_Ref_ID = or_ref.ID
                    JOIN people p ON or_ref.Object_ID = p.ID
                    LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
                    WHERE sxi.Segment_ID = ?
                    AND sxi.Role = 'user'
                    AND sxi.Deleted_At IS NULL
                    AND or_ref.Object_Type_ID = (SELECT ID FROM segment_object_type WHERE Type = 'People' LIMIT 1)
                    ORDER BY p.Last_Name, p.First_Name
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("id", rs.getInt("id"));
                    user.put("user_id", rs.getInt("user_id"));
                    user.put("name", rs.getString("name"));
                    user.put("description", rs.getString("description"));
                    user.put("email", rs.getString("email"));
                    user.put("parent", rs.getString("parent"));
                    users.add(user);
                }
            }
        }
        return users;
    }

    // Remove admin user from segment
    public void removeAdminUser(int segmentId, int peopleId, int userId) throws SQLException {
        int objectRefId = getOrCreateObjectReferenceForPeople(peopleId);
        String sql = "DELETE FROM segment_x_identity " +
                "WHERE Segment_ID = ? AND Object_Ref_ID = ? AND Role = 'admin' AND Deleted_At IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            pstmt.setInt(2, objectRefId);
            pstmt.executeUpdate();
        }

        // Invalidate cache for this user so segment access is immediately updated
        com.example.budg_v2.service.SegmentAccessService.invalidateSegmentsCache(peopleId);
    }

    public void removeOrgUnit(int segmentId, int orgUnitId, int userId) throws SQLException {
        int objectRefId = getOrCreateObjectReferenceForOrgUnit(orgUnitId);
        String sql = "DELETE FROM segment_x_resource " +
                "WHERE Segment_ID = ? AND Object_Reference_ID = ? AND Deleted_At IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            pstmt.setInt(2, objectRefId);
            pstmt.executeUpdate();
        }

        // Invalidate cache for all users in this org unit so they immediately lose
        // access to the segment
        invalidateCacheForOrgUnitUsers(orgUnitId);
    }

    public void removeUser(int segmentId, int peopleId, int userId) throws SQLException {
        int objectRefId = getOrCreateObjectReferenceForPeople(peopleId);
        String sql = "DELETE FROM segment_x_identity " +
                "WHERE Segment_ID = ? AND Object_Ref_ID = ? AND Role = 'user' AND Deleted_At IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            pstmt.setInt(2, objectRefId);
            pstmt.executeUpdate();
        }

        // Invalidate cache for this user so segment access is immediately updated
        com.example.budg_v2.service.SegmentAccessService.invalidateSegmentsCache(peopleId);
    }

    // ==================== OBJECT-TO-SEGMENT ASSIGNMENT ====================

    /**
     * Get or create object_reference for any object type
     * 
     * @param objectId   The ID of the object
     * @param objectType The type name (e.g., 'Dataset', 'Glossary', 'System', etc.)
     * @return The object_reference ID
     */
    public int getOrCreateObjectReference(int objectId, String objectType) throws SQLException {
        System.out
                .println("🔍 [getOrCreateObjectReference] START - objectId=" + objectId + ", objectType=" + objectType);

        // First, try to find existing object_reference
        String findSql = "SELECT ID FROM object_reference WHERE Object_ID = ? AND Object_Type_ID = (SELECT ID FROM segment_object_type WHERE Type = ? LIMIT 1)";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(findSql)) {
            pstmt.setInt(1, objectId);
            pstmt.setString(2, objectType);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("ID");
                    System.out.println("🔍 [getOrCreateObjectReference] Found existing object_reference ID=" + id);
                    return id;
                }
            }
        }

        System.out.println("🔍 [getOrCreateObjectReference] No existing reference found, creating new one...");

        // If not found, create new object_reference
        int objectTypeId = getOrCreateObjectType(objectType);
        System.out.println(
                "🔍 [getOrCreateObjectReference] Got objectTypeId=" + objectTypeId + " for type=" + objectType);

        String insertSql = "INSERT INTO object_reference (Object_ID, Object_Type_ID) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, objectId);
            pstmt.setInt(2, objectTypeId);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int newId = rs.getInt(1);
                    System.out.println("🔍 [getOrCreateObjectReference] Created new object_reference ID=" + newId);
                    return newId;
                }
            }
        }
        throw new SQLException("Failed to create object_reference for " + objectType);
    }

    /**
     * Assign a BUDG object to a segment
     * Per BUDG rules: An object can only belong to one segment at a time.
     * This method removes any existing segment assignment before creating a new
     * one.
     * 
     * @param segmentId  The segment ID
     * @param objectId   The object ID (e.g., Dataset ID, Glossary ID)
     * @param objectType The object type (e.g., 'Dataset', 'Glossary', 'System')
     * @param userId     The user performing the action
     */
    public void assignObjectToSegment(int segmentId, int objectId, String objectType, int userId) throws SQLException {
        System.out.println("🔍 [assignObjectToSegment] START - segmentId=" + segmentId + ", objectId=" + objectId
                + ", objectType=" + objectType + ", userId=" + userId);
        int objectRefId = getOrCreateObjectReference(objectId, objectType);
        System.out.println("🔍 [assignObjectToSegment] Got objectRefId=" + objectRefId);

        // IMPORTANT: make delete+insert atomic. Otherwise, if insert fails after
        // deleting the active row,
        // the object ends up with NO active assignment and the UI falls back to
        // Enterprise.
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // Enforce shared segment-move validation for true segment changes.
                // Parent-aware flows should validate before calling this DAO; the DAO still
                // protects stakeholder, child, and linked-relationship constraints.
                int currentSegmentId = getObjectSegmentId(objectId, objectType, conn);
                if (currentSegmentId != -1 && currentSegmentId != segmentId) {
                    SegmentValidationService.ValidationResult moveValidation = new SegmentValidationService()
                            .validateSegmentMove(objectId, segmentId, objectType, null);
                    if (!moveValidation.isValid) {
                        throw new SQLException(moveValidation.message);
                    }
                }

                // Check if already assigned to this exact segment
                String checkSql = "SELECT COUNT(*) FROM segment_x_resource WHERE Segment_ID = ? AND Object_Reference_ID = ? AND Deleted_At IS NULL";
                try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
                    pstmt.setInt(1, segmentId);
                    pstmt.setInt(2, objectRefId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            System.out.println("✅ " + objectType + " #" + objectId + " already assigned to segment "
                                    + segmentId + ", skipping");
                            conn.rollback();
                            conn.setAutoCommit(prevAutoCommit);
                            return; // Already assigned to this segment
                        }
                    }
                }

                // Soft delete any existing active assignments to OTHER segments (object can
                // only be in one segment)
                String deleteSql = "UPDATE segment_x_resource SET Deleted_At = NOW(), Last_UpdatedBy = ?, Last_Updated_At = NOW() "
                        +
                        "WHERE Object_Reference_ID = ? AND Deleted_At IS NULL";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                    pstmt.setInt(1, userId);
                    pstmt.setInt(2, objectRefId);
                    int deleted = pstmt.executeUpdate();
                    if (deleted > 0) {
                        System.out.println("✅ Soft-deleted " + deleted + " previous active segment assignment(s) for "
                                + objectType + " #" + objectId);
                    }
                }

                // Check if a row exists for this segment + objectRef (even if deleted)
                String checkExistingSql = "SELECT COUNT(*) FROM segment_x_resource WHERE Segment_ID = ? AND Object_Reference_ID = ?";
                boolean rowExists = false;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkExistingSql)) {
                    checkStmt.setInt(1, segmentId);
                    checkStmt.setInt(2, objectRefId);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            rowExists = true;
                            System.out.println("🔍 Found existing (possibly deleted) row for segment " + segmentId
                                    + " + objectRef " + objectRefId + ", will reactivate it");
                        }
                    }
                }

                if (rowExists) {
                    // Reactivate the existing row by setting Deleted_At = NULL
                    String updateSql = "UPDATE segment_x_resource SET Deleted_At = NULL, Last_UpdatedBy = ?, Last_Updated_At = NOW() "
                            +
                            "WHERE Segment_ID = ? AND Object_Reference_ID = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, userId);
                        updateStmt.setInt(2, segmentId);
                        updateStmt.setInt(3, objectRefId);
                        int rowsAffected = updateStmt.executeUpdate();
                        System.out.println("✅ " + objectType + " #" + objectId + " reactivated in segment " + segmentId
                                + " (rows affected: " + rowsAffected + ")");
                    }
                } else {
                    // Insert new assignment (first time for this segment)
                    String insertSql = "INSERT INTO segment_x_resource (Segment_ID, Object_Reference_ID, Created_By, Created_At, Last_UpdatedBy, Last_Updated_At) "
                            +
                            "VALUES (?, ?, ?, NOW(), ?, NOW())";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, segmentId);
                        insertStmt.setInt(2, objectRefId);
                        insertStmt.setInt(3, userId);
                        insertStmt.setInt(4, userId);
                        int rowsAffected = insertStmt.executeUpdate();
                        System.out.println("✅ " + objectType + " #" + objectId + " newly assigned to segment "
                                + segmentId + " (rows affected: " + rowsAffected + ")");
                    }
                }

                // Write audit record to the facet's audit history table
                insertSegmentAuditRecord(conn, objectId, objectType, segmentId, userId, true);

                System.out.println(
                        "✅ " + objectType + " #" + objectId + " successfully assigned to segment " + segmentId);

                conn.commit();
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(prevAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /**
     * Remove a BUDG object from a segment
     * 
     * @param segmentId  The segment ID
     * @param objectId   The object ID
     * @param objectType The object type
     * @param userId     The user performing the action
     */
    public void removeObjectFromSegment(int segmentId, int objectId, String objectType, int userId)
            throws SQLException {
        int objectRefId = getOrCreateObjectReference(objectId, objectType);
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String sql = "UPDATE segment_x_resource SET Deleted_At = NOW(), Last_UpdatedBy = ?, Last_Updated_At = NOW() " +
                        "WHERE Segment_ID = ? AND Object_Reference_ID = ? AND Deleted_At IS NULL";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, userId);
                    pstmt.setInt(2, segmentId);
                    pstmt.setInt(3, objectRefId);
                    pstmt.executeUpdate();
                }
                insertSegmentAuditRecord(conn, objectId, objectType, segmentId, userId, false);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Map object type (e.g. "Client", "Glossary") to the facet's audit history table name.
     */
    private String getAuditTableNameForObjectType(String objectType) {
        if (objectType == null) return null;
        switch (objectType) {
            case "System": return "system_audit_history";
            case "Committee": return "committee_audit_history";
            case "Policy": return "policy_audit_history";
            case "Process": return "process_audit_history";
            case "Project": return "project_audit_history";
            case "Product": return "product_audit_history";
            case "BusinessArea": return "business_area_audit_history";
            case "Capability": return "capability_audit_history";
            case "Client": return "client_audit_history";
            case "Dataset": return "dataset_audit_history";
            case "Glossary": return "glossary_audit_history";
            case "LegalEntity": return "legal_audit_history";
            case "Interface": return "interface_audit_history";
            case "Attribute": return "attribute_audit_history";
            case "Regulation": return "regulation_audit_history";
            case "RegulatoryTheme": return "regulatory_theme_audit_history";
            case "Regulator": return "regulator_audit_history";
            case "Geography": return "geography_audit_history";
            case "ChangeRequest": return "changerequest_audit_history";
            default: return null;
        }
    }

    private String getSegmentName(Connection conn, int segmentId) throws SQLException {
        String sql = "SELECT Name FROM segment WHERE ID = ? AND Deleted_At IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, segmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getUserFullNameForAudit(Connection conn, int userId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) AS fullName FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return "System";
    }

    /**
     * Insert one row into the facet's audit history for segment assign/remove.
     * @param added true = "Added" (to = segment name), false = "Removed" (from = segment name)
     */
    private void insertSegmentAuditRecord(Connection conn, int objectId, String objectType, int segmentId, int userId, boolean added) {
        String auditTable = getAuditTableNameForObjectType(objectType);
        if (auditTable == null) return;
        try {
            String segmentName = getSegmentName(conn, segmentId);
            if (segmentName == null) segmentName = "Segment " + segmentId;
            String author = getUserFullNameForAudit(conn, userId);
            String updateType = added ? "Added" : "Removed";
            String sql = "INSERT INTO " + auditTable + " (id, object, event, updateType, field, `from`, `to`, author) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, objectId);
                ps.setString(2, "Segment");
                ps.setString(3, "New");
                ps.setString(4, updateType);
                ps.setString(5, "Segment");
                if (added) {
                    ps.setNull(6, Types.VARCHAR);
                    ps.setString(7, segmentName);
                } else {
                    ps.setString(6, segmentName);
                    ps.setNull(7, Types.VARCHAR);
                }
                ps.setString(8, author);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("SegmentDAO: Failed to insert segment audit record: " + e.getMessage());
            // Do not rethrow - avoid breaking the main operation
        }
    }

    /**
     * Get the segment ID for an object
     * 
     * @param objectId   The object ID
     * @param objectType The object type
     * @return The segment ID (defaults to -1 when not assigned; caller may treat as
     *         Enterprise)
     */
    public int getObjectSegmentId(int objectId, String objectType) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return getObjectSegmentId(objectId, objectType, conn);
        }
    }

    /**
     * Get the segment ID for an object using the provided connection (e.g. from
     * bulk upload servlet for same-transaction visibility).
     * 
     * @param objectId   The object ID
     * @param objectType The object type (must match segment_object_type.Type, e.g.
     *                   "Regulation", "Project")
     * @param conn       Database connection (caller manages lifecycle; not closed
     *                   by this method)
     * @return The segment ID (-1 when not assigned; caller may treat as Enterprise)
     */
    public int getObjectSegmentId(int objectId, String objectType, Connection conn) throws SQLException {
        String sql = """
                    SELECT sxr.Segment_ID
                    FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    WHERE orr.Object_ID = ?
                    AND sot.Type = ?
                    AND sxr.Deleted_At IS NULL
                    LIMIT 1
                """;
        if (conn == null) {
            return getObjectSegmentId(objectId, objectType);
        }
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, objectId);
            pstmt.setString(2, objectType);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Segment_ID");
                }
            }
        }
        return -1; // Return -1 when not assigned (let caller decide how to handle)
    }

    private List<String> getSegmentTypeCandidates(String objectType) {
        List<String> candidates = new ArrayList<>();
        if (objectType == null) {
            return candidates;
        }

        String trimmed = objectType.trim();
        if (trimmed.isEmpty()) {
            return candidates;
        }

        addCandidate(candidates, getCanonicalSegmentObjectType(trimmed));
        addCandidate(candidates, trimmed);

        String compact = trimmed.replace(" ", "").replace("_", "").replace("-", "");
        switch (compact.toLowerCase()) {
            case "system", "systems" -> {
                addCandidate(candidates, "System");
                addCandidate(candidates, "Systems");
                addCandidate(candidates, "system");
                addCandidate(candidates, "systems");
            }
            case "policy", "policies" -> {
                addCandidate(candidates, "Policy");
                addCandidate(candidates, "Policies");
                addCandidate(candidates, "policy");
                addCandidate(candidates, "policies");
            }
            case "process", "processes" -> {
                addCandidate(candidates, "Process");
                addCandidate(candidates, "Processes");
                addCandidate(candidates, "process");
                addCandidate(candidates, "processes");
            }
            case "project", "projects" -> {
                addCandidate(candidates, "Project");
                addCandidate(candidates, "Projects");
                addCandidate(candidates, "project");
                addCandidate(candidates, "projects");
            }
            case "product", "products" -> {
                addCandidate(candidates, "Product");
                addCandidate(candidates, "Products");
                addCandidate(candidates, "product");
                addCandidate(candidates, "products");
            }
            case "glossary", "glossaries" -> {
                addCandidate(candidates, "Glossary");
                addCandidate(candidates, "Glossaries");
                addCandidate(candidates, "glossary");
                addCandidate(candidates, "glossaries");
            }
            case "capability", "capabilities" -> {
                addCandidate(candidates, "Capability");
                addCandidate(candidates, "Capabilities");
                addCandidate(candidates, "capability");
                addCandidate(candidates, "capabilities");
            }
            case "client", "clients" -> {
                addCandidate(candidates, "Client");
                addCandidate(candidates, "Clients");
                addCandidate(candidates, "client");
                addCandidate(candidates, "clients");
            }
            case "committee", "committees" -> {
                addCandidate(candidates, "Committee");
                addCandidate(candidates, "Committees");
                addCandidate(candidates, "committee");
                addCandidate(candidates, "committees");
            }
            case "geography", "geographies" -> {
                addCandidate(candidates, "Geography");
                addCandidate(candidates, "Geographies");
                addCandidate(candidates, "geography");
                addCandidate(candidates, "geographies");
            }
            case "regulation", "regulations" -> {
                addCandidate(candidates, "Regulation");
                addCandidate(candidates, "Regulations");
                addCandidate(candidates, "regulation");
                addCandidate(candidates, "regulations");
            }
            case "regulator", "regulators" -> {
                addCandidate(candidates, "Regulator");
                addCandidate(candidates, "Regulators");
                addCandidate(candidates, "regulator");
                addCandidate(candidates, "regulators");
            }
            case "businessarea", "businessareas" -> {
                addCandidate(candidates, "Business Area");
                addCandidate(candidates, "Business Areas");
                addCandidate(candidates, "BusinessArea");
                addCandidate(candidates, "business area");
                addCandidate(candidates, "business areas");
                addCandidate(candidates, "businessarea");
                addCandidate(candidates, "businessareas");
            }
            case "interface", "systeminterface" -> {
                addCandidate(candidates, "Interface");
                addCandidate(candidates, "SystemInterface");
            }
            case "legalentity" -> {
                addCandidate(candidates, "Legal Entity");
                addCandidate(candidates, "LegalEntity");
            }
            case "regulatorytheme" -> {
                addCandidate(candidates, "Regulatory Theme");
                addCandidate(candidates, "RegulatoryTheme");
            }
            case "orgunit" -> {
                addCandidate(candidates, "Org Unit");
                addCandidate(candidates, "OrgUnit");
            }
            case "dataset", "datasets" -> {
                addCandidate(candidates, "Dataset");
                addCandidate(candidates, "Data Sets");
            }
            default -> {
                // Keep original only.
            }
        }

        return candidates;
    }

    private String getCanonicalSegmentObjectType(String objectType) {
        if (objectType == null) {
            return null;
        }
        String trimmed = objectType.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String compact = trimmed.replace(" ", "").replace("_", "").replace("-", "");
        return switch (compact.toLowerCase()) {
            case "system", "systems" -> "System";
            case "policy", "policies" -> "Policy";
            case "process", "processes" -> "Process";
            case "project", "projects" -> "Project";
            case "product", "products" -> "Product";
            case "glossary", "glossaries" -> "Glossary";
            case "capability", "capabilities" -> "Capability";
            case "client", "clients" -> "Client";
            case "committee", "committees" -> "Committee";
            case "geography", "geographies" -> "Geography";
            case "regulation", "regulations" -> "Regulation";
            case "regulator", "regulators" -> "Regulator";
            case "businessarea", "businessareas" -> "BusinessArea";
            case "interface", "systeminterface" -> "SystemInterface";
            case "legalentity", "legalentities", "legal" -> "LegalEntity";
            case "regulatorytheme", "regulatorythemes" -> "RegulatoryTheme";
            case "changerequest", "changerequests", "cr" -> "ChangeRequest";
            // Segment core type uses OrgUnit (no space) in DB.
            case "orgunit" -> "OrgUnit";
            case "dataset", "datasets" -> "Dataset";
            default -> trimmed;
        };
    }

    private void addCandidate(List<String> candidates, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        if (!candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    /**
     * Get all objects assigned to a segment
     * 
     * @param segmentId  The segment ID
     * @param objectType The object type to filter by (null for all types)
     * @return List of object references
     */
    public List<Map<String, Object>> getSegmentObjects(int segmentId, String objectType) throws SQLException {
        List<Map<String, Object>> objects = new ArrayList<>();

        String sql = """
                    SELECT
                        orr.Object_ID AS object_id,
                        sot.Type AS object_type,
                        sxr.Created_At AS assigned_at
                    FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    WHERE sxr.Segment_ID = ?
                    AND sxr.Deleted_At IS NULL
                """;

        if (objectType != null && !objectType.isEmpty()) {
            sql += " AND sot.Type = ?";
        }
        sql += " ORDER BY sot.Type, orr.Object_ID";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            if (objectType != null && !objectType.isEmpty()) {
                pstmt.setString(2, objectType);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> obj = new HashMap<>();
                    obj.put("object_id", rs.getInt("object_id"));
                    obj.put("object_type", rs.getString("object_type"));
                    obj.put("assigned_at", rs.getTimestamp("assigned_at"));
                    objects.add(obj);
                }
            }
        }
        return objects;
    }

    // ==================== SEGMENT DELETION WITH REASSIGNMENT ====================

    /**
     * Delete a segment and move all its objects to another segment
     * Per BUDG docs: "When you delete a segment, you need to move all the objects
     * from the segment to the Enterprise segment or another segment."
     * 
     * IMPORTANT:
     * - Reassignment during segment deletion intentionally does NOT validate
     * stakeholder
     * access to the target private segment.
     * - Access is enforced at object open time; stakeholders without access will
     * receive
     * permission denied for those objects.
     * 
     * @param segmentId       The segment to delete
     * @param targetSegmentId The segment to move objects to (1 for Enterprise)
     * @param userId          The user performing the action
     * @return true if successful
     */
    public boolean deleteSegmentWithReassignment(int segmentId, int targetSegmentId, int userId) throws SQLException {
        // Cannot delete Enterprise segment
        if (segmentId == 1) {
            throw new SQLException("Cannot delete the Enterprise segment");
        }

        // Cannot delete to the same segment
        if (segmentId == targetSegmentId) {
            throw new SQLException("Target segment cannot be the same as the segment being deleted");
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Move all object assignments to target segment
            String moveObjectsSql = """
                        UPDATE segment_x_resource
                        SET Segment_ID = ?, Last_UpdatedBy = ?, Last_Updated_At = NOW()
                        WHERE Segment_ID = ? AND Deleted_At IS NULL
                    """;
            try (PreparedStatement pstmt = conn.prepareStatement(moveObjectsSql)) {
                pstmt.setInt(1, targetSegmentId);
                pstmt.setInt(2, userId);
                pstmt.setInt(3, segmentId);
                pstmt.executeUpdate();
            }

            // 2. Soft delete all user/admin assignments for this segment
            String deleteIdentitiesSql = """
                        UPDATE segment_x_identity
                        SET Deleted_At = NOW(), Last_Updated_By = ?, Last_Updated_At = NOW()
                        WHERE Segment_ID = ? AND Deleted_At IS NULL
                    """;
            try (PreparedStatement pstmt = conn.prepareStatement(deleteIdentitiesSql)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, segmentId);
                pstmt.executeUpdate();
            }

            // 3. Soft delete the segment itself
            String deleteSegmentSql = "UPDATE segment SET Deleted_At = NOW(), Last_UpdatedBy = ?, Last_UpdatedAt = NOW() WHERE ID = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteSegmentSql)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, segmentId);
                int affected = pstmt.executeUpdate();
                if (affected == 0) {
                    conn.rollback();
                    return false;
                }
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Check if a segment has any objects assigned
     * 
     * @param segmentId The segment ID
     * @return true if segment has objects
     */
    public boolean segmentHasObjects(int segmentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM segment_x_resource WHERE Segment_ID = ? AND Deleted_At IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Get count of objects in a segment by type
     * 
     * @param segmentId The segment ID
     * @return Map of object type to count
     */
    public Map<String, Integer> getSegmentObjectCounts(int segmentId) throws SQLException {
        Map<String, Integer> counts = new HashMap<>();
        String sql = """
                    SELECT sot.Type, COUNT(*) AS count
                    FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    WHERE sxr.Segment_ID = ? AND sxr.Deleted_At IS NULL
                    GROUP BY sot.Type
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString("Type"), rs.getInt("count"));
                }
            }
        }
        return counts;
    }

    // ==================== ENTERPRISE SEGMENT PROTECTION ====================

    /**
     * Ensure Enterprise segment exists and is not deleted
     * This should be called on application startup
     */
    public void ensureEnterpriseSegment() throws SQLException {
        // Check if Enterprise segment exists
        String checkSql = "SELECT ID, Deleted_At FROM segment WHERE ID = 1";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(checkSql);
                ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                // Enterprise exists, make sure it's not deleted
                if (rs.getTimestamp("Deleted_At") != null) {
                    String restoreSql = "UPDATE segment SET Deleted_At = NULL WHERE ID = 1";
                    try (PreparedStatement restoreStmt = conn.prepareStatement(restoreSql)) {
                        restoreStmt.executeUpdate();
                        System.out.println("✅ Enterprise segment restored (was soft-deleted)");
                    }
                }
            } else {
                // Enterprise doesn't exist, create it
                String insertSql = """
                            INSERT INTO segment (ID, Name, Description, CreatedBy, Created_At, Last_UpdatedAt, Status)
                            VALUES (1, 'Enterprise', 'Default enterprise-wide segment accessible to all users', 1, NOW(), NOW(), 1)
                        """;
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.executeUpdate();
                    System.out.println("✅ Enterprise segment created");
                }
            }
        }
    }

    /**
     * Check if segment is the Enterprise segment
     */
    public boolean isEnterpriseSegment(int segmentId) {
        return segmentId == 1;
    }
}
