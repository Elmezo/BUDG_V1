package com.example.budg_v2.model;

public class Dataset {

    private Integer id;
    private String primaryName;
    private Integer masterSource;
    private String refNumber;
    private String definition;
    private Integer glossary;
    private String usage;
    private Integer status;
    private Integer datasetType;
    private Integer accessControlType;
    private Integer lifecycle;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }

    public Integer getMasterSource() {
        return masterSource;
    }

    public void setMasterSource(Integer masterSource) {
        this.masterSource = masterSource;
    }

    public String getRefNumber() {
        return refNumber;
    }

    public void setRefNumber(String refNumber) {
        this.refNumber = refNumber;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public Integer getGlossary() {
        return glossary;
    }

    public void setGlossary(Integer glossary) {
        this.glossary = glossary;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDatasetType() {
        return datasetType;
    }

    public void setDatasetType(Integer datasetType) {
        this.datasetType = datasetType;
    }

    public Integer getAccessControlType() {
        return accessControlType;
    }

    public void setAccessControlType(Integer accessControlType) {
        this.accessControlType = accessControlType;
    }

    public Integer getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(Integer lifecycle) {
        this.lifecycle = lifecycle;
    }
}


