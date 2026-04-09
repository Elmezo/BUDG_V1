package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@WebServlet(name = "AdminFacetsServlet", urlPatterns = {"/admin/api/facets"})
public class AdminFacetsServlet extends HttpServlet {

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

        try (Connection conn = DatabaseConnection.getConnection()) {
            List<Map<String, Object>> facets = getFacetsFromModuleTable(conn);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", facets);
            
            resp.getWriter().write(gson.toJson(response));
            
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Database error: " + e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }

    private List<Map<String, Object>> getFacetsFromModuleTable(Connection conn) throws SQLException {
        List<Map<String, Object>> facets = new ArrayList<>();
        
        // Query to get all modules, filtering out empty primary names
        String sql = "SELECT id, primaryname, tablename FROM module WHERE primaryname IS NOT NULL AND primaryname != '' ORDER BY primaryname";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> facet = new HashMap<>();
                String primaryName = rs.getString("primaryname");
                String tableName = rs.getString("tablename");
                
                // Use primary name as ID and display name
                facet.put("id", primaryName);
                facet.put("name", primaryName);
                facet.put("tableName", tableName);
                facet.put("moduleId", rs.getInt("id"));
                
                facets.add(facet);
            }
        }

        return dedupeFacetsForAdminUi(facets);
    }

    /**
     * The {@code module} table can contain duplicate logical facets (e.g. "Org Unit" and "Org. Unit"
     * both backing {@code org_unit}), which would show twice in admin dropdowns. Collapse to one row
     * per physical table or per normalized display name, keeping the lowest module id (seed / canonical).
     */
    private static List<Map<String, Object>> dedupeFacetsForAdminUi(List<Map<String, Object>> raw) {
        Map<String, Map<String, Object>> byTable = new LinkedHashMap<>();
        List<Map<String, Object>> noTable = new ArrayList<>();
        for (Map<String, Object> f : raw) {
            String tableName = f.get("tableName") != null ? String.valueOf(f.get("tableName")) : "";
            if (tableName != null && !tableName.isBlank()) {
                String k = tableName.trim().toLowerCase(Locale.ROOT);
                byTable.merge(k, f, AdminFacetsServlet::pickBetterModuleFacet);
            } else {
                noTable.add(f);
            }
        }

        Set<String> usedNameKeys = new HashSet<>();
        for (Map<String, Object> f : byTable.values()) {
            usedNameKeys.add(normalizeFacetNameKey(String.valueOf(f.get("name"))));
        }

        Map<String, Map<String, Object>> noTableByNorm = new LinkedHashMap<>();
        for (Map<String, Object> f : noTable) {
            String nk = normalizeFacetNameKey(String.valueOf(f.get("name")));
            noTableByNorm.merge(nk, f, AdminFacetsServlet::pickBetterModuleFacet);
        }

        List<Map<String, Object>> out = new ArrayList<>(byTable.values());
        for (Map<String, Object> f : noTableByNorm.values()) {
            String nk = normalizeFacetNameKey(String.valueOf(f.get("name")));
            if (usedNameKeys.contains(nk)) {
                continue;
            }
            out.add(f);
        }

        out.sort(Comparator.comparing(m -> String.valueOf(m.get("name")), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static Map<String, Object> pickBetterModuleFacet(Map<String, Object> a, Map<String, Object> b) {
        int ida = ((Number) a.get("moduleId")).intValue();
        int idb = ((Number) b.get("moduleId")).intValue();
        return ida <= idb ? a : b;
    }

    /** Lowercase alphanumerics only — "Org Unit" and "Org. Unit" both become "orgunit". */
    private static String normalizeFacetNameKey(String primaryName) {
        if (primaryName == null) {
            return "";
        }
        return primaryName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
