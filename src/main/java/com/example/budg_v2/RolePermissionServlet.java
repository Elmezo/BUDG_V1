package com.example.budg_v2;

import com.example.budg_v2.dao.RolePermissionDAO;
import com.example.budg_v2.util.AppRoleNames;
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

@WebServlet("/api/role-permissions")
public class RolePermissionServlet extends HttpServlet {
    private transient RolePermissionDAO dao;
    private transient Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        this.dao = new RolePermissionDAO();
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // V-08: Role Permissions configuration is SuperAdmin-only (server-side enforcement)
        Object roleAttr = req.getAttribute("userRole");
        if (!AppRoleNames.isSuperAdminName(roleAttr != null ? roleAttr.toString() : "")) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Forbidden: Super Admin role required to access Role Permissions");
            error.put("code", "FORBIDDEN");
            resp.getWriter().write(gson.toJson(error));
            return;
        }

        String action = req.getParameter("action");
        try {
            if (action == null || action.equals("facets")) {
                List<String> permissionClasses = dao.getDistinctPermissionClasses();
                List<String> roleClasses = dao.getDistinctRoleClasses();
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("permissionClasses", permissionClasses);
                result.put("roleClasses", roleClasses);
                resp.getWriter().write(gson.toJson(result));
            } else {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("error", "Unsupported action");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(gson.toJson(result));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
}


