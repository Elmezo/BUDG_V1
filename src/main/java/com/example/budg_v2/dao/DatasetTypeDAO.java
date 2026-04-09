package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class DatasetTypeDAO {

    public List<Map<String, Object>> listDatasetTypes() throws SQLException {
        String sql = "SELECT ID, PrimaryName FROM dataset_type ORDER BY ID";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("ID"));
                row.put("primaryName", rs.getString("PrimaryName"));
                results.add(row);
            }
            return results;
        }
    }
}


