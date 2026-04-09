package com.example.budg_v2.model;

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
    
    private String profileName;
    
    private String functionName;
    
    private String functionDescription;
    
    private String password;
    
    private Integer orgUnitId;
    
    private Integer sourceId;
    
    private Integer profileImageId;
    
    private Integer systemRole;
    
    private Integer lifecycleId;
    
    private Integer employmentTypeId;
    
    private Timestamp createdAt;
    
    private Timestamp updatedAt;

    public Person() {}

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
    
    public String getProfileName() {
        return profileName;
    }
    
    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }
    
    public String getFunctionName() {
        return functionName;
    }
    
    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }
    
    public String getFunctionDescription() {
        return functionDescription;
    }
    
    public void setFunctionDescription(String functionDescription) {
        this.functionDescription = functionDescription;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public Integer getOrgUnitId() {
        return orgUnitId;
    }
    
    public void setOrgUnitId(Integer orgUnitId) {
        this.orgUnitId = orgUnitId;
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
    
    public Integer getSourceId() {
        return sourceId;
    }
    
    public void setSourceId(Integer sourceId) {
        this.sourceId = sourceId;
    }
    
    public Integer getProfileImageId() {
        return profileImageId;
    }
    
    public void setProfileImageId(Integer profileImageId) {
        this.profileImageId = profileImageId;
    }
    
    public Integer getSystemRole() {
        return systemRole;
    }
    
    public void setSystemRole(Integer systemRole) {
        this.systemRole = systemRole;
    }
    
    public Integer getLifecycleId() {
        return lifecycleId;
    }
    
    public void setLifecycleId(Integer lifecycleId) {
        this.lifecycleId = lifecycleId;
    }
    
    public Integer getEmploymentTypeId() {
        return employmentTypeId;
    }
    
    public void setEmploymentTypeId(Integer employmentTypeId) {
        this.employmentTypeId = employmentTypeId;
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
                ", profileName='" + profileName + '\'' +
                ", functionName='" + functionName + '\'' +
                ", functionDescription='" + functionDescription + '\'' +
                ", password='" + password + '\'' +
                ", orgUnitId=" + orgUnitId +
                ", sourceId=" + sourceId +
                ", profileImageId=" + profileImageId +
                ", systemRole=" + systemRole +
                ", lifecycleId=" + lifecycleId +
                ", employmentTypeId=" + employmentTypeId +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
