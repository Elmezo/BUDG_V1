package com.example.budg_v2;

import com.example.budg_v2.service.RegulationXRegulatoryThemeService;
import com.example.budg_v2.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/regulation-regulatory-theme/*")
public class RegulationRegulatoryThemeServlet extends HttpServlet {
    private final RegulationXRegulatoryThemeService service;
    
    public RegulationRegulatoryThemeServlet() {
        this.service = new RegulationXRegulatoryThemeService();
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo != null && pathInfo.matches("/\\d+")) {
                // GET /api/regulation-regulatory-theme/{regulationId}
                int regulationId = Integer.parseInt(pathInfo.substring(1));
                List<Map<String, Object>> regulatoryThemes = service.getRegulatoryThemesByRegulationId(regulationId);
                response.getWriter().write(JsonUtil.toJson(regulatoryThemes));
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Invalid endpoint. Use /api/regulation-regulatory-theme/{regulationId}\"}");
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid regulation ID format\"}");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal server error: " + e.getMessage() + "\"}");
        }
    }
}
