# BUDG Technical Analysis Report

## Concrete Evidence from Codebase

This document provides a factual technical analysis extracted directly from the BUDG codebase. All references are to actual classes, methods, configuration keys, and code patterns.

---

## 1. Backend Architecture

### 1.1 Servlet Structure

**Evidence**: 70 servlet classes in `src/main/java/com/example/budg_v2/`

| Servlet | Purpose |
|---------|---------|
| `DatasetServlet.java` | Dataset CRUD operations |
| `SystemServlet.java` | System entity management |
| `GlossaryServlet.java` | Glossary term management |
| `UnisonSearchServlet.java` | Unified search endpoint |
| `WorkflowInstanceServlet.java` | Workflow execution |
| `AttributeServlet.java` | Attribute management |

**Pattern Observed**: Each servlet handles HTTP methods (GET/POST/PUT/DELETE) and delegates to service layer.

### 1.2 Service Layer Separation

**Evidence**: 65 service classes in `src/main/java/com/example/budg_v2/service/`

| Service | Responsibility |
|---------|---------------|
| `UnisonSearchService.java` | Search orchestration (11,347 lines) |
| `GraphTraversalService.java` | BFS graph traversal (544 lines) |
| `LdapAuthService.java` | LDAP authentication (561 lines) |
| `SegmentAccessService.java` | Segment-based access control |
| `PermissionService.java` | Role-based permission checks |
| `WorkflowEngine.java` | Workflow instance management |

### 1.3 DAO Pattern

**Evidence**: 75 DAO classes in `src/main/java/com/example/budg_v2/dao/`

| DAO | Table |
|-----|-------|
| `WorkflowInstanceDAO.java` | `workflow_instances` |
| `WorkflowTaskDAO.java` | `workflow_tasks` |
| `ChangeRequestDAO.java` | `change_requests` |
| `CRStakeholderDAO.java` | Stakeholder junction tables |

**SQL Handling**: Parameterized queries via `PreparedStatement` throughout.

---

## 2. Search & Relationship Engine

### 2.1 GraphTraversalService Implementation

**File**: `src/main/java/com/example/unisonsearch/service/GraphTraversalService.java`

**BFS Algorithm** (Lines 106-218):
```java
Queue<TraversalNode> queue = new LinkedList<>();
queue.offer(new TraversalNode(normalizedSeedFacet, seedIds, 0));

while (!queue.isEmpty()) {
    TraversalNode node = queue.poll();
    if (node.depth >= maxDepth) continue;
    // ... traverse relationships
}
```

**Depth Control**:
- `maxDepth` parameter limits traversal (default configurable)
- `DEPTH_LIMITS` map per facet type in `RelationshipManager`

**Deduplication**:
```java
Map<String, Set<Integer>> visited = new HashMap<>();
// ...
if (!targetVisited.contains(id)) {
    newIds.add(id);
    targetVisited.add(id);
}
```

**Performance Safeguards**:
- Seed limit: 1000 IDs (`seedIds.size() > 1000`)
- Total result limit: 10,000 IDs (`maxTotalResults = 10000`)
- Truncation tracking via `TraversalStats`

### 2.2 Relationship Definitions

**File**: `src/main/java/com/example/unisonsearch/service/RelationshipManager.java`

**ALLOWED_TARGETS Map**: Defines valid traversal paths per facet:
```java
ALLOWED_TARGETS.get("dataset") → {"system", "attribute", "glossary", ...}
```

**RelationshipService Methods** (Lines 265-500):
- `getDatasetsBySystem(int systemId)`
- `getGlossariesByDatasetIncludingAttributes(int datasetId)`
- `getAttributesByGlossary(int glossaryId)`

### 2.3 UnisonSearchService

**File**: `src/main/java/com/example/unisonsearch/service/UnisonSearchService.java`

**Compound Query Support** (Lines 83-699):
- Operators: `FIND`, `AND`, `OR`, `NOT`
- Set operations on result IDs
- Filter application post-traversal

**Correlation ID Usage** (Line 146):
```java
String correlationId = "US-" + System.currentTimeMillis() + "-" + REQUEST_COUNTER.incrementAndGet();
```

---

## 3. Configuration-Driven Logic

### 3.1 ConfigurationService

**File**: `src/main/java/com/example/unisonsearch/service/ConfigurationService.java`

**Configuration Keys** (from `app_config` table):

| Config Key | Method | Default |
|------------|--------|---------|
| `UNISON_FUZZY_DEFAULT` | `getFuzzySearchConfig()` | `false` |
| `HIDE_NON_PUBLIC_OBJECTS` | `shouldHideNonPublicObjects()` | `false` |
| `AUTHORIZATION_ENABLED` | `getAuthorizationEnabled()` | `true` |
| `EVENT_MONITOR_ENABLED` | `getEventMonitorEnabled()` | `false` |
| `SEARCH_MIGRATION_V1_TO_V2` | `getSearchMigrationFlag()` | `false` |
| `DATA_MIGRATION_ENABLED` | `isDataMigrationEnabled()` | `false` |
| `INFORMATION_SEGMENTATION_ENABLED` | `isInformationSegmentationEnabled()` | `false` |
| `ENTERPRISE_SEGMENT_DEFAULT` | `isEnterpriseSegmentDefault()` | `false` |
| `ASSIGNED_SEGMENTS_DEFAULT` | `isAssignedSegmentsDefault()` | `false` |
| `UNISON_DEFAULTS` | `getUnisonDefaults()` | JSON object |

### 3.2 Feature Flag Pattern

**Usage Example** (ConfigurationService.java, Line 102):
```java
public boolean getSearchMigrationFlag() {
    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(
             "SELECT definition FROM app_config WHERE config_key = ?")) {
        ps.setString(1, Constants.CONFIG_SEARCH_MIGRATION_V1_TO_V2);
        // ...
    }
    return false; // Default
}
```

### 3.3 Dynamic Filter Configuration

**AppConfigServlet.java**: CRUD for `app_config` table entries.

**DisplaySettingsServlet.java**: Dynamic facet visibility via `UNISON_DEFAULTS`.

---

## 4. Workflow & Authorization Model

### 4.1 WorkflowEngine

**File**: `src/main/java/com/example/budg_v2/util/WorkflowEngine.java`

**Methods**:
| Method | Purpose |
|--------|---------|
| `startWorkflow(int processDefinitionId, Integer changeRequestId, int startedBy)` | Create workflow instance |
| `completeTask(int instanceId, int taskId, String decision, String comment, int userId)` | Advance workflow |
| `getWorkflowStatus(int changeRequestId)` | Query workflow state |
| `addTaskComment(int taskId, String comment, int userId)` | Add task comment |
| `getActiveTask(int instanceId)` | Get current pending task |

**Decisions**: `"approve"`, `"reject"`, `"complete"`, `"rework"`

### 4.2 WorkflowAuthorizationUtil

**File**: `src/main/java/com/example/budg_v2/util/WorkflowAuthorizationUtil.java`

**Role Resolution**:

| Method | Purpose |
|--------|---------|
| `isUserRequestor(int userId, int changeRequestId)` | Check if user created the CR |
| `checkUserHasRoleForTask(int userId, int changeRequestId, String taskRoleName)` | Verify stakeholder role |
| `normalizeRoleName(String roleName)` | Standardize role format |

**Role Normalization** (Lines 128-150):
```java
// "3:DATASET_OWNER" → "DATASET_OWNER"
// "Dataset Owner" → "DATASET_OWNER"
String cleanRole = roleName.trim();
if (cleanRole.contains(":")) {
    cleanRole = cleanRole.substring(colonIndex + 1).trim();
}
return cleanRole.toUpperCase().replaceAll("\\s+", "_");
```

### 4.3 Workflow Components

| Component | Purpose |
|-----------|---------|
| `WorkflowInstanceDAO.java` | Workflow instance persistence |
| `WorkflowTaskDAO.java` | Task state management |
| `WorkflowNotificationRuleServlet.java` | Notification configuration |
| `DefaultWorkflowRestartServlet.java` | Workflow restart handling |

---

## 5. Security Implementation

### 5.1 AuthFilter

**File**: `src/main/java/com/example/budg_v2/filter/AuthFilter.java`

**URL Patterns**: `/api/*`, `/api/view/*`, `/api/create/*`, `/auth/*`

**JWT Validation** (Lines 113-143):
```java
JWTClaimsSet claims = JwtUtil.parseAndValidate(token);
String sessionId = claims.getStringClaim("sessionId");
if (!SessionManager.exists(sessionId)) {
    sendUnauthorizedResponse(httpResponse, "Invalid session");
    return;
}
SessionManager.touch(sessionId);
```

**Role-Based Checks**:
```java
String roleNorm = normalizeRole(claims.getStringClaim("role"));
if (isSuperAdmin(roleNorm)) { chain.doFilter(request, response); return; }
if (isAdmin(roleNorm)) { /* Admin restrictions */ }
```

**Segment-Based Access** (Lines 247-283):
```java
private boolean enforceSegmentVisibilityForGuest(HttpServletRequest req, HttpServletResponse resp) {
    int segId = SegmentAccessService.getObjectSegmentId(ref.objectId, ref.objectType);
    if (segId == 1) return true; // Enterprise segment
    sendForbidden(resp, "Access denied. Object is not visible for anonymous users.");
    return false;
}
```

**Permission Service Integration** (Lines 469-518):
```java
private boolean checkRoleBasedPermission(Integer userId, String requestURI, String method) {
    PermissionService permissionService = new PermissionService();
    switch (method) {
        case "POST": return permissionService.canCreate(userId, moduleName);
        case "PUT": return permissionService.canEdit(userId, moduleName);
        case "DELETE": return permissionService.canDelete(userId, moduleName);
    }
}
```

### 5.2 LdapAuthService

**File**: `src/main/java/com/example/budg_v2/service/LdapAuthService.java`

**Library**: UnboundID LDAP SDK

**Methods**:
| Method | Purpose |
|--------|---------|
| `authenticate(String username, String password)` | LDAP bind authentication |
| `searchUser(String filter)` | User lookup |
| `syncUsersFromLdap()` | Batch user synchronization |

### 5.3 AxonLogger (Structured Logging)

**File**: `src/main/java/com/example/budg_v2/util/AxonLogger.java`

**MDC Context**:
```java
public static void setRequestData(HttpServletRequest request) {
    MDC.put("http.method", request.getMethod());
    MDC.put("http.url", request.getRequestURI());
    MDC.put("client.ip", request.getRemoteAddr());
}

public static void setUserData(Object userId, Object userEmail) {
    if (userId != null) MDC.put("user.id", String.valueOf(userId));
    if (userEmail != null) MDC.put("user.email", String.valueOf(userEmail));
}
```

**Log Levels**: `logError`, `logWarn`, `logInfo`, `logDebug`, `logAudit`

**Audit Logging** (Lines 120-136):
```java
public static void logAudit(String message, KeyValue... keyValues) {
    setContext(keyValues);
    AUDIT_LOGGER.info(message);
    // Audit logs written to separate appender
}
```

---

## 6. Frontend Architecture

### 6.1 Technology Stack

**Files**: 127+ JavaScript files in `src/main/webapp/assets/js/`

| File | Purpose |
|------|---------|
| `api-service.js` | HTTP client wrapper |
| `permission-helper.js` | Client-side permission caching |
| `auth-helper.js` | Token management |
| `search-filter.js` | Search UI logic |
| `lock-manager.js` | Optimistic locking UI |

**No Framework Dependency**: Vanilla JavaScript with modular structure.

### 6.2 Permission Helper

**File**: `src/main/webapp/assets/js/permission-helper.js`

**Pattern**: Singleton with caching
```javascript
class PermissionHelper {
    constructor() {
        this.permissions = {};
        this.loaded = false;
        this.loadPromise = null;
    }
    
    async init(moduleName = null) {
        if (this.loadPromise) return this.loadPromise;
        this.loadPromise = this._fetchPermissions(moduleName);
        return this.loadPromise;
    }
    
    canCreate(moduleName) { /* ... */ }
    canEdit(moduleName) { /* ... */ }
    canDelete(moduleName) { /* ... */ }
    
    applyPermissions(moduleName, options = {}) {
        // Auto-hide UI elements based on permissions
    }
}
```

**Global Instance**: `window.permissionHelper = new PermissionHelper();`

### 6.3 Dual Enforcement

| Layer | Class/File | Mechanism |
|-------|------------|-----------|
| Backend | `AuthFilter.java` | JWT validation, role checks, segment access |
| Backend | `PermissionService.java` | Database permission lookup |
| Frontend | `permission-helper.js` | UI element visibility |

---

## 7. Operational Characteristics

### 7.1 Stateless Request Handling

- HTTP servlets with no server-side session state
- JWT tokens carry all authentication context
- Database is single source of truth for session revocation

### 7.2 Correlation ID Tracking

**UnisonSearchService** (Line 146):
```java
String correlationId = "US-" + System.currentTimeMillis() + "-" + REQUEST_COUNTER.incrementAndGet();
```

**LdapSyncService** (Lines 79-87):
```java
correlationId = MDC.get("correlation_id");
if (correlationId == null) {
    correlationId = UUID.randomUUID().toString();
    MDC.put("correlation_id", correlationId);
}
```

**GraphTraversalService** (Line 73):
```java
String logPrefix = correlationId != null ? "[" + correlationId + "] " : "";
```

---

## 8. Identified Gaps

### 8.1 Missing or Unclear

| Area | Status |
|------|--------|
| Rate limiting | Not observed in codebase |
| Request body validation framework | Manual validation per servlet |
| API versioning | Not observed |
| Distributed tracing (OpenTelemetry) | Not observed |
| Circuit breaker pattern | Not observed |

### 8.2 Potential Improvements

1. **Centralized Validation**: Consider Bean Validation (JSR-380) for request DTOs
2. **API Gateway**: Consider adding rate limiting and request throttling
3. **Metrics Export**: Consider Micrometer for metrics to Prometheus/Grafana

---

## 9. Technology Summary

| Layer | Technology | Evidence |
|-------|------------|----------|
| Runtime | Java 17 | `pom.xml` line 14 |
| Servlet | Jakarta Servlet 6.0 | `pom.xml` line 42 |
| Container | Tomcat 11 | `pom.xml` line 19 |
| Database | Microsoft SQL Server | `mssql-jdbc` dependency |
| JSON | Gson 2.11 | `pom.xml` line 59 |
| JWT | Nimbus JOSE+JWT 9.40 | `pom.xml` line 167 |
| LDAP | UnboundID SDK 7.0.1 | `pom.xml` line 154 |
| Logging | Logback + SLF4J | `pom.xml` lines 63-79 |
| Search | Elasticsearch 8.14 | `pom.xml` lines 179-192 |
| Build | Maven | `pom.xml` |

---

*Document generated from BUDG_V2 codebase analysis.*
