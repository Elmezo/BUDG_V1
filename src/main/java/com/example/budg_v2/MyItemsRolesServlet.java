package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * Returns "My Items > Roles" for the logged-in user.
 * Selects from myitems where Type = 'ObjectXip' (or 'Object_X_IP') and VisitedBy_ID = current user,
 * then resolves a display name by joining to the corresponding facet/entity table.
 */
@WebServlet(name = "MyItemsRolesServlet", urlPatterns = "/api/my-items/roles")
public class MyItemsRolesServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer userId = getCurrentUserId(request);
            if (userId == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                objectMapper.writeValue(response.getWriter(), Map.of("error", "Not authenticated"));
                return;
            }

            List<Map<String, Object>> rows = fetchRolesItems(conn, userId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("items", rows);
            payload.put("count", rows.size());
            objectMapper.writeValue(response.getWriter(), payload);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Database error: " + e.getMessage()));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    private List<Map<String, Object>> fetchRolesItems(Connection conn, int userId) throws SQLException {
        // 1) Try to load items from myitems when Type recorded as ObjectXIP/Object_X_IP
        String sqlMyItems = """
            SELECT
                CAST(mi.Facet AS CHAR)  AS entity,
                CAST(mi.ObjectID AS CHAR) AS entity_id,
                mi.Last_UpdateDatetime  AS last_updated
            FROM myitems mi
            WHERE mi.VisitedBy_ID = ?
              AND (LOWER(mi.Type) = 'objectxip' OR LOWER(mi.Type) = 'object_x_ip')
            ORDER BY mi.Last_UpdateDatetime DESC
        """;

        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sqlMyItems)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String entity = rs.getString("entity");
                    String entityId = rs.getString("entity_id");

                    Map<String, Object> resolved = resolveEntityName(conn, entity, entityId);
                    // Skip items whose entity was deleted or could not be resolved
                    if (resolved == null || resolved.get("name") == null) {
                        continue;
                    }

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("entity", entity);
                    item.put("entity_id", entityId);
                    item.put("last_updated", rs.getTimestamp("last_updated"));
                    item.putAll(resolved);

                    results.add(item);
                }
            }
        }

        // 2) If none found (or to augment), derive from responsibilities for the current user
        //    This aligns My Items > Roles with People view responsibilities
        Map<String, Map<String, Object>> dedup = new LinkedHashMap<>();
        for (Map<String, Object> item : results) {
            String key = String.valueOf(item.get("entity")).toLowerCase() + ":" + String.valueOf(item.get("entity_id"));
            dedup.put(key, item);
        }

        List<Map<String, Object>> derived = fetchResponsibilitiesDerivedItems(conn, userId);
        for (Map<String, Object> item : derived) {
            String key = String.valueOf(item.get("entity")).toLowerCase() + ":" + String.valueOf(item.get("entity_id"));
            dedup.putIfAbsent(key, item);
        }

        return new ArrayList<>(dedup.values());
    }

    private List<Map<String, Object>> fetchResponsibilitiesDerivedItems(Connection conn, int userId) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();

        // Helper lambda to execute query and append results
        class Q {
            void run(String entityKey, String sql, String idCol, String nameCol) throws SQLException {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long id = rs.getLong(idCol);
                            String name = rs.getString(nameCol);
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("entity", entityKey);
                            item.put("entity_id", String.valueOf(id));
                            item.put("name", name);
                            item.put("route", routeForEntity(entityKey, id));
                            list.add(item);
                        }
                    }
                }
            }
        }
        Q q = new Q();

        // Dataset
        q.run("dataset", """
            SELECT d.ID AS id, d.PrimaryName AS name
            FROM object_x_people oxp
            JOIN dataset_x_objectxpeople dx ON dx.Object_x_ipid = oxp.id
            JOIN dataset d ON d.ID = dx.Dataset_ID
            WHERE oxp.ipid = ?
              AND (d.DeletedDatetime IS NULL OR d.DeletedDatetime = '0000-00-00 00:00:00')
        """, "id", "name");

        // System
        q.run("system", """
            SELECT s.id AS id, s.Name AS name
            FROM object_x_people oxp
            JOIN system_x_objectxpeople sx ON sx.Object_x_ipid = oxp.id
            JOIN system s ON s.id = sx.SystemID
            WHERE oxp.ipid = ?
              AND (s.Deleted_datetime IS NULL OR s.Deleted_datetime = '0000-00-00 00:00:00')
        """, "id", "name");

        // System Interface
        q.run("system-interface", """
            SELECT i.id AS id, i.Name AS name
            FROM object_x_people oxp
            JOIN interface_x_objectxpeople ix ON ix.Object_x_ipid = oxp.id
            JOIN interface i ON i.id = ix.InterfaceID
            WHERE oxp.ipid = ?
              AND (i.deleted_datetime IS NULL OR i.deleted_datetime = '0000-00-00 00:00:00')
        """, "id", "name");

        // Glossary
        q.run("glossary", """
            SELECT g.ID AS id, g.Name AS name
            FROM object_x_people oxp
            JOIN glossary_x_objectxpeople gx ON gx.Object_x_ipid = oxp.id
            JOIN glossary g ON g.ID = gx.GlossaryID
            WHERE oxp.ipid = ?
              AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '0000-00-00 00:00:00')
        """, "id", "name");

        // Process
        q.run("process", """
            SELECT p.id AS id, p.primaryname AS name
            FROM object_x_people oxp
            JOIN process_x_objectxpeople px ON px.Object_x_ip = oxp.id
            JOIN process p ON p.id = px.process_id
            WHERE oxp.ipid = ?
              AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '0000-00-00 00:00:00')
        """, "id", "name");

        // Product
        q.run("product", """
            SELECT p.id AS id, p.primaryname AS name
            FROM object_x_people oxp
            JOIN product_x_objectxpeople px ON px.Object_x_ip = oxp.id
            JOIN product p ON p.id = px.product_id
            WHERE oxp.ipid = ?
              AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '0000-00-00 00:00:00')
        """, "id", "name");

        // Business Area
        q.run("business-area", """
            SELECT ba.ID AS id, ba.PrimaryName AS name
            FROM object_x_people oxp
            JOIN businessarea_x_objectxpeople bx ON bx.Object_x_ipid = oxp.id
            JOIN business_area ba ON ba.ID = bx.BusinessAreaID
            WHERE oxp.ipid = ?
              AND (ba.deletedatetime IS NULL OR ba.deletedatetime = '0000-00-00 00:00:00')
        """, "id", "name");

        // Client
        q.run("client", """
            SELECT c.ID AS id, c.PrimaryName AS name
            FROM object_x_people oxp
            JOIN client_x_objectxpeople cx ON cx.Object_x_ipid = oxp.id
            JOIN client c ON c.ID = cx.ClientID
            WHERE oxp.ipid = ?
              AND (c.DeleteDatetime IS NULL OR c.DeleteDatetime = '0000-00-00 00:00:00')
        """, "id", "name");

        // Committee
        q.run("committee", """
            SELECT c.ID AS id, c.PrimaryName AS name
            FROM object_x_people oxp
            JOIN committee_x_objectxpeople cx ON cx.Object_x_ipid = oxp.id
            JOIN committee c ON c.ID = cx.Committee_ID
            WHERE oxp.ipid = ?
              AND (c.DeleteDatetime IS NULL OR c.DeleteDatetime = '0000-00-00 00:00:00')
        """, "id", "name");

        // Legal Entity
        q.run("legal-entity", """
            SELECT l.ID AS id, COALESCE(l.ShortName, l.LongName) AS name
            FROM object_x_people oxp
            JOIN legal_x_objectxpeople lx ON lx.Object_x_ip = oxp.id
            JOIN legal l ON l.ID = lx.Legal_ID
            WHERE oxp.ipid = ?
              AND (l.DeleteDatetime IS NULL OR l.DeleteDatetime = '0000-00-00 00:00:00')
        """, "id", "name");

        // Attribute
        q.run("attribute", """
            SELECT a.ID AS id, a.PrimaryName AS name
            FROM object_x_people oxp
            JOIN attribute_x_objectxpeople ax ON ax.Object_x_ipid = oxp.id
            JOIN attribute a ON a.ID = ax.AttributeID
            WHERE oxp.ipid = ?
        """, "id", "name");

        return list;
    }

    private Map<String, Object> resolveEntityName(Connection conn, String entity, String entityId) {
        if (entity == null || entityId == null || entity.isBlank() || entityId.isBlank()) return null;
        long id;
        try { id = Long.parseLong(entityId); } catch (Exception e) { return null; }

        String table = mapFacetToTable(entity);
        if (table == null) return null;

        String[] candidateSqls = mapFacetToQueries(entity, table);
        for (String sql : candidateSqls) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String name = null;
                        String e = entity.trim();
                        
                        // Extract name based on entity type and available columns
                        if ("Regulation".equals(e)) {
                            name = firstNonEmpty(rs, "primaryName", "ShortName");
                        } else if ("Process".equals(e) || "Product".equals(e) || "Project".equals(e)) {
                            name = firstNonEmpty(rs, "primaryname");
                        } else if ("System".equals(e)) {
                            name = firstNonEmpty(rs, "Name", "Long_Name");
                        } else if ("Glossary".equals(e) || "CatItemCategory".equals(e)) {
                            name = firstNonEmpty(rs, "Name");
                        } else if ("Legal".equals(e)) {
                            name = firstNonEmpty(rs, "ShortName", "LongName");
                        } else if ("InvolvedParty".equals(e)) {
                            String firstName = rs.getString("First_Name");
                            String lastName = rs.getString("Last_Name");
                            if (firstName != null || lastName != null) {
                                name = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
                                name = name.trim();
                            }
                        } else if ("Dataset".equals(e) || "CatalogueItem".equals(e) || "Policy".equals(e) || 
                                   "Capability".equals(e) || "Committee".equals(e) || "Client".equals(e)) {
                            name = firstNonEmpty(rs, "PrimaryName", "Name");
                        } else if ("Interface".equals(e) || "SystemInterface".equals(e) || "SystemXSystem".equals(e)) {
                            name = firstNonEmpty(rs, "Name");
                        } else if ("OrgUnit".equals(e)) {
                            name = firstNonEmpty(rs, "Name");
                        } else if ("BusinessConnection".equals(e)) {
                            name = firstNonEmpty(rs, "Name", "PrimaryName");
                        } else {
                            // Fallback: try all common column names
                            name = firstNonEmpty(rs,
                                    "PrimaryName", "primaryname", "primaryName",
                                    "Name", "name",
                                    "ShortName", "shortname",
                                    "LongName", "longname",
                                    "Title", "title");
                        }

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("name", name);
                        result.put("route", buildRoute(entity, id));
                        return result;
                    }
                }
            } catch (SQLException ignored) {
                // try next
            }
        }
        return Map.of("route", buildRoute(entity, id));
    }

    private String[] mapFacetToQueries(String entity, String table) {
        // Return table-specific queries with correct column names based on schema
        // Each query filters out soft-deleted objects
        String e = entity.trim();
        switch (e) {
            case "Regulation":
                return new String[] {
                    "SELECT ID, primaryName, ShortName FROM " + table + " WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')"
                };
            case "Process":
                return new String[] {
                    "SELECT id, primaryname FROM " + table + " WHERE id = ? AND (deleteddatetime IS NULL OR deleteddatetime = '0000-00-00 00:00:00')"
                };
            case "Product":
                return new String[] {
                    "SELECT id, primaryname FROM " + table + " WHERE id = ? AND (deleteddatetime IS NULL OR deleteddatetime = '0000-00-00 00:00:00')"
                };
            case "Project":
                return new String[] {
                    "SELECT id, primaryname FROM " + table + " WHERE id = ? AND (deletedatetime IS NULL OR deletedatetime = '0000-00-00 00:00:00')"
                };
            case "System":
                return new String[] {
                    "SELECT id, Name, Long_Name FROM " + table + " WHERE id = ? AND (Deleted_datetime IS NULL OR Deleted_datetime = '0000-00-00 00:00:00')"
                };
            case "Glossary":
            case "CatItemCategory":
                return new String[] {
                    "SELECT ID, Name FROM " + table + " WHERE ID = ? AND (Deleted_datetime IS NULL OR Deleted_datetime = '0000-00-00 00:00:00')"
                };
            case "Legal":
                return new String[] {
                    "SELECT ID, ShortName, LongName FROM " + table + " WHERE ID = ? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')"
                };
            case "Dataset":
            case "CatalogueItem":
                return new String[] {
                    "SELECT ID, PrimaryName, Name FROM " + table + " WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')"
                };
            case "Policy":
                return new String[] {
                    "SELECT ID, PrimaryName, Name FROM " + table + " WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')"
                };
            case "Capability":
                return new String[] {
                    "SELECT ID, PrimaryName, Name FROM " + table + " WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')"
                };
            case "Committee":
                return new String[] {
                    "SELECT ID, PrimaryName, Name FROM " + table + " WHERE ID = ? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')"
                };
            case "Client":
                return new String[] {
                    "SELECT ID, PrimaryName, Name FROM " + table + " WHERE ID = ? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')"
                };
            case "InvolvedParty":
                return new String[] {
                    "SELECT ID, First_Name, Last_Name FROM " + table + " WHERE ID = ? AND (Deleted_date IS NULL OR Deleted_date = '0000-00-00 00:00:00')"
                };
            case "Interface":
            case "SystemInterface":
            case "SystemXSystem":
                return new String[] {
                    "SELECT id, Name FROM " + table + " WHERE id = ? AND (deleted_datetime IS NULL OR deleted_datetime = '0000-00-00 00:00:00')"
                };
            case "OrgUnit":
                return new String[] {
                    "SELECT ID, Name FROM " + table + " WHERE ID = ? AND (deleted_Date IS NULL OR deleted_Date = '0000-00-00 00:00:00')"
                };
            case "BusinessConnection":
                return new String[] {
                    "SELECT ID, Name, PrimaryName FROM " + table + " WHERE ID = ? AND (deletedatetime IS NULL OR deletedatetime = '0000-00-00 00:00:00')"
                };
            default:
                // Fallback: try common column names (no deleted filter for unknown tables)
                return new String[] {
                    "SELECT ID, PrimaryName, primaryname, Name, name, ShortName FROM " + table + " WHERE ID = ?"
                };
        }
    }

    private String mapFacetToTable(String entity) {
        String e = entity.trim();
        switch (e) {
            case "System": return "system";
            case "Process": return "process";
            case "CatalogueItem": return "dataset";
            case "CatItemCategory": return "glossary";
            case "InvolvedParty": return "people";
            case "Interface": return "interface";
            case "Product": return "product";
            case "OrgUnit": return "org_unit";
            case "Capability": return "capability";
            case "Client": return "client";
            case "Committee": return "committee";
            case "Legal": return "legal";
            case "Policy": return "policy";
            case "Project": return "project";
            case "Regulation": return "regulation";
            case "BusinessConnection": return "business_area";
            case "SystemInterface": return "interface";
            case "SystemXSystem": return "interface";
            case "Attribute": return "glossary"; // common attribute storage
            default: return null;
        }
    }

    private String firstNonEmpty(ResultSet rs, String... cols) throws SQLException {
        for (String c : cols) {
            try {
                String v = rs.getString(c);
                if (v != null && !v.trim().isEmpty()) return v;
            } catch (SQLException ignored) {}
        }
        return null;
    }

    private String buildRoute(String entity, long id) {
        String type = entity == null ? "" : entity.trim().toLowerCase();
        Map<String, String> map = new HashMap<>();
        map.put("catalogueitem", "dataset");
        map.put("catitemcategory", "glossary");
        map.put("involvedparty", "people");
        map.put("orgunit", "org-unit");
        map.put("businessconnection", "business-area");
        map.put("systeminterface", "system-interface");
        map.put("systemxsystem", "system-interface");
        map.put("legal", "LegalEntity");
        map.put("regulation", "regulation");

        String normalized = map.getOrDefault(type, type);
        // Special cases that use .html routes
        if ("business-area".equals(normalized)) return "/view/business-area/business-area.html?id=" + id;
        if ("capability".equals(normalized))     return "/view/capability/" + id;
        if ("client".equals(normalized))         return "/view/client/client.html?id=" + id;
        if ("LegalEntity".equals(normalized))    return "/view/LegalEntity/" + id;
        return "/view/" + normalized + "/" + id;
    }

    private String routeForEntity(String entityKey, long id) {
        switch (entityKey) {
            case "dataset":          return "/view/dataset/" + id;
            case "system":           return "/view/system/" + id;
            case "system-interface": return "/view/system-interface/" + id;
            case "glossary":         return "/view/glossary/" + id;
            case "process":          return "/view/process/" + id;
            case "product":          return "/view/product/" + id;
            case "business-area":    return "/view/business-area/business-area.html?id=" + id;
            case "client":           return "/view/client/client.html?id=" + id;
            case "committee":        return "/view/committee/" + id;
            case "legal-entity":     return "/view/LegalEntity/" + id;
            case "attribute":        return "/view/attribute/" + id;
            case "org-unit":         return "/view/org-unit/" + id;
            default:                   return "/view/" + entityKey + "/" + id;
        }
    }

    private Integer getCurrentUserId(HttpServletRequest request) {
        try {
            // Prefer session attribute set by auth middleware if available
            Object val = request.getSession(false) != null ? request.getSession(false).getAttribute("userId") : null;
            if (val instanceof Integer) return (Integer) val;
            if (val instanceof Long) return ((Long) val).intValue();

            // Fallback to explicit query parameter for testing
            String p = request.getParameter("userId");
            if (p != null && !p.isBlank()) return Integer.parseInt(p);
        } catch (Exception ignored) {}
        return null;
    }
}


