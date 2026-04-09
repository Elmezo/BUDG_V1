"""
Org Unit Bulk Upload Validation Module
Validates Excel files for Org Unit bulk uploads and resolves lookups to IDs.
"""

from typing import List, Tuple, Dict, Any, Optional
import os
import sys
import logging
import pandas as pd
import pymysql

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Add utils directory to path for segment_validator import
sys.path.append(os.path.join(os.path.dirname(__file__), '..', '..', 'utils'))
try:
    from segment_validator import validate_segment_exists, validate_user_segment_access, get_segment_id_by_name, get_segment_name_by_id, get_object_segment_id
except ImportError:
    # Fallback if import fails
    validate_segment_exists = None
    validate_user_segment_access = None
    get_segment_id_by_name = None
    get_segment_name_by_id = None
    get_object_segment_id = None
try:
    from parent_hierarchy_validator import would_create_parent_cycle_by_entity
except ImportError:
    would_create_parent_cycle_by_entity = None

try:
    from custom_fields_validator import (
        add_custom_fields_to_validated_data,
        CUSTOM_FIELD_BLOCKING_ERROR_CODES,
    )
except ImportError:
    add_custom_fields_to_validated_data = None
    CUSTOM_FIELD_BLOCKING_ERROR_CODES = frozenset({
        "CUSTOM_FIELD_MANDATORY",
        "CUSTOM_FIELD_VALIDATION_ERROR",
        "CUSTOM_FIELD_VALIDATION_EXCEPTION",
    })
    logger.warning("Could not import custom_fields_validator")

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
    Return expected sheet name for Org. Unit bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Org. Unit"
    if upload_option == "Update Existing Items":
        return "Update Org. Unit"
    if upload_option == "Remove Existing Items":
        return "Delete Org. Unit"
    return None


def apply_column_mappings(df: pd.DataFrame, column_mappings: Optional[Dict[str, str]]) -> pd.DataFrame:
    """Apply column mappings to rename Excel columns to expected field names."""
    if not column_mappings:
        logger.info("No column mappings provided, skipping mapping step")
        return df
    
    logger.info(f"Applying column mappings. Received {len(column_mappings)} mappings")
    logger.info(f"DataFrame columns before mapping: {list(df.columns)}")
    
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
        logger.info(f"DataFrame columns after mapping: {list(df.columns)}")
    else:
        logger.warning("No column mappings could be applied")
    
    return df


def _is_effectively_empty_excel_row(row: pd.Series) -> bool:
    """Skip trailing blank rows Excel includes in the used range."""
    for v in row:
        if v is None:
            continue
        try:
            if pd.isna(v):
                continue
        except TypeError:
            pass
        s = str(v).strip()
        if s and s.lower() not in ("nan",):
            return False
    return True


def _row_has_blocking_cf_errors_since(errors: List[Any], start_idx: int, row_number: int) -> bool:
    for i in range(start_idx, len(errors)):
        e = errors[i]
        if hasattr(e, "dict"):
            d = e.dict()
        elif isinstance(e, dict):
            d = e
        else:
            continue
        try:
            r = int(d.get("row"))
        except (TypeError, ValueError):
            continue
        if r != row_number:
            continue
        c = str(d.get("error_code", "")).strip().upper()
        if c in CUSTOM_FIELD_BLOCKING_ERROR_CODES:
            return True
    return False


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
                cur.execute(f"SELECT ID FROM {table} WHERE LOWER(PrimaryName) = LOWER(%s) LIMIT 1", (str(primary_name).strip(),))
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


def get_user_id_by_email(email: Optional[str]) -> Optional[int]:
    if not email or not str(email).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM people WHERE LOWER(Email) = LOWER(%s) AND Deleted_date IS NULL LIMIT 1", (str(email).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_user_id_by_email error: %s", e)
        return None


def get_user_id_by_name(first_name: Optional[str], last_name: Optional[str]) -> Optional[int]:
    if not first_name or not last_name:
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT ID FROM people WHERE LOWER(First_Name) = LOWER(%s) AND LOWER(Last_Name) = LOWER(%s) AND Deleted_date IS NULL LIMIT 1",
                    (str(first_name).strip(), str(last_name).strip()),
                )
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_user_id_by_name error: %s", e)
        return None


def get_user_id_by_lan(lan_id: Optional[str]) -> Optional[int]:
    if not lan_id or not str(lan_id).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT p.ID FROM people p JOIN people_details pd ON p.ip_details = pd.id WHERE pd.lan_id = %s AND p.Deleted_date IS NULL LIMIT 1",
                    (str(lan_id).strip(),),
                )
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_user_id_by_lan error: %s", e)
        return None


def get_role_id_by_name(role_name: Optional[str]) -> Optional[int]:
    if not role_name or not str(role_name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM object_role WHERE LOWER(primaryname) = LOWER(%s) LIMIT 1", (str(role_name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_role_id_by_name error: %s", e)
        return None


def get_org_unit_id_by_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM org_unit WHERE LOWER(Name) = LOWER(%s) LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_org_unit_id_by_name error: %s", e)
        return None


def get_org_unit_id_by_ref(reference: Optional[str]) -> Optional[int]:
    if not reference or not str(reference).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM org_unit WHERE Reference = %s LIMIT 1", (str(reference).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_org_unit_id_by_ref error: %s", e)
        return None


def validate_and_resolve_org_unit(df: pd.DataFrame, upload_option: str, user_id: int, segment_mode: Optional[str] = None, segment: Optional[str] = None) -> Tuple[List[Dict[str, Any]], List[ValidationError]]:
    """
    Validate and resolve Org Unit records from DataFrame.
    Returns: (validated_data, errors)
    """
    validated_data = []
    errors = []
    
    operation = "INSERT"
    if upload_option == "Update Existing Items":
        operation = "UPDATE"
    elif upload_option == "Remove Existing Items":
        operation = "DELETE"

    if operation == "INSERT" and not df.empty:
        non_empty_idx = [i for i in df.index if not _is_effectively_empty_excel_row(df.loc[i])]
        df = df.loc[non_empty_idx] if non_empty_idx else df.iloc[0:0]

    # Names/refs in file — include parent columns so "parent in same file" is detected
    names_in_file = set()
    for col in ["Org Unit Name", "Name", "Parent Org Unit Name", "Parent Name"]:
        if col in df.columns:
            for v in df[col].dropna():
                s = str(v).strip()
                if s:
                    names_in_file.add(s.lower())
    refs_in_file = set()
    for col in ["Reference", "Parent Org Unit Reference", "Parent Ref."]:
        if col in df.columns:
            for v in df[col].dropna():
                s = str(v).strip()
                if s:
                    refs_in_file.add(s.lower())
    
    for idx, row in df.iterrows():
        row_number = idx + 2  # Excel row number (header is row 1)
        row_data = {"row_number": row_number, "operation": operation}
        
        try:
            if operation == "DELETE":
                # DELETE: Only need ID
                entity_id = row.get("Org Unit ID") or row.get("ID")
                if pd.isna(entity_id):
                    errors.append(ValidationError(row_number, "Org Unit ID", "Org Unit ID is required for deletion", "MISSING_REQUIRED"))
                    continue
                
                row_data["ID"] = int(entity_id)
                
                # Check if exists
                if not _exists_in_table("org_unit", row_data["ID"]):
                    errors.append(ValidationError(row_number, "Org Unit ID", f"Org Unit with ID {row_data['ID']} does not exist", "NOT_FOUND"))
                    continue
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    _eb = len(errors)
                    add_custom_fields_to_validated_data(row, row_data, "Org Unit", row_number, errors)
                    if _row_has_blocking_cf_errors_since(errors, _eb, row_number):
                        continue
                
                validated_data.append(row_data)
                
            elif operation == "UPDATE":
                # UPDATE: At least one of ID, Reference, Name required; resolve to single ID; if 2+ filled validate same
                def _has_val(v):
                    if v is None or (hasattr(v, '__len__') and not v): return False
                    if pd.isna(v): return False
                    s = str(v).strip()
                    return bool(s) and s.lower() not in ("nan", "")
                id_raw = row.get("Org Unit ID") or row.get("ID")
                ref_raw = row.get("Reference")
                name_raw = row.get("Org Unit Name") or row.get("Name")
                id_by_id = None
                if _has_val(id_raw):
                    try:
                        vid = int(float(str(id_raw).strip()))
                        if _exists_in_table("org_unit", vid):
                            id_by_id = vid
                    except (ValueError, TypeError):
                        pass
                id_by_ref = get_org_unit_id_by_ref(str(ref_raw).strip()) if _has_val(ref_raw) else None
                id_by_name = get_org_unit_id_by_name(str(name_raw).strip()) if _has_val(name_raw) else None
                filled = sum(1 for v in (id_raw, ref_raw, name_raw) if _has_val(v))
                if filled == 0:
                    errors.append(ValidationError(row_number, "Org Unit ID", "At least one of Org Unit ID, Reference, or Name is required for update", "MISSING_REQUIRED"))
                    continue
                resolved_id = id_by_id or id_by_ref or id_by_name
                if resolved_id is None:
                    errors.append(ValidationError(row_number, "Org Unit ID", "No org unit found for the provided identity (ID, Reference, or Name)", "NOT_FOUND"))
                    continue
                if filled >= 2:
                    ids = {x for x in (id_by_id, id_by_ref, id_by_name) if x is not None}
                    if len(ids) > 1:
                        errors.append(ValidationError(row_number, "Org Unit ID", "ID, Reference and Name refer to different org units", "IDENTITY_MISMATCH"))
                        continue
                row_data["ID"] = resolved_id
                
                # Name and Reference are optional for update (Java uses getStringOrDefault to keep existing when not provided)
                name = row.get("Org Unit Name") or row.get("Name")
                if not pd.isna(name) and str(name).strip():
                    row_data["Name"] = str(name).strip()
                reference = row.get("Reference")
                if not pd.isna(reference) and str(reference).strip():
                    row_data["Reference"] = str(reference).strip()
                
                # Description (optional)
                description = row.get("Description")
                if not pd.isna(description) and str(description).strip():
                    row_data["Description"] = str(description).strip()
                
                def _parent_ok_for_update(parent_id: int, field_label: str) -> bool:
                    obj_id = row_data.get("ID")
                    if obj_id and would_create_parent_cycle_by_entity:
                        if would_create_parent_cycle_by_entity(get_db_connection, "OrgUnit", obj_id, parent_id):
                            errors.append(ValidationError(
                                row_number,
                                field_label,
                                "Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.",
                                "PARENT_CYCLE",
                            ))
                            return False
                    if obj_id and get_object_segment_id:
                        obj_seg = get_object_segment_id(obj_id, "OrgUnit")
                        parent_seg = get_object_segment_id(parent_id, "OrgUnit")
                        if obj_seg is not None and parent_seg is not None and obj_seg != -1 and parent_seg != -1:
                            if obj_seg != 1 and parent_seg != 1 and obj_seg != parent_seg:
                                errors.append(ValidationError(
                                    row_number,
                                    field_label,
                                    "Cannot set parent to an object in a different private segment.",
                                    "PARENT_DIFFERENT_PRIVATE_SEGMENT",
                                ))
                                return False
                    return True

                # Parent resolution (aligned with INSERT; pass batch keys to Java; skip row on NOT_FOUND)
                parent_name = row.get("Parent Org Unit Name") or row.get("Parent Name")
                parent_ref = row.get("Parent Org Unit Reference") or row.get("Parent Ref.")
                has_pn = parent_name is not None and not pd.isna(parent_name) and str(parent_name).strip()
                has_pr = parent_ref is not None and not pd.isna(parent_ref) and str(parent_ref).strip()
                pn = str(parent_name).strip() if has_pn else None
                pr = str(parent_ref).strip() if has_pr else None
                pin = bool(pn and pn.lower() in names_in_file)
                pir = bool(pr and pr.lower() in refs_in_file)

                if has_pn and has_pr:
                    if pin and pir:
                        row_data["Parent Org Unit Name"] = pn
                        row_data["Parent Org Unit Reference"] = pr
                    elif not pin and not pir:
                        id_n = get_org_unit_id_by_name(pn)
                        id_r = get_org_unit_id_by_ref(pr)
                        if id_n and id_r and id_n != id_r:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Name",
                                f"Parent name '{pn}' and parent reference '{pr}' refer to different org units.",
                                "PARENT_NAME_REF_MISMATCH",
                            ))
                            continue
                        if not id_n:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Name",
                                f"Parent Org Unit '{pn}' not found",
                                "NOT_FOUND",
                            ))
                            continue
                        if not id_r:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Reference",
                                f"Parent Org Unit with reference '{pr}' not found",
                                "NOT_FOUND",
                            ))
                            continue
                        if not _parent_ok_for_update(id_n, "Parent Org Unit Name"):
                            continue
                        row_data["Parent_ID"] = id_n
                    elif pin and not pir:
                        row_data["Parent Org Unit Name"] = pn
                        row_data["Parent Org Unit Reference"] = pr
                        id_r = get_org_unit_id_by_ref(pr)
                        if not id_r:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Reference",
                                f"Parent Org Unit with reference '{pr}' not found",
                                "NOT_FOUND",
                            ))
                            continue
                    elif not pin and pir:
                        row_data["Parent Org Unit Reference"] = pr
                        id_n = get_org_unit_id_by_name(pn)
                        if not id_n:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Name",
                                f"Parent Org Unit '{pn}' not found",
                                "NOT_FOUND",
                            ))
                            continue
                        if not _parent_ok_for_update(id_n, "Parent Org Unit Name"):
                            continue
                        row_data["Parent_ID"] = id_n
                elif has_pn:
                    if pin:
                        row_data["Parent Org Unit Name"] = pn
                    else:
                        parent_id = get_org_unit_id_by_name(pn)
                        if parent_id:
                            if not _parent_ok_for_update(parent_id, "Parent Org Unit Name"):
                                continue
                            row_data["Parent_ID"] = parent_id
                        else:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Name",
                                f"Parent Org Unit '{pn}' not found",
                                "NOT_FOUND",
                            ))
                            continue
                elif has_pr:
                    if pir:
                        row_data["Parent Org Unit Reference"] = pr
                    else:
                        parent_id = get_org_unit_id_by_ref(pr)
                        if parent_id:
                            if not _parent_ok_for_update(parent_id, "Parent Org Unit Reference"):
                                continue
                            row_data["Parent_ID"] = parent_id
                        else:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Reference",
                                f"Parent Org Unit with reference '{pr}' not found",
                                "NOT_FOUND",
                            ))
                            continue

                # Status resolution
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_lookup_id_by_name("status", str(status_name).strip())
                    if status_id:
                        row_data["Status_ID"] = status_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Status", f"Status '{status_name}' not found", "NOT_FOUND"))
                        continue
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    _eb = len(errors)
                    add_custom_fields_to_validated_data(row, row_data, "Org Unit", row_number, errors)
                    if _row_has_blocking_cf_errors_since(errors, _eb, row_number):
                        continue
                
                validated_data.append(row_data)
                
            else:  # INSERT
                # Required fields
                name = row.get("Org Unit Name") or row.get("Name")
                if pd.isna(name) or not str(name).strip():
                    errors.append(ValidationError(row_number, "Org Unit Name", "Org Unit Name is required", "MISSING_REQUIRED"))
                    continue
                row_data["Name"] = str(name).strip()
                
                # Reference (required)
                reference = row.get("Reference")
                if pd.isna(reference) or not str(reference).strip():
                    errors.append(ValidationError(row_number, "Reference", "Reference is required", "MISSING_REQUIRED"))
                    continue
                row_data["Reference"] = str(reference).strip()
                
                # Description (optional)
                description = row.get("Description")
                if not pd.isna(description) and str(description).strip():
                    row_data["Description"] = str(description).strip()
                
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
                
                # Parent resolution — include parent columns in names_in_file/refs_in_file; pass keys to Java for batch lookup
                parent_name = row.get("Parent Org Unit Name") or row.get("Parent Name")
                parent_ref = row.get("Parent Org Unit Reference") or row.get("Parent Ref.")
                has_pn = parent_name is not None and not pd.isna(parent_name) and str(parent_name).strip()
                has_pr = parent_ref is not None and not pd.isna(parent_ref) and str(parent_ref).strip()
                pn = str(parent_name).strip() if has_pn else None
                pr = str(parent_ref).strip() if has_pr else None
                pin = bool(pn and pn.lower() in names_in_file)
                pir = bool(pr and pr.lower() in refs_in_file)

                if has_pn and has_pr:
                    if pin and pir:
                        row_data["Parent Org Unit Name"] = pn
                        row_data["Parent Org Unit Reference"] = pr
                    elif not pin and not pir:
                        id_n = get_org_unit_id_by_name(pn)
                        id_r = get_org_unit_id_by_ref(pr)
                        if id_n and id_r and id_n != id_r:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Name",
                                f"Parent name '{pn}' and parent reference '{pr}' refer to different org units.",
                                "PARENT_NAME_REF_MISMATCH",
                            ))
                            continue
                        if not id_n:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Name",
                                f"Parent Org Unit '{pn}' not found",
                                "NOT_FOUND",
                            ))
                            continue
                        if not id_r:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Reference",
                                f"Parent Org Unit with reference '{pr}' not found",
                                "NOT_FOUND",
                            ))
                            continue
                        row_data["Parent_ID"] = id_n
                    elif pin and not pir:
                        row_data["Parent Org Unit Name"] = pn
                        row_data["Parent Org Unit Reference"] = pr
                        id_r = get_org_unit_id_by_ref(pr)
                        if not id_r:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Reference",
                                f"Parent Org Unit with reference '{pr}' not found",
                                "NOT_FOUND",
                            ))
                            continue
                    elif not pin and pir:
                        row_data["Parent Org Unit Reference"] = pr
                        id_n = get_org_unit_id_by_name(pn)
                        if not id_n:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Name",
                                f"Parent Org Unit '{pn}' not found",
                                "NOT_FOUND",
                            ))
                            continue
                        row_data["Parent_ID"] = id_n
                elif has_pn:
                    if pin:
                        row_data["Parent Org Unit Name"] = pn
                    else:
                        parent_id = get_org_unit_id_by_name(pn)
                        if parent_id:
                            row_data["Parent_ID"] = parent_id
                        else:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Name",
                                f"Parent Org Unit '{pn}' not found",
                                "NOT_FOUND",
                            ))
                            continue
                elif has_pr:
                    if pir:
                        row_data["Parent Org Unit Reference"] = pr
                    else:
                        parent_id = get_org_unit_id_by_ref(pr)
                        if parent_id:
                            row_data["Parent_ID"] = parent_id
                        else:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Org Unit Reference",
                                f"Parent Org Unit with reference '{pr}' not found",
                                "NOT_FOUND",
                            ))
                            continue

                # Status resolution
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_lookup_id_by_name("status", str(status_name).strip())
                    if status_id:
                        row_data["Status_ID"] = status_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Status", f"Status '{status_name}' not found", "NOT_FOUND"))
                        continue
                
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
                    _eb = len(errors)
                    add_custom_fields_to_validated_data(row, row_data, "Org Unit", row_number, errors)
                    if _row_has_blocking_cf_errors_since(errors, _eb, row_number):
                        continue
                
                validated_data.append(row_data)
                
        except Exception as e:
            logger.error(f"Error processing row {row_number}: {e}")
            errors.append(ValidationError(row_number, "General", str(e), "PROCESSING_ERROR"))
    
    return validated_data, errors
