package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProcessAutomation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProcessAutomationDAO {

    private static final String SELECT_ALL = "SELECT * FROM process_automation ORDER BY id";
    private static final String SELECT_BY_ID = "SELECT * FROM process_automation WHERE id = ?";
    private static final String SELECT_BY_NAME = "SELECT * FROM process_automation WHERE primaryname = ?";
    private static final String SEARCH = "SELECT * FROM process_automation WHERE primaryname LIKE ? OR description LIKE ? ORDER BY id";
    private static final String SELECT_FOR_DROPDOWN = "SELECT id, primaryname FROM process_automation ORDER BY id";
    private static final String INSERT = "INSERT INTO process_automation (primaryname, description, lastupdatedatetime, lastupdateuser_id) VALUES (?, ?, NULL, ?)";
    private static final String UPDATE = "UPDATE process_automation SET primaryname = ?, description = ?, lastupdatedatetime = NOW(), lastupdateuser_id = ? WHERE id = ?";
    private static final String SOFT_DELETE = "UPDATE process_automation SET deletedate = NOW() WHERE id = ?";

    public List<ProcessAutomation> getAllProcessAutomations() throws SQLException {
        List<ProcessAutomation> automations = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                automations.add(mapResultSetToProcessAutomation(rs));
            }
        }
        return automations;
    }

    public ProcessAutomation getProcessAutomationById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcessAutomation(rs);
            }
        }
        return null;
    }

    public ProcessAutomation getProcessAutomationByPrimaryName(String primaryName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_NAME)) {

            pstmt.setString(1, primaryName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcessAutomation(rs);
            }
        }
        return null;
    }

    public List<ProcessAutomation> searchProcessAutomations(String searchQuery) throws SQLException {
        List<ProcessAutomation> automations = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                automations.add(mapResultSetToProcessAutomation(rs));
            }
        }
        return automations;
    }

    public List<ProcessAutomation> getAllProcessAutomationsForDropdown() throws SQLException {
        List<ProcessAutomation> automations = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_FOR_DROPDOWN)) {

            while (rs.next()) {
                ProcessAutomation automation = new ProcessAutomation();
                automation.setId(rs.getInt("id"));
                automation.setPrimaryName(rs.getString("primaryname"));
                automations.add(automation);
            }
        }
        return automations;
    }

    public ProcessAutomation createProcessAutomation(ProcessAutomation automation) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, automation.getPrimaryName());
            pstmt.setString(2, automation.getDescription());
            pstmt.setObject(3, automation.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating process automation failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    automation.setId(generatedKeys.getInt(1));
                    return automation;
                } else {
                    throw new SQLException("Creating process automation failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateProcessAutomation(ProcessAutomation automation) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, automation.getPrimaryName());
            pstmt.setString(2, automation.getDescription());
            pstmt.setObject(3, automation.getLastUpdateUserId());
            pstmt.setInt(4, automation.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteProcessAutomation(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private ProcessAutomation mapResultSetToProcessAutomation(ResultSet rs) throws SQLException {
        ProcessAutomation automation = new ProcessAutomation();
        automation.setId(rs.getInt("id"));
        automation.setPrimaryName(rs.getString("primaryname"));
        automation.setDescription(rs.getString("description"));
        automation.setLastUpdateDateTime(rs.getTimestamp("lastupdatedatetime"));

        int lastUpdateUserId = rs.getInt("lastupdateuser_id");
        if (!rs.wasNull()) {
            automation.setLastUpdateUserId(lastUpdateUserId);
        }
        return automation;
    }
}
