package com.example.budg_v2.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @deprecated Use {@link BudgLogger} instead.
 * This class is a thin wrapper kept for backward compatibility only.
 * All new code should call {@code BudgLogger} directly.
 */
@Deprecated
public class AxonLogger {

    /** @deprecated Use {@link BudgLogger#kv(String, Object)} */
    @Deprecated
    public static BudgLogger.KeyValue kv(String key, Object value) {
        return BudgLogger.kv(key, value);
    }

    /** @deprecated Use {@link BudgLogger#logError(String, Throwable, BudgLogger.KeyValue...)} */
    @Deprecated
    public static void logError(String message, Throwable exception, BudgLogger.KeyValue... keyValues) {
        BudgLogger.logError(message, exception, keyValues);
    }

    /** @deprecated Use {@link BudgLogger#logError(String, BudgLogger.KeyValue...)} */
    @Deprecated
    public static void logError(String message, BudgLogger.KeyValue... keyValues) {
        BudgLogger.logError(message, keyValues);
    }

    /** @deprecated Use {@link BudgLogger#logWarn(String, BudgLogger.KeyValue...)} */
    @Deprecated
    public static void logWarn(String message, BudgLogger.KeyValue... keyValues) {
        BudgLogger.logWarn(message, keyValues);
    }

    /** @deprecated Use {@link BudgLogger#logInfo(String, BudgLogger.KeyValue...)} */
    @Deprecated
    public static void logInfo(String message, BudgLogger.KeyValue... keyValues) {
        BudgLogger.logInfo(message, keyValues);
    }

    /** @deprecated Use {@link BudgLogger#logDebug(String, BudgLogger.KeyValue...)} */
    @Deprecated
    public static void logDebug(String message, BudgLogger.KeyValue... keyValues) {
        BudgLogger.logDebug(message, keyValues);
    }

    /** @deprecated Use {@link BudgLogger#logAudit(String, BudgLogger.KeyValue...)} */
    @Deprecated
    public static void logAudit(String message, BudgLogger.KeyValue... keyValues) {
        BudgLogger.logAudit(message, keyValues);
    }

    /** @deprecated Use {@link BudgLogger#setRequestData(HttpServletRequest)} */
    @Deprecated
    public static void setRequestData(HttpServletRequest request) {
        BudgLogger.setRequestData(request);
    }

    /** @deprecated Use {@link BudgLogger#setUserData(Object, Object)} */
    @Deprecated
    public static void setUserData(Object userId, Object userEmail) {
        BudgLogger.setUserData(userId, userEmail);
    }

    /**
     * @deprecated Use {@link BudgLogger.KeyValue} directly.
     * Kept as a type alias so existing code compiles without changes.
     */
    @Deprecated
    public static class KeyValue extends BudgLogger.KeyValue {
        KeyValue(String key, Object value) {
            super(key, value);
        }
    }
}
