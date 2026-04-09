package com.example.budg_v2.dao;

import com.example.budg_v2.model.ProductLifecycle;
import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductLifecycleDAO {
    
    private static final String SELECT_ALL = "SELECT * FROM product_lifecycle ORDER BY primaryname";

    public List<ProductLifecycle> getAllProductLifecycles() throws SQLException {
        List<ProductLifecycle> lifecycles = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                lifecycles.add(mapResultSetToProductLifecycle(rs));
            }
        }
        
        return lifecycles;
    }

    private ProductLifecycle mapResultSetToProductLifecycle(ResultSet rs) throws SQLException {
        ProductLifecycle lifecycle = new ProductLifecycle();
        
        lifecycle.setId(rs.getInt("id"));
        lifecycle.setPrimaryName(rs.getString("primaryname"));
        lifecycle.setDescription(rs.getString("description"));
        
        return lifecycle;
    }
}
