package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.MapMarker;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MapMarkerDAO {

    private static final String SELECT_BY_LAYER_ID = "SELECT * FROM map_markers WHERE layer_id = ? ORDER BY created_at DESC";
    private static final String SELECT_BY_ID = "SELECT * FROM map_markers WHERE id = ?";
    private static final String SELECT_BY_BOUNDS = "SELECT * FROM map_markers WHERE layer_id = ? AND lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?";
    private static final String INSERT = "INSERT INTO map_markers (layer_id, lat, lng, title, description, icon_url, metadata_json, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE map_markers SET lat = ?, lng = ?, title = ?, description = ?, icon_url = ?, metadata_json = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM map_markers WHERE id = ?";

    public List<MapMarker> getMarkersByLayerId(long layerId) throws SQLException {
        List<MapMarker> markers = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_LAYER_ID)) {

            pstmt.setLong(1, layerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    markers.add(mapResultSetToMarker(rs));
                }
            }
        }
        return markers;
    }

    public MapMarker getMarkerById(long id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToMarker(rs);
                }
            }
        }
        return null;
    }

    public List<MapMarker> getMarkersByBounds(long layerId, double minLat, double maxLat, double minLng, double maxLng) throws SQLException {
        List<MapMarker> markers = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_BOUNDS)) {

            pstmt.setLong(1, layerId);
            pstmt.setBigDecimal(2, java.math.BigDecimal.valueOf(minLat));
            pstmt.setBigDecimal(3, java.math.BigDecimal.valueOf(maxLat));
            pstmt.setBigDecimal(4, java.math.BigDecimal.valueOf(minLng));
            pstmt.setBigDecimal(5, java.math.BigDecimal.valueOf(maxLng));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    markers.add(mapResultSetToMarker(rs));
                }
            }
        }
        return markers;
    }

    public MapMarker createMarker(MapMarker marker) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setLong(1, marker.getLayerId());
            pstmt.setBigDecimal(2, marker.getLat());
            pstmt.setBigDecimal(3, marker.getLng());
            pstmt.setString(4, marker.getTitle());
            pstmt.setString(5, marker.getDescription());
            pstmt.setString(6, marker.getIconUrl());
            pstmt.setString(7, marker.getMetadataJson());
            pstmt.setObject(8, marker.getCreatedBy());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating marker failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    marker.setId(generatedKeys.getLong(1));
                    return marker;
                } else {
                    throw new SQLException("Creating marker failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateMarker(MapMarker marker) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setBigDecimal(1, marker.getLat());
            pstmt.setBigDecimal(2, marker.getLng());
            pstmt.setString(3, marker.getTitle());
            pstmt.setString(4, marker.getDescription());
            pstmt.setString(5, marker.getIconUrl());
            pstmt.setString(6, marker.getMetadataJson());
            pstmt.setLong(7, marker.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteMarker(long id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE)) {

            pstmt.setLong(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private MapMarker mapResultSetToMarker(ResultSet rs) throws SQLException {
        MapMarker marker = new MapMarker();
        marker.setId(rs.getLong("id"));
        marker.setLayerId(rs.getLong("layer_id"));
        marker.setLat(rs.getBigDecimal("lat"));
        marker.setLng(rs.getBigDecimal("lng"));
        marker.setTitle(rs.getString("title"));
        marker.setDescription(rs.getString("description"));
        marker.setIconUrl(rs.getString("icon_url"));
        marker.setMetadataJson(rs.getString("metadata_json"));
        marker.setCreatedBy(rs.getObject("created_by", Long.class));
        marker.setCreatedAt(rs.getTimestamp("created_at"));
        marker.setUpdatedAt(rs.getTimestamp("updated_at"));
        return marker;
    }
}

