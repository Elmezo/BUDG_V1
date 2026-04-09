package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulatorDAO;
import com.example.budg_v2.model.Regulator;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.SQLException;
import java.util.List;

public class RegulatorService {
    private RegulatorDAO regulatorDAO;

    public RegulatorService() {
        this.regulatorDAO = new RegulatorDAO();
    }

    public List<Regulator> getAllRegulators() throws SQLException {
        return regulatorDAO.getAllRegulators();
    }

    /**
     * Get all regulators filtered by user's segment access
     */
    public List<Regulator> getAllRegulators(int userId) throws SQLException {
        return regulatorDAO.getAllRegulators(userId);
    }

    public Regulator getRegulatorById(int id) throws SQLException {
        return regulatorDAO.getRegulatorById(id);
    }

    public List<Regulator> getRegulatorsForDropdown() throws SQLException {
        return regulatorDAO.getRegulatorsForDropdown();
    }

    public List<Regulator> getRegulatorsForDropdown(int userId) throws SQLException {
        return regulatorDAO.getRegulatorsForDropdown(userId);
    }

    public int createRegulator(Regulator regulator, HttpServletRequest request) throws SQLException {
        // Validate required fields
        validateRegulator(regulator);
        
        Regulator createdRegulator = regulatorDAO.createRegulator(regulator);
        int regulatorId = createdRegulator.getId();
        
        // Create audit records for history tracking
        try {
            String userName = getCurrentUserName(request);
            regulatorDAO.createRegulatorAuditRecords(regulatorId, userName);
            regulatorDAO.createRegulatorAuditRecord(regulatorId);
            //system.out.println("✅ Regulator audit records created for ID: " + regulatorId);
        } catch (SQLException e) {
            System.err.println("❌ Failed to create regulator audit records: " + e.getMessage());
            e.printStackTrace();
            // Don't fail the main operation if audit fails
        }
        
        return regulatorId;
    }

    public boolean updateRegulator(Regulator regulator, HttpServletRequest request) throws SQLException {
        // Validate required fields
        validateRegulator(regulator);
        
        // Get old regulator data first
        Regulator oldRegulator = regulatorDAO.getRegulatorById(regulator.getId());
        if (oldRegulator == null) {
            throw new SQLException("Regulator not found with ID: " + regulator.getId());
        }
        
        // Update with audit
        String userName = getCurrentUserName(request);
        return regulatorDAO.updateRegulatorWithAudit(oldRegulator, regulator, userName);
    }

    public boolean deleteRegulator(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return regulatorDAO.deleteRegulatorWithAudit(id, userName);
    }

    public List<Regulator> searchRegulators(String searchTerm) throws SQLException {
        return regulatorDAO.searchRegulators(searchTerm);
    }

    public List<Regulator> searchRegulators(String searchTerm, int userId) throws SQLException {
        return regulatorDAO.searchRegulators(searchTerm, userId);
    }

    private void validateRegulator(Regulator regulator) {
        if (regulator == null) {
            throw new IllegalArgumentException("Regulator cannot be null");
        }

        if (regulator.getPrimaryName() == null || regulator.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }

        if (regulator.getDescription() == null || regulator.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required");
        }

        if (regulator.getShortName() == null || regulator.getShortName().trim().isEmpty()) {
            throw new IllegalArgumentException("Short name is required");
        }

        // Validate primary name length
        if (regulator.getPrimaryName().length() > 255) {
            throw new IllegalArgumentException("Primary name cannot exceed 255 characters");
        }

        // Validate short name length
        if (regulator.getShortName().length() > 255) {
            throw new IllegalArgumentException("Short name cannot exceed 255 characters");
        }
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
