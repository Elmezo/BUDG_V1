package com.example.budg_v2;

import com.example.budg_v2.model.RegulationStage;
import com.example.budg_v2.service.RegulationStageService;
import com.example.budg_v2.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

@WebServlet(name = "RegulationStageServlet", urlPatterns = {"/api/regulation-stage/*"})
public class RegulationStageServlet extends HttpServlet {
    
    private RegulationStageService regulationStageService;

    @Override
    public void init() throws ServletException {
        super.init();
        this.regulationStageService = new RegulationStageService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        // Enable CORS
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/list")) {
                // GET /api/regulation-stage/list - Get all regulation stages
                handleGetAllRegulationStages(response);
            } else {
                // GET /api/regulation-stage/{id} - Get specific regulation stage
                String[] pathParts = pathInfo.split("/");
                if (pathParts.length == 2) {
                    try {
                        int id = Integer.parseInt(pathParts[1]);
                        handleGetRegulationStageById(id, response);
                    } catch (NumberFormatException e) {
                        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid regulation stage ID format");
                    }
                } else {
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL format");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Handle CORS preflight requests
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void handleGetAllRegulationStages(HttpServletResponse response) throws SQLException, IOException {
        List<RegulationStage> regulationStages = regulationStageService.getAllRegulationStages();
        
        PrintWriter out = response.getWriter();
        out.print(JsonUtil.toJson(regulationStages));
        out.flush();
    }

    private void handleGetRegulationStageById(int id, HttpServletResponse response) throws SQLException, IOException {
        RegulationStage regulationStage = regulationStageService.getRegulationStageById(id);
        
        if (regulationStage != null) {
            PrintWriter out = response.getWriter();
            out.print(JsonUtil.toJson(regulationStage));
            out.flush();
        } else {
            sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Regulation stage not found");
        }
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        PrintWriter out = response.getWriter();
        out.print("{\"error\": \"" + message + "\"}");
        out.flush();
    }
}
