package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Policy;
import com.example.budg_v2.model.PolicyType;
import com.example.budg_v2.model.PolicyLifecycleStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PolicyDAO {
    private static final Logger logger = LoggerFactory.getLogger(PolicyDAO.class);
    private static final String SELECT_ALL = "SELECT * FROM policy WHERE DeletedDatetime IS NULL ORDER BY ID DESC";
    private static final String SELECT_FOR_DROPDOWN = "SELECT ID, PrimaryName, Description, refNumber, Policy_Type FROM policy WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
    private static final String SELECT_BY_ID = "SELECT p.*, " +
            "CONCAT(cb.First_Name, ' ', cb.Last_Name) AS createdByName, " +
            "CONCAT(ub.First_Name, ' ', ub.Last_Name) AS lastUpdatedByName, " +
            "parent.PrimaryName AS parentName, " +
            "s.PrimaryName AS statusName " +
            "FROM policy p " +
            "LEFT JOIN people cb ON cb.ID = p.CreatedBy_ID " +
            "LEFT JOIN people ub ON ub.ID = p.LastUpdateUser_ID " +
            "LEFT JOIN policy parent ON parent.ID = p.ParentID " +
            "LEFT JOIN status s ON s.ID = p.Status " +
            "WHERE p.ID = ? AND p.DeletedDatetime IS NULL";
    private static final String SELECT_BY_REFNUMBER = "SELECT * FROM policy WHERE refNumber = ? AND DeletedDatetime IS NULL";
    private static final String SELECT_BY_REFNUMBER_EXCLUDE_ID = "SELECT * FROM policy WHERE refNumber = ? AND ID != ? AND DeletedDatetime IS NULL";
    private static final String SELECT_POLICY_TYPES = "SELECT ID, PrimaryName, Description FROM policy_type ORDER BY PrimaryName";
    private static final String SELECT_POLICY_LIFECYCLE_STATUSES = "SELECT ID, PrimaryName, Description FROM policy_lifecycle_status ORDER BY PrimaryName";
    private static final String SEARCH = "SELECT * FROM policy WHERE (PrimaryName LIKE ? OR Description LIKE ? OR refNumber LIKE ?) AND DeletedDatetime IS NULL ORDER BY ID DESC";
    private static final String INSERT = "INSERT INTO policy (ID, ParentID, isPublic, Status, Lifecycle_Status, Policy_Type, PrimaryName, refNumber, Description, EffectiveDate, EndDate, Internal, URL, createDatetime, LastUpdateDatetime, CreatedBy_ID, LastUpdateUser_ID) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE policy SET ParentID = ?, isPublic = ?, Status = ?, Lifecycle_Status = ?, Policy_Type = ?, PrimaryName = ?, refNumber = ?, Description = ?, EffectiveDate = ?, EndDate = ?, Internal = ?, URL = ?, LastUpdateDatetime = ?, LastUpdateUser_ID = ? WHERE ID = ?";
    private static final String DELETE = "UPDATE policy SET DeletedDatetime = ?, LastUpdateUser_ID = ? WHERE ID = ?";
    private static final String GET_MAX_ID = "SELECT MAX(ID) as max_id FROM policy";

    public List<Policy> getAllPolicies() throws SQLException {
        return getAllPoliciesForGuest();
    }

    /**
     * Get all policies for guest users (public, Enterprise only, not deleted)
     */
    private List<Policy> getAllPoliciesForGuest() throws SQLException {
        String guestFilter = com.example.budg_v2.service.SegmentAccessService.buildGuestFilterClause("Policy", "p", "p.ID");
        if (guestFilter == null) {
            return getAllPoliciesUnfiltered();
        }
        String sql = "SELECT p.* FROM policy p WHERE " + guestFilter + " ORDER BY p.ID DESC";
        List<Policy> policies = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                policies.add(mapResultSetToPolicy(rs));
            }
        }
        return policies;
    }

    private List<Policy> getAllPoliciesUnfiltered() throws SQLException {
        List<Policy> policies = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                policies.add(mapResultSetToPolicy(rs));
            }
        }
        return policies;
    }

    public List<Policy> getPoliciesForDropdown() throws SQLException {
        return getPoliciesForDropdownForGuest();
    }

    /**
     * Get policies for dropdown for guest users (public, Enterprise only, not deleted)
     */
    private List<Policy> getPoliciesForDropdownForGuest() throws SQLException {
        String guestFilter = com.example.budg_v2.service.SegmentAccessService.buildGuestFilterClause("Policy", "p", "p.ID");
        if (guestFilter == null) {
            return getPoliciesForDropdownUnfiltered();
        }
        String sql = "SELECT p.ID, p.PrimaryName, p.Description, p.refNumber, p.Policy_Type FROM policy p WHERE " + guestFilter + " ORDER BY p.PrimaryName";
        List<Policy> policies = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Policy policy = new Policy();
                policy.setId(rs.getInt("ID"));
                policy.setPrimaryName(rs.getString("PrimaryName"));
                policy.setDescription(rs.getString("Description"));
                policy.setRefNumber(rs.getString("refNumber"));
                policy.setPolicyType(rs.getObject("Policy_Type") != null ? rs.getInt("Policy_Type") : null);
                policies.add(policy);
            }
        }
        return policies;
    }

    private List<Policy> getPoliciesForDropdownUnfiltered() throws SQLException {
        List<Policy> policies = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_FOR_DROPDOWN);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Policy policy = new Policy();
                policy.setId(rs.getInt("ID"));
                policy.setPrimaryName(rs.getString("PrimaryName"));
                policy.setDescription(rs.getString("Description"));
                policy.setRefNumber(rs.getString("refNumber"));
                policy.setPolicyType(rs.getObject("Policy_Type") != null ? rs.getInt("Policy_Type") : null);
                policies.add(policy);
            }
        }
        return policies;
    }

    /**
     * Get all policies filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to.
     * For guest users (userId <= 0), applies guest filter (public, Enterprise only).
     * 
     * @param userId The user ID to filter for
     * @return List of accessible policies
     */
    public List<Policy> getAllPoliciesBySegmentAccess(int userId) throws SQLException {
        if (userId <= 0) {
            return getAllPoliciesForGuest();
        }
        String segmentFilter = com.example.budg_v2.service.SegmentAccessService
                .buildSelectedSegmentFilterClause(userId, "Policy", "p.ID");
        
        String sql = "SELECT * FROM policy p WHERE p.DeletedDatetime IS NULL AND " + 
                     segmentFilter + " ORDER BY p.PrimaryName";
        
        logger.info("getAllPoliciesBySegmentAccess SQL for user {}: {}", userId, sql.substring(0, Math.min(200, sql.length())));
        
        List<Policy> policies = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                policies.add(mapResultSetToPolicy(rs));
            }
        }
        logger.info("Found {} accessible policies for user {}", policies.size(), userId);
        return policies;
    }
    
    /**
     * Get policies for dropdown filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Parent selection only shows accessible objects.
     * 
     * @param userId The user ID to filter for
     * @return List of accessible policies for dropdown
     */
    public List<Policy> getPoliciesForDropdownBySegmentAccess(int userId) throws SQLException {
        if (userId <= 0) {
            return getPoliciesForDropdownForGuest();
        }
        String segmentFilter = com.example.budg_v2.service.SegmentAccessService
                .buildSelectedSegmentFilterClause(userId, "Policy", "p.ID");
        
        String sql = "SELECT p.ID, p.PrimaryName, p.Description, p.refNumber, p.ParentID, p.Policy_Type " +
                     "FROM policy p WHERE p.DeletedDatetime IS NULL AND " + 
                     segmentFilter + " ORDER BY p.PrimaryName";
        
        List<Policy> policies = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Policy policy = new Policy();
                policy.setId(rs.getInt("ID"));
                policy.setPrimaryName(rs.getString("PrimaryName"));
                policy.setDescription(rs.getString("Description"));
                policy.setRefNumber(rs.getString("refNumber"));
                policy.setParentId(rs.getObject("ParentID") != null ? rs.getInt("ParentID") : null);
                policy.setPolicyType(rs.getObject("Policy_Type") != null ? rs.getInt("Policy_Type") : null);
                policies.add(policy);
            }
        }
        logger.info("Found {} accessible policies for dropdown for user {}", policies.size(), userId);
        return policies;
    }

    public Policy getPolicyById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPolicy(rs);
                }
            }
        }
        return null;
    }

    public List<PolicyType> getPolicyTypes() throws SQLException {
        //system.out.println("PolicyDAO: Getting policy types");
        List<PolicyType> policyTypes = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_POLICY_TYPES);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                PolicyType policyType = new PolicyType();
                policyType.setId(rs.getInt("ID"));
                policyType.setPrimaryName(rs.getString("PrimaryName"));
                policyType.setDescription(rs.getString("Description"));
                policyTypes.add(policyType);
                //system.out.println("PolicyDAO: Added policy type: " + policyType.getPrimaryName());
            }
        }
        //system.out.println("PolicyDAO: Total policy types found: " + policyTypes.size());
        return policyTypes;
    }

    public List<PolicyLifecycleStatus> getPolicyLifecycleStatuses() throws SQLException {
        //system.out.println("PolicyDAO: Getting policy lifecycle statuses");
        List<PolicyLifecycleStatus> lifecycleStatuses = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_POLICY_LIFECYCLE_STATUSES);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                PolicyLifecycleStatus lifecycleStatus = new PolicyLifecycleStatus();
                lifecycleStatus.setId(rs.getInt("ID"));
                lifecycleStatus.setPrimaryName(rs.getString("PrimaryName"));
                lifecycleStatus.setDescription(rs.getString("Description"));
                lifecycleStatuses.add(lifecycleStatus);
                //system.out.println("PolicyDAO: Added lifecycle status: " + lifecycleStatus.getPrimaryName());
            }
        }
        //system.out.println("PolicyDAO: Total lifecycle statuses found: " + lifecycleStatuses.size());
        return lifecycleStatuses;
    }

    public List<Policy> searchPolicies(String searchTerm) throws SQLException {
        List<Policy> policies = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SEARCH)) {

            String searchPattern = "%" + searchTerm + "%";
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            stmt.setString(3, searchPattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    policies.add(mapResultSetToPolicy(rs));
                }
            }
        }
        return policies;
    }

    private int getNextId() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(GET_MAX_ID);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                int maxId = rs.getInt("max_id");
                return maxId + 1;
            }
            return 1; // If no records exist, start with 1
        }
    }

    public int createPolicy(Policy policy) throws SQLException {
        //system.out.println("PolicyDAO createPolicy - Starting policy creation");
        //system.out.println("PolicyDAO createPolicy - Policy data: " + policy.toString());

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT)) {

            Timestamp now = new Timestamp(System.currentTimeMillis());

            // Get next ID manually
            int nextId = getNextId();
            //system.out.println("PolicyDAO createPolicy - Generated ID: " + nextId);

            // Set parameters in correct order
            stmt.setInt(1, nextId); // ID
            stmt.setObject(2, policy.getParentId()); // ParentID
            stmt.setObject(3, policy.getIsPublic()); // isPublic
            stmt.setObject(4, policy.getStatus()); // Status
            stmt.setObject(5, policy.getLifecycleStatus()); // Lifecycle_Status
            stmt.setObject(6, policy.getPolicyType()); // Policy_Type

            // Handle string fields - convert empty strings to null
            String primaryName = policy.getPrimaryName();
            if (primaryName != null && primaryName.trim().isEmpty()) {
                primaryName = null;
            }
            stmt.setString(7, primaryName); // PrimaryName

            String refNumber = policy.getRefNumber();
            // Auto-generate refNumber if null/empty using nextId to ensure uniqueness
            if (refNumber == null || refNumber.trim().isEmpty()) {
                refNumber = "POL-" + nextId;
            }
            stmt.setString(8, refNumber); // refNumber

            String description = policy.getDescription();
            if (description != null && description.trim().isEmpty()) {
                description = null;
            }
            stmt.setString(9, description); // Description

            // Handle dates
            Timestamp effectiveDate = null;
            if (policy.getEffectiveDate() != null && !policy.getEffectiveDate().trim().isEmpty()) {
                try {
                    effectiveDate = Timestamp.valueOf(policy.getEffectiveDate() + " 00:00:00");
                    //system.out.println("PolicyDAO createPolicy - EffectiveDate converted to: " + effectiveDate);
                } catch (Exception e) {
                    //system.out.println("PolicyDAO createPolicy - Error converting EffectiveDate: " + e.getMessage());
                }
            }
            stmt.setObject(10, effectiveDate); // EffectiveDate

            Timestamp endDate = null;
            if (policy.getEndDate() != null && !policy.getEndDate().trim().isEmpty()) {
                try {
                    endDate = Timestamp.valueOf(policy.getEndDate() + " 00:00:00");
                    //system.out.println("PolicyDAO createPolicy - EndDate converted to: " + endDate);
                } catch (Exception e) {
                    //system.out.println("PolicyDAO createPolicy - Error converting EndDate: " + e.getMessage());
                }
            }
            stmt.setObject(11, endDate); // EndDate

            stmt.setObject(12, policy.getInternal()); // Internal

            String url = policy.getUrl();
            if (url != null && url.trim().isEmpty()) {
                url = null;
            }
            stmt.setString(13, url); // URL

            stmt.setTimestamp(14, now); // createDatetime
            stmt.setTimestamp(15, now); // LastUpdateDatetime (set to creation time on insert)
            stmt.setObject(16, policy.getCreatedById()); // CreatedBy_ID
            stmt.setObject(17, policy.getLastUpdateUserId()); // LastUpdateUser_ID

            //system.out.println("PolicyDAO createPolicy - Executing INSERT statement");
            int result = stmt.executeUpdate();
            //system.out.println("PolicyDAO createPolicy - ExecuteUpdate result: " + result);

            if (result > 0) {
                //system.out.println("PolicyDAO createPolicy - Policy created successfully with ID: " + nextId);
                return nextId;
            }

            //system.out.println("PolicyDAO createPolicy - No rows affected");
            return -1;
        }
    }

    public boolean updatePolicy(Policy policy, String userName) throws SQLException {
        //system.out.println("PolicyDAO updatePolicy - Starting policy update for ID: " + policy.getId());

        // الخطوة 1: جلب البيانات القديمة قبل التحديث
        Policy oldPolicy = getPolicyById(policy.getId());
        if (oldPolicy == null) {
            System.err.println("PolicyDAO updatePolicy - Policy not found with ID: " + policy.getId());
            return false;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE)) {

            Timestamp now = new Timestamp(System.currentTimeMillis());

            stmt.setObject(1, policy.getParentId());
            stmt.setObject(2, policy.getIsPublic());
            stmt.setObject(3, policy.getStatus());
            stmt.setObject(4, policy.getLifecycleStatus());
            stmt.setObject(5, policy.getPolicyType());

            // Handle string fields - convert empty strings to null
            String primaryName = policy.getPrimaryName();
            if (primaryName != null && primaryName.trim().isEmpty()) {
                primaryName = null;
            }
            stmt.setString(6, primaryName);

            String refNumber = policy.getRefNumber();
            if (refNumber != null && refNumber.trim().isEmpty()) {
                refNumber = null;
            }
            stmt.setString(7, refNumber);

            String description = policy.getDescription();
            if (description != null && description.trim().isEmpty()) {
                description = null;
            }
            stmt.setString(8, description);

            // Handle dates
            Timestamp effectiveDate = null;
            if (policy.getEffectiveDate() != null && !policy.getEffectiveDate().trim().isEmpty()) {
                try {
                    effectiveDate = Timestamp.valueOf(policy.getEffectiveDate() + " 00:00:00");
                } catch (Exception e) {
                    //system.out.println("PolicyDAO updatePolicy - Error converting EffectiveDate: " + e.getMessage());
                }
            }
            stmt.setObject(9, effectiveDate);

            Timestamp endDate = null;
            if (policy.getEndDate() != null && !policy.getEndDate().trim().isEmpty()) {
                try {
                    endDate = Timestamp.valueOf(policy.getEndDate() + " 00:00:00");
                } catch (Exception e) {
                    //system.out.println("PolicyDAO updatePolicy - Error converting EndDate: " + e.getMessage());
                }
            }
            stmt.setObject(10, endDate);

            stmt.setObject(11, policy.getInternal());

            String url = policy.getUrl();
            if (url != null && url.trim().isEmpty()) {
                url = null;
            }
            stmt.setString(12, url);

            // Check if there are actual changes before updating LastUpdateDatetime
            // Compare effectiveDate and endDate (handle null and empty strings)
            String oldEffectiveDate = oldPolicy.getEffectiveDate();
            String newEffectiveDate = policy.getEffectiveDate();
            if (oldEffectiveDate != null && oldEffectiveDate.trim().isEmpty()) oldEffectiveDate = null;
            if (newEffectiveDate != null && newEffectiveDate.trim().isEmpty()) newEffectiveDate = null;
            
            String oldEndDate = oldPolicy.getEndDate();
            String newEndDate = policy.getEndDate();
            if (oldEndDate != null && oldEndDate.trim().isEmpty()) oldEndDate = null;
            if (newEndDate != null && newEndDate.trim().isEmpty()) newEndDate = null;
            
            boolean hasChanges = !Objects.equals(oldPolicy.getParentId(), policy.getParentId()) ||
                                !Objects.equals(oldPolicy.getIsPublic(), policy.getIsPublic()) ||
                                !Objects.equals(oldPolicy.getStatus(), policy.getStatus()) ||
                                !Objects.equals(oldPolicy.getLifecycleStatus(), policy.getLifecycleStatus()) ||
                                !Objects.equals(oldPolicy.getPolicyType(), policy.getPolicyType()) ||
                                !Objects.equals(oldPolicy.getPrimaryName(), primaryName) ||
                                !Objects.equals(oldPolicy.getRefNumber(), refNumber) ||
                                !Objects.equals(oldPolicy.getDescription(), description) ||
                                !Objects.equals(oldEffectiveDate, newEffectiveDate) ||
                                !Objects.equals(oldEndDate, newEndDate) ||
                                !Objects.equals(oldPolicy.getInternal(), policy.getInternal()) ||
                                !Objects.equals(oldPolicy.getUrl(), url);
            
            // Determine LastUpdateDatetime and LastUpdateUserId based on whether there are changes
            Timestamp lastUpdateDatetime;
            Integer lastUpdateUserId;
            if (!hasChanges) {
                // If no changes, check if object has been edited before
                Integer oldLastUpdateUserId = oldPolicy.getLastUpdateUserId();
                String oldLastUpdateDatetimeStr = oldPolicy.getLastUpdateDatetime();
                
                if (oldLastUpdateUserId != null && oldLastUpdateDatetimeStr != null && !oldLastUpdateDatetimeStr.trim().isEmpty()) {
                    // Object has been edited before - keep the previous last update values
                    lastUpdateUserId = oldLastUpdateUserId;
                    try {
                        lastUpdateDatetime = Timestamp.valueOf(oldLastUpdateDatetimeStr);
                    } catch (Exception e) {
                        lastUpdateDatetime = now;
                    }
                } else {
                    // Object has never been edited - use CreatedBy and CreatedDatetime
                    lastUpdateUserId = oldPolicy.getCreatedById();
                    String createDatetimeStr = oldPolicy.getCreateDatetime();
                    if (createDatetimeStr != null && !createDatetimeStr.trim().isEmpty()) {
                        try {
                            lastUpdateDatetime = Timestamp.valueOf(createDatetimeStr);
                        } catch (Exception e) {
                            lastUpdateDatetime = now;
                        }
                    } else {
                        lastUpdateDatetime = now;
                    }
                }
            } else {
                // There are changes - update to current timestamp and provided user ID
                lastUpdateDatetime = now;
                lastUpdateUserId = policy.getLastUpdateUserId();
            }
            
            stmt.setTimestamp(13, lastUpdateDatetime); // LastUpdateDatetime
            stmt.setObject(14, lastUpdateUserId);
            stmt.setInt(15, policy.getId());

            int result = stmt.executeUpdate();
            //system.out.println("PolicyDAO updatePolicy - Update result: " + result);

            if (result > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات
                try {
                    createPolicyUpdateAuditRecords(policy.getId(), oldPolicy, policy, userName);
                    //system.out.println("✅ Policy update audit records created for ID: " + policy.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating policy update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // الخطوة 3: إنشاء snapshot جديد في policy_audit
                try {
                    createPolicyUpdateAuditSnapshot(policy.getId());
                    //system.out.println("✅ PolicyDAO: policy_audit update snapshot created for ID: " + policy.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating policy_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            return result > 0;
        }
    }

    public boolean deletePolicy(int id, int userId) throws SQLException {
        //system.out.println("PolicyDAO deletePolicy - Soft deleting policy ID: " + id);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE)) {

            Timestamp now = new Timestamp(System.currentTimeMillis());
            stmt.setTimestamp(1, now);
            stmt.setInt(2, userId);
            stmt.setInt(3, id);

            int result = stmt.executeUpdate();
            //system.out.println("PolicyDAO deletePolicy - Delete result: " + result);
            return result > 0;
        }
    }

    // Policy hierarchy method - similar to project hierarchy but using proper SQL query
    public List<java.util.Map<String, Object>> getPolicyHierarchy(int policyId) throws SQLException {
        List<java.util.Map<String, Object>> hierarchy = new ArrayList<>();

        // Get complete hierarchy: parents (ancestors) + current + siblings + children (descendants) + siblings' children
        // First, verify the policy exists and get its ParentID
        boolean policyExists = false;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT ParentID FROM policy WHERE ID = ? AND DeletedDatetime IS NULL")) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    policyExists = true;
                    rs.getObject("ParentID", Integer.class); // used only to confirm row exists
                } else {
                    return new ArrayList<>();
                }
            }
        }
        
        if (!policyExists) {
            return new ArrayList<>();
        }

        // Use SQL query similar to glossary hierarchy
        String sql = "WITH RECURSIVE " +
                // Get current policy's parent ID
                "current_parent AS (" +
                "    SELECT ParentID FROM policy WHERE ID = ? AND DeletedDatetime IS NULL " +
                "), " +
                // Get all ancestors (parents up the hierarchy)
                "ancestors AS (" +
                "    SELECT p.ID, p.ParentID, p.PrimaryName, p.Description, pt.PrimaryName as typeName, -1 as level, 'ancestor' as relation " +
                "    FROM policy p " +
                "    CROSS JOIN current_parent cp " +
                "    LEFT JOIN policy_type pt ON pt.ID = p.Policy_Type " +
                "    WHERE p.ID = cp.ParentID AND p.ID IS NOT NULL AND p.DeletedDatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT p.ID, p.ParentID, p.PrimaryName, p.Description, pt.PrimaryName as typeName, a.level - 1, 'ancestor' " +
                "    FROM policy p " +
                "    INNER JOIN ancestors a ON p.ID = a.ParentID " +
                "    LEFT JOIN policy_type pt ON pt.ID = p.Policy_Type " +
                "    WHERE a.level > -10 AND p.DeletedDatetime IS NULL " + // Prevent infinite recursion
                "), " +
                // Get siblings (other policies with the same ParentID as current)
                "siblings AS (" +
                "    SELECT p.ID, p.ParentID, p.PrimaryName, p.Description, pt.PrimaryName as typeName, 0 as level, 'sibling' as relation " +
                "    FROM policy p " +
                "    CROSS JOIN current_parent cp " +
                "    LEFT JOIN policy_type pt ON pt.ID = p.Policy_Type " +
                "    WHERE cp.ParentID IS NOT NULL AND p.ParentID = cp.ParentID " +
                "    AND p.ID != ? AND p.DeletedDatetime IS NULL " +
                "), " +
                // Get all descendants (children down the hierarchy)
                "descendants AS (" +
                "    SELECT p.ID, p.ParentID, p.PrimaryName, p.Description, pt.PrimaryName as typeName, 1 as level, 'descendant' as relation " +
                "    FROM policy p " +
                "    LEFT JOIN policy_type pt ON pt.ID = p.Policy_Type " +
                "    WHERE p.ParentID = ? AND p.DeletedDatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT p.ID, p.ParentID, p.PrimaryName, p.Description, pt.PrimaryName as typeName, d.level + 1, 'descendant' " +
                "    FROM policy p " +
                "    INNER JOIN descendants d ON p.ParentID = d.ID " +
                "    LEFT JOIN policy_type pt ON pt.ID = p.Policy_Type " +
                "    WHERE d.level < 10 AND p.DeletedDatetime IS NULL " + // Prevent infinite recursion
                "), " +
                // Get children of siblings (siblings' descendants)
                "sibling_children AS (" +
                "    SELECT p.ID, p.ParentID, p.PrimaryName, p.Description, pt.PrimaryName as typeName, 1 as level, 'sibling_child' as relation " +
                "    FROM policy p " +
                "    LEFT JOIN policy_type pt ON pt.ID = p.Policy_Type " +
                "    WHERE p.ParentID IN (SELECT ID FROM siblings) AND p.DeletedDatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT p.ID, p.ParentID, p.PrimaryName, p.Description, pt.PrimaryName as typeName, sc.level + 1, 'sibling_child' " +
                "    FROM policy p " +
                "    INNER JOIN sibling_children sc ON p.ParentID = sc.ID " +
                "    LEFT JOIN policy_type pt ON pt.ID = p.Policy_Type " +
                "    WHERE sc.level < 10 AND p.DeletedDatetime IS NULL " + // Prevent infinite recursion
                ") " +
                // Combine: ancestors + current + siblings + descendants + siblings' children
                "SELECT combined.ID, combined.ParentID, combined.PrimaryName, combined.Description, combined.typeName, combined.level, combined.relation " +
                "FROM (" +
                "    SELECT p.ID, p.ParentID, p.PrimaryName, p.Description, pt.PrimaryName as typeName, 0 as level, 'current' as relation " +
                "    FROM policy p " +
                "    LEFT JOIN policy_type pt ON pt.ID = p.Policy_Type " +
                "    WHERE p.ID = ? AND p.DeletedDatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT ID, ParentID, PrimaryName, Description, typeName, level, relation FROM ancestors " +
                "    UNION ALL " +
                "    SELECT ID, ParentID, PrimaryName, Description, typeName, level, relation FROM siblings " +
                "    UNION ALL " +
                "    SELECT ID, ParentID, PrimaryName, Description, typeName, level, relation FROM descendants " +
                "    UNION ALL " +
                "    SELECT ID, ParentID, PrimaryName, Description, typeName, level, relation FROM sibling_children " +
                ") combined " +
                "ORDER BY level, PrimaryName";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, policyId); // For current_parent query
            stmt.setInt(2, policyId); // For siblings query (exclude current)
            stmt.setInt(3, policyId); // For descendants query
            stmt.setInt(4, policyId); // For current policy query

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> item = new java.util.HashMap<>();
                    item.put("id", rs.getInt("ID"));
                    item.put("parentId", rs.getObject("ParentID", Integer.class));
                    item.put("name", rs.getString("PrimaryName"));
                    item.put("displayName", rs.getString("PrimaryName"));
                    item.put("description", rs.getString("Description"));
                    item.put("typeName", rs.getString("typeName"));
                    item.put("level", rs.getInt("level"));
                    item.put("relation", rs.getString("relation"));

                    hierarchy.add(item);
                }
            }
        }

        return hierarchy;
    }

    // Policy relationships method
    public List<java.util.Map<String, Object>> getPolicyRelationshipsBySourceId(int sourceId) throws SQLException {
        List<java.util.Map<String, Object>> relationships = new ArrayList<>();

        String sql = "SELECT pxp.id, pxp.sourceid, pxp.targetid, pxp.relationtype, pxp.description, " +
                "p.PrimaryName as targetPolicyName, pt.PrimaryName as targetPolicyType, " +
                "prt.PrimaryName as relationTypeName " +
                "FROM policy_x_policy pxp " +
                "LEFT JOIN policy source_p ON source_p.ID = pxp.sourceid " +
                "LEFT JOIN policy p ON p.ID = pxp.targetid " +
                "LEFT JOIN policy_type pt ON pt.ID = p.Policy_Type " +
                "LEFT JOIN policy_x_policy_relation_type prt ON prt.ID = pxp.relationtype " +
                "WHERE pxp.sourceid = ? " +
                "AND (source_p.DeletedDatetime IS NULL OR source_p.DeletedDatetime = '1970-01-01 00:00:00') " +
                "AND (p.DeletedDatetime IS NULL OR p.DeletedDatetime = '1970-01-01 00:00:00')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, sourceId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> relationship = new java.util.HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("sourceId", rs.getInt("sourceid"));
                    relationship.put("targetPolicyId", rs.getInt("targetid"));
                    relationship.put("relationType", rs.getInt("relationtype"));
                    relationship.put("description", rs.getString("description"));
                    relationship.put("targetPolicyName", rs.getString("targetPolicyName"));
                    relationship.put("targetPolicyType", rs.getString("targetPolicyType"));
                    relationship.put("relationTypeName", rs.getString("relationTypeName"));

                    relationships.add(relationship);
                }
            }
        }

        return relationships;
    }

    // Delete policy relationship
    public boolean deletePolicyRelationship(int relationshipId) throws SQLException {
        String sql = "DELETE FROM policy_x_policy WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, relationshipId);

            int result = stmt.executeUpdate();
            return result > 0;
        }
    }

    private Policy mapResultSetToPolicy(ResultSet rs) throws SQLException {
        Policy policy = new Policy();
        policy.setId(rs.getInt("ID"));
        policy.setParentId(rs.getObject("ParentID", Integer.class));
        policy.setIsPublic(rs.getObject("isPublic", Integer.class));
        policy.setStatus(rs.getObject("Status", Integer.class));
        try {
            policy.setStatusName(rs.getString("statusName"));
        } catch (SQLException e) {
            policy.setStatusName(null);
        }
        policy.setLifecycleStatus(rs.getObject("Lifecycle_Status", Integer.class));
        policy.setPolicyType(rs.getObject("Policy_Type", Integer.class));
        policy.setPrimaryName(rs.getString("PrimaryName"));
        policy.setRefNumber(rs.getString("refNumber"));
        policy.setDescription(rs.getString("Description"));

        // Handle dates
        Timestamp effectiveDate = rs.getTimestamp("EffectiveDate");
        if (effectiveDate != null) {
            policy.setEffectiveDate(effectiveDate.toLocalDateTime().toLocalDate().toString());
        }

        Timestamp endDate = rs.getTimestamp("EndDate");
        if (endDate != null) {
            policy.setEndDate(endDate.toLocalDateTime().toLocalDate().toString());
        }

        policy.setInternal(rs.getInt("Internal"));
        policy.setUrl(rs.getString("URL"));

        Timestamp createDatetime = rs.getTimestamp("createDatetime");
        if (createDatetime != null) {
            policy.setCreateDatetime(createDatetime.toString());
        }

        Timestamp lastUpdateDatetime = rs.getTimestamp("LastUpdateDatetime");
        if (lastUpdateDatetime != null) {
            policy.setLastUpdateDatetime(lastUpdateDatetime.toString());
        }

        policy.setCreatedById(rs.getObject("CreatedBy_ID", Integer.class));
        policy.setLastUpdateUserId(rs.getObject("LastUpdateUser_ID", Integer.class));

        // Add user name fields only if they exist
        try {
            policy.setCreatedByName(rs.findColumn("createdByName") > 0 ? rs.getString("createdByName") : null);
        } catch (SQLException e) {
            policy.setCreatedByName(null);
        }
        try {
            policy.setLastUpdatedByName(rs.findColumn("lastUpdatedByName") > 0 ? rs.getString("lastUpdatedByName") : null);
        } catch (SQLException e) {
            policy.setLastUpdatedByName(null);
        }
        // Add parent name if it exists
        try {
            policy.setParentName(rs.findColumn("parentName") > 0 ? rs.getString("parentName") : null);
        } catch (SQLException e) {
            policy.setParentName(null);
        }
        
        // Add segment info
        try {
            int policyId = policy.getId();
            Integer segmentId = getSegmentIdForPolicy(policyId);
            policy.setSegmentId(segmentId);
            String segmentName = segmentId != null ? getSegmentName(segmentId) : "Not Specified";
            policy.setSegmentName(segmentName);
        } catch (Exception e) {
            // Show "Not Specified" so errors are visible
            policy.setSegmentId(null);
            policy.setSegmentName("Not Specified");
            System.err.println("❌ Error getting segment for Policy " + policy.getId() + ": " + e.getMessage());
        }

        return policy;
    }

    // Policy relationship creation method
    public boolean createPolicyRelationship(int sourceId, int targetId, int relationType, String description, Integer userId) throws SQLException {
        //system.out.println("PolicyDAO: Creating relationship - sourceId: " + sourceId +
                        // ", targetId: " + targetId + ", relationType: " + relationType +
                    //     ", description: " + description + ", userId: " + userId);

        String sql = "INSERT INTO policy_x_policy (sourceid, targetid, relationtype, description, lastupdate_userid) VALUES (?, ?, ?, ?, ?)";
        //system.out.println("PolicyDAO: SQL: " + sql);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, sourceId);
            stmt.setInt(2, targetId);
            stmt.setInt(3, relationType);
            stmt.setString(4, description);
            if (userId != null) {
                stmt.setInt(5, userId);
            } else {
                stmt.setNull(5, java.sql.Types.INTEGER);
            }

            int result = stmt.executeUpdate();
            //system.out.println("PolicyDAO: Execute update result: " + result);
            boolean success = result > 0;
            //system.out.println("PolicyDAO: Relationship creation success: " + success);
            return success;
        } catch (SQLException e) {
            //system.out.println("PolicyDAO: SQLException in createPolicyRelationship: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // Policy relationship update method
    public boolean updatePolicyRelationship(int relationshipId, int relationType, int targetPolicyId, String description, Integer userId) throws SQLException {
        //system.out.println("PolicyDAO: Updating relationship - ID: " + relationshipId +
                  //       ", relationType: " + relationType + ", targetPolicyId: " + targetPolicyId +
                   //      ", description: " + description + ", userId: " + userId);

        String sql = "UPDATE policy_x_policy SET relationtype = ?, targetid = ?, description = ?, lastupdate_userid = ?, lastupdatedatetime = CURRENT_TIMESTAMP WHERE id = ?";
        //system.out.println("PolicyDAO: Update SQL: " + sql);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, relationType);
            stmt.setInt(2, targetPolicyId);
            stmt.setString(3, description);
            if (userId != null) {
                stmt.setInt(4, userId);
            } else {
                stmt.setNull(4, java.sql.Types.INTEGER);
            }
            stmt.setInt(5, relationshipId);

            int result = stmt.executeUpdate();
            //system.out.println("PolicyDAO: Update execute result: " + result);
            boolean success = result > 0;
            //system.out.println("PolicyDAO: Relationship update success: " + success);
            return success;
        } catch (SQLException e) {
            //system.out.println("PolicyDAO: SQLException in updatePolicyRelationship: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // Policy relationship types method
    public List<java.util.Map<String, Object>> getPolicyRelationTypes() throws SQLException {
        List<java.util.Map<String, Object>> relationTypes = new ArrayList<>();

        String sql = "SELECT ID, PrimaryName, Description FROM policy_x_policy_relation_type ORDER BY PrimaryName";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                java.util.Map<String, Object> relationType = new java.util.HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryName", rs.getString("PrimaryName"));
                relationType.put("description", rs.getString("Description"));

                relationTypes.add(relationType);
            }
        }

        return relationTypes;
    }

    // RefNumber uniqueness methods
    public Policy getPolicyByRefNumber(String refNumber) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER)) {

            pstmt.setString(1, refNumber);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPolicy(rs);
                }
            }
        }

        return null;
    }

    public Policy getPolicyByRefNumberExcludingId(String refNumber, int excludeId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER_EXCLUDE_ID)) {

            pstmt.setString(1, refNumber);
            pstmt.setInt(2, excludeId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPolicy(rs);
                }
            }
        }

        return null;
    }

    /**
     * Check if RefNumber is unique (for create operations).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     */
    public boolean isRefNumberUnique(String refNumber) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUnique("Policy", refNumber);
    }

    /**
     * Check if RefNumber is unique for update (excluding current ID).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     * Rule: Can repeat ref if it's the same object (excludeId), but cannot repeat for different objects.
     */
    public boolean isRefNumberUniqueForUpdate(String refNumber, int excludeId) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUniqueForUpdate("Policy", refNumber, excludeId);
    }

    public boolean isPrimaryNameUnique(String primaryName) throws SQLException {
        return getPolicyByPrimaryName(primaryName) == null;
    }

    public boolean isPrimaryNameUniqueForUpdate(String primaryName, int excludeId) throws SQLException {
        return getPolicyByPrimaryNameExcludingId(primaryName, excludeId) == null;
    }

    public Policy getPolicyByPrimaryName(String primaryName) throws SQLException {
        String query = "SELECT * FROM policy WHERE LOWER(PrimaryName) = LOWER(?) AND DeletedDatetime IS NULL LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, primaryName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPolicy(rs);
                }
            }
        }
        return null;
    }

    private Policy getPolicyByPrimaryNameExcludingId(String primaryName, int excludeId) throws SQLException {
        String query = "SELECT * FROM policy WHERE LOWER(PrimaryName) = LOWER(?) AND ID != ? AND DeletedDatetime IS NULL LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, primaryName);
            stmt.setInt(2, excludeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPolicy(rs);
                }
            }
        }
        return null;
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int policyId, String updateType, String field, String value, String userName) throws SQLException {
        //system.out.println("PolicyDAO: createNewAuditRecord called - policyId: " + policyId + ", field: " + field + ", value: " + value);

        auditStmt.setInt(1, policyId);        // id
        auditStmt.setString(2, updateType);    // updateType
        auditStmt.setString(3, field);          // field
        auditStmt.setString(4, value);         // to
        auditStmt.setString(5, userName);       // author

        //system.out.println("PolicyDAO: Executing audit insert for field: " + field);
        auditStmt.executeUpdate();

        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                int auditId = generatedKeys.getInt(1);
                //system.out.println("PolicyDAO: Audit record created with ID: " + auditId);
                return auditId;
            }
        }
        //system.out.println("PolicyDAO: No generated key returned");
        return -1;
    }

    /**
     * إنشاء audit records للسياسة الجديدة
     * يتم استدعاء هذا method بعد إنشاء السياسة بنجاح
     */
    public void createPolicyAuditRecords(int policyId, String userName) throws SQLException {
        //system.out.println("PolicyDAO: createPolicyAuditRecords called with policyId: " + policyId + ", userName: " + userName);
        Connection conn = null;
        PreparedStatement auditStmt = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            //system.out.println("PolicyDAO: Transaction started");

            // 1. الحصول على بيانات السياسة
            String policyDataSql = "SELECT * FROM policy WHERE ID = ?";
            //system.out.println("PolicyDAO: Executing SQL: " + policyDataSql);
            PreparedStatement policyStmt = conn.prepareStatement(policyDataSql);
            policyStmt.setInt(1, policyId);
            ResultSet policyRs = policyStmt.executeQuery();

            if (!policyRs.next()) {
                //system.out.println("PolicyDAO: Policy not found with ID: " + policyId);
                throw new SQLException("Policy not found with ID: " + policyId);
            }
            //system.out.println("PolicyDAO: Policy found, starting audit record creation");

            // 2. إعداد audit statement
            String auditSql = """
                INSERT INTO policy_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Policy', 'Details', ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);

            // Primary Name
            String primaryName = policyRs.getString("PrimaryName");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, policyId, "Added", "Primary Name", primaryName, userName);
            }

            // Description
            String description = policyRs.getString("Description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, policyId, "Added", "Description", description, userName);
            }

            // Reference Number
            String refNumber = policyRs.getString("refNumber");
            if (refNumber != null && !refNumber.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, policyId, "Added", "Reference Number", refNumber, userName);
            }

            // Parent Policy
            Integer parentId = policyRs.getObject("ParentID", Integer.class);
            if (parentId != null) {
                String parentName = getPolicyName(conn, parentId);
                if (parentName != null) {
                    createNewAuditRecord(conn, auditStmt, policyId, "Added", "Parent Policy", parentName, userName);
                }
            }

            // Status
            Integer statusId = policyRs.getObject("Status", Integer.class);
            if (statusId != null) {
                String statusName = getStatusPrimaryName(conn, statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, policyId, "Status Change", "Status", statusName, userName);
                }
            }

            // Lifecycle Status
            Integer lifecycleStatusId = policyRs.getObject("Lifecycle_Status", Integer.class);
            if (lifecycleStatusId != null) {
                String lifecycleStatusName = getPolicyLifecycleStatusName(conn, lifecycleStatusId);
                if (lifecycleStatusName != null) {
                    createNewAuditRecord(conn, auditStmt, policyId, "Status Change", "Lifecycle Status", lifecycleStatusName, userName);
                }
            }

            // Policy Type
            Integer policyTypeId = policyRs.getObject("Policy_Type", Integer.class);
            if (policyTypeId != null) {
                String policyTypeName = getPolicyTypeName(conn, policyTypeId);
                if (policyTypeName != null) {
                    createNewAuditRecord(conn, auditStmt, policyId, "Added", "Policy Type", policyTypeName, userName);
                }
            }

            // Is Public
            Integer isPublic = policyRs.getObject("isPublic", Integer.class);
            if (isPublic != null) {
                String isPublicName = getViewingName(conn, isPublic);
                if (isPublicName != null) {
                    createNewAuditRecord(conn, auditStmt, policyId, "Added", "Is Public", isPublicName, userName);
                }
            }

            // Created By
            Integer createdById = policyRs.getObject("CreatedBy_ID", Integer.class);
            if (createdById != null) {
                String createdByName = getPersonFullName(conn, createdById);
                if (createdByName != null) {
                    createNewAuditRecord(conn, auditStmt, policyId, "Added", "Created By", createdByName, userName);
                }
            }
            
            // Internal (Yes/No for History)
            Integer internalValue = policyRs.getObject("Internal", Integer.class);
            if (internalValue != null) {
                String internalText = (internalValue == 1) ? "Yes" : "No";
                createNewAuditRecord(conn, auditStmt, policyId, "Added", "Internal", internalText, userName);
            }
            
            // URL
            String url = policyRs.getString("URL");
            if (url != null && !url.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, policyId, "Added", "URL", url, userName);
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
     * إنشاء سجل في جدول policy_audit بعد إنشاء السياسة
     * يتم استدعاء هذا method بعد إنشاء السياسة بنجاح
     */
    public void createPolicyAuditRecord(int policyId) throws SQLException {
        String sql = """
            INSERT INTO policy_audit (
                ID, ParentID, isPublic, Status, Lifecycle_Status, Policy_Type, PrimaryName, refNumber, Description, 
                EffectiveDate, EndDate, Internal, URL, createDatetime, LastUpdateDatetime, DeletedDatetime, 
                CreatedBy_ID, LastUpdateUser_ID, Rev_Type
            )
            SELECT 
                ID, ParentID, isPublic, Status, Lifecycle_Status, Policy_Type, PrimaryName, refNumber, Description, 
                EffectiveDate, EndDate, Internal, URL, createDatetime, LastUpdateDatetime, DeletedDatetime, 
                CreatedBy_ID, LastUpdateUser_ID, 'Added'
            FROM policy 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            ps.executeUpdate();
        }
    }


    // Overloaded version that accepts Connection (prevents connection leaks in transactions)
    private String getPolicyName(Connection conn, int policyId) throws SQLException {
        String sql = "SELECT PrimaryName FROM policy WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }
    // Overloaded version that accepts Connection (prevents connection leaks in transactions)
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
            // Try primaryname as fallback
        }
        
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
            // Ignore
        }
        
        return "Status " + statusId; // Fallback
    }


    // Overloaded version that accepts Connection (prevents connection leaks in transactions)
    private String getPolicyLifecycleStatusName(Connection conn, int lifecycleStatusId) throws SQLException {
        String sql = "SELECT PrimaryName FROM policy_lifecycle_status WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleStatusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    // Overloaded version that accepts Connection (prevents connection leaks in transactions)
    private String getPolicyTypeName(Connection conn, int policyTypeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM policy_type WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }


    // Overloaded version that accepts Connection (prevents connection leaks in transactions)
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

    // Overloaded version that accepts Connection (prevents connection leaks in transactions)
    private String getViewingName(Connection conn, int viewingId) throws SQLException {
        // Try Name column first
        try {
            String sql = "SELECT Name FROM viewing WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, viewingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString("Name");
                        if (name != null) return name;
                    }
                }
            }
        } catch (SQLException e) {
            // Ignore and try fallback
        }
        
        return "Viewing " + viewingId; // Fallback
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إنشاء السياسة
     * يتم استدعاء هذا method بعد إنشاء السياسة بنجاح
     */
    public void createStakeholderAuditRecords(int policyId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. إعداد audit statement للـ stakeholder
            String auditSql = """
                INSERT INTO policy_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Stakeholder', 'link', ?, ?, NULL, ?, ?)
            """;
            
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleId(policyId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }
            
            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getRoleName(conn, actualRoleId);
            if (roleName == null) roleName = "Policy Owner"; // fallback
            
            // Role
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, policyId, "Added", "Role", roleName, userName);
            }
            
            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusId(policyId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(conn, statusId);
                if (fetchedStatusName != null) statusName = fetchedStatusName;
            }
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, policyId, "Added", "Role Status", statusName, userName);
            }
            
            // Name
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, policyId, "Added", "Name", userFullName, userName);
            }
            
            conn.commit(); // تأكيد الـ transaction
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback(); // إلغاء الـ transaction في حالة الخطأ
            }
            throw e;
        } finally {
            // تنظيف الموارد
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * تسجيل عملية تعديل Stakeholder في الـ audit history
     */
    public void createStakeholderEditAuditRecord(int policyId, String userName, String field, String oldValue, String newValue) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            String auditSql = """
                INSERT INTO policy_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Stakeholder', 'edit', 'Updated', ?, ?, ?, ?)
            """;
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                auditStmt.setInt(1, policyId);
                auditStmt.setString(2, field);
                auditStmt.setString(3, oldValue);
                auditStmt.setString(4, newValue);
                auditStmt.setString(5, userName);
                auditStmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * تسجيل عملية حذف Stakeholder في الـ audit history
     */
    public void createStakeholderDeleteAuditRecord(int policyId, String userName, String fullName, String roleName, String statusName) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            String auditSql = """
                INSERT INTO policy_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Stakeholder', 'delete', 'Deleted', ?, ?, NULL, ?)
            """;
            try (PreparedStatement stmt = conn.prepareStatement(auditSql)) {
                // full name
                stmt.setInt(1, policyId);
                stmt.setString(2, "Name");
                stmt.setString(3, fullName);
                stmt.setString(4, userName);
                stmt.executeUpdate();
                // role
                stmt.setInt(1, policyId);
                stmt.setString(2, "Role");
                stmt.setString(3, roleName);
                stmt.setString(4, userName);
                stmt.executeUpdate();
                // status
                stmt.setInt(1, policyId);
                stmt.setString(2, "Role Status");
                stmt.setString(3, statusName);
                stmt.setString(4, userName);
                stmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // Overloaded version that accepts Connection (prevents connection leaks in transactions)
    private String getRoleName(Connection conn, int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }
    
    // Get actual roleID from object_x_people for the stakeholder
    private Integer getStakeholderRoleId(int policyId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN policy_x_objectxpeople pxo ON pxo.object_x_ip = oxp.id " +
                    "WHERE pxo.policy_id = ? " +
                    "ORDER BY pxo.id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }
    
    // Get statusID from object_x_people for the stakeholder
    private Integer getStakeholderStatusId(int policyId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN policy_x_objectxpeople pxo ON pxo.object_x_ip = oxp.id " +
                    "WHERE pxo.policy_id = ? " +
                    "ORDER BY pxo.id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("statusID");
            }
        }
        return null;
    }


    // Overloaded version that accepts Connection (prevents connection leaks in transactions)
    private String getStatusNameById(Connection conn, int statusId) throws SQLException {
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
     * Create or update stakeholder from bulk upload
     * If stakeholder already exists for this policy and user, update the role
     * Otherwise, create new stakeholder
     */
    public void createOrUpdateStakeholderFromBulk(int policyId, int userId, int roleId, int currentUserId, String userName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check if stakeholder already exists for this policy and user
                String checkSql = """
                    SELECT oxp.ID 
                    FROM object_x_people oxp
                    JOIN policy_x_objectxpeople pxop ON pxop.object_x_ip = oxp.ID
                    WHERE pxop.policy_id = ? AND oxp.ipid = ?
                    LIMIT 1
                """;
                
                Integer existingOxpId = null;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, policyId);
                    checkStmt.setInt(2, userId);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            existingOxpId = rs.getInt("ID");
                        }
                    }
                }
                
                int objectXPeopleId;
                
                if (existingOxpId != null) {
                    // Update existing stakeholder
                    objectXPeopleId = existingOxpId;
                    
                    // Get old role for audit
                    String getOldRoleSql = "SELECT roleID FROM object_x_people WHERE ID = ?";
                    Integer oldRoleId = null;
                    try (PreparedStatement getOldRoleStmt = conn.prepareStatement(getOldRoleSql)) {
                        getOldRoleStmt.setInt(1, objectXPeopleId);
                        try (ResultSet rs = getOldRoleStmt.executeQuery()) {
                            if (rs.next()) {
                                oldRoleId = rs.getObject("roleID", Integer.class);
                            }
                        }
                    }
                    
                    // Update role only if it changed
                    if (oldRoleId == null || !oldRoleId.equals(roleId)) {
                        String updateSql = """
                            UPDATE object_x_people 
                            SET RoleID = ?, lastupdatedatetime = NOW(), lastupdateuser_id = ?
                            WHERE ID = ?
                        """;
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setInt(1, roleId);
                            updateStmt.setInt(2, currentUserId);
                            updateStmt.setInt(3, objectXPeopleId);
                            updateStmt.executeUpdate();
                        }
                        
                        // Create audit records for update if role changed
                        if (oldRoleId != null) {
                            String oldRoleName = getRoleName(conn, oldRoleId);
                            String newRoleName = getRoleName(conn, roleId);
                            if (oldRoleName != null && newRoleName != null) {
                                insertStakeholderAudit(conn, policyId, "Role", oldRoleName, newRoleName, "edit", "Updated", userName);
                            }
                        }
                    } else {
                        // Role hasn't changed, no update needed
                        logger.debug("Stakeholder role unchanged for policy ID: {}, user ID: {}, role ID: {}", policyId, userId, roleId);
                    }
                } else {
                    // Create new stakeholder
                    String insertSql = """
                        INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
                        VALUES (NULL, ?, ?, 2, 1, NOW(), ?)
                    """;
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                        insertStmt.setInt(1, userId);
                        insertStmt.setInt(2, roleId);
                        insertStmt.setInt(3, currentUserId);
                        insertStmt.executeUpdate();
                        
                        try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                objectXPeopleId = generatedKeys.getInt(1);
                            } else {
                                throw new SQLException("Failed to get generated key for object_x_people");
                            }
                        }
                    }
                    
                    // Link to policy
                    linkStakeholderToPolicy(conn, policyId, objectXPeopleId, currentUserId);
                    
                    // Create audit records for new stakeholder
                    String fullName = getPersonFullName(conn, userId);
                    String roleName = getRoleName(conn, roleId);
                    String statusName = "Active";
                    
                    if (fullName != null) {
                        insertStakeholderAudit(conn, policyId, "Name", null, fullName, "link", "Added", userName);
                    }
                    if (roleName != null) {
                        insertStakeholderAudit(conn, policyId, "Role", null, roleName, "link", "Added", userName);
                    }
                    insertStakeholderAudit(conn, policyId, "Role Status", null, statusName, "link", "Added", userName);
                }
                
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    
    /**
     * Link stakeholder to policy
     */
    
    /**
     * Insert stakeholder audit record
     */
    private void insertStakeholderAudit(Connection conn, int policyId, String field, String oldValue, String newValue, String event, String updateType, String userName) throws SQLException {
        String sql = "INSERT INTO policy_audit_history (id, object, event, updateType, field, `from`, `to`, author) VALUES (?, 'Stakeholder', ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, policyId);
            stmt.setString(2, event);
            stmt.setString(3, updateType);
            stmt.setString(4, field);
            stmt.setString(5, oldValue);
            stmt.setString(6, newValue);
            stmt.setString(7, userName);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Helper method للمقارنة بين القيم (يتعامل مع null و empty strings)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        // Handle String values specially - treat null and empty string as equal
        if (obj1 instanceof String || obj2 instanceof String) {
            String str1 = (obj1 == null) ? "" : obj1.toString().trim();
            String str2 = (obj2 == null) ? "" : obj2.toString().trim();
            return str1.equals(str2);
        }
        
        // For non-String values, use standard null comparison
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.equals(obj2);
    }

    /**
     * Helper method لإنشاء audit record للـ update مع from و to
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, 
            int policyId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, policyId);
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
     * إنشاء audit records عند تحديث السياسة
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    public void createPolicyUpdateAuditRecords(int policyId, Policy oldPolicy, Policy newPolicy, String userName) throws SQLException {
        //system.out.println("🔍 PolicyDAO.createPolicyUpdateAuditRecords - START for ID: " + policyId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO policy_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldPolicy.getPrimaryName(), newPolicy.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Updated", "Primary Name", oldPolicy.getPrimaryName(), newPolicy.getPrimaryName(), userName);
            }
            
            // Reference Number
            if (!isEqual(oldPolicy.getRefNumber(), newPolicy.getRefNumber())) {
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Updated", "Reference Number", oldPolicy.getRefNumber(), newPolicy.getRefNumber(), userName);
            }
            
            // Description
            if (!isEqual(oldPolicy.getDescription(), newPolicy.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Updated", "Description", oldPolicy.getDescription(), newPolicy.getDescription(), userName);
            }
            
            // Parent Policy
            if (!isEqual(oldPolicy.getParentId(), newPolicy.getParentId())) {
                String oldParentName = oldPolicy.getParentId() != null ? getPolicyName(conn, oldPolicy.getParentId()) : null;
                String newParentName = newPolicy.getParentId() != null ? getPolicyName(conn, newPolicy.getParentId()) : null;
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Updated", "Parent Policy", oldParentName, newParentName, userName);
            }
            
            // Status (Status Change updateType)
            if (!isEqual(oldPolicy.getStatus(), newPolicy.getStatus())) {
                String oldStatusName = oldPolicy.getStatus() != null ? getStatusPrimaryName(conn, oldPolicy.getStatus()) : null;
                String newStatusName = newPolicy.getStatus() != null ? getStatusPrimaryName(conn, newPolicy.getStatus()) : null;
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
            // Lifecycle Status (Status Change updateType)
            if (!isEqual(oldPolicy.getLifecycleStatus(), newPolicy.getLifecycleStatus())) {
                String oldLifecycleName = oldPolicy.getLifecycleStatus() != null ? getPolicyLifecycleStatusName(conn, oldPolicy.getLifecycleStatus()) : null;
                String newLifecycleName = newPolicy.getLifecycleStatus() != null ? getPolicyLifecycleStatusName(conn, newPolicy.getLifecycleStatus()) : null;
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Status Change", "Lifecycle Status", oldLifecycleName, newLifecycleName, userName);
            }
            
            // Policy Type
            if (!isEqual(oldPolicy.getPolicyType(), newPolicy.getPolicyType())) {
                String oldTypeName = oldPolicy.getPolicyType() != null ? getPolicyTypeName(conn, oldPolicy.getPolicyType()) : null;
                String newTypeName = newPolicy.getPolicyType() != null ? getPolicyTypeName(conn, newPolicy.getPolicyType()) : null;
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Updated", "Policy Type", oldTypeName, newTypeName, userName);
            }
            
            // Is Public
            if (!isEqual(oldPolicy.getIsPublic(), newPolicy.getIsPublic())) {
                String oldPublicName = oldPolicy.getIsPublic() != null ? getViewingName(conn, oldPolicy.getIsPublic()) : null;
                String newPublicName = newPolicy.getIsPublic() != null ? getViewingName(conn, newPolicy.getIsPublic()) : null;
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Updated", "Is Public", oldPublicName, newPublicName, userName);
            }
            
            // Effective Date
            if (!isEqual(oldPolicy.getEffectiveDate(), newPolicy.getEffectiveDate())) {
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Updated", "Effective Date", oldPolicy.getEffectiveDate(), newPolicy.getEffectiveDate(), userName);
            }
            
            // End Date
            if (!isEqual(oldPolicy.getEndDate(), newPolicy.getEndDate())) {
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Updated", "End Date", oldPolicy.getEndDate(), newPolicy.getEndDate(), userName);
            }
            
            // Internal (Yes/No)
            if (!isEqual(oldPolicy.getInternal(), newPolicy.getInternal())) {
                String oldInternalValue = oldPolicy.getInternal() != null ? (oldPolicy.getInternal() == 1 ? "Yes" : "No") : null;
                String newInternalValue = newPolicy.getInternal() != null ? (newPolicy.getInternal() == 1 ? "Yes" : "No") : null;
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Updated", "Internal", oldInternalValue, newInternalValue, userName);
            }
            
            // URL
            if (!isEqual(oldPolicy.getUrl(), newPolicy.getUrl())) {
                createUpdateAuditRecord(conn, auditStmt, policyId, "Policy", "Details", 
                    "Updated", "URL", oldPolicy.getUrl(), newPolicy.getUrl(), userName);
            }
            
            conn.commit();
            //system.out.println("✅ PolicyDAO.createPolicyUpdateAuditRecords - COMPLETED");
            
        } catch (SQLException e) {
            System.err.println("❌ PolicyDAO.createPolicyUpdateAuditRecords - ERROR: " + e.getMessage());
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
     * Method لإنشاء snapshot جديد في policy_audit عند الـ update
     */
    public void createPolicyUpdateAuditSnapshot(int policyId) throws SQLException {
        //system.out.println("🔍 PolicyDAO.createPolicyUpdateAuditSnapshot - Creating update snapshot for ID: " + policyId);
        String sql = """
            INSERT INTO policy_audit (
                ID, ParentID, isPublic, Status, Lifecycle_Status, Policy_Type, PrimaryName, refNumber, Description, 
                EffectiveDate, EndDate, Internal, URL, createDatetime, LastUpdateDatetime, DeletedDatetime, 
                CreatedBy_ID, LastUpdateUser_ID, Rev_Type
            )
            SELECT 
                ID, ParentID, isPublic, Status, Lifecycle_Status, Policy_Type, PrimaryName, refNumber, Description, 
                EffectiveDate, EndDate, Internal, URL, createDatetime, LastUpdateDatetime, DeletedDatetime, 
                CreatedBy_ID, LastUpdateUser_ID, 'Updated'
            FROM policy 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * حذف السياسة مع تسجيل audit records
     */
    public boolean deletePolicyWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE policy SET DeletedDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO policy_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Policy");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Policy");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في policy_audit
                String snapshotSql = """
                    INSERT INTO policy_audit (
                        ID, Parent_ID, Business_Area_ID, Is_Public, Classification, Status, Lifecycle_Status,
                        Policy_Type, RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                        DeletedDatetime, LastUpdate_UserID, RevType
                    )
                    SELECT 
                        ID, Parent_ID, Business_Area_ID, Is_Public, Classification, Status, Lifecycle_Status,
                        Policy_Type, RefNumber, PrimaryName, Description, CreateDatetime, LastUpdateDatetime,
                        DeletedDatetime, LastUpdate_UserID, 'Deleted'
                    FROM policy 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Policy deleted with audit for ID: " + id);
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
     * Link stakeholder to policy via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToPolicy(Connection conn, int policyId, int objectXPeopleId, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO policy_x_objectxpeople (Policy_ID, Object_X_IP, LastUpdate_UserID, CreateDatetime)
            VALUES (?, ?, ?, NOW())
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, policyId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, currentUserId);
            try {
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: PolicyID=" + policyId + ", Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }

    /**
     * Get the segment ID for a policy
     * @param policyId The policy ID
     * @return The segment ID or null if not assigned
     */
    private Integer getSegmentIdForPolicy(int policyId) throws SQLException {
        String sql = """
            SELECT sxr.Segment_ID
            FROM segment_x_resource sxr
            JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
            WHERE orr.Object_ID = ?
            AND sot.Type = 'Policy'
            AND sxr.Deleted_At IS NULL
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, policyId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Segment_ID");
                }
            }
        }
        return null; // Return null if not assigned - show "Not Specified"
    }

    /**
     * Get segment name by ID
     * @param segmentId The segment ID
     * @return The segment name or "Not Specified" if not found
     */
    private String getSegmentName(int segmentId) throws SQLException {
        String sql = "SELECT Name FROM segment WHERE ID = ? AND Deleted_At IS NULL";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        }
        return "Not Specified";
    }
}