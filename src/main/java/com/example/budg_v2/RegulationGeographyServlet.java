package com.example.budg_v2;

import com.example.budg_v2.service.RegulationGeographyService;
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

@WebServlet("/api/regulation-geography/*")
public class RegulationGeographyServlet extends HttpServlet {
    private final RegulationGeographyService service;
    
    public RegulationGeographyServlet() {
        this.service = new RegulationGeographyService();
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        String includeInheritance = request.getParameter("includeInheritance");
        
        try {
            if (pathInfo != null && pathInfo.matches("/\\d+")) {
                // GET /api/regulation-geography/{regulationId}?includeInheritance=true
                int regulationId = Integer.parseInt(pathInfo.substring(1));
                
                List<Map<String, Object>> geographies;
                if ("true".equals(includeInheritance)) {
                    geographies = service.getGeographiesWithInheritance(regulationId);
                } else {
                    geographies = service.getGeographiesByRegulationId(regulationId);
                }
                
                response.getWriter().write(JsonUtil.toJson(geographies));
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Invalid endpoint\"}");
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid regulation ID\"}");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal server error: " + e.getMessage() + "\"}");
        }
    }
}