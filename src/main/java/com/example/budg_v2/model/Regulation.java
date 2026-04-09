package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;
import java.sql.Timestamp;

public class Regulation {

    @SerializedName("id")
    private Integer id;

    @SerializedName("parentId")
    private Integer parentId;

    @SerializedName("isPublic")
    private Integer isPublic;

    @SerializedName("refNumber")
    private String refNumber;

    @SerializedName("shortName")
    private String shortName;

    @SerializedName("rank")
    private Integer rank;

    @SerializedName("primaryName")
    private String primaryName;

    @SerializedName("description")
    private String description;

    @SerializedName("additionalInfo")
    private String additionalInfo;

    @SerializedName("publicationDate")
    private String publicationDate;

    @SerializedName("commentsDate")
    private String commentsDate;

    @SerializedName("finalisationDate")
    private String finalisationDate;

    @SerializedName("complianceDate")
    private String complianceDate;

    @SerializedName("legalAdvice")
    private String legalAdvice;

    @SerializedName("createDateTime")
    private Timestamp createDateTime;

    @SerializedName("lastUpdateDateTime")
    private Timestamp lastUpdateDateTime;

    @SerializedName("deletedDateTime")
    private Timestamp deletedDateTime;

    @SerializedName("regulationMaturityId")
    private Integer regulationMaturityId;

    @SerializedName("regulationProbabilityId")
    private Integer regulationProbabilityId;

    @SerializedName("regulationStatusId")
    private Integer regulationStatusId;

    @SerializedName("statusName")
    private String statusName;

    @SerializedName("regulationImpactRatingId")
    private Integer regulationImpactRatingId;

    @SerializedName("legalAdviceTypeId")
    private Integer legalAdviceTypeId;

    @SerializedName("regulationStageId")
    private Integer regulationStageId;

    @SerializedName("complianceLevelId")
    private Integer complianceLevelId;

    @SerializedName("lastUpdateUserId")
    private Integer lastUpdateUserId;

    // Constructors
    public Regulation() {}

    public Regulation(Integer id, Integer parentId, Integer isPublic, String refNumber, String shortName,
                     Integer rank, String primaryName, String description, String additionalInfo,
                     String publicationDate, String commentsDate, String finalisationDate, String complianceDate,
                     String legalAdvice, Timestamp createDateTime, Timestamp lastUpdateDateTime,
                     Timestamp deletedDateTime, Integer regulationMaturityId, Integer regulationProbabilityId,
                     Integer regulationStatusId, Integer regulationImpactRatingId, Integer legalAdviceTypeId,
                     Integer regulationStageId, Integer complianceLevelId, Integer lastUpdateUserId) {
        this.id = id;
        this.parentId = parentId;
        this.isPublic = isPublic;
        this.refNumber = refNumber;
        this.shortName = shortName;
        this.rank = rank;
        this.primaryName = primaryName;
        this.description = description;
        this.additionalInfo = additionalInfo;
        this.publicationDate = publicationDate;
        this.commentsDate = commentsDate;
        this.finalisationDate = finalisationDate;
        this.complianceDate = complianceDate;
        this.legalAdvice = legalAdvice;
        this.createDateTime = createDateTime;
        this.lastUpdateDateTime = lastUpdateDateTime;
        this.deletedDateTime = deletedDateTime;
        this.regulationMaturityId = regulationMaturityId;
        this.regulationProbabilityId = regulationProbabilityId;
        this.regulationStatusId = regulationStatusId;
        this.regulationImpactRatingId = regulationImpactRatingId;
        this.legalAdviceTypeId = legalAdviceTypeId;
        this.regulationStageId = regulationStageId;
        this.complianceLevelId = complianceLevelId;
        this.lastUpdateUserId = lastUpdateUserId;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public Integer getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Integer isPublic) {
        this.isPublic = isPublic;
    }

    public String getRefNumber() {
        return refNumber;
    }

    public void setRefNumber(String refNumber) {
        this.refNumber = refNumber;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
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

    public String getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    public String getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(String publicationDate) {
        this.publicationDate = publicationDate;
    }

    public String getCommentsDate() {
        return commentsDate;
    }

    public void setCommentsDate(String commentsDate) {
        this.commentsDate = commentsDate;
    }

    public String getFinalisationDate() {
        return finalisationDate;
    }

    public void setFinalisationDate(String finalisationDate) {
        this.finalisationDate = finalisationDate;
    }

    public String getComplianceDate() {
        return complianceDate;
    }

    public void setComplianceDate(String complianceDate) {
        this.complianceDate = complianceDate;
    }

    public String getLegalAdvice() {
        return legalAdvice;
    }

    public void setLegalAdvice(String legalAdvice) {
        this.legalAdvice = legalAdvice;
    }

    public Timestamp getCreateDateTime() {
        return createDateTime;
    }

    public void setCreateDateTime(Timestamp createDateTime) {
        this.createDateTime = createDateTime;
    }

    public Timestamp getLastUpdateDateTime() {
        return lastUpdateDateTime;
    }

    public void setLastUpdateDateTime(Timestamp lastUpdateDateTime) {
        this.lastUpdateDateTime = lastUpdateDateTime;
    }

    public Timestamp getDeletedDateTime() {
        return deletedDateTime;
    }

    public void setDeletedDateTime(Timestamp deletedDateTime) {
        this.deletedDateTime = deletedDateTime;
    }

    public Integer getRegulationMaturityId() {
        return regulationMaturityId;
    }

    public void setRegulationMaturityId(Integer regulationMaturityId) {
        this.regulationMaturityId = regulationMaturityId;
    }

    public Integer getRegulationProbabilityId() {
        return regulationProbabilityId;
    }

    public void setRegulationProbabilityId(Integer regulationProbabilityId) {
        this.regulationProbabilityId = regulationProbabilityId;
    }

    public Integer getRegulationStatusId() {
        return regulationStatusId;
    }

    public void setRegulationStatusId(Integer regulationStatusId) {
        this.regulationStatusId = regulationStatusId;
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public Integer getRegulationImpactRatingId() {
        return regulationImpactRatingId;
    }

    public void setRegulationImpactRatingId(Integer regulationImpactRatingId) {
        this.regulationImpactRatingId = regulationImpactRatingId;
    }

    public Integer getLegalAdviceTypeId() {
        return legalAdviceTypeId;
    }

    public void setLegalAdviceTypeId(Integer legalAdviceTypeId) {
        this.legalAdviceTypeId = legalAdviceTypeId;
    }

    public Integer getRegulationStageId() {
        return regulationStageId;
    }

    public void setRegulationStageId(Integer regulationStageId) {
        this.regulationStageId = regulationStageId;
    }

    public Integer getComplianceLevelId() {
        return complianceLevelId;
    }

    public void setComplianceLevelId(Integer complianceLevelId) {
        this.complianceLevelId = complianceLevelId;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    @Override
    public String toString() {
        return "Regulation{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", isPublic=" + isPublic +
                ", refNumber='" + refNumber + '\'' +
                ", shortName='" + shortName + '\'' +
                ", rank=" + rank +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                ", additionalInfo='" + additionalInfo + '\'' +
                ", publicationDate=" + publicationDate +
                ", commentsDate=" + commentsDate +
                ", finalisationDate=" + finalisationDate +
                ", complianceDate=" + complianceDate +
                ", legalAdvice='" + legalAdvice + '\'' +
                ", createDateTime=" + createDateTime +
                ", lastUpdateDateTime=" + lastUpdateDateTime +
                ", deletedDateTime=" + deletedDateTime +
                ", regulationMaturityId=" + regulationMaturityId +
                ", regulationProbabilityId=" + regulationProbabilityId +
                ", regulationStatusId=" + regulationStatusId +
                ", regulationImpactRatingId=" + regulationImpactRatingId +
                ", legalAdviceTypeId=" + legalAdviceTypeId +
                ", regulationStageId=" + regulationStageId +
                ", complianceLevelId=" + complianceLevelId +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
