package com.example.budg_v2.service;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure rules service for facet classification and table exclusion.
 * 
 * This service provides classification logic for:
 * - Allowed vs forbidden facets for bulk migration
 * - Table exclusion patterns (audit, people, etc.)
 * 
 * NO UI logic, NO Excel logic, NO HTTP logic, NO database queries.
 * Pure rules only - reusable across all layers.
 */
public class FacetClassificationService {

    // ========== FORBIDDEN FACETS ==========
    // These facets CANNOT be bulk migrated (BUDG rule)
    private static final Set<String> FORBIDDEN_FACETS = Set.of(
            "people",
            "changerequest",
            "workflow",
            "task",
            "activetask",
            "physical_field",
            "physicalfield");

    // ========== ALLOWED FACETS ==========
    // These facets CAN be bulk migrated (BUDG-compliant)
    private static final Set<String> ALLOWED_FACETS = Set.of(
            "glossary",
            "dataset",
            "attribute",
            "system",
            "process",
            "business_area",
            "capability",
            "client",
            "committee",
            "geography",
            "interface",
            "legal_entity",
            "org_unit",
            "policy",
            "product",
            "project",
            "regulation",
            "regulator",
            "regulatory_theme");

    // ========== FORBIDDEN TABLE PATTERNS ==========
    // Tables matching these patterns are excluded from exports
    private static final Set<Pattern> FORBIDDEN_TABLE_PATTERNS = Set.of(
            Pattern.compile(".*_x_objectxpeople", Pattern.CASE_INSENSITIVE),
            Pattern.compile("people", Pattern.CASE_INSENSITIVE),
            Pattern.compile("changerequest.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("audit.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*_audit_history", Pattern.CASE_INSENSITIVE),
            Pattern.compile("follow.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("auth_.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("dashboard.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("workflow.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("task.*", Pattern.CASE_INSENSITIVE));

    /**
     * Check if a facet is allowed for bulk migration.
     * 
     * @param facetCode The facet code (e.g., "dataset", "glossary")
     * @return true if facet can be bulk migrated, false otherwise
     */
    public static boolean isFacetAllowed(String facetCode) {
        if (facetCode == null || facetCode.isEmpty()) {
            return false;
        }

        // Normalize facet code
        String normalized = normalizeFacetCode(facetCode);

        // Check if explicitly forbidden
        if (FORBIDDEN_FACETS.contains(normalized)) {
            return false;
        }

        // Check if in allowed list
        return ALLOWED_FACETS.contains(normalized);
    }

    /**
     * Check if a table should be excluded from exports.
     * 
     * @param tableName The table name to check
     * @return true if table should be excluded, false otherwise
     */
    public static boolean isTableForbidden(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }

        // Check against all forbidden patterns
        for (Pattern pattern : FORBIDDEN_TABLE_PATTERNS) {
            if (pattern.matcher(tableName).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get set of forbidden facets.
     * 
     * @return Unmodifiable set of forbidden facet codes
     */
    public static Set<String> getForbiddenFacets() {
        return Collections.unmodifiableSet(FORBIDDEN_FACETS);
    }

    /**
     * Get set of allowed facets.
     * 
     * @return Unmodifiable set of allowed facet codes
     */
    public static Set<String> getAllowedFacets() {
        return Collections.unmodifiableSet(ALLOWED_FACETS);
    }

    /**
     * Normalize facet code for consistent comparison.
     * Handles variations like "data-sets", "DATA_SETS", etc.
     * 
     * @param facetCode The facet code to normalize
     * @return Normalized facet code
     */
    private static String normalizeFacetCode(String facetCode) {
        if (facetCode == null) {
            return null;
        }

        // Convert to lowercase and replace hyphens/spaces with underscores
        String normalized = facetCode.toLowerCase()
                .replace("-", "_")
                .replace(" ", "_");

        // Handle specific variations
        switch (normalized) {
            case "data_sets":
            case "datasets":
                return "dataset";
            case "legalentity":
            case "legal":
                return "legal_entity";
            case "businessarea":
                return "business_area";
            case "regulatorytheme":
                return "regulatory_theme";
            case "attributes":
                return "attribute";
            case "systems":
                return "system";
            case "glossaries":
                return "glossary";
            case "policies":
                return "policy";
            case "processes":
                return "process";
            case "projects":
                return "project";
            case "products":
                return "product";
            case "clients":
                return "client";
            case "committees":
                return "committee";
            case "interfaces":
            case "system_interface":
                return "interface";
            case "capabilities":
                return "capability";
            case "regulations":
                return "regulation";
            case "geographies":
                return "geography";
            case "regulators":
                return "regulator";
            case "orgunit":
            case "org-unit":
                return "org_unit";
            default:
                return normalized;
        }
    }
}
