package com.example.budg_v2.model;

import java.sql.Timestamp;

/**
 * Model class for DFCR Type-level settings (DF_CR_Type_Settings table)
 * Stores override settings for each facet type (e.g., Glossary Domain, Dataset Reference Data)
 * NULL values mean "Inherited from Facet"
 */
public class DFCRTypeSetting {
    private int id;
    private int facetId;                    // FK to module.id
    private int typeId;                     // FK to the type table (glossary_type.ID, etc.)
    private String typeName;                // Cached type name for display
    private Integer workflowCreateId;       // FK to process_definition.ID (NULL = inherited)
    private Integer workflowEditId;         // FK to process_definition.ID (NULL = inherited)
    private Integer crTypeId;               // FK to changerequest_type.ID (NULL = inherited)
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Transient fields for display names (not stored in DB)
    private transient String facetName;
    private transient String workflowCreateName;
    private transient String workflowEditName;
    private transient String crTypeName;

    public DFCRTypeSetting() {
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getFacetId() {
        return facetId;
    }

    public void setFacetId(int facetId) {
        this.facetId = facetId;
    }

    public int getTypeId() {
        return typeId;
    }

    public void setTypeId(int typeId) {
        this.typeId = typeId;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Integer getWorkflowCreateId() {
        return workflowCreateId;
    }

    public void setWorkflowCreateId(Integer workflowCreateId) {
        this.workflowCreateId = workflowCreateId;
    }

    public Integer getWorkflowEditId() {
        return workflowEditId;
    }

    public void setWorkflowEditId(Integer workflowEditId) {
        this.workflowEditId = workflowEditId;
    }

    public Integer getCrTypeId() {
        return crTypeId;
    }

    public void setCrTypeId(Integer crTypeId) {
        this.crTypeId = crTypeId;
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

    public String getFacetName() {
        return facetName;
    }

    public void setFacetName(String facetName) {
        this.facetName = facetName;
    }

    public String getWorkflowCreateName() {
        return workflowCreateName;
    }

    public void setWorkflowCreateName(String workflowCreateName) {
        this.workflowCreateName = workflowCreateName;
    }

    public String getWorkflowEditName() {
        return workflowEditName;
    }

    public void setWorkflowEditName(String workflowEditName) {
        this.workflowEditName = workflowEditName;
    }

    public String getCrTypeName() {
        return crTypeName;
    }

    public void setCrTypeName(String crTypeName) {
        this.crTypeName = crTypeName;
    }

    /**
     * Check if this setting is fully inherited (all values are null)
     */
    public boolean isFullyInherited() {
        return workflowCreateId == null && workflowEditId == null && crTypeId == null;
    }

    @Override
    public String toString() {
        return "DFCRTypeSetting{" +
                "id=" + id +
                ", facetId=" + facetId +
                ", typeId=" + typeId +
                ", typeName='" + typeName + '\'' +
                ", workflowCreateId=" + workflowCreateId +
                ", workflowEditId=" + workflowEditId +
                ", crTypeId=" + crTypeId +
                '}';
    }
}

