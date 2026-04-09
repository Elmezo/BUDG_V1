package com.example.budg_v2.service;

import com.example.budg_v2.dao.SystemImpactDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class SystemImpactService {
    
    private SystemImpactDAO dao;
    
    public SystemImpactService() {
        this.dao = new SystemImpactDAO();
    }
    
    // ===== PRODUCT METHODS =====
    
    public List<Map<String, Object>> getProductRelationshipsBySystemId(int systemId) throws SQLException {
        return dao.getProductRelationshipsBySystemId(systemId);
    }
    
    public List<Map<String, Object>> getProductSystemRelationTypes() throws SQLException {
        return dao.getProductSystemRelationTypes();
    }
    
    public List<Map<String, Object>> getProductLegalRelationTypes() throws SQLException {
        return dao.getProductLegalRelationTypes();
    }
    
    public String getProductOwnersString(int productId) throws SQLException {
        return dao.getProductOwnersString(productId);
    }
    
    public String getProductOwnersEmail(int productId) throws SQLException {
        return dao.getProductOwnersEmail(productId);
    }
    
    public boolean saveProductRelationships(int systemId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveProductRelationships(systemId, relationships, userId);
    }
    
    // ===== CLIENT METHODS =====
    
    public List<Map<String, Object>> getClientRelationshipsBySystemId(int systemId) throws SQLException {
        return dao.getClientRelationshipsBySystemId(systemId);
    }
    
    public List<Map<String, Object>> getClientSystemRelationTypes() throws SQLException {
        return dao.getClientSystemRelationTypes();
    }
    
    public String getClientOwnersString(int clientId) throws SQLException {
        return dao.getClientOwnersString(clientId);
    }
    
    public String getClientOwnersEmail(int clientId) throws SQLException {
        return dao.getClientOwnersEmail(clientId);
    }
    
    public boolean saveClientRelationships(int systemId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveClientRelationships(systemId, relationships, userId);
    }
    
    // ===== LEGAL ENTITY METHODS =====
    
    public List<Map<String, Object>> getLegalRelationshipsBySystemId(int systemId) throws SQLException {
        return dao.getLegalRelationshipsBySystemId(systemId);
    }
    
    public List<Map<String, Object>> getLegalRelationTypes() throws SQLException {
        return dao.getLegalRelationTypes();
    }
    
    public String getLegalOwnersString(int legalId) throws SQLException {
        return dao.getLegalOwnersString(legalId);
    }
    
    public String getLegalOwnersEmail(int legalId) throws SQLException {
        return dao.getLegalOwnersEmail(legalId);
    }
    
    public boolean saveLegalRelationships(int systemId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveLegalRelationships(systemId, relationships, userId);
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    public List<Map<String, Object>> getSystemRelationshipsByProductId(int productId) throws SQLException {
        return dao.getSystemRelationshipsByProductId(productId);
    }
    
    public List<Map<String, Object>> getSystemRelationshipsByClientId(int clientId) throws SQLException {
        return dao.getSystemRelationshipsByClientId(clientId);
    }
    
    public List<Map<String, Object>> getSystemRelationshipsByLegalId(int legalId) throws SQLException {
        return dao.getSystemRelationshipsByLegalId(legalId);
    }
}

