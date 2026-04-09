package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProcessClassDAO;
import com.example.budg_v2.model.ProcessClass;

import java.sql.SQLException;
import java.util.List;

public class ProcessClassService {

    private final ProcessClassDAO processClassDAO;

    public ProcessClassService() {
        this.processClassDAO = new ProcessClassDAO();
    }

    public List<ProcessClass> getAllProcessClasses() throws SQLException {
        return processClassDAO.getAllProcessClasses();
    }

    public ProcessClass getProcessClassById(int id) throws SQLException {
        return processClassDAO.getProcessClassById(id);
    }

    public List<ProcessClass> searchProcessClasses(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllProcessClasses();
        }
        return processClassDAO.searchProcessClasses(searchQuery.trim());
    }

    public List<ProcessClass> getAllProcessClassesForDropdown() throws SQLException {
        return processClassDAO.getAllProcessClassesForDropdown();
    }

    public ProcessClass createProcessClass(ProcessClass processClass) throws SQLException, IllegalArgumentException {
        if (processClass.getPrimaryName() == null || processClass.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (processClass.getDescription() == null) processClass.setDescription("");
        return processClassDAO.createProcessClass(processClass);
    }

    public ProcessClass createProcessClass(String primaryName, String description, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        ProcessClass processClass = new ProcessClass();
        processClass.setPrimaryName(primaryName);
        processClass.setDescription(description != null ? description : "");
        processClass.setLastUpdateUserId(lastUpdateUserId);
        return createProcessClass(processClass);
    }

    public boolean updateProcessClass(ProcessClass processClass) throws SQLException, IllegalArgumentException {
        if (processClass.getId() == null) throw new IllegalArgumentException("Process class ID is required for update");
        if (processClass.getPrimaryName() == null || processClass.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (processClass.getDescription() == null) processClass.setDescription("");
        return processClassDAO.updateProcessClass(processClass);
    }

    public boolean updateProcessClass(int id, String primaryName, String description, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        ProcessClass processClass = new ProcessClass();
        processClass.setId(id);
        processClass.setPrimaryName(primaryName);
        processClass.setDescription(description != null ? description : "");
        processClass.setLastUpdateUserId(lastUpdateUserId);
        return updateProcessClass(processClass);
    }

    public boolean deleteProcessClass(int id) throws SQLException {
        return processClassDAO.deleteProcessClass(id);
    }
}
