package com.example.budg_v2.service;

import com.example.budg_v2.dao.ViewingDAO;
import com.example.budg_v2.model.Viewing;

import java.sql.SQLException;
import java.util.List;

public class ViewingService {

    private final ViewingDAO viewingDAO;

    public ViewingService() {
        this.viewingDAO = new ViewingDAO();
    }

    public List<Viewing> getAllViewings() throws SQLException {
        return viewingDAO.getAllViewings();
    }

    public List<Viewing> getAllViewingsForDropdown() throws SQLException {
        return viewingDAO.getAllViewingsForDropdown();
    }

    public Viewing getViewingById(int id) throws SQLException {
        return viewingDAO.getViewingById(id);
    }

    public List<Viewing> searchViewings(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllViewings();
        }
        return viewingDAO.searchViewings(searchQuery.trim());
    }

    public Viewing createViewing(String name, String description, Integer lastUpdateUserId) throws SQLException, IllegalArgumentException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (description == null) description = "";

        Viewing viewing = new Viewing(name.trim(), description);
        viewing.setLastUpdateUserId(lastUpdateUserId);
        
        return viewingDAO.createViewing(viewing);
    }

    public boolean updateViewing(int id, String name, String description, Integer lastUpdateUserId) throws SQLException, IllegalArgumentException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (description == null) description = "";

        Viewing viewing = new Viewing(name.trim(), description);
        viewing.setId(id);
        viewing.setLastUpdateUserId(lastUpdateUserId);
        
        return viewingDAO.updateViewing(viewing);
    }

    public boolean deleteViewing(int id, Integer lastUpdateUserId) throws SQLException {
        return viewingDAO.deleteViewing(id, lastUpdateUserId);
    }
}
