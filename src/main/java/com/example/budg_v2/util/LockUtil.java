package com.example.budg_v2.util;

import com.example.budg_v2.dao.LockDAO;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * Utility class for checking locks before allowing object updates
 */
public class LockUtil {
    
    private static final LockDAO lockDAO = new LockDAO();
    
    /**
     * Check if an object is locked and return appropriate response if locked
     * @param request HTTP request
     * @param response HTTP response
     * @param facetType The facet type (e.g., "dataset", "system", "policy")
     * @param objectId The object ID
     * @return true if object can be edited, false if locked (response already sent)
     * @throws IOException if response writing fails
     */
    public static boolean checkLockBeforeEdit(HttpServletRequest request, HttpServletResponse response, 
                                             String facetType, int objectId) throws IOException {
        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            boolean isSuperAdmin = isSuperAdmin(request);
            
            // Get module ID
            int moduleId = ModuleResolver.getModuleId(facetType);
            Map<String, Object> lockInfo = lockDAO.checkLock(moduleId, objectId, userId);
            String lockStatus = (String) lockInfo.get("status");
            
            if (!"no_lock".equals(lockStatus) && !"locked_by_self".equals(lockStatus)) {
                // Object is locked by another user or permanently locked
                boolean isPermanent = (Boolean) lockInfo.getOrDefault("isPermanent", false);
                String lockedBy = (String) lockInfo.getOrDefault("lockedByName", "another user");
                
                // Super admins can edit permanently locked objects
                if (isPermanent && !isSuperAdmin) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonObject resp = new JsonObject();
                    resp.addProperty("success", false);
                    resp.addProperty("locked", true);
                    resp.addProperty("permanentlyLocked", true);
                    resp.addProperty("message", "This object has a permanent lock. Only administrators can edit or unlock it.");
                    resp.addProperty("lockedBy", lockedBy);
                    response.getWriter().write(resp.toString());
                    return false;
                } else if (!isPermanent) {
                    // Temporary lock by another user
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    JsonObject resp = new JsonObject();
                    resp.addProperty("success", false);
                    resp.addProperty("locked", true);
                    resp.addProperty("message", "This object is being edited by " + lockedBy + " and is temporarily locked. Please try again later.");
                    resp.addProperty("lockedBy", lockedBy);
                    resp.addProperty("isPermanent", false);
                    response.getWriter().write(resp.toString());
                    return false;
                }
            }
            
            return true; // Object can be edited
        } catch (SQLException e) {
            // Log error but allow edit to continue
            System.err.println("Error checking lock: " + e.getMessage());
            return true;
        } catch (IllegalArgumentException e) {
            // Unknown facet type - allow edit to continue
            System.err.println("Unknown facet type for lock check: " + facetType);
            return true;
        }
    }
    
    private static boolean isSuperAdmin(HttpServletRequest request) {
        Object roleObj = request.getAttribute("userRole");
        if (roleObj == null) return false;
        return AppRoleNames.isSuperAdminName(roleObj.toString());
    }
}

