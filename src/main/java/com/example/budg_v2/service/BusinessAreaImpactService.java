package com.example.budg_v2.service;

import com.example.budg_v2.dao.BusinessAreaImpactDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class  BusinessAreaImpactService {
    
    private BusinessAreaImpactDAO dao;
    
    public BusinessAreaImpactService() {
        this.dao = new BusinessAreaImpactDAO();
    }
    
    // ===== GLOSSARY METHODS =====
    
    public List<Map<String, Object>> getGlossaryRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        return dao.getGlossaryRelationshipsByBusinessAreaId(businessAreaId);
    }
    
    public List<Map<String, Object>> getGlossaryRelationTypes() throws SQLException {
        return dao.getGlossaryRelationTypes();
    }
    
    public String getGlossaryOwnersString(int glossaryId) throws SQLException {
        return dao.getGlossaryOwnersString(glossaryId);
    }
    
    public String getGlossaryOwnersEmail(int glossaryId) throws SQLException {
        return dao.getGlossaryOwnersEmail(glossaryId);
    }
    
    public boolean saveGlossaryRelationships(int businessAreaId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveGlossaryRelationships(businessAreaId, relationships, userId);
    }
    
    // ===== SYSTEM METHODS =====
    
    public List<Map<String, Object>> getSystemRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        return dao.getSystemRelationshipsByBusinessAreaId(businessAreaId);
    }
    
    public List<Map<String, Object>> getSystemRelationTypes() throws SQLException {
        return dao.getSystemRelationTypes();
    }
    
    public String getSystemOwnersString(int systemId) throws SQLException {
        return dao.getSystemOwnersString(systemId);
    }
    
    public String getSystemOwnersEmail(int systemId) throws SQLException {
        return dao.getSystemOwnersEmail(systemId);
    }
    
    public boolean saveSystemRelationships(int businessAreaId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveSystemRelationships(businessAreaId, relationships, userId);
    }
    
    // ===== PROCESS METHODS =====
    
    public List<Map<String, Object>> getProcessRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        return dao.getProcessRelationshipsByBusinessAreaId(businessAreaId);
    }
    
    public List<Map<String, Object>> getProcessRelationTypes() throws SQLException {
        return dao.getProcessRelationTypes();
    }
    
    public String getProcessOwnersString(int processId) throws SQLException {
        return dao.getProcessOwnersString(processId);
    }
    
    public String getProcessOwnersEmail(int processId) throws SQLException {
        return dao.getProcessOwnersEmail(processId);
    }
    
    public boolean saveProcessRelationships(int businessAreaId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveProcessRelationships(businessAreaId, relationships, userId);
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    public List<Map<String, Object>> getBusinessAreaRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        return dao.getBusinessAreaRelationshipsByGlossaryId(glossaryId);
    }
    
    public List<Map<String, Object>> getBusinessAreaRelationshipsBySystemId(int systemId) throws SQLException {
        return dao.getBusinessAreaRelationshipsBySystemId(systemId);
    }
    
    public List<Map<String, Object>> getBusinessAreaRelationshipsByProcessId(int processId) throws SQLException {
        return dao.getBusinessAreaRelationshipsByProcessId(processId);
    }
}

