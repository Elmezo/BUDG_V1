package com.example.budg_v2.dao;

import com.example.budg_v2.model.CRRelationship;
import com.example.budg_v2.model.CRRelationshipType;
import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CRRelationshipDAO {
    
    // Get all relationship types
    public List<CRRelationshipType> getAllRelationshipTypes() throws SQLException {
        List<CRRelationshipType> types = new ArrayList<>();
        String sql = "SELECT ID, Name, Description, Status FROM cr_relationship_type WHERE Status = 'Enabled' ORDER BY Name";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                CRRelationshipType type = new CRRelationshipType();
                type.setId(rs.getInt("ID"));
                type.setName(rs.getString("Name"));
                type.setDescription(rs.getString("Description"));
                type.setStatus(rs.getString("Status"));
                types.add(type);
            }
        }
        
        return types;
    }
    
    // Get relationships for a change request (bidirectional - includes both source and target relationships)
    public List<CRRelationship> getRelationshipsBySourceId(Integer sourceId) throws SQLException {
        List<CRRelationship> relationships = new ArrayList<>();
        // Use Sets to track unique relationships by ID and by combination to prevent duplicates
        java.util.Set<Integer> seenIds = new java.util.HashSet<>();
        java.util.Set<String> seenCombinations = new java.util.HashSet<>();
        
        // Get relationships where this CR is the source OR target (bidirectional display)
        // When this CR is the source, show target. When this CR is the target, show source.
        String sql = """
            SELECT 
                cr.ID,
                ? as Source_ID,
                CASE 
                    WHEN cr.Source_ID = ? THEN cr.Target_ID
                    ELSE cr.Source_ID
                END as Target_ID,
                cr.CR_Relationship_Type_ID,
                cr.Created_At,
                cr.Updated_At,
                crt.Name as relationship_type_name,
                CASE 
                    WHEN cr.Source_ID = ? THEN target_cr.PrimaryName
                    ELSE source_cr.PrimaryName
                END as target_title,
                CASE 
                    WHEN cr.Source_ID = ? THEN target_cr.Reference
                    ELSE source_cr.Reference
                END as target_reference
            FROM cr_relationship cr
            JOIN cr_relationship_type crt ON cr.CR_Relationship_Type_ID = crt.ID
            JOIN changerequest target_cr ON cr.Target_ID = target_cr.ID
            JOIN changerequest source_cr ON cr.Source_ID = source_cr.ID
            WHERE cr.Source_ID = ? OR cr.Target_ID = ?
            ORDER BY cr.Created_At DESC
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, sourceId);
            stmt.setInt(2, sourceId);
            stmt.setInt(3, sourceId);
            stmt.setInt(4, sourceId);
            stmt.setInt(5, sourceId);
            stmt.setInt(6, sourceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Integer relationshipId = rs.getInt("ID");
                    Integer targetId = rs.getInt("Target_ID");
                    Integer typeId = rs.getInt("CR_Relationship_Type_ID");
                    
                    // Skip if we've already seen this relationship ID
                    if (seenIds.contains(relationshipId)) {
                        continue;
                    }
                    
                    // Also check for duplicate based on sourceId + targetId + typeId combination
                    // This handles cases where there might be duplicate records with different IDs
                    String combinationKey = sourceId + "_" + targetId + "_" + typeId;
                    if (seenCombinations.contains(combinationKey)) {
                        // Duplicate combination found - skip this relationship
                        continue;
                    }
                    
                    seenIds.add(relationshipId);
                    seenCombinations.add(combinationKey);
                    
                    CRRelationship relationship = new CRRelationship();
                    relationship.setId(relationshipId);
                    relationship.setSourceId(rs.getInt("Source_ID")); // Always the current CR
                    relationship.setTargetId(targetId); // The other CR
                    relationship.setCrRelationshipTypeId(typeId);
                    relationship.setCreatedAt(rs.getTimestamp("Created_At").toLocalDateTime());
                    relationship.setUpdatedAt(rs.getTimestamp("Updated_At").toLocalDateTime());
                    relationship.setRelationshipTypeName(rs.getString("relationship_type_name"));
                    relationship.setTargetChangeRequestTitle(rs.getString("target_title"));
                    relationship.setTargetChangeRequestReference(rs.getString("target_reference"));
                    relationships.add(relationship);
                }
            }
        }
        
        return relationships;
    }
    
    // Create a new relationship
    public CRRelationship createRelationship(CRRelationship relationship) throws SQLException {
        String sql = """
            INSERT INTO cr_relationship (Source_ID, Target_ID, CR_Relationship_Type_ID, Last_UserChange)
            VALUES (?, ?, ?, ?)
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, relationship.getSourceId());
            stmt.setInt(2, relationship.getTargetId());
            stmt.setInt(3, relationship.getCrRelationshipTypeId());
            stmt.setObject(4, relationship.getLastUserChange(), Types.INTEGER);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating relationship failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    relationship.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating relationship failed, no ID obtained.");
                }
            }
        }
        
        return relationship;
    }
    
    // Update a relationship
    public boolean updateRelationship(CRRelationship relationship) throws SQLException {
        String sql = """
            UPDATE cr_relationship 
            SET Target_ID = ?, 
                CR_Relationship_Type_ID = ?, 
                Last_UserChange = ?
            WHERE ID = ?
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, relationship.getTargetId());
            stmt.setInt(2, relationship.getCrRelationshipTypeId());
            stmt.setObject(3, relationship.getLastUserChange(), Types.INTEGER);
            stmt.setInt(4, relationship.getId());
            
            return stmt.executeUpdate() > 0;
        }
    }
    
    // Delete a relationship (and its reverse if bidirectional)
    public boolean deleteRelationship(Integer id) throws SQLException {
        // First, get the relationship to find its reverse
        String getSql = "SELECT Source_ID, Target_ID, CR_Relationship_Type_ID FROM cr_relationship WHERE ID = ?";
        Integer sourceId = null;
        Integer targetId = null;
        Integer relationshipTypeId = null;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement getStmt = conn.prepareStatement(getSql)) {
            
            getStmt.setInt(1, id);
            try (ResultSet rs = getStmt.executeQuery()) {
                if (rs.next()) {
                    sourceId = rs.getInt("Source_ID");
                    targetId = rs.getInt("Target_ID");
                    relationshipTypeId = rs.getInt("CR_Relationship_Type_ID");
                } else {
                    return false; // Relationship not found
                }
            }
        }
        
        // Delete the original relationship
        String deleteSql = "DELETE FROM cr_relationship WHERE ID = ?";
        boolean deleted = false;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
            
            deleteStmt.setInt(1, id);
            deleted = deleteStmt.executeUpdate() > 0;
        }
        
        // Also delete the reverse relationship if it exists (bidirectional cleanup)
        if (deleted && sourceId != null && targetId != null && relationshipTypeId != null) {
            String deleteReverseSql = "DELETE FROM cr_relationship WHERE Source_ID = ? AND Target_ID = ? AND CR_Relationship_Type_ID = ? AND ID != ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement deleteReverseStmt = conn.prepareStatement(deleteReverseSql)) {
                
                deleteReverseStmt.setInt(1, targetId);
                deleteReverseStmt.setInt(2, sourceId);
                deleteReverseStmt.setInt(3, relationshipTypeId);
                deleteReverseStmt.setInt(4, id);
                deleteReverseStmt.executeUpdate(); // Don't check result - reverse might not exist
            }
        }
        
        return deleted;
    }
    
    // Get change requests by reference (for filtering)
    public List<CRRelationship> getChangeRequestsByReference(String objectType) throws SQLException {
        List<CRRelationship> changeRequests = new ArrayList<>();
        String sql = """
            SELECT ID, PrimaryName, Reference
            FROM changerequest 
            WHERE Reference LIKE ?
            ORDER BY PrimaryName
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Filter by object type pattern (e.g., "Dataset %" to find "Dataset 1", "Dataset 2", etc.)
            stmt.setString(1, objectType + " %");
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CRRelationship cr = new CRRelationship();
                    cr.setTargetId(rs.getInt("ID"));
                    cr.setTargetChangeRequestTitle(rs.getString("PrimaryName"));
                    cr.setTargetChangeRequestReference(rs.getString("Reference"));
                    changeRequests.add(cr);
                }
            }
        }
        
        return changeRequests;
    }
    
    // Check if relationship already exists
    public boolean relationshipExists(Integer sourceId, Integer targetId, Integer relationshipTypeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM cr_relationship WHERE Source_ID = ? AND Target_ID = ? AND CR_Relationship_Type_ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, sourceId);
            stmt.setInt(2, targetId);
            stmt.setInt(3, relationshipTypeId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }
}
