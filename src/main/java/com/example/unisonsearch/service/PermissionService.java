package com.example.unisonsearch.service;

import com.example.budg_v2.service.SegmentAccessService;

/**
 * Service for checking permissions on objects and relationships.
 */
public class PermissionService {
    
    /**
     * Check if user can view an object.
     */
    public boolean canViewObject(String facet, int objectId, Object user) {
        if (!(user instanceof Integer userId) || userId <= 0) {
            return true;
        }

        String objectType = mapFacetToObjectType(facet);
        if (objectType == null) {
            return true;
        }

        try {
            return SegmentAccessService
                .filterBySelectedSegments(userId, objectType, java.util.List.of(objectId))
                .contains(objectId);
        } catch (Exception e) {
            System.err.println("[PermissionService] Failed to check object visibility for facet "
                + facet + " and object " + objectId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if user can view a relationship.
     */
    public boolean canViewRelationship(String sourceFacet, int sourceId,
                                      String targetFacet, int targetId, 
                                      Object user) {
        // Must have permission to view both objects
        return canViewObject(sourceFacet, sourceId, user) &&
               canViewObject(targetFacet, targetId, user);
    }
    
    /**
     * Check if object is public.
     */
    public boolean isPublicObject(String facet, int objectId) {
        // TODO: Implement public object check
        return false;
    }

    private String mapFacetToObjectType(String facet) {
        if (facet == null) {
            return null;
        }

        return switch (facet.trim().toLowerCase()) {
            case "dataset" -> "Dataset";
            case "attribute" -> "Dataset";
            case "system" -> "System";
            case "glossary" -> "Glossary";
            case "process" -> "Process";
            case "project" -> "Project";
            case "product" -> "Product";
            case "policy" -> "Policy";
            case "legal", "legalentity", "legal_entity" -> "LegalEntity";
            case "businessarea", "business_area" -> "BusinessArea";
            case "capability" -> "Capability";
            case "client" -> "Client";
            case "committee" -> "Committee";
            case "orgunit", "org_unit" -> "OrgUnit";
            case "geography" -> "Geography";
            case "regulation" -> "Regulation";
            case "regulator" -> "Regulator";
            case "regulatorytheme", "regulatory_theme" -> "RegulatoryTheme";
            case "interface", "systeminterface", "system_interface" -> "SystemInterface";
            case "dataquality", "data_quality" -> "Dataset";
            case "changerequest", "change_request" -> "ChangeRequest";
            default -> null;
        };
    }
}

