package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationXRegulatorDAO;
import com.example.budg_v2.model.RegulationXRegulator;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class RegulationXRegulatorService {
    private final RegulationXRegulatorDAO dao;
    
    public RegulationXRegulatorService() {
        this.dao = new RegulationXRegulatorDAO();
    }
    
    public List<Map<String, Object>> getRegulatorsByRegulationId(int regulationId) throws SQLException {
        return dao.getByRegulationId(regulationId);
    }
    
    public List<Map<String, Object>> getRegulatorsByRegulationIdWithInheritance(int regulationId) throws SQLException {
        return dao.getByRegulationIdWithInheritance(regulationId);
    }
    
    public int createRegulationXRegulator(RegulationXRegulator regulator) {
        return dao.createRegulationXRegulator(regulator);
    }
    
    /**
     * إنشاء علاقة regulation مع regulator مع تسجيل audit
     */
    public int createRegulationXRegulatorWithAudit(RegulationXRegulator regulator, String userName) {
        // Create the relationship
        int createdId = dao.createRegulationXRegulator(regulator);
        
        // Create audit records
        if (createdId > 0) {
            try {
                dao.createRegulatorLinkAuditRecords(
                    regulator.getRegulationId(), 
                    regulator.getRegulatorId(), 
                    regulator.getRelationType(), 
                    userName
                );
            } catch (SQLException e) {
                System.err.println("❌ Failed to create regulator link audit records: " + e.getMessage());
                e.printStackTrace();
                // Don't fail the main operation if audit fails
            }
        }
        
        return createdId;
    }
    
    public boolean updateRegulationXRegulator(RegulationXRegulator regulator) {
        return dao.updateRegulationXRegulator(regulator);
    }
    
    /**
     * تحديث علاقة regulation مع regulator مع تسجيل audit
     */
    public boolean updateRegulationXRegulatorWithAudit(RegulationXRegulator regulator, String userName) {
        // Get old relationship data
        RegulationXRegulator oldRelationship = dao.getById(regulator.getId());
        if (oldRelationship == null) {
            System.err.println("❌ Regulator relationship not found with ID: " + regulator.getId());
            return false;
        }
        
        // Update the relationship
        boolean updated = dao.updateRegulationXRegulator(regulator);
        
        // Create audit records for changes
        if (updated) {
            try {
                dao.createRegulatorUpdateAuditRecords(
                    regulator.getId(), 
                    oldRelationship, 
                    regulator, 
                    userName
                );
            } catch (SQLException e) {
                System.err.println("❌ Failed to create regulator update audit records: " + e.getMessage());
                e.printStackTrace();
                // Don't fail the main operation if audit fails
            }
        }
        
        return updated;
    }
    
    public boolean deleteRegulationXRegulator(int id) {
        return dao.deleteRegulationXRegulator(id);
    }
    
    /**
     * حذف علاقة regulator من regulation مع تسجيل audit
     */
    public boolean deleteRegulationXRegulatorWithAudit(int id, String userName) {
        // Create audit records before deletion
        try {
            dao.createRegulatorUnlinkAuditRecord(id, userName);
        } catch (SQLException e) {
            System.err.println("❌ Failed to create regulator unlink audit record: " + e.getMessage());
            e.printStackTrace();
            // Don't fail the main operation if audit fails
        }
        
        // Delete the relationship
        return dao.deleteRegulationXRegulator(id);
    }
    
    public RegulationXRegulator getById(int id) {
        return dao.getById(id);
    }
}
