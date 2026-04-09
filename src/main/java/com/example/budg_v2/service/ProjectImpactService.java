package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProjectImpactDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ProjectImpactService {
    private final ProjectImpactDAO projectImpactDAO;

    public ProjectImpactService() {
        this.projectImpactDAO = new ProjectImpactDAO();
    }

    // System methods
    public List<Map<String, Object>> getSystemRelationshipsByProjectId(int projectId) throws SQLException {
        return projectImpactDAO.getSystemRelationshipsByProjectId(projectId);
    }

    public List<Map<String, Object>> getSystemRelationTypes() throws SQLException {
        return projectImpactDAO.getSystemRelationTypes();
    }

    public Map<String, Object> getSystemOwnerBySystemId(int systemId) throws SQLException {
        String ownerName = projectImpactDAO.getSystemOwnersString(systemId);
        String ownerEmail = projectImpactDAO.getSystemOwnersEmail(systemId);
        return Map.of("ownerName", ownerName != null ? ownerName : "", 
                     "ownerEmail", ownerEmail != null ? ownerEmail : "");
    }

    public boolean saveSystemRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return projectImpactDAO.saveSystemRelationships(projectId, relationships, userId);
    }

    // Process methods
    public List<Map<String, Object>> getProcessRelationshipsByProjectId(int projectId) throws SQLException {
        return projectImpactDAO.getProcessRelationshipsByProjectId(projectId);
    }

    public List<Map<String, Object>> getProcessRelationTypes() throws SQLException {
        return projectImpactDAO.getProcessRelationTypes();
    }

    public Map<String, Object> getProcessOwnerByProcessId(int processId) throws SQLException {
        String ownerName = projectImpactDAO.getProcessOwnersString(processId);
        String ownerEmail = projectImpactDAO.getProcessOwnersEmail(processId);
        return Map.of("ownerName", ownerName != null ? ownerName : "", 
                     "ownerEmail", ownerEmail != null ? ownerEmail : "");
    }

    public boolean saveProcessRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        // Validate cross-segment relationships
        if (relationships != null && !relationships.isEmpty()) {
            SegmentValidationService validationService = new SegmentValidationService();
            for (Map<String, Object> rel : relationships) {
                Object processIdObj = rel.get("processId");
                if (processIdObj != null) {
                    int processId = ((Number) processIdObj).intValue();
                    var validationResult = validationService.validateCrossSegmentRelationship(
                        projectId, "Project", processId, "Process");
                    if (!validationResult.isValid) {
                        throw new SQLException(validationResult.message);
                    }
                }
            }
        }
        return projectImpactDAO.saveProcessRelationships(projectId, relationships, userId);
    }

    // Glossary methods
    public List<Map<String, Object>> getGlossaryRelationshipsByProjectId(int projectId) throws SQLException {
        return projectImpactDAO.getGlossaryRelationshipsByProjectId(projectId);
    }

    public List<Map<String, Object>> getGlossaryRelationTypes() throws SQLException {
        return projectImpactDAO.getGlossaryRelationTypes();
    }

    public Map<String, Object> getGlossaryOwnerByGlossaryId(int glossaryId) throws SQLException {
        String ownerName = projectImpactDAO.getGlossaryOwnersString(glossaryId);
        String ownerEmail = projectImpactDAO.getGlossaryOwnersEmail(glossaryId);
        return Map.of("ownerName", ownerName != null ? ownerName : "", 
                     "ownerEmail", ownerEmail != null ? ownerEmail : "");
    }

    public boolean saveGlossaryRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        // Validate cross-segment relationships
        if (relationships != null && !relationships.isEmpty()) {
            SegmentValidationService validationService = new SegmentValidationService();
            for (Map<String, Object> rel : relationships) {
                Object glossaryIdObj = rel.get("glossaryId");
                if (glossaryIdObj != null) {
                    int glossaryId = ((Number) glossaryIdObj).intValue();
                    var validationResult = validationService.validateCrossSegmentRelationship(
                        projectId, "Project", glossaryId, "Glossary");
                    if (!validationResult.isValid) {
                        throw new SQLException(validationResult.message);
                    }
                }
            }
        }
        return projectImpactDAO.saveGlossaryRelationships(projectId, relationships, userId);
    }

    // Policy methods
    public List<Map<String, Object>> getPolicyRelationshipsByProjectId(int projectId) throws SQLException {
        return projectImpactDAO.getPolicyRelationshipsByProjectId(projectId);
    }

    public List<Map<String, Object>> getPolicyRelationTypes() throws SQLException {
        return projectImpactDAO.getPolicyRelationTypes();
    }

    public Map<String, Object> getPolicyOwnerByPolicyId(int policyId) throws SQLException {
        String ownerName = projectImpactDAO.getPolicyOwnersString(policyId);
        String ownerEmail = projectImpactDAO.getPolicyOwnersEmail(policyId);
        return Map.of("ownerName", ownerName != null ? ownerName : "", 
                     "ownerEmail", ownerEmail != null ? ownerEmail : "");
    }

    public boolean savePolicyRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return projectImpactDAO.savePolicyRelationships(projectId, relationships, userId);
    }

    // Product methods
    public List<Map<String, Object>> getProductRelationshipsByProjectId(int projectId) throws SQLException {
        return projectImpactDAO.getProductRelationshipsByProjectId(projectId);
    }

    public List<Map<String, Object>> getProductRelationTypes() throws SQLException {
        return projectImpactDAO.getProductRelationTypes();
    }

    public Map<String, Object> getProductOwnerByProductId(int productId) throws SQLException {
        String ownerName = projectImpactDAO.getProductOwnersString(productId);
        String ownerEmail = projectImpactDAO.getProductOwnersEmail(productId);
        return Map.of("ownerName", ownerName != null ? ownerName : "", 
                     "ownerEmail", ownerEmail != null ? ownerEmail : "");
    }

    public boolean saveProductRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return projectImpactDAO.saveProductRelationships(projectId, relationships, userId);
    }

    // Client methods
    public List<Map<String, Object>> getClientRelationshipsByProjectId(int projectId) throws SQLException {
        return projectImpactDAO.getClientRelationshipsByProjectId(projectId);
    }

    public List<Map<String, Object>> getClientRelationTypes() throws SQLException {
        return projectImpactDAO.getClientRelationTypes();
    }

    public Map<String, Object> getClientOwnerByClientId(int clientId) throws SQLException {
        String ownerName = projectImpactDAO.getClientOwnersString(clientId);
        String ownerEmail = projectImpactDAO.getClientOwnersEmail(clientId);
        return Map.of("ownerName", ownerName != null ? ownerName : "", 
                     "ownerEmail", ownerEmail != null ? ownerEmail : "");
    }

    public boolean saveClientRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return projectImpactDAO.saveClientRelationships(projectId, relationships, userId);
    }

    // Capability methods
    public List<Map<String, Object>> getCapabilityRelationshipsByProjectId(int projectId) throws SQLException {
        return projectImpactDAO.getCapabilityRelationshipsByProjectId(projectId);
    }

    public List<Map<String, Object>> getCapabilityRelationTypes() throws SQLException {
        return projectImpactDAO.getCapabilityRelationTypes();
    }

    public Map<String, Object> getCapabilityOwnerByCapabilityId(int capabilityId) throws SQLException {
        String ownerName = projectImpactDAO.getCapabilityOwnersString(capabilityId);
        String ownerEmail = projectImpactDAO.getCapabilityOwnersEmail(capabilityId);
        return Map.of("ownerName", ownerName != null ? ownerName : "", 
                     "ownerEmail", ownerEmail != null ? ownerEmail : "");
    }

    public boolean saveCapabilityRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return projectImpactDAO.saveCapabilityRelationships(projectId, relationships, userId);
    }

    // Business Area methods
    public List<Map<String, Object>> getBusinessAreaRelationshipsByProjectId(int projectId) throws SQLException {
        return projectImpactDAO.getBusinessAreaRelationshipsByProjectId(projectId);
    }

    public List<Map<String, Object>> getBusinessAreaRelationTypes() throws SQLException {
        return projectImpactDAO.getBusinessAreaRelationTypes();
    }

    public Map<String, Object> getBusinessAreaOwnerByBusinessAreaId(int businessAreaId) throws SQLException {
        String ownerName = projectImpactDAO.getBusinessAreaOwnersString(businessAreaId);
        String ownerEmail = projectImpactDAO.getBusinessAreaOwnersEmail(businessAreaId);
        return Map.of("ownerName", ownerName != null ? ownerName : "", 
                     "ownerEmail", ownerEmail != null ? ownerEmail : "");
    }

    public boolean saveBusinessAreaRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return projectImpactDAO.saveBusinessAreaRelationships(projectId, relationships, userId);
    }

    // Dataset methods
    public List<Map<String, Object>> getDatasetRelationshipsByProjectId(int projectId) throws SQLException {
        return projectImpactDAO.getDatasetRelationshipsByProjectId(projectId);
    }

    public List<Map<String, Object>> getDatasetRelationTypes() throws SQLException {
        return projectImpactDAO.getDatasetRelationTypes();
    }

    public Map<String, Object> getDatasetOwnerByDatasetId(int datasetId) throws SQLException {
        String ownerName = projectImpactDAO.getDatasetOwnersString(datasetId);
        String ownerEmail = projectImpactDAO.getDatasetOwnersEmail(datasetId);
        return Map.of("ownerName", ownerName != null ? ownerName : "", 
                     "ownerEmail", ownerEmail != null ? ownerEmail : "");
    }

    public boolean saveDatasetRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        // Validate cross-segment relationships
        if (relationships != null && !relationships.isEmpty()) {
            SegmentValidationService validationService = new SegmentValidationService();
            for (Map<String, Object> rel : relationships) {
                Object datasetIdObj = rel.get("datasetId");
                if (datasetIdObj != null) {
                    int datasetId = ((Number) datasetIdObj).intValue();
                    var validationResult = validationService.validateCrossSegmentRelationship(
                        projectId, "Project", datasetId, "Dataset");
                    if (!validationResult.isValid) {
                        throw new SQLException(validationResult.message);
                    }
                }
            }
        }
        return projectImpactDAO.saveDatasetRelationships(projectId, relationships, userId);
    }

    // Attribute methods
    public List<Map<String, Object>> getAttributeRelationshipsByProjectId(int projectId) throws SQLException {
        return projectImpactDAO.getAttributeRelationshipsByProjectId(projectId);
    }

    public List<Map<String, Object>> getAttributeRelationTypes() throws SQLException {
        return projectImpactDAO.getAttributeRelationTypes();
    }

    public Map<String, Object> getAttributeOwnerByAttributeId(int attributeId) throws SQLException {
        String ownerName = projectImpactDAO.getAttributeOwnersString(attributeId);
        String ownerEmail = projectImpactDAO.getAttributeOwnersEmail(attributeId);
        return Map.of("ownerName", ownerName != null ? ownerName : "", 
                     "ownerEmail", ownerEmail != null ? ownerEmail : "");
    }

    public boolean saveAttributeRelationships(int projectId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return projectImpactDAO.saveAttributeRelationships(projectId, relationships, userId);
    }

    /**
     * Get all datasets list
     */
    public List<Map<String, Object>> getDatasetsList() throws SQLException {
        return projectImpactDAO.getDatasetsList();
    }

    /**
     * Get all attributes list
     */
    public List<Map<String, Object>> getAttributesList() throws SQLException {
        return projectImpactDAO.getAttributesList();
    }
    
    // ===== REVERSE LOOKUP METHODS =====
    
    public List<Map<String, Object>> getProjectRelationshipsBySystemId(int systemId) throws SQLException {
        return projectImpactDAO.getProjectRelationshipsBySystemId(systemId);
    }
    
    public List<Map<String, Object>> getProjectRelationshipsByProcessId(int processId) throws SQLException {
        return projectImpactDAO.getProjectRelationshipsByProcessId(processId);
    }
    
    public List<Map<String, Object>> getProjectRelationshipsByGlossaryId(int glossaryId) throws SQLException {
        return projectImpactDAO.getProjectRelationshipsByGlossaryId(glossaryId);
    }
    
    public List<Map<String, Object>> getProjectRelationshipsByPolicyId(int policyId) throws SQLException {
        return projectImpactDAO.getProjectRelationshipsByPolicyId(policyId);
    }
    
    public List<Map<String, Object>> getProjectRelationshipsByProductId(int productId) throws SQLException {
        return projectImpactDAO.getProjectRelationshipsByProductId(productId);
    }
    
    public List<Map<String, Object>> getProjectRelationshipsByClientId(int clientId) throws SQLException {
        return projectImpactDAO.getProjectRelationshipsByClientId(clientId);
    }
    
    public List<Map<String, Object>> getProjectRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        return projectImpactDAO.getProjectRelationshipsByCapabilityId(capabilityId);
    }
    
    public List<Map<String, Object>> getProjectRelationshipsByBusinessAreaId(int businessAreaId) throws SQLException {
        return projectImpactDAO.getProjectRelationshipsByBusinessAreaId(businessAreaId);
    }
    
    public List<Map<String, Object>> getProjectRelationshipsByDatasetId(int datasetId) throws SQLException {
        return projectImpactDAO.getProjectRelationshipsByDatasetId(datasetId);
    }
    
    public List<Map<String, Object>> getProjectRelationshipsByAttributeId(int attributeId) throws SQLException {
        return projectImpactDAO.getProjectRelationshipsByAttributeId(attributeId);
    }
}

