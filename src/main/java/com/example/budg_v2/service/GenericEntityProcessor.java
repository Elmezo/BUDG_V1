package com.example.budg_v2.service;

import com.example.budg_v2.bulk.objects.BulkUploadBroadcaster;
import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Generic processor for unknown entities using database schema introspection
 * Automatically discovers table structure and processes data dynamically
 */
public class GenericEntityProcessor implements EntityProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(GenericEntityProcessor.class);

    /** Per-table JSON keys to try before generic matching (validated Python output uses labels and *_ID). */
    private static final Map<String, Map<String, String[]>> TABLE_COLUMN_JSON_ALIASES = new HashMap<>();
    private static final Map<String, String[]> COMMON_COLUMN_JSON_ALIASES = new LinkedHashMap<>();

    static {
        COMMON_COLUMN_JSON_ALIASES.put("primaryname", new String[]{
            "PrimaryName", "Name", "Project Name", "Product Name", "Committee Name"
        });
        COMMON_COLUMN_JSON_ALIASES.put("refnumber", new String[]{
            "RefNumber", "Reference", "Ref.", "refNumber"
        });
        COMMON_COLUMN_JSON_ALIASES.put("description", new String[]{
            "Description", "Project Description", "Product Description"
        });
        COMMON_COLUMN_JSON_ALIASES.put("is_public", new String[]{
            "BUDG Viewing_ID", "IsPublic_ID", "is_public_id"
        });
        COMMON_COLUMN_JSON_ALIASES.put("status", new String[]{
            "BUDG Status_ID", "Status_ID"
        });
        COMMON_COLUMN_JSON_ALIASES.put("lifecycle_status", new String[]{
            "Lifecycle_ID", "Project Lifecycle_ID"
        });
        COMMON_COLUMN_JSON_ALIASES.put("startdate", new String[]{"Start Date", "StartDate", "start_date"});
        COMMON_COLUMN_JSON_ALIASES.put("enddate", new String[]{"End Date", "EndDate", "end_date"});

        Map<String, String[]> process = new LinkedHashMap<>();
        process.put("step_type", new String[]{"Step Type_ID", "Step Type ID"});
        process.put("type", new String[]{"Type_ID"});
        process.put("duration_type", new String[]{"Duration Type_ID"});
        process.put("lifecycle_status", new String[]{"Lifecycle_ID"});
        process.put("processclass_id", new String[]{"Classification_ID"});
        process.put("processautomation_id", new String[]{"Automation_ID"});
        process.put("ispublic", new String[]{"BUDG Viewing_ID", "IsPublic_ID"});
        process.put("status", new String[]{"BUDG Status_ID", "Status_ID"});
        process.put("primaryname", new String[]{"Name"});
        process.put("refnumber", new String[]{"Ref."});
        process.put("cancreate", new String[]{"Create Permission"});
        process.put("canread", new String[]{"Read Permission"});
        process.put("canupdate", new String[]{"Update Permission"});
        process.put("candelete", new String[]{"Delete Permission"});
        process.put("canarchive", new String[]{"Archive Permission"});
        process.put("duration", new String[]{"Duration"});
        TABLE_COLUMN_JSON_ALIASES.put("process", process);

        Map<String, String[]> project = new LinkedHashMap<>();
        project.put("primaryname", new String[]{"Project Name"});
        project.put("refnumber", new String[]{"Reference"});
        project.put("description", new String[]{"Project Description"});
        project.put("project_type", new String[]{"Project Type_ID"});
        project.put("classification", new String[]{"Classification_ID"});
        project.put("rag", new String[]{"RAG_ID"});
        project.put("is_public", new String[]{"BUDG Viewing_ID"});
        project.put("status", new String[]{"BUDG Status_ID"});
        project.put("lifecycle_status", new String[]{"Project Lifecycle_ID"});
        TABLE_COLUMN_JSON_ALIASES.put("project", project);

        Map<String, String[]> policy = new LinkedHashMap<>();
        policy.put("primaryname", new String[]{"Name"});
        policy.put("refnumber", new String[]{"Ref."});
        policy.put("parentid", new String[]{"Parent_ID", "Parent ID", "Parent"});
        policy.put("ispublic", new String[]{"BUDG Viewing_ID", "IsPublic_ID"});
        policy.put("status", new String[]{"BUDG Status_ID", "Status_ID"});
        policy.put("lifecycle_status", new String[]{"Lifecycle_ID"});
        policy.put("policy_type", new String[]{"Type_ID"});
        TABLE_COLUMN_JSON_ALIASES.put("policy", policy);

        Map<String, String[]> product = new LinkedHashMap<>();
        product.put("primaryname", new String[]{"PrimaryName"});
        product.put("lifecycle_status", new String[]{"Lifecycle_ID"});
        product.put("status", new String[]{"Status_ID"});
        product.put("is_public", new String[]{"IsPublic_ID", "BUDG Viewing_ID"});
        TABLE_COLUMN_JSON_ALIASES.put("product", product);

        Map<String, String[]> client = new LinkedHashMap<>();
        client.put("ispublic", new String[]{"BUDG Viewing_ID", "IsPublic_ID"});
        client.put("status", new String[]{"BUDG Status_ID", "Status_ID"});
        TABLE_COLUMN_JSON_ALIASES.put("client", client);

        Map<String, String[]> legal = new LinkedHashMap<>();
        legal.put("shortname", new String[]{"ShortName"});
        legal.put("longname", new String[]{"LongName"});
        legal.put("is_public", new String[]{"Is_Public_ID", "BUDG Viewing_ID"});
        legal.put("status", new String[]{"BUDG Status_ID", "Status_ID"});
        TABLE_COLUMN_JSON_ALIASES.put("legal", legal);
    }

    private static List<String> collectJsonAliasCandidates(String tableName, String columnName) {
        String colLower = columnName.toLowerCase(Locale.ROOT);
        String tbl = tableName.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        Map<String, String[]> perTable = TABLE_COLUMN_JSON_ALIASES.get(tbl);
        if (perTable != null) {
            String[] a = perTable.get(colLower);
            if (a != null) {
                for (String s : a) {
                    if (!out.contains(s)) {
                        out.add(s);
                    }
                }
            }
        }
        String[] common = COMMON_COLUMN_JSON_ALIASES.get(colLower);
        if (common != null) {
            for (String s : common) {
                if (!out.contains(s)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static String findJsonKeyCaseInsensitive(JsonObject rowData, String wantedKey) {
        if (wantedKey == null) {
            return null;
        }
        String w = wantedKey.trim();
        if (rowData.has(w)) {
            return w;
        }
        for (String k : rowData.keySet()) {
            if (k != null && k.trim().equalsIgnoreCase(w)) {
                return k;
            }
        }
        return null;
    }

    private static boolean isJsonValueCompatibleWithSqlType(JsonElement el, String dataType) {
        if (el == null || el.isJsonNull() || dataType == null) {
            return true;
        }
        String tl = dataType.toLowerCase(Locale.ROOT);
        if (tl.contains("int") || tl.contains("tinyint") || tl.contains("smallint") || tl.contains("bigint")) {
            if (!el.isJsonPrimitive()) {
                return false;
            }
            if (el.getAsJsonPrimitive().isNumber() || el.getAsJsonPrimitive().isBoolean()) {
                return true;
            }
            String s = el.getAsJsonPrimitive().getAsString().trim();
            try {
                Long.parseLong(s);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private String resolveJsonKey(JsonObject rowData, String columnName, String dataType, String tableName) {
        List<String> ordered = new ArrayList<>();
        ordered.add(columnName);
        for (String c : collectJsonAliasCandidates(tableName, columnName)) {
            if (!ordered.contains(c)) {
                ordered.add(c);
            }
        }
        for (String candidate : ordered) {
            String key = findJsonKeyCaseInsensitive(rowData, candidate);
            if (key == null) {
                continue;
            }
            if (rowData.get(key).isJsonNull()) {
                continue;
            }
            if (!isJsonValueCompatibleWithSqlType(rowData.get(key), dataType)) {
                continue;
            }
            return key;
        }
        String fallback = findJsonKey(rowData, columnName);
        if (fallback != null && rowData.has(fallback) && !rowData.get(fallback).isJsonNull()
                && isJsonValueCompatibleWithSqlType(rowData.get(fallback), dataType)) {
            return fallback;
        }
        return null;
    }

    private static boolean isSelfReferencingParentColumn(String tableName, String columnName) {
        String col = columnName.toLowerCase(Locale.ROOT);
        String tbl = tableName.toLowerCase(Locale.ROOT);
        return (tbl.equals("policy") && col.equals("parentid"))
                || (tbl.equals("process") && col.equals("parentid"))
                || (tbl.equals("project") && col.equals("parentid"))
                || (tbl.equals("product") && col.equals("parent_id"))
                || (tbl.equals("legal") && col.equals("parent_id"));
    }

    /**
     * Normalise FK / id values from JSON (numbers, whole doubles, numeric strings).
     */
    private static Long toLongId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private boolean shouldOmitMissingSelfParent(Connection conn, TableSchema schema, String columnName, Object value,
                                                Set<Long> idsInsertedThisBatch) throws SQLException {
        if (conn == null || schema == null) {
            return false;
        }
        if (!isSelfReferencingParentColumn(schema.tableName, columnName)) {
            return false;
        }
        Long pidObj = toLongId(value);
        if (pidObj == null) {
            return false;
        }
        long pid = pidObj;
        if (pid <= 0) {
            return true;
        }
        if (schema.primaryKeyColumn == null) {
            return false;
        }
        if (idsInsertedThisBatch != null && idsInsertedThisBatch.contains(pid)) {
            return false;
        }
        if (pkRowExists(conn, schema.tableName, schema.primaryKeyColumn, pid)) {
            return false;
        }
        logger.debug("Omitting {}.{}={} — parent row not found (stale export id after replace)", schema.tableName, columnName, pid);
        return true;
    }

    private boolean pkRowExists(Connection conn, String tableName, String pkColumn, long id) throws SQLException {
        String sql = "SELECT 1 FROM `" + tableName.replace("`", "") + "` WHERE `" + pkColumn.replace("`", "") + "` = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private final JobDAO jobDAO = new JobDAO();
    private final Map<String, TableSchema> schemaCache = new HashMap<>();
    
    @Override
    public void process(int jobId, JsonArray validatedData, int userId,
                       String errorHandling, String uploadOption, String segmentMode, String segment) {
        logger.info("Generic processor processing job {} with {} rows", jobId, validatedData.size());
        
        if (validatedData == null || validatedData.size() == 0) {
            logger.warn("No data to process for job {}", jobId);
            try {
                jobDAO.updateJobStatus(jobId, "Completed", true);
                jobDAO.updateJobProgress(jobId, "Completed", "No data to process");
            } catch (SQLException e) {
                logger.error("Error updating job status", e);
            }
            return;
        }
        
        // Get entity name from first row (if available)
        String entityName = extractEntityName(validatedData);
        String tableName = inferTableName(entityName);
        
        logger.info("Processing generic entity: {} -> table: {}", entityName, tableName);
        
        // Discover table schema
        TableSchema schema = discoverTableSchema(tableName);
        if (schema == null) {
            logger.error("Cannot discover schema for table: {}", tableName);
            try {
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Cannot discover table schema for: " + tableName);
            } catch (SQLException e) {
                logger.error("Error updating job status", e);
            }
            return;
        }
        
        // Process data
        processGenericData(jobId, validatedData, userId, errorHandling, uploadOption, schema);
    }
    
    @Override
    public String getEntityName() {
        return "Generic";
    }
    
    @Override
    public boolean supports(String entityName) {
        // Generic processor supports all entities as fallback
        return true;
    }
    
    /**
     * Extract entity name from validated data
     */
    private String extractEntityName(JsonArray validatedData) {
        if (validatedData.size() > 0) {
            JsonObject firstRow = validatedData.get(0).getAsJsonObject();
            // Try to find entity name in metadata or common fields
            if (firstRow.has("entity")) {
                return firstRow.get("entity").getAsString();
            }
        }
        return "unknown";
    }
    
    /**
     * Infer table name from entity name
     */
    private String inferTableName(String entityName) {
        if (entityName == null || entityName.isEmpty()) {
            return "unknown";
        }
        
        // Normalize entity name to table name
        String tableName = entityName.toLowerCase().trim();
        tableName = tableName.replace(" ", "_");
        tableName = tableName.replace("-", "_");
        
        // Handle common mappings
        if (tableName.equals("dataset") || tableName.equals("data_sets")) {
            return "dataset";
        } else if (tableName.equals("data sets")) {
            return "dataset";
        }
        if (tableName.equals("orgunit") || tableName.equals("org_unit")) {
            return "org_unit";
        }
        // DB table is regulatorytheme (no underscore); entity label is "Regulatory Theme"
        if (tableName.equals("regulatory_theme") || tableName.equals("regulatorytheme")) {
            return "regulatorytheme";
        }
        
        return tableName;
    }
    
    /**
     * Discover table schema using INFORMATION_SCHEMA
     */
    private TableSchema discoverTableSchema(String tableName) {
        // Check cache first
        if (schemaCache.containsKey(tableName)) {
            return schemaCache.get(tableName);
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String dbName = conn.getCatalog();
            if (dbName == null) {
                dbName = conn.getSchema();
            }
            
            // Check if table exists
            String checkTableSql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                                 "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
            boolean tableExists = false;
            try (PreparedStatement ps = conn.prepareStatement(checkTableSql)) {
                ps.setString(1, dbName);
                ps.setString(2, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    tableExists = rs.next();
                }
            }
            
            if (!tableExists) {
                logger.warn("Table {} does not exist in database {}", tableName, dbName);
                return null;
            }
            
            // Get column information
            String sql = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT, " +
                        "CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE " +
                        "FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                        "ORDER BY ORDINAL_POSITION";
            
            TableSchema schema = new TableSchema(tableName);
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dbName);
                ps.setString(2, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String columnName = rs.getString("COLUMN_NAME");
                        String dataType = rs.getString("DATA_TYPE");
                        String isNullable = rs.getString("IS_NULLABLE");
                        String columnKey = rs.getString("COLUMN_KEY");
                        
                        ColumnInfo columnInfo = new ColumnInfo();
                        columnInfo.name = columnName;
                        columnInfo.dataType = dataType;
                        columnInfo.nullable = "YES".equalsIgnoreCase(isNullable);
                        columnInfo.isPrimaryKey = "PRI".equalsIgnoreCase(columnKey);
                        // COLUMN_KEY is PRI/UNI/MUL only; EXTRA has auto_increment. We keep this false so
                        // bulk inserts can supply explicit PKs from the template (needed for ParentID chains).
                        columnInfo.isAutoIncrement = columnKey != null && columnKey.contains("auto_increment");
                        columnInfo.columnDefault = rs.getString("COLUMN_DEFAULT");
                        
                        // Skip system columns
                        if (isSystemColumn(columnName)) {
                            continue;
                        }
                        
                        schema.columns.put(columnName.toLowerCase(), columnInfo);
                        schema.columnNames.add(columnName);
                        
                        if (columnInfo.isPrimaryKey) {
                            schema.primaryKeyColumn = columnName;
                        }
                    }
                }
            }
            
            // Cache schema
            schemaCache.put(tableName, schema);
            logger.info("Discovered schema for table {}: {} columns", tableName, schema.columns.size());
            
            return schema;
            
        } catch (SQLException e) {
            logger.error("Error discovering schema for table {}: {}", tableName, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Check if column is a system column (should be skipped in generic processing)
     */
    private boolean isSystemColumn(String columnName) {
        String lower = columnName.toLowerCase();
        return lower.equals("id") || 
               lower.contains("created") || 
               lower.contains("updated") || 
               lower.contains("deleted") ||
               lower.contains("datetime") ||
               lower.contains("timestamp") ||
               lower.contains("userid") ||
               lower.contains("user_id");
    }
    
    /**
     * Process generic data
     */
    private void processGenericData(int jobId, JsonArray validatedData, int userId,
                                   String errorHandling, String uploadOption, TableSchema schema) {
        int insertedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;
        int totalRows = validatedData.size();
        
        Connection conn = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            Set<Long> insertedPkThisBatch = new HashSet<>();
            
            jobDAO.updateJobStatus(jobId, "Processing", false);
            jobDAO.createJobProgress(jobId, 30, "Processing", "Starting generic data insertion...");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30, 
                "Starting data insertion...", 0, 0, 0, 0);
            
            for (int i = 0; i < validatedData.size(); i++) {
                JsonObject rowData = validatedData.get(i).getAsJsonObject();
                String operation = rowData.has("operation") ? rowData.get("operation").getAsString() : "INSERT";
                int rowNumber = rowData.has("row_number") ? rowData.get("row_number").getAsInt() : i + 1;
                
                if (i % 10 == 0) {
                    int progressPercent = 30 + (int)((i / (double)totalRows) * 65);
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercent,
                        String.format("Processing row %d of %d", i + 1, totalRows),
                        insertedCount, updatedCount, deletedCount, failedCount);
                }
                
                try {
                    if ("INSERT".equals(operation)) {
                        insertGenericRow(conn, rowData, schema, userId, insertedPkThisBatch);
                        insertedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, schema.tableName, "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                            "Row inserted successfully", "info");
                            
                    } else if ("UPDATE".equals(operation)) {
                        updateGenericRow(conn, rowData, schema, userId, insertedPkThisBatch);
                        updatedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, schema.tableName, "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                            "Row updated successfully", "info");
                            
                    } else if ("DELETE".equals(operation)) {
                        deleteGenericRow(conn, rowData, schema);
                        deletedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, schema.tableName, "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                            "Row deleted successfully", "info");
                    }
                    
                } catch (Exception e) {
                    logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                    failedCount++;
                    int reportItemId = jobDAO.createJobReportItem(jobId, schema.tableName, "failed", rowNumber);
                    jobDAO.createJobReportItemMessage(reportItemId, "ERROR",
                        "Failed to process: " + e.getMessage(), "error");
                    
                    if ("Cancel on Warning".equalsIgnoreCase(errorHandling)) {
                        conn.rollback();
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed",
                            "Processing failed at row " + rowNumber + ": " + e.getMessage());
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                            "Processing failed at row " + rowNumber, insertedCount, updatedCount, deletedCount, failedCount);
                        return;
                    }
                }
            }
            
            conn.commit();
            jobDAO.updateJobStatus(jobId, "Completed", true);
            jobDAO.updateJobProgress(jobId, "Completed",
                String.format("Processing complete: %d inserted, %d updated, %d deleted, %d failed",
                    insertedCount, updatedCount, deletedCount, failedCount));
            
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                "Processing complete", insertedCount, updatedCount, deletedCount, failedCount);
            
            logger.info("Generic processing job {} completed: {} inserted, {} updated, {} deleted, {} failed",
                jobId, insertedCount, updatedCount, deletedCount, failedCount);
            
        } catch (Exception e) {
            logger.error("Error processing generic data for job {}", jobId, e);
            try {
                if (conn != null) conn.rollback();
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Processing error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                    "Processing failed: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);
            } catch (Exception e2) {
                logger.error("Error updating job status after failure", e2);
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
    
    /**
     * Insert a generic row
     */
    private void trackInsertedPrimaryKey(PreparedStatement ps, TableSchema schema, JsonObject rowData,
                                         Set<Long> insertedPkThisBatch) throws SQLException {
        if (insertedPkThisBatch == null || schema.primaryKeyColumn == null) {
            return;
        }
        long id = -1;
        try (ResultSet gk = ps.getGeneratedKeys()) {
            if (gk.next()) {
                id = gk.getLong(1);
            }
        }
        if (id <= 0) {
            ColumnInfo pkInfo = schema.columns.get(schema.primaryKeyColumn.toLowerCase());
            if (pkInfo != null) {
                String pkKey = resolveJsonKey(rowData, schema.primaryKeyColumn, pkInfo.dataType, schema.tableName);
                if (pkKey != null) {
                    Object v = getValueFromJson(rowData, pkKey, pkInfo.dataType);
                    Long parsed = toLongId(v);
                    if (parsed != null && parsed > 0) {
                        id = parsed;
                    }
                }
            }
        }
        if (id > 0) {
            insertedPkThisBatch.add(id);
        }
    }

    private void insertGenericRow(Connection conn, JsonObject rowData, TableSchema schema, int userId,
                                  Set<Long> insertedPkThisBatch) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        for (String columnName : schema.columnNames) {
            ColumnInfo columnInfo = schema.columns.get(columnName.toLowerCase());
            if (columnInfo == null || columnInfo.isAutoIncrement) {
                continue; // Skip auto-increment columns
            }
            
            String jsonKey = resolveJsonKey(rowData, columnName, columnInfo.dataType, schema.tableName);
            if (jsonKey != null && rowData.has(jsonKey) && !rowData.get(jsonKey).isJsonNull()) {
                Object value = getValueFromJson(rowData, jsonKey, columnInfo.dataType);
                if (value == null) {
                    continue;
                }
                if (shouldOmitMissingSelfParent(conn, schema, columnName, value, insertedPkThisBatch)) {
                    continue;
                }
                columns.add(columnName);
                placeholders.add("?");
                values.add(value);
            } else if (!columnInfo.nullable && columnInfo.columnDefault == null) {
                // Required field missing - use default or throw error
                logger.warn("Required column {} is missing, using null", columnName);
            }
        }
        
        if (columns.isEmpty()) {
            throw new SQLException("No columns to insert");
        }
        
        String sql = "INSERT INTO " + schema.tableName + " (" + 
                    String.join(", ", columns) + ") VALUES (" + 
                    String.join(", ", placeholders) + ")";
        
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.size(); i++) {
                setParameter(ps, i + 1, values.get(i), schema.columns.get(columns.get(i).toLowerCase()).dataType);
            }
            ps.executeUpdate();
            trackInsertedPrimaryKey(ps, schema, rowData, insertedPkThisBatch);
        }
    }
    
    /**
     * Update a generic row
     */
    private void updateGenericRow(Connection conn, JsonObject rowData, TableSchema schema, int userId,
                                  Set<Long> insertedPkThisBatch) throws SQLException {
        if (schema.primaryKeyColumn == null) {
            throw new SQLException("Cannot update: no primary key column found");
        }
        
        String primaryKeyValue = findJsonKey(rowData, schema.primaryKeyColumn);
        if (primaryKeyValue == null || !rowData.has(primaryKeyValue)) {
            throw new SQLException("Primary key value not found for update");
        }
        
        List<String> updates = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        for (String columnName : schema.columnNames) {
            if (columnName.equalsIgnoreCase(schema.primaryKeyColumn)) {
                continue; // Skip primary key in update
            }
            
            ColumnInfo columnInfo = schema.columns.get(columnName.toLowerCase());
            if (columnInfo == null || columnInfo.isAutoIncrement) {
                continue;
            }
            
            String jsonKey = resolveJsonKey(rowData, columnName, columnInfo.dataType, schema.tableName);
            if (jsonKey != null && rowData.has(jsonKey) && !rowData.get(jsonKey).isJsonNull()) {
                Object value = getValueFromJson(rowData, jsonKey, columnInfo.dataType);
                if (value == null) {
                    continue;
                }
                if (shouldOmitMissingSelfParent(conn, schema, columnName, value, insertedPkThisBatch)) {
                    continue;
                }
                updates.add(columnName + " = ?");
                values.add(value);
            }
        }
        
        if (updates.isEmpty()) {
            throw new SQLException("No columns to update");
        }
        
        // Add primary key value at the end
        Object pkValue = getValueFromJson(rowData, findJsonKey(rowData, schema.primaryKeyColumn), 
                                         schema.columns.get(schema.primaryKeyColumn.toLowerCase()).dataType);
        values.add(pkValue);
        
        String sql = "UPDATE " + schema.tableName + " SET " + 
                    String.join(", ", updates) + 
                    " WHERE " + schema.primaryKeyColumn + " = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                String dataType = i < updates.size() ? 
                    schema.columns.get(updates.get(i).split(" =")[0].toLowerCase()).dataType :
                    schema.columns.get(schema.primaryKeyColumn.toLowerCase()).dataType;
                setParameter(ps, i + 1, values.get(i), dataType);
            }
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Update failed: no rows affected");
            }
        }
    }
    
    /**
     * Delete a generic row (soft delete if possible)
     */
    private void deleteGenericRow(Connection conn, JsonObject rowData, TableSchema schema) throws SQLException {
        if (schema.primaryKeyColumn == null) {
            throw new SQLException("Cannot delete: no primary key column found");
        }
        
        String primaryKeyValue = findJsonKey(rowData, schema.primaryKeyColumn);
        if (primaryKeyValue == null || !rowData.has(primaryKeyValue)) {
            throw new SQLException("Primary key value not found for delete");
        }
        
        Object pkValue = getValueFromJson(rowData, primaryKeyValue, 
                                         schema.columns.get(schema.primaryKeyColumn.toLowerCase()).dataType);
        
        // Try soft delete first (look for deleted column)
        String deletedColumn = findDeletedColumn(schema);
        if (deletedColumn != null) {
            String sql = "UPDATE " + schema.tableName + " SET " + deletedColumn + " = NOW() WHERE " + 
                        schema.primaryKeyColumn + " = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setParameter(ps, 1, pkValue, schema.columns.get(schema.primaryKeyColumn.toLowerCase()).dataType);
                int affected = ps.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("Delete failed: no rows affected");
                }
            }
        } else {
            // Hard delete
            String sql = "DELETE FROM " + schema.tableName + " WHERE " + schema.primaryKeyColumn + " = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setParameter(ps, 1, pkValue, schema.columns.get(schema.primaryKeyColumn.toLowerCase()).dataType);
                int affected = ps.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("Delete failed: no rows affected");
                }
            }
        }
    }
    
    /**
     * Find JSON key that matches column name (case-insensitive, with variations)
     */
    private String findJsonKey(JsonObject rowData, String columnName) {
        // Direct match
        if (rowData.has(columnName)) {
            return columnName;
        }
        
        // Case-insensitive match
        for (String key : rowData.keySet()) {
            if (key.equalsIgnoreCase(columnName)) {
                return key;
            }
        }
        
        // Try with spaces/underscores
        String normalized = columnName.replace("_", " ").replace("-", " ");
        for (String key : rowData.keySet()) {
            if (key.replace("_", " ").replace("-", " ").equalsIgnoreCase(normalized)) {
                return key;
            }
        }
        
        return null;
    }
    
    /**
     * Get value from JSON and convert to appropriate type
     */
    private Object getValueFromJson(JsonObject rowData, String key, String dataType) {
        if (!rowData.has(key) || rowData.get(key).isJsonNull()) {
            return null;
        }
        
        try {
            if (dataType == null) {
                return rowData.get(key).getAsString();
            }
            
            String typeLower = dataType.toLowerCase();
            if (typeLower.contains("int") || typeLower.contains("tinyint") || typeLower.contains("smallint")) {
                JsonElement el = rowData.get(key);
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    String s = el.getAsJsonPrimitive().getAsString().trim();
                    if (s.isEmpty()) {
                        return null;
                    }
                    return Integer.parseInt(s);
                }
                return el.getAsInt();
            } else if (typeLower.contains("bigint")) {
                return rowData.get(key).getAsLong();
            } else if (typeLower.contains("decimal") || typeLower.contains("numeric") || typeLower.contains("float") || typeLower.contains("double")) {
                return rowData.get(key).getAsDouble();
            } else if (typeLower.contains("bool") || typeLower.contains("bit")) {
                return rowData.get(key).getAsBoolean();
            } else {
                return rowData.get(key).getAsString();
            }
        } catch (Exception e) {
            logger.warn("Cannot coerce JSON value for key {} to {}: {}", key, dataType, e.getMessage());
            return null;
        }
    }
    
    /**
     * Set parameter in PreparedStatement
     */
    private void setParameter(PreparedStatement ps, int index, Object value, String dataType) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NULL);
            return;
        }
        
        String typeLower = dataType != null ? dataType.toLowerCase() : "";
        if (typeLower.contains("int") || typeLower.contains("tinyint") || typeLower.contains("smallint")) {
            if (value instanceof Number) {
                ps.setInt(index, ((Number) value).intValue());
            } else {
                ps.setInt(index, Integer.parseInt(value.toString()));
            }
        } else if (typeLower.contains("bigint")) {
            if (value instanceof Number) {
                ps.setLong(index, ((Number) value).longValue());
            } else {
                ps.setLong(index, Long.parseLong(value.toString()));
            }
        } else if (typeLower.contains("decimal") || typeLower.contains("numeric") || typeLower.contains("float") || typeLower.contains("double")) {
            if (value instanceof Number) {
                ps.setDouble(index, ((Number) value).doubleValue());
            } else {
                ps.setDouble(index, Double.parseDouble(value.toString()));
            }
        } else if (typeLower.contains("bool") || typeLower.contains("bit")) {
            if (value instanceof Boolean) {
                ps.setBoolean(index, (Boolean) value);
            } else {
                ps.setBoolean(index, Boolean.parseBoolean(value.toString()));
            }
        } else {
            ps.setString(index, value.toString());
        }
    }
    
    /**
     * Find deleted column for soft delete
     */
    private String findDeletedColumn(TableSchema schema) {
        for (String columnName : schema.columnNames) {
            String lower = columnName.toLowerCase();
            if (lower.contains("deleted") && (lower.contains("datetime") || lower.contains("date") || lower.contains("time"))) {
                return columnName;
            }
        }
        return null;
    }
    
    /**
     * Table schema information
     */
    private static class TableSchema {
        String tableName;
        Map<String, ColumnInfo> columns = new LinkedHashMap<>();
        List<String> columnNames = new ArrayList<>();
        String primaryKeyColumn;
        
        TableSchema(String tableName) {
            this.tableName = tableName;
        }
    }
    
    /**
     * Column information
     */
    private static class ColumnInfo {
        @SuppressWarnings("unused") // mirrors map key; kept for clarity
        String name;
        String dataType;
        boolean nullable;
        boolean isPrimaryKey;
        boolean isAutoIncrement;
        String columnDefault;
    }
}

