package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;

public class BusinessAreaDAO {

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int businessAreaId, String object, String event, String updateType, String field, String value, String userName) throws SQLException {
        auditStmt.setInt(1, businessAreaId);        // id
        auditStmt.setString(2, object);             // object
        auditStmt.setString(3, event);              // event
        auditStmt.setString(4, updateType);         // updateType
        auditStmt.setString(5, field);              // field
        auditStmt.setString(6, value);              // to
        auditStmt.setString(7, userName);           // author
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * إنشاء audit records للـ business area الجديد
     * يتم استدعاء هذا method بعد إنشاء الـ business area بنجاح
     * @param businessAreaId The business area ID
     * @param userName The user name for audit
     * @param conn The database connection to use (must be part of an active transaction)
     */
    public void createBusinessAreaAuditRecords(int businessAreaId, String userName, Connection conn) throws SQLException {
        PreparedStatement auditStmt = null;
        
        try {
            // 1. الحصول على بيانات الـ business area
            String businessAreaDataSql = "SELECT * FROM business_area WHERE ID = ?";
            PreparedStatement businessAreaStmt = conn.prepareStatement(businessAreaDataSql);
            businessAreaStmt.setInt(1, businessAreaId);
            ResultSet businessAreaRs = businessAreaStmt.executeQuery();
            
            if (!businessAreaRs.next()) {
                throw new SQLException("Business area not found with ID: " + businessAreaId);
            }

            // 2. إعداد audit statement
            String auditSql = """
                INSERT INTO business_area_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            String primaryName = businessAreaRs.getString("PrimaryName");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", "Added", "Primary Name", primaryName, userName);
            }
            
            // Description
            String description = businessAreaRs.getString("Description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", "Added", "Description", description, userName);
            }
            
            // Parent Business Area (store Name for readability)
            Integer parentId = businessAreaRs.getObject("Parent_ID", Integer.class);
            if (parentId != null) {
                String parentName = getBusinessAreaName(conn, parentId);
                if (parentName != null) {
                    createNewAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", "Added", "Parent Business Area", parentName, userName);
                }
            }
            
            // Status (store Name for readability)
            Integer statusId = businessAreaRs.getObject("Status", Integer.class);
            if (statusId != null) {
                String statusName = getStatusPrimaryName(conn, statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", "Status Change", "Status", statusName, userName);
                }
            }
            
            // Lifecycle (store Name for readability)
            Integer lifecycleId = businessAreaRs.getObject("Lifecycle", Integer.class);
            if (lifecycleId != null) {
                String lifecycleName = getBusinessAreaLifecycleName(conn, lifecycleId);
                if (lifecycleName != null) {
                    createNewAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", "Added", "Lifecycle", lifecycleName, userName);
                }
            }
            
            // Is Public (store Name for readability)
            Integer isPublicId = businessAreaRs.getObject("Is_Public", Integer.class);
            if (isPublicId != null) {
                String isPublicName = getViewingName(conn, isPublicId);
                if (isPublicName != null) {
                    createNewAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", "Added", "Is Public", isPublicName, userName);
                }
            }
            
            // Created By
            Integer createdById = businessAreaRs.getObject("LastUpdate_UserID", Integer.class);
            if (createdById != null) {
                String createdByName = getPersonFullName(conn, createdById);
                if (createdByName != null) {
                    createNewAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", "Added", "Created By", createdByName, userName);
                }
            }
            
            // Note: Transaction commit is managed by the caller
            
        } finally {
            // تنظيف الموارد (but don't close the connection - it's managed by the caller)
            if (auditStmt != null) auditStmt.close();
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
     * Link stakeholder to business area via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToBusinessArea(Connection conn, int businessAreaId, int objectXPeopleId, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO businessarea_x_objectxpeople (Object_x_ipid, BusinessAreaID, Last_UpdateUser_ID)
            VALUES (?, ?, ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXPeopleId);
            ps.setInt(2, businessAreaId);
            ps.setInt(3, currentUserId);
            try {
                ps.executeUpdate();
                //system.out.println("✅ Successfully linked stakeholder to business area: BusinessAreaID=" + businessAreaId + ", Object_x_ipid=" + objectXPeopleId + ", rows affected=" + rowsAffected);
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: BusinessAreaID=" + businessAreaId + ", Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إنشاء الـ business area
     * يتم استدعاء هذا method بعد إنشاء الـ business area بنجاح
     */
    public void createStakeholderAuditRecords(int businessAreaId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. إعداد audit statement للـ stakeholder
            String auditSql = """
                INSERT INTO business_area_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // 2. إدراج 3 سجلات للـ stakeholder
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleId(businessAreaId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }
            
            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getRoleName(actualRoleId);
            if (roleName == null) roleName = "Business Area Owner"; // fallback
            
            // Role
            createNewAuditRecord(conn, auditStmt, businessAreaId, "Stakeholder", "link", "Added", "Role", roleName, userName);
            
            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusId(businessAreaId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(statusId);
                if (fetchedStatusName != null) statusName = fetchedStatusName;
            }
            createNewAuditRecord(conn, auditStmt, businessAreaId, "Stakeholder", "link", "Added", "Role Status", statusName, userName);
            
            // Name
            createNewAuditRecord(conn, auditStmt, businessAreaId, "Stakeholder", "link", "Added", "Name", userFullName, userName);
            
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
     * إنشاء سجل في جدول business_area_audit بعد إنشاء الـ business area
     * يتم استدعاء هذا method بعد إنشاء الـ business area بنجاح
     */
    public void createBusinessAreaAuditRecord(int businessAreaId) throws SQLException {
        String sql = """
            INSERT INTO business_area_audit (
                ID, Parent_ID, Is_Public, Status, Lifecycle, PrimaryName, Description,
                CreateDatetime, LastUpdateDatetime, createdby_id, LastUpdate_UserID, RevType
            )
            SELECT 
                ID, Parent_ID, Is_Public, Status, Lifecycle, PrimaryName, Description,
                CreateDatetime, LastUpdateDatetime, createdby_id, LastUpdate_UserID, 'Added'
            FROM business_area 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, businessAreaId);
            ps.executeUpdate();
        }
    }

    // Helper methods للحصول على الأسماء
    private String getBusinessAreaName(int businessAreaId) throws SQLException {
        String sql = "SELECT PrimaryName FROM business_area WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, businessAreaId);
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

    private String getBusinessAreaLifecycleName(int lifecycleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM business_area_lifecycle WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getViewingName(int viewingId) throws SQLException {
        String sql = "SELECT Name FROM viewing WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, viewingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    // Overloaded helper methods that use existing connection (to avoid connection leaks in transactions)
    private String getBusinessAreaName(Connection conn, int businessAreaId) throws SQLException {
        String sql = "SELECT PrimaryName FROM business_area WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, businessAreaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getStatusPrimaryName(Connection conn, int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM status WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    private String getBusinessAreaLifecycleName(Connection conn, int lifecycleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM business_area_lifecycle WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getViewingName(Connection conn, int viewingId) throws SQLException {
        String sql = "SELECT Name FROM viewing WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, viewingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
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

    // Overloaded helper method that uses existing connection (to avoid connection leaks in transactions)
    private String getPersonFullName(Connection conn, int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return null;
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
    private Integer getStakeholderRoleId(int businessAreaId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN businessarea_x_objectxpeople bx ON bx.Object_x_ipid = oxp.ID " +
                    "WHERE bx.BusinessAreaID = ? " +
                    "ORDER BY bx.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, businessAreaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }
    
    // Get statusID from object_x_people for the stakeholder
    private Integer getStakeholderStatusId(int businessAreaId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN businessarea_x_objectxpeople bx ON bx.Object_x_ipid = oxp.ID " +
                    "WHERE bx.BusinessAreaID = ? " +
                    "ORDER BY bx.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, businessAreaId);
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

    // ============================================
    // UPDATE AUDIT METHODS
    // ============================================

    /**
     * Create audit records when updating a business area
     * Compares old and new values and logs the differences
     */
    public void createBusinessAreaUpdateAuditRecords(int businessAreaId, 
            com.example.budg_v2.model.BusinessArea oldBusinessArea, 
            com.example.budg_v2.model.BusinessArea newBusinessArea, 
            String userName) throws SQLException {
        //system.out.println("🔍 BusinessAreaDAO.createBusinessAreaUpdateAuditRecords - START for ID: " + businessAreaId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO business_area_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldBusinessArea.getPrimaryName(), newBusinessArea.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", 
                    "Updated", "Primary Name", oldBusinessArea.getPrimaryName(), newBusinessArea.getPrimaryName(), userName);
            }
            
            // Description
            if (!isEqual(oldBusinessArea.getDescription(), newBusinessArea.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", 
                    "Updated", "Description", oldBusinessArea.getDescription(), newBusinessArea.getDescription(), userName);
            }
            
            // Parent Business Area (store Name for readability)
            if (!isEqual(oldBusinessArea.getParentId(), newBusinessArea.getParentId())) {
                String oldParentName = oldBusinessArea.getParentId() != null ? getBusinessAreaName(conn, oldBusinessArea.getParentId()) : null;
                String newParentName = newBusinessArea.getParentId() != null ? getBusinessAreaName(conn, newBusinessArea.getParentId()) : null;
                createUpdateAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", 
                    "Updated", "Parent Business Area", oldParentName, newParentName, userName);
            }
            
            // Status (uses "Status Change" as updateType, store Name)
            if (!isEqual(oldBusinessArea.getStatus(), newBusinessArea.getStatus())) {
                String oldStatusName = oldBusinessArea.getStatus() != null ? getStatusPrimaryName(conn, oldBusinessArea.getStatus()) : null;
                String newStatusName = newBusinessArea.getStatus() != null ? getStatusPrimaryName(conn, newBusinessArea.getStatus()) : null;
                createUpdateAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
            // Lifecycle (uses "Status Change" as updateType, store Name)
            if (!isEqual(oldBusinessArea.getLifecycle(), newBusinessArea.getLifecycle())) {
                String oldLifecycleName = oldBusinessArea.getLifecycle() != null ? getBusinessAreaLifecycleName(conn, oldBusinessArea.getLifecycle()) : null;
                String newLifecycleName = newBusinessArea.getLifecycle() != null ? getBusinessAreaLifecycleName(conn, newBusinessArea.getLifecycle()) : null;
                createUpdateAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", 
                    "Status Change", "Lifecycle", oldLifecycleName, newLifecycleName, userName);
            }
            
            // Is Public (uses "Status Change" as updateType, store Name)
            if (!isEqual(oldBusinessArea.getIsPublic(), newBusinessArea.getIsPublic())) {
                String oldIsPublicName = oldBusinessArea.getIsPublic() != null ? getViewingName(conn, oldBusinessArea.getIsPublic()) : null;
                String newIsPublicName = newBusinessArea.getIsPublic() != null ? getViewingName(conn, newBusinessArea.getIsPublic()) : null;
                createUpdateAuditRecord(conn, auditStmt, businessAreaId, "Business Area", "Details", 
                    "Status Change", "Is Public", oldIsPublicName, newIsPublicName, userName);
            }
            
            conn.commit();
            //system.out.println("✅ BusinessAreaDAO.createBusinessAreaUpdateAuditRecords - COMPLETED");
            
        } catch (SQLException e) {
            System.err.println("❌ Error in createBusinessAreaUpdateAuditRecords: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("❌ Error rolling back: " + rollbackEx.getMessage());
                }
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
     * Method to create a new snapshot in business_area_audit when updating
     */
    public void createBusinessAreaUpdateAuditSnapshot(int businessAreaId) throws SQLException {
        //system.out.println("🔍 BusinessAreaDAO.createBusinessAreaUpdateAuditSnapshot - Creating update snapshot for ID: " + businessAreaId);
        String sql = """
            INSERT INTO business_area_audit (
                ID, Parent_ID, Is_Public, Status, Lifecycle, PrimaryName, Description,
                CreateDatetime, LastUpdateDatetime, createdby_id, LastUpdate_UserID, RevType
            )
            SELECT 
                ID, Parent_ID, Is_Public, Status, Lifecycle, PrimaryName, Description,
                CreateDatetime, LastUpdateDatetime, createdby_id, LastUpdate_UserID, 'Updated'
            FROM business_area 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, businessAreaId);
            ps.executeUpdate();
            //system.out.println("✅ Update snapshot created, rows affected: " + rows);
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Helper method to create individual audit record for update with from and to values
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, 
            int businessAreaId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, businessAreaId);
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
     * Helper method to normalize values (convert null, empty string, and 0 to null)
     */
    private String normalizeValue(Object value) {
        if (value == null) return null;
        
        // Handle Integer with value 0 (considered null in foreign key context)
        if (value instanceof Integer && ((Integer) value) == 0) {
            return null;
        }
        
        String strValue = value.toString().trim();
        // Convert empty string, "null" string, and "0" string to null
        if (strValue.isEmpty() || strValue.equalsIgnoreCase("null") || strValue.equals("0")) {
            return null;
        }
        return strValue;
    }

    /**
     * Helper method for safe comparison between values (handles null, empty strings, and 0)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        // Normalize values first
        String normalized1 = normalizeValue(obj1);
        String normalized2 = normalizeValue(obj2);
        
        // Compare after normalization
        if (normalized1 == null && normalized2 == null) return true;
        if (normalized1 == null || normalized2 == null) return false;
        return normalized1.equals(normalized2);
    }

    /**
     * حذف مجال الأعمال مع تسجيل audit records
     */
    public boolean deleteBusinessAreaWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE business_area SET deletedatetime	 = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO business_area_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Business Area");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Business Area");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في business_area_audit
                String snapshotSql = """
                    INSERT INTO business_area_audit (
                        ID, Parent_ID, Is_Public, Classification, Status, RefNumber, PrimaryName,
                        Description, CreateDatetime, LastUpdateDatetime, DeleteDatetime, 
                        LastUpdate_UserID, Created_By, RevType
                    )
                    SELECT 
                        ID, Parent_ID, Is_Public, Classification, Status, RefNumber, PrimaryName,
                        Description, CreateDatetime, LastUpdateDatetime, DeleteDatetime,
                        LastUpdate_UserID, Created_By, 'Deleted'
                    FROM business_area 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Business Area deleted with audit for ID: " + id);
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

