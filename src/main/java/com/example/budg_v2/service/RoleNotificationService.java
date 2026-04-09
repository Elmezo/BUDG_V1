package com.example.budg_v2.service;

import com.example.budg_v2.dao.SystemSettingsDAO;
import com.example.budg_v2.dao.WorkflowNotificationDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.SystemSettings;
import com.example.budg_v2.model.WorkflowNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Service for creating role assignment notifications
 * Sends notifications (UI and Email) when stakeholders are assigned to objects
 */
public class RoleNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(RoleNotificationService.class);
    private final WorkflowNotificationDAO notificationDAO;
    private final EmailService emailService;
    private final SystemSettingsDAO systemSettingsDAO;

    public RoleNotificationService() {
        this.notificationDAO = new WorkflowNotificationDAO();
        this.emailService = new EmailService();
        this.systemSettingsDAO = new SystemSettingsDAO();
    }

    /**
     * Create a role notification for a stakeholder assignment
     * 
     * @param facetType Facet type (e.g., "Data Set", "System", "Glossary")
     * @param objectId ID of the object
     * @param objectName Name of the object
     * @param roleId ID of the role
     * @param userId ID of the user (stakeholder) being assigned
     * @param objectXPeopleId ID of the object_x_people record (for accept link)
     * @return Notification ID if created successfully, null otherwise
     */
    public Long createRoleNotification(String facetType, int objectId, String objectName, 
                                      int roleId, int userId, Integer objectXPeopleId) {
        try {
            // Get role name
            String roleName = getRoleName(roleId);
            if (roleName == null) {
                logger.warn("Could not find role name for roleId: {}", roleId);
                roleName = "Unknown Role";
            }

            // Get facet name (normalize from facet type)
            String facetName = getFacetName(facetType);
            if (facetName == null) {
                facetName = facetType; // Use provided facet type as fallback
            }

            // Create UI notification
            WorkflowNotification notification = new WorkflowNotification();
            notification.setRecipientUserId(userId);
            notification.setEventType("ROLE_ASSIGNED");
            notification.setTitle(roleName);
            notification.setMessage(facetName + ": " + objectName);
            notification.setChannel("ui");
            notification.setCategory("roles");
            notification.setObjectId(objectId);
            notification.setFacetType(facetType);
            notification.setRead(false);

            Long notificationId = notificationDAO.create(notification);

            // Send email notification
            sendRoleAssignmentEmail(userId, roleName, facetName, objectName, 
                                  facetType, objectId, objectXPeopleId, notificationId);

            logger.info("Role notification created for user {} with role {} on {} {}", 
                       userId, roleName, facetName, objectName);
            
            return notificationId;

        } catch (SQLException e) {
            logger.error("Error creating role notification for user {} on object {} {}", 
                        userId, facetType, objectId, e);
            return null;
        }
    }

    /**
     * Get role name from object_role table
     */
    private String getRoleName(int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        }
        return null;
    }

    /**
     * Get facet name from module table based on facet type
     * Maps facet types like "Data Set" to module primaryname
     */
    private String getFacetName(String facetType) throws SQLException {
        // Normalize facet type to match module names
        String normalizedType = normalizeFacetType(facetType);
        
        String sql = "SELECT primaryname FROM module WHERE primaryname = ? OR primaryname LIKE ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedType);
            ps.setString(2, normalizedType + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        }
        return null;
    }

    /**
     * Normalize facet type to match module names in database
     */
    private String normalizeFacetType(String facetType) {
        if (facetType == null) return null;
        
        // Map common facet type variations to module names
        String normalized = facetType.trim();
        switch (normalized.toLowerCase()) {
            case "data set":
            case "dataset":
                return "Data Sets";
            case "system":
                return "System";
            case "glossary":
                return "Glossary";
            case "process":
                return "Process";
            case "system interface":
            case "interface":
                return "Interface";
            case "policy":
                return "Policy";
            case "product":
                return "Product";
            case "project":
                return "Project";
            case "business area":
            case "businessarea":
                return "Business Area";
            case "client":
                return "Client";
            case "committee":
                return "Committee";
            case "legal entity":
            case "legalentity":
                return "Legal Entity";
            case "capability":
                return "Capability";
            case "regulation":
                return "Regulation";
            case "attribute":
                return "Attribute";
            default:
                return normalized; // Return as-is if no mapping found
        }
    }

    /**
     * Send email notification for role assignment
     */
    private void sendRoleAssignmentEmail(int userId, String roleName, String facetName, 
                                        String objectName, String facetType, int objectId,
                                        Integer objectXPeopleId, Long notificationId) {
        try {
            // Build email subject
            String subject = "New Role Assignment: " + roleName;

            // Build email body
            String objectLink = getObjectViewLink(facetType, objectId);
            String acceptLink = getAcceptRoleLink(objectXPeopleId, facetType, objectId, userId);
            String emailBody = buildRoleAssignmentEmailBody(roleName, facetName, objectName, 
                                                           objectLink, acceptLink);

            // Send email notification asynchronously (non-blocking)
            emailService.sendAsync(userId, subject, emailBody, notificationId);
            logger.info("Role assignment email queued for user {}", userId);
        } catch (Exception e) {
            logger.error("Error sending role assignment email to user {}", userId, e);
        }
    }

    /**
     * Build HTML email body for role assignment
     */
    private String buildRoleAssignmentEmailBody(String roleName, String facetName, 
                                                String objectName, String objectLink, 
                                                String acceptLink) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");
        html.append("<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;'>");
        
        html.append("<h2 style='color: #248567;'>New Role Assignment</h2>");
        
        html.append("<p>You have been assigned the role: <strong>").append(escapeHtml(roleName)).append("</strong></p>");
        html.append("<p><strong>Object:</strong> ").append(escapeHtml(facetName))
            .append(": <a href='").append(objectLink).append("'>")
            .append(escapeHtml(objectName)).append("</a></p>");
        
        html.append("<div style='margin: 30px 0;'>");
        html.append("<a href='").append(objectLink)
            .append("' style='display: inline-block; padding: 12px 24px; background-color: #248567; color: white; text-decoration: none; border-radius: 4px; margin-right: 10px;'>View Object</a>");
        html.append("<a href='").append(acceptLink)
            .append("' style='display: inline-block; padding: 12px 24px; background-color: #28a745; color: white; text-decoration: none; border-radius: 4px;'>Accept Role</a>");
        html.append("</div>");
        
        html.append("<p style='color: #666; font-size: 12px;'>This is an automated notification from BUDG Platform.</p>");
        html.append("</div></body></html>");
        
        return html.toString();
    }

    /**
     * Get application base URL from system settings or use default
     */
    private String getBaseUrl() {
        try {
            SystemSettings setting = systemSettingsDAO.getSetting("Environment", "application_base_url");
            if (setting != null && setting.getSettingValue() != null && !setting.getSettingValue().trim().isEmpty()) {
                String baseUrl = setting.getSettingValue().trim();
                // Ensure it doesn't end with /
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                return baseUrl;
            }
        } catch (SQLException e) {
            logger.warn("Could not get base URL from system settings: {}", e.getMessage());
        }
        
        // Default fallback - can be configured via system settings
        // Format: http://hostname:port
        return "http://localhost:8080";
    }

    /**
     * Generate full URL to view object based on facet type (for email links)
     * Uses format: {baseUrl}/view/{facet}/{id} for most facets
     */
    private String getObjectViewLink(String facetType, int objectId) {
        String baseUrl = getBaseUrl();
        String normalizedType = normalizeFacetTypeForUrl(facetType);
        String relativePath;
        
        // Some facets use query params format, others use path format
        // Based on MODULE_VIEW_MAPPING in search-links.js
        switch (normalizedType) {
            case "legal-entity":
                relativePath = "/view/LegalEntity/legal-entity.html?id=" + objectId;
                break;
            case "business-area":
                relativePath = "/view/business-area/business-area.html?id=" + objectId;
                break;
            case "capability":
                relativePath = "/view/capability/" + objectId;
                break;
            case "client":
                relativePath = "/view/client/client.html?id=" + objectId;
                break;
            case "geography":
                relativePath = "/view/geography/geography.html?id=" + objectId;
                break;
            case "regulation":
                relativePath = "/view/regulation/regulation.html?id=" + objectId;
                break;
            case "regulator":
                relativePath = "/view/regulator/regulator.html?id=" + objectId;
                break;
            case "regulatory-theme":
                relativePath = "/view/regulatory-theme/regulatory-theme.html?id=" + objectId;
                break;
            default:
                // Most facets use path format: /view/{facet}/{id}
                relativePath = "/view/" + normalizedType + "/" + objectId;
                break;
        }
        
        return baseUrl + relativePath;
    }

    /**
     * Generate full URL to accept role or navigate to responsibilities (for email links)
     */
    private String getAcceptRoleLink(Integer objectXPeopleId, String facetType, int objectId, int userId) {
        String baseUrl = getBaseUrl();
        
        // If we have objectXPeopleId, we can create a direct accept link
        // Otherwise, link to responsibilities page
        if (objectXPeopleId != null) {
            // Link to object's stakeholder page where user can accept
            String objectLink = getObjectViewLink(facetType, objectId);
            // Add #stakeholders anchor if using path format (not query params)
            if (objectLink.contains("?id=")) {
                return objectLink + "&tab=stakeholders";
            } else {
                return objectLink + "#stakeholders";
            }
        } else {
            // Fallback to responsibilities page
            return baseUrl + "/view/people/" + userId + "?tab=responsibilities";
        }
    }

    /**
     * Normalize facet type for URL generation
     */
    private String normalizeFacetTypeForUrl(String facetType) {
        if (facetType == null) return "dataset";
        
        String normalized = facetType.trim().toLowerCase();
        switch (normalized) {
            case "data set":
            case "dataset":
                return "dataset";
            case "system":
                return "system";
            case "glossary":
                return "glossary";
            case "process":
                return "process";
            case "system interface":
            case "interface":
                return "system-interface";
            case "policy":
                return "policy";
            case "product":
                return "product";
            case "project":
                return "project";
            case "business area":
            case "businessarea":
                return "business-area";
            case "client":
                return "client";
            case "committee":
                return "committee";
            case "legal entity":
            case "legalentity":
                return "legal-entity";
            case "capability":
                return "capability";
            case "regulation":
                return "regulation";
            case "attribute":
                return "attribute";
            default:
                return normalized.replace(" ", "-");
        }
    }

    /**
     * Escape HTML special characters
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
