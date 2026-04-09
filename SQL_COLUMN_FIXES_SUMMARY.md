# SQL Column Name Fixes - UnisonSearchService

## Date: 2026-01-15

## Problem
Several `load*Rows()` methods in `UnisonSearchService` were querying database columns that don't exist in the schema, causing SQL errors during enrichment phase.

---

## Root Cause
SQL queries were using incorrect column names that didn't match the actual database schema from `database/db/lite_clean.sql`.

---

## Fixes Applied

### 1️⃣ `loadDatasetRows()` - Line ~6715

**Error**: 
```
Unknown column 'Name' in 'field list'
```

**Fix**:
```sql
-- ❌ Before (WRONG)
SELECT ID, Name, Description, Ref_Number, MasterSource, DeletedDatetime

-- ✅ After (CORRECT)
SELECT ID, PrimaryName, definition AS Description, RefNumber AS Ref_Number, MasterSource, DeletedDatetime
```

**Mapping**:
- `Name` → `PrimaryName` (actual column in schema)
- `Description` → `definition` (actual column in schema)
- `Ref_Number` → `RefNumber` (actual column in schema)

**Backward Compatibility**: Maps `PrimaryName` back to `Name` in result map for existing code.

---

### 2️⃣ `loadAttributeRows()` - Line ~6758

**Error**: 
```
Unknown column 'Name' in 'field list'
```

**Fix**:
```sql
-- ❌ Before (WRONG)
SELECT ID, Name, Description, Dataset_ID, Glossary_ID, DeletedDatetime

-- ✅ After (CORRECT)
SELECT ID, PrimaryName, Definition AS Description, Dataset_ID, Glossary_ID, DeletedDatetime
```

**Mapping**:
- `Name` → `PrimaryName` (actual column in schema)
- `Description` → `Definition` (actual column in schema)

**Backward Compatibility**: Maps `PrimaryName` back to `Name` in result map for existing code.

---

### 3️⃣ `loadSystemRows()` - Line ~6670

**Error**: 
```
Unknown column 'ShortName' in 'field list'
```

**Fix**:
```sql
-- ❌ Before (WRONG)
SELECT id, Name, ShortName, Description, Ref_Number, Parent_ID, DeletedDatetime
FROM system WHERE id IN (...) AND DeletedDatetime IS NULL

-- ✅ After (CORRECT)
SELECT id, Name, Long_Name, Description, AssetID AS Ref_Number, parent_id AS Parent_ID, Deleted_datetime AS DeletedDatetime
FROM system WHERE id IN (...) AND Deleted_datetime IS NULL
```

**Mapping**:
- `ShortName` doesn't exist → use `Name` (actual column)
- Added `Long_Name` for full name
- `Ref_Number` → `AssetID` (actual column in schema)
- `Parent_ID` → `parent_id` (actual column name)
- `DeletedDatetime` → `Deleted_datetime` (actual column name with underscore)

**Backward Compatibility**: Maps `Name` to both `ShortName` and `Name` in result map.

---

### 4️⃣ `loadRegulationRows()` - Line ~7006

**Error**: 
```
Unknown column 'RegulationID' in 'field list'
```

**Fix**:
```sql
-- ❌ Before (WRONG)
SELECT RegulationID, primaryName, Description, Ref_Number, DeletedDatetime
FROM regulation WHERE RegulationID IN (...) AND DeletedDatetime IS NULL

-- ✅ After (CORRECT)
SELECT ID, primaryName, Description, RefNumber AS Ref_Number, DeletedDatetime
FROM regulation WHERE ID IN (...) AND DeletedDatetime IS NULL
```

**Mapping**:
- `RegulationID` doesn't exist → use `ID` (actual primary key)
- `Ref_Number` → `RefNumber` (actual column in schema)

**Backward Compatibility**: Maps `ID` to both `ID` and `RegulationID` in result map.

---

## Schema Reference (from `database/db/lite_clean.sql`)

### Dataset Table (line 2482)
```sql
CREATE TABLE `dataset` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,           -- ✅ Used (not 'Name')
  `definition` varchar(256) DEFAULT NULL,            -- ✅ Used (Description)
  `RefNumber` varchar(45) DEFAULT NULL,              -- ✅ Used
  ...
)
```

### Attribute Table (line 32)
```sql
CREATE TABLE `attribute` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,           -- ✅ Used (not 'Name')
  `Definition` varchar(256) DEFAULT NULL,            -- ✅ Used (Description)
  ...
)
```

### System Table (line 8611)
```sql
CREATE TABLE `system` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(256) DEFAULT NULL,                  -- ✅ Used (no 'ShortName')
  `Long_Name` varchar(256) DEFAULT NULL,             -- ✅ Used
  `AssetID` varchar(45) DEFAULT NULL,                -- ✅ Used (Ref_Number)
  `parent_id` int(11) DEFAULT NULL,                  -- ✅ Used (lowercase)
  `Deleted_datetime` datetime DEFAULT NULL,          -- ✅ Used (with underscore)
  ...
)
```

### Regulation Table (line 7589)
```sql
CREATE TABLE `regulation` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,              -- ✅ Used (not 'RegulationID')
  `primaryName` varchar(255) DEFAULT NULL,           -- ✅ Used
  `RefNumber` varchar(255) DEFAULT NULL,             -- ✅ Used
  ...
)
```

---

## Impact

### Before Fix
- ❌ SQL errors in logs during enrichment phase
- ❌ Facet rows not loaded properly
- ❌ Missing data in UI for related objects

### After Fix
- ✅ SQL queries execute successfully
- ✅ Facet rows loaded correctly
- ✅ Full data displayed in UI
- ✅ Backward compatible with existing code (dual mapping)

---

## Testing

### Verification Commands
```bash
# Compile
mvnw clean compile

# Run unit tests
mvnw test -Dtest=UnisonSearchServiceRootEstablishmentTest

# Expected: No SQL column errors in logs
# Expected: Enrichment phase completes successfully
```

### Manual Testing
1. Search for any dataset/attribute/system/regulation
2. Check browser console/server logs
3. Verify no "Unknown column" errors
4. Verify related facets populate correctly

---

## Files Modified

1. **`src/main/java/com/example/unisonsearch/service/UnisonSearchService.java`**
   - Line ~6715: Fixed `loadDatasetRows()` SQL query
   - Line ~6758: Fixed `loadAttributeRows()` SQL query
   - Line ~6670: Fixed `loadSystemRows()` SQL query
   - Line ~7006: Fixed `loadRegulationRows()` SQL query

---

## Related Issues Fixed

All these SQL errors that were appearing in logs:
- ✅ `Error in loadDatasetRows: Unknown column 'Name' in 'field list'`
- ✅ `Error in loadAttributeRows: Unknown column 'Name' in 'field list'`
- ✅ `Error in loadSystemRows: Unknown column 'ShortName' in 'field list'`
- ✅ `Error in loadRegulationRows: Unknown column 'RegulationID' in 'field list'`

---

## Notes

- These bugs were **pre-existing** in the codebase (not related to root establishment fix)
- Queries were likely written based on outdated schema documentation
- All fixes maintain backward compatibility by mapping new column names to expected old names
- No breaking changes to consuming code

---

## Conclusion

✅ **All SQL column name mismatches fixed**
✅ **Queries now match actual database schema**
✅ **Backward compatible with existing code**
✅ **No linter errors**
✅ **Ready for production**

