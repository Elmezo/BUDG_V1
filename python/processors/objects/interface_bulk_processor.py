"""
Interface Bulk Upload Validation Module
Validates Excel files for Interface bulk uploads and resolves lookups to IDs.
"""

from typing import List, Tuple, Dict, Any, Optional
import math
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
    logger.warning("Could not import segment_validator, segment validation will be skipped")
    validate_segment_exists = None
    validate_user_segment_access = None
    get_segment_id_by_name = None
    get_segment_name_by_id = None
    get_object_segment_id = None

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

try:
    from bulk_segment_name_utils import (
        duplicate_name_in_facet_segment,
        resolve_effective_segment_id_for_duplicate_check,
    )
except ImportError:
    duplicate_name_in_facet_segment = None
    resolve_effective_segment_id_for_duplicate_check = None

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
    Return expected sheet name for Interface bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Interface"
    if upload_option == "Update Existing Items":
        return "Update Interface"
    if upload_option == "Remove Existing Items":
        return "Delete Interface"
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
    """True if any new error since start_idx is a blocking custom-field error for this Excel row."""
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


def _exists_in_table(table: str, id_value: int, id_col: str = "id") -> bool:
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT COUNT(*) FROM {table} WHERE {id_col} = %s", (id_value,))
                return cur.fetchone()[0] > 0
    except Exception as e:
        logger.warning("exists_in_table error: %s", e)
        return False


def _get_interface_id_from_row(row: Any) -> Optional[Any]:
    """Return first non-empty value from Interface ID / ID column, matching by normalized name (strip + lower)."""
    # Build normalized column name -> original column name (prefer "interface id" then "id")
    accepted_normalized = ("interface id", "id")
    col_by_norm = {}
    if hasattr(row, "index"):
        for k in row.index:
            norm = str(k).strip().lower()
            if norm in accepted_normalized and norm not in col_by_norm:
                col_by_norm[norm] = k
    for norm in accepted_normalized:
        if norm not in col_by_norm:
            continue
        key = col_by_norm[norm]
        val = row[key] if hasattr(row, "__getitem__") else row.get(key)
        if val is None or pd.isna(val):
            continue
        if isinstance(val, int):
            return val
        if isinstance(val, float) and not math.isnan(val):
            return int(val)
        if str(val).strip() != "":
            return val
    return None


def get_interface_name_by_id(interface_id: int) -> Optional[str]:
    """Get Interface name by ID"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT Name FROM interface WHERE id = %s LIMIT 1", (interface_id,))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_interface_name_by_id error: %s", e)
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
        logger.warning("get_lookup_id_by_name error: %s", e)
        return None


def get_viewing_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("viewing", "Name", name)


def get_status_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("status", "primaryname", name)


def get_interface_lifecycle_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("interface_lifecycle", "Name", name)


def get_interface_type_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("interface_type", "Name", name)


def get_interface_classification_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("interface_classification", "Name", name)


def get_interface_transfer_method_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("interface_transfer", "Name", name)


def get_interface_transfer_format_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("interface_transfer_format", "Name", name)


def get_interface_automation_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("interface_automation", "Name", name)


def get_interface_frequency_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("interface_frequency", "Name", name)


def get_system_id_by_short_name(name: Optional[str]) -> Optional[int]:
    """Get system ID by Short Name"""
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM system WHERE LOWER(Name) = LOWER(%s) LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_system_id_by_short_name error: %s", e)
        return None


def get_system_id_if_exists(raw_id: Any) -> Optional[int]:
    """ENV export often has Source_systemID / Target_systemID numeric columns instead of Short Name."""
    if raw_id is None or (isinstance(raw_id, float) and pd.isna(raw_id)):
        return None
    try:
        i = int(float(str(raw_id).strip()))
    except (ValueError, TypeError):
        return None
    if i <= 0:
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM system WHERE id = %s LIMIT 1", (i,))
                row = cur.fetchone()
                return int(row[0]) if row else None
    except Exception as e:
        logger.warning("get_system_id_if_exists error: %s", e)
        return None


def _resolve_source_target_system_ids(row: pd.Series) -> Tuple[Optional[int], Optional[int], Optional[str]]:
    """
    Returns (source_id, target_id, error_field_or_none).
    Prefers Short Name when provided; otherwise accepts Source_systemID / Target_systemID style columns.
    """
    src_keys = ("Source System Short Name", "Source_systemID", "Source System ID", "Source_System_ID")
    tgt_keys = ("Target System Short Name", "Target_systemID", "Target System ID", "Target_System_ID")

    source_system_name = row.get("Source System Short Name")
    has_src_name = source_system_name is not None and not pd.isna(source_system_name) and str(source_system_name).strip()
    source_system_id = None
    if has_src_name:
        source_system_id = get_system_id_by_short_name(str(source_system_name).strip())
        if not source_system_id:
            return None, None, "Source System Short Name"
    else:
        for key in src_keys[1:]:
            source_system_id = get_system_id_if_exists(row.get(key))
            if source_system_id:
                break
        if not source_system_id:
            return None, None, "Source System Short Name"

    target_system_name = row.get("Target System Short Name")
    has_tgt_name = target_system_name is not None and not pd.isna(target_system_name) and str(target_system_name).strip()
    target_system_id = None
    if has_tgt_name:
        target_system_id = get_system_id_by_short_name(str(target_system_name).strip())
        if not target_system_id:
            return source_system_id, None, "Target System Short Name"
    else:
        for key in tgt_keys[1:]:
            target_system_id = get_system_id_if_exists(row.get(key))
            if target_system_id:
                break
        if not target_system_id:
            return source_system_id, None, "Target System Short Name"

    return source_system_id, target_system_id, None


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


def get_first_interface_lifecycle_id() -> Optional[int]:
    return get_first_id_from_table("interface_lifecycle")


def get_first_interface_automation_id() -> Optional[int]:
    return get_first_id_from_table("interface_automation")


def get_role_id_by_name(role_name: Optional[str]) -> Optional[int]:
    """Resolve object_role.id by primaryname scoped to the Interface module."""
    if not role_name or not str(role_name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT id FROM module WHERE LOWER(primaryname) = LOWER(%s) LIMIT 1",
                    ("Interface",),
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


def check_duplicate_interface_name(
    interface_name: str,
    exclude_id: Optional[int] = None,
    segment_id: Optional[int] = None,
) -> bool:
    """True if the same interface name exists in the target segment (SystemInterface)."""
    if duplicate_name_in_facet_segment is None:
        try:
            with get_db_connection() as conn:
                with conn.cursor() as cur:
                    if exclude_id:
                        cur.execute(
                            "SELECT COUNT(*) FROM interface WHERE LOWER(Name) = LOWER(%s) AND id != %s AND Deleted_datetime IS NULL",
                            (interface_name, exclude_id),
                        )
                    else:
                        cur.execute(
                            "SELECT COUNT(*) FROM interface WHERE LOWER(Name) = LOWER(%s) AND Deleted_datetime IS NULL",
                            (interface_name,),
                        )
                    count = cur.fetchone()[0]
                    return count > 0
        except Exception as e:
            logger.warning("check_duplicate_interface_name error: %s", e)
            return False
    sid = segment_id if segment_id is not None else 1
    return duplicate_name_in_facet_segment("Interface", sid, interface_name, exclude_id)


def get_interface_id_by_ref(ref_number: str) -> Optional[int]:
    """Get Interface ID by reference number"""
    if not ref_number or not str(ref_number).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM interface WHERE Ref_number = %s AND Deleted_datetime IS NULL LIMIT 1", (str(ref_number).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_interface_id_by_ref error: %s", e)
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


def validate_and_resolve_interface(df: pd.DataFrame, upload_option: str, user_id: int, segment_mode: Optional[str] = None, segment: Optional[str] = None) -> Tuple[List[Dict[str, Any]], List[ValidationError]]:
    """
    Validate and resolve Interface records from DataFrame.
    Returns: (validated_data, errors)
    """
    # Strip whitespace from column names so lookups (e.g. "ID", "Interface Name") are robust
    df = df.copy()
    df.columns = [str(c).strip() for c in df.columns]
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

    # Track names and refs within the template to detect duplicates (for INSERT only)
    template_names = set()  # Track names within this template
    template_refs = set()   # Track refs within this template
    
    for idx, row in df.iterrows():
        row_number = idx + 2
        row_data = {"row_number": row_number, "operation": operation}
        
        try:
            if operation == "DELETE":
                interface_id_val = _get_interface_id_from_row(row)
                if interface_id_val is None:
                    errors.append(ValidationError(row_number, "ID", 
                        "Required field missing: The 'ID' or 'Interface ID' field is required for deletion and cannot be empty. Please provide the Interface ID to delete.",
                        "MISSING_REQUIRED"))
                    continue
                
                row_data["ID"] = int(interface_id_val)
                
                if not _exists_in_table("interface", row_data["ID"]):
                    interface_name = get_interface_name_by_id(row_data["ID"])
                    if interface_name:
                        errors.append(ValidationError(row_number, "ID", 
                            f"Cannot delete Interface: Interface with ID {row_data['ID']} (Name: '{interface_name}') does not exist in the database. Please verify the ID is correct and the Interface has not already been deleted.",
                            "NOT_FOUND"))
                    else:
                        errors.append(ValidationError(row_number, "ID", 
                            f"Cannot delete Interface: Interface with ID {row_data['ID']} does not exist in the database. Please verify the ID is correct and the Interface has not already been deleted.",
                            "NOT_FOUND"))
                    continue
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    _eb = len(errors)
                    add_custom_fields_to_validated_data(row, row_data, "Interface", row_number, errors)
                    if _row_has_blocking_cf_errors_since(errors, _eb, row_number):
                        continue

                validated_data.append(row_data)
                
            elif operation == "UPDATE":
                interface_id_val = _get_interface_id_from_row(row)
                if interface_id_val is None:
                    errors.append(ValidationError(row_number, "ID", 
                        "Required field missing: The 'ID' or 'Interface ID' field is required for update and cannot be empty. Please provide the Interface ID to update.",
                        "MISSING_REQUIRED"))
                    continue
                
                resolved_id = int(interface_id_val)
                row_data["Interface ID"] = resolved_id
                
                if not _exists_in_table("interface", resolved_id):
                    interface_name = get_interface_name_by_id(resolved_id)
                    if interface_name:
                        errors.append(ValidationError(row_number, "ID", 
                            f"Cannot update Interface: Interface with ID {resolved_id} (Name: '{interface_name}') does not exist in the database. Please verify the ID is correct.",
                            "NOT_FOUND"))
                    else:
                        errors.append(ValidationError(row_number, "ID", 
                            f"Cannot update Interface: Interface with ID {resolved_id} does not exist in the database. Please verify the ID is correct.",
                            "NOT_FOUND"))
                    continue
                
                # Optional on update: Interface Name (only set when provided)
                interface_name = row.get("Interface Name")
                if interface_name is not None and not pd.isna(interface_name) and str(interface_name).strip():
                    new_name = str(interface_name).strip()
                    eff_seg_upd = 1
                    if get_object_segment_id:
                        try:
                            osid = get_object_segment_id(resolved_id, "SystemInterface")
                            if osid is not None and osid > 0:
                                eff_seg_upd = int(osid)
                        except Exception:
                            pass
                    if check_duplicate_interface_name(new_name, exclude_id=resolved_id, segment_id=eff_seg_upd):
                        errors.append(
                            ValidationError(
                                row_number,
                                "Interface Name",
                                f"Another interface with name '{new_name}' already exists in this segment",
                                "DUPLICATE_NAME",
                            )
                        )
                        continue
                    row_data["Interface Name"] = new_name
                
                # Optional on update: Interface Description (only set when provided)
                description = row.get("Interface Description")
                if description is not None and not pd.isna(description) and str(description).strip():
                    row_data["Interface Description"] = str(description).strip()
                
                # Optional on update: Lifecycle (only set when provided; no default)
                lifecycle_name = row.get("Lifecycle")
                if lifecycle_name is not None and not pd.isna(lifecycle_name) and str(lifecycle_name).strip():
                    lifecycle_id = get_interface_lifecycle_id_by_name(str(lifecycle_name).strip())
                    if not lifecycle_id:
                        errors.append(ValidationError(row_number, "Lifecycle", f"Invalid Lifecycle: {lifecycle_name}", "INVALID_LOOKUP"))
                        continue
                    row_data["Lifecycle_ID"] = lifecycle_id
                
                # Optional on update: Source System Short Name (only set when provided)
                source_system_short_name = row.get("Source System Short Name")
                if source_system_short_name is not None and not pd.isna(source_system_short_name) and str(source_system_short_name).strip():
                    source_system_id = get_system_id_by_short_name(str(source_system_short_name).strip())
                    if source_system_id:
                        row_data["Source System_ID"] = source_system_id
                    else:
                        errors.append(ValidationError(row_number, "Source System Short Name", f"Invalid Source System: {source_system_short_name}", "INVALID_LOOKUP"))
                        continue
                
                # Optional on update: Target System Short Name (only set when provided; segment will sync in Java)
                target_system_short_name = row.get("Target System Short Name")
                if target_system_short_name is not None and not pd.isna(target_system_short_name) and str(target_system_short_name).strip():
                    target_system_id = get_system_id_by_short_name(str(target_system_short_name).strip())
                    if target_system_id:
                        row_data["Target System_ID"] = target_system_id
                    else:
                        errors.append(ValidationError(row_number, "Target System Short Name", f"Invalid Target System: {target_system_short_name}", "INVALID_LOOKUP"))
                        continue
                
                # Optional on update: Automation Level (only set when provided; no default)
                automation_name = row.get("Automation Level")
                if automation_name is not None and not pd.isna(automation_name) and str(automation_name).strip():
                    automation_id = get_interface_automation_id_by_name(str(automation_name).strip())
                    if not automation_id:
                        errors.append(ValidationError(row_number, "Automation Level", f"Invalid Automation Level: {automation_name}", "INVALID_LOOKUP"))
                        continue
                    row_data["Automation Level_ID"] = automation_id
                
                # Optional: Reference
                ref = row.get("Reference")
                if not pd.isna(ref) and str(ref).strip():
                    row_data["Reference"] = str(ref).strip()
                
                # Optional: Asset ID
                asset_id = row.get("Asset ID")
                if not pd.isna(asset_id) and str(asset_id).strip():
                    row_data["Asset ID"] = str(asset_id).strip()
                
                # Optional: Synchronisation Control
                sync_control = row.get("Synchronisation Control")
                if not pd.isna(sync_control) and str(sync_control).strip():
                    row_data["Synchronisation Control"] = str(sync_control).strip()
                
                # Optional: Transfer Method lookup
                transfer_method_name = row.get("Transfer Method")
                if not pd.isna(transfer_method_name) and str(transfer_method_name).strip():
                    transfer_method_id = get_interface_transfer_method_id_by_name(str(transfer_method_name).strip())
                    if transfer_method_id:
                        row_data["Transfer Method_ID"] = transfer_method_id
                
                # Optional: Transfer Format lookup
                transfer_format_name = row.get("Transfer Format")
                if not pd.isna(transfer_format_name) and str(transfer_format_name).strip():
                    transfer_format_id = get_interface_transfer_format_id_by_name(str(transfer_format_name).strip())
                    if transfer_format_id:
                        row_data["Transfer Format_ID"] = transfer_format_id
                
                # Optional: Interface Classification lookup
                classification_name = row.get("Interface Classification")
                if not pd.isna(classification_name) and str(classification_name).strip():
                    classification_id = get_interface_classification_id_by_name(str(classification_name).strip())
                    if classification_id:
                        row_data["Interface Classification_ID"] = classification_id
                
                # Optional: BUDG Status lookup
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_status_id_by_name(str(status_name).strip())
                    if status_id:
                        row_data["BUDG Status_ID"] = status_id
                
                # Optional: Frequency lookup
                frequency_name = row.get("Frequency")
                if not pd.isna(frequency_name) and str(frequency_name).strip():
                    frequency_id = get_interface_frequency_id_by_name(str(frequency_name).strip())
                    if frequency_id:
                        row_data["Frequency_ID"] = frequency_id
                
                # Optional: BUDG Viewing lookup
                viewing_name = row.get("BUDG Viewing")
                if not pd.isna(viewing_name) and str(viewing_name).strip():
                    viewing_id = get_viewing_id_by_name(str(viewing_name).strip())
                    if viewing_id:
                        row_data["BUDG Viewing_ID"] = viewing_id
                
                # Governance Role
                role_name = row.get("Governance Role")
                if not pd.isna(role_name) and str(role_name).strip():
                    role_id = get_role_id_by_name(str(role_name).strip())
                    if role_id:
                        row_data["Governance Role_ID"] = role_id
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    _eb = len(errors)
                    add_custom_fields_to_validated_data(row, row_data, "Interface", row_number, errors)
                    if _row_has_blocking_cf_errors_since(errors, _eb, row_number):
                        continue

                validated_data.append(row_data)
                
            else:  # INSERT
                # Collect all missing mandatory field errors before continuing
                has_mandatory_errors = False
                
                # Required: Interface Name
                interface_name = row.get("Interface Name")
                if not interface_name or pd.isna(interface_name) or str(interface_name).strip() == "":
                    errors.append(ValidationError(row_number, "Interface Name", 
                        "Required field missing: The 'Interface Name' field is required and cannot be empty. Please provide a name for this Interface.",
                        "MISSING_REQUIRED"))
                    has_mandatory_errors = True
                else:
                    interface_name = str(interface_name).strip()
                    row_data["Interface Name"] = interface_name
                    
                    # Check for duplicate name within template
                    name_lower = interface_name.lower()
                    if name_lower in template_names:
                        errors.append(ValidationError(row_number, "Interface Name", 
                            f"Duplicate name '{interface_name}' found within the template. Each interface name must be unique in the upload file.",
                            "DUPLICATE_NAME_IN_TEMPLATE"))
                        has_mandatory_errors = True
                    else:
                        template_names.add(name_lower)
                        
                        eff_seg = (
                            resolve_effective_segment_id_for_duplicate_check(row, segment_mode, segment)
                            if resolve_effective_segment_id_for_duplicate_check
                            else 1
                        )
                        if check_duplicate_interface_name(interface_name, segment_id=eff_seg):
                            errors.append(ValidationError(row_number, "Interface Name", 
                                f"Interface with name '{interface_name}' already exists in this segment",
                                "DUPLICATE_NAME"))
                            has_mandatory_errors = True
                
                # Required: Interface Description
                description = row.get("Interface Description")
                if not description or pd.isna(description) or str(description).strip() == "":
                    errors.append(ValidationError(row_number, "Interface Description", 
                        "Required field missing: The 'Interface Description' field is required and cannot be empty. Please provide a description for this Interface.",
                        "MISSING_REQUIRED"))
                    has_mandatory_errors = True
                else:
                    row_data["Interface Description"] = str(description).strip()
                
                # Only continue to next row if there are mandatory field errors
                if has_mandatory_errors:
                    continue
                
                # Segment validation (Rules A, B, C) - only for INSERT operations
                # Initialize segment_name outside validation block so it's available for inclusion in row_data
                segment_value = row.get("Segment")
                segment_name = str(segment_value).strip() if segment_value and not pd.isna(segment_value) else None
                
                if False and segment_mode and segment_mode in ["MULTIPLE", "ENTERPRISE", "SPECIFIC"]: # Interface does not use segment - validation skipped
                    
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
                
                # Required: Lifecycle (use first value if empty)
                lifecycle_name = row.get("Lifecycle")
                lifecycle_id = None
                if not lifecycle_name or pd.isna(lifecycle_name) or str(lifecycle_name).strip() == "":
                    # Use first lifecycle if empty
                    lifecycle_id = get_first_interface_lifecycle_id()
                    if not lifecycle_id:
                        errors.append(ValidationError(row_number, "Lifecycle", "Lifecycle is required and no default available", "MISSING_REQUIRED"))
                        continue
                else:
                    lifecycle_id = get_interface_lifecycle_id_by_name(str(lifecycle_name).strip())
                    if not lifecycle_id:
                        errors.append(ValidationError(row_number, "Lifecycle", f"Invalid Lifecycle: {lifecycle_name}", "INVALID_LOOKUP"))
                        continue
                row_data["Lifecycle_ID"] = lifecycle_id
                
                # Source / target system: Short Name or ENV ID columns (Source_systemID, Target_systemID)
                has_additional_mandatory_errors = False
                source_system_id, target_system_id, resolve_err = _resolve_source_target_system_ids(row)
                if resolve_err == "Source System Short Name":
                    source_system_name = row.get("Source System Short Name")
                    if source_system_name and not pd.isna(source_system_name) and str(source_system_name).strip():
                        errors.append(ValidationError(row_number, "Source System Short Name",
                            f"Invalid Source System: {source_system_name}", "INVALID_LOOKUP"))
                    else:
                        errors.append(ValidationError(row_number, "Source System Short Name",
                            "Required field missing: provide 'Source System Short Name' or a valid Source_systemID / Source System ID.",
                            "MISSING_REQUIRED"))
                    has_additional_mandatory_errors = True
                elif resolve_err == "Target System Short Name":
                    target_system_name = row.get("Target System Short Name")
                    if target_system_name and not pd.isna(target_system_name) and str(target_system_name).strip():
                        errors.append(ValidationError(row_number, "Target System Short Name",
                            f"Invalid Target System: {target_system_name}", "INVALID_LOOKUP"))
                    else:
                        errors.append(ValidationError(row_number, "Target System Short Name",
                            "Required field missing: provide 'Target System Short Name' or a valid Target_systemID / Target System ID.",
                            "MISSING_REQUIRED"))
                    has_additional_mandatory_errors = True
                else:
                    row_data["Source System_ID"] = source_system_id
                    row_data["Target System_ID"] = target_system_id

                if has_additional_mandatory_errors:
                    continue
                
                # Required: Automation Level (use first value if empty)
                automation_name = row.get("Automation Level")
                automation_id = None
                if not automation_name or pd.isna(automation_name) or str(automation_name).strip() == "":
                    # Use first automation level if empty
                    automation_id = get_first_interface_automation_id()
                    if not automation_id:
                        errors.append(ValidationError(row_number, "Automation Level", "Automation Level is required and no default available", "MISSING_REQUIRED"))
                        continue
                else:
                    automation_id = get_interface_automation_id_by_name(str(automation_name).strip())
                    if not automation_id:
                        errors.append(ValidationError(row_number, "Automation Level", f"Invalid Automation Level: {automation_name}", "INVALID_LOOKUP"))
                        continue
                row_data["Automation Level_ID"] = automation_id
                
                # Optional Reference (auto-generated if empty)
                ref = row.get("Reference")
                if not pd.isna(ref) and str(ref).strip():
                    ref_value = str(ref).strip()
                    # Check for duplicate reference within template
                    ref_lower = ref_value.lower()
                    if ref_lower in template_refs:
                        errors.append(ValidationError(row_number, "Reference", 
                            f"Duplicate reference '{ref_value}' found within the template. Each interface reference must be unique in the upload file.",
                            "DUPLICATE_REF_IN_TEMPLATE"))
                        continue
                    template_refs.add(ref_lower)
                    
                    # Check for duplicate reference in database
                    existing_interface_id = get_interface_id_by_ref(ref_value)
                    if existing_interface_id:
                        errors.append(ValidationError(row_number, "Reference", 
                            f"Interface with reference '{ref_value}' already exists in the database",
                            "DUPLICATE_REF"))
                        continue
                    
                    row_data["Reference"] = ref_value
                
                # Optional Asset ID
                asset_id = row.get("Asset ID")
                if not pd.isna(asset_id) and str(asset_id).strip():
                    row_data["Asset ID"] = str(asset_id).strip()
                
                # Optional Synchronisation Control
                sync_control = row.get("Synchronisation Control")
                if not pd.isna(sync_control) and str(sync_control).strip():
                    row_data["Synchronisation Control"] = str(sync_control).strip()
                
                # Optional Transfer Method lookup
                transfer_method_name = row.get("Transfer Method")
                if not pd.isna(transfer_method_name) and str(transfer_method_name).strip():
                    transfer_method_id = get_interface_transfer_method_id_by_name(str(transfer_method_name).strip())
                    if transfer_method_id:
                        row_data["Transfer Method_ID"] = transfer_method_id
                
                # Optional Transfer Format lookup
                transfer_format_name = row.get("Transfer Format")
                if not pd.isna(transfer_format_name) and str(transfer_format_name).strip():
                    transfer_format_id = get_interface_transfer_format_id_by_name(str(transfer_format_name).strip())
                    if transfer_format_id:
                        row_data["Transfer Format_ID"] = transfer_format_id
                
                # Optional Interface Classification lookup
                classification_name = row.get("Interface Classification")
                if not pd.isna(classification_name) and str(classification_name).strip():
                    classification_id = get_interface_classification_id_by_name(str(classification_name).strip())
                    if classification_id:
                        row_data["Interface Classification_ID"] = classification_id
                
                # Optional BUDG Status lookup
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_status_id_by_name(str(status_name).strip())
                    if status_id:
                        row_data["BUDG Status_ID"] = status_id
                
                # Optional Frequency lookup
                frequency_name = row.get("Frequency")
                if not pd.isna(frequency_name) and str(frequency_name).strip():
                    frequency_id = get_interface_frequency_id_by_name(str(frequency_name).strip())
                    if frequency_id:
                        row_data["Frequency_ID"] = frequency_id
                
                # Optional BUDG Viewing lookup
                viewing_name = row.get("BUDG Viewing")
                if not pd.isna(viewing_name) and str(viewing_name).strip():
                    viewing_id = get_viewing_id_by_name(str(viewing_name).strip())
                    if viewing_id:
                        row_data["BUDG Viewing_ID"] = viewing_id
                
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
                    elif segment and segment.strip() and get_segment_name_by_id:
                        # Use selectedSegment from UI if no Excel segment
                        try:
                            segment_id_int = int(segment.strip())
                            selected_segment_name = get_segment_name_by_id(segment_id_int)
                            if selected_segment_name:
                                row_data["Segment"] = selected_segment_name
                        except (ValueError, TypeError):
                            pass  # Java will handle validation
                elif segment_mode == "SPECIFIC":
                    if segment_name:
                        # Excel segment overrides UI selection
                        row_data["Segment"] = segment_name
                    elif segment and segment.strip() and get_segment_name_by_id:
                        # Use selectedSegment from UI if no Excel segment
                        try:
                            segment_id_int = int(segment.strip())
                            selected_segment_name = get_segment_name_by_id(segment_id_int)
                            if selected_segment_name:
                                row_data["Segment"] = selected_segment_name
                        except (ValueError, TypeError):
                            pass  # Java will handle validation
                elif segment_mode == "ENTERPRISE":
                    # Always include "Enterprise" for ENTERPRISE mode
                    row_data["Segment"] = "Enterprise"
                elif segment_mode is None and segment_name:
                    # Include segment if provided even when mode is null
                    row_data["Segment"] = segment_name
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    _eb = len(errors)
                    add_custom_fields_to_validated_data(row, row_data, "Interface", row_number, errors)
                    if _row_has_blocking_cf_errors_since(errors, _eb, row_number):
                        continue

                validated_data.append(row_data)
                
        except Exception as e:
            logger.error(f"Error processing row {row_number}: {e}")
            errors.append(ValidationError(row_number, "General", str(e), "PROCESSING_ERROR"))
    
    return validated_data, errors

