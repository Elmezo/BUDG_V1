package com.example.budg_v2;

import com.example.budg_v2.model.PolicyType;
import com.example.budg_v2.service.PolicyTypeService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/api/policy-type/list")
public class PolicyTypeServlet extends HttpServlet {
    private PolicyTypeService policyTypeService;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        policyTypeService = new PolicyTypeService();
        gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            List<PolicyType> policyTypes = policyTypeService.getAllPolicyTypes();
            
            // Return simplified list for dropdowns
            JsonArray jsonArray = new JsonArray();
            for (PolicyType policyType : policyTypes) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", policyType.getId());
                obj.addProperty("primaryname", policyType.getPrimaryName());
                jsonArray.add(obj);
            }
            
            response.getWriter().write(gson.toJson(jsonArray));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
