package com.example.budg_v2.bulk.roles.util;

import com.example.budg_v2.bulk.common.BulkUploadUtil;
import com.example.budg_v2.util.DefaultStakeholderUtil;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared utility methods for all Role Handler classes
 * Eliminates code duplication across multiple role handlers
 */
public class RoleHandlerUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(RoleHandlerUtil.class);
    private static final String OBJECT_X_PEOPLE_TABLE = "object_x_people";
    
    /**
     * Get person's full name from database
     */
    public static String getPersonName(Connection conn, int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("fullName");
                }
            }
        }
        return "User " + personId;
    }
    
    /**
     * Get role's primary name from database
     */
    public static String getRoleName(Connection conn, int roleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM object_role WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        }
        return null;
    }
    
    /**
     * Check if role belongs to the specified module
     */
    public static boolean isRoleInModule(Connection conn, int roleId, String moduleName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM object_role r " +
                    "INNER JOIN module m ON r.module = m.id " +
                    "WHERE r.id = ? AND m.primaryname = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            ps.setString(2, moduleName);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }

    /**
     * Resolve role ID by name scoped to a module (facet). Prevents picking a role from another module
     * when the same primary name exists in multiple modules.
     *
     * @param conn            database connection
     * @param roleName        role primary name (trimmed, case-insensitive match)
     * @param modulePrimaryName module.primaryname e.g. "Client", "Policy"
     * @return role id, or null if not found
     * @throws IllegalArgumentException if more than one role matches (ambiguous)
     */
    public static Integer getRoleIdByNameInModule(Connection conn, String roleName, String modulePrimaryName)
            throws SQLException {
        if (roleName == null || roleName.trim().isEmpty() || modulePrimaryName == null || modulePrimaryName.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT r.id FROM object_role r "
                + "INNER JOIN module m ON r.module = m.id "
                + "WHERE LOWER(r.primaryname) = LOWER(?) AND m.primaryname = ?";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleName.trim());
            ps.setString(2, modulePrimaryName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                }
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        if (ids.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple governance roles found with name '" + roleName.trim() + "' for module " + modulePrimaryName
                            + "; use Governance Role_ID to disambiguate.");
        }
        return ids.get(0);
    }

    // Resolves role by name within module; wraps SQLException in RuntimeException.
    public static Integer getRoleIdByNameInModuleRuntime(Connection conn, String roleName, String modulePrimaryName) {
        try {
            return getRoleIdByNameInModule(conn, roleName, modulePrimaryName);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to resolve governance role: " + e.getMessage(), e);
        }
    }

    // Validates role belongs to module and person is on admin role list when configured.
    public static void validateGovernanceRoleForObjectInsert(Connection conn, int personId, int roleId,
            String modulePrimaryName, int rowNumber) {
        try {
            if (!isRoleInModule(conn, roleId, modulePrimaryName)) {
                String roleLabel = getRoleName(conn, roleId);
                String roleInfo = roleLabel != null ? "'" + roleLabel + "'" : "ID " + roleId;
                throw new RuntimeException("Row " + rowNumber + ": Governance role " + roleInfo
                        + " is not valid for " + modulePrimaryName + ". Use a role assigned to this module only.");
            }
            DefaultStakeholderUtil.ValidationResult vr =
                    DefaultStakeholderUtil.validateStakeholderRoleAssignment(conn, personId, roleId);
            if (!vr.isValid()) {
                throw new RuntimeException("Row " + rowNumber + ": " + vr.getWarningMessage());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Row " + rowNumber + ": Unable to verify governance role: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if the role assignment already exists (for INSERT operations)
     * 
     * @param entityId The entity ID (e.g., Legal_ID, System_ID)
     * @param personId The person ID
     * @param roleId The role ID
     * @param linkingTable The linking table name (e.g., "legal_x_objectxpeople")
     * @param entityCol The entity column name in linking table (e.g., "Legal_ID")
     * @param oxpCol The object_x_people column name in linking table (e.g., "Object_X_IP")
     * @param entityName The entity name for error messages (e.g., "Legal Entity")
     */
    public static boolean isDuplicateAssignment(Connection conn, int entityId, int personId, int roleId,
                                               String linkingTable, String entityCol, String oxpCol,
                                               String entityName) throws SQLException {
        // Step 1: Find ALL object_x_people records for this person + role
        // (There may be multiple records, and we need to check if ANY of them are already linked to this entity)
        String sqlFindOxp = "SELECT ID FROM " + OBJECT_X_PEOPLE_TABLE + " WHERE ipid = ? AND RoleID = ?";
        List<Integer> objectXPeopleIds = new ArrayList<>();
        
        try (PreparedStatement ps = conn.prepareStatement(sqlFindOxp)) {
            ps.setInt(1, personId);
            ps.setInt(2, roleId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    objectXPeopleIds.add(rs.getInt("ID"));
                }
            }
        }
        
        if (objectXPeopleIds.isEmpty()) {
            // No existing object_x_people record, so not a duplicate
            return false;
        }
        
        // Step 2: Check if ANY of the object_x_people records are already linked to this entity
        String sqlCheckLink = "SELECT COUNT(*) FROM " + linkingTable + " WHERE " + entityCol + " = ? AND " + oxpCol + " = ?";
        
        for (Integer objectXPeopleId : objectXPeopleIds) {
            try (PreparedStatement ps = conn.prepareStatement(sqlCheckLink)) {
                ps.setInt(1, entityId);
                ps.setInt(2, objectXPeopleId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return true; // Duplicate found - at least one link already exists
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if the role assignment exists (for DELETE operations)
     * 
     * @param entityId The entity ID
     * @param personId The person ID
     * @param roleId The role ID
     * @param linkingTable The linking table name
     * @param entityCol The entity column name in linking table
     * @param oxpCol The object_x_people column name in linking table
     * @param entityName The entity name for error messages
     */
    public static boolean assignmentExists(Connection conn, int entityId, int personId, int roleId,
                                          String linkingTable, String entityCol, String oxpCol,
                                          String entityName) throws SQLException {
        // Step 1: Find ALL object_x_people records for this person+role combination
        // (There may be multiple records, and we need to check if ANY of them are linked to this entity)
        String sqlFindOxp = "SELECT ID FROM " + OBJECT_X_PEOPLE_TABLE + " WHERE ipid = ? AND RoleID = ?";
        List<Integer> objectXPeopleIds = new ArrayList<>();
        
        try (PreparedStatement ps = conn.prepareStatement(sqlFindOxp)) {
            ps.setInt(1, personId);
            ps.setInt(2, roleId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    objectXPeopleIds.add(rs.getInt("ID"));
                }
            }
        }
        
        if (objectXPeopleIds.isEmpty()) {
            // No object_x_people record means assignment doesn't exist
            return false;
        }
        
        // Step 2: Check if ANY of the object_x_people records are linked to this entity
        String sqlCheckLink = "SELECT COUNT(*) FROM " + linkingTable + " WHERE " + entityCol + " = ? AND " + oxpCol + " = ?";
        
        for (Integer objectXPeopleId : objectXPeopleIds) {
            try (PreparedStatement ps = conn.prepareStatement(sqlCheckLink)) {
                ps.setInt(1, entityId);
                ps.setInt(2, objectXPeopleId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return true; // Assignment exists - at least one link found
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if the role assignment exists and return detailed error message if not found
     * 
     * @param conn Database connection
     * @param entityId The entity ID
     * @param personId The person ID
     * @param roleId The role ID
     * @param linkingTable The linking table name
     * @param entityCol The entity column name in linking table
     * @param oxpCol The object_x_people column name in linking table
     * @param entityName The entity name for error messages
     * @param entityTable The entity table name (e.g., "dataset", "system")
     * @param entityNameField The entity name field (e.g., "PrimaryName", "ShortName")
     * @return Error message if assignment doesn't exist, null if it exists
     */
    public static String checkAssignmentExistsWithDetails(Connection conn, int entityId, int personId, int roleId,
                                                          String linkingTable, String entityCol, String oxpCol,
                                                          String entityName, String entityTable, String entityNameField) throws SQLException {
        // Get entity name
        String entityDisplayName = null;
        try {
            String sql = "SELECT " + entityNameField + " FROM " + entityTable + " WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, entityId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        entityDisplayName = rs.getString(1);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not get entity name: {}", e.getMessage());
        }
        
        // Get person name
        String personDisplayName = getPersonName(conn, personId);
        
        // Get role name
        String roleDisplayName = null;
        try {
            String sql = "SELECT PrimaryName FROM object_role WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, roleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        roleDisplayName = rs.getString(1);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not get role name: {}", e.getMessage());
        }
        
        // Step 1: Find ALL object_x_people records for this person+role combination
        // (There may be multiple records, and we need to check if ANY of them are linked to this entity)
        String sqlFindOxp = "SELECT ID FROM " + OBJECT_X_PEOPLE_TABLE + " WHERE ipid = ? AND RoleID = ?";
        List<Integer> objectXPeopleIds = new ArrayList<>();
        
        try (PreparedStatement ps = conn.prepareStatement(sqlFindOxp)) {
            ps.setInt(1, personId);
            ps.setInt(2, roleId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    objectXPeopleIds.add(rs.getInt("ID"));
                }
            }
        }
        
        if (objectXPeopleIds.isEmpty()) {
            // Build detailed error message
            String personInfo = personDisplayName != null ? personDisplayName : "Person ID " + personId;
            String roleInfo = roleDisplayName != null ? "'" + roleDisplayName + "'" : "Role ID " + roleId;
            String entityInfo = entityDisplayName != null ? entityDisplayName : entityName + " ID " + entityId;
            
            return String.format("Cannot delete role assignment: No role assignment found for %s with role %s for %s. The person may not have this role assigned, or the role assignment may have already been removed.",
                personInfo, roleInfo, entityInfo);
        }
        
        // Step 2: Check if ANY of the object_x_people records are linked to this entity
        String sqlCheckLink = "SELECT COUNT(*) FROM " + linkingTable + " WHERE " + entityCol + " = ? AND " + oxpCol + " = ?";
        
        for (Integer objectXPeopleId : objectXPeopleIds) {
            try (PreparedStatement ps = conn.prepareStatement(sqlCheckLink)) {
                ps.setInt(1, entityId);
                ps.setInt(2, objectXPeopleId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return null; // Assignment exists - at least one link found
                    }
                }
            }
        }
        
        // Build detailed error message
        String personInfo = personDisplayName != null ? personDisplayName : "Person ID " + personId;
        String roleInfo = roleDisplayName != null ? "'" + roleDisplayName + "'" : "Role ID " + roleId;
        String entityInfo = entityDisplayName != null ? entityDisplayName : entityName + " ID " + entityId;
        
        return String.format("Cannot delete role assignment: The role %s assigned to %s does not exist for %s. The role assignment may have already been removed, or the person may have this role assigned to a different %s.",
            roleInfo, personInfo, entityInfo, entityName);
    }
    
    /**
     * Get the distinct role IDs that a person has on an entity (for DELETE validation).
     * Used to prevent deleting when the user specified the wrong role for a stakeholder who has only one role.
     *
     * @param conn Database connection
     * @param entityId Entity ID (e.g. Regulation ID)
     * @param personId Person ID (ipid in object_x_people)
     * @param linkingTable Junction table (e.g. regulation_x_objectxpeople)
     * @param entityCol Entity column name in junction (e.g. RegulationID)
     * @param oxpCol Object_x_people column name in junction (e.g. Object_x_ipid)
     * @return List of role IDs (may be empty)
     */
    public static List<Integer> getRoleIdsForPersonOnEntity(Connection conn, int entityId, int personId,
                                                            String linkingTable, String entityCol, String oxpCol) throws SQLException {
        String sql = "SELECT DISTINCT oxp.RoleID FROM " + linkingTable + " j " +
                "INNER JOIN " + OBJECT_X_PEOPLE_TABLE + " oxp ON j." + oxpCol + " = oxp.ID " +
                "WHERE j." + entityCol + " = ? AND oxp.ipid = ?";
        List<Integer> roleIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, entityId);
            ps.setInt(2, personId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roleIds.add(rs.getInt("RoleID"));
                }
            }
        }
        return roleIds;
    }
    
    /**
     * Validate that basic IDs are present in row data
     * 
     * @param rowData The row data from normalized Excel
     * @param entityIdField The entity ID field name (e.g., "Legal_ID")
     * @param entityDisplayName The entity display name for error messages (e.g., "Legal Entity")
     * @return Validation result JsonObject
     */
    public static JsonObject validateBasicIds(JsonObject rowData, String entityIdField, String entityDisplayName) {
        Integer entityId = BulkUploadUtil.getInteger(rowData, entityIdField);
        Integer personId = BulkUploadUtil.getInteger(rowData, "Person_ID");
        Integer roleId = BulkUploadUtil.getInteger(rowData, "Role_ID");
        
        if (entityId == null) {
            return BulkUploadUtil.createValidationError(entityDisplayName + " could not be identified or does not exist");
        }
        
        if (personId == null) {
            return BulkUploadUtil.createValidationError("User/Person could not be identified or does not exist");
        }
        
        if (roleId == null) {
            return BulkUploadUtil.createValidationError("Governance Role could not be identified or does not exist");
        }
        
        return BulkUploadUtil.createValidationSuccess();
    }
    
    /**
     * Find object_x_people ID for deletion operations
     * 
     * @param conn Database connection
     * @param personId The person ID
     * @param roleId The role ID
     * @return The object_x_people ID, or null if not found
     */
    public static Integer findObjectXPeopleId(Connection conn, int personId, int roleId) throws SQLException {
        String sql = "SELECT ID FROM " + OBJECT_X_PEOPLE_TABLE + " WHERE ipid = ? AND RoleID = ? LIMIT 1";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            ps.setInt(2, roleId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if a link already exists in the linking table before attempting to insert
     * 
     * @param conn Database connection
     * @param linkingTable The linking table name (e.g., "dataset_x_objectxpeople")
     * @param entityCol The entity column name in linking table (e.g., "Dataset_ID")
     * @param entityId The entity ID
     * @param oxpCol The object_x_people column name in linking table (e.g., "Object_x_ipid")
     * @param objectXPeopleId The object_x_people ID
     * @param entityName The entity name for error messages
     * @throws SQLException if link already exists
     */
    public static void checkLinkNotExists(Connection conn, String linkingTable, String entityCol, 
                                         int entityId, String oxpCol, int objectXPeopleId, 
                                         String entityName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + linkingTable + " WHERE " + entityCol + " = ? AND " + oxpCol + " = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, entityId);
            ps.setInt(2, objectXPeopleId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new SQLException("This role assignment already exists for this " + entityName);
                }
            }
        }
    }
    
    /**
     * Extract object ID from row data based on entity name
     * Maps entity name to ID field name (e.g., "Policy" → "Policy_ID", "System" → "System_ID")
     * 
     * @param rowData The row data from normalized Excel
     * @param entityName The entity name (e.g., "Policy", "System", "Glossary")
     * @return The object ID, or null if not found
     */
    public static Integer extractObjectId(JsonObject rowData, String entityName) {
        if (entityName == null || rowData == null) {
            return null;
        }
        
        // Map entity name to ID field name
        String idFieldName = getEntityIdFieldName(entityName);
        if (idFieldName == null) {
            logger.warn("Unknown entity name for ID extraction: {}", entityName);
            return null;
        }
        
        return BulkUploadUtil.getInteger(rowData, idFieldName);
    }
    
    /**
     * Map entity name to ID field name
     * 
     * @param entityName The entity name (e.g., "Policy", "System")
     * @return The ID field name (e.g., "Policy_ID", "System_ID"), or null if not found
     */
    private static String getEntityIdFieldName(String entityName) {
        if (entityName == null) {
            return null;
        }
        
        String normalized = entityName.trim();
        
        // Map entity names to ID field names
        return switch (normalized) {
            case "Policy" -> "Policy_ID";
            case "System" -> "System_ID";
            case "Glossary" -> "Glossary_ID";
            case "Data Set", "Dataset", "Data Sets" -> "Dataset_ID";
            case "Process" -> "Process_ID";
            case "Product" -> "Product_ID";
            case "Project" -> "Project_ID";
            case "Regulation" -> "Regulation_ID";
            case "Legal Entity", "LegalEntity" -> "Legal_ID";
            case "Client" -> "Client_ID";
            case "Committee" -> "Committee_ID";
            case "Business Area", "BusinessArea" -> "BusinessArea_ID";
            case "Capability" -> "Capability_ID";
            case "Interface" -> "Interface_ID";
            default -> null;
        };
    }
    
    /**
     * Map entity name to segment object type
     * Used for segment access validation
     * 
     * @param entityName The entity name (e.g., "Policy", "System")
     * @return The segment object type (e.g., "Policy", "System", "LegalEntity"), or null if not found
     */
    public static String mapEntityNameToSegmentObjectType(String entityName) {
        if (entityName == null) {
            return null;
        }
        
        String normalized = entityName.trim();
        
        // Map entity names to segment object types
        return switch (normalized) {
            case "Policy" -> "Policy";
            case "System" -> "System";
            case "Glossary" -> "Glossary";
            case "Data Set", "Dataset", "Data Sets" -> "Dataset";
            case "Process" -> "Process";
            case "Product" -> "Product";
            case "Project" -> "Project";
            case "Regulation" -> "Regulation";
            case "Legal Entity", "LegalEntity" -> "LegalEntity";
            case "Client" -> "Client";
            case "Committee" -> "Committee";
            case "Business Area", "BusinessArea" -> "BusinessArea";
            case "Capability" -> "Capability";
            case "Interface" -> "SystemInterface";
            default -> null;
        };
    }
}

