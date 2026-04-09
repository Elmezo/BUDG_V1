package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class DatasetLifecycleDAO {

    public List<Map<String, Object>> listLifecycles() throws SQLException {
        // For system page we need generic `name`
        String sql = "SELECT ID, PrimaryName as name FROM dataset_lifecycle ORDER BY ID";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("name", rs.getString("name"));
                results.add(row);
            }
            return results;
        }
    }
}


