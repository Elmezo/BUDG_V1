package com.example.budg_v2.util;

import com.example.budg_v2.service.SegmentAccessService;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.SQLException;

/**
 * Utility class for getting user context information from HTTP requests
 */
public class UserContextUtil {
    
    /**
     * Gets the current user ID from the request attributes, JWT token, or session
     * @param request The HTTP request
     * @return The user ID, or 0 if not found
     */
    public static int getCurrentUserId(HttpServletRequest request) {
        // First, try to get from request attributes (set by AuthFilter)
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj instanceof Integer) {
            return (Integer) userIdObj;
        } else if (userIdObj instanceof Number) {
            return ((Number) userIdObj).intValue();
        } else if (userIdObj instanceof String) {
            try {
                return Integer.parseInt((String) userIdObj);
            } catch (NumberFormatException e) {
                // Invalid format, continue to fallback
            }
        }
        
        // Fallback: parse JWT token from cookie if attributes weren't set
        // This is needed because AuthFilter allows GET requests without setting attributes
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                Integer userId = JwtUtil.getUserIdFromToken(token);
                if (userId != null && userId > 0) {
                    // Set attributes for future use
                    request.setAttribute("userId", userId);
                    request.setAttribute("userEmail", claims.getStringClaim("email"));
                    request.setAttribute("userName", (claims.getStringClaim("firstName") + " " + claims.getStringClaim("lastName")).trim());
                    request.setAttribute("userRole", claims.getStringClaim("role"));
                    return userId;
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired, continue to check session
        }
        
        // Last fallback: try to get from session
        if (request.getSession(false) != null) {
            Object sessionUserId = request.getSession().getAttribute("userId");
            if (sessionUserId instanceof Integer) {
                return (Integer) sessionUserId;
            } else if (sessionUserId instanceof Number) {
                return ((Number) sessionUserId).intValue();
            }
        }
        
        // Default to 0 if not found
        return 0;
    }
    
    /**
     * Gets the current user ID from the request attributes, JWT token, or session with null handling
     * @param request The HTTP request
     * @return The user ID, or null if not found
     */
    public static Integer getCurrentUserIdOrNull(HttpServletRequest request) {
        // First, try to get from request attributes (set by AuthFilter)
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj instanceof Integer) {
            return (Integer) userIdObj;
        } else if (userIdObj instanceof Number) {
            return ((Number) userIdObj).intValue();
        } else if (userIdObj instanceof String) {
            try {
                return Integer.parseInt((String) userIdObj);
            } catch (NumberFormatException e) {
                // Invalid format, continue to fallback
            }
        }
        
        // Fallback: parse JWT token from cookie if attributes weren't set
        // This is needed because AuthFilter allows GET requests without setting attributes
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                Integer userId = JwtUtil.getUserIdFromToken(token);
                if (userId != null && userId > 0) {
                    // Set attributes for future use
                    request.setAttribute("userId", userId);
                    request.setAttribute("userEmail", claims.getStringClaim("email"));
                    request.setAttribute("userName", (claims.getStringClaim("firstName") + " " + claims.getStringClaim("lastName")).trim());
                    request.setAttribute("userRole", claims.getStringClaim("role"));
                    return userId;
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired, continue to check session
        }
        
        // Last fallback: try to get from session
        if (request.getSession(false) != null) {
            Object sessionUserId = request.getSession().getAttribute("userId");
            if (sessionUserId instanceof Integer) {
                return (Integer) sessionUserId;
            } else if (sessionUserId instanceof Number) {
                return ((Number) sessionUserId).intValue();
            }
        }
        
        return null;
    }

    /**
     * Checks if the current user is an admin or super admin
     * @param request The HTTP request
     * @return true if the user is an admin or super admin, false otherwise
     */
    public static boolean isCurrentUserAdmin(HttpServletRequest request) {
        // Check for admin role in request attributes (set by auth filter)
        Object roleObj = request.getAttribute("userRole");
        //system.out.println("[UserContextUtil] userRole attribute: " + roleObj);
        
        if (roleObj instanceof String) {
            String role = normalizeRole((String) roleObj);
            //system.out.println("[UserContextUtil] Normalized role: '" + role + "'");
            if (isAdminRole(role)) {
                //system.out.println("[UserContextUtil] Role IS admin, returning true");
                return true;
            }
        } else if (roleObj instanceof Number) {
            int roleNum = ((Number) roleObj).intValue();
            //system.out.println("[UserContextUtil] Numeric role detected: " + roleNum);
            if (isAdminRole(roleNum)) {
                //system.out.println("[UserContextUtil] Numeric role IS admin, returning true");
                return true;
            }
        }
        
        // Check for isAdmin flag in request attributes
        Object isAdminObj = request.getAttribute("isAdmin");
        if (isAdminObj instanceof Boolean) {
            //system.out.println("[UserContextUtil] isAdmin attribute: " + isAdminObj);
            return (Boolean) isAdminObj;
        }
        
        // Check session for admin status
        if (request.getSession(false) != null) {
            Object sessionRole = request.getSession().getAttribute("userRole");
            if (sessionRole instanceof String) {
                String role = normalizeRole((String) sessionRole);
                if (isAdminRole(role)) {
                    //system.out.println("[UserContextUtil] Session role IS admin, returning true");
                    return true;
                }
            } else if (sessionRole instanceof Number) {
                int roleNum = ((Number) sessionRole).intValue();
                if (isAdminRole(roleNum)) {
                    //system.out.println("[UserContextUtil] Session numeric role IS admin, returning true");
                    return true;
                }
            }
            
            Object sessionIsAdmin = request.getSession().getAttribute("isAdmin");
            if (sessionIsAdmin instanceof Boolean) {
                return (Boolean) sessionIsAdmin;
            }
        }
        
        // Fallback: parse JWT token from cookie if attributes weren't set
        // This is needed because AuthFilter may not run for all endpoints
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                String role = claims.getStringClaim("role");
                if (role != null) {
                    String normalizedRole = normalizeRole(role);
                    //system.out.println("[UserContextUtil] JWT token role: '" + role + "', normalized: '" + normalizedRole + "'");
                    if (isAdminRole(normalizedRole)) {
                        // Set attributes for future use
                        request.setAttribute("userRole", role);
                        request.setAttribute("userId", JwtUtil.getUserIdFromToken(token));
                        //system.out.println("[UserContextUtil] JWT token role IS admin, returning true");
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired, continue
            //system.out.println("[UserContextUtil] Failed to parse JWT token: " + e.getMessage());
        }
        
        //system.out.println("[UserContextUtil] No admin role found, returning false");
        return false;
    }

    /**
     * Checks if the current user is a Super Admin (only Super Admin, not Admin).
     * Used for operations that only Super Admin can perform (e.g. final delete in bulk delete).
     *
     * @param request The HTTP request
     * @return true if the user is a Super Admin, false otherwise
     */
    public static boolean isCurrentUserSuperAdmin(HttpServletRequest request) {
        int userId = getCurrentUserId(request);
        if (userId <= 0) {
            return false;
        }
        try {
            return SegmentAccessService.isSuperAdmin(userId);
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Normalize role string for comparison
     */
    private static String normalizeRole(String role) {
        if (role == null) return "";
        return role.trim().toLowerCase().replace('_', ' ').replace('-', ' ');
    }
    
    /**
     * Check if normalized role is admin or super admin
     */
    private static boolean isAdminRole(String normalizedRole) {
        return AppRoleNames.isAdminOrSuperAdminName(normalizedRole);
    }

    /**
     * Check if numeric role corresponds to admin/super-admin.
     * Assumption: 2 = super admin, 1 = admin.
     */
    private static boolean isAdminRole(int roleNum) {
        return roleNum >= 1; // treat >=1 as admin, 2 as super admin
    }
    
    /**
     * Get cookie value from request
     */
    private static String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
