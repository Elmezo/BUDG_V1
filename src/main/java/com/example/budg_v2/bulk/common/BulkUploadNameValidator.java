package com.example.budg_v2.bulk.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * <b>File Overview</b><br>
 * {@code BulkUploadNameValidator} enforces <em>name-uniqueness constraints</em>
 * for every major facet (entity type) during bulk upload operations. It is a
 * stateful, per-request validator that is instantiated once per upload session
 * and accumulates names seen in the current batch so that intra-file duplicates
 * are caught before any row is committed to the database.
 *
 * <p>
 * <b>Business Capability:</b> Bulk Data Governance — Name Integrity
 * Validation. The platform enforces that object names are unique within their
 * owning scope (segment, system, or dataset) to preserve the searchability and
 * referential clarity of the data catalogue.
 * </p>
 *
 * <p>
 * <b>Modules that depend on this class:</b>
 * </p>
 * <ul>
 * <li>All per-facet {@code *BulkUploadServlet} classes that perform INSERT
 * or UPDATE operations (e.g. {@code DatasetBulkUploadServlet},
 * {@code GlossaryBulkUploadServlet}).</li>
 * <li>{@code BulkDataProcessorService} — the generic processor delegates
 * name validation to an instance of this class.</li>
 * </ul>
 *
 * <h3>Responsibility</h3>
 * <ul>
 * <li>Maintain an in-memory set of names already encountered in the current
 * upload file, partitioned by (facet + scope) to avoid cross-entity
 * false positives.</li>
 * <li>Execute parameterised SQL queries to detect pre-existing names in the
 * database, respecting soft-delete markers so that logically deleted
 * objects do not block new entries.</li>
 * <li>Expose three distinct validation modes:
 * <ol>
 * <li>{@link #validateNameUniqueInSegment} — for segment-scoped entities.</li>
 * <li>{@link #validateNameUniqueInSystem} — for dataset-to-system scoping.</li>
 * <li>{@link #validateNameUniqueInDataset} — for attributes scoped to a
 * dataset.</li>
 * </ol>
 * </li>
 * </ul>
 *
 * <h3>Typical Flow</h3>
 * <ol>
 * <li>The upload servlet instantiates this validator once before row
 * iteration begins.</li>
 * <li>For each row, the servlet calls the appropriate
 * {@code validateName*} method, passing the live
 * {@link java.sql.Connection}.</li>
 * <li>If the name is duplicate (in-file or in-DB), an
 * {@link IllegalArgumentException} is thrown with a human-readable
 * message that is surfaced back to the user in the upload report.</li>
 * <li>On success the name is recorded in the internal batch tracker so
 * subsequent rows in the same file cannot reuse it.</li>
 * <li>After the upload completes (or aborts), {@link #clearBatchTracker()}
 * is called to free memory.</li>
 * </ol>
 *
 * @author BUDG Platform Team
 * @version 2.0
 * @since 1.0
 */
public class BulkUploadNameValidator {

    // -----------------------------------------------------------------------
    // Static lookup tables (initialised once at class-load time)
    // -----------------------------------------------------------------------

    /**
     * Maps logical facet names (e.g. {@code "Dataset"}) to the <em>name
     * column</em> of their underlying database table.
     *
     * <p>
     * <b>Why a static map instead of an ORM:</b> The database schema uses
     * inconsistent column-name casing across tables (a historical artefact).
     * Maintaining an explicit map here isolates all such knowledge in one
     * place rather than scattering it across dozens of servlets.
     * </p>
     */
    private static final Map<String, String> FACET_NAME_COLUMN_MAP = new HashMap<>();

    /**
     * Maps logical facet names to the <em>database table name</em> that
     * stores objects of that facet type.
     */
    private static final Map<String, String> FACET_TABLE_MAP = new HashMap<>();

    /**
     * Maps logical facet names to the <em>primary-key column name</em> of
     * their database table.
     *
     * <p>
     * The schema uses both {@code "ID"} (uppercase) and {@code "id"}
     * (lowercase) depending on which era a table was created — this map
     * captures the exact casing required for case-sensitive databases.
     * </p>
     */
    private static final Map<String, String> FACET_ID_COLUMN_MAP = new HashMap<>();

    /**
     * Maps logical facet names to the <em>soft-delete timestamp column</em>
     * of their database table. A non-{@code NULL} value in this column means
     * the record has been logically deleted and must be excluded from
     * uniqueness checks so that a new record can reuse the name.
     */
    private static final Map<String, String> FACET_DELETED_COLUMN_MAP = new HashMap<>();

    /**
     * Maps logical facet names to the <em>object-type string</em> stored in
     * the {@code segment_object_type} table. This is required for the JOIN
     * path that resolves which segment an object belongs to.
     *
     * <p>
     * Note: {@code "Attribute"} maps to {@code "Dataset"} because
     * attributes inherit their segment membership from the parent dataset,
     * not directly from the attribute table.
     * </p>
     */
    private static final Map<String, String> FACET_OBJECT_TYPE_MAP = new HashMap<>();

    static {
        // ------------------------------------------------------------------
        // Name column mapping — each value is the exact column name used in
        // the corresponding table, including original casing.
        // ------------------------------------------------------------------
        FACET_NAME_COLUMN_MAP.put("Dataset", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("Product", "primaryname");
        FACET_NAME_COLUMN_MAP.put("System", "Name");
        FACET_NAME_COLUMN_MAP.put("Capability", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("Attribute", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("Interface", "Name");
        FACET_NAME_COLUMN_MAP.put("Glossary", "Name"); // Glossary table uses "Name" column
        FACET_NAME_COLUMN_MAP.put("RegulatoryTheme", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("Regulation", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("Policy", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("Process", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("Project", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("Committee", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("Geography", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("Regulator", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("OrgUnit", "Name");
        FACET_NAME_COLUMN_MAP.put("BusinessArea", "PrimaryName");
        FACET_NAME_COLUMN_MAP.put("Legal", "ShortName");
        FACET_NAME_COLUMN_MAP.put("Client", "PrimaryName");

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
        FACET_TABLE_MAP.put("Legal", "legal");
        FACET_TABLE_MAP.put("Client", "client");

        // ------------------------------------------------------------------
        // Primary-key column mapping
        // "ID" (uppercase) for legacy tables, "id" (lowercase) for newer ones.
        // ------------------------------------------------------------------
        // Tables using "ID" (uppercase)
        FACET_ID_COLUMN_MAP.put("Dataset", "ID");
        FACET_ID_COLUMN_MAP.put("Capability", "ID");
        FACET_ID_COLUMN_MAP.put("Attribute", "ID");
        FACET_ID_COLUMN_MAP.put("Glossary", "ID");
        FACET_ID_COLUMN_MAP.put("RegulatoryTheme", "ID");
        FACET_ID_COLUMN_MAP.put("Regulation", "ID");
        FACET_ID_COLUMN_MAP.put("Policy", "ID");
        FACET_ID_COLUMN_MAP.put("Committee", "ID");
        FACET_ID_COLUMN_MAP.put("Geography", "ID");
        FACET_ID_COLUMN_MAP.put("Regulator", "ID");
        FACET_ID_COLUMN_MAP.put("OrgUnit", "ID");
        FACET_ID_COLUMN_MAP.put("BusinessArea", "ID");
        FACET_ID_COLUMN_MAP.put("Legal", "ID");
        FACET_ID_COLUMN_MAP.put("Client", "ID");
        // Tables using "id" (lowercase)
        FACET_ID_COLUMN_MAP.put("Product", "id");
        FACET_ID_COLUMN_MAP.put("System", "id");
        FACET_ID_COLUMN_MAP.put("Interface", "id");
        FACET_ID_COLUMN_MAP.put("Process", "id");
        FACET_ID_COLUMN_MAP.put("Project", "id");

        // ------------------------------------------------------------------
        // Soft-delete column mapping — names vary wildly across tables.
        // A non-NULL (or non-empty) value in this column means the row is
        // logically deleted and should be ignored during uniqueness checks.
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
        // Object-type mapping for segment-based JOIN queries.
        // The value must match the "Type" column in the segment_object_type
        // table — these strings are controlled by the DB seed data.
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
     * Intra-file duplicate tracker for segment-scoped entities.
     *
     * <p>
     * Key format: {@code "<facet_lowercase>_<segmentId>"}
     * (e.g. {@code "dataset_3"}, {@code "system_1"}).<br>
     * Value: lower-cased names already encountered in this upload for that
     * (facet, segment) combination.
     * </p>
     *
     * <p>
     * Using a compound key prevents a name that is valid in segment A
     * from being rejected because it appears in segment B within the same
     * file.
     * </p>
     */
    private final Map<String, Set<String>> batchNames = new HashMap<>();

    /**
     * Intra-file duplicate tracker for dataset-to-system scoped entities.
     *
     * <p>
     * Key format: {@code "<facet_lowercase>_<systemId>"}
     * where {@code systemId} is {@code "NULL"} when no system is assigned.<br>
     * Value: lower-cased names already encountered in this upload for that
     * (facet, system) combination.
     * </p>
     */
    private final Map<String, Set<String>> batchNamesBySystem = new HashMap<>();

    /**
     * Intra-file duplicate tracker for attributes scoped to a specific dataset.
     *
     * <p>
     * Key format: {@code "attribute_dataset_<datasetId>"}.<br>
     * Value: lower-cased attribute names already encountered in this upload
     * for that dataset.
     * </p>
     */
    private final Map<String, Set<String>> batchNamesByDataset = new HashMap<>();

    // -----------------------------------------------------------------------
    // Public validation methods
    // -----------------------------------------------------------------------

    /**
     * Validates name uniqueness for bulk upload. For most facets, uniqueness is
     * scoped to the <b>target segment</b> only (same name may exist in another segment).
     * For {@code OrgUnit}, the name must be unique <b>project-wide</b> (global table check).
     *
     * <ol>
     * <li><b>Batch (intra-file)</b>: for non-OrgUnit, same (facet, segment); for
     *         OrgUnit, project-wide within the file.</li>
     * <li><b>Global DB</b>: only for {@code OrgUnit} — duplicate names across
     *         any segment are rejected.</li>
     * <li><b>Segment-scoped DB</b>: for other facets, JOIN through
     *         {@code object_reference → segment_object_type → segment_x_resource}.</li>
     * </ol>
     *
     * <p>
     * <b>Important side effect:</b> On success, {@code name} (lowercased
     * and trimmed) is added to the internal batch tracker so subsequent rows
     * in the same file cannot reuse it.
     * </p>
     *
     * @param facet     The logical entity type (e.g. {@code "Dataset"},
     *                  {@code "System"}, {@code "Committee"}). Must match a
     *                  key in the static lookup maps.
     * @param name      The object name submitted in the upload row.
     *                  {@code null} or blank values are silently skipped
     *                  (empty-name validation is handled by a separate
     *                  required-field check).
     * @param segmentId The target segment's primary key. Pass {@code null}
     *                  to default to the Enterprise segment (ID&nbsp;=&nbsp;1)
     *                  for segment-scoped checks (not used for OrgUnit global batch key).
     * @param conn      An active, caller-managed {@link java.sql.Connection}.
     *                  This method does not commit or roll back.
     * @throws IllegalArgumentException if a duplicate name is detected either
     *                                  within the file or in the database.
     * @throws SQLException             if the database query fails.
     */
    public void validateNameUniqueInSegment(String facet, String name, Long segmentId, Connection conn)
            throws IllegalArgumentException, SQLException {
        if (name == null || name.trim().isEmpty()) {
            return; // Empty names are not allowed but validation happens elsewhere
        }
        // Business update: Process object names are now allowed to repeat.
        if ("Process".equals(facet)) {
            return;
        }

        // Default to Enterprise segment (ID = 1) if not specified
        long targetSegmentId = (segmentId != null) ? segmentId : 1L;

        String normalizedName = name.trim();
        String facetKey = facet.toLowerCase();
        final boolean isOrgUnit = "OrgUnit".equals(facet);
        // Org Unit: names must be unique project-wide; batch key must not split by segment.
        String batchKey = isOrgUnit ? (facetKey + "_project") : (facetKey + "_" + targetSegmentId);

        // Check for duplicate within the same batch
        Set<String> batchNamesForSegment = batchNames.computeIfAbsent(batchKey, k -> new HashSet<>());
        String normalizedLower = normalizedName.toLowerCase();
        String segmentDisplay = getSegmentDisplayName(conn, targetSegmentId);
        if (batchNamesForSegment.contains(normalizedLower)) {
            if (isOrgUnit) {
                throw new IllegalArgumentException(
                        String.format("Duplicate name '%s' found in this file. " +
                                "Org Unit names must be unique across the project.", normalizedName));
            }
            throw new IllegalArgumentException(
                    String.format("Duplicate name '%s' found in this file for the same segment. " +
                            "This name appears multiple times in the upload for segment '%s'.", normalizedName,
                            segmentDisplay));
        }

        // Resolve metadata from static maps; bail out early if the facet is
        // not supported (no configuration entry means no constraint to enforce).
        String tableName = FACET_TABLE_MAP.get(facet);
        String nameColumn = FACET_NAME_COLUMN_MAP.get(facet);
        String idColumn = FACET_ID_COLUMN_MAP.get(facet);
        String deletedColumn = FACET_DELETED_COLUMN_MAP.get(facet);
        String objectType = FACET_OBJECT_TYPE_MAP.get(facet);

        if (tableName == null || nameColumn == null || objectType == null) {
            // Facet not supported or doesn't have name/segment support
            return;
        }

        // Project-wide uniqueness: only Org Unit (same name allowed in different segments for other facets).
        if (isOrgUnit) {
            StringBuilder globalSql = new StringBuilder();
            globalSql.append("SELECT t.").append(idColumn).append(" FROM ").append(tableName).append(" t ");
            globalSql.append("WHERE LOWER(t.").append(nameColumn).append(") = LOWER(?) ");
            if (deletedColumn != null) {
                globalSql.append("AND (t.").append(deletedColumn).append(" IS NULL OR t.").append(deletedColumn)
                        .append(" = '') ");
            }
            globalSql.append("LIMIT 1");
            try (PreparedStatement ps = conn.prepareStatement(globalSql.toString())) {
                ps.setString(1, normalizedName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long existingId = rs.getLong(1);
                        throw new IllegalArgumentException(
                                String.format("Duplicate name '%s' found. An Org Unit with this name already exists "
                                        + "(existing ID: %d). Search by ID or name in BUDG to open that record.",
                                        normalizedName, existingId));
                    }
                }
            }
            batchNamesForSegment.add(normalizedLower);
            return;
        }

        // Segment-scoped DB check: JOINs through the object_reference and
        // segment_x_resource bridge tables to isolate the check to the
        // specific segment the import targets.
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT t.").append(idColumn).append(" FROM ").append(tableName).append(" t ");
        sql.append("JOIN object_reference orr ON t.").append(idColumn).append(" = orr.Object_ID ");
        sql.append("JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID ");
        sql.append("JOIN segment_x_resource sxr ON orr.ID = sxr.Object_Reference_ID ");
        sql.append("WHERE sot.Type = ? ");
        sql.append("AND sxr.Segment_ID = ? ");
        sql.append("AND LOWER(t.").append(nameColumn).append(") = LOWER(?) ");
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
            ps.setString(3, normalizedName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long existingId = rs.getLong(1);
                    throw new IllegalArgumentException(
                            String.format("Duplicate name '%s' found. An object with this name already exists in "
                                    + "segment '%s' (existing %s ID: %d). Use global search or open that object by "
                                    + "ID if the list is filtered by segment or permissions.",
                                    normalizedName, segmentDisplay, facet, existingId));
                }
            }
        }

        // Name is unique in segment - add to batch tracker
        batchNamesForSegment.add(normalizedLower);
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

    /**
     * Validates that a <b>dataset</b> name is unique within its source system
     * ({@code MasterSource}).
     *
     * <p>
     * The scoping rule is:
     * </p>
     * <ul>
     * <li>If {@code systemId} is {@code null} (no system assigned): enforce
     * <em>global</em> uniqueness — only one "system-less" dataset may
     * carry this name.</li>
     * <li>If {@code systemId} is set: enforce uniqueness only among datasets
     * that share the same {@code MasterSource} value.</li>
     * </ul>
     *
     * <p>
     * <b>Important side effect:</b> On success, the name (lowercased) is
     * added to the internal {@code batchNamesBySystem} tracker.
     * </p>
     *
     * @param facet    The facet name; should be {@code "Dataset"} for this
     *                 method. Other facets are silently skipped because they
     *                 do not follow the system-scoped uniqueness model.
     * @param name     The dataset name to validate. {@code null} or blank
     *                 values are silently skipped.
     * @param systemId The MasterSource system ID, or {@code null} if the
     *                 dataset has no associated system.
     * @param conn     An active, caller-managed {@link java.sql.Connection}.
     * @throws IllegalArgumentException if a duplicate name is detected.
     * @throws SQLException             if a database query fails.
     */
    public void validateNameUniqueInSystem(String facet, String name, Integer systemId, Connection conn)
            throws IllegalArgumentException, SQLException {
        if (name == null || name.trim().isEmpty()) {
            return; // Empty names are not allowed but validation happens elsewhere
        }

        String normalizedName = name.trim();
        String facetKey = facet.toLowerCase();
        // Use "NULL" as the key suffix when no system is assigned so that
        // system-less datasets form their own isolated uniqueness group.
        String systemKey = (systemId != null) ? String.valueOf(systemId) : "NULL";
        String batchKey = facetKey + "_" + systemKey;

        // Check for duplicate within the same batch and system
        Set<String> batchNamesForSystem = batchNamesBySystem.computeIfAbsent(batchKey, k -> new HashSet<>());
        String normalizedLower = normalizedName.toLowerCase();
        if (batchNamesForSystem.contains(normalizedLower)) {
            if (systemId != null) {
                throw new IllegalArgumentException(
                        String.format("Duplicate name '%s' found in this file for the same system. " +
                                "This name appears multiple times in the upload for system ID %d.", normalizedName,
                                systemId));
            } else {
                throw new IllegalArgumentException(
                        String.format("Duplicate name '%s' found in this file. " +
                                "This name appears multiple times in the upload for datasets with no system assigned.",
                                normalizedName));
            }
        }

        // Resolve metadata from static maps
        String tableName = FACET_TABLE_MAP.get(facet);
        String nameColumn = FACET_NAME_COLUMN_MAP.get(facet);
        String deletedColumn = FACET_DELETED_COLUMN_MAP.get(facet);

        if (tableName == null || nameColumn == null) {
            // Facet not supported or doesn't have name support
            return;
        }

        // Build SQL query to check for existing name in the same system
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT 1 FROM ").append(tableName).append(" ");
        sql.append("WHERE LOWER(").append(nameColumn).append(") = LOWER(?) ");

        if (systemId != null) {
            // MasterSource is set: Check uniqueness within the same system
            sql.append("AND MasterSource = ? ");
        } else {
            // MasterSource is NULL: Check global uniqueness (only one dataset with NULL
            // MasterSource can have this name)
            sql.append("AND MasterSource IS NULL ");
        }

        // Add deleted column check if available
        if (deletedColumn != null) {
            sql.append("AND (").append(deletedColumn).append(" IS NULL OR ").append(deletedColumn).append(" = '') ");
        }

        sql.append("LIMIT 1");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, normalizedName);
            if (systemId != null) {
                ps.setInt(2, systemId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (systemId != null) {
                        throw new IllegalArgumentException(
                                String.format("Duplicate name '%s' found. " +
                                        "A dataset with this name already exists in system ID %d.", normalizedName,
                                        systemId));
                    } else {
                        throw new IllegalArgumentException(
                                String.format("Duplicate name '%s' found. " +
                                        "A dataset with this name already exists with no system assigned.",
                                        normalizedName));
                    }
                }
            }
        }

        // Name is unique in system - add to batch tracker
        batchNamesForSystem.add(normalizedLower);
    }

    /**
     * Validates that an <b>attribute</b> name is unique within the specified
     * parent dataset. Attributes from different datasets are allowed to share
     * the same name; uniqueness is scoped only to the dataset.
     *
     * <p>
     * <b>Important side effect:</b> On success, the name (lowercased) is
     * added to the internal {@code batchNamesByDataset} tracker.
     * </p>
     *
     * @param facet     The facet name; must be {@code "Attribute"}. All other
     *                  facets are silently ignored.
     * @param name      The attribute name to validate. {@code null} or blank
     *                  values are silently skipped.
     * @param datasetId The parent dataset's primary key. {@code null} skips
     *                  the check entirely (caller is responsible for
     *                  validating the dataset reference separately).
     * @param conn      An active, caller-managed {@link java.sql.Connection}.
     * @throws IllegalArgumentException if a duplicate attribute name exists in
     *                                  the same dataset (in the file or DB).
     * @throws SQLException             if a database query fails.
     */
    public void validateNameUniqueInDataset(String facet, String name, Integer datasetId, Connection conn)
            throws IllegalArgumentException, SQLException {
        if (name == null || name.trim().isEmpty() || datasetId == null) {
            return;
        }
        // Guard: this method is specifically designed for the Attribute facet.
        // Other facets follow different scoping rules handled elsewhere.
        if (!"Attribute".equals(facet)) {
            return;
        }
        String normalizedName = name.trim();
        String batchKey = "attribute_dataset_" + datasetId;
        Set<String> batchNamesForDataset = batchNamesByDataset.computeIfAbsent(batchKey, k -> new HashSet<>());
        String normalizedLower = normalizedName.toLowerCase();
        if (batchNamesForDataset.contains(normalizedLower)) {
            throw new IllegalArgumentException(
                    String.format("Duplicate name '%s' found in this file for the same dataset. " +
                            "This attribute name already appears in the upload for dataset ID %d.", normalizedName,
                            datasetId));
        }
        // Inline SQL for the attribute table: dataset-scoped check using
        // Dataset_ID foreign key rather than the segment bridge tables.
        String sql = "SELECT 1 FROM attribute WHERE Dataset_ID = ? AND LOWER(PrimaryName) = LOWER(?) AND (DeletedDatetime IS NULL OR DeletedDatetime = '') LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            ps.setString(2, normalizedName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Duplicate name '%s' found. An attribute with this name already exists in dataset ID %d.",
                                    normalizedName, datasetId));
                }
            }
        }
        batchNamesForDataset.add(normalizedLower);
    }

    // -----------------------------------------------------------------------
    // Batch management
    // -----------------------------------------------------------------------

    /**
     * Resets all in-memory batch trackers.
     *
     * <p>
     * Must be called:
     * </p>
     * <ul>
     * <li>Before starting a new upload session if the validator instance is
     * reused (not recommended — prefer constructing a fresh instance per
     * request).</li>
     * <li>After an upload completes or aborts, to free memory held by the
     * per-upload name sets.</li>
     * </ul>
     *
     * <p>
     * <b>Side effect:</b> Clears {@code batchNames}, {@code batchNamesBySystem},
     * and {@code batchNamesByDataset}.
     * </p>
     */
    public void clearBatchTracker() {
        batchNames.clear();
        batchNamesBySystem.clear();
        batchNamesByDataset.clear();
    }

    // -----------------------------------------------------------------------
    // Static query helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the name column for the given facet, or {@code null} if the
     * facet is not registered.
     *
     * <p>
     * Useful for building dynamic SELECT lists in servlets that need to
     * query the name column without duplicating the mapping knowledge.
     * </p>
     *
     * @param facet The logical entity type (e.g. {@code "Dataset"}).
     * @return The database column name, or {@code null} if not found.
     */
    public static String getNameColumnName(String facet) {
        return FACET_NAME_COLUMN_MAP.get(facet);
    }

    /**
     * Returns the object-type string (as stored in {@code segment_object_type})
     * for the given facet.
     *
     * @param facet The logical entity type.
     * @return The object-type string, or {@code null} if not found.
     */
    public static String getObjectType(String facet) {
        return FACET_OBJECT_TYPE_MAP.get(facet);
    }

    /**
     * Returns {@code true} if the given facet has both a registered name
     * column and a registered object-type — i.e. it fully participates in
     * segment-scoped name validation.
     *
     * <p>
     * Can be used as a cheap pre-flight check before calling
     * {@link #validateNameUniqueInSegment}.
     * </p>
     *
     * @param facet The logical entity type.
     * @return {@code true} if both {@code FACET_NAME_COLUMN_MAP} and
     *         {@code FACET_OBJECT_TYPE_MAP} contain an entry for
     *         {@code facet}; {@code false} otherwise.
     */
    public static boolean hasNameColumn(String facet) {
        return FACET_NAME_COLUMN_MAP.containsKey(facet) && FACET_OBJECT_TYPE_MAP.containsKey(facet);
    }
}
