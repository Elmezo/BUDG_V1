package com.example.unisonsearch.service;

import com.example.unisonsearch.model.FacetResult;

import java.util.*;

/**
 * Service for performing set operations (AND, OR, NOT) on facet search results.
 * Operates on facet-level results, not SQL conditions.
 */
public class CompoundQueryService {

    /**
     * Intersect two result sets (AND operation).
     * For each facet, returns IDs that exist in both sets.
     * 
     * @param setA First result set
     * @param setB Second result set
     * @return Intersected result set
     */
    public Map<String, FacetResult> intersectResults(Map<String, FacetResult> setA, Map<String, FacetResult> setB) {
        //system.out.println("[CompoundQuery] intersectResults called");
        //system.out.println("[CompoundQuery] setA facets: " + (setA != null ? setA.keySet() : "null"));
        //system.out.println("[CompoundQuery] setB facets: " + (setB != null ? setB.keySet() : "null"));
        
        Map<String, FacetResult> result = new HashMap<>();
        
        // Get all facets from both sets
        Set<String> allFacets = new HashSet<>();
        if (setA != null) allFacets.addAll(setA.keySet());
        if (setB != null) allFacets.addAll(setB.keySet());
        
        for (String facet : allFacets) {
            FacetResult resultA = setA != null ? setA.get(facet) : null;
            FacetResult resultB = setB != null ? setB.get(facet) : null;
            
            Set<Integer> idsA = resultA != null ? resultA.getIds() : new HashSet<>();
            Set<Integer> idsB = resultB != null ? resultB.getIds() : new HashSet<>();
            
            //system.out.println("[CompoundQuery] Facet " + facet + ": setA has " + idsA.size() + " IDs, setB has " + idsB.size() + " IDs");
            
            // Intersection: IDs that exist in both sets
            Set<Integer> intersection = new HashSet<>(idsA);
            intersection.retainAll(idsB);
            
            //system.out.println("[CompoundQuery] Facet " + facet + ": intersection has " + intersection.size() + " IDs");
            
            // hasActiveFilter is true if either set has active filter
            boolean hasActiveFilter = (resultA != null && resultA.isHasActiveFilter()) || 
                                     (resultB != null && resultB.isHasActiveFilter());
            
            Map<Integer, Integer> depth = mergeDepthIntersection(resultA, resultB, intersection);
            result.put(facet, new FacetResult(intersection, hasActiveFilter, depth));
        }
        
        //system.out.println("[CompoundQuery] intersectResults returning " + result.size() + " facets");
        return result;
    }

    /**
     * Union two result sets (OR operation).
     * For each facet, returns IDs that exist in either set.
     * 
     * @param setA First result set
     * @param setB Second result set
     * @return Unioned result set
     */
    public Map<String, FacetResult> unionResults(Map<String, FacetResult> setA, Map<String, FacetResult> setB) {
        Map<String, FacetResult> result = new HashMap<>();
        
        // Get all facets from both sets
        Set<String> allFacets = new HashSet<>();
        if (setA != null) allFacets.addAll(setA.keySet());
        if (setB != null) allFacets.addAll(setB.keySet());
        
        for (String facet : allFacets) {
            FacetResult resultA = setA != null ? setA.get(facet) : null;
            FacetResult resultB = setB != null ? setB.get(facet) : null;
            
            Set<Integer> idsA = resultA != null ? resultA.getIds() : new HashSet<>();
            Set<Integer> idsB = resultB != null ? resultB.getIds() : new HashSet<>();
            
            // Union: IDs from both sets
            Set<Integer> union = new HashSet<>(idsA);
            union.addAll(idsB);
            
            // hasActiveFilter is true if either set has active filter
            boolean hasActiveFilter = (resultA != null && resultA.isHasActiveFilter()) || 
                                     (resultB != null && resultB.isHasActiveFilter());
            
            Map<Integer, Integer> depth = mergeDepthUnion(resultA, resultB, union);
            
            // Merge rows from both results
            Map<Integer, Map<String, Object>> rowsById = new LinkedHashMap<>();
            Set<String> anonRows = new HashSet<>();
            
            mergeRowsIntoMap(rowsById, anonRows, resultA != null ? resultA.getRows() : null);
            mergeRowsIntoMap(rowsById, anonRows, resultB != null ? resultB.getRows() : null);
            
            List<Map<String, Object>> mergedRows = new ArrayList<>(rowsById.values());
            for (String s : anonRows) {
                Map<String, Object> m = new HashMap<>();
                m.put("_raw", s);
                mergedRows.add(m);
            }
            
            // Ensure union contains IDs from rows as well
            rowsById.keySet().forEach(union::add);
            
            // Merge totalCount - take the maximum, or use merged rows size if both are 0
            int totalA = resultA != null ? resultA.getTotalCount() : 0;
            int totalB = resultB != null ? resultB.getTotalCount() : 0;
            int total = Math.max(totalA, totalB);
            if (total == 0) {
                total = mergedRows != null ? mergedRows.size() : union.size();
            }
            
            result.put(facet, new FacetResult(union, hasActiveFilter, depth, mergedRows, total));
        }
        
        return result;
    }

    /**
     * Exclude second set from first set (NOT operation).
     * For each facet, returns IDs from setA that are not in setB.
     * 
     * @param setA First result set (base set)
     * @param setB Second result set (exclusion set)
     * @return Result set with setB excluded from setA
     */
    public Map<String, FacetResult> excludeResults(Map<String, FacetResult> setA, Map<String, FacetResult> setB) {
        Map<String, FacetResult> result = new HashMap<>();
        
        if (setA == null || setA.isEmpty()) {
            return result;
        }
        
        for (Map.Entry<String, FacetResult> entry : setA.entrySet()) {
            String facet = entry.getKey();
            FacetResult resultA = entry.getValue();
            FacetResult resultB = setB != null ? setB.get(facet) : null;
            
            Set<Integer> idsA = resultA != null ? resultA.getIds() : new HashSet<>();
            Set<Integer> idsB = resultB != null ? resultB.getIds() : new HashSet<>();
            
            // Exclusion: IDs from setA that are not in setB
            Set<Integer> excluded = new HashSet<>(idsA);
            excluded.removeAll(idsB);
            
            // Keep hasActiveFilter from setA
            boolean hasActiveFilter = resultA != null && resultA.isHasActiveFilter();
            
            Map<Integer, Integer> depth = resultA != null ? filterDepthMap(resultA.getDepthById(), excluded) : new HashMap<>();
            result.put(facet, new FacetResult(excluded, hasActiveFilter, depth));
        }
        
        return result;
    }

    private Map<Integer, Integer> mergeDepthUnion(FacetResult a, FacetResult b, Set<Integer> ids) {
        Map<Integer, Integer> merged = new HashMap<>();
        Map<Integer, Integer> da = a != null && a.getDepthById() != null ? a.getDepthById() : Map.of();
        Map<Integer, Integer> db = b != null && b.getDepthById() != null ? b.getDepthById() : Map.of();
        for (Integer id : ids) {
            int depth = Math.min(da.getOrDefault(id, Integer.MAX_VALUE), db.getOrDefault(id, Integer.MAX_VALUE));
            if (depth == Integer.MAX_VALUE) depth = 0;
            merged.put(id, depth);
        }
        return merged;
    }

    private Map<Integer, Integer> mergeDepthIntersection(FacetResult a, FacetResult b, Set<Integer> ids) {
        Map<Integer, Integer> merged = new HashMap<>();
        Map<Integer, Integer> da = a != null && a.getDepthById() != null ? a.getDepthById() : Map.of();
        Map<Integer, Integer> db = b != null && b.getDepthById() != null ? b.getDepthById() : Map.of();
        for (Integer id : ids) {
            int depth = Math.min(da.getOrDefault(id, Integer.MAX_VALUE), db.getOrDefault(id, Integer.MAX_VALUE));
            if (depth == Integer.MAX_VALUE) depth = 0;
            merged.put(id, depth);
        }
        return merged;
    }

    private Map<Integer, Integer> filterDepthMap(Map<Integer, Integer> source, Set<Integer> ids) {
        if (source == null) return new HashMap<>();
        Map<Integer, Integer> filtered = new HashMap<>();
        for (Integer id : ids) {
            if (source.containsKey(id)) {
                filtered.put(id, source.get(id));
            }
        }
        return filtered;
    }

    /**
     * Merge rows from a FacetResult into a map keyed by ID.
     * Handles rows with IDs and rows without IDs (anonymous rows).
     * 
     * @param rowsById Map to store rows with IDs (ID -> row)
     * @param anonRows Set to store unique anonymous rows (rows without IDs)
     * @param rows List of rows to merge
     */
    private void mergeRowsIntoMap(Map<Integer, Map<String, Object>> rowsById, Set<String> anonRows,
            List<Map<String, Object>> rows) {
        if (rows == null)
            return;
        for (Map<String, Object> row : rows) {
            Integer id = extractIntId(row);
            if (id != null) {
                rowsById.putIfAbsent(id, row);
            } else {
                // For rows without ID, keep a unique string form to avoid duplicates
                String key = row != null ? row.toString() : "null";
                anonRows.add(key);
            }
        }
    }

    /**
     * Extract integer ID from a row map.
     * Supports multiple ID field names: "ID", "id", "Id", and "taskId" (for Active Tasks).
     * 
     * @param row Row map to extract ID from
     * @return Integer ID if found, null otherwise
     */
    private Integer extractIntId(Map<String, Object> row) {
        if (row == null)
            return null;
        Object id = null;
        if (row.containsKey("ID"))
            id = row.get("ID");
        else if (row.containsKey("id"))
            id = row.get("id");
        else if (row.containsKey("Id"))
            id = row.get("Id");
        else if (row.containsKey("taskId"))
            id = row.get("taskId");
        if (id instanceof Number) {
            return ((Number) id).intValue();
        }
        return null;
    }
}

