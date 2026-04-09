package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegulationGeographyDAO {
    
    public List<Map<String, Object>> getGeographiesByRegulationId(int regulationId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                rxrxg.ID as relationId,
                g.ID as geographyId,
                g.PrimaryName as geographyName,
                g.Description as geographyDescription,
                r.ID as regulatorId,
                r.PrimaryName as regulatorName,
                r.ShortName as regulatorShortName,
                rxrxg.Description as relationDescription,
                rxrxg.Regulation_X_Regulator_ID as regulationXRegulatorId,
                rxrxg.Regulator_X_Geography_ID as regulatorXGeographyId
            FROM regulation_x_regulator rxr
            INNER JOIN regulation_x_regulator_x_geography rxrxg ON rxr.ID = rxrxg.Regulation_X_Regulator_ID
            INNER JOIN regulator_x_geography rxg ON rxrxg.Regulator_X_Geography_ID = rxg.ID
            INNER JOIN geography g ON rxg.Geography_ID = g.ID
            INNER JOIN regulator r ON rxg.Regulator_ID = r.ID
            WHERE rxr.RegulationID = ?
            ORDER BY g.PrimaryName ASC, r.PrimaryName ASC
        """;
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, regulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("relationId", rs.getInt("relationId")); // ID from regulation_x_regulator_x_geography
                    row.put("geographyId", rs.getInt("geographyId"));
                    row.put("geographyName", rs.getString("geographyName"));
                    row.put("geographyDescription", rs.getString("geographyDescription"));
                    row.put("regulatorId", rs.getInt("regulatorId"));
                    row.put("regulatorName", rs.getString("regulatorName"));
                    row.put("regulatorShortName", rs.getString("regulatorShortName"));
                    row.put("relationDescription", rs.getString("relationDescription"));
                    row.put("regulationXRegulatorId", rs.getInt("regulationXRegulatorId"));
                    row.put("regulatorXGeographyId", rs.getInt("regulatorXGeographyId"));
                    results.add(row);
                }
            }
        }
        
        return results;
    }
    
    /**
     * Get geographies for a regulation including inherited geographies from parent regulations
     */
    public List<Map<String, Object>> getGeographiesWithInheritance(int regulationId) throws SQLException {
        String sql = """
            WITH RECURSIVE regulation_hierarchy AS (
                -- Base case: current regulation
                SELECT ID, Parent_ID, primaryName, 0 as level
                FROM regulation 
                WHERE ID = ? AND DeletedDatetime IS NULL
                
                UNION ALL
                
                -- Recursive case: parent regulations
                SELECT r.ID, r.Parent_ID, r.primaryName, rh.level + 1
                FROM regulation r
                INNER JOIN regulation_hierarchy rh ON r.ID = rh.Parent_ID
                WHERE rh.level < 10 AND r.DeletedDatetime IS NULL  -- Prevent infinite recursion
            )
            SELECT DISTINCT
                rxrxg.ID as relationId,
                g.ID as geographyId,
                g.PrimaryName as geographyName,
                g.Description as geographyDescription,
                r.ID as regulatorId,
                r.PrimaryName as regulatorName,
                r.ShortName as regulatorShortName,
                rxrxg.Description as relationDescription,
                rxrxg.Regulation_X_Regulator_ID as regulationXRegulatorId,
                rxrxg.Regulator_X_Geography_ID as regulatorXGeographyId,
                rh.level as inheritanceLevel,
                rh.primaryName as sourceRegulationName
            FROM regulation_hierarchy rh
            INNER JOIN regulation_x_regulator rxr ON rh.ID = rxr.RegulationID
            INNER JOIN regulation_x_regulator_x_geography rxrxg ON rxr.ID = rxrxg.Regulation_X_Regulator_ID
            INNER JOIN regulator_x_geography rxg ON rxrxg.Regulator_X_Geography_ID = rxg.ID
            INNER JOIN geography g ON rxg.Geography_ID = g.ID
            INNER JOIN regulator r ON rxg.Regulator_ID = r.ID
            ORDER BY rh.level ASC, g.PrimaryName ASC, r.PrimaryName ASC
        """;
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, regulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("relationId", rs.getInt("relationId")); // ID from regulation_x_regulator_x_geography
                    row.put("geographyId", rs.getInt("geographyId"));
                    row.put("geographyName", rs.getString("geographyName"));
                    row.put("geographyDescription", rs.getString("geographyDescription"));
                    row.put("regulatorId", rs.getInt("regulatorId"));
                    row.put("regulatorName", rs.getString("regulatorName"));
                    row.put("regulatorShortName", rs.getString("regulatorShortName"));
                    row.put("relationDescription", rs.getString("relationDescription"));
                    row.put("regulationXRegulatorId", rs.getInt("regulationXRegulatorId"));
                    row.put("regulatorXGeographyId", rs.getInt("regulatorXGeographyId"));
                    row.put("inheritanceLevel", rs.getInt("inheritanceLevel"));
                    row.put("sourceRegulationName", rs.getString("sourceRegulationName"));
                    results.add(row);
                }
            }
        }
        
        return results;
    }
}