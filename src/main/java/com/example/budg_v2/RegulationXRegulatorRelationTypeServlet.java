package com.example.budg_v2;

import com.example.budg_v2.service.RegulationXRegulatorRelationTypeService;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/api/regulation-x-regulator-relationtype/*")
public class RegulationXRegulatorRelationTypeServlet extends HttpServlet {
    
    private RegulationXRegulatorRelationTypeService service = new RegulationXRegulatorRelationTypeService();
    private Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        try {
            String pathInfo = request.getPathInfo();
            
            if (pathInfo == null || pathInfo.equals("/")) {
                // GET /api/regulation-x-regulator-relationtype/
                out.print(gson.toJson(service.getAllRelationTypes()));
            } else if ("/list".equals(pathInfo)) {
                // GET /api/regulation-x-regulator-relationtype/list
                out.print(gson.toJson(service.getAllRelationTypes()));
            } else {
                // GET /api/regulation-x-regulator-relationtype/{id}
                int id = Integer.parseInt(pathInfo.substring(1));
                out.print(gson.toJson(service.getById(id)));
            }
            
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\": \"Invalid ID\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
    }
}
