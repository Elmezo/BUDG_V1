package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Geography;
import com.example.budg_v2.service.SegmentAccessService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GeographyDAO {

    private static final String SELECT_ALL = "SELECT * FROM geography WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String SELECT_BY_ID = "SELECT g.ID, g.ParentID, g.PrimaryName, g.Description, " +
            "g.CreateDatetime, g.LastUpdateDatetime, g.DeletedDatetime, g.LastUpdate_UserID, " +
            "CONCAT(ub.First_Name, ' ', ub.Last_Name) AS lastUpdatedByName " +
            "FROM geography g " +
            "LEFT JOIN people ub ON ub.ID = g.LastUpdate_UserID " +
            "WHERE g.ID = ? AND g.DeletedDatetime IS NULL";
    private static final String SELECT_FOR_DROPDOWN = "SELECT ID, ParentID, PrimaryName, Description FROM geography WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String SELECT_FOR_PARENT_PICKER = "SELECT ID, ParentID, PrimaryName, Description FROM geography WHERE ID != ? AND DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String INSERT = "INSERT INTO geography (ID, ParentID, PrimaryName, Description, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) VALUES ((SELECT COALESCE(MAX(ID), 0) + 1 FROM geography g), ?, ?, ?, NOW(), NOW(), ?)";
    private static final String UPDATE = "UPDATE geography SET ParentID = ?, PrimaryName = ?, Description = ?, LastUpdateDatetime = NOW(), LastUpdate_UserID = ? WHERE ID = ?";
    private static final String SOFT_DELETE = "UPDATE geography SET DeletedDatetime = NOW() WHERE ID = ?";
    private static final String SEARCH = "SELECT * FROM geography WHERE (PrimaryName LIKE ? OR Description LIKE ?) AND DeletedDatetime IS NULL ORDER BY PrimaryName";

    public List<Geography> getAllGeographies() throws SQLException {
        List<Geography> geographies = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                geographies.add(mapResultSetToGeography(rs));
            }
        }
        return geographies;
    }

    /**
     * Get all geographies filtered by user's segment access
     */
    public List<Geography> getAllGeographies(int userId) throws SQLException {
        List<Geography> allGeographies = getAllGeographies();
        if (allGeographies.isEmpty()) {
            return allGeographies;
        }
        
        // Get accessible geography IDs for this user (filtered by selected segments)
        List<Integer> allIds = allGeographies.stream().map(Geography::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Geography", allIds);
        
        // Filter to only accessible geographies
        return allGeographies.stream()
                .filter(g -> accessibleIds.contains(g.getId()))
                .collect(Collectors.toList());
    }

    public Geography getGeographyById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Geography geography = mapResultSetToGeography(rs);
                    // Debug logging
                    System.out.println("[GeographyDAO] Geography ID: " + id + 
                                     ", LastUpdate_UserID: " + geography.getLastUpdateUserId() + 
                                     ", lastUpdatedByName: " + geography.getLastUpdatedByName());
                    return geography;
                }
            }
        }
        return null;
    }

    public List<Geography> getGeographiesForDropdown() throws SQLException {
        List<Geography> geographies = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_FOR_DROPDOWN);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Geography geography = new Geography();
                geography.setId(rs.getInt("ID"));
                geography.setParentId(rs.getObject("ParentID", Integer.class));
                geography.setPrimaryName(rs.getString("PrimaryName"));
                geography.setDescription(rs.getString("Description"));
                geographies.add(geography);
            }
        }
        return geographies;
    }

    public List<Geography> getGeographiesForDropdown(int userId) throws SQLException {
        List<Geography> geographies = getGeographiesForDropdown();
        if (geographies.isEmpty()) {
            return geographies;
        }

        List<Integer> allIds = geographies.stream().map(Geography::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Geography", allIds);

        return geographies.stream()
                .filter(geography -> accessibleIds.contains(geography.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Get geographies for dropdown filtered by segment (only geographies assigned to the given segment).
     */
    public List<Geography> getGeographiesForDropdownBySegment(java.util.List<Integer> geographyIds) throws SQLException {
        if (geographyIds == null || geographyIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Geography> geographies = new ArrayList<>();
        String placeholders = geographyIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT ID, ParentID, PrimaryName, Description FROM geography WHERE DeletedDatetime IS NULL AND ID IN (" + placeholders + ") ORDER BY PrimaryName";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < geographyIds.size(); i++) {
                pstmt.setInt(i + 1, geographyIds.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Geography geography = new Geography();
                    geography.setId(rs.getInt("ID"));
                    geography.setParentId(rs.getObject("ParentID", Integer.class));
                    geography.setPrimaryName(rs.getString("PrimaryName"));
                    geography.setDescription(rs.getString("Description"));
                    geographies.add(geography);
                }
            }
        }
        return geographies;
    }

    public List<Geography> getGeographiesForParentPicker(int excludeId) throws SQLException {
        List<Geography> geographies = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_FOR_PARENT_PICKER)) {

            pstmt.setInt(1, excludeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Geography geography = new Geography();
                    geography.setId(rs.getInt("ID"));
                    geography.setParentId(rs.getObject("ParentID", Integer.class));
                    geography.setPrimaryName(rs.getString("PrimaryName"));
                    geography.setDescription(rs.getString("Description"));
                    geographies.add(geography);
                }
            }
        }
        return geographies;
    }

    public List<Geography> getGeographiesForParentPicker(int excludeId, int userId) throws SQLException {
        List<Geography> geographies = getGeographiesForParentPicker(excludeId);
        if (geographies.isEmpty()) {
            return geographies;
        }

        List<Integer> allIds = geographies.stream().map(Geography::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Geography", allIds);

        return geographies.stream()
                .filter(geography -> accessibleIds.contains(geography.getId()))
                .collect(Collectors.toList());
    }

    public Geography createGeography(Geography geography) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setObject(1, geography.getParentId());
            pstmt.setString(2, geography.getPrimaryName());
            pstmt.setString(3, geography.getDescription());
            pstmt.setObject(4, geography.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating geography failed, no rows affected.");
            }

            // Since we're using a subquery for ID generation, we need to get the ID differently
            // Find the newly created geography by its unique combination
            try (PreparedStatement idStmt = conn.prepareStatement(
                    "SELECT ID FROM geography WHERE PrimaryName = ? AND Description = ? AND CreateDatetime >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) ORDER BY ID DESC LIMIT 1")) {
                idStmt.setString(1, geography.getPrimaryName());
                idStmt.setString(2, geography.getDescription());
                try (ResultSet idRs = idStmt.executeQuery()) {
                    if (idRs.next()) {
                        int generatedId = idRs.getInt(1);
                        geography.setId(generatedId);
                        return geography;
                    } else {
                        throw new SQLException("Creating geography failed, no ID obtained.");
                    }
                }
            }
        }
    }

    public boolean updateGeography(Geography geography) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setObject(1, geography.getParentId());
            pstmt.setString(2, geography.getPrimaryName());
            pstmt.setString(3, geography.getDescription());
            pstmt.setObject(4, geography.getLastUpdateUserId());
            pstmt.setInt(5, geography.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteGeography(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    public List<Geography> searchGeographies(String searchTerm) throws SQLException {
        List<Geography> geographies = new ArrayList<>();
        String searchPattern = "%" + searchTerm + "%";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    geographies.add(mapResultSetToGeography(rs));
                }
            }
        }
        return geographies;
    }

    public List<Geography> searchGeographies(String searchTerm, int userId) throws SQLException {
        List<Geography> geographies = searchGeographies(searchTerm);
        if (geographies.isEmpty()) {
            return geographies;
        }

        List<Integer> allIds = geographies.stream().map(Geography::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Geography", allIds);

        return geographies.stream()
                .filter(geography -> accessibleIds.contains(geography.getId()))
                .collect(Collectors.toList());
    }

    private Geography mapResultSetToGeography(ResultSet rs) throws SQLException {
        Geography geography = new Geography();
        geography.setId(rs.getInt("ID"));
        geography.setParentId(rs.getObject("ParentID", Integer.class));
        geography.setPrimaryName(rs.getString("PrimaryName"));
        geography.setDescription(rs.getString("Description"));
        geography.setCreateDateTime(rs.getTimestamp("CreateDatetime"));
        geography.setLastUpdateDateTime(rs.getTimestamp("LastUpdateDatetime"));
        geography.setDeletedDateTime(rs.getTimestamp("DeletedDatetime"));
        geography.setLastUpdateUserId(rs.getObject("LastUpdate_UserID", Integer.class));
        
        // Get lastUpdatedByName if available (from JOIN in SELECT_BY_ID)
        // Check if column exists by trying to access it
        String lastUpdatedByName = null;
        try {
            // First try lowercase (as defined in SQL alias)
            lastUpdatedByName = rs.getString("lastUpdatedByName");
        } catch (SQLException e) {
            // Column might not exist in this query (e.g., SELECT_ALL doesn't have JOIN)
            // That's okay, we'll leave it as null
            try {
                // Try other case variations just in case
                lastUpdatedByName = rs.getString("LastUpdatedByName");
            } catch (SQLException e2) {
                try {
                    lastUpdatedByName = rs.getString("LASTUPDATEDBYNAME");
                } catch (SQLException e3) {
                    // Column doesn't exist in this query - that's fine
                    lastUpdatedByName = null;
                }
            }
        }
        
        // Note: lastUpdatedByName can be null even if column exists if:
        // 1. LastUpdate_UserID is null
        // 2. JOIN didn't find matching user in people table
        // 3. User exists but has no First_Name or Last_Name
        geography.setLastUpdatedByName(lastUpdatedByName);
        
        return geography;
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int geographyId, String object, String event, String updateType, String field, String value, String userName) throws SQLException {
        auditStmt.setInt(1, geographyId);        // id
        auditStmt.setString(2, object);          // object
        auditStmt.setString(3, event);           // event
        auditStmt.setString(4, updateType);      // updateType
        auditStmt.setString(5, field);           // field
        auditStmt.setString(6, value);           // to
        auditStmt.setString(7, userName);        // author
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * إنشاء audit records للجغرافيا الجديدة
     * يتم استدعاء هذا method بعد إنشاء الجغرافيا بنجاح
     */
    public void createGeographyAuditRecords(int geographyId, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. الحصول على بيانات الجغرافيا
            String geographyDataSql = "SELECT * FROM geography WHERE ID = ?";
            PreparedStatement geographyStmt = conn.prepareStatement(geographyDataSql);
            geographyStmt.setInt(1, geographyId);
            ResultSet geographyRs = geographyStmt.executeQuery();
            
            if (!geographyRs.next()) {
                throw new SQLException("Geography not found with ID: " + geographyId);
            }

            // 2. إعداد audit statement
            String auditSql = """
                INSERT INTO geography_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            String primaryName = geographyRs.getString("PrimaryName");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, geographyId, "Geography", "Details", "Added", "Primary Name", primaryName, userName);
            }
            
            // Description
            String description = geographyRs.getString("Description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, geographyId, "Geography", "Details", "Added", "Description", description, userName);
            }
            
            // Parent Geography
            Integer parentId = geographyRs.getObject("ParentID", Integer.class);
            if (parentId != null) {
                String parentName = getGeographyName(parentId);
                if (parentName != null) {
                    createNewAuditRecord(conn, auditStmt, geographyId, "Geography", "Details", "Added", "Parent Geography", parentName, userName);
                }
            }
            
            // Created By
            Integer lastUpdateUserId = geographyRs.getObject("LastUpdate_UserID", Integer.class);
            if (lastUpdateUserId != null) {
                String createdByName = getPersonFullName(lastUpdateUserId);
                if (createdByName != null) {
                    createNewAuditRecord(conn, auditStmt, geographyId, "Geography", "Details", "Added", "Created By", createdByName, userName);
                }
            }
            
            conn.commit(); // تأكيد الـ transaction
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback(); // إلغاء الـ transaction في حالة الخطأ
            }
            throw e;
        } finally {
            // تنظيف الموارد
            if (auditStmt != null) auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * إنشاء سجل في جدول geography_audit بعد إنشاء الجغرافيا
     * يتم استدعاء هذا method بعد إنشاء الجغرافيا بنجاح
     */
    public void createGeographyAuditRecord(int geographyId) throws SQLException {
        String sql = """
            INSERT INTO geography_audit (
                ID, ParentID, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                DeletedDatetime, LastUpdate_UserID, RevType
            )
            SELECT 
                ID, ParentID, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                DeletedDatetime, LastUpdate_UserID, 'Added'
            FROM geography 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, geographyId);
            ps.executeUpdate();
        }
    }

    /**
     * تحديث الجغرافيا مع تسجيل audit records
     */
    public boolean updateGeographyWithAudit(Geography oldGeography, Geography newGeography, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement updateStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // الخطوة 1: تحديث الجدول الأساسي
            String updateSql = """
                UPDATE geography 
                SET ParentID = ?, PrimaryName = ?, Description = ?, 
                    LastUpdate_UserID = ?, LastUpdateDatetime = NOW()
                WHERE ID = ?
            """;
            
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setObject(1, newGeography.getParentId());
            updateStmt.setString(2, newGeography.getPrimaryName());
            updateStmt.setString(3, newGeography.getDescription());
            updateStmt.setObject(4, newGeography.getLastUpdateUserId());
            updateStmt.setInt(5, newGeography.getId());
            
            int affectedRows = updateStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات
                try {
                    createGeographyUpdateAuditRecords(conn, newGeography.getId(), oldGeography, newGeography, userName);
                    //system.out.println("✅ Geography update audit records created for ID: " + newGeography.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating geography update audit records: " + e.getMessage());
                    e.printStackTrace();
                }

                // الخطوة 3: إنشاء snapshot جديد في geography_audit
                try {
                    createGeographyUpdateAuditSnapshot(conn, newGeography.getId());
                    //system.out.println("✅ GeographyDAO: geography_audit update snapshot created for ID: " + newGeography.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating geography_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            conn.commit(); // تأكيد الـ transaction
            return affectedRows > 0;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("❌ Error during rollback: " + rollbackEx.getMessage());
                }
            }
            throw e;
        } finally {
            try {
                if (updateStmt != null) updateStmt.close();
            } catch (SQLException e) {
                System.err.println("❌ Error closing statement: " + e.getMessage());
            }
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("❌ Error closing connection: " + e.getMessage());
            }
        }
    }

    /**
     * إنشاء audit records عند تحديث الجغرافيا
     */
    public void createGeographyUpdateAuditRecords(Connection conn, int geographyId, Geography oldGeography, Geography newGeography, String userName) throws SQLException {
        //system.out.println("🔍 GeographyDAO.createGeographyUpdateAuditRecords - START for ID: " + geographyId);
        PreparedStatement auditStmt = null;
        
        try {
            String auditSql = """
                INSERT INTO geography_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldGeography.getPrimaryName(), newGeography.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, geographyId, "Geography", "Details", 
                    "Updated", "Primary Name", oldGeography.getPrimaryName(), newGeography.getPrimaryName(), userName);
            }
            
            // Description
            if (!isEqual(oldGeography.getDescription(), newGeography.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, geographyId, "Geography", "Details", 
                    "Updated", "Description", oldGeography.getDescription(), newGeography.getDescription(), userName);
            }
            
            // Parent Geography
            if (!isEqual(oldGeography.getParentId(), newGeography.getParentId())) {
                String oldParentName = oldGeography.getParentId() != null ? getGeographyName(oldGeography.getParentId()) : null;
                String newParentName = newGeography.getParentId() != null ? getGeographyName(newGeography.getParentId()) : null;
                createUpdateAuditRecord(conn, auditStmt, geographyId, "Geography", "Details", 
                    "Updated", "Parent Geography", oldParentName, newParentName, userName);
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Error in createGeographyUpdateAuditRecords: " + e.getMessage());
            throw e;
        } finally {
            try {
                if (auditStmt != null) auditStmt.close();
            } catch (SQLException e) {
                System.err.println("❌ Error closing audit statement: " + e.getMessage());
            }
        }
    }

    /**
     * Helper method لإنشاء audit record للـ update مع from و to
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, 
            int geographyId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, geographyId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, fromValue);      // from
        auditStmt.setString(7, toValue);        // to
        auditStmt.setString(8, userName);
        
        auditStmt.executeUpdate();
        //system.out.println("    ✓ Update audit record inserted");
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Method لإنشاء snapshot جديد في geography_audit عند الـ update
     */
    public void createGeographyUpdateAuditSnapshot(Connection conn, int geographyId) throws SQLException {
        //system.out.println("🔍 GeographyDAO.createGeographyUpdateAuditSnapshot - Creating update snapshot for ID: " + geographyId);
        String sql = """
            INSERT INTO geography_audit (
                ID, ParentID, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                DeletedDatetime, LastUpdate_UserID, RevType
            )
            SELECT 
                ID, ParentID, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                DeletedDatetime, LastUpdate_UserID, 'Updated'
            FROM geography 
            WHERE ID = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, geographyId);
            ps.executeUpdate();
            //system.out.println("✅ Update snapshot created, rows affected: " + rows);
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * حذف الجغرافيا مع تسجيل audit records
     */
    public boolean deleteGeographyWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE geography SET DeletedDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO geography_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Geography");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Geography");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في geography_audit
                String snapshotSql = """
                    INSERT INTO geography_audit (
                        ID, ParentID, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                        DeletedDatetime, LastUpdate_UserID, RevType
                    )
                    SELECT 
                        ID, ParentID, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                        DeletedDatetime, LastUpdate_UserID, 'Deleted'
                    FROM geography 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Geography deleted with audit for ID: " + id);
            }
            
            conn.commit();
            return affectedRows > 0;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("❌ Error during rollback: " + rollbackEx.getMessage());
                }
            }
            throw e;
        } finally {
            try {
                if (deleteStmt != null) deleteStmt.close();
                if (auditStmt != null) auditStmt.close();
            } catch (SQLException e) {
                System.err.println("❌ Error closing statement: " + e.getMessage());
            }
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("❌ Error closing connection: " + e.getMessage());
            }
        }
    }

    // Helper methods للحصول على الأسماء
    private String getGeographyName(int geographyId) throws SQLException {
        String sql = "SELECT PrimaryName FROM geography WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, geographyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getPersonFullName(int personId) throws SQLException {
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

    /**
     * Helper method للمقارنة بين القيم (يتعامل مع null)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        
        // Handle String comparison (case-insensitive and trim)
        if (obj1 instanceof String && obj2 instanceof String) {
            String str1 = ((String) obj1).trim();
            String str2 = ((String) obj2).trim();
            return str1.equalsIgnoreCase(str2);
        }
        
        return obj1.equals(obj2);
    }
}
