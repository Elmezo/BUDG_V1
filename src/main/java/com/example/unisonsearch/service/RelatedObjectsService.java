package com.example.unisonsearch.service;

import com.example.unisonsearch.model.SearchParams;
import com.example.unisonsearch.repository.QueryBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling "Search Related Objects in Unison" functionality.
 * Converts selected grid rows to search filters and supports hierarchical searching.
 */
public class RelatedObjectsService {

    private final QueryBuilder queryBuilder;
    private final RelationshipManager relationshipManager;

    public RelatedObjectsService(QueryBuilder queryBuilder, RelationshipManager relationshipManager) {
        this.queryBuilder = queryBuilder;
        this.relationshipManager = relationshipManager;
    }

    /**
     * Get the QueryBuilder instance (for future use).
     */
    public QueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    /**
     * Get the RelationshipManager instance (for future use).
     */
    public RelationshipManager getRelationshipManager() {
        return relationshipManager;
    }

    /**
     * Convert selected grid rows to search definition format.
     * Selected rows become non-editable filters in the search definition.
     * 
     * @param selectedRows List of selected rows, each containing id, category/facetId, and other fields
     * @param targetFacetId The target facet to search in
     * @return Search definition in searchGroups format
     */
    public JsonObject createSearchFromSelectedRows(List<Map<String, Object>> selectedRows, String targetFacetId) {
        JsonObject searchDefinition = new JsonObject();
        JsonArray searchGroups = new JsonArray();

        // Group selected rows by facetId/category
        Map<String, List<Map<String, Object>>> rowsByFacet = groupRowsByFacet(selectedRows);

        boolean firstGroup = true;
        for (Map.Entry<String, List<Map<String, Object>>> entry : rowsByFacet.entrySet()) {
            String facetId = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();

            JsonObject searchGroup = new JsonObject();
            searchGroup.addProperty("operator", firstGroup ? "START" : "AND");
            searchGroup.addProperty("active", true);

            JsonArray searches = new JsonArray();
            JsonObject search = new JsonObject();
            search.addProperty("operator", "START");
            search.addProperty("active", true);
            search.addProperty("facetId", facetId);
            search.addProperty("readOnly", true); // Mark as non-editable

            // Create filterGroups from selected rows
            JsonArray filterGroups = new JsonArray();
            for (Map<String, Object> row : rows) {
                JsonObject filterGroup = new JsonObject();
                Object id = row.get("id");
                if (id != null) {
                    filterGroup.addProperty("field", "id");
                    filterGroup.addProperty("condition", "equals");
                    filterGroup.addProperty("value", id.toString());
                    filterGroup.addProperty("readOnly", true); // Non-editable
                    filterGroups.add(filterGroup);
                }
            }

            search.add("filterGroups", filterGroups);
            searches.add(search);
            searchGroup.add("searches", searches);
            searchGroups.add(searchGroup);

            firstGroup = false;
        }

        searchDefinition.add("searchGroups", searchGroups);
        return searchDefinition;
    }

    /**
     * Add additional filters to an existing search definition.
     * 
     * @param existingDefinition The existing search definition
     * @param newFilters The new filters to add
     * @param operator The operator to use (AND, OR, NOT)
     * @return Updated search definition
     */
    public JsonObject addFiltersToSearch(JsonObject existingDefinition, List<Map<String, Object>> newFilters, String operator) {
        if (!existingDefinition.has("searchGroups")) {
            existingDefinition.add("searchGroups", new JsonArray());
        }

        JsonArray searchGroups = existingDefinition.getAsJsonArray("searchGroups");
        
        // Create a new search group for the additional filters
        JsonObject newGroup = new JsonObject();
        newGroup.addProperty("operator", searchGroups.size() == 0 ? "START" : operator);
        newGroup.addProperty("active", true);

        JsonArray searches = new JsonArray();
        for (Map<String, Object> filter : newFilters) {
            JsonObject search = new JsonObject();
            search.addProperty("operator", "START");
            search.addProperty("active", true);
            search.addProperty("facetId", (String) filter.get("facetId"));
            
            JsonArray filterGroups = new JsonArray();
            JsonObject filterGroup = new JsonObject();
            filterGroup.addProperty("field", (String) filter.get("field"));
            filterGroup.addProperty("condition", (String) filter.get("condition"));
            filterGroup.addProperty("value", (String) filter.get("value"));
            filterGroups.add(filterGroup);
            
            search.add("filterGroups", filterGroups);
            searches.add(search);
        }

        newGroup.add("searches", searches);
        searchGroups.add(newGroup);

        return existingDefinition;
    }

    /**
     * Group selected rows by their facet/category.
     * 
     * @param selectedRows List of selected rows
     * @return Map of facetId to list of rows
     */
    private Map<String, List<Map<String, Object>>> groupRowsByFacet(List<Map<String, Object>> selectedRows) {
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        
        for (Map<String, Object> row : selectedRows) {
            String facetId = null;
            
            // Try to get facetId from various possible field names
            if (row.containsKey("facetId")) {
                facetId = (String) row.get("facetId");
            } else if (row.containsKey("category")) {
                facetId = moduleNameToFacetId((String) row.get("category"));
            } else if (row.containsKey("module")) {
                facetId = moduleNameToFacetId((String) row.get("module"));
            }
            
            if (facetId == null) {
                facetId = "UNKNOWN";
            }
            
            grouped.computeIfAbsent(facetId, k -> new ArrayList<>()).add(row);
        }
        
        return grouped;
    }

    /**
     * Convert module name to facet ID.
     * 
     * @param moduleName The module name (e.g., "dataset", "process")
     * @return The facet ID (e.g., "DATASET", "PROCESS")
     */
    private String moduleNameToFacetId(String moduleName) {
        if (moduleName == null) return null;
        
        String module = moduleName.trim().toLowerCase();
        return switch (module) {
            case "dataset" -> "DATASET";
            case "attribute" -> "ATTRIBUTE";
            case "system" -> "SYSTEM";
            case "glossary" -> "GLOSSARY";
            case "dataquality" -> "DATAQUALITY";
            case "people" -> "PEOPLE";
            case "role" -> "ROLE";
            case "business-area" -> "BUSINESS_AREA";
            case "legal-entity", "legalentity", "legal" -> "LEGAL_ENTITY";
            case "client" -> "CLIENT";
            case "committee" -> "COMMITTEE";
            case "policy" -> "POLICY";
            case "process" -> "PROCESS";
            case "interface" -> "INTERFACE";
            case "capability" -> "CAPABILITY";
            case "product" -> "PRODUCT";
            case "orgunit", "org-unit" -> "ORG_UNIT";
            case "geography" -> "GEOGRAPHY";
            case "regulation" -> "REGULATION";
            case "regulator" -> "REGULATOR";
            case "regulatory-theme", "regulatorytheme" -> "REGULATORY_THEME";
            default -> moduleName.toUpperCase();
        };
    }

    /**
     * Build search parameters for hierarchical facet searching.
     * Supports searching within hierarchical structures (e.g., parent-child relationships).
     * 
     * @param facetId The facet ID
     * @param parentId The parent ID (optional)
     * @param includeChildren Whether to include child objects
     * @return SearchParams for the hierarchical search
     */
    public SearchParams buildHierarchicalSearchParams(String facetId, Integer parentId, boolean includeChildren) {
        // Convert facetId to module name
        String module = facetIdToModuleName(facetId);
        if (module == null) {
            return null;
        }

        // Build search params
        // For hierarchical search, we need to query the module with parent filters
        // This is a simplified version - full implementation would handle parent-child relationships
        return new SearchParams(module, null, null, null, null, new String[]{module}, new ArrayList<>(), null);
    }

    /**
     * Convert facet ID to module name.
     */
    private String facetIdToModuleName(String facetId) {
        if (facetId == null) return null;
        
        return switch (facetId.toUpperCase()) {
            case "DATASET" -> "dataset";
            case "ATTRIBUTE" -> "attribute";
            case "SYSTEM" -> "system";
            case "GLOSSARY" -> "glossary";
            case "DATAQUALITY" -> "dataquality";
            case "PEOPLE" -> "people";
            case "ROLE" -> "role";
            case "BUSINESS_AREA" -> "business-area";
            case "LEGAL_ENTITY" -> "legal-entity";
            case "CLIENT" -> "client";
            case "COMMITTEE" -> "committee";
            case "POLICY" -> "policy";
            case "PROCESS" -> "process";
            case "INTERFACE" -> "interface";
            case "CAPABILITY" -> "capability";
            case "PRODUCT" -> "product";
            case "ORG_UNIT" -> "orgunit";
            case "GEOGRAPHY" -> "geography";
            case "REGULATION" -> "regulation";
            case "REGULATOR" -> "regulator";
            case "REGULATORY_THEME" -> "regulatory-theme";
            default -> null;
        };
    }
}

