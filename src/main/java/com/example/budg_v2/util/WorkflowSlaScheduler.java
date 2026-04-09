package com.example.budg_v2.util;

import com.example.budg_v2.dao.WorkflowTaskDAO;
import com.example.budg_v2.model.WorkflowTask;
import com.example.budg_v2.service.WorkflowNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Timestamp;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Scheduled job for evaluating and updating SLA status for workflow tasks
 * Runs periodically to check for overdue tasks and escalations
 */
@WebListener
public class WorkflowSlaScheduler implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowSlaScheduler.class);
    private Timer timer;
    private final WorkflowTaskDAO taskDAO = new WorkflowTaskDAO();
    private final WorkflowNotificationService notificationService = new WorkflowNotificationService();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        int intervalMinutes = WorkflowSlaConfig.getSchedulerIntervalMinutes();
        long intervalMillis = intervalMinutes * 60L * 1000L;
        // Capture webapp classloader so timer thread can use it (avoids NoClassDefFoundError in background threads)
        final ClassLoader webappClassLoader = Thread.currentThread().getContextClassLoader();

        timer = new Timer("workflow-sla-scheduler", true);
        // Run every X minutes
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                ClassLoader prev = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(webappClassLoader);
                    evaluateSla();
                } finally {
                    Thread.currentThread().setContextClassLoader(prev);
                }
            }
        }, intervalMillis, intervalMillis); // Start after first interval, then repeat

        logger.info("Workflow SLA scheduler started with interval: {} minutes", intervalMinutes);
    }

    /**
     * Evaluate SLA for all active tasks
     */
    private void evaluateSla() {
        try {
            List<WorkflowTask> activeTasks = taskDAO.findActiveTasksForSlaEvaluation();
            logger.debug("Evaluating SLA for {} active tasks", activeTasks.size());

            Timestamp now = new Timestamp(System.currentTimeMillis());
            int overdueCount = 0;
            int escalatedCount = 0;

            for (WorkflowTask task : activeTasks) {
                try {
                    boolean isOverdue = task.getIsOverdue() != null && task.getIsOverdue();
                    Timestamp escalatedAt = task.getEscalatedAt();

                    // Check if task is overdue
                    if (task.getDueAt() != null && now.after(task.getDueAt()) && !isOverdue) {
                        // Mark as overdue
                        task.setIsOverdue(true);
                        taskDAO.updateSlaStatus(task.getId(), true, null);
                        notificationService.sendOverdueNotification(task);
                        overdueCount++;
                        logger.info("Task {} marked as overdue (Due: {})", task.getId(), task.getDueAt());
                    }

                    // Check for escalation (only if overdue and not already escalated)
                    if (task.getIsOverdue() != null && task.getIsOverdue() && escalatedAt == null) {
                        // Get escalation days from BPMN (would need to load BPMN, but for now use config default)
                        // For Phase 4, we'll check if escalation threshold is reached
                        // Escalation threshold = Due_At + escalationDays
                        // Since we don't store escalationDays per task, we'll use config default
                        Integer escalationDays = WorkflowSlaConfig.getDefaultEscalationDays();
                        
                        if (escalationDays != null && escalationDays > 0 && task.getDueAt() != null) {
                            long escalationThreshold = task.getDueAt().getTime() + (escalationDays * 24L * 60 * 60 * 1000);
                            Timestamp escalationThresholdTime = new Timestamp(escalationThreshold);
                            
                            if (now.after(escalationThresholdTime)) {
                                // Escalation threshold reached
                                task.setEscalatedAt(now);
                                taskDAO.updateSlaStatus(task.getId(), true, now);
                                notificationService.sendEscalationNotification(task);
                                escalatedCount++;
                                logger.info("Task {} escalated (Due: {}, Escalation threshold: {})", 
                                        task.getId(), task.getDueAt(), escalationThresholdTime);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error evaluating SLA for task: {}", task.getId(), e);
                }
            }

            if (overdueCount > 0 || escalatedCount > 0) {
                logger.info("SLA evaluation completed: {} tasks marked overdue, {} tasks escalated", 
                        overdueCount, escalatedCount);
            }
        } catch (Exception e) {
            logger.error("Error in SLA evaluation", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (timer != null) {
            try {
                timer.cancel();
            } catch (Exception ignored) {
            }
        }
        logger.info("Workflow SLA scheduler stopped");
    }
}

