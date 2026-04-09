package com.example.unisonsearch.config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for Related Objects enrichment in Unison Search.
 * Controls whether related objects are fetched and displayed for search results.
 */
public class RelatedObjectsConfig {
    
    // Global enable/disable flag
    private volatile boolean enabled = true;
    
    // Maximum depth for relationship traversal (default: 1 for direct relationships only)
    private volatile int maxDepth = 1;
    
    // Per-facet enable/disable flags (thread-safe)
    private final Map<String, Boolean> facetEnabled = new ConcurrentHashMap<>();
    
    // Singleton instance
    private static volatile RelatedObjectsConfig instance;
    private static final Object LOCK = new Object();
    
    /**
     * Private constructor for singleton pattern
     */
    private RelatedObjectsConfig() {
        // Initialize default facet settings - all enabled by default
        initializeDefaults();
    }
    
    /**
     * Get singleton instance
     */
    public static RelatedObjectsConfig getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new RelatedObjectsConfig();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize default facet settings
     */
    private void initializeDefaults() {
        // All facets enabled by default
        facetEnabled.put("DATASET", true);
        facetEnabled.put("SYSTEM", true);
        facetEnabled.put("GLOSSARY", true);
        facetEnabled.put("PEOPLE", true);
        facetEnabled.put("ROLE", true);
        facetEnabled.put("INTERFACE", true);
        facetEnabled.put("ORG_UNIT", true);
        facetEnabled.put("BUSINESS_AREA", true);
        facetEnabled.put("LEGAL_ENTITY", true);
        facetEnabled.put("CLIENT", true);
        facetEnabled.put("COMMITTEE", true);
        facetEnabled.put("POLICY", true);
        facetEnabled.put("PROCESS", true);
        facetEnabled.put("PROJECT", true);
        facetEnabled.put("PRODUCT", true);
        facetEnabled.put("CAPABILITY", true);
        facetEnabled.put("GEOGRAPHY", true);
        facetEnabled.put("REGULATION", true);
        facetEnabled.put("REGULATOR", true);
        facetEnabled.put("REGULATORY_THEME", true);
        facetEnabled.put("ACTIVE_TASKS", true);
        facetEnabled.put("CHANGE_REQUESTS", true);
        facetEnabled.put("ATTRIBUTE", true);
    }
    
    /**
     * Check if related objects enrichment is globally enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Set global enable/disable flag
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Check if related objects enrichment is enabled for a specific facet
     */
    public boolean isEnabledForFacet(String facet) {
        if (!enabled) {
            return false; // Global flag overrides individual facet settings
        }
        
        if (facet == null) {
            return false;
        }
        
        // Normalize facet key to uppercase with underscores
        String normalizedFacet = facet.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        
        // Return facet-specific setting, default to true if not explicitly set
        return facetEnabled.getOrDefault(normalizedFacet, true);
    }
    
    /**
     * Enable/disable related objects for a specific facet
     */
    public void setEnabledForFacet(String facet, boolean enabled) {
        if (facet == null) {
            return;
        }
        
        String normalizedFacet = facet.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        facetEnabled.put(normalizedFacet, enabled);
    }
    
    /**
     * Get maximum depth for relationship traversal
     */
    public int getMaxDepth() {
        return maxDepth;
    }
    
    /**
     * Set maximum depth for relationship traversal
     */
    public void setMaxDepth(int maxDepth) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be non-negative");
        }
        this.maxDepth = maxDepth;
    }
    
    /**
     * Get all facet settings as a map
     */
    public Map<String, Boolean> getAllFacetSettings() {
        return new HashMap<>(facetEnabled);
    }
    
    /**
     * Reset to default settings
     */
    public void resetToDefaults() {
        enabled = true;
        maxDepth = 1;
        facetEnabled.clear();
        initializeDefaults();
    }
    
    /**
     * Get configuration summary for logging
     */
    public String getSummary() {
        int enabledCount = (int) facetEnabled.values().stream().filter(v -> v).count();
        return String.format(
            "RelatedObjectsConfig[enabled=%s, maxDepth=%d, facetsEnabled=%d/%d]",
            enabled, maxDepth, enabledCount, facetEnabled.size()
        );
    }
}

