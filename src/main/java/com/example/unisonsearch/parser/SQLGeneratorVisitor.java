package com.example.unisonsearch.parser;

import com.example.unisonsearch.util.FuzzySearchUtil;
import java.util.List;

/**
 * SQL generator visitor that converts an AST into SQL WHERE clauses.
 * Implements the visitor pattern to traverse the query tree.
 */
public class SQLGeneratorVisitor implements QueryNodeVisitor<String> {

    private final List<String> searchableColumns;
    private final List<Object> parameters;
    private final boolean useFuzzy;
    private final List<String> moduleFacetNames;
    private final String objectIdColumn;

    public SQLGeneratorVisitor(List<String> searchableColumns, List<Object> parameters, boolean useFuzzy) {
        this(searchableColumns, parameters, useFuzzy, (List<String>) null, null);
    }

    public SQLGeneratorVisitor(List<String> searchableColumns, List<Object> parameters, boolean useFuzzy,
                               String moduleFacetName, String objectIdColumn) {
        this(searchableColumns, parameters, useFuzzy,
             moduleFacetName != null ? List.of(moduleFacetName) : null, objectIdColumn);
    }

    public SQLGeneratorVisitor(List<String> searchableColumns, List<Object> parameters, boolean useFuzzy,
                               List<String> moduleFacetNames, String objectIdColumn) {
        this.searchableColumns = searchableColumns;
        this.parameters = parameters;
        this.useFuzzy = useFuzzy;
        this.moduleFacetNames = moduleFacetNames;
        this.objectIdColumn = objectIdColumn;
    }

    @Override
    public String visitTerm(TermNode node) {
        String term = node.getTerm();
        String field = node.getField();
        boolean hasWildcard = node.hasWildcard();

        // Build search condition for this term
        StringBuilder condition = new StringBuilder("(");

        if (field != null) {
            // Field-specific search: name:customer
            String columnName = findColumnByFieldName(field);
            if (columnName != null) {
                appendSingleColumnFuzzySearch(condition, columnName, term, useFuzzy || hasWildcard);
            } else {
                // Field not found, search all columns
                appendMultiColumnSearch(condition, term, useFuzzy || hasWildcard);
            }
        } else {
            // Search across all searchable columns
            appendMultiColumnSearch(condition, term, useFuzzy || hasWildcard);
        }

        condition.append(")");
        return condition.toString();
    }

    @Override
    public String visitOperator(OperatorNode node) {
        List<QueryNode> children = node.getChildren();
        if (children.isEmpty()) {
            return "";
        }

        OperatorType operator = node.getOperator();

        switch (operator) {
            case AND:
                return visitAndOperator(children);
            case OR:
                return visitOrOperator(children);
            case NOT:
                return visitNotOperator(children);
            default:
                return "";
        }
    }

    private String visitAndOperator(List<QueryNode> children) {
        StringBuilder result = new StringBuilder("(");
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                result.append(" AND ");
            }
            result.append(children.get(i).accept(this));
        }
        result.append(")");
        return result.toString();
    }

    private String visitOrOperator(List<QueryNode> children) {
        StringBuilder result = new StringBuilder("(");
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                result.append(" OR ");
            }
            result.append(children.get(i).accept(this));
        }
        result.append(")");
        return result.toString();
    }

    private String visitNotOperator(List<QueryNode> children) {
        if (children.isEmpty()) {
            return "";
        }
        // NOT operator applies to first child
        return "NOT " + children.get(0).accept(this);
    }

    private void appendMultiColumnSearch(StringBuilder condition, String term, boolean fuzzy) {
        if (fuzzy) {
            // Use FuzzySearchUtil for advanced fuzzy matching
            List<String> fuzzyPatterns = FuzzySearchUtil.generateFuzzyPatterns(term);
            
            for (int i = 0; i < searchableColumns.size(); i++) {
                if (i > 0) {
                    condition.append(" OR ");
                }
                
                // For each column, create OR conditions for all fuzzy patterns
                if (fuzzyPatterns.size() > 1) {
                    condition.append("(");
                }
                
                for (int j = 0; j < fuzzyPatterns.size(); j++) {
                    if (j > 0) {
                        condition.append(" OR ");
                    }
                    condition.append(searchableColumns.get(i)).append(" LIKE ?");
                    parameters.add(fuzzyPatterns.get(j));
                }
                
                if (fuzzyPatterns.size() > 1) {
                    condition.append(")");
                }
            }
        } else {
            // Non-fuzzy: use exact match (=) for precise results
            // This ensures "age" matches exactly "age" and not "gender" or "page"
            for (int i = 0; i < searchableColumns.size(); i++) {
                if (i > 0) {
                    condition.append(" OR ");
                }
                condition.append(searchableColumns.get(i)).append(" = ?");
                parameters.add(term);
            }
        }

        // Also search in custom field values for this module
        appendCustomFieldSearch(condition, term, fuzzy);
    }

    /**
     * Append an EXISTS subquery that searches through searchable custom field values.
     * Handles text values (Custom_Field_Value) and enum values (dropdown/multiselect).
     */
    private void appendCustomFieldSearch(StringBuilder condition, String term, boolean fuzzy) {
        if (moduleFacetNames == null || moduleFacetNames.isEmpty() || objectIdColumn == null) {
            return;
        }

        condition.append(" OR EXISTS (SELECT 1 FROM Custom_Field_Data cfd_s ");
        condition.append("INNER JOIN Custom_Field_Metadata cfm_s ON cfd_s.Custom_Field_Metadata_ID = cfm_s.ID ");
        condition.append("INNER JOIN module m_s ON cfm_s.Module_ID = m_s.ID ");
        condition.append("LEFT JOIN Custom_Field_Enum cfe_s ON cfe_s.ID = cfd_s.Custom_Field_Enum_ID ");
        condition.append("WHERE m_s.primaryname IN (");
        for (int i = 0; i < moduleFacetNames.size(); i++) {
            if (i > 0) condition.append(", ");
            condition.append("?");
            parameters.add(moduleFacetNames.get(i));
        }
        condition.append(") ");
        condition.append("AND cfm_s.is_Searchable = 1 ");
        condition.append("AND cfd_s.Facet_Object_ID = ").append(objectIdColumn).append(" ");
        condition.append("AND (");

        if (fuzzy) {
            List<String> fuzzyPatterns = FuzzySearchUtil.generateFuzzyPatterns(term);
            boolean first = true;
            for (String pattern : fuzzyPatterns) {
                if (!first) condition.append(" OR ");
                condition.append("LOWER(cfd_s.Custom_Field_Value) LIKE LOWER(?)");
                parameters.add(pattern);
                first = false;
            }
            for (String pattern : fuzzyPatterns) {
                condition.append(" OR LOWER(cfe_s.EnumValue) LIKE LOWER(?)");
                parameters.add(pattern);
            }
        } else {
            condition.append("LOWER(cfd_s.Custom_Field_Value) LIKE CONCAT('%', LOWER(?), '%')");
            parameters.add(term);
            condition.append(" OR LOWER(cfe_s.EnumValue) LIKE CONCAT('%', LOWER(?), '%')");
            parameters.add(term);
        }

        condition.append("))");
    }

    private void appendSingleColumnFuzzySearch(StringBuilder condition, String columnName, String term, boolean fuzzy) {
        if (fuzzy) {
            // Use FuzzySearchUtil for advanced fuzzy matching
            List<String> fuzzyPatterns = FuzzySearchUtil.generateFuzzyPatterns(term);
            
            if (fuzzyPatterns.size() > 1) {
                condition.append("(");
            }
            
            for (int i = 0; i < fuzzyPatterns.size(); i++) {
                if (i > 0) {
                    condition.append(" OR ");
                }
                condition.append(columnName).append(" LIKE ?");
                parameters.add(fuzzyPatterns.get(i));
            }
            
            if (fuzzyPatterns.size() > 1) {
                condition.append(")");
            }
        } else {
            // Non-fuzzy: use exact match (=) for precise results
            // This ensures "age" matches exactly "age" and not "gender" or "page"
            condition.append(columnName).append(" = ?");
            parameters.add(term);
        }
    }

    private String findColumnByFieldName(String fieldName) {
        // Map common field names to actual column names
        String lowerField = fieldName.toLowerCase();
        for (String column : searchableColumns) {
            String lowerColumn = column.toLowerCase();
            if (lowerColumn.contains(lowerField) ||
                    lowerColumn.contains("." + lowerField) ||
                    lowerColumn.endsWith("name") && lowerField.equals("name") ||
                    lowerColumn.contains("description") && lowerField.equals("description") ||
                    lowerColumn.contains("ref") && lowerField.equals("ref")) {
                return column;
            }
        }
        return null; // Field not found, will search all columns
    }
}
