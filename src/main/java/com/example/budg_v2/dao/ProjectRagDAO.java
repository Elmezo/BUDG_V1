package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProjectRag;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProjectRagDAO {
    
    private static final String SELECT_ALL = "SELECT * FROM project_rag ORDER BY primaryname";

    public List<ProjectRag> getAllProjectRags() throws SQLException {
        List<ProjectRag> rags = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                rags.add(mapResultSetToProjectRag(rs));
            }
        }
        
        return rags;
    }

    private ProjectRag mapResultSetToProjectRag(ResultSet rs) throws SQLException {
        ProjectRag rag = new ProjectRag();
        
        rag.setId(rs.getInt("id"));
        rag.setPrimaryName(rs.getString("primaryname"));
        rag.setDescription(rs.getString("description"));
        
        return rag;
    }
}
