package com.example.unisonsearch.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable scope constraint for graph traversal.
 * Maps normalized facet names to allowed object IDs within the rooted universe.
 */
public class TraversalScope {
    private final Map<String, Set<Integer>> allowedIdsByFacet;

    public TraversalScope(Map<String, Set<Integer>> allowedIdsByFacet) {
        this.allowedIdsByFacet = allowedIdsByFacet != null 
                ? new HashMap<>(allowedIdsByFacet) 
                : new HashMap<>();
    }

    /**
     * Get allowed IDs for a facet. Returns empty set if facet is not in scope.
     */
    public Set<Integer> getAllowedIds(String normalizedFacet) {
        return allowedIdsByFacet.getOrDefault(normalizedFacet, Collections.emptySet());
    }

    /**
     * Check if this scope has constraints (is not empty).
     */
    public boolean hasConstraints() {
        return !allowedIdsByFacet.isEmpty();
    }

    /**
     * Get all facets in this scope.
     */
    public Set<String> getFacets() {
        return allowedIdsByFacet.keySet();
    }

    public Map<String, Set<Integer>> getAllowedIdsByFacet() {
        return Collections.unmodifiableMap(allowedIdsByFacet);
    }
}

