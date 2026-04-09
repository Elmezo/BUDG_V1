package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegulationHierarchyDAO {
    
    public List<Map<String, Object>> getAllRegulations() throws SQLException {
        String sql = """
            SELECT 
                r.ID as id,
                r.Parent_ID as parentId,
                r.RefNumber as refNumber,
                r.primaryName as primaryName,
                r.ShortName as shortName,
                r.Description as description,
                r.LastUpdateDatetime as lastUpdateDatetime,
                rs.PrimaryName as regulationStatus,
                rc.PrimaryName as complianceLevel,
                rst.PrimaryName as regulationStage,
                GROUP_CONCAT(DISTINCT rt.PrimaryName SEPARATOR ', ') as regulatoryThemes
            FROM regulation r
            LEFT JOIN regulation_status rs ON r.RegulationStatus_ID = rs.ID
            LEFT JOIN regulation_compliance_level rc ON r.ComplianceLevel_ID = rc.ID
            LEFT JOIN regulation_stage rst ON r.RegulationStage_ID = rst.ID
            LEFT JOIN regulation_x_regulatorytheme rxrt ON r.ID = rxrt.Regulation_ID
            LEFT JOIN regulatorytheme rt ON rxrt.RegulatoryTheme_ID = rt.ID
            WHERE r.DeletedDatetime IS NULL
            GROUP BY r.ID, r.Parent_ID, r.RefNumber, r.primaryName, r.ShortName, r.Description, 
                     r.LastUpdateDatetime, rs.PrimaryName, rc.PrimaryName, rst.PrimaryName
            ORDER BY r.primaryName ASC
        """;
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("parentId", rs.getObject("parentId"));
                    row.put("refNumber", rs.getString("refNumber"));
                    row.put("primaryName", rs.getString("primaryName"));
                    row.put("shortName", rs.getString("shortName"));
                    row.put("description", rs.getString("description"));
                    row.put("lastUpdateDatetime", rs.getTimestamp("lastUpdateDatetime"));
                    row.put("regulationStatus", rs.getString("regulationStatus"));
                    row.put("complianceLevel", rs.getString("complianceLevel"));
                    row.put("regulationStage", rs.getString("regulationStage"));
                    row.put("regulatoryThemes", rs.getString("regulatoryThemes"));
                    results.add(row);
                }
        }
        
        return results;
    }
}
