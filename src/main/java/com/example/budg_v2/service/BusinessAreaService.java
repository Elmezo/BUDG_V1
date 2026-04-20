package com.example.budg_v2.service;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.BusinessArea;
import com.example.budg_v2.dao.BusinessAreaDAO;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BusinessAreaService {

    private BusinessAreaDAO businessAreaDAO;

    public BusinessAreaService() {
        this.businessAreaDAO = new BusinessAreaDAO();
    }

    public List<BusinessArea> getAllBusinessAreas() throws SQLException {
        return getAllBusinessAreasForGuest();
    }

    /**
     * Get all business areas for guest users (public, Enterprise only, not deleted)
     */
    private List<BusinessArea> getAllBusinessAreasForGuest() throws SQLException {
        String guestFilter = SegmentAccessService.buildGuestFilterClause("BusinessArea", "ba", "ba.ID");
        if (guestFilter == null) {
            return getAllBusinessAreasUnfiltered();
        }
        String sql = "SELECT ba.ID, ba.Parent_ID, ba.Is_Public, ba.Status, ba.Lifecycle, ba.PrimaryName, ba.Description, " +
                    "ba.CreateDatetime, ba.LastUpdateDatetime, ba.DeleteDatetime, ba.createdby_id, ba.LastUpdate_UserID " +
                    "FROM business_area ba WHERE " + guestFilter + " ORDER BY ba.PrimaryName";
        return executeBusinessAreaQuery(sql);
    }

    public List<BusinessArea> getAllBusinessAreasUnfiltered() throws SQLException {
        String sql = "SELECT ID, Parent_ID, Is_Public, Status, Lifecycle, PrimaryName, Description, " +
                    "CreateDatetime, LastUpdateDatetime, DeleteDatetime, createdby_id, LastUpdate_UserID " +
                    "FROM business_area WHERE deletedatetime IS NULL ORDER BY PrimaryName";
        return executeBusinessAreaQuery(sql);
    }

    private List<BusinessArea> executeBusinessAreaQuery(String sql) throws SQLException {
        List<BusinessArea> businessAreas = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                BusinessArea businessArea = new BusinessArea();
                businessArea.setId(rs.getInt("ID"));
                businessArea.setParentId(rs.getObject("Parent_ID", Integer.class));
                businessArea.setIsPublic(rs.getObject("Is_Public", Integer.class));
                businessArea.setStatus(rs.getObject("Status", Integer.class));
                businessArea.setLifecycle(rs.getObject("Lifecycle", Integer.class));
                businessArea.setPrimaryName(rs.getString("PrimaryName"));
                businessArea.setDescription(rs.getString("Description"));
                businessArea.setCreateDatetime(rs.getTimestamp("CreateDatetime"));
                businessArea.setLastUpdateDatetime(rs.getTimestamp("LastUpdateDatetime"));
                businessArea.setDeleteDatetime(rs.getTimestamp("DeleteDatetime"));
                businessArea.setCreateUserId(rs.getObject("createdby_id", Integer.class));
                businessArea.setLastUpdateUserId(rs.getObject("LastUpdate_UserID", Integer.class));
                businessAreas.add(businessArea);
            }
        }
        return businessAreas;
    }

    /**
     * Return every business area (unfiltered) for the Relationship → Hierarchy
     * view. The caller is responsible for masking sensitive fields via
     * {@link com.example.budg_v2.util.HierarchyAccessMasker}.
     */
    public List<BusinessArea> getAllBusinessAreasForHierarchy() throws SQLException {
        return getAllBusinessAreasUnfiltered();
    }

    /**
     * Get all business areas filtered by user's segment access
     */
    public List<BusinessArea> getAllBusinessAreas(int userId) throws SQLException {
        if (userId <= 0) {
            return getAllBusinessAreasForGuest();
        }
        List<BusinessArea> allBusinessAreas = getAllBusinessAreasUnfiltered();
        if (allBusinessAreas.isEmpty()) {
            return allBusinessAreas;
        }
        
        // Get accessible business area IDs for this user
        List<Integer> allIds = allBusinessAreas.stream().map(BusinessArea::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.getAccessibleObjectIdsInSegments(userId, "BusinessArea", allIds);
        
        // Filter to only accessible business areas
        return allBusinessAreas.stream()
                .filter(ba -> accessibleIds.contains(ba.getId()))
                .collect(Collectors.toList());
    }

    public BusinessArea getBusinessAreaById(int id) throws SQLException {
        String sql = "SELECT ba.ID, ba.Parent_ID, ba.Is_Public, ba.Status, ba.Lifecycle, ba.PrimaryName, ba.Description, " +
                    "ba.CreateDatetime, ba.LastUpdateDatetime, ba.deletedatetime, ba.createdby_id, ba.LastUpdate_UserID, " +
                    "s.primaryname AS statusName " +
                    "FROM business_area ba " +
                    "LEFT JOIN status s ON s.ID = ba.Status " +
                    "WHERE ba.ID = ? AND ba.deletedatetime IS NULL";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BusinessArea businessArea = new BusinessArea();
                    businessArea.setId(rs.getInt("ID"));
                    businessArea.setParentId(rs.getObject("Parent_ID", Integer.class));
                    businessArea.setIsPublic(rs.getObject("Is_Public", Integer.class));
                    businessArea.setStatus(rs.getObject("Status", Integer.class));
                    businessArea.setStatusName(rs.getString("statusName"));
                    businessArea.setLifecycle(rs.getObject("Lifecycle", Integer.class));
                    businessArea.setPrimaryName(rs.getString("PrimaryName"));
                    businessArea.setDescription(rs.getString("Description"));
                    businessArea.setCreateDatetime(rs.getTimestamp("CreateDatetime"));
                    businessArea.setLastUpdateDatetime(rs.getTimestamp("LastUpdateDatetime"));
                    businessArea.setDeleteDatetime(rs.getTimestamp("DeleteDatetime"));
                    businessArea.setCreateUserId(rs.getObject("createdby_id", Integer.class));
                    businessArea.setLastUpdateUserId(rs.getObject("LastUpdate_UserID", Integer.class));
                    
                    return businessArea;
                }
            }
        }
        return null;
    }

    public List<BusinessArea> searchBusinessAreas(String searchTerm) throws SQLException {
        List<BusinessArea> businessAreas = new ArrayList<>();
        String sql = "SELECT ID, Parent_ID, Is_Public, Status, Lifecycle, PrimaryName, Description, " +
                    "CreateDatetime, LastUpdateDatetime, DeleteDatetime, createdby_id, LastUpdate_UserID " +
                    "FROM business_area WHERE deletedatetime IS NULL " +
                    "AND (PrimaryName LIKE ? OR Description LIKE ?) ORDER BY PrimaryName";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            String searchPattern = "%" + searchTerm + "%";
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    BusinessArea businessArea = new BusinessArea();
                    businessArea.setId(rs.getInt("ID"));
                    businessArea.setParentId(rs.getObject("Parent_ID", Integer.class));
                    businessArea.setIsPublic(rs.getObject("Is_Public", Integer.class));
                    businessArea.setStatus(rs.getObject("Status", Integer.class));
                    businessArea.setLifecycle(rs.getObject("Lifecycle", Integer.class));
                    businessArea.setPrimaryName(rs.getString("PrimaryName"));
                    businessArea.setDescription(rs.getString("Description"));
                    businessArea.setCreateDatetime(rs.getTimestamp("CreateDatetime"));
                    businessArea.setLastUpdateDatetime(rs.getTimestamp("LastUpdateDatetime"));
                    businessArea.setDeleteDatetime(rs.getTimestamp("DeleteDatetime"));
                    businessArea.setCreateUserId(rs.getObject("createdby_id", Integer.class));
                    businessArea.setLastUpdateUserId(rs.getObject("LastUpdate_UserID", Integer.class));
                    
                    businessAreas.add(businessArea);
                }
            }
        }
        return businessAreas;
    }

    public int createBusinessArea(BusinessArea businessArea) throws SQLException {
        String sql = "INSERT INTO business_area (Parent_ID, Is_Public, Status, Lifecycle, PrimaryName, Description, " +
                    "CreateDatetime, LastUpdateDatetime, createdby_id, LastUpdate_UserID) " +
                    "VALUES (?, ?, ?, ?, ?, ?, NOW(), NULL, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setObject(1, businessArea.getParentId());
            stmt.setObject(2, businessArea.getIsPublic());
            stmt.setObject(3, businessArea.getStatus());
            stmt.setObject(4, businessArea.getLifecycle());
            stmt.setString(5, businessArea.getPrimaryName());
            stmt.setString(6, businessArea.getDescription());
            stmt.setObject(7, businessArea.getCreateUserId());
            stmt.setObject(8, businessArea.getLastUpdateUserId());

            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int businessAreaId = generatedKeys.getInt(1);
                        
                        // Create audit records after successful creation
                        try {
                            String userName = getUserFullName(businessArea.getLastUpdateUserId());
                            if (userName != null) {
                                businessAreaDAO.createBusinessAreaAuditRecords(businessAreaId, userName, conn);
                                //system.out.println("✅ Business area audit records created for ID: " + businessAreaId);
                            }
                        } catch (Exception e) {
                            System.err.println("❌ Error creating business area audit records: " + e.getMessage());
                            e.printStackTrace();
                        }

                        // Create business area audit snapshot
                        try {
                            businessAreaDAO.createBusinessAreaAuditRecord(businessAreaId);
                            //system.out.println("✅ BusinessAreaService: business_area_audit snapshot created for ID: " + businessAreaId);
                        } catch (Exception e) {
                            System.err.println("❌ Error creating business_area_audit snapshot: " + e.getMessage());
                            e.printStackTrace();
                        }
                        
                        return businessAreaId;
                    }
                }
            }
        }
        return -1;
    }

    public boolean updateBusinessArea(BusinessArea businessArea) throws SQLException {
        // Step 1: Get old values before update
        BusinessArea oldBusinessArea = getBusinessAreaById(businessArea.getId());
        if (oldBusinessArea == null) {
            throw new SQLException("Business area not found with ID: " + businessArea.getId());
        }
        
        String sql = "UPDATE business_area SET Parent_ID = ?, Is_Public = ?, Status = ?, Lifecycle = ?, " +
                    "PrimaryName = ?, Description = ?, LastUpdateDatetime = NOW(), LastUpdate_UserID = ? " +
                    "WHERE ID = ? AND DeleteDatetime IS NULL";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, businessArea.getParentId());
            stmt.setObject(2, businessArea.getIsPublic());
            stmt.setObject(3, businessArea.getStatus());
            stmt.setObject(4, businessArea.getLifecycle());
            stmt.setString(5, businessArea.getPrimaryName());
            stmt.setString(6, businessArea.getDescription());
            stmt.setObject(7, businessArea.getLastUpdateUserId());
            stmt.setInt(8, businessArea.getId());

            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                // Step 2: Create audit records for updates
                try {
                    String userName = getUserFullName(businessArea.getLastUpdateUserId());
                    if (userName != null) {
                        businessAreaDAO.createBusinessAreaUpdateAuditRecords(
                            businessArea.getId(), oldBusinessArea, businessArea, userName);
                        //system.out.println("✅ Business area update audit records created for ID: " + businessArea.getId());
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating business area update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // Step 3: Create snapshot in business_area_audit
                try {
                    businessAreaDAO.createBusinessAreaUpdateAuditSnapshot(businessArea.getId());
                    //system.out.println("✅ BusinessAreaService: business_area_audit update snapshot created for ID: " + businessArea.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating business_area_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            return affectedRows > 0;
        }
    }

    public boolean deleteBusinessArea(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return businessAreaDAO.deleteBusinessAreaWithAudit(id, userName);
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

    public List<BusinessArea> getBusinessAreasForDropdown() throws SQLException {
        List<BusinessArea> businessAreas = new ArrayList<>();
        String sql = "SELECT ID, PrimaryName, Description FROM business_area " +
                    "WHERE DeleteDatetime IS NULL ORDER BY PrimaryName";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                BusinessArea businessArea = new BusinessArea();
                businessArea.setId(rs.getInt("ID"));
                businessArea.setPrimaryName(rs.getString("PrimaryName"));
                businessArea.setDescription(rs.getString("Description"));
                
                businessAreas.add(businessArea);
            }
        }
        return businessAreas;
    }

    /**
     * Get business areas for dropdown filtered by user's segment access
     */
    public List<BusinessArea> getBusinessAreasForDropdown(int userId) throws SQLException {
        List<BusinessArea> allBusinessAreas = getBusinessAreasForDropdown();
        if (allBusinessAreas.isEmpty()) {
            return allBusinessAreas;
        }
        
        // Get accessible business area IDs for this user
        List<Integer> allIds = allBusinessAreas.stream().map(BusinessArea::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.getAccessibleObjectIdsInSegments(userId, "BusinessArea", allIds);
        
        // Filter to only accessible business areas
        return allBusinessAreas.stream()
                .filter(ba -> accessibleIds.contains(ba.getId()))
                .collect(Collectors.toList());
    }

    public boolean isNameUnique(String name, Integer excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM business_area WHERE PrimaryName = ? AND deletedatetime IS NULL";
        if (excludeId != null) {
            sql += " AND ID != ?";
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name);
            if (excludeId != null) {
                stmt.setInt(2, excludeId);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0;
                }
            }
        }
        return true;
    }

    /**
     * Assign creator role and create stakeholder audit after business area creation
     */
    public void assignCreatorRoleAndAudit(int businessAreaId, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int moduleId = com.example.budg_v2.util.DefaultStakeholderUtil.getModuleId(conn, "Business Area");
                List<Integer> rolesToAssign = com.example.budg_v2.util.DefaultStakeholderUtil.getDefaultRolesForCreator(conn, moduleId, userId);

                if (rolesToAssign.isEmpty()) {
                    conn.commit();
                    return;
                }

                String userFullName = getUserFullName(userId);

                for (Integer roleId : rolesToAssign) {
                    try {
                        String insertOXP = """
                                INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                                VALUES (NULL, ?, ?, 2, 1, ?)
                                """;
                        int objectXPeopleId;
                        try (PreparedStatement stmt = conn.prepareStatement(insertOXP, Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setInt(1, userId);
                            stmt.setInt(2, roleId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) {
                                throw new SQLException("Failed to insert into object_x_people");
                            }
                            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                                if (generatedKeys.next()) {
                                    objectXPeopleId = generatedKeys.getInt(1);
                                } else {
                                    throw new SQLException("Failed to get generated key for object_x_people");
                                }
                            }
                        }

                        String insertBAXOP = """
                                INSERT INTO businessarea_x_objectxpeople (Object_x_ipid, BusinessAreaID, Last_UpdateUser_ID)
                                VALUES (?, ?, ?)
                                """;
                        try (PreparedStatement stmt = conn.prepareStatement(insertBAXOP)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, businessAreaId);
                            stmt.setInt(3, userId);
                            if (stmt.executeUpdate() == 0) {
                                throw new SQLException("Failed to insert into businessarea_x_objectxpeople");
                            }
                        }

                        com.example.budg_v2.util.RoleNotificationHelper.createNotificationAfterCreatorRoleAssigned(
                                "Business Area", businessAreaId, userId, roleId, objectXPeopleId, conn);

                        if (userFullName != null) {
                            businessAreaDAO.createStakeholderAuditRecords(businessAreaId, userFullName, userFullName, roleId);
                        }
                    } catch (SQLException e) {
                        System.err.println("❌ Error assigning default role " + roleId + " to creator: " + e.getMessage());
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Helper method to get user full name for audit records
     */
    private String getUserFullName(Integer userId) {
        if (userId == null) return null;
        
        try {
            String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("fullName");
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting user full name: " + e.getMessage());
        }
        return null;
    }
}
