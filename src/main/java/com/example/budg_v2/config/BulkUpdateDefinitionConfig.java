package com.example.budg_v2.config;

import java.util.*;

/**
 * Hardcoded definition mappings for Bulk Update per facet.
 * Matches BUDG behavior exactly.
 */
public class BulkUpdateDefinitionConfig {

        public enum FieldType {
                LOOKUP, // Dropdown from lookup table
                CHECKBOX, // Boolean checkbox
                REFERENCE // Reference to another entity (e.g., person, system)
        }

        public static class DefinitionField {
                private final String fieldId;
                private final String displayName;
                private final String lookupTable;
                private final String columnName;
                private final FieldType fieldType;

                public DefinitionField(String fieldId, String displayName, String lookupTable, String columnName,
                                FieldType fieldType) {
                        this.fieldId = fieldId;
                        this.displayName = displayName;
                        this.lookupTable = lookupTable;
                        this.columnName = columnName;
                        this.fieldType = fieldType;
                }

                public String getFieldId() {
                        return fieldId;
                }

                public String getDisplayName() {
                        return displayName;
                }

                public String getLookupTable() {
                        return lookupTable;
                }

                public String getColumnName() {
                        return columnName;
                }

                public FieldType getFieldType() {
                        return fieldType;
                }
        }

        // Facets that are EXCLUDED from Bulk Update
        public static final Set<String> EXCLUDED_FACETS = Set.of(
                        "change-request", "changerequest", "change_request",
                        "active-task", "activetask", "active_task",
                        "geography",
                        "regulator",
                        "regulatory", "regulatory-theme", "regulatorytheme",
                        "physical-field", "physicalfield", "physical_field");

        // Facets that are EXCLUDED from Bulk Delete
        public static final Set<String> EXCLUDED_FACETS_FOR_DELETE = Set.of(
                        "role",
                        "physical-field", "physicalfield", "physical_field",
                        "change-request", "changerequest", "change_request",
                        "active-task", "activetask", "active_task");

        // Dataset fields that are mutually exclusive - cannot update 2+ together
        // In BUDG, a dataset can be assigned to either a System OR a Glossary OR a Segment, but not multiple
        // These three fields are mutually exclusive: System Short Name, Glossary Name, and Segment
        public static final Set<String> DATASET_MUTUALLY_EXCLUSIVE_FIELDS = Set.of(
                        "system_short_name", "glossary_name", "segment");

        // Facets requiring active_cr_id validation (skip if not null)
        public static final Set<String> FACETS_WITH_CR_VALIDATION = Set.of(
                        "dataset", "glossary", "process", "system");

        // Main table name per facet
        public static final Map<String, String> FACET_TABLE_MAP = Map.ofEntries(
                        Map.entry("dataset", "dataset"),
                        Map.entry("glossary", "glossary"),
                        Map.entry("system", "system"),
                        Map.entry("process", "process"),
                        Map.entry("attribute", "attribute"),
                        Map.entry("attributes", "attribute"),
                        Map.entry("policy", "policy"),
                        Map.entry("people", "people"),
                        Map.entry("role", "object_x_people"),
                        Map.entry("org-unit", "org_unit"),
                        Map.entry("orgunit", "org_unit"),
                        Map.entry("business-area", "business_area"),
                        Map.entry("businessarea", "business_area"),
                        Map.entry("client", "client"),
                        Map.entry("committee", "committee"),
                        Map.entry("product", "product"),
                        Map.entry("project", "project"),
                        Map.entry("interface", "interface"),
                        Map.entry("capability", "capability"),
                        Map.entry("legal-entity", "legal"),
                        Map.entry("legalentity", "legal"),
                        Map.entry("regulation", "regulation"),
                        Map.entry("regulator", "regulator"),
                        Map.entry("regulatory-theme", "regulatorytheme"),
                        Map.entry("regulatorytheme", "regulatorytheme"),
                        Map.entry("geography", "geography"),
                        Map.entry("data-quality-rule", "data_quality"),
                        Map.entry("dataquality", "data_quality"),
                        Map.entry("data_quality", "data_quality"));

        // Definition fields per facet
        private static final Map<String, List<DefinitionField>> FACET_DEFINITIONS = new HashMap<>();

        static {
                // Dataset definitions (matching BUDG behavior)
                // Note: System Short Name, Glossary Name, and Segment are mutually exclusive
                FACET_DEFINITIONS.put("dataset", Arrays.asList(
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "AccessControlType",
                                                FieldType.LOOKUP),
                                new DefinitionField("type", "Type", "dataset_type", "DatasetType",
                                                FieldType.LOOKUP),
                                new DefinitionField("system_short_name", "System Short Name", "system",
                                                "MasterSource",
                                                FieldType.REFERENCE),
                                new DefinitionField("lifecycle", "Lifecycle", "dataset_lifecycle", "lifecycle",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "status",
                                                FieldType.LOOKUP),
                                new DefinitionField("glossary_name", "Glossary Name", "glossary", "glossary",
                                                FieldType.REFERENCE),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));

                // Attribute definitions
                FACET_DEFINITIONS.put("attribute", Arrays.asList(
                                new DefinitionField("requirement", "Requirement", "attribute_requirement",
                                                "requirement_id",
                                                FieldType.LOOKUP),
                                new DefinitionField("dataset", "Data Set Name", "dataset", "dataset_id",
                                                FieldType.REFERENCE),
                                new DefinitionField("glossary", "Glossary Name", "glossary", "glossary_id",
                                                FieldType.REFERENCE),
                                new DefinitionField("origin", "Origin", "attribute_origin", "origin_id",
                                                FieldType.LOOKUP),
                                new DefinitionField("editability", "Editability", "attribute_editability",
                                                "editability_id",
                                                FieldType.LOOKUP)));

                // Role definitions (special handling)
                FACET_DEFINITIONS.put("role", Arrays.asList(
                                new DefinitionField("accept_roles", "Accept Roles", null, "AcceptedID",
                                                FieldType.CHECKBOX),
                                new DefinitionField("reassign_to", "Reassign Roles To", "people", "ipid",
                                                FieldType.REFERENCE),
                                new DefinitionField("change_status", "Change Role Status", "role_status", "statusID",
                                                FieldType.LOOKUP),
                                new DefinitionField("delete_roles", "Delete Roles", null, null, FieldType.CHECKBOX)));

                // People definitions
                FACET_DEFINITIONS.put("people", Arrays.asList(
                                new DefinitionField("org_unit", "Org Unit", "org_unit", "Org_Unit_ID",
                                                FieldType.REFERENCE),
                                new DefinitionField("profile", "Profile", "role", "System_Role",
                                                FieldType.LOOKUP),
                                new DefinitionField("lifecycle", "Lifecycle", "people_lifecycle_status",
                                                "lifecycle",
                                                FieldType.LOOKUP)));

                // Policy definitions
                FACET_DEFINITIONS.put("policy", Arrays.asList(
                                new DefinitionField("parent", "Parent Name", "policy", "ParentID",
                                                FieldType.REFERENCE),
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "isPublic",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "Status",
                                                FieldType.LOOKUP),
                                new DefinitionField("lifecycle", "Lifecycle", "policy_lifecycle", "Lifecycle_Status",
                                                FieldType.LOOKUP),
                                new DefinitionField("type", "Type", "policy_type", "Policy_Type", FieldType.LOOKUP),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));

                // Org Unit definitions
                FACET_DEFINITIONS.put("org-unit", Arrays.asList(
                                new DefinitionField("parent", "Parent", "org_unit", "parent_id", FieldType.REFERENCE)));
                FACET_DEFINITIONS.put("orgunit", FACET_DEFINITIONS.get("org-unit"));

                // System definitions
                FACET_DEFINITIONS.put("system", Arrays.asList(
                                new DefinitionField("parent", "Parent Short Name", "system", "parent_id",
                                                FieldType.REFERENCE),
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "is_Public",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "Status",
                                                FieldType.LOOKUP),
                                new DefinitionField("lifecycle", "Lifecycle", "system_lifecycle", "Lifecycle",
                                                FieldType.LOOKUP),
                                new DefinitionField("type", "Type", "system_type", "Type", FieldType.LOOKUP),
                                new DefinitionField("classification", "Classification", "system_classification",
                                                "Classification",
                                                FieldType.LOOKUP),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));

                // Glossary definitions
                FACET_DEFINITIONS.put("glossary", Arrays.asList(
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "Is_Public",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "Status",
                                                FieldType.LOOKUP),
                                new DefinitionField("parent", "Parent Name", "glossary", "Parent_ID",
                                                FieldType.REFERENCE),
                                new DefinitionField("lifecycle", "Lifecycle", "glossary_lifecycle", "Lifecycle",
                                                FieldType.LOOKUP),
                                new DefinitionField("format_type", "Format Type", "glossary_format_type",
                                                "Format_type",
                                                FieldType.LOOKUP),
                                new DefinitionField("security", "Security Classification", "glossary_security",
                                                "Security_Classification",
                                                FieldType.LOOKUP),
                                new DefinitionField("type", "Type", "glossary_type", "Type", FieldType.LOOKUP),
                                new DefinitionField("kde", "KDE", "glossary_kde_type", "KDE", FieldType.LOOKUP),
                                // Confidentiality/Integrity/Availability are CIA ratings
                                new DefinitionField("confidentiality", "Confidentiality", "cia_rating",
                                                "Confidentiality_Rating",
                                                FieldType.LOOKUP),
                                new DefinitionField("integrity", "Integrity", "cia_rating", "Integrity_Rating",
                                                FieldType.LOOKUP),
                                new DefinitionField("availability", "Availability", "cia_rating",
                                                "Availability_Rating",
                                                FieldType.LOOKUP),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));

                // Project definitions
                FACET_DEFINITIONS.put("project", Arrays.asList(
                                new DefinitionField("parent", "Parent", "project", "parentid", FieldType.REFERENCE),
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "is_public",
                                                FieldType.LOOKUP),
                                new DefinitionField("rag", "RAG", "project_rag", "rag", FieldType.LOOKUP),
                                new DefinitionField("classification", "Classification", "project_classification",
                                                "classification",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "status",
                                                FieldType.LOOKUP),
                                new DefinitionField("lifecycle", "Lifecycle", "project_lifecycle", "lifecycle_status",
                                                FieldType.LOOKUP),
                                new DefinitionField("type", "Type", "project_type", "project_type", FieldType.LOOKUP),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));

                // Process definitions
                FACET_DEFINITIONS.put("process", Arrays.asList(
                                new DefinitionField("parent", "Parent Name", "process", "parentid",
                                                FieldType.REFERENCE),
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "ispublic",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "status",
                                                FieldType.LOOKUP),
                                new DefinitionField("lifecycle", "Lifecycle", "process_lifecycle_status",
                                                "lifecycle_status",
                                                FieldType.LOOKUP),
                                new DefinitionField("type", "Type", "process_type", "type", FieldType.LOOKUP),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));

                // Interface definitions
                FACET_DEFINITIONS.put("interface", Arrays.asList(
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "is_public",
                                                FieldType.LOOKUP),
                                new DefinitionField("transfer_method", "Transfer Method", "interface_transfer_method",
                                                "Transfer_Method_ID", FieldType.LOOKUP),
                                new DefinitionField("transfer_format", "Transfer Format", "interface_transfer_format",
                                                "Transfer_Format_ID", FieldType.LOOKUP),
                                new DefinitionField("interface_classification", "Interface Classification",
                                                "interface_classification",
                                                "Classification_id", FieldType.LOOKUP),
                                new DefinitionField("lifecycle", "Lifecycle", "interface_lifecycle", "Lifecycle_id",
                                                FieldType.LOOKUP),
                                new DefinitionField("source_system", "Source System Short Name", "system",
                                                "Source_systemID",
                                                FieldType.REFERENCE),
                                new DefinitionField("target_system", "Target System Short Name", "system",
                                                "Target_systemID",
                                                FieldType.REFERENCE),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "status_id",
                                                FieldType.LOOKUP),
                                new DefinitionField("automation_level", "AutomationLevel", "interface_automation",
                                                "Automation_ID",
                                                FieldType.LOOKUP),
                                new DefinitionField("frequency", "Frequency", "interface_frequency", "Frequency_ID",
                                                FieldType.LOOKUP)));

                // Regulation definitions
                FACET_DEFINITIONS.put("regulation", Arrays.asList(
                                new DefinitionField("parent", "Parent", "regulation", "Parent_ID", FieldType.REFERENCE),
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "Is_Public",
                                                FieldType.LOOKUP),
                                new DefinitionField("business_area", "Business Area", "business_area",
                                                "Business_Area_ID",
                                                FieldType.REFERENCE),
                                new DefinitionField("maturity", "Maturity", "regulation_maturity", "RegulationMaturity_ID",
                                                FieldType.LOOKUP),
                                new DefinitionField("probability", "Probability", "regulation_probability",
                                                "RegulationProbability_ID",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "RegulationStatus_ID",
                                                FieldType.LOOKUP),
                                new DefinitionField("impact_rating", "Impact Rating", "regulation_impact_rating",
                                                "RegulationImpactRating_ID",
                                                FieldType.LOOKUP),
                                new DefinitionField("legal_advice", "Legal Advice Type", "legal_advice_type",
                                                "LegalAdviceType_ID",
                                                FieldType.LOOKUP),
                                new DefinitionField("stage", "Stage", "regulation_stage", "RegulationStage_ID", FieldType.LOOKUP),
                                new DefinitionField("compliance_level", "Compliance Level",
                                                "regulation_compliance_level",
                                                "ComplianceLevel_ID", FieldType.LOOKUP),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));

                // Business Area definitions
                FACET_DEFINITIONS.put("business-area", Arrays.asList(
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "Is_Public",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "Status",
                                                FieldType.LOOKUP),
                                new DefinitionField("parent", "Parent", "business_area", "Parent_ID",
                                                FieldType.REFERENCE),
                                // Note: Business Area Lifecycle usually matches other patterns, assuming
                                // business_area_lifecycle
                                new DefinitionField("lifecycle", "Lifecycle Status", "business_area_lifecycle",
                                                "Lifecycle",
                                                FieldType.LOOKUP),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));
                FACET_DEFINITIONS.put("businessarea", FACET_DEFINITIONS.get("business-area"));

                // Committee definitions
                FACET_DEFINITIONS.put("committee", Arrays.asList(
                                new DefinitionField("parent", "Parent", "committee", "Parent_ID", FieldType.REFERENCE),
                                new DefinitionField("classification", "Classification", "committee_classification",
                                                "Classification",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "Status",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "Is_Public",
                                                FieldType.LOOKUP),
                                new DefinitionField("lifecycle", "Lifecycle", "committee_lifecycle", "Lifecycle",
                                                FieldType.LOOKUP),
                                new DefinitionField("type", "Committee Type", "committee_type", "Committee_Type",
                                                FieldType.LOOKUP),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));

                // Legal Entity definitions
                FACET_DEFINITIONS.put("legal-entity", Arrays.asList(
                                new DefinitionField("parent", "Parent", "legal", "Parent_ID",
                                                FieldType.REFERENCE),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "Status",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "Is_Public",
                                                FieldType.LOOKUP),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));
                FACET_DEFINITIONS.put("legalentity", FACET_DEFINITIONS.get("legal-entity"));

                // Product definitions
                FACET_DEFINITIONS.put("product", Arrays.asList(
                                new DefinitionField("parent", "Parent", "product", "parent_id", FieldType.REFERENCE),
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "is_public",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "status",
                                                FieldType.LOOKUP),
                                new DefinitionField("lifecycle", "Lifecycle", "product_lifecycle", "lifecycle_status",
                                                FieldType.LOOKUP),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));

                // Capability definitions
                FACET_DEFINITIONS.put("capability", Arrays.asList(
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "Is_Public",
                                                FieldType.LOOKUP),
                                new DefinitionField("classification", "Classification", "capability_classification",
                                                "Classification", FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "Status",
                                                FieldType.LOOKUP),
                                new DefinitionField("lifecycle", "Lifecycle Status", "capability_lifecycle",
                                                "Lifecycle",
                                                FieldType.LOOKUP),
                                new DefinitionField("type", "Type", "capability_type", "Capability_Type", FieldType.LOOKUP),
                                new DefinitionField("parent", "Parent", "capability", "Parent_ID", FieldType.REFERENCE),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));

                // Client definitions
                FACET_DEFINITIONS.put("client", Arrays.asList(
                                new DefinitionField("parent", "Parent", "client", "Parent_ID", FieldType.REFERENCE),
                                new DefinitionField("lifecycle", "Lifecycle", "client_lifecycle", "Lifecycle",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_status", "BUDG Status", "axon_status", "Status",
                                                FieldType.LOOKUP),
                                new DefinitionField("axon_viewing", "BUDG Viewing", "axon_viewing", "IsPublic",
                                                FieldType.LOOKUP),
                                new DefinitionField("segment", "Segment", "segment", "segment",
                                                FieldType.REFERENCE)));
        }

        /**
         * Get definition fields for a facet
         */
        public static List<DefinitionField> getDefinitionsForFacet(String facet) {
                String normalizedFacet = normalizeFacet(facet);
                return FACET_DEFINITIONS.getOrDefault(normalizedFacet, Collections.emptyList());
        }

        /**
         * Check if facet is excluded from bulk update
         */
        public static boolean isFacetExcluded(String facet) {
                String normalizedFacet = normalizeFacet(facet);
                return EXCLUDED_FACETS.contains(normalizedFacet);
        }

        /**
         * Check if facet is excluded from bulk delete
         */
        public static boolean isFacetExcludedForBulkDelete(String facet) {
                String normalizedFacet = normalizeFacet(facet);
                return EXCLUDED_FACETS_FOR_DELETE.contains(normalizedFacet);
        }

        /**
         * Check if facet requires active_cr_id validation
         */
        public static boolean requiresCRValidation(String facet) {
                String normalizedFacet = normalizeFacet(facet);
                return FACETS_WITH_CR_VALIDATION.contains(normalizedFacet);
        }

        /**
         * Get table name for facet
         */
        public static String getTableForFacet(String facet) {
                String normalizedFacet = normalizeFacet(facet);
                return FACET_TABLE_MAP.get(normalizedFacet);
        }

        /**
         * Check if field is mutually exclusive for dataset
         */
        public static boolean isDatasetMutuallyExclusiveField(String fieldId) {
                return DATASET_MUTUALLY_EXCLUSIVE_FIELDS.contains(fieldId);
        }

        /**
         * Normalize facet name to lowercase with hyphens
         */
        private static String normalizeFacet(String facet) {
                if (facet == null)
                        return "";
                return facet.toLowerCase().trim();
        }

        /**
         * Get definition field by ID for a facet
         */
        public static DefinitionField getFieldById(String facet, String fieldId) {
                List<DefinitionField> fields = getDefinitionsForFacet(facet);
                return fields.stream()
                                .filter(f -> f.getFieldId().equals(fieldId))
                                .findFirst()
                                .orElse(null);
        }
}
