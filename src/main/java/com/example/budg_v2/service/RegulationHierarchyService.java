package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationHierarchyDAO;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class RegulationHierarchyService {
    private final RegulationHierarchyDAO dao;
    
    public RegulationHierarchyService() {
        this.dao = new RegulationHierarchyDAO();
    }
    
    public List<Map<String, Object>> getAllRegulations() throws SQLException {
        return dao.getAllRegulations();
    }
}
