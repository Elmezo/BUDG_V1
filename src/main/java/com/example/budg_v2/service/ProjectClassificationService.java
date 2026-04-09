package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProjectClassificationDAO;
import com.example.budg_v2.model.ProjectClassification;

import java.sql.SQLException;
import java.util.List;

public class ProjectClassificationService {
    private ProjectClassificationDAO projectClassificationDAO;

    public ProjectClassificationService() {
        this.projectClassificationDAO = new ProjectClassificationDAO();
    }

    public List<ProjectClassification> getAllProjectClassifications() throws SQLException {
        return projectClassificationDAO.getAllProjectClassifications();
    }
}
