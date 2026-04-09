package com.example.budg_v2.bulk.objects;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generator for Excel reports for bulk delete operations
 * Creates formatted Excel files with colored rows based on success/failure status
 */
public class BulkDeleteReportGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(BulkDeleteReportGenerator.class);
    
    /**
     * Report row data structure
     */
    public static class ReportRow {
        private int rowNumber;
        private int objectId;
        private String objectName;
        private String status; // "Success", "Failed", "Warning"
        private String errorMessages;
        private String warningMessages;
        private String actionTaken;
        
        public ReportRow(int rowNumber, int objectId, String objectName, String status, 
                        String errorMessages, String warningMessages, String actionTaken) {
            this.rowNumber = rowNumber;
            this.objectId = objectId;
            this.objectName = objectName;
            this.status = status;
            this.errorMessages = errorMessages;
            this.warningMessages = warningMessages;
            this.actionTaken = actionTaken;
        }
        
        // Getters
        public int getRowNumber() { return rowNumber; }
        public int getObjectId() { return objectId; }
        public String getObjectName() { return objectName; }
        public String getStatus() { return status; }
        public String getErrorMessages() { return errorMessages; }
        public String getWarningMessages() { return warningMessages; }
        public String getActionTaken() { return actionTaken; }
    }
    
    /**
     * Generate Excel report from report rows
     * @param reportRows List of report rows
     * @param entityName Entity name for the report title
     * @param jobId Job ID for the report
     * @return Byte array of the Excel file
     */
    public byte[] generateReport(List<ReportRow> reportRows, String entityName, int jobId) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Delete Report");
            
            // Create styles
            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle successStyle = createSuccessStyle(workbook);
            XSSFCellStyle failedStyle = createFailedStyle(workbook);
            XSSFCellStyle warningStyle = createWarningStyle(workbook);
            XSSFCellStyle defaultStyle = createDefaultStyle(workbook);
            
            int rowNum = 0;
            
            // Title row
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Bulk Delete Report - " + entityName);
            titleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
            
            // Summary row
            Row summaryRow = sheet.createRow(rowNum++);
            int successCount = (int) reportRows.stream().filter(r -> "Success".equals(r.getStatus())).count();
            int failedCount = (int) reportRows.stream().filter(r -> "Failed".equals(r.getStatus())).count();
            int warningCount = (int) reportRows.stream().filter(r -> "Warning".equals(r.getStatus())).count();
            
            Cell summaryCell = summaryRow.createCell(0);
            summaryCell.setCellValue(String.format("Summary: %d Success, %d Failed, %d Warnings (Total: %d)", 
                successCount, failedCount, warningCount, reportRows.size()));
            summaryCell.setCellStyle(defaultStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));
            
            // Empty row
            rowNum++;
            
            // Header row
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Row Number", "Object ID", "Object Name", "Status", 
                               "Error Messages", "Warning Messages", "Action Taken"};
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Freeze header row
            sheet.createFreezePane(0, rowNum - 1);
            
            // Data rows
            for (ReportRow reportRow : reportRows) {
                Row row = sheet.createRow(rowNum++);
                
                // Determine style based on status
                XSSFCellStyle rowStyle = defaultStyle;
                if ("Success".equals(reportRow.getStatus())) {
                    rowStyle = successStyle;
                } else if ("Failed".equals(reportRow.getStatus())) {
                    rowStyle = failedStyle;
                } else if ("Warning".equals(reportRow.getStatus())) {
                    rowStyle = warningStyle;
                }
                
                // Row Number
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(reportRow.getRowNumber());
                cell0.setCellStyle(rowStyle);
                
                // Object ID
                Cell cell1 = row.createCell(1);
                cell1.setCellValue(reportRow.getObjectId());
                cell1.setCellStyle(rowStyle);
                
                // Object Name
                Cell cell2 = row.createCell(2);
                cell2.setCellValue(reportRow.getObjectName() != null ? reportRow.getObjectName() : "");
                cell2.setCellStyle(rowStyle);
                
                // Status
                Cell cell3 = row.createCell(3);
                cell3.setCellValue(reportRow.getStatus());
                cell3.setCellStyle(rowStyle);
                
                // Error Messages
                Cell cell4 = row.createCell(4);
                cell4.setCellValue(reportRow.getErrorMessages() != null ? reportRow.getErrorMessages() : "");
                cell4.setCellStyle(rowStyle);
                
                // Warning Messages
                Cell cell5 = row.createCell(5);
                cell5.setCellValue(reportRow.getWarningMessages() != null ? reportRow.getWarningMessages() : "");
                cell5.setCellStyle(rowStyle);
                
                // Action Taken
                Cell cell6 = row.createCell(6);
                cell6.setCellValue(reportRow.getActionTaken() != null ? reportRow.getActionTaken() : "");
                cell6.setCellStyle(rowStyle);
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Set minimum column width
                if (sheet.getColumnWidth(i) < 2000) {
                    sheet.setColumnWidth(i, 2000);
                }
            }
            
            // Write to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            workbook.close();
            
            logger.info("Generated Excel report with {} rows for entity {} job {}", 
                reportRows.size(), entityName, jobId);
            
            return baos.toByteArray();
        }
    }
    
    /**
     * Create header style
     */
    private XSSFCellStyle createHeaderStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        
        // Background color - dark gray
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 68, (byte) 68, (byte) 68}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Font
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        
        // Alignment
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Border
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }
    
    /**
     * Create success style (green background)
     */
    private XSSFCellStyle createSuccessStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        
        // Background color - light green
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 198, (byte) 239, (byte) 206}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Font
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        
        // Border
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Wrap text
        style.setWrapText(true);
        
        return style;
    }
    
    /**
     * Create failed style (red background)
     */
    private XSSFCellStyle createFailedStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        
        // Background color - light red
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 255, (byte) 199, (byte) 206}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Font
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        
        // Border
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Wrap text
        style.setWrapText(true);
        
        return style;
    }
    
    /**
     * Create warning style (yellow background)
     */
    private XSSFCellStyle createWarningStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        
        // Background color - light yellow
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 255, (byte) 235, (byte) 156}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Font
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        
        // Border
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Wrap text
        style.setWrapText(true);
        
        return style;
    }
    
    /**
     * Create default style
     */
    private XSSFCellStyle createDefaultStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        
        // Font
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        
        // Border
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Wrap text
        style.setWrapText(true);
        
        return style;
    }
}

