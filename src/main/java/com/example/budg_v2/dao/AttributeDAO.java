package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Attribute;
import com.example.budg_v2.util.DefaultStakeholderUtil;
import com.example.budg_v2.util.ReferenceNumberGenerator;
import com.example.budg_v2.util.RefNumberValidator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttributeDAO {

    public java.util.List<Integer> getAttributeIdsByDatasetId(int datasetId) throws SQLException {
        String sql = "SELECT ID FROM attribute WHERE Dataset_ID = ? AND Deleted_Datetime IS NULL";
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("ID"));
                }
            }
        }
        return ids;
    }

    public Integer getDatasetIdForAttribute(int attributeId) throws SQLException {
        String sql = "SELECT Dataset_ID FROM attribute WHERE ID = ? AND Deleted_Datetime IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attributeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object v = rs.getObject("Dataset_ID");
                    return v == null ? null : ((Number) v).intValue();
                }
            }
        }
        return null;
    }

    public java.util.List<Map<String, Object>> getAttributesByDatasetId(int datasetId) throws SQLException {
        String sql =
                "SELECT  " +
                "    a.ID, " +
                "    a.Is_PrimaryKey       AS `Key attribute`, " +
                "    a.Rank                AS `Rank attribute`, " +
                "    a.RefNumber           AS `Ref. attribute`, " +
                "    a.PrimaryName         AS `Name attribute`, " +
                "    ( " +
                "      SELECT aan.Name " +
                "      FROM attribute_alias_name aan " +
                "      WHERE aan.AttributeID = a.ID AND aan.Name_Type = 1 " +
                "      LIMIT 1 " +
                "    ) AS `DB Field Name attribute`, " +
                "    a.Definition          AS `Definition attribute`, " +
                "    gkde.Name             AS `KDE attribute`, " +
                "    ao.PrimaryName        AS `Origin attribute`, " +
                "    a.Confidence_score    AS `Confidence Score(%)`, " +
                "    r.PrimaryName         AS `Requirement attribute`, " +
                "    g.Name                AS `Glossary Name attribute`, " +
                "    g.Description         AS `Attribute Glossary Definition`, " +
                "    gt.Name               AS `Glossary Type attribute`, " +
                "    ae.PrimaryName        AS `Editability attribute`, " +
                "    aer.PrimaryName       AS `Editability Role attribute`, " +
                "    a.Business_Logic      AS `Business Logic attribute`, " +
                "    adt.PrimaryName       AS `Data Type attribute`, " +
                "    a.DataLength          AS `Data Length attribute`, " +
                "    CONCAT(p.First_Name, ' ', p.Last_Name) AS `Created By attribute`, " +
                "    a.CreatedDatetime     AS `Created Date attribute`, " +
                "    ( " +
                "      SELECT GROUP_CONCAT(at.PrimaryName SEPARATOR '\n') " +
                "      FROM attribute_x_attribute axa " +
                "      JOIN attribute at ON at.ID = axa.Target_AttributeID " +
                "      WHERE axa.Source_AttributeID = a.ID " +
                "    ) AS `Related To attribute`, " +
                "    ( " +
                "      SELECT GROUP_CONCAT(axr.PrimaryName SEPARATOR '\n') " +
                "      FROM attribute_x_attribute axa " +
                "      JOIN attribute_x_attribute_relationtype axr ON axr.ID = axa.Relation_Type " +
                "      WHERE axa.Source_AttributeID = a.ID " +
                "    ) AS `Relationship Type attribute` " +
                "FROM attribute a " +
                "LEFT JOIN glossary g  ON g.ID = a.Glossary_ID " +
                "LEFT JOIN glossary_kde_type gkde ON gkde.ID = g.KDE " +
                "LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "LEFT JOIN attribute_origination ao ON ao.ID = a.Origination " +
                "LEFT JOIN requirement r ON r.ID = a.Requirement_ID " +
                "LEFT JOIN attribute_editability ae ON ae.ID = a.Editability " +
                "LEFT JOIN attribute_edit_role aer ON aer.ID = a.Editability_role " +
                "LEFT JOIN attribute_datatype adt ON adt.ID = a.Data_type_ID " +
                "LEFT JOIN people p ON p.ID = a.CreatedBy " +
                "WHERE a.Dataset_ID = ? AND a.DeletedDatetime IS NULL";

        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToMap(rs));
                }
            }
        }
        return list;
    }

    /**
     * Get a single attribute by its ID as a Map (for pending changes display).
     * Uses the same full query as getAttributesByDatasetId to ensure consistent column set.
     */
    public Map<String, Object> getAttributeMapById(int attributeId) throws SQLException {
        String sql =
                "SELECT  " +
                "    a.ID, " +
                "    a.Is_PrimaryKey       AS `Key attribute`, " +
                "    a.Rank                AS `Rank attribute`, " +
                "    a.RefNumber           AS `Ref. attribute`, " +
                "    a.PrimaryName         AS `Name attribute`, " +
                "    ( " +
                "      SELECT aan.Name " +
                "      FROM attribute_alias_name aan " +
                "      WHERE aan.AttributeID = a.ID AND aan.Name_Type = 1 " +
                "      LIMIT 1 " +
                "    ) AS `DB Field Name attribute`, " +
                "    a.Definition          AS `Definition attribute`, " +
                "    gkde.Name             AS `KDE attribute`, " +
                "    ao.PrimaryName        AS `Origin attribute`, " +
                "    a.Confidence_score    AS `Confidence Score(%)`, " +
                "    r.PrimaryName         AS `Requirement attribute`, " +
                "    g.Name                AS `Glossary Name attribute`, " +
                "    g.Description         AS `Attribute Glossary Definition`, " +
                "    gt.Name               AS `Glossary Type attribute`, " +
                "    ae.PrimaryName        AS `Editability attribute`, " +
                "    aer.PrimaryName       AS `Editability Role attribute`, " +
                "    a.Business_Logic      AS `Business Logic attribute`, " +
                "    adt.PrimaryName       AS `Data Type attribute`, " +
                "    a.DataLength          AS `Data Length attribute`, " +
                "    CONCAT(p.First_Name, ' ', p.Last_Name) AS `Created By attribute`, " +
                "    a.CreatedDatetime     AS `Created Date attribute`, " +
                "    ( " +
                "      SELECT GROUP_CONCAT(at.PrimaryName SEPARATOR '\n') " +
                "      FROM attribute_x_attribute axa " +
                "      JOIN attribute at ON at.ID = axa.Target_AttributeID " +
                "      WHERE axa.Source_AttributeID = a.ID " +
                "    ) AS `Related To attribute`, " +
                "    ( " +
                "      SELECT GROUP_CONCAT(axr.PrimaryName SEPARATOR '\n') " +
                "      FROM attribute_x_attribute axa " +
                "      JOIN attribute_x_attribute_relationtype axr ON axr.ID = axa.Relation_Type " +
                "      WHERE axa.Source_AttributeID = a.ID " +
                "    ) AS `Relationship Type attribute` " +
                "FROM attribute a " +
                "LEFT JOIN glossary g  ON g.ID = a.Glossary_ID " +
                "LEFT JOIN glossary_kde_type gkde ON gkde.ID = g.KDE " +
                "LEFT JOIN glossary_type gt ON gt.ID = g.Type " +
                "LEFT JOIN attribute_origination ao ON ao.ID = a.Origination " +
                "LEFT JOIN requirement r ON r.ID = a.Requirement_ID " +
                "LEFT JOIN attribute_editability ae ON ae.ID = a.Editability " +
                "LEFT JOIN attribute_edit_role aer ON aer.ID = a.Editability_role " +
                "LEFT JOIN attribute_datatype adt ON adt.ID = a.Data_type_ID " +
                "LEFT JOIN people p ON p.ID = a.CreatedBy " +
                "WHERE a.ID = ? AND a.DeletedDatetime IS NULL";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attributeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToMap(rs);
                }
                return null;
            }
        }
    }

    public int insertAttribute(java.util.Map<String, Object> data) throws SQLException {
        // Auto-generate reference number if not provided
        String refNumber = (String) data.get("ref_number");
        if (refNumber == null || refNumber.trim().isEmpty()) {
            // Generate a unique reference number
            int maxRetries = 20;
            int retryCount = 0;
            boolean isUnique = false;
            
            while (!isUnique && retryCount < maxRetries) {
                try {
                    refNumber = ReferenceNumberGenerator.generateAttributeRefNumber();
                    // Validate the generated reference is unique
                    isUnique = RefNumberValidator.isRefNumberUnique("Attribute", refNumber);
                    // If not unique and ref looks like ATT_N, try ATT_(n+1), ATT_(n+2), ... until one is unique
                    if (!isUnique && refNumber != null && refNumber.startsWith("ATT_")) {
                        try {
                            int n = Integer.parseInt(refNumber.substring(4));
                            for (int k = 1; k <= 20; k++) {
                                String candidate = "ATT_" + (n + k);
                                if (RefNumberValidator.isRefNumberUnique("Attribute", candidate)) {
                                    refNumber = candidate;
                                    isUnique = true;
                                    break;
                                }
                            }
                        } catch (NumberFormatException ignored) {
                            // not ATT_N format, fall through to outer retry
                        }
                    }
                    if (!isUnique) {
                        retryCount++;
                        // Small delay to avoid rapid retries (optional, but helps with concurrent inserts)
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (SQLException e) {
                    // If generation fails, throw the exception
                    throw new SQLException("Error generating reference number: " + e.getMessage(), e);
                }
            }
            
            if (!isUnique) {
                throw new SQLException("Failed to generate a unique reference number after " + maxRetries + " attempts. Please try again.");
            }
            
            // Update the data map with the generated reference
            data.put("ref_number", refNumber);
        }
        
        // Enforce uniqueness of PrimaryName and RefNumber per Dataset
        validateUniquePerDataset(data, null);
        String sql = "INSERT INTO attribute (Is_PrimaryKey, Requirement_ID, Business_Logic, RefNumber, PrimaryName, Definition, Glossary_ID, Origination, Editability, Editability_role, Data_type_ID, DataLength, Dataset_ID, CreatedBy, Confidence_score, CreatedDatetime, Last_UpdateDatetime) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NULL)";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            int attributeId;
            try (PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                setNullableInt(ps, 1, data.get("is_primary_key"));
                setNullableInt(ps, 2, data.get("requirement_id"));
                setNullableString(ps, 3, (String) data.get("business_logic"));
                setNullableString(ps, 4, (String) data.get("ref_number"));
                setNullableString(ps, 5, (String) data.get("primary_name"));
                setNullableString(ps, 6, (String) data.get("definition"));
                setNullableInt(ps, 7, data.get("glossary_id"));
                setNullableInt(ps, 8, data.get("origination"));
                setNullableInt(ps, 9, data.get("editability"));
                setNullableInt(ps, 10, data.get("editability_role"));
                setNullableInt(ps, 11, data.get("data_type_id"));
                setNullableInt(ps, 12, data.get("data_length"));
                setNullableInt(ps, 13, data.get("dataset_id"));
                setNullableInt(ps, 14, data.get("created_by"));
                setNullableFloat(ps, 15, data.get("confidence_score"));

                int affected = ps.executeUpdate();
                if (affected == 0) throw new SQLException("Insert failed, no rows affected");
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("Insert succeeded but no ID obtained");
                    attributeId = rs.getInt(1);
                }
            }
            
            // Get created_by user ID
            Object createdByObj = data.get("created_by");
            Integer createdBy = null;
            if (createdByObj instanceof Number) {
                createdBy = ((Number) createdByObj).intValue();
            } else if (createdByObj != null) {
                createdBy = Integer.parseInt(createdByObj.toString());
            }
            
            // Save DB Field Name if provided
            try {
                String dbFieldName = (String) data.get("db_field_name");
                saveOrUpdateDbFieldName(conn, attributeId, dbFieldName, createdBy);
            } catch (Exception e) {
                System.err.println("❌ Error saving DB Field Name: " + e.getMessage());
                // Continue - don't fail the main operation
            }
            
            // Create audit records
            if (createdBy != null) {
                try {
                    String userName = getPersonFullName(conn, createdBy);
                    if (userName != null) {
                        // Create audit history records
                        createAttributeAuditRecords(conn, attributeId, userName);
                        
                        // Create audit snapshot
                        createAttributeAuditRecord(conn, attributeId);
                        
                        // Create default stakeholder
                        createDefaultStakeholder(conn, attributeId, createdBy);
                        
                        //system.out.println("✅ Default stakeholder and audit records created for attribute ID: " + attributeId);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating default stakeholder/audit records: " + e.getMessage());
                    // Continue - don't fail the main operation
                }
            }
            
            conn.commit();
            return attributeId;
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    public boolean updateAttribute(java.util.Map<String, Object> data) throws SQLException {
        // Enforce uniqueness of PrimaryName and RefNumber per Dataset, excluding current record
        Object idObj = data.get("id");
        Integer id = null;
        if (idObj instanceof Number) id = ((Number) idObj).intValue();
        else if (idObj != null) id = Integer.parseInt(idObj.toString());
        validateUniquePerDataset(data, id);
        
        // Step 1: Get old attribute data for comparison
        if (id == null) {
            throw new SQLException("Attribute ID is required for update");
        }
        Attribute oldAttribute = getAttributeById(id);
        if (oldAttribute == null) {
            throw new SQLException("Attribute not found with ID: " + id);
        }
        
        String sql = "UPDATE attribute SET " +
                "Is_PrimaryKey = ?, " +
                "Requirement_ID = ?, " +
                "Business_Logic = ?, " +
                "RefNumber = ?, " +
                "PrimaryName = ?, " +
                "Definition = ?, " +
                "Glossary_ID = ?, " +
                "Origination = ?, " +
                "Editability = ?, " +
                "Editability_role = ?, " +
                "Data_type_ID = ?, " +
                "DataLength = ?, " +
                "Confidence_score = ?, " +
                "Last_UpdateDatetime = NOW(), " +
                "Last_UpdatedUser_ID = ? " +
                "WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInt(ps, 1, data.get("is_primary_key"));
            setNullableInt(ps, 2, data.get("requirement_id"));
            setNullableString(ps, 3, (String) data.get("business_logic"));
            setNullableString(ps, 4, (String) data.get("ref_number"));
            setNullableString(ps, 5, (String) data.get("primary_name"));
            setNullableString(ps, 6, (String) data.get("definition"));
            setNullableInt(ps, 7, data.get("glossary_id"));
            setNullableInt(ps, 8, data.get("origination"));
            setNullableInt(ps, 9, data.get("editability"));
            setNullableInt(ps, 10, data.get("editability_role"));
            setNullableInt(ps, 11, data.get("data_type_id"));
            setNullableInt(ps, 12, data.get("data_length"));
            setNullableFloat(ps, 13, data.get("confidence_score"));
            setNullableInt(ps, 14, data.get("last_updated_user_id"));
            setRequiredInt(ps, 15, data.get("id"));

            int affectedRows = ps.executeUpdate();
            
            if (affectedRows > 0) {
                // Save DB Field Name if provided
                try {
                    String dbFieldName = (String) data.get("db_field_name");
                    Object lastUpdatedUserIdObj = data.get("last_updated_user_id");
                    Integer userId = null;
                    if (lastUpdatedUserIdObj != null) {
                        userId = getIntValue(lastUpdatedUserIdObj);
                    }
                    saveOrUpdateDbFieldName(conn, id, dbFieldName, userId);
                } catch (Exception e) {
                    System.err.println("❌ Error saving DB Field Name: " + e.getMessage());
                    // Continue - don't fail the main operation
                }
                
                // Step 2: Create audit records for the updates
                try {
                    // Build new Attribute object from data
                    Attribute newAttribute = new Attribute();
                    newAttribute.setId(id);
                    newAttribute.setIsPrimaryKey(getIntValue(data.get("is_primary_key")));
                    newAttribute.setRequirementId(getIntValue(data.get("requirement_id")));
                    newAttribute.setBusinessLogic((String) data.get("business_logic"));
                    newAttribute.setRefNumber((String) data.get("ref_number"));
                    newAttribute.setPrimaryName((String) data.get("primary_name"));
                    newAttribute.setDefinition((String) data.get("definition"));
                    newAttribute.setGlossaryId(getIntValue(data.get("glossary_id")));
                    newAttribute.setOrigination(getIntValue(data.get("origination")));
                    newAttribute.setEditability(getIntValue(data.get("editability")));
                    newAttribute.setEditabilityRole(getIntValue(data.get("editability_role")));
                    newAttribute.setDataTypeId(getIntValue(data.get("data_type_id")));
                    newAttribute.setDataLength(getIntValue(data.get("data_length")));
                    
                    // Get user name for audit
                    String userName = "System"; // Default fallback
                    Object lastUpdatedUserIdObj = data.get("last_updated_user_id");
                    if (lastUpdatedUserIdObj != null) {
                        Integer userId = getIntValue(lastUpdatedUserIdObj);
                        if (userId != null && userId > 0) {
                            String fullName = getPersonFullName(conn, userId);
                            if (fullName != null && !fullName.trim().isEmpty()) {
                                userName = fullName;
                            } else {
                                userName = "User ID: " + userId;
                            }
                        }
                    }
                    
                    // id is already checked for null above, so safe to use here
                    createAttributeUpdateAuditRecords(id.intValue(), oldAttribute, newAttribute, userName);
                    //system.out.println("✅ Attribute update audit records created for ID: " + id + " with author: " + userName);
                } catch (Exception e) {
                    System.err.println("❌ Error creating attribute update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // Step 3: Create snapshot in attribute_audit
                try {
                    // id is already checked for null above, so safe to use here
                    createAttributeUpdateAuditSnapshot(id.intValue());
                    //system.out.println("✅ AttributeDAO: attribute_audit update snapshot created for ID: " + id);
                } catch (Exception e) {
                    System.err.println("❌ Error creating attribute_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return affectedRows > 0;
        }
    }
    
    /**
     * Helper method to convert Object to Integer
     */
    private Integer getIntValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private void validateUniquePerDataset(java.util.Map<String, Object> data, Integer excludeId) throws SQLException {
        String primaryName = (String) data.get("primary_name");
        String refNumber = (String) data.get("ref_number");
        Object dsObj = data.get("dataset_id");
        Integer datasetId = null;
        if (dsObj instanceof Number) datasetId = ((Number) dsObj).intValue();
        else if (dsObj != null) datasetId = Integer.parseInt(dsObj.toString());

        if (datasetId == null) return; // cannot validate without dataset

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Validate Primary Name uniqueness per dataset
            if (primaryName != null && !primaryName.isBlank()) {
                String sql = "SELECT ID FROM attribute WHERE Dataset_ID = ? AND LOWER(PrimaryName) = LOWER(?) AND DeletedDatetime IS NULL" + (excludeId != null ? " AND ID <> ?" : "") + " LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, datasetId);
                    ps.setString(2, primaryName);
                    if (excludeId != null) ps.setInt(3, excludeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) throw new SQLException("Primary Name already exists in this dataset. Please choose a different name.");
                    }
                }
            }
            
            // Validate RefNumber uniqueness across all datasets - use centralized RefNumberValidator
            // Rule: Can repeat ref if it's the same object (excludeId), but cannot repeat for different objects.
            // When editing an attribute that belongs to a cloned dataset (CR), exclude "twin" attribute(s)
            // in the original/clone pair so we don't treat the same logical ref as duplicate.
            if (refNumber != null && !refNumber.isBlank()) {
                try {
                    boolean isUnique;
                    if (excludeId != null) {
                        // Update operation - exclude current object ID and any twin attributes (same ref in original/clone dataset)
                        java.util.List<Integer> twinIds = getTwinAttributeIdsForRefNumber(conn, refNumber, datasetId, excludeId);
                        isUnique = RefNumberValidator.isRefNumberUniqueForUpdate("Attribute", refNumber, excludeId, twinIds);
                    } else {
                        // Create operation - check uniqueness
                        isUnique = RefNumberValidator.isRefNumberUnique("Attribute", refNumber);
                    }
                    if (!isUnique) {
                        throw new SQLException("This reference number is already in use. Please enter a unique reference number.");
                    }
                } catch (SQLException e) {
                    throw e; // Re-throw SQLException
                } catch (Exception e) {
                    throw new SQLException("Error validating RefNumber uniqueness: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * For an attribute update in a dataset that is part of an original/clone pair (change request),
     * returns attribute ID(s) that have the same RefNumber in the paired dataset(s), so the validator
     * can exclude them and not treat the same logical ref as duplicate.
     */
    private static java.util.List<Integer> getTwinAttributeIdsForRefNumber(Connection conn, String refNumber, int datasetId, int excludeAttributeId) throws SQLException {
        java.util.List<Integer> out = new ArrayList<>();
        if (refNumber == null || refNumber.isBlank()) return out;
        // Check if datasetId is a clone (nobject_id) -> get original object_id and find attribute there with same ref
        String sqlClone = "SELECT fc.object_id FROM dataset_changes fc INNER JOIN changerequest cr ON fc.change_request_id = cr.ID WHERE fc.nobject_id = ? AND cr.Deleted_At IS NULL LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sqlClone)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int originalDatasetId = rs.getInt("object_id");
                    String sqlAttr = "SELECT ID FROM attribute WHERE LOWER(RefNumber) = LOWER(?) AND Dataset_ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '') AND ID != ? LIMIT 1";
                    try (PreparedStatement ps2 = conn.prepareStatement(sqlAttr)) {
                        ps2.setString(1, refNumber);
                        ps2.setInt(2, originalDatasetId);
                        ps2.setInt(3, excludeAttributeId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            if (rs2.next()) out.add(rs2.getInt("ID"));
                        }
                    }
                    return out;
                }
            }
        }
        // Check if datasetId is original (object_id) -> get all nobject_ids and find attributes with same ref there
        String sqlOriginal = "SELECT DISTINCT fc.nobject_id FROM dataset_changes fc INNER JOIN changerequest cr ON fc.change_request_id = cr.ID WHERE fc.object_id = ? AND cr.Deleted_At IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sqlOriginal)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<Integer> cloneDatasetIds = new ArrayList<>();
                while (rs.next()) cloneDatasetIds.add(rs.getInt("nobject_id"));
                for (Integer cloneId : cloneDatasetIds) {
                    String sqlAttr = "SELECT ID FROM attribute WHERE LOWER(RefNumber) = LOWER(?) AND Dataset_ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '') AND ID != ?";
                    try (PreparedStatement ps2 = conn.prepareStatement(sqlAttr)) {
                        ps2.setString(1, refNumber);
                        ps2.setInt(2, cloneId);
                        ps2.setInt(3, excludeAttributeId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next()) out.add(rs2.getInt("ID"));
                        }
                    }
                }
            }
        }
        return out;
    }

    public boolean deleteAttribute(int id) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Step 1: Delete all stakeholders linked to this attribute
            // First delete from attribute_x_objectxpeople
            String deleteStakeholdersSql = "DELETE FROM attribute_x_objectxpeople WHERE AttributeID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteStakeholdersSql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            
            // Step 2: Delete the attribute itself
            String sql = "DELETE FROM attribute WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                int affectedRows = ps.executeUpdate();
                
                if (affectedRows > 0) {
                    conn.commit();
                    return true;
                } else {
                    conn.rollback();
                    return false;
                }
            }
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Soft delete Attribute with audit records
     * This is the preferred deletion method as it preserves data and creates audit trail
     */
    public boolean deleteAttributeWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Step 1: Delete all stakeholders linked to this attribute
            // Delete from attribute_x_objectxpeople first (foreign key constraint)
            String deleteStakeholdersSql = "DELETE FROM attribute_x_objectxpeople WHERE AttributeID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteStakeholdersSql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            
            // Step 2: Soft delete (set DeletedDatetime)
            String deleteSql = "UPDATE attribute SET DeletedDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // Step 3: Create audit history record
                String auditSql = """
                    INSERT INTO attribute_audit_history (id, object, event, updateType, field, `from`, `to`, author, date, lastChange)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, NOW(), NOW())
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Attribute");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Attribute");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // Step 3: Create snapshot in attribute_audit
                // Rev is auto-increment in database
                String snapshotSql = """
                    INSERT INTO attribute_audit (
                        ID, Data_type_ID, Requirement_ID, Dataset_ID, Glossary_ID, Origination,
                        Editability, Editability_role, RefNumber, PrimaryName, Definition,
                        Is_Mandatory, Is_PrimaryKey, Rank, Business_Logic,
                        CreatedDatetime, Last_UpdateDatetime, DeletedDatetime,
                        Confidence_score, DataLength, CreatedBy, Last_UpdatedUser_ID, RevType
                    )
                    SELECT 
                        ID, Data_type_ID, Requirement_ID, Dataset_ID, Glossary_ID, Origination,
                        Editability, Editability_role, RefNumber, PrimaryName, Definition,
                        Is_Mandatory, Is_PrimaryKey, Rank, Business_Logic,
                        CreatedDatetime, Last_UpdateDatetime, DeletedDatetime,
                        Confidence_score, DataLength, CreatedBy, Last_UpdatedUser_ID, 'Deleted'
                    FROM attribute 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                    //system.out.println("✅ Delete snapshot created");
                }
                
                //system.out.println("✅ Attribute deleted with audit for ID: " + id);
            }
            
            conn.commit();
            return affectedRows > 0;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("❌ Error during rollback: " + rollbackEx.getMessage());
                }
            }
            throw e;
        } finally {
            try {
                if (deleteStmt != null) deleteStmt.close();
                if (auditStmt != null) auditStmt.close();
            } catch (SQLException e) {
                System.err.println("❌ Error closing statement: " + e.getMessage());
            }
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("❌ Error closing connection: " + e.getMessage());
            }
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else if (value instanceof Number) {
            ps.setInt(index, ((Number) value).intValue());
        } else {
            ps.setInt(index, Integer.parseInt(value.toString()));
        }
    }

    private void setRequiredInt(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) throw new SQLException("Required integer parameter missing at index " + index);
        setNullableInt(ps, index, value);
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private void setNullableFloat(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.FLOAT);
        } else if (value instanceof Number) {
            ps.setFloat(index, ((Number) value).floatValue());
        } else {
            ps.setFloat(index, Float.parseFloat(value.toString()));
        }
    }

    /**
     * Save or update DB Field Name in attribute_alias_name table
     * Name_Type = 1 represents DB Field Name
     */
    private void saveOrUpdateDbFieldName(Connection conn, int attributeId, String dbFieldName, Integer userId) throws SQLException {
        // Delete existing DB Field Name alias (Name_Type = 1) for this attribute
        String deleteSql = "DELETE FROM attribute_alias_name WHERE AttributeID = ? AND Name_Type = 1";
        try (PreparedStatement delPs = conn.prepareStatement(deleteSql)) {
            delPs.setInt(1, attributeId);
            delPs.executeUpdate();
        }
        
        // Insert new DB Field Name if provided
        if (dbFieldName != null && !dbFieldName.trim().isEmpty()) {
            String insertSql = "INSERT INTO attribute_alias_name (AttributeID, Name, Name_Type, Last_UpdateUser_ID, Last_UpdateDatetime) VALUES (?, ?, 1, ?, NOW())";
            try (PreparedStatement insPs = conn.prepareStatement(insertSql)) {
                insPs.setInt(1, attributeId);
                insPs.setString(2, dbFieldName.trim());
                if (userId != null) {
                    insPs.setInt(3, userId);
                } else {
                    insPs.setNull(3, java.sql.Types.INTEGER);
                }
                insPs.executeUpdate();
            }
        }
    }

    // Lookup lists for dropdowns
    public java.util.List<java.util.Map<String, Object>> listRequirements() throws SQLException {
        String sql = "SELECT ID as id, PrimaryName as name FROM requirement ORDER BY PrimaryName";
        return listIdName(sql);
    }

    public java.util.List<java.util.Map<String, Object>> listGlossaries() throws SQLException {
        String sql = "SELECT ID as id, Name as name FROM glossary ORDER BY Name";
        return listIdName(sql);
    }

    public java.util.List<java.util.Map<String, Object>> listOriginations() throws SQLException {
        String sql = "SELECT ID as id, PrimaryName as name FROM attribute_origination ORDER BY PrimaryName";
        return listIdName(sql);
    }

    public java.util.List<java.util.Map<String, Object>> listEditabilities() throws SQLException {
        String sql = "SELECT ID as id, PrimaryName as name FROM attribute_editability ORDER BY PrimaryName";
        return listIdName(sql);
    }

    public java.util.List<java.util.Map<String, Object>> listEditRoles() throws SQLException {
        String sql = "SELECT ID as id, PrimaryName as name FROM attribute_edit_role ORDER BY PrimaryName";
        return listIdName(sql);
    }

    /**
     * Get Data Type name by ID
     */
    public String getDataTypeNameById(Integer dataTypeId) throws SQLException {
        if (dataTypeId == null) return null;
        String sql = "SELECT PrimaryName FROM attribute_datatype WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dataTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        }
        return null;
    }

    public java.util.List<java.util.Map<String, Object>> listDataTypes() throws SQLException {
        String sql = "SELECT ID as id, PrimaryName as name FROM attribute_datatype WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";
        return listIdName(sql);
    }

    private java.util.List<java.util.Map<String, Object>> listIdName(String sql) throws SQLException {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("id", rs.getObject("id"));
                row.put("name", rs.getObject("name"));
                list.add(row);
            }
        }
        return list;
    }

    private Map<String, Object> mapRowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            Object value = rs.getObject(i);
            // Normalize Java Time types to String to avoid Gson LocalDateTime adapter issues
            if (value instanceof java.time.LocalDateTime) {
                java.time.LocalDateTime dt = (java.time.LocalDateTime) value;
                value = dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else if (value instanceof java.time.LocalDate) {
                java.time.LocalDate d = (java.time.LocalDate) value;
                value = d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else if (value instanceof java.time.LocalTime) {
                java.time.LocalTime t = (java.time.LocalTime) value;
                value = t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            }
            map.put(label, value);
        }
        // Also add lowercase 'id' for compatibility if 'ID' exists
        if (map.containsKey("ID") && !map.containsKey("id")) {
            map.put("id", map.get("ID"));
        }
        return map;
    }

    // ==================== AUDIT METHODS ====================

    /**
     * Create audit records for a new attribute (with existing connection)
     */
    public void createAttributeAuditRecords(Connection conn, int attributeId, String userName) throws SQLException {
        PreparedStatement auditStmt = null;
        
        try {
            // Get attribute data
            String attributeDataSql = "SELECT * FROM attribute WHERE ID = ?";
            PreparedStatement attributeStmt = conn.prepareStatement(attributeDataSql);
            attributeStmt.setInt(1, attributeId);
            ResultSet attributeRs = attributeStmt.executeQuery();
            
            if (!attributeRs.next()) {
                throw new SQLException("Attribute not found with ID: " + attributeId);
            }

            // Prepare audit statement
            String auditSql = """
                INSERT INTO attribute_audit_history (id, object, event, updateType, field, `from`, `to`, author, date, lastChange)
                VALUES (?, 'Attribute', 'Details', ?, ?, NULL, ?, ?, NOW(), NOW())
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            String primaryName = attributeRs.getString("PrimaryName");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, attributeId, "Added", "Primary Name", primaryName, userName);
            }
            
            // Definition
            String definition = attributeRs.getString("Definition");
            if (definition != null && !definition.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, attributeId, "Added", "Definition", definition, userName);
            }
            
        } finally {
            if (auditStmt != null) auditStmt.close();
        }
    }
    
    /**
     * Create audit records for a new attribute (creates own connection)
     */
    public void createAttributeAuditRecords(int attributeId, String userName) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            createAttributeAuditRecords(conn, attributeId, userName);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Create audit record helper
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int attributeId, 
                                    String updateType, String field, String value, String userName) throws SQLException {
        auditStmt.setInt(1, attributeId);
        auditStmt.setString(2, updateType);
        auditStmt.setString(3, field);
        auditStmt.setString(4, value);
        auditStmt.setString(5, userName);
        
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1); // Returns auditidpk
            }
        }
        return -1;
    }

    // ==================== STAKEHOLDER METHODS ====================

    /**
     * Create stakeholder audit records for attribute (creates own connection)
     */
    public void createStakeholderAuditRecords(int attributeId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            createStakeholderAuditRecords(conn, attributeId, userName, userFullName, roleId);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Get role name by ID (with existing connection)
     */
    private String getRoleName(Connection conn, int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }
    
    /**
     * Create object_x_people record for stakeholder
     * Always creates a NEW record (no reuse)
     */
    public int createObjectXPeople(Connection conn, java.util.Map<String, Object> stakeholder, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
            VALUES (NULL, ?, ?, 2, 1, NOW(), ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, (Integer) stakeholder.get("userId")); // ipid
            ps.setInt(2, (Integer) stakeholder.get("roleId")); // RoleID
            ps.setInt(3, currentUserId); // lastupdateuser_id

            ps.executeUpdate();

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    //system.out.println("✅ Generated object_x_people ID: " + newId);
                    return newId;
                } else {
                    throw new SQLException("Creating object_x_people failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Link stakeholder to attribute via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToAttribute(Connection conn, int attributeId, int objectXPeopleId, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO attribute_x_objectxpeople (AttributeID, Object_x_ipid, Last_UpdateUser_ID, CreateDatetime)
            VALUES (?, ?, ?, NOW())
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attributeId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, currentUserId);
            try {
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: AttributeID=" + attributeId + ", Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }

    // ==================== DEFAULT STAKEHOLDER METHODS ====================

    /**
     * Get module ID by module name
     */
    private int getModuleId(Connection conn, String moduleName) throws SQLException {
        String sql = "SELECT id FROM module WHERE primaryname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, moduleName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        throw new SQLException("Module not found: " + moduleName);
    }

    /**
     * Get default role ID for Attribute module
     * Falls back to first role with defaultrole=1 if module role not found
     */
    private int getDefaultRoleId(Connection conn) throws SQLException {
        try {
            int moduleId = getModuleId(conn, "Attribute");
            String sql = "SELECT id FROM object_role WHERE module = ? ORDER BY primaryname LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, moduleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        } catch (SQLException e) {
            // If module not found, try to get any default role
            //system.out.println("⚠️ Attribute module not found, trying default role: " + e.getMessage());
        }
        
        // Fallback: get first role with defaultrole=1
        String fallbackSql = "SELECT id FROM object_role WHERE defaultrole = 1 ORDER BY id LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(fallbackSql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    //system.out.println("✅ Using fallback default role ID: " + rs.getInt("id"));
                    return rs.getInt("id");
                }
            }
        }
        
        // Last fallback: get first role
        String lastFallbackSql = "SELECT id FROM object_role ORDER BY id LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(lastFallbackSql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    //system.out.println("✅ Using first available role ID: " + rs.getInt("id"));
                    return rs.getInt("id");
                }
            }
        }
        
        throw new SQLException("No role found in object_role table");
    }

    /**
     * Get person full name by ID (with existing connection)
     */
    private String getPersonFullName(Connection conn, int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) AS full_name FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("full_name");
                }
            }
        }
        return null;
    }
    
    /**
     * Get person full name by ID (creates own connection)
     */
    @SuppressWarnings("unused")
    private String getPersonFullName(int personId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return getPersonFullName(conn, personId);
        }
    }

    /**
     * Create default stakeholder for a new attribute
     * Creates stakeholders for all default roles the creator should receive based on role assignments
     */
    private void createDefaultStakeholder(Connection conn, int attributeId, int userId) throws SQLException {
        try {
            // Get module ID for Attribute
            int moduleId = DefaultStakeholderUtil.getModuleId(conn, "Attribute");
            
            // Get all default roles the creator should receive
            List<Integer> rolesToAssign = DefaultStakeholderUtil.getDefaultRolesForCreator(conn, moduleId, userId);
            
            if (rolesToAssign.isEmpty()) {
                // No default roles to assign
                return;
            }
            
            // Create stakeholders for each role
            for (Integer roleId : rolesToAssign) {
                try {
                    // Insert into object_x_people
                    String insertOXP = """
                        INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
                        VALUES (NULL, ?, ?, 2, 1, NOW(), ?)
                    """;
                    
                    int objectXPeopleId;
                    try (PreparedStatement ps = conn.prepareStatement(insertOXP, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, userId); // ipid
                        ps.setInt(2, roleId); // RoleID
                        ps.setInt(3, userId); // lastupdateuser_id
                        
                        int rowsAffected = ps.executeUpdate();
                        if (rowsAffected == 0) {
                            throw new SQLException("Failed to insert object_x_people");
                        }
                        
                        try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                objectXPeopleId = generatedKeys.getInt(1);
                            } else {
                                throw new SQLException("Failed to get generated key for object_x_people");
                            }
                        }
                    }
                    
                    // Insert into attribute_x_objectxpeople
                    String insertAXOP = """
                        INSERT INTO attribute_x_objectxpeople (AttributeID, Object_x_ipid, Last_UpdateUser_ID, CreateDatetime)
                        VALUES (?, ?, ?, NOW())
                    """;
                    
                    try (PreparedStatement ps = conn.prepareStatement(insertAXOP)) {
                        ps.setInt(1, attributeId);
                        ps.setInt(2, objectXPeopleId);
                        ps.setInt(3, userId);
                        
                        int rowsAffected = ps.executeUpdate();
                        if (rowsAffected == 0) {
                            throw new SQLException("Failed to insert attribute_x_objectxpeople");
                        }
                    }
                    
                    // Create stakeholder audit records
                    String userName = getPersonFullName(conn, userId);
                    String userFullName = userName != null ? userName : "User " + userId;
                    createStakeholderAuditRecords(conn, attributeId, userName != null ? userName : "System", userFullName, roleId);
                } catch (SQLException e) {
                    // Log error but continue with other roles
                    System.err.println("❌ Error creating default stakeholder for role " + roleId + ": " + e.getMessage());
                    // Continue processing other roles
                }
            }
        } catch (SQLException e) {
            // If module not found or other critical error, log but don't fail object creation
            System.err.println("❌ Error in createDefaultStakeholder: " + e.getMessage());
            // Don't throw - allow object creation to continue
        }
    }

    /**
     * Create stakeholder audit records for attribute (with existing connection)
     */
    public void createStakeholderAuditRecords(Connection conn, int attributeId, String userName, String userFullName, int roleId) throws SQLException {
        PreparedStatement auditStmt = null;
        
        try {
            String auditSql = """
                INSERT INTO attribute_audit_history (id, object, event, updateType, field, `from`, `to`, author, date, lastChange)
                VALUES (?, 'Stakeholder', 'link', ?, ?, NULL, ?, ?, NOW(), NOW())
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Get role name
            String roleName = getRoleName(conn, roleId);
            if (roleName == null) roleName = "Attribute Owner";
            
            // Role
            createNewAuditRecord(conn, auditStmt, attributeId, "Added", "Role", roleName, userName);
            
            // Role Status
            createNewAuditRecord(conn, auditStmt, attributeId, "Added", "Role Status", "Active", userName);
            
            // Name
            createNewAuditRecord(conn, auditStmt, attributeId, "Added", "Name", userFullName, userName);
            
        } finally {
            if (auditStmt != null) auditStmt.close();
        }
    }

    /**
     * Create audit snapshot in attribute_audit table
     * Rev is auto-increment in database
     */
    public void createAttributeAuditRecord(Connection conn, int attributeId) throws SQLException {
        //system.out.println("🔍 AttributeDAO.createAttributeAuditRecord - Creating snapshot for ID: " + attributeId);
        
        String sql = """
            INSERT INTO attribute_audit (
                ID, Data_type_ID, Requirement_ID, Dataset_ID, Glossary_ID, Origination,
                Editability, Editability_role, RefNumber, PrimaryName, Definition,
                Is_Mandatory, Is_PrimaryKey, Rank, Business_Logic,
                CreatedDatetime, Last_UpdateDatetime, DeletedDatetime,
                Confidence_score, DataLength, CreatedBy, Last_UpdatedUser_ID, RevType
            )
            SELECT 
                ID, Data_type_ID, Requirement_ID, Dataset_ID, Glossary_ID, Origination,
                Editability, Editability_role, RefNumber, PrimaryName, Definition,
                Is_Mandatory, Is_PrimaryKey, Rank, Business_Logic,
                CreatedDatetime, Last_UpdateDatetime, DeletedDatetime,
                Confidence_score, DataLength, CreatedBy, Last_UpdatedUser_ID, 'Added'
            FROM attribute 
            WHERE ID = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attributeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ AttributeDAO.createAttributeAuditRecord - ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // ==================== HELPER METHODS FOR UPDATE AUDIT ====================

    /**
     * Helper method to fetch Attribute by ID for comparison purposes
     */
    private Attribute getAttributeById(int id) throws SQLException {
        String sql = """
            SELECT ID, Data_type_ID, Requirement_ID, Dataset_ID, Glossary_ID, Origination,
                   Editability, Editability_role, RefNumber, PrimaryName, Definition,
                   Is_Mandatory, Is_PrimaryKey, Rank, Business_Logic, DataLength
            FROM attribute WHERE ID = ?
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Attribute attribute = new Attribute();
                    attribute.setId(rs.getInt("ID"));
                    attribute.setDataTypeId((Integer) rs.getObject("Data_type_ID"));
                    attribute.setRequirementId((Integer) rs.getObject("Requirement_ID"));
                    attribute.setDatasetId((Integer) rs.getObject("Dataset_ID"));
                    attribute.setGlossaryId((Integer) rs.getObject("Glossary_ID"));
                    attribute.setOrigination((Integer) rs.getObject("Origination"));
                    attribute.setEditability((Integer) rs.getObject("Editability"));
                    attribute.setEditabilityRole((Integer) rs.getObject("Editability_role"));
                    attribute.setRefNumber(rs.getString("RefNumber"));
                    attribute.setPrimaryName(rs.getString("PrimaryName"));
                    attribute.setDefinition(rs.getString("Definition"));
                    attribute.setIsMandatory((Integer) rs.getObject("Is_Mandatory"));
                    attribute.setIsPrimaryKey((Integer) rs.getObject("Is_PrimaryKey"));
                    attribute.setRank((Integer) rs.getObject("Rank"));
                    attribute.setBusinessLogic(rs.getString("Business_Logic"));
                    attribute.setDataLength((Integer) rs.getObject("DataLength"));
                    return attribute;
                }
            }
        }
        return null;
    }

    /**
     * Create audit records when updating an attribute
     * Compares old and new values and logs the differences
     */
    private void createAttributeUpdateAuditRecords(int attributeId, Attribute oldAttribute, Attribute newAttribute, String userName) throws SQLException {
        //system.out.println("🔍 AttributeDAO.createAttributeUpdateAuditRecords - START for ID: " + attributeId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO attribute_audit_history (id, object, event, updateType, field, `from`, `to`, author, date, lastChange)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldAttribute.getPrimaryName(), newAttribute.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Primary Name", oldAttribute.getPrimaryName(), newAttribute.getPrimaryName(), userName);
            }
            
            // Ref Number
            if (!isEqual(oldAttribute.getRefNumber(), newAttribute.getRefNumber())) {
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Reference Number", oldAttribute.getRefNumber(), newAttribute.getRefNumber(), userName);
            }
            
            // Definition
            if (!isEqual(oldAttribute.getDefinition(), newAttribute.getDefinition())) {
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Definition", oldAttribute.getDefinition(), newAttribute.getDefinition(), userName);
            }
            
            // Business Logic
            if (!isEqual(oldAttribute.getBusinessLogic(), newAttribute.getBusinessLogic())) {
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Business Logic", oldAttribute.getBusinessLogic(), newAttribute.getBusinessLogic(), userName);
            }
            
            // Is Primary Key
            if (!isEqual(oldAttribute.getIsPrimaryKey(), newAttribute.getIsPrimaryKey())) {
                String oldValue = oldAttribute.getIsPrimaryKey() != null && oldAttribute.getIsPrimaryKey() == 1 ? "Yes" : "No";
                String newValue = newAttribute.getIsPrimaryKey() != null && newAttribute.getIsPrimaryKey() == 1 ? "Yes" : "No";
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Is Primary Key", oldValue, newValue, userName);
            }
            
            // Requirement (store name for readability)
            if (!isEqual(oldAttribute.getRequirementId(), newAttribute.getRequirementId())) {
                String oldReqName = oldAttribute.getRequirementId() != null ? getRequirementName(conn, oldAttribute.getRequirementId()) : null;
                String newReqName = newAttribute.getRequirementId() != null ? getRequirementName(conn, newAttribute.getRequirementId()) : null;
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Requirement", oldReqName, newReqName, userName);
            }
            
            // Glossary (store name for readability)
            if (!isEqual(oldAttribute.getGlossaryId(), newAttribute.getGlossaryId())) {
                String oldGlossaryName = oldAttribute.getGlossaryId() != null ? getGlossaryName(conn, oldAttribute.getGlossaryId()) : null;
                String newGlossaryName = newAttribute.getGlossaryId() != null ? getGlossaryName(conn, newAttribute.getGlossaryId()) : null;
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Glossary", oldGlossaryName, newGlossaryName, userName);
            }
            
            // Origination (store name for readability)
            if (!isEqual(oldAttribute.getOrigination(), newAttribute.getOrigination())) {
                String oldOriginationName = oldAttribute.getOrigination() != null ? getOriginationName(conn, oldAttribute.getOrigination()) : null;
                String newOriginationName = newAttribute.getOrigination() != null ? getOriginationName(conn, newAttribute.getOrigination()) : null;
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Origination", oldOriginationName, newOriginationName, userName);
            }
            
            // Editability (store name for readability)
            if (!isEqual(oldAttribute.getEditability(), newAttribute.getEditability())) {
                String oldEditabilityName = oldAttribute.getEditability() != null ? getEditabilityName(conn, oldAttribute.getEditability()) : null;
                String newEditabilityName = newAttribute.getEditability() != null ? getEditabilityName(conn, newAttribute.getEditability()) : null;
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Editability", oldEditabilityName, newEditabilityName, userName);
            }
            
            // Editability Role (store name for readability)
            if (!isEqual(oldAttribute.getEditabilityRole(), newAttribute.getEditabilityRole())) {
                String oldRoleName = oldAttribute.getEditabilityRole() != null ? getEditRoleName(conn, oldAttribute.getEditabilityRole()) : null;
                String newRoleName = newAttribute.getEditabilityRole() != null ? getEditRoleName(conn, newAttribute.getEditabilityRole()) : null;
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Editability Role", oldRoleName, newRoleName, userName);
            }
            
            // Data Type (store name for readability)
            if (!isEqual(oldAttribute.getDataTypeId(), newAttribute.getDataTypeId())) {
                String oldTypeName = oldAttribute.getDataTypeId() != null ? getDataTypeName(conn, oldAttribute.getDataTypeId()) : null;
                String newTypeName = newAttribute.getDataTypeId() != null ? getDataTypeName(conn, newAttribute.getDataTypeId()) : null;
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Data Type", oldTypeName, newTypeName, userName);
            }
            
            // Data Length
            if (!isEqual(oldAttribute.getDataLength(), newAttribute.getDataLength())) {
                String oldLength = oldAttribute.getDataLength() != null ? oldAttribute.getDataLength().toString() : null;
                String newLength = newAttribute.getDataLength() != null ? newAttribute.getDataLength().toString() : null;
                createUpdateAuditRecord(conn, auditStmt, attributeId, "Attribute", "Details", 
                    "Updated", "Data Length", oldLength, newLength, userName);
            }
            
            conn.commit();
            //system.out.println("✅ AttributeDAO.createAttributeUpdateAuditRecords - COMMITTED successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ AttributeDAO.createAttributeUpdateAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (auditStmt != null) auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Helper method to create audit record for update with from and to values
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, 
            int attributeId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, attributeId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, fromValue);      // from
        auditStmt.setString(7, toValue);        // to
        auditStmt.setString(8, userName);
        
        auditStmt.executeUpdate();
        //system.out.println("    ✓ Update audit record inserted");
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Method to create snapshot in attribute_audit for update
     * Rev is auto-increment in database
     */
    private void createAttributeUpdateAuditSnapshot(int attributeId) throws SQLException {
        //system.out.println("🔍 AttributeDAO.createAttributeUpdateAuditSnapshot - Creating update snapshot for ID: " + attributeId);
        
        // Use REPLACE INTO to handle duplicate key conflicts (update if exists, insert if not)
        String sql = """
            REPLACE INTO attribute_audit (
                ID, Data_type_ID, Requirement_ID, Dataset_ID, Glossary_ID, Origination,
                Editability, Editability_role, RefNumber, PrimaryName, Definition,
                Is_Mandatory, Is_PrimaryKey, Rank, Business_Logic,
                CreatedDatetime, Last_UpdateDatetime, DeletedDatetime,
                Confidence_score, DataLength, CreatedBy, Last_UpdatedUser_ID, RevType
            )
            SELECT 
                ID, Data_type_ID, Requirement_ID, Dataset_ID, Glossary_ID, Origination,
                Editability, Editability_role, RefNumber, PrimaryName, Definition,
                Is_Mandatory, Is_PrimaryKey, Rank, Business_Logic,
                CreatedDatetime, Last_UpdateDatetime, DeletedDatetime,
                Confidence_score, DataLength, CreatedBy, Last_UpdatedUser_ID, 'Updated'
            FROM attribute 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attributeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Helper method to normalize values (convert null, empty string, and 0 to null)
     */
    private String normalizeValue(Object value) {
        if (value == null) return null;
        
        // Handle Integer with value 0 (consider as null in foreign key context)
        if (value instanceof Integer && ((Integer) value) == 0) {
            return null;
        }
        
        String strValue = value.toString().trim();
        // Convert empty string, "null" string, and "0" string to null
        if (strValue.isEmpty() || strValue.equalsIgnoreCase("null") || strValue.equals("0")) {
            return null;
        }
        return strValue;
    }

    /**
     * Helper method to compare values (handles null, empty strings, and 0)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        // Normalize values first
        String normalized1 = normalizeValue(obj1);
        String normalized2 = normalizeValue(obj2);
        
        // Compare after normalization
        if (normalized1 == null && normalized2 == null) return true;
        if (normalized1 == null || normalized2 == null) return false;
        return normalized1.equals(normalized2);
    }

    // ==================== LOOKUP HELPER METHODS ====================

    /**
     * Get requirement name by ID (with existing connection)
     */
    private String getRequirementName(Connection conn, int requirementId) throws SQLException {
        String sql = "SELECT PrimaryName FROM requirement WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, requirementId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    /**
     * Get glossary name by ID (with existing connection)
     */
    private String getGlossaryName(Connection conn, int glossaryId) throws SQLException {
        String sql = "SELECT Name FROM glossary WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    /**
     * Get origination name by ID (with existing connection)
     */
    private String getOriginationName(Connection conn, int originationId) throws SQLException {
        String sql = "SELECT PrimaryName FROM attribute_origination WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, originationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    /**
     * Get editability name by ID (with existing connection)
     */
    private String getEditabilityName(Connection conn, int editabilityId) throws SQLException {
        String sql = "SELECT PrimaryName FROM attribute_editability WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, editabilityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    /**
     * Get edit role name by ID (with existing connection)
     */
    private String getEditRoleName(Connection conn, int roleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM attribute_edit_role WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    /**
     * Get data type name by ID (with existing connection)
     */
    private String getDataTypeName(Connection conn, int dataTypeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM attribute_datatype WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dataTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }
}


