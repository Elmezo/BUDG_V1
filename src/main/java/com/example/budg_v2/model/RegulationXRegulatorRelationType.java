package com.example.budg_v2.model;

public class RegulationXRegulatorRelationType {
    private int id;
    private String primaryName;
    private String description;
    private String reverseName;

    // Constructors
    public RegulationXRegulatorRelationType() {}

    public RegulationXRegulatorRelationType(int id, String primaryName, String description, String reverseName) {
        this.id = id;
        this.primaryName = primaryName;
        this.description = description;
        this.reverseName = reverseName;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
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

    public String getReverseName() {
        return reverseName;
    }

    public void setReverseName(String reverseName) {
        this.reverseName = reverseName;
    }
}
