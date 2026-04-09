package com.example.budg_v2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * File-based lock manager for LDAP synchronization
 * Prevents concurrent synchronization operations
 */
public class LdapSyncLockManager {
    
    private static final Logger logger = LoggerFactory.getLogger(LdapSyncLockManager.class);
    private static final String LOCK_DIR = "/budg_ldap_synchronizer/tmp";
    private static final String LOCK_FILE = "ldap_sync.lock";
    private static final long LOCK_EXPIRATION_MS = 3600 * 1000; // 3600 seconds (1 hour)
    
    private final Path lockFilePath;
    
    public LdapSyncLockManager() {
        this.lockFilePath = Paths.get(LOCK_DIR, LOCK_FILE);
    }
    
    /**
     * Acquire lock file for LDAP synchronization
     * @param userId User ID initiating the sync
     * @return true if lock was acquired, false if already locked
     */
    public boolean acquireLock(int userId) {
        try {
            // Create lock directory if it doesn't exist
            Path lockDir = lockFilePath.getParent();
            if (!Files.exists(lockDir)) {
                Files.createDirectories(lockDir);
                logger.info("Created lock directory: {}", lockDir);
            }
            
            // Check if lock already exists and is valid
            // isLocked() will automatically remove stale locks (expired or process not running)
            if (isLocked()) {
                LockInfo lockInfo = getLockInfo();
                if (lockInfo != null) {
                    logger.warn("LDAP sync is already running. Locked by user {} at {}", 
                               lockInfo.getUserId(), new java.util.Date(lockInfo.getStartTimestamp()));
                    return false;
                } else {
                    // Lock file exists but is invalid, remove it
                    logger.info("Removing invalid lock file");
                    releaseLock();
                }
            }
            
            // Create lock file with metadata
            Properties lockProps = new Properties();
            lockProps.setProperty("processId", String.valueOf(getProcessId()));
            lockProps.setProperty("startTimestamp", String.valueOf(System.currentTimeMillis()));
            lockProps.setProperty("userId", String.valueOf(userId));
            
            try (FileOutputStream fos = new FileOutputStream(lockFilePath.toFile())) {
                lockProps.store(fos, "LDAP Synchronization Lock File");
            }
            
            logger.info("LDAP sync lock acquired by user {}", userId);
            return true;
            
        } catch (IOException e) {
            logger.error("Error acquiring LDAP sync lock", e);
            return false;
        }
    }
    
    /**
     * Release lock file
     */
    public void releaseLock() {
        try {
            if (Files.exists(lockFilePath)) {
                Files.delete(lockFilePath);
                logger.info("LDAP sync lock released");
            }
        } catch (IOException e) {
            logger.error("Error releasing LDAP sync lock", e);
        }
    }
    
    /**
     * Check if lock exists and is valid (not expired)
     * @return true if lock exists and is valid
     */
    public boolean isLocked() {
        try {
            if (!Files.exists(lockFilePath)) {
                return false;
            }
            
            LockInfo lockInfo = getLockInfo();
            if (lockInfo == null) {
                return false;
            }
            
            // Check if lock has expired
            long lockAge = System.currentTimeMillis() - lockInfo.getStartTimestamp();
            if (lockAge > LOCK_EXPIRATION_MS) {
                logger.warn("Lock file expired (age: {} ms), removing stale lock", lockAge);
                releaseLock();
                return false;
            }
            
            // Check if process is still running - if not, remove stale lock
            if (!isProcessRunning(lockInfo.getProcessId())) {
                logger.warn("Process {} is not running, removing stale lock", lockInfo.getProcessId());
                releaseLock();
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error checking lock status", e);
            return false;
        }
    }
    
    /**
     * Get lock information
     * @return LockInfo object or null if lock doesn't exist or is invalid
     */
    public LockInfo getLockInfo() {
        try {
            if (!Files.exists(lockFilePath)) {
                return null;
            }
            
            Properties lockProps = new Properties();
            try (FileInputStream fis = new FileInputStream(lockFilePath.toFile())) {
                lockProps.load(fis);
            }
            
            String processIdStr = lockProps.getProperty("processId");
            String startTimestampStr = lockProps.getProperty("startTimestamp");
            String userIdStr = lockProps.getProperty("userId");
            
            if (processIdStr == null || startTimestampStr == null || userIdStr == null) {
                logger.warn("Lock file is missing required properties");
                return null;
            }
            
            return new LockInfo(
                Long.parseLong(processIdStr),
                Long.parseLong(startTimestampStr),
                Integer.parseInt(userIdStr)
            );
            
        } catch (Exception e) {
            logger.error("Error reading lock info", e);
            return null;
        }
    }
    
    /**
     * Get current process ID
     */
    private long getProcessId() {
        try {
            String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            return Long.parseLong(processName.split("@")[0]);
        } catch (Exception e) {
            // Fallback to thread ID if process ID unavailable
            return Thread.currentThread().getId();
        }
    }
    
    /**
     * Check if process is still running (best effort)
     */
    private boolean isProcessRunning(long processId) {
        try {
            // On Windows
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Process process = Runtime.getRuntime().exec(
                    "tasklist /FI \"PID eq " + processId + "\"");
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains(String.valueOf(processId))) {
                            return true;
                        }
                    }
                }
                return false;
            } else {
                // On Unix/Linux
                Process process = Runtime.getRuntime().exec(
                    new String[]{"ps", "-p", String.valueOf(processId)});
                int exitCode = process.waitFor();
                return exitCode == 0;
            }
        } catch (Exception e) {
            // If we can't check, assume process is running (conservative approach)
            logger.debug("Could not verify if process {} is running: {}", processId, e.getMessage());
            return true;
        }
    }
    
    /**
     * Lock information holder
     */
    public static class LockInfo {
        private final long processId;
        private final long startTimestamp;
        private final int userId;
        
        public LockInfo(long processId, long startTimestamp, int userId) {
            this.processId = processId;
            this.startTimestamp = startTimestamp;
            this.userId = userId;
        }
        
        public long getProcessId() {
            return processId;
        }
        
        public long getStartTimestamp() {
            return startTimestamp;
        }
        
        public int getUserId() {
            return userId;
        }
    }
}

