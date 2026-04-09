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

public class ProjectDataDAO {
    
    /**
     * Get datasets for a project from project_X_dataset table
     * Also includes datasets from:
     * - attributes linked via project_X_attribute (even if not in project_X_dataset)
     * - systems linked via project_x_system (datasets where MasterSource = linked system)
     * Includes joins to get glossary, ref, name, definition, and system
     */
    public List<Map<String, Object>> getProjectDatasets(int projectId) throws SQLException {
        // First, get datasets explicitly linked via project_X_dataset
        String sqlExplicit = """
            SELECT 
                pxd.id,
                d.ID as datasetId,
                d.RefNumber as datasetRefNumber,
                d.PrimaryName as datasetName,
                d.definition as datasetDefinition,
                g.Name as glossaryName,
                g.ID as glossaryId,
                s.name as systemName,
                s.ID as systemId
            FROM project_x_dataset pxd
            LEFT JOIN dataset d ON pxd.dataset_id = d.ID
            LEFT JOIN glossary g ON d.glossary = g.ID
            LEFT JOIN system s ON d.MasterSource = s.ID
            WHERE pxd.projectid = ?
        """;
        
        // Second, get datasets from attributes linked via project_X_attribute (but not in project_X_dataset)
        String sqlFromAttributes = """
            SELECT 
                pxa.id * 1000000 + d.ID as id,
                d.ID as datasetId,
                d.RefNumber as datasetRefNumber,
                d.PrimaryName as datasetName,
                d.definition as datasetDefinition,
                g.Name as glossaryName,
                g.ID as glossaryId,
                s.name as systemName,
                s.ID as systemId
            FROM project_x_attribute pxa
            LEFT JOIN attribute a ON pxa.attribute_id = a.ID
            LEFT JOIN dataset d ON a.Dataset_ID = d.ID
            LEFT JOIN glossary g ON d.glossary = g.ID
            LEFT JOIN system s ON d.MasterSource = s.ID
            WHERE pxa.projectid = ?
              AND d.ID IS NOT NULL
              AND d.ID NOT IN (
                  SELECT COALESCE(dataset_id, 0) 
                  FROM project_x_dataset 
                  WHERE projectid = pxa.projectid AND dataset_id IS NOT NULL
              )
        """;

        // Third, get datasets from systems linked via project_x_system (but not already included)
        String sqlFromSystems = """
            SELECT
                pxs.id * 1000000 + d.ID as id,
                d.ID as datasetId,
                d.RefNumber as datasetRefNumber,
                d.PrimaryName as datasetName,
                d.definition as datasetDefinition,
                g.Name as glossaryName,
                g.ID as glossaryId,
                s.name as systemName,
                s.ID as systemId
            FROM project_x_system pxs
            LEFT JOIN dataset d ON d.MasterSource = pxs.systemid
            LEFT JOIN glossary g ON d.glossary = g.ID
            LEFT JOIN system s ON d.MasterSource = s.ID
            WHERE pxs.projectid = ?
              AND d.ID IS NOT NULL
        """;
        
        List<Map<String, Object>> datasets = new ArrayList<>();
        java.util.Set<Integer> seenDatasetIds = new java.util.HashSet<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get explicitly linked datasets
            try (PreparedStatement ps = conn.prepareStatement(sqlExplicit)) {
                ps.setInt(1, projectId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int datasetId = rs.getInt("datasetId");
                        if (!rs.wasNull() && datasetId > 0) {
                            Map<String, Object> dataset = new HashMap<>();
                            dataset.put("id", rs.getInt("id"));
                            dataset.put("datasetId", datasetId);
                            dataset.put("datasetRefNumber", rs.getString("datasetRefNumber"));
                            dataset.put("datasetName", rs.getString("datasetName"));
                            dataset.put("datasetDefinition", rs.getString("datasetDefinition"));
                            dataset.put("glossaryName", rs.getString("glossaryName"));
                            int glossaryId = rs.getInt("glossaryId");
                            dataset.put("glossaryId", rs.wasNull() ? null : glossaryId);
                            dataset.put("systemName", rs.getString("systemName"));
                            int systemId = rs.getInt("systemId");
                            dataset.put("systemId", rs.wasNull() ? null : systemId);
                            
                            datasets.add(dataset);
                            seenDatasetIds.add(datasetId);
                        }
                    }
                }
            }
            
            // Get datasets from linked attributes
            try (PreparedStatement ps = conn.prepareStatement(sqlFromAttributes)) {
                ps.setInt(1, projectId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int datasetId = rs.getInt("datasetId");
                        if (!rs.wasNull() && datasetId > 0 && !seenDatasetIds.contains(datasetId)) {
                            Map<String, Object> dataset = new HashMap<>();
                            dataset.put("id", rs.getLong("id"));
                            dataset.put("datasetId", datasetId);
                            dataset.put("datasetRefNumber", rs.getString("datasetRefNumber"));
                            dataset.put("datasetName", rs.getString("datasetName"));
                            dataset.put("datasetDefinition", rs.getString("datasetDefinition"));
                            dataset.put("glossaryName", rs.getString("glossaryName"));
                            int glossaryId = rs.getInt("glossaryId");
                            dataset.put("glossaryId", rs.wasNull() ? null : glossaryId);
                            dataset.put("systemName", rs.getString("systemName"));
                            int systemId = rs.getInt("systemId");
                            dataset.put("systemId", rs.wasNull() ? null : systemId);
                            
                            datasets.add(dataset);
                            seenDatasetIds.add(datasetId);
                        }
                    }
                }
            }

            // Get datasets from linked systems
            try (PreparedStatement ps = conn.prepareStatement(sqlFromSystems)) {
                ps.setInt(1, projectId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int datasetId = rs.getInt("datasetId");
                        if (!rs.wasNull() && datasetId > 0 && !seenDatasetIds.contains(datasetId)) {
                            Map<String, Object> dataset = new HashMap<>();
                            dataset.put("id", rs.getLong("id"));
                            dataset.put("datasetId", datasetId);
                            dataset.put("datasetRefNumber", rs.getString("datasetRefNumber"));
                            dataset.put("datasetName", rs.getString("datasetName"));
                            dataset.put("datasetDefinition", rs.getString("datasetDefinition"));
                            dataset.put("glossaryName", rs.getString("glossaryName"));
                            int glossaryId = rs.getInt("glossaryId");
                            dataset.put("glossaryId", rs.wasNull() ? null : glossaryId);
                            dataset.put("systemName", rs.getString("systemName"));
                            int systemId = rs.getInt("systemId");
                            dataset.put("systemId", rs.wasNull() ? null : systemId);

                            datasets.add(dataset);
                            seenDatasetIds.add(datasetId);
                        }
                    }
                }
            }
        }
        
        // Sort by RefNumber, then PrimaryName
        datasets.sort((a, b) -> {
            String refA = (String) a.get("datasetRefNumber");
            String refB = (String) b.get("datasetRefNumber");
            if (refA != null && refB != null) {
                int refCompare = refA.compareTo(refB);
                if (refCompare != 0) return refCompare;
            }
            String nameA = (String) a.get("datasetName");
            String nameB = (String) b.get("datasetName");
            if (nameA != null && nameB != null) {
                return nameA.compareTo(nameB);
            }
            return 0;
        });
        
        return datasets;
    }
    
    /**
     * Get attributes for a project from project_X_attribute table
     * Also includes attributes from:
     * - datasets linked via project_X_dataset (even if not in project_X_attribute)
     * - datasets inherited from systems linked via project_x_system
     */
    public List<Map<String, Object>> getProjectAttributes(int projectId) throws SQLException {
        // First, get attributes explicitly linked via project_X_attribute
        String sqlExplicit = """
            SELECT 
                pxa.id as id,
                a.ID as attributeId,
                a.RefNumber as attributeRefNumber,
                a.PrimaryName as attributeName,
                a.Definition as attributeDefinition,
                g.Name as glossaryName,
                g.ID as glossaryId,
                d.PrimaryName as datasetName,
                d.ID as datasetId,
                s.name as systemName,
                s.ID as systemId
            FROM project_x_attribute pxa
            LEFT JOIN attribute a ON pxa.attribute_id = a.ID
            LEFT JOIN dataset d ON a.Dataset_ID = d.ID
            LEFT JOIN glossary g ON a.Glossary_ID = g.ID
            LEFT JOIN system s ON d.MasterSource = s.ID
            WHERE pxa.projectid = ?
        """;
        
        // Second, get attributes from datasets linked via project_X_dataset (but not in project_X_attribute)
        String sqlFromDatasets = """
            SELECT 
                pxd.id * 1000000 + a.ID as id,
                a.ID as attributeId,
                a.RefNumber as attributeRefNumber,
                a.PrimaryName as attributeName,
                a.Definition as attributeDefinition,
                g.Name as glossaryName,
                g.ID as glossaryId,
                d.PrimaryName as datasetName,
                d.ID as datasetId,
                s.name as systemName,
                s.ID as systemId
            FROM project_x_dataset pxd
            LEFT JOIN dataset d ON pxd.dataset_id = d.ID
            LEFT JOIN attribute a ON a.Dataset_ID = d.ID
            LEFT JOIN glossary g ON a.Glossary_ID = g.ID
            LEFT JOIN system s ON d.MasterSource = s.ID
            WHERE pxd.projectid = ?
              AND a.ID IS NOT NULL
              AND a.ID NOT IN (
                  SELECT COALESCE(attribute_id, 0) 
                  FROM project_x_attribute 
                  WHERE projectid = pxd.projectid AND attribute_id IS NOT NULL
              )
        """;

        // Third, get attributes from datasets inherited via linked systems.
        // Exclude attributes already explicitly linked in project_x_attribute.
        String sqlFromSystems = """
            SELECT
                pxs.id * 1000000 + a.ID as id,
                a.ID as attributeId,
                a.RefNumber as attributeRefNumber,
                a.PrimaryName as attributeName,
                a.Definition as attributeDefinition,
                g.Name as glossaryName,
                g.ID as glossaryId,
                d.PrimaryName as datasetName,
                d.ID as datasetId,
                s.name as systemName,
                s.ID as systemId
            FROM project_x_system pxs
            LEFT JOIN dataset d ON d.MasterSource = pxs.systemid
            LEFT JOIN attribute a ON a.Dataset_ID = d.ID
            LEFT JOIN glossary g ON a.Glossary_ID = g.ID
            LEFT JOIN system s ON d.MasterSource = s.ID
            WHERE pxs.projectid = ?
              AND a.ID IS NOT NULL
              AND a.ID NOT IN (
                  SELECT COALESCE(attribute_id, 0)
                  FROM project_x_attribute
                  WHERE projectid = ? AND attribute_id IS NOT NULL
              )
        """;
        
        List<Map<String, Object>> attributes = new ArrayList<>();
        java.util.Set<Integer> seenAttributeIds = new java.util.HashSet<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get explicitly linked attributes
            try (PreparedStatement ps = conn.prepareStatement(sqlExplicit)) {
                ps.setInt(1, projectId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> attribute = new HashMap<>();
                        attribute.put("id", rs.getInt("id"));
                        int attributeId = rs.getInt("attributeId");
                        attribute.put("attributeId", rs.wasNull() ? null : attributeId);
                        attribute.put("attributeRefNumber", rs.getString("attributeRefNumber"));
                        attribute.put("attributeName", rs.getString("attributeName"));
                        attribute.put("attributeDefinition", rs.getString("attributeDefinition"));
                        attribute.put("glossaryName", rs.getString("glossaryName"));
                        int glossaryId = rs.getInt("glossaryId");
                        attribute.put("glossaryId", rs.wasNull() ? null : glossaryId);
                        attribute.put("datasetName", rs.getString("datasetName"));
                        int datasetId = rs.getInt("datasetId");
                        attribute.put("datasetId", rs.wasNull() ? null : datasetId);
                        attribute.put("systemName", rs.getString("systemName"));
                        int systemId = rs.getInt("systemId");
                        attribute.put("systemId", rs.wasNull() ? null : systemId);

                        attributes.add(attribute);
                        if (attributeId > 0) {
                            seenAttributeIds.add(attributeId);
                        }
                    }
                }
            }
            
            // Get attributes from linked datasets
            try (PreparedStatement ps = conn.prepareStatement(sqlFromDatasets)) {
                ps.setInt(1, projectId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int attributeId = rs.getInt("attributeId");
                        if (rs.wasNull() || attributeId <= 0 || seenAttributeIds.contains(attributeId)) {
                            continue;
                        }
                        Map<String, Object> attribute = new HashMap<>();
                        attribute.put("id", rs.getLong("id"));
                        attribute.put("attributeId", attributeId);
                        attribute.put("attributeRefNumber", rs.getString("attributeRefNumber"));
                        attribute.put("attributeName", rs.getString("attributeName"));
                        attribute.put("attributeDefinition", rs.getString("attributeDefinition"));
                        attribute.put("glossaryName", rs.getString("glossaryName"));
                        int glossaryId = rs.getInt("glossaryId");
                        attribute.put("glossaryId", rs.wasNull() ? null : glossaryId);
                        attribute.put("datasetName", rs.getString("datasetName"));
                        int datasetId = rs.getInt("datasetId");
                        attribute.put("datasetId", rs.wasNull() ? null : datasetId);
                        attribute.put("systemName", rs.getString("systemName"));
                        int systemId = rs.getInt("systemId");
                        attribute.put("systemId", rs.wasNull() ? null : systemId);

                        attributes.add(attribute);
                        seenAttributeIds.add(attributeId);
                    }
                }
            }

            // Get attributes from datasets inherited via linked systems
            try (PreparedStatement ps = conn.prepareStatement(sqlFromSystems)) {
                ps.setInt(1, projectId);
                ps.setInt(2, projectId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int attributeId = rs.getInt("attributeId");
                        if (rs.wasNull() || attributeId <= 0 || seenAttributeIds.contains(attributeId)) {
                            continue;
                        }
                        Map<String, Object> attribute = new HashMap<>();
                        attribute.put("id", rs.getLong("id"));
                        attribute.put("attributeId", attributeId);
                        attribute.put("attributeRefNumber", rs.getString("attributeRefNumber"));
                        attribute.put("attributeName", rs.getString("attributeName"));
                        attribute.put("attributeDefinition", rs.getString("attributeDefinition"));
                        attribute.put("glossaryName", rs.getString("glossaryName"));
                        int glossaryId = rs.getInt("glossaryId");
                        attribute.put("glossaryId", rs.wasNull() ? null : glossaryId);
                        attribute.put("datasetName", rs.getString("datasetName"));
                        int datasetId = rs.getInt("datasetId");
                        attribute.put("datasetId", rs.wasNull() ? null : datasetId);
                        attribute.put("systemName", rs.getString("systemName"));
                        int systemId = rs.getInt("systemId");
                        attribute.put("systemId", rs.wasNull() ? null : systemId);

                        attributes.add(attribute);
                        seenAttributeIds.add(attributeId);
                    }
                }
            }
        }
        
        return attributes;
    }
}

