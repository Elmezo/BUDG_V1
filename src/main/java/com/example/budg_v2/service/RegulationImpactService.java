package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationImpactDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class RegulationImpactService {
    
    private RegulationImpactDAO dao;
    
    public RegulationImpactService() {
        this.dao = new RegulationImpactDAO();
    }
    
    // Product
    public List<Map<String, Object>> getProductRelationshipsByRegulationId(int regulationId) throws SQLException {
        return dao.getProductRelationshipsByRegulationId(regulationId);
    }
    
    // Reverse lookup: Get Regulation relationships by Product ID
    public List<Map<String, Object>> getRegulationRelationshipsByProductId(int productId) throws SQLException {
        return dao.getRegulationRelationshipsByProductId(productId);
    }
    
    // Reverse lookup: Get Regulation relationships by Policy ID
    public List<Map<String, Object>> getRegulationRelationshipsByPolicyId(int policyId) throws SQLException {
        return dao.getRegulationRelationshipsByPolicyId(policyId);
    }
    
    // Reverse lookup: Get Regulation relationships by Project ID
    public List<Map<String, Object>> getRegulationRelationshipsByProjectId(int projectId) throws SQLException {
        return dao.getRegulationRelationshipsByProjectId(projectId);
    }
    
    // Get regulation owners
    public String getRegulationOwnersString(int regulationId) throws SQLException {
        return dao.getRegulationOwnersString(regulationId);
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
    
    public boolean saveProductRelationships(int regulationId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveProductRelationships(regulationId, relationships, userId);
    }
    
    // Policy
    public List<Map<String, Object>> getPolicyRelationshipsByRegulationId(int regulationId) throws SQLException {
        return dao.getPolicyRelationshipsByRegulationId(regulationId);
    }
    
    public List<Map<String, Object>> getPolicyRelationTypes() throws SQLException {
        return dao.getPolicyRelationTypes();
    }
    
    public String getPolicyOwnersString(int policyId) throws SQLException {
        return dao.getPolicyOwnersString(policyId);
    }
    
    public String getPolicyOwnersEmail(int policyId) throws SQLException {
        return dao.getPolicyOwnersEmail(policyId);
    }
    
    public boolean savePolicyRelationships(int regulationId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.savePolicyRelationships(regulationId, relationships, userId);
    }
    
    // Project
    public List<Map<String, Object>> getProjectRelationshipsByRegulationId(int regulationId) throws SQLException {
        return dao.getProjectRelationshipsByRegulationId(regulationId);
    }
    
    public List<Map<String, Object>> getProjectRelationTypes() throws SQLException {
        return dao.getProjectRelationTypes();
    }
    
    public String getProjectOwnersString(int projectId) throws SQLException {
        return dao.getProjectOwnersString(projectId);
    }
    
    public String getProjectOwnersEmail(int projectId) throws SQLException {
        return dao.getProjectOwnersEmail(projectId);
    }
    
    public boolean saveProjectRelationships(int regulationId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveProjectRelationships(regulationId, relationships, userId);
    }
    
    // Regulatory Theme
    public List<Map<String, Object>> getRegulatoryThemeRelationshipsByRegulationId(int regulationId) throws SQLException {
        return dao.getRegulatoryThemeRelationshipsByRegulationId(regulationId);
    }
    
    public List<Map<String, Object>> getRegulatoryThemeRelationTypes() throws SQLException {
        return dao.getRegulatoryThemeRelationTypes();
    }
    
    public boolean saveRegulatoryThemeRelationships(int regulationId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveRegulatoryThemeRelationships(regulationId, relationships, userId);
    }
}

