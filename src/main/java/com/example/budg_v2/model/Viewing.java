package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;

public class Viewing {
    @SerializedName("id")
    private Integer id;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("createdatetime")
    private String createDateTime;
    
    @SerializedName("lastupdatedatetime")
    private String lastUpdateDateTime;
    
    @SerializedName("deleteddatetime")
    private String deletedDateTime;
    
    @SerializedName("lastupdateuser_id")
    private Integer lastUpdateUserId;

    // Constructors
    public Viewing() {}

    public Viewing(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreateDateTime() {
        return createDateTime;
    }

    public void setCreateDateTime(String createDateTime) {
        this.createDateTime = createDateTime;
    }

    public String getLastUpdateDateTime() {
        return lastUpdateDateTime;
    }

    public void setLastUpdateDateTime(String lastUpdateDateTime) {
        this.lastUpdateDateTime = lastUpdateDateTime;
    }

    public String getDeletedDateTime() {
        return deletedDateTime;
    }

    public void setDeletedDateTime(String deletedDateTime) {
        this.deletedDateTime = deletedDateTime;
    }

    public Integer getLastUpdateUserId() {
        return lastUpdateUserId;
    }

    public void setLastUpdateUserId(Integer lastUpdateUserId) {
        this.lastUpdateUserId = lastUpdateUserId;
    }

    @Override
    public String toString() {
        return "Viewing{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", createDateTime='" + createDateTime + '\'' +
                ", lastUpdateDateTime='" + lastUpdateDateTime + '\'' +
                ", deletedDateTime='" + deletedDateTime + '\'' +
                ", lastUpdateUserId=" + lastUpdateUserId +
                '}';
    }
}
