package com.example.budg_v2.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import com.example.budg_v2.database.DatabaseConnection;

/**
 * Utility class for generating unique reference numbers for different entities
 */
public class ReferenceNumberGenerator {
    
    private static final String SYSTEM_PREFIX = "SYS";
    private static final String DATASET_PREFIX = "DS";
    private static final String ATTRIBUTE_PREFIX = "ATT";
    private static final String INTERFACE_PREFIX = "IF";
    private static final String GLOSSARY_PREFIX = "GL";
    private static final String ORG_UNIT_PREFIX = "OU";
    private static final String COMMITTEE_PREFIX = "CM";
    private static final String BUSINESS_AREA_PREFIX = "BA";
    private static final String CAPABILITY_PREFIX = "CAP";
    private static final String CLIENT_PREFIX = "CLI";
    private static final String LEGAL_PREFIX = "LEG";
    private static final String PRODUCT_PREFIX = "PRD";
    private static final String PROCESS_PREFIX = "PRC";
    private static final String PROJECT_PREFIX = "PRJ";
    
    /**
     * Generates a unique reference number for process
     * Format: PRC-1, PRC-2, etc.
     */
    public static String generateProcessRefNumber() throws SQLException {
        return generateRefNumberWithDash(PROCESS_PREFIX, "process", "RefNumber");
    }

    /**
     * Generates a unique process reference number avoiding refs already in the current batch.
     * Use when ref is empty and the same connection/transaction is used so DB may not yet see previous inserts.
     * @param conn connection for DB query (not closed)
     * @param batchRefsLower refs already used in this batch (lowercase, e.g. "prc-10")
     * @return next available PRC-N not in batch and not in DB
     */
    public static String generateProcessRefNumber(Connection conn, Set<String> batchRefsLower) throws SQLException {
        return generateRefNumberWithDash(conn, PROCESS_PREFIX, "process", "RefNumber", batchRefsLower);
    }
    
    /**
     * Generates a unique reference number for project
     * Format: PRJ-1, PRJ-2, etc.
     */
    public static String generateProjectRefNumber() throws SQLException {
        return generateRefNumberWithDash(PROJECT_PREFIX, "project", "refnumber");
    }
    
    /**
     * Generates a unique reference number for system
     * Format: SYS + 3-digit sequence number (e.g., SYS001, SYS002)
     */
    public static String generateSystemRefNumber() throws SQLException {
        return generateRefNumber(SYSTEM_PREFIX, "system", "RefNumber");
    }
    
    /**
     * Generates a unique reference number for dataset (used by single Add Dataset form).
     * Format: DS + 3-digit sequence number (e.g., DS001, DS002).
     * Only considers non-deleted datasets so the ref never conflicts with an active row.
     */
    public static String generateDatasetRefNumber() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return generateRefNumber(conn, DATASET_PREFIX, "dataset", "RefNumber", null, "DeletedDatetime");
        }
    }

    /**
     * Generates a unique dataset reference number avoiding refs already in the current batch.
     * Use when ref is empty during bulk upload so the same connection/transaction is used and
     * multiple rows get distinct refs (e.g. DS001, DS002) instead of the same one.
     * Only considers non-deleted datasets so the generated ref never conflicts with an active row.
     *
     * @param conn connection for DB query (not closed)
     * @param batchRefsLower refs already used in this batch (lowercase, e.g. "ds001"); may be null or empty
     * @return next available DSxxx not in batch and not in DB (among non-deleted rows)
     */
    public static String generateDatasetRefNumber(Connection conn, Set<String> batchRefsLower) throws SQLException {
        return generateRefNumber(conn, DATASET_PREFIX, "dataset", "RefNumber", batchRefsLower, "DeletedDatetime");
    }
    
    /**
     * Generates a unique reference number for attribute (only among non-deleted rows).
     * Format: ATT_1, ATT_2, etc. (underscore with no leading zeros).
     * Excludes deleted rows so the next ref does not conflict with an active row.
     */
    public static String generateAttributeRefNumber() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return generateRefNumberWithUnderscore(conn, ATTRIBUTE_PREFIX, "attribute", "RefNumber", null, "DeletedDatetime");
        }
    }

    /**
     * Generates a unique attribute reference number avoiding refs already in the current batch.
     * Use when ref is empty during bulk upload so the same connection/transaction is used and
     * multiple rows get distinct refs (e.g. ATT_7, ATT_8) instead of the same one.
     *
     * @param conn connection for DB query (not closed)
     * @param batchRefsLower refs already used in this batch (lowercase, e.g. "att_7"); may be null or empty
     * @return next available ATT_N not in batch and not in DB
     */
    public static String generateAttributeRefNumber(Connection conn, Set<String> batchRefsLower) throws SQLException {
        return generateRefNumberWithUnderscore(conn, ATTRIBUTE_PREFIX, "attribute", "RefNumber", batchRefsLower, "DeletedDatetime");
    }
    
    /**
     * Generates a unique reference number for interface
     * Format: IF + 3-digit sequence number (e.g., IF001, IF002)
     */
    public static String generateInterfaceRefNumber() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return generateRefNumber(conn, INTERFACE_PREFIX, "interface", "Ref_number", null, "deleted_datetime");
        }
    }

    /**
     * Generates a unique interface reference number using a provided connection.
     * Useful when multiple validations/inserts happen in the same request.
     */
    public static String generateInterfaceRefNumber(Connection conn, Set<String> batchRefsLower) throws SQLException {
        return generateRefNumber(conn, INTERFACE_PREFIX, "interface", "Ref_number", batchRefsLower, "deleted_datetime");
    }
    
    /**
     * Generates a unique asset ID for interface
     * Format: AST + 3-digit sequence number (e.g., AST001, AST002)
     */
    public static String generateAssetId() throws SQLException {
        return generateRefNumber("AST", "interface", "Asset_ID");
    }
    
    /**
     * Generates a unique reference number for glossary (used by single Add Glossary form).
     * Format: GL + 3-digit sequence number (e.g., GL001, GL002).
     * Only considers non-deleted glossary rows so the ref never conflicts with an active row.
     */
    public static String generateGlossaryRefNumber() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return generateRefNumber(conn, GLOSSARY_PREFIX, "glossary", "Ref_Number", null, "Deleted_datetime");
        }
    }

    /**
     * Generates a unique glossary reference number avoiding refs already in the current batch.
     * Use when ref is empty during bulk upload so the same connection/transaction is used and
     * multiple rows get distinct refs (e.g. GL001, GL002) instead of the same one.
     * Only considers non-deleted glossary rows so the generated ref never conflicts with an active row.
     *
     * @param conn connection for DB query (not closed)
     * @param batchRefsLower refs already used in this batch (lowercase, e.g. "gl001"); may be null or empty
     * @return next available GLxxx not in batch and not in DB (among non-deleted rows)
     */
    public static String generateGlossaryRefNumber(Connection conn, Set<String> batchRefsLower) throws SQLException {
        return generateRefNumber(conn, GLOSSARY_PREFIX, "glossary", "Ref_Number", batchRefsLower, "Deleted_datetime");
    }
    
    /**
     * Generates a unique reference for org unit
     * Format: OU + 3-digit sequence number (e.g., OU001, OU002)
     */
    public static String generateOrgUnitReference() throws SQLException {
        return generateRefNumber(ORG_UNIT_PREFIX, "org_unit", "reference");
    }
    
    /**
     * Generates a unique reference number for committee (used by single Add Committee form).
     * Format: CM + 3-digit sequence number (e.g., CM001, CM002).
     * Only considers non-deleted committees so the ref never conflicts with an active row.
     */
    public static String generateCommitteeRefNumber() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return generateRefNumber(conn, COMMITTEE_PREFIX, "committee", "RefNumber", null, "DeleteDatetime");
        }
    }

    /**
     * Generates a unique committee reference number avoiding refs already in the current batch.
     * Use when ref is empty during bulk upload so the same connection/transaction is used and
     * multiple rows get distinct refs (e.g. CM001, CM002) instead of the same one.
     * Only considers non-deleted committees so the generated ref never conflicts with an active row.
     *
     * @param conn connection for DB query (not closed)
     * @param batchRefsLower refs already used in this batch (lowercase, e.g. "cm001"); may be null or empty
     * @return next available CMxxx not in batch and not in DB (among non-deleted rows)
     */
    public static String generateCommitteeRefNumber(Connection conn, Set<String> batchRefsLower) throws SQLException {
        return generateRefNumber(conn, COMMITTEE_PREFIX, "committee", "RefNumber", batchRefsLower, "DeleteDatetime");
    }
    
    /**
     * Generates a unique reference number for business area
     * Format: BA-1, BA-2, etc.
     */
    public static String generateBusinessAreaReference() throws SQLException {
        return generateRefNumberWithDash(BUSINESS_AREA_PREFIX, "business_area", "Reference");
    }
    
    /**
     * Generates a unique reference number for capability
     * Format: CAP-1, CAP-2, etc.
     */
    public static String generateCapabilityReference() throws SQLException {
        return generateRefNumberWithDash(CAPABILITY_PREFIX, "capability", "RefNumber");
    }
    
    /**
     * Generates a unique reference number for client
     * Format: CLI-1, CLI-2, etc.
     */
    public static String generateClientReference() throws SQLException {
        return generateRefNumberWithDash(CLIENT_PREFIX, "client", "Reference");
    }
    
    /**
     * Generates a unique reference number for legal entity
     * Format: LEG-1, LEG-2, etc.
     */
    public static String generateLegalReference() throws SQLException {
        return generateRefNumberWithDash(LEGAL_PREFIX, "legal", "Reference");
    }
    
    /**
     * Generates a unique reference number for product
     * Format: PRD-1, PRD-2, etc.
     */
    public static String generateProductReference() throws SQLException {
        return generateRefNumberWithDash(PRODUCT_PREFIX, "product", "refnumber");
    }

    /**
     * Generates a unique product reference number avoiding refs already in the current batch.
     * Use when ref is empty during bulk upload so the same connection/transaction is used and
     * multiple rows get distinct refs (e.g. PRD-6, PRD-7) instead of the same one.
     *
     * @param conn connection for DB query (not closed)
     * @param batchRefsLower refs already used in this batch (lowercase, e.g. "prd-6"); may be null or empty
     * @return next available PRD-N not in batch and not in DB
     */
    public static String generateProductReference(Connection conn, Set<String> batchRefsLower) throws SQLException {
        return generateRefNumberWithDash(conn, PRODUCT_PREFIX, "product", "refnumber", batchRefsLower);
    }
    
    /**
     * Generic method to generate reference numbers
     * @param prefix The prefix for the reference number
     * @param tableName The table name to check for uniqueness
     * @param columnName The column name to check for uniqueness
     * @return A unique reference number
     */
    private static String generateRefNumber(String prefix, String tableName, String columnName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return generateRefNumber(conn, prefix, tableName, columnName, null);
        }
    }

    /**
     * Generate reference number (prefix + 3-digit format, e.g. CM001, DS001) using provided connection
     * and optionally excluding refs already in the batch.
     * @param conn connection to use (not closed)
     * @param batchRefsLower optional set of refs already in batch (lowercase); if null, only DB is considered
     */
    private static String generateRefNumber(Connection conn, String prefix, String tableName, String columnName, Set<String> batchRefsLower) throws SQLException {
        return generateRefNumber(conn, prefix, tableName, columnName, batchRefsLower, null);
    }

    /**
     * Generate reference number (prefix + 3-digit format) with optional filter for non-deleted rows only.
     * When deletedColumn is set, only rows with that column NULL or empty are considered, so the next
     * ref never conflicts with an active (non-deleted) row.
     */
    private static String generateRefNumber(Connection conn, String prefix, String tableName, String columnName, Set<String> batchRefsLower, String deletedColumn) throws SQLException {
        // Collect all existing numeric refs into a set, then find the first gap starting from 1.
        // This avoids inheriting huge sequence numbers from legacy/corrupt data.
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT CAST(SUBSTRING(`").append(columnName).append("`, ").append(prefix.length() + 1).append(") AS UNSIGNED) AS seq");
        sql.append(" FROM `").append(tableName).append("`");
        sql.append(" WHERE `").append(columnName).append("` REGEXP ?");
        if (deletedColumn != null && !deletedColumn.isEmpty()) {
            sql.append(" AND (").append(deletedColumn).append(" IS NULL OR ").append(deletedColumn).append(" = '')");
        }
        Set<Integer> usedNumbers = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, "^" + prefix + "[0-9]{1,4}$");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    usedNumbers.add(rs.getInt("seq"));
                }
            }
        }
        int nextSequence = 1;
        while (usedNumbers.contains(nextSequence) ||
               (batchRefsLower != null && batchRefsLower.contains((prefix + String.format("%03d", nextSequence)).toLowerCase()))) {
            nextSequence++;
        }
        return prefix + String.format("%03d", nextSequence);
    }
    
    /**
     * Generic method to generate reference numbers with dash format (e.g., BA-1, CAP-2)
     * @param prefix The prefix for the reference number
     * @param tableName The table name to check for uniqueness
     * @param columnName The column name to check for uniqueness
     * @return A unique reference number with dash format
     */
    private static String generateRefNumberWithDash(String prefix, String tableName, String columnName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return generateRefNumberWithDash(conn, prefix, tableName, columnName, null);
        }
    }

    /**
     * Generate reference number with dash format using provided connection and optionally excluding batch refs.
     * @param conn connection to use (not closed)
     * @param batchRefsLower optional set of refs already in batch (lowercase); if null, only DB is considered
     */
    private static String generateRefNumberWithDash(Connection conn, String prefix, String tableName, String columnName, Set<String> batchRefsLower) throws SQLException {
        // Collect all existing numeric refs into a set, then find the first gap starting from 1.
        // Only consider refs with up to 4 digits (PREFIX-1 to PREFIX-9999) to ignore legacy huge numbers.
        int numStartPos = prefix.length() + 2; // 1-based position of numeric part after dash (e.g. PRC- -> 5)
        String sql = "SELECT CAST(SUBSTRING(`" + columnName + "`, " + numStartPos + ") AS UNSIGNED) AS seq" +
                " FROM `" + tableName + "`" +
                " WHERE `" + columnName + "` REGEXP ?";
        Set<Integer> usedNumbers = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "^" + prefix + "-[0-9]{1,4}$");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    usedNumbers.add(rs.getInt("seq"));
                }
            }
        }
        int nextSequence = 1;
        while (usedNumbers.contains(nextSequence) ||
               (batchRefsLower != null && batchRefsLower.contains((prefix + "-" + nextSequence).toLowerCase()))) {
            nextSequence++;
        }
        return prefix + "-" + nextSequence;
    }
    
    /**
     * Generic method to generate reference numbers with underscore format (e.g., ATT_1, ATT_2)
     * @param prefix The prefix for the reference number
     * @param tableName The table name to check for uniqueness
     * @param columnName The column name to check for uniqueness
     * @return A unique reference number with underscore format
     */
    private static String generateRefNumberWithUnderscore(String prefix, String tableName, String columnName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return generateRefNumberWithUnderscore(conn, prefix, tableName, columnName, null, null);
        }
    }

    /**
     * Generate reference number with underscore format using provided connection and optionally excluding batch refs.
     * @param conn connection to use (not closed)
     * @param batchRefsLower optional set of refs already in batch (lowercase); if null, only DB is considered
     */
    private static String generateRefNumberWithUnderscore(Connection conn, String prefix, String tableName, String columnName, Set<String> batchRefsLower) throws SQLException {
        return generateRefNumberWithUnderscore(conn, prefix, tableName, columnName, batchRefsLower, null);
    }

    /**
     * Generate reference number with underscore format. When deletedColumn is non-null, only non-deleted rows
     * are considered so the next ref does not conflict with an active row.
     * @param conn connection to use (not closed)
     * @param batchRefsLower optional set of refs already in batch (lowercase); if null, only DB is considered
     * @param deletedColumn optional column name (e.g. DeletedDatetime) to filter out deleted rows; null to include all rows
     */
    private static String generateRefNumberWithUnderscore(Connection conn, String prefix, String tableName, String columnName, Set<String> batchRefsLower, String deletedColumn) throws SQLException {
        // Collect all existing numeric refs into a set, then find the first gap starting from 1.
        // Only consider refs with up to 4 digits to ignore legacy huge numbers.
        int numStartPos = prefix.length() + 2; // 1-based position of numeric part (e.g. ATT_ -> 5)
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT CAST(SUBSTRING(`").append(columnName).append("`, ").append(numStartPos).append(") AS UNSIGNED) AS seq");
        sql.append(" FROM `").append(tableName).append("` WHERE `").append(columnName).append("` REGEXP ?");
        if (deletedColumn != null && !deletedColumn.isEmpty()) {
            sql.append(" AND (").append(deletedColumn).append(" IS NULL OR ").append(deletedColumn).append(" = '')");
        }
        Set<Integer> usedNumbers = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, "^" + prefix + "_[0-9]{1,4}$");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    usedNumbers.add(rs.getInt("seq"));
                }
            }
        }
        int nextSequence = 1;
        while (usedNumbers.contains(nextSequence) ||
               (batchRefsLower != null && batchRefsLower.contains((prefix + "_" + nextSequence).toLowerCase()))) {
            nextSequence++;
        }
        return prefix + "_" + nextSequence;
    }
    
    /**
     * Checks if a reference number is empty or null
     * @param refNumber The reference number to check
     * @return true if empty or null, false otherwise
     */
    public static boolean isEmpty(String refNumber) {
        return refNumber == null || refNumber.trim().isEmpty();
    }
}
