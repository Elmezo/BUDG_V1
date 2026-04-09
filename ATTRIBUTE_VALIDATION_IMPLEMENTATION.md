# Attribute Tab Validation Implementation

## Overview
Complete validation implementation for Data Type and Data Length in the Attribute tab of dataset-edit page, ensuring Axon-aligned behavior.

## ✅ Completed Features

### 1. Frontend Validation (attribute-table.js)

#### Data Type Validation
- **Required Field**: Data Type is required for all attributes
- **Validation on Save**: Checks if Data Type is selected before saving
- **Clear Error Messages**: Shows alert with attribute name if Data Type is missing
- **Visual Feedback**: Highlights Data Type field in red if validation fails

#### Data Length Validation
- **Conditional Requirement**: 
  - Required ONLY for string types: String, VARCHAR, CHAR, Text
  - NOT required for non-string types: Integer, Number, Date, Boolean, DateTime, Custom
- **Dynamic Field State**:
  - Enabled and required for string types
  - Disabled and cleared for non-string types
  - Visual indicator (gray background) when disabled
- **Real-time Updates**: Field state updates immediately when Data Type changes
- **Clear Error Messages**: Shows alert with attribute name and Data Type if Data Length is missing for string types

#### UI/UX Enhancements
- **Auto-disable Data Length**: When non-string type is selected, Data Length field is automatically disabled and cleared
- **Auto-enable Data Length**: When string type is selected, Data Length field is automatically enabled and marked as required
- **Visual Indicators**:
  - Red border on Data Length if empty for string types
  - Gray background when disabled
  - Tooltips explaining field state
- **Initial State**: Existing rows are initialized with correct field state based on their Data Type

### 2. Backend Validation (AttributeServlet.java)

#### POST /api/attribute (Create)
- **Data Type Validation**: 
  - Checks if `data_type_id` is provided
  - Returns 400 error if missing: "Data Type is required for all attributes"
  
- **Data Length Validation**:
  - Gets Data Type name from database
  - Checks if Data Type is string type (STRING, VARCHAR, CHAR, TEXT)
  - For string types: Validates that `data_length` is provided and > 0
  - For non-string types: Clears `data_length` to NULL
  - Returns 400 error if string type missing Data Length: "Data Length is required for string types (String, VARCHAR, CHAR, Text)"

#### PUT /api/attribute/{id} (Update)
- **Same validation as POST**: All validation rules apply to updates as well
- **Data Length Clearing**: Non-string types automatically have Data Length cleared to NULL

### 3. Database Integrity (AttributeDAO.java)

#### New Method: getDataTypeNameById()
- Retrieves Data Type name from `attribute_datatype` table by ID
- Used by AttributeServlet for validation
- Returns null if Data Type not found or deleted

#### Data Storage
- **Data Type**: Stored in `attribute.Data_type_ID` (foreign key to `attribute_datatype.ID`)
- **Data Length**: Stored in `attribute.DataLength` (INTEGER, nullable)
- **Correct Mapping**: Data Length is only stored for string types, NULL for others

### 4. Cross-Check with Dataset Values Upload

#### Compatibility
- ✅ Frontend validation in `dataset-values-edit.js` checks for Data Type and Data Length
- ✅ Backend validation in `DatasetValuesServlet.java` checks for Data Type and Data Length
- ✅ Same validation logic used in both Attribute tab and Values upload
- ✅ Attributes with Date/Boolean types won't cause upload rejection due to missing Data Length

## Implementation Details

### Frontend Methods

1. **isStringDataType(dataTypeName)**: 
   - Checks if Data Type is a string type
   - Returns true for: STRING, VARCHAR, CHAR, TEXT (case-insensitive)

2. **getDataTypeNameById(dataTypeId)**:
   - Gets Data Type name from lookups by ID
   - Used to determine if field should be enabled/disabled

3. **handleDataTypeChange(selectElement)**:
   - Called when Data Type select changes
   - Enables/disables Data Length field
   - Clears Data Length for non-string types
   - Updates visual indicators

4. **setupDataTypeHandlers()**:
   - Sets up event delegation for Data Type changes
   - Initializes state for existing rows
   - Called after rendering and when adding new rows

### Backend Methods

1. **AttributeServlet.doPost()**:
   - Validates Data Type is provided
   - Validates Data Length for string types
   - Clears Data Length for non-string types

2. **AttributeServlet.doPut()**:
   - Same validation as POST
   - Ensures updates maintain data integrity

3. **AttributeDAO.getDataTypeNameById()**:
   - Retrieves Data Type name from database
   - Used for validation logic

## Validation Rules

### Data Type
- ✅ **Required**: Yes, for ALL attributes
- ✅ **Validation**: Frontend and Backend
- ✅ **Error Message**: "Data Type is required for all attributes"

### Data Length
- ✅ **Required**: Only for string types (String, VARCHAR, CHAR, Text)
- ✅ **Not Required**: For non-string types (Integer, Number, Date, Boolean, DateTime, Custom)
- ✅ **Validation**: Frontend and Backend
- ✅ **Error Message**: "Data Length is required for string types (String, VARCHAR, CHAR, Text)"
- ✅ **Auto-clearing**: Non-string types automatically have Data Length set to NULL

## User Experience Flow

### Creating New Attribute
1. User enters Name and Definition (required)
2. User selects Data Type from dropdown
3. **If String Type Selected**:
   - Data Length field becomes enabled
   - Field is marked as required (red border if empty)
   - Tooltip: "Data Length is required for string types"
4. **If Non-String Type Selected**:
   - Data Length field becomes disabled
   - Field value is cleared
   - Tooltip: "Data Length is not applicable for [Type] types"
5. User clicks Save
6. **Frontend Validation**:
   - Checks Data Type is selected
   - If string type, checks Data Length is provided
   - Shows alert if validation fails
7. **Backend Validation**:
   - Double-checks all validations
   - Returns error if validation fails
8. **On Success**: Attribute saved with correct Data Type and Data Length

### Editing Existing Attribute
1. User changes Data Type
2. Data Length field state updates immediately
3. If changed to non-string type, Data Length is cleared
4. If changed to string type, Data Length becomes required
5. Save process same as creating

## Testing Checklist

### Frontend Validation
- [x] Data Type required validation
- [x] Data Length required for string types
- [x] Data Length not required for non-string types
- [x] Data Length field disabled for non-string types
- [x] Data Length field enabled for string types
- [x] Data Length cleared when switching to non-string type
- [x] Visual indicators (red border, gray background)
- [x] Tooltips showing field state
- [x] Error messages in Arabic and English

### Backend Validation
- [x] Data Type required in POST
- [x] Data Type required in PUT
- [x] Data Length required for string types in POST
- [x] Data Length required for string types in PUT
- [x] Data Length cleared for non-string types
- [x] Error messages returned correctly

### Database Integrity
- [x] Data Type saved correctly
- [x] Data Length saved for string types
- [x] Data Length is NULL for non-string types
- [x] Updates maintain data integrity

### Cross-Check with Values Upload
- [x] Attributes with Date type don't require Data Length
- [x] Attributes with Boolean type don't require Data Length
- [x] Attributes with String type require Data Length
- [x] Values upload validation works correctly
- [x] No false rejections due to Data Length for non-string types

## Expected Behavior

### String Types (String, VARCHAR, CHAR, Text)
- ✅ Data Type: Required
- ✅ Data Length: Required (must be > 0)
- ✅ Field State: Enabled, required
- ✅ Visual: Normal background, red border if empty

### Non-String Types (Integer, Number, Date, Boolean, DateTime, Custom)
- ✅ Data Type: Required
- ✅ Data Length: NOT required (automatically NULL)
- ✅ Field State: Disabled, not required
- ✅ Visual: Gray background, cleared value

## Error Messages

### Frontend
- Data Type missing: `"Data Type is required for attribute "[name]".\nنوع البيانات مطلوب لكل attribute."`
- Data Length missing (string): `"Data Length is required for string attribute "[name]" (Data Type: [type]).\nطول البيانات مطلوب للأنواع النصية (String, VARCHAR, CHAR, Text)."`

### Backend
- Data Type missing: `"Data Type is required for all attributes"` (HTTP 400)
- Data Length missing (string): `"Data Length is required for string types (String, VARCHAR, CHAR, Text)"` (HTTP 400)

## Integration Points

1. **Attribute Tab** (`dataset-edit.html?id=57&tab=attribute`):
   - Uses `AttributeTable` class
   - Validation on save
   - Real-time field updates

2. **Values Upload** (`dataset-edit.html?id=57&tab=values`):
   - Checks attributes before upload
   - Validates Data Type and Data Length
   - Uses same validation logic

3. **Backend API** (`/api/attribute`):
   - Validates on create (POST)
   - Validates on update (PUT)
   - Ensures data integrity

## Success Criteria

✅ All attributes can be created/edited without errors
✅ Data Type is required and validated
✅ Data Length is required only for string types
✅ Data Length is automatically cleared for non-string types
✅ UI provides clear feedback (enabled/disabled, tooltips, error messages)
✅ Database stores correct values (Data Length NULL for non-string types)
✅ Values upload works correctly with all attribute types
✅ No false rejections due to Data Length for Date/Boolean types
