package com.example.budg_v2.filter;

import com.example.budg_v2.util.AppErrorCode;
import com.example.budg_v2.util.BudgLogger;
import jakarta.servlet.*;
import org.slf4j.MDC;

import java.io.IOException;

/**
 * Fail-safe logging layer — catches any exception that was not already handled
 * by a servlet or service.
 *
 * <p>Position in the filter chain (enforced by {@code web.xml}):
 * <pre>
 * CorrelationIdFilter  → sets request_id / client_ip / user_agent
 * SecurityHeadersFilter
 * AuthFilter           → sets user_id / user_email
 * GlobalExceptionFilter ← HERE
 * Servlets
 * </pre>
 *
 * <p>Duplicate-logging guard: if a servlet already called
 * {@link BudgLogger#logError(String, String, String, String, String, String, BudgLogger.ErrorType, Throwable)}
 * (structured API), that method sets {@code MDC["exception_handled"] = "true"}.
 * This filter skips logging when that sentinel is present, so the same exception
 * is never recorded twice.
 *
 * <p>The exception is always re-thrown so the container still sends the normal
 * HTTP 500 response to the client.
 *
 * <p>Registration: declared only in {@code web.xml} — no {@code @WebFilter}
 * annotation — to guarantee position in the filter order.
 */
public class GlobalExceptionFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (Exception ex) {
            // Only log if no structured BudgLogger.logError call already handled this exception.
            // The sentinel MDC key is set by BudgLogger.logError (structured API) and
            // cleared automatically when CorrelationIdFilter calls MDC.clear() at request end.
            if (MDC.get(BudgLogger.EXCEPTION_HANDLED_KEY) == null) {
                String userId    = MDC.get("user_id");
                String requestId = MDC.get("request_id");

                BudgLogger.logError(
                    "GLOBAL",
                    "UNHANDLED_EXCEPTION",
                    userId != null ? userId : "unknown",
                    AppErrorCode.SYS_500.getCode(),
                    "Unhandled exception — " + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    "Check prod_errors.log with request_id=" + (requestId != null ? requestId : "n/a"),
                    BudgLogger.ErrorType.SYSTEM,
                    ex
                );
            }
            throw ex;
        }
    }

    @Override
    public void destroy() {
        // no-op
    }
}
