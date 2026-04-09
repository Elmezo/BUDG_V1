package com.example.budg_v2.dao;

import com.example.budg_v2.model.RegulationStage;
import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RegulationStageDAO {

    private static final String INSERT_SQL = 
        "INSERT INTO Regulation_Stage (PrimaryName, Description, LastUpdateDatetime, LastUpdate_UserID) VALUES (?, ?, ?, ?)";
    
    private static final String SELECT_ALL_SQL = 
        "SELECT ID, PrimaryName, Description, LastUpdateDatetime, LastUpdate_UserID FROM Regulation_Stage ORDER BY PrimaryName";
    
    private static final String SELECT_BY_ID_SQL = 
        "SELECT ID, PrimaryName, Description, LastUpdateDatetime, LastUpdate_UserID FROM Regulation_Stage WHERE ID = ?";
    
    private static final String UPDATE_SQL = 
        "UPDATE Regulation_Stage SET PrimaryName = ?, Description = ?, LastUpdateDatetime = ?, LastUpdate_UserID = ? WHERE ID = ?";
    
    private static final String DELETE_SQL = 
        "DELETE FROM Regulation_Stage WHERE ID = ?";

    public RegulationStage createRegulationStage(RegulationStage regulationStage) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, regulationStage.getPrimaryName());
            pstmt.setString(2, regulationStage.getDescription());
            pstmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            if (regulationStage.getLastUpdateUserId() != null) {
                pstmt.setInt(4, regulationStage.getLastUpdateUserId());
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }
            
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    regulationStage.setId(generatedKeys.getInt(1));
                }
            }
            
            return regulationStage;
        }
    }

    public List<RegulationStage> getAllRegulationStages() throws SQLException {
        List<RegulationStage> regulationStages = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                RegulationStage regulationStage = mapResultSetToRegulationStage(rs);
                regulationStages.add(regulationStage);
            }
        }
        
        return regulationStages;
    }

    public RegulationStage getRegulationStageById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
            
            pstmt.setInt(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRegulationStage(rs);
                }
            }
        }
        
        return null;
    }

    public RegulationStage updateRegulationStage(RegulationStage regulationStage) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_SQL)) {
            
            pstmt.setString(1, regulationStage.getPrimaryName());
            pstmt.setString(2, regulationStage.getDescription());
            pstmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            if (regulationStage.getLastUpdateUserId() != null) {
                pstmt.setInt(4, regulationStage.getLastUpdateUserId());
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }
            pstmt.setInt(5, regulationStage.getId());
            
            pstmt.executeUpdate();
            return regulationStage;
        }
    }

    public boolean deleteRegulationStage(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE_SQL)) {
            
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private RegulationStage mapResultSetToRegulationStage(ResultSet rs) throws SQLException {
        RegulationStage regulationStage = new RegulationStage();
        regulationStage.setId(rs.getInt("ID"));
        regulationStage.setPrimaryName(rs.getString("PrimaryName"));
        regulationStage.setDescription(rs.getString("Description"));
        
        Timestamp lastUpdateTimestamp = rs.getTimestamp("LastUpdateDatetime");
        if (lastUpdateTimestamp != null) {
            regulationStage.setLastUpdateDatetime(lastUpdateTimestamp.toLocalDateTime());
        }
        
        int lastUpdateUserId = rs.getInt("LastUpdate_UserID");
        if (!rs.wasNull()) {
            regulationStage.setLastUpdateUserId(lastUpdateUserId);
        }
        
        return regulationStage;
    }
}
