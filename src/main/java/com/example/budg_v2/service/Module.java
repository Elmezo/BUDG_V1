package com.example.budg_v2.service;

/**
 * Module model class representing the module table
 */
public class Module {
    private int id;
    private int groupId;
    private String primaryName;
    private String icon;
    private boolean aclRole;
    private boolean cfEnabled;

    // Default constructor
    public Module() {}

    // Constructor with all parameters
    public Module(int id, int groupId, String primaryName, String icon, boolean aclRole, boolean cfEnabled) {
        this.id = id;
        this.groupId = groupId;
        this.primaryName = primaryName;
        this.icon = icon;
        this.aclRole = aclRole;
        this.cfEnabled = cfEnabled;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isAclRole() {
        return aclRole;
    }

    public void setAclRole(boolean aclRole) {
        this.aclRole = aclRole;
    }

    public boolean isCfEnabled() {
        return cfEnabled;
    }

    public void setCfEnabled(boolean cfEnabled) {
        this.cfEnabled = cfEnabled;
    }

    @Override
    public String toString() {
        return "Module{" +
                "id=" + id +
                ", groupId=" + groupId +
                ", primaryName='" + primaryName + '\'' +
                ", icon='" + icon + '\'' +
                ", aclRole=" + aclRole +
                ", cfEnabled=" + cfEnabled +
                '}';
    }
}
