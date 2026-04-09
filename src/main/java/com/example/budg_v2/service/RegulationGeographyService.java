package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationGeographyDAO;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class RegulationGeographyService {
    private final RegulationGeographyDAO dao;
    
    public RegulationGeographyService() {
        this.dao = new RegulationGeographyDAO();
    }
    
    public List<Map<String, Object>> getGeographiesByRegulationId(int regulationId) throws SQLException {
        return dao.getGeographiesByRegulationId(regulationId);
    }
    
    public List<Map<String, Object>> getGeographiesWithInheritance(int regulationId) throws SQLException {
        return dao.getGeographiesWithInheritance(regulationId);
    }
}