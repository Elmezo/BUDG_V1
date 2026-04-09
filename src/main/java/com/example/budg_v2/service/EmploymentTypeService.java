package com.example.budg_v2.service;

import com.example.budg_v2.dao.EmploymentTypeDAO;
import com.example.budg_v2.model.EmploymentType;

import java.sql.SQLException;
import java.util.List;

public class EmploymentTypeService {

    private final EmploymentTypeDAO employmentTypeDAO;

    public EmploymentTypeService() {
        this.employmentTypeDAO = new EmploymentTypeDAO();
    }

    public List<EmploymentType> getAllEmploymentTypes() throws SQLException {
        return employmentTypeDAO.getAllEmploymentTypes();
    }

    public EmploymentType getEmploymentTypeById(int id) throws SQLException {
        return employmentTypeDAO.getEmploymentTypeById(id);
    }

    public List<EmploymentType> searchEmploymentTypes(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllEmploymentTypes();
        }
        return employmentTypeDAO.searchEmploymentTypes(searchQuery.trim());
    }

    public List<EmploymentType> getAllEmploymentTypesForDropdown() throws SQLException {
        return employmentTypeDAO.getAllEmploymentTypesForDropdown();
    }

    public EmploymentType createEmploymentType(EmploymentType employmentType) throws SQLException, IllegalArgumentException {
        if (employmentType.getPrimaryName() == null || employmentType.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        return employmentTypeDAO.createEmploymentType(employmentType.getPrimaryName(), employmentType.getLastUpdateUserId());
    }

    public EmploymentType createEmploymentType(String primaryName, int userId) throws SQLException, IllegalArgumentException {
        if (primaryName == null || primaryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        return employmentTypeDAO.createEmploymentType(primaryName, userId);
    }

    public boolean updateEmploymentType(int id, String primaryName, int userId) throws SQLException, IllegalArgumentException {
        if (primaryName == null || primaryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        return employmentTypeDAO.updateEmploymentType(id, primaryName, userId);
    }

    public EmploymentType getEmploymentTypeByPrimaryName(String primaryName) throws SQLException {
        return employmentTypeDAO.getEmploymentTypeByPrimaryName(primaryName);
    }
}
