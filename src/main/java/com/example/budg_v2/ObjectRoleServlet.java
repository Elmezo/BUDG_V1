package com.example.budg_v2;

import com.example.budg_v2.dao.ObjectRoleDAO;
import com.example.budg_v2.model.ObjectRole;
import com.example.budg_v2.model.ObjectRoleType;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet("/api/object-roles")
public class ObjectRoleServlet extends HttpServlet {
    private ObjectRoleDAO objectRoleDAO;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        objectRoleDAO = new ObjectRoleDAO();
        gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            String action = request.getParameter("action");
            
            if (action != null && action.equals("getByFacet")) {
                // جلب الأدوار حسب Facet ID
                String facetIdParam = request.getParameter("facetId");
                if (facetIdParam == null || facetIdParam.trim().isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject errorObj = new JsonObject();
                    errorObj.addProperty("success", false);
                    errorObj.addProperty("error", "Facet ID is required");
                    response.getWriter().write(gson.toJson(errorObj));
                    return;
                }
                
                try {
                    int facetId = Integer.parseInt(facetIdParam);
                    //system.out.println("ObjectRoleServlet: Fetching roles for facet ID: " + facetId);
                    
                    List<ObjectRole> roles = objectRoleDAO.getObjectRolesByModuleId(facetId);
                    //system.out.println("ObjectRoleServlet: Found " + roles.size() + " roles for facet ID: " + facetId);
                    
                    for (ObjectRole role : roles) {
                        java.util.Objects.requireNonNull(role);
                        //system.out.println("ObjectRoleServlet: Role - ID: " + role.getId() +
                                     //    ", Name: " + role.getPrimaryname() +
                                   //      ", Module: " + role.getModule());
                    }
                    
                    JsonObject responseObj = new JsonObject();
                    responseObj.addProperty("success", true);
                    responseObj.add("data", gson.toJsonTree(roles));
                    responseObj.addProperty("count", roles.size());
                    
                    //system.out.println("ObjectRoleServlet: Response JSON: " + gson.toJson(responseObj));
                    response.getWriter().write(gson.toJson(responseObj));
                    
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject errorObj = new JsonObject();
                    errorObj.addProperty("success", false);
                    errorObj.addProperty("error", "Invalid facet ID format");
                    response.getWriter().write(gson.toJson(errorObj));
                }
                
            } else {
                // جلب جميع الأدوار (السلوك الافتراضي)
                List<ObjectRole> roles = objectRoleDAO.getAllObjectRoles();
                
                // جلب جميع أنواع الأدوار
                List<ObjectRoleType> roleTypes = objectRoleDAO.getAllObjectRoleTypes();
                
                // جلب جميع أسماء modules
                List<String> moduleNames = objectRoleDAO.getAllModuleNames();
                
                // إنشاء response object
                JsonObject responseObj = new JsonObject();
                responseObj.add("roles", gson.toJsonTree(roles));
                responseObj.add("roleTypes", gson.toJsonTree(roleTypes));
                responseObj.add("moduleNames", gson.toJsonTree(moduleNames));
                responseObj.addProperty("success", true);
                
                response.getWriter().write(gson.toJson(responseObj));
            }
            
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject errorObj = new JsonObject();
            errorObj.addProperty("success", false);
            errorObj.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(errorObj));
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            // قراءة البيانات المرسلة
            StringBuilder sb = new StringBuilder();
            String line;
            try (java.io.BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            
            String jsonData = sb.toString();
            JsonObject requestData = gson.fromJson(jsonData, JsonObject.class);
            
            if (requestData.has("changes")) {
                // معالجة التغييرات
                JsonObject responseObj = new JsonObject();
                try {
                    processChanges(requestData.getAsJsonArray("changes"));
                    responseObj.addProperty("success", true);
                    responseObj.addProperty("message", "Changes processed successfully");
                } catch (Exception ex) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    responseObj.addProperty("success", false);
                    responseObj.addProperty("error", ex.getMessage());
                }
                response.getWriter().write(gson.toJson(responseObj));
            } else {
                throw new IllegalArgumentException("No changes data provided");
            }
            
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject errorObj = new JsonObject();
            errorObj.addProperty("success", false);
            errorObj.addProperty("error", e.getMessage());
            response.getWriter().write(gson.toJson(errorObj));
        }
    }
    
    private void processChanges(com.google.gson.JsonArray changes) throws Exception {
        //system.out.println("Processing " + changes.size() + " changes...");
        for (int i = 0; i < changes.size(); i++) {
            com.google.gson.JsonObject change = changes.get(i).getAsJsonObject();
            String type = change.get("type").getAsString();
            com.google.gson.JsonObject data = change.get("data").getAsJsonObject();
            
            //system.out.println("Processing change " + (i + 1) + ": " + type);
            //system.out.println("Data: " + data.toString());
            
            if ("create".equals(type)) {
                // تحقق من الحقول المطلوبة
                if (!data.has("primaryName") || !data.has("facet") || !data.has("roleType")) {
                    throw new IllegalArgumentException("Missing required fields for create: primaryName, facet, roleType");
                }
                
                // إنشاء دور جديد
                ObjectRole newRole = new ObjectRole();
                newRole.setPrimaryname(data.get("primaryName").getAsString());
                // الوصف اختياري
                String description = data.has("description") && !data.get("description").isJsonNull()
                    ? data.get("description").getAsString()
                    : null;
                newRole.setDescription(description);
                // default اختياري
                Boolean defaultVal = null;
                if (data.has("default") && !data.get("default").isJsonNull()) {
                    try {
                        defaultVal = data.get("default").getAsBoolean();
                    } catch (Exception ex) {
                        defaultVal = null;
                    }
                }
                newRole.setDefaultrole(defaultVal);
                
                // تحديد نوع الدور
                String roleTypeName = data.get("roleType").getAsString();
                List<ObjectRoleType> roleTypes = objectRoleDAO.getAllObjectRoleTypes();
                ObjectRoleType selectedRoleType = roleTypes.stream()
                    .filter(rt -> rt.getPrimaryname().equals(roleTypeName))
                    .findFirst()
                    .orElse(null);
                
                // تحديد module (facet)
                String moduleName = data.get("facet").getAsString();
                Integer moduleId = objectRoleDAO.getModuleIdByName(moduleName);
                
                if (selectedRoleType == null) {
                    throw new IllegalArgumentException("Invalid roleType: '" + roleTypeName + "' not found");
                }
                if (moduleId == null) {
                    throw new IllegalArgumentException("Invalid facet: module '" + moduleName + "' not found");
                }
                
                newRole.setObjectroletypeId(selectedRoleType.getId());
                newRole.setModule(moduleId);
                
                boolean created = objectRoleDAO.createObjectRole(newRole);
                if (!created) {
                    throw new RuntimeException("Insert failed: no rows affected");
                }
                
            } else if ("update".equals(type)) {
                // تحديث دور موجود
                int id;
                try {
                    // محاولة تحويل string إلى int
                    id = Integer.parseInt(data.get("id").getAsString());
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing role ID: " + data.get("id").getAsString());
                    continue; // تخطي هذا التحديث
                }
                ObjectRole existingRole = objectRoleDAO.getObjectRoleById(id);
                if (existingRole != null) {
                    existingRole.setPrimaryname(data.get("primaryName").getAsString());
                    existingRole.setDescription(data.get("description").getAsString());
                    existingRole.setDefaultrole(data.get("default").getAsBoolean());
                    
                    // تحديد نوع الدور
                    String roleTypeName = data.get("roleType").getAsString();
                    List<ObjectRoleType> roleTypes = objectRoleDAO.getAllObjectRoleTypes();
                    ObjectRoleType selectedRoleType = roleTypes.stream()
                        .filter(rt -> rt.getPrimaryname().equals(roleTypeName))
                        .findFirst()
                        .orElse(null);
                    
                    // تحديد module (facet)
                    String moduleName = data.get("facet").getAsString();
                    Integer moduleId = objectRoleDAO.getModuleIdByName(moduleName);
                    
                    // السماح بتحديث default حتى لو فشل تحديد roleType/module
                    Integer finalRoleTypeId = (selectedRoleType != null)
                        ? selectedRoleType.getId()
                        : existingRole.getObjectroletypeId();
                    if (selectedRoleType == null) {
                        System.err.println("WARN: RoleType not resolved from name. Preserving existing value: " + finalRoleTypeId);
                    }
                    Integer finalModuleId = (moduleId != null)
                        ? moduleId
                        : existingRole.getModule();
                    if (moduleId == null) {
                        System.err.println("WARN: ModuleId not resolved from name. Preserving existing value: " + finalModuleId);
                    }

                    existingRole.setObjectroletypeId(finalRoleTypeId);
                    existingRole.setModule(finalModuleId);

                    //system.out.println("Updating role: " + existingRole.toString());
                    boolean updateSuccess = objectRoleDAO.updateObjectRole(existingRole);
                    //system.out.println("Update success: " + updateSuccess);
                    
                    if (!updateSuccess) {
                        throw new RuntimeException("Update failed for role ID: " + id);
                    }
                }
            }
        }
        //system.out.println("All changes processed successfully");
    }
}
