package com.example.budg_v2.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manual-create / edit validation: primary (display) name must be unique only within a segment
 * (via {@code object_reference} → {@code segment_x_resource}), not globally.
 * <p>
 * Does not modify bulk-upload code paths. Legal entities use global uniqueness elsewhere.
 * </p>
 */
public final class SegmentScopedPrimaryNameCheck {

    private SegmentScopedPrimaryNameCheck() {}

    private static final class Meta {
        final String table;
        final String idColumn;
        final String nameColumn;
        final String deletedColumn;
        final String objectType;

        Meta(String table, String idColumn, String nameColumn, String deletedColumn, String objectType) {
            this.table = table;
            this.idColumn = idColumn;
            this.nameColumn = nameColumn;
            this.deletedColumn = deletedColumn;
            this.objectType = objectType;
        }
    }

    private static final Map<String, Meta> BY_FACET = new HashMap<>();

    static {
        BY_FACET.put("Product", new Meta("product", "id", "primaryname", "deleteddatetime", "Product"));
        BY_FACET.put("Dataset", new Meta("dataset", "ID", "PrimaryName", "DeletedDatetime", "Dataset"));
        BY_FACET.put("System", new Meta("system", "id", "Name", "Deleted_datetime", "System"));
        /* Long display name on system row; same object type linkage as {@code System} */
        BY_FACET.put("SystemLongName", new Meta("system", "id", "Long_Name", "Deleted_datetime", "System"));
        BY_FACET.put("Capability", new Meta("capability", "ID", "PrimaryName", "DeletedDatetime", "Capability"));
        BY_FACET.put("Interface", new Meta("interface", "id", "Name", "deleted_datetime", "SystemInterface"));
        BY_FACET.put("Glossary", new Meta("glossary", "ID", "Name", "Deleted_datetime", "Glossary"));
        BY_FACET.put("RegulatoryTheme", new Meta("regulatorytheme", "ID", "PrimaryName", "DeletedDatetime", "RegulatoryTheme"));
        BY_FACET.put("Regulation", new Meta("regulation", "ID", "PrimaryName", "DeletedDatetime", "Regulation"));
        BY_FACET.put("Policy", new Meta("policy", "ID", "PrimaryName", "DeletedDatetime", "Policy"));
        BY_FACET.put("Process", new Meta("process", "id", "PrimaryName", "deleteddatetime", "Process"));
        BY_FACET.put("Project", new Meta("project", "id", "PrimaryName", "deletedatetime", "Project"));
        BY_FACET.put("Committee", new Meta("committee", "ID", "PrimaryName", "DeleteDatetime", "Committee"));
        BY_FACET.put("Geography", new Meta("geography", "ID", "PrimaryName", "DeletedDatetime", "Geography"));
        BY_FACET.put("Regulator", new Meta("regulator", "ID", "PrimaryName", "DeletedDatetime", "Regulator"));
        BY_FACET.put("OrgUnit", new Meta("org_unit", "ID", "Name", "deleted_Date", "OrgUnit"));
        BY_FACET.put("BusinessArea", new Meta("business_area", "ID", "PrimaryName", "deletedatetime", "BusinessArea"));
        BY_FACET.put("Client", new Meta("client", "ID", "PrimaryName", "DeleteDatetime", "Client"));
    }

    /**
     * @param facet frontend facet key (e.g. {@code "Policy"}, {@code "Product"})
     * @param excludePk primary key of row to exclude (edit), or {@code null} on create
     * @return {@code true} if another non-deleted object in {@code segmentId} already has this name
     */
    public static boolean exists(Connection conn, String facet, String name, long segmentId, Integer excludePk)
            throws SQLException {
        if (excludePk == null) {
            return existsExcluding(conn, facet, name, segmentId, Collections.emptyList());
        }
        return existsExcluding(conn, facet, name, segmentId, Collections.singletonList(excludePk));
    }

    /**
     * Like {@link #exists} but excludes multiple primary keys (e.g. system row plus DF_CR clone row).
     */
    public static boolean existsExcluding(Connection conn, String facet, String name, long segmentId,
            Collection<Integer> excludePks) throws SQLException {
        if (conn == null || name == null || name.trim().isEmpty() || facet == null) {
            return false;
        }
        Meta meta = BY_FACET.get(facet);
        if (meta == null) {
            return false;
        }
        List<Integer> ids = new ArrayList<>();
        if (excludePks != null) {
            for (Integer id : excludePks) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        String normalized = name.trim();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT 1 FROM ").append(meta.table).append(" t ");
        sql.append("JOIN object_reference orr ON t.").append(meta.idColumn).append(" = orr.Object_ID ");
        sql.append("JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID ");
        sql.append("JOIN segment_x_resource sxr ON orr.ID = sxr.Object_Reference_ID ");
        sql.append("WHERE sot.Type = ? ");
        sql.append("AND sxr.Segment_ID = ? ");
        sql.append("AND LOWER(t.").append(meta.nameColumn).append(") = LOWER(?) ");
        sql.append("AND sxr.Deleted_At IS NULL ");
        if (meta.deletedColumn != null) {
            sql.append("AND (t.").append(meta.deletedColumn).append(" IS NULL OR t.").append(meta.deletedColumn)
                    .append(" = '') ");
        }
        if (!ids.isEmpty()) {
            sql.append("AND t.").append(meta.idColumn).append(" NOT IN (");
            sql.append(String.join(", ", Collections.nCopies(ids.size(), "?")));
            sql.append(") ");
        }
        sql.append("LIMIT 1");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            ps.setString(i++, meta.objectType);
            ps.setLong(i++, segmentId);
            ps.setString(i++, normalized);
            for (Integer id : ids) {
                ps.setInt(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
