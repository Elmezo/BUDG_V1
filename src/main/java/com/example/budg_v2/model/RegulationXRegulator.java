package com.example.budg_v2.model;

public class RegulationXRegulator {
    private int id;
    private Integer regulationId;
    private Integer regulatorId;
    private Integer relationType;
    private String description;
    private String createDatetime;
    private String lastUpdateDatetime;
    private Integer lastUpdateUserId;

    // Constructors
    public RegulationXRegulator() {}

    public RegulationXRegulator(int id, Integer regulationId, Integer regulatorId, 
                               Integer relationType, String description, String createDatetime, 
                               String lastUpdateDatetime, Integer lastUpdateUserId) {
        this.id = id;
        this.regulationId = regulationId;
        this.regulatorId = regulatorId;
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

    public Integer getRegulationId() {
        return regulationId;
    }

    public void setRegulationId(Integer regulationId) {
        this.regulationId = regulationId;
    }

    public Integer getRegulatorId() {
        return regulatorId;
    }

    public void setRegulatorId(Integer regulatorId) {
        this.regulatorId = regulatorId;
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
