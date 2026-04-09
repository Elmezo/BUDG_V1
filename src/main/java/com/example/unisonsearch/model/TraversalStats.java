package com.example.unisonsearch.model;

/**
 * Mutable statistics for a graph traversal operation.
 * Used to detect truncation and monitor performance.
 */
public class TraversalStats {
    private int totalResults = 0;
    private boolean truncated = false;
    private int maxDepthReached = 0;

    public int getTotalResults() {
        return totalResults;
    }

    public void setTotalResults(int totalResults) {
        this.totalResults = totalResults;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public int getMaxDepthReached() {
        return maxDepthReached;
    }

    public void setMaxDepthReached(int maxDepthReached) {
        this.maxDepthReached = maxDepthReached;
    }
}

