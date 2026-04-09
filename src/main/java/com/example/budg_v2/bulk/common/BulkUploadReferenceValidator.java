package com.example.budg_v2.bulk.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * <b>File Overview</b><br>
 * {@code BulkUploadReferenceValidator} enforces <em>reference-number
 * uniqueness</em> during bulk upload operations across every major entity
 * (facet) type in the BUDG platform. It mirrors the design of
 * {@link BulkUploadNameValidator} but focuses on the distinct "reference
 * number" field (e.g. {@code RefNumber}, {@code AssetID}) rather than the
 * human-readable name.
 *
 * <p>
 * <b>Business Capability:</b> Bulk Data Governance — Reference Integrity
 * Validation. Reference numbers are the system-external identifiers used
 * for regulatory reporting, audit trails, and cross-system integration.
 * Duplicate references corrupt those downstream flows and make audit lookups
 * ambiguous.
 * </p>
 *
 * <p>
 * <b>Modules that depend on this class:</b>
 * </p>
 * <ul>
 * <li>All per-facet {@code *BulkUploadServlet} classes (e.g.
 * {@code DatasetBulkUploadServlet}, {@code ProcessBulkUploadServlet})
 * that allow users to supply a reference number in their upload
 * template.</li>
 * <li>Reference-number auto-generation logic in servlets that calls
 * {@link #getBatchRefsForFacet} to ensure generated references do not
 * collide with refs already assigned in earlier rows of the same
 * file.</li>
 * </ul>
 *
 * <h3>Responsibility</h3>
 * <ul>
 * <li>Maintain an in-memory set of references encountered in the current
 * upload file, partitioned by facet (and optionally by segment) to
 * avoid cross-entity false positives.</li>
 * <li>Execute parameterised SQL queries to detect pre-existing references in
 * the database, respecting soft-delete markers.</li>
 * <li>Expose two validation modes:
 * <ol>
 * <li>{@link #validateReferenceUnique} — global uniqueness (no segment
 * scoping).</li>
 * <li>{@link #validateReferenceUniqueInSegment} — segment-scoped
 * uniqueness via the object-reference bridge tables.</li>
 * </ol>
 * </li>
 * <li>Expose {@link #getBatchRefsForFacet} so generators can reserve
 * already-used references before creating new auto-generated ones.</li>
 * </ul>
 *
 * <h3>Typical Flow</h3>
 * <ol>
 * <li>Upload servlet instantiates this validator once per request.</li>
 * <li>For each row that contains a non-empty reference value the servlet
 * calls {@link #validateReferenceUnique} or
 * {@link #validateReferenceUniqueInSegment}.</li>
 * <li>If duplication is detected, an {@link IllegalArgumentException} is
 * thrown and the row is marked as failed in the upload report.</li>
 * <li>For rows with an empty reference, the servlet's auto-generation
 * utility calls {@link #getBatchRefsForFacet} to obtain the set of
 * already-used refs in this batch, generates a new unique value, and
 * registers it by calling {@code validateReferenceUnique} with the new
 * value before writing it to the DB.</li>
 * <li>After the upload, {@link #clearBatchTracker()} is called to free
 * memory.</li>
 * </ol>
 *
 * @author BUDG Platform Team
 * @version 2.0
 * @since 1.0
 */
public class BulkUploadReferenceValidator {

    // -----------------------------------------------------------------------
    // Static lookup tables — schema metadata per facet
    // -----------------------------------------------------------------------

    /**
     * Maps logical facet names to the <em>reference column name</em> in the
     * corresponding database table.
     *
     * <p>
     * Column-name casing varies across tables ({@code RefNumber},
     * {@code refnumber}, {@code AssetID}, {@code Ref_number}, etc.) because
     * the schema evolved over multiple versions without a consistent naming
     * standard. All variants are captured here so that the rest of the
     * application can treat references uniformly.
     * </p>
     */
    private static final Map<String, String> FACET_REF_COLUMN_MAP = new HashMap<>();

    /**
     * Maps logical facet names to the database table name for that entity.
     */
    private static final Map<String, String> FACET_TABLE_MAP = new HashMap<>();

    /**
     * Maps logical facet names to the <em>soft-delete timestamp column</em>.
     * Non-{@code NULL} / non-empty values in this column indicate a logically
     * deleted record that must not block new references from being created.
     */
    private static final Map<String, String> FACET_DELETED_COLUMN_MAP = new HashMap<>();

    /**
     * Maps logical facet names to the primary-key column name.
     * Required for the multi-table JOIN that resolves segment membership.
     */
    private static final Map<String, String> FACET_ID_COLUMN_MAP = new HashMap<>();

    /**
     * Maps logical facet names to the {@code segment_object_type.Type} string.
     * Used to correctly scope the segment JOIN in
     * {@link #validateReferenceUniqueInSegment}.
     *
     * <p>
     * Note: {@code "Attribute"} maps to {@code "Dataset"} because
     * attributes inherit their segment from the parent dataset.
     * </p>
     */
    private static final Map<String, String> FACET_OBJECT_TYPE_MAP = new HashMap<>();

    static {
        // ------------------------------------------------------------------
        // Reference column mapping — exact DB column names including casing.
        // ------------------------------------------------------------------
        FACET_REF_COLUMN_MAP.put("Dataset", "RefNumber");
        FACET_REF_COLUMN_MAP.put("Product", "refnumber");
        FACET_REF_COLUMN_MAP.put("System", "AssetID");
        FACET_REF_COLUMN_MAP.put("Capability", "RefNumber");
        FACET_REF_COLUMN_MAP.put("Attribute", "RefNumber");
        FACET_REF_COLUMN_MAP.put("Interface", "Ref_number");
        FACET_REF_COLUMN_MAP.put("Glossary", "Ref_Number");
        FACET_REF_COLUMN_MAP.put("RegulatoryTheme", "RefNumber");
        FACET_REF_COLUMN_MAP.put("Regulation", "RefNumber");
        FACET_REF_COLUMN_MAP.put("Policy", "refNumber");
        FACET_REF_COLUMN_MAP.put("Process", "refnumber");
        FACET_REF_COLUMN_MAP.put("Project", "refnumber");
        FACET_REF_COLUMN_MAP.put("Committee", "RefNumber");
        FACET_REF_COLUMN_MAP.put("Geography", "RefNumber");
        FACET_REF_COLUMN_MAP.put("Regulator", "RefNumber");
        FACET_REF_COLUMN_MAP.put("OrgUnit", "RefNumber");
        FACET_REF_COLUMN_MAP.put("BusinessArea", "RefNumber");
        FACET_REF_COLUMN_MAP.put("Legal", "RefNumber");
        FACET_REF_COLUMN_MAP.put("Client", "RefNumber");

        // ------------------------------------------------------------------
        // Table name mapping
        // ------------------------------------------------------------------
        FACET_TABLE_MAP.put("Dataset", "dataset");
        FACET_TABLE_MAP.put("Product", "product");
        FACET_TABLE_MAP.put("System", "system");
        FACET_TABLE_MAP.put("Capability", "capability");
        FACET_TABLE_MAP.put("Attribute", "attribute");
        FACET_TABLE_MAP.put("Interface", "interface");
        FACET_TABLE_MAP.put("Glossary", "glossary");
        FACET_TABLE_MAP.put("RegulatoryTheme", "regulatorytheme");
        FACET_TABLE_MAP.put("Regulation", "regulation");
        FACET_TABLE_MAP.put("Policy", "policy");
        FACET_TABLE_MAP.put("Process", "process");
        FACET_TABLE_MAP.put("Project", "project");
        FACET_TABLE_MAP.put("Committee", "committee");
        FACET_TABLE_MAP.put("Geography", "geography");
        FACET_TABLE_MAP.put("Regulator", "regulator");
        FACET_TABLE_MAP.put("OrgUnit", "org_unit");
        FACET_TABLE_MAP.put("BusinessArea", "business_area");
        FACET_TABLE_MAP.put("Legal", "legal_entity");
        FACET_TABLE_MAP.put("Client", "client");

        // ------------------------------------------------------------------
        // Soft-delete column mapping
        // ------------------------------------------------------------------
        FACET_DELETED_COLUMN_MAP.put("Dataset", "DeletedDatetime");
        FACET_DELETED_COLUMN_MAP.put("Product", "deleteddatetime");
        FACET_DELETED_COLUMN_MAP.put("System", "Deleted_datetime");
        FACET_DELETED_COLUMN_MAP.put("Capability", "DeletedDatetime");
        FACET_DELETED_COLUMN_MAP.put("Attribute", "DeletedDatetime");
        FACET_DELETED_COLUMN_MAP.put("Interface", "deleted_datetime");
        FACET_DELETED_COLUMN_MAP.put("Glossary", "Deleted_datetime");
        FACET_DELETED_COLUMN_MAP.put("RegulatoryTheme", "DeletedDatetime");
        FACET_DELETED_COLUMN_MAP.put("Regulation", "DeletedDatetime");
        FACET_DELETED_COLUMN_MAP.put("Policy", "DeletedDatetime");
        FACET_DELETED_COLUMN_MAP.put("Process", "deleteddatetime");
        FACET_DELETED_COLUMN_MAP.put("Project", "deletedatetime");
        FACET_DELETED_COLUMN_MAP.put("Committee", "DeleteDatetime");
        FACET_DELETED_COLUMN_MAP.put("Geography", "DeletedDatetime");
        FACET_DELETED_COLUMN_MAP.put("Regulator", "DeletedDatetime");
        FACET_DELETED_COLUMN_MAP.put("OrgUnit", "deleted_Date");
        FACET_DELETED_COLUMN_MAP.put("BusinessArea", "deletedatetime");
        FACET_DELETED_COLUMN_MAP.put("Legal", "DeleteDatetime");
        FACET_DELETED_COLUMN_MAP.put("Client", "DeleteDatetime");

        // ------------------------------------------------------------------
        // Primary-key column mapping (casing sensitive)
        // ------------------------------------------------------------------
        FACET_ID_COLUMN_MAP.put("Dataset", "ID");
        FACET_ID_COLUMN_MAP.put("Product", "id");
        FACET_ID_COLUMN_MAP.put("System", "id");
        FACET_ID_COLUMN_MAP.put("Capability", "ID");
        FACET_ID_COLUMN_MAP.put("Attribute", "ID");
        FACET_ID_COLUMN_MAP.put("Interface", "id");
        FACET_ID_COLUMN_MAP.put("Glossary", "ID");
        FACET_ID_COLUMN_MAP.put("RegulatoryTheme", "ID");
        FACET_ID_COLUMN_MAP.put("Regulation", "ID");
        FACET_ID_COLUMN_MAP.put("Policy", "ID");
        FACET_ID_COLUMN_MAP.put("Process", "id");
        FACET_ID_COLUMN_MAP.put("Project", "id");
        FACET_ID_COLUMN_MAP.put("Committee", "ID");
        FACET_ID_COLUMN_MAP.put("Geography", "ID");
        FACET_ID_COLUMN_MAP.put("Regulator", "ID");
        FACET_ID_COLUMN_MAP.put("OrgUnit", "ID");
        FACET_ID_COLUMN_MAP.put("BusinessArea", "ID");
        FACET_ID_COLUMN_MAP.put("Legal", "ID");
        FACET_ID_COLUMN_MAP.put("Client", "ID");

        // ------------------------------------------------------------------
        // Object-type mapping for segment JOIN queries
        // ------------------------------------------------------------------
        FACET_OBJECT_TYPE_MAP.put("Dataset", "Dataset");
        FACET_OBJECT_TYPE_MAP.put("Product", "Product");
        FACET_OBJECT_TYPE_MAP.put("System", "System");
        FACET_OBJECT_TYPE_MAP.put("Capability", "Capability");
        FACET_OBJECT_TYPE_MAP.put("Attribute", "Dataset"); // Attributes inherit segment from dataset
        FACET_OBJECT_TYPE_MAP.put("Interface", "SystemInterface");
        FACET_OBJECT_TYPE_MAP.put("Glossary", "Glossary");
        FACET_OBJECT_TYPE_MAP.put("RegulatoryTheme", "RegulatoryTheme");
        FACET_OBJECT_TYPE_MAP.put("Regulation", "Regulation");
        FACET_OBJECT_TYPE_MAP.put("Policy", "Policy");
        FACET_OBJECT_TYPE_MAP.put("Process", "Process");
        FACET_OBJECT_TYPE_MAP.put("Project", "Project");
        FACET_OBJECT_TYPE_MAP.put("Committee", "Committee");
        FACET_OBJECT_TYPE_MAP.put("Geography", "Geography");
        FACET_OBJECT_TYPE_MAP.put("Regulator", "Regulator");
        FACET_OBJECT_TYPE_MAP.put("OrgUnit", "OrgUnit");
        FACET_OBJECT_TYPE_MAP.put("BusinessArea", "BusinessArea");
        FACET_OBJECT_TYPE_MAP.put("Legal", "LegalEntity");
        FACET_OBJECT_TYPE_MAP.put("Client", "Client");
    }

    // -----------------------------------------------------------------------
    // Per-instance state (batch tracking)
    // -----------------------------------------------------------------------

    /**
     * Intra-file reference tracker for global (non-segment-scoped) checks.
     *
     * <p>
     * Key: lower-cased facet name (e.g. {@code "dataset"}).<br>
     * Value: lower-cased reference numbers already encountered in this upload
     * for that facet.
     * </p>
     *
     * <p>
     * Also serves as the backing store consulted by
     * {@link #getBatchRefsForFacet}, which auto-generation utilities use to
     * avoid recreating a ref that was already written earlier in the same
     * file.
     * </p>
     */
    private final Map<String, Set<String>> batchReferences = new HashMap<>();

    /**
     * Intra-file reference tracker for segment-scoped checks.
     *
     * <p>
     * Key format: {@code "<facet_lowercase>_<segmentId>"}
     * (e.g. {@code "dataset_3"}).<br>
     * Value: lower-cased reference numbers already encountered in this upload
     * for that (facet, segment) combination.
     * </p>
     */
    private final Map<String, Set<String>> batchReferencesBySegment = new HashMap<>();

    // -----------------------------------------------------------------------
    // Public validation methods
    // -----------------------------------------------------------------------

    /**
     * Validates that {@code refNumber} is globally unique for the given
     * {@code facet} — i.e. it does not already exist anywhere in the database
     * table for that entity type (active records only), and does not already
     * appear earlier in the current upload file.
     *
     * <p>
     * Empty / blank reference numbers are silently skipped because the
     * uploading servlet will auto-generate a reference for those rows.
     * </p>
     *
     * <p>
     * <b>Important side effect:</b> On success, {@code refNumber}
     * (lowercased and trimmed) is added to {@code batchReferences} so that
     * subsequent rows in the same file cannot reuse it, and so that
     * {@link #getBatchRefsForFacet} can return it to auto-generation
     * utilities.
     * </p>
     *
     * @param facet     The logical entity type (e.g. {@code "Dataset"},
     *                  {@code "Process"}).
     * @param refNumber The reference number value from the upload row.
     *                  {@code null} or blank is silently ignored.
     * @param conn      An active, caller-managed {@link java.sql.Connection}.
     * @throws IllegalArgumentException if {@code refNumber} is already in use
     *                                  within the current batch or in the DB.
     * @throws SQLException             if the database query fails.
     */
    public void validateReferenceUnique(String facet, String refNumber, Connection conn)
            throws IllegalArgumentException, SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return; // Empty references are allowed (will be auto-generated)
        }

        String normalizedRef = refNumber.trim();
        String facetKey = facet.toLowerCase();

        // Check for duplicate within the same batch
        Set<String> batchRefs = batchReferences.computeIfAbsent(facetKey, k -> new HashSet<>());
        String normalizedLower = normalizedRef.toLowerCase();
        if (batchRefs.contains(normalizedLower)) {
            // "Process" has historically included the entity label in its error messages
            // for clarity in audit logs; other facets omit it to keep messages concise.
            String entityLabel = "Process".equals(facet) ? "process " : "";
            throw new IllegalArgumentException(
                    String.format("Duplicate %sreference number '%s' found in this file. " +
                            "This reference appears multiple times in the upload.", entityLabel, normalizedRef));
        }

        // Resolve schema metadata; bail early for unsupported facets.
        String tableName = FACET_TABLE_MAP.get(facet);
        String refColumn = FACET_REF_COLUMN_MAP.get(facet);
        String deletedColumn = FACET_DELETED_COLUMN_MAP.get(facet);

        if (tableName == null || refColumn == null) {
            // Facet not supported or doesn't have reference numbers
            return;
        }

        // Build SQL query to check for existing reference (global, table-level check)
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT 1 FROM ").append(tableName);
        sql.append(" WHERE LOWER(").append(refColumn).append(") = LOWER(?)");

        // Add deleted column check if available
        if (deletedColumn != null) {
            sql.append(" AND (").append(deletedColumn).append(" IS NULL OR ").append(deletedColumn).append(" = '')");
        }

        sql.append(" LIMIT 1");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, normalizedRef);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String entityLabel = "Process".equals(facet) ? "process " : "";
                    throw new IllegalArgumentException(
                            String.format("Duplicate %sreference number '%s' found. " +
                                    "This reference already exists in the database.", entityLabel, normalizedRef));
                }
            }
        }

        // Reference is unique - add to batch tracker
        batchRefs.add(normalizedLower);
    }

    /**
     * Validates that {@code refNumber} is unique within the specified
     * {@code segmentId} for the given {@code facet}. Both an intra-file check
     * (against {@code batchReferencesBySegment}) and a database check (through
     * the object-reference segment JOIN) are performed.
     *
     * <p>
     * The default segment (ID&nbsp;=&nbsp;1, "Enterprise") is used when
     * {@code segmentId} is {@code null}.
     * </p>
     *
     * <p>
     * <b>Important side effect:</b> On success, {@code refNumber}
     * (lowercased) is added to the segment-keyed batch tracker.
     * </p>
     *
     * @param facet     The logical entity type (e.g. {@code "Dataset"},
     *                  {@code "System"}).
     * @param refNumber The reference number to validate. {@code null} or
     *                  blank is silently skipped.
     * @param segmentId The target segment primary key, or {@code null} for the
     *                  Enterprise segment.
     * @param conn      An active, caller-managed {@link java.sql.Connection}.
     * @throws IllegalArgumentException if a duplicate is found within the
     *                                  file or in the database.
     * @throws SQLException             if a database query fails.
     */
    public void validateReferenceUniqueInSegment(String facet, String refNumber, Long segmentId, Connection conn)
            throws IllegalArgumentException, SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return; // Empty references are allowed (will be auto-generated)
        }

        // Default to Enterprise segment (ID = 1) if not specified
        long targetSegmentId = (segmentId != null) ? segmentId : 1L;

        String normalizedRef = refNumber.trim();
        String facetKey = facet.toLowerCase();
        String batchKey = facetKey + "_" + targetSegmentId;

        // Check for duplicate within the same batch and segment
        Set<String> batchRefsForSegment = batchReferencesBySegment.computeIfAbsent(batchKey, k -> new HashSet<>());
        String normalizedLower = normalizedRef.toLowerCase();
        String segmentDisplay = getSegmentDisplayName(conn, targetSegmentId);
        if (batchRefsForSegment.contains(normalizedLower)) {
            throw new IllegalArgumentException(
                    String.format("Duplicate reference number '%s' found in this file for the same segment. " +
                            "This reference appears multiple times in the upload for segment '%s'.", normalizedRef,
                            segmentDisplay));
        }

        // Resolve schema metadata
        String tableName = FACET_TABLE_MAP.get(facet);
        String refColumn = FACET_REF_COLUMN_MAP.get(facet);
        String idColumn = FACET_ID_COLUMN_MAP.get(facet);
        String deletedColumn = FACET_DELETED_COLUMN_MAP.get(facet);
        String objectType = FACET_OBJECT_TYPE_MAP.get(facet);

        if (tableName == null || refColumn == null || objectType == null) {
            // Facet not supported or doesn't have reference/segment support
            return;
        }

        // Segment-scoped DB check: JOINs through the object_reference and
        // segment_x_resource bridge tables to isolate the uniqueness check
        // to the specific segment targeted by the import.
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT 1 FROM ").append(tableName).append(" t ");
        sql.append("JOIN object_reference orr ON t.").append(idColumn).append(" = orr.Object_ID ");
        sql.append("JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID ");
        sql.append("JOIN segment_x_resource sxr ON orr.ID = sxr.Object_Reference_ID ");
        sql.append("WHERE sot.Type = ? ");
        sql.append("AND sxr.Segment_ID = ? ");
        sql.append("AND LOWER(t.").append(refColumn).append(") = LOWER(?) ");
        sql.append("AND sxr.Deleted_At IS NULL ");

        // Add deleted column check if available
        if (deletedColumn != null) {
            sql.append("AND (t.").append(deletedColumn).append(" IS NULL OR t.").append(deletedColumn)
                    .append(" = '') ");
        }

        sql.append("LIMIT 1");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, objectType);
            ps.setLong(2, targetSegmentId);
            ps.setString(3, normalizedRef);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException(
                            String.format("Duplicate reference number '%s' found. " +
                                    "An object with this reference already exists in segment '%s'.", normalizedRef,
                                    segmentDisplay));
                }
            }
        }

        // Reference is unique in segment - add to batch tracker
        batchRefsForSegment.add(normalizedLower);
    }

    /**
     * Returns a user-friendly segment label for error messages.
     */
    private String getSegmentDisplayName(Connection conn, long segmentId) {
        String fallback = "ID " + segmentId;
        String sql = "SELECT Name FROM segment WHERE ID = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, segmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("Name");
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            }
        } catch (SQLException ignored) {
            // Keep fallback with the ID if name lookup fails.
        }
        return fallback;
    }

    // -----------------------------------------------------------------------
    // Batch inspection / management
    // -----------------------------------------------------------------------

    /**
     * Returns an unmodifiable view of all reference numbers (lower-cased)
     * that have already been validated — and therefore reserved — for the
     * given {@code facet} in the current upload batch.
     *
     * <p>
     * This method exists specifically to support <em>reference-number
     * auto-generation</em>: when a row has no reference value the servlet
     * generates one automatically, but it must not generate a value that was
     * already consumed by an earlier row in the same file. The generator
     * passes the set returned by this method as a "reserved" list and skips
     * any candidate that appears in it.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None — this is a read-only view.
     * </p>
     *
     * @param facet The logical entity type (e.g. {@code "Attribute"},
     *              {@code "Process"}).
     * @return An unmodifiable {@link Set} of lower-cased reference strings
     *         already used in this batch for the given facet.
     *         Returns an empty set (never {@code null}) when no refs have been
     *         registered yet for that facet.
     */
    public Set<String> getBatchRefsForFacet(String facet) {
        if (facet == null) {
            return Collections.emptySet();
        }
        Set<String> refs = batchReferences.get(facet.toLowerCase());
        return refs == null ? Collections.emptySet() : Collections.unmodifiableSet(refs);
    }

    /**
     * Resets all in-memory batch reference trackers.
     *
     * <p>
     * Should be called after an upload completes or aborts to release
     * memory. If the validator instance is reused across multiple requests
     * (not recommended), call this before each new upload session.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> Clears both {@code batchReferences} and
     * {@code batchReferencesBySegment}.
     * </p>
     */
    public void clearBatchTracker() {
        batchReferences.clear();
        batchReferencesBySegment.clear();
    }

    // -----------------------------------------------------------------------
    // Static query helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the database reference column name for the given facet, or
     * {@code null} if the facet is not registered.
     *
     * <p>
     * Useful for building dynamic SELECT lists without duplicating the
     * column-name knowledge held in this class.
     * </p>
     *
     * @param facet The logical entity type (e.g. {@code "Dataset"}).
     * @return The exact database column name (with original casing), or
     *         {@code null} if not registered.
     */
    public static String getRefColumnName(String facet) {
        return FACET_REF_COLUMN_MAP.get(facet);
    }

    /**
     * Returns {@code true} if the given facet has a registered reference
     * column and therefore participates in reference-number validation.
     *
     * <p>
     * Can be used as a cheap pre-flight check before invoking validation
     * methods.
     * </p>
     *
     * @param facet The logical entity type.
     * @return {@code true} if {@code FACET_REF_COLUMN_MAP} contains an entry
     *         for {@code facet}; {@code false} otherwise.
     */
    public static boolean hasReferenceColumn(String facet) {
        return FACET_REF_COLUMN_MAP.containsKey(facet);
    }
}
