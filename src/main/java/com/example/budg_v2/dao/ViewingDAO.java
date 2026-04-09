package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Viewing;

import java.sql.*;
import java.util.*;

public class ViewingDAO {

    // Updated column names from EDITOR to match database schema
    private static final String SELECT_ALL = "SELECT * FROM viewing ORDER BY Name";
    private static final String SELECT_BY_ID = "SELECT * FROM viewing WHERE id = ?";
    private static final String SEARCH = "SELECT * FROM viewing WHERE Name LIKE ? OR Description LIKE ? ORDER BY Name";
    private static final String INSERT = "INSERT INTO viewing (Name, Description, Last_Updated_UserID, Last_updated_datetime) VALUES (?, ?, ?, NULL)";
    private static final String UPDATE = "UPDATE viewing SET Name = ?, Description = ?, Last_Updated_UserID = ?, Last_updated_datetime = NOW() WHERE id = ?";
    private static final String DELETE = "UPDATE viewing SET deleteddatetime = NOW(), Last_Updated_UserID = ? WHERE id = ?";

    public List<Map<String, Object>> listViewing() throws SQLException {
        String sql = "SELECT id, Name as name FROM viewing ORDER BY id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("name", rs.getString("name"));
                results.add(row);
            }
            return results;
        }
    }

    public List<Viewing> getAllViewings() throws SQLException {
        List<Viewing> viewings = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                viewings.add(mapResultSetToViewing(rs));
            }
        }
        
        return viewings;
    }

    public List<Viewing> getAllViewingsForDropdown() throws SQLException {
        return getAllViewings(); // Same as getAllViewings for now
    }

    public Viewing getViewingById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {
            
            pstmt.setInt(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToViewing(rs);
                }
            }
        }
        
        return null;
    }

    public List<Viewing> searchViewings(String searchQuery) throws SQLException {
        List<Viewing> viewings = new ArrayList<>();
        String searchPattern = "%" + searchQuery + "%";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {
            
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    viewings.add(mapResultSetToViewing(rs));
                }
            }
        }
        
        return viewings;
    }

    public Viewing createViewing(Viewing viewing) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, viewing.getName());
            pstmt.setString(2, viewing.getDescription());
            pstmt.setInt(3, viewing.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating viewing failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    viewing.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating viewing failed, no ID obtained.");
                }
            }

            return viewing;
        }
    }

    public boolean updateViewing(Viewing viewing) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, viewing.getName());
            pstmt.setString(2, viewing.getDescription());
            pstmt.setInt(3, viewing.getLastUpdateUserId());
            pstmt.setInt(4, viewing.getId());

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public boolean deleteViewing(int id, Integer lastUpdateUserId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE)) {

            pstmt.setInt(1, lastUpdateUserId);
            pstmt.setInt(2, id);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    private Viewing mapResultSetToViewing(ResultSet rs) throws SQLException {
        Viewing viewing = new Viewing();
        
        viewing.setId(rs.getInt("id"));
        // Updated column names from EDITOR to match database schema
        viewing.setName(rs.getString("Name"));
        viewing.setDescription(rs.getString("Description"));
        viewing.setLastUpdateUserId(rs.getInt("Last_Updated_UserID"));
        viewing.setLastUpdateDateTime(rs.getString("Last_updated_datetime"));
        
        return viewing;
    }
}


