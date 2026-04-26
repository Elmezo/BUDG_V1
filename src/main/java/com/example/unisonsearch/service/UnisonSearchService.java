package com.example.unisonsearch.service;

import com.example.unisonsearch.model.*;
import com.example.unisonsearch.config.FilterMetadataConfig;
import com.example.unisonsearch.util.UnisonTrace;
import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentAccessService;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main orchestrator service for Unison Search.
 * Coordinates text search, graph traversal, and compound query operations.
 */
public class UnisonSearchService {

    private final SearchService searchService;
    private final GraphTraversalService graphTraversalService;
    private final CompoundQueryService compoundQueryService;
    private final KeywordSearchService keywordSearchService;
    private final Gson gson = new Gson();
    
    // Request counter for correlation IDs
    private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger(0);

    // Current user ID for segment filtering (set per request)
    private Integer currentUserId;
    
    // Segment access context for filtering (set per request)
    private SegmentAccessContext accessCtx;
    
    // DEPRECATED: FACET_CANON removed - use FacetNormalizationUtil instead
    // This ensures single source of truth for facet normalization across frontend and backend
    
    /**
     * All facets that should receive general enrichment (stakeholders, roles, people, org units, CRs, tasks).
     * Updated to include all facets as per requirements: "For all of the facets, these are general things to display:
     * impact, roles, people having these roles, org unit of its stakeholders (avoid duplications), CRs raised upon it, its active tasks"
     * Note: People has special enrichment rules in enrichPeopleFacet(), but still receives general enrichment.
     * Uses FacetNormalizationUtil to normalize facet names for consistency.
     */
    private static final Set<String> GOVERNED_FACETS = Set.of(
            "DATASET", "SYSTEM", "ATTRIBUTE", "GLOSSARY",
            "PROCESS", "PROJECT", "POLICY", "CAPABILITY",
            "REGULATION", "REGULATOR", "REGULATORY_THEME", "REGULATORYTHEME",
            "LEGAL_ENTITY", "LEGALENTITY", "CLIENT",
            "GEOGRAPHY", "ORG_UNIT", "ORGUNIT", "INTERFACE",
            "PRODUCT", "BUSINESS_AREA", "BUSINESSAREA", "COMMITTEE",
            "PEOPLE", "PERSON", "CHANGE_REQUEST", "CHANGEREQUEST"
    );
    
    /**
     * Facets that support hierarchical parent-child relationships.
     * These facets can include immediate or all children in search results.
     */
    private static final Set<String> HIERARCHICAL_FACETS = Set.of(
            "GLOSSARY", "PROCESS", "POLICY", "CAPABILITY"
    );
    
    /**
     * Normalize facet ID to canonical form.
     * Uses FacetNormalizationUtil for consistency across the codebase.
     * @param facet Facet ID (e.g., "ATTRIBUTES", "ATTRIBUTE")
     * @return Canonical facet ID (e.g., "ATTRIBUTE")
     */
    private String canonFacet(String facet) {
        if (facet == null) return facet;
        // Use FacetNormalizationUtil as single source of truth
        return com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(facet);
    }

    public UnisonSearchService(SearchService searchService,
            GraphTraversalService graphTraversalService,
            CompoundQueryService compoundQueryService) {
        this.searchService = searchService;
        this.graphTraversalService = graphTraversalService;
        this.compoundQueryService = compoundQueryService;
        this.keywordSearchService = new KeywordSearchService();
    }

    /**
     * Execute a compound Unison search with multiple search conditions.
     * 
     * @param searches List of search items with operators (FIND, AND, OR, NOT)
     * @param maxDepth Maximum graph traversal depth
     * @return UnisonSearchResponse with results per facet
     * @throws SQLException if database error occurs
     */
    public UnisonSearchResponse executeUnisonSearch(List<UnisonSearchRequest.SearchItem> searches, int maxDepth)
            throws SQLException {
        return executeUnisonSearch(searches, maxDepth, null);
    }

    /**
     * Execute a compound Unison search with multiple search conditions and segment
     * filtering.
     * 
     * @param searches List of search items with operators (FIND, AND, OR, NOT)
     * @param maxDepth Maximum graph traversal depth
     * @param userId   User ID for segment filtering (null for no filtering)
     * @return UnisonSearchResponse with results per facet
     * @throws SQLException if database error occurs
     */
    public UnisonSearchResponse executeUnisonSearch(List<UnisonSearchRequest.SearchItem> searches, int maxDepth,
            Integer userId) throws SQLException {
        return executeUnisonSearch(searches, maxDepth, userId, null);
    }

    /**
     * Same as {@link #executeUnisonSearch(List, int, Integer)} with optional cross-facet filter:
     * when {@code peopleConstraintIds} is non-empty, non-People facets are intersected with objects
     * linked to those people (stakeholder, created by, updated by).
     */
    public UnisonSearchResponse executeUnisonSearch(List<UnisonSearchRequest.SearchItem> searches, int maxDepth,
            Integer userId, Set<Integer> peopleConstraintIds) throws SQLException {
        long startTime = System.currentTimeMillis();

        if (searches == null || searches.isEmpty()) {
            return UnisonSearchResponse.error("No searches provided");
        }

        // Validate and normalize first search operator
        // First clause MUST be FIND to establish root universe
        if (!searches.isEmpty()) {
            UnisonSearchRequest.SearchItem firstSearch = searches.get(0);
            String firstOperator = firstSearch.getOperator() != null ? firstSearch.getOperator().toUpperCase() : "FIND";
            
            if ("NOT".equals(firstOperator)) {
                // NOT as first search is logically invalid - cannot exclude from nothing
                return UnisonSearchResponse.error("NOT operator cannot be used as first search");
            }
            
            if ("AND".equals(firstOperator) || "OR".equals(firstOperator)) {
                // AND/OR as first clause is unusual - normalize to FIND for root establishment
                firstSearch.setOperator("FIND");
            }
            
            // Ensure first clause is FIND (default)
            if (firstSearch.getOperator() == null || firstSearch.getOperator().isEmpty()) {
                firstSearch.setOperator("FIND");
            }
        }

        Map<String, FacetResult> accumulatedResults = null;
        /** First traversal result (maxDepth>=1); used for relatedObjects so they are not lost when a later FIND replaces accumulatedResults. */
        Map<String, FacetResult> firstTraversalForRelated = null;
        Map<String, JsonObject> facetFilters = new HashMap<>();

        // Store userId for use in search methods
        this.currentUserId = userId;
        
        // Generate correlation ID for this request
        String correlationId = "US-" + System.currentTimeMillis() + "-" + REQUEST_COUNTER.incrementAndGet();
        
        // Compute segment access context once per request
        try {
            boolean isSuperAdmin = userId != null && userId > 0 && SegmentAccessService.isSuperAdmin(userId);
            Set<Integer> effectiveSegments = null;
            if (userId != null && userId > 0) {
                // For ALL authenticated users (including Super Admin): use getEffectiveFilterSegmentIds.
                // For Super Admin this returns only their cube-selected segments (no access restriction).
                // For WebUsers/Admins this returns the intersection of accessible AND selected segments.
                // This ensures the cube filter is always respected regardless of role.
                effectiveSegments = SegmentAccessService.getEffectiveFilterSegmentIds(userId);
            }
            this.accessCtx = new SegmentAccessContext(userId, isSuperAdmin, effectiveSegments);
        } catch (Exception e) {
            System.err.println("[" + correlationId + "] Error building SegmentAccessContext: " + e.getMessage());
            e.printStackTrace();
            // Continue with null accessCtx (no segment filtering in traversal)
            this.accessCtx = null;
        }

        UnisonTrace.log(correlationId, "start",
                "nSearches=" + searches.size() + " maxDepth=" + maxDepth + " userId=" + userId
                        + " accessCtx=" + (accessCtx != null));
        
        // Root scope: computed once after the first FIND, reused for all subsequent clauses
        TraversalScope rootScope = null;
        String rootFacet = null;
        Set<Integer> rootSeedIds = null;
        String rootFacetId = null; // first FIND's facet, for AND/NOT empty cross-facet relation filter

        final int nSearches = searches.size();
        final int[] indentLevels = new int[nSearches];
        final int[] parentIndex = new int[nSearches];
        for (int ii = 0; ii < nSearches; ii++) {
            UnisonSearchRequest.SearchItem si = searches.get(ii);
            int il = 0;
            if (si != null && si.getIndentLevel() != null) {
                il = Math.max(0, si.getIndentLevel());
            }
            indentLevels[ii] = il;
        }
        for (int ii = 0; ii < nSearches; ii++) {
            int ind = indentLevels[ii];
            int p = -1;
            for (int j = ii - 1; j >= 0; j--) {
                if (indentLevels[j] < ind) {
                    p = j;
                    break;
                }
            }
            parentIndex[ii] = p;
        }

        // Process each search item in sequence
        // Operators are applied as follows:
        // - First search: FIND (normal), OR (equivalent to FIND), AND (valid but unusual), NOT (invalid - logged as warning)
        // - Subsequent searches: AND (intersect), OR (union), NOT (exclude), FIND (replace all - acts as reset)
        // - Indented rows (indentLevel > 0) are skipped here and evaluated as a group when their parent row runs.
        for (int i = 0; i < nSearches; i++) {
            UnisonSearchRequest.SearchItem search = searches.get(i);
            if (search == null || search.getFacet() == null) {
                continue;
            }
            if (parentIndex[i] >= 0) {
                continue;
            }

            JsonObject searchDefinition = null;
            if (search.getFilters() != null && !search.getFilters().isEmpty()) {
                searchDefinition = buildSearchDefinition(search.getFacet(), search.getKeyword(), search.getFilters(), search.getSearchFields());
                facetFilters.put(normalizeFacetName(search.getFacet()), searchDefinition);
            } else if (search.getSearchFields() != null && !search.getSearchFields().isEmpty()) {
                // No filters but user has restricted which fields to search in — still need a definition
                searchDefinition = buildSearchDefinition(search.getFacet(), search.getKeyword(), null, search.getSearchFields());
            }

            // Display-only filter: register for row-data filtering but skip the full intersection phase.
            // This allows e.g. filtering the People panel by Profile Name without shrinking the root SYSTEM count.
            if (search.isDisplayFilter()) {
                // facetFilters already populated above — that is all we need.
                continue;
            }

            // Variables for cross-facet AND with keyword (e.g. FIND DataSets AND "foo" in People)
            Set<Integer> crossFacetAndTargetIds = null;
            String crossFacetAndTargetFacet = null;

            // Check if this facet already exists in accumulatedResults
            // If so, we need to filter existing results instead of searching all objects
            String normalizedFacet = normalizeFacetName(search.getFacet());
            String facetId = normalizedFacetToFacetId(normalizedFacet);
            boolean facetExistsInResults = accumulatedResults != null &&
                    (accumulatedResults.containsKey(search.getFacet()) ||
                            accumulatedResults.containsKey(facetId));

            if (rootFacetId == null && accumulatedResults != null && !accumulatedResults.isEmpty()) {
                rootFacetId = accumulatedResults.keySet().iterator().next();
            }

            Set<Integer> existingIds = null;
            if (facetExistsInResults && accumulatedResults != null) {
                // Get existing IDs from accumulatedResults
                FacetResult existingResult = accumulatedResults.get(search.getFacet());
                if (existingResult == null) {
                    existingResult = accumulatedResults.get(facetId);
                }
                if (existingResult != null && existingResult.getIds() != null) {
                    existingIds = existingResult.getIds();
                }
            }

            // Execute single search to get seed objects. Empty keyword: FIND=select all, AND=has relation, OR=union all, NOT=no relation
            Set<Integer> seedIds;
            String operator = search.getOperator() != null ? search.getOperator().toUpperCase() : "FIND";
            boolean usedRelationFilterForRoot = false;

            // NOT/AND with empty keyword on a *different* facet (e.g. NOT People * after FIND Dataset *): filter root facet by relation
            // Apply even when current facet (People) is not in accumulatedResults - use root facet IDs
            if (accumulatedResults != null && !accumulatedResults.isEmpty() && rootFacetId != null
                    && isKeywordEmpty(search.getKeyword()) && ("NOT".equals(operator) || "AND".equals(operator))
                    && getRootToRelationConfig(rootFacetId, search.getFacet()) != null) {
                FacetResult rootResult = accumulatedResults.get(rootFacetId);
                if (rootResult == null) {
                    String rootCanon = com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(rootFacetId);
                    for (String key : accumulatedResults.keySet()) {
                        if (key == null) continue;
                        String keyCanon = com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(key);
                        if (rootCanon != null && rootCanon.equals(keyCanon)) {
                            rootResult = accumulatedResults.get(key);
                            break;
                        }
                    }
                    if (rootResult == null) {
                        for (String key : accumulatedResults.keySet()) {
                            if (key != null && rootFacetId.equalsIgnoreCase(key.replace("-", "_").replace(" ", "_"))) {
                                rootResult = accumulatedResults.get(key);
                                break;
                            }
                        }
                    }
                }
                if (rootResult != null && rootResult.getIds() != null && !rootResult.getIds().isEmpty()) {
                    Set<Integer> rootIds = rootResult.getIds();
                    if ("NOT".equals(operator)) {
                        seedIds = getObjectIdsWithNoRelationToFacet(rootIds, rootFacetId, search.getFacet());
                        usedRelationFilterForRoot = true;
                    } else {
                        seedIds = getObjectIdsWithRelationToFacet(rootIds, rootFacetId, search.getFacet());
                        usedRelationFilterForRoot = true;
                    }
                } else {
                    seedIds = new HashSet<>();
                }
            } else if (facetExistsInResults && existingIds != null && !existingIds.isEmpty()) {
                Set<Integer> rootIds = (rootFacetId != null && accumulatedResults != null && accumulatedResults.get(rootFacetId) != null && accumulatedResults.get(rootFacetId).getIds() != null)
                        ? accumulatedResults.get(rootFacetId).getIds() : existingIds;
                if (isKeywordEmpty(search.getKeyword())) {
                    if ("NOT".equals(operator) && getRootToRelationConfig(rootFacetId != null ? rootFacetId : search.getFacet(), search.getFacet()) != null) {
                        seedIds = getObjectIdsWithNoRelationToFacet(rootIds, rootFacetId != null ? rootFacetId : search.getFacet(), search.getFacet());
                        usedRelationFilterForRoot = true;
                    } else if ("AND".equals(operator) && getRootToRelationConfig(rootFacetId != null ? rootFacetId : search.getFacet(), search.getFacet()) != null) {
                        seedIds = getObjectIdsWithRelationToFacet(rootIds, rootFacetId != null ? rootFacetId : search.getFacet(), search.getFacet());
                        usedRelationFilterForRoot = true;
                    } else if ("OR".equals(operator)) {
                        // Respect panel filters / search-in even when keyword is "*" (empty semantics)
                        seedIds = searchDefinition != null
                                ? executeSingleSearchWithDefinition(search.getFacet(), searchDefinition)
                                : getAllIdsForFacet(search.getFacet());
                    } else {
                        seedIds = applyKeywordFilterToExisting(search, searchDefinition, existingIds, operator);
                    }
                } else {
                    seedIds = applyKeywordFilterToExisting(search, searchDefinition, existingIds, operator);
                }
            } else if ("AND".equals(operator) && !isKeywordEmpty(search.getKeyword())
                    && accumulatedResults != null && !accumulatedResults.isEmpty()
                    && rootFacetId != null
                    && ("PEOPLE".equals(search.getFacet().trim().toUpperCase(Locale.ROOT))
                            || "PERSON".equals(search.getFacet().trim().toUpperCase(Locale.ROOT)))) {
                String rootCanonical = canonFacet(rootFacetId);
                if (rootCanonical == null) {
                    rootCanonical = normalizedFacetToFacetId(normalizeFacetName(rootFacetId));
                }
                boolean useStakeholder = getStakeholderTableConfig(rootFacetId) != null;
                boolean useCreatedBy = getCreatedByDatabaseColumn(rootCanonical) != null;
                if (useStakeholder || useCreatedBy) {
                    // Cross-facet AND with non-empty keyword targeting People (e.g. FIND DataSets AND "alice" in People).
                    // Match root rows where the person is a stakeholder and/or Created By (column varies per facet in FilterMetadataConfig).
                    Set<Integer> matchingPeopleIds = searchDefinition != null
                            ? executeSingleSearchWithDefinition(search.getFacet(), searchDefinition)
                            : executeSingleSearch(search.getFacet(), search.getKeyword(), search.getFilters(), search.getSearchFields());
                    if (matchingPeopleIds.isEmpty()) {
                        seedIds = new HashSet<>();
                    } else {
                        FacetResult rootFacetResult = accumulatedResults.get(rootFacetId);
                        Set<Integer> rootIds = (rootFacetResult != null && rootFacetResult.getIds() != null)
                                ? rootFacetResult.getIds() : new HashSet<>();
                        Set<Integer> byStakeholder = useStakeholder
                                ? getObjectIdsWithRelationToSpecificTargets(rootIds, rootFacetId, search.getFacet(),
                                        matchingPeopleIds)
                                : new HashSet<>();
                        Set<Integer> byCreatedBy = useCreatedBy
                                ? getObjectIdsWithCreatedByPeople(rootIds, rootFacetId, matchingPeopleIds)
                                : new HashSet<>();
                        seedIds = new HashSet<>(byStakeholder);
                        seedIds.addAll(byCreatedBy);
                        usedRelationFilterForRoot = true;
                        if (!seedIds.isEmpty()) {
                            Set<Integer> connectedPeopleIds = new HashSet<>();
                            if (useStakeholder) {
                                connectedPeopleIds.addAll(getRelationTargetIdsByRoot(seedIds, rootFacetId, search.getFacet(),
                                        matchingPeopleIds));
                            }
                            if (useCreatedBy) {
                                connectedPeopleIds.addAll(
                                        getDistinctCreatorPeopleIdsForSeedRows(seedIds, rootFacetId, matchingPeopleIds));
                            }
                            if (!connectedPeopleIds.isEmpty()) {
                                String peopleFacetKey = normalizedFacetToFacetId(normalizeFacetName(search.getFacet()));
                                FacetResult existingPeopleFR = accumulatedResults.get(peopleFacetKey);
                                if (existingPeopleFR == null || existingPeopleFR.getIds() == null
                                        || existingPeopleFR.getIds().isEmpty()) {
                                    accumulatedResults.put(peopleFacetKey, new FacetResult(connectedPeopleIds, true, null));
                                }
                                crossFacetAndTargetIds = connectedPeopleIds;
                                crossFacetAndTargetFacet = peopleFacetKey;
                            }
                        }
                    }
                } else {
                    // Same outer branch guarantees non-empty keyword and non-null accumulatedResults; operator is AND.
                    // Root facet has no stakeholder/Created By path — run a normal People keyword search for intersect.
                    if (searchDefinition != null) {
                        seedIds = executeSingleSearchWithDefinition(search.getFacet(), searchDefinition);
                    } else {
                        seedIds = executeSingleSearch(search.getFacet(), search.getKeyword(), search.getFilters(),
                                search.getSearchFields());
                    }
                }
            } else if (("AND".equals(operator) || "NOT".equals(operator))
                    && accumulatedResults != null && !accumulatedResults.isEmpty()
                    && rootFacetId != null
                    && isOrgUnitFacet(search.getFacet())
                    && getStakeholderTableConfig(rootFacetId) != null) {

                FacetResult rootFacetResult = accumulatedResults.get(rootFacetId);
                if (rootFacetResult == null) {
                    String rootCanon = com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(rootFacetId);
                    rootFacetResult = accumulatedResults.get(rootCanon);
                }
                Set<Integer> rootIds = (rootFacetResult != null && rootFacetResult.getIds() != null)
                        ? rootFacetResult.getIds() : new HashSet<>();

                if (isKeywordEmpty(search.getKeyword())) {
                    // AND/NOT ORG_UNIT * — same as AND/NOT PEOPLE * (any stakeholder = any org unit)
                    if ("AND".equals(operator)) {
                        seedIds = getObjectIdsWithRelationToFacet(rootIds, rootFacetId, "PEOPLE");
                    } else {
                        seedIds = getObjectIdsWithNoRelationToFacet(rootIds, rootFacetId, "PEOPLE");
                    }
                } else {
                    // AND/NOT ORG_UNIT "name" — match org units, then root rows whose stakeholders belong to those org units
                    Set<Integer> matchingOrgUnitIds = searchDefinition != null
                            ? executeSingleSearchWithDefinition(search.getFacet(), searchDefinition)
                            : executeSingleSearch(search.getFacet(), search.getKeyword(), search.getFilters(),
                                    search.getSearchFields());
                    if (matchingOrgUnitIds.isEmpty()) {
                        seedIds = "AND".equals(operator) ? new HashSet<>() : new HashSet<>(rootIds);
                    } else {
                        Set<Integer> withStakeholderInOu =
                                getObjectIdsWithStakeholdersInOrgUnits(rootIds, rootFacetId, matchingOrgUnitIds);
                        if ("AND".equals(operator)) {
                            seedIds = withStakeholderInOu;
                        } else {
                            seedIds = new HashSet<>(rootIds);
                            seedIds.removeAll(withStakeholderInOu);
                        }
                    }
                }
                usedRelationFilterForRoot = true;
            } else {
                if (isKeywordEmpty(search.getKeyword()) && ("FIND".equals(operator) || "OR".equals(operator))) {
                    // FIND/OR with "*" or blank means "all rows" only when there is no SQL definition.
                    // If filters (e.g. External = Yes) or "Search in" built a definition, run QueryBuilder instead of raw getAllIdsForFacet.
                    if (searchDefinition != null) {
                        seedIds = executeSingleSearchWithDefinition(search.getFacet(), searchDefinition);
                    } else {
                        seedIds = getAllIdsForFacet(search.getFacet());
                    }
                    if (accumulatedResults == null && !seedIds.isEmpty() && "FIND".equals(operator)) {
                        rootFacetId = search.getFacet();
                    }
                } else if (searchDefinition != null) {
                    seedIds = executeSingleSearchWithDefinition(search.getFacet(), searchDefinition);
                } else {
                    seedIds = executeSingleSearch(search.getFacet(), search.getKeyword(), search.getFilters(), search.getSearchFields());
                }
                if (accumulatedResults == null && !seedIds.isEmpty() && "FIND".equals(operator) && rootFacetId == null) {
                    rootFacetId = search.getFacet();
                }
            }

            // Expand with children if hierarchical options are provided
            if (search.getHierarchicalOptions() != null && !search.getHierarchicalOptions().isEmpty()) {
                seedIds = expandWithChildren(seedIds, search.getFacet(), 
                        search.getHierarchicalOptions(), search.getFilters());
            }

            if (UnisonTrace.enabled()) {
                String fk = search.getFilters() == null || search.getFilters().isEmpty() ? "[]"
                        : search.getFilters().keySet().toString();
                String defSnip = "";
                if (searchDefinition != null) {
                    String d = searchDefinition.toString();
                    defSnip = d.length() > 600 ? d.substring(0, 600) + "…" : d;
                }
                UnisonTrace.log(correlationId, "clause." + i + ".seeds",
                        "op=" + operator + " facet=" + search.getFacet() + " kwEmpty=" + isKeywordEmpty(search.getKeyword())
                                + " filterKeys=" + fk + " seedCount=" + seedIds.size() + " hasDef=" + (searchDefinition != null)
                                + (defSnip.isEmpty() ? "" : " def=" + defSnip));
            }

            if (seedIds.isEmpty()) {
                // Handle empty results based on operator
                if ("AND".equals(operator)) {
                    // AND with empty results means final result is empty
                    accumulatedResults = new HashMap<>();
                    break;
                } else if ("NOT".equals(operator)) {
                    // NOT requires existing results to exclude from
                    if (accumulatedResults == null) {
                        // NOT as first search is invalid - cannot exclude from nothing
                        accumulatedResults = new HashMap<>();
                        continue;
                    }
                    // Cross-facet NOT with relation filter: empty seed = no root objects satisfy
                    // "no link to facet B" → final result is empty (not "keep all").
                    if (usedRelationFilterForRoot) {
                        accumulatedResults = new HashMap<>();
                        break;
                    }
                    // Legacy same-facet NOT: empty exclusion set → nothing to remove
                    continue;
                } else if ("FIND".equals(operator) || accumulatedResults == null) {
                    // FIND or first search with empty results - initialize empty
                    accumulatedResults = new HashMap<>();
                    continue;
                } else {
                    // OR or default: continue with empty set (will union with existing results)
                    // Empty set unioned with existing results = existing results unchanged
                    continue;
                }
            }

            // When NOT/AND empty filtered by relation, traversal is from root facet with filtered IDs
            String facetForTraversal = (usedRelationFilterForRoot && rootFacetId != null) ? rootFacetId : search.getFacet();

            // If maxDepth is 0, return only direct search results (no graph traversal)
            // This is used when fuzzy search is disabled to get exact matches only
            Map<String, FacetResult> searchResults;

            if (maxDepth == 0) {
                // Return only seed IDs without graph traversal

                searchResults = new HashMap<>();
                String resultFacetId = facetForTraversal;
                Map<Integer, Integer> depthMap = new HashMap<>();
                for (Integer id : seedIds) {
                    depthMap.put(id, 0);
                }
                searchResults.put(resultFacetId, new FacetResult(seedIds, !seedIds.isEmpty(), depthMap));

            } else if (maxDepth == 1) {
                // For maxDepth=1, use graph traversal with depth=1
                // Old getDirectRelationshipsOnly method removed - using graph traversal instead
                TraversalStats stats = new TraversalStats();
                try {
                    searchResults = graphTraversalService.findConnectedObjects(
                        normalizeFacetName(facetForTraversal), seedIds, 1, null, null, accessCtx, stats, correlationId);
                } catch (SQLException e) {
                    System.err.println("[" + correlationId + "] Error in graph traversal: " + e.getMessage());
                    e.printStackTrace();
                    searchResults = new HashMap<>();
                }

            } else {
                // Traverse graph to find connected objects
                // Check if this is the first FIND (root establishment)
                // Root should ONLY be established on FIND operator (never on AND/OR/NOT)
                // This happens either:
                // - First FIND in the request (rootScope == null && accumulatedResults == null/OR after normalization)
                // - Explicit FIND reset (rootScope was reset to null in the operator switch below)
                boolean isRootEstablishment = operator.equals("FIND") && rootScope == null;
                
                if (isRootEstablishment) {
                    // Compute root scope ONCE for the root facet
                    // ✅ Normalize facet to canonical form to prevent duplicates (ATTRIBUTES→ATTRIBUTE, DATA_SETS→DATASET)
                    rootFacet = com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(search.getFacet());
                    rootSeedIds = seedIds;
                    
                    TraversalStats rootStats = new TraversalStats();
                    
                    // Build facet filter (applied post-traversal for backward compat with filters)
                    Map<String, JsonObject> facetFiltersCopy = new HashMap<>(facetFilters);
                    GraphTraversalService.FacetFilter filter = (normalizedFacetId, ids) -> {
                        JsonObject baseDef = facetFiltersCopy.get(normalizedFacetId);
                        if (baseDef == null || ids == null || ids.isEmpty()) {
                            return ids;
                        }
                        String filterFacetId = normalizedFacetToFacetId(normalizedFacetId);
                        String module = facetIdToModuleName(filterFacetId);
                        if (module == null) {
                            return ids;
                        }
                        JsonObject defWithIds = appendIdInFilter(filterFacetId, baseDef, ids);
                        List<Map<String, Object>> filteredRows = searchService.searchWithDefinition(
                                module, defWithIds, accessCtx);
                        return extractIdsFromRows(filteredRows);
                    };
                    
                    // Compute root universe (no rootScope constraint on initial traversal)
                    Map<String, FacetResult> rootUniverse = graphTraversalService.findConnectedObjects(
                            rootFacet, rootSeedIds, maxDepth, filter, null, accessCtx, rootStats, correlationId);
                    
                    // Build rootScope from the root universe
                    Map<String, Set<Integer>> scopeMap = new HashMap<>();
                    for (Map.Entry<String, FacetResult> entry : rootUniverse.entrySet()) {
                        String facetKey = entry.getKey();
                        // ✅ Apply canonical normalization to prevent duplicate keys
                        String canonical = com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(facetKey);
                        String normalized = normalizeFacetName(canonical);
                        Set<Integer> ids = entry.getValue() != null ? entry.getValue().getIds() : Collections.emptySet();
                        scopeMap.put(normalized, new HashSet<>(ids));
                    }
                    rootScope = new TraversalScope(scopeMap);
                    
                    // WARN if root scope was truncated
                    if (rootStats.isTruncated()) {
                        System.err.println("[" + correlationId + "] WARNING: RootScope traversal hit maxTotalResults limit. " +
                                "Results may be incomplete. rootFacet=" + rootFacet + 
                                ", rootSeeds=" + rootSeedIds.size() + 
                                ", maxDepth=" + maxDepth +
                                ", totalResults=" + rootStats.getTotalResults() + 
                                ", maxDepthReached=" + rootStats.getMaxDepthReached());
                    }
                    
                    searchResults = rootUniverse;
                } else {
                    // Subsequent clauses: use rootScope constraint
                    // ✅ Normalize facet to canonical form (use facetForTraversal when NOT/AND empty filtered by relation)
                    String canonicalTraversalFacet = com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(facetForTraversal);
                    
                    TraversalStats stats = new TraversalStats();
                    
                    // Build facet filter
                    Map<String, JsonObject> facetFiltersCopy = new HashMap<>(facetFilters);
                    GraphTraversalService.FacetFilter filter = (normalizedFacetId, ids) -> {
                        JsonObject baseDef = facetFiltersCopy.get(normalizedFacetId);
                        if (baseDef == null || ids == null || ids.isEmpty()) {
                            return ids;
                        }
                        String filterFacetId = normalizedFacetToFacetId(normalizedFacetId);
                        String module = facetIdToModuleName(filterFacetId);
                        if (module == null) {
                            return ids;
                        }
                        JsonObject defWithIds = appendIdInFilter(filterFacetId, baseDef, ids);
                        List<Map<String, Object>> filteredRows = searchService.searchWithDefinition(
                                module, defWithIds, accessCtx);
                        return extractIdsFromRows(filteredRows);
                    };
                    
                    searchResults = graphTraversalService.findConnectedObjects(
                            canonicalTraversalFacet, seedIds, maxDepth, filter, rootScope, accessCtx, stats, correlationId);
                }

                // Pin the active facet to the original seed IDs (no expansion for the seed
                // facet)
                // ✅ Use canonical normalization (facetForTraversal when NOT/AND empty relation filter)
                String canonicalSeedFacet = com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(facetForTraversal);
                String normalizedSeed = normalizeFacetName(canonicalSeedFacet);
                String seedFacetId = normalizedFacetToFacetId(normalizedSeed);
                Map<Integer, Integer> seedDepthMap = new HashMap<>();
                for (Integer id : seedIds) {
                    seedDepthMap.put(id, 0);
                }
                // Remove any alias keys (e.g., DATA-SETS) to avoid duplicates
                searchResults.remove(facetForTraversal);
                searchResults.remove(seedFacetId);
                searchResults.put(seedFacetId, new FacetResult(seedIds, true, seedDepthMap));
            }

            // Inject cross-facet AND People IDs into searchResults so intersectResults can process them
            if (crossFacetAndTargetIds != null && !crossFacetAndTargetIds.isEmpty()
                    && crossFacetAndTargetFacet != null && searchResults != null) {
                searchResults.put(crossFacetAndTargetFacet, new FacetResult(crossFacetAndTargetIds, true, null));
            }

            // Apply operator to combine with accumulated results
            if (accumulatedResults == null) {
                // First search - handle based on actual operator
                if ("NOT".equals(operator)) {
                    // NOT as first search is invalid - cannot exclude from nothing
                    // This should not happen in normal UI flow, but handle gracefully for API/saved searches
                    accumulatedResults = new HashMap<>();
                } else if ("AND".equals(operator)) {
                    // AND as first search: if results are empty, final result is empty (already handled above)
                    // If results exist, use them as initial result
                    accumulatedResults = searchResults;
                    if (maxDepth >= 1 && searchResults != null && !searchResults.isEmpty()) {
                        firstTraversalForRelated = new HashMap<>(searchResults);
                    }
                } else {
                    // FIND, OR, or null/undefined: use as initial result
                    // FIND is the normal case for first search
                    // OR as first search is unusual but valid (equivalent to FIND)
                    accumulatedResults = searchResults;
                    if (maxDepth >= 1 && searchResults != null && !searchResults.isEmpty()) {
                        firstTraversalForRelated = new HashMap<>(searchResults);
                    }
                }
            } else {
                // Apply compound operator for subsequent searches
                switch (operator) {
                    case "AND":
                        accumulatedResults = compoundQueryService.intersectResults(accumulatedResults, searchResults);
                        // After AND intersection, recalculate systems from filtered datasets
                        // This ensures systems are only from datasets that match the AND condition
                        if (accumulatedResults != null) {
                            FacetResult datasetResult = accumulatedResults.get("DATASET");
                            if (datasetResult != null && datasetResult.getIds() != null && !datasetResult.getIds().isEmpty()) {
                                Set<Integer> filteredDatasetIds = datasetResult.getIds();
                                Set<Integer> recalculatedSystemIds = new HashSet<>();
                                Map<Integer, Integer> systemDepth = new HashMap<>();
                                
                                for (Integer datasetId : filteredDatasetIds) {
                                    try {
                                        Set<Integer> systemIds = queryDatasetSystems(datasetId);
                                        for (Integer systemId : systemIds) {
                                            recalculatedSystemIds.add(systemId);
                                            // Preserve minimum depth if system was already in results
                                            FacetResult existingSystemResult = accumulatedResults.get("SYSTEM");
                                            int existingDepth = existingSystemResult != null && existingSystemResult.getDepthById() != null ?
                                                    existingSystemResult.getDepthById().getOrDefault(systemId, Integer.MAX_VALUE) : Integer.MAX_VALUE;
                                            int datasetDepth = datasetResult.getDepthById() != null ?
                                                    datasetResult.getDepthById().getOrDefault(datasetId, Integer.MAX_VALUE) : Integer.MAX_VALUE;
                                            int newDepth = datasetDepth + 1;
                                            systemDepth.put(systemId, Math.min(existingDepth, newDepth));
                                        }
                                    } catch (Exception e) {
                                        System.err.println("[" + correlationId + "] Error querying systems for dataset " + datasetId + ": " + e.getMessage());
                                    }
                                }
                                
                                // Update SYSTEM facet with recalculated systems
                                FacetResult existingSystemResult = accumulatedResults.get("SYSTEM");
                                boolean hasActiveFilter = existingSystemResult != null && existingSystemResult.isHasActiveFilter();
                                accumulatedResults.put("SYSTEM", new FacetResult(recalculatedSystemIds, hasActiveFilter, systemDepth));
                                
                                // Recalculate interfaces from filtered systems
                                // This ensures interfaces are only from systems that match the AND condition
                                if (!recalculatedSystemIds.isEmpty()) {
                                    Set<Integer> recalculatedInterfaceIds = new HashSet<>();
                                    Map<Integer, Integer> interfaceDepth = new HashMap<>();
                                    
                                    for (Integer systemId : recalculatedSystemIds) {
                                        try {
                                            Set<Integer> interfaceIds = queryInterfacesForSystem(systemId);
                                            for (Integer interfaceId : interfaceIds) {
                                                recalculatedInterfaceIds.add(interfaceId);
                                                // Preserve minimum depth if interface was already in results
                                                FacetResult existingInterfaceResult = accumulatedResults.get("INTERFACE");
                                                int existingDepth = existingInterfaceResult != null && existingInterfaceResult.getDepthById() != null ?
                                                        existingInterfaceResult.getDepthById().getOrDefault(interfaceId, Integer.MAX_VALUE) : Integer.MAX_VALUE;
                                                int systemDepthValue = systemDepth.getOrDefault(systemId, Integer.MAX_VALUE);
                                                int newDepth = systemDepthValue + 1; // system -> interface
                                                interfaceDepth.put(interfaceId, Math.min(existingDepth, newDepth));
                                            }
                                        } catch (Exception e) {
                                            System.err.println("[" + correlationId + "] Error querying interfaces for system " + systemId + ": " + e.getMessage());
                                        }
                                    }
                                    
                                    // Update INTERFACE facet with recalculated interfaces
                                    FacetResult existingInterfaceResult = accumulatedResults.get("INTERFACE");
                                    boolean interfaceHasActiveFilter = existingInterfaceResult != null && existingInterfaceResult.isHasActiveFilter();
                                    accumulatedResults.put("INTERFACE", new FacetResult(recalculatedInterfaceIds, interfaceHasActiveFilter, interfaceDepth));
                                }
                            }
                        }
                        break;
                    case "OR":
                        accumulatedResults = compoundQueryService.unionResults(accumulatedResults, searchResults);
                        break;
                    case "FIND":
                        // FIND in different facet replaces all previous results (like START)
                        // This is defensive - frontend should clear conditions, but handle it here too
                        // Note: This behavior means FIND acts as a "reset" when used after other searches
                        accumulatedResults = searchResults; // Replace, don't union
                        // Reset rootScope on explicit FIND
                        rootScope = null;
                        break;
                    case "NOT":
                        // Cross-facet NOT (e.g. FIND People * NOT Roles *): seedIds are already the
                        // root facet rows with no link to facet B; traversal from them defines the
                        // new universe — intersect. excludeResults would subtract that set from the
                        // root facet and incorrectly keep the complement.
                        if (usedRelationFilterForRoot) {
                            accumulatedResults = compoundQueryService.intersectResults(accumulatedResults, searchResults);
                        } else {
                            accumulatedResults = compoundQueryService.excludeResults(accumulatedResults, searchResults);
                        }
                        break;
                    default:
                        // Default to OR for multiple searches
                        accumulatedResults = compoundQueryService.unionResults(accumulatedResults, searchResults);
                        break;
                }
            }

            if (accumulatedResults != null && !accumulatedResults.isEmpty() && rootFacetId != null) {
                int end = subtreeEndIndex(i, indentLevels, nSearches);
                List<Integer> childIndices = directChildIndices(i, end, parentIndex);
                if (!childIndices.isEmpty()) {
                    try {
                        accumulatedResults = applyIndentedChildGroup(
                                accumulatedResults, searches, childIndices, indentLevels, parentIndex, nSearches,
                                rootFacetId, maxDepth, rootScope, accessCtx, correlationId);
                    } catch (SQLException eg) {
                        System.err.println("[" + correlationId + "] Indented group evaluation failed: " + eg.getMessage());
                        eg.printStackTrace();
                    }
                }
            }
        }

        long executionTime = System.currentTimeMillis() - startTime;

        // Populate full rows per facet and recompute counts to match rows
        if (accumulatedResults != null) {
            accumulatedResults = populateFacetRows(accumulatedResults, facetFilters);
        }

        // Enrich results with related CRs and Active Tasks
        if (accumulatedResults != null) {
            accumulatedResults = enrichWithRelatedCRsAndTasks(accumulatedResults);
        }

        // Note: Generic depth filtering has been removed. Each facet now implements
        // its own specific enrichment rules as defined in the business requirements.

        // Deduplicate/normalize facet IDs (e.g., DATA_SETS -> DATASET) and merge data
        accumulatedResults = canonicalizeFacetResults(accumulatedResults);

        // If maxDepth is 0 (no traversal), keep only the active facet's results;
        // otherwise keep cross-facet traversal results
        if (maxDepth == 0) {
            accumulatedResults = filterToActiveFacet(searches, accumulatedResults);
        }

        // For single-facet FIND, tighten to exact matches to avoid duplicates (e.g.,
        // ds001 → one dataset)
        accumulatedResults = filterSingleFacetExactKeyword(searches, accumulatedResults);

        // Attach total (segment-filtered) counts per facet so UI can show "visible of
        // total"
        accumulatedResults = applyAccessibleTotals(accumulatedResults);

        final boolean interfaceOnlyResultSet = accumulatedResults != null && isInterfaceOnlySeedSearch(accumulatedResults);
        final boolean changeRequestOnlyResultSet = accumulatedResults != null && isChangeRequestOnlySeedSearch(accumulatedResults);

        // Restore IDs for related facets (e.g. POLICY) that were emptied by populateFacetRows (e.g. segment filter).
        // Frontend reads result.results.POLICY.ids to load Policy tab; if empty, it shows no data.
        // Skip for interface-only searches: enrichment intentionally dropped traversal noise; do not re-inject first traversal.
        if (!interfaceOnlyResultSet && !changeRequestOnlyResultSet && accumulatedResults != null && firstTraversalForRelated != null
                && !firstTraversalForRelated.isEmpty()) {
            for (Map.Entry<String, FacetResult> e : firstTraversalForRelated.entrySet()) {
                String facetId = e.getKey();
                FacetResult firstFr = e.getValue();
                if (firstFr == null || firstFr.getIds() == null || firstFr.getIds().isEmpty()) continue;
                FacetResult accFr = accumulatedResults.get(facetId);
                if (accFr == null) {
                    accumulatedResults.put(facetId, new FacetResult(
                            new HashSet<>(firstFr.getIds()), false,
                            firstFr.getDepthById() != null ? new HashMap<>(firstFr.getDepthById()) : null));
                } else if (accFr.getIds() == null || accFr.getIds().isEmpty()) {
                    accumulatedResults.put(facetId, new FacetResult(
                            new HashSet<>(firstFr.getIds()), accFr.isHasActiveFilter(),
                            accFr.getDepthById() != null ? accFr.getDepthById() : (firstFr.getDepthById() != null ? new HashMap<>(firstFr.getDepthById()) : null),
                            accFr.getRows(), accFr.getTotalCount()));
                }
            }
        }

        // Build relatedObjects from traversal results so UI can show impact (e.g. Dataset -> Policies).
        // Use firstTraversalForRelated when set so POLICY and other related facets are not lost when a later FIND replaced accumulatedResults.
        Map<String, Map<String, Set<Integer>>> enrichedRelatedObjects = new HashMap<>();
        Map<String, FacetResult> sourceForRelated = (interfaceOnlyResultSet || changeRequestOnlyResultSet)
                ? accumulatedResults
                : ((firstTraversalForRelated != null && !firstTraversalForRelated.isEmpty())
                        ? firstTraversalForRelated
                        : accumulatedResults);
        if (sourceForRelated != null && !sourceForRelated.isEmpty() && searches != null && !searches.isEmpty()) {
            for (UnisonSearchRequest.SearchItem search : searches) {
                if (search == null || search.getFacet() == null) continue;
                String seedFacetId = normalizedFacetToFacetId(normalizeFacetName(search.getFacet()));
                if (seedFacetId == null) seedFacetId = search.getFacet().toUpperCase();
                Map<String, Set<Integer>> related = new HashMap<>();
                for (Map.Entry<String, FacetResult> e : sourceForRelated.entrySet()) {
                    String facet = e.getKey();
                    if (facet != null && facet.equalsIgnoreCase(seedFacetId)) continue;
                    FacetResult fr = e.getValue();
                    if (fr != null && fr.getIds() != null && !fr.getIds().isEmpty()) {
                        related.put(facet, new HashSet<>(fr.getIds()));
                    }
                }
                // Fallback: with one FIND, related objects come from first traversal; if POLICY (or others) is missing
                // (e.g. lost in populateFacetRows due to segment/empty rows), recompute from traversal for this seed.
                FacetResult seedResult = sourceForRelated.get(seedFacetId);
                Set<Integer> seedIds = (seedResult != null && seedResult.getIds() != null) ? seedResult.getIds() : null;
                if (seedIds == null && accumulatedResults != null) {
                    FacetResult accSeed = accumulatedResults.get(seedFacetId);
                    seedIds = (accSeed != null && accSeed.getIds() != null) ? accSeed.getIds() : null;
                }
                // Do not re-run broad graph traversal for interface-only results (would repopulate unrelated facets).
                if (!interfaceOnlyResultSet && seedIds != null && !seedIds.isEmpty() && maxDepth >= 1) {
                    try {
                        Map<String, FacetResult> traversal = graphTraversalService.findConnectedObjects(
                                normalizeFacetName(seedFacetId), seedIds, 1);
                        if (traversal != null) {
                            for (Map.Entry<String, FacetResult> te : traversal.entrySet()) {
                                String tf = te.getKey();
                                if (tf == null || tf.equalsIgnoreCase(seedFacetId)) continue;
                                FacetResult tfr = te.getValue();
                                if (tfr != null && tfr.getIds() != null && !tfr.getIds().isEmpty()) {
                                    if (!related.containsKey(tf) || related.get(tf).isEmpty()) {
                                        related.put(tf, new HashSet<>(tfr.getIds()));
                                    }
                                }
                            }
                        }
                    } catch (SQLException e) {
                        // ignore; keep related as built from sourceForRelated
                    }
                }
                if (!related.isEmpty()) {
                    enrichedRelatedObjects.put(seedFacetId, related);
                }
            }
        }

        // Auto-detect: if no explicit people constraint was passed by the client but the
        // search has a non-FIND, non-display People condition, treat the returned People IDs as the
        // implicit constraint so that other facets are re-filtered automatically.
        if ((peopleConstraintIds == null || peopleConstraintIds.isEmpty()) && accumulatedResults != null) {
            boolean hasPeopleSecondary = searches != null && searches.stream().anyMatch(s ->
                    s != null
                            && !s.isDisplayFilter()
                            && !"FIND".equalsIgnoreCase(s.getOperator())
                            && s.getFacet() != null
                            && ("PEOPLE".equalsIgnoreCase(s.getFacet()) || "PERSON".equalsIgnoreCase(s.getFacet())));
            if (hasPeopleSecondary) {
                FacetResult pf = accumulatedResults.get("PEOPLE");
                if (pf == null) {
                    pf = accumulatedResults.get("PERSON");
                }
                if (pf != null && pf.getIds() != null && !pf.getIds().isEmpty()) {
                    peopleConstraintIds = new HashSet<>(pf.getIds());
                }
            }
        }

        if (peopleConstraintIds != null && !peopleConstraintIds.isEmpty() && accumulatedResults != null) {
            accumulatedResults = applyPeopleConstraintToResults(accumulatedResults, peopleConstraintIds);
            accumulatedResults = applyAccessibleTotals(accumulatedResults);
        }

        UnisonSearchResponse response = new UnisonSearchResponse();
        response.setSuccess(true);
        response.setResults(accumulatedResults != null ? accumulatedResults : new HashMap<>());
        response.setSearchCounter(searches.size());
        response.setExecutionTimeMs(executionTime);
        response.setRelatedObjects(enrichedRelatedObjects);

        if (UnisonTrace.enabled() && accumulatedResults != null) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, FacetResult> e : accumulatedResults.entrySet()) {
                FacetResult fr = e.getValue();
                int n = fr != null && fr.getIds() != null ? fr.getIds().size() : 0;
                sb.append(e.getKey()).append('=').append(n).append(' ');
            }
            UnisonTrace.log(correlationId, "result.final", "ms=" + executionTime + " " + sb);
        }

        return response;
    }

    /**
     * Execute a single search in a facet to find seed objects.
     * Uses existing SearchService to perform the search.
     * If keyword is a numeric ID, searches directly by ID.
     * 
     * @param facet        Facet ID (e.g., "GLOSSARY", "DATASET")
     * @param keyword      Search keyword or ID
     * @param filters      Optional filters (BUDG search JSON: searchGroups/filterGroups)
     * @param searchFields Optional map of field keys → enabled flag from the "Search in" UI
     * @return Set of object IDs matching the search
     * @throws SQLException if database error occurs
     */
    public Set<Integer> executeSingleSearch(String facet, String keyword, Map<String, Object> filters, Map<String, Boolean> searchFields)
            throws SQLException {

        if (facet == null || keyword == null || keyword.trim().isEmpty()) {

            return new HashSet<>();
        }

        // Numeric-ID shortcut (suggestion pick) must not run when panel filters are present — otherwise
        // a role id like "2" is mistaken for Person.ID and filters are ignored.
        if (filters == null || filters.isEmpty()) {
            // Check if keyword is a numeric ID (for selected items from suggestions)
            try {
                int id = Integer.parseInt(keyword.trim());

                return executeSingleSearchById(facet, id);
            } catch (NumberFormatException e) {
                // Not a number, proceed with text search
            }
        }

        // Convert facet ID to module name
        String module = facetIdToModuleName(facet);

        if (module == null) {
            return new HashSet<>();
        }

        // Special handling for Active Tasks - use WorkflowTaskDAO
        if ("activeTasks".equals(module)) {

            try {
                com.example.budg_v2.dao.WorkflowTaskDAO taskDAO = new com.example.budg_v2.dao.WorkflowTaskDAO();

                // Add timeout to prevent indefinite blocking
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors
                        .newSingleThreadExecutor();
                java.util.concurrent.Future<List<Map<String, Object>>> future = executor
                        .submit(() -> taskDAO.findActiveTasksForUser(currentUserId != null ? currentUserId : 0));

                List<Map<String, Object>> tasks;
                try {
                    // Wait max 5 seconds for tasks query
                    tasks = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    System.err.println("[UnisonSearchService] Active tasks query timed out after 5 seconds");
                    future.cancel(true);
                    return new HashSet<>();
                } finally {
                    executor.shutdown();
                }

                // Filter by keyword if provided
                if (keyword != null && !keyword.trim().isEmpty()) {
                    String lowerKeyword = keyword.toLowerCase();
                    tasks = tasks.stream()
                            .filter(task -> {
                                String name = task.get("name") != null ? task.get("name").toString().toLowerCase() : "";
                                String title = task.get("title") != null ? task.get("title").toString().toLowerCase()
                                        : "";
                                String objectType = task.get("objectType") != null
                                        ? task.get("objectType").toString().toLowerCase()
                                        : "";
                                String object = task.get("object") != null ? task.get("object").toString().toLowerCase()
                                        : "";
                                String owner = task.get("owner") != null ? task.get("owner").toString().toLowerCase()
                                        : "";
                                return name.contains(lowerKeyword) || title.contains(lowerKeyword) ||
                                        objectType.contains(lowerKeyword) || object.contains(lowerKeyword) ||
                                        owner.contains(lowerKeyword);
                            })
                            .collect(java.util.stream.Collectors.toList());
                }

                // Extract IDs
                Set<Integer> ids = new HashSet<>();
                for (Map<String, Object> task : tasks) {
                    Object id = task.get("taskId");
                    if (id instanceof Integer) {
                        ids.add((Integer) id);
                    } else if (id instanceof Number) {
                        ids.add(((Number) id).intValue());
                    }
                }

                return ids;
            } catch (Exception e) {
                System.err.println(
                        "[UnisonSearchService] executeSingleSearch: Error searching Active Tasks: " + e.getMessage());
                e.printStackTrace();
                return new HashSet<>();
            }
        }

        // Always build a search definition and use searchWithDefinition so that:
        // 1. Segment filtering (cube selection) is applied via QueryBuilder for all roles.
        // 2. Filters and searchFields (selected search-in columns) are honoured.
        // KeywordSearchService bypasses segment logic entirely, so it is no longer used here.
        JsonObject searchDefinition = buildSearchDefinition(facet, keyword, filters, searchFields);
        List<Map<String, Object>> results = searchService.searchWithDefinition(module, searchDefinition, accessCtx);

        // Extract IDs from results
        Set<Integer> ids = new HashSet<>();
        if (results != null) {
            for (Map<String, Object> row : results) {
                Object id = row.get("ID");
                if (id instanceof Integer) {
                    ids.add((Integer) id);
                } else if (id instanceof Number) {
                    ids.add(((Number) id).intValue());
                }
            }
        }
        return ids;
    }

    /** Backward-compatible overload used by code paths that do not have searchFields. */
    public Set<Integer> executeSingleSearch(String facet, String keyword, Map<String, Object> filters)
            throws SQLException {
        return executeSingleSearch(facet, keyword, filters, null);
    }

    /**
     * Expand search results with children for hierarchical facets.
     * 
     * @param parentIds           Set of parent object IDs
     * @param facet              Facet ID (e.g., "GLOSSARY", "PROCESS")
     * @param hierarchicalOptions Map containing childInclusion and applyFilters options
     * @param filters            Optional filters to apply to children (if applyFilters is "apply")
     * @return Set of IDs including parents and children based on options
     * @throws SQLException if database error occurs
     */
    private Set<Integer> expandWithChildren(Set<Integer> parentIds, String facet, 
            Map<String, String> hierarchicalOptions, Map<String, Object> filters) throws SQLException {
        
        if (parentIds == null || parentIds.isEmpty()) {
            return parentIds != null ? parentIds : new HashSet<>();
        }
        
        if (hierarchicalOptions == null) {
            return parentIds;
        }
        
        String childInclusion = hierarchicalOptions.get("childInclusion");
        String applyFilters = hierarchicalOptions.get("applyFilters");
        
        // If not including children, return parents as is
        if (childInclusion == null || "none".equals(childInclusion)) {
            return parentIds;
        }
        
        // Check if facet is hierarchical
        if (!isHierarchicalFacet(facet)) {
            return parentIds;
        }
        
        Set<Integer> expandedIds = new HashSet<>(parentIds);
        String normalizedFacet = canonFacet(facet);
        
        try {
            // Get children for each parent
            for (Integer parentId : parentIds) {
                Set<Integer> childIds = getChildIds(normalizedFacet, parentId, childInclusion);
                
                // If applyFilters is "apply", filter children by the same filters
                if ("apply".equals(applyFilters) && filters != null && !filters.isEmpty()) {
                    childIds = filterIdsByFilters(childIds, facet, filters);
                }
                
                expandedIds.addAll(childIds);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error expanding with children: " + e.getMessage());
            e.printStackTrace();
            // Return parents only if error occurs
            return parentIds;
        }
        
        return expandedIds;
    }

    /**
     * Get child IDs for a parent object in a hierarchical facet.
     * 
     * @param facet          Normalized facet name (e.g., "GLOSSARY", "PROCESS")
     * @param parentId       Parent object ID
     * @param childInclusion "immediate" for direct children, "all" for all descendants
     * @return Set of child IDs
     * @throws SQLException if database error occurs
     */
    private Set<Integer> getChildIds(String facet, Integer parentId, String childInclusion) throws SQLException {
        Set<Integer> childIds = new HashSet<>();
        
        try {
            switch (facet) {
                case "GLOSSARY":
                    com.example.budg_v2.dao.GlossaryDAO glossaryDAO = new com.example.budg_v2.dao.GlossaryDAO();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> glossaryHierarchy = (Map<String, Object>) glossaryDAO.getGlossaryHierarchy(parentId);
                    childIds.addAll(extractChildIdsFromHierarchy(glossaryHierarchy, parentId, childInclusion));
                    break;
                    
                case "PROCESS":
                    com.example.budg_v2.dao.ProcessDAO processDAO = new com.example.budg_v2.dao.ProcessDAO();
                    List<com.example.budg_v2.model.Process> processHierarchy = processDAO.getProcessHierarchy(parentId);
                    childIds.addAll(extractProcessChildIds(processHierarchy, parentId, childInclusion));
                    break;
                    
                case "POLICY":
                    com.example.budg_v2.dao.PolicyDAO policyDAO = new com.example.budg_v2.dao.PolicyDAO();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> policyHierarchy = (Map<String, Object>) policyDAO.getPolicyHierarchy(parentId);
                    childIds.addAll(extractChildIdsFromHierarchy(policyHierarchy, parentId, childInclusion));
                    break;
                    
                case "CAPABILITY":
                    com.example.budg_v2.dao.CapabilityDAO capabilityDAO = new com.example.budg_v2.dao.CapabilityDAO();
                    // Use getCapabilityHierarchy if it exists, otherwise skip
                    try {
                        java.lang.reflect.Method method = capabilityDAO.getClass().getMethod("getCapabilityHierarchy", Integer.TYPE);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> capabilityHierarchy = (Map<String, Object>) method.invoke(capabilityDAO, parentId);
                        childIds.addAll(extractChildIdsFromHierarchy(capabilityHierarchy, parentId, childInclusion));
                    } catch (NoSuchMethodException e) {
                        System.out.println("[UnisonSearchService] getCapabilityHierarchy method not found, skipping hierarchy expansion");
                    }
                    break;
                    
                default:
                    // Unknown hierarchical facet
                    break;
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error getting child IDs for " + facet + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return childIds;
    }

    /**
     * Extract child IDs from hierarchy map (for Glossary, Policy, Capability).
     */
    private Set<Integer> extractChildIdsFromHierarchy(Map<String, Object> hierarchy, Integer parentId, String childInclusion) {
        Set<Integer> childIds = new HashSet<>();
        
        if (hierarchy == null) {
            return childIds;
        }
        
        // Get descendants list
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> descendants = (List<Map<String, Object>>) hierarchy.get("descendants");
        
        if (descendants == null || descendants.isEmpty()) {
            return childIds;
        }
        
        for (Map<String, Object> descendant : descendants) {
            Object idObj = descendant.get("id") != null ? descendant.get("id") : descendant.get("ID");
            Object levelObj = descendant.get("level");
            
            if (idObj != null) {
                Integer id = idObj instanceof Integer ? (Integer) idObj : Integer.parseInt(idObj.toString());
                
                if ("immediate".equals(childInclusion)) {
                    // Only include immediate children (level = 1 relative to parent)
                    if (levelObj != null) {
                        int level = levelObj instanceof Integer ? (Integer) levelObj : Integer.parseInt(levelObj.toString());
                        // In hierarchy results, immediate children typically have level = parent level + 1
                        // Since we don't know parent level, check if this is a direct child via parentId field
                        Object parentIdObj = descendant.get("parentId") != null ? descendant.get("parentId") : 
                                           descendant.get("parent_id") != null ? descendant.get("parent_id") :
                                           descendant.get("Parent_ID");
                        if (level >= 1 && parentIdObj != null) {
                            Integer descParentId = parentIdObj instanceof Integer ? (Integer) parentIdObj : 
                                                   Integer.parseInt(parentIdObj.toString());
                            if (parentId.equals(descParentId)) {
                                childIds.add(id);
                            }
                        }
                    }
                } else if ("all".equals(childInclusion)) {
                    // Include all descendants
                    childIds.add(id);
                }
            }
        }
        
        return childIds;
    }

    /**
     * Extract child IDs from Process hierarchy list.
     */
    private Set<Integer> extractProcessChildIds(List<com.example.budg_v2.model.Process> processHierarchy, 
            Integer parentId, String childInclusion) {
        Set<Integer> childIds = new HashSet<>();
        
        if (processHierarchy == null || processHierarchy.isEmpty()) {
            return childIds;
        }
        
        for (com.example.budg_v2.model.Process process : processHierarchy) {
            Integer id = process.getId();
            Integer procParentId = process.getParentId();
            
            if (id != null && !id.equals(parentId)) {
                if ("immediate".equals(childInclusion)) {
                    // Only include immediate children
                    if (procParentId != null && procParentId.equals(parentId)) {
                        childIds.add(id);
                    }
                } else if ("all".equals(childInclusion)) {
                    // Include all descendants
                    childIds.add(id);
                }
            }
        }
        
        return childIds;
    }

    /**
     * Filter a set of IDs by applying filters.
     * This executes a search with filters and returns only IDs that match both the input set and the filters.
     */
    private Set<Integer> filterIdsByFilters(Set<Integer> ids, String facet, Map<String, Object> filters) throws SQLException {
        if (ids == null || ids.isEmpty() || filters == null || filters.isEmpty()) {
            return ids != null ? ids : new HashSet<>();
        }
        
        try {
            String module = facetIdToModuleName(facet);
            if (module == null) {
                return ids;
            }
            
            // Build search definition with filters and ID constraint
            JsonObject searchDefinition = buildSearchDefinition(facet, "", filters);
            
            // Add ID filter to only search within the provided IDs
            JsonArray searchGroups = searchDefinition.has("searchGroups") ? 
                    searchDefinition.getAsJsonArray("searchGroups") : new JsonArray();
            
            if (searchGroups.size() > 0) {
                JsonObject searchGroup = searchGroups.get(0).getAsJsonObject();
                JsonArray searches = searchGroup.has("searches") ? 
                        searchGroup.getAsJsonArray("searches") : new JsonArray();
                
                if (searches.size() > 0) {
                    JsonObject search = searches.get(0).getAsJsonObject();
                    JsonArray filterGroups = search.has("filterGroups") ? 
                            search.getAsJsonArray("filterGroups") : new JsonArray();
                    
                    // Add ID IN filter
                    JsonObject idFilter = new JsonObject();
                    idFilter.addProperty("field", "id");
                    idFilter.addProperty("condition", "in");
                    JsonArray idArray = new JsonArray();
                    for (Integer id : ids) {
                        idArray.add(id);
                    }
                    idFilter.add("value", idArray);
                    filterGroups.add(idFilter);
                    
                    search.add("filterGroups", filterGroups);
                }
            }
            
            // Execute search with filters
            List<Map<String, Object>> results = searchService.searchWithDefinition(module, searchDefinition, accessCtx);
            
            // Extract IDs from results
            Set<Integer> filteredIds = new HashSet<>();
            if (results != null) {
                for (Map<String, Object> row : results) {
                    Object id = row.get("ID");
                    if (id instanceof Integer) {
                        filteredIds.add((Integer) id);
                    } else if (id instanceof Number) {
                        filteredIds.add(((Number) id).intValue());
                    }
                }
            }
            
            return filteredIds;
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error filtering IDs: " + e.getMessage());
            e.printStackTrace();
            return ids;
        }
    }

    /**
     * Execute a search using a pre-built BUDG search definition (searchGroups).
     */
    private Set<Integer> executeSingleSearchWithDefinition(String facet, JsonObject searchDefinition)
            throws SQLException {
        String module = facetIdToModuleName(facet);
        if (module == null) {
            return new HashSet<>();
        }

        // Special handling for Active Tasks - use WorkflowTaskDAO
        if ("activeTasks".equals(module)) {
            try {
                com.example.budg_v2.dao.WorkflowTaskDAO taskDAO = new com.example.budg_v2.dao.WorkflowTaskDAO();

                // Add timeout to prevent indefinite blocking
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors
                        .newSingleThreadExecutor();
                java.util.concurrent.Future<List<Map<String, Object>>> future = executor
                        .submit(() -> taskDAO.findActiveTasksForUser(currentUserId != null ? currentUserId : 0));

                List<Map<String, Object>> tasks;
                try {
                    // Wait max 5 seconds for tasks query
                    tasks = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    System.err.println("[UnisonSearchService] Active tasks query timed out after 5 seconds");
                    future.cancel(true);
                    return new HashSet<>();
                } finally {
                    executor.shutdown();
                }

                // Extract keyword from searchDefinition if available
                String keyword = null;
                if (searchDefinition != null && searchDefinition.has("searchGroups")) {
                    JsonArray searchGroups = searchDefinition.getAsJsonArray("searchGroups");
                    for (JsonElement groupEl : searchGroups) {
                        if (groupEl.isJsonObject()) {
                            JsonObject group = groupEl.getAsJsonObject();
                            if (group.has("searches")) {
                                JsonArray searches = group.getAsJsonArray("searches");
                                for (JsonElement searchEl : searches) {
                                    if (searchEl.isJsonObject()) {
                                        JsonObject search = searchEl.getAsJsonObject();
                                        if (search.has("filterGroups")) {
                                            JsonArray filterGroups = search.getAsJsonArray("filterGroups");
                                            for (JsonElement fgEl : filterGroups) {
                                                if (fgEl.isJsonObject()) {
                                                    JsonObject fg = fgEl.getAsJsonObject();
                                                    if (fg.has("query") && fg.get("query").isJsonPrimitive()) {
                                                        keyword = fg.get("query").getAsString();
                                                        break;
                                                    }
                                                }
                                            }
                                            if (keyword != null) break;
                                        }
                                    }
                                }
                                if (keyword != null) break;
                            }
                        }
                    }
                }

                // Filter by keyword if provided
                if (keyword != null && !keyword.trim().isEmpty()) {
                    String lowerKeyword = keyword.toLowerCase();
                    tasks = tasks.stream()
                            .filter(task -> {
                                String name = task.get("name") != null ? task.get("name").toString().toLowerCase() : "";
                                String title = task.get("title") != null ? task.get("title").toString().toLowerCase()
                                        : "";
                                String objectType = task.get("objectType") != null
                                        ? task.get("objectType").toString().toLowerCase()
                                        : "";
                                String object = task.get("object") != null ? task.get("object").toString().toLowerCase()
                                        : "";
                                String owner = task.get("owner") != null ? task.get("owner").toString().toLowerCase()
                                        : "";
                                return name.contains(lowerKeyword) || title.contains(lowerKeyword) ||
                                        objectType.contains(lowerKeyword) || object.contains(lowerKeyword) ||
                                        owner.contains(lowerKeyword);
                            })
                            .collect(java.util.stream.Collectors.toList());
                }

                // Extract IDs
                Set<Integer> ids = new HashSet<>();
                for (Map<String, Object> task : tasks) {
                    Object id = task.get("taskId");
                    if (id instanceof Integer) {
                        ids.add((Integer) id);
                    } else if (id instanceof Number) {
                        ids.add(((Number) id).intValue());
                    }
                }

                return ids;
            } catch (Exception e) {
                System.err.println(
                        "[UnisonSearchService] executeSingleSearchWithDefinition: Error searching Active Tasks: " + e.getMessage());
                e.printStackTrace();
                return new HashSet<>();
            }
        }

        List<Map<String, Object>> results = searchService.searchWithDefinition(module, searchDefinition, accessCtx);
        return extractIdsFromRows(results);
    }

    /**
     * Extract IDs from generic result rows.
     */
    private Set<Integer> extractIdsFromRows(List<Map<String, Object>> rows) {
        Set<Integer> ids = new HashSet<>();
        if (rows == null) {
            return ids;
        }
        for (Map<String, Object> row : rows) {
            // Try uppercase "ID" first (standard column alias)
            Object id = row.get("ID");
            // Fallback to lowercase "id" or "Id" for case-insensitive lookup
            if (id == null) {
                id = row.get("id");
            }
            if (id == null) {
                id = row.get("Id");
            }
            
            if (id != null) {
                if (id instanceof Integer) {
                    ids.add((Integer) id);
                } else if (id instanceof Number) {
                    ids.add(((Number) id).intValue());
                }
            }
        }
        return ids;
    }

    /** Backward-compatible 3-arg overload (no searchFields restriction). */
    private JsonObject buildSearchDefinition(String facetId, String keyword, Map<String, Object> filters) {
        return buildSearchDefinition(facetId, keyword, filters, null);
    }

    /**
     * Build a BUDG-style searchGroups definition from facet, keyword, and filters.
     * Supports:
     * - filters already containing searchGroups
     * - filters containing filterGroups array
     * - keyword included as a query filter to mimic FIND behavior
     * - searchFields embedded in the query filterGroup for column restriction
     */
    private JsonObject buildSearchDefinition(String facetId, String keyword, Map<String, Object> filters, Map<String, Boolean> searchFields) {
        // If filters already carry a searchGroups structure, honor it directly
        if (filters != null && !filters.isEmpty()) {
            JsonObject asJson = gson.toJsonTree(filters).getAsJsonObject();
            if (asJson.has("searchGroups")) {
                return asJson;
            }
        }

        // With structured filters, a purely numeric keyword is almost always a leaked role/field id from the
        // client (same as dropdown value), not a literal text search — treat as broad so QueryBuilder only
        // applies filterGroups (e.g. System_Role IN (...)).
        String effectiveKeyword = keyword;
        if (filters != null && !filters.isEmpty() && effectiveKeyword != null) {
            String t = effectiveKeyword.trim();
            if (!t.isEmpty() && !"*".equals(t) && t.matches("\\d+")) {
                effectiveKeyword = "*";
            }
        }

        JsonArray filterGroups = new JsonArray();

        // Add keyword as a query filter (BUDG FIND across searchable fields).
        // Must match isKeywordEmpty(): "*" and blank mean "no text constraint" — do NOT pass "*" into
        // QueryBuilder or LIKE runs on literal asterisk and AND with real filters returns zero rows.
        if (effectiveKeyword != null && !isKeywordEmpty(effectiveKeyword)) {
            JsonObject qFilter = new JsonObject();
            qFilter.addProperty("query", effectiveKeyword.trim());
            // Embed the user's "Search in" selection so QueryBuilder can restrict columns
            if (searchFields != null && !searchFields.isEmpty()) {
                JsonObject sfJson = new JsonObject();
                searchFields.forEach((k, v) -> sfJson.addProperty(k, v));
                qFilter.add("searchFields", sfJson);
            }
            filterGroups.add(qFilter);
        }

        // Map filters → filterGroups if provided
        if (filters != null && !filters.isEmpty()) {
            JsonElement filtersEl = gson.toJsonTree(filters);
            if (filtersEl.isJsonObject()) {
                JsonObject fo = filtersEl.getAsJsonObject();
                if (fo.has("filterGroups") && fo.get("filterGroups").isJsonArray()) {
                    // Merge provided filterGroups
                    JsonArray fgArr = fo.getAsJsonArray("filterGroups");
                    for (JsonElement fg : fgArr) {
                        filterGroups.add(fg);
                    }
                } else {
                    // Build filter objects from map entries
                    for (Map.Entry<String, JsonElement> entry : fo.entrySet()) {
                        // Skip non-filter keys
                        if (entry.getKey().equals("hierarchicalOptions")) {
                            continue;
                        }
                        
                        // Convert JsonElement to appropriate Java type
                        Object value;
                        JsonElement valueEl = entry.getValue();
                        if (valueEl.isJsonArray()) {
                            List<Object> list = new ArrayList<>();
                            for (JsonElement el : valueEl.getAsJsonArray()) {
                                if (el.isJsonPrimitive()) {
                                    if (el.getAsJsonPrimitive().isNumber()) {
                                        list.add(el.getAsNumber());
                                    } else {
                                        list.add(el.getAsString());
                                    }
                                }
                            }
                            value = list;
                        } else if (valueEl.isJsonObject()) {
                            // Date range or complex object
                            Map<String, Object> map = new HashMap<>();
                            JsonObject obj = valueEl.getAsJsonObject();
                            for (Map.Entry<String, JsonElement> objEntry : obj.entrySet()) {
                                if (objEntry.getValue().isJsonPrimitive()) {
                                    map.put(objEntry.getKey(), objEntry.getValue().getAsString());
                                }
                            }
                            value = map;
                        } else if (valueEl.isJsonPrimitive()) {
                            if (valueEl.getAsJsonPrimitive().isNumber()) {
                                value = valueEl.getAsNumber();
                            } else {
                                value = valueEl.getAsString();
                            }
                        } else {
                            continue; // Skip invalid values
                        }
                        
                        // Build filter object with facetId and filterId
                        JsonObject filterObj = buildFilterObject(facetId, entry.getKey(), value);
                        if (filterObj != null) {
                            filterGroups.add(filterObj);
                        }
                    }
                }
            }
        }

        JsonObject search = new JsonObject();
        search.addProperty("operator", "FIND");
        search.addProperty("active", true);
        search.addProperty("facetId", facetId);
        search.add("filterGroups", filterGroups);

        JsonArray searches = new JsonArray();
        searches.add(search);

        JsonObject group = new JsonObject();
        group.addProperty("operator", "START");
        group.addProperty("active", true);
        group.add("searches", searches);

        JsonArray searchGroups = new JsonArray();
        searchGroups.add(group);

        JsonObject root = new JsonObject();
        root.add("searchGroups", searchGroups);
        return root;
    }

    /**
     * Get the database column name for a filter field in a specific facet.
     * Falls back to FilterField.getFieldName() if no explicit mapping exists.
     * 
     * @param facetId The facet ID (e.g., "DATASET", "GLOSSARY")
     * @param filterId The filter ID (e.g., "createdBy", "isPublic")
     * @return The database column name, or null if filter not found
     */
    private String getColumnNameForFilter(String facetId, String filterId) {
        if (facetId == null || filterId == null) {
            return null;
        }
        
        // Get filter configuration from FilterMetadataConfig
        List<FilterField> filters = FilterMetadataConfig.getFiltersForFacet(facetId);
        for (FilterField filter : filters) {
            if (filter.getId().equals(filterId)) {
                return filter.getFieldName();
            }
        }
        
        // Fallback: return filterId as-is (for backward compatibility)
        return filterId;
    }
    
    /**
     * Build a filter object for a specific field and value.
     * Supports multiple filter types: equals, in, between, contains.
     * 
     * @param facetId The facet ID (e.g., "DATASET", "GLOSSARY")
     * @param filterId The filter ID (e.g., "createdBy", "isPublic")
     * @param value The filter value (can be String, List, Map for ranges)
     * @return JsonObject representing the filter
     */
    private JsonObject buildFilterObject(String facetId, String filterId, Object value) {
        // Custom-field dropdown / multiselect: filter id is "cf_<metadataId>" — emit a filterGroup for QueryBuilder
        if (filterId != null && filterId.startsWith("cf_")) {
            try {
                int cfMetaId = Integer.parseInt(filterId.substring(3));
                JsonObject cf = new JsonObject();
                cf.addProperty("customFieldId", cfMetaId);
                cf.addProperty("condition", "in");
                JsonArray values = new JsonArray();
                if (value instanceof List<?>) {
                    List<?> valueList = (List<?>) value;
                    for (Object v : valueList) {
                        if (v instanceof Number) {
                            values.add((Number) v);
                        } else {
                            values.add(Integer.parseInt(v.toString().trim()));
                        }
                    }
                } else if (value instanceof Number) {
                    values.add((Number) value);
                } else if (value != null) {
                    values.add(Integer.parseInt(value.toString().trim()));
                }
                if (values.size() == 0) {
                    return null;
                }
                cf.add("value", values);
                return cf;
            } catch (NumberFormatException e) {
                System.err.println("[UnisonSearchService] buildFilterObject: invalid cf_ filter id: " + filterId);
                return null;
            }
        }

        // Get filter configuration to determine type
        FilterField filterField = null;
        List<FilterField> filters = FilterMetadataConfig.getFiltersForFacet(facetId);
        for (FilterField f : filters) {
            if (f.getId().equals(filterId)) {
                filterField = f;
                break;
            }
        }
        
        // Get column name
        String columnName = getColumnNameForFilter(facetId, filterId);
        if (columnName == null) {
            columnName = filterId; // Fallback
        }
        
        JsonObject filter = new JsonObject();
        filter.addProperty("field", columnName);
        
        // Determine filter type and operator based on FilterField type and value structure
        FilterType filterType = filterField != null ? filterField.getType() : null;
        
        if (value instanceof List) {
            // Multiple values (IN operator) - for DROPDOWN, PEOPLE, BOOLEAN (multi-select)
            List<?> valueList = (List<?>) value;
            filter.addProperty("condition", "in");
            JsonArray values = new JsonArray();
            for (Object v : valueList) {
                if (v instanceof Number) {
                    values.add((Number) v);
                } else if (filterType == FilterType.BOOLEAN) {
                    // Convert BOOLEAN strings to numbers
                    String strValue = v.toString();
                    if ("Yes".equalsIgnoreCase(strValue) || "1".equals(strValue)) {
                        values.add(1);
                    } else if ("No".equalsIgnoreCase(strValue) || "0".equals(strValue)) {
                        values.add(0);
                    } else {
                        values.add(strValue);
                    }
                } else {
                    values.add(v.toString());
                }
            }
            filter.add("value", values);
        } else if (value instanceof Map) {
            // Date range (BETWEEN operator) - for DATE_RANGE
            Map<?, ?> range = (Map<?, ?>) value;
            filter.addProperty("condition", "between");
            if (range.containsKey("from")) {
                filter.addProperty("from", range.get("from").toString());
            }
            if (range.containsKey("to")) {
                filter.addProperty("to", range.get("to").toString());
            }
        } else {
            // Single value
            if (filterType == FilterType.BOOLEAN) {
                // BOOLEAN: use EQUALS operator
                filter.addProperty("condition", "equals");
                if (value instanceof Number) {
                    filter.addProperty("value", (Number) value);
                } else {
                    // Convert "Yes"/"No" to 1/0 if needed
                    String strValue = value.toString();
                    if ("Yes".equalsIgnoreCase(strValue) || "1".equals(strValue)) {
                        filter.addProperty("value", 1);
                    } else if ("No".equalsIgnoreCase(strValue) || "0".equals(strValue)) {
                        filter.addProperty("value", 0);
                    } else {
                        filter.addProperty("value", strValue);
                    }
                }
            } else if (filterType == FilterType.TEXT) {
                // TEXT: use LIKE/contains operator
                filter.addProperty("condition", "contains");
                filter.addProperty("value", value.toString());
            } else {
                // Default: EQUALS operator (for DROPDOWN, PEOPLE single value)
                filter.addProperty("condition", "equals");
                if (value instanceof Number) {
                    filter.addProperty("value", (Number) value);
                } else {
                    filter.addProperty("value", value.toString());
                }
            }
        }
        
        return filter;
    }
    
    /**
     * Legacy method for backward compatibility.
     * @deprecated Use buildFilterObject(String facetId, String filterId, Object value) instead
     */
    @Deprecated
    @SuppressWarnings("unused")
    private JsonObject buildFilterObject(String field, Object value) {
        JsonObject filter = new JsonObject();
        filter.addProperty("field", field);
        
        if (value instanceof List) {
            List<?> valueList = (List<?>) value;
            filter.addProperty("condition", "in");
            JsonArray values = new JsonArray();
            for (Object v : valueList) {
                if (v instanceof Number) {
                    values.add((Number) v);
                } else {
                    values.add(v.toString());
                }
            }
            filter.add("value", values);
        } else if (value instanceof Map) {
            Map<?, ?> range = (Map<?, ?>) value;
            filter.addProperty("condition", "between");
            if (range.containsKey("from")) {
                filter.addProperty("from", range.get("from").toString());
            }
            if (range.containsKey("to")) {
                filter.addProperty("to", range.get("to").toString());
            }
        } else {
            filter.addProperty("condition", "equals");
            if (value instanceof Number) {
                filter.addProperty("value", (Number) value);
            } else {
                filter.addProperty("value", value.toString());
            }
        }
        
        return filter;
    }

    /**
     * Append an id IN filter to an existing search definition.
     */
    private JsonObject appendIdInFilter(String facetId, JsonObject baseDefinition, Set<Integer> ids) {
        JsonObject def = gson.fromJson(baseDefinition, JsonObject.class); // deep copy

        JsonArray searchGroups = def.has("searchGroups") && def.get("searchGroups").isJsonArray()
                ? def.getAsJsonArray("searchGroups")
                : new JsonArray();

        if (searchGroups.size() == 0) {
            JsonObject search = new JsonObject();
            search.addProperty("operator", "FIND");
            search.addProperty("active", true);
            search.addProperty("facetId", facetId);
            search.add("filterGroups", new JsonArray());

            JsonArray searches = new JsonArray();
            searches.add(search);

            JsonObject group = new JsonObject();
            group.addProperty("operator", "START");
            group.addProperty("active", true);
            group.add("searches", searches);
            searchGroups.add(group);
            def.add("searchGroups", searchGroups);
        }

        // Use first group/search for id constraint
        JsonObject firstGroup = searchGroups.get(0).getAsJsonObject();
        JsonArray searches = firstGroup.has("searches") && firstGroup.get("searches").isJsonArray()
                ? firstGroup.getAsJsonArray("searches")
                : new JsonArray();
        if (searches.size() == 0) {
            JsonObject search = new JsonObject();
            search.addProperty("operator", "START");
            search.addProperty("active", true);
            search.addProperty("facetId", facetId);
            search.add("filterGroups", new JsonArray());
            searches.add(search);
            firstGroup.add("searches", searches);
        }

        JsonObject firstSearch = searches.get(0).getAsJsonObject();
        JsonArray filterGroups = firstSearch.has("filterGroups") && firstSearch.get("filterGroups").isJsonArray()
                ? firstSearch.getAsJsonArray("filterGroups")
                : new JsonArray();

        // Validate IDs before creating filter (prevent empty IN clause SQL error)
        if (ids == null || ids.isEmpty()) {
            // Return filter that matches nothing (non-existent ID)
            JsonObject emptyFilter = new JsonObject();
            emptyFilter.addProperty("field", "id");
            emptyFilter.addProperty("condition", "equals");
            emptyFilter.addProperty("value", -1); // Non-existent ID
            filterGroups.add(emptyFilter);
        } else {
            JsonObject idFilter = new JsonObject();
            idFilter.addProperty("field", "id");
            idFilter.addProperty("condition", "in");
            idFilter.add("value", gson.toJsonTree(ids));
            filterGroups.add(idFilter);
        }

        firstSearch.add("filterGroups", filterGroups);
        return def;
    }

    /**
     * Fetch full rows for each facet using the final ID sets and facet filters.
     */
    private Map<String, FacetResult> populateFacetRows(Map<String, FacetResult> results,
            Map<String, JsonObject> facetFilters) throws SQLException {
        Map<String, FacetResult> enriched = new HashMap<>();
        
        // Per-request cache to prevent duplicate queries for same module+ids combination
        // Key: module + ":" + sortedIdsHash, Value: List<Map<String, Object>>
        Map<String, List<Map<String, Object>>> queryCache = new HashMap<>();
        
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            FacetResult fr = entry.getValue();
            Set<Integer> ids = fr != null ? fr.getIds() : Collections.emptySet();
            Map<Integer, Integer> depthMap = fr != null ? fr.getDepthById() : Collections.emptyMap();
            boolean hasActiveFilter = fr != null && fr.isHasActiveFilter();

            if (ids == null || ids.isEmpty()) {
                // Special handling for Active Tasks: if no results, total should be 0
                // (not the full accessible count, which would show incorrect "0 of 9")
                String normalizedFacet = facetId != null ? facetId.trim().toUpperCase().replace("-", "_") : "";
                boolean isActiveTasks = "ACTIVE_TASKS".equals(normalizedFacet) || 
                                       "ACTIVETASKS".equals(normalizedFacet) || 
                                       "ACTIVE-TASKS".equals(normalizedFacet);
                int total = isActiveTasks ? 0 : computeAccessibleTotalCount(facetId, 0);
                enriched.put(facetId, new FacetResult(Collections.emptySet(), hasActiveFilter, depthMap,
                        Collections.emptyList(), total));
                continue;
            }

            // Normalize facet ID to canonical form (e.g., DATA_SETS -> DATASET)
            String canonicalFacetId = canonicalFacetId(facetId);
            String module = facetIdToModuleName(canonicalFacetId);
            if (module == null) {
                // Fallback to original facetId if canonical doesn't map
                module = facetIdToModuleName(facetId);
            }
            if (module == null) {
                enriched.put(facetId, fr);
                continue;
            }

            // Special handling for Active Tasks
            List<Map<String, Object>> rows;
            Set<Integer> idsFromRows;
            int total;
            if ("activeTasks".equals(module)) {

                try {
                    com.example.budg_v2.dao.WorkflowTaskDAO taskDAO = new com.example.budg_v2.dao.WorkflowTaskDAO();
                    List<Map<String, Object>> allTasks = taskDAO
                            .findActiveTasksForUser(currentUserId != null ? currentUserId : 0);

                    // Filter to only tasks with IDs in the result set
                    List<Map<String, Object>> filteredTasks;
                    if (ids != null && !ids.isEmpty()) {
                        filteredTasks = allTasks.stream()
                                .filter(task -> {
                                    Object id = task.get("taskId");
                                    if (id instanceof Integer) {
                                        return ids.contains((Integer) id);
                                    } else if (id instanceof Number) {
                                        return ids.contains(((Number) id).intValue());
                                    }
                                    return false;
                                })
                                .collect(java.util.stream.Collectors.toList());
                    } else {
                        filteredTasks = allTasks;
                    }
                    // Convert to same row shape as loadActiveTaskRows for frontend column mapping (ID, Name, Title, AssignDate, etc.)
                    rows = new ArrayList<>();
                    for (Map<String, Object> task : filteredTasks) {
                        Map<String, Object> taskRow = new HashMap<>();
                        Object taskIdObj = task.get("taskId");
                        int taskId = taskIdObj instanceof Integer ? (Integer) taskIdObj : (taskIdObj instanceof Number ? ((Number) taskIdObj).intValue() : 0);
                        taskRow.put("ID", taskId);
                        taskRow.put("id", taskId);
                        taskRow.put("Name", task.get("name"));
                        taskRow.put("name", task.get("name"));
                        taskRow.put("Title", task.get("title"));
                        taskRow.put("title", task.get("title"));
                        taskRow.put("ObjectType", task.get("objectType"));
                        taskRow.put("objectType", task.get("objectType"));
                        taskRow.put("Object", task.get("object"));
                        taskRow.put("object", task.get("object"));
                        String assignDateStrP = formatTaskDate(task.get("assignDate"));
                        taskRow.put("AssignDate", assignDateStrP);
                        taskRow.put("assignDate", assignDateStrP);
                        String dueDateStrP = formatTaskDate(task.get("dueDate"));
                        taskRow.put("DueDate", dueDateStrP);
                        taskRow.put("dueDate", dueDateStrP);
                        taskRow.put("DueInDays", task.get("dueInDays"));
                        taskRow.put("dueInDays", task.get("dueInDays"));
                        taskRow.put("isOverdue", task.get("isOverdue"));
                        taskRow.put("Owner", task.get("owner"));
                        taskRow.put("owner", task.get("owner"));
                        taskRow.put("Segments", task.get("segments"));
                        taskRow.put("segments", task.get("segments"));
                        taskRow.put("changeRequestId", task.get("changeRequestId"));
                        taskRow.put("workflowInstanceId", task.get("workflowInstanceId"));
                        taskRow.put("bpmnNodeId", task.get("bpmnNodeId"));
                        rows.add(taskRow);
                    }

                    idsFromRows = extractIdsFromRows(rows);
                    // For Active Tasks, use actual rows count as total (not the full accessible count)
                    // This ensures the displayed count matches the actual data shown
                    total = rows != null ? rows.size() : 0;
                } catch (Exception e) {
                    System.err.println(
                            "[UnisonSearchService] populateFacetRows: Error loading Active Tasks: " + e.getMessage());
                    e.printStackTrace();
                    enriched.put(facetId, fr);
                    continue;
                }
            } else {
                // Check cache first to avoid duplicate queries
                // Create cache key from module + sorted IDs hash
                List<Integer> sortedIds = new ArrayList<>(ids);
                Collections.sort(sortedIds);
                String idsHash = sortedIds.toString();
                String cacheKey = module + ":" + idsHash + ":" + (currentUserId != null ? currentUserId : "null");
                
                if (queryCache.containsKey(cacheKey)) {
                    // Use cached result
                    rows = queryCache.get(cacheKey);
                } else {
                    // Query not in cache, execute it
                    String normalized = normalizeFacetName(canonicalFacetId);
                    JsonObject baseDef = facetFilters != null ? facetFilters.get(normalized) : null;
                    if (baseDef == null) {
                        // minimal definition for id IN
                        baseDef = buildSearchDefinition(canonicalFacetId, null, Collections.emptyMap());
                    }
                    JsonObject defWithIds = appendIdInFilter(canonicalFacetId, baseDef, ids);
                    
                    // Check if user is Super Admin - skip segment filtering (userIdForSearch = -1 used by QueryBuilder when passed via context)
                    @SuppressWarnings("unused")
                    Integer userIdForSearch = currentUserId;
                    boolean isSuperAdmin = false;
                    try {
                        if (currentUserId != null && currentUserId > 0) {
                            isSuperAdmin = SegmentAccessService.isSuperAdmin(currentUserId);
                        }
                    } catch (SQLException e) {
                        System.err.println("[UnisonSearchService] Error checking Super Admin status: " + e.getMessage());
                        // Continue with normal filtering if check fails
                    }
                    
                    if (isSuperAdmin) {
                        // Use -1 as marker for Super Admin (QueryBuilder will check this)
                        userIdForSearch = -1;
                    }
                    rows = searchService.searchWithDefinition(module, defWithIds, accessCtx);
                    
                    // Store in cache for potential reuse
                    queryCache.put(cacheKey, rows);
                }
                
                idsFromRows = extractIdsFromRows(rows);
                
                // Enrich dataset rows with system impact
                if (rows != null && !rows.isEmpty() && 
                    (canonicalFacetId.equals("DATASET") || canonicalFacetId.equals("DATA_SETS") || canonicalFacetId.equals("DATASETS"))) {
                    rows = enrichDatasetRowsWithSystemImpact(rows);
                }

                // Enrich rows with custom field values
                if (rows != null && !rows.isEmpty()) {
                    searchService.enrichWithCustomFields(rows, module);
                    enrichRowsWithActualSegment(rows, module);
                }
                
                total = computeAccessibleTotalCount(facetId, idsFromRows != null ? idsFromRows.size() : 0);

                // Validate: Check for missing IDs
                // Note: Missing IDs are expected when segment filtering is active:
                // - Authenticated users: filtered by their effective segments
                // - Anonymous users: filtered by enterprise-only (segment 1 or no segment assignment)
                // - Super Admin: should see everything (missing IDs are unexpected)
                if (idsFromRows != null && ids != null && !ids.isEmpty()) {
                    Set<Integer> missingIds = new HashSet<>(ids);
                    missingIds.removeAll(idsFromRows);
                    if (!missingIds.isEmpty()) {
                        // Missing IDs are expected when segment filtering is active
                        // This is normal behavior - segmentation filters results in populateFacetRows
                        // No action needed - segmentation is applied correctly via QueryBuilder
                    }
                }
            }

            Map<Integer, Integer> trimmedDepth = new HashMap<>();
            if (idsFromRows != null) {
                for (Integer id : idsFromRows) {
                    if (depthMap != null && depthMap.containsKey(id)) {
                        trimmedDepth.put(id, depthMap.get(id));
                    }
                }
            }

            // Use canonical facet ID for storage to ensure consistency
            String canonicalFacetIdForStorage = canonFacet(facetId);
            enriched.put(canonicalFacetIdForStorage, new FacetResult(idsFromRows, hasActiveFilter, trimmedDepth, rows, total));
        }
        return enriched;
    }

    /**
     * Normalize facet to match traversal keys.
     * Uses FacetNormalizationUtil for consistency across the codebase.
     */
    private String normalizeFacetName(String facet) {
        if (facet == null)
            return null;
        // Use FacetNormalizationUtil to normalize to lowercase canonical form
        return com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToLowercase(facet);
    }

    /**
     * Check if a facet supports hierarchical parent-child relationships.
     * @param facet Facet ID (e.g., "GLOSSARY", "Process")
     * @return true if facet is hierarchical, false otherwise
     */
    private boolean isHierarchicalFacet(String facet) {
        if (facet == null) {
            return false;
        }
        String normalized = canonFacet(facet);
        return HIERARCHICAL_FACETS.contains(normalized);
    }

    /**
     * Normalize facet ID to a canonical representation to avoid duplicates.
     * Uses FacetNormalizationUtil for consistency.
     */
    private String canonicalFacetId(String facetId) {
        return com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(facetId);
    }

    /**
     * Merge two FacetResult objects, preserving unique IDs/rows and min depth.
     */
    private FacetResult mergeFacetResult(FacetResult a, FacetResult b) {
        if (a == null)
            return b;
        if (b == null)
            return a;

        // Merge IDs
        Set<Integer> mergedIds = new LinkedHashSet<>();
        if (a.getIds() != null)
            mergedIds.addAll(a.getIds());
        if (b.getIds() != null)
            mergedIds.addAll(b.getIds());

        // Merge depth maps (take min depth when duplicate)
        Map<Integer, Integer> mergedDepth = new HashMap<>();
        if (a.getDepthById() != null)
            mergedDepth.putAll(a.getDepthById());
        if (b.getDepthById() != null) {
            b.getDepthById()
                    .forEach((k, v) -> mergedDepth.merge(k, v,
                            (existing, incoming) -> (existing == null || incoming == null)
                                    ? (existing != null ? existing : incoming)
                                    : Integer.compare(existing, incoming) <= 0 ? existing : incoming));
        }

        boolean hasActive = a.isHasActiveFilter() || b.isHasActiveFilter();

        // Merge rows by ID; if ID missing, append uniquely using string hash
        Map<Integer, Map<String, Object>> rowsById = new LinkedHashMap<>();
        Set<String> anonRows = new HashSet<>();

        mergeRowsIntoMap(rowsById, anonRows, a.getRows());
        mergeRowsIntoMap(rowsById, anonRows, b.getRows());

        List<Map<String, Object>> mergedRows = new ArrayList<>(rowsById.values());
        for (String s : anonRows) {
            Map<String, Object> m = new HashMap<>();
            m.put("_raw", s);
            mergedRows.add(m);
        }

        // Ensure mergedIds contains IDs from rows as well
        rowsById.keySet().forEach(mergedIds::add);

        int total = Math.max(a.getTotalCount(), b.getTotalCount());
        if (total == 0) {
            total = mergedRows != null ? mergedRows.size() : mergedIds.size();
        }
        return new FacetResult(mergedIds, hasActive, mergedDepth, mergedRows, total);
    }

    private void mergeRowsIntoMap(Map<Integer, Map<String, Object>> rowsById, Set<String> anonRows,
            List<Map<String, Object>> rows) {
        if (rows == null)
            return;
        for (Map<String, Object> row : rows) {
            Integer id = extractIntId(row);
            if (id != null) {
                rowsById.putIfAbsent(id, row);
            } else {
                // For rows without ID, keep a unique string form to avoid duplicates
                String key = row != null ? row.toString() : "null";
                if (anonRows.add(key)) {
                    // stored via anonRows; will be rewrapped later
                }
            }
        }
    }

    private Integer extractIntId(Map<String, Object> row) {
        if (row == null)
            return null;
        Object id = null;
        if (row.containsKey("ID"))
            id = row.get("ID");
        else if (row.containsKey("id"))
            id = row.get("id");
        else if (row.containsKey("Id"))
            id = row.get("Id");
        if (id instanceof Number) {
            return ((Number) id).intValue();
        }
        return null;
    }

    /**
     * IDs present in loaded facet rows (requested IDs may be absent e.g. soft-deleted rows filtered in SQL).
     */
    private LinkedHashSet<Integer> idsFromLoadedRows(List<Map<String, Object>> rows) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            Integer id = extractIntId(row);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    private Map<Integer, Integer> depthSubsetForLoadedIds(Set<Integer> loadedIds,
            Map<Integer, Integer> fullDepth) {
        Map<Integer, Integer> m = new LinkedHashMap<>();
        if (loadedIds == null || fullDepth == null) {
            return m;
        }
        for (Integer id : loadedIds) {
            if (fullDepth.containsKey(id)) {
                m.put(id, fullDepth.get(id));
            }
        }
        return m;
    }

    private FacetResult facetResultFromLoadedRows(String facetId, Map<Integer, Integer> fullDepth,
            List<Map<String, Object>> rows, boolean hasActiveFilter) {
        Set<Integer> ids = idsFromLoadedRows(rows);
        Map<Integer, Integer> depth = depthSubsetForLoadedIds(ids, fullDepth);
        int total = computeAccessibleTotalCount(facetId, ids.size());
        return new FacetResult(ids, hasActiveFilter, depth, rows, total);
    }

    /**
     * Deduplicate facet results by canonical facet ID.
     */
    private Map<String, FacetResult> canonicalizeFacetResults(Map<String, FacetResult> results) {
        if (results == null)
            return new HashMap<>();
        Map<String, FacetResult> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String canonical = canonicalFacetId(entry.getKey());
            FacetResult existing = normalized.get(canonical);
            normalized.put(canonical, mergeFacetResult(existing, entry.getValue()));
        }
        return normalized;
    }

    /**
     * Attach total accessible counts per facet so UI can display "visible of
     * total".
     */
    private Map<String, FacetResult> applyAccessibleTotals(Map<String, FacetResult> results) {
        if (results == null)
            return null;

        Map<String, FacetResult> withTotals = new LinkedHashMap<>();
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            FacetResult fr = entry.getValue();
            if (fr == null) {
                continue;
            }

            int filteredCount = fr.getRows() != null ? fr.getRows().size()
                    : (fr.getIds() != null ? fr.getIds().size() : 0);
            int total = computeAccessibleTotalCount(facetId, filteredCount);
            if (filteredCount > total) {
                System.err.println("[UnisonSearchService] Facet count invariant violated: facetId=" + facetId + ", count=" + filteredCount + ", totalCount=" + total);
                total = Math.max(total, filteredCount);
            }

            FacetResult updated = new FacetResult(
                    fr.getIds(),
                    fr.isHasActiveFilter(),
                    fr.getDepthById(),
                    fr.getRows(),
                    total);
            withTotals.put(facetId, updated);
        }
        return withTotals;
    }


    /**
     * If only a single facet was requested, filter results to that facet to avoid
     * cross-facet leakage.
     */
    private Map<String, FacetResult> filterToActiveFacet(List<UnisonSearchRequest.SearchItem> searches,
            Map<String, FacetResult> results) {
        if (results == null || results.isEmpty() || searches == null)
            return results;

        Set<String> canonicalRequested = new LinkedHashSet<>();
        for (UnisonSearchRequest.SearchItem s : searches) {
            if (s != null && s.getFacet() != null) {
                canonicalRequested.add(canonicalFacetId(s.getFacet()));
            }
        }
        if (canonicalRequested.size() != 1) {
            return results; // multi-facet search: keep all for cross-facet filtering
        }

        String target = canonicalRequested.iterator().next();
        Map<String, FacetResult> filtered = new LinkedHashMap<>();
        if (results.containsKey(target)) {
            filtered.put(target, results.get(target));
        }
        return filtered;
    }

    /**
     * For single-facet FIND searches, filter rows/IDs to exact keyword matches on
     * Ref/Name (and facet-specific display fields) to avoid inflated counts from
     * partial LIKE matches and graph expansion (e.g. related committees, legal links).
     */
    private Map<String, FacetResult> filterSingleFacetExactKeyword(List<UnisonSearchRequest.SearchItem> searches,
            Map<String, FacetResult> results) {
        if (results == null || results.isEmpty() || searches == null || searches.size() != 1)
            return results;
        UnisonSearchRequest.SearchItem s = searches.get(0);
        if (s == null || s.getFacet() == null || s.getKeyword() == null)
            return results;
        String operator = s.getOperator() != null ? s.getOperator().toUpperCase() : "FIND";
        if (!"FIND".equals(operator))
            return results;

        String canonical = canonicalFacetId(s.getFacet());
        FacetResult fr = results.get(canonical);
        if (fr == null || fr.getRows() == null)
            return results;

        String keyword = s.getKeyword().trim();
        List<Map<String, Object>> filteredRows = new ArrayList<>();
        for (Map<String, Object> row : fr.getRows()) {
            if (row == null)
                continue;
            if (rowMatchesSingleFindKeyword(canonical, row, keyword)) {
                filteredRows.add(row);
            }
        }

        if (filteredRows.isEmpty()) {
            return results; // no exact match; keep original
        }

        Set<Integer> ids = new LinkedHashSet<>();
        Map<Integer, Integer> depth = new HashMap<>();
        for (Map<String, Object> row : filteredRows) {
            Integer id = extractIntId(row);
            if (id != null) {
                ids.add(id);
                Integer d = fr.getDepthById() != null ? fr.getDepthById().get(id) : null;
                if (d != null)
                    depth.put(id, d);
            }
        }

        FacetResult narrowed = new FacetResult(ids, fr.isHasActiveFilter(), depth, filteredRows, fr.getTotalCount());
        results.put(canonical, narrowed);
        return results;
    }

    private boolean equalsIgnoreCaseTrim(Object value, String target) {
        if (value == null || target == null)
            return false;
        String v = value.toString().trim();
        return v.equalsIgnoreCase(target.trim());
    }

    private static Object firstRowField(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String k : keys) {
            if (k != null && row.containsKey(k)) {
                Object v = row.get(k);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    /**
     * True when the row is an exact match for the FIND keyword (not substring / LIKE).
     * Handles numeric ID picks, legal entity Short/Long name columns, and standard Ref./Name.
     */
    private boolean rowMatchesSingleFindKeyword(String canonicalFacet, Map<String, Object> row, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }
        String kw = keyword.trim();
        Integer rowId = extractIntId(row);
        if (rowId != null) {
            try {
                int kid = Integer.parseInt(kw);
                if (rowId == kid) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // keyword is not a bare integer ID
            }
        }

        Object ref = firstRowField(row, "Ref.", "ref", "Ref", "refNumber", "REF", "Reference", "reference");
        if (equalsIgnoreCaseTrim(ref, kw)) {
            return true;
        }
        Object name = firstRowField(row, "Name", "name", "NAME");
        if (equalsIgnoreCaseTrim(name, kw)) {
            return true;
        }
        if ("COMMITTEE".equals(canonicalFacet)) {
            Object primary = firstRowField(row, "PrimaryName", "primaryName", "primaryname");
            if (equalsIgnoreCaseTrim(primary, kw)) {
                return true;
            }
        }
        if ("LEGAL_ENTITY".equals(canonicalFacet)) {
            Object shortName = firstRowField(row, "Short Name", "ShortName", "short_name", "Short_Name");
            if (equalsIgnoreCaseTrim(shortName, kw)) {
                return true;
            }
            Object longName = firstRowField(row, "Long Name", "LongName", "long_name", "Long_Name");
            if (equalsIgnoreCaseTrim(longName, kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert normalized facet back to facet ID.
     */
    private String normalizedFacetToFacetId(String normalizedFacet) {
        if (normalizedFacet == null)
            return null;
        return switch (normalizedFacet) {
            case "dataset" -> "DATASET";
            case "attribute" -> "ATTRIBUTE";
            case "system" -> "SYSTEM";
            case "glossary" -> "GLOSSARY";
            case "people" -> "PEOPLE";
            case "interface" -> "INTERFACE";
            case "process" -> "PROCESS";
            case "project" -> "PROJECT";
            case "product" -> "PRODUCT";
            case "policy" -> "POLICY";
            case "legal_entity" -> "LEGAL_ENTITY";
            case "business_area" -> "BUSINESS_AREA";
            case "capability" -> "CAPABILITY";
            case "client" -> "CLIENT";
            case "committee" -> "COMMITTEE";
            case "org_unit" -> "ORG_UNIT";
            case "geography" -> "GEOGRAPHY";
            case "regulation" -> "REGULATION";
            case "regulator" -> "REGULATOR";
            case "regulatory_theme" -> "REGULATORY_THEME";
            case "requirement" -> "REQUIREMENT";
            case "data_store" -> "DATA_STORE";
            case "data_quality" -> "DATAQUALITY";
            case "association_origin" -> "ASSOCIATION_ORIGIN";
            case "role" -> "ROLE";
            default -> normalizedFacet.toUpperCase();
        };
    }

    /**
     * Compute total accessible objects for a facet under current user/segment
     * selection.
     * Falls back to filteredCount when userId is missing or mapping fails.
     * Special handling for ACTIVE_TASKS: uses findActiveTasksForUser to get
     * user-specific total.
     */
    /**
     * Compute the base accessible total count for a facet (before any search/filter).
     * This is the total number of objects the user can access, regardless of current search results.
     * 
     * @param facetId The facet ID (e.g., "DATASET", "GLOSSARY")
     * @param filteredCount The filtered count from search results (not used, kept for backward compatibility)
     * @return The base accessible total count
     */
    private int computeAccessibleTotalCount(String facetId, int filteredCount) {
        if (facetId == null) {
            return 0;
        }
        
        // Normalize facet ID to canonical form for consistent lookups
        String canonicalFacet = canonFacet(facetId);
        String facet = canonicalFacet != null ? canonicalFacet.trim().toUpperCase().replace("-", "_") : "";

        // Special handling for Active Tasks - must use user-specific count
        if ("ACTIVE_TASKS".equals(facet) || "ACTIVETASKS".equals(facet) || "ACTIVE-TASKS".equals(facet)) {
            if (currentUserId == null || currentUserId <= 0) {
                return 0;
            }
            try {
                com.example.budg_v2.dao.WorkflowTaskDAO taskDAO = new com.example.budg_v2.dao.WorkflowTaskDAO();
                List<Map<String, Object>> allUserTasks = taskDAO.findActiveTasksForUser(currentUserId);
                int total = allUserTasks != null ? allUserTasks.size() : 0;
                return total;
            } catch (Exception e) {
                System.err.println(
                        "[UnisonSearchService] ⚠️ Failed to compute total count for ACTIVE_TASKS: " + e.getMessage());
                return 0;
            }
        }

        // Get module and objectType mapping
        String module = facetIdToModuleName(canonicalFacet);
        String objectType = moduleToObjectType(module);
        if (objectType == null) {
            return Math.max(0, filteredCount);
        }

        // For anonymous users, return 0 (they have no accessible objects)
        if (currentUserId == null || currentUserId <= 0) {
            return Math.max(0, filteredCount);
        }

        // Get base accessible total count
        try {
            Set<Integer> accessibleIds = SegmentAccessService.getAccessibleObjectIds(currentUserId, objectType);
            if (accessibleIds.isEmpty()) {
                return Math.max(0, filteredCount);
            }

            Set<Integer> selectedIds = SegmentAccessService.filterBySelectedSegments(
                currentUserId,
                objectType,
                new ArrayList<>(accessibleIds)
            );
            int total = selectedIds.size();
            return Math.max(total, filteredCount);
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] ⚠️ Failed to compute total count for facet " + facetId
                    + " (objectType=" + objectType + "): " + e.getMessage());
            return Math.max(0, filteredCount);
        }
    }

    /**
     * Map module name to segment object type (aligned with SegmentAccessService).
     */
    private String moduleToObjectType(String module) {
        if (module == null)
            return null;
        return switch (module.toLowerCase()) {
            case "dataset" -> "Dataset";
            case "attribute" -> "Dataset"; // attributes inherit dataset segment
            case "system" -> "System";
            case "glossary" -> "Glossary";
            case "process" -> "Process";
            case "project" -> "Project";
            case "product" -> "Product";
            case "policy" -> "Policy";
            case "legal-entity", "legalentity", "legal" -> "LegalEntity";
            case "business-area", "businessarea" -> "BusinessArea";
            case "capability" -> "Capability";
            case "client" -> "Client";
            case "committee" -> "Committee";
            case "orgunit", "org-unit" -> "OrgUnit";
            case "geography" -> "Geography";
            case "regulation" -> "Regulation";
            case "regulator" -> "Regulator";
            case "regulatory-theme", "regulatorytheme" -> "RegulatoryTheme";
            case "interface" -> "Interface";
            case "people" -> "People";
            case "role" -> "People";
            case "dataquality", "data_quality" -> "Dataset";
            case "change-request", "changerequest", "change_request" -> "ChangeRequest";
            default -> null;
        };
    }

    private void enrichRowsWithActualSegment(List<Map<String, Object>> rows, String module) {
        if (rows == null || rows.isEmpty() || module == null || module.isBlank()) {
            return;
        }

        String objectType = moduleToObjectType(module);
        if (objectType == null || "People".equalsIgnoreCase(objectType) || "OrgUnit".equalsIgnoreCase(objectType)) {
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
            } catch (Exception e) {
                // Keep row available even if segment lookup fails for one object.
            }
        }
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
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * Execute a search by specific ID in a facet.
     * Directly queries the database for the object with the given ID.
     * 
     * @param facet Facet ID (e.g., "GLOSSARY", "DATASET")
     * @param id    Object ID to search for
     * @return Set containing the ID if found, empty set otherwise
     * @throws SQLException if database error occurs
     */
    public Set<Integer> executeSingleSearchById(String facet, int id) throws SQLException {
        Set<Integer> ids = new HashSet<>();

        // Convert facet ID to module name
        String module = facetIdToModuleName(facet);
        if (module == null) {
            return ids;
        }

        // Get table name from module
        String tableName = getTableNameForModule(module);
        if (tableName == null) {
            return ids;
        }

        // Query directly by ID
        // Quote table name to handle special characters (e.g., hyphens, spaces)
        String quotedTableName = "`" + tableName + "`";
        // Try both "ID" and "id" column names for compatibility
        String sql = "SELECT ID FROM " + quotedTableName + " WHERE ID = ? OR id = ?";
        List<Object> params = List.of(id, id);

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            if (!results.isEmpty()) {
                ids.add(id);
            }
        } catch (SQLException e) {
            // If query fails, try with just "ID" column
            try {
                String sql2 = "SELECT ID FROM " + quotedTableName + " WHERE ID = ?";
                List<Object> params2 = List.of(id);
                com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
                List<Map<String, Object>> results = dbHelper.executeQuery(sql2, params2);

                if (!results.isEmpty()) {
                    ids.add(id);
                }
            } catch (SQLException e2) {
                // If still fails, try with "id" column
                try {
                    String sql3 = "SELECT id FROM " + quotedTableName + " WHERE id = ?";
                    List<Object> params3 = List.of(id);
                    com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
                    List<Map<String, Object>> results = dbHelper.executeQuery(sql3, params3);

                    if (!results.isEmpty()) {
                        ids.add(id);
                    }
                } catch (SQLException e3) {
                    // Log error but don't throw - return empty set
                    System.err.println("Error searching by ID in facet " + facet + ": " + e3.getMessage());
                }
            }
        }

        return ids;
    }

    /**
     * Apply NOT/AND/OR filter to existing IDs using keyword search (non-empty keyword).
     */
    private Set<Integer> applyKeywordFilterToExisting(UnisonSearchRequest.SearchItem search,
            JsonObject searchDefinition, Set<Integer> existingIds, String operator) throws SQLException {
        if ("NOT".equals(operator)) {
            Set<Integer> matchingIds = keywordSearchService.searchByKeyword(search.getFacet(), search.getKeyword());
            if (searchDefinition != null) {
                String module = facetIdToModuleName(search.getFacet());
                if (module != null) {
                    JsonObject defWithIds = appendIdInFilter(search.getFacet(), searchDefinition, existingIds);
                    List<Map<String, Object>> filteredRows = searchService.searchWithDefinition(
                            module, defWithIds, accessCtx);
                    matchingIds = extractIdsFromRows(filteredRows);
                }
            }
            Set<Integer> seedIds = new HashSet<>(existingIds);
            seedIds.removeAll(matchingIds);
            return seedIds;
        } else if ("AND".equals(operator)) {
            Set<Integer> matchingIds = keywordSearchService.searchByKeyword(search.getFacet(), search.getKeyword());
            if (searchDefinition != null) {
                String module = facetIdToModuleName(search.getFacet());
                if (module != null) {
                    JsonObject defWithIds = appendIdInFilter(search.getFacet(), searchDefinition, existingIds);
                    List<Map<String, Object>> filteredRows = searchService.searchWithDefinition(
                            module, defWithIds, accessCtx);
                    matchingIds = extractIdsFromRows(filteredRows);
                }
            }
            Set<Integer> seedIds = new HashSet<>(existingIds);
            seedIds.retainAll(matchingIds);
            return seedIds;
        } else {
            if (searchDefinition != null) {
                return executeSingleSearchWithDefinition(search.getFacet(), searchDefinition);
            }
            return executeSingleSearch(search.getFacet(), search.getKeyword(), search.getFilters(), search.getSearchFields());
        }
    }

    /**
     * Whether the keyword represents "empty" or "select all" (empty string or "*").
     */
    private static boolean isKeywordEmpty(String keyword) {
        if (keyword == null) return true;
        String t = keyword.trim();
        return t.isEmpty() || "*".equals(t);
    }

    /** True if facet is Org Unit (cross-facet AND/NOT with stakeholders). */
    private boolean isOrgUnitFacet(String facetId) {
        if (facetId == null) {
            return false;
        }
        String n = facetId.trim().toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        return "ORG_UNIT".equals(n) || "ORGUNIT".equals(n) || "ORG_UNITS".equals(n);
    }

    /**
     * Get all IDs for a facet (select all). Used when FIND/OR has empty keyword.
     * Respects the user's cube-selected segments for ALL roles including Super Admin.
     * When accessCtx has effectiveSegments, uses searchWithDefinition so QueryBuilder
     * applies the segment SQL. Falls back to accessible IDs or raw select when no context.
     */
    private Set<Integer> getAllIdsForFacet(String facet) throws SQLException {
        if (facet == null) return new HashSet<>();
        String module = facetIdToModuleName(facet);
        if (module == null) return new HashSet<>();

        // When we have a full access context with effective segments (cube selection),
        // delegate to searchWithDefinition so the QueryBuilder's segment SQL is applied.
        // This respects the cube filter for Super Admin and all other roles.
        if (accessCtx != null && accessCtx.getEffectiveSegments() != null && !accessCtx.getEffectiveSegments().isEmpty()) {
            try {
                // Build an empty search definition — no keyword, no filters — so QueryBuilder
                // generates "SELECT all WHERE segment IN (...)".
                JsonObject emptyDef = new JsonObject();
                emptyDef.add("searchGroups", new JsonArray());
                List<Map<String, Object>> rows = searchService.searchWithDefinition(module, emptyDef, accessCtx);
                Set<Integer> ids = extractIdsFromRows(rows);
                // Filter out nobject_id (temporary cloned rows)
                String facetName = mapModuleToFacetNameForExclusion(module);
                if (facetName != null) {
                    ids.removeAll(getActiveNObjectIdsForFacet(facetName));
                }
                return removeBudgStatusDeletedIdsForWebUser(facet, ids);
            } catch (Exception e) {
                System.err.println("[UnisonSearchService] getAllIdsForFacet (context path) error for " + facet + ": " + e.getMessage());
                // fall through to legacy path
            }
        }

        // Legacy path: use segment-accessible IDs when user is set (no cube filter applied here)
        if (currentUserId != null && currentUserId > 0) {
            String objectType = moduleToObjectType(module);
            if (objectType != null) {
                try {
                    Set<Integer> accessible = SegmentAccessService.getAccessibleObjectIds(currentUserId, objectType);
                    if (accessible != null) {
                        // Filter out nobject_id (temporary cloned rows) for the 4 facets
                        String facetName = mapModuleToFacetNameForExclusion(module);
                        if (facetName != null) {
                            Set<Integer> excludedIds = getActiveNObjectIdsForFacet(facetName);
                            accessible.removeAll(excludedIds);
                        }
                        return removeBudgStatusDeletedIdsForWebUser(facet, accessible);
                    }
                    return new HashSet<>();
                } catch (Exception e) {
                    System.err.println("[UnisonSearchService] getAllIdsForFacet segment error for " + facet + ": " + e.getMessage());
                }
            }
        }
        String sql = getSelectAllIdsSql(module);
        if (sql == null) return new HashSet<>();
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, Collections.emptyList());
            Set<Integer> allIds = extractIdsFromRows(rows);
            
            // Filter out nobject_id (temporary cloned rows) for the 4 facets
            String facetName = mapModuleToFacetNameForExclusion(module);
            if (facetName != null) {
                Set<Integer> excludedIds = getActiveNObjectIdsForFacet(facetName);
                allIds.removeAll(excludedIds);
            }
            
            return removeBudgStatusDeletedIdsForWebUser(facet, allIds);
        } catch (SQLException e) {
            System.err.println("[UnisonSearchService] getAllIdsForFacet error for " + facet + ": " + e.getMessage());
            return new HashSet<>();
        }
    }
    
    /**
     * Map module name to facet name for nobject_id exclusion.
     */
    private String mapModuleToFacetNameForExclusion(String module) {
        if (module == null) return null;
        return switch (module.toLowerCase()) {
            case "dataset", "data-sets" -> "dataset";
            case "glossary" -> "glossary";
            case "system" -> "system";
            case "process" -> "process";
            default -> null;
        };
    }

    /**
     * SQL to select all IDs for a module (with delete filter). Returns null if module not supported.
     */
    private String getSelectAllIdsSql(String module) {
        if (module == null) return null;
        return switch (module.toLowerCase()) {
            case "dataset", "data-sets" -> "SELECT ID FROM dataset WHERE DeletedDatetime IS NULL";
            case "system" -> "SELECT id AS ID FROM system WHERE Deleted_datetime IS NULL";
            case "attribute" -> "SELECT ID FROM attribute WHERE (DeletedDatetime IS NULL OR DeletedDatetime = '')";
            case "glossary" -> "SELECT ID FROM glossary WHERE (Deleted_datetime IS NULL OR Deleted_datetime = '')";
            case "interface" -> "SELECT id AS ID FROM interface WHERE (deleted_datetime IS NULL OR deleted_datetime = '')";
            case "people" -> "SELECT ID FROM people WHERE (Deleted_date IS NULL OR Deleted_date = '')";
            case "orgunit", "org-unit" -> "SELECT ID FROM org_unit WHERE (deleted_Date IS NULL OR deleted_Date = '')";
            case "project" -> "SELECT id AS ID FROM project WHERE (deletedatetime IS NULL OR deletedatetime = '')";
            case "process" -> "SELECT id AS ID FROM process WHERE (deleteddatetime IS NULL OR deleteddatetime = '')";
            case "policy" -> "SELECT ID FROM policy WHERE (DeletedDatetime IS NULL OR DeletedDatetime = '')";
            case "product" -> "SELECT id AS ID FROM product WHERE (deleteddatetime IS NULL OR deleteddatetime = '')";
            case "client" -> "SELECT ID FROM client WHERE (DeleteDatetime IS NULL OR DeleteDatetime = '')";
            case "legal", "legal-entity" -> "SELECT ID FROM legal WHERE (DeleteDatetime IS NULL OR DeleteDatetime = '')";
            case "businessarea", "business-area" -> "SELECT ID FROM business_area WHERE (deletedatetime IS NULL OR deletedatetime = '')";
            case "committee" -> "SELECT ID FROM committee WHERE (DeleteDatetime IS NULL OR DeleteDatetime = '')";
            case "geography" -> "SELECT ID FROM geography WHERE (DeletedDatetime IS NULL OR DeletedDatetime = '')";
            case "regulation" -> "SELECT ID FROM regulation WHERE (DeletedDatetime IS NULL OR DeletedDatetime = '')";
            case "regulator" -> "SELECT ID FROM regulator WHERE (DeletedDatetime IS NULL OR DeletedDatetime = '')";
            case "regulatory-theme" -> "SELECT ID FROM regulatorytheme WHERE (DeletedDatetime IS NULL OR DeletedDatetime = '')";
            case "capability" -> "SELECT ID FROM capability WHERE (DeletedDatetime IS NULL OR DeletedDatetime = '')";
            case "changerequest", "change-request" -> "SELECT ID FROM changerequest WHERE Deleted_At IS NULL";
            case "role", "roles" -> "SELECT ID FROM object_x_people";
            default -> null;
        };
    }

    /**
     * Config for relation from root facet to another facet (junction/direct FK).
     * Used for AND/NOT empty: "objects that have relation" / "objects that have no relation".
     */
    private static class RootToRelationConfig {
        final String tableOrJunction;
        final String rootIdColumn;
        /** Optional AND (...) fragment; no table alias — use bare column names. */
        final String extraSqlPredicate;

        RootToRelationConfig(String tableOrJunction, String rootIdColumn) {
            this(tableOrJunction, rootIdColumn, null);
        }

        RootToRelationConfig(String tableOrJunction, String rootIdColumn, String extraSqlPredicate) {
            this.tableOrJunction = tableOrJunction;
            this.rootIdColumn = rootIdColumn;
            this.extraSqlPredicate = extraSqlPredicate;
        }
    }

    /**
     * Normalize facet labels for direct junction resolution (FIND root vs relation facet).
     */
    private static String normalizeFacetKeyForRelation(String facetId) {
        if (facetId == null) {
            return null;
        }
        String n = facetId.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        return switch (n) {
            case "DATA_SETS", "DATASETS" -> "DATASET";
            case "ATTRIBUTES" -> "ATTRIBUTE";
            case "PERSON", "PEOPLE" -> "PEOPLE";
            case "ROLES" -> "ROLE";
            case "POLICIES" -> "POLICY";
            case "SYSTEMS" -> "SYSTEM";
            case "GLOSSARIES" -> "GLOSSARY";
            case "PROJECTS" -> "PROJECT";
            case "PROCESSES" -> "PROCESS";
            case "PRODUCTS" -> "PRODUCT";
            case "INTERFACES" -> "INTERFACE";
            case "BUSINESS_AREAS", "BUSINESSAREA" -> "BUSINESS_AREA";
            case "LEGAL_ENTITIES", "LEGALENTITY" -> "LEGAL_ENTITY";
            case "CLIENTS" -> "CLIENT";
            case "COMMITTEES" -> "COMMITTEE";
            case "ORG_UNITS", "ORGUNIT" -> "ORG_UNIT";
            case "GEOGRAPHIES" -> "GEOGRAPHY";
            case "REGULATIONS" -> "REGULATION";
            case "REGULATORS" -> "REGULATOR";
            case "CAPABILITIES" -> "CAPABILITY";
            default -> n;
        };
    }

    /**
     * Direct DB link from root facet rows to another facet (junction or FK on root table).
     * Used for empty-keyword AND/NOT across facets so the root facet is filtered by real links,
     * not by ID set subtraction on unrelated facets.
     */
    private RootToRelationConfig resolveDirectFacetRelation(String rootKey, String relKey) {
        if (rootKey == null || relKey == null) {
            return null;
        }
        if ("PEOPLE".equals(rootKey) && "ROLE".equals(relKey)) {
            return new RootToRelationConfig("object_x_people", "ipid", "RoleID IS NOT NULL");
        }
        if ("DATASET".equals(rootKey)) {
            return switch (relKey) {
                case "POLICY" -> new RootToRelationConfig("policy_x_dataset", "DatasetID");
                case "PROCESS" -> new RootToRelationConfig("process_x_dataset", "datasetid");
                case "PROJECT" -> new RootToRelationConfig("project_x_dataset", "dataset_id");
                case "PRODUCT" -> new RootToRelationConfig("product_x_dataset", "Dataset_ID");
                case "GLOSSARY" -> new RootToRelationConfig("dataset", "ID",
                        "glossary IS NOT NULL AND DeletedDatetime IS NULL");
                case "SYSTEM" -> new RootToRelationConfig("dataset", "ID",
                        "MasterSource IS NOT NULL AND DeletedDatetime IS NULL");
                case "ATTRIBUTE" -> new RootToRelationConfig("attribute", "Dataset_ID",
                        "DeletedDatetime IS NULL");
                default -> null;
            };
        }
        if ("POLICY".equals(rootKey)) {
            return switch (relKey) {
                case "DATASET" -> new RootToRelationConfig("policy_x_dataset", "PolicyID");
                case "ATTRIBUTE" -> new RootToRelationConfig("policy_x_attribute", "policyid");
                case "SYSTEM" -> new RootToRelationConfig("policy_x_system", "Policy_ID");
                case "PROCESS" -> new RootToRelationConfig("policy_x_process", "Policy_ID");
                case "PROJECT" -> new RootToRelationConfig("policy_x_project", "Policy_ID");
                case "PRODUCT" -> new RootToRelationConfig("product_x_policy", "policyid");
                case "CLIENT" -> new RootToRelationConfig("client_x_policy", "Policy_ID");
                case "BUSINESS_AREA" -> new RootToRelationConfig("policy_x_businessarea", "Policy_ID");
                case "LEGAL_ENTITY" -> new RootToRelationConfig("policy_x_legal", "Policy_ID");
                default -> null;
            };
        }
        if ("PROCESS".equals(rootKey)) {
            return switch (relKey) {
                case "DATASET" -> new RootToRelationConfig("process_x_dataset", "processid");
                default -> null;
            };
        }
        if ("PROJECT".equals(rootKey)) {
            return switch (relKey) {
                case "DATASET" -> new RootToRelationConfig("project_x_dataset", "projectid");
                default -> null;
            };
        }
        if ("PRODUCT".equals(rootKey)) {
            return switch (relKey) {
                case "DATASET" -> new RootToRelationConfig("product_x_dataset", "Product_ID");
                case "POLICY" -> new RootToRelationConfig("product_x_policy", "productid");
                default -> null;
            };
        }
        if ("SYSTEM".equals(rootKey)) {
            return switch (relKey) {
                case "DATASET" -> new RootToRelationConfig("dataset", "MasterSource",
                        "DeletedDatetime IS NULL");
                default -> null;
            };
        }
        if ("GLOSSARY".equals(rootKey)) {
            return switch (relKey) {
                case "DATASET" -> new RootToRelationConfig("dataset", "glossary",
                        "DeletedDatetime IS NULL");
                case "ATTRIBUTE" -> new RootToRelationConfig("attribute", "Glossary_ID",
                        "DeletedDatetime IS NULL");
                default -> null;
            };
        }
        if ("ATTRIBUTE".equals(rootKey)) {
            return switch (relKey) {
                case "DATASET" -> new RootToRelationConfig("attribute", "ID",
                        "Dataset_ID IS NOT NULL AND DeletedDatetime IS NULL");
                case "POLICY" -> new RootToRelationConfig("policy_x_attribute", "attributeid");
                case "GLOSSARY" -> new RootToRelationConfig("attribute", "ID",
                        "Glossary_ID IS NOT NULL AND DeletedDatetime IS NULL");
                default -> null;
            };
        }
        return null;
    }

    private RootToRelationConfig getRootToRelationConfig(String rootFacetId, String relationFacetId) {
        if (rootFacetId == null || relationFacetId == null) {
            return null;
        }
        String rootKey = normalizeFacetKeyForRelation(rootFacetId);
        String relKey = normalizeFacetKeyForRelation(relationFacetId);

        RootToRelationConfig direct = resolveDirectFacetRelation(rootKey, relKey);
        if (direct != null) {
            return direct;
        }

        String relNorm = relationFacetId.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        if ("PEOPLE".equals(relKey) || "PERSON".equals(relNorm)) {
            StakeholderTableConfig c = getStakeholderTableConfig(rootFacetId);
            if (c != null) {
                return new RootToRelationConfig(c.linkingTable, c.objectIdColumn);
            }
        }
        return null;
    }

    /**
     * Root IDs that have at least one relation to the relation facet (e.g. datasets that have stakeholder).
     */
    private Set<Integer> getObjectIdsWithRelationToFacet(Set<Integer> rootIds, String rootFacetId, String relationFacetId) throws SQLException {
        if (rootIds == null || rootIds.isEmpty()) return new HashSet<>();
        RootToRelationConfig config = getRootToRelationConfig(rootFacetId, relationFacetId);
        if (config == null) return new HashSet<>();
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(rootIds.size(), "?"));
            String sql = "SELECT DISTINCT " + config.rootIdColumn + " FROM " + config.tableOrJunction + " WHERE "
                    + config.rootIdColumn + " IN (" + placeholders + ")";
            if (config.extraSqlPredicate != null && !config.extraSqlPredicate.isBlank()) {
                sql += " AND (" + config.extraSqlPredicate + ")";
            }
            List<Object> params = new ArrayList<>(rootIds);
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, params);
            Set<Integer> out = new HashSet<>();
            for (Map<String, Object> row : rows) {
                Object v = row.get(config.rootIdColumn);
                if (v == null) {
                    String nk = normalizeColumnKey(config.rootIdColumn);
                    for (Map.Entry<String, Object> e : row.entrySet()) {
                        if (e.getKey() != null && normalizeColumnKey(e.getKey()).equals(nk)) {
                            v = e.getValue();
                            break;
                        }
                    }
                }
                if (v instanceof Integer) out.add((Integer) v);
                else if (v instanceof Number) out.add(((Number) v).intValue());
            }
            return out;
        } catch (SQLException e) {
            System.err.println("[UnisonSearchService] getObjectIdsWithRelationToFacet error: " + e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * Filter rootIds to those connected to at least one of the specified targetIds via the junction table.
     * e.g. filter Dataset IDs to those that have Fourth Test (People ID) as a stakeholder.
     */
    private Set<Integer> getObjectIdsWithRelationToSpecificTargets(Set<Integer> rootIds, String rootFacetId,
            String targetFacetId, Set<Integer> targetIds) throws SQLException {
        if (rootIds == null || rootIds.isEmpty() || targetIds == null || targetIds.isEmpty()) return new HashSet<>();
        StakeholderTableConfig config = getStakeholderTableConfig(rootFacetId);
        if (config == null) return new HashSet<>();
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String rootPlaceholders = String.join(",", Collections.nCopies(rootIds.size(), "?"));
            String targetPlaceholders = String.join(",", Collections.nCopies(targetIds.size(), "?"));
            String sql = "SELECT DISTINCT " + config.objectIdColumn + " FROM " + config.linkingTable
                    + " WHERE " + config.objectIdColumn + " IN (" + rootPlaceholders + ")"
                    + " AND " + config.linkingColumn + " IN (" + targetPlaceholders + ")";
            List<Object> params = new ArrayList<>(rootIds);
            params.addAll(targetIds);
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, params);
            Set<Integer> out = new HashSet<>();
            for (Map<String, Object> row : rows) {
                Object v = row.get(config.objectIdColumn);
                if (v instanceof Integer) out.add((Integer) v);
                else if (v instanceof Number) out.add(((Number) v).intValue());
            }
            return out;
        } catch (SQLException e) {
            System.err.println("[UnisonSearchService] getObjectIdsWithRelationToSpecificTargets error: " + e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * Among root facet rows in {@code rootIds}, those that have at least one stakeholder whose
     * {@code people.Org_Unit_ID} is in {@code orgUnitIds}.
     */
    private Set<Integer> getObjectIdsWithStakeholdersInOrgUnits(Set<Integer> rootIds, String rootFacetId,
            Set<Integer> orgUnitIds) {
        if (rootIds == null || rootIds.isEmpty() || orgUnitIds == null || orgUnitIds.isEmpty()) {
            return new HashSet<>();
        }
        StakeholderTableConfig config = getStakeholderTableConfig(rootFacetId);
        if (config == null) {
            return new HashSet<>();
        }
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper =
                    new com.example.unisonsearch.repository.DatabaseHelper();
            String rootPh = String.join(",", Collections.nCopies(rootIds.size(), "?"));
            String ouPh = String.join(",", Collections.nCopies(orgUnitIds.size(), "?"));
            String sql = "SELECT DISTINCT lx." + config.objectIdColumn
                    + " FROM " + config.linkingTable + " lx"
                    + " JOIN object_x_people oxp ON lx." + config.linkingColumn + " = oxp.id"
                    + " JOIN people p ON oxp.ipid = p.ID"
                    + " WHERE lx." + config.objectIdColumn + " IN (" + rootPh + ")"
                    + " AND p.Org_Unit_ID IN (" + ouPh + ")"
                    + " AND p.Deleted_date IS NULL";
            List<Object> params = new ArrayList<>(rootIds);
            params.addAll(orgUnitIds);
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, params);
            return extractIntColumnFromRows(rows, config.objectIdColumn);
        } catch (SQLException e) {
            System.err.println("[UnisonSearchService] getObjectIdsWithStakeholdersInOrgUnits error: " + e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * DB column for "Created By" for this facet, from {@link FilterMetadataConfig}, or null if none.
     */
    private String getCreatedByDatabaseColumn(String canonicalFacetId) {
        if (canonicalFacetId == null || canonicalFacetId.isBlank()) {
            return null;
        }
        String key = canonicalFacetId.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        List<String> keysToTry = new ArrayList<>();
        keysToTry.add(key);
        if ("CHANGEREQUEST".equals(key)) {
            keysToTry.add("CHANGE_REQUEST");
            keysToTry.add("CHANGE_REQUESTS");
        }
        for (String k : keysToTry) {
            for (FilterField f : FilterMetadataConfig.getFiltersForFacet(k)) {
                if ("createdBy".equals(f.getId())) {
                    String col = f.getFieldName();
                    return (col != null && !col.isBlank()) ? col : null;
                }
            }
        }
        return null;
    }

    /**
     * Database column for "Updated By" / last editor on a facet row (from filter metadata).
     */
    private String getUpdatedByDatabaseColumn(String canonicalFacetId) {
        if (canonicalFacetId == null || canonicalFacetId.isBlank()) {
            return null;
        }
        String key = canonicalFacetId.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        List<String> keysToTry = new ArrayList<>();
        keysToTry.add(key);
        if ("CHANGEREQUEST".equals(key) || "CHANGE_REQUEST".equals(key)) {
            keysToTry.add("CHANGE_REQUEST");
            keysToTry.add("CHANGE_REQUESTS");
        }
        for (String k : keysToTry) {
            for (FilterField f : FilterMetadataConfig.getFiltersForFacet(k)) {
                if ("updatedBy".equals(f.getId())) {
                    String col = f.getFieldName();
                    return (col != null && !col.isBlank()) ? col : null;
                }
            }
        }
        return null;
    }

    /** Facets whose counts/rows are narrowed by {@link #applyPeopleConstraintToResults}. */
    private static final Set<String> PEOPLE_CONSTRAINT_CANONICAL_FACETS = Set.of(
            "DATASET", "SYSTEM", "GLOSSARY", "INTERFACE", "PROCESS", "PROJECT", "PRODUCT", "POLICY",
            "ATTRIBUTE", "BUSINESS_AREA", "LEGAL_ENTITY", "CLIENT", "COMMITTEE", "ORG_UNIT",
            "REGULATION", "CAPABILITY", "GEOGRAPHY", "REGULATOR", "REGULATORY_THEME", "CHANGE_REQUESTS", "ROLE");

    private static final String[] AUDIT_QUERY_FACETS = {
            "DATASET", "SYSTEM", "GLOSSARY", "PROCESS", "POLICY", "CAPABILITY", "ATTRIBUTE", "INTERFACE",
            "BUSINESS_AREA", "LEGAL_ENTITY", "CLIENT", "COMMITTEE", "PRODUCT", "ORG_UNIT", "GEOGRAPHY",
            "REGULATION", "REGULATOR", "REGULATORY_THEME", "PROJECT", "CHANGE_REQUEST", "CHANGE_REQUESTS"
    };

    private void mergeFacetObjectMaps(Map<String, Set<Integer>> dest, Map<String, Set<Integer>> src) {
        if (dest == null || src == null) {
            return;
        }
        for (Map.Entry<String, Set<Integer>> e : src.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            String canon = canonicalFacetId(e.getKey());
            if (canon == null) {
                continue;
            }
            dest.computeIfAbsent(canon, k -> new HashSet<>()).addAll(e.getValue());
        }
    }

    /**
     * Objects in each facet where {@code peopleId} matches created-by or updated-by column.
     */
    private Map<String, Set<Integer>> queryObjectsByPersonAuditField(int peopleId, String filterFieldId)
            throws SQLException {
        Map<String, Set<Integer>> out = new HashMap<>();
        if (peopleId <= 0 || filterFieldId == null) {
            return out;
        }
        com.example.unisonsearch.repository.DatabaseHelper dbHelper =
                new com.example.unisonsearch.repository.DatabaseHelper();
        Set<String> seenCanonical = new HashSet<>();
        for (String facetProbe : AUDIT_QUERY_FACETS) {
            String canonical = canonicalFacetId(facetProbe);
            if (canonical == null || !seenCanonical.add(canonical)) {
                continue;
            }
            String col = "createdBy".equals(filterFieldId)
                    ? getCreatedByDatabaseColumn(canonical)
                    : getUpdatedByDatabaseColumn(canonical);
            if (col == null || col.isBlank()) {
                continue;
            }
            String moduleName = facetIdToModuleName(canonical);
            if (moduleName == null) {
                continue;
            }
            String table = getTableNameForModule(moduleName);
            if (table == null || table.isBlank()) {
                continue;
            }
            String pkCol = getEntityPrimaryKeyColumn(canonical);
            try {
                String sql = "SELECT DISTINCT `" + pkCol + "` AS _pk FROM `" + table + "` WHERE `" + col + "` = ?";
                List<Map<String, Object>> rows = dbHelper.executeQuery(sql, List.of(peopleId));
                Set<Integer> ids = new HashSet<>();
                for (Map<String, Object> row : rows) {
                    Object id = row.get("_pk");
                    if (id == null) {
                        id = row.get(pkCol);
                    }
                    if (id instanceof Integer) {
                        ids.add((Integer) id);
                    } else if (id instanceof Number) {
                        ids.add(((Number) id).intValue());
                    }
                }
                if (!ids.isEmpty()) {
                    out.put(canonical, ids);
                }
            } catch (SQLException e) {
                System.err.println("[UnisonSearchService] queryObjectsByPersonAuditField facet=" + canonical + ": "
                        + e.getMessage());
            }
        }
        return out;
    }

    /**
     * All business-object IDs linked to any of {@code peopleIds} (stakeholder rows, created by, updated by).
     */
    private Map<String, Set<Integer>> computeObjectsConnectedToPeople(Set<Integer> peopleIds) throws SQLException {
        Map<String, Set<Integer>> connected = new HashMap<>();
        for (String f : PEOPLE_CONSTRAINT_CANONICAL_FACETS) {
            connected.put(f, new HashSet<>());
        }
        if (peopleIds == null || peopleIds.isEmpty()) {
            return connected;
        }
        for (Integer pid : peopleIds) {
            if (pid == null || pid <= 0) {
                continue;
            }
            mergeFacetObjectMaps(connected, queryPeopleStakeholderObjects(pid));
            mergeFacetObjectMaps(connected, queryObjectsByPersonAuditField(pid, "createdBy"));
            mergeFacetObjectMaps(connected, queryObjectsByPersonAuditField(pid, "updatedBy"));
            Set<Integer> oxp = queryObjectXPeopleIdsForPerson(pid);
            if (oxp != null && !oxp.isEmpty()) {
                connected.get("ROLE").addAll(oxp);
            }
        }
        return connected;
    }

    /**
     * Intersects non-People facet results with objects linked to the given people.
     */
    private Map<String, FacetResult> applyPeopleConstraintToResults(Map<String, FacetResult> results,
            Set<Integer> peopleConstraintIds) throws SQLException {
        if (results == null || results.isEmpty() || peopleConstraintIds == null || peopleConstraintIds.isEmpty()) {
            return results;
        }
        Map<String, Set<Integer>> connected = computeObjectsConnectedToPeople(peopleConstraintIds);
        Map<String, FacetResult> out = new LinkedHashMap<>(results);
        for (Map.Entry<String, FacetResult> e : results.entrySet()) {
            String facetKey = e.getKey();
            if (facetKey == null) {
                continue;
            }
            String upper = facetKey.toUpperCase(Locale.ROOT);
            if ("PEOPLE".equals(upper) || "PERSON".equals(upper)) {
                continue;
            }
            String canonical = canonicalFacetId(facetKey);
            if (canonical == null || !PEOPLE_CONSTRAINT_CANONICAL_FACETS.contains(canonical)) {
                continue;
            }
            Set<Integer> allowed = connected.getOrDefault(canonical, Collections.emptySet());
            FacetResult fr = e.getValue();
            if (fr == null || fr.getIds() == null) {
                continue;
            }
            Set<Integer> filtered = new LinkedHashSet<>();
            for (Integer id : fr.getIds()) {
                if (id != null && allowed.contains(id)) {
                    filtered.add(id);
                }
            }
            Map<Integer, Integer> newDepth = new HashMap<>();
            if (fr.getDepthById() != null) {
                for (Integer id : filtered) {
                    if (fr.getDepthById().containsKey(id)) {
                        newDepth.put(id, fr.getDepthById().get(id));
                    }
                }
            }
            List<Map<String, Object>> rows = loadFacetRows(facetKey, filtered);
            out.put(facetKey, facetResultFromLoadedRows(facetKey, newDepth, rows,
                    fr.isHasActiveFilter() || !peopleConstraintIds.isEmpty()));
        }
        return out;
    }

    private String getEntityPrimaryKeyColumn(String canonicalFacetId) {
        if (canonicalFacetId == null) {
            return "ID";
        }
        String f = canonicalFacetId.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        return switch (f) {
            case "SYSTEM", "INTERFACE", "PROCESS", "PROJECT", "PRODUCT" -> "id";
            default -> "ID";
        };
    }

    /**
     * Among root facet rows in {@code rootIds}, those created by one of {@code peopleIds}
     * (uses per-facet Created By column from filter metadata).
     */
    private Set<Integer> getObjectIdsWithCreatedByPeople(Set<Integer> rootIds, String rootFacetId,
            Set<Integer> peopleIds) throws SQLException {
        if (rootIds == null || rootIds.isEmpty() || peopleIds == null || peopleIds.isEmpty()) {
            return new HashSet<>();
        }
        String canonical = canonFacet(rootFacetId);
        if (canonical == null) {
            canonical = normalizedFacetToFacetId(normalizeFacetName(rootFacetId));
        }
        String createdByCol = getCreatedByDatabaseColumn(canonical);
        if (createdByCol == null) {
            return new HashSet<>();
        }
        String moduleName = facetIdToModuleName(canonical);
        if (moduleName == null) {
            return new HashSet<>();
        }
        String table = getTableNameForModule(moduleName);
        if (table == null || table.isBlank()) {
            return new HashSet<>();
        }
        String pkCol = getEntityPrimaryKeyColumn(canonical);
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper =
                    new com.example.unisonsearch.repository.DatabaseHelper();
            String rPh = String.join(",", Collections.nCopies(rootIds.size(), "?"));
            String pPh = String.join(",", Collections.nCopies(peopleIds.size(), "?"));
            String sql = "SELECT DISTINCT `" + pkCol + "` FROM `" + table + "` WHERE `" + pkCol + "` IN (" + rPh
                    + ") AND `" + createdByCol + "` IN (" + pPh + ")";
            List<Object> params = new ArrayList<>(rootIds);
            params.addAll(peopleIds);
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, params);
            return extractIntColumnFromRows(rows, pkCol);
        } catch (SQLException e) {
            System.err.println("[UnisonSearchService] getObjectIdsWithCreatedByPeople: " + e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * People IDs from {@code matchingPeopleIds} who are recorded as creator on at least one row in {@code seedIds}.
     */
    private Set<Integer> getDistinctCreatorPeopleIdsForSeedRows(Set<Integer> seedIds, String rootFacetId,
            Set<Integer> matchingPeopleIds) throws SQLException {
        if (seedIds == null || seedIds.isEmpty() || matchingPeopleIds == null || matchingPeopleIds.isEmpty()) {
            return new HashSet<>();
        }
        String canonical = canonFacet(rootFacetId);
        if (canonical == null) {
            canonical = normalizedFacetToFacetId(normalizeFacetName(rootFacetId));
        }
        String createdByCol = getCreatedByDatabaseColumn(canonical);
        if (createdByCol == null) {
            return new HashSet<>();
        }
        String moduleName = facetIdToModuleName(canonical);
        if (moduleName == null) {
            return new HashSet<>();
        }
        String table = getTableNameForModule(moduleName);
        if (table == null || table.isBlank()) {
            return new HashSet<>();
        }
        String pkCol = getEntityPrimaryKeyColumn(canonical);
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper =
                    new com.example.unisonsearch.repository.DatabaseHelper();
            String sPh = String.join(",", Collections.nCopies(seedIds.size(), "?"));
            String mPh = String.join(",", Collections.nCopies(matchingPeopleIds.size(), "?"));
            String sql = "SELECT DISTINCT `" + createdByCol + "` AS creator_id FROM `" + table + "` WHERE `" + pkCol
                    + "` IN (" + sPh + ") AND `" + createdByCol + "` IN (" + mPh + ")";
            List<Object> params = new ArrayList<>(seedIds);
            params.addAll(matchingPeopleIds);
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, params);
            return extractIntColumnFromRows(rows, "creator_id");
        } catch (SQLException e) {
            System.err.println("[UnisonSearchService] getDistinctCreatorPeopleIdsForSeedRows: " + e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * For the given rootIds, return which of the candidateTargetIds are connected to them via the junction table.
     * e.g. given filtered Dataset IDs, find which People (from candidateTargetIds) are their stakeholders.
     */
    private Set<Integer> getRelationTargetIdsByRoot(Set<Integer> rootIds, String rootFacetId,
            String targetFacetId, Set<Integer> candidateTargetIds) throws SQLException {
        if (rootIds == null || rootIds.isEmpty() || candidateTargetIds == null || candidateTargetIds.isEmpty()) return new HashSet<>();
        StakeholderTableConfig config = getStakeholderTableConfig(rootFacetId);
        if (config == null) return new HashSet<>();
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String rootPlaceholders = String.join(",", Collections.nCopies(rootIds.size(), "?"));
            String targetPlaceholders = String.join(",", Collections.nCopies(candidateTargetIds.size(), "?"));
            String sql = "SELECT DISTINCT " + config.linkingColumn + " FROM " + config.linkingTable
                    + " WHERE " + config.objectIdColumn + " IN (" + rootPlaceholders + ")"
                    + " AND " + config.linkingColumn + " IN (" + targetPlaceholders + ")";
            List<Object> params = new ArrayList<>(rootIds);
            params.addAll(candidateTargetIds);
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, params);
            Set<Integer> out = new HashSet<>();
            for (Map<String, Object> row : rows) {
                Object v = row.get(config.linkingColumn);
                if (v instanceof Integer) out.add((Integer) v);
                else if (v instanceof Number) out.add(((Number) v).intValue());
            }
            return out;
        } catch (SQLException e) {
            System.err.println("[UnisonSearchService] getRelationTargetIdsByRoot error: " + e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * Root IDs that have no relation to the relation facet (e.g. datasets with no stakeholder).
     */
    private Set<Integer> getObjectIdsWithNoRelationToFacet(Set<Integer> rootIds, String rootFacetId, String relationFacetId) throws SQLException {
        Set<Integer> withRelation = getObjectIdsWithRelationToFacet(rootIds, rootFacetId, relationFacetId);
        Set<Integer> out = new HashSet<>(rootIds);
        out.removeAll(withRelation);
        return out;
    }

    /**
     * Get table name for a module.
     */
    private String getTableNameForModule(String module) {
        if (module == null)
            return null;

        // Align with real table names (see QueryBuilder#getTableNameForModule / schema).
        // Wrong names (e.g. businessarea, orgunit) caused audit queries to fail and no cross-facet rows.
        return switch (module.toLowerCase(Locale.ROOT)) {
            case "data-sets", "dataset" -> "dataset";
            case "business-area", "businessarea", "business_area" -> "business_area";
            case "legal-entity", "legalentity", "legal_entity" -> "legal";
            case "org-unit", "orgunit", "org_unit" -> "org_unit";
            case "regulatory-theme", "regulatorytheme", "regulatory_theme" -> "regulatorytheme";
            case "change-request", "changerequest", "change_request" -> "changerequest";
            default -> module.toLowerCase(Locale.ROOT).replace('-', '_');
        };
    }

    /**
     * Convert facet ID to module name for use with SearchService.
     */
    private String facetIdToModuleName(String facetId) {
        if (facetId == null)
            return null;

        String facet = facetId.trim().toUpperCase().replace("-", "_");
        return switch (facet) {
            case "DATASET", "DATA_SETS", "DATASETS" -> "dataset";
            case "ATTRIBUTE", "ATTRIBUTES" -> "attribute";
            case "SYSTEM", "SYSTEMS" -> "system";
            case "GLOSSARY", "GLOSSARIES" -> "glossary";
            case "DATAQUALITY", "DATA_QUALITY" -> "dataquality";
            case "PEOPLE", "PERSON" -> "people";
            case "ROLE", "ROLES" -> "role";
            case "BUSINESS_AREA", "BUSINESSAREA" -> "business-area";
            case "LEGAL_ENTITY", "LEGALENTITY" -> "legal-entity";
            case "CLIENT", "CLIENTS" -> "client";
            case "COMMITTEE", "COMMITTEES" -> "committee";
            case "POLICY", "POLICIES" -> "policy";
            case "PROCESS", "PROCESSES" -> "process";
            case "PROJECT", "PROJECTS" -> "project";
            case "INTERFACE", "INTERFACES" -> "interface";
            case "CAPABILITY", "CAPABILITIES" -> "capability";
            case "PRODUCT", "PRODUCTS" -> "product";
            case "ORG_UNIT", "ORGUNIT", "ORG_UNITS" -> "orgunit";
            case "GEOGRAPHY", "GEOGRAPHIES" -> "geography";
            case "REGULATION", "REGULATIONS" -> "regulation";
            case "REGULATOR", "REGULATORS" -> "regulator";
            case "REGULATORY_THEME", "REGULATORYTHEME" -> "regulatory-theme";
            case "ACTIVE_TASKS", "ACTIVETASKS", "ACTIVE-TASKS" -> "activeTasks";
            case "CHANGE_REQUESTS", "CHANGEREQUEST", "CHANGE_REQUEST", "CHANGEREQUESTS" -> "change-request";
            default -> null;
        };
    }

    /**
     * Build reference strings for a facet type and object ID.
     * Returns all possible variations (e.g., "Dataset 123", "Data Set 123").
     * 
     * @param facetId  Facet ID (e.g., "DATASET", "SYSTEM")
     * @param objectId Object ID
     * @return List of reference strings
     */
    private List<String> buildReferenceStrings(String facetId, Integer objectId) {
        List<String> references = new ArrayList<>();
        if (facetId == null || objectId == null) {
            return references;
        }

        // Get facet name variations based on facet ID
        String[] variations = getFacetNameVariationsForReference(facetId);
        for (String variation : variations) {
            references.add(variation + " " + objectId);
        }

        return references;
    }

    /**
     * Get facet name variations for building CR references.
     * 
     * @param facetId Facet ID (e.g., "DATASET", "SYSTEM")
     * @return Array of facet name variations
     */
    private String[] getFacetNameVariationsForReference(String facetId) {
        if (facetId == null) {
            return new String[0];
        }

        String normalized = facetId.trim().toUpperCase().replace("-", "_");
        return switch (normalized) {
            case "DATASET", "DATA_SETS", "DATASETS" -> new String[] { "Dataset", "Data Set", "dataset", "data set" };
            case "GLOSSARY", "GLOSSARIES" -> new String[] { "Glossary", "glossary" };
            case "SYSTEM", "SYSTEMS" -> new String[] { "System", "system" };
            case "PROCESS", "PROCESSES" -> new String[] { "Process", "process" };
            case "ATTRIBUTE", "ATTRIBUTES" -> new String[] { "Attribute", "attribute" };
            case "PEOPLE", "PERSON" -> new String[] { "People", "people", "Person", "person" };
            case "ROLE", "ROLES" -> new String[] { "Role", "role" };
            case "INTERFACE", "INTERFACES" -> new String[] { "Interface", "interface" };
            case "PROJECT", "PROJECTS" -> new String[] { "Project", "project" };
            case "PRODUCT", "PRODUCTS" -> new String[] { "Product", "product" };
            case "POLICY", "POLICIES" -> new String[] { "Policy", "policy" };
            case "BUSINESS_AREA", "BUSINESSAREA" -> new String[] { "Business Area", "Business area", "business area" };
            case "LEGAL_ENTITY", "LEGALENTITY" -> new String[] { "Legal Entity", "Legal entity", "legal entity" };
            case "CLIENT", "CLIENTS" -> new String[] { "Client", "client" };
            case "COMMITTEE", "COMMITTEES" -> new String[] { "Committee", "committee" };
            case "ORG_UNIT", "ORGUNIT", "ORG_UNITS" ->
                new String[] { "Org Unit", "Org unit", "org unit", "Organization Unit", "organization unit" };
            case "GEOGRAPHY", "GEOGRAPHIES" -> new String[] { "Geography", "geography" };
            case "REGULATION", "REGULATIONS" -> new String[] { "Regulation", "regulation" };
            case "REGULATOR", "REGULATORS" -> new String[] { "Regulator", "regulator" };
            case "REGULATORY_THEME", "REGULATORYTHEME" ->
                new String[] { "Regulatory Theme", "Regulatory theme", "regulatory theme" };
            case "CAPABILITY", "CAPABILITIES" -> new String[] { "Capability", "capability" };
            case "DATAQUALITY", "DATA_QUALITY" -> new String[] { "Data Quality", "Data quality", "data quality" };
            default -> {
                // Fallback: capitalize first letter
                String name = normalized.toLowerCase().replace("_", " ");
                String capitalized = name.substring(0, 1).toUpperCase() + name.substring(1);
                yield new String[] { capitalized, name };
            }
        };
    }

    /**
     * Enrich search results with related Change Requests and Active Tasks.
     * For each object in the search results, finds CRs that reference it and
     * Active Tasks related to those CRs.
     * 
     * @param results Current search results
     * @return Enriched results with CR and Active Tasks facets added
     * @throws SQLException if database error occurs
     */
    private Map<String, FacetResult> enrichWithRelatedCRsAndTasks(Map<String, FacetResult> results)
            throws SQLException {
        if (results == null || results.isEmpty()) {
            return results;
        }

        final boolean changeRequestOnlySeedSearch = isChangeRequestOnlySeedSearch(results);
        if (changeRequestOnlySeedSearch) {
            EnrichmentContext context = new EnrichmentContext();
            for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
                String facetId = entry.getKey();
                FacetResult fr = entry.getValue();
                if (fr != null && fr.getIds() != null) {
                    for (Integer id : fr.getIds()) {
                        int depth = fr.getDepthById() != null ? fr.getDepthById().getOrDefault(id, 0) : 0;
                        context.markEnriched(facetId, id, depth);
                    }
                }
            }
            results = stripGraphNoiseBeforeChangeRequestPrimaryEnrichment(results);
            results = enrichChangeRequestFacet(results, context);
            results = retainFacetsForChangeRequestPrimarySearch(results);
            results = applyFinalDeduplicationAndSorting(results, context);
            return results;
        }

        final boolean attributeOnlySeedSearch = isAttributeOnlySeedSearch(results);
        final boolean interfaceOnlySeedSearch = isInterfaceOnlySeedSearch(results);

        // Collect only seed object IDs (depth 0) and their facet types.
        // This keeps CR/task enrichment strictly scoped to searched objects.
        Map<String, Set<Integer>> facetToObjectIds = new HashMap<>();
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            FacetResult fr = entry.getValue();

            // Skip CR and Active Tasks facets (they are targets, not sources)
            if (facetId != null && (facetId.toUpperCase().contains("CHANGEREQUEST") ||
                    facetId.toUpperCase().contains("CHANGE_REQUEST") ||
                    facetId.toUpperCase().contains("ACTIVE_TASK") ||
                    facetId.toUpperCase().contains("ACTIVETASK"))) {
                continue;
            }
            
            // Only process GOVERNED_FACETS (exclude Geography, Org Unit, Interface, Product, Business Area, Committee)
            String normalizedFacetId = facetId != null ? facetId.trim().toUpperCase().replace("-", "_") : null;
            if (normalizedFacetId != null && !GOVERNED_FACETS.contains(normalizedFacetId)) {
                continue; // Skip non-governed objects
            }

            if (fr != null && fr.getIds() != null && !fr.getIds().isEmpty()) {
                Set<Integer> seedIds = new HashSet<>();
                if (fr.getDepthById() != null) {
                    for (Integer id : fr.getIds()) {
                        int depth = fr.getDepthById().getOrDefault(id, Integer.MAX_VALUE);
                        if (depth == 0) {
                            seedIds.add(id);
                        }
                    }
                } else {
                    // Backward compatibility for results without depth metadata
                    seedIds.addAll(fr.getIds());
                }

                if (!seedIds.isEmpty()) {
                    facetToObjectIds.put(facetId, seedIds);
                }
            }
        }

        if (facetToObjectIds.isEmpty()) {
            return results;
        }

        Set<Integer> crIds = Collections.emptySet();
        Set<Integer> taskIds = Collections.emptySet();

        if (!attributeOnlySeedSearch) {
            // Build all reference strings
            Set<String> allReferences = new HashSet<>();
            Map<String, Set<Integer>> referenceToObjectIds = new HashMap<>();

            for (Map.Entry<String, Set<Integer>> entry : facetToObjectIds.entrySet()) {
                String facetId = entry.getKey();
                Set<Integer> objectIds = entry.getValue();

                for (Integer objectId : objectIds) {
                    List<String> references = buildReferenceStrings(facetId, objectId);
                    for (String ref : references) {
                        allReferences.add(ref);
                        referenceToObjectIds.computeIfAbsent(ref, k -> new HashSet<>()).add(objectId);
                    }
                }
            }

            if (!allReferences.isEmpty()) {
                crIds = queryChangeRequestsByReferences(allReferences);
                taskIds = queryActiveTasksByCRIds(crIds);
            }
        }

        // Add CR results to accumulated results
        if (!crIds.isEmpty()) {
            String crFacetId = "CHANGEREQUEST";
            FacetResult existingCRResult = results.get(crFacetId);

            Set<Integer> mergedCRIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedCRDepth = new HashMap<>();

            if (existingCRResult != null && existingCRResult.getIds() != null) {
                // Keep only seed CR rows (explicit CR search), drop enriched carry-over.
                if (existingCRResult.getDepthById() != null) {
                    for (Integer id : existingCRResult.getIds()) {
                        int depth = existingCRResult.getDepthById().getOrDefault(id, Integer.MAX_VALUE);
                        if (depth == 0) {
                            mergedCRIds.add(id);
                            mergedCRDepth.put(id, 0);
                        }
                    }
                } else {
                    // Backward compatibility when depth is unavailable
                    mergedCRIds.addAll(existingCRResult.getIds());
                }
            }

            mergedCRIds.addAll(crIds);
            for (Integer crId : crIds) {
                if (!mergedCRDepth.containsKey(crId)) {
                    mergedCRDepth.put(crId, 1); // Depth 1 (direct relationship)
                }
            }

            // Load CR rows
            List<Map<String, Object>> crRows = loadChangeRequestRows(mergedCRIds);
            int crTotal = computeAccessibleTotalCount(crFacetId, crRows != null ? crRows.size() : 0);

            results.put(crFacetId, new FacetResult(mergedCRIds, true, mergedCRDepth, crRows, crTotal));
        }

        // Add Active Tasks results to accumulated results
        if (!taskIds.isEmpty()) {
            String taskFacetId = "ACTIVE_TASKS";
            FacetResult existingTaskResult = results.get(taskFacetId);

            Set<Integer> mergedTaskIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedTaskDepth = new HashMap<>();

            if (existingTaskResult != null && existingTaskResult.getIds() != null) {
                // Keep only seed task rows (explicit Active Tasks search), drop enriched carry-over.
                if (existingTaskResult.getDepthById() != null) {
                    for (Integer id : existingTaskResult.getIds()) {
                        int depth = existingTaskResult.getDepthById().getOrDefault(id, Integer.MAX_VALUE);
                        if (depth == 0) {
                            mergedTaskIds.add(id);
                            mergedTaskDepth.put(id, 0);
                        }
                    }
                } else {
                    // Backward compatibility when depth is unavailable
                    mergedTaskIds.addAll(existingTaskResult.getIds());
                }
            }

            mergedTaskIds.addAll(taskIds);
            for (Integer taskId : taskIds) {
                if (!mergedTaskDepth.containsKey(taskId)) {
                    mergedTaskDepth.put(taskId, 2); // Depth 2 (object -> CR -> task)
                }
            }

            // Load Active Tasks rows
            List<Map<String, Object>> taskRows = loadActiveTaskRows(mergedTaskIds);
            // For Active Tasks, use actual rows count as total (not the full accessible count)
            // This ensures the displayed count matches the actual data shown
            int taskTotal = taskRows != null ? taskRows.size() : 0;

            results.put(taskFacetId, new FacetResult(mergedTaskIds, true, mergedTaskDepth, taskRows, taskTotal));
        }

        // Create enrichment context for deduplication and depth control
        EnrichmentContext context = new EnrichmentContext();
        context.setAttributePrimarySearch(attributeOnlySeedSearch);

        // Mark seed objects (depth 0) from initial results
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            FacetResult fr = entry.getValue();
            if (fr != null && fr.getIds() != null) {
                for (Integer id : fr.getIds()) {
                    int depth = fr.getDepthById() != null ? fr.getDepthById().getOrDefault(id, 0) : 0;
                    context.markEnriched(facetId, id, depth);
                }
            }
        }

        // Enrich with stakeholders (People, Org Units, Roles) - direct relations only
        results = enrichWithStakeholdersAndRelated(results, context);

        // Attribute-only FIND: strict related set (dataset, system, glossaries, junctions, stakeholders)
        if (attributeOnlySeedSearch) {
            results = enrichAttributeFacet(results, context);
            results = applyFinalDeduplicationAndSorting(results, context);
            return results;
        }

        // Interface-only FIND: drop traversal noise, then only source/target systems + impact links + stakeholders + CR/tasks
        if (interfaceOnlySeedSearch) {
            results = stripGraphNoiseBeforeInterfacePrimaryEnrichment(results);
            results = enrichInterfaceFacet(results, context);
            results = retainFacetsForInterfacePrimarySearch(results);
            results = applyFinalDeduplicationAndSorting(results, context);
            return results;
        }

        // Enrich datasets with impact relationships (Process, Project, Product,
        // Business Area, Capability)
        results = enrichDatasetWithImpactRelationships(results);

        // Enrich datasets with related glossaries
        results = enrichDatasetWithGlossaries(results);

        // Facet-specific enrichment (with context to prevent recursion)
        results = enrichDatasetFacet(results, context);
        results = enrichAttributeFacet(results, context);
        results = enrichSystemFacet(results, context);
        results = enrichLegalEntityFacet(results, context);

        results = enrichGlossaryFacet(results, context);
        results = enrichPeopleFacet(results, context);
        results = enrichRoleFacet(results, context);
        results = enrichProjectProcessPolicyFacet(results, context);
        results = enrichChangeRequestFacet(results, context);
        results = enrichCapabilityFacet(results, context);
        results = enrichProductAndBusinessAreaFacet(results, context);
        results = enrichInterfaceFacet(results, context);
        results = enrichOrgUnitFacet(results, context);
        results = enrichGeographyFacet(results, context);
        results = enrichRegulationRegulatorThemeFacet(results, context);

        // Apply final deduplication and deterministic sorting
        results = applyFinalDeduplicationAndSorting(results, context);

        System.out.println("[UNISON-DEBUG][FINAL] Facets returned:");
        for (Map.Entry<String, FacetResult> e : results.entrySet()) {
            FacetResult fr = e.getValue();
            System.out.println("[UNISON-DEBUG]   " + e.getKey() + " count=" + (fr != null ? fr.getCount() : "null") + " ids=" + (fr != null ? fr.getIds() : "null"));
        }

        return results;
    }

    /**
     * Enrich search results with related People, Org Units, and Roles from direct
     * stakeholders.
     * For each object in the search results, finds direct stakeholders and
     * extracts:
     * - People IDs (from stakeholders)
     * - Role IDs (from stakeholders)
     * - Org Unit IDs (from people records)
     * 
     * @param results Current search results
     * @return Enriched results with People, Org Unit, and Roles facets added
     * @throws SQLException if database error occurs
     */
    private Map<String, FacetResult> enrichWithStakeholdersAndRelated(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty()) {
            return results;
        }

        // Identify seed facets (depth 0) - these are the original search targets
        Set<String> seedFacets = new HashSet<>();
        if (context != null) {
            for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
                String facetId = entry.getKey();
                FacetResult fr = entry.getValue();
                if (fr != null && fr.getIds() != null && fr.getDepthById() != null) {
                    // Check if any ID has depth 0 (seed object)
                    boolean hasSeedObject = fr.getIds().stream()
                        .anyMatch(id -> fr.getDepthById().getOrDefault(id, Integer.MAX_VALUE) == 0);
                    if (hasSeedObject) {
                        String normalized = facetId != null ? facetId.trim().toUpperCase().replace("-", "_") : null;
                        if (normalized != null) {
                            seedFacets.add(normalized);
                        }
                    }
                }
            }
        }

        // Collect all object IDs and their facet types - only from seed facets (direct relations only)
        Map<String, Set<Integer>> facetToObjectIds = new HashMap<>();
        System.out.println("[UNISON-DEBUG][enrichWithStakeholders] Seed facets: " + seedFacets);
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            FacetResult fr = entry.getValue();

            // Skip People, Org Unit, Roles, CR, and Active Tasks facets (they are targets,
            // not sources)
            if (facetId != null && (facetId.toUpperCase().contains("PEOPLE") ||
                    facetId.toUpperCase().contains("ORG") ||
                    facetId.toUpperCase().contains("ROLE") ||
                    facetId.toUpperCase().contains("CHANGEREQUEST") ||
                    facetId.toUpperCase().contains("CHANGE_REQUEST") ||
                    facetId.toUpperCase().contains("ACTIVE_TASK") ||
                    facetId.toUpperCase().contains("ACTIVETASK"))) {
                continue;
            }

            // Only process seed facets (original search targets) - ignore enriched/related objects
            String normalizedFacetId = facetId != null ? facetId.trim().toUpperCase().replace("-", "_") : null;
            if (normalizedFacetId != null && !seedFacets.isEmpty() && !seedFacets.contains(normalizedFacetId)) {
                continue; // Skip non-seed facets to ensure direct relations only
            }
            
            // Only process GOVERNED_FACETS (exclude Geography, Interface, Product, Business Area, Committee)
            if (normalizedFacetId != null && !GOVERNED_FACETS.contains(normalizedFacetId)) {
                continue; // Skip non-governed objects
            }

            if (fr != null && fr.getIds() != null && !fr.getIds().isEmpty()) {
                // Only include IDs with depth 0 (seed objects)
                Set<Integer> seedIds = new HashSet<>();
                if (fr.getDepthById() != null) {
                    for (Integer id : fr.getIds()) {
                        int depth = fr.getDepthById().getOrDefault(id, Integer.MAX_VALUE);
                        if (depth == 0) {
                            seedIds.add(id);
                        }
                    }
                } else {
                    // If no depth info, assume all are seed (backward compatibility)
                    seedIds.addAll(fr.getIds());
                }
                if (!seedIds.isEmpty()) {
                    facetToObjectIds.put(facetId, seedIds);
                }
            }
        }

        if (facetToObjectIds.isEmpty()) {
            return results;
        }

        // Collect all stakeholder data
        Set<Integer> allPeopleIds = new HashSet<>();
        Set<Integer> allObjectXPeopleIds = new HashSet<>();
        Set<Integer> allOrgUnitIds = new HashSet<>();

        // Query stakeholders for each object
        for (Map.Entry<String, Set<Integer>> entry : facetToObjectIds.entrySet()) {
            String facetId = entry.getKey();
            Set<Integer> objectIds = entry.getValue();
            System.out.println("[UNISON-DEBUG][enrichWithStakeholders] Querying facet=" + facetId + " ids=" + objectIds);

            for (Integer objectId : objectIds) {
                try {
                    List<Map<String, Object>> stakeholders = queryDirectStakeholders(facetId, objectId);
                    System.out.println("[UNISON-DEBUG][enrichWithStakeholders]   " + facetId + " ID=" + objectId + " -> " + stakeholders.size() + " stakeholders: " + stakeholders);
                    for (Map<String, Object> stakeholder : stakeholders) {
                        // Extract people ID
                        Object peopleIdObj = stakeholder.get("people_id");
                        if (peopleIdObj instanceof Integer) {
                            allPeopleIds.add((Integer) peopleIdObj);
                        } else if (peopleIdObj instanceof Number) {
                            allPeopleIds.add(((Number) peopleIdObj).intValue());
                        }

                        // Extract object_x_people ID (for role enrichment)
                        Object oxpIdObj = stakeholder.get("object_x_people_id");
                        if (oxpIdObj != null) {
                            Integer oxpId = null;
                            if (oxpIdObj instanceof Integer) {
                                oxpId = (Integer) oxpIdObj;
                                allObjectXPeopleIds.add(oxpId);
                            } else if (oxpIdObj instanceof Number) {
                                oxpId = ((Number) oxpIdObj).intValue();
                                allObjectXPeopleIds.add(oxpId);
                            }
                        }

                        // Extract org unit ID (omit for attribute-only search — show stakeholder people/roles only)
                        if (context == null || !context.isAttributePrimarySearch()) {
                            Object orgUnitIdObj = stakeholder.get("org_unit_id");
                            if (orgUnitIdObj != null) {
                                if (orgUnitIdObj instanceof Integer) {
                                    allOrgUnitIds.add((Integer) orgUnitIdObj);
                                } else if (orgUnitIdObj instanceof Number) {
                                    allOrgUnitIds.add(((Number) orgUnitIdObj).intValue());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Log but continue with other objects
                    System.err.println("[UnisonSearchService] Error querying stakeholders for " + facetId + " ID "
                            + objectId + ": " + e.getMessage());
                }
            }
        }

        // Also collect "Created By" people for each seed facet (e.g. system.CreatedBy_ID)
        for (Map.Entry<String, Set<Integer>> entry : facetToObjectIds.entrySet()) {
            String facetId = entry.getKey();
            Set<Integer> objectIds = entry.getValue();
            try {
                Set<Integer> creatorIds = queryCreatedByPeopleIdsForObjects(facetId, objectIds);
                if (!creatorIds.isEmpty()) {
                    System.out.println("[UNISON-DEBUG][enrichWithStakeholders] CreatedBy people for " + facetId + ": " + creatorIds);
                    allPeopleIds.addAll(creatorIds);
                }
            } catch (Exception e) {
                System.err.println("[UnisonSearchService] Error querying CreatedBy people for " + facetId + ": " + e.getMessage());
            }
        }

        // Add People results to accumulated results
        System.out.println("[UNISON-DEBUG][enrichWithStakeholders] Total people from stakeholders: " + allPeopleIds + " | org units: " + allOrgUnitIds);
        if (!allPeopleIds.isEmpty()) {
            String peopleFacetId = "PEOPLE";
            FacetResult existingPeopleResult = results.get(peopleFacetId);

            Set<Integer> mergedPeopleIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedPeopleDepth = new HashMap<>();

            if (existingPeopleResult != null && existingPeopleResult.getIds() != null) {
                mergedPeopleIds.addAll(existingPeopleResult.getIds());
                if (existingPeopleResult.getDepthById() != null) {
                    mergedPeopleDepth.putAll(existingPeopleResult.getDepthById());
                }
            }

            mergedPeopleIds.addAll(allPeopleIds);
            for (Integer peopleId : allPeopleIds) {
                if (!mergedPeopleDepth.containsKey(peopleId)) {
                    mergedPeopleDepth.put(peopleId, 1); // Depth 1 (direct stakeholder)
                }
            }

            // Load People rows
            List<Map<String, Object>> peopleRows = loadPeopleRows(mergedPeopleIds);
            results.put(peopleFacetId,
                    facetResultFromLoadedRows(peopleFacetId, mergedPeopleDepth, peopleRows, true));
        }

        // Add Org Unit results to accumulated results
        if (!allOrgUnitIds.isEmpty() && (context == null || !context.isAttributePrimarySearch())) {
            String orgUnitFacetId = "ORG_UNIT";
            FacetResult existingOrgUnitResult = results.get(orgUnitFacetId);

            Set<Integer> mergedOrgUnitIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedOrgUnitDepth = new HashMap<>();

            if (existingOrgUnitResult != null && existingOrgUnitResult.getIds() != null) {
                mergedOrgUnitIds.addAll(existingOrgUnitResult.getIds());
                if (existingOrgUnitResult.getDepthById() != null) {
                    mergedOrgUnitDepth.putAll(existingOrgUnitResult.getDepthById());
                }
            }

            mergedOrgUnitIds.addAll(allOrgUnitIds);
            for (Integer orgUnitId : allOrgUnitIds) {
                if (!mergedOrgUnitDepth.containsKey(orgUnitId)) {
                    mergedOrgUnitDepth.put(orgUnitId, 1); // Depth 1 (from people)
                }
            }

            // Load Org Unit rows
            List<Map<String, Object>> orgUnitRows = loadOrgUnitRows(mergedOrgUnitIds);
            results.put(orgUnitFacetId,
                    facetResultFromLoadedRows(orgUnitFacetId, mergedOrgUnitDepth, orgUnitRows, true));
        }

        // Add Roles results to accumulated results (using object_x_people IDs for precise filtering)
        if (!allObjectXPeopleIds.isEmpty()) {
            String roleFacetId = "ROLE";
            FacetResult existingRoleResult = results.get(roleFacetId);
            boolean roleIsSeedFacet = seedFacets.contains(roleFacetId);

            Set<Integer> mergedObjectXPeopleIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedRoleDepth = new HashMap<>();

            // Keep existing role IDs only when Role is itself a seed facet.
            // For object-driven searches, restrict to direct stakeholder assignments only.
            if (roleIsSeedFacet && existingRoleResult != null && existingRoleResult.getIds() != null) {
                mergedObjectXPeopleIds.addAll(existingRoleResult.getIds());
                if (existingRoleResult.getDepthById() != null) {
                    mergedRoleDepth.putAll(existingRoleResult.getDepthById());
                }
            }

            mergedObjectXPeopleIds.addAll(allObjectXPeopleIds);
            for (Integer oxpId : allObjectXPeopleIds) {
                if (!mergedRoleDepth.containsKey(oxpId)) {
                    mergedRoleDepth.put(oxpId, 1); // Depth 1 (direct role in object)
                }
            }

            // Load Role rows by object_x_people IDs (ensures only roles for the searched object)
            List<Map<String, Object>> roleRows = loadRoleRowsByObjectXPeopleIds(mergedObjectXPeopleIds);
            results.put(roleFacetId, facetResultFromLoadedRows(roleFacetId, mergedRoleDepth, roleRows, true));
        }

        // IMPORTANT: Also add org units for ALL people that appear in results (not just stakeholders)
        // This ensures that whenever people are displayed, their org units are shown too
        FacetResult peopleResult = results.get("PEOPLE");
        if ((context == null || !context.isAttributePrimarySearch()) && peopleResult != null
                && peopleResult.getIds() != null && !peopleResult.getIds().isEmpty()) {
            Set<Integer> additionalOrgUnitIds = new HashSet<>();
            for (Integer peopleId : peopleResult.getIds()) {
                try {
                    Integer orgUnitId = queryPeopleOrgUnit(peopleId);
                    if (orgUnitId != null && !allOrgUnitIds.contains(orgUnitId)) {
                        additionalOrgUnitIds.add(orgUnitId);
                    }
                } catch (Exception e) {
                    System.err.println("[UnisonSearchService] Error querying org unit for people " + peopleId + ": " + e.getMessage());
                }
            }
            
            // Add these org units to results
            if (!additionalOrgUnitIds.isEmpty()) {
                String orgUnitFacetId = "ORG_UNIT";
                FacetResult existingOrgUnitResult = results.get(orgUnitFacetId);
                
                Set<Integer> mergedOrgUnitIds = new LinkedHashSet<>();
                Map<Integer, Integer> mergedOrgUnitDepth = new HashMap<>();
                
                if (existingOrgUnitResult != null && existingOrgUnitResult.getIds() != null) {
                    mergedOrgUnitIds.addAll(existingOrgUnitResult.getIds());
                    if (existingOrgUnitResult.getDepthById() != null) {
                        mergedOrgUnitDepth.putAll(existingOrgUnitResult.getDepthById());
                    }
                }
                
                mergedOrgUnitIds.addAll(additionalOrgUnitIds);
                for (Integer orgUnitId : additionalOrgUnitIds) {
                    if (!mergedOrgUnitDepth.containsKey(orgUnitId)) {
                        mergedOrgUnitDepth.put(orgUnitId, 1); // Depth 1 (from people)
                    }
                }
                
                // Load Org Unit rows
                List<Map<String, Object>> orgUnitRows = loadOrgUnitRows(mergedOrgUnitIds);
                results.put(orgUnitFacetId,
                        facetResultFromLoadedRows(orgUnitFacetId, mergedOrgUnitDepth, orgUnitRows, true));
            }
        }

        return results;
    }

    /**
     * Query direct stakeholders for a specific object.
     * 
     * @param facetId  Facet ID (e.g., "DATASET", "SYSTEM")
     * @param objectId Object ID
     * @return List of stakeholder maps with people_id, role_id, org_unit_id
     * @throws SQLException if database error occurs
     */
    private List<Map<String, Object>> queryDirectStakeholders(String facetId, Integer objectId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        if (facetId == null || objectId == null) {
            return stakeholders;
        }

        // Get linking table configuration
        StakeholderTableConfig config = getStakeholderTableConfig(facetId);
        if (config == null) {
            return stakeholders;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Handle different column name patterns (Process uses lowercase, others use
            // mixed case)
            String joinColumn = "oxp.ID";
            if ("process_x_objectxpeople".equals(config.linkingTable)) {
                joinColumn = "oxp.id"; // Process uses lowercase
            }

            String sql = "SELECT DISTINCT " +
                    "oxp.ID AS object_x_people_id, " +
                    "oxp.ipid AS people_id, " +
                    "oxp.RoleID AS role_id, " +
                    "p.Org_Unit_ID AS org_unit_id " +
                    "FROM " + config.linkingTable + " lx " +
                    "JOIN object_x_people oxp ON lx." + config.linkingColumn + " = " + joinColumn + " " +
                    "JOIN people p ON oxp.ipid = p.ID " +
                    "WHERE lx." + config.objectIdColumn + " = ? " +
                    "AND p.Deleted_date IS NULL";

            List<Object> params = List.of(objectId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            stakeholders.addAll(results);
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying stakeholders for " + facetId + " ID " + objectId
                    + ": " + e.getMessage());
            e.printStackTrace();
        }

        return stakeholders;
    }

    /**
     * Returns the set of people IDs found in the "Created By" column of the given facet's table
     * for the provided object IDs. Only returns non-null, non-deleted people.
     */
    private Set<Integer> queryCreatedByPeopleIdsForObjects(String facetId, Set<Integer> objectIds) {
        if (facetId == null || objectIds == null || objectIds.isEmpty()) {
            return new HashSet<>();
        }
        String canonical = canonFacet(facetId);
        if (canonical == null) {
            canonical = normalizedFacetToFacetId(normalizeFacetName(facetId));
        }
        String createdByCol = getCreatedByDatabaseColumn(canonical);
        if (createdByCol == null) {
            return new HashSet<>();
        }
        String moduleName = facetIdToModuleName(canonical);
        if (moduleName == null) {
            return new HashSet<>();
        }
        String table = getTableNameForModule(moduleName);
        if (table == null || table.isBlank()) {
            return new HashSet<>();
        }
        String pkCol = getEntityPrimaryKeyColumn(canonical);
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper =
                    new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(objectIds.size(), "?"));
            // JOIN people to exclude deleted creators
            String sql = "SELECT DISTINCT t.`" + createdByCol + "` AS creator_id" +
                    " FROM `" + table + "` t" +
                    " INNER JOIN people p ON t.`" + createdByCol + "` = p.ID" +
                    " WHERE t.`" + pkCol + "` IN (" + placeholders + ")" +
                    " AND t.`" + createdByCol + "` IS NOT NULL" +
                    " AND p.Deleted_date IS NULL";
            List<Object> params = new ArrayList<>(objectIds);
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, params);
            return extractIntColumnFromRows(rows, "creator_id");
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] queryCreatedByPeopleIdsForObjects " + facetId + ": " + e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * Configuration for stakeholder linking tables.
     */
    private static class StakeholderTableConfig {
        String linkingTable;
        String linkingColumn;
        String objectIdColumn;

        StakeholderTableConfig(String linkingTable, String linkingColumn, String objectIdColumn) {
            this.linkingTable = linkingTable;
            this.linkingColumn = linkingColumn;
            this.objectIdColumn = objectIdColumn;
        }
    }

    /**
     * Get stakeholder table configuration for a facet type.
     * 
     * @param facetId Facet ID (e.g., "DATASET", "SYSTEM")
     * @return StakeholderTableConfig or null if not supported
     */
    private StakeholderTableConfig getStakeholderTableConfig(String facetId) {
        if (facetId == null) {
            return null;
        }

        String normalized = facetId.trim().toUpperCase().replace("-", "_");
        return switch (normalized) {
            case "DATASET", "DATA_SETS", "DATASETS" ->
                new StakeholderTableConfig("dataset_x_objectxpeople", "Object_x_ipid", "Dataset_ID");
            case "SYSTEM", "SYSTEMS" ->
                new StakeholderTableConfig("system_x_objectxpeople", "Object_x_ipid", "SystemID");
            case "GLOSSARY", "GLOSSARIES" ->
                new StakeholderTableConfig("glossary_x_objectxpeople", "Object_x_ipid", "GlossaryID");
            case "INTERFACE", "INTERFACES" ->
                new StakeholderTableConfig("interface_x_objectxpeople", "Object_x_ipid", "InterfaceID");
            case "PROCESS", "PROCESSES" ->
                new StakeholderTableConfig("process_x_objectxpeople", "object_x_ip", "process_id");
            case "ATTRIBUTE", "ATTRIBUTES" ->
                new StakeholderTableConfig("attribute_x_objectxpeople", "Object_x_ipid", "AttributeID");
            case "PROJECT", "PROJECTS" ->
                new StakeholderTableConfig("project_x_objectxpeople", "object_x_ip", "project_id");
            case "PRODUCT", "PRODUCTS" ->
                new StakeholderTableConfig("product_x_objectxpeople", "object_x_ip", "product_id");
            case "POLICY", "POLICIES" ->
                new StakeholderTableConfig("policy_x_objectxpeople", "Object_X_IP", "Policy_ID");
            case "BUSINESS_AREA", "BUSINESSAREA" ->
                new StakeholderTableConfig("businessarea_x_objectxpeople", "Object_x_ipid", "BusinessAreaID");
            case "LEGAL_ENTITY", "LEGALENTITY" ->
                new StakeholderTableConfig("legal_x_objectxpeople", "Object_X_ip", "Legal_ID");
            case "CLIENT", "CLIENTS" ->
                new StakeholderTableConfig("client_x_objectxpeople", "Object_x_ipid", "ClientID");
            case "COMMITTEE", "COMMITTEES" ->
                new StakeholderTableConfig("committee_x_objectxpeople", "Object_X_ipid", "Committee_ID");
            case "ORG_UNIT", "ORGUNIT", "ORG_UNITS" ->
                new StakeholderTableConfig("orgunit_x_objectxpeople", "Object_x_ipid", "OrgUnitID");
            case "GEOGRAPHY", "GEOGRAPHIES" ->
                new StakeholderTableConfig("geography_x_objectxpeople", "Object_x_ipid", "GeographyID");
            case "REGULATION", "REGULATIONS" ->
                new StakeholderTableConfig("regulation_x_objectxpeople", "Object_x_ipid", "RegulationID");
            case "REGULATOR", "REGULATORS" ->
                new StakeholderTableConfig("regulator_x_objectxpeople", "Object_x_ipid", "RegulatorID");
            case "REGULATORY_THEME", "REGULATORYTHEME" ->
                null; // RegulatoryTheme doesn't have a stakeholder table
            case "CAPABILITY", "CAPABILITIES" ->
                new StakeholderTableConfig("capability_x_objectxpeople", "Object_x_ipid", "CapabilityID");
            default -> null;
        };
    }

    /**
     * Load People rows for given people IDs.
     * 
     * @param peopleIds Set of people IDs
     * @return List of people row maps
     */
    private List<Map<String, Object>> loadPeopleRows(Set<Integer> peopleIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (peopleIds == null || peopleIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(peopleIds.size(), "?"));
            // Match QueryBuilder.getSqlForModule("people") column names/aliases so Unison
            // related-People tables show the same cells as the People facet (not underscore-only keys).
            String sql = """
                SELECT
                    p.ID AS ID,
                    p.First_Name AS 'First Name',
                    p.ID AS 'First Name_ID',
                    p.Last_Name AS 'Last Name',
                    p.ID AS 'Last Name_ID',
                    p.Email AS Email,
                    p.Function_Name AS `Function`,
                    p.Org_Unit_ID AS Org_Unit_ID,
                    ou.Reference AS 'Org Unit Ref',
                    ou.Name AS 'Org Unit',
                    ou.ID AS 'Org Unit_ID',
                    s.PrimaryName AS 'BUDG Status',
                    r.primaryname AS 'Profile Name',
                    p.System_Role AS System_Role,
                    p.last_User_LogIn AS 'Last Login',
                    ls.Primary_Name AS Lifecycle,
                    et.primary_Name AS 'Employee Type',
                    pd.lan_id AS 'LAN ID',
                    p.Created_Date AS 'Created Date',
                    p.Last_Updated AS 'Last Updated'
                FROM people p
                LEFT JOIN status s ON p.status_id = s.ID
                LEFT JOIN people_details pd ON p.ip_details = pd.id
                LEFT JOIN employment_type et ON pd.employment_type = et.id
                LEFT JOIN role r ON p.System_Role = r.id
                LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
                LEFT JOIN people_lifecycle_status ls ON pd.lifecycle = ls.ID
                """
                    + "WHERE p.ID IN (" + placeholders + ") " +
                "AND p.Deleted_date IS NULL";

            List<Object> params = new ArrayList<>(peopleIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> peopleRow = new HashMap<>(row);
                Object id = peopleRow.get("ID");
                if (id != null) {
                    peopleRow.put("id", id);
                }
                // Backward compatibility for callers expecting snake_case / old keys
                Object firstName = peopleRow.get("First Name");
                if (firstName == null) {
                    firstName = peopleRow.get("First_Name");
                }
                peopleRow.put("First_Name", firstName);
                peopleRow.put("first_name", firstName);

                Object lastName = peopleRow.get("Last Name");
                if (lastName == null) {
                    lastName = peopleRow.get("Last_Name");
                }
                peopleRow.put("Last_Name", lastName);
                peopleRow.put("last_name", lastName);

                Object email = peopleRow.get("Email");
                peopleRow.put("email", email);

                Object fn = peopleRow.get("Function");
                if (fn == null) {
                    fn = peopleRow.get("Function_Name");
                }
                peopleRow.put("Function_Name", fn);
                peopleRow.put("function_name", fn);

                if (peopleRow.get("Org_Unit_ID") == null) {
                    peopleRow.put("Org_Unit_ID", peopleRow.get("Org Unit_ID"));
                }
                peopleRow.put("org_unit_id", peopleRow.get("Org_Unit_ID"));

                Object ouName = peopleRow.get("Org Unit");
                peopleRow.put("org_unit", ouName);
                Object ouRef = peopleRow.get("Org Unit Ref");
                peopleRow.put("org_unit_ref", ouRef);
                rows.add(peopleRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadPeopleRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Org Unit rows for given org unit IDs.
     * 
     * @param orgUnitIds Set of org unit IDs
     * @return List of org unit row maps
     */
    private List<Map<String, Object>> loadOrgUnitRows(Set<Integer> orgUnitIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (orgUnitIds == null || orgUnitIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(orgUnitIds.size(), "?"));
            // Align with QueryBuilder org_unit list query so Unison table columns match main search
            String sql = "SELECT ou.ID AS ID, "
                    + "ou.Reference AS `Ref.`, "
                    + "ou.Name AS Name, "
                    + "ou.ID AS Name_ID, "
                    + "COALESCE(pr.Name, 'Top Level') AS Parent, "
                    + "pr.ID AS Parent_ID, "
                    + "ou.Description AS Description, "
                    + "s.PrimaryName AS `BUDG Status`, "
                    + "ou.Created_Date AS `Created Date`, "
                    + "ou.last_updated_date AS `Last Updated` "
                    + "FROM org_unit ou "
                    + "LEFT JOIN status s ON ou.status_id = s.ID "
                    + "LEFT JOIN org_unit pr ON ou.Parent_ID = pr.ID "
                    + "AND (pr.deleted_Date IS NULL OR pr.deleted_Date = '') "
                    + "WHERE ou.ID IN (" + placeholders + ") "
                    + "AND ou.deleted_Date IS NULL";

            List<Object> params = new ArrayList<>(orgUnitIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> orgUnitRow = new LinkedHashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    orgUnitRow.put("ID", id);
                    orgUnitRow.put("id", id);
                }
                orgUnitRow.put("Ref.", row.get("Ref."));
                orgUnitRow.put("Name", row.get("Name"));
                orgUnitRow.put("name", row.get("Name"));
                orgUnitRow.put("Name_ID", row.get("Name_ID"));
                orgUnitRow.put("Parent", row.get("Parent"));
                orgUnitRow.put("parent", row.get("Parent"));
                orgUnitRow.put("Parent_ID", row.get("Parent_ID"));
                orgUnitRow.put("parent_id", row.get("Parent_ID"));
                orgUnitRow.put("Description", row.get("Description"));
                orgUnitRow.put("description", row.get("Description"));
                orgUnitRow.put("BUDG Status", row.get("BUDG Status"));
                orgUnitRow.put("Created Date", row.get("Created Date"));
                orgUnitRow.put("Last Updated", row.get("Last Updated"));
                rows.add(orgUnitRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadOrgUnitRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Role rows for given role IDs.
     * 
     * @param roleIds Set of role IDs
     * @return List of role row maps
     */
    private List<Map<String, Object>> loadRoleRows(Set<Integer> roleIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
            
            // Comprehensive query with stakeholder data (matches buildOptimizedRoleQuery)
            String sql = "SELECT \n" +
                    "    oxp.ID AS ID,\n" +
                    "    orl.id AS role_id,\n" +
                    "    orl.primaryname AS primaryname,\n" +
                    "    ort.primaryname AS role_type,\n" +
                    "    orl.description AS description,\n" +
                    "    orl.module AS module,\n" +
                    "    orl.defaultrole AS defaultrole,\n" +
                    "    orl.objectroletype_id AS objectroletype_id,\n" +
                    "    CONCAT(p.First_Name, ' ', p.Last_Name) AS full_name,\n" +
                    "    p.ID AS person_id,\n" +
                    "    m.PrimaryName AS object_type,\n" +
                    "    COALESCE(\n" +
                    "        (SELECT g.Name FROM glossary_x_objectxpeople gxo JOIN glossary g ON gxo.GlossaryID = g.ID WHERE gxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT d.PrimaryName FROM dataset_x_objectxpeople dxo JOIN dataset d ON dxo.Dataset_ID = d.ID WHERE dxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT pr.PrimaryName FROM process_x_objectxpeople pxo JOIN process pr ON pxo.process_id = pr.id WHERE pxo.object_x_ip = oxp.ID LIMIT 1),\n" +
                    "        (SELECT s.Name FROM system_x_objectxpeople sxo JOIN system s ON sxo.SystemID = s.id WHERE sxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT prod.PrimaryName FROM product_x_objectxpeople proxo JOIN product prod ON proxo.product_id = prod.id WHERE proxo.object_x_ip = oxp.ID LIMIT 1),\n" +
                    "        (SELECT pj.PrimaryName FROM project_x_objectxpeople pjxo JOIN project pj ON pjxo.project_id = pj.id WHERE pjxo.object_x_ip = oxp.ID LIMIT 1),\n" +
                    "        (SELECT a.PrimaryName FROM attribute_x_objectxpeople axo JOIN attribute a ON axo.AttributeID = a.ID WHERE axo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT po.PrimaryName FROM policy_x_objectxpeople pox JOIN policy po ON pox.Policy_ID = po.ID WHERE pox.Object_X_IP = oxp.ID LIMIT 1),\n" +
                    "        (SELECT si.Name FROM interface_x_objectxpeople sixo JOIN interface si ON sixo.InterfaceID = si.id WHERE sixo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT reg.primaryName FROM regulation_x_objectxpeople regxo JOIN regulation reg ON regxo.RegulationID = reg.ID WHERE regxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT com.PrimaryName FROM committee_x_objectxpeople comxo JOIN committee com ON comxo.Committee_ID = com.ID WHERE comxo.Object_X_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT cl.PrimaryName FROM client_x_objectxpeople clxo JOIN client cl ON clxo.ClientID = cl.ID WHERE clxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT le.ShortName FROM legal_x_objectxpeople lexo JOIN legal le ON lexo.Legal_ID = le.ID WHERE lexo.Object_X_IP = oxp.ID LIMIT 1),\n" +
                    "        (SELECT ba.PrimaryName FROM businessarea_x_objectxpeople baxo JOIN business_area ba ON baxo.BusinessAreaID = ba.ID WHERE baxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT cap.PrimaryName FROM capability_x_objectxpeople capxo JOIN capability cap ON capxo.CapabilityID = cap.ID WHERE capxo.Object_x_ipid = oxp.ID LIMIT 1)\n" +
                    "    ) AS object_name,\n" +
                    "    CASE \n" +
                    "        WHEN oxp.AcceptedID IS NOT NULL THEN 'yes'\n" +
                    "        ELSE 'no'\n" +
                    "    END AS role_accepted,\n" +
                    "    ra.CreateDatetime AS date_accepted\n" +
                    "FROM object_x_people oxp\n" +
                    "JOIN object_role orl ON oxp.RoleID = orl.id\n" +
                    "LEFT JOIN object_role_type ort ON orl.objectroletype_id = ort.id\n" +
                    "LEFT JOIN people p ON oxp.ipid = p.ID\n" +
                    "LEFT JOIN module m ON orl.module = m.ID\n" +
                    "LEFT JOIN roleaccepted ra ON oxp.AcceptedID = ra.ID\n" +
                    "WHERE oxp.RoleID IN (" + placeholders + ")\n" +
                    "  AND (p.Deleted_date IS NULL OR p.ID IS NULL)\n" +
                    "ORDER BY oxp.ID DESC";

            List<Object> params = new ArrayList<>(roleIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> roleRow = new HashMap<>();
                
                // ID (object_x_people ID)
                Object id = row.get("ID");
                if (id != null) {
                    roleRow.put("ID", id);
                    roleRow.put("id", id);
                }
                
                // Role ID
                Object roleId = row.get("role_id");
                roleRow.put("role_id", roleId);
                
                // Role name
                Object primaryName = row.get("primaryname");
                roleRow.put("PrimaryName", primaryName);
                roleRow.put("primaryname", primaryName);
                roleRow.put("Role", primaryName);
                roleRow.put("role", primaryName);
                
                // Role type
                Object roleType = row.get("role_type");
                roleRow.put("Role type", roleType);
                roleRow.put("role_type", roleType);
                
                // Description
                Object description = row.get("description");
                roleRow.put("Description", description);
                roleRow.put("description", description);
                
                // Module
                Object module = row.get("module");
                roleRow.put("Module", module);
                roleRow.put("module", module);
                
                // Default role
                Object defaultRole = row.get("defaultrole");
                roleRow.put("DefaultRole", defaultRole);
                roleRow.put("defaultrole", defaultRole);
                
                // Object role type ID
                Object roleTypeId = row.get("objectroletype_id");
                roleRow.put("ObjectRoleType_ID", roleTypeId);
                roleRow.put("objectroletype_id", roleTypeId);
                
                // Full Name (person name)
                Object fullName = row.get("full_name");
                roleRow.put("Full Name", fullName);
                roleRow.put("full_name", fullName);
                
                // Person ID
                Object personId = row.get("person_id");
                roleRow.put("Full Name_ID", personId);
                roleRow.put("person_id", personId);
                
                // Object Type (facet name)
                Object objectType = row.get("object_type");
                roleRow.put("Object Type", objectType);
                roleRow.put("object_type", objectType);
                
                // Object name
                Object objectName = row.get("object_name");
                roleRow.put("Object", objectName);
                roleRow.put("object_name", objectName);
                
                // Role Accepted (yes/no)
                Object roleAccepted = row.get("role_accepted");
                roleRow.put("Role Accepted", roleAccepted);
                roleRow.put("role_accepted", roleAccepted);
                
                // Date Accepted
                Object dateAccepted = row.get("date_accepted");
                roleRow.put("Date Accepted", dateAccepted);
                roleRow.put("date_accepted", dateAccepted);
                
                rows.add(roleRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadRoleRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load role rows by object_x_people IDs (for object enrichment).
     * Returns only the specific role assignments identified by object_x_people IDs.
     * This is used when displaying roles for a specific object search result.
     */
    private List<Map<String, Object>> loadRoleRowsByObjectXPeopleIds(Set<Integer> objectXPeopleIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (objectXPeopleIds == null || objectXPeopleIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(objectXPeopleIds.size(), "?"));
            
            // Comprehensive query - same as loadRoleRows but filter by oxp.ID instead of oxp.RoleID
            String sql = "SELECT \n" +
                    "    oxp.ID AS ID,\n" +
                    "    orl.id AS role_id,\n" +
                    "    orl.primaryname AS primaryname,\n" +
                    "    ort.primaryname AS role_type,\n" +
                    "    orl.description AS description,\n" +
                    "    orl.module AS module,\n" +
                    "    orl.defaultrole AS defaultrole,\n" +
                    "    orl.objectroletype_id AS objectroletype_id,\n" +
                    "    CONCAT(p.First_Name, ' ', p.Last_Name) AS full_name,\n" +
                    "    p.ID AS person_id,\n" +
                    "    m.PrimaryName AS object_type,\n" +
                    "    COALESCE(\n" +
                    "        (SELECT g.Name FROM glossary_x_objectxpeople gxo JOIN glossary g ON gxo.GlossaryID = g.ID WHERE gxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT d.PrimaryName FROM dataset_x_objectxpeople dxo JOIN dataset d ON dxo.Dataset_ID = d.ID WHERE dxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT pr.PrimaryName FROM process_x_objectxpeople pxo JOIN process pr ON pxo.process_id = pr.id WHERE pxo.object_x_ip = oxp.ID LIMIT 1),\n" +
                    "        (SELECT s.Name FROM system_x_objectxpeople sxo JOIN system s ON sxo.SystemID = s.id WHERE sxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT prod.PrimaryName FROM product_x_objectxpeople proxo JOIN product prod ON proxo.product_id = prod.id WHERE proxo.object_x_ip = oxp.ID LIMIT 1),\n" +
                    "        (SELECT pj.PrimaryName FROM project_x_objectxpeople pjxo JOIN project pj ON pjxo.project_id = pj.id WHERE pjxo.object_x_ip = oxp.ID LIMIT 1),\n" +
                    "        (SELECT a.PrimaryName FROM attribute_x_objectxpeople axo JOIN attribute a ON axo.AttributeID = a.ID WHERE axo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT po.PrimaryName FROM policy_x_objectxpeople pox JOIN policy po ON pox.Policy_ID = po.ID WHERE pox.Object_X_IP = oxp.ID LIMIT 1),\n" +
                    "        (SELECT si.Name FROM interface_x_objectxpeople sixo JOIN interface si ON sixo.InterfaceID = si.id WHERE sixo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT reg.primaryName FROM regulation_x_objectxpeople regxo JOIN regulation reg ON regxo.RegulationID = reg.ID WHERE regxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT com.PrimaryName FROM committee_x_objectxpeople comxo JOIN committee com ON comxo.Committee_ID = com.ID WHERE comxo.Object_X_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT cl.PrimaryName FROM client_x_objectxpeople clxo JOIN client cl ON clxo.ClientID = cl.ID WHERE clxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT le.ShortName FROM legal_x_objectxpeople lexo JOIN legal le ON lexo.Legal_ID = le.ID WHERE lexo.Object_X_IP = oxp.ID LIMIT 1),\n" +
                    "        (SELECT ba.PrimaryName FROM businessarea_x_objectxpeople baxo JOIN business_area ba ON baxo.BusinessAreaID = ba.ID WHERE baxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
                    "        (SELECT cap.PrimaryName FROM capability_x_objectxpeople capxo JOIN capability cap ON capxo.CapabilityID = cap.ID WHERE capxo.Object_x_ipid = oxp.ID LIMIT 1)\n" +
                    "    ) AS object_name,\n" +
                    "    CASE \n" +
                    "        WHEN oxp.AcceptedID IS NOT NULL THEN 'yes'\n" +
                    "        ELSE 'no'\n" +
                    "    END AS role_accepted,\n" +
                    "    ra.CreateDatetime AS date_accepted\n" +
                    "FROM object_x_people oxp\n" +
                    "JOIN object_role orl ON oxp.RoleID = orl.id\n" +
                    "LEFT JOIN object_role_type ort ON orl.objectroletype_id = ort.id\n" +
                    "LEFT JOIN people p ON oxp.ipid = p.ID\n" +
                    "LEFT JOIN module m ON orl.module = m.ID\n" +
                    "LEFT JOIN roleaccepted ra ON oxp.AcceptedID = ra.ID\n" +
                    "WHERE oxp.ID IN (" + placeholders + ")\n" +
                    "  AND (p.Deleted_date IS NULL OR p.ID IS NULL)\n" +
                    "ORDER BY oxp.ID DESC";

            List<Object> params = new ArrayList<>(objectXPeopleIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> roleRow = new HashMap<>();
                
                // ID (object_x_people ID)
                Object id = row.get("ID");
                if (id != null) {
                    roleRow.put("ID", id);
                    roleRow.put("id", id);
                }
                
                // Role ID
                Object roleId = row.get("role_id");
                roleRow.put("role_id", roleId);
                
                // Role name
                Object primaryName = row.get("primaryname");
                roleRow.put("PrimaryName", primaryName);
                roleRow.put("primaryname", primaryName);
                roleRow.put("Role", primaryName);
                roleRow.put("role", primaryName);
                
                // Role type
                Object roleType = row.get("role_type");
                roleRow.put("Role type", roleType);
                roleRow.put("role_type", roleType);
                
                // Description
                Object description = row.get("description");
                roleRow.put("Description", description);
                roleRow.put("description", description);
                
                // Module
                Object module = row.get("module");
                roleRow.put("Module", module);
                roleRow.put("module", module);
                
                // Default role
                Object defaultRole = row.get("defaultrole");
                roleRow.put("DefaultRole", defaultRole);
                roleRow.put("defaultrole", defaultRole);
                
                // Object role type ID
                Object roleTypeId = row.get("objectroletype_id");
                roleRow.put("ObjectRoleType_ID", roleTypeId);
                roleRow.put("objectroletype_id", roleTypeId);
                
                // Full Name (person name)
                Object fullName = row.get("full_name");
                roleRow.put("Full Name", fullName);
                roleRow.put("full_name", fullName);
                
                // Person ID
                Object personId = row.get("person_id");
                roleRow.put("Full Name_ID", personId);
                roleRow.put("person_id", personId);
                
                // Object Type (facet name)
                Object objectType = row.get("object_type");
                roleRow.put("Object Type", objectType);
                roleRow.put("object_type", objectType);
                
                // Object name
                Object objectName = row.get("object_name");
                roleRow.put("Object", objectName);
                roleRow.put("object_name", objectName);
                
                // Role Accepted (yes/no)
                Object roleAccepted = row.get("role_accepted");
                roleRow.put("Role Accepted", roleAccepted);
                roleRow.put("role_accepted", roleAccepted);
                
                // Date Accepted
                Object dateAccepted = row.get("date_accepted");
                roleRow.put("Date Accepted", dateAccepted);
                roleRow.put("date_accepted", dateAccepted);
                
                rows.add(roleRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadRoleRowsByObjectXPeopleIds: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Query Change Requests by reference strings.
     * 
     * @param references Set of reference strings (e.g., "Dataset 123", "System
     *                   456")
     * @return Set of CR IDs
     * @throws SQLException if database error occurs
     */
    private Set<Integer> queryChangeRequestsByReferences(Set<String> references) throws SQLException {
        Set<Integer> crIds = new HashSet<>();
        if (references == null || references.isEmpty()) {
            return crIds;
        }

        try {
            com.example.budg_v2.dao.ChangeRequestDAO crDAO = new com.example.budg_v2.dao.ChangeRequestDAO();

            // Query each reference
            for (String reference : references) {
                try {
                    List<com.example.budg_v2.model.ChangeRequest> crs = crDAO.getChangeRequestsByReference(reference);
                    for (com.example.budg_v2.model.ChangeRequest cr : crs) {
                        if (cr != null && cr.getId() != null) {
                            crIds.add(cr.getId());
                        }
                    }
                } catch (Exception e) {
                    // Log but continue with other references
                    System.err.println("[UnisonSearchService] Error querying CRs for reference " + reference + ": "
                            + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in queryChangeRequestsByReferences: " + e.getMessage());
            e.printStackTrace();
        }

        return crIds;
    }

    /**
     * Query Active Tasks by Change Request IDs.
     * 
     * @param crIds Set of Change Request IDs
     * @return Set of Active Task IDs
     * @throws SQLException if database error occurs
     */
    private Set<Integer> queryActiveTasksByCRIds(Set<Integer> crIds) throws SQLException {
        Set<Integer> taskIds = new HashSet<>();
        if (crIds == null || crIds.isEmpty()) {
            return taskIds;
        }
        
        // Only return tasks for the current user
        if (currentUserId == null || currentUserId <= 0) {
            return taskIds;
        }

        try {
            // Use WorkflowTaskDAO to get user-specific tasks, then filter by CR IDs
            com.example.budg_v2.dao.WorkflowTaskDAO taskDAO = new com.example.budg_v2.dao.WorkflowTaskDAO();
            List<Map<String, Object>> userTasks = taskDAO.findActiveTasksForUser(currentUserId);
            
            // Filter to only tasks for the specified CRs
            for (Map<String, Object> task : userTasks) {
                Object changeRequestIdObj = task.get("changeRequestId");
                if (changeRequestIdObj != null) {
                    int changeRequestId;
                    if (changeRequestIdObj instanceof Integer) {
                        changeRequestId = (Integer) changeRequestIdObj;
                    } else if (changeRequestIdObj instanceof Number) {
                        changeRequestId = ((Number) changeRequestIdObj).intValue();
                    } else {
                        continue;
                    }
                    
                    if (crIds.contains(changeRequestId)) {
                        Object taskIdObj = task.get("taskId");
                        if (taskIdObj instanceof Integer) {
                            taskIds.add((Integer) taskIdObj);
                        } else if (taskIdObj instanceof Number) {
                            taskIds.add(((Number) taskIdObj).intValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in queryActiveTasksByCRIds: " + e.getMessage());
            e.printStackTrace();
        }

        return taskIds;
    }

    /**
     * Load Change Request rows for given CR IDs.
     * 
     * @param crIds Set of CR IDs
     * @return List of CR row maps
     */
    private List<Map<String, Object>> loadChangeRequestRows(Set<Integer> crIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (crIds == null || crIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.budg_v2.dao.ChangeRequestDAO crDAO = new com.example.budg_v2.dao.ChangeRequestDAO();

            for (Integer crId : crIds) {
                try {
                    com.example.budg_v2.model.ChangeRequest cr = crDAO.getChangeRequestById(crId);
                    if (cr != null) {
                        Map<String, Object> row = new HashMap<>();
                        
                        // Basic fields
                        row.put("ID", cr.getId());
                        row.put("Id", cr.getId()); // UI table expects "Id"
                        row.put("id", cr.getId());
                        
                        // Subject field (for display name and linking)
                        row.put("Subject", cr.getPrimaryName());
                        row.put("subject", cr.getPrimaryName());
                        row.put("Subject_ID", cr.getId());
                        row.put("subject_id", cr.getId());
                        
                        // Keep PrimaryName for backward compatibility
                        row.put("PrimaryName", cr.getPrimaryName());
                        row.put("primaryname", cr.getPrimaryName());
                        
                        // Reference
                        row.put("Reference", cr.getReference());
                        row.put("reference", cr.getReference());
                        
                        // Summary
                        row.put("Summary", cr.getSummary());
                        row.put("summary", cr.getSummary());
                        
                        // Type (loaded by DAO)
                        row.put("Type", cr.getTypeName());
                        row.put("type", cr.getTypeName());
                        
                        // Status (loaded by DAO)
                        row.put("Status", cr.getStatusName());
                        row.put("status", cr.getStatusName());
                        row.put("CR_StatusID", cr.getCrStatusId());
                        row.put("crStatusId", cr.getCrStatusId());
                        
                        // Severity (loaded by DAO)
                        row.put("Severity", cr.getSeverityName());
                        row.put("severity", cr.getSeverityName());
                        
                        // Urgency (loaded by DAO)
                        row.put("Urgency", cr.getUrgencyName());
                        row.put("urgency", cr.getUrgencyName());

                        String createdByName = cr.getCreatedByName() != null ? cr.getCreatedByName() : "";
                        row.put("createdByName", createdByName);
                        row.put("Created By", createdByName);
                        if (cr.getCreatedBy() != null) {
                            row.put("createdBy", cr.getCreatedBy());
                            row.put("Created By_ID", cr.getCreatedBy());
                        }
                        row.put("createdAt", cr.getCreatedAt());
                        row.put("updatedAt", cr.getUpdatedAt());
                        
                        // Dates
                        row.put("Created Date", cr.getCreatedAt());
                        row.put("Create Date", cr.getCreatedAt()); // UI table expects "Create Date"
                        row.put("created_date", cr.getCreatedAt());
                        row.put("Last Updated Date", cr.getUpdatedAt());
                        row.put("Last Update Date", cr.getUpdatedAt()); // UI table expects "Last Update Date"
                        row.put("last_updated_date", cr.getUpdatedAt());
                        
                        // Parse Reference to extract Object Type and Object ID
                        // Format: "FacetType FacetId" e.g. "Data Set 155" or "System 20"
                        // The ID is always the last token; everything before it is the type.
                        String reference = cr.getReference();
                        if (reference != null && !reference.isEmpty()) {
                            String[] parts = reference.trim().split("\\s+");
                            if (parts.length >= 2) {
                                String objectIdStr = parts[parts.length - 1];
                                // Object type = all tokens except the last (ID)
                                String objectType = String.join(" ",
                                        java.util.Arrays.copyOf(parts, parts.length - 1));
                                try {
                                    Integer objectId = Integer.parseInt(objectIdStr);
                                    row.put("Object Type", objectType);
                                    row.put("object_type", objectType);
                                    row.put("Object_ID", objectId);
                                    row.put("object_id", objectId);
                                    // Fetch actual object name for display
                                    String objectName = fetchObjectName(objectType, objectId);
                                    String displayName = (objectName != null && !objectName.isBlank())
                                            ? objectName : reference;
                                    row.put("Object", displayName);
                                    row.put("object", displayName);
                                    String segDisplay = fetchObjectSegmentDisplayName(objectType, objectId);
                                    row.put("Segment", segDisplay);
                                    row.put("Segments", segDisplay);
                                    row.put("segment", segDisplay);
                                } catch (NumberFormatException e) {
                                    row.put("Object", reference);
                                    row.put("object", reference);
                                    row.put("Segment", "Not Assigned");
                                    row.put("Segments", "Not Assigned");
                                    row.put("segment", "Not Assigned");
                                }
                            } else {
                                row.put("Object", reference);
                                row.put("object", reference);
                                row.put("Segment", "Not Assigned");
                                row.put("Segments", "Not Assigned");
                                row.put("segment", "Not Assigned");
                            }
                        } else {
                            row.put("Object", "");
                            row.put("object", "");
                            row.put("Segment", "Not Assigned");
                            row.put("Segments", "Not Assigned");
                            row.put("segment", "Not Assigned");
                        }

                        rows.add(row);
                    }
                } catch (Exception e) {
                    // Log but continue
                    System.err.println("[UnisonSearchService] Error loading CR " + crId + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadChangeRequestRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Fetch the display name of an object given its type string (as stored in CR Reference)
     * and its integer ID.  Returns null if the object cannot be found or the type is unknown.
     */
    private String fetchObjectName(String objectType, int objectId) {
        if (objectType == null || objectType.isBlank()) return null;
        String sql;
        switch (objectType.toLowerCase().trim()) {
            case "dataset", "data set", "data sets" ->
                sql = "SELECT PrimaryName AS name FROM dataset WHERE ID = ?";
            case "system", "systems" ->
                sql = "SELECT Name AS name FROM system WHERE id = ?";
            case "attribute", "attributes" ->
                sql = "SELECT PrimaryName AS name FROM attribute WHERE ID = ?";
            case "glossary", "glossaries" ->
                sql = "SELECT Name AS name FROM glossary WHERE ID = ?";
            case "policy", "policies" ->
                sql = "SELECT PrimaryName AS name FROM policy WHERE ID = ?";
            case "process", "processes" ->
                sql = "SELECT primaryname AS name FROM process WHERE id = ?";
            case "project", "projects" ->
                sql = "SELECT primaryname AS name FROM project WHERE id = ?";
            case "product", "products" ->
                sql = "SELECT primaryname AS name FROM product WHERE id = ?";
            case "capability", "capabilities" ->
                sql = "SELECT PrimaryName AS name FROM capability WHERE ID = ?";
            case "interface", "interfaces" ->
                sql = "SELECT Name AS name FROM interface WHERE id = ?";
            case "people", "person" ->
                sql = "SELECT CONCAT(First_Name, ' ', Last_Name) AS name FROM people WHERE ID = ?";
            case "org unit", "orgunit", "org_unit" ->
                sql = "SELECT Name AS name FROM org_unit WHERE ID = ? "
                        + "AND (deleted_Date IS NULL OR deleted_Date = '')";
            case "business area", "businessarea", "business_area" ->
                sql = "SELECT PrimaryName AS name FROM business_area WHERE ID = ?";
            case "legal entity", "legalentity", "legal_entity" ->
                sql = "SELECT ShortName AS name FROM legal_entity WHERE ID = ?";
            case "regulation", "regulations" ->
                sql = "SELECT primaryName AS name FROM regulation WHERE ID = ?";
            case "committee", "committees" ->
                sql = "SELECT PrimaryName AS name FROM committee WHERE ID = ?";
            default -> { return null; }
        }
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (Exception e) {
            // Non-critical — fall back to reference string
        }
        return null;
    }

    private String fetchSegmentNameById(int segmentId) {
        if (segmentId <= 0) {
            return null;
        }
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT Name FROM segment WHERE ID = ? AND Deleted_At IS NULL")) {
            ps.setInt(1, segmentId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        } catch (Exception e) {
            // non-critical
        }
        return null;
    }

    /**
     * Map CR Reference object-type text to segment_object_type.Type values to try with SegmentDAO.
     */
    private List<String> buildCrReferenceSegmentTypes(String objectType) {
        java.util.LinkedHashSet<String> types = new java.util.LinkedHashSet<>();
        if (objectType == null || objectType.isBlank()) {
            return new ArrayList<>(types);
        }
        String compact = objectType.trim().toLowerCase().replace(" ", "").replace("_", "").replace("-", "");
        switch (compact) {
            case "dataset", "datasets" -> {
                types.add("Dataset");
                types.add("Data Set");
                types.add("Data Sets");
            }
            case "system", "systems" -> types.add("System");
            case "attribute", "attributes" -> types.add("Attribute");
            case "glossary", "glossaries" -> types.add("Glossary");
            case "policy", "policies" -> types.add("Policy");
            case "process", "processes" -> types.add("Process");
            case "project", "projects" -> types.add("Project");
            case "product", "products" -> types.add("Product");
            case "capability", "capabilities" -> types.add("Capability");
            case "interface", "interfaces", "systeminterface" -> {
                types.add("Interface");
                types.add("SystemInterface");
            }
            case "people", "person" -> types.add("People");
            case "orgunit", "org_unit" -> types.add("OrgUnit");
            case "businessarea", "businessareas", "business_area" -> types.add("BusinessArea");
            case "legalentity", "legalentities", "legal_entity" -> types.add("LegalEntity");
            case "regulation", "regulations" -> types.add("Regulation");
            case "committee", "committees" -> types.add("Committee");
            case "client", "clients" -> types.add("Client");
            case "geography", "geographies" -> types.add("Geography");
            case "regulator", "regulators" -> types.add("Regulator");
            case "regulatorytheme", "regulatorythemes", "regulatory_theme" -> types.add("RegulatoryTheme");
            default -> types.add(objectType.trim());
        }
        return new ArrayList<>(types);
    }

    private String fetchObjectSegmentDisplayName(String objectType, int objectId) {
        SegmentDAO segmentDAO = new SegmentDAO();
        for (String type : buildCrReferenceSegmentTypes(objectType)) {
            try {
                int segId = segmentDAO.getObjectSegmentId(objectId, type);
                if (segId > 0) {
                    String nm = fetchSegmentNameById(segId);
                    return (nm != null && !nm.isBlank()) ? nm : "Not Assigned";
                }
            } catch (SQLException e) {
                // try next candidate type
            }
        }
        return "Not Assigned";
    }

    /**
     * Load Active Task rows for given task IDs.
     * 
     * @param taskIds Set of task IDs
     * @return List of task row maps
     */
    /**
     * Load Active Task rows with all required display columns.
     * Columns include: Id, Name, Title (CR name), Object Type, Object, Assign Date, Due Date, Due In (days), Owner, Segments
     */
    private List<Map<String, Object>> loadActiveTaskRows(Set<Integer> taskIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (taskIds == null || taskIds.isEmpty()) {
            return rows;
        }
        
        // Only return tasks for the current user
        if (currentUserId == null || currentUserId <= 0) {
            return rows;
        }

        try {
            // Use WorkflowTaskDAO to get user-specific tasks, then filter by task IDs
            com.example.budg_v2.dao.WorkflowTaskDAO taskDAO = new com.example.budg_v2.dao.WorkflowTaskDAO();
            List<Map<String, Object>> userTasks = taskDAO.findActiveTasksForUser(currentUserId);
            
            // Filter to only tasks in the provided taskIds set
            for (Map<String, Object> task : userTasks) {
                Object taskIdObj = task.get("taskId");
                if (taskIdObj != null) {
                    int taskId;
                    if (taskIdObj instanceof Integer) {
                        taskId = (Integer) taskIdObj;
                    } else if (taskIdObj instanceof Number) {
                        taskId = ((Number) taskIdObj).intValue();
                    } else {
                        continue;
                    }
                    
                    if (taskIds.contains(taskId)) {
                        // Convert task data to row format
                        Map<String, Object> taskRow = new HashMap<>();
                        
                        // Id
                        taskRow.put("ID", taskId);
                        taskRow.put("id", taskId);
                        
                        // Name
                        taskRow.put("Name", task.get("name"));
                        taskRow.put("name", task.get("name"));
                        
                        // Title (CR name)
                        taskRow.put("Title", task.get("title"));
                        taskRow.put("title", task.get("title"));
                        
                        // Object Type and Object
                        taskRow.put("ObjectType", task.get("objectType"));
                        taskRow.put("objectType", task.get("objectType"));
                        taskRow.put("Object", task.get("object"));
                        taskRow.put("object", task.get("object"));
                        taskRow.put("ObjectId", task.get("objectId"));
                        taskRow.put("objectId", task.get("objectId"));
                        
                        // Assign Date (formatted string)
                        String assignDateStr = formatTaskDate(task.get("assignDate"));
                        taskRow.put("AssignDate", assignDateStr);
                        taskRow.put("assignDate", assignDateStr);

                        // Due Date (formatted string)
                        String dueDateStr = formatTaskDate(task.get("dueDate"));
                        taskRow.put("DueDate", dueDateStr);
                        taskRow.put("dueDate", dueDateStr);

                        // Due In Days
                        taskRow.put("DueInDays", task.get("dueInDays"));
                        taskRow.put("dueInDays", task.get("dueInDays"));
                        taskRow.put("isOverdue", task.get("isOverdue"));

                        // Owner
                        taskRow.put("Owner", task.get("owner"));
                        taskRow.put("owner", task.get("owner"));

                        // Segments
                        taskRow.put("Segments", task.get("segments"));
                        taskRow.put("segments", task.get("segments"));

                        // Pass through changeRequestId and taskId for frontend linking
                        taskRow.put("changeRequestId", task.get("changeRequestId"));
                        taskRow.put("workflowInstanceId", task.get("workflowInstanceId"));
                        taskRow.put("bpmnNodeId", task.get("bpmnNodeId"));

                        rows.add(taskRow);
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadActiveTaskRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }
    
    /**
     * Format a date value (Timestamp or Date) to a "dd-MMM-yyyy" string for display.
     * Returns null if the value is null.
     */
    private String formatTaskDate(Object dateObj) {
        if (dateObj == null) return null;
        try {
            java.util.Date date = null;
            if (dateObj instanceof java.sql.Timestamp) {
                date = (java.sql.Timestamp) dateObj;
            } else if (dateObj instanceof java.util.Date) {
                date = (java.util.Date) dateObj;
            } else if (dateObj instanceof String) {
                String s = (String) dateObj;
                if (!s.isEmpty()) return s;
                return null;
            }
            if (date == null) return null;
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MMM-yyyy");
            return sdf.format(date);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Calculate "Due In (days)" string for a task.
     * Returns "Overdue by X" (red), "X days", or "Not Specified"
     */
    @SuppressWarnings("unused")
    private String calculateDueInDays(Object dueDateObj, Object isOverdueObj) {
        if (dueDateObj == null) {
            return "Not Specified";
        }
        
        try {
            java.sql.Timestamp dueDate = null;
            if (dueDateObj instanceof java.sql.Timestamp) {
                dueDate = (java.sql.Timestamp) dueDateObj;
            } else if (dueDateObj instanceof java.util.Date) {
                dueDate = new java.sql.Timestamp(((java.util.Date) dueDateObj).getTime());
            } else if (dueDateObj instanceof String) {
                // Parse string date
                dueDate = java.sql.Timestamp.valueOf((String) dueDateObj);
            }
            
            if (dueDate == null) {
                return "Not Specified";
            }
            
            // Calculate days difference
            long currentTime = System.currentTimeMillis();
            long dueTime = dueDate.getTime();
            long diffInMillis = dueTime - currentTime;
            long diffInDays = diffInMillis / (1000 * 60 * 60 * 24);
            
            // Check if overdue
            boolean isOverdue = false;
            if (isOverdueObj instanceof Boolean) {
                isOverdue = (Boolean) isOverdueObj;
            } else if (isOverdueObj instanceof Number) {
                isOverdue = ((Number) isOverdueObj).intValue() == 1;
            }
            
            if (isOverdue || diffInDays < 0) {
                long overdueDays = Math.abs(diffInDays);
                return "Overdue by " + overdueDays;
            } else {
                return diffInDays + " days";
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error calculating due in days: " + e.getMessage());
            return "Error";
        }
    }
    
    /**
     * Parse CR Reference field (e.g., "Dataset 123") to extract object information.
     * Returns map with: objectType, objectId, objectName, moduleName
     */
    @SuppressWarnings("unused")
    private Map<String, Object> parseReferenceToObjectInfo(String reference) throws Exception {
        Map<String, Object> info = new HashMap<>();
        
        if (reference == null || reference.trim().isEmpty()) {
            throw new Exception("Reference is empty");
        }
        
        // Parse reference string like "Dataset 123" or "System 456"
        String[] parts = reference.trim().split("\\s+");
        if (parts.length < 2) {
            throw new Exception("Invalid reference format: " + reference);
        }
        
        String objectTypeStr = parts[0];
        Integer objectId = Integer.parseInt(parts[1]);
        
        // Map object type to facet ID
        String facetId = mapObjectTypeToFacetId(objectTypeStr);
        if (facetId == null) {
            throw new Exception("Unknown object type: " + objectTypeStr);
        }
        
        info.put("objectType", facetId);
        info.put("objectId", objectId);
        
        // Get object name from database
        String objectName = getObjectNameById(facetId, objectId);
        info.put("objectName", objectName != null ? objectName : reference);
        
        // Get module name for this facet
        String moduleName = getModuleNameForFacet(facetId);
        info.put("moduleName", moduleName);
        
        return info;
    }
    
    /**
     * Get object name by facet ID and object ID.
     */
    private String getObjectNameById(String facetId, Integer objectId) {
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            
            String tableName = getTableNameForFacet(facetId);
            if (tableName == null) {
                return null;
            }
            
            String sql = "SELECT PrimaryName FROM " + tableName + " WHERE ID = ? AND DeletedDatetime IS NULL";
            List<Object> params = List.of(objectId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);
            
            if (!results.isEmpty()) {
                Object name = results.get(0).get("PrimaryName");
                return name != null ? name.toString() : null;
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error getting object name for " + facetId + " " + objectId + ": " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get segments for an object.
     */
    @SuppressWarnings("unused")
    private Set<Integer> getObjectSegments(String objectType, Integer objectId) {
        Set<Integer> segments = new HashSet<>();
        
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            
            // Query segment_x_resource table
            String sql = "SELECT DISTINCT Segment_ID FROM segment_x_resource " +
                         "WHERE Resource_ID = ? AND Object_Type = ?";
            List<Object> params = List.of(objectId, objectType);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);
            
            for (Map<String, Object> row : results) {
                Object segmentId = row.get("Segment_ID");
                if (segmentId instanceof Integer) {
                    segments.add((Integer) segmentId);
                } else if (segmentId instanceof Number) {
                    segments.add(((Number) segmentId).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error getting segments for " + objectType + " " + objectId + ": " + e.getMessage());
        }
        
        return segments;
    }
    
    /**
     * Get module name for a facet.
     */
    private String getModuleNameForFacet(String facetId) {
        return switch (facetId.toUpperCase()) {
            case "DATASET", "DATA_SETS", "DATASETS" -> "Data Sets";
            case "SYSTEM", "SYSTEMS" -> "Systems";
            case "ATTRIBUTE", "ATTRIBUTES" -> "Attributes";
            case "GLOSSARY", "GLOSSARIES" -> "Glossary";
            case "PROCESS", "PROCESSES" -> "Processes";
            case "PROJECT", "PROJECTS" -> "Projects";
            case "POLICY", "POLICIES" -> "Policies";
            case "INTERFACE", "INTERFACES" -> "Interfaces";
            case "PEOPLE", "PERSON" -> "People";
            case "ORG_UNIT", "ORGUNIT", "ORG_UNITS" -> "Org Units";
            case "ROLE", "ROLES" -> "Roles";
            case "CAPABILITY", "CAPABILITIES" -> "Capabilities";
            case "PRODUCT", "PRODUCTS" -> "Products";
            case "BUSINESS_AREA", "BUSINESSAREA", "BUSINESS_AREAS" -> "Business Areas";
            case "LEGAL_ENTITY", "LEGALENTITY", "LEGAL_ENTITIES" -> "Legal Entities";
            case "GEOGRAPHY", "GEOGRAPHIES" -> "Geographies";
            case "REGULATION", "REGULATIONS" -> "Regulations";
            case "REGULATOR", "REGULATORS" -> "Regulators";
            case "REGULATORY_THEME", "REGULATORYTHEME" -> "Regulatory Themes";
            case "COMMITTEE", "COMMITTEES" -> "Committees";
            case "CHANGEREQUEST", "CHANGE_REQUEST", "CHANGE_REQUESTS" -> "Change Requests";
            default -> facetId;
        };
    }
    
    /**
     * Get table name for a facet.
     */
    private String getTableNameForFacet(String facetId) {
        return switch (facetId.toUpperCase()) {
            case "DATASET", "DATA_SETS", "DATASETS" -> "dataset";
            case "SYSTEM", "SYSTEMS" -> "system";
            case "ATTRIBUTE", "ATTRIBUTES" -> "attribute";
            case "GLOSSARY", "GLOSSARIES" -> "glossary";
            case "PROCESS", "PROCESSES" -> "process";
            case "PROJECT", "PROJECTS" -> "project";
            case "POLICY", "POLICIES" -> "policy";
            case "INTERFACE", "INTERFACES" -> "interface";
            case "PEOPLE", "PERSON" -> "people";
            case "ORG_UNIT", "ORGUNIT", "ORG_UNITS" -> "org_unit";
            case "ROLE", "ROLES" -> "role";
            case "CAPABILITY", "CAPABILITIES" -> "capability";
            case "PRODUCT", "PRODUCTS" -> "product";
            case "BUSINESS_AREA", "BUSINESSAREA", "BUSINESS_AREAS" -> "business_area";
            case "LEGAL_ENTITY", "LEGALENTITY", "LEGAL_ENTITIES" -> "legal";
            case "GEOGRAPHY", "GEOGRAPHIES" -> "geography";
            case "REGULATION", "REGULATIONS" -> "regulation";
            case "REGULATOR", "REGULATORS" -> "regulator";
            case "REGULATORY_THEME", "REGULATORYTHEME" -> "regulatorytheme";
            case "COMMITTEE", "COMMITTEES" -> "committee";
            case "CHANGEREQUEST", "CHANGE_REQUEST", "CHANGE_REQUESTS" -> "changerequest";
            default -> null;
        };
    }

    /**
     * Enrich search results with impact relationships for datasets.
     * For each dataset, finds:
     * - Direct relationships: processes, projects, policies (dataset junction), products, business areas
     * - Via the dataset's system ({@code MasterSource}): projects, processes, policies linked in
     *   {@code project_x_system}, {@code process_x_system}, {@code policy_x_system}
     * - Other indirect relationships: products, business areas, capabilities where applicable
     * 
     * @param results Current search results
     * @return Enriched results with Process, Project, Policy, Product, Business Area, and
     *         Capability facets added
     * @throws SQLException if database error occurs
     */
    private Map<String, FacetResult> enrichDatasetWithImpactRelationships(Map<String, FacetResult> results)
            throws SQLException {
        if (results == null || results.isEmpty()) {
            return results;
        }

        // Check if we have datasets in the results
        FacetResult datasetResult = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("DATASET") ||
                    facetId.toUpperCase().equals("DATA_SETS") ||
                    facetId.toUpperCase().equals("DATASETS"))) {
                datasetResult = entry.getValue();
                break;
            }
        }

        if (datasetResult == null || datasetResult.getIds() == null || datasetResult.getIds().isEmpty()) {
            return results; // No datasets to enrich
        }

        // Collect all related object IDs
        Set<Integer> allProcessIds = new HashSet<>();
        Set<Integer> allProjectIds = new HashSet<>();
        Set<Integer> allPolicyIds = new HashSet<>();
        Set<Integer> allProductIds = new HashSet<>();
        Set<Integer> allBusinessAreaIds = new HashSet<>();
        Set<Integer> allCapabilityIds = new HashSet<>();

        Map<Integer, Integer> processDepth = new HashMap<>();
        Map<Integer, Integer> projectDepth = new HashMap<>();
        Map<Integer, Integer> policyDepth = new HashMap<>();
        Map<Integer, Integer> productDepth = new HashMap<>();
        Map<Integer, Integer> businessAreaDepth = new HashMap<>();
        Map<Integer, Integer> capabilityDepth = new HashMap<>();

        // Get seed dataset IDs (depth 0) - only process direct relations from seed datasets
        Set<Integer> seedDatasetIds = new HashSet<>();
        if (datasetResult.getDepthById() != null) {
            for (Integer datasetId : datasetResult.getIds()) {
                int depth = datasetResult.getDepthById().getOrDefault(datasetId, Integer.MAX_VALUE);
                if (depth == 0) {
                    seedDatasetIds.add(datasetId);
                }
            }
        } else {
            seedDatasetIds.addAll(datasetResult.getIds());
        }

        // Process only seed datasets (depth 0) - skip enriched datasets (depth > 0)
        for (Integer datasetId : seedDatasetIds) {
            try {
                // Query direct relationships (depth 1 only)
                Map<String, Set<Integer>> directRelationships = queryDatasetDirectRelationships(datasetId);

                // Add direct relationships (depth 1)
                if (directRelationships.containsKey("PROCESS")) {
                    for (Integer processId : directRelationships.get("PROCESS")) {
                        allProcessIds.add(processId);
                        processDepth.put(processId, 1);
                    }
                }
                if (directRelationships.containsKey("PROJECT")) {
                    for (Integer projectId : directRelationships.get("PROJECT")) {
                        allProjectIds.add(projectId);
                        projectDepth.put(projectId, 1);
                    }
                }
                if (directRelationships.containsKey("POLICY")) {
                    for (Integer policyId : directRelationships.get("POLICY")) {
                        allPolicyIds.add(policyId);
                        policyDepth.put(policyId, 1);
                    }
                }
                if (directRelationships.containsKey("PRODUCT")) {
                    for (Integer productId : directRelationships.get("PRODUCT")) {
                        allProductIds.add(productId);
                        productDepth.put(productId, 1);
                    }
                }
                if (directRelationships.containsKey("BUSINESS_AREA")) {
                    for (Integer businessAreaId : directRelationships.get("BUSINESS_AREA")) {
                        allBusinessAreaIds.add(businessAreaId);
                        businessAreaDepth.put(businessAreaId, 1);
                    }
                }

                // Project/Process/Policy above include rows linked via the dataset's system
                // (see queryDatasetDirectRelationships); other indirect paths remain excluded here.
            } catch (Exception e) {
                System.err
                        .println("[UnisonSearchService] Error enriching dataset " + datasetId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Add Process results
        if (!allProcessIds.isEmpty()) {
            String processFacetId = "PROCESS";
            FacetResult existingProcessResult = results.get(processFacetId);

            Set<Integer> mergedProcessIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedProcessDepth = new HashMap<>();

            if (existingProcessResult != null && existingProcessResult.getIds() != null) {
                mergedProcessIds.addAll(existingProcessResult.getIds());
                if (existingProcessResult.getDepthById() != null) {
                    mergedProcessDepth.putAll(existingProcessResult.getDepthById());
                }
            }

            mergedProcessIds.addAll(allProcessIds);
            mergedProcessDepth.putAll(processDepth);

            List<Map<String, Object>> processRows = loadProcessRows(mergedProcessIds);
            int processTotal = computeAccessibleTotalCount(processFacetId,
                    processRows != null ? processRows.size() : 0);

            results.put(processFacetId,
                    new FacetResult(mergedProcessIds, true, mergedProcessDepth, processRows, processTotal));
        }

        // Add Project results
        if (!allProjectIds.isEmpty()) {
            String projectFacetId = "PROJECT";
            FacetResult existingProjectResult = results.get(projectFacetId);

            Set<Integer> mergedProjectIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedProjectDepth = new HashMap<>();

            if (existingProjectResult != null && existingProjectResult.getIds() != null) {
                mergedProjectIds.addAll(existingProjectResult.getIds());
                if (existingProjectResult.getDepthById() != null) {
                    mergedProjectDepth.putAll(existingProjectResult.getDepthById());
                }
            }

            mergedProjectIds.addAll(allProjectIds);
            mergedProjectDepth.putAll(projectDepth);

            List<Map<String, Object>> projectRows = loadProjectRows(mergedProjectIds);
            int projectTotal = computeAccessibleTotalCount(projectFacetId,
                    projectRows != null ? projectRows.size() : 0);

            results.put(projectFacetId,
                    new FacetResult(mergedProjectIds, true, mergedProjectDepth, projectRows, projectTotal));
        }

        // Add Policy results
        if (!allPolicyIds.isEmpty()) {
            String policyFacetId = "POLICY";
            FacetResult existingPolicyResult = results.get(policyFacetId);

            Set<Integer> mergedPolicyIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedPolicyDepth = new HashMap<>();

            if (existingPolicyResult != null && existingPolicyResult.getIds() != null) {
                mergedPolicyIds.addAll(existingPolicyResult.getIds());
                if (existingPolicyResult.getDepthById() != null) {
                    mergedPolicyDepth.putAll(existingPolicyResult.getDepthById());
                }
            }

            mergedPolicyIds.addAll(allPolicyIds);
            mergedPolicyDepth.putAll(policyDepth);

            List<Map<String, Object>> policyRows = loadPolicyRows(mergedPolicyIds);
            int policyTotal = computeAccessibleTotalCount(policyFacetId,
                    policyRows != null ? policyRows.size() : 0);

            results.put(policyFacetId,
                    new FacetResult(mergedPolicyIds, true, mergedPolicyDepth, policyRows, policyTotal));
        }

        // Add Product results
        if (!allProductIds.isEmpty()) {
            String productFacetId = "PRODUCT";
            FacetResult existingProductResult = results.get(productFacetId);

            Set<Integer> mergedProductIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedProductDepth = new HashMap<>();

            if (existingProductResult != null && existingProductResult.getIds() != null) {
                mergedProductIds.addAll(existingProductResult.getIds());
                if (existingProductResult.getDepthById() != null) {
                    mergedProductDepth.putAll(existingProductResult.getDepthById());
                }
            }

            mergedProductIds.addAll(allProductIds);
            mergedProductDepth.putAll(productDepth);

            List<Map<String, Object>> productRows = loadProductRows(mergedProductIds);
            int productTotal = computeAccessibleTotalCount(productFacetId,
                    productRows != null ? productRows.size() : 0);

            results.put(productFacetId,
                    new FacetResult(mergedProductIds, true, mergedProductDepth, productRows, productTotal));
        }

        // Add Business Area results
        if (!allBusinessAreaIds.isEmpty()) {
            String businessAreaFacetId = "BUSINESS_AREA";
            FacetResult existingBusinessAreaResult = results.get(businessAreaFacetId);

            Set<Integer> mergedBusinessAreaIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedBusinessAreaDepth = new HashMap<>();

            if (existingBusinessAreaResult != null && existingBusinessAreaResult.getIds() != null) {
                mergedBusinessAreaIds.addAll(existingBusinessAreaResult.getIds());
                if (existingBusinessAreaResult.getDepthById() != null) {
                    mergedBusinessAreaDepth.putAll(existingBusinessAreaResult.getDepthById());
                }
            }

            mergedBusinessAreaIds.addAll(allBusinessAreaIds);
            mergedBusinessAreaDepth.putAll(businessAreaDepth);

            List<Map<String, Object>> businessAreaRows = loadBusinessAreaRows(mergedBusinessAreaIds);
            int businessAreaTotal = computeAccessibleTotalCount(businessAreaFacetId,
                    businessAreaRows != null ? businessAreaRows.size() : 0);

            results.put(businessAreaFacetId, new FacetResult(mergedBusinessAreaIds, true, mergedBusinessAreaDepth,
                    businessAreaRows, businessAreaTotal));
        }

        // Add Capability results
        if (!allCapabilityIds.isEmpty()) {
            String capabilityFacetId = "CAPABILITY";
            FacetResult existingCapabilityResult = results.get(capabilityFacetId);

            Set<Integer> mergedCapabilityIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedCapabilityDepth = new HashMap<>();

            if (existingCapabilityResult != null && existingCapabilityResult.getIds() != null) {
                mergedCapabilityIds.addAll(existingCapabilityResult.getIds());
                if (existingCapabilityResult.getDepthById() != null) {
                    mergedCapabilityDepth.putAll(existingCapabilityResult.getDepthById());
                }
            }

            mergedCapabilityIds.addAll(allCapabilityIds);
            mergedCapabilityDepth.putAll(capabilityDepth);

            List<Map<String, Object>> capabilityRows = loadCapabilityRows(mergedCapabilityIds);
            int capabilityTotal = computeAccessibleTotalCount(capabilityFacetId,
                    capabilityRows != null ? capabilityRows.size() : 0);

            results.put(capabilityFacetId,
                    new FacetResult(mergedCapabilityIds, true, mergedCapabilityDepth, capabilityRows, capabilityTotal));
        }

        return results;
    }

    /**
     * Helper method to extract IDs from query results.
     * 
     * @param results Query results containing ID column
     * @param columnName Name of the column containing IDs
     * @return Set of extracted IDs
     */
    private Set<Integer> extractIds(List<Map<String, Object>> results, String columnName) {
        Set<Integer> ids = new HashSet<>();
        if (results == null || columnName == null || columnName.isBlank()) {
            return ids;
        }
        String normalizedRequested = normalizeColumnKey(columnName);
        for (Map<String, Object> row : results) {
            Object id = row.get(columnName);
            if (id == null) {
                // JDBC drivers may return key names with different casing/format.
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String key = entry.getKey();
                    if (key == null) {
                        continue;
                    }
                    if (normalizeColumnKey(key).equals(normalizedRequested)) {
                        id = entry.getValue();
                        break;
                    }
                }
            }
            if (id instanceof Integer) {
                ids.add((Integer) id);
            } else if (id instanceof Number) {
                ids.add(((Number) id).intValue());
            }
        }
        return ids;
    }

    private String normalizeColumnKey(String key) {
        if (key == null) {
            return "";
        }
        return key.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "");
    }

    /**
     * Query relationships for a dataset: processes, projects, and policies from dataset junction
     * tables, merged with those linked to the dataset's system ({@code MasterSource}) via
     * {@code project_x_system}, {@code process_x_system}, and {@code policy_x_system}; plus products.
     * 
     * @param datasetId Dataset ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> queryDatasetDirectRelationships(Integer datasetId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (datasetId == null) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            List<Object> params = List.of(datasetId);

            // Processes via dataset junction (filter deleted)
            String processSql = "SELECT DISTINCT pxd.processid FROM process_x_dataset pxd " +
                    "JOIN process p ON pxd.processid = p.id " +
                    "WHERE pxd.datasetid = ? AND p.deleteddatetime IS NULL";
            Set<Integer> processIds = new HashSet<>();
            for (Map<String, Object> row : dbHelper.executeQuery(processSql, params)) {
                Object id = row.get("processid");
                if (id instanceof Integer) {
                    processIds.add((Integer) id);
                } else if (id instanceof Number) {
                    processIds.add(((Number) id).intValue());
                }
            }

            // Projects via dataset junction (filter deleted)
            String projectSql = "SELECT DISTINCT pxd.projectid FROM project_x_dataset pxd " +
                    "JOIN project pj ON pxd.projectid = pj.id " +
                    "WHERE pxd.dataset_id = ? AND pj.deletedatetime IS NULL";
            Set<Integer> projectIds = new HashSet<>();
            for (Map<String, Object> row : dbHelper.executeQuery(projectSql, params)) {
                Object id = row.get("projectid");
                if (id instanceof Integer) {
                    projectIds.add((Integer) id);
                } else if (id instanceof Number) {
                    projectIds.add(((Number) id).intValue());
                }
            }

            // Policies via dataset junction
            Set<Integer> policyIds = new HashSet<>();
            String policyDatasetSql = "SELECT DISTINCT PolicyID FROM policy_x_dataset WHERE DatasetID = ?";
            policyIds.addAll(extractIds(dbHelper.executeQuery(policyDatasetSql, params), "PolicyID"));

            // Merge project / process / policy linked to the dataset's system (MasterSource)
            String masterSql = "SELECT MasterSource FROM dataset WHERE ID = ? AND DeletedDatetime IS NULL";
            List<Map<String, Object>> masterRows = dbHelper.executeQuery(masterSql, params);
            Integer systemId = null;
            if (!masterRows.isEmpty()) {
                Object ms = masterRows.get(0).get("MasterSource");
                if (ms instanceof Integer) {
                    systemId = (Integer) ms;
                } else if (ms instanceof Number) {
                    systemId = ((Number) ms).intValue();
                }
            }
            if (systemId != null) {
                List<Object> sysParams = List.of(systemId);

                String projectSystemSql = "SELECT DISTINCT pxs.projectid FROM project_x_system pxs " +
                        "JOIN project pj ON pxs.projectid = pj.id " +
                        "WHERE pxs.systemid = ? AND pj.deletedatetime IS NULL";
                for (Map<String, Object> row : dbHelper.executeQuery(projectSystemSql, sysParams)) {
                    Object id = row.get("projectid");
                    if (id instanceof Integer) {
                        projectIds.add((Integer) id);
                    } else if (id instanceof Number) {
                        projectIds.add(((Number) id).intValue());
                    }
                }

                String processSystemSql = "SELECT DISTINCT pxs.process_id FROM process_x_system pxs " +
                        "JOIN process p ON pxs.process_id = p.id " +
                        "WHERE pxs.system_id = ? AND p.deleteddatetime IS NULL";
                for (Map<String, Object> row : dbHelper.executeQuery(processSystemSql, sysParams)) {
                    Object id = row.get("process_id");
                    if (id instanceof Integer) {
                        processIds.add((Integer) id);
                    } else if (id instanceof Number) {
                        processIds.add(((Number) id).intValue());
                    }
                }

                String policySystemSql = "SELECT DISTINCT Policy_ID FROM policy_x_system WHERE System_ID = ?";
                policyIds.addAll(extractIds(dbHelper.executeQuery(policySystemSql, sysParams), "Policy_ID"));
            }

            if (!processIds.isEmpty()) {
                relationships.put("PROCESS", processIds);
            }
            if (!projectIds.isEmpty()) {
                relationships.put("PROJECT", projectIds);
            }
            if (!policyIds.isEmpty()) {
                relationships.put("POLICY", policyIds);
            }

            // Query products (filter deleted)
            String productSql = "SELECT DISTINCT pxd.Product_ID FROM product_x_dataset pxd " +
                    "JOIN product prd ON pxd.Product_ID = prd.id " +
                    "WHERE pxd.Dataset_ID = ? AND prd.DeletedDatetime IS NULL";
            List<Map<String, Object>> productResults = dbHelper.executeQuery(productSql, params);
            Set<Integer> productIds = new HashSet<>();
            for (Map<String, Object> row : productResults) {
                // Try exact match first, then case-insensitive fallback
                Object id = row.get("Product_ID");
                if (id == null) {
                    id = row.get("ProductID");
                }
                if (id == null) {
                    // Try case-insensitive search
                    for (String key : row.keySet()) {
                        if (key != null && key.equalsIgnoreCase("Product_ID")) {
                            id = row.get(key);
                            break;
                        }
                    }
                }
                if (id instanceof Integer) {
                    productIds.add((Integer) id);
                } else if (id instanceof Number) {
                    productIds.add(((Number) id).intValue());
                }
            }
            if (!productIds.isEmpty()) {
                relationships.put("PRODUCT", productIds);
            }

            // Query business areas - REMOVED: dataset_x_businessarea table does not exist in database
            // The relationship between dataset and business_area should be handled through other means if needed
            // try {
            //     String businessAreaSql = "SELECT DISTINCT BusinessArea_ID FROM dataset_x_businessarea WHERE Dataset_ID = ?";
            //     List<Map<String, Object>> businessAreaResults = dbHelper.executeQuery(businessAreaSql, params);
            //     Set<Integer> businessAreaIds = new HashSet<>();
            //     for (Map<String, Object> row : businessAreaResults) {
            //         Object id = row.get("BusinessArea_ID");
            //         if (id instanceof Integer) {
            //             businessAreaIds.add((Integer) id);
            //         } else if (id instanceof Number) {
            //             businessAreaIds.add(((Number) id).intValue());
            //         }
            //     }
            //     if (!businessAreaIds.isEmpty()) {
            //         relationships.put("BUSINESS_AREA", businessAreaIds);
            //     }
            // } catch (SQLException e) {
            //     // Table may not exist in all deployments - this is expected, skip silently
            // }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying dataset direct relationships for dataset "
                    + datasetId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return relationships;
    }

    /**
     * Query impact relationships for System objects.
     * Merges {@link #querySystemRelationships} (process, project, product, business area, capability, policy)
     * with client and legal links not covered there.
     * 
     * @param systemId System ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> querySystemImpactRelationships(Integer systemId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (systemId == null) {
            return relationships;
        }

        try {
            relationships.putAll(querySystemRelationships(Set.of(systemId)));

            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            List<Object> p = List.of(systemId);

            Set<Integer> clientIds = extractIds(
                    dbHelper.executeQuery("SELECT DISTINCT Client_ID FROM client_x_system WHERE System_ID = ?", p),
                    "Client_ID");
            if (!clientIds.isEmpty()) {
                relationships.merge("CLIENT", clientIds, (a, b) -> {
                    Set<Integer> u = new HashSet<>(a);
                    u.addAll(b);
                    return u;
                });
            }

            Set<Integer> legalIds = extractIds(
                    dbHelper.executeQuery("SELECT DISTINCT Legal_ID FROM system_x_legal WHERE System_ID = ?", p),
                    "Legal_ID");
            if (!legalIds.isEmpty()) {
                relationships.merge("LEGAL_ENTITY", legalIds, (a, b) -> {
                    Set<Integer> u = new HashSet<>(a);
                    u.addAll(b);
                    return u;
                });
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying system impact relationships: " + e.getMessage());
        }

        return relationships;
    }

    /**
     * Enrich dataset rows with their system's impact relationships.
     * For each dataset, gets its system (via MasterSource) and adds the system's impact data.
     * 
     * @param datasetRows List of dataset row maps to enrich
     * @return Enriched dataset rows with systemImpact field added
     */
    private List<Map<String, Object>> enrichDatasetRowsWithSystemImpact(List<Map<String, Object>> datasetRows) {
        if (datasetRows == null || datasetRows.isEmpty()) {
            return datasetRows;
        }

        // Cache system impact queries to avoid duplicate queries for same system
        Map<Integer, Map<String, Set<Integer>>> systemImpactCache = new HashMap<>();

        for (Map<String, Object> datasetRow : datasetRows) {
            try {
                // Get system ID from MasterSource
                Object masterSourceObj = datasetRow.get("MasterSource");
                if (masterSourceObj == null) {
                    // No system associated, add empty impact
                    datasetRow.put("systemImpact", new HashMap<String, Set<Integer>>());
                    continue;
                }

                Integer systemId = null;
                if (masterSourceObj instanceof Integer) {
                    systemId = (Integer) masterSourceObj;
                } else if (masterSourceObj instanceof Number) {
                    systemId = ((Number) masterSourceObj).intValue();
                }

                if (systemId == null) {
                    datasetRow.put("systemImpact", new HashMap<String, Set<Integer>>());
                    continue;
                }

                // Check cache first
                Map<String, Set<Integer>> systemImpact;
                if (systemImpactCache.containsKey(systemId)) {
                    systemImpact = systemImpactCache.get(systemId);
                } else {
                    // Query system impact relationships
                    try {
                        systemImpact = querySystemImpactRelationships(systemId);
                        systemImpactCache.put(systemId, systemImpact);
                    } catch (SQLException e) {
                        System.err.println("[UnisonSearchService] Error querying system impact for system " + systemId + ": " + e.getMessage());
                        systemImpact = new HashMap<String, Set<Integer>>();
                    }
                }

                // Add system impact to dataset row
                // Convert Sets to Lists for JSON serialization
                Map<String, List<Integer>> systemImpactList = new HashMap<>();
                for (Map.Entry<String, Set<Integer>> entry : systemImpact.entrySet()) {
                    systemImpactList.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
                datasetRow.put("systemImpact", systemImpactList);

            } catch (Exception e) {
                System.err.println("[UnisonSearchService] Error enriching dataset row with system impact: " + e.getMessage());
                // Add empty impact on error
                datasetRow.put("systemImpact", new HashMap<String, List<Integer>>());
            }
        }

        return datasetRows;
    }

    /**
     * Query impact relationships for Policy objects.
     * 
     * @param policyId Policy ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> queryPolicyImpactRelationships(Integer policyId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (policyId == null) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query product_x_policy
            String sql1 = "SELECT DISTINCT productid FROM product_x_policy WHERE policyid = ?";
            List<Map<String, Object>> productResults = dbHelper.executeQuery(sql1, List.of(policyId));
            Set<Integer> productIds = extractIds(productResults, "productid");
            if (!productIds.isEmpty()) {
                relationships.put("PRODUCT", productIds);
            }

            // Query client_x_policy
            String sql2 = "SELECT DISTINCT Client_ID FROM client_x_policy WHERE Policy_ID = ?";
            List<Map<String, Object>> clientResults = dbHelper.executeQuery(sql2, List.of(policyId));
            Set<Integer> clientIds = extractIds(clientResults, "Client_ID");
            if (!clientIds.isEmpty()) {
                relationships.put("CLIENT", clientIds);
            }

            // Query policy_x_process
            String sql3 = "SELECT DISTINCT process_id FROM policy_x_process WHERE Policy_ID = ?";
            List<Map<String, Object>> processResults = dbHelper.executeQuery(sql3, List.of(policyId));
            Set<Integer> processIds = extractIds(processResults, "process_id");
            if (!processIds.isEmpty()) {
                relationships.put("PROCESS", processIds);
            }

            // Query policy_x_project
            String sql4 = "SELECT DISTINCT project_id FROM policy_x_project WHERE Policy_ID = ?";
            List<Map<String, Object>> projectResults = dbHelper.executeQuery(sql4, List.of(policyId));
            Set<Integer> projectIds = extractIds(projectResults, "project_id");
            if (!projectIds.isEmpty()) {
                relationships.put("PROJECT", projectIds);
            }

            // Query policy_x_system
            String sql5 = "SELECT DISTINCT System_ID FROM policy_x_system WHERE Policy_ID = ?";
            List<Map<String, Object>> systemResults = dbHelper.executeQuery(sql5, List.of(policyId));
            Set<Integer> systemIds = extractIds(systemResults, "System_ID");
            if (!systemIds.isEmpty()) {
                relationships.put("SYSTEM", systemIds);
            }

            // Query policy_x_businessarea
            String sql6 = "SELECT DISTINCT BusinessArea_ID FROM policy_x_businessarea WHERE Policy_ID = ?";
            List<Map<String, Object>> businessAreaResults = dbHelper.executeQuery(sql6, List.of(policyId));
            Set<Integer> businessAreaIds = extractIds(businessAreaResults, "BusinessArea_ID");
            if (!businessAreaIds.isEmpty()) {
                relationships.put("BUSINESS_AREA", businessAreaIds);
            }

            // Query policy_x_legal
            String sql7 = "SELECT DISTINCT Legal_ID FROM policy_x_legal WHERE Policy_ID = ?";
            List<Map<String, Object>> legalResults = dbHelper.executeQuery(sql7, List.of(policyId));
            Set<Integer> legalIds = extractIds(legalResults, "Legal_ID");
            if (!legalIds.isEmpty()) {
                relationships.put("LEGAL_ENTITY", legalIds);
            }

            // Query policy_x_dataset
            String sql8 = "SELECT DISTINCT Dataset_ID FROM policy_x_dataset WHERE Policy_ID = ?";
            List<Map<String, Object>> datasetResults = dbHelper.executeQuery(sql8, List.of(policyId));
            Set<Integer> datasetIds = extractIds(datasetResults, "Dataset_ID");
            if (!datasetIds.isEmpty()) {
                relationships.put("DATASET", datasetIds);
            }

            // Query policy_x_attribute
            String sql9 = "SELECT DISTINCT attributeid FROM policy_x_attribute WHERE policyid = ?";
            List<Map<String, Object>> attributeResults = dbHelper.executeQuery(sql9, List.of(policyId));
            Set<Integer> attributeIds = extractIds(attributeResults, "attributeid");
            if (!attributeIds.isEmpty()) {
                relationships.put("ATTRIBUTE", attributeIds);
            }

            // Query policy_x_glossary
            String sql10 = "SELECT DISTINCT GlossaryID FROM policy_x_glossary WHERE PolicyID = ?";
            List<Map<String, Object>> glossaryResults = dbHelper.executeQuery(sql10, List.of(policyId));
            Set<Integer> glossaryIds = extractIds(glossaryResults, "GlossaryID");
            if (!glossaryIds.isEmpty()) {
                relationships.put("GLOSSARY", glossaryIds);
            }

            // Query regulation_x_policy
            String sql11 = "SELECT DISTINCT RegulationID FROM regulation_x_policy WHERE Policy_ID = ?";
            List<Map<String, Object>> regulationResults = dbHelper.executeQuery(sql11, List.of(policyId));
            Set<Integer> regulationIds = extractIds(regulationResults, "RegulationID");
            if (!regulationIds.isEmpty()) {
                relationships.put("REGULATION", regulationIds);
            }

        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying policy impact relationships: " + e.getMessage());
        }

        return relationships;
    }

    /**
     * Query impact relationships for Process objects.
     * 
     * @param processId Process ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> queryProcessImpactRelationships(Integer processId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (processId == null) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query process_x_system
            String sql1 = "SELECT DISTINCT system_id FROM process_x_system WHERE process_id = ?";
            List<Map<String, Object>> systemResults = dbHelper.executeQuery(sql1, List.of(processId));
            Set<Integer> systemIds = extractIds(systemResults, "system_id");
            if (!systemIds.isEmpty()) {
                relationships.put("SYSTEM", systemIds);
            }

            // Query product_x_process (columns: productid, processid)
            String sql2 = "SELECT DISTINCT productid FROM product_x_process WHERE processid = ?";
            List<Map<String, Object>> productResults = dbHelper.executeQuery(sql2, List.of(processId));
            Set<Integer> productIds = extractIds(productResults, "productid");
            if (!productIds.isEmpty()) {
                relationships.put("PRODUCT", productIds);
            }

            // Query client_x_process
            String sql3 = "SELECT DISTINCT Client_ID FROM client_x_process WHERE Process_ID = ?";
            List<Map<String, Object>> clientResults = dbHelper.executeQuery(sql3, List.of(processId));
            Set<Integer> clientIds = extractIds(clientResults, "Client_ID");
            if (!clientIds.isEmpty()) {
                relationships.put("CLIENT", clientIds);
            }

            // Query glossary_x_process
            String sql4 = "SELECT DISTINCT Glossary_ID FROM glossary_x_process WHERE Process_ID = ?";
            List<Map<String, Object>> glossaryResults = dbHelper.executeQuery(sql4, List.of(processId));
            Set<Integer> glossaryIds = extractIds(glossaryResults, "Glossary_ID");
            if (!glossaryIds.isEmpty()) {
                relationships.put("GLOSSARY", glossaryIds);
            }

            // Query project_x_process
            String sql5 = "SELECT DISTINCT projectid FROM project_x_process WHERE process_id = ?";
            List<Map<String, Object>> projectResults = dbHelper.executeQuery(sql5, List.of(processId));
            Set<Integer> projectIds = extractIds(projectResults, "projectid");
            if (!projectIds.isEmpty()) {
                relationships.put("PROJECT", projectIds);
            }

            // Query policy_x_process
            String sql6 = "SELECT DISTINCT policy_id FROM policy_x_process WHERE process_id = ?";
            List<Map<String, Object>> policyResults = dbHelper.executeQuery(sql6, List.of(processId));
            Set<Integer> policyIds = extractIds(policyResults, "policy_id");
            if (!policyIds.isEmpty()) {
                relationships.put("POLICY", policyIds);
            }

            // Query process_x_interface
            String sql7 = "SELECT DISTINCT interface_id FROM process_x_interface WHERE process_id = ?";
            List<Map<String, Object>> interfaceResults = dbHelper.executeQuery(sql7, List.of(processId));
            Set<Integer> interfaceIds = extractIds(interfaceResults, "interface_id");
            if (!interfaceIds.isEmpty()) {
                relationships.put("INTERFACE", interfaceIds);
            }

            // Query process_x_legal
            String sql8 = "SELECT DISTINCT Legal_ID FROM process_x_legal WHERE Process_ID = ?";
            List<Map<String, Object>> legalResults = dbHelper.executeQuery(sql8, List.of(processId));
            Set<Integer> legalIds = extractIds(legalResults, "Legal_ID");
            if (!legalIds.isEmpty()) {
                relationships.put("LEGAL_ENTITY", legalIds);
            }

            // Query process_x_dataset
            String sql9 = "SELECT DISTINCT datasetid FROM process_x_dataset WHERE processid = ?";
            List<Map<String, Object>> datasetResults = dbHelper.executeQuery(sql9, List.of(processId));
            Set<Integer> datasetIds = extractIds(datasetResults, "datasetid");
            if (!datasetIds.isEmpty()) {
                relationships.put("DATASET", datasetIds);
            }

            // Query process_x_attribute
            String sql10 = "SELECT DISTINCT attributeid FROM process_x_attribute WHERE processid = ?";
            List<Map<String, Object>> attributeResults = dbHelper.executeQuery(sql10, List.of(processId));
            Set<Integer> attributeIds = extractIds(attributeResults, "attributeid");
            if (!attributeIds.isEmpty()) {
                relationships.put("ATTRIBUTE", attributeIds);
            }

        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying process impact relationships: " + e.getMessage());
        }

        return relationships;
    }

    /**
     * Query impact relationships for Project objects.
     * 
     * @param projectId Project ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> queryProjectImpactRelationships(Integer projectId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (projectId == null) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query project_x_system
            String sql1 = "SELECT DISTINCT SystemID FROM project_x_system WHERE project_id = ?";
            List<Map<String, Object>> systemResults = dbHelper.executeQuery(sql1, List.of(projectId));
            Set<Integer> systemIds = extractIds(systemResults, "SystemID");
            if (!systemIds.isEmpty()) {
                relationships.put("SYSTEM", systemIds);
            }

            // Query project_x_process
            String sql2 = "SELECT DISTINCT process_id FROM project_x_process WHERE project_id = ?";
            List<Map<String, Object>> processResults = dbHelper.executeQuery(sql2, List.of(projectId));
            Set<Integer> processIds = extractIds(processResults, "process_id");
            if (!processIds.isEmpty()) {
                relationships.put("PROCESS", processIds);
            }

            // Query project_x_dataset
            String sql3 = "SELECT DISTINCT datasetid FROM project_x_dataset WHERE projectid = ?";
            List<Map<String, Object>> datasetResults = dbHelper.executeQuery(sql3, List.of(projectId));
            Set<Integer> datasetIds = extractIds(datasetResults, "datasetid");
            if (!datasetIds.isEmpty()) {
                relationships.put("DATASET", datasetIds);
            }

            // Query project_x_attribute
            String sql4 = "SELECT DISTINCT AttributeID FROM project_x_attribute WHERE project_id = ?";
            List<Map<String, Object>> attributeResults = dbHelper.executeQuery(sql4, List.of(projectId));
            Set<Integer> attributeIds = extractIds(attributeResults, "AttributeID");
            if (!attributeIds.isEmpty()) {
                relationships.put("ATTRIBUTE", attributeIds);
            }

            // Query project_x_capability
            String sql5 = "SELECT DISTINCT CapabilityID FROM project_x_capability WHERE project_id = ?";
            List<Map<String, Object>> capabilityResults = dbHelper.executeQuery(sql5, List.of(projectId));
            Set<Integer> capabilityIds = extractIds(capabilityResults, "CapabilityID");
            if (!capabilityIds.isEmpty()) {
                relationships.put("CAPABILITY", capabilityIds);
            }

            // Query project_x_businessarea
            String sql6 = "SELECT DISTINCT BusinessArea_ID FROM project_x_businessarea WHERE project_id = ?";
            List<Map<String, Object>> businessAreaResults = dbHelper.executeQuery(sql6, List.of(projectId));
            Set<Integer> businessAreaIds = extractIds(businessAreaResults, "BusinessArea_ID");
            if (!businessAreaIds.isEmpty()) {
                relationships.put("BUSINESS_AREA", businessAreaIds);
            }

        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying project impact relationships: " + e.getMessage());
        }

        return relationships;
    }

    /**
     * Query impact relationships for Glossary objects.
     * 
     * @param glossaryId Glossary ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> queryGlossaryImpactRelationships(Integer glossaryId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (glossaryId == null) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query product_x_glossary
            String sql1 = "SELECT DISTINCT product_id FROM product_x_glossary WHERE GlossaryID = ?";
            List<Map<String, Object>> productResults = dbHelper.executeQuery(sql1, List.of(glossaryId));
            Set<Integer> productIds = extractIds(productResults, "product_id");
            if (!productIds.isEmpty()) {
                relationships.put("PRODUCT", productIds);
            }

            // Query client_x_glossary
            String sql2 = "SELECT DISTINCT Client_ID FROM client_x_glossary WHERE GlossaryID = ?";
            List<Map<String, Object>> clientResults = dbHelper.executeQuery(sql2, List.of(glossaryId));
            Set<Integer> clientIds = extractIds(clientResults, "Client_ID");
            if (!clientIds.isEmpty()) {
                relationships.put("CLIENT", clientIds);
            }

            // Query glossary_x_system
            String sql3 = "SELECT DISTINCT SystemID FROM glossary_x_system WHERE GlossaryID = ?";
            List<Map<String, Object>> systemResults = dbHelper.executeQuery(sql3, List.of(glossaryId));
            Set<Integer> systemIds = extractIds(systemResults, "SystemID");
            if (!systemIds.isEmpty()) {
                relationships.put("SYSTEM", systemIds);
            }

            // glossary_x_project (filter deleted project)
            String sqlProj = "SELECT DISTINCT gxp.Project_ID FROM glossary_x_project gxp " +
                    "JOIN project pj ON gxp.Project_ID = pj.id " +
                    "WHERE gxp.Glossary_ID = ? AND pj.deletedatetime IS NULL";
            Set<Integer> projectIds = extractIds(dbHelper.executeQuery(sqlProj, List.of(glossaryId)), "Project_ID");
            if (!projectIds.isEmpty()) {
                relationships.put("PROJECT", projectIds);
            }

            // glossary_x_process (filter deleted process)
            String sqlProc = "SELECT DISTINCT gxp.Process_ID FROM glossary_x_process gxp " +
                    "JOIN process p ON gxp.Process_ID = p.id " +
                    "WHERE gxp.Glossary_ID = ? AND p.deleteddatetime IS NULL";
            Set<Integer> processIds = extractIds(dbHelper.executeQuery(sqlProc, List.of(glossaryId)), "Process_ID");
            if (!processIds.isEmpty()) {
                relationships.put("PROCESS", processIds);
            }

            // policy_x_glossary
            String sqlPol = "SELECT DISTINCT pxg.PolicyID FROM policy_x_glossary pxg " +
                    "JOIN policy pol ON pxg.PolicyID = pol.ID " +
                    "WHERE pxg.GlossaryID = ? AND pol.DeletedDatetime IS NULL";
            Set<Integer> policyIds = extractIds(dbHelper.executeQuery(sqlPol, List.of(glossaryId)), "PolicyID");
            if (!policyIds.isEmpty()) {
                relationships.put("POLICY", policyIds);
            }

            // capability_x_glossary
            String sqlCap = "SELECT DISTINCT cxg.Capability_ID FROM capability_x_glossary cxg " +
                    "JOIN capability c ON cxg.Capability_ID = c.ID " +
                    "WHERE cxg.Glossary_ID = ? AND c.DeletedDatetime IS NULL";
            Set<Integer> capabilityIds = extractIds(dbHelper.executeQuery(sqlCap, List.of(glossaryId)), "Capability_ID");
            if (!capabilityIds.isEmpty()) {
                relationships.put("CAPABILITY", capabilityIds);
            }

            // businessarea_x_glossary
            String sqlBa = "SELECT DISTINCT bxg.BusinessArea_ID FROM businessarea_x_glossary bxg " +
                    "JOIN business_area ba ON bxg.BusinessArea_ID = ba.ID " +
                    "WHERE bxg.Glossary_ID = ? AND ba.deleteddatetime IS NULL";
            Set<Integer> businessAreaIds = extractIds(dbHelper.executeQuery(sqlBa, List.of(glossaryId)), "BusinessArea_ID");
            if (!businessAreaIds.isEmpty()) {
                relationships.put("BUSINESS_AREA", businessAreaIds);
            }

        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying glossary impact relationships: " + e.getMessage());
        }

        return relationships;
    }

    /**
     * Query impact relationships for Product objects.
     * 
     * @param productId Product ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> queryProductImpactRelationships(Integer productId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (productId == null) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query product_x_client
            String sql1 = "SELECT DISTINCT Client_ID FROM product_x_client WHERE Product_ID = ?";
            List<Map<String, Object>> clientResults = dbHelper.executeQuery(sql1, List.of(productId));
            Set<Integer> clientIds = extractIds(clientResults, "Client_ID");
            if (!clientIds.isEmpty()) {
                relationships.put("CLIENT", clientIds);
            }

            // Query product_x_legal
            String sql2 = "SELECT DISTINCT Legal_ID FROM product_x_legal WHERE Product_ID = ?";
            List<Map<String, Object>> legalResults = dbHelper.executeQuery(sql2, List.of(productId));
            Set<Integer> legalIds = extractIds(legalResults, "Legal_ID");
            if (!legalIds.isEmpty()) {
                relationships.put("LEGAL_ENTITY", legalIds);
            }

            // Query product_x_businessarea
            String sql3 = "SELECT DISTINCT BusinessArea_ID FROM product_x_businessarea WHERE Product_ID = ?";
            List<Map<String, Object>> businessAreaResults = dbHelper.executeQuery(sql3, List.of(productId));
            Set<Integer> businessAreaIds = extractIds(businessAreaResults, "BusinessArea_ID");
            if (!businessAreaIds.isEmpty()) {
                relationships.put("BUSINESS_AREA", businessAreaIds);
            }

            // Query product_x_dataset
            String sql4 = "SELECT DISTINCT Dataset_ID FROM product_x_dataset WHERE Product_ID = ?";
            List<Map<String, Object>> datasetResults = dbHelper.executeQuery(sql4, List.of(productId));
            Set<Integer> datasetIds = extractIds(datasetResults, "Dataset_ID");
            if (!datasetIds.isEmpty()) {
                relationships.put("DATASET", datasetIds);
            }

            // Query product_x_glossary
            String sql5 = "SELECT DISTINCT glossaryid FROM product_x_glossary WHERE productid = ?";
            List<Map<String, Object>> glossaryResults = dbHelper.executeQuery(sql5, List.of(productId));
            Set<Integer> glossaryIds = extractIds(glossaryResults, "glossaryid");
            if (!glossaryIds.isEmpty()) {
                relationships.put("GLOSSARY", glossaryIds);
            }

            // Query product_x_policy
            String sql6 = "SELECT DISTINCT policyid FROM product_x_policy WHERE productid = ?";
            List<Map<String, Object>> policyResults = dbHelper.executeQuery(sql6, List.of(productId));
            Set<Integer> policyIds = extractIds(policyResults, "policyid");
            if (!policyIds.isEmpty()) {
                relationships.put("POLICY", policyIds);
            }

            // Query product_x_process
            String sql7 = "SELECT DISTINCT processid FROM product_x_process WHERE productid = ?";
            List<Map<String, Object>> processResults = dbHelper.executeQuery(sql7, List.of(productId));
            Set<Integer> processIds = extractIds(processResults, "processid");
            if (!processIds.isEmpty()) {
                relationships.put("PROCESS", processIds);
            }

            // Query product_x_project
            String sql8 = "SELECT DISTINCT projectid FROM product_x_project WHERE productid = ?";
            List<Map<String, Object>> projectResults = dbHelper.executeQuery(sql8, List.of(productId));
            Set<Integer> projectIds = extractIds(projectResults, "projectid");
            if (!projectIds.isEmpty()) {
                relationships.put("PROJECT", projectIds);
            }

            // Query product_x_system
            String sql9 = "SELECT DISTINCT System_ID FROM product_x_system WHERE Product_ID = ?";
            List<Map<String, Object>> systemResults = dbHelper.executeQuery(sql9, List.of(productId));
            Set<Integer> systemIds = extractIds(systemResults, "System_ID");
            if (!systemIds.isEmpty()) {
                relationships.put("SYSTEM", systemIds);
            }

        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying product impact relationships: " + e.getMessage());
        }

        return relationships;
    }

    /**
     * Query impact relationships for Capability objects.
     * 
     * @param capabilityId Capability ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> queryCapabilityImpactRelationships(Integer capabilityId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (capabilityId == null) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query capability_x_system (schema: Capability_ID, System_ID)
            String sql1 = "SELECT DISTINCT System_ID FROM capability_x_system WHERE Capability_ID = ?";
            List<Map<String, Object>> systemResults = dbHelper.executeQuery(sql1, List.of(capabilityId));
            Set<Integer> systemIds = extractIds(systemResults, "System_ID");
            if (!systemIds.isEmpty()) {
                relationships.put("SYSTEM", systemIds);
            }

            // Query client_x_capability (if present)
            try {
                String sql2 = "SELECT DISTINCT Client_ID FROM client_x_capability WHERE Capability_ID = ?";
                List<Map<String, Object>> clientResults = dbHelper.executeQuery(sql2, List.of(capabilityId));
                Set<Integer> clientIds = extractIds(clientResults, "Client_ID");
                if (!clientIds.isEmpty()) {
                    relationships.put("CLIENT", clientIds);
                }
            } catch (Exception ignored) {
            }

            // Query product_x_capability (if present)
            try {
                String sql3 = "SELECT DISTINCT product_id FROM product_x_capability WHERE Capability_ID = ?";
                List<Map<String, Object>> productResults = dbHelper.executeQuery(sql3, List.of(capabilityId));
                Set<Integer> productIds = extractIds(productResults, "product_id");
                if (!productIds.isEmpty()) {
                    relationships.put("PRODUCT", productIds);
                }
            } catch (Exception ignored) {
            }

            // Query capability_x_process (schema: Process_ID)
            String sql4 = "SELECT DISTINCT Process_ID FROM capability_x_process WHERE Capability_ID = ?";
            List<Map<String, Object>> processResults = dbHelper.executeQuery(sql4, List.of(capabilityId));
            Set<Integer> processIds = extractIds(processResults, "Process_ID");
            if (!processIds.isEmpty()) {
                relationships.put("PROCESS", processIds);
            }

            // Query capability_x_glossary (schema: Glossary_ID)
            String sql5 = "SELECT DISTINCT Glossary_ID FROM capability_x_glossary WHERE Capability_ID = ?";
            List<Map<String, Object>> glossaryResults = dbHelper.executeQuery(sql5, List.of(capabilityId));
            Set<Integer> glossaryIds = extractIds(glossaryResults, "Glossary_ID");
            if (!glossaryIds.isEmpty()) {
                relationships.put("GLOSSARY", glossaryIds);
            }

            // Query capability_x_businessarea
            String sql6 = "SELECT DISTINCT BusinessArea_ID FROM capability_x_businessarea WHERE Capability_ID = ?";
            List<Map<String, Object>> businessAreaResults = dbHelper.executeQuery(sql6, List.of(capabilityId));
            Set<Integer> businessAreaIds = extractIds(businessAreaResults, "BusinessArea_ID");
            if (!businessAreaIds.isEmpty()) {
                relationships.put("BUSINESS_AREA", businessAreaIds);
            }

            // Query capability_x_legal
            String sql7 = "SELECT DISTINCT Legal_ID FROM capability_x_legal WHERE Capability_ID = ?";
            List<Map<String, Object>> legalResults = dbHelper.executeQuery(sql7, List.of(capabilityId));
            Set<Integer> legalIds = extractIds(legalResults, "Legal_ID");
            if (!legalIds.isEmpty()) {
                relationships.put("LEGAL_ENTITY", legalIds);
            }

        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying capability impact relationships: " + e.getMessage());
        }

        return relationships;
    }

    /**
     * Query impact relationships for Business Area objects.
     * 
     * @param businessAreaId Business Area ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> queryBusinessAreaImpactRelationships(Integer businessAreaId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (businessAreaId == null) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query businessarea_x_process (schema: Process_ID)
            String sql1 = "SELECT DISTINCT Process_ID FROM businessarea_x_process WHERE BusinessArea_ID = ?";
            List<Map<String, Object>> processResults = dbHelper.executeQuery(sql1, List.of(businessAreaId));
            Set<Integer> processIds = extractIds(processResults, "Process_ID");
            if (!processIds.isEmpty()) {
                relationships.put("PROCESS", processIds);
            }

            // Query businessarea_x_system (schema: System_ID)
            String sql2 = "SELECT DISTINCT System_ID FROM businessarea_x_system WHERE BusinessArea_ID = ?";
            List<Map<String, Object>> systemResults = dbHelper.executeQuery(sql2, List.of(businessAreaId));
            Set<Integer> systemIds = extractIds(systemResults, "System_ID");
            if (!systemIds.isEmpty()) {
                relationships.put("SYSTEM", systemIds);
            }

            // Query businessarea_x_glossary
            String sql3 = "SELECT DISTINCT bxg.Glossary_ID FROM businessarea_x_glossary bxg "
                    + "JOIN glossary g ON bxg.Glossary_ID = g.ID WHERE bxg.BusinessArea_ID = ? "
                    + "AND g.Deleted_datetime IS NULL";
            List<Map<String, Object>> glossaryResults = dbHelper.executeQuery(sql3, List.of(businessAreaId));
            Set<Integer> glossaryIds = extractIds(glossaryResults, "Glossary_ID");
            if (!glossaryIds.isEmpty()) {
                relationships.put("GLOSSARY", glossaryIds);
            }

        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying business area impact relationships: " + e.getMessage());
        }

        return relationships;
    }

    /**
     * Query impact relationships for Interface objects.
     * 
     * @param interfaceId Interface ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> queryInterfaceImpactRelationships(Integer interfaceId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (interfaceId == null) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query process_x_interface
            String sql1 = "SELECT DISTINCT process_id FROM process_x_interface WHERE InterfaceID = ?";
            List<Map<String, Object>> processResults = dbHelper.executeQuery(sql1, List.of(interfaceId));
            Set<Integer> processIds = extractIds(processResults, "process_id");
            if (!processIds.isEmpty()) {
                relationships.put("PROCESS", processIds);
            }

            // Linked glossaries (impact / glossary tab)
            String sqlG = "SELECT DISTINCT Glossary FROM interface_x_glossary WHERE Interface = ?";
            List<Map<String, Object>> glossaryResults = dbHelper.executeQuery(sqlG, List.of(interfaceId));
            Set<Integer> glossaryIds = extractIds(glossaryResults, "Glossary");
            if (!glossaryIds.isEmpty()) {
                relationships.put("GLOSSARY", glossaryIds);
            }

        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying interface impact relationships: " + e.getMessage());
        }

        return relationships;
    }

    /**
     * Query impact relationships for Regulation objects.
     * 
     * @param regulationId Regulation ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> queryRegulationImpactRelationships(Integer regulationId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (regulationId == null) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query regulation_x_policy
            String sql1 = "SELECT DISTINCT Policy_ID FROM regulation_x_policy WHERE RegulationID = ?";
            List<Map<String, Object>> policyResults = dbHelper.executeQuery(sql1, List.of(regulationId));
            Set<Integer> policyIds = extractIds(policyResults, "Policy_ID");
            if (!policyIds.isEmpty()) {
                relationships.put("POLICY", policyIds);
            }

            // Query regulation_x_product
            String sql2 = "SELECT DISTINCT product_id FROM regulation_x_product WHERE RegulationID = ?";
            List<Map<String, Object>> productResults = dbHelper.executeQuery(sql2, List.of(regulationId));
            Set<Integer> productIds = extractIds(productResults, "product_id");
            if (!productIds.isEmpty()) {
                relationships.put("PRODUCT", productIds);
            }

            // Query regulation_x_project
            String sql3 = "SELECT DISTINCT project_id FROM regulation_x_project WHERE RegulationID = ?";
            List<Map<String, Object>> projectResults = dbHelper.executeQuery(sql3, List.of(regulationId));
            Set<Integer> projectIds = extractIds(projectResults, "project_id");
            if (!projectIds.isEmpty()) {
                relationships.put("PROJECT", projectIds);
            }

        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying regulation impact relationships: " + e.getMessage());
        }

        return relationships;
    }

    /**
     * Query impact relationships for Legal Entity objects.
     * 
     * @param legalEntityId Legal Entity ID
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> queryLegalEntityImpactRelationships(Integer legalEntityId) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (legalEntityId == null) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query legal_x_geography (schema: Geography_ID)
            String sql1 = "SELECT DISTINCT Geography_ID FROM legal_x_geography WHERE Legal_ID = ?";
            List<Map<String, Object>> geographyResults = dbHelper.executeQuery(sql1, List.of(legalEntityId));
            Set<Integer> geographyIds = extractIds(geographyResults, "Geography_ID");
            if (!geographyIds.isEmpty()) {
                relationships.put("GEOGRAPHY", geographyIds);
            }

        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying legal entity impact relationships: " + e.getMessage());
        }

        return relationships;
    }

    /** Systems linked to a legal entity via {@code system_x_legal}. */
    private Set<Integer> queryLegalLinkedSystemIds(Integer legalId) throws SQLException {
        Set<Integer> ids = new LinkedHashSet<>();
        if (legalId == null) {
            return ids;
        }
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper =
                    new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT DISTINCT sxl.System_ID FROM system_x_legal sxl "
                    + "JOIN system s ON sxl.System_ID = s.id "
                    + "WHERE sxl.Legal_ID = ? AND s.Deleted_datetime IS NULL";
            ids.addAll(extractIds(dbHelper.executeQuery(sql, List.of(legalId)), "System_ID"));
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] queryLegalLinkedSystemIds: " + e.getMessage());
        }
        return ids;
    }

    /** Datasets linked to a legal entity via {@code dataset_x_legal}. */
    private Set<Integer> queryLegalLinkedDatasetIds(Integer legalId) throws SQLException {
        Set<Integer> ids = new LinkedHashSet<>();
        if (legalId == null) {
            return ids;
        }
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper =
                    new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT DISTINCT dxl.Dataset_ID FROM dataset_x_legal dxl "
                    + "JOIN dataset d ON dxl.Dataset_ID = d.ID "
                    + "WHERE dxl.Legal_ID = ? AND d.DeletedDatetime IS NULL";
            ids.addAll(extractIds(dbHelper.executeQuery(sql, List.of(legalId)), "Dataset_ID"));
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] queryLegalLinkedDatasetIds: " + e.getMessage());
        }
        return ids;
    }

    /**
     * Query same-type relationships for Glossary objects (glossary_x_glossary).
     * Returns other glossaries related to this glossary.
     * 
     * @param glossaryId Glossary ID
     * @return Set of related glossary IDs
     * @throws SQLException if database error occurs
     */
    private Set<Integer> queryGlossaryRelationships(Integer glossaryId) throws SQLException {
        Set<Integer> relatedGlossaries = new HashSet<>();
        if (glossaryId == null) {
            return relatedGlossaries;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query glossary_x_glossary (bidirectional)
            String sql = "SELECT DISTINCT " +
                         "CASE WHEN GlossaryID_1 = ? THEN GlossaryID_2 " +
                         "     WHEN GlossaryID_2 = ? THEN GlossaryID_1 " +
                         "END AS related_id " +
                         "FROM glossary_x_glossary " +
                         "WHERE (GlossaryID_1 = ? OR GlossaryID_2 = ?) " +
                         "AND DeletedDatetime IS NULL";

            List<Map<String, Object>> results = dbHelper.executeQuery(sql,
                    List.of(glossaryId, glossaryId, glossaryId, glossaryId));

            relatedGlossaries = extractIds(results, "related_id");
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying glossary relationships: " + e.getMessage());
        }

        return relatedGlossaries;
    }

    /**
     * Query same-type relationships for Policy objects (policy_x_policy).
     * Returns other policies related to this policy.
     * 
     * @param policyId Policy ID
     * @return Set of related policy IDs
     * @throws SQLException if database error occurs
     */
    private Set<Integer> queryPolicyRelationships(Integer policyId) throws SQLException {
        Set<Integer> relatedPolicies = new HashSet<>();
        if (policyId == null) {
            return relatedPolicies;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query policy_x_policy (bidirectional)
            String sql = "SELECT DISTINCT " +
                         "CASE WHEN sourceid = ? THEN targetid " +
                         "     WHEN targetid = ? THEN sourceid " +
                         "END AS related_id " +
                         "FROM policy_x_policy " +
                         "WHERE (sourceid = ? OR targetid = ?)";

            List<Map<String, Object>> results = dbHelper.executeQuery(sql,
                    List.of(policyId, policyId, policyId, policyId));

            relatedPolicies = extractIds(results, "related_id");
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying policy relationships: " + e.getMessage());
        }

        return relatedPolicies;
    }

    /**
     * Query same-type relationships for Regulation objects (regulation_x_regulation).
     * Returns other regulations related to this regulation.
     * 
     * @param regulationId Regulation ID
     * @return Set of related regulation IDs
     * @throws SQLException if database error occurs
     */
    private Set<Integer> queryRegulationRelationships(Integer regulationId) throws SQLException {
        Set<Integer> relatedRegulations = new HashSet<>();
        if (regulationId == null) {
            return relatedRegulations;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query regulation_x_regulation (bidirectional)
            String sql = "SELECT DISTINCT " +
                         "CASE WHEN RegulationID_1 = ? THEN RegulationID_2 " +
                         "     WHEN RegulationID_2 = ? THEN RegulationID_1 " +
                         "END AS related_id " +
                         "FROM regulation_x_regulation " +
                         "WHERE (RegulationID_1 = ? OR RegulationID_2 = ?) " +
                         "AND DeletedDatetime IS NULL";

            List<Map<String, Object>> results = dbHelper.executeQuery(sql,
                    List.of(regulationId, regulationId, regulationId, regulationId));

            relatedRegulations = extractIds(results, "related_id");
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying regulation relationships: " + e.getMessage());
        }

        return relatedRegulations;
    }

    /**
     * Query same-type relationships for Dataset objects (via attribute_x_attribute).
     * Returns other datasets that have attributes related to this dataset's attributes.
     * 
     * @param datasetId Dataset ID
     * @return Set of related dataset IDs
     * @throws SQLException if database error occurs
     */
    private Set<Integer> queryDatasetRelationships(Integer datasetId) throws SQLException {
        Set<Integer> relatedDatasets = new HashSet<>();
        if (datasetId == null) {
            return relatedDatasets;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query attribute_x_attribute to find related datasets
            // Get attributes in this dataset, then find related attributes, then their datasets
            String sql = "SELECT DISTINCT " +
                         "CASE WHEN a1.Dataset_ID = ? THEN a2.Dataset_ID " +
                         "     WHEN a2.Dataset_ID = ? THEN a1.Dataset_ID " +
                         "END AS related_dataset_id " +
                         "FROM attribute_x_attribute axa " +
                         "JOIN attribute a1 ON axa.Attribute_ID_1 = a1.ID " +
                         "JOIN attribute a2 ON axa.Attribute_ID_2 = a2.ID " +
                         "WHERE (a1.Dataset_ID = ? OR a2.Dataset_ID = ?) " +
                         "AND axa.DeletedDatetime IS NULL " +
                         "AND a1.DeletedDatetime IS NULL " +
                         "AND a2.DeletedDatetime IS NULL";

            List<Map<String, Object>> results = dbHelper.executeQuery(sql,
                    List.of(datasetId, datasetId, datasetId, datasetId));

            relatedDatasets = extractIds(results, "related_dataset_id");
            // Remove self-reference
            relatedDatasets.remove(datasetId);
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying dataset relationships: " + e.getMessage());
        }

        return relatedDatasets;
    }

    /**
     * Query same-type relationships for Project objects (project_x_project).
     * Returns other projects related to this project.
     * 
     * @param projectId Project ID
     * @return Set of related project IDs
     * @throws SQLException if database error occurs
     */
    private Set<Integer> queryProjectRelationships(Integer projectId) throws SQLException {
        Set<Integer> relatedProjects = new HashSet<>();
        if (projectId == null) {
            return relatedProjects;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query project_x_project (bidirectional)
            String sql = "SELECT DISTINCT " +
                         "CASE WHEN project_ID_1 = ? THEN project_ID_2 " +
                         "     WHEN project_ID_2 = ? THEN project_ID_1 " +
                         "END AS related_id " +
                         "FROM project_x_project " +
                         "WHERE (project_ID_1 = ? OR project_ID_2 = ?) " +
                         "AND DeletedDatetime IS NULL";

            List<Map<String, Object>> results = dbHelper.executeQuery(sql,
                    List.of(projectId, projectId, projectId, projectId));

            relatedProjects = extractIds(results, "related_id");
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying project relationships: " + e.getMessage());
        }

        return relatedProjects;
    }

    /**
     * Query systems linked to a dataset via MasterSource field.
     * 
     * @param datasetId Dataset ID
     * @return Set of system IDs
     * @throws SQLException if database error occurs
     */
    private Set<Integer> queryDatasetSystems(Integer datasetId) throws SQLException {
        Set<Integer> systemIds = new HashSet<>();
        if (datasetId == null) {
            return systemIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String sql = "SELECT DISTINCT MasterSource FROM dataset WHERE ID = ? AND MasterSource IS NOT NULL";
            List<Object> params = List.of(datasetId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("MasterSource");
                if (id instanceof Integer) {
                    systemIds.add((Integer) id);
                } else if (id instanceof Number) {
                    systemIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println(
                    "[UnisonSearchService] Error querying systems for dataset " + datasetId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return systemIds;
    }

    /**
     * Query relationships for systems (processes, projects, products, business
     * areas, capabilities).
     * 
     * @param systemIds Set of system IDs
     * @return Map of facet type to set of object IDs
     * @throws SQLException if database error occurs
     */
    private Map<String, Set<Integer>> querySystemRelationships(Set<Integer> systemIds) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (systemIds == null || systemIds.isEmpty()) {
            return relationships;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(systemIds.size(), "?"));
            List<Object> params = new ArrayList<>(systemIds);

            // Query processes (filter deleted)
            String processSql = "SELECT DISTINCT pxs.process_id FROM process_x_system pxs " +
                    "JOIN process p ON pxs.process_id = p.id " +
                    "WHERE pxs.system_id IN (" + placeholders + ") AND p.deleteddatetime IS NULL";
            List<Map<String, Object>> processResults = dbHelper.executeQuery(processSql, params);
            Set<Integer> processIds = new HashSet<>();
            for (Map<String, Object> row : processResults) {
                // Try exact match first, then case-insensitive fallback
                Object id = row.get("process_id");
                if (id == null) {
                    id = row.get("processid");
                }
                if (id == null) {
                    // Try case-insensitive search
                    for (String key : row.keySet()) {
                        if (key != null && key.equalsIgnoreCase("process_id")) {
                            id = row.get(key);
                            break;
                        }
                    }
                }
                if (id instanceof Integer) {
                    processIds.add((Integer) id);
                } else if (id instanceof Number) {
                    processIds.add(((Number) id).intValue());
                }
            }
            if (!processIds.isEmpty()) {
                relationships.put("PROCESS", processIds);
            }

            // Query projects (filter deleted)
            String projectSql = "SELECT DISTINCT pxs.projectid FROM project_x_system pxs " +
                    "JOIN project pj ON pxs.projectid = pj.id " +
                    "WHERE pxs.systemid IN (" + placeholders + ") AND pj.deletedatetime IS NULL";
            List<Map<String, Object>> projectResults = dbHelper.executeQuery(projectSql, params);
            Set<Integer> projectIds = new HashSet<>();
            for (Map<String, Object> row : projectResults) {
                Object id = row.get("projectid");
                if (id instanceof Integer) {
                    projectIds.add((Integer) id);
                } else if (id instanceof Number) {
                    projectIds.add(((Number) id).intValue());
                }
            }
            if (!projectIds.isEmpty()) {
                relationships.put("PROJECT", projectIds);
            }

            // Query products (filter deleted)
            String productSql = "SELECT DISTINCT pxs.Product_ID FROM product_x_system pxs " +
                    "JOIN product prd ON pxs.Product_ID = prd.id " +
                    "WHERE pxs.System_ID IN (" + placeholders + ") AND prd.DeletedDatetime IS NULL";
            List<Map<String, Object>> productResults = dbHelper.executeQuery(productSql, params);
            Set<Integer> productIds = new HashSet<>();
            for (Map<String, Object> row : productResults) {
                Object id = row.get("Product_ID");
                if (id instanceof Integer) {
                    productIds.add((Integer) id);
                } else if (id instanceof Number) {
                    productIds.add(((Number) id).intValue());
                }
            }
            if (!productIds.isEmpty()) {
                relationships.put("PRODUCT", productIds);
            }

            // Query business areas (filter deleted)
            String businessAreaSql = "SELECT DISTINCT bxs.BusinessArea_ID FROM businessarea_x_system bxs " +
                    "JOIN business_area ba ON bxs.BusinessArea_ID = ba.ID " +
                    "WHERE bxs.System_ID IN (" + placeholders + ") AND ba.deletedatetime IS NULL";
            List<Map<String, Object>> businessAreaResults = dbHelper.executeQuery(businessAreaSql, params);
            Set<Integer> businessAreaIds = new HashSet<>();
            for (Map<String, Object> row : businessAreaResults) {
                Object id = row.get("BusinessArea_ID");
                if (id instanceof Integer) {
                    businessAreaIds.add((Integer) id);
                } else if (id instanceof Number) {
                    businessAreaIds.add(((Number) id).intValue());
                }
            }
            if (!businessAreaIds.isEmpty()) {
                relationships.put("BUSINESS_AREA", businessAreaIds);
            }

            // Query capabilities (filter deleted)
            String capabilitySql = "SELECT DISTINCT cxs.Capability_ID FROM capability_x_system cxs " +
                    "JOIN capability c ON cxs.Capability_ID = c.ID " +
                    "WHERE cxs.System_ID IN (" + placeholders + ") AND c.DeletedDatetime IS NULL";
            List<Map<String, Object>> capabilityResults = dbHelper.executeQuery(capabilitySql, params);
            Set<Integer> capabilityIds = new HashSet<>();
            for (Map<String, Object> row : capabilityResults) {
                Object id = row.get("Capability_ID");
                if (id instanceof Integer) {
                    capabilityIds.add((Integer) id);
                } else if (id instanceof Number) {
                    capabilityIds.add(((Number) id).intValue());
                }
            }
            if (!capabilityIds.isEmpty()) {
                relationships.put("CAPABILITY", capabilityIds);
            }

            // Policies linked to system (filter deleted)
            String policySql = "SELECT DISTINCT pxs.Policy_ID FROM policy_x_system pxs " +
                    "JOIN policy pol ON pxs.Policy_ID = pol.ID " +
                    "WHERE pxs.System_ID IN (" + placeholders + ") AND pol.DeletedDatetime IS NULL";
            List<Map<String, Object>> policyResults = dbHelper.executeQuery(policySql, params);
            Set<Integer> policyIds = new HashSet<>();
            for (Map<String, Object> row : policyResults) {
                Object id = row.get("Policy_ID");
                if (id == null) {
                    id = row.get("PolicyID");
                }
                if (id instanceof Integer) {
                    policyIds.add((Integer) id);
                } else if (id instanceof Number) {
                    policyIds.add(((Number) id).intValue());
                }
            }
            if (!policyIds.isEmpty()) {
                relationships.put("POLICY", policyIds);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying system relationships: " + e.getMessage());
            e.printStackTrace();
        }

        return relationships;
    }

    /**
     * Load Process rows for given process IDs.
     * 
     * @param processIds Set of process IDs
     * @return List of process row maps
     */
    private List<Map<String, Object>> loadProcessRows(Set<Integer> processIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (processIds == null || processIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(processIds.size(), "?"));
            String sql = "SELECT id, primaryname, description, refnumber, parentid, deleteddatetime " +
                    "FROM process " +
                    "WHERE id IN (" + placeholders + ") " +
                    "AND deleteddatetime IS NULL";

            // Get active nobject_id values to exclude (temporary cloned rows from active CRs)
            Set<Integer> excludedIds = getActiveNObjectIdsForFacet("process");

            List<Object> params = new ArrayList<>(processIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object idObj = row.get("id");
                if (idObj == null) continue;
                
                int id = idObj instanceof Integer ? (Integer) idObj : ((Number) idObj).intValue();
                
                // Skip if this is a temporary cloned row (nobject_id)
                if (excludedIds.contains(id)) {
                    continue;
                }
                
                Map<String, Object> processRow = new HashMap<>();
                processRow.put("ID", id);
                processRow.put("id", id);
                processRow.put("PrimaryName", row.get("primaryname"));
                processRow.put("primaryname", row.get("primaryname"));
                processRow.put("Description", row.get("description"));
                processRow.put("description", row.get("description"));
                processRow.put("RefNumber", row.get("refnumber"));
                processRow.put("refnumber", row.get("refnumber"));
                // Get parentid with case-insensitive fallback
                Object parentId = row.get("parentid");
                if (parentId == null) {
                    parentId = row.get("Parent_ID");
                }
                if (parentId == null) {
                    // Try case-insensitive search
                    for (String key : row.keySet()) {
                        if (key != null && key.equalsIgnoreCase("parentid")) {
                            parentId = row.get(key);
                            break;
                        }
                    }
                }
                processRow.put("Parent_ID", parentId);
                processRow.put("parent_id", parentId);
                rows.add(processRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadProcessRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Project rows for given project IDs.
     * 
     * @param projectIds Set of project IDs
     * @return List of project row maps
     */
    private List<Map<String, Object>> loadProjectRows(Set<Integer> projectIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (projectIds == null || projectIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(projectIds.size(), "?"));
            String sql = "SELECT id, primaryname, description, refnumber, parentid, deletedatetime " +
                    "FROM project " +
                    "WHERE id IN (" + placeholders + ") " +
                    "AND deletedatetime IS NULL";

            List<Object> params = new ArrayList<>(projectIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> projectRow = new HashMap<>();
                Object id = row.get("id");
                if (id != null) {
                    projectRow.put("id", id);
                    projectRow.put("ID", id);
                }
                projectRow.put("PrimaryName", row.get("primaryname"));
                projectRow.put("primaryname", row.get("primaryname"));
                projectRow.put("Description", row.get("description"));
                projectRow.put("description", row.get("description"));
                projectRow.put("RefNumber", row.get("refnumber"));
                projectRow.put("refnumber", row.get("refnumber"));
                // Get parentid with case-insensitive fallback
                Object parentId = row.get("parentid");
                if (parentId == null) {
                    parentId = row.get("Parent_ID");
                }
                if (parentId == null) {
                    // Try case-insensitive search
                    for (String key : row.keySet()) {
                        if (key != null && key.equalsIgnoreCase("parentid")) {
                            parentId = row.get(key);
                            break;
                        }
                    }
                }
                projectRow.put("Parent_ID", parentId);
                projectRow.put("parent_id", parentId);
                rows.add(projectRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadProjectRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Product rows for given product IDs.
     * 
     * @param productIds Set of product IDs
     * @return List of product row maps
     */
    private List<Map<String, Object>> loadProductRows(Set<Integer> productIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (productIds == null || productIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(productIds.size(), "?"));
            // Only query columns that exist (RefNumber and ParentID may not exist)
            String sql = "SELECT ID, PrimaryName, Description, DeletedDatetime " +
                    "FROM product " +
                    "WHERE ID IN (" + placeholders + ") " +
                    "AND DeletedDatetime IS NULL";

            List<Object> params = new ArrayList<>(productIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> productRow = new HashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    productRow.put("ID", id);
                    productRow.put("id", id);
                }
                productRow.put("PrimaryName", row.get("PrimaryName"));
                productRow.put("primaryname", row.get("PrimaryName"));
                productRow.put("Description", row.get("Description"));
                productRow.put("description", row.get("Description"));
                // RefNumber and ParentID columns don't exist in product table
                rows.add(productRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadProductRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Client rows for given client IDs.
     * 
     * @param clientIds Set of client IDs
     * @return List of client row maps
     */
    private List<Map<String, Object>> loadClientRows(Set<Integer> clientIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (clientIds == null || clientIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(clientIds.size(), "?"));
            String sql = "SELECT ID, PrimaryName, Description, RefNumber, DeletedDatetime " +
                    "FROM client " +
                    "WHERE ID IN (" + placeholders + ") " +
                    "AND DeletedDatetime IS NULL";

            List<Object> params = new ArrayList<>(clientIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> clientRow = new HashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    clientRow.put("ID", id);
                    clientRow.put("id", id);
                }
                clientRow.put("PrimaryName", row.get("PrimaryName"));
                clientRow.put("primaryname", row.get("PrimaryName"));
                clientRow.put("Description", row.get("Description"));
                clientRow.put("description", row.get("Description"));
                clientRow.put("RefNumber", row.get("RefNumber"));
                clientRow.put("refnumber", row.get("RefNumber"));
                rows.add(clientRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadClientRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Business Area rows for given business area IDs.
     * 
     * @param businessAreaIds Set of business area IDs
     * @return List of business area row maps
     */
    private List<Map<String, Object>> loadBusinessAreaRows(Set<Integer> businessAreaIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (businessAreaIds == null || businessAreaIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(businessAreaIds.size(), "?"));
            // Only query columns that exist (business_area doesn't have soft delete)
            String sql = "SELECT ID, PrimaryName, Description, Parent_ID " +
                    "FROM business_area " +
                    "WHERE ID IN (" + placeholders + ")";

            List<Object> params = new ArrayList<>(businessAreaIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> businessAreaRow = new HashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    businessAreaRow.put("ID", id);
                    businessAreaRow.put("id", id);
                }
                businessAreaRow.put("PrimaryName", row.get("PrimaryName"));
                businessAreaRow.put("primaryname", row.get("PrimaryName"));
                businessAreaRow.put("Description", row.get("Description"));
                businessAreaRow.put("description", row.get("Description"));
                // RefNumber column doesn't exist in business_area table
                businessAreaRow.put("Parent_ID", row.get("Parent_ID"));
                businessAreaRow.put("parent_id", row.get("Parent_ID"));
                rows.add(businessAreaRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadBusinessAreaRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Capability rows for given capability IDs.
     * 
     * @param capabilityIds Set of capability IDs
     * @return List of capability row maps
     */
    private List<Map<String, Object>> loadCapabilityRows(Set<Integer> capabilityIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (capabilityIds == null || capabilityIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(capabilityIds.size(), "?"));
            String sql = "SELECT ID, PrimaryName, Description, RefNumber, Parent_ID, DeletedDatetime " +
                    "FROM capability " +
                    "WHERE ID IN (" + placeholders + ") " +
                    "AND DeletedDatetime IS NULL";

            List<Object> params = new ArrayList<>(capabilityIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> capabilityRow = new HashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    capabilityRow.put("ID", id);
                    capabilityRow.put("id", id);
                }
                capabilityRow.put("PrimaryName", row.get("PrimaryName"));
                capabilityRow.put("primaryname", row.get("PrimaryName"));
                capabilityRow.put("Description", row.get("Description"));
                capabilityRow.put("description", row.get("Description"));
                capabilityRow.put("RefNumber", row.get("RefNumber"));
                capabilityRow.put("refnumber", row.get("RefNumber"));
                capabilityRow.put("Parent_ID", row.get("Parent_ID"));
                capabilityRow.put("parent_id", row.get("Parent_ID"));
                rows.add(capabilityRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadCapabilityRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Enrich search results with related glossaries for datasets.
     * For each dataset, finds:
     * - Direct glossaries: from dataset.glossary field (direct FK)
     * - Attribute glossaries: from attributes in the dataset (via attribute.Glossary_ID field, direct FK)
     * 
     * @param results Current search results
     * @return Enriched results with GLOSSARY facet added
     * @throws SQLException if database error occurs
     */
    private Map<String, FacetResult> enrichDatasetWithGlossaries(Map<String, FacetResult> results) throws SQLException {
        if (results == null || results.isEmpty()) {
            return results;
        }

        // Check if we have datasets in the results
        FacetResult datasetResult = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("DATASET") ||
                    facetId.toUpperCase().equals("DATA_SETS") ||
                    facetId.toUpperCase().equals("DATASETS"))) {
                datasetResult = entry.getValue();
                break;
            }
        }

        if (datasetResult == null || datasetResult.getIds() == null || datasetResult.getIds().isEmpty()) {
            return results; // No datasets to enrich
        }

        // Collect all glossary IDs
        Set<Integer> allGlossaryIds = new HashSet<>();
        Map<Integer, Integer> glossaryDepth = new HashMap<>();

        // Process each dataset
        for (Integer datasetId : datasetResult.getIds()) {
            try {
                // Query direct glossaries (depth 1)
                Set<Integer> directGlossaries = queryDatasetDirectGlossaries(datasetId);
                for (Integer glossaryId : directGlossaries) {
                    allGlossaryIds.add(glossaryId);
                    if (!glossaryDepth.containsKey(glossaryId)) {
                        glossaryDepth.put(glossaryId, 1);
                    }
                }

                // Query attributes in this dataset
                Set<Integer> attributeIds = queryDatasetAttributes(datasetId);

                if (!attributeIds.isEmpty()) {
                    // Query glossaries from attributes (depth 2)
                    Set<Integer> attributeGlossaries = queryAttributeGlossaries(attributeIds);
                    for (Integer glossaryId : attributeGlossaries) {
                        allGlossaryIds.add(glossaryId);
                        if (!glossaryDepth.containsKey(glossaryId)) {
                            glossaryDepth.put(glossaryId, 2);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[UnisonSearchService] Error enriching dataset " + datasetId + " with glossaries: "
                        + e.getMessage());
                e.printStackTrace();
            }
        }

        // Add Glossary results
        if (!allGlossaryIds.isEmpty()) {
            String glossaryFacetId = "GLOSSARY";
            FacetResult existingGlossaryResult = results.get(glossaryFacetId);

            Set<Integer> mergedGlossaryIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedGlossaryDepth = new HashMap<>();

            if (existingGlossaryResult != null && existingGlossaryResult.getIds() != null) {
                mergedGlossaryIds.addAll(existingGlossaryResult.getIds());
                if (existingGlossaryResult.getDepthById() != null) {
                    mergedGlossaryDepth.putAll(existingGlossaryResult.getDepthById());
                }
            }

            mergedGlossaryIds.addAll(allGlossaryIds);
            // Merge depths, keeping minimum depth when duplicate
            for (Map.Entry<Integer, Integer> entry : glossaryDepth.entrySet()) {
                Integer glossaryId = entry.getKey();
                Integer depth = entry.getValue();
                mergedGlossaryDepth.merge(glossaryId, depth,
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            // Load Glossary rows
            List<Map<String, Object>> glossaryRows = loadGlossaryRows(mergedGlossaryIds);
            int glossaryTotal = computeAccessibleTotalCount(glossaryFacetId,
                    glossaryRows != null ? glossaryRows.size() : 0);

            results.put(glossaryFacetId,
                    new FacetResult(mergedGlossaryIds, true, mergedGlossaryDepth, glossaryRows, glossaryTotal));
        }

        return results;
    }

    /**
     * Query direct glossaries for a dataset.
     * Includes glossaries from dataset.glossary field (direct FK).
     * 
     * @param datasetId Dataset ID
     * @return Set of glossary IDs
     * @throws SQLException if database error occurs
     */
    private Set<Integer> queryDatasetDirectGlossaries(Integer datasetId) throws SQLException {
        Set<Integer> glossaryIds = new HashSet<>();
        if (datasetId == null) {
            return glossaryIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query from dataset.glossary field
            String directSql = "SELECT DISTINCT glossary FROM dataset WHERE ID = ? AND glossary IS NOT NULL";
            List<Object> params = List.of(datasetId);
            List<Map<String, Object>> directResults = dbHelper.executeQuery(directSql, params);
            for (Map<String, Object> row : directResults) {
                Object id = row.get("glossary");
                if (id instanceof Integer) {
                    glossaryIds.add((Integer) id);
                } else if (id instanceof Number) {
                    glossaryIds.add(((Number) id).intValue());
                }
            }

            // REMOVED: glossary_x_dataset table does not exist in database
            // The relationship between dataset and glossary is handled through dataset.glossary field (direct FK)
            // try {
            //     String junctionSql = "SELECT DISTINCT Glossary_ID FROM glossary_x_dataset WHERE Dataset_ID = ?";
            //     List<Map<String, Object>> junctionResults = dbHelper.executeQuery(junctionSql, params);
            //     for (Map<String, Object> row : junctionResults) {
            //         Object id = row.get("Glossary_ID");
            //         if (id instanceof Integer) {
            //             glossaryIds.add((Integer) id);
            //         } else if (id instanceof Number) {
            //             glossaryIds.add(((Number) id).intValue());
            //         }
            //     }
            // } catch (SQLException e) {
            //     // Table doesn't exist, skip this query silently
            //     // Only log if it's not a "table doesn't exist" error
            //     if (e.getMessage() != null && !e.getMessage().contains("doesn't exist")) {
            //         System.err.println("[UnisonSearchService] Error querying glossary_x_dataset for dataset "
            //                 + datasetId + ": " + e.getMessage());
            //     }
            // } catch (Exception e) {
            //     // Table doesn't exist, skip this query silently
            //     // Only log if it's not a "table doesn't exist" error
            //     if (e.getMessage() != null && !e.getMessage().contains("doesn't exist")) {
            //         System.err.println("[UnisonSearchService] Error querying glossary_x_dataset for dataset "
            //                 + datasetId + ": " + e.getMessage());
            //     }
            // }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying direct glossaries for dataset " + datasetId + ": "
                    + e.getMessage());
            e.printStackTrace();
        }

        return glossaryIds;
    }

    /**
     * Query attributes in a dataset.
     * 
     * @param datasetId Dataset ID
     * @return Set of attribute IDs
     * @throws SQLException if database error occurs
     */
    private Set<Integer> queryDatasetAttributes(Integer datasetId) throws SQLException {
        Set<Integer> attributeIds = new HashSet<>();
        if (datasetId == null) {
            return attributeIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String sql = "SELECT DISTINCT ID FROM attribute WHERE Dataset_ID = ? AND DeletedDatetime IS NULL";
            List<Object> params = List.of(datasetId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("ID");
                if (id instanceof Integer) {
                    attributeIds.add((Integer) id);
                } else if (id instanceof Number) {
                    attributeIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println(
                    "[UnisonSearchService] Error querying attributes for dataset " + datasetId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return attributeIds;
    }

    /**
     * Query glossaries linked to attributes.
     * Includes glossaries from attribute.Glossary_ID field (direct FK).
     * 
     * @param attributeIds Set of attribute IDs
     * @return Set of glossary IDs
     * @throws SQLException if database error occurs
     */
    /**
     * Query glossaries for a single attribute.
     */
    private Set<Integer> queryAttributeGlossaries(Integer attributeId) throws SQLException {
        Set<Integer> glossaryIds = new HashSet<>();
        if (attributeId == null) {
            return glossaryIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query from attribute.Glossary_ID field
            String sql = "SELECT DISTINCT Glossary_ID FROM attribute WHERE ID = ? AND Glossary_ID IS NOT NULL AND DeletedDatetime IS NULL";
            List<Object> params = List.of(attributeId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);
            for (Map<String, Object> row : results) {
                Object id = row.get("Glossary_ID");
                if (id instanceof Integer) {
                    glossaryIds.add((Integer) id);
                } else if (id instanceof Number) {
                    glossaryIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying glossaries for attribute " + attributeId + ": " + e.getMessage());
        }

        return glossaryIds;
    }

    /**
     * Query glossaries for multiple attributes.
     */
    private Set<Integer> queryAttributeGlossaries(Set<Integer> attributeIds) throws SQLException {
        Set<Integer> glossaryIds = new HashSet<>();
        if (attributeIds == null || attributeIds.isEmpty()) {
            return glossaryIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(attributeIds.size(), "?"));
            List<Object> params = new ArrayList<>(attributeIds);

            // Query from attribute.Glossary_ID field
            String directSql = "SELECT DISTINCT Glossary_ID FROM attribute WHERE ID IN (" + placeholders
                    + ") AND Glossary_ID IS NOT NULL";
            List<Map<String, Object>> directResults = dbHelper.executeQuery(directSql, params);
            for (Map<String, Object> row : directResults) {
                Object id = row.get("Glossary_ID");
                if (id instanceof Integer) {
                    glossaryIds.add((Integer) id);
                } else if (id instanceof Number) {
                    glossaryIds.add(((Number) id).intValue());
                }
            }

            // REMOVED: glossary_x_attribute table does not exist in database
            // The relationship between attribute and glossary is handled through attribute.Glossary_ID field (direct FK)
            // try {
            //     String junctionSql = "SELECT DISTINCT Glossary_ID FROM glossary_x_attribute WHERE AttributeID IN ("
            //             + placeholders + ")";
            //     List<Map<String, Object>> junctionResults = dbHelper.executeQuery(junctionSql, params);
            //     for (Map<String, Object> row : junctionResults) {
            //         Object id = row.get("Glossary_ID");
            //         if (id instanceof Integer) {
            //             glossaryIds.add((Integer) id);
            //         } else if (id instanceof Number) {
            //             glossaryIds.add(((Number) id).intValue());
            //         }
            //     }
            // } catch (SQLException e) {
            //     // Table doesn't exist, skip this query silently
            //     // Only log if it's not a "table doesn't exist" error
            //     if (e.getMessage() != null && !e.getMessage().contains("doesn't exist")) {
            //         System.err.println("[UnisonSearchService] Error querying glossary_x_attribute: " + e.getMessage());
            //     }
            // } catch (Exception e) {
            //     // Table doesn't exist, skip this query silently
            //     // Only log if it's not a "table doesn't exist" error
            //     if (e.getMessage() != null && !e.getMessage().contains("doesn't exist")) {
            //         System.err.println("[UnisonSearchService] Error querying glossary_x_attribute: " + e.getMessage());
            //     }
            // }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying glossaries for attributes: " + e.getMessage());
            e.printStackTrace();
        }

        return glossaryIds;
    }

    /**
     * Load Glossary rows for given glossary IDs.
     * 
     * @param glossaryIds Set of glossary IDs
     * @return List of glossary row maps
     */
    private List<Map<String, Object>> loadGlossaryRows(Set<Integer> glossaryIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (glossaryIds == null || glossaryIds.isEmpty()) {
            return rows;
        }

        try {
            // Get active nobject_id values to exclude (temporary cloned rows from active CRs)
            Set<Integer> excludedIds = getActiveNObjectIdsForFacet("glossary");
            System.out.println("[UnisonSearchService] loadGlossaryRows() - Found " + excludedIds.size() + " active nobject_id values to exclude: " + excludedIds);
            System.out.println("[UnisonSearchService] loadGlossaryRows() - Input glossaryIds: " + glossaryIds);

            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            String placeholders = String.join(",", Collections.nCopies(glossaryIds.size(), "?"));
            String sql = "SELECT ID, Name, Description, Ref_Number, Parent_ID, Deleted_datetime " +
                    "FROM glossary " +
                    "WHERE ID IN (" + placeholders + ") " +
                    "AND Deleted_datetime IS NULL";

            List<Object> params = new ArrayList<>(glossaryIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);
            System.out.println("[UnisonSearchService] loadGlossaryRows() - Retrieved " + results.size() + " rows from database");

            int excludedCount = 0;
            for (Map<String, Object> row : results) {
                Object idObj = row.get("ID");
                if (idObj == null) continue;
                
                int id = idObj instanceof Integer ? (Integer) idObj : ((Number) idObj).intValue();
                
                // Skip if this is a temporary cloned row (nobject_id)
                if (excludedIds.contains(id)) {
                    excludedCount++;
                    System.out.println("[UnisonSearchService] loadGlossaryRows() - Excluding nobject_id: " + id);
                    continue;
                }
                
                Map<String, Object> glossaryRow = new HashMap<>();
                glossaryRow.put("ID", id);
                glossaryRow.put("id", id);
                glossaryRow.put("Name", row.get("Name"));
                glossaryRow.put("name", row.get("Name"));
                glossaryRow.put("Description", row.get("Description"));
                glossaryRow.put("description", row.get("Description"));
                glossaryRow.put("Ref_Number", row.get("Ref_Number"));
                glossaryRow.put("refNumber", row.get("Ref_Number"));
                glossaryRow.put("ref_number", row.get("Ref_Number"));
                glossaryRow.put("Parent_ID", row.get("Parent_ID"));
                glossaryRow.put("parent_id", row.get("Parent_ID"));
                rows.add(glossaryRow);
            }
            if (excludedCount > 0) {
                System.out.println("[UnisonSearchService] loadGlossaryRows() - Excluded " + excludedCount + " nobject_id(s)");
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadGlossaryRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }
    
    /**
     * Get active nobject_id values (temporary cloned rows) for a facet.
     * Only returns values for the 4 facets that support Auto CR: glossary, dataset, system, process.
     */
    private Set<Integer> getActiveNObjectIdsForFacet(String facetName) {
        if (facetName == null) return new HashSet<>();
        
        try {
            com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
            Set<Integer> excludedIds = facetChangesDAO.getActiveNObjectIdsForFacet(facetName);
            System.out.println("[UnisonSearchService] getActiveNObjectIdsForFacet(" + facetName + ") - Found " + excludedIds.size() + " active nobject_id values: " + excludedIds);
            return excludedIds;
        } catch (Exception e) {
            // Log but don't fail - if we can't get excluded IDs, just return empty set
            System.err.println("[UnisonSearchService] Error getting active nobject_id for facet " + facetName + ": " + e.getMessage());
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    // ============================================================================
    // ENRICHMENT CONTEXT (Deduplication & Depth Control)
    // ============================================================================

    /**
     * Facet priority map for deterministic ordering.
     * Lower number = higher priority (appears first).
     */
    private static final Map<String, Integer> FACET_PRIORITY = Map.ofEntries(
            // Core objects (priority 1-10)
            Map.entry("DATASET", 1),
            Map.entry("SYSTEM", 2),
            Map.entry("GLOSSARY", 3),
            // People and roles (priority 11-20)
            Map.entry("PEOPLE", 11),
            Map.entry("PERSON", 11),
            Map.entry("ROLE", 12),
            Map.entry("ORG_UNIT", 13),
            Map.entry("ORGUNIT", 13),
            // Business objects (priority 21-30)
            Map.entry("PROCESS", 21),
            Map.entry("PROJECT", 22),
            Map.entry("PRODUCT", 23),
            Map.entry("POLICY", 24),
            Map.entry("BUSINESS_AREA", 25),
            Map.entry("BUSINESSAREA", 25),
            // Regulatory (priority 31-40)
            Map.entry("REGULATION", 31),
            Map.entry("REGULATOR", 32),
            Map.entry("REGULATORY_THEME", 33),
            Map.entry("REGULATORYTHEME", 33),
            Map.entry("GEOGRAPHY", 34),
            // Supporting (priority 41-50)
            Map.entry("INTERFACE", 41),
            Map.entry("ATTRIBUTE", 42),
            Map.entry("CAPABILITY", 43),
            Map.entry("LEGAL_ENTITY", 44),
            Map.entry("LEGALENTITY", 44),
            Map.entry("CLIENT", 45),
            Map.entry("COMMITTEE", 46),
            // Tasks and requests (priority 51-60)
            Map.entry("CHANGE_REQUEST", 51),
            Map.entry("CHANGEREQUEST", 51),
            Map.entry("ACTIVE_TASKS", 52),
            Map.entry("ACTIVETASKS", 52));

    /**
     * Get priority for a facet (default to 100 if not found).
     */
    private int getFacetPriority(String facetId) {
        if (facetId == null) {
            return 100;
        }
        String normalized = normalizeFacetName(facetId);
        return FACET_PRIORITY.getOrDefault(normalized, 100);
    }

    /**
     * Helper method to get seed object IDs (depth 0) for a given facet from results.
     * This ensures we only process direct relations from seed objects, not from enriched/related objects.
     */
    private Set<Integer> getSeedObjectIds(Map<String, FacetResult> results, String facetName) {
        Set<Integer> seedIds = new HashSet<>();
        if (results == null || facetName == null) {
            return seedIds;
        }
        
        // Try different facet name variations
        String[] facetVariations = {
            facetName,
            facetName.toUpperCase(),
            facetName.toLowerCase(),
            facetName.replace("_", "-"),
            facetName.replace("-", "_")
        };
        
        for (String variation : facetVariations) {
            FacetResult fr = results.get(variation);
            if (fr != null && fr.getIds() != null && fr.getDepthById() != null) {
                for (Integer id : fr.getIds()) {
                    int depth = fr.getDepthById().getOrDefault(id, Integer.MAX_VALUE);
                    if (depth == 0) {
                        seedIds.add(id);
                    }
                }
            }
        }
        
        return seedIds;
    }

    /**
     * Context to track enriched objects and prevent infinite recursion.
     * Depth is semantic (logical relationship hops), not call depth:
     * - 0 = searched object (seed)
     * - 1 = directly related object (one-hop)
     */
    private static class EnrichmentContext {
        private final Map<String, Set<Integer>> enrichedFacets = new HashMap<>();
        private final Map<String, Map<Integer, Integer>> depthByFacet = new HashMap<>();
        /** When true, enrichment is limited to attribute-search rules (no CR/tasks, no broad facet expansion). */
        private boolean attributePrimarySearch;
        void setAttributePrimarySearch(boolean attributePrimarySearch) {
            this.attributePrimarySearch = attributePrimarySearch;
        }

        boolean isAttributePrimarySearch() {
            return attributePrimarySearch;
        }

        /**
         * Check if an object has already been enriched for a given facet.
         */
        boolean isAlreadyEnriched(String facetId, Integer objectId) {
            if (facetId == null) {
                return false;
            }
            String normalized = normalizeFacetName(facetId);
            if (objectId == null) {
                // Check if facet type is being processed (for recursion prevention)
                return enrichedFacets.containsKey(normalized) && !enrichedFacets.get(normalized).isEmpty();
            }
            return enrichedFacets.getOrDefault(normalized, Collections.emptySet()).contains(objectId);
        }

        /**
         * Mark an object as enriched with its depth.
         */
        void markEnriched(String facetId, Integer objectId, int depth) {
            if (facetId == null || objectId == null) {
                return;
            }
            String normalized = normalizeFacetName(facetId);
            enrichedFacets.computeIfAbsent(normalized, k -> new HashSet<>()).add(objectId);
            depthByFacet.computeIfAbsent(normalized, k -> new HashMap<>()).put(objectId, depth);
        }

        /**
         * Get the current depth for an object in a facet.
         */
        int getDepth(String facetId, Integer objectId) {
            if (facetId == null || objectId == null) {
                return Integer.MAX_VALUE;
            }
            String normalized = normalizeFacetName(facetId);
            return depthByFacet.getOrDefault(normalized, Collections.emptyMap()).getOrDefault(objectId,
                    Integer.MAX_VALUE);
        }

        /**
         * Merge depth, always keeping the minimum (closest relationship).
         */
        int mergeDepth(int existingDepth, int newDepth) {
            return Math.min(existingDepth, newDepth);
        }

        /**
         * Normalize facet name for consistent tracking.
         */
        private String normalizeFacetName(String facetId) {
            if (facetId == null) {
                return null;
            }
            return facetId.trim().toUpperCase().replace("-", "_");
        }
    }

    /**
     * True when the only governed search seeds (depth 0) are attributes — compound searches
     * (e.g. attribute + dataset) use the full enrichment pipeline.
     */
    private boolean isAttributeOnlySeedSearch(Map<String, FacetResult> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }
        boolean hasAttributeSeed = false;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            FacetResult fr = entry.getValue();
            if (fr == null || fr.getIds() == null || fr.getIds().isEmpty()) {
                continue;
            }
            String norm = facetId != null ? facetId.trim().toUpperCase().replace("-", "_") : "";
            if (norm.contains("PEOPLE") || "PERSON".equals(norm) || norm.contains("ROLE")
                    || norm.contains("CHANGEREQUEST") || norm.contains("CHANGE_REQUEST")
                    || norm.contains("ACTIVE_TASK") || norm.contains("ACTIVETASK")) {
                continue;
            }
            Set<Integer> seeds = new HashSet<>();
            if (fr.getDepthById() != null) {
                for (Integer id : fr.getIds()) {
                    if (fr.getDepthById().getOrDefault(id, Integer.MAX_VALUE) == 0) {
                        seeds.add(id);
                    }
                }
            } else {
                seeds.addAll(fr.getIds());
            }
            if (seeds.isEmpty()) {
                continue;
            }
            if ("ATTRIBUTE".equals(norm) || "ATTRIBUTES".equals(norm)) {
                hasAttributeSeed = true;
                continue;
            }
            if (GOVERNED_FACETS.contains(norm)) {
                return false;
            }
        }
        return hasAttributeSeed;
    }

    /**
     * True when the only governed search seeds (depth 0) are interfaces — compound searches
     * (e.g. interface + dataset) use the full enrichment pipeline.
     */
    private boolean isInterfaceOnlySeedSearch(Map<String, FacetResult> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }
        boolean hasInterfaceSeed = false;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            FacetResult fr = entry.getValue();
            if (fr == null || fr.getIds() == null || fr.getIds().isEmpty()) {
                continue;
            }
            String norm = facetId != null ? facetId.trim().toUpperCase(Locale.ROOT).replace("-", "_") : "";
            if (norm.contains("PEOPLE") || "PERSON".equals(norm) || norm.contains("ROLE")
                    || norm.contains("CHANGEREQUEST") || norm.contains("CHANGE_REQUEST")
                    || norm.contains("ACTIVE_TASK") || norm.contains("ACTIVETASK")) {
                continue;
            }
            Set<Integer> seeds = new HashSet<>();
            if (fr.getDepthById() != null) {
                for (Integer id : fr.getIds()) {
                    if (fr.getDepthById().getOrDefault(id, Integer.MAX_VALUE) == 0) {
                        seeds.add(id);
                    }
                }
            } else {
                seeds.addAll(fr.getIds());
            }
            if (seeds.isEmpty()) {
                continue;
            }
            if ("INTERFACE".equals(norm) || "INTERFACES".equals(norm)) {
                hasInterfaceSeed = true;
                continue;
            }
            if (GOVERNED_FACETS.contains(norm)) {
                return false;
            }
        }
        return hasInterfaceSeed;
    }

    /**
     * True when the only governed search seeds (depth 0) are change requests — compound searches
     * (e.g. CR + dataset) use the full enrichment pipeline.
     */
    private boolean isChangeRequestOnlySeedSearch(Map<String, FacetResult> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }
        boolean hasCrSeed = false;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            FacetResult fr = entry.getValue();
            if (fr == null || fr.getIds() == null || fr.getIds().isEmpty()) {
                continue;
            }
            String norm = facetId != null ? facetId.trim().toUpperCase(Locale.ROOT).replace("-", "_") : "";
            if (norm.contains("PEOPLE") || "PERSON".equals(norm) || norm.contains("ROLE")
                    || norm.contains("ACTIVE_TASK") || norm.contains("ACTIVETASK")) {
                continue;
            }
            Set<Integer> seeds = new HashSet<>();
            if (fr.getDepthById() != null) {
                for (Integer id : fr.getIds()) {
                    if (fr.getDepthById().getOrDefault(id, Integer.MAX_VALUE) == 0) {
                        seeds.add(id);
                    }
                }
            } else {
                seeds.addAll(fr.getIds());
            }
            if (seeds.isEmpty()) {
                continue;
            }
            if (norm.contains("CHANGEREQUEST") || norm.contains("CHANGE_REQUEST")) {
                hasCrSeed = true;
                continue;
            }
            if (GOVERNED_FACETS.contains(norm)) {
                return false;
            }
        }
        return hasCrSeed;
    }

    /**
     * For CR-primary search, drop graph traversal so only seed CR rows remain; raised-upon / tasks / stakeholders are added by
     * {@link #enrichChangeRequestFacet}.
     */
    private Map<String, FacetResult> stripGraphNoiseBeforeChangeRequestPrimaryEnrichment(Map<String, FacetResult> results) {
        if (results == null || results.isEmpty()) {
            return results;
        }
        Map<String, FacetResult> out = new HashMap<>();
        for (Map.Entry<String, FacetResult> e : results.entrySet()) {
            String k = e.getKey();
            if (k == null) {
                continue;
            }
            String n = k.trim().toUpperCase(Locale.ROOT).replace("-", "_");
            if (n.contains("CHANGEREQUEST") || n.contains("CHANGE_REQUEST")) {
                out.put(k, e.getValue());
            }
        }
        return out;
    }

    /**
     * After CR enrichment, keep only change requests, active tasks, people, roles, and the object the CR was raised upon;
     * all other facets are dropped (counts effectively zero in the UI).
     */
    private Map<String, FacetResult> retainFacetsForChangeRequestPrimarySearch(Map<String, FacetResult> results)
            throws SQLException {
        if (results == null || results.isEmpty()) {
            return results;
        }
        Set<Integer> crIds = new LinkedHashSet<>();
        for (Map.Entry<String, FacetResult> e : results.entrySet()) {
            String k = e.getKey();
            if (k == null) {
                continue;
            }
            String n = k.trim().toUpperCase(Locale.ROOT).replace("-", "_");
            if (!(n.contains("CHANGEREQUEST") || n.contains("CHANGE_REQUEST"))) {
                continue;
            }
            FacetResult fr = e.getValue();
            if (fr != null && fr.getIds() != null) {
                crIds.addAll(fr.getIds());
            }
        }
        Set<String> allowedRaisedUpon = new HashSet<>();
        for (Integer crId : crIds) {
            Map<String, Integer> raised = queryCRRaisedUponObject(crId);
            if (raised == null) {
                continue;
            }
            for (String facetKey : raised.keySet()) {
                if (facetKey == null) {
                    continue;
                }
                allowedRaisedUpon.add(facetKey.trim().toUpperCase(Locale.ROOT).replace("-", "_"));
                String canon = com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(facetKey);
                if (canon != null) {
                    allowedRaisedUpon.add(canon.trim().toUpperCase(Locale.ROOT).replace("-", "_"));
                }
            }
        }

        Map<String, FacetResult> out = new HashMap<>();
        for (Map.Entry<String, FacetResult> e : results.entrySet()) {
            String key = e.getKey();
            if (key == null) {
                continue;
            }
            String norm = key.trim().toUpperCase(Locale.ROOT).replace("-", "_");
            String canon = com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(key);
            if (norm.contains("CHANGEREQUEST") || norm.contains("CHANGE_REQUEST")) {
                out.put(key, e.getValue());
                continue;
            }
            if (norm.contains("ACTIVE_TASK") || norm.contains("ACTIVETASK")) {
                out.put(key, e.getValue());
                continue;
            }
            if (norm.contains("PEOPLE") || "PERSON".equals(norm)) {
                out.put(key, e.getValue());
                continue;
            }
            if (canon != null && "ROLE".equals(canon)) {
                out.put(key, e.getValue());
                continue;
            }
            boolean raised = false;
            if (canon != null) {
                String c = canon.trim().toUpperCase(Locale.ROOT).replace("-", "_");
                if (allowedRaisedUpon.contains(c)) {
                    raised = true;
                }
            }
            if (allowedRaisedUpon.contains(norm)) {
                raised = true;
            }
            if (raised) {
                out.put(key, e.getValue());
            }
        }
        return out;
    }

    /**
     * Remove graph-traversal facets for interface-primary search; keep interface seeds, stakeholders, CRs, tasks.
     * Source/target systems are re-added by {@link #enrichInterfaceFacet}.
     */
    private Map<String, FacetResult> stripGraphNoiseBeforeInterfacePrimaryEnrichment(Map<String, FacetResult> results) {
        if (results == null || results.isEmpty()) {
            return results;
        }
        Map<String, FacetResult> out = new HashMap<>();
        for (Map.Entry<String, FacetResult> e : results.entrySet()) {
            String k = e.getKey();
            if (k == null) {
                continue;
            }
            String n = k.trim().toUpperCase(Locale.ROOT).replace("-", "_");
            if (n.contains("INTERFACE")) {
                out.put(k, e.getValue());
                continue;
            }
            if (n.contains("PEOPLE") || "PERSON".equals(n) || n.contains("ORG_UNIT") || n.contains("ORGUNIT")
                    || n.contains("ROLE") || n.contains("CHANGE_REQUEST") || n.contains("CHANGEREQUEST")
                    || n.contains("ACTIVE_TASK") || n.contains("ACTIVETASK")) {
                out.put(k, e.getValue());
            }
        }
        return out;
    }

    private boolean isFacetAllowedForInterfacePrimarySearch(String facetKey) {
        if (facetKey == null) {
            return false;
        }
        String n = facetKey.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        if (n.contains("INTERFACE")) {
            return true;
        }
        if (n.contains("SYSTEM")) {
            return true;
        }
        if (n.contains("PEOPLE") || "PERSON".equals(n)) {
            return true;
        }
        if (n.contains("ORG_UNIT") || "ORGUNIT".equals(n)) {
            return true;
        }
        if (n.contains("ROLE")) {
            return true;
        }
        if (n.contains("CHANGE_REQUEST") || n.contains("CHANGEREQUEST")) {
            return true;
        }
        if (n.contains("ACTIVE_TASK") || n.contains("ACTIVETASK")) {
            return true;
        }
        if (n.contains("PROCESS")) {
            return true;
        }
        if (n.contains("GLOSSARY")) {
            return true;
        }
        return false;
    }

    private Map<String, FacetResult> retainFacetsForInterfacePrimarySearch(Map<String, FacetResult> results) {
        if (results == null || results.isEmpty()) {
            return results;
        }
        Map<String, FacetResult> out = new HashMap<>();
        for (Map.Entry<String, FacetResult> e : results.entrySet()) {
            if (isFacetAllowedForInterfacePrimarySearch(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /**
     * Policy / process / project linked to an attribute via junction tables (excludes attribute_x_attribute).
     */
    private Map<String, Set<Integer>> queryAttributeJunctionRelations(Integer attributeId) throws SQLException {
        Map<String, Set<Integer>> out = new HashMap<>();
        if (attributeId == null) {
            return out;
        }
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper =
                    new com.example.unisonsearch.repository.DatabaseHelper();
            List<Object> p = List.of(attributeId);

            String polSql = "SELECT DISTINCT pxa.policyid FROM policy_x_attribute pxa "
                    + "JOIN policy pol ON pxa.policyid = pol.ID "
                    + "WHERE pxa.attributeid = ? AND pol.DeletedDatetime IS NULL";
            Set<Integer> policyIds = extractIds(dbHelper.executeQuery(polSql, p), "policyid");
            if (!policyIds.isEmpty()) {
                out.put("POLICY", policyIds);
            }

            String procSql = "SELECT DISTINCT pxa.processid FROM process_x_attribute pxa "
                    + "JOIN process pr ON pxa.processid = pr.id "
                    + "WHERE pxa.attributeid = ? AND pr.deleteddatetime IS NULL";
            Set<Integer> processIds = extractIds(dbHelper.executeQuery(procSql, p), "processid");
            if (!processIds.isEmpty()) {
                out.put("PROCESS", processIds);
            }

            String projSql = "SELECT DISTINCT pxa.projectid FROM project_x_attribute pxa "
                    + "JOIN project pj ON pxa.projectid = pj.id "
                    + "WHERE pxa.attribute_id = ? AND pj.deletedatetime IS NULL";
            Set<Integer> projectIds = extractIds(dbHelper.executeQuery(projSql, p), "projectid");
            if (!projectIds.isEmpty()) {
                out.put("PROJECT", projectIds);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] queryAttributeJunctionRelations: " + e.getMessage());
        }
        return out;
    }

    // ============================================================================
    // FACET-SPECIFIC ENRICHMENT METHODS
    // ============================================================================

    /**
     * Enrich Dataset facet with its system, attributes, glossaries, interfaces, and system impact.
     */
    private Map<String, FacetResult> enrichDatasetFacet(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        FacetResult datasetResult = null;
        String datasetFacetId = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("DATASET") ||
                    facetId.toUpperCase().equals("DATA_SETS") ||
                    facetId.toUpperCase().equals("DATASETS"))) {
                datasetResult = entry.getValue();
                datasetFacetId = facetId;
                break;
            }
        }

        if (datasetResult == null || datasetResult.getIds() == null || datasetResult.getIds().isEmpty()) {
            return results;
        }

        // Depth guard: only process if not already enriched (prevent recursion)
        String normalizedFacetId = normalizeFacetName(datasetFacetId);
        if (context.isAlreadyEnriched(normalizedFacetId, null)) {
            return results; // Already processing this facet type
        }

        Set<Integer> allSystemIds = new HashSet<>();
        Set<Integer> allAttributeIds = new HashSet<>();
        Set<Integer> allGlossaryIds = new HashSet<>();
        Set<Integer> allInterfaceIds = new HashSet<>();
        Set<Integer> allRelatedDatasetIds = new HashSet<>();
        Map<Integer, Integer> systemDepth = new HashMap<>();
        Map<Integer, Integer> attributeDepth = new HashMap<>();
        Map<Integer, Integer> glossaryDepth = new HashMap<>();
        Map<Integer, Integer> interfaceDepth = new HashMap<>();
        Map<Integer, Integer> relatedDatasetDepth = new HashMap<>();

        // Get seed dataset IDs (depth 0) - only process direct relations from seed objects
        Set<Integer> seedDatasetIds = new HashSet<>();
        Set<Integer> enrichedDatasetIds = new HashSet<>(); // Datasets that came from other facets (e.g., attribute)
        if (datasetResult.getDepthById() != null) {
            Map<Integer, Long> depthDistribution = new HashMap<>();
            for (Integer datasetId : datasetResult.getIds()) {
                int depth = datasetResult.getDepthById().getOrDefault(datasetId, Integer.MAX_VALUE);
                depthDistribution.put(depth, depthDistribution.getOrDefault(depth, 0L) + 1);
                if (depth == 0) {
                    seedDatasetIds.add(datasetId);
                } else {
                    // Datasets that came from other facets (e.g., attribute -> dataset)
                    enrichedDatasetIds.add(datasetId);
                }
            }
            System.out.println("[enrichDatasetFacet] Total datasets: " + datasetResult.getIds().size() + 
                ", Seed (depth 0): " + seedDatasetIds.size() + 
                ", Enriched (depth > 0): " + enrichedDatasetIds.size() + 
                ", Depth distribution: " + depthDistribution);
        } else {
            seedDatasetIds.addAll(datasetResult.getIds());
            System.out.println("[enrichDatasetFacet] No depth info, treating all " + 
                datasetResult.getIds().size() + " datasets as seed");
        }
        
        // Get seed system IDs to avoid adding them if they're already seed objects
        Set<Integer> seedSystemIds = getSeedObjectIds(results, "SYSTEM");

        // Process seed datasets (when searching for dataset directly)
        for (Integer datasetId : seedDatasetIds) {
            // Mark dataset as enriched (depth 0 = seed object)
            context.markEnriched("DATASET", datasetId, 0);
            
            try {
                // Query same-type relationships (dataset→dataset via attribute_x_attribute)
                Set<Integer> relatedIds = queryDatasetRelationships(datasetId);
                for (Integer relatedId : relatedIds) {
                    if (!seedDatasetIds.contains(relatedId)) {  // Don't add if already seed
                        allRelatedDatasetIds.add(relatedId);
                        relatedDatasetDepth.putIfAbsent(relatedId, 1);
                    }
                }
                
                // Get system of this dataset
                Set<Integer> systemIds = queryDatasetSystems(datasetId);
                for (Integer systemId : systemIds) {
                    // Skip if this system is already a seed object (direct relation only)
                    if (seedSystemIds.contains(systemId)) {
                        continue;
                    }
                    // Check if already enriched with better depth
                    int existingDepth = context.getDepth("SYSTEM", systemId);
                    int newDepth = 1; // One-hop from dataset
                    if (newDepth < existingDepth) {
                        allSystemIds.add(systemId);
                        systemDepth.put(systemId, newDepth);
                        context.markEnriched("SYSTEM", systemId, newDepth);
                    }
                }
            } catch (Exception e) {
                System.err.println("[UnisonSearchService] Error enriching dataset " + datasetId + ": " + e.getMessage());
            }
        }
        
        // REMOVED: No longer processing enriched datasets (depth > 0)
        // Only process seed datasets (depth 0) to avoid adding objects from depth > 1
        
        // Continue with seed datasets for attributes and glossaries
        for (Integer datasetId : seedDatasetIds) {
            try {
                // REMOVED: No longer getting systems (not needed after removing depth > 1 enrichment)

                // Get attributes of this dataset
                // Only add attributes that are NOT already in seed results (direct relations only)
                Set<Integer> attributeIds = queryDatasetAttributes(datasetId);
                Set<Integer> seedAttributeIds = new HashSet<>();
                FacetResult attributeResult = results.get("ATTRIBUTE");
                if (attributeResult != null && attributeResult.getIds() != null && attributeResult.getDepthById() != null) {
                    // Get seed attribute IDs (depth 0)
                    for (Integer attrId : attributeResult.getIds()) {
                        int depth = attributeResult.getDepthById().getOrDefault(attrId, Integer.MAX_VALUE);
                        if (depth == 0) {
                            seedAttributeIds.add(attrId);
                        }
                    }
                }
                
                for (Integer attributeId : attributeIds) {
                    // Skip if this attribute is already a seed object (when searching for attribute, don't add other attributes from dataset)
                    if (seedAttributeIds.contains(attributeId)) {
                        continue;
                    }
                    
                    int existingDepth = context.getDepth("ATTRIBUTE", attributeId);
                    int newDepth = 1; // One-hop from dataset
                    if (newDepth < existingDepth) {
                        allAttributeIds.add(attributeId);
                        attributeDepth.put(attributeId, newDepth);
                        context.markEnriched("ATTRIBUTE", attributeId, newDepth);
                    }
                }

                // Get glossaries linked to dataset or its attributes
                Set<Integer> directGlossaries = queryDatasetDirectGlossaries(datasetId);
                for (Integer glossaryId : directGlossaries) {
                    int existingDepth = context.getDepth("GLOSSARY", glossaryId);
                    int newDepth = 1; // One-hop from dataset
                    if (newDepth < existingDepth) {
                        allGlossaryIds.add(glossaryId);
                        glossaryDepth.put(glossaryId, newDepth);
                        context.markEnriched("GLOSSARY", glossaryId, newDepth);
                    }
                }

                // Get interfaces where the dataset's system is source or target (depth 1)
                Set<Integer> datasetSystemIds = queryDatasetSystems(datasetId);
                for (Integer systemId : datasetSystemIds) {
                    // Query interfaces for this system
                    Set<Integer> interfaceIds = querySystemInterfaces(systemId);
                    for (Integer interfaceId : interfaceIds) {
                        int existingDepth = context.getDepth("INTERFACE", interfaceId);
                        int newDepth = 1; // One-hop from dataset (through its system)
                        if (newDepth < existingDepth) {
                            allInterfaceIds.add(interfaceId);
                            interfaceDepth.put(interfaceId, newDepth);
                            context.markEnriched("INTERFACE", interfaceId, newDepth);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[UnisonSearchService] Error enriching dataset " + datasetId + ": " + e.getMessage());
            }
        }

        // Add System results
        if (!allSystemIds.isEmpty()) {
            String systemFacetId = "SYSTEM";
            FacetResult existingSystemResult = results.get(systemFacetId);
            Set<Integer> mergedSystemIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedSystemDepth = new HashMap<>();

            if (existingSystemResult != null && existingSystemResult.getIds() != null) {
                mergedSystemIds.addAll(existingSystemResult.getIds());
                if (existingSystemResult.getDepthById() != null) {
                    mergedSystemDepth.putAll(existingSystemResult.getDepthById());
                }
            }

            mergedSystemIds.addAll(allSystemIds);
            for (Map.Entry<Integer, Integer> entry : systemDepth.entrySet()) {
                mergedSystemDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> systemRows = loadSystemRows(mergedSystemIds);
            int systemTotal = computeAccessibleTotalCount(systemFacetId, systemRows != null ? systemRows.size() : 0);
            results.put(systemFacetId,
                    new FacetResult(mergedSystemIds, true, mergedSystemDepth, systemRows, systemTotal));
        }

        // Add Attribute results
        if (!allAttributeIds.isEmpty()) {
            String attributeFacetId = "ATTRIBUTE";
            FacetResult existingAttributeResult = results.get(attributeFacetId);
            Set<Integer> mergedAttributeIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedAttributeDepth = new HashMap<>();

            if (existingAttributeResult != null && existingAttributeResult.getIds() != null) {
                mergedAttributeIds.addAll(existingAttributeResult.getIds());
                if (existingAttributeResult.getDepthById() != null) {
                    mergedAttributeDepth.putAll(existingAttributeResult.getDepthById());
                }
            }

            mergedAttributeIds.addAll(allAttributeIds);
            mergedAttributeDepth.putAll(attributeDepth);

            List<Map<String, Object>> attributeRows = loadAttributeRows(mergedAttributeIds);
            int attributeTotal = computeAccessibleTotalCount(attributeFacetId,
                    attributeRows != null ? attributeRows.size() : 0);
            results.put(attributeFacetId,
                    new FacetResult(mergedAttributeIds, true, mergedAttributeDepth, attributeRows, attributeTotal));
        }

        // Add Glossary results (merge with existing)
        if (!allGlossaryIds.isEmpty()) {
            String glossaryFacetId = "GLOSSARY";
            FacetResult existingGlossaryResult = results.get(glossaryFacetId);
            Set<Integer> mergedGlossaryIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedGlossaryDepth = new HashMap<>();

            if (existingGlossaryResult != null && existingGlossaryResult.getIds() != null) {
                mergedGlossaryIds.addAll(existingGlossaryResult.getIds());
                if (existingGlossaryResult.getDepthById() != null) {
                    mergedGlossaryDepth.putAll(existingGlossaryResult.getDepthById());
                }
            }

            mergedGlossaryIds.addAll(allGlossaryIds);
            for (Map.Entry<Integer, Integer> entry : glossaryDepth.entrySet()) {
                mergedGlossaryDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> glossaryRows = loadGlossaryRows(mergedGlossaryIds);
            int glossaryTotal = computeAccessibleTotalCount(glossaryFacetId,
                    glossaryRows != null ? glossaryRows.size() : 0);
            results.put(glossaryFacetId,
                    new FacetResult(mergedGlossaryIds, true, mergedGlossaryDepth, glossaryRows, glossaryTotal));
        }

        // Add Interface results
        if (!allInterfaceIds.isEmpty()) {
            String interfaceFacetId = "INTERFACE";
            FacetResult existingInterfaceResult = results.get(interfaceFacetId);
            Set<Integer> mergedInterfaceIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedInterfaceDepth = new HashMap<>();

            if (existingInterfaceResult != null && existingInterfaceResult.getIds() != null) {
                mergedInterfaceIds.addAll(existingInterfaceResult.getIds());
                if (existingInterfaceResult.getDepthById() != null) {
                    mergedInterfaceDepth.putAll(existingInterfaceResult.getDepthById());
                }
            }

            mergedInterfaceIds.addAll(allInterfaceIds);
            mergedInterfaceDepth.putAll(interfaceDepth);

            List<Map<String, Object>> interfaceRows = loadInterfaceRows(mergedInterfaceIds);
            int interfaceTotal = computeAccessibleTotalCount(interfaceFacetId,
                    interfaceRows != null ? interfaceRows.size() : 0);
            results.put(interfaceFacetId,
                    new FacetResult(mergedInterfaceIds, true, mergedInterfaceDepth, interfaceRows, interfaceTotal));
        }

        // Add related Dataset results (from same-type relationships via attribute_x_attribute)
        if (!allRelatedDatasetIds.isEmpty()) {
            String relatedDatasetFacetId = "DATASET";
            FacetResult existingRelatedDatasetResult = results.get(relatedDatasetFacetId);
            Set<Integer> mergedRelatedDatasetIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedRelatedDatasetDepth = new HashMap<>();

            if (existingRelatedDatasetResult != null && existingRelatedDatasetResult.getIds() != null) {
                mergedRelatedDatasetIds.addAll(existingRelatedDatasetResult.getIds());
                if (existingRelatedDatasetResult.getDepthById() != null) {
                    mergedRelatedDatasetDepth.putAll(existingRelatedDatasetResult.getDepthById());
                }
            }

            mergedRelatedDatasetIds.addAll(allRelatedDatasetIds);
            mergedRelatedDatasetDepth.putAll(relatedDatasetDepth);

            List<Map<String, Object>> relatedDatasetRows = loadDatasetRows(mergedRelatedDatasetIds);
            int relatedDatasetTotal = computeAccessibleTotalCount(relatedDatasetFacetId,
                    relatedDatasetRows != null ? relatedDatasetRows.size() : 0);
            results.put(relatedDatasetFacetId,
                    new FacetResult(mergedRelatedDatasetIds, true, mergedRelatedDatasetDepth, relatedDatasetRows, relatedDatasetTotal));
        }

        return results;
    }

    /**
     * Enrich Attribute facet: parent dataset, dataset system, this attribute's glossary (Glossary_ID only),
     * dataset-linked glossaries (not other attributes' glossaries), policy/process/project junctions
     * (policy_x_attribute, process_x_attribute, project_x_attribute — not attribute_x_attribute),
     * plus People/ROLE from stakeholders (see enrichWithStakeholdersAndRelated).
     */
    private Map<String, FacetResult> enrichAttributeFacet(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        FacetResult attributeResult = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("ATTRIBUTE") ||
                    facetId.toUpperCase().equals("ATTRIBUTES"))) {
                attributeResult = entry.getValue();
                break;
            }
        }

        if (attributeResult == null || attributeResult.getIds() == null || attributeResult.getIds().isEmpty()) {
            return results;
        }

        Set<Integer> allDatasetIds = new HashSet<>();
        Set<Integer> allSystemIds = new HashSet<>();
        Set<Integer> allGlossaryIds = new HashSet<>();
        Set<Integer> allProcessIds = new HashSet<>();
        Set<Integer> allProjectIds = new HashSet<>();
        Set<Integer> allPolicyIds = new HashSet<>();
        Map<Integer, Integer> datasetDepth = new HashMap<>();
        Map<Integer, Integer> systemDepth = new HashMap<>();
        Map<Integer, Integer> glossaryDepth = new HashMap<>();
        Map<Integer, Integer> processDepth = new HashMap<>();
        Map<Integer, Integer> projectDepth = new HashMap<>();
        Map<Integer, Integer> policyDepth = new HashMap<>();

        // Get seed attribute IDs (depth 0) - only process direct relations from seed objects
        Set<Integer> seedAttributeIds = new HashSet<>();
        if (attributeResult.getDepthById() != null) {
            for (Integer attributeId : attributeResult.getIds()) {
                int depth = attributeResult.getDepthById().getOrDefault(attributeId, Integer.MAX_VALUE);
                if (depth == 0) {
                    seedAttributeIds.add(attributeId);
                }
            }
        } else {
            seedAttributeIds.addAll(attributeResult.getIds());
        }

        // Process each seed attribute
        for (Integer attributeId : seedAttributeIds) {
            try {
                // Get the attribute's dataset (depth 1)
                Integer datasetId = queryAttributeDataset(attributeId);
                if (datasetId != null) {
                    allDatasetIds.add(datasetId);
                    datasetDepth.put(datasetId, 1);
                    context.markEnriched("DATASET", datasetId, 1);

                    // Glossaries linked to the dataset (not via sibling attributes)
                    Set<Integer> datasetGlossaries = queryDatasetDirectGlossaries(datasetId);
                    for (Integer glossaryId : datasetGlossaries) {
                        allGlossaryIds.add(glossaryId);
                        glossaryDepth.merge(glossaryId, 1,
                                (existing, incoming) -> existing == null ? incoming : Math.min(existing, incoming));
                        context.markEnriched("GLOSSARY", glossaryId, 1);
                    }

                    // Get the dataset's system (depth 1 from attribute's perspective)
                    Set<Integer> systemIds = queryDatasetSystems(datasetId);
                    for (Integer systemId : systemIds) {
                        allSystemIds.add(systemId);
                        systemDepth.put(systemId, 1);
                        context.markEnriched("SYSTEM", systemId, 1);
                    }
                }

                // This attribute's glossary only (attribute.Glossary_ID)
                Set<Integer> glossaryIds = queryAttributeGlossaries(attributeId);
                for (Integer glossaryId : glossaryIds) {
                    allGlossaryIds.add(glossaryId);
                    glossaryDepth.merge(glossaryId, 1,
                            (existing, incoming) -> existing == null ? incoming : Math.min(existing, incoming));
                    context.markEnriched("GLOSSARY", glossaryId, 1);
                }

                Map<String, Set<Integer>> junction = queryAttributeJunctionRelations(attributeId);
                for (Map.Entry<String, Set<Integer>> je : junction.entrySet()) {
                    String facetKey = je.getKey();
                    for (Integer relatedId : je.getValue()) {
                        if ("PROCESS".equals(facetKey)) {
                            allProcessIds.add(relatedId);
                            processDepth.putIfAbsent(relatedId, 1);
                        } else if ("PROJECT".equals(facetKey)) {
                            allProjectIds.add(relatedId);
                            projectDepth.putIfAbsent(relatedId, 1);
                        } else if ("POLICY".equals(facetKey)) {
                            allPolicyIds.add(relatedId);
                            policyDepth.putIfAbsent(relatedId, 1);
                        }
                    }
                }

            } catch (Exception e) {
                System.err.println("[UnisonSearchService] Error enriching attribute " + attributeId + ": " + e.getMessage());
            }
        }

        // Add Dataset results
        if (!allDatasetIds.isEmpty()) {
            String datasetFacetId = "DATASET";
            FacetResult existingDatasetResult = results.get(datasetFacetId);
            Set<Integer> mergedDatasetIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDatasetDepth = new HashMap<>();

            if (existingDatasetResult != null && existingDatasetResult.getIds() != null) {
                mergedDatasetIds.addAll(existingDatasetResult.getIds());
                if (existingDatasetResult.getDepthById() != null) {
                    mergedDatasetDepth.putAll(existingDatasetResult.getDepthById());
                }
            }

            mergedDatasetIds.addAll(allDatasetIds);
            mergedDatasetDepth.putAll(datasetDepth);

            List<Map<String, Object>> datasetRows = loadDatasetRows(mergedDatasetIds);
            int datasetTotal = computeAccessibleTotalCount(datasetFacetId,
                    datasetRows != null ? datasetRows.size() : 0);
            results.put(datasetFacetId,
                    new FacetResult(mergedDatasetIds, true, mergedDatasetDepth, datasetRows, datasetTotal));
        }

        // Add System results
        if (!allSystemIds.isEmpty()) {
            String systemFacetId = "SYSTEM";
            FacetResult existingSystemResult = results.get(systemFacetId);
            Set<Integer> mergedSystemIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedSystemDepth = new HashMap<>();

            if (existingSystemResult != null && existingSystemResult.getIds() != null) {
                mergedSystemIds.addAll(existingSystemResult.getIds());
                if (existingSystemResult.getDepthById() != null) {
                    mergedSystemDepth.putAll(existingSystemResult.getDepthById());
                }
            }

            mergedSystemIds.addAll(allSystemIds);
            mergedSystemDepth.putAll(systemDepth);

            List<Map<String, Object>> systemRows = loadSystemRows(mergedSystemIds);
            int systemTotal = computeAccessibleTotalCount(systemFacetId, systemRows != null ? systemRows.size() : 0);
            results.put(systemFacetId,
                    new FacetResult(mergedSystemIds, true, mergedSystemDepth, systemRows, systemTotal));
        }

        // Add Glossary results
        if (!allGlossaryIds.isEmpty()) {
            String glossaryFacetId = "GLOSSARY";
            FacetResult existingGlossaryResult = results.get(glossaryFacetId);
            Set<Integer> mergedGlossaryIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedGlossaryDepth = new HashMap<>();

            if (existingGlossaryResult != null && existingGlossaryResult.getIds() != null) {
                mergedGlossaryIds.addAll(existingGlossaryResult.getIds());
                if (existingGlossaryResult.getDepthById() != null) {
                    mergedGlossaryDepth.putAll(existingGlossaryResult.getDepthById());
                }
            }

            mergedGlossaryIds.addAll(allGlossaryIds);
            for (Map.Entry<Integer, Integer> entry : glossaryDepth.entrySet()) {
                mergedGlossaryDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> glossaryRows = loadGlossaryRows(mergedGlossaryIds);
            int glossaryTotal = computeAccessibleTotalCount(glossaryFacetId,
                    glossaryRows != null ? glossaryRows.size() : 0);
            results.put(glossaryFacetId,
                    new FacetResult(mergedGlossaryIds, true, mergedGlossaryDepth, glossaryRows, glossaryTotal));
        }

        if (!allProcessIds.isEmpty()) {
            String facetId = "PROCESS";
            FacetResult existing = results.get(facetId);
            Set<Integer> merged = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDepth = new HashMap<>();
            if (existing != null && existing.getIds() != null) {
                merged.addAll(existing.getIds());
                if (existing.getDepthById() != null) {
                    mergedDepth.putAll(existing.getDepthById());
                }
            }
            merged.addAll(allProcessIds);
            for (Map.Entry<Integer, Integer> e : processDepth.entrySet()) {
                mergedDepth.merge(e.getKey(), e.getValue(),
                        (a, b) -> (a == null || b == null) ? (a != null ? a : b)
                                : Integer.compare(a, b) <= 0 ? a : b);
            }
            List<Map<String, Object>> rows = loadProcessRows(merged);
            int total = computeAccessibleTotalCount(facetId, rows != null ? rows.size() : 0);
            results.put(facetId, new FacetResult(merged, true, mergedDepth, rows, total));
        }
        if (!allProjectIds.isEmpty()) {
            String facetId = "PROJECT";
            FacetResult existing = results.get(facetId);
            Set<Integer> merged = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDepth = new HashMap<>();
            if (existing != null && existing.getIds() != null) {
                merged.addAll(existing.getIds());
                if (existing.getDepthById() != null) {
                    mergedDepth.putAll(existing.getDepthById());
                }
            }
            merged.addAll(allProjectIds);
            for (Map.Entry<Integer, Integer> e : projectDepth.entrySet()) {
                mergedDepth.merge(e.getKey(), e.getValue(),
                        (a, b) -> (a == null || b == null) ? (a != null ? a : b)
                                : Integer.compare(a, b) <= 0 ? a : b);
            }
            List<Map<String, Object>> rows = loadProjectRows(merged);
            int total = computeAccessibleTotalCount(facetId, rows != null ? rows.size() : 0);
            results.put(facetId, new FacetResult(merged, true, mergedDepth, rows, total));
        }
        if (!allPolicyIds.isEmpty()) {
            String facetId = "POLICY";
            FacetResult existing = results.get(facetId);
            Set<Integer> merged = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDepth = new HashMap<>();
            if (existing != null && existing.getIds() != null) {
                merged.addAll(existing.getIds());
                if (existing.getDepthById() != null) {
                    mergedDepth.putAll(existing.getDepthById());
                }
            }
            merged.addAll(allPolicyIds);
            for (Map.Entry<Integer, Integer> e : policyDepth.entrySet()) {
                mergedDepth.merge(e.getKey(), e.getValue(),
                        (a, b) -> (a == null || b == null) ? (a != null ? a : b)
                                : Integer.compare(a, b) <= 0 ? a : b);
            }
            List<Map<String, Object>> rows = loadPolicyRows(merged);
            int total = computeAccessibleTotalCount(facetId, rows != null ? rows.size() : 0);
            results.put(facetId, new FacetResult(merged, true, mergedDepth, rows, total));
        }

        return results;
    }

    /**
     * Enrich System facet with datasets, attributes, glossaries, interfaces (source/target), and impact links.
     */
    private Map<String, FacetResult> enrichSystemFacet(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        FacetResult systemResult = null;
        String systemFacetId = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("SYSTEM") ||
                    facetId.toUpperCase().equals("SYSTEMS"))) {
                systemResult = entry.getValue();
                systemFacetId = facetId;
                break;
            }
        }

        if (systemResult == null || systemResult.getIds() == null || systemResult.getIds().isEmpty()) {
            return results;
        }

        // Depth guard: only process if not already enriched (prevent recursion)
        String normalizedFacetId = normalizeFacetName(systemFacetId);
        if (context.isAlreadyEnriched(normalizedFacetId, null)) {
            return results; // Already processing this facet type
        }

        Set<Integer> allDatasetIds = new HashSet<>();
        Set<Integer> allAttributeIds = new HashSet<>();
        Set<Integer> allGlossaryIds = new HashSet<>();
        Set<Integer> allInterfaceIds = new HashSet<>();
        Set<Integer> allProductIds = new HashSet<>();
        Set<Integer> allClientIds = new HashSet<>();
        Set<Integer> allLegalEntityIds = new HashSet<>();
        Set<Integer> allProcessIds = new HashSet<>();
        Set<Integer> allProjectIds = new HashSet<>();
        Set<Integer> allBusinessAreaIds = new HashSet<>();
        Set<Integer> allCapabilityIds = new HashSet<>();
        Set<Integer> allPolicyIds = new HashSet<>();
        Map<Integer, Integer> datasetDepth = new HashMap<>();
        Map<Integer, Integer> attributeDepth = new HashMap<>();
        Map<Integer, Integer> glossaryDepth = new HashMap<>();
        Map<Integer, Integer> interfaceDepth = new HashMap<>();
        Map<Integer, Integer> productDepth = new HashMap<>();
        Map<Integer, Integer> clientDepth = new HashMap<>();
        Map<Integer, Integer> legalEntityDepth = new HashMap<>();
        Map<Integer, Integer> processDepth = new HashMap<>();
        Map<Integer, Integer> projectDepth = new HashMap<>();
        Map<Integer, Integer> businessAreaDepth = new HashMap<>();
        Map<Integer, Integer> capabilityDepth = new HashMap<>();
        Map<Integer, Integer> policyDepth = new HashMap<>();

        // Get seed system IDs (depth 0) - only process direct relations from seed objects
        Set<Integer> seedSystemIds = new HashSet<>();
        Map<Integer, Long> systemDepthDistribution = new HashMap<>();
        if (systemResult.getDepthById() != null) {
            for (Integer systemId : systemResult.getIds()) {
                int depth = systemResult.getDepthById().getOrDefault(systemId, Integer.MAX_VALUE);
                systemDepthDistribution.put(depth, systemDepthDistribution.getOrDefault(depth, 0L) + 1);
                if (depth == 0) {
                    seedSystemIds.add(systemId);
                }
            }
            System.out.println("[enrichSystemFacet] Total systems: " + systemResult.getIds().size() + 
                ", Seed systems (depth 0): " + seedSystemIds.size() + 
                ", Depth distribution: " + systemDepthDistribution);
        } else {
            // If no depth info, assume all are seed (backward compatibility)
            seedSystemIds.addAll(systemResult.getIds());
            System.out.println("[enrichSystemFacet] No depth info, treating all " + 
                systemResult.getIds().size() + " systems as seed");
        }
        
        // Get seed dataset IDs to avoid adding them if they're already seed objects
        Set<Integer> seedDatasetIds = getSeedObjectIds(results, "DATASET");
        Set<Integer> seedInterfaceIds = getSeedObjectIds(results, "INTERFACE");
        
        for (Integer systemId : seedSystemIds) {
            // Get the depth of this system (should be 0 for seed, but check to be safe)
            int systemDepth = systemResult.getDepthById() != null ? 
                systemResult.getDepthById().getOrDefault(systemId, 0) : 0;
            
            // Mark system as enriched
            context.markEnriched("SYSTEM", systemId, systemDepth);
            
            try {
                // Query impact relationships for this system
                Map<String, Set<Integer>> impactRelationships = querySystemImpactRelationships(systemId);
                for (Map.Entry<String, Set<Integer>> impactEntry : impactRelationships.entrySet()) {
                    String impactFacetId = impactEntry.getKey();
                    Set<Integer> impactIds = impactEntry.getValue();
                    int newDepth = systemDepth + 1;
                    
                    if (impactFacetId.equals("PRODUCT")) {
                        for (Integer productId : impactIds) {
                            if (newDepth <= 1) {
                                allProductIds.add(productId);
                                productDepth.putIfAbsent(productId, newDepth);
                            }
                        }
                    } else if (impactFacetId.equals("CLIENT")) {
                        for (Integer clientId : impactIds) {
                            if (newDepth <= 1) {
                                allClientIds.add(clientId);
                                clientDepth.putIfAbsent(clientId, newDepth);
                            }
                        }
                    } else if (impactFacetId.equals("LEGAL_ENTITY")) {
                        for (Integer legalId : impactIds) {
                            if (newDepth <= 1) {
                                allLegalEntityIds.add(legalId);
                                legalEntityDepth.putIfAbsent(legalId, newDepth);
                            }
                        }
                    } else if (impactFacetId.equals("PROCESS")) {
                        for (Integer processId : impactIds) {
                            if (newDepth <= 1) {
                                allProcessIds.add(processId);
                                processDepth.putIfAbsent(processId, newDepth);
                            }
                        }
                    } else if (impactFacetId.equals("PROJECT")) {
                        for (Integer projectId : impactIds) {
                            if (newDepth <= 1) {
                                allProjectIds.add(projectId);
                                projectDepth.putIfAbsent(projectId, newDepth);
                            }
                        }
                    } else if (impactFacetId.equals("BUSINESS_AREA")) {
                        for (Integer baId : impactIds) {
                            if (newDepth <= 1) {
                                allBusinessAreaIds.add(baId);
                                businessAreaDepth.putIfAbsent(baId, newDepth);
                            }
                        }
                    } else if (impactFacetId.equals("CAPABILITY")) {
                        for (Integer capId : impactIds) {
                            if (newDepth <= 1) {
                                allCapabilityIds.add(capId);
                                capabilityDepth.putIfAbsent(capId, newDepth);
                            }
                        }
                    } else if (impactFacetId.equals("POLICY")) {
                        for (Integer policyId : impactIds) {
                            if (newDepth <= 1) {
                                allPolicyIds.add(policyId);
                                policyDepth.putIfAbsent(policyId, newDepth);
                            }
                        }
                    }
                }

                // Interfaces where this system is source or target (one hop from seed system)
                Set<Integer> systemInterfaceIds = querySystemInterfaces(systemId);
                for (Integer interfaceId : systemInterfaceIds) {
                    if (seedInterfaceIds.contains(interfaceId)) {
                        continue;
                    }
                    int ifaceDepth = systemDepth + 1;
                    if (ifaceDepth <= 1) {
                        int existingIfaceDepth = context.getDepth("INTERFACE", interfaceId);
                        if (ifaceDepth < existingIfaceDepth) {
                            allInterfaceIds.add(interfaceId);
                            interfaceDepth.put(interfaceId, ifaceDepth);
                            context.markEnriched("INTERFACE", interfaceId, ifaceDepth);
                        }
                    }
                }
                
                // Get datasets in this system
                Set<Integer> datasetIds = querySystemDatasets(systemId);
                if (!datasetIds.isEmpty()) {
                    System.out.println("[enrichSystemFacet] System " + systemId + " (depth " + systemDepth + 
                        ") has " + datasetIds.size() + " datasets");
                }
                for (Integer datasetId : datasetIds) {
                    // Skip if this dataset is already a seed object (direct relation only)
                    if (seedDatasetIds.contains(datasetId)) {
                        continue;
                    }
                    
                    // Calculate depth: system depth + 1
                    int newDepth = systemDepth + 1;
                    // Only add if depth <= 1 (direct relation from seed system)
                    if (newDepth <= 1) {
                        int existingDepth = context.getDepth("DATASET", datasetId);
                        if (newDepth < existingDepth) {
                            allDatasetIds.add(datasetId);
                            datasetDepth.put(datasetId, newDepth);
                            context.markEnriched("DATASET", datasetId, newDepth);
                            
                            // For SYSTEM facet, depth 2 is allowed for attributes and glossaries
                            // Get attributes of this dataset (depth 2 from system)
                            Set<Integer> attributeIds = queryDatasetAttributes(datasetId);
                            for (Integer attributeId : attributeIds) {
                                int attrDepth = systemDepth + 2; // System -> Dataset -> Attribute
                                int existingAttrDepth = context.getDepth("ATTRIBUTE", attributeId);
                                if (attrDepth < existingAttrDepth) {
                                    allAttributeIds.add(attributeId);
                                    attributeDepth.put(attributeId, attrDepth);
                                    context.markEnriched("ATTRIBUTE", attributeId, attrDepth);
                                }
                            }
                            
                            // Get glossaries linked to this dataset (depth 2 from system)
                            Set<Integer> datasetGlossaryIds = queryDatasetDirectGlossaries(datasetId);
                            for (Integer glossaryId : datasetGlossaryIds) {
                                int glossDepth = systemDepth + 2; // System -> Dataset -> Glossary
                                int existingGlossDepth = context.getDepth("GLOSSARY", glossaryId);
                                if (glossDepth < existingGlossDepth) {
                                    allGlossaryIds.add(glossaryId);
                                    glossaryDepth.put(glossaryId, glossDepth);
                                    context.markEnriched("GLOSSARY", glossaryId, glossDepth);
                                }
                            }
                            
                            // Get glossaries linked to attributes of this dataset (depth 2 from system)
                            for (Integer attributeId : attributeIds) {
                                Set<Integer> attrGlossaryIds = queryAttributeGlossaries(attributeId);
                                for (Integer glossaryId : attrGlossaryIds) {
                                    int glossDepth = systemDepth + 2; // System -> Dataset -> Attribute -> Glossary
                                    int existingGlossDepth = context.getDepth("GLOSSARY", glossaryId);
                                    if (glossDepth < existingGlossDepth) {
                                        allGlossaryIds.add(glossaryId);
                                        glossaryDepth.put(glossaryId, glossDepth);
                                        context.markEnriched("GLOSSARY", glossaryId, glossDepth);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[UnisonSearchService] Error enriching system " + systemId + ": " + e.getMessage());
            }
        }

        // Add Dataset results
        if (!allDatasetIds.isEmpty()) {
            String datasetFacetId = "DATASET";
            FacetResult existingDatasetResult = results.get(datasetFacetId);
            Set<Integer> mergedDatasetIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDatasetDepth = new HashMap<>();

            if (existingDatasetResult != null && existingDatasetResult.getIds() != null) {
                mergedDatasetIds.addAll(existingDatasetResult.getIds());
                if (existingDatasetResult.getDepthById() != null) {
                    mergedDatasetDepth.putAll(existingDatasetResult.getDepthById());
                }
            }

            mergedDatasetIds.addAll(allDatasetIds);
            for (Map.Entry<Integer, Integer> entry : datasetDepth.entrySet()) {
                mergedDatasetDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> datasetRows = loadDatasetRows(mergedDatasetIds);
            int datasetTotal = computeAccessibleTotalCount(datasetFacetId,
                    datasetRows != null ? datasetRows.size() : 0);
            results.put(datasetFacetId,
                    new FacetResult(mergedDatasetIds, true, mergedDatasetDepth, datasetRows, datasetTotal));
        }

        // Add Attribute results
        if (!allAttributeIds.isEmpty()) {
            String attributeFacetId = "ATTRIBUTE";
            FacetResult existingAttributeResult = results.get(attributeFacetId);
            Set<Integer> mergedAttributeIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedAttributeDepth = new HashMap<>();

            if (existingAttributeResult != null && existingAttributeResult.getIds() != null) {
                mergedAttributeIds.addAll(existingAttributeResult.getIds());
                if (existingAttributeResult.getDepthById() != null) {
                    mergedAttributeDepth.putAll(existingAttributeResult.getDepthById());
                }
            }

            mergedAttributeIds.addAll(allAttributeIds);
            for (Map.Entry<Integer, Integer> entry : attributeDepth.entrySet()) {
                mergedAttributeDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> attributeRows = loadAttributeRows(mergedAttributeIds);
            int attributeTotal = computeAccessibleTotalCount(attributeFacetId,
                    attributeRows != null ? attributeRows.size() : 0);
            results.put(attributeFacetId,
                    new FacetResult(mergedAttributeIds, true, mergedAttributeDepth, attributeRows, attributeTotal));
        }

        // Add Glossary results
        if (!allGlossaryIds.isEmpty()) {
            String glossaryFacetId = "GLOSSARY";
            FacetResult existingGlossaryResult = results.get(glossaryFacetId);
            Set<Integer> mergedGlossaryIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedGlossaryDepth = new HashMap<>();

            if (existingGlossaryResult != null && existingGlossaryResult.getIds() != null) {
                mergedGlossaryIds.addAll(existingGlossaryResult.getIds());
                if (existingGlossaryResult.getDepthById() != null) {
                    mergedGlossaryDepth.putAll(existingGlossaryResult.getDepthById());
                }
            }

            mergedGlossaryIds.addAll(allGlossaryIds);
            for (Map.Entry<Integer, Integer> entry : glossaryDepth.entrySet()) {
                mergedGlossaryDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> glossaryRows = loadGlossaryRows(mergedGlossaryIds);
            int glossaryTotal = computeAccessibleTotalCount(glossaryFacetId,
                    glossaryRows != null ? glossaryRows.size() : 0);
            results.put(glossaryFacetId,
                    new FacetResult(mergedGlossaryIds, true, mergedGlossaryDepth, glossaryRows, glossaryTotal));
        }

        // Add Interface results (source or target system = this seed system)
        if (!allInterfaceIds.isEmpty()) {
            String interfaceFacetId = "INTERFACE";
            FacetResult existingInterfaceResult = results.get(interfaceFacetId);
            Set<Integer> mergedInterfaceIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedInterfaceDepth = new HashMap<>();

            if (existingInterfaceResult != null && existingInterfaceResult.getIds() != null) {
                mergedInterfaceIds.addAll(existingInterfaceResult.getIds());
                if (existingInterfaceResult.getDepthById() != null) {
                    mergedInterfaceDepth.putAll(existingInterfaceResult.getDepthById());
                }
            }

            mergedInterfaceIds.addAll(allInterfaceIds);
            for (Map.Entry<Integer, Integer> entry : interfaceDepth.entrySet()) {
                mergedInterfaceDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> interfaceRows = loadInterfaceRows(mergedInterfaceIds);
            int interfaceTotal = computeAccessibleTotalCount(interfaceFacetId,
                    interfaceRows != null ? interfaceRows.size() : 0);
            results.put(interfaceFacetId,
                    new FacetResult(mergedInterfaceIds, true, mergedInterfaceDepth, interfaceRows, interfaceTotal));
        }

        // Add Product results (from impact relationships)
        if (!allProductIds.isEmpty()) {
            String productFacetId = "PRODUCT";
            FacetResult existingProductResult = results.get(productFacetId);
            Set<Integer> mergedProductIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedProductDepth = new HashMap<>();

            if (existingProductResult != null && existingProductResult.getIds() != null) {
                mergedProductIds.addAll(existingProductResult.getIds());
                if (existingProductResult.getDepthById() != null) {
                    mergedProductDepth.putAll(existingProductResult.getDepthById());
                }
            }

            mergedProductIds.addAll(allProductIds);
            mergedProductDepth.putAll(productDepth);

            List<Map<String, Object>> productRows = loadProductRows(mergedProductIds);
            int productTotal = computeAccessibleTotalCount(productFacetId,
                    productRows != null ? productRows.size() : 0);
            results.put(productFacetId,
                    new FacetResult(mergedProductIds, true, mergedProductDepth, productRows, productTotal));
        }

        // Add Client results (from impact relationships)
        if (!allClientIds.isEmpty()) {
            String clientFacetId = "CLIENT";
            FacetResult existingClientResult = results.get(clientFacetId);
            Set<Integer> mergedClientIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedClientDepth = new HashMap<>();

            if (existingClientResult != null && existingClientResult.getIds() != null) {
                mergedClientIds.addAll(existingClientResult.getIds());
                if (existingClientResult.getDepthById() != null) {
                    mergedClientDepth.putAll(existingClientResult.getDepthById());
                }
            }

            mergedClientIds.addAll(allClientIds);
            mergedClientDepth.putAll(clientDepth);

            List<Map<String, Object>> clientRows = loadClientRows(mergedClientIds);
            int clientTotal = computeAccessibleTotalCount(clientFacetId,
                    clientRows != null ? clientRows.size() : 0);
            results.put(clientFacetId,
                    new FacetResult(mergedClientIds, true, mergedClientDepth, clientRows, clientTotal));
        }

        // Add Legal Entity results (from impact relationships)
        if (!allLegalEntityIds.isEmpty()) {
            String legalFacetId = "LEGAL_ENTITY";
            FacetResult existingLegalResult = results.get(legalFacetId);
            Set<Integer> mergedLegalIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedLegalDepth = new HashMap<>();

            if (existingLegalResult != null && existingLegalResult.getIds() != null) {
                mergedLegalIds.addAll(existingLegalResult.getIds());
                if (existingLegalResult.getDepthById() != null) {
                    mergedLegalDepth.putAll(existingLegalResult.getDepthById());
                }
            }

            mergedLegalIds.addAll(allLegalEntityIds);
            mergedLegalDepth.putAll(legalEntityDepth);

            List<Map<String, Object>> legalRows = loadLegalEntityRows(mergedLegalIds);
            int legalTotal = computeAccessibleTotalCount(legalFacetId,
                    legalRows != null ? legalRows.size() : 0);
            results.put(legalFacetId,
                    new FacetResult(mergedLegalIds, true, mergedLegalDepth, legalRows, legalTotal));
        }

        // Add Process results (from impact relationships)
        if (!allProcessIds.isEmpty()) {
            String processFacetId = "PROCESS";
            FacetResult existingProcessResult = results.get(processFacetId);
            Set<Integer> mergedProcessIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedProcessDepth = new HashMap<>();

            if (existingProcessResult != null && existingProcessResult.getIds() != null) {
                mergedProcessIds.addAll(existingProcessResult.getIds());
                if (existingProcessResult.getDepthById() != null) {
                    mergedProcessDepth.putAll(existingProcessResult.getDepthById());
                }
            }

            mergedProcessIds.addAll(allProcessIds);
            mergedProcessDepth.putAll(processDepth);

            List<Map<String, Object>> processRows = loadProcessRows(mergedProcessIds);
            int processTotal = computeAccessibleTotalCount(processFacetId,
                    processRows != null ? processRows.size() : 0);
            results.put(processFacetId,
                    new FacetResult(mergedProcessIds, true, mergedProcessDepth, processRows, processTotal));
        }

        // Add Project results (from system junction tables)
        if (!allProjectIds.isEmpty()) {
            String projectFacetId = "PROJECT";
            FacetResult existingProjectResult = results.get(projectFacetId);
            Set<Integer> mergedProjectIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedProjectDepth = new HashMap<>();

            if (existingProjectResult != null && existingProjectResult.getIds() != null) {
                mergedProjectIds.addAll(existingProjectResult.getIds());
                if (existingProjectResult.getDepthById() != null) {
                    mergedProjectDepth.putAll(existingProjectResult.getDepthById());
                }
            }

            mergedProjectIds.addAll(allProjectIds);
            mergedProjectDepth.putAll(projectDepth);

            List<Map<String, Object>> projectRows = loadProjectRows(mergedProjectIds);
            int projectTotal = computeAccessibleTotalCount(projectFacetId,
                    projectRows != null ? projectRows.size() : 0);
            results.put(projectFacetId,
                    new FacetResult(mergedProjectIds, true, mergedProjectDepth, projectRows, projectTotal));
        }

        // Add Business Area results
        if (!allBusinessAreaIds.isEmpty()) {
            String baFacetId = "BUSINESS_AREA";
            FacetResult existingBaResult = results.get(baFacetId);
            Set<Integer> mergedBaIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedBaDepth = new HashMap<>();

            if (existingBaResult != null && existingBaResult.getIds() != null) {
                mergedBaIds.addAll(existingBaResult.getIds());
                if (existingBaResult.getDepthById() != null) {
                    mergedBaDepth.putAll(existingBaResult.getDepthById());
                }
            }

            mergedBaIds.addAll(allBusinessAreaIds);
            mergedBaDepth.putAll(businessAreaDepth);

            List<Map<String, Object>> baRows = loadBusinessAreaRows(mergedBaIds);
            int baTotal = computeAccessibleTotalCount(baFacetId, baRows != null ? baRows.size() : 0);
            results.put(baFacetId, new FacetResult(mergedBaIds, true, mergedBaDepth, baRows, baTotal));
        }

        // Add Capability results
        if (!allCapabilityIds.isEmpty()) {
            String capFacetId = "CAPABILITY";
            FacetResult existingCapResult = results.get(capFacetId);
            Set<Integer> mergedCapIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedCapDepth = new HashMap<>();

            if (existingCapResult != null && existingCapResult.getIds() != null) {
                mergedCapIds.addAll(existingCapResult.getIds());
                if (existingCapResult.getDepthById() != null) {
                    mergedCapDepth.putAll(existingCapResult.getDepthById());
                }
            }

            mergedCapIds.addAll(allCapabilityIds);
            mergedCapDepth.putAll(capabilityDepth);

            List<Map<String, Object>> capRows = loadCapabilityRows(mergedCapIds);
            int capTotal = computeAccessibleTotalCount(capFacetId, capRows != null ? capRows.size() : 0);
            results.put(capFacetId, new FacetResult(mergedCapIds, true, mergedCapDepth, capRows, capTotal));
        }

        // Add Policy results
        if (!allPolicyIds.isEmpty()) {
            String policyFacetId = "POLICY";
            FacetResult existingPolicyResult = results.get(policyFacetId);
            Set<Integer> mergedPolicyIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedPolicyDepth = new HashMap<>();

            if (existingPolicyResult != null && existingPolicyResult.getIds() != null) {
                mergedPolicyIds.addAll(existingPolicyResult.getIds());
                if (existingPolicyResult.getDepthById() != null) {
                    mergedPolicyDepth.putAll(existingPolicyResult.getDepthById());
                }
            }

            mergedPolicyIds.addAll(allPolicyIds);
            mergedPolicyDepth.putAll(policyDepth);

            List<Map<String, Object>> policyRows = loadPolicyRows(mergedPolicyIds);
            int policyTotal = computeAccessibleTotalCount(policyFacetId,
                    policyRows != null ? policyRows.size() : 0);
            results.put(policyFacetId,
                    new FacetResult(mergedPolicyIds, true, mergedPolicyDepth, policyRows, policyTotal));
        }

        return results;
    }

    /**
     * Reverse junction links to a legal entity (when not only system_x_legal / dataset_x_legal).
     */
    private void collectLegalEntityCrossFacetLinks(Integer legalId, int linkDepth,
            Set<Integer> geographyIds, Map<Integer, Integer> geographyDepth,
            Set<Integer> capabilityIds, Map<Integer, Integer> capabilityDepth,
            Set<Integer> productIds, Map<Integer, Integer> productDepth,
            Set<Integer> policyIds, Map<Integer, Integer> policyDepth,
            Set<Integer> processIds, Map<Integer, Integer> processDepth) {
        if (legalId == null) {
            return;
        }
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper =
                    new com.example.unisonsearch.repository.DatabaseHelper();
            List<Map<String, Object>> rows;

            rows = dbHelper.executeQuery(
                    "SELECT DISTINCT Geography_ID FROM legal_x_geography WHERE Legal_ID = ?",
                    List.of(legalId));
            for (Map<String, Object> row : rows) {
                Object id = row.get("Geography_ID");
                if (id instanceof Number) {
                    int gid = ((Number) id).intValue();
                    geographyIds.add(gid);
                    geographyDepth.merge(gid, linkDepth, (a, b) -> {
                        if (a == null) {
                            return b;
                        }
                        if (b == null) {
                            return a;
                        }
                        return Math.min(a, b);
                    });
                }
            }

            rows = dbHelper.executeQuery(
                    "SELECT DISTINCT Capability_ID FROM capability_x_legal WHERE Legal_ID = ?",
                    List.of(legalId));
            for (Map<String, Object> row : rows) {
                Object id = row.get("Capability_ID");
                if (id instanceof Number) {
                    int cid = ((Number) id).intValue();
                    capabilityIds.add(cid);
                    capabilityDepth.merge(cid, linkDepth, (a, b) -> {
                        if (a == null) {
                            return b;
                        }
                        if (b == null) {
                            return a;
                        }
                        return Math.min(a, b);
                    });
                }
            }

            rows = dbHelper.executeQuery(
                    "SELECT DISTINCT Product_ID FROM product_x_legal WHERE Legal_ID = ?",
                    List.of(legalId));
            for (Map<String, Object> row : rows) {
                Object id = row.get("Product_ID");
                if (id instanceof Number) {
                    int pid = ((Number) id).intValue();
                    productIds.add(pid);
                    productDepth.merge(pid, linkDepth, (a, b) -> {
                        if (a == null) {
                            return b;
                        }
                        if (b == null) {
                            return a;
                        }
                        return Math.min(a, b);
                    });
                }
            }

            rows = dbHelper.executeQuery(
                    "SELECT DISTINCT Policy_ID FROM policy_x_legal WHERE Legal_ID = ?",
                    List.of(legalId));
            for (Map<String, Object> row : rows) {
                Object id = row.get("Policy_ID");
                if (id instanceof Number) {
                    int pid = ((Number) id).intValue();
                    policyIds.add(pid);
                    policyDepth.merge(pid, linkDepth, (a, b) -> {
                        if (a == null) {
                            return b;
                        }
                        if (b == null) {
                            return a;
                        }
                        return Math.min(a, b);
                    });
                }
            }

            rows = dbHelper.executeQuery(
                    "SELECT DISTINCT Process_ID FROM process_x_legal WHERE Legal_ID = ?",
                    List.of(legalId));
            for (Map<String, Object> row : rows) {
                Object id = row.get("Process_ID");
                if (id instanceof Number) {
                    int pid = ((Number) id).intValue();
                    processIds.add(pid);
                    processDepth.merge(pid, linkDepth, (a, b) -> {
                        if (a == null) {
                            return b;
                        }
                        if (b == null) {
                            return a;
                        }
                        return Math.min(a, b);
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] collectLegalEntityCrossFacetLinks legal " + legalId + ": "
                    + e.getMessage());
        }
    }

    /**
     * Enrich Legal Entity search: from {@code system_x_legal} add systems, their datasets, and
     * dataset-direct glossaries only (not attribute glossaries). From {@code dataset_x_legal} add
     * the dataset, its system, attributes, dataset glossaries, and attribute glossaries.
     */
    private Map<String, FacetResult> enrichLegalEntityFacet(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        FacetResult legalResult = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("LEGAL_ENTITY")
                    || facetId.toUpperCase().equals("LEGALENTITY")
                    || facetId.toUpperCase().equals("LEGAL_ENTITIES"))) {
                legalResult = entry.getValue();
                break;
            }
        }

        if (legalResult == null || legalResult.getIds() == null || legalResult.getIds().isEmpty()) {
            return results;
        }

        Set<Integer> seedLegalIds = new HashSet<>();
        if (legalResult.getDepthById() != null) {
            for (Integer legalId : legalResult.getIds()) {
                if (legalResult.getDepthById().getOrDefault(legalId, Integer.MAX_VALUE) == 0) {
                    seedLegalIds.add(legalId);
                }
            }
        } else {
            seedLegalIds.addAll(legalResult.getIds());
        }
        if (seedLegalIds.isEmpty()) {
            return results;
        }

        Set<Integer> allSystemIds = new HashSet<>();
        Set<Integer> allDatasetIds = new HashSet<>();
        Set<Integer> allAttributeIds = new HashSet<>();
        Set<Integer> allGlossaryIds = new HashSet<>();
        Map<Integer, Integer> systemDepth = new HashMap<>();
        Map<Integer, Integer> datasetDepth = new HashMap<>();
        Map<Integer, Integer> attributeDepth = new HashMap<>();
        Map<Integer, Integer> glossaryDepth = new HashMap<>();
        Set<Integer> extGeographyIds = new HashSet<>();
        Map<Integer, Integer> extGeographyDepth = new HashMap<>();
        Set<Integer> extCapabilityIds = new HashSet<>();
        Map<Integer, Integer> extCapabilityDepth = new HashMap<>();
        Set<Integer> extProductIds = new HashSet<>();
        Map<Integer, Integer> extProductDepth = new HashMap<>();
        Set<Integer> extPolicyIds = new HashSet<>();
        Map<Integer, Integer> extPolicyDepth = new HashMap<>();
        Set<Integer> extProcessIds = new HashSet<>();
        Map<Integer, Integer> extProcessDepth = new HashMap<>();

        for (Integer legalId : seedLegalIds) {
            int base = legalResult.getDepthById() != null
                    ? legalResult.getDepthById().getOrDefault(legalId, 0)
                    : 0;
            try {
                // --- Legal → system → datasets in system: glossaries from dataset only (not attributes)
                Set<Integer> linkedSystems = queryLegalLinkedSystemIds(legalId);
                for (Integer systemId : linkedSystems) {
                    int dSys = base + 1;
                    allSystemIds.add(systemId);
                    systemDepth.merge(systemId, dSys,
                            (a, b) -> a == null ? b : (b == null ? a : Integer.min(a, b)));
                    context.markEnriched("SYSTEM", systemId, dSys);

                    Set<Integer> dsInSystem = querySystemDatasets(systemId);
                    for (Integer datasetId : dsInSystem) {
                        int dDs = base + 2;
                        allDatasetIds.add(datasetId);
                        datasetDepth.merge(datasetId, dDs,
                                (a, b) -> a == null ? b : (b == null ? a : Integer.min(a, b)));
                        context.markEnriched("DATASET", datasetId, dDs);

                        for (Integer gId : queryDatasetDirectGlossaries(datasetId)) {
                            int dG = base + 3;
                            allGlossaryIds.add(gId);
                            glossaryDepth.merge(gId, dG,
                                    (a, b) -> a == null ? b : (b == null ? a : Integer.min(a, b)));
                            context.markEnriched("GLOSSARY", gId, dG);
                        }
                    }
                }

                // --- Legal → dataset directly: system, attributes, dataset + attribute glossaries
                Set<Integer> linkedDatasets = queryLegalLinkedDatasetIds(legalId);
                for (Integer datasetId : linkedDatasets) {
                    int dDs = base + 1;
                    allDatasetIds.add(datasetId);
                    datasetDepth.merge(datasetId, dDs,
                            (a, b) -> a == null ? b : (b == null ? a : Integer.min(a, b)));
                    context.markEnriched("DATASET", datasetId, dDs);

                    for (Integer systemId : queryDatasetSystems(datasetId)) {
                        int dSys = base + 2;
                        allSystemIds.add(systemId);
                        systemDepth.merge(systemId, dSys,
                                (a, b) -> a == null ? b : (b == null ? a : Integer.min(a, b)));
                        context.markEnriched("SYSTEM", systemId, dSys);
                    }

                    Set<Integer> attrIds = queryDatasetAttributes(datasetId);
                    for (Integer attrId : attrIds) {
                        int dAt = base + 2;
                        allAttributeIds.add(attrId);
                        attributeDepth.merge(attrId, dAt,
                                (a, b) -> a == null ? b : (b == null ? a : Integer.min(a, b)));
                        context.markEnriched("ATTRIBUTE", attrId, dAt);
                    }

                    for (Integer gId : queryDatasetDirectGlossaries(datasetId)) {
                        int dG = base + 2;
                        allGlossaryIds.add(gId);
                        glossaryDepth.merge(gId, dG,
                                (a, b) -> a == null ? b : (b == null ? a : Integer.min(a, b)));
                        context.markEnriched("GLOSSARY", gId, dG);
                    }

                    for (Integer attrId : attrIds) {
                        for (Integer gId : queryAttributeGlossaries(attrId)) {
                            int dG = base + 3;
                            allGlossaryIds.add(gId);
                            glossaryDepth.merge(gId, dG,
                                    (a, b) -> a == null ? b : (b == null ? a : Integer.min(a, b)));
                            context.markEnriched("GLOSSARY", gId, dG);
                        }
                    }
                }

                collectLegalEntityCrossFacetLinks(legalId, base + 1,
                        extGeographyIds, extGeographyDepth,
                        extCapabilityIds, extCapabilityDepth,
                        extProductIds, extProductDepth,
                        extPolicyIds, extPolicyDepth,
                        extProcessIds, extProcessDepth);
            } catch (Exception e) {
                System.err.println("[UnisonSearchService] enrichLegalEntityFacet legal " + legalId + ": "
                        + e.getMessage());
            }
        }

        if (!allSystemIds.isEmpty()) {
            String fid = "SYSTEM";
            FacetResult ex = results.get(fid);
            Set<Integer> merged = new LinkedHashSet<>();
            Map<Integer, Integer> mergedD = new HashMap<>();
            if (ex != null && ex.getIds() != null) {
                merged.addAll(ex.getIds());
                if (ex.getDepthById() != null) {
                    mergedD.putAll(ex.getDepthById());
                }
            }
            merged.addAll(allSystemIds);
            for (Map.Entry<Integer, Integer> e : systemDepth.entrySet()) {
                mergedD.merge(e.getKey(), e.getValue(),
                        (a, b) -> (a == null || b == null) ? (a != null ? a : b)
                                : Integer.compare(a, b) <= 0 ? a : b);
            }
            List<Map<String, Object>> rows = loadSystemRows(merged);
            int tot = computeAccessibleTotalCount(fid, rows != null ? rows.size() : 0);
            results.put(fid, new FacetResult(merged, true, mergedD, rows, tot));
        }
        if (!allDatasetIds.isEmpty()) {
            String fid = "DATASET";
            FacetResult ex = results.get(fid);
            Set<Integer> merged = new LinkedHashSet<>();
            Map<Integer, Integer> mergedD = new HashMap<>();
            if (ex != null && ex.getIds() != null) {
                merged.addAll(ex.getIds());
                if (ex.getDepthById() != null) {
                    mergedD.putAll(ex.getDepthById());
                }
            }
            merged.addAll(allDatasetIds);
            for (Map.Entry<Integer, Integer> e : datasetDepth.entrySet()) {
                mergedD.merge(e.getKey(), e.getValue(),
                        (a, b) -> (a == null || b == null) ? (a != null ? a : b)
                                : Integer.compare(a, b) <= 0 ? a : b);
            }
            List<Map<String, Object>> rows = loadDatasetRows(merged);
            int tot = computeAccessibleTotalCount(fid, rows != null ? rows.size() : 0);
            results.put(fid, new FacetResult(merged, true, mergedD, rows, tot));
        }
        if (!allAttributeIds.isEmpty()) {
            String fid = "ATTRIBUTE";
            FacetResult ex = results.get(fid);
            Set<Integer> merged = new LinkedHashSet<>();
            Map<Integer, Integer> mergedD = new HashMap<>();
            if (ex != null && ex.getIds() != null) {
                merged.addAll(ex.getIds());
                if (ex.getDepthById() != null) {
                    mergedD.putAll(ex.getDepthById());
                }
            }
            merged.addAll(allAttributeIds);
            for (Map.Entry<Integer, Integer> e : attributeDepth.entrySet()) {
                mergedD.merge(e.getKey(), e.getValue(),
                        (a, b) -> (a == null || b == null) ? (a != null ? a : b)
                                : Integer.compare(a, b) <= 0 ? a : b);
            }
            List<Map<String, Object>> rows = loadAttributeRows(merged);
            int tot = computeAccessibleTotalCount(fid, rows != null ? rows.size() : 0);
            results.put(fid, new FacetResult(merged, true, mergedD, rows, tot));
        }
        if (!allGlossaryIds.isEmpty()) {
            String fid = "GLOSSARY";
            FacetResult ex = results.get(fid);
            Set<Integer> merged = new LinkedHashSet<>();
            Map<Integer, Integer> mergedD = new HashMap<>();
            if (ex != null && ex.getIds() != null) {
                merged.addAll(ex.getIds());
                if (ex.getDepthById() != null) {
                    mergedD.putAll(ex.getDepthById());
                }
            }
            merged.addAll(allGlossaryIds);
            for (Map.Entry<Integer, Integer> e : glossaryDepth.entrySet()) {
                mergedD.merge(e.getKey(), e.getValue(),
                        (a, b) -> (a == null || b == null) ? (a != null ? a : b)
                                : Integer.compare(a, b) <= 0 ? a : b);
            }
            List<Map<String, Object>> rows = loadGlossaryRows(merged);
            int tot = computeAccessibleTotalCount(fid, rows != null ? rows.size() : 0);
            results.put(fid, new FacetResult(merged, true, mergedD, rows, tot));
        }

        if (!extGeographyIds.isEmpty()) {
            mergeFacetEnrichmentBatch(results, "GEOGRAPHY", extGeographyIds, extGeographyDepth);
        }
        if (!extCapabilityIds.isEmpty()) {
            mergeFacetEnrichmentBatch(results, "CAPABILITY", extCapabilityIds, extCapabilityDepth);
        }
        if (!extProductIds.isEmpty()) {
            mergeFacetEnrichmentBatch(results, "PRODUCT", extProductIds, extProductDepth);
        }
        if (!extPolicyIds.isEmpty()) {
            mergeFacetEnrichmentBatch(results, "POLICY", extPolicyIds, extPolicyDepth);
        }
        if (!extProcessIds.isEmpty()) {
            mergeFacetEnrichmentBatch(results, "PROCESS", extProcessIds, extProcessDepth);
        }

        return results;
    }

    /**
     * Enrich Glossary facet with datasets, attributes, and systems.
     */
    private Map<String, FacetResult> enrichGlossaryFacet(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        FacetResult glossaryResult = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("GLOSSARY") ||
                    facetId.toUpperCase().equals("GLOSSARIES"))) {
                glossaryResult = entry.getValue();
                break;
            }
        }

        if (glossaryResult == null || glossaryResult.getIds() == null || glossaryResult.getIds().isEmpty()) {
            return results;
        }

        Set<Integer> allDatasetIds = new HashSet<>();
        Set<Integer> allAttributeIds = new HashSet<>();
        Set<Integer> allSystemIds = new HashSet<>();
        Set<Integer> allProductIds = new HashSet<>();
        Set<Integer> allClientIds = new HashSet<>();
        Set<Integer> allProcessIds = new HashSet<>();
        Set<Integer> allProjectIds = new HashSet<>();
        Set<Integer> allPolicyIds = new HashSet<>();
        Set<Integer> allCapabilityIds = new HashSet<>();
        Set<Integer> allBusinessAreaIds = new HashSet<>();
        Set<Integer> allRelatedGlossaryIds = new HashSet<>();
        Map<Integer, Integer> datasetDepth = new HashMap<>();
        Map<Integer, Integer> attributeDepth = new HashMap<>();
        Map<Integer, Integer> systemDepth = new HashMap<>();
        Map<Integer, Integer> productDepth = new HashMap<>();
        Map<Integer, Integer> clientDepth = new HashMap<>();
        Map<Integer, Integer> processDepth = new HashMap<>();
        Map<Integer, Integer> projectDepth = new HashMap<>();
        Map<Integer, Integer> policyDepth = new HashMap<>();
        Map<Integer, Integer> capabilityDepth = new HashMap<>();
        Map<Integer, Integer> businessAreaDepth = new HashMap<>();
        Map<Integer, Integer> relatedGlossaryDepth = new HashMap<>();

        // Get seed glossary IDs (depth 0) - only process direct relations from seed objects
        Set<Integer> seedGlossaryIds = new HashSet<>();
        if (glossaryResult.getDepthById() != null) {
            for (Integer glossaryId : glossaryResult.getIds()) {
                int depth = glossaryResult.getDepthById().getOrDefault(glossaryId, Integer.MAX_VALUE);
                if (depth == 0) {
                    seedGlossaryIds.add(glossaryId);
                }
            }
        } else {
            seedGlossaryIds.addAll(glossaryResult.getIds());
        }
        
        // Get seed IDs to avoid adding them if they're already seed objects
        Set<Integer> seedDatasetIds = getSeedObjectIds(results, "DATASET");
        Set<Integer> seedAttributeIds = getSeedObjectIds(results, "ATTRIBUTE");
        // Datasets with dataset.glossary = a seed glossary: show all attributes on that dataset
        Set<Integer> datasetsWithGlossaryFieldLink = new LinkedHashSet<>();

        for (Integer glossaryId : seedGlossaryIds) {
            try {
                // Get the depth of this glossary (should be 0 for seed, but check to be safe)
                int glossaryDepthVal = glossaryResult.getDepthById() != null ? 
                    glossaryResult.getDepthById().getOrDefault(glossaryId, 0) : 0;
                
                // Datasets directly attached via dataset.glossary: full attribute expansion + systems
                Set<Integer> datasetIds = queryGlossaryDatasets(glossaryId);
                for (Integer datasetId : datasetIds) {
                    // Skip if this dataset is already a seed object (direct relation only)
                    if (seedDatasetIds.contains(datasetId)) {
                        continue;
                    }
                    // Calculate depth: glossary depth + 1
                    int newDepth = glossaryDepthVal + 1;
                    // Only add if depth <= 1 (direct relation from seed glossary)
                    if (newDepth <= 1) {
                        allDatasetIds.add(datasetId);
                        datasetDepth.put(datasetId, newDepth);
                        datasetsWithGlossaryFieldLink.add(datasetId);

                        for (Integer attrId : queryPPPDatasetAttributeIds(datasetId)) {
                            if (attrId == null) {
                                continue;
                            }
                            allAttributeIds.add(attrId);
                            int attrD = glossaryDepthVal + 2;
                            attributeDepth.merge(attrId, attrD, (a, b) -> {
                                if (a == null) {
                                    return b;
                                }
                                if (b == null) {
                                    return a;
                                }
                                return Math.min(a, b);
                            });
                        }

                        // For GLOSSARY facet, depth 2 is allowed for systems of datasets
                        Set<Integer> systemIds = queryDatasetSystems(datasetId);
                        for (Integer systemId : systemIds) {
                            int sysDepth = glossaryDepthVal + 2; // Glossary -> Dataset -> System
                            int existingSysDepth = context.getDepth("SYSTEM", systemId);
                            if (sysDepth < existingSysDepth) {
                                allSystemIds.add(systemId);
                                systemDepth.put(systemId, sysDepth);
                                context.markEnriched("SYSTEM", systemId, sysDepth);
                            }
                        }
                    }
                }

                // Query impact relationships for this glossary
                Map<String, Set<Integer>> impactRelationships = queryGlossaryImpactRelationships(glossaryId);
                for (Map.Entry<String, Set<Integer>> entry : impactRelationships.entrySet()) {
                    String impactFacetId = entry.getKey();
                    Set<Integer> impactIds = entry.getValue();
                    int newDepth = glossaryDepthVal + 1;
                    
                    if (impactFacetId.equals("PRODUCT") && newDepth <= 1) {
                        for (Integer productId : impactIds) {
                            allProductIds.add(productId);
                            productDepth.putIfAbsent(productId, newDepth);
                        }
                    } else if (impactFacetId.equals("CLIENT") && newDepth <= 1) {
                        for (Integer clientId : impactIds) {
                            allClientIds.add(clientId);
                            clientDepth.putIfAbsent(clientId, newDepth);
                        }
                    } else if (impactFacetId.equals("SYSTEM") && newDepth <= 1) {
                        for (Integer systemId : impactIds) {
                            allSystemIds.add(systemId);
                            systemDepth.putIfAbsent(systemId, newDepth);
                        }
                    } else if (impactFacetId.equals("PROCESS") && newDepth <= 1) {
                        for (Integer processId : impactIds) {
                            allProcessIds.add(processId);
                            processDepth.putIfAbsent(processId, newDepth);
                        }
                    } else if (impactFacetId.equals("PROJECT") && newDepth <= 1) {
                        for (Integer projectId : impactIds) {
                            allProjectIds.add(projectId);
                            projectDepth.putIfAbsent(projectId, newDepth);
                        }
                    } else if (impactFacetId.equals("POLICY") && newDepth <= 1) {
                        for (Integer policyId : impactIds) {
                            allPolicyIds.add(policyId);
                            policyDepth.putIfAbsent(policyId, newDepth);
                        }
                    } else if (impactFacetId.equals("CAPABILITY") && newDepth <= 1) {
                        for (Integer capId : impactIds) {
                            allCapabilityIds.add(capId);
                            capabilityDepth.putIfAbsent(capId, newDepth);
                        }
                    } else if (impactFacetId.equals("BUSINESS_AREA") && newDepth <= 1) {
                        for (Integer baId : impactIds) {
                            allBusinessAreaIds.add(baId);
                            businessAreaDepth.putIfAbsent(baId, newDepth);
                        }
                    }
                }
                
                // Query same-type relationships (glossary → glossary)
                Set<Integer> relatedIds = queryGlossaryRelationships(glossaryId);
                for (Integer relatedId : relatedIds) {
                    if (!seedGlossaryIds.contains(relatedId)) {  // Don't add if already seed
                        int newDepth = glossaryDepthVal + 1;
                        if (newDepth <= 1) {
                            allRelatedGlossaryIds.add(relatedId);
                            relatedGlossaryDepth.putIfAbsent(relatedId, newDepth);
                        }
                    }
                }
                
                // Attributes with Glossary_ID = seed: only these attrs for their dataset unless dataset
                // was already included via dataset.glossary (full expansion for that dataset).
                Set<Integer> attributeIds = queryGlossaryAttributes(glossaryId);
                for (Integer attributeId : attributeIds) {
                    if (seedAttributeIds.contains(attributeId)) {
                        continue;
                    }
                    int newDepthAttr = glossaryDepthVal + 1;
                    if (newDepthAttr > 1) {
                        continue;
                    }
                    Integer attrDatasetId = queryAttributeDataset(attributeId);
                    if (attrDatasetId != null && datasetsWithGlossaryFieldLink.contains(attrDatasetId)) {
                        continue;
                    }
                    allAttributeIds.add(attributeId);
                    attributeDepth.merge(attributeId, newDepthAttr, (a, b) -> {
                        if (a == null) {
                            return b;
                        }
                        if (b == null) {
                            return a;
                        }
                        return Math.min(a, b);
                    });

                    if (attrDatasetId != null) {
                        if (!seedDatasetIds.contains(attrDatasetId)) {
                            allDatasetIds.add(attrDatasetId);
                            int dsDepth = glossaryDepthVal + 2;
                            datasetDepth.merge(attrDatasetId, dsDepth, (a, b) -> {
                                if (a == null) {
                                    return b;
                                }
                                if (b == null) {
                                    return a;
                                }
                                return Math.min(a, b);
                            });
                        }
                        Set<Integer> systemIds = queryDatasetSystems(attrDatasetId);
                        for (Integer systemId : systemIds) {
                            int sysDepth = glossaryDepthVal + 2;
                            int existingSysDepth = context.getDepth("SYSTEM", systemId);
                            if (sysDepth < existingSysDepth) {
                                allSystemIds.add(systemId);
                                systemDepth.put(systemId, sysDepth);
                                context.markEnriched("SYSTEM", systemId, sysDepth);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[UnisonSearchService] Error enriching glossary " + glossaryId + ": " + e.getMessage());
            }
        }

        // Add Dataset results
        if (!allDatasetIds.isEmpty()) {
            String datasetFacetId = "DATASET";
            FacetResult existingDatasetResult = results.get(datasetFacetId);
            Set<Integer> mergedDatasetIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDatasetDepth = new HashMap<>();

            if (existingDatasetResult != null && existingDatasetResult.getIds() != null) {
                mergedDatasetIds.addAll(existingDatasetResult.getIds());
                if (existingDatasetResult.getDepthById() != null) {
                    mergedDatasetDepth.putAll(existingDatasetResult.getDepthById());
                }
            }

            mergedDatasetIds.addAll(allDatasetIds);
            for (Map.Entry<Integer, Integer> entry : datasetDepth.entrySet()) {
                mergedDatasetDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> datasetRows = loadDatasetRows(mergedDatasetIds);
            int datasetTotal = computeAccessibleTotalCount(datasetFacetId,
                    datasetRows != null ? datasetRows.size() : 0);
            results.put(datasetFacetId,
                    new FacetResult(mergedDatasetIds, true, mergedDatasetDepth, datasetRows, datasetTotal));
        }

        // Add Attribute results
        if (!allAttributeIds.isEmpty()) {
            String attributeFacetId = "ATTRIBUTE";
            FacetResult existingAttributeResult = results.get(attributeFacetId);
            Set<Integer> mergedAttributeIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedAttributeDepth = new HashMap<>();

            if (existingAttributeResult != null && existingAttributeResult.getIds() != null) {
                mergedAttributeIds.addAll(existingAttributeResult.getIds());
                if (existingAttributeResult.getDepthById() != null) {
                    mergedAttributeDepth.putAll(existingAttributeResult.getDepthById());
                }
            }

            mergedAttributeIds.addAll(allAttributeIds);
            for (Map.Entry<Integer, Integer> entry : attributeDepth.entrySet()) {
                mergedAttributeDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> attributeRows = loadAttributeRows(mergedAttributeIds);
            int attributeTotal = computeAccessibleTotalCount(attributeFacetId,
                    attributeRows != null ? attributeRows.size() : 0);
            results.put(attributeFacetId,
                    new FacetResult(mergedAttributeIds, true, mergedAttributeDepth, attributeRows, attributeTotal));
        }

        // Add System results
        if (!allSystemIds.isEmpty()) {
            String systemFacetId = "SYSTEM";
            FacetResult existingSystemResult = results.get(systemFacetId);
            Set<Integer> mergedSystemIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedSystemDepth = new HashMap<>();

            if (existingSystemResult != null && existingSystemResult.getIds() != null) {
                mergedSystemIds.addAll(existingSystemResult.getIds());
                if (existingSystemResult.getDepthById() != null) {
                    mergedSystemDepth.putAll(existingSystemResult.getDepthById());
                }
            }

            mergedSystemIds.addAll(allSystemIds);
            for (Map.Entry<Integer, Integer> entry : systemDepth.entrySet()) {
                mergedSystemDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> systemRows = loadSystemRows(mergedSystemIds);
            int systemTotal = computeAccessibleTotalCount(systemFacetId, systemRows != null ? systemRows.size() : 0);
            results.put(systemFacetId,
                    new FacetResult(mergedSystemIds, true, mergedSystemDepth, systemRows, systemTotal));
        }

        // Add Product results (from impact relationships)
        if (!allProductIds.isEmpty()) {
            String productFacetId = "PRODUCT";
            FacetResult existingProductResult = results.get(productFacetId);
            Set<Integer> mergedProductIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedProductDepth = new HashMap<>();

            if (existingProductResult != null && existingProductResult.getIds() != null) {
                mergedProductIds.addAll(existingProductResult.getIds());
                if (existingProductResult.getDepthById() != null) {
                    mergedProductDepth.putAll(existingProductResult.getDepthById());
                }
            }

            mergedProductIds.addAll(allProductIds);
            mergedProductDepth.putAll(productDepth);

            List<Map<String, Object>> productRows = loadProductRows(mergedProductIds);
            int productTotal = computeAccessibleTotalCount(productFacetId,
                    productRows != null ? productRows.size() : 0);
            results.put(productFacetId,
                    new FacetResult(mergedProductIds, true, mergedProductDepth, productRows, productTotal));
        }

        // Add Client results (from impact relationships)
        if (!allClientIds.isEmpty()) {
            String clientFacetId = "CLIENT";
            FacetResult existingClientResult = results.get(clientFacetId);
            Set<Integer> mergedClientIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedClientDepth = new HashMap<>();

            if (existingClientResult != null && existingClientResult.getIds() != null) {
                mergedClientIds.addAll(existingClientResult.getIds());
                if (existingClientResult.getDepthById() != null) {
                    mergedClientDepth.putAll(existingClientResult.getDepthById());
                }
            }

            mergedClientIds.addAll(allClientIds);
            mergedClientDepth.putAll(clientDepth);

            List<Map<String, Object>> clientRows = loadClientRows(mergedClientIds);
            int clientTotal = computeAccessibleTotalCount(clientFacetId,
                    clientRows != null ? clientRows.size() : 0);
            results.put(clientFacetId,
                    new FacetResult(mergedClientIds, true, mergedClientDepth, clientRows, clientTotal));
        }

        // Add Process / Project / Policy / Capability / Business Area from glossary junction tables
        if (!allProcessIds.isEmpty()) {
            String processFacetId = "PROCESS";
            FacetResult existing = results.get(processFacetId);
            Set<Integer> mergedIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDepth = new HashMap<>();
            if (existing != null && existing.getIds() != null) {
                mergedIds.addAll(existing.getIds());
                if (existing.getDepthById() != null) {
                    mergedDepth.putAll(existing.getDepthById());
                }
            }
            mergedIds.addAll(allProcessIds);
            mergedDepth.putAll(processDepth);
            List<Map<String, Object>> rows = loadProcessRows(mergedIds);
            int total = computeAccessibleTotalCount(processFacetId, rows != null ? rows.size() : 0);
            results.put(processFacetId, new FacetResult(mergedIds, true, mergedDepth, rows, total));
        }
        if (!allProjectIds.isEmpty()) {
            String projectFacetId = "PROJECT";
            FacetResult existing = results.get(projectFacetId);
            Set<Integer> mergedIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDepth = new HashMap<>();
            if (existing != null && existing.getIds() != null) {
                mergedIds.addAll(existing.getIds());
                if (existing.getDepthById() != null) {
                    mergedDepth.putAll(existing.getDepthById());
                }
            }
            mergedIds.addAll(allProjectIds);
            mergedDepth.putAll(projectDepth);
            List<Map<String, Object>> rows = loadProjectRows(mergedIds);
            int total = computeAccessibleTotalCount(projectFacetId, rows != null ? rows.size() : 0);
            results.put(projectFacetId, new FacetResult(mergedIds, true, mergedDepth, rows, total));
        }
        if (!allPolicyIds.isEmpty()) {
            String policyFacetId = "POLICY";
            FacetResult existing = results.get(policyFacetId);
            Set<Integer> mergedIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDepth = new HashMap<>();
            if (existing != null && existing.getIds() != null) {
                mergedIds.addAll(existing.getIds());
                if (existing.getDepthById() != null) {
                    mergedDepth.putAll(existing.getDepthById());
                }
            }
            mergedIds.addAll(allPolicyIds);
            mergedDepth.putAll(policyDepth);
            List<Map<String, Object>> rows = loadPolicyRows(mergedIds);
            int total = computeAccessibleTotalCount(policyFacetId, rows != null ? rows.size() : 0);
            results.put(policyFacetId, new FacetResult(mergedIds, true, mergedDepth, rows, total));
        }
        if (!allCapabilityIds.isEmpty()) {
            String capFacetId = "CAPABILITY";
            FacetResult existing = results.get(capFacetId);
            Set<Integer> mergedIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDepth = new HashMap<>();
            if (existing != null && existing.getIds() != null) {
                mergedIds.addAll(existing.getIds());
                if (existing.getDepthById() != null) {
                    mergedDepth.putAll(existing.getDepthById());
                }
            }
            mergedIds.addAll(allCapabilityIds);
            mergedDepth.putAll(capabilityDepth);
            List<Map<String, Object>> rows = loadCapabilityRows(mergedIds);
            int total = computeAccessibleTotalCount(capFacetId, rows != null ? rows.size() : 0);
            results.put(capFacetId, new FacetResult(mergedIds, true, mergedDepth, rows, total));
        }
        if (!allBusinessAreaIds.isEmpty()) {
            String baFacetId = "BUSINESS_AREA";
            FacetResult existing = results.get(baFacetId);
            Set<Integer> mergedIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDepth = new HashMap<>();
            if (existing != null && existing.getIds() != null) {
                mergedIds.addAll(existing.getIds());
                if (existing.getDepthById() != null) {
                    mergedDepth.putAll(existing.getDepthById());
                }
            }
            mergedIds.addAll(allBusinessAreaIds);
            mergedDepth.putAll(businessAreaDepth);
            List<Map<String, Object>> rows = loadBusinessAreaRows(mergedIds);
            int total = computeAccessibleTotalCount(baFacetId, rows != null ? rows.size() : 0);
            results.put(baFacetId, new FacetResult(mergedIds, true, mergedDepth, rows, total));
        }

        // Add related Glossary results (from same-type relationships)
        if (!allRelatedGlossaryIds.isEmpty()) {
            String glossaryFacetId = "GLOSSARY";
            FacetResult existingGlossaryResult = results.get(glossaryFacetId);
            Set<Integer> mergedGlossaryIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedGlossaryDepth = new HashMap<>();

            if (existingGlossaryResult != null && existingGlossaryResult.getIds() != null) {
                mergedGlossaryIds.addAll(existingGlossaryResult.getIds());
                if (existingGlossaryResult.getDepthById() != null) {
                    mergedGlossaryDepth.putAll(existingGlossaryResult.getDepthById());
                }
            }

            mergedGlossaryIds.addAll(allRelatedGlossaryIds);
            mergedGlossaryDepth.putAll(relatedGlossaryDepth);

            List<Map<String, Object>> glossaryRows = loadGlossaryRows(mergedGlossaryIds);
            int glossaryTotal = computeAccessibleTotalCount(glossaryFacetId,
                    glossaryRows != null ? glossaryRows.size() : 0);
            results.put(glossaryFacetId,
                    new FacetResult(mergedGlossaryIds, true, mergedGlossaryDepth, glossaryRows, glossaryTotal));
        }

        return results;
    }

    /**
     * Extract role IDs from object_x_people IDs.
     * Role search returns object_x_people.ID values, but we need object_role.id (RoleID) values.
     */
    private Set<Integer> getRoleIdsFromObjectXPeople(Set<Integer> objectXPeopleIds) throws SQLException {
        if (objectXPeopleIds == null || objectXPeopleIds.isEmpty()) {
            return new HashSet<>();
        }

        Set<Integer> roleIds = new HashSet<>();
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(objectXPeopleIds.size(), "?"));
            String sql = "SELECT DISTINCT RoleID FROM object_x_people WHERE ID IN (" + placeholders + ")";

            List<Object> params = new ArrayList<>(objectXPeopleIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object roleId = row.get("RoleID");
                if (roleId != null) {
                    if (roleId instanceof Integer) {
                        roleIds.add((Integer) roleId);
                    } else if (roleId instanceof Number) {
                        roleIds.add(((Number) roleId).intValue());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error extracting RoleIDs from object_x_people: " + e.getMessage());
        }

        return roleIds;
    }

    /**
     * Enrich People facet with org unit, stakeholder objects, roles, CRs raised, and active tasks.
     */
    private Map<String, FacetResult> enrichPeopleFacet(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        FacetResult peopleResult = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("PEOPLE") ||
                    facetId.toUpperCase().equals("PERSON"))) {
                peopleResult = entry.getValue();
                break;
            }
        }

        if (peopleResult == null || peopleResult.getIds() == null || peopleResult.getIds().isEmpty()) {
            return results;
        }

        Set<Integer> allOrgUnitIds = new HashSet<>();
        /** object_x_people.ID values — never use RoleID IN (...) (that pulls every assignment with those role types). */
        Set<Integer> allPersonRoleAssignmentOxpIds = new HashSet<>();
        Set<Integer> allCRIds = new HashSet<>();
        Set<Integer> allTaskIds = new HashSet<>();
        Map<String, Set<Integer>> stakeholderObjects = new HashMap<>();
        Map<Integer, Integer> orgUnitDepth = new HashMap<>();
        Map<Integer, Integer> roleAssignmentOxpDepth = new HashMap<>();
        Map<Integer, Integer> crDepth = new HashMap<>();
        Map<Integer, Integer> taskDepth = new HashMap<>();

        // Get seed people IDs (depth 0) - only process direct relations from seed objects
        Set<Integer> seedPeopleIds = new HashSet<>();
        if (peopleResult.getDepthById() != null) {
            for (Integer peopleId : peopleResult.getIds()) {
                int depth = peopleResult.getDepthById().getOrDefault(peopleId, Integer.MAX_VALUE);
                if (depth == 0) {
                    seedPeopleIds.add(peopleId);
                }
            }
        } else {
            seedPeopleIds.addAll(peopleResult.getIds());
        }
        
        // Get seed IDs to avoid adding them if they're already seed objects
        Set<Integer> seedOrgUnitIds = getSeedObjectIds(results, "ORG_UNIT");
        
        for (Integer peopleId : seedPeopleIds) {
            try {
                // Get the depth of this person (should be 0 for seed, but check to be safe)
                int peopleDepth = peopleResult.getDepthById() != null ? 
                    peopleResult.getDepthById().getOrDefault(peopleId, 0) : 0;
                
                // Get org unit of this person
                Integer orgUnitId = queryPeopleOrgUnit(peopleId);
                if (orgUnitId != null) {
                    // Skip if this org unit is already a seed object (direct relation only)
                    if (seedOrgUnitIds.contains(orgUnitId)) {
                        // Still add it but don't skip - org unit is a direct relation
                    }
                    // Calculate depth: people depth + 1
                    int newDepth = peopleDepth + 1;
                    // Only add if depth <= 1 (direct relation from seed person)
                    if (newDepth <= 1) {
                        allOrgUnitIds.add(orgUnitId);
                        orgUnitDepth.put(orgUnitId, newDepth);
                    }
                }

                // Get objects where person is a direct stakeholder - but only if they're not already seed objects
                Map<String, Set<Integer>> objects = queryPeopleStakeholderObjects(peopleId);
                for (Map.Entry<String, Set<Integer>> entry : objects.entrySet()) {
                    String objectFacet = entry.getKey();
                    Set<Integer> objectIds = entry.getValue();
                    // Filter out seed objects to ensure direct relations only
                    Set<Integer> seedObjectIdsForFacet = getSeedObjectIds(results, objectFacet);
                    Set<Integer> filteredObjectIds = new HashSet<>();
                    // Calculate depth: people depth + 1
                    int newDepth = peopleDepth + 1;
                    // Only add if depth <= 1 (direct relation from seed person)
                    if (newDepth <= 1) {
                        for (Integer objectId : objectIds) {
                            if (!seedObjectIdsForFacet.contains(objectId)) {
                                filteredObjectIds.add(objectId);
                            }
                        }
                        if (!filteredObjectIds.isEmpty()) {
                            stakeholderObjects.computeIfAbsent(objectFacet, k -> new HashSet<>()).addAll(filteredObjectIds);
                        }
                    }
                }

                // Objects created or last-updated by this person (same depth / seed rules as stakeholders)
                Map<String, Set<Integer>> createdByMap = queryObjectsByPersonAuditField(peopleId, "createdBy");
                Map<String, Set<Integer>> updatedByMap = queryObjectsByPersonAuditField(peopleId, "updatedBy");
                for (Map<String, Set<Integer>> extra : Arrays.asList(createdByMap, updatedByMap)) {
                    for (Map.Entry<String, Set<Integer>> entry : extra.entrySet()) {
                        String objectFacet = entry.getKey();
                        Set<Integer> objectIds = entry.getValue();
                        Set<Integer> seedObjectIdsForFacet = getSeedObjectIds(results, objectFacet);
                        Set<Integer> filteredObjectIds = new HashSet<>();
                        int newDepth = peopleDepth + 1;
                        if (newDepth <= 1) {
                            for (Integer objectId : objectIds) {
                                if (!seedObjectIdsForFacet.contains(objectId)) {
                                    filteredObjectIds.add(objectId);
                                }
                            }
                            if (!filteredObjectIds.isEmpty()) {
                                stakeholderObjects.computeIfAbsent(objectFacet, k -> new HashSet<>()).addAll(filteredObjectIds);
                            }
                        }
                    }
                }

                // Role assignments for this person only (object_x_people rows), not all rows sharing their RoleIDs
                Set<Integer> personOxpIds = queryObjectXPeopleIdsForPerson(peopleId);
                int roleRelDepth = peopleDepth + 1;
                if (roleRelDepth <= 1) {
                    for (Integer oxpId : personOxpIds) {
                        allPersonRoleAssignmentOxpIds.add(oxpId);
                        roleAssignmentOxpDepth.putIfAbsent(oxpId, roleRelDepth);
                    }
                }

                // Get CRs raised by this person
                Set<Integer> crIds = queryPeopleCRsRaised(peopleId);
                for (Integer crId : crIds) {
                    // Calculate depth: people depth + 1
                    int newDepth = peopleDepth + 1;
                    // Only add if depth <= 1 (direct relation from seed person)
                    if (newDepth <= 1) {
                        allCRIds.add(crId);
                        crDepth.put(crId, newDepth);
                    }
                }

                // Get active tasks that belong to the CRs raised by this person
                Set<Integer> taskIds = queryActiveTasksByCRIds(crIds);
                for (Integer taskId : taskIds) {
                    // Calculate depth: people depth + 1
                    int newDepth = peopleDepth + 1;
                    // Only add if depth <= 1 (direct relation from seed person)
                    if (newDepth <= 1) {
                        allTaskIds.add(taskId);
                        taskDepth.put(taskId, newDepth);
                    }
                }
            } catch (Exception e) {
                System.err.println("[UnisonSearchService] Error enriching people " + peopleId + ": " + e.getMessage());
            }
        }

        // Add Org Unit results
        if (!allOrgUnitIds.isEmpty()) {
            String orgUnitFacetId = "ORG_UNIT";
            FacetResult existingOrgUnitResult = results.get(orgUnitFacetId);
            Set<Integer> mergedOrgUnitIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedOrgUnitDepth = new HashMap<>();

            if (existingOrgUnitResult != null && existingOrgUnitResult.getIds() != null) {
                mergedOrgUnitIds.addAll(existingOrgUnitResult.getIds());
                if (existingOrgUnitResult.getDepthById() != null) {
                    mergedOrgUnitDepth.putAll(existingOrgUnitResult.getDepthById());
                }
            }

            mergedOrgUnitIds.addAll(allOrgUnitIds);
            mergedOrgUnitDepth.putAll(orgUnitDepth);

            List<Map<String, Object>> orgUnitRows = loadOrgUnitRows(mergedOrgUnitIds);
            results.put(orgUnitFacetId,
                    facetResultFromLoadedRows(orgUnitFacetId, mergedOrgUnitDepth, orgUnitRows, true));
        }

        // Add Role results (facet IDs are object_x_people assignment rows)
        if (!allPersonRoleAssignmentOxpIds.isEmpty()) {
            String roleFacetId = "ROLE";
            FacetResult existingRoleResult = results.get(roleFacetId);
            Set<Integer> mergedOxpIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedRoleDepth = new HashMap<>();

            if (existingRoleResult != null && existingRoleResult.getIds() != null) {
                mergedOxpIds.addAll(existingRoleResult.getIds());
                if (existingRoleResult.getDepthById() != null) {
                    mergedRoleDepth.putAll(existingRoleResult.getDepthById());
                }
            }

            mergedOxpIds.addAll(allPersonRoleAssignmentOxpIds);
            for (Map.Entry<Integer, Integer> entry : roleAssignmentOxpDepth.entrySet()) {
                mergedRoleDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> roleRows = loadRoleRowsByObjectXPeopleIds(mergedOxpIds);
            results.put(roleFacetId, facetResultFromLoadedRows(roleFacetId, mergedRoleDepth, roleRows, true));
        }

        // Add stakeholder objects to results
        for (Map.Entry<String, Set<Integer>> entry : stakeholderObjects.entrySet()) {
            String facetId = entry.getKey();
            Set<Integer> objectIds = entry.getValue();
            if (objectIds.isEmpty()) {
                continue;
            }

            FacetResult existingResult = results.get(facetId);
            Set<Integer> mergedIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDepth = new HashMap<>();

            if (existingResult != null && existingResult.getIds() != null) {
                mergedIds.addAll(existingResult.getIds());
                if (existingResult.getDepthById() != null) {
                    mergedDepth.putAll(existingResult.getDepthById());
                }
            }

            mergedIds.addAll(objectIds);
            for (Integer objectId : objectIds) {
                if (!mergedDepth.containsKey(objectId)) {
                    mergedDepth.put(objectId, 1);
                }
            }

            // Load rows based on facet type
            List<Map<String, Object>> rows = loadFacetRows(facetId, mergedIds);
            results.put(facetId, facetResultFromLoadedRows(facetId, mergedDepth, rows, true));
        }

        // Add Change Request results
        if (!allCRIds.isEmpty()) {
            String crFacetId = "CHANGE_REQUEST";
            FacetResult existingCRResult = results.get(crFacetId);
            Set<Integer> mergedCRIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedCRDepth = new HashMap<>();

            if (existingCRResult != null && existingCRResult.getIds() != null) {
                mergedCRIds.addAll(existingCRResult.getIds());
                if (existingCRResult.getDepthById() != null) {
                    mergedCRDepth.putAll(existingCRResult.getDepthById());
                }
            }

            mergedCRIds.addAll(allCRIds);
            mergedCRDepth.putAll(crDepth);

            List<Map<String, Object>> crRows = loadChangeRequestRows(mergedCRIds);
            results.put(crFacetId, facetResultFromLoadedRows(crFacetId, mergedCRDepth, crRows, true));
        }

        // Add Active Tasks results
        if (!allTaskIds.isEmpty()) {
            String taskFacetId = "ACTIVE_TASKS";
            FacetResult existingTaskResult = results.get(taskFacetId);
            Set<Integer> mergedTaskIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedTaskDepth = new HashMap<>();

            if (existingTaskResult != null && existingTaskResult.getIds() != null) {
                mergedTaskIds.addAll(existingTaskResult.getIds());
                if (existingTaskResult.getDepthById() != null) {
                    mergedTaskDepth.putAll(existingTaskResult.getDepthById());
                }
            }

            mergedTaskIds.addAll(allTaskIds);
            mergedTaskDepth.putAll(taskDepth);

            List<Map<String, Object>> taskRows = loadActiveTaskRows(mergedTaskIds);
            // For Active Tasks, use actual rows count as total (not the full accessible count)
            // This ensures the displayed count matches the actual data shown
            Set<Integer> taskIdsFromRows = idsFromLoadedRows(taskRows);
            Map<Integer, Integer> taskDepthFromRows = depthSubsetForLoadedIds(taskIdsFromRows, mergedTaskDepth);
            int taskTotal = taskRows != null ? taskRows.size() : 0;
            results.put(taskFacetId, new FacetResult(taskIdsFromRows, true, taskDepthFromRows, taskRows, taskTotal));
        }

        return results;
    }

    /**
     * Enrich Role facet with related objects from all facets.
     * When searching for roles, also display the objects that have those roles assigned.
     */
    private Map<String, FacetResult> enrichRoleFacet(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        // Find ROLE facet in results
        FacetResult roleResult = results.get("ROLE");
        if (roleResult == null || roleResult.getIds() == null || roleResult.getIds().isEmpty()) {
            return results;
        }

        // Get seed role IDs (depth 0) - only process direct relations from seed roles
        Set<Integer> seedObjectXPeopleIds = new HashSet<>();
        if (roleResult.getDepthById() != null) {
            for (Integer objXPeopleId : roleResult.getIds()) {
                int depth = roleResult.getDepthById().getOrDefault(objXPeopleId, Integer.MAX_VALUE);
                if (depth == 0) {
                    seedObjectXPeopleIds.add(objXPeopleId);
                }
            }
        } else {
            seedObjectXPeopleIds.addAll(roleResult.getIds());
        }

        if (seedObjectXPeopleIds.isEmpty()) {
            return results;
        }

        // Business object(s) for these assignment rows only (junction → object_x_people.ID)
        Map<String, Set<Integer>> stakeholderObjects = queryObjectsByObjectXPeopleIds(seedObjectXPeopleIds);

        // People assigned on these rows + org units of those people
        Set<Integer> personIds = queryPersonIdsForObjectXPeopleIds(seedObjectXPeopleIds);
        Set<Integer> orgUnitIds = new LinkedHashSet<>();
        Map<Integer, Integer> orgUnitDepth = new HashMap<>();
        for (Integer pid : personIds) {
            Integer ou = queryPeopleOrgUnit(pid);
            if (ou != null) {
                orgUnitIds.add(ou);
                orgUnitDepth.putIfAbsent(ou, 1);
            }
        }

        String peopleFacetId = "PEOPLE";
        if (!personIds.isEmpty()) {
            FacetResult existingPeople = results.get(peopleFacetId);
            Set<Integer> mergedPeopleIds = new LinkedHashSet<>();
            Map<Integer, Integer> peopleDepth = new HashMap<>();
            if (existingPeople != null && existingPeople.getIds() != null) {
                mergedPeopleIds.addAll(existingPeople.getIds());
                if (existingPeople.getDepthById() != null) {
                    peopleDepth.putAll(existingPeople.getDepthById());
                }
            }
            mergedPeopleIds.addAll(personIds);
            for (Integer pid : personIds) {
                peopleDepth.putIfAbsent(pid, 1);
            }
            List<Map<String, Object>> peopleRows = loadPeopleRows(mergedPeopleIds);
            results.put(peopleFacetId, facetResultFromLoadedRows(peopleFacetId, peopleDepth, peopleRows, true));
        }

        if (!orgUnitIds.isEmpty()) {
            String orgUnitFacetId = "ORG_UNIT";
            FacetResult existingOu = results.get(orgUnitFacetId);
            Set<Integer> mergedOuIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedOuDepth = new HashMap<>();
            if (existingOu != null && existingOu.getIds() != null) {
                mergedOuIds.addAll(existingOu.getIds());
                if (existingOu.getDepthById() != null) {
                    mergedOuDepth.putAll(existingOu.getDepthById());
                }
            }
            mergedOuIds.addAll(orgUnitIds);
            mergedOuDepth.putAll(orgUnitDepth);
            List<Map<String, Object>> ouRows = loadOrgUnitRows(mergedOuIds);
            results.put(orgUnitFacetId, facetResultFromLoadedRows(orgUnitFacetId, mergedOuDepth, ouRows, true));
        }

        // Add each facet's stakeholder objects to results
        for (Map.Entry<String, Set<Integer>> entry : stakeholderObjects.entrySet()) {
            String facetId = entry.getKey();
            Set<Integer> objectIds = entry.getValue();

            if (objectIds.isEmpty()) {
                continue;
            }

            FacetResult existingResult = results.get(facetId);
            Set<Integer> mergedIds = new LinkedHashSet<>();
            Map<Integer, Integer> depthMap = new HashMap<>();

            if (existingResult != null && existingResult.getIds() != null) {
                mergedIds.addAll(existingResult.getIds());
                if (existingResult.getDepthById() != null) {
                    depthMap.putAll(existingResult.getDepthById());
                }
            }

            mergedIds.addAll(objectIds);
            for (Integer objectId : objectIds) {
                depthMap.putIfAbsent(objectId, 1);
            }

            List<Map<String, Object>> rows = loadFacetRows(facetId, mergedIds);
            results.put(facetId, facetResultFromLoadedRows(facetId, depthMap, rows, true));
        }

        return results;
    }

    /**
     * Enrich Project/Process/Policy facet with glossaries, datasets, attributes, systems, and impact relationships.
     */
    private Map<String, FacetResult> enrichProjectProcessPolicyFacet(Map<String, FacetResult> results,
            EnrichmentContext context) throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        // Check for Project, Process, or Policy facets (accept plural keys e.g. PROCESSES)
        Map<String, FacetResult> facetResults = new HashMap<>();
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null) {
                String canon = canonicalFacetId(facetId);
                if ("PROJECT".equals(canon) || "PROCESS".equals(canon) || "POLICY".equals(canon)) {
                    facetResults.put(facetId, entry.getValue());
                }
            }
        }

        if (facetResults.isEmpty()) {
            return results;
        }

        Set<Integer> allGlossaryIds = new HashSet<>();
        Set<Integer> allDatasetIds = new HashSet<>();
        Set<Integer> allAttributeIds = new LinkedHashSet<>();
        Set<Integer> allSystemIds = new HashSet<>();
        Set<Integer> allRelatedPolicyIds = new HashSet<>();
        Set<Integer> allRelatedProjectIds = new HashSet<>();
        Map<Integer, Integer> glossaryDepth = new HashMap<>();
        Map<Integer, Integer> datasetDepth = new HashMap<>();
        Map<Integer, Integer> attributeDepth = new HashMap<>();
        Map<Integer, Integer> systemDepth = new HashMap<>();
        Map<Integer, Integer> relatedPolicyDepth = new HashMap<>();
        Map<Integer, Integer> relatedProjectDepth = new HashMap<>();
        Set<Integer> datasetsWithFullAttributeExpansion = new LinkedHashSet<>();
        Map<Integer, Set<Integer>> partialAttributesByDataset = new HashMap<>();
        Set<Integer> standalonePppAttributeIds = new LinkedHashSet<>();

        for (Map.Entry<String, FacetResult> entry : facetResults.entrySet()) {
            String facetId = entry.getKey();
            FacetResult fr = entry.getValue();
            if (fr == null || fr.getIds() == null || fr.getIds().isEmpty()) {
                continue;
            }

            // Get seed object IDs (depth 0). If depth metadata is missing or empty (e.g. after row
            // population dropped depth keys), treat all IDs as search seeds so impact enrichment still runs.
            Set<Integer> seedObjectIds = new HashSet<>();
            Map<Integer, Integer> depthById = fr.getDepthById();
            if (depthById != null && !depthById.isEmpty()) {
                for (Integer objectId : fr.getIds()) {
                    int depth = depthById.getOrDefault(objectId, 0);
                    if (depth == 0) {
                        seedObjectIds.add(objectId);
                    }
                }
            } else {
                seedObjectIds.addAll(fr.getIds());
            }
            
            // Get seed IDs to avoid adding them if they're already seed objects
            Set<Integer> seedGlossaryIds = getSeedObjectIds(results, "GLOSSARY");
            
            for (Integer objectId : seedObjectIds) {
                try {
                    // Get the depth of this object (should be 0 for seed, but check to be safe)
                    int objectDepth = depthById != null && !depthById.isEmpty()
                            ? depthById.getOrDefault(objectId, 0)
                            : 0;
                    
                    // Get glossaries linked to this object (depth 1 - direct relation only)
                    Set<Integer> glossaryIds = queryObjectGlossaries(facetId, objectId);
                    for (Integer glossaryId : glossaryIds) {
                        // Skip if this glossary is already a seed object (direct relation only)
                        if (seedGlossaryIds.contains(glossaryId)) {
                            continue;
                        }
                        // Calculate depth: object depth + 1
                        int newDepth = objectDepth + 1;
                        // Only add if depth <= 1 (direct relation from seed object)
                        if (newDepth <= 1) {
                            allGlossaryIds.add(glossaryId);
                            if (!glossaryDepth.containsKey(glossaryId)) {
                                glossaryDepth.put(glossaryId, newDepth);
                            }
                        }
                    }

                    // For each directly-linked glossary, expand to datasets/attributes/systems
                    // that belong to that glossary — all treated as depth 1 from the PPP seed.
                    for (Integer glossaryId : glossaryIds) {
                        if (seedGlossaryIds.contains(glossaryId)) continue;

                        // Datasets whose dataset.glossary = this glossary
                        Set<Integer> gDatasetsFromField = queryGlossaryLinkedDatasetsViaField(glossaryId);
                        for (Integer dsId : gDatasetsFromField) {
                            allDatasetIds.add(dsId);
                            datasetDepth.putIfAbsent(dsId, 1);
                            Set<Integer> dsSysIds = queryDatasetSystems(dsId);
                            for (Integer sysId : dsSysIds) {
                                allSystemIds.add(sysId);
                                systemDepth.putIfAbsent(sysId, 1);
                            }
                            markPppDatasetFullAttributes(datasetsWithFullAttributeExpansion, partialAttributesByDataset,
                                    dsId);
                            addGlossaryIdsFromDatasetAttributes(dsId, allGlossaryIds, glossaryDepth);
                        }

                        // Attributes whose Glossary_ID = this glossary: dataset row + only these attributes (unless full)
                        Set<Integer> gAttrIds = queryGlossaryLinkedAttributes(glossaryId);
                        for (Integer attrId : gAttrIds) {
                            Integer attrDsId = queryAttributeDataset(attrId);
                            if (attrDsId != null) {
                                allDatasetIds.add(attrDsId);
                                datasetDepth.putIfAbsent(attrDsId, 1);
                                Set<Integer> attrSysIds = queryDatasetSystems(attrDsId);
                                for (Integer sysId : attrSysIds) {
                                    allSystemIds.add(sysId);
                                    systemDepth.putIfAbsent(sysId, 1);
                                }
                                markPppDatasetPartialAttribute(datasetsWithFullAttributeExpansion,
                                        partialAttributesByDataset, attrDsId, attrId);
                            } else {
                                standalonePppAttributeIds.add(attrId);
                            }
                            Integer attrGlossaryId = queryPPPAttributeGlossary(attrId);
                            if (attrGlossaryId != null) {
                                allGlossaryIds.add(attrGlossaryId);
                                glossaryDepth.putIfAbsent(attrGlossaryId, 1);
                            }
                        }
                    }

                    // For each dataset directly linked to this PPP object: full attribute expansion + glossaries
                    Set<Integer> directDatasetIds = queryPPPLinkedDatasets(facetId, objectId);
                    for (Integer dsId : directDatasetIds) {
                        allDatasetIds.add(dsId);
                        datasetDepth.putIfAbsent(dsId, 1);
                        // System
                        Set<Integer> dsSysIds = queryDatasetSystems(dsId);
                        for (Integer sysId : dsSysIds) {
                            allSystemIds.add(sysId);
                            systemDepth.putIfAbsent(sysId, 1);
                        }
                        // Glossary from dataset.glossary field
                        Set<Integer> dsGlossaries = queryDatasetDirectGlossaries(dsId);
                        for (Integer gId : dsGlossaries) {
                            allGlossaryIds.add(gId);
                            glossaryDepth.putIfAbsent(gId, 1);
                        }
                        markPppDatasetFullAttributes(datasetsWithFullAttributeExpansion, partialAttributesByDataset, dsId);
                        addGlossaryIdsFromDatasetAttributes(dsId, allGlossaryIds, glossaryDepth);
                    }

                    // PPP→attribute: show attribute + parent dataset/system/glossaries; only that attribute unless dataset full
                    Set<Integer> directAttrIds = queryPPPLinkedAttributes(facetId, objectId);
                    for (Integer attrId : directAttrIds) {
                        Integer attrDsId = queryAttributeDataset(attrId);
                        if (attrDsId != null) {
                            allDatasetIds.add(attrDsId);
                            datasetDepth.putIfAbsent(attrDsId, 1);
                            Set<Integer> attrSysIds = queryDatasetSystems(attrDsId);
                            for (Integer sysId : attrSysIds) {
                                allSystemIds.add(sysId);
                                systemDepth.putIfAbsent(sysId, 1);
                            }
                            Set<Integer> dsDsGlossaries = queryDatasetDirectGlossaries(attrDsId);
                            for (Integer gId : dsDsGlossaries) {
                                allGlossaryIds.add(gId);
                                glossaryDepth.putIfAbsent(gId, 1);
                            }
                            markPppDatasetPartialAttribute(datasetsWithFullAttributeExpansion, partialAttributesByDataset,
                                    attrDsId, attrId);
                        } else {
                            standalonePppAttributeIds.add(attrId);
                        }
                        Integer attrGlossaryId = queryPPPAttributeGlossary(attrId);
                        if (attrGlossaryId != null) {
                            allGlossaryIds.add(attrGlossaryId);
                            glossaryDepth.putIfAbsent(attrGlossaryId, 1);
                        }
                    }

                    // For each system directly linked to this PPP object: datasets on system use full attribute expansion
                    Set<Integer> directSystemIds = queryPPPLinkedSystems(facetId, objectId);
                    for (Integer sysId : directSystemIds) {
                        allSystemIds.add(sysId);
                        systemDepth.putIfAbsent(sysId, 1);
                        List<Integer> sysDsIds = context != null ? new ArrayList<>() : new ArrayList<>();
                        try {
                            com.example.unisonsearch.repository.DatabaseHelper dbH = new com.example.unisonsearch.repository.DatabaseHelper();
                            List<Map<String, Object>> sdsRows = dbH.executeQuery(
                                "SELECT ID FROM dataset WHERE MasterSource = ? AND DeletedDatetime IS NULL", List.of(sysId));
                            for (Map<String, Object> r : sdsRows) {
                                Object v = r.get("ID");
                                if (v instanceof Integer) sysDsIds.add((Integer) v);
                                else if (v instanceof Number) sysDsIds.add(((Number) v).intValue());
                            }
                        } catch (Exception ignored) {}
                        for (Integer sysDsId : sysDsIds) {
                            allDatasetIds.add(sysDsId);
                            datasetDepth.putIfAbsent(sysDsId, 1);
                            Set<Integer> dsGlossaries = queryDatasetDirectGlossaries(sysDsId);
                            for (Integer gId : dsGlossaries) {
                                allGlossaryIds.add(gId);
                                glossaryDepth.putIfAbsent(gId, 1);
                            }
                            markPppDatasetFullAttributes(datasetsWithFullAttributeExpansion, partialAttributesByDataset,
                                    sysDsId);
                            addGlossaryIdsFromDatasetAttributes(sysDsId, allGlossaryIds, glossaryDepth);
                        }
                    }

                    // Get impact relationships (other facets' objects linked through impact)
                    Map<String, Set<Integer>> impactRelations = queryObjectImpactRelationships(facetId, objectId);
                    for (Map.Entry<String, Set<Integer>> impactEntry : impactRelations.entrySet()) {
                        String impactFacetId = impactEntry.getKey();
                        Set<Integer> impactIds = impactEntry.getValue();
                        if (impactIds.isEmpty()) {
                            continue;
                        }

                        FacetResult existingImpactResult = results.get(impactFacetId);
                        Set<Integer> mergedImpactIds = new LinkedHashSet<>();
                        Map<Integer, Integer> mergedImpactDepth = new HashMap<>();

                        if (existingImpactResult != null && existingImpactResult.getIds() != null) {
                            mergedImpactIds.addAll(existingImpactResult.getIds());
                            if (existingImpactResult.getDepthById() != null) {
                                mergedImpactDepth.putAll(existingImpactResult.getDepthById());
                            }
                        }

                        mergedImpactIds.addAll(impactIds);
                        for (Integer impactId : impactIds) {
                            if (!mergedImpactDepth.containsKey(impactId)) {
                                mergedImpactDepth.put(impactId, 1);
                            }
                        }

                        List<Map<String, Object>> impactRows = loadFacetRows(impactFacetId, mergedImpactIds);
                        int impactTotal = computeAccessibleTotalCount(impactFacetId,
                                impactRows != null ? impactRows.size() : 0);
                        results.put(impactFacetId,
                                new FacetResult(mergedImpactIds, true, mergedImpactDepth, impactRows, impactTotal));
                    }
                    
                    // Get same-type relationships (policy→policy, project→project)
                    String pppCanon = canonicalFacetId(facetId);
                    if ("POLICY".equals(pppCanon)) {
                        Set<Integer> relatedIds = queryPolicyRelationships(objectId);
                        for (Integer relatedId : relatedIds) {
                            int newDepth = objectDepth + 1;
                            if (newDepth <= 1) {
                                allRelatedPolicyIds.add(relatedId);
                                relatedPolicyDepth.putIfAbsent(relatedId, newDepth);
                            }
                        }
                    } else if ("PROJECT".equals(pppCanon)) {
                        Set<Integer> relatedIds = queryProjectRelationships(objectId);
                        for (Integer relatedId : relatedIds) {
                            int newDepth = objectDepth + 1;
                            if (newDepth <= 1) {
                                allRelatedProjectIds.add(relatedId);
                                relatedProjectDepth.putIfAbsent(relatedId, newDepth);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[UnisonSearchService] Error enriching " + facetId + " " + objectId + ": "
                            + e.getMessage());
                }
            }
        }

        reconcilePppProjectProcessPolicyAttributes(datasetsWithFullAttributeExpansion, partialAttributesByDataset,
                standalonePppAttributeIds, allAttributeIds, attributeDepth);

        // Add Glossary results
        if (!allGlossaryIds.isEmpty()) {
            String glossaryFacetId = "GLOSSARY";
            FacetResult existingGlossaryResult = results.get(glossaryFacetId);
            Set<Integer> mergedGlossaryIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedGlossaryDepth = new HashMap<>();

            if (existingGlossaryResult != null && existingGlossaryResult.getIds() != null) {
                mergedGlossaryIds.addAll(existingGlossaryResult.getIds());
                if (existingGlossaryResult.getDepthById() != null) {
                    mergedGlossaryDepth.putAll(existingGlossaryResult.getDepthById());
                }
            }

            mergedGlossaryIds.addAll(allGlossaryIds);
            for (Map.Entry<Integer, Integer> entry : glossaryDepth.entrySet()) {
                mergedGlossaryDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> glossaryRows = loadGlossaryRows(mergedGlossaryIds);
            int glossaryTotal = computeAccessibleTotalCount(glossaryFacetId,
                    glossaryRows != null ? glossaryRows.size() : 0);
            results.put(glossaryFacetId,
                    new FacetResult(mergedGlossaryIds, true, mergedGlossaryDepth, glossaryRows, glossaryTotal));
        }

        // Add Dataset results
        if (!allDatasetIds.isEmpty()) {
            String datasetFacetId = "DATASET";
            FacetResult existingDatasetResult = results.get(datasetFacetId);
            Set<Integer> mergedDatasetIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDatasetDepth = new HashMap<>();

            if (existingDatasetResult != null && existingDatasetResult.getIds() != null) {
                mergedDatasetIds.addAll(existingDatasetResult.getIds());
                if (existingDatasetResult.getDepthById() != null) {
                    mergedDatasetDepth.putAll(existingDatasetResult.getDepthById());
                }
            }

            mergedDatasetIds.addAll(allDatasetIds);
            for (Map.Entry<Integer, Integer> entry : datasetDepth.entrySet()) {
                mergedDatasetDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> datasetRows = loadDatasetRows(mergedDatasetIds);
            int datasetTotal = computeAccessibleTotalCount(datasetFacetId,
                    datasetRows != null ? datasetRows.size() : 0);
            results.put(datasetFacetId,
                    new FacetResult(mergedDatasetIds, true, mergedDatasetDepth, datasetRows, datasetTotal));
        }

        // Add Attribute results
        if (!allAttributeIds.isEmpty()) {
            String attributeFacetId = "ATTRIBUTE";
            FacetResult existingAttributeResult = results.get(attributeFacetId);
            Set<Integer> mergedAttributeIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedAttributeDepth = new HashMap<>();

            if (existingAttributeResult != null && existingAttributeResult.getIds() != null) {
                mergedAttributeIds.addAll(existingAttributeResult.getIds());
                if (existingAttributeResult.getDepthById() != null) {
                    mergedAttributeDepth.putAll(existingAttributeResult.getDepthById());
                }
            }

            mergedAttributeIds.addAll(allAttributeIds);
            for (Map.Entry<Integer, Integer> entry : attributeDepth.entrySet()) {
                mergedAttributeDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> attributeRows = loadAttributeRows(mergedAttributeIds);
            int attributeTotal = computeAccessibleTotalCount(attributeFacetId,
                    attributeRows != null ? attributeRows.size() : 0);
            results.put(attributeFacetId,
                    new FacetResult(mergedAttributeIds, true, mergedAttributeDepth, attributeRows, attributeTotal));
        }

        // Add System results
        if (!allSystemIds.isEmpty()) {
            String systemFacetId = "SYSTEM";
            FacetResult existingSystemResult = results.get(systemFacetId);
            Set<Integer> mergedSystemIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedSystemDepth = new HashMap<>();

            if (existingSystemResult != null && existingSystemResult.getIds() != null) {
                mergedSystemIds.addAll(existingSystemResult.getIds());
                if (existingSystemResult.getDepthById() != null) {
                    mergedSystemDepth.putAll(existingSystemResult.getDepthById());
                }
            }

            mergedSystemIds.addAll(allSystemIds);
            for (Map.Entry<Integer, Integer> entry : systemDepth.entrySet()) {
                mergedSystemDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> systemRows = loadSystemRows(mergedSystemIds);
            int systemTotal = computeAccessibleTotalCount(systemFacetId, systemRows != null ? systemRows.size() : 0);
            results.put(systemFacetId,
                    new FacetResult(mergedSystemIds, true, mergedSystemDepth, systemRows, systemTotal));
        }

        // Add related Policy results (from same-type relationships)
        if (!allRelatedPolicyIds.isEmpty()) {
            String policyFacetId = "POLICY";
            FacetResult existingPolicyResult = results.get(policyFacetId);
            Set<Integer> mergedPolicyIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedPolicyDepth = new HashMap<>();

            if (existingPolicyResult != null && existingPolicyResult.getIds() != null) {
                mergedPolicyIds.addAll(existingPolicyResult.getIds());
                if (existingPolicyResult.getDepthById() != null) {
                    mergedPolicyDepth.putAll(existingPolicyResult.getDepthById());
                }
            }

            mergedPolicyIds.addAll(allRelatedPolicyIds);
            mergedPolicyDepth.putAll(relatedPolicyDepth);

            List<Map<String, Object>> policyRows = loadPolicyRows(mergedPolicyIds);
            int policyTotal = computeAccessibleTotalCount(policyFacetId,
                    policyRows != null ? policyRows.size() : 0);
            results.put(policyFacetId,
                    new FacetResult(mergedPolicyIds, true, mergedPolicyDepth, policyRows, policyTotal));
        }

        // Add related Project results (from same-type relationships)
        if (!allRelatedProjectIds.isEmpty()) {
            String projectFacetId = "PROJECT";
            FacetResult existingProjectResult = results.get(projectFacetId);
            Set<Integer> mergedProjectIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedProjectDepth = new HashMap<>();

            if (existingProjectResult != null && existingProjectResult.getIds() != null) {
                mergedProjectIds.addAll(existingProjectResult.getIds());
                if (existingProjectResult.getDepthById() != null) {
                    mergedProjectDepth.putAll(existingProjectResult.getDepthById());
                }
            }

            mergedProjectIds.addAll(allRelatedProjectIds);
            mergedProjectDepth.putAll(relatedProjectDepth);

            List<Map<String, Object>> projectRows = loadProjectRows(mergedProjectIds);
            int projectTotal = computeAccessibleTotalCount(projectFacetId,
                    projectRows != null ? projectRows.size() : 0);
            results.put(projectFacetId,
                    new FacetResult(mergedProjectIds, true, mergedProjectDepth, projectRows, projectTotal));
        }

        return results;
    }

    /**
     * Enrich Change Request facet with raised upon object, active tasks, stakeholders, and roles.
     */
    private Map<String, FacetResult> enrichChangeRequestFacet(Map<String, FacetResult> results,
            EnrichmentContext context) throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        FacetResult crResult = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().contains("CHANGEREQUEST") ||
                    facetId.toUpperCase().contains("CHANGE_REQUEST"))) {
                crResult = entry.getValue();
                break;
            }
        }

        if (crResult == null || crResult.getIds() == null || crResult.getIds().isEmpty()) {
            return results;
        }

        Set<Integer> allTaskIds = new HashSet<>();
        Set<Integer> allPeopleIds = new HashSet<>();
        Set<Integer> allStakeholderRoleOxpIds = new HashSet<>();
        Map<String, Set<Integer>> raisedUponObjects = new HashMap<>();
        Map<Integer, Integer> taskDepth = new HashMap<>();
        Map<Integer, Integer> peopleDepth = new HashMap<>();
        Map<Integer, Integer> roleOxpDepth = new HashMap<>();

        for (Integer crId : crResult.getIds()) {
            try {
                // Get the object this CR was raised upon
                Map<String, Integer> raisedUpon = queryCRRaisedUponObject(crId);
                if (raisedUpon != null && !raisedUpon.isEmpty()) {
                    for (Map.Entry<String, Integer> entry : raisedUpon.entrySet()) {
                        String objectFacet = entry.getKey();
                        Integer objectId = entry.getValue();
                        raisedUponObjects.computeIfAbsent(objectFacet, k -> new HashSet<>()).add(objectId);
                    }
                }

                // Get active tasks related to this CR
                Set<Integer> taskIds = queryActiveTasksByCRId(crId);
                for (Integer taskId : taskIds) {
                    allTaskIds.add(taskId);
                    taskDepth.put(taskId, 1);
                }

                // Get stakeholders of this CR (same as its object stakeholders)
                if (raisedUpon != null && !raisedUpon.isEmpty()) {
                    for (Map.Entry<String, Integer> entry : raisedUpon.entrySet()) {
                        String objectFacet = entry.getKey();
                        Integer objectId = entry.getValue();
                        List<Map<String, Object>> stakeholders = queryDirectStakeholders(objectFacet, objectId);
                        for (Map<String, Object> stakeholder : stakeholders) {
                            Object peopleIdObj = stakeholder.get("people_id");
                            if (peopleIdObj instanceof Integer) {
                                allPeopleIds.add((Integer) peopleIdObj);
                                if (!peopleDepth.containsKey((Integer) peopleIdObj)) {
                                    peopleDepth.put((Integer) peopleIdObj, 2);
                                }
                            } else if (peopleIdObj instanceof Number) {
                                Integer peopleId = ((Number) peopleIdObj).intValue();
                                allPeopleIds.add(peopleId);
                                if (!peopleDepth.containsKey(peopleId)) {
                                    peopleDepth.put(peopleId, 2);
                                }
                            }

                            Object oxpIdObj = stakeholder.get("object_x_people_id");
                            if (oxpIdObj != null) {
                                Integer oxpId = null;
                                if (oxpIdObj instanceof Integer) {
                                    oxpId = (Integer) oxpIdObj;
                                } else if (oxpIdObj instanceof Number) {
                                    oxpId = ((Number) oxpIdObj).intValue();
                                }
                                if (oxpId != null) {
                                    allStakeholderRoleOxpIds.add(oxpId);
                                    roleOxpDepth.putIfAbsent(oxpId, 2);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[UnisonSearchService] Error enriching change request " + crId + ": " + e.getMessage());
            }
        }

        // Add raised upon objects to results
        for (Map.Entry<String, Set<Integer>> entry : raisedUponObjects.entrySet()) {
            String facetId = entry.getKey();
            Set<Integer> objectIds = entry.getValue();
            if (objectIds.isEmpty()) {
                continue;
            }

            FacetResult existingResult = results.get(facetId);
            Set<Integer> mergedIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDepth = new HashMap<>();

            if (existingResult != null && existingResult.getIds() != null) {
                mergedIds.addAll(existingResult.getIds());
                if (existingResult.getDepthById() != null) {
                    mergedDepth.putAll(existingResult.getDepthById());
                }
            }

            mergedIds.addAll(objectIds);
            for (Integer objectId : objectIds) {
                if (!mergedDepth.containsKey(objectId)) {
                    mergedDepth.put(objectId, 1);
                }
            }

            List<Map<String, Object>> rows = loadFacetRows(facetId, mergedIds);
            results.put(facetId, facetResultFromLoadedRows(facetId, mergedDepth, rows, true));
        }

        // Add Active Tasks results
        if (!allTaskIds.isEmpty()) {
            String taskFacetId = "ACTIVE_TASKS";
            FacetResult existingTaskResult = results.get(taskFacetId);
            Set<Integer> mergedTaskIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedTaskDepth = new HashMap<>();

            if (existingTaskResult != null && existingTaskResult.getIds() != null) {
                mergedTaskIds.addAll(existingTaskResult.getIds());
                if (existingTaskResult.getDepthById() != null) {
                    mergedTaskDepth.putAll(existingTaskResult.getDepthById());
                }
            }

            mergedTaskIds.addAll(allTaskIds);
            mergedTaskDepth.putAll(taskDepth);

            List<Map<String, Object>> taskRows = loadActiveTaskRows(mergedTaskIds);
            // For Active Tasks, use actual rows count as total (not the full accessible count)
            // This ensures the displayed count matches the actual data shown
            Set<Integer> taskIdsFromRows = idsFromLoadedRows(taskRows);
            Map<Integer, Integer> taskDepthFromRows = depthSubsetForLoadedIds(taskIdsFromRows, mergedTaskDepth);
            int taskTotal = taskRows != null ? taskRows.size() : 0;
            results.put(taskFacetId, new FacetResult(taskIdsFromRows, true, taskDepthFromRows, taskRows, taskTotal));
        }

        // Add People results
        if (!allPeopleIds.isEmpty()) {
            String peopleFacetId = "PEOPLE";
            FacetResult existingPeopleResult = results.get(peopleFacetId);
            Set<Integer> mergedPeopleIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedPeopleDepth = new HashMap<>();

            if (existingPeopleResult != null && existingPeopleResult.getIds() != null) {
                mergedPeopleIds.addAll(existingPeopleResult.getIds());
                if (existingPeopleResult.getDepthById() != null) {
                    mergedPeopleDepth.putAll(existingPeopleResult.getDepthById());
                }
            }

            mergedPeopleIds.addAll(allPeopleIds);
            for (Map.Entry<Integer, Integer> entry : peopleDepth.entrySet()) {
                mergedPeopleDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> peopleRows = loadPeopleRows(mergedPeopleIds);
            results.put(peopleFacetId,
                    facetResultFromLoadedRows(peopleFacetId, mergedPeopleDepth, peopleRows, true));
        }

        // Add Role results (facet IDs are object_x_people rows for the raised-upon object only)
        if (!allStakeholderRoleOxpIds.isEmpty()) {
            String roleFacetId = "ROLE";
            FacetResult existingRoleResult = results.get(roleFacetId);
            Set<Integer> mergedOxpIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedRoleDepth = new HashMap<>();

            if (existingRoleResult != null && existingRoleResult.getIds() != null) {
                mergedOxpIds.addAll(existingRoleResult.getIds());
                if (existingRoleResult.getDepthById() != null) {
                    mergedRoleDepth.putAll(existingRoleResult.getDepthById());
                }
            }

            mergedOxpIds.addAll(allStakeholderRoleOxpIds);
            for (Map.Entry<Integer, Integer> entry : roleOxpDepth.entrySet()) {
                mergedRoleDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> roleRows = loadRoleRowsByObjectXPeopleIds(mergedOxpIds);
            results.put(roleFacetId, facetResultFromLoadedRows(roleFacetId, mergedRoleDepth, roleRows, true));
        }

        return results;
    }

    /**
     * Enrich Capability facet with glossaries, datasets, and attributes when linked to glossary.
     */
    private Map<String, FacetResult> enrichCapabilityFacet(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        FacetResult capabilityResult = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("CAPABILITY") ||
                    facetId.toUpperCase().equals("CAPABILITIES"))) {
                capabilityResult = entry.getValue();
                break;
            }
        }

        if (capabilityResult == null || capabilityResult.getIds() == null || capabilityResult.getIds().isEmpty()) {
            return results;
        }

        Set<Integer> allGlossaryIds = new HashSet<>();
        Set<Integer> allDatasetIds = new HashSet<>();
        Set<Integer> allAttributeIds = new HashSet<>();
        Set<Integer> allSystemIds = new HashSet<>();
        Map<Integer, Integer> glossaryDepth = new HashMap<>();
        Map<Integer, Integer> datasetDepth = new HashMap<>();
        Map<Integer, Integer> attributeDepth = new HashMap<>();
        Map<Integer, Integer> systemDepth = new HashMap<>();

        // Get seed capability IDs (depth 0) - only process direct relations from seed objects
        Set<Integer> seedCapabilityIds = new HashSet<>();
        if (capabilityResult.getDepthById() != null) {
            for (Integer capabilityId : capabilityResult.getIds()) {
                int depth = capabilityResult.getDepthById().getOrDefault(capabilityId, Integer.MAX_VALUE);
                if (depth == 0) {
                    seedCapabilityIds.add(capabilityId);
                }
            }
        } else {
            seedCapabilityIds.addAll(capabilityResult.getIds());
        }
        
        // Get seed IDs to avoid adding them if they're already seed objects
        Set<Integer> seedGlossaryIds = getSeedObjectIds(results, "GLOSSARY");
        
        for (Integer capabilityId : seedCapabilityIds) {
            try {
                // Get the depth of this capability (should be 0 for seed, but check to be safe)
                int capabilityDepth = capabilityResult.getDepthById() != null ? 
                    capabilityResult.getDepthById().getOrDefault(capabilityId, 0) : 0;
                
                // Get impact relationships for this capability
                Map<String, Set<Integer>> impactRelations = queryCapabilityImpactRelationships(capabilityId);
                for (Map.Entry<String, Set<Integer>> impactEntry : impactRelations.entrySet()) {
                    String impactFacetId = impactEntry.getKey();
                    Set<Integer> impactIds = impactEntry.getValue();
                    if (impactIds.isEmpty()) {
                        continue;
                    }

                    int newDepth = capabilityDepth + 1;
                    if (newDepth <= 1) {
                        FacetResult existingImpactResult = results.get(impactFacetId);
                        Set<Integer> mergedImpactIds = new LinkedHashSet<>();
                        Map<Integer, Integer> mergedImpactDepth = new HashMap<>();

                        if (existingImpactResult != null && existingImpactResult.getIds() != null) {
                            mergedImpactIds.addAll(existingImpactResult.getIds());
                            if (existingImpactResult.getDepthById() != null) {
                                mergedImpactDepth.putAll(existingImpactResult.getDepthById());
                            }
                        }

                        mergedImpactIds.addAll(impactIds);
                        for (Integer impactId : impactIds) {
                            mergedImpactDepth.putIfAbsent(impactId, newDepth);
                        }

                        List<Map<String, Object>> impactRows = loadFacetRows(impactFacetId, mergedImpactIds);
                        int impactTotal = computeAccessibleTotalCount(impactFacetId,
                                impactRows != null ? impactRows.size() : 0);
                        results.put(impactFacetId,
                                new FacetResult(mergedImpactIds, true, mergedImpactDepth, impactRows, impactTotal));
                    }
                }
                
                // Glossaries linked to capability → expand datasets / attributes / systems for each glossary
                Set<Integer> glossaryIds = queryCapabilityGlossaries(capabilityId);
                for (Integer glossaryId : glossaryIds) {
                    if (seedGlossaryIds.contains(glossaryId)) {
                        continue;
                    }
                    int newDepth = capabilityDepth + 1;
                    if (newDepth <= 1) {
                        allGlossaryIds.add(glossaryId);
                        glossaryDepth.put(glossaryId, newDepth);
                        expandGlossaryLinkedArtifactsForEnrichment(glossaryId, newDepth, context,
                                allGlossaryIds, glossaryDepth, allDatasetIds, datasetDepth,
                                allAttributeIds, attributeDepth, allSystemIds, systemDepth);
                    }
                }
            } catch (Exception e) {
                System.err.println(
                        "[UnisonSearchService] Error enriching capability " + capabilityId + ": " + e.getMessage());
            }
        }

        // Add Glossary results
        if (!allGlossaryIds.isEmpty()) {
            String glossaryFacetId = "GLOSSARY";
            FacetResult existingGlossaryResult = results.get(glossaryFacetId);
            Set<Integer> mergedGlossaryIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedGlossaryDepth = new HashMap<>();

            if (existingGlossaryResult != null && existingGlossaryResult.getIds() != null) {
                mergedGlossaryIds.addAll(existingGlossaryResult.getIds());
                if (existingGlossaryResult.getDepthById() != null) {
                    mergedGlossaryDepth.putAll(existingGlossaryResult.getDepthById());
                }
            }

            mergedGlossaryIds.addAll(allGlossaryIds);
            mergedGlossaryDepth.putAll(glossaryDepth);

            List<Map<String, Object>> glossaryRows = loadGlossaryRows(mergedGlossaryIds);
            int glossaryTotal = computeAccessibleTotalCount(glossaryFacetId,
                    glossaryRows != null ? glossaryRows.size() : 0);
            results.put(glossaryFacetId,
                    new FacetResult(mergedGlossaryIds, true, mergedGlossaryDepth, glossaryRows, glossaryTotal));
        }

        // Add Dataset results
        if (!allDatasetIds.isEmpty()) {
            String datasetFacetId = "DATASET";
            FacetResult existingDatasetResult = results.get(datasetFacetId);
            Set<Integer> mergedDatasetIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDatasetDepth = new HashMap<>();

            if (existingDatasetResult != null && existingDatasetResult.getIds() != null) {
                mergedDatasetIds.addAll(existingDatasetResult.getIds());
                if (existingDatasetResult.getDepthById() != null) {
                    mergedDatasetDepth.putAll(existingDatasetResult.getDepthById());
                }
            }

            mergedDatasetIds.addAll(allDatasetIds);
            for (Map.Entry<Integer, Integer> entry : datasetDepth.entrySet()) {
                mergedDatasetDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> datasetRows = loadDatasetRows(mergedDatasetIds);
            int datasetTotal = computeAccessibleTotalCount(datasetFacetId,
                    datasetRows != null ? datasetRows.size() : 0);
            results.put(datasetFacetId,
                    new FacetResult(mergedDatasetIds, true, mergedDatasetDepth, datasetRows, datasetTotal));
        }

        // Add Attribute results
        if (!allAttributeIds.isEmpty()) {
            String attributeFacetId = "ATTRIBUTE";
            FacetResult existingAttributeResult = results.get(attributeFacetId);
            Set<Integer> mergedAttributeIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedAttributeDepth = new HashMap<>();

            if (existingAttributeResult != null && existingAttributeResult.getIds() != null) {
                mergedAttributeIds.addAll(existingAttributeResult.getIds());
                if (existingAttributeResult.getDepthById() != null) {
                    mergedAttributeDepth.putAll(existingAttributeResult.getDepthById());
                }
            }

            mergedAttributeIds.addAll(allAttributeIds);
            for (Map.Entry<Integer, Integer> entry : attributeDepth.entrySet()) {
                mergedAttributeDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> attributeRows = loadAttributeRows(mergedAttributeIds);
            int attributeTotal = computeAccessibleTotalCount(attributeFacetId,
                    attributeRows != null ? attributeRows.size() : 0);
            results.put(attributeFacetId,
                    new FacetResult(mergedAttributeIds, true, mergedAttributeDepth, attributeRows, attributeTotal));
        }

        if (!allSystemIds.isEmpty()) {
            String systemFacetId = "SYSTEM";
            FacetResult existingSystemResult = results.get(systemFacetId);
            Set<Integer> mergedSystemIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedSystemDepth = new HashMap<>();
            if (existingSystemResult != null && existingSystemResult.getIds() != null) {
                mergedSystemIds.addAll(existingSystemResult.getIds());
                if (existingSystemResult.getDepthById() != null) {
                    mergedSystemDepth.putAll(existingSystemResult.getDepthById());
                }
            }
            mergedSystemIds.addAll(allSystemIds);
            for (Map.Entry<Integer, Integer> entry : systemDepth.entrySet()) {
                mergedSystemDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }
            List<Map<String, Object>> systemRows = loadSystemRows(mergedSystemIds);
            int systemTotal = computeAccessibleTotalCount(systemFacetId,
                    systemRows != null ? systemRows.size() : 0);
            results.put(systemFacetId,
                    new FacetResult(mergedSystemIds, true, mergedSystemDepth, systemRows, systemTotal));
        }

        return results;
    }

    /**
     * Product / Business Area primary search: merge junction impact rows and expand linked glossaries
     * into datasets, attributes, and systems (same rules as capability→glossary).
     */
    private Map<String, FacetResult> enrichProductAndBusinessAreaFacet(Map<String, FacetResult> results,
            EnrichmentContext context) throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        Set<Integer> xDataset = new HashSet<>();
        Map<Integer, Integer> xDatasetDepth = new HashMap<>();
        Set<Integer> xAttr = new HashSet<>();
        Map<Integer, Integer> xAttrDepth = new HashMap<>();
        Set<Integer> xSystem = new HashSet<>();
        Map<Integer, Integer> xSystemDepth = new HashMap<>();
        Set<Integer> xGlossary = new HashSet<>();
        Map<Integer, Integer> xGlossaryDepth = new HashMap<>();

        FacetResult pr = null;
        for (Map.Entry<String, FacetResult> e : results.entrySet()) {
            if (e.getKey() != null && "PRODUCT".equals(canonicalFacetId(e.getKey()))) {
                pr = e.getValue();
                break;
            }
        }
        if (pr != null && pr.getIds() != null && !pr.getIds().isEmpty()) {
            Set<Integer> seedProductIds = new HashSet<>();
            if (pr.getDepthById() != null) {
                for (Integer id : pr.getIds()) {
                    if (pr.getDepthById().getOrDefault(id, Integer.MAX_VALUE) == 0) {
                        seedProductIds.add(id);
                    }
                }
            } else {
                seedProductIds.addAll(pr.getIds());
            }
            for (Integer productId : seedProductIds) {
                int od = pr.getDepthById() != null ? pr.getDepthById().getOrDefault(productId, 0) : 0;
                Map<String, Set<Integer>> rel = queryProductImpactRelationships(productId);
                Set<Integer> glossaries = rel.getOrDefault("GLOSSARY", Collections.emptySet());
                for (Map.Entry<String, Set<Integer>> e : rel.entrySet()) {
                    Set<Integer> ids = e.getValue();
                    if (ids == null || ids.isEmpty()) {
                        continue;
                    }
                    mergeFacetEnrichmentBatch(results, e.getKey(), ids, depthMapUniform(ids, od + 1));
                }
                for (Integer gid : glossaries) {
                    if (gid == null) {
                        continue;
                    }
                    expandGlossaryLinkedArtifactsForEnrichment(gid, od + 1, context,
                            xGlossary, xGlossaryDepth, xDataset, xDatasetDepth,
                            xAttr, xAttrDepth, xSystem, xSystemDepth);
                }
            }
        }

        FacetResult baFr = null;
        for (Map.Entry<String, FacetResult> e : results.entrySet()) {
            if (e.getKey() != null && "BUSINESS_AREA".equals(canonicalFacetId(e.getKey()))) {
                baFr = e.getValue();
                break;
            }
        }
        if (baFr != null && baFr.getIds() != null && !baFr.getIds().isEmpty()) {
            Set<Integer> seedBa = new HashSet<>();
            if (baFr.getDepthById() != null) {
                for (Integer id : baFr.getIds()) {
                    if (baFr.getDepthById().getOrDefault(id, Integer.MAX_VALUE) == 0) {
                        seedBa.add(id);
                    }
                }
            } else {
                seedBa.addAll(baFr.getIds());
            }
            for (Integer baId : seedBa) {
                int od = baFr.getDepthById() != null ? baFr.getDepthById().getOrDefault(baId, 0) : 0;
                Map<String, Set<Integer>> rel = queryBusinessAreaImpactRelationships(baId);
                Set<Integer> glossaries = rel.getOrDefault("GLOSSARY", Collections.emptySet());
                for (Map.Entry<String, Set<Integer>> e : rel.entrySet()) {
                    Set<Integer> ids = e.getValue();
                    if (ids == null || ids.isEmpty()) {
                        continue;
                    }
                    mergeFacetEnrichmentBatch(results, e.getKey(), ids, depthMapUniform(ids, od + 1));
                }
                for (Integer gid : glossaries) {
                    if (gid == null) {
                        continue;
                    }
                    expandGlossaryLinkedArtifactsForEnrichment(gid, od + 1, context,
                            xGlossary, xGlossaryDepth, xDataset, xDatasetDepth,
                            xAttr, xAttrDepth, xSystem, xSystemDepth);
                }
            }
        }

        if (!xGlossary.isEmpty()) {
            mergeFacetEnrichmentBatch(results, "GLOSSARY", xGlossary, xGlossaryDepth);
        }
        if (!xDataset.isEmpty()) {
            mergeFacetEnrichmentBatch(results, "DATASET", xDataset, xDatasetDepth);
        }
        if (!xAttr.isEmpty()) {
            mergeFacetEnrichmentBatch(results, "ATTRIBUTE", xAttr, xAttrDepth);
        }
        if (!xSystem.isEmpty()) {
            mergeFacetEnrichmentBatch(results, "SYSTEM", xSystem, xSystemDepth);
        }
        return results;
    }

    /**
     * Enrich Interface facet with source and target systems.
     */
    private Map<String, FacetResult> enrichInterfaceFacet(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        FacetResult interfaceResult = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("INTERFACE") ||
                    facetId.toUpperCase().equals("INTERFACES"))) {
                interfaceResult = entry.getValue();
                break;
            }
        }

        if (interfaceResult == null || interfaceResult.getIds() == null || interfaceResult.getIds().isEmpty()) {
            return results;
        }

        Set<Integer> allSystemIds = new HashSet<>();
        Map<Integer, Integer> systemDepth = new HashMap<>();

        // Get seed interface IDs (depth 0) - only process direct relations from seed objects
        Set<Integer> seedInterfaceIds = new HashSet<>();
        if (interfaceResult.getDepthById() != null) {
            for (Integer interfaceId : interfaceResult.getIds()) {
                int depth = interfaceResult.getDepthById().getOrDefault(interfaceId, Integer.MAX_VALUE);
                if (depth == 0) {
                    seedInterfaceIds.add(interfaceId);
                }
            }
        } else {
            seedInterfaceIds.addAll(interfaceResult.getIds());
        }
        
        // Get seed system IDs to avoid adding them if they're already seed objects
        Set<Integer> seedSystemIds = getSeedObjectIds(results, "SYSTEM");
        
        for (Integer interfaceId : seedInterfaceIds) {
            try {
                // Get the depth of this interface
                int interfaceDepth = interfaceResult.getDepthById() != null ? 
                    interfaceResult.getDepthById().getOrDefault(interfaceId, 0) : 0;
                
                // Get impact relationships for this interface
                Map<String, Set<Integer>> impactRelations = queryInterfaceImpactRelationships(interfaceId);
                for (Map.Entry<String, Set<Integer>> impactEntry : impactRelations.entrySet()) {
                    String impactFacetId = impactEntry.getKey();
                    Set<Integer> impactIds = impactEntry.getValue();
                    if (impactIds.isEmpty()) {
                        continue;
                    }

                    int newDepth = interfaceDepth + 1;
                    if (newDepth <= 1) {
                        FacetResult existingImpactResult = results.get(impactFacetId);
                        Set<Integer> mergedImpactIds = new LinkedHashSet<>();
                        Map<Integer, Integer> mergedImpactDepth = new HashMap<>();

                        if (existingImpactResult != null && existingImpactResult.getIds() != null) {
                            mergedImpactIds.addAll(existingImpactResult.getIds());
                            if (existingImpactResult.getDepthById() != null) {
                                mergedImpactDepth.putAll(existingImpactResult.getDepthById());
                            }
                        }

                        mergedImpactIds.addAll(impactIds);
                        for (Integer impactId : impactIds) {
                            mergedImpactDepth.putIfAbsent(impactId, newDepth);
                        }

                        List<Map<String, Object>> impactRows = loadFacetRows(impactFacetId, mergedImpactIds);
                        int impactTotal = computeAccessibleTotalCount(impactFacetId,
                                impactRows != null ? impactRows.size() : 0);
                        results.put(impactFacetId,
                                new FacetResult(mergedImpactIds, true, mergedImpactDepth, impactRows, impactTotal));
                    }
                }
                
                // Get source and target systems
                Map<String, Integer> systems = queryInterfaceSystems(interfaceId);
                if (systems.containsKey("SOURCE")) {
                    Integer sourceSystemId = systems.get("SOURCE");
                    if (sourceSystemId != null) {
                        // Skip if this system is already a seed object (direct relation only)
                        if (!seedSystemIds.contains(sourceSystemId)) {
                            allSystemIds.add(sourceSystemId);
                            systemDepth.put(sourceSystemId, 1);
                        }
                    }
                }
                if (systems.containsKey("TARGET")) {
                    Integer targetSystemId = systems.get("TARGET");
                    if (targetSystemId != null) {
                        // Skip if this system is already a seed object (direct relation only)
                        if (!seedSystemIds.contains(targetSystemId)) {
                            allSystemIds.add(targetSystemId);
                            systemDepth.put(targetSystemId, 1);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println(
                        "[UnisonSearchService] Error enriching interface " + interfaceId + ": " + e.getMessage());
            }
        }

        // Add System results
        if (!allSystemIds.isEmpty()) {
            String systemFacetId = "SYSTEM";
            FacetResult existingSystemResult = results.get(systemFacetId);
            Set<Integer> mergedSystemIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedSystemDepth = new HashMap<>();

            if (existingSystemResult != null && existingSystemResult.getIds() != null) {
                mergedSystemIds.addAll(existingSystemResult.getIds());
                if (existingSystemResult.getDepthById() != null) {
                    mergedSystemDepth.putAll(existingSystemResult.getDepthById());
                }
            }

            mergedSystemIds.addAll(allSystemIds);
            mergedSystemDepth.putAll(systemDepth);

            List<Map<String, Object>> systemRows = loadSystemRows(mergedSystemIds);
            int systemTotal = computeAccessibleTotalCount(systemFacetId, systemRows != null ? systemRows.size() : 0);
            results.put(systemFacetId,
                    new FacetResult(mergedSystemIds, true, mergedSystemDepth, systemRows, systemTotal));
        }

        return results;
    }

    /**
     * Enrich Org Unit facet with people and objects with stakeholders from org unit.
     * Only process seed org units (depth 0) to avoid excessive indirect relationships.
     */
    private Map<String, FacetResult> enrichOrgUnitFacet(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        FacetResult orgUnitResult = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("ORG_UNIT") ||
                    facetId.toUpperCase().equals("ORGUNIT") ||
                    facetId.toUpperCase().equals("ORG_UNITS"))) {
                orgUnitResult = entry.getValue();
                break;
            }
        }

        if (orgUnitResult == null || orgUnitResult.getIds() == null || orgUnitResult.getIds().isEmpty()) {
            return results;
        }

        // Only process seed org units (depth 0)
        Set<Integer> seedOrgUnitIds = new HashSet<>();
        if (orgUnitResult.getDepthById() != null) {
            for (Map.Entry<Integer, Integer> entry : orgUnitResult.getDepthById().entrySet()) {
                if (entry.getValue() != null && entry.getValue() == 0) {
                    seedOrgUnitIds.add(entry.getKey());
                }
            }
        }

        if (seedOrgUnitIds.isEmpty()) {
            return results;
        }

        Set<Integer> allPeopleIds = new HashSet<>();
        Map<String, Set<Integer>> stakeholderObjects = new HashMap<>();
        Map<Integer, Integer> peopleDepth = new HashMap<>();

        for (Integer orgUnitId : seedOrgUnitIds) {
            try {
                // Get people belonging to this org unit
                Set<Integer> peopleIds = queryOrgUnitPeople(orgUnitId);
                System.out.println("[UNISON-DEBUG][enrichOrgUnit] OrgUnit ID=" + orgUnitId + " -> people=" + peopleIds);
                for (Integer peopleId : peopleIds) {
                    allPeopleIds.add(peopleId);
                    peopleDepth.put(peopleId, 1);
                }

                // Get objects that have stakeholders belonging to this org unit
                Map<String, Set<Integer>> objects = queryOrgUnitStakeholderObjects(orgUnitId);
                for (Map.Entry<String, Set<Integer>> entry : objects.entrySet()) {
                    String objectFacet = entry.getKey();
                    Set<Integer> objectIds = entry.getValue();
                    stakeholderObjects.computeIfAbsent(objectFacet, k -> new HashSet<>()).addAll(objectIds);
                }
            } catch (Exception e) {
                System.err.println("[UnisonSearchService] Error enriching org unit " + orgUnitId + ": " + e.getMessage());
            }
        }

        // Add People results
        if (!allPeopleIds.isEmpty()) {
            String peopleFacetId = "PEOPLE";
            FacetResult existingPeopleResult = results.get(peopleFacetId);
            Set<Integer> mergedPeopleIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedPeopleDepth = new HashMap<>();

            if (existingPeopleResult != null && existingPeopleResult.getIds() != null) {
                mergedPeopleIds.addAll(existingPeopleResult.getIds());
                if (existingPeopleResult.getDepthById() != null) {
                    mergedPeopleDepth.putAll(existingPeopleResult.getDepthById());
                }
            }

            mergedPeopleIds.addAll(allPeopleIds);
            mergedPeopleDepth.putAll(peopleDepth);

            List<Map<String, Object>> peopleRows = loadPeopleRows(mergedPeopleIds);
            results.put(peopleFacetId,
                    facetResultFromLoadedRows(peopleFacetId, mergedPeopleDepth, peopleRows, true));
        }

        // Add stakeholder objects to results
        for (Map.Entry<String, Set<Integer>> entry : stakeholderObjects.entrySet()) {
            String facetId = entry.getKey();
            Set<Integer> objectIds = entry.getValue();
            if (objectIds.isEmpty()) {
                continue;
            }

            FacetResult existingResult = results.get(facetId);
            Set<Integer> mergedIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedDepth = new HashMap<>();

            if (existingResult != null && existingResult.getIds() != null) {
                mergedIds.addAll(existingResult.getIds());
                if (existingResult.getDepthById() != null) {
                    mergedDepth.putAll(existingResult.getDepthById());
                }
            }

            mergedIds.addAll(objectIds);
            for (Integer objectId : objectIds) {
                if (!mergedDepth.containsKey(objectId)) {
                    mergedDepth.put(objectId, 1);
                }
            }

            List<Map<String, Object>> rows = loadFacetRows(facetId, mergedIds);
            results.put(facetId, facetResultFromLoadedRows(facetId, mergedDepth, rows, true));
        }

        return results;
    }

    /**
     * Enrich Geography facet with legal entities, regulators, regulations, and regulatory themes.
     * Only process seed geographies (depth 0) to avoid excessive indirect relationships.
     */
    private Map<String, FacetResult> enrichGeographyFacet(Map<String, FacetResult> results, EnrichmentContext context)
            throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        FacetResult geographyResult = null;
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null && (facetId.toUpperCase().equals("GEOGRAPHY") ||
                    facetId.toUpperCase().equals("GEOGRAPHIES"))) {
                geographyResult = entry.getValue();
                break;
            }
        }

        if (geographyResult == null || geographyResult.getIds() == null || geographyResult.getIds().isEmpty()) {
            return results;
        }

        // Only process seed geographies (depth 0)
        Set<Integer> seedGeographyIds = new HashSet<>();
        if (geographyResult.getDepthById() != null) {
            for (Map.Entry<Integer, Integer> entry : geographyResult.getDepthById().entrySet()) {
                if (entry.getValue() != null && entry.getValue() == 0) {
                    seedGeographyIds.add(entry.getKey());
                }
            }
        }

        if (seedGeographyIds.isEmpty()) {
            return results;
        }

        Set<Integer> allLegalEntityIds = new HashSet<>();
        Set<Integer> allRegulatorIds = new HashSet<>();
        Set<Integer> allRegulationIds = new HashSet<>();
        Set<Integer> allRegulatoryThemeIds = new HashSet<>();
        Map<Integer, Integer> legalEntityDepth = new HashMap<>();
        Map<Integer, Integer> regulatorDepth = new HashMap<>();
        Map<Integer, Integer> regulationDepth = new HashMap<>();
        Map<Integer, Integer> regulatoryThemeDepth = new HashMap<>();

        for (Integer geographyId : seedGeographyIds) {
            try {
                // Get legal entities directly linked through impact
                Set<Integer> legalEntityIds = queryGeographyLegalEntities(geographyId);
                for (Integer legalEntityId : legalEntityIds) {
                    allLegalEntityIds.add(legalEntityId);
                    legalEntityDepth.put(legalEntityId, 1);
                }

                // Get regulators linked directly through regulator facet (regulator_x_geography)
                Set<Integer> regulatorIds = queryGeographyRegulators(geographyId);
                for (Integer regulatorId : regulatorIds) {
                    allRegulatorIds.add(regulatorId);
                    regulatorDepth.put(regulatorId, 1);
                }

                // Get regulations directly linked through regulation object (regulation_x_regulator_x_geography)
                Set<Integer> regulationIds = queryGeographyRegulations(geographyId);
                for (Integer regulationId : regulationIds) {
                    allRegulationIds.add(regulationId);
                    regulationDepth.put(regulationId, 1);
                }

                // Get regulatory themes linked to regulations linked to this geography
                if (!regulationIds.isEmpty()) {
                    for (Integer regulationId : regulationIds) {
                        Set<Integer> themeIds = queryRegulationRegulatoryThemes(regulationId);
                        for (Integer themeId : themeIds) {
                            allRegulatoryThemeIds.add(themeId);
                            if (!regulatoryThemeDepth.containsKey(themeId)) {
                                regulatoryThemeDepth.put(themeId, 2);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println(
                        "[UnisonSearchService] Error enriching geography " + geographyId + ": " + e.getMessage());
            }
        }

        // Add Legal Entity results
        if (!allLegalEntityIds.isEmpty()) {
            String legalEntityFacetId = "LEGAL_ENTITY";
            FacetResult existingLegalEntityResult = results.get(legalEntityFacetId);
            Set<Integer> mergedLegalEntityIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedLegalEntityDepth = new HashMap<>();

            if (existingLegalEntityResult != null && existingLegalEntityResult.getIds() != null) {
                mergedLegalEntityIds.addAll(existingLegalEntityResult.getIds());
                if (existingLegalEntityResult.getDepthById() != null) {
                    mergedLegalEntityDepth.putAll(existingLegalEntityResult.getDepthById());
                }
            }

            mergedLegalEntityIds.addAll(allLegalEntityIds);
            mergedLegalEntityDepth.putAll(legalEntityDepth);

            List<Map<String, Object>> legalEntityRows = loadLegalEntityRows(mergedLegalEntityIds);
            int legalEntityTotal = computeAccessibleTotalCount(legalEntityFacetId,
                    legalEntityRows != null ? legalEntityRows.size() : 0);
            results.put(legalEntityFacetId,
                    new FacetResult(mergedLegalEntityIds, true, mergedLegalEntityDepth, legalEntityRows,
                            legalEntityTotal));
        }

        // Add Regulator results
        if (!allRegulatorIds.isEmpty()) {
            String regulatorFacetId = "REGULATOR";
            FacetResult existingRegulatorResult = results.get(regulatorFacetId);
            Set<Integer> mergedRegulatorIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedRegulatorDepth = new HashMap<>();

            if (existingRegulatorResult != null && existingRegulatorResult.getIds() != null) {
                mergedRegulatorIds.addAll(existingRegulatorResult.getIds());
                if (existingRegulatorResult.getDepthById() != null) {
                    mergedRegulatorDepth.putAll(existingRegulatorResult.getDepthById());
                }
            }

            mergedRegulatorIds.addAll(allRegulatorIds);
            mergedRegulatorDepth.putAll(regulatorDepth);

            List<Map<String, Object>> regulatorRows = loadRegulatorRows(mergedRegulatorIds);
            int regulatorTotal = computeAccessibleTotalCount(regulatorFacetId,
                    regulatorRows != null ? regulatorRows.size() : 0);
            results.put(regulatorFacetId,
                    new FacetResult(mergedRegulatorIds, true, mergedRegulatorDepth, regulatorRows, regulatorTotal));
        }

        // Add Regulation results
        if (!allRegulationIds.isEmpty()) {
            String regulationFacetId = "REGULATION";
            FacetResult existingRegulationResult = results.get(regulationFacetId);
            Set<Integer> mergedRegulationIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedRegulationDepth = new HashMap<>();

            if (existingRegulationResult != null && existingRegulationResult.getIds() != null) {
                mergedRegulationIds.addAll(existingRegulationResult.getIds());
                if (existingRegulationResult.getDepthById() != null) {
                    mergedRegulationDepth.putAll(existingRegulationResult.getDepthById());
                }
            }

            mergedRegulationIds.addAll(allRegulationIds);
            mergedRegulationDepth.putAll(regulationDepth);

            List<Map<String, Object>> regulationRows = loadRegulationRows(mergedRegulationIds);
            int regulationTotal = computeAccessibleTotalCount(regulationFacetId,
                    regulationRows != null ? regulationRows.size() : 0);
            results.put(regulationFacetId,
                    new FacetResult(mergedRegulationIds, true, mergedRegulationDepth, regulationRows, regulationTotal));
        }

        // Add Regulatory Theme results
        if (!allRegulatoryThemeIds.isEmpty()) {
            String regulatoryThemeFacetId = "REGULATORY_THEME";
            FacetResult existingRegulatoryThemeResult = results.get(regulatoryThemeFacetId);
            Set<Integer> mergedRegulatoryThemeIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedRegulatoryThemeDepth = new HashMap<>();

            if (existingRegulatoryThemeResult != null && existingRegulatoryThemeResult.getIds() != null) {
                mergedRegulatoryThemeIds.addAll(existingRegulatoryThemeResult.getIds());
                if (existingRegulatoryThemeResult.getDepthById() != null) {
                    mergedRegulatoryThemeDepth.putAll(existingRegulatoryThemeResult.getDepthById());
                }
            }

            mergedRegulatoryThemeIds.addAll(allRegulatoryThemeIds);
            for (Map.Entry<Integer, Integer> entry : regulatoryThemeDepth.entrySet()) {
                mergedRegulatoryThemeDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> regulatoryThemeRows = loadRegulatoryThemeRows(mergedRegulatoryThemeIds);
            int regulatoryThemeTotal = computeAccessibleTotalCount(regulatoryThemeFacetId,
                    regulatoryThemeRows != null ? regulatoryThemeRows.size() : 0);
            results.put(regulatoryThemeFacetId,
                    new FacetResult(mergedRegulatoryThemeIds, true, mergedRegulatoryThemeDepth, regulatoryThemeRows,
                            regulatoryThemeTotal));
        }

        return results;
    }

    /**
     * Enrich Regulation/Regulator/Regulatory Theme facet with geographies, regulators, and impact relationships.
     */
    private Map<String, FacetResult> enrichRegulationRegulatorThemeFacet(Map<String, FacetResult> results,
            EnrichmentContext context) throws SQLException {
        if (results == null || results.isEmpty() || context == null) {
            return results;
        }

        // Check for Regulation, Regulator, or Regulatory Theme facets
        Map<String, FacetResult> facetResults = new HashMap<>();
        for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
            String facetId = entry.getKey();
            if (facetId != null) {
                String normalized = facetId.toUpperCase();
                if (normalized.equals("REGULATION") || normalized.equals("REGULATOR") ||
                        normalized.equals("REGULATORY_THEME") || normalized.equals("REGULATORYTHEME")) {
                    facetResults.put(facetId, entry.getValue());
                }
            }
        }

        if (facetResults.isEmpty()) {
            return results;
        }

        Set<Integer> allGeographyIds = new HashSet<>();
        Set<Integer> allRegulatorIds = new HashSet<>();
        Set<Integer> allRelatedRegulationIds = new HashSet<>();
        Map<Integer, Integer> geographyDepth = new HashMap<>();
        Map<Integer, Integer> regulatorDepth = new HashMap<>();
        Map<Integer, Integer> relatedRegulationDepth = new HashMap<>();

        for (Map.Entry<String, FacetResult> entry : facetResults.entrySet()) {
            String facetId = entry.getKey();
            FacetResult fr = entry.getValue();
            if (fr == null || fr.getIds() == null || fr.getIds().isEmpty()) {
                continue;
            }

            // Only process seed objects (depth 0)
            Set<Integer> seedIds = new HashSet<>();
            if (fr.getDepthById() != null) {
                for (Map.Entry<Integer, Integer> depthEntry : fr.getDepthById().entrySet()) {
                    if (depthEntry.getValue() != null && depthEntry.getValue() == 0) {
                        seedIds.add(depthEntry.getKey());
                    }
                }
            }

            if (seedIds.isEmpty()) {
                continue;
            }

            String normalized = facetId.toUpperCase();
            for (Integer objectId : seedIds) {
                try {
                    if (normalized.equals("REGULATION")) {
                        // Get geographies linked to regulation (from regulation_x_regulator_x_geography)
                        Set<Integer> geographyIds = queryRegulationGeographies(objectId);
                        for (Integer geographyId : geographyIds) {
                            allGeographyIds.add(geographyId);
                            if (!geographyDepth.containsKey(geographyId)) {
                                geographyDepth.put(geographyId, 1);
                            }
                        }

                        // Get regulators linked to regulation
                        Set<Integer> regulatorIds = queryRegulationRegulators(objectId);
                        for (Integer regulatorId : regulatorIds) {
                            allRegulatorIds.add(regulatorId);
                            if (!regulatorDepth.containsKey(regulatorId)) {
                                regulatorDepth.put(regulatorId, 1);
                            }
                        }
                        
                        // Get same-type relationships (regulation→regulation)
                        Set<Integer> relatedRegulationIds = queryRegulationRelationships(objectId);
                        for (Integer relatedId : relatedRegulationIds) {
                            allRelatedRegulationIds.add(relatedId);
                            relatedRegulationDepth.putIfAbsent(relatedId, 1);
                        }
                        
                        // Get impact relationships for regulation
                        Map<String, Set<Integer>> impactRelations = queryRegulationImpactRelationships(objectId);
                        for (Map.Entry<String, Set<Integer>> impactEntry : impactRelations.entrySet()) {
                            String impactFacetId = impactEntry.getKey();
                            Set<Integer> impactIds = impactEntry.getValue();
                            if (impactIds.isEmpty()) {
                                continue;
                            }

                            FacetResult existingImpactResult = results.get(impactFacetId);
                            Set<Integer> mergedImpactIds = new LinkedHashSet<>();
                            Map<Integer, Integer> mergedImpactDepth = new HashMap<>();

                            if (existingImpactResult != null && existingImpactResult.getIds() != null) {
                                mergedImpactIds.addAll(existingImpactResult.getIds());
                                if (existingImpactResult.getDepthById() != null) {
                                    mergedImpactDepth.putAll(existingImpactResult.getDepthById());
                                }
                            }

                            mergedImpactIds.addAll(impactIds);
                            for (Integer impactId : impactIds) {
                                mergedImpactDepth.putIfAbsent(impactId, 1);
                            }

                            List<Map<String, Object>> impactRows = loadFacetRows(impactFacetId, mergedImpactIds);
                            int impactTotal = computeAccessibleTotalCount(impactFacetId,
                                    impactRows != null ? impactRows.size() : 0);
                            results.put(impactFacetId,
                                    new FacetResult(mergedImpactIds, true, mergedImpactDepth, impactRows, impactTotal));
                        }
                    } else if (normalized.equals("REGULATOR")) {
                        // Get geographies linked to regulator (from regulator_x_geography)
                        Set<Integer> geographyIds = queryRegulatorGeographies(objectId);
                        for (Integer geographyId : geographyIds) {
                            allGeographyIds.add(geographyId);
                            if (!geographyDepth.containsKey(geographyId)) {
                                geographyDepth.put(geographyId, 1);
                            }
                        }
                    } else if (normalized.equals("REGULATORY_THEME") || normalized.equals("REGULATORYTHEME")) {
                        // Get regulations linked to regulatory theme
                        Set<Integer> regulationIds = queryRegulatoryThemeRegulations(objectId);
                        // Then get geographies and regulators from those regulations
                        for (Integer regulationId : regulationIds) {
                            Set<Integer> geographyIds = queryRegulationGeographies(regulationId);
                            for (Integer geographyId : geographyIds) {
                                allGeographyIds.add(geographyId);
                                if (!geographyDepth.containsKey(geographyId)) {
                                    geographyDepth.put(geographyId, 2);
                                }
                            }

                            Set<Integer> regulatorIds = queryRegulationRegulators(regulationId);
                            for (Integer regulatorId : regulatorIds) {
                                allRegulatorIds.add(regulatorId);
                                if (!regulatorDepth.containsKey(regulatorId)) {
                                    regulatorDepth.put(regulatorId, 2);
                                }
                            }
                        }
                    }

                    // Get impact relationships
                    Map<String, Set<Integer>> impactRelations = queryObjectImpactRelationships(facetId, objectId);
                    for (Map.Entry<String, Set<Integer>> impactEntry : impactRelations.entrySet()) {
                        String impactFacetId = impactEntry.getKey();
                        Set<Integer> impactIds = impactEntry.getValue();
                        if (impactIds.isEmpty()) {
                            continue;
                        }

                        FacetResult existingImpactResult = results.get(impactFacetId);
                        Set<Integer> mergedImpactIds = new LinkedHashSet<>();
                        Map<Integer, Integer> mergedImpactDepth = new HashMap<>();

                        if (existingImpactResult != null && existingImpactResult.getIds() != null) {
                            mergedImpactIds.addAll(existingImpactResult.getIds());
                            if (existingImpactResult.getDepthById() != null) {
                                mergedImpactDepth.putAll(existingImpactResult.getDepthById());
                            }
                        }

                        mergedImpactIds.addAll(impactIds);
                        for (Integer impactId : impactIds) {
                            if (!mergedImpactDepth.containsKey(impactId)) {
                                mergedImpactDepth.put(impactId, 1);
                            }
                        }

                        List<Map<String, Object>> impactRows = loadFacetRows(impactFacetId, mergedImpactIds);
                        int impactTotal = computeAccessibleTotalCount(impactFacetId,
                                impactRows != null ? impactRows.size() : 0);
                        results.put(impactFacetId,
                                new FacetResult(mergedImpactIds, true, mergedImpactDepth, impactRows, impactTotal));
                    }
                } catch (Exception e) {
                    System.err.println("[UnisonSearchService] Error enriching " + facetId + " " + objectId + ": "
                            + e.getMessage());
                }
            }
        }

        // Add Geography results
        if (!allGeographyIds.isEmpty()) {
            String geographyFacetId = "GEOGRAPHY";
            FacetResult existingGeographyResult = results.get(geographyFacetId);
            Set<Integer> mergedGeographyIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedGeographyDepth = new HashMap<>();

            if (existingGeographyResult != null && existingGeographyResult.getIds() != null) {
                mergedGeographyIds.addAll(existingGeographyResult.getIds());
                if (existingGeographyResult.getDepthById() != null) {
                    mergedGeographyDepth.putAll(existingGeographyResult.getDepthById());
                }
            }

            mergedGeographyIds.addAll(allGeographyIds);
            for (Map.Entry<Integer, Integer> entry : geographyDepth.entrySet()) {
                mergedGeographyDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> geographyRows = loadGeographyRows(mergedGeographyIds);
            // For Geography enrichment from Regulator search, use actual row count as total
            // (Geography objects not yet registered in segment system, and we want count relative to search context)
            int geographyTotal = geographyRows != null ? geographyRows.size() : mergedGeographyIds.size();
            results.put(geographyFacetId,
                    new FacetResult(mergedGeographyIds, true, mergedGeographyDepth, geographyRows, geographyTotal));
        }

        // Add Regulator results
        if (!allRegulatorIds.isEmpty()) {
            String regulatorFacetId = "REGULATOR";
            FacetResult existingRegulatorResult = results.get(regulatorFacetId);
            Set<Integer> mergedRegulatorIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedRegulatorDepth = new HashMap<>();

            if (existingRegulatorResult != null && existingRegulatorResult.getIds() != null) {
                mergedRegulatorIds.addAll(existingRegulatorResult.getIds());
                if (existingRegulatorResult.getDepthById() != null) {
                    mergedRegulatorDepth.putAll(existingRegulatorResult.getDepthById());
                }
            }

            mergedRegulatorIds.addAll(allRegulatorIds);
            for (Map.Entry<Integer, Integer> entry : regulatorDepth.entrySet()) {
                mergedRegulatorDepth.merge(entry.getKey(), entry.getValue(),
                        (existing, incoming) -> (existing == null || incoming == null)
                                ? (existing != null ? existing : incoming)
                                : Integer.compare(existing, incoming) <= 0 ? existing : incoming);
            }

            List<Map<String, Object>> regulatorRows = loadRegulatorRows(mergedRegulatorIds);
            int regulatorTotal = computeAccessibleTotalCount(regulatorFacetId,
                    regulatorRows != null ? regulatorRows.size() : 0);
            results.put(regulatorFacetId,
                    new FacetResult(mergedRegulatorIds, true, mergedRegulatorDepth, regulatorRows, regulatorTotal));
        }

        // Add related Regulation results (from same-type relationships)
        if (!allRelatedRegulationIds.isEmpty()) {
            String regulationFacetId = "REGULATION";
            FacetResult existingRegulationResult = results.get(regulationFacetId);
            Set<Integer> mergedRegulationIds = new LinkedHashSet<>();
            Map<Integer, Integer> mergedRegulationDepth = new HashMap<>();

            if (existingRegulationResult != null && existingRegulationResult.getIds() != null) {
                mergedRegulationIds.addAll(existingRegulationResult.getIds());
                if (existingRegulationResult.getDepthById() != null) {
                    mergedRegulationDepth.putAll(existingRegulationResult.getDepthById());
                }
            }

            mergedRegulationIds.addAll(allRelatedRegulationIds);
            mergedRegulationDepth.putAll(relatedRegulationDepth);

            List<Map<String, Object>> regulationRows = loadRegulationRows(mergedRegulationIds);
            int regulationTotal = computeAccessibleTotalCount(regulationFacetId,
                    regulationRows != null ? regulationRows.size() : 0);
            results.put(regulationFacetId,
                    new FacetResult(mergedRegulationIds, true, mergedRegulationDepth, regulationRows, regulationTotal));
        }

        return results;
    }

    // ============================================================================
    // HELPER QUERY METHODS
    // ============================================================================

    /**
     * Query interfaces where system is source or target.
     */
    private Set<Integer> queryInterfacesForSystem(Integer systemId) throws SQLException {
        Set<Integer> interfaceIds = new HashSet<>();
        if (systemId == null) {
            return interfaceIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT DISTINCT id FROM interface WHERE (Source_systemID = ? OR Target_systemID = ?) AND deleted_datetime IS NULL";
            List<Object> params = List.of(systemId, systemId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("id");
                if (id instanceof Integer) {
                    interfaceIds.add((Integer) id);
                } else if (id instanceof Number) {
                    interfaceIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying interfaces for system " + systemId + ": "
                    + e.getMessage());
        }

        return interfaceIds;
    }

    /**
     * Query system impact relationships.
     */
    @SuppressWarnings("unused")
    private Map<String, Set<Integer>> querySystemImpactRelationships(Set<Integer> systemIds) throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (systemIds == null || systemIds.isEmpty()) {
            return relationships;
        }

        // Use existing querySystemRelationships method
        return querySystemRelationships(systemIds);
    }

    /**
     * Query datasets in a system.
     */
    private Set<Integer> querySystemDatasets(Integer systemId) throws SQLException {
        Set<Integer> datasetIds = new HashSet<>();
        if (systemId == null) {
            return datasetIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT DISTINCT ID FROM dataset WHERE MasterSource = ? AND DeletedDatetime IS NULL";
            List<Object> params = List.of(systemId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("ID");
                if (id instanceof Integer) {
                    datasetIds.add((Integer) id);
                } else if (id instanceof Number) {
                    datasetIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying datasets for system " + systemId + ": "
                    + e.getMessage());
        }

        return datasetIds;
    }

    /**
     * Query interfaces where a system is source or target.
     */
    private Set<Integer> querySystemInterfaces(Integer systemId) throws SQLException {
        Set<Integer> interfaceIds = new HashSet<>();
        if (systemId == null) {
            return interfaceIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            // Query interfaces where this system is either source or target
            // Fixed: Use correct column names Source_systemID and Target_systemID (not Source_System_ID)
            String sql = "SELECT DISTINCT id FROM interface WHERE " +
                         "(Source_systemID = ? OR Target_systemID = ?) AND deleted_datetime IS NULL";
            List<Object> params = List.of(systemId, systemId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("id");
                if (id == null) {
                    id = row.get("ID");
                }
                if (id instanceof Integer) {
                    interfaceIds.add((Integer) id);
                } else if (id instanceof Number) {
                    interfaceIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying interfaces for system " + systemId + ": "
                    + e.getMessage());
        }

        return interfaceIds;
    }

    /**
     * Query datasets directly attached to a glossary.
     */
    private Set<Integer> queryGlossaryDatasets(Integer glossaryId) throws SQLException {
        Set<Integer> datasetIds = new HashSet<>();
        if (glossaryId == null) {
            return datasetIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query from dataset.glossary field
            String directSql = "SELECT DISTINCT ID FROM dataset WHERE glossary = ? AND DeletedDatetime IS NULL";
            List<Object> params = List.of(glossaryId);
            List<Map<String, Object>> directResults = dbHelper.executeQuery(directSql, params);
            for (Map<String, Object> row : directResults) {
                Object id = row.get("ID");
                if (id instanceof Integer) {
                    datasetIds.add((Integer) id);
                } else if (id instanceof Number) {
                    datasetIds.add(((Number) id).intValue());
                }
            }

            // REMOVED: glossary_x_dataset table does not exist in database
            // The relationship between glossary and dataset is handled through dataset.glossary field (direct FK)
            // try {
            //     String junctionSql = "SELECT DISTINCT Dataset_ID FROM glossary_x_dataset WHERE Glossary_ID = ?";
            //     List<Map<String, Object>> junctionResults = dbHelper.executeQuery(junctionSql, params);
            //     for (Map<String, Object> row : junctionResults) {
            //         Object id = row.get("Dataset_ID");
            //         if (id instanceof Integer) {
            //             datasetIds.add((Integer) id);
            //         } else if (id instanceof Number) {
            //             datasetIds.add(((Number) id).intValue());
            //         }
            //     }
            // } catch (SQLException e) {
            //     // Table might not exist, ignore
            // }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying datasets for glossary " + glossaryId + ": "
                    + e.getMessage());
        }

        return datasetIds;
    }

    /**
     * Query attributes directly attached to a glossary.
     */
    private Set<Integer> queryGlossaryAttributes(Integer glossaryId) throws SQLException {
        Set<Integer> attributeIds = new HashSet<>();
        if (glossaryId == null) {
            return attributeIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            // Query from attribute.Glossary_ID field
            String directSql = "SELECT DISTINCT ID FROM attribute WHERE Glossary_ID = ? AND DeletedDatetime IS NULL";
            List<Object> params = List.of(glossaryId);
            List<Map<String, Object>> directResults = dbHelper.executeQuery(directSql, params);
            for (Map<String, Object> row : directResults) {
                Object id = row.get("ID");
                if (id instanceof Integer) {
                    attributeIds.add((Integer) id);
                } else if (id instanceof Number) {
                    attributeIds.add(((Number) id).intValue());
                }
            }

            // REMOVED: glossary_x_attribute table does not exist in database
            // The relationship between glossary and attribute is handled through attribute.Glossary_ID field (direct FK)
            // try {
            //     String junctionSql = "SELECT DISTINCT AttributeID FROM glossary_x_attribute WHERE Glossary_ID = ?";
            //     List<Map<String, Object>> junctionResults = dbHelper.executeQuery(junctionSql, params);
            //     for (Map<String, Object> row : junctionResults) {
            //         Object id = row.get("AttributeID");
            //         if (id instanceof Integer) {
            //             attributeIds.add((Integer) id);
            //         } else if (id instanceof Number) {
            //             attributeIds.add(((Number) id).intValue());
            //         }
            //     }
            // } catch (SQLException e) {
            //     // Table might not exist, ignore
            // }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying attributes for glossary " + glossaryId + ": "
                    + e.getMessage());
        }

        return attributeIds;
    }

    /**
     * Query dataset of an attribute.
     */
    private Integer queryAttributeDataset(Integer attributeId) throws SQLException {
        if (attributeId == null) {
            return null;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT Dataset_ID FROM attribute WHERE ID = ? AND DeletedDatetime IS NULL";
            List<Object> params = List.of(attributeId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            if (!results.isEmpty()) {
                Object datasetId = results.get(0).get("Dataset_ID");
                if (datasetId instanceof Integer) {
                    return (Integer) datasetId;
                } else if (datasetId instanceof Number) {
                    return ((Number) datasetId).intValue();
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying dataset for attribute " + attributeId + ": "
                    + e.getMessage());
        }

        return null;
    }

    /**
     * Query datasets for multiple attributes.
     * @param attributeIds Set of attribute IDs
     * @return Map of attribute ID to dataset ID
     */
    @SuppressWarnings("unused")
    private Map<Integer, Integer> queryAttributeDatasets(Set<Integer> attributeIds) throws SQLException {
        Map<Integer, Integer> attributeToDataset = new HashMap<>();
        if (attributeIds == null || attributeIds.isEmpty()) {
            return attributeToDataset;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            
            // Build IN clause for efficient batch query
            String placeholders = String.join(",", Collections.nCopies(attributeIds.size(), "?"));
            String sql = "SELECT ID, Dataset_ID FROM attribute WHERE ID IN (" + placeholders + ") AND DeletedDatetime IS NULL";
            List<Object> params = new ArrayList<>(attributeIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object attrId = row.get("ID");
                Object datasetId = row.get("Dataset_ID");
                
                if (attrId != null && datasetId != null) {
                    Integer attrIdInt = (attrId instanceof Integer) ? (Integer) attrId : ((Number) attrId).intValue();
                    Integer datasetIdInt = (datasetId instanceof Integer) ? (Integer) datasetId : ((Number) datasetId).intValue();
                    attributeToDataset.put(attrIdInt, datasetIdInt);
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying datasets for attributes: " + e.getMessage());
        }

        return attributeToDataset;
    }

    /**
     * Query org unit of a person.
     */
    private Integer queryPeopleOrgUnit(Integer peopleId) throws SQLException {
        if (peopleId == null) {
            return null;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT Org_Unit_ID FROM people WHERE ID = ? AND Deleted_date IS NULL";
            List<Object> params = List.of(peopleId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            if (!results.isEmpty()) {
                Object orgUnitId = results.get(0).get("Org_Unit_ID");
                if (orgUnitId instanceof Integer) {
                    return (Integer) orgUnitId;
                } else if (orgUnitId instanceof Number) {
                    return ((Number) orgUnitId).intValue();
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying org unit for people " + peopleId + ": "
                    + e.getMessage());
        }

        return null;
    }

    /**
     * Query all objects where a person is a direct stakeholder.
     */
    private Map<String, Set<Integer>> queryPeopleStakeholderObjects(Integer peopleId) throws SQLException {
        Map<String, Set<Integer>> objects = new HashMap<>();
        if (peopleId == null) {
            return objects;
        }

        // Query all stakeholder tables to find objects where this person is a stakeholder
        String[] tables = { "dataset_x_objectxpeople", "system_x_objectxpeople", "glossary_x_objectxpeople",
                "interface_x_objectxpeople", "process_x_objectxpeople", "project_x_objectxpeople",
                "product_x_objectxpeople", "policy_x_objectxpeople", "attribute_x_objectxpeople",
                "businessarea_x_objectxpeople", "legal_x_objectxpeople", "client_x_objectxpeople",
                "committee_x_objectxpeople", "orgunit_x_objectxpeople",
                "regulation_x_objectxpeople",
                "capability_x_objectxpeople" };

        // Per-table join column for linking to object_x_people (varies by table schema)
        String[] joinColumns = { "Object_x_ipid", "Object_x_ipid", "Object_x_ipid",
                "Object_x_ipid", "object_x_ip", "object_x_ip",
                "object_x_ip", "Object_X_IP", "Object_x_ipid",
                "Object_x_ipid", "Object_X_ip", "Object_x_ipid",
                "Object_X_ipid", "Object_x_ipid",
                "Object_x_ipid",
                "Object_x_ipid" };

        String[] idColumns = { "Dataset_ID", "SystemID", "GlossaryID", "InterfaceID", "process_id", "project_id",
                "product_id", "Policy_ID", "AttributeID", "BusinessAreaID", "Legal_ID", "ClientID",
                "Committee_ID", "OrgUnitID",
                "RegulationID",
                "CapabilityID" };

        String[] facetIds = { "DATASET", "SYSTEM", "GLOSSARY", "INTERFACE", "PROCESS", "PROJECT", "PRODUCT", "POLICY",
                "ATTRIBUTE", "BUSINESS_AREA", "LEGAL_ENTITY", "CLIENT", "COMMITTEE", "ORG_UNIT",
                "REGULATION",
                "CAPABILITY" };

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            for (int i = 0; i < tables.length; i++) {
                try {
                    String sql = "SELECT DISTINCT " + idColumns[i] + " FROM " + tables[i]
                            + " lx JOIN object_x_people oxp ON lx." + joinColumns[i] + " = oxp.ID WHERE oxp.ipid = ?";
                    List<Object> params = List.of(peopleId);
                    List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

                    Set<Integer> objectIds = new HashSet<>();
                    for (Map<String, Object> row : results) {
                        Object id = row.get(idColumns[i]);
                        if (id instanceof Integer) {
                            objectIds.add((Integer) id);
                        } else if (id instanceof Number) {
                            objectIds.add(((Number) id).intValue());
                        }
                    }

                    if (!objectIds.isEmpty()) {
                        objects.put(facetIds[i], objectIds);
                    }
                } catch (SQLException e) {
                    // Table might not exist or have different structure, continue
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying stakeholder objects for people " + peopleId + ": "
                    + e.getMessage());
        }

        return objects;
    }

    /**
     * For each object_x_people assignment row ID, resolve the business object linked via
     * *_x_objectxpeople (Object_x_ipid / object_x_ip → assignment id). This is the correct
     * scope for a Role facet search (object_x_people IDs), unlike querying by RoleID which
     * returns every object using that role definition.
     */
    private Map<String, Set<Integer>> queryObjectsByObjectXPeopleIds(Set<Integer> objectXPeopleIds) throws SQLException {
        Map<String, Set<Integer>> objects = new HashMap<>();
        if (objectXPeopleIds == null || objectXPeopleIds.isEmpty()) {
            return objects;
        }

        String[] tables = { "dataset_x_objectxpeople", "system_x_objectxpeople", "glossary_x_objectxpeople",
                "interface_x_objectxpeople", "process_x_objectxpeople", "project_x_objectxpeople",
                "product_x_objectxpeople", "policy_x_objectxpeople", "attribute_x_objectxpeople",
                "businessarea_x_objectxpeople", "legal_x_objectxpeople", "client_x_objectxpeople",
                "committee_x_objectxpeople", "orgunit_x_objectxpeople",
                "regulation_x_objectxpeople",
                "capability_x_objectxpeople" };

        String[] joinColumns = { "Object_x_ipid", "Object_x_ipid", "Object_x_ipid",
                "Object_x_ipid", "object_x_ip", "object_x_ip",
                "object_x_ip", "Object_X_IP", "Object_x_ipid",
                "Object_x_ipid", "Object_X_ip", "Object_x_ipid",
                "Object_X_ipid", "Object_x_ipid",
                "Object_x_ipid",
                "Object_x_ipid" };

        String[] idColumns = { "Dataset_ID", "SystemID", "GlossaryID", "InterfaceID", "process_id", "project_id",
                "product_id", "Policy_ID", "AttributeID", "BusinessAreaID", "Legal_ID", "ClientID",
                "Committee_ID", "OrgUnitID",
                "RegulationID",
                "CapabilityID" };

        String[] facetIds = { "DATASET", "SYSTEM", "GLOSSARY", "INTERFACE", "PROCESS", "PROJECT", "PRODUCT", "POLICY",
                "ATTRIBUTE", "BUSINESS_AREA", "LEGAL_ENTITY", "CLIENT", "COMMITTEE", "ORG_UNIT",
                "REGULATION",
                "CAPABILITY" };

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(objectXPeopleIds.size(), "?"));
            List<Object> params = new ArrayList<>(objectXPeopleIds);

            for (int i = 0; i < tables.length; i++) {
                try {
                    String sql = "SELECT DISTINCT " + idColumns[i] + " AS _oid FROM " + tables[i]
                            + " WHERE " + joinColumns[i] + " IN (" + placeholders + ")";
                    List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

                    Set<Integer> objectIds = new HashSet<>();
                    for (Map<String, Object> row : results) {
                        Object id = row.get("_oid");
                        if (id == null) {
                            id = row.get(idColumns[i]);
                        }
                        if (id instanceof Integer) {
                            objectIds.add((Integer) id);
                        } else if (id instanceof Number) {
                            objectIds.add(((Number) id).intValue());
                        }
                    }

                    if (!objectIds.isEmpty()) {
                        objects.computeIfAbsent(facetIds[i], k -> new HashSet<>()).addAll(objectIds);
                    }
                } catch (SQLException e) {
                    // Table or column mismatch in some deployments — skip
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying objects by object_x_people IDs: " + e.getMessage());
        }

        return objects;
    }

    /**
     * People (ipid) for the given object_x_people assignment row IDs.
     */
    private Set<Integer> queryPersonIdsForObjectXPeopleIds(Set<Integer> objectXPeopleIds) throws SQLException {
        Set<Integer> peopleIds = new HashSet<>();
        if (objectXPeopleIds == null || objectXPeopleIds.isEmpty()) {
            return peopleIds;
        }
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(objectXPeopleIds.size(), "?"));
            String sql = "SELECT DISTINCT ipid FROM object_x_people WHERE ID IN (" + placeholders + ") "
                    + "AND ipid IS NOT NULL AND (Deleted_date IS NULL OR Deleted_date = '')";
            List<Object> params = new ArrayList<>(objectXPeopleIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);
            for (Map<String, Object> row : results) {
                Object id = row.get("ipid");
                if (id instanceof Integer) {
                    peopleIds.add((Integer) id);
                } else if (id instanceof Number) {
                    peopleIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying people for object_x_people IDs: " + e.getMessage());
        }
        return peopleIds;
    }

    /**
     * Query objects by role IDs across all facets.
     * Given a set of role IDs, find all objects that have those roles assigned.
     */
    private Map<String, Set<Integer>> queryRoleStakeholderObjects(Set<Integer> roleIds) throws SQLException {
        Map<String, Set<Integer>> objects = new HashMap<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return objects;
        }

        // Query all stakeholder tables to find objects linked to these roles
        String[] tables = { "dataset_x_objectxpeople", "system_x_objectxpeople", "glossary_x_objectxpeople",
                "interface_x_objectxpeople", "process_x_objectxpeople", "project_x_objectxpeople",
                "product_x_objectxpeople", "policy_x_objectxpeople", "attribute_x_objectxpeople",
                "businessarea_x_objectxpeople", "legal_x_objectxpeople", "client_x_objectxpeople",
                "committee_x_objectxpeople", "capability_x_objectxpeople", "regulation_x_objectxpeople" };

        // Per-table join column for linking to object_x_people (varies by table schema)
        String[] joinColumns = { "Object_x_ipid", "Object_x_ipid", "Object_x_ipid",
                "Object_x_ipid", "object_x_ip", "object_x_ip",
                "object_x_ip", "Object_X_IP", "Object_x_ipid",
                "Object_x_ipid", "Object_X_ip", "Object_x_ipid",
                "Object_X_ipid", "Object_x_ipid", "Object_x_ipid" };

        String[] idColumns = { "Dataset_ID", "SystemID", "GlossaryID", "InterfaceID", "process_id", "project_id",
                "product_id", "Policy_ID", "AttributeID", "BusinessAreaID", "Legal_ID", "ClientID",
                "Committee_ID", "CapabilityID", "RegulationID" };

        String[] facetIds = { "DATASET", "SYSTEM", "GLOSSARY", "INTERFACE", "PROCESS", "PROJECT", "PRODUCT", "POLICY",
                "ATTRIBUTE", "BUSINESS_AREA", "LEGAL_ENTITY", "CLIENT", "COMMITTEE", "CAPABILITY", "REGULATION" };

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
            List<Object> params = new ArrayList<>(roleIds);

            for (int i = 0; i < tables.length; i++) {
                try {
                    String sql = "SELECT DISTINCT " + idColumns[i] + " FROM " + tables[i]
                            + " lx JOIN object_x_people oxp ON lx." + joinColumns[i] + " = oxp.ID "
                            + "WHERE oxp.RoleID IN (" + placeholders + ") AND oxp.Deleted_date IS NULL";
                    List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

                    Set<Integer> objectIds = new HashSet<>();
                    for (Map<String, Object> row : results) {
                        Object id = row.get(idColumns[i]);
                        if (id instanceof Integer) {
                            objectIds.add((Integer) id);
                        } else if (id instanceof Number) {
                            objectIds.add(((Number) id).intValue());
                        }
                    }

                    if (!objectIds.isEmpty()) {
                        objects.put(facetIds[i], objectIds);
                    }
                } catch (SQLException e) {
                    // Table might not exist or have different structure, continue
                    System.err.println("[UnisonSearchService] Error querying " + tables[i] + " for roles: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying stakeholder objects for roles: " + e.getMessage());
        }

        return objects;
    }

    /**
     * object_x_people rows for one person (their role assignments only).
     * Used instead of RoleID lists: {@code loadRoleRows} filters by RoleID and would return
     * every assignment with those role types across all objects.
     */
    private Set<Integer> queryObjectXPeopleIdsForPerson(Integer peopleId) throws SQLException {
        Set<Integer> ids = new LinkedHashSet<>();
        if (peopleId == null) {
            return ids;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT ID FROM object_x_people WHERE ipid = ? AND RoleID IS NOT NULL";
            List<Object> params = List.of(peopleId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("ID");
                if (id instanceof Integer) {
                    ids.add((Integer) id);
                } else if (id instanceof Number) {
                    ids.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying object_x_people for person " + peopleId + ": "
                    + e.getMessage());
        }

        return ids;
    }

    /**
     * Query CRs raised by a person.
     */
    private Set<Integer> queryPeopleCRsRaised(Integer peopleId) throws SQLException {
        Set<Integer> crIds = new HashSet<>();
        if (peopleId == null) {
            return crIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT DISTINCT ID FROM changerequest WHERE Created_By = ?";
            List<Object> params = List.of(peopleId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("ID");
                if (id instanceof Integer) {
                    crIds.add((Integer) id);
                } else if (id instanceof Number) {
                    crIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying CRs raised by people " + peopleId + ": "
                    + e.getMessage());
        }

        return crIds;
    }

    // ============================================================
    // PPP Enrichment Helpers — added to support enrichProjectProcessPolicyFacet
    // ============================================================

    /**
     * Dataset should show all attributes (PPP→dataset, PPP→system datasets, glossary via dataset.glossary).
     */
    private void markPppDatasetFullAttributes(Set<Integer> fullDatasetIds,
            Map<Integer, Set<Integer>> partialAttrsByDataset, Integer datasetId) {
        if (datasetId == null) {
            return;
        }
        fullDatasetIds.add(datasetId);
        partialAttrsByDataset.remove(datasetId);
    }

    /**
     * Dataset reached only via glossary-on-attribute (or PPP→attribute): show only these attributes unless full.
     */
    private void markPppDatasetPartialAttribute(Set<Integer> fullDatasetIds,
            Map<Integer, Set<Integer>> partialAttrsByDataset, Integer datasetId, Integer attributeId) {
        if (datasetId == null || attributeId == null) {
            return;
        }
        if (fullDatasetIds.contains(datasetId)) {
            return;
        }
        partialAttrsByDataset.computeIfAbsent(datasetId, k -> new LinkedHashSet<>()).add(attributeId);
    }

    /**
     * Build ATTRIBUTE facet ids: full expansion for marked datasets, then partial rows where dataset is not full.
     */
    private void reconcilePppProjectProcessPolicyAttributes(Set<Integer> fullDatasetIds,
            Map<Integer, Set<Integer>> partialAttrsByDataset,
            Set<Integer> standaloneAttributeIds,
            Set<Integer> allAttributeIdsOut,
            Map<Integer, Integer> attributeDepthOut) {
        final int depth = 1;
        if (standaloneAttributeIds != null) {
            for (Integer aid : standaloneAttributeIds) {
                if (aid == null) {
                    continue;
                }
                allAttributeIdsOut.add(aid);
                attributeDepthOut.putIfAbsent(aid, depth);
            }
        }
        for (Integer dsId : fullDatasetIds) {
            for (Integer attrId : queryPPPDatasetAttributeIds(dsId)) {
                if (attrId == null) {
                    continue;
                }
                allAttributeIdsOut.add(attrId);
                attributeDepthOut.putIfAbsent(attrId, depth);
            }
        }
        for (Map.Entry<Integer, Set<Integer>> e : partialAttrsByDataset.entrySet()) {
            if (fullDatasetIds.contains(e.getKey())) {
                continue;
            }
            for (Integer attrId : e.getValue()) {
                if (attrId == null) {
                    continue;
                }
                allAttributeIdsOut.add(attrId);
                attributeDepthOut.putIfAbsent(attrId, depth);
            }
        }
    }

    /**
     * Add glossaries referenced by any attribute on a dataset (attribute.Glossary_ID).
     */
    private void addGlossaryIdsFromDatasetAttributes(Integer datasetId, Set<Integer> allGlossaryIds,
            Map<Integer, Integer> glossaryDepth) {
        addGlossaryIdsFromDatasetAttributes(datasetId, allGlossaryIds, glossaryDepth, 1);
    }

    private void addGlossaryIdsFromDatasetAttributes(Integer datasetId, Set<Integer> allGlossaryIds,
            Map<Integer, Integer> glossaryDepth, int depthForGlossary) {
        if (datasetId == null) {
            return;
        }
        for (Integer attrId : queryPPPDatasetAttributeIds(datasetId)) {
            Integer g = queryPPPAttributeGlossary(attrId);
            if (g != null) {
                allGlossaryIds.add(g);
                glossaryDepth.merge(g, depthForGlossary, (a, b) -> {
                    if (a == null) {
                        return b;
                    }
                    if (b == null) {
                        return a;
                    }
                    return Math.min(a, b);
                });
            }
        }
    }

    /**
     * From a glossary: datasets with dataset.glossary = id (all attributes + systems + term glossaries on attrs),
     * plus attributes with Glossary_ID = id (and their datasets/systems) when not already covered by field link.
     */
    private void expandGlossaryLinkedArtifactsForEnrichment(Integer glossaryId, int glossaryLinkDepth,
            EnrichmentContext context,
            Set<Integer> allGlossaryIds, Map<Integer, Integer> glossaryDepth,
            Set<Integer> allDatasetIds, Map<Integer, Integer> datasetDepth,
            Set<Integer> allAttributeIds, Map<Integer, Integer> attributeDepth,
            Set<Integer> allSystemIds, Map<Integer, Integer> systemDepth) throws SQLException {
        if (glossaryId == null) {
            return;
        }
        Set<Integer> datasetsWithFieldLink = new LinkedHashSet<>();
        for (Integer dsId : queryGlossaryLinkedDatasetsViaField(glossaryId)) {
            if (dsId == null) {
                continue;
            }
            datasetsWithFieldLink.add(dsId);
            allDatasetIds.add(dsId);
            datasetDepth.merge(dsId, glossaryLinkDepth + 1, (a, b) -> {
                if (a == null) {
                    return b;
                }
                if (b == null) {
                    return a;
                }
                return Math.min(a, b);
            });
            if (context != null) {
                context.markEnriched("DATASET", dsId, glossaryLinkDepth + 1);
            }
            for (Integer attrId : queryPPPDatasetAttributeIds(dsId)) {
                if (attrId == null) {
                    continue;
                }
                allAttributeIds.add(attrId);
                attributeDepth.merge(attrId, glossaryLinkDepth + 2, (a, b) -> {
                    if (a == null) {
                        return b;
                    }
                    if (b == null) {
                        return a;
                    }
                    return Math.min(a, b);
                });
            }
            addGlossaryIdsFromDatasetAttributes(dsId, allGlossaryIds, glossaryDepth, glossaryLinkDepth + 2);
            for (Integer sysId : queryDatasetSystems(dsId)) {
                if (sysId == null) {
                    continue;
                }
                allSystemIds.add(sysId);
                systemDepth.merge(sysId, glossaryLinkDepth + 2, (a, b) -> {
                    if (a == null) {
                        return b;
                    }
                    if (b == null) {
                        return a;
                    }
                    return Math.min(a, b);
                });
            }
        }
        for (Integer attrId : queryGlossaryLinkedAttributes(glossaryId)) {
            if (attrId == null) {
                continue;
            }
            Integer dsId = queryAttributeDataset(attrId);
            if (dsId != null && datasetsWithFieldLink.contains(dsId)) {
                continue;
            }
            allAttributeIds.add(attrId);
            attributeDepth.merge(attrId, glossaryLinkDepth + 1, (a, b) -> {
                if (a == null) {
                    return b;
                }
                if (b == null) {
                    return a;
                }
                return Math.min(a, b);
            });
            Integer ag = queryPPPAttributeGlossary(attrId);
            if (ag != null) {
                allGlossaryIds.add(ag);
                glossaryDepth.merge(ag, glossaryLinkDepth + 1, (a, b) -> {
                    if (a == null) {
                        return b;
                    }
                    if (b == null) {
                        return a;
                    }
                    return Math.min(a, b);
                });
            }
            if (dsId != null) {
                allDatasetIds.add(dsId);
                datasetDepth.merge(dsId, glossaryLinkDepth + 2, (a, b) -> {
                    if (a == null) {
                        return b;
                    }
                    if (b == null) {
                        return a;
                    }
                    return Math.min(a, b);
                });
                for (Integer sysId : queryDatasetSystems(dsId)) {
                    if (sysId == null) {
                        continue;
                    }
                    allSystemIds.add(sysId);
                    systemDepth.merge(sysId, glossaryLinkDepth + 2, (a, b) -> {
                        if (a == null) {
                            return b;
                        }
                        if (b == null) {
                            return a;
                        }
                        return Math.min(a, b);
                    });
                }
            }
        }
    }

    /**
     * Merge enrichment IDs into an existing facet result (reload rows).
     */
    private void mergeFacetEnrichmentBatch(Map<String, FacetResult> results, String facetId,
            Set<Integer> newIds, Map<Integer, Integer> depthForIds) {
        if (results == null || facetId == null || newIds == null || newIds.isEmpty()) {
            return;
        }
        FacetResult existing = results.get(facetId);
        Set<Integer> merged = new LinkedHashSet<>();
        Map<Integer, Integer> mergedDepth = new HashMap<>();
        if (existing != null && existing.getIds() != null) {
            merged.addAll(existing.getIds());
            if (existing.getDepthById() != null) {
                mergedDepth.putAll(existing.getDepthById());
            }
        }
        merged.addAll(newIds);
        if (depthForIds != null) {
            for (Map.Entry<Integer, Integer> e : depthForIds.entrySet()) {
                mergedDepth.merge(e.getKey(), e.getValue(),
                        (a, b) -> (a == null || b == null) ? (a != null ? a : b)
                                : Integer.compare(a, b) <= 0 ? a : b);
            }
        }
        List<Map<String, Object>> rows = loadFacetRows(facetId, merged);
        int tot = computeAccessibleTotalCount(facetId, rows != null ? rows.size() : 0);
        results.put(facetId, new FacetResult(merged, true, mergedDepth, rows, tot));
    }

    private Map<Integer, Integer> depthMapUniform(Set<Integer> ids, int depth) {
        Map<Integer, Integer> m = new HashMap<>();
        if (ids == null) {
            return m;
        }
        for (Integer id : ids) {
            if (id != null) {
                m.put(id, depth);
            }
        }
        return m;
    }

    /**
     * Return dataset IDs directly linked to a Policy / Project / Process object
     * via the junction tables (does NOT use graph traversal).
     */
    private Set<Integer> queryPPPLinkedDatasets(String facetId, Integer objectId) {
        Set<Integer> ids = new HashSet<>();
        if (facetId == null || objectId == null) return ids;
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbH = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = null;
            String col = null;
            String canon = canonicalFacetId(facetId);
            if ("POLICY".equals(canon)) {
                sql = "SELECT DatasetID AS id FROM policy_x_dataset WHERE PolicyID = ? AND DatasetID IS NOT NULL";
                col = "id";
            } else if ("PROJECT".equals(canon)) {
                sql = "SELECT dataset_id AS id FROM project_x_dataset WHERE projectid = ? AND dataset_id IS NOT NULL";
                col = "id";
            } else if ("PROCESS".equals(canon)) {
                sql = "SELECT datasetid AS id FROM process_x_dataset WHERE processid = ? AND datasetid IS NOT NULL";
                col = "id";
            }
            if (sql != null) {
                for (Map<String, Object> row : dbH.executeQuery(sql, List.of(objectId))) {
                    Object v = row.get(col);
                    if (v instanceof Integer) ids.add((Integer) v);
                    else if (v instanceof Number) ids.add(((Number) v).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] queryPPPLinkedDatasets error: " + e.getMessage());
        }
        return ids;
    }

    /**
     * Return attribute IDs directly linked to a Policy / Project / Process object.
     */
    private Set<Integer> queryPPPLinkedAttributes(String facetId, Integer objectId) {
        Set<Integer> ids = new HashSet<>();
        if (facetId == null || objectId == null) return ids;
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbH = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = null;
            String col = null;
            String canon = canonicalFacetId(facetId);
            if ("POLICY".equals(canon)) {
                sql = "SELECT attributeid AS id FROM policy_x_attribute WHERE policyid = ? AND attributeid IS NOT NULL";
                col = "id";
            } else if ("PROJECT".equals(canon)) {
                sql = "SELECT attribute_id AS id FROM project_x_attribute WHERE projectid = ? AND attribute_id IS NOT NULL";
                col = "id";
            } else if ("PROCESS".equals(canon)) {
                sql = "SELECT attributeid AS id FROM process_x_attribute WHERE processid = ? AND attributeid IS NOT NULL";
                col = "id";
            }
            if (sql != null) {
                for (Map<String, Object> row : dbH.executeQuery(sql, List.of(objectId))) {
                    Object v = row.get(col);
                    if (v instanceof Integer) ids.add((Integer) v);
                    else if (v instanceof Number) ids.add(((Number) v).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] queryPPPLinkedAttributes error: " + e.getMessage());
        }
        return ids;
    }

    /**
     * Return system IDs directly linked to a Policy / Project / Process object.
     */
    private Set<Integer> queryPPPLinkedSystems(String facetId, Integer objectId) {
        Set<Integer> ids = new HashSet<>();
        if (facetId == null || objectId == null) return ids;
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbH = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = null;
            String col = null;
            String canon = canonicalFacetId(facetId);
            if ("POLICY".equals(canon)) {
                sql = "SELECT System_ID AS id FROM policy_x_system WHERE Policy_ID = ? AND System_ID IS NOT NULL";
                col = "id";
            } else if ("PROJECT".equals(canon)) {
                sql = "SELECT systemid AS id FROM project_x_system WHERE projectid = ? AND systemid IS NOT NULL";
                col = "id";
            } else if ("PROCESS".equals(canon)) {
                sql = "SELECT system_id AS id FROM process_x_system WHERE process_id = ? AND system_id IS NOT NULL";
                col = "id";
            }
            if (sql != null) {
                for (Map<String, Object> row : dbH.executeQuery(sql, List.of(objectId))) {
                    Object v = row.get(col);
                    if (v instanceof Integer) ids.add((Integer) v);
                    else if (v instanceof Number) ids.add(((Number) v).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] queryPPPLinkedSystems error: " + e.getMessage());
        }
        return ids;
    }

    /**
     * Return dataset IDs whose dataset.glossary field = glossaryId.
     */
    private Set<Integer> queryGlossaryLinkedDatasetsViaField(Integer glossaryId) {
        Set<Integer> ids = new HashSet<>();
        if (glossaryId == null) return ids;
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbH = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT ID FROM dataset WHERE glossary = ? AND DeletedDatetime IS NULL";
            for (Map<String, Object> row : dbH.executeQuery(sql, List.of(glossaryId))) {
                Object v = row.get("ID");
                if (v instanceof Integer) ids.add((Integer) v);
                else if (v instanceof Number) ids.add(((Number) v).intValue());
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] queryGlossaryLinkedDatasetsViaField error: " + e.getMessage());
        }
        return ids;
    }

    /**
     * Return attribute IDs whose attribute.Glossary_ID = glossaryId.
     */
    private Set<Integer> queryGlossaryLinkedAttributes(Integer glossaryId) {
        Set<Integer> ids = new HashSet<>();
        if (glossaryId == null) return ids;
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbH = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT ID FROM attribute WHERE Glossary_ID = ? AND DeletedDatetime IS NULL";
            for (Map<String, Object> row : dbH.executeQuery(sql, List.of(glossaryId))) {
                Object v = row.get("ID");
                if (v instanceof Integer) ids.add((Integer) v);
                else if (v instanceof Number) ids.add(((Number) v).intValue());
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] queryGlossaryLinkedAttributes error: " + e.getMessage());
        }
        return ids;
    }

    /**
     * Return attribute IDs belonging to a dataset (attribute.Dataset_ID = datasetId).
     */
    private Set<Integer> queryPPPDatasetAttributeIds(Integer datasetId) {
        Set<Integer> ids = new HashSet<>();
        if (datasetId == null) return ids;
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbH = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT ID FROM attribute WHERE Dataset_ID = ? AND DeletedDatetime IS NULL";
            for (Map<String, Object> row : dbH.executeQuery(sql, List.of(datasetId))) {
                Object v = row.get("ID");
                if (v instanceof Integer) ids.add((Integer) v);
                else if (v instanceof Number) ids.add(((Number) v).intValue());
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] queryPPPDatasetAttributeIds error: " + e.getMessage());
        }
        return ids;
    }

    /**
     * Return the glossary ID of an attribute (attribute.Glossary_ID).
     */
    private Integer queryPPPAttributeGlossary(Integer attributeId) {
        if (attributeId == null) return null;
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbH = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT Glossary_ID FROM attribute WHERE ID = ? AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = dbH.executeQuery(sql, List.of(attributeId));
            if (rows.isEmpty()) return null;
            Object v = rows.get(0).get("Glossary_ID");
            if (v instanceof Integer) return (Integer) v;
            if (v instanceof Number) return ((Number) v).intValue();
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] queryPPPAttributeGlossary error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Query glossaries linked to an object.
     */
    private Set<Integer> queryObjectGlossaries(String facetId, Integer objectId) throws SQLException {
        Set<Integer> glossaryIds = new HashSet<>();
        if (facetId == null || objectId == null) {
            return glossaryIds;
        }

        String canon = canonicalFacetId(facetId);
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            if ("PROJECT".equals(canon)) {
                // Query glossary_x_project (filter deleted)
                String sql = "SELECT DISTINCT gxp.Glossary_ID FROM glossary_x_project gxp " +
                        "JOIN glossary g ON gxp.Glossary_ID = g.ID " +
                        "WHERE gxp.Project_ID = ? AND g.Deleted_datetime IS NULL";
                List<Object> params = List.of(objectId);
                List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);
                for (Map<String, Object> row : results) {
                    Object id = row.get("Glossary_ID");
                    if (id instanceof Integer) {
                        glossaryIds.add((Integer) id);
                    } else if (id instanceof Number) {
                        glossaryIds.add(((Number) id).intValue());
                    }
                }
            } else if ("PROCESS".equals(canon)) {
                // Query glossary_x_process (filter deleted)
                String sql = "SELECT DISTINCT gxp.Glossary_ID FROM glossary_x_process gxp " +
                        "JOIN glossary g ON gxp.Glossary_ID = g.ID " +
                        "WHERE gxp.Process_ID = ? AND g.Deleted_datetime IS NULL";
                List<Object> params = List.of(objectId);
                List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);
                for (Map<String, Object> row : results) {
                    Object id = row.get("Glossary_ID");
                    if (id instanceof Integer) {
                        glossaryIds.add((Integer) id);
                    } else if (id instanceof Number) {
                        glossaryIds.add(((Number) id).intValue());
                    }
                }
            } else if ("POLICY".equals(canon)) {
                // Query policy_x_glossary (filter deleted)
                String sql = "SELECT DISTINCT pxg.GlossaryID AS Glossary_ID FROM policy_x_glossary pxg " +
                        "JOIN glossary g ON pxg.GlossaryID = g.ID " +
                        "WHERE pxg.PolicyID = ? AND g.Deleted_datetime IS NULL";
                List<Object> params = List.of(objectId);
                List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);
                for (Map<String, Object> row : results) {
                    Object id = row.get("Glossary_ID");
                    if (id instanceof Integer) {
                        glossaryIds.add((Integer) id);
                    } else if (id instanceof Number) {
                        glossaryIds.add(((Number) id).intValue());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying glossaries for " + facetId + " " + objectId + ": "
                    + e.getMessage());
        }

        return glossaryIds;
    }

    /**
     * Query impact relationships for an object.
     */
    private Map<String, Set<Integer>> queryObjectImpactRelationships(String facetId, Integer objectId)
            throws SQLException {
        Map<String, Set<Integer>> relationships = new HashMap<>();
        if (facetId == null || objectId == null) {
            return relationships;
        }

        // Route to facet-specific impact relationship query methods
        String normalized = facetId.toUpperCase();
        switch (normalized) {
            case "DATASET", "DATA_SETS", "DATASETS":
                return queryDatasetDirectRelationships(objectId);
            case "SYSTEM", "SYSTEMS":
                return querySystemImpactRelationships(objectId);
            case "POLICY", "POLICIES":
                return queryPolicyImpactRelationships(objectId);
            case "PROCESS", "PROCESSES":
                return queryProcessImpactRelationships(objectId);
            case "PROJECT", "PROJECTS":
                return queryProjectImpactRelationships(objectId);
            case "GLOSSARY", "GLOSSARIES":
                return queryGlossaryImpactRelationships(objectId);
            case "PRODUCT", "PRODUCTS":
                return queryProductImpactRelationships(objectId);
            case "CAPABILITY", "CAPABILITIES":
                return queryCapabilityImpactRelationships(objectId);
            case "BUSINESS_AREA", "BUSINESSAREA":
                return queryBusinessAreaImpactRelationships(objectId);
            case "INTERFACE", "INTERFACES":
                return queryInterfaceImpactRelationships(objectId);
            case "REGULATION", "REGULATIONS":
                return queryRegulationImpactRelationships(objectId);
            case "LEGAL_ENTITY", "LEGALENTITY":
                return queryLegalEntityImpactRelationships(objectId);
            default:
                return relationships;
        }
    }

    /**
     * Helper method to get dataset count for logging.
     */
    @SuppressWarnings("unused")
    private String getDatasetCount(Map<String, FacetResult> results) {
        if (results == null) return "null results";
        FacetResult datasetResult = results.get("DATASET");
        if (datasetResult == null || datasetResult.getIds() == null) {
            return "0 datasets";
        }
        int count = datasetResult.getIds().size();
        if (datasetResult.getDepthById() != null) {
            Map<Integer, Long> depthDist = new HashMap<>();
            for (Integer id : datasetResult.getIds()) {
                int depth = datasetResult.getDepthById().getOrDefault(id, -1);
                depthDist.put(depth, depthDist.getOrDefault(depth, 0L) + 1);
            }
            return count + " datasets (depth dist: " + depthDist + ")";
        }
        return count + " datasets";
    }
    
    /**
     * Query the object a CR was raised upon by parsing the Reference field.
     */
    private Map<String, Integer> queryCRRaisedUponObject(Integer crId) throws SQLException {
        Map<String, Integer> result = new HashMap<>();
        if (crId == null) {
            return result;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT Reference FROM changerequest WHERE ID = ?";
            List<Object> params = List.of(crId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            if (!results.isEmpty()) {
                Object referenceObj = results.get(0).get("Reference");
                if (referenceObj != null && referenceObj instanceof String) {
                    String reference = (String) referenceObj;
                    // Parse reference string like "Dataset 123" or "System 456"
                    String[] parts = reference.trim().split("\\s+");
                    if (parts.length >= 2) {
                        try {
                            String objectType = parts[0];
                            Integer objectId = Integer.parseInt(parts[1]);

                            // Map object type to facet ID
                            String facetId = mapObjectTypeToFacetId(objectType);
                            if (facetId != null) {
                                result.put(facetId, objectId);
                            }
                        } catch (NumberFormatException e) {
                            // Invalid reference format
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying CR raised upon object for CR " + crId + ": "
                    + e.getMessage());
        }

        return result;
    }

    /**
     * Map object type string to facet ID.
     */
    private String mapObjectTypeToFacetId(String objectType) {
        if (objectType == null) {
            return null;
        }

        String normalized = objectType.toUpperCase();
        return switch (normalized) {
            case "DATASET", "DATASETS", "DATA", "DATA SET" -> "DATASET";
            case "SYSTEM", "SYSTEMS" -> "SYSTEM";
            case "GLOSSARY", "GLOSSARIES" -> "GLOSSARY";
            case "PROCESS", "PROCESSES" -> "PROCESS";
            case "PROJECT", "PROJECTS" -> "PROJECT";
            case "PRODUCT", "PRODUCTS" -> "PRODUCT";
            case "POLICY", "POLICIES" -> "POLICY";
            case "ATTRIBUTE", "ATTRIBUTES" -> "ATTRIBUTE";
            case "INTERFACE", "INTERFACES" -> "INTERFACE";
            case "PEOPLE", "PERSON" -> "PEOPLE";
            case "ROLE", "ROLES" -> "ROLE";
            case "ORG", "ORGUNIT", "ORG_UNIT", "ORG UNIT" -> "ORG_UNIT";
            case "GEOGRAPHY", "GEOGRAPHIES" -> "GEOGRAPHY";
            case "REGULATION", "REGULATIONS" -> "REGULATION";
            case "REGULATOR", "REGULATORS" -> "REGULATOR";
            case "REGULATORY", "REGULATORYTHEME", "REGULATORY_THEME" -> "REGULATORY_THEME";
            case "CAPABILITY", "CAPABILITIES" -> "CAPABILITY";
            case "BUSINESS", "BUSINESSAREA", "BUSINESS_AREA" -> "BUSINESS_AREA";
            case "LEGAL", "LEGALENTITY", "LEGAL_ENTITY" -> "LEGAL_ENTITY";
            case "CLIENT", "CLIENTS" -> "CLIENT";
            case "COMMITTEE", "COMMITTEES" -> "COMMITTEE";
            default -> null;
        };
    }

    /**
     * Query active tasks by CR ID.
     */
    private Set<Integer> queryActiveTasksByCRId(Integer crId) throws SQLException {
        Set<Integer> taskIds = new HashSet<>();
        if (crId == null) {
            return taskIds;
        }
        
        // Only return tasks for the current user
        if (currentUserId == null || currentUserId <= 0) {
            return taskIds;
        }

        try {
            // Use WorkflowTaskDAO to get user-specific tasks, then filter by CR ID
            com.example.budg_v2.dao.WorkflowTaskDAO taskDAO = new com.example.budg_v2.dao.WorkflowTaskDAO();
            List<Map<String, Object>> userTasks = taskDAO.findActiveTasksForUser(currentUserId);
            
            // Filter to only tasks for the specified CR
            for (Map<String, Object> task : userTasks) {
                Object changeRequestIdObj = task.get("changeRequestId");
                if (changeRequestIdObj != null) {
                    int changeRequestId;
                    if (changeRequestIdObj instanceof Integer) {
                        changeRequestId = (Integer) changeRequestIdObj;
                    } else if (changeRequestIdObj instanceof Number) {
                        changeRequestId = ((Number) changeRequestIdObj).intValue();
                    } else {
                        continue;
                    }
                    
                    if (changeRequestId == crId) {
                        Object taskIdObj = task.get("taskId");
                        if (taskIdObj instanceof Integer) {
                            taskIds.add((Integer) taskIdObj);
                        } else if (taskIdObj instanceof Number) {
                            taskIds.add(((Number) taskIdObj).intValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying active tasks for CR " + crId + ": "
                    + e.getMessage());
        }

        return taskIds;
    }

    /**
     * Query glossaries linked to a capability.
     */
    private Set<Integer> queryCapabilityGlossaries(Integer capabilityId) throws SQLException {
        Set<Integer> glossaryIds = new HashSet<>();
        if (capabilityId == null) {
            return glossaryIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            // Filter deleted glossaries
            String sql = "SELECT DISTINCT cxg.Glossary_ID FROM capability_x_glossary cxg " +
                    "JOIN glossary g ON cxg.Glossary_ID = g.ID " +
                    "WHERE cxg.Capability_ID = ? AND g.Deleted_datetime IS NULL";
            List<Object> params = List.of(capabilityId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("Glossary_ID");
                if (id instanceof Integer) {
                    glossaryIds.add((Integer) id);
                } else if (id instanceof Number) {
                    glossaryIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying glossaries for capability " + capabilityId + ": "
                    + e.getMessage());
        }

        return glossaryIds;
    }

    /**
     * Query source and target systems of an interface.
     */
    private Map<String, Integer> queryInterfaceSystems(Integer interfaceId) throws SQLException {
        Map<String, Integer> systems = new HashMap<>();
        if (interfaceId == null) {
            return systems;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT Source_systemID, Target_systemID FROM interface WHERE id = ? AND deleted_datetime IS NULL";
            List<Object> params = List.of(interfaceId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            if (!results.isEmpty()) {
                Map<String, Object> row = results.get(0);
                Object sourceId = row.get("Source_systemID");
                Object targetId = row.get("Target_systemID");

                if (sourceId instanceof Integer) {
                    systems.put("SOURCE", (Integer) sourceId);
                } else if (sourceId instanceof Number) {
                    systems.put("SOURCE", ((Number) sourceId).intValue());
                }

                if (targetId instanceof Integer) {
                    systems.put("TARGET", (Integer) targetId);
                } else if (targetId instanceof Number) {
                    systems.put("TARGET", ((Number) targetId).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying systems for interface " + interfaceId + ": "
                    + e.getMessage());
        }

        return systems;
    }

    /**
     * Query people belonging to an org unit.
     */
    private Set<Integer> queryOrgUnitPeople(Integer orgUnitId) throws SQLException {
        Set<Integer> peopleIds = new HashSet<>();
        if (orgUnitId == null) {
            return peopleIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String sql = "SELECT DISTINCT ID FROM people WHERE Org_Unit_ID = ? AND Deleted_date IS NULL";
            List<Object> params = List.of(orgUnitId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("ID");
                if (id instanceof Integer) {
                    peopleIds.add((Integer) id);
                } else if (id instanceof Number) {
                    peopleIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying people for org unit " + orgUnitId + ": "
                    + e.getMessage());
        }

        return peopleIds;
    }

    /**
     * Query objects that have stakeholders belonging to an org unit.
     */
    private Map<String, Set<Integer>> queryOrgUnitStakeholderObjects(Integer orgUnitId) throws SQLException {
        Map<String, Set<Integer>> objects = new HashMap<>();
        if (orgUnitId == null) {
            return objects;
        }

        // Query all stakeholder tables to find objects with stakeholders from this org unit
        String[] tables = { "dataset_x_objectxpeople", "system_x_objectxpeople", "glossary_x_objectxpeople",
                "interface_x_objectxpeople", "process_x_objectxpeople", "project_x_objectxpeople",
                "product_x_objectxpeople", "policy_x_objectxpeople", "attribute_x_objectxpeople",
                "businessarea_x_objectxpeople", "legal_x_objectxpeople", "client_x_objectxpeople",
                "committee_x_objectxpeople", "orgunit_x_objectxpeople",
                "regulation_x_objectxpeople",
                "capability_x_objectxpeople" };

        // Per-table join column for linking to object_x_people (varies by table schema)
        String[] joinColumns = { "Object_x_ipid", "Object_x_ipid", "Object_x_ipid",
                "Object_x_ipid", "object_x_ip", "object_x_ip",
                "object_x_ip", "Object_X_IP", "Object_x_ipid",
                "Object_x_ipid", "Object_X_ip", "Object_x_ipid",
                "Object_X_ipid", "Object_x_ipid",
                "Object_x_ipid",
                "Object_x_ipid" };

        String[] idColumns = { "Dataset_ID", "SystemID", "GlossaryID", "InterfaceID", "process_id", "project_id",
                "product_id", "Policy_ID", "AttributeID", "BusinessAreaID", "Legal_ID", "ClientID",
                "Committee_ID", "OrgUnitID",
                "RegulationID",
                "CapabilityID" };

        String[] facetIds = { "DATASET", "SYSTEM", "GLOSSARY", "INTERFACE", "PROCESS", "PROJECT", "PRODUCT", "POLICY",
                "ATTRIBUTE", "BUSINESS_AREA", "LEGAL_ENTITY", "CLIENT", "COMMITTEE", "ORG_UNIT",
                "REGULATION",
                "CAPABILITY" };

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();

            for (int i = 0; i < tables.length; i++) {
                try {
                    String sql = "SELECT DISTINCT lx." + idColumns[i] + " FROM " + tables[i]
                            + " lx JOIN object_x_people oxp ON lx." + joinColumns[i] + " = oxp.ID JOIN people p ON oxp.ipid = p.ID WHERE p.Org_Unit_ID = ? AND p.Deleted_date IS NULL";
                    List<Object> params = List.of(orgUnitId);
                    List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

                    Set<Integer> objectIds = new HashSet<>();
                    for (Map<String, Object> row : results) {
                        Object id = row.get(idColumns[i]);
                        if (id instanceof Integer) {
                            objectIds.add((Integer) id);
                        } else if (id instanceof Number) {
                            objectIds.add(((Number) id).intValue());
                        }
                    }

                    if (!objectIds.isEmpty()) {
                        objects.put(facetIds[i], objectIds);
                    }
                } catch (SQLException e) {
                    // Table might not exist or have different structure, continue
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying stakeholder objects for org unit " + orgUnitId
                    + ": " + e.getMessage());
        }

        return objects;
    }

    /**
     * Query legal entities linked to geography through impact.
     */
    private Set<Integer> queryGeographyLegalEntities(Integer geographyId) throws SQLException {
        Set<Integer> legalEntityIds = new HashSet<>();
        if (geographyId == null) {
            return legalEntityIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            // Query legal_x_geography (filter deleted)
            String sql = "SELECT DISTINCT lexg.Legal_ID FROM legal_x_geography lexg " +
                    "JOIN legal l ON lexg.Legal_ID = l.ID " +
                    "WHERE lexg.Geography_ID = ? AND (l.DeleteDatetime IS NULL OR l.DeleteDatetime = '')";
            List<Object> params = List.of(geographyId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("Legal_ID");
                if (id instanceof Integer) {
                    legalEntityIds.add((Integer) id);
                } else if (id instanceof Number) {
                    legalEntityIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            // Table might not exist, ignore
        }

        return legalEntityIds;
    }

    /**
     * Query regulators linked to geography.
     */
    private Set<Integer> queryGeographyRegulators(Integer geographyId) throws SQLException {
        Set<Integer> regulatorIds = new HashSet<>();
        if (geographyId == null) {
            return regulatorIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            // Filter deleted regulators
            String sql = "SELECT DISTINCT rxg.Regulator_ID FROM regulator_x_geography rxg " +
                    "JOIN regulator reg ON rxg.Regulator_ID = reg.ID " +
                    "WHERE rxg.Geography_ID = ? AND reg.DeletedDatetime IS NULL";
            List<Object> params = List.of(geographyId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("Regulator_ID");
                if (id instanceof Integer) {
                    regulatorIds.add((Integer) id);
                } else if (id instanceof Number) {
                    regulatorIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying regulators for geography " + geographyId + ": "
                    + e.getMessage());
        }

        return regulatorIds;
    }

    /**
     * Query regulations linked to geography.
     */
    private Set<Integer> queryGeographyRegulations(Integer geographyId) throws SQLException {
        Set<Integer> regulationIds = new HashSet<>();
        if (geographyId == null) {
            return regulationIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            // Filter deleted regulations
            String sql = "SELECT DISTINCT rxr.RegulationID FROM regulation_x_regulator_x_geography rxrxg "
                    + "JOIN regulation_x_regulator rxr ON rxrxg.Regulation_X_Regulator_ID = rxr.ID "
                    + "JOIN regulator_x_geography rxg ON rxrxg.Regulator_X_Geography_ID = rxg.ID "
                    + "JOIN regulation r ON rxr.RegulationID = r.ID "
                    + "WHERE rxg.Geography_ID = ? AND r.DeletedDatetime IS NULL";
            List<Object> params = List.of(geographyId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("RegulationID");
                if (id instanceof Integer) {
                    regulationIds.add((Integer) id);
                } else if (id instanceof Number) {
                    regulationIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying regulations for geography " + geographyId + ": "
                    + e.getMessage());
        }

        return regulationIds;
    }

    /**
     * Query regulatory themes linked to a regulation.
     */
    private Set<Integer> queryRegulationRegulatoryThemes(Integer regulationId) throws SQLException {
        Set<Integer> themeIds = new HashSet<>();
        if (regulationId == null) {
            return themeIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            // Filter deleted regulatory themes
            String sql = "SELECT DISTINCT rxrt.RegulatoryTheme_ID FROM regulation_x_regulatorytheme rxrt " +
                    "JOIN regulatorytheme rt ON rxrt.RegulatoryTheme_ID = rt.ID " +
                    "WHERE rxrt.Regulation_ID = ? AND rt.DeletedDatetime IS NULL";
            List<Object> params = List.of(regulationId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("RegulatoryTheme_ID");
                if (id instanceof Integer) {
                    themeIds.add((Integer) id);
                } else if (id instanceof Number) {
                    themeIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying regulatory themes for regulation " + regulationId
                    + ": " + e.getMessage());
        }

        return themeIds;
    }

    /**
     * Query geographies linked to a regulation.
     */
    private Set<Integer> queryRegulationGeographies(Integer regulationId) throws SQLException {
        Set<Integer> geographyIds = new HashSet<>();
        if (regulationId == null) {
            return geographyIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            // Primary path: explicit regulation-regulator-geography relation mapping.
            String sql = "SELECT DISTINCT rxg.Geography_ID FROM regulation_x_regulator_x_geography rxrxg "
                    + "JOIN regulation_x_regulator rxr ON rxrxg.Regulation_X_Regulator_ID = rxr.ID "
                    + "JOIN regulator_x_geography rxg ON rxrxg.Regulator_X_Geography_ID = rxg.ID "
                    + "JOIN geography g ON rxg.Geography_ID = g.ID "
                    + "WHERE rxr.RegulationID = ? AND g.DeletedDatetime IS NULL";
            List<Object> params = List.of(regulationId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("Geography_ID");
                if (id instanceof Integer) {
                    geographyIds.add((Integer) id);
                } else if (id instanceof Number) {
                    geographyIds.add(((Number) id).intValue());
                }
            }

            // Fallback path: if bridge rows are missing, derive geographies from
            // regulation->regulator and regulator->geography relations.
            String fallbackSql = "SELECT DISTINCT rxg.Geography_ID FROM regulation_x_regulator rxr "
                    + "JOIN regulator_x_geography rxg ON rxr.RegulatorID = rxg.Regulator_ID "
                    + "JOIN geography g ON rxg.Geography_ID = g.ID "
                    + "WHERE rxr.RegulationID = ? AND g.DeletedDatetime IS NULL";
            List<Map<String, Object>> fallbackResults = dbHelper.executeQuery(fallbackSql, params);
            for (Map<String, Object> row : fallbackResults) {
                Object id = row.get("Geography_ID");
                if (id instanceof Integer) {
                    geographyIds.add((Integer) id);
                } else if (id instanceof Number) {
                    geographyIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying geographies for regulation " + regulationId
                    + ": " + e.getMessage());
        }

        return geographyIds;
    }

    /**
     * Query regulators linked to a regulation.
     */
    private Set<Integer> queryRegulationRegulators(Integer regulationId) throws SQLException {
        Set<Integer> regulatorIds = new HashSet<>();
        if (regulationId == null) {
            return regulatorIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            // Filter deleted regulators
            String sql = "SELECT DISTINCT rxr.RegulatorID FROM regulation_x_regulator rxr " +
                    "JOIN regulator reg ON rxr.RegulatorID = reg.ID " +
                    "WHERE rxr.RegulationID = ? AND reg.DeletedDatetime IS NULL";
            List<Object> params = List.of(regulationId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("RegulatorID");
                if (id instanceof Integer) {
                    regulatorIds.add((Integer) id);
                } else if (id instanceof Number) {
                    regulatorIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying regulators for regulation " + regulationId + ": "
                    + e.getMessage());
        }

        return regulatorIds;
    }

    /**
     * Query geographies linked to a regulator.
     */
    private Set<Integer> queryRegulatorGeographies(Integer regulatorId) throws SQLException {
        Set<Integer> geographyIds = new HashSet<>();
        if (regulatorId == null) {
            return geographyIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            // Filter deleted geographies
            String sql = "SELECT DISTINCT rxg.Geography_ID FROM regulator_x_geography rxg " +
                    "JOIN geography g ON rxg.Geography_ID = g.ID " +
                    "WHERE rxg.Regulator_ID = ? AND g.DeletedDatetime IS NULL";
            List<Object> params = List.of(regulatorId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("Geography_ID");
                if (id instanceof Integer) {
                    geographyIds.add((Integer) id);
                } else if (id instanceof Number) {
                    geographyIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying geographies for regulator " + regulatorId + ": "
                    + e.getMessage());
        }

        return geographyIds;
    }

    /**
     * Query regulations linked to a regulatory theme.
     */
    private Set<Integer> queryRegulatoryThemeRegulations(Integer regulatoryThemeId) throws SQLException {
        Set<Integer> regulationIds = new HashSet<>();
        if (regulatoryThemeId == null) {
            return regulationIds;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            // Filter deleted regulations
            String sql = "SELECT DISTINCT rxrt.Regulation_ID FROM regulation_x_regulatorytheme rxrt " +
                    "JOIN regulation r ON rxrt.Regulation_ID = r.ID " +
                    "WHERE rxrt.RegulatoryTheme_ID = ? AND r.DeletedDatetime IS NULL";
            List<Object> params = List.of(regulatoryThemeId);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object id = row.get("Regulation_ID");
                if (id instanceof Integer) {
                    regulationIds.add((Integer) id);
                } else if (id instanceof Number) {
                    regulationIds.add(((Number) id).intValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error querying regulations for regulatory theme "
                    + regulatoryThemeId + ": " + e.getMessage());
        }

        return regulationIds;
    }

    // ============================================================================
    // HELPER LOAD METHODS
    // ============================================================================

    /**
     * Load System rows for given system IDs.
     */
    private List<Map<String, Object>> loadSystemRows(Set<Integer> systemIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (systemIds == null || systemIds.isEmpty()) {
            return rows;
        }

        try {
            // Get active nobject_id values to exclude (temporary cloned rows from active CRs)
            Set<Integer> excludedIds = getActiveNObjectIdsForFacet("system");

            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(systemIds.size(), "?"));
            // Fixed: ShortName column doesn't exist in schema - use Name and Long_Name instead
            String sql = "SELECT id, Name, Long_Name, Description, AssetID AS Ref_Number, parent_id AS Parent_ID, Deleted_datetime AS DeletedDatetime "
                    + "FROM system WHERE id IN (" + placeholders + ") AND Deleted_datetime IS NULL";

            List<Object> params = new ArrayList<>(systemIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object idObj = row.get("id");
                if (idObj == null) continue;
                
                int id = idObj instanceof Integer ? (Integer) idObj : ((Number) idObj).intValue();
                
                // Skip if this is a temporary cloned row (nobject_id)
                if (excludedIds.contains(id)) {
                    continue;
                }
                
                Map<String, Object> systemRow = new HashMap<>();
                systemRow.put("ID", id);
                systemRow.put("id", id);
                systemRow.put("Name", row.get("Name"));
                systemRow.put("name", row.get("Name"));
                // Map Name to ShortName for backward compatibility
                systemRow.put("ShortName", row.get("Name"));
                systemRow.put("shortName", row.get("Name"));
                systemRow.put("Long_Name", row.get("Long_Name"));
                systemRow.put("longName", row.get("Long_Name"));
                systemRow.put("Description", row.get("Description"));
                systemRow.put("description", row.get("Description"));
                systemRow.put("Ref_Number", row.get("Ref_Number"));
                systemRow.put("refNumber", row.get("Ref_Number"));
                systemRow.put("Parent_ID", row.get("Parent_ID"));
                systemRow.put("parent_id", row.get("Parent_ID"));
                rows.add(systemRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadSystemRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Dataset rows for given dataset IDs.
     */
    private List<Map<String, Object>> loadDatasetRows(Set<Integer> datasetIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (datasetIds == null || datasetIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(datasetIds.size(), "?"));
            // Fixed: Use PrimaryName instead of Name (column doesn't exist in schema)
            String sql = "SELECT ID, PrimaryName, definition AS Description, RefNumber AS Ref_Number, MasterSource, DeletedDatetime "
                    + "FROM dataset WHERE ID IN (" + placeholders + ") AND DeletedDatetime IS NULL";

            // Get active nobject_id values to exclude (temporary cloned rows from active CRs)
            Set<Integer> excludedIds = getActiveNObjectIdsForFacet("dataset");

            List<Object> params = new ArrayList<>(datasetIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Object idObj = row.get("ID");
                if (idObj == null) continue;
                
                int id = idObj instanceof Integer ? (Integer) idObj : ((Number) idObj).intValue();
                
                // Skip if this is a temporary cloned row (nobject_id)
                if (excludedIds.contains(id)) {
                    continue;
                }
                
                Map<String, Object> datasetRow = new HashMap<>();
                datasetRow.put("ID", id);
                datasetRow.put("id", id);
                // Map PrimaryName to both Name and name for backward compatibility
                datasetRow.put("Name", row.get("PrimaryName"));
                datasetRow.put("name", row.get("PrimaryName"));
                datasetRow.put("PrimaryName", row.get("PrimaryName"));
                datasetRow.put("Description", row.get("Description"));
                datasetRow.put("description", row.get("Description"));
                datasetRow.put("Ref_Number", row.get("Ref_Number"));
                datasetRow.put("refNumber", row.get("Ref_Number"));
                datasetRow.put("MasterSource", row.get("MasterSource"));
                datasetRow.put("masterSource", row.get("MasterSource"));
                rows.add(datasetRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadDatasetRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Attribute rows for given attribute IDs.
     */
    private List<Map<String, Object>> loadAttributeRows(Set<Integer> attributeIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (attributeIds == null || attributeIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(attributeIds.size(), "?"));
            // Fixed: Use PrimaryName instead of Name (column doesn't exist in schema)
            String sql = "SELECT ID, PrimaryName, Definition AS Description, Dataset_ID, Glossary_ID, DeletedDatetime "
                    + "FROM attribute WHERE ID IN (" + placeholders + ") AND DeletedDatetime IS NULL";

            List<Object> params = new ArrayList<>(attributeIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> attributeRow = new HashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    attributeRow.put("ID", id);
                    attributeRow.put("id", id);
                }
                // Map PrimaryName to both Name and name for backward compatibility
                attributeRow.put("Name", row.get("PrimaryName"));
                attributeRow.put("name", row.get("PrimaryName"));
                attributeRow.put("PrimaryName", row.get("PrimaryName"));
                attributeRow.put("Description", row.get("Description"));
                attributeRow.put("description", row.get("Description"));
                attributeRow.put("Dataset_ID", row.get("Dataset_ID"));
                attributeRow.put("dataset_id", row.get("Dataset_ID"));
                attributeRow.put("Glossary_ID", row.get("Glossary_ID"));
                attributeRow.put("glossary_id", row.get("Glossary_ID"));
                rows.add(attributeRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadAttributeRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Interface rows for given interface IDs.
     */
    private List<Map<String, Object>> loadInterfaceRows(Set<Integer> interfaceIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (interfaceIds == null || interfaceIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(interfaceIds.size(), "?"));
            // Match QueryBuilder interface list query so related-interface rows populate all Unison columns
            String sql = "SELECT i.id AS ID, "
                    + "i.Ref_number AS `Ref.`, "
                    + "i.Name AS Name, "
                    + "i.id AS Name_ID, "
                    + "i.Description AS Description, "
                    + "sys1.Name AS `Source System Short Name`, "
                    + "sys1.id AS `Source System Short Name_ID`, "
                    + "sys2.Name AS `Target System Short Name`, "
                    + "sys2.id AS `Target System Short Name_ID`, "
                    + "s.PrimaryName AS `BUDG Status`, "
                    + "a.Name AS Automation, "
                    + "f.Name AS Frequency, "
                    + "l.Name AS Lifecycle, "
                    + "i.Synchronisation_Control AS Synchronisation, "
                    + "i.Asset_ID AS `Asset ID`, "
                    + "c.Name AS Classification, "
                    + "tf.Name AS `Transfer Format`, "
                    + "tm.Name AS `Transfer Method`, "
                    + "CONCAT(p.First_Name, ' ', p.Last_Name) AS `Created By`, "
                    + "p.ID AS `Created By_ID`, "
                    + "i.Created_datetime AS `Created Date`, "
                    + "i.last_updatedtime AS `Last Updated`, "
                    + "v.Name AS `BUDG Viewing` "
                    + "FROM interface i "
                    + "LEFT JOIN status s ON i.status_id = s.ID "
                    + "LEFT JOIN viewing v ON i.is_Public = v.ID "
                    + "LEFT JOIN people p ON i.createdBy_ID = p.ID "
                    + "LEFT JOIN interface_classification c ON i.Classification_id = c.id "
                    + "LEFT JOIN interface_lifecycle l ON i.Lifecycle_id = l.id "
                    + "LEFT JOIN interface_frequency f ON i.Frequency_ID = f.id "
                    + "LEFT JOIN interface_automation a ON i.Automation_ID = a.id "
                    + "LEFT JOIN interface_transfer_format tf ON i.Transfer_Format_ID = tf.id "
                    + "LEFT JOIN interface_transfer tm ON i.Transfer_Method_ID = tm.id "
                    + "LEFT JOIN system sys1 ON i.Source_systemID = sys1.id "
                    + "LEFT JOIN system sys2 ON i.Target_systemID = sys2.id "
                    + "WHERE i.id IN (" + placeholders + ") AND i.deleted_datetime IS NULL";

            List<Object> params = new ArrayList<>(interfaceIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> interfaceRow = new LinkedHashMap<>(row);
                Object id = interfaceRow.get("ID");
                if (id != null && !interfaceRow.containsKey("id")) {
                    interfaceRow.put("id", id);
                }
                if (interfaceRow.containsKey("Name") && !interfaceRow.containsKey("name")) {
                    interfaceRow.put("name", interfaceRow.get("Name"));
                }
                if (interfaceRow.containsKey("Description") && !interfaceRow.containsKey("description")) {
                    interfaceRow.put("description", interfaceRow.get("Description"));
                }
                rows.add(interfaceRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadInterfaceRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Entity table + id/status columns for filtering out BUDG status = "Deleted" for Web Users.
     * Aligned with {@code BulkDeleteDAO} / {@code QueryBuilder} status joins.
     */
    private record BudgStatusBinding(String table, String idCol, String statusCol, String statusTable) {
    }

    /**
     * Same as {@code QueryBuilder#isAdminOrSuperAdmin}: super admin, or system role "admin" only.
     */
    private boolean isUnisonAdminOrSuperAdmin(Integer userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        try {
            if (SegmentAccessService.isSuperAdmin(userId)) {
                return true;
            }
        } catch (SQLException e) {
            // continue
        }
        String sql = """
            SELECT r.primaryname AS role_name
            FROM people p
            LEFT JOIN role r ON p.System_Role = r.id
            WHERE p.ID = ?
            """;
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String roleName = rs.getString("role_name");
                    if (roleName != null && "admin".equalsIgnoreCase(roleName.trim())) {
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            // assume not admin
        }
        return false;
    }

    /**
     * BUDG Status lookup binding for a canonical facet, or null when this rule should not apply.
     */
    private BudgStatusBinding getBudgStatusBindingForFacet(String facetId) {
        if (facetId == null) {
            return null;
        }
        String c = com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(facetId);
        if (c == null) {
            return null;
        }
        return switch (c) {
            case "DATASET" -> new BudgStatusBinding("dataset", "ID", "status", "status");
            case "SYSTEM" -> new BudgStatusBinding("system", "id", "status", "status");
            case "GLOSSARY" -> new BudgStatusBinding("glossary", "ID", "status", "status");
            case "PROCESS" -> new BudgStatusBinding("process", "id", "status", "status");
            case "PROJECT" -> new BudgStatusBinding("project", "id", "status", "status");
            case "POLICY" -> new BudgStatusBinding("policy", "ID", "status", "status");
            case "PRODUCT" -> new BudgStatusBinding("product", "id", "status", "status");
            case "CAPABILITY" -> new BudgStatusBinding("capability", "ID", "status", "status");
            case "CLIENT" -> new BudgStatusBinding("client", "ID", "status", "status");
            case "COMMITTEE" -> new BudgStatusBinding("committee", "ID", "status", "status");
            case "LEGAL_ENTITY" -> new BudgStatusBinding("legal", "ID", "status", "status");
            case "BUSINESS_AREA" -> new BudgStatusBinding("business_area", "ID", "status", "status");
            case "INTERFACE" -> new BudgStatusBinding("interface", "id", "status_id", "status");
            case "ORG_UNIT" -> new BudgStatusBinding("org_unit", "ID", "status_id", "status");
            case "REGULATION" -> new BudgStatusBinding("regulation", "ID", "RegulationStatus_ID", "regulation_status");
            case "REGULATORY_THEME" -> new BudgStatusBinding("regulatorytheme", "ID", "Status_ID", "status");
            // No lifecycle BUDG "Deleted" status (or out of scope): do not filter here
            case "ATTRIBUTE", "PEOPLE", "ROLE", "GEOGRAPHY", "REGULATOR", "CHANGE_REQUESTS", "CHANGE_REQUEST", "CHANGEREQUEST",
                 "ACTIVE_TASKS" -> null;
            default -> null;
        };
    }

    /**
     * For Web Users, remove object IDs whose BUDG status display name is "Deleted".
     * Admins / super admins return {@code ids} unchanged.
     */
    private Set<Integer> removeBudgStatusDeletedIdsForWebUser(String facetId, Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return ids;
        }
        if (isUnisonAdminOrSuperAdmin(currentUserId)) {
            return ids;
        }
        BudgStatusBinding b = getBudgStatusBindingForFacet(facetId);
        if (b == null) {
            return ids;
        }
        List<Integer> idList = new ArrayList<>(ids);
        final int batch = 500;
        Set<Integer> toRemove = new HashSet<>();
        for (int i = 0; i < idList.size(); i += batch) {
            int end = Math.min(i + batch, idList.size());
            List<Integer> sub = idList.subList(i, end);
            int n = sub.size();
            if (n == 0) {
                continue;
            }
            String placeholders = String.join(",", Collections.nCopies(n, "?"));
            String q = "SELECT t.`" + b.idCol() + "` AS oid FROM `" + b.table() + "` t "
                    + "INNER JOIN `" + b.statusTable() + "` s ON t.`" + b.statusCol() + "` = s.id "
                    + "WHERE t.`" + b.idCol() + "` IN (" + placeholders + ") "
                    + "AND LOWER(TRIM(s.primaryname)) = 'deleted'";
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement(q)) {
                for (int j = 0; j < n; j++) {
                    ps.setInt(j + 1, sub.get(j));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        toRemove.add(rs.getInt("oid"));
                    }
                }
            } catch (SQLException e) {
                System.err.println("[UnisonSearchService] removeBudgStatusDeletedIdsForWebUser: " + e.getMessage());
            }
        }
        if (toRemove.isEmpty()) {
            return ids;
        }
        Set<Integer> out = new HashSet<>(ids);
        out.removeAll(toRemove);
        return out;
    }

    /**
     * Generic method to load facet rows based on facet ID.
     */
    private List<Map<String, Object>> loadFacetRows(String facetId, Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Integer> filtered = removeBudgStatusDeletedIdsForWebUser(facetId, ids);
        if (filtered.isEmpty()) {
            return new ArrayList<>();
        }

        String normalized = facetId.toUpperCase();
        return switch (normalized) {
            case "DATASET", "DATA_SETS", "DATASETS" -> loadDatasetRows(filtered);
            case "SYSTEM", "SYSTEMS" -> loadSystemRows(filtered);
            case "ATTRIBUTE", "ATTRIBUTES" -> loadAttributeRows(filtered);
            case "GLOSSARY", "GLOSSARIES" -> loadGlossaryRows(filtered);
            case "INTERFACE", "INTERFACES" -> loadInterfaceRows(filtered);
            case "PEOPLE", "PERSON" -> loadPeopleRows(filtered);
            case "ORG_UNIT", "ORGUNIT", "ORG_UNITS" -> loadOrgUnitRows(filtered);
            case "ROLE", "ROLES" -> loadRoleRows(filtered);
            case "PROCESS", "PROCESSES" -> loadProcessRows(filtered);
            case "PROJECT", "PROJECTS" -> loadProjectRows(filtered);
            case "PRODUCT", "PRODUCTS" -> loadProductRows(filtered);
            case "POLICY", "POLICIES" -> loadPolicyRows(filtered);
            case "BUSINESS_AREA", "BUSINESSAREA" -> loadBusinessAreaRows(filtered);
            case "CAPABILITY", "CAPABILITIES" -> loadCapabilityRows(filtered);
            case "LEGAL_ENTITY", "LEGALENTITY" -> loadLegalEntityRows(filtered);
            case "GEOGRAPHY", "GEOGRAPHIES" -> loadGeographyRows(filtered);
            case "REGULATION", "REGULATIONS" -> loadRegulationRows(filtered);
            case "REGULATOR", "REGULATORS" -> loadRegulatorRows(filtered);
            case "REGULATORY_THEME", "REGULATORYTHEME" -> loadRegulatoryThemeRows(filtered);
            case "CHANGE_REQUEST", "CHANGEREQUEST", "CHANGE_REQUESTS" -> loadChangeRequestRows(filtered);
            case "ACTIVE_TASKS", "ACTIVETASKS", "ACTIVE-TASKS" -> loadActiveTaskRows(filtered);
            default -> new ArrayList<>();
        };
    }

    /**
     * Load Policy rows for given policy IDs.
     */
    private List<Map<String, Object>> loadPolicyRows(Set<Integer> policyIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (policyIds == null || policyIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(policyIds.size(), "?"));
            String sql = "SELECT ID, PrimaryName, Description, refNumber, DeletedDatetime "
                    + "FROM policy WHERE ID IN (" + placeholders + ") AND DeletedDatetime IS NULL";

            List<Object> params = new ArrayList<>(policyIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> policyRow = new HashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    policyRow.put("ID", id);
                    policyRow.put("id", id);
                    policyRow.put("Policy_ID", id);
                }
                policyRow.put("PrimaryName", row.get("PrimaryName"));
                policyRow.put("primaryName", row.get("PrimaryName"));
                policyRow.put("Description", row.get("Description"));
                policyRow.put("description", row.get("Description"));
                policyRow.put("Ref_Number", row.get("refNumber"));
                policyRow.put("refNumber", row.get("refNumber"));
                rows.add(policyRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadPolicyRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Legal Entity rows for given legal entity IDs.
     */
    private List<Map<String, Object>> loadLegalEntityRows(Set<Integer> legalEntityIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (legalEntityIds == null || legalEntityIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(legalEntityIds.size(), "?"));
            String sql = "SELECT l.ID, COALESCE(l.ShortName, l.LongName) AS PrimaryName, l.Description, NULL AS Ref_Number, l.DeleteDatetime "
                    + "FROM legal l WHERE l.ID IN (" + placeholders + ") AND (l.DeleteDatetime IS NULL OR l.DeleteDatetime = '')";

            List<Object> params = new ArrayList<>(legalEntityIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> legalEntityRow = new HashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    legalEntityRow.put("ID", id);
                    legalEntityRow.put("id", id);
                    legalEntityRow.put("Legal_ID", id);
                }
                legalEntityRow.put("PrimaryName", row.get("PrimaryName"));
                legalEntityRow.put("primaryName", row.get("PrimaryName"));
                legalEntityRow.put("Description", row.get("Description"));
                legalEntityRow.put("description", row.get("Description"));
                Object refNumber = row.get("Ref_Number");
                if (refNumber != null) {
                    legalEntityRow.put("Ref_Number", refNumber);
                    legalEntityRow.put("refNumber", refNumber);
                }
                rows.add(legalEntityRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadLegalEntityRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Geography rows for given geography IDs.
     */
    private List<Map<String, Object>> loadGeographyRows(Set<Integer> geographyIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (geographyIds == null || geographyIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(geographyIds.size(), "?"));
            String sql = "SELECT ID, PrimaryName, Description, DeletedDatetime "
                    + "FROM geography WHERE ID IN (" + placeholders + ") AND DeletedDatetime IS NULL";

            List<Object> params = new ArrayList<>(geographyIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> geographyRow = new HashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    geographyRow.put("ID", id);
                    geographyRow.put("id", id);
                }
                geographyRow.put("PrimaryName", row.get("PrimaryName"));
                geographyRow.put("primaryName", row.get("PrimaryName"));
                geographyRow.put("Description", row.get("Description"));
                geographyRow.put("description", row.get("Description"));
                rows.add(geographyRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadGeographyRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Regulation rows for given regulation IDs.
     */
    private List<Map<String, Object>> loadRegulationRows(Set<Integer> regulationIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (regulationIds == null || regulationIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(regulationIds.size(), "?"));
            // Fixed: Use ID instead of RegulationID (column doesn't exist in schema)
            String sql = "SELECT ID, primaryName, Description, RefNumber AS Ref_Number, DeletedDatetime "
                    + "FROM regulation WHERE ID IN (" + placeholders + ") AND DeletedDatetime IS NULL";

            List<Object> params = new ArrayList<>(regulationIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> regulationRow = new HashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    regulationRow.put("ID", id);
                    regulationRow.put("id", id);
                    // Map ID to RegulationID for backward compatibility
                    regulationRow.put("RegulationID", id);
                }
                regulationRow.put("primaryName", row.get("primaryName"));
                regulationRow.put("PrimaryName", row.get("primaryName"));
                regulationRow.put("Description", row.get("Description"));
                regulationRow.put("description", row.get("Description"));
                regulationRow.put("Ref_Number", row.get("Ref_Number"));
                regulationRow.put("refNumber", row.get("Ref_Number"));
                rows.add(regulationRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadRegulationRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Regulator rows for given regulator IDs.
     */
    private List<Map<String, Object>> loadRegulatorRows(Set<Integer> regulatorIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (regulatorIds == null || regulatorIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(regulatorIds.size(), "?"));
            // Fixed: Use ID instead of RegulatorID (column doesn't exist in schema)
            // Removed Ref_Number (column doesn't exist in regulator table)
            String sql = "SELECT ID, PrimaryName, ShortName, Description, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID, DeletedDatetime "
                    + "FROM regulator WHERE ID IN (" + placeholders + ") AND DeletedDatetime IS NULL";

            List<Object> params = new ArrayList<>(regulatorIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> regulatorRow = new HashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    regulatorRow.put("ID", id);
                    regulatorRow.put("id", id);
                    // Map ID to RegulatorID for backward compatibility
                    regulatorRow.put("RegulatorID", id);
                }
                regulatorRow.put("PrimaryName", row.get("PrimaryName"));
                regulatorRow.put("primaryName", row.get("PrimaryName"));
                regulatorRow.put("ShortName", row.get("ShortName"));
                regulatorRow.put("shortName", row.get("ShortName"));
                regulatorRow.put("Description", row.get("Description"));
                regulatorRow.put("description", row.get("Description"));
                regulatorRow.put("Created Date", row.get("CreateDatetime"));
                regulatorRow.put("createdDate", row.get("CreateDatetime"));
                regulatorRow.put("Last Updated", row.get("LastUpdateDatetime"));
                regulatorRow.put("lastUpdated", row.get("LastUpdateDatetime"));
                regulatorRow.put("Last Updated By", row.get("LastUpdate_UserID"));
                regulatorRow.put("lastUpdatedBy", row.get("LastUpdate_UserID"));
                rows.add(regulatorRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadRegulatorRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * Load Regulatory Theme rows for given regulatory theme IDs.
     */
    private List<Map<String, Object>> loadRegulatoryThemeRows(Set<Integer> regulatoryThemeIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (regulatoryThemeIds == null || regulatoryThemeIds.isEmpty()) {
            return rows;
        }

        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String placeholders = String.join(",", Collections.nCopies(regulatoryThemeIds.size(), "?"));
            String sql = "SELECT ID, PrimaryName, Description, RefNumber, DeletedDatetime "
                    + "FROM regulatorytheme WHERE ID IN (" + placeholders
                    + ") AND DeletedDatetime IS NULL";

            List<Object> params = new ArrayList<>(regulatoryThemeIds);
            List<Map<String, Object>> results = dbHelper.executeQuery(sql, params);

            for (Map<String, Object> row : results) {
                Map<String, Object> regulatoryThemeRow = new HashMap<>();
                Object id = row.get("ID");
                if (id != null) {
                    regulatoryThemeRow.put("ID", id);
                    regulatoryThemeRow.put("id", id);
                    regulatoryThemeRow.put("RegulatoryThemeID", id);
                }
                regulatoryThemeRow.put("PrimaryName", row.get("PrimaryName"));
                regulatoryThemeRow.put("primaryName", row.get("PrimaryName"));
                regulatoryThemeRow.put("Description", row.get("Description"));
                regulatoryThemeRow.put("description", row.get("Description"));
                regulatoryThemeRow.put("Ref_Number", row.get("RefNumber"));
                regulatoryThemeRow.put("refNumber", row.get("RefNumber"));
                rows.add(regulatoryThemeRow);
            }
        } catch (Exception e) {
            System.err.println("[UnisonSearchService] Error in loadRegulatoryThemeRows: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    // ============================================================================
    // FINAL DEDUPLICATION AND SORTING
    // ============================================================================

    /**
     * Apply final deduplication and deterministic sorting to results.
     * Ensures:
     * 1. No duplicate objects (same facet + ID)
     * 2. Minimum depth is preserved
     * 3. Results are sorted by facet priority, then by name/ID
     */
    private Map<String, FacetResult> applyFinalDeduplicationAndSorting(Map<String, FacetResult> results,
            EnrichmentContext context) {
        if (results == null || results.isEmpty()) {
            return results;
        }

        Map<String, FacetResult> finalResults = new LinkedHashMap<>();

        // Sort facets by priority
        List<Map.Entry<String, FacetResult>> sortedEntries = new ArrayList<>(results.entrySet());
        sortedEntries.sort((e1, e2) -> {
            int priority1 = getFacetPriority(e1.getKey());
            int priority2 = getFacetPriority(e2.getKey());
            if (priority1 != priority2) {
                return Integer.compare(priority1, priority2);
            }
            // Tie-breaker: sort by facet name
            return e1.getKey().compareToIgnoreCase(e2.getKey());
        });

        // Process each facet with deduplication
        for (Map.Entry<String, FacetResult> entry : sortedEntries) {
            String facetId = entry.getKey();
            FacetResult fr = entry.getValue();

            if (fr == null || fr.getIds() == null || fr.getIds().isEmpty()) {
                continue;
            }

            // Deduplicate IDs and ensure minimum depth
            Set<Integer> deduplicatedIds = new LinkedHashSet<>();
            Map<Integer, Integer> deduplicatedDepth = new HashMap<>();
            List<Map<String, Object>> rows = fr.getRows() != null ? new ArrayList<>(fr.getRows()) : new ArrayList<>();

            // Create map of rows by ID for sorting
            Map<Integer, Map<String, Object>> rowsById = new HashMap<>();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Object idObj = row.get("ID");
                    if (idObj == null) {
                        idObj = row.get("id");
                    }
                    if (idObj instanceof Integer) {
                        rowsById.put((Integer) idObj, row);
                    } else if (idObj instanceof Number) {
                        rowsById.put(((Number) idObj).intValue(), row);
                    }
                }
            }

            // Process each ID with depth tracking
            for (Integer id : fr.getIds()) {
                int existingDepth = context.getDepth(facetId, id);
                int resultDepth = fr.getDepthById() != null ? fr.getDepthById().getOrDefault(id, 1) : 1;

                // Use minimum depth
                int finalDepth = context.mergeDepth(existingDepth, resultDepth);
                context.markEnriched(facetId, id, finalDepth);

                deduplicatedIds.add(id);
                deduplicatedDepth.put(id, finalDepth);
            }

            // Sort rows by name/ID for tie-breaking
            if (!rows.isEmpty()) {
                rows.sort((r1, r2) -> {
                    // Try to get name for sorting
                    String name1 = getRowName(r1);
                    String name2 = getRowName(r2);
                    if (name1 != null && name2 != null) {
                        int nameCompare = name1.compareToIgnoreCase(name2);
                        if (nameCompare != 0) {
                            return nameCompare;
                        }
                    }
                    // Fallback to ID
                    Integer id1 = getRowId(r1);
                    Integer id2 = getRowId(r2);
                    if (id1 != null && id2 != null) {
                        return id1.compareTo(id2);
                    }
                    return 0;
                });
            }

            // Create sorted ID set based on row order
            Set<Integer> sortedIds = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                Integer id = getRowId(row);
                if (id != null && deduplicatedIds.contains(id)) {
                    sortedIds.add(id);
                }
            }
            // Add any IDs not in rows
            for (Integer id : deduplicatedIds) {
                if (!sortedIds.contains(id)) {
                    sortedIds.add(id);
                }
            }

            finalResults.put(facetId,
                    new FacetResult(sortedIds, fr.isHasActiveFilter(), deduplicatedDepth, rows, fr.getTotalCount()));
        }

        return finalResults;
    }

    /**
     * Get name from a row for sorting.
     */
    private String getRowName(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object name = row.get("Name");
        if (name == null) {
            name = row.get("name");
        }
        if (name == null) {
            name = row.get("PrimaryName");
        }
        if (name == null) {
            name = row.get("primaryName");
        }
        return name != null ? name.toString() : null;
    }

    /**
     * Get ID from a row.
     */
    private Integer getRowId(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object id = row.get("ID");
        if (id == null) {
            id = row.get("id");
        }
        if (id instanceof Integer) {
            return (Integer) id;
        } else if (id instanceof Number) {
            return ((Number) id).intValue();
        }
        return null;
    }

    // getDirectRelationshipsOnly method removed - using new UnifiedSearchService instead

    private static int subtreeEndIndex(int i, int[] indents, int n) {
        int base = indents[i];
        for (int j = i + 1; j < n; j++) {
            if (indents[j] <= base) {
                return j - 1;
            }
        }
        return n - 1;
    }

    private static List<Integer> directChildIndices(int parent, int end, int[] parentIndex) {
        List<Integer> out = new ArrayList<>();
        for (int j = parent + 1; j <= end; j++) {
            if (parentIndex[j] == parent) {
                out.add(j);
            }
        }
        return out;
    }

    private static Set<Integer> combineRootIdSets(Set<Integer> a, String op, Set<Integer> b) {
        if (a == null) {
            a = new HashSet<>();
        }
        if (b == null) {
            b = Collections.emptySet();
        }
        if ("OR".equals(op)) {
            Set<Integer> u = new HashSet<>(a);
            u.addAll(b);
            return u;
        }
        if ("NOT".equals(op)) {
            Set<Integer> x = new HashSet<>(a);
            x.removeAll(b);
            return x;
        }
        Set<Integer> x = new HashSet<>(a);
        x.retainAll(b);
        return x;
    }

    private String resolveAccumulatedFacetKey(Map<String, FacetResult> acc, String rootFacetId) {
        if (acc == null || rootFacetId == null) {
            return null;
        }
        String canon = com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(rootFacetId);
        for (String key : acc.keySet()) {
            if (key != null
                    && Objects.equals(
                            com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToCanonical(key), canon)) {
                return key;
            }
        }
        return null;
    }

    private Set<Integer> filterDatasetIdsByMasterSourceIn(Set<Integer> datasetUniverse, Set<Integer> systemIds)
            throws SQLException {
        if (datasetUniverse == null || datasetUniverse.isEmpty() || systemIds == null || systemIds.isEmpty()) {
            return new HashSet<>();
        }
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String dPh = String.join(",", Collections.nCopies(datasetUniverse.size(), "?"));
            String sPh = String.join(",", Collections.nCopies(systemIds.size(), "?"));
            List<Object> params = new ArrayList<>(datasetUniverse);
            params.addAll(systemIds);
            String sql = "SELECT ID FROM dataset WHERE ID IN (" + dPh + ") AND MasterSource IN (" + sPh
                    + ") AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, params);
            return extractIntColumnFromRows(rows, "ID");
        } catch (SQLException e) {
            System.err.println("[UnisonSearchService] filterDatasetIdsByMasterSourceIn: " + e.getMessage());
            return new HashSet<>();
        }
    }

    private static Set<Integer> extractIntColumnFromRows(List<Map<String, Object>> rows, String col) {
        Set<Integer> out = new HashSet<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            Object v = row != null ? row.get(col) : null;
            if (v == null && row != null) {
                v = row.get(col.toLowerCase(Locale.ROOT));
            }
            if (v instanceof Integer) {
                out.add((Integer) v);
            } else if (v instanceof Number) {
                out.add(((Number) v).intValue());
            }
        }
        return out;
    }

    private Set<Integer> filterDatasetsHavingMasterSource(Set<Integer> datasetUniverse) throws SQLException {
        if (datasetUniverse == null || datasetUniverse.isEmpty()) {
            return new HashSet<>();
        }
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String dPh = String.join(",", Collections.nCopies(datasetUniverse.size(), "?"));
            String sql = "SELECT ID FROM dataset WHERE ID IN (" + dPh
                    + ") AND MasterSource IS NOT NULL AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, new ArrayList<>(datasetUniverse));
            return extractIntColumnFromRows(rows, "ID");
        } catch (SQLException e) {
            System.err.println("[UnisonSearchService] filterDatasetsHavingMasterSource: " + e.getMessage());
            return new HashSet<>();
        }
    }

    private Set<Integer> filterSystemIdsByLinkedDatasets(Set<Integer> systemUniverse, Set<Integer> matchingDatasetIds)
            throws SQLException {
        if (systemUniverse == null || systemUniverse.isEmpty() || matchingDatasetIds == null
                || matchingDatasetIds.isEmpty()) {
            return new HashSet<>();
        }
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String dPh = String.join(",", Collections.nCopies(matchingDatasetIds.size(), "?"));
            String sql = "SELECT DISTINCT MasterSource FROM dataset WHERE ID IN (" + dPh
                    + ") AND MasterSource IS NOT NULL AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, new ArrayList<>(matchingDatasetIds));
            Set<Integer> fromDs = extractIntColumnFromRows(rows, "MasterSource");
            fromDs.retainAll(systemUniverse);
            return fromDs;
        } catch (SQLException e) {
            System.err.println("[UnisonSearchService] filterSystemIdsByLinkedDatasets: " + e.getMessage());
            return new HashSet<>();
        }
    }

    private Set<Integer> filterDatasetIdsByAttributeIds(Set<Integer> datasetUniverse, Set<Integer> attributeIds)
            throws SQLException {
        if (datasetUniverse == null || datasetUniverse.isEmpty() || attributeIds == null || attributeIds.isEmpty()) {
            return new HashSet<>();
        }
        try {
            com.example.unisonsearch.repository.DatabaseHelper dbHelper = new com.example.unisonsearch.repository.DatabaseHelper();
            String dPh = String.join(",", Collections.nCopies(datasetUniverse.size(), "?"));
            String aPh = String.join(",", Collections.nCopies(attributeIds.size(), "?"));
            List<Object> params = new ArrayList<>(datasetUniverse);
            params.addAll(attributeIds);
            String sql = "SELECT DISTINCT Dataset_ID FROM attribute WHERE Dataset_ID IN (" + dPh + ") AND ID IN ("
                    + aPh + ") AND DeletedDatetime IS NULL";
            List<Map<String, Object>> rows = dbHelper.executeQuery(sql, params);
            return extractIntColumnFromRows(rows, "Dataset_ID");
        } catch (SQLException e) {
            System.err.println("[UnisonSearchService] filterDatasetIdsByAttributeIds: " + e.getMessage());
            return new HashSet<>();
        }
    }

    private Set<Integer> evalGroupLeafAsRootIds(UnisonSearchRequest.SearchItem item,
            Map<String, FacetResult> accumulatedResults, String rootFacetId) throws SQLException {
        if (item == null || item.getFacet() == null) {
            return new HashSet<>();
        }
        String rootKey = resolveAccumulatedFacetKey(accumulatedResults, rootFacetId);
        FacetResult rootFr = rootKey != null ? accumulatedResults.get(rootKey) : null;
        if (rootFr == null || rootFr.getIds() == null || rootFr.getIds().isEmpty()) {
            return new HashSet<>();
        }
        Set<Integer> rootIds = new HashSet<>(rootFr.getIds());

        JsonObject searchDefinition = null;
        if (item.getFilters() != null && !item.getFilters().isEmpty()) {
            searchDefinition = buildSearchDefinition(item.getFacet(), item.getKeyword(), item.getFilters(),
                    item.getSearchFields());
        } else if (item.getSearchFields() != null && !item.getSearchFields().isEmpty()) {
            searchDefinition = buildSearchDefinition(item.getFacet(), item.getKeyword(), null, item.getSearchFields());
        }

        String itemFacetId = normalizedFacetToFacetId(normalizeFacetName(item.getFacet()));
        String rootCanon = normalizedFacetToFacetId(normalizeFacetName(rootFacetId));

        if (isKeywordEmpty(item.getKeyword())) {
            if (itemFacetId != null && itemFacetId.equals(rootCanon)) {
                return new HashSet<>(rootIds);
            }
            RootToRelationConfig cfg = getRootToRelationConfig(rootFacetId, item.getFacet());
            if (cfg != null) {
                return getObjectIdsWithRelationToFacet(rootIds, rootFacetId, item.getFacet());
            }
            if ("DATASET".equals(rootCanon) && "SYSTEM".equals(itemFacetId)) {
                return filterDatasetsHavingMasterSource(rootIds);
            }
            return new HashSet<>();
        }

        if (itemFacetId != null && itemFacetId.equals(rootCanon)) {
            Set<Integer> matches = searchDefinition != null
                    ? executeSingleSearchWithDefinition(item.getFacet(), searchDefinition)
                    : executeSingleSearch(item.getFacet(), item.getKeyword(), item.getFilters(), item.getSearchFields());
            Set<Integer> out = new HashSet<>(rootIds);
            out.retainAll(matches);
            return out;
        }

        String peopleNorm = item.getFacet().trim().toUpperCase(Locale.ROOT);
        if ("PEOPLE".equals(peopleNorm) || "PERSON".equals(peopleNorm)) {
            String rootFilterCanon = canonFacet(rootFacetId);
            if (rootFilterCanon == null) {
                rootFilterCanon = rootCanon;
            }
            boolean useStakeholder = getStakeholderTableConfig(rootFacetId) != null;
            boolean useCreatedBy = getCreatedByDatabaseColumn(rootFilterCanon) != null;
            if (!useStakeholder && !useCreatedBy) {
                return new HashSet<>();
            }
            Set<Integer> matchingPeople = searchDefinition != null
                    ? executeSingleSearchWithDefinition(item.getFacet(), searchDefinition)
                    : executeSingleSearch(item.getFacet(), item.getKeyword(), item.getFilters(), item.getSearchFields());
            if (matchingPeople.isEmpty()) {
                return new HashSet<>();
            }
            Set<Integer> byStakeholder = useStakeholder
                    ? getObjectIdsWithRelationToSpecificTargets(rootIds, rootFacetId, item.getFacet(), matchingPeople)
                    : new HashSet<>();
            Set<Integer> byCreatedBy = useCreatedBy
                    ? getObjectIdsWithCreatedByPeople(rootIds, rootFacetId, matchingPeople)
                    : new HashSet<>();
            Set<Integer> combined = new HashSet<>(byStakeholder);
            combined.addAll(byCreatedBy);
            return combined;
        }

        if ("DATASET".equals(rootCanon) && "SYSTEM".equals(itemFacetId)) {
            Set<Integer> matchingSystems = searchDefinition != null
                    ? executeSingleSearchWithDefinition(item.getFacet(), searchDefinition)
                    : executeSingleSearch(item.getFacet(), item.getKeyword(), item.getFilters(), item.getSearchFields());
            return filterDatasetIdsByMasterSourceIn(rootIds, matchingSystems);
        }

        if ("SYSTEM".equals(rootCanon) && "DATASET".equals(itemFacetId)) {
            Set<Integer> matchingDatasets = searchDefinition != null
                    ? executeSingleSearchWithDefinition(item.getFacet(), searchDefinition)
                    : executeSingleSearch(item.getFacet(), item.getKeyword(), item.getFilters(), item.getSearchFields());
            return filterSystemIdsByLinkedDatasets(rootIds, matchingDatasets);
        }

        if ("DATASET".equals(rootCanon)
                && ("ATTRIBUTE".equals(itemFacetId) || "ATTRIBUTES".equals(itemFacetId))) {
            Set<Integer> attrIds = searchDefinition != null
                    ? executeSingleSearchWithDefinition(item.getFacet(), searchDefinition)
                    : executeSingleSearch(item.getFacet(), item.getKeyword(), item.getFilters(), item.getSearchFields());
            return filterDatasetIdsByAttributeIds(rootIds, attrIds);
        }

        return new HashSet<>();
    }

    private Set<Integer> evaluateIndentedGroupMember(int memberIdx, List<UnisonSearchRequest.SearchItem> searches,
            int[] indentLevels, int[] parentIndex, int nSearches, Map<String, FacetResult> accumulatedResults,
            String rootFacetId) throws SQLException {
        int end = subtreeEndIndex(memberIdx, indentLevels, nSearches);
        List<Integer> nested = directChildIndices(memberIdx, end, parentIndex);
        if (nested.isEmpty()) {
            return evalGroupLeafAsRootIds(searches.get(memberIdx), accumulatedResults, rootFacetId);
        }
        Set<Integer> inner = evaluateIndentedGroupMember(
                nested.get(0), searches, indentLevels, parentIndex, nSearches, accumulatedResults, rootFacetId);
        for (int k = 1; k < nested.size(); k++) {
            int ni = nested.get(k);
            String op = searches.get(ni).getOperator() != null
                    ? searches.get(ni).getOperator().toUpperCase(Locale.ROOT)
                    : "OR";
            inner = combineRootIdSets(inner, op,
                    evaluateIndentedGroupMember(ni, searches, indentLevels, parentIndex, nSearches, accumulatedResults,
                            rootFacetId));
        }
        return inner;
    }

    private Map<String, FacetResult> applyIndentedChildGroup(Map<String, FacetResult> accumulatedResults,
            List<UnisonSearchRequest.SearchItem> searches, List<Integer> childIndices, int[] indentLevels,
            int[] parentIndex, int nSearches, String rootFacetId, int maxDepth, TraversalScope rootScope,
            SegmentAccessContext accessCtx, String correlationId) throws SQLException {
        if (childIndices.isEmpty()) {
            return accumulatedResults;
        }
        Set<Integer> groupVal = evaluateIndentedGroupMember(
                childIndices.get(0), searches, indentLevels, parentIndex, nSearches, accumulatedResults, rootFacetId);
        for (int k = 1; k < childIndices.size(); k++) {
            int idx = childIndices.get(k);
            UnisonSearchRequest.SearchItem it = searches.get(idx);
            String op = it.getOperator() != null ? it.getOperator().toUpperCase(Locale.ROOT) : "OR";
            groupVal = combineRootIdSets(groupVal, op,
                    evaluateIndentedGroupMember(idx, searches, indentLevels, parentIndex, nSearches, accumulatedResults,
                            rootFacetId));
        }
        UnisonSearchRequest.SearchItem firstChild = searches.get(childIndices.get(0));
        String linkOp = firstChild.getOperator() != null ? firstChild.getOperator().toUpperCase(Locale.ROOT) : "AND";
        return applyGroupedRootFilter(accumulatedResults, rootFacetId, linkOp, groupVal, maxDepth, rootScope, accessCtx,
                correlationId);
    }

    private Map<String, FacetResult> applyGroupedRootFilter(Map<String, FacetResult> accumulatedResults,
            String rootFacetId, String linkOp, Set<Integer> groupRootIds, int maxDepth, TraversalScope rootScope,
            SegmentAccessContext accessCtx, String correlationId) throws SQLException {
        String rootKey = resolveAccumulatedFacetKey(accumulatedResults, rootFacetId);
        if (rootKey == null) {
            return accumulatedResults;
        }
        FacetResult rootFr = accumulatedResults.get(rootKey);
        if (rootFr == null || rootFr.getIds() == null || rootFr.getIds().isEmpty()) {
            return accumulatedResults;
        }
        Set<Integer> cur = new HashSet<>(rootFr.getIds());
        Set<Integer> g = groupRootIds != null ? groupRootIds : new HashSet<>();

        Set<Integer> newRoot;
        if ("OR".equals(linkOp)) {
            newRoot = new HashSet<>(cur);
            newRoot.addAll(g);
        } else if ("NOT".equals(linkOp)) {
            newRoot = new HashSet<>(cur);
            newRoot.removeAll(g);
        } else {
            newRoot = new HashSet<>(cur);
            newRoot.retainAll(g);
        }

        if (newRoot.isEmpty()) {
            return new HashMap<>();
        }

        if (maxDepth <= 0) {
            Map<String, FacetResult> out = new HashMap<>(accumulatedResults);
            Map<Integer, Integer> dm = new HashMap<>();
            for (Integer id : newRoot) {
                dm.put(id, 0);
            }
            out.put(rootKey, new FacetResult(newRoot, true, dm));
            return out;
        }

        TraversalStats stats = new TraversalStats();
        String normRoot = normalizeFacetName(rootFacetId);
        Map<String, FacetResult> filterMap = graphTraversalService.findConnectedObjects(
                normRoot, newRoot, maxDepth, null, rootScope, accessCtx, stats, correlationId);
        return compoundQueryService.intersectResults(accumulatedResults, filterMap);
    }
}

