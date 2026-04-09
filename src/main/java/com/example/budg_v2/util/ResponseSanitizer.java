package com.example.budg_v2.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Set;

/**
 * V-07 Stakeholder Isolation Rule:
 * When a user accesses an object via Stakeholder-only path (no segment access),
 * the response must be stripped of hierarchical and segment metadata.
 *
 * Fields removed: segmentId, segmentName, parentId, childrenIds, and variants.
 */
public final class ResponseSanitizer {

    private ResponseSanitizer() {}

    /**
     * Fields that reveal segment membership or object hierarchy — must be removed
     * for stakeholder-only access so that isolation is fully enforced.
     */
    private static final Set<String> STAKEHOLDER_RESTRICTED_FIELDS = Set.of(
        // Segment metadata (added by SegmentResponseUtil)
        "segmentId", "segmentName",
        // Parent references (hierarchy)
        "parentId", "parent_id", "parentName", "parent_name",
        "ParentId", "Parent_ID", "Parent_id",
        // Children references (hierarchy)
        "childrenIds", "children_ids", "children", "childObjects",
        "ChildrenIds", "Children_IDs",
        // Segment membership lists
        "segmentNames", "segment_names", "segmentIds", "segment_ids",
        "segmentInfo", "Segment_ID"
    );

    /**
     * If the request carries the {@code stakeholderAccessOnly} attribute (set by
     * {@code AuthFilter} when access is granted via stakeholder path), strip all
     * restricted fields from the mutable response map.
     *
     * <p>Call this method on every object-detail response <em>before</em> writing
     * the JSON to the servlet output stream.</p>
     *
     * @param result mutable Map that will be serialised as JSON
     * @param req    the current HTTP request
     */
    public static void applyStakeholderIsolation(Map<String, Object> result,
                                                  HttpServletRequest req) {
        if (result == null || req == null) return;
        if (!Boolean.TRUE.equals(req.getAttribute("stakeholderAccessOnly"))) return;

        for (String field : STAKEHOLDER_RESTRICTED_FIELDS) {
            result.remove(field);
        }
    }

    /**
     * Returns {@code true} when the current request is a stakeholder-only access path.
     */
    public static boolean isStakeholderOnly(HttpServletRequest req) {
        return req != null && Boolean.TRUE.equals(req.getAttribute("stakeholderAccessOnly"));
    }
}
