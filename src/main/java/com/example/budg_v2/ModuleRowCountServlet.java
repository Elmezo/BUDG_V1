package com.example.budg_v2;

import com.example.budg_v2.dao.WorkflowTaskDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentAccessService;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;

@WebServlet(name = "ModuleRowCountServlet", urlPatterns = { "/api/module-row-count" })
public class ModuleRowCountServlet extends HttpServlet {

    // تحويل اسم الموديول لاسم جدول صحيح
    private String formatTableName(String primaryName) {
        if (primaryName.equalsIgnoreCase("Role")) {
            return "object_x_people"; // Use object_x_people for Role (same table used in unison search)
        }
        if (primaryName.equalsIgnoreCase("Data Sets")) {
            return "dataset";
        }
        if (primaryName.equalsIgnoreCase("Attributes")) {
            return "attribute";
        }
        if (primaryName.equalsIgnoreCase("Processes")) {
            return "process";
        }
        if (primaryName.equalsIgnoreCase("Projects")) {
            return "project";
        }
        if (primaryName.equalsIgnoreCase("Products")) {
            return "product";
        }
        if (primaryName.equalsIgnoreCase("Policies")) {
            return "policy";
        }
        if (primaryName.equalsIgnoreCase("Legal Entity")) {
            return "legal";
        }
        if (primaryName.equalsIgnoreCase("Regulatory Theme") || primaryName.equalsIgnoreCase("Regulatory Themes")) {
            return "regulatorytheme";
        }
        if (primaryName.equalsIgnoreCase("Change Requests") || primaryName.equalsIgnoreCase("Change Request")) {
            return "changerequest";
        }
        // Active Tasks is not a database table - it's fetched via API endpoint
        if (primaryName.equalsIgnoreCase("Active Tasks") || primaryName.equalsIgnoreCase("ActiveTasks")) {
            return null; // Special marker to skip table counting
        }

        if (primaryName == null || primaryName.isBlank())
            return "";
        String formatted = primaryName.trim().toLowerCase();
        formatted = formatted.replaceAll("\\s+", "_");
        formatted = formatted.replaceAll("[^a-z0-9_]", "");
        if (formatted.equalsIgnoreCase("Role")) {
            return "role_type";
        }
        return formatted;
    }
    // حساب عدد الصفوف لأي جدول

    private int getTableRowCount(String tableName) {
        String query = "SELECT COUNT(*) FROM `" + tableName + "`" + wherestatment(tableName);

        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next())
                return rs.getInt(1);
        } catch (SQLException e) {
            // Check if error is due to table not existing (error code 1146)
            if (e.getErrorCode() != 1146) {
                // Only log errors that are NOT "table doesn't exist"
                System.err.println("Error counting rows for table " + tableName + ": " + e.getMessage());
            }
            // For missing tables, silently return 0
        }
        return 0;
    }

    private String wherestatment(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return "";
        }

        String table = tableName.trim().toLowerCase(Locale.ROOT);
        String deletedCol = switch (table) {
            case "dataset", "data set", "data-set", "data_set" -> "DeletedDatetime";
            case "attribute" -> "DeletedDatetime";
            case "system" -> "Deleted_datetime";
            case "glossary" -> "Deleted_datetime";
            case "people" -> "Deleted_date";
            case "interface" -> "deleted_datetime";
            case "orgunit", "org-unit", "org_unit" -> "deleted_Date";
            case "process" -> "deleteddatetime";
            case "project" -> "deletedatetime";
            case "product" -> "deleteddatetime";
            case "policy" -> "DeletedDatetime";
            case "legal-entity", "legalentity", "legal" -> "DeleteDatetime";
            case "business-area", "business_area" -> "deletedatetime";
            case "capability" -> "DeletedDatetime";
            case "client" -> null; // لا يوجد عمود حذف معروف هنا في السكيم الحالية
            case "committee" -> "DeleteDatetime";
            case "geography" -> "DeletedDatetime";
            case "regulation" -> "DeletedDatetime";
            case "regulator" -> "DeletedDatetime";
            case "regulatory-theme", "regulatorytheme" -> "DeletedDatetime";
            default -> null;
        };

        if (deletedCol == null) {
            return ""; // لا شرط حذف معروف لهذا الجدول
        }
        return " WHERE " + deletedCol + " IS NULL";
    }

    /**
     * Get count of roles from object_x_people table
     * Filters out deleted people records (same logic as unison search)
     */
    private int getRoleRowCount() {
        // Use the same query as RoleDashboardServlet and unison search
        // object_x_people doesn't have a soft delete column, so we filter by people.Deleted_date
        String query = "SELECT COUNT(*) FROM object_x_people oxp " +
                      "LEFT JOIN people p ON oxp.ipid = p.ID " +
                      "WHERE (p.Deleted_date IS NULL OR p.ID IS NULL)";
        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next())
                return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Error counting role rows: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Get count of active tasks from workflow_instance_task table
     * Only counts tasks with status 'Pending' or 'InProgress'
     */
    private int getActiveTasksCount() {
        String query = "SELECT COUNT(*) FROM workflow_instance_task WHERE Status IN ('Pending', 'InProgress')";
        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next())
                return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Error counting active tasks: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Same resolution order as UnisonSearchService.moduleToObjectType / computeAccessibleTotalCount
     * so sidebar preload "Y" matches search denominators (segment + cube).
     */
    private String modulePrimaryNameToSegmentObjectType(String primaryName) {
        if (primaryName == null || primaryName.isBlank()) {
            return null;
        }
        String x = primaryName.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return switch (x) {
            case "data sets", "dataset", "datasets" -> "Dataset";
            case "attributes", "attribute" -> "Dataset";
            case "systems", "system" -> "System";
            case "glossaries", "glossary" -> "Glossary";
            case "processes", "process" -> "Process";
            case "projects", "project" -> "Project";
            case "products", "product" -> "Product";
            case "policies", "policy" -> "Policy";
            case "legal entity", "legal entities" -> "LegalEntity";
            case "business area", "business areas" -> "BusinessArea";
            case "capabilities", "capability" -> "Capability";
            case "clients", "client" -> "Client";
            case "committees", "committee" -> "Committee";
            case "org unit", "org units" -> "OrgUnit";
            case "geographies", "geography" -> "Geography";
            case "regulations", "regulation" -> "Regulation";
            case "regulators", "regulator" -> "Regulator";
            case "regulatory theme", "regulatory themes" -> "RegulatoryTheme";
            case "interfaces", "interface" -> "Interface";
            case "people", "person" -> "People";
            case "role", "roles" -> "People";
            case "data quality", "dataquality" -> "Dataset";
            case "change requests", "change request" -> "ChangeRequest";
            default -> null;
        };
    }

    private int getSegmentFilteredTotal(int userId, String objectType) throws SQLException {
        Set<Integer> accessible = SegmentAccessService.getAccessibleObjectIds(userId, objectType);
        if (accessible.isEmpty()) {
            return 0;
        }
        Set<Integer> selected = SegmentAccessService.filterBySelectedSegments(userId, objectType,
                new ArrayList<>(accessible));
        return selected.size();
    }

    private int getActiveTasksCountForUser(int userId) {
        try {
            WorkflowTaskDAO taskDAO = new WorkflowTaskDAO();
            List<Map<String, Object>> tasks = taskDAO.findActiveTasksForUser(userId);
            return tasks != null ? tasks.size() : 0;
        } catch (SQLException e) {
            System.err.println("[ModuleRowCountServlet] user active tasks count: " + e.getMessage());
            return getActiveTasksCount();
        }
    }

    private Integer getUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("userId");
        if (attr instanceof Number) {
            int uid = ((Number) attr).intValue();
            if (uid > 0) {
                return uid;
            }
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sid = session.getAttribute("userId");
            if (sid instanceof Number) {
                int uid = ((Number) sid).intValue();
                if (uid > 0) {
                    return uid;
                }
            }
        }
        try {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("ACCESS_TOKEN".equals(cookie.getName())) {
                        String token = cookie.getValue();
                        if (token != null) {
                            Integer uid = com.example.budg_v2.util.JwtUtil.getUserIdFromToken(token);
                            if (uid != null && uid > 0) {
                                return uid;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ModuleRowCountServlet] token parse: " + e.getMessage());
        }
        return null;
    }

    /**
     * Row count aligned with Unison facet totals when user is logged in; raw table count otherwise.
     */
    private int resolveRowCountForModule(String moduleName, String tableName, Integer userId) {
        final int uid = (userId != null && userId > 0) ? userId : 0;
        boolean loggedIn = uid > 0;

        if (moduleName.equalsIgnoreCase("Active Tasks") || moduleName.equalsIgnoreCase("ActiveTasks")) {
            return loggedIn ? getActiveTasksCountForUser(uid) : getActiveTasksCount();
        }

        if (tableName == null) {
            return 0;
        }

        if (loggedIn) {
            String objectType = modulePrimaryNameToSegmentObjectType(moduleName);
            if (objectType != null) {
                try {
                    return getSegmentFilteredTotal(uid, objectType);
                } catch (SQLException e) {
                    System.err.println("[ModuleRowCountServlet] segment count for " + moduleName + ": " + e.getMessage());
                }
            }
        }

        if (moduleName.equalsIgnoreCase("Role") || "object_x_people".equalsIgnoreCase(tableName)) {
            return getRoleRowCount();
        }
        return getTableRowCount(tableName);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");
        List<Map<String, Object>> rowCounts = new ArrayList<>();

        Integer userId = getUserId(req);

        String sql = "SELECT primaryname FROM module";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String moduleName = rs.getString("primaryname");
                String tableName = formatTableName(moduleName);

                // Skip modules that don't have a database table (like Active Tasks)
                if (tableName == null) {
                    // For Active Tasks, count from workflow_instance_task table
                    if (moduleName.equalsIgnoreCase("Active Tasks") || moduleName.equalsIgnoreCase("ActiveTasks")) {
                        int rowCount = resolveRowCountForModule(moduleName, null, userId);
                        Map<String, Object> row = new HashMap<>();
                        row.put("moduleName", moduleName);
                        row.put("tableName", "workflow_instance_task");
                        row.put("rowCount", rowCount);
                        rowCounts.add(row);
                    }
                    continue;
                }

                int rowCount = resolveRowCountForModule(moduleName, tableName, userId);

                Map<String, Object> row = new HashMap<>();
                row.put("moduleName", moduleName);
                row.put("tableName", tableName);
                row.put("rowCount", rowCount);
                rowCounts.add(row);
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            e.printStackTrace();
        }

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("rowCounts", rowCounts);

        PrintWriter out = resp.getWriter();
        out.print(new Gson().toJson(responseMap));
        out.flush();
    }
}
