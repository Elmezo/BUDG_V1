package com.example.unisonsearch.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Manages module relationship map and dynamic initialization for all
 * relationships.
 * Updated to match actual database schema from projectbackup.sql.
 * 
 * NEW VERSION: Uses ALLOWED_TARGETS, SPECIAL_RELATIONSHIPS, and DEPTH_LIMITS
 * instead of RELATIONSHIP_MAP with hardcoded SQL conditions.
 */
public class RelationshipManager {

    // Dynamic relationships logic removed - using ALLOWED_TARGETS and SPECIAL_RELATIONSHIPS instead

    /**
     * Get the module ID column name for _x_objectxpeople tables.
     * These column names may differ from the main table's ID column.
     */
    @SuppressWarnings("unused")
    private String getModuleIdColumnForXObjectXPeopleTable(String module) {
        return switch (module) {
            case "dataset" -> "Dataset_ID";
            case "attribute" -> "AttributeID";
            case "system" -> "SystemID";
            case "glossary" -> "GlossaryID"; // Note: GlossaryID, not Glossary_ID
            case "interface" -> "InterfaceID";
            case "process" -> "process_id";
            case "project" -> "project_id";
            case "product" -> "product_id";
            case "policy" -> "Policy_ID";
            case "business_area" -> "BusinessAreaID";
            case "capability" -> "CapabilityID";
            case "client" -> "ClientID";
            case "committee" -> "Committee_ID";
            case "legal_entity" -> "Legal_ID";
            case "regulation" -> "RegulationID";
            default -> {
                // Try to get from main table method, but may need adjustment
                String mainColumn = getModuleIdColumnName(module);
                // Convert common patterns: Glossary_ID -> GlossaryID, etc.
                if (mainColumn.endsWith("_ID")) {
                    yield mainColumn.replace("_ID", "ID");
                }
                yield mainColumn;
            }
        };
    }

    /**
     * Get the Object_x_ipid column name for _x_objectxpeople tables.
     * Column names vary: Object_x_ipid, Object_X_ipid, Object_X_IP, object_x_ip
     */
    @SuppressWarnings("unused")
    private String getObjectXIPidColumnName(String module) {
        return switch (module) {
            case "committee" -> "Object_x_ipid";
            case "legal_entity" -> "Object_x_ipid";
            case "policy" -> "Object_x_ipid";
            case "process" -> "Object_x_ip";
            case "project" -> "Object_x_ip";
            case "product" -> "Object_x_ip";
            default -> "Object_x_ipid";
        };
    }

    @SuppressWarnings("unused")
    private boolean tableExists(Connection conn, String tableName) {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, new String[] { "TABLE" })) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    private String getModuleIdColumnName(String module) {
        return switch (module) {
            case "dataset" -> "Dataset_ID";
            case "attribute" -> "AttributeID";
            case "system" -> "SystemID";
            case "glossary" -> "Glossary_ID";
            case "interface" -> "id"; // interface.id (lowercase)
            case "process" -> "id";
            case "project" -> "id";
            case "product" -> "id";
            case "policy" -> "Policy_ID";
            case "business_area" -> "BusinessArea_ID";
            case "capability" -> "Capability_ID";
            case "client" -> "Client_ID";
            case "committee" -> "Committee_ID";
            case "legal_entity" -> "Legal_ID";
            case "regulation" -> "Regulation_ID";
            case "geography" -> "Geography_ID";
            case "regulator" -> "Regulator_ID";
            case "regulatory_theme" -> "RegulatoryTheme_ID";
            case "people" -> "ID";
            case "org_unit" -> "OrgUnit_ID";
            case "requirement" -> "id";
            case "data_store" -> "Dataset_ID";
            case "data_quality" -> "id";
            case "association_origin" -> "ID";
            default -> {
                String normalized = module.replace("-", "_");
                yield normalized + "_ID";
            }
        };
    }

    @SuppressWarnings("unused")
    private String getXObjectTableName(String module) {
        String tableName = module.replace("-", "_");
        return tableName + "_x_object";
    }

    @SuppressWarnings("unused")
    private String getModuleIdColumnForXObjectTable(String module) {
        return switch (module) {
            case "dataset" -> "Dataset_ID";
            case "attribute" -> "Attribute_ID";
            case "system" -> "System_ID";
            case "glossary" -> "Glossary_ID";
            case "interface" -> "Interface_ID";
            case "process" -> "Process_ID";
            case "project" -> "Project_ID";
            case "product" -> "Product_ID";
            case "policy" -> "Policy_ID";
            case "business_area" -> "BusinessArea_ID";
            case "capability" -> "Capability_ID";
            case "client" -> "Client_ID";
            case "committee" -> "Committee_ID";
            case "legal_entity" -> "Legal_ID";
            case "regulation" -> "Regulation_ID";
            case "geography" -> "Geography_ID";
            case "regulator" -> "Regulator_ID";
            case "regulatory_theme" -> "RegulatoryTheme_ID";
            case "people" -> "People_ID";
            case "org_unit" -> "OrgUnit_ID";
            default -> {
                String normalized = module.replace("-", "_");
                yield normalized + "_ID";
            }
        };
    }

    @SuppressWarnings("unused")
    private String normalizeObjectTypeToModule(String objectType) {
        if (objectType == null)
            return null;
        String normalized = objectType.trim().toLowerCase()
                .replace(" ", "_")
                .replace("-", "_");
        return normalizeFacetNameForLookup(normalized);
    }

    /**
     * Get ID column for a module (for use in SQL queries).
     */
    public String getIdColumnForModule(String module) {
        String normalized = normalizeFacetNameForLookup(module);
        return getModuleIdColumnName(normalized);
    }

    /**
     * Normalize facet name for lookup in maps.
     * Uses FacetNormalizationUtil for consistency across the codebase.
     */
    private static String normalizeFacetNameForLookup(String facet) {
        if (facet == null) return null;
        // Use FacetNormalizationUtil to normalize to lowercase canonical form
        return com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToLowercase(facet);
    }

    // ============================================================================
    // ALLOWED_TARGETS - العلاقات المسموحة لكل facet
    // ============================================================================
    public static final Map<String, Set<String>> ALLOWED_TARGETS = new HashMap<>() {
        {
            put("dataset", Set.of("system", "attribute", "glossary", "interface", "project", "process", "policy",
                    "legal_entity", "client"));
            put("system", Set.of("dataset", "attribute", "glossary", "project", "process", "policy"));
            put("glossary", Set.of("dataset", "attribute", "project", "process", "policy",
                    "capability", "business_area", "legal_entity", "client", "product"));
            put("attribute", Set.of("dataset", "system", "glossary", "project", "process", "policy"));
            put("interface", Set.of("system", "dataset"));
            put("person", Set.of("orgunit", "object", "role", "changerequest", "task"));
            put("people", Set.of("orgunit", "object", "role", "changerequest", "task"));
            put("changerequest", Set.of("targetobject", "task"));
            put("project", Set.of("glossary", "dataset", "attribute", "system"));
            put("process", Set.of("glossary", "dataset", "attribute", "system"));
            put("policy", Set.of("glossary", "dataset", "attribute", "system"));
            put("capability", Set.of("glossary", "dataset", "attribute", "system"));
            put("product", Set.of("glossary", "dataset", "system"));
            put("business_area", Set.of("glossary", "process", "system"));
            put("businessarea", Set.of("glossary", "process", "system"));
            put("client", Set.of("glossary", "dataset", "system", "policy", "process", "project"));
            put("committee", Set.of("capability"));
            put("legal_entity", Set.of("geography", "dataset"));
            put("legalentity", Set.of("geography", "dataset"));
            put("legal", Set.of("geography", "dataset"));
            put("orgunit", Set.of("person", "object"));
            put("org_unit", Set.of("person", "object"));
            // Regulatory facets
            put("regulation", Set.of("regulator", "regulatory_theme"));
            put("regulator", Set.of("geography", "regulation"));
            put("regulatory_theme", Set.of("regulation"));
            put("regulatorytheme", Set.of("regulation"));
            put("geography", Set.of("regulator"));
        }
    };

    // ============================================================================
    // SPECIAL_RELATIONSHIPS - العلاقات المشتقة (derived) التي تحتاج depth > 1
    // ============================================================================
    public static final Map<String, Set<String>> SPECIAL_RELATIONSHIPS = new HashMap<>() {
        {
            // Glossary → System (via Attribute → Dataset): Glossary → Attribute → Dataset → System
            put("glossary", Set.of("system"));
            // Attribute → System (parent chain): Attribute → Dataset → System
            put("attribute", Set.of("system"));
            // Project/Process/Policy/Capability → Dataset, Attribute, System (special relationships)
            put("project", Set.of("dataset", "attribute", "system"));
            put("process", Set.of("dataset", "attribute", "system"));
            put("policy", Set.of("dataset", "attribute", "system"));
            put("capability", Set.of("dataset", "attribute", "system"));
            // Product → Attribute (via Dataset): Product → Dataset → Attribute
            put("product", Set.of("attribute"));
            // Interface → Dataset (via System): Interface → System → Dataset
            put("interface", Set.of("dataset"));
            // Business Area → Dataset, Attribute (via System/Process)
            put("business_area", Set.of("dataset", "attribute"));
            put("businessarea", Set.of("dataset", "attribute"));
            // Client → Attribute (via Dataset)
            put("client", Set.of("attribute"));
            // Committee → Glossary, Dataset, Attribute, System (via Capability)
            put("committee", Set.of("glossary", "dataset", "attribute", "system"));
            // Legal Entity → System, Attribute (via Dataset)
            put("legal_entity", Set.of("system", "attribute"));
            put("legalentity", Set.of("system", "attribute"));
            put("legal", Set.of("system", "attribute"));
            // Regulation → Geography (via Regulator)
            put("regulation", Set.of("geography"));
            // Regulator → RegulatoryTheme (via Regulation)
            put("regulator", Set.of("regulatory_theme"));
            // RegulatoryTheme → Regulator, Geography (via Regulation)
            put("regulatory_theme", Set.of("regulator", "geography"));
            put("regulatorytheme", Set.of("regulator", "geography"));
            // Geography → Regulation, RegulatoryTheme (via Regulator)
            put("geography", Set.of("regulation", "regulatory_theme"));
        }
    };

    // ============================================================================
    // DEPTH_LIMITS - حدود العمق لكل نوع علاقة
    // ============================================================================
    public static final Map<String, Integer> DEPTH_LIMITS = new HashMap<>() {
        {
            put("DEFAULT", 1);
            put("GLOSSARY_CHAIN", 3);        // Glossary → Attribute → Dataset → System
            put("PROJECT_DEPTH", 1);         // Project → Glossary فقط
        }
    };

    // ============================================================================
    // Helper Methods
    // ============================================================================
    
    /**
     * Get allowed direct targets for a facet.
     */
    public static Set<String> getAllowedTargets(String facet) {
        String normalized = normalizeFacetNameForLookup(facet);
        return ALLOWED_TARGETS.getOrDefault(normalized, Set.of());
    }

    /**
     * Get special relationships for a facet.
     */
    public static Set<String> getSpecialRelationships(String facet) {
        String normalized = normalizeFacetNameForLookup(facet);
        return SPECIAL_RELATIONSHIPS.getOrDefault(normalized, Set.of());
    }

    /**
     * Check if a relationship is special (derived).
     */
    public static boolean isSpecialRelationship(String sourceFacet, String targetFacet) {
        String normalizedSource = normalizeFacetNameForLookup(sourceFacet);
        Set<String> special = SPECIAL_RELATIONSHIPS.get(normalizedSource);
        if (special == null) return false;
        String normalizedTarget = normalizeFacetNameForLookup(targetFacet);
        return special.contains(normalizedTarget);
    }

    /**
     * Get depth limit for a facet or relationship type.
     */
    public static Integer getDepthLimit(String facet) {
        String normalized = normalizeFacetNameForLookup(facet);
        return DEPTH_LIMITS.get(normalized);
    }

    // ============================================================================
    // OLD RELATIONSHIP_MAP - REMOVED - Use ALLOWED_TARGETS and RelationshipService instead
    // ============================================================================
    // RELATIONSHIP_MAP completely removed - all SQL conditions were incorrect
    // Use RelationshipService with proper queries instead
}
