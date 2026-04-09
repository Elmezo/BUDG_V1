package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ChangeRequestAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for ChangeRequestAnalysis operations
 */
public class ChangeRequestAnalysisDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(ChangeRequestAnalysisDAO.class);

    /**
     * Create a new analysis entry
     */
    public Integer createAnalysis(ChangeRequestAnalysis analysis) throws SQLException {
        String sql = "INSERT INTO changerequest_analysis (ChangeRequest_ID, Description, LastUserChange, Created_At, Updated_At) " +
                    "VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, analysis.getChangeRequestId());
            stmt.setString(2, analysis.getAnalysis());
            stmt.setInt(3, analysis.getLastUserChange());
            stmt.setTimestamp(4, Timestamp.valueOf(analysis.getCreatedAt()));
            stmt.setTimestamp(5, Timestamp.valueOf(analysis.getUpdatedAt()));
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating analysis failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating analysis failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Get all analysis entries for a change request
     */
    public List<ChangeRequestAnalysis> getAnalysisByChangeRequestId(int changeRequestId) throws SQLException {
        List<ChangeRequestAnalysis> analysisList = new ArrayList<>();
        String sql = "SELECT * FROM changerequest_analysis WHERE ChangeRequest_ID = ? ORDER BY Created_At DESC";
        
        logger.info("Fetching analysis for ChangeRequest_ID: {}", changeRequestId);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, changeRequestId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChangeRequestAnalysis analysis = new ChangeRequestAnalysis();
                    analysis.setId(rs.getInt("ID"));
                    analysis.setChangeRequestId(rs.getInt("ChangeRequest_ID"));
                    analysis.setAnalysis(rs.getString("Description"));
                    analysis.setLastUserChange(rs.getInt("LastUserChange"));
                    
                    Timestamp createdAt = rs.getTimestamp("Created_At");
                    if (createdAt != null) {
                        analysis.setCreatedAt(createdAt.toLocalDateTime());
                    }
                    
                    Timestamp updatedAt = rs.getTimestamp("Updated_At");
                    if (updatedAt != null) {
                        analysis.setUpdatedAt(updatedAt.toLocalDateTime());
                    }
                    
                    analysisList.add(analysis);
                    logger.info("Found analysis ID: {}, Description: {}", analysis.getId(), analysis.getAnalysis());
                }
            }
        }
        
        logger.info("Total analysis entries found: {}", analysisList.size());
        return analysisList;
    }

    /**
     * Update an analysis entry
     */
    public boolean updateAnalysis(ChangeRequestAnalysis analysis) throws SQLException {
        String sql = "UPDATE changerequest_analysis SET Description = ?, LastUserChange = ?, Updated_At = ? WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, analysis.getAnalysis());
            stmt.setInt(2, analysis.getLastUserChange());
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(4, analysis.getId());
            
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Delete an analysis entry
     */
    public boolean deleteAnalysis(int analysisId) throws SQLException {
        String sql = "DELETE FROM changerequest_analysis WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, analysisId);
            
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Check if analysis exists for a change request
     */
    public boolean hasAnalysisForChangeRequest(int changeRequestId) throws SQLException {
        String sql = "SELECT COUNT(*) as cnt FROM changerequest_analysis WHERE ChangeRequest_ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, changeRequestId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt") > 0;
                }
            }
        }
        
        return false;
    }

    /**
     * Get analysis by ID to verify it exists and belongs to the change request
     */
    public ChangeRequestAnalysis getAnalysisById(int analysisId) throws SQLException {
        String sql = "SELECT * FROM changerequest_analysis WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, analysisId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ChangeRequestAnalysis analysis = new ChangeRequestAnalysis();
                    analysis.setId(rs.getInt("ID"));
                    analysis.setChangeRequestId(rs.getInt("ChangeRequest_ID"));
                    analysis.setAnalysis(rs.getString("Description"));
                    analysis.setLastUserChange(rs.getInt("LastUserChange"));
                    
                    Timestamp createdAt = rs.getTimestamp("Created_At");
                    if (createdAt != null) {
                        analysis.setCreatedAt(createdAt.toLocalDateTime());
                    }
                    
                    Timestamp updatedAt = rs.getTimestamp("Updated_At");
                    if (updatedAt != null) {
                        analysis.setUpdatedAt(updatedAt.toLocalDateTime());
                    }
                    
                    return analysis;
                }
            }
        }
        
        return null;
    }
}
