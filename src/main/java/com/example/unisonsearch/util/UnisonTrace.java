package com.example.unisonsearch.util;

/**
 * Optional verbose logging for Unison Search (request + SQL seeds + facet counts).
 * <ul>
 *   <li>JVM: {@code -Dbudg.unison.trace=true}</li>
 *   <li>Environment: {@code BUDG_UNISON_TRACE=1}</li>
 *   <li>Per request: header {@code X-Unison-Trace: 1} (servlet sets thread-local)</li>
 * </ul>
 */
public final class UnisonTrace {

    private static final ThreadLocal<Boolean> REQUEST_FORCE = new ThreadLocal<>();

    private UnisonTrace() {
    }

    public static boolean enabled() {
        if (Boolean.TRUE.equals(REQUEST_FORCE.get())) {
            return true;
        }
        String p = System.getProperty("budg.unison.trace");
        if (p != null && ("true".equalsIgnoreCase(p) || "1".equals(p))) {
            return true;
        }
        String e = System.getenv("BUDG_UNISON_TRACE");
        return e != null && ("1".equals(e) || "true".equalsIgnoreCase(e));
    }

    /** Enable tracing for the current request thread only (cleared in servlet finally). */
    public static void setRequestForceTrace(boolean on) {
        if (on) {
            REQUEST_FORCE.set(Boolean.TRUE);
        } else {
            REQUEST_FORCE.remove();
        }
    }

    public static void clearRequestTrace() {
        REQUEST_FORCE.remove();
    }

    public static void log(String correlationId, String phase, String detail) {
        if (!enabled()) {
            return;
        }
        String cid = (correlationId != null && !correlationId.isEmpty()) ? correlationId : "-";
        System.out.println("[UnisonTrace] " + cid + " | " + phase + " | " + detail);
    }
}
