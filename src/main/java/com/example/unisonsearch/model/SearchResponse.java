package com.example.unisonsearch.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response model for Unified Search.
 */
public class SearchResponse {
    private List<SearchResult> results;
    private List<ActiveTask> activeTasks;
    private Map<String, Object> stats;
    private boolean success;
    private String errorMessage;

    public SearchResponse() {
        this.results = new ArrayList<>();
        this.activeTasks = new ArrayList<>();
        this.stats = new HashMap<>();
        this.success = true;
    }

    public static SearchResponse error(String errorMessage) {
        SearchResponse response = new SearchResponse();
        response.success = false;
        response.errorMessage = errorMessage;
        return response;
    }

    public List<SearchResult> getResults() {
        return results;
    }

    public void setResults(List<SearchResult> results) {
        this.results = results;
    }

    public void addResult(SearchResult result) {
        this.results.add(result);
    }

    public List<ActiveTask> getActiveTasks() {
        return activeTasks;
    }

    public void setActiveTasks(List<ActiveTask> activeTasks) {
        this.activeTasks = activeTasks;
    }

    public void addActiveTask(ActiveTask task) {
        this.activeTasks.add(task);
    }

    public Map<String, Object> getStats() {
        return stats;
    }

    public void setStats(Map<String, Object> stats) {
        this.stats = stats;
    }

    public void addStat(String key, Object value) {
        this.stats.put(key, value);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getTotalResults() {
        return results != null ? results.size() : 0;
    }

    public boolean hasMore() {
        Boolean hasMore = (Boolean) stats.get("hasMore");
        return hasMore != null && hasMore;
    }
}

