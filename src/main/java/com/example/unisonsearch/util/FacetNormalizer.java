package com.example.unisonsearch.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Canonical facet key normalizer - Single source of truth for facet naming.
 * Eliminates duplicates like DATA_SETS vs DATASET, ATTRIBUTES vs ATTRIBUTE.
 */
public class FacetNormalizer {
    
    // Facet alias mapping
    private static final Map<String, String> FACET_ALIASES = new HashMap<>();
    
    static {
        // Dataset variations → DATASET
        FACET_ALIASES.put("DATA_SETS", "DATASET");
        FACET_ALIASES.put("DATA-SETS", "DATASET");
        FACET_ALIASES.put("DATASETS", "DATASET");
        FACET_ALIASES.put("DATA_SET", "DATASET");
        FACET_ALIASES.put("DATA-SET", "DATASET");
        
        // Attribute variations → ATTRIBUTE
        FACET_ALIASES.put("ATTRIBUTES", "ATTRIBUTE");
        FACET_ALIASES.put("ATTR", "ATTRIBUTE");
        
        // Keep these as-is but normalize format
        FACET_ALIASES.put("ORG-UNIT", "ORG_UNIT");
        FACET_ALIASES.put("ORGUNIT", "ORG_UNIT");
        
        FACET_ALIASES.put("ACTIVE-TASKS", "ACTIVE_TASKS");
        FACET_ALIASES.put("ACTIVETASKS", "ACTIVE_TASKS");
        FACET_ALIASES.put("ACTIVE-TASK", "ACTIVE_TASKS");
        
        FACET_ALIASES.put("BUSINESS-AREA", "BUSINESS_AREA");
        FACET_ALIASES.put("BUSINESSAREA", "BUSINESS_AREA");
        
        FACET_ALIASES.put("LEGAL-ENTITY", "LEGAL_ENTITY");
        FACET_ALIASES.put("LEGALENTITY", "LEGAL_ENTITY");
        
        FACET_ALIASES.put("REGULATORY-THEME", "REGULATORY_THEME");
        FACET_ALIASES.put("REGULATORYTHEME", "REGULATORY_THEME");
        
        FACET_ALIASES.put("CHANGE-REQUESTS", "CHANGE_REQUESTS");
        FACET_ALIASES.put("CHANGEREQUESTS", "CHANGE_REQUESTS");
        FACET_ALIASES.put("CHANGE-REQUEST", "CHANGE_REQUESTS");
        FACET_ALIASES.put("CHANGEREQUEST", "CHANGE_REQUESTS");
    }
    
    /**
     * Normalize facet key to canonical form.
     * @param facet Raw facet key from any source
     * @return Canonical facet key (uppercase with underscores)
     */
    public static String canonicalFacetKey(String facet) {
        if (facet == null || facet.trim().isEmpty()) {
            return "";
        }
        
        // Step 1: Trim, uppercase, replace hyphens with underscores
        String normalized = facet.trim().toUpperCase().replace("-", "_");
        
        // Step 2: Map via aliases
        String canonical = FACET_ALIASES.getOrDefault(normalized, normalized);
        
        return canonical;
    }
    
    /**
     * Convert facet to module name for database queries.
     * @param facet Canonical facet key
     * @return Module name (lowercase)
     */
    public static String facetToModule(String facet) {
        if (facet == null || facet.trim().isEmpty()) {
            return null;
        }
        
        String canonical = canonicalFacetKey(facet);
        
        // Map to module names
        switch (canonical) {
            case "DATASET":
                return "dataset";
            case "ATTRIBUTE":
                return "attribute";
            case "SYSTEM":
                return "system";
            case "GLOSSARY":
                return "glossary";
            case "PEOPLE":
                return "people";
            case "ROLE":
                return "role";
            case "INTERFACE":
                return "interface";
            case "ORG_UNIT":
                return "orgunit";
            case "BUSINESS_AREA":
                return "business-area";
            case "LEGAL_ENTITY":
                return "legal-entity";
            case "CLIENT":
                return "client";
            case "COMMITTEE":
                return "committee";
            case "POLICY":
                return "policy";
            case "PROCESS":
                return "process";
            case "PROJECT":
                return "project";
            case "PRODUCT":
                return "product";
            case "CAPABILITY":
                return "capability";
            case "GEOGRAPHY":
                return "geography";
            case "REGULATION":
                return "regulation";
            case "REGULATOR":
                return "regulator";
            case "REGULATORY_THEME":
                return "regulatory-theme";
            case "ACTIVE_TASKS":
                return "activeTasks";
            case "CHANGE_REQUESTS":
                return "change-requests";
            case "DATAQUALITY":
            case "DATA_QUALITY":
                return "data-quality";
            default:
                return canonical.toLowerCase().replace("_", "-");
        }
    }
    
    /**
     * Convert module name back to canonical facet key.
     * @param module Module name
     * @return Canonical facet key
     */
    public static String moduleToFacet(String module) {
        if (module == null || module.trim().isEmpty()) {
            return null;
        }
        
        String normalized = module.trim().toLowerCase().replace("-", "_");
        
        switch (normalized) {
            case "dataset":
                return "DATASET";
            case "attribute":
                return "ATTRIBUTE";
            case "system":
                return "SYSTEM";
            case "glossary":
                return "GLOSSARY";
            case "people":
                return "PEOPLE";
            case "role":
                return "ROLE";
            case "interface":
                return "INTERFACE";
            case "orgunit":
            case "org_unit":
                return "ORG_UNIT";
            case "business_area":
            case "businessarea":
                return "BUSINESS_AREA";
            case "legal_entity":
            case "legalentity":
                return "LEGAL_ENTITY";
            case "client":
                return "CLIENT";
            case "committee":
                return "COMMITTEE";
            case "policy":
                return "POLICY";
            case "process":
                return "PROCESS";
            case "project":
                return "PROJECT";
            case "product":
                return "PRODUCT";
            case "capability":
                return "CAPABILITY";
            case "geography":
                return "GEOGRAPHY";
            case "regulation":
                return "REGULATION";
            case "regulator":
                return "REGULATOR";
            case "regulatory_theme":
            case "regulatorytheme":
                return "REGULATORY_THEME";
            case "activetasks":
            case "active_tasks":
                return "ACTIVE_TASKS";
            case "change_requests":
            case "changerequests":
                return "CHANGE_REQUESTS";
            case "data_quality":
            case "dataquality":
                return "DATAQUALITY";
            default:
                return normalized.toUpperCase();
        }
    }
}

