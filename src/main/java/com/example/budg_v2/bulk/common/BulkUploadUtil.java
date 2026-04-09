package com.example.budg_v2.bulk.common;

import com.google.gson.JsonObject;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

/**
 * <b>File Overview</b><br>
 * {@code BulkUploadUtil} is a stateless utility class that provides
 * <em>standardized helper methods</em> shared by every bulk-upload servlet and
 * processor in the BUDG platform. It acts as a single source of truth for:
 *
 * <ul>
 * <li>Null-safe extraction of typed values from GSON {@link JsonObject}
 * instances.</li>
 * <li>Binding of potentially {@code null} Java values to JDBC
 * {@link PreparedStatement} parameters with proper {@code SQL NULL}
 * handling.</li>
 * <li>Multi-source value coalescing (first-non-null semantics).</li>
 * <li>Generating uniform JSON validation response objects.</li>
 * <li>Validating "all-or-none" and "at-least-one" identity-column
 * constraints used during UPDATE operations.</li>
 * </ul>
 *
 * <p>
 * <b>Business Capability:</b> Cross-functional Bulk Data Utilities.
 * Centralising these concerns prevents subtle bugs (e.g. binding an empty
 * string as {@code ""} instead of {@code NULL}) from being independently
 * re-implemented with slightly different semantics in each of the 20+ bulk
 * upload servlets.
 * </p>
 *
 * <p>
 * <b>Modules that depend on this class:</b>
 * </p>
 * <ul>
 * <li>Every {@code *BulkUploadServlet} in all bulk sub-packages.</li>
 * <li>{@code BulkDataProcessorService} — the generic processor uses these
 * methods for JSON field extraction and JDBC binding.</li>
 * <li>{@code BulkUploadNameValidator} and
 * {@code BulkUploadReferenceValidator} — use {@link #getString} and
 * {@link #getInteger} internally.</li>
 * </ul>
 *
 * <h3>Responsibility</h3>
 * <ul>
 * <li>JSON field extraction with type safety and graceful null handling.</li>
 * <li>JDBC parameter binding abstraction ({@code setNullable*} methods).</li>
 * <li>Multi-source value coalescing (overloaded {@code coalesce}).</li>
 * <li>Standardised JSON-based validation response generation.</li>
 * <li>Identity-column group validation for UPDATE rows.</li>
 * </ul>
 *
 * <h3>Typical Flow</h3>
 * <ol>
 * <li>Servlet calls {@link #getString} / {@link #getInteger} /
 * {@link #getBoolean} to extract field values from a parsed JSON
 * upload row.</li>
 * <li>For UPDATE rows, the servlet calls
 * {@link #validateIdentityColumnsAllOrNone} or
 * {@link #requireAtLeastOneIdentityColumn} to ensure the row provides
 * enough information to identify the target record.</li>
 * <li>For INSERT/UPDATE SQL statements the servlet calls
 * {@code setNullable*} to bind values, mapping Java {@code null} /
 * empty strings to SQL {@code NULL}.</li>
 * <li>On validation failure the servlet calls
 * {@link #createValidationError} to build the error response JSON.</li>
 * <li>On validation success the servlet calls
 * {@link #createValidationSuccess} as the pre-persist signal.</li>
 * </ol>
 *
 * @author BUDG Platform Team
 * @version 2.0
 * @since 1.0
 */
public class BulkUploadUtil {

    // -----------------------------------------------------------------------
    // JSON Extraction Helpers
    // -----------------------------------------------------------------------

    /**
     * Safely extracts a {@link String} value from a GSON {@link JsonObject}.
     *
     * <p>
     * <b>Purpose:</b> Prevents {@link NullPointerException} when accessing
     * fields that may be absent or explicitly JSON {@code null}. Returns Java
     * {@code null} in both cases, allowing callers to use a single null-check
     * rather than two separate existence and null checks.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @param obj The source {@link JsonObject}. Must not be {@code null}.
     * @param key The JSON property name to retrieve.
     * @return The string value if the key exists and its value is not JSON
     *         {@code null}; otherwise Java {@code null}.
     */
    public static String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    /**
     * Safely extracts an {@link Integer} value from a GSON {@link JsonObject}.
     *
     * <p>
     * <b>Purpose:</b> Handles the common scenario where a numeric ID is
     * transmitted as either a JSON number primitive or a quoted string (e.g.
     * {@code "123"}). Both representations are accepted and converted to
     * {@link Integer}. Returns {@code null} instead of throwing on
     * malformed values, delegating error reporting to the caller's validation
     * layer.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @param obj The source {@link JsonObject}. Must not be {@code null}.
     * @param key The JSON property name to retrieve.
     * @return The integer value, or {@code null} if the field is absent,
     *         JSON {@code null}, or not parseable as an integer.
     */
    public static Integer getInteger(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsInt();
            } catch (Exception e) {
                // Fallback: numeric ID may be sent as string (e.g. "123") — try string parse
                try {
                    String s = obj.get(key).getAsString();
                    if (s != null && !s.trim().isEmpty()) {
                        return Integer.parseInt(s.trim());
                    }
                } catch (Exception ignored) {
                    // not a parseable string — fall through to return null
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Safely extracts a {@link Boolean} value from a GSON {@link JsonObject}.
     *
     * <p>
     * <b>Purpose:</b> Provides standardised conversion for flag fields (e.g.
     * {@code IsActive}, {@code IsMandatory}). Returns {@code null} instead of
     * throwing on malformed values so the caller can apply a project-wide
     * default.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @param obj The source {@link JsonObject}. Must not be {@code null}.
     * @param key The JSON property name to retrieve.
     * @return The boolean value, or {@code null} if the field is absent, JSON
     *         {@code null}, or not representable as a boolean.
     */
    public static Boolean getBoolean(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsBoolean();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // JDBC Binding Helpers
    // -----------------------------------------------------------------------

    /**
     * Binds a nullable {@link Integer} to a JDBC {@link PreparedStatement}
     * parameter.
     *
     * <p>
     * <b>Purpose:</b> When {@code value} is {@code null}, calls
     * {@code ps.setNull(index, Types.INTEGER)} rather than accidentally
     * binding the Java default {@code 0}, which would silently corrupt
     * foreign-key columns that use {@code 0} as a sentinel value for "no
     * parent".
     * </p>
     *
     * @param ps    The {@link PreparedStatement} to bind to.
     * @param index The 1-based parameter index.
     * @param value The integer value to bind, or {@code null} to set SQL
     *              {@code NULL}.
     * @throws SQLException if a database access error occurs.
     */
    public static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    /**
     * Binds a nullable {@link String} to a JDBC {@link PreparedStatement}
     * parameter, treating blank strings ({@code ""} or whitespace-only) as
     * SQL {@code NULL}.
     *
     * <p>
     * <b>Purpose:</b> Maintains data integrity by ensuring that optional
     * text fields are stored as {@code NULL} rather than empty strings, which
     * simplifies {@code IS NULL} queries throughout the application and
     * prevents empty-string vs. {@code NULL} ambiguity in reports.
     * </p>
     *
     * @param ps    The {@link PreparedStatement} to bind to.
     * @param index The 1-based parameter index.
     * @param value The string value to bind. A {@code null} or blank value
     *              causes SQL {@code NULL} to be bound.
     * @throws SQLException if a database access error occurs.
     */
    public static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value != null && !value.trim().isEmpty()) {
            ps.setString(index, value);
        } else {
            ps.setNull(index, Types.VARCHAR);
        }
    }

    /**
     * Binds a nullable {@link Boolean} to a JDBC {@link PreparedStatement}
     * parameter.
     *
     * <p>
     * <b>Purpose:</b> Provides SQL {@code NULL} support for boolean flag
     * columns, which is necessary when the database schema distinguishes
     * between "explicitly false" and "not set".
     * </p>
     *
     * @param ps    The {@link PreparedStatement} to bind to.
     * @param index The 1-based parameter index.
     * @param value The boolean value to bind, or {@code null} to set SQL
     *              {@code NULL}.
     * @throws SQLException if a database access error occurs.
     */
    public static void setNullableBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value != null) {
            ps.setBoolean(index, value);
        } else {
            ps.setNull(index, Types.BOOLEAN);
        }
    }

    // -----------------------------------------------------------------------
    // Coalescing Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the first non-{@code null} {@link Integer} from {@code values}.
     *
     * <p>
     * <b>Purpose:</b> Implements a SQL-style {@code COALESCE} for Java
     * code — useful when an ID may be resolved from multiple sources (e.g.
     * a user-supplied ID column, a lookup by name, or a default value) and
     * only the first successful resolution should be used.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @param values Varargs array of {@link Integer} candidates.
     * @return The first non-{@code null} element, or {@code null} if all
     *         candidates are {@code null}.
     */
    public static Integer coalesce(Integer... values) {
        for (Integer value : values) {
            if (value != null)
                return value;
        }
        return null;
    }

    /**
     * Returns the first non-{@code null}, non-blank {@link String} from
     * {@code values}, trimmed of leading/trailing whitespace.
     *
     * <p>
     * <b>Purpose:</b> Implements a SQL-style {@code COALESCE} for string
     * fields — useful when a display value may come from a user-supplied
     * column or fall back to a computed default.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @param values Varargs array of {@link String} candidates.
     * @return The first non-blank element (trimmed), or {@code null} if all
     *         candidates are {@code null} or blank.
     */
    public static String coalesce(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty())
                return value.trim();
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Validation Response Builders
    // -----------------------------------------------------------------------

    /**
     * Constructs a standardised JSON error response for a validation failure.
     *
     * <p>
     * <b>Purpose:</b> Ensures all bulk validation failures produce a
     * machine-parseable response with a consistent structure that the Python
     * validator and the JavaScript upload UI both understand without ad-hoc
     * parsing.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @param message A human-readable error description displayed to the end
     *                user in the upload progress report.
     * @return A {@link JsonObject} of the form {@code {"valid":false,
     *         "error":"<message>"}}.
     */
    public static JsonObject createValidationError(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("valid", false);
        result.addProperty("error", message);
        return result;
    }

    /**
     * Constructs a standardised JSON success response for a passed validation.
     *
     * <p>
     * <b>Purpose:</b> Provides a uniform "green light" signal that all
     * validators and consumers agree on, avoiding magic strings or
     * per-servlet response shapes.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @return A {@link JsonObject} of the form {@code {"valid":true}}.
     */
    public static JsonObject createValidationSuccess() {
        JsonObject result = new JsonObject();
        result.addProperty("valid", true);
        return result;
    }

    // -----------------------------------------------------------------------
    // Identity Column Validation Helpers (for UPDATE rows)
    // -----------------------------------------------------------------------

    /**
     * Counts how many of the specified identity columns contain a non-empty
     * value in the given upload row.
     *
     * <p>
     * <b>Purpose:</b> Shared counting logic reused by
     * {@link #validateIdentityColumnsAllOrNone} and
     * {@link #requireAtLeastOneIdentityColumn} to avoid duplicating the
     * String/Integer extraction and emptiness logic.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @param rowData             A parsed JSON object representing one upload
     *                            row.
     * @param identityColumnNames The list of header names that form the
     *                            identity group (e.g. {@code ["Committee ID",
     *                            "Reference", "Committee Name"]}).
     * @return The number of identity columns that are non-empty, in the range
     *         {@code [0, identityColumnNames.size()]}.
     */
    public static int countFilledIdentityColumns(JsonObject rowData, List<String> identityColumnNames) {
        if (identityColumnNames == null || identityColumnNames.isEmpty()) {
            return 0;
        }
        int filled = 0;
        for (String col : identityColumnNames) {
            String val = getString(rowData, col);
            Integer intVal = getInteger(rowData, col);
            // A column counts as "filled" when it has either a non-blank string
            // or a non-null integer — both representations are valid depending
            // on the column's type.
            boolean hasValue = (val != null && !val.trim().isEmpty()) || (intVal != null);
            if (hasValue) {
                filled++;
            }
        }
        return filled;
    }

    /**
     * Enforces an "all-or-none" rule on a group of identity columns in an
     * UPDATE row.
     *
     * <p>
     * The rule is: a user must supply <em>all</em> identity columns or
     * <em>none</em> of them. Providing only a subset is ambiguous — the
     * system cannot determine which record the user intends to update — and
     * is therefore rejected.
     * </p>
     *
     * <p>
     * Examples:
     * </p>
     * <ul>
     * <li>All 3 columns filled → valid (full identity provided).</li>
     * <li>0 columns filled → valid (INSERT semantics or row skipped).</li>
     * <li>1 or 2 of 3 columns filled → invalid — error message returned.</li>
     * </ul>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @param rowData             A parsed JSON object representing one upload
     *                            row.
     * @param identityColumnNames The header names of the identity group.
     * @param entityDisplayName   Human-readable entity name for the error
     *                            message (e.g. {@code "Committee"}).
     * @param rowNumber           1-based row number for the error message.
     * @return A localised error message string if exactly 1 or 2 identity
     *         columns are filled; {@code null} if the row is valid (all 0 or
     *         all filled).
     */
    public static String validateIdentityColumnsAllOrNone(JsonObject rowData, List<String> identityColumnNames,
            String entityDisplayName, int rowNumber) {
        if (identityColumnNames == null || identityColumnNames.isEmpty()) {
            return null;
        }
        int filled = countFilledIdentityColumns(rowData, identityColumnNames);
        // Valid conditions: 0 (no identity supplied, treat as INSERT) or all columns
        // filled.
        if (filled == 0 || filled == identityColumnNames.size()) {
            return null;
        }
        String colList = String.join(", ", identityColumnNames);
        return "Row " + rowNumber + ": For " + entityDisplayName + " update, you must provide either all of "
                + colList + " together, or leave all empty. Partial identity columns are not allowed.";
    }

    /**
     * Returns an error message when <em>all</em> identity columns are empty
     * in an UPDATE row, meaning the system cannot identify the target record.
     *
     * <p>
     * <b>Purpose:</b> Provides a consistent "no identity" error message
     * that callers use when they have separately determined that at least one
     * identity column is required but none were supplied.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @param rowNumber           1-based row number for context.
     * @param entityDisplayName   Human-readable entity name (e.g.
     *                            {@code "Dataset"}).
     * @param identityColumnNames The header names of the identity group;
     *                            {@code null} or empty produces a generic
     *                            "identity columns" placeholder.
     * @return A localised error message describing which columns are needed.
     */
    public static String identityAllEmptyMessage(int rowNumber, String entityDisplayName,
            List<String> identityColumnNames) {
        String colList = identityColumnNames == null || identityColumnNames.isEmpty() ? "identity columns"
                : String.join(", ", identityColumnNames);
        return "Row " + rowNumber + ": You must provide at least one of " + colList
                + " to identify the record to update.";
    }

    /**
     * Validates that at least one identity column is filled for an UPDATE row.
     *
     * <p>
     * Unlike {@link #validateIdentityColumnsAllOrNone}, this variant does
     * not require <em>all</em> identity columns to be present — it is used
     * when multiple independent identifiers (e.g. database ID, reference
     * number, name) can each uniquely identify the target record on their
     * own. Consistency of multiple supplied identifiers is then verified
     * separately within the calling servlet.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @param rowData             A parsed JSON object representing one upload
     *                            row.
     * @param identityColumnNames The header names constituting the identity
     *                            group.
     * @param entityDisplayName   Human-readable entity name for the error
     *                            message.
     * @param rowNumber           1-based row number for the error message.
     * @return An error message if <em>no</em> identity column is filled;
     *         {@code null} if at least one column is present.
     */
    public static String requireAtLeastOneIdentityColumn(JsonObject rowData, List<String> identityColumnNames,
            String entityDisplayName, int rowNumber) {
        if (identityColumnNames == null || identityColumnNames.isEmpty()) {
            return null;
        }
        int filled = countFilledIdentityColumns(rowData, identityColumnNames);
        if (filled > 0) {
            return null;
        }
        return identityAllEmptyMessage(rowNumber, entityDisplayName, identityColumnNames);
    }

    /**
     * Convenience overload of {@link #validateIdentityColumnsAllOrNone} that
     * accepts an array rather than a {@link List}.
     *
     * <p>
     * Delegates directly to the List-based overload by wrapping the array
     * with {@link Arrays#asList}. A {@code null} array is treated as an
     * empty list (no validation performed).
     * </p>
     *
     * @param rowData             A parsed JSON object representing one upload
     *                            row.
     * @param identityColumnNames Array of header names for the identity group.
     * @param entityDisplayName   Human-readable entity name for the error
     *                            message.
     * @param rowNumber           1-based row number for the error message.
     * @return An error message if exactly 1 or 2 identity columns are filled;
     *         {@code null} if the row is valid.
     */
    public static String validateIdentityColumnsAllOrNone(JsonObject rowData, String[] identityColumnNames,
            String entityDisplayName, int rowNumber) {
        return validateIdentityColumnsAllOrNone(rowData,
                identityColumnNames == null ? null : Arrays.asList(identityColumnNames),
                entityDisplayName, rowNumber);
    }
}
