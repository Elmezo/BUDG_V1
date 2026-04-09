package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProcessLifecycleStatusDAO;
import com.example.budg_v2.model.ProcessLifecycleStatus;

import java.sql.SQLException;
import java.util.List;

public class ProcessLifecycleStatusService {

    private final ProcessLifecycleStatusDAO processLifecycleStatusDAO;

    public ProcessLifecycleStatusService() {
        this.processLifecycleStatusDAO = new ProcessLifecycleStatusDAO();
    }

    public List<ProcessLifecycleStatus> getAllProcessLifecycleStatuses() throws SQLException {
        return processLifecycleStatusDAO.getAllProcessLifecycleStatuses();
    }

    public ProcessLifecycleStatus getProcessLifecycleStatusById(int id) throws SQLException {
        return processLifecycleStatusDAO.getProcessLifecycleStatusById(id);
    }

    public List<ProcessLifecycleStatus> searchProcessLifecycleStatuses(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllProcessLifecycleStatuses();
        }
        return processLifecycleStatusDAO.searchProcessLifecycleStatuses(searchQuery.trim());
    }

    public List<ProcessLifecycleStatus> getAllProcessLifecycleStatusesForDropdown() throws SQLException {
        return processLifecycleStatusDAO.getAllProcessLifecycleStatusesForDropdown();
    }

    public ProcessLifecycleStatus createProcessLifecycleStatus(ProcessLifecycleStatus status) throws SQLException, IllegalArgumentException {
        if (status.getPrimaryName() == null || status.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (status.getDescription() == null) status.setDescription("");
        return processLifecycleStatusDAO.createProcessLifecycleStatus(status);
    }

    public ProcessLifecycleStatus createProcessLifecycleStatus(String primaryName, String description, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        ProcessLifecycleStatus status = new ProcessLifecycleStatus();
        status.setPrimaryName(primaryName);
        status.setDescription(description != null ? description : "");
        status.setLastUpdateUserId(lastUpdateUserId);
        return createProcessLifecycleStatus(status);
    }

    public boolean updateProcessLifecycleStatus(ProcessLifecycleStatus status) throws SQLException, IllegalArgumentException {
        if (status.getId() == null) throw new IllegalArgumentException("Process lifecycle status ID is required for update");
        if (status.getPrimaryName() == null || status.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (status.getDescription() == null) status.setDescription("");
        return processLifecycleStatusDAO.updateProcessLifecycleStatus(status);
    }

    public boolean updateProcessLifecycleStatus(int id, String primaryName, String description, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        ProcessLifecycleStatus status = new ProcessLifecycleStatus();
        status.setId(id);
        status.setPrimaryName(primaryName);
        status.setDescription(description != null ? description : "");
        status.setLastUpdateUserId(lastUpdateUserId);
        return updateProcessLifecycleStatus(status);
    }

    public boolean deleteProcessLifecycleStatus(int id) throws SQLException {
        return processLifecycleStatusDAO.deleteProcessLifecycleStatus(id);
    }
}
