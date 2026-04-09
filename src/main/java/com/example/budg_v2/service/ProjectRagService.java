package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProjectRagDAO;
import com.example.budg_v2.model.ProjectRag;

import java.sql.SQLException;
import java.util.List;

public class ProjectRagService {
    private ProjectRagDAO projectRagDAO;

    public ProjectRagService() {
        this.projectRagDAO = new ProjectRagDAO();
    }

    public List<ProjectRag> getAllProjectRags() throws SQLException {
        return projectRagDAO.getAllProjectRags();
    }
}
