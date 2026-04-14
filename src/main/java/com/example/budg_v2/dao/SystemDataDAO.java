package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Isolated DAO for System Data Tab
 * Handles datasets and attributes related to a system
 */
public class SystemDataDAO {

    /**
     * Get datasets belonging to a system
     * Includes status from status table
     */
    public List<Map<String, Object>> getDatasetsBySystemId(int systemId) throws SQLException {
        String sql = """
            SELECT 
                d.ID,
                d.RefNumber,
                d.PrimaryName,
                d.definition,
                d.MasterSource,
                s.Name AS systemName,
                d.status,
                st.primaryname AS statusName,
                dt.PrimaryName AS typeName,
                d.lifecycle AS lifecycleId,
                dlc.PrimaryName AS lifecycleName
            FROM dataset d
            LEFT JOIN system s ON s.id = d.MasterSource
            LEFT JOIN status st ON st.ID = d.status
            LEFT JOIN dataset_type dt ON dt.ID = d.DatasetType
            LEFT JOIN dataset_lifecycle dlc ON dlc.ID = d.lifecycle
            WHERE d.MasterSource = ? AND d.DeletedDatetime IS NULL
            ORDER BY d.PrimaryName
        """;

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, systemId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("refNumber", rs.getString("RefNumber"));
                    row.put("primaryName", rs.getString("PrimaryName"));
                    row.put("definition", rs.getString("definition"));
                    row.put("masterSource", rs.getObject("MasterSource"));
                    row.put("systemId", rs.getObject("MasterSource"));
                    row.put("systemName", rs.getString("systemName"));
                    row.put("status", rs.getObject("status"));
                    row.put("statusName", rs.getString("statusName"));
                    row.put("typeName", rs.getString("typeName"));
                    row.put("lifecycleId", rs.getObject("lifecycleId"));
                    row.put("lifecycleName", rs.getString("lifecycleName"));
                    results.add(row);
                }
            }
        }
        return results;
    }

    /**
     * Get attributes of all datasets in a system
     * Includes dataset, glossary, attribute, and attribute_origination
     * Related To and Relationship Type only show when attribute is TARGET (inbound relationship)
     */
    public List<Map<String, Object>> getAttributesBySystemId(int systemId) throws SQLException {
        String sql = """
            SELECT 
                a.ID AS attributeId,
                a.PrimaryName AS attributeName,
                a.RefNumber AS attributeRef,
                a.Definition AS attributeDefinition,
                a.Is_PrimaryKey AS isPrimary,
                a.Rank AS attributeRank,
                a.DataLength AS dataLength,
                a.Confidence_score AS confidenceScore,
                a.Requirement_ID AS requirementId,
                a.Business_Logic AS businessLogic,
                a.Data_type_ID AS dataTypeId,
                
                -- System
                s.Name AS systemName,
                s.id AS systemId,
                
                -- Dataset
                d.PrimaryName AS datasetName,
                d.ID AS datasetId,
                
                -- Glossary of the dataset
                gd.Name AS datasetGlossaryName,
                gd.ID AS datasetGlossaryId,
                
                -- Attribute Glossary
                ga.Name AS attributeGlossaryName,
                ga.ID AS attributeGlossaryId,
                ga.Description AS attributeGlossaryDefinition,
                
                -- Editability
                ae.PrimaryName AS editability,
                
                -- Editability Role
                aer.PrimaryName AS editabilityRole,
                
                -- Origin
                ao.PrimaryName AS origin,
                
                -- KDE
                gkde.Name AS kde,
                
                -- Requirement
                r.PrimaryName AS requirement,
                
                -- Data Type
                adt.PrimaryName AS dataType,
                
                -- DB Field Name (glossary_alias_name)
                (SELECT aan.Name 
                 FROM attribute_alias_name aan 
                 WHERE aan.AttributeID = a.ID AND aan.Name_Type = 1 
                 LIMIT 1) AS dbFieldName,
                
                -- Related To (only when this attribute is TARGET - inbound relationship)
                -- Format: "dataset name_ Attribute Name"
                (SELECT GROUP_CONCAT(
                    CONCAT(dt.PrimaryName, '_', at.PrimaryName)
                    SEPARATOR ', '
                 )
                 FROM attribute_x_attribute axa
                 JOIN attribute at ON at.ID = axa.Source_AttributeID
                 JOIN dataset dt ON dt.ID = at.Dataset_ID
                 WHERE axa.Target_AttributeID = a.ID
                ) AS relatedTo,
                
                -- Relationship Type (only when this attribute is TARGET - inbound relationship)
                (SELECT GROUP_CONCAT(axr.PrimaryName SEPARATOR ', ')
                 FROM attribute_x_attribute axa
                 JOIN attribute_x_attribute_relationtype axr ON axr.ID = axa.Relation_Type
                 WHERE axa.Target_AttributeID = a.ID
                ) AS relationshipType,
                
                -- Interface (only when this attribute is TARGET - inbound relationship)
                (SELECT GROUP_CONCAT(i.Name SEPARATOR ', ')
                 FROM attribute_x_attribute axa
                 LEFT JOIN interface i ON i.id = axa.Relation_Method
                 WHERE axa.Target_AttributeID = a.ID AND i.id IS NOT NULL
                ) AS interfaceName,
                
                -- Review Status (when this attribute is TARGET - inbound relationship)
                (SELECT GROUP_CONCAT(axa.Review_Status SEPARATOR ', ')
                 FROM attribute_x_attribute axa
                 WHERE axa.Target_AttributeID = a.ID AND axa.Review_Status IS NOT NULL AND axa.Review_Status != ''
                ) AS reviewStatus,
                
                -- Sourcing Logic (when this attribute is TARGET - inbound relationship)
                (SELECT GROUP_CONCAT(axa.Sourcing_Logic SEPARATOR ', ')
                 FROM attribute_x_attribute axa
                 WHERE axa.Target_AttributeID = a.ID AND axa.Sourcing_Logic IS NOT NULL AND axa.Sourcing_Logic != ''
                ) AS sourcingLogic
                
            FROM attribute a
            LEFT JOIN dataset d ON d.ID = a.Dataset_ID
            LEFT JOIN system s ON s.id = d.MasterSource
            LEFT JOIN glossary gd ON gd.ID = d.glossary
            LEFT JOIN glossary ga ON ga.ID = a.Glossary_ID
            LEFT JOIN glossary_kde_type gkde ON gkde.ID = ga.KDE
            LEFT JOIN attribute_editability ae ON ae.ID = a.Editability
            LEFT JOIN attribute_edit_role aer ON aer.ID = a.Editability_role
            LEFT JOIN attribute_origination ao ON ao.ID = a.Origination
            LEFT JOIN requirement r ON r.ID = a.Requirement_ID
            LEFT JOIN attribute_datatype adt ON adt.ID = a.Data_type_ID
            WHERE d.MasterSource = ? AND d.DeletedDatetime IS NULL
            ORDER BY d.PrimaryName, a.PrimaryName
        """;

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, systemId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    
                    // Basic attribute info
                    row.put("attributeId", rs.getInt("attributeId"));
                    row.put("attributeName", rs.getString("attributeName"));
                    row.put("attributeRef", rs.getString("attributeRef"));
                    row.put("attributeDefinition", rs.getString("attributeDefinition"));
                    row.put("isPrimary", rs.getObject("isPrimary"));
                    row.put("attributeRank", rs.getObject("attributeRank"));
                    row.put("dataLength", rs.getObject("dataLength"));
                    row.put("confidenceScore", rs.getObject("confidenceScore"));
                    row.put("businessLogic", rs.getString("businessLogic"));
                    
                    // System
                    row.put("systemId", rs.getObject("systemId"));
                    row.put("systemName", rs.getString("systemName"));
                    
                    // Dataset
                    row.put("datasetId", rs.getObject("datasetId"));
                    row.put("datasetName", rs.getString("datasetName"));
                    
                    // Glossary of dataset
                    row.put("datasetGlossaryId", rs.getObject("datasetGlossaryId"));
                    row.put("datasetGlossaryName", rs.getString("datasetGlossaryName"));
                    
                    // Attribute Glossary
                    row.put("attributeGlossaryId", rs.getObject("attributeGlossaryId"));
                    row.put("attributeGlossaryName", rs.getString("attributeGlossaryName"));
                    row.put("attributeGlossaryDefinition", rs.getString("attributeGlossaryDefinition"));
                    
                    // Editability
                    row.put("editability", rs.getString("editability"));
                    row.put("editabilityRole", rs.getString("editabilityRole"));
                    
                    // Other fields
                    row.put("origin", rs.getString("origin"));
                    row.put("kde", rs.getString("kde"));
                    row.put("requirement", rs.getString("requirement"));
                    row.put("dataType", rs.getString("dataType"));
                    row.put("dbFieldName", rs.getString("dbFieldName"));
                    row.put("relatedTo", rs.getString("relatedTo"));
                    row.put("relationshipType", rs.getString("relationshipType"));
                    row.put("interfaceName", rs.getString("interfaceName"));
                    row.put("reviewStatus", rs.getString("reviewStatus"));
                    row.put("sourcingLogic", rs.getString("sourcingLogic"));
                    
                    results.add(row);
                }
            }
        }
        return results;
    }
}

