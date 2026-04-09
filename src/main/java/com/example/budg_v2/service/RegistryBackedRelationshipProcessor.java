package com.example.budg_v2.service;

import com.example.budg_v2.bulk.objects.BulkUploadBroadcaster;
import com.example.budg_v2.bulk.relationships.RelationshipConfigRegistry;
import com.example.budg_v2.bulk.relationships.config.RelationshipConfig;
import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Inserts relationship rows using {@link RelationshipConfigRegistry} (any registered junction table).
 */
public class RegistryBackedRelationshipProcessor implements EntityProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RegistryBackedRelationshipProcessor.class);

    private final JobDAO jobDAO = new JobDAO();
    private final String registryKey;

    public RegistryBackedRelationshipProcessor(String registryKey) {
        this.registryKey = registryKey;
    }

    @Override
    public void process(int jobId, JsonArray validatedData, int userId, String errorHandling,
            String uploadOption, String segmentMode, String segment) {

        RelationshipConfig rc = RelationshipConfigRegistry.getConfig(registryKey);
        if (rc == null) {
            logger.error("No RelationshipConfig for key {}", registryKey);
            fail(jobId, "Unknown relationship key: " + registryKey);
            return;
        }

        String tableName = rc.getTableName();
        if (validatedData == null || validatedData.size() == 0) {
            try {
                jobDAO.updateJobStatus(jobId, "Completed", true);
                jobDAO.updateJobProgress(jobId, "Completed", "No rows to process");
            } catch (SQLException e) {
                logger.error("Error updating job", e);
            }
            return;
        }

        int inserted = 0;
        int deleted = 0;
        int failed = 0;
        Connection conn = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            jobDAO.updateJobStatus(jobId, "Processing", false);
            jobDAO.createJobProgress(jobId, 30, "Processing", "Inserting relationships...");

            for (int i = 0; i < validatedData.size(); i++) {
                JsonObject row = validatedData.get(i).getAsJsonObject();
                String operation = row.has("operation") ? row.get("operation").getAsString() : "INSERT";
                int rowNumber = row.has("row_number") ? row.get("row_number").getAsInt()
                        : (row.has("rowNumber") ? row.get("rowNumber").getAsInt() : i + 1);

                try {
                    if ("INSERT".equals(operation)) {
                        insertRow(conn, tableName, row, rc, userId);
                        inserted++;
                    } else if ("DELETE".equals(operation)) {
                        deleteRow(conn, tableName, row, rc);
                        deleted++;
                    }
                } catch (SQLException e) {
                    logger.error("Row {}: {}", rowNumber, e.getMessage(), e);
                    failed++;
                    if ("STOP_ON_ERROR".equals(errorHandling)) {
                        throw e;
                    }
                }
            }

            conn.commit();
            jobDAO.updateJobStatus(jobId, "Completed", true);
            jobDAO.createJobProgress(jobId, 100, "Completed",
                    String.format("Done: %d inserted, %d deleted, %d failed", inserted, deleted, failed));
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100, "Import completed",
                    inserted, 0, deleted, failed);

        } catch (Exception e) {
            logger.error("Relationship job {} failed", jobId, e);
            try {
                if (conn != null) {
                    conn.rollback();
                }
                fail(jobId, e.getMessage());
            } catch (SQLException ex) {
                logger.error("Rollback failed", ex);
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

    private void insertRow(Connection conn, String tableName, JsonObject row, RelationshipConfig rc, int userId)
            throws SQLException {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        row.entrySet().forEach(entry -> {
            String key = entry.getKey();
            if ("operation".equals(key) || "row_number".equals(key) || "rowNumber".equalsIgnoreCase(key)
                    || "ID".equalsIgnoreCase(key)) {
                return;
            }
            columns.add(key);
            com.google.gson.JsonElement v = entry.getValue();
            if (v.isJsonNull()) {
                values.add(null);
            } else if (v.getAsJsonPrimitive().isNumber()) {
                values.add(v.getAsInt());
            } else {
                values.add(v.getAsString());
            }
        });

        if (columns.isEmpty()) {
            throw new SQLException("No columns to insert into " + tableName);
        }

        if ("glossary_x_glossary".equalsIgnoreCase(tableName)) {
            int nextId = nextId(conn, tableName);
            columns.add(0, "ID");
            values.add(0, nextId);
            if (!columnListContains(columns, "RelationType")) {
                columns.add("RelationType");
                values.add(1);
            }
            columns.add("CreateDatetime");
            values.add(new Timestamp(System.currentTimeMillis()));
            columns.add("LastUpdateDatetime");
            values.add(new Timestamp(System.currentTimeMillis()));
            columns.add("LastUpdateUser_ID");
            values.add(1);
        } else if ("glossary_x_system".equalsIgnoreCase(tableName)) {
            if (!columnListContains(columns, "Relation_TypeID")) {
                columns.add("Relation_TypeID");
                values.add(1);
            }
            columns.add("LastUpdate_Datetime");
            values.add(new Timestamp(System.currentTimeMillis()));
            columns.add("LastUpdate_UserID");
            values.add(userId > 0 ? userId : 1);
        }

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName,
                String.join(", ", columns),
                String.join(", ", columns.stream().map(c -> "?").toArray(String[]::new)));

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            ps.executeUpdate();
        }
    }

    private static boolean columnListContains(List<String> cols, String name) {
        for (String c : cols) {
            if (c.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void deleteRow(Connection conn, String tableName, JsonObject row, RelationshipConfig rc)
            throws SQLException {
        List<String> where = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        addWhere(row, rc.getEntityAIdColumn(), where, vals);
        addWhere(row, rc.getEntityBIdColumn(), where, vals);
        if (where.isEmpty()) {
            throw new SQLException("No FK columns for DELETE on " + tableName);
        }
        String sql = "DELETE FROM " + tableName + " WHERE " + String.join(" AND ", where);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < vals.size(); i++) {
                ps.setObject(i + 1, vals.get(i));
            }
            ps.executeUpdate();
        }
    }

    private static void addWhere(JsonObject row, String col, List<String> where, List<Object> vals) {
        if (col == null || col.isEmpty() || !row.has(col) || row.get(col).isJsonNull()) {
            return;
        }
        where.add(col + " = ?");
        vals.add(jsonPrimitiveToObject(row.get(col)));
    }

    private static Object jsonPrimitiveToObject(com.google.gson.JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.getAsJsonPrimitive().isNumber()) {
            return el.getAsInt();
        }
        return el.getAsString();
    }

    private static int nextId(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COALESCE(MAX(ID), 0) + 1 AS next_id FROM " + tableName;
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("next_id");
            }
            return 1;
        }
    }

    private void fail(int jobId, String msg) {
        try {
            jobDAO.updateJobStatus(jobId, "Failed", true);
            jobDAO.updateJobProgress(jobId, "Failed", msg);
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100, msg);
        } catch (SQLException e) {
            logger.error("Error updating failed job", e);
        }
    }

    @Override
    public String getEntityName() {
        return registryKey;
    }

    @Override
    public boolean supports(String entityName) {
        return registryKey.equalsIgnoreCase(MigrationTargetRefResolver.normalizeRegistryKey(entityName));
    }
}
