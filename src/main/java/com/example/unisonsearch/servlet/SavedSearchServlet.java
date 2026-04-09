package com.example.unisonsearch.servlet;

import java.io.*;
import java.sql.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import com.google.gson.*;
import java.util.*;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.JwtUtil;
import com.nimbusds.jwt.JWTClaimsSet;
import com.example.unisonsearch.model.UnisonSearchRequest;
import com.example.unisonsearch.model.UnisonSearchResponse;
import com.example.unisonsearch.service.UnisonSearchService;
import com.example.unisonsearch.service.SearchService;
import com.example.unisonsearch.service.GraphTraversalService;
import com.example.unisonsearch.service.RelationshipManager;
import com.example.unisonsearch.service.ConfigurationService;
import com.example.unisonsearch.service.CompoundQueryService;
import com.example.unisonsearch.repository.QueryBuilder;
import com.example.unisonsearch.repository.DatabaseHelper;
import com.google.gson.reflect.TypeToken;

/**
 * Servlet للتعامل مع Saved Searches
 * يدعم: Create, Update, Delete, List, Share, Run
 */
@WebServlet("/api/search/*")
public class SavedSearchServlet extends HttpServlet {

    // Connection is now obtained via the shared DatabaseConnection pool
    // (was previously hardcoded to localhost which caused a split-brain with the main DB)

    private Gson gson = new Gson();
    private final ConfigurationService configurationService = new ConfigurationService();
    private final RelationshipManager relationshipManager = new RelationshipManager();
    private final QueryBuilder queryBuilder = new QueryBuilder(configurationService, relationshipManager);
    private final DatabaseHelper databaseHelper = new DatabaseHelper();
    private final SearchService searchService = new SearchService(queryBuilder, databaseHelper);
    private final GraphTraversalService graphTraversalService = new GraphTraversalService(relationshipManager, databaseHelper);
    private final CompoundQueryService compoundQueryService = new CompoundQueryService();
    private final UnisonSearchService unisonSearchService = new UnisonSearchService(searchService, graphTraversalService, compoundQueryService);

    /**
     * GET Methods:
     * /api/search/my - جلب البحوث الخاصة بي
     * /api/search/public - جلب البحوث العامة
     * /api/search/shared - جلب البحوث المشاركة معي
     * /api/search/{id} - جلب بحث محدد
     * /api/search/{id}/run - تشغيل البحث
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        Integer userId = getUserIdFromSession(request);

        if (userId == null) {
            sendError(response, 401, "غير مصرح لك بالدخول");
            return;
        }

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // Default: return all accessible searches
                getAllSearches(response, userId);
            } else if (pathInfo.equals("/my")) {
                getMySearches(response, userId);
            } else if (pathInfo.equals("/public")) {
                getPublicSearches(response, userId);
            } else if (pathInfo.equals("/shared")) {
                getSharedWithMe(response, userId);
            } else if (pathInfo.equals("/recent")) {
                getRecentViews(response, userId);
            } else if (pathInfo.equals("/quick-link")) {
                getQuickLink(response, userId);
            } else {
                String[] parts = pathInfo.split("/");
                if (parts.length >= 2) {
                    int searchId = Integer.parseInt(parts[1]);

                    if (parts.length == 3 && parts[2].equals("run")) {
                        runSearch(request, response, userId, searchId);
                    } else {
                        getSearchById(response, userId, searchId);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, 500, "خطأ في الخادم: " + e.getMessage());
        }
    }

    /**
     * POST Methods:
     * /api/search - إنشاء بحث جديد
     * /api/search/{id}/share - مشاركة البحث
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        Integer userId = getUserIdFromSession(request);

        if (userId == null) {
            sendError(response, 401, "غير مصرح لك بالدخول");
            return;
        }

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                createSearch(request, response, userId);
            } else {
                String[] parts = pathInfo.split("/");
                if (parts.length == 3 && parts[2].equals("share")) {
                    int searchId = Integer.parseInt(parts[1]);
                    shareSearch(request, response, userId, searchId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, 500, "خطأ في الخادم: " + e.getMessage());
        }
    }

    /**
     * PUT Method:
     * /api/search/{id} - تحديث البحث
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        Integer userId = getUserIdFromSession(request);

        if (userId == null) {
            sendError(response, 401, "غير مصرح لك بالدخول");
            return;
        }

        try {
            String[] parts = pathInfo.split("/");
            if (parts.length >= 2) {
                int searchId = Integer.parseInt(parts[1]);
                updateSearch(request, response, userId, searchId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, 500, "خطأ في الخادم: " + e.getMessage());
        }
    }

    /**
     * DELETE Method:
     * /api/search/{id} - حذف البحث
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        Integer userId = getUserIdFromSession(request);

        if (userId == null) {
            sendError(response, 401, "غير مصرح لك بالدخول");
            return;
        }

        try {
            String[] parts = pathInfo.split("/");
            if (parts.length >= 2) {
                int searchId = Integer.parseInt(parts[1]);
                deleteSearch(request, response, userId, searchId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, 500, "خطأ في الخادم: " + e.getMessage());
        }
    }

    // ==================== GET Methods Implementation ====================

    /**
     * GET /api/search/quick-link
     * Returns the quick link for the current user:
     *   1. User-specific row (target_user_id = userId)
     *   2. Global row (target_user_id IS NULL)
     *   3. null if neither exists
     */
    private void getQuickLink(HttpServletResponse response, int userId)
            throws SQLException, IOException {

        String userSql = "SELECT uql.search_id, uql.search_name, uql.description " +
                "FROM user_quick_link uql " +
                "INNER JOIN user_search us ON us.id = uql.search_id " +
                "WHERE uql.target_user_id = ? LIMIT 1";

        String globalSql = "SELECT uql.search_id, uql.search_name, uql.description " +
                "FROM user_quick_link uql " +
                "INNER JOIN user_search us ON us.id = uql.search_id " +
                "WHERE uql.target_user_id IS NULL LIMIT 1";

        try (Connection conn = getConnection()) {
            Map<String, Object> row = null;

            try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        row = new HashMap<>();
                        row.put("savedSearchId", String.valueOf(rs.getInt("search_id")));
                        row.put("savedSearchName", rs.getString("search_name") != null ? rs.getString("search_name") : "");
                        row.put("description", rs.getString("description") != null ? rs.getString("description") : "");
                    }
                }
            }

            if (row == null) {
                try (PreparedStatement ps = conn.prepareStatement(globalSql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        row = new HashMap<>();
                        row.put("savedSearchId", String.valueOf(rs.getInt("search_id")));
                        row.put("savedSearchName", rs.getString("search_name") != null ? rs.getString("search_name") : "");
                        row.put("description", rs.getString("description") != null ? rs.getString("description") : "");
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            if (row != null) {
                result.put("definition", gson.toJson(row));
            } else {
                result.put("definition", null);
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(result));
        }
    }

    private void getMySearches(HttpServletResponse response, int userId)
            throws SQLException, IOException {

        // Only return searches owned by the user (not shared with them)
        String sql = "SELECT s.* " +
                "FROM user_search s " +
                "WHERE s.user_reference = ? " +
                "ORDER BY s.created_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            List<Map<String, Object>> searches = resultSetToList(rs);
            
            // Add shared users info for each search
            for (Map<String, Object> search : searches) {
                int searchId = (Integer) search.get("id");
                try {
                    List<Map<String, Object>> sharedUsers = getSharedUsers(conn, searchId);
                    search.put("shared_users", sharedUsers);
                } catch (SQLException e) {
                    // If error getting shared users, just set empty list
                    search.put("shared_users", new ArrayList<>());
                }
            }
            
            sendSuccess(response, searches);
        } catch (SQLException e) {
            e.printStackTrace();
            // Return empty list on error instead of failing
            sendSuccess(response, new ArrayList<>());
        }
    }
    
    /**
     * Get list of users that a search is shared with
     */
    private List<Map<String, Object>> getSharedUsers(Connection conn, int searchId) throws SQLException {
        List<Map<String, Object>> users = new ArrayList<>();
        
        String sql = "SELECT ux.user_reference, p.First_Name, p.Last_Name, p.Email " +
                "FROM user_x_search ux " +
                "LEFT JOIN people p ON ux.user_reference = p.ID " +
                "WHERE ux.search_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, searchId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("id", rs.getInt("user_reference"));
                    String firstName = rs.getString("First_Name");
                    String lastName = rs.getString("Last_Name");
                    String email = rs.getString("Email");
                    if (firstName != null && lastName != null) {
                        user.put("name", firstName + " " + lastName);
                    } else {
                        user.put("name", email != null ? email : "Unknown");
                    }
                    user.put("email", email);
                    users.add(user);
                }
            }
        }
        
        return users;
    }

    private void getPublicSearches(HttpServletResponse response, int userId)
            throws SQLException, IOException {

        String sql = "SELECT * FROM user_search WHERE is_public = 1 ORDER BY hitcount DESC, created_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            List<Map<String, Object>> searches = resultSetToList(rs);
            sendSuccess(response, searches);
        }
    }

    /**
     * Returns searches that are either explicitly shared with the user (user_x_search)
     * or public (is_public = 1), excluding the user's own searches.
     * Public saved searches appear here for everyone.
     */
    private void getSharedWithMe(HttpServletResponse response, int userId)
            throws SQLException, IOException {

        String sql = "SELECT DISTINCT s.*, p.First_Name, p.Last_Name, p.Email " +
                "FROM user_search s " +
                "LEFT JOIN people p ON s.user_reference = p.ID " +
                "WHERE s.user_reference != ? " +
                "AND ( " +
                "  EXISTS (SELECT 1 FROM user_x_search x WHERE x.search_id = s.id AND x.user_reference = ?) " +
                "  OR s.is_public = 1 " +
                ") " +
                "ORDER BY s.created_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();

            List<Map<String, Object>> searches = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> search = resultSetToMap(rs);
                
                // Add shared by user info
                String firstName = rs.getString("First_Name");
                String lastName = rs.getString("Last_Name");
                String email = rs.getString("Email");
                if (firstName != null && lastName != null) {
                    search.put("shared_by", firstName + " " + lastName + (email != null ? " (" + email + ")" : ""));
                } else {
                    search.put("shared_by", email != null ? email : "Unknown User");
                }
                
                // Add shared users info (same as owner sees)
                int searchId = (Integer) search.get("id");
                try {
                    List<Map<String, Object>> sharedUsers = getSharedUsers(conn, searchId);
                    search.put("shared_users", sharedUsers);
                } catch (SQLException e) {
                    // If error getting shared users, just set empty list
                    search.put("shared_users", new ArrayList<>());
                }
                
                searches.add(search);
            }
            
            sendSuccess(response, searches);
        } catch (SQLException e) {
            e.printStackTrace();
            // Return empty list on error instead of failing
            sendSuccess(response, new ArrayList<>());
        }
    }

    private void getRecentViews(HttpServletResponse response, int userId)
            throws SQLException, IOException {

        // Get last 10 recent views from user_search_usage
        // If no usage records exist, return empty list (not an error)
        String sql = "SELECT DISTINCT " +
                "    us.id, " +
                "    us.name, " +
                "    us.description, " +
                "    us.hitcount, " +
                "    us.is_public, " +
                "    us.user_reference, " +
                "    MAX(usu.visited_at) as last_visited " +
                "FROM user_search_usage usu " +
                "INNER JOIN user_search us ON usu.search_reference = us.id " +
                "WHERE usu.user_reference = ? " +
                "    AND (us.user_reference = ? OR us.is_public = 1 OR EXISTS (" +
                "        SELECT 1 FROM user_x_search uxs " +
                "        WHERE uxs.search_id = us.id AND uxs.user_reference = ?" +
                "    )) " +
                "GROUP BY us.id, us.name, us.description, us.hitcount, us.is_public, us.user_reference " +
                "ORDER BY last_visited DESC " +
                "LIMIT 10";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            stmt.setInt(3, userId);
            ResultSet rs = stmt.executeQuery();

            List<Map<String, Object>> searches = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> search = new HashMap<>();
                search.put("id", rs.getInt("id"));
                search.put("name", rs.getString("name"));
                search.put("description", rs.getString("description"));
                search.put("hitcount", rs.getInt("hitcount"));
                search.put("is_public", rs.getBoolean("is_public"));
                search.put("user_reference", rs.getInt("user_reference"));
                Timestamp lastVisited = rs.getTimestamp("last_visited");
                search.put("last_visited", lastVisited != null ? lastVisited.toString() : null);
                searches.add(search);
            }
            // Always return success, even if list is empty
            sendSuccess(response, searches);
        } catch (SQLException e) {
            e.printStackTrace();
            // On SQL error, return empty list instead of error
            sendSuccess(response, new ArrayList<>());
        }
    }

    private void getAllSearches(HttpServletResponse response, int userId)
            throws SQLException, IOException {

        String sql = "SELECT DISTINCT s.*, " +
                "CASE WHEN s.user_reference = ? THEN 'owner' " +
                "     WHEN s.is_public = 1 THEN 'public' " +
                "     ELSE 'shared' END as access_type " +
                "FROM user_search s " +
                "LEFT JOIN user_x_search x ON s.id = x.search_id " +
                "WHERE s.user_reference = ? OR s.is_public = 1 OR x.user_reference = ? " +
                "ORDER BY s.created_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            stmt.setInt(3, userId);
            ResultSet rs = stmt.executeQuery();

            List<Map<String, Object>> searches = resultSetToList(rs);
            sendSuccess(response, searches);
        }
    }

    private void getSearchById(HttpServletResponse response, int userId, int searchId)
            throws SQLException, IOException {

        if (!canUserAccess(userId, searchId)) {
            sendError(response, 403, "ليس لديك صلاحية للوصول لهذا البحث");
            return;
        }

        String sql = "SELECT * FROM user_search WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, searchId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Map<String, Object> search = resultSetToMap(rs);
                
                // Add shared users info
                try {
                    List<Map<String, Object>> sharedUsers = getSharedUsers(conn, searchId);
                    search.put("shared_users", sharedUsers);
                } catch (SQLException e) {
                    // If error getting shared users, just set empty list
                    search.put("shared_users", new ArrayList<>());
                }
                
                sendSuccess(response, search);
            } else {
                sendError(response, 404, "Not found");
            }
        }
    }

    private void runSearch(HttpServletRequest request, HttpServletResponse response, int userId, int searchId)
            throws SQLException, IOException {

        String role = getRoleFromRequest(request);
        boolean isSuperAdmin = AppRoleNames.isSuperAdminName(role);

        if (!isSuperAdmin && !canUserAccess(userId, searchId)) {
            sendError(response, 403, "error.savedSearch.noAccess");
            return;
        }

        Connection conn = null;
        try {
            conn = getConnection();

            // 1. Get search definition
            String searchSql = "SELECT condition_definition FROM user_search WHERE id = ?";
            String conditionJson = null;

            try (PreparedStatement stmt = conn.prepareStatement(searchSql)) {
                stmt.setInt(1, searchId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    conditionJson = rs.getString("condition_definition");
                }
            }

            if (conditionJson == null) {
                sendError(response, 404, "Not Found");
                return;
            }

            // 2. Log usage (skip silently if user doesn't exist in people table, e.g. guest)
            String usageSql = "INSERT INTO user_search_usage (user_reference, search_reference) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(usageSql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, searchId);
                stmt.executeUpdate();
            } catch (java.sql.SQLIntegrityConstraintViolationException fkEx) {
                // Guest/anonymous users may not have a row in people — skip usage tracking
                System.out.println("[SavedSearchServlet] Skipping usage log for userId=" + userId + " (not in people table)");
            }

            // 3. Update hit count
            String updateSql = "UPDATE user_search SET hitcount = COALESCE(hitcount, 0) + 1 WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setInt(1, searchId);
                stmt.executeUpdate();
            }

            // 4. Return search definition for frontend to execute
            Map<String, Object> result = new HashMap<>();
            result.put("searchId", searchId);
            result.put("conditionDefinition", gson.fromJson(conditionJson, Object.class));

            // Execute server-side using Unison pipeline with segment filtering
            try {
                UnisonSearchRequest unisonRequest = buildUnisonRequestFromSavedDefinition(conditionJson);
                UnisonSearchResponse searchResult = unisonSearchService.executeUnisonSearch(
                        unisonRequest.getSearches(), unisonRequest.getOptions() != null ? unisonRequest.getOptions().getMaxDepth() : 1, userId);
                result.put("searchResult", searchResult);
            } catch (Exception ex) {
                System.err.println("[SavedSearchServlet] Failed to execute saved search server-side: " + ex.getMessage());
            }

            result.put("message", "تم تسجيل الاستخدام بنجاح");

            sendSuccess(response, result);

        } finally {
            if (conn != null) conn.close();
        }
    }

    private UnisonSearchRequest buildUnisonRequestFromSavedDefinition(String conditionJson) {
        JsonObject json = gson.fromJson(conditionJson, JsonObject.class);
        List<UnisonSearchRequest.SearchItem> items = new ArrayList<>();
        if (json != null && json.has("searchGroups") && json.get("searchGroups").isJsonArray()) {
            JsonArray groups = json.getAsJsonArray("searchGroups");
            for (JsonElement gEl : groups) {
                JsonObject g = gEl.getAsJsonObject();
                String groupOp = g.has("operator") ? g.get("operator").getAsString() : "START";
                if (!g.has("searches") || !g.get("searches").isJsonArray()) continue;
                JsonArray searches = g.getAsJsonArray("searches");
                for (JsonElement sEl : searches) {
                    JsonObject s = sEl.getAsJsonObject();
                    UnisonSearchRequest.SearchItem item = new UnisonSearchRequest.SearchItem();
                    item.setOperator(s.has("operator") ? s.get("operator").getAsString() : groupOp);
                    if (s.has("facetId")) {
                        item.setFacet(s.get("facetId").getAsString());
                    } else if (s.has("facet")) {
                        item.setFacet(s.get("facet").getAsString());
                    }
                    // Try to pull keyword from query filterGroup
                    if (s.has("filterGroups") && s.get("filterGroups").isJsonArray()) {
                        JsonArray fgs = s.getAsJsonArray("filterGroups");
                        for (JsonElement fgEl : fgs) {
                            JsonObject fg = fgEl.getAsJsonObject();
                            if (fg.has("query")) {
                                item.setKeyword(fg.get("query").getAsString());
                                break;
                            }
                        }
                    }
                    // Store filters map for fidelity
                    Map<String, Object> filters = gson.fromJson(s, new TypeToken<Map<String, Object>>(){}.getType());
                    item.setFilters(filters);
                    items.add(item);
                }
            }
        }
        UnisonSearchRequest.SearchOptions opts = new UnisonSearchRequest.SearchOptions();
        return new UnisonSearchRequest(items, opts);
    }

    // ==================== POST Methods Implementation ====================

    private void createSearch(HttpServletRequest request, HttpServletResponse response, int userId)
            throws SQLException, IOException {

        // Web users are not allowed to create saved searches
        if (isWebUser(getRoleFromRequest(request))) {
            sendError(response, 403, "Saving searches is not allowed for web users.");
            return;
        }

        // Ensure user exists in i_user table
        ensureUserExists(userId);

        // Read request body
        String body = getRequestBody(request);
        JsonObject json = gson.fromJson(body, JsonObject.class);

        String name = json.has("name") ? json.get("name").getAsString() : null;
        if (name == null && json.has("conditionDefinition")) {
            // Try to get name from conditionDefinition (new format)
            JsonObject conditionDef = json.getAsJsonObject("conditionDefinition");
            if (conditionDef.has("name")) {
                name = conditionDef.get("name").getAsString();
            }
        }
        
        String description = json.has("description") ? json.get("description").getAsString() : null;
        
        // Support both old format (conditionDefinition as separate field) and new format (searchGroups)
        String conditionDefinition;
        if (json.has("conditionDefinition")) {
            // New format: full searchGroups structure
            JsonObject conditionDef = json.getAsJsonObject("conditionDefinition");
            // Ensure it has userRef, name, searchGroups, public fields
            if (!conditionDef.has("userRef")) {
                conditionDef.addProperty("userRef", String.valueOf(userId));
            }
            if (!conditionDef.has("name") && name != null) {
                conditionDef.addProperty("name", name);
            }
            if (!conditionDef.has("public")) {
                conditionDef.addProperty("public", json.has("isPublic") && json.get("isPublic").getAsBoolean());
            }
            conditionDefinition = gson.toJson(conditionDef);
        } else if (json.has("searchGroups")) {
            // Alternative format: searchGroups at root level
            JsonObject conditionDef = new JsonObject();
            conditionDef.addProperty("userRef", String.valueOf(userId));
            conditionDef.addProperty("name", name != null ? name : "Untitled Search");
            conditionDef.add("searchGroups", json.getAsJsonArray("searchGroups"));
            conditionDef.addProperty("public", json.has("isPublic") && json.get("isPublic").getAsBoolean());
            conditionDefinition = gson.toJson(conditionDef);
        } else {
            // Old format: just conditionDefinition as JSON string
            conditionDefinition = gson.toJson(json.get("conditionDefinition"));
        }
        
        boolean isPublic = json.has("isPublic") && json.get("isPublic").getAsBoolean();

        String sql = "INSERT INTO user_search (name, description, condition_definition, user_reference, is_public, hitcount) " +
                "VALUES (?, ?, ?, ?, ?, 0)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setString(3, conditionDefinition);
            stmt.setInt(4, userId);
            stmt.setBoolean(5, isPublic);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int newId = rs.getInt(1);

                    Map<String, Object> result = new HashMap<>();
                    result.put("id", newId);
                    result.put("message", "saved");

                    sendSuccess(response, result);
                }
            }
        }
    }

    private void shareSearch(HttpServletRequest request, HttpServletResponse response,
                             int userId, int searchId) throws SQLException, IOException {

        // Check if user is owner
        if (!isOwner(userId, searchId)) {
            sendError(response, 403, "only admin can share");
            return;
        }

        String body = getRequestBody(request);
        JsonObject json = gson.fromJson(body, JsonObject.class);
        
        // Support both old format (userIds array) and new format (isPublic + userIds)
        boolean isPublic = json.has("isPublic") && json.get("isPublic").getAsBoolean();
        JsonArray userIds = json.has("userIds") ? json.getAsJsonArray("userIds") : new JsonArray();

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            // Update is_public flag based on sharing type
            // If Public → is_public = true
            // If Limited (has userIds) → is_public = false
            boolean shouldBePublic = isPublic && userIds.size() == 0;
            String updatePublicSql = "UPDATE user_search SET is_public = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updatePublicSql)) {
                stmt.setBoolean(1, shouldBePublic);
                stmt.setInt(2, searchId);
                stmt.executeUpdate();
            }

            // Delete existing shares
            String deleteSql = "DELETE FROM user_x_search WHERE search_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, searchId);
                stmt.executeUpdate();
            }

            // Insert new shares (only if Limited sharing)
            if (!shouldBePublic && userIds.size() > 0) {
                // Validate: prevent sharing with self
                // Get search owner
                String ownerSql = "SELECT user_reference FROM user_search WHERE id = ?";
                int searchOwnerId = -1;
                try (PreparedStatement ownerStmt = conn.prepareStatement(ownerSql)) {
                    ownerStmt.setInt(1, searchId);
                    try (ResultSet ownerRs = ownerStmt.executeQuery()) {
                        if (ownerRs.next()) {
                            searchOwnerId = ownerRs.getInt("user_reference");
                        }
                    }
                }
                
                // Check if owner is trying to share with themselves
                for (int i = 0; i < userIds.size(); i++) {
                    int shareUserId = userIds.get(i).getAsInt();
                    if (shareUserId == searchOwnerId) {
                        conn.rollback();
                        sendError(response, 400, "You cannot share the saved search with yourself. Please choose another user.");
                        return;
                    }
                }
                
                String insertSql = "INSERT INTO user_x_search (search_id, user_reference) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    for (int i = 0; i < userIds.size(); i++) {
                        int shareUserId = userIds.get(i).getAsInt();
                        stmt.setInt(1, searchId);
                        stmt.setInt(2, shareUserId);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            }

            conn.commit();

            Map<String, Object> result = new HashMap<>();
            result.put("message", "تمت المشاركة بنجاح");
            result.put("isPublic", shouldBePublic);
            result.put("sharedWith", userIds.size());

            sendSuccess(response, result);

        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // ==================== PUT Method Implementation ====================

    private void updateSearch(HttpServletRequest request, HttpServletResponse response,
                              int userId, int searchId) throws SQLException, IOException {

        // Web users are not allowed to update saved searches
        if (isWebUser(getRoleFromRequest(request))) {
            sendError(response, 403, "Saving searches is not allowed for web users.");
            return;
        }

        if (!isOwner(userId, searchId)) {
            sendError(response, 403, "only admin can edit");
            return;
        }

        String body = getRequestBody(request);
        JsonObject json = gson.fromJson(body, JsonObject.class);

        String name = json.has("name") ? json.get("name").getAsString() : null;
        if (name == null && json.has("conditionDefinition")) {
            JsonObject conditionDef = json.getAsJsonObject("conditionDefinition");
            if (conditionDef.has("name")) {
                name = conditionDef.get("name").getAsString();
            }
        }
        
        String description = json.has("description") ? json.get("description").getAsString() : null;
        
        // Support both old and new format for conditionDefinition
        String conditionDefinition;
        if (json.has("conditionDefinition")) {
            JsonObject conditionDef = json.getAsJsonObject("conditionDefinition");
            // Ensure it has userRef, name, searchGroups, public fields
            if (!conditionDef.has("userRef")) {
                conditionDef.addProperty("userRef", String.valueOf(userId));
            }
            if (!conditionDef.has("name") && name != null) {
                conditionDef.addProperty("name", name);
            }
            if (!conditionDef.has("public")) {
                conditionDef.addProperty("public", json.has("isPublic") && json.get("isPublic").getAsBoolean());
            }
            conditionDefinition = gson.toJson(conditionDef);
        } else if (json.has("searchGroups")) {
            JsonObject conditionDef = new JsonObject();
            conditionDef.addProperty("userRef", String.valueOf(userId));
            conditionDef.addProperty("name", name != null ? name : "Untitled Search");
            conditionDef.add("searchGroups", json.getAsJsonArray("searchGroups"));
            conditionDef.addProperty("public", json.has("isPublic") && json.get("isPublic").getAsBoolean());
            conditionDefinition = gson.toJson(conditionDef);
        } else {
            conditionDefinition = gson.toJson(json.get("conditionDefinition"));
        }
        
        boolean isPublic = json.has("isPublic") && json.get("isPublic").getAsBoolean();

        String sql = "UPDATE user_search SET name = ?, description = ?, condition_definition = ?, is_public = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setString(3, conditionDefinition);
            stmt.setBoolean(4, isPublic);
            stmt.setInt(5, searchId);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("message", "تم تحديث البحث بنجاح");
                sendSuccess(response, result);
            } else {
                sendError(response, 404, "البحث غير موجود");
            }
        }
    }

    // ==================== DELETE Method Implementation ====================

    private void deleteSearch(HttpServletRequest request, HttpServletResponse response, int userId, int searchId)
            throws SQLException, IOException {

        // Web users are not allowed to delete saved searches
        if (isWebUser(getRoleFromRequest(request))) {
            sendError(response, 403, "Saving searches is not allowed for web users.");
            return;
        }

        if (!isOwner(userId, searchId)) {
            sendError(response, 403, "يمكن فقط للمالك حذف البحث");
            return;
        }

        String sql = "DELETE FROM user_search WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, searchId);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("message", "تم حذف البحث بنجاح");
                sendSuccess(response, result);
            } else {
                sendError(response, 404, "البحث غير موجود");
            }
        }
    }

    // ==================== Helper Methods ====================

    private Connection getConnection() throws SQLException {
        return com.example.budg_v2.database.DatabaseConnection.getConnection();
    }

    private Integer getUserIdFromSession(HttpServletRequest request) {
        // First, try to get from request attributes (set by AuthFilter)
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            if (userIdAttr instanceof Integer) {
                return (Integer) userIdAttr;
            } else if (userIdAttr instanceof Number) {
                return ((Number) userIdAttr).intValue();
            }
        }
        
        // Fallback: parse ACCESS_TOKEN cookie directly if filter didn't set attributes
        // This is needed because AuthFilter allows GET requests without token,
        // but we need authentication for /api/search endpoints
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                Integer userId = JwtUtil.getUserIdFromToken(token);
                if (userId != null) {
                    // Set attributes for future use
                    request.setAttribute("userId", userId);
                    request.setAttribute("userEmail", claims.getStringClaim("email"));
                    request.setAttribute("userName", (claims.getStringClaim("firstName") + " " + claims.getStringClaim("lastName")).trim());
                    request.setAttribute("userRole", claims.getStringClaim("role"));
                    return userId;
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired, continue to check session
        }
        
        // Last fallback: try to get from session
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object userId = session.getAttribute("userId");
            if (userId != null) {
                if (userId instanceof Integer) {
                    return (Integer) userId;
                } else if (userId instanceof Number) {
                    return ((Number) userId).intValue();
                }
            }
        }
        
        return null;
    }

    /**
     * Get user role from request (set by AuthFilter or JWT). Used to restrict saved-search create/update/delete to non-web users.
     */
    private String getRoleFromRequest(HttpServletRequest request) {
        Object roleAttr = request.getAttribute("userRole");
        if (roleAttr != null) {
            return String.valueOf(roleAttr).trim();
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object role = session.getAttribute("userRole");
            if (role != null) {
                return String.valueOf(role).trim();
            }
        }
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                String role = claims.getStringClaim("role");
                if (role != null) {
                    request.setAttribute("userRole", role);
                    return role.trim();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    /**
     * True if the role is "web user" (or any non-admin, non-super-admin). Such users cannot create/update/delete saved searches.
     */
    private boolean isWebUser(String role) {
        if (role == null || role.isBlank()) return true;
        return !AppRoleNames.isAdminOrSuperAdminName(role);
    }
    
    /**
     * Helper method to get cookie value by name
     */
    private String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Ensure user exists in i_user table. Creates a row if it doesn't exist.
     * 
     * @param userId The user ID from session
     * @throws SQLException if database error occurs
     */
    private void ensureUserExists(int userId) throws SQLException {
        String checkSql = "SELECT COUNT(*) as count FROM i_user WHERE reference = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            
            checkStmt.setInt(1, userId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt("count") == 0) {
                    // User doesn't exist, create it
                    String insertSql = "INSERT INTO i_user (reference, active) VALUES (?, 1)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, userId);
                        insertStmt.executeUpdate();
                    }
                }
            }
        }
    }

    private boolean canUserAccess(int userId, int searchId) throws SQLException {
        String sql = "SELECT COUNT(*) as can_access FROM user_search s " +
                "LEFT JOIN user_x_search x ON s.id = x.search_id " +
                "WHERE s.id = ? AND (s.user_reference = ? OR s.is_public = 1 OR x.user_reference = ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, searchId);
            stmt.setInt(2, userId);
            stmt.setInt(3, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("can_access") > 0;
            }
        }
        return false;
    }

    private boolean isOwner(int userId, int searchId) throws SQLException {
        String sql = "SELECT user_reference FROM user_search WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, searchId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("user_reference") == userId;
            }
        }
        return false;
    }

    private String getRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        while (rs.next()) {
            list.add(resultSetToMap(rs));
        }
        return list;
    }

    private Map<String, Object> resultSetToMap(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            Object value = rs.getObject(i);

            // Parse JSON fields
            if (columnName.equals("condition_definition") && value != null) {
                value = gson.fromJson(value.toString(), Object.class);
            }

            map.put(columnName, value);
        }
        return map;
    }

    private void sendSuccess(HttpServletResponse response, Object data) throws IOException {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", data);

        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(gson.toJson(result));
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);

        response.setStatus(status);
        response.getWriter().write(gson.toJson(result));
    }
}
