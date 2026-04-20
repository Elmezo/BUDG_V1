package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Legal;
import com.example.budg_v2.service.SegmentAccessService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LegalDAO {

    public LegalDAO() {
    }

    // SELECT queries
    private static final String SELECT_ALL_WITH_JOINS = """
        SELECT 
            l.ID                                AS "Id",
            l.ShortName                         AS "Short Name",
            l.LongName                          AS "Long Name",
            COALESCE(p.ShortName, 'Top Level')  AS "Parent Short Name",
            COALESCE(p.LongName,  'Top Level')  AS "Parent Long Name",
            l.Description                       AS "Description",
            s.PrimaryName                       AS "BUDG Status",
            v.Name                              AS "BUDG Viewing",
            l.CreateDatetime                    AS "Created Date",
            l.LastUpdateDatetime                AS "Last Updated",
            CONCAT(pe.First_Name, ' ', pe.Last_Name) AS "Last Updated By",
            l.Parent_ID,
            l.Status,
            l.Is_Public,
            l.LastUpdate_UserID
        FROM legal l
        LEFT JOIN legal   p  ON l.Parent_ID = p.ID
        LEFT JOIN status  s  ON l.Status    = s.ID
        LEFT JOIN viewing v  ON l.Is_Public = v.id
        LEFT JOIN people  pe ON l.LastUpdate_UserID = pe.ID
        WHERE l.`DeleteDatetime` IS NULL
        ORDER BY l.ID
        """;

    private static final String SELECT_BY_ID_WITH_JOINS = """
        SELECT 
            l.ID                                AS "Id",
            l.ShortName                         AS "Short Name",
            l.LongName                          AS "Long Name",
            COALESCE(p.ShortName, 'Top Level')  AS "Parent Short Name",
            COALESCE(p.LongName,  'Top Level')  AS "Parent Long Name",
            l.Description                       AS "Description",
            s.PrimaryName                       AS "BUDG Status",
            v.Name                              AS "BUDG Viewing",
            l.CreateDatetime                    AS "Created Date",
            l.LastUpdateDatetime                AS "Last Updated",
            CONCAT(pe.First_Name, ' ', pe.Last_Name) AS "Last Updated By",
            l.Parent_ID,
            l.Status,
            l.Is_Public,
            l.LastUpdate_UserID
        FROM legal l
        LEFT JOIN legal   p  ON l.Parent_ID = p.ID
        LEFT JOIN status  s  ON l.Status    = s.ID
        LEFT JOIN viewing v  ON l.Is_Public = v.id
        LEFT JOIN people  pe ON l.LastUpdate_UserID = pe.ID
        WHERE l.ID = ? AND l.`DeleteDatetime` IS NULL
        """;

    private static final String SEARCH_WITH_JOINS = """
        SELECT 
            l.ID                                AS "Id",
            l.ShortName                         AS "Short Name",
            l.LongName                          AS "Long Name",
            COALESCE(p.ShortName, 'Top Level')  AS "Parent Short Name",
            COALESCE(p.LongName,  'Top Level')  AS "Parent Long Name",
            l.Description                       AS "Description",
            s.PrimaryName                       AS "BUDG Status",
            v.Name                              AS "BUDG Viewing",
            l.CreateDatetime                    AS "Created Date",
            l.LastUpdateDatetime                AS "Last Updated",
            CONCAT(pe.First_Name, ' ', pe.Last_Name) AS "Last Updated By",
            l.Parent_ID,
            l.Status,
            l.Is_Public,
            l.LastUpdate_UserID
        FROM legal l
        LEFT JOIN legal   p  ON l.Parent_ID = p.ID
        LEFT JOIN status  s  ON l.Status    = s.ID
        LEFT JOIN viewing v  ON l.Is_Public = v.id
        LEFT JOIN people  pe ON l.LastUpdate_UserID = pe.ID
        WHERE l.`DeleteDatetime` IS NULL 
        AND (l.ShortName LIKE ? OR l.LongName LIKE ? OR l.Description LIKE ?)
        ORDER BY l.ID
        """;

    // INSERT query
    private static final String INSERT = """
        INSERT INTO legal (
            LongName,
            ShortName,
            Parent_ID,
            Description,
            Status,
            Is_Public,
            LastUpdate_UserID
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    // UPDATE query
    private static final String UPDATE = """
        UPDATE legal
        SET 
            LongName           = ?,
            ShortName          = ?,
            Description        = ?,
            Status             = ?,
            Is_Public          = ?,
            Parent_ID          = ?,
            LastUpdate_UserID  = ?,
            LastUpdateDatetime = NOW()
        WHERE ID = ?
        """;

    // DELETE query (soft delete)
    private static final String SOFT_DELETE = """
        UPDATE legal 
        SET deletedatetime = NOW() 
        WHERE ID = ?
        """;

    public List<Legal> getAllLegals() throws SQLException {
        return getAllLegalsForGuest();
    }

    /**
     * Get all legal entities for guest users (public, Enterprise only, not deleted)
     */
    public List<Legal> getAllLegalsForGuest() throws SQLException {
        String guestFilter = SegmentAccessService.buildGuestFilterClause("Legal", "l", "l.ID");
        if (guestFilter == null) {
            return getAllLegalsUnfiltered();
        }
        String sql = "SELECT l.ID AS \"Id\", l.ShortName AS \"Short Name\", l.LongName AS \"Long Name\", " +
                "COALESCE(p.ShortName, 'Top Level') AS \"Parent Short Name\", COALESCE(p.LongName, 'Top Level') AS \"Parent Long Name\", " +
                "l.Description AS \"Description\", s.PrimaryName AS \"BUDG Status\", v.Name AS \"BUDG Viewing\", " +
                "l.CreateDatetime AS \"Created Date\", l.LastUpdateDatetime AS \"Last Updated\", " +
                "CONCAT(pe.First_Name, ' ', pe.Last_Name) AS \"Last Updated By\", l.Parent_ID, l.Status, l.Is_Public, l.LastUpdate_UserID " +
                "FROM legal l " +
                "LEFT JOIN legal p ON l.Parent_ID = p.ID " +
                "LEFT JOIN status s ON l.Status = s.ID " +
                "LEFT JOIN viewing v ON l.Is_Public = v.id " +
                "LEFT JOIN people pe ON l.LastUpdate_UserID = pe.ID " +
                "WHERE " + guestFilter + " ORDER BY l.ID";
        List<Legal> legals = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                legals.add(mapResultSetToLegal(rs));
            }
        }
        return legals;
    }

    public List<Legal> getAllLegalsUnfiltered() throws SQLException {
        List<Legal> legals = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL_WITH_JOINS)) {
            while (rs.next()) {
                legals.add(mapResultSetToLegal(rs));
            }
        }
        return legals;
    }

    /**
     * Get all legal entities filtered by user's segment access
     */
    public List<Legal> getAllLegals(int userId) throws SQLException {
        if (userId <= 0) {
            return getAllLegalsForGuest();
        }
        List<Legal> allLegals = getAllLegalsUnfiltered();
        if (allLegals.isEmpty()) {
            return allLegals;
        }
        
        // Get accessible legal entity IDs for this user (filtered by selected segments)
        List<Integer> allIds = allLegals.stream().map(Legal::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Legal", allIds);
        
        // Filter to only accessible legal entities
        return allLegals.stream()
                .filter(l -> accessibleIds.contains(l.getId()))
                .collect(Collectors.toList());
    }

    public Legal getLegalById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID_WITH_JOINS)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToLegal(rs);
            }
        }
        return null;
    }

    public List<Legal> searchLegals(String searchQuery) throws SQLException {
        List<Legal> legals = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH_WITH_JOINS)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                legals.add(mapResultSetToLegal(rs));
            }
        }
        return legals;
    }

    public Legal createLegal(Legal legal) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, legal.getLongName());
            pstmt.setString(2, legal.getShortName());
            pstmt.setObject(3, legal.getParentId());
            pstmt.setString(4, legal.getDescription());
            pstmt.setInt(5, legal.getStatus());
            pstmt.setInt(6, legal.getIsPublic());
            pstmt.setObject(7, legal.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating legal entity failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int legalId = generatedKeys.getInt(1);
                    legal.setId(legalId);
                    
                    // Create audit records after successful insert
                    try {
                        String userName = getPersonFullName(legal.getLastUpdateUserId());
                        createLegalAuditRecords(legalId, userName);
                        createLegalAuditRecord(legalId);
                    } catch (Exception e) {
                        //system.out.println("LegalDAO: Error creating audit records: " + e.getMessage());
                        // Don't fail the insert if audit fails
                    }
                    
                    return legal;
                } else {
                    throw new SQLException("Creating legal entity failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateLegal(Legal legal) throws SQLException {
        // الخطوة 1: احصل على البيانات القديمة قبل التحديث
        Legal oldLegal = getLegalById(legal.getId());
        if (oldLegal == null) {
            throw new SQLException("Legal entity not found with ID: " + legal.getId());
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, legal.getLongName());
            pstmt.setString(2, legal.getShortName());
            pstmt.setString(3, legal.getDescription());
            pstmt.setInt(4, legal.getStatus());
            pstmt.setInt(5, legal.getIsPublic());
            pstmt.setObject(6, legal.getParentId());
            pstmt.setObject(7, legal.getLastUpdateUserId());
            pstmt.setInt(8, legal.getId());

            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات
                try {
                    String userName = getPersonFullName(legal.getLastUpdateUserId());
                    if (userName != null) {
                        createLegalUpdateAuditRecords(legal.getId(), oldLegal, legal, userName);
                        //system.out.println("✅ Legal entity update audit records created for ID: " + legal.getId());
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating legal entity update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // الخطوة 3: إنشاء snapshot جديد في legal_audit
                try {
                    createLegalUpdateAuditSnapshot(legal.getId());
                    //system.out.println("✅ LegalDAO: legal_audit update snapshot created for ID: " + legal.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating legal_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return affectedRows > 0;
        }
    }

    public boolean deleteLegal(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private Legal mapResultSetToLegal(ResultSet rs) throws SQLException {
        Legal legal = new Legal();
        
        // Basic fields
        legal.setId(rs.getInt("Id"));
        legal.setShortName(rs.getString("Short Name"));
        legal.setLongName(rs.getString("Long Name"));
        legal.setDescription(rs.getString("Description"));
        legal.setCreateDatetime(rs.getTimestamp("Created Date"));
        legal.setLastUpdateDatetime(rs.getTimestamp("Last Updated"));
        
        // Foreign key fields
        int parentId = rs.getInt("Parent_ID");
        if (!rs.wasNull()) {
            legal.setParentId(parentId);
        }
        
        legal.setStatus(rs.getInt("Status"));
        legal.setIsPublic(rs.getInt("Is_Public"));
        
        int lastUpdateUserId = rs.getInt("LastUpdate_UserID");
        if (!rs.wasNull()) {
            legal.setLastUpdateUserId(lastUpdateUserId);
        }
        
        // Joined data fields
        legal.setParentShortName(rs.getString("Parent Short Name"));
        legal.setParentLongName(rs.getString("Parent Long Name"));
        legal.setBUDGStatus(rs.getString("BUDG Status"));
        legal.setBUDGViewing(rs.getString("BUDG Viewing"));
        legal.setLastUpdatedBy(rs.getString("Last Updated By"));
        
        return legal;
    }

    /**
     * Check if a ShortName already exists (excluding a specific ID for updates)
     */
    public boolean isShortNameExists(String shortName, Integer excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM legal WHERE ShortName = ?";
        if (excludeId != null) {
            sql += " AND ID != ?";
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, shortName);
            if (excludeId != null) {
                stmt.setInt(2, excludeId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Check if a LongName already exists (excluding a specific ID for updates)
     */
    public boolean isLongNameExists(String longName, Integer excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM legal WHERE LongName = ?";
        if (excludeId != null) {
            sql += " AND ID != ?";
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, longName);
            if (excludeId != null) {
                stmt.setInt(2, excludeId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Check if a Status ID exists in the Status table
     */
    public boolean isStatusExists(int statusId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM status WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, statusId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Check if a Viewing ID exists in the Viewing table
     */
    public boolean isViewingExists(int viewingId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM viewing WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, viewingId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Check if a Parent Legal ID exists in the Legal table
     */
    public boolean isParentLegalExists(int parentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM legal WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, parentId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Check if a User ID exists in the People table
     */
    public boolean isUserExists(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM people WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int legalId, String updateType, String field, String value, String userName) throws SQLException {
        //system.out.println("LegalDAO: createNewAuditRecord called - legalId: " + legalId + ", field: " + field + ", value: " + value);
        
        auditStmt.setInt(1, legalId);        // id
        auditStmt.setString(2, updateType);    // updateType
        auditStmt.setString(3, field);          // field
        auditStmt.setString(4, value);         // to
        auditStmt.setString(5, userName);       // author
        
        //system.out.println("LegalDAO: Executing audit insert for field: " + field);
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                int auditId = generatedKeys.getInt(1);
                //system.out.println("LegalDAO: Audit record created with ID: " + auditId);
                return auditId;
            }
        }
        //system.out.println("LegalDAO: No generated key returned");
        return -1;
    }

    /**
     * إنشاء audit records للـ legal entity الجديد
     * يتم استدعاء هذا method بعد إنشاء الـ legal entity بنجاح
     */
    public void createLegalAuditRecords(int legalId, String userName) throws SQLException {
        //system.out.println("LegalDAO: createLegalAuditRecords called with legalId: " + legalId + ", userName: " + userName);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            //system.out.println("LegalDAO: Transaction started");
            
            // 1. الحصول على بيانات الـ legal entity
            String legalDataSql = "SELECT * FROM legal WHERE ID = ?";
            //system.out.println("LegalDAO: Executing SQL: " + legalDataSql);
            PreparedStatement legalStmt = conn.prepareStatement(legalDataSql);
            legalStmt.setInt(1, legalId);
            ResultSet legalRs = legalStmt.executeQuery();
            
            if (!legalRs.next()) {
                //system.out.println("LegalDAO: Legal entity not found with ID: " + legalId);
                throw new SQLException("Legal entity not found with ID: " + legalId);
            }
            //system.out.println("LegalDAO: Legal entity found, starting audit record creation");

            // 2. إعداد audit statement
            String auditSql = """
                INSERT INTO legal_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Legal', 'Details', ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Short Name
            String shortName = legalRs.getString("ShortName");
            if (shortName != null && !shortName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, legalId, "Added", "Short Name", shortName, userName);
            }
            
            // Long Name
            String longName = legalRs.getString("LongName");
            if (longName != null && !longName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, legalId, "Added", "Long Name", longName, userName);
            }
            
            // Description
            String description = legalRs.getString("Description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, legalId, "Added", "Description", description, userName);
            }
            
            // Parent Legal Entity
            Integer parentId = legalRs.getObject("Parent_ID", Integer.class);
            if (parentId != null) {
                String parentName = getLegalEntityName(parentId);
                if (parentName != null) {
                    createNewAuditRecord(conn, auditStmt, legalId, "Added", "Parent Legal Entity", parentName, userName);
                }
            }
            
            // Status
            Integer statusId = legalRs.getObject("Status", Integer.class);
            if (statusId != null) {
                String statusName = getStatusPrimaryName(statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, legalId, "Status Change", "Status", statusName, userName);
                }
            }
            
            // Is Public
            Integer isPublicId = legalRs.getObject("Is_Public", Integer.class);
            if (isPublicId != null) {
                String isPublicName = getViewingName(isPublicId);
                if (isPublicName != null) {
                    createNewAuditRecord(conn, auditStmt, legalId, "Added", "Is Public", isPublicName, userName);
                }
            }
            
            // Created By
            Integer createdById = legalRs.getObject("LastUpdate_UserID", Integer.class);
            if (createdById != null) {
                String createdByName = getPersonFullName(createdById);
                if (createdByName != null) {
                    createNewAuditRecord(conn, auditStmt, legalId, "Added", "Created By", createdByName, userName);
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
     * إنشاء سجل في جدول legal_audit بعد إنشاء الـ legal entity
     * يتم استدعاء هذا method بعد إنشاء الـ legal entity بنجاح
     */
    public void createLegalAuditRecord(int legalId) throws SQLException {
        String sql = """
            INSERT INTO legal_audit (
                ID, Parent_ID, Status, Is_Public, ShortName, LongName, Description, 
                CreateDatetime, LastUpdateDatetime, DeleteDatetime, LastUpdate_UserID, RevType
            )
            SELECT 
                ID, Parent_ID, Status, Is_Public, ShortName, LongName, Description, 
                CreateDatetime, LastUpdateDatetime, DeleteDatetime, LastUpdate_UserID, 'Added'
            FROM legal 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, legalId);
            ps.executeUpdate();
        }
    }

    // Helper methods للحصول على الأسماء
    private String getLegalEntityName(int legalId) throws SQLException {
        String sql = "SELECT ShortName FROM legal WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, legalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("ShortName");
            }
        }
        return null;
    }

    private String getStatusPrimaryName(int statusId) throws SQLException {
        //system.out.println("LegalDAO: getStatusPrimaryName called with statusId: " + statusId);
        
        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT PrimaryName FROM status WHERE ID = ?";
            //system.out.println("LegalDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("PrimaryName");
                        //system.out.println("LegalDAO: Found PrimaryName: " + result);
                        return result;
                    } else {
                        //system.out.println("LegalDAO: No record found for statusId: " + statusId);
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("LegalDAO: Error with PrimaryName - " + e.getMessage());
        }
        
        // Try primaryname as fallback
        try {
            String sql = "SELECT primaryname FROM status WHERE ID = ?";
            //system.out.println("LegalDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("primaryname");
                        //system.out.println("LegalDAO: Found primaryname: " + result);
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("LegalDAO: Error with primaryname - " + e.getMessage());
        }
        
        //system.out.println("LegalDAO: Using fallback for statusId: " + statusId);
        return "Status " + statusId; // Fallback
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

    private String getViewingName(int viewingId) throws SQLException {
        //system.out.println("LegalDAO: getViewingName called with viewingId: " + viewingId);
        
        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT Name FROM viewing WHERE id = ?";
            //system.out.println("LegalDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, viewingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("PrimaryName");
                        //system.out.println("LegalDAO: Found PrimaryName: " + result);
                        return result;
                    } else {
                        //system.out.println("LegalDAO: No record found for viewingId: " + viewingId);
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("LegalDAO: Error with PrimaryName - " + e.getMessage());
        }
        
        // Try Name as fallback
        try {
            String sql = "SELECT Name FROM viewing WHERE id = ?";
            //system.out.println("LegalDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, viewingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("Name");
                        //system.out.println("LegalDAO: Found Name: " + result);
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("LegalDAO: Error with Name - " + e.getMessage());
        }
        
        //system.out.println("LegalDAO: Using fallback for viewingId: " + viewingId);
        return "Viewing " + viewingId; // Fallback
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إضافته للـ legal entity
     * يتم استدعاء هذا method عند إضافة stakeholder جديد
     */
    public void createStakeholderAuditRecord(int legalId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. إعداد audit statement للـ stakeholder
            String auditSql = """
                INSERT INTO legal_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Stakeholder', 'link', ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // 2. إدراج 3 سجلات للـ stakeholder
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleId(legalId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }
            
            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getRoleName(actualRoleId);
            if (roleName == null) roleName = "Legal Entity Owner"; // fallback
            
            // Role
            createNewAuditRecord(conn, auditStmt, legalId, "Added", "Role", roleName, userName);
            
            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusId(legalId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(statusId);
                if (fetchedStatusName != null) statusName = fetchedStatusName;
            }
            createNewAuditRecord(conn, auditStmt, legalId, "Added", "Role Status", statusName, userName);
            
            // Name
            createNewAuditRecord(conn, auditStmt, legalId, "Added", "Name", userFullName, userName);
            
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


    private String getRoleName(int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }
    
    // Get actual roleID from object_x_people for the stakeholder
    private Integer getStakeholderRoleId(int legalId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN legal_x_objectxpeople lxo ON lxo.Object_X_ip = oxp.ID " +
                    "WHERE lxo.Legal_ID = ? " +
                    "ORDER BY lxo.id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, legalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }
    
    // Get statusID from object_x_people for the stakeholder
    private Integer getStakeholderStatusId(int legalId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN legal_x_objectxpeople lxo ON lxo.Object_X_ip = oxp.ID " +
                    "WHERE lxo.Legal_ID = ? " +
                    "ORDER BY lxo.id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, legalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("statusID");
            }
        }
        return null;
    }
    
    // Get status name from object_x_ip_status by statusID
    private String getStatusNameById(int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM object_x_ip_status WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * إنشاء audit records عند تحديث الـ legal entity
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    public void createLegalUpdateAuditRecords(int legalId, Legal oldLegal, Legal newLegal, String userName) throws SQLException {
        //system.out.println("🔍 LegalDAO.createLegalUpdateAuditRecords - START for ID: " + legalId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO legal_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Short Name
            if (!isEqual(oldLegal.getShortName(), newLegal.getShortName())) {
                createUpdateAuditRecord(conn, auditStmt, legalId, "Legal", "Details", 
                    "Updated", "Short Name", oldLegal.getShortName(), newLegal.getShortName(), userName);
            }
            
            // Long Name
            if (!isEqual(oldLegal.getLongName(), newLegal.getLongName())) {
                createUpdateAuditRecord(conn, auditStmt, legalId, "Legal", "Details", 
                    "Updated", "Long Name", oldLegal.getLongName(), newLegal.getLongName(), userName);
            }
            
            // Description
            if (!isEqual(oldLegal.getDescription(), newLegal.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, legalId, "Legal", "Details", 
                    "Updated", "Description", oldLegal.getDescription(), newLegal.getDescription(), userName);
            }
            
            // Parent Legal Entity
            if (!isEqual(oldLegal.getParentId(), newLegal.getParentId())) {
                String oldParentName = oldLegal.getParentId() != null ? getLegalEntityName(oldLegal.getParentId()) : null;
                String newParentName = newLegal.getParentId() != null ? getLegalEntityName(newLegal.getParentId()) : null;
                createUpdateAuditRecord(conn, auditStmt, legalId, "Legal", "Details", 
                    "Updated", "Parent Legal Entity", oldParentName, newParentName, userName);
            }
            
            // Status (يستخدم "Status Change" كـ updateType)
            if (!isEqual(oldLegal.getStatus(), newLegal.getStatus())) {
                String oldStatusName = oldLegal.getStatus() != null ? getStatusPrimaryName(oldLegal.getStatus()) : null;
                String newStatusName = newLegal.getStatus() != null ? getStatusPrimaryName(newLegal.getStatus()) : null;
                createUpdateAuditRecord(conn, auditStmt, legalId, "Legal", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
            // Is Public
            if (!isEqual(oldLegal.getIsPublic(), newLegal.getIsPublic())) {
                String oldIsPublicName = oldLegal.getIsPublic() != null ? getViewingName(oldLegal.getIsPublic()) : null;
                String newIsPublicName = newLegal.getIsPublic() != null ? getViewingName(newLegal.getIsPublic()) : null;
                createUpdateAuditRecord(conn, auditStmt, legalId, "Legal", "Details", 
                    "Updated", "Is Public", oldIsPublicName, newIsPublicName, userName);
            }
            
            conn.commit();
            //system.out.println("✅ LegalDAO.createLegalUpdateAuditRecords - COMMITTED successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ LegalDAO.createLegalUpdateAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
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
     * Helper method لإنشاء audit record للـ update مع from و to
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, 
            int legalId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, legalId);
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
     * Method لإنشاء snapshot جديد في legal_audit عند الـ update
     */
    public void createLegalUpdateAuditSnapshot(int legalId) throws SQLException {
        //system.out.println("🔍 LegalDAO.createLegalUpdateAuditSnapshot - Creating update snapshot for ID: " + legalId);
        String sql = """
            INSERT INTO legal_audit (
                ID, Parent_ID, Status, Is_Public, ShortName, LongName, Description, 
                CreateDatetime, LastUpdateDatetime, DeleteDatetime, LastUpdate_UserID, RevType
            )
            SELECT 
                ID, Parent_ID, Status, Is_Public, ShortName, LongName, Description, 
                CreateDatetime, LastUpdateDatetime, DeleteDatetime, LastUpdate_UserID, 'Updated'
            FROM legal 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, legalId);
            ps.executeUpdate();
            //system.out.println("✅ Update snapshot created, rows affected: " + rows);
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Helper method للمقارنة بين القيم (يتعامل مع null)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.equals(obj2);
    }

    /**
     * حذف Legal Entity مع تسجيل audit records
     */
    public boolean deleteLegalWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE legal SET DeleteDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO legal_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Legal");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Legal");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في legal_audit
                String snapshotSql = """
                    INSERT INTO legal_audit (
                        ID, Parent_ID, Status, Is_Public, ShortName, LongName, Description, 
                        CreateDatetime, LastUpdateDatetime, DeleteDatetime, LastUpdate_UserID, RevType
                    )
                    SELECT 
                        ID, Parent_ID, Status, Is_Public, ShortName, LongName, Description, 
                        CreateDatetime, LastUpdateDatetime, DeleteDatetime, LastUpdate_UserID, 'Deleted'
                    FROM legal 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Legal deleted with audit for ID: " + id);
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

    /**
     * Create object_x_people record for stakeholder
     * Always creates a NEW record (no reuse)
     */
    public int createObjectXPeople(Connection conn, java.util.Map<String, Object> stakeholder, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
            VALUES (NULL, ?, ?, 2, 1, NOW(), ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, (Integer) stakeholder.get("userId")); // ipid
            ps.setInt(2, (Integer) stakeholder.get("roleId")); // RoleID
            ps.setInt(3, currentUserId); // lastupdateuser_id

            ps.executeUpdate();
            //system.out.println("✅ Created object_x_people record, rows affected=" + rowsAffected);

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    //system.out.println("✅ Generated object_x_people ID: " + newId);
                    return newId;
                } else {
                    throw new SQLException("Creating object_x_people failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Link stakeholder to legal via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToLegal(Connection conn, int legalId, int objectXPeopleId, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO legal_x_objectxpeople (Legal_ID, Object_X_IP, LastUpdate_UserID, CreateDatetime)
            VALUES (?, ?, ?, NOW())
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, legalId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, currentUserId);
            try {
                ps.executeUpdate();
                //system.out.println("✅ Successfully linked stakeholder to legal: LegalID=" + legalId + ", Object_x_ipid=" + objectXPeopleId + ", rows affected=" + rowsAffected);
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: LegalID=" + legalId + ", Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }
}
