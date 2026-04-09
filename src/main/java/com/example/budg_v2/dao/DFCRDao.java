package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.DFCR;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for Default Change Request settings (DF_CR table)
 */
public class DFCRDao {

    /**
     * Get DF_CR settings by facet ID (module ID)
     */
    public DFCR getByFacetId(int facetId) throws SQLException {
        String sql = "SELECT * FROM DF_CR WHERE facet_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, facetId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToDFCR(rs);
                }
            }
        }
        return null;
    }

    /**
     * Get DF_CR settings by facet name
     */
    public DFCR getByFacetName(String facetName) throws SQLException {
        Integer facetId = getFacetIdByName(facetName);
        if (facetId == null) {
            return null;
        }
        return getByFacetId(facetId);
    }

    /**
     * Get all DF_CR settings
     */
    public List<DFCR> getAll() throws SQLException {
        String sql = "SELECT * FROM DF_CR";
        List<DFCR> list = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                list.add(mapResultSetToDFCR(rs));
            }
        }
        return list;
    }

    /**
     * Insert or update DF_CR settings for a facet
     */
    public void upsert(DFCR dfcr) throws SQLException {
        String sql = "INSERT INTO DF_CR " +
                "(facet_id, status_id, lifecycle_table, cr_type_id, cr_urgency_id, cr_severity_id, " +
                "workflow_create_id, workflow_edit_id, process_definition_id, can_create, can_read, cr_system, " +
                "workflow_approval_enabled, workflow_for_types_enabled, admin_workflow_bypass, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW()) " +
                "ON DUPLICATE KEY UPDATE " +
                "status_id = VALUES(status_id), " +
                "lifecycle_table = VALUES(lifecycle_table), " +
                "cr_type_id = VALUES(cr_type_id), " +
                "cr_urgency_id = VALUES(cr_urgency_id), " +
                "cr_severity_id = VALUES(cr_severity_id), " +
                "workflow_create_id = VALUES(workflow_create_id), " +
                "workflow_edit_id = VALUES(workflow_edit_id), " +
                "process_definition_id = VALUES(process_definition_id), " +
                "can_create = VALUES(can_create), " +
                "can_read = VALUES(can_read), " +
                "cr_system = VALUES(cr_system), " +
                "workflow_approval_enabled = VALUES(workflow_approval_enabled), " +
                "workflow_for_types_enabled = VALUES(workflow_for_types_enabled), " +
                "admin_workflow_bypass = VALUES(admin_workflow_bypass), " +
                "updated_at = NOW()";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, dfcr.getFacetId());
            setNullableInt(stmt, 2, dfcr.getStatusId());
            setNullableString(stmt, 3, dfcr.getLifecycleTable());
            setNullableInt(stmt, 4, dfcr.getCrTypeId());
            setNullableInt(stmt, 5, dfcr.getCrUrgencyId());
            setNullableInt(stmt, 6, dfcr.getCrSeverityId());
            setNullableInt(stmt, 7, dfcr.getWorkflowCreateId());
            setNullableInt(stmt, 8, dfcr.getWorkflowEditId());
            setNullableInt(stmt, 9, dfcr.getProcessDefinitionId());
            setNullableInt(stmt, 10, dfcr.getCanCreate());
            setNullableInt(stmt, 11, dfcr.getCanRead());
            stmt.setString(12, dfcr.getCrSystem() != null ? dfcr.getCrSystem() : "Native");
            stmt.setBoolean(13, dfcr.isWorkflowApprovalEnabled());
            stmt.setBoolean(14, dfcr.isWorkflowForTypesEnabled());
            stmt.setBoolean(15, dfcr.isAdminWorkflowBypass());
            
            stmt.executeUpdate();
        }
    }

    /**
     * Delete DF_CR settings for a facet
     */
    public void deleteByFacetId(int facetId) throws SQLException {
        String sql = "DELETE FROM DF_CR WHERE facet_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, facetId);
            stmt.executeUpdate();
        }
    }

    /**
     * Get facet ID (module ID) by facet name
     */
    public Integer getFacetIdByName(String facetName) throws SQLException {
        // Map facet names to module names as they appear in the database
        String moduleName = mapFacetNameToModuleName(facetName);
        
        String sql = "SELECT id FROM module WHERE primaryname = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, moduleName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }

    /**
     * Get facet name by facet ID (module ID)
     */
    public String getFacetNameById(int facetId) throws SQLException {
        String sql = "SELECT primaryname FROM module WHERE id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, facetId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapModuleNameToFacetName(rs.getString("primaryname"));
                }
            }
        }
        return null;
    }

    /**
     * Determine lifecycle table based on facet
     */
    public String getLifecycleTableForFacet(String facetName) {
        switch (facetName.toLowerCase()) {
            case "glossary":
                return "glossary_lifecycle";
            case "data set":
            case "data sets":
                return "dataset_lifecycle";
            case "process":
                return "process_lifecycle_status";
            case "system":
                return "system_lifecycle";
            default:
                return null;
        }
    }

    // Helper methods

    private String mapFacetNameToModuleName(String facetName) {
        switch (facetName) {
            case "Data Set":
                return "Data Sets";
            default:
                return facetName;
        }
    }

    private String mapModuleNameToFacetName(String moduleName) {
        switch (moduleName) {
            case "Data Sets":
                return "Data Set";
            default:
                return moduleName;
        }
    }

    private DFCR mapResultSetToDFCR(ResultSet rs) throws SQLException {
        DFCR dfcr = new DFCR();
        dfcr.setId(rs.getInt("id"));
        dfcr.setFacetId(rs.getInt("facet_id"));
        dfcr.setStatusId(getNullableInt(rs, "status_id"));
        dfcr.setLifecycleTable(rs.getString("lifecycle_table"));
        dfcr.setCrTypeId(getNullableInt(rs, "cr_type_id"));
        dfcr.setCrUrgencyId(getNullableInt(rs, "cr_urgency_id"));
        dfcr.setCrSeverityId(getNullableInt(rs, "cr_severity_id"));
        dfcr.setWorkflowCreateId(getNullableInt(rs, "workflow_create_id"));
        dfcr.setWorkflowEditId(getNullableInt(rs, "workflow_edit_id"));
        dfcr.setProcessDefinitionId(getNullableInt(rs, "process_definition_id"));
        dfcr.setCanCreate(getNullableInt(rs, "can_create"));
        dfcr.setCanRead(getNullableInt(rs, "can_read"));
        dfcr.setCrSystem(rs.getString("cr_system"));
        dfcr.setWorkflowApprovalEnabled(rs.getBoolean("workflow_approval_enabled"));
        dfcr.setWorkflowForTypesEnabled(rs.getBoolean("workflow_for_types_enabled"));
        dfcr.setAdminWorkflowBypass(rs.getBoolean("admin_workflow_bypass"));
        dfcr.setCreatedAt(rs.getTimestamp("created_at"));
        dfcr.setUpdatedAt(rs.getTimestamp("updated_at"));
        return dfcr;
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.INTEGER);
        } else {
            stmt.setInt(index, value);
        }
    }

    private void setNullableString(PreparedStatement stmt, int index, String value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.VARCHAR);
        } else {
            stmt.setString(index, value);
        }
    }
}

