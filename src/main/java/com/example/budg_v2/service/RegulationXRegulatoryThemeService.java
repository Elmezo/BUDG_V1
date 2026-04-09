package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationXRegulatoryThemeDAO;
import com.example.budg_v2.model.RegulationXRegulatoryTheme;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class RegulationXRegulatoryThemeService {

    private final RegulationXRegulatoryThemeDAO regulationXRegulatoryThemeDAO;

    public RegulationXRegulatoryThemeService() {
        this.regulationXRegulatoryThemeDAO = new RegulationXRegulatoryThemeDAO();
    }

    public List<RegulationXRegulatoryTheme> getAllRegulationXRegulatoryThemes() throws SQLException {
        return regulationXRegulatoryThemeDAO.getAllRegulationXRegulatoryThemes();
    }

    public RegulationXRegulatoryTheme getRegulationXRegulatoryThemeById(int id) throws SQLException {
        return regulationXRegulatoryThemeDAO.getRegulationXRegulatoryThemeById(id);
    }

    public List<RegulationXRegulatoryTheme> getRegulationsByRegulatoryThemeId(int regulatoryThemeId) throws SQLException {
        return regulationXRegulatoryThemeDAO.getRegulationsByRegulatoryThemeId(regulatoryThemeId);
    }

    public RegulationXRegulatoryTheme createRegulationXRegulatoryTheme(RegulationXRegulatoryTheme regulation) throws SQLException, IllegalArgumentException {
        if (regulation.getRegulationId() == null) {
            throw new IllegalArgumentException("Regulation ID is required");
        }
        if (regulation.getRegulatoryThemeId() == null) {
            throw new IllegalArgumentException("Regulatory Theme ID is required");
        }
        if (regulation.getRelationType() == null) {
            throw new IllegalArgumentException("Relation Type is required");
        }
        
        return regulationXRegulatoryThemeDAO.createRegulationXRegulatoryTheme(regulation);
    }

    /**
     * إنشاء علاقة regulation مع regulatory theme مع تسجيل audit
     */
    public RegulationXRegulatoryTheme createRegulationXRegulatoryThemeWithAudit(RegulationXRegulatoryTheme regulation, String userName) throws SQLException, IllegalArgumentException {
        if (regulation.getRegulationId() == null) {
            throw new IllegalArgumentException("Regulation ID is required");
        }
        if (regulation.getRegulatoryThemeId() == null) {
            throw new IllegalArgumentException("Regulatory Theme ID is required");
        }
        if (regulation.getRelationType() == null) {
            throw new IllegalArgumentException("Relation Type is required");
        }
        
        // Create the relationship
        RegulationXRegulatoryTheme created = regulationXRegulatoryThemeDAO.createRegulationXRegulatoryTheme(regulation);
        
        // Create audit records
        try {
            regulationXRegulatoryThemeDAO.createRegulationLinkAuditRecords(
                created.getRegulatoryThemeId(), 
                created.getRegulationId(), 
                created.getRelationType(), 
                userName
            );
        } catch (SQLException e) {
            System.err.println("❌ Failed to create regulation link audit records: " + e.getMessage());
            e.printStackTrace();
            // Don't fail the main operation if audit fails
        }
        
        return created;
    }

    public boolean updateRegulationXRegulatoryTheme(RegulationXRegulatoryTheme regulation) throws SQLException, IllegalArgumentException {
        if (regulation.getId() == null) {
            throw new IllegalArgumentException("Regulation relationship ID is required for update");
        }
        if (regulation.getRegulationId() == null) {
            throw new IllegalArgumentException("Regulation ID is required");
        }
        if (regulation.getRelationType() == null) {
            throw new IllegalArgumentException("Relation Type is required");
        }
        
        return regulationXRegulatoryThemeDAO.updateRegulationXRegulatoryTheme(regulation);
    }

    /**
     * تحديث علاقة regulation مع regulatory theme مع تسجيل audit
     */
    public boolean updateRegulationXRegulatoryThemeWithAudit(RegulationXRegulatoryTheme regulation, String userName) throws SQLException, IllegalArgumentException {
        if (regulation.getId() == null) {
            throw new IllegalArgumentException("Regulation relationship ID is required for update");
        }
        if (regulation.getRegulationId() == null) {
            throw new IllegalArgumentException("Regulation ID is required");
        }
        if (regulation.getRelationType() == null) {
            throw new IllegalArgumentException("Relation Type is required");
        }
        
        // Get old relationship data
        RegulationXRegulatoryTheme oldRelationship = regulationXRegulatoryThemeDAO.getRegulationXRegulatoryThemeById(regulation.getId());
        if (oldRelationship == null) {
            throw new SQLException("Regulation relationship not found with ID: " + regulation.getId());
        }
        
        // Update the relationship
        boolean updated = regulationXRegulatoryThemeDAO.updateRegulationXRegulatoryTheme(regulation);
        
        // Create audit records for changes
        if (updated) {
            try {
                regulationXRegulatoryThemeDAO.createRegulationUpdateAuditRecords(
                    regulation.getId(), 
                    oldRelationship, 
                    regulation, 
                    userName
                );
            } catch (SQLException e) {
                System.err.println("❌ Failed to create regulation update audit records: " + e.getMessage());
                e.printStackTrace();
                // Don't fail the main operation if audit fails
            }
        }
        
        return updated;
    }

    public boolean deleteRegulationXRegulatoryTheme(int id) throws SQLException {
        return regulationXRegulatoryThemeDAO.deleteRegulationXRegulatoryTheme(id);
    }

    /**
     * حذف علاقة regulation من regulatory theme مع تسجيل audit
     */
    public boolean deleteRegulationXRegulatoryThemeWithAudit(int id, String userName) throws SQLException {
        // Create audit records before deletion
        try {
            regulationXRegulatoryThemeDAO.createRegulationUnlinkAuditRecord(id, userName);
        } catch (SQLException e) {
            System.err.println("❌ Failed to create regulation unlink audit record: " + e.getMessage());
            e.printStackTrace();
            // Don't fail the main operation if audit fails
        }
        
        // Delete the relationship
        return regulationXRegulatoryThemeDAO.deleteRegulationXRegulatoryTheme(id);
    }

    public List<Map<String, Object>> getRegulatoryThemesByRegulationId(int regulationId) throws SQLException {
        return regulationXRegulatoryThemeDAO.getRegulatoryThemesByRegulationId(regulationId);
    }

    public List<Map<String, Object>> getRegulationsWithDetailsByRegulatoryThemeId(int regulatoryThemeId) throws SQLException {
        return regulationXRegulatoryThemeDAO.getRegulationsWithDetailsByRegulatoryThemeId(regulatoryThemeId);
    }
}