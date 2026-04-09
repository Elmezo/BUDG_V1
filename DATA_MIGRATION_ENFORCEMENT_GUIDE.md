# Data Migration Backend Enforcement Guide

## Overview
This document describes where and how to add backend enforcement checks for Data Migration feature flag.

## Feature Flag
The Data Migration feature is controlled by the `DATA_MIGRATION_ENABLED` configuration in the `app_config` table. When disabled (default), all Data Migration APIs should return 403 Forbidden.

## Implementation Pattern

### 1. Import Required Classes
```java
import com.example.unisonsearch.service.ConfigurationService;
```

### 2. Add Check at Beginning of Servlet Method
Add this check at the very beginning of any servlet method that handles Data Migration operations:

```java
ConfigurationService configService = new ConfigurationService();
if (!configService.isDataMigrationEnabled()) {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    JsonObject error = new JsonObject();
    error.addProperty("error", "Data Migration is not enabled. Please enable it in Admin Panel > System Settings > Environment.");
    response.getWriter().write(gson.toJson(error));
    return;
}
```

## APIs That Need Enforcement

### Current (Future Implementation)
When these APIs are implemented, they MUST include the enforcement check:

1. **Bulk Migrate Selected Objects**
   - Endpoint: `/api/bulk-migrate/selected` (or similar)
   - Check before processing any migration request

2. **Bulk Migrate All Objects**
   - Endpoint: `/api/bulk-migrate/all` (or similar)
   - Check before processing any migration request

3. **Export Migrated Data**
   - Endpoint: `/api/export/migrated-data` (or similar)
   - Check before generating export file

4. **Import Migrated Data**
   - Endpoint: `/api/import/migrated-data` (or similar)
   - Check before processing import file

## Important Notes

- **Security**: UI hiding is NOT sufficient. Backend enforcement is critical.
- **Default State**: Feature is disabled by default (`DATA_MIGRATION_ENABLED = false`)
- **Permission**: Only Admin and SuperAdmin can enable/disable the feature
- **Audit**: All configuration changes are logged

## Testing Checklist

When implementing new Data Migration APIs:

- [ ] Add enforcement check at the beginning of the servlet method
- [ ] Test with feature disabled - should return 403 Forbidden
- [ ] Test with feature enabled - should work normally
- [ ] Verify error message is clear and helpful
- [ ] Test with non-admin user - should not be able to enable feature

