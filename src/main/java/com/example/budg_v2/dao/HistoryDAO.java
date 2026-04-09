package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class HistoryDAO {

    private static final String VISITED_ENTITY_TYPE = "VisitedEntity";

    public long insertVisit(Long userId, String entity, String entityId, String route, String routeParamsJson) throws SQLException {
        String insertHistory = "INSERT INTO history (create_date_time, lastupdateuser_id) VALUES (NOW(), ?)";
        String insertHve = "INSERT INTO history_visited_entity (id, entity, entity_id, route, route_params) VALUES (?,?,?,?,?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(insertHistory, Statement.RETURN_GENERATED_KEYS)) {
                if (userId == null) ps.setNull(1, Types.BIGINT); else ps.setLong(1, userId);
                ps.executeUpdate();
                long id;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No generated key for history");
                    id = rs.getLong(1);
                }

                try (PreparedStatement ps2 = conn.prepareStatement(insertHve)) {
                    ps2.setLong(1, id);
                    ps2.setString(2, entity);
                    ps2.setString(3, entityId);
                    ps2.setString(4, route);
                    if (routeParamsJson == null) ps2.setNull(5, Types.VARCHAR); else ps2.setString(5, routeParamsJson);
                    ps2.executeUpdate();
                }

                upsertMyItem(conn, userId, entity, entityId);

                conn.commit();
                return id;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** Returns recent entities visited by THIS user (isolated), limited by `limit` (1..100). */
    public List<Map<String, Object>> recentForUser(long userId, int limit) throws SQLException {
        String query = """
            WITH
            user_visits AS (
              SELECT
                CAST(hve.entity       AS CHAR) AS entity,
                CAST(hve.entity_id    AS CHAR) AS entity_id,
                CAST(hve.route        AS CHAR) AS route,
                CAST(hve.route_params AS CHAR) AS route_params,
                h.create_date_time             AS visited_time
              FROM history_visited_entity hve
              JOIN history h ON h.id = hve.id
              WHERE h.lastupdateuser_id = ?
            ),
            latest_route AS (
              SELECT
                uv.entity,
                uv.entity_id,
                uv.route,
                uv.route_params,
                ROW_NUMBER() OVER (PARTITION BY uv.entity, uv.entity_id ORDER BY uv.visited_time DESC) AS rn
              FROM user_visits uv
            ),
            unioned AS (
              SELECT uv.entity, uv.entity_id, uv.visited_time
              FROM user_visits uv
              UNION ALL
              SELECT
                CAST(mi.Facet AS CHAR) AS entity,
                CAST(mi.ObjectID AS CHAR) AS entity_id,
                COALESCE(h.create_date_time, mi.Last_UpdateDatetime) AS visited_time
              FROM myitems mi
              LEFT JOIN history_visited_entity ve
                ON CAST(ve.entity AS CHAR)    = CAST(mi.Facet AS CHAR)
               AND CAST(ve.entity_id AS CHAR) = CAST(mi.ObjectID AS CHAR)
              LEFT JOIN history h
                ON h.id = ve.id
               AND h.lastupdateuser_id = mi.VisitedBy_ID
              WHERE mi.VisitedBy_ID = ?
            ),
            visits AS (
              SELECT entity, entity_id, MAX(visited_time) AS last_visited
              FROM unioned
              GROUP BY entity, entity_id
            )
            SELECT
              v.entity,
              v.entity_id,
              lr.route,
              lr.route_params,
              v.last_visited,
              COALESCE(
                (SELECT s.Name FROM `system` s
                  WHERE v.entity='System'        AND CAST(s.id  AS CHAR)=v.entity_id
                    AND (s.Deleted_datetime IS NULL OR s.Deleted_datetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT p.primaryname FROM `process` p
                  WHERE v.entity='Process'       AND CAST(p.id  AS CHAR)=v.entity_id
                    AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT d.primaryname FROM `dataset` d
                  WHERE v.entity='CatalogueItem' AND CAST(d.id  AS CHAR)=v.entity_id
                    AND (d.DeletedDatetime IS NULL OR d.DeletedDatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT g.Name FROM `glossary` g
                  WHERE v.entity='CatItemCategory' AND CAST(g.id AS CHAR)=v.entity_id
                    AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT CONCAT(COALESCE(ip.First_Name,''),' ',COALESCE(ip.Last_Name,'')) FROM `people` ip
                  WHERE v.entity='InvolvedParty' AND CAST(ip.id AS CHAR)=v.entity_id
                    AND (ip.Deleted_date IS NULL OR ip.Deleted_date = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT i.Name FROM `interface` i
                  WHERE v.entity IN ('Interface','SystemInterface','SystemXSystem')
                    AND CAST(i.id AS CHAR)=v.entity_id
                    AND (i.deleted_datetime IS NULL OR i.deleted_datetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT pr.primaryname FROM `product` pr
                  WHERE v.entity='Product'       AND CAST(pr.id AS CHAR)=v.entity_id
                    AND (pr.deleteddatetime IS NULL OR pr.deleteddatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT ou.Name FROM `org_unit` ou
                  WHERE v.entity='OrgUnit'       AND CAST(ou.id AS CHAR)=v.entity_id
                    AND (ou.deleted_Date IS NULL OR ou.deleted_Date = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT cap.primaryname FROM `capability` cap
                  WHERE v.entity='Capability'    AND CAST(cap.id AS CHAR)=v.entity_id
                    AND (cap.DeletedDatetime IS NULL OR cap.DeletedDatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT cl.primaryname FROM `client` cl
                  WHERE v.entity='Client'        AND CAST(cl.id AS CHAR)=v.entity_id
                    AND (cl.DeleteDatetime IS NULL OR cl.DeleteDatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT com.primaryname FROM `committee` com
                  WHERE v.entity='Committee'     AND CAST(com.id AS CHAR)=v.entity_id
                    AND (com.DeleteDatetime IS NULL OR com.DeleteDatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT COALESCE(lg.LongName, lg.ShortName) FROM `legal` lg
                  WHERE v.entity='Legal'         AND CAST(lg.id AS CHAR)=v.entity_id
                    AND (lg.DeleteDatetime IS NULL OR lg.DeleteDatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT pol.primaryname FROM `policy` pol
                  WHERE v.entity='Policy'        AND CAST(pol.id AS CHAR)=v.entity_id
                    AND (pol.DeletedDatetime IS NULL OR pol.DeletedDatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT pj.primaryname FROM `project` pj
                  WHERE v.entity='Project'       AND CAST(pj.id AS CHAR)=v.entity_id
                    AND (pj.deletedatetime IS NULL OR pj.deletedatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT bc.PrimaryName FROM `business_area` bc
                  WHERE v.entity IN ('BusinessConnection', 'BusinessArea') AND CAST(bc.id AS CHAR)=v.entity_id
                    AND (bc.deletedatetime IS NULL OR bc.deletedatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT reg.primaryName FROM `regulation` reg
                  WHERE v.entity='Regulation' AND CAST(reg.id AS CHAR)=v.entity_id
                    AND (reg.DeletedDatetime IS NULL OR reg.DeletedDatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT regt.PrimaryName FROM `regulator` regt
                  WHERE v.entity='Regulator' AND CAST(regt.id AS CHAR)=v.entity_id
                    AND (regt.DeletedDatetime IS NULL OR regt.DeletedDatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT rt.PrimaryName FROM `regulatorytheme` rt
                  WHERE v.entity='RegulatoryTheme' AND CAST(rt.id AS CHAR)=v.entity_id
                    AND (rt.DeletedDatetime IS NULL OR rt.DeletedDatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT geo.PrimaryName FROM `geography` geo
                  WHERE v.entity='Geography' AND CAST(geo.id AS CHAR)=v.entity_id
                    AND (geo.DeletedDatetime IS NULL OR geo.DeletedDatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT attr.PrimaryName FROM `attribute` attr
                  WHERE v.entity='Attribute' AND CAST(attr.id AS CHAR)=v.entity_id LIMIT 1),
                (SELECT COALESCE(le.LongName, le.ShortName) FROM `legal` le
                  WHERE v.entity='LegalEntity' AND CAST(le.id AS CHAR)=v.entity_id
                    AND (le.DeleteDatetime IS NULL OR le.DeleteDatetime = '0000-00-00 00:00:00') LIMIT 1),
                (SELECT r.primaryname FROM `role` r
                  WHERE v.entity='Role' AND CAST(r.id AS CHAR)=v.entity_id LIMIT 1),
                (SELECT cr.PrimaryName FROM `changerequest` cr
                  WHERE v.entity='ChangeRequest' AND CAST(cr.id AS CHAR)=v.entity_id LIMIT 1)
              ) AS display_name
            FROM visits v
            LEFT JOIN latest_route lr
              ON lr.entity = v.entity
             AND lr.entity_id = v.entity_id
             AND lr.rn = 1
             ORDER BY v.last_visited DESC
             LIMIT ?
             """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    String entity = rs.getString("entity");
                    String entityId = rs.getString("entity_id");
                    row.put("entity", entity);
                    row.put("entity_id", entityId);
                    row.put("route", rs.getString("route"));
                    row.put("route_params", rs.getString("route_params"));
                    row.put("last_visited", rs.getTimestamp("last_visited"));

                    String displayName = rs.getString("display_name");
                    if (displayName == null || displayName.trim().isEmpty()) {
                        displayName = tryResolveName(conn, entity, entityId);
                    }
                    // Skip deleted objects (display_name is NULL because the entity
                    // didn't pass the soft-delete check in the COALESCE subqueries)
                    if (displayName == null || displayName.trim().isEmpty()) {
                        continue;
                    }
                    row.put("display_name", displayName);
                    list.add(row);
                }
                return list;
            }
        }
    }

    private void upsertMyItem(Connection conn, Long userId, String entity, String entityId) throws SQLException {
        if (userId == null) return;

        String updateMyItems = "UPDATE myitems SET Last_UpdateDatetime = NOW() WHERE Facet = ? AND ObjectID = ? AND VisitedBy_ID = ?";
        String insertMyItems = "INSERT INTO myitems (Facet, ObjectID, Type, Pinned, Created_Datetime, Last_UpdateDatetime, VisitedBy_ID) VALUES (?, ?, ?, ?, NOW(), NULL, ?)";

        boolean updated;
        try (PreparedStatement update = conn.prepareStatement(updateMyItems)) {
            update.setString(1, entity);
            setObjectIdParameter(update, 2, entityId);
            update.setLong(3, userId);
            updated = update.executeUpdate() > 0;
        }

        if (!updated) {
            try (PreparedStatement insert = conn.prepareStatement(insertMyItems)) {
                insert.setString(1, entity);
                setObjectIdParameter(insert, 2, entityId);
                insert.setString(3, VISITED_ENTITY_TYPE);
                insert.setBoolean(4, false);
                insert.setLong(5, userId);
                insert.executeUpdate();
            }
        }
    }

    private void setObjectIdParameter(PreparedStatement ps, int index, String entityId) throws SQLException {
        if (entityId == null || entityId.isBlank()) ps.setNull(index, Types.VARCHAR);
        else ps.setString(index, entityId);
    }

    private String tryResolveName(Connection conn, String entity, String entityId) {
        Map<String, String[]> map = new HashMap<>();
        map.put("System", new String[]{ "SELECT Name FROM system WHERE id=? AND (Deleted_datetime IS NULL OR Deleted_datetime = '0000-00-00 00:00:00')" });
        map.put("Process", new String[]{ "SELECT primaryname FROM process WHERE id=? AND (deleteddatetime IS NULL OR deleteddatetime = '0000-00-00 00:00:00')"});
        map.put("CatalogueItem", new String[]{ "SELECT PrimaryName FROM dataset WHERE id=? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')"});
        map.put("CatItemCategory", new String[]{ "SELECT Name FROM glossary WHERE id=? AND (Deleted_datetime IS NULL OR Deleted_datetime = '0000-00-00 00:00:00')"});
        map.put("InvolvedParty", new String[]{ "SELECT CONCAT(COALESCE(First_Name, ''), ' ', COALESCE(Last_Name, '')) AS FullName FROM people WHERE id=? AND (Deleted_date IS NULL OR Deleted_date = '0000-00-00 00:00:00')" });
        map.put("Interface", new String[]{ "SELECT Name FROM interface WHERE id=? AND (deleted_datetime IS NULL OR deleted_datetime = '0000-00-00 00:00:00')" });
        map.put("Product", new String[]{ "SELECT primaryname FROM product WHERE id=? AND (deleteddatetime IS NULL OR deleteddatetime = '0000-00-00 00:00:00')" });
        map.put("OrgUnit", new String[]{ "SELECT Name FROM org_unit WHERE id=? AND (deleted_Date IS NULL OR deleted_Date = '0000-00-00 00:00:00')"});
        map.put("Capability", new String[]{ "SELECT PrimaryName FROM capability WHERE id=? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')" });
        map.put("Client", new String[]{ "SELECT PrimaryName FROM client WHERE id=? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')" });
        map.put("Committee", new String[]{ "SELECT PrimaryName FROM committee WHERE id=? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')" });
        map.put("DQMeasure", new String[]{ "SELECT PrimaryName FROM dqmeasure WHERE id=?", "SELECT Name FROM dqmeasure WHERE id=?" });
        map.put("Jurisdiction", new String[]{ "SELECT PrimaryName FROM jurisdiction WHERE id=?", "SELECT Name FROM jurisdiction WHERE id=?" });
        map.put("Legal", new String[]{ "SELECT ShortName FROM legal WHERE id=? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')" });
        map.put("Policy", new String[]{ "SELECT PrimaryName FROM policy WHERE id=? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')" });
        map.put("Project", new String[]{ "SELECT primaryname FROM project WHERE id=? AND (deletedatetime IS NULL OR deletedatetime = '0000-00-00 00:00:00')" });
        map.put("Regulation", new String[]{ "SELECT PrimaryName FROM regulation WHERE id=? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')" });
        map.put("Regulator", new String[]{ "SELECT PrimaryName FROM regulator WHERE id=? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')" });
        map.put("RegulatoryTheme", new String[]{ "SELECT PrimaryName FROM regulatorytheme WHERE id=? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')" });
        map.put("BusinessConnection", new String[]{ "SELECT PrimaryName FROM business_area WHERE id=? AND (deletedatetime IS NULL OR deletedatetime = '0000-00-00 00:00:00')" });
        map.put("BusinessArea", new String[]{ "SELECT PrimaryName FROM business_area WHERE id=? AND (deletedatetime IS NULL OR deletedatetime = '0000-00-00 00:00:00')" });
        map.put("SystemInterface", new String[]{ "SELECT Name FROM interface WHERE id=? AND (deleted_datetime IS NULL OR deleted_datetime = '0000-00-00 00:00:00')" });
        map.put("SystemXSystem", new String[]{ "SELECT Name FROM interface WHERE id=? AND (deleted_datetime IS NULL OR deleted_datetime = '0000-00-00 00:00:00')" });
        map.put("LegalEntity", new String[]{ "SELECT ShortName FROM legal WHERE id=? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')", "SELECT LongName FROM legal WHERE id=? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')" });
        map.put("Role", new String[]{ "SELECT primaryname FROM role WHERE id=?", "SELECT Name FROM role WHERE id=?" });
        map.put("Geography", new String[]{ "SELECT PrimaryName FROM geography WHERE id=? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')" });
        map.put("ChangeRequest", new String[]{ "SELECT PrimaryName FROM changerequest WHERE id=?" });

        String[] attempts = map.get(entity);
        if (attempts == null) return null;
        long id = parseLongSafe(entityId);
        for (String sql : attempts) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String val = rs.getString(1);
                        if (val != null && !val.isBlank()) return val;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private long parseLongSafe(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }
}
