package com.example.budg_v2.service;

import com.example.budg_v2.bulk.relationships.RelationshipConfigRegistry;
import com.example.budg_v2.bulk.relationships.config.RelationshipConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Destructive wipe of governance data prior to full ENV import (replace mode).
 * Uses MySQL {@code FOREIGN_KEY_CHECKS=0}. Never truncates users, authentication,
 * audit history, job infrastructure, or migration metadata — see
 * {@link #isTableProtectedFromReplaceTruncate(String)}.
 * <p>
 * Stakeholder / person links ({@code *_x_objectxpeople}) are never truncated here so ENV replace
 * does not clear role assignments to people; export already skips these tables.
 */
public final class EnvironmentCoreTruncator {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentCoreTruncator.class);

    /**
     * Exact table names (case-insensitive) that must never be truncated in replace mode.
     */
    private static final Set<String> PROTECTED_EXACT = Set.of(
            "people",
            "auth_config",
            "auth_sessions",
            "auth_tokens",
            "app_config",
            "admin_activity_log",
            "admin_activity_log_details",
            "job",
            "job_progress",
            "job_resource_filename",
            "job_report_item",
            "job_report_item_messages",
            "job_configuration",
            "revinfo");

    /** Core entity tables aligned with bulk-migratable facets. */
    private static final List<String> ENTITY_TABLES = List.of(
            "attribute",
            "dataset",
            "glossary",
            "policy",
            "process",
            "project",
            "product",
            "client",
            "legal_entity",
            "committee",
            "interface",
            "business_area",
            "capability",
            "regulation",
            "geography",
            "regulator",
            "regulatory_theme",
            "org_unit",
            "system");

    private EnvironmentCoreTruncator() {
    }

    public static void truncateCoreGovernanceData(Connection conn) throws SQLException {
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        for (RelationshipConfig cfg : RelationshipConfigRegistry.getAllConfigurations()) {
            if (cfg == null || cfg.getTableName() == null) {
                continue;
            }
            String main = cfg.getTableName();
            if (isExcludedRelationshipTable(main)) {
                continue;
            }
            String rt = cfg.getRelationTypeTable();
            if (rt != null && !rt.isEmpty() && !isExcludedRelationshipTable(rt)) {
                tables.add(rt);
            }
            tables.add(main);
        }
        tables.addAll(ENTITY_TABLES);

        List<String> ordered = new ArrayList<>();
        for (String t : tables) {
            if (isStakeholderObjectPeopleLinkTable(t)) {
                logger.info("Replace mode: omitting person/stakeholder link table from TRUNCATE: {}", t);
                continue;
            }
            if (isTableProtectedFromReplaceTruncate(t)) {
                logger.info("Replace mode: omitting protected table from TRUNCATE: {}", t);
                continue;
            }
            ordered.add(t);
        }
        try (Statement st = conn.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String t : ordered) {
                try {
                    st.execute("TRUNCATE TABLE `" + sanitizeIdentifier(t) + "`");
                    logger.debug("Truncated {}", t);
                } catch (SQLException e) {
                    logger.warn("Skipping truncate for table {}: {}", t, e.getMessage());
                }
            }
            st.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    /**
     * Tables matching this must never be truncated during ENV replace import.
     * Covers explicit denylist plus naming patterns for audit and auth-adjacent data.
     */
    public static boolean isTableProtectedFromReplaceTruncate(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (PROTECTED_EXACT.contains(lower)) {
            return true;
        }
        if ("audit".equals(lower)) {
            return true;
        }
        if (lower.startsWith("flyway_")) {
            return true;
        }
        if (lower.endsWith("_audit") || lower.endsWith("_audit_history")) {
            return true;
        }
        return false;
    }

    private static boolean isExcludedRelationshipTable(String name) {
        if (name == null) {
            return true;
        }
        String lower = name.toLowerCase();
        return lower.contains("people")
                || lower.contains("segment_x_identity")
                || "people_x_people".equals(lower);
    }

    /** True for junction tables linking facets to people / object-in-role (never truncated in ENV replace). */
    public static boolean isStakeholderObjectPeopleLinkTable(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return name.toLowerCase(Locale.ROOT).contains("objectxpeople");
    }

    private static String sanitizeIdentifier(String raw) {
        if (raw == null || !raw.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + raw);
        }
        return raw;
    }
}
