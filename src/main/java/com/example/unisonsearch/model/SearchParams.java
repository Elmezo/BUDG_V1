package com.example.unisonsearch.model;

import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.UserContextUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates request parameters for Unison search.
 * Built from HttpServletRequest via fromRequest() without changing existing
 * parsing logic.
 */
public class SearchParams {
    public final String module;
    public final String q;
    public final Integer idFilter;
    public final String relatedModule;
    public final Integer relatedId;
    public final String[] pathParts;
    public final List<SearchCondition> conditions;

    // New fields for AST-based parsing
    public final String rawQuery;
    public final boolean useNewParser;

    // User ID for segment filtering
    public final Integer userId;

    public SearchParams(String module, String q, Integer idFilter, String relatedModule, Integer relatedId,
            String[] pathParts, List<SearchCondition> conditions, Integer userId) {
        this.module = module;
        this.q = q;
        this.idFilter = idFilter;
        this.relatedModule = relatedModule;
        this.relatedId = relatedId;
        this.pathParts = pathParts;
        this.conditions = conditions != null ? conditions : new ArrayList<>();
        this.userId = userId;

        // Determine if we should use the new parser
        this.rawQuery = q;
        this.useNewParser = shouldUseNewParser(q, conditions);
    }

    /**
     * Determine if the new AST-based parser should be used.
     * Use new parser if:
     * 1. Query contains boolean operators (AND, OR, NOT)
     * 2. Query contains parentheses
     * 3. No conditions array is provided (prefer raw query parsing)
     */
    private static boolean shouldUseNewParser(String query, List<SearchCondition> conditions) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        // If conditions array is provided and not empty, use legacy mode
        if (conditions != null && !conditions.isEmpty()) {
            return false;
        }

        // Check if query contains operators or parentheses
        String upperQuery = query.toUpperCase();
        return upperQuery.contains(" AND ") ||
                upperQuery.contains(" OR ") ||
                upperQuery.contains(" NOT ") ||
                query.contains("(") ||
                query.contains(")");
    }

    public static SearchParams fromRequest(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        
        String module = null;
        String[] parts = new String[] {};
        
        if (pathInfo != null && !pathInfo.isEmpty()) {
            // Remove leading slash if present, then split
            String cleanPath = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            parts = cleanPath.isEmpty() ? new String[] {} : cleanPath.split("/");
            
            // First non-empty part is the module
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty()) {
                    module = part.trim();
                    break;
                }
            }
        }

        String q = request.getParameter("q");

        Integer idFilter = null;
        // After cleaning, parts[0] is module, parts[1] could be idFilter
        if (parts.length >= 2) {
            try {
                idFilter = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        if (idFilter == null && parts.length >= 3 && "id".equalsIgnoreCase(parts[1])) {
            try {
                idFilter = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
            }
        }

        String relatedModule = request.getParameter("relatedModule");
        Integer relatedId = null;
        try {
            String relatedIdParam = request.getParameter("relatedId");
            if (relatedIdParam != null && !relatedIdParam.trim().isEmpty()) {
                relatedId = Integer.parseInt(relatedIdParam);
            }
        } catch (NumberFormatException ignored) {
        }

        // Parse multiple conditions
        List<SearchCondition> conditions = parseConditions(request);

        // Get user ID for segment filtering
        // 1) request attribute (preferred - set by AuthFilter)
        Integer userId = UserContextUtil.getCurrentUserIdOrNull(request);
        
        if (userId != null) {
            //system.out.println("[SearchParams] ✅ Got userId from request attribute: " + userId);
        } else {
            // 2) ACCESS_TOKEN cookie (fallback - UnisonSearchServlet may not be behind AuthFilter)
            try {
                Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        if ("ACCESS_TOKEN".equals(cookie.getName())) {
                            String token = cookie.getValue();
                            userId = JwtUtil.getUserIdFromToken(token);
                            //system.out.println("[SearchParams] ✅ Extracted userId from ACCESS_TOKEN cookie: " + userId);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                //system.out.println("[SearchParams] ❌ Failed to extract userId from ACCESS_TOKEN cookie: " + e.getMessage());
            }
        }

        return new SearchParams(module, q, idFilter, relatedModule, relatedId, parts, conditions, userId);
    }

    private static List<SearchCondition> parseConditions(HttpServletRequest request) {
        List<SearchCondition> conditions = new ArrayList<>();

        // Check for conditions array: conditions[0][operator], conditions[0][module],
        // conditions[0][query], etc.
        int index = 0;
        while (true) {
            String operator = request.getParameter("conditions[" + index + "][operator]");
            String conditionModule = request.getParameter("conditions[" + index + "][module]");
            String query = request.getParameter("conditions[" + index + "][query]");

            if (operator == null && conditionModule == null && query == null) {
                break; // No more conditions
            }

            if (conditionModule != null && query != null && !query.trim().isEmpty()) {
                conditions.add(new SearchCondition(operator, conditionModule, query));
            }

            index++;

            // Safety limit to prevent infinite loops
            if (index > 100)
                break;
        }

        return conditions;
    }
}
