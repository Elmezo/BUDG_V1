package com.example.budg_v2.util;

import com.example.budg_v2.service.SegmentValidationService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ImpactSegmentValidationUtil {
    private ImpactSegmentValidationUtil() {}

    public static SegmentValidationService.ValidationResult validateRelationships(
            int sourceObjectId,
            String sourceObjectType,
            Object relationshipsData,
            String targetObjectType,
            String... candidateKeys) throws SQLException {
        SegmentValidationService segmentValidationService = new SegmentValidationService();
        Set<String> targetKeys = new HashSet<>(Arrays.asList(candidateKeys));
        Set<Integer> seenTargetIds = new HashSet<>();
        return validateRecursive(
                segmentValidationService,
                sourceObjectId,
                sourceObjectType,
                relationshipsData,
                targetObjectType,
                targetKeys,
                seenTargetIds);
    }

    private static SegmentValidationService.ValidationResult validateRecursive(
            SegmentValidationService segmentValidationService,
            int sourceObjectId,
            String sourceObjectType,
            Object node,
            String targetObjectType,
            Set<String> candidateKeys,
            Set<Integer> seenTargetIds) throws SQLException {
        if (node == null) {
            return SegmentValidationService.ValidationResult.success();
        }

        if (node instanceof JsonObject jsonObject) {
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                if (candidateKeys.contains(entry.getKey())) {
                    Integer targetId = extractInteger(entry.getValue());
                    if (targetId != null && targetId > 0 && seenTargetIds.add(targetId)) {
                        SegmentValidationService.ValidationResult validationResult =
                                segmentValidationService.validateCrossSegmentRelationship(
                                        sourceObjectId, sourceObjectType, targetId, targetObjectType);
                        if (!validationResult.isValid) {
                            return validationResult;
                        }
                    }
                }

                SegmentValidationService.ValidationResult nestedResult = validateRecursive(
                        segmentValidationService,
                        sourceObjectId,
                        sourceObjectType,
                        entry.getValue(),
                        targetObjectType,
                        candidateKeys,
                        seenTargetIds);
                if (!nestedResult.isValid) {
                    return nestedResult;
                }
            }
            return SegmentValidationService.ValidationResult.success();
        }

        if (node instanceof JsonArray jsonArray) {
            for (JsonElement element : jsonArray) {
                SegmentValidationService.ValidationResult nestedResult = validateRecursive(
                        segmentValidationService,
                        sourceObjectId,
                        sourceObjectType,
                        element,
                        targetObjectType,
                        candidateKeys,
                        seenTargetIds);
                if (!nestedResult.isValid) {
                    return nestedResult;
                }
            }
            return SegmentValidationService.ValidationResult.success();
        }

        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if (candidateKeys.contains(key)) {
                    Integer targetId = extractInteger(value);
                    if (targetId != null && targetId > 0 && seenTargetIds.add(targetId)) {
                        SegmentValidationService.ValidationResult validationResult =
                                segmentValidationService.validateCrossSegmentRelationship(
                                        sourceObjectId, sourceObjectType, targetId, targetObjectType);
                        if (!validationResult.isValid) {
                            return validationResult;
                        }
                    }
                }

                SegmentValidationService.ValidationResult nestedResult = validateRecursive(
                        segmentValidationService,
                        sourceObjectId,
                        sourceObjectType,
                        value,
                        targetObjectType,
                        candidateKeys,
                        seenTargetIds);
                if (!nestedResult.isValid) {
                    return nestedResult;
                }
            }
            return SegmentValidationService.ValidationResult.success();
        }

        if (node instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                SegmentValidationService.ValidationResult nestedResult = validateRecursive(
                        segmentValidationService,
                        sourceObjectId,
                        sourceObjectType,
                        item,
                        targetObjectType,
                        candidateKeys,
                        seenTargetIds);
                if (!nestedResult.isValid) {
                    return nestedResult;
                }
            }
        }

        return SegmentValidationService.ValidationResult.success();
    }

    private static Integer extractInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof JsonPrimitive primitive) {
            if (primitive.isNumber()) {
                return primitive.getAsInt();
            }
            if (primitive.isString()) {
                try {
                    return Integer.parseInt(primitive.getAsString());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
        if (value instanceof JsonElement jsonElement) {
            return extractInteger(jsonElement);
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
