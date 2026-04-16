package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.RequestedSegmentFilterUtil;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Committee Lookup Servlet for getting reference data
 * Endpoint: /api/committee/lookup
 */
@WebServlet("/api/committee/lookup")
public class CommitteeLookupServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();
    private final SegmentDAO segmentDAO = new SegmentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String type = request.getParameter("type");

            if (type == null || type.isEmpty()) {
                Map<String, List<Map<String, Object>>> allLookups = getAllLookupData(conn);
                out.print(gson.toJson(allLookups));
            } else {
                List<Map<String, Object>> lookupData = getLookupDataByType(conn, type);
                if ("committees".equalsIgnoreCase(type)) {
                    lookupData = RequestedSegmentFilterUtil.filterByRequestedSegment(
                            lookupData,
                            RequestedSegmentFilterUtil.resolveEffectiveSegmentId(request, segmentDAO),
                            "Committee",
                            m -> ((Number) m.get("ID")).intValue());
                }
                out.print(gson.toJson(lookupData));
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    // Helper methods

    private Map<String, List<Map<String, Object>>> getAllLookupData(Connection conn) throws SQLException {
        Map<String, List<Map<String, Object>>> allLookups = new HashMap<>();

        allLookups.put("status", getStatusLookup(conn));
        allLookups.put("lifecycle", getLifecycleLookup(conn));
        allLookups.put("classification", getClassificationLookup(conn));
        allLookups.put("committeeType", getCommitteeTypeLookup(conn));
        allLookups.put("viewing", getViewingLookup(conn));
        allLookups.put("people", getPeopleLookup(conn));
        allLookups.put("committees", getCommitteesLookup(conn));

        return allLookups;
    }

    private List<Map<String, Object>> getLookupDataByType(Connection conn, String type) throws SQLException {
        switch (type.toLowerCase()) {
            case "status":
                return getStatusLookup(conn);
            case "lifecycle":
                return getLifecycleLookup(conn);
            case "classification":
                return getClassificationLookup(conn);
            case "committeetype":
            case "committee_type":
                return getCommitteeTypeLookup(conn);
            case "viewing":
                return getViewingLookup(conn);
            case "people":
                return getPeopleLookup(conn);
            case "committees":
                return getCommitteesLookup(conn);
            default:
                return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> getStatusLookup(Connection conn) throws SQLException {
        String query = "SELECT ID, primaryname, description FROM status ";
        return executeLookupQuery(conn, query);
    }

    private List<Map<String, Object>> getLifecycleLookup(Connection conn) throws SQLException {
        String query = "SELECT ID, PrimaryName, Description FROM committee_lifecycle ";
        return executeLookupQuery(conn, query);
    }

    private List<Map<String, Object>> getClassificationLookup(Connection conn) throws SQLException {
        String query = "SELECT ID, PrimaryName, Description FROM committee_classification ";
        return executeLookupQuery(conn, query);
    }

    private List<Map<String, Object>> getCommitteeTypeLookup(Connection conn) throws SQLException {
        String query = "SELECT ID, PrimaryName, Description FROM committee_type ";
        return executeLookupQuery(conn, query);
    }

    private List<Map<String, Object>> getViewingLookup(Connection conn) throws SQLException {
        String query = "SELECT id as ID, Name, description as Description FROM viewing ";
        return executeLookupQuery(conn, query);
    }

    private List<Map<String, Object>> getPeopleLookup(Connection conn) throws SQLException {
        String query = """
            SELECT ID, First_Name, Last_Name, Email, 
                   CONCAT(First_Name, ' ', Last_Name) AS Full_Name
            FROM people 
            WHERE Deleted_date IS NULL 
            ORDER BY First_Name, Last_Name
            """;
        return executeLookupQuery(conn, query);
    }

    private List<Map<String, Object>> getCommitteesLookup(Connection conn) throws SQLException {
        String query = """
            SELECT ID, PrimaryName, RefNumber, 
                   CONCAT(COALESCE(RefNumber, ''), ' - ', PrimaryName) AS Display_Name
            FROM committee 
            WHERE DeleteDatetime IS NULL 
            ORDER BY PrimaryName
            """;
        return executeLookupQuery(conn, query);
    }

    private List<Map<String, Object>> executeLookupQuery(Connection conn, String query) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                results.add(row);
            }
        }

        return results;
    }
}
