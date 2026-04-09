package com.example.budg_v2.util;

import java.util.List;

/**
 * Utility class for building search filter conditions in dashboard servlets
 */
public class DashboardSearchUtil {
    
    /**
     * Get searchable columns for a given module
     * @param module The module name (e.g., "dataset", "system", "glossary")
     * @return List of column names to search in
     */
    public static List<String> getSearchableColumnsForModule(String module) {
        if (module == null) {
            return List.of();
        }
        String moduleLower = module.trim().toLowerCase();
        return switch (moduleLower) {
            case "dataset" -> List.of("dataset.PrimaryName", "dataset.RefNumber");
            case "attribute" -> List.of("a.PrimaryName", "a.RefNumber");
            case "system" -> List.of("system.Name", "system.Long_Name", "system.Description");
            case "glossary" -> List.of("glossary.Name", "glossary.Description");
            case "people" -> List.of("CONCAT(people.First_Name, ' ', people.Last_Name)", "people.Email");
            case "interface" -> List.of("interface.Name", "interface.Ref_number");
            case "role" -> List.of("orl.PrimaryName", "CONCAT(p.First_Name, ' ', p.Last_Name)");
            case "orgunit", "org-unit" -> List.of("org_unit.Name", "org_unit.Reference");
            case "process" -> List.of("process.primaryname", "process.refnumber");
            case "project" -> List.of("project.primaryname", "project.refnumber");
            case "product" -> List.of("product.primaryname", "product.longname");
            case "policy" -> List.of("policy.PrimaryName", "policy.refNumber");
            case "legal-entity", "legalentity", "legal" -> List.of("legal_entity.ShortName", "legal_entity.LongName");
            case "business-area" -> List.of("business_area.PrimaryName");
            case "capability" -> List.of("capability.PrimaryName", "capability.RefNumber");
            case "client" -> List.of("client.PrimaryName", "client.LongName");
            case "committee" -> List.of("committee.PrimaryName", "committee.RefNumber");
            case "regulation" -> List.of("regulation.primaryName", "regulation.RefNumber");
            case "regulatory-theme", "regulatorytheme" -> List.of("regulatorytheme.PrimaryName", "regulatorytheme.RefNumber");
            default -> List.of();
        };
    }
    
    /**
     * Build search filter condition for a given module and query
     * @param module The module name
     * @param tableAlias The table alias to use (e.g., "dataset", "system", "glossary")
     * @param query The search query string
     * @return SQL condition string (e.g., " AND (column1 LIKE '%query%' OR column2 LIKE '%query%')")
     */
    public static String buildSearchFilterCondition(String module, String tableAlias, String query) {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }
        
        List<String> searchableColumns = getSearchableColumnsForModule(module);
        if (searchableColumns.isEmpty()) {
            return "";
        }
        
        // Escape single quotes to prevent SQL injection
        String escapedQuery = query.replace("'", "''");
        // Use LIKE with % wildcards for partial matching
        String searchPattern = "%" + escapedQuery + "%";
        
        // Build OR conditions for all searchable columns
        StringBuilder condition = new StringBuilder(" AND (");
        for (int i = 0; i < searchableColumns.size(); i++) {
            if (i > 0) {
                condition.append(" OR ");
            }
            // Replace module/table name with tableAlias if needed
            String column = searchableColumns.get(i);
            // For columns that already have table alias, use as-is
            // For columns without alias, prepend tableAlias
            if (!column.contains(".")) {
                column = tableAlias + "." + column;
            } else {
                // Replace the table name part with tableAlias
                String columnName = column.substring(column.indexOf(".") + 1);
                column = tableAlias + "." + columnName;
            }
            condition.append(column).append(" COLLATE utf8mb4_unicode_ci LIKE '").append(searchPattern).append("'");
        }
        condition.append(")");
        
        return condition.toString();
    }
}
