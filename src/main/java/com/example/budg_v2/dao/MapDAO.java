package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Map;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MapDAO {

    private static final String SELECT_ALL = "SELECT * FROM maps WHERE enabled = TRUE ORDER BY name";
    private static final String SELECT_BY_ID = "SELECT * FROM maps WHERE id = ?";
    private static final String INSERT = "INSERT INTO maps (name, description, default_center_lat, default_center_lng, default_zoom, enabled, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE maps SET name = ?, description = ?, default_center_lat = ?, default_center_lng = ?, default_zoom = ?, enabled = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM maps WHERE id = ?";

    public List<Map> getAllMaps() throws SQLException {
        List<Map> maps = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                maps.add(mapResultSetToMap(rs));
            }
        }
        return maps;
    }

    public Map getMapById(long id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToMap(rs);
                }
            }
        }
        return null;
    }

    public Map createMap(Map map) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, map.getName());
            pstmt.setString(2, map.getDescription());
            pstmt.setBigDecimal(3, map.getDefaultCenterLat());
            pstmt.setBigDecimal(4, map.getDefaultCenterLng());
            pstmt.setInt(5, map.getDefaultZoom() != null ? map.getDefaultZoom() : 10);
            pstmt.setBoolean(6, map.getEnabled() != null ? map.getEnabled() : true);
            pstmt.setObject(7, map.getCreatedBy());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating map failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    map.setId(generatedKeys.getLong(1));
                    return map;
                } else {
                    throw new SQLException("Creating map failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateMap(Map map) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, map.getName());
            pstmt.setString(2, map.getDescription());
            pstmt.setBigDecimal(3, map.getDefaultCenterLat());
            pstmt.setBigDecimal(4, map.getDefaultCenterLng());
            pstmt.setInt(5, map.getDefaultZoom() != null ? map.getDefaultZoom() : 10);
            pstmt.setBoolean(6, map.getEnabled() != null ? map.getEnabled() : true);
            pstmt.setLong(7, map.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteMap(long id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE)) {

            pstmt.setLong(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private Map mapResultSetToMap(ResultSet rs) throws SQLException {
        Map map = new Map();
        map.setId(rs.getLong("id"));
        map.setName(rs.getString("name"));
        map.setDescription(rs.getString("description"));
        map.setDefaultCenterLat(rs.getBigDecimal("default_center_lat"));
        map.setDefaultCenterLng(rs.getBigDecimal("default_center_lng"));
        map.setDefaultZoom(rs.getInt("default_zoom"));
        map.setEnabled(rs.getBoolean("enabled"));
        map.setCreatedAt(rs.getTimestamp("created_at"));
        map.setUpdatedAt(rs.getTimestamp("updated_at"));
        map.setCreatedBy(rs.getObject("created_by", Long.class));
        return map;
    }
}

