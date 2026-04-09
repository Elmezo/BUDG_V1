package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProjectLifecycle;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProjectLifecycleDAO {
    
    private static final String SELECT_ALL = "SELECT * FROM project_lifecycle ORDER BY primaryname";

    public List<ProjectLifecycle> getAllProjectLifecycles() throws SQLException {
        List<ProjectLifecycle> lifecycles = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                lifecycles.add(mapResultSetToProjectLifecycle(rs));
            }
        }
        
        return lifecycles;
    }

    private ProjectLifecycle mapResultSetToProjectLifecycle(ResultSet rs) throws SQLException {
        ProjectLifecycle lifecycle = new ProjectLifecycle();
        
        lifecycle.setId(rs.getInt("id"));
        lifecycle.setPrimaryName(rs.getString("primaryname"));
        lifecycle.setDescription(rs.getString("description"));
        lifecycle.setLastUpdateUserId(rs.getInt("lastupdate_userid"));
        lifecycle.setLastUpdatedDateTime(rs.getString("lastupdatedatetime"));
        
        return lifecycle;
    }
}
