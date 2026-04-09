package com.example.budg_v2.service;

import com.example.budg_v2.dao.ClientDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Client;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class ClientService {

    private final ClientDAO clientDAO = new ClientDAO();

    public List<Map<String, Object>> getAllClients() throws SQLException {
        String sql = """
            SELECT 
                c.ID,
                c.PrimaryName,
                c.Description AS Definition,
                c.LongName AS `Long Name`,
                s.PrimaryName AS `BUDG Status`,
                cl.PrimaryName AS Lifecycle,
                v.Name AS `BUDG Viewing`,
                c.CreateDatetime AS Created,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS Last_Updated_By,
                c.LastUpdateDatetime AS `Last Updated`,
                c.Parent_ID,
                c.Lifecycle AS Lifecycle_ID,
                c.Status AS Status_ID,
                c.IsPublic AS IsPublic_ID,
                c.LastUpdate_UserID
            FROM client c
            LEFT JOIN status s ON c.Status = s.ID
            LEFT JOIN client_lifecycle cl ON c.Lifecycle = cl.ID
            LEFT JOIN viewing v ON c.IsPublic = v.ID
            LEFT JOIN people p ON c.LastUpdate_UserID = p.ID
            ORDER BY c.ID
        """;
        return executeClientQuery(sql);
    }

    /**
     * Get all clients filtered by user's segment access
     */
    public List<Map<String, Object>> getAllClients(int userId) throws SQLException {
        List<Map<String, Object>> allClients = getAllClients();
        if (allClients.isEmpty()) {
            return allClients;
        }
        
        // Get accessible client IDs for this user
        List<Integer> allIds = allClients.stream()
                .map(c -> (Integer) c.get("id"))
                .collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.getAccessibleObjectIdsInSegments(userId, "Client", allIds);
        
        // Filter to only accessible clients
        return allClients.stream()
                .filter(c -> accessibleIds.contains((Integer) c.get("id")))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getClientById(int id) throws SQLException {
        // Created_By_* from first client_audit row with RevType = 'Added' (creator at insert time)
        String sql = """
            SELECT
                c.ID,
                c.PrimaryName,
                c.Description AS Definition,
                c.LongName AS `Long Name`,
                s.PrimaryName AS `BUDG Status`,
                cl.PrimaryName AS Lifecycle,
                v.Name AS `BUDG Viewing`,
                c.CreateDatetime AS Created,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS Last_Updated_By,
                c.LastUpdateDatetime AS `Last Updated`,
                c.Parent_ID,
                c.Lifecycle AS Lifecycle_ID,
                c.Status AS Status_ID,
                c.IsPublic AS IsPublic_ID,
                c.LastUpdate_UserID,
                ca_creator.LastUpdate_UserID AS Created_By_ID,
                NULLIF(TRIM(CONCAT(COALESCE(pcreator.First_Name, ''), ' ', COALESCE(pcreator.Last_Name, ''))), '') AS Created_By_Name
            FROM client c
            LEFT JOIN status s ON c.Status = s.ID
            LEFT JOIN client_lifecycle cl ON c.Lifecycle = cl.ID
            LEFT JOIN viewing v ON c.IsPublic = v.ID
            LEFT JOIN people p ON c.LastUpdate_UserID = p.ID
            LEFT JOIN (
                SELECT ca.ID, ca.LastUpdate_UserID
                FROM client_audit ca
                INNER JOIN (
                    SELECT ID, MIN(Rev) AS min_rev
                    FROM client_audit
                    WHERE RevType = 'Added'
                    GROUP BY ID
                ) first_added ON first_added.ID = ca.ID AND first_added.min_rev = ca.Rev
                WHERE ca.RevType = 'Added'
            ) ca_creator ON ca_creator.ID = c.ID
            LEFT JOIN people pcreator ON pcreator.ID = ca_creator.LastUpdate_UserID
            WHERE c.ID = ?
        """;
        List<Map<String, Object>> results = executeClientQuery(sql, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Map<String, Object>> getClientStakeholders(int clientId, Integer moduleId) throws SQLException {
        StringBuilder sql = new StringBuilder("\n" +
                "SELECT \n" +
                "    orl.PrimaryName AS Role,\n" +
                "    CONCAT(p.First_Name, ' ', p.Last_Name) AS Name,\n" +
                "    ou.Name AS OrgUnit,\n" +
                "    ra.Message AS RoleAccepted,\n" +
                "    oxp.isdelegateof AS isDelegateOf\n" +
                "FROM object_role orl\n" +
                "JOIN object_x_people oxp \n" +
                "    ON oxp.RoleID = orl.ID\n" +
                "JOIN client_x_objectxpeople cxop \n" +
                "    ON cxop.Object_x_ipid = oxp.ID\n" +
                "JOIN people p\n" +
                "    ON p.ID = oxp.iPID\n" +
                "JOIN org_unit ou \n" +
                "    ON ou.ID = p.Org_Unit_ID\n" +
                "LEFT JOIN roleaccepted ra \n" +
                "    ON ra.ID = oxp.AcceptedID\n" +
                "WHERE cxop.ClientID = ?\n");

        List<Object> params = new ArrayList<>();
        params.add(clientId);
        if (moduleId != null) {
            sql.append("  AND orl.module = ?\n");
            params.add(moduleId);
        }
        return executeStakeholdersQuery(sql.toString(), params.toArray());
    }

    private List<Map<String, Object>> executeStakeholdersQuery(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("role", rs.getString("Role"));
                    row.put("name", rs.getString("Name"));
                    row.put("orgUnit", rs.getString("OrgUnit"));
                    row.put("roleAccepted", rs.getString("RoleAccepted"));
                    row.put("isDelegateOf", rs.getObject("isDelegateOf"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    public List<Map<String, Object>> searchClients(String searchQuery) throws SQLException {
        String sql = """
            SELECT 
                c.ID,
                c.PrimaryName,
                c.Description AS Definition,
                c.LongName AS `Long Name`,
                s.PrimaryName AS `BUDG Status`,
                cl.PrimaryName AS Lifecycle,
                v.Name AS `BUDG Viewing`,
                c.CreateDatetime AS Created,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS Last_Updated_By,
                c.LastUpdateDatetime AS `Last Updated`,
                c.Parent_ID,
                c.Lifecycle AS Lifecycle_ID,
                c.Status AS Status_ID,
                c.IsPublic AS IsPublic_ID,
                c.LastUpdate_UserID
            FROM client c
            LEFT JOIN status s ON c.Status = s.ID
            LEFT JOIN client_lifecycle cl ON c.Lifecycle = cl.ID
            LEFT JOIN viewing v ON c.IsPublic = v.ID
            LEFT JOIN people p ON c.LastUpdate_UserID = p.ID
            WHERE c.PrimaryName LIKE ? OR c.LongName LIKE ? OR c.Description LIKE ?
            ORDER BY c.ID
        """;
        String pattern = "%" + searchQuery + "%";
        return executeClientQuery(sql, pattern, pattern, pattern);
    }

    public Map<String, Object> createClient(Map<String, Object> clientData, int userId) throws SQLException {
        // Validate mandatory fields
        validateMandatoryFields(clientData);
        
        // Convert Map to Client object
        Client client = new Client();
        client.setParentId((Integer) clientData.get("parent_id"));
        client.setLifecycle((Integer) clientData.get("lifecycle"));
        client.setStatus((Integer) clientData.get("status"));
        client.setIsPublic((Integer) clientData.get("is_public"));
        client.setPrimaryName((String) clientData.get("primary_name"));
        client.setLongName((String) clientData.get("long_name"));
        client.setDescription((String) clientData.get("description"));
        client.setLastUpdateUserID(userId);
        
        // Use ClientDAO to create client (this will automatically create audit records)
        Client createdClient = clientDAO.createClient(client);
        
        // Convert back to Map for response
        clientData.put("id", createdClient.getId());
        return clientData;
    }

    public boolean updateClient(Map<String, Object> clientData, int userId) throws SQLException {
        // Convert Map to Client object
        Client client = new Client();
        client.setId((Integer) clientData.get("id"));
        client.setParentId((Integer) clientData.get("parent_id"));
        client.setLifecycle((Integer) clientData.get("lifecycle"));
        client.setStatus((Integer) clientData.get("status"));
        client.setIsPublic((Integer) clientData.get("is_public"));
        client.setPrimaryName((String) clientData.get("primary_name"));
        client.setLongName((String) clientData.get("long_name"));
        client.setDescription((String) clientData.get("description"));
        client.setLastUpdateUserID(userId);
        
        // Use ClientDAO to update client
        return clientDAO.updateClient(client);
    }

    public boolean deleteClient(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return clientDAO.deleteClientWithAudit(id, userName);
    }

    private String getCurrentUserName(HttpServletRequest request) {
        try {
            String userJson = (String) request.getAttribute("user");
            if (userJson != null && userJson.contains("\"username\":")) {
                int start = userJson.indexOf("\"username\":\"") + 12;
                int end = userJson.indexOf("\"", start);
                if (end > start) {
                    return userJson.substring(start, end);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting username: " + e.getMessage());
        }
        return "Unknown User";
    }

    // Helper method to get reference data for dropdowns
    public List<Map<String, Object>> getStatusList() throws SQLException {
        String sql = "SELECT ID, PrimaryName FROM status ORDER BY PrimaryName";
        return executeReferenceQuery(sql);
    }

    public List<Map<String, Object>> getLifecycleList() throws SQLException {
        String sql = "SELECT ID, PrimaryName FROM client_lifecycle ORDER BY PrimaryName";
        return executeReferenceQuery(sql);
    }

    public List<Map<String, Object>> getViewingList() throws SQLException {
        String sql = "SELECT ID, Name FROM viewing ORDER BY Name";
        return executeReferenceQuery(sql);
    }

    public List<Map<String, Object>> getParentClients() throws SQLException {
        String sql = "SELECT ID, PrimaryName FROM client ORDER BY PrimaryName";
        return executeReferenceQuery(sql);
    }

    /**
     * Get parent clients filtered by user's segment access
     */
    public List<Map<String, Object>> getParentClients(int userId) throws SQLException {
        List<Map<String, Object>> allClients = getParentClients();
        if (allClients.isEmpty()) {
            return allClients;
        }
        
        // Get accessible client IDs for this user
        List<Integer> allIds = allClients.stream()
                .map(c -> (Integer) c.get("id"))
                .collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.getAccessibleObjectIdsInSegments(userId, "Client", allIds);
        
        // Filter to only accessible clients
        return allClients.stream()
                .filter(c -> accessibleIds.contains((Integer) c.get("id")))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> executeClientQuery(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> clients = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> client = new HashMap<>();
                    client.put("id", rs.getInt("ID"));
                    client.put("primary_name", rs.getString("PrimaryName"));
                    client.put("definition", rs.getString("Definition"));
                    client.put("long_name", rs.getString("Long Name"));
                    client.put("BUDG_status", rs.getString("BUDG Status"));
                    client.put("lifecycle", rs.getString("Lifecycle"));
                    client.put("BUDG_viewing", rs.getString("BUDG Viewing"));
                    client.put("created", rs.getTimestamp("Created"));
                    client.put("last_updated_by", rs.getString("Last_Updated_By"));
                    client.put("last_updated", rs.getTimestamp("Last Updated"));
                    client.put("parent_id", rs.getObject("Parent_ID"));
                    client.put("lifecycle_id", rs.getObject("Lifecycle_ID"));
                    client.put("status_id", rs.getObject("Status_ID"));
                    client.put("is_public_id", rs.getObject("IsPublic_ID"));
                    client.put("last_update_user_id", rs.getObject("LastUpdate_UserID"));
                    // Optional columns: only present when SQL includes client_audit creator join (getClientById)
                    try {
                        Object cbId = rs.getObject("Created_By_ID");
                        if (cbId != null) {
                            client.put("created_by_id", cbId);
                            client.put("createdBy_ID", cbId);
                            client.put("Created_By_ID", cbId);
                        }
                    } catch (SQLException ignored) {
                        // Column not in result set (list/search queries)
                    }
                    try {
                        String cbName = rs.getString("Created_By_Name");
                        if (cbName != null && !cbName.isBlank()) {
                            String trimmed = cbName.trim();
                            client.put("created_by_name", trimmed);
                            client.put("createdByName", trimmed);
                            client.put("Created_By_Name", trimmed);
                        }
                    } catch (SQLException ignored) {
                        // Column not in result set
                    }
                    clients.add(client);
                }
            }
        }
        return clients;
    }

    private List<Map<String, Object>> executeReferenceQuery(String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", rs.getInt("ID"));
                item.put("name", rs.getString(2)); // Second column is the name
                results.add(item);
            }
        }
        return results;
    }


    private void validateMandatoryFields(Map<String, Object> clientData) throws SQLException {
        if (clientData.get("description") == null || ((String) clientData.get("description")).trim().isEmpty()) {
            throw new SQLException("Description is required");
        }
        if (clientData.get("primary_name") == null || ((String) clientData.get("primary_name")).trim().isEmpty()) {
            throw new SQLException("Primary Name is required");
        }
        if (clientData.get("status") == null) {
            throw new SQLException("BUDG Status is required");
        }
        if (clientData.get("lifecycle") == null) {
            throw new SQLException("Lifecycle is required");
        }
        if (clientData.get("is_public") == null) {
            throw new SQLException("BUDG Viewing is required");
        }
    }
}
