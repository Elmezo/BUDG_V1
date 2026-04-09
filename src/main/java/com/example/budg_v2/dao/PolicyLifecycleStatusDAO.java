package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.PolicyLifecycleStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PolicyLifecycleStatusDAO {
    private static final String SELECT_ALL = "SELECT * FROM policy_lifecycle_status ORDER BY id";
    private static final String SELECT_BY_ID = "SELECT * FROM policy_lifecycle_status WHERE id = ?";
    private static final String SELECT_FOR_DROPDOWN = "SELECT id, PrimaryName FROM policy_lifecycle_status ORDER BY id";

    public List<PolicyLifecycleStatus> getAllPolicyLifecycleStatuses() throws SQLException {
        List<PolicyLifecycleStatus> statuses = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                statuses.add(mapResultSetToPolicyLifecycleStatus(rs));
            }
        }
        return statuses;
    }

    public PolicyLifecycleStatus getPolicyLifecycleStatusById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPolicyLifecycleStatus(rs);
                }
            }
        }
        return null;
    }

    public List<PolicyLifecycleStatus> getAllPolicyLifecycleStatusesForDropdown() throws SQLException {
        List<PolicyLifecycleStatus> statuses = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_FOR_DROPDOWN)) {

            while (rs.next()) {
                PolicyLifecycleStatus status = new PolicyLifecycleStatus();
                status.setId(rs.getInt("id"));
                status.setPrimaryName(rs.getString("PrimaryName"));
                statuses.add(status);
            }
        }
        return statuses;
    }

    private PolicyLifecycleStatus mapResultSetToPolicyLifecycleStatus(ResultSet rs) throws SQLException {
        PolicyLifecycleStatus status = new PolicyLifecycleStatus();
        status.setId(rs.getInt("id"));
        status.setPrimaryName(rs.getString("PrimaryName"));
        status.setDescription(rs.getString("Description"));
        
        Timestamp lastUpdateDatetime = rs.getTimestamp("LastUpdateDatetime");
        if (lastUpdateDatetime != null) {
            status.setLastUpdateDatetime(lastUpdateDatetime.toString());
        }
        
        status.setLastUpdateUserId(rs.getObject("LastUpdateUser_ID", Integer.class));
        
        return status;
    }
}
