package com.example.budg_v2;

import com.google.gson.Gson;

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
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servlet to handle People data export
 * Supports PDF, Excel, and CSV formats
 * Fields: First Name, Last Name, Email, Function Name, Org Unit Name
 */
@WebServlet("/api/export/people")
public class PeopleExportServlet extends HttpServlet {
    private static final String CONFIG_KEY = "EXPORT_PEOPLE_ENABLED";
    @SuppressWarnings("unused")
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Check authentication
        // HttpSession session = request.getSession(false);
        // if (session == null || session.getAttribute("user") == null) {
        // response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // response.getWriter().print("{\"error\": \"Unauthorized\"}");
        // return;
        // }

        // Check if export is enabled
        if (!isExportEnabled()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().print("{\"error\": \"Export is not enabled\"}");
            return;
        }

        String format = request.getParameter("format");
        if (format == null || format.isEmpty()) {
            format = "excel"; // Default format
        }

        String idsParam = request.getParameter("ids"); // Optional comma-separated person IDs for export selected only

        try {
            List<PersonData> people = fetchPeopleData(idsParam);

            switch (format.toLowerCase()) {
                case "pdf":
                    exportToPDF(response, people);
                    break;
                case "csv":
                    exportToCSV(response, people);
                    break;
                case "excel":
                default:
                    exportToExcel(response, people);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"error\": \"Export failed: " + e.getMessage() + "\"}");
        }
    }

    private boolean isExportEnabled() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT definition FROM app_config WHERE config_key = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, CONFIG_KEY);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return "true".equalsIgnoreCase(rs.getString("definition"));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Fetch people data, optionally restricted to given IDs.
     * @param idsParam Optional comma-separated person IDs (e.g. "1,2,3"). If null or empty, all people are returned.
     */
    private List<PersonData> fetchPeopleData(String idsParam) throws SQLException {
        List<PersonData> people = new ArrayList<>();

        List<Integer> ids = null;
        if (idsParam != null && !idsParam.trim().isEmpty()) {
            ids = Arrays.stream(idsParam.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(id -> id != null && id > 0)
                    .collect(Collectors.toList());
            if (ids.isEmpty()) {
                ids = null;
            }
        }

        String sql = "SELECT p.First_Name, p.Last_Name, p.Email, p.Function_Name, " +
                "o.Name as Org_Unit_Name " +
                "FROM people p " +
                "LEFT JOIN org_unit o ON p.Org_Unit_ID = o.ID ";
        if (ids != null && !ids.isEmpty()) {
            sql += "WHERE p.ID IN (" + ids.stream().map(i -> "?").collect(Collectors.joining(",")) + ") ";
        }
        sql += "ORDER BY p.Last_Name, p.First_Name";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (ids != null && !ids.isEmpty()) {
                for (int i = 0; i < ids.size(); i++) {
                    stmt.setInt(i + 1, ids.get(i));
                }
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PersonData person = new PersonData();
                    person.firstName = rs.getString("First_Name");
                    person.lastName = rs.getString("Last_Name");
                    person.email = rs.getString("Email");
                    person.functionName = rs.getString("Function_Name");
                    person.orgUnitName = rs.getString("Org_Unit_Name");
                    people.add(person);
                }
            }
        }

        return people;
    }

    private void exportToExcel(HttpServletResponse response, List<PersonData> people) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=people_export_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("People");

            // Create header row
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = { "First Name", "Last Name", "Email", "Function Name", "Org Unit" };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            int rowNum = 1;
            for (PersonData person : people) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(person.firstName != null ? person.firstName : "");
                row.createCell(1).setCellValue(person.lastName != null ? person.lastName : "");
                row.createCell(2).setCellValue(person.email != null ? person.email : "");
                row.createCell(3).setCellValue(person.functionName != null ? person.functionName : "");
                row.createCell(4).setCellValue(person.orgUnitName != null ? person.orgUnitName : "");
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to response
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
            }
        }
    }

    private void exportToPDF(HttpServletResponse response, List<PersonData> people)
            throws IOException, DocumentException {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=people_export_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf");

        Document document = new Document(PageSize.A4.rotate());
        OutputStream out = null;
        try {
            out = response.getOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            // Add title
            com.itextpdf.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Paragraph title = new Paragraph("People Export", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Add export date
            com.itextpdf.text.Font dateFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.GRAY);
            Paragraph date = new Paragraph(
                    "Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), dateFont);
            date.setAlignment(Element.ALIGN_CENTER);
            date.setSpacingAfter(20);
            document.add(date);

            // Create table
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(10f);

            // Set column widths
            float[] columnWidths = { 15f, 15f, 25f, 20f, 25f };
            table.setWidths(columnWidths);

            // Add headers
            com.itextpdf.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.WHITE);
            String[] headers = { "First Name", "Last Name", "Email", "Function Name", "Org Unit" };
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(new BaseColor(70, 130, 180));
                cell.setPadding(8);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            // Add data rows
            com.itextpdf.text.Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
            for (PersonData person : people) {
                table.addCell(new Phrase(person.firstName != null ? person.firstName : "", dataFont));
                table.addCell(new Phrase(person.lastName != null ? person.lastName : "", dataFont));
                table.addCell(new Phrase(person.email != null ? person.email : "", dataFont));
                table.addCell(new Phrase(person.functionName != null ? person.functionName : "", dataFont));
                table.addCell(new Phrase(person.orgUnitName != null ? person.orgUnitName : "", dataFont));
            }

            document.add(table);

            // Add footer
            Paragraph footer = new Paragraph("Total Records: " + people.size(), dateFont);
            footer.setAlignment(Element.ALIGN_RIGHT);
            footer.setSpacingBefore(20);
            document.add(footer);

            // IMPORTANT: Close document before closing the stream
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

    private void exportToCSV(HttpServletResponse response, List<PersonData> people) throws IOException {
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=people_export_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv");

        try (PrintWriter writer = response.getWriter()) {
            // Write header
            writer.println("First Name,Last Name,Email,Function Name,Org Unit");

            // Write data rows
            for (PersonData person : people) {
                writer.println(
                        escapeCSV(person.firstName) + "," +
                                escapeCSV(person.lastName) + "," +
                                escapeCSV(person.email) + "," +
                                escapeCSV(person.functionName) + "," +
                                escapeCSV(person.orgUnitName));
            }
        }
    }

    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        // Escape quotes and wrap in quotes if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static class PersonData {
        String firstName;
        String lastName;
        String email;
        String functionName;
        String orgUnitName;
    }
}
