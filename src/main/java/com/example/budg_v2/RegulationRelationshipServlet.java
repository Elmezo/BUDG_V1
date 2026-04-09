package com.example.budg_v2;

import com.example.budg_v2.service.RegulationRelationshipService;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/regulation-relationships/*")
public class RegulationRelationshipServlet extends HttpServlet {
    private final RegulationRelationshipService service;
    
    public RegulationRelationshipServlet() {
        this.service = new RegulationRelationshipService();
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo != null && pathInfo.matches("/\\d+")) {
                // GET /api/regulation-relationships/{regulationId}
                int regulationId = Integer.parseInt(pathInfo.substring(1));
                List<Map<String, Object>> relationships = service.getRelationshipsByRegulationId(regulationId);
                // V-05: hide related regulations that the current user cannot access
                relationships = RelationshipAccessUtil.filterBySegmentAccess(
                        relationships, request, "Regulation", "relatedRegulationId");
                response.getWriter().write(JsonUtil.toJson(relationships));
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Invalid endpoint. Use /api/regulation-relationships/{regulationId}\"}");
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
