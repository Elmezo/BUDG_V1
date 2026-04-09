package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Module;
import com.example.budg_v2.model.ModuleGroup;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ModuleDAO {

    public List<ModuleGroup> getAllModuleGroups() throws SQLException {
        List<ModuleGroup> groups = new ArrayList<>();
        String query = "SELECT id, primaryname FROM module_group";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                groups.add(new ModuleGroup(rs.getInt("id"), rs.getString("primaryname")));
            }
        }
        return groups;
    }

    public List<Module> getAllModules() throws SQLException {
        List<Module> modules = new ArrayList<>();
        String query = "SELECT id, group_id, primaryname FROM module";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                modules.add(new Module(
                        rs.getInt("id"),
                        rs.getInt("group_id"),
                        rs.getString("primaryname"),
                        0 // rowCount سنجيبه من API منفصل
                ));
            }
        }
        return modules;
    }

    public int getTableRowCount(String tableName) throws SQLException {
         tableName = formatTableName(tableName);

        String query = "SELECT COUNT(*) FROM " + tableName;
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }
    public String formatTableName(String primaryName) {
        if (primaryName == null || primaryName.isBlank()) {
            throw new IllegalArgumentException("الاسم الأساسي للموديول فارغ أو غير صالح.");
        }

        // Handle special cases
        if (primaryName.equalsIgnoreCase("Change Requests") || primaryName.equalsIgnoreCase("Change Request")) {
            return "changerequest";
        }
        if (primaryName.equalsIgnoreCase("Role")) {
            return "object_role";
        }
        if (primaryName.equalsIgnoreCase("Data Sets")) {
            return "dataset";
        }
        if (primaryName.equalsIgnoreCase("Attributes")) {
            return "attribute";
        }
        if (primaryName.equalsIgnoreCase("Processes")) {
            return "process";
        }
        if (primaryName.equalsIgnoreCase("Projects")) {
            return "project";
        }
        if (primaryName.equalsIgnoreCase("Products")) {
            return "product";
        }
        if (primaryName.equalsIgnoreCase("Policies")) {
            return "policy";
        }
        if (primaryName.equalsIgnoreCase("Legal Entity")) {
            return "legal";
        }
        if (primaryName.equalsIgnoreCase("Regulatory Theme") || primaryName.equalsIgnoreCase("Regulatory Themes")) {
            return "regulatorytheme";
        }

        String formatted = primaryName.trim().toLowerCase();

        formatted = formatted.replaceAll("\\s+", "_");

        formatted = formatted.replaceAll("[^a-z0-9_]", "");

        if (formatted.isEmpty()) {
            throw new IllegalArgumentException("الاسم بعد المعالجة أصبح فارغًا. تحقق من البيانات.");
        }

        return formatted;
    }

}


