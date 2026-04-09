package com.example.budg_v2.service;

import com.example.budg_v2.dao.CapabilityImpactDAO;

import java.util.List;
import java.util.Map;

public class CapabilityImpactService {
    private final CapabilityImpactDAO capabilityImpactDAO;

    public CapabilityImpactService() {
        this.capabilityImpactDAO = new CapabilityImpactDAO();
    }

    // ==================== SYSTEM METHODS ====================
    
    /**
     * Get system relationships for a capability with owner information
     */
    public List<Map<String, Object>> getSystemRelationships(int capabilityId) throws Exception {
        try {
            return capabilityImpactDAO.getSystemRelationshipsByCapabilityId(capabilityId);
        } catch (Exception e) {
            System.err.println("CapabilityImpactService: Error in getSystemRelationships: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Get all system relation types
     */
    public List<Map<String, Object>> getSystemRelationTypes() throws Exception {
        return capabilityImpactDAO.getSystemRelationTypes();
    }

    /**
     * Save system relationships for a capability
     */
    public boolean saveSystemRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return capabilityImpactDAO.saveSystemRelationships(capabilityId, jsonData, userId);
    }

    /**
     * Get system owner by system ID
     */
    public Map<String, Object> getSystemOwner(int systemId) throws Exception {
        return capabilityImpactDAO.getSystemOwnerBySystemId(systemId);
    }

    /**
     * Get all system owners as concatenated string
     */
    public String getSystemOwnersString(int systemId) throws Exception {
        return capabilityImpactDAO.getSystemOwnersString(systemId);
    }

    /**
     * Get all system owner emails as concatenated string
     */
    public String getSystemOwnersEmail(int systemId) throws Exception {
        return capabilityImpactDAO.getSystemOwnersEmail(systemId);
    }

    // ==================== CLIENT METHODS ====================
    
    /**
     * Get client relationships for a capability with owner information
     */
    public List<Map<String, Object>> getClientRelationships(int capabilityId) throws Exception {
        return capabilityImpactDAO.getClientRelationshipsByCapabilityId(capabilityId);
    }

    /**
     * Get all client relation types
     */
    public List<Map<String, Object>> getClientRelationTypes() throws Exception {
        return capabilityImpactDAO.getClientRelationTypes();
    }

    /**
     * Save client relationships for a capability
     */
    public boolean saveClientRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return capabilityImpactDAO.saveClientRelationships(capabilityId, jsonData, userId);
    }

    /**
     * Get client owner by client ID
     */
    public Map<String, Object> getClientOwner(int clientId) throws Exception {
        return capabilityImpactDAO.getClientOwnerByClientId(clientId);
    }

    /**
     * Get all client owners as concatenated string
     */
    public String getClientOwnersString(int clientId) throws Exception {
        return capabilityImpactDAO.getClientOwnersString(clientId);
    }

    /**
     * Get all client owner emails as concatenated string
     */
    public String getClientOwnersEmail(int clientId) throws Exception {
        return capabilityImpactDAO.getClientOwnersEmail(clientId);
    }

    // ==================== PRODUCT METHODS ====================
    
    /**
     * Get product relationships for a capability with owner information
     */
    public List<Map<String, Object>> getProductRelationships(int capabilityId) throws Exception {
        return capabilityImpactDAO.getProductRelationshipsByCapabilityId(capabilityId);
    }

    /**
     * Get all product relation types
     */
    public List<Map<String, Object>> getProductRelationTypes() throws Exception {
        return capabilityImpactDAO.getProductRelationTypes();
    }

    /**
     * Save product relationships for a capability
     */
    public boolean saveProductRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return capabilityImpactDAO.saveProductRelationships(capabilityId, jsonData, userId);
    }

    /**
     * Get product owner by product ID
     */
    public Map<String, Object> getProductOwner(int productId) throws Exception {
        return capabilityImpactDAO.getProductOwnerByProductId(productId);
    }

    /**
     * Get all product owners as concatenated string
     */
    public String getProductOwnersString(int productId) throws Exception {
        return capabilityImpactDAO.getProductOwnersString(productId);
    }

    /**
     * Get all product owner emails as concatenated string
     */
    public String getProductOwnersEmail(int productId) throws Exception {
        return capabilityImpactDAO.getProductOwnersEmail(productId);
    }

    // ==================== PROCESS METHODS ====================
    
    /**
     * Get process relationships for a capability with owner information
     */
    public List<Map<String, Object>> getProcessRelationships(int capabilityId) throws Exception {
        return capabilityImpactDAO.getProcessRelationshipsByCapabilityId(capabilityId);
    }

    /**
     * Get all process relation types
     */
    public List<Map<String, Object>> getProcessRelationTypes() throws Exception {
        return capabilityImpactDAO.getProcessRelationTypes();
    }

    /**
     * Save process relationships for a capability
     */
    public boolean saveProcessRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return capabilityImpactDAO.saveProcessRelationships(capabilityId, jsonData, userId);
    }

    /**
     * Get process owner by process ID
     */
    public Map<String, Object> getProcessOwner(int processId) throws Exception {
        return capabilityImpactDAO.getProcessOwnerByProcessId(processId);
    }

    /**
     * Get all process owners as concatenated string
     */
    public String getProcessOwnersString(int processId) throws Exception {
        return capabilityImpactDAO.getProcessOwnersString(processId);
    }

    /**
     * Get all process owner emails as concatenated string
     */
    public String getProcessOwnersEmail(int processId) throws Exception {
        return capabilityImpactDAO.getProcessOwnersEmail(processId);
    }

    // ==================== GLOSSARY METHODS ====================
    
    /**
     * Get glossary relationships for a capability
     */
    public List<Map<String, Object>> getGlossaryRelationships(int capabilityId) throws Exception {
        return capabilityImpactDAO.getGlossaryRelationshipsByCapabilityId(capabilityId);
    }
    
    /**
     * Get glossary relation types
     */
    public List<Map<String, Object>> getGlossaryRelationTypes() throws Exception {
        return capabilityImpactDAO.getGlossaryRelationTypes();
    }
    
    /**
     * Save glossary relationships
     */
    public boolean saveGlossaryRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return capabilityImpactDAO.saveGlossaryRelationships(capabilityId, jsonData, userId);
    }
    
    /**
     * Get all glossary owners as concatenated string
     */
    public String getGlossaryOwnersString(int glossaryId) throws Exception {
        return capabilityImpactDAO.getGlossaryOwnersString(glossaryId);
    }
    
    /**
     * Get all glossary owner emails as concatenated string
     */
    public String getGlossaryOwnersEmail(int glossaryId) throws Exception {
        return capabilityImpactDAO.getGlossaryOwnersEmail(glossaryId);
    }
    
    /**
     * Get glossary owner by glossary ID
     */
    public Map<String, Object> getGlossaryOwner(int glossaryId) throws Exception {
        return capabilityImpactDAO.getGlossaryOwnerByGlossaryId(glossaryId);
    }

    // ==================== BUSINESS AREA METHODS ====================
    
    /**
     * Get business area relationships for a capability with owner information
     */
    public List<Map<String, Object>> getBusinessAreaRelationships(int capabilityId) throws Exception {
        return capabilityImpactDAO.getBusinessAreaRelationshipsByCapabilityId(capabilityId);
    }

    /**
     * Get all business area relation types
     */
    public List<Map<String, Object>> getBusinessAreaRelationTypes() throws Exception {
        return capabilityImpactDAO.getBusinessAreaRelationTypes();
    }

    /**
     * Save business area relationships for a capability
     */
    public boolean saveBusinessAreaRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return capabilityImpactDAO.saveBusinessAreaRelationships(capabilityId, jsonData, userId);
    }

    /**
     * Get business area owner by business area ID
     */
    public Map<String, Object> getBusinessAreaOwner(int businessAreaId) throws Exception {
        return capabilityImpactDAO.getBusinessAreaOwnerByBusinessAreaId(businessAreaId);
    }

    /**
     * Get all business area owners as concatenated string
     */
    public String getBusinessAreaOwnersString(int businessAreaId) throws Exception {
        return capabilityImpactDAO.getBusinessAreaOwnersString(businessAreaId);
    }

    /**
     * Get all business area owner emails as concatenated string
     */
    public String getBusinessAreaOwnersEmail(int businessAreaId) throws Exception {
        return capabilityImpactDAO.getBusinessAreaOwnersEmail(businessAreaId);
    }

    // ==================== LEGAL ENTITY METHODS ====================
    
    /**
     * Get legal entity relationships for a capability with owner information
     */
    public List<Map<String, Object>> getLegalRelationships(int capabilityId) throws Exception {
        return capabilityImpactDAO.getLegalRelationshipsByCapabilityId(capabilityId);
    }

    /**
     * Get all legal entity relation types
     */
    public List<Map<String, Object>> getLegalRelationTypes() throws Exception {
        return capabilityImpactDAO.getLegalRelationTypes();
    }

    /**
     * Save legal entity relationships for a capability
     */
    public boolean saveLegalRelationships(int capabilityId, com.google.gson.JsonObject jsonData, int userId) throws Exception {
        return capabilityImpactDAO.saveLegalRelationships(capabilityId, jsonData, userId);
    }

    /**
     * Get legal entity owner by legal ID
     */
    public Map<String, Object> getLegalOwner(int legalId) throws Exception {
        return capabilityImpactDAO.getLegalOwnerByLegalId(legalId);
    }

    /**
     * Get all legal entity owners as concatenated string
     */
    public String getLegalOwnersString(int legalId) throws Exception {
        return capabilityImpactDAO.getLegalOwnersString(legalId);
    }

    /**
     * Get all legal entity owner emails as concatenated string
     */
    public String getLegalOwnersEmail(int legalId) throws Exception {
        return capabilityImpactDAO.getLegalOwnersEmail(legalId);
    }
    
    // ==================== REVERSE LOOKUP METHODS ====================
    
    public List<Map<String, Object>> getCapabilityRelationshipsBySystemId(int systemId) throws Exception {
        return capabilityImpactDAO.getCapabilityRelationshipsBySystemId(systemId);
    }
    
    public List<Map<String, Object>> getCapabilityRelationshipsByClientId(int clientId) throws Exception {
        return capabilityImpactDAO.getCapabilityRelationshipsByClientId(clientId);
    }
    
    public List<Map<String, Object>> getCapabilityRelationshipsByProductId(int productId) throws Exception {
        return capabilityImpactDAO.getCapabilityRelationshipsByProductId(productId);
    }
    
    public List<Map<String, Object>> getCapabilityRelationshipsByProcessId(int processId) throws Exception {
        return capabilityImpactDAO.getCapabilityRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getCapabilityRelationshipsByGlossaryId(int glossaryId) throws Exception {
        return capabilityImpactDAO.getCapabilityRelationshipsByGlossaryId(glossaryId);
    }
    
    public List<Map<String, Object>> getCapabilityRelationshipsByBusinessAreaId(int businessAreaId) throws Exception {
        return capabilityImpactDAO.getCapabilityRelationshipsByBusinessAreaId(businessAreaId);
    }
    
    public List<Map<String, Object>> getCapabilityRelationshipsByLegalId(int legalId) throws Exception {
        return capabilityImpactDAO.getCapabilityRelationshipsByLegalId(legalId);
    }
}

