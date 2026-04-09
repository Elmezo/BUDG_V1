package com.example.budg_v2.service;

import com.example.budg_v2.bulk.MigrationManifestEntry;
import com.example.budg_v2.bulk.relationships.RelationshipConfigRegistry;
import com.example.budg_v2.bulk.relationships.config.RelationshipConfig;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatically detect relationships between entities from imported data
 * Analyzes Excel files to find foreign key relationships and dependencies
 */
public class RelationshipDetector {

    private static final Logger logger = LoggerFactory.getLogger(RelationshipDetector.class);

    // Pattern to match foreign key columns (e.g., System_ID, Dataset_ID)
    private static final Pattern FK_COLUMN_PATTERN = Pattern.compile("(.+?)_ID$", Pattern.CASE_INSENSITIVE);

    /** Columns that imply same-entity parent/child; graph order does not reorder rows within a sheet. */
    private static final Pattern SAME_SHEET_PARENT_COLUMN = Pattern.compile(
            "(?i)^(parent\\s*id|parentid|parent_id|parent\\s+name|parent\\s+ref\\.?|parent\\s*ref)$");

    /**
     * Detect relationships from manifest entries and files
     * 
     * @param entries Manifest entries
     * @param files   Map of file names to File objects
     * @return Dependency graph
     */
    public DependencyGraph detectRelationships(List<MigrationManifestEntry> entries,
            Map<String, File> files) {
        DependencyGraph graph = new DependencyGraph();

        // Map entity names from targetRef
        Map<String, String> fileToEntity = new HashMap<>();
        for (MigrationManifestEntry entry : entries) {
            String entityName = MigrationTargetRefResolver.resolveEntityLabel(entry.targetRef);
            if (entityName != null) {
                fileToEntity.put(entry.fileEntry, entityName);
            }
        }

        // Analyze each Excel file for foreign key columns
        for (MigrationManifestEntry entry : entries) {
            File file = files.get(entry.fileEntry);
            if (file == null || !file.exists()) {
                continue;
            }

            String entityName = fileToEntity.get(entry.fileEntry);
            if (entityName == null) {
                continue;
            }

            String rk = MigrationTargetRefResolver.relationshipRegistryKeyOrNull(entry.targetRef);
            if (rk != null) {
                RelationshipConfig rc = RelationshipConfigRegistry.getConfig(rk);
                if (rc != null) {
                    String depA = EntityProcessorRegistry.facetToImporterEntity(rc.getEntityA().getName());
                    String depB = EntityProcessorRegistry.facetToImporterEntity(rc.getEntityB().getName());
                    if (depA != null) {
                        graph.addDependency(entityName, depA);
                    }
                    if (depB != null) {
                        graph.addDependency(entityName, depB);
                    }
                }
            }

            try {
                Set<String> dependencies = analyzeFileForDependencies(file, fileToEntity);
                for (String dependency : dependencies) {
                    graph.addDependency(entityName, dependency);
                    logger.debug("Detected dependency: {} depends on {}", entityName, dependency);
                }
            } catch (Exception e) {
                logger.warn("Error analyzing file {} for dependencies: {}", entry.fileEntry, e.getMessage());
            }
        }

        logger.info("Detected {} relationships in dependency graph", graph.getAllEntities().size());
        return graph;
    }

    /**
     * Analyze Excel file for foreign key dependencies
     */
    private Set<String> analyzeFileForDependencies(File file, Map<String, String> fileToEntity) throws IOException {
        Set<String> dependencies = new HashSet<>();

        try (FileInputStream fis = new FileInputStream(file);
                Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                return dependencies;
            }

            // Get header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return dependencies;
            }

            boolean loggedParentHint = false;

            // Analyze column headers for foreign key patterns
            for (Cell cell : headerRow) {
                String columnName = getCellValueAsString(cell);
                if (columnName == null || columnName.isEmpty()) {
                    continue;
                }

                String trimmed = columnName.trim();
                if (!loggedParentHint && SAME_SHEET_PARENT_COLUMN.matcher(trimmed).matches()) {
                    loggedParentHint = true;
                    logger.info(
                            "Same-sheet hierarchy column '{}' in {} — import order is still parents-before-children per row in this file; "
                                    + "the dependency graph only orders separate Excel files.",
                            trimmed,
                            file.getName());
                }

                // Check if column matches foreign key pattern
                Matcher matcher = FK_COLUMN_PATTERN.matcher(columnName);
                if (matcher.find()) {
                    String referencedEntity = matcher.group(1);
                    // Normalize entity name
                    String normalizedEntity = MigrationTargetRefResolver.resolveEntityLabel(referencedEntity);
                    if (normalizedEntity == null) {
                        normalizedEntity = referencedEntity;
                    }

                    // Check if this entity exists in our file list
                    if (fileToEntity.containsValue(normalizedEntity)) {
                        dependencies.add(normalizedEntity);
                    } else {
                        // Try to match with known entities
                        String matchedEntity = findMatchingEntity(normalizedEntity, fileToEntity.values());
                        if (matchedEntity != null) {
                            dependencies.add(matchedEntity);
                        }
                    }
                }
            }
        }

        return dependencies;
    }

    /**
     * Recalculate import order based on detected dependencies
     */
    public List<MigrationManifestEntry> recalculateImportOrder(
            DependencyGraph graph, List<MigrationManifestEntry> entries) {

        // Get topological order
        List<String> orderedEntities = graph.getTopologicalOrder();

        // Map entries by entity name
        Map<String, List<MigrationManifestEntry>> entriesByEntity = new HashMap<>();
        Map<String, String> targetRefToEntity = new HashMap<>();

        for (MigrationManifestEntry entry : entries) {
            String entityName = MigrationTargetRefResolver.resolveEntityLabel(entry.targetRef);
            if (entityName != null) {
                entriesByEntity.computeIfAbsent(entityName, k -> new ArrayList<>()).add(entry);
                targetRefToEntity.put(entry.targetRef, entityName);
            }
        }

        // Build ordered list
        List<MigrationManifestEntry> orderedEntries = new ArrayList<>();
        for (String entity : orderedEntities) {
            List<MigrationManifestEntry> entityEntries = entriesByEntity.get(entity);
            if (entityEntries != null) {
                orderedEntries.addAll(entityEntries);
            }
        }

        // Add any entries not in the graph
        for (MigrationManifestEntry entry : entries) {
            if (!orderedEntries.contains(entry)) {
                orderedEntries.add(entry);
            }
        }

        logger.info("Recalculated import order: {} entries", orderedEntries.size());
        return orderedEntries;
    }

    /**
     * Check for circular dependencies
     */
    public boolean hasCircularDependency(DependencyGraph graph) {
        return graph.hasCircularDependency();
    }

    /**
     * Suggest resolution for circular dependencies
     */
    public List<String> suggestResolutionForCircularDependency(DependencyGraph graph) {
        List<String> suggestions = new ArrayList<>();

        List<List<String>> cycles = graph.getCircularDependencies();
        if (cycles.isEmpty()) {
            return suggestions;
        }

        for (List<String> cycle : cycles) {
            StringBuilder sb = new StringBuilder("Circular dependency detected: ");
            for (int i = 0; i < cycle.size() - 1; i++) {
                sb.append(cycle.get(i));
                if (i < cycle.size() - 2) {
                    sb.append(" -> ");
                }
            }
            sb.append(" -> ").append(cycle.get(0));

            suggestions.add(sb.toString());
            suggestions.add("Suggestion: Break the cycle by importing one entity first, " +
                    "then updating it with relationships after other entities are imported.");
        }

        return suggestions;
    }

    /**
     * Find matching entity in collection
     */
    private String findMatchingEntity(String entityName, Collection<String> entities) {
        String lowerEntity = entityName.toLowerCase();
        for (String entity : entities) {
            if (entity.toLowerCase().equals(lowerEntity) ||
                    entity.toLowerCase().replace(" ", "_").equals(lowerEntity) ||
                    entity.toLowerCase().replace("_", " ").equals(lowerEntity)) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Get cell value as string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
}
