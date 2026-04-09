package com.example.budg_v2.service;

import com.example.budg_v2.dao.StatusDAO;
import com.example.budg_v2.model.Status;

import java.sql.SQLException;
import java.util.List;

public class StatusService {

    private final StatusDAO statusDAO;

    public StatusService() {
        this.statusDAO = new StatusDAO();
    }

    public List<Status> getAllStatuses() throws SQLException {
        return statusDAO.getAllStatuses();
    }

    public Status getStatusById(int id) throws SQLException {
        return statusDAO.getStatusById(id);
    }

    public List<Status> searchStatuses(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllStatuses();
        }
        return statusDAO.searchStatuses(searchQuery.trim());
    }

    public List<Status> getAllStatusesForDropdown() throws SQLException {
        return statusDAO.getAllStatusesForDropdown();
    }

    public Status createStatus(Status status) throws SQLException, IllegalArgumentException {
        if (status.getPrimaryName() == null || status.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (status.getPriority() == null) status.setPriority(1);
        if (status.getDescription() == null) status.setDescription("");
        return statusDAO.createStatus(status);
    }

    public Status createStatus(String primaryName, String description, Integer priority, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        Status status = new Status();
        status.setPrimaryName(primaryName);
        status.setDescription(description != null ? description : "");
        status.setPriority(priority != null ? priority : 1);
        status.setLastUpdateUserId(lastUpdateUserId);
        return createStatus(status);
    }

    public boolean updateStatus(Status status) throws SQLException, IllegalArgumentException {
        if (status.getId() == null) throw new IllegalArgumentException("Status ID is required for update");
        if (status.getPrimaryName() == null || status.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (status.getPriority() == null) status.setPriority(1);
        if (status.getDescription() == null) status.setDescription("");
        return statusDAO.updateStatus(status);
    }

    public boolean updateStatus(int id, String primaryName, String description, Integer priority, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        Status status = new Status();
        status.setId(id);
        status.setPrimaryName(primaryName);
        status.setDescription(description != null ? description : "");
        status.setPriority(priority != null ? priority : 1);
        status.setLastUpdateUserId(lastUpdateUserId);
        return updateStatus(status);
    }

    public boolean deleteStatus(int id) throws SQLException {
        return statusDAO.deleteStatus(id);
    }

}
