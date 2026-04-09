package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

@WebServlet("/check-policy-table")
public class CheckPolicyTableServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("text/html; charset=UTF-8");
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><title>Policy Table Check</title>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; direction: rtl; }");
        html.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; }");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        html.append("th { background-color: #f2f2f2; }");
        html.append(".success { color: green; }");
        html.append(".error { color: red; }");
        html.append(".info { color: blue; }");
        html.append("</style></head><body>");
        
        html.append("<h1>فحص جدول Policy في قاعدة البيانات المتصلة</h1>");
        html.append("<p><strong>قاعدة البيانات:</strong> localhost:3306/project</p>");
        
        try {
            // Test database connection
            html.append("<h2>1. الاتصال بقاعدة البيانات</h2>");
            try (Connection conn = DatabaseConnection.getConnection()) {
                html.append("<p class='success'>✓ تم الاتصال بقاعدة البيانات بنجاح!</p>");
                html.append("<p>URL: ").append(conn.getMetaData().getURL()).append("</p>");
                html.append("<p>نوع قاعدة البيانات: ").append(conn.getMetaData().getDatabaseProductName()).append("</p>");
            }
            
            // Check all tables
            html.append("<h2>2. جميع الجداول في قاعدة البيانات</h2>");
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                ResultSet rs = stmt.executeQuery("SHOW TABLES");
                html.append("<table>");
                html.append("<tr><th>اسم الجدول</th><th>يحتوي على Policy</th></tr>");
                
                boolean policyTableExists = false;
                while (rs.next()) {
                    String tableName = rs.getString(1);
                    boolean containsPolicy = tableName.toLowerCase().contains("policy");
                    if (containsPolicy) policyTableExists = true;
                    
                    html.append("<tr>");
                    html.append("<td>").append(tableName).append("</td>");
                    html.append("<td class='").append(containsPolicy ? "success" : "error").append("'>")
                        .append(containsPolicy ? "✓ نعم" : "❌ لا").append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");
                
                if (policyTableExists) {
                    html.append("<p class='success'>✓ تم العثور على جداول تحتوي على كلمة 'policy'</p>");
                } else {
                    html.append("<p class='error'>❌ لم يتم العثور على أي جدول يحتوي على كلمة 'policy'</p>");
                }
            }
            
            // Check for policy table specifically
            html.append("<h2>3. فحص جدول policy المحدد</h2>");
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                // Check if policy table exists
                ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'policy'");
                if (rs.next()) {
                    html.append("<p class='success'>✓ تم العثور على جدول 'policy'!</p>");
                    html.append("<p class='info'>رابط phpMyAdmin: <a href='http://localhost/phpmyadmin/index.php?route=/table/change&db=project&table=policy&server=2' target='_blank'>فتح في phpMyAdmin</a></p>");
                    
                    // Get table structure
                    html.append("<h3>هيكل جدول policy:</h3>");
                    rs = stmt.executeQuery("DESCRIBE policy");
                    html.append("<table>");
                    html.append("<tr><th>العمود</th><th>النوع</th><th>Null</th><th>Key</th><th>Default</th><th>Extra</th></tr>");
                    
                    while (rs.next()) {
                        html.append("<tr>");
                        html.append("<td>").append(rs.getString("Field")).append("</td>");
                        html.append("<td>").append(rs.getString("Type")).append("</td>");
                        html.append("<td>").append(rs.getString("Null")).append("</td>");
                        html.append("<td>").append(rs.getString("Key")).append("</td>");
                        html.append("<td>").append(rs.getString("Default")).append("</td>");
                        html.append("<td>").append(rs.getString("Extra")).append("</td>");
                        html.append("</tr>");
                    }
                    html.append("</table>");
                    
                    // Get data count
                    rs = stmt.executeQuery("SELECT COUNT(*) as count FROM policy");
                    if (rs.next()) {
                        int count = rs.getInt("count");
                        html.append("<p><strong>عدد السجلات:</strong> ").append(count).append("</p>");
                        
                        if (count > 0) {
                            // Show sample data
                            html.append("<h3>عينة من البيانات (أول 10 سجلات):</h3>");
                            rs = stmt.executeQuery("SELECT * FROM policy LIMIT 10");
                            html.append("<table>");
                            
                            // Get column names first
                            ResultSetMetaData metaData = rs.getMetaData();
                            int columnCount = metaData.getColumnCount();
                            html.append("<tr>");
                            for (int i = 1; i <= columnCount; i++) {
                                html.append("<th>").append(metaData.getColumnName(i)).append("</th>");
                            }
                            html.append("</tr>");
                            
                            // Show data
                            while (rs.next()) {
                                html.append("<tr>");
                                for (int i = 1; i <= columnCount; i++) {
                                    String value = rs.getString(i);
                                    if (value == null) {
                                        html.append("<td><em>NULL</em></td>");
                                    } else if (value.length() > 100) {
                                        html.append("<td>").append(value.substring(0, 100)).append("...</td>");
                                    } else {
                                        html.append("<td>").append(value).append("</td>");
                                    }
                                }
                                html.append("</tr>");
                            }
                            html.append("</table>");
                            
                            // Show all data if count is reasonable
                            if (count <= 50) {
                                html.append("<h3>جميع البيانات في جدول policy:</h3>");
                                rs = stmt.executeQuery("SELECT * FROM policy ORDER BY id");
                                html.append("<table>");
                                
                                // Get column names first
                                metaData = rs.getMetaData();
                                columnCount = metaData.getColumnCount();
                                html.append("<tr>");
                                for (int i = 1; i <= columnCount; i++) {
                                    html.append("<th>").append(metaData.getColumnName(i)).append("</th>");
                                }
                                html.append("</tr>");
                                
                                // Show all data
                                while (rs.next()) {
                                    html.append("<tr>");
                                    for (int i = 1; i <= columnCount; i++) {
                                        String value = rs.getString(i);
                                        if (value == null) {
                                            html.append("<td><em>NULL</em></td>");
                                        } else if (value.length() > 200) {
                                            html.append("<td title='").append(value.replace("\"", "&quot;")).append("'>").append(value.substring(0, 200)).append("...</td>");
                                        } else {
                                            html.append("<td>").append(value).append("</td>");
                                        }
                                    }
                                    html.append("</tr>");
                                }
                                html.append("</table>");
                            }
                        } else {
                            html.append("<p class='error'>❌ جدول policy فارغ!</p>");
                        }
                    }
                } else {
                    html.append("<p class='error'>❌ لم يتم العثور على جدول 'policy'</p>");
                }
            }
            
            // Check for policy in other tables
            html.append("<h2>4. البحث عن Policy في جداول أخرى</h2>");
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                // Get all table names
                ResultSet rs = stmt.executeQuery("SHOW TABLES");
                while (rs.next()) {
                    String tableName = rs.getString(1);
                    
                    // Check if table has columns that might contain policy data
                    try {
                        ResultSet columnRs = stmt.executeQuery("SHOW COLUMNS FROM " + tableName);
                        boolean hasPolicyColumn = false;
                        while (columnRs.next()) {
                            String columnName = columnRs.getString("Field").toLowerCase();
                            if (columnName.contains("policy")) {
                                hasPolicyColumn = true;
                                break;
                            }
                        }
                        
                        if (hasPolicyColumn) {
                            html.append("<h3>جدول ").append(tableName).append(" يحتوي على أعمدة Policy:</h3>");
                            
                            // Show columns
                            columnRs = stmt.executeQuery("SHOW COLUMNS FROM " + tableName);
                            html.append("<table>");
                            html.append("<tr><th>العمود</th><th>النوع</th><th>Null</th><th>Key</th><th>Default</th></tr>");
                            
                            while (columnRs.next()) {
                                String columnName = columnRs.getString("Field");
                                if (columnName.toLowerCase().contains("policy")) {
                                    html.append("<tr style='background-color: #ffffcc;'>");
                                } else {
                                    html.append("<tr>");
                                }
                                html.append("<td>").append(columnName).append("</td>");
                                html.append("<td>").append(columnRs.getString("Type")).append("</td>");
                                html.append("<td>").append(columnRs.getString("Null")).append("</td>");
                                html.append("<td>").append(columnRs.getString("Key")).append("</td>");
                                html.append("<td>").append(columnRs.getString("Default")).append("</td>");
                                html.append("</tr>");
                            }
                            html.append("</table>");
                        }
                    } catch (Exception e) {
                        // Skip tables that can't be accessed
                    }
                }
            }
            
        } catch (Exception e) {
            html.append("<h2 class='error'>خطأ</h2>");
            html.append("<p class='error'>").append(e.getMessage()).append("</p>");
            e.printStackTrace();
        }
        
        html.append("</body></html>");
        resp.getWriter().write(html.toString());
    }
}
