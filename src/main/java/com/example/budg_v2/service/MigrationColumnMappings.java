package com.example.budg_v2.service;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Excel column renames for ENV migration: exported headers (often DB-style) → names expected by Python validators.
 * <p>
 * Each entry is {@code sourceColumnInExcel -> targetColumnForProcessor}. This matches
 * {@code apply_column_mappings} in Python (rename {@code source} to {@code target}).
 * Processors that use fuzzy matching treat the key as a search pattern against actual headers.
 */
public final class MigrationColumnMappings {

    private static final Map<String, Map<String, String>> BY_ENTITY_LOWER = new HashMap<>();

    static {
        // Policy: fuzzy column match in policy_bulk_processor
        Map<String, String> policy = new HashMap<>();
        policy.put("Primary Name", "Name");
        policy.put("refNumber", "Ref.");
        policy.put("Lifecycle_Status", "Lifecycle");
        policy.put("Policy_Type", "Type");
        policy.put("isPublic", "Internal");
        BY_ENTITY_LOWER.put("policy", Collections.unmodifiableMap(policy));

        // Process / Project: exact match — keys must equal DataFrame column names after Excel read
        Map<String, String> process = new HashMap<>();
        process.put("primaryname", "Name");
        process.put("refnumber", "Ref.");
        process.put("description", "Description");
        process.put("type", "Type");
        process.put("lifecycle_status", "Lifecycle");
        process.put("step_type", "Step Type");
        BY_ENTITY_LOWER.put("process", Collections.unmodifiableMap(process));

        Map<String, String> product = new HashMap<>();
        product.put("primaryname", "Product Name");
        product.put("refnumber", "Reference Number");
        product.put("description", "Product Description");
        product.put("longname", "Long Name");
        product.put("lifecycle_status", "Lifecycle");
        product.put("status", "BUDG Status");
        product.put("is_public", "BUDG Viewing");
        BY_ENTITY_LOWER.put("product", Collections.unmodifiableMap(product));

        Map<String, String> project = new HashMap<>();
        project.put("primaryname", "Project Name");
        project.put("refnumber", "Reference");
        project.put("description", "Project Description");
        project.put("startdate", "Start Date");
        project.put("enddate", "End Date");
        project.put("rag", "RAG");
        project.put("lifecycle_status", "Project Lifecycle");
        project.put("project_type", "Project Type");
        project.put("status", "BUDG Status");
        project.put("is_public", "BUDG Viewing");
        BY_ENTITY_LOWER.put("project", Collections.unmodifiableMap(project));

        // Capability: exact match on typical export headers
        Map<String, String> capability = new HashMap<>();
        capability.put("PrimaryName", "Capability Name");
        capability.put("RefNumber", "RefNumber");
        capability.put("Description", "Capability Definition");
        capability.put("Lifecycle", "Lifecycle");
        capability.put("Capability_Type", "Capability Type");
        capability.put("Classification", "Classification");
        BY_ENTITY_LOWER.put("capability", Collections.unmodifiableMap(capability));

        // Regulation: smart match
        Map<String, String> regulation = new HashMap<>();
        regulation.put("primaryName", "Regulation Long Name");
        regulation.put("PrimaryName", "Regulation Long Name");
        regulation.put("RefNumber", "Reference");
        regulation.put("Reference", "Reference");
        regulation.put("Description", "Description");
        BY_ENTITY_LOWER.put("regulation", Collections.unmodifiableMap(regulation));

        Map<String, String> glossary = new HashMap<>();
        glossary.put("Primary Name", "Name");
        BY_ENTITY_LOWER.put("glossary", Collections.unmodifiableMap(glossary));

        Map<String, String> dataset = new HashMap<>();
        dataset.put("Primary Name", "Name");
        BY_ENTITY_LOWER.put("dataset", Collections.unmodifiableMap(dataset));

        Map<String, String> iface = new HashMap<>();
        iface.put("Ref_number", "Reference");
        iface.put("Name", "Interface Name");
        iface.put("Description", "Interface Description");
        BY_ENTITY_LOWER.put("interface", Collections.unmodifiableMap(iface));

        // Geography / Committee: smart match
        Map<String, String> geography = new HashMap<>();
        geography.put("primaryname", "Geography Name");
        geography.put("PrimaryName", "Geography Name");
        geography.put("Name", "Geography Name");
        BY_ENTITY_LOWER.put("geography", Collections.unmodifiableMap(geography));

        Map<String, String> committee = new HashMap<>();
        committee.put("PrimaryName", "Committee Name");
        committee.put("primaryname", "Committee Name");
        committee.put("Name", "Committee Name");
        committee.put("RefNumber", "Reference");
        committee.put("refnumber", "Reference");
        committee.put("Ref.", "Reference");
        committee.put("Description", "Description");
        committee.put("Classification", "Classification");
        committee.put("Lifecycle", "Lifecycle");
        committee.put("Committee_Type", "Committee Type");
        BY_ENTITY_LOWER.put("committee", Collections.unmodifiableMap(committee));

        Map<String, String> regulatoryTheme = new HashMap<>();
        regulatoryTheme.put("PrimaryName", "Regulatory Theme Long Name");
        regulatoryTheme.put("primaryName", "Regulatory Theme Long Name");
        regulatoryTheme.put("RefNumber", "Reference");
        regulatoryTheme.put("ShortName", "Short Name");
        regulatoryTheme.put("Description", "Description");
        regulatoryTheme.put("Status_ID", "BUDG Status");
        BY_ENTITY_LOWER.put("regulatory theme", Collections.unmodifiableMap(regulatoryTheme));
        BY_ENTITY_LOWER.put("regulatory_theme", Collections.unmodifiableMap(regulatoryTheme));
    }

    private MigrationColumnMappings() {
    }

    /**
     * Adds {@code column_mappings} to the validation JSON when the entity has known migration aliases.
     */
    public static void addToValidationRequest(JsonObject validationRequest, String entityLabel) {
        if (validationRequest == null || entityLabel == null) {
            return;
        }
        String k = entityLabel.trim().toLowerCase(Locale.ROOT);
        Map<String, String> m = BY_ENTITY_LOWER.get(k);
        if (m == null || m.isEmpty()) {
            m = BY_ENTITY_LOWER.get(k.replace(' ', '_'));
        }
        if (m == null || m.isEmpty()) {
            return;
        }
        JsonObject cm = new JsonObject();
        for (Map.Entry<String, String> e : m.entrySet()) {
            cm.addProperty(e.getKey(), e.getValue());
        }
        validationRequest.add("column_mappings", cm);
    }
}
