package com.example.budg_v2.snapshot;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.example.unisonsearch.service.ConfigurationService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Full-table snapshot export/import for allowlisted tables.
 * Download is AES-256-GCM encrypted (extension .bsnap); inner payload remains a ZIP (metadata.json + JSONL).
 */
@WebServlet(urlPatterns = { "/api/table-snapshot", "/api/table-snapshot/*" })
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 10,
        maxFileSize = 1024 * 1024 * 500,
        maxRequestSize = 1024 * 1024 * 520)
public class TableSnapshotServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(TableSnapshotServlet.class);
    private static final Gson gson = new Gson();

    private final ConfigurationService configurationService = new ConfigurationService();
    private final TableSnapshotService snapshotService = new TableSnapshotService();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.setCorsHeaders(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(req, resp);
        if (!configurationService.isDataMigrationEnabled()) {
            sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN,
                    "Data Migration is not enabled. Enable it in Admin Panel > System Settings > Environment.");
            return;
        }
        if (!UserContextUtil.isCurrentUserAdmin(req)) {
            sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "Admin or SuperAdmin access required");
            return;
        }

        String path = req.getPathInfo();
        if (path == null) {
            path = "";
        }
        if (!"/export".equals(path)) {
            sendJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "Use GET /api/table-snapshot/export");
            return;
        }

        String filename = "table_snapshot_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".bsnap";
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setReadOnly(true);
            SecretKey key = TableSnapshotCipher.resolveKey();
            try (CipherOutputStream cos = TableSnapshotCipher.encryptingZipSink(resp.getOutputStream(), key)) {
                snapshotService.exportToZip(cos, conn);
            }
            resp.flushBuffer();
        } catch (Exception e) {
            logger.error("Table snapshot export failed", e);
            if (!resp.isCommitted()) {
                resp.reset();
                resp.setContentType("application/json");
                sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Export failed: " + e.getMessage());
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(req, resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if (!configurationService.isDataMigrationEnabled()) {
            sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN,
                    "Data Migration is not enabled. Enable it in Admin Panel > System Settings > Environment.");
            return;
        }
        if (!UserContextUtil.isCurrentUserAdmin(req)) {
            sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "Admin or SuperAdmin access required");
            return;
        }
        int userId = UserContextUtil.getCurrentUserId(req);
        if (userId <= 0) {
            sendJsonError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Unable to determine user ID");
            return;
        }

        String path = req.getPathInfo();
        if (path == null) {
            path = "";
        }
        if (!"/import".equals(path)) {
            sendJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "Use POST /api/table-snapshot/import");
            return;
        }

        String mode = req.getParameter("mode");
        if (mode == null || mode.isBlank()) {
            mode = "merge";
        }
        mode = mode.trim().toLowerCase();
        if (!"merge".equals(mode) && !"replace".equals(mode)) {
            sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid mode: use merge or replace");
            return;
        }
        boolean replace = "replace".equals(mode);

        Part filePart;
        try {
            filePart = req.getPart("file");
        } catch (Exception e) {
            sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Multipart file part 'file' required");
            return;
        }
        if (filePart == null) {
            sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "No file uploaded");
            return;
        }
        byte[] uploaded;
        try {
            uploaded = filePart.getInputStream().readAllBytes();
        } catch (IOException e) {
            sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Could not read uploaded file");
            return;
        }
        if (uploaded.length == 0) {
            sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Empty file");
            return;
        }

        String submittedFileName = filePart.getSubmittedFileName();
        if (submittedFileName == null || submittedFileName.isBlank()) {
            submittedFileName = "snapshot.bsnap";
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                SecretKey key = TableSnapshotCipher.resolveKey();
                byte[] zipBytes = TableSnapshotCipher.unwrapToZipBytes(uploaded, key);
                JsonObject fullResult = snapshotService.importFromZip(new ByteArrayInputStream(zipBytes), conn, replace);
                conn.commit();
                TableSnapshotImportJobRecorder.JobRecord jobRecord =
                        TableSnapshotImportJobRecorder.recordSuccess(userId, submittedFileName, fullResult);
                JsonObject clientPayload = TableSnapshotImportJobRecorder.toClientSummary(fullResult, jobRecord);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(clientPayload));
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            logger.error("Table snapshot import failed", e);
            sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Import failed: " + e.getMessage());
        }
    }

    private static void sendJsonError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        JsonObject err = new JsonObject();
        err.addProperty("status", "error");
        err.addProperty("error", message);
        resp.getWriter().write(gson.toJson(err));
    }
}
