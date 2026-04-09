package com.example.budg_v2.dao;

import com.example.budg_v2.model.Role;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RoleDAO {
    private final Connection connection;

    public RoleDAO(Connection connection) {
        this.connection = connection;
    }

    public List<Role> getAllRoles() throws SQLException {
        List<Role> roles = new ArrayList<>();
        String query = "SELECT id, primaryname FROM role";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Role role = new Role();
                role.setId(rs.getInt("id"));
                role.setPrimaryname(rs.getString("primaryname"));
                roles.add(role);
            }
        }

        return roles;
    }

    /** Returns only roles with primaryname 'Admin' or 'Web User' (case-insensitive) for bulk upload when uploader is admin. */
    public List<Role> getRolesForBulkUploadByAdmin() throws SQLException {
        List<Role> roles = new ArrayList<>();
        String query = "SELECT id, primaryname FROM role WHERE LOWER(TRIM(primaryname)) IN ('admin', 'web user') ORDER BY primaryname";

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Role role = new Role();
                role.setId(rs.getInt("id"));
                role.setPrimaryname(rs.getString("primaryname"));
                roles.add(role);
            }
        }
        return roles;
    }

    // Get all object roles (for Roles & Responsibilities)
    public List<ObjectRole> getAllObjectRoles() throws SQLException {
        List<ObjectRole> roles = new ArrayList<>();
        String query = "SELECT id, module, primaryname, description, defaultrole, objectroletype_id FROM object_role";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                ObjectRole role = new ObjectRole();
                role.setId(rs.getInt("id"));
                role.setModule(rs.getInt("module"));
                role.setPrimaryname(rs.getString("primaryname"));
                role.setDescription(rs.getString("description"));
                role.setDefaultrole(rs.getBoolean("defaultrole"));
                role.setObjectroletypeId(rs.getInt("objectroletype_id"));
                roles.add(role);
            }
        }

        return roles;
    }

    // Insert new object role
    public int insertObjectRole(ObjectRole role) throws SQLException {
        String query = "INSERT INTO object_role (module, primaryname, description, defaultrole, objectroletype_id) VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, role.getModule());
            stmt.setString(2, role.getPrimaryname());
            stmt.setString(3, role.getDescription());
            stmt.setBoolean(4, role.isDefaultrole());
            stmt.setInt(5, role.getObjectroletypeId());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating role failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating role failed, no ID obtained.");
                }
            }
        }
    }

    // Update object role
    public boolean updateObjectRole(ObjectRole role) throws SQLException {
        String query = "UPDATE object_role SET module = ?, primaryname = ?, description = ?, defaultrole = ?, objectroletype_id = ? WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, role.getModule());
            stmt.setString(2, role.getPrimaryname());
            stmt.setString(3, role.getDescription());
            stmt.setBoolean(4, role.isDefaultrole());
            stmt.setInt(5, role.getObjectroletypeId());
            stmt.setInt(6, role.getId());
            
            return stmt.executeUpdate() > 0;
        }
    }

    // Check if role is in use (referenced in role_assignment table)
    public boolean isRoleInUse(int id) throws SQLException {
        String query = "SELECT COUNT(*) FROM role_assignment WHERE objectroleid = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }
    
    // Get role name by ID for error messages
    public String getRoleNameById(int id) throws SQLException {
        String query = "SELECT primaryname FROM object_role WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        }
        
        return "Unknown Role";
    }

    // Delete object role
    public boolean deleteObjectRole(int id) throws SQLException {
        // Check if role is in use before attempting deletion
        if (isRoleInUse(id)) {
            String roleName = getRoleNameById(id);
            throw new SQLException("Cannot delete role \"" + roleName + "\" because it is currently assigned to one or more users. Please remove all role assignments before deleting the role.");
        }
        
        String query = "DELETE FROM object_role WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    // Get module ID by name
    public Integer getModuleIdByName(String moduleName) throws SQLException {
        String query = "SELECT id FROM module WHERE primaryname = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, moduleName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        
        return null;
    }

    // Get role type ID by name
    public Integer getRoleTypeIdByName(String roleTypeName) throws SQLException {
        String query = "SELECT id FROM object_role_type WHERE primaryname = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, roleTypeName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        
        return null;
    }

    // ObjectRole model class
    public static class ObjectRole {
        private int id;
        private int module;
        private String primaryname;
        private String description;
        private boolean defaultrole;
        private int objectroletypeId;

        public ObjectRole() {}

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public int getModule() { return module; }
        public void setModule(int module) { this.module = module; }

        public String getPrimaryname() { return primaryname; }
        public void setPrimaryname(String primaryname) { this.primaryname = primaryname; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public boolean isDefaultrole() { return defaultrole; }
        public void setDefaultrole(boolean defaultrole) { this.defaultrole = defaultrole; }

        public int getObjectroletypeId() { return objectroletypeId; }
        public void setObjectroletypeId(int objectroletypeId) { this.objectroletypeId = objectroletypeId; }
    }
}
