package com.example.budg_v2.util;

import com.example.budg_v2.service.SegmentAccessService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hides identifying details for hierarchy nodes that belong to private segments
 * the current user cannot access.
 *
 * <p>Used by the Relationship → Hierarchy views (policy, capability, project,
 * product, business area, ...) so the tree structure is preserved while names
 * and descriptions of locked nodes are replaced with a non-revealing placeholder.
 */
public final class HierarchyAccessMasker {

    private static final Logger log = LoggerFactory.getLogger(HierarchyAccessMasker.class);

    public static final String PLACEHOLDER = "xxxx";

    /** Fields that may carry a recognisable label and must be hidden when masked. */
    private static final String[] LABEL_FIELDS = {
            "name", "displayName",
            "primaryName", "primaryname",
            "longName", "longname",
            "refNumber", "refnumber",
            "typeName",
            "description"
    };

    private HierarchyAccessMasker() {
    }

    /**
     * Mask in place the entries of a {@code JsonArray} of hierarchy nodes that
     * belong to segments the user cannot access.
     */
    public static void mask(JsonArray items, String objectType, int userId) {
        if (items == null || items.size() == 0) return;
        Set<Integer> accessibleIds = resolveAccessibleIds(objectType, userId);
        if (accessibleIds == null) return; // super admin / failure → no masking

        for (JsonElement el : items) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            Integer id = extractIdFromJson(obj);
            if (id == null) continue;
            if (!accessibleIds.contains(id)) {
                applyMaskToJson(obj);
            }
        }
    }

    /**
     * Mask in place the entries of a {@code List<Map<String,Object>>} of
     * hierarchy nodes (the shape returned by lineage DAOs).
     */
    public static List<Map<String, Object>> mask(List<Map<String, Object>> items,
                                                 String objectType,
                                                 int userId) {
        if (items == null || items.isEmpty()) return items;
        Set<Integer> accessibleIds = resolveAccessibleIds(objectType, userId);
        if (accessibleIds == null) return items;

        for (Map<String, Object> item : items) {
            Integer id = extractIdFromMap(item);
            if (id == null) continue;
            if (!accessibleIds.contains(id)) {
                applyMaskToMap(item);
            }
        }
        return items;
    }

    /**
     * @return the set of object IDs the user can access, or {@code null} when no
     *         masking should be applied (anonymous request, super admin, or a
     *         lookup error – fail open to preserve current behaviour).
     */
    private static Set<Integer> resolveAccessibleIds(String objectType, int userId) {
        if (userId <= 0 || objectType == null) return null;
        try {
            if (SegmentAccessService.isSuperAdmin(userId)) return null;
            return SegmentAccessService.getAccessibleObjectIds(userId, canonicalType(objectType));
        } catch (SQLException e) {
            log.warn("HierarchyAccessMasker: failed to resolve accessible IDs for type={} user={}: {}",
                    objectType, userId, e.getMessage());
            return null;
        }
    }

    private static void applyMaskToJson(JsonObject obj) {
        obj.addProperty("masked", true);
        for (String f : LABEL_FIELDS) {
            if (obj.has(f) && !obj.get(f).isJsonNull()) {
                obj.addProperty(f, PLACEHOLDER);
            }
        }
    }

    private static void applyMaskToMap(Map<String, Object> item) {
        item.put("masked", true);
        for (String f : LABEL_FIELDS) {
            if (item.containsKey(f) && item.get(f) != null) {
                item.put(f, PLACEHOLDER);
            }
        }
    }

    private static Integer extractIdFromJson(JsonObject obj) {
        for (String key : new String[] {"id", "ID", "Id"}) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                try {
                    return obj.get(key).getAsInt();
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        return null;
    }

    private static Integer extractIdFromMap(Map<String, Object> item) {
        for (String key : new String[] {"id", "ID", "Id"}) {
            Object v = item.get(key);
            if (v == null) continue;
            if (v instanceof Number) return ((Number) v).intValue();
            try {
                return Integer.parseInt(v.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return null;
    }

    /** Normalize the object type to the values stored in {@code segment_object_type.Type}. */
    private static String canonicalType(String objectType) {
        String compact = objectType.trim().replace(" ", "").replace("_", "").replace("-", "").toLowerCase();
        return switch (compact) {
            case "policy", "policies" -> "Policy";
            case "capability", "capabilities" -> "Capability";
            case "project", "projects" -> "Project";
            case "product", "products" -> "Product";
            case "businessarea", "businessareas" -> "BusinessArea";
            case "process", "processes" -> "Process";
            case "client", "clients" -> "Client";
            case "system", "systems" -> "System";
            case "regulation", "regulations" -> "Regulation";
            case "committee", "committees" -> "Committee";
            case "legalentity", "legalentities", "legal" -> "LegalEntity";
            case "regulatorytheme", "regulatorythemes" -> "RegulatoryTheme";
            case "glossary", "glossaries" -> "Glossary";
            default -> objectType;
        };
    }
}
