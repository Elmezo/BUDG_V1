package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.PolicyType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PolicyTypeDAO {
    private static final String SELECT_ALL = "SELECT * FROM policy_type ORDER BY ID";

    public List<PolicyType> getAllPolicyTypes() throws SQLException {
        List<PolicyType> policyTypes = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                policyTypes.add(mapResultSetToPolicyType(rs));
            }
        }
        return policyTypes;
    }

    private PolicyType mapResultSetToPolicyType(ResultSet rs) throws SQLException {
        PolicyType policyType = new PolicyType();
        policyType.setId(rs.getInt("ID"));
        policyType.setPrimaryName(rs.getString("PrimaryName"));
        policyType.setDescription(rs.getString("Description"));
        
        Timestamp lastUpdateDatetime = rs.getTimestamp("LastUpdateDatetime");
        if (lastUpdateDatetime != null) {
            policyType.setLastUpdateDatetime(lastUpdateDatetime.toString());
        }
        
        policyType.setLastUpdateUserId(rs.getObject("LastUpdateUser_ID", Integer.class));
        
        return policyType;
    }
}
