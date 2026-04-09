package com.example.unisonsearch.service;

import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing Unison user configurations.
 * Handles initialization of unison rows and facet configurations per user.
 */
public class UnisonService {

    private final ConfigurationService configurationService;
    
    // List of all facets (including Active Tasks, Project, and Change Requests)
    private static final String[] ALL_FACETS = {
        "DATASET", "ATTRIBUTE", "SYSTEM", "GLOSSARY", "DATAQUALITY",
        "PEOPLE", "ROLE", "BUSINESS_AREA", "LEGAL_ENTITY", "CLIENT",
        "COMMITTEE", "POLICY", "PROCESS", "INTERFACE", "CAPABILITY",
        "PRODUCT", "ORG_UNIT", "GEOGRAPHY", "REGULATION", "REGULATOR",
        "REGULATORY_THEME", "ACTIVE_TASKS", "PROJECT", "CHANGE_REQUESTS"
    };

    public UnisonService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    /**
     * Initialize or get unison row for a user.
     * Creates unison entry and all 22 facet rows if they don't exist.
     * 
     * @param userReference The user's reference ID
     * @return The unison ID for the user
     * @throws SQLException if database error occurs
     */
    public int initializeUnisonForUser(int userReference) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Ensure user exists in i_user (FK from unison) so INSERT into unison does not fail
            ensureUserInIUser(conn, userReference);

            // Check if unison row exists
            Integer unisonId = getUnisonIdForUser(conn, userReference);

            if (unisonId == null) {
                // Create unison row
                unisonId = createUnisonRow(conn, userReference);
            }

            // Ensure all 22 facets exist for this unison
            ensureAllFacetsExist(conn, unisonId);

            return unisonId;
        }
    }

    /**
     * Ensure the user has a row in i_user so that unison (FK user_reference -> i_user.reference) can be created.
     * If missing, insert (reference, active=1). Idempotent.
     */
    private void ensureUserInIUser(Connection conn, int userReference) throws SQLException {
        String checkSql = "SELECT 1 FROM i_user WHERE reference = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setInt(1, userReference);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        String insertSql = "INSERT INTO i_user (reference, active) VALUES (?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, userReference);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate")) {
                return;
            }
            throw e;
        }
    }

    /**
     * Get the unison ID for a user.
     * 
     * @param conn Database connection
     * @param userReference The user's reference ID
     * @return The unison ID, or null if not found
     * @throws SQLException if database error occurs
     */
    private Integer getUnisonIdForUser(Connection conn, int userReference) throws SQLException {
        String sql = "SELECT id FROM unison WHERE user_reference = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userReference);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }

    /**
     * Create a new unison row for a user.
     * 
     * @param conn Database connection
     * @param userReference The user's reference ID
     * @return The newly created unison ID
     * @throws SQLException if database error occurs
     */
    private int createUnisonRow(Connection conn, int userReference) throws SQLException {
        String sql = "INSERT INTO unison (user_reference, facet_version) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userReference);
            ps.setString(2, "1.0");
            ps.executeUpdate();
            
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create unison row for user " + userReference);
    }

    /**
     * Public entry point for bootstrap/initializer: ensure all facets exist for a unison row using the given connection.
     * Reads defaults from UNISON_DEFAULTS in app_config. Used when seeding unison_facets for default users.
     *
     * @param conn Database connection
     * @param unisonId The unison ID
     * @throws SQLException if database error occurs
     */
    public void ensureAllFacetsExistForBootstrap(Connection conn, int unisonId) throws SQLException {
        ensureAllFacetsExist(conn, unisonId);
    }

    /**
     * Ensure all 22 facets exist for a unison row.
     * Creates missing facets based on UNISON_DEFAULTS configuration.
     * 
     * @param conn Database connection
     * @param unisonId The unison ID
     * @throws SQLException if database error occurs
     */
    private void ensureAllFacetsExist(Connection conn, int unisonId) throws SQLException {
        // Get default configuration
        JsonObject defaults = configurationService.getUnisonDefaults();
        Map<String, FacetDefault> facetDefaults = parseFacetDefaults(defaults);
        
        // Check which facets are missing
        List<String> existingFacets = getExistingFacetIds(conn, unisonId);
        
        for (String facetId : ALL_FACETS) {
            if (!existingFacets.contains(facetId)) {
                createFacetRow(conn, unisonId, facetId, facetDefaults.get(facetId));
            }
        }
    }

    /**
     * Get list of existing facet IDs for a unison.
     * 
     * @param conn Database connection
     * @param unisonId The unison ID
     * @return List of facet IDs
     * @throws SQLException if database error occurs
     */
    private List<String> getExistingFacetIds(Connection conn, int unisonId) throws SQLException {
        List<String> facets = new ArrayList<>();
        String sql = "SELECT facetId FROM unison_facets WHERE unison_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, unisonId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    facets.add(rs.getString("facetId"));
                }
            }
        }
        return facets;
    }

    /**
     * Create a facet row for a unison.
     * 
     * @param conn Database connection
     * @param unisonId The unison ID
     * @param facetId The facet ID
     * @param defaultConfig Default configuration for this facet (can be null)
     * @throws SQLException if database error occurs
     */
    private void createFacetRow(Connection conn, int unisonId, String facetId, FacetDefault defaultConfig) throws SQLException {
        boolean active = defaultConfig != null ? defaultConfig.visibility : false;
        String activeFields = defaultConfig != null ? defaultConfig.activeFields : "";
        int ordering = active ? getNextOrdering(conn, unisonId) : 0;
        
        String sql = "INSERT INTO unison_facets (unison_id, facetId, active, active_fields, ordering) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, unisonId);
            ps.setString(2, facetId);
            ps.setBoolean(3, active);
            ps.setString(4, activeFields);
            ps.setInt(5, ordering);
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
     * Parse facet defaults from UNISON_DEFAULTS JSON.
     * 
     * @param defaults The UNISON_DEFAULTS JsonObject
     * @return Map of facet ID to FacetDefault
     */
    private Map<String, FacetDefault> parseFacetDefaults(JsonObject defaults) {
        Map<String, FacetDefault> facetDefaults = new HashMap<>();
        
        if (defaults == null || !defaults.has("facets")) {
            return facetDefaults;
        }
        
        JsonArray facets = defaults.getAsJsonArray("facets");
        for (int i = 0; i < facets.size(); i++) {
            JsonObject facet = facets.get(i).getAsJsonObject();
            String facetId = facet.has("id") ? facet.get("id").getAsString() : null;
            if (facetId != null) {
                boolean visibility = facet.has("visibility") && facet.get("visibility").getAsBoolean();
                String activeFields = facet.has("activeFields") ? facet.get("activeFields").getAsString() : "";
                facetDefaults.put(facetId, new FacetDefault(visibility, activeFields));
            }
        }
        
        return facetDefaults;
    }

    /**
     * Inner class to hold facet default configuration.
     */
    private static class FacetDefault {
        final boolean visibility;
        final String activeFields;
        
        FacetDefault(boolean visibility, String activeFields) {
            this.visibility = visibility;
            this.activeFields = activeFields;
        }
    }

    /**
     * Get unison ID for a user, initializing if necessary.
     * 
     * @param userReference The user's reference ID
     * @return The unison ID
     * @throws SQLException if database error occurs
     */
    public int getUnisonId(int userReference) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer unisonId = getUnisonIdForUser(conn, userReference);
            if (unisonId == null) {
                return initializeUnisonForUser(userReference);
            }
            return unisonId;
        }
    }
}

