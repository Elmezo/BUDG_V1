package com.example.budg_v2;

import com.example.budg_v2.search.SearchService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@WebServlet(name = "SearchServlet", urlPatterns = {"/api/search"})
public class SearchServlet extends HttpServlet {

    private final Gson gson = new Gson();
    private final SearchService searchService = new SearchService();


    // Mapping between Elasticsearch index names and SegmentAccessService object types
    private static final Map<String, String> INDEX_TO_OBJECT_TYPE;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("system", "System");
        map.put("dataset", "Dataset");
        map.put("interface", "SystemInterface");
        map.put("glossary", "Glossary");
        map.put("process", "Process");
        map.put("project", "Project");
        map.put("product", "Product");
        map.put("policy", "Policy");
        map.put("business_area", "BusinessArea");
        map.put("capability", "Capability");
        map.put("legal", "LegalEntity");
        map.put("client", "Client");
        map.put("committee", "Committee");
        map.put("geography", "Geography");
        map.put("regulation", "Regulation");
        map.put("regulator", "Regulator");
        map.put("regulatory_theme", "RegulatoryTheme");
        map.put("dataquality", "Dataset");
        map.put("attribute", "Dataset");
        map.put("org_unit", "OrgUnit");
        map.put("people", "People");
        INDEX_TO_OBJECT_TYPE = Collections.unmodifiableMap(map);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try {
            String keyword = req.getParameter("q");
            String sizeParam = req.getParameter("size");
            String syncParam = req.getParameter("sync");

            int size = 10;
            if (sizeParam != null && !sizeParam.trim().isEmpty()) {
                try {
                    size = Integer.parseInt(sizeParam);
                } catch (NumberFormatException e) {
                    // Use default size
                }
            }

            boolean syncOnce = true;
            if (syncParam != null && !syncParam.trim().isEmpty()) {
                syncOnce = Boolean.parseBoolean(syncParam);
            }

            if (syncOnce) {
                // Use force=false so sync only happens once (first call after server start).
                // The SearchService.synced flag ensures subsequent calls are instant no-ops.
                searchService.ensureSynced(false);
            }

            if (keyword == null || keyword.trim().isEmpty()) {
                resp.getWriter().write(gson.toJson(Collections.emptyMap()));
                return;
            }

            Map<String, Object> results = searchService.searchAll(keyword.trim(), size);

            // Apply filtering to search results based on user access
            Integer userId = UserContextUtil.getCurrentUserIdOrNull(req);
            
            if (userId != null && userId > 0) {
                // Authenticated user - filter by selected segments
                results = filterSearchResultsBySelectedSegments(results, userId);
            } else {
                // Guest user (userId <= 0 or null) - filter by public + enterprise only
                results = filterSearchResultsForGuests(results);
            }

            resp.getWriter().write(gson.toJson(results));

        } catch (Exception e) {
            System.err.println("Search error: " + e.getMessage());
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(Map.of("error", "Search error: " + e.getMessage())));
        }
    }

    /**
     * Filter search results to only include objects in the user's selected segments
     */
    private Map<String, Object> filterSearchResultsBySelectedSegments(Map<String, Object> results, int userId) {
        Map<String, Object> filteredResults = new LinkedHashMap<>();

        try {
            for (Map.Entry<String, Object> entry : results.entrySet()) {
                String indexName = entry.getKey();
                Object resultObj = entry.getValue();

                if (!(resultObj instanceof Map)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) resultObj;
                Object dataObj = resultMap.get("data");

                if (!(dataObj instanceof List)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;

                // Get object type for this index
                String objectType = INDEX_TO_OBJECT_TYPE.get(indexName);
                if (objectType == null) {
                    // If we don't have a mapping, include all results for this index
                    filteredResults.put(indexName, resultMap);
                    continue;
                }

                // Extract object IDs from search results
                List<Integer> objectIds = dataList.stream()
                    .map(item -> {
                        Object idObj = item.get("id");
                        if (idObj instanceof Number) {
                            return ((Number) idObj).intValue();
                        } else if (idObj instanceof String) {
                            try {
                                return Integer.parseInt((String) idObj);
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

                if (objectIds.isEmpty()) {
                    continue;
                }

                // Filter objects by selected segments
                Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, objectType, objectIds);

                // Filter the data list to only include accessible objects
                List<Map<String, Object>> filteredData = dataList.stream()
                    .filter(item -> {
                        Object idObj = item.get("id");
                        Integer id = null;
                        if (idObj instanceof Number) {
                            id = ((Number) idObj).intValue();
                        } else if (idObj instanceof String) {
                            try {
                                id = Integer.parseInt((String) idObj);
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        }
                        return id != null && accessibleIds.contains(id);
                    })
                    .collect(Collectors.toList());

                if (!filteredData.isEmpty()) {
                    Map<String, Object> filteredResultMap = new LinkedHashMap<>(resultMap);
                    filteredResultMap.put("data", filteredData);
                    filteredResultMap.put("total", filteredData.size());
                    filteredResults.put(indexName, filteredResultMap);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error filtering search results by segments: " + e.getMessage());
            // Return original results if filtering fails
            return results;
        }

        return filteredResults;
    }
    
    /**
     * Filter search results for guest users.
     * Guests can only see objects that are:
     * 1. Public (Is_Public = 1 or AccessControlType = 1)
     * 2. In Enterprise segment (Segment_ID = 1) OR have no segment assignment
     * 3. Not deleted
     */
    private Map<String, Object> filterSearchResultsForGuests(Map<String, Object> results) {
        Map<String, Object> filteredResults = new LinkedHashMap<>();

        try {
            for (Map.Entry<String, Object> entry : results.entrySet()) {
                String indexName = entry.getKey();
                Object resultObj = entry.getValue();

                if (!(resultObj instanceof Map)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) resultObj;
                Object dataObj = resultMap.get("data");

                if (!(dataObj instanceof List)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;

                // Get object type for this index
                String objectType = INDEX_TO_OBJECT_TYPE.get(indexName);
                if (objectType == null) {
                    // Skip objects without type mapping (guests can't access unknown types)
                    continue;
                }

                // Filter objects by guest access rules
                List<Map<String, Object>> filteredData = dataList.stream()
                    .filter(item -> {
                        Object idObj = item.get("id");
                        Integer id = null;
                        if (idObj instanceof Number) {
                            id = ((Number) idObj).intValue();
                        } else if (idObj instanceof String) {
                            try {
                                id = Integer.parseInt((String) idObj);
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        }
                        
                        if (id == null) {
                            return false;
                        }
                        
                        // Check if guest can access this object
                        try {
                            return SegmentAccessService.canGuestAccessObject(id, objectType);
                        } catch (SQLException e) {
                            System.err.println("Error checking guest access for " + objectType + " " + id + ": " + e.getMessage());
                            return false; // Deny access on error
                        }
                    })
                    .collect(Collectors.toList());

                if (!filteredData.isEmpty()) {
                    Map<String, Object> filteredResultMap = new LinkedHashMap<>(resultMap);
                    filteredResultMap.put("data", filteredData);
                    filteredResultMap.put("total", filteredData.size());
                    filteredResults.put(indexName, filteredResultMap);
                }
            }
        } catch (Exception e) {
            System.err.println("Error filtering search results for guests: " + e.getMessage());
            e.printStackTrace();
            // Return empty results on error (safer than returning unfiltered results)
            return new LinkedHashMap<>();
        }

        return filteredResults;
    }
}
