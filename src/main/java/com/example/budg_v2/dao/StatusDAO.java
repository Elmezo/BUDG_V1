package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Status;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StatusDAO {

    private static final String SELECT_ALL = "SELECT * FROM status ORDER BY ID";
    private static final String SELECT_BY_ID = "SELECT * FROM status WHERE ID = ?";
    private static final String SELECT_BY_NAME = "SELECT * FROM status WHERE primaryname = ?";
    private static final String SEARCH = "SELECT * FROM status WHERE primaryname LIKE ? OR description LIKE ? ORDER BY ID";
    private static final String SELECT_FOR_DROPDOWN = "SELECT ID, primaryname FROM status ORDER BY ID";
    private static final String INSERT = "INSERT INTO status (primaryname, description, lastupdatedatetime, priority, lastupdateuser_id) VALUES (?, ?, NULL, ?, ?)";
    private static final String UPDATE = "UPDATE status SET primaryname = ?, description = ?, lastupdatedatetime = NOW(), priority = ?, lastupdateuser_id = ? WHERE ID = ?";
    private static final String SOFT_DELETE = "UPDATE status SET deletedate = NOW() WHERE ID = ?";

    public List<Status> getAllStatuses() throws SQLException {
        List<Status> statuses = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                statuses.add(mapResultSetToStatus(rs));
            }
        }
        return statuses;
    }

    public Status getStatusById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToStatus(rs);
            }
        }
        return null;
    }

    public Status getStatusByPrimaryName(String primaryName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_NAME)) {

            pstmt.setString(1, primaryName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToStatus(rs);
            }
        }
        return null;
    }

    public List<Status> searchStatuses(String searchQuery) throws SQLException {
        List<Status> statuses = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                statuses.add(mapResultSetToStatus(rs));
            }
        }
        return statuses;
    }

    public List<Status> getAllStatusesForDropdown() throws SQLException {
        List<Status> statuses = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_FOR_DROPDOWN)) {

            while (rs.next()) {
                Status status = new Status();
                status.setId(rs.getInt("ID"));
                status.setPrimaryName(rs.getString("primaryname"));
                statuses.add(status);
            }
        }
        return statuses;
    }

    public Status createStatus(Status status) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, status.getPrimaryName());
            pstmt.setString(2, status.getDescription());
            pstmt.setInt(3, status.getPriority() != null ? status.getPriority() : 1);
            pstmt.setObject(4, status.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating status failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    status.setId(generatedKeys.getInt(1));
                    return status;
                } else {
                    throw new SQLException("Creating status failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateStatus(Status status) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, status.getPrimaryName());
            pstmt.setString(2, status.getDescription());
            pstmt.setInt(3, status.getPriority() != null ? status.getPriority() : 1);
            pstmt.setObject(4, status.getLastUpdateUserId());
            pstmt.setInt(5, status.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteStatus(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private Status mapResultSetToStatus(ResultSet rs) throws SQLException {
        Status status = new Status();
        status.setId(rs.getInt("ID"));
        status.setPrimaryName(rs.getString("primaryname"));
        status.setDescription(rs.getString("description"));
        status.setLastUpdateDateTime(rs.getTimestamp("lastupdatedatetime"));
        status.setDeleteDate(rs.getTimestamp("deletedate"));
        status.setPriority(rs.getInt("priority"));

        int lastUpdateUserId = rs.getInt("lastupdateuser_id");
        if (!rs.wasNull()) {
            status.setLastUpdateUserId(lastUpdateUserId);
        }
        return status;
    }
}
