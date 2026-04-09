package com.example.unisonsearch.service;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.unisonsearch.util.Constants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Loads configuration flags from app_config table.
 */
public class ConfigurationService {

    public boolean getFuzzySearchConfig() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT definition FROM app_config WHERE config_key = ?")) {
            ps.setString(1, Constants.CONFIG_FUZZY_DEFAULT);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("definition");
                    return "true".equalsIgnoreCase(value);
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting fuzzy search config: " + e.getMessage());
        }
        return false;
    }

    /**
     * Returns true if Non-Public objects should be hidden for non-stakeholders in Unison Search.
     * Reads from the same config key used by Admin Panel (HIDE_NON_PUBLIC_OBJECTS), with fallback to Hide_Non_Public.
     */
    public boolean shouldHideNonPublicObjects() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Prefer key used by Admin Panel → Search Settings (batch save)
            for (String configKey : new String[]{"HIDE_NON_PUBLIC_OBJECTS", Constants.CONFIG_HIDE_NON_PUBLIC}) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT definition FROM app_config WHERE config_key = ?")) {
                    ps.setString(1, configKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String value = rs.getString("definition");
                            return "true".equalsIgnoreCase(value) || "1".equals(value);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting hide non-public config: " + e.getMessage());
        }
        return false;
    }

    /**
     * Get Authorization_enabled configuration flag.
     * @return true if authorization is enabled, false otherwise
     */
    public boolean getAuthorizationEnabled() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT definition FROM app_config WHERE config_key = ?")) {
            ps.setString(1, Constants.CONFIG_AUTHORIZATION_ENABLED);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("definition");
                    return "true".equalsIgnoreCase(value) || "1".equals(value);
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting authorization enabled config: " + e.getMessage());
        }
        return true; // Default to enabled for security
    }

    /**
     * Get Event_Monitor_enabled configuration flag.
     * @return true if event monitoring is enabled, false otherwise
     */
    public boolean getEventMonitorEnabled() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT definition FROM app_config WHERE config_key = ?")) {
            ps.setString(1, Constants.CONFIG_EVENT_MONITOR_ENABLED);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("definition");
                    return "true".equalsIgnoreCase(value) || "1".equals(value);
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting event monitor enabled config: " + e.getMessage());
        }
        return false; // Default to disabled
    }

    /**
     * Get SEARCH_MIGRATION_V1_TO_V2 configuration flag.
     * @return true if migration from V1 to V2 search format is enabled
     */
    public boolean getSearchMigrationFlag() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT definition FROM app_config WHERE config_key = ?")) {
            ps.setString(1, Constants.CONFIG_SEARCH_MIGRATION_V1_TO_V2);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("definition");
                    return "true".equalsIgnoreCase(value) || "1".equals(value);
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting search migration flag: " + e.getMessage());
        }
        return false; // Default to false (no migration)
    }

    /**
     * Get UNISON_DEFAULTS configuration as a JsonObject.
     * This contains the default facet configuration with visibility and activeFields.
     * @return JsonObject containing the unison defaults, or null if not found
     */
    public JsonObject getUnisonDefaults() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT definition FROM app_config WHERE config_key = ?")) {
            ps.setString(1, Constants.CONFIG_UNISON_DEFAULTS);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String definition = rs.getString("definition");
                    if (definition != null && !definition.trim().isEmpty()) {
                        try {
                            return JsonParser.parseString(definition).getAsJsonObject();
                        } catch (Exception e) {
                            System.err.println("ConfigurationService: Error parsing UNISON_DEFAULTS JSON: " + e.getMessage());
                            return null;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting unison defaults: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get DATA_MIGRATION_ENABLED configuration flag.
     * 
     * This method should be called at the beginning of any servlet/endpoint that handles:
     * - Bulk Migrate operations (selected objects / facet ZIP)
     * - Table snapshot export/import ({@code TableSnapshotServlet})
     * 
     * Example usage in a servlet:
     * <pre>
     * ConfigurationService configService = new ConfigurationService();
     * if (!configService.isDataMigrationEnabled()) {
     *     response.setStatus(HttpServletResponse.SC_FORBIDDEN);
     *     JsonObject error = new JsonObject();
     *     error.addProperty("error", "Data Migration is not enabled");
     *     response.getWriter().write(gson.toJson(error));
     *     return;
     * }
     * </pre>
     * 
     * @return true if data migration is enabled, false otherwise
     */
    public boolean isDataMigrationEnabled() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT definition FROM app_config WHERE config_key = ?")) {
            ps.setString(1, Constants.CONFIG_DATA_MIGRATION_ENABLED);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("definition");
                    return "true".equalsIgnoreCase(value) || "1".equals(value);
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting data migration enabled config: " + e.getMessage());
        }
        return false; // Default to disabled
    }

    /**
     * Whether Unison Search should exclude system nobject_id (CR clone IDs) from system search.
     * When true (default), systems that are temporary clones in active CRs are hidden.
     * Set to false in app_config (Unison_Exclude_System_NObjectId = false) to show them if e.g. system 128 is missing.
     * @return true to exclude system nobject_id, false to include them
     */
    public boolean getExcludeSystemNObjectIdInSearch() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT definition FROM app_config WHERE config_key = ?")) {
            ps.setString(1, Constants.CONFIG_EXCLUDE_SYSTEM_NOBJECT_ID);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("definition");
                    return value == null || "true".equalsIgnoreCase(value) || "1".equals(value);
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting exclude system nobject_id config: " + e.getMessage());
        }
        return true; // Default: exclude system nobject_id (current behavior)
    }

    /**
     * Get a specific config value as a string.
     * @param configKey The configuration key to retrieve
     * @return The configuration value, or null if not found
     */
    public String getConfigValue(String configKey) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT definition FROM app_config WHERE config_key = ?")) {
            ps.setString(1, configKey);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("definition");
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting config value for key '" + configKey + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Get INFORMATION_SEGMENTATION_ENABLED configuration flag.
     * This flag controls whether Information Segmentation feature is enabled.
     * When enabled, Segment column is added to bulk upload templates for INSERT operations.
     * 
     * @return true if information segmentation is enabled, false otherwise
     */
    public boolean isInformationSegmentationEnabled() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT definition FROM app_config WHERE config_key = ?")) {
            ps.setString(1, "INFORMATION_SEGMENTATION_ENABLED");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("definition");
                    return "true".equalsIgnoreCase(value) || "1".equals(value);
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting information segmentation enabled config: " + e.getMessage());
        }
        return false; // Default to disabled
    }

    /**
     * Get ENTERPRISE_SEGMENT_DEFAULT configuration flag.
     * This flag controls if Enterprise Segment is the default segment while viewing BUDG content.
     * 
     * @return true if enterprise segment is set as default, false otherwise
     */
    public boolean isEnterpriseSegmentDefault() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT definition FROM app_config WHERE config_key = ?")) {
            ps.setString(1, "ENTERPRISE_SEGMENT_DEFAULT");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("definition");
                    return "true".equalsIgnoreCase(value) || "1".equals(value);
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting enterprise segment default config: " + e.getMessage());
        }
        return false; // Default to disabled
    }

    /**
     * Get ASSIGNED_SEGMENTS_DEFAULT configuration flag.
     * This flag controls if Assigned Segments is the default segment while viewing BUDG content.
     * 
     * @return true if assigned segments is set as default, false otherwise
     */
    public boolean isAssignedSegmentsDefault() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT definition FROM app_config WHERE config_key = ?")) {
            ps.setString(1, "ASSIGNED_SEGMENTS_DEFAULT");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("definition");
                    return "true".equalsIgnoreCase(value) || "1".equals(value);
                }
            }
        } catch (SQLException e) {
            System.err.println("ConfigurationService: Error getting assigned segments default config: " + e.getMessage());
        }
        return false; // Default to disabled
    }
}


