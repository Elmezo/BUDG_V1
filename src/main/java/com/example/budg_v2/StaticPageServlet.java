package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "StaticPageServlet", urlPatterns = { "/admin/api/static-pages/*" })
public class StaticPageServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (pathInfo == null || pathInfo.equals("/")) {
                // Get All
                List<Map<String, Object>> pages = new ArrayList<>();
                String sql = "SELECT * FROM static_pages";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                        ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> page = new HashMap<>();
                        page.put("id", rs.getInt("id"));
                        page.put("title", rs.getString("title"));
                        page.put("link", rs.getString("link"));
                        page.put("context", rs.getString("context"));
                        page.put("is_homepage", rs.getBoolean("is_homepage"));
                        // Content might be large, maybe don't fetch it in list view if not needed
                        // But for now, let's include it or maybe just a snippet if needed
                        page.put("content", rs.getString("content"));
                        pages.add(page);
                    }
                }
                resp.getWriter().write(gson.toJson(pages));
            } else {
                // Get One by ID
                try {
                    String idStr = pathInfo.substring(1);
                    int id = Integer.parseInt(idStr);
                    String sql = "SELECT * FROM static_pages WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                Map<String, Object> page = new HashMap<>();
                                page.put("id", rs.getInt("id"));
                                page.put("title", rs.getString("title"));
                                page.put("link", rs.getString("link"));
                                page.put("context", rs.getString("context"));
                                page.put("is_homepage", rs.getBoolean("is_homepage"));
                                page.put("content", rs.getString("content"));
                                resp.getWriter().write(gson.toJson(page));
                            } else {
                                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                                resp.getWriter().write("{\"error\":\"Page not found\"}");
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Invalid ID\"}");
                }
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"ID is required for update\"}");
            return;
        }

        try {
            int id = Integer.parseInt(pathInfo.substring(1));

            BufferedReader reader = req.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            JsonObject requestData = JsonParser.parseString(jsonBuilder.toString()).getAsJsonObject();

            try (Connection conn = DatabaseConnection.getConnection()) {
                // We only allow updating content, title, link, context, is_homepage
                // But based on requirements, mainly content is editable by user, but let's
                // support all

                StringBuilder sqlBuilder = new StringBuilder("UPDATE static_pages SET ");
                List<Object> params = new ArrayList<>();
                boolean first = true;

                if (requestData.has("title")) {
                    sqlBuilder.append("title = ?");
                    params.add(requestData.get("title").getAsString());
                    first = false;
                }
                if (requestData.has("link")) {
                    if (!first)
                        sqlBuilder.append(", ");
                    sqlBuilder.append("link = ?");
                    params.add(requestData.get("link").getAsString());
                    first = false;
                }
                if (requestData.has("context")) {
                    if (!first)
                        sqlBuilder.append(", ");
                    sqlBuilder.append("context = ?");
                    params.add(requestData.get("context").getAsString());
                    first = false;
                }
                if (requestData.has("is_homepage")) {
                    if (!first)
                        sqlBuilder.append(", ");
                    sqlBuilder.append("is_homepage = ?");
                    params.add(requestData.get("is_homepage").getAsBoolean());
                    first = false;
                }
                if (requestData.has("content")) {
                    if (!first)
                        sqlBuilder.append(", ");
                    sqlBuilder.append("content = ?");
                    params.add(requestData.get("content").getAsString());
                    first = false;
                }

                sqlBuilder.append(" WHERE id = ?");
                params.add(id);

                if (first) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"No fields to update\"}");
                    return;
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlBuilder.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        Object param = params.get(i);
                        if (param instanceof String) {
                            ps.setString(i + 1, (String) param);
                        } else if (param instanceof Boolean) {
                            ps.setBoolean(i + 1, (Boolean) param);
                        } else if (param instanceof Integer) {
                            ps.setInt(i + 1, (Integer) param);
                        }
                    }

                    int rowsAffected = ps.executeUpdate();
                    if (rowsAffected > 0) {
                        resp.getWriter().write("{\"success\":true,\"message\":\"Page updated successfully\"}");
                    } else {
                        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        resp.getWriter().write("{\"error\":\"Page not found\"}");
                    }
                }
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid ID\"}");
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }
}
