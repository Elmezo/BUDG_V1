package com.example.budg_v2.admin;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.exception.LdapConnectionException;
import com.example.budg_v2.service.LdapAuthService;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.LdapConfigUtil;
import com.example.budg_v2.util.LdapPlaceholderPassword;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Servlet to download log files as a ZIP archive.
 * Only accessible by ADMIN users.
 * <p>
 * The archive contains only human-readable formatted log files.
 */
@WebServlet(urlPatterns = {
        "/admin/logs/download",
        "/admin/logs/status",
        "/admin/logs/verify-password",
        "/admin/logs/delete"
})
public class LogsDownloadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String LOGS_DIRECTORY = "logs";
    private static final String LAST_DELETE_CONFIG_KEY = "LOGS_LAST_DELETE_INFO";
    private static final int LOG_DELETE_KEEP_DAYS = 2;

    private static final String[] LOG_FILE_PATTERNS = {
        "prod_errors-*.log",
        "prod_app-*.log",
        "prod_audit-*.log"
    };

    private static final String[] LOG_FILE_NAMES = {
        "prod_errors.log",
        "prod_app.log",
        "prod_audit.log"
    };

    /** Keys used in the summary header line (rest go to Details). */
    private static final Set<String> SUMMARY_KEYS = Set.of(
            "timestamp", "@timestamp", "level", "logger_name", "logger",
            "thread_name", "thread", "channel"
    );

    private final Gson gson = new Gson();
    private final LdapAuthService ldapAuthService = new LdapAuthService();

    private static final List<ServicePattern> SERVICE_PATTERNS = List.of(
            new ServicePattern("authorization", "authorization", "segmentation"),
            new ServicePattern("bulk-upload", "bulk-upload", "bulkupload", "bulk_upload", "asset-onboarder",
                    "assetonboarder", "onboarder"),
            new ServicePattern("bulk-migrate", "bulk-migrate", "bulkmigrate", "bulk_migrate", "migration",
                    "migrate"),
            new ServicePattern("change-request", "change-request", "changerequest", "change_request"),
            new ServicePattern("document-upload", "document-upload", "documentupload", "document_upload",
                    "documentmanagement"),
            new ServicePattern("notification", "notification", "notify"),
            new ServicePattern("unison", "unison", "unison-search"),
            new ServicePattern("value-list", "value-list", "valuelist", "value_list"),
            new ServicePattern("event-monitor", "event-monitor", "eventmonitor", "event_monitor", "monitor"),
            new ServicePattern("dashboard", "dashboard"),
            new ServicePattern("workflow", "workflow"),
            new ServicePattern("policy", "policy"),
            new ServicePattern("process", "process"),
            new ServicePattern("product", "product"),
            new ServicePattern("capability", "capability"),
            new ServicePattern("interface", "interface"),
            new ServicePattern("glossary", "glossary"),
            new ServicePattern("dataset", "dataset")
    );

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (isStatusRequest(request)) {
            handleStatus(request, response);
            return;
        }

        if (!UserContextUtil.isCurrentUserAdmin(request)) {
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    "Forbidden: Admin access required");
            return;
        }

        ActivityLogHelper.logSimpleActivity(request,
                ActivityLogConstants.SETTING_DOWNLOAD_LOGS,
                ActivityLogConstants.COMPONENT_LOGS,
                ActivityLogConstants.CHANGE_TYPE_OTHER_ACTIONS);

        try {
            Path logsDir = getLogsDirectory();
            if (logsDir == null || !Files.exists(logsDir)) {
                StringBuilder errorMsg = new StringBuilder("Logs directory not found. Searched locations: ");
                errorMsg.append("catalina.base/logs, catalina.home/logs, ./logs, ");
                errorMsg.append("servlet context, user.dir/logs");
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, errorMsg.toString());
                return;
            }

            List<Path> logFiles = collectLogFiles(logsDir);

            if (logFiles.isEmpty()) {
                String errorMsg = String.format(
                        "No log files found in directory: %s. " +
                                "Patterns searched: %s. " +
                                "Make sure the application has generated log files.",
                        logsDir.toAbsolutePath(),
                        String.join(", ", LOG_FILE_PATTERNS)
                );
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, errorMsg);
                return;
            }

            byte[] zipData = createZipFile(logFiles);

            String zipFileName = generateZipFileName();
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + zipFileName + "\"");
            response.setContentLength(zipData.length);

            try (OutputStream out = response.getOutputStream()) {
                out.write(zipData);
                out.flush();
            }

        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error generating log archive: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isVerifyPasswordRequest(request)) {
            sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Not found");
            return;
        }

        if (!UserContextUtil.isCurrentUserSuperAdmin(request)) {
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    "Forbidden: Super Admin access required");
            return;
        }

        JsonObject body;
        try {
            body = gson.fromJson(request.getReader(), JsonObject.class);
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request body");
            return;
        }

        String password = getString(body, "password");
        if (password == null || password.isBlank()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Password is required");
            return;
        }

        try {
            if (!verifyCurrentUserPassword(request, password)) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid password");
                return;
            }
            sendJsonResponse(response, Map.of("success", true));
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error verifying password: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isDeleteRequest(request)) {
            sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Not found");
            return;
        }

        if (!UserContextUtil.isCurrentUserSuperAdmin(request)) {
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    "Forbidden: Super Admin access required");
            return;
        }

        JsonObject body;
        try {
            body = gson.fromJson(request.getReader(), JsonObject.class);
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request body");
            return;
        }

        String password = getString(body, "password");
        String confirmationText = getString(body, "confirmationText");
        if (password == null || password.isBlank()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Password is required");
            return;
        }

        ActivityLogHelper.UserInfo userInfo = ActivityLogHelper.getUserInfo(request);
        String userName = displayUserName(userInfo);
        if (!isValidConfirmation(confirmationText, userName)) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Confirmation text does not match");
            return;
        }

        try {
            if (!verifyCurrentUserPassword(request, password)) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid password");
                return;
            }

            Path logsDir = getLogsDirectory();
            if (logsDir == null || !Files.exists(logsDir)) {
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Logs directory not found");
                return;
            }

            DeleteResult result = deleteOldLogFiles(logsDir);
            saveLastDeletedUser(userName, userInfo.userEmail, result.deletedCount);

            Map<String, Object> contextMap = new LinkedHashMap<>();
            contextMap.put("deletedFiles", result.deletedCount);
            contextMap.put("keptDays", LOG_DELETE_KEEP_DAYS);
            contextMap.put("failedFiles", result.failedFiles);
            ActivityLogHelper.logActivity(request,
                    ActivityLogConstants.SETTING_DOWNLOAD_LOGS,
                    ActivityLogConstants.COMPONENT_LOGS,
                    ActivityLogConstants.CHANGE_TYPE_DELETE,
                    null,
                    Map.of("deletedFiles", result.deletedCount),
                    contextMap);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("deletedFiles", result.deletedCount);
            payload.put("failedFiles", result.failedFiles);
            payload.put("lastDeletedUser", userName);
            sendJsonResponse(response, payload);
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error deleting log files: " + e.getMessage());
        }
    }

    private void handleStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!UserContextUtil.isCurrentUserAdmin(request)) {
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    "Forbidden: Admin access required");
            return;
        }

        ActivityLogHelper.UserInfo userInfo = ActivityLogHelper.getUserInfo(request);
        String userName = displayUserName(userInfo);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("isSuperAdmin", UserContextUtil.isCurrentUserSuperAdmin(request));
        payload.put("currentUserName", userName);
        payload.put("confirmationPhraseEn", confirmationPhraseEn(userName));
        payload.put("confirmationPhraseAr", confirmationPhraseAr(userName));
        payload.put("lastDeleted", getLastDeletedInfo());
        sendJsonResponse(response, payload);
    }

    private Path getLogsDirectory() {
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null) {
            Path catalinaLogs = Paths.get(catalinaBase, LOGS_DIRECTORY);
            if (Files.exists(catalinaLogs) && Files.isDirectory(catalinaLogs)) {
                return catalinaLogs.toAbsolutePath();
            }
        }

        String catalinaHome = System.getProperty("catalina.home");
        if (catalinaHome != null) {
            Path catalinaHomeLogs = Paths.get(catalinaHome, LOGS_DIRECTORY);
            if (Files.exists(catalinaHomeLogs) && Files.isDirectory(catalinaHomeLogs)) {
                return catalinaHomeLogs.toAbsolutePath();
            }
        }

        Path logsDir = Paths.get(LOGS_DIRECTORY);
        if (Files.exists(logsDir) && Files.isDirectory(logsDir)) {
            return logsDir.toAbsolutePath();
        }

        String realPath = getServletContext().getRealPath("/" + LOGS_DIRECTORY);
        if (realPath != null) {
            Path path = Paths.get(realPath);
            if (Files.exists(path) && Files.isDirectory(path)) {
                return path;
            }
        }

        Path cwdLogs = Paths.get(System.getProperty("user.dir"), LOGS_DIRECTORY);
        if (Files.exists(cwdLogs) && Files.isDirectory(cwdLogs)) {
            return cwdLogs.toAbsolutePath();
        }

        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            Path parentLogs = Paths.get(userDir).getParent();
            if (parentLogs != null) {
                Path parentLogsDir = parentLogs.resolve(LOGS_DIRECTORY);
                if (Files.exists(parentLogsDir) && Files.isDirectory(parentLogsDir)) {
                    return parentLogsDir.toAbsolutePath();
                }
            }
        }

        return null;
    }

    private static boolean isStatusRequest(HttpServletRequest request) {
        return request.getServletPath() != null && request.getServletPath().endsWith("/status");
    }

    private static boolean isDeleteRequest(HttpServletRequest request) {
        return request.getServletPath() != null && request.getServletPath().endsWith("/delete");
    }

    private static boolean isVerifyPasswordRequest(HttpServletRequest request) {
        return request.getServletPath() != null && request.getServletPath().endsWith("/verify-password");
    }

    private List<Path> collectLogFiles(Path logsDir) throws IOException {
        List<Path> logFiles = new ArrayList<>();

        if (!Files.exists(logsDir) || !Files.isDirectory(logsDir)) {
            return logFiles;
        }

        for (String pattern : LOG_FILE_PATTERNS) {
            String regex = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*");

            try (Stream<Path> paths = Files.list(logsDir)) {
                paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches(regex))
                        .forEach(logFiles::add);
            } catch (IOException e) {
                System.err.println("Error listing log files for pattern " + pattern + ": " + e.getMessage());
            }
        }

        for (String fileName : LOG_FILE_NAMES) {
            Path logFile = logsDir.resolve(fileName);
            if (Files.exists(logFile) && Files.isRegularFile(logFile)) {
                logFiles.add(logFile);
            }
        }

        Set<Path> uniqueFiles = new LinkedHashSet<>(logFiles);
        logFiles = new ArrayList<>(uniqueFiles);
        logFiles.sort(Comparator.comparing(path -> path.getFileName().toString()));

        return logFiles;
    }

    private DeleteResult deleteOldLogFiles(Path logsDir) throws IOException {
        DeleteResult result = new DeleteResult();
        Instant cutoff = Instant.now().minusSeconds(LOG_DELETE_KEEP_DAYS * 24L * 60L * 60L);

        try (Stream<Path> paths = Files.list(logsDir)) {
            Iterator<Path> iterator = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isKnownLogFile)
                    .filter(path -> !isCurrentLogFile(path))
                    .iterator();

            while (iterator.hasNext()) {
                Path logFile = iterator.next();
                try {
                    Instant lastModified = Files.getLastModifiedTime(logFile).toInstant();
                    if (!lastModified.isBefore(cutoff)) {
                        continue;
                    }
                    Files.deleteIfExists(logFile);
                    result.deletedCount++;
                } catch (IOException e) {
                    result.failedFiles.add(logFile.getFileName().toString());
                }
            }
        }

        return result;
    }

    private boolean isKnownLogFile(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(".log")) {
            return false;
        }
        return fileName.matches("prod_(errors|app|audit)(-.+)?\\.log");
    }

    private static boolean isCurrentLogFile(Path path) {
        String fileName = path.getFileName().toString();
        for (String currentName : LOG_FILE_NAMES) {
            if (currentName.equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds ZIP with a microservice-style layout:
     * all-services/ for the full application view, then services/{service}/errors|combined.
     */
    private byte[] createZipFile(List<Path> logFiles) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<SourceLogFile> sourceFiles = new ArrayList<>();

        for (Path logFile : logFiles) {
            SourceLogFile source = toSourceLogFile(logFile);
            if (source != null) {
                sourceFiles.add(source);
            }
        }

        sourceFiles.sort(Comparator
                .comparing((SourceLogFile source) -> source.daySortKey())
                .thenComparing(source -> source.path.getFileName().toString()));

        List<LogEvent> events = readLogEvents(sourceFiles);
        events.sort(Comparator
                .comparing((LogEvent event) -> event.daySortKey())
                .thenComparing(event -> event.timestampSortKey)
                .thenComparing(event -> event.sourceFileName));

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeAllServicesArchive(zos, events);
            writeServiceArchives(zos, events);
        }

        return baos.toByteArray();
    }

    private List<LogEvent> readLogEvents(List<SourceLogFile> sourceFiles) throws IOException {
        List<LogEvent> events = new ArrayList<>();
        for (SourceLogFile source : sourceFiles) {
            try (BufferedReader reader = Files.newBufferedReader(source.path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    for (String eventText : splitLogEvents(line)) {
                        if (eventText.isBlank()) {
                            continue;
                        }
                        events.add(toLogEvent(eventText, source));
                    }
                }
            }
        }
        return events;
    }

    private LogEvent toLogEvent(String eventText, SourceLogFile source) {
        JsonObject json = null;
        try {
            JsonElement root = JsonParser.parseString(eventText);
            if (root.isJsonObject()) {
                json = root.getAsJsonObject();
            }
        } catch (Exception ignored) {
            // Keep malformed log lines in the archive; they will be rendered as readable raw blocks.
        }

        String level = firstString(json, "level");
        boolean error = "ERROR".equalsIgnoreCase(level)
                || (level == null && (source.type == LogArchiveType.ERRORS || rawLooksLikeError(eventText)));
        String serviceName = classifyService(json, eventText, source.path.getFileName().toString());
        String timestamp = firstString(json, "timestamp", "@timestamp");

        return new LogEvent(eventText, json, source.path.getFileName().toString(), source.dayKey,
                timestamp != null ? timestamp : "", serviceName, error);
    }

    private static String classifyService(JsonObject event, String rawEventText, String sourceFileName) {
        String searchable = buildServiceSearchText(event, rawEventText, sourceFileName);
        for (ServicePattern pattern : SERVICE_PATTERNS) {
            if (pattern.matches(searchable)) {
                return pattern.serviceName;
            }
        }
        return "other";
    }

    private static String buildServiceSearchText(JsonObject event, String rawEventText, String sourceFileName) {
        StringBuilder sb = new StringBuilder();
        appendSearchText(sb, sourceFileName);
        appendSearchText(sb, firstString(event, "logger_name", "logger"));
        appendSearchText(sb, firstString(event, "thread_name", "thread"));
        appendSearchText(sb, firstString(event, "component", "service", "serviceName", "module"));
        appendSearchText(sb, firstString(event, "message"));
        appendSearchText(sb, rawEventText);
        return sb.toString()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace('.', '-');
    }

    private static void appendSearchText(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append(' ').append(value);
    }

    private SourceLogFile toSourceLogFile(Path logFile) throws IOException {
        if (!Files.exists(logFile) || !Files.isRegularFile(logFile)) {
            return null;
        }

        String fileName = logFile.getFileName().toString();
        LogArchiveType type = LogArchiveType.fromFileName(fileName);
        if (type == null) {
            return null;
        }

        return new SourceLogFile(logFile, type, extractLogDay(fileName), Files.getLastModifiedTime(logFile).toMillis());
    }

    private void writeAllServicesArchive(ZipOutputStream zos, List<LogEvent> events) throws IOException {
        putDirectoryEntry(zos, "all-services/");
        putDirectoryEntry(zos, "all-services/by-day/");

        Map<String, List<LogEvent>> byDay = groupEventsByDay(events);
        for (Map.Entry<String, List<LogEvent>> dayEntry : byDay.entrySet()) {
            writeEventEntry(zos,
                    "all-services/by-day/all-services-" + dayEntry.getKey() + ".log",
                    dayEntry.getValue());
        }

        writeEventEntry(zos, "all-services/all-services-all-days.log", events);
    }

    private void writeServiceArchives(ZipOutputStream zos, List<LogEvent> events) throws IOException {
        putDirectoryEntry(zos, "services/");
        Map<String, List<LogEvent>> byService = new TreeMap<>();
        for (LogEvent event : events) {
            byService.computeIfAbsent(event.serviceName, k -> new ArrayList<>()).add(event);
        }

        for (Map.Entry<String, List<LogEvent>> serviceEntry : byService.entrySet()) {
            String serviceFolder = "services/" + serviceEntry.getKey() + "/";
            putDirectoryEntry(zos, serviceFolder);

            List<LogEvent> serviceEvents = serviceEntry.getValue();
            writeServiceTypeArchive(zos, serviceFolder, "errors", filterEvents(serviceEvents, true));
            writeServiceTypeArchive(zos, serviceFolder, "combined", filterEvents(serviceEvents, false));
        }
    }

    private void writeServiceTypeArchive(ZipOutputStream zos, String serviceFolder, String typeName,
                                         List<LogEvent> events) throws IOException {
        String typeFolder = serviceFolder + typeName + "/";
        putDirectoryEntry(zos, typeFolder);
        putDirectoryEntry(zos, typeFolder + "by-day/");

        Map<String, List<LogEvent>> byDay = groupEventsByDay(events);
        for (Map.Entry<String, List<LogEvent>> dayEntry : byDay.entrySet()) {
            writeEventEntry(zos, typeFolder + "by-day/" + typeName + "-" + dayEntry.getKey() + ".log",
                    dayEntry.getValue());
        }

        writeEventEntry(zos, typeFolder + typeName + "-all-days.log", events);
    }

    private static List<LogEvent> filterEvents(List<LogEvent> events, boolean errorsOnly) {
        List<LogEvent> filtered = new ArrayList<>();
        for (LogEvent event : events) {
            if (event.error == errorsOnly) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    private Map<String, List<LogEvent>> groupEventsByDay(List<LogEvent> events) {
        Map<String, List<LogEvent>> byDay = new TreeMap<>(this::compareDayKeys);
        for (LogEvent event : events) {
            byDay.computeIfAbsent(event.dayKey, k -> new ArrayList<>()).add(event);
        }
        return byDay;
    }

    private void writeEventEntry(ZipOutputStream zos, String entryName, List<LogEvent> events) throws IOException {
        putZipEntry(zos, entryName, System.currentTimeMillis());
        Writer writer = new OutputStreamWriter(zipEntrySink(zos), StandardCharsets.UTF_8);
        writeFormattedLogHeader(writer, entryName);

        if (events.isEmpty()) {
            writer.write("No log entries found for this group.\n");
        } else {
            for (LogEvent event : events) {
                writeLogEvent(writer, event);
                writer.write('\n');
            }
        }

        writer.flush();
        writer.close();
        zos.closeEntry();
    }

    private void writeLogEvent(Writer writer, LogEvent event) throws IOException {
        if (event.json != null) {
            writeJsonLogBlock(writer, event.json, event.sourceFileName, event.serviceName);
        } else {
            writeNonJsonBlock(writer, event.rawText, event.sourceFileName, event.serviceName);
        }
    }

    private int compareDayKeys(String a, String b) {
        if ("current".equals(a) && "current".equals(b)) {
            return 0;
        }
        if ("current".equals(a)) {
            return 1;
        }
        if ("current".equals(b)) {
            return -1;
        }
        return a.compareTo(b);
    }

    private static void putZipEntry(ZipOutputStream zos, String entryName, long lastModified) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(lastModified);
        zos.putNextEntry(entry);
    }

    private static void putDirectoryEntry(ZipOutputStream zos, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(System.currentTimeMillis());
        zos.putNextEntry(entry);
        zos.closeEntry();
    }

    /**
     * Wraps the ZIP stream so closing the writer only flushes — does not close the archive.
     */
    private static OutputStream zipEntrySink(final ZipOutputStream zos) {
        return new FilterOutputStream(zos) {
            @Override
            public void close() throws IOException {
                flush();
            }
        };
    }

    private static void writeFormattedLogHeader(Writer writer, String fileName) throws IOException {
        writer.write("================================================================================\n");
        writer.write("Formatted System Log: ");
        writer.write(fileName);
        writer.write('\n');
        writer.write("================================================================================\n");
        writer.write("This file is formatted for administrators. Each log event is separated into a clear block.\n");
        writer.write("Literal \\\\n separators from the original JSON log are expanded into real line breaks here.\n\n");
        writer.write("Log Levels Guide\n");
        writer.write("| Level | Usage |\n");
        writer.write("|-------|-------|\n");
        writer.write("| ERROR | A failure that prevents the system or an operation from continuing and needs attention. |\n");
        writer.write("| WARN  | An unexpected condition; the system is still running, but it may lead to a future error. |\n");
        writer.write("| INFO  | Important operational information, such as system status, startup/shutdown messages, and database connections. |\n");
        writer.write("| DEBUG | Detailed diagnostic information for troubleshooting; normally not used in production. |\n");
        writer.write("================================================================================\n\n");
    }

    /**
     * Some deployed log files contain escaped "\n" or "\r\n" between JSON events
     * instead of real newlines. Split those boundaries without touching escaped
     * line breaks inside a JSON message unless they are followed by the next
     * timestamp object.
     */
    private static List<String> splitLogEvents(String line) {
        return Arrays.asList(line.split("(?:\\\\r)?\\\\n(?=\\s*\\{\\s*\"@?timestamp\")"));
    }

    private void writeJsonLogBlock(Writer writer, JsonObject o, String sourceFileName, String serviceName) throws IOException {
        String ts = firstString(o, "timestamp", "@timestamp");
        String level = firstString(o, "level");
        String channel = firstString(o, "channel");
        String logger = firstString(o, "logger_name", "logger");

        writer.write("================================================================================\n");
        writer.write("[");
        writer.write(ts != null ? ts : "?");
        writer.write("] [");
        writer.write(level != null ? level.toUpperCase(Locale.ROOT) : "?");
        writer.write("]");
        if (channel != null) {
            writer.write(" [");
            writer.write(channel);
            writer.write("]");
        }
        if (logger != null) {
            writer.write(" ");
            writer.write(logger);
        }
        writer.write("\nLevel Meaning: ");
        writer.write(levelMeaning(level));
        writer.write("\nSource File: ");
        writer.write(sourceFileName != null ? sourceFileName : "unknown");
        writer.write("\nService: ");
        writer.write(serviceName != null ? serviceName : "other");
        writer.write("\nMessage:\n");

        String message = jsonElementToPlainText(o.get("message"));
        if (message == null || message.isEmpty()) {
            writer.write("(empty)\n");
        } else {
            writer.write(message);
            if (!message.endsWith("\n")) {
                writer.write('\n');
            }
        }

        if (o.has("stack_trace") && !o.get("stack_trace").isJsonNull()) {
            String st = jsonElementToPlainText(o.get("stack_trace"));
            if (st != null && !st.isEmpty()) {
                writer.write("\nStack trace:\n");
                writer.write(st);
                if (!st.endsWith("\n")) {
                    writer.write('\n');
                }
            }
        }

        writer.write("--------------------------------------------------------------------------------\n");
        writer.write("Details:\n");

        List<String> keys = new ArrayList<>();
        for (String k : o.keySet()) {
            if ("message".equals(k) || "stack_trace".equals(k)) {
                continue;
            }
            if (SUMMARY_KEYS.contains(k)) {
                continue;
            }
            keys.add(k);
        }
        Collections.sort(keys);

        if (keys.isEmpty()) {
            writer.write("(none)\n");
        } else {
            for (String k : keys) {
                writer.write(k);
                writer.write("=");
                writer.write(jsonElementToOneLine(o.get(k)));
                writer.write('\n');
            }
        }

        // Repeat summary fields that are often useful in Details for grep
        writer.write("--------------------------------------------------------------------------------\n");
        writer.write("_summary: thread=");
        writer.write(firstString(o, "thread_name", "thread") != null
                ? firstString(o, "thread_name", "thread") : "?");
        writer.write(" | logger=");
        writer.write(logger != null ? logger : "?");
        writer.write(" | channel=");
        writer.write(channel != null ? channel : "?");
        writer.write('\n');
        writer.write("================================================================================\n");
    }

    private static String levelMeaning(String level) {
        if (level == null) {
            return "Unknown";
        }
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "ERROR" -> "ERROR - A failure that prevents the system or an operation from continuing";
            case "WARN" -> "WARN - An unexpected condition; the system is still running, but it may lead to a future error";
            case "INFO" -> "INFO - Important operational information for system activity and status";
            case "DEBUG" -> "DEBUG - Detailed diagnostic information for troubleshooting";
            default -> level.toUpperCase(Locale.ROOT);
        };
    }

    private void writeNonJsonBlock(Writer writer, String line, String sourceFileName, String serviceName) throws IOException {
        writer.write("================================================================================\n");
        writer.write("[RAW LINE — not valid JSON]\n");
        writer.write("Source File: ");
        writer.write(sourceFileName != null ? sourceFileName : "unknown");
        writer.write("\nService: ");
        writer.write(serviceName != null ? serviceName : "other");
        writer.write('\n');
        writer.write(expandEscapedControlCharacters(line));
        writer.write('\n');
        writer.write("================================================================================\n");
    }

    private static String expandEscapedControlCharacters(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\\t", "    ");
    }

    private static String firstString(JsonObject o, String... names) {
        if (o == null) {
            return null;
        }
        for (String n : names) {
            if (!o.has(n) || o.get(n).isJsonNull()) {
                continue;
            }
            JsonElement e = o.get(n);
            if (e.isJsonPrimitive() && ((JsonPrimitive) e).isString()) {
                return e.getAsString();
            }
            if (e.isJsonPrimitive()) {
                return e.getAsString();
            }
        }
        return null;
    }

    /**
     * Converts JSON message field to plain text (newlines preserved for parsed JSON strings).
     */
    private String jsonElementToPlainText(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isString()) {
                return p.getAsString();
            }
            return p.getAsString();
        }
        return gson.toJson(el);
    }

    private String jsonElementToOneLine(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return "";
        }
        String s;
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            s = el.getAsString();
        } else {
            s = gson.toJson(el);
        }
        return s.replace('\r', ' ').replace('\n', ' ');
    }

    private boolean verifyCurrentUserPassword(HttpServletRequest request, String password)
            throws SQLException, LdapConnectionException {
        int userId = UserContextUtil.getCurrentUserId(request);
        if (userId <= 0) {
            return false;
        }

        String sql = """
                SELECT Email, Password
                FROM people
                WHERE ID = ?
                  AND Deleted_date IS NULL
                LIMIT 1
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }

                String email = rs.getString("Email");
                String storedPassword = rs.getString("Password");
                if (LdapPlaceholderPassword.matches(storedPassword)) {
                    if (!LdapConfigUtil.isLdapEnabled()) {
                        return false;
                    }
                    return ldapAuthService.authenticateUser(email, password) != null;
                }

                return Objects.equals(storedPassword, password);
            }
        }
    }

    private void saveLastDeletedUser(String userName, String userEmail, int deletedCount) throws SQLException {
        JsonObject definition = new JsonObject();
        definition.addProperty("userName", userName);
        definition.addProperty("userEmail", userEmail);
        definition.addProperty("deletedFiles", deletedCount);
        definition.addProperty("deletedAt", Instant.now().toString());

        String sql = """
                INSERT INTO app_config (config_key, definition)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE definition = VALUES(definition)
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LAST_DELETE_CONFIG_KEY);
            ps.setString(2, gson.toJson(definition));
            ps.executeUpdate();
        }
    }

    private Map<String, Object> getLastDeletedInfo() {
        String sql = "SELECT definition FROM app_config WHERE config_key = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LAST_DELETE_CONFIG_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                JsonObject definition = JsonParser.parseString(rs.getString("definition")).getAsJsonObject();
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("userName", getString(definition, "userName"));
                info.put("userEmail", getString(definition, "userEmail"));
                info.put("deletedAt", getString(definition, "deletedAt"));
                info.put("deletedFiles", definition.has("deletedFiles") ? definition.get("deletedFiles").getAsInt() : 0);
                return info;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isValidConfirmation(String confirmationText, String userName) {
        if (confirmationText == null) {
            return false;
        }
        String normalized = confirmationText.trim();
        return normalized.equals(confirmationPhraseEn(userName)) || normalized.equals(confirmationPhraseAr(userName));
    }

    private static String confirmationPhraseEn(String userName) {
        return "I " + userName + " approve deleting old log files";
    }

    private static String confirmationPhraseAr(String userName) {
        return "أنا " + userName + " أوافق على حذف ملفات السجل القديمة";
    }

    private static String displayUserName(ActivityLogHelper.UserInfo userInfo) {
        if (userInfo != null && userInfo.userName != null && !userInfo.userName.isBlank()) {
            return userInfo.userName.trim();
        }
        if (userInfo != null && userInfo.userEmail != null && !userInfo.userEmail.isBlank()) {
            return userInfo.userEmail.trim();
        }
        return "Current User";
    }

    private static String getString(JsonObject body, String key) {
        if (body == null || !body.has(key) || body.get(key).isJsonNull()) {
            return null;
        }
        return body.get(key).getAsString();
    }

    private String generateZipFileName() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");
        return "axon-logs-" + now.format(formatter) + ".zip";
    }

    private static String extractLogDay(String fileName) {
        int dashIndex = fileName.indexOf('-');
        int extensionIndex = fileName.lastIndexOf(".log");
        if (dashIndex < 0 || extensionIndex <= dashIndex + 1) {
            return "current";
        }

        String datePart = fileName.substring(dashIndex + 1, extensionIndex);
        if (datePart.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return datePart;
        }

        return "current";
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message)
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        error.put("code", String.valueOf(statusCode));

        response.getWriter().write(gson.toJson(error));
    }

    private void sendJsonResponse(HttpServletResponse response, Object payload) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(gson.toJson(payload));
    }

    private static class DeleteResult {
        int deletedCount;
        List<String> failedFiles = new ArrayList<>();
    }

    private enum LogArchiveType {
        ERRORS,
        APPLICATION,
        AUDIT;

        static LogArchiveType fromFileName(String fileName) {
            if (fileName.startsWith("prod_errors")) {
                return ERRORS;
            }
            if (fileName.startsWith("prod_app")) {
                return APPLICATION;
            }
            if (fileName.startsWith("prod_audit")) {
                return AUDIT;
            }
            return null;
        }
    }

    private static boolean rawLooksLikeError(String rawEventText) {
        if (rawEventText == null) {
            return false;
        }
        String normalized = rawEventText.toLowerCase(Locale.ROOT);
        return normalized.contains("\"level\":\"error\"")
                || normalized.contains("\"level\" : \"error\"")
                || normalized.contains(" error:");
    }

    private static class ServicePattern {
        final String serviceName;
        final List<String> aliases;

        ServicePattern(String serviceName, String... aliases) {
            this.serviceName = serviceName;
            this.aliases = Arrays.asList(aliases);
        }

        boolean matches(String searchableText) {
            for (String alias : aliases) {
                String normalizedAlias = alias.toLowerCase(Locale.ROOT).replace('_', '-').replace('.', '-');
                if (searchableText.contains(normalizedAlias)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class SourceLogFile {
        final Path path;
        final LogArchiveType type;
        final String dayKey;
        final long lastModified;

        SourceLogFile(Path path, LogArchiveType type, String dayKey, long lastModified) {
            this.path = path;
            this.type = type;
            this.dayKey = dayKey;
            this.lastModified = lastModified;
        }

        String daySortKey() {
            return "current".equals(dayKey) ? "9999-12-31" : dayKey;
        }
    }

    private static class LogEvent {
        final String rawText;
        final JsonObject json;
        final String sourceFileName;
        final String dayKey;
        final String timestampSortKey;
        final String serviceName;
        final boolean error;

        LogEvent(String rawText, JsonObject json, String sourceFileName, String dayKey,
                 String timestampSortKey, String serviceName, boolean error) {
            this.rawText = rawText;
            this.json = json;
            this.sourceFileName = sourceFileName;
            this.dayKey = dayKey;
            this.timestampSortKey = timestampSortKey;
            this.serviceName = serviceName;
            this.error = error;
        }

        String daySortKey() {
            return "current".equals(dayKey) ? "9999-12-31" : dayKey;
        }
    }
}
