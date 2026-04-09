package com.example.budg_v2.service;

import com.example.budg_v2.dao.*;
import com.example.budg_v2.model.*;
import com.example.budg_v2.util.WorkflowAuthorizationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for sending workflow SLA notifications
 * Phase 4.2: Dynamic notification rules implementation
 */
public class WorkflowNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowNotificationService.class);

    private final WorkflowNotificationRuleService ruleService;
    private final WorkflowNotificationDAO notificationDAO;
    private final WorkflowInstanceDAO instanceDAO;
    private final ProcessDefinitionDAO processDefinitionDAO;
    private final EmailService emailService;
    private final EmailQueueService emailQueueService;
    private final EmailSettingsService emailSettingsService;
    private final UserEmailFrequencyService userEmailFrequencyService;
    private final NotificationSettingsService notificationSettingsService;
    private final CRStakeholderDAO stakeholderDAO;
    private final ChangeRequestDAO changeRequestDAO;
    private final SystemSettingsDAO systemSettingsDAO;

    public WorkflowNotificationService() {
        this.ruleService = new WorkflowNotificationRuleService();
        this.notificationDAO = new WorkflowNotificationDAO();
        this.instanceDAO = new WorkflowInstanceDAO();
        this.processDefinitionDAO = new ProcessDefinitionDAO();
        this.emailService = new EmailService();
        this.emailQueueService = new EmailQueueService();
        this.emailSettingsService = new EmailSettingsService();
        this.userEmailFrequencyService = new UserEmailFrequencyService();
        this.notificationSettingsService = new NotificationSettingsService();
        this.stakeholderDAO = new CRStakeholderDAO();
        this.changeRequestDAO = new ChangeRequestDAO();
        this.systemSettingsDAO = new SystemSettingsDAO();
    }

    /**
     * Send notification when task is assigned
     * 
     * @param task The assigned task (must have ID and workflowInstanceId set)
     */
    public List<Integer> sendTaskAssignmentNotification(WorkflowTask task) {
        if (task.getId() == 0 || task.getWorkflowInstanceId() == 0) {
            logger.warn("Cannot send notification: task ID or workflow instance ID is missing");
            return new ArrayList<>();
        }

        try {
            String module = getModuleName(task);
            List<WorkflowNotificationRule> rules = findMatchingRules(task, "ASSIGN", module);

            String categoryHeader = "Workflow Notifications";
            String message = String.format("Your workflow step '%s' for change request %s is pending.",
                    task.getName(), getChangeRequestName(task));

            // Using explicit loop to send via email with custom body
            // We bypass the generic sendNotificationViaChannels for email to use our custom
            // body
            // But we still need to track notified users
            List<Integer> notifiedUsers = new ArrayList<>();

            for (WorkflowNotificationRule rule : rules) {
                List<Integer> recipients = getRecipientUserIds(task, rule);
                for (Integer recipientId : recipients) {
                    if (!notifiedUsers.contains(recipientId)) {
                        // Send Email
                        if (rule.getChannels().contains("email")) {
                            String specificBody = buildBudgStyleEmail(
                                    getUserName(recipientId),
                                    "Hi " + getUserName(recipientId)
                                            + ", tasks have been assigned to you recently. See the list below for details.",
                                    categoryHeader,
                                    message,
                                    getChangeRequestUrl(task.getWorkflowInstanceId(), "task", task.getId()));
                            sendCustomEmail(recipientId, "Workflow Call to Action", specificBody, rule.getId(),
                                    task.getId(), task.getWorkflowInstanceId());
                        }
                        // Send UI (fallback to generic logic or duplicate here - let's duplicate for
                        // safety)
                        if (rule.getChannels().contains("ui")) {
                            storeUINotification(task, rule, "ASSIGN", "Workflow Call to Action", message, recipientId);
                        }
                        notifiedUsers.add(recipientId);
                    }
                }
            }

            logger.info("TASK ASSIGNED: Task ID={}, Name={}, Role={}, Due Date={}, Rules matched={}",
                    task.getId(),
                    task.getName(),
                    task.getRoleName(),
                    task.getDueAt(),
                    rules.size());

            return notifiedUsers;
        } catch (Exception e) {
            logger.error("Error sending task assignment notification for task: " + task.getId(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Send notification when task becomes overdue
     * 
     * @param task The overdue task (must have ID and workflowInstanceId set)
     */
    public void sendOverdueNotification(WorkflowTask task) {
        if (task.getId() == 0 || task.getWorkflowInstanceId() == 0) {
            logger.warn("Cannot send notification: task ID or workflow instance ID is missing");
            return;
        }

        try {
            String module = getModuleName(task);
            List<WorkflowNotificationRule> rules = findMatchingRules(task, "OVERDUE", module);

            String title = "Workflow Task Overdue";
            String message = String.format("Workflow task '%s' for change request %s is overdue by %s.",
                    task.getName(), getChangeRequestName(task), calculateOverdueDuration(task));

            sendNotificationViaChannels(task, rules, "OVERDUE", title, message);

            logger.warn("TASK OVERDUE: Task ID={}, Name={}, Role={}, Due Date={}, Overdue Since={}, Rules matched={}",
                    task.getId(),
                    task.getName(),
                    task.getRoleName(),
                    task.getDueAt(),
                    calculateOverdueDuration(task),
                    rules.size());
        } catch (Exception e) {
            logger.error("Error sending overdue notification for task: " + task.getId(), e);
        }
    }

    /**
     * Send notification when task escalation threshold is reached
     * 
     * @param task The escalated task (must have ID and workflowInstanceId set)
     */
    public void sendEscalationNotification(WorkflowTask task) {
        if (task.getId() == 0 || task.getWorkflowInstanceId() == 0) {
            logger.warn("Cannot send notification: task ID or workflow instance ID is missing");
            return;
        }

        try {
            String module = getModuleName(task);
            List<WorkflowNotificationRule> rules = findMatchingRules(task, "ESCALATION", module);

            String title = "Workflow Task Escalated";
            String message = String.format(
                    "Workflow task '%s' for change request %s has been escalated. Overdue by %s.",
                    task.getName(), getChangeRequestName(task), calculateOverdueDuration(task));

            sendNotificationViaChannels(task, rules, "ESCALATION", title, message);

            logger.error(
                    "TASK ESCALATED: Task ID={}, Name={}, Role={}, Due Date={}, Escalated At={}, Overdue Duration={}, Rules matched={}",
                    task.getId(),
                    task.getName(),
                    task.getRoleName(),
                    task.getDueAt(),
                    task.getEscalatedAt(),
                    calculateOverdueDuration(task),
                    rules.size());
        } catch (Exception e) {
            logger.error("Error sending escalation notification for task: " + task.getId(), e);
        }
    }

    /**
     * Find matching rules for a task and event type
     */
    private List<WorkflowNotificationRule> findMatchingRules(WorkflowTask task, String eventType, String module)
            throws SQLException {
        return ruleService.getRulesForEvent(module, eventType);
    }

    /**
     * Get module name (Process Definition PrimaryName) from task
     */
    private String getModuleName(WorkflowTask task) throws SQLException {
        WorkflowInstance instance = instanceDAO.findById(task.getWorkflowInstanceId());
        if (instance == null) {
            logger.warn("Workflow instance not found for task: {}", task.getId());
            return "*"; // Fallback to wildcard
        }

        ProcessDefinition processDef = processDefinitionDAO.findById(instance.getProcessDefinitionId());
        if (processDef == null || processDef.getPrimaryName() == null) {
            logger.warn("Process definition not found for instance: {}", instance.getId());
            return "*"; // Fallback to wildcard
        }

        return processDef.getPrimaryName();
    }

    /**
     * Get change request name or ID for display
     */
    private String getChangeRequestName(WorkflowTask task) {
        try {
            WorkflowInstance instance = instanceDAO.findById(task.getWorkflowInstanceId());
            if (instance != null && instance.getChangeRequestId() != null) {
                // For now, return the ID. Can be enhanced to fetch actual name
                return String.valueOf(instance.getChangeRequestId());
            }
        } catch (SQLException e) {
            logger.error("Error getting change request name", e);
        }
        return "N/A";
    }

    /**
     * Send notifications via all matching rules and channels
     */
    private List<Integer> sendNotificationViaChannels(WorkflowTask task, List<WorkflowNotificationRule> rules,
            String eventType, String title, String message) throws SQLException {
        List<Integer> allNotifiedUserIds = new ArrayList<>();

        for (WorkflowNotificationRule rule : rules) {
            List<Integer> recipientUserIds = getRecipientUserIds(task, rule);

            for (Integer recipientUserId : recipientUserIds) {
                // Determine actual channels for this user/rule
                // (Currently just iterates rule channels, logic is compatible)
                for (String channel : rule.getChannels()) {
                    sendNotificationViaChannel(task, rule, eventType, title, message, channel, recipientUserId);
                }

                if (!allNotifiedUserIds.contains(recipientUserId)) {
                    allNotifiedUserIds.add(recipientUserId);
                }
            }
        }
        return allNotifiedUserIds;
    }

    /**
     * Get recipient user IDs based on rule configuration
     * IMPORTANT: Only returns stakeholders who have the required role for the
     * change request
     */
    private List<Integer> getRecipientUserIds(WorkflowTask task, WorkflowNotificationRule rule) throws SQLException {
        List<Integer> userIds = new ArrayList<>();

        // Get change request ID from workflow instance
        WorkflowInstance instance = instanceDAO.findById(task.getWorkflowInstanceId());
        if (instance == null || instance.getChangeRequestId() == null) {
            logger.warn("Cannot get recipients: workflow instance or change request ID not found for task: {}",
                    task.getId());
            return userIds;
        }

        Integer changeRequestId = instance.getChangeRequestId();

        // If specific user is specified, verify they are a stakeholder with the
        // required role
        if (rule.getRecipientUserId() != null) {
            Integer specificUserId = rule.getRecipientUserId().intValue();
            // Verify this user is a stakeholder with matching role
            if (task.getRoleName() != null && !task.getRoleName().trim().isEmpty()) {
                if (WorkflowAuthorizationUtil.checkUserHasRoleForTask(specificUserId, changeRequestId,
                        task.getRoleName())) {
                    userIds.add(specificUserId);
                } else {
                    logger.warn("Specified user {} is not a stakeholder with role {} for CR {}",
                            specificUserId, task.getRoleName(), changeRequestId);
                }
            } else {
                // If no task role, just add the user (for escalation cases)
                userIds.add(specificUserId);
            }
            return userIds;
        }

        // Determine which role to use for filtering
        String targetRoleName = null;
        if (rule.getRecipientRole() != null && !rule.getRecipientRole().trim().isEmpty()) {
            targetRoleName = rule.getRecipientRole();
        } else if (task.getRoleName() != null && !task.getRoleName().trim().isEmpty()) {
            targetRoleName = task.getRoleName();
        }

        // Get stakeholders for the change request
        List<Map<String, Object>> stakeholders = stakeholderDAO.getStakeholdersForChangeRequest(changeRequestId);

        if (targetRoleName != null) {
            // Filter stakeholders by role name (normalized comparison)
            String normalizedTargetRole = WorkflowAuthorizationUtil.normalizeRoleName(targetRoleName);

            for (Map<String, Object> stakeholder : stakeholders) {
                Integer stakeholderUserId = (Integer) stakeholder.get("userId");
                String stakeholderRoleName = (String) stakeholder.get("roleName");

                if (stakeholderUserId != null && stakeholderRoleName != null) {
                    String normalizedStakeholderRole = WorkflowAuthorizationUtil.normalizeRoleName(stakeholderRoleName);
                    if (normalizedTargetRole.equals(normalizedStakeholderRole)) {
                        userIds.add(stakeholderUserId);
                        logger.debug("Added stakeholder {} with role {} for notification",
                                stakeholderUserId, normalizedStakeholderRole);
                    }
                }
            }
        } else {
            // If no role specified, get all stakeholders (for escalation cases)
            for (Map<String, Object> stakeholder : stakeholders) {
                Integer stakeholderUserId = (Integer) stakeholder.get("userId");
                if (stakeholderUserId != null) {
                    userIds.add(stakeholderUserId);
                }
            }
        }

        // Fallback: if no stakeholders found and task has assignedTo, verify and add
        if (userIds.isEmpty() && task.getAssignedTo() != null) {
            if (targetRoleName == null ||
                    WorkflowAuthorizationUtil.checkUserHasRoleForTask(task.getAssignedTo(), changeRequestId,
                            targetRoleName)) {
                userIds.add(task.getAssignedTo());
            }
        }

        if (userIds.isEmpty()) {
            logger.warn("No stakeholders found with role {} for CR {} (task: {})",
                    targetRoleName, changeRequestId, task.getId());
        }

        return userIds;
    }

    /**
     * Send notification via a specific channel
     */
    private void sendNotificationViaChannel(WorkflowTask task, WorkflowNotificationRule rule,
            String eventType, String title, String message,
            String channel, Integer recipientUserId) throws SQLException {
        switch (channel.toLowerCase()) {
            case "ui":
                storeUINotification(task, rule, eventType, title, message, recipientUserId);
                break;
            case "email":
                sendEmailNotification(task, rule, eventType, title, message, recipientUserId);
                break;
            case "sms":
                // TODO: Phase 5 - Send SMS notification
                logger.debug("SMS notification would be sent to user: {} for task: {}", recipientUserId, task.getId());
                break;
            default:
                logger.warn("Unknown notification channel: {}", channel);
        }
    }

    /**
     * Store UI notification in database
     */
    private void storeUINotification(WorkflowTask task, WorkflowNotificationRule rule,
            String eventType, String title, String message,
            Integer recipientUserId) throws SQLException {
        WorkflowNotification notification = new WorkflowNotification();
        notification.setNotificationRuleId(rule.getId());
        notification.setWorkflowTaskId(task.getId());

        // Get change request ID from workflow instance
        try {
            WorkflowInstance instance = instanceDAO.findById(task.getWorkflowInstanceId());
            if (instance != null && instance.getChangeRequestId() != null) {
                notification.setChangeRequestId(instance.getChangeRequestId());
            }
        } catch (SQLException e) {
            logger.error("Error getting change request ID", e);
        }

        notification.setRecipientUserId(recipientUserId);
        notification.setEventType(eventType);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setChannel("ui");
        notification.setCategory("workflow"); // Workflow notifications are always in workflow category
        notification.setRead(false);

        notificationDAO.create(notification);
        logger.debug("Stored UI notification for user: {} for task: {}", recipientUserId, task.getId());
    }

    /**
     * Send email notification
     */
    private void sendEmailNotification(WorkflowTask task, WorkflowNotificationRule rule,
            String eventType, String title, String message,
            Integer recipientUserId) throws SQLException {
        // Check if email is enabled globally
        if (!emailSettingsService.isEmailEnabled()) {
            logger.debug("Email is disabled globally, skipping email notification");
            return;
        }

        // Check if notifications are disabled for Tasks (Workflows)
        if (notificationSettingsService.isNotificationDisabledForTasks()) {
            logger.debug("Email notifications are disabled for tasks, skipping email notification");
            return;
        }

        // Check if user should receive emails
        if (!userEmailFrequencyService.shouldReceiveEmails(recipientUserId)) {
            logger.debug("User {} has opted out of email notifications", recipientUserId);
            return;
        }

        // Create email notification record in database
        WorkflowNotification emailNotification = new WorkflowNotification();
        emailNotification.setNotificationRuleId(rule.getId());
        emailNotification.setWorkflowTaskId(task.getId());

        // Get change request ID from workflow instance
        try {
            WorkflowInstance instance = instanceDAO.findById(task.getWorkflowInstanceId());
            if (instance != null && instance.getChangeRequestId() != null) {
                emailNotification.setChangeRequestId(instance.getChangeRequestId());
            }
        } catch (SQLException e) {
            logger.error("Error getting change request ID", e);
        }

        emailNotification.setRecipientUserId(recipientUserId);
        emailNotification.setEventType(eventType);
        emailNotification.setTitle(title);
        emailNotification.setMessage(message);
        emailNotification.setChannel("email");
        emailNotification.setCategory("workflow"); // Workflow notifications are always in workflow category
        emailNotification.setRead(false);
        emailNotification.setEmailSent(false);

        Long notificationId = notificationDAO.create(emailNotification);

        // Build email subject and body
        String emailSubject = title;
        String emailBody = buildEmailBody(task, message, emailNotification.getChangeRequestId());

        // Determine delivery mode
        String deliveryMode = rule.getDeliveryMode();
        if (deliveryMode == null || deliveryMode.isEmpty()) {
            deliveryMode = "IMMEDIATE"; // Default
        }

        // Check if should send immediately or queue
        boolean sendImmediate = userEmailFrequencyService.shouldSendImmediately(recipientUserId, deliveryMode);

        if (sendImmediate || "IMMEDIATE".equalsIgnoreCase(deliveryMode)) {
            // Send immediately
            boolean sent = emailService.sendImmediate(recipientUserId, emailSubject, emailBody, notificationId);
            if (sent) {
                logger.info("Email notification sent immediately to user: {} for task: {}", recipientUserId,
                        task.getId());
            } else {
                logger.warn("Failed to send email notification to user: {} for task: {}", recipientUserId,
                        task.getId());
            }
        } else {
            // Queue for batch delivery
            try {
                emailQueueService.queueEmail(recipientUserId, emailSubject, emailBody, notificationId);
                logger.info("Email notification queued for batch delivery to user: {} for task: {}", recipientUserId,
                        task.getId());
            } catch (SQLException e) {
                logger.error("Error queueing email notification", e);
            }
        }
    }

    /**
     * Build HTML email body with enhanced modern design
     */
    private String buildEmailBody(WorkflowTask task, String message, Integer changeRequestId) {
        // Get change request name
        String changeRequestName = null;
        if (changeRequestId != null) {
            try {
                ChangeRequest cr = changeRequestDAO.getChangeRequestById(changeRequestId);
                if (cr != null) {
                    changeRequestName = cr.getPrimaryName();
                }
            } catch (SQLException e) {
                logger.warn("Could not fetch change request name for ID {}: {}", changeRequestId, e.getMessage());
            }
        }
        
        String crDisplayName = (changeRequestName != null && !changeRequestName.trim().isEmpty()) 
            ? escapeHtml(changeRequestName) 
            : ("Change Request #" + changeRequestId);

        // Icons SVG
        String taskIcon = "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='#0d9488' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'></path><polyline points='14 2 14 8 20 8'></polyline><line x1='16' y1='13' x2='8' y2='13'></line><line x1='16' y1='17' x2='8' y2='17'></line><polyline points='10 9 9 9 8 9'></polyline></svg>";
        String roleIcon = "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='#0d9488' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2'></path><circle cx='9' cy='7' r='4'></circle><path d='M23 21v-2a4 4 0 0 0-3-3.87'></path><path d='M16 3.13a4 4 0 0 1 0 7.75'></path></svg>";
        String dateIcon = "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='#0d9488' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><rect x='3' y='4' width='18' height='18' rx='2' ry='2'></rect><line x1='16' y1='2' x2='16' y2='6'></line><line x1='8' y1='2' x2='8' y2='6'></line><line x1='3' y1='10' x2='21' y2='10'></line></svg>";
        String crIcon = "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='#0d9488' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'></circle><polyline points='12 6 12 12 16 14'></polyline></svg>";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<style>");
        html.append("  * { box-sizing: border-box; }");
        html.append("  body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f0f4f8; line-height: 1.6; }");
        html.append("  .email-wrapper { padding: 20px 0; background-color: #f0f4f8; }");
        html.append("  .email-container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08); }");
        html.append("  .header { background: linear-gradient(135deg, #0d9488 0%, #0891b2 50%, #06b6d4 100%); padding: 40px 30px; text-align: center; position: relative; }");
        html.append("  .header::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 4px; background: linear-gradient(90deg, rgba(255,255,255,0.3) 0%, rgba(255,255,255,0.1) 100%); }");
        html.append("  .logo { font-size: 32px; font-weight: 700; color: #ffffff; margin-bottom: 8px; letter-spacing: 2px; text-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        html.append("  .app-name { font-size: 14px; color: #ffffff; opacity: 0.95; letter-spacing: 1.5px; font-weight: 400; text-transform: uppercase; }");
        html.append("  .content { padding: 40px 30px; }");
        html.append("  .message-title { font-size: 22px; color: #0f172a; margin-bottom: 24px; font-weight: 600; line-height: 1.4; }");
        html.append("  .info-card { background: linear-gradient(to bottom, #f8fafc 0%, #f1f5f9 100%); padding: 24px; border-radius: 10px; margin: 24px 0; border-left: 5px solid #0d9488; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04); }");
        html.append("  .info-row { margin: 16px 0; display: flex; align-items: flex-start; gap: 12px; }");
        html.append("  .info-row:first-child { margin-top: 0; }");
        html.append("  .info-row:last-child { margin-bottom: 0; }");
        html.append("  .info-icon { flex-shrink: 0; margin-top: 2px; opacity: 0.8; }");
        html.append("  .info-label { font-weight: 600; color: #475569; min-width: 130px; font-size: 14px; }");
        html.append("  .info-value { color: #1e293b; flex: 1; font-size: 14px; line-height: 1.5; }");
        html.append("  .info-value a { color: #0d9488; text-decoration: none; font-weight: 500; transition: color 0.2s; }");
        html.append("  .info-value a:hover { color: #0891b2; text-decoration: underline; }");
        html.append("  .divider { height: 1px; background: linear-gradient(90deg, transparent 0%, #e2e8f0 50%, transparent 100%); margin: 20px 0; }");
        html.append("  .button-container { margin-top: 28px; text-align: center; }");
        html.append("  .button { display: inline-block; background: linear-gradient(135deg, #0d9488 0%, #0891b2 100%); color: #ffffff; padding: 14px 32px; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 15px; margin-top: 8px; box-shadow: 0 4px 12px rgba(13, 148, 136, 0.3); transition: all 0.3s ease; letter-spacing: 0.3px; }");
        html.append("  .button:hover { background: linear-gradient(135deg, #0891b2 0%, #06b6d4 100%); box-shadow: 0 6px 16px rgba(13, 148, 136, 0.4); transform: translateY(-1px); }");
        html.append("  .footer { background-color: #f8fafc; padding: 30px 20px; text-align: center; border-top: 1px solid #e2e8f0; }");
        html.append("  .footer-text { color: #64748b; font-size: 13px; margin: 8px 0; line-height: 1.6; }");
        html.append("  .footer-links { margin: 16px 0; }");
        html.append("  .footer-link { color: #0d9488; text-decoration: none; margin: 0 12px; font-size: 13px; font-weight: 500; transition: color 0.2s; }");
        html.append("  .footer-link:hover { color: #0891b2; text-decoration: underline; }");
        html.append("  .footer-brand { margin-top: 20px; padding-top: 20px; border-top: 1px solid #e2e8f0; }");
        html.append("  .footer-brand-text { color: #94a3b8; font-size: 11px; letter-spacing: 0.5px; text-transform: uppercase; }");
        html.append("  @media only screen and (max-width: 600px) {");
        html.append("    .email-wrapper { padding: 0; }");
        html.append("    .email-container { border-radius: 0; box-shadow: none; }");
        html.append("    .header { padding: 30px 20px; }");
        html.append("    .logo { font-size: 28px; }");
        html.append("    .app-name { font-size: 12px; }");
        html.append("    .content { padding: 30px 20px; }");
        html.append("    .message-title { font-size: 20px; margin-bottom: 20px; }");
        html.append("    .info-card { padding: 20px; margin: 20px 0; }");
        html.append("    .info-row { flex-direction: row; gap: 10px; margin: 14px 0; }");
        html.append("    .info-label { min-width: 100px; font-size: 13px; }");
        html.append("    .info-value { font-size: 13px; }");
        html.append("    .button { display: block; width: 100%; padding: 14px 24px; text-align: center; box-sizing: border-box; }");
        html.append("    .footer { padding: 24px 16px; }");
        html.append("    .footer-link { display: inline-block; margin: 4px 8px; }");
        html.append("  }");
        html.append("</style>");
        html.append("</head><body>");
        html.append("<div class='email-wrapper'>");
        html.append("<div class='email-container'>");
        
        // Header with logo and app name
        html.append("<div class='header'>");
        html.append("<div class='logo'>BUDG</div>");
        html.append("<div class='app-name'>Business Unified Data Governance</div>");
        html.append("</div>");
        
        // Content
        html.append("<div class='content'>");
        html.append("<div class='message-title'>").append(escapeHtml(message)).append("</div>");
        
        // Info card
        html.append("<div class='info-card'>");
        
        html.append("<div class='info-row'>");
        html.append("<span class='info-icon'>").append(taskIcon).append("</span>");
        html.append("<span class='info-label'>Task:</span>");
        html.append("<span class='info-value'>").append(escapeHtml(task.getName())).append("</span>");
        html.append("</div>");
        
        if (task.getRoleName() != null) {
            html.append("<div class='divider'></div>");
            html.append("<div class='info-row'>");
            html.append("<span class='info-icon'>").append(roleIcon).append("</span>");
            html.append("<span class='info-label'>Role:</span>");
            html.append("<span class='info-value'>").append(escapeHtml(task.getRoleName())).append("</span>");
            html.append("</div>");
        }
        
        if (task.getDueAt() != null) {
            html.append("<div class='divider'></div>");
            html.append("<div class='info-row'>");
            html.append("<span class='info-icon'>").append(dateIcon).append("</span>");
            html.append("<span class='info-label'>Due Date:</span>");
            html.append("<span class='info-value'>").append(task.getDueAt().toString()).append("</span>");
            html.append("</div>");
        }
        
        if (changeRequestId != null) {
            html.append("<div class='divider'></div>");
            html.append("<div class='info-row'>");
            html.append("<span class='info-icon'>").append(crIcon).append("</span>");
            html.append("<span class='info-label'>Change Request:</span>");
            html.append("<span class='info-value'><a href='").append(getChangeRequestUrl(changeRequestId))
                    .append("'>").append(crDisplayName).append("</a></span>");
            html.append("</div>");
        }
        html.append("</div>");
        
        // Action button
        if (changeRequestId != null) {
            html.append("<div class='button-container'>");
            html.append("<a href='").append(getChangeRequestUrl(changeRequestId))
                    .append("' class='button'>View Change Request</a>");
            html.append("</div>");
        }
        
        html.append("</div>");
        
        // Footer
        html.append("<div class='footer'>");
        html.append("<p class='footer-text'>This is an automated notification from BUDG Platform.</p>");
        html.append("<div class='footer-links'>");
        html.append("<a href='/view/my-requests' class='footer-link'>My Change Requests</a>");
        html.append("<a href='/settings/notifications' class='footer-link'>Notification Settings</a>");
        html.append("</div>");
        html.append("<div class='footer-brand'>");
        html.append("<p class='footer-brand-text'>Powered by BUSSMA COMPANY S.M.E.</p>");
        html.append("</div>");
        html.append("</div>");
        
        html.append("</div>");
        html.append("</div>");
        html.append("</body></html>");
        return html.toString();
    }

    /**
     * Get Change Request URL (absolute URL for email links)
     */
    private String getChangeRequestUrl(Integer changeRequestId) {
        String baseUrl = getBaseUrl();
        return baseUrl + "/view/change-request/change-request-view.html?id=" + changeRequestId;
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

    /**
     * Calculate how long task has been overdue (in days)
     */
    private String calculateOverdueDuration(WorkflowTask task) {
        if (task.getDueAt() == null) {
            return "Unknown";
        }

        long overdueMillis = System.currentTimeMillis() - task.getDueAt().getTime();
        long overdueDays = overdueMillis / (24 * 60 * 60 * 1000);

        if (overdueDays == 0) {
            long overdueHours = overdueMillis / (60 * 60 * 1000);
            return overdueHours + " hours";
        }

        return overdueDays + " days";
    }

    /**
     * Send notification when a CR is first raised/created.
     * Notifies the person who raised it and all stakeholders of the object.
     */
    public void sendCrRaisedNotification(int changeRequestId, int raisedByUserId) {
        try {
            String title = "New Change Request Raised";
            String message = "Change Request #" + changeRequestId + " has been raised.";

            // Notify person who raised it
            createDirectNotification(changeRequestId, raisedByUserId, "CR_RAISED", title, message, null);

            // Notify all stakeholders (skip raisedByUserId to avoid duplicate)
            List<Map<String, Object>> stakeholders = stakeholderDAO.getStakeholdersForChangeRequest(changeRequestId);
            for (Map<String, Object> stakeholder : stakeholders) {
                Integer userId = (Integer) stakeholder.get("userId");
                if (userId != null && userId != raisedByUserId) {
                    createDirectNotification(changeRequestId, userId, "CR_RAISED", title, message, null);
                }
            }

            logger.info("CR RAISED: Notification sent to raiser {} and {} stakeholder(s) for CR {}",
                    raisedByUserId, stakeholders.size(), changeRequestId);

        } catch (Exception e) {
            logger.error("Error sending CR raised notification for CR: " + changeRequestId, e);
        }
    }

    /**
     * Send notification when a workflow is started (CR Start).
     * Notifies ALL stakeholders and the creator — no exclusions.
     */
    public void sendCrStartNotification(WorkflowInstance instance, int startedByUserId, List<Integer> excludedUserIds) {
        if (instance == null || instance.getChangeRequestId() == null) {
            logger.warn("Cannot send CR start notification: instance or CR ID missing");
            return;
        }

        try {
            Integer changeRequestId = instance.getChangeRequestId();
            Integer creatorId = getChangeRequestCreatorId(changeRequestId);

            String title = "Change Request Workflow Started";
            String message = "Workflow for Change Request #" + changeRequestId + " has been started.";

            if (creatorId != null && creatorId > 0) {
                createDirectNotification(changeRequestId, creatorId, "CR_START", title, message, 0);
            } else {
                createDirectNotification(changeRequestId, startedByUserId, "CR_START", title, message, 0);
            }

            // Notify ALL stakeholders — excludedUserIds is intentionally ignored so every
            // stakeholder receives the workflow-started notification.
            List<Map<String, Object>> stakeholders = stakeholderDAO.getStakeholdersForChangeRequest(changeRequestId);
            for (Map<String, Object> stakeholder : stakeholders) {
                Integer userId = (Integer) stakeholder.get("userId");
                if (userId != null && (creatorId == null || !userId.equals(creatorId))) {
                    createDirectNotification(changeRequestId, userId, "CR_START", title, message, null);
                }
            }

            logger.info("CR START: Notification sent to creator/stakeholders for CR {}", changeRequestId);

        } catch (Exception e) {
            logger.error("Error sending CR start notification for CR: " + instance.getChangeRequestId(), e);
        }
    }

    /**
     * Send notification when a step is completed or rejected.
     * Notifies the CR creator, all stakeholders, and the person who completed the step.
     */
    public void sendStepCompletionNotification(WorkflowTask task, String decision, int completedByUserId) {
        try {
            WorkflowInstance instance = instanceDAO.findById(task.getWorkflowInstanceId());
            if (instance == null || instance.getChangeRequestId() == null)
                return;

            Integer changeRequestId = instance.getChangeRequestId();
            String title = "Workflow Step " + (decision != null && decision.toLowerCase().contains("reject") ? "Rejected" : "Completed");
            String message = String.format("Step '%s' for Change Request #%d was %s.",
                    task.getName(), changeRequestId,
                    decision != null ? decision.toLowerCase() : "completed");

            // Track who has been notified to avoid duplicates
            List<Integer> notified = new ArrayList<>();

            // Notify CR creator
            Integer creatorId = getChangeRequestCreatorId(changeRequestId);
            if (creatorId != null && creatorId > 0) {
                createDirectNotification(changeRequestId, creatorId, "STEP_COMPLETED", title, message, task.getId());
                notified.add(creatorId);
            }

            // Notify all stakeholders
            List<Map<String, Object>> stakeholders = stakeholderDAO.getStakeholdersForChangeRequest(changeRequestId);
            for (Map<String, Object> stakeholder : stakeholders) {
                Integer userId = (Integer) stakeholder.get("userId");
                if (userId != null && !notified.contains(userId)) {
                    createDirectNotification(changeRequestId, userId, "STEP_COMPLETED", title, message, task.getId());
                    notified.add(userId);
                }
            }

            // Notify person who completed/rejected the step (if not already notified)
            if (!notified.contains(completedByUserId)) {
                createDirectNotification(changeRequestId, completedByUserId, "STEP_COMPLETED", title, message, task.getId());
                notified.add(completedByUserId);
            }

            logger.info("STEP COMPLETED: Notification sent to {} recipients for task {}", notified.size(), task.getId());

        } catch (Exception e) {
            logger.error("Error sending step completion notification for task: " + task.getId(), e);
        }
    }

    /**
     * Send notification when a dashboard is shared with a specific user.
     */
    public void sendDashboardSharedNotification(int dashboardId, String dashboardName, int sharedWithUserId, int sharedByUserId) {
        try {
            String title = "Dashboard Shared With You";
            String message = String.format("The dashboard \"%s\" has been shared with you.", dashboardName);
            createDirectNotification(null, sharedWithUserId, "DASHBOARD_SHARED", title, message, null);
            logger.info("DASHBOARD SHARED: Notification sent to user {} for dashboard {}", sharedWithUserId, dashboardId);
        } catch (Exception e) {
            logger.error("Error sending dashboard shared notification for dashboard: " + dashboardId, e);
        }
    }

    /**
     * Send notification when a dashboard is no longer shared with a user.
     */
    public void sendDashboardUnsharedNotification(int dashboardId, String dashboardName, int userId) {
        try {
            String title = "Dashboard No Longer Shared";
            String message = String.format("The dashboard \"%s\" is no longer shared with you.", dashboardName);
            createDirectNotification(null, userId, "DASHBOARD_UNSHARED", title, message, null);
            logger.info("DASHBOARD UNSHARED: Notification sent to user {} for dashboard {}", userId, dashboardId);
        } catch (Exception e) {
            logger.error("Error sending dashboard unshared notification for dashboard: " + dashboardId, e);
        }
    }

    /**
     * Send notification when a shared dashboard has been deleted.
     */
    public void sendDashboardRemovedNotification(int dashboardId, String dashboardName, int userId) {
        try {
            String title = "Shared Dashboard Removed";
            String message = String.format("The dashboard \"%s\" that was shared with you has been removed.", dashboardName);
            createDirectNotification(null, userId, "DASHBOARD_REMOVED", title, message, null);
            logger.info("DASHBOARD REMOVED: Notification sent to user {} for dashboard {}", userId, dashboardId);
        } catch (Exception e) {
            logger.error("Error sending dashboard removed notification for dashboard: " + dashboardId, e);
        }
    }

    /**
     * Send notification when a user is mentioned in a comment
     */
    public void sendMentionNotification(WorkflowTaskComment comment, List<String> mentionedUserNames) {
        try {
            WorkflowTask task = new WorkflowTaskDAO().findById(comment.getWorkflowTaskId());
            if (task == null)
                return;

            WorkflowInstance instance = instanceDAO.findById(task.getWorkflowInstanceId());
            if (instance == null)
                return;

            Integer changeRequestId = instance.getChangeRequestId();

            for (String userName : mentionedUserNames) {
                Integer mentionedUserId = resolveUserByName(userName);

                if (mentionedUserId != null) {
                    String title = "You were mentioned in a comment";
                    String message = String.format("You were mentioned in a comment on Change Request #%d: \"%s...\"",
                            changeRequestId,
                            comment.getCommentText().length() > 50 ? comment.getCommentText().substring(0, 50)
                                    : comment.getCommentText());

                    createDirectNotification(changeRequestId, mentionedUserId, "MENTION", title, message, task.getId());
                    logger.info("MENTION: Notification sent to user {} for comment on task {}", mentionedUserId,
                            task.getId());
                } else {
                    logger.warn("Could not resolve mentioned user: '{}'. Name must match 'First Last' exactly.",
                            userName);
                }
            }

        } catch (Exception e) {
            logger.error("Error sending mention notification", e);
        }
    }

    /**
     * Send notification when Change Request is Completed
     */
    public void sendCrCompletedNotification(WorkflowInstance instance) {
        if (instance == null || instance.getChangeRequestId() == null)
            return;

        try {
            Integer crId = instance.getChangeRequestId();
            String title = "Change Request Completed";
            String message = "Change Request " + getChangeRequestName(instance) + " has been completed";
            String header = "Completed Change Requests";

            // Notify Creator and Stakeholders
            List<Integer> recipients = new ArrayList<>();
            recipients.add(getChangeRequestCreatorId(crId));

            List<Map<String, Object>> stakeholders = stakeholderDAO.getStakeholdersForChangeRequest(crId);
            for (Map<String, Object> stakeholder : stakeholders) {
                Integer uid = (Integer) stakeholder.get("userId");
                if (uid != null && !recipients.contains(uid))
                    recipients.add(uid);
            }

            for (Integer recipientId : recipients) {
                if (recipientId == null)
                    continue;
                String name = getUserName(recipientId);
                String intro = "Hi " + name
                        + ", some change requests have been finalized. See the list below for details.";

                String body = buildBudgStyleEmail(name, intro, header, message,
                        getChangeRequestUrl(crId, "view", null));

                // Create Notification Record
                createDirectNotification(crId, recipientId, "COMPLETED", title, message, null);

                // Send Email directly (bypassing generic creator which uses simple body)
                sendCustomEmail(recipientId, title, body, null, null, crId);
            }
        } catch (Exception e) {
            logger.error("Error sending CR completed notification", e);
        }
    }

    /**
     * Send notification when Change Request is Cancelled
     */
    public void sendCrCancelledNotification(Integer changeRequestId, Integer cancelledByUserId) {
        if (changeRequestId == null)
            return;

        try {
            String title = "Change Request Cancelled";
            String message = "Change Request #" + changeRequestId + " has been cancelled";
            String header = "Cancelled Change Requests";

            // Notify Creator and Stakeholders
            List<Integer> recipients = new ArrayList<>();
            recipients.add(getChangeRequestCreatorId(changeRequestId));

            List<Map<String, Object>> stakeholders = stakeholderDAO.getStakeholdersForChangeRequest(changeRequestId);
            for (Map<String, Object> stakeholder : stakeholders) {
                Integer uid = (Integer) stakeholder.get("userId");
                if (uid != null && !recipients.contains(uid))
                    recipients.add(uid);
            }

            for (Integer recipientId : recipients) {
                if (recipientId == null)
                    continue;
                String name = getUserName(recipientId);
                String intro = "Hi " + name
                        + ", some change requests have been finalized. See the list below for details.";

                String body = buildBudgStyleEmail(name, intro, header, message,
                        getChangeRequestUrl(changeRequestId, "view", null));

                // Create Record
                createDirectNotification(changeRequestId, recipientId, "CANCELLED", title, message, null);

                // Send Email
                sendCustomEmail(recipientId, title, body, null, null, changeRequestId);
            }
        } catch (Exception e) {
            logger.error("Error sending CR cancelled notification", e);
        }
    }

    private void sendCustomEmail(Integer recipientId, String subject, String body, Long ruleId, Integer taskId,
            Integer crId) throws SQLException {
        if (!emailSettingsService.isEmailEnabled() || !userEmailFrequencyService.shouldReceiveEmails(recipientId))
            return;

        // Check if notifications are disabled for Change Requests
        if (notificationSettingsService.isNotificationDisabledForCRs()) {
            logger.debug("Email notifications are disabled for CRs, skipping email notification");
            return;
        }

        // We might want to link this to a notification record ID for tracking,
        // but for now we'll just send it.
        // Ideally we update the notification record created by createDirectNotification
        // or similar.

        if (userEmailFrequencyService.shouldSendImmediately(recipientId, "IMMEDIATE")) {
            emailService.sendImmediate(recipientId, subject, body, null);
        } else {
            emailQueueService.queueEmail(recipientId, subject, body, null);
        }
    }

    private String getUserName(Integer userId) {
        if (userId == null)
            return "User";
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                java.sql.PreparedStatement ps = conn
                        .prepareStatement("SELECT First_name, Last_name FROM people WHERE ID = ?")) {
            ps.setInt(1, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String first = rs.getString("First_name");
                    String last = rs.getString("Last_name");
                    return (first != null ? first : "") + (last != null ? " " + last : "");
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching user name", e);
        }
        return "User";
    }

    private String getChangeRequestName(WorkflowInstance instance) {
        return instance.getChangeRequestId() != null ? "#" + instance.getChangeRequestId() : "Unknown";
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

    private String getChangeRequestUrl(Integer id, String type, Integer taskId) {
        String baseUrl = getBaseUrl();
        String url = baseUrl + "/view/change-request/change-request-view.html?id=" + id;

        if ("task".equals(type) && taskId != null) {
            url += "&taskId=" + taskId;
        }
        return url;
    }

    /**
     * Build Premium Professional HTML Email (BUDG Style) - Modern Light Theme
     */
    private String buildBudgStyleEmail(String userName, String introduction, String categoryHeader, String message,
            String actionLink) {
        String iconSvg = getIconForCategory(categoryHeader);
        String accentColor = getAccentColorForCategory(categoryHeader);

        return "<!DOCTYPE html>" +
                "<html><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "  * { box-sizing: border-box; }" +
                "  body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f0f4f8; line-height: 1.6; }" +
                "  .email-wrapper { padding: 20px 0; background-color: #f0f4f8; }" +
                "  .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08); }" +
                "  .header { background: linear-gradient(135deg, #0d9488 0%, #0891b2 50%, " + accentColor + " 100%); padding: 40px 30px; text-align: center; position: relative; }" +
                "  .header::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 4px; background: linear-gradient(90deg, rgba(255,255,255,0.3) 0%, rgba(255,255,255,0.1) 100%); }" +
                "  .icon-container { display: inline-flex; align-items: center; justify-content: center; width: 56px; height: 56px; border-radius: 50%; background-color: rgba(255,255,255,0.2); margin-bottom: 16px; backdrop-filter: blur(10px); }" +
                "  .header h1 { margin: 0; font-size: 26px; font-weight: 700; letter-spacing: 1px; text-transform: uppercase; color: #ffffff; text-shadow: 0 2px 4px rgba(0,0,0,0.1); }" +
                "  .content { padding: 40px 30px; }" +
                "  .greeting { font-size: 20px; margin-bottom: 16px; color: #0f172a; font-weight: 600; }" +
                "  .intro-text { color: #475569; line-height: 1.7; font-size: 15px; margin-bottom: 32px; }" +
                "  .card { background: linear-gradient(to bottom, #f8fafc 0%, #f1f5f9 100%); border-left: 5px solid " + accentColor + "; padding: 28px; border-radius: 10px; margin-bottom: 32px; display: flex; align-items: center; justify-content: space-between; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04); gap: 20px; }" +
                "  .message-content { flex: 1; min-width: 0; }" +
                "  .message-title { display: block; font-size: 12px; text-transform: uppercase; color: #64748b; margin-bottom: 8px; font-weight: 700; letter-spacing: 1.2px; }" +
                "  .message-text { font-size: 16px; color: #1e293b; font-weight: 500; line-height: 1.5; }" +
                "  .button { background: linear-gradient(135deg, " + accentColor + " 0%, " + (categoryHeader.contains("Completed") ? "#00e676" : categoryHeader.contains("Cancelled") ? "#ff5252" : "#06b6d4") + " 100%); color: #ffffff; text-decoration: none; padding: 14px 28px; border-radius: 8px; font-weight: 600; font-size: 15px; transition: all 0.3s ease; box-shadow: 0 4px 12px rgba(0,0,0,0.15); white-space: nowrap; letter-spacing: 0.3px; }" +
                "  .button:hover { box-shadow: 0 6px 16px rgba(0,0,0,0.2); transform: translateY(-1px); }" +
                "  .footer { background-color: #f8fafc; padding: 30px 20px; text-align: center; border-top: 1px solid #e2e8f0; }" +
                "  .footer-text { color: #64748b; font-size: 13px; margin: 8px 0; line-height: 1.6; }" +
                "  .footer-links { margin: 16px 0; }" +
                "  .footer-link { color: " + accentColor + "; text-decoration: none; margin: 0 12px; font-size: 13px; font-weight: 500; transition: color 0.2s; }" +
                "  .footer-link:hover { text-decoration: underline; opacity: 0.8; }" +
                "  .footer-brand { margin-top: 20px; padding-top: 20px; border-top: 1px solid #e2e8f0; }" +
                "  .footer-brand-text { color: #94a3b8; font-size: 11px; letter-spacing: 0.5px; text-transform: uppercase; }" +
                "  @media only screen and (max-width: 600px) {" +
                "    .email-wrapper { padding: 0; }" +
                "    .container { width: 100%; border-radius: 0; box-shadow: none; }" +
                "    .header { padding: 30px 20px; }" +
                "    .icon-container { width: 48px; height: 48px; margin-bottom: 12px; }" +
                "    .header h1 { font-size: 22px; }" +
                "    .content { padding: 30px 20px; }" +
                "    .greeting { font-size: 18px; }" +
                "    .intro-text { font-size: 14px; margin-bottom: 24px; }" +
                "    .card { flex-direction: column; align-items: flex-start; padding: 24px; gap: 16px; }" +
                "    .button { display: block; width: 100%; text-align: center; box-sizing: border-box; white-space: normal; }" +
                "    .footer { padding: 24px 16px; }" +
                "    .footer-link { display: inline-block; margin: 4px 8px; }" +
                "  }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "  <div class='email-wrapper'>" +
                "    <div class='container'>" +
                "      <div class='header'>" +
                "        <div class='icon-container'>" + iconSvg + "</div>" +
                "        <h1>My BUDG Actions</h1>" +
                "      </div>" +
                "      <div class='content'>" +
                "        <div class='greeting'>Hi " + escapeHtml(userName) + ",</div>" +
                "        <div class='intro-text'>" + escapeHtml(introduction) + "</div>" +
                "        <div class='card'>" +
                "          <div class='message-content'>" +
                "            <span class='message-title'>Details</span>" +
                "            <div class='message-text'>" + escapeHtml(message) + "</div>" +
                "          </div>" +
                "          <a href='" + actionLink + "' class='button'>Review & Validate</a>" +
                "        </div>" +
                "      </div>" +
                "      <div class='footer'>" +
                "        <p class='footer-text'>You received this email because you are a stakeholder in this workflow.</p>" +
                "        <div class='footer-links'>" +
                "          <a href='/view/my-requests' class='footer-link'>My Change Requests</a>" +
                "          <a href='/settings/notifications' class='footer-link'>Notification Settings</a>" +
                "        </div>" +
                "        <div class='footer-brand'>" +
                "          <p class='footer-brand-text'>Powered by BUSSMA COMPANY S.M.E.</p>" +
                "        </div>" +
                "      </div>" +
                "    </div>" +
                "  </div>" +
                "</body></html>";
    }

    private String getIconForCategory(String category) {
        // Enhanced SVG icons with better styling
        if (category.contains("Completed")) {
            // Checkmark circle icon
            return "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#ffffff' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'><path d='M22 11.08V12a10 10 0 1 1-5.93-9.14'></path><polyline points='22 4 12 14.01 9 11.01'></polyline></svg>";
        } else if (category.contains("Cancelled")) {
            // X circle icon
            return "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#ffffff' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'></circle><line x1='15' y1='9' x2='9' y2='15'></line><line x1='9' y1='9' x2='15' y2='15'></line></svg>";
        } else if (category.contains("Workflow") || category.contains("Started") || category.contains("Start")) {
            // Play/Start icon
            return "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#ffffff' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'></circle><polygon points='10 8 16 12 10 16 10 8'></polygon></svg>";
        } else if (category.contains("Overdue") || category.contains("Escalation")) {
            // Alert/Warning icon
            return "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#ffffff' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'><path d='M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z'></path><line x1='12' y1='9' x2='12' y2='13'></line><line x1='12' y1='17' x2='12.01' y2='17'></line></svg>";
        } else if (category.contains("Mention")) {
            // At/mention icon
            return "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#ffffff' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='4'></circle><path d='M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-3.92 7.94'></path></svg>";
        } else {
            // Clock/Action icon (default for Workflow/Pending)
            return "<svg width='28' height='28' viewBox='0 0 24 24' fill='none' stroke='#ffffff' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'></circle><polyline points='12 6 12 12 16 14'></polyline></svg>";
        }
    }

    private String getAccentColorForCategory(String category) {
        if (category.contains("Completed"))
            return "#10b981"; // Modern Green
        if (category.contains("Cancelled"))
            return "#ef4444"; // Modern Red
        if (category.contains("Overdue") || category.contains("Escalation"))
            return "#f59e0b"; // Amber/Orange for warnings
        if (category.contains("Started") || category.contains("Start"))
            return "#3b82f6"; // Blue for start actions
        if (category.contains("Mention"))
            return "#8b5cf6"; // Purple for mentions
        return "#06b6d4"; // Modern Cyan (Default)
    }

    private void createDirectNotification(Integer changeRequestId, Integer recipientUserId, String eventType,
            String title, String message, Integer taskId) throws SQLException {
        if (recipientUserId == null)
            return;

        WorkflowNotification notification = new WorkflowNotification();
        notification.setChangeRequestId(changeRequestId);
        notification.setWorkflowTaskId((taskId != null && taskId > 0) ? taskId : null);
        notification.setRecipientUserId(recipientUserId);
        notification.setEventType(eventType);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setChannel("ui");
        notification.setCategory("workflow"); // Workflow notifications are always in workflow category
        notification.setRead(false);

        notificationDAO.create(notification);

        if (emailSettingsService.isEmailEnabled() && userEmailFrequencyService.shouldReceiveEmails(recipientUserId)) {
            // Check if notifications are disabled based on event type
            // CR events: CR_RAISED, CR_START, CANCELLED, COMPLETED
            // Workflow events: ASSIGN, OVERDUE, ESCALATION, MENTION, STEP_COMPLETED
            boolean isCREvent = "CR_RAISED".equals(eventType) || "CR_START".equals(eventType) ||
                                 "CANCELLED".equals(eventType) || "COMPLETED".equals(eventType);
            boolean isWorkflowEvent = "ASSIGN".equals(eventType) || "OVERDUE".equals(eventType) || 
                                     "ESCALATION".equals(eventType) || "MENTION".equals(eventType) || 
                                     "STEP_COMPLETED".equals(eventType);

            if (isCREvent && notificationSettingsService.isNotificationDisabledForCRs()) {
                logger.debug("Email notifications are disabled for CRs, skipping email notification for event: {}", eventType);
                return;
            }
            if (isWorkflowEvent && notificationSettingsService.isNotificationDisabledForTasks()) {
                logger.debug("Email notifications are disabled for tasks, skipping email notification for event: {}", eventType);
                return;
            }

            try {
                String deliveryMode = "IMMEDIATE";

                WorkflowNotification emailNotif = new WorkflowNotification();
                emailNotif.setChangeRequestId(changeRequestId);
                emailNotif.setRecipientUserId(recipientUserId);
                emailNotif.setEventType(eventType);
                emailNotif.setTitle(title);
                emailNotif.setMessage(message);
                emailNotif.setChannel("email");
                emailNotif.setCategory("workflow"); // Workflow notifications are always in workflow category
                emailNotif.setRead(false);
                emailNotif.setEmailSent(false);

                Long id = notificationDAO.create(emailNotif);

                String body = buildSimpleEmailBody(title, message, changeRequestId);

                if (userEmailFrequencyService.shouldSendImmediately(recipientUserId, deliveryMode)) {
                    emailService.sendImmediate(recipientUserId, title, body, id);
                } else {
                    emailQueueService.queueEmail(recipientUserId, title, body, id);
                }
            } catch (Exception e) {
                logger.error("Error sending email for " + eventType, e);
            }
        }
    }

    private Integer getChangeRequestCreatorId(Integer crId) {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                java.sql.PreparedStatement ps = conn
                        .prepareStatement("SELECT Created_By FROM changerequest WHERE id = ?")) {
            ps.setInt(1, crId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Created_By");
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching CR creator", e);
        }
        return null; // Return null if not found
    }

    private Integer resolveUserByName(String fullName) {
        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT ID FROM people WHERE LOWER(CONCAT(COALESCE(First_name,''), ' ', COALESCE(Last_name,''))) = LOWER(?) LIMIT 1")) {
            ps.setString(1, fullName.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        } catch (Exception e) {
            logger.error("Error resolving user by name: " + fullName, e);
        }
        return null;
    }

    private String buildSimpleEmailBody(String title, String message, Integer changeRequestId) {
        // Get change request name
        String changeRequestName = null;
        if (changeRequestId != null) {
            try {
                ChangeRequest cr = changeRequestDAO.getChangeRequestById(changeRequestId);
                if (cr != null) {
                    changeRequestName = cr.getPrimaryName();
                }
            } catch (SQLException e) {
                logger.warn("Could not fetch change request name for ID {}: {}", changeRequestId, e.getMessage());
            }
        }
        
        String crDisplayName = (changeRequestName != null && !changeRequestName.trim().isEmpty()) 
            ? escapeHtml(changeRequestName) 
            : ("Change Request #" + changeRequestId);

        // Icon SVG
        String crIcon = "<svg width='20' height='20' viewBox='0 0 24 24' fill='none' stroke='#0d9488' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'></circle><polyline points='12 6 12 12 16 14'></polyline></svg>";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<style>");
        html.append("  * { box-sizing: border-box; }");
        html.append("  body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f0f4f8; line-height: 1.6; }");
        html.append("  .email-wrapper { padding: 20px 0; background-color: #f0f4f8; }");
        html.append("  .email-container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08); }");
        html.append("  .header { background: linear-gradient(135deg, #0d9488 0%, #0891b2 50%, #06b6d4 100%); padding: 40px 30px; text-align: center; position: relative; }");
        html.append("  .header::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 4px; background: linear-gradient(90deg, rgba(255,255,255,0.3) 0%, rgba(255,255,255,0.1) 100%); }");
        html.append("  .logo { font-size: 32px; font-weight: 700; color: #ffffff; margin-bottom: 8px; letter-spacing: 2px; text-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        html.append("  .app-name { font-size: 14px; color: #ffffff; opacity: 0.95; letter-spacing: 1.5px; font-weight: 400; text-transform: uppercase; }");
        html.append("  .content { padding: 40px 30px; }");
        html.append("  .title { font-size: 24px; color: #0f172a; margin-bottom: 20px; font-weight: 600; line-height: 1.3; }");
        html.append("  .message { color: #475569; line-height: 1.7; margin-bottom: 28px; font-size: 15px; }");
        html.append("  .button-container { margin-top: 28px; text-align: center; }");
        html.append("  .button { display: inline-flex; align-items: center; gap: 8px; background: linear-gradient(135deg, #0d9488 0%, #0891b2 100%); color: #ffffff; padding: 14px 32px; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 15px; box-shadow: 0 4px 12px rgba(13, 148, 136, 0.3); transition: all 0.3s ease; letter-spacing: 0.3px; }");
        html.append("  .button:hover { background: linear-gradient(135deg, #0891b2 0%, #06b6d4 100%); box-shadow: 0 6px 16px rgba(13, 148, 136, 0.4); transform: translateY(-1px); }");
        html.append("  .button-icon { flex-shrink: 0; }");
        html.append("  .footer { background-color: #f8fafc; padding: 30px 20px; text-align: center; border-top: 1px solid #e2e8f0; }");
        html.append("  .footer-text { color: #64748b; font-size: 13px; margin: 8px 0; line-height: 1.6; }");
        html.append("  .footer-links { margin: 16px 0; }");
        html.append("  .footer-link { color: #0d9488; text-decoration: none; margin: 0 12px; font-size: 13px; font-weight: 500; transition: color 0.2s; }");
        html.append("  .footer-link:hover { color: #0891b2; text-decoration: underline; }");
        html.append("  .footer-brand { margin-top: 20px; padding-top: 20px; border-top: 1px solid #e2e8f0; }");
        html.append("  .footer-brand-text { color: #94a3b8; font-size: 11px; letter-spacing: 0.5px; text-transform: uppercase; }");
        html.append("  @media only screen and (max-width: 600px) {");
        html.append("    .email-wrapper { padding: 0; }");
        html.append("    .email-container { border-radius: 0; box-shadow: none; }");
        html.append("    .header { padding: 30px 20px; }");
        html.append("    .logo { font-size: 28px; }");
        html.append("    .app-name { font-size: 12px; }");
        html.append("    .content { padding: 30px 20px; }");
        html.append("    .title { font-size: 22px; margin-bottom: 18px; }");
        html.append("    .message { font-size: 14px; margin-bottom: 24px; }");
        html.append("    .button { display: flex; width: 100%; justify-content: center; padding: 14px 24px; box-sizing: border-box; }");
        html.append("    .footer { padding: 24px 16px; }");
        html.append("    .footer-link { display: inline-block; margin: 4px 8px; }");
        html.append("  }");
        html.append("</style>");
        html.append("</head><body>");
        html.append("<div class='email-wrapper'>");
        html.append("<div class='email-container'>");
        
        // Header with logo and app name
        html.append("<div class='header'>");
        html.append("<div class='logo'>BUDG</div>");
        html.append("<div class='app-name'>Business Unified Data Governance</div>");
        html.append("</div>");
        
        // Content
        html.append("<div class='content'>");
        html.append("<div class='title'>").append(escapeHtml(title)).append("</div>");
        html.append("<div class='message'>").append(escapeHtml(message)).append("</div>");
        
        if (changeRequestId != null) {
            html.append("<div class='button-container'>");
            html.append("<a href='").append(getChangeRequestUrl(changeRequestId))
                    .append("' class='button'><span class='button-icon'>").append(crIcon).append("</span>View ").append(crDisplayName).append("</a>");
            html.append("</div>");
        }
        
        html.append("</div>");
        
        // Footer
        html.append("<div class='footer'>");
        html.append("<p class='footer-text'>This is an automated notification from BUDG Platform.</p>");
        html.append("<div class='footer-links'>");
        html.append("<a href='/view/my-requests' class='footer-link'>My Change Requests</a>");
        html.append("<a href='/settings/notifications' class='footer-link'>Notification Settings</a>");
        html.append("</div>");
        html.append("<div class='footer-brand'>");
        html.append("<p class='footer-brand-text'>Powered by BUSSMA COMPANY S.M.E.</p>");
        html.append("</div>");
        html.append("</div>");
        
        html.append("</div>");
        html.append("</div>");
        html.append("</body></html>");
        return html.toString();
    }
}
