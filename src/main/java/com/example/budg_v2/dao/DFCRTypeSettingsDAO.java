package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.DFCRTypeSetting;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for DFCR Type-level settings (DF_CR_Type_Settings table)
 * Handles per-type overrides for facet types
 */
public class DFCRTypeSettingsDAO {

    /**
     * Ensure the DF_CR_Type_Settings table exists
     */
    public void ensureTableExists() throws SQLException {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS `DF_CR_Type_Settings` (
              `id` INT NOT NULL AUTO_INCREMENT,
              `facet_id` INT NOT NULL,
              `type_id` INT NOT NULL,
              `type_name` VARCHAR(255) NULL,
              `workflow_create_id` INT NULL,
              `workflow_edit_id` INT NULL,
              `cr_type_id` INT NULL,
              `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
              `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE KEY `uq_dfcr_type_facet_type` (`facet_id`, `type_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTableSql);
        }
    }

    /**
     * Get all type settings for a facet
     */
    public List<DFCRTypeSetting> getByFacetId(int facetId) throws SQLException {
        String sql = """
            SELECT ts.*, 
                   pd_create.PrimaryName AS workflow_create_name,
                   pd_edit.PrimaryName AS workflow_edit_name,
                   crt.PrimaryName AS cr_type_name
            FROM DF_CR_Type_Settings ts
            LEFT JOIN process_definition pd_create ON ts.workflow_create_id = pd_create.ID
            LEFT JOIN process_definition pd_edit ON ts.workflow_edit_id = pd_edit.ID
            LEFT JOIN changerequest_type crt ON ts.cr_type_id = crt.ID
            WHERE ts.facet_id = ?
            ORDER BY ts.type_name
            """;

        List<DFCRTypeSetting> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, facetId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Get a specific type setting
     */
    public DFCRTypeSetting getByFacetAndType(int facetId, int typeId) throws SQLException {
        String sql = """
            SELECT ts.*, 
                   pd_create.PrimaryName AS workflow_create_name,
                   pd_edit.PrimaryName AS workflow_edit_name,
                   crt.PrimaryName AS cr_type_name
            FROM DF_CR_Type_Settings ts
            LEFT JOIN process_definition pd_create ON ts.workflow_create_id = pd_create.ID
            LEFT JOIN process_definition pd_edit ON ts.workflow_edit_id = pd_edit.ID
            LEFT JOIN changerequest_type crt ON ts.cr_type_id = crt.ID
            WHERE ts.facet_id = ? AND ts.type_id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, facetId);
            stmt.setInt(2, typeId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * Insert or update type settings
     */
    public void upsert(DFCRTypeSetting setting) throws SQLException {
        ensureTableExists();
        
        String sql = """
            INSERT INTO DF_CR_Type_Settings 
            (facet_id, type_id, type_name, workflow_create_id, workflow_edit_id, cr_type_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
            type_name = VALUES(type_name),
            workflow_create_id = VALUES(workflow_create_id),
            workflow_edit_id = VALUES(workflow_edit_id),
            cr_type_id = VALUES(cr_type_id),
            updated_at = NOW()
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, setting.getFacetId());
            stmt.setInt(2, setting.getTypeId());
            setNullableString(stmt, 3, setting.getTypeName());
            setNullableInt(stmt, 4, setting.getWorkflowCreateId());
            setNullableInt(stmt, 5, setting.getWorkflowEditId());
            setNullableInt(stmt, 6, setting.getCrTypeId());

            stmt.executeUpdate();
        }
    }

    /**
     * Batch upsert multiple type settings
     */
    public void upsertBatch(List<DFCRTypeSetting> settings) throws SQLException {
        ensureTableExists();
        
        String sql = """
            INSERT INTO DF_CR_Type_Settings 
            (facet_id, type_id, type_name, workflow_create_id, workflow_edit_id, cr_type_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
            type_name = VALUES(type_name),
            workflow_create_id = VALUES(workflow_create_id),
            workflow_edit_id = VALUES(workflow_edit_id),
            cr_type_id = VALUES(cr_type_id),
            updated_at = NOW()
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (DFCRTypeSetting setting : settings) {
                stmt.setInt(1, setting.getFacetId());
                stmt.setInt(2, setting.getTypeId());
                setNullableString(stmt, 3, setting.getTypeName());
                setNullableInt(stmt, 4, setting.getWorkflowCreateId());
                setNullableInt(stmt, 5, setting.getWorkflowEditId());
                setNullableInt(stmt, 6, setting.getCrTypeId());
                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }

    /**
     * Delete type setting
     */
    public void delete(int facetId, int typeId) throws SQLException {
        String sql = "DELETE FROM DF_CR_Type_Settings WHERE facet_id = ? AND type_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, facetId);
            stmt.setInt(2, typeId);
            stmt.executeUpdate();
        }
    }

    /**
     * Delete all type settings for a facet (restore defaults)
     */
    public void deleteByFacetId(int facetId) throws SQLException {
        String sql = "DELETE FROM DF_CR_Type_Settings WHERE facet_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, facetId);
            stmt.executeUpdate();
        }
    }

    /**
     * Get all types for a facet from the database
     */
    public List<DFCRTypeSetting> getAllTypesForFacet(int facetId, String facetName) throws SQLException {
        List<DFCRTypeSetting> types = new ArrayList<>();
        String sql;
        
        // Determine which type table to query based on facet
        switch (facetName.toLowerCase()) {
            case "glossary":
                sql = "SELECT ID as type_id, Name as type_name FROM glossary_type ORDER BY Name";
                break;
            case "data set":
            case "data sets":
                sql = "SELECT ID as type_id, PrimaryName as type_name FROM dataset_type ORDER BY PrimaryName";
                break;
            case "system":
                sql = "SELECT id as type_id, Name as type_name FROM system_type ORDER BY Name";
                break;
            default:
                return types; // Return empty list for unsupported facets
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                DFCRTypeSetting setting = new DFCRTypeSetting();
                setting.setFacetId(facetId);
                setting.setTypeId(rs.getInt("type_id"));
                setting.setTypeName(rs.getString("type_name"));
                // All other fields null = inherited
                types.add(setting);
            }
        }
        return types;
    }

    /**
     * Get workflows filtered by Entity_ID (facet) and Status = 'Enabled'
     */
    public List<Object[]> getWorkflowsForFacet(int facetId) throws SQLException {
        String sql = """
            SELECT ID, PrimaryName, Reference 
            FROM process_definition 
            WHERE Entity_ID = ? AND Status = 'Enabled'
            ORDER BY PrimaryName
            """;

        List<Object[]> workflows = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, facetId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    workflows.add(new Object[]{
                        rs.getInt("ID"),
                        rs.getString("PrimaryName"),
                        rs.getString("Reference")
                    });
                }
            }
        }
        return workflows;
    }

    // Helper methods

    private DFCRTypeSetting mapResultSet(ResultSet rs) throws SQLException {
        DFCRTypeSetting setting = new DFCRTypeSetting();
        setting.setId(rs.getInt("id"));
        setting.setFacetId(rs.getInt("facet_id"));
        setting.setTypeId(rs.getInt("type_id"));
        setting.setTypeName(rs.getString("type_name"));
        setting.setWorkflowCreateId(getNullableInt(rs, "workflow_create_id"));
        setting.setWorkflowEditId(getNullableInt(rs, "workflow_edit_id"));
        setting.setCrTypeId(getNullableInt(rs, "cr_type_id"));
        setting.setCreatedAt(rs.getTimestamp("created_at"));
        setting.setUpdatedAt(rs.getTimestamp("updated_at"));
        
        // Map transient display names if available
        try {
            setting.setWorkflowCreateName(rs.getString("workflow_create_name"));
        } catch (SQLException ignored) {}
        try {
            setting.setWorkflowEditName(rs.getString("workflow_edit_name"));
        } catch (SQLException ignored) {}
        try {
            setting.setCrTypeName(rs.getString("cr_type_name"));
        } catch (SQLException ignored) {}
        
        return setting;
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

