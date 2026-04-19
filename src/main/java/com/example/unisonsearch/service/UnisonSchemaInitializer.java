package com.example.unisonsearch.service;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.unisonsearch.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ensures Unison-related tables (i_user, unison, unison_facets) and app_config.UNISON_DEFAULTS
 * exist and are seeded with defaults on application startup. Idempotent.
 */
public class UnisonSchemaInitializer {

    private static final Logger logger = LoggerFactory.getLogger(UnisonSchemaInitializer.class);

    private static final String DEFAULT_UNISON_DEFAULTS_JSON = buildDefaultUnisonDefaultsJson();

    /**
     * Ensure all Unison tables exist, UNISON_DEFAULTS is in app_config, and default data is seeded when tables are empty.
     * Called from ApplicationInitializer on startup. Idempotent; safe to call multiple times.
     */
    public static void ensureUnisonTablesAndDefaults() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            ensureIUserTableAndSeed(conn);
            ensureUnisonTableAndSeed(conn);
            ensureUnisonDefaultsInAppConfig(conn);
            ensureUnisonFacetsTableAndSeed(conn);
            ensureUnisonFacetsColumnWidthsColumn(conn);
            logger.info("Unison schema and defaults initialization completed");
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("duplicate entry") || msg.contains("already exists")) {
                logger.debug("Unison bootstrap skipped or partial (already initialized): {}", e.getMessage());
            } else {
                logger.error("Unison schema initialization failed: {}", e.getMessage(), e);
            }
            throw new RuntimeException("Unison schema initialization failed", e);
        }
    }

    private static void ensureIUserTableAndSeed(Connection conn) throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS i_user (" +
                "reference INT(11) NOT NULL," +
                "active TINYINT(1) DEFAULT NULL," +
                "PRIMARY KEY (reference)," +
                "UNIQUE KEY reference (reference)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createSql);
        }
        if (isTableEmpty(conn, "i_user")) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO i_user (reference, active) VALUES (?, ?)")) {
                ps.setInt(1, 1);
                ps.setInt(2, 1);
                ps.addBatch();
                ps.setInt(1, 2);
                ps.setInt(2, 1);
                ps.addBatch();
                ps.executeBatch();
            }
            logger.info("Unison: seeded default i_user rows");
        }
    }

    private static void ensureUnisonTableAndSeed(Connection conn) throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS unison (" +
                "id INT(11) NOT NULL AUTO_INCREMENT," +
                "facet_version VARCHAR(256) DEFAULT NULL," +
                "user_reference INT(11) DEFAULT NULL," +
                "PRIMARY KEY (id)," +
                "KEY fk_unison_user (user_reference)," +
                "CONSTRAINT fk_unison_user FOREIGN KEY (user_reference) REFERENCES i_user (reference)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createSql);
        }
        if (isTableEmpty(conn, "unison")) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO unison (id, facet_version, user_reference) VALUES (?, ?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "1.0");
                ps.setInt(3, 1);
                ps.addBatch();
                ps.setInt(1, 2);
                ps.setString(2, "1.0");
                ps.setInt(3, 2);
                ps.addBatch();
                ps.executeBatch();
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE unison AUTO_INCREMENT = 3");
            }
            logger.info("Unison: seeded default unison rows");
        }
    }

    private static void ensureUnisonDefaultsInAppConfig(Connection conn) throws SQLException {
        String checkSql = "SELECT 1 FROM app_config WHERE config_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, Constants.CONFIG_UNISON_DEFAULTS);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        String insertSql = "INSERT INTO app_config (config_key, definition) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, Constants.CONFIG_UNISON_DEFAULTS);
            ps.setString(2, DEFAULT_UNISON_DEFAULTS_JSON);
            ps.executeUpdate();
            logger.info("Unison: inserted default UNISON_DEFAULTS into app_config");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate")) {
                return;
            }
            throw e;
        }
    }

    private static void ensureUnisonFacetsTableAndSeed(Connection conn) throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS unison_facets (" +
                "facetId VARCHAR(256) NOT NULL," +
                "active TINYINT(1) DEFAULT NULL," +
                "active_fields TEXT DEFAULT NULL," +
                "column_widths TEXT DEFAULT NULL," +
                "ordering INT(11) DEFAULT NULL," +
                "unison_id INT(11) NOT NULL," +
                "PRIMARY KEY (unison_id, facetId)," +
                "KEY fk_unison_facets_unison (unison_id)," +
                "CONSTRAINT fk_unison_facets_unison FOREIGN KEY (unison_id) REFERENCES unison (id) ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createSql);
        }
        if (!isTableEmpty(conn, "unison_facets")) {
            return;
        }
        ConfigurationService configurationService = new ConfigurationService();
        UnisonService unisonService = new UnisonService(configurationService);
        unisonService.ensureAllFacetsExistForBootstrap(conn, 1);
        unisonService.ensureAllFacetsExistForBootstrap(conn, 2);
        logger.info("Unison: seeded unison_facets from UNISON_DEFAULTS for default users");
    }

    private static boolean isTableEmpty(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM " + tableName + " LIMIT 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return !rs.next();
        }
    }

    /**
     * Adds {@code column_widths} to {@code unison_facets} when upgrading an older database.
     */
    private static void ensureUnisonFacetsColumnWidthsColumn(Connection conn) throws SQLException {
        if (columnExists(conn, "unison_facets", "column_widths")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "ALTER TABLE unison_facets ADD COLUMN column_widths TEXT DEFAULT NULL AFTER active_fields");
        }
        logger.info("Unison: added column_widths to unison_facets");
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) AS c FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("c") > 0;
            }
        }
    }

    private static String buildDefaultUnisonDefaultsJson() {
        return "{\"facets\":[" +
                "{\"id\":\"DATASET\",\"visibility\":true,\"activeFields\":\"refNumber,name,definition,lifecycle,systemId,systemName\"}," +
                "{\"id\":\"ATTRIBUTE\",\"visibility\":true,\"activeFields\":\"refNumber,name,definition,dataSetId,dataSetName,systemId,systemName\"}," +
                "{\"id\":\"SYSTEM\",\"visibility\":true,\"activeFields\":\"name,description,type,lifecycle,classification,ciarating\"}," +
                "{\"id\":\"GLOSSARY\",\"visibility\":true,\"activeFields\":\"name,type,parentName,parentType,kde,description,lifecycle\"}," +
                "{\"id\":\"DATAQUALITY\",\"visibility\":true,\"activeFields\":\"\"}," +
                "{\"id\":\"PEOPLE\",\"visibility\":true,\"activeFields\":\"firstName,lastName,email,function,orgUnit\"}," +
                "{\"id\":\"ROLE\",\"visibility\":true,\"activeFields\":\"role,fullName,objectType,object,roleAccepted\"}," +
                "{\"id\":\"BUSINESS_AREA\",\"visibility\":true,\"activeFields\":\"name,parent,description\"}," +
                "{\"id\":\"LEGAL_ENTITY\",\"visibility\":false,\"activeFields\":\"shortName,parentShortName,description\"}," +
                "{\"id\":\"CLIENT\",\"visibility\":false,\"activeFields\":\"name,parent,description,lifecycle\"}," +
                "{\"id\":\"COMMITTEE\",\"visibility\":false,\"activeFields\":\"refNumber,name,parent,description\"}," +
                "{\"id\":\"POLICY\",\"visibility\":false,\"activeFields\":\"refNumber,name,parentName,description,lifecycle\"}," +
                "{\"id\":\"PROCESS\",\"visibility\":false,\"activeFields\":\"refNumber,name,parentName,description\"}," +
                "{\"id\":\"INTERFACE\",\"visibility\":false,\"activeFields\":\"refNumber,name,description,sourceSystemShortName,targetSystemShortName,automation,frequency,lifecycle\"}," +
                "{\"id\":\"CAPABILITY\",\"visibility\":false,\"activeFields\":\"refNumber,name,parent,description\"}," +
                "{\"id\":\"PRODUCT\",\"visibility\":false,\"activeFields\":\"refNumber,name,parent,description,axonStatus\"}," +
                "{\"id\":\"ORG_UNIT\",\"visibility\":false,\"activeFields\":\"refNumber,name,parent,description,axonStatus\"}," +
                "{\"id\":\"GEOGRAPHY\",\"visibility\":false,\"activeFields\":\"name,parent,description\"}," +
                "{\"id\":\"REGULATION\",\"visibility\":false,\"activeFields\":\"refNumber,name,parent,description\"}," +
                "{\"id\":\"REGULATOR\",\"visibility\":false,\"activeFields\":\"name,description,shortName\"}," +
                "{\"id\":\"REGULATORY_THEME\",\"visibility\":false,\"activeFields\":\"refNumber,name,parent,description\"}," +
                "{\"id\":\"ACTIVE_TASKS\",\"visibility\":true,\"activeFields\":\"id,name,title,objectType,object,assignDate,dueDate,dueInDays,owner,segments,actions\"}," +
                "{\"id\":\"PROJECT\",\"visibility\":false,\"activeFields\":\"\"}," +
                "{\"id\":\"CHANGE_REQUESTS\",\"visibility\":false,\"activeFields\":\"\"}" +
                "]}";
    }
}
