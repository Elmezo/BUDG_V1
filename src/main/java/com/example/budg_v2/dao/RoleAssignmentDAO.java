package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.RoleAssignment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RoleAssignmentDAO {

    public List<RoleAssignment> listAll() throws SQLException {
        String sql = "SELECT ra.id, ra.objectroleid, ra.users, ra.createdate, ra.lastupdateddate, " +
                " r.primaryname AS role_name, m.primaryname AS module_name, m.id AS module_id " +
                " FROM role_assignment ra " +
                " LEFT JOIN object_role r ON ra.objectroleid = r.id " +
                " LEFT JOIN module m ON r.module = m.id " +
                " ORDER BY ra.id DESC";
        List<RoleAssignment> out = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RoleAssignment ra = mapRow(rs);
                out.add(ra);
            }
        }
        return out;
    }

    public RoleAssignment upsert(Integer objectRoleId, String usersJson) throws SQLException {
        // Defensive: ensure usersJson is a JSON array representation
        if (usersJson == null || usersJson.trim().isEmpty()) {
            usersJson = "[]";
        }

        // Validate FK: if objectRoleId provided, ensure it exists to avoid FK violations
        if (objectRoleId != null) {
            try (Connection c = DatabaseConnection.getConnection();
                 PreparedStatement chk = c.prepareStatement("SELECT 1 FROM object_role WHERE id = ?")) {
                chk.setInt(1, objectRoleId);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Invalid objectRoleId: " + objectRoleId + " does not exist in object_role");
                    }
                }
            }
        }

        // Try update existing by objectroleid, else insert
        String select = "SELECT id FROM role_assignment WHERE objectroleid = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement sel = c.prepareStatement(select)) {
            sel.setObject(1, objectRoleId, Types.INTEGER);
            try (ResultSet rs = sel.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String update = "UPDATE role_assignment SET users = ?, lastupdateddate = CURRENT_TIMESTAMP WHERE id = ?";
                    try (PreparedStatement up = c.prepareStatement(update)) {
                        up.setString(1, usersJson);
                        up.setInt(2, id);
                        up.executeUpdate();
                    }
                } else {
                    // compute next id similar to other DAOs
                    int nextId = getNextId(c);
                    String insert = "INSERT INTO role_assignment (id, objectroleid, users, createdate, lastupdateddate) VALUES (?,?,?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
                    try (PreparedStatement ins = c.prepareStatement(insert)) {
                        ins.setInt(1, nextId);
                        if (objectRoleId == null) ins.setNull(2, Types.INTEGER); else ins.setInt(2, objectRoleId);
                        ins.setString(3, usersJson);
                        ins.executeUpdate();
                    }
                }
            }
        }
        // Return latest row by objectroleid
        String sql = "SELECT ra.id, ra.objectroleid, ra.users, ra.createdate, ra.lastupdateddate, " +
                " r.primaryname AS role_name, m.primaryname AS module_name, m.id AS module_id " +
                " FROM role_assignment ra " +
                " LEFT JOIN object_role r ON ra.objectroleid = r.id " +
                " LEFT JOIN module m ON r.module = m.id " +
                " WHERE ra.objectroleid <=> ?"; // NULL-safe
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (objectRoleId == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, objectRoleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public boolean deleteById(int id) throws SQLException {
        String sql = "DELETE FROM role_assignment WHERE id = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private int getNextId(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(id),0)+1 AS next_id FROM role_assignment");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt("next_id");
        }
        return 1;
    }

    private RoleAssignment mapRow(ResultSet rs) throws SQLException {
        RoleAssignment ra = new RoleAssignment();
        ra.setId(rs.getInt("id"));
        ra.setObjectRoleId((Integer) rs.getObject("objectroleid"));
        ra.setUsersJson(rs.getString("users"));
        Timestamp created = rs.getTimestamp("createdate");
        Timestamp updated = rs.getTimestamp("lastupdateddate");
        ra.setCreateDate(created);
        ra.setLastUpdatedDate(updated);
        // Pre-format as yyyy-MM-dd HH:mm:ss to avoid timezone/client differences
        ra.setCreateDateString(created == null ? null : created.toLocalDateTime().toString().replace('T', ' '));
        ra.setLastUpdatedDateString(updated == null ? null : updated.toLocalDateTime().toString().replace('T', ' '));
        ra.setRoleName(rs.getString("role_name"));
        ra.setFacetName(rs.getString("module_name"));
        ra.setModuleId((Integer) rs.getObject("module_id"));
        return ra;
    }
}