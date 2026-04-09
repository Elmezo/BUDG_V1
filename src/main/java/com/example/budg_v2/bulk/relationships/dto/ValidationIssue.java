package com.example.budg_v2.bulk.relationships.dto;

/**
 * Represents a validation issue (warning or error) for a specific row
 */
public class ValidationIssue {
    private int row;
    private String column;
    private String value;
    private String message;
    private String issueType; // WARNING or ERROR
    
    public ValidationIssue() {
    }
    
    public ValidationIssue(int row, String column, String value, String message, String issueType) {
        this.row = row;
        this.column = column;
        this.value = value;
        this.message = message;
        this.issueType = issueType;
    }
    
    public static ValidationIssue warning(int row, String column, String value, String message) {
        return new ValidationIssue(row, column, value, message, "WARNING");
    }
    
    public static ValidationIssue error(int row, String column, String value, String message) {
        return new ValidationIssue(row, column, value, message, "ERROR");
    }
    
    // Getters and Setters
    public int getRow() {
        return row;
    }
    
    public void setRow(int row) {
        this.row = row;
    }
    
    public String getColumn() {
        return column;
    }
    
    public void setColumn(String column) {
        this.column = column;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getIssueType() {
        return issueType;
    }
    
    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }
    
    @Override
    public String toString() {
        return "ValidationIssue{" +
                "row=" + row +
                ", column='" + column + '\'' +
                ", value='" + value + '\'' +
                ", message='" + message + '\'' +
                ", issueType='" + issueType + '\'' +
                '}';
    }
}

