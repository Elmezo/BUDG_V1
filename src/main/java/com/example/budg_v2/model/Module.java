package com.example.budg_v2.model;

public class Module {
    private int id;
    private int groupId;
    private String primaryName;
    private int rowCount;

    // Default constructor for Gson serialization
    public Module() {
    }

    public Module(int id, int groupId, String primaryName, int rowCount) {
        this.id = id;
        this.groupId = groupId;
        this.primaryName = primaryName;
        this.rowCount = rowCount;
    }

    // Getters
    public int getId() { return id; }
    public int getGroupId() { return groupId; }
    public String getPrimaryName() { return primaryName; }
    public int getRowCount() { return rowCount; }

    // Setters for Gson deserialization
    public void setId(int id) { this.id = id; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public void setPrimaryName(String primaryName) { this.primaryName = primaryName; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
}
