package com.example.budg_v2.service;

import com.example.budg_v2.dao.InterfaceImpactDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class InterfaceImpactService {
    
    private final InterfaceImpactDAO dao;
    
    public InterfaceImpactService() {
        this.dao = new InterfaceImpactDAO();
    }
    
    // ===== PROCESS RELATIONSHIPS =====
    
    public List<Map<String, Object>> getProcessRelationshipsByInterfaceId(int interfaceId) throws SQLException {
        return dao.getProcessRelationshipsByInterfaceId(interfaceId);
    }
    
    public List<Map<String, Object>> getProcessRelationTypes() throws SQLException {
        return dao.getProcessRelationTypes();
    }
    
    public String getProcessOwnersString(int processId) throws SQLException {
        return dao.getProcessOwnersString(processId);
    }
    
    public String getProcessOwnersEmail(int processId) throws SQLException {
        return dao.getProcessOwnersEmail(processId);
    }
    
    public boolean saveProcessRelationships(int interfaceId, List<Map<String, Object>> relationships, int userId) throws SQLException {
        return dao.saveProcessRelationships(interfaceId, relationships, userId);
    }
}

