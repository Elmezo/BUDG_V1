package com.example.budg_v2.bulk.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * <b>File Overview</b><br>
 * {@code BulkUploadParentValidator} provides a single, reusable method that
 * detects <em>circular parent–child hierarchies</em> before they are persisted
 * to the database during a bulk-upload UPDATE operation.
 *
 * <p>
 * <b>Business Capability:</b> Bulk Data Integrity — Hierarchy Cycle
 * Prevention. Several BUDG entities (Process, Policy, Glossary, System,
 * Regulation, Project, OrgUnit, Client, Product) support multi-level
 * parent–child trees. Assigning an object's parent to one of its own
 * descendants would corrupt the tree, make recursive tree queries loop
 * infinitely, and produce incorrect lineage reports.
 * </p>
 *
 * <p>
 * <b>Modules that depend on this class:</b>
 * </p>
 * <ul>
 * <li>Per-facet bulk-upload servlets that handle UPDATE rows containing a
 * modified {@code ParentID} / {@code parent_id} field.</li>
 * <li>Any future generic bulk-processor that processes hierarchical
 * entities.</li>
 * </ul>
 *
 * <h3>Responsibility</h3>
 * <ul>
 * <li>Walk the ancestor chain of the proposed <em>new parent</em> upward
 * through the database, one level per iteration.</li>
 * <li>Return {@code true} as soon as the object being updated is encountered
 * in that ancestor chain (cycle detected).</li>
 * <li>Respect soft-deleted records: logically deleted nodes are excluded from
 * the ancestor traversal so they cannot act as phantom cycle participants.</li>
 * <li>Guard against pathological deep trees or data corruption by capping
 * traversal at {@value #MAX_ANCESTOR_DEPTH} levels.</li>
 * </ul>
 *
 * <h3>Typical Flow</h3>
 * <ol>
 * <li>Servlet extracts the object-being-updated ({@code objectId}) and the
 * proposed new parent ({@code newParentId}) from the upload row.</li>
 * <li>Servlet calls
 * {@link #wouldCreateParentCycle(Connection, int, int, String)}.</li>
 * <li>If {@code true} is returned, the row is rejected with a descriptive
 * error message (e.g. "Setting this parent would create a circular
 * hierarchy").</li>
 * <li>If {@code false}, the UPDATE proceeds normally.</li>
 * </ol>
 *
 * @author BUDG Platform Team
 * @version 2.0
 * @since 1.0
 */
public class BulkUploadParentValidator {

    /**
     * Maximum number of parent-chain hops to follow before aborting the
     * cycle check.
     *
     * <p>
     * A depth of 1 000 is effectively infinite for any realistic catalogue
     * hierarchy, but guards against infinite loops caused by data corruption
     * that may already exist in the database (e.g. a cycle introduced by a
     * direct SQL edit outside of BUDG).
     * </p>
     */
    private static final int MAX_ANCESTOR_DEPTH = 1000;

    // -----------------------------------------------------------------------
    // Static lookup tables — schema metadata per entity type
    // -----------------------------------------------------------------------

    /**
     * Maps logical entity-type names to database table names.
     * Only hierarchical entities that support a parent column are registered
     * here; flat entities are intentionally excluded.
     */
    private static final Map<String, String> TABLE_MAP = new HashMap<>();

    /**
     * Maps logical entity-type names to their primary-key column name.
     * Casing matches the exact column name in the database (mixed casing is
     * a known schema inconsistency).
     */
    private static final Map<String, String> ID_COLUMN_MAP = new HashMap<>();

    /**
     * Maps logical entity-type names to the column that holds the optional
     * parent's primary key. A {@code NULL} value means the record is a
     * root node.
     */
    private static final Map<String, String> PARENT_COLUMN_MAP = new HashMap<>();

    /**
     * Maps logical entity-type names to their soft-delete timestamp column.
     * When building the ancestor-walk SQL, a predicate is added to exclude
     * logically deleted rows so the traversal does not follow phantom links.
     */
    private static final Map<String, String> DELETED_COLUMN_MAP = new HashMap<>();

    static {
        TABLE_MAP.put("Process", "process");
        TABLE_MAP.put("Policy", "policy");
        TABLE_MAP.put("Glossary", "glossary");
        TABLE_MAP.put("System", "system");
        TABLE_MAP.put("Regulation", "regulation");
        TABLE_MAP.put("Project", "project");
        TABLE_MAP.put("OrgUnit", "org_unit");
        TABLE_MAP.put("Client", "client");
        TABLE_MAP.put("Product", "product");

        ID_COLUMN_MAP.put("Process", "id");
        ID_COLUMN_MAP.put("Policy", "ID");
        ID_COLUMN_MAP.put("Glossary", "ID");
        ID_COLUMN_MAP.put("System", "id");
        ID_COLUMN_MAP.put("Regulation", "ID");
        ID_COLUMN_MAP.put("Project", "id");
        ID_COLUMN_MAP.put("OrgUnit", "ID");
        ID_COLUMN_MAP.put("Client", "ID");
        ID_COLUMN_MAP.put("Product", "id");

        // Note: the process table uses "parentid" (no underscore) — this was
        // a naming inconsistency introduced in an early schema version.
        PARENT_COLUMN_MAP.put("Process", "parentid"); // process table uses parentid, not parent_id
        PARENT_COLUMN_MAP.put("Policy", "ParentID");
        PARENT_COLUMN_MAP.put("Glossary", "Parent_ID");
        PARENT_COLUMN_MAP.put("System", "parent_id");
        PARENT_COLUMN_MAP.put("Regulation", "parent_id");
        PARENT_COLUMN_MAP.put("Project", "parentid");
        PARENT_COLUMN_MAP.put("OrgUnit", "Parent_ID");
        PARENT_COLUMN_MAP.put("Client", "Parent_ID");
        PARENT_COLUMN_MAP.put("Product", "parent_id");

        DELETED_COLUMN_MAP.put("Process", "deleteddatetime");
        DELETED_COLUMN_MAP.put("Policy", "DeletedDatetime");
        DELETED_COLUMN_MAP.put("Glossary", "Deleted_datetime");
        DELETED_COLUMN_MAP.put("System", "Deleted_datetime");
        DELETED_COLUMN_MAP.put("Regulation", "DeletedDatetime");
        DELETED_COLUMN_MAP.put("Project", "deletedatetime");
        DELETED_COLUMN_MAP.put("OrgUnit", "deleted_Date");
        DELETED_COLUMN_MAP.put("Client", "DeleteDatetime");
        DELETED_COLUMN_MAP.put("Product", "deleteddatetime");
    }

    // -----------------------------------------------------------------------
    // Core public method
    // -----------------------------------------------------------------------

    /**
     * Determines whether assigning {@code newParentId} as the parent of
     * {@code objectId} would introduce a circular reference in the hierarchy.
     *
     * <p>
     * <b>Algorithm:</b> Starting from {@code newParentId}, this method
     * walks up the ancestor chain by repeatedly looking up the parent of the
     * current node. At each step it checks whether the current node equals
     * {@code objectId}. If yes, {@code newParentId} is already a descendant
     * of {@code objectId} and the proposed change would create a cycle.
     * The walk terminates when:
     * </p>
     * <ul>
     * <li>A cycle is detected → returns {@code true}.</li>
     * <li>A root node is reached (parent is {@code NULL}) → returns
     * {@code false}.</li>
     * <li>A node is not found in the database (already deleted or missing)
     * → returns {@code false} (conservative: assume no cycle).</li>
     * <li>{@value #MAX_ANCESTOR_DEPTH} hops are exhausted → returns
     * {@code false} (guards against corrupt data with pre-existing
     * cycles).</li>
     * </ul>
     *
     * <p>
     * <b>Short-circuit:</b> When {@code objectId == newParentId} the method
     * immediately returns {@code true} without any database access — an object
     * cannot be its own parent.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> Issues one SELECT per ancestor level against
     * the database. No data is modified.
     * </p>
     *
     * @param conn        An active, caller-managed {@link java.sql.Connection}.
     *                    This method does not commit or roll back.
     * @param objectId    The primary key of the entity whose parent is being
     *                    changed (the entity that would become a child).
     * @param newParentId The primary key of the proposed new parent.
     * @param objectType  The logical entity type; must be one of
     *                    {@code "Process"}, {@code "Policy"}, {@code "Glossary"},
     *                    {@code "System"}, {@code "Regulation"},
     *                    {@code "Project"}, {@code "OrgUnit"}, {@code "Client"},
     *                    {@code "Product"}. Unrecognised types return
     *                    {@code false} (no cycle, safe to proceed).
     * @return {@code true} if the proposed parent assignment would create a
     *         circular hierarchy; {@code false} if it is safe to proceed.
     * @throws SQLException if a database access error occurs during the
     *                      ancestor-chain traversal.
     */
    public static boolean wouldCreateParentCycle(Connection conn, int objectId, int newParentId, String objectType)
            throws SQLException {
        // Trivial self-reference check — no DB query needed.
        if (objectId == newParentId) {
            return true;
        }
        String table = TABLE_MAP.get(objectType);
        String idCol = ID_COLUMN_MAP.get(objectType);
        String parentCol = PARENT_COLUMN_MAP.get(objectType);
        String deletedCol = DELETED_COLUMN_MAP.get(objectType);
        if (table == null || idCol == null || parentCol == null) {
            // Unknown objectType — cannot validate; allow the operation to
            // proceed and let other constraints handle it.
            return false;
        }
        // Build the ancestor-lookup SQL once; it is reused in the loop below.
        // The deleted condition is appended dynamically because not all tables
        // have a consistent soft-delete column naming convention.
        String deletedCondition = (deletedCol != null)
                ? " AND (" + deletedCol + " IS NULL OR " + deletedCol + " = '')"
                : "";
        String sql = "SELECT " + parentCol + " FROM " + table + " WHERE " + idCol + " = ?" + deletedCondition
                + " LIMIT 1";
        int current = newParentId;
        int depth = 0;
        // Walk up the ancestor chain. Each iteration moves one level up by
        // fetching the parent of the current node.
        while (current > 0 && depth < MAX_ANCESTOR_DEPTH) {
            if (current == objectId) {
                // We have reached objectId while walking up from newParentId,
                // which proves that objectId is an ancestor of newParentId.
                // Therefore setting objectId's parent to newParentId creates a cycle.
                return true;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, current);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        // Node not found — orphan or hard-deleted row; no cycle possible.
                        break;
                    }
                    Object parentVal = rs.getObject(parentCol);
                    if (rs.wasNull() || parentVal == null) {
                        // Reached a root node (no parent) — no cycle.
                        break;
                    }
                    if (parentVal instanceof Number) {
                        current = ((Number) parentVal).intValue();
                    } else {
                        // Unexpected type for parent column — treat as end of chain.
                        break;
                    }
                }
            }
            depth++;
        }
        return false;
    }
}
