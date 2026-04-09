package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentAccessService;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class LockDAO {
    
    /**
     * Acquire a lock on an object
     * @param moduleId The module ID (e.g., 11 for Data Sets)
     * @param objectId The object ID to lock
     * @param userId The user ID acquiring the lock
     * @param isPermanent Whether the lock is permanent
     * @return The lock ID if successful, null if object is already locked
     * @throws SQLException if database error occurs
     */
    public Integer acquireLock(int moduleId, int objectId, int userId, boolean isPermanent) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if object is already locked
            Integer existingLockId = getLockId(conn, moduleId, objectId);
            if (existingLockId != null) {
                // Check if it's locked by the same user
                Integer lockedBy = getLockedBy(conn, existingLockId);
                boolean existingIsPermanent = getIsPermanent(conn, existingLockId);
                
                // If there's a permanent lock by another user, cannot override
                if (existingIsPermanent && (lockedBy == null || lockedBy != userId)) {
                    return null; // Permanent lock exists, cannot acquire
                }
                
                if (lockedBy != null && lockedBy == userId) {
                    // Same user, update the lock timestamp and permanence if changing
                    if (existingIsPermanent != isPermanent) {
                        // Update permanence status
                        String updateSql = "UPDATE object_lock SET Is_Permanent = ?, Updated_Datetime = NOW() WHERE ID = ?";
                        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                            ps.setBoolean(1, isPermanent);
                            ps.setInt(2, existingLockId);
                            ps.executeUpdate();
                        }
                    } else {
                        // Just update timestamp
                        updateLockTimestamp(conn, existingLockId);
                    }
                    return existingLockId;
                }
                // Different user, cannot acquire lock
                return null;
            }
            
            // Create new lock
            String sql = "INSERT INTO object_lock (Module_ID, Object_ID, Is_Permanent, Created_Datetime, Updated_Datetime, LockedBy_ID) " +
                        "VALUES (?, ?, ?, NOW(), NOW(), ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, moduleId);
                ps.setInt(2, objectId);
                ps.setBoolean(3, isPermanent);
                ps.setInt(4, userId);
                
                int rowsAffected = ps.executeUpdate();
                if (rowsAffected > 0) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Release a lock on an object
     * @param moduleId The module ID
     * @param objectId The object ID to unlock
     * @param userId The user ID releasing the lock (must be the lock owner or super admin)
     * @param isSuperAdmin Whether the user is a super admin
     * @return true if lock was released, false otherwise
     * @throws SQLException if database error occurs
     */
    public boolean releaseLock(int moduleId, int objectId, int userId, boolean isSuperAdmin) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer lockId = getLockId(conn, moduleId, objectId);
            System.out.println("🔓 LockDAO.releaseLock: lockId=" + lockId + " for moduleId=" + moduleId + " objectId=" + objectId);
            if (lockId == null) {
                System.out.println("🔓 LockDAO.releaseLock: No lock exists");
                return false; // No lock exists
            }
            
            // Get lock details
            boolean isPermanent = getIsPermanent(conn, lockId);
            Integer lockedBy = getLockedBy(conn, lockId);
            System.out.println("🔓 LockDAO.releaseLock: isPermanent=" + isPermanent + " lockedBy=" + lockedBy + " userId=" + userId);
            
            if (lockedBy == null) {
                System.out.println("🔓 LockDAO.releaseLock: lockedBy is null");
                return false;
            }
            
            // Lock owner can release their own lock (temporary or permanent)
            // Super admin can release any lock
            boolean isOwner = lockedBy.equals(userId);
            if (!isOwner && !isSuperAdmin) {
                System.out.println("🔓 LockDAO.releaseLock: Cannot release - not owner (lockedBy=" + lockedBy + " != userId=" + userId + ")");
                return false; // Not the lock owner and not super admin
            }
            
            // Delete the lock
            System.out.println("🔓 LockDAO.releaseLock: Deleting lock ID " + lockId);
            String sql = "DELETE FROM object_lock WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, lockId);
                int deleted = ps.executeUpdate();
                System.out.println("🔓 LockDAO.releaseLock: Deleted " + deleted + " row(s)");
                return deleted > 0;
            }
        }
    }
    
    /**
     * Release a TEMPORARY lock by module and object ID - for beacon/tab close only.
     * This method ONLY releases temporary locks and does not require user ID validation.
     * It's safe to use during page unload because:
     * 1. It only releases temporary locks (permanent locks are protected)
     * 2. If userId is provided and matches, it releases; if userId is 0 or doesn't match,
     *    it still releases if the lock is temporary (for tab close scenarios where context is lost)
     * 
     * @param moduleId The module ID
     * @param objectId The object ID
     * @param userId The user ID (can be 0 if unknown during tab close)
     * @return true if lock was released, false otherwise
     * @throws SQLException if database error occurs
     */
    public boolean releaseTemporaryLockForBeacon(int moduleId, int objectId, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer lockId = getLockId(conn, moduleId, objectId);
            System.out.println("🔓 LockDAO.releaseTemporaryLockForBeacon: lockId=" + lockId + " for moduleId=" + moduleId + " objectId=" + objectId);
            
            if (lockId == null) {
                System.out.println("🔓 LockDAO.releaseTemporaryLockForBeacon: No lock exists");
                return true; // No lock exists - consider it released
            }
            
            // Check if lock is permanent
            boolean isPermanent = getIsPermanent(conn, lockId);
            Integer lockedBy = getLockedBy(conn, lockId);
            System.out.println("🔓 LockDAO.releaseTemporaryLockForBeacon: isPermanent=" + isPermanent + " lockedBy=" + lockedBy + " userId=" + userId);
            
            // NEVER release permanent locks via beacon
            if (isPermanent) {
                System.out.println("🔓 LockDAO.releaseTemporaryLockForBeacon: Lock is permanent, NOT releasing");
                return false;
            }
            
            // For temporary locks during tab close, DELETE the lock row.
            // This is safe because:
            // 1. Permanent locks are already protected above
            // 2. Temporary locks are short-lived by design
            // 3. The lock was created by this same browser session
            // 4. If userId matches or is unknown (0), release it
            // 5. Even if userId doesn't match, release if it's the beacon call (browser is closing anyway)
            System.out.println("🔓 LockDAO.releaseTemporaryLockForBeacon: Deleting temporary lock ID " + lockId);
            String sql = "DELETE FROM `object_lock` WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, lockId);
                int deleted = ps.executeUpdate();
                System.out.println("🔓 LockDAO.releaseTemporaryLockForBeacon: Deleted " + deleted + " row(s)");
                return deleted > 0;
            }
        }
    }
    
    private boolean getIsPermanent(Connection conn, int lockId) throws SQLException {
        String sql = "SELECT Is_Permanent FROM object_lock WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("Is_Permanent");
                }
            }
        }
        return false;
    }
    
    /**
     * Release a lock by lock ID (for admin panel)
     * @param lockId The lock ID
     * @return true if lock was released, false otherwise
     * @throws SQLException if database error occurs
     */
    public boolean releaseLockById(int lockId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "DELETE FROM object_lock WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, lockId);
                return ps.executeUpdate() > 0;
            }
        }
    }
    
    /**
     * Release all locks owned by a specific user.
     * Only non-permanent locks are deleted. Permanent locks are skipped.
     * @param userId The user ID whose locks should be released
     * @return The number of locks that were released
     * @throws SQLException if database error occurs
     */
    public int releaseAllUserLocks(int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "DELETE FROM object_lock WHERE LockedBy_ID = ? AND Is_Permanent = 0";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                int deleted = ps.executeUpdate();
                System.out.println("🔓 LockDAO.releaseAllUserLocks: Deleted " + deleted + " non-permanent lock(s) for user " + userId);
                return deleted;
            }
        }
    }
    
    /**
     * Check if an object is locked
     * @param moduleId The module ID
     * @param objectId The object ID
     * @param currentUserId The current user ID (for determining lock state)
     * @return Lock information map with status: "no_lock", "locked_by_self", "locked_by_other", "permanently_locked"
     * @throws SQLException if database error occurs
     */
    public Map<String, Object> checkLock(int moduleId, int objectId, Integer currentUserId) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "no_lock");
        result.put("locked", false);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT ol.ID, ol.Is_Permanent, ol.Created_Datetime, ol.Updated_Datetime, ol.LockedBy_ID, " +
                        "CONCAT(p.First_Name, ' ', p.Last_Name) as LockedBy_Name " +
                        "FROM object_lock ol " +
                        "LEFT JOIN people p ON ol.LockedBy_ID = p.ID " +
                        "WHERE ol.Module_ID = ? AND ol.Object_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, moduleId);
                ps.setInt(2, objectId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int lockedById = rs.getInt("LockedBy_ID");
                        boolean isPermanent = rs.getBoolean("Is_Permanent");
                        
                        result.put("locked", true);
                        result.put("lockId", rs.getInt("ID"));
                        result.put("isPermanent", isPermanent);
                        result.put("createdDatetime", rs.getTimestamp("Created_Datetime"));
                        result.put("updatedDatetime", rs.getTimestamp("Updated_Datetime"));
                        result.put("lockedById", lockedById);
                        result.put("lockedByName", rs.getString("LockedBy_Name"));
                        
                        // Determine lock status
                        if (isPermanent) {
                            result.put("status", "permanently_locked");
                        } else if (currentUserId != null && lockedById == currentUserId) {
                            result.put("status", "locked_by_self");
                        } else {
                            result.put("status", "locked_by_other");
                        }
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * Check if an object is locked (backward compatibility - without user ID)
     * @param moduleId The module ID
     * @param objectId The object ID
     * @return Lock information map or null if not locked
     * @throws SQLException if database error occurs
     */
    public Map<String, Object> checkLock(int moduleId, int objectId) throws SQLException {
        return checkLock(moduleId, objectId, null);
    }
    
    /**
     * Get all locks (for admin panel)
     * @return List of lock information maps
     * @throws SQLException if database error occurs
     */
    public List<Map<String, Object>> getAllLocks() throws SQLException {
        List<Map<String, Object>> locks = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT ol.ID, ol.Module_ID, ol.Object_ID, ol.Is_Permanent, ol.Created_Datetime, " +
                        "ol.Updated_Datetime, ol.LockedBy_ID, " +
                        "CONCAT(p.First_Name, ' ', p.Last_Name) as LockedBy_Name, " +
                        "m.primaryname as Module_Name " +
                    "FROM object_lock ol " +
                    "LEFT JOIN people p ON ol.LockedBy_ID = p.ID " +
                    "LEFT JOIN module m ON ol.Module_ID = m.id " +
                    "WHERE ol.Is_Permanent = 1 " +  // ONLY PERMANENT LOCKS FOR ADMIN PANEL
                    "ORDER BY ol.Created_Datetime DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> lockInfo = new HashMap<>();
                    lockInfo.put("lockId", rs.getInt("ID"));
                    lockInfo.put("moduleId", rs.getInt("Module_ID"));
                    lockInfo.put("objectId", rs.getInt("Object_ID"));
                    lockInfo.put("isPermanent", rs.getBoolean("Is_Permanent"));
                    lockInfo.put("createdDatetime", rs.getTimestamp("Created_Datetime"));
                    lockInfo.put("updatedDatetime", rs.getTimestamp("Updated_Datetime"));
                    lockInfo.put("lockedById", rs.getInt("LockedBy_ID"));
                    lockInfo.put("lockedByName", rs.getString("LockedBy_Name"));
                    lockInfo.put("moduleName", rs.getString("Module_Name"));
                    
                    // Get object name based on module
                    String objectName = getObjectName(conn, rs.getInt("Module_ID"), rs.getInt("Object_ID"));
                    lockInfo.put("objectName", objectName);
                    
                    locks.add(lockInfo);
                }
            }
        }
        return locks;
    }
    
    /**
     * TC-014: Get all permanent locks scoped to the segments accessible to an Admin user.
     * SuperAdmin should use {@link #getAllLocks()} instead to see all locks.
     *
     * @param adminUserId the Admin user's ID used to filter by accessible segments
     * @return filtered list of permanent locks visible to this Admin
     * @throws SQLException if database error occurs
     */
    public List<Map<String, Object>> getAllLocksForAdmin(int adminUserId) throws SQLException {
        List<Map<String, Object>> allLocks = getAllLocks();
        List<Map<String, Object>> filtered = new ArrayList<>(allLocks.size());
        for (Map<String, Object> lock : allLocks) {
            String moduleName = (String) lock.get("moduleName");
            Object objectIdObj = lock.get("objectId");
            if (objectIdObj == null) {
                filtered.add(lock);
                continue;
            }
            int objectId = ((Number) objectIdObj).intValue();
            String segmentType = moduleNameToSegmentType(moduleName);
            if (segmentType == null) {
                // Unknown type — show it (fail-open)
                filtered.add(lock);
                continue;
            }
            try {
                if (SegmentAccessService.canAccessObject(adminUserId, objectId, segmentType)) {
                    filtered.add(lock);
                }
            } catch (Exception e) {
                filtered.add(lock); // fail-open on unexpected error
            }
        }
        return filtered;
    }

    private static String moduleNameToSegmentType(String moduleName) {
        if (moduleName == null) return null;
        return switch (moduleName.trim().toLowerCase()) {
            case "dataset" -> "Dataset";
            case "system" -> "System";
            case "glossary" -> "Glossary";
            case "process" -> "Process";
            case "project" -> "Project";
            case "product" -> "Product";
            case "policy" -> "Policy";
            case "legalentity", "legal entity", "legal_entity" -> "LegalEntity";
            case "businessarea", "business area", "business_area" -> "BusinessArea";
            case "capability" -> "Capability";
            case "client" -> "Client";
            case "committee" -> "Committee";
            case "geography" -> "Geography";
            case "regulation" -> "Regulation";
            case "regulator" -> "Regulator";
            case "regulatorytheme", "regulatory theme", "regulatory_theme" -> "RegulatoryTheme";
            case "interface", "systeminterface", "system interface" -> "SystemInterface";
            default -> null;
        };
    }

    /**
     * Get locks for a specific user
     * @param userId The user ID
     * @return List of lock information maps
     * @throws SQLException if database error occurs
     */
    public List<Map<String, Object>> getUserLocks(int userId) throws SQLException {
        List<Map<String, Object>> locks = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT ol.ID, ol.Module_ID, ol.Object_ID, ol.Is_Permanent, ol.Created_Datetime, " +
                        "ol.Updated_Datetime, ol.LockedBy_ID, " +
                        "CONCAT(p.First_Name, ' ', p.Last_Name) as LockedBy_Name, " +
                        "m.primaryname as Module_Name " +
                        "FROM object_lock ol " +
                        "LEFT JOIN people p ON ol.LockedBy_ID = p.ID " +
                        "LEFT JOIN module m ON ol.Module_ID = m.id " +
                        "WHERE ol.LockedBy_ID = ? " +
                        "ORDER BY ol.Created_Datetime DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> lockInfo = new HashMap<>();
                        lockInfo.put("lockId", rs.getInt("ID"));
                        lockInfo.put("moduleId", rs.getInt("Module_ID"));
                        lockInfo.put("objectId", rs.getInt("Object_ID"));
                        lockInfo.put("isPermanent", rs.getBoolean("Is_Permanent"));
                        lockInfo.put("createdDatetime", rs.getTimestamp("Created_Datetime"));
                        lockInfo.put("updatedDatetime", rs.getTimestamp("Updated_Datetime"));
                        lockInfo.put("lockedById", rs.getInt("LockedBy_ID"));
                        lockInfo.put("lockedByName", rs.getString("LockedBy_Name"));
                        lockInfo.put("moduleName", rs.getString("Module_Name"));
                        
                        // Get object name based on module
                        String objectName = getObjectName(conn, rs.getInt("Module_ID"), rs.getInt("Object_ID"));
                        lockInfo.put("objectName", objectName);
                        
                        locks.add(lockInfo);
                    }
                }
            }
        }
        return locks;
    }
    
    // Helper methods
    
    private Integer getLockId(Connection conn, int moduleId, int objectId) throws SQLException {
        String sql = "SELECT ID FROM object_lock WHERE Module_ID = ? AND Object_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            ps.setInt(2, objectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }
        return null;
    }
    
    private Integer getLockedBy(Connection conn, int lockId) throws SQLException {
        String sql = "SELECT LockedBy_ID FROM object_lock WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("LockedBy_ID");
                }
            }
        }
        return null;
    }
    
    private void updateLockTimestamp(Connection conn, int lockId) throws SQLException {
        String sql = "UPDATE object_lock SET Updated_Datetime = NOW() WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lockId);
            ps.executeUpdate();
        }
    }
    
    /**
     * Check if an object exists in the database
     * @param moduleId The module ID
     * @param objectId The object ID
     * @return true if object exists, false otherwise
     * @throws SQLException if database error occurs
     */
    public boolean objectExists(int moduleId, int objectId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get module name first
            String moduleName = null;
            String sql = "SELECT primaryname FROM module WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, moduleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        moduleName = rs.getString("primaryname");
                    }
                }
            }
            
            if (moduleName == null) {
                return false; // Module doesn't exist
            }
            
            // Get object existence check SQL based on module
            // Include check for deletion status (soft delete)
            String objectCheckSql = null;
            
            if (moduleName.equalsIgnoreCase("Data Sets")) {
                objectCheckSql = "SELECT 1 FROM dataset WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("System")) {
                objectCheckSql = "SELECT 1 FROM system WHERE id = ? AND (Deleted_datetime IS NULL OR Deleted_datetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Interface")) {
                objectCheckSql = "SELECT 1 FROM interface WHERE id = ? AND (deleted_datetime IS NULL OR deleted_datetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Glossary")) {
                objectCheckSql = "SELECT 1 FROM glossary WHERE ID = ? AND (Deleted_datetime IS NULL OR Deleted_datetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Process")) {
                objectCheckSql = "SELECT 1 FROM process WHERE ID = ? AND (deleteddatetime IS NULL OR deleteddatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Product")) {
                objectCheckSql = "SELECT 1 FROM product WHERE ID = ? AND (deleteddatetime IS NULL OR deleteddatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Project")) {
                objectCheckSql = "SELECT 1 FROM project WHERE ID = ? AND (deletedatetime IS NULL OR deletedatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Client")) {
                objectCheckSql = "SELECT 1 FROM client WHERE ID = ? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Policy")) {
                objectCheckSql = "SELECT 1 FROM policy WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Regulation")) {
                objectCheckSql = "SELECT 1 FROM regulation WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Regulator")) {
                objectCheckSql = "SELECT 1 FROM regulator WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Capability")) {
                objectCheckSql = "SELECT 1 FROM capability WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Business Area")) {
                objectCheckSql = "SELECT 1 FROM business_area WHERE ID = ? AND (deletedatetime IS NULL OR deletedatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Committee")) {
                objectCheckSql = "SELECT 1 FROM committee WHERE ID = ? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Geography")) {
                objectCheckSql = "SELECT 1 FROM geography WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("People")) {
                objectCheckSql = "SELECT 1 FROM people WHERE ID = ? AND (Deleted_date IS NULL OR Deleted_date = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Org Unit")) {
                objectCheckSql = "SELECT 1 FROM org_unit WHERE ID = ? AND (deleted_Date IS NULL OR deleted_Date = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Legal Entity")) {
                objectCheckSql = "SELECT 1 FROM legal WHERE ID = ? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')";
            } else if (moduleName.equalsIgnoreCase("Regulatory Theme")) {
                objectCheckSql = "SELECT 1 FROM regulatorytheme WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            }
            // Add more modules as needed
            
            if (objectCheckSql != null) {
                try (PreparedStatement ps = conn.prepareStatement(objectCheckSql)) {
                    ps.setInt(1, objectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next(); // Object exists if result set has at least one row
                    }
                }
            }
            
            // If we don't have a check SQL for this module, assume it doesn't exist
            return false;
        }
    }
    
    private String getObjectName(Connection conn, int moduleId, int objectId) throws SQLException {
        // Get module name first
        String moduleName = null;
        String sql = "SELECT primaryname FROM module WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    moduleName = rs.getString("primaryname");
                }
            }
        }
        
        if (moduleName == null) {
            return "Unknown Object";
        }
        
        // Get object name based on module
        String objectNameSql = null;
        String columnName = null;
        
        if (moduleName.equalsIgnoreCase("Data Sets")) {
            objectNameSql = "SELECT PrimaryName FROM dataset WHERE ID = ?";
            columnName = "PrimaryName";
        } else if (moduleName.equalsIgnoreCase("System")) {
            objectNameSql = "SELECT Name FROM system WHERE id = ?";
            columnName = "Name";
        } else if (moduleName.equalsIgnoreCase("Interface")) {
            objectNameSql = "SELECT Name FROM interface WHERE id = ?";
            columnName = "Name";
        } else if (moduleName.equalsIgnoreCase("Glossary")) {
            objectNameSql = "SELECT Name FROM glossary WHERE ID = ?";
            columnName = "Name";
        } else if (moduleName.equalsIgnoreCase("Legal Entity")) {
            objectNameSql = "SELECT LongName FROM legal WHERE ID = ?";
            columnName = "LongName";
        } else if (moduleName.equalsIgnoreCase("Regulatory Theme")) {
            objectNameSql = "SELECT PrimaryName FROM regulatorytheme WHERE ID = ?";
            columnName = "PrimaryName";
        } else if (moduleName.equalsIgnoreCase("People")) {
            objectNameSql = "SELECT CONCAT(First_Name, ' ', Last_Name) AS DisplayName FROM people WHERE ID = ?";
            columnName = "DisplayName";
        } else if (moduleName.equalsIgnoreCase("Committee")) {
            objectNameSql = "SELECT PrimaryName FROM committee WHERE ID = ?";
            columnName = "PrimaryName";
        } else if (moduleName.equalsIgnoreCase("Business Area")) {
            objectNameSql = "SELECT PrimaryName FROM business_area WHERE ID = ?";
            columnName = "PrimaryName";
        } else if (moduleName.equalsIgnoreCase("Capability")) {
            objectNameSql = "SELECT PrimaryName FROM capability WHERE ID = ?";
            columnName = "PrimaryName";
        } else if (moduleName.equalsIgnoreCase("Client")) {
            objectNameSql = "SELECT PrimaryName FROM client WHERE ID = ?";
            columnName = "PrimaryName";
        } else if (moduleName.equalsIgnoreCase("Policy")) {
            objectNameSql = "SELECT PrimaryName FROM policy WHERE ID = ?";
            columnName = "PrimaryName";
        } else if (moduleName.equalsIgnoreCase("Process")) {
            objectNameSql = "SELECT primaryname FROM process WHERE ID = ?";
            columnName = "primaryname";
        } else if (moduleName.equalsIgnoreCase("Product")) {
            objectNameSql = "SELECT primaryname FROM product WHERE ID = ?";
            columnName = "primaryname";
        } else if (moduleName.equalsIgnoreCase("Project")) {
            objectNameSql = "SELECT primaryname FROM project WHERE ID = ?";
            columnName = "primaryname";
        } else if (moduleName.equalsIgnoreCase("Geography")) {
            objectNameSql = "SELECT PrimaryName FROM geography WHERE ID = ?";
            columnName = "PrimaryName";
        } else if (moduleName.equalsIgnoreCase("Regulation")) {
            objectNameSql = "SELECT primaryName FROM regulation WHERE ID = ?";
            columnName = "primaryName";
        } else if (moduleName.equalsIgnoreCase("Regulator")) {
            objectNameSql = "SELECT PrimaryName FROM regulator WHERE ID = ?";
            columnName = "PrimaryName";
        } else if (moduleName.equalsIgnoreCase("Org Unit")) {
            objectNameSql = "SELECT Name FROM org_unit WHERE ID = ?";
            columnName = "Name";
        } else if (moduleName.equalsIgnoreCase("Attribute")) {
            objectNameSql = "SELECT PrimaryName FROM attribute WHERE ID = ?";
            columnName = "PrimaryName";
        }
        // Add more modules as needed
        
        if (objectNameSql != null) {
            try (PreparedStatement ps = conn.prepareStatement(objectNameSql)) {
                ps.setInt(1, objectId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString(columnName);
                        return name != null ? name : ("Object #" + objectId);
                    }
                }
            } catch (SQLException e) {
                // If query fails, return a default name
                System.err.println("Error fetching object name for module " + moduleName + ", object " + objectId + ": " + e.getMessage());
                return moduleName + " #" + objectId;
            }
        }
        
        return moduleName + " #" + objectId;
    }
}

