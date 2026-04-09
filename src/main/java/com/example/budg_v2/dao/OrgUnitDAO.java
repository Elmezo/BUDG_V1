package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.OrgUnit;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.ReferenceNumberGenerator;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class OrgUnitDAO {

    public List<OrgUnit> getAllOrgUnits() throws SQLException {
        List<OrgUnit> orgUnits = new ArrayList<>();
        String sql = "SELECT * FROM org_unit WHERE (deleted_Date IS NULL OR deleted_Date = '') ORDER BY ID";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                OrgUnit orgUnit = mapResultSetToOrgUnit(rs);
                orgUnits.add(orgUnit);
            }
        }
        return orgUnits;
    }

    /**
     * Get all org units filtered by user's segment access
     */
    public List<OrgUnit> getAllOrgUnits(int userId) throws SQLException {
        List<OrgUnit> allOrgUnits = getAllOrgUnits();
        if (allOrgUnits.isEmpty()) {
            return allOrgUnits;
        }
        
        // Get accessible org unit IDs for this user (filtered by selected segments)
        List<Integer> allIds = allOrgUnits.stream().map(OrgUnit::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "OrgUnit", allIds);
        
        // Filter to only accessible org units
        return allOrgUnits.stream()
                .filter(ou -> accessibleIds.contains(ou.getId()))
                .collect(Collectors.toList());
    }

    public OrgUnit getOrgUnitById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT ou.*, s.primaryname AS statusName FROM org_unit ou " +
                             "LEFT JOIN status s ON s.ID = ou.status_id " +
                             "WHERE ou.ID = ?")) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToOrgUnit(rs);
            }
        }
        return null;
    }

    public OrgUnit getOrgUnitByName(String name) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM org_unit WHERE LOWER(Name) = LOWER(?)")) {

            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToOrgUnit(rs);
            }
        }

        return null;
    }

    public OrgUnit getOrgUnitByReference(String reference) throws SQLException {
        if (reference == null || reference.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM org_unit WHERE Reference = ?")) {

            pstmt.setString(1, reference.trim());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToOrgUnit(rs);
            }
        }
        return null;
    }

    public List<OrgUnit> searchOrgUnits(String searchQuery) throws SQLException {
        List<OrgUnit> orgUnits = new ArrayList<>();
        String sql = "SELECT * FROM org_unit WHERE (deleted_Date IS NULL OR deleted_Date = '') "
                + "AND (Name LIKE ? OR Description LIKE ?) ORDER BY ID";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                OrgUnit orgUnit = mapResultSetToOrgUnit(rs);
                orgUnits.add(orgUnit);
            }
        }
        return orgUnits;
    }

    public int createOrgUnit(OrgUnit orgUnit, int userId) throws SQLException {
        // Auto-generate Reference if empty
        if (ReferenceNumberGenerator.isEmpty(orgUnit.getReference())) {
            orgUnit.setReference(ReferenceNumberGenerator.generateOrgUnitReference());
        }
        
		try (Connection conn = DatabaseConnection.getConnection();
		     PreparedStatement pstmt = conn.prepareStatement(
				     "INSERT INTO org_unit (Reference, Name, Description, Parent_ID, status_id, Created_Date, last_updated_date, lastupdateuser_id) VALUES (?, ?, ?, ?, ?, NOW(), NULL, ?)",
				     Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, orgUnit.getReference());
            pstmt.setString(2, orgUnit.getName());
            pstmt.setString(3, orgUnit.getDescription() != null ? orgUnit.getDescription() : "");

            if (orgUnit.getParentId() != null) {
                pstmt.setInt(4, orgUnit.getParentId());
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }

            pstmt.setInt(5, orgUnit.getStatusId() != null ? orgUnit.getStatusId() : 1);
            pstmt.setInt(6, userId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating organization unit failed, no rows affected.");
            }

            ResultSet generatedKeys = pstmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                int newOrgUnitId = generatedKeys.getInt(1);
                
                // Create org unit audit records
                try {
                    String userName = getPersonFullName(userId);
                    if (userName != null) {
                        createOrgUnitAuditRecords(newOrgUnitId, userName);
                        //system.out.println("✅ OrgUnit audit records created for ID: " + newOrgUnitId);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating org unit audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the main operation if audit fails
                }

                // Create org unit audit snapshot (main audit table)
                try {
                    createOrgUnitAuditRecord(newOrgUnitId);
                    //system.out.println("✅ OrgUnitDAO: org_unit_audit snapshot created for ID: " + newOrgUnitId);
                } catch (Exception e) {
                    System.err.println("❌ Error creating org_unit_audit snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
                
                return newOrgUnitId;
            } else {
                throw new SQLException("Creating organization unit failed, no ID obtained.");
            }
        }
    }

    public boolean updateOrgUnit(int id, OrgUnit orgUnit, int userId) throws SQLException {
        // Auto-generate Reference if empty
        if (ReferenceNumberGenerator.isEmpty(orgUnit.getReference())) {
            orgUnit.setReference(ReferenceNumberGenerator.generateOrgUnitReference());
        }
        
        // الخطوة 1: احصل على البيانات القديمة قبل التحديث
        OrgUnit oldOrgUnit = getOrgUnitById(id);
        if (oldOrgUnit == null) {
            throw new SQLException("OrgUnit not found with ID: " + id);
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "UPDATE org_unit SET Reference = ?, Name = ?, Description = ?, Parent_ID = ?, status_id = ?, last_updated_date = NOW(), lastupdateuser_id = ? WHERE ID = ?")) {

            pstmt.setString(1, orgUnit.getReference());
            pstmt.setString(2, orgUnit.getName());
            pstmt.setString(3, orgUnit.getDescription() != null ? orgUnit.getDescription() : "");

            if (orgUnit.getParentId() != null) {
                pstmt.setInt(4, orgUnit.getParentId());
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }

            pstmt.setInt(5, orgUnit.getStatusId() != null ? orgUnit.getStatusId() : 1);
            pstmt.setInt(6, userId);
            pstmt.setInt(7, id);

            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات
                try {
                    String userName = getPersonFullName(userId);
                    if (userName != null) {
                        createOrgUnitUpdateAuditRecords(id, oldOrgUnit, orgUnit, userName);
                        //system.out.println("✅ OrgUnit update audit records created for ID: " + id);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating org unit update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // الخطوة 3: إنشاء snapshot جديد في org_unit_audit
                try {
                    createOrgUnitUpdateAuditSnapshot(id);
                    //system.out.println("✅ OrgUnitDAO: org_unit_audit update snapshot created for ID: " + id);
                } catch (Exception e) {
                    System.err.println("❌ Error creating org_unit_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return affectedRows > 0;
        }
    }


    public boolean deleteOrgUnit(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM org_unit WHERE ID = ?")) {

            pstmt.setInt(1, id);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }


    private OrgUnit mapResultSetToOrgUnit(ResultSet rs) throws SQLException {
        OrgUnit orgUnit = new OrgUnit();

        orgUnit.setId(rs.getInt("ID"));
        orgUnit.setReference(rs.getString("Reference"));
        orgUnit.setName(rs.getString("Name"));
        orgUnit.setDescription(rs.getString("Description"));

        int parentId = rs.getInt("Parent_ID");
        if (!rs.wasNull()) {
            orgUnit.setParentId(parentId);
        }

        orgUnit.setStatusId(rs.getInt("status_id"));
        try {
            orgUnit.setStatusName(rs.getString("statusName"));
        } catch (SQLException e) {
            orgUnit.setStatusName(null);
        }

        Timestamp createdDate = rs.getTimestamp("Created_Date");
        if (!rs.wasNull()) {
            orgUnit.setCreatedDate(createdDate);
        }

        Timestamp lastUpdatedDate = rs.getTimestamp("last_updated_date");
        if (!rs.wasNull()) {
            orgUnit.setLastUpdatedDate(lastUpdatedDate);
        }

        Timestamp deletedDate = rs.getTimestamp("deleted_Date");
        if (!rs.wasNull()) {
            orgUnit.setDeletedDate(deletedDate);
        }

        int lastUpdateUserId = rs.getInt("lastupdateuser_ID");
        if (!rs.wasNull()) {
            orgUnit.setLastUpdateUserId(lastUpdateUserId);
        }

        return orgUnit;
    }

    /**
     * إنشاء audit records للـ org_unit الجديد
     * يتم استدعاء هذا method بعد إنشاء الـ org_unit بنجاح
     */
    public void createOrgUnitAuditRecords(int orgUnitId, String userName) throws SQLException {
        //system.out.println("🔍 OrgUnitDAO.createOrgUnitAuditRecords - START for ID: " + orgUnitId + ", userName: " + userName);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            //system.out.println("✓ Connection established, autoCommit set to false");
            
            // Get org_unit data
            String orgUnitDataSql = "SELECT * FROM org_unit WHERE ID = ?";
            PreparedStatement orgUnitStmt = conn.prepareStatement(orgUnitDataSql);
            orgUnitStmt.setInt(1, orgUnitId);
            ResultSet orgUnitRs = orgUnitStmt.executeQuery();
            
            if (!orgUnitRs.next()) {
                throw new SQLException("OrgUnit not found with ID: " + orgUnitId);
            }
            //system.out.println("✓ OrgUnit data retrieved successfully");

            // Prepare audit statement
            String auditSql = """
                INSERT INTO orgunit_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            String name = orgUnitRs.getString("Name");
            if (name != null && !name.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, orgUnitId, "Org Unit", "Details", "Added", "Primary Name", name, userName);
            }
            
            // Ref Number
            String refNumber = orgUnitRs.getString("Reference");
            if (refNumber != null && !refNumber.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, orgUnitId, "Org Unit", "Details", "Added", "Ref Number", refNumber, userName);
            }
            
            // Description
            String description = orgUnitRs.getString("Description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, orgUnitId, "Org Unit", "Details", "Added", "Description", description, userName);
            }
            
            // Parent Org Unit
            Integer parentId = orgUnitRs.getObject("Parent_ID", Integer.class);
            if (parentId != null) {
                String parentName = getOrgUnitName(parentId);
                if (parentName != null) {
                    createNewAuditRecord(conn, auditStmt, orgUnitId, "Org Unit", "Details", "Added", "Parent Org Unit", parentName, userName);
                }
            }
            
            // Status
            Integer statusId = orgUnitRs.getObject("status_id", Integer.class);
            if (statusId != null) {
                String statusName = getStatusPrimaryName(statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, orgUnitId, "Org Unit", "Details", "Status Change", "Status", statusName, userName);
                }
            }
            
            // Created By
            Integer createdById = orgUnitRs.getObject("lastupdateuser_id", Integer.class);
            if (createdById != null) {
                String createdByName = getPersonFullName(createdById);
                if (createdByName != null) {
                    createNewAuditRecord(conn, auditStmt, orgUnitId, "Org Unit", "Details", "Added", "Created By", createdByName, userName);
                }
            }
            
            conn.commit();
            //system.out.println("✅ OrgUnitDAO.createOrgUnitAuditRecords - COMMITTED successfully for ID: " + orgUnitId);
            
        } catch (SQLException e) {
            System.err.println("❌ OrgUnitDAO.createOrgUnitAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                conn.rollback();
                //system.out.println("↩️ Transaction rolled back");
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
     * إنشاء سجل في جدول org_unit_audit بعد إنشاء الـ org_unit
     * يتم استدعاء هذا method بعد إنشاء الـ org_unit بنجاح
     */
    public void createOrgUnitAuditRecord(int orgUnitId) throws SQLException {
        //system.out.println("🔍 OrgUnitDAO.createOrgUnitAuditRecord - Creating snapshot for ID: " + orgUnitId);
        String sql = """
            INSERT INTO org_unit_audit (
                id, parent_id, status, primaryname, description, refnumber,
                createdatetime, lastupdatedatetime, deleteddatetime, lastupdateuser_id, rev_type
            )
            SELECT 
                ID, Parent_ID, status_id, Name, Description, Reference,
                Created_Date, last_updated_date, deleted_Date, lastupdateuser_ID, 'Added'
            FROM org_unit 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orgUnitId);
            ps.executeUpdate();
            //system.out.println("✅ OrgUnitDAO.createOrgUnitAuditRecord - Snapshot created, rows affected: " + rows);
        } catch (SQLException e) {
            System.err.println("❌ OrgUnitDAO.createOrgUnitAuditRecord - ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int orgUnitId, String object, String event, String updateType, String field, String value, String userName) throws SQLException {
        //system.out.println("    📝 Creating audit record: [" + object + "][" + event + "][" + field + "] = " + value);
        auditStmt.setInt(1, orgUnitId);      // id
        auditStmt.setString(2, object);       // object
        auditStmt.setString(3, event);        // event
        auditStmt.setString(4, updateType);   // updateType
        auditStmt.setString(5, field);        // field
        auditStmt.setString(6, value);        // to
        auditStmt.setString(7, userName);     // author
        
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                int auditId = generatedKeys.getInt(1);
                //system.out.println("    ✓ Generated auditidpk: " + auditId);
                return auditId;
            }
        }
        //system.out.println("    ⚠️ No generated key returned");
        return -1;
    }

    private String getOrgUnitName(int orgUnitId) throws SQLException {
        String sql = "SELECT Name FROM org_unit WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orgUnitId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getStatusPrimaryName(int statusId) throws SQLException {
        String sql = "SELECT PrimaryName FROM status WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
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
     * إنشاء audit records عند تحديث الـ org_unit
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    public void createOrgUnitUpdateAuditRecords(int orgUnitId, OrgUnit oldOrgUnit, OrgUnit newOrgUnit, String userName) throws SQLException {
        //system.out.println("🔍 OrgUnitDAO.createOrgUnitUpdateAuditRecords - START for ID: " + orgUnitId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO orgunit_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldOrgUnit.getName(), newOrgUnit.getName())) {
                createUpdateAuditRecord(conn, auditStmt, orgUnitId, "Org Unit", "Details", 
                    "Updated", "Primary Name", oldOrgUnit.getName(), newOrgUnit.getName(), userName);
            }
            
            // Ref Number
            if (!isEqual(oldOrgUnit.getReference(), newOrgUnit.getReference())) {
                createUpdateAuditRecord(conn, auditStmt, orgUnitId, "Org Unit", "Details", 
                    "Updated", "Ref Number", oldOrgUnit.getReference(), newOrgUnit.getReference(), userName);
            }
            
            // Description
            if (!isEqual(oldOrgUnit.getDescription(), newOrgUnit.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, orgUnitId, "Org Unit", "Details", 
                    "Updated", "Description", oldOrgUnit.getDescription(), newOrgUnit.getDescription(), userName);
            }
            
            // Parent Org Unit
            if (!isEqual(oldOrgUnit.getParentId(), newOrgUnit.getParentId())) {
                String oldParentName = oldOrgUnit.getParentId() != null ? getOrgUnitName(oldOrgUnit.getParentId()) : null;
                String newParentName = newOrgUnit.getParentId() != null ? getOrgUnitName(newOrgUnit.getParentId()) : null;
                createUpdateAuditRecord(conn, auditStmt, orgUnitId, "Org Unit", "Details", 
                    "Updated", "Parent Org Unit", oldParentName, newParentName, userName);
            }
            
            // Status (يستخدم "Status Change" كـ updateType)
            if (!isEqual(oldOrgUnit.getStatusId(), newOrgUnit.getStatusId())) {
                String oldStatusName = oldOrgUnit.getStatusId() != null ? getStatusPrimaryName(oldOrgUnit.getStatusId()) : null;
                String newStatusName = newOrgUnit.getStatusId() != null ? getStatusPrimaryName(newOrgUnit.getStatusId()) : null;
                createUpdateAuditRecord(conn, auditStmt, orgUnitId, "Org Unit", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
            conn.commit();
            //system.out.println("✅ OrgUnitDAO.createOrgUnitUpdateAuditRecords - COMMITTED successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ OrgUnitDAO.createOrgUnitUpdateAuditRecords - ERROR: " + e.getMessage());
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
            int orgUnitId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, orgUnitId);
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
     * Method لإنشاء snapshot جديد في org_unit_audit عند الـ update
     */
    public void createOrgUnitUpdateAuditSnapshot(int orgUnitId) throws SQLException {
        //system.out.println("🔍 OrgUnitDAO.createOrgUnitUpdateAuditSnapshot - Creating update snapshot for ID: " + orgUnitId);
        String sql = """
            INSERT INTO org_unit_audit (
                id, parent_id, status, primaryname, description, refnumber,
                createdatetime, lastupdatedatetime, deleteddatetime, lastupdateuser_id, rev_type
            )
            SELECT 
                ID, Parent_ID, status_id, Name, Description, Reference,
                Created_Date, last_updated_date, deleted_Date, lastupdateuser_ID, 'Updated'
            FROM org_unit 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orgUnitId);
            ps.executeUpdate();
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
     * حذف Org Unit مع تسجيل audit records
     */
    public boolean deleteOrgUnitWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE org_unit SET deleted_Date = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO org_unit_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Org Unit");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Org Unit");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في org_unit_audit
                String snapshotSql = """
                    INSERT INTO org_unit_audit (
                        id, parent_id, status, primaryname, description, refnumber,
                        createdatetime, lastupdatedatetime, deleteddatetime, lastupdateuser_id, rev_type
                    )
                    SELECT 
                        ID, Parent_ID, status_id, Name, Description, Reference,
                        Created_Date, last_updated_date, deleted_Date, lastupdateuser_ID, 'Deleted'
                    FROM org_unit 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Org Unit deleted with audit for ID: " + id);
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

}
