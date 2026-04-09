package com.example.budg_v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.budg_v2.database.DatabaseConnection;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/user-followed-items")
public class UserFollowedItemsServlet extends HttpServlet {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Get user ID from request parameter
            String userIdParam = request.getParameter("userId");
            System.out.println("UserFollowedItemsServlet: userIdParam = " + userIdParam);
            
            if (userIdParam == null || userIdParam.trim().isEmpty()) {
                System.out.println("UserFollowedItemsServlet: No userId parameter provided");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, Object> error = Map.of("error", "User ID is required");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }

            int userId = Integer.parseInt(userIdParam);
            System.out.println("UserFollowedItemsServlet: Processing userId = " + userId);
            
            List<Map<String, Object>> followedItems = getFollowedItems(userId);
            System.out.println("UserFollowedItemsServlet: Found " + followedItems.size() + " followed items");

            Map<String, Object> result = Map.of(
                "success", true,
                "followedItems", followedItems,
                "count", followedItems.size()
            );

            objectMapper.writeValue(response.getWriter(), result);

        } catch (NumberFormatException e) {
            System.err.println("UserFollowedItemsServlet: Invalid user ID format: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> error = Map.of("error", "Invalid user ID format");
            objectMapper.writeValue(response.getWriter(), error);
        } catch (SQLException e) {
            System.err.println("UserFollowedItemsServlet: Database error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = Map.of("error", "Database error: " + e.getMessage());
            objectMapper.writeValue(response.getWriter(), error);
        } catch (Exception e) {
            System.err.println("UserFollowedItemsServlet: Internal server error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = Map.of("error", "Internal server error: " + e.getMessage());
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private List<Map<String, Object>> getFollowedItems(int userId) throws SQLException {
        List<Map<String, Object>> followedItems = new ArrayList<>();
        System.out.println("UserFollowedItemsServlet: getFollowedItems called with userId = " + userId);

        try (Connection conn = DatabaseConnection.getConnection()) {
            System.out.println("UserFollowedItemsServlet: Database connection established");
            // Query to get all followed items with their details
            String sql = """
                SELECT 
                    f.ID as follow_id,
                    f.Follow_type,
                    f.With_Children,
                    f.Description as follow_description,
                    f.Created_datetime,
                    f.last_updated_datetime,
                    f.Module_id,
                    f.objectID,
                    ft.Name as follow_type_name,
                    ft.Description as follow_type_description
                FROM follow f
                LEFT JOIN follow_type ft ON f.Follow_type = ft.id
                WHERE f.ip_id = ?
                ORDER BY f.last_updated_datetime DESC
                """;

            System.out.println("UserFollowedItemsServlet: Executing SQL query");
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                System.out.println("UserFollowedItemsServlet: Prepared statement with userId = " + userId);

                try (ResultSet rs = ps.executeQuery()) {
                    System.out.println("UserFollowedItemsServlet: Query executed successfully");
                    int rowCount = 0;
                    while (rs.next()) {
                        rowCount++;
                        System.out.println("UserFollowedItemsServlet: Processing row " + rowCount);
                        Map<String, Object> item = new HashMap<>();
                        item.put("followId", rs.getInt("follow_id"));
                        item.put("followType", rs.getInt("Follow_type"));
                        item.put("followTypeName", rs.getString("follow_type_name"));
                        item.put("followTypeDescription", rs.getString("follow_type_description"));
                        item.put("withChildren", rs.getBoolean("With_Children"));
                        item.put("description", rs.getString("follow_description"));
                        item.put("createdDate", rs.getTimestamp("Created_datetime"));
                        item.put("lastUpdated", rs.getTimestamp("last_updated_datetime"));
                        item.put("moduleId", rs.getInt("Module_id"));
                        item.put("objectId", rs.getInt("objectID"));

                        // Get entity details based on module_id and objectID
                        System.out.println("UserFollowedItemsServlet: Getting entity details for moduleId=" + rs.getInt("Module_id") + ", objectId=" + rs.getInt("objectID"));
                        try {
                            Map<String, Object> entityDetails = getEntityDetails(conn, rs.getInt("Module_id"), rs.getInt("objectID"));
                            if (entityDetails != null) {
                                item.putAll(entityDetails);
                                System.out.println("UserFollowedItemsServlet: Entity details added");
                            } else {
                                // Entity not found or deleted — skip this item
                                System.out.println("UserFollowedItemsServlet: Entity not found or deleted, skipping");
                                continue;
                            }
                        } catch (Exception e) {
                            System.err.println("UserFollowedItemsServlet: Error getting entity details: " + e.getMessage());
                            // Entity could not be resolved — skip
                            continue;
                        }

                        followedItems.add(item);
                    }
                    System.out.println("UserFollowedItemsServlet: Processed " + rowCount + " rows from database");
                }
            }
        }

        System.out.println("UserFollowedItemsServlet: Returning " + followedItems.size() + " followed items");
        return followedItems;
    }

    private Map<String, Object> getEntityDetails(Connection conn, int moduleId, int objectId) throws SQLException {
        // Map module_id to entity types and their corresponding tables
        String entityType = getEntityTypeFromModuleId(moduleId);
        if (entityType == null) {
            System.out.println("UserFollowedItemsServlet: Unknown moduleId: " + moduleId);
            return null;
        }

        String tableName = getTableNameFromEntityType(entityType);
        if (tableName == null) {
            System.out.println("UserFollowedItemsServlet: Unknown entityType: " + entityType);
            return null;
        }

        System.out.println("UserFollowedItemsServlet: Looking up " + entityType + " (table: " + tableName + ") with ID: " + objectId);
        
        // Use specific queries for each entity type to get the right name field
        String sql = getEntityQuery(entityType, tableName);
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> details = new HashMap<>();
                    details.put("entityType", entityType);
                    // Handle different ID column names (ID vs id)
                    try {
                        details.put("entityId", rs.getInt("ID"));
                    } catch (SQLException e) {
                        try {
                            details.put("entityId", rs.getInt("id"));
                        } catch (SQLException e2) {
                            System.err.println("UserFollowedItemsServlet: Could not get ID for " + entityType + " ID " + objectId);
                        }
                    }
                    
                    // Get the name based on entity type
                    String name = getEntityName(rs, entityType);
                    if (name != null && !name.trim().isEmpty()) {
                        details.put("name", name);
                        System.out.println("UserFollowedItemsServlet: Found name: " + name);
                    } else {
                        System.out.println("UserFollowedItemsServlet: No name found for " + entityType + " ID " + objectId);
                    }
                    
                    return details;
                } else {
                    System.out.println("UserFollowedItemsServlet: No record found for " + entityType + " ID " + objectId);
                }
            }
        } catch (SQLException e) {
            System.err.println("UserFollowedItemsServlet: Error getting entity details for " + entityType + " ID " + objectId + ": " + e.getMessage());
        }

        return null;
    }
    
    private String getEntityQuery(String entityType, String tableName) {
        // Return specific queries for each entity type to get the right name field
        // Each query filters out soft-deleted objects
        switch (entityType) {
            case "system":
                return "SELECT ID, Name FROM " + tableName + " WHERE ID = ? AND (Deleted_datetime IS NULL OR Deleted_datetime = '0000-00-00 00:00:00')";
            case "dataset":
            case "data_sets":
                return "SELECT ID, PrimaryName FROM " + tableName + " WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            case "policy":
                return "SELECT ID, PrimaryName FROM " + tableName + " WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            case "process":
                return "SELECT ID, primaryname FROM " + tableName + " WHERE ID = ? AND (deleteddatetime IS NULL OR deleteddatetime = '0000-00-00 00:00:00')";
            case "project":
                return "SELECT ID, primaryname FROM " + tableName + " WHERE ID = ? AND (deletedatetime IS NULL OR deletedatetime = '0000-00-00 00:00:00')";
            case "product":
                return "SELECT id, primaryname FROM " + tableName + " WHERE id = ? AND (deleteddatetime IS NULL OR deleteddatetime = '0000-00-00 00:00:00')";
            case "client":
                return "SELECT ID, PrimaryName FROM " + tableName + " WHERE ID = ? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')";
            case "business_area":
                return "SELECT ID, PrimaryName FROM " + tableName + " WHERE ID = ? AND (deletedatetime IS NULL OR deletedatetime = '0000-00-00 00:00:00')";
            case "capability":
                return "SELECT ID, PrimaryName FROM " + tableName + " WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            case "committee":
                return "SELECT ID, PrimaryName FROM " + tableName + " WHERE ID = ? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')";
            case "regulation":
                return "SELECT ID, primaryName FROM " + tableName + " WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            case "legal_entity":
            case "legalentity":
                return "SELECT ID, ShortName, LongName FROM " + tableName + " WHERE ID = ? AND (DeleteDatetime IS NULL OR DeleteDatetime = '0000-00-00 00:00:00')";
            case "interface":
            case "system_interface":
                return "SELECT id, Name FROM " + tableName + " WHERE id = ? AND (deleted_datetime IS NULL OR deleted_datetime = '0000-00-00 00:00:00')";
            case "glossary":
                return "SELECT ID, Name FROM " + tableName + " WHERE ID = ? AND (Deleted_datetime IS NULL OR Deleted_datetime = '0000-00-00 00:00:00')";
            case "people":
                return "SELECT ID, First_Name, Last_Name FROM " + tableName + " WHERE ID = ? AND (Deleted_date IS NULL OR Deleted_date = '0000-00-00 00:00:00')";
            case "geography":
                return "SELECT ID, PrimaryName FROM " + tableName + " WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            case "regulator":
                return "SELECT ID, PrimaryName FROM " + tableName + " WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            case "regulatory_theme":
            case "regulatorytheme":
                return "SELECT ID, Name FROM " + tableName + " WHERE ID = ? AND (DeletedDatetime IS NULL OR DeletedDatetime = '0000-00-00 00:00:00')";
            case "org_unit":
            case "orgunit":
                return "SELECT ID, Name FROM " + tableName + " WHERE ID = ? AND (deleted_Date IS NULL OR deleted_Date = '0000-00-00 00:00:00')";
            case "attribute":
                return "SELECT ID, PrimaryName FROM " + tableName + " WHERE ID = ?";
            default:
                return "SELECT ID, Name FROM " + tableName + " WHERE ID = ?";
        }
    }

    private String getEntityName(ResultSet rs, String entityType) throws SQLException {
        // For regulation, prioritize primaryName
        if ("regulation".equals(entityType)) {
            try {
                String value = rs.getString("primaryName");
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            } catch (SQLException ignored) {
            }
        }
        
        // For legal_entity, prioritize LongName, fallback to ShortName
        if ("legal_entity".equals(entityType) || "legalentity".equals(entityType)) {
            try {
                String value = rs.getString("LongName");
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            } catch (SQLException ignored) {
            }
            try {
                String value = rs.getString("ShortName");
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            } catch (SQLException ignored) {
            }
        }
        
        // For dataset/data_sets, use PrimaryName
        if ("dataset".equals(entityType) || "data_sets".equals(entityType)) {
            try {
                String value = rs.getString("PrimaryName");
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            } catch (SQLException ignored) {
            }
        }
        
        // For process, use primaryname (lowercase)
        if ("process".equals(entityType)) {
            try {
                String value = rs.getString("primaryname");
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            } catch (SQLException ignored) {
            }
        }
        
        // For project, use primaryname (lowercase)
        if ("project".equals(entityType)) {
            try {
                String value = rs.getString("primaryname");
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            } catch (SQLException ignored) {
            }
        }
        
        // For product, use primaryname (lowercase)
        if ("product".equals(entityType)) {
            try {
                String value = rs.getString("primaryname");
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            } catch (SQLException ignored) {
            }
        }
        
        // For policy, client, committee, capability, business_area, geography, regulator, attribute, use PrimaryName (capitalized)
        if ("policy".equals(entityType) || "client".equals(entityType) || "committee".equals(entityType) 
                || "capability".equals(entityType) || "business_area".equals(entityType) 
                || "geography".equals(entityType) || "regulator".equals(entityType) || "attribute".equals(entityType)) {
            try {
                String value = rs.getString("PrimaryName");
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            } catch (SQLException ignored) {
            }
        }
        
        // For people, use CONCAT(First_Name, ' ', Last_Name)
        if ("people".equals(entityType)) {
            try {
                String firstName = rs.getString("First_Name");
                String lastName = rs.getString("Last_Name");
                if (firstName != null || lastName != null) {
                    String fullName = (firstName != null ? firstName : "") + 
                                     (firstName != null && lastName != null ? " " : "") + 
                                     (lastName != null ? lastName : "");
                    if (!fullName.trim().isEmpty()) {
                        return fullName.trim();
                    }
                }
            } catch (SQLException ignored) {
            }
        }
        
        // For glossary, interface, system, org_unit, regulatory_theme, use Name
        if ("glossary".equals(entityType) || "interface".equals(entityType) || "system".equals(entityType) 
                || "org_unit".equals(entityType) || "orgunit".equals(entityType) 
                || "regulatory_theme".equals(entityType) || "regulatorytheme".equals(entityType)) {
            try {
                String value = rs.getString("Name");
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            } catch (SQLException ignored) {
            }
        }
        
        String[] candidateColumns = new String[] { "Name", "Title", "name", "primaryname", "primaryName", "PrimaryName" };

        for (String column : candidateColumns) {
            try {
                String value = rs.getString(column);
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            } catch (SQLException ignored) {
            }
        }
        return null;
    }

    private String getEntityTypeFromModuleId(int moduleId) {
        // Fetch module information from database
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT primaryname FROM module WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, moduleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String moduleName = rs.getString("primaryname");
                        System.out.println("UserFollowedItemsServlet: Found module " + moduleId + " = " + moduleName);
                        return moduleName.toLowerCase().replace(" ", "_");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("UserFollowedItemsServlet: Error fetching module " + moduleId + ": " + e.getMessage());
        }
        
        System.out.println("UserFollowedItemsServlet: Unknown moduleId: " + moduleId + " - treating as generic entity");
        return "entity";
    }

    private String getTableNameFromEntityType(String entityType) {
        // Handle common entity type mappings
        switch (entityType) {
            case "system": return "system";
            case "dataset": 
            case "data_sets": return "dataset";
            case "policy": return "policy";
            case "process": return "process";
            case "project": return "project";
            case "business_area": 
            case "businessarea": return "business_area";
            case "capability": return "capability";
            case "committee": return "committee";
            case "product": return "product";
            case "regulation": return "regulation";
            case "interface": 
            case "system_interface": return "interface";
            case "glossary": return "glossary";
            case "legal_entity":
            case "legalentity": return "legal";
            case "people": return "people";
            case "geography": return "geography";
            case "regulator": return "regulator";
            case "regulatory_theme":
            case "regulatorytheme": return "regulatory_theme";
            case "org_unit":
            case "orgunit": return "org_unit";
            case "attribute": return "attribute";
            case "entity": return "dataset"; // Fallback for unknown types
            default: 
                // For unknown types, try to use the entity type as table name
                System.out.println("UserFollowedItemsServlet: Using entityType as table name: " + entityType);
                return entityType;
        }
    }
}
