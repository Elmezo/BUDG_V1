package com.example.budg_v2.model;

public class ObjectRole {
    private int id;
    private Integer module;
    private String primaryname;
    private String description;
    private Boolean defaultrole;
    private Integer objectroletypeId;
    private String moduleName; // إضافة اسم module
    
    // Constructor
    public ObjectRole() {}
    
    public ObjectRole(int id, Integer module, String primaryname, String description, 
                     Boolean defaultrole, Integer objectroletypeId) {
        this.id = id;
        this.module = module;
        this.primaryname = primaryname;
        this.description = description;
        this.defaultrole = defaultrole;
        this.objectroletypeId = objectroletypeId;
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public Integer getModule() {
        return module;
    }
    
    public void setModule(Integer module) {
        this.module = module;
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
    
    public Boolean getDefaultrole() {
        return defaultrole;
    }
    
    public void setDefaultrole(Boolean defaultrole) {
        this.defaultrole = defaultrole;
    }
    
    public Integer getObjectroletypeId() {
        return objectroletypeId;
    }
    
    public void setObjectroletypeId(Integer objectroletypeId) {
        this.objectroletypeId = objectroletypeId;
    }
    
    // إضافة getter و setter لـ moduleName
    public String getModuleName() {
        return moduleName;
    }
    
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }
    
    @Override
    public String toString() {
        return "ObjectRole{" +
                "id=" + id +
                ", module=" + module +
                ", primaryname='" + primaryname + '\'' +
                ", description='" + description + '\'' +
                ", defaultrole=" + defaultrole +
                ", objectroletypeId=" + objectroletypeId +
                ", moduleName='" + moduleName + '\'' +
                '}';
    }
}
