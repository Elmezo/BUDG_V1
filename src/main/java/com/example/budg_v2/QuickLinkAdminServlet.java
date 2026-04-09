package com.example.budg_v2;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quick Link admin CRUD servlet.
 *
 * GET    /admin/api/quick-link/list   — list all assignments
 * POST   /admin/api/quick-link/save   — upsert (create or update)
 * DELETE /admin/api/quick-link/remove — delete an assignment
 *
 * Role enforcement:
 *   - Super Admin: all operations allowed.
 *   - Admin: may save/remove personal link (targetUserId = self) or
 *     assign to a WebUser only. Cannot save global link (targetUserId = null).
 */
@WebServlet(name = "QuickLinkAdminServlet", urlPatterns = {"/admin/api/quick-link/*"})
public class QuickLinkAdminServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(QuickLinkAdminServlet.class);

    private final Gson gson = new Gson();

    /** List JSON must include null targetUserId so the admin UI can detect global rows (default Gson omits null keys). */
    private final Gson gsonList = new GsonBuilder().serializeNulls().create();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    // -------------------------------------------------------------------------
    // GET /admin/api/quick-link/list
    // -------------------------------------------------------------------------
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String path = req.getPathInfo();
        boolean isListPath = matchesActionPath(path, req.getRequestURI(), "list", true);
        if (!isListPath) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found - path: " + path + "\"}");
            return;
        }

        String sql = "SELECT " +
                "uql.id, " +
                "uql.target_user_id, " +
                "CASE WHEN uql.target_user_id IS NULL THEN NULL " +
                "     ELSE CONCAT(pu.First_Name, ' ', pu.Last_Name) END AS target_user_name, " +
                "CASE WHEN uql.target_user_id IS NULL THEN NULL " +
                "     ELSE pu.Email END AS target_user_email, " +
                "uql.search_id, " +
                "uql.search_name, " +
                "uql.description, " +
                "DATE_FORMAT(uql.created_at, '%Y-%m-%dT%H:%i:%s') AS created_at " +
                "FROM user_quick_link uql " +
                "LEFT JOIN people pu ON pu.ID = uql.target_user_id " +
                "ORDER BY uql.target_user_id IS NOT NULL, uql.created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                int targetUserId = rs.getInt("target_user_id");
                // wasNull() must be read immediately after target_user_id — not after other columns
                boolean targetUserIdNull = rs.wasNull();
                row.put("id", rs.getInt("id"));
                row.put("targetUserId", targetUserIdNull ? null : targetUserId);
                row.put("targetUserName", rs.getString("target_user_name"));
                row.put("targetUserEmail", rs.getString("target_user_email"));
                row.put("searchId", rs.getInt("search_id"));
                row.put("searchName", rs.getString("search_name"));
                row.put("description", rs.getString("description"));
                row.put("createdAt", rs.getString("created_at"));
                list.add(row);
            }
            resp.getWriter().write(gsonList.toJson(list));

        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    // -------------------------------------------------------------------------
    // POST /admin/api/quick-link/save
    // -------------------------------------------------------------------------
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String path = req.getPathInfo();
        // Accept /save, /save/, and base endpoint /admin/api/quick-link
        boolean isSavePath = matchesActionPath(path, req.getRequestURI(), "save", true);
        if (!isSavePath) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found - path: " + path + "\"}");
            return;
        }

        String callerRole = (String) req.getAttribute("userRole");
        Integer callerId = (Integer) req.getAttribute("userId");
        boolean isSuperAdmin = AppRoleNames.isSuperAdminName(callerRole);
        boolean isAdmin = AppRoleNames.isAdminOnlyName(callerRole);

        // Parse request body
        JsonObject body;
        try {
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            body = JsonParser.parseString(sb.toString()).getAsJsonObject();
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\":false,\"error\":\"Invalid JSON\"}");
            return;
        }

        // targetUserId: null means global link
        Integer targetUserId = null;
        if (body.has("targetUserId") && !body.get("targetUserId").isJsonNull()) {
            targetUserId = body.get("targetUserId").getAsInt();
        }

        if (!body.has("searchId") || body.get("searchId").isJsonNull()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\":false,\"error\":\"searchId is required\"}");
            return;
        }
        int searchId = body.get("searchId").getAsInt();
        String searchName = body.has("searchName") && !body.get("searchName").isJsonNull()
                ? body.get("searchName").getAsString() : "";
        String description = body.has("description") && !body.get("description").isJsonNull()
                ? body.get("description").getAsString() : "";

        // Role enforcement
        if (targetUserId == null && !isSuperAdmin) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"success\":false,\"error\":\"Super Admin only\"}");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {

            // Validate searchId exists
            if (!searchExists(conn, searchId)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"success\":false,\"error\":\"Search not found\"}");
                return;
            }

            // Validate targetUserId if provided
            if (targetUserId != null) {
                String targetRole = getRoleForUser(conn, targetUserId);
                if (targetRole == null) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"success\":false,\"error\":\"User not found or inactive\"}");
                    return;
                }
                // Admin can only assign to WebUsers
                if (isAdmin && !isWebUser(targetRole)) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.getWriter().write("{\"success\":false,\"error\":\"Admin can only assign to WebUsers\"}");
                    return;
                }
            }

            // Upsert: check if row exists first (NULL != NULL in UNIQUE, so manual check)
            Integer existingId = findExistingRow(conn, targetUserId);
            if (existingId != null) {
                // UPDATE
                String updateSql = "UPDATE user_quick_link SET search_id=?, search_name=?, description=?, " +
                        "created_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, searchId);
                    ps.setString(2, searchName);
                    ps.setString(3, description);
                    ps.setInt(4, callerId);
                    ps.setInt(5, existingId);
                    ps.executeUpdate();
                }
            } else {
                // INSERT
                String insertSql = "INSERT INTO user_quick_link (target_user_id, search_id, search_name, description, created_by) " +
                        "VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    if (targetUserId == null) {
                        ps.setNull(1, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(1, targetUserId);
                    }
                    ps.setInt(2, searchId);
                    ps.setString(3, searchName);
                    ps.setString(4, description);
                    ps.setInt(5, callerId);
                    ps.executeUpdate();
                }
            }

            // Activity log
            Map<String, Object> newState = new HashMap<>();
            newState.put("Search ID", String.valueOf(searchId));
            newState.put("Search Name", searchName);
            if (targetUserId != null) newState.put("Target User ID", String.valueOf(targetUserId));
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("component", ActivityLogConstants.COMPONENT_QUICK_LINKS);
            ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_APP_SETTINGS,
                    ActivityLogConstants.COMPONENT_QUICK_LINKS,
                    existingId != null ? ActivityLogConstants.CHANGE_TYPE_UPDATE : ActivityLogConstants.CHANGE_TYPE_CREATE,
                    new HashMap<>(), newState, ctx);

            resp.getWriter().write("{\"success\":true,\"message\":\"Quick Link saved\"}");

        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"success\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /admin/api/quick-link/remove
    // -------------------------------------------------------------------------
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String path = req.getPathInfo();
        String requestUri = req.getRequestURI();
        Object callerIdAttr = req.getAttribute("userId");
        // Accept /remove, /remove/, and base endpoint /admin/api/quick-link
        boolean isRemovePath = matchesActionPath(path, requestUri, "remove", true);
        if (!isRemovePath) {
            log.warn("QuickLink DELETE: path mismatch pathInfo={} requestURI={} servletPath={}",
                    path, requestUri, req.getServletPath());
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found - path: " + path + "\"}");
            return;
        }

        String callerRole = (String) req.getAttribute("userRole");
        boolean isSuperAdmin = AppRoleNames.isSuperAdminName(callerRole);

        JsonObject body;
        String rawBody = "";
        try {
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            rawBody = sb.toString();
            body = JsonParser.parseString(rawBody.isEmpty() ? "{}" : rawBody).getAsJsonObject();
        } catch (Exception e) {
            log.warn("QuickLink DELETE: invalid JSON callerId={} role={} rawBody={} — {}",
                    callerIdAttr, callerRole, rawBody, e.getMessage());
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\":false,\"error\":\"Invalid JSON\"}");
            return;
        }

        Integer targetUserId = null;
        if (body.has("targetUserId") && !body.get("targetUserId").isJsonNull()) {
            int tid = body.get("targetUserId").getAsInt();
            // 0 is never a valid people.ID; reject mistaken "global" payloads from old clients
            if (tid <= 0) {
                log.warn("QuickLink DELETE: invalid targetUserId={} (must be > 0 for per-user remove)", tid);
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"success\":false,\"error\":\"Invalid targetUserId\"}");
                return;
            }
            targetUserId = tid;
        }

        log.info("QuickLink DELETE remove: callerId={} role={} superAdmin={} targetUserId={} rawBody={}",
                callerIdAttr, callerRole, isSuperAdmin, targetUserId, rawBody);

        // Only Super Admin can remove the global link
        if (targetUserId == null && !isSuperAdmin) {
            log.warn("QuickLink DELETE: forbidden — global remove requires Super Admin (callerId={})", callerIdAttr);
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"success\":false,\"error\":\"Super Admin only\"}");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String deleteSql;
            PreparedStatement ps;
            if (targetUserId == null) {
                deleteSql = "DELETE FROM user_quick_link WHERE target_user_id IS NULL";
                ps = conn.prepareStatement(deleteSql);
            } else {
                deleteSql = "DELETE FROM user_quick_link WHERE target_user_id = ?";
                ps = conn.prepareStatement(deleteSql);
                ps.setInt(1, targetUserId);
            }

            int rows = ps.executeUpdate();
            ps.close();

            if (rows == 0) {
                log.warn("QuickLink DELETE: no row deleted (targetUserId={}) — table user_quick_link has no matching row",
                        targetUserId);
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"success\":false,\"error\":\"Quick Link not found\"}");
                return;
            }

            // Activity log (non-fatal — row is already deleted)
            try {
                Map<String, Object> oldState = new HashMap<>();
                if (targetUserId != null) oldState.put("Target User ID", String.valueOf(targetUserId));
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("component", ActivityLogConstants.COMPONENT_QUICK_LINKS);
                ActivityLogHelper.logActivity(req, ActivityLogConstants.SETTING_APP_SETTINGS,
                        ActivityLogConstants.COMPONENT_QUICK_LINKS,
                        ActivityLogConstants.CHANGE_TYPE_DELETE,
                        oldState, new HashMap<>(), ctx);
            } catch (Exception logEx) {
                log.error("QuickLink DELETE: activity log failed after DB delete (targetUserId={})", targetUserId, logEx);
            }

            log.info("QuickLink DELETE: success rows={} targetUserId={}", rows, targetUserId);
            resp.getWriter().write("{\"success\":true}");

        } catch (SQLException e) {
            log.error("QuickLink DELETE: SQL error targetUserId={}", targetUserId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"success\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean searchExists(Connection conn, int searchId) throws SQLException {
        String sql = "SELECT 1 FROM user_search WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, searchId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Returns the role primaryname for an active, non-deleted user, or null if not found. */
    private String getRoleForUser(Connection conn, int userId) throws SQLException {
        String sql = "SELECT r.primaryname FROM people p " +
                "JOIN role r ON p.System_Role = r.id " +
                "JOIN i_user iu ON iu.reference = p.ID " +
                "WHERE p.ID = ? AND iu.active = 1 AND p.Deleted_date IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("primaryname") : null;
            }
        }
    }

    /** Returns the existing row id for a given targetUserId (null = global), or null if not found. */
    private Integer findExistingRow(Connection conn, Integer targetUserId) throws SQLException {
        String sql = targetUserId == null
                ? "SELECT id FROM user_quick_link WHERE target_user_id IS NULL"
                : "SELECT id FROM user_quick_link WHERE target_user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (targetUserId != null) ps.setInt(1, targetUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : null;
            }
        }
    }

    private boolean isWebUser(String role) {
        if (role == null) return false;
        return "webuser".equals(AppRoleNames.compactRoleKey(role));
    }

    /**
     * Normalizes path matching so servlet works with:
     * - /admin/api/quick-link/{action}
     * - /admin/api/quick-link/{action}/
     * - /admin/api/quick-link (base endpoint)
     */
    private boolean matchesActionPath(String pathInfo, String requestUri, String action, boolean allowBaseEndpoint) {
        String actionPath = "/" + action;
        String normalizedPath = normalizePath(pathInfo);
        String normalizedUri = normalizePath(requestUri);

        if (actionPath.equals(normalizedPath)) return true;
        if (normalizedUri.endsWith(actionPath)) return true;
        if (allowBaseEndpoint) {
            return "/admin/api/quick-link".equals(normalizedPath)
                    || normalizedUri.endsWith("/admin/api/quick-link");
        }
        return false;
    }

    private String normalizePath(String value) {
        if (value == null) return "";
        String v = value.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }
}
