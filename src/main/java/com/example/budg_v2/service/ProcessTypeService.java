package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProcessTypeDAO;
import com.example.budg_v2.model.ProcessType;

import java.sql.SQLException;
import java.util.List;

public class ProcessTypeService {

    private final ProcessTypeDAO processTypeDAO;

    public ProcessTypeService() {
        this.processTypeDAO = new ProcessTypeDAO();
    }

    public List<ProcessType> getAllProcessTypes() throws SQLException {
        return processTypeDAO.getAllProcessTypes();
    }

    public ProcessType getProcessTypeById(int id) throws SQLException {
        return processTypeDAO.getProcessTypeById(id);
    }

    public List<ProcessType> searchProcessTypes(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllProcessTypes();
        }
        return processTypeDAO.searchProcessTypes(searchQuery.trim());
    }

    public List<ProcessType> getAllProcessTypesForDropdown() throws SQLException {
        return processTypeDAO.getAllProcessTypesForDropdown();
    }

    public ProcessType createProcessType(ProcessType type) throws SQLException, IllegalArgumentException {
        if (type.getPrimaryName() == null || type.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (type.getDescription() == null) type.setDescription("");
        return processTypeDAO.createProcessType(type);
    }

    public ProcessType createProcessType(String primaryName, String description, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        ProcessType type = new ProcessType();
        type.setPrimaryName(primaryName);
        type.setDescription(description != null ? description : "");
        type.setLastUpdateUserId(lastUpdateUserId);
        return createProcessType(type);
    }

    public boolean updateProcessType(ProcessType type) throws SQLException, IllegalArgumentException {
        if (type.getId() == null) throw new IllegalArgumentException("Process type ID is required for update");
        if (type.getPrimaryName() == null || type.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (type.getDescription() == null) type.setDescription("");
        return processTypeDAO.updateProcessType(type);
    }

    public boolean updateProcessType(int id, String primaryName, String description, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        ProcessType type = new ProcessType();
        type.setId(id);
        type.setPrimaryName(primaryName);
        type.setDescription(description != null ? description : "");
        type.setLastUpdateUserId(lastUpdateUserId);
        return updateProcessType(type);
    }

    public boolean deleteProcessType(int id) throws SQLException {
        return processTypeDAO.deleteProcessType(id);
    }
}
