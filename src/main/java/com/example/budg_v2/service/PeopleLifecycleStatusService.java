package com.example.budg_v2.service;

import com.example.budg_v2.dao.PeopleLifecycleStatusDAO;
import com.example.budg_v2.model.PeopleLifecycleStatus;

import java.sql.SQLException;
import java.util.List;

public class PeopleLifecycleStatusService {

    private final PeopleLifecycleStatusDAO peopleLifecycleStatusDAO;

    public PeopleLifecycleStatusService() {
        this.peopleLifecycleStatusDAO = new PeopleLifecycleStatusDAO();
    }

    public List<PeopleLifecycleStatus> getAllPeopleLifecycleStatuses() throws SQLException {
        return peopleLifecycleStatusDAO.getAllPeopleLifecycleStatuses();
    }

    public PeopleLifecycleStatus getPeopleLifecycleStatusById(int id) throws SQLException {
        return peopleLifecycleStatusDAO.getPeopleLifecycleStatusById(id);
    }

    public List<PeopleLifecycleStatus> searchPeopleLifecycleStatuses(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllPeopleLifecycleStatuses();
        }
        return peopleLifecycleStatusDAO.searchPeopleLifecycleStatuses(searchQuery.trim());
    }

    public List<PeopleLifecycleStatus> getAllPeopleLifecycleStatusesForDropdown() throws SQLException {
        return peopleLifecycleStatusDAO.getAllPeopleLifecycleStatusesForDropdown();
    }

    public PeopleLifecycleStatus createPeopleLifecycleStatus(PeopleLifecycleStatus status) throws SQLException, IllegalArgumentException {
        if (status.getPrimaryName() == null || status.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        return peopleLifecycleStatusDAO.createPeopleLifecycleStatus(
            status.getPrimaryName(), 
            status.getDescription() != null ? status.getDescription() : "", 
            status.getLastUpdateUserId()
        );
    }

    public PeopleLifecycleStatus createPeopleLifecycleStatus(String primaryName, String description, int userId) throws SQLException, IllegalArgumentException {
        if (primaryName == null || primaryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        return peopleLifecycleStatusDAO.createPeopleLifecycleStatus(primaryName, description, userId);
    }

    public boolean updatePeopleLifecycleStatus(int id, String primaryName, String description, int userId) throws SQLException, IllegalArgumentException {
        if (primaryName == null || primaryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        return peopleLifecycleStatusDAO.updatePeopleLifecycleStatus(id, primaryName, description, userId);
    }

    public PeopleLifecycleStatus getPeopleLifecycleStatusByPrimaryName(String primaryName) throws SQLException {
        return peopleLifecycleStatusDAO.getPeopleLifecycleStatusByPrimaryName(primaryName);
    }
}
