package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProcessImpactDAO;
import com.example.budg_v2.util.UserContextUtil;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ProcessImpactService {
    private final ProcessImpactDAO processImpactDAO;
    
    public ProcessImpactService() {
        this.processImpactDAO = new ProcessImpactDAO();
    }
    
    // ===== SYSTEM =====
    
    public List<Map<String, Object>> getSystemRelationships(int processId) throws SQLException {
        return processImpactDAO.getSystemRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getSystemRelationTypes() throws SQLException {
        return processImpactDAO.getSystemRelationTypes();
    }
    
    public boolean saveSystemRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        return processImpactDAO.saveSystemRelationships(processId, relationships, userId);
    }
    
    public String getSystemOwnersString(int systemId) throws SQLException {
        return processImpactDAO.getSystemOwnersString(systemId);
    }
    
    public String getSystemOwnersEmail(int systemId) throws SQLException {
        return processImpactDAO.getSystemOwnersEmail(systemId);
    }
    
    // ===== PRODUCT =====
    
    public List<Map<String, Object>> getProductRelationships(int processId) throws SQLException {
        return processImpactDAO.getProductRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getProductRelationTypes() throws SQLException {
        return processImpactDAO.getProductRelationTypes();
    }
    
    public boolean saveProductRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        return processImpactDAO.saveProductRelationships(processId, relationships, userId);
    }
    
    public String getProductOwnersString(int productId) throws SQLException {
        return processImpactDAO.getProductOwnersString(productId);
    }
    
    public String getProductOwnersEmail(int productId) throws SQLException {
        return processImpactDAO.getProductOwnersEmail(productId);
    }
    
    // ===== CLIENT =====
    
    public List<Map<String, Object>> getClientRelationships(int processId) throws SQLException {
        return processImpactDAO.getClientRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getClientRelationTypes() throws SQLException {
        return processImpactDAO.getClientRelationTypes();
    }
    
    public boolean saveClientRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        return processImpactDAO.saveClientRelationships(processId, relationships, userId);
    }
    
    public String getClientOwnersString(int clientId) throws SQLException {
        return processImpactDAO.getClientOwnersString(clientId);
    }
    
    public String getClientOwnersEmail(int clientId) throws SQLException {
        return processImpactDAO.getClientOwnersEmail(clientId);
    }
    
    // ===== GLOSSARY =====
    
    public List<Map<String, Object>> getGlossaryRelationships(int processId) throws SQLException {
        return processImpactDAO.getGlossaryRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getGlossaryRelationTypes() throws SQLException {
        return processImpactDAO.getGlossaryRelationTypes();
    }
    
    public boolean saveGlossaryRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        return processImpactDAO.saveGlossaryRelationships(processId, relationships, userId);
    }
    
    public String getGlossaryOwnersString(int glossaryId) throws SQLException {
        return processImpactDAO.getGlossaryOwnersString(glossaryId);
    }
    
    public String getGlossaryOwnersEmail(int glossaryId) throws SQLException {
        return processImpactDAO.getGlossaryOwnersEmail(glossaryId);
    }
    
    // ===== PROJECT =====
    
    public List<Map<String, Object>> getProjectRelationships(int processId) throws SQLException {
        return processImpactDAO.getProjectRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getProjectRelationTypes() throws SQLException {
        return processImpactDAO.getProjectRelationTypes();
    }
    
    public boolean saveProjectRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        return processImpactDAO.saveProjectRelationships(processId, relationships, userId);
    }
    
    public String getProjectOwnersString(int projectId) throws SQLException {
        return processImpactDAO.getProjectOwnersString(projectId);
    }
    
    public String getProjectOwnersEmail(int projectId) throws SQLException {
        return processImpactDAO.getProjectOwnersEmail(projectId);
    }
    
    // ===== POLICY =====
    
    public List<Map<String, Object>> getPolicyRelationships(int processId) throws SQLException {
        return processImpactDAO.getPolicyRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getPolicyRelationTypes() throws SQLException {
        return processImpactDAO.getPolicyRelationTypes();
    }
    
    public boolean savePolicyRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        return processImpactDAO.savePolicyRelationships(processId, relationships, userId);
    }
    
    public String getPolicyOwnersString(int policyId) throws SQLException {
        return processImpactDAO.getPolicyOwnersString(policyId);
    }
    
    public String getPolicyOwnersEmail(int policyId) throws SQLException {
        return processImpactDAO.getPolicyOwnersEmail(policyId);
    }
    
    // ===== SYSTEM INTERFACE =====
    
    public List<Map<String, Object>> getInterfaceRelationships(int processId) throws SQLException {
        return processImpactDAO.getInterfaceRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getInterfaceRelationTypes() throws SQLException {
        return processImpactDAO.getInterfaceRelationTypes();
    }
    
    public List<Map<String, Object>> getAllInterfaces() throws SQLException {
        return processImpactDAO.getAllInterfaces();
    }
    
    public boolean saveInterfaceRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        return processImpactDAO.saveInterfaceRelationships(processId, relationships, userId);
    }
    
    public String getInterfaceOwnersString(int interfaceId) throws SQLException {
        return processImpactDAO.getInterfaceOwnersString(interfaceId);
    }
    
    public String getInterfaceOwnersEmail(int interfaceId) throws SQLException {
        return processImpactDAO.getInterfaceOwnersEmail(interfaceId);
    }
    
    // ===== LEGAL ENTITY =====
    
    public List<Map<String, Object>> getLegalRelationships(int processId) throws SQLException {
        return processImpactDAO.getLegalRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getLegalRelationTypes() throws SQLException {
        return processImpactDAO.getLegalRelationTypes();
    }
    
    public boolean saveLegalRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        return processImpactDAO.saveLegalRelationships(processId, relationships, userId);
    }
    
    public String getLegalOwnersString(int legalId) throws SQLException {
        return processImpactDAO.getLegalOwnersString(legalId);
    }
    
    public String getLegalOwnersEmail(int legalId) throws SQLException {
        return processImpactDAO.getLegalOwnersEmail(legalId);
    }
    
    // ===== DATASET =====
    
    public List<Map<String, Object>> getDatasetRelationships(int processId) throws SQLException {
        return processImpactDAO.getDatasetRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getDatasetRelationTypes() throws SQLException {
        return processImpactDAO.getDatasetRelationTypes();
    }
    
    public boolean saveDatasetRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        return processImpactDAO.saveDatasetRelationships(processId, relationships, userId);
    }
    
    public String getDatasetOwnersString(int datasetId) throws SQLException {
        return processImpactDAO.getDatasetOwnersString(datasetId);
    }
    
    public String getDatasetOwnersEmail(int datasetId) throws SQLException {
        return processImpactDAO.getDatasetOwnersEmail(datasetId);
    }
    
    public List<Map<String, Object>> getDatasetsList() throws SQLException {
        return processImpactDAO.getDatasetsList();
    }
    
    public List<Map<String, Object>> getProcessRelationshipsByDatasetId(int datasetId) throws SQLException {
        return processImpactDAO.getProcessRelationshipsByDatasetId(datasetId);
    }
    
    // ===== DATA ATTRIBUTES =====
    
    public List<Map<String, Object>> getAttributeRelationships(int processId) throws SQLException {
        return processImpactDAO.getAttributeRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getAttributeRelationTypes() throws SQLException {
        return processImpactDAO.getAttributeRelationTypes();
    }
    
    public boolean saveAttributeRelationships(int processId, List<Map<String, Object>> relationships, int userId) 
            throws SQLException {
        return processImpactDAO.saveAttributeRelationships(processId, relationships, userId);
    }
    
    public String getAttributeOwnersString(int attributeId) throws SQLException {
        return processImpactDAO.getAttributeOwnersString(attributeId);
    }
    
    public String getAttributeOwnersEmail(int attributeId) throws SQLException {
        return processImpactDAO.getAttributeOwnersEmail(attributeId);
    }
    
    public List<Map<String, Object>> getAttributesList() throws SQLException {
        return processImpactDAO.getAttributesList();
    }
    
    // ===== PREDECESSORS (PROCESS X PROCESS) =====
    
    public List<Map<String, Object>> getPredecessorRelationships(int processId) throws SQLException {
        return processImpactDAO.getPredecessorRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getSuccessorRelationships(int processId) throws SQLException {
        return processImpactDAO.getSuccessorRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getProcessRelationTypes() throws SQLException {
        return processImpactDAO.getProcessRelationTypes();
    }
    
    public boolean savePredecessorRelationships(int processId, List<Map<String, Object>> relationships, HttpServletRequest request)
            throws SQLException {
        return savePredecessorRelationships(processId, relationships, request, false);
    }

    /**
     * Overload that lets the caller request per-field audit history rows for the
     * predecessor change. Audit should only be written when the save targets the
     * original process row (no pending CR clone).
     */
    public boolean savePredecessorRelationships(int processId, List<Map<String, Object>> relationships,
                                                HttpServletRequest request, boolean writeAudit)
            throws SQLException {
        int userId = UserContextUtil.getCurrentUserId(request);
        if (userId <= 0) {
            throw new SQLException("User authentication required");
        }
        return processImpactDAO.savePredecessorRelationships(processId, relationships, userId, writeAudit);
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    public List<Map<String, Object>> getProcessRelationshipsBySystemId(int systemId) throws SQLException {
        return processImpactDAO.getProcessRelationshipsBySystemId(systemId);
    }
    
    public List<Map<String, Object>> getProcessRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        return processImpactDAO.getProcessRelationshipsByGlossaryId(glossaryId);
    }
    
    public List<Map<String, Object>> getProcessRelationshipsByClientId(int clientId) throws SQLException {
        return processImpactDAO.getProcessRelationshipsByClientId(clientId);
    }
    
    public List<Map<String, Object>> getProcessRelationshipsByProductId(int productId) throws SQLException {
        return processImpactDAO.getProcessRelationshipsByProductId(productId);
    }
    
    public List<Map<String, Object>> getProcessRelationshipsByLegalId(int legalId) throws SQLException {
        return processImpactDAO.getProcessRelationshipsByLegalId(legalId);
    }
}

