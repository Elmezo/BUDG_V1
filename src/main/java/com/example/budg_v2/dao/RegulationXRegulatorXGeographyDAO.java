package com.example.budg_v2.dao;

import com.example.budg_v2.model.RegulationXRegulatorXGeography;
import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class RegulationXRegulatorXGeographyDAO {
    
    private static final String SELECT_BY_REGULATION_ID = 
        "SELECT rxrxg.*, " +
        "       rxr.RegulationID, " +
        "       r.PrimaryName as regulatorName, " +
        "       g.PrimaryName as geographyName, " +
        "       rt.PrimaryName as relationTypeName " +
        "FROM regulation_x_regulator_x_geography rxrxg " +
        "LEFT JOIN regulation_x_regulator rxr ON rxrxg.Regulation_X_Regulator_ID = rxr.ID " +
        "LEFT JOIN regulator_x_geography rxg ON rxrxg.Regulator_X_Geography_ID = rxg.ID " +
        "LEFT JOIN regulator r ON rxg.Regulator_ID = r.ID " +
        "LEFT JOIN geography g ON rxg.Geography_ID = g.ID " +
        "LEFT JOIN regulation_x_regulator_x_geography_relationtype rt ON rxrxg.RelationType = rt.ID " +
        "WHERE rxr.RegulationID = ? " +
        "ORDER BY rxrxg.ID";
    
    private static final String INSERT_REGULATION_X_REGULATOR_X_GEOGRAPHY = 
        "INSERT INTO regulation_x_regulator_x_geography (Regulation_X_Regulator_ID, Regulator_X_Geography_ID, RelationType, Description, LastUpdateDatetime, LastUpdate_UserID) " +
        "VALUES (?, ?, ?, ?, NULL, ?)";
    
    private static final String UPDATE_REGULATION_X_REGULATOR_X_GEOGRAPHY = 
        "UPDATE regulation_x_regulator_x_geography SET Regulation_X_Regulator_ID = ?, Regulator_X_Geography_ID = ?, RelationType = ?, Description = ?, LastUpdateDatetime = NOW(), LastUpdate_UserID = ? WHERE ID = ?";
    
    private static final String DELETE_REGULATION_X_REGULATOR_X_GEOGRAPHY = 
        "DELETE FROM regulation_x_regulator_x_geography WHERE ID = ?";
    
    private static final String SELECT_BY_ID = 
        "SELECT * FROM regulation_x_regulator_x_geography WHERE ID = ?";

    public List<Map<String, Object>> getGeographiesByRegulationId(int regulationId) {
        List<Map<String, Object>> geographies = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_REGULATION_ID)) {
            
            stmt.setInt(1, regulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> geography = new HashMap<>();
                    geography.put("id", rs.getInt("ID"));
                    geography.put("regulationId", rs.getInt("RegulationID"));
                    geography.put("regulationXRegulatorId", rs.getInt("Regulation_X_Regulator_ID"));
                    geography.put("regulatorXGeographyId", rs.getInt("Regulator_X_Geography_ID"));
                    geography.put("relationType", rs.getInt("RelationType"));
                    geography.put("description", rs.getString("Description"));
                    geography.put("lastUpdateDatetime", rs.getString("LastUpdateDatetime"));
                    geography.put("lastUpdateUserId", rs.getInt("LastUpdate_UserID"));
                    geography.put("regulatorName", rs.getString("regulatorName"));
                    geography.put("geographyName", rs.getString("geographyName"));
                    geography.put("relationTypeName", rs.getString("relationTypeName"));
                    geographies.add(geography);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return geographies;
    }

    public int createRegulationXRegulatorXGeography(RegulationXRegulatorXGeography geography) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_REGULATION_X_REGULATOR_X_GEOGRAPHY, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, geography.getRegulationXRegulatorId());
            stmt.setInt(2, geography.getRegulatorXGeographyId());
            stmt.setInt(3, geography.getRelationType());
            stmt.setString(4, geography.getDescription());
            stmt.setInt(5, geography.getLastUpdateUserId() != null ? geography.getLastUpdateUserId() : 1);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return -1;
    }

    public boolean updateRegulationXRegulatorXGeography(RegulationXRegulatorXGeography geography) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_REGULATION_X_REGULATOR_X_GEOGRAPHY)) {
            
            stmt.setInt(1, geography.getRegulationXRegulatorId());
            stmt.setInt(2, geography.getRegulatorXGeographyId());
            stmt.setInt(3, geography.getRelationType());
            stmt.setString(4, geography.getDescription());
            stmt.setInt(5, geography.getLastUpdateUserId() != null ? geography.getLastUpdateUserId() : 1);
            stmt.setInt(6, geography.getId());
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return false;
    }

    public boolean deleteRegulationXRegulatorXGeography(int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_REGULATION_X_REGULATOR_X_GEOGRAPHY)) {
            
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return false;
    }

    public RegulationXRegulatorXGeography getById(int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new RegulationXRegulatorXGeography(
                        rs.getInt("ID"),
                        rs.getInt("Regulation_X_Regulator_ID"),
                        rs.getInt("Regulator_X_Geography_ID"),
                        rs.getInt("RelationType"),
                        rs.getString("Description"),
                        rs.getString("LastUpdateDatetime"),
                        rs.getInt("LastUpdate_UserID")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
}
