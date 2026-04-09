package com.example.budg_v2.service;

import com.example.budg_v2.dao.PermissionsDAO;
import com.example.budg_v2.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service for checking user permissions based on role assignments
 * 
 * Permission Flow:
 * 1. User is assigned a role in Role Assignment (e.g., "Policy Steward" for "Policy" facet)
 * 2. Role has permissions defined in Role Permissions (e.g., "New", "Edit", "View")
 * 3. This service checks if user has required permission for a specific module action
 * 
 * Rules:
 * - Admin and Super Admin bypass all permission checks
 * - All users have "View" permission by default
 * - Regular users cannot delete (only Admin/Super Admin)
 * - Permissions are cumulative (if user has multiple roles, gets all their permissions)
 */
public class PermissionService {
    
    private static final Logger logger = LoggerFactory.getLogger(PermissionService.class);
    
    private final PermissionsDAO permissionsDAO;
    
    // Permission name constants
    public static final String PERMISSION_VIEW = "View";
    public static final String PERMISSION_NEW = "New";
    public static final String PERMISSION_EDIT = "Edit";
    public static final String PERMISSION_DELETE = "Delete";
    public static final String PERMISSION_READ = "Read";
    public static final String PERMISSION_WRITE = "Write";
    public static final String PERMISSION_EXECUTE = "Execute";
    public static final String PERMISSION_ADMIN = "Admin";
    
    public PermissionService() {
        this.permissionsDAO = new PermissionsDAO();
    }
    
    public PermissionService(PermissionsDAO permissionsDAO) {
        this.permissionsDAO = permissionsDAO;
    }
    
    /**
     * Check if user is Admin or Super Admin
     * @param userId User ID
     * @return true if user is Admin or Super Admin
     */
    public boolean isAdminOrSuperAdmin(int userId) {
        if (userId <= 0) {
            return false;
        }
        
        try {
            return SegmentAccessService.isSuperAdmin(userId) || isAdmin(userId);
        } catch (SQLException e) {
            logger.error("Error checking admin status for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if user is an Admin (not super admin, just admin)
     * @param userId User ID
     * @return true if user is Admin
     */
    private boolean isAdmin(int userId) throws SQLException {
        final String sql = """
            SELECT r.primaryname AS role_name
            FROM people p
            LEFT JOIN role r ON p.System_Role = r.id
            WHERE p.ID = ?
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String roleName = rs.getString("role_name");
                    if (roleName != null) {
                        String normalizedRole = roleName.toLowerCase().trim();
                        return "admin".equals(normalizedRole);
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Normalize module name to match database primaryname
     * Handles variations like "Data Sets" vs "Dataset", "Business Areas" vs "Business Area", etc.
     * 
     * @param moduleName Module name from frontend
     * @return Normalized module name that matches database
     */
    private String normalizeModuleName(String moduleName) {
        if (moduleName == null) {
            return null;
        }
        
        // Common mappings for module name variations
        String normalized = moduleName.trim();
        
        // Handle plural/singular variations
        switch (normalized) {
            case "Data Sets":
            case "Dataset":
            case "dataset":
                return "Data Sets";
            case "Business Areas":
            case "Business Area":
            case "business-area":
            case "businessarea":
                return "Business Area";
            case "Org Units":
            case "Org Unit":
            case "org-unit":
            case "orgunit":
                return "Org Unit";
            case "Regulatory Theme":
            case "Regulatory Themes":
            case "regulatory-theme":
            case "regulatorytheme":
                return "Regulatory Theme";
            case "Legal Entity":
            case "Legal Entities":
            case "legal-entity":
            case "legalentity":
            case "legal":
                return "Legal Entity";
            case "System Interface":
            case "Interface":
            case "system-interface":
            case "interface":
                return "Interface";
            case "System":
            case "system":
                return "System";
            case "Policy":
            case "policy":
                return "Policy";
            case "Process":
            case "process":
                return "Process";
            case "Glossary":
            case "glossary":
                return "Glossary";
            case "Capability":
            case "capability":
                return "Capability";
            case "Committee":
            case "committee":
                return "Committee";
            case "Project":
            case "project":
                return "Project";
            case "People":
            case "people":
            case "person":
                return "People";
            case "Regulation":
            case "regulation":
                return "Regulation";
            case "Regulator":
            case "regulator":
                return "Regulator";
            case "Geography":
            case "geography":
                return "Geography";
            case "Product":
            case "product":
                return "Product";
            case "Client":
            case "client":
                return "Client";
            case "Change Request":
            case "Change Requests":
            case "change-request":
            case "changerequest":
                return "Change Requests";
            default:
                // Return as-is, let database query handle it
                return normalized;
        }
    }
    
    /**
     * Check if user has a specific permission for a module
     * Admin and Super Admin always return true
     * 
     * @param userId User ID
     * @param moduleName Module name (e.g., "Policy", "System", "Data Sets")
     * @param permissionName Permission name (e.g., "New", "Edit", "View")
     * @return true if user has permission
     */
    public boolean hasPermission(int userId, String moduleName, String permissionName) {
        if (userId <= 0 || moduleName == null || permissionName == null) {
            return false;
        }
        
        // Admin and Super Admin bypass all checks
        if (isAdminOrSuperAdmin(userId)) {
            logger.debug("User {} is admin/super admin, bypassing permission check for {} on {}", 
                userId, permissionName, moduleName);
            return true;
        }
        
        // Normalize module name to match database
        String normalizedModuleName = normalizeModuleName(moduleName);
        
        try {
            return permissionsDAO.hasPermissionByRoleAssignment(userId, normalizedModuleName, permissionName);
        } catch (SQLException e) {
            logger.error("Error checking permission {} for user {} on module {} (normalized: {}): {}", 
                permissionName, userId, moduleName, normalizedModuleName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if user can create/add new items in a module
     * Permission name checked: "New" only
     * 
     * @param userId User ID
     * @param moduleName Module name
     * @return true if user can create
     */
    public boolean canCreate(int userId, String moduleName) {
        // Admin and Super Admin can always create
        if (isAdminOrSuperAdmin(userId)) {
            return true;
        }
        
        // Check for "New" permission only (as per database: id=10)
        return hasPermission(userId, moduleName, PERMISSION_NEW);
    }
    
    /**
     * Check if user can edit items in a module
     * Permission name checked: "Edit" only
     * 
     * @param userId User ID
     * @param moduleName Module name
     * @return true if user can edit
     */
    public boolean canEdit(int userId, String moduleName) {
        // Admin and Super Admin can always edit
        if (isAdminOrSuperAdmin(userId)) {
            return true;
        }
        
        // Check for "Edit" permission only (as per database: id=11)
        return hasPermission(userId, moduleName, PERMISSION_EDIT);
    }
    
    /**
     * Check if user can view items in a module
     * All users have View permission by default
     * 
     * @param userId User ID
     * @param moduleName Module name
     * @return true if user can view (always true for authenticated users)
     */
    public boolean canView(int userId, String moduleName) {
        // All authenticated users can view
        if (userId <= 0) {
            return false;
        }
        
        // View is default for all users
        return true;
    }
    
    /**
     * Check if user can delete items in a module
     * Only Super Admin can delete
     * Regular Admins and users cannot delete regardless of permissions
     * 
     * @param userId User ID
     * @param moduleName Module name
     * @return true if user can delete (only Super Admin)
     */
    public boolean canDelete(int userId, String moduleName) {
        // Only Super Admin can delete
        try {
            return com.example.budg_v2.service.SegmentAccessService.isSuperAdmin(userId);
        } catch (java.sql.SQLException e) {
            return false;
        }
    }
    
    /**
     * Get all permissions for a user by module
     * Returns a map of module name -> set of permission names
     * 
     * @param userId User ID
     * @return Map of module to permissions
     */
    public Map<String, Set<String>> getUserPermissions(int userId) {
        if (userId <= 0) {
            return new java.util.HashMap<>();
        }
        
        try {
            Map<String, Set<String>> permissions = permissionsDAO.getUserPermissionsByRoleAssignment(userId);
            
            // If user is admin, they have all permissions
            if (isAdminOrSuperAdmin(userId)) {
                // Add all modules with all permissions
                Set<String> allPermissions = new HashSet<>();
                allPermissions.add(PERMISSION_VIEW);
                allPermissions.add(PERMISSION_NEW);
                allPermissions.add(PERMISSION_EDIT);
                allPermissions.add(PERMISSION_DELETE);
                allPermissions.add(PERMISSION_READ);
                allPermissions.add(PERMISSION_WRITE);
                allPermissions.add(PERMISSION_EXECUTE);
                allPermissions.add(PERMISSION_ADMIN);
                
                // Update all existing modules with all permissions
                for (String module : permissions.keySet()) {
                    permissions.get(module).addAll(allPermissions);
                }
            } else {
                // Add View permission to all modules for regular users
                for (String module : permissions.keySet()) {
                    permissions.get(module).add(PERMISSION_VIEW);
                }
            }
            
            return permissions;
        } catch (SQLException e) {
            logger.error("Error getting permissions for user {}: {}", userId, e.getMessage());
            return new java.util.HashMap<>();
        }
    }
    
    /**
     * Get permissions for a specific module for a user
     * @param userId User ID
     * @param moduleName Module name
     * @return Set of permission names for the module
     */
    public Set<String> getModulePermissions(int userId, String moduleName) {
        // Normalize module name to match database
        String normalizedModuleName = normalizeModuleName(moduleName);
        
        Map<String, Set<String>> allPermissions = getUserPermissions(userId);
        Set<String> modulePermissions = allPermissions.getOrDefault(normalizedModuleName, new HashSet<>());
        
        // Always add View for authenticated users
        if (userId > 0) {
            modulePermissions.add(PERMISSION_VIEW);
        }
        
        // Admin/Super Admin get all permissions
        if (isAdminOrSuperAdmin(userId)) {
            modulePermissions.add(PERMISSION_NEW);
            modulePermissions.add(PERMISSION_EDIT);
            modulePermissions.add(PERMISSION_DELETE);
        }
        
        return modulePermissions;
    }
    
    /**
     * Check permissions and return a result object with details
     * Useful for returning permission info to frontend
     * 
     * @param userId User ID
     * @param moduleName Module name (will be normalized)
     * @return PermissionResult with all permission flags
     */
    public PermissionResult checkAllPermissions(int userId, String moduleName) {
        // Normalize module name to match database
        String normalizedModuleName = normalizeModuleName(moduleName);
        
        PermissionResult result = new PermissionResult();
        result.setUserId(userId);
        result.setModuleName(normalizedModuleName);
        result.setAdmin(isAdminOrSuperAdmin(userId));
        result.setCanView(canView(userId, normalizedModuleName));
        result.setCanCreate(canCreate(userId, normalizedModuleName));
        result.setCanEdit(canEdit(userId, normalizedModuleName));
        result.setCanDelete(canDelete(userId, normalizedModuleName));
        return result;
    }
    
    /**
     * Result class for permission checks
     */
    public static class PermissionResult {
        private int userId;
        private String moduleName;
        private boolean isAdmin;
        private boolean canView;
        private boolean canCreate;
        private boolean canEdit;
        private boolean canDelete;
        
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        public String getModuleName() { return moduleName; }
        public void setModuleName(String moduleName) { this.moduleName = moduleName; }
        public boolean isAdmin() { return isAdmin; }
        public void setAdmin(boolean admin) { isAdmin = admin; }
        public boolean isCanView() { return canView; }
        public void setCanView(boolean canView) { this.canView = canView; }
        public boolean isCanCreate() { return canCreate; }
        public void setCanCreate(boolean canCreate) { this.canCreate = canCreate; }
        public boolean isCanEdit() { return canEdit; }
        public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }
        public boolean isCanDelete() { return canDelete; }
        public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }
    }
}
