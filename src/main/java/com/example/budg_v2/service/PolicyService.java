package com.example.budg_v2.service;

import com.example.budg_v2.dao.PolicyDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Policy;
import com.example.budg_v2.model.PolicyType;
import com.example.budg_v2.model.PolicyLifecycleStatus;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class PolicyService {
    private PolicyDAO policyDAO;

    public PolicyService() {
        this.policyDAO = new PolicyDAO();
    }

    public List<Policy> getAllPolicies() throws SQLException {
        return policyDAO.getAllPolicies();
    }
    
    /**
     * Get all policies filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to.
     */
    public List<Policy> getAllPoliciesBySegmentAccess(int userId) throws SQLException {
        return policyDAO.getAllPoliciesBySegmentAccess(userId);
    }

    public List<Policy> getPoliciesForDropdown() throws SQLException {
        return policyDAO.getPoliciesForDropdown();
    }
    
    /**
     * Get policies for dropdown filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Parent selection only shows accessible objects.
     */
    public List<Policy> getPoliciesForDropdownBySegmentAccess(int userId) throws SQLException {
        return policyDAO.getPoliciesForDropdownBySegmentAccess(userId);
    }

    public Policy getPolicyById(int id) throws SQLException {
        return policyDAO.getPolicyById(id);
    }

    public List<PolicyType> getPolicyTypes() throws SQLException {
        return policyDAO.getPolicyTypes();
    }

    public List<PolicyLifecycleStatus> getPolicyLifecycleStatuses() throws SQLException {
        return policyDAO.getPolicyLifecycleStatuses();
    }

    public List<Policy> searchPolicies(String searchTerm) throws SQLException {
        return policyDAO.searchPolicies(searchTerm);
    }

    public int createPolicy(Policy policy, HttpServletRequest request) throws SQLException, IllegalArgumentException {
        //system.out.println("PolicyService createPolicy - Creating policy");
        //system.out.println("- PrimaryName: " + policy.getPrimaryName());
        //system.out.println("- Description: " + policy.getDescription());
        //system.out.println("- ParentId: " + policy.getParentId());
        //system.out.println("- PolicyType: " + policy.getPolicyType());
        //system.out.println("- Status: " + policy.getStatus());
        //system.out.println("- LifecycleStatus: " + policy.getLifecycleStatus());
        //system.out.println("- IsPublic: " + policy.getIsPublic());
        //system.out.println("- RefNumber: " + policy.getRefNumber());

        // Primary name uniqueness is enforced per segment in PolicyServlet.

        // Validate refnumber uniqueness
        if (policy.getRefNumber() != null && !policy.getRefNumber().trim().isEmpty()) {
            if (!policyDAO.isRefNumberUnique(policy.getRefNumber())) {
                throw new IllegalArgumentException("Reference Number already exists");
            }
        }

        int policyId = policyDAO.createPolicy(policy);
        
        // Create audit records for history tracking
        try {
            // Get current user name from request attributes
            String userName = getCurrentUserName(request);
            policyDAO.createPolicyAuditRecords(policyId, userName);
            policyDAO.createPolicyAuditRecord(policyId);
            
            // Create default stakeholder audit records if lastUpdateUserId is provided
            if (policy.getLastUpdateUserId() != null) {
                try {
                    String userFullName = getPersonFullName(policy.getLastUpdateUserId());
                    if (userFullName != null) {
                        // Create stakeholder audit records with default role (1 = Policy Owner)
                        policyDAO.createStakeholderAuditRecords(policyId, userName, userFullName, 1);
                        //system.out.println("✅ Default stakeholder audit records created for policy ID: " + policyId);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating default stakeholder audit records: " + e.getMessage());
                    // Continue - don't fail the main operation
                }
            }
        } catch (SQLException e) {
            // Log the error but don't fail the main operation
            System.err.println("Failed to create history audit records: " + e.getMessage());
        }
        
        return policyId;
    }

    public boolean updatePolicy(Policy policy, HttpServletRequest request) throws SQLException, IllegalArgumentException {
        // Primary name uniqueness is enforced per segment in PolicyServlet.

        // Validate refnumber uniqueness for update (excluding current policy)
        if (policy.getRefNumber() != null && !policy.getRefNumber().trim().isEmpty()) {
            if (!policyDAO.isRefNumberUniqueForUpdate(policy.getRefNumber(), policy.getId())) {
                throw new IllegalArgumentException("Reference Number already exists");
            }
        }

        String userName = getCurrentUserName(request);
        return policyDAO.updatePolicy(policy, userName);
    }

    public boolean deletePolicy(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return policyDAO.deletePolicyWithAudit(id, userName);
    }

    // Policy hierarchy method
    public List<java.util.Map<String, Object>> getPolicyHierarchy(int policyId) throws SQLException {
        return policyDAO.getPolicyHierarchy(policyId);
    }

    // Policy relationships methods
    public List<java.util.Map<String, Object>> getPolicyRelationshipsBySourceId(int sourceId) throws SQLException {
        return policyDAO.getPolicyRelationshipsBySourceId(sourceId);
    }

    public boolean deletePolicyRelationship(int relationshipId) throws SQLException {
        return policyDAO.deletePolicyRelationship(relationshipId);
    }

    // Policy relationship CRUD methods
    public boolean createPolicyRelationship(int sourceId, int targetId, int relationType, String description, Integer userId) throws SQLException {
        return policyDAO.createPolicyRelationship(sourceId, targetId, relationType, description, userId);
    }

    public boolean updatePolicyRelationship(int relationshipId, int relationType, int targetPolicyId, String description, Integer userId) throws SQLException {
        return policyDAO.updatePolicyRelationship(relationshipId, relationType, targetPolicyId, description, userId);
    }

    public List<java.util.Map<String, Object>> getPolicyRelationTypes() throws SQLException {
        return policyDAO.getPolicyRelationTypes();
    }

    // Helper method to get current user name from request
    private String getCurrentUserName(HttpServletRequest request) {
        Object userNameObj = request.getAttribute("userName");
        if (userNameObj != null) {
            return userNameObj.toString();
        }
        return "System"; // Fallback if no user name found
    }

    // Helper method to get person full name
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
}
