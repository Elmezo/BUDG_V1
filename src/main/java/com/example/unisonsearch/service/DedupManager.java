package com.example.unisonsearch.service;

import com.example.unisonsearch.model.SearchResult;
import java.util.*;

/**
 * Manages deduplication of search results.
 * Uses BOTH strategy (GLOBAL + PER_SOURCE) by default.
 */
public class DedupManager {
    
    public enum DedupStrategy {
        GLOBAL,      // Prevent duplicates globally
        PER_SOURCE, // Prevent duplicates per source facet
        BOTH        // Both GLOBAL and PER_SOURCE (default)
    }

    private final Set<ObjectKey> globalVisited = new HashSet<>();
    private final Map<String, Set<ObjectKey>> visitedByOriginFacet = new HashMap<>();
    private final DedupStrategy strategy;

    public DedupManager() {
        this(DedupStrategy.BOTH);
    }

    public DedupManager(DedupStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Add a result with deduplication check.
     * @param originFacet The facet that found this result
     * @param result The search result to add
     * @return true if the result was added (not a duplicate), false otherwise
     */
    public boolean addResult(String originFacet, SearchResult result) {
        ObjectKey key = ObjectKey.fromResult(result);

        boolean globallyNew = true;
        boolean locallyNew = true;

        if (strategy == DedupStrategy.GLOBAL || strategy == DedupStrategy.BOTH) {
            globallyNew = globalVisited.add(key);
        }

        if (strategy == DedupStrategy.PER_SOURCE || strategy == DedupStrategy.BOTH) {
            Set<ObjectKey> visitedForFacet = visitedByOriginFacet
                .computeIfAbsent(originFacet, k -> new HashSet<>());
            locallyNew = visitedForFacet.add(key);
        }

        // For BOTH strategy, both must be new
        if (strategy == DedupStrategy.BOTH) {
            return globallyNew && locallyNew;
        } else if (strategy == DedupStrategy.GLOBAL) {
            return globallyNew;
        } else {
            return locallyNew;
        }
    }

    /**
     * Reset the deduplication state.
     */
    public void reset() {
        globalVisited.clear();
        visitedByOriginFacet.clear();
    }

    /**
     * Check if a result already exists.
     */
    public boolean contains(String originFacet, SearchResult result) {
        ObjectKey key = ObjectKey.fromResult(result);

        if (strategy == DedupStrategy.GLOBAL || strategy == DedupStrategy.BOTH) {
            if (globalVisited.contains(key)) {
                return true;
            }
        }

        if (strategy == DedupStrategy.PER_SOURCE || strategy == DedupStrategy.BOTH) {
            Set<ObjectKey> visitedForFacet = visitedByOriginFacet.get(originFacet);
            if (visitedForFacet != null && visitedForFacet.contains(key)) {
                return true;
            }
        }

        return false;
    }

    /**
     * ObjectKey for deduplication based on Display Facet + ID + EntityType.
     */
    public record ObjectKey(String facet, int id, String entityType) {
        public static ObjectKey fromResult(SearchResult result) {
            return new ObjectKey(
                result.entityFacet(),  // Display Facet
                result.id(),
                result.entityType() != null ? result.entityType() : ""
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ObjectKey objectKey = (ObjectKey) o;
            return id == objectKey.id &&
                   Objects.equals(facet, objectKey.facet) &&
                   Objects.equals(entityType, objectKey.entityType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(facet, id, entityType);
        }
    }
}

