package com.example.budg_v2.model;

import java.time.LocalDateTime;

public class GlossaryXGlossary {
    private int id;
    private Integer sourceGlossaryId;
    private Integer targetGlossaryId;
    private Integer relationType;
    private LocalDateTime createDatetime;
    private LocalDateTime lastUpdateDatetime;
    private Integer lastUpdateUserId;

    // Constructors
    public GlossaryXGlossary() {}

    public GlossaryXGlossary(int id, Integer sourceGlossaryId, Integer targetGlossaryId, 
                            Integer relationType, LocalDateTime createDatetime, 
                            LocalDateTime lastUpdateDatetime, Integer lastUpdateUserId) {
        this.id = id;
        this.sourceGlossaryId = sourceGlossaryId;
        this.targetGlossaryId = targetGlossaryId;
        this.relationType = relationType;
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

    public Integer getSourceGlossaryId() {
        return sourceGlossaryId;
    }

    public void setSourceGlossaryId(Integer sourceGlossaryId) {
        this.sourceGlossaryId = sourceGlossaryId;
    }

    public Integer getTargetGlossaryId() {
        return targetGlossaryId;
    }

    public void setTargetGlossaryId(Integer targetGlossaryId) {
        this.targetGlossaryId = targetGlossaryId;
    }

    public Integer getRelationType() {
        return relationType;
    }

    public void setRelationType(Integer relationType) {
        this.relationType = relationType;
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
        return "GlossaryXGlossary{" +
                "id=" + id +
                ", sourceGlossaryId=" + sourceGlossaryId +
                ", targetGlossaryId=" + targetGlossaryId +
                ", relationType=" + relationType +
                ", createDatetime=" + createDatetime +
                ", lastUpdateDatetime=" + lastUpdateDatetime +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
