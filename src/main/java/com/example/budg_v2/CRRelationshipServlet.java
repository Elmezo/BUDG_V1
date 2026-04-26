package com.example.budg_v2;

import com.example.budg_v2.dao.CRRelationshipDAO;
import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.dao.CRStakeholderDAO;
import com.example.budg_v2.model.CRRelationship;
import com.example.budg_v2.model.CRRelationshipType;
import com.example.budg_v2.model.ChangeRequest;
import com.example.budg_v2.service.PermissionService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.UserContextUtil;
import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@WebServlet({
    "/api/cr-relationships/*",
    "/api/cr-relationship-types",
    "/api/cr-relationships-by-reference"
})
public class CRRelationshipServlet extends HttpServlet {
    
    private CRRelationshipDAO crRelationshipDAO;
    private Gson gson;
    
    @Override
    public void init() throws ServletException {
        super.init();
        this.crRelationshipDAO = new CRRelationshipDAO();
        this.gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    }
    
    // Custom LocalDateTime adapter for Gson
    private static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>, 
                                                        com.google.gson.JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime src, java.lang.reflect.Type typeOfSrc, 
                                                   com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(formatter.format(src));
        }
        
        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT, 
                                       com.google.gson.JsonDeserializationContext context) 
                                       throws com.google.gson.JsonParseException {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        String servletPath = request.getServletPath();
        
        try {
            if ("/api/cr-relationship-types".equals(servletPath)) {
                handleGetRelationshipTypes(response);
            } else if ("/api/cr-relationships-by-reference".equals(servletPath)) {
                handleGetChangeRequestsByReference(request, response);
            } else if (pathInfo != null && pathInfo.startsWith("/")) {
                // Get relationships for a specific change request
                String[] pathParts = pathInfo.split("/");
                if (pathParts.length >= 2) {
                    try {
                        Integer sourceId = Integer.parseInt(pathParts[1]);
                        handleGetRelationships(sourceId, response);
                    } catch (NumberFormatException e) {
                        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid change request ID");
                    }
                } else {
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Change request ID is required");
                }
            } else {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                            "Database error: " + e.getMessage());
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            // Get current user ID from session
            HttpSession session = request.getSession(false);
            Integer currentUserId = null;
            if (session != null) {
                currentUserId = (Integer) session.getAttribute("userId");
            }
            
            // Read request body
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                sb.append(line);
            }
            
            CRRelationship relationship = gson.fromJson(sb.toString(), CRRelationship.class);
            
            if (relationship.getSourceId() == null || relationship.getTargetId() == null || 
                relationship.getCrRelationshipTypeId() == null) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                                "Source ID, Target ID, and Relationship Type ID are required");
                return;
            }
            
            // Check if relationship already exists
            if (crRelationshipDAO.relationshipExists(relationship.getSourceId(), 
                                                   relationship.getTargetId(), 
                                                   relationship.getCrRelationshipTypeId())) {
                sendErrorResponse(response, HttpServletResponse.SC_CONFLICT, 
                                "Relationship already exists");
                return;
            }
            
            relationship.setLastUserChange(currentUserId);
            CRRelationship createdRelationship = crRelationshipDAO.createRelationship(relationship);
            
            // Create bidirectional relationship: also create reverse relationship
            // Check if reverse relationship already exists
            if (!crRelationshipDAO.relationshipExists(relationship.getTargetId(), 
                                                      relationship.getSourceId(), 
                                                      relationship.getCrRelationshipTypeId())) {
                CRRelationship reverseRelationship = new CRRelationship();
                reverseRelationship.setSourceId(relationship.getTargetId());
                reverseRelationship.setTargetId(relationship.getSourceId());
                reverseRelationship.setCrRelationshipTypeId(relationship.getCrRelationshipTypeId());
                reverseRelationship.setLastUserChange(currentUserId);
                crRelationshipDAO.createRelationship(reverseRelationship);
            }
            
            response.setStatus(HttpServletResponse.SC_CREATED);
            PrintWriter out = response.getWriter();
            out.print(gson.toJson(createdRelationship));
            out.flush();
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                            "Database error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                            "Invalid request data: " + e.getMessage());
        }
    }
    
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.length() <= 1) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Relationship ID is required");
            return;
        }
        
        try {
            String[] pathParts = pathInfo.split("/");
            if (pathParts.length < 2) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Relationship ID is required");
                return;
            }
            
            Integer relationshipId = Integer.parseInt(pathParts[1]);
            
            // Get current user ID from session
            HttpSession session = request.getSession(false);
            Integer currentUserId = null;
            if (session != null) {
                currentUserId = (Integer) session.getAttribute("userId");
            }
            
            // Read request body
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                sb.append(line);
            }
            
            CRRelationship relationship = gson.fromJson(sb.toString(), CRRelationship.class);
            relationship.setId(relationshipId);
            relationship.setLastUserChange(currentUserId);
            
            if (relationship.getTargetId() == null || relationship.getCrRelationshipTypeId() == null) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                                "Target ID and Relationship Type ID are required");
                return;
            }
            
            boolean updated = crRelationshipDAO.updateRelationship(relationship);
            
            if (updated) {
                response.setStatus(HttpServletResponse.SC_OK);
                PrintWriter out = response.getWriter();
                out.print(gson.toJson(relationship));
                out.flush();
            } else {
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Relationship not found");
            }
            
        } catch (NumberFormatException e) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid relationship ID");
        } catch (SQLException e) {
            e.printStackTrace();
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                            "Database error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                            "Invalid request data: " + e.getMessage());
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.length() <= 1) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Relationship ID is required");
            return;
        }
        
        try {
            String[] pathParts = pathInfo.split("/");
            if (pathParts.length < 2) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Relationship ID is required");
                return;
            }
            
            Integer relationshipId = Integer.parseInt(pathParts[1]);
            
            // Get current user ID
            Integer userId = UserContextUtil.getCurrentUserId(request);
            
            if (userId == null || userId <= 0) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated");
                return;
            }
            
            // Get relationship to find the source Change Request ID
            String getSql = "SELECT Source_ID, Target_ID FROM cr_relationship WHERE ID = ?";
            Integer sourceCrId = null;
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement getStmt = conn.prepareStatement(getSql)) {
                
                getStmt.setInt(1, relationshipId);
                try (ResultSet rs = getStmt.executeQuery()) {
                    if (rs.next()) {
                        sourceCrId = rs.getInt("Source_ID");
                    } else {
                        sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Relationship not found");
                        return;
                    }
                }
            }
            
            // Check if user can edit the source Change Request
            // Same permission logic as ChangeRequestServlet.doPut
            boolean canEdit = false;
            
            // 1. Check if user is super admin or admin
            boolean isSuperAdmin = false;
            boolean isAdmin = false;
            try {
                isSuperAdmin = SegmentAccessService.isSuperAdmin(userId);
                if (!isSuperAdmin) {
                    isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                }
            } catch (SQLException e) {
                // Log and continue
            }
            
            if (isSuperAdmin || isAdmin) {
                canEdit = true;
            } else {
                // 2. Get Change Request to check creator
                ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
                ChangeRequest cr = changeRequestDAO.getChangeRequestById(sourceCrId);
                
                if (cr == null) {
                    sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Change request not found");
                    return;
                }
                
                // Check if user is the requester (creator)
                Integer createdBy = cr.getCreatedBy();
                if (createdBy != null && createdBy.equals(userId)) {
                    canEdit = true;
                } else {
                    // 3. Check if user is a stakeholder with edit permission on Change Requests
                    String reference = cr.getReference();
                    if (reference != null && !reference.trim().isEmpty()) {
                        try {
                            CRStakeholderDAO stakeholderDAO = new CRStakeholderDAO();
                            
                            // Check if CR is auto CR (mandatory workflow) to determine which method to use
                            boolean isAutoCR = cr.getMandatoryWorkflow() != null && cr.getMandatoryWorkflow();
                            List<Map<String, Object>> stakeholders;
                            
                            if (isAutoCR) {
                                // For auto CRs, get stakeholders from the source object
                                stakeholders = stakeholderDAO.getStakeholdersFromSourceObject(reference);
                            } else {
                                stakeholders = stakeholderDAO.getStakeholdersForManualChangeRequest(sourceCrId, reference);
                            }
                            
                            boolean isStakeholder = false;
                            for (Map<String, Object> stakeholder : stakeholders) {
                                Integer stakeholderUserId = null;
                                if (stakeholder.containsKey("personId")) {
                                    Object userIdObj = stakeholder.get("personId");
                                    if (userIdObj instanceof Number) {
                                        stakeholderUserId = ((Number) userIdObj).intValue();
                                    }
                                } else if (stakeholder.containsKey("userId")) {
                                    Object userIdObj = stakeholder.get("userId");
                                    if (userIdObj instanceof Number) {
                                        stakeholderUserId = ((Number) userIdObj).intValue();
                                    }
                                } else if (stakeholder.containsKey("User_ID")) {
                                    Object userIdObj = stakeholder.get("User_ID");
                                    if (userIdObj instanceof Number) {
                                        stakeholderUserId = ((Number) userIdObj).intValue();
                                    }
                                }
                                
                                if (stakeholderUserId != null && stakeholderUserId.equals(userId)) {
                                    isStakeholder = true;
                                    break;
                                }
                            }
                            
                            if (isStakeholder) {
                                // Check edit permission on Change Requests module
                                PermissionService permissionService = new PermissionService();
                                boolean hasEditPermission = permissionService.canEdit(userId, "Change Requests");
                                
                                if (hasEditPermission) {
                                    canEdit = true;
                                }
                            }
                        } catch (SQLException e) {
                            // Log and continue - deny access on error
                        }
                    }
                }
            }
            
            if (!canEdit) {
                sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, 
                    "You don't have permission to delete this relationship. Only super admin, admin, requester, or stakeholder with edit permission can delete relationships.");
                return;
            }
            
            // Log successful permission check
            System.out.println("[CRRelationshipServlet] User " + userId + " authorized to delete relationship " + relationshipId + " for CR " + sourceCrId);
            
            // Permission check passed - proceed with deletion
            boolean deleted = crRelationshipDAO.deleteRelationship(relationshipId);
            
            if (deleted) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Relationship not found");
            }
            
        } catch (NumberFormatException e) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid relationship ID");
        } catch (SQLException e) {
            e.printStackTrace();
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                            "Database error: " + e.getMessage());
        }
    }
    
    private void handleGetRelationshipTypes(HttpServletResponse response) throws SQLException, IOException {
        List<CRRelationshipType> types = crRelationshipDAO.getAllRelationshipTypes();
        
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(types));
        out.flush();
    }
    
    private void handleGetRelationships(Integer sourceId, HttpServletResponse response) 
            throws SQLException, IOException {
        List<CRRelationship> relationships = crRelationshipDAO.getRelationshipsBySourceId(sourceId);
        
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(relationships));
        out.flush();
    }
    
    private void handleGetChangeRequestsByReference(HttpServletRequest request, HttpServletResponse response) 
            throws SQLException, IOException {
        String reference = request.getParameter("reference");
        
        if (reference == null || reference.trim().isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Reference parameter is required");
            return;
        }
        
        List<CRRelationship> changeRequests = crRelationshipDAO.getChangeRequestsByReference(reference);
        
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(changeRequests));
        out.flush();
    }
    
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) 
            throws IOException {
        response.setStatus(statusCode);
        PrintWriter out = response.getWriter();
        out.print("{\"error\": \"" + message + "\"}");
        out.flush();
    }
}
