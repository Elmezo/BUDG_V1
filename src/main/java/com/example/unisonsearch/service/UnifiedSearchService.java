package com.example.unisonsearch.service;

import com.example.unisonsearch.model.*;
import java.sql.SQLException;
import java.util.*;

/**
 * Unified Search Service - Main orchestrator for correct relationship search.
 * Replaces the old incorrect logic with new correct relationship handling.
 */
public class UnifiedSearchService {
    
    private final RelationshipService relationshipService;
    private final DedupManager dedupManager;
    @SuppressWarnings("unused")
    private final RelationshipManager relationshipManager;
    private final ActiveTasksService activeTasksService;
    
    public UnifiedSearchService(RelationshipService relationshipService, 
                               DedupManager dedupManager,
                               RelationshipManager relationshipManager,
                               ActiveTasksService activeTasksService) {
        this.relationshipService = relationshipService;
        this.dedupManager = dedupManager;
        this.relationshipManager = relationshipManager;
        this.activeTasksService = activeTasksService;
    }
    
    /**
     * Execute unified search with correct relationships.
     */
    public SearchResponse executeSearch(SearchRequest request) throws SQLException {
        // Reset dedup manager
        dedupManager.reset();
        
        // Validate request
        if (request == null || request.getFacet() == null || request.getObjectId() <= 0) {
            return SearchResponse.error("Invalid search request");
        }
        
        String facet = normalizeFacetName(request.getFacet());
        int objectId = request.getObjectId();
        int maxDepth = request.getMaxDepth();
        
        SearchResponse response = new SearchResponse();
        
        // Collect direct results (allowed targets only)
        List<SearchResult> directResults = collectDirectResults(facet, objectId, maxDepth);
        for (SearchResult result : directResults) {
            if (dedupManager.addResult(facet, result)) {
                response.addResult(result);
            }
        }
        
        // Collect derived results (special relationships)
        List<SearchResult> derivedResults = collectDerivedResults(facet, objectId, maxDepth);
        for (SearchResult result : derivedResults) {
            if (dedupManager.addResult(facet, result)) {
                response.addResult(result);
            }
        }
        
        // Add active tasks
        addActiveTasks(response, facet, objectId);
        
        // Normalize response
        normalizeResponse(response);
        
        // Add stats
        response.addStat("totalResults", response.getTotalResults());
        response.addStat("directResults", directResults.size());
        response.addStat("derivedResults", derivedResults.size());
        response.addStat("activeTasks", response.getActiveTasks().size());
        
        return response;
    }
    
    /**
     * Collect direct results (allowed targets only).
     */
    private List<SearchResult> collectDirectResults(String facet, int objectId, int maxDepth) {
        List<SearchResult> results = new ArrayList<>();
        
        Set<String> allowedTargets = RelationshipManager.getAllowedTargets(facet);
        if (allowedTargets.isEmpty()) {
            return results;
        }
        
        String normalizedFacet = normalizeFacetName(facet);
        
        try {
            switch (normalizedFacet.toLowerCase()) {
                case "dataset":
                    collectDatasetDirectResults(objectId, allowedTargets, results);
                    break;
                case "system":
                    collectSystemDirectResults(objectId, allowedTargets, results);
                    break;
                case "glossary":
                    collectGlossaryDirectResults(objectId, allowedTargets, results);
                    break;
                case "attribute":
                    collectAttributeDirectResults(objectId, allowedTargets, results);
                    break;
                case "interface":
                    collectInterfaceDirectResults(objectId, allowedTargets, results);
                    break;
                case "project":
                case "process":
                case "policy":
                case "capability":
                    collectGovernanceDirectResults(normalizedFacet, objectId, allowedTargets, results);
                    break;
                case "people":
                case "person":
                case "changerequest":
                case "change_request":
                case "orgunit":
                case "org_unit":
                case "geography":
                case "regulation":
                case "regulator":
                case "regulatory_theme":
                case "regulatorytheme":
                case "business_area":
                case "businessarea":
                case "product":
                case "committee":
                case "legal_entity":
                case "legalentity":
                case "client":
                    // These facets are supported by UnisonSearchService
                    // UnifiedSearchService will delegate to UnisonSearchService for these facets
                    // For now, return empty results - they will be handled by UnisonSearchService
                    break;
                default:
                    // Unknown facet - return empty results
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error collecting direct results for " + facet + " " + objectId + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return results;
    }
    
    /**
     * Collect Dataset direct results.
     */
    private void collectDatasetDirectResults(int datasetId, Set<String> allowedTargets, List<SearchResult> results) {
        // System (direct)
        if (allowedTargets.contains("system")) {
            Integer systemId = relationshipService.getSystemOfDataset(datasetId);
            if (systemId != null) {
                results.add(createResult("SYSTEM", systemId, "System", "dataset"));
            }
        }
        
        // Attribute (direct)
        if (allowedTargets.contains("attribute")) {
            List<Integer> attributeIds = relationshipService.getAttributesByDataset(datasetId);
            for (Integer attributeId : attributeIds) {
                results.add(createResult("ATTRIBUTE", attributeId, "Attribute", "dataset"));
            }
        }
        
        // Glossary (direct)
        if (allowedTargets.contains("glossary")) {
            List<Integer> glossaryIds = relationshipService.getGlossariesByDatasetIncludingAttributes(datasetId);
            for (Integer glossaryId : glossaryIds) {
                results.add(createResult("GLOSSARY", glossaryId, "Glossary", "dataset"));
            }
        }

        // Legal entity / client (direct via dataset_x_legal, client_x_dataset)
        if (allowedTargets.contains("legal_entity") || allowedTargets.contains("legalentity")
                || allowedTargets.contains("legal")) {
            List<Integer> legalIds = relationshipService.getLegalEntitiesByDataset(datasetId);
            for (Integer legalId : legalIds) {
                results.add(createResult("LEGAL_ENTITY", legalId, "Legal Entity", "dataset"));
            }
        }
        if (allowedTargets.contains("client")) {
            List<Integer> clientIds = relationshipService.getClientsByDataset(datasetId);
            for (Integer clientId : clientIds) {
                results.add(createResult("CLIENT", clientId, "Client", "dataset"));
            }
        }

        if (allowedTargets.contains("interface")) {
            List<Integer> interfaceIds = relationshipService.getInterfacesByDataset(datasetId);
            for (Integer interfaceId : interfaceIds) {
                results.add(createResult("INTERFACE", interfaceId, "Interface", "dataset"));
            }
        }

        Integer systemForGovernance = null;
        if (allowedTargets.contains("project") || allowedTargets.contains("process") || allowedTargets.contains("policy")) {
            systemForGovernance = relationshipService.getSystemOfDataset(datasetId);
        }
        if (allowedTargets.contains("project")) {
            LinkedHashSet<Integer> projectIds = new LinkedHashSet<>(relationshipService.getProjectsByDataset(datasetId));
            if (systemForGovernance != null) {
                projectIds.addAll(relationshipService.getProjectsBySystem(systemForGovernance));
            }
            for (Integer projectId : projectIds) {
                results.add(createResult("PROJECT", projectId, "Project", "dataset"));
            }
        }
        if (allowedTargets.contains("process")) {
            LinkedHashSet<Integer> processIds = new LinkedHashSet<>(relationshipService.getProcessesByDataset(datasetId));
            if (systemForGovernance != null) {
                processIds.addAll(relationshipService.getProcessesBySystem(systemForGovernance));
            }
            for (Integer processId : processIds) {
                results.add(createResult("PROCESS", processId, "Process", "dataset"));
            }
        }
        if (allowedTargets.contains("policy")) {
            LinkedHashSet<Integer> policyIds = new LinkedHashSet<>(relationshipService.getPoliciesByDataset(datasetId));
            if (systemForGovernance != null) {
                policyIds.addAll(relationshipService.getPoliciesBySystem(systemForGovernance));
            }
            for (Integer policyId : policyIds) {
                results.add(createResult("POLICY", policyId, "Policy", "dataset"));
            }
        }
        
        // Impact (direct) - TODO: implement if needed
    }
    
    /**
     * Collect System direct results.
     */
    private void collectSystemDirectResults(int systemId, Set<String> allowedTargets, List<SearchResult> results) {
        // Dataset (direct)
        if (allowedTargets.contains("dataset")) {
            List<Integer> datasetIds = relationshipService.getDatasetsBySystem(systemId);
            for (Integer datasetId : datasetIds) {
                results.add(createResult("DATASET", datasetId, "Dataset", "system"));
            }
        }
        
        // Glossary (direct)
        if (allowedTargets.contains("glossary")) {
            List<Integer> glossaryIds = relationshipService.getGlossariesBySystem(systemId);
            for (Integer glossaryId : glossaryIds) {
                results.add(createResult("GLOSSARY", glossaryId, "Glossary", "system"));
            }
        }

        // Attributes on datasets where MasterSource = this system
        if (allowedTargets.contains("attribute")) {
            List<Integer> attributeIds = relationshipService.getAllAttributesBySystem(systemId);
            for (Integer attributeId : attributeIds) {
                results.add(createResult("ATTRIBUTE", attributeId, "Attribute", "system"));
            }
        }
    }
    
    /**
     * Collect Glossary direct results.
     */
    private void collectGlossaryDirectResults(int glossaryId, Set<String> allowedTargets, List<SearchResult> results) {
        // Dataset (direct)
        if (allowedTargets.contains("dataset")) {
            List<Integer> datasetIds = relationshipService.getDatasetsByGlossary(glossaryId);
            for (Integer datasetId : datasetIds) {
                results.add(createResult("DATASET", datasetId, "Dataset", "glossary"));
            }
        }
        
        // Attribute (direct)
        if (allowedTargets.contains("attribute")) {
            List<Integer> attributeIds = relationshipService.getAttributesByGlossary(glossaryId);
            for (Integer attributeId : attributeIds) {
                results.add(createResult("ATTRIBUTE", attributeId, "Attribute", "glossary"));
            }
        }

        if (allowedTargets.contains("project")) {
            List<Integer> projectIds = relationshipService.getProjectsByGlossary(glossaryId);
            for (Integer projectId : projectIds) {
                results.add(createResult("PROJECT", projectId, "Project", "glossary"));
            }
        }

        if (allowedTargets.contains("process")) {
            List<Integer> processIds = relationshipService.getProcessesByGlossary(glossaryId);
            for (Integer processId : processIds) {
                results.add(createResult("PROCESS", processId, "Process", "glossary"));
            }
        }

        if (allowedTargets.contains("policy")) {
            List<Integer> policyIds = relationshipService.getPoliciesByGlossary(glossaryId);
            for (Integer policyId : policyIds) {
                results.add(createResult("POLICY", policyId, "Policy", "glossary"));
            }
        }

        if (allowedTargets.contains("capability")) {
            List<Integer> capabilityIds = relationshipService.getCapabilitiesByGlossary(glossaryId);
            for (Integer capabilityId : capabilityIds) {
                results.add(createResult("CAPABILITY", capabilityId, "Capability", "glossary"));
            }
        }

        if (allowedTargets.contains("business_area")) {
            List<Integer> businessAreaIds = relationshipService.getBusinessAreasByGlossary(glossaryId);
            for (Integer businessAreaId : businessAreaIds) {
                results.add(createResult("BUSINESS_AREA", businessAreaId, "Business Area", "glossary"));
            }
        }

        if (allowedTargets.contains("legal_entity")) {
            List<Integer> legalEntityIds = relationshipService.getLegalEntitiesByGlossary(glossaryId);
            for (Integer legalEntityId : legalEntityIds) {
                results.add(createResult("LEGAL_ENTITY", legalEntityId, "Legal Entity", "glossary"));
            }
        }

        if (allowedTargets.contains("client")) {
            List<Integer> clientIds = relationshipService.getClientsByGlossary(glossaryId);
            for (Integer clientId : clientIds) {
                results.add(createResult("CLIENT", clientId, "Client", "glossary"));
            }
        }

        if (allowedTargets.contains("product")) {
            List<Integer> productIds = relationshipService.getProductsByGlossary(glossaryId);
            for (Integer productId : productIds) {
                results.add(createResult("PRODUCT", productId, "Product", "glossary"));
            }
        }
    }
    
    /**
     * Collect Attribute direct results.
     */
    private void collectAttributeDirectResults(int attributeId, Set<String> allowedTargets, List<SearchResult> results) {
        // Dataset (direct)
        if (allowedTargets.contains("dataset")) {
            Integer datasetId = relationshipService.getDatasetOfAttribute(attributeId);
            if (datasetId != null) {
                results.add(createResult("DATASET", datasetId, "Dataset", "attribute"));
            }
        }
        
        // System (direct)
        if (allowedTargets.contains("system")) {
            Integer systemId = relationshipService.getSystemOfAttribute(attributeId);
            if (systemId != null) {
                results.add(createResult("SYSTEM", systemId, "System", "attribute"));
            }
        }
        
        // Glossary (direct)
        if (allowedTargets.contains("glossary")) {
            Integer glossaryId = relationshipService.getGlossaryOfAttribute(attributeId);
            if (glossaryId != null) {
                results.add(createResult("GLOSSARY", glossaryId, "Glossary", "attribute"));
            }
        }
    }
    
    /**
     * Collect Interface direct results.
     */
    private void collectInterfaceDirectResults(int interfaceId, Set<String> allowedTargets, List<SearchResult> results) {
        // System (direct)
        if (allowedTargets.contains("system")) {
            List<Integer> systemIds = relationshipService.getSystemsByInterface(interfaceId);
            for (Integer systemId : systemIds) {
                results.add(createResult("SYSTEM", systemId, "System", "interface"));
            }
        }
    }
    
    /**
     * Collect Governance (Project/Process/Policy/Capability) direct results.
     */
    private void collectGovernanceDirectResults(String facet, int objectId, Set<String> allowedTargets, List<SearchResult> results) {
        // Glossary (direct)
        if (allowedTargets.contains("glossary")) {
            List<Integer> glossaryIds = getGlossariesByGovernance(facet, objectId);
            for (Integer glossaryId : glossaryIds) {
                results.add(createResult("GLOSSARY", glossaryId, "Glossary", facet));
            }
        }
        
        // ImpactObject (direct) - TODO: implement if needed
    }
    
    /**
     * Get Glossaries by Governance object.
     */
    private List<Integer> getGlossariesByGovernance(String facet, int objectId) {
        return switch (facet.toLowerCase()) {
            case "project" -> relationshipService.getGlossariesByProject(objectId);
            case "process" -> relationshipService.getGlossariesByProcess(objectId);
            case "policy" -> relationshipService.getGlossariesByPolicy(objectId);
            case "capability" -> relationshipService.getGlossariesByCapability(objectId);
            default -> new ArrayList<>();
        };
    }
    
    /**
     * Collect derived results (special relationships).
     */
    private List<SearchResult> collectDerivedResults(String facet, int objectId, int maxDepth) {
        List<SearchResult> results = new ArrayList<>();
        
        Set<String> specialRelationships = RelationshipManager.getSpecialRelationships(facet);
        if (specialRelationships.isEmpty()) {
            return results;
        }
        
        String normalizedFacet = normalizeFacetName(facet);
        
        try {
            switch (normalizedFacet.toLowerCase()) {
                case "glossary":
                    collectGlossaryDerivedResults(objectId, specialRelationships, maxDepth, results);
                    break;
                case "attribute":
                    collectAttributeDerivedResults(objectId, specialRelationships, results);
                    break;
                case "project":
                case "process":
                case "policy":
                case "capability":
                    collectGovernanceDerivedResults(normalizedFacet, objectId, specialRelationships, results);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error collecting derived results for " + facet + " " + objectId + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return results;
    }
    
    /**
     * Collect Glossary derived results (special relationships).
     */
    private void collectGlossaryDerivedResults(int glossaryId, Set<String> specialRelationships, int maxDepth, List<SearchResult> results) {
        // System (special, derived chain depth = 3)
        if (specialRelationships.contains("system") && maxDepth >= 3) {
            List<Integer> systemIds = relationshipService.getSystemByGlossary(glossaryId);
            for (Integer systemId : systemIds) {
                results.add(createResult("SYSTEM", systemId, "System", "glossary"));
            }
        }
    }
    
    /**
     * Collect Attribute derived results (special relationships).
     */
    private void collectAttributeDerivedResults(int attributeId, Set<String> specialRelationships, List<SearchResult> results) {
        // System (special, parent chain)
        if (specialRelationships.contains("system")) {
            Integer systemId = relationshipService.getSystemOfAttribute(attributeId);
            if (systemId != null) {
                results.add(createResult("SYSTEM", systemId, "System", "attribute"));
            }
        }
    }
    
    /**
     * Collect Governance derived results (special relationships).
     */
    private void collectGovernanceDerivedResults(String facet, int objectId, Set<String> specialRelationships, List<SearchResult> results) {
        // Dataset (special)
        if (specialRelationships.contains("dataset")) {
            List<Integer> datasetIds = getDatasetsByGovernance(facet, objectId);
            for (Integer datasetId : datasetIds) {
                results.add(createResult("DATASET", datasetId, "Dataset", facet));
            }
        }
        
        // Attribute (special)
        if (specialRelationships.contains("attribute")) {
            List<Integer> attributeIds = getAttributesByGovernance(facet, objectId);
            for (Integer attributeId : attributeIds) {
                results.add(createResult("ATTRIBUTE", attributeId, "Attribute", facet));
            }
        }
        
        // System (special)
        if (specialRelationships.contains("system")) {
            List<Integer> systemIds = getSystemsByGovernance(facet, objectId);
            for (Integer systemId : systemIds) {
                results.add(createResult("SYSTEM", systemId, "System", facet));
            }
        }
    }
    
    /**
     * Get Datasets by Governance object.
     */
    private List<Integer> getDatasetsByGovernance(String facet, int objectId) {
        return switch (facet.toLowerCase()) {
            case "project" -> relationshipService.getDatasetsByProject(objectId);
            case "process" -> relationshipService.getDatasetsByProcess(objectId);
            case "policy" -> relationshipService.getDatasetsByPolicy(objectId);
            case "capability" -> relationshipService.getDatasetsByCapability(objectId);
            default -> new ArrayList<>();
        };
    }
    
    /**
     * Get Attributes by Governance object.
     */
    private List<Integer> getAttributesByGovernance(String facet, int objectId) {
        return switch (facet.toLowerCase()) {
            case "project" -> relationshipService.getAttributesByProject(objectId);
            case "process" -> relationshipService.getAttributesByProcess(objectId);
            case "policy" -> relationshipService.getAttributesByPolicy(objectId);
            case "capability" -> relationshipService.getAttributesByCapability(objectId);
            default -> new ArrayList<>();
        };
    }
    
    /**
     * Get Systems by Governance object.
     */
    private List<Integer> getSystemsByGovernance(String facet, int objectId) {
        return switch (facet.toLowerCase()) {
            case "project" -> relationshipService.getSystemsByProject(objectId);
            case "process" -> relationshipService.getSystemsByProcess(objectId);
            case "policy" -> relationshipService.getSystemsByPolicy(objectId);
            case "capability" -> relationshipService.getSystemsByCapability(objectId);
            default -> new ArrayList<>();
        };
    }
    
    /**
     * Add active tasks to response.
     */
    private void addActiveTasks(SearchResponse response, String facet, int objectId) {
        try {
            String objectType = mapFacetToObjectType(facet);
            List<ActiveTask> tasks = activeTasksService.getActiveTasksForObject(objectType, objectId);
            for (ActiveTask task : tasks) {
                response.addActiveTask(task);
            }
        } catch (Exception e) {
            System.err.println("Error adding active tasks for " + facet + " " + objectId + ": " + e.getMessage());
            // Continue without tasks
        }
    }
    
    /**
     * Map facet name to object type.
     */
    private String mapFacetToObjectType(String facet) {
        String normalized = normalizeFacetName(facet);
        return switch (normalized.toLowerCase()) {
            case "dataset" -> "Dataset";
            case "attribute" -> "Attribute";
            case "system" -> "System";
            case "glossary" -> "Glossary";
            case "interface" -> "Interface";
            case "process" -> "Process";
            case "project" -> "Project";
            case "policy" -> "Policy";
            default -> normalized;
        };
    }
    
    /**
     * Normalize response.
     */
    private void normalizeResponse(SearchResponse response) {
        // Remove duplicates, sort, etc.
        // For now, dedup is already handled by DedupManager
    }
    
    /**
     * Create a SearchResult.
     */
    private SearchResult createResult(String entityFacet, int id, String entityType, String sourceFacet) {
        SearchResult result = new SearchResult();
        result.setEntityFacet(entityFacet);
        result.setId(id);
        result.setEntityType(entityType);
        result.setSourceFacet(sourceFacet);
        result.setDepth(1);
        return result;
    }
    
    /**
     * Normalize facet name.
     * Uses FacetNormalizationUtil for consistency across the codebase.
     */
    private String normalizeFacetName(String facet) {
        if (facet == null) return null;
        // Use FacetNormalizationUtil to normalize to lowercase canonical form
        return com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToLowercase(facet);
    }
}

