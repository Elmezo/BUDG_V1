package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PermissionsDAO {

    private static final Gson gson = new Gson();

    public static class PermissionView {
        private int id;
        private Integer moduleId;
        private Integer objectRoleId;
        private Integer permissionId;
        private Integer ipid;
        private Integer objectid;
        private String module;
        private String role;
        private String permission;
        private String permissionsJson; // New field for multiple permissions as JSON array
        private List<String> permissions; // Parsed list of permission names
        private List<Integer> permissionIds; // List of permission IDs
        private String createdatetime;
        private String lastupdatedatetime;
        private Integer lastupdateuser_id;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public Integer getModuleId() { return moduleId; }
        public void setModuleId(Integer moduleId) { this.moduleId = moduleId; }
        public Integer getObjectRoleId() { return objectRoleId; }
        public void setObjectRoleId(Integer objectRoleId) { this.objectRoleId = objectRoleId; }
        public Integer getPermissionId() { return permissionId; }
        public void setPermissionId(Integer permissionId) { this.permissionId = permissionId; }
        public Integer getIpid() { return ipid; }
        public void setIpid(Integer ipid) { this.ipid = ipid; }
        public Integer getObjectid() { return objectid; }
        public void setObjectid(Integer objectid) { this.objectid = objectid; }
        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getPermission() { return permission; }
        public void setPermission(String permission) { this.permission = permission; }
        public String getPermissionsJson() { return permissionsJson; }
        public void setPermissionsJson(String permissionsJson) { this.permissionsJson = permissionsJson; }
        public List<String> getPermissions() { return permissions; }
        public void setPermissions(List<String> permissions) { this.permissions = permissions; }
        public List<Integer> getPermissionIds() { return permissionIds; }
        public void setPermissionIds(List<Integer> permissionIds) { this.permissionIds = permissionIds; }
        public String getCreatedatetime() { return createdatetime; }
        public void setCreatedatetime(String createdatetime) { this.createdatetime = createdatetime; }
        public String getLastupdatedatetime() { return lastupdatedatetime; }
        public void setLastupdatedatetime(String lastupdatedatetime) { this.lastupdatedatetime = lastupdatedatetime; }
        public Integer getLastupdateuser_id() { return lastupdateuser_id; }
        public void setLastupdateuser_id(Integer lastupdateuser_id) { this.lastupdateuser_id = lastupdateuser_id; }
    }

    public List<PermissionView> getAllPermissions() throws SQLException {
        // Updated query to include permissions_json column
        final String sql =
            "SELECT p.id, p.moduleid AS moduleId, p.Object_Role_ID AS objectRoleId, p.permission AS permissionId, " +
            "p.permissions_json, p.ipid, p.objectid, p.createdatetime, p.lastupdatedatetime, p.lastupdateuser_id, " +
            "m.primaryname AS module, r.primaryname AS role, pn.Name AS permission " +
            "FROM permissions p " +
            "LEFT JOIN module m ON p.moduleid = m.id " +
            "LEFT JOIN object_role r ON p.Object_Role_ID = r.id " +
            "LEFT JOIN permission_names pn ON p.permission = pn.id " +
            "ORDER BY m.primaryname, r.primaryname, pn.Name";

        List<PermissionView> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PermissionView row = new PermissionView();
                row.setId(rs.getInt("id"));
                row.setModuleId((Integer) rs.getObject("moduleId"));
                row.setObjectRoleId((Integer) rs.getObject("objectRoleId"));
                row.setPermissionId((Integer) rs.getObject("permissionId"));
                row.setIpid((Integer) rs.getObject("ipid"));
                row.setObjectid((Integer) rs.getObject("objectid"));
                row.setModule(rs.getString("module"));
                row.setRole(rs.getString("role"));
                row.setPermission(rs.getString("permission"));
                
                // Handle permissions_json field
                String permissionsJson = rs.getString("permissions_json");
                row.setPermissionsJson(permissionsJson);
                if (permissionsJson != null && !permissionsJson.isEmpty()) {
                    List<Integer> permIds = parsePermissionIdsFromJson(permissionsJson);
                    row.setPermissionIds(permIds);
                    row.setPermissions(getPermissionNamesByIds(conn, permIds));
                } else if (row.getPermissionId() != null) {
                    // Fallback to single permission for backward compatibility
                    List<Integer> singlePermId = new ArrayList<>();
                    singlePermId.add(row.getPermissionId());
                    row.setPermissionIds(singlePermId);
                    List<String> singlePerm = new ArrayList<>();
                    if (row.getPermission() != null) {
                        singlePerm.add(row.getPermission());
                    }
                    row.setPermissions(singlePerm);
                }
                
                // Handle datetime fields
                java.sql.Timestamp created = rs.getTimestamp("createdatetime");
                if (created != null) {
                    row.setCreatedatetime(created.toString());
                }
                java.sql.Timestamp updated = rs.getTimestamp("lastupdatedatetime");
                if (updated != null) {
                    row.setLastupdatedatetime(updated.toString());
                }
                
                row.setLastupdateuser_id((Integer) rs.getObject("lastupdateuser_id"));
                list.add(row);
            }
        }
        return list;
    }
    
    /**
     * Parse permission IDs from JSON array string
     * @param jsonArray JSON array string like "[1, 2, 3]"
     * @return List of permission IDs
     */
    private List<Integer> parsePermissionIdsFromJson(String jsonArray) {
        if (jsonArray == null || jsonArray.isEmpty() || "[]".equals(jsonArray.trim())) {
            return new ArrayList<>();
        }
        try {
            return gson.fromJson(jsonArray, new TypeToken<List<Integer>>(){}.getType());
        } catch (Exception e) {
            // If parsing fails, return empty list
            return new ArrayList<>();
        }
    }
    
    /**
     * Convert list of permission IDs to JSON array string
     * @param permissionIds List of permission IDs
     * @return JSON array string like "[1, 2, 3]"
     */
    public String convertPermissionIdsToJson(List<Integer> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return "[]";
        }
        return gson.toJson(permissionIds);
    }
    
    /**
     * Get permission names by their IDs
     * @param conn Database connection
     * @param permissionIds List of permission IDs
     * @return List of permission names
     */
    private List<String> getPermissionNamesByIds(Connection conn, List<Integer> permissionIds) throws SQLException {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> names = new ArrayList<>();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < permissionIds.size(); i++) {
            if (i > 0) placeholders.append(", ");
            placeholders.append("?");
        }
        
        String sql = "SELECT id, Name FROM permission_names WHERE id IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < permissionIds.size(); i++) {
                ps.setInt(i + 1, permissionIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("Name"));
                }
            }
        }
        return names;
    }
    
    /**
     * Get permission names by their IDs (public version with new connection)
     * @param permissionIds List of permission IDs
     * @return List of permission names
     */
    public List<String> getPermissionNamesByIds(List<Integer> permissionIds) throws SQLException {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new ArrayList<>();
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            return getPermissionNamesByIds(conn, permissionIds);
        }
    }

    // Lookup helpers
    public Integer findModuleIdByPrimaryName(String name) throws SQLException {
        if (name == null) return null;
        // Use case-insensitive comparison to handle any case variations
        final String sql = "SELECT id FROM module WHERE LOWER(TRIM(primaryname)) = LOWER(TRIM(?)) LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null;
    }

    public Integer findRoleIdByPrimaryName(String name) throws SQLException {
        if (name == null) return null;
        final String sql = "SELECT id FROM object_role WHERE primaryname = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null;
    }

    public Integer findPermissionIdByName(String name) throws SQLException {
        if (name == null) return null;
        // Use case-insensitive comparison to handle any case variations
        final String sql = "SELECT id FROM permission_names WHERE LOWER(TRIM(Name)) = LOWER(TRIM(?)) LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null;
    }

    public Integer findObjectIdByRoleId(Integer roleId) throws SQLException {
        if (roleId == null) return null;
        final String sql = "SELECT objectroletype_id FROM object_role WHERE id = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Integer objectId = rs.getObject("objectroletype_id", Integer.class);
                    return objectId;
                }
            }
        }
        return null;
    }

    // Get current user ID (placeholder - should be implemented based on your authentication system)
    public Integer getCurrentUserId() {
        // TODO: Implement proper user authentication
        // For now, return a default user ID or get from session/context
        return 0; // Default user ID
    }

    // Mutations
    public int insertPermission(Integer moduleId, Integer objectRoleId, Integer permissionId) throws SQLException {
        return insertPermission(moduleId, objectRoleId, permissionId, getCurrentUserId());
    }
    
    public int insertPermission(Integer moduleId, Integer objectRoleId, Integer permissionId, Integer userId) throws SQLException {
        // Convert single permission to list for new method
        List<Integer> permissionIds = new ArrayList<>();
        if (permissionId != null) {
            permissionIds.add(permissionId);
        }
        return insertPermissionWithMultiple(moduleId, objectRoleId, permissionIds, userId);
    }
    
    /**
     * Insert permission with multiple permission IDs
     * @param moduleId Module ID
     * @param objectRoleId Object Role ID
     * @param permissionIds List of permission IDs
     * @param userId Current user ID
     * @return The new permission record ID
     */
    public int insertPermissionWithMultiple(Integer moduleId, Integer objectRoleId, List<Integer> permissionIds, Integer userId) throws SQLException {
        if (moduleId == null || objectRoleId == null) {
            throw new SQLException("Missing required ID(s) for insert: moduleId/objectRoleId");
        }
        if (permissionIds == null || permissionIds.isEmpty()) {
            throw new SQLException("At least one permission is required");
        }

        Integer currentUserId = userId != null ? userId : getCurrentUserId();
        
        // Get objectid from object_role table
        Integer objectId = findObjectIdByRoleId(objectRoleId);
        
        // Convert permission IDs to JSON array
        String permissionsJson = convertPermissionIdsToJson(permissionIds);
        
        // Use first permission ID for backward compatibility with single permission column
        Integer firstPermissionId = permissionIds.get(0);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get next id manually to avoid AUTO_INCREMENT dependency
            int nextId = 0;
            try (PreparedStatement psNext = conn.prepareStatement("SELECT COALESCE(MAX(id),0) + 1 AS nextId FROM permissions");
                 ResultSet rs = psNext.executeQuery()) {
                if (rs.next()) {
                    nextId = rs.getInt(1);
                }
            }
            if (nextId <= 0) nextId = 1;

            final String sql = "INSERT INTO permissions (id, moduleid, Object_Role_ID, permission, permissions_json, ipid, objectid, createdatetime, lastupdatedatetime, lastupdateuser_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, nextId);
                ps.setInt(2, moduleId);
                ps.setInt(3, objectRoleId);
                ps.setInt(4, firstPermissionId); // Keep first for backward compatibility
                ps.setString(5, permissionsJson); // Store all permissions as JSON
                ps.setInt(6, currentUserId); // ipid - current user who created
                if (objectId != null) {
                    ps.setInt(7, objectId); // objectid from object_role.objectroletype_id
                } else {
                    ps.setNull(7, java.sql.Types.INTEGER); // objectid
                }
                ps.setTimestamp(8, new java.sql.Timestamp(System.currentTimeMillis())); // createdatetime
                ps.setTimestamp(9, new java.sql.Timestamp(System.currentTimeMillis())); // lastupdatedatetime
                ps.setInt(10, currentUserId); // lastupdateuser_id - current user who created
                ps.executeUpdate();
                return nextId;
            }
        }
    }

    public int updatePermission(int id, Integer moduleId, Integer objectRoleId, Integer permissionId) throws SQLException {
        return updatePermission(id, moduleId, objectRoleId, permissionId, getCurrentUserId());
    }
    
    public int updatePermission(int id, Integer moduleId, Integer objectRoleId, Integer permissionId, Integer userId) throws SQLException {
        // Convert single permission to list for new method
        List<Integer> permissionIds = new ArrayList<>();
        if (permissionId != null) {
            permissionIds.add(permissionId);
        }
        return updatePermissionWithMultiple(id, moduleId, objectRoleId, permissionIds, userId);
    }
    
    /**
     * Update permission with multiple permission IDs
     * @param id Permission record ID
     * @param moduleId Module ID
     * @param objectRoleId Object Role ID
     * @param permissionIds List of permission IDs
     * @param userId Current user ID
     * @return Number of rows affected
     */
    public int updatePermissionWithMultiple(int id, Integer moduleId, Integer objectRoleId, List<Integer> permissionIds, Integer userId) throws SQLException {
        Integer currentUserId = userId != null ? userId : getCurrentUserId();
        
        // Get objectid from object_role table if objectRoleId is provided
        Integer objectId = null;
        if (objectRoleId != null) {
            objectId = findObjectIdByRoleId(objectRoleId);
        }
        
        // Convert permission IDs to JSON array
        String permissionsJson = convertPermissionIdsToJson(permissionIds);
        
        // Use first permission ID for backward compatibility (or null if empty)
        Integer firstPermissionId = (permissionIds != null && !permissionIds.isEmpty()) ? permissionIds.get(0) : null;
        
        final String sql = "UPDATE permissions SET moduleid = ?, Object_Role_ID = ?, permission = ?, permissions_json = ?, objectid = ?, lastupdatedatetime = ?, lastupdateuser_id = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (moduleId == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setInt(1, moduleId);
            if (objectRoleId == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, objectRoleId);
            if (firstPermissionId == null) ps.setNull(3, java.sql.Types.INTEGER); else ps.setInt(3, firstPermissionId);
            ps.setString(4, permissionsJson);
            if (objectId != null) {
                ps.setInt(5, objectId); // objectid from object_role.objectroletype_id
            } else {
                ps.setNull(5, java.sql.Types.INTEGER); // objectid
            }
            ps.setTimestamp(6, new java.sql.Timestamp(System.currentTimeMillis())); // lastupdatedatetime
            ps.setInt(7, currentUserId); // lastupdateuser_id - current user who updated
            ps.setInt(8, id);
            return ps.executeUpdate();
        }
    }

    public int deletePermission(int id) throws SQLException {
        final String sql = "DELETE FROM permissions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }
    
    public PermissionView getPermissionById(int id) throws SQLException {
        final String sql =
            "SELECT p.id, p.moduleid AS moduleId, p.Object_Role_ID AS objectRoleId, p.permission AS permissionId, " +
            "p.ipid, p.objectid, p.createdatetime, p.lastupdatedatetime, p.lastupdateuser_id, " +
            "m.primaryname AS module, r.primaryname AS role, pn.Name AS permission " +
            "FROM permissions p " +
            "LEFT JOIN module m ON p.moduleid = m.id " +
            "LEFT JOIN object_role r ON p.Object_Role_ID = r.id " +
            "LEFT JOIN permission_names pn ON p.permission = pn.id " +
            "WHERE p.id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PermissionView pv = new PermissionView();
                    pv.setId(rs.getInt("id"));
                    pv.setModuleId(rs.getObject("moduleId", Integer.class));
                    pv.setObjectRoleId(rs.getObject("objectRoleId", Integer.class));
                    pv.setPermissionId(rs.getObject("permissionId", Integer.class));
                    pv.setIpid(rs.getObject("ipid", Integer.class));
                    pv.setObjectid(rs.getObject("objectid", Integer.class));
                    pv.setModule(rs.getString("module"));
                    pv.setRole(rs.getString("role"));
                    pv.setPermission(rs.getString("permission"));
                    pv.setCreatedatetime(rs.getString("createdatetime"));
                    pv.setLastupdatedatetime(rs.getString("lastupdatedatetime"));
                    pv.setLastupdateuser_id(rs.getObject("lastupdateuser_id", Integer.class));
                    return pv;
                }
            }
        }
        return null;
    }

    public List<Map<String, Object>> getAllPermissionNames() throws SQLException {
        final String sql = "SELECT id, Name FROM permission_names ORDER BY Name";
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("name", rs.getString("Name"));
                result.add(row);
            }
        }
        return result;
    }

    /**
     * Check if user has permission for a specific module and permission name
     * @param userId User ID
     * @param moduleName Module name (e.g., "System", "Regulation")
     * @param permissionName Permission name (e.g., "Create", "Update", "Delete")
     * @return true if user has permission, false otherwise
     */
    public boolean hasPermission(int userId, String moduleName, String permissionName) throws SQLException {
        if (moduleName == null || permissionName == null) {
            return false;
        }
        
        final String sql = """
            SELECT COUNT(*) as count
            FROM permissions p
            JOIN module m ON p.moduleid = m.id
            JOIN permission_names pn ON p.permission = pn.id
            WHERE p.ipid = ? 
            AND m.primaryname = ?
            AND pn.Name = ?
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, moduleName);
            ps.setString(3, permissionName);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        }
        return false;
    }

    /**
     * Get all permissions for a user grouped by module
     * @param userId User ID
     * @return Map of module name to list of permission names
     */
    public Map<String, List<String>> getUserPermissions(int userId) throws SQLException {
        Map<String, List<String>> permissions = new HashMap<>();
        
        final String sql = """
            SELECT DISTINCT m.primaryname AS module, pn.Name AS permission
            FROM permissions p
            JOIN module m ON p.moduleid = m.id
            JOIN permission_names pn ON p.permission = pn.id
            WHERE p.ipid = ?
            ORDER BY m.primaryname, pn.Name
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String module = rs.getString("module");
                    String permission = rs.getString("permission");
                    
                    permissions.computeIfAbsent(module, k -> new ArrayList<>()).add(permission);
                }
            }
        }
        
        return permissions;
    }

    /**
     * Check if user can perform bulk upload for a specific entity
     * This checks for "New" permission for the entity module
     * @param userId User ID
     * @param entityName Entity name (e.g., "System", "Regulation", "Regulator")
     * @return true if user can upload, false otherwise
     */
    public boolean canBulkUpload(int userId, String entityName) throws SQLException {
        // Check for "New" permission only (as per database: id=10)
        return hasPermissionByRoleAssignment(userId, entityName, "New");
    }
    
    /**
     * Check if user has permission based on their role assignments
     * This is the NEW permission check that works with role_assignment table
     * 
     * Flow:
     * 1. Get all roles assigned to the user from role_assignment table
     * 2. For each role, check the permissions table for the given module and permission
     * 3. Check permissions_json column for multiple permissions support
     * 
     * @param userId User ID
     * @param moduleName Module name (e.g., "Policy", "System")
     * @param permissionName Permission name (e.g., "New", "Edit", "View")
     * @return true if user has permission, false otherwise
     */
    public boolean hasPermissionByRoleAssignment(int userId, String moduleName, String permissionName) throws SQLException {
        if (moduleName == null || permissionName == null) {
            return false;
        }
        
        // Get permission ID by name
        Integer permissionId = findPermissionIdByName(permissionName);
        if (permissionId == null) {
            return false;
        }
        
        // Get module ID by name
        Integer moduleId = findModuleIdByPrimaryName(moduleName);
        if (moduleId == null) {
            return false;
        }
        
        // Query to check if user is assigned to any role that has the required permission
        // Step 1: Find roles where user is in the users JSON array in role_assignment
        // Step 2: Check permissions table for those roles with the module and permission
        final String sql = """
            SELECT COUNT(*) as count
            FROM role_assignment ra
            JOIN permissions p ON p.Object_Role_ID = ra.objectroleid AND p.moduleid = ?
            WHERE (
                -- Check if user ID is in the users JSON array
                JSON_CONTAINS(ra.users, CAST(? AS CHAR), '$')
                OR JSON_CONTAINS(ra.users, CONCAT('"', ?, '"'), '$')
            )
            AND (
                -- Check single permission column
                p.permission = ?
                OR 
                -- Check permissions_json array
                (p.permissions_json IS NOT NULL AND JSON_CONTAINS(p.permissions_json, CAST(? AS CHAR), '$'))
            )
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            ps.setInt(2, userId);
            ps.setString(3, String.valueOf(userId));
            ps.setInt(4, permissionId);
            ps.setInt(5, permissionId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        }
        return false;
    }
    
    /**
     * Get all permissions for a user based on their role assignments
     * Returns a map of module name -> list of permission names
     * 
     * @param userId User ID
     * @return Map of module name to list of permission names
     */
    public Map<String, Set<String>> getUserPermissionsByRoleAssignment(int userId) throws SQLException {
        Map<String, Set<String>> permissions = new HashMap<>();
        
        // Query to get all permissions for user based on role assignments
        final String sql = """
            SELECT DISTINCT m.primaryname AS module, p.permissions_json, p.permission AS single_perm_id
            FROM role_assignment ra
            JOIN permissions p ON p.Object_Role_ID = ra.objectroleid
            JOIN module m ON p.moduleid = m.id
            WHERE (
                JSON_CONTAINS(ra.users, CAST(? AS CHAR), '$')
                OR JSON_CONTAINS(ra.users, CONCAT('"', ?, '"'), '$')
            )
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, String.valueOf(userId));
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String module = rs.getString("module");
                    String permissionsJson = rs.getString("permissions_json");
                    Integer singlePermId = rs.getObject("single_perm_id", Integer.class);
                    
                    Set<String> modulePermissions = permissions.computeIfAbsent(module, k -> new HashSet<>());
                    
                    // Parse permissions_json if available
                    if (permissionsJson != null && !permissionsJson.isEmpty()) {
                        List<Integer> permIds = parsePermissionIdsFromJson(permissionsJson);
                        List<String> permNames = getPermissionNamesByIds(conn, permIds);
                        modulePermissions.addAll(permNames);
                    } else if (singlePermId != null) {
                        // Fallback to single permission
                        List<Integer> singleId = new ArrayList<>();
                        singleId.add(singlePermId);
                        List<String> permNames = getPermissionNamesByIds(conn, singleId);
                        modulePermissions.addAll(permNames);
                    }
                }
            }
        }
        
        return permissions;
    }
    
    /**
     * Get all roles assigned to a user from role_assignment table
     * @param userId User ID
     * @return List of object_role IDs
     */
    public List<Integer> getUserAssignedRoles(int userId) throws SQLException {
        List<Integer> roleIds = new ArrayList<>();
        
        final String sql = """
            SELECT ra.objectroleid
            FROM role_assignment ra
            WHERE (
                JSON_CONTAINS(ra.users, CAST(? AS CHAR), '$')
                OR JSON_CONTAINS(ra.users, CONCAT('"', ?, '"'), '$')
            )
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, String.valueOf(userId));
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roleIds.add(rs.getInt("objectroleid"));
                }
            }
        }
        
        return roleIds;
    }
    
    /**
     * Find permission IDs by their names
     * @param permissionNames List of permission names
     * @return List of permission IDs
     */
    public List<Integer> findPermissionIdsByNames(List<String> permissionNames) throws SQLException {
        if (permissionNames == null || permissionNames.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Integer> ids = new ArrayList<>();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < permissionNames.size(); i++) {
            if (i > 0) placeholders.append(", ");
            placeholders.append("?");
        }
        
        String sql = "SELECT id FROM permission_names WHERE Name IN (" + placeholders + ")";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < permissionNames.size(); i++) {
                ps.setString(i + 1, permissionNames.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                }
            }
        }
        return ids;
    }
}


