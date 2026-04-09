package com.example.budg_v2.service;

import com.example.budg_v2.dao.GeographyDAO;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.model.Geography;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GeographyService {
    private final GeographyDAO geographyDAO;
    private final SegmentDAO segmentDAO;

    public GeographyService() {
        this.geographyDAO = new GeographyDAO();
        this.segmentDAO = new SegmentDAO();
    }

    public List<Geography> getAllGeographies() throws SQLException {
        return geographyDAO.getAllGeographies();
    }

    /**
     * Get all geographies filtered by user's segment access
     */
    public List<Geography> getAllGeographies(int userId) throws SQLException {
        return geographyDAO.getAllGeographies(userId);
    }

    public Geography getGeographyById(int id) throws SQLException {
        return geographyDAO.getGeographyById(id);
    }

    public List<Geography> getGeographiesForDropdown() throws SQLException {
        return geographyDAO.getGeographiesForDropdown();
    }

    public List<Geography> getGeographiesForDropdown(int userId) throws SQLException {
        return geographyDAO.getGeographiesForDropdown(userId);
    }

    /**
     * Get geographies for dropdown that belong to the given segment (for legal entity impact etc.).
     */
    public List<Geography> getGeographiesForDropdownBySegment(int segmentId) throws SQLException {
        List<Map<String, Object>> objects = segmentDAO.getSegmentObjects(segmentId, "Geography");
        List<Integer> geographyIds = objects.stream()
                .map(o -> ((Number) o.get("object_id")).intValue())
                .collect(Collectors.toList());
        return geographyDAO.getGeographiesForDropdownBySegment(geographyIds);
    }

    public List<Geography> getGeographiesForParentPicker(int excludeId) throws SQLException {
        return geographyDAO.getGeographiesForParentPicker(excludeId);
    }

    public List<Geography> getGeographiesForParentPicker(int excludeId, int userId) throws SQLException {
        return geographyDAO.getGeographiesForParentPicker(excludeId, userId);
    }

    public int createGeography(Geography geography, HttpServletRequest request) throws SQLException {
        // Validate required fields
        validateGeography(geography);
        
        Geography createdGeography = geographyDAO.createGeography(geography);
        int geographyId = createdGeography.getId();
        
        // Create audit records for history tracking
        try {
            String userName = getCurrentUserName(request);
            geographyDAO.createGeographyAuditRecords(geographyId, userName);
            geographyDAO.createGeographyAuditRecord(geographyId);
            //system.out.println("✅ Geography audit records created for ID: " + geographyId);
        } catch (SQLException e) {
            System.err.println("❌ Failed to create geography audit records: " + e.getMessage());
            e.printStackTrace();
            // Don't fail the main operation if audit fails
        }
        
        return geographyId;
    }

    public boolean updateGeography(Geography geography, HttpServletRequest request) throws SQLException {
        // Validate required fields
        validateGeography(geography);
        
        // Get old geography data first
        Geography oldGeography = geographyDAO.getGeographyById(geography.getId());
        if (oldGeography == null) {
            throw new SQLException("Geography not found with ID: " + geography.getId());
        }
        
        // Update with audit
        String userName = getCurrentUserName(request);
        return geographyDAO.updateGeographyWithAudit(oldGeography, geography, userName);
    }

    public boolean deleteGeography(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return geographyDAO.deleteGeographyWithAudit(id, userName);
    }

    public List<Geography> searchGeographies(String searchTerm) throws SQLException {
        return geographyDAO.searchGeographies(searchTerm);
    }

    public List<Geography> searchGeographies(String searchTerm, int userId) throws SQLException {
        return geographyDAO.searchGeographies(searchTerm, userId);
    }

    private void validateGeography(Geography geography) {
        if (geography == null) {
            throw new IllegalArgumentException("Geography cannot be null");
        }

        if (geography.getPrimaryName() == null || geography.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }

        if (geography.getDescription() == null || geography.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required");
        }

        // Validate primary name length
        if (geography.getPrimaryName().length() > 255) {
            throw new IllegalArgumentException("Primary name cannot exceed 255 characters");
        }

        // Prevent self-referencing parent
        if (geography.getParentId() != null && geography.getId() != null && 
            geography.getParentId().equals(geography.getId())) {
            throw new IllegalArgumentException("Geography cannot be its own parent");
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
