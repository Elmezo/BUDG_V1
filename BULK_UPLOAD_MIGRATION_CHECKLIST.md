# Bulk Upload Architecture Migration - Checklist

## ✅ Completed Steps

### 1. Frontend Changes
- ✅ Added `RELATIONSHIP_ENTITIES` configuration with 72 relationship types
- ✅ Added `ROLE_ENTITIES` configuration with 15 role types
- ✅ Added upload options for all relationships and roles (INSERT + DELETE only)
- ✅ Created `getEntitiesForUploadType()` helper function
- ✅ Updated `StepChooseFile.jsx` to filter entities by upload type
- ✅ Added useEffect to reset selections when upload type changes
- ✅ Unified all upload operations to use "Upload New Items" instead of "Add New Items"
- ✅ Updated permission filtering logic for relationships and roles

### 2. Backend Restructuring
- ✅ Created new directory structure:
  ```
  bulk/
  ├── common/          # Shared utilities
  ├── objects/         # All existing object servlets (MOVED)
  ├── relationships/   # New relationship handlers
  │   ├── base/
  │   └── handlers/
  └── roles/          # New role handlers
      ├── base/
      └── handlers/
  ```
- ✅ Moved all object servlets to `bulk/objects/`
- ✅ Updated package declarations in object servlets
- ✅ Verified object servlets still work (no URL changes needed)

### 3. Common Utilities
- ✅ Created `BulkUploadUtil.java` with helper methods:
  - JSON parsing (getString, getInteger, getBoolean)
  - PreparedStatement helpers (setNullableInt, setNullableString, setNullableBoolean)
  - Validation helpers (createValidationError, createValidationSuccess)
  - Coalesce methods

### 4. Relationship Infrastructure
- ✅ Created `RelationshipUploadHandler` interface
- ✅ Created example handler: `PolicySystemRelationshipHandler`
- ✅ Created comprehensive README with instructions

### 5. Role Infrastructure
- ✅ Created `RoleUploadHandler` interface
- ✅ Created example handler: `PolicyRoleHandler`
- ✅ Created comprehensive README with instructions

### 6. Documentation
- ✅ Main README in `bulk/` directory
- ✅ README in `relationships/handlers/`
- ✅ README in `roles/handlers/`
- ✅ This checklist document

---

## 🚧 Next Steps (TO DO)

### Phase 1: Create Main Servlets

#### A. RelationshipBulkUploadServlet
- [ ] Create `relationships/base/BaseRelationshipBulkUploadServlet.java`
  - Abstract class with shared upload logic
  - File handling
  - Job creation
  - Python validation integration
  - Row processing loop
  - WebSocket broadcasting
  - Error handling

- [ ] Create `relationships/RelationshipBulkUploadServlet.java`
  - Extends BaseRelationshipBulkUploadServlet
  - @WebServlet("/api/bulk/relationship/upload")
  - Handler registry (static Map)
  - Handler registration method
  - doPost() to route to appropriate handler

#### B. RoleBulkUploadServlet
- [ ] Create `roles/base/BaseRoleBulkUploadServlet.java`
  - Similar to BaseRelationshipBulkUploadServlet
  - Handle role-specific logic

- [ ] Create `roles/RoleBulkUploadServlet.java`
  - Extends BaseRoleBulkUploadServlet
  - @WebServlet("/api/bulk/role/upload")
  - Handler registry
  - doPost() to route to appropriate handler

### Phase 2: Implement Handlers

#### Priority Relationships (implement first)
- [ ] PolicySystemRelationshipHandler (✅ already created as example)
- [ ] PolicyClientRelationshipHandler
- [ ] PolicyProcessRelationshipHandler
- [ ] ProcessSystemRelationshipHandler
- [ ] ProcessClientRelationshipHandler
- [ ] SystemClientRelationshipHandler

#### Priority Roles (implement first)
- [ ] PolicyRoleHandler (✅ already created as example)
- [ ] SystemRoleHandler
- [ ] ProcessRoleHandler
- [ ] ProjectRoleHandler

#### Remaining Relationships (72 total)
- [ ] Attribute X Attribute
- [ ] Attribute X Physical Field
- [ ] Business Area X Glossary
- [ ] Business Area X Process
- [ ] Business Area X System
- [ ] Capability X Business Area
- [ ] Capability X Client
- [ ] Capability X Glossary
- [ ] Capability X Legal Entity
- [ ] Capability X Process
- [ ] Capability X Product
- [ ] Capability X System
- [ ] Committee X Capability
- [ ] Committee X Committee
- [ ] Data Set X Client
- [ ] Data Set X Legal Entity
- [ ] Data Set X Product
- [ ] Glossary X Client
- [ ] Glossary X Glossary
- [ ] Glossary X Product
- [ ] Glossary X System
- [ ] Interface X Glossary
- [ ] Process X System Interface
- [ ] Legal Entity X Geography
- [ ] Regulator X Geography
- [ ] Local Data Quality Rule X Technical Reference
- [ ] Standard Data Quality Rule X Technical Reference
- [ ] People X People
- [ ] Policy X Attribute
- [ ] Policy X Business Area
- [ ] Policy X Data Set
- [ ] Policy X Glossary
- [ ] Policy X Legal Entity
- [ ] Policy X Policy
- [ ] Policy X Product
- [ ] Policy X Project
- [ ] Process X Attribute
- [ ] Process X Data Set
- [ ] Process X Glossary
- [ ] Process X Legal Entity
- [ ] Process X Process
- [ ] Process X Product
- [ ] Product X Business Area
- [ ] Product X Client
- [ ] Product X Legal Entity
- [ ] Project X Attribute
- [ ] Project X Business Area
- [ ] Project X Capability
- [ ] Project X Client
- [ ] Project X Data Set
- [ ] Project X Glossary
- [ ] Project X Process
- [ ] Project X Product
- [ ] Project X Project
- [ ] Project X System
- [ ] Regulation X Policy
- [ ] Regulation X Product
- [ ] Regulation X Project
- [ ] Regulation X Regulator
- [ ] Regulation X Regulatory Theme
- [ ] Segment X Object
- [ ] System X Data Onboarding Rule
- [ ] System X Glossary
- [ ] System X Legal Entity
- [ ] System X Product
- [ ] System X Resource

#### Remaining Roles (15 total)
- [ ] Business Area Role
- [ ] Capability Role
- [ ] Client Role
- [ ] Committee Role
- [ ] Data Quality Role
- [ ] Data Set Role
- [ ] Glossary Role
- [ ] Interface Role
- [ ] Legal Entity Role
- [ ] Product Role
- [ ] Regulation Role

### Phase 3: Python Service Updates

- [ ] Create relationship validation processors
  - [ ] Generic relationship validator
  - [ ] Entity-specific validators if needed

- [ ] Create role assignment validation processors
  - [ ] Generic role validator
  - [ ] Verify person_id and role_id exist

- [ ] Update `bulk_validation_service.py`
  - [ ] Add relationship endpoint
  - [ ] Add role endpoint
  - [ ] Route to appropriate validators

### Phase 4: Template Generation

- [ ] Create relationship templates
  - [ ] Generic template with EntityA_ID, EntityB_ID columns
  - [ ] Entity-specific templates if needed

- [ ] Create role templates
  - [ ] Generic template with Entity_ID, Person_ID, Role_ID columns
  - [ ] Entity-specific templates if needed

- [ ] Update `BulkTemplateGeneratorServlet`
  - [ ] Handle relationship template requests
  - [ ] Handle role template requests

### Phase 5: Testing

#### Unit Tests
- [ ] Test BulkUploadUtil methods
- [ ] Test individual relationship handlers
- [ ] Test individual role handlers

#### Integration Tests
- [ ] Test RelationshipBulkUploadServlet
- [ ] Test RoleBulkUploadServlet
- [ ] Test end-to-end flow for each type

#### Manual Testing
- [ ] Test object uploads (verify no regression)
- [ ] Test relationship uploads
- [ ] Test role uploads
- [ ] Test validation errors
- [ ] Test WebSocket updates
- [ ] Test permissions
- [ ] Test template downloads

### Phase 6: Deployment

- [ ] Deploy database schema updates (if any)
- [ ] Deploy Python service updates
- [ ] Deploy Java backend updates
- [ ] Deploy frontend updates
- [ ] Update documentation
- [ ] Train users

---

## 📊 Progress Tracking

- **Completed**: 6 / 6 major sections
- **In Progress**: Phase 1 (Main Servlets)
- **Remaining**: Phases 2-6

---

## 🎯 Quick Start Guide

### To Add a New Relationship:

1. **Create Handler** in `relationships/handlers/`:
   ```bash
   # Copy example and modify
   cp PolicySystemRelationshipHandler.java NewRelationshipHandler.java
   ```

2. **Register in Servlet** (when created):
   ```java
   registerHandler(new NewRelationshipHandler());
   ```

3. **Test**:
   - Upload Excel with correct columns
   - Verify INSERT operation
   - Verify DELETE operation

### To Add a New Role:

1. **Create Handler** in `roles/handlers/`:
   ```bash
   # Copy example and modify
   cp PolicyRoleHandler.java NewRoleHandler.java
   ```

2. **Register in Servlet** (when created):
   ```java
   registerHandler(new NewRoleHandler());
   ```

3. **Test**:
   - Upload Excel with Entity_ID, Person_ID, Role_ID
   - Verify role assignment
   - Verify role removal

---

## 💡 Tips

- **Start Small**: Implement 2-3 handlers first, test thoroughly
- **Copy Pattern**: Use existing handlers as templates
- **Test Each Handler**: Unit test before integration
- **Check Database**: Verify table structure matches expectations
- **Monitor Logs**: Use logger to debug issues
- **Incremental Deploy**: Deploy and test in stages

---

## 📞 Support

- Check `bulk/README.md` for architecture overview
- Check `relationships/handlers/README.md` for relationship guide
- Check `roles/handlers/README.md` for role guide
- Review example handlers for patterns

---

## 🚀 Estimated Timeline

- Phase 1 (Main Servlets): 1-2 days
- Phase 2 (Priority Handlers): 2-3 days
- Phase 2 (All Handlers): 1-2 weeks
- Phase 3 (Python): 2-3 days
- Phase 4 (Templates): 1-2 days
- Phase 5 (Testing): 3-5 days
- Phase 6 (Deployment): 1 day

**Total Estimated Time**: 3-4 weeks for complete implementation

---

Last Updated: 2025-01-16

