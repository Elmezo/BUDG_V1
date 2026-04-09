package com.example.unisonsearch.service;

import com.example.unisonsearch.model.SearchResponse;
import java.util.List;

/**
 * Limits search results to prevent performance issues.
 */
public class SearchLimiter {
    
    private final int maxResultsPerFacet;
    private final int maxTotalResults;
    private final int timeoutMs;
    
    public SearchLimiter() {
        this.maxResultsPerFacet = 50;
        this.maxTotalResults = 200;
        this.timeoutMs = 5000;
    }
    
    public SearchLimiter(int maxResultsPerFacet, int maxTotalResults, int timeoutMs) {
        this.maxResultsPerFacet = maxResultsPerFacet;
        this.maxTotalResults = maxTotalResults;
        this.timeoutMs = timeoutMs;
    }
    
    /**
     * Apply limits to search response.
     */
    public SearchResponse applyLimits(SearchResponse response) {
        if (response == null) {
            return response;
        }
        
        List<com.example.unisonsearch.model.SearchResult> results = response.getResults();
        
        // Limit total results
        if (results.size() > maxTotalResults) {
            results = results.subList(0, maxTotalResults);
            response.setResults(results);
            response.addStat("hasMore", true);
        } else {
            response.addStat("hasMore", false);
        }
        
        return response;
    }
    
    public int getMaxResultsPerFacet() {
        return maxResultsPerFacet;
    }
    
    public int getMaxTotalResults() {
        return maxTotalResults;
    }
    
    public int getTimeoutMs() {
        return timeoutMs;
    }
}

