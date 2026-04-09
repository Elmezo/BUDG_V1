package com.example.budg_v2.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for handling default stakeholder role assignments.
 * Provides methods to check role assignments and determine which default roles
 * a creator should receive when creating objects.
 */
public class DefaultStakeholderUtil {

    /**
     * Get all default roles for a module
     * @param conn Database connection
     * @param moduleId Module ID
     * @return List of default role IDs
     * @throws SQLException if database error occurs
     */
    public static List<Integer> getDefaultRoleIds(Connection conn, int moduleId) throws SQLException {
        List<Integer> roleIds = new ArrayList<>();
        String sql = "SELECT id FROM object_role WHERE module = ? AND defaultrole = 1 ORDER BY id";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roleIds.add(rs.getInt("id"));
                }
            }
        }
        return roleIds;
    }

    /**
     * Get module ID by module name
     * @param conn Database connection
     * @param moduleName Module name
     * @return Module ID
     * @throws SQLException if module not found or database error occurs
     */
    public static int getModuleId(Connection conn, String moduleName) throws SQLException {
        String sql = "SELECT id FROM module WHERE primaryname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, moduleName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        throw new SQLException("Module not found: " + moduleName);
    }

    /**
     * Check if a role has an assignment in role_assignment table
     * @param conn Database connection
     * @param roleId Role ID
     * @return true if assignment exists, false otherwise
     * @throws SQLException if database error occurs
     */
    public static boolean hasRoleAssignment(Connection conn, int roleId) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM role_assignment WHERE objectroleid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        }
        return false;
    }

    /**
     * Get assigned user IDs from role_assignment table
     * @param conn Database connection
     * @param roleId Role ID
     * @return List of assigned user IDs, empty list if no assignment or error
     * @throws SQLException if database error occurs
     */
    public static List<Integer> getAssignedUserIds(Connection conn, int roleId) throws SQLException {
        List<Integer> userIds = new ArrayList<>();
        String sql = "SELECT users FROM role_assignment WHERE objectroleid = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String usersJson = rs.getString("users");
                    if (usersJson != null && !usersJson.trim().isEmpty()) {
                        userIds = parseUsersJson(usersJson);
                    }
                }
            }
        }
        return userIds;
    }

    /**
     * Parse users JSON string to list of user IDs
     * Handles formats: "[1,2,3]", "1,2,3", or single number
     * @param usersJson JSON string containing user IDs
     * @return List of user IDs
     */
    public static List<Integer> parseUsersJson(String usersJson) {
        List<Integer> userIds = new ArrayList<>();
        if (usersJson == null || usersJson.trim().isEmpty()) {
            return userIds;
        }
        
        try {
            usersJson = usersJson.trim();
            // Remove brackets if present
            if (usersJson.startsWith("[") && usersJson.endsWith("]")) {
                usersJson = usersJson.substring(1, usersJson.length() - 1);
            }
            
            if (!usersJson.isEmpty()) {
                String[] userIdStrings = usersJson.split(",");
                for (String userIdStr : userIdStrings) {
                    try {
                        int userId = Integer.parseInt(userIdStr.trim());
                        userIds.add(userId);
                    } catch (NumberFormatException e) {
                        // Skip invalid user IDs
                    }
                }
            }
        } catch (Exception e) {
            // Return empty list on parse error
        }
        
        return userIds;
    }

    /**
     * Check if a user is assigned to a role
     * @param conn Database connection
     * @param userId User ID
     * @param roleId Role ID
     * @return true if user is assigned, false otherwise
     * @throws SQLException if database error occurs
     */
    public static boolean isUserAssignedToRole(Connection conn, int userId, int roleId) throws SQLException {
        List<Integer> assignedUserIds = getAssignedUserIds(conn, roleId);
        return assignedUserIds.contains(userId);
    }

    /**
     * Check if a user is "actually" assigned to a role (role has role_assignment and user is in the list).
     * If the role has no role_assignment row, nobody is "actually assigned" in admin terms.
     *
     * @param conn Database connection
     * @param userId User ID
     * @param roleId Role ID
     * @return true only when the role has a role_assignment row and the user is in the assigned users list
     */
    public static boolean isUserActuallyAssignedToRole(Connection conn, int userId, int roleId) throws SQLException {
        return hasRoleAssignment(conn, roleId) && isUserAssignedToRole(conn, userId, roleId);
    }

    /**
     * Get current user's stakeholder role records (object_x_people.id, roleId) on the given object.
     *
     * @param conn Database connection
     * @param linkTable Join table name (e.g. system_x_objectxpeople)
     * @param idColumn Column name for object id in link table (e.g. SystemID)
     * @param objectId Object id
     * @param objectXIpidColumn Column name linking to object_x_people.id (e.g. Object_x_ipid or object_x_ip)
     * @param currentUserId Current user's people id (ipid)
     * @return List of [objectXPeopleId, roleId] pairs; empty if none
     */
    public static List<int[]> getCurrentUserStakeholderRoleRecords(Connection conn, String linkTable,
            String idColumn, int objectId, String objectXIpidColumn, int currentUserId) throws SQLException {
        String sql = "SELECT oxp.id, oxp.RoleID FROM " + linkTable + " jt "
                + "JOIN object_x_people oxp ON jt." + objectXIpidColumn + " = oxp.id "
                + "WHERE jt." + idColumn + " = ? AND oxp.ipid = ?";
        List<int[]> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectId);
            ps.setInt(2, currentUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new int[] { rs.getInt("id"), rs.getInt("RoleID") });
                }
            }
        }
        return out;
    }

    /**
     * Extract object_x_people IDs from a stakeholder save payload's "deletes" list.
     * @param payload Map with "deletes" key (list of maps with "objectXPeopleId" or "object_x_people_id")
     * @return Set of IDs (never null)
     */
    public static Set<Integer> extractObjectXPeopleIdsFromDeletes(Map<String, Object> payload) {
        Set<Integer> out = new HashSet<>();
        Object deletesObj = payload == null ? null : payload.get("deletes");
        if (!(deletesObj instanceof List)) {
            return out;
        }
        for (Object o : (List<?>) deletesObj) {
            if (o instanceof Map) {
                Map<?, ?> row = (Map<?, ?>) o;
                Object idObj = row.get("objectXPeopleId");
                if (idObj == null) {
                    idObj = row.get("object_x_people_id");
                }
                if (idObj instanceof Number) {
                    out.add(((Number) idObj).intValue());
                }
            }
        }
        return out;
    }

    /** Message shown when current user has a default-only role and cannot save stakeholder changes. */
    public static final String DEFAULT_ROLE_CANNOT_SAVE_MESSAGE =
            "You are not actually assigned to that role (you received it by default because no one else was assigned). "
                    + "You cannot save stakeholder changes unless you remove that record or an administrator assigns that role to you from the admin panel.";

    /**
     * Validate that the current user can save stakeholder changes for this object.
     * Blocks when the current user is a stakeholder with a role they are not "actually" assigned to
     * (e.g. they got it by default), unless those records are being deleted in this request.
     *
     * @param conn Database connection
     * @param linkTable Join table name (e.g. system_x_objectxpeople)
     * @param idColumn Column name for object id in link table
     * @param objectId Object id
     * @param objectXIpidColumn Column name linking to object_x_people.id
     * @param currentUserId Current user's people id
     * @param objectXPeopleIdsBeingDeleted Set of object_x_people ids that are in the request's deletes list (may be null)
     * @return ValidationResult invalid with message if user has a default-only role record not being deleted
     */
    public static ValidationResult validateCurrentUserCanSaveStakeholders(Connection conn, String linkTable,
            String idColumn, int objectId, String objectXIpidColumn, int currentUserId,
            Set<Integer> objectXPeopleIdsBeingDeleted) throws SQLException {
        Set<Integer> deletes = objectXPeopleIdsBeingDeleted != null ? objectXPeopleIdsBeingDeleted : Collections.emptySet();
        List<int[]> records = getCurrentUserStakeholderRoleRecords(conn, linkTable, idColumn, objectId, objectXIpidColumn, currentUserId);
        for (int[] rec : records) {
            int oxpId = rec[0];
            int roleId = rec[1];
            if (!deletes.contains(oxpId) && !isUserActuallyAssignedToRole(conn, currentUserId, roleId)) {
                return new ValidationResult(false, DEFAULT_ROLE_CANNOT_SAVE_MESSAGE);
            }
        }
        return new ValidationResult(true, null);
    }

    /** Entity config for validateCurrentUserCanSaveStakeholders by entity type. */
    private static final java.util.Map<String, EntityStakeholderConfig> ENTITY_STAKEHOLDER_CONFIG;

    static {
        java.util.Map<String, EntityStakeholderConfig> m = new java.util.HashMap<>();
        m.put("system", new EntityStakeholderConfig("system_x_objectxpeople", "SystemID", "Object_x_ipid"));
        m.put("dataset", new EntityStakeholderConfig("dataset_x_objectxpeople", "Dataset_ID", "Object_x_ipid"));
        m.put("interface", new EntityStakeholderConfig("interface_x_objectxpeople", "InterfaceID", "Object_x_ipid"));
        m.put("glossary", new EntityStakeholderConfig("glossary_x_objectxpeople", "GlossaryID", "Object_x_ipid"));
        m.put("regulation", new EntityStakeholderConfig("regulation_x_objectxpeople", "RegulationID", "Object_x_ipid"));
        m.put("process", new EntityStakeholderConfig("process_x_objectxpeople", "process_id", "object_x_ip"));
        m.put("project", new EntityStakeholderConfig("project_x_objectxpeople", "project_id", "object_x_ip"));
        m.put("product", new EntityStakeholderConfig("product_x_objectxpeople", "product_id", "object_x_ip"));
        m.put("capability", new EntityStakeholderConfig("capability_x_objectxpeople", "CapabilityID", "Object_x_ipid"));
        m.put("client", new EntityStakeholderConfig("client_x_objectxpeople", "ClientID", "Object_x_ipid"));
        m.put("committee", new EntityStakeholderConfig("committee_x_objectxpeople", "Committee_ID", "Object_X_ipid"));
        m.put("policy", new EntityStakeholderConfig("policy_x_objectxpeople", "Policy_ID", "Object_X_IP"));
        m.put("legalentity", new EntityStakeholderConfig("legal_x_objectxpeople", "Legal_ID", "Object_X_IP"));
        m.put("legal-entity", new EntityStakeholderConfig("legal_x_objectxpeople", "Legal_ID", "Object_X_IP"));
        m.put("businessarea", new EntityStakeholderConfig("businessarea_x_objectxpeople", "BusinessAreaID", "Object_x_ipid"));
        m.put("business-area", new EntityStakeholderConfig("businessarea_x_objectxpeople", "BusinessAreaID", "Object_x_ipid"));
        m.put("attribute", new EntityStakeholderConfig("attribute_x_objectxpeople", "AttributeID", "Object_x_ipid"));
        ENTITY_STAKEHOLDER_CONFIG = Collections.unmodifiableMap(m);
    }

    /**
     * Validate that the current user can save stakeholder changes, using entity type to resolve table/columns.
     *
     * @param conn Database connection
     * @param entityType Entity type (e.g. "System", "Dataset", "process") - case-insensitive
     * @param objectId Object id
     * @param currentUserId Current user's people id
     * @param objectXPeopleIdsBeingDeleted Set of object_x_people ids in the request's deletes list (may be null)
     * @return ValidationResult invalid with message if user has a default-only role record not being deleted
     */
    public static ValidationResult validateCurrentUserCanSaveStakeholders(Connection conn, String entityType,
            int objectId, int currentUserId, Set<Integer> objectXPeopleIdsBeingDeleted) throws SQLException {
        EntityStakeholderConfig config = ENTITY_STAKEHOLDER_CONFIG.get(entityType == null ? null : entityType.toLowerCase().trim());
        if (config == null) {
            return new ValidationResult(true, null);
        }
        return validateCurrentUserCanSaveStakeholders(conn, config.linkTable, config.idColumn, objectId,
                config.objectXIpidColumn, currentUserId, objectXPeopleIdsBeingDeleted);
    }

    private static final class EntityStakeholderConfig {
        final String linkTable;
        final String idColumn;
        final String objectXIpidColumn;

        EntityStakeholderConfig(String linkTable, String idColumn, String objectXIpidColumn) {
            this.linkTable = linkTable;
            this.idColumn = idColumn;
            this.objectXIpidColumn = objectXIpidColumn;
        }
    }

    /**
     * Determine which default roles a creator should get based on assignment rules
     * @param conn Database connection
     * @param moduleId Module ID
     * @param creatorUserId Creator user ID
     * @return List of role IDs the creator should receive
     * @throws SQLException if database error occurs
     */
    public static List<Integer> getDefaultRolesForCreator(Connection conn, int moduleId, int creatorUserId) throws SQLException {
        List<Integer> rolesToAssign = new ArrayList<>();
        List<Integer> defaultRoleIds = getDefaultRoleIds(conn, moduleId);
        
        for (Integer roleId : defaultRoleIds) {
            boolean hasAssignment = hasRoleAssignment(conn, roleId);
            
            if (!hasAssignment) {
                // No assignment: creator automatically gets the role
                rolesToAssign.add(roleId);
            } else {
                // Has assignment: check if creator is in the assigned users list
                if (isUserAssignedToRole(conn, creatorUserId, roleId)) {
                    rolesToAssign.add(roleId);
                }
                // If creator is not in the list, they don't get the role (skip)
            }
        }
        
        return rolesToAssign;
    }

    /**
     * Check if a stakeholder holds a role purely by default (no role_assignment row exists for the role).
     * Such a user received the role automatically at object creation time because no one was formally
     * assigned to it in the admin panel. The backend blocks saves for this user until the record is
     * removed or an administrator formally assigns them via role_assignment.
     *
     * @param conn   Database connection
     * @param roleId Role ID to check
     * @return true when no role_assignment row exists for the role (default-only scenario)
     * @throws SQLException if database error occurs
     */
    public static boolean isDefaultOnlyStakeholder(Connection conn, int roleId) throws SQLException {
        return !hasRoleAssignment(conn, roleId);
    }

    /**
     * Validate if a stakeholder has a valid role assignment (admin panel).
     * For any role that has an entry in role_assignment, the user must be in the assigned users list.
     * If the role has no role_assignment row, anyone can be assigned (no admin restriction).
     *
     * @param conn Database connection
     * @param userId User ID
     * @param roleId Role ID
     * @return ValidationResult with isValid flag and optional warning message
     * @throws SQLException if database error occurs
     */
    public static ValidationResult validateStakeholderRoleAssignment(Connection conn, int userId, int roleId) throws SQLException {
        // If role has no admin assignment, anyone can have this role
        boolean hasAssignment = hasRoleAssignment(conn, roleId);
        if (!hasAssignment) {
            return new ValidationResult(true, null);
        }

        // Assignment exists: user must be in the assigned list
        boolean isAssigned = isUserAssignedToRole(conn, userId, roleId);
        if (isAssigned) {
            return new ValidationResult(true, null);
        }

        String warning = "The user is not assigned to this role. Select another user that is assigned to this role.";
        return new ValidationResult(false, warning);
    }

    /**
     * Get role name by role ID
     * @param conn Database connection
     * @param roleId Role ID
     * @return Role name or "Unknown Role" if not found
     * @throws SQLException if database error occurs
     */
    @SuppressWarnings("unused")
    private static String getRoleName(Connection conn, int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        }
        return "Unknown Role";
    }

    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean isValid;
        private final String warningMessage;

        public ValidationResult(boolean isValid, String warningMessage) {
            this.isValid = isValid;
            this.warningMessage = warningMessage;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getWarningMessage() {
            return warningMessage;
        }
    }
}

