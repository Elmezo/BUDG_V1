package com.example.budg_v2.service;

import com.example.budg_v2.bulk.relationships.config.EntityConfig;
import com.example.budg_v2.bulk.relationships.dto.CacheStats;
import com.example.budg_v2.bulk.relationships.dto.EntityResolutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for resolving entity IDs with session-level caching
 * Implements priority-based search: Reference → Name+Parent → Name only
 */
public class EntityResolverService {
    
    private static final Logger logger = LoggerFactory.getLogger(EntityResolverService.class);
    
    /**
     * Returns true if the value is null, empty, or contains only whitespace (including Unicode).
     * Used so that required entities fail when ref/name are whitespace-only.
     */
    private static boolean isBlankOrWhitespace(String value) {
        if (value == null) return true;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) return false;
        }
        return true;
    }

    /**
     * Normalize a name for DB lookup: trim, replace non-breaking space (U+00A0) with normal space,
     * replace underscores with space (so "BA_BU44" matches DB "BA BU44"), insert space at letter-digit
     * boundary (so "process4" matches DB "process 4"), collapse multiple spaces to one.
     * Reduces "not found" when DB or Excel have extra spaces, underscores, or Unicode variants.
     */
    private static String normalizeNameForLookup(String name) {
        if (name == null) return "";
        String s = name.trim();
        s = s.replace('\u00A0', ' ');
        s = s.replace('_', ' ');
        s = s.replaceAll("(?<=[A-Za-z])(?=\\d)", " ");
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }
    
    /**
     * Build SQL expression that normalizes a name column for comparison: TRIM, replace '_' with space,
     * LOWER, and collapse multiple spaces (so DB "legal  testttt" matches input "legal testttt").
     * Matches Java normalizeNameForLookup so Excel/DB space differences do not cause "not found".
     */
    private static String normalizeNameColumnSql(String nameColumn) {
        String base = "TRIM(REPLACE(" + nameColumn + ", '_', ' '))";
        String collapsed = "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(" + base + ", '  ', ' '), '  ', ' '), '  ', ' '), '  ', ' '), '  ', ' ')";
        return "LOWER(" + collapsed + ")";
    }
    
    /**
     * Main resolution method with caching
     * 
     * @param conn Database connection
     * @param entityConfig Entity configuration
     * @param refValue Reference value (optional)
     * @param nameValue Name value (optional)
     * @param parentValue Parent value (optional)
     * @param rowNumber Row number for error reporting
     * @param cache Session cache
     * @param cacheStats Cache statistics tracker
     * @return Resolution result with ID or warnings/errors
     */
    public EntityResolutionResult resolveEntity(Connection conn, EntityConfig entityConfig,
                                                 String refValue, String nameValue, String parentValue,
                                                 int rowNumber, Map<String, Integer> cache, 
                                                 CacheStats cacheStats) {
        try {
            // Validate input
            if (entityConfig == null) {
                return EntityResolutionResult.error("Entity configuration is null");
            }
            
            // Treat null, empty, and whitespace-only (including Unicode) as "not provided"
            if (isBlankOrWhitespace(refValue) && isBlankOrWhitespace(nameValue)) {
                if (entityConfig.isRequired()) {
                    return EntityResolutionResult.error(String.format(
                        "Missing required information: Either Reference or Name must be provided for %s. Please provide at least one identifier to locate the %s in the system.",
                        entityConfig.getName(), entityConfig.getName().toLowerCase()));
                } else {
                    // Optional entity with no data - this is okay
                    return EntityResolutionResult.success(null, "OPTIONAL_EMPTY");
                }
            }
            
            // Build cache key and check cache
            String cacheKey = buildCacheKey(entityConfig.getTableName(), refValue, nameValue, parentValue);
            if (cache.containsKey(cacheKey)) {
                // CRITICAL: Always check for null after cache.get() to prevent NullPointerException
                // Even though containsKey() returns true, the value might still be null
                Integer cachedId = cache.get(cacheKey);
                if (cachedId != null) {
                    // Additional defensive check: ensure cachedId is a valid positive integer
                    if (cachedId > 0) {
                        cacheStats.incrementEntityCacheHits();
                        logger.debug("Row {}: Cache hit for {} - ID={}", rowNumber, entityConfig.getName(), cachedId);
                        return EntityResolutionResult.success(cachedId, "CACHE");
                    } else {
                        // Cached ID is invalid (0 or negative) - log and continue to query
                        logger.warn("Row {}: Cache key '{}' exists but contains invalid ID value {} for {}. " +
                            "Proceeding to query database.",
                            rowNumber, cacheKey, cachedId, entityConfig.getName());
                    }
                } else {
                    // Cache key exists but value is null - this is unexpected, log and continue to query
                    logger.warn("Row {}: Cache key '{}' exists but value is null for {}. " +
                        "This may indicate a cache corruption issue. Proceeding to query database. " +
                        "This could cause NullPointerException if intValue() is called on the null value.",
                        rowNumber, cacheKey, entityConfig.getName());
                }
            }
            
            // Try resolution in priority order
            EntityResolutionResult result = null;
            
            // Priority 1: By Reference
            if (refValue != null && !refValue.trim().isEmpty() && entityConfig.hasRefColumn()) {
                result = resolveByReference(conn, entityConfig, refValue);
                if (result.isFound()) {
                    // If name is also provided, validate it points to the same entity
                    if (nameValue != null && !nameValue.trim().isEmpty()) {
                        EntityResolutionResult nameResult = resolveByName(conn, entityConfig, nameValue);
                        cacheStats.incrementTotalQueries();
                        if (nameResult.isFound() && !nameResult.isAmbiguous()) {
                            if (!nameResult.getId().equals(result.getId())) {
                                return EntityResolutionResult.error(String.format(
                                    "%s mismatch: The Reference '%s' and Name '%s' point to different %s objects. " +
                                    "Reference points to ID %d, but Name points to ID %d. " +
                                    "Please ensure both Reference and Name refer to the same %s.",
                                    entityConfig.getName(), refValue, nameValue, 
                                    entityConfig.getName().toLowerCase(), result.getId(), nameResult.getId(),
                                    entityConfig.getName().toLowerCase()));
                            }
                        } else if (nameResult.isFound() && nameResult.isAmbiguous()) {
                            // Name is ambiguous - check if ref ID is in the possible IDs
                            if (!nameResult.getPossibleIds().contains(result.getId())) {
                                return EntityResolutionResult.error(String.format(
                                    "%s mismatch: The Reference '%s' points to ID %d, but Name '%s' matches multiple %s objects " +
                                    "and none of them match the Reference. Please ensure both Reference and Name refer to the same %s.",
                                    entityConfig.getName(), refValue, result.getId(), nameValue,
                                    entityConfig.getName().toLowerCase(), entityConfig.getName().toLowerCase()));
                            }
                        }
                    }
                    result.setResolvedBy("REF");
                    cache.put(cacheKey, result.getId());
                    cacheStats.incrementTotalQueries();
                    logger.debug("Row {}: Resolved {} by reference - ID={}", 
                        rowNumber, entityConfig.getName(), result.getId());
                    
                    // Validate parent if provided
                    if (parentValue != null && !parentValue.trim().isEmpty() && entityConfig.hasParentColumn()) {
                        EntityResolutionResult parentValidation = validateParent(conn, entityConfig, result.getId(), parentValue, refValue, nameValue, rowNumber);
                        if (parentValidation.hasError()) {
                            return parentValidation;
                        }
                    }
                    
                    return result;
                } else {
                    // Reference was provided but not found - for policy, try resolving by name (ref value) once before failing
                    if ("policy".equalsIgnoreCase(entityConfig.getTableName()) && (nameValue == null || nameValue.trim().isEmpty())) {
                        EntityResolutionResult nameFallback = resolveByName(conn, entityConfig, refValue);
                        cacheStats.incrementTotalQueries();
                        if (nameFallback.isFound() && !nameFallback.isAmbiguous()) {
                            nameFallback.setResolvedBy("REF");
                            cache.put(cacheKey, nameFallback.getId());
                            logger.debug("Row {}: Policy ref not found, resolved by name (ref value) - ID={}", rowNumber, nameFallback.getId());
                            if (parentValue != null && !parentValue.trim().isEmpty() && entityConfig.hasParentColumn()) {
                                EntityResolutionResult parentValidation = validateParent(conn, entityConfig, nameFallback.getId(), parentValue, refValue, refValue, rowNumber);
                                if (parentValidation.hasError()) {
                                    return parentValidation;
                                }
                            }
                            return nameFallback;
                        }
                    }
                    // Project: try case-insensitive ref lookup when exact ref match fails (e.g. "ref66" matches DB "Ref66")
                    if ("project".equalsIgnoreCase(entityConfig.getTableName())) {
                        EntityResolutionResult projectRefFallback = resolveProjectByRefCaseInsensitive(conn, entityConfig, refValue);
                        cacheStats.incrementTotalQueries();
                        if (projectRefFallback.isFound()) {
                            projectRefFallback.setResolvedBy("REF");
                            cache.put(cacheKey, projectRefFallback.getId());
                            logger.debug("Row {}: Resolved project by ref (case-insensitive fallback) - ID={}", rowNumber, projectRefFallback.getId());
                            if (parentValue != null && !parentValue.trim().isEmpty() && entityConfig.hasParentColumn()) {
                                EntityResolutionResult parentValidation = validateParent(conn, entityConfig, projectRefFallback.getId(), parentValue, refValue, nameValue, rowNumber);
                                if (parentValidation.hasError()) {
                                    return parentValidation;
                                }
                            }
                            return projectRefFallback;
                        }
                    }
                    String errorMsg = result.getWarning() != null ? result.getWarning() : String.format(
                        "Reference not found: No %s found with the provided Reference. Please verify the Reference is correct.",
                        entityConfig.getName().toLowerCase());
                    return EntityResolutionResult.error(errorMsg);
                }
            }
            
            // Priority 2: By Name + Parent
            if (nameValue != null && !nameValue.trim().isEmpty() &&
                parentValue != null && !parentValue.trim().isEmpty() &&
                entityConfig.hasParentColumn()) {
                result = resolveByNameAndParent(conn, entityConfig, nameValue, parentValue);
                if (result.isFound() && !result.isAmbiguous()) {
                    // If ref is also provided, validate it points to the same entity
                    if (refValue != null && !refValue.trim().isEmpty() && entityConfig.hasRefColumn()) {
                        EntityResolutionResult refResult = resolveByReference(conn, entityConfig, refValue);
                        cacheStats.incrementTotalQueries();
                        if (refResult.isFound() && !refResult.getId().equals(result.getId())) {
                            return EntityResolutionResult.error(String.format(
                                "%s mismatch: The Reference '%s' and Name '%s' point to different %s objects. " +
                                "Reference points to ID %d, but Name+Parent points to ID %d. " +
                                "Please ensure both Reference and Name refer to the same %s.",
                                entityConfig.getName(), refValue, nameValue, 
                                entityConfig.getName().toLowerCase(), refResult.getId(), result.getId(),
                                entityConfig.getName().toLowerCase()));
                        }
                    }
                    result.setResolvedBy("NAME_PARENT");
                    cache.put(cacheKey, result.getId());
                    cacheStats.incrementTotalQueries();
                    logger.debug("Row {}: Resolved {} by name+parent - ID={}", 
                        rowNumber, entityConfig.getName(), result.getId());
                    
                    // Validate parent if provided (even though we used it for resolution, verify it matches actual parent)
                    if (parentValue != null && !parentValue.trim().isEmpty() && entityConfig.hasParentColumn()) {
                        EntityResolutionResult parentValidation = validateParent(conn, entityConfig, result.getId(), parentValue, refValue, nameValue, rowNumber);
                        if (parentValidation.hasError()) {
                            return parentValidation;
                        }
                    }
                    
                    return result;
                }
            }
            
            // Priority 3: By Name only
            if (nameValue != null && !nameValue.trim().isEmpty()) {
                result = resolveByName(conn, entityConfig, nameValue);
                cacheStats.incrementTotalQueries();
                
                if (!result.isFound()) {
                    String warning;
                    if ("legal".equalsIgnoreCase(entityConfig.getTableName()) && parentValue != null && !parentValue.trim().isEmpty()) {
                        warning = "Incompatible legal parent name: No legal found with the given Legal Entity Short Name and Parent Legal Entity Short Name. Please verify both the name and the parent are correct.";
                    } else {
                        warning = String.format("%s not found: No %s found with name '%s' in the database (searched in %s column). Please verify the name is correct and the %s exists in the system. You may also try using the Reference number instead.",
                            entityConfig.getName(), entityConfig.getName().toLowerCase(), nameValue, entityConfig.getNameColumn(), entityConfig.getName().toLowerCase());
                        if ("attribute".equalsIgnoreCase(entityConfig.getTableName())) {
                            warning += " Check spelling (e.g. 'Service' not 'Serivce').";
                        }
                    }
                    result.setWarning(warning);
                    logger.debug("Row {}: {} not found - {} (column: {})", 
                        rowNumber, entityConfig.getName(), nameValue, entityConfig.getNameColumn());
                } else if (result.isAmbiguous()) {
                    String warning = String.format("Ambiguous match: Multiple %s found with name '%s' (%d matches). Please provide the Parent Name or Reference number to uniquely identify the correct %s.",
                        entityConfig.getName(), nameValue, result.getPossibleIds().size(), entityConfig.getName().toLowerCase());
                    result.setWarning(warning);
                    logger.debug("Row {}: {} ambiguous - {} matches for '{}'", 
                        rowNumber, entityConfig.getName(), result.getPossibleIds().size(), nameValue);
                } else {
                    // If ref is also provided, validate it points to the same entity
                    if (refValue != null && !refValue.trim().isEmpty() && entityConfig.hasRefColumn()) {
                        EntityResolutionResult refResult = resolveByReference(conn, entityConfig, refValue);
                        cacheStats.incrementTotalQueries();
                        if (refResult.isFound() && !refResult.getId().equals(result.getId())) {
                            return EntityResolutionResult.error(String.format(
                                "%s mismatch: The Reference '%s' and Name '%s' point to different %s objects. " +
                                "Reference points to ID %d, but Name points to ID %d. " +
                                "Please ensure both Reference and Name refer to the same %s.",
                                entityConfig.getName(), refValue, nameValue, 
                                entityConfig.getName().toLowerCase(), refResult.getId(), result.getId(),
                                entityConfig.getName().toLowerCase()));
                        }
                    }
                    result.setResolvedBy("NAME");
                    cache.put(cacheKey, result.getId());
                    logger.debug("Row {}: Resolved {} by name - ID={}", 
                        rowNumber, entityConfig.getName(), result.getId());
                    
                    // Validate parent if provided
                    if (parentValue != null && !parentValue.trim().isEmpty() && entityConfig.hasParentColumn()) {
                        EntityResolutionResult parentValidation = validateParent(conn, entityConfig, result.getId(), parentValue, refValue, nameValue, rowNumber);
                        if (parentValidation.hasError()) {
                            return parentValidation;
                        }
                    }
                }
                
                return result;
            }
            
            // Should not reach here
            return EntityResolutionResult.error(String.format(
                "Unable to resolve %s: No valid identifier (Reference or Name) was provided or could be resolved. Please verify the provided values are correct.",
                entityConfig.getName()));
            
        } catch (SQLException e) {
            logger.error("SQL error resolving entity at row {}: {}", rowNumber, e.getMessage(), e);
            return EntityResolutionResult.error("Database error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error resolving entity at row {}: {}", rowNumber, e.getMessage(), e);
            return EntityResolutionResult.error("Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Resolve entity by reference number
     */
    private EntityResolutionResult resolveByReference(Connection conn, EntityConfig entityConfig, 
                                                      String refValue) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getIdColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE ").append(entityConfig.getRefColumn()).append(" = ?");
        
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        
        sql.append(" LIMIT 1");
        
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, refValue.trim());
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Integer id = rs.getInt(1);
                    return EntityResolutionResult.success(id, "REF");
                }
            }
        }
        
        return EntityResolutionResult.notFound(String.format(
            "Reference not found: No %s found with Reference '%s' in the database. Please verify the Reference number is correct and the %s exists in the system.",
            entityConfig.getName().toLowerCase(), refValue, entityConfig.getName().toLowerCase()));
    }
    
    /**
     * Project ref fallback: case-insensitive ref lookup so "ref66" matches DB "Ref66".
     */
    private EntityResolutionResult resolveProjectByRefCaseInsensitive(Connection conn, EntityConfig entityConfig, String refValue) throws SQLException {
        String refColumn = entityConfig.getRefColumn();
        if (refColumn == null || refColumn.isEmpty()) {
            return EntityResolutionResult.notFound(null);
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getIdColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE LOWER(TRIM(").append(refColumn).append(")) = LOWER(?)");
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        sql.append(" LIMIT 1");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, refValue.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return EntityResolutionResult.success(rs.getInt(1), "REF");
                }
            }
        }
        return EntityResolutionResult.notFound(null);
    }
    
    /**
     * Resolve entity by name and parent
     * Note: After resolution, the parent should be validated to ensure the provided parent name
     * actually matches the entity's actual parent (not just used for disambiguation)
     */
    private EntityResolutionResult resolveByNameAndParent(Connection conn, EntityConfig entityConfig,
                                                          String nameValue, String parentValue) throws SQLException {
        String normalizedName = normalizeNameForLookup(nameValue);
        String nameColumn = entityConfig.getNameColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getIdColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE ").append(normalizeNameColumnSql(nameColumn)).append(" = LOWER(?)")
           .append(" AND ").append(entityConfig.getParentColumn()).append(" = ?");
        
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        
        // Resolve parent value to ID first
        Integer parentIdToSearch;
        try {
            parentIdToSearch = Integer.parseInt(parentValue.trim());
        } catch (NumberFormatException e) {
            // Parent value is not an ID, need to look it up
            parentIdToSearch = resolveParentByName(conn, entityConfig, parentValue);
            if (parentIdToSearch == null) {
                String msg = "legal".equalsIgnoreCase(entityConfig.getTableName())
                    ? "Incompatible legal parent name: The parent Legal Entity specified does not exist. Please verify the Parent Legal Entity Short Name."
                    : String.format(
                        "Parent not found: The parent %s '%s' for %s '%s' does not exist in the database. Please verify the parent name is correct.",
                        entityConfig.getName().toLowerCase(), parentValue, entityConfig.getName().toLowerCase(), normalizedName);
                return EntityResolutionResult.notFound(msg);
            }
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, normalizedName);
            ps.setInt(2, parentIdToSearch);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Integer id = rs.getInt(1);
                    // CRITICAL: Even though we found an entity by name+parent, we must verify
                    // that the provided parent name actually matches the entity's actual parent.
                    // This prevents wrong parent names from being accepted if they happen to
                    // match a different entity's parent structure.
                    // Note: The actual validation will be done in validateParent() which is called
                    // after this method returns, but we return the result here for consistency.
                    return EntityResolutionResult.success(id, "NAME_PARENT");
                }
            }
        }
        
        String msg = "legal".equalsIgnoreCase(entityConfig.getTableName())
            ? "Incompatible legal parent name: No legal found with the given Legal Entity Short Name and Parent Legal Entity Short Name. Please verify both the name and the parent are correct."
            : String.format(
                "Name and Parent combination not found: No %s found with name '%s' and parent '%s' in the database. Please verify both the name and parent are correct.",
                entityConfig.getName().toLowerCase(), normalizedName, parentValue);
        return EntityResolutionResult.notFound(msg);
    }
    
    /**
     * Resolve entity by name only (may return multiple results)
     * Query format: SELECT ID FROM table WHERE LOWER(REPLACE(TRIM(NameColumn), '_', ' ')) = LOWER(?)
     * Input is normalized (trim, collapse spaces, replace underscore with space) so Excel/DB underscore/space differences match.
     */
    private EntityResolutionResult resolveByName(Connection conn, EntityConfig entityConfig,
                                                 String nameValue) throws SQLException {
        String normalizedName = normalizeNameForLookup(nameValue);
        String nameColumn = entityConfig.getNameColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getIdColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE ").append(normalizeNameColumnSql(nameColumn)).append(" = LOWER(?)");
        
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        
        String finalSql = sql.toString();
        logger.debug("Resolving {} by name: SQL={}, value={}", 
            entityConfig.getName(), finalSql, normalizedName);
        
        List<Integer> ids = new ArrayList<>();
        
        try (PreparedStatement ps = conn.prepareStatement(finalSql)) {
            ps.setString(1, normalizedName);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        
        if (ids.isEmpty() && normalizedName != null && !normalizedName.isEmpty()) {
            // Fallback: try simpler match (LOWER(TRIM(column)) = LOWER(?)) so DB values with different spacing/encoding still match
            ids = resolveByNameFallback(conn, entityConfig, normalizedName);
        }
        // Glossary: try PrimaryName early (before underscore-to-space) so display name in PrimaryName is found when Name column is empty or different
        if (ids.isEmpty() && "glossary".equalsIgnoreCase(entityConfig.getTableName()) && normalizedName != null && !normalizedName.isEmpty()) {
            ids = resolveGlossaryByPrimaryName(conn, normalizedName);
            if (!ids.isEmpty()) {
                logger.debug("Resolved glossary by PrimaryName (early) - {} match(es)", ids.size());
            }
        }
        // Process: try trimmed-only fallback early when normalized name differs from raw trimmed (e.g. "process 4" vs "process4") so "process4" matches DB "process4"
        if (ids.isEmpty() && "process".equalsIgnoreCase(entityConfig.getTableName()) && nameValue != null && !nameValue.trim().isEmpty()) {
            String trimmedOnly = nameValue.trim();
            if (!trimmedOnly.equals(normalizedName)) {
                ids = resolveByNameProcessFallback(conn, entityConfig, trimmedOnly);
                if (!ids.isEmpty()) {
                    logger.debug("Resolved process by name (trimmed-only fallback, early) - {} match(es)", ids.size());
                }
            }
        }
        if (ids.isEmpty() && normalizedName != null && !normalizedName.isEmpty()) {
            // Second fallback: match with DB underscore replaced by space so "Dataset_Test5" matches input "dataset test5"
            ids = resolveByNameFallbackUnderscoreToSpace(conn, entityConfig, normalizedName);
        }
        if (ids.isEmpty() && "process".equalsIgnoreCase(entityConfig.getTableName()) && nameValue != null && !nameValue.trim().isEmpty()) {
            // Process-specific: try match without letter-digit boundary normalization so "ahmed12" matches DB "ahmed12"
            String trimmedOnly = nameValue.trim();
            ids = resolveByNameProcessFallback(conn, entityConfig, trimmedOnly);
            if (!ids.isEmpty()) {
                logger.debug("Resolved process by name (trimmed-only fallback) - {} match(es)", ids.size());
            }
        }
        if (ids.isEmpty() && "glossary".equalsIgnoreCase(entityConfig.getTableName()) && normalizedName != null && !normalizedName.isEmpty()) {
            // Glossary-specific: try PrimaryName column (display name may be stored in PrimaryName)
            ids = resolveGlossaryByPrimaryName(conn, normalizedName);
            if (!ids.isEmpty()) {
                logger.debug("Resolved glossary by PrimaryName - {} match(es)", ids.size());
            }
        }
        if (ids.isEmpty() && "glossary".equalsIgnoreCase(entityConfig.getTableName())
                && entityConfig.hasDeletedColumn() && normalizedName != null && !normalizedName.isEmpty()) {
            // Glossary-specific: if still not found, check whether a deleted row exists (so we can show a clear message)
            List<Integer> deletedIds = resolveByNameIncludingDeleted(conn, entityConfig, normalizedName);
            if (deletedIds.size() == 1) {
                logger.debug("Glossary '{}' found but marked as deleted", normalizedName);
                return EntityResolutionResult.error("Glossary found but it is marked as deleted.");
            }
        }
        if (ids.isEmpty() && "glossary".equalsIgnoreCase(entityConfig.getTableName()) && entityConfig.hasRefColumn()
                && normalizedName != null && !normalizedName.isEmpty()) {
            // Glossary-specific: try Ref_Number when Name and PrimaryName failed (value in name column may be a reference)
            EntityResolutionResult refResult = resolveByReference(conn, entityConfig, normalizedName);
            if (refResult.isFound()) {
                logger.debug("Resolved glossary by Ref_Number (name column value) - ID={}", refResult.getId());
                return EntityResolutionResult.success(refResult.getId(), "REF");
            }
        }
        if (ids.isEmpty() && "attribute".equalsIgnoreCase(entityConfig.getTableName()) && nameValue != null && !nameValue.trim().isEmpty()) {
            // Attribute-specific: try trimmed-only lookup (no letter-digit normalization) so "att1" matches DB "att1"
            ids = resolveByNameAttributeFallback(conn, entityConfig, nameValue.trim());
            if (!ids.isEmpty()) {
                logger.debug("Resolved attribute by name (trimmed-only fallback) - {} match(es)", ids.size());
            }
        }
        if (ids.isEmpty() && "business_area".equalsIgnoreCase(entityConfig.getTableName()) && nameValue != null && !nameValue.trim().isEmpty()) {
            // Business Area: try RefNumber when name (PrimaryName) lookup fails (e.g. BA_BU66 stored in RefNumber)
            ids = resolveBusinessAreaByRefNumber(conn, entityConfig, nameValue.trim());
            if (!ids.isEmpty()) {
                logger.debug("Resolved business area by RefNumber fallback - {} match(es)", ids.size());
            }
        }
        if (ids.isEmpty() && "legal".equalsIgnoreCase(entityConfig.getTableName()) && nameValue != null && !nameValue.trim().isEmpty()) {
            // Legal: try LongName when ShortName lookup fails (e.g. value stored in LongName like "legal testttt")
            ids = resolveLegalByLongName(conn, entityConfig, nameValue.trim());
            if (!ids.isEmpty()) {
                logger.debug("Resolved legal by LongName fallback - {} match(es)", ids.size());
            }
        }
        if (ids.isEmpty() && "legal".equalsIgnoreCase(entityConfig.getTableName()) && nameValue != null && !nameValue.trim().isEmpty()) {
            // Legal: try ShortName with raw trimmed value only (no underscore-to-space normalization) so case/encoding edge cases match (e.g. "lE_test5" vs DB "LE_test5")
            ids = resolveLegalByShortNameTrimOnly(conn, entityConfig, nameValue.trim());
            if (!ids.isEmpty()) {
                logger.debug("Resolved legal by ShortName trim-only fallback - {} match(es)", ids.size());
            }
        }
        if (ids.isEmpty() && "process".equalsIgnoreCase(entityConfig.getTableName()) && nameValue != null && !nameValue.trim().isEmpty() && entityConfig.hasRefColumn()) {
            // Process: try refnumber when primaryname lookup fails (e.g. value stored in refnumber like "teeeesssstt")
            ids = resolveProcessByRefNumber(conn, entityConfig, nameValue.trim());
            if (!ids.isEmpty()) {
                logger.debug("Resolved process by RefNumber fallback - {} match(es)", ids.size());
            }
        }
        if (ids.isEmpty() && "process".equalsIgnoreCase(entityConfig.getTableName()) && nameValue != null && !nameValue.trim().isEmpty()) {
            // Process: try match with spaces removed so "process 66" matches DB "process66"
            ids = resolveProcessByNameNoSpaces(conn, entityConfig, nameValue.trim());
            if (!ids.isEmpty()) {
                logger.debug("Resolved process by name (no-spaces fallback) - {} match(es)", ids.size());
            }
        }
        if (ids.isEmpty() && "project".equalsIgnoreCase(entityConfig.getTableName()) && nameValue != null && !nameValue.trim().isEmpty() && entityConfig.hasRefColumn()) {
            // Project: try refnumber when primaryname lookup fails (e.g. user entered ref in name column, or primaryname differs)
            EntityResolutionResult refResult = resolveByReference(conn, entityConfig, nameValue.trim());
            if (refResult.isFound()) {
                logger.debug("Resolved project by RefNumber (name column value) - ID={}", refResult.getId());
                return EntityResolutionResult.success(refResult.getId(), "REF");
            }
        }
        if (ids.isEmpty()) {
            logger.debug("No {} found with {} = '{}' (normalized input)", 
                entityConfig.getName(), nameColumn, normalizedName);
            String msg = String.format(
                "Name not found: No %s found with name '%s' in the database (searched in %s column). Please verify the name is correct and the %s exists in the system. You may also try using the Reference number instead.",
                entityConfig.getName().toLowerCase(), normalizedName, nameColumn, entityConfig.getName().toLowerCase());
            if ("attribute".equalsIgnoreCase(entityConfig.getTableName())) {
                msg += " Check spelling (e.g. 'Service' not 'Serivce').";
            }
            return EntityResolutionResult.notFound(msg);
        } else if (ids.size() == 1) {
            logger.debug("Found {} with {} = '{}', ID={}", 
                entityConfig.getName(), nameColumn, normalizedName, ids.get(0));
            return EntityResolutionResult.success(ids.get(0), "NAME");
        } else {
            // Multiple matches - ambiguous
            logger.debug("Multiple {} found with {} = '{}', count={}", 
                entityConfig.getName(), nameColumn, normalizedName, ids.size());
            return EntityResolutionResult.ambiguous(ids, "Ambiguous match - multiple results");
        }
    }
    
    /**
     * Fallback name lookup: LOWER(TRIM(nameColumn)) = LOWER(?).
     * Used when the normalized (underscore-to-space, collapse spaces) query returns no row,
     * so DB values with different spacing or encoding can still match (e.g. legal ShortName "gg").
     */
    private List<Integer> resolveByNameFallback(Connection conn, EntityConfig entityConfig, String normalizedName) throws SQLException {
        String nameColumn = entityConfig.getNameColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getIdColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE LOWER(TRIM(").append(nameColumn).append(")) = LOWER(?)");
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, normalizedName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        if (!ids.isEmpty()) {
            logger.debug("Resolved {} by name fallback (LOWER(TRIM)) - {} match(es)", entityConfig.getName(), ids.size());
        }
        return ids;
    }

    /**
     * Fallback name lookup with underscore-to-space on DB column: LOWER(REPLACE(TRIM(nameColumn), '_', ' ')) = LOWER(?).
     * Used when the main query and first fallback return no row, so DB values like "Dataset_Test5" match normalized input "dataset test5".
     */
    private List<Integer> resolveByNameFallbackUnderscoreToSpace(Connection conn, EntityConfig entityConfig, String normalizedName) throws SQLException {
        String nameColumn = entityConfig.getNameColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getIdColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE LOWER(REPLACE(TRIM(").append(nameColumn).append("), '_', ' ')) = LOWER(?)");
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, normalizedName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        if (!ids.isEmpty()) {
            logger.debug("Resolved {} by name fallback (REPLACE underscore) - {} match(es)", entityConfig.getName(), ids.size());
        }
        return ids;
    }

    /**
     * Process-specific fallback: LOWER(TRIM(primaryname)) = LOWER(?).
     * Uses trimmed input only (no letter-digit boundary space) so "ahmed12" matches DB "ahmed12"
     * when the main normalized lookup would compare "ahmed 12" and fail.
     */
    private List<Integer> resolveByNameProcessFallback(Connection conn, EntityConfig entityConfig, String trimmedName) throws SQLException {
        String nameColumn = entityConfig.getNameColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getIdColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE LOWER(TRIM(").append(nameColumn).append(")) = LOWER(?)");
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, trimmedName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /**
     * Process-specific fallback: match with spaces removed from both DB column and input.
     * So "process 66" matches DB "process66" when LOWER(TRIM) would not.
     */
    private List<Integer> resolveProcessByNameNoSpaces(Connection conn, EntityConfig entityConfig, String trimmedName) throws SQLException {
        String nameColumn = entityConfig.getNameColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getIdColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE LOWER(REPLACE(TRIM(").append(nameColumn).append("), ' ', '')) = LOWER(REPLACE(?, ' ', ''))");
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, trimmedName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /**
     * Attribute-specific fallback: LOWER(TRIM(PrimaryName)) = LOWER(?).
     * Uses trimmed input only (no letter-digit boundary space) so "att1" matches DB "att1"
     * when the main normalized lookup would compare "att 1" and fail.
     */
    private List<Integer> resolveByNameAttributeFallback(Connection conn, EntityConfig entityConfig, String trimmedName) throws SQLException {
        String nameColumn = entityConfig.getNameColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getIdColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE LOWER(TRIM(").append(nameColumn).append(")) = LOWER(?)");
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, trimmedName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /**
     * Business-area fallback: lookup by RefNumber when name (PrimaryName) lookup returns no rows.
     * business_area table has RefNumber; config may have refColumn = null so name-only resolution fails for values like BA_BU66.
     */
    private List<Integer> resolveBusinessAreaByRefNumber(Connection conn, EntityConfig entityConfig, String value) throws SQLException {
        String tableName = entityConfig.getTableName();
        String idColumn = entityConfig.getIdColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(idColumn).append(" FROM ").append(tableName)
           .append(" WHERE RefNumber = ?");
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /**
     * Legal fallback: lookup by LongName when ShortName lookup returned no row.
     * Queries legal table: LOWER(TRIM(LongName)) = LOWER(value) and deleted column IS NULL.
     */
    private List<Integer> resolveLegalByLongName(Connection conn, EntityConfig entityConfig, String value) throws SQLException {
        String tableName = entityConfig.getTableName();
        String idColumn = entityConfig.getIdColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(idColumn).append(" FROM ").append(tableName)
           .append(" WHERE LOWER(TRIM(LongName)) = LOWER(?)");
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /**
     * Legal fallback: lookup by ShortName with raw trimmed value only (LOWER(TRIM(ShortName)) = LOWER(?)).
     * Used when normalized (underscore-to-space, etc.) lookup and LongName fallback returned no row,
     * so case or DB collation edge cases still match (e.g. "lE_test5" vs DB "LE_test5").
     */
    private List<Integer> resolveLegalByShortNameTrimOnly(Connection conn, EntityConfig entityConfig, String rawTrimmedValue) throws SQLException {
        String tableName = entityConfig.getTableName();
        String idColumn = entityConfig.getIdColumn();
        String nameColumn = entityConfig.getNameColumn();
        if (nameColumn == null || nameColumn.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(idColumn).append(" FROM ").append(tableName)
           .append(" WHERE LOWER(TRIM(").append(nameColumn).append(")) = LOWER(?)");
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, rawTrimmedValue);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /**
     * Process fallback: lookup by ref column (e.g. refnumber) when primaryname lookup returned no row.
     */
    private List<Integer> resolveProcessByRefNumber(Connection conn, EntityConfig entityConfig, String value) throws SQLException {
        String tableName = entityConfig.getTableName();
        String idColumn = entityConfig.getIdColumn();
        String refColumn = entityConfig.getRefColumn();
        if (refColumn == null || refColumn.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(idColumn).append(" FROM ").append(tableName)
           .append(" WHERE LOWER(TRIM(").append(refColumn).append(")) = LOWER(?)");
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /**
     * Lookup by name without Deleted_datetime filter (glossary only).
     * Used when the main query and fallbacks return no row: if a single deleted row matches,
     * we return a clear "marked as deleted" error instead of "not found".
     */
    private List<Integer> resolveByNameIncludingDeleted(Connection conn, EntityConfig entityConfig, String normalizedName) throws SQLException {
        String nameColumn = entityConfig.getNameColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getIdColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE LOWER(TRIM(").append(nameColumn).append(")) = LOWER(?)");
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, normalizedName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /**
     * Glossary-specific: resolve by PrimaryName when Name column search returns no row.
     * Display names may be stored in PrimaryName in the glossary table.
     * Uses same normalization as main Name column (collapse spaces, underscore to space) so "Glossary  Test  3" matches "glossary test 3".
     */
    private List<Integer> resolveGlossaryByPrimaryName(Connection conn, String normalizedName) throws SQLException {
        String nameExpr = normalizeNameColumnSql("PrimaryName");
        String sql = "SELECT ID FROM glossary WHERE " + nameExpr + " = LOWER(?) AND (Deleted_datetime IS NULL OR Deleted_datetime = '')";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("ID"));
                }
            }
        }
        return ids;
    }

    /**
     * Glossary-specific: resolve by PrimaryName with LOWER(TRIM(PrimaryName)) = LOWER(?).
     * Used when normalized PrimaryName match fails; handles minor spacing/case differences between input and DB.
     */
    private List<Integer> resolveGlossaryByPrimaryNameTrimFallback(Connection conn, String trimmedName) throws SQLException {
        String sql = "SELECT ID FROM glossary WHERE LOWER(TRIM(PrimaryName)) = LOWER(?) AND (Deleted_datetime IS NULL OR Deleted_datetime = '')";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trimmedName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("ID"));
                }
            }
        }
        return ids;
    }

    /**
     * Helper: Resolve parent ID by name
     */
    private Integer resolveParentByName(Connection conn, EntityConfig entityConfig, String parentName) throws SQLException {
        String normalizedParentName = normalizeNameForLookup(parentName);
        String nameColumn = entityConfig.getNameColumn();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getIdColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE ").append(normalizeNameColumnSql(nameColumn)).append(" = LOWER(?)");
        
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        
        sql.append(" LIMIT 1");
        
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, normalizedParentName);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Build cache key from entity identifiers
     */
    public String buildCacheKey(String tableName, String refValue, String nameValue, String parentValue) {
        StringBuilder key = new StringBuilder(tableName);
        
        if (refValue != null && !refValue.trim().isEmpty()) {
            key.append(":REF:").append(refValue.trim().toLowerCase());
        }
        
        if (nameValue != null && !nameValue.trim().isEmpty()) {
            key.append(":NAME:").append(nameValue.trim().toLowerCase());
        }
        
        if (parentValue != null && !parentValue.trim().isEmpty()) {
            key.append(":PARENT:").append(parentValue.trim().toLowerCase());
        }
        
        return key.toString();
    }
    
    /**
     * Validate that the provided parent matches the actual parent of the resolved entity
     * 
     * @param conn Database connection
     * @param entityConfig Entity configuration
     * @param entityId The resolved entity ID
     * @param providedParentValue The parent value provided by the user
     * @param refValue Reference value (for error messages)
     * @param nameValue Name value (for error messages)
     * @param rowNumber Row number for logging
     * @return EntityResolutionResult with error if parent doesn't match, success otherwise
     */
    private EntityResolutionResult validateParent(Connection conn, EntityConfig entityConfig,
                                                 Integer entityId, String providedParentValue,
                                                 String refValue, String nameValue, int rowNumber) {
        try {
            // Get the actual parent ID of the resolved entity
            Integer actualParentId = getEntityParentId(conn, entityConfig, entityId);
            
            // If entity has no parent, provided parent should also be empty/null
            if (actualParentId == null) {
                // Check if provided parent is empty or null
                if (providedParentValue == null || providedParentValue.trim().isEmpty()) {
                    return EntityResolutionResult.success(entityId, "PARENT_VALIDATED");
                } else {
                    return EntityResolutionResult.error(String.format(
                        "%s parent mismatch: The %s with %s '%s' has no parent, but you provided Parent '%s'. " +
                        "Please remove the parent value or verify the %s identifier is correct.",
                        entityConfig.getName(), entityConfig.getName().toLowerCase(),
                        refValue != null ? "Reference" : "Name",
                        refValue != null ? refValue : nameValue,
                        providedParentValue, entityConfig.getName().toLowerCase()));
                }
            }
            
            // Resolve the provided parent value to an ID
            Integer providedParentId = resolveParentValue(conn, entityConfig, providedParentValue);
            
            if (providedParentId == null) {
                // Parent name provided but not found in database - return error
                String entityIdentifier = refValue != null ? refValue : (nameValue != null ? nameValue : "ID " + entityId);
                return EntityResolutionResult.error(String.format(
                    "%s parent not found: The parent %s '%s' specified for %s '%s' does not exist in the database. " +
                    "Please verify the parent name is correct or remove it if the %s has no parent.",
                    entityConfig.getName(), entityConfig.getName().toLowerCase(), providedParentValue,
                    entityConfig.getName().toLowerCase(), entityIdentifier, entityConfig.getName().toLowerCase()));
            }
            
            // Compare parent IDs
            if (!providedParentId.equals(actualParentId)) {
                // Get actual parent name for better error message
                String actualParentName = getEntityParentName(conn, entityConfig, actualParentId);
                String entityIdentifier = refValue != null ? refValue : (nameValue != null ? nameValue : "ID " + entityId);
                String errorMsg;
                if ("product".equalsIgnoreCase(entityConfig.getName())) {
                    errorMsg = String.format(
                        "Incompatible product parent name: The provided Parent '%s' does not match the actual parent of the product. " +
                        "The product with %s '%s' has parent '%s' (not '%s'). Please verify the parent name is correct.",
                        providedParentValue,
                        refValue != null ? "Reference" : "Name",
                        entityIdentifier,
                        actualParentName != null ? actualParentName : "ID " + actualParentId,
                        providedParentValue);
                } else {
                    errorMsg = String.format(
                        "%s parent mismatch: The provided Parent '%s' does not match the actual parent of the %s. " +
                        "The %s with %s '%s' has parent '%s' (not '%s'). Please verify the parent name is correct.",
                        entityConfig.getName(), providedParentValue, entityConfig.getName().toLowerCase(),
                        entityConfig.getName().toLowerCase(),
                        refValue != null ? "Reference" : "Name",
                        entityIdentifier,
                        actualParentName != null ? actualParentName : "ID " + actualParentId,
                        providedParentValue);
                }
                logger.debug("Row {}: Parent validation failed - provided '{}' (ID: {}), actual '{}' (ID: {})",
                    rowNumber, providedParentValue, providedParentId, actualParentName, actualParentId);
                return EntityResolutionResult.error(errorMsg);
            }
            
            // Additional verification: Check that the provided parent name matches the actual parent name
            // This catches cases where wrong parent name accidentally resolves to correct parent ID
            String actualParentName = getEntityParentName(conn, entityConfig, actualParentId);
            if (actualParentName != null && !actualParentName.trim().equalsIgnoreCase(providedParentValue.trim())) {
                // Parent ID matches but name doesn't - this shouldn't happen, but verify anyway
                logger.warn("Row {}: Parent ID matches but name differs - provided '{}', actual '{}'. " +
                    "This may indicate a data inconsistency.", rowNumber, providedParentValue, actualParentName);
                // Still return success since ID matches, but log warning
            }
            
            return EntityResolutionResult.success(entityId, "PARENT_VALIDATED");
            
        } catch (SQLException e) {
            logger.error("SQL error validating parent at row {}: {}", rowNumber, e.getMessage(), e);
            // Return error instead of success to prevent wrong data from being accepted
            return EntityResolutionResult.error(String.format(
                "Error validating %s parent: %s. Please verify the parent name is correct.",
                entityConfig.getName().toLowerCase(), e.getMessage()));
        }
    }
    
    /**
     * Get the parent ID of an entity
     * 
     * @param conn Database connection
     * @param entityConfig Entity configuration
     * @param entityId The entity ID
     * @return Parent ID, or null if entity has no parent
     */
    private Integer getEntityParentId(Connection conn, EntityConfig entityConfig, Integer entityId) throws SQLException {
        if (!entityConfig.hasParentColumn() || entityId == null) {
            return null;
        }
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getParentColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE ").append(entityConfig.getIdColumn()).append(" = ?");
        
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        
        sql.append(" LIMIT 1");
        
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, entityId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Integer parentId = rs.getInt(1);
                    if (rs.wasNull()) {
                        return null;
                    }
                    return parentId;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Resolve parent value to an ID (handles both ID and name)
     * 
     * @param conn Database connection
     * @param entityConfig Entity configuration
     * @param parentValue The parent value (can be ID or name)
     * @return Parent ID, or null if not found
     */
    private Integer resolveParentValue(Connection conn, EntityConfig entityConfig, String parentValue) throws SQLException {
        if (parentValue == null || parentValue.trim().isEmpty()) {
            return null;
        }
        
        // Try to parse as integer ID first
        try {
            int parentId = Integer.parseInt(parentValue.trim());
            return parentId;
        } catch (NumberFormatException e) {
            // Not an ID, resolve by name
            return resolveParentByName(conn, entityConfig, parentValue);
        }
    }
    
    /**
     * Get the parent name of an entity by parent ID
     * 
     * @param conn Database connection
     * @param entityConfig Entity configuration
     * @param parentId The parent entity ID
     * @return Parent name, or null if not found or parentId is null
     */
    private String getEntityParentName(Connection conn, EntityConfig entityConfig, Integer parentId) throws SQLException {
        if (parentId == null) {
            return null;
        }
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(entityConfig.getNameColumn())
           .append(" FROM ").append(entityConfig.getTableName())
           .append(" WHERE ").append(entityConfig.getIdColumn()).append(" = ?");
        
        if (entityConfig.hasDeletedColumn()) {
            sql.append(" AND ").append(entityConfig.getDeletedColumn()).append(" IS NULL");
        }
        
        sql.append(" LIMIT 1");
        
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, parentId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        
        return null;
    }
}

