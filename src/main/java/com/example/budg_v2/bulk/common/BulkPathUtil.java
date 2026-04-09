package com.example.budg_v2.bulk.common;

import java.io.File;

/**
 * <b>File Overview</b><br>
 * {@code BulkPathUtil} is a pure-utility class that centralises the resolution
 * of the <em>bulk storage base directory</em> used by both the Java/Tomcat
 * back-end and the Python validation worker that process uploaded Excel files.
 *
 * <p>
 * <b>Business Capability:</b> Bulk-upload infrastructure / Configuration
 * management. Ensures that Tomcat and the out-of-process Python validator
 * always read and write the same directory tree regardless of the deployment
 * environment (local, staging, production VM).
 * </p>
 *
 * <p>
 * <b>Modules that depend on this class:</b>
 * </p>
 * <ul>
 * <li>All {@code *BulkUploadServlet} classes that construct file paths for
 * templates, uploaded files and result JSON files.</li>
 * <li>The Python-driven validation middleware that is invoked from those
 * servlets and must agree on the same absolute base path.</li>
 * </ul>
 *
 * <h3>Responsibility</h3>
 * <ul>
 * <li>Reads the bulk base directory from configuration in a well-defined
 * priority order (JVM system property → OS environment variable →
 * hard-coded local-dev fallback).</li>
 * <li>Normalises path separators so that code written once works on both
 * Windows (development) and Linux (production).</li>
 * <li>Guarantees that every returned path ends with the platform's file
 * separator so callers can always safely append a filename.</li>
 * </ul>
 *
 * <h3>Typical Flow</h3>
 * <ol>
 * <li>Servlet calls {@link #getBulkBasePath()} to obtain the configured
 * root.</li>
 * <li>Servlet calls {@link #getBulkPathForEntity(String)} to obtain the
 * entity-specific sub-directory (e.g. {@code .../bulk/dataset/}).</li>
 * <li>Servlet appends the uploaded filename or result filename to the
 * returned path.</li>
 * </ol>
 *
 * @author BUDG Platform Team
 * @version 2.0
 * @since 1.0
 */
public final class BulkPathUtil {

    /**
     * Relative fallback path used when neither the JVM system property nor
     * the environment variable is set. Suitable for local development where
     * the working directory is the project root.
     */
    private static final String FALLBACK_BASE = "src/main/bulk/";

    /**
     * Private constructor — prevents instantiation.
     * This class is a stateless utility; all members are static.
     */
    private BulkPathUtil() {
    }

    /**
     * Resolves the <em>bulk base directory</em> using the following priority
     * order:
     * <ol>
     * <li>JVM system property {@code bulk.template.path}
     * (set via {@code -Dbulk.template.path=...} in Tomcat's startup
     * script or {@code catalina.sh}).</li>
     * <li>OS environment variable {@code BULK_TEMPLATE_PATH}
     * (useful for Docker / systemd deployments).</li>
     * <li>Hard-coded fallback {@value #FALLBACK_BASE}
     * (relative to the JVM working directory — works for local Maven
     * runs).</li>
     * </ol>
     *
     * <p>
     * <b>Side effect:</b> None — this method is pure and deterministic for
     * a given JVM launch configuration.
     * </p>
     *
     * @return The resolved base directory path, always ending with the
     *         platform's file separator character ({@code /} or {@code \}).
     *         Never {@code null}.
     */
    public static String getBulkBasePath() {
        String base = System.getProperty("bulk.template.path");
        if (base != null && !base.isEmpty()) {
            return ensureTrailingSeparator(base);
        }
        base = System.getenv("BULK_TEMPLATE_PATH");
        if (base != null && !base.isEmpty()) {
            return ensureTrailingSeparator(base);
        }
        File fallback = new File(FALLBACK_BASE);
        if (!fallback.isAbsolute()) {
            fallback = new File(System.getProperty("user.dir", "."), FALLBACK_BASE);
        }
        return ensureTrailingSeparator(fallback.getAbsolutePath());
    }

    /**
     * Returns the absolute (or relative) path to an entity-specific
     * sub-directory under the bulk base path.
     *
     * <p>
     * Example: if the base is {@code /srv/budg/bulk/} and
     * {@code entitySubdir} is {@code "dataset"}, this returns
     * {@code /srv/budg/bulk/dataset/}.
     * </p>
     *
     * <p>
     * Forward slashes and back-slashes inside {@code entitySubdir} are
     * normalised to the current platform's separator so that servlet code
     * written on Windows also works on Linux production servers.
     * </p>
     *
     * <p>
     * <b>Side effect:</b> None.
     * </p>
     *
     * @param entitySubdir The sub-directory name (or relative path) that
     *                     identifies the entity type, e.g. {@code "people"},
     *                     {@code "roles"}, {@code "dataset"}.
     *                     Pass {@code null} or an empty string to receive the
     *                     bare base path.
     * @return Fully constructed path ending with a file separator.
     *         Never {@code null}.
     */
    public static String getBulkPathForEntity(String entitySubdir) {
        String base = getBulkBasePath();
        if (entitySubdir == null || entitySubdir.isEmpty()) {
            return base;
        }
        // Normalise any mixed separators that may come from configuration files
        // written on a different OS.
        String normalized = entitySubdir.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        if (normalized.endsWith(File.separator)) {
            return base + normalized;
        }
        return base + normalized + File.separator;
    }

    /**
     * Appends a trailing file-separator character to {@code path} if one is
     * not already present.
     *
     * <p>
     * This is necessary because some configuration sources (e.g., a
     * properties file edited by a human) may or may not include a trailing
     * slash, and downstream code must be able to blindly concatenate a
     * filename to the returned value.
     * </p>
     *
     * @param path The raw directory path. May already contain a trailing
     *             separator.
     * @return The path with exactly one trailing separator; or the original
     *         value unchanged if it was {@code null} or empty.
     */
    private static String ensureTrailingSeparator(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        if (path.endsWith("/") || path.endsWith("\\")) {
            return path;
        }
        return path + File.separator;
    }
}
