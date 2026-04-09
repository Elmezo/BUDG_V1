package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Isolated DAO for Glossary Data Tab operations.
 * Datasets: directly linked to this glossary only (dataset.glossary = glossaryId); attribute-linked datasets are not included.
 * Attributes: linked to this glossary only (no child-glossary expansion).
 */
public class GlossaryDataDAO {

    /**
     * Get all child glossary IDs in the hierarchy (recursively)
     */
    private Set<Integer> getAllChildGlossaryIds(int glossaryId) throws SQLException {
        Set<Integer> childIds = new HashSet<>();
        
        String sql = """
            WITH RECURSIVE descendants AS (
                SELECT g.ID
                FROM glossary g
                WHERE g.Parent_ID = ? AND g.Deleted_datetime IS NULL
                UNION ALL
                SELECT g.ID
                FROM glossary g
                INNER JOIN descendants d ON g.Parent_ID = d.ID
                WHERE d.ID IS NOT NULL AND g.Deleted_datetime IS NULL
            )
            SELECT ID FROM descendants
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, glossaryId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    childIds.add(rs.getInt("ID"));
                }
            }
        }
        
        return childIds;
    }

    /**
     * Get child glossary ID -> (name, typeName) for display. Returns only IDs that exist.
     */
    private Map<Integer, String[]> getChildGlossaryNamesAndTypes(Set<Integer> childIds) throws SQLException {
        Map<Integer, String[]> out = new HashMap<>();
        if (childIds == null || childIds.isEmpty()) return out;
        List<Integer> idList = new ArrayList<>(childIds);
        String placeholders = String.join(",", java.util.Collections.nCopies(idList.size(), "?"));
        String sql = "SELECT g.ID, g.Name, gt.Name AS typeName FROM glossary g " +
            "LEFT JOIN glossary_type gt ON g.Type = gt.id WHERE g.ID IN (" + placeholders + ") AND g.Deleted_datetime IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < idList.size(); i++) {
                ps.setInt(i + 1, idList.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("ID");
                    String name = rs.getString("Name");
                    String typeName = rs.getString("typeName");
                    out.put(id, new String[] { name != null ? name : "", typeName != null ? typeName : "" });
                }
            }
        }
        return out;
    }

    /**
     * Get datasets directly linked to one glossary (dataset.glossary = glossaryId).
     * Single-glossary only; no child expansion or child fields.
     */
    private List<Map<String, Object>> getDatasetsForOneGlossary(int glossaryId) throws SQLException {
        String sql = """
            SELECT 
                d.ID,
                d.RefNumber,
                d.PrimaryName,
                d.definition,
                d.MasterSource,
                s.Name AS systemName
            FROM dataset d
            LEFT JOIN system s ON s.id = d.MasterSource
            WHERE d.DeletedDatetime IS NULL
              AND d.glossary = ?
            ORDER BY d.PrimaryName
        """;

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, glossaryId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("ID"));
                    row.put("refNumber", rs.getString("RefNumber"));
                    row.put("primaryName", rs.getString("PrimaryName"));
                    row.put("definition", rs.getString("definition"));
                    row.put("masterSource", rs.getObject("MasterSource"));
                    row.put("systemId", rs.getObject("MasterSource")); // Also add as systemId for frontend
                    row.put("systemName", rs.getString("systemName"));
                    results.add(row);
                }
            }
        }
        return results;
    }

    /**
     * Get datasets directly linked to this glossary (dataset.glossary = glossaryId).
     * No child-glossary expansion.
     */
    public List<Map<String, Object>> getDatasetsByGlossaryId(int glossaryId) throws SQLException {
        return getDatasetsByGlossaryId(glossaryId, false);
    }

    /**
     * Get datasets for this glossary (and optionally child glossaries when includeRollup is true).
     * Only datasets directly linked (dataset.glossary = glossaryId or child ID) are included.
     * When includeRollup is true: also returns datasets directly linked to child glossaries,
     * with isChildGlossary, childGlossaryId, childGlossaryName, childGlossaryType set; deduped by dataset ID
     * (preferring the opened glossary row when a dataset appears for both opened and a child).
     */
    public List<Map<String, Object>> getDatasetsByGlossaryId(int glossaryId, boolean includeRollup) throws SQLException {
        if (!includeRollup) {
            return getDatasetsForOneGlossary(glossaryId);
        }
        // Opened glossary rows first (no child fields)
        List<Map<String, Object>> forOpened = getDatasetsForOneGlossary(glossaryId);
        Set<Integer> seenDatasetIds = new HashSet<>();
        for (Map<String, Object> row : forOpened) {
            Object idObj = row.get("id");
            if (idObj != null) seenDatasetIds.add(idObj instanceof Integer ? (Integer) idObj : ((Number) idObj).intValue());
        }
        List<Map<String, Object>> merged = new ArrayList<>(forOpened);
        Set<Integer> childIds = getAllChildGlossaryIds(glossaryId);
        Map<Integer, String[]> childInfo = getChildGlossaryNamesAndTypes(childIds);
        for (Integer childId : childIds) {
            String[] info = childInfo.get(childId);
            String childName = info != null ? info[0] : "";
            String childTypeName = info != null ? info[1] : "";
            List<Map<String, Object>> forChild = getDatasetsForOneGlossary(childId);
            for (Map<String, Object> row : forChild) {
                Object idObj = row.get("id");
                if (idObj == null) continue;
                int datasetId = idObj instanceof Integer ? (Integer) idObj : ((Number) idObj).intValue();
                if (seenDatasetIds.contains(datasetId)) continue; // prefer opened glossary row
                seenDatasetIds.add(datasetId);
                row.put("isChildGlossary", true);
                row.put("childGlossaryId", childId);
                row.put("childGlossaryName", childName);
                row.put("childGlossaryType", childTypeName);
                merged.add(row);
            }
        }
        return merged;
    }

    /**
     * Get attributes linked to one glossary only (a.Glossary_ID = glossaryId).
     * Single-glossary only; no child expansion or child fields.
     */
    private List<Map<String, Object>> getAttributesForOneGlossary(int glossaryId) throws SQLException {
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
                ga.Type AS attributeGlossaryTypeId,
                
                -- Glossary Type for child glossary
                gct.Name AS glossaryTypeName,
                
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
                
                -- Related To (target attributes)
                (SELECT GROUP_CONCAT(
                    CONCAT(at.PrimaryName, ' (', dt.PrimaryName, ')')
                    SEPARATOR ', '
                 )
                 FROM attribute_x_attribute axa
                 JOIN attribute at ON at.ID = axa.Target_AttributeID
                 JOIN dataset dt ON dt.ID = at.Dataset_ID
                 WHERE axa.Source_AttributeID = a.ID
                ) AS relatedTo,
                
                -- Relationship Type
                (SELECT GROUP_CONCAT(axr.PrimaryName SEPARATOR ', ')
                 FROM attribute_x_attribute axa
                 JOIN attribute_x_attribute_relationtype axr ON axr.ID = axa.Relation_Type
                 WHERE axa.Source_AttributeID = a.ID
                ) AS relationshipType,
                
                -- Interface
                (SELECT GROUP_CONCAT(i.Name SEPARATOR ', ')
                 FROM attribute_x_attribute axa
                 LEFT JOIN interface i ON i.id = axa.Relation_Method
                 WHERE axa.Source_AttributeID = a.ID AND i.id IS NOT NULL
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
            LEFT JOIN glossary_type gct ON gct.ID = ga.Type
            LEFT JOIN glossary_kde_type gkde ON gkde.ID = ga.KDE
            LEFT JOIN attribute_editability ae ON ae.ID = a.Editability
            LEFT JOIN attribute_edit_role aer ON aer.ID = a.Editability_role
            LEFT JOIN attribute_origination ao ON ao.ID = a.Origination
            LEFT JOIN requirement r ON r.ID = a.Requirement_ID
            LEFT JOIN attribute_datatype adt ON adt.ID = a.Data_type_ID
            WHERE a.Glossary_ID = ? AND (a.DeletedDatetime IS NULL)
            ORDER BY ga.ID, a.PrimaryName
        """;

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, glossaryId);

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
                    
                    // Child Glossary info (not set for single glossary query)
                    row.put("childGlossaryName", null);
                    row.put("childGlossaryType", null);
                    row.put("isChildGlossary", false);
                    
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
                    // Physical Fields: attribute_x_physicalfield link; placeholder until schema confirmed
                    row.put("physicalFields", null);
                    
                    results.add(row);
                }
            }
        }
        return results;
    }

    /**
     * Get attributes linked to this glossary only (no child-glossary expansion).
     * Includes all required columns as specified in requirements.
     */
    public List<Map<String, Object>> getAttributesByGlossaryId(int glossaryId) throws SQLException {
        return getAttributesByGlossaryId(glossaryId, false);
    }

    /**
     * Get attributes for this glossary (and optionally child glossaries when includeRollup is true).
     * When includeRollup is true: also returns attributes linked to child glossaries,
     * with isChildGlossary, childGlossaryId, childGlossaryName, childGlossaryType set.
     * Note: Attributes are identified by their Glossary_ID, so we check if the attribute's glossary is a child.
     */
    public List<Map<String, Object>> getAttributesByGlossaryId(int glossaryId, boolean includeRollup) throws SQLException {
        if (!includeRollup) {
            return getAttributesForOneGlossary(glossaryId);
        }
        // Opened glossary rows first (no child fields)
        List<Map<String, Object>> forOpened = getAttributesForOneGlossary(glossaryId);
        List<Map<String, Object>> merged = new ArrayList<>(forOpened);
        Set<Integer> childIds = getAllChildGlossaryIds(glossaryId);
        Map<Integer, String[]> childInfo = getChildGlossaryNamesAndTypes(childIds);
        for (Integer childId : childIds) {
            String[] info = childInfo.get(childId);
            String childName = info != null ? info[0] : "";
            String childTypeName = info != null ? info[1] : "";
            List<Map<String, Object>> forChild = getAttributesForOneGlossary(childId);
            for (Map<String, Object> row : forChild) {
                row.put("isChildGlossary", true);
                row.put("childGlossaryId", childId);
                row.put("childGlossaryName", childName);
                row.put("childGlossaryType", childTypeName);
                merged.add(row);
            }
        }
        return merged;
    }
}

