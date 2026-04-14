package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.AuthConfigUtil;
import com.example.budg_v2.util.DistributedRateLimiter;
import com.example.budg_v2.util.SessionManager;
import com.example.budg_v2.util.LdapConfigUtil;
import com.example.budg_v2.util.CookieUtil;
import com.example.budg_v2.util.CsrfTokenUtil;
import com.example.budg_v2.util.LdapPlaceholderPassword;
import com.example.budg_v2.service.LdapAuthService;
import com.example.budg_v2.exception.InactiveAccountException;
import com.example.budg_v2.exception.LdapConnectionException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

@WebServlet(urlPatterns = { "/login", "/auth/login" })
public class LoginServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(LoginServlet.class);
    private static final String ACCESS_COOKIE = "ACCESS_TOKEN";
    private static final String REFRESH_COOKIE = "REFRESH_TOKEN";
    private static final LdapAuthService ldapAuthService = new LdapAuthService();

    @Override
    public void init() throws ServletException {
        super.init();
        //system.out.println("=============================================");
        //system.out.println("DEBUG CHECK: LoginServlet initialized (System.out)");
        //system.out.println("=============================================");
        logger.info("DEBUG CHECK: LoginServlet initialized (Logger.info)");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Get client identifier for rate limiting
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        String clientIdentifier = DistributedRateLimiter.getClientIdentifier(clientIp, userAgent);

        // Parse request body first to get email for rate limiting
        JsonObject requestBody = null;
        String email = null;
        String password = null;

        try {
            requestBody = new Gson().fromJson(request.getReader(), JsonObject.class);
            email = requestBody.get("email").getAsString();
            password = requestBody.get("password").getAsString();
        } catch (Exception e) {
            sendErrorResponse(response, "Invalid request format. Please provide valid email and password.", 400, "general", "INVALID_REQUEST");
            return;
        }

        try {
            // Check distributed rate limiting (5 attempts per minute)
            // Checks both IP-based and email-based rate limiting
            if (!DistributedRateLimiter.isLoginAllowed(clientIdentifier, email)) {
                long remainingSeconds = DistributedRateLimiter.getRemainingSeconds(clientIdentifier);
                int ipAttemptCount = DistributedRateLimiter.getAttemptCount(clientIdentifier);
                int emailAttemptCount = DistributedRateLimiter.getAttemptCountByEmail(email);

                // Use the higher count for the error message
                int maxAttemptCount = Math.max(ipAttemptCount, emailAttemptCount);

                logger.warn(
                        "Login blocked for client {} / email {} due to rate limiting. IP attempts: {}/5, Email attempts: {}/5, Remaining: {} seconds",
                        clientIdentifier, email, ipAttemptCount, emailAttemptCount, remainingSeconds);

                long remainingMinutes = (remainingSeconds + 59) / 60; // Round up to minutes
                sendErrorResponse(response, 
                    "Too many login attempts (" + maxAttemptCount + "/5). Please try again in " + remainingMinutes + " minute(s).", 
                    429, "general", "RATE_LIMIT_EXCEEDED");
                return;
            }

            // Validate input
            if (email == null || email.trim().isEmpty()) {
                sendErrorResponse(response, "Email address is required", 400, "email", "EMAIL_REQUIRED");
                return;
            }
            if (password == null || password.trim().isEmpty()) {
                sendErrorResponse(response, "Password is required", 400, "password", "PASSWORD_REQUIRED");
                return;
            }

            // Enhanced input validation
            if (!isValidEmailOrUsername(email)) {
                logger.warn("Invalid email or username format attempted: {}", email);
                sendErrorResponse(response, "Invalid email or username format. Please enter a valid email address or username.", 400, "email", "INVALID_EMAIL_FORMAT");
                return;
            }

            if (!isValidPassword(password)) {
                logger.warn("Invalid password format attempted for email: {}", email);
                sendErrorResponse(response, "Invalid password format. Password must be between 1 and 100 characters.", 400, "password", "INVALID_PASSWORD_FORMAT");
                return;
            }

            // Debug logging
            logger.info("Attempting login for email: {} with password length: {}", email, password.length());

            // Check if account is locked before attempting authentication
            if (isAccountLocked(email)) {
                logger.warn("Login attempt blocked for locked account: {} from IP: {}", email, clientIp);
                sendErrorResponse(response, "Your account has been locked. Please contact your system administrator to unlock it.", 403, "general", "ACCOUNT_LOCKED");
                return;
            }

            // Authenticate user
            Map<String, Object> user;
            try {
                user = authenticateUser(email, password);
            } catch (LdapConnectionException e) {
                // LDAP connection failed - show error message to user
                logger.error("LDAP connection error during login for email: {} from IP: {}. Error: {}", 
                            email, clientIp, e.getMessage(), e);
                String errorCode = "LDAP_" + e.getErrorType().name();
                sendErrorResponse(response, e.getUserFriendlyMessage(), 503, "general", errorCode);
                return;
            } catch (InactiveAccountException e) {
                logger.warn("Login blocked for inactive account: {} from IP: {}", email, clientIp);
                sendErrorResponse(response, "Your account is inactive. Please contact your system administrator to reactivate it.", 403, "general", "ACCOUNT_INACTIVE");
                return;
            }

            if (user == null) {
                // Record failed attempt in distributed rate limiter
                DistributedRateLimiter.recordFailedAttempt(clientIdentifier, email, clientIp);
                logger.warn("Failed login attempt for email: {} from IP: {} - Password length: {}", email, clientIp,
                        password.length());

                // Check if this is the 5th failed attempt and lock the account
                int failedAttempts = DistributedRateLimiter.getAttemptCountByEmail(email);
                if (failedAttempts >= 5) {
                    lockUserAccount(email);
                    logger.warn("Account locked after 5 failed attempts for email: {}", email);
                    sendErrorResponse(response, "Your account has been locked due to too many failed login attempts. Please contact your system administrator to unlock it.",
                            403, "general", "ACCOUNT_LOCKED");
                    return;
                }

                sendErrorResponse(response, "Invalid email or password. Please check your credentials and try again.", 401, "password", "INVALID_CREDENTIALS");
                return;
            }

            // Record successful login
            DistributedRateLimiter.recordSuccessfulLogin(clientIdentifier, email, clientIp);
            logger.info("Successful login for user: {} from IP: {}", email, clientIp);

            // Update last login timestamp for the user
            updateLastLoginTime((Integer) user.get("id"));

            // Read auth config
            AuthConfigUtil.AuthConfig cfg = AuthConfigUtil.getConfig();

            // Create session in DB
            String sessionId = SessionManager.registerSession(
                    (Integer) user.get("id"),
                    clientIp,
                    userAgent);

            // Generate tokens (Nimbus)
            int userId = (Integer) user.get("id");
            String accessToken = JwtUtil.generateAccessToken(
                    userId,
                    email,
                    (String) user.get("first_name"),
                    (String) user.get("last_name"),
                    (String) user.get("role"),
                    (String) user.get("avatar_path"),
                    sessionId,
                    cfg.jwtValiditySeconds);
            String refreshToken = JwtUtil.generateRefreshToken(
                    userId,
                    email,
                    (String) user.get("first_name"),
                    (String) user.get("last_name"),
                    (String) user.get("role"),
                    (String) user.get("avatar_path"),
                    sessionId,
                    cfg.refreshValiditySeconds);

            // Persist refresh token metadata
            persistRefreshToken(userId, refreshToken, sessionId, cfg.refreshValiditySeconds);

            // Detect HTTPS
            boolean isHttps = request.isSecure() ||
                    "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));

            // Set ACCESS cookie (HttpOnly, SameSite=Strict)
            CookieUtil.addCookie(response, ACCESS_COOKIE, accessToken, 
                                (int) cfg.jwtValiditySeconds, isHttps, "Strict");

            // Set REFRESH cookie (HttpOnly, SameSite=Lax)
            CookieUtil.addCookie(response, REFRESH_COOKIE, refreshToken, 
                                (int) cfg.refreshValiditySeconds, isHttps, "Lax");

            CsrfTokenUtil.setCsrfCookie(request, response, CsrfTokenUtil.newToken());

            // Prepare response data
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("firstName", user.get("first_name"));
            responseData.put("lastName", user.get("last_name"));
            responseData.put("role", user.get("role"));
            responseData.put("avatarPath", user.get("avatar_path"));
            responseData.put("sessionId", sessionId);

            // Send success response
            response.setStatus(200);
            response.getWriter().write(new Gson().toJson(responseData));

        } catch (Exception e) {
            logger.error("Unexpected error during login", e);
            e.printStackTrace();
            sendErrorResponse(response, "An internal server error occurred. Please try again later or contact support if the problem persists.", 500, "general", "INTERNAL_SERVER_ERROR");
        }
    }

    private void persistRefreshToken(int userId, String refreshToken, String sessionId, long validitySeconds)
            throws SQLException {
        String jti;
        java.util.Date exp;
        try {
            com.nimbusds.jwt.SignedJWT jwt = com.nimbusds.jwt.SignedJWT.parse(refreshToken);
            jti = jwt.getJWTClaimsSet().getJWTID();
            exp = jwt.getJWTClaimsSet().getExpirationTime();
        } catch (java.text.ParseException e) {
            throw new RuntimeException("Failed to parse refresh token", e);
        }
        String sql = "INSERT INTO auth_tokens (token_id, user_id, type, is_revoked, issued_at, expires_at, session_id) VALUES (?,?, 'REFRESH', FALSE, CURRENT_TIMESTAMP, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jti);
            ps.setInt(2, userId);
            ps.setTimestamp(3, new java.sql.Timestamp(exp.getTime()));
            ps.setString(4, sessionId);
            ps.executeUpdate();
        }
    }

    private Map<String, Object> authenticateUser(String email, String password) throws SQLException, LdapConnectionException, InactiveAccountException {
        // Step 1: Check if user exists in database and get their password
        String dbPassword = checkUserPasswordInDatabase(email);
        
        // Step 2: If user doesn't exist, reject login immediately
        // Users must be created first through LDAP sync from admin panel
        if (dbPassword == null) {
            logger.warn("Login attempt for non-existent user: {}. User must be created through LDAP sync first.", email);
            return null; // Reject login - user doesn't exist
        }
        
        // Step 3: Determine authentication method based on password type
        if (LdapPlaceholderPassword.matches(dbPassword)) {
            // User is from LDAP - must authenticate via LDAP only
            if (!LdapConfigUtil.isLdapEnabled()) {
                logger.error("LDAP user attempted login but LDAP is disabled: {}", email);
                return null; // Reject - LDAP is required but disabled
            }
            
            logger.info("LDAP user detected, attempting LDAP authentication for email: {}", email);
            try {
                Map<String, Object> ldapUser = ldapAuthService.authenticateUser(email, password);
                if (ldapUser != null) {
                    logger.info("LDAP authentication successful for email: {}", email);
                    return convertLdapUserToSystemUser(ldapUser);
                } else {
                    // LDAP authentication failed - reject login (no fallback to database)
                    logger.warn("LDAP authentication failed for LDAP user: {}", email);
                    return null;
                }
            } catch (LdapConnectionException e) {
                // LDAP connection error - rethrow to show error message to user
                logger.error("LDAP connection failed for email: {}. Error: {}", email, e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                // Other LDAP errors - reject login (no fallback)
                logger.error("LDAP authentication error for LDAP user: {}. Error: {}", email, e.getMessage(), e);
                return null;
            }
        } else {
            // User is regular (not from LDAP) - authenticate via database only
            logger.info("Regular user detected, attempting database authentication for email: {}", email);
            return authenticateUserFromDatabase(email, password);
        }
    }

    /**
     * True when status name indicates the person must not log in (matches
     * {@code LOWER(primaryname) LIKE 'inactive%'} used elsewhere).
     */
    private static boolean isInactiveStatus(String statusName) {
        if (statusName == null) {
            return false;
        }
        return statusName.trim().toLowerCase().startsWith("inactive");
    }

    /**
     * Authenticate user from database. Login is allowed for any status except inactive.
     */
    private Map<String, Object> authenticateUserFromDatabase(String email, String password) throws SQLException, InactiveAccountException {
        // Single query: exclude only inactive status; all other statuses may log in
        String sql = """
                    SELECT
                        p.ID,
                        p.First_Name,
                        p.Last_Name,
                        p.Email,
                        p.Password,
                        p.Description,
                        p.Function_Name,
                        p.Function_Description,
                        p.Org_Unit_ID,
                        p.status_id,
                        p.System_Role,
                        p.profile_ImageID,
                        s.primaryname AS status_name,
                        ou.Name AS org_unit_name,
                        COALESCE(r.primaryname, 'User') AS role,
                        COALESCE(f.path, NULL) AS avatar_path
                    FROM people p
                    LEFT JOIN status s ON p.status_id = s.ID
                    LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
                    LEFT JOIN role r ON p.System_Role = r.id
                    LEFT JOIN files f ON p.profile_ImageID = f.id
                    WHERE p.Email = ?
                    AND p.Password = ?
                    AND p.Deleted_date IS NULL
                    AND (s.primaryname IS NULL OR LOWER(s.primaryname) NOT LIKE 'inactive%')
                    LIMIT 1
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            pstmt.setString(2, password);

            logger.info("Executing database authentication query for email: {}", email);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    logger.info("Database authentication successful for user: {}", email);
                    return mapResultSetToUser(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Database error during authentication for email: {}", email, e);
            throw e;
        }

        // No row: either wrong password or user is inactive — try load by credentials only to return inactive error
        return authenticateInactiveUserForError(email, password);
    }

    /**
     * Load user by email/password without status filter; if found and inactive, throw
     * {@link InactiveAccountException} so the client gets ACCOUNT_INACTIVE instead of invalid credentials.
     */
    private Map<String, Object> authenticateInactiveUserForError(String email, String password)
            throws SQLException, InactiveAccountException {
        String sql = """
                    SELECT
                        p.ID,
                        p.First_Name,
                        p.Last_Name,
                        p.Email,
                        p.Password,
                        p.Description,
                        p.Function_Name,
                        p.Function_Description,
                        p.Org_Unit_ID,
                        p.status_id,
                        p.System_Role,
                        p.profile_ImageID,
                        s.primaryname AS status_name,
                        ou.Name AS org_unit_name,
                        COALESCE(r.primaryname, 'User') AS role,
                        COALESCE(f.path, NULL) AS avatar_path
                    FROM people p
                    LEFT JOIN status s ON p.status_id = s.ID
                    LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
                    LEFT JOIN role r ON p.System_Role = r.id
                    LEFT JOIN files f ON p.profile_ImageID = f.id
                    WHERE p.Email = ?
                    AND p.Password = ?
                    AND p.Deleted_date IS NULL
                    LIMIT 1
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            pstmt.setString(2, password);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String statusName = rs.getString("status_name");
                    if (isInactiveStatus(statusName)) {
                        logger.warn("Login denied for user '{}' with inactive status: '{}'", email, statusName);
                        throw new InactiveAccountException(email, statusName);
                    }
                } else {
                    logger.warn("No user found in database for email: {} with matching password", email);
                }
            }
        }
        return null;
    }

    private static Map<String, Object> mapResultSetToUser(ResultSet rs) throws SQLException {
        Map<String, Object> user = new HashMap<>();
        user.put("id", rs.getInt("ID"));
        user.put("first_name", rs.getString("First_Name"));
        user.put("last_name", rs.getString("Last_Name"));
        user.put("email", rs.getString("Email"));
        user.put("description", rs.getString("Description"));
        user.put("function_name", rs.getString("Function_Name"));
        user.put("function_description", rs.getString("Function_Description"));
        user.put("org_unit_id", rs.getInt("Org_Unit_ID"));
        user.put("org_unit_name", rs.getString("org_unit_name"));
        user.put("status_id", rs.getInt("status_id"));
        user.put("status_name", rs.getString("status_name"));
        user.put("role", rs.getString("role"));
        user.put("avatar_path", rs.getString("avatar_path"));
        return user;
    }

    @SuppressWarnings("unused")
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode)
            throws IOException {
        sendErrorResponse(response, message, statusCode, "general", null);
    }

    @SuppressWarnings("unused")
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode, String field)
            throws IOException {
        sendErrorResponse(response, message, statusCode, field, null);
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode, String field, String errorCode)
            throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", message);
        errorResponse.put("field", field != null ? field : "general");
        if (errorCode != null) {
            errorResponse.put("errorCode", errorCode);
        }
        response.getWriter().write(new Gson().toJson(errorResponse));
    }

    /**
     * Convert LDAP user information to system user format and create/update in
     * database
     */
    private Map<String, Object> convertLdapUserToSystemUser(Map<String, Object> ldapUser) throws SQLException, InactiveAccountException {
        String email = (String) ldapUser.get("email");
        String ldapRole = (String) ldapUser.get("role");

        // First, try to find existing user in database
        Map<String, Object> existingUser = findUserByEmail(email);

        if (existingUser != null) {
            // Reject login only when status is inactive; any other status (including null) may log in
            String statusName = (String) existingUser.get("status_name");
            if (isInactiveStatus(statusName)) {
                logger.warn("Login denied for LDAP user '{}' with inactive status: '{}'", email, statusName);
                throw new InactiveAccountException(email, statusName);
            }
            // User exists and is not inactive, update their role if needed
            updateUserRoleFromLdap((Integer) existingUser.get("id"), ldapRole);
            existingUser.put("role", ldapRole);
            existingUser.put("ldap_user", true);
            existingUser.put("ldap_dn", ldapUser.get("dn"));
            existingUser.put("ldap_groups", ldapUser.get("groups"));
            return existingUser;
        } else {
            // Create new user in database
            Integer userId = createLdapUserInDatabase(ldapUser);

            Map<String, Object> user = new HashMap<>();
            user.put("id", userId);
            user.put("first_name", ldapUser.get("givenName"));
            user.put("last_name", ldapUser.get("sn"));
            user.put("email", email);
            user.put("description", "LDAP User");
            user.put("function_name", "");
            user.put("function_description", "");
            user.put("org_unit_id", 0);
            user.put("org_unit_name", "LDAP");
            user.put("status_id", 1); // Assume active
            user.put("status_name", "Active");
            user.put("role", ldapRole);
            user.put("avatar_path", null);
            user.put("ldap_user", true);
            user.put("ldap_dn", ldapUser.get("dn"));
            user.put("ldap_groups", ldapUser.get("groups"));

            return user;
        }
    }

    /**
     * Find user by email in database
     */
    private Map<String, Object> findUserByEmail(String email) throws SQLException {
        String sql = """
                    SELECT
                        p.ID,
                        p.First_Name,
                        p.Last_Name,
                        p.Email,
                        p.Description,
                        p.Function_Name,
                        p.Function_Description,
                        p.Org_Unit_ID,
                        p.status_id,
                        p.System_Role,
                        p.profile_ImageID,
                        s.primaryname AS status_name,
                        ou.Name AS org_unit_name,
                        COALESCE(r.primaryname, 'User') AS role,
                        COALESCE(f.path, NULL) AS avatar_path
                    FROM people p
                    LEFT JOIN status s ON p.status_id = s.ID
                    LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
                    LEFT JOIN role r ON p.System_Role = r.id
                    LEFT JOIN files f ON p.profile_ImageID = f.id
                    WHERE p.Email = ?
                    AND p.Deleted_date IS NULL
                    LIMIT 1
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("id", rs.getInt("ID"));
                    user.put("first_name", rs.getString("First_Name"));
                    user.put("last_name", rs.getString("Last_Name"));
                    user.put("email", rs.getString("Email"));
                    user.put("description", rs.getString("Description"));
                    user.put("function_name", rs.getString("Function_Name"));
                    user.put("function_description", rs.getString("Function_Description"));
                    user.put("org_unit_id", rs.getInt("Org_Unit_ID"));
                    user.put("org_unit_name", rs.getString("org_unit_name"));
                    user.put("status_id", rs.getInt("status_id"));
                    user.put("status_name", rs.getString("status_name"));
                    user.put("role", rs.getString("role"));
                    user.put("avatar_path", rs.getString("avatar_path"));

                    return user;
                }
            }
        }
        return null;
    }

    /**
     * Check if user exists in database and get their password
     * Returns the password if user exists, null if user doesn't exist
     */
    private String checkUserPasswordInDatabase(String email) throws SQLException {
        String sql = """
                    SELECT p.Password
                    FROM people p
                    WHERE p.Email = ?
                    AND p.Deleted_date IS NULL
                    LIMIT 1
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Password");
                }
            }
        }
        return null;
    }

    /**
     * Create LDAP user in database
     */
    private Integer createLdapUserInDatabase(Map<String, Object> ldapUser) throws SQLException {
        String sql = """
                    INSERT INTO people
                    (First_Name, Last_Name, Email, Password, Description, Function_Name, Function_Description,
                     Org_Unit_ID, status_id, System_Role, source_id, profile_ImageID, Created_Date, Last_Updated, lastupdateuser_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?)
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, (String) ldapUser.get("givenName"));
            pstmt.setString(2, (String) ldapUser.get("sn"));
            pstmt.setString(3, (String) ldapUser.get("email"));
            pstmt.setString(4, LdapPlaceholderPassword.storedValue()); // LDAP-linked users authenticate via LDAP only
            pstmt.setString(5, "LDAP User");
            pstmt.setString(6, "");
            pstmt.setString(7, "");
            pstmt.setNull(8, Types.INTEGER); // Org_Unit_ID
            pstmt.setInt(9, 1); // status_id (active)
            pstmt.setInt(10, getRoleIdFromName((String) ldapUser.get("role"))); // System_Role
            pstmt.setNull(11, Types.INTEGER); // source_id
            pstmt.setNull(12, Types.INTEGER); // profile_ImageID
            pstmt.setInt(13, 1); // lastupdateuser_id (system)

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating LDAP user failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int userId = generatedKeys.getInt(1);
                    logger.info("Created LDAP user in database with ID: {} for email: {}", userId,
                            ldapUser.get("email"));
                    return userId;
                } else {
                    throw new SQLException("Creating LDAP user failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Update user role from LDAP
     */
    private void updateUserRoleFromLdap(Integer userId, String ldapRole) throws SQLException {
        String sql = "UPDATE people SET System_Role = ?, Last_Updated = NOW() WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, getRoleIdFromName(ldapRole));
            pstmt.setInt(2, userId);

            int rowsUpdated = pstmt.executeUpdate();
            if (rowsUpdated > 0) {
                logger.info("Updated role for LDAP user ID: {} to role: {}", userId, ldapRole);
            }
        }
    }

    /**
     * Get role ID from role name
     */
    private int getRoleIdFromName(String roleName) throws SQLException {
        String sql = "SELECT id FROM role WHERE LOWER(primaryname) = LOWER(?) LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, roleName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }

        // Default to User role if not found
        logger.warn("Role '{}' not found in database, defaulting to User role", roleName);
        return 1; // Assuming User role has ID 1
    }

    /**
     * Validate email or username format
     */
    private boolean isValidEmailOrUsername(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        // Allow both email format (contains @) and username format (no @)
        if (email.contains("@")) {
            return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
        } else {
            // Username format: alphanumeric, dots, underscores, hyphens
            return email.matches("^[A-Za-z0-9._-]+$");
        }
    }

    /**
     * Validate email format (legacy method for backward compatibility)
     */

    /**
     * Validate password format (basic validation)
     */
    private boolean isValidPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        // Allow any password length (even 1 character)
        return password.length() >= 1 && password.length() <= 100;
    }

    /**
     * Get client IP address from request
     * Handles various proxy headers and load balancers for accurate IP detection
     * Supports: X-Forwarded-For, X-Real-IP, CF-Connecting-IP (Cloudflare),
     * True-Client-IP
     * 
     * @param request HTTP request
     * @return Client IP address (real IP when behind proxy)
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // Priority order for IP detection:
        // 1. CF-Connecting-IP (Cloudflare)
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.trim().isEmpty()) {
            return cfConnectingIp.trim();
        }

        // 2. True-Client-IP (some proxies)
        String trueClientIp = request.getHeader("True-Client-IP");
        if (trueClientIp != null && !trueClientIp.trim().isEmpty()) {
            return trueClientIp.trim();
        }

        // 3. X-Forwarded-For (most common proxy header)
        // Format: "client_ip, proxy1_ip, proxy2_ip"
        // We want the first (leftmost) IP which is the original client
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                String clientIp = ips[0].trim();
                // Validate it's not empty and not a private IP from proxy
                if (!clientIp.isEmpty()) {
                    return clientIp;
                }
            }
        }

        // 4. X-Real-IP (Nginx and some other proxies)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.trim().isEmpty()) {
            return xRealIp.trim();
        }

        // 5. X-Forwarded (alternative header)
        String xForwarded = request.getHeader("X-Forwarded");
        if (xForwarded != null && !xForwarded.trim().isEmpty()) {
            String[] ips = xForwarded.split(",");
            if (ips.length > 0 && !ips[0].trim().isEmpty()) {
                return ips[0].trim();
            }
        }

        // 6. Fallback to direct connection IP
        // This is the proxy's IP if behind proxy, or real client IP if direct
        // connection
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.isEmpty()) {
            return remoteAddr;
        }

        // 7. Last resort
        logger.warn("Could not determine client IP address from request headers");
        return "unknown";
    }

    /**
     * Update the last login timestamp for a user
     */
    private void updateLastLoginTime(int userId) {
        String sql = "UPDATE people SET last_User_LogIn = NOW() WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            int rowsUpdated = pstmt.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Updated last login time for user ID: {}", userId);
            } else {
                logger.warn("No rows updated for last login time for user ID: {}", userId);
            }

        } catch (SQLException e) {
            logger.error("Error updating last login time for user ID: {}", userId, e);
        }
    }

    /**
     * Check if an account is locked
     * 
     * @param email User email address
     * @return true if account is locked, false otherwise
     */
    private boolean isAccountLocked(String email) {
        String sql = "SELECT is_locked FROM people WHERE Email = ? AND Deleted_date IS NULL LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int isLocked = rs.getInt("is_locked");
                    return isLocked == 1;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking account lock status for email: {}", email, e);
            // On error, don't block login (fail open)
            return false;
        }

        return false;
    }

    /**
     * Lock a user account after too many failed login attempts
     * 
     * @param email User email address
     */
    private void lockUserAccount(String email) {
        String sql = """
                    UPDATE people
                    SET is_locked = 1,
                        locked_date = NOW(),
                        lock_reason = 'Too many failed login attempts'
                    WHERE Email = ? AND Deleted_date IS NULL
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            int rowsUpdated = pstmt.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Account locked for email: {}", email);
            } else {
                logger.warn("No account found to lock for email: {}", email);
            }

        } catch (SQLException e) {
            logger.error("Error locking account for email: {}", email, e);
            // Don't throw - locking failure shouldn't block the login response
        }
    }

}
