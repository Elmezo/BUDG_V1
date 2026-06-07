package com.example.budg_v2.model;

public class Attribute {

    private Integer id;
    private Integer dataTypeId;
    private Integer requirementId;
    private Integer datasetId;
    private Integer glossaryId;
    private Integer origination;
    private Integer editability;
    private Integer editabilityRole;
    private String refNumber;
    private String primaryName;
    private String definition;
    private Integer isMandatory;
    private Integer isPrimaryKey;
    private Integer rank;
    private String businessLogic;
    private Integer dataLength;
    private Double confidenceScore;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getDataTypeId() {
        return dataTypeId;
    }

    public void setDataTypeId(Integer dataTypeId) {
        this.dataTypeId = dataTypeId;
    }

    public Integer getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Integer requirementId) {
        this.requirementId = requirementId;
    }

    public Integer getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Integer datasetId) {
        this.datasetId = datasetId;
    }

    public Integer getGlossaryId() {
        return glossaryId;
    }

    public void setGlossaryId(Integer glossaryId) {
        this.glossaryId = glossaryId;
    }

    public Integer getOrigination() {
        return origination;
    }

    public void setOrigination(Integer origination) {
        this.origination = origination;
    }

    public Integer getEditability() {
        return editability;
    }

    public void setEditability(Integer editability) {
        this.editability = editability;
    }

    public Integer getEditabilityRole() {
        return editabilityRole;
    }

    public void setEditabilityRole(Integer editabilityRole) {
        this.editabilityRole = editabilityRole;
    }

    public String getRefNumber() {
        return refNumber;
    }

    public void setRefNumber(String refNumber) {
        this.refNumber = refNumber;
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public Integer getIsMandatory() {
        return isMandatory;
    }

    public void setIsMandatory(Integer isMandatory) {
        this.isMandatory = isMandatory;
    }

    public Integer getIsPrimaryKey() {
        return isPrimaryKey;
    }

    public void setIsPrimaryKey(Integer isPrimaryKey) {
        this.isPrimaryKey = isPrimaryKey;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public String getBusinessLogic() {
        return businessLogic;
    }

    public void setBusinessLogic(String businessLogic) {
        this.businessLogic = businessLogic;
    }

    public Integer getDataLength() {
        return dataLength;
    }

    public void setDataLength(Integer dataLength) {
        this.dataLength = dataLength;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }
}

