package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ChangeRequestResolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for ChangeRequestResolution operations
 */
public class ChangeRequestResolutionDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(ChangeRequestResolutionDAO.class);

    /**
     * Create a new resolution entry
     */
    public Integer createResolution(ChangeRequestResolution resolution) throws SQLException {
        String sql = "INSERT INTO changerequest_resolution (ChangeRequest_ID, CR_ResolutionStatus_ID, Description, LastUserChange, Created_At, Updated_At) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, resolution.getChangeRequestId());
            // Handle null resolution status ID
            if (resolution.getResolutionStatusId() != null) {
                stmt.setInt(2, resolution.getResolutionStatusId());
            } else {
                stmt.setNull(2, java.sql.Types.INTEGER);
            }
            stmt.setString(3, resolution.getDescription());
            stmt.setInt(4, resolution.getLastUserChange());
            stmt.setTimestamp(5, Timestamp.valueOf(resolution.getCreatedAt()));
            stmt.setTimestamp(6, Timestamp.valueOf(resolution.getUpdatedAt()));
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating resolution failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int resolutionId = generatedKeys.getInt(1);
                    insertResolutionHistory(resolution.getChangeRequestId(), null, resolution.getDescription(),
                            resolution.getLastUserChange());
                    return resolutionId;
                } else {
                    throw new SQLException("Creating resolution failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Get all resolution entries for a change request with status names
     */
    public List<ChangeRequestResolution> getResolutionsByChangeRequestId(int changeRequestId) throws SQLException {
        List<ChangeRequestResolution> resolutionList = new ArrayList<>();
        String sql = "SELECT cr.*, crs.PrimaryName as StatusName " +
                    "FROM changerequest_resolution cr " +
                    "LEFT JOIN changerequest_resolution_status crs ON cr.CR_ResolutionStatus_ID = crs.ID " +
                    "WHERE cr.ChangeRequest_ID = ? ORDER BY cr.Created_At DESC";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, changeRequestId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChangeRequestResolution resolution = new ChangeRequestResolution();
                    resolution.setId(rs.getInt("ID"));
                    resolution.setChangeRequestId(rs.getInt("ChangeRequest_ID"));
                    
                    // Handle NULL resolution status ID properly
                    int statusId = rs.getInt("CR_ResolutionStatus_ID");
                    if (rs.wasNull()) {
                        resolution.setResolutionStatusId(null);
                    } else {
                        resolution.setResolutionStatusId(statusId);
                    }
                    
                    resolution.setDescription(rs.getString("Description"));
                    resolution.setLastUserChange(rs.getInt("LastUserChange"));
                    
                    Timestamp createdAt = rs.getTimestamp("Created_At");
                    if (createdAt != null) {
                        resolution.setCreatedAt(createdAt.toLocalDateTime());
                    }
                    
                    Timestamp updatedAt = rs.getTimestamp("Updated_At");
                    if (updatedAt != null) {
                        resolution.setUpdatedAt(updatedAt.toLocalDateTime());
                    }
                    
                    resolution.setStatusName(rs.getString("StatusName"));
                    
                    resolutionList.add(resolution);
                }
            }
        }
        
        return resolutionList;
    }

    /**
     * Get all resolution statuses
     */
    public List<ChangeRequestResolution> getAllResolutionStatuses() throws SQLException {
        List<ChangeRequestResolution> statusList = new ArrayList<>();
        // Get all resolution statuses from the table
        // If Status column exists and has value 'Enabled', filter by it; otherwise get all
        String sql = "SELECT ID, PrimaryName FROM changerequest_resolution_status";
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Try to check if Status column exists
            boolean hasStatusColumn = false;
            try {
                java.sql.DatabaseMetaData metaData = conn.getMetaData();
                try (java.sql.ResultSet columns = metaData.getColumns(null, null, "changerequest_resolution_status", "Status")) {
                    hasStatusColumn = columns.next();
                }
            } catch (Exception e) {
                logger.debug("Could not check for Status column: {}", e.getMessage());
            }
            
            if (hasStatusColumn) {
                sql += " WHERE Status = 'Enabled'";
                logger.info("Filtering resolution statuses by Status = 'Enabled'");
            } else {
                logger.info("Status column not found, fetching all resolution statuses");
            }
            
            sql += " ORDER BY PrimaryName";
            
            logger.info("Executing SQL: {}", sql);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    ChangeRequestResolution status = new ChangeRequestResolution();
                    status.setId(rs.getInt("ID"));
                    status.setStatusName(rs.getString("PrimaryName"));
                    statusList.add(status);
                    logger.info("Found resolution status ID: {}, Name: {}", status.getId(), status.getStatusName());
                }
            }
        }
        
        logger.info("Total resolution statuses found: {}", statusList.size());
        return statusList;
    }

    /**
     * Update a resolution entry
     */
    public boolean updateResolution(ChangeRequestResolution resolution) throws SQLException {
        ChangeRequestResolution oldResolution = getResolutionById(resolution.getId());
        String sql = "UPDATE changerequest_resolution SET CR_ResolutionStatus_ID = ?, Description = ?, LastUserChange = ?, Updated_At = ? WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Handle null resolution status ID
            if (resolution.getResolutionStatusId() != null) {
                stmt.setInt(1, resolution.getResolutionStatusId());
            } else {
                stmt.setNull(1, java.sql.Types.INTEGER);
            }
            stmt.setString(2, resolution.getDescription());
            stmt.setInt(3, resolution.getLastUserChange());
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(5, resolution.getId());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0 && oldResolution != null
                    && !java.util.Objects.equals(oldResolution.getDescription(), resolution.getDescription())) {
                insertResolutionHistory(resolution.getChangeRequestId(), oldResolution.getDescription(),
                        resolution.getDescription(), resolution.getLastUserChange());
            }
            return affectedRows > 0;
        }
    }

    private void insertResolutionHistory(int crId, String fromVal, String toVal, Integer userId) {
        try {
            ChangeRequestHistoryDAO historyDAO = new ChangeRequestHistoryDAO();
            String author = getPersonNameForHistory(userId);
            historyDAO.insertHistoryRecord(crId, "Resolution Description", fromVal, toVal, author);
        } catch (SQLException e) {
            logger.warn("Could not insert resolution history for CR {}: {}", crId, e.getMessage());
        }
    }

    private String getPersonNameForHistory(Integer personId) throws SQLException {
        if (personId == null) return "System";
        String sql = "SELECT TRIM(CONCAT(COALESCE(First_Name,''), ' ', COALESCE(Last_Name,''))) AS displayName FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("displayName");
                    return (name != null && !name.trim().isEmpty()) ? name.trim() : "System";
                }
                return "System";
            }
        }
    }

    /**
     * Delete a resolution entry
     */
    public boolean deleteResolution(int resolutionId) throws SQLException {
        String sql = "DELETE FROM changerequest_resolution WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, resolutionId);
            
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Get resolution status ID by name from changerequest_resolution_status table (case-insensitive LIKE search)
     */
    public Integer getResolutionStatusIdByName(String name) throws SQLException {
        String sql = "SELECT ID FROM changerequest_resolution_status WHERE PrimaryName LIKE ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + name + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }
        return null; // Status not found
    }

    /**
     * Check if resolution exists for a change request
     */
    public boolean hasResolutionForChangeRequest(int changeRequestId) throws SQLException {
        String sql = "SELECT COUNT(*) as cnt FROM changerequest_resolution WHERE ChangeRequest_ID = ?";
        
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
     * Get resolution by ID to verify it exists and belongs to the change request
     */
    public ChangeRequestResolution getResolutionById(int resolutionId) throws SQLException {
        String sql = "SELECT cr.*, crs.PrimaryName as StatusName " +
                    "FROM changerequest_resolution cr " +
                    "LEFT JOIN changerequest_resolution_status crs ON cr.CR_ResolutionStatus_ID = crs.ID " +
                    "WHERE cr.ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, resolutionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ChangeRequestResolution resolution = new ChangeRequestResolution();
                    resolution.setId(rs.getInt("ID"));
                    resolution.setChangeRequestId(rs.getInt("ChangeRequest_ID"));
                    
                    // Handle NULL resolution status ID properly
                    int statusId = rs.getInt("CR_ResolutionStatus_ID");
                    if (rs.wasNull()) {
                        resolution.setResolutionStatusId(null);
                    } else {
                        resolution.setResolutionStatusId(statusId);
                    }
                    
                    resolution.setDescription(rs.getString("Description"));
                    resolution.setLastUserChange(rs.getInt("LastUserChange"));
                    
                    Timestamp createdAt = rs.getTimestamp("Created_At");
                    if (createdAt != null) {
                        resolution.setCreatedAt(createdAt.toLocalDateTime());
                    }
                    
                    Timestamp updatedAt = rs.getTimestamp("Updated_At");
                    if (updatedAt != null) {
                        resolution.setUpdatedAt(updatedAt.toLocalDateTime());
                    }
                    
                    resolution.setStatusName(rs.getString("StatusName"));
                    
                    return resolution;
                }
            }
        }
        
        return null;
    }
}
