package com.example.budg_v2.util;

/**
 * Centralised registry of application error codes.
 *
 * <p>Each code carries:
 * <ul>
 *   <li>A short human-readable {@link #getDescription()}</li>
 *   <li>A default {@link #getErrorType()} — {@code BUSINESS} when the user can fix it,
 *       {@code SYSTEM} when ops/infra must fix it</li>
 * </ul>
 *
 * <p>Usage with {@link BudgLogger}:
 * <pre>{@code
 * AppErrorCode code = AppErrorCode.AUTH_10258;
 *
 * BudgLogger.logError(
 *     "AUTH_SERVICE", "LOGIN", userId,
 *     code.getCode(),
 *     code.getDescription(),
 *     "Verify Azure SAML userprincipalname matches LDAP account",
 *     code.getErrorType(),
 *     ex);
 * }</pre>
 *
 * <p>Error code categories:
 * <ul>
 *   <li>{@code AUTH_*}  — Authentication &amp; authorisation</li>
 *   <li>{@code DATA_*}  — Data access &amp; validation</li>
 *   <li>{@code BULK_*}  — Bulk import / export</li>
 *   <li>{@code WF_*}    — Workflow engine</li>
 *   <li>{@code SYS_*}   — System / infrastructure</li>
 * </ul>
 */
public enum AppErrorCode {

    // -------------------------------------------------------------------------
    // AUTH — Authentication & Authorisation
    // -------------------------------------------------------------------------

    /** Generic authentication failure. */
    AUTH_001("Authentication failed", BudgLogger.ErrorType.BUSINESS),

    /** Access token is expired or has an invalid signature. */
    AUTH_002("Token expired or invalid", BudgLogger.ErrorType.BUSINESS),

    /** Authenticated user does not have the required permission. */
    AUTH_003("Insufficient permissions", BudgLogger.ErrorType.BUSINESS),

    /** JWT could not be parsed or is malformed. */
    AUTH_004("Malformed or unparseable token", BudgLogger.ErrorType.BUSINESS),

    /** Session has expired; user must re-authenticate. */
    AUTH_005("Session expired", BudgLogger.ErrorType.BUSINESS),

    /** Guest / anonymous access is not permitted for this resource. */
    AUTH_006("Guest access not allowed", BudgLogger.ErrorType.BUSINESS),

    /**
     * LDAP user not found in domain.
     * Mirrors the vendor error UM_10258 from Azure / ADFS SAML flows.
     */
    AUTH_10258("User does not exist in LDAP domain", BudgLogger.ErrorType.BUSINESS),

    // -------------------------------------------------------------------------
    // DATA — Data access & validation
    // -------------------------------------------------------------------------

    /** Requested record does not exist. */
    DATA_404("Record not found", BudgLogger.ErrorType.BUSINESS),

    /** Request would create a duplicate record. */
    DATA_409("Conflict — duplicate record", BudgLogger.ErrorType.BUSINESS),

    /** Required field is missing from the request payload. */
    DATA_400("Missing required field in request", BudgLogger.ErrorType.BUSINESS),

    /** Field value does not pass validation rules. */
    DATA_422("Invalid field value", BudgLogger.ErrorType.BUSINESS),

    /** Generic data-layer processing error. */
    DATA_500("Data processing error", BudgLogger.ErrorType.SYSTEM),

    // -------------------------------------------------------------------------
    // BULK — Bulk import / export operations
    // -------------------------------------------------------------------------

    /** Uploaded file has an unsupported format or is corrupt. */
    BULK_001("Invalid or unsupported file format", BudgLogger.ErrorType.BUSINESS),

    /** One or more rows in the bulk file failed validation. */
    BULK_002("Bulk validation errors detected", BudgLogger.ErrorType.BUSINESS),

    /** Bulk operation exceeded the allowed row limit. */
    BULK_003("Bulk file exceeds maximum row limit", BudgLogger.ErrorType.BUSINESS),

    // -------------------------------------------------------------------------
    // WORKFLOW — Workflow engine
    // -------------------------------------------------------------------------

    /** No active workflow process definition found for the given key. */
    WF_001("Workflow process definition not found", BudgLogger.ErrorType.SYSTEM),

    /** Workflow instance is in an unexpected or inconsistent state. */
    WF_002("Workflow instance in invalid state", BudgLogger.ErrorType.SYSTEM),

    /** Required workflow task could not be claimed or completed. */
    WF_003("Workflow task unavailable", BudgLogger.ErrorType.SYSTEM),

    // -------------------------------------------------------------------------
    // SYS — System / infrastructure
    // -------------------------------------------------------------------------

    /** Unhandled internal server error. */
    SYS_500("Internal server error", BudgLogger.ErrorType.SYSTEM),

    /** Downstream service or dependency is temporarily unavailable. */
    SYS_503("Service unavailable", BudgLogger.ErrorType.SYSTEM),

    /** Database connection could not be established or was lost. */
    SYS_DB_001("Database connection failed", BudgLogger.ErrorType.SYSTEM),

    /** Elasticsearch connection or query failed. */
    SYS_ES_001("Elasticsearch operation failed", BudgLogger.ErrorType.SYSTEM),

    /** LDAP/AD directory service is unreachable. */
    SYS_LDAP_001("LDAP service unreachable", BudgLogger.ErrorType.SYSTEM),

    /** Email dispatch failed (SMTP or batch scheduler). */
    SYS_MAIL_001("Email delivery failed", BudgLogger.ErrorType.SYSTEM),

    /** An expected configuration value is missing or invalid. */
    SYS_CFG_001("Missing or invalid configuration", BudgLogger.ErrorType.SYSTEM);

    // -------------------------------------------------------------------------

    private final String                description;
    private final BudgLogger.ErrorType  errorType;

    AppErrorCode(String description, BudgLogger.ErrorType errorType) {
        this.description = description;
        this.errorType   = errorType;
    }

    /** Returns the enum name as the error code string (e.g. {@code "AUTH_001"}). */
    public String getCode() { return name(); }

    /** Short human-readable description — suitable for log messages and error responses. */
    public String getDescription() { return description; }

    /**
     * Default {@link BudgLogger.ErrorType} for this code.
     * Pass directly to {@link BudgLogger#logError} so the dashboard can classify errors.
     */
    public BudgLogger.ErrorType getErrorType() { return errorType; }

    /** {@code "CODE - description"} — useful for error response bodies. */
    @Override
    public String toString() { return name() + " - " + description; }
}
