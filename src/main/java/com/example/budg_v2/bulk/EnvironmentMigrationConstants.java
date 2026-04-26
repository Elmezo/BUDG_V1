package com.example.budg_v2.bulk;

/**
 * Versioning and package identity for Bulk Migrate / Import ENV (BUDG migration ZIP).
 */
public final class EnvironmentMigrationConstants {

    public static final String PACKAGE_TYPE = "budg_env";
    public static final int SCHEMA_VERSION = 1;
    public static final String METADATA_FILE = "metadata.json";
    /** Manifest listing {@code targetRef} / {@code fileEntry} pairs for ENV migration ZIPs. */
    public static final String MANIFEST_FILE = "Manifest.json";
    /** Older exports used lowercase; import still accepts this name when present in the ZIP. */
    public static final String LEGACY_MANIFEST_FILE = "manifest.json";
    /** Value written to metadata.json when workbooks use stable ENV names (no timestamp). */
    public static final String EXCEL_NAMING_STABLE = "stable_v1";

    private EnvironmentMigrationConstants() {
    }
}
