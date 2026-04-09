"""
Process Bulk Upload Validation Module
Validates Excel files for Process bulk uploads and resolves lookups to IDs.
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
    )
except ImportError:
    # Fallback if import fails
    logger.warning("Could not import segment_validator, segment validation will be skipped")
    validate_segment_exists = None
    validate_user_segment_access = None
    get_segment_id_by_name = None
    get_segment_name_by_id = None
    get_object_segment_id = None
try:
    from bulk_segment_name_utils import (
        duplicate_name_in_facet_segment,
        resolve_effective_segment_id_for_duplicate_check,
    )
except ImportError:
    duplicate_name_in_facet_segment = None
    resolve_effective_segment_id_for_duplicate_check = None

try:
    from parent_hierarchy_validator import would_create_parent_cycle_by_entity
except ImportError:
    would_create_parent_cycle_by_entity = None

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
    Return expected sheet name for Process bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Process"
    if upload_option == "Update Existing Items":
        return "Update Process"
    if upload_option == "Remove Existing Items":
        return "Delete Process"
    return None


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
        logger.warning("exists_in_table error for %s: %s", table, e)
        return False


def get_lookup_id_by_name(table: str, primary_name: Optional[str]) -> Optional[int]:
    if not primary_name or not str(primary_name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                # Case-insensitive and trim so template values match DB (e.g. "Fully Automated" vs "Fully automated")
                cur.execute(
                    f"SELECT ID FROM {table} WHERE LOWER(TRIM(PrimaryName)) = LOWER(TRIM(%s)) LIMIT 1",
                    (str(primary_name).strip(),),
                )
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_lookup_id_by_name error for %s: %s", table, e)
        return None


def get_viewing_id_by_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM viewing WHERE LOWER(Name) = LOWER(%s) LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_viewing_id_by_name error: %s", e)
        return None


def get_process_id_by_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM process WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_process_id_by_name error: %s", e)
        return None


def get_process_id_by_ref(ref_number: Optional[str]) -> Optional[int]:
    if not ref_number or not str(ref_number).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM process WHERE refNumber = %s AND DeletedDatetime IS NULL LIMIT 1", (str(ref_number).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_process_id_by_ref error: %s", e)
        return None


def check_duplicate_primary_name(primary_name: str, exclude_id: Optional[int] = None, segment_id: Optional[int] = None) -> bool:
    """Check if a PrimaryName already exists in the database"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                if exclude_id:
                    cur.execute("SELECT COUNT(*) FROM process WHERE LOWER(PrimaryName) = LOWER(%s) AND ID != %s AND DeletedDatetime IS NULL", (primary_name, exclude_id))
                else:
                    cur.execute("SELECT COUNT(*) FROM process WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL", (primary_name,))
                count = cur.fetchone()[0]
                return count > 0
    except Exception as e:
        logger.warning("check_duplicate_primary_name error: %s", e)
        return False


def get_first_step_type_id() -> Optional[int]:
    """Get the first step type ID from process_step_type table"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM process_step_type ORDER BY ID ASC LIMIT 1")
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_first_step_type_id error: %s", e)
        return None


def get_first_type_id() -> Optional[int]:
    """Get the first type ID from process_type table"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM process_type ORDER BY ID ASC LIMIT 1")
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_first_type_id error: %s", e)
        return None


def get_first_process_lifecycle_id() -> Optional[int]:
    """Get the first lifecycle ID from process_lifecycle table (legacy; prefer get_first_process_lifecycle_status_id)."""
    return get_first_process_lifecycle_status_id()


def get_first_process_lifecycle_status_id() -> Optional[int]:
    """Get the first lifecycle ID from process_lifecycle_status table (matches Java/DB)."""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM process_lifecycle_status ORDER BY ID ASC LIMIT 1")
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_first_process_lifecycle_status_id error: %s", e)
        return None


def parse_bool(value: Any) -> Optional[int]:
    if value is None:
        return None
    s = str(value).strip().lower()
    if s in ("true", "yes", "y", "1", "t"):
        return 1
    if s in ("false", "no", "n", "0", "f"):
        return 0
    try:
        num = int(s)
        return 1 if num != 0 else 0
    except Exception:
        return None


def normalize_process_columns(df: pd.DataFrame, upload_option: str) -> pd.DataFrame:
    """
    Normalize column names so 'Process ID' is accepted as identity column.
    Renames 'Process ID' -> 'ID' (case-insensitive) so validation and Java receive the expected key.
    """
    if df.empty:
        return df
    columns_list = list(df.columns)
    rename_dict = {}
    has_id = any(str(c).strip() == "ID" for c in columns_list)
    for col in columns_list:
        c = str(col).strip()
        if c.lower() == "process id" and not has_id:
            rename_dict[col] = "ID"
            has_id = True
            break
    if rename_dict:
        df = df.rename(columns=rename_dict)
        logger.info("Normalized Process columns: %s -> %s", list(rename_dict.keys()), list(rename_dict.values()))
    return df


def validate_column_headers(df: pd.DataFrame, upload_option: str) -> List[ValidationError]:
    errors: List[ValidationError] = []
    columns = [str(c).strip() for c in df.columns]
    def missing(col: str) -> bool:
        return col not in columns
    if upload_option == "Add New Items":
        required = ["Name", "Description", "Step Type", "Type", "Lifecycle"]
        for col in required:
            if missing(col):
                errors.append(ValidationError(0, col, f"Missing required column: {col}", "MISSING_COLUMN"))
    elif upload_option == "Update Existing Items":
        required = ["ID"]
        for col in required:
            if missing(col):
                errors.append(ValidationError(0, col, f"Missing required column: {col}", "MISSING_COLUMN"))
    elif upload_option == "Remove Existing Items":
        if missing("ID"):
            errors.append(ValidationError(0, "ID", "Missing required column: ID", "MISSING_COLUMN"))
    return errors


def _resolve_parent_id(row: Dict[str, Any]) -> Optional[int]:
    # Prefer Parent_ID, else Parent Ref., else Parent Name
    parent_id = row.get("Parent_ID")
    if parent_id:
        try:
            parent_id_int = int(parent_id)
            return parent_id_int if _exists_in_table("process", parent_id_int) else None
        except Exception:
            pass
    by_ref = get_process_id_by_ref(row.get("Parent Ref."))
    if by_ref:
        return by_ref
    return get_process_id_by_name(row.get("Parent Name"))


def validate_row_data(df: pd.DataFrame, upload_option: str, segment_mode: Optional[str] = None, segment: Optional[str] = None, user_id: Optional[int] = None) -> Tuple[List[ValidationError], List[Dict[str, Any]]]:
    errors: List[ValidationError] = []
    data: List[Dict[str, Any]] = []
    # Normalize column names to exact strings used downstream
    df = df.rename(columns={str(c): str(c).strip() for c in df.columns})
    
    # Build sets of names and refs in file so parent "in same file" is allowed
    names_in_file = set()
    refs_in_file = set()
    if "Name" in df.columns:
        for v in df["Name"].dropna():
            s = str(v).strip()
            if s:
                names_in_file.add(s.lower())
    if "Ref." in df.columns:
        for v in df["Ref."].dropna():
            s = str(v).strip()
            if s:
                refs_in_file.add(s.lower())
    
    # Track names and refs within the template to detect duplicates
    template_names = set()  # Track names within this template
    template_refs = set()    # Track refs within this template
    
    for idx, series in df.iterrows():
        row_num = idx + 2  # account for header row in Excel
        row = {k: (None if pd.isna(v) else v) for k, v in series.items()}
        try:
            if upload_option == "Add New Items":
                # Validate Name (required)
                name = str(row.get("Name", "")).strip() if row.get("Name") and str(row.get("Name")).strip() else ""
                if not name:
                    errors.append(ValidationError(row_num, "Name", "Name is required and cannot be empty", "REQUIRED_FIELD_EMPTY"))
                    continue
                
                # Check for duplicate name within template
                name_lower = name.lower()
                if name_lower in template_names:
                    errors.append(ValidationError(
                        row_num,
                        "Name",
                        f"Duplicate name '{name}' found within the template. Each process name must be unique in the upload file.",
                        "DUPLICATE_NAME_IN_TEMPLATE"
                    ))
                    continue
                
                eff_seg = (
                    resolve_effective_segment_id_for_duplicate_check(row, segment_mode, segment)
                    if resolve_effective_segment_id_for_duplicate_check
                    else 1
                )
                if check_duplicate_primary_name(name, segment_id=eff_seg):
                    errors.append(ValidationError(
                        row_num,
                        "Name",
                        f"Process with name '{name}' already exists in the database for this segment",
                        "DUPLICATE_NAME"
                    ))
                    continue
                
                # Validate Ref. for duplicates within template (if provided)
                ref_value = None
                if row.get("Ref.") and str(row.get("Ref.")).strip():
                    ref_value = str(row.get("Ref.")).strip()
                    ref_lower = ref_value.lower()
                    if ref_lower in template_refs:
                        errors.append(ValidationError(
                            row_num,
                            "Ref.",
                            f"Duplicate reference '{ref_value}' found within the template. Each process reference must be unique in the upload file.",
                            "DUPLICATE_REF_IN_TEMPLATE"
                        ))
                        continue
                    
                    # Check for duplicate ref in database
                    existing_process_id = get_process_id_by_ref(ref_value)
                    if existing_process_id:
                        errors.append(ValidationError(
                            row_num,
                            "Ref.",
                            f"Process with reference '{ref_value}' already exists in the database",
                            "DUPLICATE_REF"
                        ))
                        continue
                
                # Required text fields
                for req in ["Description"]:
                    if not row.get(req) or str(row.get(req)).strip() == "":
                        errors.append(ValidationError(row_num, req, f"Required field '{req}' is empty", "REQUIRED_FIELD_EMPTY"))
                        continue
                
                # Segment validation (Rules A, B, C) - only for INSERT operations
                # Initialize segment_name outside validation block so it's available for inclusion in resolved
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
                                                    errors.append(ValidationError(row_num, "Segment", f"User does not have access to selected segment '{selected_segment_name}'", "SEGMENT_ACCESS_DENIED"))
                                                # If accessible, use it (will be set in row_data below)
                                            else:
                                                errors.append(ValidationError(row_num, "Segment", f"Selected segment ID '{segment}' does not exist", "SEGMENT_NOT_FOUND"))
                                        else:
                                            # Cannot validate by ID, require segment in Excel
                                            errors.append(ValidationError(row_num, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                                    except ValueError:
                                        # segment is not a valid ID, require segment in Excel
                                        errors.append(ValidationError(row_num, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                                else:
                                    # No validation functions available, require segment in Excel
                                    errors.append(ValidationError(row_num, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                            else:
                                # No segment in Excel and no selectedSegment in UI
                                errors.append(ValidationError(row_num, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                        else:
                            # Segment specified in Excel - validate it
                            # Rule C: Segment must exist and user must have access
                            if validate_segment_exists and user_id:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_num, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                else:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_num, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED"))
                    elif segment_mode == "ENTERPRISE":
                        # Segment column is optional, but if provided, validate it
                        if segment_name and segment_name != "" and segment_name.lower() != "enterprise":
                            # Allow empty or "Enterprise", but validate if something else is provided
                            if validate_segment_exists:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_num, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                elif user_id and validate_user_segment_access:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_num, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED"))
                    elif segment_mode == "SPECIFIC":
                        # SPECIFIC mode: If segment is provided in Excel, use it (overrides UI), otherwise use selectedSegment from UI
                        if segment_name and segment_name != "":
                            # Segment specified in Excel - use it (overrides UI selection)
                            # Rule C: Validate segment exists and user has access
                            if validate_segment_exists and user_id:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_num, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                else:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_num, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED"))
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
                                                    errors.append(ValidationError(row_num, "Segment", f"User does not have access to selected segment '{selected_segment_name}'", "SEGMENT_ACCESS_DENIED"))
                                                # If accessible, will use it (Java will use selectedSegment parameter)
                                            else:
                                                errors.append(ValidationError(row_num, "Segment", f"Selected segment ID '{segment}' does not exist", "SEGMENT_NOT_FOUND"))
                                        # If no get_segment_name_by_id function, Java will handle validation
                                    except ValueError:
                                        # segment is not a valid ID
                                        errors.append(ValidationError(row_num, "Segment", f"Invalid selected segment ID: '{segment}'", "SEGMENT_INVALID"))
                                # If no validation functions, Java will handle it
                            # If no segment in Excel and no selectedSegment in UI, Java will throw error
                
                # Prepare resolved fields
                resolved: Dict[str, Any] = {
                    "operation": "INSERT",
                    "row_number": row_num,
                }
                
                # Include Segment in resolved data if present
                # For MULTIPLE mode: use segment from Excel, or selectedSegment from UI if no Excel segment
                # For SPECIFIC mode: use segment from Excel (overrides UI), or selectedSegment from UI if no Excel segment
                # For ENTERPRISE mode: use "Enterprise" or segment from Excel if provided
                # For null mode: use segment from Excel if provided
                if segment_mode == "MULTIPLE":
                    if segment_name:
                        resolved["Segment"] = segment_name
                    elif segment and segment.strip():
                        # Use selectedSegment from UI if no Excel segment
                        if get_segment_name_by_id:
                            try:
                                selected_segment_name = get_segment_name_by_id(int(segment.strip()))
                                if selected_segment_name:
                                    resolved["Segment"] = selected_segment_name
                            except (ValueError, TypeError):
                                pass  # Java will handle validation
                elif segment_mode == "SPECIFIC":
                    if segment_name:
                        # Segment from Excel (overrides UI)
                        resolved["Segment"] = segment_name
                    # If no segment in Excel, Java will use selectedSegment from UI
                elif segment_mode == "ENTERPRISE":
                    # Always include "Enterprise" for ENTERPRISE mode (or segment from Excel if provided)
                    if segment_name and segment_name.lower() != "enterprise":
                        resolved["Segment"] = segment_name
                    else:
                        resolved["Segment"] = "Enterprise"
                elif segment_mode is None and segment_name:
                    # Include segment if provided even when mode is null
                    resolved["Segment"] = segment_name
                # Booleans to 0/1
                for perm_col, out_col in [
                    ("Create Permission", "Create Permission"),
                    ("Read Permission", "Read Permission"),
                    ("Update Permission", "Update Permission"),
                    ("Delete Permission", "Delete Permission"),
                    ("Archive Permission", "Archive Permission"),
                ]:
                    if perm_col in row:
                        val = parse_bool(row.get(perm_col))
                        if val is not None:
                            resolved[out_col] = val
                # Numeric
                if row.get("Duration") not in (None, ""):
                    try:
                        resolved["Duration"] = int(float(row.get("Duration")))
                    except Exception:
                        errors.append(ValidationError(row_num, "Duration", "Duration must be a number", "INVALID_VALUE"))
                        continue
                # Validate Parent Process - check if both ref and name are provided and match
                parent_ref = row.get("Parent Ref.")
                parent_name = row.get("Parent Name")
                has_ref = parent_ref and str(parent_ref).strip()
                has_name = parent_name and str(parent_name).strip()
                # Parent in same file: allow without DB lookup; Java will resolve from batch
                parent_in_file = False
                if has_ref and str(parent_ref).strip().lower() in refs_in_file:
                    parent_in_file = True
                if has_name and str(parent_name).strip().lower() in names_in_file:
                    parent_in_file = True
                
                if parent_in_file:
                    # Do not set resolved["Parent_ID"]; Java will resolve from batch
                    pass
                elif has_ref and has_name:
                    # Both provided - check if they refer to the same process
                    parent_id_by_ref = get_process_id_by_ref(str(parent_ref).strip())
                    parent_id_by_name = get_process_id_by_name(str(parent_name).strip())
                    
                    if parent_id_by_ref and parent_id_by_name:
                        if parent_id_by_ref != parent_id_by_name:
                            errors.append(ValidationError(
                                row=row_num,
                                field="Parent Ref.",
                                message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different processes",
                                error_code="PARENT_NAME_REF_MISMATCH"
                            ))
                            continue
                    elif not parent_id_by_ref and not parent_id_by_name:
                        # Neither exists
                        errors.append(ValidationError(
                            row=row_num,
                            field="Parent Ref.",
                            message=f"Parent process not found with reference '{parent_ref}' or name '{parent_name}'",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                    elif not parent_id_by_ref:
                        errors.append(ValidationError(
                            row=row_num,
                            field="Parent Ref.",
                            message=f"Parent process not found with reference '{parent_ref}'",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                    elif not parent_id_by_name:
                        errors.append(ValidationError(
                            row=row_num,
                            field="Parent Name",
                            message=f"Parent process not found with name '{parent_name}'",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                elif has_ref:
                    # Only ref provided - verify it exists
                    parent_id_by_ref = get_process_id_by_ref(str(parent_ref).strip())
                    if not parent_id_by_ref:
                        errors.append(ValidationError(
                            row=row_num,
                            field="Parent Ref.",
                            message=f"Parent process not found with reference '{parent_ref}'",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                elif has_name:
                    # Only name provided - verify it exists
                    parent_id_by_name = get_process_id_by_name(str(parent_name).strip())
                    if not parent_id_by_name:
                        errors.append(ValidationError(
                            row=row_num,
                            field="Parent Name",
                            message=f"Parent process not found with name '{parent_name}'",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                else:
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                # Lookups
                # Viewing (Name column)
                vid = get_viewing_id_by_name(row.get("BUDG Viewing"))
                if vid:
                    resolved["BUDG Viewing_ID"] = vid
                sid = get_lookup_id_by_name("status", row.get("BUDG Status"))
                if sid:
                    resolved["BUDG Status_ID"] = sid
                # Type (required) - auto-fill with first if empty
                type_id = get_lookup_id_by_name("process_type", row.get("Type"))
                if type_id is None:
                    type_id = get_first_type_id()
                if type_id:
                    resolved["Type_ID"] = type_id
                duration_type_value = row.get("Duration Type")
                if duration_type_value not in (None, ""):
                    dur_type_id = get_lookup_id_by_name("process_duration_type", duration_type_value)
                    if not dur_type_id:
                        errors.append(ValidationError(
                            row_num,
                            "Duration Type",
                            f"Duration Type '{duration_type_value}' not found",
                            "NOT_FOUND"
                        ))
                        continue
                    resolved["Duration Type_ID"] = dur_type_id
                # Lifecycle (required) - auto-fill with first if empty
                lifecycle_id = get_lookup_id_by_name("process_lifecycle_status", row.get("Lifecycle"))
                if lifecycle_id is None:
                    lifecycle_id = get_first_process_lifecycle_status_id()
                if lifecycle_id:
                    resolved["Lifecycle_ID"] = lifecycle_id
                class_id = get_lookup_id_by_name("process_class", row.get("Classification"))
                if class_id:
                    resolved["Classification_ID"] = class_id
                auto_id = get_lookup_id_by_name("process_automation", row.get("Automation"))
                if auto_id:
                    resolved["Automation_ID"] = auto_id
                # Step Type (required) - auto-fill with first if empty
                step_type_id = get_lookup_id_by_name("process_step_type", row.get("Step Type"))
                if step_type_id is None:
                    step_type_id = get_first_step_type_id()
                if step_type_id:
                    resolved["Step Type_ID"] = step_type_id
                # Governance Role (optional)
                gov_role_id = get_lookup_id_by_name("object_role", row.get("Governance Role"))
                if gov_role_id:
                    resolved["Governance Role_ID"] = gov_role_id
                # Include original fields as-is (Name, Ref., Descriptions, Parent Ref./Name for batch resolution, etc.)
                for k in [
                    "Name", "Ref.", "Description", "Input Description", "Output Description",
                    "Parent Ref.", "Parent Name"
                ]:
                    if k in row:
                        resolved[k] = row.get(k)
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(series, resolved, "Process", row_num, errors)
                
                # Add to tracking sets only after all validations pass
                template_names.add(name_lower)
                if ref_value:
                    ref_lower = ref_value.lower()
                    template_refs.add(ref_lower)
                
                data.append(resolved)

            elif upload_option == "Update Existing Items":
                def _has_val(v):
                    if v is None or (hasattr(v, '__len__') and not v): return False
                    s = str(v).strip()
                    return bool(s) and s.lower() not in ("nan", "")
                id_raw = row.get("ID")
                ref_raw = row.get("Ref.")
                name_raw = row.get("Name")
                id_by_id = None
                if _has_val(id_raw) and str(id_raw).strip().replace("-", "").isdigit():
                    try:
                        vid = int(float(str(id_raw).strip()))
                        if _exists_in_table("process", vid):
                            id_by_id = vid
                    except (ValueError, TypeError):
                        pass
                id_by_ref = get_process_id_by_ref(str(ref_raw).strip()) if _has_val(ref_raw) else None
                id_by_name = get_process_id_by_name(str(name_raw).strip()) if _has_val(name_raw) else None
                filled = sum(1 for v in (id_raw, ref_raw, name_raw) if _has_val(v))
                if filled == 0:
                    continue
                resolved_id = id_by_id or id_by_ref or id_by_name
                if resolved_id is None:
                    errors.append(ValidationError(row_num, "ID", "No process found for the provided identity (ID, Ref., or Name)", "NOT_FOUND"))
                    continue
                if filled >= 2:
                    ids = {x for x in (id_by_id, id_by_ref, id_by_name) if x is not None}
                    if len(ids) > 1:
                        errors.append(ValidationError(row_num, "ID", "ID, Ref. and Name refer to different processes", "IDENTITY_MISMATCH"))
                        continue
                resolved: Dict[str, Any] = {
                    "operation": "UPDATE",
                    "row_number": row_num,
                    "ID": resolved_id,
                }
                # Same resolution as insert, but all optional
                for perm_col in ["Create Permission", "Read Permission", "Update Permission", "Delete Permission", "Archive Permission"]:
                    val = parse_bool(row.get(perm_col))
                    if val is not None:
                        resolved[perm_col] = val
                if row.get("Duration") not in (None, ""):
                    try:
                        resolved["Duration"] = int(float(row.get("Duration")))
                    except Exception:
                        errors.append(ValidationError(row_num, "Duration", "Duration must be a number", "INVALID_VALUE"))
                        continue
                # Validate Parent Process - check if both ref and name are provided and match
                parent_ref = row.get("Parent Ref.")
                parent_name = row.get("Parent Name")
                has_ref = parent_ref and str(parent_ref).strip()
                has_name = parent_name and str(parent_name).strip()
                parent_in_file = False
                if has_ref and str(parent_ref).strip().lower() in refs_in_file:
                    parent_in_file = True
                if has_name and str(parent_name).strip().lower() in names_in_file:
                    parent_in_file = True
                
                if parent_in_file:
                    pass  # Java will resolve from batch; do not set Parent_ID
                elif has_ref and has_name:
                    # Both provided - check if they refer to the same process
                    parent_id_by_ref = get_process_id_by_ref(str(parent_ref).strip())
                    parent_id_by_name = get_process_id_by_name(str(parent_name).strip())
                    
                    if parent_id_by_ref and parent_id_by_name:
                        if parent_id_by_ref != parent_id_by_name:
                            errors.append(ValidationError(
                                row=row_num,
                                field="Parent Ref.",
                                message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different processes",
                                error_code="PARENT_NAME_REF_MISMATCH"
                            ))
                            continue
                    elif not parent_id_by_ref and not parent_id_by_name:
                        errors.append(ValidationError(
                            row=row_num,
                            field="Parent Ref.",
                            message=f"Parent process not found with reference '{parent_ref}' or name '{parent_name}'",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                    elif not parent_id_by_ref:
                        errors.append(ValidationError(
                            row=row_num,
                            field="Parent Ref.",
                            message=f"Parent process not found with reference '{parent_ref}'",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                    elif not parent_id_by_name:
                        errors.append(ValidationError(
                            row=row_num,
                            field="Parent Name",
                            message=f"Parent process not found with name '{parent_name}'",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                elif has_ref:
                    parent_id_by_ref = get_process_id_by_ref(str(parent_ref).strip())
                    if not parent_id_by_ref:
                        errors.append(ValidationError(
                            row=row_num,
                            field="Parent Ref.",
                            message=f"Parent process not found with reference '{parent_ref}'",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                elif has_name:
                    parent_id_by_name = get_process_id_by_name(str(parent_name).strip())
                    if not parent_id_by_name:
                        errors.append(ValidationError(
                            row=row_num,
                            field="Parent Name",
                            message=f"Parent process not found with name '{parent_name}'",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                else:
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                # Update-only: prevent circular parent and cross-private-segment parent
                obj_id = resolved.get("ID")
                parent_id_val = resolved.get("Parent_ID")
                if obj_id and parent_id_val and would_create_parent_cycle_by_entity:
                    if would_create_parent_cycle_by_entity(get_db_connection, "Process", obj_id, parent_id_val):
                        errors.append(ValidationError(
                            row_num,
                            "Parent Ref.",
                            "Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.",
                            "PARENT_CYCLE",
                        ))
                        continue
                if obj_id and parent_id_val and get_object_segment_id:
                    obj_seg = get_object_segment_id(obj_id, "Process")
                    parent_seg = get_object_segment_id(parent_id_val, "Process")
                    if obj_seg is not None and parent_seg is not None and obj_seg != -1 and parent_seg != -1:
                        if obj_seg != 1 and parent_seg != 1 and obj_seg != parent_seg:
                            errors.append(ValidationError(
                                row_num,
                                "Parent Ref.",
                                "Cannot set parent to an object in a different private segment.",
                                "PARENT_DIFFERENT_PRIVATE_SEGMENT",
                            ))
                            continue
                mappings = [
                    ("BUDG Viewing", "BUDG Viewing_ID", get_viewing_id_by_name),
                    ("BUDG Status", "BUDG Status_ID", lambda v: get_lookup_id_by_name("status", v)),
                    ("Type", "Type_ID", lambda v: get_lookup_id_by_name("process_type", v)),
                    ("Duration Type", "Duration Type_ID", lambda v: get_lookup_id_by_name("process_duration_type", v)),
                    ("Lifecycle", "Lifecycle_ID", lambda v: get_lookup_id_by_name("process_lifecycle_status", v)),
                    ("Classification", "Classification_ID", lambda v: get_lookup_id_by_name("process_class", v)),
                    ("Automation", "Automation_ID", lambda v: get_lookup_id_by_name("process_automation", v)),
                    ("Step Type", "Step Type_ID", lambda v: get_lookup_id_by_name("process_step_type", v)),
                ]
                duration_type_value = row.get("Duration Type")
                if duration_type_value not in (None, ""):
                    duration_type_id = get_lookup_id_by_name("process_duration_type", duration_type_value)
                    if not duration_type_id:
                        errors.append(ValidationError(
                            row_num,
                            "Duration Type",
                            f"Duration Type '{duration_type_value}' not found",
                            "NOT_FOUND"
                        ))
                        continue
                    resolved["Duration Type_ID"] = duration_type_id
                for src, out, fn in mappings:
                    if src == "Duration Type":
                        continue
                    vid = fn(row.get(src))
                    if vid:
                        resolved[out] = vid
                # Copy pass-through text fields if present (excluding Step Type as it's now resolved to ID)
                for k in ["Name", "Ref.", "Description", "Input Description", "Output Description"]:
                    if k in row and row.get(k) not in (None, ""):
                        resolved[k] = row.get(k)
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(series, resolved, "Process", row_num, errors)
                
                data.append(resolved)

            elif upload_option == "Remove Existing Items":
                if row.get("ID") in (None, ""):
                    errors.append(ValidationError(row_num, "ID", "ID is required for delete", "REQUIRED_FIELD_EMPTY"))
                    continue
                data.append({
                    "operation": "DELETE",
                    "row_number": row_num,
                    "ID": int(row.get("ID")) if str(row.get("ID")).strip().isdigit() else row.get("ID"),
                })
            else:
                errors.append(ValidationError(row_num, "operation", f"Unsupported operation: {upload_option}", "INVALID_OPERATION"))
        except Exception as e:
            logger.error("Error validating row %s: %s", row_num, e)
            errors.append(ValidationError(row_num, "row", f"Unexpected error: {e}", "UNEXPECTED_ERROR"))
    return errors, data


def apply_column_mappings(df: pd.DataFrame, mappings: Dict[str, str]) -> pd.DataFrame:
    """
    Reverse mapping received from Java: mappings = {excelColName: expectedFieldName}
    Rename DataFrame columns accordingly where possible.
    """
    # Build a renaming dict: if a column name exists in mappings keys, rename it to mappings[col]
    rename_dict = {}
    for col in df.columns:
        if col in mappings:
            rename_dict[col] = mappings[col]
    if rename_dict:
        df = df.rename(columns=rename_dict)
    return df


