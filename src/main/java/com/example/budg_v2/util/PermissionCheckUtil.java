package com.example.budg_v2.util;

import com.example.budg_v2.service.PermissionService;
import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for checking permissions in Servlets
 * Provides common permission check methods to reduce code duplication
 * 
 * Rules:
 * - Admin and Super Admin bypass all permission checks (except delete)
 * - All users have View permission by default
 * - Only Super Admin can delete (regular Admins cannot delete)
 * - Create and Edit permissions are based on role assignments
 */
public class PermissionCheckUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(PermissionCheckUtil.class);
    private static final PermissionService permissionService = new PermissionService();
    
    /**
     * Check if user can create objects in the specified module
     * Returns true if user has permission, sends 403 response and returns false otherwise
     * 
     * @param request HTTP request
     * @param response HTTP response
     * @param moduleName Module name (e.g., "Policy", "System")
     * @return true if user has permission, false if denied (response already sent)
     */
    public static boolean checkCreatePermission(HttpServletRequest request, HttpServletResponse response, 
                                                 String moduleName) throws IOException {
        int userId = UserContextUtil.getCurrentUserId(request);
        
        // Admin and Super Admin bypass permission checks
        if (UserContextUtil.isCurrentUserAdmin(request)) {
            logger.debug("User {} is admin, bypassing create permission check for {}", userId, moduleName);
            return true;
        }
        
        if (userId <= 0) {
            sendForbiddenResponse(response, "Authentication required to create " + moduleName);
            return false;
        }
        
        if (!permissionService.canCreate(userId, moduleName)) {
            logger.warn("User {} denied create permission for {}", userId, moduleName);
            sendForbiddenResponse(response, "You don't have permission to create new " + moduleName + " records.");
            return false;
        }
        
        logger.debug("User {} granted create permission for {}", userId, moduleName);
        return true;
    }
    
    /**
     * Check if user can edit objects in the specified module
     * Returns true if user has permission, sends 403 response and returns false otherwise
     * 
     * @param request HTTP request
     * @param response HTTP response
     * @param moduleName Module name (e.g., "Policy", "System")
     * @return true if user has permission, false if denied (response already sent)
     */
    public static boolean checkEditPermission(HttpServletRequest request, HttpServletResponse response, 
                                               String moduleName) throws IOException {
        int userId = UserContextUtil.getCurrentUserId(request);
        
        // Admin and Super Admin bypass permission checks
        if (UserContextUtil.isCurrentUserAdmin(request)) {
            logger.debug("User {} is admin, bypassing edit permission check for {}", userId, moduleName);
            return true;
        }
        
        if (userId <= 0) {
            sendForbiddenResponse(response, "Authentication required to edit " + moduleName);
            return false;
        }
        
        if (!permissionService.canEdit(userId, moduleName)) {
            logger.warn("User {} denied edit permission for {}", userId, moduleName);
            sendForbiddenResponse(response, "You don't have permission to edit " + moduleName + " records.");
            return false;
        }
        
        logger.debug("User {} granted edit permission for {}", userId, moduleName);
        return true;
    }
    
    /**
     * Check if user can delete objects in the specified module
     * Only Super Admin can delete - regular Admins and users cannot delete
     * 
     * @param request HTTP request
     * @param response HTTP response
     * @param moduleName Module name (e.g., "Policy", "System")
     * @return true if user has permission, false if denied (response already sent)
     */
    public static boolean checkDeletePermission(HttpServletRequest request, HttpServletResponse response, 
                                                 String moduleName) throws IOException {
        int userId = UserContextUtil.getCurrentUserId(request);
        
        // Only Super Admin can delete
        if (!UserContextUtil.isCurrentUserSuperAdmin(request)) {
            logger.warn("User {} denied delete permission for {} - only Super Admins can delete", userId, moduleName);
            sendForbiddenResponse(response, "Only Super Administrators can delete " + moduleName + " records.");
            return false;
        }
        
        logger.debug("User {} (Super Admin) granted delete permission for {}", userId, moduleName);
        return true;
    }
    
    /**
     * Check if user can view objects in the specified module
     * All authenticated users have view permission by default
     * 
     * @param request HTTP request
     * @param response HTTP response
     * @param moduleName Module name (e.g., "Policy", "System")
     * @return true if user has permission, false if denied (response already sent)
     */
    public static boolean checkViewPermission(HttpServletRequest request, HttpServletResponse response, 
                                               String moduleName) throws IOException {
        int userId = UserContextUtil.getCurrentUserId(request);
        
        // All authenticated users can view
        if (userId <= 0) {
            sendForbiddenResponse(response, "Authentication required to view " + moduleName);
            return false;
        }
        
        // View is always allowed for authenticated users
        return true;
    }
    
    /**
     * Get the permission result for a user and module
     * Useful for returning permission info to frontend
     * 
     * @param request HTTP request
     * @param moduleName Module name
     * @return PermissionResult with all permission flags
     */
    public static PermissionService.PermissionResult getPermissions(HttpServletRequest request, String moduleName) {
        int userId = UserContextUtil.getCurrentUserId(request);
        return permissionService.checkAllPermissions(userId, moduleName);
    }
    
    /**
     * Add permission info to a JSON response
     * Adds canCreate, canEdit, canDelete flags to the response
     * 
     * @param request HTTP request
     * @param json JSON object to add permissions to
     * @param moduleName Module name
     */
    public static void addPermissionInfo(HttpServletRequest request, JsonObject json, String moduleName) {
        int userId = UserContextUtil.getCurrentUserId(request);
        boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
        
        json.addProperty("canView", true); // All authenticated users can view
        json.addProperty("canCreate", isAdmin || permissionService.canCreate(userId, moduleName));
        json.addProperty("canEdit", isAdmin || permissionService.canEdit(userId, moduleName));
        json.addProperty("canDelete", UserContextUtil.isCurrentUserSuperAdmin(request)); // Only Super Admins can delete
        json.addProperty("isAdmin", isAdmin);
    }
    
    /**
     * Check if user can edit a specific object in the specified module
     * Requires: role permission + stakeholder status on the object
     * Returns true if user has permission, sends 403 response and returns false otherwise
     * 
     * @param request HTTP request
     * @param response HTTP response
     * @param moduleName Module name (e.g., "Policy", "System", "Glossary")
     * @param objectId The specific object ID to check
     * @return true if user has permission, false if denied (response already sent)
     */
    public static boolean checkEditPermissionWithStakeholder(HttpServletRequest request, 
                                                             HttpServletResponse response,
                                                             String moduleName, 
                                                             int objectId) throws IOException {
        int userId = UserContextUtil.getCurrentUserId(request);
        
        // Admin and Super Admin bypass permission checks
        if (UserContextUtil.isCurrentUserAdmin(request)) {
            logger.debug("User {} is admin, bypassing edit permission check for {} object {}", 
                userId, moduleName, objectId);
            return true;
        }
        
        if (userId <= 0) {
            sendForbiddenResponse(response, "Authentication required to edit " + moduleName);
            return false;
        }
        
        // Step 1: Check role-based permission
        if (!permissionService.canEdit(userId, moduleName)) {
            logger.warn("User {} denied edit permission for {} (no role permission)", userId, moduleName);
            sendForbiddenResponse(response, "You don't have permission to edit " + moduleName + " records.");
            return false;
        }
        
        // Step 2: Check if user is stakeholder on this specific object
        // Normalize module name to facet type
        String facetType = normalizeModuleNameToFacetType(moduleName);
        if (facetType == null) {
            logger.warn("Could not normalize module name {} to facet type", moduleName);
            sendForbiddenResponse(response, "Invalid module name: " + moduleName);
            return false;
        }
        
        if (!isUserStakeholder(userId, objectId, facetType)) {
            logger.warn("User {} denied edit permission for {} object {} (not a stakeholder)", 
                userId, moduleName, objectId);
            sendForbiddenResponse(response, 
                "You don't have permission to edit this " + moduleName + " record. " +
                "You must be a stakeholder on this object to edit it.");
            return false;
        }
        
        logger.debug("User {} granted edit permission for {} object {} (has role permission + is stakeholder)", 
            userId, moduleName, objectId);
        return true;
    }
    
    /**
     * Check if user is a stakeholder on a specific object in a facet
     * @param userId The user ID
     * @param objectId The object ID
     * @param facetType The facet type (e.g., "glossary", "system", "dataset")
     * @return true if user is a stakeholder, false otherwise
     */
    public static boolean isUserStakeholder(int userId, int objectId, String facetType) {
        try {
            // Normalize facet type
            String normalizedFacetType = facetType.toLowerCase()
                .replace(" ", "-")
                .replace("_", "-");
            
            // Get table mappings (same as CRStakeholderDAO)
            Map<String, String> facetStakeholderTables = new HashMap<>();
            Map<String, String> facetIdColumns = new HashMap<>();
            Map<String, String> facetObjectXIpidColumns = new HashMap<>();
            
            // Initialize mappings
            facetStakeholderTables.put("business-area", "businessarea_x_objectxpeople");
            facetStakeholderTables.put("businessarea", "businessarea_x_objectxpeople");
            facetStakeholderTables.put("dataset", "dataset_x_objectxpeople");
            facetStakeholderTables.put("data-set", "dataset_x_objectxpeople");
            facetStakeholderTables.put("system", "system_x_objectxpeople");
            facetStakeholderTables.put("capability", "capability_x_objectxpeople");
            facetStakeholderTables.put("client", "client_x_objectxpeople");
            facetStakeholderTables.put("product", "product_x_objectxpeople");
            facetStakeholderTables.put("system-interface", "interface_x_objectxpeople");
            facetStakeholderTables.put("interface", "interface_x_objectxpeople");
            facetStakeholderTables.put("policy", "policy_x_objectxpeople");
            facetStakeholderTables.put("committee", "committee_x_objectxpeople");
            facetStakeholderTables.put("process", "process_x_objectxpeople");
            facetStakeholderTables.put("glossary", "glossary_x_objectxpeople");
            facetStakeholderTables.put("geography", "geography_x_objectxpeople");
            // Legal entity stakeholders are stored in legal_x_objectxpeople
            facetStakeholderTables.put("legal-entity", "legal_x_objectxpeople");
            facetStakeholderTables.put("legalentity", "legal_x_objectxpeople");
            facetStakeholderTables.put("org-unit", "orgunit_x_objectxpeople");
            facetStakeholderTables.put("orgunit", "orgunit_x_objectxpeople");
            facetStakeholderTables.put("project", "project_x_objectxpeople");
            facetStakeholderTables.put("regulation", "regulation_x_objectxpeople");
            facetStakeholderTables.put("regulator", "regulator_x_objectxpeople");
            facetStakeholderTables.put("regulatory-theme", "regulatorytheme_x_objectxpeople");
            facetStakeholderTables.put("regulatorytheme", "regulatorytheme_x_objectxpeople");
            
            facetIdColumns.put("business-area", "BusinessAreaID");
            facetIdColumns.put("businessarea", "BusinessAreaID");
            facetIdColumns.put("dataset", "Dataset_ID");
            facetIdColumns.put("data-set", "Dataset_ID");
            facetIdColumns.put("system", "SystemID");
            facetIdColumns.put("capability", "CapabilityID");
            facetIdColumns.put("client", "ClientID");
            facetIdColumns.put("product", "product_id");
            facetIdColumns.put("system-interface", "InterfaceID");
            facetIdColumns.put("interface", "InterfaceID");
            facetIdColumns.put("policy", "Policy_ID");
            facetIdColumns.put("committee", "Committee_ID");
            facetIdColumns.put("process", "process_id");
            facetIdColumns.put("glossary", "GlossaryID");
            facetIdColumns.put("geography", "GeographyID");
            facetIdColumns.put("legal-entity", "Legal_ID");
            facetIdColumns.put("legalentity", "Legal_ID");
            facetIdColumns.put("org-unit", "OrgUnitID");
            facetIdColumns.put("orgunit", "OrgUnitID");
            // Project stakeholders use snake_case in this schema
            facetIdColumns.put("project", "project_id");
            facetIdColumns.put("regulation", "RegulationID");
            facetIdColumns.put("regulator", "RegulatorID");
            facetIdColumns.put("regulatory-theme", "RegulatoryThemeID");
            facetIdColumns.put("regulatorytheme", "RegulatoryThemeID");
            
            facetObjectXIpidColumns.put("committee", "Object_X_ipid");
            facetObjectXIpidColumns.put("policy", "Object_X_IP");
            facetObjectXIpidColumns.put("process", "object_x_ip");
            facetObjectXIpidColumns.put("product", "object_x_ip");
            facetObjectXIpidColumns.put("project", "object_x_ip");
            facetObjectXIpidColumns.put("legal-entity", "Object_X_IP");
            facetObjectXIpidColumns.put("legalentity", "Object_X_IP");
            
            String tableName = facetStakeholderTables.get(normalizedFacetType);
            String idColumn = facetIdColumns.get(normalizedFacetType);
            String objectXIpidColumn = facetObjectXIpidColumns.getOrDefault(normalizedFacetType, "Object_x_ipid");
            
            if (tableName == null || idColumn == null) {
                logger.warn("Unknown facet type for stakeholder check: {}", facetType);
                return false;
            }
            
            // Query to check if user is stakeholder
            String sql = String.format(
                "SELECT COUNT(*) as count " +
                "FROM %s jt " +
                "JOIN object_x_people oxp ON jt.%s = oxp.id " +
                "WHERE jt.%s = ? AND oxp.ipid = ?",
                tableName, objectXIpidColumn, idColumn
            );
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, objectId);
                stmt.setInt(2, userId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt("count");
                        boolean isStakeholder = count > 0;
                        logger.debug("User {} is {} stakeholder on {} {} (count: {})", 
                            userId, isStakeholder ? "a" : "not a", facetType, objectId, count);
                        return isStakeholder;
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            logger.error("Error checking if user {} is stakeholder on {} {}: {}", 
                userId, facetType, objectId, e.getMessage(), e);
            return false; // Fail securely - deny access on error
        }
    }
    
    /**
     * Normalize a lock module name (e.g. "policy", "data sets") to the facet type
     * used by the stakeholder check tables. Public so LockServlet can use it.
     */
    public static String normalizeFacetTypeForLock(String moduleName) {
        return normalizeModuleNameToFacetType(moduleName);
    }

    /**
     * Normalize module name to facet type for stakeholder checks
     */
    private static String normalizeModuleNameToFacetType(String moduleName) {
        if (moduleName == null) {
            return null;
        }
        
        // Map common module names to facet types
        String normalized = moduleName.toLowerCase()
            .replace(" ", "-")
            .replace("_", "-");
        
        // Handle special cases
        if (normalized.equals("data-sets") || normalized.equals("dataset")) {
            return "dataset";
        }
        if (normalized.equals("system-interface") || normalized.equals("interface")) {
            return "interface";
        }
        if (normalized.equals("business-area") || normalized.equals("businessarea")) {
            return "business-area";
        }
        if (normalized.equals("legal-entity") || normalized.equals("legalentity")) {
            return "legal-entity";
        }
        if (normalized.equals("org-unit") || normalized.equals("orgunit")) {
            return "org-unit";
        }
        if (normalized.equals("regulatory-theme") || normalized.equals("regulatorytheme")) {
            return "regulatory-theme";
        }
        
        return normalized;
    }
    
    /**
     * Send a 403 Forbidden response with error message
     */
    private static void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        // Add CORS headers before sending response
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("code", "FORBIDDEN");
        response.getWriter().write(error.toString());
    }
}
