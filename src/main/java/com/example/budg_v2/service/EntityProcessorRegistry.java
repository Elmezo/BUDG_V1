package com.example.budg_v2.service;

import com.example.budg_v2.bulk.MigrationManifestEntry;
import com.example.budg_v2.bulk.relationships.RelationshipConfigRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for dynamic entity processor registration and discovery
 * Supports auto-discovery of entity types and fallback to generic processor
 */
public class EntityProcessorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(EntityProcessorRegistry.class);

    private static volatile EntityProcessorRegistry instance;
    private final Map<String, EntityProcessor> processors = new ConcurrentHashMap<>();
    private final Map<String, List<String>> entityAliases = new ConcurrentHashMap<>();
    private final Map<String, EntityProcessor> registryRelationshipProcessors = new ConcurrentHashMap<>();
    private final Map<String, EntityProcessor> roleProcessors = new ConcurrentHashMap<>();
    private GenericEntityProcessor genericProcessor;
    private final BulkDataProcessorService bulkDataProcessorService;
    private final RoleAssignmentBulkService roleAssignmentBulkService = new RoleAssignmentBulkService();

    private EntityProcessorRegistry() {
        bulkDataProcessorService = new BulkDataProcessorService();
        initializeDefaultProcessors();
    }

    /**
     * Get singleton instance
     */
    public static EntityProcessorRegistry getInstance() {
        if (instance == null) {
            synchronized (EntityProcessorRegistry.class) {
                if (instance == null) {
                    instance = new EntityProcessorRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize default processors for known entities
     */
    private void initializeDefaultProcessors() {
        // Register processors that delegate to BulkDataProcessorService methods
        registerProcessor("System", new EntityProcessor() {
            @Override
            public void process(int jobId, com.google.gson.JsonArray validatedData, int userId,
                    String errorHandling, String uploadOption, String segmentMode, String segment) {
                bulkDataProcessorService.processSystemData(jobId, validatedData, userId,
                        errorHandling, uploadOption, segmentMode, segment);
            }

            @Override
            public String getEntityName() {
                return "System";
            }

            @Override
            public boolean supports(String entityName) {
                return "system".equalsIgnoreCase(entityName);
            }
        });

        registerProcessor("Dataset", new EntityProcessor() {
            @Override
            public void process(int jobId, com.google.gson.JsonArray validatedData, int userId,
                    String errorHandling, String uploadOption, String segmentMode, String segment) {
                bulkDataProcessorService.processDatasetData(jobId, validatedData, userId,
                        errorHandling, uploadOption, segmentMode, segment);
            }

            @Override
            public String getEntityName() {
                return "Dataset";
            }

            @Override
            public boolean supports(String entityName) {
                String normalized = normalizeEntityName(entityName);
                return "dataset".equalsIgnoreCase(normalized) || "data sets".equalsIgnoreCase(normalized);
            }
        });

        registerProcessor("Glossary", new EntityProcessor() {
            @Override
            public void process(int jobId, com.google.gson.JsonArray validatedData, int userId,
                    String errorHandling, String uploadOption, String segmentMode, String segment) {
                bulkDataProcessorService.processGlossaryData(jobId, validatedData, userId,
                        errorHandling, uploadOption, segmentMode, segment);
            }

            @Override
            public String getEntityName() {
                return "Glossary";
            }

            @Override
            public boolean supports(String entityName) {
                return "glossary".equalsIgnoreCase(normalizeEntityName(entityName));
            }
        });

        registerProcessor("Attribute", new EntityProcessor() {
            @Override
            public void process(int jobId, com.google.gson.JsonArray validatedData, int userId,
                    String errorHandling, String uploadOption, String segmentMode, String segment) {
                bulkDataProcessorService.processAttributeData(jobId, validatedData, userId,
                        errorHandling, uploadOption, segmentMode, segment);
            }

            @Override
            public String getEntityName() {
                return "Attribute";
            }

            @Override
            public boolean supports(String entityName) {
                return "attribute".equalsIgnoreCase(normalizeEntityName(entityName));
            }
        });

        registerProcessor("Interface", new EntityProcessor() {
            @Override
            public void process(int jobId, com.google.gson.JsonArray validatedData, int userId,
                    String errorHandling, String uploadOption, String segmentMode, String segment) {
                bulkDataProcessorService.processInterfaceData(jobId, validatedData, userId,
                        errorHandling, uploadOption, segmentMode, segment);
            }

            @Override
            public String getEntityName() {
                return "Interface";
            }

            @Override
            public boolean supports(String entityName) {
                return "interface".equalsIgnoreCase(normalizeEntityName(entityName));
            }
        });

        // Register relationship entity processors
        registerProcessor("SystemXCatItemCategory", new GenericRelationshipProcessor("SystemXCatItemCategory"));
        registerProcessor("CatItemCategoryXSystem", new GenericRelationshipProcessor("CatItemCategoryXSystem"));
        registerProcessor("CatItemCategory Hierarchy", new GenericRelationshipProcessor("CatItemCategory Hierarchy"));
        registerProcessor("CatItemCategoryXCatItemCategory",
                new GenericRelationshipProcessor("CatItemCategoryXCatItemCategory"));

        // Initialize generic processor
        genericProcessor = new GenericEntityProcessor();

        // Register entity aliases
        registerAlias("System", Arrays.asList("system", "systems"));
        registerAlias("Dataset", Arrays.asList("dataset", "datasets", "data sets", "data_sets"));
        registerAlias("Glossary", Arrays.asList("glossary", "glossaries", "catitemcategory"));
        registerAlias("Attribute", Arrays.asList("attribute", "attributes", "catitemcomponent"));
        registerAlias("Interface", Arrays.asList("interface", "interfaces"));

        // Register relationship entity aliases
        registerAlias("SystemXCatItemCategory", Arrays.asList("systemxcatitemcategory", "system x glossary"));
        registerAlias("CatItemCategoryXSystem", Arrays.asList("catitemcategoryxsystem", "glossary x system"));
        registerAlias("CatItemCategory Hierarchy", Arrays.asList("catitemcategory hierarchy", "glossary hierarchy"));
        registerAlias("CatItemCategoryXCatItemCategory",
                Arrays.asList("catitemcategoryxcatitemcategory", "glossary x glossary", "glossary_glossary"));

        logger.info("Initialized EntityProcessorRegistry with {} processors", processors.size());
    }

    /**
     * Register a processor for an entity
     * 
     * @param entityName Entity name
     * @param processor  Processor instance
     */
    public void registerProcessor(String entityName, EntityProcessor processor) {
        if (entityName == null || processor == null) {
            logger.warn("Cannot register null entity name or processor");
            return;
        }

        String normalized = normalizeEntityName(entityName);
        processors.put(normalized.toLowerCase(), processor);
        logger.info("Registered processor for entity: {}", normalized);
    }

    /**
     * Get processor for an entity
     * 
     * @param entityName Entity name
     * @return Processor instance, or generic processor if not found
     */
    public EntityProcessor getProcessor(String entityName) {
        if (entityName == null) {
            return genericProcessor;
        }

        String regKey = MigrationTargetRefResolver.normalizeRegistryKey(entityName);
        if (RelationshipConfigRegistry.isSupported(regKey)) {
            return registryRelationshipProcessors.computeIfAbsent(regKey, RegistryBackedRelationshipProcessor::new);
        }

        String el = entityName.trim().toLowerCase(Locale.ROOT);
        if (el.endsWith(" role")) {
            final String roleLabel = entityName.trim();
            return roleProcessors.computeIfAbsent(el, k -> new EntityProcessor() {
                @Override
                public void process(int jobId, com.google.gson.JsonArray validatedData, int userId,
                        String errorHandling, String uploadOption, String segmentMode, String segment) {
                    try {
                        roleAssignmentBulkService.processRoleAssignments(roleLabel, jobId, validatedData, userId,
                                errorHandling);
                    } catch (Exception e) {
                        logger.error("Role bulk processing failed: {}", e.getMessage(), e);
                    }
                }

                @Override
                public String getEntityName() {
                    return roleLabel;
                }

                @Override
                public boolean supports(String name) {
                    return name != null && name.trim().equalsIgnoreCase(roleLabel);
                }
            });
        }

        String normalized = normalizeEntityName(entityName);
        String key = normalized.toLowerCase();

        // Direct lookup
        EntityProcessor processor = processors.get(key);
        if (processor != null) {
            return processor;
        }

        // Try alias lookup
        for (Map.Entry<String, List<String>> aliasEntry : entityAliases.entrySet()) {
            if (aliasEntry.getValue().contains(key)) {
                processor = processors.get(aliasEntry.getKey().toLowerCase());
                if (processor != null) {
                    return processor;
                }
            }
        }

        // Try supports() method
        for (EntityProcessor p : processors.values()) {
            if (p.supports(entityName)) {
                return p;
            }
        }

        // Fallback to generic processor
        logger.debug("No specific processor found for entity: {}, using generic processor", normalized);
        return genericProcessor;
    }

    /**
     * Check if a processor exists for an entity
     * 
     * @param entityName Entity name
     * @return true if processor exists, false otherwise
     */
    public boolean hasProcessor(String entityName) {
        if (entityName == null) {
            return false;
        }

        String normalized = normalizeEntityName(entityName);
        String key = normalized.toLowerCase();

        if (processors.containsKey(key)) {
            return true;
        }

        // Check aliases
        for (List<String> aliases : entityAliases.values()) {
            if (aliases.contains(key)) {
                return true;
            }
        }

        // Check supports() method
        for (EntityProcessor processor : processors.values()) {
            if (processor.supports(entityName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Register entity aliases
     * 
     * @param entityName Main entity name
     * @param aliases    List of aliases
     */
    public void registerAlias(String entityName, List<String> aliases) {
        if (entityName == null || aliases == null || aliases.isEmpty()) {
            return;
        }

        String normalized = normalizeEntityName(entityName);
        entityAliases.put(normalized.toLowerCase(), new ArrayList<>(aliases));
    }

    /**
     * Auto-discover entity types from manifest entries
     * 
     * @param entries Manifest entries (list of objects with targetRef and
     *                fileEntry)
     * @return Set of discovered entity names
     */
    public Set<String> autoDiscoverFromManifest(List<MigrationManifestEntry> entries) {
        Set<String> discoveredEntities = new HashSet<>();

        for (MigrationManifestEntry entry : entries) {
            String entityName = extractEntityNameFromTargetRef(entry.targetRef);
            if (entityName != null) {
                discoveredEntities.add(entityName);
                logger.debug("Discovered entity: {} from targetRef: {}", entityName, entry.targetRef);
            }
        }

        logger.info("Auto-discovered {} unique entities from manifest", discoveredEntities.size());
        return discoveredEntities;
    }

    /**
     * Extract entity name from targetRef (e.g., "System_bulkCreate" -> "System")
     * 
     * @param targetRef Target reference from manifest
     * @return Entity name or null if cannot extract
     */
    private String extractEntityNameFromTargetRef(String targetRef) {
        if (targetRef == null || targetRef.isEmpty()) {
            return null;
        }
        return MigrationTargetRefResolver.resolveEntityLabel(targetRef);
    }

    /**
     * Normalize entity name to standard format
     * 
     * @param entityName Entity name
     * @return Normalized entity name
     */
    private String normalizeEntityName(String entityName) {
        if (entityName == null) {
            return "Unknown";
        }

        String scratch = entityName.trim();
        scratch = scratch.replace(" (Roles)", "");
        scratch = scratch.replace("Roles", "");

        String resolved = MigrationTargetRefResolver.resolveEntityLabel(scratch);
        if (resolved != null) {
            return resolved;
        }

        String normalized = scratch.replace("_", " ");
        if (normalized.equalsIgnoreCase("CatalogueItem")) {
            normalized = "Dataset";
        } else if (normalized.equalsIgnoreCase("CatItemCategory")) {
            normalized = "Glossary";
        } else if (normalized.equalsIgnoreCase("CatItemComponent")) {
            normalized = "Attribute";
        }

        return normalized;
    }

    /**
     * Maps relationship registry facet names to importer entity labels used in the dependency graph.
     */
    public static String facetToImporterEntity(String facetName) {
        if (facetName == null) {
            return null;
        }
        String f = facetName.toLowerCase(Locale.ROOT).replace("_", "");
        switch (f) {
            case "dataset":
                return "Dataset";
            case "glossary":
                return "Glossary";
            case "attribute":
                return "Attribute";
            case "policy":
                return "Policy";
            case "process":
                return "Process";
            case "product":
                return "Product";
            case "project":
                return "Project";
            case "system":
                return "System";
            case "client":
                return "Client";
            case "capability":
                return "Capability";
            case "committee":
                return "Committee";
            case "interface":
                return "Interface";
            case "regulation":
                return "Regulation";
            case "regulator":
                return "Regulator";
            case "geography":
                return "Geography";
            case "legalentity":
            case "legal":
                return "Legal";
            case "businessarea":
                return "Business Area";
            case "regulatorytheme":
                return "Regulatory Theme";
            case "orgunit":
                return "Org Unit";
            default: {
                String alt = MigrationTargetRefResolver.resolveEntityLabel(facetName);
                return alt != null ? alt : facetName;
            }
        }
    }

    /**
     * Get all registered entity names
     * 
     * @return Set of registered entity names
     */
    public Set<String> getRegisteredEntities() {
        return new HashSet<>(processors.keySet());
    }

    /**
     * Set generic processor (for testing or custom implementation)
     * 
     * @param genericProcessor Generic processor instance
     */
    public void setGenericProcessor(GenericEntityProcessor genericProcessor) {
        this.genericProcessor = genericProcessor;
    }
}
