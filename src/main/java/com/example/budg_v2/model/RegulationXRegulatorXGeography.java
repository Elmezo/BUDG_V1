package com.example.budg_v2.model;

public class RegulationXRegulatorXGeography {
    private int id;
    private Integer regulationXRegulatorId;
    private Integer regulatorXGeographyId;
    private Integer relationType;
    private String description;
    private String lastUpdateDatetime;
    private Integer lastUpdateUserId;

    // Constructors
    public RegulationXRegulatorXGeography() {}

    public RegulationXRegulatorXGeography(int id, Integer regulationXRegulatorId, Integer regulatorXGeographyId, 
                                        Integer relationType, String description, 
                                        String lastUpdateDatetime, Integer lastUpdateUserId) {
        this.id = id;
        this.regulationXRegulatorId = regulationXRegulatorId;
        this.regulatorXGeographyId = regulatorXGeographyId;
        this.relationType = relationType;
        this.description = description;
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

    public Integer getRegulationXRegulatorId() {
        return regulationXRegulatorId;
    }

    public void setRegulationXRegulatorId(Integer regulationXRegulatorId) {
        this.regulationXRegulatorId = regulationXRegulatorId;
    }

    public Integer getRegulatorXGeographyId() {
        return regulatorXGeographyId;
    }

    public void setRegulatorXGeographyId(Integer regulatorXGeographyId) {
        this.regulatorXGeographyId = regulatorXGeographyId;
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
