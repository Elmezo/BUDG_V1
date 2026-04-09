package com.example.budg_v2.service;

import com.example.budg_v2.dao.LegalAdviceTypeDAO;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class LegalAdviceTypeService {
    private final LegalAdviceTypeDAO dao;
    
    public LegalAdviceTypeService() {
        this.dao = new LegalAdviceTypeDAO();
    }
    
    public List<Map<String, Object>> getAll() throws SQLException {
        return dao.getAll();
    }
}
