package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationStageDAO;
import com.example.budg_v2.model.RegulationStage;

import java.sql.SQLException;
import java.util.List;

public class RegulationStageService {
    
    private final RegulationStageDAO regulationStageDAO;
    
    public RegulationStageService() {
        this.regulationStageDAO = new RegulationStageDAO();
    }
    
    public RegulationStageService(RegulationStageDAO regulationStageDAO) {
        this.regulationStageDAO = regulationStageDAO;
    }

    public int createRegulationStage(RegulationStage regulationStage) throws SQLException {
        // Validate required fields
        validateRegulationStage(regulationStage);
        
        RegulationStage createdRegulationStage = regulationStageDAO.createRegulationStage(regulationStage);
        return createdRegulationStage.getId();
    }

    public List<RegulationStage> getAllRegulationStages() throws SQLException {
        return regulationStageDAO.getAllRegulationStages();
    }

    public RegulationStage getRegulationStageById(int id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("RegulationStage ID must be positive");
        }
        
        return regulationStageDAO.getRegulationStageById(id);
    }

    public RegulationStage updateRegulationStage(RegulationStage regulationStage) throws SQLException {
        // Validate required fields
        validateRegulationStage(regulationStage);
        
        if (regulationStage.getId() <= 0) {
            throw new IllegalArgumentException("RegulationStage ID must be positive for update");
        }
        
        // Check if regulation stage exists
        RegulationStage existingRegulationStage = regulationStageDAO.getRegulationStageById(regulationStage.getId());
        if (existingRegulationStage == null) {
            throw new IllegalArgumentException("RegulationStage with ID " + regulationStage.getId() + " not found");
        }
        
        return regulationStageDAO.updateRegulationStage(regulationStage);
    }

    public boolean deleteRegulationStage(int id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("RegulationStage ID must be positive");
        }
        
        // Check if regulation stage exists
        RegulationStage existingRegulationStage = regulationStageDAO.getRegulationStageById(id);
        if (existingRegulationStage == null) {
            throw new IllegalArgumentException("RegulationStage with ID " + id + " not found");
        }
        
        return regulationStageDAO.deleteRegulationStage(id);
    }

    private void validateRegulationStage(RegulationStage regulationStage) {
        if (regulationStage == null) {
            throw new IllegalArgumentException("RegulationStage cannot be null");
        }
        
        if (regulationStage.getPrimaryName() == null || regulationStage.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("RegulationStage primary name is required");
        }
        
        if (regulationStage.getPrimaryName().length() > 255) {
            throw new IllegalArgumentException("RegulationStage primary name cannot exceed 255 characters");
        }
    }
}
