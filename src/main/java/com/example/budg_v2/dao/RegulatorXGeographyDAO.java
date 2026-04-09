package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class RegulatorXGeographyDAO {

    public List<Map<String, Object>> getByRegulatorId(int regulatorId) throws SQLException {
        String sql = "SELECT rxg.ID, rxg.Regulator_ID, rxg.Geography_ID, rxg.Description, " +
                "g.PrimaryName AS geographyName " +
                "FROM regulator_x_geography rxg " +
                "LEFT JOIN geography g ON g.ID = rxg.Geography_ID " +
                "WHERE rxg.Regulator_ID = ? " +
                "ORDER BY rxg.ID";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulatorId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("regulatorId", rs.getInt("Regulator_ID"));
                    row.put("geographyId", rs.getInt("Geography_ID"));
                    row.put("description", rs.getString("Description"));
                    row.put("geographyName", rs.getString("geographyName"));
                    results.add(row);
                }
                return results;
            }
        }
    }

    public Map<String, Object> create(int regulatorId, int geographyId, String description, Integer userId) throws SQLException {
        String sql = "INSERT INTO regulator_x_geography (Regulator_ID, Geography_ID, Description, LastUpdateDatetime, LastUpdate_UserID) " +
                "VALUES (?, ?, ?, NULL, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, regulatorId);
            ps.setInt(2, geographyId);
            ps.setString(3, description);
            if (userId != null) {
                ps.setInt(4, userId);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            
            ps.executeUpdate();
            
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", id);
                    result.put("regulatorId", regulatorId);
                    result.put("geographyId", geographyId);
                    result.put("description", description);
                    result.put("userId", userId);
                    return result;
                }
            }
        }
        throw new SQLException("Failed to create regulator-geography relationship");
    }

    public boolean delete(int id) throws SQLException {
        //system.out.println("DAO: Attempting to delete regulator_x_geography with ID: " + id);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false); // Start transaction
            
            try {
                // First, delete dependent records from regulation_x_regulator_x_geography
                String deleteDependentSql = "DELETE FROM regulation_x_regulator_x_geography WHERE Regulator_X_Geography_ID = ?";
                //system.out.println("DAO: Deleting dependent records with SQL: " + deleteDependentSql);
                
                try (PreparedStatement dependentStmt = conn.prepareStatement(deleteDependentSql)) {
                    dependentStmt.setInt(1, id);
                    dependentStmt.executeUpdate();
                }
                
                // Then delete the main record
                String sql = "DELETE FROM regulator_x_geography WHERE ID = ?";
                //system.out.println("DAO: Deleting main record with SQL: " + sql);
                
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    int rowsAffected = ps.executeUpdate();
                    //system.out.println("DAO: Main record rows affected: " + rowsAffected);
                    
                    if (rowsAffected > 0) {
                        conn.commit(); // Commit transaction
                        //system.out.println("DAO: Transaction committed successfully");
                        return true;
                    } else {
                        conn.rollback(); // Rollback if no rows affected
                        //system.out.println("DAO: No rows affected, rolling back");
                        return false;
                    }
                }
                
            } catch (SQLException e) {
                conn.rollback(); // Rollback on error
                //system.out.println("DAO: Error occurred, rolling back transaction: " + e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Update an existing regulator_x_geography relationship
     */
    public boolean update(int id, int regulatorId, int geographyId, String description, Integer userId) throws SQLException {
        String sql = "UPDATE regulator_x_geography " +
                "SET Regulator_ID = ?, Geography_ID = ?, Description = ?, " +
                "LastUpdateDatetime = NOW(), LastUpdate_UserID = ? " +
                "WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulatorId);
            ps.setInt(2, geographyId);
            ps.setString(3, description);
            if (userId != null) {
                ps.setInt(4, userId);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.setInt(5, id);
            return ps.executeUpdate() > 0;
        }
    }
}
