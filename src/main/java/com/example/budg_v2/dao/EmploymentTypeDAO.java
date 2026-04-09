package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.EmploymentType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EmploymentTypeDAO {

    private static final String SELECT_ALL = "SELECT * FROM employment_type ORDER BY id";
    private static final String SELECT_BY_ID = "SELECT * FROM employment_type WHERE id = ?";
    private static final String SELECT_BY_NAME = "SELECT * FROM employment_type WHERE primary_Name = ?";
    private static final String SEARCH = "SELECT * FROM employment_type WHERE primary_Name LIKE ? ORDER BY id";
    private static final String SELECT_FOR_DROPDOWN = "SELECT id, primary_Name FROM employment_type ORDER BY id";
    private static final String INSERT = "INSERT INTO employment_type (primary_Name, last_updated_date, last_update_user_id) VALUES (?, NULL, ?)";
    private static final String UPDATE = "UPDATE employment_type SET primary_Name = ?, last_updated_date = NOW(), last_update_user_id = ? WHERE id = ?";

    public List<EmploymentType> getAllEmploymentTypes() throws SQLException {
        List<EmploymentType> employmentTypes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                employmentTypes.add(mapResultSetToEmploymentType(rs));
            }
        }
        return employmentTypes;
    }

    public EmploymentType getEmploymentTypeById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToEmploymentType(rs);
            }
        }
        return null;
    }

    public EmploymentType getEmploymentTypeByPrimaryName(String primaryName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_NAME)) {

            pstmt.setString(1, primaryName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToEmploymentType(rs);
            }
        }
        return null;
    }

    public List<EmploymentType> searchEmploymentTypes(String searchQuery) throws SQLException {
        List<EmploymentType> employmentTypes = new ArrayList<>();
        String searchPattern = "%" + searchQuery + "%";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            pstmt.setString(1, searchPattern);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                employmentTypes.add(mapResultSetToEmploymentType(rs));
            }
        }
        return employmentTypes;
    }

    public List<EmploymentType> getAllEmploymentTypesForDropdown() throws SQLException {
        List<EmploymentType> employmentTypes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_FOR_DROPDOWN)) {

            while (rs.next()) {
                EmploymentType employmentType = new EmploymentType();
                employmentType.setId(rs.getInt("id"));
                employmentType.setPrimaryName(rs.getString("primary_Name"));
                employmentTypes.add(employmentType);
            }
        }
        return employmentTypes;
    }

    public EmploymentType createEmploymentType(String primaryName, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, primaryName);
            pstmt.setInt(2, userId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating employment type failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    EmploymentType employmentType = new EmploymentType();
                    employmentType.setId(generatedKeys.getInt(1));
                    employmentType.setPrimaryName(primaryName);
                    employmentType.setLastUpdateUserId(userId);
                    return employmentType;
                } else {
                    throw new SQLException("Creating employment type failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateEmploymentType(int id, String primaryName, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setString(1, primaryName);
            pstmt.setInt(2, userId);
            pstmt.setInt(3, id);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    private EmploymentType mapResultSetToEmploymentType(ResultSet rs) throws SQLException {
        EmploymentType employmentType = new EmploymentType();
        employmentType.setId(rs.getInt("id"));
        employmentType.setPrimaryName(rs.getString("primary_Name"));
        employmentType.setLastUpdatedDate(rs.getTimestamp("last_updated_date"));
        employmentType.setLastUpdateUserId(rs.getInt("last_update_user_id"));
        return employmentType;
    }
}
