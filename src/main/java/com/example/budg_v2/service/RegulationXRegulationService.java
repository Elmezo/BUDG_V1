package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationXRegulationDAO;
import com.example.budg_v2.model.RegulationXRegulation;

import java.util.List;
import java.util.Map;

public class RegulationXRegulationService {
    
    private RegulationXRegulationDAO dao = new RegulationXRegulationDAO();

    public List<Map<String, Object>> getRelationsBySourceRegulationId(int sourceRegulationId) {
        return dao.getRelationsBySourceRegulationId(sourceRegulationId);
    }

    public List<Map<String, Object>> getRelationsByTargetRegulationId(int targetRegulationId) {
        return dao.getRelationsByTargetRegulationId(targetRegulationId);
    }

    public int createRegulationXRegulation(RegulationXRegulation relation) {
        return dao.createRegulationXRegulation(relation);
    }

    public boolean updateRegulationXRegulation(RegulationXRegulation relation) {
        return dao.updateRegulationXRegulation(relation);
    }

    public boolean deleteRegulationXRegulation(int id) {
        return dao.deleteRegulationXRegulation(id);
    }

    public RegulationXRegulation getById(int id) {
        return dao.getById(id);
    }
}
