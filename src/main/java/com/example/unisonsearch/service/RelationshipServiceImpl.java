package com.example.unisonsearch.service;

import com.example.unisonsearch.repository.DatabaseHelper;
import java.sql.SQLException;
import java.util.*;

/**
 * Implementation of RelationshipService.
 * Provides correct relationship queries based on the new whitelist and special relationships.
 */
public class RelationshipServiceImpl implements RelationshipService {
    
    private final DatabaseHelper databaseHelper;
    
    public RelationshipServiceImpl(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }
    
    // ========== Dataset Relations ==========
    
    @Override
    public Integer getSystemOfDataset(int datasetId) {
        try {
            String sql = "SELECT MasterSource FROM dataset WHERE ID = ? AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(datasetId));
            if (rows.isEmpty()) return null;
            Object value = rows.get(0).get("MasterSource");
            if (value instanceof Integer) return (Integer) value;
            if (value instanceof Number) return ((Number) value).intValue();
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting system of dataset " + datasetId + ": " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public List<Integer> getAttributesByDataset(int datasetId) {
        try {
            String sql = "SELECT ID FROM attribute WHERE Dataset_ID = ? AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(datasetId));
            return extractIds(rows, "ID");
        } catch (SQLException e) {
            System.err.println("Error getting attributes by dataset " + datasetId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getGlossariesByDatasetIncludingAttributes(int datasetId) {
        try {
            Set<Integer> glossaryIds = new HashSet<>();
            
            // Direct glossary on dataset
            String sql1 = "SELECT glossary FROM dataset WHERE ID = ? AND glossary IS NOT NULL AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows1 = databaseHelper.executeQuery(sql1, List.of(datasetId));
            for (Map<String, Object> row : rows1) {
                Object value = row.get("glossary");
                if (value instanceof Integer) glossaryIds.add((Integer) value);
                else if (value instanceof Number) glossaryIds.add(((Number) value).intValue());
            }
            
            // Glossaries via attributes
            String sql2 = "SELECT DISTINCT Glossary_ID FROM attribute WHERE Dataset_ID = ? AND Glossary_ID IS NOT NULL AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows2 = databaseHelper.executeQuery(sql2, List.of(datasetId));
            for (Map<String, Object> row : rows2) {
                Object value = row.get("Glossary_ID");
                if (value instanceof Integer) glossaryIds.add((Integer) value);
                else if (value instanceof Number) glossaryIds.add(((Number) value).intValue());
            }
            
            return new ArrayList<>(glossaryIds);
        } catch (SQLException e) {
            System.err.println("Error getting glossaries by dataset " + datasetId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getInterfacesByDataset(int datasetId) {
        // Special relationship: Dataset → System → Interface
        try {
            Integer systemId = getSystemOfDataset(datasetId);
            if (systemId == null) return new ArrayList<>();
            
            String sql = "SELECT id FROM interface WHERE (Source_systemID = ? OR Target_systemID = ?) AND deleted_datetime IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(systemId, systemId));
            return extractIds(rows, "id");
        } catch (SQLException e) {
            System.err.println("Error getting interfaces by dataset " + datasetId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<Integer> getLegalEntitiesByDataset(int datasetId) {
        try {
            String sql = "SELECT DISTINCT Legal_ID FROM dataset_x_legal WHERE Dataset_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(datasetId));
            return extractIds(rows, "Legal_ID");
        } catch (SQLException e) {
            System.err.println("Error getting legal entities by dataset " + datasetId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<Integer> getClientsByDataset(int datasetId) {
        try {
            String sql = "SELECT DISTINCT Client_ID FROM client_x_dataset WHERE Dataset_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(datasetId));
            return extractIds(rows, "Client_ID");
        } catch (SQLException e) {
            System.err.println("Error getting clients by dataset " + datasetId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== System Relations ==========
    
    @Override
    public List<Integer> getDatasetsBySystem(int systemId) {
        try {
            String sql = "SELECT ID FROM dataset WHERE MasterSource = ? AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(systemId));
            return extractIds(rows, "ID");
        } catch (SQLException e) {
            System.err.println("Error getting datasets by system " + systemId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAllAttributesBySystem(int systemId) {
        // Special relationship with depth = 2: System → Dataset → Attribute
        try {
            String sql = "SELECT a.ID FROM attribute a " +
                        "INNER JOIN dataset d ON a.Dataset_ID = d.ID " +
                        "WHERE d.MasterSource = ? AND a.DeletedDatetime IS NULL AND d.DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(systemId));
            return extractIds(rows, "ID");
        } catch (SQLException e) {
            System.err.println("Error getting attributes by system " + systemId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getGlossariesBySystem(int systemId) {
        try {
            // Get glossaries via datasets
            Set<Integer> glossaryIds = new HashSet<>();
            List<Integer> datasetIds = getDatasetsBySystem(systemId);
            for (Integer datasetId : datasetIds) {
                List<Integer> glossaries = getGlossariesByDatasetIncludingAttributes(datasetId);
                glossaryIds.addAll(glossaries);
            }
            return new ArrayList<>(glossaryIds);
        } catch (Exception e) {
            System.err.println("Error getting glossaries by system " + systemId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getInterfacesBySystem(int systemId) {
        try {
            String sql = "SELECT id FROM interface WHERE (Source_systemID = ? OR Target_systemID = ?) AND deleted_datetime IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(systemId, systemId));
            return extractIds(rows, "id");
        } catch (SQLException e) {
            System.err.println("Error getting interfaces by system " + systemId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Glossary Relations ==========
    
    @Override
    public List<Integer> getDatasetsByGlossary(int glossaryId) {
        try {
            Set<Integer> datasetIds = new HashSet<>();
            
            // Direct glossary on dataset
            String sql1 = "SELECT ID FROM dataset WHERE glossary = ? AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows1 = databaseHelper.executeQuery(sql1, List.of(glossaryId));
            datasetIds.addAll(extractIds(rows1, "ID"));
            
            // Datasets via attributes
            String sql2 = "SELECT DISTINCT Dataset_ID FROM attribute WHERE Glossary_ID = ? AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows2 = databaseHelper.executeQuery(sql2, List.of(glossaryId));
            datasetIds.addAll(extractIds(rows2, "Dataset_ID"));
            
            return new ArrayList<>(datasetIds);
        } catch (SQLException e) {
            System.err.println("Error getting datasets by glossary " + glossaryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAttributesByGlossary(int glossaryId) {
        try {
            String sql = "SELECT ID FROM attribute WHERE Glossary_ID = ? AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(glossaryId));
            return extractIds(rows, "ID");
        } catch (SQLException e) {
            System.err.println("Error getting attributes by glossary " + glossaryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getSystemByGlossary(int glossaryId) {
        // Special relationship, derived chain depth = 3: Glossary → Attribute → Dataset → System
        try {
            Set<Integer> systemIds = new HashSet<>();
            List<Integer> attributeIds = getAttributesByGlossary(glossaryId);
            for (Integer attributeId : attributeIds) {
                Integer datasetId = getDatasetOfAttribute(attributeId);
                if (datasetId != null) {
                    Integer systemId = getSystemOfDataset(datasetId);
                    if (systemId != null) {
                        systemIds.add(systemId);
                    }
                }
            }
            return new ArrayList<>(systemIds);
        } catch (Exception e) {
            System.err.println("Error getting system by glossary " + glossaryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Attribute Relations ==========
    
    @Override
    public Integer getDatasetOfAttribute(int attributeId) {
        try {
            String sql = "SELECT Dataset_ID FROM attribute WHERE ID = ? AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(attributeId));
            if (rows.isEmpty()) return null;
            Object value = rows.get(0).get("Dataset_ID");
            if (value instanceof Integer) return (Integer) value;
            if (value instanceof Number) return ((Number) value).intValue();
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting dataset of attribute " + attributeId + ": " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public Integer getGlossaryOfAttribute(int attributeId) {
        try {
            String sql = "SELECT Glossary_ID FROM attribute WHERE ID = ? AND Glossary_ID IS NOT NULL AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(attributeId));
            if (rows.isEmpty()) return null;
            Object value = rows.get(0).get("Glossary_ID");
            if (value instanceof Integer) return (Integer) value;
            if (value instanceof Number) return ((Number) value).intValue();
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting glossary of attribute " + attributeId + ": " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public Integer getSystemOfAttribute(int attributeId) {
        // Special relationship, parent chain: Attribute → Dataset → System
        Integer datasetId = getDatasetOfAttribute(attributeId);
        if (datasetId == null) return null;
        return getSystemOfDataset(datasetId);
    }
    
    // ========== Interface Relations ==========
    
    @Override
    public List<Integer> getSystemsByInterface(int interfaceId) {
        try {
            String sql = "SELECT DISTINCT Source_systemID, Target_systemID FROM interface WHERE id = ? AND deleted_datetime IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(interfaceId));
            Set<Integer> systemIds = new HashSet<>();
            for (Map<String, Object> row : rows) {
                Object source = row.get("Source_systemID");
                Object target = row.get("Target_systemID");
                if (source instanceof Integer) systemIds.add((Integer) source);
                else if (source instanceof Number) systemIds.add(((Number) source).intValue());
                if (target instanceof Integer) systemIds.add((Integer) target);
                else if (target instanceof Number) systemIds.add(((Number) target).intValue());
            }
            return new ArrayList<>(systemIds);
        } catch (SQLException e) {
            System.err.println("Error getting systems by interface " + interfaceId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Project/Process/Policy/Capability Relations ==========
    
    @Override
    public List<Integer> getGlossariesByProject(int projectId) {
        try {
            String sql = "SELECT Glossary_ID FROM glossary_x_project WHERE Project_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(projectId));
            return extractIds(rows, "Glossary_ID");
        } catch (SQLException e) {
            System.err.println("Error getting glossaries by project " + projectId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getDatasetsByProject(int projectId) {
        // Special relationship
        try {
            String sql = "SELECT dataset_id FROM project_x_dataset WHERE projectid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(projectId));
            return extractIds(rows, "dataset_id");
        } catch (SQLException e) {
            System.err.println("Error getting datasets by project " + projectId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAttributesByProject(int projectId) {
        // Special relationship
        try {
            String sql = "SELECT attribute_id FROM project_x_attribute WHERE projectid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(projectId));
            return extractIds(rows, "attribute_id");
        } catch (SQLException e) {
            System.err.println("Error getting attributes by project " + projectId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getSystemsByProject(int projectId) {
        // Special relationship
        try {
            String sql = "SELECT systemid FROM project_x_system WHERE projectid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(projectId));
            return extractIds(rows, "systemid");
        } catch (SQLException e) {
            System.err.println("Error getting systems by project " + projectId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getGlossariesByProcess(int processId) {
        try {
            String sql = "SELECT Glossary_ID FROM glossary_x_process WHERE Process_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(processId));
            return extractIds(rows, "Glossary_ID");
        } catch (SQLException e) {
            System.err.println("Error getting glossaries by process " + processId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getDatasetsByProcess(int processId) {
        // Special relationship
        try {
            String sql = "SELECT datasetid FROM process_x_dataset WHERE processid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(processId));
            return extractIds(rows, "datasetid");
        } catch (SQLException e) {
            System.err.println("Error getting datasets by process " + processId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAttributesByProcess(int processId) {
        // Special relationship
        try {
            String sql = "SELECT attributeid FROM process_x_attribute WHERE processid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(processId));
            return extractIds(rows, "attributeid");
        } catch (SQLException e) {
            System.err.println("Error getting attributes by process " + processId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getSystemsByProcess(int processId) {
        // Special relationship
        try {
            String sql = "SELECT system_id FROM process_x_system WHERE process_id = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(processId));
            return extractIds(rows, "system_id");
        } catch (SQLException e) {
            System.err.println("Error getting systems by process " + processId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getGlossariesByPolicy(int policyId) {
        try {
            String sql = "SELECT GlossaryID FROM policy_x_glossary WHERE PolicyID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(policyId));
            return extractIds(rows, "GlossaryID");
        } catch (SQLException e) {
            System.err.println("Error getting glossaries by policy " + policyId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getDatasetsByPolicy(int policyId) {
        // Special relationship
        try {
            String sql = "SELECT DatasetID FROM policy_x_dataset WHERE PolicyID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(policyId));
            return extractIds(rows, "DatasetID");
        } catch (SQLException e) {
            System.err.println("Error getting datasets by policy " + policyId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAttributesByPolicy(int policyId) {
        // Special relationship
        try {
            String sql = "SELECT attributeid FROM policy_x_attribute WHERE policyid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(policyId));
            return extractIds(rows, "attributeid");
        } catch (SQLException e) {
            System.err.println("Error getting attributes by policy " + policyId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getSystemsByPolicy(int policyId) {
        // Special relationship
        try {
            String sql = "SELECT System_ID FROM policy_x_system WHERE Policy_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(policyId));
            return extractIds(rows, "System_ID");
        } catch (SQLException e) {
            System.err.println("Error getting systems by policy " + policyId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Reverse / Impact (Dataset/Attribute/System/Glossary → Project/Process/Policy) ==========
    
    @Override
    public List<Integer> getProjectsByDataset(int datasetId) {
        try {
            String sql = "SELECT projectid FROM project_x_dataset WHERE dataset_id = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(datasetId));
            return extractIds(rows, "projectid");
        } catch (SQLException e) {
            System.err.println("Error getting projects by dataset " + datasetId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getProcessesByDataset(int datasetId) {
        try {
            String sql = "SELECT processid FROM process_x_dataset WHERE datasetid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(datasetId));
            return extractIds(rows, "processid");
        } catch (SQLException e) {
            System.err.println("Error getting processes by dataset " + datasetId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getPoliciesByDataset(int datasetId) {
        try {
            String sql = "SELECT PolicyID FROM policy_x_dataset WHERE DatasetID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(datasetId));
            return extractIds(rows, "PolicyID");
        } catch (SQLException e) {
            System.err.println("Error getting policies by dataset " + datasetId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getProjectsByAttribute(int attributeId) {
        try {
            String sql = "SELECT projectid FROM project_x_attribute WHERE attribute_id = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(attributeId));
            return extractIds(rows, "projectid");
        } catch (SQLException e) {
            System.err.println("Error getting projects by attribute " + attributeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getProcessesByAttribute(int attributeId) {
        try {
            String sql = "SELECT processid FROM process_x_attribute WHERE attributeid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(attributeId));
            return extractIds(rows, "processid");
        } catch (SQLException e) {
            System.err.println("Error getting processes by attribute " + attributeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getPoliciesByAttribute(int attributeId) {
        try {
            String sql = "SELECT policyid FROM policy_x_attribute WHERE attributeid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(attributeId));
            return extractIds(rows, "policyid");
        } catch (SQLException e) {
            System.err.println("Error getting policies by attribute " + attributeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getProjectsBySystem(int systemId) {
        try {
            String sql = "SELECT projectid FROM project_x_system WHERE systemid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(systemId));
            return extractIds(rows, "projectid");
        } catch (SQLException e) {
            System.err.println("Error getting projects by system " + systemId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getProcessesBySystem(int systemId) {
        try {
            String sql = "SELECT process_id FROM process_x_system WHERE system_id = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(systemId));
            return extractIds(rows, "process_id");
        } catch (SQLException e) {
            System.err.println("Error getting processes by system " + systemId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getPoliciesBySystem(int systemId) {
        try {
            String sql = "SELECT Policy_ID FROM policy_x_system WHERE System_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(systemId));
            return extractIds(rows, "Policy_ID");
        } catch (SQLException e) {
            System.err.println("Error getting policies by system " + systemId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getProjectsByGlossary(int glossaryId) {
        try {
            String sql = "SELECT Project_ID FROM glossary_x_project WHERE Glossary_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(glossaryId));
            return extractIds(rows, "Project_ID");
        } catch (SQLException e) {
            System.err.println("Error getting projects by glossary " + glossaryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getProcessesByGlossary(int glossaryId) {
        try {
            String sql = "SELECT Process_ID FROM glossary_x_process WHERE Glossary_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(glossaryId));
            return extractIds(rows, "Process_ID");
        } catch (SQLException e) {
            System.err.println("Error getting processes by glossary " + glossaryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getPoliciesByGlossary(int glossaryId) {
        try {
            String sql = "SELECT PolicyID FROM policy_x_glossary WHERE GlossaryID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(glossaryId));
            return extractIds(rows, "PolicyID");
        } catch (SQLException e) {
            System.err.println("Error getting policies by glossary " + glossaryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<Integer> getCapabilitiesByGlossary(int glossaryId) {
        try {
            String sql = "SELECT Capability_ID FROM capability_x_glossary WHERE Glossary_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(glossaryId));
            return extractIds(rows, "Capability_ID");
        } catch (SQLException e) {
            System.err.println("Error getting capabilities by glossary " + glossaryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<Integer> getBusinessAreasByGlossary(int glossaryId) {
        try {
            String sql = "SELECT BusinessArea_ID FROM businessarea_x_glossary WHERE Glossary_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(glossaryId));
            return extractIds(rows, "BusinessArea_ID");
        } catch (SQLException e) {
            System.err.println("Error getting business areas by glossary " + glossaryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<Integer> getLegalEntitiesByGlossary(int glossaryId) {
        // Derived via dataset links: Glossary -> Dataset -> Legal Entity
        try {
            List<Integer> datasetIds = getDatasetsByGlossary(glossaryId);
            if (datasetIds.isEmpty()) {
                return new ArrayList<>();
            }

            String placeholders = String.join(",", Collections.nCopies(datasetIds.size(), "?"));
            String sql = "SELECT DISTINCT Legal_ID FROM dataset_x_legal WHERE Dataset_ID IN (" + placeholders + ")";
            List<Object> params = new ArrayList<>(datasetIds);
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, params);
            return extractIds(rows, "Legal_ID");
        } catch (SQLException e) {
            System.err.println("Error getting legal entities by glossary " + glossaryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<Integer> getClientsByGlossary(int glossaryId) {
        try {
            String sql = "SELECT Client_ID FROM client_x_glossary WHERE Glossary_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(glossaryId));
            return extractIds(rows, "Client_ID");
        } catch (SQLException e) {
            System.err.println("Error getting clients by glossary " + glossaryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<Integer> getProductsByGlossary(int glossaryId) {
        try {
            String sql = "SELECT productid FROM product_x_glossary WHERE glossaryid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(glossaryId));
            return extractIds(rows, "productid");
        } catch (SQLException e) {
            System.err.println("Error getting products by glossary " + glossaryId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getGlossariesByCapability(int capabilityId) {
        try {
            String sql = "SELECT Glossary_ID FROM capability_x_glossary WHERE Capability_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(capabilityId));
            return extractIds(rows, "Glossary_ID");
        } catch (SQLException e) {
            System.err.println("Error getting glossaries by capability " + capabilityId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getDatasetsByCapability(int capabilityId) {
        // Via glossaries: Capability → Glossary → Dataset
        try {
            Set<Integer> datasetIds = new HashSet<>();
            List<Integer> glossaryIds = getGlossariesByCapability(capabilityId);
            for (Integer glossaryId : glossaryIds) {
                List<Integer> datasets = getDatasetsByGlossary(glossaryId);
                datasetIds.addAll(datasets);
            }
            return new ArrayList<>(datasetIds);
        } catch (Exception e) {
            System.err.println("Error getting datasets by capability " + capabilityId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAttributesByCapability(int capabilityId) {
        // Via glossaries: Capability → Glossary → Attribute
        try {
            Set<Integer> attributeIds = new HashSet<>();
            List<Integer> glossaryIds = getGlossariesByCapability(capabilityId);
            for (Integer glossaryId : glossaryIds) {
                List<Integer> attrs = getAttributesByGlossary(glossaryId);
                attributeIds.addAll(attrs);
            }
            return new ArrayList<>(attributeIds);
        } catch (Exception e) {
            System.err.println("Error getting attributes by capability " + capabilityId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getSystemsByCapability(int capabilityId) {
        // Via glossaries: Capability → Glossary → Attribute → Dataset → System
        try {
            Set<Integer> systemIds = new HashSet<>();
            List<Integer> glossaryIds = getGlossariesByCapability(capabilityId);
            for (Integer glossaryId : glossaryIds) {
                List<Integer> systems = getSystemByGlossary(glossaryId);
                systemIds.addAll(systems);
            }
            return new ArrayList<>(systemIds);
        } catch (Exception e) {
            System.err.println("Error getting systems by capability " + capabilityId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Product Relations ==========
    
    @Override
    public List<Integer> getGlossariesByProduct(int productId) {
        try {
            String sql = "SELECT glossaryid FROM product_x_glossary WHERE productid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(productId));
            return extractIds(rows, "glossaryid");
        } catch (SQLException e) {
            System.err.println("Error getting glossaries by product " + productId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getDatasetsByProduct(int productId) {
        try {
            String sql = "SELECT Dataset_ID FROM product_x_dataset WHERE Product_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(productId));
            return extractIds(rows, "Dataset_ID");
        } catch (SQLException e) {
            System.err.println("Error getting datasets by product " + productId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getSystemsByProduct(int productId) {
        try {
            String sql = "SELECT systemid FROM product_x_system WHERE productid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(productId));
            return extractIds(rows, "systemid");
        } catch (SQLException e) {
            System.err.println("Error getting systems by product " + productId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAttributesByProduct(int productId) {
        // Special relationship: Product → Dataset → Attribute
        try {
            Set<Integer> attributeIds = new HashSet<>();
            List<Integer> datasetIds = getDatasetsByProduct(productId);
            for (Integer datasetId : datasetIds) {
                List<Integer> attrs = getAttributesByDataset(datasetId);
                attributeIds.addAll(attrs);
            }
            return new ArrayList<>(attributeIds);
        } catch (Exception e) {
            System.err.println("Error getting attributes by product " + productId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Interface Relations (Additional) ==========
    
    @Override
    public List<Integer> getDatasetsByInterface(int interfaceId) {
        // Via systems: Interface → System → Dataset
        try {
            Set<Integer> datasetIds = new HashSet<>();
            List<Integer> systemIds = getSystemsByInterface(interfaceId);
            for (Integer systemId : systemIds) {
                List<Integer> datasets = getDatasetsBySystem(systemId);
                datasetIds.addAll(datasets);
            }
            return new ArrayList<>(datasetIds);
        } catch (Exception e) {
            System.err.println("Error getting datasets by interface " + interfaceId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Business Area Relations ==========
    
    @Override
    public List<Integer> getGlossariesByBusinessArea(int businessAreaId) {
        try {
            String sql = "SELECT Glossary_ID FROM businessarea_x_glossary WHERE BusinessArea_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(businessAreaId));
            return extractIds(rows, "Glossary_ID");
        } catch (SQLException e) {
            System.err.println("Error getting glossaries by business area " + businessAreaId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getProcessesByBusinessArea(int businessAreaId) {
        try {
            String sql = "SELECT Process_ID FROM businessarea_x_process WHERE BusinessArea_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(businessAreaId));
            return extractIds(rows, "Process_ID");
        } catch (SQLException e) {
            System.err.println("Error getting processes by business area " + businessAreaId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getSystemsByBusinessArea(int businessAreaId) {
        try {
            String sql = "SELECT System_ID FROM businessarea_x_system WHERE BusinessArea_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(businessAreaId));
            return extractIds(rows, "System_ID");
        } catch (SQLException e) {
            System.err.println("Error getting systems by business area " + businessAreaId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getDatasetsByBusinessArea(int businessAreaId) {
        // Special relationship: BusinessArea → System → Dataset
        try {
            Set<Integer> datasetIds = new HashSet<>();
            List<Integer> systemIds = getSystemsByBusinessArea(businessAreaId);
            for (Integer systemId : systemIds) {
                List<Integer> datasets = getDatasetsBySystem(systemId);
                datasetIds.addAll(datasets);
            }
            return new ArrayList<>(datasetIds);
        } catch (Exception e) {
            System.err.println("Error getting datasets by business area " + businessAreaId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAttributesByBusinessArea(int businessAreaId) {
        // Special relationship: BusinessArea → System → Dataset → Attribute
        try {
            Set<Integer> attributeIds = new HashSet<>();
            List<Integer> datasetIds = getDatasetsByBusinessArea(businessAreaId);
            for (Integer datasetId : datasetIds) {
                List<Integer> attrs = getAttributesByDataset(datasetId);
                attributeIds.addAll(attrs);
            }
            return new ArrayList<>(attributeIds);
        } catch (Exception e) {
            System.err.println("Error getting attributes by business area " + businessAreaId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Client Relations ==========
    
    @Override
    public List<Integer> getGlossariesByClient(int clientId) {
        try {
            String sql = "SELECT Glossary_ID FROM client_x_glossary WHERE Client_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(clientId));
            return extractIds(rows, "Glossary_ID");
        } catch (SQLException e) {
            System.err.println("Error getting glossaries by client " + clientId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getDatasetsByClient(int clientId) {
        try {
            String sql = "SELECT Dataset_ID FROM client_x_dataset WHERE Client_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(clientId));
            return extractIds(rows, "Dataset_ID");
        } catch (SQLException e) {
            System.err.println("Error getting datasets by client " + clientId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getSystemsByClient(int clientId) {
        try {
            String sql = "SELECT System_ID FROM client_x_system WHERE Client_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(clientId));
            return extractIds(rows, "System_ID");
        } catch (SQLException e) {
            System.err.println("Error getting systems by client " + clientId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getPoliciesByClient(int clientId) {
        try {
            String sql = "SELECT Policy_ID FROM client_x_policy WHERE Client_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(clientId));
            return extractIds(rows, "Policy_ID");
        } catch (SQLException e) {
            System.err.println("Error getting policies by client " + clientId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getProcessesByClient(int clientId) {
        try {
            String sql = "SELECT Process_ID FROM client_x_process WHERE Client_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(clientId));
            return extractIds(rows, "Process_ID");
        } catch (SQLException e) {
            System.err.println("Error getting processes by client " + clientId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getProjectsByClient(int clientId) {
        try {
            String sql = "SELECT Project_ID FROM client_x_project WHERE Client_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(clientId));
            return extractIds(rows, "Project_ID");
        } catch (SQLException e) {
            System.err.println("Error getting projects by client " + clientId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAttributesByClient(int clientId) {
        // Special relationship: Client → Dataset → Attribute
        try {
            Set<Integer> attributeIds = new HashSet<>();
            List<Integer> datasetIds = getDatasetsByClient(clientId);
            for (Integer datasetId : datasetIds) {
                List<Integer> attrs = getAttributesByDataset(datasetId);
                attributeIds.addAll(attrs);
            }
            return new ArrayList<>(attributeIds);
        } catch (Exception e) {
            System.err.println("Error getting attributes by client " + clientId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Committee Relations ==========
    
    @Override
    public List<Integer> getCapabilitiesByCommittee(int committeeId) {
        try {
            String sql = "SELECT Capability_ID FROM committee_x_capability WHERE Committee_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(committeeId));
            return extractIds(rows, "Capability_ID");
        } catch (SQLException e) {
            System.err.println("Error getting capabilities by committee " + committeeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getCommitteesByCommittee(int committeeId) {
        try {
            String sql = "SELECT Target_ID FROM committee_x_committee WHERE Source_ID = ? AND DeleteDatetime IS NULL " +
                        "UNION SELECT Source_ID FROM committee_x_committee WHERE Target_ID = ? AND DeleteDatetime IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(committeeId, committeeId));
            Set<Integer> committeeIds = new HashSet<>();
            for (Map<String, Object> row : rows) {
                Object value = row.get("Target_ID");
                if (value == null) value = row.get("Source_ID");
                if (value instanceof Integer) committeeIds.add((Integer) value);
                else if (value instanceof Number) committeeIds.add(((Number) value).intValue());
            }
            return new ArrayList<>(committeeIds);
        } catch (SQLException e) {
            System.err.println("Error getting committees by committee " + committeeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getGlossariesByCommittee(int committeeId) {
        // Via capabilities: Committee → Capability → Glossary
        try {
            Set<Integer> glossaryIds = new HashSet<>();
            List<Integer> capabilityIds = getCapabilitiesByCommittee(committeeId);
            for (Integer capabilityId : capabilityIds) {
                List<Integer> glossaries = getGlossariesByCapability(capabilityId);
                glossaryIds.addAll(glossaries);
            }
            return new ArrayList<>(glossaryIds);
        } catch (Exception e) {
            System.err.println("Error getting glossaries by committee " + committeeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getDatasetsByCommittee(int committeeId) {
        // Via capabilities: Committee → Capability → Glossary → Dataset
        try {
            Set<Integer> datasetIds = new HashSet<>();
            List<Integer> capabilityIds = getCapabilitiesByCommittee(committeeId);
            for (Integer capabilityId : capabilityIds) {
                List<Integer> datasets = getDatasetsByCapability(capabilityId);
                datasetIds.addAll(datasets);
            }
            return new ArrayList<>(datasetIds);
        } catch (Exception e) {
            System.err.println("Error getting datasets by committee " + committeeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAttributesByCommittee(int committeeId) {
        // Via capabilities: Committee → Capability → Glossary → Attribute
        try {
            Set<Integer> attributeIds = new HashSet<>();
            List<Integer> capabilityIds = getCapabilitiesByCommittee(committeeId);
            for (Integer capabilityId : capabilityIds) {
                List<Integer> attrs = getAttributesByCapability(capabilityId);
                attributeIds.addAll(attrs);
            }
            return new ArrayList<>(attributeIds);
        } catch (Exception e) {
            System.err.println("Error getting attributes by committee " + committeeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getSystemsByCommittee(int committeeId) {
        // Via capabilities: Committee → Capability → Glossary → System
        try {
            Set<Integer> systemIds = new HashSet<>();
            List<Integer> capabilityIds = getCapabilitiesByCommittee(committeeId);
            for (Integer capabilityId : capabilityIds) {
                List<Integer> systems = getSystemsByCapability(capabilityId);
                systemIds.addAll(systems);
            }
            return new ArrayList<>(systemIds);
        } catch (Exception e) {
            System.err.println("Error getting systems by committee " + committeeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Legal Entity Relations ==========
    
    @Override
    public List<Integer> getGeographiesByLegalEntity(int legalId) {
        try {
            String sql = "SELECT Geography_ID FROM legal_x_geography WHERE Legal_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(legalId));
            return extractIds(rows, "Geography_ID");
        } catch (SQLException e) {
            System.err.println("Error getting geographies by legal entity " + legalId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getDatasetsByLegalEntity(int legalId) {
        try {
            String sql = "SELECT Dataset_ID FROM dataset_x_legal WHERE Legal_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(legalId));
            return extractIds(rows, "Dataset_ID");
        } catch (SQLException e) {
            System.err.println("Error getting datasets by legal entity " + legalId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getSystemsByLegalEntity(int legalId) {
        // Special relationship: Legal → Dataset → System
        try {
            Set<Integer> systemIds = new HashSet<>();
            List<Integer> datasetIds = getDatasetsByLegalEntity(legalId);
            for (Integer datasetId : datasetIds) {
                Integer systemId = getSystemOfDataset(datasetId);
                if (systemId != null) {
                    systemIds.add(systemId);
                }
            }
            return new ArrayList<>(systemIds);
        } catch (Exception e) {
            System.err.println("Error getting systems by legal entity " + legalId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAttributesByLegalEntity(int legalId) {
        // Special relationship: Legal → Dataset → Attribute
        try {
            Set<Integer> attributeIds = new HashSet<>();
            List<Integer> datasetIds = getDatasetsByLegalEntity(legalId);
            for (Integer datasetId : datasetIds) {
                List<Integer> attrs = getAttributesByDataset(datasetId);
                attributeIds.addAll(attrs);
            }
            return new ArrayList<>(attributeIds);
        } catch (Exception e) {
            System.err.println("Error getting attributes by legal entity " + legalId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Regulation Relations ==========
    
    @Override
    public List<Integer> getRegulatorsByRegulation(int regulationId) {
        try {
            String sql = "SELECT RegulatorID FROM regulation_x_regulator WHERE RegulationID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(regulationId));
            return extractIds(rows, "RegulatorID");
        } catch (SQLException e) {
            System.err.println("Error getting regulators by regulation " + regulationId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getRegulatoryThemesByRegulation(int regulationId) {
        try {
            String sql = "SELECT RegulatoryTheme_ID FROM regulation_x_regulatorytheme WHERE Regulation_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(regulationId));
            return extractIds(rows, "RegulatoryTheme_ID");
        } catch (SQLException e) {
            System.err.println("Error getting regulatory themes by regulation " + regulationId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getGeographiesByRegulation(int regulationId) {
        // Special relationship: Regulation → Regulator → Geography
        try {
            Set<Integer> geographyIds = new HashSet<>();
            List<Integer> regulatorIds = getRegulatorsByRegulation(regulationId);
            for (Integer regulatorId : regulatorIds) {
                List<Integer> geographies = getGeographiesByRegulator(regulatorId);
                geographyIds.addAll(geographies);
            }
            return new ArrayList<>(geographyIds);
        } catch (Exception e) {
            System.err.println("Error getting geographies by regulation " + regulationId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Regulator Relations ==========
    
    @Override
    public List<Integer> getGeographiesByRegulator(int regulatorId) {
        try {
            String sql = "SELECT Geography_ID FROM regulator_x_geography WHERE Regulator_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(regulatorId));
            return extractIds(rows, "Geography_ID");
        } catch (SQLException e) {
            System.err.println("Error getting geographies by regulator " + regulatorId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getRegulationsByRegulator(int regulatorId) {
        try {
            String sql = "SELECT RegulationID FROM regulation_x_regulator WHERE RegulatorID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(regulatorId));
            return extractIds(rows, "RegulationID");
        } catch (SQLException e) {
            System.err.println("Error getting regulations by regulator " + regulatorId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getRegulatoryThemesByRegulator(int regulatorId) {
        // Special relationship: Regulator → Regulation → Theme
        try {
            Set<Integer> themeIds = new HashSet<>();
            List<Integer> regulationIds = getRegulationsByRegulator(regulatorId);
            for (Integer regulationId : regulationIds) {
                List<Integer> themes = getRegulatoryThemesByRegulation(regulationId);
                themeIds.addAll(themes);
            }
            return new ArrayList<>(themeIds);
        } catch (Exception e) {
            System.err.println("Error getting regulatory themes by regulator " + regulatorId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== RegulatoryTheme Relations ==========
    
    @Override
    public List<Integer> getRegulationsByRegulatoryTheme(int themeId) {
        try {
            String sql = "SELECT Regulation_ID FROM regulation_x_regulatorytheme WHERE RegulatoryTheme_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(themeId));
            return extractIds(rows, "Regulation_ID");
        } catch (SQLException e) {
            System.err.println("Error getting regulations by regulatory theme " + themeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getRegulatorsByRegulatoryTheme(int themeId) {
        // Special relationship: Theme → Regulation → Regulator
        try {
            Set<Integer> regulatorIds = new HashSet<>();
            List<Integer> regulationIds = getRegulationsByRegulatoryTheme(themeId);
            for (Integer regulationId : regulationIds) {
                List<Integer> regulators = getRegulatorsByRegulation(regulationId);
                regulatorIds.addAll(regulators);
            }
            return new ArrayList<>(regulatorIds);
        } catch (Exception e) {
            System.err.println("Error getting regulators by regulatory theme " + themeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getGeographiesByRegulatoryTheme(int themeId) {
        // Special relationship: Theme → Regulation → Regulator → Geography
        try {
            Set<Integer> geographyIds = new HashSet<>();
            List<Integer> regulatorIds = getRegulatorsByRegulatoryTheme(themeId);
            for (Integer regulatorId : regulatorIds) {
                List<Integer> geographies = getGeographiesByRegulator(regulatorId);
                geographyIds.addAll(geographies);
            }
            return new ArrayList<>(geographyIds);
        } catch (Exception e) {
            System.err.println("Error getting geographies by regulatory theme " + themeId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Geography Relations (Regulatory) ==========
    
    @Override
    public List<Integer> getRegulatorsByGeography(int geographyId) {
        try {
            String sql = "SELECT Regulator_ID FROM regulator_x_geography WHERE Geography_ID = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(geographyId));
            return extractIds(rows, "Regulator_ID");
        } catch (SQLException e) {
            System.err.println("Error getting regulators by geography " + geographyId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getRegulationsByGeography(int geographyId) {
        // Special relationship: Geography → Regulator → Regulation
        try {
            Set<Integer> regulationIds = new HashSet<>();
            List<Integer> regulatorIds = getRegulatorsByGeography(geographyId);
            for (Integer regulatorId : regulatorIds) {
                List<Integer> regulations = getRegulationsByRegulator(regulatorId);
                regulationIds.addAll(regulations);
            }
            return new ArrayList<>(regulationIds);
        } catch (Exception e) {
            System.err.println("Error getting regulations by geography " + geographyId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getRegulatoryThemesByGeography(int geographyId) {
        // Special relationship: Geography → Regulator → Regulation → Theme
        try {
            Set<Integer> themeIds = new HashSet<>();
            List<Integer> regulationIds = getRegulationsByGeography(geographyId);
            for (Integer regulationId : regulationIds) {
                List<Integer> themes = getRegulatoryThemesByRegulation(regulationId);
                themeIds.addAll(themes);
            }
            return new ArrayList<>(themeIds);
        } catch (Exception e) {
            System.err.println("Error getting regulatory themes by geography " + geographyId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Person/People Relations ==========
    
    @Override
    public List<Integer> getChangeRequestsByPerson(int personId) {
        try {
            String sql = "SELECT ID FROM changerequest WHERE Created_By = ? AND Deleted_At IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(personId));
            return extractIds(rows, "ID");
        } catch (SQLException e) {
            System.err.println("Error getting change requests by person " + personId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getActiveTasksByPerson(int personId) {
        try {
            String sql = "SELECT ID FROM workflow_instance_task " +
                        "WHERE Assigned_To = ? AND Status IN ('Pending', 'InProgress')";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(personId));
            return extractIds(rows, "ID");
        } catch (SQLException e) {
            System.err.println("Error getting active tasks by person " + personId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getOrgUnitsByPerson(int personId) {
        try {
            String sql = "SELECT Org_Unit_ID FROM people WHERE ID = ? AND Deleted_date IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(personId));
            return extractIds(rows, "Org_Unit_ID");
        } catch (SQLException e) {
            System.err.println("Error getting org units by person " + personId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getRolesByPerson(int personId) {
        try {
            String sql = "SELECT DISTINCT roleID FROM object_x_people WHERE ipid = ?";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(personId));
            return extractIds(rows, "roleID");
        } catch (SQLException e) {
            System.err.println("Error getting roles by person " + personId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getPeopleByOrgUnit(int orgUnitId) {
        try {
            String sql = "SELECT DISTINCT ID FROM people " +
                        "WHERE Org_Unit_ID = ? AND Deleted_date IS NULL";
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(orgUnitId));
            return extractIds(rows, "ID");
        } catch (SQLException e) {
            System.err.println("Error getting people by org unit " + orgUnitId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getObjectsByOrgUnit(int orgUnitId) {
        try {
            List<Integer> allObjectIds = new ArrayList<>();
            
            // Define all stakeholder tables and their object ID columns
            String[] tables = {
                "dataset_x_objectxpeople", "system_x_objectxpeople", "glossary_x_objectxpeople",
                "interface_x_objectxpeople", "process_x_objectxpeople", "project_x_objectxpeople",
                "product_x_objectxpeople", "policy_x_objectxpeople", "attribute_x_objectxpeople",
                "businessarea_x_objectxpeople", "legal_x_objectxpeople", "client_x_objectxpeople",
                "committee_x_objectxpeople", "geography_x_objectxpeople",
                "regulation_x_objectxpeople", "capability_x_objectxpeople"
            };
            
            String[] idColumns = {
                "Dataset_ID", "SystemID", "GlossaryID", "InterfaceID", "process_id", "project_id",
                "product_id", "Policy_ID", "AttributeID", "BusinessAreaID", "Legal_ID", "ClientID",
                "Committee_ID", "GeographyID", "RegulationID", "CapabilityID"
            };
            
            // Query each stakeholder table
            for (int i = 0; i < tables.length; i++) {
                try {
                    String sql = "SELECT DISTINCT lx." + idColumns[i] + 
                               " FROM " + tables[i] + " lx " +
                               "JOIN object_x_people oxp ON lx.Object_x_ipid = oxp.id " +
                               "JOIN people p ON oxp.ipid = p.ID " +
                               "WHERE p.Org_Unit_ID = ? AND p.Deleted_date IS NULL";
                               
                    List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, List.of(orgUnitId));
                    List<Integer> objectIds = extractIds(rows, idColumns[i]);
                    allObjectIds.addAll(objectIds);
                } catch (SQLException e) {
                    // Table might not exist or have different structure, continue
                    System.err.println("Error querying " + tables[i] + " for org unit " + orgUnitId + ": " + e.getMessage());
                }
            }
            
            return allObjectIds;
        } catch (Exception e) {
            System.err.println("Error getting objects by org unit " + orgUnitId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== Batch Operations ==========
    
    @Override
    public List<Integer> getSystemsByDatasetIds(Set<Integer> datasetIds) {
        if (datasetIds == null || datasetIds.isEmpty()) return new ArrayList<>();
        try {
            String placeholders = String.join(",", Collections.nCopies(datasetIds.size(), "?"));
            String sql = "SELECT DISTINCT MasterSource FROM dataset WHERE ID IN (" + placeholders + ") AND MasterSource IS NOT NULL AND DeletedDatetime IS NULL";
            List<Object> params = new ArrayList<>(datasetIds);
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, params);
            return extractIds(rows, "MasterSource");
        } catch (SQLException e) {
            System.err.println("Error getting systems by dataset IDs: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getDatasetsByIds(Set<Integer> datasetIds) {
        if (datasetIds == null || datasetIds.isEmpty()) return new ArrayList<>();
        try {
            String placeholders = String.join(",", Collections.nCopies(datasetIds.size(), "?"));
            String sql = "SELECT ID FROM dataset WHERE ID IN (" + placeholders + ") AND DeletedDatetime IS NULL";
            List<Object> params = new ArrayList<>(datasetIds);
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, params);
            return extractIds(rows, "ID");
        } catch (SQLException e) {
            System.err.println("Error getting datasets by IDs: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Integer> getAttributesByIds(Set<Integer> attributeIds) {
        if (attributeIds == null || attributeIds.isEmpty()) return new ArrayList<>();
        try {
            String placeholders = String.join(",", Collections.nCopies(attributeIds.size(), "?"));
            String sql = "SELECT ID FROM attribute WHERE ID IN (" + placeholders + ") AND DeletedDatetime IS NULL";
            List<Object> params = new ArrayList<>(attributeIds);
            List<Map<String, Object>> rows = databaseHelper.executeQuery(sql, params);
            return extractIds(rows, "ID");
        } catch (SQLException e) {
            System.err.println("Error getting attributes by IDs: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ========== People & Stakeholders ==========
    
    @Override
    public List<Integer> getDirectStakeholders(String objectType, int objectId) {
        // TODO: Implement based on object_x_people tables
        return new ArrayList<>();
    }
    
    @Override
    public List<Integer> getDirectRoles(int personId) {
        // TODO: Implement
        return new ArrayList<>();
    }
    
    // ========== Change Request ==========
    
    @Override
    public Object getTargetObjectOfChangeRequest(int crId) {
        // TODO: Implement
        return null;
    }
    
    @Override
    public List<Integer> getTasksByChangeRequest(int crId) {
        // TODO: Implement
        return new ArrayList<>();
    }
    
    // ========== Helper Methods ==========
    
    private List<Integer> extractIds(List<Map<String, Object>> rows, String columnName) {
        List<Integer> ids = new ArrayList<>();
        String columnLower = columnName != null ? columnName.toLowerCase() : null;
        for (Map<String, Object> row : rows) {
            Object value = row.get(columnName);
            if (value == null && columnLower != null) {
                value = row.get(columnLower);
            }
            if (value == null && columnName != null && !columnName.equals(columnLower)) {
                for (String key : row.keySet()) {
                    if (key != null && key.equalsIgnoreCase(columnName)) {
                        value = row.get(key);
                        break;
                    }
                }
            }
            if (value instanceof Integer) {
                ids.add((Integer) value);
            } else if (value instanceof Number) {
                ids.add(((Number) value).intValue());
            }
        }
        return ids;
    }
}

