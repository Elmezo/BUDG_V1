package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.RegulationXRegulator;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegulationXRegulatorDAO {
    
    public List<Map<String, Object>> getByRegulationId(int regulationId) throws SQLException {
        String sql = """
            SELECT 
                rxr.ID as relationId,
                rxr.Regulator_Reg_Ref as regulatorRegRef,
                rxr.RegulationID as regulationId,
                rxr.RegulatorID as regulatorId,
                rxr.RelationType as relationTypeId,
                r.PrimaryName as regulatorName,
                r.ShortName as regulatorShortName,
                r.Description as regulatorDescription,
                rt.primaryName as relationType,
                rt.Description as relationTypeDescription
            FROM regulation_x_regulator rxr
            LEFT JOIN regulator r ON rxr.RegulatorID = r.ID
            LEFT JOIN regulation_x_regulator_relationtype rt ON rxr.RelationType = rt.ID
            WHERE rxr.RegulationID = ? 
            AND (r.DeletedDatetime IS NULL OR r.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY r.PrimaryName ASC
        """;
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, regulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("relationId", rs.getInt("relationId"));
                    row.put("regulatorRegRef", rs.getString("regulatorRegRef"));
                    row.put("regulationId", rs.getInt("regulationId"));
                    row.put("regulatorId", rs.getInt("regulatorId"));
                    row.put("relationTypeId", rs.getInt("relationTypeId"));
                    row.put("regulatorName", rs.getString("regulatorName"));
                    row.put("regulatorShortName", rs.getString("regulatorShortName"));
                    row.put("regulatorDescription", rs.getString("regulatorDescription"));
                    row.put("relationType", rs.getString("relationType"));
                    row.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    results.add(row);
                }
            }
        }
        
        return results;
    }
    
    /**
     * Get regulators for a regulation including inherited regulators from parent regulations
     */
    public List<Map<String, Object>> getByRegulationIdWithInheritance(int regulationId) throws SQLException {
        String sql = """
            WITH RECURSIVE regulation_hierarchy AS (
                -- Base case: current regulation
                SELECT ID, Parent_ID, primaryName, 0 as level
                FROM regulation 
                WHERE ID = ? AND DeletedDatetime IS NULL
                
                UNION ALL
                
                -- Recursive case: parent regulations
                SELECT r.ID, r.Parent_ID, r.primaryName, rh.level + 1
                FROM regulation r
                INNER JOIN regulation_hierarchy rh ON r.ID = rh.Parent_ID
                WHERE rh.level < 10 AND r.DeletedDatetime IS NULL  -- Prevent infinite recursion
            )
            SELECT 
                rxr.ID as relationId,
                rxr.Regulator_Reg_Ref as regulatorRegRef,
                rxr.RegulationID as regulationId,
                rxr.RegulatorID as regulatorId,
                rxr.RelationType as relationTypeId,
                r.PrimaryName as regulatorName,
                r.ShortName as regulatorShortName,
                r.Description as regulatorDescription,
                rt.primaryName as relationType,
                rt.Description as relationTypeDescription,
                rh.level as inheritanceLevel,
                rh.primaryName as sourceRegulationName
            FROM regulation_hierarchy rh
            INNER JOIN regulation_x_regulator rxr ON rh.ID = rxr.RegulationID
            LEFT JOIN regulator r ON rxr.RegulatorID = r.ID
            LEFT JOIN regulation_x_regulator_relationtype rt ON rxr.RelationType = rt.ID
            WHERE (r.DeletedDatetime IS NULL OR r.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY rh.level ASC, r.PrimaryName ASC
        """;
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, regulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("relationId", rs.getInt("relationId"));
                    row.put("regulatorRegRef", rs.getString("regulatorRegRef"));
                    row.put("regulationId", rs.getInt("regulationId"));
                    row.put("regulatorId", rs.getInt("regulatorId"));
                    row.put("relationTypeId", rs.getInt("relationTypeId"));
                    row.put("regulatorName", rs.getString("regulatorName"));
                    row.put("regulatorShortName", rs.getString("regulatorShortName"));
                    row.put("regulatorDescription", rs.getString("regulatorDescription"));
                    row.put("relationType", rs.getString("relationType"));
                    row.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    row.put("inheritanceLevel", rs.getInt("inheritanceLevel"));
                    row.put("sourceRegulationName", rs.getString("sourceRegulationName"));
                    results.add(row);
                }
            }
        }
        
        return results;
    }
    
    private static final String INSERT_REGULATION_X_REGULATOR = 
        "INSERT INTO regulation_x_regulator (RegulationID, RegulatorID, RelationType, LastUpdateDatetime, LastUpdate_UserID) " +
        "VALUES (?, ?, ?, NULL, ?)";
    
    private static final String UPDATE_REGULATION_X_REGULATOR = 
        "UPDATE regulation_x_regulator SET RegulatorID = ?, RelationType = ?, LastUpdateDatetime = NOW(), LastUpdate_UserID = ? WHERE ID = ?";
    
    private static final String DELETE_REGULATION_X_REGULATOR = 
        "DELETE FROM regulation_x_regulator WHERE ID = ?";
    
    private static final String SELECT_BY_ID = 
        "SELECT * FROM regulation_x_regulator WHERE ID = ?";

    public int createRegulationXRegulator(RegulationXRegulator regulator) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_REGULATION_X_REGULATOR, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, regulator.getRegulationId());
            stmt.setInt(2, regulator.getRegulatorId());
            stmt.setInt(3, regulator.getRelationType());
            stmt.setInt(4, regulator.getLastUpdateUserId() != null ? regulator.getLastUpdateUserId() : 1);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return -1;
    }

    public boolean updateRegulationXRegulator(RegulationXRegulator regulator) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_REGULATION_X_REGULATOR)) {
            
            stmt.setInt(1, regulator.getRegulatorId());
            stmt.setInt(2, regulator.getRelationType());
            stmt.setInt(3, regulator.getLastUpdateUserId() != null ? regulator.getLastUpdateUserId() : 1);
            stmt.setInt(4, regulator.getId());
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return false;
    }

    public boolean deleteRegulationXRegulator(int id) {
        // When deleting a regulator from a regulation, we must also delete its geographies
        // This prevents orphaned geography relationships
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false); // Start transaction
            
            try {
                // First, delete dependent geography relationships from regulation_x_regulator_x_geography
                String deleteDependentSql = "DELETE FROM regulation_x_regulator_x_geography WHERE Regulation_X_Regulator_ID = ?";
                try (PreparedStatement dependentStmt = conn.prepareStatement(deleteDependentSql)) {
                    dependentStmt.setInt(1, id);
                    dependentStmt.executeUpdate();
                }
                
                // Then delete the main regulator relationship
                try (PreparedStatement stmt = conn.prepareStatement(DELETE_REGULATION_X_REGULATOR)) {
                    stmt.setInt(1, id);
                    int rowsAffected = stmt.executeUpdate();
                    
                    if (rowsAffected > 0) {
                        conn.commit(); // Commit transaction
                        //system.out.println("Successfully deleted regulation_x_regulator ID: " + id);
                        return true;
                    } else {
                        conn.rollback(); // Rollback if no rows affected
                        return false;
                    }
                }
                
            } catch (SQLException e) {
                conn.rollback(); // Rollback on error
                //system.out.println("Error deleting regulation_x_regulator, rolling back transaction: " + e.getMessage());
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return false;
    }

    public RegulationXRegulator getById(int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new RegulationXRegulator(
                        rs.getInt("ID"),
                        rs.getInt("RegulationID"),
                        rs.getInt("RegulatorID"),
                        rs.getInt("RelationType"),
                        null,                              // Description not in table
                        null,                              // CreateDatetime not in table
                        rs.getString("LastUpdateDatetime"),
                        rs.getInt("LastUpdate_UserID")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }

    // ===== AUDIT TRACKING METHODS =====

    /**
     * إنشاء audit records عند ربط regulator بـ regulation
     * يتم استدعاء هذا method بعد إنشاء العلاقة بنجاح
     */
    public void createRegulatorLinkAuditRecords(int regulationId, int regulatorId, Integer relationTypeId, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // إعداد audit statement
            String auditSql = """
                INSERT INTO regulation_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Regulator Name
            String regulatorName = getRegulatorName(regulatorId);
            if (regulatorName != null) {
                createNewAuditRecord(conn, auditStmt, regulationId, "Regulator", "Links", "Added", "Regulator Name", regulatorName, userName);
            }
            
            // Relation Type
            if (relationTypeId != null) {
                String relationTypeName = getRelationTypeName(relationTypeId);
                if (relationTypeName != null) {
                    createNewAuditRecord(conn, auditStmt, regulationId, "Regulator", "Links", "Added", "Relation Type", relationTypeName, userName);
                }
            }
            
            conn.commit();
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (auditStmt != null) auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * إنشاء audit records عند إلغاء ربط regulator من regulation
     * يتم استدعاء هذا method قبل حذف العلاقة
     */
    public void createRegulatorUnlinkAuditRecord(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الحصول على بيانات العلاقة قبل الحذف
            RegulationXRegulator relationship = getById(id);
            if (relationship == null) {
                throw new SQLException("Regulator relationship not found with ID: " + id);
            }
            
            // إعداد audit statement
            String auditSql = """
                INSERT INTO regulation_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, NULL, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Regulator Name
            String regulatorName = getRegulatorName(relationship.getRegulatorId());
            if (regulatorName != null) {
                createRemovedAuditRecord(conn, auditStmt, relationship.getRegulationId(), "Regulator", "Links", "Removed", "Regulator Name", regulatorName, userName);
            }
            
            // Relation Type
            if (relationship.getRelationType() != null) {
                String relationTypeName = getRelationTypeName(relationship.getRelationType());
                if (relationTypeName != null) {
                    createRemovedAuditRecord(conn, auditStmt, relationship.getRegulationId(), "Regulator", "Links", "Removed", "Relation Type", relationTypeName, userName);
                }
            }
            
            conn.commit();
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (auditStmt != null) auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * إنشاء audit records عند تحديث علاقة regulator بـ regulation
     * يتم استدعاء هذا method بعد التحديث بنجاح
     */
    public void createRegulatorUpdateAuditRecords(int id, RegulationXRegulator oldRelationship, RegulationXRegulator newRelationship, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // إعداد audit statement
            String auditSql = """
                INSERT INTO regulation_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Check if Regulator changed
            if (!isEqual(oldRelationship.getRegulatorId(), newRelationship.getRegulatorId())) {
                String oldRegulatorName = getRegulatorName(oldRelationship.getRegulatorId());
                String newRegulatorName = getRegulatorName(newRelationship.getRegulatorId());
                createUpdateAuditRecord(conn, auditStmt, newRelationship.getRegulationId(), "Regulator", "Links", 
                    "Updated", "Regulator Name", oldRegulatorName, newRegulatorName, userName);
            }
            
            // Check if Relation Type changed
            if (!isEqual(oldRelationship.getRelationType(), newRelationship.getRelationType())) {
                String oldRelationTypeName = oldRelationship.getRelationType() != null ? getRelationTypeName(oldRelationship.getRelationType()) : null;
                String newRelationTypeName = newRelationship.getRelationType() != null ? getRelationTypeName(newRelationship.getRelationType()) : null;
                createUpdateAuditRecord(conn, auditStmt, newRelationship.getRegulationId(), "Regulator", "Links", 
                    "Updated", "Relation Type", oldRelationTypeName, newRelationTypeName, userName);
            }
            
            conn.commit();
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (auditStmt != null) auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * إنشاء audit record جديد (للإضافة)
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int regulationId, 
                                    String object, String event, String updateType, String field, 
                                    String value, String userName) throws SQLException {
        auditStmt.setInt(1, regulationId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, value);
        auditStmt.setString(7, userName);
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * إنشاء audit record للحذف
     */
    private int createRemovedAuditRecord(Connection conn, PreparedStatement auditStmt, int regulationId,
                                        String object, String event, String updateType, String field,
                                        String value, String userName) throws SQLException {
        auditStmt.setInt(1, regulationId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, value);
        auditStmt.setString(7, userName);
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * إنشاء audit record للتحديث
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, int regulationId,
                                       String object, String event, String updateType, String field,
                                       String oldValue, String newValue, String userName) throws SQLException {
        auditStmt.setInt(1, regulationId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, oldValue);
        auditStmt.setString(7, newValue);
        auditStmt.setString(8, userName);
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    // ===== HELPER METHODS =====

    /**
     * الحصول على اسم الـ regulator
     */
    private String getRegulatorName(int regulatorId) throws SQLException {
        String sql = "SELECT PrimaryName FROM regulator WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulatorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    /**
     * الحصول على اسم نوع العلاقة
     */
    private String getRelationTypeName(int relationTypeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM regulation_x_regulator_relationtype WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    /**
     * مقارنة قيمتين (يتعامل مع null)
     */
    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
