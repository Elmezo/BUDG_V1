package com.example.budg_v2.util;

import com.example.budg_v2.service.ActivityLogContext;
import com.example.budg_v2.service.AdminActivityLogService;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Helper utility for activity logging
 * Provides convenience methods to get user info and log activities
 */
public class ActivityLogHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(ActivityLogHelper.class);
    private static final AdminActivityLogService logService = new AdminActivityLogService();
    
    /**
     * Get user information from request
     * Tries request attributes first, then JWT token, then session
     */
    public static UserInfo getUserInfo(HttpServletRequest request) {
        UserInfo userInfo = new UserInfo();
        
        // Try request attributes first (set by AuthFilter)
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            userInfo.userId = userIdAttr instanceof Integer ? (Integer) userIdAttr : 
                             userIdAttr instanceof Number ? ((Number) userIdAttr).intValue() : null;
            userInfo.userName = (String) request.getAttribute("userName");
            userInfo.userEmail = (String) request.getAttribute("userEmail");
            
            if (userInfo.userId != null && userInfo.userName != null && userInfo.userEmail != null) {
                return userInfo;
            }
        }
        
        // Fallback: parse JWT token
        try {
            String token = getCookie(request, "ACCESS_TOKEN");
            if (token != null) {
                JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
                userInfo.userId = JwtUtil.getUserIdFromToken(token);
                userInfo.userName = (claims.getStringClaim("firstName") + " " + 
                                   claims.getStringClaim("lastName")).trim();
                userInfo.userEmail = claims.getStringClaim("email");
                
                if (userInfo.userId != null && userInfo.userName != null && userInfo.userEmail != null) {
                    return userInfo;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to parse token from cookie: {}", e.getMessage());
        }
        
        return userInfo;
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
    
    /**
     * True if userId is valid for activity logging (must exist in people table; guest/anonymous = -1/0 are not).
     */
    private static boolean isValidUserIdForLogging(Integer userId) {
        return userId != null && userId > 0;
    }

    /**
     * Log a simple activity (no details)
     */
    public static void logSimpleActivity(HttpServletRequest request, String setting, 
                                        String component, String changeType) {
        try {
            UserInfo userInfo = getUserInfo(request);
            if (isValidUserIdForLogging(userInfo.userId)) {
                logService.logSimpleActivity(setting, component, userInfo.userId, 
                                           userInfo.userName, userInfo.userEmail, changeType);
            }
        } catch (Exception e) {
            logger.error("Failed to log simple activity", e);
        }
    }
    
    /**
     * Log an activity with old and new states
     */
    public static void logActivity(HttpServletRequest request, String setting, String component,
                                  String changeType, Map<String, Object> oldState, 
                                  Map<String, Object> newState) {
        logActivity(request, setting, component, changeType, oldState, newState, null);
    }
    
    /**
     * Log an activity with old and new states and additional context map
     */
    public static void logActivity(HttpServletRequest request, String setting, String component,
                                  String changeType, Map<String, Object> oldState, 
                                  Map<String, Object> newState, Map<String, Object> contextMap) {
        try {
            UserInfo userInfo = getUserInfo(request);
            if (isValidUserIdForLogging(userInfo.userId)) {
                ActivityLogContext context = new ActivityLogContext();
                context.setSetting(setting);
                context.setComponent(component);
                context.setUserId(userInfo.userId);
                context.setUserName(userInfo.userName);
                context.setUserEmail(userInfo.userEmail);
                context.setChangeType(changeType);
                context.setOldState(oldState);
                context.setNewState(newState);
                context.setContextMap(contextMap);
                
                logService.logActivity(context);
            }
        } catch (Exception e) {
            logger.error("Failed to log activity", e);
        }
    }
    
    /**
     * User info holder class
     */
    public static class UserInfo {
        public Integer userId;
        public String userName;
        public String userEmail;
    }
}

