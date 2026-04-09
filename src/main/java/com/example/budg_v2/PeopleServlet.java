package com.example.budg_v2;

import com.example.budg_v2.service.PeopleService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "PeopleServlet", urlPatterns = {"/api/people", "/api/people/*", "/api/view/people", "/api/view/people/*", "/api/create/people", "/api/create/people/*"})
public class PeopleServlet extends HttpServlet {

    private final PeopleService peopleService = new PeopleService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();
            if (path == null || path.equals("/")) sendJson(response, peopleService.getAllPeople());
            else if (path.equals("/org-units")) sendJson(response, peopleService.getOrgUnitsForPeople());
            else if (path.equals("/by-org-unit")) {
                String idStr = request.getParameter("id");
                if (idStr == null || idStr.isBlank()) { sendError(response, "Missing org unit id", 400); return; }
                int id = Integer.parseInt(idStr);
                sendJson(response, peopleService.getPeopleByOrgUnitId(id));
            }
            else if (path.matches("/\\d+")) {
                Map<String, Object> person = peopleService.getPersonById(Integer.parseInt(path.substring(1)));
                if (person == null) {
                    sendError(response, "Person not found", 404);
                    return;
                }
                String statusName = person.get("status_name") != null ? String.valueOf(person.get("status_name")) : null;
                if (!UserContextUtil.isCurrentUserAdmin(request) && "Deleted".equalsIgnoreCase(statusName)) {
                    sendError(response, "This object is not available.", 403);
                    return;
                }
                sendJson(response, person);
            }
            else if (path.equals("/search")) {
                String q = request.getParameter("q");
                if (q != null && !q.trim().isEmpty()) sendJson(response, peopleService.searchPeople(q.trim()));
                else sendError(response, "Search query parameter 'q' is required", 400);
            } else if (path.equals("/search-firstname")) {
                String firstName = request.getParameter("firstName");
                if (firstName != null && !firstName.trim().isEmpty()) sendJson(response, peopleService.searchPeopleByFirstName(firstName.trim()));
                else sendError(response, "First name parameter 'firstName' is required", 400);
            } else sendError(response, "Invalid endpoint", 404);
        } catch (NumberFormatException e) {
            sendError(response, "Invalid ID format", 400);
        } catch (Exception e) {
            sendError(response, e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();

            // POST /api/people/{personId}/roles/{objectXPeopleId}/accept
            // POST /api/people/{personId}/roles/{objectXPeopleId}/undo-accept
            if (path != null && path.matches("^/\\d+/roles/\\d+/(accept|undo-accept)$")) {
                String[] parts = path.split("/");
                int personId = Integer.parseInt(parts[1]);
                int objectXPeopleId = Integer.parseInt(parts[3]);
                String action = parts[4];

                int userId = UserContextUtil.getCurrentUserId(request);
                int acceptedId = action.equals("accept") ? 1 : 2; // roleaccepted: 1=Yes, 2=No

                try (var conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                     var ps = conn.prepareStatement("""
                         UPDATE object_x_people
                         SET AcceptedID = ?, lastupdatedatetime = NOW(), lastupdateuser_id = ?
                         WHERE id = ? AND ipid = ?
                         """)) {
                    ps.setInt(1, acceptedId);
                    ps.setInt(2, userId);
                    ps.setInt(3, objectXPeopleId);
                    ps.setInt(4, personId);

                    int updated = ps.executeUpdate();
                    if (updated <= 0) {
                        sendError(response, "Role assignment not found for this person", 404);
                        return;
                    }
                }

                sendSuccess(response, action.equals("accept") ? "Role accepted" : "Role acceptance undone");
                return;
            }

            if (path == null || path.equals("/")) {
                // Check create permission for new person creation
                if (!PermissionCheckUtil.checkCreatePermission(request, response, "People")) {
                    return; // Response already sent
                }
                
                JsonObject data = parsePersonFromRequest(request);
                // Mandatory: Email only
                String email = com.example.budg_v2.util.JsonUtil.getJsonString(data, "email");
                if (email == null || email.trim().isEmpty()) { sendError(response, "Email is required", 400); return; }
                try (var conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                     var ps = conn.prepareStatement("SELECT 1 FROM people WHERE LOWER(Email)=LOWER(?) LIMIT 1")) {
                    ps.setString(1, email.trim());
                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) { sendError(response, "Email already exists", 400); return; }
                    }
                } catch (Exception ignore) { /* rely on DB unique if exists */ }
                int userId = UserContextUtil.getCurrentUserId(request);
                sendJson(response, peopleService.createPerson(toMap(data), userId));
            } else sendError(response, "Invalid endpoint", 404);
        } catch (Exception e) {
            sendError(response, e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        
        // Check role-based edit permission first
        if (!PermissionCheckUtil.checkEditPermission(request, response, "People")) {
            return; // Response already sent
        }
        
        try {
            String path = request.getPathInfo();
            if (path != null && path.matches("/\\d+")) {
                int id = Integer.parseInt(path.substring(1));
                int userId = UserContextUtil.getCurrentUserId(request);
                
                // Check if user is Super Admin
                boolean isSuperAdmin = false;
                try {
                    isSuperAdmin = SegmentAccessService.isSuperAdmin(userId);
                } catch (Exception e) {
                    // Log error but continue - will default to restricting access
                    System.err.println("Error checking Super Admin status: " + e.getMessage());
                }
                
                // Super Admins can edit any profile, but Admins and Web Users can only edit their own
                if (!isSuperAdmin && id != userId) {
                    sendError(response, "You can only edit your own profile", 403);
                    return;
                }
                
                JsonObject data = parsePersonFromRequest(request);
                Map<String, Object> map = toMap(data);
                map.put("id", id);
                
                //system.out.println("=== PEOPLE SERVLET UPDATE CALLED ===");
                //system.out.println("Updating person ID: " + id + " with data: " + map);
                //system.out.println("User ID: " + userId);
                
                //system.out.println("=== SERVLET DEBUG: About to call peopleService.updatePerson ===");
                //system.out.println("Map being sent to service: " + map);
                //system.out.println("User ID: " + userId);
                
                try {
                    Map<String, Object> updateResult = peopleService.updatePerson(map, userId);
                    //system.out.println("=== SERVLET DEBUG: PeopleService.updatePerson returned ===");
                    //system.out.println("Result: " + updateResult);
                    //system.out.println("Result is null: " + (updateResult == null));
                    
                    if (updateResult != null) {
                        //system.out.println("Person updated successfully - sending JSON response");
                        sendJson(response, updateResult);
                    } else {
                        //system.out.println("Person update failed - sending error response");
                        sendError(response, "Person not found or update failed", 404);
                    }
                } catch (Exception e) {
                    //system.out.println("=== SERVLET ERROR: Exception in updatePerson ===");
                    //system.out.println("Error: " + e.getMessage());
                    e.printStackTrace();
                    sendError(response, "Update failed: " + e.getMessage(), 500);
                }
            } else sendError(response, "Invalid endpoint", 404);
        } catch (NumberFormatException e) {
            sendError(response, "Invalid ID format", 400);
        } catch (Exception e) {
            System.err.println("Error updating person: " + e.getMessage());
            e.printStackTrace();
            sendError(response, e.getMessage(), 500);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setupResponse(response);
        try {
            String path = request.getPathInfo();
            if (path != null && path.matches("/\\d+")) {
                int id = Integer.parseInt(path.substring(1));
                if (peopleService.deletePerson(id)) sendSuccess(response, "Person deleted successfully");
                else sendError(response, "Person not found or deletion failed", 404);
            } else sendError(response, "Invalid endpoint", 404);
        } catch (NumberFormatException e) {
            sendError(response, "Invalid ID format", 400);
        } catch (java.sql.SQLException e) {
            // Return 400 for CR validation errors, 500 for other DB errors
            if (e.getMessage() != null && e.getMessage().contains("Cannot delete person")) {
                sendError(response, e.getMessage(), 400);
            } else {
                sendError(response, e.getMessage(), 500);
            }
        } catch (Exception e) {
            sendError(response, e.getMessage(), 500);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void setupResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
    }

    private JsonObject parsePersonFromRequest(HttpServletRequest request) throws IOException {
        JsonObject data = JsonUtil.parseJsonFromRequest(request.getReader());
        return data;
    }

    private Map<String, Object> toMap(JsonObject json) {
        Map<String, Object> map = new HashMap<>();
        // Basic people table fields
        map.put("first_name", JsonUtil.getJsonString(json, "first_name") != null ? JsonUtil.getJsonString(json, "first_name") : "");
        map.put("last_name", JsonUtil.getJsonString(json, "last_name") != null ? JsonUtil.getJsonString(json, "last_name") : "");
        map.put("email", JsonUtil.getJsonString(json, "email"));
        map.put("password", JsonUtil.getJsonString(json, "password") != null ? JsonUtil.getJsonString(json, "password") : "");
        map.put("description", JsonUtil.getJsonString(json, "description"));
        map.put("function_name", JsonUtil.getJsonString(json, "function_name"));
        map.put("function_description", JsonUtil.getJsonString(json, "function_description"));
        map.put("status_id", JsonUtil.getJsonInt(json, "status_id") != null ? JsonUtil.getJsonInt(json, "status_id") : 1);
        map.put("org_unit_id", JsonUtil.getJsonInt(json, "org_unit_id"));
        map.put("system_role", JsonUtil.getJsonInt(json, "system_role"));
        map.put("source_id", JsonUtil.getJsonInt(json, "source_id"));
        map.put("profile_ImageID", JsonUtil.getJsonInt(json, "profile_ImageID"));
        
        // People_details table fields
        map.put("lifecycle_id", JsonUtil.getJsonInt(json, "lifecycle_id"));
        map.put("employment_type_id", JsonUtil.getJsonInt(json, "employment_type_id"));
        map.put("company_name", JsonUtil.getJsonString(json, "company_name"));
        map.put("office_location", JsonUtil.getJsonString(json, "office_location"));
        map.put("internal_mail_code", JsonUtil.getJsonString(json, "internal_mail_code"));
        map.put("office_phone", JsonUtil.getJsonString(json, "office_phone"));
        map.put("mobile_phone", JsonUtil.getJsonString(json, "mobile_phone"));
        map.put("linkedin_url", JsonUtil.getJsonString(json, "linkedin_url"));
        map.put("twitter", JsonUtil.getJsonString(json, "twitter"));
        map.put("lan_id", JsonUtil.getJsonString(json, "lan_id"));
        map.put("employed_since", JsonUtil.getJsonString(json, "employed_since"));
        
        return map;
    }

    private void sendJson(HttpServletResponse response, Object data) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        obj.add("data", JsonParser.parseString(JsonUtil.toJson(data)));
        response.getWriter().write(obj.toString());
    }

    private void sendError(HttpServletResponse response, String message, int status) throws IOException {
        JsonUtil.sendErrorResponse(response.getWriter(), message, status);
    }

    private void sendSuccess(HttpServletResponse response, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        obj.addProperty("message", message);
        response.getWriter().write(obj.toString());
    }
}
