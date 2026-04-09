package com.example.unisonsearch.model;

/**
 * Single search result item.
 */
public class SearchResult {
    private String entityFacet;  // Display Facet
    private int id;
    private String entityType;
    private String name;
    private int depth;
    private String sourceFacet;  // Origin facet that found this result

    public SearchResult() {
    }

    public SearchResult(String entityFacet, int id, String entityType) {
        this.entityFacet = entityFacet;
        this.id = id;
        this.entityType = entityType;
    }

    public static SearchResult fromEntity(String entityFacet, int id, String entityType) {
        return new SearchResult(entityFacet, id, entityType);
    }

    public String entityFacet() {
        return entityFacet;
    }

    public void setEntityFacet(String entityFacet) {
        this.entityFacet = entityFacet;
    }

    public int id() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String entityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String getSourceFacet() {
        return sourceFacet;
    }

    public void setSourceFacet(String sourceFacet) {
        this.sourceFacet = sourceFacet;
    }
}

