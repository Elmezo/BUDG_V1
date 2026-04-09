package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProjectDataDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ProjectDataService {
    private final ProjectDataDAO projectDataDAO;
    
    public ProjectDataService() {
        this.projectDataDAO = new ProjectDataDAO();
    }
    
    /**
     * Get datasets for a project
     */
    public List<Map<String, Object>> getProjectDatasets(int projectId) throws SQLException {
        return projectDataDAO.getProjectDatasets(projectId);
    }
    
    /**
     * Get attributes for a project
     * Includes attributes from project_X_attribute and attributes from datasets in project_X_dataset
     */
    public List<Map<String, Object>> getProjectAttributes(int projectId) throws SQLException {
        return projectDataDAO.getProjectAttributes(projectId);
    }
}

