package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class RegulationXRegulatorXGeographyRelationTypeDAO {
    
    private static final String SELECT_ALL = 
        "SELECT ID, PrimaryName, ReverseName, Description " +
        "FROM regulation_x_regulator_x_geography_relationtype " +
        "ORDER BY PrimaryName";
    
    private static final String SELECT_BY_ID = 
        "SELECT ID, PrimaryName, ReverseName, Description " +
        "FROM regulation_x_regulator_x_geography_relationtype " +
        "WHERE ID = ?";

    public List<Map<String, Object>> getAllRelationTypes() throws SQLException {
        List<Map<String, Object>> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> relationType = new HashMap<>();
                relationType.put("id", rs.getInt("ID"));
                relationType.put("primaryName", rs.getString("PrimaryName"));
                relationType.put("reverseName", rs.getString("ReverseName"));
                relationType.put("description", rs.getString("Description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }

    public Map<String, Object> getById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> relationType = new HashMap<>();
                    relationType.put("id", rs.getInt("ID"));
                    relationType.put("primaryName", rs.getString("PrimaryName"));
                    relationType.put("reverseName", rs.getString("ReverseName"));
                    relationType.put("description", rs.getString("Description"));
                    return relationType;
                }
            }
        }
        
        return null;
    }
}
