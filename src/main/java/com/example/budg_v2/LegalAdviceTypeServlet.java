package com.example.budg_v2;

import com.example.budg_v2.service.LegalAdviceTypeService;
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

@WebServlet("/api/legal-advice-type/*")
public class LegalAdviceTypeServlet extends HttpServlet {
    private final LegalAdviceTypeService service;
    
    public LegalAdviceTypeServlet() {
        this.service = new LegalAdviceTypeService();
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/list")) {
                // GET /api/legal-advice-type or /api/legal-advice-type/list
                List<Map<String, Object>> legalAdviceTypes = service.getAll();
                response.getWriter().write(JsonUtil.toJson(legalAdviceTypes));
            } else if (pathInfo.matches("/\\d+")) {
                // GET /api/legal-advice-type/{id}
                int id = Integer.parseInt(pathInfo.substring(1));
                List<Map<String, Object>> legalAdviceTypes = service.getAll();
                Map<String, Object> legalAdviceType = legalAdviceTypes.stream()
                    .filter(item -> (Integer) item.get("id") == id)
                    .findFirst()
                    .orElse(null);
                
                if (legalAdviceType != null) {
                    response.getWriter().write(JsonUtil.toJson(legalAdviceType));
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().write("{\"error\":\"Legal advice type not found\"}");
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Invalid endpoint\"}");
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid legal advice type ID\"}");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal server error: " + e.getMessage() + "\"}");
        }
    }
}
