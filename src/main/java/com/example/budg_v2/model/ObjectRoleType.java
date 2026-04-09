package com.example.budg_v2.model;

public class ObjectRoleType {
    private int id;
    private String primaryname;
    private String description;
    
    // Constructor
    public ObjectRoleType() {}
    
    public ObjectRoleType(int id, String primaryname, String description) {
        this.id = id;
        this.primaryname = primaryname;
        this.description = description;
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getPrimaryname() {
        return primaryname;
    }
    
    public void setPrimaryname(String primaryname) {
        this.primaryname = primaryname;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return "ObjectRoleType{" +
                "id=" + id +
                ", primaryname='" + primaryname + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
