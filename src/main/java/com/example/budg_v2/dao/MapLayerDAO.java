package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.MapLayer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MapLayerDAO {

    private static final String SELECT_BY_MAP_ID = "SELECT * FROM map_layers WHERE map_id = ? ORDER BY order_index, id";
    private static final String SELECT_BY_ID = "SELECT * FROM map_layers WHERE id = ?";
    private static final String INSERT = "INSERT INTO map_layers (map_id, name, type, url_template, visible, order_index, style_json) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE map_layers SET name = ?, type = ?, url_template = ?, visible = ?, order_index = ?, style_json = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM map_layers WHERE id = ?";
    private static final String UPDATE_VISIBILITY = "UPDATE map_layers SET visible = ? WHERE id = ?";

    public List<MapLayer> getLayersByMapId(long mapId) throws SQLException {
        List<MapLayer> layers = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_MAP_ID)) {

            pstmt.setLong(1, mapId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    layers.add(mapResultSetToLayer(rs));
                }
            }
        }
        return layers;
    }

    public MapLayer getLayerById(long id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToLayer(rs);
                }
            }
        }
        return null;
    }

    public MapLayer createLayer(MapLayer layer) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setLong(1, layer.getMapId());
            pstmt.setString(2, layer.getName());
            pstmt.setString(3, layer.getType());
            pstmt.setString(4, layer.getUrlTemplate());
            pstmt.setBoolean(5, layer.getVisible() != null ? layer.getVisible() : true);
            pstmt.setInt(6, layer.getOrderIndex() != null ? layer.getOrderIndex() : 0);
            pstmt.setString(7, layer.getStyleJson());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating layer failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    layer.setId(generatedKeys.getLong(1));
                    return layer;
                } else {
                    throw new SQLException("Creating layer failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateLayer(MapLayer layer) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, layer.getName());
            pstmt.setString(2, layer.getType());
            pstmt.setString(3, layer.getUrlTemplate());
            pstmt.setBoolean(4, layer.getVisible() != null ? layer.getVisible() : true);
            pstmt.setInt(5, layer.getOrderIndex() != null ? layer.getOrderIndex() : 0);
            pstmt.setString(6, layer.getStyleJson());
            pstmt.setLong(7, layer.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean updateLayerVisibility(long layerId, boolean visible) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_VISIBILITY)) {

            pstmt.setBoolean(1, visible);
            pstmt.setLong(2, layerId);

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteLayer(long id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE)) {

            pstmt.setLong(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private MapLayer mapResultSetToLayer(ResultSet rs) throws SQLException {
        MapLayer layer = new MapLayer();
        layer.setId(rs.getLong("id"));
        layer.setMapId(rs.getLong("map_id"));
        layer.setName(rs.getString("name"));
        layer.setType(rs.getString("type"));
        layer.setUrlTemplate(rs.getString("url_template"));
        layer.setVisible(rs.getBoolean("visible"));
        layer.setOrderIndex(rs.getInt("order_index"));
        
        // Handle JSON column - MySQL returns it as String
        String styleJson = rs.getString("style_json");
        layer.setStyleJson(styleJson);
        
        layer.setCreatedAt(rs.getTimestamp("created_at"));
        layer.setUpdatedAt(rs.getTimestamp("updated_at"));
        return layer;
    }
}

