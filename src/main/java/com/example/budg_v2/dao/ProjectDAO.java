package com.example.budg_v2.dao;

import com.example.budg_v2.audit.AuditHistoryWriter;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Project;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ProjectDAO {
    
    private static final String SELECT_ALL = "SELECT * FROM project WHERE deletedatetime IS NULL ORDER BY primaryname";
    private static final String SELECT_BY_ID = "SELECT p.*, s.primaryname AS statusName FROM project p " +
            "LEFT JOIN status s ON s.ID = p.status " +
            "WHERE p.id = ? AND p.deletedatetime IS NULL";
    private static final String SELECT_BY_REFNUMBER = "SELECT * FROM project WHERE LOWER(refnumber) = LOWER(?) AND deletedatetime IS NULL";
    private static final String SELECT_BY_REFNUMBER_EXCLUDE_ID = "SELECT * FROM project WHERE LOWER(refnumber) = LOWER(?) AND id != ? AND deletedatetime IS NULL";
    private static final String SELECT_BY_PRIMARYNAME = "SELECT * FROM project WHERE primaryname = ? AND deletedatetime IS NULL";
    private static final String SEARCH = "SELECT * FROM project WHERE (primaryname LIKE ? OR description LIKE ?) AND deletedatetime IS NULL ORDER BY primaryname";
    private static final String SELECT_FOR_DROPDOWN = "SELECT id, primaryname, description FROM project ORDER BY primaryname";
    private static final String SELECT_FOR_PARENT_PICKER = "SELECT id, primaryname, description FROM project WHERE id != ? ORDER BY primaryname";
    private static final String INSERT = "INSERT INTO project (id, parentid, is_public, rag, classification, status, lifecycle_status, project_type, refnumber, primaryname, description, startdate, enddate, createdatetime, lastupdatedatetime, createdby_id, lastupdateuser_id) VALUES ((SELECT COALESCE(MAX(id), 0) + 1 FROM project p), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?)";
    private static final String UPDATE = "UPDATE project SET parentid = ?, is_public = ?, rag = ?, classification = ?, status = ?, lifecycle_status = ?, project_type = ?, refnumber = ?, primaryname = ?, description = ?, startdate = ?, enddate = ?, lastupdatedatetime = ?, lastupdateuser_id = ? WHERE id = ?";
    private static final String SOFT_DELETE = "UPDATE project SET deletedatetime = NOW() WHERE id = ?";

    public List<Project> getAllProjects() throws SQLException {
        return getAllProjectsForGuest();
    }

    /**
     * Get all projects for guest users (public, Enterprise only, not deleted)
     */
    public List<Project> getAllProjectsForGuest() throws SQLException {
        String guestFilter = com.example.budg_v2.service.SegmentAccessService.buildGuestFilterClause("Project", "p", "p.id");
        if (guestFilter == null) {
            return getAllProjectsUnfiltered();
        }
        String sql = "SELECT p.* FROM project p WHERE " + guestFilter + " ORDER BY p.primaryname";
        List<Project> projects = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                projects.add(mapResultSetToProject(rs));
            }
        }
        return projects;
    }

    public List<Project> getAllProjectsUnfiltered() throws SQLException {
        List<Project> projects = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                projects.add(mapResultSetToProject(rs));
            }
        }
        return projects;
    }

    /**
     * Get all projects filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to.
     */
    public List<Project> getAllProjectsBySegmentAccess(int userId) throws SQLException {
        String segmentFilter = com.example.budg_v2.service.SegmentAccessService
                .buildSelectedSegmentFilterClause(userId, "Project", "p.id");
        
        String sql = "SELECT * FROM project p WHERE p.deletedatetime IS NULL AND " + 
                     segmentFilter + " ORDER BY p.primaryname";
        
        List<Project> projects = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                projects.add(mapResultSetToProject(rs));
            }
        }
        //system.out.println("📋 Found " + projects.size() + " accessible projects for user " + userId);
        return projects;
    }

    public Project getProjectById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {
            
            pstmt.setInt(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToProject(rs);
                }
            }
        }
        
        return null;
    }

    public List<Project> searchProjects(String searchTerm) throws SQLException {
        List<Project> projects = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {
            
            String searchPattern = "%" + searchTerm + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    projects.add(mapResultSetToProject(rs));
                }
            }
        }
        
        return projects;
    }

    public List<Project> getAllProjectsForDropdown() throws SQLException {
        return getAllProjectsForDropdownForGuest();
    }

    /**
     * Get projects for dropdown for guest users (public, Enterprise only, not deleted)
     */
    private List<Project> getAllProjectsForDropdownForGuest() throws SQLException {
        String guestFilter = com.example.budg_v2.service.SegmentAccessService.buildGuestFilterClause("Project", "p", "p.id");
        if (guestFilter == null) {
            return getAllProjectsForDropdownUnfiltered();
        }
        String sql = "SELECT p.id, p.primaryname, p.description FROM project p WHERE " + guestFilter + " ORDER BY p.primaryname";
        List<Project> projects = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Project project = new Project();
                project.setId(rs.getInt("id"));
                project.setPrimaryName(rs.getString("primaryname"));
                project.setDescription(rs.getString("description"));
                projects.add(project);
            }
        }
        return projects;
    }

    private List<Project> getAllProjectsForDropdownUnfiltered() throws SQLException {
        List<Project> projects = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_FOR_DROPDOWN);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Project project = new Project();
                project.setId(rs.getInt("id"));
                project.setPrimaryName(rs.getString("primaryname"));
                project.setDescription(rs.getString("description"));
                projects.add(project);
            }
        }
        return projects;
    }

    public List<Project> getProjectsForParentPicker(int excludeId) throws SQLException {
        List<Project> projects = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_FOR_PARENT_PICKER)) {
            
            pstmt.setInt(1, excludeId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Project project = new Project();
                    project.setId(rs.getInt("id"));
                    project.setPrimaryName(rs.getString("primaryname"));
                    project.setDescription(rs.getString("description"));
                    projects.add(project);
                }
            }
        }
        
        return projects;
    }

    public Project createProject(Project project) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return createProject(project, conn);
        }
    }

    /**
     * Create project using the provided connection (e.g. for bulk upload).
     * Caller is responsible for commit/rollback. Does not close the connection.
     */
    public Project createProject(Project project, Connection conn) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(INSERT)) {
            pstmt.setObject(1, project.getParentId());
            pstmt.setObject(2, project.getIsPublic());
            pstmt.setObject(3, project.getRag());
            pstmt.setObject(4, project.getClassification());
            pstmt.setObject(5, project.getStatus());
            pstmt.setObject(6, project.getLifecycleStatus());
            pstmt.setObject(7, project.getProjectType());
            pstmt.setString(8, project.getRefNumber());
            pstmt.setString(9, project.getPrimaryName());
            pstmt.setString(10, project.getDescription());
            pstmt.setObject(11, project.getStartDate());
            pstmt.setObject(12, project.getEndDate());
            pstmt.setObject(13, project.getCreatedById());
            pstmt.setObject(14, project.getCreatedById()); // lastupdateuser_id same as createdby_id on insert

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating project failed, no rows affected.");
            }

            // Find the newly created project by its unique combination
            try (PreparedStatement idStmt = conn.prepareStatement(
                    "SELECT id FROM project WHERE primaryname = ? AND description = ? AND createdatetime >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) ORDER BY id DESC LIMIT 1")) {
                idStmt.setString(1, project.getPrimaryName());
                idStmt.setString(2, project.getDescription());
                try (ResultSet idRs = idStmt.executeQuery()) {
                    if (idRs.next()) {
                        int generatedId = idRs.getInt(1);
                        project.setId(generatedId);
                        return project;
                    } else {
                        throw new SQLException("Creating project failed, no ID obtained.");
                    }
                }
            }
        }
    }

    public boolean updateProject(Project project) throws SQLException {
        // الخطوة 1: احصل على البيانات القديمة قبل التحديث
        Project oldProject = getProjectById(project.getId());
        if (oldProject == null) {
            throw new SQLException("Project not found with ID: " + project.getId());
        }
        
        // Check if there are actual changes
        boolean hasChanges = !Objects.equals(oldProject.getParentId(), project.getParentId()) ||
                            !Objects.equals(oldProject.getIsPublic(), project.getIsPublic()) ||
                            !Objects.equals(oldProject.getRag(), project.getRag()) ||
                            !Objects.equals(oldProject.getClassification(), project.getClassification()) ||
                            !Objects.equals(oldProject.getStatus(), project.getStatus()) ||
                            !Objects.equals(oldProject.getLifecycleStatus(), project.getLifecycleStatus()) ||
                            !Objects.equals(oldProject.getProjectType(), project.getProjectType()) ||
                            !Objects.equals(oldProject.getRefNumber(), project.getRefNumber()) ||
                            !Objects.equals(oldProject.getPrimaryName(), project.getPrimaryName()) ||
                            !Objects.equals(oldProject.getDescription(), project.getDescription()) ||
                            !Objects.equals(oldProject.getStartDate(), project.getStartDate()) ||
                            !Objects.equals(oldProject.getEndDate(), project.getEndDate());
        
        // Determine LastUpdateDatetime and LastUpdateUserId based on whether there are changes
        Timestamp lastUpdateDatetime;
        Integer lastUpdateUserId;
        Timestamp now = new Timestamp(System.currentTimeMillis());
        
        if (!hasChanges) {
            // If no changes, check if object has been edited before
            Integer oldLastUpdateUserId = oldProject.getLastUpdateUserId();
            Timestamp oldLastUpdateDatetime = oldProject.getLastUpdateDateTime();
            
            System.out.println("🔍 ProjectDAO.updateProject - No changes detected");
            System.out.println("   oldLastUpdateUserId: " + oldLastUpdateUserId);
            System.out.println("   oldLastUpdateDatetime: " + oldLastUpdateDatetime);
            System.out.println("   createdById: " + oldProject.getCreatedById());
            System.out.println("   createDateTime: " + oldProject.getCreateDateTime());
            
            if (oldLastUpdateUserId != null && oldLastUpdateDatetime != null) {
                // Object has been edited before - keep the previous last update values
                lastUpdateUserId = oldLastUpdateUserId;
                lastUpdateDatetime = oldLastUpdateDatetime;
                System.out.println("   ✅ Keeping previous Last Updated values");
            } else {
                // Object has never been edited - use CreatedBy and CreatedDatetime
                lastUpdateUserId = oldProject.getCreatedById();
                Timestamp createDatetime = oldProject.getCreateDateTime();
                if (createDatetime != null) {
                    lastUpdateDatetime = createDatetime;
                } else {
                    // Fallback to current time if createDatetime is null
                    lastUpdateDatetime = now;
                }
                System.out.println("   ✅ Setting Last Updated to CreatedBy/CreatedDate (first edit with no changes)");
                System.out.println("   lastUpdateUserId: " + lastUpdateUserId);
                System.out.println("   lastUpdateDatetime: " + lastUpdateDatetime);
            }
        } else {
            // There are changes - use current timestamp and provided user ID
            lastUpdateDatetime = now;
            lastUpdateUserId = project.getLastUpdateUserId();
            System.out.println("🔍 ProjectDAO.updateProject - Changes detected, using current timestamp");
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setObject(1, project.getParentId());
            pstmt.setObject(2, project.getIsPublic());
            pstmt.setObject(3, project.getRag());
            pstmt.setObject(4, project.getClassification());
            pstmt.setObject(5, project.getStatus());
            pstmt.setObject(6, project.getLifecycleStatus());
            pstmt.setObject(7, project.getProjectType());
            pstmt.setString(8, project.getRefNumber());
            pstmt.setString(9, project.getPrimaryName());
            pstmt.setString(10, project.getDescription());
            pstmt.setObject(11, project.getStartDate());
            pstmt.setObject(12, project.getEndDate());
            pstmt.setTimestamp(13, lastUpdateDatetime);
            pstmt.setObject(14, lastUpdateUserId);
            pstmt.setInt(15, project.getId());

            System.out.println("🔍 ProjectDAO.updateProject - Executing UPDATE with:");
            System.out.println("   lastUpdateDatetime: " + lastUpdateDatetime);
            System.out.println("   lastUpdateUserId: " + lastUpdateUserId);
            System.out.println("   projectId: " + project.getId());

            int affectedRows = pstmt.executeUpdate();
            System.out.println("   ✅ Update executed, affected rows: " + affectedRows);
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات
                try {
                    // Use the actual lastUpdateUserId that was set (CreatedBy if no changes, or provided user if changes)
                    Integer userId = lastUpdateUserId;
                    String userName = "System"; // Default fallback
                    if (userId != null) {
                        String fullName = getPersonFullName(userId);
                        if (fullName != null && !fullName.trim().isEmpty()) {
                            userName = fullName;
                        } else {
                            // إذا لم نجد الاسم، استخدم User ID كبديل أفضل من "System"
                            userName = "User ID: " + userId;
                        }
                    }
                    
                    createProjectUpdateAuditRecords(project.getId(), oldProject, project, userName);
                    //system.out.println("✅ Project update audit records created for ID: " + project.getId() + " with author: " + userName);
                } catch (Exception e) {
                    System.err.println("❌ Error creating project update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // الخطوة 3: إنشاء snapshot جديد في project_audit
                try {
                    createProjectUpdateAuditSnapshot(project.getId());
                    //system.out.println("✅ ProjectDAO: project_audit update snapshot created for ID: " + project.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating project_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return affectedRows > 0;
        }
    }

    public boolean deleteProject(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {
            
            pstmt.setInt(1, id);
            
            return pstmt.executeUpdate() > 0;
        }
    }

    private Project mapResultSetToProject(ResultSet rs) throws SQLException {
        Project project = new Project();
        
        project.setId(rs.getInt("id"));
        project.setParentId(rs.getInt("parentid"));
        project.setIsPublic(rs.getInt("is_public"));
        project.setRag(rs.getInt("rag"));
        project.setClassification(rs.getInt("classification"));
        project.setStatus(rs.getInt("status"));
        try {
            project.setStatusName(rs.getString("statusName"));
        } catch (SQLException e) {
            project.setStatusName(null);
        }
        project.setLifecycleStatus(rs.getInt("lifecycle_status"));
        project.setProjectType(rs.getInt("project_type"));
        project.setRefNumber(rs.getString("refnumber"));
        project.setPrimaryName(rs.getString("primaryname"));
        project.setDescription(rs.getString("description"));
        project.setStartDate(rs.getTimestamp("startdate"));
        project.setEndDate(rs.getTimestamp("enddate"));
        project.setCreateDateTime(rs.getTimestamp("createdatetime"));
        project.setLastUpdateDateTime(rs.getTimestamp("lastupdatedatetime"));
        project.setDeletedDateTime(rs.getTimestamp("deletedatetime"));
        project.setCreatedById(rs.getInt("createdby_id"));
        // Use getObject to properly handle NULL values (getInt returns 0 for NULL)
        Object lastUpdateUserIdObj = rs.getObject("lastupdateuser_id");
        if (lastUpdateUserIdObj != null) {
            project.setLastUpdateUserId(((Number) lastUpdateUserIdObj).intValue());
        } else {
            project.setLastUpdateUserId(null);
        }
        
        return project;
    }

    // Project hierarchy method - similar to policy hierarchy (includes siblings)
    public List<java.util.Map<String, Object>> getProjectHierarchy(int projectId) throws SQLException {
        List<java.util.Map<String, Object>> hierarchy = new ArrayList<>();
        
        // Get complete hierarchy: parents (ancestors) + current + siblings + children (descendants) + siblings' children
        // First, verify the project exists and get its parentid
        boolean projectExists = false;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT parentid FROM project WHERE id = ? AND deletedatetime IS NULL")) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    projectExists = true;
                    rs.getObject("parentid", Integer.class);
                } else {
                    return new ArrayList<>();
                }
            }
        }
        
        if (!projectExists) {
            return new ArrayList<>();
        }
        
        // Use SQL query similar to policy hierarchy - include all required columns and siblings
        String sql = "WITH RECURSIVE " +
                // Get current project's parent ID
                "current_parent AS (" +
                "    SELECT parentid FROM project WHERE id = ? AND deletedatetime IS NULL " +
                "), " +
                // Get all ancestors (parents up the hierarchy)
                "ancestors AS (" +
                "    SELECT p.id, p.parentid, p.primaryname, p.description, p.refnumber, p.rag, p.lifecycle_status, p.startdate, p.enddate, " +
                "           pt.primaryname as typeName, pl.primaryname as lifecycleName, pr.primaryname as ragName, -1 as level, 'ancestor' as relation " +
                "    FROM project p " +
                "    CROSS JOIN current_parent cp " +
                "    LEFT JOIN project_type pt ON pt.id = p.project_type " +
                "    LEFT JOIN project_lifecycle pl ON pl.id = p.lifecycle_status " +
                "    LEFT JOIN project_rag pr ON pr.id = p.rag " +
                "    WHERE p.id = cp.parentid AND p.id IS NOT NULL AND p.deletedatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT p.id, p.parentid, p.primaryname, p.description, p.refnumber, p.rag, p.lifecycle_status, p.startdate, p.enddate, " +
                "           pt.primaryname as typeName, pl.primaryname as lifecycleName, pr.primaryname as ragName, a.level - 1, 'ancestor' " +
                "    FROM project p " +
                "    INNER JOIN ancestors a ON p.id = a.parentid " +
                "    LEFT JOIN project_type pt ON pt.id = p.project_type " +
                "    LEFT JOIN project_lifecycle pl ON pl.id = p.lifecycle_status " +
                "    LEFT JOIN project_rag pr ON pr.id = p.rag " +
                "    WHERE a.level > -10 AND p.deletedatetime IS NULL " + // Prevent infinite recursion
                "), " +
                // Get siblings (other projects with the same parentid as current)
                "siblings AS (" +
                "    SELECT p.id, p.parentid, p.primaryname, p.description, p.refnumber, p.rag, p.lifecycle_status, p.startdate, p.enddate, " +
                "           pt.primaryname as typeName, pl.primaryname as lifecycleName, pr.primaryname as ragName, 0 as level, 'sibling' as relation " +
                "    FROM project p " +
                "    CROSS JOIN current_parent cp " +
                "    LEFT JOIN project_type pt ON pt.id = p.project_type " +
                "    LEFT JOIN project_lifecycle pl ON pl.id = p.lifecycle_status " +
                "    LEFT JOIN project_rag pr ON pr.id = p.rag " +
                "    WHERE cp.parentid IS NOT NULL AND p.parentid = cp.parentid " +
                "    AND p.id != ? AND p.deletedatetime IS NULL " +
                "), " +
                // Get all descendants (children down the hierarchy)
                "descendants AS (" +
                "    SELECT p.id, p.parentid, p.primaryname, p.description, p.refnumber, p.rag, p.lifecycle_status, p.startdate, p.enddate, " +
                "           pt.primaryname as typeName, pl.primaryname as lifecycleName, pr.primaryname as ragName, 1 as level, 'descendant' as relation " +
                "    FROM project p " +
                "    LEFT JOIN project_type pt ON pt.id = p.project_type " +
                "    LEFT JOIN project_lifecycle pl ON pl.id = p.lifecycle_status " +
                "    LEFT JOIN project_rag pr ON pr.id = p.rag " +
                "    WHERE p.parentid = ? AND p.deletedatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT p.id, p.parentid, p.primaryname, p.description, p.refnumber, p.rag, p.lifecycle_status, p.startdate, p.enddate, " +
                "           pt.primaryname as typeName, pl.primaryname as lifecycleName, pr.primaryname as ragName, d.level + 1, 'descendant' " +
                "    FROM project p " +
                "    INNER JOIN descendants d ON p.parentid = d.id " +
                "    LEFT JOIN project_type pt ON pt.id = p.project_type " +
                "    LEFT JOIN project_lifecycle pl ON pl.id = p.lifecycle_status " +
                "    LEFT JOIN project_rag pr ON pr.id = p.rag " +
                "    WHERE d.level < 10 AND p.deletedatetime IS NULL " + // Prevent infinite recursion
                "), " +
                // Get children of siblings (siblings' descendants)
                "sibling_children AS (" +
                "    SELECT p.id, p.parentid, p.primaryname, p.description, p.refnumber, p.rag, p.lifecycle_status, p.startdate, p.enddate, " +
                "           pt.primaryname as typeName, pl.primaryname as lifecycleName, pr.primaryname as ragName, 1 as level, 'sibling_child' as relation " +
                "    FROM project p " +
                "    LEFT JOIN project_type pt ON pt.id = p.project_type " +
                "    LEFT JOIN project_lifecycle pl ON pl.id = p.lifecycle_status " +
                "    LEFT JOIN project_rag pr ON pr.id = p.rag " +
                "    WHERE p.parentid IN (SELECT id FROM siblings) AND p.deletedatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT p.id, p.parentid, p.primaryname, p.description, p.refnumber, p.rag, p.lifecycle_status, p.startdate, p.enddate, " +
                "           pt.primaryname as typeName, pl.primaryname as lifecycleName, pr.primaryname as ragName, sc.level + 1, 'sibling_child' " +
                "    FROM project p " +
                "    INNER JOIN sibling_children sc ON p.parentid = sc.id " +
                "    LEFT JOIN project_type pt ON pt.id = p.project_type " +
                "    LEFT JOIN project_lifecycle pl ON pl.id = p.lifecycle_status " +
                "    LEFT JOIN project_rag pr ON pr.id = p.rag " +
                "    WHERE sc.level < 10 AND p.deletedatetime IS NULL " + // Prevent infinite recursion
                ") " +
                // Combine: ancestors + current + siblings + descendants + siblings' children
                "SELECT id, parentid, primaryname, description, refnumber, rag, lifecycle_status, startdate, enddate, " +
                "       typeName, lifecycleName, ragName, level, relation FROM (" +
                "    SELECT p.id, p.parentid, p.primaryname, p.description, p.refnumber, p.rag, p.lifecycle_status, p.startdate, p.enddate, " +
                "           pt.primaryname as typeName, pl.primaryname as lifecycleName, pr.primaryname as ragName, 0 as level, 'current' as relation " +
                "    FROM project p " +
                "    LEFT JOIN project_type pt ON pt.id = p.project_type " +
                "    LEFT JOIN project_lifecycle pl ON pl.id = p.lifecycle_status " +
                "    LEFT JOIN project_rag pr ON pr.id = p.rag " +
                "    WHERE p.id = ? AND p.deletedatetime IS NULL " +
                "    UNION ALL " +
                "    SELECT id, parentid, primaryname, description, refnumber, rag, lifecycle_status, startdate, enddate, " +
                "           typeName, lifecycleName, ragName, level, relation FROM ancestors " +
                "    UNION ALL " +
                "    SELECT id, parentid, primaryname, description, refnumber, rag, lifecycle_status, startdate, enddate, " +
                "           typeName, lifecycleName, ragName, level, relation FROM siblings " +
                "    UNION ALL " +
                "    SELECT id, parentid, primaryname, description, refnumber, rag, lifecycle_status, startdate, enddate, " +
                "           typeName, lifecycleName, ragName, level, relation FROM descendants " +
                "    UNION ALL " +
                "    SELECT id, parentid, primaryname, description, refnumber, rag, lifecycle_status, startdate, enddate, " +
                "           typeName, lifecycleName, ragName, level, relation FROM sibling_children " +
                ") combined " +
                "ORDER BY level, primaryname";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, projectId); // For current_parent query
            stmt.setInt(2, projectId); // For siblings query (exclude current)
            stmt.setInt(3, projectId); // For descendants query  
            stmt.setInt(4, projectId); // For current project query
            
            System.out.println("🔍 ProjectDAO.getProjectHierarchy - Executing query for projectId: " + projectId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> item = new java.util.HashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("parentId", rs.getObject("parentid", Integer.class));
                    item.put("name", rs.getString("primaryname"));
                    item.put("primaryname", rs.getString("primaryname"));
                    item.put("displayName", rs.getString("primaryname"));
                    item.put("description", rs.getString("description"));
                    item.put("refnumber", rs.getString("refnumber"));
                    item.put("rag", rs.getObject("rag", Integer.class));
                    item.put("ragName", rs.getString("ragName"));
                    item.put("lifecycle_status", rs.getObject("lifecycle_status", Integer.class));
                    item.put("lifecycleName", rs.getString("lifecycleName"));
                    item.put("startdate", rs.getTimestamp("startdate"));
                    item.put("enddate", rs.getTimestamp("enddate"));
                    item.put("typeName", rs.getString("typeName"));
                    item.put("level", rs.getInt("level"));
                    item.put("relation", rs.getString("relation"));
                    
                    hierarchy.add(item);
                }
            }
            System.out.println("🔍 ProjectDAO.getProjectHierarchy - Found " + hierarchy.size() + " items in hierarchy");
        } catch (SQLException e) {
            System.err.println("❌ ProjectDAO.getProjectHierarchy - SQL Error: " + e.getMessage());
            System.err.println("❌ SQL State: " + e.getSQLState());
            System.err.println("❌ Error Code: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }
        
        return hierarchy;
    }

    // Project relationships method - bidirectional (shows relationships where project is source OR target)
    public List<java.util.Map<String, Object>> getProjectRelationshipsBySourceId(int projectId) throws SQLException {
        List<java.util.Map<String, Object>> relationships = new ArrayList<>();
        
        // Query relationships where current project is SOURCE (forward direction)
        String forwardSql = "SELECT pxp.id, pxp.sourceprojectid, pxp.targetprojectid, pxp.relationtype, pxp.description, " +
                    "COALESCE(p.primaryname, p.description, '') as otherProjectName, " +
                    "COALESCE(p.refnumber, '') as otherProjectRef, " +
                    "COALESCE(pt.primaryname, '') as otherProjectType, " +
                    "COALESCE(prt.primaryname, '') as relationTypeName, " +
                    "COALESCE(prt.reversename, prt.primaryname, '') as relationTypeReverseName, " +
                    "'forward' as direction " +
                    "FROM project_x_project pxp " +
                    "LEFT JOIN project source_p ON source_p.id = pxp.sourceprojectid " +
                    "LEFT JOIN project p ON p.id = pxp.targetprojectid " +
                    "LEFT JOIN project_type pt ON pt.id = p.project_type " +
                    "LEFT JOIN project_x_project_relationtype prt ON prt.id = pxp.relationtype " +
                    "WHERE pxp.sourceprojectid = ? " +
                    "AND (source_p.deletedatetime IS NULL OR source_p.deletedatetime = '1970-01-01 00:00:00') " +
                    "AND (p.deletedatetime IS NULL OR p.deletedatetime = '1970-01-01 00:00:00' OR p.id IS NULL)";
        
        // Query relationships where current project is TARGET (reverse direction)
        String reverseSql = "SELECT pxp.id, pxp.sourceprojectid, pxp.targetprojectid, pxp.relationtype, pxp.description, " +
                    "COALESCE(p.primaryname, p.description, '') as otherProjectName, " +
                    "COALESCE(p.refnumber, '') as otherProjectRef, " +
                    "COALESCE(pt.primaryname, '') as otherProjectType, " +
                    "COALESCE(prt.primaryname, '') as relationTypeName, " +
                    "COALESCE(prt.reversename, prt.primaryname, '') as relationTypeReverseName, " +
                    "'reverse' as direction " +
                    "FROM project_x_project pxp " +
                    "LEFT JOIN project target_p ON target_p.id = pxp.targetprojectid " +
                    "LEFT JOIN project p ON p.id = pxp.sourceprojectid " +
                    "LEFT JOIN project_type pt ON pt.id = p.project_type " +
                    "LEFT JOIN project_x_project_relationtype prt ON prt.id = pxp.relationtype " +
                    "WHERE pxp.targetprojectid = ? " +
                    "AND (target_p.deletedatetime IS NULL OR target_p.deletedatetime = '1970-01-01 00:00:00') " +
                    "AND (p.deletedatetime IS NULL OR p.deletedatetime = '1970-01-01 00:00:00' OR p.id IS NULL)";
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get forward relationships
            try (PreparedStatement stmt = conn.prepareStatement(forwardSql)) {
                stmt.setInt(1, projectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        java.util.Map<String, Object> relationship = new java.util.HashMap<>();
                        relationship.put("id", rs.getInt("id"));
                        relationship.put("sourceProjectId", rs.getInt("sourceprojectid"));
                        Integer targetProjectId = rs.getObject("targetprojectid", Integer.class);
                        relationship.put("targetProjectId", targetProjectId);
                        relationship.put("relationType", rs.getObject("relationtype", Integer.class));
                        relationship.put("description", rs.getString("description"));
                        relationship.put("otherProjectId", targetProjectId);
                        relationship.put("otherProjectName", rs.getString("otherProjectName"));
                        relationship.put("otherProjectRef", rs.getString("otherProjectRef"));
                        relationship.put("otherProjectType", rs.getString("otherProjectType"));
                        relationship.put("relationTypeName", rs.getString("relationTypeName"));
                        relationship.put("direction", "forward");
                        relationships.add(relationship);
                    }
                }
            }
            
            // Get reverse relationships
            try (PreparedStatement stmt = conn.prepareStatement(reverseSql)) {
                stmt.setInt(1, projectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        java.util.Map<String, Object> relationship = new java.util.HashMap<>();
                        relationship.put("id", rs.getInt("id"));
                        relationship.put("sourceProjectId", rs.getInt("sourceprojectid"));
                        Integer targetProjectId = rs.getObject("targetprojectid", Integer.class);
                        relationship.put("targetProjectId", targetProjectId);
                        relationship.put("relationType", rs.getObject("relationtype", Integer.class));
                        relationship.put("description", rs.getString("description"));
                        relationship.put("otherProjectId", rs.getInt("sourceprojectid")); // In reverse, other project is the source
                        relationship.put("otherProjectName", rs.getString("otherProjectName"));
                        relationship.put("otherProjectRef", rs.getString("otherProjectRef"));
                        relationship.put("otherProjectType", rs.getString("otherProjectType"));
                        // Use reverse name if available, otherwise use the regular name
                        String reverseName = rs.getString("relationTypeReverseName");
                        relationship.put("relationTypeName", (reverseName != null && !reverseName.trim().isEmpty()) ? reverseName : rs.getString("relationTypeName"));
                        relationship.put("direction", "reverse");
                        relationships.add(relationship);
                    }
                }
            }
        }
        
        return relationships;
    }

    // Project relationship deletion method
    public boolean deleteProjectRelationship(int relationshipId) throws SQLException {
        String sql = "DELETE FROM project_x_project WHERE id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, relationshipId);
            int result = stmt.executeUpdate();
            return result > 0;
        }
    }
    
    public java.util.Map<String, Object> getProjectRelationshipById(int relationshipId) throws SQLException {
        String sql = "SELECT id, sourceprojectid, targetprojectid, relationtype, description FROM project_x_project WHERE id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, relationshipId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    java.util.Map<String, Object> relationship = new java.util.HashMap<>();
                    relationship.put("id", rs.getInt("id"));
                    relationship.put("sourceProjectId", rs.getInt("sourceprojectid"));
                    relationship.put("targetProjectId", rs.getObject("targetprojectid", Integer.class));
                    relationship.put("relationType", rs.getObject("relationtype", Integer.class));
                    relationship.put("description", rs.getString("description"));
                    return relationship;
                }
            }
        }
        return null;
    }
    
    // Get project_x_project relation types
    public List<java.util.Map<String, Object>> getProjectRelationTypes() throws SQLException {
        List<java.util.Map<String, Object>> relationTypes = new ArrayList<>();
        String sql = "SELECT id, primaryname, description FROM project_x_project_relationtype ORDER BY primaryname";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                java.util.Map<String, Object> relationType = new java.util.HashMap<>();
                relationType.put("id", rs.getInt("id"));
                relationType.put("primaryname", rs.getString("primaryname"));
                relationType.put("description", rs.getString("description"));
                relationTypes.add(relationType);
            }
        }
        
        return relationTypes;
    }
    
    // Create project relationship
    public boolean createProjectRelationship(int sourceProjectId, int targetProjectId, int relationType, String description) throws SQLException {
        String sql = "INSERT INTO project_x_project (sourceprojectid, targetprojectid, relationtype, description) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, sourceProjectId);
            stmt.setInt(2, targetProjectId);
            stmt.setInt(3, relationType);
            stmt.setString(4, description);
            
            int result = stmt.executeUpdate();
            return result > 0;
        }
    }
    
    public boolean updateProjectRelationship(int relationshipId, int targetProjectId, int relationType, String description) throws SQLException {
        String sql = "UPDATE project_x_project SET targetprojectid = ?, relationtype = ?, description = ? WHERE id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, targetProjectId);
            stmt.setInt(2, relationType);
            stmt.setString(3, description);
            stmt.setInt(4, relationshipId);
            
            int result = stmt.executeUpdate();
            return result > 0;
        }
    }

    // Project types method
    public List<com.example.budg_v2.model.ProjectType> getProjectTypes() throws SQLException {
        String sql = "SELECT ID, PrimaryName, Description FROM project_type ORDER BY PrimaryName";
        List<com.example.budg_v2.model.ProjectType> projectTypes = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                com.example.budg_v2.model.ProjectType projectType = new com.example.budg_v2.model.ProjectType();
                projectType.setId(rs.getInt("ID"));
                projectType.setPrimaryName(rs.getString("PrimaryName"));
                projectType.setDescription(rs.getString("Description"));
                projectTypes.add(projectType);
            }
        }
        
        //system.out.println("ProjectDAO: Retrieved " + projectTypes.size() + " project types");
        return projectTypes;
    }

    // Project lifecycle statuses method
    public List<com.example.budg_v2.model.ProjectLifecycle> getProjectLifecycleStatuses() throws SQLException {
        String sql = "SELECT ID, PrimaryName, Description FROM project_lifecycle ORDER BY PrimaryName";
        List<com.example.budg_v2.model.ProjectLifecycle> lifecycle = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                com.example.budg_v2.model.ProjectLifecycle projectLifecycle = new com.example.budg_v2.model.ProjectLifecycle();
                projectLifecycle.setId(rs.getInt("ID"));
                projectLifecycle.setPrimaryName(rs.getString("PrimaryName"));
                projectLifecycle.setDescription(rs.getString("Description"));
                lifecycle.add(projectLifecycle);
            }
        }
        
        //system.out.println("ProjectDAO: Retrieved " + lifecycle.size() + " project lifecycle statuses");
        return lifecycle;
    }

    // RefNumber uniqueness methods
    public Project getProjectByRefNumber(String refNumber) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER)) {
            
            pstmt.setString(1, refNumber);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToProject(rs);
                }
            }
        }
        
        return null;
    }

    public Project getProjectByRefNumberExcludingId(String refNumber, int excludeId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER_EXCLUDE_ID)) {
            
            pstmt.setString(1, refNumber);
            pstmt.setInt(2, excludeId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToProject(rs);
                }
            }
        }
        
        return null;
    }

    public Project getProjectByPrimaryName(String primaryName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_PRIMARYNAME)) {
            
            pstmt.setString(1, primaryName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToProject(rs);
                }
            }
        }
        
        return null;
    }

    /**
     * Check if RefNumber is unique (for create operations).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     */
    public boolean isRefNumberUnique(String refNumber) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUnique("Project", refNumber);
    }

    /**
     * Check if RefNumber is unique for update (excluding current ID).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     * Rule: Can repeat ref if it's the same object (excludeId), but cannot repeat for different objects.
     */
    public boolean isRefNumberUniqueForUpdate(String refNumber, int excludeId) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUniqueForUpdate("Project", refNumber, excludeId);
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int projectId, String updateType, String field, String value, String userName) throws SQLException {
        auditStmt.setInt(1, projectId);        // id
        auditStmt.setString(2, updateType);    // updateType
        auditStmt.setString(3, field);          // field
        auditStmt.setString(4, value);         // to
        auditStmt.setString(5, userName);       // author
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * إنشاء audit records للمشروع الجديد (transaction منفصلة — للاستدعاء من خارج bulk upload)
     */
    public void createProjectAuditRecords(int projectId, String userName) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            createProjectAuditRecords(conn, projectId, userName);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                    // best effort
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                    // best effort
                }
            }
        }
    }

    /**
     * إنشاء audit records للمشروع الجديد باستخدام نفس {@link Connection} للمعاملة الحالية
     * (مطلوب لـ bulk upload حيث الإدراج غير مُلتزم بعد ولا يظهر على اتصالات أخرى).
     */
    public void createProjectAuditRecords(Connection conn, int projectId, String userName) throws SQLException {
        PreparedStatement auditStmt = null;
        PreparedStatement projectStmt = null;

        try {
            // 1. الحصول على بيانات المشروع
            String projectDataSql = "SELECT * FROM project WHERE id = ?";
            projectStmt = conn.prepareStatement(projectDataSql);
            projectStmt.setInt(1, projectId);
            ResultSet projectRs = projectStmt.executeQuery();

            if (!projectRs.next()) {
                throw new SQLException("Project not found with ID: " + projectId);
            }

            // 2. إعداد audit statement
            String auditSql = """
                INSERT INTO project_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Project', 'Details', ?, ?, NULL, ?, ?)
                """;
            AuditHistoryWriter.logCreatedBy(conn, "project_audit_history", projectId, "Project", userName);
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);

            // Primary Name
            String primaryName = projectRs.getString("primaryname");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, projectId, "Added", "Primary Name", primaryName, userName);
            }
            
            // Description
            String description = projectRs.getString("description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, projectId, "Added", "Description", description, userName);
            }
            
            // Reference Number
            String refNumber = projectRs.getString("refnumber");
            if (refNumber != null && !refNumber.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, projectId, "Added", "Reference Number", refNumber, userName);
            }
            
            // Parent Project
            Integer parentId = projectRs.getObject("parentid", Integer.class);
            if (parentId != null) {
                String parentName = getProjectName(conn, parentId);
                if (parentName != null) {
                    createNewAuditRecord(conn, auditStmt, projectId, "Added", "Parent Project", parentName, userName);
                }
            }
            
            // Status
            Integer statusId = projectRs.getObject("status", Integer.class);
            if (statusId != null) {
                String statusName = getStatusPrimaryName(conn, statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, projectId, "Status Change", "Status", statusName, userName);
                }
            }
            
            // Lifecycle Status
            Integer lifecycleStatusId = projectRs.getObject("lifecycle_status", Integer.class);
            if (lifecycleStatusId != null) {
                String lifecycleStatusName = getProjectLifecycleStatusName(conn, lifecycleStatusId);
                if (lifecycleStatusName != null) {
                    createNewAuditRecord(conn, auditStmt, projectId, "Status Change", "Lifecycle Status", lifecycleStatusName, userName);
                }
            }
            
            // Project Type
            Integer projectTypeId = projectRs.getObject("project_type", Integer.class);
            if (projectTypeId != null) {
                String projectTypeName = getProjectTypeName(conn, projectTypeId);
                if (projectTypeName != null) {
                    createNewAuditRecord(conn, auditStmt, projectId, "Added", "Project Type", projectTypeName, userName);
                }
            }
            
            // Is Public
            Integer isPublic = projectRs.getObject("is_public", Integer.class);
            if (isPublic != null) {
                String isPublicName = getViewingName(conn, isPublic);
                if (isPublicName != null) {
                    createNewAuditRecord(conn, auditStmt, projectId, "Added", "Is Public", isPublicName, userName);
                }
            }
            
            // RAG
            Integer ragId = projectRs.getObject("rag", Integer.class);
            if (ragId != null) {
                String ragName = getProjectRagName(conn, ragId);
                if (ragName != null) {
                    createNewAuditRecord(conn, auditStmt, projectId, "Added", "RAG", ragName, userName);
                }
            }
            
            // Classification
            Integer classificationId = projectRs.getObject("classification", Integer.class);
            if (classificationId != null) {
                String classificationName = getProjectClassificationName(conn, classificationId);
                if (classificationName != null) {
                    createNewAuditRecord(conn, auditStmt, projectId, "Added", "Classification", classificationName, userName);
                }
            }
            
            // Start Date
            java.sql.Timestamp startDate = projectRs.getTimestamp("startdate");
            if (startDate != null) {
                String formattedStartDate = new java.text.SimpleDateFormat("yyyy-MM-dd").format(startDate);
                createNewAuditRecord(conn, auditStmt, projectId, "Added", "Start Date", formattedStartDate, userName);
            }
            
            // End Date
            java.sql.Timestamp endDate = projectRs.getTimestamp("enddate");
            if (endDate != null) {
                String formattedEndDate = new java.text.SimpleDateFormat("yyyy-MM-dd").format(endDate);
                createNewAuditRecord(conn, auditStmt, projectId, "Added", "End Date", formattedEndDate, userName);
            }
            
            // Created By is written as the first row via AuditHistoryWriter.logCreatedBy.
        } finally {
            if (projectStmt != null) {
                try {
                    projectStmt.close();
                } catch (SQLException ignored) {
                    // best effort
                }
            }
            if (auditStmt != null) {
                try {
                    auditStmt.close();
                } catch (SQLException ignored) {
                    // best effort
                }
            }
        }
    }

    /**
     * إنشاء سجل في جدول project_audit بعد إنشاء المشروع
     * يتم استدعاء هذا method بعد إنشاء المشروع بنجاح
     */
    public void createProjectAuditRecord(int projectId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            createProjectAuditRecord(conn, projectId);
        }
    }

    /**
     * Snapshot row in {@code project_audit} using the caller's connection (e.g. bulk upload transaction).
     */
    public void createProjectAuditRecord(Connection conn, int projectId) throws SQLException {
        String sql = """
            INSERT INTO project_audit (
                id, parentid, is_public, rag, classification, status, lifecycle_status, project_type,
                refnumber, primaryname, description, startdate, enddate, createdatetime, lastupdatedatetime, 
                deletedatetime, createdby_id, lastupdateuser_id, revtype
            )
            SELECT 
                id, parentid, is_public, rag, classification, status, lifecycle_status, project_type,
                refnumber, primaryname, description, DATE(startdate), DATE(enddate), createdatetime, lastupdatedatetime, 
                deletedatetime, createdby_id, lastupdateuser_id, 'Added'
            FROM project 
            WHERE id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ps.executeUpdate();
        }
    }

    
    private String getProjectName(Connection conn, int projectId) throws SQLException {
        String sql = "SELECT primaryname FROM project WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    
    private String getStatusPrimaryName(Connection conn, int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM status WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    
    private String getProjectLifecycleStatusName(Connection conn, int lifecycleStatusId) throws SQLException {
        String sql = "SELECT PrimaryName FROM project_lifecycle WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleStatusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    
    private String getProjectTypeName(Connection conn, int projectTypeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM project_type WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getPersonFullName(int personId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return getPersonFullName(conn, personId);
        }
    }
    
    private String getPersonFullName(Connection conn, int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return null;
    }

    
    private String getViewingName(Connection conn, int viewingId) throws SQLException {
        String sql = "SELECT Name FROM viewing WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, viewingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    
    private String getProjectRagName(Connection conn, int ragId) throws SQLException {
        String sql = "SELECT primaryname FROM project_rag WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ragId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    
    
    private String getProjectClassificationName(Connection conn, int classificationId) throws SQLException {
        String sql = "SELECT primaryname FROM project_classification WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classificationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * Helper method للمقارنة بين القيم (يتعامل مع null)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.equals(obj2);
    }

    /**
     * Helper method لإنشاء audit record للـ update مع from و to
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, 
            int projectId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, projectId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, fromValue);      // from
        auditStmt.setString(7, toValue);        // to
        auditStmt.setString(8, userName);
        
        auditStmt.executeUpdate();
        //system.out.println("    ✓ Update audit record inserted");
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * إنشاء audit records عند تحديث المشروع
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    public void createProjectUpdateAuditRecords(int projectId, Project oldProject, Project newProject, String userName) throws SQLException {
        //system.out.println("🔍 ProjectDAO.createProjectUpdateAuditRecords - START for ID: " + projectId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO project_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldProject.getPrimaryName(), newProject.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                    "Updated", "Primary Name", oldProject.getPrimaryName(), newProject.getPrimaryName(), userName);
            }
            
            // Description
            if (!isEqual(oldProject.getDescription(), newProject.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                    "Updated", "Description", oldProject.getDescription(), newProject.getDescription(), userName);
            }
            
            // Reference Number
            if (!isEqual(oldProject.getRefNumber(), newProject.getRefNumber())) {
                createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                    "Updated", "Reference Number", oldProject.getRefNumber(), newProject.getRefNumber(), userName);
            }
            
            // Parent Project
            if (!isEqual(oldProject.getParentId(), newProject.getParentId())) {
                String oldParentName = oldProject.getParentId() != null ? getProjectName(conn, oldProject.getParentId()) : null;
                String newParentName = newProject.getParentId() != null ? getProjectName(conn, newProject.getParentId()) : null;
                // Only log if the resolved names are actually different
                if (!isEqual(oldParentName, newParentName)) {
                    createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                        "Updated", "Parent Project", oldParentName, newParentName, userName);
                }
            }
            
            // Status (يستخدم "Status Change" كـ updateType)
            if (!isEqual(oldProject.getStatus(), newProject.getStatus())) {
                String oldStatusName = oldProject.getStatus() != null ? getStatusPrimaryName(conn, oldProject.getStatus()) : null;
                String newStatusName = newProject.getStatus() != null ? getStatusPrimaryName(conn, newProject.getStatus()) : null;
                // Only log if the resolved names are actually different
                if (!isEqual(oldStatusName, newStatusName)) {
                    createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                        "Status Change", "Status", oldStatusName, newStatusName, userName);
                }
            }
            
            // Lifecycle Status (يستخدم "Status Change" كـ updateType)
            if (!isEqual(oldProject.getLifecycleStatus(), newProject.getLifecycleStatus())) {
                String oldLifecycleName = oldProject.getLifecycleStatus() != null ? getProjectLifecycleStatusName(conn, oldProject.getLifecycleStatus()) : null;
                String newLifecycleName = newProject.getLifecycleStatus() != null ? getProjectLifecycleStatusName(conn, newProject.getLifecycleStatus()) : null;
                // Only log if the resolved names are actually different
                if (!isEqual(oldLifecycleName, newLifecycleName)) {
                    createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                        "Status Change", "Lifecycle Status", oldLifecycleName, newLifecycleName, userName);
                }
            }
            
            // Project Type
            if (!isEqual(oldProject.getProjectType(), newProject.getProjectType())) {
                String oldTypeName = oldProject.getProjectType() != null ? getProjectTypeName(conn, oldProject.getProjectType()) : null;
                String newTypeName = newProject.getProjectType() != null ? getProjectTypeName(conn, newProject.getProjectType()) : null;
                // Only log if the resolved names are actually different
                if (!isEqual(oldTypeName, newTypeName)) {
                    createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                        "Updated", "Project Type", oldTypeName, newTypeName, userName);
                }
            }
            
            // Is Public
            if (!isEqual(oldProject.getIsPublic(), newProject.getIsPublic())) {
                String oldIsPublicName = oldProject.getIsPublic() != null ? getViewingName(conn, oldProject.getIsPublic()) : null;
                String newIsPublicName = newProject.getIsPublic() != null ? getViewingName(conn, newProject.getIsPublic()) : null;
                // Only log if the resolved names are actually different
                if (!isEqual(oldIsPublicName, newIsPublicName)) {
                    createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                        "Updated", "Is Public", oldIsPublicName, newIsPublicName, userName);
                }
            }
            
            // RAG
            if (!isEqual(oldProject.getRag(), newProject.getRag())) {
                String oldRagName = oldProject.getRag() != null ? getProjectRagName(conn, oldProject.getRag()) : null;
                String newRagName = newProject.getRag() != null ? getProjectRagName(conn, newProject.getRag()) : null;
                // Only log if the resolved names are actually different
                if (!isEqual(oldRagName, newRagName)) {
                    createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                        "Updated", "RAG", oldRagName, newRagName, userName);
                }
            }
            
            // Classification
            if (!isEqual(oldProject.getClassification(), newProject.getClassification())) {
                String oldClassificationName = oldProject.getClassification() != null ? getProjectClassificationName(conn, oldProject.getClassification()) : null;
                String newClassificationName = newProject.getClassification() != null ? getProjectClassificationName(conn, newProject.getClassification()) : null;
                // Only log if the resolved names are actually different
                if (!isEqual(oldClassificationName, newClassificationName)) {
                    createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                        "Updated", "Classification", oldClassificationName, newClassificationName, userName);
                }
            }
            
            // Start Date
            if (!isEqual(oldProject.getStartDate(), newProject.getStartDate())) {
                String oldStartDate = oldProject.getStartDate() != null ? 
                    new java.text.SimpleDateFormat("yyyy-MM-dd").format(oldProject.getStartDate()) : null;
                String newStartDate = newProject.getStartDate() != null ? 
                    new java.text.SimpleDateFormat("yyyy-MM-dd").format(newProject.getStartDate()) : null;
                createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                    "Updated", "Start Date", oldStartDate, newStartDate, userName);
            }
            
            // End Date
            if (!isEqual(oldProject.getEndDate(), newProject.getEndDate())) {
                String oldEndDate = oldProject.getEndDate() != null ? 
                    new java.text.SimpleDateFormat("yyyy-MM-dd").format(oldProject.getEndDate()) : null;
                String newEndDate = newProject.getEndDate() != null ? 
                    new java.text.SimpleDateFormat("yyyy-MM-dd").format(newProject.getEndDate()) : null;
                createUpdateAuditRecord(conn, auditStmt, projectId, "Project", "Details", 
                    "Updated", "End Date", oldEndDate, newEndDate, userName);
            }
            
            conn.commit();
            //system.out.println("✅ ProjectDAO.createProjectUpdateAuditRecords - COMMITTED successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ ProjectDAO.createProjectUpdateAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (auditStmt != null) auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Method لإنشاء snapshot جديد في project_audit عند الـ update
     */
    public void createProjectUpdateAuditSnapshot(int projectId) throws SQLException {
        //system.out.println("🔍 ProjectDAO.createProjectUpdateAuditSnapshot - Creating update snapshot for ID: " + projectId);
        String sql = """
            INSERT INTO project_audit (
                id, parentid, is_public, rag, classification, status, lifecycle_status, project_type,
                refnumber, primaryname, description, startdate, enddate, createdatetime, lastupdatedatetime, 
                deletedatetime, createdby_id, lastupdateuser_id, revtype
            )
            SELECT 
                id, parentid, is_public, rag, classification, status, lifecycle_status, project_type,
                refnumber, primaryname, description, DATE(startdate), DATE(enddate), createdatetime, lastupdatedatetime, 
                deletedatetime, createdby_id, lastupdateuser_id, 'Updated'
            FROM project 
            WHERE id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إنشاء المشروع
     * يتم استدعاء هذا method بعد إنشاء المشروع بنجاح
     */
    public void createStakeholderAuditRecords(int projectId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. إعداد audit statement للـ stakeholder
            String auditSql = """
                INSERT INTO project_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Stakeholder', 'link', ?, ?, NULL, ?, ?)
            """;
            
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleId(conn, projectId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }
            
            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getRoleName(conn, actualRoleId);
            if (roleName == null) roleName = "Project Owner"; // fallback
            
            // Role
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, projectId, "Added", "Role", roleName, userName);
            }
            
            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusId(conn, projectId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(conn, statusId);
                if (fetchedStatusName != null) statusName = fetchedStatusName;
            }
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, projectId, "Added", "Role Status", statusName, userName);
            }
            
            // Name
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, projectId, "Added", "Name", userFullName, userName);
            }
            
            conn.commit(); // تأكيد الـ transaction
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback(); // إلغاء الـ transaction في حالة الخطأ
            }
            throw e;
        } finally {
            // تنظيف الموارد
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

   
    
    private String getRoleName(Connection conn, int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }
 
    
    private Integer getStakeholderRoleId(Connection conn, int projectId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN project_x_objectxpeople pxop ON pxop.object_x_ip = oxp.id " +
                    "WHERE pxop.project_id = ? " +
                    "ORDER BY pxop.id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }

    
    private Integer getStakeholderStatusId(Connection conn, int projectId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN project_x_objectxpeople pxop ON pxop.object_x_ip = oxp.id " +
                    "WHERE pxop.project_id = ? " +
                    "ORDER BY pxop.id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("statusID");
            }
        }
        return null;
    }
    
    
    private String getStatusNameById(Connection conn, int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM object_x_ip_status WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * حذف المشروع مع تسجيل audit records
     */
    public boolean deleteProjectWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE project SET deletedatetime = NOW() WHERE id = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO project_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Project");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Project");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في project_audit (lowercase column names to match schema)
                String snapshotSql = """
                    INSERT INTO project_audit (
                        id, parentid, is_public, rag, classification, status, lifecycle_status,
                        project_type, refnumber, primaryname, description, startdate, enddate,
                        createdatetime, lastupdatedatetime, deletedatetime, createdby_id, lastupdateuser_id, revtype
                    )
                    SELECT 
                        id, parentid, is_public, rag, classification, status, lifecycle_status,
                        project_type, refnumber, primaryname, description, startdate, enddate,
                        createdatetime, lastupdatedatetime, deletedatetime, createdby_id, lastupdateuser_id, 'Deleted'
                    FROM project 
                    WHERE id = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Project deleted with audit for ID: " + id);
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
                if (deleteStmt != null) deleteStmt.close();
                if (auditStmt != null) auditStmt.close();
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
    public int createObjectXPeople(Connection conn, Map<String, Object> stakeholder, int currentUserId) throws SQLException {
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
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating object_x_people failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Link stakeholder to project via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToProject(Connection conn, int projectId, int objectXPeopleId) throws SQLException {
        String sql = """
            INSERT INTO project_x_objectxpeople (object_x_ip, project_id, lastupdate_userid)
            VALUES (?, ?, ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXPeopleId);
            ps.setInt(2, projectId);
            ps.setNull(3, Types.INTEGER); // lastupdate_userid can be null
            try {
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: ProjectID=" + projectId + ", Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }
}
