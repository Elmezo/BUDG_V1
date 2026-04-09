package com.example.budg_v2;

import com.example.budg_v2.dao.PermissionNameDAO;
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

@WebServlet("/api/permission-names")
public class PermissionNameServlet extends HttpServlet {
    private transient PermissionNameDAO dao;
    private transient Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        this.dao = new PermissionNameDAO();
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            List<String> names = dao.getAllPermissionNames();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", names);
            resp.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}


