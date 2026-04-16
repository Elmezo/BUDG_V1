package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Project;
import com.example.budg_v2.service.ProjectService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.SegmentValidationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RequestedSegmentFilterUtil;
import com.example.budg_v2.util.SegmentScopedPrimaryNameCheck;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.example.budg_v2.util.SegmentResponseUtil.SegmentInfo;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@WebServlet("/api/project/*")
public class ProjectServlet extends HttpServlet {

    private ProjectService projectService;
    private SegmentDAO segmentDAO;
    private SegmentValidationService segmentValidationService;

    @Override
    public void init() {
        this.projectService = new ProjectService();
        this.segmentDAO = new SegmentDAO();
        this.segmentValidationService = new SegmentValidationService();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        
        try {
            // Get current user ID for segment filtering
            // Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to
            int userId = UserContextUtil.getCurrentUserId(request);
            
            if (pathInfo == null || "/list".equals(pathInfo)) {
                // Return all projects - filtered by segment access
                //system.out.println("ProjectServlet: Getting all projects for user " + userId);
                List<Project> projects = userId > 0 
                    ? projectService.getAllProjectsBySegmentAccess(userId)
                    : projectService.getAllProjects();
                projects = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        projects,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Project",
                        Project::getId);
                //system.out.println("ProjectServlet: Retrieved " + projects.size() + " projects");
                
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Project p : projects) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", p.getId());
                    o.addProperty("primaryname", p.getPrimaryName());
                    o.addProperty("description", p.getDescription());
                    o.addProperty("refnumber", p.getRefNumber());
                    arr.add(o);
                    //system.out.println("ProjectServlet: Added project to response: " + p.getPrimaryName());
                }
                response.getWriter().write(arr.toString());
            } else if ("/parent-picker".equals(pathInfo)) {
                String excludeIdParam = request.getParameter("excludeId");
                int excludeId = excludeIdParam != null ? Integer.parseInt(excludeIdParam) : 0;
                
                List<Project> projects = projectService.getProjectsForParentPicker(excludeId);
                projects = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        projects,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Project",
                        Project::getId);
                
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Project p : projects) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", p.getId());
                    o.addProperty("primaryname", p.getPrimaryName());
                    o.addProperty("description", p.getDescription());
                    o.addProperty("refnumber", p.getRefNumber());
                    arr.add(o);
                    //system.out.println("ProjectServlet: Added parent project to response: " + p.getPrimaryName());
                }
                response.getWriter().write(arr.toString());
            } else if ("/search".equals(pathInfo)) {
                String searchQuery = request.getParameter("q");
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    searchProjects(response, searchQuery.trim());
                } else {
                    getAllProjects(response);
                }
            } else if ("/hierarchy".equals(pathInfo)) {
                // Get all projects for hierarchy display - filtered by segment access
                //system.out.println("ProjectServlet /hierarchy - Starting hierarchy request for user " + userId);
                List<Project> projects = userId > 0 
                    ? projectService.getAllProjectsBySegmentAccess(userId)
                    : projectService.getAllProjects();
                projects = RequestedSegmentFilterUtil.filterByRequestedSegment(
                        projects,
                        RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                        "Project",
                        Project::getId);
                //system.out.println("ProjectServlet /hierarchy - projects count: " + projects.size());
                
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Project p : projects) {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("id", p.getId());
                o.addProperty("primaryName", p.getPrimaryName());
                o.addProperty("description", p.getDescription());
                o.addProperty("refNumber", p.getRefNumber());
                o.addProperty("parentId", p.getParentId());
                arr.add(o);
                    //system.out.println("ProjectServlet: Added project to hierarchy: " + p.getPrimaryName() + " (ID: " + p.getId() + ", Parent: " + p.getParentId() + ")");
                }
                //system.out.println("ProjectServlet /hierarchy - JSON response size: " + arr.size());
                response.getWriter().write(arr.toString());
            } else if (pathInfo.startsWith("/hierarchy/")) {
                // Get project hierarchy
                try {
                    // "/hierarchy/" is 11 characters, so substring(11) gets the ID
                    String idStr = pathInfo.substring(11);
                    System.out.println("🔍 ProjectServlet /hierarchy/ - pathInfo: " + pathInfo + ", extracted ID string: " + idStr);
                    int id = Integer.parseInt(idStr);
                    System.out.println("🔍 ProjectServlet /hierarchy/ - parsed ID: " + id);
                    List<java.util.Map<String, Object>> hierarchy = projectService.getProjectHierarchy(id);
                    response.getWriter().write(JsonUtil.toJson(hierarchy));
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid project ID");
                    response.getWriter().write(JsonUtil.toJson(error));
                } catch (SQLException e) {
                    System.err.println("ProjectServlet /hierarchy/ error: " + e.getMessage());
                    e.printStackTrace();
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Database error: " + e.getMessage());
                    response.getWriter().write(JsonUtil.toJson(error));
                } catch (Exception e) {
                    System.err.println("ProjectServlet /hierarchy/ error: " + e.getMessage());
                    e.printStackTrace();
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Error loading hierarchy: " + e.getMessage());
                    response.getWriter().write(JsonUtil.toJson(error));
                }
            } else if (pathInfo.startsWith("/relationships/")) {
                // Get project relationships
                try {
                    int id = Integer.parseInt(pathInfo.substring(15));
                    List<java.util.Map<String, Object>> relationships = projectService.getProjectRelationshipsBySourceId(id);
                    response.getWriter().write(JsonUtil.toJson(relationships));
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid project ID");
                    response.getWriter().write(JsonUtil.toJson(error));
                }
            } else if ("/project-type".equals(pathInfo)) {
                // Get project types
                //system.out.println("ProjectServlet: Getting project types");
                List<com.example.budg_v2.model.ProjectType> projectTypes = projectService.getProjectTypes();
                //system.out.println("ProjectServlet: Retrieved " + projectTypes.size() + " project types");
                response.getWriter().write(JsonUtil.toJson(projectTypes));
            } else if ("/lifecycle/list".equals(pathInfo)) {
                // Get project lifecycle statuses
                //system.out.println("ProjectServlet: Getting project lifecycle statuses");
                List<com.example.budg_v2.model.ProjectLifecycle> lifecycle = projectService.getProjectLifecycleStatuses();
                //system.out.println("ProjectServlet: Retrieved " + lifecycle.size() + " lifecycle statuses");
                response.getWriter().write(JsonUtil.toJson(lifecycle));
            } else if ("/relation-types".equals(pathInfo)) {
                // Get project_x_project relation types
                try {
                    List<java.util.Map<String, Object>> relationTypes = projectService.getProjectRelationTypes();
                    response.getWriter().write(JsonUtil.toJson(relationTypes));
                } catch (SQLException e) {
                    System.err.println("ProjectServlet /relation-types error: " + e.getMessage());
                    e.printStackTrace();
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Database error: " + e.getMessage());
                    response.getWriter().write(JsonUtil.toJson(error));
                }
            } else if (pathInfo != null && pathInfo.length() > 1) {
                String idParam = pathInfo.substring(1);
                try {
                    int id = Integer.parseInt(idParam);
                    getProjectById(request, response, id);
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(response.getWriter(), "Invalid project ID: " + idParam, 400);
                } catch (Exception e) {
                    System.err.println("ProjectServlet GET error: " + e.getMessage());
                    e.printStackTrace();
                    JsonUtil.sendErrorResponse(response.getWriter(), "Error retrieving project: " + e.getMessage(), 500);
                }
            } else {
                getAllProjects(response);
            }
        } catch (Exception e) {
            System.err.println("ProjectServlet error: " + e.getMessage());
            e.printStackTrace();
            JsonUtil.sendErrorResponse(response.getWriter(), "Error: " + e.getMessage(), 500);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        // Check create permission for new project creation
        if (!PermissionCheckUtil.checkCreatePermission(request, response, "Project")) {
            return; // Response already sent
        }

        try {
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String primaryName = JsonUtil.getJsonString(jsonData, "primaryname");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer parentId = JsonUtil.getJsonInt(jsonData, "parentid");
            Integer status = JsonUtil.getJsonInt(jsonData, "status");
            Integer projectType = JsonUtil.getJsonInt(jsonData, "project_type");
            Integer rag = JsonUtil.getJsonInt(jsonData, "rag");
            Integer classification = JsonUtil.getJsonInt(jsonData, "classification");
            Integer lifecycleStatus = JsonUtil.getJsonInt(jsonData, "lifecycle_status");
            String refNumber = JsonUtil.getJsonString(jsonData, "refnumber");
            Integer isPublic = JsonUtil.getJsonInt(jsonData, "is_public");
            
            // Get current user ID from request (like DatasetServlet does)
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId <= 0) {
                JsonUtil.sendErrorResponse(response.getWriter(), "User authentication required", 401);
                return;
            }
            Integer createdById = currentUserId;
            Integer lastUpdateUserId = null; // NULL on creation - will be set to CreatedBy only when editing with no changes

            // Handle dates
            Timestamp startDate = null;
            Timestamp endDate = null;
            String startDateStr = JsonUtil.getJsonString(jsonData, "startdate");
            String endDateStr = JsonUtil.getJsonString(jsonData, "enddate");
            
            if (startDateStr != null && !startDateStr.trim().isEmpty()) {
                startDate = Timestamp.valueOf(startDateStr + " 00:00:00");
            }
            if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                endDate = Timestamp.valueOf(endDateStr + " 23:59:59");
            }

            // Check if this is a relationship creation request
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && "/relationship".equals(pathInfo)) {
                // Create project relationship
                try {
                    Integer sourceProjectId = JsonUtil.getJsonInt(jsonData, "sourceProjectId");
                    Integer targetProjectId = JsonUtil.getJsonInt(jsonData, "targetProjectId");
                    Integer relationType = JsonUtil.getJsonInt(jsonData, "relationType");
                    String relationshipDescription = JsonUtil.getJsonString(jsonData, "description");
                    
                    if (sourceProjectId == null || targetProjectId == null || relationType == null) {
                        JsonUtil.sendErrorResponse(response.getWriter(), "sourceProjectId, targetProjectId, and relationType are required", 400);
                        return;
                    }

                    var projectRelValidation = segmentValidationService.validateCrossSegmentRelationship(sourceProjectId, "Project", targetProjectId, "Project");
                    if (!projectRelValidation.isValid) {
                        JsonUtil.sendErrorResponse(response.getWriter(), projectRelValidation.message, 400);
                        return;
                    }
                    
                    boolean success = projectService.createProjectRelationship(sourceProjectId, targetProjectId, relationType, relationshipDescription);
                    JsonObject responseJson = new JsonObject();
                    responseJson.addProperty("success", success);
                    response.getWriter().write(JsonUtil.toJson(responseJson));
                    return;
                } catch (Exception e) {
                    System.err.println("ProjectServlet POST relationship error: " + e.getMessage());
                    e.printStackTrace();
                    JsonUtil.sendErrorResponse(response.getWriter(), "Error creating relationship: " + e.getMessage(), 500);
                    return;
                }
            }
            
            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            // Validate segment hierarchy before creating project
            Integer segmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
            if (segmentId == null) segmentId = 1; // Default to Enterprise segment
            
            // Validate hierarchy: parent-child relationships must stay within the same segment
            if (parentId != null && parentId > 0) {
                try {
                    var hierarchyValidation = segmentValidationService.validateParentChildSegment(parentId, segmentId, "Project");
                    if (!hierarchyValidation.isValid) {
                        JsonUtil.sendErrorResponse(response.getWriter(), hierarchyValidation.message, 400);
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error validating project hierarchy: " + e.getMessage());
                    JsonUtil.sendErrorResponse(response.getWriter(), "Error validating segment hierarchy: " + e.getMessage(), 500);
                    return;
                }
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Project", primaryName.trim(),
                        segmentId.longValue(), null)) {
                    JsonUtil.sendErrorResponse(response.getWriter(),
                            "A project with this name already exists in the selected segment.", 400);
                    return;
                }
            }

            Project newProject = projectService.createProject(
                    primaryName.trim(), description, parentId, status, projectType, rag, 
                    classification, lifecycleStatus, refNumber, startDate, endDate, 
                    isPublic, createdById, lastUpdateUserId, request);

            // Assign project to segment
            try {
                segmentDAO.assignObjectToSegment(segmentId, newProject.getId(), "Project", lastUpdateUserId != null ? lastUpdateUserId : 1);
                //system.out.println("✅ Project " + newProject.getId() + " assigned to segment " + segmentId);
            } catch (Exception e) {
                System.err.println("❌ Error assigning project to segment: " + e.getMessage());
            }

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Project created successfully");
            successResponse.add("data", JsonParser.parseString(JsonUtil.toJson(newProject)));

            response.getWriter().write(successResponse.toString());

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            System.err.println("ProjectServlet POST error: " + e.getMessage());
            e.printStackTrace();
            JsonUtil.sendErrorResponse(response.getWriter(), "Error creating project: " + e.getMessage(), 500);
        }
    }

    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            JsonObject jsonData = JsonUtil.parseJsonFromRequest(request.getReader());

            String pathInfo = request.getPathInfo();
            
            // Check if this is a relationship update request
            if (pathInfo != null && pathInfo.startsWith("/relationship/")) {
                // Update project relationship
                try {
                    int relationshipId = Integer.parseInt(pathInfo.substring(14));
                    Integer targetProjectId = JsonUtil.getJsonInt(jsonData, "targetProjectId");
                    Integer relationType = JsonUtil.getJsonInt(jsonData, "relationType");
                    String relationshipDescription = JsonUtil.getJsonString(jsonData, "description");
                    
                    if (targetProjectId == null || relationType == null) {
                        JsonUtil.sendErrorResponse(response.getWriter(), "targetProjectId and relationType are required", 400);
                        return;
                    }

                    int sourceProjectId = getSourceProjectId(relationshipId);
                    if (sourceProjectId > 0) {
                        var projectRelValidation = segmentValidationService.validateCrossSegmentRelationship(sourceProjectId, "Project", targetProjectId, "Project");
                        if (!projectRelValidation.isValid) {
                            JsonUtil.sendErrorResponse(response.getWriter(), projectRelValidation.message, 400);
                            return;
                        }
                    }
                    
                    boolean success = projectService.updateProjectRelationship(relationshipId, targetProjectId, relationType, relationshipDescription);
                    JsonObject responseJson = new JsonObject();
                    responseJson.addProperty("success", success);
                    response.getWriter().write(JsonUtil.toJson(responseJson));
                    return;
                } catch (Exception e) {
                    System.err.println("ProjectServlet PUT relationship error: " + e.getMessage());
                    e.printStackTrace();
                    JsonUtil.sendErrorResponse(response.getWriter(), "Error updating relationship: " + e.getMessage(), 500);
                    return;
                }
            }

            // Get ID from URL path instead of JSON payload
            Integer id = null;
            if (pathInfo != null && pathInfo.startsWith("/")) {
                String[] pathParts = pathInfo.substring(1).split("/");
                if (pathParts.length > 0) {
                    try {
                        id = Integer.parseInt(pathParts[0]);
                    } catch (NumberFormatException e) {
                        // ID not found in path
                    }
                }
            }
            
            if (id == null) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Project ID is required", 400);
                return;
            }
            
            // Check role-based edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Project", id)) {
                return; // Response already sent
            }
            
            String primaryName = JsonUtil.getJsonString(jsonData, "primaryname");
            String description = JsonUtil.getJsonString(jsonData, "description");
            Integer parentId = JsonUtil.getJsonInt(jsonData, "parentid");
            Integer status = JsonUtil.getJsonInt(jsonData, "status");
            Integer projectType = JsonUtil.getJsonInt(jsonData, "project_type");
            Integer rag = JsonUtil.getJsonInt(jsonData, "rag");
            Integer classification = JsonUtil.getJsonInt(jsonData, "classification");
            Integer lifecycleStatus = JsonUtil.getJsonInt(jsonData, "lifecycle_status");
            String refNumber = JsonUtil.getJsonString(jsonData, "refnumber");
            Integer isPublic = JsonUtil.getJsonInt(jsonData, "is_public");
            
            // Get current user ID from request (like DatasetServlet does)
            int currentUserId = UserContextUtil.getCurrentUserId(request);
            if (currentUserId <= 0) {
                JsonUtil.sendErrorResponse(response.getWriter(), "User authentication required", 401);
                return;
            }
            Integer lastUpdateUserId = currentUserId;

            // Handle dates
            Timestamp startDate = null;
            Timestamp endDate = null;
            String startDateStr = JsonUtil.getJsonString(jsonData, "startdate");
            String endDateStr = JsonUtil.getJsonString(jsonData, "enddate");
            
            if (startDateStr != null && !startDateStr.trim().isEmpty()) {
                startDate = Timestamp.valueOf(startDateStr + " 00:00:00");
            }
            if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                endDate = Timestamp.valueOf(endDateStr + " 23:59:59");
            }

            // Check segment-based edit permission
            if (currentUserId > 0) {
                boolean canEdit = SegmentAccessService.canEditObject(currentUserId, id, "Project");
                if (!canEdit) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonUtil.sendErrorResponse(response.getWriter(), "Access denied. You don't have permission to edit this project.", 403);
                    return;
                }
            }

            if (primaryName == null || primaryName.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Primary name is required", 400);
                return;
            }

            Integer reqSeg = JsonUtil.getJsonInt(jsonData, "segmentId");
            int curSeg = segmentDAO.getObjectSegmentId(id, "Project");
            long effSeg = (reqSeg != null) ? reqSeg.longValue() : (curSeg > 0 ? curSeg : 1L);
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (SegmentScopedPrimaryNameCheck.exists(conn, "Project", primaryName.trim(), effSeg, id)) {
                    JsonUtil.sendErrorResponse(response.getWriter(),
                            "A project with this name already exists in this segment.", 400);
                    return;
                }
            }

            boolean success = projectService.updateProject(
                    id, primaryName.trim(), description, parentId, status, projectType, rag,
                    classification, lifecycleStatus, refNumber, startDate, endDate,
                    isPublic, lastUpdateUserId);

            // Update segment assignment if provided - even if the base update returned false.
            // This fixes the case where the user only changes the segment field.
            boolean segmentUpdated = false;
                Integer segmentId = JsonUtil.getJsonInt(jsonData, "segmentId");
                if (segmentId != null) {
                    try {
                        int currentSegmentId = segmentDAO.getObjectSegmentId(id, "Project");
                        if (currentSegmentId != segmentId) {
                            // Validate stakeholder access before changing segment
                            // Reuse parentId already read from JSON above
                            var validationResult = segmentValidationService.validateSegmentMove(id, segmentId, "Project", parentId);
                            if (!validationResult.isValid) {
                                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                JsonObject errorResponse = new JsonObject();
                                errorResponse.addProperty("success", false);
                                errorResponse.addProperty("error", validationResult.message);
                                response.getWriter().write(errorResponse.toString());
                                return;
                            }
                            
                            if (currentSegmentId > 0) {
                                segmentDAO.removeObjectFromSegment(currentSegmentId, id, "Project", lastUpdateUserId != null ? lastUpdateUserId : 1);
                            }
                            segmentDAO.assignObjectToSegment(segmentId, id, "Project", lastUpdateUserId != null ? lastUpdateUserId : 1);
                            //system.out.println("✅ Project " + id + " segment changed from " + currentSegmentId + " to " + segmentId);
                        segmentUpdated = true;
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error updating project segment: " + e.getMessage());
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        JsonObject errorResponse = new JsonObject();
                        errorResponse.addProperty("success", false);
                        errorResponse.addProperty("error", "Error updating project segment: " + e.getMessage());
                        response.getWriter().write(errorResponse.toString());
                        return;
                    }
                }

            if (success || segmentUpdated) {
                JsonObject successResponse = new JsonObject();
                successResponse.addProperty("success", true);
                successResponse.addProperty("message", "Project updated successfully");
                response.getWriter().write(successResponse.toString());
            } else {
                JsonUtil.sendErrorResponse(response.getWriter(), "Failed to update project", 500);
            }

        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(response.getWriter(), e.getMessage(), 400);
        } catch (Exception e) {
            System.err.println("ProjectServlet PUT error: " + e.getMessage());
            e.printStackTrace();
            JsonUtil.sendErrorResponse(response.getWriter(), "Error updating project: " + e.getMessage(), 500);
        }
    }

    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        if (pathInfo != null && pathInfo.startsWith("/relationship/")) {
            // Delete project relationship
            try {
                int relationshipId = Integer.parseInt(pathInfo.substring(14));
                boolean success = projectService.deleteProjectRelationship(relationshipId);
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", success);
                response.getWriter().write(JsonUtil.toJson(responseJson));
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid relationship ID");
                response.getWriter().write(JsonUtil.toJson(error));
            } catch (Exception e) {
                System.err.println("ProjectServlet DELETE relationship error: " + e.getMessage());
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Error deleting relationship: " + e.getMessage());
                response.getWriter().write(JsonUtil.toJson(error));
            }
        } else if (pathInfo != null && pathInfo.length() > 1) {
            String idParam = pathInfo.substring(1);
            try {
                int id = Integer.parseInt(idParam);
                deleteProject(request, response, id);
            } catch (NumberFormatException e) {
                JsonUtil.sendErrorResponse(response.getWriter(), "Invalid project ID: " + idParam, 400);
            } catch (Exception e) {
                System.err.println("ProjectServlet DELETE error: " + e.getMessage());
                e.printStackTrace();
                JsonUtil.sendErrorResponse(response.getWriter(), "Error deleting project: " + e.getMessage(), 500);
            }
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Project ID is required", 400);
        }
    }

    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getAllProjects(HttpServletResponse response) throws Exception {
        List<Project> projects = projectService.getAllProjects();
        JsonUtil.sendJsonResponse(response.getWriter(), projects);
    }

    private void getProjectById(HttpServletRequest request, HttpServletResponse response, int id) throws Exception {
        Project project = projectService.getProjectById(id);
        if (project != null) {
            if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(project.getStatusName())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"This object is not available.\"}");
                return;
            }
            // Check access control for all users (including guests)
            int userId = UserContextUtil.getCurrentUserId(request);

            // GUEST ACCESS CHECK: Only allow public objects in Enterprise segment
            if (userId <= 0) {
                // Guest user - check if object is public and in Enterprise segment
                try {
                    boolean canAccess = SegmentAccessService.canGuestAccessObject(id, "Project");
                    if (!canAccess) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("{\"error\":\"Access denied. This resource is not publicly accessible.\"}");
                        return;
                    }
                } catch (SQLException e) {
                    // On error, deny access for safety
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. Unable to verify access permissions.\"}");
                    return;
                }
            }

            // Authenticated user access check
            if (userId > 0) {
                boolean canAccess = SegmentAccessService.canAccessObject(userId, id, "Project");
                if (!canAccess) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied. You don't have permission to view this project.\"}");
                    return;
                }
            }

            JsonObject projectJson = JsonParser.parseString(JsonUtil.toJson(project)).getAsJsonObject();
            SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, id, "Project");
            SegmentResponseUtil.applySegmentInfo(projectJson, segmentInfo, request);
            response.getWriter().write(projectJson.toString());
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Project not found", 404);
        }
    }

    private void searchProjects(HttpServletResponse response, String searchTerm) throws Exception {
        List<Project> projects = projectService.searchProjects(searchTerm);
        JsonUtil.sendJsonResponse(response.getWriter(), projects);
    }

    private void deleteProject(HttpServletRequest request, HttpServletResponse response, int id) throws Exception {
        boolean success = projectService.deleteProject(id, request);
        if (success) {
            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("success", true);
            successResponse.addProperty("message", "Project deleted successfully");
            response.getWriter().write(successResponse.toString());
        } else {
            JsonUtil.sendErrorResponse(response.getWriter(), "Failed to delete project", 500);
        }
    }

    private int getSourceProjectId(int relationshipId) {
        String sql = "SELECT sourceprojectid FROM project_x_project WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, relationshipId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("sourceprojectid");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error looking up source project for relationship " + relationshipId + ": " + e.getMessage());
        }
        return -1;
    }
}
