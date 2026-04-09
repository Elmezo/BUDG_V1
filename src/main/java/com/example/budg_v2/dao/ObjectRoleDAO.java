package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ObjectRole;
import com.example.budg_v2.model.ObjectRoleType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ObjectRoleDAO {
    
    // جلب جميع الأدوار مع بيانات module
    public List<ObjectRole> getAllObjectRoles() throws SQLException {
        List<ObjectRole> roles = new ArrayList<>();
        String sql = "SELECT r.id, r.module, r.primaryname, r.description, r.defaultrole, r.objectroletype_id, " +
                     "m.primaryname as module_name " +
                     "FROM object_role r " +
                     "LEFT JOIN module m ON r.module = m.id";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                ObjectRole role = new ObjectRole();
                role.setId(rs.getInt("id"));
                role.setModule(rs.getObject("module", Integer.class));
                role.setPrimaryname(rs.getString("primaryname"));
                role.setDescription(rs.getString("description"));
                {
                    int defaultRoleInt = rs.getInt("defaultrole");
                    role.setDefaultrole(!rs.wasNull() ? defaultRoleInt == 1 : null);
                }
                role.setObjectroletypeId(rs.getObject("objectroletype_id", Integer.class));
                
                // إضافة اسم module كخاصية جديدة
                role.setModuleName(rs.getString("module_name"));
                
                roles.add(role);
            }
        }
        
        return roles;
    }
    
    // جلب الأدوار حسب Module ID
    public List<ObjectRole> getObjectRolesByModuleId(int moduleId) throws SQLException {
        List<ObjectRole> roles = new ArrayList<>();
        String sql = "SELECT r.id, r.module, r.primaryname, r.description, r.defaultrole, r.objectroletype_id, " +
                     "m.primaryname as module_name " +
                     "FROM object_role r " +
                     "LEFT JOIN module m ON r.module = m.id " +
                     "WHERE r.module = ? " +
                     "ORDER BY r.primaryname";
        
        //system.out.println("ObjectRoleDAO: Executing SQL: " + sql);
        //system.out.println("ObjectRoleDAO: Module ID parameter: " + moduleId);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, moduleId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ObjectRole role = new ObjectRole();
                    role.setId(rs.getInt("id"));
                    role.setModule(rs.getObject("module", Integer.class));
                    role.setPrimaryname(rs.getString("primaryname"));
                    role.setDescription(rs.getString("description"));
                    {
                        int defaultRoleInt = rs.getInt("defaultrole");
                        role.setDefaultrole(!rs.wasNull() ? defaultRoleInt == 1 : null);
                    }
                    role.setObjectroletypeId(rs.getObject("objectroletype_id", Integer.class));
                    
                    // إضافة اسم module كخاصية جديدة
                    role.setModuleName(rs.getString("module_name"));
                    
                    //system.out.println("ObjectRoleDAO: Found role " + count + ": ID=" + role.getId() + 
                                   //  ", Name=" + role.getPrimaryname() +
                                  //   ", Module=" + role.getModule());
                    
                    roles.add(role);
                }
                //system.out.println("ObjectRoleDAO: Total roles found: " + count);
            }
        }
        
        return roles;
    }
    
    // جلب جميع أنواع الأدوار
    public List<ObjectRoleType> getAllObjectRoleTypes() throws SQLException {
        List<ObjectRoleType> roleTypes = new ArrayList<>();
        String sql = "SELECT id, primaryname, description FROM object_role_type";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                ObjectRoleType roleType = new ObjectRoleType();
                roleType.setId(rs.getInt("id"));
                roleType.setPrimaryname(rs.getString("primaryname"));
                roleType.setDescription(rs.getString("description"));
                roleTypes.add(roleType);
            }
        }
        
        return roleTypes;
    }
    
    // جلب نوع دور بواسطة المعرف
    public ObjectRoleType getObjectRoleTypeById(int id) throws SQLException {
        String sql = "SELECT id, primaryname, description FROM object_role_type WHERE id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                ObjectRoleType roleType = new ObjectRoleType();
                roleType.setId(rs.getInt("id"));
                roleType.setPrimaryname(rs.getString("primaryname"));
                roleType.setDescription(rs.getString("description"));
                return roleType;
            }
        }
        
        return null;
    }
    
    // جلب أسماء جميع الجداول في قاعدة البيانات
    public List<String> getAllTableNames() throws SQLException {
        List<String> tableNames = new ArrayList<>();
        String sql = "SHOW TABLES";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                tableNames.add(rs.getString(1));
            }
        }
        
        return tableNames;
    }
    
    // جلب جميع modules
    public List<String> getAllModuleNames() throws SQLException {
        List<String> moduleNames = new ArrayList<>();
        String sql = "SELECT primaryname FROM module ORDER BY primaryname";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                moduleNames.add(rs.getString("primaryname"));
            }
        }
        
        return moduleNames;
    }
    
    // جلب دور بواسطة المعرف
    public ObjectRole getObjectRoleById(int id) throws SQLException {
        String sql = "SELECT id, module, primaryname, description, defaultrole, objectroletype_id FROM object_role WHERE id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                ObjectRole role = new ObjectRole();
                role.setId(rs.getInt("id"));
                role.setModule(rs.getObject("module", Integer.class));
                role.setPrimaryname(rs.getString("primaryname"));
                role.setDescription(rs.getString("description"));
                {
                    int defaultRoleInt = rs.getInt("defaultrole");
                    role.setDefaultrole(!rs.wasNull() ? defaultRoleInt == 1 : null);
                }
                role.setObjectroletypeId(rs.getObject("objectroletype_id", Integer.class));
                return role;
            }
        }
        
        return null;
    }
    
    // إنشاء دور جديد
    public boolean createObjectRole(ObjectRole role) throws SQLException {
        // بعض قواعد البيانات الحالية لا تحتوي على AUTO_INCREMENT للحقل id في object_role
        // لذلك نحسب المعرف التالي يدوياً (MAX(id)+1)
        Integer nextId = getNextObjectRoleId();
        String sql = "INSERT INTO object_role (id, module, primaryname, description, defaultrole, objectroletype_id) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, nextId);
            stmt.setObject(2, role.getModule());
            stmt.setString(3, role.getPrimaryname());
            stmt.setString(4, role.getDescription());
            if (role.getDefaultrole() == null) {
                stmt.setNull(5, Types.TINYINT);
            } else {
                stmt.setInt(5, role.getDefaultrole() ? 1 : 0);
            }
            stmt.setObject(6, role.getObjectroletypeId());
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    private Integer getNextObjectRoleId() throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM object_role";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("next_id");
            }
        }
        // fallback
        return 1;
    }
    
    // تحديث دور موجود
    public boolean updateObjectRole(ObjectRole role) throws SQLException {
        String sql = "UPDATE object_role SET module = ?, primaryname = ?, description = ?, defaultrole = ?, objectroletype_id = ? WHERE id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            //system.out.println("Updating role with ID: " + role.getId());
            //system.out.println("Role data: " + role.toString());
            
            stmt.setObject(1, role.getModule());
            stmt.setString(2, role.getPrimaryname());
            stmt.setString(3, role.getDescription());
            if (role.getDefaultrole() == null) {
                stmt.setNull(4, Types.TINYINT);
            } else {
                stmt.setInt(4, role.getDefaultrole() ? 1 : 0);
            }
            stmt.setObject(5, role.getObjectroletypeId());
            stmt.setInt(6, role.getId());
            
            int rowsAffected = stmt.executeUpdate();
            //system.out.println("Rows affected by update: " + rowsAffected);
            
            return rowsAffected > 0;
        }
    }
    
    // جلب module ID بواسطة الاسم
    public Integer getModuleIdByName(String moduleName) throws SQLException {
        String sql = "SELECT id FROM module WHERE primaryname = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, moduleName);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        
        return null;
    }
}
