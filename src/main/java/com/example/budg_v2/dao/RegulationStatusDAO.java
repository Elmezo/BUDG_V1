package com.example.budg_v2.dao;

import com.example.budg_v2.model.RegulationStatus;
import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RegulationStatusDAO {

    private static final String INSERT_SQL = 
        "INSERT INTO Regulation_Status (PrimaryName, Description, LastUpdateDatetime, LastUpdate_UserID) VALUES (?, ?, ?, ?)";
    
    private static final String SELECT_ALL_SQL = 
        "SELECT ID, PrimaryName, Description, LastUpdateDatetime, LastUpdate_UserID FROM Regulation_Status ORDER BY PrimaryName";
    
    private static final String SELECT_BY_ID_SQL = 
        "SELECT ID, PrimaryName, Description, LastUpdateDatetime, LastUpdate_UserID FROM Regulation_Status WHERE ID = ?";
    
    private static final String UPDATE_SQL = 
        "UPDATE Regulation_Status SET PrimaryName = ?, Description = ?, LastUpdateDatetime = ?, LastUpdate_UserID = ? WHERE ID = ?";
    
    private static final String DELETE_SQL = 
        "DELETE FROM Regulation_Status WHERE ID = ?";

    public RegulationStatus createRegulationStatus(RegulationStatus regulationStatus) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, regulationStatus.getPrimaryName());
            pstmt.setString(2, regulationStatus.getDescription());
            pstmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            if (regulationStatus.getLastUpdateUserId() != null) {
                pstmt.setInt(4, regulationStatus.getLastUpdateUserId());
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }
            
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    regulationStatus.setId(generatedKeys.getInt(1));
                }
            }
            
            return regulationStatus;
        }
    }

    public List<RegulationStatus> getAllRegulationStatuses() throws SQLException {
        List<RegulationStatus> regulationStatuses = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                RegulationStatus regulationStatus = mapResultSetToRegulationStatus(rs);
                regulationStatuses.add(regulationStatus);
            }
        }
        
        return regulationStatuses;
    }

    public RegulationStatus getRegulationStatusById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
            
            pstmt.setInt(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRegulationStatus(rs);
                }
            }
        }
        
        return null;
    }

    public RegulationStatus updateRegulationStatus(RegulationStatus regulationStatus) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_SQL)) {
            
            pstmt.setString(1, regulationStatus.getPrimaryName());
            pstmt.setString(2, regulationStatus.getDescription());
            pstmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            if (regulationStatus.getLastUpdateUserId() != null) {
                pstmt.setInt(4, regulationStatus.getLastUpdateUserId());
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }
            pstmt.setInt(5, regulationStatus.getId());
            
            pstmt.executeUpdate();
            return regulationStatus;
        }
    }

    public boolean deleteRegulationStatus(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE_SQL)) {
            
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private RegulationStatus mapResultSetToRegulationStatus(ResultSet rs) throws SQLException {
        RegulationStatus regulationStatus = new RegulationStatus();
        regulationStatus.setId(rs.getInt("ID"));
        regulationStatus.setPrimaryName(rs.getString("PrimaryName"));
        regulationStatus.setDescription(rs.getString("Description"));
        
        Timestamp lastUpdateTimestamp = rs.getTimestamp("LastUpdateDatetime");
        if (lastUpdateTimestamp != null) {
            regulationStatus.setLastUpdateDatetime(lastUpdateTimestamp.toLocalDateTime());
        }
        
        int lastUpdateUserId = rs.getInt("LastUpdate_UserID");
        if (!rs.wasNull()) {
            regulationStatus.setLastUpdateUserId(lastUpdateUserId);
        }
        
        return regulationStatus;
    }
}
