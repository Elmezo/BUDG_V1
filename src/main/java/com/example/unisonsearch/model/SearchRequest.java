package com.example.unisonsearch.model;

import java.util.Map;

/**
 * Request model for Unified Search.
 */
public class SearchRequest {
    private String facet;
    private int objectId;
    private int maxDepth;
    private String dedupStrategy;
    private Integer userId;
    private Map<String, Object> filters;

    public SearchRequest() {
        this.maxDepth = 1;
        this.dedupStrategy = "BOTH";
    }

    public SearchRequest(String facet, int objectId) {
        this();
        this.facet = facet;
        this.objectId = objectId;
    }

    public SearchRequest(String facet, int objectId, int maxDepth) {
        this(facet, objectId);
        this.maxDepth = maxDepth;
    }

    public String getFacet() {
        return facet;
    }

    public void setFacet(String facet) {
        this.facet = facet;
    }

    public int getObjectId() {
        return objectId;
    }

    public void setObjectId(int objectId) {
        this.objectId = objectId;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public String getDedupStrategy() {
        return dedupStrategy;
    }

    public void setDedupStrategy(String dedupStrategy) {
        this.dedupStrategy = dedupStrategy;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }
}

