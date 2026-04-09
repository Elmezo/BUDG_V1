package com.example.budg_v2;

import com.example.budg_v2.service.DefaultWorkflowService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Servlet for restarting/resetting default workflows
 * Endpoint: /admin/api/default-workflows/restart
 */
@WebServlet("/admin/api/default-workflows/restart")
public class DefaultWorkflowRestartServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DefaultWorkflowRestartServlet.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final DefaultWorkflowService defaultWorkflowService = new DefaultWorkflowService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        // Enable CORS
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        try {
            logger.info("Default workflow restart requested");
            
            // Call service to reset all default workflows
            DefaultWorkflowService.ResetResult result = defaultWorkflowService.resetAllDefaultWorkflows();
            
            // Build response
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("created", result.getCreated());
            response.addProperty("reset", result.getReset());
            
            // Add errors if any
            if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                response.add("errors", gson.toJsonTree(result.getErrors()));
            } else {
                response.add("errors", gson.toJsonTree(new String[0]));
            }
            
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(response));
            
            logger.info("Default workflow restart completed. Created: {}, Reset: {}, Errors: {}", 
                        result.getCreated(), result.getReset(), result.getErrors().size());
            
        } catch (Exception e) {
            logger.error("Error restarting default workflows", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", "Failed to restart default workflows: " + e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}

