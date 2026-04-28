package com.example.budg_v2.util;

import com.example.budg_v2.dao.FacetChangesDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Binds custom field values ({@code Custom_Field_Data.Facet_Object_ID}) to the pending clone row
 * (nobject_id) for automatic change requests on Glossary, Dataset, System, and Process — same as
 * core facet fields: original view reads canonical id; changes view and saves use the clone until
 * apply (promote) or discard (delete clone rows).
 */
public final class CustomFieldPendingFacetHelper {

    private static final Logger logger = LoggerFactory.getLogger(CustomFieldPendingFacetHelper.class);

    private CustomFieldPendingFacetHelper() {}

    /**
     * @param modulePrimaryName row from {@code module.primaryname} (e.g. "Glossary", "Data Sets")
     */
    public static String toFacetChangesKeyFromModulePrimaryName(String modulePrimaryName) {
        if (modulePrimaryName == null) {
            return null;
        }
        String n = modulePrimaryName.trim().toLowerCase();
        if ("glossary".equals(n)) {
            return "glossary";
        }
        if ("data sets".equals(n) || "data set".equals(n) || "dataset".equals(n)) {
            return "dataset";
        }
        if ("system".equals(n) || "systems".equals(n)) {
            return "system";
        }
        if ("process".equals(n) || "processes".equals(n)) {
            return "process";
        }
        return null;
    }

    private static Integer getModuleIdForFacetKey(Connection conn, String facetChangesKey) throws SQLException {
        if (facetChangesKey == null) {
            return null;
        }
        String[] primaryCandidates;
        switch (facetChangesKey.toLowerCase()) {
            case "glossary":
                primaryCandidates = new String[] { "Glossary" };
                break;
            case "dataset":
                primaryCandidates = new String[] { "Data Sets", "Data Set" };
                break;
            case "system":
                primaryCandidates = new String[] { "System" };
                break;
            case "process":
                primaryCandidates = new String[] { "Process" };
                break;
            default:
                return null;
        }
        for (String primary : primaryCandidates) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM module WHERE primaryname = ?")) {
                ps.setString(1, primary);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        }
        return null;
    }

    /**
     * @param viewParam        request {@code view} query (use {@code "changes"} for View Changes toggle)
     * @param forPendingSave   when true (POST /data), use clone id whenever an active auto CR mapping exists
     */
    public static int resolveEffectiveFacetObjectId(Connection conn, int moduleId, String facetChangesKey,
                                                      int canonicalObjectId, String viewParam, boolean forPendingSave)
            throws SQLException {
        if (facetChangesKey == null) {
            return canonicalObjectId;
        }
        FacetChangesDAO dao = new FacetChangesDAO();
        Integer facetTypeId = dao.getFacetId(facetChangesKey);
        if (facetTypeId == null) {
            return canonicalObjectId;
        }
        Integer crId = dao.getActiveAutomaticChangeRequestId(facetTypeId, canonicalObjectId);
        if (crId == null) {
            return canonicalObjectId;
        }
        Integer nobjectId = dao.getNObjectId(facetChangesKey, canonicalObjectId, "summary", crId);
        if (nobjectId == null) {
            return canonicalObjectId;
        }
        boolean wantClone = forPendingSave || "changes".equalsIgnoreCase(viewParam);
        if (!wantClone) {
            return canonicalObjectId;
        }
        ensurePendingCloneHasCustomFieldRows(conn, moduleId, canonicalObjectId, nobjectId);
        return nobjectId;
    }

    /**
     * If the clone has no custom field rows yet, copy all rows from the canonical object so
     * "View Changes" shows the same baseline as original until the user edits.
     */
    public static void ensurePendingCloneHasCustomFieldRows(Connection conn, int moduleId,
                                                            int originalObjectId, int cloneObjectId) throws SQLException {
        try (PreparedStatement cnt = conn.prepareStatement("""
                SELECT COUNT(*) FROM Custom_Field_Data cfd
                INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
                WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
                """)) {
            cnt.setInt(1, moduleId);
            cnt.setInt(2, cloneObjectId);
            try (ResultSet rs = cnt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }

        String insertSql = """
                INSERT INTO Custom_Field_Data
                (Custom_Field_Metadata_ID, Custom_Field_Enum_ID, Facet_Object_ID, Custom_Field_Value, LastUpdate_UserID, CreateDatetime)
                SELECT cfd.Custom_Field_Metadata_ID, cfd.Custom_Field_Enum_ID, ?, cfd.Custom_Field_Value, cfd.LastUpdate_UserID, CURRENT_TIMESTAMP
                FROM Custom_Field_Data cfd
                INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
                WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
                """;
        try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
            ins.setInt(1, cloneObjectId);
            ins.setInt(2, moduleId);
            ins.setInt(3, originalObjectId);
            int copied = ins.executeUpdate();
            if (copied > 0) {
                logger.info("[CustomFieldPending] Seeded {} custom field row(s) onto pending clone {} from object {}",
                        copied, cloneObjectId, originalObjectId);
            }
        }
    }

    /**
     * After summary row apply: move pending custom field values from clone to canonical object.
     */
    public static void promotePendingCloneCustomFields(Connection conn, String facetChangesKey,
                                                       int originalObjectId, int cloneObjectId) throws SQLException {
        Integer moduleId = getModuleIdForFacetKey(conn, facetChangesKey);
        if (moduleId == null) {
            return;
        }
        try (PreparedStatement del = conn.prepareStatement("""
                DELETE cfd FROM Custom_Field_Data cfd
                INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
                WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
                """)) {
            del.setInt(1, moduleId);
            del.setInt(2, originalObjectId);
            del.executeUpdate();
        }
        try (PreparedStatement upd = conn.prepareStatement("""
                UPDATE Custom_Field_Data cfd
                INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
                SET cfd.Facet_Object_ID = ?
                WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
                """)) {
            upd.setInt(1, originalObjectId);
            upd.setInt(2, moduleId);
            upd.setInt(3, cloneObjectId);
            int moved = upd.executeUpdate();
            if (moved > 0) {
                logger.info("[CustomFieldPending] Promoted {} custom field row(s) from clone {} to object {} (facet {})",
                        moved, cloneObjectId, originalObjectId, facetChangesKey);
            }
        }
    }

    /**
     * Discard: remove custom field rows stored against the pending clone id only.
     */
    public static void deleteCloneCustomFields(Connection conn, String facetChangesKey, int cloneObjectId)
            throws SQLException {
        Integer moduleId = getModuleIdForFacetKey(conn, facetChangesKey);
        if (moduleId == null) {
            return;
        }
        try (PreparedStatement delAudit = conn.prepareStatement("""
                DELETE cfa FROM Custom_Field_Data_Audit cfa
                INNER JOIN Custom_Field_Data cfd ON cfa.ID = cfd.ID
                INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
                WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
                """)) {
            delAudit.setInt(1, moduleId);
            delAudit.setInt(2, cloneObjectId);
            delAudit.executeUpdate();
        }
        try (PreparedStatement del = conn.prepareStatement("""
                DELETE cfd FROM Custom_Field_Data cfd
                INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
                WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
                """)) {
            del.setInt(1, moduleId);
            del.setInt(2, cloneObjectId);
            int removed = del.executeUpdate();
            if (removed > 0) {
                logger.info("[CustomFieldPending] Discarded {} custom field row(s) for clone {} (facet {})",
                        removed, cloneObjectId, facetChangesKey);
            }
        }
    }

}
