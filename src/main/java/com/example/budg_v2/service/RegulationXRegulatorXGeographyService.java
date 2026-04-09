package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationXRegulatorXGeographyDAO;
import com.example.budg_v2.model.RegulationXRegulatorXGeography;

import java.util.List;
import java.util.Map;

public class RegulationXRegulatorXGeographyService {
    
    private RegulationXRegulatorXGeographyDAO dao = new RegulationXRegulatorXGeographyDAO();

    public List<Map<String, Object>> getGeographiesByRegulationId(int regulationId) {
        return dao.getGeographiesByRegulationId(regulationId);
    }

    public int createRegulationXRegulatorXGeography(RegulationXRegulatorXGeography geography) {
        return dao.createRegulationXRegulatorXGeography(geography);
    }

    public boolean updateRegulationXRegulatorXGeography(RegulationXRegulatorXGeography geography) {
        return dao.updateRegulationXRegulatorXGeography(geography);
    }

    public boolean deleteRegulationXRegulatorXGeography(int id) {
        return dao.deleteRegulationXRegulatorXGeography(id);
    }

    public RegulationXRegulatorXGeography getById(int id) {
        return dao.getById(id);
    }
}
