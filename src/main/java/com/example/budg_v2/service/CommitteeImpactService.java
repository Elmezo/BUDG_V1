package com.example.budg_v2.service;

import com.example.budg_v2.dao.CommitteeImpactDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class CommitteeImpactService {
    private final CommitteeImpactDAO committeeImpactDAO;
    
    public CommitteeImpactService() {
        this.committeeImpactDAO = new CommitteeImpactDAO();
    }
    
    public List<Map<String, Object>> getCapabilityRelationshipsByCommitteeId(int committeeId) throws SQLException {
        return committeeImpactDAO.getCapabilityRelationshipsByCommitteeId(committeeId);
    }
    
    public List<Map<String, Object>> getCommitteeRelationshipsByCapabilityId(int capabilityId) throws SQLException {
        return committeeImpactDAO.getCommitteeRelationshipsByCapabilityId(capabilityId);
    }
    
    public List<Map<String, Object>> getCapabilityRelationTypes() throws SQLException {
        return committeeImpactDAO.getCapabilityRelationTypes();
    }
    
    public String getCapabilityOwnersString(int capabilityId) throws SQLException {
        return committeeImpactDAO.getCapabilityOwnersString(capabilityId);
    }
    
    public String getCapabilityOwnersEmail(int capabilityId) throws SQLException {
        return committeeImpactDAO.getCapabilityOwnersEmail(capabilityId);
    }
    
    public boolean saveCapabilityRelationships(int committeeId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return committeeImpactDAO.saveCapabilityRelationships(committeeId, relationships, userId);
    }
}

