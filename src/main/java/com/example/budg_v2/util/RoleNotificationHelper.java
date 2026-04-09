package com.example.budg_v2.util;

import com.example.budg_v2.service.RoleNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper utility for creating role notifications across all facets
 * Provides dynamic methods to avoid code duplication
 */
public class RoleNotificationHelper {

    private static final Logger logger = LoggerFactory.getLogger(RoleNotificationHelper.class);
    private static final RoleNotificationService notificationService = new RoleNotificationService();

    /**
     * Configuration for each facet: table name, name column, and facet type
     */
    private static class FacetConfig {
        final String tableName;
        final String nameColumn;
        @SuppressWarnings("unused")
        final String facetType;

        FacetConfig(String tableName, String nameColumn, String facetType) {
            this.tableName = tableName;
            this.nameColumn = nameColumn;
            this.facetType = facetType;
        }
    }

    /**
     * Mapping of facet types to their database configuration
     */
    private static final Map<String, FacetConfig> FACET_CONFIG = new HashMap<>();

    static {
        // Initialize facet configurations
        FACET_CONFIG.put("Data Set", new FacetConfig("dataset", "PrimaryName", "Data Set"));
        FACET_CONFIG.put("System", new FacetConfig("system", "Name", "System"));
        FACET_CONFIG.put("Process", new FacetConfig("process", "primaryname", "Process"));
        FACET_CONFIG.put("Glossary", new FacetConfig("glossary", "Name", "Glossary"));
        FACET_CONFIG.put("Policy", new FacetConfig("policy", "PrimaryName", "Policy"));
        FACET_CONFIG.put("Product", new FacetConfig("product", "primaryname", "Product"));
        FACET_CONFIG.put("Project", new FacetConfig("project", "primaryname", "Project"));
        FACET_CONFIG.put("Interface", new FacetConfig("interface", "Name", "Interface"));
        FACET_CONFIG.put("Capability", new FacetConfig("capability", "PrimaryName", "Capability"));
        FACET_CONFIG.put("Client", new FacetConfig("client", "PrimaryName", "Client"));
        FACET_CONFIG.put("Legal Entity", new FacetConfig("legalentity", "Name", "Legal Entity"));
        FACET_CONFIG.put("Business Area", new FacetConfig("business_area", "PrimaryName", "Business Area"));
        FACET_CONFIG.put("Committee", new FacetConfig("committee", "PrimaryName", "Committee"));
        FACET_CONFIG.put("Attribute", new FacetConfig("attribute", "PrimaryName", "Attribute"));
    }

    /**
     * Get object name from database based on facet type and object ID
     * 
     * @param facetType Facet type (e.g., "Data Set", "System", "Process")
     * @param objectId Object ID
     * @param conn Database connection
     * @return Object name or null if not found
     */
    public static String getObjectName(String facetType, int objectId, Connection conn) throws SQLException {
        FacetConfig config = FACET_CONFIG.get(facetType);
        if (config == null) {
            logger.warn("Unknown facet type: {}", facetType);
            return null;
        }

        String sql = String.format("SELECT %s FROM %s WHERE ID = ?", config.nameColumn, config.tableName);
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(config.nameColumn);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting object name for facet {} object {}", facetType, objectId, e);
            throw e;
        }
        
        return null;
    }

    /**
     * Create role notification after a stakeholder is added to an object
     * This method should be called after commit() in stakeholder servlets
     * 
     * @param facetType Facet type (e.g., "Data Set", "System", "Process")
     * @param objectId Object ID
     * @param userId User ID (stakeholder being assigned)
     * @param roleId Role ID
     * @param objectXPeopleId Object_x_people record ID
     * @param conn Database connection (can be used to get object name)
     */
    public static void createNotificationAfterStakeholderAdded(
            String facetType, int objectId, int userId, int roleId, 
            Integer objectXPeopleId, Connection conn) {
        try {
            // Get object name
            String objectName = getObjectName(facetType, objectId, conn);
            if (objectName == null) {
                logger.warn("Could not get object name for facet {} object {}", facetType, objectId);
                return;
            }

            // Create notification
            notificationService.createRoleNotification(
                facetType, objectId, objectName, roleId, userId, objectXPeopleId
            );

            logger.info("Role notification created for stakeholder added: facet={}, objectId={}, userId={}", 
                       facetType, objectId, userId);

        } catch (Exception e) {
            logger.warn("Failed to create role notification for stakeholder added: facet={}, objectId={}, userId={}", 
                       facetType, objectId, userId, e);
            // Don't throw - notification failure shouldn't break the main operation
        }
    }

    /**
     * Create role notification after creator role is assigned during object creation
     * This method should be called after assignCreatorRole in object creation servlets
     * 
     * @param facetType Facet type (e.g., "Data Set", "System", "Process")
     * @param objectId Object ID
     * @param userId User ID (creator)
     * @param roleId Role ID (creator role)
     * @param objectXPeopleId Object_x_people record ID
     * @param conn Database connection (can be used to get object name)
     */
    public static void createNotificationAfterCreatorRoleAssigned(
            String facetType, int objectId, int userId, int roleId, 
            Integer objectXPeopleId, Connection conn) {
        try {
            // Get object name
            String objectName = getObjectName(facetType, objectId, conn);
            if (objectName == null) {
                logger.warn("Could not get object name for facet {} object {}", facetType, objectId);
                return;
            }

            // Create notification
            notificationService.createRoleNotification(
                facetType, objectId, objectName, roleId, userId, objectXPeopleId
            );

            logger.info("Role notification created for creator role: facet={}, objectId={}, userId={}", 
                       facetType, objectId, userId);

        } catch (Exception e) {
            logger.warn("Failed to create role notification for creator role: facet={}, objectId={}, userId={}", 
                       facetType, objectId, userId, e);
            // Don't throw - notification failure shouldn't break the main operation
        }
    }
}
