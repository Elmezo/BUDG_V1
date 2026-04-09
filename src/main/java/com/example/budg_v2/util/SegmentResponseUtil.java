package com.example.budg_v2.util;

import com.example.budg_v2.dao.SegmentDAO;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * Utility helpers for enriching API responses with segment metadata so that
 * front-end editors can pre-select the correct segment value.
 */
public final class SegmentResponseUtil {
    private static final int ENTERPRISE_SEGMENT_ID = 1;
    private static final String ENTERPRISE_SEGMENT_NAME = "Enterprise";

    private SegmentResponseUtil() {
        // Utility class
    }

    public record SegmentInfo(int id, String name) {}

    /**
     * Resolve the current segment assignment for a given object.
     *
     * @param segmentDAO Segment DAO instance
     * @param objectId   Object identifier
     * @param objectType Object type label used in segment tables
     * @return SegmentInfo with id/name. Missing assignments are normalized to
     * Enterprise for consistent UI behavior.
     */
    public static SegmentInfo resolveSegmentInfo(SegmentDAO segmentDAO, int objectId, String objectType) {
        if (segmentDAO == null || objectId <= 0 || objectType == null || objectType.isBlank()) {
            return new SegmentInfo(ENTERPRISE_SEGMENT_ID, ENTERPRISE_SEGMENT_NAME);
        }

        try {
            int currentSegmentId = segmentDAO.getObjectSegmentId(objectId, objectType);
            System.out.println("🔍 [SegmentResponseUtil] getObjectSegmentId returned: " + currentSegmentId + " for " + objectType + " #" + objectId);
            
            // If no segment found (-1), normalize to Enterprise.
            if (currentSegmentId == -1) {
                System.out.println("⚠️ [SegmentResponseUtil] " + objectType + " #" + objectId + " has no segment assignment; defaulting to Enterprise");
                return new SegmentInfo(ENTERPRISE_SEGMENT_ID, ENTERPRISE_SEGMENT_NAME);
            }
            
            // If segment found, use it
            if (currentSegmentId > 0) {
                // Get segment name
                Map<String, Object> segment = segmentDAO.getSegmentById(currentSegmentId);
                System.out.println("🔍 [SegmentResponseUtil] getSegmentById(" + currentSegmentId + ") returned: " + segment);
                String segmentName = "Unknown";
                if (segment != null) {
                    Object nameObj = segment.get("name");
                    if (nameObj == null) {
                        nameObj = segment.get("Name");
                    }
                    if (nameObj != null) {
                        segmentName = nameObj.toString();
                    }
                }
                System.out.println("✅ [SegmentResponseUtil] Final result for " + objectType + " #" + objectId + ": segmentId=" + currentSegmentId + ", segmentName=" + segmentName);
                return new SegmentInfo(currentSegmentId, segmentName);
            }
        } catch (Exception e) {
            System.err.println("[SegmentResponseUtil] Unable to resolve segment metadata for "
                    + objectType + " #" + objectId + ": " + e.getMessage());
            e.printStackTrace();
            return new SegmentInfo(ENTERPRISE_SEGMENT_ID, ENTERPRISE_SEGMENT_NAME);
        }

        return new SegmentInfo(ENTERPRISE_SEGMENT_ID, ENTERPRISE_SEGMENT_NAME);
    }

    /**
     * Apply segment metadata to a mutable map before JSON serialization.
     */
    public static void applySegmentInfo(Map<String, Object> target, SegmentInfo info) {
        if (target == null || info == null) {
            return;
        }
        target.put("segmentId", info.id());
        target.put("segmentName", info.name());
    }

    /**
     * Apply segment metadata directly to a JsonObject response.
     */
    public static void applySegmentInfo(JsonObject target, SegmentInfo info) {
        if (target == null || info == null) {
            return;
        }
        target.addProperty("segmentId", info.id());
        target.addProperty("segmentName", info.name());
    }

    /**
     * V-07: Request-aware variant for Map responses.
     * Skips adding segment metadata when the request is a stakeholder-only access path.
     */
    public static void applySegmentInfo(Map<String, Object> target, SegmentInfo info,
                                        HttpServletRequest req) {
        if (ResponseSanitizer.isStakeholderOnly(req)) return;
        applySegmentInfo(target, info);
    }

    /**
     * V-07: Request-aware variant for JsonObject responses.
     * Skips adding segment metadata when the request is a stakeholder-only access path.
     */
    public static void applySegmentInfo(JsonObject target, SegmentInfo info,
                                        HttpServletRequest req) {
        if (ResponseSanitizer.isStakeholderOnly(req)) return;
        applySegmentInfo(target, info);
    }
}

