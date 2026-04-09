package com.example.budg_v2.service;

import com.example.budg_v2.bulk.objects.BulkUploadBroadcaster;
import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic processor for relationship entities (e.g., glossary_x_system,
 * glossary_x_glossary)
 * Handles INSERT and DELETE operations for many-to-many relationship tables
 */
public class GenericRelationshipProcessor implements EntityProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenericRelationshipProcessor.class);
    private final JobDAO jobDAO = new JobDAO();
    private final String entityName;
    private final String tableName;

    // Mapping of entity names to their database tables
    private static final Map<String, String> ENTITY_TABLE_MAP = new HashMap<>();

    static {
        ENTITY_TABLE_MAP.put("systemxcatitemcategory", "glossary_x_system");
        ENTITY_TABLE_MAP.put("catitemcategoryxsystem", "glossary_x_system");
        ENTITY_TABLE_MAP.put("catitemcategory hierarchy", "glossary_x_glossary");
        ENTITY_TABLE_MAP.put("catitemcategoryxcatitemcategory", "glossary_x_glossary");
        ENTITY_TABLE_MAP.put("system x glossary", "glossary_x_system");
        ENTITY_TABLE_MAP.put("glossary x system", "glossary_x_system");
        ENTITY_TABLE_MAP.put("glossary hierarchy", "glossary_x_glossary");
        ENTITY_TABLE_MAP.put("glossary x glossary", "glossary_x_glossary");
        ENTITY_TABLE_MAP.put("glossary_glossary", "glossary_x_glossary");
    }

    public GenericRelationshipProcessor(String entityName) {
        this.entityName = entityName;
        this.tableName = inferTableName(entityName);
    }

    private String inferTableName(String entityName) {
        String normalized = entityName.toLowerCase().trim();
        return ENTITY_TABLE_MAP.getOrDefault(normalized, "unknown");
    }

    @Override
    public void process(int jobId, JsonArray validatedData, int userId, String errorHandling,
            String uploadOption, String segmentMode, String segment) {

        if ("unknown".equals(tableName)) {
            logger.error("Cannot process relationship entity {}: unknown table mapping", entityName);
            try {
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.createJobProgress(jobId, 100, "Failed",
                        "Unknown relationship entity: " + entityName);
            } catch (SQLException e) {
                logger.error("Error updating job status", e);
            }
            return;
        }

        int insertedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;
        int totalRows = validatedData.size();

        Connection conn = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            jobDAO.updateJobStatus(jobId, "Processing", false);
            jobDAO.createJobProgress(jobId, 30, "Processing",
                    String.format("Processing %s relationships...", entityName));
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30,
                    "Processing relationships...", 0, 0, 0, 0);

            for (int i = 0; i < validatedData.size(); i++) {
                JsonObject rowData = validatedData.get(i).getAsJsonObject();
                String operation = rowData.has("operation") ? rowData.get("operation").getAsString() : "INSERT";
                int rowNumber = rowData.has("row_number") ? rowData.get("row_number").getAsInt() : i + 1;

                try {
                    if ("INSERT".equals(operation)) {
                        insertRelationship(conn, tableName, rowData);
                        insertedCount++;
                    } else if ("DELETE".equals(operation)) {
                        deleteRelationship(conn, tableName, rowData);
                        deletedCount++;
                    }

                    if (i % 10 == 0) {
                        int progressPercent = 30 + (int) ((i / (double) totalRows) * 65);
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercent,
                                String.format("Processing row %d of %d", i + 1, totalRows),
                                insertedCount, 0, deletedCount, failedCount);
                    }

                } catch (SQLException e) {
                    logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                    failedCount++;

                    if ("STOP_ON_ERROR".equals(errorHandling)) {
                        throw e;
                    }
                }
            }

            conn.commit();

            jobDAO.updateJobStatus(jobId, "Completed", true);
            jobDAO.createJobProgress(jobId, 100, "Completed",
                    String.format("Completed: %d inserted, %d deleted, %d failed",
                            insertedCount, deletedCount, failedCount));
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                    "Import completed successfully",
                    insertedCount, 0, deletedCount, failedCount);

            logger.info("Relationship bulk upload job {} completed: {} inserted, {} deleted, {} failed",
                    jobId, insertedCount, deletedCount, failedCount);

        } catch (Exception e) {
            logger.error("Error processing relationship bulk upload job {}", jobId, e);
            try {
                if (conn != null) {
                    conn.rollback();
                }
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.createJobProgress(jobId, 100, "Failed",
                        "Error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Import failed: " + e.getMessage(),
                        insertedCount, 0, deletedCount, failedCount);
            } catch (SQLException rollbackEx) {
                logger.error("Error during rollback", rollbackEx);
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    private void insertRelationship(Connection conn, String tableName, JsonObject rowData) throws SQLException {
        // Build dynamic INSERT based on the fields present in rowData
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // Exclude auto-increment ID and metadata fields from rowData
        rowData.entrySet().forEach(entry -> {
            String key = entry.getKey();
            // Skip operation, row_number, and ID field (will be handled separately for
            // glossary_x_glossary)
            if (!key.equals("operation") && !key.equals("row_number") && !key.equalsIgnoreCase("ID")) {
                columns.add(key);
                if (entry.getValue().isJsonNull()) {
                    values.add(null);
                } else if (entry.getValue().isJsonPrimitive()) {
                    if (entry.getValue().getAsJsonPrimitive().isNumber()) {
                        values.add(entry.getValue().getAsInt());
                    } else {
                        values.add(entry.getValue().getAsString());
                    }
                }
            }
        });

        // Add standard audit fields based on table type
        if (tableName.equals("glossary_x_glossary")) {
            // glossary_x_glossary doesn't have AUTO_INCREMENT, so we need to generate the
            // ID
            int nextId = getNextId(conn, tableName);
            columns.add(0, "ID"); // Add ID as first column
            values.add(0, nextId); // Add ID value as first value

            // Add RelationType (default to 1 if not present)
            if (!columns.contains("RelationType")) {
                columns.add("RelationType");
                values.add(1);
            }
            // Add CreateDatetime
            columns.add("CreateDatetime");
            values.add(new Timestamp(System.currentTimeMillis()));
            // Add LastUpdateDatetime
            columns.add("LastUpdateDatetime");
            values.add(new Timestamp(System.currentTimeMillis()));
            // Add LastUpdateUser_ID (default to 1)
            columns.add("LastUpdateUser_ID");
            values.add(1);
        } else if (tableName.equals("glossary_x_system")) {
            // glossary_x_system has AUTO_INCREMENT, so we don't need to add ID
            // Add defaults for glossary_x_system if needed
            if (!columns.contains("Relation_TypeID")) {
                columns.add("Relation_TypeID");
                values.add(1);
            }
            columns.add("LastUpdate_Datetime");
            values.add(new Timestamp(System.currentTimeMillis()));
            columns.add("LastUpdate_UserID");
            values.add(1);
        }

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                tableName,
                String.join(", ", columns),
                String.join(", ", columns.stream().map(c -> "?").toArray(String[]::new)));

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            ps.executeUpdate();
        }
    }

    /**
     * Get the next available ID for tables without AUTO_INCREMENT
     */
    private int getNextId(Connection conn, String tableName) throws SQLException {
        String sql = String.format("SELECT COALESCE(MAX(ID), 0) + 1 AS next_id FROM %s", tableName);
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("next_id");
            }
            return 1; // Default to 1 if table is empty
        }
    }

    private void deleteRelationship(Connection conn, String tableName, JsonObject rowData) throws SQLException {
        // Build dynamic DELETE based on the fields present in rowData
        List<String> whereClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        rowData.entrySet().forEach(entry -> {
            String key = entry.getKey();
            if (!key.equals("operation") && !key.equals("row_number")) {
                whereClauses.add(key + " = ?");
                if (entry.getValue().isJsonNull()) {
                    values.add(null);
                } else if (entry.getValue().isJsonPrimitive()) {
                    if (entry.getValue().getAsJsonPrimitive().isNumber()) {
                        values.add(entry.getValue().getAsInt());
                    } else {
                        values.add(entry.getValue().getAsString());
                    }
                }
            }
        });

        String sql = String.format("DELETE FROM %s WHERE %s",
                tableName,
                String.join(" AND ", whereClauses));

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            ps.executeUpdate();
        }
    }

    @Override
    public String getEntityName() {
        return entityName;
    }

    @Override
    public boolean supports(String entityName) {
        return this.entityName.equalsIgnoreCase(entityName);
    }
}
