"""
Glossary Bulk Upload Validation Module
Validates Excel files for Glossary bulk uploads and resolves lookups to IDs.
"""

from typing import List, Tuple, Dict, Any, Optional
import os
import sys
import logging
import pandas as pd
import pymysql

# Add utils directory to path for segment_validator import
sys.path.append(os.path.join(os.path.dirname(__file__), '..', '..', 'utils'))
try:
    from segment_validator import validate_segment_exists, validate_user_segment_access, get_segment_id_by_name, get_segment_name_by_id
except ImportError:
    # Fallback if import fails
    logger.warning("Could not import segment_validator, segment validation will be skipped")
    validate_segment_exists = None
    validate_user_segment_access = None
    get_segment_id_by_name = None
    get_segment_name_by_id = None

try:
    from custom_fields_validator import add_custom_fields_to_validated_data
except ImportError:
    # Fallback if import fails
    add_custom_fields_to_validated_data = None
    logger.warning("Could not import custom_fields_validator")

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USERNAME", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "database": os.getenv("DB_NAME", "project"),
    "charset": "utf8mb4"
}


def get_db_connection():
    return pymysql.connect(**DB_CONFIG)


def get_sheet_name(upload_option: str) -> Optional[str]:
    """
    Return expected sheet name for Glossary bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Glossary"
    if upload_option == "Update Existing Items":
        return "Update Glossary"
    if upload_option == "Remove Existing Items":
        return "Delete Glossary"
    return None


def apply_column_mappings(df: pd.DataFrame, column_mappings: Optional[Dict[str, str]]) -> pd.DataFrame:
    if not column_mappings:
        return df
    
    rename_dict = {}
    for excel_col, expected_field in column_mappings.items():
        if excel_col and expected_field and str(excel_col).strip() and str(expected_field).strip():
            excel_col_normalized = str(excel_col).strip()
            expected_field_normalized = str(expected_field).strip()
            if excel_col_normalized in df.columns:
                rename_dict[excel_col_normalized] = expected_field_normalized
    
    if rename_dict:
        df = df.rename(columns=rename_dict)
    
    return df


class ValidationError:
    def __init__(self, row: int, field: str, message: str, error_code: str):
        self.row = row
        self.field = field
        self.message = message
        self.error_code = error_code

    def dict(self):
        return {"row": self.row, "field": self.field, "message": self.message, "error_code": self.error_code}


def _exists_in_table(table: str, id_value: int, id_col: str = "ID") -> bool:
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT COUNT(*) FROM {table} WHERE {id_col} = %s", (id_value,))
                return cur.fetchone()[0] > 0
    except Exception as e:
        logger.warning("exists_in_table error: %s", e)
        return False


def get_lookup_id_by_name(table: str, name_col: str, name_value: Optional[str]) -> Optional[int]:
    if not name_value or not str(name_value).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT ID FROM {table} WHERE {name_col} = %s LIMIT 1", (str(name_value).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_lookup_id_by_name error: %s", e)
        return None


def get_viewing_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("viewing", "Name", name)


def get_status_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("status", "primaryname", name)


def get_glossary_lifecycle_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("glossary_lifecycle", "Name", name)


def get_glossary_type_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("glossary_type", "Name", name)


def get_glossary_format_type_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("glossary_format_type", "Name", name)


def get_security_classification_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("security_classification", "Name", name)


def get_role_id_by_name(role_name: Optional[str]) -> Optional[int]:
    """Resolve object_role.id by primaryname scoped to the Glossary module."""
    if not role_name or not str(role_name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT id FROM module WHERE LOWER(primaryname) = LOWER(%s) LIMIT 1",
                    ("Glossary",),
                )
                row = cur.fetchone()
                if not row:
                    return None
                module_id = row[0]
                cur.execute(
                    "SELECT id FROM object_role WHERE LOWER(primaryname) = LOWER(%s) AND module = %s LIMIT 1",
                    (str(role_name).strip(), module_id),
                )
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_role_id_by_name error: %s", e)
        return None


def get_glossary_id_by_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM glossary WHERE LOWER(Name) = LOWER(%s) AND (Deleted_datetime IS NULL OR Deleted_datetime = '') LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_glossary_id_by_name error: %s", e)
        return None


def get_glossary_id_by_ref(ref: Optional[str]) -> Optional[int]:
    if not ref or not str(ref).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM glossary WHERE Ref_Number = %s AND (Deleted_datetime IS NULL OR Deleted_datetime = '') LIMIT 1", (str(ref).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_glossary_id_by_ref error: %s", e)
        return None


def validate_and_resolve_glossary(df: pd.DataFrame, upload_option: str, user_id: int, segment_mode: Optional[str] = None, segment: Optional[str] = None) -> Tuple[List[Dict[str, Any]], List[ValidationError]]:
    """
    Validate and resolve Glossary records from DataFrame.
    Returns: (validated_data, errors)
    """
    validated_data = []
    errors = []
    
    operation = "INSERT"
    if upload_option == "Update Existing Items":
        operation = "UPDATE"
    elif upload_option == "Remove Existing Items":
        operation = "DELETE"
    
    for idx, row in df.iterrows():
        row_number = idx + 2
        row_data = {"row_number": row_number, "operation": operation}
        
        try:
            if operation == "DELETE":
                glossary_id = row.get("ID")
                if pd.isna(glossary_id):
                    errors.append(ValidationError(row_number, "ID", "ID is required for deletion", "MISSING_REQUIRED"))
                    continue
                
                row_data["ID"] = int(glossary_id)
                
                if not _exists_in_table("glossary", row_data["ID"]):
                    errors.append(ValidationError(row_number, "ID", f"Glossary with ID {row_data['ID']} does not exist", "NOT_FOUND"))
                    continue
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "Glossary", row_number, errors)
                
                validated_data.append(row_data)
                
            elif operation == "UPDATE":
                def _has_val(v):
                    if v is None or (hasattr(v, '__len__') and not v): return False
                    if pd.isna(v): return False
                    s = str(v).strip()
                    return bool(s) and s.lower() not in ("nan", "")
                id_raw = row.get("ID")
                name_raw = row.get("Name")
                ref_raw = row.get("Ref.")
                id_by_id = None
                if _has_val(id_raw):
                    try:
                        vid = int(float(str(id_raw).strip()))
                        if _exists_in_table("glossary", vid):
                            id_by_id = vid
                    except (ValueError, TypeError):
                        pass
                id_by_name = get_glossary_id_by_name(str(name_raw).strip()) if _has_val(name_raw) else None
                id_by_ref = get_glossary_id_by_ref(str(ref_raw).strip()) if _has_val(ref_raw) else None
                filled = sum(1 for v in (id_raw, name_raw, ref_raw) if _has_val(v))
                if filled == 0:
                    errors.append(ValidationError(row_number, "ID", "At least one of ID, Name, or Ref. is required for update", "MISSING_REQUIRED"))
                    continue
                glossary_id = id_by_id or id_by_name or id_by_ref
                if glossary_id is None:
                    errors.append(ValidationError(row_number, "ID", "No glossary found for the provided identity (ID, Name, or Ref.)", "NOT_FOUND"))
                    continue
                if filled >= 2:
                    ids = {x for x in (id_by_id, id_by_name, id_by_ref) if x is not None}
                    if len(ids) > 1:
                        errors.append(ValidationError(row_number, "ID", "ID, Name and Ref. refer to different glossary terms", "IDENTITY_MISMATCH"))
                        continue
                row_data["ID"] = glossary_id
                
                # Name (optional on update: only set when non-empty)
                name = row.get("Name")
                if name and not pd.isna(name) and str(name).strip():
                    row_data["Name"] = str(name).strip()
                
                # Definition (optional on update: only set when non-empty)
                definition = row.get("Definition")
                if definition and not pd.isna(definition) and str(definition).strip():
                    row_data["Definition"] = str(definition).strip()
                
                # Optional text fields
                for field in ["Ref.", "Usage", "Acronym", "Context"]:
                    val = row.get(field)
                    if not pd.isna(val) and str(val).strip():
                        row_data[field] = str(val).strip()
                
                # Lookups
                viewing_name = row.get("BUDG Viewing")
                if not pd.isna(viewing_name) and str(viewing_name).strip():
                    viewing_id = get_viewing_id_by_name(str(viewing_name).strip())
                    if viewing_id:
                        row_data["BUDG Viewing_ID"] = viewing_id
                
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_status_id_by_name(str(status_name).strip())
                    if status_id:
                        row_data["BUDG Status_ID"] = status_id
                
                # Lifecycle (optional on update: only set when non-empty)
                lifecycle_name = row.get("Lifecycle")
                if lifecycle_name and not pd.isna(lifecycle_name) and str(lifecycle_name).strip():
                    lifecycle_id = get_glossary_lifecycle_id_by_name(str(lifecycle_name).strip())
                    if not lifecycle_id:
                        errors.append(ValidationError(row_number, "Lifecycle", f"Lifecycle '{lifecycle_name}' not found", "NOT_FOUND"))
                        continue
                    row_data["Lifecycle_ID"] = lifecycle_id
                
                # Format Type (optional on update: only set when provided)
                format_type_name = row.get("Format Type")
                if format_type_name is not None and not pd.isna(format_type_name) and str(format_type_name).strip():
                    format_type_id = get_glossary_format_type_id_by_name(str(format_type_name).strip())
                    if not format_type_id:
                        errors.append(ValidationError(row_number, "Format Type", f"Format Type '{format_type_name}' not found", "NOT_FOUND"))
                        continue
                    row_data["Format Type_ID"] = format_type_id
                
                # Security Classification (optional on update: only set when provided)
                security_classification_name = row.get("Security Classification")
                if security_classification_name is not None and not pd.isna(security_classification_name) and str(security_classification_name).strip():
                    security_classification_id = get_security_classification_id_by_name(str(security_classification_name).strip())
                    if not security_classification_id:
                        errors.append(ValidationError(row_number, "Security Classification", f"Security Classification '{security_classification_name}' not found", "NOT_FOUND"))
                        continue
                    row_data["Security Classification_ID"] = security_classification_id
                
                # Type (optional on update: only set when provided)
                type_name = row.get("Type")
                if type_name is not None and not pd.isna(type_name) and str(type_name).strip():
                    type_id = get_glossary_type_id_by_name(str(type_name).strip())
                    if not type_id:
                        errors.append(ValidationError(row_number, "Type", f"Type '{type_name}' not found", "NOT_FOUND"))
                        continue
                    row_data["Type_ID"] = type_id
                
                # Governance Role
                role_name = row.get("Governance Role")
                if not pd.isna(role_name) and str(role_name).strip():
                    role_id = get_role_id_by_name(str(role_name).strip())
                    if role_id:
                        row_data["Governance Role_ID"] = role_id
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "Glossary", row_number, errors)
                
                validated_data.append(row_data)
                
            else:  # INSERT
                # Required: Name
                name = row.get("Name")
                if not name or pd.isna(name) or str(name).strip() == "":
                    errors.append(ValidationError(row_number, "Name", "Name is required", "MISSING_REQUIRED"))
                    continue
                row_data["Name"] = str(name).strip()
                
                # Required: Definition
                definition = row.get("Definition")
                if not definition or pd.isna(definition) or str(definition).strip() == "":
                    errors.append(ValidationError(row_number, "Definition", "Definition is required", "MISSING_REQUIRED"))
                    continue
                row_data["Definition"] = str(definition).strip()
                
                # Segment validation (Rules A, B, C) - only for INSERT operations
                # Initialize segment_name outside validation block so it's available for inclusion in row_data
                segment_value = row.get("Segment")
                segment_name = str(segment_value).strip() if segment_value and not pd.isna(segment_value) else None
                
                if segment_mode and segment_mode in ["MULTIPLE", "ENTERPRISE", "SPECIFIC"]:
                    
                    # Rule A: Segment Mode Enforcement
                    if segment_mode == "MULTIPLE":
                        if not segment_name or segment_name == "":
                            # No segment in Excel - check if selectedSegment from UI is available
                            if segment and segment.strip():
                                # Use selectedSegment from UI as default for this row
                                # Validate that the selected segment is accessible
                                if validate_user_segment_access and user_id:
                                    # segment is the segment ID from UI
                                    try:
                                        segment_id_int = int(segment.strip())
                                        # Get segment name by ID to validate access
                                        if get_segment_name_by_id:
                                            selected_segment_name = get_segment_name_by_id(segment_id_int)
                                            if selected_segment_name:
                                                has_access, access_segment_id = validate_user_segment_access(user_id, selected_segment_name)
                                                if not has_access:
                                                    errors.append(ValidationError(row_number, "Segment", f"User does not have access to selected segment '{selected_segment_name}'", "SEGMENT_ACCESS_DENIED"))
                                                # If accessible, use it (will be set in row_data below)
                                            else:
                                                errors.append(ValidationError(row_number, "Segment", f"Selected segment ID '{segment}' does not exist", "SEGMENT_NOT_FOUND"))
                                        else:
                                            # Cannot validate by ID, require segment in Excel
                                            errors.append(ValidationError(row_number, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                                    except ValueError:
                                        # segment is not a valid ID, require segment in Excel
                                        errors.append(ValidationError(row_number, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                                else:
                                    # No validation functions available, require segment in Excel
                                    errors.append(ValidationError(row_number, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                            else:
                                # No segment in Excel and no selectedSegment in UI
                                errors.append(ValidationError(row_number, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                        else:
                            # Segment specified in Excel - validate it
                            # Rule C: Segment must exist and user must have access
                            if validate_segment_exists and user_id:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_number, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                else:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_number, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED"))
                    elif segment_mode == "ENTERPRISE":
                        # Segment column is optional, but if provided, validate it
                        if segment_name and segment_name != "" and segment_name.lower() != "enterprise":
                            # Allow empty or "Enterprise", but validate if something else is provided
                            if validate_segment_exists:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_number, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                elif user_id and validate_user_segment_access:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_number, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED"))
                    elif segment_mode == "SPECIFIC":
                        # SPECIFIC mode: If segment is provided in Excel, use it (overrides UI), otherwise use selectedSegment from UI
                        if segment_name and segment_name != "":
                            # Segment specified in Excel - use it (overrides UI selection)
                            # Rule C: Validate segment exists and user has access
                            if validate_segment_exists and user_id:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_number, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                else:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_number, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED"))
                        else:
                            # No segment in Excel - use selectedSegment from UI (applies to all rows)
                            if segment and segment.strip():
                                # Validate that the selected segment is accessible
                                if validate_user_segment_access and user_id:
                                    try:
                                        segment_id_int = int(segment.strip())
                                        # Get segment name by ID to validate access
                                        if get_segment_name_by_id:
                                            selected_segment_name = get_segment_name_by_id(segment_id_int)
                                            if selected_segment_name:
                                                has_access, access_segment_id = validate_user_segment_access(user_id, selected_segment_name)
                                                if not has_access:
                                                    errors.append(ValidationError(row_number, "Segment", f"User does not have access to selected segment '{selected_segment_name}'", "SEGMENT_ACCESS_DENIED"))
                                                # If accessible, will use it (Java will use selectedSegment parameter)
                                            else:
                                                errors.append(ValidationError(row_number, "Segment", f"Selected segment ID '{segment}' does not exist", "SEGMENT_NOT_FOUND"))
                                        # If no get_segment_name_by_id function, Java will handle validation
                                    except ValueError:
                                        # segment is not a valid ID
                                        errors.append(ValidationError(row_number, "Segment", f"Invalid selected segment ID: '{segment}'", "SEGMENT_INVALID"))
                                # If no validation functions, Java will handle it
                            # If no segment in Excel and no selectedSegment in UI, Java will throw error
                
                # Optional Ref (auto-generated if empty)
                ref = row.get("Ref.")
                if not pd.isna(ref) and str(ref).strip():
                    row_data["Ref."] = str(ref).strip()
                
                # Optional text fields
                for field in ["Usage", "Acronym", "Context"]:
                    val = row.get(field)
                    if not pd.isna(val) and str(val).strip():
                        row_data[field] = str(val).strip()
                
                # Lookups
                viewing_name = row.get("BUDG Viewing")
                if not pd.isna(viewing_name) and str(viewing_name).strip():
                    viewing_id = get_viewing_id_by_name(str(viewing_name).strip())
                    if viewing_id:
                        row_data["BUDG Viewing_ID"] = viewing_id
                
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_status_id_by_name(str(status_name).strip())
                    if status_id:
                        row_data["BUDG Status_ID"] = status_id
                
                # Lifecycle (required)
                lifecycle_name = row.get("Lifecycle")
                if pd.isna(lifecycle_name) or not str(lifecycle_name).strip():
                    errors.append(ValidationError(row_number, "Lifecycle", "Lifecycle is required", "MISSING_REQUIRED"))
                    continue
                lifecycle_id = get_glossary_lifecycle_id_by_name(str(lifecycle_name).strip())
                if not lifecycle_id:
                    errors.append(ValidationError(row_number, "Lifecycle", f"Lifecycle '{lifecycle_name}' not found", "NOT_FOUND"))
                    continue
                row_data["Lifecycle_ID"] = lifecycle_id
                
                # Format Type (required)
                format_type_name = row.get("Format Type")
                if pd.isna(format_type_name) or not str(format_type_name).strip():
                    errors.append(ValidationError(row_number, "Format Type", "Format Type is required", "MISSING_REQUIRED"))
                    continue
                format_type_id = get_glossary_format_type_id_by_name(str(format_type_name).strip())
                if not format_type_id:
                    errors.append(ValidationError(row_number, "Format Type", f"Format Type '{format_type_name}' not found", "NOT_FOUND"))
                    continue
                row_data["Format Type_ID"] = format_type_id
                
                # Security Classification (required)
                security_classification_name = row.get("Security Classification")
                if pd.isna(security_classification_name) or not str(security_classification_name).strip():
                    errors.append(ValidationError(row_number, "Security Classification", "Security Classification is required", "MISSING_REQUIRED"))
                    continue
                security_classification_id = get_security_classification_id_by_name(str(security_classification_name).strip())
                if not security_classification_id:
                    errors.append(ValidationError(row_number, "Security Classification", f"Security Classification '{security_classification_name}' not found", "NOT_FOUND"))
                    continue
                row_data["Security Classification_ID"] = security_classification_id
                
                # Type (required)
                type_name = row.get("Type")
                if pd.isna(type_name) or not str(type_name).strip():
                    errors.append(ValidationError(row_number, "Type", "Type is required", "MISSING_REQUIRED"))
                    continue
                type_id = get_glossary_type_id_by_name(str(type_name).strip())
                if not type_id:
                    errors.append(ValidationError(row_number, "Type", f"Type '{type_name}' not found", "NOT_FOUND"))
                    continue
                row_data["Type_ID"] = type_id
                
                # Governance Role
                role_name = row.get("Governance Role")
                if not pd.isna(role_name) and str(role_name).strip():
                    role_id = get_role_id_by_name(str(role_name).strip())
                    if role_id:
                        row_data["Governance Role_ID"] = role_id
                
                # Include Segment in resolved data if present
                # For MULTIPLE mode: use segment from Excel, or selectedSegment from UI if no Excel segment
                # For SPECIFIC mode: use segment from Excel (overrides UI), or selectedSegment from UI if no Excel segment
                # For ENTERPRISE mode: use "Enterprise" or segment from Excel if provided
                # For null mode: use segment from Excel if provided
                if segment_mode == "MULTIPLE":
                    if segment_name:
                        row_data["Segment"] = segment_name
                    elif segment and segment.strip():
                        # Use selectedSegment from UI if no Excel segment
                        if get_segment_name_by_id:
                            try:
                                selected_segment_name = get_segment_name_by_id(int(segment.strip()))
                                if selected_segment_name:
                                    row_data["Segment"] = selected_segment_name
                            except (ValueError, TypeError):
                                pass  # Java will handle validation
                elif segment_mode == "SPECIFIC":
                    if segment_name:
                        # Segment from Excel (overrides UI)
                        row_data["Segment"] = segment_name
                    # If no segment in Excel, Java will use selectedSegment from UI
                elif segment_mode == "ENTERPRISE":
                    # Always include "Enterprise" for ENTERPRISE mode (or segment from Excel if provided)
                    if segment_name and segment_name.lower() != "enterprise":
                        row_data["Segment"] = segment_name
                    else:
                        row_data["Segment"] = "Enterprise"
                elif segment_mode is None and segment_name:
                    # Include segment if provided even when mode is null
                    row_data["Segment"] = segment_name
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "Glossary", row_number, errors)
                
                validated_data.append(row_data)
                
        except Exception as e:
            logger.error(f"Error processing row {row_number}: {e}")
            errors.append(ValidationError(row_number, "General", str(e), "PROCESSING_ERROR"))
    
    return validated_data, errors

