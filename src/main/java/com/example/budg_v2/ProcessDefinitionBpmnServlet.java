package com.example.budg_v2;

import com.example.budg_v2.dao.BpmnContentDAO;
import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.BpmnFileManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Servlet for BPMN file operations
 * Endpoints:
 * GET /api/process_definitions/{id}/bpmn - Get BPMN XML
 * POST /api/process_definitions/{id}/bpmn - Save BPMN XML
 */
@WebServlet("/api/process_definitions/*/bpmn")
public class ProcessDefinitionBpmnServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ProcessDefinitionBpmnServlet.class);
    private final Gson gson = new Gson();
    private final BpmnContentDAO dao = new BpmnContentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // Prevent caching of BPMN XML to ensure fresh data after updates
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        try {
            int processDefId = extractProcessDefId(request);

            // Try to load from database first
            String xmlContent = dao.getXmlContent(processDefId);

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

        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
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

        // BPMN diagram saving is Super Admin only
        Object roleAttr = request.getAttribute("userRole");
        if (!AppRoleNames.isSuperAdminName(roleAttr != null ? roleAttr.toString() : "")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Forbidden: Super Admin role required to save workflow diagrams");
            response.getWriter().write(gson.toJson(error));
            return;
        }

        try {
            int processDefId = extractProcessDefId(request);

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

            // Save to database
            dao.saveOrUpdate(processDefId, xmlContent);

            // Save to file system
            BpmnFileManager.saveBpmnFile(processDefId, xmlContent);

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("path", BpmnFileManager.getAbsolutePath(processDefId));
            response.getWriter().write(gson.toJson(result));

            logger.info("Saved BPMN for process definition: {}", processDefId);

        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
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
     * Extract process definition ID from URL path
     * Handles both /api/process_definitions/{id}/bpmn and /api/process_definitions/{id}/bpmn patterns
     */
    private int extractProcessDefId(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        String requestURI = request.getRequestURI();
        
        // Try to extract from pathInfo first
        if (pathInfo != null && !pathInfo.isEmpty()) {
            // Path format: /{id}/bpmn or /{id}
            String[] parts = pathInfo.split("/");
            for (String part : parts) {
                if (!part.isEmpty() && !part.equals("bpmn")) {
                    try {
                        return Integer.parseInt(part);
                    } catch (NumberFormatException e) {
                        // Continue to next part
                    }
                }
            }
        }
        
        // Fallback: try to extract from request URI
        if (requestURI != null) {
            // Pattern: /api/process_definitions/{id}/bpmn
            String[] uriParts = requestURI.split("/");
            for (int i = 0; i < uriParts.length; i++) {
                if ("process_definitions".equals(uriParts[i]) && i + 1 < uriParts.length) {
                    try {
                        return Integer.parseInt(uriParts[i + 1]);
                    } catch (NumberFormatException e) {
                        // Continue
                    }
                }
            }
        }
        
        throw new IllegalArgumentException("Process definition ID is required and must be a valid number");
    }
}
