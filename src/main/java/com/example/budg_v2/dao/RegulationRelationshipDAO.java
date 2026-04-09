package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegulationRelationshipDAO {
    
    /**
     * Get all relationships for a specific regulation (both as source and target)
     */
    public List<Map<String, Object>> getRelationshipsByRegulationId(int regulationId) throws SQLException {
        String sql = """
            SELECT 
                rxr.ID as relationshipId,
                rxr.SourceRegulationID as sourceRegulationId,
                rxr.TargetRegulationID as targetRegulationId,
                rxr.RelationType as relationTypeId,
                rxr.Description as relationshipDescription,
                
                -- Source regulation details
                source_reg.RefNumber as sourceRefNumber,
                source_reg.primaryName as sourceRegulationName,
                source_reg.ShortName as sourceShortName,
                
                -- Target regulation details  
                target_reg.RefNumber as targetRefNumber,
                target_reg.primaryName as targetRegulationName,
                target_reg.ShortName as targetShortName,
                
                -- Relationship type details
                rt.primaryName as relationTypeName,
                rt.Description as relationTypeDescription,
                rt.ReverseName as relationTypeReverseName,
                
                -- Determine direction relative to current regulation
                CASE 
                    WHEN rxr.SourceRegulationID = ? THEN 'outgoing'
                    WHEN rxr.TargetRegulationID = ? THEN 'incoming'
                    ELSE 'unknown'
                END as direction
                
            FROM regulation_x_regulation rxr
            LEFT JOIN regulation source_reg ON rxr.SourceRegulationID = source_reg.ID
            LEFT JOIN regulation target_reg ON rxr.TargetRegulationID = target_reg.ID
            LEFT JOIN regulation_x_regulation_relationtype rt ON rxr.RelationType = rt.ID
            WHERE (rxr.SourceRegulationID = ? OR rxr.TargetRegulationID = ?)
            AND (source_reg.DeletedDatetime IS NULL OR source_reg.DeletedDatetime = '1970-01-01 00:00:00')
            AND (target_reg.DeletedDatetime IS NULL OR target_reg.DeletedDatetime = '1970-01-01 00:00:00')
            ORDER BY rt.primaryName ASC, source_reg.primaryName ASC, target_reg.primaryName ASC
        """;
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Set parameters for the CASE statement and WHERE clause
            stmt.setInt(1, regulationId); // For CASE WHEN SourceRegulationID = ?
            stmt.setInt(2, regulationId); // For CASE WHEN TargetRegulationID = ?
            stmt.setInt(3, regulationId); // For WHERE SourceRegulationID = ?
            stmt.setInt(4, regulationId); // For WHERE TargetRegulationID = ?
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    
                    // Basic relationship info
                    row.put("relationshipId", rs.getInt("relationshipId"));
                    row.put("sourceRegulationId", rs.getInt("sourceRegulationId"));
                    row.put("targetRegulationId", rs.getInt("targetRegulationId"));
                    row.put("relationTypeId", rs.getInt("relationTypeId"));
                    row.put("relationshipDescription", rs.getString("relationshipDescription"));
                    
                    // Source regulation info
                    row.put("sourceRefNumber", rs.getString("sourceRefNumber"));
                    row.put("sourceRegulationName", rs.getString("sourceRegulationName"));
                    row.put("sourceShortName", rs.getString("sourceShortName"));
                    
                    // Target regulation info
                    row.put("targetRefNumber", rs.getString("targetRefNumber"));
                    row.put("targetRegulationName", rs.getString("targetRegulationName"));
                    row.put("targetShortName", rs.getString("targetShortName"));
                    
                    // Relationship type info
                    row.put("relationTypeName", rs.getString("relationTypeName"));
                    row.put("relationTypeDescription", rs.getString("relationTypeDescription"));
                    row.put("relationTypeReverseName", rs.getString("relationTypeReverseName"));
                    
                    // Direction info
                    row.put("direction", rs.getString("direction"));
                    
                    // Determine the "other" regulation (the one that's not the current regulation)
                    String direction = rs.getString("direction");
                    if ("outgoing".equals(direction)) {
                        // Current regulation is source, so target is the "related regulation"
                        row.put("relatedRegulationId", rs.getInt("targetRegulationId"));
                        row.put("relatedRegulationName", rs.getString("targetRegulationName"));
                        row.put("relatedRefNumber", rs.getString("targetRefNumber"));
                        row.put("relatedShortName", rs.getString("targetShortName"));
                        row.put("effectiveRelationTypeName", rs.getString("relationTypeName"));
                    } else if ("incoming".equals(direction)) {
                        // Current regulation is target, so source is the "related regulation"
                        row.put("relatedRegulationId", rs.getInt("sourceRegulationId"));
                        row.put("relatedRegulationName", rs.getString("sourceRegulationName"));
                        row.put("relatedRefNumber", rs.getString("sourceRefNumber"));
                        row.put("relatedShortName", rs.getString("sourceShortName"));
                        // Use reverse name if available, otherwise use primary name
                        String reverseName = rs.getString("relationTypeReverseName");
                        row.put("effectiveRelationTypeName", 
                            (reverseName != null && !reverseName.trim().isEmpty()) ? reverseName : rs.getString("relationTypeName"));
                    }
                    
                    results.add(row);
                }
            }
        }
        
        return results;
    }
}
