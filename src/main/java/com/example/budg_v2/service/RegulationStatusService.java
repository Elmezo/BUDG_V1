package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationStatusDAO;
import com.example.budg_v2.model.RegulationStatus;

import java.sql.SQLException;
import java.util.List;

public class RegulationStatusService {
    
    private final RegulationStatusDAO regulationStatusDAO;
    
    public RegulationStatusService() {
        this.regulationStatusDAO = new RegulationStatusDAO();
    }
    
    public RegulationStatusService(RegulationStatusDAO regulationStatusDAO) {
        this.regulationStatusDAO = regulationStatusDAO;
    }

    public int createRegulationStatus(RegulationStatus regulationStatus) throws SQLException {
        // Validate required fields
        validateRegulationStatus(regulationStatus);
        
        RegulationStatus createdRegulationStatus = regulationStatusDAO.createRegulationStatus(regulationStatus);
        return createdRegulationStatus.getId();
    }

    public List<RegulationStatus> getAllRegulationStatuses() throws SQLException {
        return regulationStatusDAO.getAllRegulationStatuses();
    }

    public RegulationStatus getRegulationStatusById(int id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("RegulationStatus ID must be positive");
        }
        
        return regulationStatusDAO.getRegulationStatusById(id);
    }

    public RegulationStatus updateRegulationStatus(RegulationStatus regulationStatus) throws SQLException {
        // Validate required fields
        validateRegulationStatus(regulationStatus);
        
        if (regulationStatus.getId() <= 0) {
            throw new IllegalArgumentException("RegulationStatus ID must be positive for update");
        }
        
        // Check if regulation status exists
        RegulationStatus existingRegulationStatus = regulationStatusDAO.getRegulationStatusById(regulationStatus.getId());
        if (existingRegulationStatus == null) {
            throw new IllegalArgumentException("RegulationStatus with ID " + regulationStatus.getId() + " not found");
        }
        
        return regulationStatusDAO.updateRegulationStatus(regulationStatus);
    }

    public boolean deleteRegulationStatus(int id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("RegulationStatus ID must be positive");
        }
        
        // Check if regulation status exists
        RegulationStatus existingRegulationStatus = regulationStatusDAO.getRegulationStatusById(id);
        if (existingRegulationStatus == null) {
            throw new IllegalArgumentException("RegulationStatus with ID " + id + " not found");
        }
        
        return regulationStatusDAO.deleteRegulationStatus(id);
    }

    private void validateRegulationStatus(RegulationStatus regulationStatus) {
        if (regulationStatus == null) {
            throw new IllegalArgumentException("RegulationStatus cannot be null");
        }
        
        if (regulationStatus.getPrimaryName() == null || regulationStatus.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("RegulationStatus primary name is required");
        }
        
        if (regulationStatus.getPrimaryName().length() > 255) {
            throw new IllegalArgumentException("RegulationStatus primary name cannot exceed 255 characters");
        }
    }
}
