package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Public read-only endpoint for the Quick Link button on the search page.
 *
 * Priority: user-specific row (target_user_id = currentUserId)
 *        → global row (target_user_id IS NULL)
 *        → null (no quick link)
 *
 * Uses INNER JOIN user_search to defensively skip any row whose
 * referenced search no longer exists (ON DELETE CASCADE handles this
 * automatically, but the JOIN guards against any edge case).
 *
 * Response format (unchanged for backward compatibility):
 *   { "definition": "{\"savedSearchId\":\"X\",\"savedSearchName\":\"...\",\"description\":\"...\"}" }
 *   or
 *   { "definition": null }
 */
@WebServlet(name = "QuickLinkPublicServlet", urlPatterns = {"/api/quick-link/current", "/api/search/quick-link"})
public class QuickLinkPublicServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        Integer userId = null;
        Object userIdAttr = req.getAttribute("userId");
        if (userIdAttr instanceof Integer) {
            userId = (Integer) userIdAttr;
        } else if (userIdAttr instanceof Number) {
            userId = ((Number) userIdAttr).intValue();
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            QuickLinkRow row = null;

            // Try user-specific link first
            if (userId != null) {
                row = fetchRow(conn, userId, false);
            }

            // Fall back to global link
            if (row == null) {
                row = fetchRow(conn, null, true);
            }

            JsonObject result = new JsonObject();
            if (row != null) {
                // Build definition JSON string (same format as old app_config approach)
                JsonObject def = new JsonObject();
                def.addProperty("savedSearchId", String.valueOf(row.searchId));
                def.addProperty("savedSearchName", row.searchName != null ? row.searchName : "");
                def.addProperty("description", row.description != null ? row.description : "");
                result.addProperty("definition", def.toString());
            } else {
                result.add("definition", JsonNull.INSTANCE);
            }

            resp.getWriter().write(gson.toJson(result));

        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * Fetches a quick link row joined with user_search (defensive check).
     *
     * @param conn     DB connection
     * @param userId   target_user_id value; ignored when isGlobal=true
     * @param isGlobal if true, queries WHERE target_user_id IS NULL
     */
    private QuickLinkRow fetchRow(Connection conn, Integer userId, boolean isGlobal) throws SQLException {
        String sql = "SELECT uql.search_id, uql.search_name, uql.description " +
                "FROM user_quick_link uql " +
                "INNER JOIN user_search us ON us.id = uql.search_id " +
                (isGlobal
                        ? "WHERE uql.target_user_id IS NULL"
                        : "WHERE uql.target_user_id = ?") +
                " LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!isGlobal) ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    QuickLinkRow row = new QuickLinkRow();
                    row.searchId = rs.getInt("search_id");
                    row.searchName = rs.getString("search_name");
                    row.description = rs.getString("description");
                    return row;
                }
            }
        }
        return null;
    }

    private static class QuickLinkRow {
        int searchId;
        String searchName;
        String description;
    }
}
