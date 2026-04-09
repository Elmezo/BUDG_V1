package com.example.budg_v2;

import com.example.budg_v2.dao.WorkflowMappingDAO;
import com.example.budg_v2.model.WorkflowMapping;
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
import java.sql.SQLException;
import java.util.List;

/**
 * Servlet for workflow type mapping operations
 * Handles mapping between process definitions and CR types
 * Special behavior: When Type 2 is selected, creates 2 rows
 * 
 * Endpoints:
 * GET /api/workflow_types/{processDefinitionId} - Get mappings
 * POST /api/workflow_types - Create mapping
 * PUT /api/workflow_types/{id} - Update mapping
 * DELETE /api/workflow_types/{id} - Delete mapping
 */
@WebServlet("/api/workflow_types/*")
public class WorkflowTypeMappingServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowTypeMappingServlet.class);
    private final Gson gson = new Gson();
    private final WorkflowMappingDAO dao = new WorkflowMappingDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        String processDefIdParam = request.getParameter("processDefinitionId");

        try {
            int processDefId;
            
            // Try to get ID from path first, then from query parameter
            if (pathInfo != null && !pathInfo.equals("/")) {
                processDefId = Integer.parseInt(pathInfo.substring(1));
            } else if (processDefIdParam != null && !processDefIdParam.isEmpty()) {
                processDefId = Integer.parseInt(processDefIdParam);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Process definition ID required");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            List<WorkflowMapping> mappings = dao.findByProcessDefinitionId(processDefId);
            response.getWriter().write(gson.toJson(mappings));

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

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            JsonObject requestBody = gson.fromJson(request.getReader(), JsonObject.class);

            int processDefId = requestBody.get("processDefinitionId").getAsInt();
            int entityId = requestBody.get("entityId").getAsInt();

            // Get user ID from session
            int userId = 0;
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("userId") != null) {
                userId = (Integer) session.getAttribute("userId");
            }

            // Check if this is a batch operation (crTypes array) or single operation (crType)
            if (requestBody.has("crTypes") && requestBody.get("crTypes").isJsonArray()) {
                // Batch operation: delete all existing mappings and create new ones for all types
                dao.deleteByProcessDefinitionId(processDefId);
                
                com.google.gson.JsonArray crTypesArray = requestBody.get("crTypes").getAsJsonArray();
                int createdCount = 0;
                
                for (int i = 0; i < crTypesArray.size(); i++) {
                    String crType = crTypesArray.get(i).getAsString();
                    
                    // Special handling for Type 2: create 2 rows
                    if ("Type 2".equals(crType) || "2".equals(crType)) {
                        // Create mapping for Type 1
                        WorkflowMapping mapping1 = new WorkflowMapping(processDefId, "Type 1", entityId);
                        mapping1.setLastUserChange(userId);
                        dao.create(mapping1);
                        createdCount++;

                        // Create mapping for Type 2
                        WorkflowMapping mapping2 = new WorkflowMapping(processDefId, "Type 2", entityId);
                        mapping2.setLastUserChange(userId);
                        dao.create(mapping2);
                        createdCount++;
                    } else {
                        // Create single mapping
                        WorkflowMapping mapping = new WorkflowMapping(processDefId, crType, entityId);
                        mapping.setLastUserChange(userId);
                        dao.create(mapping);
                        createdCount++;
                    }
                }
                
                logger.info("Created {} mappings for batch operation", createdCount);
                
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("count", createdCount);
                response.getWriter().write(gson.toJson(result));
                
            } else {
                // Single operation: backward compatibility with existing API
                String crType = requestBody.get("crType").getAsString();
                
                // Delete existing mappings for this process definition
                dao.deleteByProcessDefinitionId(processDefId);

                // Special handling for Type 2: create 2 rows
                if ("Type 2".equals(crType) || "2".equals(crType)) {
                    // Create mapping for Type 1
                    WorkflowMapping mapping1 = new WorkflowMapping(processDefId, "Type 1", entityId);
                    mapping1.setLastUserChange(userId);
                    dao.create(mapping1);

                    // Create mapping for Type 2
                    WorkflowMapping mapping2 = new WorkflowMapping(processDefId, "Type 2", entityId);
                    mapping2.setLastUserChange(userId);
                    dao.create(mapping2);

                    logger.info("Created 2 mappings for Type 2 selection");

                    JsonObject result = new JsonObject();
                    result.addProperty("success", true);
                    result.addProperty("count", 2);
                    response.getWriter().write(gson.toJson(result));

                } else {
                    // Create single mapping
                    WorkflowMapping mapping = new WorkflowMapping(processDefId, crType, entityId);
                    mapping.setLastUserChange(userId);
                    int id = dao.create(mapping);

                    mapping.setId(id);
                    response.setStatus(HttpServletResponse.SC_CREATED);
                    response.getWriter().write(gson.toJson(mapping));
                }
            }

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
            WorkflowMapping mapping = gson.fromJson(request.getReader(), WorkflowMapping.class);
            mapping.setId(id);

            // Get user ID from session
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("userId") != null) {
                mapping.setLastUserChange((Integer) session.getAttribute("userId"));
            }

            dao.update(mapping);
            response.getWriter().write(gson.toJson(mapping));

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
            dao.delete(id);

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
}
