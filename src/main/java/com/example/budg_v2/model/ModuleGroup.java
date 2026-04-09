package com.example.budg_v2.model;

public class ModuleGroup {
    private int id;
    private String primaryName;

    public ModuleGroup(int id, String primaryName) {
        this.id = id;
        this.primaryName = primaryName;
    }

    public int getId() { return id; }
    public String getPrimaryName() { return primaryName; }
}
