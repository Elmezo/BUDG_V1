package com.example.budg_v2.util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.MDC;

/**
 * Custom filter for audit logs.
 * Accepts only log entries that have [AUDIT] prefix in the message
 * or have audit=true in MDC context.
 */
public class AuditLogFilter extends Filter<ILoggingEvent> {
    
    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event == null) {
            return FilterReply.DENY;
        }
        
        // Check if audit flag is set in MDC
        String auditFlag = MDC.get("audit");
        if ("true".equals(auditFlag)) {
            return FilterReply.ACCEPT;
        }
        
        // Check if message contains [AUDIT] prefix
        String message = event.getFormattedMessage();
        if (message != null && message.contains("[AUDIT]")) {
            return FilterReply.ACCEPT;
        }
        
        // Deny all other logs
        return FilterReply.DENY;
    }
}

