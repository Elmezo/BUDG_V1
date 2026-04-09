package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProcessAutomationDAO;
import com.example.budg_v2.model.ProcessAutomation;

import java.sql.SQLException;
import java.util.List;

public class ProcessAutomationService {

    private final ProcessAutomationDAO processAutomationDAO;

    public ProcessAutomationService() {
        this.processAutomationDAO = new ProcessAutomationDAO();
    }

    public List<ProcessAutomation> getAllProcessAutomations() throws SQLException {
        return processAutomationDAO.getAllProcessAutomations();
    }

    public ProcessAutomation getProcessAutomationById(int id) throws SQLException {
        return processAutomationDAO.getProcessAutomationById(id);
    }

    public List<ProcessAutomation> searchProcessAutomations(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllProcessAutomations();
        }
        return processAutomationDAO.searchProcessAutomations(searchQuery.trim());
    }

    public List<ProcessAutomation> getAllProcessAutomationsForDropdown() throws SQLException {
        return processAutomationDAO.getAllProcessAutomationsForDropdown();
    }

    public ProcessAutomation createProcessAutomation(ProcessAutomation automation) throws SQLException, IllegalArgumentException {
        if (automation.getPrimaryName() == null || automation.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (automation.getDescription() == null) automation.setDescription("");
        return processAutomationDAO.createProcessAutomation(automation);
    }

    public ProcessAutomation createProcessAutomation(String primaryName, String description, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        ProcessAutomation automation = new ProcessAutomation();
        automation.setPrimaryName(primaryName);
        automation.setDescription(description != null ? description : "");
        automation.setLastUpdateUserId(lastUpdateUserId);
        return createProcessAutomation(automation);
    }

    public boolean updateProcessAutomation(ProcessAutomation automation) throws SQLException, IllegalArgumentException {
        if (automation.getId() == null) throw new IllegalArgumentException("Process automation ID is required for update");
        if (automation.getPrimaryName() == null || automation.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (automation.getDescription() == null) automation.setDescription("");
        return processAutomationDAO.updateProcessAutomation(automation);
    }

    public boolean updateProcessAutomation(int id, String primaryName, String description, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        ProcessAutomation automation = new ProcessAutomation();
        automation.setId(id);
        automation.setPrimaryName(primaryName);
        automation.setDescription(description != null ? description : "");
        automation.setLastUpdateUserId(lastUpdateUserId);
        return updateProcessAutomation(automation);
    }

    public boolean deleteProcessAutomation(int id) throws SQLException {
        return processAutomationDAO.deleteProcessAutomation(id);
    }
}
