package com.example.unisonsearch.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Active Task model for workflow and direct tasks.
 */
public class ActiveTask {
    private int id;
    private String name;
    private String objectType;
    private int objectId;
    private String objectName;
    private LocalDateTime dueDate;
    private LocalDateTime assignDate;
    private Person owner;
    private TaskStatus status;
    private boolean overdue;
    private Integer changeRequestId;
    private String workflowStep;
    
    public enum TaskStatus {
        PENDING_START,
        RUNNING,
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }
    
    public ActiveTask() {
    }
    
    public ActiveTask(int id, String name, String objectType, int objectId, String objectName,
                     LocalDateTime dueDate, LocalDateTime assignDate, Person owner,
                     TaskStatus status, boolean overdue, Integer changeRequestId, String workflowStep) {
        this.id = id;
        this.name = name;
        this.objectType = objectType;
        this.objectId = objectId;
        this.objectName = objectName;
        this.dueDate = dueDate;
        this.assignDate = assignDate;
        this.owner = owner;
        this.status = status;
        this.overdue = overdue;
        this.changeRequestId = changeRequestId;
        this.workflowStep = workflowStep;
    }
    
    /**
     * Calculate due days as a string.
     * Returns "Not Specified" if dueDate is null.
     * Returns "X days overdue" if overdue, "X days" if not overdue.
     */
    public String getDueDays() {
        if (dueDate == null) {
            return "Not Specified";
        }
        
        LocalDate now = LocalDate.now();
        LocalDate due = dueDate.toLocalDate();
        
        long days = ChronoUnit.DAYS.between(now, due);
        
        if (days < 0) {
            return Math.abs(days) + " days overdue";
        }
        
        return days + " days";
    }
    
    // Getters and Setters
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getObjectType() {
        return objectType;
    }
    
    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }
    
    public int getObjectId() {
        return objectId;
    }
    
    public void setObjectId(int objectId) {
        this.objectId = objectId;
    }
    
    public String getObjectName() {
        return objectName;
    }
    
    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }
    
    public LocalDateTime getDueDate() {
        return dueDate;
    }
    
    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }
    
    public LocalDateTime getAssignDate() {
        return assignDate;
    }
    
    public void setAssignDate(LocalDateTime assignDate) {
        this.assignDate = assignDate;
    }
    
    public Person getOwner() {
        return owner;
    }
    
    public void setOwner(Person owner) {
        this.owner = owner;
    }
    
    public TaskStatus getStatus() {
        return status;
    }
    
    public void setStatus(TaskStatus status) {
        this.status = status;
    }
    
    public boolean isOverdue() {
        return overdue;
    }
    
    public void setOverdue(boolean overdue) {
        this.overdue = overdue;
    }
    
    public Integer getChangeRequestId() {
        return changeRequestId;
    }
    
    public void setChangeRequestId(Integer changeRequestId) {
        this.changeRequestId = changeRequestId;
    }
    
    public String getWorkflowStep() {
        return workflowStep;
    }
    
    public void setWorkflowStep(String workflowStep) {
        this.workflowStep = workflowStep;
    }
    
    /**
     * Simple Person class for owner.
     */
    public static class Person {
        private int id;
        private String firstName;
        private String lastName;
        
        public Person() {
        }
        
        public Person(int id, String firstName, String lastName) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
        }
        
        public int getId() {
            return id;
        }
        
        public void setId(int id) {
            this.id = id;
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
        
        @Override
        public String toString() {
            return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "").trim();
        }
    }
}

