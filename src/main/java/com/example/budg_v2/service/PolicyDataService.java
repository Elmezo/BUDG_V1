package com.example.budg_v2.service;

import com.example.budg_v2.dao.PolicyDataDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class PolicyDataService {
    private final PolicyDataDAO policyDataDAO;
    
    public PolicyDataService() {
        this.policyDataDAO = new PolicyDataDAO();
    }
    
    /**
     * Get datasets for a policy
     */
    public List<Map<String, Object>> getPolicyDatasets(int policyId) throws SQLException {
        return policyDataDAO.getPolicyDatasets(policyId);
    }
    
    /**
     * Get attributes for a policy
     * Includes attributes from policy_X_attribute and attributes from datasets in policy_X_dataset
     */
    public List<Map<String, Object>> getPolicyAttributes(int policyId) throws SQLException {
        return policyDataDAO.getPolicyAttributes(policyId);
    }
}

