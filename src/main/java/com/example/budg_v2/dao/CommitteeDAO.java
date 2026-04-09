package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Committee;

import java.sql.*;

public class CommitteeDAO {

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int committeeId, String object, String event, String updateType, String field, String value, String userName) throws SQLException {
        auditStmt.setInt(1, committeeId);        // id
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
     * إنشاء audit records للجنة الجديدة
     * يتم استدعاء هذا method بعد إنشاء الجنة بنجاح
     */
    public void createCommitteeAuditRecords(int committeeId, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. الحصول على بيانات الجنة
            String committeeDataSql = "SELECT * FROM committee WHERE ID = ?";
            PreparedStatement committeeStmt = conn.prepareStatement(committeeDataSql);
            committeeStmt.setInt(1, committeeId);
            ResultSet committeeRs = committeeStmt.executeQuery();
            
            if (!committeeRs.next()) {
                throw new SQLException("Committee not found with ID: " + committeeId);
            }

            // 2. إعداد audit statement
            String auditSql = """
                INSERT INTO committee_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            String primaryName = committeeRs.getString("PrimaryName");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", "Added", "Primary Name", primaryName, userName);
            }
            
            // Description
            String description = committeeRs.getString("Description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", "Added", "Description", description, userName);
            }
            
            // Reference Number
            String refNumber = committeeRs.getString("RefNumber");
            if (refNumber != null && !refNumber.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", "Added", "Reference Number", refNumber, userName);
            }
            
            // Parent Committee
            Integer parentId = committeeRs.getObject("Parent_ID", Integer.class);
            if (parentId != null) {
                String parentName = getCommitteeName(parentId);
                if (parentName != null) {
                    createNewAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", "Added", "Parent Committee", parentName, userName);
                }
            }
            
            // BUDG Viewing (Is_Public)
            Integer viewingId = committeeRs.getObject("Is_Public", Integer.class);
            if (viewingId != null) {
                String viewingName = getViewingName(viewingId);
                if (viewingName != null) {
                    createNewAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", "Status Change", "BUDG Viewing", viewingName, userName);
                }
            }
            
            // Status
            Integer statusId = committeeRs.getObject("Status", Integer.class);
            if (statusId != null) {
                String statusName = getStatusPrimaryName(statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", "Status Change", "Status", statusName, userName);
                }
            }
            
            // Lifecycle
            Integer lifecycleId = committeeRs.getObject("Lifecycle", Integer.class);
            if (lifecycleId != null) {
                String lifecycleName = getCommitteeLifecycleName(lifecycleId);
                if (lifecycleName != null) {
                    createNewAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", "Status Change", "Lifecycle", lifecycleName, userName);
                }
            }
            
            // Committee Type
            Integer committeeTypeId = committeeRs.getObject("Committee_Type", Integer.class);
            if (committeeTypeId != null) {
                String committeeTypeName = getCommitteeTypeName(committeeTypeId);
                if (committeeTypeName != null) {
                    createNewAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", "Status Change", "Committee Type", committeeTypeName, userName);
                }
            }
            
            // Classification
            Integer classificationId = committeeRs.getObject("Classification", Integer.class);
            if (classificationId != null) {
                String classificationName = getCommitteeClassificationName(classificationId);
                if (classificationName != null) {
                    createNewAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", "Status Change", "Classification", classificationName, userName);
                }
            }
            
            // Created By (use Created_By column when present, else fallback to LastUpdate_UserID)
            Integer createdById = committeeRs.getObject("Created_By", Integer.class);
            if (createdById == null) {
                createdById = committeeRs.getObject("LastUpdate_UserID", Integer.class);
            }
            if (createdById != null) {
                String createdByName = getPersonFullName(createdById);
                if (createdByName != null) {
                    createNewAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", "Added", "Created By", createdByName, userName);
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
     * إنشاء audit records للـ stakeholder بعد إنشاء الجنة
     * يتم استدعاء هذا method بعد إنشاء الجنة بنجاح
     */
    public void createStakeholderAuditRecords(int committeeId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. إعداد audit statement للـ stakeholder
            String auditSql = """
                INSERT INTO committee_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // 2. إدراج 3 سجلات للـ stakeholder
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleId(committeeId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }
            
            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getRoleName(actualRoleId);
            if (roleName == null) roleName = "Committee Owner"; // fallback
            
            // Role
            createNewAuditRecord(conn, auditStmt, committeeId, "Stakeholder", "link", "Added", "Role", roleName, userName);
            
            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusId(committeeId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(statusId);
                if (fetchedStatusName != null) statusName = fetchedStatusName;
            }
            createNewAuditRecord(conn, auditStmt, committeeId, "Stakeholder", "link", "Added", "Role Status", statusName, userName);
            
            // Name
            createNewAuditRecord(conn, auditStmt, committeeId, "Stakeholder", "link", "Added", "Name", userFullName, userName);
            
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
     * إنشاء سجل في جدول committee_audit بعد إنشاء الجنة
     * يتم استدعاء هذا method بعد إنشاء الجنة بنجاح
     */
    public void createCommitteeAuditRecord(int committeeId) throws SQLException {
        String sql = """
            INSERT INTO committee_audit (
                ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Committee_Type,
                RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                DeleteDatetime, LastUpdate_UserID, Created_By, RevType
            )
            SELECT 
                ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Committee_Type,
                RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                DeleteDatetime, LastUpdate_UserID, Created_By, 'Added'
            FROM committee 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, committeeId);
            ps.executeUpdate();
        }
    }

    // Helper methods للحصول على الأسماء
    private String getCommitteeName(int committeeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM committee WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, committeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getCommitteeTypeName(int typeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM committee_type WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
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

    private String getCommitteeLifecycleName(int lifecycleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM committee_lifecycle WHERE ID = ?";
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

    private String getCommitteeClassificationName(int classificationId) throws SQLException {
        String sql = "SELECT PrimaryName FROM committee_classification WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classificationId);
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
    private Integer getStakeholderRoleId(int committeeId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN committee_x_objectxpeople cxo ON cxo.Object_X_ipid = oxp.ID " +
                    "WHERE cxo.Committee_ID = ? " +
                    "ORDER BY cxo.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, committeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }
    
    // Get statusID from object_x_people for the stakeholder
    private Integer getStakeholderStatusId(int committeeId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN committee_x_objectxpeople cxo ON cxo.Object_X_ipid = oxp.ID " +
                    "WHERE cxo.Committee_ID = ? " +
                    "ORDER BY cxo.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, committeeId);
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
     * تحديث الجنة مع تسجيل audit records
     * @param oldCommittee البيانات القديمة للجنة
     * @param newCommittee البيانات الجديدة للجنة
     * @param userName اسم المستخدم الذي قام بالتحديث
     * @return true إذا تم التحديث بنجاح
     */
    public boolean updateCommitteeWithAudit(Committee oldCommittee, Committee newCommittee, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement updateStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // الخطوة 1: تحديث الجدول الأساسي
            String updateSql = """
                UPDATE committee 
                SET Parent_ID = ?, Is_Public = ?, Classification = ?, Status = ?, 
                    Lifecycle = ?, Committee_Type = ?, RefNumber = ?, PrimaryName = ?, 
                    Description = ?, LastUpdate_UserID = ?, LastUpdateDatetime = NOW()
                WHERE ID = ?
            """;
            
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setObject(1, newCommittee.getParentId());
            updateStmt.setObject(2, newCommittee.getIsPublic());
            updateStmt.setObject(3, newCommittee.getClassification());
            updateStmt.setObject(4, newCommittee.getStatus());
            updateStmt.setObject(5, newCommittee.getLifecycle());
            updateStmt.setObject(6, newCommittee.getCommitteeType());
            updateStmt.setString(7, newCommittee.getRefNumber());
            updateStmt.setString(8, newCommittee.getPrimaryName());
            updateStmt.setString(9, newCommittee.getDescription());
            updateStmt.setObject(10, newCommittee.getLastUpdateUserID());
            updateStmt.setInt(11, newCommittee.getId());
            
            int affectedRows = updateStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات - استخدام نفس الـ connection
                try {
                    createCommitteeUpdateAuditRecords(conn, newCommittee.getId(), oldCommittee, newCommittee, userName);
                    //system.out.println("✅ Committee update audit records created for ID: " + newCommittee.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating committee update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // الخطوة 3: إنشاء snapshot جديد في committee_audit - استخدام نفس الـ connection
                try {
                    createCommitteeUpdateAuditSnapshot(conn, newCommittee.getId());
                    //system.out.println("✅ CommitteeDAO: committee_audit update snapshot created for ID: " + newCommittee.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating committee_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            conn.commit(); // تأكيد الـ transaction
            return affectedRows > 0;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback(); // إلغاء الـ transaction في حالة الخطأ
                } catch (SQLException rollbackEx) {
                    System.err.println("❌ Error during rollback: " + rollbackEx.getMessage());
                }
            }
            throw e;
        } finally {
            // تنظيف الموارد
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
     * إنشاء audit records عند تحديث الجنة
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    public void createCommitteeUpdateAuditRecords(Connection conn, int committeeId, Committee oldCommittee, Committee newCommittee, String userName) throws SQLException {
        //system.out.println("🔍 CommitteeDAO.createCommitteeUpdateAuditRecords - START for ID: " + committeeId);
        PreparedStatement auditStmt = null;
        
        try {
            String auditSql = """
                INSERT INTO committee_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldCommittee.getPrimaryName(), newCommittee.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", 
                    "Updated", "Primary Name", oldCommittee.getPrimaryName(), newCommittee.getPrimaryName(), userName);
            }
            
            // Description
            if (!isEqual(oldCommittee.getDescription(), newCommittee.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", 
                    "Updated", "Description", oldCommittee.getDescription(), newCommittee.getDescription(), userName);
            }
            
            // Reference Number
            if (!isEqual(oldCommittee.getRefNumber(), newCommittee.getRefNumber())) {
                createUpdateAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", 
                    "Updated", "Reference Number", oldCommittee.getRefNumber(), newCommittee.getRefNumber(), userName);
            }
            
            // Parent Committee
            if (!isEqual(oldCommittee.getParentId(), newCommittee.getParentId())) {
                String oldParentName = oldCommittee.getParentId() != null ? getCommitteeName(oldCommittee.getParentId()) : null;
                String newParentName = newCommittee.getParentId() != null ? getCommitteeName(newCommittee.getParentId()) : null;
                createUpdateAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", 
                    "Updated", "Parent Committee", oldParentName, newParentName, userName);
            }
            
            // BUDG Viewing (Is_Public)
            if (!isEqual(oldCommittee.getIsPublic(), newCommittee.getIsPublic())) {
                String oldViewingName = oldCommittee.getIsPublic() != null ? getViewingName(oldCommittee.getIsPublic()) : null;
                String newViewingName = newCommittee.getIsPublic() != null ? getViewingName(newCommittee.getIsPublic()) : null;
                createUpdateAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", 
                    "Status Change", "BUDG Viewing", oldViewingName, newViewingName, userName);
            }
            
            // Status
            if (!isEqual(oldCommittee.getStatus(), newCommittee.getStatus())) {
                String oldStatusName = oldCommittee.getStatus() != null ? getStatusPrimaryName(oldCommittee.getStatus()) : null;
                String newStatusName = newCommittee.getStatus() != null ? getStatusPrimaryName(newCommittee.getStatus()) : null;
                createUpdateAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
            // Lifecycle
            if (!isEqual(oldCommittee.getLifecycle(), newCommittee.getLifecycle())) {
                String oldLifecycleName = oldCommittee.getLifecycle() != null ? getCommitteeLifecycleName(oldCommittee.getLifecycle()) : null;
                String newLifecycleName = newCommittee.getLifecycle() != null ? getCommitteeLifecycleName(newCommittee.getLifecycle()) : null;
                createUpdateAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", 
                    "Status Change", "Lifecycle", oldLifecycleName, newLifecycleName, userName);
            }
            
            // Committee Type
            if (!isEqual(oldCommittee.getCommitteeType(), newCommittee.getCommitteeType())) {
                String oldTypeName = oldCommittee.getCommitteeType() != null ? getCommitteeTypeName(oldCommittee.getCommitteeType()) : null;
                String newTypeName = newCommittee.getCommitteeType() != null ? getCommitteeTypeName(newCommittee.getCommitteeType()) : null;
                createUpdateAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", 
                    "Status Change", "Committee Type", oldTypeName, newTypeName, userName);
            }
            
            // Classification
            if (!isEqual(oldCommittee.getClassification(), newCommittee.getClassification())) {
                String oldClassificationName = oldCommittee.getClassification() != null ? getCommitteeClassificationName(oldCommittee.getClassification()) : null;
                String newClassificationName = newCommittee.getClassification() != null ? getCommitteeClassificationName(newCommittee.getClassification()) : null;
                createUpdateAuditRecord(conn, auditStmt, committeeId, "Committee", "Details", 
                    "Status Change", "Classification", oldClassificationName, newClassificationName, userName);
            }
            
            // لا نحتاج commit هنا لأن الـ connection يتم إدارته من الـ calling method
            
        } catch (SQLException e) {
            System.err.println("❌ Error in createCommitteeUpdateAuditRecords: " + e.getMessage());
            throw e;
        } finally {
            // تنظيف الـ statement فقط - الـ connection يتم إدارته خارجياً
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
            int committeeId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, committeeId);
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
     * Method لإنشاء snapshot جديد في committee_audit عند الـ update
     */
    public void createCommitteeUpdateAuditSnapshot(Connection conn, int committeeId) throws SQLException {
        //system.out.println("🔍 CommitteeDAO.createCommitteeUpdateAuditSnapshot - Creating update snapshot for ID: " + committeeId);
        String sql = """
            INSERT INTO committee_audit (
                ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Committee_Type,
                RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                DeleteDatetime, LastUpdate_UserID, Created_By, RevType
            )
            SELECT 
                ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Committee_Type,
                RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                DeleteDatetime, LastUpdate_UserID, Created_By, 'Updated'
            FROM committee 
            WHERE ID = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, committeeId);
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
        
        // Handle String comparison (case-insensitive and trim)
        if (obj1 instanceof String && obj2 instanceof String) {
            String str1 = ((String) obj1).trim();
            String str2 = ((String) obj2).trim();
            return str1.equalsIgnoreCase(str2);
        }
        
        return obj1.equals(obj2);
    }

    /**
     * Get committee by ID
     */
    public Committee getCommitteeById(int id) throws SQLException {
        String sql = """
            SELECT c.ID, c.Parent_ID, c.Is_Public, c.Classification, c.Status, c.Lifecycle, c.Committee_Type,
                   RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                   c.DeleteDatetime, c.LastUpdate_UserID, c.Created_By, s.primaryname AS statusName
            FROM committee c
            LEFT JOIN status s ON s.ID = c.Status
            WHERE c.ID = ? AND c.DeleteDatetime IS NULL
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Committee committee = new Committee();
                    committee.setId(rs.getInt("ID"));
                    committee.setParentId(rs.getObject("Parent_ID", Integer.class));
                    committee.setIsPublic(rs.getObject("Is_Public", Integer.class));
                    committee.setClassification(rs.getObject("Classification", Integer.class));
                    committee.setStatus(rs.getObject("Status", Integer.class));
                    committee.setStatusName(rs.getString("statusName"));
                    committee.setLifecycle(rs.getObject("Lifecycle", Integer.class));
                    committee.setCommitteeType(rs.getObject("Committee_Type", Integer.class));
                    committee.setRefNumber(rs.getString("RefNumber"));
                    committee.setPrimaryName(rs.getString("PrimaryName"));
                    committee.setDescription(rs.getString("Description"));
                    committee.setCreateDatetime(rs.getTimestamp("CreateDatetime"));
                    committee.setLastUpdateDatetime(rs.getTimestamp("LastUpdateDatetime"));
                    committee.setDeleteDatetime(rs.getTimestamp("DeleteDatetime"));
                    committee.setLastUpdateUserID(rs.getObject("LastUpdate_UserID", Integer.class));
                    committee.setCreatedBy(rs.getObject("Created_By", Integer.class));
                    return committee;
                }
            }
        }
        return null;
    }

    /**
     * Get committee by reference number
     */
    public Committee getCommitteeByRefNumber(String refNumber) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return null;
        }
        
        String sql = """
            SELECT ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Committee_Type,
                   RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                   DeleteDatetime, LastUpdate_UserID
            FROM committee
            WHERE RefNumber = ? AND DeleteDatetime IS NULL
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, refNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Committee committee = new Committee();
                    committee.setId(rs.getInt("ID"));
                    committee.setParentId(rs.getObject("Parent_ID", Integer.class));
                    committee.setIsPublic(rs.getObject("Is_Public", Integer.class));
                    committee.setClassification(rs.getObject("Classification", Integer.class));
                    committee.setStatus(rs.getObject("Status", Integer.class));
                    committee.setLifecycle(rs.getObject("Lifecycle", Integer.class));
                    committee.setCommitteeType(rs.getObject("Committee_Type", Integer.class));
                    committee.setRefNumber(rs.getString("RefNumber"));
                    committee.setPrimaryName(rs.getString("PrimaryName"));
                    committee.setDescription(rs.getString("Description"));
                    committee.setCreateDatetime(rs.getTimestamp("CreateDatetime"));
                    committee.setLastUpdateDatetime(rs.getTimestamp("LastUpdateDatetime"));
                    committee.setDeleteDatetime(rs.getTimestamp("DeleteDatetime"));
                    committee.setLastUpdateUserID(rs.getObject("LastUpdate_UserID", Integer.class));
                    return committee;
                }
            }
        }
        return null;
    }

    /**
     * Get committee by primary name
     */
    public Committee getCommitteeByPrimaryName(String primaryName) throws SQLException {
        if (primaryName == null || primaryName.trim().isEmpty()) {
            return null;
        }
        
        String sql = """
            SELECT ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Committee_Type,
                   RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                   DeleteDatetime, LastUpdate_UserID
            FROM committee
            WHERE PrimaryName = ? AND DeleteDatetime IS NULL
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, primaryName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Committee committee = new Committee();
                    committee.setId(rs.getInt("ID"));
                    committee.setParentId(rs.getObject("Parent_ID", Integer.class));
                    committee.setIsPublic(rs.getObject("Is_Public", Integer.class));
                    committee.setClassification(rs.getObject("Classification", Integer.class));
                    committee.setStatus(rs.getObject("Status", Integer.class));
                    committee.setLifecycle(rs.getObject("Lifecycle", Integer.class));
                    committee.setCommitteeType(rs.getObject("Committee_Type", Integer.class));
                    committee.setRefNumber(rs.getString("RefNumber"));
                    committee.setPrimaryName(rs.getString("PrimaryName"));
                    committee.setDescription(rs.getString("Description"));
                    committee.setCreateDatetime(rs.getTimestamp("CreateDatetime"));
                    committee.setLastUpdateDatetime(rs.getTimestamp("LastUpdateDatetime"));
                    committee.setDeleteDatetime(rs.getTimestamp("DeleteDatetime"));
                    committee.setLastUpdateUserID(rs.getObject("LastUpdate_UserID", Integer.class));
                    return committee;
                }
            }
        }
        return null;
    }

    /**
     * Create committee and return created object with ID
     */
    public Committee createCommittee(Committee committee) throws SQLException {
        String sql = """
            INSERT INTO committee (
                Parent_ID, Is_Public, Classification, Status, Lifecycle, Committee_Type,
                RefNumber, PrimaryName, Description, LastUpdate_UserID, CreateDatetime, LastUpdateDatetime
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setObject(1, committee.getParentId());
            ps.setObject(2, committee.getIsPublic());
            ps.setObject(3, committee.getClassification());
            ps.setObject(4, committee.getStatus());
            ps.setObject(5, committee.getLifecycle());
            ps.setObject(6, committee.getCommitteeType());
            ps.setString(7, committee.getRefNumber());
            ps.setString(8, committee.getPrimaryName());
            ps.setString(9, committee.getDescription());
            ps.setObject(10, committee.getLastUpdateUserID());
            
            int affectedRows = ps.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        committee.setId(generatedKeys.getInt(1));
                        return committee;
                    }
                }
            }
        }
        
        throw new SQLException("Creating committee failed, no ID obtained.");
    }

    /**
     * حذف اللجنة مع تسجيل audit records
     */
    public boolean deleteCommitteeWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE committee SET DeleteDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO committee_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Committee");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Committee");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في committee_audit
                String snapshotSql = """
                    INSERT INTO committee_audit (
                        ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Committee_Type,
                        RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                        DeleteDatetime, LastUpdate_UserID, Created_By, RevType
                    )
                    SELECT 
                        ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Committee_Type,
                        RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                        DeleteDatetime, LastUpdate_UserID, Created_By, 'Deleted'
                    FROM committee 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Committee deleted with audit for ID: " + id);
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
     * Link stakeholder to committee via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToCommittee(Connection conn, int committeeId, int objectXPeopleId, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO committee_x_objectxpeople (Committee_ID, Object_X_ipid, LastUpdateUser_ID, CreateDatetime)
            VALUES (?, ?, ?, NOW())
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, committeeId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, currentUserId);
            try {
                ps.executeUpdate();
                //system.out.println("✅ Successfully linked stakeholder to committee: Committee_ID=" + committeeId + ", Object_X_ipid=" + objectXPeopleId + ", rows affected=" + rowsAffected);
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: Committee_ID=" + committeeId + ", Object_X_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }
}
