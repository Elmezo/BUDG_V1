package com.example.unisonsearch.service;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.unisonsearch.util.Constants;
import com.example.unisonsearch.util.FacetNormalizationUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing Unison facet configurations.
 * Handles visibility, ordering, and activeFields for facets per user.
 */
public class UnisonFacetService {

    private final Gson gson = new Gson();

    private final UnisonService unisonService;
    private final ConfigurationService configurationService;

    public UnisonFacetService(UnisonService unisonService, ConfigurationService configurationService) {
        this.unisonService = unisonService;
        this.configurationService = configurationService;
    }

    /**
     * Get all facets for a user.
     * 
     * @param userReference The user's reference ID
     * @return List of facet configurations
     * @throws SQLException if database error occurs
     */
    public List<Map<String, Object>> getFacetsForUser(int userReference) throws SQLException {
        int unisonId = unisonService.getUnisonId(userReference);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT facetId, active, active_fields, column_widths, ordering FROM unison_facets WHERE unison_id = ? ORDER BY ordering, facetId";
            List<Map<String, Object>> facets = new ArrayList<>();
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, unisonId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> facet = new HashMap<>();
                        facet.put("facetId", FacetNormalizationUtil.normalizeToCanonical(rs.getString("facetId")));
                        facet.put("active", rs.getBoolean("active"));
                        facet.put("activeFields", rs.getString("active_fields"));
                        facet.put("ordering", rs.getInt("ordering"));
                        String cw = rs.getString("column_widths");
                        facet.put("columnWidths", parseColumnWidthsJson(cw));
                        facets.add(facet);
                    }
                }
            }
            
            return facets;
        }
    }

    /**
     * Update facet configuration for a user.
     * 
     * @param userReference The user's reference ID
     * @param facetId The facet ID to update
     * @param active Whether the facet is active
     * @param activeFields The active fields for this facet
     * @param ordering The ordering (0 if inactive, incremental if active)
     * @throws SQLException if database error occurs
     */
    public void updateFacet(int userReference, String facetId, Boolean active, String activeFields, Integer ordering) throws SQLException {
        int unisonId = unisonService.getUnisonId(userReference);
        String canonicalFacetId = FacetNormalizationUtil.normalizeToCanonical(facetId);
        if (canonicalFacetId == null) {
            throw new SQLException("Invalid facetId");
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            // If active is being set to false, ensure ordering is 0
            if (active != null && !active) {
                ordering = 0;
            }
            
            String sql = "UPDATE unison_facets SET active = COALESCE(?, active), active_fields = COALESCE(?, active_fields), ordering = COALESCE(?, ordering) WHERE unison_id = ? AND facetId = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, active);
                ps.setString(2, activeFields);
                ps.setObject(3, ordering);
                ps.setInt(4, unisonId);
                ps.setString(5, canonicalFacetId);
                ps.executeUpdate();
            }
            
            // If facet was activated, ensure ordering is set properly
            if (active != null && active && (ordering == null || ordering == 0)) {
                setNextOrdering(conn, unisonId, canonicalFacetId);
            }
        }
    }

    /**
     * Update multiple facets at once (for drag-and-drop reordering).
     * 
     * @param userReference The user's reference ID
     * @param facets List of facet updates, each containing facetId, active, activeFields, ordering
     * @throws SQLException if database error occurs
     */
    public void updateFacets(int userReference, List<Map<String, Object>> facets) throws SQLException {
        int unisonId = unisonService.getUnisonId(userReference);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Use INSERT ... ON DUPLICATE KEY UPDATE to handle both insert and update
                // This ensures facets are created if they don't exist, and updated if they do
                String sql = "INSERT INTO unison_facets (unison_id, facetId, active, active_fields, column_widths, ordering) VALUES (?, ?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE active = VALUES(active), active_fields = VALUES(active_fields), column_widths = VALUES(column_widths), ordering = VALUES(ordering)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (Map<String, Object> facet : facets) {
                        String facetId = FacetNormalizationUtil.normalizeToCanonical((String) facet.get("facetId"));
                        if (facetId == null) {
                            continue;
                        }
                        Boolean active = (Boolean) facet.get("active");
                        String activeFields = (String) facet.get("activeFields");
                        String columnWidthsStr = stringifyColumnWidthsFromFacetMap(facet);
                        Integer ordering = (Integer) facet.get("ordering");
                        
                        // Ensure ordering is 0 if inactive
                        if (active != null && !active) {
                            ordering = 0;
                        }
                        
                        ps.setInt(1, unisonId);
                        ps.setString(2, facetId);
                        ps.setBoolean(3, active != null ? active : false);
                        ps.setString(4, activeFields != null ? activeFields : "");
                        ps.setString(5, columnWidthsStr);
                        ps.setInt(6, ordering != null ? ordering : 0);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Set the next ordering value for an active facet.
     * 
     * @param conn Database connection
     * @param unisonId The unison ID
     * @param facetId The facet ID
     * @throws SQLException if database error occurs
     */
    private void setNextOrdering(Connection conn, int unisonId, String facetId) throws SQLException {
        int nextOrdering = getNextOrdering(conn, unisonId);
        String sql = "UPDATE unison_facets SET ordering = ? WHERE unison_id = ? AND facetId = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, nextOrdering);
            ps.setInt(2, unisonId);
            ps.setString(3, facetId);
            ps.executeUpdate();
        }
    }

    /**
     * Get the next ordering value for active facets.
     * 
     * @param conn Database connection
     * @param unisonId The unison ID
     * @return The next ordering value
     * @throws SQLException if database error occurs
     */
    private int getNextOrdering(Connection conn, int unisonId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ordering), 0) + 1 AS next_ordering FROM unison_facets WHERE unison_id = ? AND active = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, unisonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("next_ordering");
                }
            }
        }
        return 1;
    }

    /**
     * Reset user's facets to SuperAdmin defaults.
     * 
     * @param userReference The user's reference ID
     * @throws SQLException if database error occurs
     */
    public void resetToDefaults(int userReference) throws SQLException {
        JsonObject defaults = configurationService.getUnisonDefaults();
        if (defaults == null) {
            throw new SQLException("UNISON_DEFAULTS configuration not found");
        }
        
        applyDefaults(userReference, defaults);
    }

    /**
     * Apply default facet configuration to a user.
     * 
     * @param userReference The user's reference ID
     * @param defaults The default configuration JSON
     * @throws SQLException if database error occurs
     */
    public void applyDefaults(int userReference, JsonObject defaults) throws SQLException {
        int unisonId = unisonService.getUnisonId(userReference);
        
        if (!defaults.has("facets")) {
            return;
        }
        
        JsonArray facets = defaults.getAsJsonArray("facets");
        Map<String, FacetConfig> facetConfigs = new HashMap<>();
        
        // Parse defaults
        int ordering = 1;
        for (int i = 0; i < facets.size(); i++) {
            JsonObject facet = facets.get(i).getAsJsonObject();
            String facetId = facet.has("id") ? facet.get("id").getAsString() : null;
            if (facetId != null) {
                boolean visibility = facet.has("visibility") && facet.get("visibility").getAsBoolean();
                String activeFields = facet.has("activeFields") ? facet.get("activeFields").getAsString() : "";
                String columnWidthsJson = "";
                if (facet.has("columnWidths") && !facet.get("columnWidths").isJsonNull()) {
                    JsonElement cw = facet.get("columnWidths");
                    if (cw.isJsonObject()) {
                        columnWidthsJson = cw.getAsJsonObject().toString();
                    }
                }
                int order = visibility ? ordering++ : 0;
                facetConfigs.put(facetId, new FacetConfig(visibility, activeFields, columnWidthsJson, order));
            }
        }
        
        // Apply to database use upsert so new users (no existing rows) also get defaults
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String sql = "INSERT INTO unison_facets (unison_id, facetId, active, active_fields, column_widths, ordering) " +
                             "VALUES (?, ?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE active = VALUES(active), active_fields = VALUES(active_fields), column_widths = VALUES(column_widths), ordering = VALUES(ordering)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (Map.Entry<String, FacetConfig> entry : facetConfigs.entrySet()) {
                        String facetId = entry.getKey();
                        FacetConfig config = entry.getValue();
                        
                        ps.setInt(1, unisonId);
                        ps.setString(2, facetId);
                        ps.setBoolean(3, config.visibility);
                        ps.setString(4, config.activeFields);
                        ps.setString(5, config.columnWidthsJson != null ? config.columnWidthsJson : "");
                        ps.setInt(6, config.ordering);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Save SuperAdmin default layout.
     * Updates UNISON_DEFAULTS in app_config with the provided configuration.
     * 
     * @param defaults The default configuration JSON
     * @throws SQLException if database error occurs
     */
    public void saveSuperAdminDefaults(JsonObject defaults) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String checkSql = "SELECT COUNT(*) as count FROM app_config WHERE config_key = ?";
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, Constants.CONFIG_UNISON_DEFAULTS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        exists = rs.getInt("count") > 0;
                    }
                }
            }
            
            String sql = exists 
                ? "UPDATE app_config SET definition = ? WHERE config_key = ?"
                : "INSERT INTO app_config (config_key, definition) VALUES (?, ?)";
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, defaults.toString());
                ps.setString(2, Constants.CONFIG_UNISON_DEFAULTS);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Get SuperAdmin default layout.
     * 
     * @return The default configuration JSON, or null if not found
     */
    public JsonObject getSuperAdminDefaults() {
        return configurationService.getUnisonDefaults();
    }

    /**
     * Save column preferences for a specific facet.
     * Updates only the activeFields column without affecting active or ordering.
     * 
     * @param userReference The user's reference ID
     * @param facetId The facet ID
     * @param columns Array of column names to save (will be stored as comma-separated string)
     * @throws SQLException if database error occurs
     */
    public void saveColumnPreferences(int userReference, String facetId, List<String> columns, String columnWidthsJson) throws SQLException {
        int unisonId = unisonService.getUnisonId(userReference);
        String canonicalFacetId = FacetNormalizationUtil.normalizeToCanonical(facetId);
        if (canonicalFacetId == null) {
            throw new SQLException("Invalid facetId");
        }

        // Join columns with comma
        String activeFields = columns != null && !columns.isEmpty() 
            ? String.join(", ", columns) 
            : "";
        String widths = columnWidthsJson != null ? columnWidthsJson.trim() : "";
        if (widths.isEmpty()) {
            widths = null;
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "UPDATE unison_facets SET active_fields = ?, column_widths = ? WHERE unison_id = ? AND facetId = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, activeFields);
                if (widths != null) {
                    ps.setString(2, widths);
                } else {
                    ps.setNull(2, java.sql.Types.LONGVARCHAR);
                }
                ps.setInt(3, unisonId);
                ps.setString(4, canonicalFacetId);
                
                int rowsAffected = ps.executeUpdate();
                if (rowsAffected == 0) {
                    // Facet doesn't exist, create it with default values
                    String insertSql = "INSERT INTO unison_facets (unison_id, facetId, active, active_fields, column_widths, ordering) VALUES (?, ?, 0, ?, ?, 0)";
                    try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                        insertPs.setInt(1, unisonId);
                        insertPs.setString(2, canonicalFacetId);
                        insertPs.setString(3, activeFields);
                        if (widths != null) {
                            insertPs.setString(4, widths);
                        } else {
                            insertPs.setNull(4, java.sql.Types.LONGVARCHAR);
                        }
                        insertPs.executeUpdate();
                    }
                }
            }
        }
    }

    /**
     * Get column preferences for a specific facet.
     * 
     * @param userReference The user's reference ID
     * @param facetId The facet ID
     * @return List of column names, or null if not found
     * @throws SQLException if database error occurs
     */
    public List<String> getColumnPreferences(int userReference, String facetId) throws SQLException {
        int unisonId = unisonService.getUnisonId(userReference);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT active_fields FROM unison_facets WHERE unison_id = ? AND facetId = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, unisonId);
                ps.setString(2, facetId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String activeFields = rs.getString("active_fields");
                        if (activeFields != null && !activeFields.trim().isEmpty()) {
                            // Split by comma and trim each column name
                            List<String> columns = new ArrayList<>();
                            String[] parts = activeFields.split(",");
                            for (String part : parts) {
                                String trimmed = part.trim();
                                if (!trimmed.isEmpty()) {
                                    columns.add(trimmed);
                                }
                            }
                            return columns.isEmpty() ? null : columns;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Map<String, Object> parseColumnWidthsJson(String cw) {
        if (cw == null || cw.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = gson.fromJson(cw, Map.class);
            return parsed != null ? parsed : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String stringifyColumnWidthsFromFacetMap(Map<String, Object> facet) {
        if (facet == null || !facet.containsKey("columnWidths")) {
            return "";
        }
        Object o = facet.get("columnWidths");
        if (o == null) {
            return "";
        }
        if (o instanceof String) {
            return ((String) o).trim();
        }
        try {
            return gson.toJson(o);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Inner class to hold facet configuration.
     */
    private static class FacetConfig {
        final boolean visibility;
        final String activeFields;
        final String columnWidthsJson;
        final int ordering;
        
        FacetConfig(boolean visibility, String activeFields, String columnWidthsJson, int ordering) {
            this.visibility = visibility;
            this.activeFields = activeFields;
            this.columnWidthsJson = columnWidthsJson;
            this.ordering = ordering;
        }
    }
}

