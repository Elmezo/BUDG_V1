package com.example.unisonsearch.config;

import com.example.unisonsearch.model.FilterField;
import com.example.unisonsearch.model.FilterType;

import java.util.*;

/**
 * Configuration class that defines available filters for each facet.
 * Maps facet IDs to their respective filter fields.
 */
public class FilterMetadataConfig {
    private static final Map<String, List<FilterField>> FACET_FILTERS = new HashMap<>();
    
    static {
        // Dataset filters
        List<FilterField> datasetFilters = new ArrayList<>();
        datasetFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "dataset_lifecycle", "lifecycle"));
        datasetFilters.add(new FilterField("type", "Type", FilterType.DROPDOWN, "dataset_type", "DatasetType"));
        datasetFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "status"));
        datasetFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreateDatetime"));
        datasetFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "LastUpdateDatetime"));
        datasetFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "Createdby_ID"));
        FACET_FILTERS.put("DATASET", datasetFilters);
        
        // Glossary filters
        List<FilterField> glossaryFilters = new ArrayList<>();
        glossaryFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "glossary_lifecycle", "Lifecycle"));
        glossaryFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "Status"));
        glossaryFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "Created_Datetime"));
        glossaryFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "Last_Updated_Datetime"));
        glossaryFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "CreatedBy_ID"));
        FACET_FILTERS.put("GLOSSARY", glossaryFilters);
        
        // Process filters
        List<FilterField> processFilters = new ArrayList<>();
        processFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "process_lifecycle_status", "lifecycle_status"));
        processFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "status"));
        processFilters.add(new FilterField("type", "Type", FilterType.DROPDOWN, "process_type", "type"));
        processFilters.add(new FilterField("classification", "Process Class", FilterType.DROPDOWN, "process_class", "processclass_id"));
        processFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "createdatetime"));
        processFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "lastupdatedatetime"));
        processFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "createdby_id"));
        FACET_FILTERS.put("PROCESS", processFilters);
        
        // Policy filters
        List<FilterField> policyFilters = new ArrayList<>();
        policyFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "policy_lifecycle_status", "Lifecycle_Status"));
        policyFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "Status"));
        policyFilters.add(new FilterField("type", "Type", FilterType.DROPDOWN, "policy_type", "Policy_Type"));
        policyFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "createDatetime"));
        policyFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "LastUpdateDatetime"));
        policyFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "CreatedBy_ID"));
        FACET_FILTERS.put("POLICY", policyFilters);
        
        // Capability filters
        List<FilterField> capabilityFilters = new ArrayList<>();
        capabilityFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "capability_lifecyle", "Lifecycle"));
        capabilityFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "Status"));
        capabilityFilters.add(new FilterField("type", "Type", FilterType.DROPDOWN, "capability_type", "Capability_Type"));
        capabilityFilters.add(new FilterField("classification", "Classification", FilterType.DROPDOWN, "capability_classification", "Classification"));
        capabilityFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreateDatetime"));
        capabilityFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "LastUpdateDatetime"));
        capabilityFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "LastUpdateUser_ID"));
        FACET_FILTERS.put("CAPABILITY", capabilityFilters);
        
        // System filters
        List<FilterField> systemFilters = new ArrayList<>();
        systemFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "system_lifecycle", "Lifecycle"));
        systemFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "status"));
        systemFilters.add(new FilterField("type", "Type", FilterType.DROPDOWN, "system_type", "Type"));
        systemFilters.add(new FilterField("classification", "Classification", FilterType.DROPDOWN, "system_classification", "Classification"));
        systemFilters.add(new FilterField("external", "External", FilterType.BOOLEAN, null, "External"));
        systemFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "Created_Datetime"));
        systemFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "Last_Updated_Datetime"));
        systemFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "CreatedBy_ID"));
        FACET_FILTERS.put("SYSTEM", systemFilters);
        
        // Attribute filters
        List<FilterField> attributeFilters = new ArrayList<>();
        attributeFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreatedDatetime"));
        attributeFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "Last_UpdateDatetime"));
        attributeFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "CreatedBy"));
        attributeFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "Last_UpdatedUser_ID"));
        attributeFilters.add(new FilterField("origination", "Origination", FilterType.DROPDOWN, "attribute_origination", "Origination"));
        attributeFilters.add(new FilterField("editability", "Editability", FilterType.DROPDOWN, "attribute_editability", "Editability"));
        attributeFilters.add(new FilterField("isMandatory", "Is Mandatory", FilterType.BOOLEAN, null, "Is_Mandatory"));
        FACET_FILTERS.put("ATTRIBUTE", attributeFilters);
        
        // Interface filters
        List<FilterField> interfaceFilters = new ArrayList<>();
        interfaceFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "interface_lifecycle", "Lifecycle_id"));
        interfaceFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "status_id"));
        interfaceFilters.add(new FilterField("classification", "Classification", FilterType.DROPDOWN, "interface_classification", "Classification_id"));
        interfaceFilters.add(new FilterField("transferMethod", "Transfer Method", FilterType.DROPDOWN, "interface_transfer", "Transfer_Method_ID"));
        interfaceFilters.add(new FilterField("transferFormat", "Transfer Format", FilterType.DROPDOWN, "interface_transfer_format", "Transfer_Format_ID"));
        interfaceFilters.add(new FilterField("automation", "Automation", FilterType.DROPDOWN, "interface_automation", "Automation_ID"));
        interfaceFilters.add(new FilterField("frequency", "Frequency", FilterType.DROPDOWN, "interface_frequency", "Frequency_ID"));
        interfaceFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "created_datetime"));
        interfaceFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "last_updatedtime"));
        interfaceFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "createdBy_ID"));
        interfaceFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "last_updateuser_id"));
        FACET_FILTERS.put("INTERFACE", interfaceFilters);
        
        // People filters
        List<FilterField> peopleFilters = new ArrayList<>();
        peopleFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "status_id"));
        peopleFilters.add(new FilterField("profileName", "Profile Name", FilterType.DROPDOWN, "role", "System_Role"));
        peopleFilters.add(new FilterField("systemRole", "System Role", FilterType.DROPDOWN, "role_type", "System_Role"));
        peopleFilters.add(new FilterField("source", "Source", FilterType.DROPDOWN, "people_source", "source_id"));
        peopleFilters.add(new FilterField("orgUnit", "Org Unit", FilterType.DROPDOWN, "org_unit", "Org_Unit_ID"));
        peopleFilters.add(new FilterField("isLocked", "Is Locked", FilterType.BOOLEAN, null, "is_locked"));
        peopleFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "Created_Date"));
        peopleFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "Last_Updated"));
        peopleFilters.add(new FilterField("lastLogin", "Last Login", FilterType.DATE_RANGE, null, "last_User_LogIn"));
        FACET_FILTERS.put("PEOPLE", peopleFilters);
        
        // Role filters
        List<FilterField> roleFilters = new ArrayList<>();
        roleFilters.add(new FilterField("roleType", "Role Type", FilterType.DROPDOWN, "role_type", "roletype"));
        FACET_FILTERS.put("ROLE", roleFilters);
        
        // Business Area filters
        List<FilterField> businessAreaFilters = new ArrayList<>();
        businessAreaFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "business_area_lifecycle", "Lifecycle"));
        businessAreaFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "Status"));
        businessAreaFilters.add(new FilterField("isPublic", "Is Public", FilterType.BOOLEAN, null, "Is_Public"));
        businessAreaFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreateDatetime"));
        businessAreaFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "LastUpdateDatetime"));
        businessAreaFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "createdby_id"));
        businessAreaFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "LastUpdate_UserID"));
        FACET_FILTERS.put("BUSINESS_AREA", businessAreaFilters);
        
        // Legal Entity filters
        List<FilterField> legalEntityFilters = new ArrayList<>();
        legalEntityFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "Status"));
        legalEntityFilters.add(new FilterField("isPublic", "Is Public", FilterType.BOOLEAN, null, "Is_Public"));
        legalEntityFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreateDatetime"));
        legalEntityFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "LastUpdateDatetime"));
        legalEntityFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "LastUpdate_UserID"));
        FACET_FILTERS.put("LEGAL_ENTITY", legalEntityFilters);
        
        // Client filters
        List<FilterField> clientFilters = new ArrayList<>();
        clientFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "client_lifecycle", "Lifecycle"));
        clientFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "Status"));
        clientFilters.add(new FilterField("isPublic", "Is Public", FilterType.DROPDOWN, null, "IsPublic"));
        clientFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreateDatetime"));
        clientFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "LastUpdateDatetime"));
        clientFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "LastUpdate_UserID"));
        FACET_FILTERS.put("CLIENT", clientFilters);
        
        // Committee filters
        List<FilterField> committeeFilters = new ArrayList<>();
        committeeFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "committee_lifecycle", "Lifecycle"));
        committeeFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "Status"));
        committeeFilters.add(new FilterField("classification", "Classification", FilterType.DROPDOWN, "committee_classification", "Classification"));
        committeeFilters.add(new FilterField("type", "Type", FilterType.DROPDOWN, "committee_type", "Committee_Type"));
        committeeFilters.add(new FilterField("isPublic", "Is Public", FilterType.BOOLEAN, null, "Is_Public"));
        committeeFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreateDatetime"));
        committeeFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "LastUpdateDatetime"));
        committeeFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "LastUpdate_UserID"));
        FACET_FILTERS.put("COMMITTEE", committeeFilters);
        
        // Product filters
        List<FilterField> productFilters = new ArrayList<>();
        productFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "product_lifecycle", "lifecycle_status"));
        productFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "status"));
        productFilters.add(new FilterField("isPublic", "Is Public", FilterType.BOOLEAN, null, "is_public"));
        productFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "createdatetime"));
        productFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "lastupdatedatetime"));
        productFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "createdby_id"));
        productFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "lastupdate_userid"));
        FACET_FILTERS.put("PRODUCT", productFilters);
        
        // Org Unit filters
        List<FilterField> orgUnitFilters = new ArrayList<>();
        orgUnitFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "status_id"));
        orgUnitFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "Created_Date"));
        orgUnitFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "last_updated_date"));
        orgUnitFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "lastupdateuser_ID"));
        FACET_FILTERS.put("ORG_UNIT", orgUnitFilters);
        
        // Geography filters
        List<FilterField> geographyFilters = new ArrayList<>();
        geographyFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreateDatetime"));
        geographyFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "LastUpdateDatetime"));
        geographyFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "LastUpdate_UserID"));
        FACET_FILTERS.put("GEOGRAPHY", geographyFilters);
        
        // Regulation filters
        List<FilterField> regulationFilters = new ArrayList<>();
        regulationFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "regulation_status", "RegulationStatus_ID"));
        regulationFilters.add(new FilterField("maturity", "Maturity", FilterType.DROPDOWN, "regulation_maturity", "RegulationMaturity_ID"));
        regulationFilters.add(new FilterField("probability", "Probability", FilterType.DROPDOWN, "regulation_probability", "RegulationProbability_ID"));
        regulationFilters.add(new FilterField("impactRating", "Impact Rating", FilterType.DROPDOWN, "regulation_impact_rating", "RegulationImpactRating_ID"));
        regulationFilters.add(new FilterField("stage", "Stage", FilterType.DROPDOWN, "regulation_stage", "RegulationStage_ID"));
        regulationFilters.add(new FilterField("complianceLevel", "Compliance Level", FilterType.DROPDOWN, "regulation_compliance_level", "ComplianceLevel_ID"));
        regulationFilters.add(new FilterField("legalAdviceType", "Legal Advice Type", FilterType.DROPDOWN, "legal_advice_type", "LegalAdviceType_ID"));
        regulationFilters.add(new FilterField("isPublic", "Is Public", FilterType.BOOLEAN, null, "Is_Public"));
        regulationFilters.add(new FilterField("publicationDate", "Publication Date", FilterType.DATE_RANGE, null, "PublicationDate"));
        regulationFilters.add(new FilterField("commentsDate", "Comments Date", FilterType.DATE_RANGE, null, "CommentsDate"));
        regulationFilters.add(new FilterField("finalisationDate", "Finalisation Date", FilterType.DATE_RANGE, null, "FinalisationDate"));
        regulationFilters.add(new FilterField("complianceDate", "Compliance Date", FilterType.DATE_RANGE, null, "ComplianceDate"));
        regulationFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreateDatetime"));
        regulationFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "LastUpdateDatetime"));
        regulationFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "LastUpdate_UserID"));
        FACET_FILTERS.put("REGULATION", regulationFilters);
        
        // Regulator filters
        List<FilterField> regulatorFilters = new ArrayList<>();
        regulatorFilters.add(new FilterField("description", "Description", FilterType.TEXT, null, "Description"));
        regulatorFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreateDatetime"));
        regulatorFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "LastUpdateDatetime"));
        regulatorFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "LastUpdate_UserID"));
        FACET_FILTERS.put("REGULATOR", regulatorFilters);
        
        // Regulatory Theme filters
        List<FilterField> regulatoryThemeFilters = new ArrayList<>();
        regulatoryThemeFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "Status_ID"));
        regulatoryThemeFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreateDatetime"));
        regulatoryThemeFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "LastUpdateDatetime"));
        regulatoryThemeFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "LastUpdate_UserID"));
        FACET_FILTERS.put("REGULATORY_THEME", regulatoryThemeFilters);
        
        // Project filters
        List<FilterField> projectFilters = new ArrayList<>();
        projectFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "project_lifecycle", "lifecycle_status"));
        projectFilters.add(new FilterField("status", "Status", FilterType.DROPDOWN, "status", "status"));
        projectFilters.add(new FilterField("classification", "Classification", FilterType.DROPDOWN, "project_classification", "classification"));
        projectFilters.add(new FilterField("type", "Type", FilterType.DROPDOWN, "project_type", "project_type"));
        projectFilters.add(new FilterField("ragStatus", "RAG Status", FilterType.DROPDOWN, "project_rag", "rag"));
        projectFilters.add(new FilterField("isPublic", "Is Public", FilterType.BOOLEAN, null, "is_public"));
        projectFilters.add(new FilterField("startDate", "Start Date", FilterType.DATE_RANGE, null, "startdate"));
        projectFilters.add(new FilterField("endDate", "End Date", FilterType.DATE_RANGE, null, "enddate"));
        projectFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "createdatetime"));
        projectFilters.add(new FilterField("lastUpdated", "Last Updated", FilterType.DATE_RANGE, null, "lastupdatedatetime"));
        projectFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "createdby_id"));
        projectFilters.add(new FilterField("updatedBy", "Updated By", FilterType.PEOPLE, null, "lastupdateuser_id"));
        FACET_FILTERS.put("PROJECT", projectFilters);
        
        // Change Request filters
        List<FilterField> changeRequestFilters = new ArrayList<>();
        changeRequestFilters.add(new FilterField("type", "Type", FilterType.DROPDOWN, "changerequest_type", "CR_TypeID"));
        changeRequestFilters.add(new FilterField("severity", "Severity", FilterType.DROPDOWN, "changerequest_severity", "CR_SeverityID"));
        changeRequestFilters.add(new FilterField("urgency", "Urgency", FilterType.DROPDOWN, "changerequest_urgency", "CR_UrgencyID"));
        changeRequestFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "Created_At"));
        changeRequestFilters.add(new FilterField("updatedDate", "Updated Date", FilterType.DATE_RANGE, null, "Updated_At"));
        changeRequestFilters.add(new FilterField("createdBy", "Created By", FilterType.PEOPLE, null, "Created_By"));
        changeRequestFilters.add(new FilterField("lastUserChange", "Last Changed By", FilterType.PEOPLE, null, "LastUserChange"));
        FACET_FILTERS.put("CHANGE_REQUESTS", changeRequestFilters);
        FACET_FILTERS.put("CHANGE_REQUEST", changeRequestFilters); // Alias
        
        // Active Tasks filters (workflow_instance_task)
        List<FilterField> activeTasksFilters = new ArrayList<>();
        activeTasksFilters.add(new FilterField("assignedTo", "Assigned To", FilterType.PEOPLE, null, "Assigned_To"));
        activeTasksFilters.add(new FilterField("completedBy", "Completed By", FilterType.PEOPLE, null, "Completed_By"));
        activeTasksFilters.add(new FilterField("assignedDate", "Assigned Date", FilterType.DATE_RANGE, null, "Assigned_At"));
        activeTasksFilters.add(new FilterField("dueDate", "Due Date", FilterType.DATE_RANGE, null, "Due_Date"));
        activeTasksFilters.add(new FilterField("startedDate", "Started Date", FilterType.DATE_RANGE, null, "Started_At"));
        activeTasksFilters.add(new FilterField("completedDate", "Completed Date", FilterType.DATE_RANGE, null, "Completed_At"));
        activeTasksFilters.add(new FilterField("isOverdue", "Is Overdue", FilterType.BOOLEAN, null, "Is_Overdue"));
        FACET_FILTERS.put("ACTIVE_TASKS", activeTasksFilters);
        FACET_FILTERS.put("ACTIVETASKS", activeTasksFilters); // Alias
    }
    
    /**
     * Get filter fields for a specific facet.
     * @param facetId The facet ID (e.g., "DATASET", "GLOSSARY")
     * @return List of filter fields, or empty list if facet not found
     */
    public static List<FilterField> getFiltersForFacet(String facetId) {
        if (facetId == null) {
            return Collections.emptyList();
        }
        String normalizedFacetId = facetId.toUpperCase().trim();
        return FACET_FILTERS.getOrDefault(normalizedFacetId, Collections.emptyList());
    }
    
    /**
     * Check if a facet has filters configured.
     * @param facetId The facet ID
     * @return true if filters are configured, false otherwise
     */
    public static boolean hasFilters(String facetId) {
        if (facetId == null) {
            return false;
        }
        String normalizedFacetId = facetId.toUpperCase().trim();
        return FACET_FILTERS.containsKey(normalizedFacetId) && 
               !FACET_FILTERS.get(normalizedFacetId).isEmpty();
    }
    
    /**
     * Get all configured facet IDs.
     * @return Set of facet IDs
     */
    public static Set<String> getAllFacetIds() {
        return FACET_FILTERS.keySet();
    }
}
