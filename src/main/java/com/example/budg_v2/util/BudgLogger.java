package com.example.budg_v2.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * BUDG unified logging facade built on SLF4J + Logback.
 *
 * <h2>Structured API (preferred for new code)</h2>
 * <pre>{@code
 * long start = BudgLogger.now();
 *
 * BudgLogger.logInfo   ("AUTH_SERVICE", "LOGIN", userId, "Login attempt started");
 * BudgLogger.logSuccess("AUTH_SERVICE", "LOGIN", userId, "Login successful", start);
 * BudgLogger.logError  ("AUTH_SERVICE", "LOGIN", userId,
 *                        AppErrorCode.AUTH_10258.getCode(),
 *                        AppErrorCode.AUTH_10258.getDescription(),
 *                        "Verify SAML userprincipalname matches LDAP account",
 *                        ErrorType.BUSINESS, ex);
 *
 * // System / infrastructure events (no user context needed)
 * BudgLogger.logSystemEvent("DB_SERVICE", "STARTUP", LogStatus.COMPLETED, "Pool ready");
 * }</pre>
 *
 * <h2>Every log line carries these MDC fields</h2>
 * <pre>
 * request_id   — UUID per HTTP request (set by CorrelationIdFilter)
 * duration_ms  — total request duration (set by CorrelationIdFilter)
 *              — individual operation duration (set by logSuccess / logError when startTimeMs given)
 * service      — e.g. "AUTH_SERVICE"
 * action       — e.g. "LOGIN"
 * status       — STARTED | COMPLETED | FAILED | WARNING
 * user_id      — authenticated user ID
 * error_code   — e.g. "AUTH_10258"
 * error_type   — BUSINESS | SYSTEM
 * cause        — root cause description
 * solution     — suggested fix
 * </pre>
 *
 * <h2>Legacy API (backward-compatible, existing call-sites still work)</h2>
 * {@code logInfo(message, kv(...))} / {@code logError(message, Throwable, kv(...))}
 */
public class BudgLogger {

    private static final Logger logger = LoggerFactory.getLogger(BudgLogger.class);

    // SLF4J markers — used for Logback routing / filtering
    public static final Marker SUCCESS_MARKER = MarkerFactory.getMarker("SUCCESS");
    public static final Marker SYSTEM_MARKER  = MarkerFactory.getMarker("SYSTEM");

    // MDC keys
    private static final String SERVICE_KEY        = "service";
    private static final String ACTION_KEY         = "action";
    private static final String STATUS_KEY         = "status";
    private static final String ERROR_CODE_KEY     = "error_code";
    private static final String ERROR_TYPE_KEY     = "error_type";
    private static final String CAUSE_KEY          = "cause";
    private static final String SOLUTION_KEY       = "solution";
    private static final String DURATION_MS_KEY        = "duration_ms";
    /** Sentinel set by the structured logError — read by GlobalExceptionFilter to skip duplicate logs. */
    public static final String EXCEPTION_HANDLED_KEY = "exception_handled";
    private static final String CORRELATION_ID_KEY = "correlation_id";
    private static final String COMPONENT_KEY      = "component";
    private static final String MODULE_KEY         = "module";
    private static final String USER_ID_KEY        = "user_id";
    private static final String USER_EMAIL_KEY     = "user_email";
    private static final String ENVIRONMENT_KEY    = "environment";
    private static final String SOURCE_KEY         = "source";

    // Defaults
    private static final String DEFAULT_COMPONENT        = "BUDG-core";
    private static final int    DEFAULT_STACKTRACE_MAX   = 5000;
    private static final int    STACKTRACE_FIRST_LINES   = 20;
    private static final int    STACKTRACE_LAST_LINES    = 10;

    // =========================================================================
    // Public enums
    // =========================================================================

    /**
     * Whether an error originates from user/business input or an infrastructure fault.
     * Drives "most errors are from users vs system?" dashboards.
     */
    public enum ErrorType {
        /** Wrong input, missing account, business-rule violation — user-fixable. */
        BUSINESS,
        /** DB down, NPE, config missing, network error — ops-fixable. */
        SYSTEM
    }

    /**
     * Fixed set of allowed status values (prevents typos in MDC).
     */
    public enum LogStatus {
        STARTED, COMPLETED, FAILED, WARNING
    }

    // =========================================================================
    // Convenience
    // =========================================================================

    /** Returns current epoch millis — use as {@code startTimeMs} for duration tracking. */
    public static long now() {
        return System.currentTimeMillis();
    }

    // =========================================================================
    // Structured API
    // =========================================================================

    /**
     * INFO — action started.
     *
     * <pre>[INFO] [AUTH_SERVICE] [LOGIN] [STARTED] [user123] Login attempt started</pre>
     *
     * @param service Service name  (e.g. "AUTH_SERVICE")
     * @param action  Action name   (e.g. "LOGIN")
     * @param userId  User identifier
     * @param message Human-readable message
     */
    public static void logInfo(String service, String action, String userId, String message) {
        setStructuredContext(service, action, LogStatus.STARTED, userId, -1);
        logger.info("INFO: {} started by user {} | {}",
                action, userId, SensitiveDataMasker.maskMessage(message));
        clearStructuredContext();
    }

    /**
     * SUCCESS — action completed.
     * Maps to INFO level with a {@code SUCCESS} marker for Logback routing.
     *
     * <pre>[SUCCESS] [AUTH_SERVICE] [LOGIN] [COMPLETED] [user123] Login successful (45ms)</pre>
     *
     * @param service      Service name
     * @param action       Action name
     * @param userId       User identifier
     * @param message      Human-readable message
     * @param startTimeMs  Value of {@link #now()} captured before the operation; pass {@code -1} to omit duration
     */
    public static void logSuccess(String service, String action, String userId,
                                  String message, long startTimeMs) {
        setStructuredContext(service, action, LogStatus.COMPLETED, userId, startTimeMs);
        logger.info(SUCCESS_MARKER,
                "SUCCESS: {} completed successfully for user {} | {}",
                action, userId, SensitiveDataMasker.maskMessage(message));
        clearStructuredContext();
    }

    /** SUCCESS — without duration tracking. */
    public static void logSuccess(String service, String action, String userId, String message) {
        logSuccess(service, action, userId, message, -1);
    }

    /**
     * WARN — suspicious state.
     *
     * @param service  Service name
     * @param action   Action name
     * @param userId   User identifier
     * @param message  Human-readable message
     */
    public static void logWarn(String service, String action, String userId, String message) {
        setStructuredContext(service, action, LogStatus.WARNING, userId, -1);
        logger.warn("WARNING: {} | User: {} | {}",
                action, userId, SensitiveDataMasker.maskMessage(message));
        clearStructuredContext();
    }

    /**
     * ERROR — full structured failure with error code, root cause, suggested fix,
     * and BUSINESS / SYSTEM classification.
     *
     * <pre>
     * ERROR: AUTH_10258 - LOGIN | User: user123 | Cause: User not found in LDAP
     * | Solution: Verify SAML mapping | error_type=BUSINESS
     * </pre>
     *
     * @param service    Service name
     * @param action     Action name
     * @param userId     User identifier
     * @param errorCode  Error code — use {@link AppErrorCode#getCode()}
     * @param cause      Root cause (human-readable)
     * @param solution   Suggested fix for support / end user
     * @param errorType  {@link ErrorType#BUSINESS} or {@link ErrorType#SYSTEM}
     * @param startTimeMs Value of {@link #now()} captured before the operation; {@code -1} to omit
     * @param ex         Underlying exception (may be null)
     */
    public static void logError(String service, String action, String userId,
                                String errorCode, String cause, String solution,
                                ErrorType errorType, long startTimeMs, Throwable ex) {
        setStructuredContext(service, action, LogStatus.FAILED, userId, startTimeMs);
        MDC.put(EXCEPTION_HANDLED_KEY, "true");
        MDC.put(ERROR_CODE_KEY, errorCode  != null ? errorCode              : "UNKNOWN");
        MDC.put(ERROR_TYPE_KEY, errorType  != null ? errorType.name()       : ErrorType.SYSTEM.name());
        MDC.put(CAUSE_KEY,      cause      != null ? cause                  : "");
        MDC.put(SOLUTION_KEY,   solution   != null ? solution               : "");
        setExceptionContext(ex);

        String msg = String.format(
                "ERROR: %s - %s | User: %s | Cause: %s | Solution: %s | error_type=%s",
                errorCode, action, userId,
                SensitiveDataMasker.maskMessage(cause    != null ? cause    : ""),
                SensitiveDataMasker.maskMessage(solution != null ? solution : ""),
                errorType != null ? errorType.name() : ErrorType.SYSTEM.name());

        if (ex != null) {
            logger.error(msg, ex);
        } else {
            logger.error(msg);
        }

        clearExceptionContext();
        MDC.remove(ERROR_CODE_KEY);
        MDC.remove(ERROR_TYPE_KEY);
        MDC.remove(CAUSE_KEY);
        MDC.remove(SOLUTION_KEY);
        clearStructuredContext();
    }

    /** ERROR — without duration tracking. */
    public static void logError(String service, String action, String userId,
                                String errorCode, String cause, String solution,
                                ErrorType errorType, Throwable ex) {
        logError(service, action, userId, errorCode, cause, solution, errorType, -1, ex);
    }

    /** ERROR — defaults to {@link ErrorType#SYSTEM} (backward-compatible). */
    public static void logError(String service, String action, String userId,
                                String errorCode, String cause, String solution, Throwable ex) {
        logError(service, action, userId, errorCode, cause, solution, ErrorType.SYSTEM, -1, ex);
    }

    /**
     * SYSTEM event — Tomcat startup, DB connection, service init, health checks.
     * No user context required.
     *
     * <pre>[INFO] SYSTEM: [DB_SERVICE] [STARTUP] [COMPLETED] Connection pool ready</pre>
     *
     * @param service  Service name  (e.g. "TOMCAT", "DB_SERVICE")
     * @param action   Action name   (e.g. "STARTUP", "HEALTH_CHECK")
     * @param status   {@link LogStatus} value
     * @param message  Human-readable message
     */
    public static void logSystemEvent(String service, String action, LogStatus status, String message) {
        MDC.put(SERVICE_KEY, service != null ? service       : "SYSTEM");
        MDC.put(ACTION_KEY,  action  != null ? action        : "");
        MDC.put(STATUS_KEY,  status  != null ? status.name() : LogStatus.COMPLETED.name());
        logger.info(SYSTEM_MARKER,
                "SYSTEM: [{}] [{}] [{}] {}",
                service, action, status != null ? status.name() : "COMPLETED",
                SensitiveDataMasker.maskMessage(message));
        clearStructuredContext();
    }

    // =========================================================================
    // Legacy API — preserved for backward compatibility
    // =========================================================================

    /**
     * Legacy ERROR with exception and key-value context.
     *
     * @param message   Log message
     * @param exception Exception (may be null)
     * @param keyValues Optional MDC key-value pairs
     */
    public static void logError(String message, Throwable exception, KeyValue... keyValues) {
        setContext(keyValues);
        setExceptionContext(exception);
        String masked = SensitiveDataMasker.maskMessage(message);
        if (exception != null) {
            logger.error(masked, exception);
        } else {
            logger.error(masked);
        }
        clearExceptionContext();
    }

    /** Legacy ERROR without exception. */
    public static void logError(String message, KeyValue... keyValues) {
        logError(message, null, keyValues);
    }

    /** Legacy WARN with key-value context. */
    public static void logWarn(String message, KeyValue... keyValues) {
        setContext(keyValues);
        logger.warn(SensitiveDataMasker.maskMessage(message));
    }

    /** Legacy INFO with key-value context. */
    public static void logInfo(String message, KeyValue... keyValues) {
        setContext(keyValues);
        logger.info(SensitiveDataMasker.maskMessage(message));
    }

    /** Legacy DEBUG with key-value context. */
    public static void logDebug(String message, KeyValue... keyValues) {
        setContext(keyValues);
        logger.debug(SensitiveDataMasker.maskMessage(message));
    }

    /**
     * AUDIT event — written with an [AUDIT] prefix for compliance appenders.
     *
     * @param message   Audit message
     * @param keyValues Optional MDC key-value pairs
     */
    public static void logAudit(String message, KeyValue... keyValues) {
        setContext(keyValues);
        MDC.put("audit", "true");
        logger.info("[AUDIT] " + SensitiveDataMasker.maskMessage(message));
        MDC.remove("audit");
    }

    // =========================================================================
    // MDC helpers — public, used by servlets / filters
    // =========================================================================

    /**
     * Fluent key-value builder. Also writes the value into MDC immediately.
     *
     * <pre>{@code BudgLogger.logInfo("msg", BudgLogger.kv("module", "GlossaryServlet")); }</pre>
     */
    public static KeyValue kv(String key, Object value) {
        if (key != null && value != null) {
            MDC.put(key, SensitiveDataMasker.maskValue(key, value));
        }
        return new KeyValue(key, value);
    }

    /**
     * Populate MDC with HTTP request context.
     * Call this at the top of servlet {@code doGet}/{@code doPost}.
     */
    public static void setRequestData(HttpServletRequest request) {
        if (request == null) return;
        MDC.put("request_method", request.getMethod());
        MDC.put("request_uri", request.getRequestURI());
        String qs = request.getQueryString();
        MDC.put("request_get_parameters",
                qs != null ? SensitiveDataMasker.maskMessage(qs) : "");
        MDC.put("request_post_parameters", "");
    }

    /**
     * Populate MDC with authenticated user context.
     * Call this after authentication succeeds.
     */
    public static void setUserData(Object userId, Object userEmail) {
        if (userId    != null) MDC.put(USER_ID_KEY,    userId.toString());
        if (userEmail != null) MDC.put(USER_EMAIL_KEY, userEmail.toString());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static void setStructuredContext(String service, String action,
                                             LogStatus status, String userId,
                                             long startTimeMs) {
        MDC.put(SERVICE_KEY, service != null ? service       : "");
        MDC.put(ACTION_KEY,  action  != null ? action        : "");
        MDC.put(STATUS_KEY,  status  != null ? status.name() : "");
        if (userId != null) {
            MDC.put(USER_ID_KEY, userId);
        } else if (MDC.get(USER_ID_KEY) == null) {
            MDC.put(USER_ID_KEY, "public");
        }
        if (MDC.get(COMPONENT_KEY) == null) {
            MDC.put(COMPONENT_KEY, DEFAULT_COMPONENT);
        }
        if (startTimeMs > 0) {
            MDC.put(DURATION_MS_KEY, String.valueOf(now() - startTimeMs));
        }
    }

    private static void clearStructuredContext() {
        MDC.remove(SERVICE_KEY);
        MDC.remove(ACTION_KEY);
        MDC.remove(STATUS_KEY);
        MDC.remove(DURATION_MS_KEY);
    }

    private static void setContext(KeyValue... keyValues) {
        if (keyValues != null) {
            for (KeyValue kv : keyValues) {
                if (kv != null && kv.key != null && kv.value != null) {
                    MDC.put(kv.key, SensitiveDataMasker.maskValue(kv.key, kv.value));
                }
            }
        }
        if (MDC.get(COMPONENT_KEY) == null) {
            MDC.put(COMPONENT_KEY, DEFAULT_COMPONENT);
        }
        if (MDC.get(USER_ID_KEY) == null) {
            MDC.put(USER_ID_KEY, "public");
        }
    }

    private static void setExceptionContext(Throwable ex) {
        if (ex == null) return;
        MDC.put("exception_type", ex.getClass().getName());
        String msg = ex.getMessage();
        if (msg != null) {
            MDC.put("exception_message", SensitiveDataMasker.maskMessage(msg));
        }
        MDC.put("stack_trace", truncateStackTrace(ex));
    }

    private static void clearExceptionContext() {
        MDC.remove("exception_type");
        MDC.remove("exception_message");
        MDC.remove("stack_trace");
    }

    private static String truncateStackTrace(Throwable ex) {
        if (ex == null) return null;
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String full = sw.toString();
        int max = maxStackTraceLength();
        if (full.length() <= max) return full;

        String[] lines = full.split("\n");
        if (lines.length <= STACKTRACE_FIRST_LINES + STACKTRACE_LAST_LINES) {
            return full.substring(0, max - 20) + "... (truncated)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < STACKTRACE_FIRST_LINES; i++) sb.append(lines[i]).append("\n");
        sb.append("... (truncated ")
          .append(lines.length - STACKTRACE_FIRST_LINES - STACKTRACE_LAST_LINES)
          .append(" lines) ...\n");
        int startLast = Math.max(STACKTRACE_FIRST_LINES, lines.length - STACKTRACE_LAST_LINES);
        for (int i = startLast; i < lines.length; i++) {
            sb.append(lines[i]);
            if (i < lines.length - 1) sb.append("\n");
        }
        String result = sb.toString();
        return result.length() > max ? result.substring(0, max - 20) + "... (truncated)" : result;
    }

    private static int maxStackTraceLength() {
        String val = System.getProperty("log.stacktrace.max.length");
        if (val != null) {
            try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_STACKTRACE_MAX;
    }

    // =========================================================================
    // KeyValue — used by the legacy API
    // =========================================================================

    /** Immutable key-value pair for the legacy vararg logging API. */
    public static class KeyValue {
        final String key;
        final Object value;
        KeyValue(String key, Object value) {
            this.key   = key;
            this.value = value;
        }
    }
}
