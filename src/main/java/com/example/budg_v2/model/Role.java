package com.example.budg_v2.model;

public class Role {
    private int id;
    private Integer roletype;
    private String primaryname;
    private String description;
    private Integer parent;

    public Role() {
    }

    public Role(int id, Integer roletype, String primaryname, String description, Integer parent) {
        this.id = id;
        this.roletype = roletype;
        this.primaryname = primaryname;
        this.description = description;
        this.parent = parent;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getRoletype() {
        return roletype;
    }

    public void setRoletype(Integer roletype) {
        this.roletype = roletype;
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

    public Integer getParent() {
        return parent;
    }

    public void setParent(Integer parent) {
        this.parent = parent;
    }

    @Override
    public String toString() {
        return "Role{" +
                "id=" + id +
                ", roletype=" + roletype +
                ", primaryname='" + primaryname + '\'' +
                ", description='" + description + '\'' +
                ", parent=" + parent +
                '}';
    }
}
