package com.example.budg_v2;

import com.google.gson.Gson;

import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.JwtUtil;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@WebServlet(urlPatterns = {"/api/me", "/auth/validate"})
public class MeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Object firstName = request.getAttribute("userName");
        Object role = request.getAttribute("userRole");
        Object avatar = request.getAttribute("userAvatar");
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            // Fallback: parse ACCESS_TOKEN cookie directly if filter didn't set attributes
            try {
                String token = getCookie(request, "ACCESS_TOKEN");
                if (token != null) {
                    JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                    request.setAttribute("userEmail", claims.getStringClaim("email"));
                    request.setAttribute("userName", (claims.getStringClaim("firstName") + " " + claims.getStringClaim("lastName")).trim());
                    request.setAttribute("userRole", claims.getStringClaim("role"));
                    request.setAttribute("userAvatar", claims.getStringClaim("avatarPath"));
                    request.setAttribute("userId", JwtUtil.getUserIdFromToken(token));
                    userId = request.getAttribute("userId");
                    firstName = request.getAttribute("userName");
                    role = request.getAttribute("userRole");
                    avatar = request.getAttribute("userAvatar");
                }
            } catch (Exception e) {
                // Ignore and fall through to unauthenticated response
            }
        }
        if (userId == null) {
            sendGuestResponse(response);
            return;
        }
        String userName = firstName == null ? "" : String.valueOf(firstName);
        String[] parts = userName.trim().split(" ", 2);
        String fn = parts.length > 0 ? parts[0] : "";
        String ln = parts.length > 1 ? parts[1] : "";

        // Get current role from database instead of JWT token
        // This ensures the role is always up-to-date even if it was changed after login
        String currentRole = role != null ? String.valueOf(role) : null; // Fallback to JWT role if DB fetch fails
        if (userId != null) {
            try {
                int userIdInt;
                if (userId instanceof Integer) {
                    userIdInt = (Integer) userId;
                } else if (userId instanceof Number) {
                    userIdInt = ((Number) userId).intValue();
                } else {
                    try {
                        userIdInt = Integer.parseInt(String.valueOf(userId));
                    } catch (NumberFormatException e) {
                        userIdInt = 0;
                    }
                }
                
                if (userIdInt > 0) {
                    String dbRole = SegmentAccessService.getUserRole(userIdInt);
                    if (dbRole != null && !dbRole.trim().isEmpty()) {
                        currentRole = dbRole;
                    }
                }
            } catch (SQLException e) {
                // Log error but continue with JWT role as fallback
                System.err.println("Error fetching user role from database: " + e.getMessage());
            }
        }

        String userLocale = null;
        try {
            int userIdInt = userId instanceof Integer ? (Integer) userId
                : (userId instanceof Number ? ((Number) userId).intValue() : Integer.parseInt(String.valueOf(userId)));
            if (userIdInt > 0) {
                userLocale = SegmentAccessService.getUserLocale(userIdInt);
            }
        } catch (Exception e) {
            // ignore
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("id", userId);
        responseData.put("firstName", fn);
        responseData.put("lastName", ln);
        responseData.put("role", currentRole);
        responseData.put("avatarPath", avatar);
        responseData.put("locale", userLocale);
        responseData.put("authenticated", true);
        response.setStatus(200);
        response.getWriter().write(new Gson().toJson(responseData));
    }

    private static final java.util.Set<String> ALLOWED_LOCALES = java.util.Set.of("en", "ar");

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(request.getMethod())) {
            doPatchLocale(request, response);
            return;
        }
        super.service(request, response);
    }

    private void doPatchLocale(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Object userIdObj = request.getAttribute("userId");
        if (userIdObj == null) {
            try {
                String token = getCookie(request, "ACCESS_TOKEN");
                if (token != null) {
                    userIdObj = JwtUtil.getUserIdFromToken(token);
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (userIdObj == null) {
            response.setStatus(401);
            response.getWriter().write(new Gson().toJson(Map.of("error", "Unauthorized")));
            return;
        }

        int userId;
        try {
            if (userIdObj instanceof Integer) {
                userId = (Integer) userIdObj;
            } else if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).intValue();
            } else {
                userId = Integer.parseInt(String.valueOf(userIdObj));
            }
        } catch (NumberFormatException e) {
            response.setStatus(400);
            response.getWriter().write(new Gson().toJson(Map.of("error", "Invalid user")));
            return;
        }

        String body = request.getReader().lines().reduce("", (a, b) -> a + b);
        com.google.gson.JsonObject json;
        try {
            json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            response.setStatus(400);
            response.getWriter().write(new Gson().toJson(Map.of("error", "Invalid JSON")));
            return;
        }
        if (!json.has("locale") || !json.get("locale").isJsonPrimitive()) {
            response.setStatus(400);
            response.getWriter().write(new Gson().toJson(Map.of("error", "Missing or invalid locale")));
            return;
        }
        String locale = json.get("locale").getAsString();
        if (locale == null || locale.isBlank() || !ALLOWED_LOCALES.contains(locale.trim())) {
            response.setStatus(400);
            response.getWriter().write(new Gson().toJson(Map.of("error", "Locale must be one of: en, ar")));
            return;
        }
        locale = locale.trim();

        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "UPDATE people SET Locale = ?, Last_Updated = NOW() WHERE ID = ? AND (Deleted_date IS NULL OR Deleted_date = '')")) {
            ps.setString(1, locale);
            ps.setInt(2, userId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                response.setStatus(404);
                response.getWriter().write(new Gson().toJson(Map.of("error", "User not found")));
                return;
            }
        } catch (SQLException e) {
            System.err.println("MeServlet PATCH locale error: " + e.getMessage());
            response.setStatus(500);
            response.getWriter().write(new Gson().toJson(Map.of("error", "Failed to update locale")));
            return;
        }

        response.setStatus(200);
        response.getWriter().write(new Gson().toJson(Map.of("locale", locale)));
    }

    private void sendGuestResponse(HttpServletResponse response)
            throws IOException {
        response.setStatus(200);
        Map<String, Object> guest = new HashMap<>();
        guest.put("authenticated", false);
        response.getWriter().write(new Gson().toJson(guest));
    }

    private String getCookie(jakarta.servlet.http.HttpServletRequest request, String name) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode)
            throws IOException {
        response.setStatus(statusCode);
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", message);
        response.getWriter().write(new Gson().toJson(errorResponse));
    }
}
