package com.example.unisonsearch.model;

import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * Represents search results for a single facet.
 * Contains the IDs of connected objects and metadata about the facet.
 */
public class FacetResult {
    private final Set<Integer> ids;
    private final int count;
    private final boolean hasActiveFilter;
    private final int totalCount;
    private final Map<Integer, Integer> depthById;
    private final List<Map<String, Object>> rows;

    public FacetResult(Set<Integer> ids, boolean hasActiveFilter, Map<Integer, Integer> depthById) {
        this(ids, hasActiveFilter, depthById, null, -1);
    }

    public FacetResult(Set<Integer> ids, boolean hasActiveFilter, Map<Integer, Integer> depthById, List<Map<String, Object>> rows) {
        this(ids, hasActiveFilter, depthById, rows, -1);
    }

    public FacetResult(Set<Integer> ids, boolean hasActiveFilter, Map<Integer, Integer> depthById, List<Map<String, Object>> rows, int totalCount) {
        this.ids = ids;
        this.rows = rows;
        int rowCount = rows != null ? rows.size() : (ids != null ? ids.size() : 0);
        this.count = rowCount;
        this.hasActiveFilter = hasActiveFilter;
        this.depthById = depthById;
        this.totalCount = totalCount >= 0 ? totalCount : rowCount;
    }

    public Set<Integer> getIds() {
        return ids;
    }

    public int getCount() {
        return count;
    }

    public boolean isHasActiveFilter() {
        return hasActiveFilter;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public Map<Integer, Integer> getDepthById() {
        return depthById;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }
}

