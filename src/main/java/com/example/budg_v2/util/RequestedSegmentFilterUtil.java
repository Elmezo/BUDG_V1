package com.example.budg_v2.util;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.service.SegmentValidationService;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public final class RequestedSegmentFilterUtil {
    private RequestedSegmentFilterUtil() {}

    public static Integer getRequestedSegmentId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String raw = request.getParameter("segmentId");
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Resolve the effective (private) segment ID for impact-list filtering.
     *
     * <p>Priority: (1) {@code segmentId} when it is a private segment ({@code > 1});
     * (2) DB lookup via {@code sourceObjectId} + {@code sourceObjectType} when that
     * resolves to a private segment; (3) {@code segmentId} as-is (Enterprise 1) or
     * {@code null}.</p>
     */
    public static Integer resolveEffectiveSegmentId(HttpServletRequest request, SegmentDAO segmentDAO)
            throws SQLException {
        Integer explicit = getRequestedSegmentId(request);

        if (explicit != null && explicit > 1) {
            return explicit;
        }

        String sourceObjectIdParam = request.getParameter("sourceObjectId");
        String sourceObjectType = request.getParameter("sourceObjectType");

        if (sourceObjectIdParam != null && !sourceObjectIdParam.trim().isEmpty()
                && sourceObjectType != null && !sourceObjectType.trim().isEmpty()) {
            try {
                int sourceObjectId = Integer.parseInt(sourceObjectIdParam.trim());
                if (sourceObjectId > 0 && segmentDAO != null) {
                    int sourceSegmentId = segmentDAO.getObjectSegmentId(
                            sourceObjectId, sourceObjectType.trim());
                    if (sourceSegmentId > 1) {
                        return sourceSegmentId;
                    }
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }

        return explicit;
    }

    public static <T> List<T> filterByRequestedSegment(
            List<T> items,
            Integer requestedSegmentId,
            String objectType,
            ToIntFunction<T> idExtractor) throws SQLException {
        if (items == null || items.isEmpty() || requestedSegmentId == null || requestedSegmentId <= 0) {
            return items;
        }

        SegmentDAO segmentDAO = new SegmentDAO();
        SegmentValidationService validationService = new SegmentValidationService();
        List<T> filtered = new ArrayList<>();

        for (T item : items) {
            int objectId = idExtractor.applyAsInt(item);
            int targetSegmentId = segmentDAO.getObjectSegmentId(objectId, objectType);
            if (targetSegmentId < 0) {
                targetSegmentId = 1;
            }

            SegmentValidationService.ValidationResult validationResult =
                    validationService.validateCrossSegmentRelationshipBySegmentIds(
                            requestedSegmentId, targetSegmentId);
            if (validationResult.isValid) {
                filtered.add(item);
            }
        }

        return filtered;
    }
}
