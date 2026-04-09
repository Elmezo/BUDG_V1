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

@WebServlet("/policy-structure")
public class PolicyTableStructureServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("text/html; charset=UTF-8");
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><title>Policy Table Structure</title>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; direction: rtl; }");
        html.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; }");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        html.append("th { background-color: #f2f2f2; }");
        html.append(".success { color: green; }");
        html.append(".error { color: red; }");
        html.append(".info { color: blue; }");
        html.append(".code { background-color: #f5f5f5; padding: 10px; border-radius: 5px; font-family: monospace; }");
        html.append("</style></head><body>");
        
        html.append("<h1>مكونات جدول Policy</h1>");
        html.append("<p><strong>قاعدة البيانات:</strong> localhost:3306/project</p>");
        
        try {
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                // فحص وجود الجدول
                ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'policy'");
                if (rs.next()) {
                    html.append("<p class='success'>✓ تم العثور على جدول policy</p>");
                    
                    // عرض هيكل الجدول
                    html.append("<h2>هيكل جدول Policy</h2>");
                    rs = stmt.executeQuery("DESCRIBE policy");
                    html.append("<table>");
                    html.append("<tr><th>العمود</th><th>النوع</th><th>Null</th><th>Key</th><th>Default</th><th>Extra</th></tr>");
                    
                    while (rs.next()) {
                        html.append("<tr>");
                        html.append("<td><strong>").append(rs.getString("Field")).append("</strong></td>");
                        html.append("<td>").append(rs.getString("Type")).append("</td>");
                        html.append("<td>").append(rs.getString("Null")).append("</td>");
                        html.append("<td>").append(rs.getString("Key")).append("</td>");
                        html.append("<td>").append(rs.getString("Default") != null ? rs.getString("Default") : "NULL").append("</td>");
                        html.append("<td>").append(rs.getString("Extra")).append("</td>");
                        html.append("</tr>");
                    }
                    html.append("</table>");
                    
                    // عرض معلومات إضافية عن الجدول
                    html.append("<h2>معلومات إضافية</h2>");
                    
                    // عدد السجلات
                    rs = stmt.executeQuery("SELECT COUNT(*) as count FROM policy");
                    if (rs.next()) {
                        int count = rs.getInt("count");
                        html.append("<p><strong>عدد السجلات:</strong> ").append(count).append("</p>");
                    }
                    
                    // حجم الجدول
                    rs = stmt.executeQuery("SELECT ROUND(((data_length + index_length) / 1024 / 1024), 2) AS 'DB Size in MB' FROM information_schema.tables WHERE table_schema='project' AND table_name='policy'");
                    if (rs.next()) {
                        String size = rs.getString("DB Size in MB");
                        html.append("<p><strong>حجم الجدول:</strong> ").append(size).append(" MB</p>");
                    }
                    
                    // عرض عينة من البيانات
                    rs = stmt.executeQuery("SELECT COUNT(*) as count FROM policy");
                    if (rs.next() && rs.getInt("count") > 0) {
                        html.append("<h2>عينة من البيانات</h2>");
                        rs = stmt.executeQuery("SELECT * FROM policy LIMIT 5");
                        
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        
                        html.append("<table>");
                        html.append("<tr>");
                        for (int i = 1; i <= columnCount; i++) {
                            html.append("<th>").append(metaData.getColumnName(i)).append("</th>");
                        }
                        html.append("</tr>");
                        
                        while (rs.next()) {
                            html.append("<tr>");
                            for (int i = 1; i <= columnCount; i++) {
                                String value = rs.getString(i);
                                if (value == null) {
                                    html.append("<td><em>NULL</em></td>");
                                } else if (value.length() > 50) {
                                    html.append("<td title='").append(value.replace("\"", "&quot;")).append("'>").append(value.substring(0, 50)).append("...</td>");
                                } else {
                                    html.append("<td>").append(value).append("</td>");
                                }
                            }
                            html.append("</tr>");
                        }
                        html.append("</table>");
                    }
                    
                    // عرض SQL لإنشاء الجدول
                    html.append("<h2>SQL لإنشاء الجدول</h2>");
                    rs = stmt.executeQuery("SHOW CREATE TABLE policy");
                    if (rs.next()) {
                        String createTableSQL = rs.getString("Create Table");
                        html.append("<div class='code'>").append(createTableSQL.replace("\n", "<br>")).append("</div>");
                    }
                    
                } else {
                    html.append("<p class='error'>❌ لم يتم العثور على جدول policy</p>");
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
