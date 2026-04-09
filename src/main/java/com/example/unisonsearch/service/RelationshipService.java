package com.example.unisonsearch.service;

import java.util.List;
import java.util.Set;

/**
 * Service interface for retrieving relationships between facets.
 * Provides methods for direct and derived relationships.
 */
public interface RelationshipService {
    
    // ========== Dataset Relations ==========
    
    /**
     * Get the System of a Dataset (direct relationship).
     */
    Integer getSystemOfDataset(int datasetId);
    
    /**
     * Get all Attributes by Dataset (direct relationship).
     */
    List<Integer> getAttributesByDataset(int datasetId);
    
    /**
     * Get Glossaries linked to Dataset (including via Attributes).
     */
    List<Integer> getGlossariesByDatasetIncludingAttributes(int datasetId);
    
    /**
     * Get Interfaces for a Dataset (special relationship via System).
     */
    List<Integer> getInterfacesByDataset(int datasetId);

    /**
     * Legal entities linked to a Dataset via dataset_x_legal (direct).
     */
    List<Integer> getLegalEntitiesByDataset(int datasetId);

    /**
     * Clients linked to a Dataset via client_x_dataset (direct).
     */
    List<Integer> getClientsByDataset(int datasetId);
    
    // ========== System Relations ==========
    
    /**
     * Get all Datasets by System (direct relationship).
     */
    List<Integer> getDatasetsBySystem(int systemId);
    
    /**
     * Get all Attributes by System (special relationship with depth = 2).
     * System → Dataset → Attribute
     */
    List<Integer> getAllAttributesBySystem(int systemId);
    
    /**
     * Get Glossaries by System (direct relationship).
     */
    List<Integer> getGlossariesBySystem(int systemId);
    
    /**
     * Get Interfaces by System (direct relationship).
     */
    List<Integer> getInterfacesBySystem(int systemId);
    
    // ========== Glossary Relations ==========
    
    /**
     * Get Datasets by Glossary (direct relationship).
     */
    List<Integer> getDatasetsByGlossary(int glossaryId);
    
    /**
     * Get Attributes by Glossary (direct relationship).
     */
    List<Integer> getAttributesByGlossary(int glossaryId);
    
    /**
     * Get System by Glossary (special relationship, derived chain depth = 3).
     * Glossary → Attribute → Dataset → System
     */
    List<Integer> getSystemByGlossary(int glossaryId);
    
    // ========== Attribute Relations ==========
    
    /**
     * Get Dataset of an Attribute (direct relationship).
     */
    Integer getDatasetOfAttribute(int attributeId);
    
    /**
     * Get Glossary of an Attribute (direct relationship).
     */
    Integer getGlossaryOfAttribute(int attributeId);
    
    /**
     * Get System of an Attribute (special relationship, parent chain).
     * Attribute → Dataset → System
     */
    Integer getSystemOfAttribute(int attributeId);
    
    // ========== Interface Relations ==========
    
    /**
     * Get System of an Interface (direct relationship).
     */
    List<Integer> getSystemsByInterface(int interfaceId);
    
    // ========== Project/Process/Policy/Capability Relations ==========
    
    /**
     * Get Glossaries by Project (direct relationship).
     */
    List<Integer> getGlossariesByProject(int projectId);
    
    /**
     * Get Datasets by Project (special relationship).
     */
    List<Integer> getDatasetsByProject(int projectId);
    
    /**
     * Get Attributes by Project (special relationship).
     */
    List<Integer> getAttributesByProject(int projectId);
    
    /**
     * Get Systems by Project (special relationship).
     */
    List<Integer> getSystemsByProject(int projectId);
    
    /**
     * Get Glossaries by Process (direct relationship).
     */
    List<Integer> getGlossariesByProcess(int processId);
    
    /**
     * Get Datasets by Process (special relationship).
     */
    List<Integer> getDatasetsByProcess(int processId);
    
    /**
     * Get Attributes by Process (special relationship).
     */
    List<Integer> getAttributesByProcess(int processId);
    
    /**
     * Get Systems by Process (special relationship).
     */
    List<Integer> getSystemsByProcess(int processId);
    
    /**
     * Get Glossaries by Policy (direct relationship).
     */
    List<Integer> getGlossariesByPolicy(int policyId);
    
    /**
     * Get Datasets by Policy (special relationship).
     */
    List<Integer> getDatasetsByPolicy(int policyId);
    
    /**
     * Get Attributes by Policy (special relationship).
     */
    List<Integer> getAttributesByPolicy(int policyId);
    
    /**
     * Get Systems by Policy (special relationship).
     */
    List<Integer> getSystemsByPolicy(int policyId);
    
    // ========== Reverse / Impact (Dataset/Attribute/System/Glossary → Project/Process/Policy) ==========
    
    List<Integer> getProjectsByDataset(int datasetId);
    List<Integer> getProcessesByDataset(int datasetId);
    List<Integer> getPoliciesByDataset(int datasetId);
    List<Integer> getProjectsByAttribute(int attributeId);
    List<Integer> getProcessesByAttribute(int attributeId);
    List<Integer> getPoliciesByAttribute(int attributeId);
    List<Integer> getProjectsBySystem(int systemId);
    List<Integer> getProcessesBySystem(int systemId);
    List<Integer> getPoliciesBySystem(int systemId);
    List<Integer> getProjectsByGlossary(int glossaryId);
    List<Integer> getProcessesByGlossary(int glossaryId);
    List<Integer> getPoliciesByGlossary(int glossaryId);
    List<Integer> getCapabilitiesByGlossary(int glossaryId);
    List<Integer> getBusinessAreasByGlossary(int glossaryId);
    List<Integer> getLegalEntitiesByGlossary(int glossaryId);
    List<Integer> getClientsByGlossary(int glossaryId);
    List<Integer> getProductsByGlossary(int glossaryId);
    
    /**
     * Get Glossaries by Capability (direct relationship).
     */
    List<Integer> getGlossariesByCapability(int capabilityId);
    
    /**
     * Get Datasets by Capability (special relationship).
     */
    List<Integer> getDatasetsByCapability(int capabilityId);
    
    /**
     * Get Attributes by Capability (special relationship).
     */
    List<Integer> getAttributesByCapability(int capabilityId);
    
    /**
     * Get Systems by Capability (special relationship).
     */
    List<Integer> getSystemsByCapability(int capabilityId);
    
    // ========== Product Relations ==========
    
    /**
     * Get Glossaries by Product (direct relationship).
     */
    List<Integer> getGlossariesByProduct(int productId);
    
    /**
     * Get Datasets by Product (direct relationship).
     */
    List<Integer> getDatasetsByProduct(int productId);
    
    /**
     * Get Systems by Product (direct relationship).
     */
    List<Integer> getSystemsByProduct(int productId);
    
    /**
     * Get Attributes by Product (special relationship via Dataset).
     * Product → Dataset → Attribute
     */
    List<Integer> getAttributesByProduct(int productId);
    
    // ========== Interface Relations (Additional) ==========
    
    /**
     * Get Datasets by Interface (special relationship via System).
     * Interface → System → Dataset
     */
    List<Integer> getDatasetsByInterface(int interfaceId);
    
    // ========== Business Area Relations ==========
    
    /**
     * Get Glossaries by Business Area (direct relationship).
     */
    List<Integer> getGlossariesByBusinessArea(int businessAreaId);
    
    /**
     * Get Processes by Business Area (direct relationship).
     */
    List<Integer> getProcessesByBusinessArea(int businessAreaId);
    
    /**
     * Get Systems by Business Area (direct relationship).
     */
    List<Integer> getSystemsByBusinessArea(int businessAreaId);
    
    /**
     * Get Datasets by Business Area (special relationship via System).
     * BusinessArea → System → Dataset
     */
    List<Integer> getDatasetsByBusinessArea(int businessAreaId);
    
    /**
     * Get Attributes by Business Area (special relationship via System/Dataset).
     * BusinessArea → System → Dataset → Attribute
     */
    List<Integer> getAttributesByBusinessArea(int businessAreaId);
    
    // ========== Client Relations ==========
    
    /**
     * Get Glossaries by Client (direct relationship).
     */
    List<Integer> getGlossariesByClient(int clientId);
    
    /**
     * Get Datasets by Client (direct relationship).
     */
    List<Integer> getDatasetsByClient(int clientId);
    
    /**
     * Get Systems by Client (direct relationship).
     */
    List<Integer> getSystemsByClient(int clientId);
    
    /**
     * Get Policies by Client (direct relationship).
     */
    List<Integer> getPoliciesByClient(int clientId);
    
    /**
     * Get Processes by Client (direct relationship).
     */
    List<Integer> getProcessesByClient(int clientId);
    
    /**
     * Get Projects by Client (direct relationship).
     */
    List<Integer> getProjectsByClient(int clientId);
    
    /**
     * Get Attributes by Client (special relationship via Dataset).
     * Client → Dataset → Attribute
     */
    List<Integer> getAttributesByClient(int clientId);
    
    // ========== Committee Relations ==========
    
    /**
     * Get Capabilities by Committee (direct relationship).
     */
    List<Integer> getCapabilitiesByCommittee(int committeeId);
    
    /**
     * Get related Committees (self-referential relationship).
     */
    List<Integer> getCommitteesByCommittee(int committeeId);
    
    /**
     * Get Glossaries by Committee (special relationship via Capability).
     * Committee → Capability → Glossary
     */
    List<Integer> getGlossariesByCommittee(int committeeId);
    
    /**
     * Get Datasets by Committee (special relationship via Capability).
     * Committee → Capability → Glossary → Dataset
     */
    List<Integer> getDatasetsByCommittee(int committeeId);
    
    /**
     * Get Attributes by Committee (special relationship via Capability).
     * Committee → Capability → Glossary → Attribute
     */
    List<Integer> getAttributesByCommittee(int committeeId);
    
    /**
     * Get Systems by Committee (special relationship via Capability).
     * Committee → Capability → Glossary → System
     */
    List<Integer> getSystemsByCommittee(int committeeId);
    
    // ========== Legal Entity Relations ==========
    
    /**
     * Get Geographies by Legal Entity (direct relationship).
     */
    List<Integer> getGeographiesByLegalEntity(int legalId);
    
    /**
     * Get Datasets by Legal Entity (direct relationship).
     */
    List<Integer> getDatasetsByLegalEntity(int legalId);
    
    /**
     * Get Systems by Legal Entity (special relationship via Dataset).
     * Legal → Dataset → System
     */
    List<Integer> getSystemsByLegalEntity(int legalId);
    
    /**
     * Get Attributes by Legal Entity (special relationship via Dataset).
     * Legal → Dataset → Attribute
     */
    List<Integer> getAttributesByLegalEntity(int legalId);
    
    // ========== Regulation Relations ==========
    
    /**
     * Get Regulators by Regulation (direct relationship).
     */
    List<Integer> getRegulatorsByRegulation(int regulationId);
    
    /**
     * Get RegulatoryThemes by Regulation (direct relationship).
     */
    List<Integer> getRegulatoryThemesByRegulation(int regulationId);
    
    /**
     * Get Geographies by Regulation (special relationship via Regulator).
     * Regulation → Regulator → Geography
     */
    List<Integer> getGeographiesByRegulation(int regulationId);
    
    // ========== Regulator Relations ==========
    
    /**
     * Get Geographies by Regulator (direct relationship).
     */
    List<Integer> getGeographiesByRegulator(int regulatorId);
    
    /**
     * Get Regulations by Regulator (reverse relationship).
     */
    List<Integer> getRegulationsByRegulator(int regulatorId);
    
    /**
     * Get RegulatoryThemes by Regulator (special relationship via Regulation).
     * Regulator → Regulation → Theme
     */
    List<Integer> getRegulatoryThemesByRegulator(int regulatorId);
    
    // ========== RegulatoryTheme Relations ==========
    
    /**
     * Get Regulations by RegulatoryTheme (direct relationship).
     */
    List<Integer> getRegulationsByRegulatoryTheme(int themeId);
    
    /**
     * Get Regulators by RegulatoryTheme (special relationship via Regulation).
     * Theme → Regulation → Regulator
     */
    List<Integer> getRegulatorsByRegulatoryTheme(int themeId);
    
    /**
     * Get Geographies by RegulatoryTheme (special relationship via Regulation).
     * Theme → Regulation → Regulator → Geography
     */
    List<Integer> getGeographiesByRegulatoryTheme(int themeId);
    
    // ========== Geography Relations (Regulatory) ==========
    
    /**
     * Get Regulators by Geography (reverse relationship).
     */
    List<Integer> getRegulatorsByGeography(int geographyId);
    
    /**
     * Get Regulations by Geography (special relationship via Regulator).
     * Geography → Regulator → Regulation
     */
    List<Integer> getRegulationsByGeography(int geographyId);
    
    /**
     * Get RegulatoryThemes by Geography (special relationship via Regulator).
     * Geography → Regulator → Regulation → Theme
     */
    List<Integer> getRegulatoryThemesByGeography(int geographyId);
    
    // ========== Person/People Relations ==========
    
    /**
     * Get Change Requests raised by Person (direct relationship).
     * Person → ChangeRequest (via Created_By)
     */
    List<Integer> getChangeRequestsByPerson(int personId);
    
    /**
     * Get Active Tasks assigned to Person (direct relationship).
     * Person → Task (via Assigned_To)
     */
    List<Integer> getActiveTasksByPerson(int personId);
    
    /**
     * Get Org Units by Person (direct relationship).
     * Person → OrgUnit (via person.Org_Unit_ID)
     */
    List<Integer> getOrgUnitsByPerson(int personId);
    
    /**
     * Get Roles by Person (via object_x_people).
     */
    List<Integer> getRolesByPerson(int personId);
    
    /**
     * Get People by Org Unit (direct relationship).
     * OrgUnit → Person (via people.Org_Unit_ID)
     */
    List<Integer> getPeopleByOrgUnit(int orgUnitId);
    
    /**
     * Get Objects by Org Unit (derived relationship via stakeholders).
     * OrgUnit → Any Object that has stakeholders from this org unit
     * 
     * This is a special cross-facet query that returns ALL object IDs
     * across different facets that have stakeholders belonging to the org unit.
     * The graph traversal system will then expand these objects.
     */
    List<Integer> getObjectsByOrgUnit(int orgUnitId);
    
    // ========== Batch Operations ==========
    
    /**
     * Get Systems for multiple Datasets (batch operation).
     */
    List<Integer> getSystemsByDatasetIds(Set<Integer> datasetIds);
    
    /**
     * Get Datasets by IDs (batch operation).
     */
    List<Integer> getDatasetsByIds(Set<Integer> datasetIds);
    
    /**
     * Get Attributes by IDs (batch operation).
     */
    List<Integer> getAttributesByIds(Set<Integer> attributeIds);
    
    // ========== People & Stakeholders ==========
    
    /**
     * Get direct stakeholders for a target object.
     */
    List<Integer> getDirectStakeholders(String objectType, int objectId);
    
    /**
     * Get direct roles for a person.
     */
    List<Integer> getDirectRoles(int personId);
    
    // ========== Change Request ==========
    
    /**
     * Get target object of a Change Request.
     */
    Object getTargetObjectOfChangeRequest(int crId);
    
    /**
     * Get tasks by Change Request.
     */
    List<Integer> getTasksByChangeRequest(int crId);
}

