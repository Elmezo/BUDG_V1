package com.example.budg_v2;

import com.example.budg_v2.dao.HistoryDAO;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet(name = "HistoryServlet", urlPatterns = {"/api/history/recent", "/api/history/visit"})
public class HistoryServlet extends HttpServlet {

    private final HistoryDAO dao = new HistoryDAO();
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
        try {
            // Get current user ID from session/request attributes
            int currentUserId = UserContextUtil.getCurrentUserId(req);
            int limit = 30;
            List<Map<String, Object>> list = dao.recentForUser(currentUserId, limit);
            resp.getWriter().write(gson.toJson(list));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            e.printStackTrace(); // Log the full error for debugging
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage().replace("\"", "\\\"") + "\"}");
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            e.printStackTrace(); // Log the full error for debugging
            resp.getWriter().write("{\"error\":\"Error: " + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);
        try {
            String body = readBody(req);
            JsonObject json = gson.fromJson(body, JsonObject.class);
            // Get current user ID from session/request attributes instead of JSON payload
            Long userId = (long) UserContextUtil.getCurrentUserId(req);
            String entity = json.has("entity") ? json.get("entity").getAsString() : null;
            String entityId = json.has("entityId") ? json.get("entityId").getAsString() : null;
            String route = json.has("route") ? json.get("route").getAsString() : null;
            String routeParams = json.has("routeParams") && !json.get("routeParams").isJsonNull() ? json.get("routeParams").toString() : null;

            if (entity == null || entityId == null || route == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Missing fields\"}");
                return;
            }

            long id = dao.insertVisit(userId, entity, entityId, route, routeParams);
            resp.getWriter().write("{\"success\":true,\"id\":" + id + "}");
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private static String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}


