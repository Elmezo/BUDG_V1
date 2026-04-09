# Bulk Upload Implementation Status

## Completed Tasks ✓

### 1. Java Servlets - COMPLETED
#### Status/Download/Report Servlets (15 files) - ALL CREATED ✓
- SystemBulkStatusServlet.java ✓
- SystemBulkDownloadServlet.java ✓
- SystemBulkReportServlet.java ✓
- DatasetBulkStatusServlet.java ✓
- DatasetBulkDownloadServlet.java ✓
- DatasetBulkReportServlet.java ✓
- AttributeBulkStatusServlet.java ✓
- AttributeBulkDownloadServlet.java ✓
- AttributeBulkReportServlet.java ✓
- InterfaceBulkStatusServlet.java ✓
- InterfaceBulkDownloadServlet.java ✓
- InterfaceBulkReportServlet.java ✓
- GlossaryBulkStatusServlet.java ✓
- GlossaryBulkDownloadServlet.java ✓
- GlossaryBulkReportServlet.java ✓

#### Upload Servlets (2 of 5 COMPLETE)
- SystemBulkUploadServlet.java ✓ (1000+ lines, full implementation with audit, stakeholder linking)
- DatasetBulkUploadServlet.java ✓ (full implementation with valuesjob_dataset tracking)
- AttributeBulkUploadServlet.java ⚠️ (NEEDS CREATION - similar to Dataset with valuesjob_dataset)
- InterfaceBulkUploadServlet.java ⚠️ (NEEDS CREATION - similar to System)
- GlossaryBulkUploadServlet.java ⚠️ (NEEDS CREATION - similar to System)

### 2. JobDAO Enhancements - COMPLETED ✓
- Added `insertValuesjobDataset(int jobId, int datasetId)` method
- Added `insertValuesjobMetaAttribute(int jobId, String metaAttributeName, String metaAttributeValue)` method
- Both methods properly insert into the respective tables with foreign key constraints

## Remaining Tasks ⚠️

### 1. Upload Servlets (3 remaining)
Need to create following the SystemBulkUploadServlet pattern:

#### AttributeBulkUploadServlet.java
- Base path: `src/main/bulk/attribute/`
- Entity: "Attribute"
- Reference prefix: "ATT-"
- Special requirements:
  - Insert into `valuesjob_dataset` table (links attribute to dataset)
  - Insert into `valuesjob_meta_attribute` table (Suffix_Type='Append', IsOverwrite=0)
  - Auto-generate Reference Number if empty (ATT### format)
  - Link stakeholder via attribute_x_stakeholder table
  - Create attribute audit records (needs new methods in AttributeDAO)

#### InterfaceBulkUploadServlet.java
- Base path: `src/main/bulk/interface/`
- Entity: "Interface"
- Reference prefix: "IF-"
- Special requirements:
  - Auto-generate Reference if empty (IF### format)
  - Link stakeholder via interface_x_stakeholder table
  - Requires Source_systemID and Target_systemID resolution
  - Create interface audit records (methods exist in InterfaceDAO)

#### GlossaryBulkUploadServlet.java
- Base path: `src/main/bulk/glossary/`
- Entity: "Glossary"
- Reference prefix: "GL-"
- Special requirements:
  - Auto-generate Ref if empty (GL### format)
  - Handle Alias Names (comma-separated, insert into glossary_alias_name table)
  - Link stakeholder via glossary_x_stakeholder table
  - Parent resolution by Name + Ref
  - Create glossary audit records (methods exist in GlossaryDAO)

### 2. Python Validation Processors (5 files) - ALL NEED CREATION
Location: `python/`

#### system_bulk_processor.py
```python
# Required fields: Short Name, External, Description, Lifecycle, Type
# Lookups: BUDG Viewing, BUDG Status, system_lifecycle, system_type, system_classification, cia_rating
# Parent resolution: by Short Name
# User resolution: Email, First/Last Name, Lan ID
# Governance Role resolution
# Default first element for empty required lists
```

#### dataset_bulk_processor.py
```python
# Required: Name, Definition, Type, Lifecycle
# Lookups: viewing, dataset_type, dataset_lifecycle, status
# System resolution: ID, Short Name, Parent Short Name
# Glossary resolution: Ref, Name, Parent Name
# Auto-generate Ref if empty (DS### format)
# Return Dataset_ID in validated data
```

#### attribute_bulk_processor.py
```python
# Required: Attribute Name, Attribute Definition
# Lookups: attribute_datatype, requirement, attribute_origination, attribute_editability, attribute_edit_role
# Dataset resolution: Ref, Name + System Short Name
# Glossary resolution: Ref, Name, Parent Name
# Auto-generate Reference Number if empty (ATT### format)
# Return Dataset_ID for valuesjob_dataset linkage
```

#### interface_bulk_processor.py
```python
# Required: Interface Name, Description, Source System Short Name, Target System Short Name, Automation Level, Lifecycle
# Lookups: interface_transfer, interface_transfer_format, interface_classification, interface_lifecycle, 
#          status, interface_automation, interface_frequency, viewing
# System resolution for source and target by Short Name
# Auto-generate Reference if empty (IF### format)
```

#### glossary_bulk_processor.py
```python
# Required: Name, Definition, Lifecycle, Format Type, Security Classification, Type
# Lookups: viewing, status, glossary_lifecycle, glossary_format_type, glossary_kde_type, 
#          security_classification, glossary_type, cia_rating
# Parent resolution: Name + Ref
# Auto-generate Ref if empty (GL### format)
# Handle Alias Names (comma-separated)
```

### 3. Update bulk_validation_service.py
Add routing for 5 new entities:
```python
system_processor = load_processor_module("system_bulk_processor")
dataset_processor = load_processor_module("dataset_bulk_processor")
attribute_processor = load_processor_module("attribute_bulk_processor")
interface_processor = load_processor_module("interface_bulk_processor")
glossary_processor = load_processor_module("glossary_bulk_processor")

# Add to validation routing:
elif entity == "System":
    validated_data, errors = system_processor.validate_and_resolve_system(df, upload_option, user_id)
elif entity == "Dataset":
    validated_data, errors = dataset_processor.validate_and_resolve_dataset(df, upload_option, user_id)
elif entity == "Attribute":
    validated_data, errors = attribute_processor.validate_and_resolve_attribute(df, upload_option, user_id)
elif entity == "Interface":
    validated_data, errors = interface_processor.validate_and_resolve_interface(df, upload_option, user_id)
elif entity == "Glossary":
    validated_data, errors = glossary_processor.validate_and_resolve_glossary(df, upload_option, user_id)
```

### 4. Template Generation (BulkTemplateGeneratorServlet.java)
Add template definitions in `generateTemplate()` method for each entity with proper columns and dropdowns.

See plan file for complete column specifications for INSERT/UPDATE/DELETE templates.

### 5. DAO Enhancements

#### SystemDAO.java ⚠️
- MISSING: `createSystemUpdateAuditSnapshot(int systemId)` method
- MISSING: `createStakeholderAuditRecords(int systemId, String userName, String stakeholderName, int roleId)` method
- Existing: `createSystemAuditRecords()` ✓
- Existing: `createSystemAuditRecord()` ✓

#### DatasetDAO.java ⚠️
- MISSING: `createStakeholderAuditRecords(int datasetId, String userName, String stakeholderName, int roleId)` method
- Existing: `createDatasetAuditRecords()` ✓
- Existing: `createDatasetAuditRecord()` ✓

#### AttributeDAO.java ⚠️
- MISSING: `createAttributeAuditRecords(int attributeId, String userName)` method
- MISSING: `createAttributeAuditRecord(int attributeId)` method
- MISSING: `createAttributeUpdateAuditRecords()` method
- MISSING: Stakeholder methods (if applicable)

#### InterfaceDAO.java ⚠️
- MISSING: `createStakeholderAuditRecords(int interfaceId, String userName, String stakeholderName, int roleId)` method
- Existing: `createInterfaceAuditRecords()` ✓
- Existing: `createInterfaceAuditRecord()` ✓

#### GlossaryDAO.java ⚠️
- MISSING: `createStakeholderAuditRecords(int glossaryId, String userName, String stakeholderName, int roleId)` method
- Existing: `createGlossaryAuditRecords()` ✓
- Existing: `createGlossaryAuditRecord()` ✓

### 6. Database Tables

#### Audit Tables
- system_audit ✓
- system_audit_history ✓
- dataset_audit ✓
- dataset_audit_history ✓
- interface_audit ✓
- interface_audit_history ✓
- glossary_audit ✓
- glossary_audit_history ✓
- attribute_audit ⚠️ NEEDS CREATION
- attribute_audit_history ⚠️ NEEDS CREATION

SQL for attribute audit tables:
```sql
CREATE TABLE `attribute_audit` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `attribute_id` int(11) NOT NULL,
  `snapshot_datetime` datetime DEFAULT CURRENT_TIMESTAMP,
  `data` text,
  PRIMARY KEY (`id`),
  KEY `attribute_id` (`attribute_id`),
  CONSTRAINT `fk_attr_audit_attribute` FOREIGN KEY (`attribute_id`) REFERENCES `attribute` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `attribute_audit_history` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `attribute_id` int(11) NOT NULL,
  `object` varchar(100) DEFAULT NULL,
  `event` varchar(100) DEFAULT NULL,
  `updateType` varchar(100) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` text,
  `to` text,
  `author` varchar(255) DEFAULT NULL,
  `created_datetime` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `attribute_id` (`attribute_id`),
  CONSTRAINT `fk_attr_audit_hist_attribute` FOREIGN KEY (`attribute_id`) REFERENCES `attribute` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
```

#### Stakeholder Junction Tables
Verify or create:
- system_x_stakeholder ⚠️ (verify exists)
- dataset_x_stakeholder ⚠️ (verify exists)
- attribute_x_stakeholder ⚠️ (verify/create)
- interface_x_stakeholder ⚠️ (verify exists)
- glossary_x_stakeholder ⚠️ (verify exists)

Standard pattern:
```sql
CREATE TABLE `{entity}_x_stakeholder` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `{entity}_id` int(11) NOT NULL,
  `object_x_people_id` int(11) NOT NULL,
  `created_by` int(11) DEFAULT NULL,
  `created_datetime` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `{entity}_id` (`{entity}_id`),
  KEY `object_x_people_id` (`object_x_people_id`),
  CONSTRAINT `fk_{entity}_stakeholder_{entity}` FOREIGN KEY (`{entity}_id`) REFERENCES `{entity}` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_{entity}_stakeholder_oxp` FOREIGN KEY (`object_x_people_id`) REFERENCES `object_x_people` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
```

#### Job Metadata Tables (Already exist, methods added to JobDAO)
- valuesjob_dataset ✓
- valuesjob_meta_attribute ✓

### 7. BulkUploadPermissionsServlet.java
Add permissions check for 5 new entities in the servlet.

## Implementation Priority

### Critical Path (Must Complete):
1. Create 3 remaining Upload Servlets (Attribute, Interface, Glossary)
2. Create 5 Python processors
3. Update bulk_validation_service.py routing
4. Create attribute audit tables
5. Add missing DAO methods for audit and stakeholder linking

### Important (Should Complete):
6. Add template definitions to BulkTemplateGeneratorServlet
7. Verify/create stakeholder junction tables
8. Update BulkUploadPermissionsServlet

## Notes

- All Status/Download/Report servlets are complete and follow the standard pattern
- SystemBulkUploadServlet and DatasetBulkUploadServlet are fully implemented and can serve as templates
- JobDAO now has valuesjob methods needed for Dataset and Attribute tracking
- Python processors should follow the pattern of existing processors (policy_bulk_processor.py, regulation_bulk_processor.py)
- All upload servlets must call BulkUploadBroadcaster for WebSocket updates
- Reference number auto-generation is critical for all entities
- Stakeholder linking requires object_x_people table and entity-specific junction tables

## Testing Checklist

For each entity (after completion):
- [ ] Template generation works with correct dropdowns
- [ ] File upload creates job and validates
- [ ] INSERT creates records with audit trail
- [ ] UPDATE modifies records with audit snapshots
- [ ] DELETE soft-deletes records
- [ ] Stakeholders are linked correctly
- [ ] valuesjob_dataset tracking works (Dataset/Attribute)
- [ ] valuesjob_meta_attribute tracking works (Dataset/Attribute)
- [ ] Job status updates via WebSocket
- [ ] Report shows success/failure details
- [ ] Download returns processed file

