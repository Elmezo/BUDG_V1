package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.SessionManager;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(urlPatterns = {"/api/sessions/*", "/api/view/sessions/*", "/api/create/sessions/*"})
public class SessionManagementServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        if (pathInfo != null && pathInfo.equals("/stats")) {
            handleStats(request, response);
            return;
        }

        handleListUserSessions(request, response);
    }
    
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Session ID required\"}");
            return;
        }

        if ("/all".equals(pathInfo)) {
            handleInvalidateAllExceptCurrent(request, response);
            return;
        }

        // Expect /{sessionId}
        String sessionId = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        handleInvalidateOne(request, response, sessionId);
    }

    private void handleListUserSessions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer userId = (Integer) request.getAttribute("userId");
        String currentSessionId = (String) request.getAttribute("sessionId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }
        
        String sql = "SELECT session_id, ip_address, user_agent, device_info, location, created_at, last_activity FROM auth_sessions WHERE user_id = ? ORDER BY created_at DESC";
        List<Map<String, Object>> sessions = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> s = new HashMap<>();
                    s.put("sessionId", rs.getString(1));
                    s.put("ipAddress", rs.getString(2));
                    s.put("userAgent", rs.getString(3));
                    s.put("deviceInfo", rs.getString(4));
                    s.put("location", rs.getString(5));
                    s.put("createdAt", rs.getTimestamp(6));
                    s.put("lastActivity", rs.getTimestamp(7));
                    s.put("isCurrent", rs.getString(1).equals(currentSessionId));
                    sessions.add(s);
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to load sessions\"}");
            return;
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("sessions", sessions);
        response.getWriter().write(new Gson().toJson(resp));
    }

    private void handleInvalidateOne(HttpServletRequest request, HttpServletResponse response, String sessionId) throws IOException {
        Integer userId = (Integer) request.getAttribute("userId");
        String currentSessionId = (String) request.getAttribute("sessionId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        if (sessionId != null && sessionId.equals(currentSessionId)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Cannot invalidate current session\"}");
            return;
        }
        
        String sql = "SELECT 1 FROM auth_sessions WHERE session_id = ? AND user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().write("{\"error\":\"Session not found\"}");
                    return;
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to validate session\"}");
            return;
        }
        
        // Reuse manager to revoke tokens and delete session
        try {
            SessionManager.invalidate(sessionId);
        } catch (RuntimeException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to invalidate session\"}");
            return;
        }

        response.getWriter().write("{\"success\":true}");
    }

    private void handleInvalidateAllExceptCurrent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer userId = (Integer) request.getAttribute("userId");
        String currentSessionId = (String) request.getAttribute("sessionId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        String sql = "SELECT session_id FROM auth_sessions WHERE user_id = ? AND session_id <> ?";
        int count = 0;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, currentSessionId == null ? "" : currentSessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sid = rs.getString(1);
                    try {
                        SessionManager.invalidate(sid);
                        count++;
                    } catch (RuntimeException ignored) {}
                }
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to invalidate sessions\"}");
            return;
        }
        
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("invalidatedSessions", count);
        response.getWriter().write(new Gson().toJson(resp));
    }

    private void handleStats(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sqlTotal = "SELECT COUNT(*) FROM auth_sessions";
        String sqlUsers = "SELECT COUNT(DISTINCT user_id) FROM auth_sessions";
        String sqlMaxActivity = "SELECT MAX(last_activity) FROM auth_sessions";
        String sqlTopUsers = "SELECT user_id, COUNT(*) AS c FROM auth_sessions GROUP BY user_id ORDER BY c DESC LIMIT 5";
        String sqlPerRole = "SELECT COALESCE(p.System_Role, 0) AS role_id, COUNT(*) AS c FROM auth_sessions s JOIN people p ON p.ID = s.user_id GROUP BY role_id ORDER BY c DESC";

        Map<String, Object> stats = new HashMap<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlTotal); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.put("totalActiveSessions", rs.getInt(1));
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlUsers); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.put("totalUsers", rs.getInt(1));
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlMaxActivity); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.put("lastActivity", rs.getTimestamp(1));
            }
            // Top 5 users by session count
            List<Map<String, Object>> topUsers = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sqlTopUsers); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> u = new HashMap<>();
                    u.put("userId", rs.getInt(1));
                    u.put("sessions", rs.getInt(2));
                    topUsers.add(u);
                }
            }
            stats.put("topUsers", topUsers);

            // Sessions per role
            List<Map<String, Object>> perRole = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sqlPerRole); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("roleId", rs.getInt(1));
                    r.put("sessions", rs.getInt(2));
                    perRole.add(r);
                }
            }
            stats.put("sessionsPerRole", perRole);

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to load stats\"}");
            return;
        }

        response.getWriter().write(new Gson().toJson(stats));
    }
}


