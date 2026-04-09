package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Dataset;
import com.example.budg_v2.util.ModuleResolver;
import com.example.budg_v2.util.ReferenceNumberGenerator;

import java.sql.*;

public class DatasetDAO {

    public int insert(Dataset dataset, int userId) throws SQLException {
        // Auto-generate RefNumber if empty
        if (ReferenceNumberGenerator.isEmpty(dataset.getRefNumber())) {
            dataset.setRefNumber(ReferenceNumberGenerator.generateDatasetRefNumber());
        }

        String sql = "INSERT INTO `dataset` (`PrimaryName`, `MasterSource`, `RefNumber`, `definition`, `glossary`, `Usage`, `status`, `DatasetType`, `AccessControlType`, `lifecycle`, `Createdby_ID`, `CreateDatetime`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, dataset.getPrimaryName());
            if (dataset.getMasterSource() != null) ps.setInt(2, dataset.getMasterSource()); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, dataset.getRefNumber());
            ps.setString(4, dataset.getDefinition());
            if (dataset.getGlossary() != null) ps.setInt(5, dataset.getGlossary()); else ps.setNull(5, Types.INTEGER);
            if (dataset.getUsage() != null) ps.setString(6, dataset.getUsage()); else ps.setNull(6, Types.VARCHAR);
            ps.setInt(7, dataset.getStatus());
            ps.setInt(8, dataset.getDatasetType());
            ps.setInt(9, dataset.getAccessControlType());
            ps.setInt(10, dataset.getLifecycle());
            ps.setInt(11, userId);

            int affected = ps.executeUpdate();
            if (affected == 0) throw new SQLException("Insert failed, no rows affected");

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int datasetId = rs.getInt(1);
                    
                    // Create audit records after successful insert
                    try {
                        String userName = getPersonFullName(userId);
                        createDatasetAuditRecords(datasetId, userName);
                        createDatasetAuditRecord(datasetId);
                    } catch (Exception e) {
                        //system.out.println("DatasetDAO: Error creating audit records: " + e.getMessage());
                        // Don't fail the insert if audit fails
                    }
                    
                    return datasetId;
                } else {
                    throw new SQLException("Insert succeeded but no ID obtained");
                }
            }
        }
    }

    public boolean update(Dataset dataset, int userId) throws SQLException {
        // الخطوة 1: الحصول على القيم القديمة
        Dataset oldDataset = getDatasetById(dataset.getId());
        if (oldDataset == null) {
            throw new SQLException("Dataset not found with ID: " + dataset.getId());
        }
        
        // Auto-generate RefNumber if empty
        if (ReferenceNumberGenerator.isEmpty(dataset.getRefNumber())) {
            dataset.setRefNumber(ReferenceNumberGenerator.generateDatasetRefNumber());
        }

        String sql = "UPDATE `dataset` SET `PrimaryName` = ?, `MasterSource` = ?, `RefNumber` = ?, `definition` = ?, `glossary` = ?, `Usage` = ?, `status` = ?, `AccessControlType` = ?, `DatasetType` = ?, `lifecycle` = ?, `LastUpdateDatetime` = NOW(), `LastUpdateUser_id` = ? WHERE `ID` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dataset.getPrimaryName());
            if (dataset.getMasterSource() != null) ps.setInt(2, dataset.getMasterSource()); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, dataset.getRefNumber());
            ps.setString(4, dataset.getDefinition());
            if (dataset.getGlossary() != null) ps.setInt(5, dataset.getGlossary()); else ps.setNull(5, Types.INTEGER);
            if (dataset.getUsage() != null) ps.setString(6, dataset.getUsage()); else ps.setNull(6, Types.VARCHAR);
            if (dataset.getStatus() != null) ps.setInt(7, dataset.getStatus()); else ps.setNull(7, Types.INTEGER);
            if (dataset.getAccessControlType() != null) ps.setInt(8, dataset.getAccessControlType()); else ps.setNull(8, Types.INTEGER);
            if (dataset.getDatasetType() != null) ps.setInt(9, dataset.getDatasetType()); else ps.setNull(9, Types.INTEGER);
            if (dataset.getLifecycle() != null) ps.setInt(10, dataset.getLifecycle()); else ps.setNull(10, Types.INTEGER);
            ps.setInt(11, userId);
            ps.setInt(12, dataset.getId());
            
            int affectedRows = ps.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات
                try {
                    String userName = "System"; // Default fallback
                    if (userId > 0) {
                        String fullName = getPersonFullName(userId);
                        if (fullName != null && !fullName.trim().isEmpty()) {
                            userName = fullName;
                        } else {
                            // إذا لم نجد الاسم، استخدم User ID كبديل أفضل من "System"
                            userName = "User ID: " + userId;
                        }
                    }
                    
                    createDatasetUpdateAuditRecords(dataset.getId(), oldDataset, dataset, userName);
                    //system.out.println("✅ Dataset update audit records created for ID: " + dataset.getId() + " with author: " + userName);
                } catch (Exception e) {
                    System.err.println("❌ Error creating dataset update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // الخطوة 3: إنشاء snapshot جديد في dataset_audit
                try {
                    createDatasetUpdateAuditSnapshot(dataset.getId());
                    //system.out.println("✅ DatasetDAO: dataset_audit update snapshot created for ID: " + dataset.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating dataset_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return affectedRows > 0;
        }
    }
    
    /**
     * Helper method to fetch Dataset by ID for comparison purposes
     */
    private Dataset getDatasetById(int id) throws SQLException {
        String sql = "SELECT ID, PrimaryName, MasterSource, RefNumber, definition, glossary, `Usage`, status, DatasetType, AccessControlType, lifecycle FROM dataset WHERE ID = ? AND DeletedDatetime IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Dataset dataset = new Dataset();
                    dataset.setId(rs.getInt("ID"));
                    dataset.setPrimaryName(rs.getString("PrimaryName"));
                    dataset.setMasterSource((Integer) rs.getObject("MasterSource"));
                    dataset.setRefNumber(rs.getString("RefNumber"));
                    dataset.setDefinition(rs.getString("definition"));
                    dataset.setGlossary((Integer) rs.getObject("glossary"));
                    dataset.setUsage(rs.getString("Usage"));
                    dataset.setStatus((Integer) rs.getObject("status"));
                    dataset.setDatasetType((Integer) rs.getObject("DatasetType"));
                    dataset.setAccessControlType((Integer) rs.getObject("AccessControlType"));
                    dataset.setLifecycle((Integer) rs.getObject("lifecycle"));
                    return dataset;
                }
            }
        }
        return null;
    }

    /**
     * Get dataset by ID for bulk update (partial update). Exposes same data as getDatasetById.
     */
    public Dataset getDatasetForUpdate(int id) throws SQLException {
        return getDatasetById(id);
    }

    /**
     * Resolve dataset ID by RefNumber (for bulk update when ID is empty).
     * @return dataset ID if found, null otherwise
     */
    public Integer getDatasetIdByRefNumber(Connection conn, String refNumber) throws SQLException {
        if (refNumber == null || refNumber.isBlank()) {
            return null;
        }
        String sql = "SELECT ID FROM dataset WHERE RefNumber = ? AND DeletedDatetime IS NULL LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, refNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("ID") : null;
            }
        }
    }

    /**
     * Resolve dataset ID by PrimaryName (for bulk update when ID is empty).
     * When {@code masterSourceId} is non-null and positive, the match is scoped to that system
     * so the same name may exist on other systems.
     * When {@code masterSourceId} is null, the name must identify at most one non-deleted dataset;
     * if multiple datasets share the name (under different systems), returns null (ambiguous).
     */
    public Integer getDatasetIdByPrimaryName(Connection conn, String primaryName, Integer masterSourceId) throws SQLException {
        if (primaryName == null || primaryName.isBlank()) {
            return null;
        }
        String trimmed = primaryName.trim();
        if (masterSourceId != null && masterSourceId > 0) {
            String sql = "SELECT ID FROM dataset WHERE LOWER(PrimaryName) = LOWER(?) AND MasterSource = ? AND DeletedDatetime IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trimmed);
                ps.setInt(2, masterSourceId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt("ID") : null;
                }
            }
        }
        String sql = "SELECT ID FROM dataset WHERE LOWER(PrimaryName) = LOWER(?) AND DeletedDatetime IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trimmed);
            try (ResultSet rs = ps.executeQuery()) {
                Integer found = null;
                while (rs.next()) {
                    if (found != null) {
                        return null; // ambiguous: same name on multiple systems
                    }
                    found = rs.getInt("ID");
                }
                return found;
            }
        }
    }

    /** Count non-deleted datasets with this primary name (any system). */
    public int countDatasetsByPrimaryName(Connection conn, String primaryName) throws SQLException {
        if (primaryName == null || primaryName.isBlank()) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM dataset WHERE LOWER(PrimaryName) = LOWER(?) AND DeletedDatetime IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, primaryName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public boolean deleteById(int id) throws SQLException {
        String sql = "DELETE FROM dataset WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public java.util.Map<String, Object> getById(int id) throws SQLException {
        String sql = "SELECT d.ID, d.RefNumber, d.PrimaryName, d.definition, d.Usage, d.MasterSource, s.Name AS systemName, " +
                "d.glossary, g.Name AS glossaryName, g.Parent_ID AS glossaryParentId, pg.Name AS glossaryParentName, d.status, st.primaryname AS statusName, d.DatasetType, dt.PrimaryName AS datasetTypeName, " +
                "d.lifecycle, dl.PrimaryName AS lifecycleName, d.AccessControlType, v.Name AS viewingName, d.DQ_Score, d.DQ_Green, d.DQ_Amber, " +
                "d.CreateDatetime, d.LastUpdateDatetime, d.Createdby_ID, d.LastUpdateUser_id, " +
                "CONCAT(cb.First_Name, ' ', cb.Last_Name) AS createdByName, CONCAT(ub.First_Name, ' ', ub.Last_Name) AS updatedByName " +
                "FROM dataset d " +
                "LEFT JOIN system s ON s.id = d.MasterSource " +
                "LEFT JOIN glossary g ON g.ID = d.glossary " +
                "LEFT JOIN glossary pg ON pg.ID = g.Parent_ID " +
                "LEFT JOIN status st ON st.ID = d.status " +
                "LEFT JOIN dataset_type dt ON dt.ID = d.DatasetType " +
                "LEFT JOIN dataset_lifecycle dl ON dl.ID = d.lifecycle " +
                "LEFT JOIN viewing v ON v.id = d.AccessControlType " +
                "LEFT JOIN people cb ON cb.ID = d.Createdby_ID " +
                "LEFT JOIN people ub ON ub.ID = d.LastUpdateUser_id " +
                "WHERE d.ID = ? AND d.DeletedDatetime IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("ref", rs.getString("RefNumber"));
                row.put("name", rs.getString("PrimaryName"));
                row.put("definition", rs.getString("definition"));
                row.put("usage", rs.getString("Usage"));
                row.put("systemId", (Object) rs.getObject("MasterSource"));
                row.put("systemName", rs.getString("systemName"));
                row.put("glossaryId", (Object) rs.getObject("glossary"));
                row.put("glossaryName", rs.getString("glossaryName"));
                row.put("glossaryParentId", (Object) rs.getObject("glossaryParentId"));
                row.put("glossaryParentName", rs.getString("glossaryParentName"));
                
                // Get full glossary hierarchy (all ancestors from top-level to immediate parent)
                Integer glossaryId = (Integer) rs.getObject("glossary");
                java.util.List<String> glossaryHierarchy = getGlossaryAncestorNames(glossaryId);
                row.put("glossaryHierarchy", glossaryHierarchy);
                
                row.put("statusId", (Object) rs.getObject("status"));
                row.put("statusName", rs.getString("statusName"));
                row.put("typeId", (Object) rs.getObject("DatasetType"));
                row.put("typeName", rs.getString("datasetTypeName"));
                row.put("lifecycleId", (Object) rs.getObject("lifecycle"));
                row.put("lifecycleName", rs.getString("lifecycleName"));
                row.put("viewingId", (Object) rs.getObject("AccessControlType"));
                row.put("viewingName", rs.getString("viewingName"));
                row.put("dqScore", (Object) rs.getObject("DQ_Score"));
                row.put("dqGreen", (Object) rs.getObject("DQ_Green"));
                row.put("dqAmber", (Object) rs.getObject("DQ_Amber"));
                row.put("created", rs.getTimestamp("CreateDatetime"));
                row.put("lastUpdated", rs.getTimestamp("LastUpdateDatetime"));
                row.put("createdById", (Object) rs.getObject("Createdby_ID"));
                row.put("updatedById", (Object) rs.getObject("LastUpdateUser_id"));
                row.put("createdByName", rs.getString("createdByName"));
                row.put("updatedByName", rs.getString("updatedByName"));
                
                // Get segment info for this dataset
                int datasetId = rs.getInt("ID");
                java.util.Map<String, Object> segmentInfo = getDatasetSegment(datasetId);
                row.put("segmentId", segmentInfo.get("segmentId"));
                row.put("segmentName", segmentInfo.get("segmentName"));
                
                return row;
            }
        }
    }
    
    /**
     * Get segment info for a dataset
     * @param datasetId The dataset ID
     * @return Map with segmentId and segmentName (null/"Not Specified" if not assigned)
     */
    private java.util.Map<String, Object> getDatasetSegment(int datasetId) throws SQLException {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("segmentId", null); // null if not assigned - show "Not Specified"
        result.put("segmentName", "Not Specified");
        
        String sql = """
            SELECT s.ID AS segment_id, s.Name AS segment_name
            FROM segment_x_resource sxr
            JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
            JOIN segment s ON sxr.Segment_ID = s.ID
            WHERE orr.Object_ID = ?
            AND sot.Type = 'Dataset'
            AND sxr.Deleted_At IS NULL
            AND s.Deleted_At IS NULL
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("segmentId", rs.getInt("segment_id"));
                    result.put("segmentName", rs.getString("segment_name"));
                }
            }
        }
        
        return result;
    }

    public java.util.List<java.util.Map<String, Object>> getDirectStakeholdersForDataset(int datasetId) throws SQLException {
        String sql = """
            SELECT 
                oxp.ID as object_x_ipid,
                oxp.ipid AS people_id,
                oxp.RoleID AS role_id,
                oxp.statusID AS status_id,
                st.primaryname AS status_name,
                oxp.isDelegateOF AS delegate_of_id,
                r.PrimaryName AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                ou.Name AS org_unit,
                CASE oxp.AcceptedID WHEN 1 THEN 'True' ELSE 'False' END AS role_accepted,
                CONCAT(dp.First_Name, ' ', dp.Last_Name) AS delegate_of,
                oxp.createdatetime AS date_accepted
            FROM dataset_x_objectxpeople dx
            JOIN object_x_people oxp ON dx.Object_x_ipid = oxp.ID
            JOIN object_role r ON oxp.RoleID = r.ID
            JOIN people p ON oxp.ipid = p.ID
            JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
            LEFT JOIN status st ON oxp.statusID = st.ID
            LEFT JOIN object_x_people d_oxp ON oxp.isDelegateOF = d_oxp.ID
            LEFT JOIN people dp ON d_oxp.ipid = dp.ID
            WHERE dx.Dataset_ID = ?
            ORDER BY r.PrimaryName, p.Last_Name, p.First_Name
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("object_x_ipid", rs.getInt("object_x_ipid"));
                    row.put("peopleId", (Object) rs.getObject("people_id"));
                    row.put("roleId", (Object) rs.getObject("role_id"));
                    row.put("statusId", (Object) rs.getObject("status_id"));
                    row.put("delegateOfId", (Object) rs.getObject("delegate_of_id"));
                    row.put("statusName", rs.getString("status_name"));
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

    public java.util.List<java.util.Map<String, Object>> getDatasetsByGlossaryId(int glossaryId) throws SQLException {
        String sql = "SELECT d.ID, d.RefNumber, d.PrimaryName, d.definition, d.MasterSource, s.Name AS systemName " +
                "FROM dataset d " +
                "LEFT JOIN system s ON s.id = d.MasterSource " +
                "WHERE d.glossary = ? AND d.DeletedDatetime IS NULL " +
                "ORDER BY d.PrimaryName";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("refNumber", rs.getString("RefNumber"));
                    row.put("primaryName", rs.getString("PrimaryName"));
                    row.put("definition", rs.getString("definition"));
                    row.put("masterSource", (Object) rs.getObject("MasterSource"));
                    row.put("systemName", rs.getString("systemName"));
                    results.add(row);
                }
                return results;
            }
        }
    }
    public java.util.List<java.util.Map<String, Object>> getRolesForDataset(int datasetId) throws SQLException {
        // Get module ID dynamically based on entity type
        int moduleId = ModuleResolver.getModuleId("dataset");
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
                java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("name", rs.getString("primaryname"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public java.util.List<java.util.Map<String, Object>> getUsersByRole(int datasetId, int roleId) throws SQLException {
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
                java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("name"));
                    row.put("email", rs.getString("Email"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public java.util.List<java.util.Map<String, Object>> getStatusesForDataset(int datasetId) throws SQLException {
        String sql = """
            SELECT ID, primaryname
            FROM status
            ORDER BY primaryname
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("primaryname"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public void saveStakeholdersChanges(int datasetId, java.util.Map<String, Object> changes, int currentUserId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                @SuppressWarnings("unchecked") java.util.List<java.util.Map<String, Object>> inserts = (java.util.List<java.util.Map<String, Object>>) changes.getOrDefault("inserts", java.util.Collections.emptyList());
                @SuppressWarnings("unchecked") java.util.List<java.util.Map<String, Object>> updates = (java.util.List<java.util.Map<String, Object>>) changes.getOrDefault("updates", java.util.Collections.emptyList());
                @SuppressWarnings("unchecked") java.util.List<java.util.Map<String, Object>> deletes = (java.util.List<java.util.Map<String, Object>>) changes.getOrDefault("deletes", java.util.Collections.emptyList());

                for (java.util.Map<String, Object> row : inserts) {
                    validateRequired(row);
                    int oxpId = createObjectXPeople(conn, convertRow(conn, row), currentUserId);
                    linkStakeholderToDataset(conn, datasetId, oxpId, currentUserId);
                    
                    // Create audit record for the new stakeholder
                    try {
                        String userName = getPersonFullName((Integer) row.get("ipid"));
                        String userFullName = getPersonFullName((Integer) row.get("ipid"));
                        Integer roleId = getInt(row.get("roleId"));
                        if (userName != null && userFullName != null && roleId != null) {
                            createStakeholderAuditRecord(datasetId, userName, userFullName, roleId);
                        }
                    } catch (Exception e) {
                        //system.out.println("DatasetDAO: Error creating stakeholder audit record: " + e.getMessage());
                        // Don't fail the transaction if audit fails
                    }
                }

                for (java.util.Map<String, Object> row : updates) {
                    validateRequired(row);
                    Integer oxpId = getInt(row.get("objectXPeopleId"));
                    if (oxpId == null) throw new IllegalArgumentException("Missing objectXPeopleId in update row");
                    updateObjectXPeople(conn, oxpId, convertRow(conn, row));
                }

                for (java.util.Map<String, Object> row : deletes) {
                    Integer oxpId = getInt(row.get("objectXPeopleId"));
                    if (oxpId == null) throw new IllegalArgumentException("Missing objectXPeopleId in delete row");
                    unlinkStakeholderFromDataset(conn, datasetId, oxpId);
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

    private java.util.Map<String, Object> convertRow(Connection conn, java.util.Map<String, Object> row) throws SQLException {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        // Required fields per request: roleId, ipid, statusId
        m.put("roleId", getInt(row.get("roleId")));
        m.put("userId", getInt(row.get("ipid")));
        // statusId maps to statusID column in object_x_people (not AcceptedID)
        m.put("statusId", getInt(row.get("statusId")));
        // Accepted is always true (1)
        m.put("acceptedId", 1);
        // Delegate is provided as object_x_people.id via 'delegateIpId' in payload
        Integer delegateOxpId = getInt(row.get("delegateIpId"));
        m.put("delegateOfId", delegateOxpId);
        return m;
    }

    private void validateRequired(java.util.Map<String, Object> row) {
        if (getInt(row.get("roleId")) == null) throw new IllegalArgumentException("Role is required");
        if (getInt(row.get("ipid")) == null) throw new IllegalArgumentException("Name is required");
    }

    private Integer getInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }

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

    private void updateObjectXPeople(Connection conn, int objectXPeopleId, java.util.Map<String, Object> stakeholder) throws SQLException {
        String sql = """
            UPDATE object_x_people 
            SET RoleID = ?, ipid = ?, statusID = ?, AcceptedID = 2, isDelegateOF = ?, lastupdatedatetime = NOW()
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
            ps.executeUpdate(); // If already deleted, ignore
        }
    }

    public void linkStakeholderToDataset(Connection conn, int datasetId, int objectXPeopleId, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO dataset_x_objectxpeople (Dataset_ID, Object_x_ipid, Last_UpdateUser_ID, CreateDatetime)
            VALUES (?, ?, ?, NOW())
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, currentUserId);
            try {
                ps.executeUpdate();
                //system.out.println("✅ Successfully linked stakeholder to dataset: Dataset_ID=" + datasetId + ", Object_x_ipid=" + objectXPeopleId + ", rows affected=" + rowsAffected);
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: Dataset_ID=" + datasetId + ", Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }

    private void unlinkStakeholderFromDataset(Connection conn, int datasetId, int objectXPeopleId) throws SQLException {
        String sql = "DELETE FROM dataset_x_objectxpeople WHERE Dataset_ID = ? AND Object_x_ipid = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            ps.setInt(2, objectXPeopleId);
            ps.executeUpdate(); // If link not present, ignore
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
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int datasetId, String updateType, String field, String value, String userName) throws SQLException {
        //system.out.println("DatasetDAO: createNewAuditRecord called - datasetId: " + datasetId + ", field: " + field + ", value: " + value);
        
        auditStmt.setInt(1, datasetId);        // id
        auditStmt.setString(2, updateType);    // updateType
        auditStmt.setString(3, field);          // field
        auditStmt.setString(4, value);         // to
        auditStmt.setString(5, userName);       // author
        
        //system.out.println("DatasetDAO: Executing audit insert for field: " + field);
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                int auditId = generatedKeys.getInt(1);
                //system.out.println("DatasetDAO: Audit record created with ID: " + auditId);
                return auditId;
            }
        }
        //system.out.println("DatasetDAO: No generated key returned");
        return -1;
    }

    /**
     * إنشاء audit records للـ dataset الجديد
     * يتم استدعاء هذا method بعد إنشاء الـ dataset بنجاح
     */
    public void createDatasetAuditRecords(int datasetId, String userName) throws SQLException {
        //system.out.println("DatasetDAO: createDatasetAuditRecords called with datasetId: " + datasetId + ", userName: " + userName);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            //system.out.println("DatasetDAO: Transaction started");
            
            // 1. الحصول على بيانات الـ dataset
            String datasetDataSql = "SELECT * FROM dataset WHERE ID = ?";
            //system.out.println("DatasetDAO: Executing SQL: " + datasetDataSql);
            PreparedStatement datasetStmt = conn.prepareStatement(datasetDataSql);
            datasetStmt.setInt(1, datasetId);
            ResultSet datasetRs = datasetStmt.executeQuery();
            
            if (!datasetRs.next()) {
                //system.out.println("DatasetDAO: Dataset not found with ID: " + datasetId);
                throw new SQLException("Dataset not found with ID: " + datasetId);
            }
            //system.out.println("DatasetDAO: Dataset found, starting audit record creation");

            // 2. إعداد audit statement
            String auditSql = """
                INSERT INTO dataset_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Dataset', 'Details', ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            String primaryName = datasetRs.getString("PrimaryName");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Primary Name", primaryName, userName);
            }
            
            // Definition
            String definition = datasetRs.getString("definition");
            if (definition != null && !definition.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Definition", definition, userName);
            }
            
            // Reference Number
            String refNumber = datasetRs.getString("RefNumber");
            if (refNumber != null && !refNumber.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Reference Number", refNumber, userName);
            }
            
            // Usage
            String usage = datasetRs.getString("Usage");
            if (usage != null && !usage.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Usage", usage, userName);
            }
            
            // Master Source
            Integer masterSourceId = datasetRs.getObject("MasterSource", Integer.class);
            if (masterSourceId != null) {
                String masterSourceName = getSystemName(masterSourceId);
                if (masterSourceName != null) {
                    createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Master Source", masterSourceName, userName);
                }
            }
            
            // Glossary
            Integer glossaryId = datasetRs.getObject("glossary", Integer.class);
            if (glossaryId != null) {
                String glossaryName = getGlossaryName(glossaryId);
                if (glossaryName != null) {
                    createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Glossary", glossaryName, userName);
                }
            }
            
            // Status
            Integer statusId = datasetRs.getObject("status", Integer.class);
            if (statusId != null) {
                String statusName = getStatusPrimaryName(statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, datasetId, "Status Change", "Status", statusName, userName);
                }
            }
            
            // Dataset Type
            Integer datasetTypeId = datasetRs.getObject("DatasetType", Integer.class);
            if (datasetTypeId != null) {
                String datasetTypeName = getDatasetTypeName(datasetTypeId);
                if (datasetTypeName != null) {
                    createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Dataset Type", datasetTypeName, userName);
                }
            }
            
            // Access Control Type
            Integer accessControlTypeId = datasetRs.getObject("AccessControlType", Integer.class);
            if (accessControlTypeId != null) {
                String accessControlTypeName = getViewingName(accessControlTypeId);
                if (accessControlTypeName != null) {
                    createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Access Control Type", accessControlTypeName, userName);
                }
            }
            
            // Lifecycle
            Integer lifecycleId = datasetRs.getObject("lifecycle", Integer.class);
            if (lifecycleId != null) {
                String lifecycleName = getDatasetLifecycleName(lifecycleId);
                if (lifecycleName != null) {
                    createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Lifecycle", lifecycleName, userName);
                }
            }
            
            // Created By
            Integer createdById = datasetRs.getObject("Createdby_ID", Integer.class);
            if (createdById != null) {
                String createdByName = getPersonFullName(createdById);
                if (createdByName != null) {
                    createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Created By", createdByName, userName);
                }
            }
            
            // Note: Stakeholders will be added later through the stakeholder management interface
            // No need to create stakeholder audit records here as they don't exist yet
            
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
     * إنشاء سجل في جدول dataset_audit بعد إنشاء الـ dataset
     * يتم استدعاء هذا method بعد إنشاء الـ dataset بنجاح
     */
    public void createDatasetAuditRecord(int datasetId) throws SQLException {
        String sql = """
            INSERT INTO dataset_audit (
                ID, ValueInfoID, AccessControlType, DatasetType, locked, MasterSource, lifecycle, status, glossary, 
                RefNumber, version, PrimaryName, definition, `Usage`, AccessControlType_Desc, CreateDatetime, 
                LastUpdateDatetime, DeletedDatetime, DQ_Score, DQ_Green, DQ_Amber, Createdby_ID, LastUpdateUser_id, RevType
            )
            SELECT 
                ID, ValueInfoID, AccessControlType, DatasetType, locked, MasterSource, lifecycle, status, glossary, 
                RefNumber, version, PrimaryName, definition, `Usage`, AccessControlType_Desc, CreateDatetime, 
                LastUpdateDatetime, DeletedDatetime, DQ_Score, DQ_Green, DQ_Amber, Createdby_ID, LastUpdateUser_id, 'Added'
            FROM dataset 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            ps.executeUpdate();
        }
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إضافته للـ dataset
     * يتم استدعاء هذا method عند إضافة stakeholder جديد
     */
    public void createStakeholderAuditRecord(int datasetId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. إعداد audit statement للـ stakeholder
            String auditSql = """
                INSERT INTO dataset_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Stakeholder', 'link', ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // 2. إدراج 3 سجلات للـ stakeholder
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleId(datasetId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }
            
            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getRoleName(actualRoleId);
            if (roleName == null) roleName = "Dataset Owner"; // fallback
            
            // Role
            createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Role", roleName, userName);
            
            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusId(datasetId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(statusId);
                if (fetchedStatusName != null) statusName = fetchedStatusName;
            }
            createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Role Status", statusName, userName);
            
            // Name
            createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Name", userFullName, userName);
            
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


    // Helper methods للحصول على الأسماء
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
    
    // Overloaded method that uses existing connection
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

    private String getGlossaryName(int glossaryId) throws SQLException {
        String sql = "SELECT Name FROM glossary WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    /**
     * Get all ancestor names for a glossary in order from top-level to immediate parent
     * Returns an empty list if glossaryId is null or has no parents
     */
    private java.util.List<String> getGlossaryAncestorNames(Integer glossaryId) throws SQLException {
        java.util.List<String> ancestors = new java.util.ArrayList<>();
        
        if (glossaryId == null) {
            return ancestors;
        }
        
        // Use recursive CTE to traverse up the glossary hierarchy
        String sql = "WITH RECURSIVE ancestors AS (" +
                // Base case: get the immediate parent of the given glossary
                "    SELECT g.ID, g.Parent_ID, g.Name, 1 as level " +
                "    FROM glossary g " +
                "    WHERE g.ID = (SELECT Parent_ID FROM glossary WHERE ID = ?) AND g.ID IS NOT NULL AND g.Deleted_datetime IS NULL " +
                "    UNION ALL " +
                // Recursive case: get parent of current ancestor
                "    SELECT g.ID, g.Parent_ID, g.Name, a.level + 1 " +
                "    FROM glossary g " +
                "    INNER JOIN ancestors a ON g.ID = a.Parent_ID " +
                "    WHERE a.level < 10 AND g.Deleted_datetime IS NULL " + // Prevent infinite recursion
                ") " +
                "SELECT Name, level FROM ancestors ORDER BY level DESC"; // DESC to get top-level first
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("Name");
                    if (name != null) {
                        ancestors.add(name);
                    }
                }
            }
        }
        
        return ancestors;
    }
    
    // Overloaded method that uses existing connection
    private String getGlossaryName(Connection conn, int glossaryId) throws SQLException {
        String sql = "SELECT Name FROM glossary WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getStatusPrimaryName(int statusId) throws SQLException {
        //system.out.println("DatasetDAO: getStatusPrimaryName called with statusId: " + statusId);
        
        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT PrimaryName FROM status WHERE ID = ?";
            //system.out.println("DatasetDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("PrimaryName");
                        //system.out.println("DatasetDAO: Found PrimaryName: " + result);
                        return result;
                    } else {
                        //system.out.println("DatasetDAO: No record found for statusId: " + statusId);
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("DatasetDAO: Error with PrimaryName - " + e.getMessage());
        }
        
        // Try primaryname as fallback
        try {
            String sql = "SELECT primaryname FROM status WHERE ID = ?";
            //system.out.println("DatasetDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("primaryname");
                        //system.out.println("DatasetDAO: Found primaryname: " + result);
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("DatasetDAO: Error with primaryname - " + e.getMessage());
        }
        
        //system.out.println("DatasetDAO: Using fallback for statusId: " + statusId);
        return "Status " + statusId; // Fallback
    }
    
    // Overloaded method that uses existing connection
    private String getStatusPrimaryName(Connection conn, int statusId) throws SQLException {
        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT PrimaryName FROM status WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("PrimaryName");
                    }
                }
            }
        } catch (SQLException e) {
            // Continue to fallback
        }
        
        // Try primaryname as fallback
        try {
            String sql = "SELECT primaryname FROM status WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("primaryname");
                    }
                }
            }
        } catch (SQLException e) {
            // Continue to fallback
        }
        
        return "Status " + statusId; // Fallback
    }

    private String getDatasetTypeName(int datasetTypeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM dataset_type WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }
    
    // Overloaded method that uses existing connection
    private String getDatasetTypeName(Connection conn, int datasetTypeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM dataset_type WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getDatasetLifecycleName(int lifecycleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM dataset_lifecycle WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }
    
    // Overloaded method that uses existing connection
    private String getDatasetLifecycleName(Connection conn, int lifecycleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM dataset_lifecycle WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
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
    

    private String getViewingName(int viewingId) throws SQLException {
        //system.out.println("DatasetDAO: getViewingName called with viewingId: " + viewingId);
        
        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT Name FROM viewing WHERE id = ?";
            //system.out.println("DatasetDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, viewingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("PrimaryName");
                        //system.out.println("DatasetDAO: Found PrimaryName: " + result);
                        return result;
                    } else {
                        //system.out.println("DatasetDAO: No record found for viewingId: " + viewingId);
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("DatasetDAO: Error with PrimaryName - " + e.getMessage());
        }
        
        // Try Name as fallback
        try {
            String sql = "SELECT Name FROM viewing WHERE id = ?";
            //system.out.println("DatasetDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, viewingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("Name");
                        //system.out.println("DatasetDAO: Found Name: " + result);
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("DatasetDAO: Error with Name - " + e.getMessage());
        }
        
        //system.out.println("DatasetDAO: Using fallback for viewingId: " + viewingId);
        return "Viewing " + viewingId; // Fallback
    }
    
    // Overloaded method that uses existing connection
    private String getViewingName(Connection conn, int viewingId) throws SQLException {
        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT Name FROM viewing WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, viewingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("PrimaryName");
                    }
                }
            }
        } catch (SQLException e) {
            // Continue to fallback
        }
        
        // Try Name as fallback
        try {
            String sql = "SELECT Name FROM viewing WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, viewingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("Name");
                    }
                }
            }
        } catch (SQLException e) {
            // Continue to fallback
        }
        
        return "Viewing " + viewingId; // Fallback
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
    private Integer getStakeholderRoleId(int datasetId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN dataset_x_objectxpeople dxo ON dxo.Object_x_ipid = oxp.ID " +
                    "WHERE dxo.Dataset_ID = ? " +
                    "ORDER BY dxo.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }
    
    // Get statusID from object_x_people for the stakeholder
    private Integer getStakeholderStatusId(int datasetId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN dataset_x_objectxpeople dxo ON dxo.Object_x_ipid = oxp.ID " +
                    "WHERE dxo.Dataset_ID = ? " +
                    "ORDER BY dxo.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
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
     * إنشاء audit records عند تحديث الـ dataset
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    private void createDatasetUpdateAuditRecords(int datasetId, Dataset oldDataset, Dataset newDataset, String userName) throws SQLException {
        //system.out.println("🔍 DatasetDAO.createDatasetUpdateAuditRecords - START for ID: " + datasetId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO dataset_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldDataset.getPrimaryName(), newDataset.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, datasetId, "Dataset", "Details", 
                    "Updated", "Primary Name", oldDataset.getPrimaryName(), newDataset.getPrimaryName(), userName);
            }
            
            // Ref Number
            if (!isEqual(oldDataset.getRefNumber(), newDataset.getRefNumber())) {
                createUpdateAuditRecord(conn, auditStmt, datasetId, "Dataset", "Details", 
                    "Updated", "Reference Number", oldDataset.getRefNumber(), newDataset.getRefNumber(), userName);
            }
            
            // Definition
            if (!isEqual(oldDataset.getDefinition(), newDataset.getDefinition())) {
                createUpdateAuditRecord(conn, auditStmt, datasetId, "Dataset", "Details", 
                    "Updated", "Definition", oldDataset.getDefinition(), newDataset.getDefinition(), userName);
            }
            
            // Usage
            if (!isEqual(oldDataset.getUsage(), newDataset.getUsage())) {
                createUpdateAuditRecord(conn, auditStmt, datasetId, "Dataset", "Details", 
                    "Updated", "Usage", oldDataset.getUsage(), newDataset.getUsage(), userName);
            }
            
            // Master Source (store Name for readability in audit_history)
            if (!isEqual(oldDataset.getMasterSource(), newDataset.getMasterSource())) {
                String oldSystemName = oldDataset.getMasterSource() != null ? getSystemName(conn, oldDataset.getMasterSource()) : null;
                String newSystemName = newDataset.getMasterSource() != null ? getSystemName(conn, newDataset.getMasterSource()) : null;
                createUpdateAuditRecord(conn, auditStmt, datasetId, "Dataset", "Details", 
                    "Updated", "Master Source", oldSystemName, newSystemName, userName);
            }
            
            // Glossary (store Name for readability in audit_history)
            if (!isEqual(oldDataset.getGlossary(), newDataset.getGlossary())) {
                String oldGlossaryName = oldDataset.getGlossary() != null ? getGlossaryName(conn, oldDataset.getGlossary()) : null;
                String newGlossaryName = newDataset.getGlossary() != null ? getGlossaryName(conn, newDataset.getGlossary()) : null;
                createUpdateAuditRecord(conn, auditStmt, datasetId, "Dataset", "Details", 
                    "Updated", "Glossary", oldGlossaryName, newGlossaryName, userName);
            }
            
            // Status (store Name for readability in audit_history)
            if (!isEqual(oldDataset.getStatus(), newDataset.getStatus())) {
                String oldStatusName = oldDataset.getStatus() != null ? getStatusPrimaryName(conn, oldDataset.getStatus()) : null;
                String newStatusName = newDataset.getStatus() != null ? getStatusPrimaryName(conn, newDataset.getStatus()) : null;
                createUpdateAuditRecord(conn, auditStmt, datasetId, "Dataset", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
            // Dataset Type (store Name for readability in audit_history)
            if (!isEqual(oldDataset.getDatasetType(), newDataset.getDatasetType())) {
                String oldTypeName = oldDataset.getDatasetType() != null ? getDatasetTypeName(conn, oldDataset.getDatasetType()) : null;
                String newTypeName = newDataset.getDatasetType() != null ? getDatasetTypeName(conn, newDataset.getDatasetType()) : null;
                createUpdateAuditRecord(conn, auditStmt, datasetId, "Dataset", "Details", 
                    "Updated", "Dataset Type", oldTypeName, newTypeName, userName);
            }
            
            // Access Control Type (store Name for readability in audit_history)
            if (!isEqual(oldDataset.getAccessControlType(), newDataset.getAccessControlType())) {
                String oldViewingName = oldDataset.getAccessControlType() != null ? getViewingName(conn, oldDataset.getAccessControlType()) : null;
                String newViewingName = newDataset.getAccessControlType() != null ? getViewingName(conn, newDataset.getAccessControlType()) : null;
                createUpdateAuditRecord(conn, auditStmt, datasetId, "Dataset", "Details", 
                    "Updated", "Access Control Type", oldViewingName, newViewingName, userName);
            }
            
            // Lifecycle (store Name for readability in audit_history)
            if (!isEqual(oldDataset.getLifecycle(), newDataset.getLifecycle())) {
                String oldLifecycleName = oldDataset.getLifecycle() != null ? getDatasetLifecycleName(conn, oldDataset.getLifecycle()) : null;
                String newLifecycleName = newDataset.getLifecycle() != null ? getDatasetLifecycleName(conn, newDataset.getLifecycle()) : null;
                createUpdateAuditRecord(conn, auditStmt, datasetId, "Dataset", "Details", 
                    "Updated", "Lifecycle", oldLifecycleName, newLifecycleName, userName);
            }
            
            conn.commit();
            //system.out.println("✅ DatasetDAO.createDatasetUpdateAuditRecords - COMMITTED successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ DatasetDAO.createDatasetUpdateAuditRecords - ERROR: " + e.getMessage());
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
            int datasetId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, datasetId);
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
     * Method لإنشاء snapshot جديد في dataset_audit عند الـ update
     */
    private void createDatasetUpdateAuditSnapshot(int datasetId) throws SQLException {
        //system.out.println("🔍 DatasetDAO.createDatasetUpdateAuditSnapshot - Creating update snapshot for ID: " + datasetId);
        String sql = """
            INSERT INTO dataset_audit (
                ID, ValueInfoID, AccessControlType, DatasetType, locked, MasterSource, lifecycle, status, glossary, 
                RefNumber, version, PrimaryName, definition, `Usage`, AccessControlType_Desc, CreateDatetime, 
                LastUpdateDatetime, DeletedDatetime, DQ_Score, DQ_Green, DQ_Amber, Createdby_ID, LastUpdateUser_id, RevType
            )
            SELECT 
                ID, ValueInfoID, AccessControlType, DatasetType, locked, MasterSource, lifecycle, status, glossary, 
                RefNumber, version, PrimaryName, definition, `Usage`, AccessControlType_Desc, CreateDatetime, 
                LastUpdateDatetime, DeletedDatetime, DQ_Score, DQ_Green, DQ_Amber, Createdby_ID, LastUpdateUser_id, 'Updated'
            FROM dataset 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            ps.executeUpdate();
            //system.out.println("✅ Update snapshot created, rows affected: " + rows);
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Helper method لتطبيع القيم (تحويل null و empty string و 0 إلى null)
     */
    private String normalizeValue(Object value) {
        if (value == null) return null;
        
        // التعامل مع Integer الذي قيمته 0 (يعتبر null في سياق foreign keys)
        if (value instanceof Integer && ((Integer) value) == 0) {
            return null;
        }
        
        String strValue = value.toString().trim();
        // تحويل empty string و "null" string و "0" string إلى null
        if (strValue.isEmpty() || strValue.equalsIgnoreCase("null") || strValue.equals("0")) {
            return null;
        }
        return strValue;
    }

    /**
     * Helper method للمقارنة بين القيم (يتعامل مع null و empty strings و 0)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        // تطبيع القيم أولاً
        String normalized1 = normalizeValue(obj1);
        String normalized2 = normalizeValue(obj2);
        
        // المقارنة بعد التطبيع
        if (normalized1 == null && normalized2 == null) return true;
        if (normalized1 == null || normalized2 == null) return false;
        return normalized1.equals(normalized2);
    }

    /**
     * حذف Dataset مع تسجيل audit records
     */
    public boolean deleteDatasetWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE dataset SET DeletedDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO dataset_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Dataset");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Dataset");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في dataset_audit
                String snapshotSql = """
                    INSERT INTO dataset_audit (
                        ID, ValueInfoID, AccessControlType, DatasetType, locked, MasterSource, lifecycle, status, glossary, 
                        RefNumber, version, PrimaryName, definition, `Usage`, AccessControlType_Desc, CreateDatetime, 
                        LastUpdateDatetime, DeletedDatetime, DQ_Score, DQ_Green, DQ_Amber, Createdby_ID, LastUpdateUser_id, RevType
                    )
                    SELECT 
                        ID, ValueInfoID, AccessControlType, DatasetType, locked, MasterSource, lifecycle, status, glossary, 
                        RefNumber, version, PrimaryName, definition, `Usage`, AccessControlType_Desc, CreateDatetime, 
                        LastUpdateDatetime, DeletedDatetime, DQ_Score, DQ_Green, DQ_Amber, Createdby_ID, LastUpdateUser_id, 'Deleted'
                    FROM dataset 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Dataset deleted with audit for ID: " + id);
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
     * Create stakeholder audit records for dataset
     */
    public void createStakeholderAuditRecords(int datasetId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO dataset_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Stakeholder', 'link', ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Get role name
            String roleName = getRoleName(roleId);
            if (roleName == null) roleName = "Dataset Owner";
            
            // Role
            createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Role", roleName, userName);
            
            // Role Status
            createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Role Status", "Active", userName);
            
            // Name
            createNewAuditRecord(conn, auditStmt, datasetId, "Added", "Name", userFullName, userName);
            
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
     * List datasets filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to.
     * 
     * @param userId The user ID to filter for
     * @return List of accessible datasets
     */
    public java.util.List<java.util.Map<String, Object>> listDatasetsBySegmentAccess(int userId) throws SQLException {
        // Get the segment filter clause
        String segmentFilter = com.example.budg_v2.service.SegmentAccessService
                .buildSelectedSegmentFilterClause(userId, "Dataset", "d.ID");
        
        String sql = "SELECT d.ID, d.PrimaryName, d.definition, d.RefNumber " +
                     "FROM dataset d " +
                     "WHERE d.DeletedDatetime IS NULL AND " + segmentFilter +
                     " ORDER BY d.PrimaryName";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("name", rs.getString("PrimaryName"));
                row.put("primaryName", rs.getString("PrimaryName"));
                row.put("description", rs.getString("definition"));
                row.put("definition", rs.getString("definition"));
                row.put("refNumber", rs.getString("RefNumber"));
                results.add(row);
            }
            return results;
        }
    }

    /**
     * List datasets for Attribute bulk update with additional constraint:
     * - Only Enterprise datasets (segment 1 OR unassigned) OR datasets in the selected attribute segment
     * - Still respects user's selected segments (cube filter)
     * 
     * @param userId The user ID to filter for
     * @param attributeSegmentId The segment ID of the attributes being updated
     * @return List of accessible datasets
     */
    public java.util.List<java.util.Map<String, Object>> listDatasetsBySegmentAccessAndAttributeSegment(int userId, int attributeSegmentId) throws SQLException {
        String segmentFilter = com.example.budg_v2.service.SegmentAccessService
                .buildSelectedSegmentFilterClause(userId, "Dataset", "d.ID");

        String allowedSegmentSql = buildEnterpriseOrSameSegmentConstraintSql(attributeSegmentId);

        String sql = "SELECT d.ID, d.PrimaryName, d.definition, d.RefNumber " +
                "FROM dataset d " +
                "WHERE d.DeletedDatetime IS NULL AND " + segmentFilter + " AND " + allowedSegmentSql +
                " ORDER BY d.PrimaryName";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            idx = bindEnterpriseOrSameSegmentConstraintParams(ps, idx, attributeSegmentId);

            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("name", rs.getString("PrimaryName"));
                    row.put("primaryName", rs.getString("PrimaryName"));
                    row.put("description", rs.getString("definition"));
                    row.put("definition", rs.getString("definition"));
                    row.put("refNumber", rs.getString("RefNumber"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    /**
     * List datasets without segment filtering (fallback for unauthenticated users).
     * 
     * @return List of all non-deleted datasets
     */
    public java.util.List<java.util.Map<String, Object>> listDatasets() throws SQLException {
        // Get active nobject_id values to exclude (temporary cloned rows from active CRs)
        java.util.Set<Integer> excludedIds = getActiveNObjectIds();
        
        String excludeClause = "";
        if (!excludedIds.isEmpty()) {
            String idsList = excludedIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
            excludeClause = " AND d.ID NOT IN (" + idsList + ")";
        }
        
        String sql = "SELECT d.ID, d.PrimaryName, d.definition, d.RefNumber " +
                     "FROM dataset d " +
                     "WHERE d.DeletedDatetime IS NULL " + excludeClause +
                     " ORDER BY d.PrimaryName";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("name", rs.getString("PrimaryName"));
                row.put("primaryName", rs.getString("PrimaryName"));
                row.put("description", rs.getString("definition"));
                row.put("definition", rs.getString("definition"));
                row.put("refNumber", rs.getString("RefNumber"));
                results.add(row);
            }
            return results;
        }
    }
    
    /**
     * Get active nobject_id values (temporary cloned rows) for dataset facet.
     */
    private java.util.Set<Integer> getActiveNObjectIds() {
        try {
            FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
            return facetChangesDAO.getActiveNObjectIdsForFacet("dataset");
        } catch (Exception e) {
            // Log but don't fail - if we can't get excluded IDs, just return empty set
            System.err.println("[DatasetDAO] Error getting active nobject_id: " + e.getMessage());
            return new java.util.HashSet<>();
        }
    }

    /**
     * SQL condition for "Enterprise OR selected segment" datasets.
     * - Enterprise means segment 1 OR unassigned (no segment_x_resource row).
     * - If attributeSegmentId == 1, only enterprise/unassigned is allowed.
     */
    private String buildEnterpriseOrSameSegmentConstraintSql(int attributeSegmentId) {
        return "(" +
                "NOT EXISTS (" +
                "  SELECT 1 FROM segment_x_resource sxr " +
                "  JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                "  JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                "  WHERE orr.Object_ID = d.ID AND sot.Type = 'Dataset' AND sxr.Deleted_At IS NULL" +
                ") " +
                "OR EXISTS (" +
                "  SELECT 1 FROM segment_x_resource sxr " +
                "  JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID " +
                "  JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID " +
                "  WHERE orr.Object_ID = d.ID AND sot.Type = 'Dataset' AND sxr.Deleted_At IS NULL " +
                "    AND sxr.Segment_ID IN (" + (attributeSegmentId == 1 ? "?" : "?, ?") + ")" +
                ")" +
                ")";
    }

    private int bindEnterpriseOrSameSegmentConstraintParams(PreparedStatement ps, int startIndex, int attributeSegmentId) throws SQLException {
        ps.setInt(startIndex++, 1);
        if (attributeSegmentId != 1) {
            ps.setInt(startIndex++, attributeSegmentId);
        }
        return startIndex;
    }

    /**
     * Get dataset by RefNumber excluding a specific ID (for update validation)
     * @param refNumber Reference number to search for
     * @param excludeId ID to exclude from search
     * @return Dataset if found, null otherwise
     */
    public Dataset getDatasetByRefNumberExcludingId(String refNumber, int excludeId) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return null;
        }
        
        String sql = "SELECT * FROM dataset WHERE LOWER(RefNumber) = LOWER(?) AND ID != ? AND (LastUpdateDatetime IS NOT NULL OR CreateDatetime IS NOT NULL)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, refNumber.trim());
            pstmt.setInt(2, excludeId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return getDatasetById(rs.getInt("ID"));
                }
            }
        }
        return null;
    }

    /**
     * Check if RefNumber is unique for update (excluding current ID).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     * Rule: Can repeat ref if it's the same object (excludeId), but cannot repeat for different objects.
     * @param refNumber Reference number to check
     * @param excludeId ID to exclude from check
     * @return true if unique, false if duplicate exists
     */
    public boolean isRefNumberUniqueForUpdate(String refNumber, int excludeId) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUniqueForUpdate("Dataset", refNumber, excludeId);
    }
}


