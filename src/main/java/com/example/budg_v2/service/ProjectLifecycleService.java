package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProjectLifecycleDAO;
import com.example.budg_v2.model.ProjectLifecycle;

import java.sql.SQLException;
import java.util.List;

public class ProjectLifecycleService {
    private ProjectLifecycleDAO projectLifecycleDAO;

    public ProjectLifecycleService() {
        this.projectLifecycleDAO = new ProjectLifecycleDAO();
    }

    public List<ProjectLifecycle> getAllProjectLifecycles() throws SQLException {
        return projectLifecycleDAO.getAllProjectLifecycles();
    }
}
