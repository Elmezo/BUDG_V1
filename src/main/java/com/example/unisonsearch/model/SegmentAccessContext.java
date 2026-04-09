package com.example.unisonsearch.model;

import java.util.Set;

/**
 * Precomputed segment access context for a Unison Search request.
 * Computed once per request and reused across traversal queries.
 */
public class SegmentAccessContext {
    private final Integer userId;
    private final boolean isSuperAdmin;
    private final Set<Integer> effectiveSegments;
    private final String segmentIdList; // Precomputed comma-separated string
    private final boolean enterpriseSelected;

    public SegmentAccessContext(Integer userId, boolean isSuperAdmin, Set<Integer> effectiveSegments) {
        this.userId = userId;
        this.isSuperAdmin = isSuperAdmin;
        this.effectiveSegments = effectiveSegments;
        
        if (effectiveSegments != null && !effectiveSegments.isEmpty()) {
            this.segmentIdList = effectiveSegments.stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("1");
            this.enterpriseSelected = effectiveSegments.contains(1);
        } else {
            this.segmentIdList = "1";
            this.enterpriseSelected = false;
        }
    }

    public Integer getUserId() {
        return userId;
    }

    public boolean isSuperAdmin() {
        return isSuperAdmin;
    }

    public Set<Integer> getEffectiveSegments() {
        return effectiveSegments;
    }

    public String getSegmentIdList() {
        return segmentIdList;
    }

    public boolean isEnterpriseSelected() {
        return enterpriseSelected;
    }

    public boolean isAnonymous() {
        return userId == null || userId <= 0;
    }
}

