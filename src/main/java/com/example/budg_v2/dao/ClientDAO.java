package com.example.budg_v2.dao;

import com.example.budg_v2.audit.AuditHistoryWriter;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Client;
import com.example.budg_v2.service.SegmentAccessService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ClientDAO {

    // SELECT queries
    private static final String SELECT_ALL_WITH_JOINS = """
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
            WHERE c.DeleteDatetime IS NULL
            ORDER BY c.ID
            """;

    private static final String SELECT_BY_ID_WITH_JOINS = """
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
            WHERE c.ID = ?
            """;

    private static final String SEARCH_WITH_JOINS = """
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
            WHERE c.DeleteDatetime IS NULL
            AND (c.PrimaryName LIKE ? OR c.LongName LIKE ? OR c.Description LIKE ?)
            ORDER BY c.ID
            """;

    // INSERT query
    private static final String INSERT = """
            INSERT INTO client
            (Parent_ID, Lifecycle, Status, IsPublic, PrimaryName, LongName, Description, LastUpdate_UserID)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    // UPDATE query
    private static final String UPDATE = """
            UPDATE client SET
                Parent_ID = ?,
                PrimaryName = ?,
                Description = ?,
                LongName = ?,
                Status = ?,
                Lifecycle = ?,
                IsPublic = ?,
                LastUpdate_UserID = ?,
                LastUpdateDatetime = NOW()
            WHERE ID = ?
            """;

    // DELETE query
    private static final String DELETE = "DELETE FROM client WHERE ID = ?";

    public List<Client> getAllClients() throws SQLException {
        List<Client> clients = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(SELECT_ALL_WITH_JOINS)) {

            while (rs.next()) {
                clients.add(mapResultSetToClient(rs));
            }
        }
        return clients;
    }

    /**
     * Get all clients filtered by user's segment access
     */
    public List<Client> getAllClients(int userId) throws SQLException {
        List<Client> allClients = getAllClients();
        if (allClients.isEmpty()) {
            return allClients;
        }

        // Get accessible client IDs for this user (filtered by selected segments)
        List<Integer> allIds = allClients.stream().map(Client::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Client", allIds);

        // Filter to only accessible clients
        return allClients.stream()
                .filter(c -> accessibleIds.contains(c.getId()))
                .collect(Collectors.toList());
    }

    public Client getClientById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID_WITH_JOINS)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToClient(rs);
            }
        }
        return null;
    }

    public List<Client> searchClients(String searchQuery) throws SQLException {
        List<Client> clients = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SEARCH_WITH_JOINS)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                clients.add(mapResultSetToClient(rs));
            }
        }
        return clients;
    }

    public Client createClient(Client client) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setObject(1, client.getParentId());
            pstmt.setInt(2, client.getLifecycle());
            pstmt.setInt(3, client.getStatus());
            pstmt.setInt(4, client.getIsPublic());
            pstmt.setString(5, client.getPrimaryName());
            pstmt.setString(6, client.getLongName());
            pstmt.setString(7, client.getDescription());
            pstmt.setObject(8, client.getLastUpdateUserID());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating client failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int clientId = generatedKeys.getInt(1);
                    client.setId(clientId);

                    // Create audit records after successful insert (stakeholder audit is created by servlet after assignCreatorRole)
                    try {
                        String userName = getPersonFullName(client.getLastUpdateUserID());
                        createClientAuditRecords(clientId, userName);
                        createClientAuditRecord(clientId);
                    } catch (Exception e) {
                        // Don't fail the insert if audit fails
                    }

                    return client;
                } else {
                    throw new SQLException("Creating client failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updateClient(Client client) throws SQLException {
        // الخطوة 1: الحصول على البيانات القديمة قبل التحديث
        Client oldClient = getClientById(client.getId());
        if (oldClient == null) {
            throw new SQLException("Client not found with ID: " + client.getId());
        }

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setObject(1, client.getParentId());
            pstmt.setString(2, client.getPrimaryName());
            pstmt.setString(3, client.getDescription());
            pstmt.setString(4, client.getLongName());
            pstmt.setInt(5, client.getStatus());
            pstmt.setInt(6, client.getLifecycle());
            pstmt.setInt(7, client.getIsPublic());
            pstmt.setObject(8, client.getLastUpdateUserID());
            pstmt.setInt(9, client.getId());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات
                try {
                    String userName = "System"; // Default fallback
                    Integer userId = client.getLastUpdateUserID();
                    if (userId != null && userId > 0) {
                        String fullName = getPersonFullName(userId);
                        if (fullName != null && !fullName.trim().isEmpty()) {
                            userName = fullName;
                        } else {
                            // إذا لم نجد الاسم، استخدم User ID كبديل أفضل من "System"
                            userName = "User ID: " + userId;
                        }
                    }

                    createClientUpdateAuditRecords(client.getId(), oldClient, client, userName);
                    // system.out.println("✅ Client update audit records created for ID: " +
                    // client.getId() + " with author: " + userName);
                } catch (Exception e) {
                    System.err.println("❌ Error creating client update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // الخطوة 3: إنشاء snapshot جديد في client_audit
                try {
                    createClientUpdateAuditSnapshot(client.getId());
                    // system.out.println("✅ ClientDAO: client_audit update snapshot created for ID:
                    // " + client.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating client_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            return affectedRows > 0;
        }
    }

    public boolean deleteClient(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private Client mapResultSetToClient(ResultSet rs) throws SQLException {
        Client client = new Client();

        // Basic fields
        client.setId(rs.getInt("ID"));
        client.setPrimaryName(rs.getString("PrimaryName"));
        client.setLongName(rs.getString("Long Name"));
        client.setDescription(rs.getString("Definition"));
        client.setCreateDatetime(rs.getTimestamp("Created"));
        client.setLastUpdateDatetime(rs.getTimestamp("Last Updated"));

        // Foreign key fields
        int parentId = rs.getInt("Parent_ID");
        if (!rs.wasNull()) {
            client.setParentId(parentId);
        }

        client.setLifecycle(rs.getInt("Lifecycle_ID"));
        client.setStatus(rs.getInt("Status_ID"));
        client.setIsPublic(rs.getInt("IsPublic_ID"));

        int lastUpdateUserId = rs.getInt("LastUpdate_UserID");
        if (!rs.wasNull()) {
            client.setLastUpdateUserID(lastUpdateUserId);
        }

        // Joined data fields
        client.setStatusName(rs.getString("BUDG Status"));
        client.setLifecycleName(rs.getString("Lifecycle"));
        client.setViewingName(rs.getString("BUDG Viewing"));
        client.setLastUpdatedByName(rs.getString("Last_Updated_By"));

        return client;
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int clientId, String object,
            String event, String updateType, String field, String value, String userName) throws SQLException {
        // system.out.println("ClientDAO: createNewAuditRecord called - clientId: " +
        // clientId + ", field: " + field + ", value: " + value);

        auditStmt.setInt(1, clientId); // id
        auditStmt.setString(2, object); // object
        auditStmt.setString(3, event); // event
        auditStmt.setString(4, updateType); // updateType
        auditStmt.setString(5, field); // field
        auditStmt.setString(6, value); // to
        auditStmt.setString(7, userName); // author

        // system.out.println("ClientDAO: Executing audit insert for field: " + field);
        auditStmt.executeUpdate();

        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                int auditId = generatedKeys.getInt(1);
                // system.out.println("ClientDAO: Audit record created with ID: " + auditId);
                return auditId;
            }
        }
        // system.out.println("ClientDAO: No generated key returned");
        return -1;
    }

    /**
     * إنشاء audit records للـ client الجديد
     * يتم استدعاء هذا method بعد إنشاء الـ client بنجاح
     */
    public void createClientAuditRecords(int clientId, String userName) throws SQLException {
        // system.out.println("ClientDAO: createClientAuditRecords called with clientId:
        // " + clientId + ", userName: " + userName);
        Connection conn = null;
        PreparedStatement auditStmt = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            // system.out.println("ClientDAO: Transaction started");

            // 1. الحصول على بيانات الـ client
            String clientDataSql = "SELECT * FROM client WHERE ID = ?";
            // system.out.println("ClientDAO: Executing SQL: " + clientDataSql);
            PreparedStatement clientStmt = conn.prepareStatement(clientDataSql);
            clientStmt.setInt(1, clientId);
            ResultSet clientRs = clientStmt.executeQuery();

            if (!clientRs.next()) {
                // system.out.println("ClientDAO: Client not found with ID: " + clientId);
                throw new SQLException("Client not found with ID: " + clientId);
            }
            // system.out.println("ClientDAO: Client found, starting audit record
            // creation");

            // 2. إعداد audit statement
            String auditSql = """
                        INSERT INTO client_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                        VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
                    """;
            AuditHistoryWriter.logCreatedBy(conn, "client_audit_history", clientId, "Client", userName);
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);

            // Primary Name
            String primaryName = clientRs.getString("PrimaryName");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, clientId, "Client", "Details", "Added", "Primary Name",
                        primaryName, userName);
            }

            // Long Name
            String longName = clientRs.getString("LongName");
            if (longName != null && !longName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, clientId, "Client", "Details", "Added", "Long Name", longName,
                        userName);
            }

            // Description
            String description = clientRs.getString("Description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, clientId, "Client", "Details", "Added", "Description",
                        description, userName);
            }

            // Parent Client
            Integer parentId = clientRs.getObject("Parent_ID", Integer.class);
            if (parentId != null) {
                String parentName = getClientName(parentId);
                if (parentName != null) {
                    createNewAuditRecord(conn, auditStmt, clientId, "Client", "Details", "Added", "Parent Client",
                            parentName, userName);
                }
            }

            // Status
            Integer statusId = clientRs.getObject("Status", Integer.class);
            if (statusId != null) {
                String statusName = getStatusPrimaryName(statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, clientId, "Client", "Details", "Status Change", "Status",
                            statusName, userName);
                }
            }

            // Lifecycle
            Integer lifecycleId = clientRs.getObject("Lifecycle", Integer.class);
            if (lifecycleId != null) {
                String lifecycleName = getClientLifecycleName(lifecycleId);
                if (lifecycleName != null) {
                    createNewAuditRecord(conn, auditStmt, clientId, "Client", "Details", "Added", "Lifecycle",
                            lifecycleName, userName);
                }
            }

            // Is Public
            Integer isPublicId = clientRs.getObject("IsPublic", Integer.class);
            if (isPublicId != null) {
                String isPublicName = getViewingName(isPublicId);
                if (isPublicName != null) {
                    createNewAuditRecord(conn, auditStmt, clientId, "Client", "Details", "Added", "Is Public",
                            isPublicName, userName);
                }
            }

            // Created By is written as the first row via AuditHistoryWriter.logCreatedBy.

            conn.commit(); // تأكيد الـ transaction

        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback(); // إلغاء الـ transaction في حالة الخطأ
            }
            throw e;
        } finally {
            // تنظيف الموارد
            if (auditStmt != null)
                auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * إنشاء سجل في جدول client_audit بعد إنشاء الـ client
     * يتم استدعاء هذا method بعد إنشاء الـ client بنجاح
     */
    public void createClientAuditRecord(int clientId) throws SQLException {
        String sql = """
                    INSERT INTO client_audit (
                        ID, Parent_ID, Lifecycle, Status, IsPublic, PrimaryName, LongName, Description,
                        CreateDatetime, LastUpdateDatetime, LastUpdate_UserID, RevType
                    )
                    SELECT
                        ID, Parent_ID, Lifecycle, Status, IsPublic, PrimaryName, LongName, Description,
                        CreateDatetime, LastUpdateDatetime, LastUpdate_UserID, 'Added'
                    FROM client
                    WHERE ID = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            ps.executeUpdate();
        }
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إنشاء الـ client
     * يتم استدعاء هذا method بعد إنشاء الـ client بنجاح
     */
    public void createStakeholderAuditRecords(int clientId, String userName, String userFullName, int roleId)
            throws SQLException {
        Connection conn = null;
        PreparedStatement auditStmt = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction

            // 1. إعداد audit statement للـ stakeholder
            String auditSql = """
                        INSERT INTO client_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                        VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
                    """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);

            // 2. إدراج 3 سجلات للـ stakeholder
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleId(clientId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }

            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getRoleName(actualRoleId);
            if (roleName == null)
                roleName = "Client Owner"; // fallback

            // Role
            createNewAuditRecord(conn, auditStmt, clientId, "Stakeholder", "link", "Added", "Role", roleName, userName);

            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusId(clientId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(statusId);
                if (fetchedStatusName != null)
                    statusName = fetchedStatusName;
            }
            createNewAuditRecord(conn, auditStmt, clientId, "Stakeholder", "link", "Added", "Role Status", statusName,
                    userName);

            // Name
            createNewAuditRecord(conn, auditStmt, clientId, "Stakeholder", "link", "Added", "Name", userFullName,
                    userName);

            conn.commit(); // تأكيد الـ transaction

        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback(); // إلغاء الـ transaction في حالة الخطأ
            }
            throw e;
        } finally {
            // تنظيف الموارد
            if (auditStmt != null)
                auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // Helper methods للحصول على الأسماء
    private String getClientName(int clientId) throws SQLException {
        String sql = "SELECT PrimaryName FROM client WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getStatusPrimaryName(int statusId) throws SQLException {
        // system.out.println("ClientDAO: getStatusPrimaryName called with statusId: " +
        // statusId);

        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT PrimaryName FROM status WHERE ID = ?";
            // system.out.println("ClientDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("PrimaryName");
                        // system.out.println("ClientDAO: Found PrimaryName: " + result);
                        return result;
                    } else {
                        // system.out.println("ClientDAO: No record found for statusId: " + statusId);
                    }
                }
            }
        } catch (SQLException e) {
            // system.out.println("ClientDAO: Error with PrimaryName - " + e.getMessage());
        }

        // Try primaryname as fallback
        try {
            String sql = "SELECT primaryname FROM status WHERE ID = ?";
            // system.out.println("ClientDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("primaryname");
                        // system.out.println("ClientDAO: Found primaryname: " + result);
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            // system.out.println("ClientDAO: Error with primaryname - " + e.getMessage());
        }

        // system.out.println("ClientDAO: Using fallback for statusId: " + statusId);
        return "Status " + statusId; // Fallback
    }

    private String getClientLifecycleName(int lifecycleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM client_lifecycle WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    /**
     * Public helper for callers (e.g. servlet) that need to resolve user display name for audit.
     */
    public String getPersonFullNameForAudit(int personId) throws SQLException {
        return getPersonFullName(personId);
    }

    private String getPersonFullName(int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("fullName");
            }
        }
        return null;
    }

    private String getViewingName(int viewingId) throws SQLException {
        String sql = "SELECT Name FROM viewing WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, viewingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("Name");
            }
        }
        return null;
    }

    private String getRoleName(int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("primaryname");
            }
        }
        return null;
    }

    // Get actual roleID from object_x_people for the stakeholder
    private Integer getStakeholderRoleId(int clientId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                "JOIN client_x_objectxpeople cxo ON cxo.Object_x_ipid = oxp.ID " +
                "WHERE cxo.ClientID = ? " +
                "ORDER BY cxo.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("roleID");
            }
        }
        return null;
    }

    // Get statusID from object_x_people for the stakeholder
    private Integer getStakeholderStatusId(int clientId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                "JOIN client_x_objectxpeople cxo ON cxo.Object_x_ipid = oxp.ID " +
                "WHERE cxo.ClientID = ? " +
                "ORDER BY cxo.ID DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("statusID");
            }
        }
        return null;
    }

    // Get status name from object_x_ip_status by statusID
    private String getStatusNameById(int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM object_x_ip_status WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * إنشاء audit records عند تحديث الـ client
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    public void createClientUpdateAuditRecords(int clientId, Client oldClient, Client newClient, String userName)
            throws SQLException {
        // system.out.println("🔍 ClientDAO.createClientUpdateAuditRecords - START for
        // ID: " + clientId);
        Connection conn = null;
        PreparedStatement auditStmt = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            String auditSql = """
                        INSERT INTO client_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);

            boolean hasChanges = false;

            // Primary Name
            if (!isEqual(oldClient.getPrimaryName(), newClient.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, clientId, "Client", "Details",
                        "Updated", "Primary Name", oldClient.getPrimaryName(), newClient.getPrimaryName(), userName);
                hasChanges = true;
            }

            // Long Name
            if (!isEqual(oldClient.getLongName(), newClient.getLongName())) {
                createUpdateAuditRecord(conn, auditStmt, clientId, "Client", "Details",
                        "Updated", "Long Name", oldClient.getLongName(), newClient.getLongName(), userName);
                hasChanges = true;
            }

            // Description
            if (!isEqual(oldClient.getDescription(), newClient.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, clientId, "Client", "Details",
                        "Updated", "Description", oldClient.getDescription(), newClient.getDescription(), userName);
                hasChanges = true;
            }

            // Parent Client
            if (!isEqual(oldClient.getParentId(), newClient.getParentId())) {
                String oldParentName = oldClient.getParentId() != null
                        ? getClientNameWithConnection(conn, oldClient.getParentId())
                        : null;
                String newParentName = newClient.getParentId() != null
                        ? getClientNameWithConnection(conn, newClient.getParentId())
                        : null;
                createUpdateAuditRecord(conn, auditStmt, clientId, "Client", "Details",
                        "Updated", "Parent Client", oldParentName, newParentName, userName);
                hasChanges = true;
            }

            // Status (مع updateType خاص)
            if (!isEqual(oldClient.getStatus(), newClient.getStatus())) {
                String oldStatusName = oldClient.getStatus() != null
                        ? getStatusPrimaryNameWithConnection(conn, oldClient.getStatus())
                        : null;
                String newStatusName = newClient.getStatus() != null
                        ? getStatusPrimaryNameWithConnection(conn, newClient.getStatus())
                        : null;
                createUpdateAuditRecord(conn, auditStmt, clientId, "Client", "Details",
                        "Status Change", "Status", oldStatusName, newStatusName, userName);
                hasChanges = true;
            }

            // Lifecycle
            if (!isEqual(oldClient.getLifecycle(), newClient.getLifecycle())) {
                String oldLifecycleName = oldClient.getLifecycle() != null
                        ? getClientLifecycleNameWithConnection(conn, oldClient.getLifecycle())
                        : null;
                String newLifecycleName = newClient.getLifecycle() != null
                        ? getClientLifecycleNameWithConnection(conn, newClient.getLifecycle())
                        : null;
                createUpdateAuditRecord(conn, auditStmt, clientId, "Client", "Details",
                        "Updated", "Lifecycle", oldLifecycleName, newLifecycleName, userName);
                hasChanges = true;
            }

            // Is Public
            if (!isEqual(oldClient.getIsPublic(), newClient.getIsPublic())) {
                String oldViewingName = oldClient.getIsPublic() != null
                        ? getViewingNameWithConnection(conn, oldClient.getIsPublic())
                        : null;
                String newViewingName = newClient.getIsPublic() != null
                        ? getViewingNameWithConnection(conn, newClient.getIsPublic())
                        : null;
                createUpdateAuditRecord(conn, auditStmt, clientId, "Client", "Details",
                        "Updated", "Is Public", oldViewingName, newViewingName, userName);
                hasChanges = true;
            }

            if (hasChanges) {
                conn.commit();
                // system.out.println("✅ ClientDAO.createClientUpdateAuditRecords - COMMITTED
                // successfully");
            } else {
                // system.out.println("ℹ️ ClientDAO.createClientUpdateAuditRecords - No changes
                // detected, skipping audit");
            }

        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            System.err.println("❌ ClientDAO.createClientUpdateAuditRecords - ERROR: " + e.getMessage());
            throw e;
        } finally {
            if (auditStmt != null)
                auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Helper method لإنشاء audit record للـ update مع from و to
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt,
            int clientId, String object, String event, String updateType,
            String field, String fromValue, String toValue, String userName) throws SQLException {

        // system.out.println(" 📝 Update audit: [" + field + "] from '" + fromValue +
        // "' to '" + toValue + "'");

        auditStmt.setInt(1, clientId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, fromValue); // from
        auditStmt.setString(7, toValue); // to
        auditStmt.setString(8, userName);

        auditStmt.executeUpdate();
        // system.out.println(" ✓ Update audit record inserted");

        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Method لإنشاء snapshot جديد في client_audit عند الـ update
     */
    public void createClientUpdateAuditSnapshot(int clientId) throws SQLException {
        // system.out.println("🔍 ClientDAO.createClientUpdateAuditSnapshot - Creating
        // update snapshot for ID: " + clientId);

        String sql = """
                    INSERT INTO client_audit (
                        ID, Parent_ID, Lifecycle, Status, IsPublic, PrimaryName, LongName, Description,
                        CreateDatetime, LastUpdateDatetime, LastUpdate_UserID, RevType
                    )
                    SELECT
                        ID, Parent_ID, Lifecycle, Status, IsPublic, PrimaryName, LongName, Description,
                        CreateDatetime, LastUpdateDatetime, LastUpdate_UserID, 'Updated'
                    FROM client
                    WHERE ID = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            ps.executeUpdate();
            // system.out.println("✅ ClientDAO.createClientUpdateAuditSnapshot - Snapshot
            // created, rows affected: " + affectedRows);
        }
    }

    /**
     * Helper method للمقارنة الآمنة بين القيم
     */
    private boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null)
            return true;
        if (obj1 == null || obj2 == null)
            return false;
        return obj1.equals(obj2);
    }

    // Helper methods التي تستخدم connection موجود لتجنب connection leaks
    private String getClientNameWithConnection(Connection conn, int clientId) throws SQLException {
        String sql = "SELECT PrimaryName FROM client WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getStatusPrimaryNameWithConnection(Connection conn, int statusId) throws SQLException {
        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT PrimaryName FROM status WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("PrimaryName");
                    }
                }
            }
        } catch (SQLException e) {
            // Try primaryname as fallback
            try {
                String sql = "SELECT primaryname FROM status WHERE ID = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, statusId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getString("primaryname");
                        }
                    }
                }
            } catch (SQLException e2) {
                // Ignore and return fallback
            }
        }
        return "Status " + statusId; // Fallback
    }

    private String getClientLifecycleNameWithConnection(Connection conn, int lifecycleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM client_lifecycle WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getViewingNameWithConnection(Connection conn, int viewingId) throws SQLException {
        String sql = "SELECT Name FROM viewing WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, viewingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("Name");
            }
        }
        return null;
    }

    /**
     * حذف العميل مع تسجيل audit records
     */
    public boolean deleteClientWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE client SET DeleteDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();

            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                            INSERT INTO client_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                            VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                        """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Client");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Client");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();

                // الخطوة 3: إنشاء snapshot في client_audit
                String snapshotSql = """
                            INSERT INTO client_audit (
                                ID, Parent_ID, Lifecycle, Status, IsPublic, PrimaryName, LongName, Description,
                                CreateDatetime, LastUpdateDatetime, LastUpdate_UserID, RevType
                            )
                            SELECT
                                ID, Parent_ID, Lifecycle, Status, IsPublic, PrimaryName, LongName, Description,
                                CreateDatetime, LastUpdateDatetime, LastUpdate_UserID, 'Deleted'
                            FROM client
                            WHERE ID = ?
                        """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }

                // system.out.println("✅ Client deleted with audit for ID: " + id);
            }

            conn.commit();
            return affectedRows > 0;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("❌ Error during rollback: " + rollbackEx.getMessage());
                }
            }
            throw e;
        } finally {
            try {
                if (deleteStmt != null)
                    deleteStmt.close();
                if (auditStmt != null)
                    auditStmt.close();
            } catch (SQLException e) {
                System.err.println("❌ Error closing statement: " + e.getMessage());
            }
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("❌ Error closing connection: " + e.getMessage());
            }
        }
    }

    /**
     * Create object_x_people record for stakeholder
     * Always creates a NEW record (no reuse)
     */
    public int createObjectXPeople(Connection conn, java.util.Map<String, Object> stakeholder, int currentUserId)
            throws SQLException {
        String sql = """
                    INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
                    VALUES (NULL, ?, ?, 2, 1, NOW(), ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, (Integer) stakeholder.get("userId")); // ipid
            ps.setInt(2, (Integer) stakeholder.get("roleId")); // RoleID
            ps.setInt(3, currentUserId); // lastupdateuser_id

            ps.executeUpdate();

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    // system.out.println("✅ Generated object_x_people ID: " + newId);
                    return newId;
                } else {
                    throw new SQLException("Creating object_x_people failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Link stakeholder to client via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToClient(Connection conn, int clientId, int objectXPeopleId, int currentUserId)
            throws SQLException {
        String sql = """
                    INSERT INTO client_x_objectxpeople (ClientID, Object_x_ipid, Last_UpdateUser_ID, CreateDatetime)
                    VALUES (?, ?, ?, NOW())
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, currentUserId);
            try {
                ps.executeUpdate();
                // system.out.println("✅ Successfully linked stakeholder to client: ClientID=" +
                // clientId + ", Object_x_ipid=" + objectXPeopleId + ", rows affected=" +
                // rowsAffected);
            } catch (SQLIntegrityConstraintViolationException dup) {
                // system.out.println("⚠️ Link already exists: ClientID=" + clientId + ",
                // Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }
}
