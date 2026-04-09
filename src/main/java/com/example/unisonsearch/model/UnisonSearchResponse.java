package com.example.unisonsearch.model;

import java.util.Map;
import java.util.Set;

/**
 * Response model for Unison Search API.
 * Contains results per facet and metadata about the search execution.
 */
public class UnisonSearchResponse {
    private boolean success;
    private Map<String, FacetResult> results;
    private int searchCounter;
    private long executionTimeMs;
    private String error;
    private Map<String, Map<String, Set<Integer>>> relatedObjects; // facet -> (relatedFacet -> relatedIds)

    public UnisonSearchResponse() {
    }

    public UnisonSearchResponse(boolean success, Map<String, FacetResult> results, int searchCounter, long executionTimeMs) {
        this.success = success;
        this.results = results;
        this.searchCounter = searchCounter;
        this.executionTimeMs = executionTimeMs;
    }

    public static UnisonSearchResponse error(String errorMessage) {
        UnisonSearchResponse response = new UnisonSearchResponse();
        response.success = false;
        response.error = errorMessage;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Map<String, FacetResult> getResults() {
        return results;
    }

    public void setResults(Map<String, FacetResult> results) {
        this.results = results;
    }

    public int getSearchCounter() {
        return searchCounter;
    }

    public void setSearchCounter(int searchCounter) {
        this.searchCounter = searchCounter;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Map<String, Map<String, Set<Integer>>> getRelatedObjects() {
        return relatedObjects;
    }

    public void setRelatedObjects(Map<String, Map<String, Set<Integer>>> relatedObjects) {
        this.relatedObjects = relatedObjects;
    }
}

