package com.example.budg_v2.service;

import com.example.budg_v2.dao.PolicyTypeDAO;
import com.example.budg_v2.model.PolicyType;

import java.sql.SQLException;
import java.util.List;

public class PolicyTypeService {
    private PolicyTypeDAO policyTypeDAO;

    public PolicyTypeService() {
        this.policyTypeDAO = new PolicyTypeDAO();
    }

    public List<PolicyType> getAllPolicyTypes() throws SQLException {
        return policyTypeDAO.getAllPolicyTypes();
    }
}
