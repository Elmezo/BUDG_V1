package com.example.budg_v2.dao;

import com.example.budg_v2.model.RegulationXRegulationRelationType;
import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RegulationXRegulationRelationTypeDAO {
    
    private static final String SELECT_ALL = 
        "SELECT * FROM regulation_x_regulation_relationtype ORDER BY primaryName";
    
    private static final String SELECT_BY_ID = 
        "SELECT * FROM regulation_x_regulation_relationtype WHERE ID = ?";

    public List<RegulationXRegulationRelationType> getAllRelationTypes() {
        List<RegulationXRegulationRelationType> relationTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL)) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    relationTypes.add(new RegulationXRegulationRelationType(
                        rs.getInt("ID"),
                        rs.getString("primaryName"),
                        rs.getString("Description"),
                        rs.getString("ReverseName")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return relationTypes;
    }

    public RegulationXRegulationRelationType getById(int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new RegulationXRegulationRelationType(
                        rs.getInt("ID"),
                        rs.getString("primaryName"),
                        rs.getString("Description"),
                        rs.getString("ReverseName")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
}
