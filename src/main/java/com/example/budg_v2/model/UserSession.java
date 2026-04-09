package com.example.budg_v2.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Model class representing a user session
 */
public class UserSession {
    private String sessionId;
    private int userId;
    private String userEmail;
    private String deviceInfo;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime loginTime;
    private LocalDateTime lastActivity;
    private LocalDateTime expiryTime;
    private boolean isActive;
    private String location;
    private String browser;
    private String os;
    
    public UserSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.loginTime = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
        this.isActive = true;
    }
    
    public UserSession(int userId, String userEmail, String deviceInfo, String ipAddress, String userAgent) {
        this();
        this.userId = userId;
        this.userEmail = userEmail;
        this.deviceInfo = deviceInfo;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.expiryTime = LocalDateTime.now().plusHours(2); // 2 hours expiry
    }
    
    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public int getUserId() {
        return userId;
    }
    
    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    public String getUserEmail() {
        return userEmail;
    }
    
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
    
    public String getDeviceInfo() {
        return deviceInfo;
    }
    
    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public LocalDateTime getLoginTime() {
        return loginTime;
    }
    
    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }
    
    public LocalDateTime getLastActivity() {
        return lastActivity;
    }
    
    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }
    
    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }
    
    public void setExpiryTime(LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }
    
    public String getBrowser() {
        return browser;
    }
    
    public void setBrowser(String browser) {
        this.browser = browser;
    }
    
    public String getOs() {
        return os;
    }
    
    public void setOs(String os) {
        this.os = os;
    }
    
    /**
     * Check if session is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
    
    /**
     * Update last activity time
     */
    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }
    
    /**
     * Extend session expiry time
     */
    public void extendSession() {
        this.expiryTime = LocalDateTime.now().plusHours(2);
    }
    
    /**
     * Get session duration in minutes
     */
    public long getSessionDurationMinutes() {
        return java.time.Duration.between(loginTime, LocalDateTime.now()).toMinutes();
    }
    
    /**
     * Get time since last activity in minutes
     */
    public long getInactivityMinutes() {
        return java.time.Duration.between(lastActivity, LocalDateTime.now()).toMinutes();
    }
    
    @Override
    public String toString() {
        return "UserSession{" +
                "sessionId='" + sessionId + '\'' +
                ", userId=" + userId +
                ", userEmail='" + userEmail + '\'' +
                ", deviceInfo='" + deviceInfo + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", loginTime=" + loginTime +
                ", lastActivity=" + lastActivity +
                ", isActive=" + isActive +
                '}';
    }
}
