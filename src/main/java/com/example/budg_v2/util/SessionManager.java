package com.example.budg_v2.util;

import com.example.budg_v2.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public class SessionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    
    public static String registerSession(int userId, String ip, String userAgent) {
        String sessionId = java.util.UUID.randomUUID().toString().replace("-", "");
        String deviceInfo = parseBrowser(userAgent) + " / " + parseOperatingSystem(userAgent);
        String approxLocation = getLocationFromIP(ip);
        String sql = "INSERT INTO auth_sessions (session_id, user_id, ip_address, user_agent, device_info, location, created_at, last_activity) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setInt(2, userId);
            ps.setString(3, ip);
            ps.setString(4, userAgent);
            ps.setString(5, deviceInfo);
            ps.setString(6, approxLocation);
            ps.executeUpdate();
            return sessionId;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register session", e);
        }
    }

    public static boolean touch(String sessionId) {
        String sql = "UPDATE auth_sessions SET last_activity = ? WHERE session_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, sessionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to touch session {}", sessionId, e);
            return false;
        }
    }

    public static boolean exists(String sessionId) {
        String sql = "SELECT 1 FROM auth_sessions WHERE session_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Failed to check session existence", e);
            return false;
        }
    }

    public static void invalidate(String sessionId) {
        // Mark tokens revoked for this session and delete the session
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement("UPDATE auth_tokens SET is_revoked = TRUE WHERE session_id = ?");
                 PreparedStatement ps2 = conn.prepareStatement("DELETE FROM auth_sessions WHERE session_id = ?")) {
                ps1.setString(1, sessionId);
                ps1.executeUpdate();
                ps2.setString(1, sessionId);
                ps2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to invalidate session", e);
        }
    }

    public static void invalidateAllUserSessions(int userId) {
        // Collect all sessions for user and invalidate
        String sql = "SELECT session_id FROM auth_sessions WHERE user_id = ?";
        Set<String> sessions = new HashSet<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) sessions.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list user sessions", e);
        }
        for (String sid : sessions) {
            invalidate(sid);
        }
    }

    private static String parseBrowser(String userAgent) {
        if (userAgent == null) return "Unknown Browser";
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("chrome")) return "Chrome";
        if (ua.contains("firefox")) return "Firefox";
        if (ua.contains("safari")) return "Safari";
        if (ua.contains("opera") || ua.contains("opr/")) return "Opera";
        return "Unknown Browser";
    }
    
    private static String parseOperatingSystem(String userAgent) {
        if (userAgent == null) return "Unknown OS";
        String ua = userAgent.toLowerCase();
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("mac os") || ua.contains("macintosh")) return "macOS";
        if (ua.contains("linux")) return "Linux";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return "iOS";
        return "Unknown OS";
    }
    
    private static String getLocationFromIP(String ipAddress) {
        if (ipAddress == null) return "Unknown";
        if (ipAddress.startsWith("192.168.") || ipAddress.startsWith("10.") || ipAddress.startsWith("172.")) {
            return "Local Network";
        }
        if ("127.0.0.1".equals(ipAddress) || "::1".equals(ipAddress)) {
            return "Localhost";
        }
        // Simple placeholder: return prefix only
        int dot = ipAddress.indexOf('.');
        return dot > 0 ? (ipAddress.substring(0, dot) + ".x.x.x") : ipAddress;
    }
}

