package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LegalAdviceTypeDAO {
    
    public List<Map<String, Object>> getAll() throws SQLException {
        String sql = """
            SELECT 
                ID as id,
                PrimaryName as primaryName,
                Description as description
            FROM legal_advice_type
            ORDER BY PrimaryName ASC
        """;
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("primaryName", rs.getString("primaryName"));
                row.put("description", rs.getString("description"));
                results.add(row);
            }
        }
        
        return results;
    }
}
