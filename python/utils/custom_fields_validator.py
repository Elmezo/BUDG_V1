"""
Custom Fields Validation Utility
Provides functions to validate custom field values based on their data types
"""

import pymysql
import os
import logging
import math
from typing import Optional, Dict, List, Any, Tuple, AbstractSet
import re
from datetime import datetime

logger = logging.getLogger(__name__)

# Must match filter_rows_with_blocking_custom_field_errors in bulk_validation_service.py
CUSTOM_FIELD_BLOCKING_ERROR_CODES = frozenset({
    "CUSTOM_FIELD_MANDATORY",
    "CUSTOM_FIELD_VALIDATION_ERROR",
    "CUSTOM_FIELD_VALIDATION_EXCEPTION",
})

# Database configuration from environment variables
DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USERNAME", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "database": os.getenv("DB_NAME", "project"),
    "charset": "utf8mb4"
}


def get_db_connection():
    """Create a database connection"""
    try:
        return pymysql.connect(**DB_CONFIG)
    except Exception as e:
        logger.error(f"Database connection failed: {e}")
        raise


def get_module_id_by_object_type(object_type: str) -> Optional[int]:
    """
    Get module ID by object type name
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT id FROM module WHERE LOWER(TRIM(primaryname)) = LOWER(TRIM(%s)) LIMIT 1"
        cursor.execute(query, (object_type,))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting module ID for object type '{object_type}': {e}")
        return None


def get_custom_fields_for_module(module_id: int) -> List[Dict[str, Any]]:
    """
    Get all custom fields for a module
    Returns list of custom field metadata dictionaries
    """
    custom_fields = []
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor(pymysql.cursors.DictCursor)
        
        query = """
            SELECT ID, DisplayName, CustomFieldName, DataType, is_Mandatory, 
                   Default_Value, Description, Placeholder_Text
            FROM Custom_Field_Metadata
            WHERE Module_ID = %s
            ORDER BY ID
        """
        cursor.execute(query, (module_id,))
        
        results = cursor.fetchall()
        cursor.close()
        conn.close()
        
        for row in results:
            custom_fields.append({
                "id": row["ID"],
                "displayName": row["DisplayName"],
                "customFieldName": row["CustomFieldName"],
                "dataType": row["DataType"],
                "isMandatory": bool(row["is_Mandatory"]),
                "defaultValue": row["Default_Value"],
                "description": row["Description"],
                "placeholderText": row["Placeholder_Text"]
            })
            
    except Exception as e:
        logger.error(f"Error getting custom fields for module {module_id}: {e}")
    
    return custom_fields


def get_custom_field_enum_values(metadata_id: int) -> List[str]:
    """
    Get enum values for a custom field (for dropdown/multiselect)
    """
    enum_values = []
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = """
            SELECT EnumValue
            FROM Custom_Field_Enum
            WHERE Custom_Field_Metadata_ID = %s
            ORDER BY EnumValue
        """
        cursor.execute(query, (metadata_id,))
        
        results = cursor.fetchall()
        cursor.close()
        conn.close()
        
        for row in results:
            enum_values.append(row[0])
            
    except Exception as e:
        logger.error(f"Error getting enum values for custom field {metadata_id}: {e}")
    
    return enum_values


def _dropdown_match_candidates(value: str) -> List[str]:
    """
    Excel often stores numeric choices as floats; str(cell) becomes '1.0' while EnumValue is '1'.
    Returns distinct candidate strings to match against Custom_Field_Enum.EnumValue.
    """
    if value is None:
        return []
    s = value.strip()
    if not s:
        return []
    out: List[str] = []
    seen = set()

    def add(x: str) -> None:
        if x and x not in seen:
            seen.add(x)
            out.append(x)

    add(s)
    try:
        f = float(s.replace(",", ""))
        if math.isfinite(f) and f == int(f):
            add(str(int(f)))
    except ValueError:
        pass
    return out


def resolve_canonical_enum_value(value: str, enum_values: List[str]) -> Optional[str]:
    """
    Resolve input value to canonical enum value using case-insensitive matching.
    Returns the exact enum value stored in DB, or None if no match.
    """
    if value is None:
        return None

    for cand in _dropdown_match_candidates(value):
        normalized_input = cand.lower()
        for enum_value in enum_values:
            if enum_value is None:
                continue
            if enum_value.strip().lower() == normalized_input:
                return enum_value
    return None


def get_enum_value_by_enum_row_id(metadata_id: int, raw: str) -> Optional[str]:
    """
    If the user enters the Custom_Field_Enum row ID (not the EnumValue text), resolve to EnumValue.
    """
    if not raw or not str(raw).strip().isdigit():
        return None
    try:
        eid = int(str(raw).strip())
    except ValueError:
        return None
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute(
            """
            SELECT EnumValue FROM Custom_Field_Enum
            WHERE Custom_Field_Metadata_ID = %s AND ID = %s
            LIMIT 1
            """,
            (metadata_id, eid),
        )
        row = cursor.fetchone()
        cursor.close()
        conn.close()
        return row[0] if row else None
    except Exception as e:
        logger.error(f"Error resolving enum by ID for metadata {metadata_id}: {e}")
        return None


def resolve_dropdown_for_bulk(metadata_id: int, value: str, enum_values: List[str]) -> Optional[str]:
    """Resolve dropdown cell to canonical EnumValue: label match (with Excel float normalization) or enum row ID."""
    if value is None or not str(value).strip():
        return None
    v = str(value).strip()
    r = resolve_canonical_enum_value(v, enum_values)
    if r is not None:
        return r
    return get_enum_value_by_enum_row_id(metadata_id, v)


def format_excel_cell_for_custom_field(value: Any) -> Optional[str]:
    """
    Normalize pandas/Excel scalars so dropdowns validate: float 1.0 -> '1', not '1.0'.
    """
    if value is None:
        return None
    try:
        import pandas as pd
        if pd.isna(value):
            return None
    except Exception:
        pass
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, int) and not isinstance(value, bool):
        return str(value)
    if isinstance(value, float):
        if math.isnan(value):
            return None
        if value == int(value):
            return str(int(value))
        return str(value)
    if hasattr(value, "item") and callable(getattr(value, "item", None)):
        try:
            return format_excel_cell_for_custom_field(value.item())
        except Exception:
            pass
    s = str(value).strip()
    if not s or s.lower() == "nan":
        return None
    try:
        f = float(s.replace(",", ""))
        if math.isfinite(f) and f == int(f):
            return str(int(f))
    except ValueError:
        pass
    return s


def _is_valid_integer_number_string(raw: str) -> bool:
    """Accept '42', '42.0', '1,000', scientific notation that resolves to an integer."""
    if not raw or not str(raw).strip():
        return False
    try:
        t = str(raw).strip().replace(",", "")
        f = float(t)
        return math.isfinite(f) and f == int(f)
    except ValueError:
        return False


def validate_custom_field_value(value: str, data_type: str, metadata_id: int, display_name: str) -> Tuple[bool, Optional[str]]:
    """
    Validate custom field value based on data type
    Returns (is_valid, error_message)
    """
    if not value or value.strip() == "":
        return (True, None)  # Empty values are handled separately (mandatory check)
    
    value = value.strip()
    
    try:
        if data_type.lower() == "text":
            # Text - any string is valid
            return (True, None)
            
        elif data_type.lower() == "number":
            # Number - must be an integer (normalize again in case value bypassed format_excel_cell)
            nv = format_excel_cell_for_custom_field(value)
            if nv is None or (isinstance(nv, str) and not nv.strip()):
                return (True, None)
            if _is_valid_integer_number_string(nv.strip()):
                return (True, None)
            return (False, f"Field '{display_name}' must be a valid integer number")
                
        elif data_type.lower() == "decimal":
            # Decimal - must be decimal, integer part max 13 digits
            try:
                # Check if it's a valid decimal
                decimal_value = float(value)
                
                # Check integer part length
                parts = value.split(".")
                integer_part = parts[0].replace("-", "")
                if len(integer_part) > 13:
                    return (False, f"Field '{display_name}' integer part cannot exceed 13 digits")
                
                return (True, None)
            except ValueError:
                return (False, f"Field '{display_name}' must be a valid decimal number")
                
        elif data_type.lower() == "date":
            # Date - validate format (dd/mm/yyyy or yyyy-mm-dd)
            if not is_valid_date(value):
                return (False, f"Field '{display_name}' must be a valid date (dd/mm/yyyy or yyyy-mm-dd)")
            return (True, None)
            
        elif data_type.lower() in ("checkbox", "boolean"):
            # Checkbox / boolean — accept true or false in any letter case
            if value.strip().lower() not in ("true", "false"):
                return (False, f"Field '{display_name}' must be 'true' or 'false' (any case)")
            return (True, None)
            
        elif data_type.lower() == "time":
            # Time - validate format (HH:mm:ss or HH:mm)
            if not is_valid_time(value):
                return (False, f"Field '{display_name}' must be a valid time (HH:mm:ss or HH:mm)")
            return (True, None)
            
        elif data_type.lower() == "percentage":
            # Percentage - must be number between 0 and 100
            try:
                pct = float(value)
                if pct < 0 or pct > 100:
                    return (False, f"Field '{display_name}' must be a percentage value between 0 and 100")
                return (True, None)
            except ValueError:
                return (False, f"Field '{display_name}' must be a valid percentage number")
                
        elif data_type.lower() == "dropdown":
            # Dropdown — match EnumValue text (handles Excel 1.0 vs DB "1") or Custom_Field_Enum row ID
            enum_values = get_custom_field_enum_values(metadata_id)
            if resolve_dropdown_for_bulk(metadata_id, value, enum_values) is None:
                return (False, f"Field '{display_name}' must be one of the allowed dropdown values: {', '.join(enum_values)}")
            return (True, None)
            
        elif data_type.lower() == "multiselect":
            # Multiselect — same resolution as dropdown (labels, Excel floats, enum row IDs)
            enum_values = get_custom_field_enum_values(metadata_id)
            values = [v.strip() for v in value.split(",")]
            for v in values:
                if v and resolve_dropdown_for_bulk(metadata_id, v, enum_values) is None:
                    return (False, f"Field '{display_name}' contains invalid value: '{v}'. Allowed values: {', '.join(enum_values)}")
            return (True, None)
            
        else:
            logger.warn(f"Unknown data type for custom field validation: {data_type}")
            return (True, None)  # Allow unknown types
            
    except Exception as e:
        logger.error(f"Error validating custom field value: {e}")
        return (False, f"Error validating field '{display_name}': {str(e)}")


def is_valid_date(date_str: str) -> bool:
    """
    Validate date format
    Supports: dd/mm/yyyy, yyyy-mm-dd, etc.
    """
    if not date_str or date_str.strip() == "":
        return False
    
    # Try common date formats
    date_formats = [
        "%d/%m/%Y",
        "%Y-%m-%d",
        "%d-%m-%Y",
        "%Y/%m/%d",
        "%d.%m.%Y",
        "%Y.%m.%d"
    ]
    
    for fmt in date_formats:
        try:
            datetime.strptime(date_str.strip(), fmt)
            return True
        except ValueError:
            continue
    
    # Also check simple pattern match
    if re.match(r'^\d{1,2}[/-]\d{1,2}[/-]\d{2,4}$', date_str.strip()) or \
       re.match(r'^\d{4}-\d{2}-\d{2}$', date_str.strip()):
        return True
    
    return False


def is_valid_time(time_str: str) -> bool:
    """
    Validate time format
    Supports: HH:mm:ss or HH:mm
    """
    if not time_str or time_str.strip() == "":
        return False
    
    # Validate HH:mm:ss or HH:mm format
    pattern = r'^\d{1,2}:\d{2}(:\d{2})?$'
    if re.match(pattern, time_str.strip()):
        # Additional validation for hour and minute ranges
        parts = time_str.strip().split(":")
        hour = int(parts[0])
        minute = int(parts[1])
        if 0 <= hour <= 23 and 0 <= minute <= 59:
            if len(parts) == 3:
                second = int(parts[2])
                if 0 <= second <= 59:
                    return True
            else:
                return True
    
    return False


def merge_excel_custom_field_passthrough(
    row: Any,
    validated_row: Dict[str, Any],
    builtin_template_columns: AbstractSet[str],
) -> None:
    """
    Copy Excel columns that are not built-in template fields into validated_row so Java
    CustomFieldBulkUploadHelper receives values when Python's Custom_Field_Metadata DB
    differs from Tomcat. Works for any user-defined custom field header (any DisplayName / cf_*).
    """
    import pandas as pd

    if hasattr(row, "index") and hasattr(row, "get"):
        cols = list(row.index)

        def get_val(c):
            return row.get(c)
    elif isinstance(row, dict):
        cols = list(row.keys())

        def get_val(c):
            return row.get(c)
    else:
        return

    for col in cols:
        key = str(col)
        if not key or key in validated_row:
            continue
        if key in builtin_template_columns:
            continue
        raw = get_val(col)
        cell = format_excel_cell_for_custom_field(raw)
        if cell is None or (isinstance(cell, str) and not cell.strip()):
            continue
        validated_row[key] = cell


# Standard bulk-template column names per module primary name (object_type passed to validators).
# Any other Excel header is forwarded as a potential custom field.
BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE: Dict[str, frozenset] = {
    "Regulation": frozenset({
        "Reference", "Ref.", "Short Name", "Regulation Long Name", "Description", "Additional Info",
        "Publication Date", "Comments Date", "Finalisation Date", "Compliance Date", "Legal Advice",
        "Parent Regulation Name", "Parent Ref.", "Regulation Maturity", "Regulation Probability",
        "BUDG Status", "Legal Advice Type", "Regulation Stage", "Compliance Level",
        "User Email", "User First Name", "User Last Name", "User Lan ID", "Governance Role",
        "Segment", "Regulation ID",
    }),
    "Regulator": frozenset({"PrimaryName", "ShortName", "Description", "ID", "Segment"}),
    "Policy": frozenset({
        "Name", "Internal", "Description", "Lifecycle", "Type", "Ref.", "refNumber", "URL", "Effective Date",
        "End Date", "Parent Name", "Parent Ref.", "BUDG Viewing", "BUDG Status", "Governance Role",
        "Segment", "ID", "User Email",
    }),
    "Geography": frozenset({
        "Geography Name", "Geography Definition", "Parent Geography Name", "Geography ID", "Segment",
    }),
    "Regulatory Theme": frozenset({
        "Regulatory Theme Long Name", "Reference", "Short Name", "Description",
        "Parent Regulatory Theme Name", "Parent Ref.", "BUDG Status", "Segment", "Regulatory Theme ID",
    }),
    "Capability": frozenset({
        "Capability ID", "ID", "RefNumber", "Reference", "Ref.", "Capability Name", "PrimaryName",
        "Capability Definition", "Description", "BUDG Status", "Lifecycle", "BUDG Viewing",
        "Classification", "Capability Type", "Segment", "Parent Ref.", "Parent Capability",
        "Parent ID", "Parent_ID", "Parent Capability ID", "Governance Role",
    }),
    "Business Area": frozenset({
        "Business Area ID", "ID", "Business Area Name", "PrimaryName", "Description", "BUDG Status",
        "Lifecycle", "BUDG Viewing", "Segment", "Parent Business Area", "Governance Role",
    }),
    "Org Unit": frozenset({
        "Org Unit ID", "ID", "Reference", "Org Unit Name", "Name", "Description",
        "Parent Org Unit Name", "Parent Name", "Parent Org Unit Reference", "Parent Ref.",
        "Segment", "BUDG Status",
    }),
    "Interface": frozenset({
        "Interface ID", "ID", "Interface Name", "Interface Description", "Lifecycle",
        "Source System Short Name", "Target System Short Name", "Automation Level", "Reference",
        "Asset ID", "Synchronisation Control", "Transfer Method", "Transfer Format",
        "Interface Classification", "BUDG Status", "Frequency", "BUDG Viewing", "Governance Role", "Segment",
    }),
    "Product": frozenset({
        "Product ID", "ID", "Reference Number", "Reference", "Product Name", "Primary Name", "PrimaryName",
        "Product Description", "Description", "Lifecycle", "Long Name", "Parent Ref.", "Parent Product Name",
        "BUDG Status", "BUDG Viewing", "Segment",
    }),
    "People": frozenset({
        "People ID", "ID", "Email", "E-mail", "email", "First Name", "Last Name", "Name", "Profile",
        "Employment Type", "Lifecycle", "Function", "Function Description", "Description", "Password",
        "BUDG Status", "Org Unit Reference", "Org Unit Name", "Office Location", "Internal Mail Code",
        "Office Telephone", "Mobile/Cell", "LAN ID", "LAN Id", "Segment",
    }),
    "Client": frozenset({
        "Client ID", "ID", "Client Name", "PrimaryName", "Description", "Long Name", "Parent Client Name",
        "Lifecycle", "BUDG Status", "BUDG Viewing", "Segment",
    }),
    "Legal": frozenset({
        "Legal ID", "ID", "Short Name", "ShortName", "Long Name", "LongName", "Description",
        "BUDG Status", "BUDG Viewing", "Segment",
    }),
    "System": frozenset({
        "ID", "Short Name", "External", "Description", "DQAutomation", "Parent Short Name",
        "BUDG Viewing", "BUDG Status", "Lifecycle", "Type", "Classification", "Governance Role", "Segment",
    }),
    "Data Attributes": frozenset({
        "Attribute ID", "Reference Number", "Attribute Name", "Data Set Ref.", "Data Set Name",
        "System Short Name", "Attribute Definition", "Key", "Data Length", "Data Type",
        "Attribute Requirement", "Glossary Ref.", "Glossary Name", "Parent Glossary Name", "Origin",
        "Editability", "Editability Role", "Governance Role", "Segment",
    }),
    "Dataset": frozenset({
        "ID", "Ref.", "Name", "Definition", "Usage", "BUDG Viewing", "Type", "Lifecycle", "BUDG Status",
        "Glossary Ref.", "Glossary Name", "Parent Glossary Name", "Governance Role", "Segment",
        "System ID", "System Short Name", "Parent System Short Name", "User Email",
    }),
    "Glossary": frozenset({
        "ID", "Name", "Definition", "Ref.", "BUDG Viewing", "BUDG Status", "Lifecycle", "Format Type",
        "Security Classification", "Type", "Governance Role", "Segment",
    }),
    "Committee": frozenset({
        "Committee ID", "Committee Name", "Reference", "Segment", "Parent Ref.", "Parent Committee Name",
        "BUDG Viewing", "BUDG Status", "Classification", "Lifecycle", "Committee Type",
        "Governance Role", "User Email", "Parent_ID",
    }),
    "Process": frozenset({
        "ID", "Ref.", "Name", "Segment", "Duration", "Parent Ref.", "Parent Name", "Parent_ID",
        "BUDG Viewing", "BUDG Status", "Type", "Duration Type", "Lifecycle", "Classification",
        "Automation", "Step Type", "Governance Role",
    }),
    "Project": frozenset({
        "Project ID", "Reference", "Project Name", "Segment", "Start Date", "End Date",
        "Parent Ref.", "Parent Project Name", "Parent_ID", "BUDG Viewing", "RAG", "Classification",
        "BUDG Status", "Project Lifecycle", "Project Type", "Governance Role",
    }),
}


def add_custom_fields_to_validated_data(row: Any, validated_data_dict: Dict[str, Any], 
                                        object_type: str, row_number: int, errors: List[Any]) -> None:
    """
    Helper function to validate and add custom fields to validated_data_dict
    Modifies validated_data_dict and errors in place
    
    Args:
        row: Original row data from Excel (pandas Series or dict)
        validated_data_dict: Dictionary to add validated custom fields to
        object_type: Object type name (e.g., "Regulatory Theme", "Product")
        row_number: Row number for error reporting
        errors: List to append validation errors to
    """
    try:
        import pandas as pd

        if hasattr(row, "index") and hasattr(row, "get"):
            row_dict = {
                str(col): (None if pd.isna(row.get(col)) else row.get(col))
                for col in row.index
            }
        elif hasattr(row, "to_dict"):
            d = row.to_dict()
            row_dict = {}
            for k, v in d.items():
                if v is None:
                    row_dict[str(k)] = None
                elif isinstance(v, float) and pd.isna(v):
                    row_dict[str(k)] = None
                else:
                    try:
                        row_dict[str(k)] = None if pd.isna(v) else v
                    except (TypeError, ValueError):
                        row_dict[str(k)] = v
        elif isinstance(row, dict):
            row_dict = {}
            for k, v in row.items():
                if v is None:
                    row_dict[str(k)] = None
                elif isinstance(v, float) and pd.isna(v):
                    row_dict[str(k)] = None
                else:
                    try:
                        row_dict[str(k)] = None if pd.isna(v) else v
                    except (TypeError, ValueError):
                        row_dict[str(k)] = v
        else:
            row_dict = row

        custom_field_errors, validated_custom_fields = validate_custom_fields_for_row(
            row_dict, object_type, row_number
        )

        for error in custom_field_errors:
            errors.append(error)

        validated_data_dict.update(validated_custom_fields)

        builtins = BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE.get(object_type)
        if builtins is not None:
            merge_excel_custom_field_passthrough(row, validated_data_dict, builtins)

    except Exception as e:
        logger.error(f"Error validating custom fields for row {row_number}: {e}")
        errors.append({
            "row": row_number,
            "field": "Custom Fields",
            "message": f"Unexpected error while validating custom fields: {str(e)}",
            "error_code": "CUSTOM_FIELD_VALIDATION_EXCEPTION"
        })


def validate_custom_fields_for_row(row: Dict[str, Any], object_type: str, row_number: int) -> Tuple[List[Dict[str, Any]], List[str]]:
    """
    Validate all custom fields for a row
    Returns (errors, validated_custom_fields_dict)
    errors: List of error dictionaries with row, field, message, error_code
    validated_custom_fields_dict: Dictionary of validated custom field values (displayName -> value)
    """
    errors = []
    validated_custom_fields = {}
    
    # Get module ID
    module_id = get_module_id_by_object_type(object_type)
    if module_id is None:
        # No custom fields if module not found
        return (errors, validated_custom_fields)
    
    # Get custom fields for this module
    custom_fields = get_custom_fields_for_module(module_id)
    if not custom_fields:
        return (errors, validated_custom_fields)
    
    # Validate each custom field
    for field in custom_fields:
        display_name = field["displayName"]
        custom_field_name = (field.get("customFieldName") or "").strip()
        data_type = field["dataType"]
        metadata_id = field["id"]
        is_mandatory = field["isMandatory"]
        default_value = field["defaultValue"]
        
        # Get value from row: DisplayName, "DisplayName *", CustomFieldName (e.g. cf_number), "cf_number *"
        value = None
        row_value = None
        candidate_keys = [display_name, f"{display_name} *"]
        if custom_field_name:
            candidate_keys.extend([custom_field_name, f"{custom_field_name} *"])
        for ck in candidate_keys:
            if not ck:
                continue
            try:
                rv = row[ck]
            except (KeyError, TypeError, IndexError):
                continue
            if rv is None:
                continue
            try:
                import pandas as pd
                if pd.isna(rv):
                    continue
            except Exception:
                pass
            row_value = rv
            break

        if row_value is not None:
            value = format_excel_cell_for_custom_field(row_value)

        # Bulk: mandatory requires a non-empty cell; metadata default does not satisfy mandatory
        if is_mandatory and (value is None or (isinstance(value, str) and value.strip() == "")):
            errors.append({
                "row": row_number,
                "field": display_name,
                "message": f"Field '{display_name}' is mandatory and cannot be empty",
                "error_code": "CUSTOM_FIELD_MANDATORY"
            })
            continue

        if (value is None or (isinstance(value, str) and value.strip() == "")) and default_value and str(default_value).strip():
            value = str(default_value).strip()
        
        # Validate value format if not empty
        if value and value.strip():
            is_valid, error_message = validate_custom_field_value(value, data_type, metadata_id, display_name)
            if not is_valid:
                errors.append({
                    "row": row_number,
                    "field": display_name,
                    "message": error_message,
                    "error_code": "CUSTOM_FIELD_VALIDATION_ERROR"
                })
                continue
            
            # Normalize enum-like values to canonical DB casing before persistence.
            normalized_value = value
            if data_type.lower() == "dropdown":
                enum_values = get_custom_field_enum_values(metadata_id)
                canonical_value = resolve_dropdown_for_bulk(metadata_id, value, enum_values)
                if canonical_value is not None:
                    normalized_value = canonical_value
            elif data_type.lower() in ("checkbox", "boolean"):
                normalized_value = "true" if value.strip().lower() == "true" else "false"
            elif data_type.lower() == "multiselect":
                enum_values = get_custom_field_enum_values(metadata_id)
                canonical_parts = []
                for part in [v.strip() for v in value.split(",")]:
                    if not part:
                        continue
                    canonical_part = resolve_dropdown_for_bulk(metadata_id, part, enum_values)
                    canonical_parts.append(canonical_part if canonical_part is not None else part)
                normalized_value = ", ".join(canonical_parts)
            elif data_type.lower() == "number" and _is_valid_integer_number_string(value):
                normalized_value = str(int(float(value.replace(",", "").strip())))

            # Add to validated custom fields (ensure it's a string)
            validated_custom_fields[display_name] = str(normalized_value) if normalized_value is not None else normalized_value
    
    return (errors, validated_custom_fields)


def _extract_row_number_from_payload(row: Dict[str, Any]) -> Optional[int]:
    """Extract canonical row number from validated row payload (bulk upload)."""
    if not isinstance(row, dict):
        return None
    for key in ("row_number", "row", "row_num"):
        value = row.get(key)
        if value is None:
            continue
        try:
            return int(value)
        except (TypeError, ValueError):
            continue
    return None


def filter_rows_with_blocking_custom_field_errors(
    validated_data: List[Dict[str, Any]],
    all_errors: List[Any],
) -> List[Dict[str, Any]]:
    """
    Remove rows that have blocking custom-field validation errors.
    Accepts error dicts or Pydantic-like objects with error_code/row.
    """
    blocked_rows = set()
    for error in all_errors:
        if isinstance(error, dict):
            d = error
        elif hasattr(error, "model_dump"):
            d = error.model_dump()
        elif hasattr(error, "dict"):
            d = error.dict()
        else:
            continue
        code = str(d.get("error_code", "")).strip().upper()
        if code not in CUSTOM_FIELD_BLOCKING_ERROR_CODES:
            continue
        try:
            blocked_rows.add(int(d.get("row")))
        except (TypeError, ValueError):
            continue

    if not blocked_rows:
        return validated_data

    filtered_rows = []
    for row in validated_data:
        row_number = _extract_row_number_from_payload(row)
        if row_number is not None and row_number in blocked_rows:
            continue
        filtered_rows.append(row)

    removed_count = len(validated_data) - len(filtered_rows)
    if removed_count > 0:
        logger.info(
            "Filtered %s row(s) from validated_data due to custom-field validation errors",
            removed_count
        )
    return filtered_rows

