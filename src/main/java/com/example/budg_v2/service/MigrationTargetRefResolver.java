package com.example.budg_v2.service;

import com.example.budg_v2.bulk.relationships.RelationshipConfigRegistry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps manifest {@code targetRef} values (BUDG / DatasetMigration conventions) to entity strings
 * sent to the Python validation service and to {@link EntityProcessorRegistry}.
 */
public final class MigrationTargetRefResolver {

    private static final Map<String, String> BUDG_SINGLETON_TO_PYTHON = new HashMap<>();

    static {
        BUDG_SINGLETON_TO_PYTHON.put("CatalogueItem", "Dataset");
        BUDG_SINGLETON_TO_PYTHON.put("CatItemCategory", "Glossary");
        BUDG_SINGLETON_TO_PYTHON.put("CatItemComponent", "Attribute");
        BUDG_SINGLETON_TO_PYTHON.put("OrgUnit", "Org Unit");
        BUDG_SINGLETON_TO_PYTHON.put("LegalEntity", "Legal");
        BUDG_SINGLETON_TO_PYTHON.put("BusinessArea", "Business Area");
        BUDG_SINGLETON_TO_PYTHON.put("RegulatoryTheme", "Regulatory Theme");
        BUDG_SINGLETON_TO_PYTHON.put("System", "System");
        BUDG_SINGLETON_TO_PYTHON.put("Policy", "Policy");
        BUDG_SINGLETON_TO_PYTHON.put("Process", "Process");
        BUDG_SINGLETON_TO_PYTHON.put("Project", "Project");
        BUDG_SINGLETON_TO_PYTHON.put("Product", "Product");
        BUDG_SINGLETON_TO_PYTHON.put("Client", "Client");
        BUDG_SINGLETON_TO_PYTHON.put("Capability", "Capability");
        BUDG_SINGLETON_TO_PYTHON.put("Committee", "Committee");
        BUDG_SINGLETON_TO_PYTHON.put("Interface", "Interface");
        BUDG_SINGLETON_TO_PYTHON.put("Regulation", "Regulation");
        BUDG_SINGLETON_TO_PYTHON.put("Geography", "Geography");
        BUDG_SINGLETON_TO_PYTHON.put("Regulator", "Regulator");
        BUDG_SINGLETON_TO_PYTHON.put("Geography", "Geography");
    }

    /**
     * BUDG facet token (in relationship targetRef) -> RelationshipConfigRegistry key segment (lowercase).
     */
    private static final Map<String, String> BUDG_PART_TO_REGISTRY_FACET = new HashMap<>();

    static {
        BUDG_PART_TO_REGISTRY_FACET.put("CatalogueItem", "dataset");
        BUDG_PART_TO_REGISTRY_FACET.put("CatItemCategory", "glossary");
        BUDG_PART_TO_REGISTRY_FACET.put("CatItemComponent", "attribute");
        BUDG_PART_TO_REGISTRY_FACET.put("LegalEntity", "legalentity");
        BUDG_PART_TO_REGISTRY_FACET.put("BusinessArea", "businessarea");
        BUDG_PART_TO_REGISTRY_FACET.put("RegulatoryTheme", "regulatorytheme");
        BUDG_PART_TO_REGISTRY_FACET.put("OrgUnit", "orgunit");
        BUDG_PART_TO_REGISTRY_FACET.put("System", "system");
        BUDG_PART_TO_REGISTRY_FACET.put("Policy", "policy");
        BUDG_PART_TO_REGISTRY_FACET.put("Process", "process");
        BUDG_PART_TO_REGISTRY_FACET.put("Project", "project");
        BUDG_PART_TO_REGISTRY_FACET.put("Product", "product");
        BUDG_PART_TO_REGISTRY_FACET.put("Client", "client");
        BUDG_PART_TO_REGISTRY_FACET.put("Capability", "capability");
        BUDG_PART_TO_REGISTRY_FACET.put("Committee", "committee");
        BUDG_PART_TO_REGISTRY_FACET.put("Interface", "interface");
        BUDG_PART_TO_REGISTRY_FACET.put("Regulation", "regulation");
        BUDG_PART_TO_REGISTRY_FACET.put("Geography", "geography");
        BUDG_PART_TO_REGISTRY_FACET.put("Regulator", "regulator");
    }

    /** Full BUDG AXB string -> registry compact key when simple facet composition is wrong. */
    private static final Map<String, String> BUDG_RELATIONSHIP_ALIASES = new HashMap<>();

    static {
        BUDG_RELATIONSHIP_ALIASES.put("ProcessXInterface", "processxsysteminterface");
    }

    private static final Map<String, String> BUDG_TO_ROLE_DISPLAY = new HashMap<>();

    static {
        BUDG_TO_ROLE_DISPLAY.put("CatalogueItem", "Data Set Role");
        BUDG_TO_ROLE_DISPLAY.put("CatItemCategory", "Glossary Role");
        BUDG_TO_ROLE_DISPLAY.put("CatItemComponent", "Attribute Role");
        BUDG_TO_ROLE_DISPLAY.put("Policy", "Policy Role");
        BUDG_TO_ROLE_DISPLAY.put("Process", "Process Role");
        BUDG_TO_ROLE_DISPLAY.put("Product", "Product Role");
        BUDG_TO_ROLE_DISPLAY.put("Project", "Project Role");
        BUDG_TO_ROLE_DISPLAY.put("System", "System Role");
        BUDG_TO_ROLE_DISPLAY.put("Client", "Client Role");
        BUDG_TO_ROLE_DISPLAY.put("Capability", "Capability Role");
        BUDG_TO_ROLE_DISPLAY.put("Committee", "Committee Role");
        BUDG_TO_ROLE_DISPLAY.put("Regulation", "Regulation Role");
        BUDG_TO_ROLE_DISPLAY.put("Interface", "Interface Role");
        BUDG_TO_ROLE_DISPLAY.put("LegalEntity", "Legal Entity Role");
        BUDG_TO_ROLE_DISPLAY.put("BusinessArea", "Business Area Role");
        BUDG_TO_ROLE_DISPLAY.put("Dataset", "Data Set Role");
        BUDG_TO_ROLE_DISPLAY.put("Glossary", "Glossary Role");
        BUDG_TO_ROLE_DISPLAY.put("Attribute", "Attribute Role");
    }

    private MigrationTargetRefResolver() {
    }

    /**
     * Strip {@code _bulkCreate} / {@code _bulkUpdate} / {@code _bulkDelete}.
     */
    public static String stripBulkSuffix(String targetRef) {
        if (targetRef == null || targetRef.isEmpty()) {
            return "";
        }
        String s = targetRef;
        if (s.endsWith("_bulkCreate")) {
            s = s.substring(0, s.length() - "_bulkCreate".length());
        } else if (s.endsWith("_bulkUpdate")) {
            s = s.substring(0, s.length() - "_bulkUpdate".length());
        } else if (s.endsWith("_bulkDelete")) {
            s = s.substring(0, s.length() - "_bulkDelete".length());
        }
        return s;
    }

    /**
     * Entity label for Python {@code entity} field and Java processing (singletons, roles, or compact
     * relationship key e.g. {@code policyxdataset}).
     */
    public static String resolveEntityLabel(String targetRef) {
        if (targetRef == null || targetRef.isEmpty()) {
            return null;
        }
        String base = stripBulkSuffix(targetRef);
        if (base.isEmpty()) {
            return null;
        }

        if (base.endsWith("XObjectXIP")) {
            String budgFacet = base.substring(0, base.length() - "XObjectXIP".length());
            String role = BUDG_TO_ROLE_DISPLAY.get(budgFacet);
            if (role != null) {
                return role;
            }
            return splitCamelTitleCase(budgFacet).trim() + " Role";
        }

        if (base.contains("X")) {
            String alias = BUDG_RELATIONSHIP_ALIASES.get(base);
            if (alias != null) {
                return alias;
            }
            String[] parts = base.split("X", 2);
            if (parts.length == 2) {
                String left = facetToken(parts[0].trim());
                String right = facetToken(parts[1].trim());
                String key = left + "x" + right;
                if (RelationshipConfigRegistry.isSupported(key)) {
                    return key;
                }
                String swapped = right + "x" + left;
                if (RelationshipConfigRegistry.isSupported(swapped)) {
                    return swapped;
                }
                return key;
            }
        }

        String mapped = BUDG_SINGLETON_TO_PYTHON.get(base);
        if (mapped != null) {
            return mapped;
        }
        if (base.contains("_")) {
            return base.replace("_", " ");
        }
        return splitCamelTitleCase(base);
    }

    /**
     * Normalized registry relationship key (no spaces/underscores, lowercase) if {@code targetRef} is a
     * relationship; otherwise null.
     */
    public static String relationshipRegistryKeyOrNull(String targetRef) {
        String label = resolveEntityLabel(targetRef);
        if (label == null) {
            return null;
        }
        String nk = normalizeRegistryKey(label);
        if (RelationshipConfigRegistry.isSupported(nk)) {
            return nk;
        }
        return null;
    }

    public static String normalizeRegistryKey(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).trim().replaceAll("[\\s_]+", "");
    }

    private static String facetToken(String budgPart) {
        if (budgPart.isEmpty()) {
            return budgPart;
        }
        return BUDG_PART_TO_REGISTRY_FACET.getOrDefault(budgPart, budgPart.toLowerCase(Locale.ROOT));
    }

    private static String splitCamelTitleCase(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && (Character.isLowerCase(prev) || (i + 1 < s.length() && Character.isLowerCase(s.charAt(i + 1))))) {
                sb.append(' ');
            }
            sb.append(c);
            prev = c;
        }
        return sb.toString().trim();
    }
}
