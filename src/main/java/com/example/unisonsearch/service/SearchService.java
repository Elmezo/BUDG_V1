package com.example.unisonsearch.service;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.unisonsearch.model.QueryResult;
import com.example.unisonsearch.model.SearchParams;
import com.example.unisonsearch.model.SegmentAccessContext;
import com.example.unisonsearch.repository.DatabaseHelper;
import com.example.unisonsearch.repository.QueryBuilder;
import com.google.gson.JsonObject;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates search: build SQL, execute, and return mapped results.
 * Cache disabled to ensure deleted records are immediately filtered from
 * results.
 */
public class SearchService {
    private final QueryBuilder queryBuilder;
    private final DatabaseHelper databaseHelper;
    
    // Track query execution for debugging duplicate queries
    // Key format: "module:userId:sqlHash" for more accurate duplicate detection
    private static final Map<String, AtomicInteger> queryCallCounts = new ConcurrentHashMap<>();
    private static final Map<String, Long> queryLastCallTime = new ConcurrentHashMap<>();
    // Cache recent query hashes to detect exact duplicates
    private static final Map<String, String> queryHashCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    public SearchService(QueryBuilder queryBuilder, DatabaseHelper databaseHelper) {
        this.queryBuilder = queryBuilder;
        this.databaseHelper = databaseHelper;
    }

    public List<Map<String, Object>> search(SearchParams params) throws SQLException {
        QueryResult qr = queryBuilder.build(params);
        if (qr.sql == null || qr.sql.trim().isEmpty()) {
            return Collections.emptyList();
        }
        logQuery("search", qr);
        List<Map<String, Object>> rows = databaseHelper.executeQuery(qr.sql, qr.parameters);
        enrichRowsWithActualSegment(rows, params.module);
        enrichWithCustomFields(rows, params.module);
        return rows;
    }

    /**
     * Execute a search using an BUDG-style searchGroups definition.
     * Used by UnisonSearchService to honor filterGroups and field-level FIND.
     */
    public List<Map<String, Object>> searchWithDefinition(String module, JsonObject searchGroupsJson)
            throws SQLException {
        return searchWithDefinition(module, searchGroupsJson, (Integer) null);
    }

    /**
     * Execute a search using an BUDG-style searchGroups definition with segment
     * filtering using AccessContext.
     * Used by UnisonSearchService to honor filterGroups and field-level FIND.
     */
    public List<Map<String, Object>> searchWithDefinition(String module, JsonObject searchGroupsJson, SegmentAccessContext accessCtx)
            throws SQLException {
        if (accessCtx == null) {
            return searchWithDefinition(module, searchGroupsJson, (Integer) null);
        }
        long startTime = System.currentTimeMillis();
        QueryResult qr = queryBuilder.buildFromSearchGroupsWithContext(module, searchGroupsJson, accessCtx);
        if (qr.sql == null || qr.sql.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String sqlHash = Integer.toHexString(qr.sql.hashCode());
        String contextStr = accessCtx.isSuperAdmin() ? "superadmin" : "user_" + accessCtx.getUserId();
        String queryKey = module + ":" + contextStr + ":" + sqlHash;
        
        int callCount = queryCallCounts.computeIfAbsent(queryKey, k -> new AtomicInteger(0)).incrementAndGet();
        long lastCallTime = queryLastCallTime.getOrDefault(queryKey, 0L);
        long timeSinceLastCall = startTime - lastCallTime;
        queryLastCallTime.put(queryKey, startTime);
        if (callCount > 1 && timeSinceLastCall < 1000) {
            // Log possible duplicate query when same query runs again within 1s
            System.out.println("[SearchService] Possible duplicate query: " + queryKey + " callCount=" + callCount + " msSinceLast=" + timeSinceLastCall);
        }
        
        if (queryHashCache.size() < MAX_CACHE_SIZE) {
            queryHashCache.put(queryKey, qr.sql.substring(0, Math.min(100, qr.sql.length())));
        }
        
        List<Map<String, Object>> results = databaseHelper.executeQuery(qr.sql, qr.parameters);
        enrichRowsWithActualSegment(results, module);
        
        return results;
    }

    /**
     * Execute a search using an BUDG-style searchGroups definition with segment
     * filtering.
     * Used by UnisonSearchService to honor filterGroups and field-level FIND.
     */
    public List<Map<String, Object>> searchWithDefinition(String module, JsonObject searchGroupsJson, Integer userId)
            throws SQLException {
        long startTime = System.currentTimeMillis();
        QueryResult qr = queryBuilder.buildFromSearchGroups(module, searchGroupsJson, userId);
        if (qr.sql == null || qr.sql.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        // Create a hash of the SQL query for more accurate duplicate detection
        // This helps detect when the exact same query is executed multiple times
        String sqlHash = Integer.toHexString(qr.sql.hashCode());
        String queryKey = module + ":" + (userId != null ? userId : "null") + ":" + sqlHash;
        
        // Track query calls for duplicate detection
        int callCount = queryCallCounts.computeIfAbsent(queryKey, k -> new AtomicInteger(0)).incrementAndGet();
        long lastCallTime = queryLastCallTime.getOrDefault(queryKey, 0L);
        long timeSinceLastCall = startTime - lastCallTime;
        queryLastCallTime.put(queryKey, startTime);
        if (callCount > 1 && timeSinceLastCall < 1000) {
            System.out.println("[SearchService] Possible duplicate searchWithDefinition: " + queryKey + " callCount=" + callCount + " msSinceLast=" + timeSinceLastCall);
        }
        
        // Store query hash for reference (with size limit to prevent memory issues)
        if (queryHashCache.size() < MAX_CACHE_SIZE) {
            queryHashCache.put(queryKey, qr.sql.substring(0, Math.min(100, qr.sql.length())));
        }
        
        List<Map<String, Object>> results = databaseHelper.executeQuery(qr.sql, qr.parameters);
        enrichRowsWithActualSegment(results, module);
        
        return results;
    }

    private void enrichRowsWithActualSegment(List<Map<String, Object>> rows, String module) {
        if (rows == null || rows.isEmpty() || module == null || module.isBlank()) {
            return;
        }

        String objectType = moduleToObjectType(module);
        if (objectType == null) {
            return;
        }

        SegmentDAO segmentDAO = new SegmentDAO();
        Map<Integer, String> segmentNameCache = new HashMap<>();

        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }

            Object idObj = row.get("ID");
            if (!(idObj instanceof Number)) {
                idObj = row.get("id");
            }
            if (!(idObj instanceof Number)) {
                continue;
            }

            int objectId = ((Number) idObj).intValue();
            if (objectId <= 0) {
                continue;
            }

            try {
                int segmentId;
                if ("Interface".equalsIgnoreCase(objectType) || "SystemInterface".equalsIgnoreCase(objectType)) {
                    segmentId = resolveInterfaceSegmentId(segmentDAO, row, objectId);
                } else {
                    segmentId = segmentDAO.getObjectSegmentId(objectId, objectType);
                }
                int effectiveSegmentId = segmentId > 0 ? segmentId : 1;
                row.put("Segment_ID", effectiveSegmentId);
                row.put("segment_id", effectiveSegmentId);

                String segmentName = segmentNameCache.get(effectiveSegmentId);
                if (segmentName == null) {
                    Map<String, Object> segment = segmentDAO.getSegmentById(effectiveSegmentId);
                    Object nameObj = segment != null ? (segment.get("name") != null ? segment.get("name") : segment.get("Name")) : null;
                    segmentName = (nameObj != null && !nameObj.toString().trim().isEmpty())
                            ? nameObj.toString().trim()
                            : ("Segment " + effectiveSegmentId);
                    segmentNameCache.put(effectiveSegmentId, segmentName);
                }

                row.put("Segment", segmentName);
                row.put("segment", segmentName);
                row.put("Segments", segmentName);
                row.put("segments", segmentName);
            } catch (Exception ignored) {
                // Keep search results available even when segment lookup fails for one row.
            }
        }
    }

    private String moduleToObjectType(String module) {
        String normalized = module.trim().toLowerCase().replace("-", "_").replace(" ", "_");
        return switch (normalized) {
            case "dataset", "datasets" -> "Dataset";
            case "attribute", "attributes", "dataquality", "data_quality" -> "Dataset";
            case "system", "systems" -> "System";
            case "glossary", "glossaries" -> "Glossary";
            case "process", "processes" -> "Process";
            case "project", "projects" -> "Project";
            case "product", "products" -> "Product";
            case "policy", "policies" -> "Policy";
            case "legal_entity", "legalentity", "legal_entities", "legal" -> "LegalEntity";
            case "business_area", "businessarea", "business_areas", "businessareas" -> "BusinessArea";
            case "capability", "capabilities" -> "Capability";
            case "client", "clients" -> "Client";
            case "committee", "committees" -> "Committee";
            case "geography", "geographies" -> "Geography";
            case "regulation", "regulations" -> "Regulation";
            case "regulator", "regulators" -> "Regulator";
            case "regulatory_theme", "regulatorytheme", "regulatory_themes", "regulatorythemes" -> "RegulatoryTheme";
            case "interface", "interfaces", "system_interface", "systeminterface" -> "Interface";
            case "change_request", "changerequest", "change_requests", "changerequests" -> "ChangeRequest";
            default -> null;
        };
    }

    private int resolveInterfaceSegmentId(SegmentDAO segmentDAO, Map<String, Object> row, int interfaceId)
            throws SQLException {
        int segmentId = segmentDAO.getObjectSegmentId(interfaceId, "Interface");
        if (segmentId > 0) {
            return segmentId;
        }

        segmentId = segmentDAO.getObjectSegmentId(interfaceId, "SystemInterface");
        if (segmentId > 0) {
            return segmentId;
        }

        Integer targetSystemId = coerceInteger(
                row.get("Target System Short Name_ID"),
                row.get("targetSystemId"),
                row.get("target_system_id"),
                row.get("Target_systemID"),
                row.get("target_systemID"),
                row.get("toId"));
        if (targetSystemId != null && targetSystemId > 0) {
            return segmentDAO.getObjectSegmentId(targetSystemId, "System");
        }
        return -1;
    }

    private Integer coerceInteger(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value == null) continue;
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * Enrich search result rows with custom field values for the given module.
     * Fetches CF metadata and values from the database, then adds them as
     * additional columns in each result row.
     */
    public void enrichWithCustomFields(List<Map<String, Object>> results, String module) {
        if (results == null || results.isEmpty() || module == null || module.isBlank()) return;

        try {
            // Normalize module name for matching against module table primaryname
            String normalized = module.trim().toLowerCase()
                    .replace("-", "").replace("_", "").replace(" ", "");

            // Find the module ID by matching primaryname
            String findModuleSql = "SELECT id, primaryname FROM module WHERE primaryname IS NOT NULL AND primaryname != ''";
            List<Map<String, Object>> modules = databaseHelper.executeQuery(findModuleSql, List.of());

            Integer moduleId = null;
            for (Map<String, Object> mod : modules) {
                Object pnObj = mod.get("primaryname");
                if (pnObj == null) continue;
                String pn = pnObj.toString().trim().toLowerCase()
                        .replace("-", "").replace("_", "").replace(" ", "");
                if (pn.equals(normalized) || pn.startsWith(normalized) || normalized.startsWith(pn)) {
                    Object idObj = mod.get("id");
                    if (idObj instanceof Number) {
                        moduleId = ((Number) idObj).intValue();
                    }
                    break;
                }
            }

            if (moduleId == null) {
                return;
            }

            // Get all CF metadata for this module (only visible ones with is_Searchable)
            String metaSql = "SELECT id, DisplayName, DataType FROM Custom_Field_Metadata WHERE Module_ID = ? ORDER BY id";
            List<Map<String, Object>> cfMeta = databaseHelper.executeQuery(metaSql, List.of((Object) moduleId));

            if (cfMeta.isEmpty()) return;

            List<String> allCFNames = new ArrayList<>();
            Set<String> percentageCFs = new HashSet<>();
            for (Map<String, Object> m : cfMeta) {
                Object dn = m.get("DisplayName");
                if (dn != null && !dn.toString().trim().isEmpty()) {
                    String name = dn.toString().trim();
                    allCFNames.add(name);
                    Object dt = m.get("DataType");
                    if (dt != null && "percentage".equalsIgnoreCase(dt.toString().trim())) {
                        percentageCFs.add(name);
                    }
                }
            }
            if (allCFNames.isEmpty()) return;

            // Extract all object IDs from results
            List<Object> objectIds = new ArrayList<>();
            for (Map<String, Object> row : results) {
                Object id = row.get("ID");
                if (id instanceof Number) {
                    objectIds.add(((Number) id).intValue());
                }
            }
            if (objectIds.isEmpty()) return;

            // Build IN clause for object IDs
            StringBuilder placeholders = new StringBuilder();
            List<Object> params = new ArrayList<>();
            params.add(moduleId);
            for (int i = 0; i < objectIds.size(); i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
                params.add(objectIds.get(i));
            }

            String cfSql = "SELECT cfd.Facet_Object_ID, cfm.DisplayName, cfd.Custom_Field_Value, " +
                    "cfm.DataType, cfe.EnumValue " +
                    "FROM Custom_Field_Data cfd " +
                    "INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID " +
                    "LEFT JOIN Custom_Field_Enum cfe ON cfe.ID = cfd.Custom_Field_Enum_ID " +
                    "WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID IN (" + placeholders + ") " +
                    "ORDER BY cfd.Facet_Object_ID, cfm.ID";

            List<Map<String, Object>> cfData = databaseHelper.executeQuery(cfSql, params);

            // Group CF data by object ID
            Map<Integer, Map<String, String>> cfByObject = new LinkedHashMap<>();
            for (Map<String, Object> cf : cfData) {
                Object objIdRaw = cf.get("Facet_Object_ID");
                if (!(objIdRaw instanceof Number)) continue;
                int objectId = ((Number) objIdRaw).intValue();

                String displayName = cf.get("DisplayName") != null ? cf.get("DisplayName").toString().trim() : null;
                if (displayName == null || displayName.isEmpty()) continue;

                String value = cf.get("Custom_Field_Value") != null ? cf.get("Custom_Field_Value").toString() : null;
                String enumValue = cf.get("EnumValue") != null ? cf.get("EnumValue").toString() : null;
                String dataType = cf.get("DataType") != null ? cf.get("DataType").toString() : null;

                String displayValue = enumValue != null && !enumValue.isEmpty() ? enumValue
                        : (value != null ? value : "");

                Map<String, String> objectCFs = cfByObject.computeIfAbsent(objectId, k -> new LinkedHashMap<>());

                if ("multiselect".equalsIgnoreCase(dataType)) {
                    String existing = objectCFs.get(displayName);
                    if (existing != null && !existing.isEmpty()) {
                        objectCFs.put(displayName, existing + ", " + displayValue);
                    } else {
                        objectCFs.put(displayName, displayValue);
                    }
                } else {
                    objectCFs.put(displayName, displayValue);
                }
            }

            // Merge CF data into each result row
            for (Map<String, Object> row : results) {
                Object id = row.get("ID");
                Integer objectId = id instanceof Number ? ((Number) id).intValue() : null;
                Map<String, String> objectCFs = objectId != null ? cfByObject.get(objectId) : null;

                for (String cfName : allCFNames) {
                    String val = objectCFs != null ? objectCFs.get(cfName) : null;
                    if (val != null && !val.isEmpty() && percentageCFs.contains(cfName)) {
                        val = val + "%";
                    }
                    row.put(cfName, val != null ? val : "");
                }
            }

        } catch (Exception e) {
            System.err.println("[SearchService] Error enriching with custom fields: " + e.getMessage());
        }
    }

    private void logQuery(String context, QueryResult qr) {
        try {
            // Check for GROUP_CONCAT without proper GROUP BY
            String sqlUpper = qr.sql.toUpperCase();
            boolean hasGroupConcat = sqlUpper.contains("GROUP_CONCAT");
            boolean hasGroupBy = sqlUpper.contains("GROUP BY");
            
            if (hasGroupConcat && !hasGroupBy) {
                System.err.println("[UnisonSQL] ⚠️ WARNING: Query contains GROUP_CONCAT but no GROUP BY clause!");
            }
        } catch (Exception e) {
            // Silently ignore logging errors
        }
    }
    
    /**
     * Reset query call tracking (useful for testing or periodic cleanup)
     */
    public static void resetQueryTracking() {
        queryCallCounts.clear();
        queryLastCallTime.clear();
        queryHashCache.clear();
    }
    
    /**
     * Clean up old query tracking entries to prevent memory issues.
     * Removes entries older than the specified age in milliseconds.
     * 
     * @param maxAgeMs Maximum age of entries to keep (in milliseconds)
     */
    public static void cleanupOldQueryTracking(long maxAgeMs) {
        long currentTime = System.currentTimeMillis();
        queryLastCallTime.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > maxAgeMs);
        // Also remove corresponding call counts and cache entries
        queryCallCounts.keySet().retainAll(queryLastCallTime.keySet());
        queryHashCache.keySet().retainAll(queryLastCallTime.keySet());
    }
}
