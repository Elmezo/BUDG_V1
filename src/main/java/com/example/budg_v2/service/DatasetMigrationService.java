package com.example.budg_v2.service;

import com.example.budg_v2.bulk.EnvironmentMigrationConstants;
import com.example.budg_v2.bulk.relationships.RelationshipConfigRegistry;
import com.example.budg_v2.bulk.relationships.config.RelationshipConfig;
import com.example.budg_v2.bulk.relationships.config.EntityConfig;
import com.example.budg_v2.dao.DatasetDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.ExcelGenerator;
import com.example.budg_v2.util.GlossaryHierarchyExcelGenerator;
import com.example.budg_v2.util.RelationshipExcelGenerator;
import com.example.budg_v2.util.RoleExcelGenerator;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Service for handling Dataset migration operations
 * Generates ZIP files with Excel exports and {@link EnvironmentMigrationConstants#MANIFEST_FILE}.
 * <p>
 * <b>Export scopes (product choice)</b>
 * <ul>
 *   <li><b>Ecosystem (default)</b> — {@code allMode} without full-tenant: facets come from
 *       {@link #getAvailableFacets(String)} (mandatory/optional facets for the source facet).
 *       Object IDs are discovered from the selected source rows and related tables.</li>
 *   <li><b>Full tenant</b> — {@code allMode} + {@code fullTenantExport}: every
 *       verified facet allowed by {@link FacetClassificationService#isFacetAllowed(String)} is included and each facet uses
 *       {@link #getAllObjectIdsForFacet(String)} (entire table, non-deleted). Use when the goal is
 *       to clone all bulk-migratable objects, not only the ecosystem of one facet.</li>
 * </ul>
 */
public class DatasetMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(DatasetMigrationService.class);
    private final Gson gson = new Gson();
    private final ExcelGenerator excelGenerator;
    private final RelationshipExcelGenerator relationshipExcelGenerator;
    private final RoleExcelGenerator roleExcelGenerator;
    private final DatasetDAO datasetDAO;

    // Verified facets (including orgunit)
    private static final Set<String> VERIFIED_FACETS = Set.of(
            "system", "policy", "process", "dataset", "attribute", "glossary",
            "product", "client", "legal_entity", "project", "committee",
            "interface", "business_area", "capability", "regulation",
            "geography", "regulator", "regulatory_theme", "org_unit");

    /** Stable workbook names inside env_export.zip (metadata + manifest reference these). */
    private static final Map<String, String> ENV_STABLE_FACET_FILES = Map.ofEntries(
            Map.entry("system", "systems.xlsx"),
            Map.entry("policy", "policies.xlsx"),
            Map.entry("process", "processes.xlsx"),
            Map.entry("dataset", "datasets.xlsx"),
            Map.entry("attribute", "attributes.xlsx"),
            Map.entry("glossary", "glossary.xlsx"),
            Map.entry("product", "products.xlsx"),
            Map.entry("client", "clients.xlsx"),
            Map.entry("legal_entity", "legal_entities.xlsx"),
            Map.entry("project", "projects.xlsx"),
            Map.entry("committee", "committees.xlsx"),
            Map.entry("interface", "interfaces.xlsx"),
            Map.entry("business_area", "business_areas.xlsx"),
            Map.entry("capability", "capabilities.xlsx"),
            Map.entry("regulation", "regulations.xlsx"),
            Map.entry("geography", "geographies.xlsx"),
            Map.entry("regulator", "regulators.xlsx"),
            Map.entry("regulatory_theme", "regulatory_themes.xlsx"),
            Map.entry("org_unit", "org_units.xlsx"));

    // Facet relationships map - defines which facets are related to each source
    // facet
    // Format: sourceFacet -> {mandatory: [facets], optional: [facets]}
    private static final Map<String, FacetRelations> FACET_RELATIONSHIPS = new HashMap<>();

    static {
        // Complete relationship definitions based on user requirements
        // Format: mandatory (marked with D) and optional related facets

        // Dataset: System(D), Glossary(D), Roles, Client, Legal Entity, Product,
        // Policy, Process, Project, Attribute
        FACET_RELATIONSHIPS.put("dataset", new FacetRelations(
                Arrays.asList("system", "glossary"), // Mandatory (D)
                Arrays.asList("roles", "attribute", "client", "legal_entity", "product", "policy", "process",
                        "project")));

        // Attribute: Glossary(D), System(D), Dataset(D), Roles, Project, Process,
        // Policy, Source Attribute
        FACET_RELATIONSHIPS.put("attribute", new FacetRelations(
                Arrays.asList("dataset", "system", "glossary"), // Mandatory (D)
                Arrays.asList("roles", "project", "process", "policy", "source_attribute")));

        // System: Roles, Business Area, Capability, Policy, Process, Project, Client,
        // Glossary, Product, Legal Entity, Data Set
        FACET_RELATIONSHIPS.put("system", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "business_area", "capability", "policy", "process", "project", "client",
                        "glossary", "product", "legal_entity", "dataset")));

        // Glossary: Roles, Process, Policy, Project, System, Product, Client,
        // Capability, Business Area, Data Set, Attribute, Target Glossary
        FACET_RELATIONSHIPS.put("glossary", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "process", "policy", "project", "system", "product", "client", "capability",
                        "business_area", "dataset", "attribute", "target_glossary")));

        // Project: Roles, Policy, Attribute, Capability, Client, Dataset, Glossary,
        // Process, Product, Project, System, Regulation, Business Area
        FACET_RELATIONSHIPS.put("project", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "policy", "attribute", "capability", "client", "dataset", "glossary", "process",
                        "product", "system", "regulation", "business_area")));

        // Process: Roles, Business Area, Capability, Policy, Attribute, Client,
        // Dataset, Glossary, Legal Entity, Product, System, System Interface, Project,
        // Process Predecessors
        FACET_RELATIONSHIPS.put("process", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "business_area", "capability", "policy", "attribute", "client", "dataset",
                        "glossary", "legal_entity", "product", "system", "system_interface", "project",
                        "process_predecessors")));

        // Policy: Roles, Attribute, Client, Dataset, Glossary, Legal Entity, Process,
        // Product, System, Regulation, Business Area, Project, Target Policy
        FACET_RELATIONSHIPS.put("policy", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "attribute", "client", "dataset", "glossary", "legal_entity", "process",
                        "product", "system", "regulation", "business_area", "project", "target_policy")));

        // Committee: Roles, Capability, Target Committee
        FACET_RELATIONSHIPS.put("committee", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "capability", "target_committee")));

        // Interface: System(D), Roles, Process, Glossary
        FACET_RELATIONSHIPS.put("interface", new FacetRelations(
                Arrays.asList("system"), // Mandatory (D)
                Arrays.asList("roles", "process", "glossary")));

        // Business Area: Roles, Process, Glossary, System, Policy, Capability, Project,
        // Product
        FACET_RELATIONSHIPS.put("business_area", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "process", "glossary", "system", "policy", "capability", "project", "product")));

        // Capability: Roles, Committee, System, Glossary, Product, Client, Project,
        // Process, Business Area, Legal Entity
        FACET_RELATIONSHIPS.put("capability", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "committee", "system", "glossary", "product", "client", "project", "process",
                        "business_area", "legal_entity")));

        // Product: Roles, Capability, Dataset, Glossary, Policy, Process, Project,
        // Regulation, System, Legal Entity, Business Area, Client
        FACET_RELATIONSHIPS.put("product", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "capability", "dataset", "glossary", "policy", "process", "project",
                        "regulation", "system", "legal_entity", "business_area", "client")));

        // Legal Entity: Roles, Capability, Dataset, Policy, Process, System, Geography,
        // Product
        FACET_RELATIONSHIPS.put("legal_entity", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "capability", "dataset", "policy", "process", "system", "geography",
                        "product")));

        // Client: Roles, Capability, Dataset, Glossary, Policy, Process, Project,
        // System, Product
        FACET_RELATIONSHIPS.put("client", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "capability", "dataset", "glossary", "policy", "process", "project", "system",
                        "product")));

        // Regulation: Roles, Policy, Product, Project, Regulator, Regulatory Theme
        FACET_RELATIONSHIPS.put("regulation", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles", "policy", "product", "project", "regulator", "regulatory_theme")));

        // Geography: Legal Entity, Regulator
        FACET_RELATIONSHIPS.put("geography", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("legal_entity", "regulator")));

        // Regulator: Regulation, Geography
        FACET_RELATIONSHIPS.put("regulator", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("regulation", "geography")));

        // Regulatory Theme: Regulation
        FACET_RELATIONSHIPS.put("regulatory_theme", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("regulation")));

        // Org Unit: Roles
        FACET_RELATIONSHIPS.put("org_unit", new FacetRelations(
                Collections.emptyList(), // No mandatory dependencies
                Arrays.asList("roles")));
    }

    /**
     * Inner class to hold facet relationships
     */
    private static class FacetRelations {
        final List<String> mandatory;
        final List<String> optional;

        FacetRelations(List<String> mandatory, List<String> optional) {
            this.mandatory = mandatory;
            this.optional = optional;
        }
    }

    public DatasetMigrationService() {
        this.excelGenerator = new ExcelGenerator();
        this.relationshipExcelGenerator = new RelationshipExcelGenerator();
        this.roleExcelGenerator = new RoleExcelGenerator();
        this.datasetDAO = new DatasetDAO();
    }

    /**
     * Generate migration ZIP file (ecosystem scope when {@code allMode} is true).
     */
    public byte[] generateMigrationZip(String sourceFacet, List<Integer> objectIds,
            Set<String> selectedFacets, Set<String> selectedRelationships,
            boolean allMode) {
        return generateMigrationZip(sourceFacet, objectIds, selectedFacets, selectedRelationships, allMode, false);
    }

    /**
     * Generate migration ZIP file.
     *
     * @param fullTenantExport when {@code true} and {@code allMode} is {@code true}, exports all
     *                         allowed {@link #VERIFIED_FACETS} with full-table ID lists; ignored
     *                         when {@code allMode} is {@code false}.
     */
    public byte[] generateMigrationZip(String sourceFacet, List<Integer> objectIds,
            Set<String> selectedFacets, Set<String> selectedRelationships,
            boolean allMode, boolean fullTenantExport) {
        return generateMigrationZip(sourceFacet, objectIds, selectedFacets, selectedRelationships,
                allMode, fullTenantExport, false, null);
    }

    /**
     * @param environmentPackage when true, writes {@code metadata.json} and stable {@code .xlsx} names.
     * @param environmentHostHint optional label (e.g. server name) stored in metadata; may be null.
     */
    public byte[] generateMigrationZip(String sourceFacet, List<Integer> objectIds,
            Set<String> selectedFacets, Set<String> selectedRelationships,
            boolean allMode, boolean fullTenantExport, boolean environmentPackage,
            String environmentHostHint) {
        try {
            if (environmentPackage && (!allMode || !fullTenantExport)) {
                throw new IllegalArgumentException(
                        "Environment package export requires allMode and fullTenantExport");
            }
            // Normalize source facet name at the start
            String originalSourceFacet = sourceFacet;
            sourceFacet = normalizeFacetName(sourceFacet);
            if (!originalSourceFacet.equals(sourceFacet)) {
                logger.debug("[DatasetMigrationService] Normalized source facet from '{}' to '{}'",
                        originalSourceFacet, sourceFacet);
            }

            // Generate single timestamp for entire operation
            String timestamp = generateTimestamp();
            // system.out.println("[DatasetMigrationService] DEBUG: generateMigrationZip
            // starting - sourceFacet="
            // + sourceFacet + ", allMode=" + allMode + ", timestamp=" + timestamp);
            logger.info("Starting migration - sourceFacet: {}, mode: {}, fullTenant: {}, timestamp: {}",
                    sourceFacet, allMode ? "all" : "selected", allMode && fullTenantExport, timestamp);

            // Handle "all" mode - get all object IDs for source facet
            if (allMode || objectIds == null || objectIds.isEmpty()) {
                objectIds = getAllObjectIdsForFacet(sourceFacet);
                logger.info("All mode: Retrieved {} {} objects", objectIds.size(), sourceFacet);
            }

            // Validate mandatory dependencies for facets that have them
            if (!objectIds.isEmpty()) {
                ValidationResult validation = validateDependencies(sourceFacet, objectIds);
                if (!validation.isValid()) {
                    logger.error("Dependency validation failed for facet '{}': {}", sourceFacet,
                            validation.getErrors());
                    throw new IllegalStateException(
                            "Dependency validation failed: " + String.join(", ", validation.getErrors()));
                }
            }

            // Discover facets and relationships
            Set<String> facetsToInclude;
            Set<String> relationshipsToInclude;
            boolean includeRoles = false;

            if (allMode) {
                if (fullTenantExport) {
                    facetsToInclude = new HashSet<>(discoverAllowedFacetsForFullTenantExport());
                    logger.info("[DatasetMigrationService] Full-tenant export: {} facets", facetsToInclude.size());
                } else {
                    facetsToInclude = getAvailableFacets(sourceFacet);
                    // Remove "roles" from facetsToInclude as it's not a real facet
                    facetsToInclude.remove("roles");
                }
                // For all mode, include all relationships
                relationshipsToInclude = discoverRelationshipsForFacets(facetsToInclude);
                // In all mode, include role workbooks except for ENV ZIP (roles/person links stay in DB)
                includeRoles = !environmentPackage;
            } else {
                // Normalize selected facets to ensure case-insensitivity
                facetsToInclude = new HashSet<>();
                if (selectedFacets != null) {
                    for (String f : selectedFacets) {
                        String normalized = normalizeFacetName(f);
                        if ("roles".equals(normalized)) {
                            includeRoles = true;
                        } else {
                            facetsToInclude.add(normalized);
                        }
                    }
                }

                // Enforce mandatory facets server-side: add any missing mandatory facet
                // so the export is never produced with an incomplete dependency set.
                List<String> mandatoryFacets = getMandatoryFacets(sourceFacet);
                for (String mandatory : mandatoryFacets) {
                    String normalizedMandatory = normalizeFacetName(mandatory);
                    if (!facetsToInclude.contains(normalizedMandatory)) {
                        logger.warn(
                                "[DatasetMigrationService] Mandatory facet '{}' was missing from selected facets for source '{}' — auto-including it.",
                                normalizedMandatory, sourceFacet);
                        facetsToInclude.add(normalizedMandatory);
                    }
                }

                // Auto-discover relationships for selected facets
                if (!facetsToInclude.isEmpty()) {
                    relationshipsToInclude = discoverRelationshipsForFacets(facetsToInclude);
                } else {
                    relationshipsToInclude = selectedRelationships != null ? selectedRelationships : new HashSet<>();
                }
            }

            // Always include source facet in facetsToInclude (already normalized above)
            facetsToInclude.add(sourceFacet);

            // Get all object IDs for each facet (pseudo-facets merge into canonical entity keys)
            Map<String, List<Integer>> facetObjectIds = new HashMap<>();
            logger.info("[DatasetMigrationService] Discovering object IDs for facets: {}", facetsToInclude);
            for (String facet : facetsToInclude) {
                String norm = normalizeFacetName(facet);
                if ("roles".equals(norm)) {
                    continue;
                }
                String storageKey = canonicalFacetStorageKey(facet);
                if (!FacetClassificationService.isFacetAllowed(storageKey)) {
                    continue;
                }

                List<Integer> ids;
                if (allMode && fullTenantExport) {
                    ids = getAllObjectIdsForFacet(storageKey);
                } else {
                    ids = getFacetObjectIds(facet, objectIds, sourceFacet, allMode);
                }

                logger.info("[DatasetMigrationService] Facet '{}' (storageKey='{}') has {} objects",
                        facet, storageKey, ids.size());

                if ("geography".equals(storageKey) || "geographies".equals(facet)) {
                    logger.info(
                            "[DatasetMigrationService] Geography facet discovery - facet: {}, sourceFacet: {}, ids count: {}, ids: {}",
                            facet, sourceFacet, ids.size(), ids);
                }

                if (!ids.isEmpty()) {
                    facetObjectIds.merge(storageKey, new ArrayList<>(ids), DatasetMigrationService::mergeIdLists);
                } else {
                    logger.warn("[DatasetMigrationService] Facet '{}' returned empty object IDs list", facet);
                }
            }

            if (allMode && !fullTenantExport) {
                augmentSystemIdsFromDatasets(facetObjectIds);
            }

            // Fallback for mandatory facets in ecosystem "all mode": if a mandatory facet has no IDs,
            // try to get all objects of that facet type
            if (allMode && !fullTenantExport) {
                FacetRelations relations = FACET_RELATIONSHIPS.get(sourceFacet);
                if (relations != null) {
                    for (String mandatoryFacet : relations.mandatory) {
                        if (!FacetClassificationService.isFacetAllowed(mandatoryFacet)
                                || "roles".equals(mandatoryFacet)) {
                            continue;
                        }
                        String canon = canonicalFacetStorageKey(mandatoryFacet);
                        if (!facetObjectIds.containsKey(canon)) {
                            logger.info(
                                    "Mandatory facet {} returned empty, attempting to include all {} objects in all mode",
                                    mandatoryFacet, canon);
                            List<Integer> allIds = getAllObjectIdsForFacet(canon);
                            if (!allIds.isEmpty()) {
                                facetObjectIds.put(canon, allIds);
                                logger.info("Included {} {} objects as fallback for mandatory facet",
                                        allIds.size(), canon);
                            }
                        }
                    }
                }
            }

            // Create ZIP file
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            List<ManifestEntry> manifestEntries = new ArrayList<>();

            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // Generate facet Excel files
                for (Map.Entry<String, List<Integer>> entry : facetObjectIds.entrySet()) {
                    String facet = entry.getKey();
                    List<Integer> ids = entry.getValue();

                    try {
                        logger.info("[DatasetMigrationService] Retrieving data for facet '{}' with {} object IDs",
                                facet, ids.size());
                        List<Map<String, Object>> facetData = getFacetObjects(facet, ids);
                        logger.info("[DatasetMigrationService] Retrieved {} rows of data for facet '{}'",
                                facetData.size(), facet);

                        // Validation: Ensure we have data before generating Excel
                        if (facetData.isEmpty() && ids.size() > 0) {
                            logger.error(
                                    "[DatasetMigrationService] WARNING: No data retrieved for facet '{}' despite having {} object IDs. "
                                            +
                                            "This indicates a problem with the data retrieval query or the objects may have been deleted.",
                                    facet, ids.size());
                        }

                        if (!facetData.isEmpty()) {
                            byte[] excelData = excelGenerator.generateFacetExcel(facet, facetData, timestamp);
                            if (excelData != null) {
                                String fileName = environmentPackage
                                        ? getStableEnvFacetFileName(facet)
                                        : getFacetFileName(facet, timestamp);
                                zos.putNextEntry(new ZipEntry(fileName));
                                zos.write(excelData);
                                zos.closeEntry();

                                manifestEntries.add(new ManifestEntry(
                                        getTargetRefForFacet(facet), fileName));
                                logger.info("Added facet Excel: {} with {} rows", fileName, facetData.size());
                            } else {
                                logger.warn("[DatasetMigrationService] Excel generation returned null for facet '{}'",
                                        facet);
                            }
                        } else {
                            logger.warn(
                                    "[DatasetMigrationService] No data retrieved for facet '{}' with {} object IDs. " +
                                            "This may indicate a problem with data retrieval or the facet query.",
                                    facet, ids.size());
                        }
                    } catch (Exception e) {
                        logger.error("Error generating Excel for facet {}: {}, skipping", facet, e.getMessage(), e);
                        // Continue with other files
                    }
                }

                // Generate Glossary Hierarchy whenever datasets are in export scope
                {
                    List<Integer> datasetIds = facetObjectIds.get("dataset");
                    if (datasetIds != null && !datasetIds.isEmpty()) {
                        try {
                            GlossaryHierarchyExcelGenerator glossaryGen = new GlossaryHierarchyExcelGenerator();
                            byte[] glossaryHierarchyData = glossaryGen.generateGlossaryHierarchyExcel(datasetIds,
                                    timestamp);
                            if (glossaryHierarchyData != null) {
                                String fileName = environmentPackage
                                        ? "glossary_hierarchy.xlsx"
                                        : ("Glossary_Hierarchy" + timestamp + ".xlsx");
                                zos.putNextEntry(new ZipEntry(fileName));
                                zos.write(glossaryHierarchyData);
                                zos.closeEntry();
                                manifestEntries
                                        .add(new ManifestEntry("CatItemCategory_Hierarchy_bulkCreate", fileName));
                                logger.info("Added Glossary Hierarchy Excel: {} for {} datasets", fileName,
                                        datasetIds.size());
                            }
                        } catch (Exception e) {
                            logger.error("Error generating Glossary Hierarchy Excel: {}, skipping", e.getMessage(), e);
                            // Continue with other files
                        }
                    }
                }

                // Generate relationship Excel files
                for (String relationshipKey : relationshipsToInclude) {
                    try {
                        // Skip forbidden relationships (e.g., *_x_objectxpeople)
                        if (FacetClassificationService.isTableForbidden(relationshipKey)) {
                            logger.info("Skipping forbidden relationship: {}", relationshipKey);
                            continue;
                        }

                        RelationshipConfig config = RelationshipConfigRegistry.getConfig(relationshipKey);
                        if (config == null) {
                            logger.warn("Relationship config not found for: {}, skipping", relationshipKey);
                            continue;
                        }
                        if (EnvironmentCoreTruncator.isStakeholderObjectPeopleLinkTable(config.getTableName())) {
                            logger.info("Skipping person/stakeholder link relationship export: {}", relationshipKey);
                            continue;
                        }

                        // Determine if this is optional (Client, Legal Entity for Dataset)
                        boolean isOptional = isOptionalRelationship(relationshipKey, sourceFacet);

                        List<Map<String, Object>> relationshipData = getRelationshipObjects(
                                relationshipKey, objectIds, sourceFacet, isOptional, facetObjectIds);

                        if (!relationshipData.isEmpty()) {
                            byte[] excelData = relationshipExcelGenerator.generateRelationshipExcel(
                                    relationshipKey, relationshipData, timestamp, isOptional);
                            if (excelData != null) {
                                String fileName = environmentPackage
                                        ? getStableEnvRelationshipFileName(relationshipKey)
                                        : getRelationshipFileName(relationshipKey, timestamp);
                                zos.putNextEntry(new ZipEntry(fileName));
                                zos.write(excelData);
                                zos.closeEntry();

                                manifestEntries.add(new ManifestEntry(
                                        getTargetRefForRelationship(relationshipKey), fileName));
                                // system.out.println("[DatasetMigrationService] DEBUG: Added relationship
                                // Excel: "
                                // + fileName + " with " + relationshipData.size() + " rows");
                                logger.info("Added relationship Excel: {} with {} rows", fileName,
                                        relationshipData.size());
                            }
                        } else if (isOptional) {
                            logger.info("No relationships found for optional relationship: {}, skipping",
                                    relationshipKey);
                        }
                    } catch (Exception e) {
                        logger.error("Error generating Excel for relationship {}: {}, skipping",
                                relationshipKey, e.getMessage(), e);
                        // Continue with other files
                    }
                }

                // Generate Role Excel files (only if "roles" is selected)
                if (includeRoles) {
                    // Include source facet + all selected facets for roles
                    Set<String> facetsForRoles = new HashSet<>(facetObjectIds.keySet());
                    // Ensure source facet is included even if not in facetObjectIds
                    if (!facetsForRoles.contains(sourceFacet)) {
                        List<Integer> sourceIds = getFacetObjectIds(sourceFacet, objectIds, sourceFacet, allMode);
                        if (!sourceIds.isEmpty()) {
                            facetObjectIds.put(sourceFacet, sourceIds);
                            facetsForRoles.add(sourceFacet);
                        }
                    }

                    for (String facet : facetsForRoles) {
                        List<Integer> ids = facetObjectIds.get(facet);
                        if (ids == null || ids.isEmpty()) {
                            continue;
                        }

                        if (!roleExcelGenerator.hasRoleStakeholderTable(facet)) {
                            logger.debug("Skipping role Excel for facet {}: no stakeholder table in schema", facet);
                            continue;
                        }

                        try {
                            List<Map<String, Object>> roleData = roleExcelGenerator.getRoleData(facet, ids);
                            if (!roleData.isEmpty()) {
                                byte[] excelData = roleExcelGenerator.generateRoleExcel(facet, roleData, timestamp);
                                if (excelData != null) {
                                    String fileName = environmentPackage
                                            ? getStableEnvRoleFileName(facet)
                                            : getRoleFileName(facet, timestamp);
                                    zos.putNextEntry(new ZipEntry(fileName));
                                    zos.write(excelData);
                                    zos.closeEntry();

                                    manifestEntries.add(new ManifestEntry(
                                            getTargetRefForRole(facet), fileName));
                                    // system.out.println("[DatasetMigrationService] DEBUG: Added role Excel: " +
                                    // fileName
                                    // + " with " + roleData.size() + " roles");
                                    logger.info("Added role Excel: {} with {} roles", fileName, roleData.size());
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Error generating Role Excel for facet {}: {}, skipping", facet,
                                    e.getMessage(),
                                    e);
                        }
                    }
                }

                // Generate Glossary template if glossary facet is included
                if (facetObjectIds.containsKey("glossary")) {
                    try {
                        byte[] templateData = generateGlossaryTemplate();
                        if (templateData != null) {
                            String fileName = "Glossary_Template.xlsx";
                            zos.putNextEntry(new ZipEntry(fileName));
                            zos.write(templateData);
                            zos.closeEntry();
                            logger.info("Added Glossary template: {}", fileName);
                        }
                    } catch (Exception e) {
                        logger.error("Error generating Glossary template: {}, skipping", e.getMessage(), e);
                        // Continue with other files
                    }
                }

                if (environmentPackage) {
                    JsonObject metadata = buildEnvironmentMetadata(originalSourceFacet, environmentHostHint,
                            facetObjectIds.keySet(), manifestEntries);
                    zos.putNextEntry(new ZipEntry(EnvironmentMigrationConstants.METADATA_FILE));
                    zos.write(gson.toJson(metadata).getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }

                // Generate Manifest.json (array of { targetRef, fileEntry })
                JsonArray manifestArray = new JsonArray();
                for (ManifestEntry entry : manifestEntries) {
                    JsonObject entryObj = new JsonObject();
                    entryObj.addProperty("targetRef", entry.targetRef);
                    entryObj.addProperty("fileEntry", entry.fileEntry);
                    manifestArray.add(entryObj);
                }

                zos.putNextEntry(new ZipEntry(EnvironmentMigrationConstants.MANIFEST_FILE));
                zos.write(gson.toJson(manifestArray).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                logger.info("Migration ZIP generated successfully with {} files, timestamp: {}",
                        manifestEntries.size() + 1, timestamp);
            }

            return baos.toByteArray();

        } catch (Exception e) {
            logger.error("Error generating migration ZIP", e);
            throw new RuntimeException("Failed to generate migration ZIP: " + e.getMessage(), e);
        }
    }

    /**
     * BUDG full environment ZIP: full-tenant export, stable workbook names, {@code metadata.json}.
     *
     * @param sourceFacet UI facet (normalized); must be bulk-migrate-allowed
     * @param environmentHostHint optional server label for metadata (may be null)
     */
    public byte[] generateEnvironmentExportZip(String sourceFacet, String environmentHostHint) {
        if (sourceFacet == null || sourceFacet.isEmpty()) {
            throw new IllegalArgumentException("sourceFacet is required");
        }
        String norm = normalizeFacetName(sourceFacet);
        if (!FacetClassificationService.isFacetAllowed(norm)) {
            throw new IllegalArgumentException("Bulk migration not allowed for facet: " + sourceFacet);
        }
        return generateMigrationZip(norm, Collections.emptyList(), null, null, true, true, true,
                environmentHostHint);
    }

    /**
     * Discover all facets (including orgunit)
     */
    public Set<String> discoverAllFacets() {
        Set<String> facets = new HashSet<>(VERIFIED_FACETS);
        return facets;
    }

    /**
     * Facets included when {@code fullTenantExport} is used with {@code allMode}: every verified facet
     * that {@link FacetClassificationService#isFacetAllowed(String)} permits.
     */
    public Set<String> discoverAllowedFacetsForFullTenantExport() {
        Set<String> facets = new HashSet<>();
        for (String f : VERIFIED_FACETS) {
            if (FacetClassificationService.isFacetAllowed(f)) {
                facets.add(f);
            }
        }
        return facets;
    }

    /**
     * Get available facets for a source facet
     * Returns facets specific to the source facet based on FACET_RELATIONSHIPS map
     */
    public Set<String> getAvailableFacets(String sourceFacet) {
        Set<String> facets = new HashSet<>();

        // Normalize facet name
        String normalizedFacet = normalizeFacetName(sourceFacet);

        // Get relationships for this facet
        FacetRelations relations = FACET_RELATIONSHIPS.get(normalizedFacet);

        if (relations != null) {
            // Add mandatory facets
            facets.addAll(relations.mandatory);
            // Add optional facets
            facets.addAll(relations.optional);
        } else {
            // Fallback: if facet not in map, return all verified facets
            logger.warn("Facet {} not found in FACET_RELATIONSHIPS, using fallback", normalizedFacet);
            facets.addAll(VERIFIED_FACETS);
        }

        // Always include source facet (if not already included)
        facets.add(normalizedFacet);

        return facets;
    }

    /**
     * Get mandatory facets for a source facet
     */
    public List<String> getMandatoryFacets(String sourceFacet) {
        String normalizedFacet = normalizeFacetName(sourceFacet);
        FacetRelations relations = FACET_RELATIONSHIPS.get(normalizedFacet);
        if (relations != null) {
            return new ArrayList<>(relations.mandatory);
        }
        return new ArrayList<>();
    }

    /**
     * Normalize facet name (handle variations like legal_entity vs legalentity)
     */
    private String normalizeFacetName(String facet) {
        if (facet == null)
            return null;
        String normalized = facet.toLowerCase().replace("-", "_").replace(" ", "_");

        // Canonical mapping to match FACET_RELATIONSHIPS keys and database tables
        if (normalized.equals("datasets") || normalized.equals("data_sets") || normalized.equals("data-sets")) {
            return "dataset";
        }
        if (normalized.equals("attributes")) {
            return "attribute";
        }
        if (normalized.equals("systems")) {
            return "system";
        }
        if (normalized.equals("glossaries")) {
            return "glossary";
        }
        if (normalized.equals("policies")) {
            return "policy";
        }
        if (normalized.equals("processes")) {
            return "process";
        }
        if (normalized.equals("projects")) {
            return "project";
        }
        if (normalized.equals("products")) {
            return "product";
        }
        if (normalized.equals("clients")) {
            return "client";
        }
        if (normalized.equals("legalentity") || normalized.equals("legal_entity") || normalized.equals("legal")) {
            return "legal_entity";
        }
        if (normalized.equals("committees")) {
            return "committee";
        }
        if (normalized.equals("interfaces") || normalized.equals("system_interface")) {
            return "interface";
        }
        if (normalized.equals("businessarea") || normalized.equals("business_area")) {
            return "business_area";
        }
        if (normalized.equals("capabilities")) {
            return "capability";
        }
        if (normalized.equals("regulations")) {
            return "regulation";
        }
        if (normalized.equals("geographies")) {
            return "geography";
        }
        if (normalized.equals("regulators")) {
            return "regulator";
        }
        if (normalized.equals("regulatorytheme") || normalized.equals("regulatory_theme")) {
            return "regulatory_theme";
        }

        // Handle variations used in other parts of the code
        if (normalized.equals("processpredecessors") || normalized.equals("process_predecessors")) {
            return "process_predecessors";
        }
        if (normalized.equals("targetglossary") || normalized.equals("target_glossary")) {
            return "target_glossary";
        }
        if (normalized.equals("targetpolicy") || normalized.equals("target_policy")) {
            return "target_policy";
        }
        if (normalized.equals("targetcommittee") || normalized.equals("target_committee")) {
            return "target_committee";
        }
        if (normalized.equals("sourceattribute") || normalized.equals("source_attribute")) {
            return "source_attribute";
        }
        if (normalized.equals("orgunit") || normalized.equals("org_unit")) {
            return "org_unit";
        }

        return normalized;
    }

    /**
     * Collapse UI/pseudo facet labels to the storage key used in {@code facetObjectIds}
     * (matches {@link RelationshipConfig} entity names and Excel facet SQL).
     */
    private String canonicalFacetStorageKey(String facet) {
        String n = normalizeFacetName(facet);
        switch (n) {
            case "target_glossary":
                return "glossary";
            case "target_policy":
                return "policy";
            case "target_committee":
                return "committee";
            case "process_predecessors":
                return "process";
            case "system_interface":
                return "interface";
            default:
                return n;
        }
    }

    private static List<Integer> mergeIdLists(List<Integer> a, List<Integer> b) {
        LinkedHashSet<Integer> u = new LinkedHashSet<>();
        if (a != null) {
            u.addAll(a);
        }
        if (b != null) {
            u.addAll(b);
        }
        return new ArrayList<>(u);
    }

    private void augmentSystemIdsFromDatasets(Map<String, List<Integer>> facetObjectIds) {
        List<Integer> datasetIds = facetObjectIds.get("dataset");
        if (datasetIds == null || datasetIds.isEmpty()) {
            return;
        }
        List<Integer> fromDs = getSystemIdsForDatasets(datasetIds);
        if (fromDs.isEmpty()) {
            return;
        }
        List<Integer> systemIds = facetObjectIds.getOrDefault("system", new ArrayList<>());
        facetObjectIds.put("system", mergeIdLists(systemIds, fromDs));
        logger.info("[DatasetMigrationService] Merged {} system IDs from dataset MasterSource into system facet",
                fromDs.size());
    }

    private List<Integer> getTargetGlossaryIds(List<Integer> sourceGlossaryIds) {
        List<Integer> out = new ArrayList<>();
        if (sourceGlossaryIds == null || sourceGlossaryIds.isEmpty()) {
            return out;
        }
        Set<Integer> unique = new LinkedHashSet<>();
        String sql = "SELECT DISTINCT TargetGlossaryID AS tid FROM glossary_x_glossary WHERE SourceGlossaryID IN ("
                + buildInClause(sourceGlossaryIds.size()) + ") AND TargetGlossaryID IS NOT NULL AND TargetGlossaryID <> 0";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, sourceGlossaryIds, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("tid");
                    if (id > 0 && unique.add(id)) {
                        out.add(id);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error resolving target_glossary IDs: {}", e.getMessage());
        }
        return out;
    }

    private List<Integer> getTargetPolicyIds(List<Integer> sourcePolicyIds) {
        List<Integer> out = new ArrayList<>();
        if (sourcePolicyIds == null || sourcePolicyIds.isEmpty()) {
            return out;
        }
        Set<Integer> unique = new LinkedHashSet<>();
        String sql = "SELECT DISTINCT targetid AS tid FROM policy_x_policy WHERE sourceid IN ("
                + buildInClause(sourcePolicyIds.size()) + ") AND targetid IS NOT NULL AND targetid <> 0";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, sourcePolicyIds, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("tid");
                    if (id > 0 && unique.add(id)) {
                        out.add(id);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error resolving target_policy IDs: {}", e.getMessage());
        }
        return out;
    }

    private List<Integer> getTargetCommitteeIds(List<Integer> sourceCommitteeIds) {
        List<Integer> out = new ArrayList<>();
        if (sourceCommitteeIds == null || sourceCommitteeIds.isEmpty()) {
            return out;
        }
        Set<Integer> unique = new LinkedHashSet<>();
        String sql = "SELECT DISTINCT Target_ID AS tid FROM committee_x_committee WHERE Source_ID IN ("
                + buildInClause(sourceCommitteeIds.size()) + ") AND Target_ID IS NOT NULL AND Target_ID <> 0";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, sourceCommitteeIds, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("tid");
                    if (id > 0 && unique.add(id)) {
                        out.add(id);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error resolving target_committee IDs: {}", e.getMessage());
        }
        return out;
    }

    private List<Integer> getProcessPredecessorIds(List<Integer> sourceProcessIds) {
        List<Integer> out = new ArrayList<>();
        if (sourceProcessIds == null || sourceProcessIds.isEmpty()) {
            return out;
        }
        Set<Integer> unique = new LinkedHashSet<>();
        String sql = "SELECT DISTINCT targetprocess_id AS tid FROM process_x_process WHERE sourceprocess_id IN ("
                + buildInClause(sourceProcessIds.size())
                + ") AND targetprocess_id IS NOT NULL AND targetprocess_id <> 0";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, sourceProcessIds, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("tid");
                    if (id > 0 && unique.add(id)) {
                        out.add(id);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Error resolving process_predecessors IDs: {}", e.getMessage());
        }
        return out;
    }

    /**
     * Discover relationships for given facets
     */
    public Set<String> discoverRelationshipsForFacets(Set<String> facets) {
        Set<String> relationships = new HashSet<>();

        // Special handling: "source_attribute" is not a real facet, it's a relationship
        // Map it to "attributexattribute" relationship
        Set<String> normalizedFacets = new HashSet<>(facets);
        if (facets.contains("source_attribute") || facets.contains("sourceattribute")) {
            relationships.add("attributexattribute");
            // Also treat it as "attribute" for other relationship discovery
            normalizedFacets.add("attribute");
        }

        // Get all supported relationship keys
        Set<String> allRelationships = RelationshipConfigRegistry.getSupportedKeys();

        for (String relKey : allRelationships) {
            RelationshipConfig config = RelationshipConfigRegistry.getConfig(relKey);
            if (config != null) {
                String entityA = config.getEntityA().getName();
                String entityB = config.getEntityB().getName();

                // Normalize entity names for comparison
                String normalizedEntityA = normalizeFacetName(entityA);
                String normalizedEntityB = normalizeFacetName(entityB);

                // Check if either entity A or B facet is in the selected facets
                boolean entityAMatches = normalizedFacets.contains(normalizedEntityA) ||
                        normalizedFacets.contains(entityA);
                boolean entityBMatches = normalizedFacets.contains(normalizedEntityB) ||
                        normalizedFacets.contains(entityB);

                if (entityAMatches || entityBMatches) {
                    // Use config canonical key so registry aliases (e.g. legalxgeography) dedupe
                    relationships.add(config.getKey());
                }
            }
        }

        return relationships;
    }

    /**
     * Get available relationships for selected facets
     */
    public Set<String> getAvailableRelationships(Set<String> facets) {
        return discoverRelationshipsForFacets(facets);
    }

    // REMOVED: discoverRoleRelationships() method
    // Reason: Exports forbidden *_x_objectxpeople tables (BUDG rule violation)
    // People/roles cannot be migrated as facets or relationships

    /**
     * Validate dependencies for a given source facet and its object IDs.
     * Dispatches to the appropriate facet-specific checks based on the mandatory
     * relationships defined in FACET_RELATIONSHIPS.
     */
    public ValidationResult validateDependencies(String sourceFacet, List<Integer> objectIds) {
        List<String> errors = new ArrayList<>();
        String normalized = normalizeFacetName(sourceFacet);

        try (Connection conn = DatabaseConnection.getConnection()) {
            switch (normalized) {
                case "dataset":
                    validateDatasetDependencies(conn, objectIds, errors);
                    break;
                case "attribute":
                    validateAttributeDependencies(conn, objectIds, errors);
                    break;
                case "interface":
                    validateInterfaceDependencies(conn, objectIds, errors);
                    break;
                default:
                    // No mandatory DB-level pre-flight check for other facets
                    break;
            }
        } catch (SQLException e) {
            logger.error("Error validating dependencies for facet '{}'", normalized, e);
            errors.add("Error validating dependencies: " + e.getMessage());
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * @deprecated Use {@link #validateDependencies(String, List)} instead.
     */
    @Deprecated
    public ValidationResult validateDependencies(List<Integer> datasetIds) {
        return validateDependencies("dataset", datasetIds);
    }

    private void validateDatasetDependencies(Connection conn, List<Integer> datasetIds,
            List<String> errors) throws SQLException {
        // Check System dependencies
        String systemSql = "SELECT DISTINCT d.ID, d.PrimaryName " +
                "FROM dataset d " +
                "WHERE d.ID IN (" + buildInClause(datasetIds.size()) + ") " +
                "AND (d.MasterSource IS NULL OR d.MasterSource = 0)";

        try (PreparedStatement ps = conn.prepareStatement(systemSql)) {
            setParameters(ps, datasetIds, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    errors.add("Missing mandatory System dependency for Dataset: " +
                            rs.getString("PrimaryName") + " (ID: " + rs.getInt("ID") + ")");
                }
            }
        }

        // Check Glossary dependencies
        String glossarySql = "SELECT DISTINCT d.ID, d.PrimaryName " +
                "FROM dataset d " +
                "WHERE d.ID IN (" + buildInClause(datasetIds.size()) + ") " +
                "AND (d.glossary IS NULL OR d.glossary = 0)";

        try (PreparedStatement ps = conn.prepareStatement(glossarySql)) {
            setParameters(ps, datasetIds, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    errors.add("Missing mandatory Glossary dependency for Dataset: " +
                            rs.getString("PrimaryName") + " (ID: " + rs.getInt("ID") + ")");
                }
            }
        }
    }

    private void validateAttributeDependencies(Connection conn, List<Integer> attributeIds,
            List<String> errors) throws SQLException {
        // Attribute must belong to a Dataset
        String datasetSql = "SELECT DISTINCT a.ID, a.PrimaryName " +
                "FROM attribute a " +
                "WHERE a.ID IN (" + buildInClause(attributeIds.size()) + ") " +
                "AND (a.Dataset_ID IS NULL OR a.Dataset_ID = 0)";

        try (PreparedStatement ps = conn.prepareStatement(datasetSql)) {
            setParameters(ps, attributeIds, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    errors.add("Missing mandatory Dataset dependency for Attribute: " +
                            rs.getString("PrimaryName") + " (ID: " + rs.getInt("ID") + ")");
                }
            }
        }

        // Dataset linked to this attribute must have a System (MasterSource)
        String systemSql = "SELECT DISTINCT a.ID, a.PrimaryName " +
                "FROM attribute a " +
                "JOIN dataset d ON d.ID = a.Dataset_ID " +
                "WHERE a.ID IN (" + buildInClause(attributeIds.size()) + ") " +
                "AND a.Dataset_ID IS NOT NULL AND a.Dataset_ID != 0 " +
                "AND (d.MasterSource IS NULL OR d.MasterSource = 0)";

        try (PreparedStatement ps = conn.prepareStatement(systemSql)) {
            setParameters(ps, attributeIds, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    errors.add("Parent Dataset has no System (MasterSource) for Attribute: " +
                            rs.getString("PrimaryName") + " (ID: " + rs.getInt("ID") + ")");
                }
            }
        }

        // Attribute must have a Glossary
        String glossarySql = "SELECT DISTINCT a.ID, a.PrimaryName " +
                "FROM attribute a " +
                "WHERE a.ID IN (" + buildInClause(attributeIds.size()) + ") " +
                "AND (a.Glossary_ID IS NULL OR a.Glossary_ID = 0)";

        try (PreparedStatement ps = conn.prepareStatement(glossarySql)) {
            setParameters(ps, attributeIds, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    errors.add("Missing mandatory Glossary dependency for Attribute: " +
                            rs.getString("PrimaryName") + " (ID: " + rs.getInt("ID") + ")");
                }
            }
        }
    }

    private void validateInterfaceDependencies(Connection conn, List<Integer> interfaceIds,
            List<String> errors) throws SQLException {
        // Interface must have at least one system linked (Source_systemID or Target_systemID)
        String systemSql = "SELECT DISTINCT i.ID, i.PrimaryName " +
                "FROM `interface` i " +
                "WHERE i.ID IN (" + buildInClause(interfaceIds.size()) + ") " +
                "AND (i.Source_systemID IS NULL OR i.Source_systemID = 0) " +
                "AND (i.Target_systemID IS NULL OR i.Target_systemID = 0)";

        try (PreparedStatement ps = conn.prepareStatement(systemSql)) {
            setParameters(ps, interfaceIds, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    errors.add("Missing mandatory System dependency for Interface: " +
                            rs.getString("PrimaryName") + " (ID: " + rs.getInt("ID") +
                            ") — neither Source_systemID nor Target_systemID is set");
                }
            }
        }
    }

    /**
     * Get facet object IDs
     * 
     * @param facet       Target facet to discover
     * @param sourceIds   Original selected source IDs (for glossary source, these
     *                    are the user-selected IDs only)
     * @param sourceFacet Source facet name
     * @param allMode     Whether this is "all mode"
     * @return List of object IDs for the target facet
     */
    private List<Integer> getFacetObjectIds(String facet, List<Integer> sourceIds, String sourceFacet,
            boolean allMode) {
        // Normalize facet names for comparison
        String normalizedFacet = normalizeFacetName(facet);
        String normalizedSourceFacet = normalizeFacetName(sourceFacet);

        if (normalizedFacet.equals(normalizedSourceFacet)) {
            logger.debug("[DatasetMigrationService] Facet '{}' is source facet, returning {} source IDs",
                    facet, sourceIds != null ? sourceIds.size() : 0);
            return sourceIds != null ? sourceIds : new ArrayList<>();
        }

        // Special handling for Glossary facet - it can be related through multiple
        // paths
        // This will include both selected and related glossaries for the glossary file
        if (normalizedFacet.equals("glossary")) {
            return getGlossaryIdsForSourceFacet(sourceIds, sourceFacet, allMode);
        }

        // Special handling for Attribute facet when source is Glossary
        // Use only original selected glossary IDs (not related ones)
        if (normalizedFacet.equals("attribute") && normalizedSourceFacet.equals("glossary")) {
            return getAttributeIdsForGlossary(sourceIds);
        }

        // For other facets when source is glossary, ensure we use only original
        // selected IDs
        // (not the related glossaries discovered in getGlossaryIdsForSourceFacet)
        // This prevents cascading to related glossaries for non-glossary facets
        if (normalizedSourceFacet.equals("glossary") && !normalizedFacet.equals("glossary")) {
            logger.debug(
                    "[DatasetMigrationService] Discovering {} from glossary - using only {} original selected glossary IDs (not related ones)",
                    facet, sourceIds != null ? sourceIds.size() : 0);
        }

        // For Dataset dependencies
        if (normalizedSourceFacet.equals("dataset")) {
            if (normalizedFacet.equals("system")) {
                return getSystemIdsForDatasets(sourceIds);
            }
        }

        // Pseudo-facets: same underlying entity as glossary/policy/committee/process; IDs from link tables
        if ("target_glossary".equals(normalizedFacet)) {
            if (!"glossary".equals(normalizedSourceFacet) || sourceIds == null || sourceIds.isEmpty()) {
                return new ArrayList<>();
            }
            return getTargetGlossaryIds(sourceIds);
        }
        if ("target_policy".equals(normalizedFacet)) {
            if (!"policy".equals(normalizedSourceFacet) || sourceIds == null || sourceIds.isEmpty()) {
                return new ArrayList<>();
            }
            return getTargetPolicyIds(sourceIds);
        }
        if ("target_committee".equals(normalizedFacet)) {
            if (!"committee".equals(normalizedSourceFacet) || sourceIds == null || sourceIds.isEmpty()) {
                return new ArrayList<>();
            }
            return getTargetCommitteeIds(sourceIds);
        }
        if ("process_predecessors".equals(normalizedFacet)) {
            if (!"process".equals(normalizedSourceFacet) || sourceIds == null || sourceIds.isEmpty()) {
                return new ArrayList<>();
            }
            return getProcessPredecessorIds(sourceIds);
        }

        // For other facets, get related IDs
        List<Integer> relatedIds = getRelatedFacetIds(facet, sourceIds, sourceFacet);

        // Special logging for geography discovery
        if (normalizedFacet.equals("geography")) {
            logger.info("[DatasetMigrationService] Geography discovery - sourceFacet: {}, sourceIds count: {}, " +
                    "discovered geography IDs count: {}", sourceFacet,
                    sourceIds != null ? sourceIds.size() : 0, relatedIds.size());
        }

        return relatedIds;
    }

    /**
     * Get System IDs for Datasets
     */
    private List<Integer> getSystemIdsForDatasets(List<Integer> datasetIds) {
        List<Integer> systemIds = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT DISTINCT MasterSource FROM dataset " +
                    "WHERE ID IN (" + buildInClause(datasetIds.size()) + ") " +
                    "AND MasterSource IS NOT NULL AND MasterSource != 0";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setParameters(ps, datasetIds, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        systemIds.add(rs.getInt("MasterSource"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting System IDs for Datasets", e);
        }
        return systemIds;
    }

    /**
     * Get Glossary IDs for Datasets
     */
    private List<Integer> getGlossaryIdsForDatasets(List<Integer> datasetIds) {
        List<Integer> glossaryIds = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT DISTINCT glossary FROM dataset " +
                    "WHERE ID IN (" + buildInClause(datasetIds.size()) + ") " +
                    "AND glossary IS NOT NULL AND glossary != 0";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setParameters(ps, datasetIds, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        glossaryIds.add(rs.getInt("glossary"));
                    }
                }
            }

            // Also query from attribute.glossary relationships
            if (!datasetIds.isEmpty()) {
                String attrSql = "SELECT DISTINCT Glossary_ID FROM attribute " +
                        "WHERE Dataset_ID IN (" + buildInClause(datasetIds.size()) + ") " +
                        "AND Glossary_ID IS NOT NULL AND Glossary_ID != 0 " +
                        "AND DeletedDatetime IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(attrSql)) {
                    setParameters(ps, datasetIds, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int glossaryId = rs.getInt("Glossary_ID");
                            if (glossaryId > 0 && !glossaryIds.contains(glossaryId)) {
                                glossaryIds.add(glossaryId);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting Glossary IDs for Datasets", e);
        }
        return glossaryIds;
    }

    /**
     * Get Attribute IDs for Glossary
     * Retrieves attributes directly linked to glossaries via attribute.Glossary_ID
     */
    private List<Integer> getAttributeIdsForGlossary(List<Integer> glossaryIds) {
        List<Integer> attributeIds = new ArrayList<>();
        if (glossaryIds == null || glossaryIds.isEmpty()) {
            return attributeIds;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT DISTINCT ID FROM attribute " +
                    "WHERE Glossary_ID IN (" + buildInClause(glossaryIds.size()) + ") " +
                    "AND Glossary_ID IS NOT NULL AND Glossary_ID != 0 " +
                    "AND DeletedDatetime IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setParameters(ps, glossaryIds, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        attributeIds.add(rs.getInt("ID"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting Attribute IDs for Glossary: {}", e.getMessage(), e);
        }
        return attributeIds;
    }

    /**
     * Get Glossary IDs for any source facet
     * Retrieves glossary IDs from multiple sources:
     * - Direct foreign key relationships (dataset.glossary, attribute.Glossary_ID)
     * - Relationship tables via RelationshipConfigRegistry
     * - Fallback to all glossaries in "all mode" if no relationships found
     */
    private List<Integer> getGlossaryIdsForSourceFacet(List<Integer> sourceIds, String sourceFacet, boolean allMode) {
        List<Integer> glossaryIds = new ArrayList<>();
        Set<Integer> uniqueIds = new HashSet<>();

        if (sourceIds == null || sourceIds.isEmpty()) {
            // In all mode, if no source IDs, return all glossaries
            if (allMode) {
                return getAllObjectIdsForFacet("glossary");
            }
            return glossaryIds;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String normalizedSourceFacet = normalizeFacetName(sourceFacet);

            // 1. Query direct foreign key relationships
            if (normalizedSourceFacet.equals("dataset")) {
                // From dataset.glossary
                String sql = "SELECT DISTINCT glossary FROM dataset " +
                        "WHERE ID IN (" + buildInClause(sourceIds.size()) + ") " +
                        "AND glossary IS NOT NULL AND glossary != 0";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    setParameters(ps, sourceIds, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("glossary");
                            if (id > 0 && uniqueIds.add(id)) {
                                glossaryIds.add(id);
                            }
                        }
                    }
                }

                // From attribute.Glossary_ID (attributes linked to these datasets)
                String attrSql = "SELECT DISTINCT Glossary_ID FROM attribute " +
                        "WHERE Dataset_ID IN (" + buildInClause(sourceIds.size()) + ") " +
                        "AND Glossary_ID IS NOT NULL AND Glossary_ID != 0 " +
                        "AND DeletedDatetime IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(attrSql)) {
                    setParameters(ps, sourceIds, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("Glossary_ID");
                            if (id > 0 && uniqueIds.add(id)) {
                                glossaryIds.add(id);
                            }
                        }
                    }
                }
            } else if (normalizedSourceFacet.equals("attribute")) {
                // From attribute.Glossary_ID
                String sql = "SELECT DISTINCT Glossary_ID FROM attribute " +
                        "WHERE ID IN (" + buildInClause(sourceIds.size()) + ") " +
                        "AND Glossary_ID IS NOT NULL AND Glossary_ID != 0 " +
                        "AND DeletedDatetime IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    setParameters(ps, sourceIds, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("Glossary_ID");
                            if (id > 0 && uniqueIds.add(id)) {
                                glossaryIds.add(id);
                            }
                        }
                    }
                }
            } else if (normalizedSourceFacet.equals("glossary")) {
                // When source facet is glossary, start with the selected glossary IDs
                for (Integer id : sourceIds) {
                    if (id > 0 && uniqueIds.add(id)) {
                        glossaryIds.add(id);
                    }
                }

                // Also discover related glossaries from glossary_x_glossary (both directions)
                // This ensures the glossary file includes both selected and related glossaries
                // Query both directions: where selected glossary is source, and where it's
                // target
                String relatedSql = "SELECT DISTINCT TargetGlossaryID AS RelatedGlossaryID " +
                        "FROM glossary_x_glossary " +
                        "WHERE SourceGlossaryID IN (" + buildInClause(sourceIds.size()) + ") " +
                        "AND TargetGlossaryID IS NOT NULL " +
                        "UNION " +
                        "SELECT DISTINCT SourceGlossaryID AS RelatedGlossaryID " +
                        "FROM glossary_x_glossary " +
                        "WHERE TargetGlossaryID IN (" + buildInClause(sourceIds.size()) + ") " +
                        "AND SourceGlossaryID IS NOT NULL";

                try (PreparedStatement ps = conn.prepareStatement(relatedSql)) {
                    // Set parameters for first part (SourceGlossaryID)
                    for (int i = 0; i < sourceIds.size(); i++) {
                        ps.setInt(i + 1, sourceIds.get(i));
                    }
                    // Set parameters for second part (TargetGlossaryID) - same IDs
                    for (int i = 0; i < sourceIds.size(); i++) {
                        ps.setInt(sourceIds.size() + i + 1, sourceIds.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("RelatedGlossaryID");
                            if (id > 0 && uniqueIds.add(id)) {
                                glossaryIds.add(id);
                                logger.debug("Added related glossary ID {} from glossary_x_glossary", id);
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error querying related glossaries from glossary_x_glossary: {}", e.getMessage());
                }
            }

            // 2. Query relationship tables via RelationshipConfigRegistry
            Set<String> allRelationshipKeys = RelationshipConfigRegistry.getSupportedKeys();
            for (String relKey : allRelationshipKeys) {
                RelationshipConfig config = RelationshipConfigRegistry.getConfig(relKey);
                if (config == null) {
                    continue;
                }

                EntityConfig entityA = config.getEntityA();
                EntityConfig entityB = config.getEntityB();
                String entityAName = normalizeFacetName(entityA.getName());
                String entityBName = normalizeFacetName(entityB.getName());

                // Check if this relationship connects sourceFacet to glossary
                boolean sourceIsA = entityAName.equals(normalizedSourceFacet);
                boolean sourceIsB = entityBName.equals(normalizedSourceFacet);

                if ((sourceIsA && entityBName.equals("glossary")) || (sourceIsB && entityAName.equals("glossary"))) {
                    String sourceIdColumn = sourceIsA ? config.getEntityAIdColumn() : config.getEntityBIdColumn();
                    String targetIdColumn = entityAName.equals("glossary") ? config.getEntityAIdColumn()
                            : config.getEntityBIdColumn();
                    String tableName = config.getTableName();

                    String sql = "SELECT DISTINCT " + targetIdColumn + " FROM " + tableName +
                            " WHERE " + sourceIdColumn + " IN (" + buildInClause(sourceIds.size()) + ")" +
                            " AND " + targetIdColumn + " IS NOT NULL";

                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        setParameters(ps, sourceIds, 1);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                int id = rs.getInt(targetIdColumn);
                                if (id > 0 && uniqueIds.add(id)) {
                                    glossaryIds.add(id);
                                }
                            }
                        }
                    } catch (SQLException e) {
                        logger.warn("Error querying relationship {} for {} -> glossary: {}",
                                relKey, sourceFacet, e.getMessage());
                        // Continue with other relationships
                    }
                }
            }

            // 3. Fallback: In "all mode", if no glossary IDs found and glossary is
            // mandatory/optional for this facet,
            // include all glossaries
            if (glossaryIds.isEmpty() && allMode) {
                FacetRelations relations = FACET_RELATIONSHIPS.get(normalizedSourceFacet);
                if (relations != null &&
                        (relations.mandatory.contains("glossary") || relations.optional.contains("glossary"))) {
                    logger.info("No glossary relationships found for {} in all mode, including all glossaries",
                            sourceFacet);
                    return getAllObjectIdsForFacet("glossary");
                }
            }

        } catch (SQLException e) {
            logger.error("Error getting Glossary IDs for source facet {}: {}", sourceFacet, e.getMessage(), e);
        }

        return glossaryIds;
    }

    /**
     * Get related facet IDs by querying relationship tables
     */
    private List<Integer> getRelatedFacetIds(String facet, List<Integer> sourceIds, String sourceFacet) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            logger.debug("[DatasetMigrationService] getRelatedFacetIds: sourceIds is empty for {} -> {}",
                    sourceFacet, facet);
            return new ArrayList<>();
        }

        List<Integer> relatedIds = new ArrayList<>();
        Set<Integer> uniqueIds = new HashSet<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Normalize facet names for comparison
            String normalizedSourceFacet = normalizeFacetName(sourceFacet);
            String normalizedTargetFacet = normalizeFacetName(facet);

            logger.debug(
                    "[DatasetMigrationService] getRelatedFacetIds: Discovering {} IDs from {} (normalized: {} -> {})",
                    facet, sourceFacet, normalizedSourceFacet, normalizedTargetFacet);

            // Get all relationship configs
            Set<String> allRelationshipKeys = RelationshipConfigRegistry.getSupportedKeys();

            for (String relKey : allRelationshipKeys) {
                RelationshipConfig config = RelationshipConfigRegistry.getConfig(relKey);
                if (config == null) {
                    continue;
                }

                EntityConfig entityA = config.getEntityA();
                EntityConfig entityB = config.getEntityB();

                // Normalize entity names for comparison
                String entityAName = normalizeFacetName(entityA.getName());
                String entityBName = normalizeFacetName(entityB.getName());

                // Check if this relationship connects sourceFacet to target facet
                boolean sourceIsA = entityAName.equals(normalizedSourceFacet);
                boolean sourceIsB = entityBName.equals(normalizedSourceFacet);
                boolean targetIsA = entityAName.equals(normalizedTargetFacet);
                boolean targetIsB = entityBName.equals(normalizedTargetFacet);

                if ((sourceIsA && targetIsB) || (sourceIsB && targetIsA)) {
                    // This relationship connects source to target
                    String sourceIdColumn = sourceIsA ? config.getEntityAIdColumn() : config.getEntityBIdColumn();
                    String targetIdColumn = targetIsA ? config.getEntityAIdColumn() : config.getEntityBIdColumn();
                    String tableName = config.getTableName();

                    // Build query to get target IDs
                    String sql = "SELECT DISTINCT " + targetIdColumn + " FROM " + tableName +
                            " WHERE " + sourceIdColumn + " IN (" + buildInClause(sourceIds.size()) + ")" +
                            " AND " + targetIdColumn + " IS NOT NULL";

                    logger.debug("[DatasetMigrationService] Querying relationship {}: {}", relKey, sql);

                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        setParameters(ps, sourceIds, 1);
                        try (ResultSet rs = ps.executeQuery()) {
                            int count = 0;
                            while (rs.next()) {
                                int id = rs.getInt(targetIdColumn);
                                if (id > 0 && uniqueIds.add(id)) {
                                    relatedIds.add(id);
                                    count++;
                                }
                            }
                            if (count > 0) {
                                logger.debug("[DatasetMigrationService] Relationship {} found {} {} IDs",
                                        relKey, count, facet);
                            }
                        }
                    } catch (SQLException e) {
                        logger.warn("Error querying relationship {} for {} -> {}: {}",
                                relKey, sourceFacet, facet, e.getMessage(), e);
                        // Continue with other relationships
                    }
                }
            }

            // Also handle direct foreign key relationships (like dataset -> attribute,
            // dataset -> system)
            if (normalizedSourceFacet.equals("dataset") && normalizedTargetFacet.equals("attribute")) {
                String sql = "SELECT DISTINCT ID FROM attribute " +
                        "WHERE Dataset_ID IN (" + buildInClause(sourceIds.size()) + ") " +
                        "AND DeletedDatetime IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    setParameters(ps, sourceIds, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("ID");
                            if (id > 0 && uniqueIds.add(id)) {
                                relatedIds.add(id);
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error querying attribute for dataset: {}", e.getMessage());
                }
            }

            // Handle dataset -> system relationship via MasterSource foreign key
            if (normalizedSourceFacet.equals("dataset") && normalizedTargetFacet.equals("system")) {
                String sql = "SELECT DISTINCT MasterSource FROM dataset " +
                        "WHERE ID IN (" + buildInClause(sourceIds.size()) + ") " +
                        "AND MasterSource IS NOT NULL AND MasterSource != 0 " +
                        "AND DeletedDatetime IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    setParameters(ps, sourceIds, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("MasterSource");
                            if (id > 0 && uniqueIds.add(id)) {
                                relatedIds.add(id);
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error querying system for dataset: {}", e.getMessage());
                }
            }

            // Handle attribute -> dataset (reverse)
            if (normalizedSourceFacet.equals("attribute") && normalizedTargetFacet.equals("dataset")) {
                String sql = "SELECT DISTINCT Dataset_ID FROM attribute " +
                        "WHERE ID IN (" + buildInClause(sourceIds.size()) + ") " +
                        "AND Dataset_ID IS NOT NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    setParameters(ps, sourceIds, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("Dataset_ID");
                            if (id > 0 && uniqueIds.add(id)) {
                                relatedIds.add(id);
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error querying dataset for attribute: {}", e.getMessage());
                }
            }

            // Special handling for glossary facet - query direct FK relationships
            if (normalizedTargetFacet.equals("glossary")) {
                // From dataset.glossary
                if (normalizedSourceFacet.equals("dataset")) {
                    String sql = "SELECT DISTINCT glossary FROM dataset " +
                            "WHERE ID IN (" + buildInClause(sourceIds.size()) + ") " +
                            "AND glossary IS NOT NULL AND glossary != 0";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        setParameters(ps, sourceIds, 1);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                int id = rs.getInt("glossary");
                                if (id > 0 && uniqueIds.add(id)) {
                                    relatedIds.add(id);
                                }
                            }
                        }
                    } catch (SQLException e) {
                        logger.warn("Error querying glossary from dataset: {}", e.getMessage());
                    }
                }

                // From attribute.Glossary_ID
                if (normalizedSourceFacet.equals("attribute")) {
                    String sql = "SELECT DISTINCT Glossary_ID FROM attribute " +
                            "WHERE ID IN (" + buildInClause(sourceIds.size()) + ") " +
                            "AND Glossary_ID IS NOT NULL AND Glossary_ID != 0 " +
                            "AND DeletedDatetime IS NULL";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        setParameters(ps, sourceIds, 1);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                int id = rs.getInt("Glossary_ID");
                                if (id > 0 && uniqueIds.add(id)) {
                                    relatedIds.add(id);
                                }
                            }
                        }
                    } catch (SQLException e) {
                        logger.warn("Error querying glossary from attribute: {}", e.getMessage());
                    }
                }

                // Also check for glossary_x_* relationship tables (if they exist)
                // This is handled by the RelationshipConfigRegistry loop above, but we can also
                // try direct queries for common patterns
                // Note: Most glossary relationships are handled via direct FKs or
                // RelationshipConfigRegistry
            }

            // Special handling for dataset facet when source is glossary - query direct FK
            // relationships
            if (normalizedTargetFacet.equals("dataset") && normalizedSourceFacet.equals("glossary")) {
                // Direct relationship: datasets with glossary FK
                String directSql = "SELECT DISTINCT ID FROM dataset " +
                        "WHERE glossary IN (" + buildInClause(sourceIds.size()) + ") " +
                        "AND glossary IS NOT NULL AND glossary != 0 " +
                        "AND DeletedDatetime IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(directSql)) {
                    setParameters(ps, sourceIds, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("ID");
                            if (id > 0 && uniqueIds.add(id)) {
                                relatedIds.add(id);
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error querying dataset from glossary (direct): {}", e.getMessage());
                }

                // Indirect relationship: datasets via attributes that have Glossary_ID
                String indirectSql = "SELECT DISTINCT Dataset_ID FROM attribute " +
                        "WHERE Glossary_ID IN (" + buildInClause(sourceIds.size()) + ") " +
                        "AND Glossary_ID IS NOT NULL AND Glossary_ID != 0 " +
                        "AND Dataset_ID IS NOT NULL " +
                        "AND DeletedDatetime IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(indirectSql)) {
                    setParameters(ps, sourceIds, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("Dataset_ID");
                            if (id > 0 && uniqueIds.add(id)) {
                                relatedIds.add(id);
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error querying dataset from glossary (via attributes): {}", e.getMessage());
                }
            }

        } catch (SQLException e) {
            logger.error("Error getting related facet IDs for {} -> {}: {}",
                    sourceFacet, facet, e.getMessage(), e);
        }

        logger.info("[DatasetMigrationService] getRelatedFacetIds: Found {} {} IDs from {} source IDs",
                relatedIds.size(), facet, sourceIds.size());

        return relatedIds;
    }

    /**
     * Get all object IDs for a facet (for "all" mode)
     */
    private List<Integer> getAllObjectIdsForFacet(String facet) {
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = getSelectAllQueryForFacet(facet);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getInt("ID"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting all object IDs for facet {}: {}", facet, e.getMessage(), e);
        }
        return ids;
    }

    /**
     * Get SELECT query for all objects of a facet
     */
    private String getSelectAllQueryForFacet(String facet) {
        // Use normalized name for consistent matching
        String normalizedFacet = normalizeFacetName(facet);

        switch (normalizedFacet) {
            case "dataset":
                return "SELECT ID FROM dataset WHERE DeletedDatetime IS NULL";
            case "system":
                return "SELECT id AS ID FROM system WHERE Deleted_datetime IS NULL";
            case "glossary":
                return "SELECT ID FROM glossary WHERE Deleted_datetime IS NULL";
            case "policy":
                return "SELECT ID FROM policy WHERE DeletedDatetime IS NULL";
            case "process":
                return "SELECT id AS ID FROM process WHERE deleteddatetime IS NULL";
            case "project":
                return "SELECT id AS ID FROM project WHERE deletedatetime IS NULL";
            case "attribute":
                return "SELECT ID FROM attribute WHERE DeletedDatetime IS NULL";
            case "product":
                return "SELECT id AS ID FROM product WHERE deleteddatetime IS NULL";
            case "client":
                return "SELECT ID FROM client WHERE DeleteDatetime IS NULL";
            case "legal_entity":
                return "SELECT ID FROM legal WHERE DeleteDatetime IS NULL";
            case "interface":
                return "SELECT id AS ID FROM `interface` WHERE deleted_datetime IS NULL";
            case "business_area":
                return "SELECT ID AS ID FROM business_area WHERE deletedatetime IS NULL";
            case "capability":
                return "SELECT ID AS ID FROM capability WHERE DeletedDatetime IS NULL";
            case "committee":
                return "SELECT ID AS ID FROM committee WHERE DeleteDatetime IS NULL";
            case "regulation":
                return "SELECT ID AS ID FROM regulation WHERE DeletedDatetime IS NULL";
            case "geography":
                return "SELECT id AS ID FROM geography WHERE DeletedDatetime IS NULL";
            case "regulator":
                return "SELECT id AS ID FROM regulator WHERE DeletedDatetime IS NULL";
            case "regulatory_theme":
                return "SELECT id AS ID FROM regulatorytheme WHERE DeletedDatetime IS NULL";
            case "org_unit":
                return "SELECT ID FROM org_unit WHERE deleted_Date IS NULL";
            default:
                // Generic query - escape table name with backticks for safety
                return "SELECT ID FROM `" + normalizedFacet + "` WHERE DeletedDatetime IS NULL";
        }
    }

    /**
     * Get facet objects with all required columns
     */
    private List<Map<String, Object>> getFacetObjects(String facet, List<Integer> ids) {
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return excelGenerator.getFacetData(facet, ids);
        } catch (Exception e) {
            logger.error("Error getting facet objects for {}: {}", facet, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Get relationship objects
     */
    private List<Map<String, Object>> getRelationshipObjects(String relationshipKey,
            List<Integer> sourceIds,
            String sourceFacet,
            boolean optional,
            Map<String, List<Integer>> facetObjectIds) {
        RelationshipConfig config = RelationshipConfigRegistry.getConfig(relationshipKey);
        if (config == null) {
            logger.warn("Relationship config not found: {}", relationshipKey);
            return new ArrayList<>();
        }

        try {
            EntityConfig entityA = config.getEntityA();
            EntityConfig entityB = config.getEntityB();

            // Get IDs for both entities if they're in facetObjectIds
            List<Integer> entityAIds = facetObjectIds != null
                    ? facetObjectIds.getOrDefault(entityA.getName(), new ArrayList<>())
                    : new ArrayList<>();
            List<Integer> entityBIds = facetObjectIds != null
                    ? facetObjectIds.getOrDefault(entityB.getName(), new ArrayList<>())
                    : new ArrayList<>();

            List<Map<String, Object>> relationships;

            // Use bidirectional query if both entities have IDs (for comprehensive
            // coverage)
            if (!entityAIds.isEmpty() && !entityBIds.isEmpty()) {
                logger.info("Using bidirectional query for relationship {} with {} entityA IDs and {} entityB IDs",
                        relationshipKey, entityAIds.size(), entityBIds.size());
                relationships = relationshipExcelGenerator.getRelationshipData(
                        relationshipKey, entityAIds, entityBIds, entityA.getName(), entityB.getName());
            } else {
                // Fallback to unidirectional query based on source facet
                relationships = relationshipExcelGenerator.getRelationshipData(
                        relationshipKey, sourceIds, sourceFacet);
            }

            if (relationships.isEmpty() && optional) {
                logger.info("No relationships found for optional relationship: {}, skipping", relationshipKey);
            }

            return relationships;
        } catch (Exception e) {
            if (optional) {
                logger.warn("Error retrieving optional relationship {}: {}, skipping",
                        relationshipKey, e.getMessage());
                return new ArrayList<>();
            } else {
                logger.error("Error retrieving relationship {}: {}", relationshipKey, e.getMessage(), e);
                throw new RuntimeException("Error retrieving relationship: " + relationshipKey, e);
            }
        }
    }

    /**
     * Check if relationship is optional (Client, Legal Entity for Dataset)
     */
    private boolean isOptionalRelationship(String relationshipKey, String sourceFacet) {
        if (sourceFacet.equals("dataset")) {
            return relationshipKey.equals("datasetxclient") ||
                    relationshipKey.equals("datasetxlegalentity");
        }
        return false;
    }

    /**
     * Generate timestamp in format ddMMyyHHmmss
     */
    private String generateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyHHmmss");
        return sdf.format(new Date());
    }

    private String getStableEnvFacetFileName(String facet) {
        String key = canonicalFacetStorageKey(facet);
        return ENV_STABLE_FACET_FILES.getOrDefault(key, key.replace(' ', '_') + ".xlsx");
    }

    private String getStableEnvRelationshipFileName(String relationshipKey) {
        String slug = relationshipKey == null ? "unknown"
                : relationshipKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return "rel_" + slug + ".xlsx";
    }

    private String getStableEnvRoleFileName(String facet) {
        String n = normalizeFacetName(facet).replace(' ', '_');
        return "roles_" + n + ".xlsx";
    }

    private JsonObject buildEnvironmentMetadata(String rawSourceFacet, String hostHint,
            Set<String> facetKeys, List<ManifestEntry> manifestEntries) {
        JsonObject meta = new JsonObject();
        meta.addProperty("packageType", EnvironmentMigrationConstants.PACKAGE_TYPE);
        meta.addProperty("schemaVersion", EnvironmentMigrationConstants.SCHEMA_VERSION);
        meta.addProperty("excelNaming", EnvironmentMigrationConstants.EXCEL_NAMING_STABLE);
        meta.addProperty("manifestVersion", 1);
        meta.addProperty("exportedAt", Instant.now().toString());
        if (rawSourceFacet != null && !rawSourceFacet.isEmpty()) {
            meta.addProperty("sourceFacet", rawSourceFacet);
        }
        if (hostHint != null && !hostHint.isEmpty()) {
            meta.addProperty("sourceHost", hostHint);
        }
        JsonArray facets = new JsonArray();
        new TreeSet<>(facetKeys).forEach(facets::add);
        meta.add("facetsIncluded", facets);
        JsonArray files = new JsonArray();
        for (ManifestEntry e : manifestEntries) {
            JsonObject o = new JsonObject();
            o.addProperty("targetRef", e.targetRef);
            o.addProperty("fileEntry", e.fileEntry);
            files.add(o);
        }
        meta.add("files", files);
        return meta;
    }

    /**
     * Get facet file name
     */
    private String getFacetFileName(String facet, String timestamp) {
        String facetName = getFacetDisplayName(facet);
        return facetName + timestamp + ".xlsx";
    }

    /**
     * Get relationship file name
     */
    private String getRelationshipFileName(String relationshipKey, String timestamp) {
        RelationshipConfig config = RelationshipConfigRegistry.getConfig(relationshipKey);
        if (config != null) {
            String entityAName = getFacetDisplayName(config.getEntityA().getName());
            String entityBName = getFacetDisplayName(config.getEntityB().getName());
            return entityAName + "_" + entityBName + timestamp + ".xlsx";
        }
        return relationshipKey + timestamp + ".xlsx";
    }

    /**
     * Get role file name
     */
    private String getRoleFileName(String facet, String timestamp) {
        String facetName = getFacetDisplayName(facet);
        return facetName + "_Role_" + timestamp + ".xlsx";
    }

    /**
     * Get facet display name for file naming
     */
    private String getFacetDisplayName(String facet) {
        String normalized = normalizeFacetName(facet);
        Map<String, String> displayNames = Map.ofEntries(
                Map.entry("dataset", "Data_Set"),
                Map.entry("legal_entity", "Legal_Entity"),
                Map.entry("business_area", "Business_Area"),
                Map.entry("regulatory_theme", "Regulatory_Theme"),
                Map.entry("org_unit", "Org_Unit"));
        return displayNames.getOrDefault(normalized, capitalizeFirst(normalized));
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
     * Get targetRef for Role file (BUDG-compatible mapping)
     */
    private String getTargetRefForRole(String facet) {
        String budgBaseName = getBudgTargetRefName(facet);
        // Pattern: <BudgFacetName>XObjectXIP_bulkCreate
        return budgBaseName + "XObjectXIP_bulkCreate";
    }

    /**
     * Get targetRef for facet (using BUDG naming conventions)
     */
    private String getTargetRefForFacet(String facet) {
        // Map facets to BUDG targetRef names
        Map<String, String> budgTargetRefMap = new HashMap<>();
        budgTargetRefMap.put("dataset", "CatalogueItem");
        budgTargetRefMap.put("glossary", "CatItemCategory");
        budgTargetRefMap.put("attribute", "CatItemComponent");
        // Other facets use their capitalized name
        budgTargetRefMap.put("system", "System");
        budgTargetRefMap.put("policy", "Policy");
        budgTargetRefMap.put("process", "Process");
        budgTargetRefMap.put("project", "Project");
        budgTargetRefMap.put("product", "Product");
        budgTargetRefMap.put("client", "Client");
        budgTargetRefMap.put("legal_entity", "LegalEntity");
        budgTargetRefMap.put("capability", "Capability");
        budgTargetRefMap.put("business_area", "BusinessArea");
        budgTargetRefMap.put("committee", "Committee");
        budgTargetRefMap.put("interface", "Interface");
        budgTargetRefMap.put("regulation", "Regulation");
        budgTargetRefMap.put("geography", "Geography");
        budgTargetRefMap.put("regulator", "Regulator");
        budgTargetRefMap.put("regulatory_theme", "RegulatoryTheme");
        budgTargetRefMap.put("org_unit", "OrgUnit");

        String normalizedFacet = normalizeFacetName(facet);
        String budgName = budgTargetRefMap.get(normalizedFacet);

        if (budgName == null) {
            // Fallback to capitalized display name
            budgName = capitalizeFirst(normalizedFacet.replace("_", ""));
        }

        return budgName + "_bulkCreate";
    }

    /**
     * Get targetRef for relationship (using BUDG naming conventions)
     */
    private String getTargetRefForRelationship(String relationshipKey) {
        RelationshipConfig config = RelationshipConfigRegistry.getConfig(relationshipKey);
        if (config != null) {
            String entityAName = getBudgTargetRefName(config.getEntityA().getName());
            String entityBName = getBudgTargetRefName(config.getEntityB().getName());
            return entityAName + "X" + entityBName + "_bulkCreate";
        }
        return relationshipKey + "_bulkCreate";
    }

    /**
     * Get BUDG targetRef name for a facet
     */
    private String getBudgTargetRefName(String facet) {
        // Map facets to BUDG targetRef names
        Map<String, String> budgTargetRefMap = new HashMap<>();
        budgTargetRefMap.put("dataset", "CatalogueItem");
        budgTargetRefMap.put("glossary", "CatItemCategory");
        budgTargetRefMap.put("attribute", "CatItemComponent");
        // Other facets use their capitalized name
        budgTargetRefMap.put("system", "System");
        budgTargetRefMap.put("policy", "Policy");
        budgTargetRefMap.put("process", "Process");
        budgTargetRefMap.put("project", "Project");
        budgTargetRefMap.put("product", "Product");
        budgTargetRefMap.put("client", "Client");
        budgTargetRefMap.put("legal_entity", "LegalEntity");
        budgTargetRefMap.put("legal", "LegalEntity");
        budgTargetRefMap.put("capability", "Capability");
        budgTargetRefMap.put("business_area", "BusinessArea");
        budgTargetRefMap.put("committee", "Committee");
        budgTargetRefMap.put("interface", "Interface");
        budgTargetRefMap.put("regulation", "Regulation");
        budgTargetRefMap.put("geography", "Geography");
        budgTargetRefMap.put("regulator", "Regulator");
        budgTargetRefMap.put("regulatory_theme", "RegulatoryTheme");
        budgTargetRefMap.put("org_unit", "OrgUnit");
        // Special case for object_x_people (roles)
        budgTargetRefMap.put("objectxpeople", "ObjectXIP");
        budgTargetRefMap.put("object_x_people", "ObjectXIP");
        budgTargetRefMap.put("people", "ObjectXIP");

        String normalizedFacet = normalizeFacetName(facet);
        String budgName = budgTargetRefMap.get(normalizedFacet);

        if (budgName == null) {
            // Fallback to capitalized display name
            budgName = capitalizeFirst(normalizedFacet.replace("_", ""));
        }

        return budgName;
    }

    /**
     * Generate Glossary template Excel file
     * Creates a template with all required columns for glossary bulk upload
     */
    private byte[] generateGlossaryTemplate() {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Create Glossary");

            // Create header row
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Define headers matching the glossary Excel format
            String[] headers = {
                    "Name", "Definition", "Ref.", "Examples", "Business Logic",
                    "Format Description", "LDM Reference", "Parent Name", "Parent Ref.",
                    "BUDG Viewing", "BUDG Status", "Lifecycle", "Format Type", "KDE",
                    "Security Classification", "Type", "Confidentiality", "Integrity",
                    "Availability", "Alias Names", "User Email", "User First Name",
                    "User Last Name", "User Lan ID", "Governance Role"
            };

            // Create header cells
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write workbook to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            workbook.close();
            return baos.toByteArray();

        } catch (Exception e) {
            logger.error("Error generating Glossary template: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build IN clause placeholder string
     */
    private String buildInClause(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    /**
     * Set parameters for IN clause
     */
    private void setParameters(PreparedStatement ps, List<Integer> ids, int startIndex) throws SQLException {
        for (int i = 0; i < ids.size(); i++) {
            ps.setInt(startIndex + i, ids.get(i));
        }
    }

    /**
     * Validation result
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * Manifest entry
     */
    private static class ManifestEntry {
        final String targetRef;
        final String fileEntry;

        ManifestEntry(String targetRef, String fileEntry) {
            this.targetRef = targetRef;
            this.fileEntry = fileEntry;
        }
    }
}
