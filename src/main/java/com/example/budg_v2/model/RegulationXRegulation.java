package com.example.budg_v2.model;

public class RegulationXRegulation {
    private int id;
    private Integer sourceRegulationId;
    private Integer targetRegulationId;
    private Integer relationType;
    private String description;
    private String createDatetime;
    private String lastUpdateDatetime;
    private Integer lastUpdateUserId;

    // Constructors
    public RegulationXRegulation() {}

    public RegulationXRegulation(int id, Integer sourceRegulationId, Integer targetRegulationId, 
                                Integer relationType, String description, String createDatetime, 
                                String lastUpdateDatetime, Integer lastUpdateUserId) {
        this.id = id;
        this.sourceRegulationId = sourceRegulationId;
        this.targetRegulationId = targetRegulationId;
        this.relationType = relationType;
        this.description = description;
        this.createDatetime = createDatetime;
        this.lastUpdateDatetime = lastUpdateDatetime;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getSourceRegulationId() {
        return sourceRegulationId;
    }

    public void setSourceRegulationId(Integer sourceRegulationId) {
        this.sourceRegulationId = sourceRegulationId;
    }

    public Integer getTargetRegulationId() {
        return targetRegulationId;
    }

    public void setTargetRegulationId(Integer targetRegulationId) {
        this.targetRegulationId = targetRegulationId;
    }

    public Integer getRelationType() {
        return relationType;
    }

    public void setRelationType(Integer relationType) {
        this.relationType = relationType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreateDatetime() {
        return createDatetime;
    }

    public void setCreateDatetime(String createDatetime) {
        this.createDatetime = createDatetime;
    }

    public String getLastUpdateDatetime() {
        return lastUpdateDatetime;
    }

    public void setLastUpdateDatetime(String lastUpdateDatetime) {
        this.lastUpdateDatetime = lastUpdateDatetime;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }
}
