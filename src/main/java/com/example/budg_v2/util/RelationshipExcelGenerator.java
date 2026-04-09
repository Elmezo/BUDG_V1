package com.example.budg_v2.util;

import com.example.budg_v2.bulk.relationships.RelationshipConfigRegistry;
import com.example.budg_v2.bulk.relationships.config.EntityConfig;
import com.example.budg_v2.bulk.relationships.config.RelationshipConfig;
import com.example.budg_v2.database.DatabaseConnection;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Utility class for generating Excel files for relationships
 */
public class RelationshipExcelGenerator {

    private static final Logger logger = LoggerFactory.getLogger(RelationshipExcelGenerator.class);

    /**
     * Generate Excel file for a relationship with data
     */
    public byte[] generateRelationshipExcel(String relationshipKey, List<Map<String, Object>> data, 
                                           String timestamp, boolean optional) {
        try {
            RelationshipConfig config = RelationshipConfigRegistry.getConfig(relationshipKey);
            if (config == null) {
                logger.warn("Relationship config not found for: {}", relationshipKey);
                return null;
            }

            XSSFWorkbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet(config.getDisplayName());

            // Create header row
            Row headerRow = sheet.createRow(0);
            List<String> headers = getHeadersForRelationship(config);
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            int rowNum = 1;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                int colNum = 0;
                for (String header : headers) {
                    Cell cell = row.createCell(colNum++);
                    Object value = rowData.get(header);
                    setCellValue(cell, value);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            workbook.close();
            
            return baos.toByteArray();

        } catch (Exception e) {
            logger.error("Error generating Excel for relationship {}: {}", relationshipKey, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get relationship data from database (backward compatible - uses source facet only)
     */
    public List<Map<String, Object>> getRelationshipData(String relationshipKey, 
                                                         List<Integer> sourceIds, 
                                                         String sourceFacet) {
        RelationshipConfig config = RelationshipConfigRegistry.getConfig(relationshipKey);
        if (config == null) {
            logger.warn("Relationship config not found for: {}", relationshipKey);
            return new ArrayList<>();
        }

        if (sourceIds.isEmpty()) {
            return new ArrayList<>();
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = buildRelationshipQuery(config, sourceIds.size(), sourceFacet);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setParameters(ps, sourceIds, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> results = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = mapResultSetToRow(rs, config);
                        results.add(row);
                    }
                    return results;
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting relationship data for {}: {}", relationshipKey, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Get relationship data from database with bidirectional support
     * Queries relationships where either entity A or entity B IDs match
     */
    public List<Map<String, Object>> getRelationshipData(String relationshipKey, 
                                                         List<Integer> entityAIds,
                                                         List<Integer> entityBIds,
                                                         String entityAName,
                                                         String entityBName) {
        RelationshipConfig config = RelationshipConfigRegistry.getConfig(relationshipKey);
        if (config == null) {
            logger.warn("Relationship config not found for: {}", relationshipKey);
            return new ArrayList<>();
        }

        // If both lists are empty, return empty
        if ((entityAIds == null || entityAIds.isEmpty()) && (entityBIds == null || entityBIds.isEmpty())) {
            return new ArrayList<>();
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = buildRelationshipQueryBidirectional(config, entityAIds, entityBIds, entityAName, entityBName);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                if (entityAIds != null && !entityAIds.isEmpty()) {
                    paramIndex = setParameters(ps, entityAIds, paramIndex);
                }
                if (entityBIds != null && !entityBIds.isEmpty()) {
                    setParameters(ps, entityBIds, paramIndex);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> results = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = mapResultSetToRow(rs, config);
                        results.add(row);
                    }
                    return results;
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting bidirectional relationship data for {}: {}", relationshipKey, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Build SQL query for relationship
     */
    private String buildRelationshipQuery(RelationshipConfig config, int idCount, String sourceFacet) {
        String inClause = String.join(",", Collections.nCopies(idCount, "?"));
        String tableName = config.getTableName();
        EntityConfig entityA = config.getEntityA();
        EntityConfig entityB = config.getEntityB();
        
        // Determine which entity is the source based on sourceFacet
        // Normalize facet names for comparison
        String normalizedSourceFacet = sourceFacet.toLowerCase().replace("_", "");
        String entityAName = entityA.getName().toLowerCase().replace("_", "");
        
        boolean sourceIsA = entityAName.equals(normalizedSourceFacet) || 
                           entityA.getTableName().toLowerCase().equals(normalizedSourceFacet);
        
        String sourceIdColumn = sourceIsA ? config.getEntityAIdColumn() : config.getEntityBIdColumn();

        // Build SELECT clause with proper column aliases matching headers
        StringBuilder select = new StringBuilder("SELECT ");
        
        // Special handling for Attribute X Attribute relationship
        if ("attributexattribute".equals(config.getKey()) || "attribute_x_attribute".equals(tableName)) {
            // Sourcing Logic
            select.append("r.Sourcing_Logic AS 'Sourcing Logic', ");
            // Sourcing Type (from relation type table)
            select.append("rt.PrimaryName AS 'Sourcing Type', ");
            // Target Attribute columns
            select.append("b.RefNumber AS 'Target Attribute Ref.', ");
            select.append("b.PrimaryName AS 'Target Attribute Name', ");
            select.append("d_tgt.PrimaryName AS 'Target Data Set Name', ");
            select.append("s_tgt.Name AS 'Target System Short Name', ");
            // Source Attribute columns
            select.append("a.RefNumber AS 'Source Attribute Ref.', ");
            select.append("a.PrimaryName AS 'Source Attribute Name', ");
            select.append("d_src.PrimaryName AS 'Source Data Set Name', ");
            select.append("s_src.Name AS 'Source System Short Name', ");
            // Scope of Data (from relation scope table)
            select.append("rs.PrimaryName AS 'Scope of Data', ");
            // Interface columns
            select.append("i.Name AS 'Interface Name', ");
            select.append("s_if_src.Name AS 'Interface Source System Short Name', ");
            select.append("s_if_tgt.Name AS 'Interface Target System Short Name'");
        } else {
            // Entity A columns
            if (entityA.hasRefColumn()) {
                select.append("a.").append(entityA.getRefColumn()).append(" AS '").append(getEntityDisplayRef(entityA)).append("', ");
            }
            select.append("a.").append(entityA.getNameColumn()).append(" AS '").append(getEntityDisplayName(entityA)).append("'");
            if (entityA.hasParentColumn()) {
                select.append(", parentA.").append(entityA.getNameColumn()).append(" AS '").append(getEntityDisplayParent(entityA)).append("'");
            }
            
            // Entity B columns
            select.append(", ");
            if (entityB.hasRefColumn()) {
                select.append("b.").append(entityB.getRefColumn()).append(" AS '").append(getEntityDisplayRef(entityB)).append("', ");
            }
            select.append("b.").append(entityB.getNameColumn()).append(" AS '").append(getEntityDisplayName(entityB)).append("'");
            if (entityB.hasParentColumn()) {
                select.append(", parentB.").append(entityB.getNameColumn()).append(" AS '").append(getEntityDisplayParent(entityB)).append("'");
            }
            
            // Additional context (e.g., System for Dataset)
            if (entityB.getName().equals("dataset")) {
                select.append(", sys.Name AS 'Data Set System Short Name'");
            } else if (entityA.getName().equals("dataset")) {
                select.append(", sys.Name AS 'Data Set System Short Name'");
            }
            
            // Relation type if exists - show name instead of ID
            if (config.hasRelationType()) {
                // If relation type table exists, show the name instead of ID
                if (config.getRelationTypeTable() != null && !config.getRelationTypeTable().isEmpty()) {
                    select.append(", rt.PrimaryName AS 'Relationship Type'");
                } else {
                    // Fallback to ID if no relation type table
                    select.append(", r.").append(config.getRelationTypeColumn()).append(" AS 'Relationship Type'");
                }
            }
            
            // Description if exists
            if (config.hasDescription()) {
                select.append(", r.").append(config.getDescriptionColumn()).append(" AS 'Description'");
            }
            
            // Additional columns
            for (String col : config.getAdditionalColumns()) {
                select.append(", r.").append(col).append(" AS '").append(col).append("'");
            }
        }

        // Build FROM clause
        StringBuilder from = new StringBuilder(" FROM ").append(tableName).append(" r");
        
        // Special handling for Attribute X Attribute relationship
        if ("attributexattribute".equals(config.getKey()) || "attribute_x_attribute".equals(tableName)) {
            // Join source attribute
            from.append(" LEFT JOIN attribute a ON a.ID = r.Source_AttributeID");
            // Join target attribute
            from.append(" LEFT JOIN attribute b ON b.ID = r.Target_AttributeID");
            // Join source dataset
            from.append(" LEFT JOIN dataset d_src ON d_src.ID = a.Dataset_ID");
            // Join target dataset
            from.append(" LEFT JOIN dataset d_tgt ON d_tgt.ID = b.Dataset_ID");
            // Join source system
            from.append(" LEFT JOIN system s_src ON s_src.id = d_src.MasterSource");
            // Join target system
            from.append(" LEFT JOIN system s_tgt ON s_tgt.id = d_tgt.MasterSource");
            // Join relation type (Sourcing Type)
            from.append(" LEFT JOIN attribute_x_attribute_relationtype rt ON rt.ID = r.Relation_Type");
            // Join relation scope (Scope of Data)
            from.append(" LEFT JOIN attribute_x_attribute_relationscope rs ON rs.ID = r.Relation_Scope");
            // Join interface (Interface Name)
            from.append(" LEFT JOIN interface i ON i.id = r.Relation_Method");
            // Join interface source system
            from.append(" LEFT JOIN system s_if_src ON s_if_src.id = i.Source_systemID");
            // Join interface target system
            from.append(" LEFT JOIN system s_if_tgt ON s_if_tgt.id = i.Target_systemID");
        } else {
            // Add JOINs to get entity A data
            from.append(" LEFT JOIN ").append(entityA.getTableName()).append(" a ON a.").append(entityA.getIdColumn())
                .append(" = r.").append(config.getEntityAIdColumn());
            if (entityA.hasParentColumn()) {
                from.append(" LEFT JOIN ").append(entityA.getTableName()).append(" parentA ON parentA.").append(entityA.getIdColumn())
                    .append(" = a.").append(entityA.getParentColumn());
            }
            
            // Add JOINs to get entity B data
            from.append(" LEFT JOIN ").append(entityB.getTableName()).append(" b ON b.").append(entityB.getIdColumn())
                .append(" = r.").append(config.getEntityBIdColumn());
            if (entityB.hasParentColumn()) {
                from.append(" LEFT JOIN ").append(entityB.getTableName()).append(" parentB ON parentB.").append(entityB.getIdColumn())
                    .append(" = b.").append(entityB.getParentColumn());
            }
            
            // Add System join for Dataset
            if (entityB.getName().equals("dataset")) {
                from.append(" LEFT JOIN system sys ON sys.id = b.MasterSource");
            } else if (entityA.getName().equals("dataset")) {
                from.append(" LEFT JOIN system sys ON sys.id = a.MasterSource");
            }
            
            // Add relation type join if exists
            if (config.hasRelationType() && config.getRelationTypeTable() != null && !config.getRelationTypeTable().isEmpty()) {
                from.append(" LEFT JOIN ").append(config.getRelationTypeTable()).append(" rt ON rt.ID = r.").append(config.getRelationTypeColumn());
            }
        }

        // Build WHERE clause - use the column that matches sourceFacet
        StringBuilder where = new StringBuilder(" WHERE r.").append(sourceIdColumn)
            .append(" IN (").append(inClause).append(")");
        
        // Add soft delete checks
        if (entityA.hasDeletedColumn()) {
            where.append(" AND (a.").append(entityA.getDeletedColumn()).append(" IS NULL OR a.").append(entityA.getDeletedColumn()).append(" = '1970-01-01 00:00:00')");
        }
        if (entityB.hasDeletedColumn()) {
            where.append(" AND (b.").append(entityB.getDeletedColumn()).append(" IS NULL OR b.").append(entityB.getDeletedColumn()).append(" = '1970-01-01 00:00:00')");
        }

        return select.toString() + from.toString() + where.toString();
    }

    /**
     * Build SQL query for relationship with bidirectional support
     * Queries relationships where either entity A or entity B IDs match
     */
    private String buildRelationshipQueryBidirectional(RelationshipConfig config, 
                                                      List<Integer> entityAIds,
                                                      List<Integer> entityBIds,
                                                      String entityAName,
                                                      String entityBName) {
        String tableName = config.getTableName();
        EntityConfig entityA = config.getEntityA();
        EntityConfig entityB = config.getEntityB();
        
        // Build SELECT clause (same as unidirectional)
        StringBuilder select = new StringBuilder("SELECT ");
        
        // Special handling for Attribute X Attribute relationship
        if ("attributexattribute".equals(config.getKey()) || "attribute_x_attribute".equals(tableName)) {
            // Sourcing Logic
            select.append("r.Sourcing_Logic AS 'Sourcing Logic', ");
            // Sourcing Type (from relation type table)
            select.append("rt.PrimaryName AS 'Sourcing Type', ");
            // Target Attribute columns
            select.append("b.RefNumber AS 'Target Attribute Ref.', ");
            select.append("b.PrimaryName AS 'Target Attribute Name', ");
            select.append("d_tgt.PrimaryName AS 'Target Data Set Name', ");
            select.append("s_tgt.Name AS 'Target System Short Name', ");
            // Source Attribute columns
            select.append("a.RefNumber AS 'Source Attribute Ref.', ");
            select.append("a.PrimaryName AS 'Source Attribute Name', ");
            select.append("d_src.PrimaryName AS 'Source Data Set Name', ");
            select.append("s_src.Name AS 'Source System Short Name', ");
            // Scope of Data (from relation scope table)
            select.append("rs.PrimaryName AS 'Scope of Data', ");
            // Interface columns
            select.append("i.Name AS 'Interface Name', ");
            select.append("s_if_src.Name AS 'Interface Source System Short Name', ");
            select.append("s_if_tgt.Name AS 'Interface Target System Short Name'");
        } else {
            // Entity A columns
            if (entityA.hasRefColumn()) {
                select.append("a.").append(entityA.getRefColumn()).append(" AS '").append(getEntityDisplayRef(entityA)).append("', ");
            }
            select.append("a.").append(entityA.getNameColumn()).append(" AS '").append(getEntityDisplayName(entityA)).append("'");
            if (entityA.hasParentColumn()) {
                select.append(", parentA.").append(entityA.getNameColumn()).append(" AS '").append(getEntityDisplayParent(entityA)).append("'");
            }
            
            // Entity B columns
            select.append(", ");
            if (entityB.hasRefColumn()) {
                select.append("b.").append(entityB.getRefColumn()).append(" AS '").append(getEntityDisplayRef(entityB)).append("', ");
            }
            select.append("b.").append(entityB.getNameColumn()).append(" AS '").append(getEntityDisplayName(entityB)).append("'");
            if (entityB.hasParentColumn()) {
                select.append(", parentB.").append(entityB.getNameColumn()).append(" AS '").append(getEntityDisplayParent(entityB)).append("'");
            }
            
            // Additional context (e.g., System for Dataset)
            if (entityB.getName().equals("dataset")) {
                select.append(", sys.Name AS 'Data Set System Short Name'");
            } else if (entityA.getName().equals("dataset")) {
                select.append(", sys.Name AS 'Data Set System Short Name'");
            }
            
            // Relation type if exists
            if (config.hasRelationType()) {
                if (config.getRelationTypeTable() != null && !config.getRelationTypeTable().isEmpty()) {
                    select.append(", rt.PrimaryName AS 'Relationship Type'");
                } else {
                    select.append(", r.").append(config.getRelationTypeColumn()).append(" AS 'Relationship Type'");
                }
            }
            
            // Description if exists
            if (config.hasDescription()) {
                select.append(", r.").append(config.getDescriptionColumn()).append(" AS 'Description'");
            }
            
            // Additional columns
            for (String col : config.getAdditionalColumns()) {
                select.append(", r.").append(col).append(" AS '").append(col).append("'");
            }
        }

        // Build FROM clause (same as unidirectional)
        StringBuilder from = new StringBuilder(" FROM ").append(tableName).append(" r");
        
        // Special handling for Attribute X Attribute relationship
        if ("attributexattribute".equals(config.getKey()) || "attribute_x_attribute".equals(tableName)) {
            // Join source attribute
            from.append(" LEFT JOIN attribute a ON a.ID = r.Source_AttributeID");
            // Join target attribute
            from.append(" LEFT JOIN attribute b ON b.ID = r.Target_AttributeID");
            // Join source dataset
            from.append(" LEFT JOIN dataset d_src ON d_src.ID = a.Dataset_ID");
            // Join target dataset
            from.append(" LEFT JOIN dataset d_tgt ON d_tgt.ID = b.Dataset_ID");
            // Join source system
            from.append(" LEFT JOIN system s_src ON s_src.id = d_src.MasterSource");
            // Join target system
            from.append(" LEFT JOIN system s_tgt ON s_tgt.id = d_tgt.MasterSource");
            // Join relation type (Sourcing Type)
            from.append(" LEFT JOIN attribute_x_attribute_relationtype rt ON rt.ID = r.Relation_Type");
            // Join relation scope (Scope of Data)
            from.append(" LEFT JOIN attribute_x_attribute_relationscope rs ON rs.ID = r.Relation_Scope");
            // Join interface (Interface Name)
            from.append(" LEFT JOIN interface i ON i.id = r.Relation_Method");
            // Join interface source system
            from.append(" LEFT JOIN system s_if_src ON s_if_src.id = i.Source_systemID");
            // Join interface target system
            from.append(" LEFT JOIN system s_if_tgt ON s_if_tgt.id = i.Target_systemID");
        } else {
            // Add JOINs to get entity A data
            from.append(" LEFT JOIN ").append(entityA.getTableName()).append(" a ON a.").append(entityA.getIdColumn())
                .append(" = r.").append(config.getEntityAIdColumn());
            if (entityA.hasParentColumn()) {
                from.append(" LEFT JOIN ").append(entityA.getTableName()).append(" parentA ON parentA.").append(entityA.getIdColumn())
                    .append(" = a.").append(entityA.getParentColumn());
            }
            
            // Add JOINs to get entity B data
            from.append(" LEFT JOIN ").append(entityB.getTableName()).append(" b ON b.").append(entityB.getIdColumn())
                .append(" = r.").append(config.getEntityBIdColumn());
            if (entityB.hasParentColumn()) {
                from.append(" LEFT JOIN ").append(entityB.getTableName()).append(" parentB ON parentB.").append(entityB.getIdColumn())
                    .append(" = b.").append(entityB.getParentColumn());
            }
            
            // Add System join for Dataset
            if (entityB.getName().equals("dataset")) {
                from.append(" LEFT JOIN system sys ON sys.id = b.MasterSource");
            } else if (entityA.getName().equals("dataset")) {
                from.append(" LEFT JOIN system sys ON sys.id = a.MasterSource");
            }
            
            // Add relation type join if exists
            if (config.hasRelationType() && config.getRelationTypeTable() != null && !config.getRelationTypeTable().isEmpty()) {
                from.append(" LEFT JOIN ").append(config.getRelationTypeTable()).append(" rt ON rt.ID = r.").append(config.getRelationTypeColumn());
            }
        }

        // Build WHERE clause with OR condition for bidirectional query
        StringBuilder where = new StringBuilder(" WHERE (");
        List<String> conditions = new ArrayList<>();
        
        if (entityAIds != null && !entityAIds.isEmpty()) {
            String inClauseA = String.join(",", Collections.nCopies(entityAIds.size(), "?"));
            conditions.add("r." + config.getEntityAIdColumn() + " IN (" + inClauseA + ")");
        }
        
        if (entityBIds != null && !entityBIds.isEmpty()) {
            String inClauseB = String.join(",", Collections.nCopies(entityBIds.size(), "?"));
            conditions.add("r." + config.getEntityBIdColumn() + " IN (" + inClauseB + ")");
        }
        
        if (conditions.isEmpty()) {
            // No IDs provided, return empty result
            return select.toString() + from.toString() + " WHERE 1=0";
        }
        
        where.append(String.join(" OR ", conditions)).append(")");
        
        // Add soft delete checks
        if (entityA.hasDeletedColumn()) {
            where.append(" AND (a.").append(entityA.getDeletedColumn()).append(" IS NULL OR a.").append(entityA.getDeletedColumn()).append(" = '1970-01-01 00:00:00')");
        }
        if (entityB.hasDeletedColumn()) {
            where.append(" AND (b.").append(entityB.getDeletedColumn()).append(" IS NULL OR b.").append(entityB.getDeletedColumn()).append(" = '1970-01-01 00:00:00')");
        }

        return select.toString() + from.toString() + where.toString();
    }

    /**
     * Public wrapper for exporters / tooling (same as migration Excel headers).
     */
    public List<String> getHeadersForRelationshipPublic(RelationshipConfig config) {
        return getHeadersForRelationship(config);
    }

    /**
     * Get headers for relationship Excel
     * Format matches bulk upload template format
     */
    private List<String> getHeadersForRelationship(RelationshipConfig config) {
        List<String> headers = new ArrayList<>();
        
        // Special handling for Attribute X Attribute relationship
        if ("attributexattribute".equals(config.getKey()) || "attribute_x_attribute".equals(config.getTableName())) {
            headers.add("Sourcing Logic");
            headers.add("Sourcing Type");
            headers.add("Target Attribute Ref.");
            headers.add("Target Attribute Name");
            headers.add("Target Data Set Name");
            headers.add("Target System Short Name");
            headers.add("Source Attribute Ref.");
            headers.add("Source Attribute Name");
            headers.add("Source Data Set Name");
            headers.add("Source System Short Name");
            headers.add("Scope of Data");
            headers.add("Interface Name");
            headers.add("Interface Source System Short Name");
            headers.add("Interface Target System Short Name");
            return headers;
        }
        
        EntityConfig entityA = config.getEntityA();
        EntityConfig entityB = config.getEntityB();
        
        // Entity A columns (with proper display names)
        if (entityA.hasRefColumn()) {
            headers.add(getEntityDisplayRef(entityA));
        }
        headers.add(getEntityDisplayName(entityA));
        if (entityA.hasParentColumn()) {
            headers.add(getEntityDisplayParent(entityA));
        }
        
        // Entity B columns (with proper display names)
        if (entityB.hasRefColumn()) {
            headers.add(getEntityDisplayRef(entityB));
        }
        headers.add(getEntityDisplayName(entityB));
        if (entityB.hasParentColumn()) {
            headers.add(getEntityDisplayParent(entityB));
        }
        
        // Additional context columns (e.g., System Short Name for Dataset)
        if (entityB.getName().equals("dataset") || entityA.getName().equals("dataset")) {
            headers.add("Data Set System Short Name");
        }
        
        // Relation type if exists
        if (config.hasRelationType()) {
            headers.add("Relationship Type");
        }
        
        // Description if exists
        if (config.hasDescription()) {
            headers.add("Description");
        }
        
        // Additional columns
        for (String col : config.getAdditionalColumns()) {
            headers.add(col);
        }
        
        return headers;
    }

    /**
     * Get entity display ref column name
     */
    private String getEntityDisplayRef(EntityConfig entity) {
        String entityName = getEntityDisplayNameForHeader(entity.getName());
        return entityName + " Ref.";
    }

    /**
     * Get entity display name column
     */
    private String getEntityDisplayName(EntityConfig entity) {
        return getEntityDisplayNameForHeader(entity.getName()) + " Name";
    }

    /**
     * Get entity display parent column
     */
    private String getEntityDisplayParent(EntityConfig entity) {
        return "Parent " + getEntityDisplayNameForHeader(entity.getName()) + " Name";
    }

    /**
     * Get display name for entity in headers
     */
    private String getEntityDisplayNameForHeader(String entityName) {
        Map<String, String> displayNames = Map.of(
            "dataset", "Data Set",
            "legal_entity", "Legal Entity",
            "legalentity", "Legal Entity",
            "business_area", "Business Area",
            "regulatory_theme", "Regulatory Theme"
        );
        return displayNames.getOrDefault(entityName.toLowerCase(), capitalizeFirst(entityName.replace("_", " ")));
    }

    /**
     * Map ResultSet to row Map
     * Headers use display names (e.g., "Policy Ref.") which match the SQL aliases
     */
    private Map<String, Object> mapResultSetToRow(ResultSet rs, RelationshipConfig config) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        List<String> headers = getHeadersForRelationship(config);
        
        // Get column names from ResultSet metadata
        java.sql.ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        Map<String, Integer> columnIndexMap = new HashMap<>();
        for (int i = 1; i <= columnCount; i++) {
            String columnLabel = metaData.getColumnLabel(i);
            columnIndexMap.put(columnLabel, i);
        }
        
        for (String header : headers) {
            try {
                // Try to find column by label (SQL alias)
                Integer columnIndex = columnIndexMap.get(header);
                if (columnIndex != null) {
                    Object value = rs.getObject(columnIndex);
                    row.put(header, value);
                } else {
                    // Column not found, set to null
                    row.put(header, null);
                }
            } catch (SQLException e) {
                // Column might not exist, set to null
                row.put(header, null);
            }
        }
        
        return row;
    }

    /**
     * Set cell value based on type
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof java.util.Date) {
            cell.setCellValue((java.util.Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Create header style
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * Capitalize first letter
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Set parameters for IN clause
     * Returns the next parameter index after setting all IDs
     */
    private int setParameters(PreparedStatement ps, List<Integer> ids, int startIndex) throws SQLException {
        for (int i = 0; i < ids.size(); i++) {
            ps.setInt(startIndex + i, ids.get(i));
        }
        return startIndex + ids.size();
    }
}

