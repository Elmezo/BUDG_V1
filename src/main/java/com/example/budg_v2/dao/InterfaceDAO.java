package com.example.budg_v2.dao;

import com.example.budg_v2.audit.AuditHistoryWriter;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.ModuleResolver;
import com.example.budg_v2.model.Interface;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class InterfaceDAO {

    public Map<String, Object> getById(int id) throws SQLException {
        String sql = "SELECT i.id, i.Name, i.Ref_number, i.Description, i.Classification_id, ic.Name AS classificationName, " +
                "i.Lifecycle_id, il.Name AS lifecycleName, i.status_id, st.primaryname AS statusName, " +
                "i.Source_systemID, s1.Name AS sourceName, i.Target_systemID, s2.Name AS targetName, " +
                "i.Automation_ID, ia.Name AS automationName, i.Frequency_ID, ifr.Name AS frequencyName, " +
                "i.Transfer_Method_ID, itm.Name AS transferMethodName, i.Transfer_Format_ID, itf.Name AS transferFormatName, " +
                "i.Asset_ID, i.Synchronisation_Control, i.is_public, v.Name AS viewingName, " +
                "i.created_datetime, i.last_updatedtime, i.createdBy_ID, i.last_updateuser_id, " +
                "CONCAT(p1.First_Name, ' ', p1.Last_Name) AS createdByName, " +
                "CONCAT(p2.First_Name, ' ', p2.Last_Name) AS lastUpdatedByName " +
                "FROM interface i " +
                "LEFT JOIN interface_classification ic ON ic.id = i.Classification_id " +
                "LEFT JOIN interface_lifecycle il ON il.id = i.Lifecycle_id " +
                "LEFT JOIN status st ON st.ID = i.status_id " +
                "LEFT JOIN system s1 ON s1.id = i.Source_systemID " +
                "LEFT JOIN system s2 ON s2.id = i.Target_systemID " +
                "LEFT JOIN interface_automation ia ON ia.id = i.Automation_ID " +
                "LEFT JOIN interface_frequency ifr ON ifr.id = i.Frequency_ID " +
                "LEFT JOIN interface_transfer itm ON itm.id = i.Transfer_Method_ID " +
                "LEFT JOIN interface_transfer_format itf ON itf.id = i.Transfer_Format_ID " +
                "LEFT JOIN viewing v ON v.id = i.is_public " +
                "LEFT JOIN people p1 ON p1.ID = i.createdBy_ID " +
                "LEFT JOIN people p2 ON p2.ID = i.last_updateuser_id " +
                "WHERE i.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("name", rs.getString("Name"));
                row.put("ref", rs.getString("Ref_number"));
                row.put("description", rs.getString("Description"));
                row.put("classificationName", rs.getString("classificationName"));
                row.put("lifecycleName", rs.getString("lifecycleName"));
                row.put("statusName", rs.getString("statusName"));
                row.put("sourceName", rs.getString("sourceName"));
                row.put("targetName", rs.getString("targetName"));
                row.put("automationName", rs.getString("automationName"));
                row.put("frequencyName", rs.getString("frequencyName"));
                row.put("transferMethodName", rs.getString("transferMethodName"));
                row.put("transferFormatName", rs.getString("transferFormatName"));
                row.put("assetId", rs.getString("Asset_ID"));
                row.put("ref_number", rs.getString("Ref_number"));
                row.put("classification_id", rs.getObject("Classification_id"));
                row.put("lifecycle_id", rs.getObject("Lifecycle_id"));
                row.put("status_id", rs.getObject("status_id"));
                row.put("automation_id", rs.getObject("Automation_ID"));
                row.put("frequency_id", rs.getObject("Frequency_ID"));
                row.put("transfer_method_id", rs.getObject("Transfer_Method_ID"));
                row.put("transfer_format_id", rs.getObject("Transfer_Format_ID"));
                row.put("is_public", rs.getObject("is_public"));
                row.put("sourceSystemId", rs.getObject("Source_systemID"));
                row.put("targetSystemId", rs.getObject("Target_systemID"));
                row.put("syncControl", rs.getString("Synchronisation_Control"));
                row.put("viewingName", rs.getString("viewingName"));
                row.put("created_datetime", rs.getTimestamp("created_datetime"));
                row.put("last_updatedtime", rs.getTimestamp("last_updatedtime"));
                row.put("createdBy_ID", rs.getObject("createdBy_ID"));
                row.put("last_updateuser_id", rs.getObject("last_updateuser_id"));
                row.put("createdByName", rs.getString("createdByName"));
                row.put("lastUpdatedByName", rs.getString("lastUpdatedByName"));
                
                // Debug logging
                //system.out.println("Interface data for ID " + id + ":");
                //system.out.println("  createdByName: " + rs.getString("createdByName"));
                //system.out.println("  lastUpdatedByName: " + rs.getString("lastUpdatedByName"));
                //system.out.println("  created_datetime: " + rs.getTimestamp("created_datetime"));
                //system.out.println("  last_updatedtime: " + rs.getTimestamp("last_updatedtime"));
                //system.out.println("  createdBy_ID: " + rs.getObject("createdBy_ID"));
                //system.out.println("  last_updateuser_id: " + rs.getObject("last_updateuser_id"));

                // Compute Data Attributes count per provided query logic, scoped to this interface
                String countSql = """
                    SELECT COUNT(*) AS cnt
                    FROM attribute_x_attribute axa
                    WHERE axa.Relation_Method = ?
                """;
                try (PreparedStatement cps = conn.prepareStatement(countSql)) {
                    cps.setInt(1, id);
                    try (ResultSet crs = cps.executeQuery()) {
                        if (crs.next()) {
                            row.put("dataAttributesCount", crs.getInt("cnt"));
                        } else {
                            row.put("dataAttributesCount", 0);
                        }
                    }
                }

                return row;
            }
        }
    }

    public List<Map<String, Object>> getDirectStakeholdersForInterface(int interfaceId) throws SQLException {
        String sql = """
            SELECT
                oxp.ID as object_x_ipid,
                oxp.ipid AS people_id,
                oxp.RoleID AS role_id,
                oxp.statusID AS status_id,
                oxp.isDelegateOF AS delegate_of_id,
                r.PrimaryName AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                ou.Name AS org_unit,
                CASE oxp.AcceptedID WHEN 1 THEN 'True' ELSE 'False' END AS role_accepted,
                CONCAT(delegate_p.First_Name, ' ', delegate_p.Last_Name) AS delegate_of,
                oxp.createdatetime AS date_accepted
            FROM interface_x_objectxpeople ix
            JOIN object_x_people oxp ON ix.Object_x_ipid = oxp.ID
            JOIN object_role r ON oxp.RoleID = r.ID
            JOIN people p ON oxp.ipid = p.ID
            JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
            LEFT JOIN object_x_people d_oxp ON oxp.isDelegateOF = d_oxp.ID
            LEFT JOIN people delegate_p ON d_oxp.ipid = delegate_p.ID
            WHERE ix.InterfaceID = ?
            ORDER BY r.PrimaryName, p.Last_Name, p.First_Name
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interfaceId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("object_x_ipid", rs.getInt("object_x_ipid"));
                    row.put("peopleId", (Object) rs.getObject("people_id"));
                    row.put("roleId", (Object) rs.getObject("role_id"));
                    row.put("statusId", (Object) rs.getObject("status_id"));
                    row.put("delegateOfId", (Object) rs.getObject("delegate_of_id"));
                    row.put("role", rs.getString("role"));
                    row.put("name", rs.getString("name"));
                    row.put("orgUnit", rs.getString("org_unit"));
                    row.put("roleAccepted", rs.getString("role_accepted"));
                    row.put("delegateOf", rs.getString("delegate_of"));
                    row.put("dateAccepted", rs.getTimestamp("date_accepted"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public List<Map<String, Object>> getRolesForInterface(int interfaceId) throws SQLException {
        // Get module ID dynamically based on entity type
        int moduleId = ModuleResolver.getModuleId("interface");
        String sql = """
            SELECT DISTINCT r.id, r.primaryname
            FROM object_role r
            WHERE r.module = ?
            ORDER BY r.primaryname
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("name", rs.getString("primaryname"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public List<Map<String, Object>> getUsersByRole(int interfaceId, int roleId) throws SQLException {
        String sql = """
            SELECT DISTINCT p.ID, CONCAT(p.First_Name, ' ', p.Last_Name) AS name, p.Email
            FROM people p
            JOIN role_assignment ra ON FIND_IN_SET(p.ID, REPLACE(REPLACE(ra.users, '[', ''), ']', '')) > 0
            WHERE ra.objectroleid = ?
            ORDER BY p.Last_Name, p.First_Name
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("name"));
                    row.put("email", rs.getString("Email"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public List<Map<String, Object>> getStatusesForInterface(int interfaceId) throws SQLException {
        String sql = """
            SELECT ID, primaryname
            FROM status
            ORDER BY primaryname
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("primaryname"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public void saveStakeholdersChanges(int interfaceId, Map<String, Object> changes, int currentUserId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                @SuppressWarnings("unchecked") List<Map<String, Object>> inserts = (List<Map<String, Object>>) changes.getOrDefault("inserts", java.util.Collections.emptyList());
                @SuppressWarnings("unchecked") List<Map<String, Object>> updates = (List<Map<String, Object>>) changes.getOrDefault("updates", java.util.Collections.emptyList());
                @SuppressWarnings("unchecked") List<Map<String, Object>> deletes = (List<Map<String, Object>>) changes.getOrDefault("deletes", java.util.Collections.emptyList());

                for (Map<String, Object> row : inserts) {
                    validateRequired(row);
                    int oxpId = createObjectXPeople(conn, convertRow(row), currentUserId);
                    linkStakeholderToInterface(conn, interfaceId, oxpId);
                }

                for (Map<String, Object> row : updates) {
                    validateRequired(row);
                    Integer oxpId = getInt(row.get("objectXPeopleId"));
                    if (oxpId == null) throw new IllegalArgumentException("Missing objectXPeopleId in update row");
                    updateObjectXPeople(conn, oxpId, convertRow(row));
                }

                for (Map<String, Object> row : deletes) {
                    Integer oxpId = getInt(row.get("objectXPeopleId"));
                    if (oxpId == null) throw new IllegalArgumentException("Missing objectXPeopleId in delete row");
                    unlinkStakeholderFromInterface(conn, interfaceId, oxpId);
                    if (!hasAnyOtherLink(conn, oxpId)) {
                        deleteObjectXPeople(conn, oxpId);
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw new SQLException(e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private Map<String, Object> convertRow(Map<String, Object> row) {
        Map<String, Object> m = new HashMap<>();
        m.put("roleId", getInt(row.get("roleId")));
        m.put("userId", getInt(row.get("ipid")));
        m.put("statusId", normalizeAcceptedOrStatus(row.get("accepted"), row.get("statusId")));
        m.put("delegateOfId", getInt(row.get("delegateIpId")));
        return m;
    }

    private Integer normalizeAcceptedOrStatus(Object accepted, Object statusId) {
        Integer sid = getInt(statusId);
        Integer acc = getInt(accepted);
        if (acc != null) return acc == 1 ? 1 : 0;
        return sid;
    }

    private void validateRequired(Map<String, Object> row) {
        if (getInt(row.get("roleId")) == null) throw new IllegalArgumentException("Role is required");
        if (getInt(row.get("ipid")) == null) throw new IllegalArgumentException("Name is required");
    }

    private Integer getInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    /**
     * Create object_x_people record for stakeholder
     * Always creates a NEW record (no reuse)
     */
    public int createObjectXPeople(Connection conn, Map<String, Object> stakeholder, int currentUserId) throws SQLException {
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
    
    private void updateObjectXPeople(Connection conn, int objectXPeopleId, Map<String, Object> stakeholder) throws SQLException {
        String sql = """
            UPDATE object_x_people 
            SET RoleID = ?, ipid = ?, AcceptedID = 2, statusID = ?, isDelegateOF = ?, lastupdatedatetime = NOW()
            WHERE ID = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, (Integer) stakeholder.get("roleId"));
            ps.setInt(2, (Integer) stakeholder.get("userId"));
            
            Integer statusId = (Integer) stakeholder.get("statusId");
            if (statusId != null) {
                ps.setInt(3, statusId);
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            
            // Handle delegateOf
            Integer delegateOfId = (Integer) stakeholder.get("delegateOfId");
            if (delegateOfId != null) {
                ps.setInt(4, delegateOfId);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            
            ps.setInt(5, objectXPeopleId);
            
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No rows updated for object_x_people ID: " + objectXPeopleId);
            }
        }
    }
    
    private void deleteObjectXPeople(Connection conn, int objectXPeopleId) throws SQLException {
        String sql = "DELETE FROM object_x_people WHERE ID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXPeopleId);
            
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No rows deleted for object_x_people ID: " + objectXPeopleId);
            }
        }
    }
    
    /**
     * Link stakeholder to interface via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToInterface(Connection conn, int interfaceId, int objectXPeopleId) throws SQLException {
        String sql = """
            INSERT INTO interface_x_objectxpeople (InterfaceID, Object_x_ipid, CreateDatetime)
            VALUES (?, ?, NOW())
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interfaceId);
            ps.setInt(2, objectXPeopleId);
            try {
                ps.executeUpdate();
                //system.out.println("✅ Successfully linked stakeholder to interface: InterfaceID=" + interfaceId + ", Object_x_ipid=" + objectXPeopleId + ", rows affected=" + rowsAffected);
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: InterfaceID=" + interfaceId + ", Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }
    
    private void unlinkStakeholderFromInterface(Connection conn, int interfaceId, int objectXPeopleId) throws SQLException {
        String sql = "DELETE FROM interface_x_objectxpeople WHERE InterfaceID = ? AND Object_x_ipid = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interfaceId);
            ps.setInt(2, objectXPeopleId);
            
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No rows deleted from interface_x_objectxpeople for InterfaceID: " + interfaceId + ", Object_x_ipid: " + objectXPeopleId);
            }
        }
    }

    private boolean hasAnyOtherLink(Connection conn, int objectXPeopleId) throws SQLException {
        String sql = "SELECT ("
                + " (SELECT COUNT(*) FROM system_x_objectxpeople WHERE Object_x_ipid=?) +"
                + " (SELECT COUNT(*) FROM dataset_x_objectxpeople WHERE Object_x_ipid=?) +"
                + " (SELECT COUNT(*) FROM interface_x_objectxpeople WHERE Object_x_ipid=?) +"
                + " (SELECT COUNT(*) FROM glossary_x_objectxpeople WHERE Object_x_ipid=?)"
                + ") AS cnt";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXPeopleId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, objectXPeopleId);
            ps.setInt(4, objectXPeopleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int interfaceId, String object, String event, String updateType, String field, String value, String userName) throws SQLException {
        //system.out.println("    📝 Creating audit record: [" + object + "][" + event + "][" + field + "] = " + value);
        auditStmt.setInt(1, interfaceId);        // id
        auditStmt.setString(2, object);          // object
        auditStmt.setString(3, event);           // event
        auditStmt.setString(4, updateType);      // updateType
        auditStmt.setString(5, field);           // field
        auditStmt.setString(6, value);           // to
        auditStmt.setString(7, userName);        // author
        
        auditStmt.executeUpdate();
        //system.out.println("    ✓ Audit record inserted, rows affected: " + rowsAffected);
        
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

    /**
     * إنشاء audit records للـ interface الجديد (version with new connection)
     * يتم استدعاء هذا method بعد إنشاء الـ interface بنجاح
     */
    public void createInterfaceAuditRecords(int interfaceId, String userName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            createInterfaceAuditRecords(conn, interfaceId, userName);
        }
    }

    /**
     * إنشاء audit records للـ interface الجديد (version with existing connection)
     * يتم استدعاء هذا method بعد إنشاء الـ interface بنجاح
     */
    public void createInterfaceAuditRecords(Connection conn, int interfaceId, String userName) throws SQLException {
        //system.out.println("🔍 InterfaceDAO.createInterfaceAuditRecords - START for ID: " + interfaceId + ", userName: " + userName);
        PreparedStatement auditStmt = null;
        
        try {
            //system.out.println("✓ Using existing connection for audit records");
            
            // Get interface data
            String interfaceDataSql = "SELECT * FROM interface WHERE id = ?";
            PreparedStatement interfaceStmt = conn.prepareStatement(interfaceDataSql);
            interfaceStmt.setInt(1, interfaceId);
            ResultSet interfaceRs = interfaceStmt.executeQuery();
            
            if (!interfaceRs.next()) {
                throw new SQLException("Interface not found with ID: " + interfaceId);
            }
            //system.out.println("✓ Interface data retrieved successfully");

            // Prepare audit statement
            String auditSql = """
                INSERT INTO interface_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
            AuditHistoryWriter.logCreatedBy(conn, "interface_audit_history", interfaceId, "Interface", userName);
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);

            // Name
            String name = interfaceRs.getString("Name");
            if (name != null && !name.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Name", name, userName);
            }
            
            // Reference Number
            String refNumber = interfaceRs.getString("Ref_number");
            if (refNumber != null && !refNumber.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Reference Number", refNumber, userName);
            }
            
            // Description
            String description = interfaceRs.getString("Description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Description", description, userName);
            }
            
            // Classification
            Integer classificationId = interfaceRs.getObject("Classification_id", Integer.class);
            if (classificationId != null) {
                String classificationName = getInterfaceClassificationName(classificationId);
                if (classificationName != null) {
                    createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Classification", classificationName, userName);
                }
            }
            
            // Status
            Integer statusId = interfaceRs.getObject("status_id", Integer.class);
            if (statusId != null) {
                String statusName = getStatusPrimaryName(statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Status Change", "Status", statusName, userName);
                }
            }
            
            // Lifecycle
            Integer lifecycleId = interfaceRs.getObject("Lifecycle_id", Integer.class);
            if (lifecycleId != null) {
                String lifecycleName = getInterfaceLifecycleName(lifecycleId);
                if (lifecycleName != null) {
                    createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Lifecycle", lifecycleName, userName);
                }
            }
            
            // Source System
            Integer sourceSystemId = interfaceRs.getObject("Source_systemID", Integer.class);
            if (sourceSystemId != null) {
                String sourceSystemName = getSystemName(sourceSystemId);
                if (sourceSystemName != null) {
                    createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Source System", sourceSystemName, userName);
                }
            }
            
            // Target System
            Integer targetSystemId = interfaceRs.getObject("Target_systemID", Integer.class);
            if (targetSystemId != null) {
                String targetSystemName = getSystemName(targetSystemId);
                if (targetSystemName != null) {
                    createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Target System", targetSystemName, userName);
                }
            }
            
            // Automation
            Integer automationId = interfaceRs.getObject("Automation_ID", Integer.class);
            if (automationId != null) {
                String automationName = getInterfaceAutomationName(automationId);
                if (automationName != null) {
                    createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Automation", automationName, userName);
                }
            }
            
            // Frequency
            Integer frequencyId = interfaceRs.getObject("Frequency_ID", Integer.class);
            if (frequencyId != null) {
                String frequencyName = getInterfaceFrequencyName(frequencyId);
                if (frequencyName != null) {
                    createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Frequency", frequencyName, userName);
                }
            }
            
            // Transfer Method
            Integer transferMethodId = interfaceRs.getObject("Transfer_Method_ID", Integer.class);
            if (transferMethodId != null) {
                String transferMethodName = getInterfaceTransferMethodName(transferMethodId);
                if (transferMethodName != null) {
                    createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Transfer Method", transferMethodName, userName);
                }
            }
            
            // Transfer Format
            Integer transferFormatId = interfaceRs.getObject("Transfer_Format_ID", Integer.class);
            if (transferFormatId != null) {
                String transferFormatName = getInterfaceTransferFormatName(transferFormatId);
                if (transferFormatName != null) {
                    createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Transfer Format", transferFormatName, userName);
                }
            }
            
            // Is Public
            Integer isPublicId = interfaceRs.getObject("is_public", Integer.class);
            if (isPublicId != null) {
                String isPublicName = getViewingName(isPublicId);
                if (isPublicName != null) {
                    createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Is Public", isPublicName, userName);
                }
            }
            
            // Asset ID
            String assetId = interfaceRs.getString("Asset_ID");
            if (assetId != null && !assetId.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Asset ID", assetId, userName);
            }
            
            // Synchronisation Control
            String synchronisationControl = interfaceRs.getString("Synchronisation_Control");
            if (synchronisationControl != null && !synchronisationControl.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", "Added", "Synchronisation Control", synchronisationControl, userName);
            }
            
            // Created By is written as the first row via AuditHistoryWriter.logCreatedBy.

            //system.out.println("✅ InterfaceDAO.createInterfaceAuditRecords - completed successfully for ID: " + interfaceId);
            
        } catch (SQLException e) {
            System.err.println("❌ InterfaceDAO.createInterfaceAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            if (auditStmt != null) auditStmt.close();
            // لا نغلق الـ connection لأنه تم تمريره من method أخرى
        }
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إضافته للـ interface (version with new connection)
     * يتم استدعاء هذا method عند إضافة stakeholder جديد
     */
    public void createStakeholderAuditRecords(int interfaceId, String userName, String userFullName, int roleId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            createStakeholderAuditRecords(conn, interfaceId, userName, userFullName, roleId);
        }
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إضافته للـ interface (version with existing connection)
     * يتم استدعاء هذا method عند إضافة stakeholder جديد
     */
    public void createStakeholderAuditRecords(Connection conn, int interfaceId, String userName, String userFullName, int roleId) throws SQLException {
        //system.out.println("🔍 InterfaceDAO.createStakeholderAuditRecords - START for Interface ID: " + interfaceId + ", User: " + userFullName + ", Role ID: " + roleId);
        PreparedStatement auditStmt = null;
        
        try {
            //system.out.println("✓ Using existing connection for stakeholder audit");
            
            // إعداد audit statement للـ stakeholder
            String auditSql = """
                INSERT INTO interface_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // إدراج 3 سجلات للـ stakeholder
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleId(interfaceId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }
            
            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getRoleName(actualRoleId);
            if (roleName == null) roleName = "Interface Owner";
            
            // Role
            createNewAuditRecord(conn, auditStmt, interfaceId, "Stakeholder", "link", "Added", "Role", roleName, userName);
            //system.out.println("  ✓ Role audit record created: " + roleName);
            
            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusId(interfaceId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(statusId);
                if (fetchedStatusName != null) statusName = fetchedStatusName;
            }
            createNewAuditRecord(conn, auditStmt, interfaceId, "Stakeholder", "link", "Added", "Role Status", statusName, userName);
            //system.out.println("  ✓ Role Status audit record created: " + statusName);
            
            // Name
            createNewAuditRecord(conn, auditStmt, interfaceId, "Stakeholder", "link", "Added", "Name", userFullName, userName);
            //system.out.println("  ✓ Name audit record created: " + userFullName);
            
            //system.out.println("✅ InterfaceDAO.createStakeholderAuditRecords - completed successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ InterfaceDAO.createStakeholderAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            if (auditStmt != null) auditStmt.close();
            // لا نغلق الـ connection لأنه تم تمريره من method أخرى
        }
    }

    /**
     * إنشاء سجل في جدول interface_audit بعد إنشاء الـ interface (version with new connection)
     * يتم استدعاء هذا method بعد إنشاء الـ interface بنجاح
     */
    public void createInterfaceAuditRecord(int interfaceId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            createInterfaceAuditRecord(conn, interfaceId);
        }
    }

    /**
     * إنشاء سجل في جدول interface_audit بعد إنشاء الـ interface (version with existing connection)
     * يتم استدعاء هذا method بعد إنشاء الـ interface بنجاح
     */
    public void createInterfaceAuditRecord(Connection conn, int interfaceId) throws SQLException {
        //system.out.println("🔍 InterfaceDAO.createInterfaceAuditRecord - Creating snapshot for ID: " + interfaceId);
        String sql = """
            INSERT INTO interface_audit (
                id, Name, Ref_number, Description, Classification_id, Lifecycle_id, status_id,
                Source_systemID, Target_systemID, Automation_ID, Frequency_ID, 
                Transfer_Method_ID, Transfer_Format_ID, Asset_ID, Synchronisation_Control,
                is_public, created_datetime, last_updatedtime, createdBy_ID, last_updateuser_id, RevType
            )
            SELECT 
                id, Name, Ref_number, Description, Classification_id, Lifecycle_id, status_id,
                Source_systemID, Target_systemID, Automation_ID, Frequency_ID,
                Transfer_Method_ID, Transfer_Format_ID, Asset_ID, Synchronisation_Control,
                is_public, created_datetime, last_updatedtime, createdBy_ID, last_updateuser_id, 'Added'
            FROM interface 
            WHERE id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interfaceId);
            ps.executeUpdate();
            //system.out.println("✅ InterfaceDAO.createInterfaceAuditRecord - Snapshot created, rows affected: " + rows);
        } catch (SQLException e) {
            System.err.println("❌ InterfaceDAO.createInterfaceAuditRecord - ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private String getRoleName(int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        }
        return null;
    }
    
    // Get actual roleID from object_x_people for the stakeholder
    private Integer getStakeholderRoleId(int interfaceId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN interface_x_objectxpeople ixo ON ixo.Object_x_ipid = oxp.ID " +
                    "WHERE ixo.InterfaceID = ? " +
                    "ORDER BY ixo.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interfaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }
    
    // Get statusID from object_x_people for the stakeholder
    private Integer getStakeholderStatusId(int interfaceId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN interface_x_objectxpeople ixo ON ixo.Object_x_ipid = oxp.ID " +
                    "WHERE ixo.InterfaceID = ? " +
                    "ORDER BY ixo.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interfaceId);
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

    // Helper methods للحصول على الأسماء - versions with existing connection to avoid connection leaks
    private String getInterfaceClassificationName(Connection conn, int classificationId) throws SQLException {
        String sql = "SELECT Name FROM interface_classification WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classificationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
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

    private String getInterfaceLifecycleName(Connection conn, int lifecycleId) throws SQLException {
        String sql = "SELECT Name FROM interface_lifecycle WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getSystemName(Connection conn, int systemId) throws SQLException {
        String sql = "SELECT Name FROM system WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getInterfaceAutomationName(Connection conn, int automationId) throws SQLException {
        String sql = "SELECT Name FROM interface_automation WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, automationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getInterfaceFrequencyName(Connection conn, int frequencyId) throws SQLException {
        String sql = "SELECT Name FROM interface_frequency WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, frequencyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getInterfaceTransferMethodName(Connection conn, int transferMethodId) throws SQLException {
        String sql = "SELECT Name FROM interface_transfer WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transferMethodId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getInterfaceTransferFormatName(Connection conn, int transferFormatId) throws SQLException {
        String sql = "SELECT Name FROM interface_transfer_format WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transferFormatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
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

    // Helper methods للحصول على الأسماء - original versions (kept for backward compatibility)
    // These methods are used by existing audit methods that don't have access to a connection
    private String getInterfaceClassificationName(int classificationId) throws SQLException {
        String sql = "SELECT Name FROM interface_classification WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classificationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
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

    private String getInterfaceLifecycleName(int lifecycleId) throws SQLException {
        String sql = "SELECT Name FROM interface_lifecycle WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getSystemName(int systemId) throws SQLException {
        String sql = "SELECT Name FROM system WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getInterfaceAutomationName(int automationId) throws SQLException {
        String sql = "SELECT Name FROM interface_automation WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, automationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getInterfaceFrequencyName(int frequencyId) throws SQLException {
        String sql = "SELECT Name FROM interface_frequency WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, frequencyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getInterfaceTransferMethodName(int transferMethodId) throws SQLException {
        String sql = "SELECT Name FROM interface_transfer WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transferMethodId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getInterfaceTransferFormatName(int transferFormatId) throws SQLException {
        String sql = "SELECT Name FROM interface_transfer_format WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transferFormatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
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

    // Version with existing connection (to avoid connection leaks)
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

    /**
     * Get Interface object by ID for update operations
     */
    public Interface getInterfaceById(int id) throws SQLException {
        String sql = """
            SELECT id, Name, Ref_number, Description, Classification_id, Lifecycle_id, status_id,
                   Source_systemID, Target_systemID, Automation_ID, Frequency_ID, 
                   Transfer_Method_ID, Transfer_Format_ID, Asset_ID, Synchronisation_Control,
                   is_public, created_datetime, last_updatedtime, deleted_datetime, 
                   createdBy_ID, last_updateuser_id
            FROM interface 
            WHERE id = ?
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                
                Interface interfaceObj = new Interface();
                interfaceObj.setId(rs.getInt("id"));
                interfaceObj.setName(rs.getString("Name"));
                interfaceObj.setRefNumber(rs.getString("Ref_number"));
                interfaceObj.setDescription(rs.getString("Description"));
                interfaceObj.setClassificationId(rs.getObject("Classification_id", Integer.class));
                interfaceObj.setLifecycleId(rs.getObject("Lifecycle_id", Integer.class));
                interfaceObj.setStatusId(rs.getObject("status_id", Integer.class));
                interfaceObj.setSourceSystemId(rs.getObject("Source_systemID", Integer.class));
                interfaceObj.setTargetSystemId(rs.getObject("Target_systemID", Integer.class));
                interfaceObj.setAutomationId(rs.getObject("Automation_ID", Integer.class));
                interfaceObj.setFrequencyId(rs.getObject("Frequency_ID", Integer.class));
                interfaceObj.setTransferMethodId(rs.getObject("Transfer_Method_ID", Integer.class));
                interfaceObj.setTransferFormatId(rs.getObject("Transfer_Format_ID", Integer.class));
                interfaceObj.setAssetId(rs.getString("Asset_ID"));
                interfaceObj.setSynchronisationControl(rs.getString("Synchronisation_Control"));
                interfaceObj.setIsPublic(rs.getObject("is_public", Integer.class));
                interfaceObj.setCreatedDatetime(rs.getTimestamp("created_datetime"));
                interfaceObj.setLastUpdatedtime(rs.getTimestamp("last_updatedtime"));
                interfaceObj.setDeletedDatetime(rs.getTimestamp("deleted_datetime"));
                interfaceObj.setCreatedById(rs.getObject("createdBy_ID", Integer.class));
                interfaceObj.setLastUpdateUserId(rs.getObject("last_updateuser_id", Integer.class));
                
                return interfaceObj;
            }
        }
    }

    /**
     * Get Interface object by ID using an existing connection (for transaction context)
     * This version is used when we need to read data within the same transaction
     */
    public Interface getInterfaceById(Connection conn, int id) throws SQLException {
        String sql = """
            SELECT id, Name, Ref_number, Description, Classification_id, Lifecycle_id, status_id,
                   Source_systemID, Target_systemID, Automation_ID, Frequency_ID, 
                   Transfer_Method_ID, Transfer_Format_ID, Asset_ID, Synchronisation_Control,
                   is_public, created_datetime, last_updatedtime, deleted_datetime, 
                   createdBy_ID, last_updateuser_id
            FROM interface 
            WHERE id = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                
                Interface interfaceObj = new Interface();
                interfaceObj.setId(rs.getInt("id"));
                interfaceObj.setName(rs.getString("Name"));
                interfaceObj.setRefNumber(rs.getString("Ref_number"));
                interfaceObj.setDescription(rs.getString("Description"));
                interfaceObj.setClassificationId(rs.getObject("Classification_id", Integer.class));
                interfaceObj.setLifecycleId(rs.getObject("Lifecycle_id", Integer.class));
                interfaceObj.setStatusId(rs.getObject("status_id", Integer.class));
                interfaceObj.setSourceSystemId(rs.getObject("Source_systemID", Integer.class));
                interfaceObj.setTargetSystemId(rs.getObject("Target_systemID", Integer.class));
                interfaceObj.setAutomationId(rs.getObject("Automation_ID", Integer.class));
                interfaceObj.setFrequencyId(rs.getObject("Frequency_ID", Integer.class));
                interfaceObj.setTransferMethodId(rs.getObject("Transfer_Method_ID", Integer.class));
                interfaceObj.setTransferFormatId(rs.getObject("Transfer_Format_ID", Integer.class));
                interfaceObj.setAssetId(rs.getString("Asset_ID"));
                interfaceObj.setSynchronisationControl(rs.getString("Synchronisation_Control"));
                interfaceObj.setIsPublic(rs.getObject("is_public", Integer.class));
                interfaceObj.setCreatedDatetime(rs.getTimestamp("created_datetime"));
                interfaceObj.setLastUpdatedtime(rs.getTimestamp("last_updatedtime"));
                interfaceObj.setDeletedDatetime(rs.getTimestamp("deleted_datetime"));
                interfaceObj.setCreatedById(rs.getObject("createdBy_ID", Integer.class));
                interfaceObj.setLastUpdateUserId(rs.getObject("last_updateuser_id", Integer.class));
                
                return interfaceObj;
            }
        }
    }

    /**
     * Update Interface with audit tracking
     */
    public boolean updateInterface(Interface interfaceObj, int userId) throws SQLException {
        //system.out.println("🔍 InterfaceDAO.updateInterface - START for ID: " + interfaceObj.getId());
        
        // الخطوة 1: الحصول على البيانات القديمة قبل التحديث
        Interface oldInterface = getInterfaceById(interfaceObj.getId());
        if (oldInterface == null) {
            throw new SQLException("Interface not found with ID: " + interfaceObj.getId());
        }
        
        String sql = """
            UPDATE interface SET 
                Name = ?, Ref_number = ?, Description = ?, Classification_id = ?, 
                Lifecycle_id = ?, status_id = ?, Source_systemID = ?, Target_systemID = ?, 
                Automation_ID = ?, Frequency_ID = ?, Transfer_Method_ID = ?, Transfer_Format_ID = ?, 
                Asset_ID = ?, Synchronisation_Control = ?, is_public = ?, 
                last_updatedtime = NOW(), last_updateuser_id = ?
            WHERE id = ?
        """;
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);  // بدء Transaction
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, interfaceObj.getName());
                pstmt.setString(2, interfaceObj.getRefNumber());
                pstmt.setString(3, interfaceObj.getDescription());
                
                // Handle nullable integers
                setNullableInt(pstmt, 4, interfaceObj.getClassificationId());
                setNullableInt(pstmt, 5, interfaceObj.getLifecycleId());
                setNullableInt(pstmt, 6, interfaceObj.getStatusId());
                setNullableInt(pstmt, 7, interfaceObj.getSourceSystemId());
                setNullableInt(pstmt, 8, interfaceObj.getTargetSystemId());
                setNullableInt(pstmt, 9, interfaceObj.getAutomationId());
                setNullableInt(pstmt, 10, interfaceObj.getFrequencyId());
                setNullableInt(pstmt, 11, interfaceObj.getTransferMethodId());
                setNullableInt(pstmt, 12, interfaceObj.getTransferFormatId());
                
                pstmt.setString(13, interfaceObj.getAssetId());
                pstmt.setString(14, interfaceObj.getSynchronisationControl());
                setNullableInt(pstmt, 15, interfaceObj.getIsPublic());
                pstmt.setInt(16, userId);
                pstmt.setInt(17, interfaceObj.getId());
                
                int affectedRows = pstmt.executeUpdate();
                
                if (affectedRows > 0) {
                    // الخطوة 2: إنشاء audit records للتحديثات (استخدام نفس الـ connection)
                    //system.out.println("🔍 Starting audit record creation for interface ID: " + interfaceObj.getId());
                    String userName = getPersonFullName(conn, userId);
                    //system.out.println("📌 User name retrieved: " + userName + " for userId: " + userId);
                    
                    if (userName == null || userName.trim().isEmpty()) {
                        System.err.println("⚠️ WARNING: userName is null or empty for userId: " + userId);
                        userName = userId > 0 ? "User ID: " + userId : "System"; // استخدام قيمة افتراضية أفضل
                    }
                    
                    try {
                        createInterfaceUpdateAuditRecords(conn, interfaceObj.getId(), oldInterface, interfaceObj, userName);
                        //system.out.println("✅ Interface update audit records created for ID: " + interfaceObj.getId());
                    } catch (Exception e) {
                        System.err.println("❌ Error creating interface update audit records: " + e.getMessage());
                        e.printStackTrace();
                        // رمي الـ exception مرة أخرى لعمل rollback
                        throw new SQLException("Failed to create audit records: " + e.getMessage(), e);
                    }

                    // الخطوة 3: إنشاء snapshot جديد في interface_audit
                    try {
                        createInterfaceUpdateAuditSnapshot(conn, interfaceObj.getId());
                        //system.out.println("✅ InterfaceDAO: interface_audit update snapshot created for ID: " + interfaceObj.getId());
                    } catch (Exception e) {
                        System.err.println("❌ Error creating interface_audit update snapshot: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                // Commit كل التغييرات
                conn.commit();
                //system.out.println("✅ InterfaceDAO.updateInterface - COMMITTED successfully");
                
                return affectedRows > 0;
            } catch (Exception e) {
                // Rollback في حالة حدوث خطأ
                if (conn != null) {
                    conn.rollback();
                    System.err.println("❌ InterfaceDAO.updateInterface - Transaction rolled back");
                }
                throw e;
            }
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Helper method to set nullable integer parameters
     */
    private void setNullableInt(PreparedStatement pstmt, int parameterIndex, Integer value) throws SQLException {
        if (value != null) {
            pstmt.setInt(parameterIndex, value);
        } else {
            pstmt.setNull(parameterIndex, Types.INTEGER);
        }
    }

    /**
     * إنشاء audit records عند تحديث الـ interface
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    public void createInterfaceUpdateAuditRecords(Connection conn, int interfaceId, Interface oldInterface, Interface newInterface, String userName) throws SQLException {
        //system.out.println("🔍 InterfaceDAO.createInterfaceUpdateAuditRecords - START for ID: " + interfaceId);
        //system.out.println("    📋 Old Interface: " + oldInterface.getName());
        //system.out.println("    📋 New Interface: " + newInterface.getName());
        //system.out.println("    👤 Author: " + userName);
        
        PreparedStatement auditStmt = null;
        int changesCount = 0;
        
        try {
            String auditSql = """
                INSERT INTO interface_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            //system.out.println("🔍 Comparing fields for changes:");
            
            // Name
            //system.out.println("  Name: old='" + oldInterface.getName() + "', new='" + newInterface.getName() + "', equal=" + isEqual(oldInterface.getName(), newInterface.getName()));
            if (!isEqual(oldInterface.getName(), newInterface.getName())) {
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Name", oldInterface.getName(), newInterface.getName(), userName);
                changesCount++;
            }
            
            // Reference Number
            if (!isEqual(oldInterface.getRefNumber(), newInterface.getRefNumber())) {
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Reference Number", oldInterface.getRefNumber(), newInterface.getRefNumber(), userName);
                changesCount++;
            }
            
            // Description
            if (!isEqual(oldInterface.getDescription(), newInterface.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Description", oldInterface.getDescription(), newInterface.getDescription(), userName);
                changesCount++;
            }
            
            // Classification
            if (!isEqual(oldInterface.getClassificationId(), newInterface.getClassificationId())) {
                String oldClassificationName = oldInterface.getClassificationId() != null ? getInterfaceClassificationName(conn, oldInterface.getClassificationId()) : null;
                String newClassificationName = newInterface.getClassificationId() != null ? getInterfaceClassificationName(conn, newInterface.getClassificationId()) : null;
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Classification", oldClassificationName, newClassificationName, userName);
                changesCount++;
            }
            
            // Lifecycle
            if (!isEqual(oldInterface.getLifecycleId(), newInterface.getLifecycleId())) {
                String oldLifecycleName = oldInterface.getLifecycleId() != null ? getInterfaceLifecycleName(conn, oldInterface.getLifecycleId()) : null;
                String newLifecycleName = newInterface.getLifecycleId() != null ? getInterfaceLifecycleName(conn, newInterface.getLifecycleId()) : null;
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Lifecycle", oldLifecycleName, newLifecycleName, userName);
                changesCount++;
            }
            
            // Status (يستخدم "Status Change" كـ updateType)
            if (!isEqual(oldInterface.getStatusId(), newInterface.getStatusId())) {
                String oldStatusName = oldInterface.getStatusId() != null ? getStatusPrimaryName(conn, oldInterface.getStatusId()) : null;
                String newStatusName = newInterface.getStatusId() != null ? getStatusPrimaryName(conn, newInterface.getStatusId()) : null;
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
                changesCount++;
            }
            
            // Source System
            if (!isEqual(oldInterface.getSourceSystemId(), newInterface.getSourceSystemId())) {
                String oldSourceName = oldInterface.getSourceSystemId() != null ? getSystemName(conn, oldInterface.getSourceSystemId()) : null;
                String newSourceName = newInterface.getSourceSystemId() != null ? getSystemName(conn, newInterface.getSourceSystemId()) : null;
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Source System", oldSourceName, newSourceName, userName);
                changesCount++;
            }
            
            // Target System
            if (!isEqual(oldInterface.getTargetSystemId(), newInterface.getTargetSystemId())) {
                String oldTargetName = oldInterface.getTargetSystemId() != null ? getSystemName(conn, oldInterface.getTargetSystemId()) : null;
                String newTargetName = newInterface.getTargetSystemId() != null ? getSystemName(conn, newInterface.getTargetSystemId()) : null;
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Target System", oldTargetName, newTargetName, userName);
                changesCount++;
            }
            
            // Automation
            if (!isEqual(oldInterface.getAutomationId(), newInterface.getAutomationId())) {
                String oldAutomationName = oldInterface.getAutomationId() != null ? getInterfaceAutomationName(conn, oldInterface.getAutomationId()) : null;
                String newAutomationName = newInterface.getAutomationId() != null ? getInterfaceAutomationName(conn, newInterface.getAutomationId()) : null;
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Automation", oldAutomationName, newAutomationName, userName);
                changesCount++;
            }
            
            // Frequency
            if (!isEqual(oldInterface.getFrequencyId(), newInterface.getFrequencyId())) {
                String oldFrequencyName = oldInterface.getFrequencyId() != null ? getInterfaceFrequencyName(conn, oldInterface.getFrequencyId()) : null;
                String newFrequencyName = newInterface.getFrequencyId() != null ? getInterfaceFrequencyName(conn, newInterface.getFrequencyId()) : null;
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Frequency", oldFrequencyName, newFrequencyName, userName);
                changesCount++;
            }
            
            // Transfer Method
            if (!isEqual(oldInterface.getTransferMethodId(), newInterface.getTransferMethodId())) {
                String oldTransferMethodName = oldInterface.getTransferMethodId() != null ? getInterfaceTransferMethodName(conn, oldInterface.getTransferMethodId()) : null;
                String newTransferMethodName = newInterface.getTransferMethodId() != null ? getInterfaceTransferMethodName(conn, newInterface.getTransferMethodId()) : null;
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Transfer Method", oldTransferMethodName, newTransferMethodName, userName);
                changesCount++;
            }
            
            // Transfer Format
            if (!isEqual(oldInterface.getTransferFormatId(), newInterface.getTransferFormatId())) {
                String oldTransferFormatName = oldInterface.getTransferFormatId() != null ? getInterfaceTransferFormatName(conn, oldInterface.getTransferFormatId()) : null;
                String newTransferFormatName = newInterface.getTransferFormatId() != null ? getInterfaceTransferFormatName(conn, newInterface.getTransferFormatId()) : null;
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Transfer Format", oldTransferFormatName, newTransferFormatName, userName);
                changesCount++;
            }
            
            // Is Public
            if (!isEqual(oldInterface.getIsPublic(), newInterface.getIsPublic())) {
                String oldIsPublicName = oldInterface.getIsPublic() != null ? getViewingName(conn, oldInterface.getIsPublic()) : null;
                String newIsPublicName = newInterface.getIsPublic() != null ? getViewingName(conn, newInterface.getIsPublic()) : null;
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Is Public", oldIsPublicName, newIsPublicName, userName);
                changesCount++;
            }
            
            // Asset ID
            if (!isEqual(oldInterface.getAssetId(), newInterface.getAssetId())) {
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Asset ID", oldInterface.getAssetId(), newInterface.getAssetId(), userName);
                changesCount++;
            }
            
            // Synchronisation Control
            if (!isEqual(oldInterface.getSynchronisationControl(), newInterface.getSynchronisationControl())) {
                createUpdateAuditRecord(conn, auditStmt, interfaceId, "Interface", "Details", 
                    "Updated", "Synchronisation Control", oldInterface.getSynchronisationControl(), newInterface.getSynchronisationControl(), userName);
                changesCount++;
            }
            
            //system.out.println("✅ InterfaceDAO.createInterfaceUpdateAuditRecords - " + changesCount + " changes tracked");
            if (changesCount == 0) {
                //system.out.println("⚠️ No changes detected between old and new interface data!");
            }
            
        } catch (SQLException e) {
            System.err.println("❌ InterfaceDAO.createInterfaceUpdateAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            if (auditStmt != null) auditStmt.close();
            // لا نغلق الـ connection لأنه تم تمريره من method أخرى
        }
    }

    /**
     * Helper method لإنشاء audit record للـ update مع from و to
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, 
            int interfaceId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        //system.out.println("       SQL Parameters:");
        //system.out.println("         id=" + interfaceId);
        //system.out.println("         object=" + object);
        //system.out.println("         event=" + event);
        //system.out.println("         updateType=" + updateType);
        //system.out.println("         field=" + field);
        //system.out.println("         from=" + fromValue);
        //system.out.println("         to=" + toValue);
        //system.out.println("         author=" + userName);
        
        auditStmt.setInt(1, interfaceId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, fromValue);      // from
        auditStmt.setString(7, toValue);        // to
        auditStmt.setString(8, userName);
        
        auditStmt.executeUpdate();
        //system.out.println("    ✓ Update audit record inserted, rows affected: " + rowsAffected);
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                int auditId = generatedKeys.getInt(1);
                //system.out.println("    ✓ Generated audit ID: " + auditId);
                return auditId;
            }
        }
        //system.out.println("    ⚠️ No generated key returned!");
        return -1;
    }

    /**
     * Method لإنشاء snapshot جديد في interface_audit عند الـ update
     */
    public void createInterfaceUpdateAuditSnapshot(Connection conn, int interfaceId) throws SQLException {
        //system.out.println("🔍 InterfaceDAO.createInterfaceUpdateAuditSnapshot - Creating update snapshot for ID: " + interfaceId);
        String sql = """
            INSERT INTO interface_audit (
                id, Name, Ref_number, Description, Transfer_Method_ID, Transfer_Format_ID,
                Classification_id, Lifecycle_id, status_id, Source_systemID, Target_systemID,
                Automation_ID, Frequency_ID, is_public, Asset_ID, Synchronisation_Control,
                created_datetime, last_updatedtime, deleted_datetime, createdBy_ID, last_updateuser_id, RevType
            )
            SELECT 
                id, Name, Ref_number, Description, Transfer_Method_ID, Transfer_Format_ID,
                Classification_id, Lifecycle_id, status_id, Source_systemID, Target_systemID,
                Automation_ID, Frequency_ID, is_public, Asset_ID, Synchronisation_Control,
                created_datetime, last_updatedtime, deleted_datetime, createdBy_ID, last_updateuser_id, 'Updated'
            FROM interface 
            WHERE id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interfaceId);
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
     * إنشاء audit records عند حذف الـ interface
     * يسجل عملية الحذف في interface_audit_history و interface_audit
     */
    public void createInterfaceDeleteAuditRecords(Connection conn, int interfaceId, String userName) throws SQLException {
        //system.out.println("🔍 InterfaceDAO.createInterfaceDeleteAuditRecords - START for ID: " + interfaceId);
        //system.out.println("    👤 Author: " + userName);
        
        try {
            // الخطوة 1: إنشاء audit history record
            String auditSql = """
                INSERT INTO interface_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
            """;
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                auditStmt.setInt(1, interfaceId);
                auditStmt.setString(2, "Interface");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Interface");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                //system.out.println("    ✓ Delete audit record created in interface_audit_history");
            }
            
            // الخطوة 2: إنشاء snapshot في interface_audit
            String snapshotSql = """
                INSERT INTO interface_audit (
                    id, Name, Ref_number, Description, Classification_id, Lifecycle_id, status_id,
                    Source_systemID, Target_systemID, Automation_ID, Frequency_ID, 
                    Transfer_Method_ID, Transfer_Format_ID, Asset_ID, Synchronisation_Control,
                    is_public, created_datetime, last_updatedtime, createdBy_ID, last_updateuser_id, RevType
                )
                SELECT 
                    id, Name, Ref_number, Description, Classification_id, Lifecycle_id, status_id,
                    Source_systemID, Target_systemID, Automation_ID, Frequency_ID,
                    Transfer_Method_ID, Transfer_Format_ID, Asset_ID, Synchronisation_Control,
                    is_public, created_datetime, last_updatedtime, createdBy_ID, last_updateuser_id, 'Deleted'
                FROM interface 
                WHERE id = ?
            """;
            try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                snapshotStmt.setInt(1, interfaceId);
                snapshotStmt.executeUpdate();
                //system.out.println("    ✓ Delete snapshot created in interface_audit");
            }
            
            //system.out.println("✅ InterfaceDAO.createInterfaceDeleteAuditRecords - COMPLETED");
        } catch (SQLException e) {
            System.err.println("❌ InterfaceDAO.createInterfaceDeleteAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}



