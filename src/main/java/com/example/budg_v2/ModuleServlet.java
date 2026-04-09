package com.example.budg_v2;

import com.example.budg_v2.dao.ModuleDAO;
import com.example.budg_v2.model.Module;
import com.example.budg_v2.model.ModuleGroup;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet for handling Module-related operations
 */
@WebServlet("/api/modules")
public class ModuleServlet extends HttpServlet {
    private ModuleDAO moduleDAO;

    @Override
    public void init() throws ServletException {
        super.init();
        moduleDAO = new ModuleDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Handle CORS
        CorsUtil.setCorsHeaders(response);
        
        try {
            String action = request.getParameter("action");

            if ("list".equals(action)) {
                // Get all modules
                List<Module> modules = moduleDAO.getAllModules();
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", modules);
                result.put("count", modules.size());
                
                JsonUtil.sendJsonResponse(response.getWriter(), result);
            } else {
                // Legacy response for callers expecting groups and modules without envelope
                List<ModuleGroup> groups = moduleDAO.getAllModuleGroups();
                List<Module> modules = moduleDAO.getAllModules();

                Map<String, Object> legacy = new HashMap<>();
                legacy.put("groups", groups);
                legacy.put("modules", modules);

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(new Gson().toJson(legacy));
            }
            
        } catch (SQLException e) {
            System.err.println("Database error in ModuleServlet: " + e.getMessage());
            e.printStackTrace();
            JsonUtil.sendErrorResponse(response.getWriter(), "Database error occurred", 500);
        } catch (Exception e) {
            System.err.println("Unexpected error in ModuleServlet: " + e.getMessage());
            e.printStackTrace();
            JsonUtil.sendErrorResponse(response.getWriter(), "Internal server error", 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        CorsUtil.handlePreflight(response);
    }
}
