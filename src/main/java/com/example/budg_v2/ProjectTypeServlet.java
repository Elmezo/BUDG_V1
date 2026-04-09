package com.example.budg_v2;

import com.example.budg_v2.model.ProjectType;
import com.example.budg_v2.service.ProjectTypeService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet("/api/project/type/list")
public class ProjectTypeServlet extends HttpServlet {

    private ProjectTypeService projectTypeService;

    @Override
    public void init() {
        this.projectTypeService = new ProjectTypeService();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            List<ProjectType> projectTypes = projectTypeService.getAllProjectTypes();
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (ProjectType pt : projectTypes) {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("id", pt.getId());
                o.addProperty("primaryname", pt.getPrimaryName());
                arr.add(o);
            }
            response.getWriter().write(arr.toString());
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error retrieving project types: " + e.getMessage(), 500);
        }
    }

    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
