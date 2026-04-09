package com.example.budg_v2.service;

import com.example.budg_v2.dao.PolicyImpactDAO;

import java.util.List;
import java.util.Map;

public class PolicyImpactService {
    private final PolicyImpactDAO policyImpactDAO;

    public PolicyImpactService() {
        this.policyImpactDAO = new PolicyImpactDAO();
    }

    /**
     * Get product relationships for a policy with owner information
     */
    public List<Map<String, Object>> getProductRelationships(int policyId) throws Exception {
        return policyImpactDAO.getProductRelationshipsByPolicyId(policyId);
    }

    /**
     * Get client relationships for a policy with owner information
     */
    public List<Map<String, Object>> getClientRelationships(int policyId) throws Exception {
        return policyImpactDAO.getClientRelationshipsByPolicyId(policyId);
    }

    /**
     * Get all product relation types
     */
    public List<Map<String, Object>> getProductRelationTypes() throws Exception {
        return policyImpactDAO.getProductRelationTypes();
    }

    /**
     * Get all client relation types
     */
    public List<Map<String, Object>> getClientRelationTypes() throws Exception {
        return policyImpactDAO.getClientRelationTypes();
    }

    /**
     * Save product relationships (add/update/delete)
     */
    public boolean saveProductRelationships(int policyId, List<Map<String, Object>> relationships, int userId) throws Exception {
        return policyImpactDAO.saveProductRelationships(policyId, relationships, userId);
    }

    /**
     * Save client relationships (add/update/delete)
     */
    public boolean saveClientRelationships(int policyId, List<Map<String, Object>> relationships, int userId) throws Exception {
        return policyImpactDAO.saveClientRelationships(policyId, relationships, userId);
    }

    /**
     * Get product owner by product ID
     */
    public Map<String, Object> getProductOwner(int productId) throws Exception {
        return policyImpactDAO.getProductOwnerByProductId(productId);
    }

    /**
     * Get all product owners as concatenated string
     */
    public String getProductOwnersString(int productId) throws Exception {
        return policyImpactDAO.getProductOwnersString(productId);
    }

    /**
     * Get all product owner emails as concatenated string
     */
    public String getProductOwnersEmail(int productId) throws Exception {
        return policyImpactDAO.getProductOwnersEmail(productId);
    }

    /**
     * Get client owner by client ID
     */
    public Map<String, Object> getClientOwner(int clientId) throws Exception {
        return policyImpactDAO.getClientOwnerByClientId(clientId);
    }

    /**
     * Get all client owners as concatenated string
     */
    public String getClientOwnersString(int clientId) throws Exception {
        return policyImpactDAO.getClientOwnersString(clientId);
    }

    /**
     * Get all client owner emails as concatenated string
     */
    public String getClientOwnersEmail(int clientId) throws Exception {
        return policyImpactDAO.getClientOwnersEmail(clientId);
    }

    /**
     * Validate database schema
     */
    public boolean validateSchema() throws Exception {
        return policyImpactDAO.validateSchema();
    }

    /**
     * Get process relationships for a policy with owner information
     */
    public List<Map<String, Object>> getProcessRelationships(int policyId) throws Exception {
        return policyImpactDAO.getProcessRelationshipsByPolicyId(policyId);
    }

    /**
     * Get project relationships for a policy with owner information
     */
    public List<Map<String, Object>> getProjectRelationships(int policyId) throws Exception {
        return policyImpactDAO.getProjectRelationshipsByPolicyId(policyId);
    }

    /**
     * Get all process relation types
     */
    public List<Map<String, Object>> getProcessRelationTypes() throws Exception {
        return policyImpactDAO.getProcessRelationTypes();
    }

    /**
     * Get all project relation types
     */
    public List<Map<String, Object>> getProjectRelationTypes() throws Exception {
        return policyImpactDAO.getProjectRelationTypes();
    }

    /**
     * Save process relationships for a policy
     */
    public boolean saveProcessRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return policyImpactDAO.saveProcessRelationships(policyId, jsonData, userId);
    }

    /**
     * Save project relationships for a policy
     */
    public boolean saveProjectRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return policyImpactDAO.saveProjectRelationships(policyId, jsonData, userId);
    }

    /**
     * Get process owner by process ID
     */
    public Map<String, Object> getProcessOwner(int processId) throws Exception {
        return policyImpactDAO.getProcessOwnerByProcessId(processId);
    }

    /**
     * Get all process owners as concatenated string
     */
    public String getProcessOwnersString(int processId) throws Exception {
        return policyImpactDAO.getProcessOwnersString(processId);
    }

    /**
     * Get all process owner emails as concatenated string
     */
    public String getProcessOwnersEmail(int processId) throws Exception {
        return policyImpactDAO.getProcessOwnersEmail(processId);
    }

    /**
     * Get project owner by project ID
     */
    public Map<String, Object> getProjectOwner(int projectId) throws Exception {
        return policyImpactDAO.getProjectOwnerByProjectId(projectId);
    }

    /**
     * Get all project owners as concatenated string
     */
    public String getProjectOwnersString(int projectId) throws Exception {
        return policyImpactDAO.getProjectOwnersString(projectId);
    }

    /**
     * Get all project owner emails as concatenated string
     */
    public String getProjectOwnersEmail(int projectId) throws Exception {
        return policyImpactDAO.getProjectOwnersEmail(projectId);
    }

    // System methods
    /**
     * Get system relationships for a policy with owner information
     */
    public List<Map<String, Object>> getSystemRelationships(int policyId) throws Exception {
        return policyImpactDAO.getSystemRelationshipsByPolicyId(policyId);
    }

    /**
     * Get all system relation types
     */
    public List<Map<String, Object>> getSystemRelationTypes() throws Exception {
        return policyImpactDAO.getSystemRelationTypes();
    }

    /**
     * Save system relationships for a policy
     */
    public boolean saveSystemRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return policyImpactDAO.saveSystemRelationships(policyId, jsonData, userId);
    }

    /**
     * Get system owner by system ID
     */
    public Map<String, Object> getSystemOwner(int systemId) throws Exception {
        return policyImpactDAO.getSystemOwnerBySystemId(systemId);
    }

    // Business Area methods
    /**
     * Get business area relationships for a policy with owner information
     */
    public List<Map<String, Object>> getBusinessAreaRelationships(int policyId) throws Exception {
        return policyImpactDAO.getBusinessAreaRelationshipsByPolicyId(policyId);
    }

    /**
     * Get all business area relation types
     */
    public List<Map<String, Object>> getBusinessAreaRelationTypes() throws Exception {
        return policyImpactDAO.getBusinessAreaRelationTypes();
    }

    /**
     * Save business area relationships for a policy
     */
    public boolean saveBusinessAreaRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return policyImpactDAO.saveBusinessAreaRelationships(policyId, jsonData, userId);
    }

    /**
     * Get business area owner by business area ID
     */
    public Map<String, Object> getBusinessAreaOwner(int businessAreaId) throws Exception {
        return policyImpactDAO.getBusinessAreaOwnerByBusinessAreaId(businessAreaId);
    }

    // Legal Entity methods
    /**
     * Get legal relationships for a policy with owner information
     */
    public List<Map<String, Object>> getLegalRelationships(int policyId) throws Exception {
        return policyImpactDAO.getLegalRelationshipsByPolicyId(policyId);
    }

    /**
     * Get all legal relation types
     */
    public List<Map<String, Object>> getLegalRelationTypes() throws Exception {
        return policyImpactDAO.getLegalRelationTypes();
    }

    /**
     * Save legal relationships for a policy
     */
    public boolean saveLegalRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return policyImpactDAO.saveLegalRelationships(policyId, jsonData, userId);
    }

    /**
     * Get legal owner by legal ID
     */
    public Map<String, Object> getLegalOwner(int legalId) throws Exception {
        return policyImpactDAO.getLegalOwnerByLegalId(legalId);
    }

    // Dataset methods
    /**
     * Get dataset relationships for a policy with owner information
     */
    public List<Map<String, Object>> getDatasetRelationships(int policyId) throws Exception {
        return policyImpactDAO.getDatasetRelationshipsByPolicyId(policyId);
    }

    /**
     * Get all dataset relation types
     */
    public List<Map<String, Object>> getDatasetRelationTypes() throws Exception {
        return policyImpactDAO.getDatasetRelationTypes();
    }

    /**
     * Save dataset relationships for a policy
     */
    public boolean saveDatasetRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return policyImpactDAO.saveDatasetRelationships(policyId, jsonData, userId);
    }

    /**
     * Get dataset owner by dataset ID
     */
    public Map<String, Object> getDatasetOwner(int datasetId) throws Exception {
        return policyImpactDAO.getDatasetOwnerByDatasetId(datasetId);
    }

    /**
     * Get all dataset owners as concatenated string
     */
    public String getDatasetOwnersString(int datasetId) throws Exception {
        return policyImpactDAO.getDatasetOwnersString(datasetId);
    }

    /**
     * Get all dataset owner emails as concatenated string
     */
    public String getDatasetOwnersEmail(int datasetId) throws Exception {
        return policyImpactDAO.getDatasetOwnersEmail(datasetId);
    }

    // Attribute methods
    /**
     * Get attribute relationships for a policy with owner information
     */
    public List<Map<String, Object>> getAttributeRelationships(int policyId) throws Exception {
        return policyImpactDAO.getAttributeRelationshipsByPolicyId(policyId);
    }

    /**
     * Get all attribute relation types
     */
    public List<Map<String, Object>> getAttributeRelationTypes() throws Exception {
        return policyImpactDAO.getAttributeRelationTypes();
    }

    /**
     * Get all datasets list
     */
    public List<Map<String, Object>> getDatasetsList() throws Exception {
        return policyImpactDAO.getDatasetsList();
    }

    /**
     * Get all attributes list
     */
    public List<Map<String, Object>> getAttributesList() throws Exception {
        return policyImpactDAO.getAttributesList();
    }

    /**
     * Save attribute relationships for a policy
     */
    public boolean saveAttributeRelationships(int policyId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return policyImpactDAO.saveAttributeRelationships(policyId, jsonData, userId);
    }

    /**
     * Get attribute owner by attribute ID
     */
    public Map<String, Object> getAttributeOwner(int attributeId) throws Exception {
        return policyImpactDAO.getAttributeOwnerByAttributeId(attributeId);
    }

    /**
     * Get all attribute owners as concatenated string
     */
    public String getAttributeOwnersString(int attributeId) throws Exception {
        return policyImpactDAO.getAttributeOwnersString(attributeId);
    }

    /**
     * Get all attribute owner emails as concatenated string
     */
    public String getAttributeOwnersEmail(int attributeId) throws Exception {
        return policyImpactDAO.getAttributeOwnersEmail(attributeId);
    }
    
    // ==================== GLOSSARY METHODS ====================
    
    /**
     * Get glossary relationships for a policy
     */
    public List<Map<String, Object>> getGlossaryRelationships(int policyId) throws Exception {
        return policyImpactDAO.getGlossaryRelationshipsByPolicyId(policyId);
    }
    
    /**
     * Get glossary relation types
     */
    public List<Map<String, Object>> getGlossaryRelationTypes() throws Exception {
        return policyImpactDAO.getGlossaryRelationTypes();
    }
    
    /**
     * Save glossary relationships
     */
    public boolean saveGlossaryRelationships(int policyId, List<Map<String, Object>> relationships, int userId) throws Exception {
        return policyImpactDAO.saveGlossaryRelationships(policyId, relationships, userId);
    }
    
    /**
     * Get all glossary owners as concatenated string
     */
    public String getGlossaryOwnersString(int glossaryId) throws Exception {
        return policyImpactDAO.getGlossaryOwnersString(glossaryId);
    }
    
    /**
     * Get all glossary owner emails as concatenated string
     */
    public String getGlossaryOwnersEmail(int glossaryId) throws Exception {
        return policyImpactDAO.getGlossaryOwnersEmail(glossaryId);
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    public List<Map<String, Object>> getPolicyRelationshipsByProductId(int productId) throws Exception {
        return policyImpactDAO.getPolicyRelationshipsByProductId(productId);
    }
    
    public List<Map<String, Object>> getPolicyRelationshipsByClientId(int clientId) throws Exception {
        return policyImpactDAO.getPolicyRelationshipsByClientId(clientId);
    }
    
    public List<Map<String, Object>> getPolicyRelationshipsByProcessId(int processId) throws Exception {
        return policyImpactDAO.getPolicyRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getPolicyRelationshipsByProjectId(int projectId) throws Exception {
        return policyImpactDAO.getPolicyRelationshipsByProjectId(projectId);
    }
    
    public List<Map<String, Object>> getPolicyRelationshipsBySystemId(int systemId) throws Exception {
        return policyImpactDAO.getPolicyRelationshipsBySystemId(systemId);
    }
    
    public List<Map<String, Object>> getPolicyRelationshipsByBusinessAreaId(int businessAreaId) throws Exception {
        return policyImpactDAO.getPolicyRelationshipsByBusinessAreaId(businessAreaId);
    }
    
    public List<Map<String, Object>> getPolicyRelationshipsByLegalId(int legalId) throws Exception {
        return policyImpactDAO.getPolicyRelationshipsByLegalId(legalId);
    }
    
    public List<Map<String, Object>> getPolicyRelationshipsByGlossaryId(int glossaryId) throws Exception {
        return policyImpactDAO.getPolicyRelationshipsByGlossaryId(glossaryId);
    }
    
    public List<Map<String, Object>> getPolicyRelationshipsByDatasetId(int datasetId) throws Exception {
        return policyImpactDAO.getPolicyRelationshipsByDatasetId(datasetId);
    }
}
