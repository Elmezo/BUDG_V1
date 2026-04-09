package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationXRegulationRelationTypeDAO;
import com.example.budg_v2.model.RegulationXRegulationRelationType;

import java.util.List;

public class RegulationXRegulationRelationTypeService {
    
    private RegulationXRegulationRelationTypeDAO dao = new RegulationXRegulationRelationTypeDAO();

    public List<RegulationXRegulationRelationType> getAllRelationTypes() {
        return dao.getAllRelationTypes();
    }

    public RegulationXRegulationRelationType getById(int id) {
        return dao.getById(id);
    }
}
