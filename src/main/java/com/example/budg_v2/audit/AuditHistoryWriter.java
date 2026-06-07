package com.example.budg_v2.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared writer for {facet}_audit_history rows.
 *
 * Enforces the From/To rules from the audit-trail spec:
 * <ul>
 *   <li>Added: from = NULL, to = value</li>
 *   <li>Updated: from = oldValue, to = newValue (only when they differ)</li>
 *   <li>Deleted: from = oldValue, to = NULL</li>
 *   <li>Status Change: same semantics as Updated; used for classification fields</li>
 * </ul>
 *
 * Each populated field becomes its own row. Blank values are skipped on Added
 * and Deleted so the history tab does not surface empty rows.
 */
public final class AuditHistoryWriter {

    public static final String UPDATE_TYPE_ADDED = "Added";
    public static final String UPDATE_TYPE_UPDATED = "Updated";
    public static final String UPDATE_TYPE_DELETED = "Deleted";
    public static final String UPDATE_TYPE_STATUS_CHANGE = "Status Change";

    /** Canonical event/field labels for the mandatory "Created By" row that begins every object's history. */
    public static final String EVENT_DETAILS = "Details";
    public static final String FIELD_CREATED_BY = "Created By";

    private AuditHistoryWriter() {
    }

    /**
     * Insert the mandatory "Created By" row that must be the first audit entry for any
     * newly-created object. The row uses Added semantics:
     *   field = "Created By", from = NULL, to = author, event = "Details".
     *
     * Callers should invoke this BEFORE writing any per-field Added rows so that the
     * History tab shows authorship as the first record. Blank authors are skipped
     * because we should never record a creation with no responsible user.
     *
     * @param conn       active JDBC connection (caller controls transaction)
     * @param auditTable name of the *_audit_history table for the facet (e.g. "glossary_audit_history")
     * @param objectId   primary key of the freshly created row
     * @param object     display-style facet name (e.g. "Glossary")
     * @param author     full name of the user who created the object
     * @return true if a row was written, false if the author was blank (skipped)
     */
    public static boolean logCreatedBy(Connection conn, String auditTable, int objectId,
                                       String object, String author) throws SQLException {
        if (isBlank(author)) {
            return false;
        }
        insertRow(conn, auditTable, objectId, object, EVENT_DETAILS, UPDATE_TYPE_ADDED,
                FIELD_CREATED_BY, null, author, author);
        return true;
    }

    /**
     * Convert a relationship table name (e.g. "glossary_x_client") to the
     * display-style Object column value ("Glossary X Client").
     */
    public static String toDisplayObjectName(String relationshipTable) {
        if (relationshipTable == null || relationshipTable.isEmpty()) {
            return relationshipTable;
        }
        String[] parts = relationshipTable.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            if ("x".equals(part)) {
                sb.append('X');
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.toString();
    }

    /**
     * Insert one "Added" row.
     *
     * @return true if a row was written, false if value is blank (skipped).
     */
    public static boolean logAdded(Connection conn, String auditTable, int objectId,
                                   String object, String event, String field,
                                   String value, String author) throws SQLException {
        if (isBlank(value)) {
            return false;
        }
        insertRow(conn, auditTable, objectId, object, event, UPDATE_TYPE_ADDED, field,
                null, value, author);
        return true;
    }

    /**
     * Insert one "Updated" row when oldValue != newValue. Blank-to-blank is skipped.
     *
     * @return true if a row was written, false if values are equivalent.
     */
    public static boolean logUpdated(Connection conn, String auditTable, int objectId,
                                     String object, String event, String field,
                                     String oldValue, String newValue, String author) throws SQLException {
        if (equalsNullSafe(oldValue, newValue)) {
            return false;
        }
        insertRow(conn, auditTable, objectId, object, event, UPDATE_TYPE_UPDATED, field,
                normalize(oldValue), normalize(newValue), author);
        return true;
    }

    /**
     * Insert one "Deleted" row when the previous value was not blank.
     */
    public static boolean logDeleted(Connection conn, String auditTable, int objectId,
                                     String object, String event, String field,
                                     String oldValue, String author) throws SQLException {
        if (isBlank(oldValue)) {
            return false;
        }
        insertRow(conn, auditTable, objectId, object, event, UPDATE_TYPE_DELETED, field,
                oldValue, null, author);
        return true;
    }

    /**
     * Insert a "Status Change" row. Used for classification / status fields.
     * On create, pass oldValue = null. On update, pass both values; equal values are skipped.
     */
    public static boolean logStatusChange(Connection conn, String auditTable, int objectId,
                                          String object, String event, String field,
                                          String oldValue, String newValue, String author) throws SQLException {
        if (equalsNullSafe(oldValue, newValue)) {
            return false;
        }
        insertRow(conn, auditTable, objectId, object, event, UPDATE_TYPE_STATUS_CHANGE, field,
                normalize(oldValue), normalize(newValue), author);
        return true;
    }

    /**
     * Bulk insert "Added" rows from a Map preserving insertion order. Blanks are skipped.
     * Useful for relationship inserts where many fields are populated in one operation.
     *
     * @return number of rows written
     */
    public static int logAddedFields(Connection conn, String auditTable, int objectId,
                                     String object, String event,
                                     Map<String, String> fieldToValue, String author) throws SQLException {
        if (fieldToValue == null || fieldToValue.isEmpty()) return 0;
        int written = 0;
        for (Map.Entry<String, String> entry : fieldToValue.entrySet()) {
            if (logAdded(conn, auditTable, objectId, object, event, entry.getKey(), entry.getValue(), author)) {
                written++;
            }
        }
        return written;
    }

    /**
     * Bulk insert "Deleted" rows from a Map. Useful for relationship deletes.
     */
    public static int logDeletedFields(Connection conn, String auditTable, int objectId,
                                       String object, String event,
                                       Map<String, String> fieldToValue, String author) throws SQLException {
        if (fieldToValue == null || fieldToValue.isEmpty()) return 0;
        int written = 0;
        for (Map.Entry<String, String> entry : fieldToValue.entrySet()) {
            if (logDeleted(conn, auditTable, objectId, object, event, entry.getKey(), entry.getValue(), author)) {
                written++;
            }
        }
        return written;
    }

    /**
     * Convenience builder for a small ordered field map.
     */
    public static Map<String, String> fields() {
        return new LinkedHashMap<>();
    }

    private static void insertRow(Connection conn, String auditTable, int objectId,
                                  String object, String event, String updateType, String field,
                                  String fromValue, String toValue, String author) throws SQLException {
        String sql = "INSERT INTO `" + auditTable + "`"
                + " (id, object, event, updateType, field, `from`, `to`, author, date, lastChange)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.NO_GENERATED_KEYS)) {
            ps.setInt(1, objectId);
            ps.setString(2, object);
            ps.setString(3, event);
            ps.setString(4, updateType);
            ps.setString(5, field);
            if (fromValue == null) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, fromValue);
            }
            if (toValue == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, toValue);
            }
            ps.setString(8, author);
            ps.executeUpdate();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private static boolean equalsNullSafe(String a, String b) {
        String na = a == null ? "" : a.trim();
        String nb = b == null ? "" : b.trim();
        return na.equals(nb);
    }
}
