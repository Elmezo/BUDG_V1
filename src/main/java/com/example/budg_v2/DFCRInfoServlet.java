package com.example.budg_v2;

import com.example.budg_v2.model.DFCR;
import com.example.budg_v2.service.DFCRService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Servlet to get DF_CR locked fields info for frontend
 * Endpoint: /api/dfcr/info
 * 
 * Query params:
 * - facet: The facet name (Glossary, Data Set, Process, System)
 * 
 * Returns locked fields info for the facet, considering user's admin status
 */
@WebServlet("/api/dfcr/info")
public class DFCRInfoServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DFCRInfoServlet.class);
    private final Gson gson = new Gson();
    private final DFCRService dfcrService = new DFCRService();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String facet = req.getParameter("facet");
        String objectIdParam = req.getParameter("objectId");

        if (facet == null || facet.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Missing facet parameter");
            resp.getWriter().write(gson.toJson(error));
            return;
        }

        // Parse objectId if provided
        Integer objectId = null;
        if (objectIdParam != null && !objectIdParam.trim().isEmpty()) {
            try {
                objectId = Integer.parseInt(objectIdParam);
            } catch (NumberFormatException e) {
                logger.warn("[DFCR Info] Invalid objectId parameter: {}", objectIdParam);
            }
        }

        try {
            // Log all user attributes for debugging
            Object userIdAttr = req.getAttribute("userId");
            Object userEmail = req.getAttribute("userEmail");
            Object roleObj = req.getAttribute("userRole");
            logger.info("[DFCR Info] Request attributes - userId: {}, userEmail: {}, userRole: {}, objectId: {}", 
                userIdAttr, userEmail, roleObj, objectId);
            
            // Check if current user is admin
            boolean isAdmin = UserContextUtil.isCurrentUserAdmin(req);
            
            logger.info("[DFCR Info] Facet: {}, User Role: '{}', isAdmin: {}, objectId: {}", facet, roleObj, isAdmin, objectId);
            
            // Get the raw DFCR settings to log
            DFCR rawDfcr = dfcrService.getSettingsForFacet(facet);
            if (rawDfcr != null) {
                logger.info("[DFCR Info] Raw DB values - workflowApprovalEnabled: {}, adminWorkflowBypass: {}, workflowCreateId: {}, workflowEditId: {}", 
                    rawDfcr.isWorkflowApprovalEnabled(), rawDfcr.isAdminWorkflowBypass(), 
                    rawDfcr.getWorkflowCreateId(), rawDfcr.getWorkflowEditId());
            } else {
                logger.info("[DFCR Info] No DFCR record found for facet: {}", facet);
            }
            
            // Get locked fields info (pass userId and objectId if provided)
            int userId = UserContextUtil.getCurrentUserId(req);
            DFCRService.LockedFieldsInfo info = dfcrService.getLockedFieldsInfo(facet, isAdmin, userId, objectId);
            
            // Check if user is super admin
            boolean isSuperAdmin = false;
            try {
                isSuperAdmin = com.example.budg_v2.service.SegmentAccessService.isSuperAdmin(userId);
            } catch (java.sql.SQLException e) {
                logger.warn("[DFCR Info] Error checking super admin status for user {}: {}", userId, e.getMessage());
            }
            
            logger.info("[DFCR Info] Response - workflowEnabled: {}, statusLocked: {}, lifecycleLocked: {}, isSuperAdmin: {}", 
                info.isWorkflowEnabled(), info.isStatusLocked(), info.isLifecycleLocked(), isSuperAdmin);
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("facet", facet);
            response.addProperty("isAdmin", isAdmin);
            response.addProperty("isSuperAdmin", isSuperAdmin);
            response.addProperty("userRole", roleObj != null ? roleObj.toString() : "null");
            response.addProperty("adminBypassEnabled", rawDfcr != null ? rawDfcr.isAdminWorkflowBypass() : false);
            response.addProperty("workflowEnabled", info.isWorkflowEnabled());
            response.addProperty("statusLocked", info.isStatusLocked());
            response.addProperty("defaultStatusId", info.getDefaultStatusId());
            response.addProperty("lifecycleLocked", info.isLifecycleLocked());
            response.addProperty("defaultLifecycleId", info.getDefaultLifecycleId());
            
            // Add edit workflow info for existing objects
            response.addProperty("editWorkflowEnabled", rawDfcr != null && rawDfcr.getWorkflowEditId() != null);
            response.addProperty("editWorkflowId", rawDfcr != null && rawDfcr.getWorkflowEditId() != null ? rawDfcr.getWorkflowEditId() : 0);
            response.addProperty("createWorkflowEnabled", rawDfcr != null && rawDfcr.getWorkflowCreateId() != null);
            
            // Add default CR values for edit workflow
            response.addProperty("defaultCrTypeId", rawDfcr != null && rawDfcr.getCrTypeId() != null ? rawDfcr.getCrTypeId() : 0);
            response.addProperty("defaultCrUrgencyId", rawDfcr != null && rawDfcr.getCrUrgencyId() != null ? rawDfcr.getCrUrgencyId() : 0);
            response.addProperty("defaultCrSeverityId", rawDfcr != null && rawDfcr.getCrSeverityId() != null ? rawDfcr.getCrSeverityId() : 0);
            
            logger.info("[DFCR Info] FULL RESPONSE: {}", gson.toJson(response));
            
            resp.getWriter().write(gson.toJson(response));
            
        } catch (Exception e) {
            logger.error("Error getting DF_CR info for facet: {}", facet, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Error getting DF_CR info: " + e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
}

