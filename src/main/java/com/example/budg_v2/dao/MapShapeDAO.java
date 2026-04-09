package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.MapShape;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MapShapeDAO {

    private static final String SELECT_BY_LAYER_ID = "SELECT * FROM map_shapes WHERE layer_id = ? ORDER BY created_at DESC";
    private static final String SELECT_BY_ID = "SELECT * FROM map_shapes WHERE id = ?";
    private static final String INSERT = "INSERT INTO map_shapes (layer_id, type, coordinates_json, style_json, metadata_json, created_by) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE map_shapes SET type = ?, coordinates_json = ?, style_json = ?, metadata_json = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM map_shapes WHERE id = ?";

    public List<MapShape> getShapesByLayerId(long layerId) throws SQLException {
        List<MapShape> shapes = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_LAYER_ID)) {

            pstmt.setLong(1, layerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    shapes.add(mapResultSetToShape(rs));
                }
            }
        }
        return shapes;
    }

    public MapShape getShapeById(long id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToShape(rs);
                }
            }
        }
        return null;
    }

    public MapShape createShape(MapShape shape) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setLong(1, shape.getLayerId());
            pstmt.setString(2, shape.getType());
            pstmt.setString(3, shape.getCoordinatesJson());
            pstmt.setString(4, shape.getStyleJson());
            pstmt.setString(5, shape.getMetadataJson());
            pstmt.setObject(6, shape.getCreatedBy());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating shape failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    shape.setId(generatedKeys.getLong(1));
                    return shape;
                } else {
                    throw new SQLException("Creating shape failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateShape(MapShape shape) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, shape.getType());
            pstmt.setString(2, shape.getCoordinatesJson());
            pstmt.setString(3, shape.getStyleJson());
            pstmt.setString(4, shape.getMetadataJson());
            pstmt.setLong(5, shape.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteShape(long id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE)) {

            pstmt.setLong(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private MapShape mapResultSetToShape(ResultSet rs) throws SQLException {
        MapShape shape = new MapShape();
        shape.setId(rs.getLong("id"));
        shape.setLayerId(rs.getLong("layer_id"));
        shape.setType(rs.getString("type"));
        shape.setCoordinatesJson(rs.getString("coordinates_json"));
        shape.setStyleJson(rs.getString("style_json"));
        shape.setMetadataJson(rs.getString("metadata_json"));
        shape.setCreatedBy(rs.getObject("created_by", Long.class));
        shape.setCreatedAt(rs.getTimestamp("created_at"));
        shape.setUpdatedAt(rs.getTimestamp("updated_at"));
        return shape;
    }
}

