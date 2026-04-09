package com.example.budg_v2;

import com.example.budg_v2.model.RegulationMaturity;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@WebServlet(name = "RegulationComplianceLevelServlet", urlPatterns = {"/api/regulation-compliance-level/*"})
public class RegulationComplianceLevelServlet extends HttpServlet {
    
    @Override
    public void init() throws ServletException {
        super.init();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/list")) {
                List<RegulationMaturity> complianceLevels = getAllRegulationComplianceLevels();
                PrintWriter out = response.getWriter();
                out.print(JsonUtil.toJson(complianceLevels));
                out.flush();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private List<RegulationMaturity> getAllRegulationComplianceLevels() throws SQLException {
        List<RegulationMaturity> complianceLevels = new ArrayList<>();
        String sql = "SELECT ID, PrimaryName, Description, LastUpdateDatetime, LastUpdate_UserID FROM Regulation_Compliance_Level ORDER BY PrimaryName";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                RegulationMaturity complianceLevel = new RegulationMaturity();
                complianceLevel.setId(rs.getInt("ID"));
                complianceLevel.setPrimaryName(rs.getString("PrimaryName"));
                complianceLevel.setDescription(rs.getString("Description"));
                
                Timestamp timestamp = rs.getTimestamp("LastUpdateDatetime");
                if (timestamp != null) {
                    complianceLevel.setLastUpdateDatetime(timestamp.toLocalDateTime());
                }
                
                int userId = rs.getInt("LastUpdate_UserID");
                if (!rs.wasNull()) {
                    complianceLevel.setLastUpdateUserId(userId);
                }
                
                complianceLevels.add(complianceLevel);
            }
        }
        
        return complianceLevels;
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        PrintWriter out = response.getWriter();
        out.print("{\"error\": \"" + message + "\"}");
        out.flush();
    }
}
