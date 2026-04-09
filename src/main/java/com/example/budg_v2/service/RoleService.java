package com.example.budg_v2.service;

import com.example.budg_v2.dao.RoleDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class RoleService {

    public List<Role> getAllRoles() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            RoleDAO roleDAO = new RoleDAO(connection);
            return roleDAO.getAllRoles();
        }
    }

    /** Returns only Admin and Web User roles (for bulk upload when the uploader is an admin). */
    public List<Role> getRolesForBulkUploadByAdmin() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            RoleDAO roleDAO = new RoleDAO(connection);
            return roleDAO.getRolesForBulkUploadByAdmin();
        }
    }

    // Get all object roles (for Roles & Responsibilities)
    public List<RoleDAO.ObjectRole> getAllObjectRoles() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            RoleDAO roleDAO = new RoleDAO(connection);
            return roleDAO.getAllObjectRoles();
        }
    }

    // Insert new object role
    public int insertObjectRole(RoleDAO.ObjectRole role) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            RoleDAO roleDAO = new RoleDAO(connection);
            return roleDAO.insertObjectRole(role);
        }
    }

    // Update object role
    public boolean updateObjectRole(RoleDAO.ObjectRole role) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            RoleDAO roleDAO = new RoleDAO(connection);
            return roleDAO.updateObjectRole(role);
        }
    }

    // Check if role is in use
    public boolean isRoleInUse(int id) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            RoleDAO roleDAO = new RoleDAO(connection);
            return roleDAO.isRoleInUse(id);
        }
    }
    
    // Delete object role
    public boolean deleteObjectRole(int id) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            RoleDAO roleDAO = new RoleDAO(connection);
            return roleDAO.deleteObjectRole(id);
        }
    }

    // Get module ID by name
    public Integer getModuleIdByName(String moduleName) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            RoleDAO roleDAO = new RoleDAO(connection);
            return roleDAO.getModuleIdByName(moduleName);
        }
    }

    // Get role type ID by name
    public Integer getRoleTypeIdByName(String roleTypeName) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            RoleDAO roleDAO = new RoleDAO(connection);
            return roleDAO.getRoleTypeIdByName(roleTypeName);
        }
    }
}
