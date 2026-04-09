"""
Capability Bulk Upload Validation Module
Validates Excel files for Capability bulk uploads and resolves lookups to IDs.
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
    from segment_validator import (
        validate_segment_exists,
        validate_user_segment_access,
        get_segment_id_by_name,
        get_segment_name_by_id,
        get_object_segment_id,
    )
except ImportError:
    validate_segment_exists = None
    validate_user_segment_access = None
    get_segment_id_by_name = None
    get_segment_name_by_id = None
    get_object_segment_id = None
    logger.warning("Could not import segment_validator, segment validation will be skipped")

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


def _is_effectively_empty_excel_row(row: pd.Series) -> bool:
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


def _resolve_capability_target_segment_id(
    segment_mode: Optional[str],
    segment: Optional[str],
    segment_name: Optional[str],
) -> Optional[int]:
    if get_segment_id_by_name is None:
        return None
    mode = (segment_mode or "").strip().upper()
    sn = (segment_name or "").strip()
    sel = (segment or "").strip() if segment else ""

    if not mode:
        if sn:
            return get_segment_id_by_name(sn)
        return None
    if mode == "ENTERPRISE":
        if sn and sn.lower() != "enterprise":
            return get_segment_id_by_name(sn)
        return 1
    if mode == "MULTIPLE":
        if sn:
            return get_segment_id_by_name(sn)
        if sel:
            try:
                sid = int(sel.strip())
                return sid if sid > 0 else None
            except ValueError:
                return get_segment_id_by_name(sel)
        return None
    if mode == "SPECIFIC":
        if sn:
            return get_segment_id_by_name(sn)
        if sel:
            try:
                return int(sel.strip())
            except ValueError:
                return get_segment_id_by_name(sel)
        return None
    return None


def capability_ref_exists(ref: str) -> bool:
    if not ref or not str(ref).strip():
        return False
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT ID FROM capability WHERE RefNumber = %s AND DeletedDatetime IS NULL LIMIT 1",
                    (str(ref).strip(),),
                )
                return cur.fetchone() is not None
    except Exception as e:
        logger.warning("capability_ref_exists: %s", e)
        return False


def get_sheet_name(upload_option: str) -> Optional[str]:
    """
    Return expected sheet name for Capability bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Capability"
    if upload_option == "Update Existing Items":
        return "Update Capability"
    if upload_option == "Remove Existing Items":
        return "Delete Capability"
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


def get_first_id_from_table(table: str, id_col: str = "ID") -> Optional[int]:
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


def get_first_capability_classification_id() -> Optional[int]:
    return get_first_id_from_table("capability_classification")


def get_first_capability_lifecycle_id() -> Optional[int]:
    return get_first_id_from_table("capability_lifecyle")


def get_first_capability_type_id() -> Optional[int]:
    return get_first_id_from_table("capability_type")


def get_capability_id_by_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM capability WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_capability_id_by_name error: %s", e)
        return None


def get_capability_id_by_ref(ref_number: Optional[str]) -> Optional[int]:
    if not ref_number or not str(ref_number).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM capability WHERE RefNumber = %s AND DeletedDatetime IS NULL LIMIT 1", (str(ref_number).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_capability_id_by_ref error: %s", e)
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


def validate_and_resolve_capability(df: pd.DataFrame, upload_option: str, user_id: int, segment_mode: Optional[str] = None, segment: Optional[str] = None) -> Tuple[List[Dict[str, Any]], List[ValidationError]]:
    """
    Validate and resolve Capability records from DataFrame.
    Returns: (validated_data, errors)
    """
    validated_data = []
    errors = []
    
    operation = "INSERT"
    if upload_option == "Update Existing Items":
        operation = "UPDATE"
    elif upload_option == "Remove Existing Items":
        operation = "DELETE"

    insert_seen_refs: set = set()

    if operation == "INSERT" and not df.empty:
        non_empty_idx = [i for i in df.index if not _is_effectively_empty_excel_row(df.loc[i])]
        df = df.loc[non_empty_idx] if non_empty_idx else df.iloc[0:0]
    
    # Include parent columns so batch parent resolution works; child ref columns for duplicate detection
    names_in_file = set()
    refs_in_file = set()
    for col in ["Capability Name", "PrimaryName", "Parent Capability"]:
        if col in df.columns:
            for v in df[col].dropna():
                s = str(v).strip()
                if s:
                    names_in_file.add(s.lower())
    for col in ["RefNumber", "Reference", "Ref.", "Parent Ref."]:
        if col in df.columns:
            for v in df[col].dropna():
                s = str(v).strip()
                if s:
                    refs_in_file.add(s.lower())
    
    for idx, row in df.iterrows():
        row_number = idx + 2
        row_data = {"row_number": row_number, "operation": operation}
        
        try:
            if operation == "DELETE":
                cap_id = row.get("Capability ID") or row.get("ID")
                if pd.isna(cap_id):
                    errors.append(ValidationError(row_number, "Capability ID", "Capability ID is required for deletion", "MISSING_REQUIRED"))
                    continue
                
                row_data["ID"] = int(cap_id)
                
                if not _exists_in_table("capability", row_data["ID"]):
                    errors.append(ValidationError(row_number, "Capability ID", f"Capability with ID {row_data['ID']} does not exist", "NOT_FOUND"))
                    continue
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    _eb = len(errors)
                    add_custom_fields_to_validated_data(row, row_data, "Capability", row_number, errors)
                    if _row_has_blocking_cf_errors_since(errors, _eb, row_number):
                        continue
                
                validated_data.append(row_data)
                
            elif operation == "UPDATE":
                def _has_val(v):
                    if v is None or (hasattr(v, '__len__') and not v): return False
                    if pd.isna(v): return False
                    s = str(v).strip()
                    return bool(s) and s.lower() not in ("nan", "")
                id_raw = row.get("Capability ID") or row.get("ID")
                ref_raw = row.get("RefNumber") or row.get("Reference")
                name_raw = row.get("Capability Name") or row.get("PrimaryName")
                id_by_id = None
                if _has_val(id_raw):
                    try:
                        vid = int(float(str(id_raw).strip()))
                        if _exists_in_table("capability", vid):
                            id_by_id = vid
                    except (ValueError, TypeError):
                        pass
                id_by_ref = get_capability_id_by_ref(str(ref_raw).strip()) if _has_val(ref_raw) else None
                id_by_name = get_capability_id_by_name(str(name_raw).strip()) if _has_val(name_raw) else None
                filled = sum(1 for v in (id_raw, ref_raw, name_raw) if _has_val(v))
                if filled == 0:
                    errors.append(ValidationError(row_number, "Capability ID", "At least one of Capability ID, Reference, or Capability Name is required for update", "MISSING_REQUIRED"))
                    continue
                resolved_id = id_by_id or id_by_ref or id_by_name
                if resolved_id is None:
                    errors.append(ValidationError(row_number, "Capability ID", "No capability found for the provided identity (ID, Reference, or Capability Name)", "NOT_FOUND"))
                    continue
                if filled >= 2:
                    ids = {x for x in (id_by_id, id_by_ref, id_by_name) if x is not None}
                    if len(ids) > 1:
                        errors.append(ValidationError(row_number, "Capability ID", "ID, Reference and Capability Name refer to different capabilities", "IDENTITY_MISMATCH"))
                        continue
                row_data["ID"] = resolved_id
                
                # Capability Name and Definition are optional for update (Java uses existing values when omitted)
                primary_name = row.get("Capability Name") or row.get("PrimaryName")
                if not pd.isna(primary_name) and str(primary_name).strip():
                    row_data["PrimaryName"] = str(primary_name).strip()
                
                description = row.get("Capability Definition") or row.get("Description")
                if not pd.isna(description) and str(description).strip():
                    row_data["Description"] = str(description).strip()
                
                ref_number = row.get("RefNumber")
                if not pd.isna(ref_number) and str(ref_number).strip():
                    row_data["RefNumber"] = str(ref_number).strip()
                
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_lookup_id_by_name("status", str(status_name).strip())
                    if status_id:
                        row_data["Status_ID"] = status_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Status", f"Status '{status_name}' not found", "NOT_FOUND"))
                
                lifecycle_name = row.get("Lifecycle")
                if not pd.isna(lifecycle_name) and str(lifecycle_name).strip():
                    lifecycle_id = get_lookup_id_by_name("capability_lifecyle", str(lifecycle_name).strip())
                    if lifecycle_id:
                        row_data["Lifecycle_ID"] = lifecycle_id
                    else:
                        errors.append(ValidationError(row_number, "Lifecycle", f"Lifecycle '{lifecycle_name}' not found", "NOT_FOUND"))
                
                viewing_name = row.get("BUDG Viewing")
                if not pd.isna(viewing_name) and str(viewing_name).strip():
                    viewing_id = get_viewing_id_by_name(str(viewing_name).strip())
                    if viewing_id:
                        row_data["Is_Public_ID"] = viewing_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Viewing", f"Viewing '{viewing_name}' not found", "NOT_FOUND"))
                
                # Classification (required - use first value if empty)
                classification_name = row.get("Classification")
                classification_id = None
                if pd.isna(classification_name) or not str(classification_name).strip():
                    # Use first classification if empty
                    classification_id = get_first_capability_classification_id()
                    if not classification_id:
                        errors.append(ValidationError(row_number, "Classification", "Classification is required and no default available", "MISSING_REQUIRED"))
                        continue
                else:
                    classification_id = get_lookup_id_by_name("capability_classification", str(classification_name).strip())
                    if not classification_id:
                        errors.append(ValidationError(row_number, "Classification", f"Classification '{classification_name}' not found", "NOT_FOUND"))
                        continue
                row_data["Classification_ID"] = classification_id
                
                # Lifecycle (required - use first value if empty)
                lifecycle_name = row.get("Lifecycle")
                lifecycle_id = None
                if pd.isna(lifecycle_name) or not str(lifecycle_name).strip():
                    # Use first lifecycle if empty
                    lifecycle_id = get_first_capability_lifecycle_id()
                    if not lifecycle_id:
                        errors.append(ValidationError(row_number, "Lifecycle", "Lifecycle is required and no default available", "MISSING_REQUIRED"))
                        continue
                else:
                    lifecycle_id = get_lookup_id_by_name("capability_lifecyle", str(lifecycle_name).strip())
                    if not lifecycle_id:
                        errors.append(ValidationError(row_number, "Lifecycle", f"Lifecycle '{lifecycle_name}' not found", "NOT_FOUND"))
                        continue
                row_data["Lifecycle_ID"] = lifecycle_id
                
                # Capability Type (required - use first value if empty)
                type_name = row.get("Capability Type")
                type_id = None
                if pd.isna(type_name) or not str(type_name).strip():
                    # Use first type if empty
                    type_id = get_first_capability_type_id()
                    if not type_id:
                        errors.append(ValidationError(row_number, "Capability Type", "Capability Type is required and no default available", "MISSING_REQUIRED"))
                        continue
                else:
                    type_id = get_lookup_id_by_name("capability_type", str(type_name).strip())
                    if not type_id:
                        errors.append(ValidationError(row_number, "Capability Type", f"Capability Type '{type_name}' not found", "NOT_FOUND"))
                        continue
                row_data["Capability_Type_ID"] = type_id
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "Capability", row_number, errors)
                
                validated_data.append(row_data)
                
            else:  # INSERT
                # Collect all missing mandatory field errors before continuing
                has_mandatory_errors = False
                
                # Required fields
                primary_name = row.get("Capability Name") or row.get("PrimaryName")
                if pd.isna(primary_name) or not str(primary_name).strip():
                    errors.append(ValidationError(row_number, "Capability Name", "Capability Name is required", "MISSING_REQUIRED"))
                    has_mandatory_errors = True
                else:
                    row_data["PrimaryName"] = str(primary_name).strip()
                
                # Capability Definition/Description (required)
                description = row.get("Capability Definition") or row.get("Description")
                if pd.isna(description) or not str(description).strip():
                    errors.append(ValidationError(row_number, "Capability Definition", "Capability Definition is required", "MISSING_REQUIRED"))
                    has_mandatory_errors = True
                else:
                    row_data["Description"] = str(description).strip()
                
                # Only continue to next row if there are mandatory field errors
                if has_mandatory_errors:
                    continue

                segment_value = row.get("Segment")
                segment_name = str(segment_value).strip() if segment_value and not pd.isna(segment_value) else None
                target_segment_id = _resolve_capability_target_segment_id(segment_mode, segment, segment_name)

                parent_ref = row.get("Parent Ref.")
                parent_name = row.get("Parent Capability")
                has_pr = parent_ref is not None and not pd.isna(parent_ref) and str(parent_ref).strip()
                has_pn = parent_name is not None and not pd.isna(parent_name) and str(parent_name).strip()
                pr = str(parent_ref).strip() if has_pr else None
                pn = str(parent_name).strip() if has_pn else None
                pir = bool(pr and pr.lower() in refs_in_file)
                pin = bool(pn and pn.lower() in names_in_file)

                explicit_parent_raw = row.get("Parent ID") or row.get("Parent_ID") or row.get("Parent Capability ID")
                explicit_parent_id = None
                if explicit_parent_raw is not None and not pd.isna(explicit_parent_raw) and str(explicit_parent_raw).strip():
                    try:
                        explicit_parent_id = int(float(str(explicit_parent_raw).strip()))
                    except (ValueError, TypeError):
                        explicit_parent_id = None

                resolved_parent_id = None

                if has_pr and has_pn:
                    if pir and pin:
                        row_data["Parent Ref."] = pr
                        row_data["Parent Capability"] = pn
                    elif not pir and not pin:
                        id_r = get_capability_id_by_ref(pr)
                        id_n = get_capability_id_by_name(pn)
                        if id_r and id_n and id_r != id_n:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Ref.",
                                f"Parent reference '{pr}' and parent name '{pn}' refer to different capabilities",
                                "PARENT_NAME_REF_MISMATCH",
                            ))
                            continue
                        if not id_r:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Ref.",
                                f"Parent capability not found with reference: {pr}",
                                "NOT_FOUND",
                            ))
                            continue
                        if not id_n:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Capability",
                                f"Parent capability not found with name: {pn}",
                                "NOT_FOUND",
                            ))
                            continue
                        resolved_parent_id = id_r
                    elif pir and not pin:
                        row_data["Parent Ref."] = pr
                        row_data["Parent Capability"] = pn
                        id_n = get_capability_id_by_name(pn)
                        if not id_n:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Capability",
                                f"Parent capability not found with name: {pn}",
                                "NOT_FOUND",
                            ))
                            continue
                    elif not pir and pin:
                        row_data["Parent Ref."] = pr
                        row_data["Parent Capability"] = pn
                        id_r = get_capability_id_by_ref(pr)
                        if not id_r:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Ref.",
                                f"Parent capability not found with reference: {pr}",
                                "NOT_FOUND",
                            ))
                            continue
                elif has_pr:
                    if pir:
                        row_data["Parent Ref."] = pr
                    else:
                        resolved_parent_id = get_capability_id_by_ref(pr)
                        if not resolved_parent_id:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Ref.",
                                f"Parent capability not found with reference: {pr}",
                                "NOT_FOUND",
                            ))
                            continue
                elif has_pn:
                    if pin:
                        row_data["Parent Capability"] = pn
                    else:
                        resolved_parent_id = get_capability_id_by_name(pn)
                        if not resolved_parent_id:
                            errors.append(ValidationError(
                                row_number,
                                "Parent Capability",
                                f"Parent capability not found with name: {pn}",
                                "NOT_FOUND",
                            ))
                            continue

                if explicit_parent_id is not None:
                    if not _exists_in_table("capability", explicit_parent_id):
                        errors.append(ValidationError(
                            row_number,
                            "Parent ID",
                            f"Parent capability with ID {explicit_parent_id} does not exist",
                            "NOT_FOUND",
                        ))
                        continue
                    if resolved_parent_id is not None and resolved_parent_id != explicit_parent_id:
                        errors.append(ValidationError(
                            row_number,
                            "Parent ID",
                            "Parent ID does not match the parent resolved from Parent Ref. / Parent Capability.",
                            "PARENT_ID_MISMATCH",
                        ))
                        continue
                    row_data["Parent_ID"] = explicit_parent_id
                    pid_seg = explicit_parent_id
                    if get_object_segment_id:
                        pseg = get_object_segment_id(pid_seg, "Capability")
                        cseg = target_segment_id if target_segment_id is not None else 1
                        if pseg is not None and pseg != -1:
                            if cseg != 1 and pseg != 1 and cseg != pseg:
                                errors.append(ValidationError(
                                    row_number,
                                    "Parent Capability",
                                    "Cannot set parent to a Capability in a different private segment.",
                                    "PARENT_DIFFERENT_PRIVATE_SEGMENT",
                                ))
                                continue
                elif resolved_parent_id is not None:
                    row_data["Parent_ID"] = resolved_parent_id
                    if get_object_segment_id:
                        pseg = get_object_segment_id(resolved_parent_id, "Capability")
                        cseg = target_segment_id if target_segment_id is not None else 1
                        if pseg is not None and pseg != -1:
                            if cseg != 1 and pseg != 1 and cseg != pseg:
                                errors.append(ValidationError(
                                    row_number,
                                    "Parent Capability",
                                    "Cannot set parent to a Capability in a different private segment.",
                                    "PARENT_DIFFERENT_PRIVATE_SEGMENT",
                                ))
                                continue
                
                # Segment validation (Rules A, B, C) - only for INSERT operations
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
                
                ref_number = row.get("RefNumber") or row.get("Reference") or row.get("Ref.")
                if ref_number is not None and not pd.isna(ref_number) and str(ref_number).strip():
                    rfv = str(ref_number).strip()
                    row_data["RefNumber"] = rfv
                    rkl = rfv.lower()
                    if rkl in insert_seen_refs:
                        errors.append(ValidationError(
                            row_number,
                            "Reference",
                            f"Duplicate reference '{rfv}' in this file.",
                            "DUPLICATE_REF_IN_FILE",
                        ))
                        continue
                    if capability_ref_exists(rfv):
                        errors.append(ValidationError(
                            row_number,
                            "Reference",
                            f"Reference '{rfv}' is already used by another capability.",
                            "DUPLICATE_REF",
                        ))
                        continue
                
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_lookup_id_by_name("status", str(status_name).strip())
                    if status_id:
                        row_data["Status_ID"] = status_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Status", f"Status '{status_name}' not found", "NOT_FOUND"))
                
                lifecycle_name = row.get("Lifecycle")
                if not pd.isna(lifecycle_name) and str(lifecycle_name).strip():
                    lifecycle_id = get_lookup_id_by_name("capability_lifecyle", str(lifecycle_name).strip())
                    if lifecycle_id:
                        row_data["Lifecycle_ID"] = lifecycle_id
                    else:
                        errors.append(ValidationError(row_number, "Lifecycle", f"Lifecycle '{lifecycle_name}' not found", "NOT_FOUND"))
                
                viewing_name = row.get("BUDG Viewing")
                if not pd.isna(viewing_name) and str(viewing_name).strip():
                    viewing_id = get_viewing_id_by_name(str(viewing_name).strip())
                    if viewing_id:
                        row_data["Is_Public_ID"] = viewing_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Viewing", f"Viewing '{viewing_name}' not found", "NOT_FOUND"))
                
                # Classification (required - use first value if empty)
                classification_name = row.get("Classification")
                classification_id = None
                if pd.isna(classification_name) or not str(classification_name).strip():
                    # Use first classification if empty
                    classification_id = get_first_capability_classification_id()
                    if not classification_id:
                        errors.append(ValidationError(row_number, "Classification", "Classification is required and no default available", "MISSING_REQUIRED"))
                        continue
                else:
                    classification_id = get_lookup_id_by_name("capability_classification", str(classification_name).strip())
                    if not classification_id:
                        errors.append(ValidationError(row_number, "Classification", f"Classification '{classification_name}' not found", "NOT_FOUND"))
                        continue
                row_data["Classification_ID"] = classification_id
                
                # Lifecycle (required - use first value if empty)
                lifecycle_name = row.get("Lifecycle")
                lifecycle_id = None
                if pd.isna(lifecycle_name) or not str(lifecycle_name).strip():
                    # Use first lifecycle if empty
                    lifecycle_id = get_first_capability_lifecycle_id()
                    if not lifecycle_id:
                        errors.append(ValidationError(row_number, "Lifecycle", "Lifecycle is required and no default available", "MISSING_REQUIRED"))
                        continue
                else:
                    lifecycle_id = get_lookup_id_by_name("capability_lifecyle", str(lifecycle_name).strip())
                    if not lifecycle_id:
                        errors.append(ValidationError(row_number, "Lifecycle", f"Lifecycle '{lifecycle_name}' not found", "NOT_FOUND"))
                        continue
                row_data["Lifecycle_ID"] = lifecycle_id
                
                # Capability Type (required - use first value if empty)
                type_name = row.get("Capability Type")
                type_id = None
                if pd.isna(type_name) or not str(type_name).strip():
                    # Use first type if empty
                    type_id = get_first_capability_type_id()
                    if not type_id:
                        errors.append(ValidationError(row_number, "Capability Type", "Capability Type is required and no default available", "MISSING_REQUIRED"))
                        continue
                else:
                    type_id = get_lookup_id_by_name("capability_type", str(type_name).strip())
                    if not type_id:
                        errors.append(ValidationError(row_number, "Capability Type", f"Capability Type '{type_name}' not found", "NOT_FOUND"))
                        continue
                row_data["Capability_Type_ID"] = type_id
                
                role_name = row.get("Governance Role")
                if not pd.isna(role_name) and str(role_name).strip():
                    role_id = get_role_id_by_name(str(role_name).strip())
                    if role_id:
                        row_data["Governance Role_ID"] = role_id
                    else:
                        logger.warning(f"Row {row_number}: Governance Role '{role_name}' not found")
                
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
                    add_custom_fields_to_validated_data(row, row_data, "Capability", row_number, errors)
                    if _row_has_blocking_cf_errors_since(errors, _eb, row_number):
                        continue

                rn = row_data.get("RefNumber")
                if rn:
                    insert_seen_refs.add(str(rn).strip().lower())
                
                validated_data.append(row_data)
                
        except Exception as e:
            logger.error(f"Error processing row {row_number}: {e}")
            errors.append(ValidationError(row_number, "General", str(e), "PROCESSING_ERROR"))
    
    return validated_data, errors

