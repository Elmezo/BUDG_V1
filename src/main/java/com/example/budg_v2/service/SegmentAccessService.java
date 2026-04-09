package com.example.budg_v2.service;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.CacheManager;
import com.example.budg_v2.util.DefaultStakeholderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * Service for managing segment access control
 * Determines which segments a user can access based on:
 * 1. Enterprise segment (always accessible to all)
 * 2. Direct user assignment (segment_x_identity) - Role 'admin' or 'user'
 * 3. Org unit assignment (segment_x_resource)
 * 
 * NOTE: Database uses PascalCase (Segment_ID, Created_By, etc.)
 *       Compatible with project_db.sql structure
 */
public class SegmentAccessService {

    private static final Logger log = LoggerFactory.getLogger(SegmentAccessService.class);
    
    /**
     * Get all segments accessible by a user based on their role:
     * - Super Admins: All segments including Enterprise
     * - Admins: Enterprise + segments where they are admin or have access
     * - Web Users: Enterprise + segments they have access to
     * Only returns segments that exist (have been created)
     * 
     * @param userId The user ID
     * @return List of accessible segments
     */
    public static List<Map<String, Object>> getUserAccessibleSegments(int userId) throws SQLException {
        // Check cache first
        String cacheKey = CacheManager.getSegmentsKey(userId);
        List<Map<String, Object>> cached = CacheManager.getInstance().get(cacheKey);
        if (cached != null) {
            return new ArrayList<>(cached); // Return copy to prevent external modification
        }
        
        // Check if user is SuperAdmin (this will also use cache)
        boolean userIsSuperAdmin = isSuperAdmin(userId);
        
        List<Map<String, Object>> segments = new ArrayList<>();
        
        if (userIsSuperAdmin) {
            // Super Admins: see ALL segments including Enterprise
            // Super Admins don't need to be assigned to segments - they have full access
            // They can create, delete, and manage all segments
            // Cube panel appears as soon as any segment (other than Enterprise) is created
            String sql = """
                SELECT 
                    s.ID AS segment_id,
                    s.Name AS segment_name,
                    s.Description AS segment_description
                FROM segment s
                WHERE s.Deleted_At IS NULL
                ORDER BY s.ID
            """;
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> segment = new HashMap<>();
                        segment.put("id", rs.getInt("segment_id"));
                        segment.put("name", rs.getString("segment_name"));
                        segment.put("description", rs.getString("segment_description"));
                        segments.add(segment);
                    }
                }
            }
            
            // Always ensure Enterprise exists (create if missing or show even if deleted)
            boolean hasEnterprise = segments.stream().anyMatch(s -> (Integer)s.get("id") == 1);
            if (!hasEnterprise) {
                // Check if Enterprise exists (even if deleted)
                String enterpriseCheckSql = """
                    SELECT ID, Name, Description
                    FROM segment
                    WHERE ID = 1
                """;
                try (Connection conn = DatabaseConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(enterpriseCheckSql)) {
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            Map<String, Object> enterprise = new HashMap<>();
                            enterprise.put("id", 1);
                            enterprise.put("name", rs.getString("Name") != null ? rs.getString("Name") : "Enterprise");
                            enterprise.put("description", rs.getString("Description") != null ? rs.getString("Description") : "Default enterprise-wide segment");
                            segments.add(0, enterprise);
                        } else {
                            // Enterprise doesn't exist, create a default entry
                            Map<String, Object> enterprise = new HashMap<>();
                            enterprise.put("id", 1);
                            enterprise.put("name", "Enterprise");
                            enterprise.put("description", "Default enterprise-wide segment accessible to all users");
                            segments.add(0, enterprise);
                        }
                    }
                }
            }
            
        } else {
            // Admins and Web Users: get accessible segments
            // Enterprise (ID=1) is always accessible to all users, even if deleted
            // Also get segments from v_user_accessible_segments view (which may exclude deleted Enterprise)
            String sql = """
                SELECT DISTINCT
                    vas.segment_id,
                    vas.segment_name,
                    vas.segment_description
                FROM v_user_accessible_segments vas
                WHERE vas.user_id = ?
                ORDER BY vas.segment_id
            """;
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> segment = new HashMap<>();
                        segment.put("id", rs.getInt("segment_id"));
                        segment.put("name", rs.getString("segment_name"));
                        segment.put("description", rs.getString("segment_description"));
                        segments.add(segment);
                    }
                }
            }
            
            // Always ensure Enterprise (ID=1) is included for all users, even if deleted
            // Enterprise is the default segment and should always be accessible
            boolean hasEnterprise = segments.stream().anyMatch(s -> (Integer)s.get("id") == 1);
            if (!hasEnterprise) {
                // Get Enterprise segment info (even if deleted)
                String enterpriseSql = """
                    SELECT ID, Name, Description
                    FROM segment
                    WHERE ID = 1
                """;
                try (Connection conn = DatabaseConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(enterpriseSql)) {
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            Map<String, Object> enterprise = new HashMap<>();
                            enterprise.put("id", 1);
                            String name = rs.getString("Name");
                            String desc = rs.getString("Description");
                            enterprise.put("name", name != null ? name : "Enterprise");
                            enterprise.put("description", desc != null ? desc : "Default enterprise-wide segment accessible to all users");
                            segments.add(0, enterprise);
                        } else {
                            // Enterprise doesn't exist in database, create default entry
                            Map<String, Object> enterprise = new HashMap<>();
                            enterprise.put("id", 1);
                            enterprise.put("name", "Enterprise");
                            enterprise.put("description", "Default enterprise-wide segment accessible to all users");
                            segments.add(0, enterprise);
                        }
                    }
                }
            }
            
        }
        
        // Cache the result
        CacheManager.getInstance().put(cacheKey, new ArrayList<>(segments), CacheManager.SEGMENTS_TTL);
        
        return segments;
    }
    
    /**
     * Invalidate segments cache for a user
     * Call this when user's segment access changes
     * @param userId The user ID
     */
    public static void invalidateSegmentsCache(int userId) {
        String cacheKey = CacheManager.getSegmentsKey(userId);
        CacheManager.getInstance().remove(cacheKey);
    }
    
    /**
     * Invalidate segments cache for all users
     * Call this when segments are created/deleted/modified
     */
    public static void invalidateAllSegmentsCache() {
        CacheManager.getInstance().invalidateByPrefix("segments:user:");
    }
    
    /**
     * Check if user has access to a specific segment
     * @param userId The user ID
     * @param segmentId The segment ID
     * @return true if user has access
     */
    public static boolean hasSegmentAccess(int userId, int segmentId) throws SQLException {
        // Enterprise (ID=1) is always accessible
        if (segmentId == 1) return true;
        
        String sql = """
            SELECT COUNT(*) AS has_access
            FROM v_user_accessible_segments
            WHERE user_id = ? AND segment_id = ?
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("has_access") > 0;
                }
            }
        }
        return false;
    }
    
    /**
     * Get IDs of segments accessible by user
     * @param userId The user ID
     * @return List of segment IDs
     */
    public static List<Integer> getUserSegmentIds(int userId) throws SQLException {
        List<Integer> segmentIds = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT segment_id
            FROM v_user_accessible_segments
            WHERE user_id = ?
            ORDER BY segment_id
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    segmentIds.add(rs.getInt("segment_id"));
                }
            }
        }
        
        // Always ensure Enterprise is included
        if (!segmentIds.contains(1)) {
            segmentIds.add(0, 1);
        }
        
        return segmentIds;
    }
    
    /**
     * Get SQL WHERE clause for filtering by accessible segments
     * Usage: WHERE ${getSegmentFilterClause("d", userId)}
     * 
     * @param tableAlias The table alias (e.g., "d" for dataset)
     * @param userId The user ID to filter for
     * @return SQL WHERE clause string
     */
    public static String getSegmentFilterSql(String tableAlias, int userId) throws SQLException {
        List<Integer> segmentIds = getUserSegmentIds(userId);
        if (segmentIds.isEmpty()) {
            return String.format("%s.Segment_ID = 1", tableAlias); // Enterprise only
        }
        
        String ids = segmentIds.stream()
                               .map(String::valueOf)
                               .reduce((a, b) -> a + "," + b)
                               .orElse("1");
        
        return String.format("%s.Segment_ID IN (%s)", tableAlias, ids);
    }
    
    /**
     * Check if user is a segment admin for a specific segment
     * Uses PascalCase column names from project database
     * 
     * @param userId The user ID
     * @param segmentId The segment ID
     * @return true if user is a segment admin
     */
    public static boolean isSegmentAdmin(int userId, int segmentId) throws SQLException {
        String sql = """
            SELECT COUNT(*) AS is_admin
            FROM segment_x_identity sxi
            JOIN object_reference orr ON sxi.Object_Ref_ID = orr.ID
            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
            WHERE sxi.Segment_ID = ?
              AND orr.Object_ID = ?
              AND sot.Type = 'People'
              AND sxi.Role = 'admin'
              AND sxi.Deleted_At IS NULL
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            pstmt.setInt(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("is_admin") > 0;
                }
            }
        }
        return false;
    }
    
    /**
     * Check if user is a Super Admin (full access, no assignment needed)
     * Dynamically checks the role name from the role table
     * Uses cache to avoid repeated database queries
     * @param userId The user ID
     * @return true if user has 'Super Admin' role (exact match)
     */
    public static boolean isSuperAdmin(int userId) throws SQLException {
        // Check cache first
        String cacheKey = CacheManager.getSuperAdminKey(userId);
        Boolean cached = CacheManager.getInstance().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Join people with role table to get the actual role name
        String sql = """
            SELECT r.primaryname AS role_name
            FROM people p
            LEFT JOIN role r ON p.System_Role = r.id
            WHERE p.ID = ?
        """;
        
        boolean isSuperAdmin = false;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String roleName = rs.getString("role_name");
                    
                    if (roleName != null) {
                        // Only Super Admin has full access without assignment
                        // "Admin" needs to be assigned to segments
                        isSuperAdmin = AppRoleNames.isSuperAdminName(roleName);
                    }
                }
            }
        }
        
        // Cache the result
        CacheManager.getInstance().put(cacheKey, isSuperAdmin, CacheManager.SUPER_ADMIN_TTL);
        
        return isSuperAdmin;
    }
    
    /**
     * Invalidate Super Admin cache for a user
     * Call this when user's role changes
     * @param userId The user ID
     */
    public static void invalidateSuperAdminCache(int userId) {
        String cacheKey = CacheManager.getSuperAdminKey(userId);
        CacheManager.getInstance().remove(cacheKey);
    }
    
    /**
     * Get the default segment for new objects
     * @return Enterprise segment ID (1)
     */
    public static int getDefaultSegmentId() {
        return 1; // Enterprise
    }

    /**
     * Get segments for anonymous/guest users (no login).
     * Returns only the Enterprise segment (ID=1) so the UI can show the cube without 401.
     */
    public static List<Map<String, Object>> getSegmentsForAnonymousUser() throws SQLException {
        List<Map<String, Object>> segments = new ArrayList<>();
        String sql = "SELECT ID, Name, Description FROM segment WHERE ID = 1 AND Deleted_At IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> enterprise = new HashMap<>();
                    enterprise.put("id", 1);
                    enterprise.put("name", rs.getString("Name") != null ? rs.getString("Name") : "Enterprise");
                    enterprise.put("description", rs.getString("Description") != null ? rs.getString("Description") : "Default enterprise-wide segment");
                    segments.add(enterprise);
                } else {
                    Map<String, Object> enterprise = new HashMap<>();
                    enterprise.put("id", 1);
                    enterprise.put("name", "Enterprise");
                    enterprise.put("description", "Default enterprise-wide segment accessible to all users");
                    segments.add(enterprise);
                }
            }
        }
        return segments;
    }
    
    /**
     * Get list of segment IDs the user can access
     * Used for filtering queries
     * @param userId The user ID
     * @return Set of accessible segment IDs
     */
    public static Set<Integer> getAccessibleSegmentIds(int userId) throws SQLException {
        List<Map<String, Object>> segments = getUserAccessibleSegments(userId);
        return segments.stream()
                .map(s -> (Integer) s.get("id"))
                .collect(java.util.stream.Collectors.toSet());
    }
    
    /**
     * Check if user can access an object based on its segment
     * @param userId The user ID
     * @param objectId The object ID
     * @param objectType The object type (e.g., "Dataset", "Glossary", etc.)
     * @return true if user can access the object
     */
    public static boolean canAccessObject(int userId, int objectId, String objectType) throws SQLException {
        // Super Admin can access everything
        if (isSuperAdmin(userId)) {
            return true;
        }
        
        // Get the segment of the object
        int objectSegmentId = getObjectSegmentId(objectId, objectType);
        
        // If object has no segment assignment (-1), access is denied (not found)
        if (objectSegmentId == -1) {
            return false;
        }
        
        // Enterprise segment (ID=1) is accessible to all
        if (objectSegmentId == 1) {
            return true;
        }
        
        // Check if user has access to the object's segment
        Set<Integer> accessibleSegments = getAccessibleSegmentIds(userId);
        return accessibleSegments.contains(objectSegmentId);
    }
    
    /**
     * Check if a segment exists in the database
     * @param segmentId The segment ID to check
     * @return true if segment exists and is not deleted, false otherwise
     */
    @SuppressWarnings("unused")
    private static boolean segmentExists(int segmentId) throws SQLException {
        String sql = "SELECT 1 FROM segment WHERE ID = ? AND Deleted_At IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    /**
     * Get the segment ID of an object
     * @param objectId The object ID
     * @param objectType The object type
     * @return Segment ID if found, or -1 if object has no segment assignment (not found)
     */
    public static int getObjectSegmentId(int objectId, String objectType) throws SQLException {
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
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, objectId);
            pstmt.setString(2, objectType);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Segment_ID");
                }
            }
        }
        
        // Object has no segment assignment - return -1 (not found)
        // Do not default to Enterprise - objects must have explicit segment assignments
        return -1;
    }
    
    /**
     * E-10 / TC-015: Check whether an object has been soft-deleted.
     * Returns {@code true} if the object is soft-deleted (deleteddatetime is set),
     * {@code false} if it is live or if the type is not tracked here (fail-open).
     */
    public static boolean isSoftDeleted(int objectId, String objectType) {
        String[] tableAndCol = softDeleteTableFor(objectType);
        if (tableAndCol == null) return false; // unknown type – fail-open
        String sql = "SELECT 1 FROM " + tableAndCol[0]
                + " WHERE id = ? AND " + tableAndCol[1] + " IS NOT NULL LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false; // fail-open on error
        }
    }

    private static String[] softDeleteTableFor(String objectType) {
        if (objectType == null) return null;
        return switch (objectType) {
            case "Dataset"        -> new String[]{"dataset",        "DeletedDatetime"};
            case "System"         -> new String[]{"system",         "DeletedDatetime"};
            case "Glossary"       -> new String[]{"glossary",       "DeletedDatetime"};
            case "Process"        -> new String[]{"process",        "DeletedDatetime"};
            case "Project"        -> new String[]{"project",        "DeletedDatetime"};
            case "Product"        -> new String[]{"product",        "deleteddatetime"};
            case "Policy"         -> new String[]{"policy",         "DeletedDatetime"};
            case "LegalEntity"    -> new String[]{"legal",          "deletedatetime"};
            case "BusinessArea"   -> new String[]{"business_area",  "deletedatetime"};
            case "Capability"     -> new String[]{"capability",     "DeletedDatetime"};
            case "Client"         -> new String[]{"client",         "DeleteDatetime"};
            case "Committee"      -> new String[]{"committee",      "DeleteDatetime"};
            case "Geography"      -> new String[]{"geography",      "DeletedDatetime"};
            case "Regulation"     -> new String[]{"regulation",     "DeletedDatetime"};
            case "Regulator"      -> new String[]{"regulator",      "DeletedDatetime"};
            case "RegulatoryTheme"-> new String[]{"regulatorytheme","DeletedDatetime"};
            case "SystemInterface"-> new String[]{"interface",      "deleted_datetime"};
            default               -> null;
        };
    }

    /**
     * Build SQL WHERE clause for segment-based filtering
     * @param userId The user ID
     * @param objectType The object type (e.g., "Dataset")
     * @param objectIdColumn The column name for object ID in the query (e.g., "d.ID")
     * @return SQL WHERE clause fragment, or empty string if super admin
     */
    public static String buildSegmentFilterClause(int userId, String objectType, String objectIdColumn) throws SQLException {
        // All users (including Super Admin) are filtered by their cube-selected segments.
        // getEffectiveFilterSegmentIds returns cube selection for Super Admin,
        // and accessible ∩ selected segments for other roles.
        Set<Integer> accessibleSegments = getEffectiveFilterSegmentIds(userId);
        
        if (accessibleSegments.isEmpty()) {
            // User has no segment access - return impossible condition
            return " AND 1=0 ";
        }
        
        // Build the filter: object must be in Enterprise OR in user's accessible segments
        String segmentIds = accessibleSegments.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        
        return String.format("""
            AND (
                -- Object is in Enterprise segment (accessible to all)
                EXISTS (
                    SELECT 1 FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    WHERE orr.Object_ID = %s
                    AND sot.Type = '%s'
                    AND sxr.Segment_ID = 1
                    AND sxr.Deleted_At IS NULL
                )
                OR
                -- Object is in user's accessible segments
                EXISTS (
                    SELECT 1 FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    WHERE orr.Object_ID = %s
                    AND sot.Type = '%s'
                    AND sxr.Segment_ID IN (%s)
                    AND sxr.Deleted_At IS NULL
                )
                OR
                -- Object has no segment assignment (treat as Enterprise)
                NOT EXISTS (
                    SELECT 1 FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    WHERE orr.Object_ID = %s
                    AND sot.Type = '%s'
                    AND sxr.Deleted_At IS NULL
                )
            )
        """, objectIdColumn, objectType, objectIdColumn, objectType, segmentIds, objectIdColumn, objectType);
    }
    
    /**
     * Check if user can edit an object based on segment and role
     * - Super Admin: Can edit all
     * - Admin: Can edit Enterprise + assigned segments
     * - WebUser: Can edit if they have role-based Edit permission AND can access the object
     * @param userId The user ID
     * @param objectId The object ID
     * @param objectType The object type
     * @return true if user can edit the object
     */
    public static boolean canEditObject(int userId, int objectId, String objectType) throws SQLException {
        // Get user's role
        String roleName = getUserRole(userId);
        if (roleName == null) {
            return false;
        }
        
        // Super Admin can edit everything
        if (AppRoleNames.isSuperAdminName(roleName)) {
            return true;
        }
        
        // Get object segment ID
            int objectSegmentId = getObjectSegmentId(objectId, objectType);
            
        // Admin can edit objects in Enterprise and their assigned segments
        if (AppRoleNames.isAdminOnlyName(roleName)) {
            // If object has no segment assignment (-1), cannot edit (not found)
            if (objectSegmentId == -1) {
                return false;
            }
            
            // Enterprise is always editable by Admin
            if (objectSegmentId == 1) {
                return true;
            }
            
            // Check if admin is assigned to the object's segment
            Set<Integer> accessibleSegments = getAccessibleSegmentIds(userId);
            return accessibleSegments.contains(objectSegmentId);
        }
        
        // WebUser: Can edit if they have role-based permission
        // The role-based permission check is already done in the servlet before calling this method
        // For WebUsers with role-based Edit permission:
        // - If object is in Enterprise (segmentId = 1), allow edit
        // - If object has no segment assignment (segmentId = -1), allow edit (user has role-based permission)
        // - If object is in accessible segment, allow edit
        if (objectSegmentId == 1) {
            // Enterprise - always accessible
            return true;
        }
        
        if (objectSegmentId == -1) {
            // No segment assignment - allow if user has role-based permission (already checked in servlet)
            return true;
        }
        
        // Check if user can access the object's segment
        Set<Integer> accessibleSegments = getAccessibleSegmentIds(userId);
        return accessibleSegments.contains(objectSegmentId);
    }
    
    /**
     * Get user's role name
     * @param userId The user ID
     * @return Role name or null if not found
     */
    public static String getUserRole(int userId) throws SQLException {
        String cacheKey = CacheManager.getUserRoleKey(userId);
        String cached = CacheManager.getInstance().get(cacheKey);
        if (cached != null) {
            return "__NULL__".equals(cached) ? null : cached;
        }

        String sql = """
            SELECT r.primaryname AS role_name
            FROM people p
            LEFT JOIN role r ON p.System_Role = r.id
            WHERE p.ID = ?
        """;

        String roleName = null;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    roleName = rs.getString("role_name");
                }
            }
        }

        CacheManager.getInstance().put(cacheKey, roleName != null ? roleName : "__NULL__", CacheManager.USER_ROLE_TTL);
        return roleName;
    }
    
    /**
     * Check if guest user can access an object.
     * Guests can ONLY access objects that are:
     * 1. Public (Is_Public = 1 or isPublic = 1 or is_Public = 1, depending on table)
     * 2. In Enterprise segment (Segment_ID = 1) OR have no segment assignment
     * 3. Not deleted
     * 
     * @param objectId The object ID
     * @param objectType The object type (e.g., "Dataset", "Glossary", "System", etc.)
     * @return true if guest can access, false otherwise
     * @throws SQLException if database error occurs
     */
    public static boolean canGuestAccessObject(int objectId, String objectType) throws SQLException {
        // Step 1: Check if object is public
        if (!isObjectPublic(objectId, objectType)) {
            return false;
        }
        
        // Step 2: Check segment (must be Enterprise or no segment)
        int segmentId = getObjectSegmentId(objectId, objectType);
        
        // Allow if: Enterprise (1) OR no segment assignment (-1)
        // Deny if: Private segment (any ID other than 1 or -1)
        return (segmentId == 1 || segmentId == -1);
    }
    
    /**
     * Check if an object is public (Is_Public = 1).
     * Handles different column name variations across tables.
     * 
     * @param objectId The object ID
     * @param objectType The object type
     * @return true if object is public, false otherwise
     * @throws SQLException if database error occurs
     */
    private static boolean isObjectPublic(int objectId, String objectType) throws SQLException {
        String tableName = getTableName(objectType);
        if (tableName == null) {
            return false;
        }
        
        // Determine the correct Is_Public column name for this table
        String publicColumnName = getPublicColumnName(objectType);
        if (publicColumnName == null) {
            // Table doesn't have Is_Public column - deny access for guests
            return false;
        }
        
        String sql = String.format(
            "SELECT %s FROM %s WHERE ID = ? OR id = ?",
            publicColumnName, tableName
        );
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, objectId);
            pstmt.setInt(2, objectId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Object isPublicObj = rs.getObject(1);
                    if (isPublicObj == null) {
                        return false;
                    }
                    
                    // Handle different data types (int, boolean)
                    if (isPublicObj instanceof Number) {
                        return ((Number) isPublicObj).intValue() == 1;
                    } else if (isPublicObj instanceof Boolean) {
                        return (Boolean) isPublicObj;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get the Is_Public column name for a given object type.
     * Different tables use different naming conventions.
     * 
     * @param objectType The object type
     * @return Column name or null if table doesn't have Is_Public
     */
    private static String getPublicColumnName(String objectType) {
        if (objectType == null) return null;
        
        String normalized = objectType.toLowerCase().trim();
        return switch (normalized) {
            case "glossary" -> "Is_Public";
            case "business_area", "businessarea" -> "Is_Public";
            case "capability" -> "Is_Public";
            case "committee" -> "Is_Public";
            case "legal_entity", "legalentity", "legal" -> "Is_Public";
            case "system" -> "is_Public";
            case "interface", "systeminterface" -> "is_Public";
            case "product" -> "is_Public";
            case "process" -> "isPublic";
            case "policy" -> "isPublic";
            case "project" -> "is_public";
            case "dataset" -> "AccessControlType"; // Dataset uses AccessControlType (1 = Public)
            case "regulation" -> "Is_Public";
            case "regulator" -> null; // Regulator doesn't have Is_Public
            case "regulatory_theme", "regulatorytheme" -> null; // RegulatoryTheme doesn't have Is_Public
            case "geography" -> null; // Geography doesn't have Is_Public
            case "client" -> null; // Client doesn't have Is_Public
            case "org_unit", "orgunit" -> null; // Org Unit doesn't have Is_Public
            case "people", "person" -> null; // People doesn't have Is_Public
            default -> null;
        };
    }

    /**
     * Get user's preferred locale from people.Locale
     * @param userId The user ID
     * @return Locale code (e.g. "en", "ar") or null if not set
     */
    public static String getUserLocale(int userId) throws SQLException {
        String sql = """
            SELECT Locale FROM people
            WHERE ID = ? AND (Deleted_date IS NULL OR Deleted_date = '')
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String locale = rs.getString("Locale");
                    return (locale != null && !locale.isBlank()) ? locale.trim() : null;
                }
            }
        }
        return null;
    }
    
    // ==================== PARENT SELECTION FILTERING ====================
    // Per BUDG v7.0-7.2: Users can only see objects from segments they have access to
    
    /**
     * Get list of object IDs that user can access for a given object type.
     * Used for filtering parent selection dropdowns.
     * 
     * @param userId The user ID
     * @param objectType The object type (e.g., "Glossary", "Policy", "System")
     * @return Set of accessible object IDs
     */
    public static Set<Integer> getAccessibleObjectIds(int userId, String objectType) throws SQLException {
        Set<Integer> accessibleIds = new HashSet<>();
        
        // Super Admin can access everything
        if (isSuperAdmin(userId)) {
            return getAllObjectIds(objectType);
        }
        
        Set<Integer> accessibleSegments = getAccessibleSegmentIds(userId);
        
        // Get objects in Enterprise segment (always accessible)
        // Plus objects in user's accessible segments
        // Plus objects without segment assignment (treated as Enterprise)
        String sql = """
            SELECT DISTINCT obj_id FROM (
                -- Objects in Enterprise segment (ID=1)
                SELECT orr.Object_ID as obj_id
                FROM segment_x_resource sxr
                JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                WHERE sot.Type = ?
                AND sxr.Segment_ID = 1
                AND sxr.Deleted_At IS NULL
                
                UNION
                
                -- Objects in user's accessible segments
                SELECT orr.Object_ID as obj_id
                FROM segment_x_resource sxr
                JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                WHERE sot.Type = ?
                AND sxr.Segment_ID IN (%s)
                AND sxr.Deleted_At IS NULL
            ) AS accessible_objects
        """;
        
        String segmentIdList = accessibleSegments.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        
        if (segmentIdList.isEmpty()) {
            segmentIdList = "1"; // Default to Enterprise only
        }
        
        String formattedSql = String.format(sql, segmentIdList);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(formattedSql)) {
            pstmt.setString(1, objectType);
            pstmt.setString(2, objectType);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    accessibleIds.add(rs.getInt("obj_id"));
                }
            }
        }
        
        // Also include objects that have NO segment assignment (treated as Enterprise)
        Set<Integer> unassignedObjects = getObjectsWithoutSegment(objectType);
        accessibleIds.addAll(unassignedObjects);
        
        return accessibleIds;
    }
    
    /**
     * Get all object IDs for a given type (for Super Admin)
     */
    private static Set<Integer> getAllObjectIds(String objectType) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        
        String tableName = getTableName(objectType);
        if (tableName == null) return ids;
        
        // Get the correct deleted column name for this table
        String deletedColumn = getDeletedColumnForTable(tableName);
        if (deletedColumn == null) {
            // Table doesn't have soft delete, query all records
            String sql = String.format("SELECT ID FROM %s", tableName);
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    ids.add(rs.getInt("ID"));
                }
            }
            return ids;
        }
        
        String sql = String.format("SELECT ID FROM %s WHERE %s IS NULL", tableName, deletedColumn);
        
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getInt("ID"));
            }
        } catch (SQLException e) {
            // Table might use different column name for ID or deletion
            System.err.println("Error getting all object IDs for " + objectType + ": " + e.getMessage());
        }
        
        return ids;
    }
    
    /**
     * Get the deleted column name for a table based on actual database schema.
     */
    private static String getDeletedColumnForTable(String tableName) {
        if (tableName == null) return null;
        String table = tableName.toLowerCase();
        
        return switch (table) {
            case "dataset" -> "DeletedDatetime";
            case "attribute" -> "DeletedDatetime";
            case "system" -> "Deleted_datetime";
            case "glossary" -> "Deleted_datetime";
            case "people" -> "Deleted_date";
            case "interface" -> "deleted_datetime";
            case "process" -> "deleteddatetime";
            case "project" -> "deletedatetime";
            case "product" -> "deleteddatetime";
            case "policy" -> "DeletedDatetime";
            case "legal_entity", "legal" -> "DeleteDatetime";
            case "business_area" -> "deletedatetime";
            case "capability" -> "DeletedDatetime";
            case "client" -> "DeleteDatetime";
            case "committee" -> "DeleteDatetime";
            case "org_unit", "orgunit" -> "deleted_Date";
            case "geography" -> "DeletedDatetime";
            case "regulation" -> "DeletedDatetime";
            case "regulator" -> "DeletedDatetime";
            case "regulatory_theme", "regulatorytheme" -> "DeletedDatetime";
            case "requirement" -> "DeletedDatetime";
            default -> null;
        };
    }
    
    /**
     * Get objects that have no segment assignment (treated as Enterprise)
     */
    private static Set<Integer> getObjectsWithoutSegment(String objectType) throws SQLException {
        Set<Integer> unassignedIds = new HashSet<>();
        
        String tableName = getTableName(objectType);
        if (tableName == null) return unassignedIds;
        
        String sql = String.format("""
            SELECT t.ID
            FROM %s t
            WHERE NOT EXISTS (
                SELECT 1 FROM segment_x_resource sxr
                JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                WHERE orr.Object_ID = t.ID
                AND sot.Type = ?
                AND sxr.Deleted_At IS NULL
            )
        """, tableName);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, objectType);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    unassignedIds.add(rs.getInt("ID"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting unassigned objects for " + objectType + ": " + e.getMessage());
        }
        
        return unassignedIds;
    }
    
    /**
     * Get table name for object type
     */
    private static String getTableName(String objectType) {
        return switch (objectType) {
            case "Glossary" -> "glossary";
            case "Policy" -> "policy";
            case "System" -> "system";
            case "Regulation" -> "regulation";
            case "Project" -> "project";
            case "Process" -> "process";
            case "OrgUnit" -> "org_unit";
            case "People" -> "people";
            case "Client" -> "client";
            case "Product" -> "product";
            case "Dataset" -> "dataset";
            case "BusinessArea" -> "business_area";
            case "Committee" -> "committee";
            case "Geography" -> "geography";
            case "RegulatoryTheme" -> "regulatorytheme";
            case "Regulator" -> "regulator";
            case "ChangeRequest" -> "changerequest";
            case "SystemInterface", "Interface" -> "interface";
            case "LegalEntity", "Legal" -> "legal";
            case "Capability" -> "capability";
            default -> null;
        };
    }
    
    /**
     * Build SQL WHERE clause for guest user filtering.
     * Guest users (userId <= 0 or no userId) should ONLY see objects that are:
     * 1. Public (Is_Public = 1 or equivalent)
     * 2. In Enterprise segment (Segment_ID = 1) OR have no segment assignment
     * 3. Not deleted
     * 
     * @param objectType The object type (must match segment_object_type.Type, e.g. "Process", "Project", "Legal")
     * @param tableAlias The table alias used in the query (e.g. "p" for process)
     * @param idColumn The full ID column reference (e.g. "p.id" or "p.ID")
     * @return SQL WHERE clause fragment (without leading AND), or null if object type not supported
     */
    public static String buildGuestFilterClause(String objectType, String tableAlias, String idColumn) {
        String tableName = getTableName(objectType);
        if (tableName == null) {
            return null;
        }
        
        String deletedColumn = getDeletedColumnForTable(tableName);
        if (deletedColumn == null) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(tableAlias).append(".").append(deletedColumn).append(" IS NULL");
        
        String publicColumn = getPublicColumnName(objectType);
        if (publicColumn != null) {
            sb.append(" AND ").append(tableAlias).append(".").append(publicColumn).append(" = 1");
        }
        
        sb.append(" AND (");
        sb.append("EXISTS (");
        sb.append("SELECT 1 FROM segment_x_resource sxr ");
        sb.append("JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID ");
        sb.append("JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID ");
        sb.append("WHERE orr.Object_ID = ").append(idColumn).append(" ");
        sb.append("AND sot.Type = '").append(objectType).append("' ");
        sb.append("AND sxr.Segment_ID = 1 AND sxr.Deleted_At IS NULL");
        sb.append(") OR NOT EXISTS (");
        sb.append("SELECT 1 FROM segment_x_resource sxr ");
        sb.append("INNER JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID ");
        sb.append("INNER JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID ");
        sb.append("WHERE orr.Object_ID = ").append(idColumn).append(" ");
        sb.append("AND sot.Type = '").append(objectType).append("' ");
        sb.append("AND sxr.Deleted_At IS NULL");
        sb.append("))");
        
        return sb.toString();
    }
    
    /**
     * Build SQL IN clause for filtering objects by segment access.
     * Use in queries like: WHERE id IN (accessible_ids) OR segment filter
     * 
     * @param userId The user ID
     * @param objectType The object type
     * @param idColumnName The ID column name in the query (e.g., "g.ID")
     * @return SQL clause fragment
     */
    public static String buildAccessibleObjectsFilter(int userId, String objectType, String idColumnName) throws SQLException {
        // All users (including Super Admin) are filtered by their cube-selected segments.
        // getEffectiveFilterSegmentIds returns cube selection for Super Admin,
        // and accessible ∩ selected segments for other roles.
        Set<Integer> accessibleSegments = getEffectiveFilterSegmentIds(userId);
        String segmentIdList = accessibleSegments.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        
        if (segmentIdList.isEmpty()) {
            segmentIdList = "1"; // Default to Enterprise only
        }
        
        // Object is accessible if:
        // 1. It's in Enterprise segment (ID=1)
        // 2. It's in one of the user's accessible segments
        // 3. It has no segment assignment (treated as Enterprise)
        return String.format("""
            (
                -- In Enterprise segment
                EXISTS (
                    SELECT 1 FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    WHERE orr.Object_ID = %s
                    AND sot.Type = '%s'
                    AND sxr.Segment_ID = 1
                    AND sxr.Deleted_At IS NULL
                )
                OR
                -- In user's accessible segments
                EXISTS (
                    SELECT 1 FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    WHERE orr.Object_ID = %s
                    AND sot.Type = '%s'
                    AND sxr.Segment_ID IN (%s)
                    AND sxr.Deleted_At IS NULL
                )
                OR
                -- No segment assignment (treated as Enterprise)
                NOT EXISTS (
                    SELECT 1 FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    WHERE orr.Object_ID = %s
                    AND sot.Type = '%s'
                    AND sxr.Deleted_At IS NULL
                )
            )
        """, idColumnName, objectType, idColumnName, objectType, segmentIdList, idColumnName, objectType);
    }
    
    /**
     * Filter a list of object IDs to only those accessible by the user.
     * This is the main method used by facet DAOs to implement segment filtering.
     * 
     * @param userId The user ID
     * @param objectType The type of objects (e.g., "Glossary", "Policy", "Process", "Product")
     * @param objectIds List of all object IDs to filter
     * @return Set of object IDs that the user can access
     */
    public static Set<Integer> getAccessibleObjectIdsInSegments(
            int userId, 
            String objectType, 
            List<Integer> objectIds) throws SQLException {
        
        // If no objects to filter, return empty set
        if (objectIds == null || objectIds.isEmpty()) {
            return new HashSet<>();
        }
        
        Set<Integer> accessibleIds = new HashSet<>();
        
        // All users (including Super Admin) are filtered by their cube-selected segments.
        // getEffectiveFilterSegmentIds returns cube selection for Super Admin,
        // and accessible ∩ selected segments for other roles.
        Set<Integer> accessibleSegments = getEffectiveFilterSegmentIds(userId);
        
        // For each object, check if it's accessible
        for (Integer objectId : objectIds) {
            if (objectId == null) continue;
            
            int objectSegmentId = getObjectSegmentId(objectId, objectType);
            
            // Object is accessible if:
            // 1. It's in Enterprise segment (ID=1)
            // 2. It's in one of the user's accessible segments
            // Objects with no segment assignment (-1) are not accessible
            if (objectSegmentId > 0 && (objectSegmentId == 1 || accessibleSegments.contains(objectSegmentId))) {
                accessibleIds.add(objectId);
            }
        }
        
        return accessibleIds;
    }
    
    // ==================== USER SELECTED SEGMENTS (CUBE FILTER) ====================
    // These methods respect the user's selection in the Segments Cube panel
    
    /**
     * Get user's SELECTED segment IDs from the cube panel (user_segment_selection table).
     * These are the segments the user has chosen to view in the cube panel.
     * If no selection exists, defaults to Enterprise (ID=1).
     * 
     * @param userId The user ID
     * @return Set of selected segment IDs
     */
    public static Set<Integer> getUserSelectedSegmentIds(int userId) throws SQLException {
        String cacheKey = CacheManager.getSelectedSegmentsKey(userId);
        Set<Integer> cached = CacheManager.getInstance().get(cacheKey);
        if (cached != null) {
            return new HashSet<>(cached);
        }

        Set<Integer> selectedIds = new HashSet<>();

        String sql = "SELECT segment_id FROM user_segment_selection WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    selectedIds.add(rs.getInt("segment_id"));
                }
            }
        }

        // If no selection saved, default to Enterprise
        if (selectedIds.isEmpty()) {
            selectedIds.add(1);
        }

        CacheManager.getInstance().put(cacheKey, new HashSet<>(selectedIds), CacheManager.SELECTED_SEGMENTS_TTL);
        return selectedIds;
    }
    
    /**
     * Get the effective segment filter for a user - intersection of accessible AND selected segments.
     * This ensures user only sees objects in segments they both have access to AND have selected.
     * 
     * @param userId The user ID
     * @return Set of segment IDs to filter by
     */
    public static Set<Integer> getEffectiveFilterSegmentIds(int userId) throws SQLException {
        String cacheKey = CacheManager.getEffectiveSegmentsKey(userId);
        Set<Integer> cached = CacheManager.getInstance().get(cacheKey);
        if (cached != null) {
            return new HashSet<>(cached);
        }

        Set<Integer> effectiveSegments;

        // Super Admin sees based on their selection only (they have access to everything)
        if (isSuperAdmin(userId)) {
            effectiveSegments = getUserSelectedSegmentIds(userId);
        } else {
            Set<Integer> accessibleSegments = getAccessibleSegmentIds(userId);
            Set<Integer> selectedSegments = getUserSelectedSegmentIds(userId);

            // Intersection: only segments that are both accessible AND selected
            effectiveSegments = new HashSet<>(accessibleSegments);
            effectiveSegments.retainAll(selectedSegments);

            // If intersection is empty, default to Enterprise (always accessible)
            if (effectiveSegments.isEmpty()) {
                effectiveSegments.add(1);
            }
        }

        CacheManager.getInstance().put(cacheKey, new HashSet<>(effectiveSegments), CacheManager.SELECTED_SEGMENTS_TTL);
        return effectiveSegments;
    }
    
    /**
     * Build SQL WHERE clause for filtering by user's SELECTED segments (cube filter).
     * Objects are visible if they're in one of the user's selected segments.
     * 
     * @param userId The user ID
     * @param objectType The object type (e.g., "Dataset", "Glossary")
     * @param objectIdColumn The column name for object ID (e.g., "d.ID")
     * @return SQL WHERE clause fragment
     */
    public static String buildSelectedSegmentFilterClause(int userId, String objectType, String objectIdColumn) throws SQLException {
        Set<Integer> effectiveSegments = getEffectiveFilterSegmentIds(userId);
        
        String segmentIds = effectiveSegments.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        
        // Object is visible if:
        // 1. It's in one of the user's selected segments
        // 2. OR it has no segment assignment AND Enterprise is selected
        boolean enterpriseSelected = effectiveSegments.contains(1);
        
        if (enterpriseSelected) {
            // Simplified logic: Object is visible if:
            // 1. It's in one of the user's selected segments, OR
            // 2. It has no active segment assignment (no active segment_x_resource row) - treated as Enterprise
            // This covers both cases: no object_reference OR object_reference exists but no segment_x_resource
            return String.format("""
                (
                    -- Object is in user's selected segments
                    EXISTS (
                        SELECT 1 FROM segment_x_resource sxr
                        JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                        JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                        WHERE orr.Object_ID = %s
                        AND sot.Type = '%s'
                        AND sxr.Segment_ID IN (%s)
                        AND sxr.Deleted_At IS NULL
                    )
                    OR
                    -- Object has no active segment assignment (treated as Enterprise)
                    -- This works for both: no object_reference OR object_reference exists but no segment_x_resource
                    -- If object_reference doesn't exist, the JOIN returns no rows, so NOT EXISTS is true
                    -- If object_reference exists but no segment_x_resource, NOT EXISTS is also true
                    NOT EXISTS (
                        SELECT 1 FROM segment_x_resource sxr
                        INNER JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                        INNER JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                        WHERE orr.Object_ID = %s
                        AND sot.Type = '%s'
                        AND sxr.Deleted_At IS NULL
                    )
                )
            """, objectIdColumn, objectType, segmentIds, objectIdColumn, objectType);
        } else {
            // Enterprise not selected - only show objects explicitly in selected segments
            return String.format("""
                EXISTS (
                    SELECT 1 FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    WHERE orr.Object_ID = %s
                    AND sot.Type = '%s'
                    AND sxr.Segment_ID IN (%s)
                    AND sxr.Deleted_At IS NULL
                )
            """, objectIdColumn, objectType, segmentIds);
        }
    }
    
    /**
     * Filter objects by user's SELECTED segments (cube filter).
     * This is the main filtering method that respects the cube selection.
     * 
     * @param userId The user ID
     * @param objectType The object type
     * @param objectIds List of object IDs to filter
     * @return Set of object IDs that are in the user's selected segments
     */
    public static Set<Integer> filterBySelectedSegments(
            int userId,
            String objectType,
            List<Integer> objectIds) throws SQLException {

        if (objectIds == null || objectIds.isEmpty()) {
            return new HashSet<>();
        }

        Set<Integer> effectiveSegments = getEffectiveFilterSegmentIds(userId);

        // Check if user is WebUser (not Admin or Super Admin)
        String userRole = getUserRole(userId);
        boolean isWebUser = userRole != null &&
            !AppRoleNames.isAdminOrSuperAdminName(userRole);

        // Batch-fetch segment assignments for all IDs in one query instead of N queries.
        // Returns: objectId -> segmentId (only rows that have an assignment)
        Map<Integer, Integer> segmentByObjectId = batchGetObjectSegmentIds(objectIds, objectType);

        Set<Integer> filteredIds = new HashSet<>();

        for (Integer objectId : objectIds) {
            if (objectId == null) continue;

            // Use batch result; default to Enterprise (1) when no assignment found
            int objectSegmentId = segmentByObjectId.getOrDefault(objectId, 1);

            // Check if object is in user's selected segments
            boolean inSelectedSegment = objectSegmentId > 0 && effectiveSegments.contains(objectSegmentId);

            // For WebUsers, skip soft-deleted objects
            if (isWebUser && inSelectedSegment) {
                if (isSoftDeleted(objectId, objectType)) {
                    continue;
                }
            }

            if (inSelectedSegment) {
                filteredIds.add(objectId);
            }
        }

        return filteredIds;
    }

    /**
     * Batch-fetch the segment ID for each object ID in one SQL query.
     * Objects with no assignment are absent from the result map (caller treats them as Enterprise).
     */
    private static Map<Integer, Integer> batchGetObjectSegmentIds(
            List<Integer> objectIds, String objectType) throws SQLException {

        Map<Integer, Integer> result = new HashMap<>();
        if (objectIds == null || objectIds.isEmpty()) return result;

        // Deduplicate
        List<Integer> unique = objectIds.stream().filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        if (unique.isEmpty()) return result;

        // Build IN clause
        String placeholders = unique.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = String.format("""
            SELECT orr.Object_ID, sxr.Segment_ID
            FROM segment_x_resource sxr
            JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
            WHERE sot.Type = ?
              AND orr.Object_ID IN (%s)
              AND sxr.Deleted_At IS NULL
            ORDER BY sxr.Segment_ID
        """, placeholders);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, objectType);
            for (int i = 0; i < unique.size(); i++) {
                ps.setInt(i + 2, unique.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int oid = rs.getInt("Object_ID");
                    int sid = rs.getInt("Segment_ID");
                    // Keep the first (lowest) segment ID encountered per object;
                    // Enterprise (1) wins if present, which is the desired behaviour.
                    result.putIfAbsent(oid, sid);
                    if (sid == 1) result.put(oid, 1); // Enterprise always wins
                }
            }
        } catch (SQLException e) {
            log.warn("[SegmentAccessService] batchGetObjectSegmentIds failed for type={}: {}", objectType, e.getMessage());
        }

        return result;
    }
    
    // ==================== STAKEHOLDER SELECTION FILTERING ====================
    
    /**
     * Get users available for stakeholder selection based on object's segment.
     * For objects in private segments, only returns:
     * - Web users that have access to the segment
     * - All super admins
     * - Admins who have access to that segment OR are admins to that segment
     * 
     * For Enterprise segment or unassigned objects, returns all users with the role.
     * 
     * @param objectId The object ID
     * @param objectType The object type (e.g., "Project", "Capability", "System")
     * @param roleId The role ID to filter by
     * @return List of user maps with PeopleID and Name
     * @throws SQLException if database error occurs
     */
    public static List<Map<String, Object>> getUsersForStakeholderSelection(int objectId, String objectType, int roleId) throws SQLException {
        List<Map<String, Object>> users = new ArrayList<>();
        
        // Get the object's segment ID
        int segmentId = getObjectSegmentId(objectId, objectType);
        
        // If Enterprise (ID=1) or unassigned (-1), return all users with the role (current behavior)
        if (segmentId == 1 || segmentId == -1) {
            return getAllUsersByRole(roleId);
        }
        
        // For private segments, filter users based on access
        String sql = """
            SELECT DISTINCT
                p.ID AS PeopleID,
                CONCAT(p.First_Name, ' ', p.Last_Name, ' (', p.Email, ')') AS Name
            FROM role_assignment ra
            JOIN people p ON REPLACE(REPLACE(REPLACE(ra.users, '[', ''), ']', ''), ' ', '')
                REGEXP CONCAT('(^|,)', p.ID, '(,|$)')
            LEFT JOIN role r ON p.System_Role = r.id
            WHERE ra.objectroleid = ?
            AND p.Deleted_date IS NULL
            AND (
                -- Super Admins: always included (all spellings: Super Admin, SuperAdmin, etc.)
                (r.primaryname IS NOT NULL AND REPLACE(REPLACE(REPLACE(LOWER(TRIM(r.primaryname)), ' ', ''), '-', ''), '_', '') IN ('superadmin', 'suberadmin'))
                OR
                -- Admins: must have segment access OR be segment admin
                (r.primaryname IS NOT NULL AND REPLACE(REPLACE(REPLACE(LOWER(TRIM(r.primaryname)), ' ', ''), '-', ''), '_', '') = 'admin'
                 AND (
                     EXISTS (
                         SELECT 1 FROM v_user_accessible_segments vas
                         WHERE vas.user_id = p.ID AND vas.segment_id = ?
                     )
                     OR
                     EXISTS (
                         SELECT 1 FROM segment_x_identity sxi
                         JOIN object_reference orr ON sxi.Object_Ref_ID = orr.ID
                         JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                         WHERE sxi.Segment_ID = ?
                         AND orr.Object_ID = p.ID
                         AND sot.Type = 'People'
                         AND sxi.Role = 'admin'
                         AND sxi.Deleted_At IS NULL
                     )
                 ))
                OR
                -- Web Users: must have segment access
                (r.primaryname IS NULL OR (REPLACE(REPLACE(REPLACE(LOWER(TRIM(COALESCE(r.primaryname, ''))), ' ', ''), '-', ''), '_', '') NOT IN ('superadmin', 'suberadmin', 'admin'))
                 AND EXISTS (
                     SELECT 1 FROM v_user_accessible_segments vas
                     WHERE vas.user_id = p.ID AND vas.segment_id = ?
                 ))
            )
            ORDER BY p.First_Name, p.Last_Name
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, roleId);
            pstmt.setInt(2, segmentId);
            pstmt.setInt(3, segmentId);
            pstmt.setInt(4, segmentId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("PeopleID", rs.getInt("PeopleID"));
                    user.put("Name", rs.getString("Name"));
                    users.add(user);
                }
            }
        }

        // Fallback: segment filter excludes users without segment access, which can leave the
        // dropdown empty even when role_assignment has members. If the role has assignments
        // but the filtered list is empty, return the full role-assigned list so the UI can
        // show usernames; save-time validation still enforces rules where applicable.
        if (users.isEmpty()) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (DefaultStakeholderUtil.hasRoleAssignment(conn, roleId)) {
                    List<Integer> assigned = DefaultStakeholderUtil.getAssignedUserIds(conn, roleId);
                    if (!assigned.isEmpty()) {
                        List<Map<String, Object>> unfiltered = getAllUsersByRole(roleId);
                        if (!unfiltered.isEmpty()) {
                            log.info("Stakeholder lookup: segment filter returned no users for roleId={} objectId={} type={}; using full role list ({} users).",
                                    roleId, objectId, objectType, unfiltered.size());
                            return unfiltered;
                        }
                        // REGEXP join can miss IDs if users JSON format differs; load by parsed IDs
                        List<Map<String, Object>> byIds = loadPeopleByIds(conn, assigned);
                        if (!byIds.isEmpty()) {
                            log.info("Stakeholder lookup: loaded {} users for roleId={} via role_assignment user IDs (segment filter empty, REGEXP join empty).",
                                    byIds.size(), roleId);
                            return byIds;
                        }
                    }
                }
            }
        }

        return users;
    }

    /**
     * Load people rows by primary key list (fallback when role_assignment.users does not match REGEXP).
     */
    private static List<Map<String, Object>> loadPeopleByIds(Connection conn, List<Integer> peopleIds) throws SQLException {
        if (peopleIds == null || peopleIds.isEmpty()) {
            return new ArrayList<>();
        }
        // Deduplicate while preserving order
        List<Integer> unique = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Integer id : peopleIds) {
            if (id != null && seen.add(id)) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return new ArrayList<>();
        }
        String placeholders = unique.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT p.ID AS PeopleID, CONCAT(p.First_Name, ' ', p.Last_Name, ' (', p.Email, ')') AS Name " +
                "FROM people p WHERE p.ID IN (" + placeholders + ") AND p.Deleted_date IS NULL " +
                "ORDER BY p.First_Name, p.Last_Name";
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < unique.size(); i++) {
                ps.setInt(i + 1, unique.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("PeopleID", rs.getInt("PeopleID"));
                    user.put("Name", rs.getString("Name"));
                    out.add(user);
                }
            }
        }
        return out;
    }
    
    /**
     * Get all users by role (for Enterprise or unassigned segments)
     * @param roleId The role ID
     * @return List of user maps with PeopleID and Name
     * @throws SQLException if database error occurs
     */
    private static List<Map<String, Object>> getAllUsersByRole(int roleId) throws SQLException {
        List<Map<String, Object>> users = new ArrayList<>();

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

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, roleId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("PeopleID", rs.getInt("PeopleID"));
                    user.put("Name", rs.getString("Name"));
                    users.add(user);
                }
            }

            // If REGEXP join returns nothing but role_assignment has parsed user IDs, load by ID
            if (users.isEmpty() && DefaultStakeholderUtil.hasRoleAssignment(conn, roleId)) {
                List<Integer> assigned = DefaultStakeholderUtil.getAssignedUserIds(conn, roleId);
                if (!assigned.isEmpty()) {
                    users = loadPeopleByIds(conn, assigned);
                }
            }
        }

        return users;
    }
}

