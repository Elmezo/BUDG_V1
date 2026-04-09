package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.SegmentResponseUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

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

/**
 * Committee Detail Servlet for handling complex queries with JOINs
 * Endpoint: /api/committee/detail
 */
@WebServlet("/api/committee/detail")
public class CommitteeDetailServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();
    private SegmentDAO segmentDAO = new SegmentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String idParam = request.getParameter("id");

            if (idParam != null && !idParam.isEmpty()) {
                // Get single committee with detailed information
                JsonObject committeeDetail = getCommitteeDetailById(conn, Integer.parseInt(idParam));
                if (committeeDetail != null) {
                    SegmentResponseUtil.SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, Integer.parseInt(idParam), "Committee");
                    SegmentResponseUtil.applySegmentInfo(committeeDetail, segmentInfo, request);
                    out.print(gson.toJson(committeeDetail));
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.print("{\"error\": \"Committee not found\"}");
                }
            } else {
                // Get all committees with detailed information
                List<JsonObject> committeeDetails = getAllCommitteeDetails(conn);
                // Add segment info to each committee
                for (JsonObject committeeDetail : committeeDetails) {
                    int committeeId = committeeDetail.get("ID").getAsInt();
                    SegmentResponseUtil.SegmentInfo segmentInfo = SegmentResponseUtil.resolveSegmentInfo(segmentDAO, committeeId, "Committee");
                    SegmentResponseUtil.applySegmentInfo(committeeDetail, segmentInfo, request);
                }
                out.print(gson.toJson(committeeDetails));
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

    private JsonObject getCommitteeDetailById(Connection conn, int id) throws SQLException {
        String query = """
            SELECT 
                c.ID,
                c.RefNumber AS Ref,
                c.PrimaryName AS Name,
                c.Description,
                s.primaryname AS Status,
                cl.PrimaryName AS Lifecycle,
                cc.PrimaryName AS Classification,
                ct.PrimaryName AS Committee_Type,
                v.Name AS Viewing,
                c.CreateDatetime AS Created,
                c.LastUpdateDatetime AS Last_Updated,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS Last_Updated_By,
                c.Parent_ID,
                parent.PrimaryName AS Parent_Name,
                p_created.ID AS Created_By_ID,
                CONCAT(p_created.First_Name, ' ', p_created.Last_Name) AS Created_By_Name
            FROM committee c
            LEFT JOIN status s ON c.Status = s.ID
            LEFT JOIN committee_lifecycle cl ON c.Lifecycle = cl.ID
            LEFT JOIN committee_classification cc ON c.Classification = cc.ID
            LEFT JOIN committee_type ct ON c.Committee_Type = ct.ID
            LEFT JOIN viewing v ON c.Is_Public = v.ID
            LEFT JOIN people p ON c.LastUpdate_UserID = p.ID
            LEFT JOIN people p_created ON c.Created_By = p_created.ID
            LEFT JOIN committee parent ON c.Parent_ID = parent.ID
            WHERE c.ID = ? AND c.DeleteDatetime IS NULL
            """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                JsonObject committeeDetail = new JsonObject();
                committeeDetail.addProperty("ID", rs.getInt("ID"));
                committeeDetail.addProperty("Ref", rs.getString("Ref"));
                committeeDetail.addProperty("Name", rs.getString("Name"));
                committeeDetail.addProperty("Description", rs.getString("Description"));
                committeeDetail.addProperty("Status", rs.getString("Status"));
                committeeDetail.addProperty("Lifecycle", rs.getString("Lifecycle"));
                committeeDetail.addProperty("Classification", rs.getString("Classification"));
                committeeDetail.addProperty("Committee_Type", rs.getString("Committee_Type"));
                committeeDetail.addProperty("Viewing", rs.getString("Viewing"));
                committeeDetail.addProperty("Created", rs.getTimestamp("Created") != null ?
                        rs.getTimestamp("Created").toString() : null);
                committeeDetail.addProperty("Last_Updated", rs.getTimestamp("Last_Updated") != null ?
                        rs.getTimestamp("Last_Updated").toString() : null);
                committeeDetail.addProperty("Last_Updated_By", rs.getString("Last_Updated_By"));
                committeeDetail.addProperty("Parent_ID", rs.getObject("Parent_ID", Integer.class));
                committeeDetail.addProperty("Parent_Name", rs.getString("Parent_Name"));
                committeeDetail.addProperty("Created_By_ID", rs.getObject("Created_By_ID", Integer.class));
                committeeDetail.addProperty("Created_By_Name", rs.getString("Created_By_Name"));
                committeeDetail.addProperty("createdById", rs.getObject("Created_By_ID", Integer.class));
                committeeDetail.addProperty("createdByName", rs.getString("Created_By_Name"));

                return committeeDetail;
            }
        }
        return null;
    }

    private List<JsonObject> getAllCommitteeDetails(Connection conn) throws SQLException {
        String query = """
            SELECT 
                c.ID,
                c.RefNumber AS Ref,
                c.PrimaryName AS Name,
                c.Description,
                s.primaryname AS Status,
                cl.PrimaryName AS Lifecycle,
                cc.PrimaryName AS Classification,
                ct.PrimaryName AS Committee_Type,
                v.Name AS Viewing,
                c.CreateDatetime AS Created,
                c.LastUpdateDatetime AS Last_Updated,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS Last_Updated_By,
                c.Parent_ID,
                parent.PrimaryName AS Parent_Name,
                p_created.ID AS Created_By_ID,
                CONCAT(p_created.First_Name, ' ', p_created.Last_Name) AS Created_By_Name
            FROM committee c
            LEFT JOIN status s ON c.Status = s.ID
            LEFT JOIN committee_lifecycle cl ON c.Lifecycle = cl.ID
            LEFT JOIN committee_classification cc ON c.Classification = cc.ID
            LEFT JOIN committee_type ct ON c.Committee_Type = ct.ID
            LEFT JOIN viewing v ON c.Is_Public = v.ID
            LEFT JOIN people p ON c.LastUpdate_UserID = p.ID
            LEFT JOIN people p_created ON c.Created_By = p_created.ID
            LEFT JOIN committee parent ON c.Parent_ID = parent.ID
            WHERE c.DeleteDatetime IS NULL
            ORDER BY c.ID
            """;

        List<JsonObject> committeeDetails = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                JsonObject committeeDetail = new JsonObject();
                committeeDetail.addProperty("ID", rs.getInt("ID"));
                committeeDetail.addProperty("Ref", rs.getString("Ref"));
                committeeDetail.addProperty("Name", rs.getString("Name"));
                committeeDetail.addProperty("Description", rs.getString("Description"));
                committeeDetail.addProperty("Status", rs.getString("Status"));
                committeeDetail.addProperty("Lifecycle", rs.getString("Lifecycle"));
                committeeDetail.addProperty("Classification", rs.getString("Classification"));
                committeeDetail.addProperty("Committee_Type", rs.getString("Committee_Type"));
                committeeDetail.addProperty("Viewing", rs.getString("Viewing"));
                committeeDetail.addProperty("Created", rs.getTimestamp("Created") != null ?
                        rs.getTimestamp("Created").toString() : null);
                committeeDetail.addProperty("Last_Updated", rs.getTimestamp("Last_Updated") != null ?
                        rs.getTimestamp("Last_Updated").toString() : null);
                committeeDetail.addProperty("Last_Updated_By", rs.getString("Last_Updated_By"));
                committeeDetail.addProperty("Parent_ID", rs.getObject("Parent_ID", Integer.class));
                committeeDetail.addProperty("Parent_Name", rs.getString("Parent_Name"));
                committeeDetail.addProperty("Created_By_ID", rs.getObject("Created_By_ID", Integer.class));
                committeeDetail.addProperty("Created_By_Name", rs.getString("Created_By_Name"));
                committeeDetail.addProperty("createdById", rs.getObject("Created_By_ID", Integer.class));
                committeeDetail.addProperty("createdByName", rs.getString("Created_By_Name"));

                committeeDetails.add(committeeDetail);
            }
        }
        return committeeDetails;
    }
}
