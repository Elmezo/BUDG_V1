package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.RegulationXRegulatoryTheme;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegulationXRegulatoryThemeDAO {
    
    private static final String SELECT_ALL = "SELECT * FROM regulation_x_regulatorytheme ORDER BY ID";
    private static final String SELECT_BY_ID = "SELECT * FROM regulation_x_regulatorytheme WHERE ID = ?";
    private static final String SELECT_BY_REGULATORY_THEME_ID = "SELECT * FROM regulation_x_regulatorytheme WHERE RegulatoryTheme_ID = ? ORDER BY ID";
    private static final String SELECT_BY_REGULATION_ID = "SELECT rxt.*, rt.PrimaryName as RegulatoryThemeName, rt.Description as RegulatoryThemeDescription, " +
            "rt.RefNumber as RegulatoryThemeRefNumber, " +
            "COUNT(rxt2.Regulation_ID) as RegulationCount, " +
            "GROUP_CONCAT(DISTINCT r2.PrimaryName ORDER BY r2.PrimaryName SEPARATOR ', ') as AllRegulations " +
            "FROM regulation_x_regulatorytheme rxt " +
            "JOIN regulatorytheme rt ON rxt.RegulatoryTheme_ID = rt.ID " +
            "LEFT JOIN regulation_x_regulatorytheme rxt2 ON rt.ID = rxt2.RegulatoryTheme_ID " +
            "LEFT JOIN regulation r2 ON rxt2.Regulation_ID = r2.ID " +
            "WHERE rxt.Regulation_ID = ? " +
            "GROUP BY rxt.ID, rt.ID, rt.PrimaryName, rt.Description, rt.RefNumber " +
            "ORDER BY rt.PrimaryName";
    private static final String SELECT_WITH_DETAILS_BY_REGULATORY_THEME_ID = "SELECT rxt.*, r.PrimaryName as RegulationName, r.RefNumber as RegulationRefNumber, " +
            "pr.PrimaryName as ParentRegulationName " +
            "FROM regulation_x_regulatorytheme rxt " +
            "JOIN regulation r ON rxt.Regulation_ID = r.ID " +
            "LEFT JOIN regulation pr ON r.Parent_ID = pr.ID " +
            "WHERE rxt.RegulatoryTheme_ID = ? ORDER BY r.PrimaryName";
    private static final String INSERT = "INSERT INTO regulation_x_regulatorytheme (Regulation_ID, RegulatoryTheme_ID, RelationType, Description, CreateDatetime, LastUpdate_UserID) VALUES (?, ?, ?, ?, NOW(), ?)";
    private static final String UPDATE = "UPDATE regulation_x_regulatorytheme SET Regulation_ID = ?, RelationType = ?, Description = ?, LastUpdateDatetime = NOW(), LastUpdate_UserID = ? WHERE ID = ?";
    private static final String DELETE = "DELETE FROM regulation_x_regulatorytheme WHERE ID = ?";

    public List<RegulationXRegulatoryTheme> getAllRegulationXRegulatoryThemes() throws SQLException {
        List<RegulationXRegulatoryTheme> regulations = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                regulations.add(mapResultSetToRegulationXRegulatoryTheme(rs));
            }
        }
        return regulations;
    }

    public RegulationXRegulatoryTheme getRegulationXRegulatoryThemeById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToRegulationXRegulatoryTheme(rs);
            }
        }
        return null;
    }

    public List<RegulationXRegulatoryTheme> getRegulationsByRegulatoryThemeId(int regulatoryThemeId) throws SQLException {
        List<RegulationXRegulatoryTheme> regulations = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REGULATORY_THEME_ID)) {
            
            pstmt.setInt(1, regulatoryThemeId);
            ResultSet rs = pstmt.executeQuery();
            
                while (rs.next()) {
                regulations.add(mapResultSetToRegulationXRegulatoryTheme(rs));
            }
        }
        return regulations;
    }

    public List<Map<String, Object>> getRegulatoryThemesByRegulationId(int regulationId) throws SQLException {
        List<Map<String, Object>> regulatoryThemes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REGULATION_ID)) {

            pstmt.setInt(1, regulationId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> regulatoryTheme = new HashMap<>();
                regulatoryTheme.put("id", rs.getInt("ID"));
                regulatoryTheme.put("regulationId", rs.getInt("Regulation_ID"));
                regulatoryTheme.put("regulatoryThemeId", rs.getInt("RegulatoryTheme_ID"));
                regulatoryTheme.put("relationType", rs.getInt("RelationType"));
                regulatoryTheme.put("description", rs.getString("Description"));
                regulatoryTheme.put("regulatoryThemeName", rs.getString("RegulatoryThemeName"));
                regulatoryTheme.put("regulatoryThemeDescription", rs.getString("RegulatoryThemeDescription"));
                regulatoryTheme.put("refNumber", rs.getString("RegulatoryThemeRefNumber"));
                regulatoryTheme.put("regulationCount", rs.getInt("RegulationCount"));
                regulatoryTheme.put("allRegulations", rs.getString("AllRegulations"));
                
                Timestamp createDatetime = rs.getTimestamp("CreateDatetime");
                if (createDatetime != null) {
                    regulatoryTheme.put("createDatetime", createDatetime.toLocalDateTime());
                }
                
                Timestamp lastUpdateDatetime = rs.getTimestamp("LastUpdateDatetime");
                if (lastUpdateDatetime != null) {
                    regulatoryTheme.put("lastUpdateDatetime", lastUpdateDatetime.toLocalDateTime());
                }
                
                int lastUpdateUserId = rs.getInt("LastUpdate_UserID");
                if (!rs.wasNull()) {
                    regulatoryTheme.put("lastUpdateUserId", lastUpdateUserId);
                }
                
                regulatoryThemes.add(regulatoryTheme);
            }
        }
        return regulatoryThemes;
    }

    public List<Map<String, Object>> getRegulationsWithDetailsByRegulatoryThemeId(int regulatoryThemeId) throws SQLException {
        List<Map<String, Object>> regulations = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_WITH_DETAILS_BY_REGULATORY_THEME_ID)) {
            
            pstmt.setInt(1, regulatoryThemeId);
            ResultSet rs = pstmt.executeQuery();
            
                while (rs.next()) {
                Map<String, Object> regulation = new HashMap<>();
                regulation.put("id", rs.getInt("ID"));
                regulation.put("regulationId", rs.getInt("Regulation_ID"));
                regulation.put("regulatoryThemeId", rs.getInt("RegulatoryTheme_ID"));
                regulation.put("relationType", rs.getInt("RelationType"));
                regulation.put("description", rs.getString("Description"));
                regulation.put("regulationName", rs.getString("RegulationName"));
                regulation.put("regulationRefNumber", rs.getString("RegulationRefNumber"));
                regulation.put("parentRegulationName", rs.getString("ParentRegulationName"));
                
                Timestamp createDatetime = rs.getTimestamp("CreateDatetime");
                if (createDatetime != null) {
                    regulation.put("createDatetime", createDatetime.toLocalDateTime());
                }
                
                Timestamp lastUpdateDatetime = rs.getTimestamp("LastUpdateDatetime");
                if (lastUpdateDatetime != null) {
                    regulation.put("lastUpdateDatetime", lastUpdateDatetime.toLocalDateTime());
                }
                
                int lastUpdateUserId = rs.getInt("LastUpdate_UserID");
                if (!rs.wasNull()) {
                    regulation.put("lastUpdateUserId", lastUpdateUserId);
                }
                
                regulations.add(regulation);
            }
        }
        return regulations;
    }

    public RegulationXRegulatoryTheme createRegulationXRegulatoryTheme(RegulationXRegulatoryTheme regulation) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, regulation.getRegulationId());
            pstmt.setInt(2, regulation.getRegulatoryThemeId());
            pstmt.setObject(3, regulation.getRelationType());
            pstmt.setString(4, regulation.getDescription());
            pstmt.setObject(5, regulation.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating regulation relationship failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    regulation.setId(generatedKeys.getInt(1));
                    return regulation;
                } else {
                    throw new SQLException("Creating regulation relationship failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateRegulationXRegulatoryTheme(RegulationXRegulatoryTheme regulation) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setInt(1, regulation.getRegulationId());
            pstmt.setObject(2, regulation.getRelationType());
            pstmt.setString(3, regulation.getDescription());
            pstmt.setObject(4, regulation.getLastUpdateUserId());
            pstmt.setInt(5, regulation.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteRegulationXRegulatoryTheme(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private RegulationXRegulatoryTheme mapResultSetToRegulationXRegulatoryTheme(ResultSet rs) throws SQLException {
        RegulationXRegulatoryTheme regulation = new RegulationXRegulatoryTheme();
        regulation.setId(rs.getInt("ID"));
        regulation.setRegulationId(rs.getInt("Regulation_ID"));
        regulation.setRegulatoryThemeId(rs.getInt("RegulatoryTheme_ID"));
        
        int relationType = rs.getInt("RelationType");
        if (!rs.wasNull()) {
            regulation.setRelationType(relationType);
        }
        
        regulation.setDescription(rs.getString("Description"));
        
        Timestamp createDatetime = rs.getTimestamp("CreateDatetime");
        if (createDatetime != null) {
            regulation.setCreateDatetime(createDatetime.toLocalDateTime());
        }
        
        Timestamp lastUpdateDatetime = rs.getTimestamp("LastUpdateDatetime");
        if (lastUpdateDatetime != null) {
            regulation.setLastUpdateDatetime(lastUpdateDatetime.toLocalDateTime());
        }
        
        int lastUpdateUserId = rs.getInt("LastUpdate_UserID");
        if (!rs.wasNull()) {
            regulation.setLastUpdateUserId(lastUpdateUserId);
        }
        
        return regulation;
    }

    // ===== AUDIT TRACKING METHODS =====

    /**
     * إنشاء audit records عند ربط regulation بـ regulatory theme
     * يتم استدعاء هذا method بعد إنشاء العلاقة بنجاح
     */
    public void createRegulationLinkAuditRecords(int regulatoryThemeId, int regulationId, Integer relationTypeId, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // إعداد audit statement
            String auditSql = """
                INSERT INTO regulatory_theme_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Regulation Name
            String regulationName = getRegulationName(regulationId);
            if (regulationName != null) {
                createNewAuditRecord(conn, auditStmt, regulatoryThemeId, "Regulation X Regulatory Theme", "link", "Added", "Regulation Name", regulationName, userName);
            }

            // Relation Type
            if (relationTypeId != null) {
                String relationTypeName = getRelationTypeName(relationTypeId);
                if (relationTypeName != null) {
                    createNewAuditRecord(conn, auditStmt, regulatoryThemeId, "Regulation X Regulatory Theme", "link", "Added", "Relation Type", relationTypeName, userName);
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
     * إنشاء audit records عند إلغاء ربط regulation من regulatory theme
     * يتم استدعاء هذا method قبل حذف العلاقة
     */
    public void createRegulationUnlinkAuditRecord(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الحصول على بيانات العلاقة قبل الحذف
            RegulationXRegulatoryTheme relationship = getRegulationXRegulatoryThemeById(id);
            if (relationship == null) {
                throw new SQLException("Regulation relationship not found with ID: " + id);
            }
            
            // إعداد audit statement
            String auditSql = """
                INSERT INTO regulatory_theme_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, NULL, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Regulation Name
            String regulationName = getRegulationName(relationship.getRegulationId());
            if (regulationName != null) {
                createRemovedAuditRecord(conn, auditStmt, relationship.getRegulatoryThemeId(), "Regulation X Regulatory Theme", "link", "Deleted", "Regulation Name", regulationName, userName);
            }

            // Relation Type
            if (relationship.getRelationType() != null) {
                String relationTypeName = getRelationTypeName(relationship.getRelationType());
                if (relationTypeName != null) {
                    createRemovedAuditRecord(conn, auditStmt, relationship.getRegulatoryThemeId(), "Regulation X Regulatory Theme", "link", "Deleted", "Relation Type", relationTypeName, userName);
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
     * إنشاء audit records عند تحديث علاقة regulation بـ regulatory theme
     * يتم استدعاء هذا method بعد التحديث بنجاح
     */
    public void createRegulationUpdateAuditRecords(int id, RegulationXRegulatoryTheme oldRelationship, RegulationXRegulatoryTheme newRelationship, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // إعداد audit statement
            String auditSql = """
                INSERT INTO regulatory_theme_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Check if Regulation changed
            if (!oldRelationship.getRegulationId().equals(newRelationship.getRegulationId())) {
                String oldRegulationName = getRegulationName(oldRelationship.getRegulationId());
                String newRegulationName = getRegulationName(newRelationship.getRegulationId());
                createUpdateAuditRecord(conn, auditStmt, newRelationship.getRegulatoryThemeId(), "Regulation X Regulatory Theme", "link",
                    "Updated", "Regulation Name", oldRegulationName, newRegulationName, userName);
            }

            // Check if Relation Type changed
            if (!isEqual(oldRelationship.getRelationType(), newRelationship.getRelationType())) {
                String oldRelationTypeName = oldRelationship.getRelationType() != null ? getRelationTypeName(oldRelationship.getRelationType()) : null;
                String newRelationTypeName = newRelationship.getRelationType() != null ? getRelationTypeName(newRelationship.getRelationType()) : null;
                createUpdateAuditRecord(conn, auditStmt, newRelationship.getRegulatoryThemeId(), "Regulation X Regulatory Theme", "link",
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
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int regulatoryThemeId, 
                                    String object, String event, String updateType, String field, 
                                    String value, String userName) throws SQLException {
        auditStmt.setInt(1, regulatoryThemeId);
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
    private int createRemovedAuditRecord(Connection conn, PreparedStatement auditStmt, int regulatoryThemeId,
                                        String object, String event, String updateType, String field,
                                        String value, String userName) throws SQLException {
        auditStmt.setInt(1, regulatoryThemeId);
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
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, int regulatoryThemeId,
                                       String object, String event, String updateType, String field,
                                       String oldValue, String newValue, String userName) throws SQLException {
        auditStmt.setInt(1, regulatoryThemeId);
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
     * الحصول على اسم الـ regulation
     */
    private String getRegulationName(int regulationId) throws SQLException {
        String sql = "SELECT primaryName FROM regulation WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryName");
            }
        }
        return null;
    }

    /**
     * الحصول على اسم نوع العلاقة
     */
    private String getRelationTypeName(int relationTypeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM regulation_x_regulatorytheme_relationtype WHERE ID = ?";
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