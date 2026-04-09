package com.example.budg_v2.service;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

/**
 * Service for managing segment assignments to facet objects
 * 
 * Compatible with project database structure (PascalCase columns)
 */
public class ObjectSegmentService {

    private static int getOrCreateObjectTypeId(Connection conn, String objectType) throws SQLException {
        String findSql = "SELECT ID FROM segment_object_type WHERE Type = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, objectType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }

        // Create if missing (keeps v_user_accessible_segments working)
        String insertSql = "INSERT INTO segment_object_type (Type) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, objectType);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLIntegrityConstraintViolationException dup) {
            // Race: another request created it; re-select.
        }

        // Re-select in case of race or if generated keys unavailable
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, objectType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("ID");
            }
        }

        throw new SQLException("Failed to get or create segment_object_type for Type=" + objectType);
    }

    private static int getOrCreateObjectReferenceId(Connection conn, long objectId, int objectTypeId) throws SQLException {
        String findSql = "SELECT ID FROM object_reference WHERE Object_ID = ? AND Object_Type_ID = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setLong(1, objectId);
            ps.setInt(2, objectTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("ID");
            }
        }

        String insertSql = "INSERT INTO object_reference (Object_ID, Object_Type_ID) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, objectId);
            ps.setInt(2, objectTypeId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLIntegrityConstraintViolationException dup) {
            // Race: another request created it; re-select.
        }

        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setLong(1, objectId);
            ps.setInt(2, objectTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("ID");
            }
        }

        throw new SQLException("Failed to get or create object_reference for Object_ID=" + objectId + ", Object_Type_ID=" + objectTypeId);
    }
    
    /**
     * Assign any facet object to a segment
     * 
     * @param objectId The ID of the object (Dataset.ID, System.id, etc.)
     * @param objectType The type name ('Dataset', 'System', 'Policy', etc.)
     * @param segmentId The segment ID to assign to
     * @param userId The user performing the assignment
     * @throws SQLException if database error occurs
     */
    public static void assignObjectToSegment(Long objectId, String objectType, Long segmentId, int userId) throws SQLException {
        if (objectId == null || objectType == null || objectType.isBlank() || segmentId == null) {
            throw new IllegalArgumentException("objectId, objectType, and segmentId are required");
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                assignObjectToSegment(conn, objectId, objectType, segmentId, userId);
                conn.commit();
                //system.out.println("✅ Assigned " + objectType + " #" + objectId + " to Segment #" + segmentId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    
    /**
     * Assign any facet object to a segment using an existing connection
     * (for use within an existing transaction)
     * 
     * @param conn The database connection to use
     * @param objectId The ID of the object (Dataset.ID, System.id, etc.)
     * @param objectType The type name ('Dataset', 'System', 'Policy', etc.)
     * @param segmentId The segment ID to assign to
     * @param userId The user performing the assignment
     * @throws SQLException if database error occurs
     */
    public static void assignObjectToSegment(Connection conn, Long objectId, String objectType, Long segmentId, int userId) throws SQLException {
        if (objectId == null || objectType == null || objectType.isBlank() || segmentId == null) {
            throw new IllegalArgumentException("objectId, objectType, and segmentId are required");
        }
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }

        Long currentSegmentId = getObjectSegment(objectId, objectType);
        if (currentSegmentId != null && currentSegmentId > 0 && !currentSegmentId.equals(segmentId)) {
            SegmentValidationService.ValidationResult validationResult =
                new SegmentValidationService().validateSegmentMove(objectId.intValue(), segmentId.intValue(), objectType, null);
            if (!validationResult.isValid) {
                throw new SQLException(validationResult.message);
            }
        }

        int objectTypeId = getOrCreateObjectTypeId(conn, objectType);
        int objectRefId = getOrCreateObjectReferenceId(conn, objectId, objectTypeId);

        // Soft delete existing assignment(s) for this object in other segments
        String softDeleteSql = """
            UPDATE segment_x_resource
            SET Deleted_At = NOW(),
                Last_UpdatedBy = ?,
                Last_Updated_At = NOW()
            WHERE Object_Reference_ID = ?
              AND Deleted_At IS NULL
        """;
        try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
            ps.setInt(1, userId);
            ps.setInt(2, objectRefId);
            ps.executeUpdate();
        }

        // Reactivate existing row for (Segment_ID, Object_Reference_ID) if present (avoids duplicate key)
        String selectSql = """
            SELECT Segment_ID FROM segment_x_resource
            WHERE Segment_ID = ? AND Object_Reference_ID = ?
            LIMIT 1
        """;
        boolean rowExists = false;
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setLong(1, segmentId);
            ps.setInt(2, objectRefId);
            try (ResultSet rs = ps.executeQuery()) {
                rowExists = rs.next();
            }
        }

        if (rowExists) {
            String reactivateSql = """
                UPDATE segment_x_resource
                SET Deleted_At = NULL,
                    Last_UpdatedBy = ?,
                    Last_Updated_At = NOW()
                WHERE Segment_ID = ? AND Object_Reference_ID = ?
                """;
            try (PreparedStatement ps = conn.prepareStatement(reactivateSql)) {
                ps.setInt(1, userId);
                ps.setLong(2, segmentId);
                ps.setInt(3, objectRefId);
                ps.executeUpdate();
            }
        } else {
            String insertSql = """
                INSERT INTO segment_x_resource (
                    Segment_ID,
                    Object_Reference_ID,
                    Created_By,
                    Created_At,
                    Last_UpdatedBy,
                    Last_Updated_At
                ) VALUES (?, ?, ?, NOW(), ?, NOW())
            """;
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setLong(1, segmentId);
                ps.setInt(2, objectRefId);
                ps.setInt(3, userId);
                ps.setInt(4, userId);
                ps.executeUpdate();
            }
        }
    }
    
    /**
     * Get the segment ID for any facet object
     * 
     * @param objectId The ID of the object
     * @param objectType The type name ('Dataset', 'System', etc.)
     * @return The segment ID (defaults to 1 = Enterprise if not assigned)
     * @throws SQLException if database error occurs
     */
    public static Long getObjectSegment(Long objectId, String objectType) throws SQLException {
        if (objectId == null || objectType == null || objectType.isBlank()) {
            return 1L;
        }

        String sql = """
            SELECT COALESCE((
                SELECT sxr.Segment_ID
                FROM segment_x_resource sxr
                JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                WHERE orr.Object_ID = ?
                  AND sot.Type = ?
                  AND sxr.Deleted_At IS NULL
                LIMIT 1
            ), 1) AS segment_id
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, objectId);
            ps.setString(2, objectType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("segment_id");
            }
        }

        return 1L;
    }
    
    /**
     * Get object reference ID for a facet object
     * Creates one if it doesn't exist
     * 
     * @param objectId The ID of the object
     * @param objectType The type name
     * @return The object_reference.ID
     * @throws SQLException if database error occurs
     */
    public static Long getOrCreateObjectReference(Long objectId, String objectType) throws SQLException {
        if (objectId == null || objectType == null || objectType.isBlank()) {
            throw new IllegalArgumentException("objectId and objectType are required");
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            int objectTypeId = getOrCreateObjectTypeId(conn, objectType);
            int objectRefId = getOrCreateObjectReferenceId(conn, objectId, objectTypeId);
            return (long) objectRefId;
        }
    }
    
    /**
     * Unassign object from its current segment
     * (Soft deletes the segment_x_resource entry)
     * 
     * @param objectId The ID of the object
     * @param objectType The type name
     * @param userId The user performing the unassignment
     * @throws SQLException if database error occurs
     */
    public static void unassignObjectFromSegment(Long objectId, String objectType, int userId) throws SQLException {
        String sql = """
            UPDATE segment_x_resource sxr
            JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
            SET sxr.Deleted_At = NOW(),
                sxr.Last_UpdatedBy = ?,
                sxr.Last_Updated_At = NOW()
            WHERE orr.Object_ID = ?
              AND sot.Type = ?
              AND sxr.Deleted_At IS NULL
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            pstmt.setLong(2, objectId);
            pstmt.setString(3, objectType);
            
            pstmt.executeUpdate();
        }
    }
    
    /**
     * Move object from one segment to another
     * 
     * @param objectId The ID of the object
     * @param objectType The type name
     * @param targetSegmentId The target segment ID
     * @param userId The user performing the move
     * @throws SQLException if database error occurs
     */
    public static void moveObjectToSegment(Long objectId, String objectType, Long targetSegmentId, int userId) throws SQLException {
        // This internally soft-deletes old assignment and creates new one
        assignObjectToSegment(objectId, objectType, targetSegmentId, userId);
    }
    
    /**
     * Get all objects in a specific segment
     * 
     * @param segmentId The segment ID
     * @param objectType The type name ('Dataset', 'System', etc.)
     * @return List of object IDs
     * @throws SQLException if database error occurs
     */
    public static List<Long> getObjectsInSegment(Long segmentId, String objectType) throws SQLException {
        String sql = """
            SELECT orr.Object_ID
            FROM segment_x_resource sxr
            JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
            WHERE sxr.Segment_ID = ?
              AND sot.Type = ?
              AND sxr.Deleted_At IS NULL
            ORDER BY orr.Object_ID
        """;
        
        List<Long> objectIds = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, segmentId);
            pstmt.setString(2, objectType);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    objectIds.add(rs.getLong("Object_ID"));
                }
            }
        }
        
        return objectIds;
    }
    
    /**
     * Count objects in a segment by type
     * 
     * @param segmentId The segment ID
     * @return Map of object type to count
     * @throws SQLException if database error occurs
     */
    public static Map<String, Integer> countObjectsByType(Long segmentId) throws SQLException {
        String sql = """
            SELECT sot.Type, COUNT(*) as count
            FROM segment_x_resource sxr
            JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
            WHERE sxr.Segment_ID = ?
              AND sxr.Deleted_At IS NULL
            GROUP BY sot.Type
            ORDER BY sot.Type
        """;
        
        Map<String, Integer> counts = new HashMap<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, segmentId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString("Type"), rs.getInt("count"));
                }
            }
        }
        
        return counts;
    }
}

