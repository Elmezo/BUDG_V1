package com.example.budg_v2.model;

import java.time.LocalDateTime;

public class RegulationXRegulatoryThemeRelationType {
    private Integer id;
    private String primaryName;
    private String description;
    private Integer priority;
    private String reverseName;
    private LocalDateTime lastUpdateDatetime;
    private LocalDateTime deleteDatetime;
    private Integer lastUpdateUserId;

    // Default constructor
    public RegulationXRegulatoryThemeRelationType() {}

    // Constructor with all fields
    public RegulationXRegulatoryThemeRelationType(Integer id, String primaryName, String description, 
                                                Integer priority, String reverseName, LocalDateTime lastUpdateDatetime, 
                                                LocalDateTime deleteDatetime, Integer lastUpdateUserId) {
        this.id = id;
        this.primaryName = primaryName;
        this.description = description;
        this.priority = priority;
        this.reverseName = reverseName;
        this.lastUpdateDatetime = lastUpdateDatetime;
        this.deleteDatetime = deleteDatetime;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    // Getters and Setters
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getReverseName() {
        return reverseName;
    }

    public void setReverseName(String reverseName) {
        this.reverseName = reverseName;
    }

    public LocalDateTime getLastUpdateDatetime() {
        return lastUpdateDatetime;
    }

    public void setLastUpdateDatetime(LocalDateTime lastUpdateDatetime) {
        this.lastUpdateDatetime = lastUpdateDatetime;
    }

    public LocalDateTime getDeleteDatetime() {
        return deleteDatetime;
    }

    public void setDeleteDatetime(LocalDateTime deleteDatetime) {
        this.deleteDatetime = deleteDatetime;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    @Override
    public String toString() {
        return "RegulationXRegulatoryThemeRelationType{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", priority=" + priority +
                ", reverseName='" + reverseName + '\'' +
                ", lastUpdateDatetime=" + lastUpdateDatetime +
                ", deleteDatetime=" + deleteDatetime +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
