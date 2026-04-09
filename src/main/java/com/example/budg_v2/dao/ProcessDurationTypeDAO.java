package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProcessDurationType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProcessDurationTypeDAO {

    private static final String SELECT_ALL = "SELECT * FROM process_duration_type WHERE DeletedDatetime IS NULL ORDER BY Priority, ID";
    private static final String SELECT_BY_ID = "SELECT * FROM process_duration_type WHERE ID = ? AND DeletedDatetime IS NULL";
    private static final String SELECT_BY_NAME = "SELECT * FROM process_duration_type WHERE PrimaryName = ? AND DeletedDatetime IS NULL";
    private static final String SEARCH = "SELECT * FROM process_duration_type WHERE (PrimaryName LIKE ? OR Description LIKE ?) AND DeletedDatetime IS NULL ORDER BY Priority, ID";
    private static final String SELECT_FOR_DROPDOWN = "SELECT ID, PrimaryName FROM process_duration_type WHERE DeletedDatetime IS NULL ORDER BY Priority, ID";
    private static final String INSERT = "INSERT INTO process_duration_type (PrimaryName, Description, Priority, LastUpdateDatetime, LastUpdateUser_ID) VALUES (?, ?, ?, NULL, ?)";
    private static final String UPDATE = "UPDATE process_duration_type SET PrimaryName = ?, Description = ?, Priority = ?, LastUpdateDatetime = NOW(), LastUpdateUser_ID = ? WHERE ID = ?";
    private static final String SOFT_DELETE = "UPDATE process_duration_type SET DeletedDatetime = NOW() WHERE ID = ?";

    public List<ProcessDurationType> getAllProcessDurationTypes() throws SQLException {
        List<ProcessDurationType> durationTypes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                durationTypes.add(mapResultSetToProcessDurationType(rs));
            }
        }
        return durationTypes;
    }

    public ProcessDurationType getProcessDurationTypeById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcessDurationType(rs);
            }
        }
        return null;
    }

    public ProcessDurationType getProcessDurationTypeByPrimaryName(String primaryName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_NAME)) {

            pstmt.setString(1, primaryName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcessDurationType(rs);
            }
        }
        return null;
    }

    public List<ProcessDurationType> searchProcessDurationTypes(String searchQuery) throws SQLException {
        List<ProcessDurationType> durationTypes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                durationTypes.add(mapResultSetToProcessDurationType(rs));
            }
        }
        return durationTypes;
    }

    public List<ProcessDurationType> getAllProcessDurationTypesForDropdown() throws SQLException {
        List<ProcessDurationType> durationTypes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_FOR_DROPDOWN)) {

            while (rs.next()) {
                ProcessDurationType durationType = new ProcessDurationType();
                durationType.setId(rs.getInt("ID"));
                durationType.setPrimaryName(rs.getString("PrimaryName"));
                durationTypes.add(durationType);
            }
        }
        return durationTypes;
    }

    public ProcessDurationType createProcessDurationType(ProcessDurationType durationType) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, durationType.getPrimaryName());
            pstmt.setString(2, durationType.getDescription());
            pstmt.setInt(3, durationType.getPriority() != null ? durationType.getPriority() : 1);
            pstmt.setObject(4, durationType.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating process duration type failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    durationType.setId(generatedKeys.getInt(1));
                    return durationType;
                } else {
                    throw new SQLException("Creating process duration type failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateProcessDurationType(ProcessDurationType durationType) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, durationType.getPrimaryName());
            pstmt.setString(2, durationType.getDescription());
            pstmt.setInt(3, durationType.getPriority() != null ? durationType.getPriority() : 1);
            pstmt.setObject(4, durationType.getLastUpdateUserId());
            pstmt.setInt(5, durationType.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteProcessDurationType(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private ProcessDurationType mapResultSetToProcessDurationType(ResultSet rs) throws SQLException {
        ProcessDurationType durationType = new ProcessDurationType();
        durationType.setId(rs.getInt("ID"));
        durationType.setPrimaryName(rs.getString("PrimaryName"));
        durationType.setDescription(rs.getString("Description"));
        durationType.setPriority(rs.getInt("Priority"));
        durationType.setLastUpdateDateTime(rs.getTimestamp("LastUpdateDatetime"));
        durationType.setDeletedDateTime(rs.getTimestamp("DeletedDatetime"));

        int lastUpdateUserId = rs.getInt("LastUpdateUser_ID");
        if (!rs.wasNull()) {
            durationType.setLastUpdateUserId(lastUpdateUserId);
        }
        return durationType;
    }
}
