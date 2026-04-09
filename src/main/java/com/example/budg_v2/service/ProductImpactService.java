package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProductImpactDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ProductImpactService {
    
    private ProductImpactDAO dao;
    
    public ProductImpactService() {
        this.dao = new ProductImpactDAO();
    }
    
    // ===== LEGAL ENTITY =====
    
    public List<Map<String, Object>> getLegalRelationshipsByProductId(int productId) throws SQLException {
        return dao.getLegalRelationshipsByProductId(productId);
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
    
    public boolean saveLegalRelationships(int productId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveLegalRelationships(productId, relationships, userId);
    }
    
    // ===== CLIENT =====
    
    public List<Map<String, Object>> getClientRelationshipsByProductId(int productId) throws SQLException {
        return dao.getClientRelationshipsByProductId(productId);
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
    
    public boolean saveClientRelationships(int productId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveClientRelationships(productId, relationships, userId);
    }
    
    // ===== BUSINESS AREA =====
    
    public List<Map<String, Object>> getBusinessAreaRelationshipsByProductId(int productId) throws SQLException {
        return dao.getBusinessAreaRelationshipsByProductId(productId);
    }
    
    public List<Map<String, Object>> getBusinessAreaRelationTypes() throws SQLException {
        return dao.getBusinessAreaRelationTypes();
    }
    
    public String getBusinessAreaOwnersString(int businessAreaId) throws SQLException {
        return dao.getBusinessAreaOwnersString(businessAreaId);
    }
    
    public String getBusinessAreaOwnersEmail(int businessAreaId) throws SQLException {
        return dao.getBusinessAreaOwnersEmail(businessAreaId);
    }
    
    public boolean saveBusinessAreaRelationships(int productId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveBusinessAreaRelationships(productId, relationships, userId);
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    public List<Map<String, Object>> getProductRelationshipsByClientId(int clientId) throws SQLException {
        return dao.getProductRelationshipsByClientId(clientId);
    }
    
    public List<Map<String, Object>> getProductRelationshipsByLegalId(int legalId) throws SQLException {
        return dao.getProductRelationshipsByLegalId(legalId);
    }
    
    public List<Map<String, Object>> getProductRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        return dao.getProductRelationshipsByBusinessAreaId(businessAreaId);
    }
}

