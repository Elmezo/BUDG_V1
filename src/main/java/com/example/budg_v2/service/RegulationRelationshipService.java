package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationRelationshipDAO;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class RegulationRelationshipService {
    private final RegulationRelationshipDAO dao;
    
    public RegulationRelationshipService() {
        this.dao = new RegulationRelationshipDAO();
    }
    
    public List<Map<String, Object>> getRelationshipsByRegulationId(int regulationId) throws SQLException {
        return dao.getRelationshipsByRegulationId(regulationId);
    }
}
