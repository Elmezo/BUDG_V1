package com.example.budg_v2;

import com.example.budg_v2.model.ProjectRag;
import com.example.budg_v2.service.ProjectRagService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet("/api/project/rag/list")
public class ProjectRagServlet extends HttpServlet {

    private ProjectRagService projectRagService;

    @Override
    public void init() {
        this.projectRagService = new ProjectRagService();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            List<ProjectRag> rags = projectRagService.getAllProjectRags();
            JsonUtil.sendJsonResponse(response.getWriter(), rags);
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error retrieving project rags: " + e.getMessage(), 500);
        }
    }

    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
