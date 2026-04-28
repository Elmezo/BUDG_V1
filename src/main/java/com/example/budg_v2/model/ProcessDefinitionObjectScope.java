package com.example.budg_v2.model;

import java.sql.Timestamp;

/**
 * Links a process_definition row to a specific facet object (private / object-scoped workflow).
 */
public class ProcessDefinitionObjectScope {
    private int id;
    private int processDefinitionId;
    private String facetType;
    private int objectId;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(int processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public String getFacetType() {
        return facetType;
    }

    public void setFacetType(String facetType) {
        this.facetType = facetType;
    }

    public int getObjectId() {
        return objectId;
    }

    public void setObjectId(int objectId) {
        this.objectId = objectId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
