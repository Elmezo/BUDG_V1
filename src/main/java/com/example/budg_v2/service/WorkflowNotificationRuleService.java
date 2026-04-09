package com.example.budg_v2.service;

import com.example.budg_v2.dao.WorkflowNotificationRuleDAO;
import com.example.budg_v2.model.WorkflowNotificationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Service for managing workflow notification rules
 */
public class WorkflowNotificationRuleService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowNotificationRuleService.class);
    private final WorkflowNotificationRuleDAO ruleDAO;

    // Valid channels
    private static final List<String> VALID_CHANNELS = Arrays.asList("email", "ui", "sms");

    public WorkflowNotificationRuleService() {
        this.ruleDAO = new WorkflowNotificationRuleDAO();
    }

    /**
     * Create a new notification rule with validation
     */
    public Long createRule(WorkflowNotificationRule rule) throws SQLException, IllegalArgumentException {
        validateRule(rule);
        return ruleDAO.create(rule);
    }

    /**
     * Update an existing notification rule
     */
    public void updateRule(Long id, WorkflowNotificationRule rule) throws SQLException, IllegalArgumentException {
        if (id == null) {
            throw new IllegalArgumentException("Rule ID cannot be null");
        }
        rule.setId(id);
        validateRule(rule);
        ruleDAO.update(rule);
    }

    /**
     * Delete a notification rule
     */
    public void deleteRule(Long id) throws SQLException {
        if (id == null) {
            throw new IllegalArgumentException("Rule ID cannot be null");
        }
        ruleDAO.delete(id);
    }

    /**
     * Get all notification rules
     */
    public List<WorkflowNotificationRule> getAllRules() throws SQLException {
        return ruleDAO.findAll();
    }

    /**
     * Get rules for a specific event (module and event type)
     */
    public List<WorkflowNotificationRule> getRulesForEvent(String module, String eventType) throws SQLException {
        if (module == null || module.isEmpty()) {
            throw new IllegalArgumentException("Module cannot be null or empty");
        }
        if (eventType == null || eventType.isEmpty()) {
            throw new IllegalArgumentException("Event type cannot be null or empty");
        }
        return ruleDAO.findActiveRulesForEvent(module, eventType);
    }

    /**
     * Validate notification rule
     */
    public void validateRule(WorkflowNotificationRule rule) throws IllegalArgumentException {
        if (rule == null) {
            throw new IllegalArgumentException("Rule cannot be null");
        }

        // Validate module
        if (rule.getModule() == null || rule.getModule().trim().isEmpty()) {
            throw new IllegalArgumentException("Module cannot be null or empty");
        }

        // Validate event type
        if (rule.getEventType() == null || rule.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type cannot be null or empty");
        }
        if (!Arrays.asList("ASSIGN", "OVERDUE", "ESCALATION").contains(rule.getEventType())) {
            throw new IllegalArgumentException("Invalid event type: " + rule.getEventType());
        }

        // Validate channels
        if (rule.getChannels() == null || rule.getChannels().isEmpty()) {
            throw new IllegalArgumentException("At least one channel must be specified");
        }
        for (String channel : rule.getChannels()) {
            if (!VALID_CHANNELS.contains(channel.toLowerCase())) {
                throw new IllegalArgumentException("Invalid channel: " + channel + ". Valid channels are: " + VALID_CHANNELS);
            }
        }

        // Validate recipient (either role or user must be specified, or both can be null for role-based from task)
        // For ASSIGN and OVERDUE, recipient can be null (will use task role)
        // For ESCALATION, recipient_role is typically required
        if (rule.getRecipientRole() == null && rule.getRecipientUserId() == null) {
            // This is allowed for ASSIGN and OVERDUE events
            if (!"ASSIGN".equals(rule.getEventType()) && !"OVERDUE".equals(rule.getEventType())) {
                logger.warn("Rule for event {} has no recipient specified", rule.getEventType());
            }
        }

        logger.debug("Rule validation passed for module: {}, event: {}", rule.getModule(), rule.getEventType());
    }

    /**
     * Check for duplicate rules
     */
    public boolean isDuplicate(WorkflowNotificationRule rule, Long excludeId) throws SQLException {
        try {
            List<WorkflowNotificationRule> existing = ruleDAO.findByModuleAndEventType(
                    rule.getModule(), rule.getEventType());
            
            for (WorkflowNotificationRule existingRule : existing) {
                if (excludeId != null && existingRule.getId().equals(excludeId)) {
                    continue; // Skip the rule being updated
                }
                
                // Check if recipient matches
                boolean recipientMatch = false;
                if (rule.getRecipientRole() != null && existingRule.getRecipientRole() != null) {
                    recipientMatch = rule.getRecipientRole().equals(existingRule.getRecipientRole());
                } else if (rule.getRecipientRole() == null && existingRule.getRecipientRole() == null) {
                    recipientMatch = true;
                }
                
                if (rule.getRecipientUserId() != null && existingRule.getRecipientUserId() != null) {
                    recipientMatch = recipientMatch && rule.getRecipientUserId().equals(existingRule.getRecipientUserId());
                } else if (rule.getRecipientUserId() == null && existingRule.getRecipientUserId() == null) {
                    recipientMatch = recipientMatch && true;
                } else {
                    recipientMatch = false;
                }
                
                if (recipientMatch) {
                    return true; // Duplicate found
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking for duplicate rules", e);
            throw e;
        }
        return false;
    }
}

