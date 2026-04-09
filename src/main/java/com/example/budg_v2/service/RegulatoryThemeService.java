package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulatoryThemeDAO;
import com.example.budg_v2.model.RegulatoryTheme;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.SQLException;
import java.util.List;

public class RegulatoryThemeService {
    private RegulatoryThemeDAO regulatoryThemeDAO;

    public RegulatoryThemeService() {
        this.regulatoryThemeDAO = new RegulatoryThemeDAO();
    }

    public List<RegulatoryTheme> getAllRegulatoryThemes() throws SQLException {
        return regulatoryThemeDAO.getAllRegulatoryThemes();
    }

    /**
     * Get all regulatory themes filtered by user's segment access
     */
    public List<RegulatoryTheme> getAllRegulatoryThemes(int userId) throws SQLException {
        return regulatoryThemeDAO.getAllRegulatoryThemes(userId);
    }

    public RegulatoryTheme getRegulatoryThemeById(int id) throws SQLException {
        return regulatoryThemeDAO.getRegulatoryThemeById(id);
    }

    public List<RegulatoryTheme> getRegulatoryThemesForDropdown() throws SQLException {
        return regulatoryThemeDAO.getRegulatoryThemesForDropdown();
    }

    public List<RegulatoryTheme> getRegulatoryThemesForDropdown(int userId) throws SQLException {
        return regulatoryThemeDAO.getRegulatoryThemesForDropdown(userId);
    }

    public List<RegulatoryTheme> getRegulatoryThemesForParentPicker(int excludeId) throws SQLException {
        return regulatoryThemeDAO.getRegulatoryThemesForParentPicker(excludeId);
    }

    public List<RegulatoryTheme> getRegulatoryThemesForParentPicker(int excludeId, int userId) throws SQLException {
        return regulatoryThemeDAO.getRegulatoryThemesForParentPicker(excludeId, userId);
    }

    public int createRegulatoryTheme(RegulatoryTheme theme, HttpServletRequest request) throws SQLException {
        // Validate required fields
        validateRegulatoryTheme(theme);
        // Generate default ref if missing
        if (theme.getRefNumber() == null || theme.getRefNumber().trim().isEmpty()) {
            String nextCode = generateDefaultRefCode();
            theme.setRefNumber(nextCode);
        }
        RegulatoryTheme createdTheme = regulatoryThemeDAO.createRegulatoryTheme(theme);
        int themeId = createdTheme.getId();
        
        // Create audit records for history tracking
        try {
            String userName = getCurrentUserName(request);
            regulatoryThemeDAO.createRegulatoryThemeAuditRecords(themeId, userName);
            regulatoryThemeDAO.createRegulatoryThemeAuditRecord(themeId);
            //system.out.println("✅ Regulatory Theme audit records created for ID: " + themeId);
        } catch (SQLException e) {
            System.err.println("❌ Failed to create regulatory theme audit records: " + e.getMessage());
            e.printStackTrace();
            // Don't fail the main operation if audit fails
        }
        
        return themeId;
    }

    public boolean updateRegulatoryTheme(RegulatoryTheme theme, HttpServletRequest request) throws SQLException {
        // Validate required fields
        validateRegulatoryTheme(theme);
        
        // Validate refNumber uniqueness for update
        if (theme.getRefNumber() != null && !theme.getRefNumber().trim().isEmpty()) {
            if (!regulatoryThemeDAO.isRefNumberUniqueForUpdate(theme.getRefNumber(), theme.getId())) {
                throw new IllegalArgumentException("This reference number is already in use. Please enter a unique reference number.");
            }
        }
        
        // Get old theme data first
        RegulatoryTheme oldTheme = regulatoryThemeDAO.getRegulatoryThemeById(theme.getId());
        if (oldTheme == null) {
            throw new SQLException("Regulatory Theme not found with ID: " + theme.getId());
        }
        
        // Update with audit
        String userName = getCurrentUserName(request);
        return regulatoryThemeDAO.updateRegulatoryThemeWithAudit(oldTheme, theme, userName);
    }

    public boolean deleteRegulatoryTheme(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return regulatoryThemeDAO.deleteRegulatoryThemeWithAudit(id, userName);
    }

    public List<RegulatoryTheme> searchRegulatoryThemes(String searchTerm) throws SQLException {
        return regulatoryThemeDAO.searchRegulatoryThemes(searchTerm);
    }

    public List<RegulatoryTheme> searchRegulatoryThemes(String searchTerm, int userId) throws SQLException {
        return regulatoryThemeDAO.searchRegulatoryThemes(searchTerm, userId);
    }

    private void validateRegulatoryTheme(RegulatoryTheme theme) {
        if (theme == null) {
            throw new IllegalArgumentException("Regulatory theme cannot be null");
        }

        if (theme.getPrimaryName() == null || theme.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }

        if (theme.getDescription() == null || theme.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required");
        }

        if (theme.getStatusId() == null) {
            throw new IllegalArgumentException("Status is required");
        }

        // Validate primary name length
        if (theme.getPrimaryName().length() > 255) {
            throw new IllegalArgumentException("Primary name cannot exceed 255 characters");
        }

        // Validate short name length if provided
        if (theme.getShortName() != null && theme.getShortName().length() > 255) {
            throw new IllegalArgumentException("Short name cannot exceed 255 characters");
        }

        // Validate ref number length if provided
        if (theme.getRefNumber() != null && theme.getRefNumber().length() > 255) {
            throw new IllegalArgumentException("Reference number cannot exceed 255 characters");
        }
    }

    private String generateDefaultRefCode() throws SQLException {
        // Use current timestamp-based fallback plus count to avoid collisions
        // Prefer database max(ID) + 1 pattern like DAO does
        int nextId = 1;
        try {
            java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
            try (java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(ID), 0) + 1 AS next_id FROM regulatorytheme")) {
                if (rs.next()) {
                    nextId = rs.getInt("next_id");
                }
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
            // fallback will use 1
        }
        return "REGTH-" + nextId;
    }

    // Helper method to get current user name from request
    private String getCurrentUserName(HttpServletRequest request) {
        try {
            Object userNameObj = request.getAttribute("userName");
            if (userNameObj != null) {
                return userNameObj.toString();
            }
            
            // Try to get from userId
            Integer userId = (Integer) request.getAttribute("userId");
            if (userId != null) {
                return "User ID: " + userId;
            }
            
            return "System"; // Fallback if no user name found
        } catch (Exception e) {
            System.err.println("Error getting current user name: " + e.getMessage());
            return "System";
        }
    }
}
