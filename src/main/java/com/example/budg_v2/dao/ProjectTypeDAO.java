package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProjectType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProjectTypeDAO {
    
    private static final String SELECT_ALL = "SELECT * FROM project_type ORDER BY primaryname";

    public List<ProjectType> getAllProjectTypes() throws SQLException {
        List<ProjectType> projectTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                projectTypes.add(mapResultSetToProjectType(rs));
            }
        }
        
        return projectTypes;
    }

    private ProjectType mapResultSetToProjectType(ResultSet rs) throws SQLException {
        ProjectType projectType = new ProjectType();
        
        projectType.setId(rs.getInt("id"));
        projectType.setPrimaryName(rs.getString("primaryname"));
        projectType.setDescription(rs.getString("description"));
        projectType.setLastUpdateUserId(rs.getInt("lastupdateuser_id"));
        projectType.setLastUpdatedDateTime(rs.getString("lastupdatedatetime"));
        
        return projectType;
    }
}
