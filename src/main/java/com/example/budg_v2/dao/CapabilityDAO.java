package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Capability;
import com.example.budg_v2.service.SegmentAccessService;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CapabilityDAO {
    
    private static final String SELECT_ALL = "SELECT * FROM capability WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String SELECT_BY_ID = "SELECT c.*, s.primaryname AS statusName FROM capability c " +
            "LEFT JOIN status s ON s.ID = c.Status " +
            "WHERE c.ID = ? AND c.DeletedDatetime IS NULL";
    private static final String SELECT_FOR_DROPDOWN = "SELECT ID, PrimaryName, Description FROM capability WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String SEARCH = "SELECT * FROM capability WHERE (PrimaryName LIKE ? OR Description LIKE ?) AND DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String INSERT = "INSERT INTO capability (Parent_ID, Is_Public, Classification, Status, Lifecycle, Capability_Type, RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?)";
    private static final String UPDATE = "UPDATE capability SET Parent_ID = ?, Is_Public = ?, Classification = ?, Status = ?, Lifecycle = ?, Capability_Type = ?, RefNumber = ?, PrimaryName = ?, Description = ?, LastUpdateDatetime = NOW(), LastUpdateUser_ID = ? WHERE ID = ?";
    private static final String UPDATE_WITHOUT_TIMESTAMP = "UPDATE capability SET Parent_ID = ?, Is_Public = ?, Classification = ?, Status = ?, Lifecycle = ?, Capability_Type = ?, RefNumber = ?, PrimaryName = ?, Description = ? WHERE ID = ?";
    private static final String SOFT_DELETE = "UPDATE capability SET DeletedDatetime = NOW(), LastUpdateUser_ID = ? WHERE ID = ?";
    private static final String CHECK_NAME_UNIQUE = "SELECT COUNT(*) FROM capability WHERE PrimaryName = ? AND DeletedDatetime IS NULL AND ID != ?";
    private static final String SELECT_BY_REFNUMBER_EXCLUDE_ID = "SELECT * FROM capability WHERE LOWER(RefNumber) = LOWER(?) AND ID != ? AND DeletedDatetime IS NULL";

    public List<Capability> getAllCapabilities() throws SQLException {
        return getAllCapabilitiesForGuest();
    }

    /**
     * Get all capabilities for guest users (public, Enterprise only, not deleted)
     */
    private List<Capability> getAllCapabilitiesForGuest() throws SQLException {
        String guestFilter = SegmentAccessService.buildGuestFilterClause("Capability", "c", "c.ID");
        if (guestFilter == null) {
            return getAllCapabilitiesUnfiltered();
        }
        String sql = "SELECT c.* FROM capability c WHERE " + guestFilter + " ORDER BY c.PrimaryName";
        List<Capability> capabilities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                capabilities.add(mapResultSetToCapability(rs));
            }
        }
        return capabilities;
    }

    public List<Capability> getAllCapabilitiesUnfiltered() throws SQLException {
        List<Capability> capabilities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                capabilities.add(mapResultSetToCapability(rs));
            }
        }
        return capabilities;
    }

    /**
     * Get all capabilities filtered by user's segment access
     */
    public List<Capability> getAllCapabilities(int userId) throws SQLException {
        if (userId <= 0) {
            return getAllCapabilitiesForGuest();
        }
        List<Capability> allCapabilities = getAllCapabilitiesUnfiltered();
        if (allCapabilities.isEmpty()) {
            return allCapabilities;
        }
        
        // Get accessible capability IDs for this user (filtered by selected segments)
        List<Integer> allIds = allCapabilities.stream().map(Capability::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Capability", allIds);
        
        // Filter to only accessible capabilities
        return allCapabilities.stream()
                .filter(c -> accessibleIds.contains(c.getId()))
                .collect(Collectors.toList());
    }

    public List<Capability> getAllCapabilitiesForDropdown() throws SQLException {
        return getAllCapabilitiesForDropdownForGuest();
    }

    /**
     * Get capabilities for dropdown for guest users (public, Enterprise only, not deleted)
     */
    private List<Capability> getAllCapabilitiesForDropdownForGuest() throws SQLException {
        String guestFilter = SegmentAccessService.buildGuestFilterClause("Capability", "c", "c.ID");
        if (guestFilter == null) {
            return getAllCapabilitiesForDropdownUnfiltered();
        }
        String sql = "SELECT c.ID, c.PrimaryName, c.Description FROM capability c WHERE " + guestFilter + " ORDER BY c.PrimaryName";
        List<Capability> capabilities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Capability capability = new Capability();
                capability.setId(rs.getInt("ID"));
                capability.setPrimaryName(rs.getString("PrimaryName"));
                capability.setDescription(rs.getString("Description"));
                capabilities.add(capability);
            }
        }
        return capabilities;
    }

    private List<Capability> getAllCapabilitiesForDropdownUnfiltered() throws SQLException {
        List<Capability> capabilities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_FOR_DROPDOWN);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Capability capability = new Capability();
                capability.setId(rs.getInt("ID"));
                capability.setPrimaryName(rs.getString("PrimaryName"));
                capability.setDescription(rs.getString("Description"));
                capabilities.add(capability);
            }
        }
        return capabilities;
    }

    /**
     * Get capabilities for dropdown filtered by user's segment access
     */
    public List<Capability> getAllCapabilitiesForDropdown(int userId) throws SQLException {
        if (userId <= 0) {
            return getAllCapabilitiesForDropdownForGuest();
        }
        List<Capability> allCapabilities = getAllCapabilitiesForDropdownUnfiltered();
        if (allCapabilities.isEmpty()) {
            return allCapabilities;
        }
        
        // Get accessible capability IDs for this user (filtered by selected segments)
        List<Integer> allIds = allCapabilities.stream().map(Capability::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Capability", allIds);
        
        // Filter to only accessible capabilities
        return allCapabilities.stream()
                .filter(c -> accessibleIds.contains(c.getId()))
                .collect(Collectors.toList());
    }

    public Capability getCapabilityById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {
            
            pstmt.setInt(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCapability(rs);
                }
            }
        }
        
        return null;
    }

    public List<Capability> searchCapabilities(String searchQuery) throws SQLException {
        List<Capability> capabilities = new ArrayList<>();
        String searchPattern = "%" + searchQuery + "%";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {
            
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    capabilities.add(mapResultSetToCapability(rs));
                }
            }
        }
        
        return capabilities;
    }

    public Capability createCapability(Capability capability) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setObject(1, capability.getParentId());
            pstmt.setObject(2, capability.getIsPublic());
            pstmt.setObject(3, capability.getClassification());
            pstmt.setObject(4, capability.getStatus());
            pstmt.setObject(5, capability.getLifecycle());
            pstmt.setObject(6, capability.getCapabilityType());
            pstmt.setString(7, capability.getRefNumber());
            pstmt.setString(8, capability.getPrimaryName());
            pstmt.setString(9, capability.getDescription());
            pstmt.setObject(10, capability.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating capability failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int capabilityId = generatedKeys.getInt(1);
                    capability.setId(capabilityId);
                    
                    // Create audit snapshot (always)
                    try {
                        createCapabilityAuditRecord(capabilityId);
                        //system.out.println("✅ CapabilityDAO: capability_audit snapshot created for ID: " + capabilityId);
                    } catch (Exception e) {
                        System.err.println("❌ Error creating capability_audit snapshot: " + e.getMessage());
                        e.printStackTrace();
                    }
                    
                    // Create audit records only if userId is available
                    try {
                        Integer userId = capability.getLastUpdateUserId();
                        if (userId != null) {
                            String userName = getPersonFullName(userId);
                            if (userName != null) {
                                createCapabilityAuditRecords(capabilityId, userName);
                                
                                // Create stakeholder audit records if user ID is available
                                String userFullName = getPersonFullName(userId);
                                if (userFullName != null) {
                                    // Assuming role ID 1 is the default capability owner role
                                    // You may need to adjust this based on your role structure
                                    int defaultRoleId = 1; // Adjust this as needed
                                    createStakeholderAuditRecords(capabilityId, userName, userFullName, defaultRoleId);
                                }
                                //system.out.println("✅ CapabilityDAO: Audit history records created for ID: " + capabilityId);
                            } else {
                                //system.out.println("⚠️ CapabilityDAO: User name not found for userId: " + userId);
                            }
                        } else {
                            //system.out.println("⚠️ CapabilityDAO: lastUpdateUserId is null, skipping audit history records");
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error creating audit history records: " + e.getMessage());
                        e.printStackTrace();
                        // Don't fail the insert if audit fails
                    }
                    
                    return capability;
                } else {
                    throw new SQLException("Creating capability failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateCapability(Capability capability) throws SQLException {
        // الخطوة 1: احصل على البيانات القديمة قبل التحديث
        Capability oldCapability = getCapabilityById(capability.getId());
        if (oldCapability == null) {
            throw new SQLException("Capability not found with ID: " + capability.getId());
        }
        
        // Check if there are actual changes
        boolean hasChanges = !isEqual(oldCapability.getParentId(), capability.getParentId()) ||
                            !isEqual(oldCapability.getIsPublic(), capability.getIsPublic()) ||
                            !isEqual(oldCapability.getClassification(), capability.getClassification()) ||
                            !isEqual(oldCapability.getStatus(), capability.getStatus()) ||
                            !isEqual(oldCapability.getLifecycle(), capability.getLifecycle()) ||
                            !isEqual(oldCapability.getCapabilityType(), capability.getCapabilityType()) ||
                            !isEqual(oldCapability.getRefNumber(), capability.getRefNumber()) ||
                            !isEqual(oldCapability.getPrimaryName(), capability.getPrimaryName()) ||
                            !isEqual(oldCapability.getDescription(), capability.getDescription());
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            PreparedStatement pstmt;
            
            if (hasChanges) {
                // Update with timestamp and user ID
                pstmt = conn.prepareStatement(UPDATE);
                pstmt.setObject(1, capability.getParentId());
                pstmt.setObject(2, capability.getIsPublic());
                pstmt.setObject(3, capability.getClassification());
                pstmt.setObject(4, capability.getStatus());
                pstmt.setObject(5, capability.getLifecycle());
                pstmt.setObject(6, capability.getCapabilityType());
                pstmt.setString(7, capability.getRefNumber());
                pstmt.setString(8, capability.getPrimaryName());
                pstmt.setString(9, capability.getDescription());
                pstmt.setObject(10, capability.getLastUpdateUserId());
                pstmt.setInt(11, capability.getId());
            } else {
                // No changes - don't update timestamp or user ID
                pstmt = conn.prepareStatement(UPDATE_WITHOUT_TIMESTAMP);
                pstmt.setObject(1, capability.getParentId());
                pstmt.setObject(2, capability.getIsPublic());
                pstmt.setObject(3, capability.getClassification());
                pstmt.setObject(4, capability.getStatus());
                pstmt.setObject(5, capability.getLifecycle());
                pstmt.setObject(6, capability.getCapabilityType());
                pstmt.setString(7, capability.getRefNumber());
                pstmt.setString(8, capability.getPrimaryName());
                pstmt.setString(9, capability.getDescription());
                pstmt.setInt(10, capability.getId());
            }

            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0 && hasChanges) {
                // الخطوة 2: إنشاء audit records للتحديثات (only if there were changes)
                try {
                    Integer userId = capability.getLastUpdateUserId();
                    if (userId != null) {
                        String userName = getPersonFullName(userId);
                        if (userName != null) {
                            createCapabilityUpdateAuditRecords(capability.getId(), oldCapability, capability, userName);
                            //system.out.println("✅ Capability update audit records created for ID: " + capability.getId());
                        } else {
                            //system.out.println("⚠️ User name not found for userId: " + userId);
                        }
                    } else {
                        //system.out.println("⚠️ lastUpdateUserId is null, skipping audit records");
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating capability update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // الخطوة 3: إنشاء snapshot جديد في capability_audit (only if there were changes)
                try {
                    createCapabilityUpdateAuditSnapshot(capability.getId());
                    //system.out.println("✅ CapabilityDAO: capability_audit update snapshot created for ID: " + capability.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating capability_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            pstmt.close();
            return affectedRows > 0;
        }
    }
    
    /**
     * Helper method للمقارنة بين القيم (يتعامل مع null)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        if (obj1 instanceof String && obj2 instanceof String) {
            String str1 = ((String) obj1).trim();
            String str2 = ((String) obj2).trim();
            return str1.equals(str2);
        }
        return obj1.equals(obj2);
    }

    public boolean deleteCapability(int id, Integer lastUpdateUserId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setObject(1, lastUpdateUserId);
            pstmt.setInt(2, id);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public boolean isNameUnique(String name, Integer excludeId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(CHECK_NAME_UNIQUE)) {

            pstmt.setString(1, name);
            pstmt.setObject(2, excludeId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0;
                }
            }
        }
        
        return false;
    }

    /**
     * Get capability hierarchy including ancestors, current capability, siblings, and descendants.
     * Similar to ProcessDAO.getProcessHierarchy() and PolicyDAO.getPolicyHierarchy().
     * 
     * @param capabilityId The capability ID to get hierarchy for
     * @return Map containing current, ancestors, siblings, descendants lists
     * @throws SQLException if database error occurs
     */
    public java.util.Map<String, Object> getCapabilityHierarchy(int capabilityId) throws SQLException {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        String hierarchyQuery = 
            "WITH RECURSIVE ancestors AS (\n" +
            "  SELECT c.ID, c.Parent_ID\n" +
            "  FROM capability c\n" +
            "  WHERE c.ID = ? AND c.DeletedDatetime IS NULL\n" +
            "  UNION ALL\n" +
            "  SELECT parent.ID, parent.Parent_ID\n" +
            "  FROM capability parent\n" +
            "  JOIN ancestors a ON a.Parent_ID = parent.ID\n" +
            "  WHERE parent.DeletedDatetime IS NULL\n" +
            "), root AS (\n" +
            "  SELECT a.ID\n" +
            "  FROM ancestors a\n" +
            "  WHERE a.Parent_ID IS NULL\n" +
            "  LIMIT 1\n" +
            "), ch AS (\n" +
            "  SELECT c.ID, c.Parent_ID, 0 AS level, CAST(c.ID AS CHAR(1000)) AS path\n" +
            "  FROM capability c\n" +
            "  JOIN root r ON r.ID = c.ID\n" +
            "  WHERE c.DeletedDatetime IS NULL\n" +
            "  UNION ALL\n" +
            "  SELECT child.ID, child.Parent_ID, ch.level + 1, CONCAT(ch.path, ',', child.ID)\n" +
            "  FROM capability child\n" +
            "  JOIN ch ON child.Parent_ID = ch.ID\n" +
            "  WHERE child.DeletedDatetime IS NULL\n" +
            ")\n" +
            ", sel AS (SELECT ? AS selected_id)\n" +
            ", sel_path AS (\n" +
            "    SELECT ch.path AS path\n" +
            "    FROM ch JOIN sel ON ch.ID = sel.selected_id\n" +
            "    LIMIT 1\n" +
            ")\n" +
            "SELECT c.ID, c.Parent_ID, c.Is_Public, c.Classification, c.Status, c.Lifecycle,\n" +
            "       c.Capability_Type, c.RefNumber, c.PrimaryName, c.Description,\n" +
            "       c.CreateDatetime, c.LastUpdateDatetime, c.DeletedDatetime, c.LastUpdateUser_ID,\n" +
            "       parent.PrimaryName AS parentName,\n" +
            "       ch.level AS level,\n" +
            "       ch.path AS path\n" +
            "FROM ch\n" +
            "JOIN capability c ON c.ID = ch.ID\n" +
            "LEFT JOIN capability parent ON parent.ID = c.Parent_ID AND parent.DeletedDatetime IS NULL\n" +
            "CROSS JOIN sel\n" +
            "LEFT JOIN sel_path sp ON 1=1\n" +
            "WHERE c.DeletedDatetime IS NULL\n" +
            "  AND ( (sp.path IS NOT NULL AND sp.path LIKE CONCAT(ch.path, '%'))\n" +
            "        OR ch.path = CAST(sel.selected_id AS CHAR(1000))\n" +
            "        OR ch.path LIKE CONCAT('%,', sel.selected_id, ',%')\n" +
            "        OR ch.path LIKE CONCAT(sel.selected_id, ',%')\n" +
            "      )\n" +
            "ORDER BY ch.level, c.PrimaryName";
        
        java.util.Map<String, Object> current = null;
        java.util.List<java.util.Map<String, Object>> ancestors = new java.util.ArrayList<>();
        java.util.List<java.util.Map<String, Object>> siblings = new java.util.ArrayList<>();
        java.util.List<java.util.Map<String, Object>> descendants = new java.util.ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(hierarchyQuery)) {
            
            pstmt.setInt(1, capabilityId);
            pstmt.setInt(2, capabilityId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                Integer currentParentId = null;
                int currentLevel = -1;
                
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    int id = rs.getInt("ID");
                    Integer parentId = rs.getObject("Parent_ID", Integer.class);
                    int level = rs.getInt("level");
                    
                    row.put("id", id);
                    row.put("parentId", parentId);
                    row.put("isPublic", rs.getObject("Is_Public", Integer.class));
                    row.put("classification", rs.getObject("Classification", Integer.class));
                    row.put("status", rs.getObject("Status", Integer.class));
                    row.put("lifecycle", rs.getObject("Lifecycle", Integer.class));
                    row.put("capabilityType", rs.getObject("Capability_Type", Integer.class));
                    row.put("refNumber", rs.getString("RefNumber"));
                    row.put("primaryName", rs.getString("PrimaryName"));
                    row.put("description", rs.getString("Description"));
                    row.put("parentName", rs.getString("parentName"));
                    row.put("level", level);
                    row.put("path", rs.getString("path"));
                    
                    if (id == capabilityId) {
                        current = row;
                        currentParentId = parentId;
                        currentLevel = level;
                    } else if (currentLevel >= 0) {
                        if (level < currentLevel) {
                            // Ancestor
                            ancestors.add(row);
                        } else if (level == currentLevel && parentId != null && parentId.equals(currentParentId)) {
                            // Sibling (same parent)
                            siblings.add(row);
                        } else if (level > currentLevel) {
                            // Descendant
                            descendants.add(row);
                        }
                    }
                }
            }
        }
        
        result.put("current", current);
        result.put("ancestors", ancestors);
        result.put("siblings", siblings);
        result.put("descendants", descendants);
        
        return result;
    }

    private Capability mapResultSetToCapability(ResultSet rs) throws SQLException {
        Capability capability = new Capability();
        
        capability.setId(rs.getInt("ID"));
        capability.setParentId(rs.getObject("Parent_ID", Integer.class));
        capability.setIsPublic(rs.getObject("Is_Public", Integer.class));
        capability.setClassification(rs.getObject("Classification", Integer.class));
        capability.setStatus(rs.getObject("Status", Integer.class));
        try {
            capability.setStatusName(rs.getString("statusName"));
        } catch (SQLException e) {
            capability.setStatusName(null);
        }
        capability.setLifecycle(rs.getObject("Lifecycle", Integer.class));
        capability.setCapabilityType(rs.getObject("Capability_Type", Integer.class));
        capability.setRefNumber(rs.getString("RefNumber"));
        capability.setPrimaryName(rs.getString("PrimaryName"));
        capability.setDescription(rs.getString("Description"));
        capability.setCreateDatetime(rs.getTimestamp("CreateDatetime") != null ? 
            rs.getTimestamp("CreateDatetime").toLocalDateTime() : null);
        capability.setLastUpdateDatetime(rs.getTimestamp("LastUpdateDatetime") != null ? 
            rs.getTimestamp("LastUpdateDatetime").toLocalDateTime() : null);
        capability.setDeletedDatetime(rs.getTimestamp("DeletedDatetime") != null ? 
            rs.getTimestamp("DeletedDatetime").toLocalDateTime() : null);
        capability.setLastUpdateUserId(rs.getObject("LastUpdateUser_ID", Integer.class));
        
        return capability;
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int capabilityId, String updateType, String field, String value, String userName) throws SQLException {
        //system.out.println("CapabilityDAO: createNewAuditRecord called - capabilityId: " + capabilityId + ", field: " + field + ", value: " + value);
        
        auditStmt.setInt(1, capabilityId);        // id
        auditStmt.setString(2, updateType);    // updateType
        auditStmt.setString(3, field);          // field
        auditStmt.setString(4, value);         // to
        auditStmt.setString(5, userName);       // author
        
        //system.out.println("CapabilityDAO: Executing audit insert for field: " + field);
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                int auditId = generatedKeys.getInt(1);
                //system.out.println("CapabilityDAO: Audit record created with ID: " + auditId);
                return auditId;
            }
        }
        //system.out.println("CapabilityDAO: No generated key returned");
        return -1;
    }

    /**
     * إنشاء audit records للـ capability الجديد
     * يتم استدعاء هذا method بعد إنشاء الـ capability بنجاح
     */
    public void createCapabilityAuditRecords(int capabilityId, String userName) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            createCapabilityAuditRecords(conn, capabilityId, userName);
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
     * إنشاء audit records للـ capability الجديد باستخدام connection موجود
     * يتم استدعاء هذا method داخل transaction موجودة
     */
    public void createCapabilityAuditRecords(Connection conn, int capabilityId, String userName) throws SQLException {
        //system.out.println("CapabilityDAO: createCapabilityAuditRecords called with capabilityId: " + capabilityId + ", userName: " + userName);
        PreparedStatement auditStmt = null;
        
        try {
            // 1. الحصول على بيانات الـ capability
            String capabilityDataSql = "SELECT * FROM capability WHERE ID = ?";
            //system.out.println("CapabilityDAO: Executing SQL: " + capabilityDataSql);
            try (PreparedStatement capabilityStmt = conn.prepareStatement(capabilityDataSql)) {
                capabilityStmt.setInt(1, capabilityId);
                try (ResultSet capabilityRs = capabilityStmt.executeQuery()) {
                    if (!capabilityRs.next()) {
                        //system.out.println("CapabilityDAO: Capability not found with ID: " + capabilityId);
                        throw new SQLException("Capability not found with ID: " + capabilityId);
                    }
                    //system.out.println("CapabilityDAO: Capability found, starting audit record creation");

                    // 2. إعداد audit statement
                    String auditSql = """
                        INSERT INTO capability_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                        VALUES (?, 'Capability', 'Details', ?, ?, NULL, ?, ?)
                    """;
                    auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
                    
                    // Primary Name
                    String primaryName = capabilityRs.getString("PrimaryName");
                    if (primaryName != null && !primaryName.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Primary Name", primaryName, userName);
                    }
                    
                    // Description
                    String description = capabilityRs.getString("Description");
                    if (description != null && !description.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Description", description, userName);
                    }
                    
                    // Reference Number
                    String refNumber = capabilityRs.getString("RefNumber");
                    if (refNumber != null && !refNumber.trim().isEmpty()) {
                        createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Reference Number", refNumber, userName);
                    }
                    
                    // Parent Capability
                    Integer parentId = capabilityRs.getObject("Parent_ID", Integer.class);
                    if (parentId != null) {
                        String parentName = getCapabilityNameWithConn(conn, parentId);
                        if (parentName != null) {
                            createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Parent Capability", parentName, userName);
                        }
                    }
                    
                    // Status
                    Integer statusId = capabilityRs.getObject("Status", Integer.class);
                    if (statusId != null) {
                        String statusName = getStatusPrimaryNameWithConn(conn, statusId);
                        if (statusName != null) {
                            createNewAuditRecord(conn, auditStmt, capabilityId, "Status Change", "Status", statusName, userName);
                        }
                    }
                    
                    // Lifecycle
                    Integer lifecycleId = capabilityRs.getObject("Lifecycle", Integer.class);
                    if (lifecycleId != null) {
                        String lifecycleName = getCapabilityLifecycleNameWithConn(conn, lifecycleId);
                        if (lifecycleName != null) {
                            createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Lifecycle", lifecycleName, userName);
                        }
                    }
                    
                    // Capability Type
                    Integer capabilityTypeId = capabilityRs.getObject("Capability_Type", Integer.class);
                    if (capabilityTypeId != null) {
                        String capabilityTypeName = getCapabilityTypeNameWithConn(conn, capabilityTypeId);
                        if (capabilityTypeName != null) {
                            createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Capability Type", capabilityTypeName, userName);
                        }
                    }
                    
                    // Classification
                    Integer classificationId = capabilityRs.getObject("Classification", Integer.class);
                    if (classificationId != null) {
                        String classificationName = getCapabilityClassificationNameWithConn(conn, classificationId);
                        if (classificationName != null) {
                            createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Classification", classificationName, userName);
                        }
                    }
                    
                    // Is Public
                    Integer isPublicId = capabilityRs.getObject("Is_Public", Integer.class);
                    if (isPublicId != null) {
                        String isPublicName = getViewingNameWithConn(conn, isPublicId);
                        if (isPublicName != null) {
                            createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Is Public", isPublicName, userName);
                        }
                    }
                    
                    // Created By
                    Integer createdById = capabilityRs.getObject("LastUpdateUser_ID", Integer.class);
                    if (createdById != null) {
                        String createdByName = getPersonFullNameWithConn(conn, createdById);
                        if (createdByName != null) {
                            createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Created By", createdByName, userName);
                        }
                    }
                }
            }
        } finally {
            // تنظيف الموارد
            if (auditStmt != null) auditStmt.close();
        }
    }

    /**
     * إنشاء سجل في جدول capability_audit بعد إنشاء الـ capability
     * يتم استدعاء هذا method بعد إنشاء الـ capability بنجاح
     */
    public void createCapabilityAuditRecord(int capabilityId) throws SQLException {
        String sql = """
            INSERT INTO capability_audit (
                ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Capability_Type, RefNumber, 
                PrimaryName, Description, CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdateUser_ID, RevType
            )
            SELECT 
                ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Capability_Type, RefNumber, 
                PrimaryName, Description, CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdateUser_ID, 'Added'
            FROM capability 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            ps.executeUpdate();
        }
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إنشاء الـ capability
     * يتم استدعاء هذا method بعد إنشاء الـ capability بنجاح
     */
    public void createStakeholderAuditRecords(int capabilityId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            createStakeholderAuditRecords(conn, capabilityId, userName, userFullName, roleId);
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
     * إنشاء audit records للـ stakeholder بعد إنشاء الـ capability باستخدام connection موجود
     * يتم استدعاء هذا method داخل transaction موجودة
     */
    public void createStakeholderAuditRecords(Connection conn, int capabilityId, String userName, String userFullName, int roleId) throws SQLException {
        PreparedStatement auditStmt = null;
        
        try {
            // 1. إعداد audit statement للـ stakeholder
            String auditSql = """
                INSERT INTO capability_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Stakeholder', 'link', ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // 2. إدراج 3 سجلات للـ stakeholder
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleIdWithConn(conn, capabilityId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }
            
            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getRoleNameWithConn(conn, actualRoleId);
            if (roleName == null) roleName = "Capability Owner"; // fallback
            
            // Role
            createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Role", roleName, userName);
            
            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusIdWithConn(conn, capabilityId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameByIdWithConn(conn, statusId);
                if (fetchedStatusName != null) statusName = fetchedStatusName;
            }
            createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Role Status", statusName, userName);
            
            // Name
            createNewAuditRecord(conn, auditStmt, capabilityId, "Added", "Name", userFullName, userName);
            
        } finally {
            // تنظيف الموارد
            if (auditStmt != null) auditStmt.close();
        }
    }

    // Helper methods للحصول على الأسماء
    private String getCapabilityName(int capabilityId) throws SQLException {
        String sql = "SELECT PrimaryName FROM capability WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getStatusPrimaryName(int statusId) throws SQLException {
        //system.out.println("CapabilityDAO: getStatusPrimaryName called with statusId: " + statusId);
        
        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT PrimaryName FROM status WHERE ID = ?";
            //system.out.println("CapabilityDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("PrimaryName");
                        //system.out.println("CapabilityDAO: Found PrimaryName: " + result);
                        return result;
                    } else {
                        //system.out.println("CapabilityDAO: No record found for statusId: " + statusId);
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("CapabilityDAO: Error with PrimaryName - " + e.getMessage());
        }
        
        // Try primaryname as fallback
        try {
            String sql = "SELECT primaryname FROM status WHERE ID = ?";
            //system.out.println("CapabilityDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("primaryname");
                        //system.out.println("CapabilityDAO: Found primaryname: " + result);
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("CapabilityDAO: Error with primaryname - " + e.getMessage());
        }
        
        //system.out.println("CapabilityDAO: Using fallback for statusId: " + statusId);
        return "Status " + statusId; // Fallback
    }

    private String getCapabilityLifecycleName(int lifecycleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM capability_lifecyle WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getCapabilityTypeName(int capabilityTypeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM capability_type WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getCapabilityClassificationName(int classificationId) throws SQLException {
        String sql = "SELECT PrimaryName FROM capability_classification WHERE ID = ?";
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

    private String getViewingName(int viewingId) throws SQLException {
        //system.out.println("CapabilityDAO: getViewingName called with viewingId: " + viewingId);
        
        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT Name FROM viewing WHERE id = ?";
            //system.out.println("CapabilityDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, viewingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("PrimaryName");
                        //system.out.println("CapabilityDAO: Found PrimaryName: " + result);
                        return result;
                    } else {
                        //system.out.println("CapabilityDAO: No record found for viewingId: " + viewingId);
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("CapabilityDAO: Error with PrimaryName - " + e.getMessage());
        }
        
        // Try Name as fallback
        try {
            String sql = "SELECT Name FROM viewing WHERE id = ?";
            //system.out.println("CapabilityDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, viewingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("Name");
                        //system.out.println("CapabilityDAO: Found Name: " + result);
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("CapabilityDAO: Error with Name - " + e.getMessage());
        }
        
        //system.out.println("CapabilityDAO: Using fallback for viewingId: " + viewingId);
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
    private Integer getStakeholderRoleId(int capabilityId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN capability_x_objectxpeople cxo ON cxo.Object_x_ipid = oxp.ID " +
                    "WHERE cxo.CapabilityID = ? " +
                    "ORDER BY cxo.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }
    
    // Get statusID from object_x_people for the stakeholder
    private Integer getStakeholderStatusId(int capabilityId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN capability_x_objectxpeople cxo ON cxo.Object_x_ipid = oxp.ID " +
                    "WHERE cxo.CapabilityID = ? " +
                    "ORDER BY cxo.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
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
    
    // Connection-aware helper methods for stakeholder audit records
    private String getRoleNameWithConn(Connection conn, int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }
    
    private Integer getStakeholderRoleIdWithConn(Connection conn, int capabilityId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN capability_x_objectxpeople cxo ON cxo.Object_x_ipid = oxp.ID " +
                    "WHERE cxo.CapabilityID = ? " +
                    "ORDER BY cxo.ID DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }
    
    private Integer getStakeholderStatusIdWithConn(Connection conn, int capabilityId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN capability_x_objectxpeople cxo ON cxo.Object_x_ipid = oxp.ID " +
                    "WHERE cxo.CapabilityID = ? " +
                    "ORDER BY cxo.ID DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("statusID");
            }
        }
        return null;
    }
    
    private String getStatusNameByIdWithConn(Connection conn, int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM object_x_ip_status WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * إنشاء audit records عند تحديث الـ capability
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    public void createCapabilityUpdateAuditRecords(int capabilityId, Capability oldCapability, Capability newCapability, String userName) throws SQLException {
        //system.out.println("🔍 CapabilityDAO.createCapabilityUpdateAuditRecords - START for ID: " + capabilityId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO capability_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldCapability.getPrimaryName(), newCapability.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, capabilityId, "Capability", "Details", 
                    "Updated", "Primary Name", oldCapability.getPrimaryName(), newCapability.getPrimaryName(), userName);
            }
            
            // Description
            if (!isEqual(oldCapability.getDescription(), newCapability.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, capabilityId, "Capability", "Details", 
                    "Updated", "Description", oldCapability.getDescription(), newCapability.getDescription(), userName);
            }
            
            // Reference Number
            if (!isEqual(oldCapability.getRefNumber(), newCapability.getRefNumber())) {
                createUpdateAuditRecord(conn, auditStmt, capabilityId, "Capability", "Details", 
                    "Updated", "Reference Number", oldCapability.getRefNumber(), newCapability.getRefNumber(), userName);
            }
            
            // Parent Capability
            if (!isEqual(oldCapability.getParentId(), newCapability.getParentId())) {
                String oldParentName = oldCapability.getParentId() != null ? getCapabilityNameWithConn(conn, oldCapability.getParentId()) : null;
                String newParentName = newCapability.getParentId() != null ? getCapabilityNameWithConn(conn, newCapability.getParentId()) : null;
                createUpdateAuditRecord(conn, auditStmt, capabilityId, "Capability", "Details", 
                    "Updated", "Parent Capability", oldParentName, newParentName, userName);
            }
            
            // Status (يستخدم "Status Change" كـ updateType)
            if (!isEqual(oldCapability.getStatus(), newCapability.getStatus())) {
                String oldStatusName = oldCapability.getStatus() != null ? getStatusPrimaryNameWithConn(conn, oldCapability.getStatus()) : null;
                String newStatusName = newCapability.getStatus() != null ? getStatusPrimaryNameWithConn(conn, newCapability.getStatus()) : null;
                createUpdateAuditRecord(conn, auditStmt, capabilityId, "Capability", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
            // Lifecycle
            if (!isEqual(oldCapability.getLifecycle(), newCapability.getLifecycle())) {
                String oldLifecycleName = oldCapability.getLifecycle() != null ? getCapabilityLifecycleNameWithConn(conn, oldCapability.getLifecycle()) : null;
                String newLifecycleName = newCapability.getLifecycle() != null ? getCapabilityLifecycleNameWithConn(conn, newCapability.getLifecycle()) : null;
                createUpdateAuditRecord(conn, auditStmt, capabilityId, "Capability", "Details", 
                    "Updated", "Lifecycle", oldLifecycleName, newLifecycleName, userName);
            }
            
            // Capability Type
            if (!isEqual(oldCapability.getCapabilityType(), newCapability.getCapabilityType())) {
                String oldTypeName = oldCapability.getCapabilityType() != null ? getCapabilityTypeNameWithConn(conn, oldCapability.getCapabilityType()) : null;
                String newTypeName = newCapability.getCapabilityType() != null ? getCapabilityTypeNameWithConn(conn, newCapability.getCapabilityType()) : null;
                createUpdateAuditRecord(conn, auditStmt, capabilityId, "Capability", "Details", 
                    "Updated", "Capability Type", oldTypeName, newTypeName, userName);
            }
            
            // Classification
            if (!isEqual(oldCapability.getClassification(), newCapability.getClassification())) {
                String oldClassificationName = oldCapability.getClassification() != null ? getCapabilityClassificationNameWithConn(conn, oldCapability.getClassification()) : null;
                String newClassificationName = newCapability.getClassification() != null ? getCapabilityClassificationNameWithConn(conn, newCapability.getClassification()) : null;
                createUpdateAuditRecord(conn, auditStmt, capabilityId, "Capability", "Details", 
                    "Updated", "Classification", oldClassificationName, newClassificationName, userName);
            }
            
            // Is Public
            if (!isEqual(oldCapability.getIsPublic(), newCapability.getIsPublic())) {
                String oldIsPublicName = oldCapability.getIsPublic() != null ? getViewingNameWithConn(conn, oldCapability.getIsPublic()) : null;
                String newIsPublicName = newCapability.getIsPublic() != null ? getViewingNameWithConn(conn, newCapability.getIsPublic()) : null;
                createUpdateAuditRecord(conn, auditStmt, capabilityId, "Capability", "Details", 
                    "Updated", "Is Public", oldIsPublicName, newIsPublicName, userName);
            }
            
            conn.commit();
            //system.out.println("✅ CapabilityDAO.createCapabilityUpdateAuditRecords - COMMITTED successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ CapabilityDAO.createCapabilityUpdateAuditRecords - ERROR: " + e.getMessage());
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
            int capabilityId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, capabilityId);
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
     * Method لإنشاء snapshot جديد في capability_audit عند الـ update
     */
    public void createCapabilityUpdateAuditSnapshot(int capabilityId) throws SQLException {
        //system.out.println("🔍 CapabilityDAO.createCapabilityUpdateAuditSnapshot - Creating update snapshot for ID: " + capabilityId);
        String sql = """
            INSERT INTO capability_audit (
                ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Capability_Type, RefNumber, 
                PrimaryName, Description, CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdateUser_ID, RevType
            )
            SELECT 
                ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Capability_Type, RefNumber, 
                PrimaryName, Description, CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdateUser_ID, 'Updated'
            FROM capability 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            ps.executeUpdate();
            //system.out.println("✅ Update snapshot created, rows affected: " + rows);
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }


    // Helper methods that use existing connection to avoid connection leaks
    private String getCapabilityNameWithConn(Connection conn, int capabilityId) throws SQLException {
        String sql = "SELECT PrimaryName FROM capability WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getStatusPrimaryNameWithConn(Connection conn, int statusId) throws SQLException {
        String sql = "SELECT PrimaryName FROM status WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getCapabilityLifecycleNameWithConn(Connection conn, int lifecycleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM capability_lifecyle WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getCapabilityTypeNameWithConn(Connection conn, int capabilityTypeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM capability_type WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getCapabilityClassificationNameWithConn(Connection conn, int classificationId) throws SQLException {
        String sql = "SELECT PrimaryName FROM capability_classification WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classificationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getViewingNameWithConn(Connection conn, int viewingId) throws SQLException {
        String sql = "SELECT Name FROM viewing WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, viewingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }
    
    private String getPersonFullNameWithConn(Connection conn, int personId) throws SQLException {
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
     * حذف القدرة مع تسجيل audit records
     */
    public boolean deleteCapabilityWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE capability SET DeletedDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO capability_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Capability");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Capability");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في capability_audit
                String snapshotSql = """
                    INSERT INTO capability_audit (
                        ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Capability_Type, RefNumber, 
                        PrimaryName, Description, CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdateUser_ID, RevType
                    )
                    SELECT 
                        ID, Parent_ID, Is_Public, Classification, Status, Lifecycle, Capability_Type, RefNumber, 
                        PrimaryName, Description, CreateDatetime, LastUpdateDatetime, DeletedDatetime, LastUpdateUser_ID, 'Deleted'
                    FROM capability 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Capability deleted with audit for ID: " + id);
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
     * Link stakeholder to capability via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToCapability(Connection conn, int capabilityId, int objectXPeopleId, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO capability_x_objectxpeople (CapabilityID, Object_x_ipid, Last_UpdateUser_ID, CreateDatetime)
            VALUES (?, ?, ?, NOW())
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, currentUserId);
            try {
                ps.executeUpdate();
                //system.out.println("✅ Successfully linked stakeholder to capability: CapabilityID=" + capabilityId + ", Object_x_ipid=" + objectXPeopleId + ", rows affected=" + rowsAffected);
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: CapabilityID=" + capabilityId + ", Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }

    /**
     * Get capability by RefNumber excluding a specific ID (for update validation)
     * @param refNumber Reference number to search for
     * @param excludeId ID to exclude from search
     * @return Capability if found, null otherwise
     */
    public Capability getCapabilityByRefNumberExcludingId(String refNumber, int excludeId) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return null;
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER_EXCLUDE_ID)) {

            pstmt.setString(1, refNumber.trim());
            pstmt.setInt(2, excludeId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCapability(rs);
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
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUniqueForUpdate("Capability", refNumber, excludeId);
    }

    /**
     * Get capability by RefNumber
     * @param refNumber Reference number to search for
     * @return Capability if found, null otherwise
     */
    public Capability getCapabilityByRefNumber(String refNumber) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return null;
        }
        
        String sql = "SELECT * FROM capability WHERE LOWER(RefNumber) = LOWER(?) AND DeletedDatetime IS NULL LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, refNumber.trim());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCapability(rs);
                }
            }
        }
        return null;
    }

    /**
     * Get capability by PrimaryName
     * @param primaryName Primary name to search for
     * @return Capability if found, null otherwise
     */
    public Capability getCapabilityByPrimaryName(String primaryName) throws SQLException {
        if (primaryName == null || primaryName.trim().isEmpty()) {
            return null;
        }
        
        String sql = "SELECT * FROM capability WHERE PrimaryName = ? AND DeletedDatetime IS NULL LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, primaryName.trim());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCapability(rs);
                }
            }
        }
        return null;
    }

    /**
     * Get capability hierarchy similar to glossary hierarchy
     * Returns a flat list with ancestors, current, siblings, descendants, and siblings' children
     * Each item has: id, parentId, name, description, level, relation (ancestor/current/sibling/descendant/sibling_child)
     */
    public List<

            Map<String, Object>> getCapabilityHierarchyFlat(int capabilityId) throws SQLException {
        System.out.println("[CapabilityDAO] getCapabilityHierarchyFlat called for capabilityId: " + capabilityId);
        
        // Get complete hierarchy: parents (ancestors) + current + siblings + children (descendants) + siblings' children
        // First, verify the capability exists and get its Parent_ID
        Integer currentParentId = null;
        boolean capabilityExists = false;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT Parent_ID FROM capability WHERE ID = ? AND DeletedDatetime IS NULL")) {
            ps.setInt(1, capabilityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    capabilityExists = true;
                    currentParentId = rs.getObject("Parent_ID", Integer.class);
                    System.out.println("[CapabilityDAO] Capability exists, Parent_ID: " + currentParentId);
                } else {
                    System.err.println("[CapabilityDAO] Capability with ID " + capabilityId + " not found or deleted");
                    return new ArrayList<>();
                }
            }
        }
        
        if (!capabilityExists) {
            return new ArrayList<>();
        }
        
        String sql = "WITH RECURSIVE " +
                // Get current capability's parent ID
                "current_parent AS (" +
                "    SELECT Parent_ID FROM capability WHERE ID = ? AND DeletedDatetime IS NULL " +
                "), " +
                // Get all ancestors (parents up the hierarchy)
                "ancestors AS (" +
                "    SELECT c.ID, c.Parent_ID, c.PrimaryName as name, c.Description, ct.PrimaryName as typeName, -1 as level, 'ancestor' as relation, " +
                "           c.LastUpdateDatetime " +
                "    FROM capability c " +
                "    CROSS JOIN current_parent cp " +
                "    LEFT JOIN capability_type ct ON ct.ID = c.Capability_Type " +
                "    WHERE c.ID = cp.Parent_ID AND c.ID IS NOT NULL AND c.DeletedDatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT c.ID, c.Parent_ID, c.PrimaryName as name, c.Description, ct.PrimaryName as typeName, a.level - 1, 'ancestor', " +
                "           c.LastUpdateDatetime " +
                "    FROM capability c " +
                "    INNER JOIN ancestors a ON c.ID = a.Parent_ID " +
                "    LEFT JOIN capability_type ct ON ct.ID = c.Capability_Type " +
                "    WHERE a.level > -10 AND c.DeletedDatetime IS NULL " + // Prevent infinite recursion
                "), " +
                // Get siblings (other capabilities with the same Parent_ID as current)
                "siblings AS (" +
                "    SELECT c.ID, c.Parent_ID, c.PrimaryName as name, c.Description, ct.PrimaryName as typeName, 0 as level, 'sibling' as relation, " +
                "           c.LastUpdateDatetime " +
                "    FROM capability c " +
                "    CROSS JOIN current_parent cp " +
                "    LEFT JOIN capability_type ct ON ct.ID = c.Capability_Type " +
                "    WHERE cp.Parent_ID IS NOT NULL AND c.Parent_ID = cp.Parent_ID " +
                "    AND c.ID != ? AND c.DeletedDatetime IS NULL " +
                "), " +
                // Get all descendants of current capability (children down the hierarchy)
                "descendants AS (" +
                "    SELECT c.ID, c.Parent_ID, c.PrimaryName as name, c.Description, ct.PrimaryName as typeName, 1 as level, 'descendant' as relation, " +
                "           c.LastUpdateDatetime " +
                "    FROM capability c " +
                "    LEFT JOIN capability_type ct ON ct.ID = c.Capability_Type " +
                "    WHERE c.Parent_ID = ? AND c.DeletedDatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT c.ID, c.Parent_ID, c.PrimaryName as name, c.Description, ct.PrimaryName as typeName, d.level + 1, 'descendant', " +
                "           c.LastUpdateDatetime " +
                "    FROM capability c " +
                "    INNER JOIN descendants d ON c.Parent_ID = d.ID " +
                "    LEFT JOIN capability_type ct ON ct.ID = c.Capability_Type " +
                "    WHERE d.level < 10 AND c.DeletedDatetime IS NULL " + // Prevent infinite recursion
                "), " +
                // Get children of siblings (siblings' descendants)
                "sibling_children AS (" +
                "    SELECT c.ID, c.Parent_ID, c.PrimaryName as name, c.Description, ct.PrimaryName as typeName, 1 as level, 'sibling_child' as relation, " +
                "           c.LastUpdateDatetime " +
                "    FROM capability c " +
                "    LEFT JOIN capability_type ct ON ct.ID = c.Capability_Type " +
                "    WHERE c.Parent_ID IN (SELECT ID FROM siblings) AND c.DeletedDatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT c.ID, c.Parent_ID, c.PrimaryName as name, c.Description, ct.PrimaryName as typeName, sc.level + 1, 'sibling_child', " +
                "           c.LastUpdateDatetime " +
                "    FROM capability c " +
                "    INNER JOIN sibling_children sc ON c.Parent_ID = sc.ID " +
                "    LEFT JOIN capability_type ct ON ct.ID = c.Capability_Type " +
                "    WHERE sc.level < 10 AND c.DeletedDatetime IS NULL " + // Prevent infinite recursion
                ") " +
                // Combine: ancestors + current + siblings + descendants + siblings' children
                "SELECT combined.ID, combined.Parent_ID, combined.name, combined.Description, combined.typeName, combined.level, combined.relation, " +
                "       combined.LastUpdateDatetime " +
                "FROM (" +
                "    SELECT c.ID, c.Parent_ID, c.PrimaryName as name, c.Description, ct.PrimaryName as typeName, 0 as level, 'current' as relation, " +
                "           c.LastUpdateDatetime " +
                "    FROM capability c " +
                "    LEFT JOIN capability_type ct ON ct.ID = c.Capability_Type " +
                "    WHERE c.ID = ? AND c.DeletedDatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT a.ID, a.Parent_ID, a.name, a.Description, a.typeName, a.level, a.relation, a.LastUpdateDatetime FROM ancestors a " +
                "    UNION ALL " +
                "    SELECT s.ID, s.Parent_ID, s.name, s.Description, s.typeName, s.level, s.relation, s.LastUpdateDatetime FROM siblings s " +
                "    UNION ALL " +
                "    SELECT d.ID, d.Parent_ID, d.name, d.Description, d.typeName, d.level, d.relation, d.LastUpdateDatetime FROM descendants d " +
                "    UNION ALL " +
                "    SELECT sc.ID, sc.Parent_ID, sc.name, sc.Description, sc.typeName, sc.level, sc.relation, sc.LastUpdateDatetime FROM sibling_children sc " +
                ") combined " +
                "ORDER BY level, relation, name";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capabilityId);  // For current_parent CTE
            ps.setInt(2, capabilityId);  // For siblings query - exclude current
            ps.setInt(3, capabilityId);  // For descendants query - get children
            ps.setInt(4, capabilityId);  // For current capability query
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("parentId", rs.getObject("Parent_ID"));
                    row.put("name", rs.getString("name"));
                    row.put("description", rs.getString("Description"));
                    row.put("typeName", rs.getString("typeName"));
                    row.put("level", rs.getInt("level"));
                    row.put("relation", rs.getString("relation"));
                    row.put("lastUpdated", rs.getTimestamp("LastUpdateDatetime"));
                    results.add(row);
                }
                // If no results, at least return the current capability
                if (results.isEmpty()) {
                    System.out.println("[CapabilityDAO] Main query returned no results, using fallback for capabilityId: " + capabilityId);
                    // Fallback: get just the current capability
                    String fallbackSql = "SELECT c.ID, c.Parent_ID, c.PrimaryName as name, c.Description, ct.PrimaryName as typeName, 0 as level, 'current' as relation, " +
                            "c.LastUpdateDatetime " +
                            "FROM capability c LEFT JOIN capability_type ct ON ct.ID = c.Capability_Type WHERE c.ID = ? AND c.DeletedDatetime IS NULL";
                    try (PreparedStatement fallbackPs = conn.prepareStatement(fallbackSql)) {
                        fallbackPs.setInt(1, capabilityId);
                        try (ResultSet fallbackRs = fallbackPs.executeQuery()) {
                            if (fallbackRs.next()) {
                                Map<String, Object> row = new HashMap<>();
                                row.put("id", fallbackRs.getInt("ID"));
                                row.put("parentId", fallbackRs.getObject("Parent_ID"));
                                row.put("name", fallbackRs.getString("name"));
                                row.put("description", fallbackRs.getString("Description"));
                                row.put("typeName", fallbackRs.getString("typeName"));
                                row.put("level", fallbackRs.getInt("level"));
                                row.put("relation", fallbackRs.getString("relation"));
                                row.put("lastUpdated", fallbackRs.getTimestamp("LastUpdateDatetime"));
                                results.add(row);
                                System.out.println("[CapabilityDAO] Fallback query succeeded, returned capability: " + row.get("name"));
                            } else {
                                System.err.println("[CapabilityDAO] Fallback query also returned no results for capabilityId: " + capabilityId);
                            }
                        }
                    }
                }
                System.out.println("[CapabilityDAO] Returning " + results.size() + " hierarchy items for capabilityId: " + capabilityId);
                return results;
            }
        } catch (SQLException e) {
            System.err.println("[CapabilityDAO] SQLException in getCapabilityHierarchyFlat for capabilityId " + capabilityId + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
