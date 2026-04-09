package com.example.budg_v2.admin;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.dao.AdminActivityLogDAO;
import com.example.budg_v2.model.ActivityLog;
import com.example.budg_v2.model.ActivityLogDetail;
import com.example.budg_v2.model.ActivityLogFilter;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet for Admin Activity Logs API
 * Endpoints:
 * GET /admin/api/activity-logs - List logs with filters
 * GET /admin/api/activity-logs/{id}/details - Get details for a log
 * GET /admin/api/activity-logs/export - Export to CSV
 * GET /admin/api/activity-logs/filters - Get filter options
 */
@WebServlet(name = "AdminActivityLogsServlet", urlPatterns = {
    "/admin/api/activity-logs",
    "/admin/api/activity-logs/*"
})
public class AdminActivityLogsServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminActivityLogsServlet.class);
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(java.time.LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    private final AdminActivityLogDAO dao = new AdminActivityLogDAO();
    
    // LocalDateTime adapter for Gson
    private static class LocalDateTimeAdapter extends TypeAdapter<java.time.LocalDateTime> {
        private static final java.time.format.DateTimeFormatter formatter = 
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public void write(JsonWriter out, java.time.LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public java.time.LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String dateTimeString = in.nextString();
            return java.time.LocalDateTime.parse(dateTimeString, formatter);
        }
    }
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        
        String pathInfo = request.getPathInfo();
        String requestURI = request.getRequestURI();
        
        try {
            // Handle different endpoints
            if (pathInfo != null && pathInfo.matches("/\\d+/details")) {
                // GET /admin/api/activity-logs/{id}/details
                handleGetDetails(request, response, pathInfo);
            } else if (requestURI.endsWith("/export")) {
                // GET /admin/api/activity-logs/export
                handleExport(request, response);
            } else if (requestURI.endsWith("/filters")) {
                // GET /admin/api/activity-logs/filters
                handleGetFilters(request, response);
            } else {
                // GET /admin/api/activity-logs (list with filters)
                handleGetLogs(request, response);
            }
        } catch (SQLException e) {
            logger.error("Database error in AdminActivityLogsServlet", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Unexpected error in AdminActivityLogsServlet", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Unexpected error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Handle GET /admin/api/activity-logs - List logs with filters
     */
    private void handleGetLogs(HttpServletRequest request, HttpServletResponse response) 
            throws SQLException, IOException {
        ActivityLogFilter filter = buildFilterFromRequest(request);
        
        List<ActivityLog> logs = dao.getActivityLogs(filter);
        int totalCount = dao.getActivityLogsCount(filter);
        
        Map<String, Object> result = new HashMap<>();
        result.put("logs", logs);
        result.put("totalCount", totalCount);
        result.put("offset", filter.getOffset());
        result.put("limit", filter.getLimit());
        
        response.getWriter().write(gson.toJson(result));
    }
    
    /**
     * Handle GET /admin/api/activity-logs/{id}/details
     */
    private void handleGetDetails(HttpServletRequest request, HttpServletResponse response, 
                                  String pathInfo) throws SQLException, IOException {
        // Extract ID from pathInfo (e.g., "/123/details" -> "123")
        String idStr = pathInfo.replace("/", "").replace("details", "");
        try {
            Long logId = Long.parseLong(idStr);
            List<ActivityLogDetail> details = dao.getActivityLogDetails(logId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("details", details);
            result.put("count", details.size());
            
            response.getWriter().write(gson.toJson(result));
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid log ID");
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    /**
     * Handle GET /admin/api/activity-logs/export - Export to CSV
     */
    private void handleExport(HttpServletRequest request, HttpServletResponse response) 
            throws SQLException, IOException {
        // Log the download action
        ActivityLogHelper.logSimpleActivity(request, 
            ActivityLogConstants.SETTING_DOWNLOAD_LOGS,
            ActivityLogConstants.COMPONENT_LOGS,
            ActivityLogConstants.CHANGE_TYPE_OTHER_ACTIONS);
        
        ActivityLogFilter filter = buildFilterFromRequest(request);
        List<ActivityLog> logs = dao.getActivityLogs(filter);
        
        // Set CSV headers
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"admin_activity_logs.csv\"");
        response.setCharacterEncoding("UTF-8");
        
        // Write CSV header
        response.getWriter().write("ID,Setting,Component,User Name,User Email,Change Type,Timestamp\n");
        
        // Write CSV rows
        for (ActivityLog log : logs) {
            StringBuilder row = new StringBuilder();
            row.append(log.getId()).append(",");
            row.append(escapeCsv(log.getSetting())).append(",");
            row.append(escapeCsv(log.getComponent())).append(",");
            row.append(escapeCsv(log.getUserName())).append(",");
            row.append(escapeCsv(log.getUserEmail())).append(",");
            row.append(escapeCsv(log.getChangeType())).append(",");
            if (log.getTimestamp() != null) {
                row.append(escapeCsv(log.getTimestamp().toString()));
            }
            row.append("\n");
            response.getWriter().write(row.toString());
        }
    }
    
    /**
     * Handle GET /admin/api/activity-logs/filters - Get filter options
     */
    private void handleGetFilters(HttpServletRequest request, HttpServletResponse response) 
            throws SQLException, IOException {
        Map<String, Object> filters = new HashMap<>();
        filters.put("settings", dao.getDistinctSettings());
        filters.put("components", dao.getDistinctComponents());
        filters.put("changeTypes", dao.getDistinctChangeTypes());
        filters.put("users", dao.getDistinctUsers());
        
        response.getWriter().write(gson.toJson(filters));
    }
    
    /**
     * Build filter from request parameters
     */
    private ActivityLogFilter buildFilterFromRequest(HttpServletRequest request) {
        ActivityLogFilter filter = new ActivityLogFilter();
        
        String setting = request.getParameter("setting");
        if (setting != null && !setting.isEmpty()) {
            filter.setSetting(setting);
        }
        
        String component = request.getParameter("component");
        if (component != null && !component.isEmpty()) {
            filter.setComponent(component);
        }
        
        String changeType = request.getParameter("changeType");
        if (changeType != null && !changeType.isEmpty()) {
            filter.setChangeType(changeType);
        }
        
        String userId = request.getParameter("userId");
        if (userId != null && !userId.isEmpty()) {
            try {
                filter.setUserId(Integer.parseInt(userId));
            } catch (NumberFormatException e) {
                logger.warn("Invalid userId parameter: {}", userId);
            }
        }
        
        String userName = request.getParameter("userName");
        if (userName != null && !userName.isEmpty()) {
            filter.setUserName(userName);
        }
        
        String userEmail = request.getParameter("userEmail");
        if (userEmail != null && !userEmail.isEmpty()) {
            filter.setUserEmail(userEmail);
        }
        
        String fromDate = request.getParameter("fromDate");
        if (fromDate != null && !fromDate.isEmpty()) {
            try {
                filter.setFromDate(LocalDateTime.parse(fromDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } catch (DateTimeParseException e) {
                logger.warn("Invalid fromDate parameter: {}", fromDate);
            }
        }
        
        String toDate = request.getParameter("toDate");
        if (toDate != null && !toDate.isEmpty()) {
            try {
                filter.setToDate(LocalDateTime.parse(toDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } catch (DateTimeParseException e) {
                logger.warn("Invalid toDate parameter: {}", toDate);
            }
        }
        
        String searchDetails = request.getParameter("searchDetails");
        if (searchDetails != null && !searchDetails.isEmpty()) {
            filter.setSearchDetails(searchDetails);
        }
        
        String offset = request.getParameter("offset");
        if (offset != null && !offset.isEmpty()) {
            try {
                filter.setOffset(Integer.parseInt(offset));
            } catch (NumberFormatException e) {
                logger.warn("Invalid offset parameter: {}", offset);
            }
        }
        
        String limit = request.getParameter("limit");
        if (limit != null && !limit.isEmpty()) {
            try {
                filter.setLimit(Integer.parseInt(limit));
            } catch (NumberFormatException e) {
                logger.warn("Invalid limit parameter: {}", limit);
            }
        }
        
        return filter;
    }
    
    /**
     * Escape CSV field
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

