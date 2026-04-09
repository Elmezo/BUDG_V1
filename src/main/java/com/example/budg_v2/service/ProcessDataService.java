package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProcessDataDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ProcessDataService {
    private final ProcessDataDAO processDataDAO;
    
    public ProcessDataService() {
        this.processDataDAO = new ProcessDataDAO();
    }
    
    /**
     * Get datasets for a process
     */
    public List<Map<String, Object>> getProcessDatasets(int processId) throws SQLException {
        return processDataDAO.getProcessDatasets(processId);
    }
    
    /**
     * Get attributes for a process
     * Includes attributes from process_X_attribute and attributes from datasets in process_X_dataset
     */
    public List<Map<String, Object>> getProcessAttributes(int processId) throws SQLException {
        return processDataDAO.getProcessAttributes(processId);
    }
}

