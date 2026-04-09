package com.example.budg_v2.service;

import com.example.budg_v2.dao.GlossaryImpactDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class GlossaryImpactService {
    private GlossaryImpactDAO dao;
    
    public GlossaryImpactService() {
        this.dao = new GlossaryImpactDAO();
    }
    
    // Product relationships
    public List<Map<String, Object>> getProductRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        return dao.getProductRelationshipsByGlossaryId(glossaryId);
    }
    
    public List<Map<String, Object>> getProductRelationTypes() throws SQLException {
        return dao.getProductRelationTypes();
    }
    
    public String getProductOwnersString(int productId) throws SQLException {
        return dao.getProductOwnersString(productId);
    }
    
    public String getProductOwnersEmail(int productId) throws SQLException {
        return dao.getProductOwnersEmail(productId);
    }
    
    public boolean saveProductRelationships(int glossaryId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveProductRelationships(glossaryId, relationships, userId);
    }
    
    // Client relationships
    public List<Map<String, Object>> getClientRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        return dao.getClientRelationshipsByGlossaryId(glossaryId);
    }
    
    public List<Map<String, Object>> getClientRelationTypes() throws SQLException {
        return dao.getClientRelationTypes();
    }
    
    public String getClientOwnersString(int clientId) throws SQLException {
        return dao.getClientOwnersString(clientId);
    }
    
    public String getClientOwnersEmail(int clientId) throws SQLException {
        return dao.getClientOwnersEmail(clientId);
    }
    
    public boolean saveClientRelationships(int glossaryId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveClientRelationships(glossaryId, relationships, userId);
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    public List<Map<String, Object>> getGlossaryRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        return dao.getGlossaryRelationshipsByBusinessAreaId(businessAreaId);
    }
    
    public List<Map<String, Object>> getGlossaryRelationshipsByProcessId(int processId) throws SQLException {
        return dao.getGlossaryRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getGlossaryRelationshipsByPolicyId(int policyId) throws SQLException {
        return dao.getGlossaryRelationshipsByPolicyId(policyId);
    }
    
    public List<Map<String, Object>> getGlossaryRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        return dao.getGlossaryRelationshipsByCapabilityId(capabilityId);
    }
    
    public List<Map<String, Object>> getGlossaryRelationshipsByClientId(int clientId) throws SQLException {
        return dao.getGlossaryRelationshipsByClientId(clientId);
    }
    
    public List<Map<String, Object>> getGlossaryRelationshipsByProductId(int productId) throws SQLException {
        return dao.getGlossaryRelationshipsByProductId(productId);
    }
}

