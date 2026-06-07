package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet for managing follow relationships across all entity types.
 */
@WebServlet("/api/follow/*")
public class FollowServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(FollowServlet.class.getName());
    static {
        // Disable parent handlers to avoid duplicate logs
        LOGGER.setUseParentHandlers(false);

        // Check if handler already exists
        if (LOGGER.getHandlers().length == 0) {
            java.util.logging.ConsoleHandler consoleHandler = new java.util.logging.ConsoleHandler();
            consoleHandler.setLevel(Level.ALL);
            consoleHandler.setFormatter(new java.util.logging.SimpleFormatter());
            LOGGER.addHandler(consoleHandler);
        }

        LOGGER.setLevel(Level.ALL);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Module ID mappings
    private static final Map<String, Integer> MODULE_IDS = new HashMap<>();
    static {
        MODULE_IDS.put("dataset", 11);
        MODULE_IDS.put("glossary", 12);
        MODULE_IDS.put("system", 13);
        MODULE_IDS.put("interface", 14);
        MODULE_IDS.put("capability", 16);
        MODULE_IDS.put("client", 17);
        MODULE_IDS.put("legal-entity", 18);
        MODULE_IDS.put("product", 21);
        MODULE_IDS.put("policy", 3);
        MODULE_IDS.put("process", 4);
        MODULE_IDS.put("project", 5);
        MODULE_IDS.put("committee", 2);
        MODULE_IDS.put("regulation", 23);
    }

    // Follow table mappings
    private static final Map<String, String> FOLLOW_TABLES = new HashMap<>();
    static {
        FOLLOW_TABLES.put("dataset", "dataset_follow");
        FOLLOW_TABLES.put("glossary", "glossary_follow");
        FOLLOW_TABLES.put("system", "system_follow");
        FOLLOW_TABLES.put("interface", "interface_follow");
        FOLLOW_TABLES.put("capability", "capability_x_follow");
        FOLLOW_TABLES.put("client", "client_x_follow");
        FOLLOW_TABLES.put("legal-entity", "legal_x_follow");
        FOLLOW_TABLES.put("product", "product_x_follow");
        FOLLOW_TABLES.put("policy", "policy_x_follow");
        FOLLOW_TABLES.put("process", "process_x_follow");
        FOLLOW_TABLES.put("project", "project_x_follow");
        FOLLOW_TABLES.put("committee", "committee_x_follow");
        FOLLOW_TABLES.put("regulation", "regulation_x_follow");
    }

    private record AuditMetadata(String auditTable, String objectLabel, String entityTable,
                                 String idColumn, String nameExpression) {}

    private static final Map<String, AuditMetadata> AUDIT_METADATA = new HashMap<>();
    static {
        AUDIT_METADATA.put("dataset", new AuditMetadata("dataset_audit_history", "Dataset", "dataset", "ID", "PrimaryName"));
        AUDIT_METADATA.put("glossary", new AuditMetadata("glossary_audit_history", "Glossary", "glossary", "ID", "Name"));
        AUDIT_METADATA.put("system", new AuditMetadata("system_audit_history", "System", "system", "id", "Name"));
        AUDIT_METADATA.put("interface", new AuditMetadata("interface_audit_history", "Interface", "interface", "id", "Name"));
        AUDIT_METADATA.put("capability", new AuditMetadata("capability_audit_history", "Capability", "capability", "ID", "PrimaryName"));
        AUDIT_METADATA.put("client", new AuditMetadata("client_audit_history", "Client", "client", "ID", "PrimaryName"));
        AUDIT_METADATA.put("legal-entity", new AuditMetadata("legal_audit_history", "Legal Entity", "legal", "ID", "COALESCE(LongName, ShortName)"));
        AUDIT_METADATA.put("product", new AuditMetadata("product_audit_history", "Product", "product", "id", "PrimaryName"));
        AUDIT_METADATA.put("policy", new AuditMetadata("policy_audit_history", "Policy", "policy", "ID", "PrimaryName"));
        AUDIT_METADATA.put("process", new AuditMetadata("process_audit_history", "Process", "process", "id", "PrimaryName"));
        AUDIT_METADATA.put("project", new AuditMetadata("project_audit_history", "Project", "project", "id", "PrimaryName"));
        AUDIT_METADATA.put("committee", new AuditMetadata("committee_audit_history", "Committee", "committee", "ID", "PrimaryName"));
        AUDIT_METADATA.put("regulation", new AuditMetadata("regulation_audit_history", "Regulation", "regulation", "ID", "PrimaryName"));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Entity type and ID are required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Parse path: /{entityType}/{entityId}
            String[] pathParts = pathInfo.substring(1).split("/");
            if (pathParts.length < 2) {
                sendError(response, "Both entity type and entity ID are required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String entityType = pathParts[0].toLowerCase();
            int entityId = Integer.parseInt(pathParts[1]);

            // Parse request body
            @SuppressWarnings("unchecked")
            Map<String, Object> requestData = objectMapper.readValue(request.getReader(), Map.class);
            int userId = (Integer) requestData.get("userId");
            int followType = (Integer) requestData.get("followType");
            boolean withChildren = (Boolean) requestData.getOrDefault("withChildren", false);
            String description = (String) requestData.getOrDefault("description", "");

            // Validate entity type
            if (!MODULE_IDS.containsKey(entityType)) {
                sendError(response, "Invalid entity type: " + entityType, HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Create follow relationship
            int followId = createFollowRelationship(userId, entityType, entityId, followType, withChildren, description, request);

            if (followId > 0) {
                response.setStatus(HttpServletResponse.SC_CREATED);
                Map<String, Object> result = Map.of(
                        "success", true,
                        "followId", followId,
                        "message", "Follow relationship created successfully"
                );
                objectMapper.writeValue(response.getWriter(), result);
            } else {
                sendError(response, "Failed to create follow relationship", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

        } catch (NumberFormatException nfe) {
            sendError(response, "Invalid entity ID format", HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(response, "Internal server error: " + ex.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Entity type, entity ID, and user ID are required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Parse path: /{entityType}/{entityId}/{userId}
            String[] pathParts = pathInfo.substring(1).split("/");
            if (pathParts.length < 3) {
                sendError(response, "Entity type, entity ID, and user ID are required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String entityType = pathParts[0].toLowerCase();
            int entityId = Integer.parseInt(pathParts[1]);
            int userId = Integer.parseInt(pathParts[2]);

            // Validate entity type
            if (!MODULE_IDS.containsKey(entityType)) {
                sendError(response, "Invalid entity type: " + entityType, HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Determine whether audit history should also be purged
            boolean purgeAuditHistory = Boolean.parseBoolean(
                    Optional.ofNullable(request.getParameter("purgeAuditHistory")).orElse("false"));

            // Remove follow relationship
            boolean success = removeFollowRelationship(userId, entityType, entityId, request, purgeAuditHistory);

            if (success) {
                response.setStatus(HttpServletResponse.SC_OK);
                Map<String, Object> result = Map.of(
                        "success", true,
                        "message", "Follow relationship removed successfully"
                );
                objectMapper.writeValue(response.getWriter(), result);
            } else {
                sendError(response, "Failed to remove follow relationship or relationship not found", HttpServletResponse.SC_NOT_FOUND);
            }

        } catch (NumberFormatException nfe) {
            sendError(response, "Invalid ID format", HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(response, "Internal server error: " + ex.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Entity type and ID are required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Parse path: /{entityType}/{entityId} or /{entityType}/{entityId}/{userId}
            String[] pathParts = pathInfo.substring(1).split("/");
            if (pathParts.length < 2) {
                sendError(response, "Entity type and entity ID are required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String entityType = pathParts[0].toLowerCase();
            int entityId = Integer.parseInt(pathParts[1]);

            // Validate entity type
            if (!MODULE_IDS.containsKey(entityType)) {
                sendError(response, "Invalid entity type: " + entityType, HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // If 3 parts: check if specific user follows the entity
            if (pathParts.length >= 3) {
                int userId = Integer.parseInt(pathParts[2]);
                Map<String, Object> followData = getFollowRelationship(userId, entityType, entityId);
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), followData);
            } else {
                // If 2 parts: get all followers for the entity
                Map<String, Object> followersData = getAllFollowersForEntity(entityType, entityId);
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), followersData);
            }

        } catch (NumberFormatException nfe) {
            sendError(response, "Invalid ID format", HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(response, "Internal server error: " + ex.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Entity type and ID are required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Parse path: /{entityType}/{entityId}
            String[] pathParts = pathInfo.substring(1).split("/");
            if (pathParts.length < 2) {
                sendError(response, "Both entity type and entity ID are required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String entityType = pathParts[0].toLowerCase();
            int entityId = Integer.parseInt(pathParts[1]);

            // Parse request body
            @SuppressWarnings("unchecked")
            Map<String, Object> requestData = objectMapper.readValue(request.getReader(), Map.class);
            Integer userId = (Integer) requestData.get("userId");
            Integer followType = requestData.containsKey("followType") ? (Integer) requestData.get("followType") : null;
            Boolean withChildren = requestData.containsKey("withChildren") ? (Boolean) requestData.get("withChildren") : null;
            String description = requestData.containsKey("description") ? (String) requestData.get("description") : null;

            if (userId == null) {
                sendError(response, "userId is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Validate entity type
            if (!MODULE_IDS.containsKey(entityType)) {
                sendError(response, "Invalid entity type: " + entityType, HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            boolean updated = updateFollowRelationship(userId, entityType, entityId, followType, withChildren, description, request);

            if (updated) {
                response.setStatus(HttpServletResponse.SC_OK);
                Map<String, Object> result = Map.of(
                        "success", true,
                        "message", "Follow relationship updated successfully"
                );
                objectMapper.writeValue(response.getWriter(), result);
            } else {
                sendError(response, "Follow relationship not found or no fields to update", HttpServletResponse.SC_NOT_FOUND);
            }

        } catch (NumberFormatException nfe) {
            sendError(response, "Invalid entity ID format", HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(response, "Internal server error: " + ex.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private int createFollowRelationship(int userId, String entityType, int entityId, int followType, boolean withChildren, String description, HttpServletRequest request) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Insert into main follow table
                String followSql = """
                    INSERT INTO follow (ip_id, Follow_type, With_Children, Description, Created_datetime, last_updated_datetime, Module_id, objectID, last_updated_userID)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

                LocalDateTime now = LocalDateTime.now();
                int moduleId = MODULE_IDS.get(entityType);

                try (PreparedStatement followPs = conn.prepareStatement(followSql, Statement.RETURN_GENERATED_KEYS)) {
                    followPs.setInt(1, userId);
                    followPs.setInt(2, followType);
                    followPs.setBoolean(3, withChildren);
                    followPs.setString(4, description);
                    followPs.setTimestamp(5, Timestamp.valueOf(now));
                    followPs.setTimestamp(6, Timestamp.valueOf(now));
                    followPs.setInt(7, moduleId);
                    followPs.setInt(8, entityId);
                    followPs.setInt(9, userId);

                    int rowsAffected = followPs.executeUpdate();
                    if (rowsAffected == 0) {
                        return 0;
                    }

                    try (ResultSet generatedKeys = followPs.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            int followId = generatedKeys.getInt(1);

                            // Insert into specific follow table
                            String specificFollowTable = FOLLOW_TABLES.get(entityType);
                            String specificFollowSql = getSpecificFollowInsertSql(entityType, specificFollowTable);

                            try (PreparedStatement specificPs = conn.prepareStatement(specificFollowSql)) {
                                setSpecificFollowParameters(specificPs, entityType, entityId, followId, userId, now);
                                specificPs.executeUpdate();
                            }

                            // Insert audit record for the new follow
                            String auditSql = """
                                INSERT INTO follow_audit (
                                    ID, ip_id, Follow_type, With_Children, Description, Created_datetetime, 
                                    last_updated_datetime, Module_id, objectID, last_updated_userID, rev_type
                                )
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Insert')
                            """;

                            try (PreparedStatement auditPs = conn.prepareStatement(auditSql)) {
                                auditPs.setInt(1, followId);
                                auditPs.setInt(2, userId);
                                auditPs.setInt(3, followType);
                                auditPs.setBoolean(4, withChildren);
                                auditPs.setString(5, description);
                                auditPs.setTimestamp(6, Timestamp.valueOf(now));
                                auditPs.setTimestamp(7, Timestamp.valueOf(now));
                                auditPs.setInt(8, moduleId);
                                auditPs.setInt(9, entityId);
                                auditPs.setInt(10, userId);
                                auditPs.executeUpdate();
                            }

                            String author = resolveAuthorName(request, userId);
                            String followerName = getUserFullName(userId);
                            logFollowInsertAudit(conn, entityType, entityId, followType, description, author, followerName);

                            conn.commit();
                            return followId;
                        }
                    }
                }

                return 0;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    private boolean removeFollowRelationship(int userId, String entityType, int entityId,
                                             HttpServletRequest request, boolean purgeAuditHistory) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Find the follow record
                String findFollowSql = """
                SELECT f.ID, f.Follow_type, f.Description FROM follow f
                JOIN %s sf ON sf.follow_id = f.ID
                WHERE f.ip_id = ? AND f.Module_id = ? AND f.objectID = ?
            """;

                String specificFollowTable = FOLLOW_TABLES.get(entityType);
                int moduleId = MODULE_IDS.get(entityType);

                String findSql = String.format(findFollowSql, specificFollowTable);

                try (PreparedStatement findPs = conn.prepareStatement(findSql)) {
                    findPs.setInt(1, userId);
                    findPs.setInt(2, moduleId);
                    findPs.setInt(3, entityId);

                    try (ResultSet rs = findPs.executeQuery()) {
                        if (rs.next()) {
                            int followId = rs.getInt("ID");

                            // Create audit snapshot before deletion in follow_audit table
                            String auditSql = """
                            INSERT INTO follow_audit (
                                ID, ip_id, Follow_type, With_Children, Description, Created_datetetime, 
                                last_updated_datetime, Module_id, objectID, last_updated_userID, rev_type
                            )
                            SELECT 
                                ID, ip_id, Follow_type, With_Children, Description, Created_datetime, 
                                last_updated_datetime, Module_id, objectID, last_updated_userID, 'Deleted'
                            FROM follow 
                            WHERE ID = ?
                        """;

                            try (PreparedStatement auditPs = conn.prepareStatement(auditSql)) {
                                auditPs.setInt(1, followId);
                                auditPs.executeUpdate();
                            }

                            String followerName = getUserFullName(userId);

                            // Delete from specific follow table
                            String deleteSpecificSql = "DELETE FROM " + specificFollowTable + " WHERE follow_id = ?";
                            try (PreparedStatement deleteSpecificPs = conn.prepareStatement(deleteSpecificSql)) {
                                deleteSpecificPs.setInt(1, followId);
                                deleteSpecificPs.executeUpdate();
                            }

                            if (purgeAuditHistory) {
                                // HARD DELETE from audit_history for this specific follow.
                                // Pass the same author resolution used at insert so the
                                // DELETE matches the rows actually written for this user.
                                String author = resolveAuthorName(request, userId);
                                deleteFollowAuditHistory(conn, entityType, entityId, followerName, author);
                            }

                            // Delete from main follow table
                            String deleteFollowSql = "DELETE FROM follow WHERE ID = ? AND ip_id = ?";
                            try (PreparedStatement deleteFollowPs = conn.prepareStatement(deleteFollowSql)) {
                                deleteFollowPs.setInt(1, followId);
                                deleteFollowPs.setInt(2, userId);
                                deleteFollowPs.executeUpdate();
                            }

                            conn.commit();
                            return true;
                        }
                    }
                }

                return false;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    private void deleteFollowAuditHistory(Connection conn, String entityType, int entityId,
                                          String followerName, String author) throws SQLException {
        AuditMetadata metadata = getAuditMetadata(entityType);
        if (metadata == null) {
            return;
        }

        // Match by event='Follow' (covers both the spec-compliant Object='Follow'
        // rows we now write and any historical rows that recorded the facet label
        // as Object). Author + the "Name" row's `to` value together identify the
        // exact rows inserted for this follower's session.
        String sql = "DELETE FROM " + metadata.auditTable +
                " WHERE id = ? AND event = 'Follow' AND author = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, entityId);
            ps.setString(2, author != null ? author : followerName);
            int deletedRows = ps.executeUpdate();
            LOGGER.fine(() -> String.format("Deleted %d audit history rows for follower %s on entity %d",
                    deletedRows, followerName, entityId));
        }
    }

    private String getCurrentUserName(HttpServletRequest request) {
        try {
            // Try to get from request attributes first
            if (request != null) {
                Object userAttr = request.getAttribute("userName");
                if (userAttr != null) {
                    return userAttr.toString();
                }

                // Try to get from userId
                Integer userId = (Integer) request.getAttribute("userId");
                if (userId != null) {
                    return getUserFullName(userId);
                }
            }

            return "System";
        } catch (Exception e) {
            System.err.println("Error getting current user name: " + e.getMessage());
            return "System";
        }
    }

    private String resolveAuthorName(HttpServletRequest request, int userId) {
        String current = getCurrentUserName(request);
        if (current == null || current.isBlank() || "System".equalsIgnoreCase(current)) {
            return getUserFullName(userId);
        }
        return current;
    }

    /**
     * Gets user full name by ID from database.
     */
    private String getUserFullName(int userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("fullName");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user full name: " + e.getMessage());
        }
        return "User " + userId;
    }

    private Map<String, Object> getFollowRelationship(int userId, String entityType, int entityId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Try direct query to follow table first (more reliable)
            String directFindSql = "SELECT ID, Follow_type, With_Children, Description, Created_datetime, last_updated_datetime FROM follow WHERE ip_id = ? AND Module_id = ? AND objectID = ?";

            int moduleId = MODULE_IDS.get(entityType);

            try (PreparedStatement findPs = conn.prepareStatement(directFindSql)) {
                findPs.setInt(1, userId);
                findPs.setInt(2, moduleId);
                findPs.setInt(3, entityId);

                try (ResultSet rs = findPs.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("exists", true);
                        result.put("followId", rs.getInt("ID"));
                        result.put("followType", rs.getInt("Follow_type"));
                        result.put("withChildren", rs.getBoolean("With_Children"));
                        result.put("description", rs.getString("Description"));
                        result.put("createdDatetime", rs.getTimestamp("Created_datetime"));
                        result.put("lastUpdatedDatetime", rs.getTimestamp("last_updated_datetime"));
                        result.put("followedDirectly", true);
                        result.put("followedThroughParent", false);
                        return result;
                    }
                }
            }

            // If not followed directly, check if followed through a parent with With_Children = 1
            List<Integer> ancestorIds = getAncestorIds(conn, entityType, entityId);
            if (!ancestorIds.isEmpty()) {
                String parentFollowSql = "SELECT ID, Follow_type, With_Children, Description, Created_datetime, last_updated_datetime, objectID FROM follow WHERE ip_id = ? AND Module_id = ? AND objectID IN (" + 
                    ancestorIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(",")) + 
                    ") AND With_Children = 1";
                
                try (PreparedStatement parentPs = conn.prepareStatement(parentFollowSql)) {
                    parentPs.setInt(1, userId);
                    parentPs.setInt(2, moduleId);
                    int paramIndex = 3;
                    for (Integer ancestorId : ancestorIds) {
                        parentPs.setInt(paramIndex++, ancestorId);
                    }
                    
                    try (ResultSet rs = parentPs.executeQuery()) {
                        if (rs.next()) {
                            Map<String, Object> result = new HashMap<>();
                            result.put("exists", true);
                            result.put("followId", rs.getInt("ID"));
                            result.put("followType", rs.getInt("Follow_type"));
                            result.put("withChildren", rs.getBoolean("With_Children"));
                            result.put("description", rs.getString("Description"));
                            result.put("createdDatetime", rs.getTimestamp("Created_datetime"));
                            result.put("lastUpdatedDatetime", rs.getTimestamp("last_updated_datetime"));
                            result.put("followedDirectly", false);
                            result.put("followedThroughParent", true);
                            result.put("parentFollowId", rs.getInt("objectID"));
                            return result;
                        }
                    }
                }
            }

            return Map.of("exists", false);
        }
    }

    /**
     * Get all followers for an entity
     */
    private Map<String, Object> getAllFollowersForEntity(String entityType, int entityId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            int moduleId = MODULE_IDS.get(entityType);
            String specificFollowTable = FOLLOW_TABLES.get(entityType);
            
            // Build the SQL query based on entity type
            String sql = buildGetAllFollowersSql(entityType, specificFollowTable);
            
            List<Map<String, Object>> followers = new ArrayList<>();
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, moduleId);
                ps.setInt(2, entityId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> follower = new HashMap<>();
                        follower.put("personId", rs.getInt("person_id"));
                        follower.put("name", rs.getString("name"));
                        follower.put("orgUnit", rs.getString("org_unit"));
                        follower.put("function", rs.getString("function"));
                        Timestamp followingSince = rs.getTimestamp("following_since");
                        follower.put("followingSince", followingSince != null ? followingSince.toString() : null);
                        followers.add(follower);
                    }
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("followers", followers);
            return result;
        }
    }

    /**
     * Build SQL query to get all followers for an entity type
     */
    private String buildGetAllFollowersSql(String entityType, String specificFollowTable) {
        // Get the entity ID column name for the specific follow table
        String entityIdColumn = getEntityIdColumnForFollowTable(entityType);
        
        return String.format(
            "SELECT " +
                "p.ID AS person_id, " +
                "CONCAT(p.First_Name, ' ', p.Last_Name) AS name, " +
                "COALESCE(ou.Name, '') AS org_unit, " +
                "COALESCE(p.Function_Name, '') AS function, " +
                "f.Created_datetime AS following_since " +
            "FROM follow f " +
            "JOIN %s sf ON sf.follow_id = f.ID " +
            "JOIN people p ON f.ip_id = p.ID " +
            "LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID " +
            "WHERE f.Module_id = ? AND sf.%s = ? " +
            "ORDER BY f.Created_datetime DESC",
            specificFollowTable, entityIdColumn);
    }

    /**
     * Get the entity ID column name for a specific follow table
     */
    private String getEntityIdColumnForFollowTable(String entityType) {
        switch (entityType) {
            case "dataset":
                return "Dataset_id";
            case "glossary":
                return "Glossary_id";
            case "system":
                return "System_id";
            case "interface":
                return "Interface_id";
            case "capability":
                return "Capability_ID";
            case "client":
                return "Client_ID";
            case "legal-entity":
                return "Legal_ID";
            case "product":
                return "product_id";
            case "policy":
                return "Policy_ID";
            case "process":
                return "process_id";
            case "project":
                return "project_id";
            case "committee":
                return "Committee_ID";
            case "regulation":
                return "RegulationID";
            default:
                throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }
    }

    /**
     * Get all ancestor IDs (parents, grandparents, etc.) for an entity
     */
    private List<Integer> getAncestorIds(Connection conn, String entityType, int entityId) throws SQLException {
        List<Integer> ancestorIds = new ArrayList<>();
        String parentColumn = getParentColumnName(entityType);
        String tableName = getTableName(entityType);
        String idColumn = getIdColumnName(entityType);
        
        if (parentColumn == null || tableName == null) {
            return ancestorIds; // Entity type doesn't support hierarchy
        }

        // Use recursive CTE to get all ancestors
        String sql = "WITH RECURSIVE ancestors AS (" +
            "    SELECT " + parentColumn + " as parent_id " +
            "    FROM " + tableName + " " +
            "    WHERE " + idColumn + " = ? AND " + parentColumn + " IS NOT NULL " +
            "    UNION ALL " +
            "    SELECT e." + parentColumn + " " +
            "    FROM " + tableName + " e " +
            "    INNER JOIN ancestors a ON e." + idColumn + " = a.parent_id " +
            "    WHERE e." + parentColumn + " IS NOT NULL " +
            ") " +
            "SELECT DISTINCT parent_id FROM ancestors WHERE parent_id IS NOT NULL";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ancestorIds.add(rs.getInt("parent_id"));
                }
            }
        } catch (SQLException e) {
            // If recursive CTE fails (e.g., MySQL < 8.0), fall back to iterative approach
            return getAncestorIdsIterative(conn, entityType, entityId);
        }

        return ancestorIds;
    }

    /**
     * Fallback method to get ancestors iteratively (for databases that don't support recursive CTE)
     */
    private List<Integer> getAncestorIdsIterative(Connection conn, String entityType, int entityId) throws SQLException {
        List<Integer> ancestorIds = new ArrayList<>();
        String parentColumn = getParentColumnName(entityType);
        String tableName = getTableName(entityType);
        String idColumn = getIdColumnName(entityType);
        
        if (parentColumn == null || tableName == null) {
            return ancestorIds;
        }

        int currentId = entityId;
        Set<Integer> visited = new HashSet<>();
        int maxDepth = 20; // Prevent infinite loops
        int depth = 0;

        while (depth < maxDepth && !visited.contains(currentId)) {
            visited.add(currentId);
            String sql = "SELECT " + parentColumn + " FROM " + tableName + " WHERE " + idColumn + " = ?";
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, currentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Integer parentId = rs.getObject(parentColumn, Integer.class);
                        if (parentId != null && parentId > 0) {
                            ancestorIds.add(parentId);
                            currentId = parentId;
                            depth++;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }

        return ancestorIds;
    }

    /**
     * Get the parent column name for an entity type
     */
    private String getParentColumnName(String entityType) {
        switch (entityType) {
            case "glossary":
                return "Parent_ID";
            case "system":
                return "parent_id";
            case "policy":
                return "ParentID";
            case "project":
                return "parentid";
            case "process":
                return "parentid";
            case "product":
                return "parent_id";
            case "legal-entity":
                return "Parent_ID";
            case "capability":
                return "Parent_ID";
            case "client":
                return "Parent_ID";
            case "committee":
                return "Parent_ID";
            case "regulation":
                return "Parent_ID";
            default:
                return null;
        }
    }

    /**
     * Get the table name for an entity type
     */
    private String getTableName(String entityType) {
        switch (entityType) {
            case "glossary":
                return "glossary";
            case "system":
                return "system";
            case "policy":
                return "policy";
            case "project":
                return "project";
            case "process":
                return "process";
            case "product":
                return "product";
            case "legal-entity":
                return "legal";
            case "capability":
                return "capability";
            case "client":
                return "client";
            case "committee":
                return "committee";
            case "regulation":
                return "regulation";
            default:
                return null;
        }
    }

    /**
     * Get the ID column name for an entity type
     */
    private String getIdColumnName(String entityType) {
        switch (entityType) {
            case "glossary":
                return "ID";
            case "system":
                return "id";
            case "policy":
                return "ID";
            case "project":
                return "id";
            case "process":
                return "id";
            case "product":
                return "id";
            case "legal-entity":
                return "ID";
            case "capability":
                return "ID";
            case "client":
                return "ID";
            case "committee":
                return "ID";
            case "regulation":
                return "ID";
            default:
                return "ID";
        }
    }

    private String getSpecificFollowInsertSql(String entityType, String tableName) {
        switch (entityType) {
            case "dataset":
                return "INSERT INTO " + tableName + " (Dataset_id, follow_id, Created_datetime, Last_updated_datetime, Last_updatedUser_ID) VALUES (?, ?, ?, ?, ?)";
            case "glossary":
                return "INSERT INTO " + tableName + " (Glossary_id, follow_id, Created_Datetime, Last_updated_datetime, Last_updated_userID) VALUES (?, ?, ?, ?, ?)";
            case "system":
                return "INSERT INTO " + tableName + " (System_id, follow_id, Created_datetime, Last_updated_datetime, Last_updatedUser_ID) VALUES (?, ?, ?, ?, ?)";
            case "interface":
                return "INSERT INTO " + tableName + " (Interface_id, follow_id, Created_datetime, Last_updated_datetime, Last_updatedUser_ID) VALUES (?, ?, ?, ?, ?)";
            case "capability":
                return "INSERT INTO " + tableName + " (Capability_ID, follow_id, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) VALUES (?, ?, ?, ?, ?)";
            case "client":
                return "INSERT INTO " + tableName + " (Client_ID, follow_id, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) VALUES (?, ?, ?, ?, ?)";
            case "legal-entity":
                return "INSERT INTO " + tableName + " (Legal_ID, follow_id, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) VALUES (?, ?, ?, ?, ?)";
            case "product":
                return "INSERT INTO " + tableName + " (product_id, follow_id, createdatetime, lastupdatedatetime, lastupdate_userid) VALUES (?, ?, ?, ?, ?)";
            case "policy":
                return "INSERT INTO " + tableName + " (Policy_ID, follow_id, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) VALUES (?, ?, ?, ?, ?)";
            case "process":
                return "INSERT INTO " + tableName + " (process_id, follow_id, createdatetime, lastupdatedatetime, lastupdate_userid) VALUES (?, ?, ?, ?, ?)";
            case "project":
                return "INSERT INTO " + tableName + " (project_id, follow_id, createdatetime, lastupdatedatetime, lastupdate_userid) VALUES (?, ?, ?, ?, ?)";
            case "committee":
                return "INSERT INTO " + tableName + " (Committee_ID, Follow_ID, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) VALUES (?, ?, ?, ?, ?)";
            case "regulation":
                return "INSERT INTO " + tableName + " (RegulationID, follow_id, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) VALUES (?, ?, ?, ?, ?)";
            default:
                throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }
    }

    private void setSpecificFollowParameters(PreparedStatement ps, String entityType, int entityId, int followId, int userId, LocalDateTime now) throws SQLException {
        switch (entityType) {
            case "dataset":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "glossary":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "system":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "interface":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "capability":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "client":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "legal-entity":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "product":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "policy":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "process":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "project":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "committee":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            case "regulation":
                ps.setInt(1, entityId);
                ps.setInt(2, followId);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.setInt(5, userId);
                break;
            default:
                throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }
    }

    private boolean updateFollowRelationship(int userId, String entityType, int entityId, Integer followType, Boolean withChildren, String description, HttpServletRequest request) throws SQLException {
        if (followType == null && withChildren == null && description == null) {
            LOGGER.fine(String.format("Skipping update: no mutable fields provided (userId=%d, entityType=%s, entityId=%d)", userId, entityType, entityId));
            return false; // nothing to update
        }

        LOGGER.info(String.format(
                "Update request received [userId=%d, entityType=%s, entityId=%d, followType=%s, withChildren=%s, description=%s]",
                userId, entityType, entityId, String.valueOf(followType), String.valueOf(withChildren), description));

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                int moduleId = MODULE_IDS.get(entityType);

                // Locate existing follow record
                String findSql = "SELECT ID, Follow_type, Description FROM follow WHERE ip_id = ? AND Module_id = ? AND objectID = ?";
                Integer followId = null;
                Integer existingFollowType = null;
                String existingDescription = null;
                try (PreparedStatement findPs = conn.prepareStatement(findSql)) {
                    findPs.setInt(1, userId);
                    findPs.setInt(2, moduleId);
                    findPs.setInt(3, entityId);
                    try (ResultSet rs = findPs.executeQuery()) {
                        if (rs.next()) {
                            followId = rs.getInt("ID");
                            existingFollowType = (Integer) rs.getObject("Follow_type");
                            existingDescription = rs.getString("Description");
                        }
                    }
                }

                if (followId == null) {
                    LOGGER.warning(String.format(
                            "No follow record found for update [userId=%d, entityType=%s, entityId=%d]. Attempting to insert new follow row.",
                            userId, entityType, entityId));
                    conn.rollback();

                    if (followType == null) {
                        LOGGER.warning(String.format(
                                "Cannot insert new follow row during update because followType is null [userId=%d, entityType=%s, entityId=%d]",
                                userId, entityType, entityId));
                        return false;
                    }

                    boolean newWithChildren = withChildren != null && withChildren;
                    String newDescription = description != null ? description : "";
                    int newFollowId = createFollowRelationship(userId, entityType, entityId, followType, newWithChildren, newDescription, request);

                    if (newFollowId > 0) {
                        LOGGER.info(String.format(
                                "Inserted new follow row during update fallback [newFollowId=%d, userId=%d, entityType=%s, entityId=%d]",
                                newFollowId, userId, entityType, entityId));
                        return true;
                    } else {
                        LOGGER.severe(String.format(
                                "Failed to insert new follow row during update fallback [userId=%d, entityType=%s, entityId=%d]",
                                userId, entityType, entityId));
                        return false;
                    }
                }
                LOGGER.fine(String.format(
                        "Located follow record [followId=%d, existingFollowType=%s, existingDescription=%s]",
                        followId, String.valueOf(existingFollowType), existingDescription));

                // Build dynamic update
                StringBuilder sql = new StringBuilder("UPDATE follow SET ");
                boolean first = true;
                if (followType != null) {
                    sql.append("Follow_type = ?");
                    first = false;
                }
                if (withChildren != null) {
                    if (!first) sql.append(", ");
                    sql.append("With_Children = ?");
                    first = false;
                }
                if (description != null) {
                    if (!first) sql.append(", ");
                    sql.append("Description = ?");
                    first = false;
                }
                if (!first) sql.append(", ");
                sql.append("last_updated_datetime = ?, last_updated_userID = ? WHERE ID = ?");
                String updateSqlPreview = sql.toString();
                LOGGER.fine("Executing follow update SQL: " + updateSqlPreview);

                LocalDateTime now = LocalDateTime.now();

                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    int idx = 1;
                    if (followType != null) {
                        ps.setInt(idx++, followType);
                    }
                    if (withChildren != null) {
                        ps.setBoolean(idx++, withChildren);
                    }
                    if (description != null) {
                        ps.setString(idx++, description);
                    }
                    ps.setTimestamp(idx++, Timestamp.valueOf(now));
                    ps.setInt(idx++, userId);
                    ps.setInt(idx, followId);
                    int affected = ps.executeUpdate();
                    LOGGER.fine(String.format("Follow update SQL affected %d row(s) [followId=%d]", affected, followId));
                }

                Integer latestFollowType = existingFollowType;
                String latestDescription = existingDescription;
                String refreshSql = "SELECT Follow_type, Description FROM follow WHERE ID = ?";
                try (PreparedStatement refreshPs = conn.prepareStatement(refreshSql)) {
                    refreshPs.setInt(1, followId);
                    try (ResultSet refreshRs = refreshPs.executeQuery()) {
                        if (refreshRs.next()) {
                            latestFollowType = (Integer) refreshRs.getObject("Follow_type");
                            latestDescription = refreshRs.getString("Description");
                        }
                    }
                }

                String author = resolveAuthorName(request, userId);
                LOGGER.fine(String.format(
                        "Post-update values [followId=%d, followType=%s, description=%s]",
                        followId, String.valueOf(latestFollowType), latestDescription));

                logFollowUpdateAudit(conn, entityType, entityId, existingFollowType, latestFollowType, existingDescription, latestDescription, author);
                LOGGER.fine(String.format("Audit log updated for followId=%d (author=%s)", followId, author));

                // Audit snapshot after update
                String auditSql = """
                    INSERT INTO follow_audit (
                        ID, ip_id, Follow_type, With_Children, Description, Created_datetetime,
                        last_updated_datetime, Module_id, objectID, last_updated_userID, rev_type
                    )
                    SELECT 
                        ID, ip_id, Follow_type, With_Children, Description, Created_datetime,
                        last_updated_datetime, Module_id, objectID, last_updated_userID, 'Updated'
                    FROM follow 
                    WHERE ID = ?
                """;

                try (PreparedStatement auditPs = conn.prepareStatement(auditSql)) {
                    auditPs.setInt(1, followId);
                    auditPs.executeUpdate();
                }

                LOGGER.info(String.format("Follow relationship updated successfully [followId=%d, userId=%d, entityType=%s, entityId=%d]",
                        followId, userId, entityType, entityId));
                conn.commit();
                return true;
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, String.format("Error while updating follow relationship [userId=%d, entityType=%s, entityId=%d]: %s",
                        userId, entityType, entityId, e.getMessage()), e);
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private AuditMetadata getAuditMetadata(String entityType) {
        return AUDIT_METADATA.get(entityType);
    }

    private void logFollowInsertAudit(Connection conn, String entityType, int entityId, int followType,
                                      String description, String author, String followerName) throws SQLException {
        AuditMetadata metadata = getAuditMetadata(entityType);
        if (metadata == null) {
            return;
        }

        String followTypeName = resolveFollowTypeName(conn, followType);

        // Per the audit spec the Object column is "Follow" for follow events; the
        // facet identifier is implicit because the rows are written to the facet's
        // audit history table.
        String object = "Follow";

        // Follow Type
        insertFollowAuditRow(conn, metadata.auditTable, object, entityId, "Follow", "Added", "Follow Type", null, followTypeName, author);

        // Name (the user who followed)
        insertFollowAuditRow(conn, metadata.auditTable, object, entityId, "Follow", "Added", "Name", null, followerName, author);

        // Object ID
        insertFollowAuditRow(conn, metadata.auditTable, object, entityId, "Follow", "Added", "ObjectId", null, String.valueOf(entityId), author);

        // Description - skip when the user did not enter one (per the audit spec).
        if (description != null && !description.trim().isEmpty()) {
            insertFollowAuditRow(conn, metadata.auditTable, object, entityId, "Follow", "Added", "Description", null, description, author);
        }
    }

    /**
     * Insert one Follow audit row using "Follow" as the Object column value
     * regardless of the facet (e.g. Glossary follows still record Object = "Follow").
     */
    private void insertFollowAuditRow(Connection conn, String auditTable, String object,
                                      int entityId, String event, String updateType, String field,
                                      String fromValue, String toValue, String author) throws SQLException {
        String sql = "INSERT INTO `" + auditTable + "`"
                + " (id, object, event, updateType, field, `from`, `to`, author, date, lastChange)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, entityId);
            ps.setString(2, object);
            ps.setString(3, event);
            ps.setString(4, updateType);
            ps.setString(5, field);
            if (fromValue == null) ps.setNull(6, Types.VARCHAR); else ps.setString(6, fromValue);
            if (toValue == null) ps.setNull(7, Types.VARCHAR); else ps.setString(7, toValue);
            ps.setString(8, author);
            ps.executeUpdate();
        }
    }

    private void logFollowUpdateAudit(Connection conn, String entityType, int entityId,
                                      Integer oldFollowType, Integer newFollowType,
                                      String oldDescription, String newDescription,
                                      String author) throws SQLException {
        AuditMetadata metadata = getAuditMetadata(entityType);
        if (metadata == null) {
            return;
        }

        // Log Follow Type change
        if (!Objects.equals(oldFollowType, newFollowType) && newFollowType != null) {
            LOGGER.fine(() -> String.format("Audit follow type change detected for entity %s: old=%s new=%s",
                    entityId, oldFollowType, newFollowType));
            String oldName = oldFollowType == null ? "" : resolveFollowTypeName(conn, oldFollowType);
            String newName = resolveFollowTypeName(conn, newFollowType);
            insertAuditHistory(conn, metadata, entityId, "Follow", "Updated", "Follow Type", oldName, newName, author);
        }

        // Log Description change
        if (newDescription != null) {
            String normalizedOldDescription = (oldDescription == null || oldDescription.trim().isEmpty()) ? "" : oldDescription;
            String normalizedNewDescription = (newDescription == null || newDescription.trim().isEmpty()) ? "" : newDescription;

            if (!Objects.equals(normalizedOldDescription, normalizedNewDescription)) {
                LOGGER.fine(() -> String.format("Audit description change detected for entity %s", entityId));
                insertAuditHistory(conn, metadata, entityId, "Follow", "Updated", "Description",
                        normalizedOldDescription, normalizedNewDescription, author);
            }
        }
    }

    private void insertAuditHistory(Connection conn, AuditMetadata metadata, int entityId,
                                    String event, String updateType, String fieldName,
                                    String fromValue, String toValue, String author) throws SQLException {
        String sql = "INSERT INTO " + metadata.auditTable +
                " (id, object, event, updateType, field, `from`, `to`, author, date, lastChange) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, entityId);
            ps.setString(2, metadata.objectLabel);
            ps.setString(3, event);
            ps.setString(4, updateType);
            ps.setString(5, fieldName);
            if (fromValue == null) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, fromValue);
            }
            if (toValue == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, toValue);
            }
            ps.setString(8, author);
            ps.setTimestamp(9, now);
            ps.setTimestamp(10, now);
            int rows = ps.executeUpdate();
            LOGGER.log(Level.FINE, () -> String.format("Inserted %d audit row(s) for table %s (entity %s, field %s).",
                    rows, metadata.auditTable, entityId, fieldName));
        }
    }

    private String resolveFollowTypeName(Connection conn, int followTypeId) throws SQLException {
        String sql = "SELECT Name FROM follow_type WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, followTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        }
        return String.valueOf(followTypeId);
    }
    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = Map.of("error", message);
        objectMapper.writeValue(response.getWriter(), error);
    }
}
