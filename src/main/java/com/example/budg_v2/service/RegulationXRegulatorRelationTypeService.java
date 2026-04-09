package com.example.budg_v2.service;

import com.example.budg_v2.dao.RegulationXRegulatorRelationTypeDAO;
import com.example.budg_v2.model.RegulationXRegulatorRelationType;

import java.util.List;

public class RegulationXRegulatorRelationTypeService {
    
    private RegulationXRegulatorRelationTypeDAO dao = new RegulationXRegulatorRelationTypeDAO();

    public List<RegulationXRegulatorRelationType> getAllRelationTypes() {
        return dao.getAllRelationTypes();
    }

    public RegulationXRegulatorRelationType getById(int id) {
        return dao.getById(id);
    }
}
