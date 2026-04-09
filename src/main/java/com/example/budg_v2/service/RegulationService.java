package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationDAO;
import com.example.budg_v2.model.Regulation;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class RegulationService {
    private RegulationDAO regulationDAO;

    public RegulationService() {
        this.regulationDAO = new RegulationDAO();
    }

    public List<Regulation> getAllRegulations() throws SQLException {
        return regulationDAO.getAllRegulations();
    }
    
    /**
     * Get all regulations filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to.
     */
    public List<Regulation> getAllRegulationsBySegmentAccess(int userId) throws SQLException {
        return regulationDAO.getAllRegulationsBySegmentAccess(userId);
    }

    public Regulation getRegulationById(int id) throws SQLException {
        return regulationDAO.getRegulationById(id);
    }

    public List<Regulation> getRegulationsForDropdown() throws SQLException {
        return regulationDAO.getRegulationsForDropdown();
    }
    
    /**
     * Get regulations for dropdown filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Parent selection only shows accessible objects.
     */
    public List<Regulation> getRegulationsForDropdownBySegmentAccess(int userId) throws SQLException {
        return regulationDAO.getRegulationsForDropdownBySegmentAccess(userId);
    }

    public List<Regulation> getRegulationsForParentPicker(int excludeId) throws SQLException {
        return regulationDAO.getRegulationsForParentPicker(excludeId);
    }

    public int createRegulation(Regulation regulation) throws SQLException {
        // Validate required fields
        validateRegulation(regulation);
        
        // Default RefNumber if not provided: REG-(next id)
        if (regulation.getRefNumber() == null || regulation.getRefNumber().trim().isEmpty()) {
            int nextId = regulationDAO.getNextId();
            regulation.setRefNumber("REG-" + nextId);
        }
        
        // Get userId from regulation object, default to a system user if not provided
        int userId = (regulation.getLastUpdateUserId() != null) ? regulation.getLastUpdateUserId() : 1;
        
        Regulation createdRegulation = regulationDAO.createRegulation(regulation, userId);
        return createdRegulation.getId();
    }

    public boolean updateRegulation(Regulation regulation) throws SQLException {
        // Validate required fields
        validateRegulation(regulation);
        
        // Validate RefNumber uniqueness for update (exclude current ID)
        if (regulation.getRefNumber() != null && !regulation.getRefNumber().trim().isEmpty()) {
            if (!regulationDAO.isRefNumberUniqueForUpdate(regulation.getRefNumber(), regulation.getId())) {
                throw new IllegalArgumentException("This reference number is already in use. Please enter a unique reference number.");
            }
        }
        
        // Get userId from regulation object, default to a system user if not provided
        int userId = (regulation.getLastUpdateUserId() != null) ? regulation.getLastUpdateUserId() : 1;
        
        return regulationDAO.updateRegulation(regulation.getId(), regulation, userId);
    }

    public boolean deleteRegulation(int id) throws SQLException {
        return regulationDAO.deleteRegulation(id);
    }

    public List<Regulation> searchRegulations(String searchTerm) throws SQLException {
        return regulationDAO.searchRegulations(searchTerm);
    }

    public List<Regulation> searchRegulationsBySegmentAccess(String searchTerm, int userId) throws SQLException {
        return regulationDAO.searchRegulationsBySegmentAccess(searchTerm, userId);
    }

    // Reference data methods
    public List<Map<String, Object>> getViewingOptions() throws SQLException {
        return regulationDAO.getViewingOptions();
    }

    public List<Map<String, Object>> getLegalAdviceTypes() throws SQLException {
        return regulationDAO.getLegalAdviceTypes();
    }

    public List<Map<String, Object>> getRegulationMaturityOptions() throws SQLException {
        return regulationDAO.getRegulationMaturityOptions();
    }

    public List<Map<String, Object>> getRegulationProbabilityOptions() throws SQLException {
        return regulationDAO.getRegulationProbabilityOptions();
    }

    public List<Map<String, Object>> getRegulationStatusOptions() throws SQLException {
        return regulationDAO.getRegulationStatusOptions();
    }

    public List<Map<String, Object>> getRegulationImpactRatingOptions() throws SQLException {
        return regulationDAO.getRegulationImpactRatingOptions();
    }

    public List<Map<String, Object>> getRegulationStageOptions() throws SQLException {
        return regulationDAO.getRegulationStageOptions();
    }

    public List<Map<String, Object>> getRegulationComplianceLevelOptions() throws SQLException {
        return regulationDAO.getRegulationComplianceLevelOptions();
    }

    // Stakeholder methods
    public List<Map<String, Object>> getRegulationStakeholders(int regulationId) throws SQLException {
        return regulationDAO.getDirectStakeholdersForRegulation(regulationId);
    }

    public void saveRegulationStakeholders(int regulationId, Map<String, Object> changes, int currentUserId) throws SQLException {
        regulationDAO.saveStakeholdersChanges(regulationId, changes, currentUserId);
    }

    public boolean acceptStakeholderStatus(int objectXPeopleId, int currentUserId, int regulationId) throws SQLException {
        return regulationDAO.acceptStakeholderStatus(objectXPeopleId, currentUserId, regulationId);
    }

    private void validateRegulation(Regulation regulation) {
        if (regulation == null) {
            throw new IllegalArgumentException("Regulation cannot be null");
        }

        if (regulation.getPrimaryName() == null || regulation.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Long Name is required");
        }

        if (regulation.getDescription() == null || regulation.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required");
        }

        // Validate primary name length
        if (regulation.getPrimaryName().length() > 255) {
            throw new IllegalArgumentException("Long Name cannot exceed 255 characters");
        }

        // Validate RefNumber length if provided
        if (regulation.getRefNumber() != null && regulation.getRefNumber().length() > 255) {
            throw new IllegalArgumentException("Reference Number cannot exceed 255 characters");
        }

        // Validate ShortName length if provided
        if (regulation.getShortName() != null && regulation.getShortName().length() > 255) {
            throw new IllegalArgumentException("Short Name cannot exceed 255 characters");
        }

        // Prevent self-referencing parent
        if (regulation.getParentId() != null && regulation.getId() != null && 
            regulation.getParentId().equals(regulation.getId())) {
            throw new IllegalArgumentException("Regulation cannot be its own parent");
        }

        // Validate rank is positive if provided
        if (regulation.getRank() != null && regulation.getRank() < 0) {
            throw new IllegalArgumentException("Rank must be a positive number");
        }
    }
}
