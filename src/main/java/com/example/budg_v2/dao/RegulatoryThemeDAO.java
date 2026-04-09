package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.RegulatoryTheme;
import com.example.budg_v2.service.SegmentAccessService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RegulatoryThemeDAO {

    private static final String SELECT_ALL = "SELECT * FROM regulatorytheme WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String SELECT_BY_ID = "SELECT rt.*, CONCAT(ub.First_Name, ' ', ub.Last_Name) AS updatedByName, " +
            "s.primaryname AS statusName " +
            "FROM regulatorytheme rt " +
            "LEFT JOIN people ub ON ub.ID = rt.LastUpdate_UserID " +
            "LEFT JOIN status s ON s.ID = rt.Status_ID " +
            "WHERE rt.ID = ? AND rt.DeletedDatetime IS NULL";
    private static final String SELECT_BY_REFNUMBER = "SELECT * FROM regulatorytheme WHERE LOWER(RefNumber) = LOWER(?) AND DeletedDatetime IS NULL";
    private static final String SELECT_BY_REFNUMBER_EXCLUDE_ID = "SELECT * FROM regulatorytheme WHERE LOWER(RefNumber) = LOWER(?) AND ID != ? AND DeletedDatetime IS NULL";
    private static final String SELECT_FOR_DROPDOWN = "SELECT ID, PrimaryName, Description, RefNumber FROM regulatorytheme WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String SELECT_FOR_PARENT_PICKER = "SELECT ID, PrimaryName, Description FROM regulatorytheme WHERE ID != ? AND DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String INSERT = "INSERT INTO regulatorytheme (ID, Parent_ID, Status_ID, RefNumber, ShortName, PrimaryName, Description, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) VALUES ((SELECT COALESCE(MAX(ID), 0) + 1 FROM regulatorytheme r), ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?)";
    private static final String UPDATE = "UPDATE regulatorytheme SET Parent_ID = ?, Status_ID = ?, RefNumber = ?, ShortName = ?, PrimaryName = ?, Description = ?, LastUpdateDatetime = NOW(), LastUpdate_UserID = ? WHERE ID = ?";
    private static final String SOFT_DELETE = "UPDATE regulatorytheme SET DeletedDatetime = NOW() WHERE ID = ?";
    private static final String SEARCH = "SELECT * FROM regulatorytheme WHERE (PrimaryName LIKE ? OR Description LIKE ? OR RefNumber LIKE ?) AND DeletedDatetime IS NULL ORDER BY PrimaryName";

    public List<RegulatoryTheme> getAllRegulatoryThemes() throws SQLException {
        List<RegulatoryTheme> themes = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                themes.add(mapResultSetToRegulatoryTheme(rs));
            }
        }
        return themes;
    }

    /**
     * Get all regulatory themes filtered by user's segment access
     */
    public List<RegulatoryTheme> getAllRegulatoryThemes(int userId) throws SQLException {
        List<RegulatoryTheme> allThemes = getAllRegulatoryThemes();
        if (allThemes.isEmpty()) {
            return allThemes;
        }
        
        // Get accessible theme IDs for this user (filtered by selected segments)
        List<Integer> allIds = allThemes.stream().map(RegulatoryTheme::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "RegulatoryTheme", allIds);
        
        // Filter to only accessible themes
        return allThemes.stream()
                .filter(t -> accessibleIds.contains(t.getId()))
                .collect(Collectors.toList());
    }

    public RegulatoryTheme getRegulatoryThemeById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRegulatoryTheme(rs);
                }
            }
        }
        return null;
    }

    public RegulatoryTheme getRegulatoryThemeByPrimaryName(String primaryName) throws SQLException {
        if (primaryName == null || primaryName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM regulatorytheme WHERE PrimaryName = ? AND DeletedDatetime IS NULL LIMIT 1")) {
            pstmt.setString(1, primaryName.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRegulatoryTheme(rs);
                }
            }
        }
        return null;
    }

    public List<RegulatoryTheme> getRegulatoryThemesForDropdown() throws SQLException {
        List<RegulatoryTheme> themes = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_FOR_DROPDOWN);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                RegulatoryTheme theme = new RegulatoryTheme();
                theme.setId(rs.getInt("ID"));
                theme.setPrimaryName(rs.getString("PrimaryName"));
                theme.setDescription(rs.getString("Description"));
                theme.setRefNumber(rs.getString("RefNumber"));
                themes.add(theme);
            }
        }
        return themes;
    }

    public List<RegulatoryTheme> getRegulatoryThemesForDropdown(int userId) throws SQLException {
        List<RegulatoryTheme> themes = getRegulatoryThemesForDropdown();
        if (themes.isEmpty()) {
            return themes;
        }

        List<Integer> allIds = themes.stream().map(RegulatoryTheme::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "RegulatoryTheme", allIds);

        return themes.stream()
                .filter(theme -> accessibleIds.contains(theme.getId()))
                .collect(Collectors.toList());
    }

    public List<RegulatoryTheme> getRegulatoryThemesForParentPicker(int excludeId) throws SQLException {
        List<RegulatoryTheme> themes = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_FOR_PARENT_PICKER)) {

            pstmt.setInt(1, excludeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    RegulatoryTheme theme = new RegulatoryTheme();
                    theme.setId(rs.getInt("ID"));
                    theme.setPrimaryName(rs.getString("PrimaryName"));
                    theme.setDescription(rs.getString("Description"));
                    themes.add(theme);
                }
            }
        }
        return themes;
    }

    public List<RegulatoryTheme> getRegulatoryThemesForParentPicker(int excludeId, int userId) throws SQLException {
        List<RegulatoryTheme> themes = getRegulatoryThemesForParentPicker(excludeId);
        if (themes.isEmpty()) {
            return themes;
        }

        List<Integer> allIds = themes.stream().map(RegulatoryTheme::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "RegulatoryTheme", allIds);

        return themes.stream()
                .filter(theme -> accessibleIds.contains(theme.getId()))
                .collect(Collectors.toList());
    }

    public RegulatoryTheme createRegulatoryTheme(RegulatoryTheme theme) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return createRegulatoryTheme(theme, conn);
        }
    }

    /**
     * Insert using the caller's connection (e.g. bulk upload transaction). Does not commit or close {@code conn}.
     */
    public RegulatoryTheme createRegulatoryTheme(RegulatoryTheme theme, Connection conn) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(INSERT)) {

            pstmt.setObject(1, theme.getParentId());
            pstmt.setObject(2, theme.getStatusId());
            pstmt.setString(3, theme.getRefNumber());
            pstmt.setString(4, theme.getShortName());
            pstmt.setString(5, theme.getPrimaryName());
            pstmt.setString(6, theme.getDescription());
            pstmt.setObject(7, theme.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating regulatory theme failed, no rows affected.");
            }
        }

        try (PreparedStatement idStmt = conn.prepareStatement(
                "SELECT ID FROM regulatorytheme WHERE PrimaryName = ? AND Description = ? AND CreateDatetime >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) ORDER BY ID DESC LIMIT 1")) {
            idStmt.setString(1, theme.getPrimaryName());
            idStmt.setString(2, theme.getDescription());
            try (ResultSet idRs = idStmt.executeQuery()) {
                if (idRs.next()) {
                    int generatedId = idRs.getInt(1);
                    theme.setId(generatedId);
                    return theme;
                } else {
                    throw new SQLException("Creating regulatory theme failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateRegulatoryTheme(RegulatoryTheme theme) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setObject(1, theme.getParentId());
            pstmt.setObject(2, theme.getStatusId());
            pstmt.setString(3, theme.getRefNumber());
            pstmt.setString(4, theme.getShortName());
            pstmt.setString(5, theme.getPrimaryName());
            pstmt.setString(6, theme.getDescription());
            pstmt.setObject(7, theme.getLastUpdateUserId());
            pstmt.setInt(8, theme.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteRegulatoryTheme(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    public List<RegulatoryTheme> searchRegulatoryThemes(String searchTerm) throws SQLException {
        List<RegulatoryTheme> themes = new ArrayList<>();
        String searchPattern = "%" + searchTerm + "%";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    themes.add(mapResultSetToRegulatoryTheme(rs));
                }
            }
        }
        return themes;
    }

    public List<RegulatoryTheme> searchRegulatoryThemes(String searchTerm, int userId) throws SQLException {
        List<RegulatoryTheme> themes = searchRegulatoryThemes(searchTerm);
        if (themes.isEmpty()) {
            return themes;
        }

        List<Integer> allIds = themes.stream().map(RegulatoryTheme::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "RegulatoryTheme", allIds);

        return themes.stream()
                .filter(theme -> accessibleIds.contains(theme.getId()))
                .collect(Collectors.toList());
    }

    private RegulatoryTheme mapResultSetToRegulatoryTheme(ResultSet rs) throws SQLException {
        RegulatoryTheme theme = new RegulatoryTheme();
        theme.setId(rs.getInt("ID"));
        theme.setParentId(rs.getObject("Parent_ID", Integer.class));
        theme.setStatusId(rs.getObject("Status_ID", Integer.class));
        try {
            theme.setStatusName(rs.getString("statusName"));
        } catch (SQLException e) {
            theme.setStatusName(null);
        }
        theme.setRefNumber(rs.getString("RefNumber"));
        theme.setShortName(rs.getString("ShortName"));
        theme.setPrimaryName(rs.getString("PrimaryName"));
        theme.setDescription(rs.getString("Description"));
        theme.setCreateDateTime(rs.getTimestamp("CreateDatetime"));
        theme.setLastUpdateDateTime(rs.getTimestamp("LastUpdateDatetime"));
        theme.setDeletedDateTime(rs.getTimestamp("DeletedDatetime"));
        theme.setLastUpdateUserId(rs.getObject("LastUpdate_UserID", Integer.class));
        // Get updatedByName if available (from JOIN in SELECT_BY_ID)
        // Try different case variations since MySQL column names can be case-sensitive
        String updatedByName = null;
        try {
            updatedByName = rs.getString("updatedByName");
        } catch (SQLException e) {
            try {
                updatedByName = rs.getString("UpdatedByName");
            } catch (SQLException e2) {
                try {
                    updatedByName = rs.getString("UPDATEDBYNAME");
                } catch (SQLException e3) {
                    // Column doesn't exist in this query, that's okay
                }
            }
        }
        theme.setUpdatedByName(updatedByName);
        return theme;
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int themeId, String object, String event, String updateType, String field, String value, String userName) throws SQLException {
        auditStmt.setInt(1, themeId);            // id
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

    private static final String INSERT_REGULATORYTHEME_AUDIT_SNAPSHOT = """
            INSERT INTO regulatorytheme_audit (
                ID, Parent_ID, Status_ID, RefNumber, ShortName, PrimaryName, Description,
                CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdate_UserID, RevType
            )
            SELECT
                ID, Parent_ID, Status_ID, RefNumber, ShortName, PrimaryName, Description,
                CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdate_UserID, 'Added'
            FROM regulatorytheme
            WHERE ID = ?
            """;

    /**
     * إنشاء audit records للموضوع التنظيمي الجديد
     * يتم استدعاء هذا method بعد إنشاء الموضوع بنجاح
     */
    public void createRegulatoryThemeAuditRecords(int themeId, String userName) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            insertRegulatoryThemeAuditHistoryRecords(themeId, userName, conn);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Same as {@link #createRegulatoryThemeAuditRecords(int, String)} but participates in the caller's transaction.
     */
    public void createRegulatoryThemeAuditRecords(int themeId, String userName, Connection conn) throws SQLException {
        insertRegulatoryThemeAuditHistoryRecords(themeId, userName, conn);
    }

    private void insertRegulatoryThemeAuditHistoryRecords(int themeId, String userName, Connection conn) throws SQLException {
        String themeDataSql = "SELECT * FROM regulatorytheme WHERE ID = ?";
        try (PreparedStatement themeStmt = conn.prepareStatement(themeDataSql)) {
            themeStmt.setInt(1, themeId);
            try (ResultSet themeRs = themeStmt.executeQuery()) {
                if (!themeRs.next()) {
                    throw new SQLException("Regulatory Theme not found with ID: " + themeId);
                }

                String auditSql = """
                        INSERT INTO regulatory_theme_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                        VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
                        """;
                try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {

                    String primaryName = themeRs.getString("PrimaryName");
                    if (primaryName != null && !primaryName.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", "Added", "Primary Name", primaryName, userName);
                    }

                    String shortName = themeRs.getString("ShortName");
                    if (shortName != null && !shortName.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", "Added", "Short Name", shortName, userName);
                    }

                    String description = themeRs.getString("Description");
                    if (description != null && !description.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", "Added", "Description", description, userName);
                    }

                    String refNumber = themeRs.getString("RefNumber");
                    if (refNumber != null && !refNumber.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", "Added", "Reference Number", refNumber, userName);
                    }

                    Integer parentId = themeRs.getObject("Parent_ID", Integer.class);
                    if (parentId != null) {
                        String parentName = getRegulatoryThemeName(parentId);
                        if (parentName != null) {
                            createNewAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", "Added", "Parent Regulatory Theme", parentName, userName);
                        }
                    }

                    Integer statusId = themeRs.getObject("Status_ID", Integer.class);
                    if (statusId != null) {
                        String statusName = getStatusPrimaryName(statusId);
                        if (statusName != null) {
                            createNewAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", "Status Change", "Status", statusName, userName);
                        }
                    }

                    Integer lastUpdateUserId = themeRs.getObject("LastUpdate_UserID", Integer.class);
                    if (lastUpdateUserId != null) {
                        String createdByName = getPersonFullName(lastUpdateUserId);
                        if (createdByName != null) {
                            createNewAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", "Added", "Created By", createdByName, userName);
                        }
                    }
                }
            }
        }
    }

    /**
     * إنشاء سجل في جدول regulatorytheme_audit بعد إنشاء الموضوع التنظيمي
     * يتم استدعاء هذا method بعد إنشاء الموضوع بنجاح
     */
    public void createRegulatoryThemeAuditRecord(int themeId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_REGULATORYTHEME_AUDIT_SNAPSHOT)) {
            ps.setInt(1, themeId);
            ps.executeUpdate();
        }
    }

    public void createRegulatoryThemeAuditRecord(int themeId, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_REGULATORYTHEME_AUDIT_SNAPSHOT)) {
            ps.setInt(1, themeId);
            ps.executeUpdate();
        }
    }

    /**
     * تحديث الموضوع التنظيمي مع تسجيل audit records
     */
    public boolean updateRegulatoryThemeWithAudit(RegulatoryTheme oldTheme, RegulatoryTheme newTheme, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement updateStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // Check if there are actual changes to any fields
            boolean hasChanges = hasFieldChanges(oldTheme, newTheme);
            
            // الخطوة 1: تحديث الجدول الأساسي
            // Only update LastUpdateDatetime and LastUpdate_UserID if there are actual changes
            String updateSql;
            if (hasChanges) {
                updateSql = """
                    UPDATE regulatorytheme 
                    SET Parent_ID = ?, Status_ID = ?, RefNumber = ?, ShortName = ?, 
                        PrimaryName = ?, Description = ?, LastUpdate_UserID = ?, LastUpdateDatetime = NOW()
                    WHERE ID = ?
                """;
            } else {
                // No changes detected - preserve original LastUpdateDatetime and LastUpdate_UserID
                updateSql = """
                    UPDATE regulatorytheme 
                    SET Parent_ID = ?, Status_ID = ?, RefNumber = ?, ShortName = ?, 
                        PrimaryName = ?, Description = ?
                    WHERE ID = ?
                """;
            }
            
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setObject(1, newTheme.getParentId());
            updateStmt.setObject(2, newTheme.getStatusId());
            updateStmt.setString(3, newTheme.getRefNumber());
            updateStmt.setString(4, newTheme.getShortName());
            updateStmt.setString(5, newTheme.getPrimaryName());
            updateStmt.setString(6, newTheme.getDescription());
            if (hasChanges) {
                updateStmt.setObject(7, newTheme.getLastUpdateUserId());
                updateStmt.setInt(8, newTheme.getId());
            } else {
                updateStmt.setInt(7, newTheme.getId());
            }
            
            int affectedRows = updateStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات
                try {
                    createRegulatoryThemeUpdateAuditRecords(conn, newTheme.getId(), oldTheme, newTheme, userName);
                    //system.out.println("✅ Regulatory Theme update audit records created for ID: " + newTheme.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating regulatory theme update audit records: " + e.getMessage());
                    e.printStackTrace();
                }

                // الخطوة 3: إنشاء snapshot جديد في regulatorytheme_audit
                try {
                    createRegulatoryThemeUpdateAuditSnapshot(conn, newTheme.getId());
                    //system.out.println("✅ RegulatoryThemeDAO: regulatorytheme_audit update snapshot created for ID: " + newTheme.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating regulatorytheme_audit update snapshot: " + e.getMessage());
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
     * إنشاء audit records عند تحديث الموضوع التنظيمي
     */
    public void createRegulatoryThemeUpdateAuditRecords(Connection conn, int themeId, RegulatoryTheme oldTheme, RegulatoryTheme newTheme, String userName) throws SQLException {
        //system.out.println("🔍 RegulatoryThemeDAO.createRegulatoryThemeUpdateAuditRecords - START for ID: " + themeId);
        PreparedStatement auditStmt = null;
        
        try {
            String auditSql = """
                INSERT INTO regulatory_theme_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldTheme.getPrimaryName(), newTheme.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", 
                    "Updated", "Primary Name", oldTheme.getPrimaryName(), newTheme.getPrimaryName(), userName);
            }
            
            // Short Name
            if (!isEqual(oldTheme.getShortName(), newTheme.getShortName())) {
                createUpdateAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", 
                    "Updated", "Short Name", oldTheme.getShortName(), newTheme.getShortName(), userName);
            }
            
            // Description
            if (!isEqual(oldTheme.getDescription(), newTheme.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", 
                    "Updated", "Description", oldTheme.getDescription(), newTheme.getDescription(), userName);
            }
            
            // Reference Number
            if (!isEqual(oldTheme.getRefNumber(), newTheme.getRefNumber())) {
                createUpdateAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", 
                    "Updated", "Reference Number", oldTheme.getRefNumber(), newTheme.getRefNumber(), userName);
            }
            
            // Parent Regulatory Theme
            if (!isEqual(oldTheme.getParentId(), newTheme.getParentId())) {
                String oldParentName = oldTheme.getParentId() != null ? getRegulatoryThemeName(oldTheme.getParentId()) : null;
                String newParentName = newTheme.getParentId() != null ? getRegulatoryThemeName(newTheme.getParentId()) : null;
                createUpdateAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", 
                    "Updated", "Parent Regulatory Theme", oldParentName, newParentName, userName);
            }
            
            // Status
            if (!isEqual(oldTheme.getStatusId(), newTheme.getStatusId())) {
                String oldStatusName = oldTheme.getStatusId() != null ? getStatusPrimaryName(oldTheme.getStatusId()) : null;
                String newStatusName = newTheme.getStatusId() != null ? getStatusPrimaryName(newTheme.getStatusId()) : null;
                createUpdateAuditRecord(conn, auditStmt, themeId, "Regulatory Theme", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Error in createRegulatoryThemeUpdateAuditRecords: " + e.getMessage());
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
            int themeId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, themeId);
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
     * Method لإنشاء snapshot جديد في regulatorytheme_audit عند الـ update
     */
    public void createRegulatoryThemeUpdateAuditSnapshot(Connection conn, int themeId) throws SQLException {
        //system.out.println("🔍 RegulatoryThemeDAO.createRegulatoryThemeUpdateAuditSnapshot - Creating update snapshot for ID: " + themeId);
        String sql = """
            INSERT INTO regulatorytheme_audit (
                ID, Parent_ID, Status_ID, RefNumber, ShortName, PrimaryName, Description,
                CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdate_UserID, RevType
            )
            SELECT 
                ID, Parent_ID, Status_ID, RefNumber, ShortName, PrimaryName, Description,
                CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdate_UserID, 'Updated'
            FROM regulatorytheme 
            WHERE ID = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, themeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * حذف الموضوع التنظيمي مع تسجيل audit records
     */
    public boolean deleteRegulatoryThemeWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE regulatorytheme SET DeletedDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO regulatory_theme_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Regulatory Theme");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Regulatory Theme");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في regulatorytheme_audit
                String snapshotSql = """
                    INSERT INTO regulatorytheme_audit (
                        ID, Parent_ID, Status_ID, RefNumber, ShortName, PrimaryName, Description,
                        CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdate_UserID, RevType
                    )
                    SELECT 
                        ID, Parent_ID, Status_ID, RefNumber, ShortName, PrimaryName, Description,
                        CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdate_UserID, 'Deleted'
                    FROM regulatorytheme 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Regulatory Theme deleted with audit for ID: " + id);
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
    private String getRegulatoryThemeName(int themeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM regulatorytheme WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, themeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getStatusPrimaryName(int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM status WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
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
     * Helper method to check if there are any field changes between old and new theme
     */
    private boolean hasFieldChanges(RegulatoryTheme oldTheme, RegulatoryTheme newTheme) {
        // Check all fields that can be updated
        return !isEqual(oldTheme.getPrimaryName(), newTheme.getPrimaryName()) ||
               !isEqual(oldTheme.getShortName(), newTheme.getShortName()) ||
               !isEqual(oldTheme.getDescription(), newTheme.getDescription()) ||
               !isEqual(oldTheme.getRefNumber(), newTheme.getRefNumber()) ||
               !isEqual(oldTheme.getParentId(), newTheme.getParentId()) ||
               !isEqual(oldTheme.getStatusId(), newTheme.getStatusId());
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

    /**
     * Get regulatory theme by RefNumber
     */
    public RegulatoryTheme getRegulatoryThemeByRefNumber(String refNumber) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return null;
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER)) {
            
            pstmt.setString(1, refNumber.trim());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRegulatoryTheme(rs);
                }
            }
        }
        return null;
    }

    /**
     * Get regulatory theme by RefNumber excluding a specific ID (for update validation)
     */
    public RegulatoryTheme getRegulatoryThemeByRefNumberExcludingId(String refNumber, int excludeId) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return null;
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER_EXCLUDE_ID)) {
            
            pstmt.setString(1, refNumber.trim());
            pstmt.setInt(2, excludeId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRegulatoryTheme(rs);
                }
            }
        }
        return null;
    }

    /**
     * Check if RefNumber is unique
     */
    /**
     * Check if RefNumber is unique (for create operations).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     */
    public boolean isRefNumberUnique(String refNumber) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUnique("RegulatoryTheme", refNumber);
    }

    /**
     * Check if RefNumber is unique for update (excluding current ID).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     * Rule: Can repeat ref if it's the same object (excludeId), but cannot repeat for different objects.
     */
    public boolean isRefNumberUniqueForUpdate(String refNumber, int excludeId) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUniqueForUpdate("RegulatoryTheme", refNumber, excludeId);
    }
}
