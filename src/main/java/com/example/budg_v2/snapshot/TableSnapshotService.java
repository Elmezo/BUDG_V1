package com.example.budg_v2.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Export/import full table rows for the snapshot allowlist as a versioned ZIP (metadata.json + JSONL per table).
 */
public class TableSnapshotService {

    private static final Logger logger = LoggerFactory.getLogger(TableSnapshotService.class);
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int INSERT_BATCH_SIZE = 250;

    public void exportToZip(OutputStream rawOut, Connection conn) throws IOException, SQLException {
        List<String> allowlist = TableSnapshotAllowlist.load();
        String schema = currentSchema(conn);
        Map<String, String> lowerToActual = loadBaseTablesByLowerName(conn, schema);
        List<String> exported = new ArrayList<>();
        List<String> skippedMissing = new ArrayList<>();
        for (String low : allowlist) {
            if (lowerToActual.containsKey(low)) {
                exported.add(lowerToActual.get(low));
            } else {
                skippedMissing.add(low);
            }
        }
        Collections.sort(exported);

        JsonObject rootMeta = new JsonObject();
        rootMeta.addProperty("packageType", TableSnapshotConstants.PACKAGE_TYPE);
        rootMeta.addProperty("formatVersion", TableSnapshotConstants.FORMAT_VERSION);
        rootMeta.addProperty("exportedAt", Instant.now().toString());
        rootMeta.addProperty("databaseProduct", conn.getMetaData().getDatabaseProductName());
        rootMeta.addProperty("schema", schema);

        JsonArray skippedArr = new JsonArray();
        for (String s : skippedMissing) {
            skippedArr.add(s);
        }
        rootMeta.add("skippedAllowlistNotInDatabase", skippedArr);

        JsonArray tablesArr = new JsonArray();
        for (String tableName : exported) {
            tablesArr.add(buildTableMetadata(conn, schema, tableName));
        }
        rootMeta.add("tables", tablesArr);
        byte[] metaBytes = GSON.toJson(rootMeta).getBytes(StandardCharsets.UTF_8);

        try (ZipOutputStream zos = new ZipOutputStream(rawOut, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry(TableSnapshotConstants.METADATA_FILE));
            zos.write(metaBytes);
            zos.closeEntry();

            for (String tableName : exported) {
                zos.putNextEntry(new ZipEntry(jsonlEntryPath(tableName)));
                exportTableJsonl(conn, tableName, zos);
                zos.closeEntry();
            }
        }
        logger.info("Table snapshot export: {} tables, {} allowlist names missing in DB", exported.size(),
                skippedMissing.size());
    }

    private static String jsonlEntryPath(String tableName) {
        return "data/" + tableName.toLowerCase(Locale.ROOT) + ".jsonl";
    }

    private JsonObject buildTableMetadata(Connection conn, String schema, String tableName) throws SQLException {
        JsonObject o = new JsonObject();
        o.addProperty("name", tableName);
        o.addProperty("dataEntry", jsonlEntryPath(tableName));

        List<String> columns = new ArrayList<>();
        Set<String> binaryCols = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION")) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("DATA_TYPE");
                    columns.add(col);
                    if (isBinarySqlTypeName(dataType)) {
                        binaryCols.add(col);
                    }
                }
            }
        }

        JsonArray colArr = new JsonArray();
        for (String c : columns) {
            colArr.add(c);
        }
        o.add("columns", colArr);

        JsonArray binArr = new JsonArray();
        for (String b : binaryCols) {
            binArr.add(b);
        }
        o.add("binaryColumns", binArr);

        boolean dedupeSafe = hasUniqueOrPrimaryKey(conn, schema, tableName);
        o.addProperty("deduplicationSafe", dedupeSafe);

        JsonArray pk = loadPrimaryKeyColumns(conn, schema, tableName);
        o.add("primaryKeyColumns", pk);

        return o;
    }

    private static boolean isBinarySqlTypeName(String dataType) {
        if (dataType == null) {
            return false;
        }
        String t = dataType.toLowerCase(Locale.ROOT);
        return t.contains("blob") || t.equals("binary") || t.equals("varbinary");
    }

    private static boolean hasUniqueOrPrimaryKey(Connection conn, String schema, String table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = ? AND table_name = ? AND non_unique = 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static JsonArray loadPrimaryKeyColumns(Connection conn, String schema, String table) throws SQLException {
        JsonArray arr = new JsonArray();
        String sql = "SELECT COLUMN_NAME FROM information_schema.statistics "
                + "WHERE table_schema = ? AND table_name = ? AND index_name = 'PRIMARY' ORDER BY SEQ_IN_INDEX";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    arr.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return arr;
    }

    private void exportTableJsonl(Connection conn, String tableName, OutputStream out) throws SQLException, IOException {
        String sql = "SELECT * FROM `" + sanitizeIdentifier(tableName) + "`";
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int n = meta.getColumnCount();
            while (rs.next()) {
                JsonObject row = new JsonObject();
                for (int i = 1; i <= n; i++) {
                    String col = meta.getColumnLabel(i);
                    int sqlType = meta.getColumnType(i);
                    JsonElement cell = exportCell(rs, i, sqlType);
                    row.add(col, cell);
                }
                out.write(GSON.toJson(row).getBytes(StandardCharsets.UTF_8));
                out.write('\n');
            }
        }
    }

    private static JsonElement exportCell(ResultSet rs, int i, int sqlType) throws SQLException {
        Object val = rs.getObject(i);
        if (val == null) {
            return JsonNull.INSTANCE;
        }
        switch (sqlType) {
            case Types.BIT:
            case Types.BOOLEAN:
                return new JsonPrimitive(rs.getBoolean(i));
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
                return new JsonPrimitive(rs.getInt(i));
            case Types.BIGINT:
                return new JsonPrimitive(rs.getLong(i));
            case Types.REAL:
            case Types.FLOAT:
                return new JsonPrimitive(rs.getFloat(i));
            case Types.DOUBLE:
                return new JsonPrimitive(rs.getDouble(i));
            case Types.DECIMAL:
            case Types.NUMERIC:
                BigDecimal bd = rs.getBigDecimal(i);
                return bd == null ? JsonNull.INSTANCE : new JsonPrimitive(bd);
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
            case Types.BLOB:
                byte[] bytes = rs.getBytes(i);
                return bytes == null ? JsonNull.INSTANCE : new JsonPrimitive(Base64.getEncoder().encodeToString(bytes));
            case Types.DATE:
                java.sql.Date d = rs.getDate(i);
                return d == null ? JsonNull.INSTANCE : new JsonPrimitive(d.toLocalDate().toString());
            case Types.TIME:
                java.sql.Time t = rs.getTime(i);
                return t == null ? JsonNull.INSTANCE : new JsonPrimitive(t.toLocalTime().toString());
            case Types.TIMESTAMP:
                Timestamp ts = rs.getTimestamp(i);
                if (ts == null) {
                    return JsonNull.INSTANCE;
                }
                return new JsonPrimitive(ts.toInstant().toString());
            default:
                return new JsonPrimitive(rs.getString(i));
        }
    }

    /**
     * Import from a ZIP input stream. Caller should manage transaction/autocommit if desired.
     */
    public JsonObject importFromZip(InputStream zipIn, Connection conn, boolean replaceMode)
            throws IOException, SQLException {
        Map<String, FileHolder> files = extractZipToMemory(zipIn);
        FileHolder metaHolder = findMetadata(files);
        if (metaHolder == null) {
            throw new IOException(TableSnapshotConstants.METADATA_FILE + " not found in ZIP");
        }
        JsonObject meta = JsonParser.parseString(new String(metaHolder.bytes, StandardCharsets.UTF_8))
                .getAsJsonObject();
        validateMetadata(meta);

        JsonArray tables = meta.getAsJsonArray("tables");
        String schema = currentSchema(conn);

        JsonArray mergeWarnings = new JsonArray();
        JsonArray tableResults = new JsonArray();

        try (Statement st = conn.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS=0");
            if (replaceMode) {
                for (int i = 0; i < tables.size(); i++) {
                    JsonObject tmeta = tables.get(i).getAsJsonObject();
                    String tableName = tmeta.get("name").getAsString();
                    if (!tableExistsAsBase(conn, schema, tableName)) {
                        logger.warn("Replace: skip truncate, table missing: {}", tableName);
                        continue;
                    }
                    try {
                        st.execute("TRUNCATE TABLE `" + sanitizeIdentifier(tableName) + "`");
                    } catch (SQLException e) {
                        logger.warn("Truncate failed for {}: {}", tableName, e.getMessage());
                        throw e;
                    }
                }
            }

            for (int i = 0; i < tables.size(); i++) {
                JsonObject tmeta = tables.get(i).getAsJsonObject();
                String tableName = tmeta.get("name").getAsString();
                String dataEntry = tmeta.has("dataEntry")
                        ? tmeta.get("dataEntry").getAsString()
                        : jsonlEntryPath(tableName);
                boolean dedupeSafe = !tmeta.has("deduplicationSafe") || tmeta.get("deduplicationSafe").getAsBoolean();
                if (!replaceMode && !dedupeSafe) {
                    JsonObject w = new JsonObject();
                    w.addProperty("table", tableName);
                    w.addProperty("code", "NO_UNIQUE_KEY");
                    w.addProperty("message",
                            "Table has no PRIMARY KEY or UNIQUE index; INSERT IGNORE cannot prevent duplicate rows.");
                    mergeWarnings.add(w);
                }

                if (!tableExistsAsBase(conn, schema, tableName)) {
                    JsonObject tr = new JsonObject();
                    tr.addProperty("table", tableName);
                    tr.addProperty("status", "skipped");
                    tr.addProperty("reason", "table_not_found");
                    tableResults.add(tr);
                    continue;
                }

                List<String> metaColumns = jsonArrayToStrings(tmeta.getAsJsonArray("columns"));
                Set<String> binaryCols = new HashSet<>(jsonArrayToStrings(tmeta.getAsJsonArray("binaryColumns")));

                Map<String, Integer> liveTypes = loadLiveColumnTypes(conn, tableName);
                for (String col : metaColumns) {
                    if (!liveTypes.containsKey(col)) {
                        throw new SQLException("Column '" + col + "' from snapshot metadata not found in live table "
                                + tableName);
                    }
                }

                String jsonlKey = dataEntryKey(dataEntry);
                FileHolder dataFile = files.get(jsonlKey);
                if (dataFile == null) {
                    dataFile = files.get(jsonlKey.toLowerCase(Locale.ROOT));
                }
                if (dataFile == null) {
                    throw new IOException("Missing data file in ZIP for table " + tableName + " (expected key "
                            + jsonlKey + ")");
                }

                JsonObject stats = importTableJsonl(conn, tableName, metaColumns, binaryCols, liveTypes,
                        dataFile.bytes, replaceMode);
                stats.addProperty("table", tableName);
                tableResults.add(stats);
            }
            st.execute("SET FOREIGN_KEY_CHECKS=1");
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("mode", replaceMode ? "replace" : "merge");
        response.add("mergeWarnings", mergeWarnings);
        response.add("tables", tableResults);
        return response;
    }

    private static String dataEntryKey(String dataEntry) {
        String name = new java.io.File(dataEntry).getName();
        return name.toLowerCase(Locale.ROOT);
    }

    private JsonObject importTableJsonl(Connection conn, String tableName, List<String> columns,
            Set<String> binaryCols, Map<String, Integer> liveTypes, byte[] jsonlBytes, boolean replaceMode)
            throws SQLException, IOException {
        StringBuilder sql = new StringBuilder();
        if (replaceMode) {
            sql.append("INSERT INTO `").append(sanitizeIdentifier(tableName)).append("` (");
        } else {
            sql.append("INSERT IGNORE INTO `").append(sanitizeIdentifier(tableName)).append("` (");
        }
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("`").append(sanitizeIdentifier(columns.get(i))).append("`");
        }
        sql.append(") VALUES (");
        sql.append(String.join(", ", Collections.nCopies(columns.size(), "?")));
        sql.append(")");

        long rowsAttempted = 0;
        long rowsInserted = 0;
        long rowsSkippedDuplicate = 0;

        try (PreparedStatement ps = conn.prepareStatement(sql.toString());
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new java.io.ByteArrayInputStream(jsonlBytes), StandardCharsets.UTF_8))) {
            String line;
            int inBatch = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                rowsAttempted++;
                JsonObject row = JsonParser.parseString(line).getAsJsonObject();
                for (int c = 0; c < columns.size(); c++) {
                    String col = columns.get(c);
                    int stype = liveTypes.get(col);
                    JsonElement el = row.get(col);
                    boolean binary = binaryCols.contains(col);
                    bindParameter(ps, c + 1, stype, el, binary);
                }
                ps.addBatch();
                inBatch++;
                if (inBatch >= INSERT_BATCH_SIZE) {
                    int[] res = ps.executeBatch();
                    Counts c = countBatchResults(res);
                    rowsInserted += c.inserted;
                    rowsSkippedDuplicate += c.skipped;
                    inBatch = 0;
                }
            }
            if (inBatch > 0) {
                int[] res = ps.executeBatch();
                Counts c = countBatchResults(res);
                rowsInserted += c.inserted;
                rowsSkippedDuplicate += c.skipped;
            }
        }

        JsonObject o = new JsonObject();
        o.addProperty("status", "ok");
        o.addProperty("rowsAttempted", rowsAttempted);
        o.addProperty("rowsInserted", rowsInserted);
        o.addProperty("rowsSkippedDuplicate", rowsSkippedDuplicate);
        return o;
    }

    private static final class Counts {
        long inserted;
        long skipped;
    }

    private static Counts countBatchResults(int[] results) {
        Counts c = new Counts();
        if (results == null) {
            return c;
        }
        for (int r : results) {
            if (r >= 0) {
                if (r > 0) {
                    c.inserted += r;
                } else {
                    c.skipped += 1;
                }
            }
        }
        return c;
    }

    private static void bindParameter(PreparedStatement ps, int idx, int sqlType, JsonElement el, boolean binaryColumn)
            throws SQLException {
        if (el == null || el.isJsonNull()) {
            ps.setNull(idx, sqlType);
            return;
        }
        if (binaryColumn || sqlType == Types.BINARY || sqlType == Types.VARBINARY
                || sqlType == Types.LONGVARBINARY || sqlType == Types.BLOB) {
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                ps.setNull(idx, sqlType);
                return;
            }
            String s = el.getAsString();
            byte[] raw = Base64.getDecoder().decode(s);
            ps.setBytes(idx, raw);
            return;
        }
        switch (sqlType) {
            case Types.BIT:
            case Types.BOOLEAN:
                ps.setBoolean(idx, asBoolean(el));
                return;
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
                ps.setInt(idx, (int) asLong(el));
                return;
            case Types.BIGINT:
                ps.setLong(idx, asLong(el));
                return;
            case Types.REAL:
            case Types.FLOAT:
                ps.setFloat(idx, (float) asDouble(el));
                return;
            case Types.DOUBLE:
                ps.setDouble(idx, asDouble(el));
                return;
            case Types.DECIMAL:
            case Types.NUMERIC:
                ps.setBigDecimal(idx, new BigDecimal(el.getAsJsonPrimitive().toString()));
                return;
            case Types.DATE:
                ps.setDate(idx, java.sql.Date.valueOf(LocalDate.parse(el.getAsString())));
                return;
            case Types.TIME:
                ps.setTime(idx, java.sql.Time.valueOf(java.time.LocalTime.parse(el.getAsString())));
                return;
            case Types.TIMESTAMP:
                ps.setTimestamp(idx, parseTimestamp(el));
                return;
            default:
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    ps.setString(idx, el.getAsString());
                } else {
                    ps.setString(idx, el.toString());
                }
        }
    }

    private static Timestamp parseTimestamp(JsonElement el) throws SQLException {
        try {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                return new Timestamp(el.getAsLong());
            }
            String s = el.getAsString();
            try {
                return Timestamp.from(Instant.parse(s));
            } catch (DateTimeParseException e) {
                LocalDateTime ldt = LocalDateTime.parse(s);
                return Timestamp.valueOf(ldt);
            }
        } catch (Exception e) {
            throw new SQLException("Invalid timestamp JSON: " + el, e);
        }
    }

    private static boolean asBoolean(JsonElement el) {
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isBoolean()) {
                return p.getAsBoolean();
            }
            if (p.isNumber()) {
                return p.getAsInt() != 0;
            }
        }
        return Boolean.parseBoolean(el.getAsString());
    }

    private static long asLong(JsonElement el) {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return el.getAsLong();
        }
        return Long.parseLong(el.getAsString().trim());
    }

    private static double asDouble(JsonElement el) {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return el.getAsDouble();
        }
        return Double.parseDouble(el.getAsString().trim());
    }

    private static Map<String, Integer> loadLiveColumnTypes(Connection conn, String tableName) throws SQLException {
        Map<String, Integer> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM `" + sanitizeIdentifier(tableName) + "` LIMIT 0")) {
            ResultSetMetaData rsmd = ps.getMetaData();
            int n = rsmd.getColumnCount();
            for (int i = 1; i <= n; i++) {
                map.put(rsmd.getColumnLabel(i), rsmd.getColumnType(i));
            }
        }
        return map;
    }

    private static boolean tableExistsAsBase(Connection conn, String schema, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                + "AND TABLE_TYPE = 'BASE TABLE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void validateMetadata(JsonObject meta) throws IOException {
        if (!meta.has("packageType")
                || !TableSnapshotConstants.PACKAGE_TYPE.equals(meta.get("packageType").getAsString())) {
            throw new IOException("metadata.json packageType must be \"" + TableSnapshotConstants.PACKAGE_TYPE + "\"");
        }
        if (!meta.has("formatVersion")
                || meta.get("formatVersion").getAsInt() != TableSnapshotConstants.FORMAT_VERSION) {
            throw new IOException("Unsupported formatVersion; expected " + TableSnapshotConstants.FORMAT_VERSION);
        }
        if (!meta.has("tables")) {
            throw new IOException("metadata.json missing tables array");
        }
    }

    private static List<String> jsonArrayToStrings(JsonArray arr) {
        List<String> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (JsonElement e : arr) {
            list.add(e.getAsString());
        }
        return list;
    }

    private static String currentSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        throw new SQLException("Could not resolve current database/schema");
    }

    private static Map<String, String> loadBaseTablesByLowerName(Connection conn, String schema) throws SQLException {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT TABLE_NAME FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    map.put(name.toLowerCase(Locale.ROOT), name);
                }
            }
        }
        return map;
    }

    private static final long MAX_ZIP_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 2000;

    private static Map<String, FileHolder> extractZipToMemory(InputStream zipIn) throws IOException {
        Map<String, FileHolder> out = new HashMap<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zis = new ZipInputStream(zipIn, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw new IOException("ZIP has too many entries");
                }
                String name = entry.getName();
                if (name.contains("..")) {
                    throw new IOException("Invalid ZIP path");
                }
                byte[] buf = zis.readAllBytes();
                total += buf.length;
                if (total > MAX_ZIP_BYTES) {
                    throw new IOException("ZIP uncompressed size exceeds limit");
                }
                String key = new java.io.File(name).getName().toLowerCase(Locale.ROOT);
                out.put(key, new FileHolder(buf));
                zis.closeEntry();
            }
        }
        return out;
    }

    private static FileHolder findMetadata(Map<String, FileHolder> files) {
        FileHolder f = files.get(TableSnapshotConstants.METADATA_FILE.toLowerCase(Locale.ROOT));
        if (f != null) {
            return f;
        }
        for (Map.Entry<String, FileHolder> e : files.entrySet()) {
            if ("metadata.json".equals(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static final class FileHolder {
        final byte[] bytes;

        FileHolder(byte[] bytes) {
            this.bytes = bytes;
        }
    }

    private static String sanitizeIdentifier(String raw) {
        if (raw == null || !raw.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + raw);
        }
        return raw;
    }
}
