package com.example.budg_v2.service;

import com.example.budg_v2.dao.DatasetImpactDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class DatasetImpactService {
    private final DatasetImpactDAO datasetImpactDAO;

    public DatasetImpactService() {
        this.datasetImpactDAO = new DatasetImpactDAO();
    }

    // ===== PRODUCT METHODS =====
    
    public List<Map<String, Object>> getProductRelationshipsByDatasetId(int datasetId) throws SQLException {
        return datasetImpactDAO.getProductRelationshipsByDatasetId(datasetId);
    }

    public List<Map<String, Object>> getProductRelationTypes() throws SQLException {
        return datasetImpactDAO.getProductRelationTypes();
    }

    public boolean saveProductRelationships(int datasetId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return datasetImpactDAO.saveProductRelationships(datasetId, relationships, userId);
    }

    public String getProductOwnersString(int productId) throws SQLException {
        return datasetImpactDAO.getProductOwnersString(productId);
    }

    public String getProductOwnersEmail(int productId) throws SQLException {
        return datasetImpactDAO.getProductOwnersEmail(productId);
    }

    // ===== CLIENT METHODS =====
    
    public List<Map<String, Object>> getClientRelationshipsByDatasetId(int datasetId) throws SQLException {
        return datasetImpactDAO.getClientRelationshipsByDatasetId(datasetId);
    }

    public List<Map<String, Object>> getClientRelationTypes() throws SQLException {
        return datasetImpactDAO.getClientRelationTypes();
    }

    public boolean saveClientRelationships(int datasetId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return datasetImpactDAO.saveClientRelationships(datasetId, relationships, userId);
    }

    public String getClientOwnersString(int clientId) throws SQLException {
        return datasetImpactDAO.getClientOwnersString(clientId);
    }

    public String getClientOwnersEmail(int clientId) throws SQLException {
        return datasetImpactDAO.getClientOwnersEmail(clientId);
    }

    // ===== LEGAL ENTITY METHODS =====
    
    public List<Map<String, Object>> getLegalRelationshipsByDatasetId(int datasetId) throws SQLException {
        return datasetImpactDAO.getLegalRelationshipsByDatasetId(datasetId);
    }

    public List<Map<String, Object>> getLegalRelationTypes() throws SQLException {
        return datasetImpactDAO.getLegalRelationTypes();
    }

    public boolean saveLegalRelationships(int datasetId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return datasetImpactDAO.saveLegalRelationships(datasetId, relationships, userId);
    }

    public String getLegalOwnersString(int legalId) throws SQLException {
        return datasetImpactDAO.getLegalOwnersString(legalId);
    }

    public String getLegalOwnersEmail(int legalId) throws SQLException {
        return datasetImpactDAO.getLegalOwnersEmail(legalId);
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    public List<Map<String, Object>> getDatasetRelationshipsByProductId(int productId) throws SQLException {
        return datasetImpactDAO.getDatasetRelationshipsByProductId(productId);
    }
    
    public List<Map<String, Object>> getDatasetRelationshipsByClientId(int clientId) throws SQLException {
        return datasetImpactDAO.getDatasetRelationshipsByClientId(clientId);
    }
    
    public List<Map<String, Object>> getDatasetRelationshipsByLegalId(int legalId) throws SQLException {
        return datasetImpactDAO.getDatasetRelationshipsByLegalId(legalId);
    }
}

