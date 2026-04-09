package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProcessClass;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProcessClassDAO {

    private static final String SELECT_ALL = "SELECT * FROM process_class ORDER BY id";
    private static final String SELECT_BY_ID = "SELECT * FROM process_class WHERE id = ?";
    private static final String SELECT_BY_NAME = "SELECT * FROM process_class WHERE primaryname = ?";
    private static final String SEARCH = "SELECT * FROM process_class WHERE primaryname LIKE ? OR description LIKE ? ORDER BY id";
    private static final String SELECT_FOR_DROPDOWN = "SELECT id, primaryname FROM process_class ORDER BY id";
    private static final String INSERT = "INSERT INTO process_class (primaryname, description, lastupdatedatetime, lastupdateuser_id) VALUES (?, ?, NULL, ?)";
    private static final String UPDATE = "UPDATE process_class SET primaryname = ?, description = ?, lastupdatedatetime = NOW(), lastupdateuser_id = ? WHERE id = ?";
    private static final String SOFT_DELETE = "UPDATE process_class SET deletedate = NOW() WHERE id = ?";

    public List<ProcessClass> getAllProcessClasses() throws SQLException {
        List<ProcessClass> classes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                classes.add(mapResultSetToProcessClass(rs));
            }
        }
        return classes;
    }

    public ProcessClass getProcessClassById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcessClass(rs);
            }
        }
        return null;
    }

    public ProcessClass getProcessClassByPrimaryName(String primaryName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_NAME)) {

            pstmt.setString(1, primaryName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcessClass(rs);
            }
        }
        return null;
    }

    public List<ProcessClass> searchProcessClasses(String searchQuery) throws SQLException {
        List<ProcessClass> classes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                classes.add(mapResultSetToProcessClass(rs));
            }
        }
        return classes;
    }

    public List<ProcessClass> getAllProcessClassesForDropdown() throws SQLException {
        List<ProcessClass> classes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_FOR_DROPDOWN)) {

            while (rs.next()) {
                ProcessClass processClass = new ProcessClass();
                processClass.setId(rs.getInt("id"));
                processClass.setPrimaryName(rs.getString("primaryname"));
                classes.add(processClass);
            }
        }
        return classes;
    }

    public ProcessClass createProcessClass(ProcessClass processClass) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, processClass.getPrimaryName());
            pstmt.setString(2, processClass.getDescription());
            pstmt.setObject(3, processClass.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating process class failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    processClass.setId(generatedKeys.getInt(1));
                    return processClass;
                } else {
                    throw new SQLException("Creating process class failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateProcessClass(ProcessClass processClass) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, processClass.getPrimaryName());
            pstmt.setString(2, processClass.getDescription());
            pstmt.setObject(3, processClass.getLastUpdateUserId());
            pstmt.setInt(4, processClass.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteProcessClass(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private ProcessClass mapResultSetToProcessClass(ResultSet rs) throws SQLException {
        ProcessClass processClass = new ProcessClass();
        processClass.setId(rs.getInt("id"));
        processClass.setPrimaryName(rs.getString("primaryname"));
        processClass.setDescription(rs.getString("description"));
        processClass.setLastUpdateDateTime(rs.getTimestamp("lastupdatedatetime"));

        int lastUpdateUserId = rs.getInt("lastupdateuser_id");
        if (!rs.wasNull()) {
            processClass.setLastUpdateUserId(lastUpdateUserId);
        }
        return processClass;
    }
}
