package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProjectClassification;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProjectClassificationDAO {
    
    private static final String SELECT_ALL = "SELECT * FROM project_classification ORDER BY primaryname";

    public List<ProjectClassification> getAllProjectClassifications() throws SQLException {
        List<ProjectClassification> classifications = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                classifications.add(mapResultSetToProjectClassification(rs));
            }
        }
        
        return classifications;
    }

    private ProjectClassification mapResultSetToProjectClassification(ResultSet rs) throws SQLException {
        ProjectClassification classification = new ProjectClassification();
        
        classification.setId(rs.getInt("id"));
        classification.setParentId(rs.getInt("parent_id"));
        classification.setPrimaryName(rs.getString("primaryname"));
        classification.setDescription(rs.getString("description"));
        classification.setLastUpdateUserId(rs.getInt("lastupdate_userid"));
        classification.setLastUpdatedDateTime(rs.getString("lastupdatedatetime"));
        
        return classification;
    }
}
