package com.example.budg_v2.service;

import com.example.budg_v2.dao.PolicyLifecycleStatusDAO;
import com.example.budg_v2.model.PolicyLifecycleStatus;

import java.sql.SQLException;
import java.util.List;

public class PolicyLifecycleStatusService {

    private final PolicyLifecycleStatusDAO policyLifecycleStatusDAO;

    public PolicyLifecycleStatusService() {
        this.policyLifecycleStatusDAO = new PolicyLifecycleStatusDAO();
    }

    public List<PolicyLifecycleStatus> getAllPolicyLifecycleStatuses() throws SQLException {
        return policyLifecycleStatusDAO.getAllPolicyLifecycleStatuses();
    }

    public PolicyLifecycleStatus getPolicyLifecycleStatusById(int id) throws SQLException {
        return policyLifecycleStatusDAO.getPolicyLifecycleStatusById(id);
    }

    public List<PolicyLifecycleStatus> getAllPolicyLifecycleStatusesForDropdown() throws SQLException {
        return policyLifecycleStatusDAO.getAllPolicyLifecycleStatusesForDropdown();
    }
}
