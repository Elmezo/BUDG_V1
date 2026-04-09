package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Regulator;
import com.example.budg_v2.service.SegmentAccessService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RegulatorDAO {

    private static final String SELECT_ALL = "SELECT * FROM regulator WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String SELECT_BY_ID = "SELECT r.ID, r.PrimaryName, r.ShortName, r.Description, " +
            "r.CreateDatetime, r.LastUpdateDatetime, r.DeletedDatetime, r.LastUpdate_UserID, " +
            "CONCAT(ub.First_Name, ' ', ub.Last_Name) AS lastUpdatedByName " +
            "FROM regulator r " +
            "LEFT JOIN people ub ON ub.ID = r.LastUpdate_UserID " +
            "WHERE r.ID = ? AND r.DeletedDatetime IS NULL";
    private static final String SELECT_FOR_DROPDOWN = "SELECT ID, PrimaryName, ShortName, Description FROM regulator WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String INSERT = "INSERT INTO regulator (ID, PrimaryName, ShortName, Description, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) VALUES ((SELECT COALESCE(MAX(ID), 0) + 1 FROM regulator r), ?, ?, ?, NOW(), NOW(), ?)";
    private static final String UPDATE = "UPDATE regulator SET PrimaryName = ?, ShortName = ?, Description = ?, LastUpdateDatetime = NOW(), LastUpdate_UserID = ? WHERE ID = ?";
    private static final String SOFT_DELETE = "UPDATE regulator SET DeletedDatetime = NOW() WHERE ID = ?";
    private static final String SEARCH = "SELECT * FROM regulator WHERE (PrimaryName LIKE ? OR ShortName LIKE ? OR Description LIKE ?) AND DeletedDatetime IS NULL ORDER BY PrimaryName";

    public List<Regulator> getAllRegulators() throws SQLException {
        List<Regulator> regulators = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                regulators.add(mapResultSetToRegulator(rs));
            }
        }
        return regulators;
    }

    /**
     * Get all regulators filtered by user's segment access
     */
    public List<Regulator> getAllRegulators(int userId) throws SQLException {
        List<Regulator> allRegulators = getAllRegulators();
        if (allRegulators.isEmpty()) {
            return allRegulators;
        }
        
        // Get accessible regulator IDs for this user (filtered by selected segments)
        List<Integer> allIds = allRegulators.stream().map(Regulator::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Regulator", allIds);
        
        // Filter to only accessible regulators
        return allRegulators.stream()
                .filter(r -> accessibleIds.contains(r.getId()))
                .collect(Collectors.toList());
    }

    public Regulator getRegulatorById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRegulator(rs);
                }
            }
        }
        return null;
    }

    public Regulator getRegulatorByPrimaryName(String primaryName) throws SQLException {
        if (primaryName == null || primaryName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM regulator WHERE PrimaryName = ? AND DeletedDatetime IS NULL LIMIT 1")) {
            pstmt.setString(1, primaryName.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRegulator(rs);
                }
            }
        }
        return null;
    }

    public List<Regulator> getRegulatorsForDropdown() throws SQLException {
        List<Regulator> regulators = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_FOR_DROPDOWN);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Regulator regulator = new Regulator();
                regulator.setId(rs.getInt("ID"));
                regulator.setPrimaryName(rs.getString("PrimaryName"));
                regulator.setShortName(rs.getString("ShortName"));
                regulator.setDescription(rs.getString("Description"));
                regulators.add(regulator);
            }
        }
        return regulators;
    }

    public List<Regulator> getRegulatorsForDropdown(int userId) throws SQLException {
        List<Regulator> regulators = getRegulatorsForDropdown();
        if (regulators.isEmpty()) {
            return regulators;
        }

        List<Integer> allIds = regulators.stream().map(Regulator::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Regulator", allIds);

        return regulators.stream()
                .filter(regulator -> accessibleIds.contains(regulator.getId()))
                .collect(Collectors.toList());
    }

    public Regulator createRegulator(Regulator regulator) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, regulator.getPrimaryName());
            pstmt.setString(2, regulator.getShortName());
            pstmt.setString(3, regulator.getDescription());
            pstmt.setObject(4, regulator.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating regulator failed, no rows affected.");
            }

            // Since we're using a subquery for ID generation, we need to get the ID differently
            // Find the newly created regulator by its unique combination
            try (PreparedStatement idStmt = conn.prepareStatement(
                    "SELECT ID FROM regulator WHERE PrimaryName = ? AND Description = ? AND CreateDatetime >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) ORDER BY ID DESC LIMIT 1")) {
                idStmt.setString(1, regulator.getPrimaryName());
                idStmt.setString(2, regulator.getDescription());
                try (ResultSet idRs = idStmt.executeQuery()) {
                    if (idRs.next()) {
                        int generatedId = idRs.getInt(1);
                        regulator.setId(generatedId);
                        return regulator;
                    } else {
                        throw new SQLException("Creating regulator failed, no ID obtained.");
                    }
                }
            }
        }
    }

    public boolean updateRegulator(Regulator regulator) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, regulator.getPrimaryName());
            pstmt.setString(2, regulator.getShortName());
            pstmt.setString(3, regulator.getDescription());
            pstmt.setObject(4, regulator.getLastUpdateUserId());
            pstmt.setInt(5, regulator.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteRegulator(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    public List<Regulator> searchRegulators(String searchTerm) throws SQLException {
        List<Regulator> regulators = new ArrayList<>();
        String searchPattern = "%" + searchTerm + "%";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    regulators.add(mapResultSetToRegulator(rs));
                }
            }
        }
        return regulators;
    }

    public List<Regulator> searchRegulators(String searchTerm, int userId) throws SQLException {
        List<Regulator> regulators = searchRegulators(searchTerm);
        if (regulators.isEmpty()) {
            return regulators;
        }

        List<Integer> allIds = regulators.stream().map(Regulator::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Regulator", allIds);

        return regulators.stream()
                .filter(regulator -> accessibleIds.contains(regulator.getId()))
                .collect(Collectors.toList());
    }

    private Regulator mapResultSetToRegulator(ResultSet rs) throws SQLException {
        Regulator regulator = new Regulator();
        regulator.setId(rs.getInt("ID"));
        regulator.setPrimaryName(rs.getString("PrimaryName"));
        regulator.setShortName(rs.getString("ShortName"));
        regulator.setDescription(rs.getString("Description"));
        regulator.setCreateDateTime(rs.getTimestamp("CreateDatetime"));
        regulator.setLastUpdateDateTime(rs.getTimestamp("LastUpdateDatetime"));
        regulator.setDeletedDateTime(rs.getTimestamp("DeletedDatetime"));
        regulator.setLastUpdateUserId(rs.getObject("LastUpdate_UserID", Integer.class));
        
        // Get lastUpdatedByName if available (from JOIN in SELECT_BY_ID)
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
        regulator.setLastUpdatedByName(lastUpdatedByName);
        
        return regulator;
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int regulatorId, String object, String event, String updateType, String field, String value, String userName) throws SQLException {
        auditStmt.setInt(1, regulatorId);        // id
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
     * إنشاء audit records للمنظم الجديد
     * يتم استدعاء هذا method بعد إنشاء المنظم بنجاح
     */
    public void createRegulatorAuditRecords(int regulatorId, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. الحصول على بيانات المنظم
            String regulatorDataSql = "SELECT * FROM regulator WHERE ID = ?";
            PreparedStatement regulatorStmt = conn.prepareStatement(regulatorDataSql);
            regulatorStmt.setInt(1, regulatorId);
            ResultSet regulatorRs = regulatorStmt.executeQuery();
            
            if (!regulatorRs.next()) {
                throw new SQLException("Regulator not found with ID: " + regulatorId);
            }

            // 2. إعداد audit statement
            String auditSql = """
                INSERT INTO regulator_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            String primaryName = regulatorRs.getString("PrimaryName");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, regulatorId, "Regulator", "Details", "Added", "Primary Name", primaryName, userName);
            }
            
            // Short Name
            String shortName = regulatorRs.getString("ShortName");
            if (shortName != null && !shortName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, regulatorId, "Regulator", "Details", "Added", "Short Name", shortName, userName);
            }
            
            // Description
            String description = regulatorRs.getString("Description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, regulatorId, "Regulator", "Details", "Added", "Description", description, userName);
            }
            
            // Created By
            Integer lastUpdateUserId = regulatorRs.getObject("LastUpdate_UserID", Integer.class);
            if (lastUpdateUserId != null) {
                String createdByName = getPersonFullName(lastUpdateUserId);
                if (createdByName != null) {
                    createNewAuditRecord(conn, auditStmt, regulatorId, "Regulator", "Details", "Added", "Created By", createdByName, userName);
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
     * إنشاء سجل في جدول regulator_audit بعد إنشاء المنظم
     * يتم استدعاء هذا method بعد إنشاء المنظم بنجاح
     */
    public void createRegulatorAuditRecord(int regulatorId) throws SQLException {
        String sql = """
            INSERT INTO regulator_audit (
                ID, PrimaryName, ShortName, Description, CreateDatetime, LastUpdateDatetime,
                DeletedDatetime, LastUpdate_UserID, RevType
            )
            SELECT 
                ID, PrimaryName, ShortName, Description, CreateDatetime, LastUpdateDatetime,
                DeletedDatetime, LastUpdate_UserID, 'Added'
            FROM regulator 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulatorId);
            ps.executeUpdate();
        }
    }

    /**
     * تحديث المنظم مع تسجيل audit records
     */
    public boolean updateRegulatorWithAudit(Regulator oldRegulator, Regulator newRegulator, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement updateStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // الخطوة 1: تحديث الجدول الأساسي
            String updateSql = """
                UPDATE regulator 
                SET PrimaryName = ?, ShortName = ?, Description = ?, 
                    LastUpdate_UserID = ?, LastUpdateDatetime = NOW()
                WHERE ID = ?
            """;
            
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setString(1, newRegulator.getPrimaryName());
            updateStmt.setString(2, newRegulator.getShortName());
            updateStmt.setString(3, newRegulator.getDescription());
            updateStmt.setObject(4, newRegulator.getLastUpdateUserId());
            updateStmt.setInt(5, newRegulator.getId());
            
            int affectedRows = updateStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات
                try {
                    createRegulatorUpdateAuditRecords(conn, newRegulator.getId(), oldRegulator, newRegulator, userName);
                    //system.out.println("✅ Regulator update audit records created for ID: " + newRegulator.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating regulator update audit records: " + e.getMessage());
                    e.printStackTrace();
                }

                // الخطوة 3: إنشاء snapshot جديد في regulator_audit
                try {
                    createRegulatorUpdateAuditSnapshot(conn, newRegulator.getId());
                    //system.out.println("✅ RegulatorDAO: regulator_audit update snapshot created for ID: " + newRegulator.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating regulator_audit update snapshot: " + e.getMessage());
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
     * إنشاء audit records عند تحديث المنظم
     */
    public void createRegulatorUpdateAuditRecords(Connection conn, int regulatorId, Regulator oldRegulator, Regulator newRegulator, String userName) throws SQLException {
        //system.out.println("🔍 RegulatorDAO.createRegulatorUpdateAuditRecords - START for ID: " + regulatorId);
        PreparedStatement auditStmt = null;
        
        try {
            String auditSql = """
                INSERT INTO regulator_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldRegulator.getPrimaryName(), newRegulator.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, regulatorId, "Regulator", "Details", 
                    "Updated", "Primary Name", oldRegulator.getPrimaryName(), newRegulator.getPrimaryName(), userName);
            }
            
            // Short Name
            if (!isEqual(oldRegulator.getShortName(), newRegulator.getShortName())) {
                createUpdateAuditRecord(conn, auditStmt, regulatorId, "Regulator", "Details", 
                    "Updated", "Short Name", oldRegulator.getShortName(), newRegulator.getShortName(), userName);
            }
            
            // Description
            if (!isEqual(oldRegulator.getDescription(), newRegulator.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, regulatorId, "Regulator", "Details", 
                    "Updated", "Description", oldRegulator.getDescription(), newRegulator.getDescription(), userName);
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Error in createRegulatorUpdateAuditRecords: " + e.getMessage());
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
            int regulatorId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, regulatorId);
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
     * Method لإنشاء snapshot جديد في regulator_audit عند الـ update
     */
    public void createRegulatorUpdateAuditSnapshot(Connection conn, int regulatorId) throws SQLException {
        //system.out.println("🔍 RegulatorDAO.createRegulatorUpdateAuditSnapshot - Creating update snapshot for ID: " + regulatorId);
        String sql = """
            INSERT INTO regulator_audit (
                ID, PrimaryName, ShortName, Description, CreateDatetime, LastUpdateDatetime,
                DeletedDatetime, LastUpdate_UserID, RevType
            )
            SELECT 
                ID, PrimaryName, ShortName, Description, CreateDatetime, LastUpdateDatetime,
                DeletedDatetime, LastUpdate_UserID, 'Updated'
            FROM regulator 
            WHERE ID = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulatorId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * حذف المنظم مع تسجيل audit records
     */
    public boolean deleteRegulatorWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE regulator SET DeletedDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO regulator_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Regulator");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Regulator");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في regulator_audit
                String snapshotSql = """
                    INSERT INTO regulator_audit (
                        ID, PrimaryName, ShortName, Description, CreateDatetime, LastUpdateDatetime,
                        DeletedDatetime, LastUpdate_UserID, RevType
                    )
                    SELECT 
                        ID, PrimaryName, ShortName, Description, CreateDatetime, LastUpdateDatetime,
                        DeletedDatetime, LastUpdate_UserID, 'Deleted'
                    FROM regulator 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Regulator deleted with audit for ID: " + id);
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

    // Helper methods
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
