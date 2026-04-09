package com.example.budg_v2.model;

import java.time.LocalDateTime;

public class RegulationXRegulatoryTheme {
    private Integer id;
    private Integer regulationId;
    private Integer regulatoryThemeId;
    private Integer relationType;
    private String description;
    private LocalDateTime createDatetime;
    private LocalDateTime lastUpdateDatetime;
    private Integer lastUpdateUserId;

    // Default constructor
    public RegulationXRegulatoryTheme() {}

    // Constructor with all fields
    public RegulationXRegulatoryTheme(Integer id, Integer regulationId, Integer regulatoryThemeId, 
                                    Integer relationType, String description, LocalDateTime createDatetime, 
                                    LocalDateTime lastUpdateDatetime, Integer lastUpdateUserId) {
        this.id = id;
        this.regulationId = regulationId;
        this.regulatoryThemeId = regulatoryThemeId;
        this.relationType = relationType;
        this.description = description;
        this.createDatetime = createDatetime;
        this.lastUpdateDatetime = lastUpdateDatetime;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getRegulationId() {
        return regulationId;
    }

    public void setRegulationId(Integer regulationId) {
        this.regulationId = regulationId;
    }

    public Integer getRegulatoryThemeId() {
        return regulatoryThemeId;
    }

    public void setRegulatoryThemeId(Integer regulatoryThemeId) {
        this.regulatoryThemeId = regulatoryThemeId;
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

    public LocalDateTime getCreateDatetime() {
        return createDatetime;
    }

    public void setCreateDatetime(LocalDateTime createDatetime) {
        this.createDatetime = createDatetime;
    }

    public LocalDateTime getLastUpdateDatetime() {
        return lastUpdateDatetime;
    }

    public void setLastUpdateDatetime(LocalDateTime lastUpdateDatetime) {
        this.lastUpdateDatetime = lastUpdateDatetime;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    @Override
    public String toString() {
        return "RegulationXRegulatoryTheme{" +
                "id=" + id +
                ", regulationId=" + regulationId +
                ", regulatoryThemeId=" + regulatoryThemeId +
                ", relationType=" + relationType +
                ", description='" + description + '\'' +
                ", createDatetime=" + createDatetime +
                ", lastUpdateDatetime=" + lastUpdateDatetime +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
