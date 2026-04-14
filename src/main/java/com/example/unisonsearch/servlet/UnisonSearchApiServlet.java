package com.example.unisonsearch.servlet;

import com.example.budg_v2.util.CorsUtil;
import com.example.unisonsearch.model.UnisonSearchRequest;
import com.example.unisonsearch.model.UnisonSearchResponse;
import com.example.unisonsearch.model.SearchRequest;
import com.example.unisonsearch.model.SearchResponse;
import com.example.unisonsearch.repository.DatabaseHelper;
import com.example.unisonsearch.repository.QueryBuilder;
import com.example.unisonsearch.repository.TaskRepository;
import com.example.unisonsearch.service.*;
import com.example.unisonsearch.model.FacetResult;
import com.example.unisonsearch.util.UnisonTrace;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * API endpoint for Unison Search.
 * Handles compound searches across multiple facets with graph traversal.
 * 
 * Endpoint: POST /api/unison/search
 */
@WebServlet(name = "UnisonSearchApiServlet", urlPatterns = { "/api/unison/search" })
public class UnisonSearchApiServlet extends HttpServlet {

    private final Gson gson = createGson();

    /**
     * Create Gson instance with custom deserializer for filters field.
     * Handles both array and object formats for filters.
     */
    private Gson createGson() {
        GsonBuilder builder = new GsonBuilder();

        // Register Java 8 time type adapters to avoid reflection issues with Java 9+ modules
        builder.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
               .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
               .registerTypeAdapter(LocalTime.class, new LocalTimeAdapter());

        // Custom deserializer for SearchItem.filters field
        builder.registerTypeAdapter(UnisonSearchRequest.SearchItem.class,
                new JsonDeserializer<UnisonSearchRequest.SearchItem>() {
                    @Override
                    public UnisonSearchRequest.SearchItem deserialize(JsonElement json, Type typeOfT,
                            JsonDeserializationContext context) throws JsonParseException {
                        JsonObject jsonObject = json.getAsJsonObject();
                        UnisonSearchRequest.SearchItem item = new UnisonSearchRequest.SearchItem();

                        if (jsonObject.has("operator")) {
                            item.setOperator(jsonObject.get("operator").getAsString());
                        }
                        if (jsonObject.has("facet")) {
                            item.setFacet(jsonObject.get("facet").getAsString());
                        }
                        if (jsonObject.has("keyword")) {
                            item.setKeyword(jsonObject.get("keyword").getAsString());
                        }

                        // Handle filters - can be array, object, or null
                        if (jsonObject.has("filters")) {
                            JsonElement filtersElement = jsonObject.get("filters");
                            Map<String, Object> filters = new HashMap<>();

                            if (filtersElement.isJsonArray()) {
                                // If filters is an array (like ["Name", "Ref"]), convert to empty map
                                // The fields are handled separately in the keyword search
                                filters = new HashMap<>();
                            } else if (filtersElement.isJsonObject()) {
                                // If filters is an object, parse it normally
                                filters = context.deserialize(filtersElement, new TypeToken<Map<String, Object>>() {
                                }.getType());
                            } else if (filtersElement.isJsonNull()) {
                                filters = new HashMap<>();
                            }

                            item.setFilters(filters);
                        } else {
                            item.setFilters(new HashMap<>());
                        }

                        // Handle searchFields - { "name": true, "ref": false, ... }
                        if (jsonObject.has("searchFields") && jsonObject.get("searchFields").isJsonObject()) {
                            Map<String, Boolean> searchFields = context.deserialize(
                                jsonObject.get("searchFields"),
                                new TypeToken<Map<String, Boolean>>() {}.getType());
                            item.setSearchFields(searchFields);
                        }

                        if (jsonObject.has("hierarchicalOptions")
                                && jsonObject.get("hierarchicalOptions").isJsonObject()) {
                            Map<String, String> hierarchicalOptions = context.deserialize(
                                    jsonObject.get("hierarchicalOptions"),
                                    new TypeToken<Map<String, String>>() {
                                    }.getType());
                            item.setHierarchicalOptions(hierarchicalOptions);
                        }
                        if (jsonObject.has("indentLevel") && !jsonObject.get("indentLevel").isJsonNull()) {
                            item.setIndentLevel(jsonObject.get("indentLevel").getAsInt());
                        }
                        if (jsonObject.has("displayFilter") && !jsonObject.get("displayFilter").isJsonNull()) {
                            item.setDisplayFilter(jsonObject.get("displayFilter").getAsBoolean());
                        }

                        return item;
                    }
                });

        return builder.create();
    }

    private final ConfigurationService configurationService;
    private final RelationshipManager relationshipManager;
    private final QueryBuilder queryBuilder;
    private final DatabaseHelper databaseHelper;
    private final SearchService searchService;
    private final GraphTraversalService graphTraversalService;
    private final CompoundQueryService compoundQueryService;
    private final UnisonSearchService unisonSearchService;
    private final UnisonService unisonService;
    private final UnisonFacetService unisonFacetService;
    
    // New Unified Search Service
    private UnifiedSearchService unifiedSearchService;

    public UnisonSearchApiServlet() {

        try {
            this.configurationService = new ConfigurationService();
            this.relationshipManager = new RelationshipManager();
            this.databaseHelper = new DatabaseHelper();
            // Initialize RelationshipService for new relationship logic
            RelationshipService relationshipService = new RelationshipServiceImpl(databaseHelper);
            this.queryBuilder = new QueryBuilder(configurationService, relationshipManager, relationshipService);
            this.searchService = new SearchService(queryBuilder, databaseHelper);
            this.graphTraversalService = new GraphTraversalService(relationshipManager, databaseHelper);
            this.compoundQueryService = new CompoundQueryService();
            this.unisonSearchService = new UnisonSearchService(
                    searchService, graphTraversalService, compoundQueryService);
            this.unisonService = new UnisonService(configurationService);
            this.unisonFacetService = new UnisonFacetService(unisonService, configurationService);
            
            // Initialize new UnifiedSearchService (reuse relationshipService)
            DedupManager dedupManager = new DedupManager();
            TaskRepository taskRepository = new TaskRepository();
            ActiveTasksService activeTasksService = new ActiveTasksService(taskRepository);
            this.unifiedSearchService = new UnifiedSearchService(
                relationshipService, dedupManager, relationshipManager, activeTasksService);

        } catch (Exception e) {
            System.err.println("[UnisonSearchApiServlet] Error during initialization: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize UnisonSearchApiServlet", e);
        }
    }

    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // GET requests should return method not allowed
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        response.getWriter().write("{\"error\":\"Method not allowed. Use POST instead.\"}");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Debug logging

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        Integer userId = getUserId(request);

        // Put userId into MDC so CorrelationIdFilter can log it in slow-request warnings
        if (userId != null && userId > 0) {
            org.slf4j.MDC.put("user_id", String.valueOf(userId));
        }

        final boolean traceFromHeader = "1".equalsIgnoreCase(request.getHeader("X-Unison-Trace"));
        if (traceFromHeader) {
            UnisonTrace.setRequestForceTrace(true);
        }

        try {
            final long apiStartMs = System.currentTimeMillis();
            // Parse request body
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                requestBody.append(line);
            }

            String jsonBody = requestBody.toString();

            UnisonSearchRequest searchRequest = gson.fromJson(jsonBody, UnisonSearchRequest.class);

            if (searchRequest == null || searchRequest.getSearches() == null || searchRequest.getSearches().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                UnisonSearchResponse errorResponse = UnisonSearchResponse
                        .error("Invalid request: searches array is required");
                response.getWriter().write(gson.toJson(errorResponse));
                return;
            }

            if (UnisonTrace.enabled()) {
                UnisonTrace.log("API", "request.summary",
                        "userId=" + userId + " bodyChars=" + jsonBody.length() + " searches=" + searchRequest.getSearches().size());
                for (int si = 0; si < searchRequest.getSearches().size(); si++) {
                    UnisonSearchRequest.SearchItem it = searchRequest.getSearches().get(si);
                    if (it == null) {
                        UnisonTrace.log("API", "request.clause." + si, "(null item)");
                        continue;
                    }
                    String fk = it.getFilters() == null || it.getFilters().isEmpty() ? "{}"
                            : it.getFilters().keySet().toString();
                    UnisonTrace.log("API", "request.clause." + si,
                            "op=" + it.getOperator() + " facet=" + it.getFacet()
                                    + " keywordLen=" + (it.getKeyword() != null ? it.getKeyword().length() : -1)
                                    + " filterKeys=" + fk
                                    + " searchFields=" + (it.getSearchFields() != null ? it.getSearchFields().size() : 0));
                }
            }

            // Get max depth from options (default 1 — same as UnisonSearchRequest.SearchOptions)
            int maxDepth = 1; // direct relations only unless client raises maxDepth
            if (searchRequest.getOptions() != null) {
                maxDepth = searchRequest.getOptions().getMaxDepth();

                // Allow maxDepth = 0 for exact matches only (no graph traversal)
                if (maxDepth < 0) {
                    maxDepth = 1;
                }
            }

            // Check if using new unified search (if request has objectId parameter)
            // Note: UnifiedSearchService currently supports limited facets (dataset, system, glossary, attribute, interface, project, process, policy, capability)
            // For other facets, use UnisonSearchService which supports all facets
            boolean useUnifiedSearch = false;
            JsonObject requestJson = gson.fromJson(jsonBody, JsonObject.class);
            if (requestJson.has("objectId") && requestJson.get("objectId").getAsInt() > 0) {
                // Check if facet is supported by UnifiedSearchService
                String facet = null;
                if (requestJson.has("facet")) {
                    facet = requestJson.get("facet").getAsString();
                } else if (searchRequest.getSearches() != null && !searchRequest.getSearches().isEmpty()) {
                    facet = searchRequest.getSearches().get(0).getFacet();
                }
                
                // UnifiedSearchService supports: dataset, system, glossary, attribute, interface, project, process, policy, capability
                if (facet != null) {
                    String normalizedFacet = facet.toLowerCase().trim();
                    useUnifiedSearch = normalizedFacet.equals("dataset") || normalizedFacet.equals("system") ||
                            normalizedFacet.equals("glossary") || normalizedFacet.equals("attribute") ||
                            normalizedFacet.equals("interface") || normalizedFacet.equals("project") ||
                            normalizedFacet.equals("process") || normalizedFacet.equals("policy") ||
                            normalizedFacet.equals("capability");
                }
            }
            
            UnisonSearchResponse searchResponse;
            if (useUnifiedSearch) {
                // Use new UnifiedSearchService for supported facets
                SearchRequest unifiedRequest = new SearchRequest();
                if (requestJson.has("facet")) {
                    unifiedRequest.setFacet(requestJson.get("facet").getAsString());
                } else if (searchRequest.getSearches() != null && !searchRequest.getSearches().isEmpty()) {
                    unifiedRequest.setFacet(searchRequest.getSearches().get(0).getFacet());
                }
                if (requestJson.has("objectId")) {
                    unifiedRequest.setObjectId(requestJson.get("objectId").getAsInt());
                }
                unifiedRequest.setMaxDepth(maxDepth);
                unifiedRequest.setUserId(userId);
                
                SearchResponse unifiedResponse = unifiedSearchService.executeSearch(unifiedRequest);
                // Convert to UnisonSearchResponse format
                searchResponse = convertToUnisonResponse(unifiedResponse);
            } else {
                // Use UnisonSearchService for all facets (supports all facets including people, change request, org unit, geography, etc.)
                searchResponse = unisonSearchService.executeUnisonSearch(
                        searchRequest.getSearches(), maxDepth, userId);
            }

            // Apply facet visibility/fields per user if available
            if (userId != null && searchResponse != null && searchResponse.getResults() != null) {
                try {
                    List<Map<String, Object>> facetConfigs = unisonFacetService.getFacetsForUser(userId);
                    Map<String, FacetResult> adjusted = applyFacetPreferences(searchResponse.getResults(),
                            facetConfigs);
                    searchResponse.setResults(adjusted);
                } catch (Exception e) {
                    System.err.println("[UnisonSearchApiServlet] Failed to apply facet preferences: " + e.getMessage());
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(searchResponse));

            if (UnisonTrace.enabled()) {
                Map<String, FacetResult> res = searchResponse != null ? searchResponse.getResults() : null;
                StringBuilder sb = new StringBuilder();
                sb.append("ms=").append(System.currentTimeMillis() - apiStartMs);
                if (res != null) {
                    sb.append(" facets=").append(res.size());
                    res.forEach((k, v) -> {
                        int n = (v != null && v.getIds() != null) ? v.getIds().size() : 0;
                        sb.append(" ").append(k).append("=").append(n);
                    });
                }
                UnisonTrace.log("API", "response.summary", sb.toString());
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            System.err.println("UnisonSearchApiServlet: Database error: " + e.getMessage());
            e.printStackTrace();
            UnisonSearchResponse errorResponse = UnisonSearchResponse.error("Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(errorResponse));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            System.err.println("UnisonSearchApiServlet: Unexpected error: " + e.getMessage());
            e.printStackTrace();
            UnisonSearchResponse errorResponse = UnisonSearchResponse.error("Unexpected error: " + e.getMessage());
            response.getWriter().write(gson.toJson(errorResponse));
        } finally {
            if (traceFromHeader) {
                UnisonTrace.clearRequestTrace();
            }
            org.slf4j.MDC.remove("user_id");
        }
    }

    private Integer getUserId(HttpServletRequest request) {
        // Try to get from request attributes (set by AuthFilter)
        Object attr = request.getAttribute("userId");
        if (attr instanceof Number) {
            Integer userId = ((Number) attr).intValue();
            if (userId != null && userId > 0) {
                return userId;
            }
        }
        
        // Try session
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sid = session.getAttribute("userId");
            if (sid instanceof Number) {
                Integer userId = ((Number) sid).intValue();
                if (userId != null && userId > 0) {
                    return userId;
                }
            }
        }
        
        // Fallback: parse ACCESS_TOKEN cookie (similar to UnisonSearchServlet)
        try {
            jakarta.servlet.http.Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie cookie : cookies) {
                    if ("ACCESS_TOKEN".equals(cookie.getName())) {
                        String token = cookie.getValue();
                        if (token != null) {
                            Integer userId = com.example.budg_v2.util.JwtUtil.getUserIdFromToken(token);
                            if (userId != null && userId > 0) {
                                return userId;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired - silently continue
            System.err.println("[UnisonSearchApiServlet] Failed to parse token from cookie: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Apply unison_facets visibility/order/activeFields to results and keep counts
     * in sync.
     */
    private Map<String, FacetResult> applyFacetPreferences(Map<String, FacetResult> results,
            List<Map<String, Object>> facetConfigs) {
        if (results == null || facetConfigs == null)
            return results;

        Map<String, Map<String, Object>> configById = new HashMap<>();
        for (Map<String, Object> cfg : facetConfigs) {
            Object fid = cfg.get("facetId");
            if (fid != null) {
                configById.put(fid.toString(), cfg);
            }
        }

        List<String> orderedFacetIds = facetConfigs.stream()
                .sorted(Comparator.comparingInt(cfg -> ((Number) cfg.getOrDefault("ordering", 0)).intValue()))
                .map(cfg -> cfg.get("facetId").toString())
                .collect(Collectors.toList());

        Map<String, FacetResult> ordered = new LinkedHashMap<>();
        for (String facetId : orderedFacetIds) {
            Map<String, Object> cfg = configById.get(facetId);
            boolean active = cfg == null || Boolean.TRUE.equals(cfg.get("active"))
                    || (cfg.get("active") instanceof Number && ((Number) cfg.get("active")).intValue() == 1);
            if (!active) {
                continue;
            }
            FacetResult fr = results.get(facetId);
            if (fr == null)
                continue;
            List<String> activeFields = parseActiveFields(cfg != null ? (String) cfg.get("activeFields") : null);
            List<Map<String, Object>> projectedRows = projectRows(fr.getRows(), activeFields);
            // Preserve totalCount (segment-filtered) so frontend "X of Y" is correct
            FacetResult projected = new FacetResult(fr.getIds(), fr.isHasActiveFilter(), fr.getDepthById(),
                    projectedRows, fr.getTotalCount());
            ordered.put(facetId, projected);
        }

        // Add any remaining facets not in config in original order
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            if (!ordered.containsKey(entry.getKey())) {
                ordered.put(entry.getKey(), entry.getValue());
            }
        }
        return ordered;
    }

    private List<String> parseActiveFields(String activeFields) {
        if (activeFields == null || activeFields.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = activeFields.split(",");
        List<String> fields = new ArrayList<>();
        for (String p : parts) {
            if (!p.trim().isEmpty()) {
                fields.add(p.trim());
            }
        }
        return fields;
    }

    private List<Map<String, Object>> projectRows(List<Map<String, Object>> rows, List<String> activeFields) {
        if (rows == null || rows.isEmpty() || activeFields == null || activeFields.isEmpty()) {
            return rows != null ? rows : new ArrayList<>();
        }
        List<Map<String, Object>> projected = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> pr = new LinkedHashMap<>();
            if (row.containsKey("ID")) {
                pr.put("ID", row.get("ID"));
            }
            for (String field : activeFields) {
                if (row.containsKey(field)) {
                    pr.put(field, row.get(field));
                }
            }
            // Preserve custom field columns (dynamic columns not in activeFields).
            // These are added by backend CF enrichment and should always pass through.
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if (!pr.containsKey(key) && !key.endsWith("_ID") && !key.endsWith("_id")) {
                    pr.put(key, entry.getValue());
                }
            }
            projected.add(pr);
        }
        return projected;
    }
    
    /**
     * Convert SearchResponse to UnisonSearchResponse for backward compatibility.
     */
    private UnisonSearchResponse convertToUnisonResponse(SearchResponse unifiedResponse) {
        UnisonSearchResponse response = new UnisonSearchResponse();
        response.setSuccess(unifiedResponse.isSuccess());
        
        if (!unifiedResponse.isSuccess()) {
            response.setError(unifiedResponse.getErrorMessage());
            return response;
        }
        
        // Convert results
        Map<String, FacetResult> facetResults = new HashMap<>();
        Map<String, java.util.Set<Integer>> facetIds = new HashMap<>();
        Map<String, java.util.Map<Integer, Integer>> facetDepths = new HashMap<>();
        
        for (com.example.unisonsearch.model.SearchResult result : unifiedResponse.getResults()) {
            String facetId = result.entityFacet();
            facetIds.computeIfAbsent(facetId, k -> new java.util.HashSet<>()).add(result.id());
            facetDepths.computeIfAbsent(facetId, k -> new java.util.HashMap<>()).put(result.id(), result.getDepth());
        }
        
        // Create FacetResult objects
        for (Map.Entry<String, java.util.Set<Integer>> entry : facetIds.entrySet()) {
            String facetId = entry.getKey();
            java.util.Set<Integer> ids = entry.getValue();
            java.util.Map<Integer, Integer> depths = facetDepths.getOrDefault(facetId, new java.util.HashMap<>());
            FacetResult facetResult = new FacetResult(ids, false, depths);
            facetResults.put(facetId, facetResult);
        }
        
        response.setResults(facetResults);
        
        // Add active tasks info if needed
        if (!unifiedResponse.getActiveTasks().isEmpty()) {
            // Store in relatedObjects or custom field
            Map<String, Map<String, java.util.Set<Integer>>> relatedObjects = new HashMap<>();
            Map<String, java.util.Set<Integer>> activeTasksMap = new HashMap<>();
            java.util.Set<Integer> taskIds = new java.util.HashSet<>();
            for (com.example.unisonsearch.model.ActiveTask task : unifiedResponse.getActiveTasks()) {
                taskIds.add(task.getId());
            }
            activeTasksMap.put("ACTIVE_TASK", taskIds);
            relatedObjects.put("ACTIVE_TASK", activeTasksMap);
            response.setRelatedObjects(relatedObjects);
        }
        
        return response;
    }
    
    // ============================
    // Gson TypeAdapters for Java 8 Time types
    // ============================

    /**
     * TypeAdapter for LocalDateTime to avoid Java 9+ module reflection issues.
     * Format: "yyyy-MM-dd HH:mm:ss"
     */
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String dateTimeString = in.nextString();
            return LocalDateTime.parse(dateTimeString, formatter);
        }
    }

    /**
     * TypeAdapter for LocalDate to avoid Java 9+ module reflection issues.
     * Format: "yyyy-MM-dd"
     */
    private static class LocalDateAdapter extends TypeAdapter<LocalDate> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String dateString = in.nextString();
            return LocalDate.parse(dateString, formatter);
        }
    }

    /**
     * TypeAdapter for LocalTime to avoid Java 9+ module reflection issues.
     * Format: "HH:mm:ss"
     */
    private static class LocalTimeAdapter extends TypeAdapter<LocalTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        @Override
        public void write(JsonWriter out, LocalTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String timeString = in.nextString();
            return LocalTime.parse(timeString, formatter);
        }
    }
}
