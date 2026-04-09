package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.PeopleLifecycleStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PeopleLifecycleStatusDAO {

    private static final String SELECT_ALL = "SELECT * FROM people_lifecycle_status ORDER BY id";
    private static final String SELECT_BY_ID = "SELECT * FROM people_lifecycle_status WHERE id = ?";
    private static final String SELECT_BY_NAME = "SELECT * FROM people_lifecycle_status WHERE primary_Name = ?";
    private static final String SEARCH = "SELECT * FROM people_lifecycle_status WHERE primary_Name LIKE ? OR Description LIKE ? ORDER BY id";
    private static final String SELECT_FOR_DROPDOWN = "SELECT id, primary_Name FROM people_lifecycle_status ORDER BY id";
    private static final String INSERT = "INSERT INTO people_lifecycle_status (primary_Name, Description, last_updated_date, last_update_user_id) VALUES (?, ?, NULL, ?)";
    private static final String UPDATE = "UPDATE people_lifecycle_status SET primary_Name = ?, Description = ?, last_updated_date = NOW(), last_update_user_id = ? WHERE id = ?";

    public List<PeopleLifecycleStatus> getAllPeopleLifecycleStatuses() throws SQLException {
        List<PeopleLifecycleStatus> statuses = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                statuses.add(mapResultSetToPeopleLifecycleStatus(rs));
            }
        }
        return statuses;
    }

    public PeopleLifecycleStatus getPeopleLifecycleStatusById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToPeopleLifecycleStatus(rs);
            }
        }
        return null;
    }

    public PeopleLifecycleStatus getPeopleLifecycleStatusByPrimaryName(String primaryName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_NAME)) {

            pstmt.setString(1, primaryName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToPeopleLifecycleStatus(rs);
            }
        }
        return null;
    }

    public List<PeopleLifecycleStatus> searchPeopleLifecycleStatuses(String searchQuery) throws SQLException {
        List<PeopleLifecycleStatus> statuses = new ArrayList<>();
        String searchPattern = "%" + searchQuery + "%";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                statuses.add(mapResultSetToPeopleLifecycleStatus(rs));
            }
        }
        return statuses;
    }

    public List<PeopleLifecycleStatus> getAllPeopleLifecycleStatusesForDropdown() throws SQLException {
        List<PeopleLifecycleStatus> statuses = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_FOR_DROPDOWN)) {

            while (rs.next()) {
                PeopleLifecycleStatus status = new PeopleLifecycleStatus();
                status.setId(rs.getInt("id"));
                status.setPrimaryName(rs.getString("primary_Name"));
                statuses.add(status);
            }
        }
        return statuses;
    }

    public PeopleLifecycleStatus createPeopleLifecycleStatus(String primaryName, String description, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, primaryName);
            pstmt.setString(2, description);
            pstmt.setInt(3, userId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating people lifecycle status failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    PeopleLifecycleStatus status = new PeopleLifecycleStatus();
                    status.setId(generatedKeys.getInt(1));
                    status.setPrimaryName(primaryName);
                    status.setDescription(description);
                    status.setLastUpdateUserId(userId);
                    return status;
                } else {
                    throw new SQLException("Creating people lifecycle status failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updatePeopleLifecycleStatus(int id, String primaryName, String description, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, primaryName);
            pstmt.setString(2, description);
            pstmt.setInt(3, userId);
            pstmt.setInt(4, id);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    private PeopleLifecycleStatus mapResultSetToPeopleLifecycleStatus(ResultSet rs) throws SQLException {
        PeopleLifecycleStatus status = new PeopleLifecycleStatus();
        status.setId(rs.getInt("id"));
        status.setPrimaryName(rs.getString("primary_Name"));
        status.setDescription(rs.getString("Description"));
        status.setLastUpdatedDate(rs.getTimestamp("last_updated_date"));
        status.setLastUpdateUserId(rs.getInt("last_update_user_id"));
        return status;
    }
}
