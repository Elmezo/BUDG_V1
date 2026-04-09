package com.example.budg_v2;

import com.example.budg_v2.model.PolicyLifecycleStatus;
import com.example.budg_v2.service.PolicyLifecycleStatusService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet(name = "PolicyLifecycleServlet", urlPatterns = {"/api/policy/lifecycle/list"})
public class PolicyLifecycleServlet extends HttpServlet {

    private final PolicyLifecycleStatusService policyLifecycleStatusService;

    public PolicyLifecycleServlet() {
        this.policyLifecycleStatusService = new PolicyLifecycleStatusService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            // Return simplified list with id and primaryname for dropdown
            List<PolicyLifecycleStatus> statuses = policyLifecycleStatusService.getAllPolicyLifecycleStatusesForDropdown();
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (PolicyLifecycleStatus s : statuses) {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("id", s.getId());
                o.addProperty("primaryname", s.getPrimaryName());
                arr.add(o);
            }
            response.getWriter().write(arr.toString());
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error retrieving policy lifecycles: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
