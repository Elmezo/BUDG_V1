package com.example.budg_v2.service;

import com.example.budg_v2.dao.OrgUnitDAO;
import com.example.budg_v2.model.OrgUnit;
import com.example.budg_v2.database.DatabaseConnection;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrgUnitService {

    private final OrgUnitDAO orgUnitDAO;

    public OrgUnitService() {
        this.orgUnitDAO = new OrgUnitDAO();
    }

    public List<OrgUnit> getAllOrgUnits() throws SQLException {
        return orgUnitDAO.getAllOrgUnits();
    }

    public List<OrgUnit> getAllOrgUnits(int userId) throws SQLException {
        return orgUnitDAO.getAllOrgUnits(userId);
    }

    public OrgUnit getOrgUnitById(int id) throws SQLException {
        return orgUnitDAO.getOrgUnitById(id);
    }

    public List<OrgUnit> searchOrgUnits(String searchQuery) throws SQLException {
        return orgUnitDAO.searchOrgUnits(searchQuery.trim());
    }

    public OrgUnit createOrgUnitFromNames(String reference, String name, String description,
                                          String parentName, String statusName, int userId) throws SQLException {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Name is required");

        Integer parentId = resolveParentId(parentName);
        Integer statusId = resolveStatusId(statusName);

        OrgUnit orgUnit = new OrgUnit(reference, name, description, parentId, statusId);
        int generatedId = orgUnitDAO.createOrgUnit(orgUnit, userId);
        orgUnit.setId(generatedId);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        orgUnit.setCreatedDate(now);
        orgUnit.setLastUpdatedDate(now);
        return orgUnit;
    }

    public OrgUnit updateOrgUnitFromNames(int id, String reference, String name, String description,
                                          String parentName, String statusName, int userId) throws SQLException {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Name is required");

        OrgUnit existingUnit = orgUnitDAO.getOrgUnitById(id);
        if (existingUnit == null) throw new IllegalArgumentException("Organization unit not found");

        Integer parentId = resolveParentId(parentName);
        Integer statusId = resolveStatusId(statusName);

        OrgUnit orgUnit = new OrgUnit(reference, name, description, parentId, statusId);
        boolean updated = orgUnitDAO.updateOrgUnit(id, orgUnit, userId);
        if (!updated) throw new SQLException("Failed to update organization unit");

        orgUnit.setId(id);
        orgUnit.setLastUpdatedDate(new Timestamp(System.currentTimeMillis()));
        return orgUnit;
    }

    // New: Accept status_id directly (preferred). If null, fallback to statusName.
    public OrgUnit createOrgUnit(String reference, String name, String description,
                                 String parentName, Integer statusId, String statusName, int userId) throws SQLException {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Name is required");

        Integer parentId = resolveParentId(parentName);
        Integer resolvedStatusId = (statusId != null) ? statusId : resolveStatusId(statusName);

        OrgUnit orgUnit = new OrgUnit(reference, name, description, parentId, resolvedStatusId);
        int generatedId = orgUnitDAO.createOrgUnit(orgUnit, userId);
        orgUnit.setId(generatedId);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        orgUnit.setCreatedDate(now);
        orgUnit.setLastUpdatedDate(now);
        return orgUnit;
    }

    public OrgUnit updateOrgUnit(int id, String reference, String name, String description,
                                 String parentName, Integer statusId, String statusName, int userId) throws SQLException {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Name is required");

        OrgUnit existingUnit = orgUnitDAO.getOrgUnitById(id);
        if (existingUnit == null) throw new IllegalArgumentException("Organization unit not found");

        Integer parentId = resolveParentId(parentName);
        Integer resolvedStatusId = (statusId != null) ? statusId : resolveStatusId(statusName);

        OrgUnit orgUnit = new OrgUnit(reference, name, description, parentId, resolvedStatusId);
        boolean updated = orgUnitDAO.updateOrgUnit(id, orgUnit, userId);
        if (!updated) throw new SQLException("Failed to update organization unit");

        orgUnit.setId(id);
        orgUnit.setLastUpdatedDate(new Timestamp(System.currentTimeMillis()));
        return orgUnit;
    }

    public boolean deleteOrgUnit(int id, HttpServletRequest request) throws SQLException {
        OrgUnit existingUnit = orgUnitDAO.getOrgUnitById(id);
        if (existingUnit == null) throw new IllegalArgumentException("Organization unit not found");
        String userName = getCurrentUserName(request);
        return orgUnitDAO.deleteOrgUnitWithAudit(id, userName);
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

    public List<StatusInfo> getAllStatuses() throws SQLException {
        List<StatusInfo> statuses = new ArrayList<>();
        String sql = "SELECT ID, primaryname, description, priority FROM status WHERE deletedate IS NULL ORDER BY priority , ID ";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                StatusInfo status = new StatusInfo();
                status.id = rs.getInt("ID");
                status.primaryName = rs.getString("primaryname");
                status.description = rs.getString("description");
                status.priority = rs.getInt("priority");
                statuses.add(status);
            }
        }
        return statuses;
    }


    public StatusInfo getStatusByName(String name) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT ID, primaryname, description, priority FROM status WHERE primaryname = ? AND deletedate IS NULL")) {

            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                StatusInfo status = new StatusInfo();
                status.id = rs.getInt("ID");
                status.primaryName = rs.getString("primaryname");
                status.description = rs.getString("description");
                status.priority = rs.getInt("priority");
                return status;
            }
        }
        return null;
    }

    public Integer getStatusIdByName(String name) throws SQLException {
        StatusInfo status = getStatusByName(name);
        return status != null ? status.id : null;
    }


    private Integer resolveParentId(String parentName) throws SQLException {
        if (parentName == null || parentName.trim().isEmpty()) return null;
        OrgUnit parentUnit = orgUnitDAO.getOrgUnitByName(parentName.trim());
        if (parentUnit == null) throw new IllegalArgumentException("Parent organization unit not found: " + parentName);
        return parentUnit.getId();
    }

    private Integer resolveStatusId(String statusName) throws SQLException {
        Integer statusId = 1;
        if (statusName != null && !statusName.trim().isEmpty()) {
            Integer foundStatusId = getStatusIdByName(statusName.trim());
            if (foundStatusId != null) statusId = foundStatusId;
            else throw new IllegalArgumentException("Status not found: " + statusName);
        }
        return statusId;
    }

    public static class StatusInfo {
        public int id;
        public String primaryName;
        public String description;
        public int priority;

        @Override
        public String toString() {
            return primaryName != null ? primaryName : "Unknown";
        }
    }
}
