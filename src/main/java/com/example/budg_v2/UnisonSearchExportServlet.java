package com.example.budg_v2;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.example.budg_v2.database.DatabaseConnection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Servlet to handle Unison Search data export for all facets (except People)
 * Supports PDF, Excel, and CSV formats with optional stakeholders column
 */
@WebServlet("/api/export/unison-search")
public class UnisonSearchExportServlet extends HttpServlet {
    @SuppressWarnings("unused")
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            // Parse request body
            StringBuilder requestBody = new StringBuilder();
            String line;
            try (java.io.BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    requestBody.append(line);
                }
            }

            JsonObject jsonRequest = JsonParser.parseString(requestBody.toString()).getAsJsonObject();

            String format = jsonRequest.has("format") ? jsonRequest.get("format").getAsString() : "excel";
            boolean includeStakeholders = jsonRequest.has("includeStakeholders")
                    ? jsonRequest.get("includeStakeholders").getAsBoolean()
                    : true;
            com.google.gson.JsonArray dataArray = jsonRequest.getAsJsonArray("data");
            com.google.gson.JsonArray columnsArray = jsonRequest.getAsJsonArray("columns");
            String category = jsonRequest.has("category") ? jsonRequest.get("category").getAsString() : "dataset";
            String userName = jsonRequest.has("userName") ? jsonRequest.get("userName").getAsString() : "Unknown User";
            com.google.gson.JsonArray objectIdsArray = jsonRequest.has("objectIds") ? jsonRequest.getAsJsonArray("objectIds") : null;

            // Convert JSON arrays to Java lists
            List<String> columns = new ArrayList<>();
            if (columnsArray != null) {
                for (int i = 0; i < columnsArray.size(); i++) {
                    columns.add(columnsArray.get(i).getAsString());
                }
            }

            // Get column labels if available
            Map<String, String> columnLabels = new HashMap<>();
            if (jsonRequest.has("columnLabels")) {
                JsonObject labelsObj = jsonRequest.getAsJsonObject("columnLabels");
                for (String key : labelsObj.keySet()) {
                    columnLabels.put(key, labelsObj.get(key).getAsString());
                }
            }

            // Convert data array to list of maps
            List<Map<String, String>> data = new ArrayList<>();
            if (dataArray != null) {
                for (int i = 0; i < dataArray.size(); i++) {
                    JsonObject rowObj = dataArray.get(i).getAsJsonObject();
                    Map<String, String> row = new HashMap<>();
                    for (String key : rowObj.keySet()) {
                        if (rowObj.get(key) != null && !rowObj.get(key).isJsonNull()) {
                            row.put(key, rowObj.get(key).getAsString());
                        } else {
                            row.put(key, "");
                        }
                    }
                    data.add(row);
                }
            }

            // Collect object IDs for fetching stakeholders
            List<Integer> objectIds = new ArrayList<>();
            if (objectIdsArray != null) {
                for (int i = 0; i < objectIdsArray.size(); i++) {
                    try {
                        objectIds.add(objectIdsArray.get(i).getAsInt());
                    } catch (Exception e) {
                        // Skip invalid IDs
                    }
                }
            }

            // Fetch stakeholders from backend if needed
            Map<Integer, String> stakeholdersMap = new HashMap<>();
            if (includeStakeholders && !objectIds.isEmpty()) {
                stakeholdersMap = fetchStakeholdersFromDatabase(category, objectIds);
            }

            // Add stakeholders data to rows
            if (includeStakeholders) {
                // Add stakeholders column if not already present
                if (!columns.contains("stakeholders") && !columns.contains("Stakeholders")) {
                    columns.add("stakeholders");
                    columnLabels.put("stakeholders", "Stakeholders");
                }

                // Add stakeholders data to each row based on object IDs
                // Match object IDs with data rows by index
                for (int i = 0; i < data.size(); i++) {
                    Map<String, String> row = data.get(i);
                    Integer objectId = null;

                    // Get object ID from the objectIds list (same index as data row)
                    if (i < objectIds.size()) {
                        objectId = objectIds.get(i);
                    }

                    // If no object ID from list, try to find it in row data
                    if (objectId == null) {
                        for (String key : row.keySet()) {
                            if (key.equalsIgnoreCase("id") || key.toLowerCase().endsWith("_id") ||
                                    key.toLowerCase().endsWith("id")) {
                                try {
                                    objectId = Integer.parseInt(row.get(key));
                                    break;
                                } catch (NumberFormatException e) {
                                    // Continue searching
                                }
                            }
                        }
                    }

                    // Add stakeholders data if found
                    if (objectId != null && stakeholdersMap.containsKey(objectId)) {
                        row.put("stakeholders", stakeholdersMap.get(objectId));
                    } else {
                        row.put("stakeholders", "");
                    }
                }
            }

            // Remove stakeholders column if needed
            if (!includeStakeholders) {
                columns.removeIf(col -> {
                    String colLower = col.toLowerCase();
                    return colLower.equals("stakeholders") || colLower.equals("stakeholder");
                });
            }

            // Get user name from request if not provided
            if (userName == null || userName.equals("Unknown User")) {
                userName = getCurrentUserName(request);
            }

            // Export based on format
            switch (format.toLowerCase()) {
                case "pdf":
                    exportToPDF(response, data, columns, columnLabels, category, userName);
                    break;
                case "csv":
                    exportToCSV(response, data, columns, columnLabels, category, userName);
                    break;
                case "excel":
                default:
                    exportToExcel(response, data, columns, columnLabels, category, userName);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().print("{\"error\": \"Export failed: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Get current user name from request
     */
    private String getCurrentUserName(HttpServletRequest request) {
        try {
            // Try to get from request attribute
            Object userNameAttr = request.getAttribute("userName");
            if (userNameAttr != null) {
                return userNameAttr.toString();
            }

            // Try to get from userId
            Integer userId = null;
            Object uidAttr = request.getAttribute("userId");
            if (uidAttr instanceof Integer) {
                userId = (Integer) uidAttr;
            } else if (uidAttr != null) {
                try {
                    userId = Integer.parseInt(String.valueOf(uidAttr));
                } catch (Exception ignore) {
                }
            }

            // If userId found, get name from database
            if (userId != null) {
                try (Connection conn = DatabaseConnection.getConnection()) {
                    String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, userId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String fullName = rs.getString("fullName");
                                if (fullName != null && !fullName.trim().isEmpty()) {
                                    return fullName;
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Error getting user name from database: " + e.getMessage());
                }
            }

            return "Unknown User";
        } catch (Exception e) {
            System.err.println("Error getting current user name: " + e.getMessage());
            return "Unknown User";
        }
    }

    /**
     * Format date as "Sunday 28th of December 2025 12:38:00 pm UTC"
     */
    private String formatDate(Date date) {
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.ENGLISH);
        SimpleDateFormat dayOfMonthFormat = new SimpleDateFormat("d");
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.ENGLISH);
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH);

        String day = dayFormat.format(date);
        int dayOfMonth = Integer.parseInt(dayOfMonthFormat.format(date));
        String month = monthFormat.format(date);
        String year = yearFormat.format(date);
        String time = timeFormat.format(date);

        // Add ordinal suffix
        String suffix = getOrdinalSuffix(dayOfMonth);

        return String.format("%s %d%s of %s %s %s UTC", day, dayOfMonth, suffix, month, year, time);
    }

    private String getOrdinalSuffix(int n) {
        if (n >= 11 && n <= 13) {
            return "th";
        }
        switch (n % 10) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
            default:
                return "th";
        }
    }

    /**
     * Export to Excel format
     */
    private void exportToExcel(HttpServletResponse response, List<Map<String, String>> data,
            List<String> columns, Map<String, String> columnLabels, String category, String userName)
            throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + category + "_export_"
                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(category);

            int rowNum = 0;

            // Add header with logo and user info
            Row headerRow = sheet.createRow(rowNum++);
            Cell headerCell = headerRow.createCell(0);
            headerCell.setCellValue("BUDG");
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 14);
            headerStyle.setFont(headerFont);
            headerCell.setCellStyle(headerStyle);

            Row userRow = sheet.createRow(rowNum++);
            Cell userCell = userRow.createCell(0);
            userCell.setCellValue("printed by " + userName + " - " + formatDate(new Date()));

            // Empty row
            rowNum++;

            // Create column headers
            Row columnHeaderRow = sheet.createRow(rowNum++);
            CellStyle columnHeaderStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font columnHeaderFont = workbook.createFont();
            columnHeaderFont.setBold(true);
            columnHeaderStyle.setFont(columnHeaderFont);
            columnHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            columnHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (int i = 0; i < columns.size(); i++) {
                Cell cell = columnHeaderRow.createCell(i);
                String columnKey = columns.get(i);
                String label = columnLabels.getOrDefault(columnKey, columnKey);
                cell.setCellValue(label);
                cell.setCellStyle(columnHeaderStyle);
            }

            // Add data rows
            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true);

            for (Map<String, String> row : data) {
                Row dataRow = sheet.createRow(rowNum++);
                for (int i = 0; i < columns.size(); i++) {
                    String columnKey = columns.get(i);
                    String value = row.getOrDefault(columnKey, "");
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(value);
                    // Enable text wrapping for cells that might contain line breaks (like
                    // stakeholders)
                    if (value.contains("\n") || columnKey.toLowerCase().contains("stakeholder")) {
                        cell.setCellStyle(wrapStyle);
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to response
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
            }
        }
    }

    /**
     * Export to PDF format
     */
    private void exportToPDF(HttpServletResponse response, List<Map<String, String>> data,
            List<String> columns, Map<String, String> columnLabels, String category, String userName)
            throws IOException, DocumentException {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + category + "_export_"
                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf");

        Document document = new Document(PageSize.A4.rotate());
        OutputStream out = null;
        try {
            out = response.getOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            // Add logo/header
            com.itextpdf.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.BLACK);
            Paragraph logo = new Paragraph("BUDG", headerFont);
            logo.setAlignment(Element.ALIGN_LEFT);
            logo.setSpacingAfter(10);
            document.add(logo);

            // Add user info
            com.itextpdf.text.Font userFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.GRAY);
            Paragraph userInfo = new Paragraph("printed by " + userName + " - " + formatDate(new Date()), userFont);
            userInfo.setAlignment(Element.ALIGN_LEFT);
            userInfo.setSpacingAfter(20);
            document.add(userInfo);

            // Create table
            PdfPTable table = new PdfPTable(columns.size());
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(10f);

            // Set column widths (equal width)
            float[] columnWidths = new float[columns.size()];
            Arrays.fill(columnWidths, 100f / columns.size());
            table.setWidths(columnWidths);

            // Add headers
            com.itextpdf.text.Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10,
                    BaseColor.WHITE);
            for (String columnKey : columns) {
                String label = columnLabels.getOrDefault(columnKey, columnKey);
                PdfPCell cell = new PdfPCell(new Phrase(label, tableHeaderFont));
                cell.setBackgroundColor(new BaseColor(70, 130, 180));
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            // Add data rows
            com.itextpdf.text.Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 9, BaseColor.BLACK);
            for (Map<String, String> row : data) {
                for (String columnKey : columns) {
                    String value = row.getOrDefault(columnKey, "");
                    // Handle line breaks in stakeholders column
                    if (value.contains("\n")) {
                        // Split by newlines and create multiple phrases
                        String[] lines = value.split("\n");
                        Phrase phrase = new Phrase();
                        for (int i = 0; i < lines.length; i++) {
                            phrase.add(new Chunk(lines[i], dataFont));
                            if (i < lines.length - 1) {
                                phrase.add(Chunk.NEWLINE);
                            }
                        }
                        PdfPCell cell = new PdfPCell(phrase);
                        cell.setPadding(4);
                        table.addCell(cell);
                    } else {
                        table.addCell(new Phrase(value, dataFont));
                    }
                }
            }

            document.add(table);

            // Add footer
            Paragraph footer = new Paragraph("Total Records: " + data.size(), userFont);
            footer.setAlignment(Element.ALIGN_RIGHT);
            footer.setSpacingBefore(20);
            document.add(footer);

            document.close();

        } finally {
            if (document != null && document.isOpen()) {
                document.close();
            }
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }

    /**
     * Export to CSV format.
     * Uses only the response OutputStream (never getWriter()) to avoid IllegalStateException.
     */
    private void exportToCSV(HttpServletResponse response, List<Map<String, String>> data,
            List<String> columns, Map<String, String> columnLabels, String category, String userName)
            throws IOException {
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + category + "_export_"
                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv");

        OutputStream out = response.getOutputStream();
        // Add UTF-8 BOM for Excel compatibility
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            // Write header info
            writer.println("BUDG");
            writer.println("printed by " + userName + " - " + formatDate(new Date()));
            writer.println(); // Empty line

            // Write column headers
            List<String> headerValues = new ArrayList<>();
            for (String columnKey : columns) {
                String label = columnLabels.getOrDefault(columnKey, columnKey);
                headerValues.add(escapeCSV(label));
            }
            writer.println(String.join(",", headerValues));

            // Write data rows
            for (Map<String, String> row : data) {
                List<String> rowValues = new ArrayList<>();
                for (String columnKey : columns) {
                    String value = row.getOrDefault(columnKey, "");
                    rowValues.add(escapeCSV(value));
                }
                writer.println(String.join(",", rowValues));
            }
        }
    }

    /**
     * Escape CSV value
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        // Escape quotes and wrap in quotes if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Fetch stakeholders from database for given category and object IDs
     * 
     * @param category  The category/facet type (e.g., "dataset", "attribute",
     *                  "system")
     * @param objectIds List of object IDs to fetch stakeholders for
     * @return Map of object ID to stakeholders string (formatted as "Role -
     *         Name\nRole - Name")
     */
    private Map<Integer, String> fetchStakeholdersFromDatabase(String category, List<Integer> objectIds) {
        Map<Integer, String> stakeholdersMap = new HashMap<>();

        if (objectIds == null || objectIds.isEmpty()) {
            return stakeholdersMap;
        }

        // Normalize category name
        String normalizedCategory = category.toLowerCase().trim();

        // Map category to table and column names
        String junctionTable = null;
        String idColumn = null;
        String objectIdColumn = "Object_x_ipid"; // Default

        switch (normalizedCategory) {
            case "dataset":
            case "datasets":
            case "data-set":
            case "data-sets":
                junctionTable = "dataset_x_objectxpeople";
                idColumn = "Dataset_ID";
                break;
            case "attribute":
            case "attributes":
                junctionTable = "attribute_x_objectxpeople";
                idColumn = "AttributeID";
                break;
            case "system":
            case "systems":
                junctionTable = "system_x_objectxpeople";
                idColumn = "SystemID";
                break;
            case "glossary":
            case "glossaries":
                junctionTable = "glossary_x_objectxpeople";
                idColumn = "GlossaryID";
                break;
            case "process":
            case "processes":
                junctionTable = "process_x_objectxpeople";
                idColumn = "process_id";
                objectIdColumn = "object_x_ip";
                break;
            case "policy":
            case "policies":
                junctionTable = "policy_x_objectxpeople";
                idColumn = "Policy_ID";
                objectIdColumn = "Object_X_IP";
                break;
            case "regulation":
            case "regulations":
                junctionTable = "regulation_x_objectxpeople";
                idColumn = "RegulationID";
                break;
            case "project":
            case "projects":
                junctionTable = "project_x_objectxpeople";
                idColumn = "project_id";
                objectIdColumn = "object_x_ip";
                break;
            case "product":
            case "products":
                junctionTable = "product_x_objectxpeople";
                idColumn = "product_id";
                objectIdColumn = "object_x_ip";
                break;
            case "client":
            case "clients":
                junctionTable = "client_x_objectxpeople";
                idColumn = "ClientID";
                break;
            case "committee":
            case "committees":
                junctionTable = "committee_x_objectxpeople";
                idColumn = "Committee_ID";
                objectIdColumn = "Object_X_ipid";
                break;
            case "business-area":
            case "businessarea":
            case "business-areas":
                junctionTable = "businessarea_x_objectxpeople";
                idColumn = "BusinessAreaID";
                break;
            case "legal-entity":
            case "legalentity":
            case "legal":
                junctionTable = "legal_x_objectxpeople";
                idColumn = "Legal_ID";
                objectIdColumn = "Object_X_IP";
                break;
            case "capability":
            case "capabilities":
                junctionTable = "capability_x_objectxpeople";
                idColumn = "CapabilityID";
                break;
            case "interface":
            case "interfaces":
                junctionTable = "interface_x_objectxpeople";
                idColumn = "InterfaceID";
                break;
            default:
                System.err.println("Unknown category for stakeholders: " + category);
                return stakeholdersMap;
        }

        if (junctionTable == null || idColumn == null) {
            return stakeholdersMap;
        }

        // Build SQL query to fetch stakeholders for all object IDs
        String sql = String.format("""
                SELECT
                    jt.%s AS object_id,
                    orl.PrimaryName AS role_name,
                    CONCAT(p.First_Name, ' ', p.Last_Name) AS person_name
                FROM %s jt
                JOIN object_x_people oxp ON jt.%s = oxp.ID
                JOIN people p ON oxp.ipid = p.ID
                JOIN object_role orl ON oxp.RoleID = orl.ID
                WHERE jt.%s IN (%s)
                ORDER BY jt.%s, orl.PrimaryName, p.Last_Name, p.First_Name
                """,
                idColumn, junctionTable, objectIdColumn, idColumn,
                objectIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")),
                idColumn);

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int objectId = rs.getInt("object_id");
                String roleName = rs.getString("role_name");
                String personName = rs.getString("person_name");

                // Format: "Role - Name"
                String stakeholderLine = roleName + " - " + personName;

                // Add to map, appending if already exists (multiple stakeholders per object)
                if (stakeholdersMap.containsKey(objectId)) {
                    stakeholdersMap.put(objectId, stakeholdersMap.get(objectId) + "\n" + stakeholderLine);
                } else {
                    stakeholdersMap.put(objectId, stakeholderLine);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching stakeholders from database: " + e.getMessage());
            e.printStackTrace();
        }

        return stakeholdersMap;
    }
}
