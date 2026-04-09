package com.example.budg_v2;

import com.example.budg_v2.dao.UserPreferencesDAO;
import com.example.budg_v2.dao.UserPreferencesDAO.UserPreference;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.JsonObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Servlet for managing user preferences (language, locale, etc.)
 * Endpoints:
 *   GET  /api/user-preferences/locale     - Get current user's locale
 *   PUT  /api/user-preferences/locale     - Update current user's locale
 *   GET  /api/user-preferences/me         - Get all preferences for current user
 */
@WebServlet("/api/user-preferences/*")
public class UserPreferencesServlet extends HttpServlet {
    
    private static final Logger logger = Logger.getLogger(UserPreferencesServlet.class.getName());
    private UserPreferencesDAO userPreferencesDAO;
    
    @Override
    public void init() {
        this.userPreferencesDAO = new UserPreferencesDAO();
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            
            // Check if user is authenticated
            if (userId <= 0) {
                logger.log(Level.WARNING, "Unauthorized access attempt to user preferences");
                sendErrorResponse(response, "Unauthorized", 401);
                return;
            }
            
            String pathInfo = request.getPathInfo();
            
            if ("/locale".equals(pathInfo)) {
                getLocale(response, userId);
            } else if ("/me".equals(pathInfo)) {
                getAllPreferences(response, userId);
            } else {
                sendErrorResponse(response, "Invalid endpoint", 404);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in UserPreferencesServlet.doGet", e);
            sendErrorResponse(response, "Internal server error", 500);
        }
    }
    
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            
            // Check if user is authenticated
            if (userId <= 0) {
                logger.log(Level.WARNING, "Unauthorized access attempt to update user preferences");
                sendErrorResponse(response, "Unauthorized", 401);
                return;
            }
            
            String pathInfo = request.getPathInfo();
            
            if ("/locale".equals(pathInfo)) {
                updateLocale(request, response, userId);
            } else {
                sendErrorResponse(response, "Invalid endpoint", 404);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in UserPreferencesServlet.doPut", e);
            sendErrorResponse(response, "Internal server error", 500);
        }
    }
    
    /**
     * Get user's current locale
     */
    private void getLocale(HttpServletResponse response, int userId) throws IOException {
        try {
            String locale = userPreferencesDAO.getUserLocale(userId);
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("locale", locale);
            responseJson.addProperty("userId", userId);
            
            logger.log(Level.INFO, "Retrieved locale for user " + userId + ": " + locale);
            response.getWriter().write(responseJson.toString());
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error retrieving locale for user " + userId, e);
            sendErrorResponse(response, "Database error", 500);
        }
    }
    
    /**
     * Get all preferences for current user
     */
    private void getAllPreferences(HttpServletResponse response, int userId) throws IOException {
        try {
            UserPreference prefs = userPreferencesDAO.getUserPreferences(userId);
            
            if (prefs == null) {
                sendErrorResponse(response, "User not found", 404);
                return;
            }
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("userId", prefs.getUserId());
            responseJson.addProperty("email", prefs.getEmail());
            responseJson.addProperty("firstName", prefs.getFirstName());
            responseJson.addProperty("lastName", prefs.getLastName());
            responseJson.addProperty("locale", prefs.getLocale());
            
            logger.log(Level.INFO, "Retrieved all preferences for user " + userId);
            response.getWriter().write(responseJson.toString());
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error retrieving preferences for user " + userId, e);
            sendErrorResponse(response, "Database error", 500);
        }
    }
    
    /**
     * Update user's locale
     */
    private void updateLocale(HttpServletRequest request, HttpServletResponse response, int userId) throws IOException {
        try {
            // Read request body
            String body = getRequestBody(request);
            JsonObject requestJson = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            
            String newLocale = requestJson.has("locale") ? requestJson.get("locale").getAsString() : null;
            
            if (newLocale == null || newLocale.trim().isEmpty()) {
                sendErrorResponse(response, "Locale is required", 400);
                return;
            }
            
            // Update database
            boolean success = userPreferencesDAO.updateUserLocale(userId, newLocale);
            
            if (!success) {
                sendErrorResponse(response, "Failed to update locale", 500);
                return;
            }
            
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("message", "Locale updated successfully");
            responseJson.addProperty("userId", userId);
            responseJson.addProperty("locale", newLocale);
            
            logger.log(Level.INFO, "Locale updated for user " + userId + " to: " + newLocale);
            response.getWriter().write(responseJson.toString());
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error updating locale for user " + userId, e);
            sendErrorResponse(response, "Database error", 500);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing request body", e);
            sendErrorResponse(response, "Invalid request format", 400);
        }
    }
    
    /**
     * Helper method to read request body
     */
    private String getRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Scanner scanner = new Scanner(request.getInputStream(), "UTF-8")) {
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine());
            }
        }
        return sb.toString();
    }
    
    /**
     * Helper method to send error response
     */
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        JsonObject errorJson = new JsonObject();
        errorJson.addProperty("success", false);
        errorJson.addProperty("message", message);
        response.getWriter().write(errorJson.toString());
    }
}
