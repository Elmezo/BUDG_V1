package com.example.unisonsearch.repository;

import com.example.unisonsearch.model.SegmentAccessContext;

/**
 * Generates segment access predicates for SQL queries.
 * Mirrors QueryBuilder segment filtering logic for consistency.
 */
public class SegmentAccessSql {

    /**
     * Generate segment access predicate for a normalized facet.
     * Returns null if no filtering needed (SuperAdmin or facet without object type).
     * 
     * @param normalizedFacet Normalized facet name (e.g., "dataset", "system")
     * @param idColumnExpr SQL expression for the object ID column (e.g., "d.ID", "s.id")
     * @param accessCtx Precomputed access context
     * @return SQL predicate string, or null if no filtering needed
     */
    public static String predicateForFacet(String normalizedFacet, String idColumnExpr, SegmentAccessContext accessCtx) {
        if (accessCtx == null) {
            return null;
        }

        // Super Admin: apply cube-selected segment filter (effectiveSegments), same as any user.
        // If no segments are stored in context yet (legacy call-site), fall through to normal path
        // which will build the predicate using getSegmentIdList() / isEnterpriseSelected() derived
        // from effectiveSegments set on the context object.
        // Note: isSuperAdmin no longer bypasses filtering — the cube selection must be respected.

        // Get object type for this facet
        String objectType = getObjectTypeForFacet(normalizedFacet);
        if (objectType == null) {
            // No object type mapping (e.g., People, Role) - skip filtering
            return null;
        }

        // For ATTRIBUTE, use dataset-based filtering (match QueryBuilder logic)
        String effectiveIdExpr = idColumnExpr;
        String effectiveObjectType = objectType;
        if ("attribute".equalsIgnoreCase(normalizedFacet)) {
            // Attributes inherit segment from their dataset
            // If idColumnExpr is "a.ID", we need "a.Dataset_ID" for segment check
            if (idColumnExpr.toLowerCase().contains("a.id") || idColumnExpr.toLowerCase().contains("a2.id")) {
                effectiveIdExpr = idColumnExpr.replace(".ID", ".Dataset_ID").replace(".id", ".Dataset_ID");
            } else if (idColumnExpr.contains(".")) {
                // Has table alias, replace ID with Dataset_ID
                String tableAlias = idColumnExpr.substring(0, idColumnExpr.indexOf("."));
                effectiveIdExpr = tableAlias + ".Dataset_ID";
            } else {
                // No table alias - use Dataset_ID directly (matches query structure like "SELECT Dataset_ID FROM attribute")
                effectiveIdExpr = "Dataset_ID";
            }
            effectiveObjectType = "Dataset";
        }

        if (accessCtx.isAnonymous()) {
            // Anonymous users see Enterprise-only objects
            return buildEnterpriseOnlyPredicate(effectiveIdExpr, effectiveObjectType);
        } else {
            // All authenticated users (including Super Admin) see objects in their effective
            // (cube-selected) segments only.
            return buildSegmentFilterPredicate(effectiveIdExpr, effectiveObjectType, accessCtx);
        }
    }

    private static String buildSegmentFilterPredicate(String idExpr, String objectType, SegmentAccessContext accessCtx) {
        String segmentIdList = accessCtx.getSegmentIdList();
        
        if (accessCtx.isEnterpriseSelected()) {
            // Enterprise selected - include objects in selected segments OR objects without segment assignment
            return String.format("""
                    (
                        EXISTS (
                            SELECT 1 FROM segment_x_resource sxr
                            JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                            WHERE orr.Object_ID = %s
                            AND sot.Type = '%s'
                            AND sxr.Segment_ID IN (%s)
                            AND sxr.Deleted_At IS NULL
                        )
                        OR
                        NOT EXISTS (
                            SELECT 1 FROM segment_x_resource sxr
                            JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                            WHERE orr.Object_ID = %s
                            AND sot.Type = '%s'
                            AND sxr.Deleted_At IS NULL
                        )
                    )
                    """, idExpr, objectType, segmentIdList, idExpr, objectType);
        } else {
            // Enterprise not selected - only show objects explicitly in selected segments
            return String.format("""
                    EXISTS (
                        SELECT 1 FROM segment_x_resource sxr
                        JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                        JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                        WHERE orr.Object_ID = %s
                        AND sot.Type = '%s'
                        AND sxr.Segment_ID IN (%s)
                        AND sxr.Deleted_At IS NULL
                    )
                    """, idExpr, objectType, segmentIdList);
        }
    }

    private static String buildEnterpriseOnlyPredicate(String idExpr, String objectType) {
        return String.format("""
                (
                    EXISTS (
                        SELECT 1 FROM segment_x_resource sxr
                        JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                        JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                        WHERE orr.Object_ID = %s
                        AND sot.Type = '%s'
                        AND sxr.Segment_ID = 1
                        AND sxr.Deleted_At IS NULL
                    )
                    OR
                    NOT EXISTS (
                        SELECT 1 FROM segment_x_resource sxr
                        JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                        JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                        WHERE orr.Object_ID = %s
                        AND sot.Type = '%s'
                        AND sxr.Deleted_At IS NULL
                    )
                )
                """, idExpr, objectType, idExpr, objectType);
    }

    /**
     * Map normalized facet name to segment object type.
     * Returns null for facets that don't map to a single object type.
     */
    private static String getObjectTypeForFacet(String normalizedFacet) {
        if (normalizedFacet == null) return null;
        
        return switch (normalizedFacet.toLowerCase()) {
            case "dataset" -> "Dataset";
            case "attribute" -> "Dataset"; // Attributes inherit from dataset
            case "system" -> "System";
            case "glossary" -> "Glossary";
            case "process" -> "Process";
            case "project" -> "Project";
            case "product" -> "Product";
            case "policy" -> "Policy";
            case "legal_entity" -> "LegalEntity";
            case "business_area" -> "BusinessArea";
            case "capability" -> "Capability";
            case "client" -> "Client";
            case "committee" -> "Committee";
            case "org_unit" -> "OrgUnit";
            case "geography" -> "Geography";
            case "regulation" -> "Regulation";
            case "regulator" -> "Regulator";
            case "regulatory_theme" -> "RegulatoryTheme";
            case "interface" -> "SystemInterface";
            case "people" -> null; // Handled via segment_x_identity
            case "role" -> null; // Spans multiple object types
            case "data_quality" -> "Dataset";
            default -> null;
        };
    }
}

