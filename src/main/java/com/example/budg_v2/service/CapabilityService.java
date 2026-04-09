package com.example.budg_v2.service;

import com.example.budg_v2.dao.CapabilityDAO;
import com.example.budg_v2.model.Capability;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.List;

public class CapabilityService {

    private final CapabilityDAO capabilityDAO;
    
    public CapabilityService() {
        this.capabilityDAO = new CapabilityDAO();
    }
    
    public List<Capability> getAllCapabilities() throws SQLException {
        return capabilityDAO.getAllCapabilities();
    }
    
    public List<Capability> getAllCapabilities(int userId) throws SQLException {
        return capabilityDAO.getAllCapabilities(userId);
    }
    
    public List<Capability> getAllCapabilitiesForDropdown() throws SQLException {
        return capabilityDAO.getAllCapabilitiesForDropdown();
    }

    public List<Capability> getAllCapabilitiesForDropdown(int userId) throws SQLException {
        return capabilityDAO.getAllCapabilitiesForDropdown(userId);
    }

    public Capability getCapabilityById(int id) throws SQLException {
        return capabilityDAO.getCapabilityById(id);
    }
    
    public List<Capability> searchCapabilities(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllCapabilities();
        }
        return capabilityDAO.searchCapabilities(searchQuery.trim());
    }
    
    public Capability createCapability(Capability capability) throws SQLException {
        // Validate required fields
        if (capability.getPrimaryName() == null || capability.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        
        // Name uniqueness is scoped to segment in CapabilityServlet.

        // Set default values
        if (capability.getLastUpdateUserId() == null) {
            capability.setLastUpdateUserId(1); // Default user ID
        }
        
        return capabilityDAO.createCapability(capability);
    }

    public boolean updateCapability(Capability capability) throws SQLException {
        // Validate required fields
        if (capability.getPrimaryName() == null || capability.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        
        // Name uniqueness is scoped to segment in CapabilityServlet.

        // Validate RefNumber uniqueness for update (exclude current ID)
        if (capability.getRefNumber() != null && !capability.getRefNumber().trim().isEmpty()) {
            if (!capabilityDAO.isRefNumberUniqueForUpdate(capability.getRefNumber(), capability.getId())) {
                throw new IllegalArgumentException("This reference number is already in use. Please enter a unique reference number.");
            }
        }
        
        return capabilityDAO.updateCapability(capability);
    }
    
    public boolean deleteCapability(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return capabilityDAO.deleteCapabilityWithAudit(id, userName);
    }

    private String getCurrentUserName(HttpServletRequest request) {
        try {
            String userJson = (String) request.getAttribute("user");
            if (userJson != null && userJson.contains("\"username\":")) {
                int start = userJson.indexOf("\"username\":\"") + 12;
                int end = userJson.indexOf("\"", start);
                if (end > start) {
                    return userJson.substring(start, end);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting username: " + e.getMessage());
        }
        return "Unknown User";
    }

    public boolean isNameUnique(String name, Integer excludeId) throws SQLException {
        return capabilityDAO.isNameUnique(name, excludeId);
    }
}