package com.example.budg_v2.dao;

import com.example.budg_v2.model.RegulationXRegulation;
import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class RegulationXRegulationDAO {
    
    private static final String SELECT_BY_SOURCE_REGULATION_ID = 
        "SELECT r.*, rt.primaryName as relationTypeName, " +
        "t.primaryName as targetRegulationName, t.refNumber as targetRefNumber " +
        "FROM regulation_x_regulation r " +
        "LEFT JOIN regulation_x_regulation_relationtype rt ON r.RelationType = rt.ID " +
        "LEFT JOIN regulation t ON r.TargetRegulationID = t.ID " +
        "WHERE r.SourceRegulationID = ? " +
        "ORDER BY r.ID";
    
    private static final String SELECT_BY_TARGET_REGULATION_ID = 
        "SELECT r.*, rt.primaryName as relationTypeName, " +
        "s.primaryName as sourceRegulationName, s.refNumber as sourceRefNumber " +
        "FROM regulation_x_regulation r " +
        "LEFT JOIN regulation_x_regulation_relationtype rt ON r.RelationType = rt.ID " +
        "LEFT JOIN regulation s ON r.SourceRegulationID = s.ID " +
        "WHERE r.TargetRegulationID = ? " +
        "ORDER BY r.ID";
    
    private static final String INSERT_REGULATION_X_REGULATION = 
        "INSERT INTO regulation_x_regulation (SourceRegulationID, TargetRegulationID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) " +
        "VALUES (?, ?, ?, ?, NOW(), NULL, ?)";
    
    private static final String UPDATE_REGULATION_X_REGULATION = 
        "UPDATE regulation_x_regulation SET TargetRegulationID = ?, RelationType = ?, Description = ?, LastUpdateDatetime = NOW(), LastUpdate_UserID = ? WHERE ID = ?";
    
    private static final String DELETE_REGULATION_X_REGULATION = 
        "DELETE FROM regulation_x_regulation WHERE ID = ?";
    
    private static final String SELECT_BY_ID = 
        "SELECT * FROM regulation_x_regulation WHERE ID = ?";

    public List<Map<String, Object>> getRelationsBySourceRegulationId(int sourceRegulationId) {
        List<Map<String, Object>> relations = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_SOURCE_REGULATION_ID)) {
            
            stmt.setInt(1, sourceRegulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relation = new HashMap<>();
                    relation.put("id", rs.getInt("ID"));
                    relation.put("sourceRegulationId", rs.getInt("SourceRegulationID"));
                    relation.put("targetRegulationId", rs.getInt("TargetRegulationID"));
                    relation.put("relationType", rs.getInt("RelationType"));
                    relation.put("description", rs.getString("Description"));
                    relation.put("createDatetime", rs.getString("CreateDatetime"));
                    relation.put("lastUpdateDatetime", rs.getString("LastUpdateDatetime"));
                    relation.put("lastUpdateUserId", rs.getInt("LastUpdate_UserID"));
                    relation.put("relationTypeName", rs.getString("relationTypeName"));
                    relation.put("targetRegulationName", rs.getString("targetRegulationName"));
                    relation.put("targetRefNumber", rs.getString("targetRefNumber"));
                    relations.add(relation);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return relations;
    }

    public List<Map<String, Object>> getRelationsByTargetRegulationId(int targetRegulationId) {
        List<Map<String, Object>> relations = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TARGET_REGULATION_ID)) {
            
            stmt.setInt(1, targetRegulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> relation = new HashMap<>();
                    relation.put("id", rs.getInt("ID"));
                    relation.put("sourceRegulationId", rs.getInt("SourceRegulationID"));
                    relation.put("targetRegulationId", rs.getInt("TargetRegulationID"));
                    relation.put("relationType", rs.getInt("RelationType"));
                    relation.put("description", rs.getString("Description"));
                    relation.put("createDatetime", rs.getString("CreateDatetime"));
                    relation.put("lastUpdateDatetime", rs.getString("LastUpdateDatetime"));
                    relation.put("lastUpdateUserId", rs.getInt("LastUpdate_UserID"));
                    relation.put("relationTypeName", rs.getString("relationTypeName"));
                    relation.put("sourceRegulationName", rs.getString("sourceRegulationName"));
                    relation.put("sourceRefNumber", rs.getString("sourceRefNumber"));
                    relations.add(relation);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return relations;
    }

    public int createRegulationXRegulation(RegulationXRegulation relation) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_REGULATION_X_REGULATION, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, relation.getSourceRegulationId());
            stmt.setInt(2, relation.getTargetRegulationId());
            stmt.setInt(3, relation.getRelationType());
            stmt.setString(4, relation.getDescription());
            stmt.setInt(5, relation.getLastUpdateUserId() != null ? relation.getLastUpdateUserId() : 1);
            
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

    public boolean updateRegulationXRegulation(RegulationXRegulation relation) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_REGULATION_X_REGULATION)) {
            
            stmt.setInt(1, relation.getTargetRegulationId());
            stmt.setInt(2, relation.getRelationType());
            stmt.setString(3, relation.getDescription());
            stmt.setInt(4, relation.getLastUpdateUserId() != null ? relation.getLastUpdateUserId() : 1);
            stmt.setInt(5, relation.getId());
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return false;
    }

    public boolean deleteRegulationXRegulation(int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_REGULATION_X_REGULATION)) {
            
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return false;
    }

    public RegulationXRegulation getById(int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new RegulationXRegulation(
                        rs.getInt("ID"),
                        rs.getInt("SourceRegulationID"),
                        rs.getInt("TargetRegulationID"),
                        rs.getInt("RelationType"),
                        rs.getString("Description"),
                        rs.getString("CreateDatetime"),
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
