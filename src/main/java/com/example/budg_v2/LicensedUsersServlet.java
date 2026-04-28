package com.example.budg_v2;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.example.budg_v2.database.DatabaseConnection;

@WebServlet("/api/licensed-users")
public class LicensedUsersServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            // الاتصال بقاعدة البيانات باستخدام DatabaseConnection
            conn = DatabaseConnection.getConnection();

            // Super Admin, Admin, or Web User with at least one Edit permission (direct ipid or via role_assignment)
            String editPermSub = "(SELECT id FROM permission_names WHERE LOWER(TRIM(Name)) = 'edit' LIMIT 1)";
            String sql = "SELECT p.First_Name, p.Last_Name, r.primaryname AS User_Type " +
                    "FROM people p " +
                    "LEFT JOIN role r ON p.System_Role = r.id " +
                    "LEFT JOIN status s ON p.status_id = s.ID " +
                    "WHERE p.Deleted_date IS NULL " +
                    "AND (s.primaryname IS NULL OR (LOWER(s.primaryname) != 'inactive' AND LOWER(s.primaryname) != 'deleted')) " +
                    "AND ( " +
                    "  LOWER(REPLACE(REPLACE(REPLACE(COALESCE(r.primaryname, ''), ' ', ''), '_', ''), '-', '')) IN ('superadmin', 'suberadmin', 'admin') " +
                    "  OR ( " +
                    "    LOWER(REPLACE(REPLACE(REPLACE(COALESCE(r.primaryname, ''), ' ', ''), '_', ''), '-', '')) = 'webuser' " +
                    "    AND ( " +
                    "      EXISTS ( " +
                    "        SELECT 1 FROM permissions perm_e " +
                    "        WHERE perm_e.ipid = p.ID " +
                    "        AND ( " +
                    "          perm_e.permission = " + editPermSub + " " +
                    "          OR (perm_e.permissions_json IS NOT NULL " +
                    "            AND JSON_CONTAINS(perm_e.permissions_json, CAST(" + editPermSub + " AS CHAR), '$')) " +
                    "        ) " +
                    "      ) " +
                    "      OR EXISTS ( " +
                    "        SELECT 1 FROM role_assignment ra " +
                    "        JOIN permissions perm_e ON perm_e.Object_Role_ID = ra.objectroleid " +
                    "        WHERE ( " +
                    "          JSON_CONTAINS(ra.users, CAST(p.ID AS CHAR), '$') " +
                    "          OR JSON_CONTAINS(ra.users, CONCAT('\"', p.ID, '\"'), '$') " +
                    "        ) " +
                    "        AND ( " +
                    "          perm_e.permission = " + editPermSub + " " +
                    "          OR (perm_e.permissions_json IS NOT NULL " +
                    "            AND JSON_CONTAINS(perm_e.permissions_json, CAST(" + editPermSub + " AS CHAR), '$')) " +
                    "        ) " +
                    "      ) " +
                    "    ) " +
                    "  ) " +
                    ") " +
                    "ORDER BY p.ID";

            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            // إنشاء JSON Array
            JsonArray usersArray = new JsonArray();

            while (rs.next()) {
                JsonObject user = new JsonObject();
                user.addProperty("firstName", rs.getString("First_Name"));
                user.addProperty("lastName", rs.getString("Last_Name"));
                user.addProperty("userType", rs.getString("User_Type"));
                usersArray.add(user);
            }

            // إنشاء JSON Response النهائي
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("success", true);
            jsonResponse.add("data", usersArray);
            jsonResponse.addProperty("count", usersArray.size());

            // إرسال الاستجابة
            Gson gson = new Gson();
            out.print(gson.toJson(jsonResponse));

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("message", "Database error: " + e.getMessage());
            out.print(new Gson().toJson(error));
            e.printStackTrace();

        } finally {
            // إغلاق الموارد
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseConnection.closeConnection(conn);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }
}