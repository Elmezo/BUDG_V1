package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationXRegulatoryThemeRelationTypeDAO;
import com.example.budg_v2.model.RegulationXRegulatoryThemeRelationType;

import java.sql.SQLException;
import java.util.List;

public class RegulationXRegulatoryThemeRelationTypeService {

    private final RegulationXRegulatoryThemeRelationTypeDAO relationTypeDAO;

    public RegulationXRegulatoryThemeRelationTypeService() {
        this.relationTypeDAO = new RegulationXRegulatoryThemeRelationTypeDAO();
    }

    public List<RegulationXRegulatoryThemeRelationType> getAllRelationTypes() throws SQLException {
        return relationTypeDAO.getAllRelationTypes();
    }

    public RegulationXRegulatoryThemeRelationType getRelationTypeById(int id) throws SQLException {
        return relationTypeDAO.getRelationTypeById(id);
    }

    public List<RegulationXRegulatoryThemeRelationType> getAllRelationTypesForDropdown() throws SQLException {
        return relationTypeDAO.getAllRelationTypesForDropdown();
    }
}
