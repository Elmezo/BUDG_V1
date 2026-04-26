package com.example.budg_v2.bulk;

import com.example.budg_v2.bulk.common.BulkPathUtil;
import com.example.budg_v2.bulk.objects.BulkUploadBroadcaster;
import com.example.budg_v2.dao.JobDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.DependencyGraph;
import com.example.budg_v2.service.EnvironmentCoreTruncator;
import com.example.budg_v2.service.ParallelImportProcessor;
import com.example.budg_v2.service.RelationshipDetector;
import com.example.budg_v2.snapshot.TableSnapshotConstants;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.example.unisonsearch.service.ConfigurationService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Import {@code env_export.zip} packages (metadata.json + Manifest.json + stable Excel names).
 */
@WebServlet("/api/import-env")
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 10,
        maxFileSize = 1024 * 1024 * 500,
        maxRequestSize = 1024 * 1024 * 520)
public class ImportEnvServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ImportEnvServlet.class);
    private static final Gson gson = new Gson();

    private static String getTempDir() {
        return BulkPathUtil.getBulkPathForEntity("migration-import-env");
    }

    private static final long MAX_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_EXTRACTED_ENTRIES = 500;

    private final JobDAO jobDAO = new JobDAO();
    private final ConfigurationService configurationService = new ConfigurationService();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.setCorsHeaders(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(request, response);

        if (!configurationService.isDataMigrationEnabled()) {
            sendErrorResponse(response,
                    "Data Migration is not enabled. Please enable it in Admin Panel > System Settings > Environment.",
                    403);
            return;
        }
        if (!UserContextUtil.isCurrentUserAdmin(request)) {
            sendErrorResponse(response, "Admin or SuperAdmin access required", 403);
            return;
        }
        int userId = UserContextUtil.getCurrentUserId(request);
        if (userId <= 0) {
            sendErrorResponse(response, "Unable to determine user ID", 401);
            return;
        }

        String mode = request.getParameter("mode");
        if (mode == null || mode.isEmpty()) {
            mode = "merge";
        }
        mode = mode.trim().toLowerCase();
        if (!"replace".equals(mode) && !"merge".equals(mode)) {
            sendErrorResponse(response, "Invalid mode: use replace or merge", 400);
            return;
        }
        boolean replaceMode = "replace".equals(mode);
        boolean mergeMode = "merge".equals(mode);

        int parentJobId = -1;
        File tempExtractDir = null;

        try {
            Part filePart = request.getPart("file");
            if (filePart == null) {
                sendErrorResponse(response, "No file uploaded", 400);
                return;
            }
            String originalFileName = getFileName(filePart);
            if (originalFileName == null || !originalFileName.toLowerCase().endsWith(".zip")) {
                sendErrorResponse(response, "Invalid file type. Please upload a ZIP file.", 400);
                return;
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String referenceName = "ENV-" + timestamp;
            parentJobId = jobDAO.createJob("Bulk Import ENV", referenceName, 0, "Pending", userId);
            jobDAO.createJobProgress(parentJobId, 0, "Extracting", "Extracting ENV ZIP...");
            BulkUploadBroadcaster.getInstance().broadcast(parentJobId, "Pending", 5, "Extracting ZIP...");

            tempExtractDir = new File(getTempDir() + "extract_env_" + timestamp);
            if (!tempExtractDir.exists()) {
                tempExtractDir.mkdirs();
            }

            Map<String, File> extractedFiles = extractZipFile(filePart.getInputStream(), tempExtractDir);

            File metadataFile = findMetadataFile(extractedFiles);
            if (metadataFile == null || !metadataFile.exists()) {
                throw new IOException(EnvironmentMigrationConstants.METADATA_FILE + " not found. "
                        + "This ZIP is not a Bulk Import ENV package.");
            }
            validateMetadata(metadataFile);

            File manifestFile = findManifestFile(extractedFiles);
            if (manifestFile == null || !manifestFile.exists()) {
                throw new IOException(EnvironmentMigrationConstants.MANIFEST_FILE + " not found in ZIP file.");
            }
            List<MigrationManifestEntry> manifestEntries = parseManifest(manifestFile);
            validateManifestFilesPresent(manifestEntries, extractedFiles);
            validateExcelWorkbooksReadable(manifestEntries, extractedFiles);

            RelationshipDetector relationshipDetector = new RelationshipDetector();
            DependencyGraph graph = relationshipDetector.detectRelationships(manifestEntries, extractedFiles);
            if (graph.hasCircularDependency()) {
                List<String> suggestions = relationshipDetector.suggestResolutionForCircularDependency(graph);
                String circularDetail = suggestions != null && !suggestions.isEmpty()
                        ? String.join("; ", suggestions)
                        : "no additional detail available";
                throw new IllegalStateException(
                        "Import aborted: circular dependencies detected. Details: " + circularDetail);
            }
            List<MigrationManifestEntry> orderedEntries =
                    relationshipDetector.recalculateImportOrder(graph, manifestEntries);

            jobDAO.updateJobItemsCount(parentJobId, orderedEntries.size());
            jobDAO.updateJobStatus(parentJobId, "Processing", false);
            jobDAO.createJobProgress(parentJobId, 8, "Preparing",
                    replaceMode ? "Truncating core tables (replace mode)..." : "Skipping truncate (merge mode)...");
            BulkUploadBroadcaster.getInstance().broadcast(parentJobId, "Processing", 8,
                    replaceMode ? "Truncating..." : "Merge import...");

            if (replaceMode) {
                try (Connection conn = DatabaseConnection.getConnection()) {
                    conn.setAutoCommit(true);
                    EnvironmentCoreTruncator.truncateCoreGovernanceData(conn);
                } catch (SQLException e) {
                    throw new IOException("Replace mode truncate failed: " + e.getMessage(), e);
                }
            }

            jobDAO.createJobProgress(parentJobId, 10, "Processing",
                    "Processing " + orderedEntries.size() + " files...");
            ParallelImportProcessor parallelProcessor = new ParallelImportProcessor();
            try {
                parallelProcessor.processInParallel(orderedEntries, extractedFiles, parentJobId, userId, mergeMode,
                        replaceMode, mergeMode);
            } finally {
                parallelProcessor.shutdown();
            }

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("status", "success");
            successResponse.addProperty("job_id", parentJobId);
            successResponse.addProperty("reference_name", referenceName);
            successResponse.addProperty("mode", mode);
            successResponse.addProperty("message", "ENV import finished. Job " + referenceName + ".");
            successResponse.addProperty("total_files", orderedEntries.size());
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(successResponse));

        } catch (Throwable e) {
            logger.error("Error importing ENV package", e);
            if (parentJobId > 0) {
                try {
                    jobDAO.updateJobStatus(parentJobId, "Failed", true);
                    jobDAO.createJobProgress(parentJobId, 0, "Failed", "Error: " + e.getMessage());
                    BulkUploadBroadcaster.getInstance().broadcast(parentJobId, "Failed", 0,
                            "Error: " + e.getMessage());
                } catch (Exception ex) {
                    logger.error("Error updating job", ex);
                }
            }
            sendErrorResponse(response, "Error importing ENV: " + e.getMessage(), 500);
        } finally {
            if (tempExtractDir != null && tempExtractDir.exists()) {
                deleteDirectory(tempExtractDir);
            }
        }
    }

    private static void validateMetadata(File metadataFile) throws IOException {
        try (FileReader reader = new FileReader(metadataFile)) {
            JsonElement root;
            try {
                root = JsonParser.parseReader(reader);
            } catch (Exception e) {
                throw new IOException("metadata.json is not valid JSON: " + e.getMessage(), e);
            }
            if (root == null || root.isJsonNull()) {
                throw new IOException("metadata.json is empty or invalid JSON");
            }
            if (!root.isJsonObject()) {
                throw new IOException("metadata.json must be a JSON object for a budg_env package");
            }
            JsonObject meta = root.getAsJsonObject();
            if (meta.has("packageType") && meta.get("packageType").isJsonPrimitive()) {
                String pt = meta.get("packageType").getAsString();
                if (TableSnapshotConstants.PACKAGE_TYPE.equals(pt)) {
                    throw new IOException(
                            "This ZIP is a table_snapshot package. Table snapshots must be uploaded as encrypted .bsnap "
                                    + "to /api/table-snapshot/import. This page's .zip upload is for budg_env migration packages only.");
                }
            }
            if (!meta.has("packageType")
                    || !meta.get("packageType").isJsonPrimitive()
                    || !EnvironmentMigrationConstants.PACKAGE_TYPE.equals(meta.get("packageType").getAsString())) {
                throw new IOException("metadata.json packageType must be \"" + EnvironmentMigrationConstants.PACKAGE_TYPE + "\"");
            }
            if (!meta.has("schemaVersion")) {
                throw new IOException("metadata.json missing schemaVersion");
            }
            int schemaVersion;
            try {
                schemaVersion = meta.get("schemaVersion").getAsInt();
            } catch (Exception e) {
                throw new IOException("metadata.json schemaVersion must be an integer", e);
            }
            if (schemaVersion != EnvironmentMigrationConstants.SCHEMA_VERSION) {
                throw new IOException("Unsupported schemaVersion " + schemaVersion + ". Required: "
                        + EnvironmentMigrationConstants.SCHEMA_VERSION);
            }
        }
    }

    private static void validateManifestFilesPresent(
            List<MigrationManifestEntry> entries,
            Map<String, File> extractedFiles) throws IOException {
        for (MigrationManifestEntry e : entries) {
            File f = extractedFiles.get(e.fileEntry);
            if (f == null || !f.exists()) {
                f = extractedFiles.get(normalizeFileName(e.fileEntry));
            }
            if (f == null || !f.exists()) {
                throw new IOException("Manifest references missing file: " + e.fileEntry);
            }
        }
    }

    private static void validateExcelWorkbooksReadable(
            List<MigrationManifestEntry> entries,
            Map<String, File> extractedFiles) throws IOException {
        for (MigrationManifestEntry e : entries) {
            if (e.fileEntry == null || !e.fileEntry.toLowerCase().endsWith(".xlsx")) {
                continue;
            }
            File f = extractedFiles.get(e.fileEntry);
            if (f == null || !f.exists()) {
                f = extractedFiles.get(normalizeFileName(e.fileEntry));
            }
            if (f == null || !f.exists()) {
                continue;
            }
            try (InputStream in = new FileInputStream(f); Workbook wb = new XSSFWorkbook(in)) {
                wb.getSheetAt(0);
            }
        }
    }

    private static File findMetadataFile(Map<String, File> extractedFiles) {
        File f = extractedFiles.get(EnvironmentMigrationConstants.METADATA_FILE);
        if (f != null && f.exists()) {
            return f;
        }
        for (Map.Entry<String, File> e : extractedFiles.entrySet()) {
            if (EnvironmentMigrationConstants.METADATA_FILE.equalsIgnoreCase(normalizeFileName(e.getKey()))) {
                return e.getValue();
            }
        }
        return null;
    }

    private static File findManifestFile(Map<String, File> extractedFiles) {
        final String canonical = EnvironmentMigrationConstants.MANIFEST_FILE;
        final String normalizedCanonical = normalizeFileName(canonical);

        File manifestFile = extractedFiles.get(canonical);
        if (manifestFile != null && manifestFile.exists()) {
            return manifestFile;
        }
        manifestFile = extractedFiles.get(normalizedCanonical);
        if (manifestFile != null && manifestFile.exists()) {
            return manifestFile;
        }
        manifestFile = extractedFiles.get(EnvironmentMigrationConstants.LEGACY_MANIFEST_FILE);
        if (manifestFile != null && manifestFile.exists()) {
            return manifestFile;
        }
        for (Map.Entry<String, File> entry : extractedFiles.entrySet()) {
            if (normalizedCanonical.equals(normalizeFileName(entry.getKey()))) {
                File file = entry.getValue();
                if (file != null && file.exists()) {
                    return file;
                }
            }
        }
        return null;
    }

    private static List<MigrationManifestEntry> parseManifest(File manifestFile) throws IOException {
        final String manifestName = EnvironmentMigrationConstants.MANIFEST_FILE;
        List<MigrationManifestEntry> entries = new ArrayList<>();
        try (FileReader reader = new FileReader(manifestFile)) {
            JsonElement root;
            try {
                root = JsonParser.parseReader(reader);
            } catch (Exception e) {
                throw new IOException(manifestName + " is not valid JSON: " + e.getMessage(), e);
            }
            if (root == null || !root.isJsonArray()) {
                throw new IOException(manifestName
                        + " must be a JSON array of objects with string fields targetRef and fileEntry");
            }
            JsonArray manifestArray = root.getAsJsonArray();
            for (int i = 0; i < manifestArray.size(); i++) {
                JsonElement el = manifestArray.get(i);
                int oneBased = i + 1;
                if (el == null || !el.isJsonObject()) {
                    throw new IOException(manifestName + " entry " + oneBased
                            + " must be a JSON object with string fields targetRef and fileEntry");
                }
                JsonObject entryObj = el.getAsJsonObject();
                if (!entryObj.has("targetRef") || !entryObj.get("targetRef").isJsonPrimitive()) {
                    throw new IOException(manifestName + " entry " + oneBased + " missing string field targetRef");
                }
                if (!entryObj.has("fileEntry") || !entryObj.get("fileEntry").isJsonPrimitive()) {
                    throw new IOException(manifestName + " entry " + oneBased + " missing string field fileEntry");
                }
                String targetRef = entryObj.get("targetRef").getAsString();
                String fileEntry = entryObj.get("fileEntry").getAsString();
                if (targetRef == null || targetRef.isBlank()) {
                    throw new IOException(manifestName + " entry " + oneBased + " has empty targetRef");
                }
                if (fileEntry == null || fileEntry.isBlank()) {
                    throw new IOException(manifestName + " entry " + oneBased + " has empty fileEntry");
                }
                entries.add(new MigrationManifestEntry(targetRef, fileEntry));
            }
        }
        return entries;
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String normalized = new File(fileName).getName();
        return normalized.toLowerCase();
    }

    private static Map<String, File> extractZipFile(InputStream zipInputStream, File extractDir) throws IOException {
        Map<String, File> extractedFiles = new HashMap<>();
        long totalExtractedBytes = 0;
        int entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String originalPath = entry.getName();
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                entryCount++;
                if (entryCount > MAX_EXTRACTED_ENTRIES) {
                    throw new IOException("ZIP extraction aborted: too many entries.");
                }
                String normalizedKey = normalizeFileName(originalPath);
                File file = new File(extractDir, new File(originalPath).getName());
                String canonicalExtractDir = extractDir.getCanonicalPath();
                if (!file.getCanonicalPath().startsWith(canonicalExtractDir + File.separator)
                        && !file.getCanonicalPath().equals(canonicalExtractDir)) {
                    throw new IOException("ZIP entry path traversal detected: " + originalPath);
                }
                file.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        totalExtractedBytes += len;
                        if (totalExtractedBytes > MAX_EXTRACTED_BYTES) {
                            throw new IOException("ZIP extraction aborted: size limit exceeded.");
                        }
                        fos.write(buffer, 0, len);
                    }
                }
                extractedFiles.put(normalizedKey, file);
                extractedFiles.put(originalPath, file);
                zis.closeEntry();
            }
        }
        return extractedFiles;
    }

    private static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    private static String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition != null) {
            String[] tokens = contentDisposition.split(";");
            for (String token : tokens) {
                if (token.trim().startsWith("filename")) {
                    return token.substring(token.indexOf('=') + 2, token.length() - 1);
                }
            }
        }
        return null;
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int status) throws IOException {
        response.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("error", message);
        response.getWriter().write(gson.toJson(error));
    }
}
