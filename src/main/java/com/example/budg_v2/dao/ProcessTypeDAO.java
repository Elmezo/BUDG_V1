package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProcessType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProcessTypeDAO {

    private static final String SELECT_ALL = "SELECT * FROM process_type ORDER BY ID";
    private static final String SELECT_BY_ID = "SELECT * FROM process_type WHERE ID = ?";
    private static final String SELECT_BY_NAME = "SELECT * FROM process_type WHERE PrimaryName = ?";
    private static final String SEARCH = "SELECT * FROM process_type WHERE PrimaryName LIKE ? OR Description LIKE ? ORDER BY ID";
    private static final String SELECT_FOR_DROPDOWN = "SELECT ID, PrimaryName FROM process_type ORDER BY ID";
    private static final String INSERT = "INSERT INTO process_type (PrimaryName, Description, LastUpdateDatetime, LastUpdateUser_ID) VALUES (?, ?, NULL, ?)";
    private static final String UPDATE = "UPDATE process_type SET PrimaryName = ?, Description = ?, LastUpdateDatetime = NOW(), LastUpdateUser_ID = ? WHERE ID = ?";
    private static final String SOFT_DELETE = "UPDATE process_type SET deletedate = NOW() WHERE ID = ?";

    public List<ProcessType> getAllProcessTypes() throws SQLException {
        List<ProcessType> types = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                types.add(mapResultSetToProcessType(rs));
            }
        }
        return types;
    }

    public ProcessType getProcessTypeById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcessType(rs);
            }
        }
        return null;
    }

    public ProcessType getProcessTypeByPrimaryName(String primaryName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_NAME)) {

            pstmt.setString(1, primaryName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcessType(rs);
            }
        }
        return null;
    }

    public List<ProcessType> searchProcessTypes(String searchQuery) throws SQLException {
        List<ProcessType> types = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                types.add(mapResultSetToProcessType(rs));
            }
        }
        return types;
    }

    public List<ProcessType> getAllProcessTypesForDropdown() throws SQLException {
        List<ProcessType> types = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_FOR_DROPDOWN)) {

            while (rs.next()) {
                ProcessType type = new ProcessType();
                type.setId(rs.getInt("ID"));
                type.setPrimaryName(rs.getString("PrimaryName"));
                types.add(type);
            }
        }
        return types;
    }

    public ProcessType createProcessType(ProcessType type) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, type.getPrimaryName());
            pstmt.setString(2, type.getDescription());
            pstmt.setObject(3, type.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating process type failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    type.setId(generatedKeys.getInt(1));
                    return type;
                } else {
                    throw new SQLException("Creating process type failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateProcessType(ProcessType type) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, type.getPrimaryName());
            pstmt.setString(2, type.getDescription());
            pstmt.setObject(3, type.getLastUpdateUserId());
            pstmt.setInt(4, type.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteProcessType(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private ProcessType mapResultSetToProcessType(ResultSet rs) throws SQLException {
        ProcessType type = new ProcessType();
        type.setId(rs.getInt("ID"));
        type.setPrimaryName(rs.getString("PrimaryName"));
        type.setDescription(rs.getString("Description"));
        type.setLastUpdateDateTime(rs.getTimestamp("LastUpdateDatetime"));

        int lastUpdateUserId = rs.getInt("LastUpdateUser_ID");
        if (!rs.wasNull()) {
            type.setLastUpdateUserId(lastUpdateUserId);
        }
        return type;
    }
}
