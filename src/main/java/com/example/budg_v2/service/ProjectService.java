package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProjectDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Project;
import com.example.budg_v2.model.ProjectType;
import com.example.budg_v2.model.ProjectLifecycle;
import com.example.budg_v2.util.DefaultStakeholderUtil;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

public class ProjectService {

    private final ProjectDAO projectDAO;

    public ProjectService() {
        this.projectDAO = new ProjectDAO();
    }

    public List<Project> getAllProjects() throws SQLException {
        return projectDAO.getAllProjects();
    }

    /**
     * Get all projects filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to.
     */
    public List<Project> getAllProjectsBySegmentAccess(int userId) throws SQLException {
        return projectDAO.getAllProjectsBySegmentAccess(userId);
    }

    public Project getProjectById(int id) throws SQLException {
        return projectDAO.getProjectById(id);
    }

    public List<Project> searchProjects(String searchTerm) throws SQLException {
        return projectDAO.searchProjects(searchTerm);
    }

    public List<Project> getAllProjectsForDropdown() throws SQLException {
        return projectDAO.getAllProjectsForDropdown();
    }

    public List<Project> getProjectsForParentPicker(int excludeId) throws SQLException {
        return projectDAO.getProjectsForParentPicker(excludeId);
    }

    public Project createProject(Project project, HttpServletRequest request) throws SQLException, IllegalArgumentException {
        if (project.getPrimaryName() == null || project.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (project.getDescription() == null) project.setDescription("");
        if (project.getIsPublic() == null) project.setIsPublic(1);
        
        // Validate project_type if provided (must exist in project_comment_type table)
        if (project.getProjectType() != null) {
            if (!isValidProjectType(project.getProjectType())) {
                throw new IllegalArgumentException("Invalid project type. The selected project type does not exist.");
            }
        }
        
        // Auto-generate refnumber if empty (before creation, like process & policy)
        if (project.getRefNumber() == null || project.getRefNumber().trim().isEmpty()) {
            try {
                project.setRefNumber(com.example.budg_v2.util.ReferenceNumberGenerator.generateProjectRefNumber());
            } catch (SQLException e) {
                System.err.println("Failed to auto-generate project ref number: " + e.getMessage());
                // Continue with null ref number
            }
        } else {
            // Validate refnumber uniqueness if provided
            if (!projectDAO.isRefNumberUnique(project.getRefNumber())) {
                throw new IllegalArgumentException("This reference number is already in use. Please enter a unique reference number.");
            }
        }
        
        // Create the project in the main table
        Project createdProject = projectDAO.createProject(project);
        
        // Create audit records for history tracking
        try {
            // Get current user name from request attributes
            String userName = getCurrentUserName(request);
            projectDAO.createProjectAuditRecords(createdProject.getId(), userName);
            projectDAO.createProjectAuditRecord(createdProject.getId());
            
            // Create default stakeholder records using createdById (creator)
            if (createdProject.getCreatedById() != null) {
                try {
                    String userFullName = getPersonFullName(createdProject.getCreatedById());
                    if (userFullName != null) {
                        // Create stakeholders for all default roles the creator should receive
                        createDefaultStakeholder(createdProject.getId(), createdProject.getCreatedById(), request);
                        //system.out.println("✅ Default stakeholder records created for project ID: " + createdProject.getId());
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating default stakeholder records: " + e.getMessage());
                    // Continue - don't fail the main operation
                }
            }
        } catch (SQLException e) {
            // Log the error but don't fail the main operation
            System.err.println("Failed to create history audit records: " + e.getMessage());
        }
        
        return createdProject;
    }

    public Project createProject(String primaryName, String description, Integer parentId, Integer status, 
                                 Integer projectType, Integer rag, Integer classification, Integer lifecycleStatus, 
                                 String refNumber, Timestamp startDate, Timestamp endDate, Integer isPublic,
                                 Integer createdById, Integer lastUpdateUserId, HttpServletRequest request) throws SQLException, IllegalArgumentException {
        Project project = new Project();
        project.setPrimaryName(primaryName);
        project.setDescription(description != null ? description : "");
        project.setParentId(parentId);
        project.setStatus(status);
        project.setProjectType(projectType);
        project.setRag(rag);
        project.setClassification(classification);
        project.setLifecycleStatus(lifecycleStatus);
        project.setRefNumber(refNumber);
        project.setStartDate(startDate);
        project.setEndDate(endDate);
        project.setIsPublic(isPublic);
        project.setCreatedById(createdById);
        project.setLastUpdateUserId(lastUpdateUserId);
        return createProject(project, request);
    }

    public boolean updateProject(Project project) throws SQLException, IllegalArgumentException {
        if (project.getId() == null) throw new IllegalArgumentException("Project ID is required for update");
        if (project.getPrimaryName() == null || project.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (project.getDescription() == null) project.setDescription("");
        if (project.getIsPublic() == null) project.setIsPublic(1);
        
        // Validate refnumber uniqueness for update
        if (project.getRefNumber() != null && !project.getRefNumber().trim().isEmpty()) {
            if (!projectDAO.isRefNumberUniqueForUpdate(project.getRefNumber(), project.getId())) {
                throw new IllegalArgumentException("This reference number is already in use. Please enter a unique reference number.");
            }
        }
        
        // Update the project in the main table
        boolean success = projectDAO.updateProject(project);
        
        return success;
    }

    public boolean updateProject(int id, String primaryName, String description, Integer parentId, 
                                Integer status, Integer projectType, Integer rag, Integer classification, 
                                Integer lifecycleStatus, String refNumber, Timestamp startDate, Timestamp endDate,
                                Integer isPublic, Integer lastUpdateUserId) throws SQLException, IllegalArgumentException {
        Project project = new Project();
        project.setId(id);
        project.setPrimaryName(primaryName);
        project.setDescription(description);
        project.setParentId(parentId);
        project.setStatus(status);
        project.setProjectType(projectType);
        project.setRag(rag);
        project.setClassification(classification);
        project.setLifecycleStatus(lifecycleStatus);
        project.setRefNumber(refNumber);
        project.setStartDate(startDate);
        project.setEndDate(endDate);
        project.setIsPublic(isPublic);
        project.setLastUpdateUserId(lastUpdateUserId);
        return updateProject(project);
    }

    public boolean deleteProject(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return projectDAO.deleteProjectWithAudit(id, userName);
    }

    // Project hierarchy method
    public List<java.util.Map<String, Object>> getProjectHierarchy(int projectId) throws SQLException {
        return projectDAO.getProjectHierarchy(projectId);
    }

    // Project relationships methods
    public List<java.util.Map<String, Object>> getProjectRelationshipsBySourceId(int sourceId) throws SQLException {
        return projectDAO.getProjectRelationshipsBySourceId(sourceId);
    }

    public boolean deleteProjectRelationship(int relationshipId) throws SQLException {
        return projectDAO.deleteProjectRelationship(relationshipId);
    }
    
    public List<java.util.Map<String, Object>> getProjectRelationTypes() throws SQLException {
        return projectDAO.getProjectRelationTypes();
    }
    
    public boolean createProjectRelationship(int sourceProjectId, int targetProjectId, int relationType, String description) throws SQLException {
        // Validate cross-segment relationship
        SegmentValidationService validationService = new SegmentValidationService();
        var validationResult = validationService.validateCrossSegmentRelationship(
            sourceProjectId, "Project", targetProjectId, "Project");
        if (!validationResult.isValid) {
            throw new SQLException(validationResult.message);
        }
        
        return projectDAO.createProjectRelationship(sourceProjectId, targetProjectId, relationType, description);
    }
    
    public boolean updateProjectRelationship(int relationshipId, int targetProjectId, int relationType, String description) throws SQLException {
        // Get source project ID from relationship
        java.util.Map<String, Object> relationship = projectDAO.getProjectRelationshipById(relationshipId);
        if (relationship == null) {
            throw new SQLException("Relationship not found");
        }
        
        int sourceProjectId = (Integer) relationship.get("sourceProjectId");
        
        // Validate cross-segment relationship
        SegmentValidationService validationService = new SegmentValidationService();
        var validationResult = validationService.validateCrossSegmentRelationship(
            sourceProjectId, "Project", targetProjectId, "Project");
        if (!validationResult.isValid) {
            throw new SQLException(validationResult.message);
        }
        
        return projectDAO.updateProjectRelationship(relationshipId, targetProjectId, relationType, description);
    }

    public List<ProjectType> getProjectTypes() throws SQLException {
        return projectDAO.getProjectTypes();
    }

    public List<ProjectLifecycle> getProjectLifecycleStatuses() throws SQLException {
        return projectDAO.getProjectLifecycleStatuses();
    }
    
    /**
     * Check if a project type ID exists in the project_comment_type table (as per foreign key constraint)
     */
    private boolean isValidProjectType(Integer projectTypeId) throws SQLException {
        if (projectTypeId == null) {
            return true; // NULL is allowed
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM project_comment_type WHERE id = ?")) {
            ps.setInt(1, projectTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            // If project_comment_type table doesn't exist, try project_type as fallback
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM project_type WHERE id = ?")) {
                ps.setInt(1, projectTypeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        }
        return false;
    }

    // Helper method to get current user name from request
    private String getCurrentUserName(HttpServletRequest request) {
        Object userNameObj = request.getAttribute("userName");
        if (userNameObj != null) {
            return userNameObj.toString();
        }
        return "System"; // Fallback if no user name found
    }

    // Helper method to get person full name
    private String getPersonFullName(int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return null;
    }

    // Create default stakeholder using ProjectStakeholderServlet logic
    // Creates stakeholders for all default roles the creator should receive based on role assignments
    private void createDefaultStakeholder(int projectId, int userId, HttpServletRequest request) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Get module ID for Project
                int moduleId = DefaultStakeholderUtil.getModuleId(conn, "Project");
                
                // Get all default roles the creator should receive
                List<Integer> rolesToAssign = DefaultStakeholderUtil.getDefaultRolesForCreator(conn, moduleId, userId);
                
                if (rolesToAssign.isEmpty()) {
                    // No default roles to assign
                    conn.commit();
                    return;
                }
                
                String userName = getCurrentUserName(request);
                String userFullName = getPersonFullName(userId);
                
                // Create stakeholders for each role
                for (Integer roleId : rolesToAssign) {
                    try {
                        // Insert into object_x_people (same logic as ProjectStakeholderServlet)
                        String insertOXP = """
                            INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                            VALUES (NULL, ?, ?, 2, 1, ?)
                            """;

                        int objectXPeopleId;
                        try (PreparedStatement stmt = conn.prepareStatement(insertOXP, Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setInt(1, userId); // ipid
                            stmt.setInt(2, roleId); // RoleID
                            stmt.setInt(3, userId); // lastupdateuser_id

                            int rowsAffected = stmt.executeUpdate();
                            if (rowsAffected == 0) {
                                throw new SQLException("Failed to insert object_x_people");
                            }

                            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                                if (generatedKeys.next()) {
                                    objectXPeopleId = generatedKeys.getInt(1);
                                } else {
                                    throw new SQLException("Failed to get generated key for object_x_people");
                                }
                            }
                        }

                        // Insert into project_x_objectxpeople (same logic as ProjectStakeholderServlet)
                        String insertCXOP = """
                            INSERT INTO project_x_objectxpeople (object_x_ip, project_id, lastupdate_userid)
                            VALUES (?, ?, ?)
                            """;

                        try (PreparedStatement stmt = conn.prepareStatement(insertCXOP)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, projectId);
                            stmt.setInt(3, userId);

                            int rowsAffected = stmt.executeUpdate();
                            
                            if (rowsAffected == 0) {
                                throw new SQLException("Failed to insert project_x_objectxpeople");
                            }
                        }
                        
                        // Create stakeholder audit records
                        if (userFullName != null) {
                            projectDAO.createStakeholderAuditRecords(projectId, userName, userFullName, roleId);
                        }
                    } catch (SQLException e) {
                        // Log error but continue with other roles
                        System.err.println("❌ Error creating default stakeholder for role " + roleId + ": " + e.getMessage());
                        // Continue processing other roles
                    }
                }

                conn.commit();
                //system.out.println("✅ Default stakeholders created successfully for project ID: " + projectId);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // If module not found or other critical error, log but don't fail object creation
            System.err.println("❌ Error in createDefaultStakeholder: " + e.getMessage());
            // Don't throw - allow object creation to continue
        }
    }
}
