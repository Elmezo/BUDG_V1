package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RolePermissionDAO {

    public List<String> getDistinctPermissionClasses() throws SQLException {
        List<String> result = new ArrayList<>();
        final String sql = "SELECT DISTINCT permissionclass FROM role_permission ORDER BY permissionclass";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String val = rs.getString(1);
                if (val != null && !val.isEmpty()) {
                    result.add(val);
                }
            }
        }
        return result;
    }

    public List<String> getDistinctRoleClasses() throws SQLException {
        List<String> result = new ArrayList<>();
        final String sql = "SELECT DISTINCT roleclass FROM role_permission ORDER BY roleclass";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String val = rs.getString(1);
                if (val != null && !val.isEmpty()) {
                    result.add(val);
                }
            }
        }
        return result;
    }
}


