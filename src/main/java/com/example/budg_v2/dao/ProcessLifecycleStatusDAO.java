package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProcessLifecycleStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProcessLifecycleStatusDAO {

    private static final String SELECT_ALL = "SELECT * FROM process_lifecycle_status ORDER BY id";
    private static final String SELECT_BY_ID = "SELECT * FROM process_lifecycle_status WHERE id = ?";
    private static final String SELECT_BY_NAME = "SELECT * FROM process_lifecycle_status WHERE primaryname = ?";
    private static final String SEARCH = "SELECT * FROM process_lifecycle_status WHERE primaryname LIKE ? OR description LIKE ? ORDER BY id";
    private static final String SELECT_FOR_DROPDOWN = "SELECT id, primaryname FROM process_lifecycle_status ORDER BY id";
    private static final String INSERT = "INSERT INTO process_lifecycle_status (primaryname, description, lastupdatedatetime, lastupdateuser_id) VALUES (?, ?, NULL, ?)";
    private static final String UPDATE = "UPDATE process_lifecycle_status SET primaryname = ?, description = ?, lastupdatedatetime = NOW(), lastupdateuser_id = ? WHERE id = ?";
    private static final String SOFT_DELETE = "UPDATE process_lifecycle_status SET deletedate = NOW() WHERE id = ?";

    public List<ProcessLifecycleStatus> getAllProcessLifecycleStatuses() throws SQLException {
        List<ProcessLifecycleStatus> statuses = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                statuses.add(mapResultSetToProcessLifecycleStatus(rs));
            }
        }
        return statuses;
    }

    public ProcessLifecycleStatus getProcessLifecycleStatusById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcessLifecycleStatus(rs);
            }
        }
        return null;
    }

    public ProcessLifecycleStatus getProcessLifecycleStatusByPrimaryName(String primaryName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_NAME)) {

            pstmt.setString(1, primaryName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcessLifecycleStatus(rs);
            }
        }
        return null;
    }

    public List<ProcessLifecycleStatus> searchProcessLifecycleStatuses(String searchQuery) throws SQLException {
        List<ProcessLifecycleStatus> statuses = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                statuses.add(mapResultSetToProcessLifecycleStatus(rs));
            }
        }
        return statuses;
    }

    public List<ProcessLifecycleStatus> getAllProcessLifecycleStatusesForDropdown() throws SQLException {
        List<ProcessLifecycleStatus> statuses = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_FOR_DROPDOWN)) {

            while (rs.next()) {
                ProcessLifecycleStatus status = new ProcessLifecycleStatus();
                status.setId(rs.getInt("id"));
                status.setPrimaryName(rs.getString("primaryname"));
                statuses.add(status);
            }
        }
        return statuses;
    }

    public ProcessLifecycleStatus createProcessLifecycleStatus(ProcessLifecycleStatus status) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, status.getPrimaryName());
            pstmt.setString(2, status.getDescription());
            pstmt.setObject(3, status.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating process lifecycle status failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    status.setId(generatedKeys.getInt(1));
                    return status;
                } else {
                    throw new SQLException("Creating process lifecycle status failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateProcessLifecycleStatus(ProcessLifecycleStatus status) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, status.getPrimaryName());
            pstmt.setString(2, status.getDescription());
            pstmt.setObject(3, status.getLastUpdateUserId());
            pstmt.setInt(4, status.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteProcessLifecycleStatus(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private ProcessLifecycleStatus mapResultSetToProcessLifecycleStatus(ResultSet rs) throws SQLException {
        ProcessLifecycleStatus status = new ProcessLifecycleStatus();
        status.setId(rs.getInt("id"));
        status.setPrimaryName(rs.getString("primaryname"));
        status.setDescription(rs.getString("description"));
        status.setLastUpdateDateTime(rs.getTimestamp("lastupdatedatetime"));

        int lastUpdateUserId = rs.getInt("lastupdateuser_id");
        if (!rs.wasNull()) {
            status.setLastUpdateUserId(lastUpdateUserId);
        }
        return status;
    }
}
