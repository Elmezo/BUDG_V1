package com.example.budg_v2;

import com.example.budg_v2.dao.PermissionsDAO;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/permissions/names")
public class PermissionNamesServlet extends HttpServlet {
    private transient PermissionsDAO dao;
    private transient Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        this.dao = new PermissionsDAO();
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CorsUtil.setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        try {
            List<Map<String, Object>> permissionNames = dao.getAllPermissionNames();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", permissionNames);
            resp.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CorsUtil.handlePreflight(resp);
    }
}
