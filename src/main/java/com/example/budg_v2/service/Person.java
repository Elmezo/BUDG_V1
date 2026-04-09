package com.example.budg_v2.service;

import java.sql.Timestamp;

public class Person {
    private int id;
    private int statusId;
    private String statusName;
    private String orgUnitName;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String description;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    // Default constructor
    public Person() {}
    
    // Constructor with all fields
    public Person(int id, int statusId, String statusName, String orgUnitName, 
                  String firstName, String lastName, String email, String phone, 
                  String description, Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.statusId = statusId;
        this.statusName = statusName;
        this.orgUnitName = orgUnitName;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getStatusId() {
        return statusId;
    }
    
    public void setStatusId(int statusId) {
        this.statusId = statusId;
    }
    
    public String getStatusName() {
        return statusName;
    }
    
    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }
    
    public String getOrgUnitName() {
        return orgUnitName;
    }
    
    public void setOrgUnitName(String orgUnitName) {
        this.orgUnitName = orgUnitName;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public String toString() {
        return "Person{" +
                "id=" + id +
                ", statusId=" + statusId +
                ", statusName='" + statusName + '\'' +
                ", orgUnitName='" + orgUnitName + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
