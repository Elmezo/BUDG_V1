package com.example.unisonsearch.model;

import java.util.List;
import java.util.Map;

/**
 * Request model for Unison Search API.
 * Represents a compound search with multiple search conditions.
 */
public class UnisonSearchRequest {
    private List<SearchItem> searches;
    private SearchOptions options;
    /**
     * When set, every non-People facet in the response is intersected with objects
     * linked to these people (stakeholder, created by, or updated by).
     */
    private List<Integer> peopleConstraintIds;

    public UnisonSearchRequest() {
    }

    public UnisonSearchRequest(List<SearchItem> searches, SearchOptions options) {
        this.searches = searches;
        this.options = options;
    }

    public List<SearchItem> getSearches() {
        return searches;
    }

    public void setSearches(List<SearchItem> searches) {
        this.searches = searches;
    }

    public SearchOptions getOptions() {
        return options;
    }

    public void setOptions(SearchOptions options) {
        this.options = options;
    }

    public List<Integer> getPeopleConstraintIds() {
        return peopleConstraintIds;
    }

    public void setPeopleConstraintIds(List<Integer> peopleConstraintIds) {
        this.peopleConstraintIds = peopleConstraintIds;
    }

    /**
     * Represents a single search condition.
     */
    public static class SearchItem {
        private String operator; // FIND, AND, OR, NOT
        private String facet; // Facet ID (e.g., "GLOSSARY", "DATASET")
        private String keyword;
        private Map<String, Object> filters;
        private Map<String, String> hierarchicalOptions; // For parent-child filtering
        /**
         * Optional field selection map from the "Search in" UI.
         * Keys are field keys (e.g. "name", "ref", "description"), values are booleans.
         * When present, only columns mapped to true keys are searched by the keyword.
         * When null/empty, all available columns for the facet are searched (default behaviour).
         */
        private Map<String, Boolean> searchFields;
        /**
         * Visual nesting in the query builder. 0 = top-level clause; each increment indents under the
         * nearest previous line with a lower level. Indented siblings form one grouped sub-expression whose
         * combined result filters the parent line (e.g. FIND Data Sets with indented AND/OR on System).
         */
        private Integer indentLevel;

        /**
         * When true this condition is treated as a "display-only filter": its filters are applied to
         * the row-data retrieval stage (so only matching rows are shown in the UI) but it is completely
         * excluded from the AND/OR/NOT accumulation phase.  This allows, e.g., filtering the People
         * panel by Profile Name without reducing the root System count.
         * The condition is still persisted in saved searches so the filter is restored on reload.
         */
        private boolean displayFilter;

        public SearchItem() {
        }

        public SearchItem(String operator, String facet, String keyword, Map<String, Object> filters) {
            this.operator = operator;
            this.facet = facet;
            this.keyword = keyword;
            this.filters = filters;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getFacet() {
            return facet;
        }

        public void setFacet(String facet) {
            this.facet = facet;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public Map<String, Object> getFilters() {
            return filters;
        }

        public void setFilters(Map<String, Object> filters) {
            this.filters = filters;
        }

        public Map<String, String> getHierarchicalOptions() {
            return hierarchicalOptions;
        }

        public void setHierarchicalOptions(Map<String, String> hierarchicalOptions) {
            this.hierarchicalOptions = hierarchicalOptions;
        }

        public Map<String, Boolean> getSearchFields() {
            return searchFields;
        }

        public void setSearchFields(Map<String, Boolean> searchFields) {
            this.searchFields = searchFields;
        }

        public Integer getIndentLevel() {
            return indentLevel;
        }

        public void setIndentLevel(Integer indentLevel) {
            this.indentLevel = indentLevel;
        }

        public boolean isDisplayFilter() {
            return displayFilter;
        }

        public void setDisplayFilter(boolean displayFilter) {
            this.displayFilter = displayFilter;
        }
    }

    /**
     * Search options for controlling traversal behavior.
     */
    public static class SearchOptions {
        /** Default 1 = only direct relations (impact of search seed only, no second hop). */
        private int maxDepth = 1;
        private boolean includeCounts = true;

        public SearchOptions() {
        }

        public SearchOptions(int maxDepth, boolean includeCounts) {
            this.maxDepth = maxDepth;
            this.includeCounts = includeCounts;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public boolean isIncludeCounts() {
            return includeCounts;
        }

        public void setIncludeCounts(boolean includeCounts) {
            this.includeCounts = includeCounts;
        }
    }
}

