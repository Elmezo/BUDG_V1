package com.example.budg_v2.service;

import com.example.budg_v2.dao.LegalImpactDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class LegalImpactService {
    private LegalImpactDAO dao;
    
    public LegalImpactService() {
        this.dao = new LegalImpactDAO();
    }
    
    // Geography relationships
    public List<Map<String, Object>> getGeographyRelationshipsByLegalId(int legalId) throws SQLException {
        return dao.getGeographyRelationshipsByLegalId(legalId);
    }
    
    public List<Map<String, Object>> getGeographyRelationTypes() throws SQLException {
        return dao.getGeographyRelationTypes();
    }
    
    public boolean saveGeographyRelationships(int legalId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveGeographyRelationships(legalId, relationships, userId);
    }
    
    // Reverse lookup: Get legal entity relationships for a geography
    public List<Map<String, Object>> getLegalEntityRelationshipsByGeographyId(int geographyId) throws SQLException {
        return dao.getLegalEntityRelationshipsByGeographyId(geographyId);
    }
}
