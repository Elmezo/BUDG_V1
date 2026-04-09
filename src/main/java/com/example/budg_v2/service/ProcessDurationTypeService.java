package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProcessDurationTypeDAO;
import com.example.budg_v2.model.ProcessDurationType;

import java.sql.SQLException;
import java.util.List;

public class ProcessDurationTypeService {

    private final ProcessDurationTypeDAO processDurationTypeDAO;

    public ProcessDurationTypeService() {
        this.processDurationTypeDAO = new ProcessDurationTypeDAO();
    }

    public List<ProcessDurationType> getAllProcessDurationTypes() throws SQLException {
        return processDurationTypeDAO.getAllProcessDurationTypes();
    }

    public ProcessDurationType getProcessDurationTypeById(int id) throws SQLException {
        return processDurationTypeDAO.getProcessDurationTypeById(id);
    }

    public List<ProcessDurationType> searchProcessDurationTypes(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllProcessDurationTypes();
        }
        return processDurationTypeDAO.searchProcessDurationTypes(searchQuery.trim());
    }

    public List<ProcessDurationType> getAllProcessDurationTypesForDropdown() throws SQLException {
        return processDurationTypeDAO.getAllProcessDurationTypesForDropdown();
    }

    public ProcessDurationType createProcessDurationType(ProcessDurationType durationType) throws SQLException, IllegalArgumentException {
        if (durationType.getPrimaryName() == null || durationType.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (durationType.getDescription() == null) durationType.setDescription("");
        if (durationType.getPriority() == null) durationType.setPriority(1);
        return processDurationTypeDAO.createProcessDurationType(durationType);
    }

    public ProcessDurationType createProcessDurationType(String primaryName, String description, Integer priority, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        ProcessDurationType durationType = new ProcessDurationType();
        durationType.setPrimaryName(primaryName);
        durationType.setDescription(description != null ? description : "");
        durationType.setPriority(priority != null ? priority : 1);
        durationType.setLastUpdateUserId(lastUpdateUserId);
        return createProcessDurationType(durationType);
    }

    public boolean updateProcessDurationType(ProcessDurationType durationType) throws SQLException, IllegalArgumentException {
        if (durationType.getId() == null) throw new IllegalArgumentException("Process duration type ID is required for update");
        if (durationType.getPrimaryName() == null || durationType.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (durationType.getDescription() == null) durationType.setDescription("");
        if (durationType.getPriority() == null) durationType.setPriority(1);
        return processDurationTypeDAO.updateProcessDurationType(durationType);
    }

    public boolean updateProcessDurationType(int id, String primaryName, String description, Integer priority, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        ProcessDurationType durationType = new ProcessDurationType();
        durationType.setId(id);
        durationType.setPrimaryName(primaryName);
        durationType.setDescription(description != null ? description : "");
        durationType.setPriority(priority != null ? priority : 1);
        durationType.setLastUpdateUserId(lastUpdateUserId);
        return updateProcessDurationType(durationType);
    }

    public boolean deleteProcessDurationType(int id) throws SQLException {
        return processDurationTypeDAO.deleteProcessDurationType(id);
    }
}
