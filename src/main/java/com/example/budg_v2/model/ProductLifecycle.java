package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;

public class ProductLifecycle {
    @SerializedName("id")
    private Integer id;
    
    @SerializedName("primaryname")
    private String primaryName;
    
    @SerializedName("description")
    private String description;

    // Constructors
    public ProductLifecycle() {}

    public ProductLifecycle(String primaryName, String description) {
        this.primaryName = primaryName;
        this.description = description;
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

    @Override
    public String toString() {
        return "ProductLifecycle{" +
                "id=" + id +
                ", primaryName='" + primaryName + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
