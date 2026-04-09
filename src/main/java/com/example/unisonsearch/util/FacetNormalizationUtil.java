package com.example.unisonsearch.util;

/**
 * Utility class for normalizing facet IDs to ensure consistency across
 * frontend and backend.
 * This prevents duplicate facet results and ensures proper facet matching.
 */
public class FacetNormalizationUtil {

    /**
     * Normalize a facet ID to canonical uppercase form with underscores.
     * This is the single source of truth for facet normalization.
     * 
     * @param facetId The facet ID to normalize (e.g., "data-sets", "DATASET",
     *                "datasets")
     * @return Canonical facet ID (e.g., "DATASET")
     */
    public static String normalizeToCanonical(String facetId) {
        if (facetId == null || facetId.trim().isEmpty()) {
            return null;
        }

        // Convert to uppercase and replace hyphens with underscores
        String normalized = facetId.trim().toUpperCase().replace("-", "_").replace(" ", "_");

        // Map common variations to canonical forms
        // This is the SINGLE SOURCE OF TRUTH for facet normalization
        // All backend services and frontend code should use this utility
        return switch (normalized) {
            // Dataset variations
            case "DATA_SETS", "DATA_SET", "DATASETS", "DATA-SETS", "DATA-SET" -> "DATASET";
            // Attribute variations
            case "ATTRIBUTES", "ATTR", "FIELD", "FIELDS", "COLUMN", "COLUMNS" -> "ATTRIBUTE";
            // System variations
            case "SYSTEMS", "SYS", "APPLICATION", "APPLICATIONS", "APP", "APPS" -> "SYSTEM";
            // Glossary variations
            case "GLOSSARIES", "DICTIONARY", "DICTIONARIES", "TERM", "TERMS" -> "GLOSSARY";
            // People variations
            case "PEOPLE", "PERSON", "PERSONS", "USER", "USERS", "EMPLOYEE", "EMPLOYEES", "STAFF" -> "PEOPLE";
            // Interface variations
            case "INTERFACES", "INTEGRATION", "INTEGRATIONS", "CONNECTION", "CONNECTIONS" -> "INTERFACE";
            // Org Unit variations
            case "ORG_UNITS", "ORG_UNIT", "ORGUNIT", "ORG-UNIT", "ORG-UNITS", "ORGANIZATION", "ORGANIZATIONS", "DEPARTMENT", "DEPARTMENTS" -> "ORG_UNIT";
            // Legal Entity variations
            case "LEGAL_ENTITIES", "LEGAL_ENTITY", "LEGALENTITY", "LEGAL-ENTITY", "LEGAL", "COMPANY", "COMPANIES" -> "LEGAL_ENTITY";
            // Business Area variations
            case "BUSINESS_AREAS", "BUSINESS_AREA", "BUSINESSAREAS", "BUSINESSAREA", "BUSINESS-AREA", "BUSINESS-AREAS", "BUSINESS" -> "BUSINESS_AREA";
            // Regulation variations
            case "REGULATIONS" -> "REGULATION";
            // Geography variations
            case "GEOGRAPHIES", "GEOGRAPHY", "GEO" -> "GEOGRAPHY";
            // Regulator variations
            case "REGULATORS" -> "REGULATOR";
            // Regulatory Theme variations
            case "REGULATORY_THEMES", "REGULATORY_THEME", "REGULATORYTHEMES", "REGULATORYTHEME", "REGULATORY-THEME", "REGULATORY-THEMES", "THEME", "THEMES" -> "REGULATORY_THEME";
            // Process variations
            case "PROCESSES" -> "PROCESS";
            // Project variations
            case "PROJECTS" -> "PROJECT";
            // Product variations
            case "PRODUCTS" -> "PRODUCT";
            // Policy variations
            case "POLICIES" -> "POLICY";
            // Capability variations
            case "CAPABILITIES" -> "CAPABILITY";
            // Client variations
            case "CLIENTS" -> "CLIENT";
            // Committee variations
            case "COMMITTEES" -> "COMMITTEE";
            // Requirement variations
            case "REQUIREMENTS" -> "REQUIREMENT";
            // Data Store variations
            case "DATA_STORES", "DATA_STORE", "DATASTORES", "DATASTORE" -> "DATA_STORE";
            // Data Quality variations
            case "DATA_QUALITY", "DATAQUALITY" -> "DATAQUALITY";
            // Association Origin variations
            case "ASSOCIATION_ORIGINS", "ASSOCIATION_ORIGIN", "ASSOCIATIONORIGINS", "ASSOCIATIONORIGIN" -> "ASSOCIATION_ORIGIN";
            // Role variations
            case "ROLES" -> "ROLE";
            // Active Tasks variations
            case "ACTIVE_TASKS", "ACTIVE_TASK", "ACTIVETASKS", "ACTIVETASK", "ACTIVE-TASKS", "ACTIVE-TASK", "TASKS", "TASK" -> "ACTIVE_TASKS";
            // Change Request variations
            case "CHANGE_REQUESTS", "CHANGE_REQUEST", "CHANGEREQUESTS", "CHANGEREQUEST", "CHANGE-REQUESTS", "CHANGE-REQUEST" -> "CHANGE_REQUESTS";
            default -> normalized;
        };
    }

    /**
     * Normalize to lowercase with underscores (for internal use).
     * Used for map keys and internal comparisons.
     * 
     * @param facetId The facet ID to normalize
     * @return Lowercase normalized form (e.g., "dataset")
     */
    public static String normalizeToLowercase(String facetId) {
        if (facetId == null || facetId.trim().isEmpty()) {
            return null;
        }

        String canonical = normalizeToCanonical(facetId);
        return canonical != null ? canonical.toLowerCase() : null;
    }

    /**
     * Check if two facet IDs represent the same facet.
     * 
     * @param facetId1 First facet ID
     * @param facetId2 Second facet ID
     * @return true if they represent the same facet
     */
    public static boolean areSameFacet(String facetId1, String facetId2) {
        String norm1 = normalizeToCanonical(facetId1);
        String norm2 = normalizeToCanonical(facetId2);

        if (norm1 == null || norm2 == null) {
            return false;
        }

        return norm1.equals(norm2);
    }
}
