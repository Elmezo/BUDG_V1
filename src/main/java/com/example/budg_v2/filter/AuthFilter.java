package com.example.budg_v2.filter;

import com.example.budg_v2.util.AppRoleNames;
import com.example.budg_v2.util.GuestAuthHelper;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.SessionManager;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.service.PermissionService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.google.gson.Gson;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebFilter(urlPatterns = {"/api/*", "/api/view/*", "/api/create/*", "/auth/*"})
public class AuthFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization code if needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Allow unauthenticated access for login/logout and public GET APIs
        String requestURI = httpRequest.getRequestURI();
        if (requestURI == null) {
            requestURI = "";
        }
        String method = httpRequest.getMethod();

        // Allow Unison search APIs for unauthenticated users (public search)
        if (requestURI != null && requestURI.startsWith("/api/unison/")) {
            chain.doFilter(request, response);
            return;
        }

        if ("/login".equals(requestURI) || "/logout".equals(requestURI) ||
                "/auth/login".equals(requestURI) || "/auth/logout".equals(requestURI) ||
                "/auth/refresh".equals(requestURI) ||
                requestURI.startsWith("/view") ||
                isPublicApi(requestURI, method)) {
            chain.doFilter(request, response);
            return;
        }

        // Allow CORS preflight requests to pass
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Get ACCESS token from cookie
        String token = getCookie(httpRequest, "ACCESS_TOKEN");

        if (token == null) {
            // Dashboard endpoints require authentication even for GET requests
            if (requestURI.startsWith("/api/dashboard/")) {
                sendUnauthorizedResponse(httpResponse, "No authentication token found");
                return;
            }
            // Segments accessible endpoint: auto-issue guest tokens so it can be used anonymously
            if (requestURI.startsWith("/api/segments/accessible") && "GET".equalsIgnoreCase(method)) {
                GuestAuthHelper.issueGuestTokens(httpRequest, httpResponse);
                chain.doFilter(request, response);
                return;
            }
            // Other segments endpoints still require authentication
            if (requestURI.equals("/api/segments") || requestURI.startsWith("/api/segments/")) {
                sendUnauthorizedResponse(httpResponse, "No authentication token found");
                return;
            }
            // DFCR API requires authentication to determine admin status
            if (requestURI.startsWith("/api/dfcr/")) {
                sendUnauthorizedResponse(httpResponse, "No authentication token found");
                return;
            }
            // Notifications API requires authentication to get user-specific notifications
            if (requestURI.startsWith("/api/notifications/")) {
                sendUnauthorizedResponse(httpResponse, "No authentication token found");
                return;
            }
            // System settings API requires authentication (SuperAdmin only)
            if (requestURI.startsWith("/api/system-settings/")) {
                sendUnauthorizedResponse(httpResponse, "No authentication token found");
                return;
            }
            // For other GET requests (read-only), allow access without token
            if ("GET".equalsIgnoreCase(method)) {
                // Enforce segment visibility for anonymous users: only Enterprise/unassigned objects
                if (!enforceSegmentVisibilityForGuest(httpRequest, httpResponse)) {
                    return;
                }
                chain.doFilter(request, response);
                return;
            }
            sendUnauthorizedResponse(httpResponse, "No authentication token found");
            return;
        }

        try {
            // Validate JWT token and extract claims
            // JWT tokens are stateless and self-contained, but we also validate the session
            // in the database for revocation support. See SessionCleanupJob for details.
            JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
            String sessionId = claims.getStringClaim("sessionId");
            boolean isGuest = GuestAuthHelper.isGuestRole(claims.getStringClaim("role"));
            if (!isGuest) {
                // For non-guest users, verify that the session exists in the database.
                // This allows for session revocation: if a session is deleted from auth_sessions
                // (e.g., by SessionCleanupJob or explicit logout), the token becomes invalid
                // even if it hasn't expired yet. This provides a balance between:
                // - Performance: JWT validation is stateless (no DB lookup for token validation)
                // - Security: Session revocation is possible via database session deletion
                if (sessionId == null || !SessionManager.exists(sessionId)) {
                    sendUnauthorizedResponse(httpResponse, "Invalid session");
                    return;
                }
                // Update last activity timestamp for the session
                SessionManager.touch(sessionId);
            }

            // Add user attributes to request
            Integer userId = null;
            
            // Try to extract userId using JwtUtil first
            try {
                userId = JwtUtil.getUserIdFromToken(token);
            } catch (Exception e) {
                logger.debug("AuthFilter: JwtUtil.getUserIdFromToken failed, trying direct claim extraction - Error: {}", e.getMessage());
            }
            
            // If JwtUtil failed, try direct claim extraction as fallback
            if (userId == null) {
                try {
                    // First try the new "userId" claim
                    Object userIdClaim = claims.getClaim("userId");
                    if (userIdClaim != null) {
                        if (userIdClaim instanceof Number) {
                            userId = ((Number) userIdClaim).intValue();
                        } else if (userIdClaim instanceof String) {
                            userId = Integer.parseInt((String) userIdClaim);
                        }
                    }
                    
                    // If userId claim not found or invalid, try the old "sub" claim
                    if (userId == null) {
                        Object subClaim = claims.getClaim("sub");
                        String subject = claims.getSubject();
                        
                        if (subClaim instanceof Number) {
                            userId = ((Number) subClaim).intValue();
                        } else if (subClaim instanceof String) {
                            userId = Integer.parseInt((String) subClaim);
                        } else if (subject != null) {
                            userId = Integer.parseInt(subject);
                        }
                    }
                    
                    if (userId == null) {
                        throw new IllegalArgumentException("Unable to extract userId from any claim");
                    }
                } catch (Exception e2) {
                    logger.error("AuthFilter: Failed to extract userId from token claims - Error: {}", e2.getMessage());
                    sendUnauthorizedResponse(httpResponse, "Invalid token: unable to extract user ID");
                    return;
                }
            }
            httpRequest.setAttribute("userEmail", claims.getStringClaim("email"));
            httpRequest.setAttribute("userName", (claims.getStringClaim("firstName") + " " + claims.getStringClaim("lastName")).trim());
            httpRequest.setAttribute("userRole", claims.getStringClaim("role"));
            httpRequest.setAttribute("userAvatar", claims.getStringClaim("avatarPath"));
            httpRequest.setAttribute("userId", userId);
            httpRequest.setAttribute("sessionId", sessionId);

            // Role checks
            String roleNorm = normalizeRole(claims.getStringClaim("role"));

            // Super admin-only API enforcement for /admin/* endpoints
            if (isAdminApi(requestURI)) {
                if (!isSuperAdmin(roleNorm)) {
                    sendForbidden(httpResponse, "Forbidden: super admin role required for admin APIs");
                    return;
                }
                // Fall through to chain.doFilter after this try-catch
            } else if (isSuperAdmin(roleNorm)) {
                // Fall through to chain.doFilter after this try-catch
            } else if (requestURI.startsWith("/api/user/segment-selection") && "POST".equals(method)) {
                // Any authenticated user can save their segment selection
            } else if (requestURI.startsWith("/api/refresh") && "POST".equals(method)) {
                // Any authenticated user can refresh their tokens
            } else if (requestURI.startsWith("/api/segments/") && "GET".equals(method)) {
                // Any authenticated user can view accessible segments
            } else if (requestURI.startsWith("/api/user/segment-selection") && "GET".equals(method)) {
                // Any authenticated user can view their segment selections
            } else {

            // Admin-only API enforcement for modifying endpoints
            if (isAdminOnlyApi(requestURI, method)) {
                if (!isAdmin(roleNorm) && !isSuperAdmin(roleNorm)) {
                    sendForbidden(httpResponse, "Forbidden: admin role required");
                    return;
                }
            }

            // Method-level restrictions per role
            try {
                String m = method == null ? "" : method.toUpperCase();
                if (isAdmin(roleNorm)) {
                    // Admin role: allowed to POST/PUT/PATCH generally, but DELETE is restricted
                    // Exception: allow DELETE for segments API as segment admins can manage their own segments
                    // Exception: allow DELETE for lock API as admins need to release locks
                    // Exception: allow DELETE for stakeholder edit endpoints as admins with edit permission can manage stakeholders
                    if ("DELETE".equals(m) && !requestURI.startsWith("/api/segments/") && !requestURI.startsWith("/api/lock")
                            && !requestURI.startsWith("/admin/api/quick-link")
                            && !requestURI.startsWith("/api/system-stakeholder/")
                            && !requestURI.startsWith("/api/process-stakeholder/")
                            && !requestURI.startsWith("/api/dataset-stakeholder/")
                            && !requestURI.startsWith("/api/product-stakeholder/")
                            && !requestURI.startsWith("/api/project-stakeholder/")
                            && !requestURI.startsWith("/api/policy-stakeholder/")
                            && !requestURI.startsWith("/api/glossary-stakeholder/")
                            && !requestURI.startsWith("/api/interface-stakeholder/")
                            && !requestURI.startsWith("/api/legal-entity-stakeholder/")
                            && !requestURI.startsWith("/api/committee-stakeholder/")
                            && !requestURI.startsWith("/api/client-stakeholder/")
                            && !requestURI.startsWith("/api/capability-stakeholder/")
                            && !requestURI.startsWith("/api/businessarea-stakeholder/")
                            && !requestURI.startsWith("/api/Attribute-stakeholder/")
                            && !requestURI.startsWith("/api/regulation/stakeholder/")) {
                        sendForbidden(httpResponse, "Forbidden: DELETE not allowed for Admin");
                        return;
                    }
                } else if (!isSuperAdmin(roleNorm)) {
                    // Web User or any other non-admin role: check role-based permissions
                    // Exception: allow POST to /api/history/visit for visit logging
                    // Exception: allow POST/PUT/DELETE to /api/follow/ for follow functionality
                    // Exception: allow GET to /api/user/permissions/ for permission checks
                    // Exception: allow POST/DELETE to /api/lock for users with edit permissions
                    // Exception: allow POST to /api/workflow_tasks/* for task completion - WorkflowTaskServlet handles permission checks internally
                    // Exception: allow POST to /api/workflow_instances for starting workflows - WorkflowInstanceServlet handles permission checks internally
                    boolean allowedByDefault = "GET".equals(m) || "OPTIONS".equals(m) || 
                        (requestURI.startsWith("/api/history/visit") && "POST".equals(m)) ||
                        (requestURI.startsWith("/api/follow/") && ("POST".equals(m) || "PUT".equals(m) || "DELETE".equals(m))) ||
                        (requestURI.startsWith("/api/user/permissions")) ||
                        (requestURI.startsWith("/api/workflow_tasks/") && "POST".equals(m)) ||
                        (requestURI.startsWith("/api/workflow_instances") && "POST".equals(m));
                    
                    if (!allowedByDefault) {
                        // Check if user has role-based permission for this operation
                        boolean hasRolePermission = checkRoleBasedPermission(userId, requestURI, m);
                        if (!hasRolePermission) {
                            sendForbidden(httpResponse, "Forbidden: You don't have permission for this action"); 
                            return; 
                        }
                    }
                }
            } catch (Exception __) { /* fall through */ }

            // Continue with the request
            // Enforce segment visibility for authenticated users on object-detail GET endpoints
            // Skip this check for HTML pages and view routes (they handle their own access control)
            if ("GET".equalsIgnoreCase(method) && !requestURI.startsWith("/view") && !requestURI.endsWith(".html")) {
                if (!enforceSegmentVisibilityForAuthenticated(httpRequest, httpResponse, userId)) {
                    return;
                }
            }
            } // end else (non-super-admin / non-special-path)

        } catch (Exception e) {
            // Token is invalid or expired (only from validation above; servlet exceptions propagate)
            logger.debug("AuthFilter: Token validation failed for URI: {} - Error: {}", requestURI, e.getMessage());
            sendUnauthorizedResponse(httpResponse, "Invalid or expired token");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Enforce that anonymous users can only access Enterprise/unassigned objects.
     * This blocks direct access to private/non-enterprise segment objects via GET /api/.../{id}.
     */
    private boolean enforceSegmentVisibilityForGuest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        SegmentObjectRef ref = parseSegmentProtectedObject(req.getRequestURI());
        if (ref == null) return true;
        try {
            int segId = SegmentAccessService.getObjectSegmentId(ref.objectId, ref.objectType);
            // Enterprise (1) is visible; objects with no segment assignment (-1) are not visible
            if (segId == 1) return true;
            if (segId == -1) {
                sendForbidden(resp, "Access denied. Object segment not found.");
                return false;
            }
            sendForbidden(resp, "Access denied. Object is not visible for anonymous users.");
            return false;
        } catch (SQLException e) {
            sendForbidden(resp, "Access denied. Unable to validate segment access.");
            return false;
        }
    }

    /**
     * Enforce that authenticated users can only access objects in segments they can access.
     * Stakeholder status does not bypass segment visibility.
     */
    private boolean enforceSegmentVisibilityForAuthenticated(HttpServletRequest req, HttpServletResponse resp, Integer userId) throws IOException {
        if (userId == null || userId <= 0) return true;
        SegmentObjectRef ref = parseSegmentProtectedObject(req.getRequestURI());
        if (ref == null) return true;
        try {
            // E-07: WebUsers and Guests can NEVER see soft-deleted objects.
            String userRoleAttr = req.getAttribute("userRole") != null ? req.getAttribute("userRole").toString() : "";
            String userRoleNorm = normalizeRole(userRoleAttr);
            boolean isWebUserOrGuest = !isAdmin(userRoleNorm) && !isSuperAdmin(userRoleNorm);
            if (isWebUserOrGuest && SegmentAccessService.isSoftDeleted(ref.objectId, ref.objectType)) {
                sendForbidden(resp, "Access denied. Object not found.");
                return false;
            }

            boolean hasSegmentAccess = SegmentAccessService.canAccessObject(userId, ref.objectId, ref.objectType);
            if (hasSegmentAccess) {
                // Segment access granted — full object visibility
                return true;
            }

            sendForbidden(resp, "Access denied. You don't have permission to view this object.");
            return false;
        } catch (SQLException e) {
            sendForbidden(resp, "Access denied. Unable to validate segment access.");
            return false;
        }
    }

    private static class SegmentObjectRef {
        final String objectType;
        final int objectId;
        SegmentObjectRef(String objectType, int objectId) {
            this.objectType = objectType;
            this.objectId = objectId;
        }
    }

    /**
     * Parse URIs like:
     * - /api/{module}/{id}
     * - /api/view/{module}/{id}
     * - /api/create/{module}/{id}
     * Returns null if not an object-detail request.
     */
    private SegmentObjectRef parseSegmentProtectedObject(String requestURI) {
        if (requestURI == null) return null;
        String path = requestURI.startsWith("/") ? requestURI.substring(1) : requestURI;
        String[] parts = path.split("/");
        if (parts.length < 3) return null;
        if (!"api".equalsIgnoreCase(parts[0])) return null;

        int idx = 1;
        // handle /api/view/... and /api/create/...
        if ("view".equalsIgnoreCase(parts[1]) || "create".equalsIgnoreCase(parts[1])) {
            idx = 2;
            if (parts.length < 4) return null;
        }

        String module = parts[idx];
        String idPart = parts[idx + 1];
        if (idPart == null || !idPart.matches("\\d+")) return null;
        int objectId = Integer.parseInt(idPart);

        String objectType = moduleToSegmentObjectType(module);
        if (objectType == null) return null;
        return new SegmentObjectRef(objectType, objectId);
    }

    /**
     * Map REST module path to segment_object_type.Type used in segment tables.
     */
    private String moduleToSegmentObjectType(String module) {
        if (module == null) return null;
        String m = module.trim().toLowerCase().replace("-", "_");
        return switch (m) {
            case "dataset", "datasets" -> "Dataset";
            case "system", "systems" -> "System";
            case "glossary", "glossaries" -> "Glossary";
            case "process", "processes" -> "Process";
            case "project", "projects" -> "Project";
            case "product", "products" -> "Product";
            case "policy", "policies" -> "Policy";
            case "legalentity", "legalentities", "legal_entity", "legal_entities" -> "LegalEntity";
            case "business_area", "business_areas", "businessarea", "businessareas" -> "BusinessArea";
            case "capability", "capabilities" -> "Capability";
            case "client", "clients" -> "Client";
            case "committee", "committees" -> "Committee";
            case "geography", "geographies" -> "Geography";
            case "regulation", "regulations" -> "Regulation";
            case "regulator", "regulators" -> "Regulator";
            case "regulatory_theme", "regulatory_themes" -> "RegulatoryTheme";
            case "interface", "interfaces", "system_interface", "system_interfaces" -> "SystemInterface";
            default -> null;
        };
    }

    private boolean isPublicApi(String uri, String method) {
        if (uri == null) return false;
        String m = method == null ? "" : method.trim().toUpperCase();

        // History API requires authentication even for GET requests
        if (uri.startsWith("/api/history/")) {
            return false;
        }

        // Bulk permissions API requires authentication even for GET requests
        if (uri.startsWith("/api/bulk/permissions")) {
            return false;
        }

        // User tokens API requires authentication even for GET requests
        if (uri.startsWith("/api/user/tokens")) {
            return false;
        }
		
	    if (uri.startsWith("/api/lock")) {
            return false;
        }
		if (uri.startsWith("/api/dashboard/")) {
            return false;
        }
        
        // Users list API requires authentication even for GET requests
        if (uri.startsWith("/api/users/")) {
            return false;
        }

        // Segments API requires authentication to know which user's segments to return
        if (uri.equals("/api/segments") || uri.startsWith("/api/segments/")) {
            return false;
        }
        
        // User segment selection API requires authentication but allows all authenticated users
        if (uri.startsWith("/api/user/segment-selection")) {
            return false;
        }

        // Notifications API requires authentication to get user-specific notifications
        if (uri.startsWith("/api/notifications/")) {
            return false;
        }

        // System settings API requires authentication (SuperAdmin only)
        if (uri.startsWith("/api/system-settings/")) {
            return false;
        }

        // Quick Link: must not use the blanket public-GET bypass — JWT is needed so
        // QuickLinkPublicServlet receives userId and can return per-user rows from user_quick_link.
        if ("/api/quick-link/current".equals(uri) || "/api/search/quick-link".equals(uri)) {
            return false;
        }

        // Any other GET request is public (unauthenticated users can read-only any API)
        return "GET".equals(m);
    }

    private boolean isAdminApi(String uri) {
        if (uri == null) return false;
        if (!uri.startsWith("/admin")) return false;
        // These endpoints are accessible to both Admins and Super Admins.
        // Role-specific enforcement is done inside each servlet.
        if (uri.equals("/admin/api/quick-link") || uri.startsWith("/admin/api/quick-link/")) return false;
        if (uri.startsWith("/admin/api/users/list")) return false;
        if (uri.startsWith("/admin/api/savedsearches")) return false;
        return true;
    }

    private boolean isAdminOnlyApi(String uri, String method) {
        String m = method == null ? "" : method.trim().toUpperCase();
        // Quick Link and user-list endpoints are admin-panel only (Admin or Super Admin)
        if (uri.equals("/admin/api/quick-link") || uri.startsWith("/admin/api/quick-link/") || uri.startsWith("/admin/api/users/list")
                || uri.startsWith("/admin/api/savedsearches")) {
            return true;
        }
        //web users ll recent
        if ("POST".equals(m) && uri.startsWith("/api/history/visit")) {
            return false;
        }

        // Allow any authenticated user to follow/unfollow objects
        if (uri.startsWith("/api/follow/")) {
            return false;
        }
        
        // Allow authenticated users to access their permissions
        if (uri.startsWith("/api/user/permissions")) {
            return false;
        }
        
        // Lock API is checked via role-based permissions, not admin-only
        if (uri.startsWith("/api/lock")) {
            return false;
        }
        
        // Allow role-based permissions to handle facet APIs (not strictly admin-only)
        // The checkRoleBasedPermission method will verify if user has proper permissions
        // This is a change from the original design to support role-based permissions
        // Admin-only APIs are now limited to admin panel and system settings
        if (uri.startsWith("/api/admin/") || uri.startsWith("/api/system-settings/")) {
            return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m) || "DELETE".equals(m);
        }

        // For regular facet APIs, let checkRoleBasedPermission handle permission checks
        return false;
    }

    private String normalizeRole(Object role) {
        if (role == null) return "";
        return String.valueOf(role).trim().toLowerCase().replace('_', ' ').replace('-', ' ');
    }

    private boolean isSuperAdmin(String roleNorm) {
        return AppRoleNames.isSuperAdminName(roleNorm);
    }

    private boolean isAdmin(String roleNorm) {
        return AppRoleNames.isAdminOnlyName(roleNorm);
    }
    
    /**
     * Check if user has role-based permission for the given URI and method
     * Uses the PermissionService to check if user has New/Edit permissions for the module
     * 
     * @param userId User ID
     * @param requestURI Request URI
     * @param method HTTP method (POST, PUT, DELETE)
     * @return true if user has permission
     */
    private boolean checkRoleBasedPermission(Integer userId, String requestURI, String method) {
        if (userId == null || userId <= 0) {
            return false;
        }
        
        // For lock API, allow it to pass through - LockServlet handles permission checks internally
        // LockServlet checks Edit permission based on moduleName from request body
        if (requestURI.startsWith("/api/lock")) {
            return true;
        }
        
        // For custom-fields API, allow it to pass through - CustomFieldServlet handles permission checks internally
        // CustomFieldServlet checks Edit permission based on facetId from request body
        if (requestURI.startsWith("/api/custom-fields")) {
            return true;
        }
        
        // For documents/upload API, allow it to pass through - DocumentUploadServlet handles permission checks internally
        // DocumentUploadServlet checks Edit permission based on facetType from request parameters
        if (requestURI.startsWith("/api/documents/upload")) {
            return true;
        }

        // DocumentManagementServlet: POST (URL batch add), PUT (edit), DELETE - permission checked in servlet via hasEditPermission
        // Without this, POST /api/documents is mapped to module "Documents" and canCreate fails; DELETE similarly
        if (requestURI.startsWith("/api/documents") && !requestURI.startsWith("/api/documents/upload")) {
            return true;
        }
        
        // For changerequest-analysis, changerequest-resolution, and cr-relationships APIs, allow them to pass through
        // These servlets handle permission checks internally (checking stakeholder status + edit permission)
        if (requestURI.startsWith("/api/changerequest-analysis") || 
            requestURI.startsWith("/api/changerequest-resolution") ||
            requestURI.startsWith("/api/cr-relationships")) {
            return true;
        }
        
        // Extract module name from URI
        String moduleName = extractModuleName(requestURI);
        if (moduleName == null) {
            return false;
        }
        
        try {
            PermissionService permissionService = new PermissionService();
            
            switch (method) {
                case "POST":
                    // POST = Create (New permission) or lock acquire
                    if (requestURI.startsWith("/api/lock")) {
                        // For lock API, check if user has Edit permission for the module
                        // Lock is needed for editing, so Edit permission is required
                        return permissionService.canEdit(userId, moduleName);
                    }
                    // For impact endpoints, POST means saving relationships (Edit permission)
                    if (requestURI.contains("-impact") && requestURI.endsWith("/save")) {
                        return permissionService.canEdit(userId, moduleName);
                    }
                    // For facet_X_facet endpoints (e.g., /api/glossary-x-glossary), POST means adding relationships (Edit permission)
                    if (requestURI.contains("-x-")) {
                        return permissionService.canEdit(userId, moduleName);
                    }
                    // For dataset-values endpoints (e.g., /api/dataset-values/{id}/save), POST means saving values (Edit permission)
                    if (requestURI.contains("/dataset-values/") && requestURI.endsWith("/save")) {
                        return permissionService.canEdit(userId, moduleName);
                    }
                    // For attribute endpoints, POST means creating/updating attributes (Edit permission)
                    // Attributes are part of Dataset facet editing
                    if (requestURI.startsWith("/api/attribute")) {
                        return permissionService.canEdit(userId, moduleName);
                    }
                    return permissionService.canCreate(userId, moduleName);
                    
                case "PUT":
                case "PATCH":
                    // PUT/PATCH = Edit permission
                    return permissionService.canEdit(userId, moduleName);
                    
                case "DELETE":
                    // DELETE - only admins can delete (handled in canDelete)
                    if (requestURI.startsWith("/api/lock")) {
                        // For lock API, allow users to release their own locks
                        return true;
                    }
                    return permissionService.canDelete(userId, moduleName);
                    
                default:
                    return false;
            }
        } catch (Exception e) {
            // Log error and deny access
            return false;
        }
    }
    
    /**
     * Extract module name from request URI
     * Handles URIs like /api/policy, /api/policy/1, /api/glossary-impact/products/save, 
     * /api/glossary-x-glossary, /api/glossary-x-system, etc.
     */
    private String extractModuleName(String requestURI) {
        if (requestURI == null) return null;
        
        String path = requestURI.startsWith("/") ? requestURI.substring(1) : requestURI;
        String[] parts = path.split("/");
        
        if (parts.length < 2) return null;
        if (!"api".equalsIgnoreCase(parts[0])) return null;
        
        String module = parts[1];
        
        // Handle impact endpoints: /api/{module}-impact/*
        if (module.endsWith("-impact")) {
            // Extract module name from "glossary-impact" -> "glossary"
            String baseModule = module.substring(0, module.length() - "-impact".length());
            return mapModuleName(baseModule);
        }
        
        // Handle facet_X_facet endpoints: /api/{facet1}-x-{facet2}/*
        // Examples: /api/glossary-x-glossary, /api/glossary-x-system, /api/policy-x-policy
        // Use the first facet as the module name (the one being edited)
        if (module.contains("-x-")) {
            String[] facetParts = module.split("-x-");
            if (facetParts.length > 0) {
                // Use the first facet (e.g., "glossary" from "glossary-x-glossary")
                return mapModuleName(facetParts[0]);
            }
        }
        
        // Handle dataset-values endpoints: /api/dataset-values/{id}/save
        // These are part of Dataset facet, so use "Data Sets"
        if (module.equals("dataset-values")) {
            return "Data Sets";
        }
        
        // Handle attribute endpoints: /api/attribute
        // Attributes are part of Dataset facet, so use "Data Sets"
        if (module.equals("attribute")) {
            return "Data Sets";
        }
        
        // Handle stakeholder endpoints: /api/{facet}-stakeholder/*
        // Examples: /api/businessarea-stakeholder, /api/glossary-stakeholder, /api/system-stakeholder
        // Extract the facet name from the pattern
        if (module.endsWith("-stakeholder")) {
            String baseModule = module.substring(0, module.length() - "-stakeholder".length());
            return mapModuleName(baseModule);
        }
        
        // Handle regular endpoints
        return mapModuleName(module);
    }
    
    /**
     * Map API path segment to module name
     */
    private String mapModuleName(String module) {
        return switch (module.toLowerCase()) {
            case "policy", "policies" -> "Policy";
            case "system", "systems" -> "System";
            case "dataset", "datasets" -> "Data Sets";
            case "glossary", "glossaries" -> "Glossary";
            case "process", "processes" -> "Process";
            case "project", "projects" -> "Project";
            case "product", "products" -> "Product";
            case "regulation", "regulations" -> "Regulation";
            case "regulator", "regulators" -> "Regulator";
            case "interface", "interfaces" -> "Interface";
            case "system-interface", "system_interface", "systeminterfaces" -> "Interface";
            case "client", "clients" -> "Client";
            case "committee", "committees" -> "Committee";
            case "geography", "geographies" -> "Geography";
            case "capability", "capabilities" -> "Capability";
            case "business-area", "business_area", "business-areas", "business_areas" -> "Business Area";
            case "org-unit", "org_unit", "org-units", "org_units" -> "Org Unit";
            case "people" -> "People";
            case "legal", "legal-entity", "legalentity", "legal-entities", "legal_entities" -> "Legal Entity";
            case "changerequest", "changerequests", "change-request", "change-requests", "change_request", "change_requests" -> "Change Requests";
            case "lock" -> null; // Lock API handled separately
            default -> capitalizeFirst(module);
        };
    }
    
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, String> err = new HashMap<>();
        err.put("error", message);
        err.put("code", "FORBIDDEN");
        response.getWriter().write(new Gson().toJson(err));
    }

    @Override
    public void destroy() {
        // Cleanup code if needed
    }

    private String getCookie(HttpServletRequest request, String name) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", message);
        errorResponse.put("code", "UNAUTHORIZED");

        response.getWriter().write(new Gson().toJson(errorResponse));
    }
}