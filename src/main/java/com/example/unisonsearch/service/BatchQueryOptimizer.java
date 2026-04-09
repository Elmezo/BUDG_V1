package com.example.unisonsearch.service;

import com.example.unisonsearch.repository.DatabaseHelper;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Optimizes queries by batching multiple IDs together.
 */
public class BatchQueryOptimizer {
    
    private final DatabaseHelper databaseHelper;
    
    public BatchQueryOptimizer(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }
    
    /**
     * Get Systems for multiple Datasets (batch operation).
     */
    public List<Integer> getSystemsForMultipleDatasets(List<Integer> datasetIds) throws SQLException {
        if (datasetIds == null || datasetIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        String placeholders = String.join(",", Collections.nCopies(datasetIds.size(), "?"));
        String sql = "SELECT DISTINCT MasterSource FROM dataset " +
                    "WHERE ID IN (" + placeholders + ") " +
                    "AND MasterSource IS NOT NULL " +
                    "AND DeletedDatetime IS NULL";
        
        List<Object> params = new ArrayList<>(datasetIds);
        List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, params);
        
        return extractIds(rows, "MasterSource");
    }
    
    /**
     * Get tasks for multiple objects (batch operation).
     */
    public Map<Integer, List<Map<String, Object>>> getTasksForMultipleObjects(
            String objectType, List<Integer> objectIds) throws SQLException {
        
        if (objectIds == null || objectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        String placeholders = String.join(",", Collections.nCopies(objectIds.size(), "?"));
        String sql = "SELECT t.*, t.Object_ID " +
                    "FROM task t " +
                    "WHERE t.Object_Type = ? " +
                    "AND t.Object_ID IN (" + placeholders + ") " +
                    "AND t.Status IN ('PENDING_START', 'RUNNING')";
        
        List<Object> params = new ArrayList<>();
        params.add(objectType);
        params.addAll(objectIds);
        
        List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, params);
        
        // Group by Object_ID
        Map<Integer, List<Map<String, Object>>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object objectIdObj = row.get("Object_ID");
            if (objectIdObj instanceof Number) {
                int objectId = ((Number) objectIdObj).intValue();
                result.computeIfAbsent(objectId, k -> new ArrayList<>()).add(row);
            }
        }
        
        return result;
    }
    
    private List<Integer> extractIds(List<Map<String, Object>> rows, String columnName) {
        return rows.stream()
            .map(row -> row.get(columnName))
            .filter(Objects::nonNull)
            .map(id -> {
                if (id instanceof Integer) return (Integer) id;
                if (id instanceof Number) return ((Number) id).intValue();
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}

