package com.example.budg_v2.service;

import com.example.budg_v2.dao.DatasetDAO;
import com.example.budg_v2.model.Dataset;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;

public class DatasetService {

    private final DatasetDAO datasetDAO = new DatasetDAO();

    public int createDataset(Dataset dataset, int userId) throws SQLException {
        validate(dataset);
        return datasetDAO.insert(dataset, userId);
    }

    public boolean updateDataset(Dataset dataset, int userId) throws SQLException {
        if (dataset.getId() == null) throw new IllegalArgumentException("Dataset id is required");
        validate(dataset);
        return datasetDAO.update(dataset, userId);
    }

    public boolean deleteDataset(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return datasetDAO.deleteDatasetWithAudit(id, userName);
    }

    private String getCurrentUserName(HttpServletRequest request) {
        try {
            String userJson = (String) request.getAttribute("user");
            if (userJson != null && userJson.contains("\"username\":")) {
                int start = userJson.indexOf("\"username\":\"") + 12;
                int end = userJson.indexOf("\"", start);
                if (end > start) {
                    return userJson.substring(start, end);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting username: " + e.getMessage());
        }
        return "Unknown User";
    }

    private void validate(Dataset d) {
        if (d.getPrimaryName() == null || d.getPrimaryName().trim().isEmpty())
            throw new IllegalArgumentException("Primary Name is required.");
        if (d.getMasterSource() == null)
            throw new IllegalArgumentException("Master Source is required.");
        if (d.getDefinition() == null || d.getDefinition().trim().isEmpty())
            throw new IllegalArgumentException("Definition is required.");
        if (d.getGlossary() == null)
            throw new IllegalArgumentException("Glossary is required.");
        if (d.getStatus() == null)
            throw new IllegalArgumentException("Status is required.");
        if (d.getDatasetType() == null)
            throw new IllegalArgumentException("Dataset Type is required.");
        if (d.getAccessControlType() == null)
            throw new IllegalArgumentException("Access Control Type is required.");
        if (d.getLifecycle() == null)
            throw new IllegalArgumentException("Lifecycle is required.");
    }
}


