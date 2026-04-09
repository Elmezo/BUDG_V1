package com.example.budg_v2.util;

import com.example.budg_v2.service.SegmentAccessService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V-05 / C-26: Maps "Hidden Relationship Exists" enforcement.
 *
 * <p>When returning related objects on the Maps/Impact tab, each related object
 * must be segment-access-checked.  If the requesting user does not have access to
 * the target segment, the full object data must be replaced with a minimal hidden
 * marker so the front-end can display a "hidden relationship exists" indicator.</p>
 */
public final class RelationshipAccessUtil {

    private RelationshipAccessUtil() {}

    /**
     * Filter a list of relationship maps so that any related object whose segment
     * the current user cannot access is replaced with a hidden marker.
     *
     * @param relationships  mutable list of relationship maps returned by a DAO
     * @param userId         current user ID (0 = guest/unauthenticated)
     * @param relatedType    segment object-type string used in segment tables (e.g. "Glossary")
     * @param relatedIdField map key holding the related object's primary ID
     * @return new list with inaccessible entries replaced by hidden markers
     */
    public static List<Map<String, Object>> filterBySegmentAccess(
            List<Map<String, Object>> relationships,
            int userId,
            String relatedType,
            String relatedIdField) {

        if (relationships == null || relationships.isEmpty()) return relationships;
        if (userId <= 0) return hiddenList(relationships, relatedType, relatedIdField);

        List<Map<String, Object>> result = new ArrayList<>(relationships.size());
        for (Map<String, Object> rel : relationships) {
            Object idObj = rel.get(relatedIdField);
            if (idObj == null) {
                result.add(rel);
                continue;
            }
            int relatedId;
            try {
                relatedId = ((Number) idObj).intValue();
            } catch (ClassCastException e) {
                result.add(rel);
                continue;
            }

            boolean canAccess;
            try {
                canAccess = SegmentAccessService.canAccessObject(userId, relatedId, relatedType);
            } catch (Exception e) {
                // Fail-safe: hide on error
                canAccess = false;
            }

            // E-10 / TC-015: also hide soft-deleted related objects
            if (canAccess && SegmentAccessService.isSoftDeleted(relatedId, relatedType)) {
                canAccess = false;
            }

            if (canAccess) {
                result.add(rel);
            } else {
                result.add(buildHiddenMarker(relatedId, relatedType));
            }
        }
        return result;
    }

    /**
     * Convenience overload that reads the user ID from the request attributes set
     * by {@code AuthFilter} (attribute {@code "userId"}).
     */
    public static List<Map<String, Object>> filterBySegmentAccess(
            List<Map<String, Object>> relationships,
            HttpServletRequest req,
            String relatedType,
            String relatedIdField) {

        Integer userId = UserContextUtil.getCurrentUserIdOrNull(req);
        return filterBySegmentAccess(relationships, userId != null ? userId : 0,
                relatedType, relatedIdField);
    }

    /**
     * Count and return the number of hidden entries that would result from the filter.
     * Use alongside {@link #filterBySegmentAccess} when a {@code "hiddenCount"} field
     * should be added to the response envelope.
     */
    public static int countHidden(List<Map<String, Object>> filtered) {
        if (filtered == null) return 0;
        int count = 0;
        for (Map<String, Object> entry : filtered) {
            if (Boolean.TRUE.equals(entry.get("hidden"))) count++;
        }
        return count;
    }

    // -----------------------------------------------------------------------

    private static Map<String, Object> buildHiddenMarker(int id, String type) {
        Map<String, Object> marker = new HashMap<>(3);
        marker.put("hidden", true);
        marker.put("id", id);
        marker.put("type", type);
        return marker;
    }

    private static List<Map<String, Object>> hiddenList(
            List<Map<String, Object>> relationships,
            String relatedType,
            String relatedIdField) {
        List<Map<String, Object>> result = new ArrayList<>(relationships.size());
        for (Map<String, Object> rel : relationships) {
            Object idObj = rel.get(relatedIdField);
            if (idObj instanceof Number) {
                result.add(buildHiddenMarker(((Number) idObj).intValue(), relatedType));
            } else {
                result.add(rel);
            }
        }
        return result;
    }
}
