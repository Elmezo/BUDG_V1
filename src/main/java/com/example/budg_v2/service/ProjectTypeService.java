package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProjectTypeDAO;
import com.example.budg_v2.model.ProjectType;

import java.sql.SQLException;
import java.util.List;

public class ProjectTypeService {
    private ProjectTypeDAO projectTypeDAO;

    public ProjectTypeService() {
        this.projectTypeDAO = new ProjectTypeDAO();
    }

    public List<ProjectType> getAllProjectTypes() throws SQLException {
        return projectTypeDAO.getAllProjectTypes();
    }
}
