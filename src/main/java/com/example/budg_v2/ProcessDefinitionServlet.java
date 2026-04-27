package com.example.budg_v2;

import com.example.budg_v2.dao.BpmnContentDAO;
import com.example.budg_v2.dao.ProcessDefinitionDAO;
import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ProcessDefinition;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.BpmnFileManager;
import com.example.budg_v2.util.BpmnParser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet for process definition CRUD operations
 * Endpoints:
 * GET /api/process_definitions - List all
 * GET /api/process_definitions/{id} - Get single
 * POST /api/process_definitions - Create
 * PUT /api/process_definitions/{id} - Update
 * DELETE /api/process_definitions/{id} - Delete
 */
@WebServlet("/api/process_definitions/*")
public class ProcessDefinitionServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ProcessDefinitionServlet.class);
    private final Gson gson = new Gson();
    private final ProcessDefinitionDAO dao = new ProcessDefinitionDAO();
    private final BpmnContentDAO bpmnDao = new BpmnContentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();

        // Handle BPMN requests
        if (pathInfo != null && pathInfo.endsWith("/bpmn")) {
            handleBpmnGet(request, response);
            return;
        }

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // List all process definitions
                String entityIdParam = request.getParameter("entityId");
                List<ProcessDefinition> list;

                if (entityIdParam != null && !entityIdParam.trim().isEmpty()) {
                    int entityId = Integer.parseInt(entityIdParam);
                    list = dao.findByEntityId(entityId);
                } else {
                    list = dao.findAll();
                }

                response.getWriter().write(gson.toJson(list));

            } else {
                // Get single process definition
                int id = Integer.parseInt(pathInfo.substring(1));
                ProcessDefinition pd = dao.findById(id);

                if (pd == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Process definition not found");
                    response.getWriter().write(gson.toJson(error));
                } else {
                    response.getWriter().write(gson.toJson(pd));
                }
            }

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid ID format");
            response.getWriter().write(gson.toJson(error));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = request.getPathInfo();

        // Handle BPMN POST requests
        if (pathInfo != null && pathInfo.endsWith("/bpmn")) {
            handleBpmnPost(request, response);
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Default Workflows editing is Super Admin only
        Object roleAttr = request.getAttribute("userRole");
        if (!AppRoleNames.isSuperAdminName(roleAttr != null ? roleAttr.toString() : "")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Forbidden: Super Admin role required to create workflows");
            response.getWriter().write(gson.toJson(error));
            return;
        }

        try {
            ProcessDefinition pd = gson.fromJson(request.getReader(), ProcessDefinition.class);

            // Validation
            if (pd.getPrimaryName() == null || pd.getPrimaryName().length() < 6) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Workflow name must be at least 6 characters");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            if (pd.getDescription() == null || pd.getDescription().length() < 6) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Description must be at least 6 characters");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Check uniqueness
            if (!dao.isNameUnique(pd.getPrimaryName(), pd.getEntityId(), null)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Workflow name must be unique for this facet");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Get user ID from session
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("userId") != null) {
                pd.setLastUserChange((Integer) session.getAttribute("userId"));
            }

            // Create
            int id = dao.create(pd);
            pd.setId(id);

            // Log activity
            String facetName = getFacetNameByEntityId(pd.getEntityId());
            Map<String, Object> newState = new HashMap<>();
            newState.put("name", pd.getPrimaryName());
            newState.put("description", pd.getDescription());
            newState.put("facetName", facetName);
            newState.put("status", pd.getStatus());
            // Capture actual diagram XML for Create
            String newDiagram = bpmnDao.getXmlContent(id);
            if (newDiagram != null) {
                newState.put("workflowDiagram", newDiagram);
            }
            ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_DEFAULT_WORKFLOWS,
                String.format("%s - %s", facetName, pd.getPrimaryName()),
                ActivityLogConstants.CHANGE_TYPE_CREATE, null, newState);

            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(gson.toJson(pd));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Default Workflows editing is Super Admin only
        Object roleAttr = request.getAttribute("userRole");
        if (!AppRoleNames.isSuperAdminName(roleAttr != null ? roleAttr.toString() : "")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Forbidden: Super Admin role required to update workflows");
            response.getWriter().write(gson.toJson(error));
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "ID required for update");
            response.getWriter().write(gson.toJson(error));
            return;
        }

        try {
            int id = Integer.parseInt(pathInfo.substring(1));
            
            // Get old state before update
            ProcessDefinition oldPd = dao.findById(id);
            Map<String, Object> oldState = new HashMap<>();
            String oldFacetName = null;
            if (oldPd != null) {
                oldFacetName = getFacetNameByEntityId(oldPd.getEntityId());
                oldState.put("name", oldPd.getPrimaryName());
                oldState.put("description", oldPd.getDescription());
                oldState.put("facetName", oldFacetName);
                oldState.put("status", oldPd.getStatus());
                // Capture actual diagram XML, not just "View"
                String oldDiagram = bpmnDao.getXmlContent(id);
                if (oldDiagram != null) {
                    oldState.put("workflowDiagram", oldDiagram);
                }
            }
            
            ProcessDefinition pd = gson.fromJson(request.getReader(), ProcessDefinition.class);
            pd.setId(id);

            // Validation
            if (pd.getPrimaryName() == null || pd.getPrimaryName().length() < 6) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Workflow name must be at least 6 characters");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            if (pd.getDescription() == null || pd.getDescription().length() < 6) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Description must be at least 6 characters");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Check uniqueness (excluding current record)
            if (!dao.isNameUnique(pd.getPrimaryName(), pd.getEntityId(), id)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Workflow name must be unique for this facet");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Get user ID from session
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("userId") != null) {
                pd.setLastUserChange((Integer) session.getAttribute("userId"));
            }

            // Update
            dao.update(pd);
            
            // Log activity - include diagram if it exists (for comparison)
            String facetName = getFacetNameByEntityId(pd.getEntityId());
            Map<String, Object> newState = new HashMap<>();
            newState.put("name", pd.getPrimaryName());
            newState.put("description", pd.getDescription());
            newState.put("facetName", facetName);
            newState.put("status", pd.getStatus());
            
            // Include diagram state for comparison (use marker, not full XML)
            String newDiagram = bpmnDao.getXmlContent(id);
            String oldDiagram = oldState != null && oldState.containsKey("workflowDiagram") ? 
                (oldState.get("workflowDiagram") instanceof String ? (String) oldState.get("workflowDiagram") : null) : null;
            
            // Only include diagram in state if it exists (for comparison in strategy)
            if (newDiagram != null) {
                newState.put("workflowDiagram", "DIAGRAM_EXISTS");
                newState.put("processDefinitionId", id);
            }
            if (oldDiagram != null && !oldDiagram.isEmpty() && !oldDiagram.equals("DIAGRAM_EXISTS")) {
                // Old diagram was stored as full XML (legacy) or marker
                oldState.put("workflowDiagram", oldDiagram.startsWith("<?xml") ? "DIAGRAM_EXISTS" : oldDiagram);
            } else if (oldPd != null) {
                // Check if old diagram existed
                String oldDiagramFromDb = bpmnDao.getXmlContent(id);
                if (oldDiagramFromDb != null) {
                    oldState.put("workflowDiagram", "DIAGRAM_EXISTS");
                }
            }
            if (oldState != null && newDiagram != null) {
                oldState.put("processDefinitionId", id);
            }
            
            // Only log if there are actual changes (oldState is empty when oldPd was null / create case)
            boolean hasChanges = oldState.isEmpty() ||
                !areEqual(oldState.get("name"), newState.get("name")) ||
                !areEqual(oldState.get("description"), newState.get("description")) ||
                !areEqual(oldState.get("status"), newState.get("status")) ||
                !areEqual(oldState.get("workflowDiagram"), newState.get("workflowDiagram"));
            
            if (hasChanges) {
                // Pass contextMap with component name
                Map<String, Object> contextMap = new HashMap<>();
                contextMap.put("facetName", facetName);
                contextMap.put("name", pd.getPrimaryName());
                
                ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_DEFAULT_WORKFLOWS,
                    String.format("%s - %s", facetName, pd.getPrimaryName()),
                    ActivityLogConstants.CHANGE_TYPE_UPDATE, oldState, newState, contextMap);
            }

            response.getWriter().write(gson.toJson(pd));

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid ID format");
            response.getWriter().write(gson.toJson(error));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "ID required for delete");
            response.getWriter().write(gson.toJson(error));
            return;
        }

        try {
            int id = Integer.parseInt(pathInfo.substring(1));
            
            // Get old state before delete
            ProcessDefinition oldPd = dao.findById(id);
            Map<String, Object> oldState = new HashMap<>();
            String facetName = null;
            if (oldPd != null) {
                facetName = getFacetNameByEntityId(oldPd.getEntityId());
                oldState.put("name", oldPd.getPrimaryName());
                oldState.put("description", oldPd.getDescription());
                oldState.put("facetName", facetName);
                oldState.put("status", oldPd.getStatus());
                // Capture actual diagram XML
                String oldDiagram = bpmnDao.getXmlContent(id);
                if (oldDiagram != null) {
                    oldState.put("workflowDiagram", oldDiagram);
                }
            }
            
            dao.delete(id);
            
            // Log activity
            if (oldPd != null && facetName != null) {
                ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_DEFAULT_WORKFLOWS,
                    String.format("%s - %s", facetName, oldPd.getPrimaryName()),
                    ActivityLogConstants.CHANGE_TYPE_DELETE, oldState, null);
            }

            JsonObject success = new JsonObject();
            success.addProperty("success", true);
            response.getWriter().write(gson.toJson(success));

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid ID format");
            response.getWriter().write(gson.toJson(error));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Handle BPMN GET requests
     * Extracts process definition ID from path like /{id}/bpmn
     */
    private void handleBpmnGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // Prevent caching of BPMN XML to ensure fresh data after updates
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        try {
            String pathInfo = request.getPathInfo();
            // Path format: /{id}/bpmn
            String idStr = pathInfo.substring(1, pathInfo.length() - 5); // Remove leading / and trailing /bpmn
            int processDefId = Integer.parseInt(idStr);

            // Try to load from database first
            String xmlContent = bpmnDao.getXmlContent(processDefId);

            // If not in DB, try file system
            if (xmlContent == null) {
                xmlContent = BpmnFileManager.loadBpmnFile(processDefId);
            }

            if (xmlContent == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "BPMN definition not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("xml", xmlContent);
            response.getWriter().write(gson.toJson(result));

        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid process definition ID");
            response.getWriter().write(gson.toJson(error));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));

        } catch (IOException e) {
            logger.error("File system error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "File system error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Handle BPMN POST requests
     * Extracts process definition ID from path like /{id}/bpmn
     */
    private void handleBpmnPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            // Path format: /{id}/bpmn
            String idStr = pathInfo.substring(1, pathInfo.length() - 5); // Remove leading / and trailing /bpmn
            int processDefId = Integer.parseInt(idStr);

            // Parse request body
            JsonObject requestBody = gson.fromJson(request.getReader(), JsonObject.class);

            if (!requestBody.has("xml") || requestBody.get("xml").isJsonNull()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "XML content is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            String xmlContent = requestBody.get("xml").getAsString();

            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "XML content cannot be empty");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            try {
                if (!BpmnParser.hasAtLeastOneEndEvent(xmlContent)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "BPMN must contain at least one end event");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }
            } catch (Exception e) {
                logger.warn("BPMN parse error during end event validation: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid BPMN XML: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Get old diagram before update
            String oldXmlContent = bpmnDao.getXmlContent(processDefId);
            
            // Save to database
            bpmnDao.saveOrUpdate(processDefId, xmlContent);

            // Save to file system
            BpmnFileManager.saveBpmnFile(processDefId, xmlContent);
            
            // Only log diagram change if it was modified AND it's a standalone diagram update
            // (not part of a workflow metadata update - those are logged in doPut)
            // We check if the diagram actually changed
            if (oldXmlContent == null || !oldXmlContent.equals(xmlContent)) {
                // Get process definition for context
                ProcessDefinition pd = dao.findById(processDefId);
                if (pd != null) {
                    // Check if there was a recent workflow update (within last 2 seconds)
                    // If so, don't log separately - it was already logged in doPut
                    // For now, always log standalone diagram changes
                    String facetName = getFacetNameByEntityId(pd.getEntityId());
                    Map<String, Object> oldState = new HashMap<>();
                    Map<String, Object> newState = new HashMap<>();
                    
                    // Store special markers for diagrams (not full XML)
                    oldState.put("workflowDiagram", oldXmlContent != null ? "DIAGRAM_EXISTS" : "");
                    newState.put("workflowDiagram", "DIAGRAM_EXISTS");
                    
                    // Store process definition ID so frontend can fetch diagrams
                    oldState.put("processDefinitionId", processDefId);
                    newState.put("processDefinitionId", processDefId);
                    
                    // Also include other workflow info for context (but mark as unchanged)
                    oldState.put("name", pd.getPrimaryName());
                    oldState.put("description", pd.getDescription());
                    oldState.put("facetName", facetName);
                    newState.put("name", pd.getPrimaryName());
                    newState.put("description", pd.getDescription());
                    newState.put("facetName", facetName);
                    
                    // Pass contextMap with component name
                    Map<String, Object> contextMap = new HashMap<>();
                    contextMap.put("facetName", facetName);
                    contextMap.put("name", pd.getPrimaryName());
                    
                    ActivityLogHelper.logActivity(request, ActivityLogConstants.SETTING_DEFAULT_WORKFLOWS,
                        String.format("%s - %s", facetName, pd.getPrimaryName()),
                        ActivityLogConstants.CHANGE_TYPE_UPDATE, oldState, newState, contextMap);
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("path", BpmnFileManager.getAbsolutePath(processDefId));
            response.getWriter().write(gson.toJson(result));

            logger.info("Saved BPMN for process definition: {}", processDefId);

        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid process definition ID");
            response.getWriter().write(gson.toJson(error));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));

        } catch (IOException e) {
            logger.error("File system error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "File system error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Helper method to compare two objects
     */
    private boolean areEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
    
    /**
     * Get facet name by entity ID (module ID)
     */
    private String getFacetNameByEntityId(int entityId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT primaryname FROM module WHERE id = ?")) {
            ps.setInt(1, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting facet name for entityId: {}", entityId, e);
        }
        return "Unknown";
    }
}
