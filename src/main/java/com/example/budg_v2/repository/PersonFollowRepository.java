package com.example.budg_v2.repository;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared data access utilities for follow records related to people.
 */
public final class PersonFollowRepository {

    private static final int MODULE_DATASET = 11;
    private static final int MODULE_GLOSSARY = 12;
    private static final int MODULE_SYSTEM = 13;
    private static final int MODULE_INTERFACE = 14;
    private static final int MODULE_CAPABILITY = 16;
    private static final int MODULE_CLIENT = 17;
    private static final int MODULE_LEGAL_ENTITY = 18;
    private static final int MODULE_PRODUCT = 21;
    private static final int MODULE_POLICY = 3;
    private static final int MODULE_PROCESS = 4;
    private static final int MODULE_PROJECT = 5;
    private static final int MODULE_COMMITTEE = 2;
    private static final int MODULE_REGULATION = 23;

    private PersonFollowRepository() {
        // utility class
    }

    private record FollowQuery(String type, int moduleId, String sql) {}

    private static final List<FollowQuery> FOLLOW_QUERIES = Arrays.asList(
            new FollowQuery("Dataset", MODULE_DATASET, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                df.Last_updated_datetime AS link_last_updated,
                df.Last_updatedUser_ID AS link_user_id,
                d.ID AS object_id,
                d.RefNumber AS reference,
                d.PrimaryName AS name,
                d.definition AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN dataset_follow df ON df.follow_id = f.ID
            JOIN dataset d ON df.Dataset_id = d.ID
            LEFT JOIN people link_user ON df.Last_updatedUser_ID = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("Glossary", MODULE_GLOSSARY, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                gf.Last_updated_datetime AS link_last_updated,
                gf.Last_updated_userID AS link_user_id,
                g.ID AS object_id,
                g.Ref_Number AS reference,
                g.Name AS name,
                g.Description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN glossary_follow gf ON gf.follow_id = f.ID
            JOIN glossary g ON gf.Glossary_id = g.ID
            LEFT JOIN people link_user ON gf.Last_updated_userID = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("System", MODULE_SYSTEM, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                sf.Last_updated_datetime AS link_last_updated,
                sf.Last_updatedUser_ID AS link_user_id,
                s.id AS object_id,
                s.AssetID AS reference,
                s.Name AS name,
                s.Description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN system_follow sf ON sf.follow_id = f.ID
            JOIN system s ON sf.System_id = s.id
            LEFT JOIN people link_user ON sf.Last_updatedUser_ID = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("Interface", MODULE_INTERFACE, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                inf.Last_updated_datetime AS link_last_updated,
                inf.Last_updatedUser_ID AS link_user_id,
                i.id AS object_id,
                i.Ref_number AS reference,
                i.Name AS name,
                i.Description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN interface_follow inf ON inf.follow_id = f.ID
            JOIN interface i ON inf.Interface_id = i.id
            LEFT JOIN people link_user ON inf.Last_updatedUser_ID = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("Capability", MODULE_CAPABILITY, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                cf.LastUpdateDatetime AS link_last_updated,
                cf.LastUpdate_UserID AS link_user_id,
                c.ID AS object_id,
                c.RefNumber AS reference,
                c.PrimaryName AS name,
                c.Description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN capability_x_follow cf ON cf.follow_id = f.ID
            JOIN capability c ON cf.Capability_ID = c.ID
            LEFT JOIN people link_user ON cf.LastUpdate_UserID = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("Client", MODULE_CLIENT, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                cf.LastUpdateDatetime AS link_last_updated,
                cf.LastUpdate_UserID AS link_user_id,
                c.ID AS object_id,
                CONCAT('CLIENT-', c.ID) AS reference,
                c.PrimaryName AS name,
                c.Description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN client_x_follow cf ON cf.follow_id = f.ID
            JOIN client c ON cf.Client_ID = c.ID
            LEFT JOIN people link_user ON cf.LastUpdate_UserID = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("Committee", MODULE_COMMITTEE, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                cf.LastUpdateDatetime AS link_last_updated,
                cf.LastUpdate_UserID AS link_user_id,
                c.ID AS object_id,
                c.RefNumber AS reference,
                c.PrimaryName AS name,
                c.Description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN committee_x_follow cf ON cf.Follow_ID = f.ID
            JOIN committee c ON cf.Committee_ID = c.ID
            LEFT JOIN people link_user ON cf.LastUpdate_UserID = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("Legal Entity", MODULE_LEGAL_ENTITY, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                lf.LastUpdateDatetime AS link_last_updated,
                lf.LastUpdate_UserID AS link_user_id,
                l.ID AS object_id,
                l.ShortName AS reference,
                l.LongName AS name,
                l.Description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN legal_x_follow lf ON lf.follow_id = f.ID
            JOIN legal l ON lf.Legal_ID = l.ID
            LEFT JOIN people link_user ON lf.LastUpdate_UserID = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("Policy", MODULE_POLICY, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                pf.LastUpdateDatetime AS link_last_updated,
                pf.LastUpdate_UserID AS link_user_id,
                p.ID AS object_id,
                p.refNumber AS reference,
                p.PrimaryName AS name,
                p.Description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN policy_x_follow pf ON pf.follow_id = f.ID
            JOIN policy p ON pf.Policy_ID = p.ID
            LEFT JOIN people link_user ON pf.LastUpdate_UserID = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("Regulation", MODULE_REGULATION, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                rf.LastUpdateDatetime AS link_last_updated,
                rf.LastUpdate_UserID AS link_user_id,
                r.ID AS object_id,
                r.RefNumber AS reference,
                r.primaryName AS name,
                r.Description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN regulation_x_follow rf ON rf.follow_id = f.ID
            JOIN regulation r ON rf.RegulationID = r.ID
            LEFT JOIN people link_user ON rf.LastUpdate_UserID = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("Process", MODULE_PROCESS, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                pf.lastupdatedatetime AS link_last_updated,
                pf.lastupdate_userid AS link_user_id,
                pr.id AS object_id,
                pr.refnumber AS reference,
                pr.primaryname AS name,
                pr.description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN process_x_follow pf ON pf.follow_id = f.ID
            JOIN process pr ON pf.process_id = pr.id
            LEFT JOIN people link_user ON pf.lastupdate_userid = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("Product", MODULE_PRODUCT, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                pf.lastupdatedatetime AS link_last_updated,
                pf.lastupdate_userid AS link_user_id,
                p.id AS object_id,
                p.refnumber AS reference,
                p.primaryname AS name,
                p.description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN product_x_follow pf ON pf.follow_id = f.ID
            JOIN product p ON pf.product_id = p.id
            LEFT JOIN people link_user ON pf.lastupdate_userid = link_user.ID
            WHERE f.ip_id = ?
        """),
            new FollowQuery("Project", MODULE_PROJECT, """
            SELECT
                f.ID AS follow_id,
                f.With_Children AS include_children,
                f.Description AS additional_info,
                ft.Name AS reason,
                ft.Description AS reason_description,
                f.last_updated_datetime AS follow_last_updated,
                pf.lastupdatedatetime AS link_last_updated,
                pf.lastupdate_userid AS link_user_id,
                p.id AS object_id,
                p.refnumber AS reference,
                p.primaryname AS name,
                p.description AS description,
                link_user.First_Name AS link_first_name,
                link_user.Last_Name AS link_last_name
            FROM follow f
            LEFT JOIN follow_type ft ON f.Follow_type = ft.ID
            JOIN project_x_follow pf ON pf.follow_id = f.ID
            JOIN project p ON pf.project_id = p.id
            LEFT JOIN people link_user ON pf.lastupdate_userid = link_user.ID
            WHERE f.ip_id = ?
        """)
    );

    public static Map<String, Object> getFollowingData(int personId) throws SQLException {
        Map<String, Map<String, Object>> recordMap = new LinkedHashMap<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            for (FollowQuery query : FOLLOW_QUERIES) {
                runFollowQuery(conn, personId, recordMap, query);
            }
        }

        List<Map<String, Object>> records = new ArrayList<>(recordMap.values());

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            String type = (String) record.getOrDefault("type", "Other");
            counts.merge(type, 1, (a, b) -> (a != null ? a : 0) + (b != null ? b : 0));
        }

        List<Map<String, Object>> facets = new ArrayList<>();
        facets.add(buildFacet("All", records.size()));
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> facets.add(buildFacet(entry.getKey(), entry.getValue())));

        Map<String, Object> payload = new HashMap<>();
        payload.put("records", records);
        payload.put("facets", facets);
        payload.put("total", records.size());
        return payload;
    }

    private static void runFollowQuery(Connection conn, int personId, Map<String, Map<String, Object>> recordMap, FollowQuery query) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(query.sql())) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> record = buildFollowRecord(rs, query.type(), query.moduleId());
                    addRecord(recordMap, record);
                }
            }
        }
    }

    private static Map<String, Object> buildFollowRecord(ResultSet rs, String type, int moduleId) throws SQLException {
        Map<String, Object> record = new HashMap<>();
        record.put("followId", rs.getInt("follow_id"));
        record.put("type", type);
        record.put("moduleId", moduleId);
        record.put("reference", safeTrim(rs.getString("reference")));
        record.put("name", safeTrim(rs.getString("name")));
        record.put("description", safeTrim(rs.getString("description")));
        record.put("additionalInfo", safeTrim(rs.getString("additional_info")));
        String reason = safeTrim(rs.getString("reason"));
        if (reason.isEmpty()) {
            reason = safeTrim(rs.getString("reason_description"));
        }
        record.put("reason", reason);
        record.put("includeChildren", rs.getInt("include_children") == 1);
        record.put("lastUpdated", toIso(rs.getTimestamp("link_last_updated")));
        String fullName = buildFullName(rs.getString("link_first_name"), rs.getString("link_last_name"));
        record.put("lastUpdatedBy", fullName.isEmpty() ? null : fullName);
        record.put("objectId", rs.getInt("object_id"));
        record.put("linkUserId", rs.getInt("link_user_id"));
        return record;
    }

    private static void addRecord(Map<String, Map<String, Object>> recordMap, Map<String, Object> record) {
        if (record == null) return;
        Object followId = record.get("followId");
        Object moduleId = record.get("moduleId");
        Object objectId = record.get("objectId");
        String key = followId + ":" + moduleId + ":" + objectId;
        recordMap.putIfAbsent(key, record);
    }

    private static Map<String, Object> buildFacet(String label, int count) {
        Map<String, Object> facet = new HashMap<>();
        facet.put("type", label);
        facet.put("count", count);
        return facet;
    }

    private static String buildFullName(String firstName, String lastName) {
        String first = safeTrim(firstName);
        String last = safeTrim(lastName);
        return (first + " " + last).trim();
    }

    private static String toIso(Timestamp timestamp) {
        if (timestamp == null) return null;
        return timestamp.toInstant().atOffset(ZoneOffset.UTC).toString();
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Removes a following relationship from all follow tables.
     * @param personId The person ID
     * @param followId The follow ID to remove
     * @return true if successful, false otherwise
     * @throws SQLException if database error occurs
     */
    public static boolean removeFollowing(int personId, int followId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First, verify that the follow record belongs to this person
            String verifySql = "SELECT COUNT(*) FROM follow WHERE ID = ? AND ip_id = ?";
            try (PreparedStatement verifyPs = conn.prepareStatement(verifySql)) {
                verifyPs.setInt(1, followId);
                verifyPs.setInt(2, personId);
                try (ResultSet rs = verifyPs.executeQuery()) {
                    if (!rs.next() || rs.getInt(1) == 0) {
                        return false; // Follow record doesn't belong to this person
                    }
                }
            }

            // List of all follow tables that need to be checked
            String[] followTables = {
                    "capability_x_follow", "client_x_follow", "committee_x_follow",
                    "dataset_follow", "glossary_follow", "interface_follow",
                    "legal_x_follow", "policy_x_follow", "process_x_follow",
                    "product_x_follow", "project_x_follow", "system_follow"
            };

            boolean foundAndDeleted = false;

            for (String tableName : followTables) {
                // Check if the follow relationship exists in this table
                String checkSql = "SELECT COUNT(*) FROM " + tableName + " WHERE follow_id = ?";
                try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                    checkPs.setInt(1, followId);
                    try (ResultSet rs = checkPs.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            // Found the relationship, delete it
                            String deleteSql = "DELETE FROM " + tableName + " WHERE follow_id = ?";
                            try (PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {
                                deletePs.setInt(1, followId);
                                int deletedRows = deletePs.executeUpdate();
                                if (deletedRows > 0) {
                                    foundAndDeleted = true;
                                    break; // Found and deleted, no need to check other tables
                                }
                            }
                        }
                    }
                }
            }

            // Also delete the main follow record
            if (foundAndDeleted) {
                String deleteFollowSql = "DELETE FROM follow WHERE ID = ? AND ip_id = ?";
                try (PreparedStatement deleteFollowPs = conn.prepareStatement(deleteFollowSql)) {
                    deleteFollowPs.setInt(1, followId);
                    deleteFollowPs.setInt(2, personId);
                    deleteFollowPs.executeUpdate();
                }
            }

            return foundAndDeleted;
        }
    }
}

