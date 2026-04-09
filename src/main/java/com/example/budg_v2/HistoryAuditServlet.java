package com.example.budg_v2;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Servlet for handling history/audit data requests
 */
@WebServlet("/api/history")
public class HistoryAuditServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(HistoryAuditServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Add CORS headers
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            // Get parameters
            String moduleId = request.getParameter("module_id");
            String objectId = request.getParameter("object_id");
            String fromDate = request.getParameter("from_date");
            String toDate = request.getParameter("to_date");
            String page = request.getParameter("page");
            String limit = request.getParameter("limit");


            // Enhanced validation
            if (moduleId == null || moduleId.trim().isEmpty() ||
                objectId == null || objectId.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"module_id and object_id are required and cannot be empty\"}");
                return;
            }

            // Validate module_id and object_id are numeric
            try {
                Integer.parseInt(moduleId);
                Integer.parseInt(objectId);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"module_id and object_id must be valid numbers\"}");
                return;
            }

            // Date filtering is now handled in frontend only

            // Parse pagination parameters
            int pageNum = (page != null && !page.trim().isEmpty()) ? Integer.parseInt(page) : 1;
            int limitNum = (limit != null && !limit.trim().isEmpty()) ? Integer.parseInt(limit) : 100;

            // Validate pagination parameters
            if (pageNum < 1) pageNum = 1;
            if (limitNum < 1 || limitNum > 1000) limitNum = 100;


            // Get history data with pagination
            HistoryResult historyResult = getHistoryData(moduleId, objectId, fromDate, toDate, pageNum, limitNum);

            // Create response
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("success", true);
            responseJson.addProperty("count", historyResult.getTotalCount());
            responseJson.addProperty("page", pageNum);
            responseJson.addProperty("limit", limitNum);
            responseJson.addProperty("totalPages", historyResult.getTotalPages());

            // Ensure data is properly serialized as array
            if (historyResult.getData() != null && !historyResult.getData().isEmpty()) {
                responseJson.add("data", new Gson().toJsonTree(historyResult.getData()));
            } else {
                responseJson.add("data", new Gson().toJsonTree(new ArrayList<>()));
            }

            response.getWriter().write(new Gson().toJson(responseJson));

        } catch (Exception e) {
            logger.severe("Error in HistoryAuditServlet: " + e.getMessage());
            e.printStackTrace();

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("error", "Internal server error");
            response.getWriter().write(new Gson().toJson(errorResponse));
        }
    }

    // Date validation removed - filtering is now handled in frontend only

    // Helper method to format date for display (DD-MMM-YYYY HH:mm:ss)
    private String formatDateForDisplay(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return "";
        }

        try {
            // If already in DD-MMM-YYYY HH:mm:ss format, return as is
            if (dateString.matches("\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2}:\\d{2}")) {
                return dateString;
            }

            // Handle MySQL DATETIME format: YYYY-MM-DD HH:mm:ss
            java.time.LocalDateTime dateTime;
            if (dateString.contains(" ")) {
                // Has time component
                dateTime = java.time.LocalDateTime.parse(dateString, 
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                // Date only
                java.time.LocalDate date = java.time.LocalDate.parse(dateString);
                dateTime = date.atStartOfDay();
            }
            
            return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", java.util.Locale.ENGLISH));
        } catch (Exception e) {
            logger.warning("Failed to format date: " + dateString + " - " + e.getMessage());
            // If parsing fails, return original string
            return dateString;
        }
    }

    private HistoryResult getHistoryData(String moduleId, String objectId, String fromDate, String toDate, int page, int limit)
            throws SQLException {

        List<HistoryRecord> historyList = new ArrayList<>();
        int totalCount = 0;

        // Get module name and build audit table name
        String moduleName = getModuleName(Integer.parseInt(moduleId));
        String auditTable = getAuditTableName(moduleName);
        
        // Check if this is Dataset module - if so, include attribute audit history
        boolean isDataset = moduleName.equalsIgnoreCase("Dataset") || moduleName.equalsIgnoreCase("Data Sets");
        
        // Check if this is Attribute module
        boolean isAttribute = moduleName.equalsIgnoreCase("Attribute") || moduleName.equalsIgnoreCase("Attributes");
        
        if (isDataset) {
            // For Dataset: combine dataset_audit_history and attribute_audit_history
            totalCount = getDatasetAndAttributesCount(objectId);
            historyList = getDatasetAndAttributesHistory(objectId, page, limit);
        } else if (isAttribute) {
            // For Attribute: use attribute_audit_history directly
            totalCount = getAttributeCount(objectId);
            historyList = getAttributeHistory(objectId, page, limit);
        } else {
            // For other modules: use standard single-table query
            // First, get total count for pagination
            StringBuilder countSql = new StringBuilder();
            countSql.append("SELECT COUNT(*) FROM `").append(auditTable).append("` ");
            countSql.append("WHERE `id` = ? ");

            // Get total count
            try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 PreparedStatement countStmt = conn.prepareStatement(countSql.toString())) {

                int paramIndex = 1;
                countStmt.setInt(paramIndex++, Integer.parseInt(objectId));

                try (ResultSet countRs = countStmt.executeQuery()) {
                    if (countRs.next()) {
                        totalCount = countRs.getInt(1);
                    }
                }
            }

            // Build main SQL query with pagination
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ");
            sql.append("`id`, `auditidpk`, `object`, `event`, `updateType`, ");
            sql.append("`field`, `from`, `to`, `author`, `date`, `lastChange` ");
            sql.append("FROM `").append(auditTable).append("` ");
            sql.append("WHERE `id` = ? ");
            sql.append("ORDER BY `date` DESC, `auditidpk` DESC ");
            sql.append("LIMIT ? OFFSET ?");

            try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

                int paramIndex = 1;
                stmt.setInt(paramIndex++, Integer.parseInt(objectId));

                // Add pagination parameters
                stmt.setInt(paramIndex++, limit);
                stmt.setInt(paramIndex++, (page - 1) * limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        HistoryRecord record = new HistoryRecord();
                        record.setId(rs.getInt("id"));
                        record.setAuditIdPk(rs.getInt("auditidpk"));
                        record.setObject(rs.getString("object"));
                        record.setEvent(rs.getString("event"));
                        record.setUpdateType(rs.getString("updateType"));
                        record.setField(rs.getString("field"));
                        record.setFrom(rs.getString("from"));
                        record.setTo(rs.getString("to"));
                        record.setAuthor(rs.getString("author"));
                        
                        String dateStr = rs.getString("date");
                        String lastChangeStr = rs.getString("lastChange");

                        record.setDate(formatDateForDisplay(dateStr));
                        record.setLastChange(formatDateForDisplay(lastChangeStr));

                        historyList.add(record);
                    }
                }
            }
        }

        int totalPages = (int) Math.ceil((double) totalCount / limit);

        return new HistoryResult(historyList, totalCount, totalPages);
    }
    
    /**
     * Get count of attribute audit history records
     */
    private int getAttributeCount(String attributeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attribute_audit_history WHERE id = ?";
        
        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(attributeId));
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
    
    /**
     * Get attribute audit history with pagination
     */
    private List<HistoryRecord> getAttributeHistory(String attributeId, int page, int limit) throws SQLException {
        List<HistoryRecord> historyList = new ArrayList<>();
        
        String sql = """
            SELECT id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange
            FROM attribute_audit_history
            WHERE id = ?
            ORDER BY date DESC, auditidpk DESC
            LIMIT ? OFFSET ?
        """;
        
        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(attributeId));
            ps.setInt(2, limit);
            ps.setInt(3, (page - 1) * limit);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    HistoryRecord record = new HistoryRecord();
                    record.setId(rs.getInt("id"));
                    record.setAuditIdPk(rs.getInt("auditidpk"));
                    record.setObject(rs.getString("object"));
                    record.setEvent(rs.getString("event"));
                    record.setUpdateType(rs.getString("updateType"));
                    record.setField(rs.getString("field"));
                    record.setFrom(rs.getString("from"));
                    record.setTo(rs.getString("to"));
                    record.setAuthor(rs.getString("author"));
                    
                    String dateStr = rs.getString("date");
                    String lastChangeStr = rs.getString("lastChange");

                    record.setDate(formatDateForDisplay(dateStr));
                    record.setLastChange(formatDateForDisplay(lastChangeStr));

                    historyList.add(record);
                }
            }
        }
        
        return historyList;
    }
    
    /**
     * Get combined count of dataset and its attributes audit history
     */
    private int getDatasetAndAttributesCount(String datasetId) throws SQLException {
        String sql = """
            SELECT 
                (SELECT COUNT(*) FROM dataset_audit_history WHERE id = ?) +
                (SELECT COUNT(*) FROM attribute_audit_history 
                 WHERE id IN (SELECT ID FROM attribute WHERE Dataset_ID = ? AND DeletedDatetime IS NULL))
                AS total_count
        """;
        
        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(datasetId));
            ps.setInt(2, Integer.parseInt(datasetId));
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_count");
                }
            }
        }
        return 0;
    }
    
    /**
     * Get combined dataset and attributes audit history with pagination
     */
    private List<HistoryRecord> getDatasetAndAttributesHistory(String datasetId, int page, int limit) throws SQLException {
        List<HistoryRecord> historyList = new ArrayList<>();
        
        // Union query to combine dataset and attribute audit histories
        String sql = """
            SELECT id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange
            FROM (
                SELECT 
                    id, auditidpk, object, event, updateType, field, `from`, `to`, author, date, lastChange
                FROM dataset_audit_history
                WHERE id = ?
                
                UNION ALL
                
                SELECT 
                    aah.id, aah.auditidpk, aah.object, aah.event, aah.updateType, aah.field, 
                    aah.`from`, aah.`to`, aah.author, aah.date, aah.lastChange
                FROM attribute_audit_history aah
                JOIN attribute a ON a.ID = aah.id
                WHERE a.Dataset_ID = ? AND a.DeletedDatetime IS NULL
            ) combined
            ORDER BY date DESC, auditidpk DESC
            LIMIT ? OFFSET ?
        """;
        
        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(datasetId));
            ps.setInt(2, Integer.parseInt(datasetId));
            ps.setInt(3, limit);
            ps.setInt(4, (page - 1) * limit);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    HistoryRecord record = new HistoryRecord();
                    record.setId(rs.getInt("id"));
                    record.setAuditIdPk(rs.getInt("auditidpk"));
                    record.setObject(rs.getString("object"));
                    record.setEvent(rs.getString("event"));
                    record.setUpdateType(rs.getString("updateType"));
                    record.setField(rs.getString("field"));
                    record.setFrom(rs.getString("from"));
                    record.setTo(rs.getString("to"));
                    record.setAuthor(rs.getString("author"));
                    
                    String dateStr = rs.getString("date");
                    String lastChangeStr = rs.getString("lastChange");

                    record.setDate(formatDateForDisplay(dateStr));
                    record.setLastChange(formatDateForDisplay(lastChangeStr));

                    historyList.add(record);
                }
            }
        }
        
        return historyList;
    }


    /**
     * Get audit table name from module name
     */
    private String getAuditTableName(String moduleName) {
        // Handle special cases for module names that don't follow standard naming
        
        // System modules
        if (moduleName.equalsIgnoreCase("System")) {
            return "system_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Committee")) {
            return "committee_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Policy")) {
            return "policy_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Process")) {
            return "process_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Project")) {
            return "project_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Product")) {
            return "product_audit_history";
        }
        if (moduleName.equalsIgnoreCase("BusinessArea")) {
            return "business_area_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Capability")) {
            return "capability_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Client")) {
            return "client_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Dataset") || moduleName.equalsIgnoreCase("Data Sets")) {
            return "dataset_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Glossary")) {
            return "glossary_audit_history";
        }
        if (moduleName.equalsIgnoreCase("LegalEntity") || moduleName.equalsIgnoreCase("Legal Entity")) {
            return "legal_audit_history";
        }
        if (moduleName.equalsIgnoreCase("OrgUnit") || moduleName.equalsIgnoreCase("Org Unit")) {
            return "orgunit_audit_history";
        }
        if (moduleName.equalsIgnoreCase("People")) {
            return "people_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Stakeholder")) {
            return "stakeholder_audit_history";
        }
        if (moduleName.equalsIgnoreCase("SystemInterface")) {
            return "systeminterface_audit_history";
        }
        
        // Additional special cases
        if (moduleName.equalsIgnoreCase("Role")) {
            return "object_role_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Attribute") || moduleName.equalsIgnoreCase("Attributes")) {
            return "attribute_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Processes")) {
            return "process_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Projects")) {
            return "project_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Products")) {
            return "product_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Policies")) {
            return "policy_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Interface")) {
            return "interface_audit_history";
        }
        if (moduleName.equalsIgnoreCase("Regulatory Theme") || moduleName.equalsIgnoreCase("RegulatoryTheme")) {
            return "regulatory_theme_audit_history";
        }
        
        // Default: convert to lowercase, replace spaces with underscores, and add _audit_history
        return moduleName.toLowerCase().replaceAll("\\s+", "_") + "_audit_history";
    }

    /**
     * Get module name by ID
     */
    private String getModuleName(int moduleId) throws SQLException {
        // First try to get from module table
        String sql = "SELECT primaryname FROM module WHERE id = ?";
        try (Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        }
        
        // If not found in module table, check for specific entities
        // Map common module IDs to entity names
        switch (moduleId) {
            case 1: return "System";
            case 2: return "Committee";
            case 3: return "Policy";
            case 4: return "Process";
            case 5: return "Project";
            case 6: return "Product";
            case 7: return "BusinessArea";
            case 8: return "Capability";
            case 9: return "Client";
            case 10: return "Dataset";
            case 11: return "Glossary";
            case 12: return "LegalEntity";
            case 13: return "Org Unit";
            case 14: return "People";
            case 15: return "Stakeholder";
            case 16: return "SystemInterface";
            default: return "System"; // Default fallback
        }
    }


    /**
     * History result wrapper class for pagination
     */
    public static class HistoryResult {
        private List<HistoryRecord> data;
        private int totalCount;
        private int totalPages;

        public HistoryResult(List<HistoryRecord> data, int totalCount, int totalPages) {
            this.data = data;
            this.totalCount = totalCount;
            this.totalPages = totalPages;
        }

        public List<HistoryRecord> getData() { return data; }
        public int getTotalCount() { return totalCount; }
        public int getTotalPages() { return totalPages; }
    }

    /**
     * History record data class
     */
    public static class HistoryRecord {
        private int id;
        private int moduleId;
        private int auditIdPk;
        private String object;
        private String event;
        private String updateType;
        private String field;
        private String from;
        private String to;
        private String author;
        private String date;
        private String lastChange;

        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public int getModuleId() { return moduleId; }
        public void setModuleId(int moduleId) { this.moduleId = moduleId; }

        public int getAuditIdPk() { return auditIdPk; }
        public void setAuditIdPk(int auditIdPk) { this.auditIdPk = auditIdPk; }

        public String getObject() { return object; }
        public void setObject(String object) { this.object = object; }

        public String getEvent() { return event; }
        public void setEvent(String event) { this.event = event; }

        public String getUpdateType() { return updateType; }
        public void setUpdateType(String updateType) { this.updateType = updateType; }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }

        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public String getLastChange() { return lastChange; }
        public void setLastChange(String lastChange) { this.lastChange = lastChange; }
    }
}
