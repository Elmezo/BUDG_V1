# Frontend Translation Gaps - Bulk Upload & Components

**Date**: February 17, 2026  
**Status**: ⚠️ Incomplete - Multiple untranslated UI strings found  
**Scope**: Bulk Upload Wizard, Components, Configurations

---

## 📊 Summary

| Category | Priority | Count | Status |
|----------|----------|-------|--------|
| **Entity Field Display Names** | 🔴 Critical | 500+ | ❌ Not translated |
| **Upload Options Descriptions** | 🔴 Critical | 100+ | ❌ Not translated |
| **Sheet Name References** | 🟠 High | 50+ | ❌ Hardcoded |
| **Error Message Labels** | 🟠 High | 3-5 | ❌ Not translated |
| **Dialog/Form Labels** | 🟡 Medium | 10+ | ❌ Partially translated |
| **Console Messages** | 🟢 Low | 20+ | ℹ️ Debug only |

---

## 🔴 CRITICAL ISSUES

### 1️⃣ Entity Field Display Names (`entityConfig.js`)

**File**: `frontend/src/config/entityConfig.js`  
**Impact**: Step 2 (Map Columns) - Field labels visible to users

#### Example Hardcoded Strings:
```javascript
// REGULATOR entity config
{
    displayName: 'Primary Name',           // ❌ Not translated
    description: 'The primary name of the regulator'  // ❌ Not translated
}

// GEOGRAPHY entity config
{
    displayName: 'Geography Name',         // ❌ Not translated
    description: 'Name of the geographic area'  // ❌ Not translated
}

// POLICY entity config  
{
    displayName: 'Policy Title',           // ❌ Not translated
    description: 'Official policy title'   // ❌ Not translated
}
```

#### Affected Field Types (500+ total):
- Regulator fields (Primary Name, Short Name, Regulator ID, etc.)
- Geography fields (Geography Name, Geography Code, etc.)
- Policy fields (Policy Title, Policy Code, Policy Type, etc.)
- System fields (System Name, System Description, etc.)
- Dataset fields (Dataset Name, Dataset Owner, Dataset Classification, etc.)
- **AND MORE for all 20+ entity types**

#### Solution Needed:
```javascript
// RECOMMENDED APPROACH:
{
    displayName: t('entity.regulator.field.primaryName'),      // ✅ Translatable
    description: t('entity.regulator.field.primaryName.desc')  // ✅ Translatable
}
```

---

### 2️⃣ Upload Options Descriptions (`uploadTypes.js`)

**File**: `frontend/src/config/uploadTypes.js`  
**Impact**: Step 1 (Choose File) - Dropdown descriptions  
**Lines**: 184-370+

#### Hardcoded Descriptions:

```javascript
UPLOAD_OPTIONS: {
    regulators: {
        CREATE: {
            description: 'Create new regulators',  // ❌ Not translated
            // ...
        },
        UPDATE: {
            description: 'Update existing regulators',  // ❌ Not translated
            // ...
        },
        DELETE: {
            description: 'Delete existing regulators'  // ❌ Not translated
            // ...
        }
    },
    
    committees: {
        CREATE: {
            description: 'Create new committees'  // ❌ Not translated
            // ...
        },
        // More...
    },
    
    policies: {
        CREATE: {
            description: 'Create new policies'  // ❌ Not translated
            // ...
        },
        // More...
    }
    
    // ... 100+ more descriptions across all entities
}
```

#### Complete List of Affected Entities:
- Regulators (3 operations)
- Committees (3 operations)
- Policies (3 operations)
- Regulations (3 operations)
- Geographies (3 operations)
- Business Areas (3 operations)
- Processes (3 operations)
- Capabilities (3 operations)
- Glossaries (3 operations)
- Systems (3 operations)
- Org Units (3 operations)
- People (3 operations)
- Projects (3 operations)
- Legal Entities (3 operations)
- Clients (3 operations)
- Regulatory Themes (3 operations)
- Products (3 operations)
- And more entity combinations...

**Total: 100+ descriptions**

#### Solution Needed:
```javascript
// RECOMMENDED:
description: t('bulkUpload.uploadOption.regulators.create.desc')
// OR
description: t(`entity.${entity}.operation.${operation}.description`)
```

---

### 3️⃣ Sheet Names in Excel Templates (`StepMapColumns.jsx`)

**File**: `frontend/src/components/StepMapColumns.jsx`  
**Lines**: 24-180 in `getSheetName()` function  
**Impact**: Sheet name validation when parsing Excel files

#### Hardcoded Sheet Names:
```javascript
function getSheetName(entity, operation) {
    switch (entity) {
        case 'Regulator':
            if (operation === 'INSERT') return 'Create Regulator';      // ❌ Hardcoded
            if (operation === 'UPDATE') return 'Update Regulator';      // ❌ Hardcoded
            if (operation === 'DELETE') return 'Delete Regulator';      // ❌ Hardcoded
            
        case 'Geography':
            if (operation === 'INSERT') return 'Create Geography';      // ❌ Hardcoded
            if (operation === 'UPDATE') return 'Update Geography';      // ❌ Hardcoded
            if (operation === 'DELETE') return 'Delete Geography';      // ❌ Hardcoded
            
        case 'Policy':
            if (operation === 'INSERT') return 'Create Policy';         // ❌ Hardcoded
            if (operation === 'UPDATE') return 'Update Policy';         // ❌ Hardcoded
            if (operation === 'DELETE') return 'Delete Policy';         // ❌ Hardcoded
            
        case 'Regulation':
            if (operation === 'INSERT') return 'Create Regulation';     // ❌ Hardcoded
            if (operation === 'UPDATE') return 'Update Regulation';     // ❌ Hardcoded
            if (operation === 'DELETE') return 'Delete Regulation';     // ❌ Hardcoded
            
        // ... 40+ more entity/operation combinations
    }
}
```

**Impact on Users**: 
- Template download gets Excel files with English sheet names
- If user downloads template in English but system shows Arabic, there's a mismatch
- Users in Arabic see "Create Regulator" (English) instead of "إنشاء منظم" (Arabic)

---

## 🟠 HIGH PRIORITY ISSUES

### 4️⃣ Error Detail Labels (`Alert.jsx`)

**File**: `frontend/src/components/ui/Alert.jsx`  
**Lines**: 42-44  
**Impact**: Error display messages in validation results

#### Hardcoded Labels:
```jsx
<div>
    <p>Field: {error.field}</p>           // ❌ "Field:" label not translated
    <p>Row: {error.row}</p>               // ❌ "Row:" label not translated
    <p>Code: {error.error_code}</p>       // ❌ "Code:" label not translated
</div>
```

#### Solution:
```jsx
<div>
    <p>{t('bulkUpload.error.field')}: {error.field}</p>
    <p>{t('bulkUpload.error.row')}: {error.row}</p>
    <p>{t('bulkUpload.error.code')}: {error.error_code}</p>
</div>
```

---

### 5️⃣ Configuration Description in Field Metadata

**File**: `frontend/src/config/entityConfig.js`  
**Example Issue**:
```javascript
// Regulator configuration
const REGULATOR_FIELDS = {
    fields: [
        {
            name: 'id',
            displayName: 'ID',              // ❌ Not translated
            description: 'Unique identifier'  // ❌ Not translated
        },
        {
            name: 'primaryName',
            displayName: 'Primary Name',     // ❌ Not translated
            description: 'The main name of the regulator'  // ❌ Not translated
        },
        // ... 50-100 more fields per entity
    ]
}
```

**Total Affected**: ~500+ field definitions across all 20+ entities

---

## 🟡 MEDIUM PRIORITY ISSUES

### 6️⃣ Entity Type Names in Dropdown

**Files**: Multiple component files  
**Issue**: Entity names like "Regulator", "Geography", "Policy" appear in UI but aren't consistently translated

Example in template download error:
```javascript
// File: StepChooseFile.jsx, Line 350
const errorMsg = `Template mapping not found for: ${option}`;  // ❌ Not translated
```

---

### 7️⃣ Field Type Labels

**File**: `frontend/src/components/StepMapColumns.jsx`  
**Line**: 548

```jsx
<span>Type: {field.dataType}</span>  // ❌ "Type:" label not translated
```

Should be:
```jsx
<span>{t('bulkUpload.step2.type')}: {field.dataType}</span>
```

---

## 🟢 LOW PRIORITY ISSUES

### 8️⃣ Console Log Messages (Debug Only)

These are NOT visible to users but should be reviewed:

| File | Line(s) | String | Type |
|------|---------|--------|------|
| `StepChooseFile.jsx` | 59 | `'Loading permissions for current authenticated user...'` | Debug log |
| `StepChooseFile.jsx` | 142 | `'Using expected sheet: ${sheetName}'` | Debug log |
| `StepMapColumns.jsx` | 285 | `'Error parsing Excel:'` | Debug log |
| `StepUploadProgress.jsx` | 56 | `'✅ validationErrors updated:'` | Debug log with emoji |
| `StepUploadProgress.jsx` | 77 | `'📤 Uploading file with user ID:'` | Debug log with emoji |
| `BulkJobsDashboard.jsx` | 112-115 | Emoji-prefixed console messages | Debug logs |

**Action**: These are development/debugging only and don't impact user experience. Can be ignored or cleaned up in future.

---

### 9️⃣ Status Icons (Emoji-based)

**File**: `frontend/src/components/BulkJobsDashboard.jsx`  
**Lines**: 64-67

```javascript
const STATUS_ICONS = {
    'Pending': '⏳',        // Hourglass emoji - not translatable
    'Processing': '⚙️',    // Gear emoji - not translatable
    'Completed': '✓',      // Checkmark - not translatable
    'Failed': '✕'          // X mark - not translatable
};
```

**Note**: Emoji icons are inherently non-translatable but work cross-culturally. This is acceptable UX.

---

## 📋 Translation Keys Needed

### For `en.json` and `ar.json`:

```json
{
    "bulkUpload": {
        "entity": {
            "regulator": {
                "name": "Regulator",
                "field": {
                    "primaryName": "Primary Name",
                    "shortName": "Short Name",
                    "regulatorId": "Regulator ID",
                    "description": "Description"
                }
            },
            "geography": {
                "name": "Geography",
                "field": {
                    "geographyName": "Geography Name",
                    "geographyCode": "Geography Code",
                    "description": "Description"
                }
            },
            // ... more entities
        },
        "uploadOption": {
            "regulators": {
                "create": {
                    "description": "Create new regulators"
                },
                "update": {
                    "description": "Update existing regulators"
                },
                "delete": {
                    "description": "Delete existing regulators"
                }
            },
            "committees": {
                "create": {
                    "description": "Create new committees"
                },
                "update": {
                    "description": "Update existing committees"
                },
                "delete": {
                    "description": "Delete existing committees"
                }
            },
            // ... more entities and operations
        },
        "sheetName": {
            "regulator": {
                "create": "Create Regulator",
                "update": "Update Regulator",
                "delete": "Delete Regulator"
            },
            "geography": {
                "create": "Create Geography",
                "update": "Update Geography",
                "delete": "Delete Geography"
            },
            // ... more entities
        },
        "error": {
            "field": "Field",
            "row": "Row",
            "code": "Code",
            "type": "Type"
        }
    }
}
```

---

## ✅ COMPLETED TRANSLATIONS

These items have been recently fixed:

| Item | File(s) | Status |
|------|---------|--------|
| Dialog title "Select Report Format" | `en.json`, `ar.json`, `apiService.js` | ✅ Fixed |
| Excel format button "Excel (.xlsx)" | `en.json`, `ar.json`, `apiService.js` | ✅ Fixed |
| JSON format button "JSON (.json)" | `en.json`, `ar.json`, `apiService.js` | ✅ Fixed |

---

## ⚠️ ACCESS/VISIBILITY ISSUE

The main challenge with the missing translations:

### **Most Critical Problem**: Entity Metadata Not Translatable from Frontend Alone

**Root Cause**: Most entity field metadata (`displayName`, descriptions, etc.) comes from backend configuration in `entityConfig.js`, which is:
- Hardcoded in frontend config files
- Not using translation system
- Not parameterized for multi-language support

### **Recommended Approach**:

**Option A - Frontend Only (Quick Fix)**: 
- Map all hardcoded strings to translation keys
- Requires ~600+ new translation keys
- Doesn't solve dynamic field descriptions from backend

**Option B - Backend + Frontend (Better Long-term)**:
- Backend returns field metadata with translation keys
- Frontend uses `t()` to translate received keys
- More maintainable but requires backend changes

**Option C - Hybrid (Recommended)**:
- Frontend configs use translation keys for static metadata
- Backend API returns translation keys for dynamic fields
- Frontend applies `t()` to all metadata

---

## 🎯 RECOMMENDED PRIORITY ORDER

### Phase 1 (Highest Impact):
1. ✅ Already fixed: Dialog strings (done)
2. Fix entity field display names in `entityConfig.js`
3. Fix upload option descriptions in `uploadTypes.js`
4. Add sheet name translations

### Phase 2 (Medium Impact):
5. Fix error label translations in `Alert.jsx`
6. Fix field type labels in `StepMapColumns.jsx`
7. Consistent entity type name translations

### Phase 3 (Low Impact):
8. Clean up console.log messages
9. Consider status icon improvements

---

## 📝 NEXT STEPS

1. **Add 600+ translation keys** to `en.json` and `ar.json` for entity fields and operations
2. **Update component files** to use translation keys:
   - `entityConfig.js` - map all displayName/description fields
   - `uploadTypes.js` - map all operation descriptions
   - `StepMapColumns.jsx` - use translation keys for sheet names
   - `Alert.jsx` - translate error labels
3. **Test translations** switching between English and Arabic
4. **Consider backend integration** for dynamic field metadata translation

---

**Last Updated**: February 17, 2026  
**Assigned To**: Development Team  
**Status**: 🟠 In Progress
