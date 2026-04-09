package com.example.budg_v2.util;

/**
 * Configuration for workflow SLA defaults
 * Can be overridden via system properties
 */
public class WorkflowSlaConfig {

    // Default due days if not specified in BPMN
    private static final String DEFAULT_DUE_DAYS_PROP = "workflow.sla.default.dueDays";
    private static final int DEFAULT_DUE_DAYS = 5;

    // Default escalation days if not specified in BPMN
    private static final String DEFAULT_ESCALATION_DAYS_PROP = "workflow.sla.default.escalationDays";
    private static final Integer DEFAULT_ESCALATION_DAYS = null; // No escalation by default

    // SLA scheduler interval in minutes
    private static final String SCHEDULER_INTERVAL_PROP = "workflow.sla.scheduler.interval.minutes";
    private static final int DEFAULT_SCHEDULER_INTERVAL_MINUTES = 15;
    private static final int MIN_SCHEDULER_INTERVAL_MINUTES = 5;

    /**
     * Get default due days (from system property or default value)
     */
    public static int getDefaultDueDays() {
        String prop = System.getProperty(DEFAULT_DUE_DAYS_PROP);
        if (prop != null) {
            try {
                int value = Integer.parseInt(prop.trim());
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // Invalid value, use default
            }
        }
        return DEFAULT_DUE_DAYS;
    }

    /**
     * Get default escalation days (from system property or default value)
     */
    public static Integer getDefaultEscalationDays() {
        String prop = System.getProperty(DEFAULT_ESCALATION_DAYS_PROP);
        if (prop != null && !prop.trim().isEmpty()) {
            try {
                int value = Integer.parseInt(prop.trim());
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // Invalid value, use default
            }
        }
        return DEFAULT_ESCALATION_DAYS;
    }

    /**
     * Get scheduler interval in minutes (from system property or default value)
     */
    public static int getSchedulerIntervalMinutes() {
        String prop = System.getProperty(SCHEDULER_INTERVAL_PROP);
        if (prop != null) {
            try {
                int value = Integer.parseInt(prop.trim());
                if (value >= MIN_SCHEDULER_INTERVAL_MINUTES) {
                    return value;
                } else {
                    // Use minimum if value is too low
                    return MIN_SCHEDULER_INTERVAL_MINUTES;
                }
            } catch (NumberFormatException e) {
                // Invalid value, use default
            }
        }
        return DEFAULT_SCHEDULER_INTERVAL_MINUTES;
    }
}

