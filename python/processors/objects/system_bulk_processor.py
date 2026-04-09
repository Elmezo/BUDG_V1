"""
System Bulk Upload Validation Module
Validates Excel files for System bulk uploads and resolves lookups to IDs.
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
    from segment_validator import (
        validate_segment_exists,
        validate_user_segment_access,
        get_segment_id_by_name,
        get_segment_name_by_id,
        get_object_segment_id,
        segment_matches_ui_selection,
    )
except ImportError:
    # Fallback if import fails
    logging.warning("Could not import segment_validator, segment validation will be skipped")
    validate_segment_exists = None
    validate_user_segment_access = None
    get_segment_id_by_name = None
    get_segment_name_by_id = None
    get_object_segment_id = None
    segment_matches_ui_selection = None
try:
    from parent_hierarchy_validator import would_create_parent_cycle_by_entity
except ImportError:
    would_create_parent_cycle_by_entity = None

try:
    from custom_fields_validator import add_custom_fields_to_validated_data
except ImportError:
    add_custom_fields_to_validated_data = None
    logging.warning("Could not import custom_fields_validator")

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

try:
    from budg_db_config import get_pymysql_config

    DB_CONFIG = get_pymysql_config()
except ImportError:
    DB_CONFIG = {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": int(os.getenv("DB_PORT", "3306")),
        "user": os.getenv("DB_USERNAME", "root"),
        "password": os.getenv("DB_PASSWORD", ""),
        "database": os.getenv("DB_NAME", "project"),
        "charset": "utf8mb4",
    }


def get_db_connection():
    return pymysql.connect(**DB_CONFIG)


def get_sheet_name(upload_option: str) -> Optional[str]:
    """
    Return expected sheet name for System bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create System"
    if upload_option == "Update Existing Items":
        return "Update System"
    if upload_option == "Remove Existing Items":
        return "Delete System"
    return None


def apply_column_mappings(df: pd.DataFrame, column_mappings: Optional[Dict[str, str]]) -> pd.DataFrame:
    """Apply column mappings to rename Excel columns to expected field names."""
    if not column_mappings:
        logger.info("No column mappings provided, skipping mapping step")
        return df
    
    logger.info(f"Applying column mappings. Received {len(column_mappings)} mappings")
    
    rename_dict = {}
    
    for excel_col, expected_field in column_mappings.items():
        if not excel_col or not expected_field or not str(excel_col).strip() or not str(expected_field).strip():
            continue
        
        excel_col_normalized = str(excel_col).strip()
        expected_field_normalized = str(expected_field).strip()
        
        if excel_col_normalized in df.columns:
            rename_dict[excel_col_normalized] = expected_field_normalized
            logger.info(f"✓ Mapping: '{excel_col_normalized}' -> '{expected_field_normalized}'")
        else:
            logger.warning(f"✗ Excel column '{excel_col_normalized}' not found in DataFrame")
    
    if rename_dict:
        df = df.rename(columns=rename_dict)
        logger.info(f"Applied {len(rename_dict)} column mappings")
    else:
        logger.warning("No column mappings could be applied")
    
    return df


class ValidationError:
    def __init__(self, row: int, field: str, message: str, error_code: str):
        self.row = row
        self.field = field
        self.message = message
        self.error_code = error_code

    def dict(self):
        return {"row": self.row, "field": self.field, "message": self.message, "error_code": self.error_code}


def _exists_in_table(table: str, id_value: int, id_col: str = "id") -> bool:
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT COUNT(*) FROM {table} WHERE {id_col} = %s", (id_value,))
                return cur.fetchone()[0] > 0
    except Exception as e:
        logger.warning("exists_in_table error for %s: %s", table, e)
        return False


def _system_exists_and_not_deleted(system_id: int) -> bool:
    """Return True only if the system exists and is not soft-deleted."""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT 1 FROM system WHERE id = %s AND (Deleted_datetime IS NULL OR Deleted_datetime = '') LIMIT 1",
                    (system_id,),
                )
                return cur.fetchone() is not None
    except Exception as e:
        logger.warning("_system_exists_and_not_deleted error for id %s: %s", system_id, e)
        return False


def get_system_name_by_id(system_id: int) -> Optional[str]:
    """Get System name (Short Name) by ID"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT Name FROM system WHERE id = %s LIMIT 1", (system_id,))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_system_name_by_id error: %s", e)
        return None


def get_lookup_id_by_name(table: str, name_col: str, name_value: Optional[str]) -> Optional[int]:
    if not name_value or not str(name_value).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT id FROM {table} WHERE {name_col} = %s LIMIT 1", (str(name_value).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_lookup_id_by_name error for %s.%s: %s", table, name_col, e)
        return None


def get_viewing_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("viewing", "Name", name)


def get_status_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("status", "primaryname", name)


def get_system_lifecycle_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("system_lifecycle", "Name", name)


def get_system_type_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("system_type", "Name", name)


def get_system_classification_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("system_classification", "Name", name)


def get_cia_rating_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("cia_rating", "`Values`", name)


def get_system_id_by_name(name: Optional[str]) -> Optional[int]:
    """Get system ID by Short Name"""
    return get_lookup_id_by_name("system", "Name", name)


def get_first_id_from_table(table: str, id_col: str = "id") -> Optional[int]:
    """Get the first ID from a lookup table"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT {id_col} FROM {table} ORDER BY {id_col} ASC LIMIT 1")
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning(f"get_first_id_from_table error for {table}: %s", e)
        return None


def get_first_system_lifecycle_id() -> Optional[int]:
    return get_first_id_from_table("system_lifecycle")


def get_first_system_type_id() -> Optional[int]:
    return get_first_id_from_table("system_type")


def get_role_id_by_name(role_name: Optional[str]) -> Optional[int]:
    """Resolve object_role.id by primaryname scoped to the System module."""
    if not role_name or not str(role_name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT id FROM module WHERE LOWER(primaryname) = LOWER(%s) LIMIT 1",
                    ("System",),
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


def parse_bool(value: Any) -> Optional[int]:
    """Convert various boolean representations to 0 or 1"""
    if value is None or pd.isna(value):
        return None
    
    str_val = str(value).strip().lower()
    
    if str_val in ['true', 'yes', '1', 'y', 't']:
        return 1
    elif str_val in ['false', 'no', '0', 'n', 'f']:
        return 0
    
    return None


def validate_and_resolve_system(df: pd.DataFrame, upload_option: str, user_id: int, segment_mode: Optional[str] = None, segment: Optional[str] = None) -> Tuple[List[Dict[str, Any]], List[ValidationError]]:
    """
    Validate and resolve System records from DataFrame.
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
        row_number = idx + 2  # Excel row number (header is row 1)
        row_data = {"row_number": row_number, "operation": operation}
        
        try:
            if operation == "DELETE":
                # DELETE: Only need ID
                system_id = row.get("ID")
                if pd.isna(system_id):
                    errors.append(ValidationError(row_number, "ID", 
                        "Required field missing: The 'ID' field is required for deletion and cannot be empty. Please provide the System ID to delete.",
                        "MISSING_REQUIRED"))
                    continue
                
                row_data["ID"] = int(system_id)
                
                if not _system_exists_and_not_deleted(row_data["ID"]):
                    system_name = get_system_name_by_id(row_data["ID"])
                    if system_name:
                        errors.append(ValidationError(row_number, "ID", 
                            f"Cannot delete System: System with ID {row_data['ID']} (Name: '{system_name}') does not exist in the database. Please verify the ID is correct and the System has not already been deleted.",
                            "NOT_FOUND"))
                    else:
                        errors.append(ValidationError(row_number, "ID", 
                            f"Cannot delete System: System with ID {row_data['ID']} does not exist in the database. Please verify the ID is correct and the System has not already been deleted.",
                            "NOT_FOUND"))
                    continue
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "System", row_number, errors)
                
                validated_data.append(row_data)
                
            elif operation == "UPDATE":
                def _has_val(v):
                    if v is None or (hasattr(v, '__len__') and not v): return False
                    if pd.isna(v): return False
                    s = str(v).strip()
                    return bool(s) and s.lower() not in ("nan", "")
                id_raw = row.get("ID")
                name_raw = row.get("Short Name")
                id_by_id = None
                if _has_val(id_raw):
                    try:
                        vid = int(float(str(id_raw).strip()))
                        if _system_exists_and_not_deleted(vid):
                            id_by_id = vid
                    except (ValueError, TypeError):
                        pass
                id_by_name = get_system_id_by_name(str(name_raw).strip()) if _has_val(name_raw) else None
                filled = sum(1 for v in (id_raw, name_raw) if _has_val(v))
                if filled == 0:
                    errors.append(ValidationError(row_number, "ID", "At least one of ID or Short Name is required for update", "MISSING_REQUIRED"))
                    continue
                system_id = id_by_id or id_by_name
                if system_id is None:
                    if _has_val(id_raw):
                        try:
                            vid = int(float(str(id_raw).strip()))
                            errors.append(ValidationError(row_number, "ID", f"System with ID {vid} does not exist or has been deleted.", "NOT_FOUND"))
                        except (ValueError, TypeError):
                            errors.append(ValidationError(row_number, "ID", "No system found for the provided identity (ID or Short Name).", "NOT_FOUND"))
                    else:
                        errors.append(ValidationError(row_number, "ID", "No system found for the provided identity (ID or Short Name).", "NOT_FOUND"))
                    continue
                if filled >= 2:
                    ids = {x for x in (id_by_id, id_by_name) if x is not None}
                    if len(ids) > 1:
                        errors.append(ValidationError(row_number, "ID", "ID and Short Name refer to different systems", "IDENTITY_MISMATCH"))
                        continue
                row_data["ID"] = system_id
                
                # Short Name optional for update (empty = keep existing)
                short_name = row.get("Short Name")
                if short_name is not None and not pd.isna(short_name) and str(short_name).strip() != "":
                    row_data["Short Name"] = str(short_name).strip()
                else:
                    row_data["Short Name"] = ""
                
                # External optional for update (empty = keep existing; omit key so Java uses existing)
                external = row.get("External")
                if external is not None and not pd.isna(external) and str(external).strip() != "":
                    parsed_external = parse_bool(external)
                    if parsed_external is None:
                        errors.append(ValidationError(row_number, "External", 
                            f"Invalid value: The 'External' field must be TRUE or FALSE, but received '{external}'. Please provide a valid boolean value.",
                            "INVALID_VALUE"))
                        continue
                    row_data["External"] = parsed_external
                # when empty, do not set row_data["External"] so Java keeps existing
                
                # Description (required)
                description = row.get("Description")
                if not description or pd.isna(description) or str(description).strip() == "":
                    errors.append(ValidationError(row_number, "Description", 
                        "Required field missing: The 'Description' field is required and cannot be empty. Please provide a description for this System.",
                        "MISSING_REQUIRED"))
                    continue
                row_data["Description"] = str(description).strip()
                
                # Optional text fields
                for field in ["Long Name", "Asset ID", "URL"]:
                    val = row.get(field)
                    if not pd.isna(val) and str(val).strip():
                        row_data[field] = str(val).strip()
                
                # DQ Automation (optional boolean)
                dq_automation = row.get("DQAutomation")
                if not pd.isna(dq_automation):
                    row_data["DQAutomation"] = parse_bool(dq_automation)
                
                # Parent resolution
                parent_short_name = row.get("Parent Short Name")
                if not pd.isna(parent_short_name) and str(parent_short_name).strip():
                    parent_id = get_system_id_by_name(str(parent_short_name).strip())
                    if parent_id:
                        row_data["Parent_ID"] = parent_id
                        obj_id = row_data.get("ID")
                        if obj_id and would_create_parent_cycle_by_entity:
                            if would_create_parent_cycle_by_entity(get_db_connection, "System", obj_id, parent_id):
                                errors.append(ValidationError(
                                    row_number,
                                    "Parent Short Name",
                                    "Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.",
                                    "PARENT_CYCLE",
                                ))
                                continue
                        if obj_id and get_object_segment_id:
                            obj_seg = get_object_segment_id(obj_id, "System")
                            parent_seg = get_object_segment_id(parent_id, "System")
                            if obj_seg is not None and parent_seg is not None and obj_seg != -1 and parent_seg != -1:
                                if obj_seg != 1 and parent_seg != 1 and obj_seg != parent_seg:
                                    errors.append(ValidationError(
                                        row_number,
                                        "Parent Short Name",
                                        "Cannot set parent to an object in a different private segment.",
                                        "PARENT_DIFFERENT_PRIVATE_SEGMENT",
                                    ))
                                    continue
                    else:
                        errors.append(ValidationError(row_number, "Parent Short Name",
                                                     f"Parent System not found: No System found with Short Name '{parent_short_name}'. Please verify the name is correct and the System exists in the system.",
                                                     "NOT_FOUND"))
                
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
                
                # Lifecycle (required - use first value if empty)
                lifecycle_name = row.get("Lifecycle")
                lifecycle_id = None
                if not lifecycle_name or pd.isna(lifecycle_name) or str(lifecycle_name).strip() == "":
                    # Use first lifecycle if empty
                    lifecycle_id = get_first_system_lifecycle_id()
                else:
                    lifecycle_id = get_system_lifecycle_id_by_name(str(lifecycle_name).strip())
                if lifecycle_id:
                    row_data["Lifecycle_ID"] = lifecycle_id
                
                # Type (required - use first value if empty)
                type_name = row.get("Type")
                type_id = None
                if not type_name or pd.isna(type_name) or str(type_name).strip() == "":
                    # Use first type if empty
                    type_id = get_first_system_type_id()
                else:
                    type_id = get_system_type_id_by_name(str(type_name).strip())
                if type_id:
                    row_data["Type_ID"] = type_id
                
                # Classification
                classification_name = row.get("Classification")
                if not pd.isna(classification_name) and str(classification_name).strip():
                    classification_id = get_system_classification_id_by_name(str(classification_name).strip())
                    if classification_id:
                        row_data["Classification_ID"] = classification_id
                
                # CIA Ratings
                for cia_field in ["Confidentiality", "Integrity", "Availability"]:
                    cia_name = row.get(cia_field)
                    if not pd.isna(cia_name) and str(cia_name).strip():
                        cia_id = get_cia_rating_id_by_name(str(cia_name).strip())
                        if cia_id:
                            row_data[f"{cia_field}_ID"] = cia_id
                
                # Governance Role
                role_name = row.get("Governance Role")
                if not pd.isna(role_name) and str(role_name).strip():
                    role_id = get_role_id_by_name(str(role_name).strip())
                    if role_id:
                        row_data["Governance Role_ID"] = role_id
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "System", row_number, errors)
                
                validated_data.append(row_data)
                
            else:  # INSERT
                # Required fields
                short_name = row.get("Short Name")
                if not short_name or pd.isna(short_name) or str(short_name).strip() == "":
                    errors.append(ValidationError(row_number, "Short Name", 
                        "Required field missing: The 'Short Name' field is required and cannot be empty. Please provide a short name for this System.",
                        "MISSING_REQUIRED"))
                    continue
                row_data["Short Name"] = str(short_name).strip()
                
                # External (required)
                external = row.get("External")
                if pd.isna(external):
                    errors.append(ValidationError(row_number, "External", 
                        "Required field missing: The 'External' field is required and must be either TRUE or FALSE. Please provide a valid value.",
                        "MISSING_REQUIRED"))
                    continue
                parsed_external = parse_bool(external)
                if parsed_external is None:
                    errors.append(ValidationError(row_number, "External", 
                        f"Invalid value: The 'External' field must be TRUE or FALSE, but received '{external}'. Please provide a valid boolean value.",
                        "INVALID_VALUE"))
                    continue
                row_data["External"] = parsed_external
                
                # Description (required)
                description = row.get("Description")
                if not description or pd.isna(description) or str(description).strip() == "":
                    errors.append(ValidationError(row_number, "Description", 
                        "Required field missing: The 'Description' field is required and cannot be empty. Please provide a description for this System.",
                        "MISSING_REQUIRED"))
                    continue
                row_data["Description"] = str(description).strip()
                
                # Segment validation (Rules A, B, C) - only for INSERT operations
                # Initialize segment_name outside validation block so it's available for inclusion in row_data
                segment_value = row.get("Segment")
                segment_name = str(segment_value).strip() if segment_value and not pd.isna(segment_value) else None
                if segment_name:
                    segment_name = segment_name.strip("\ufeff").strip()

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
                                if not exists and segment_matches_ui_selection and segment:
                                    fb_ok, fb_id = segment_matches_ui_selection(segment_name, segment)
                                    if fb_ok:
                                        exists, segment_id = True, fb_id
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
                                if not exists and segment_matches_ui_selection and segment:
                                    fb_ok, fb_id = segment_matches_ui_selection(segment_name, segment)
                                    if fb_ok:
                                        exists, segment_id = True, fb_id
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
                                if not exists and segment_matches_ui_selection and segment:
                                    fb_ok, fb_id = segment_matches_ui_selection(segment_name, segment)
                                    if fb_ok:
                                        exists, segment_id = True, fb_id
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
                
                # Optional text fields
                for field in ["Long Name", "Asset ID", "URL"]:
                    val = row.get(field)
                    if not pd.isna(val) and str(val).strip():
                        row_data[field] = str(val).strip()
                
                # DQ Automation (optional boolean)
                dq_automation = row.get("DQAutomation")
                if not pd.isna(dq_automation):
                    row_data["DQAutomation"] = parse_bool(dq_automation)
                
                # Parent resolution
                parent_short_name = row.get("Parent Short Name")
                if not pd.isna(parent_short_name) and str(parent_short_name).strip():
                    parent_id = get_system_id_by_name(str(parent_short_name).strip())
                    if parent_id:
                        row_data["Parent_ID"] = parent_id
                        obj_id = row_data.get("ID")
                        if obj_id and would_create_parent_cycle_by_entity:
                            if would_create_parent_cycle_by_entity(get_db_connection, "System", obj_id, parent_id):
                                errors.append(ValidationError(
                                    row_number,
                                    "Parent Short Name",
                                    "Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.",
                                    "PARENT_CYCLE",
                                ))
                                continue
                        if obj_id and get_object_segment_id:
                            obj_seg = get_object_segment_id(obj_id, "System")
                            parent_seg = get_object_segment_id(parent_id, "System")
                            if obj_seg is not None and parent_seg is not None and obj_seg != -1 and parent_seg != -1:
                                if obj_seg != 1 and parent_seg != 1 and obj_seg != parent_seg:
                                    errors.append(ValidationError(
                                        row_number,
                                        "Parent Short Name",
                                        "Cannot set parent to an object in a different private segment.",
                                        "PARENT_DIFFERENT_PRIVATE_SEGMENT",
                                    ))
                                    continue
                    else:
                        errors.append(ValidationError(row_number, "Parent Short Name",
                                                     f"Parent System not found: No System found with Short Name '{parent_short_name}'. Please verify the name is correct and the System exists in the system.",
                                                     "NOT_FOUND"))
                
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
                
                # Lifecycle (required - use first value if empty)
                lifecycle_name = row.get("Lifecycle")
                lifecycle_id = None
                if not lifecycle_name or pd.isna(lifecycle_name) or str(lifecycle_name).strip() == "":
                    # Use first lifecycle if empty
                    lifecycle_id = get_first_system_lifecycle_id()
                else:
                    lifecycle_id = get_system_lifecycle_id_by_name(str(lifecycle_name).strip())
                if lifecycle_id:
                    row_data["Lifecycle_ID"] = lifecycle_id
                
                # Type (required - use first value if empty)
                type_name = row.get("Type")
                type_id = None
                if not type_name or pd.isna(type_name) or str(type_name).strip() == "":
                    # Use first type if empty
                    type_id = get_first_system_type_id()
                else:
                    type_id = get_system_type_id_by_name(str(type_name).strip())
                if type_id:
                    row_data["Type_ID"] = type_id
                
                # Classification
                classification_name = row.get("Classification")
                if not pd.isna(classification_name) and str(classification_name).strip():
                    classification_id = get_system_classification_id_by_name(str(classification_name).strip())
                    if classification_id:
                        row_data["Classification_ID"] = classification_id
                
                # CIA Ratings
                for cia_field in ["Confidentiality", "Integrity", "Availability"]:
                    cia_name = row.get(cia_field)
                    if not pd.isna(cia_name) and str(cia_name).strip():
                        cia_id = get_cia_rating_id_by_name(str(cia_name).strip())
                        if cia_id:
                            row_data[f"{cia_field}_ID"] = cia_id
                
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
                    add_custom_fields_to_validated_data(row, row_data, "System", row_number, errors)
                
                validated_data.append(row_data)
                
        except Exception as e:
            logger.error(f"Error processing row {row_number}: {e}")
            errors.append(ValidationError(row_number, "General", str(e), "PROCESSING_ERROR"))
    
    return validated_data, errors

