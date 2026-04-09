"""
Dataset Bulk Upload Validation Module
Validates Excel files for Dataset bulk uploads and resolves lookups to IDs.
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
    Return expected sheet name for Dataset bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Dataset"
    if upload_option == "Update Existing Items":
        return "Update Dataset"
    if upload_option == "Remove Existing Items":
        return "Delete Dataset"
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


def _has_val(v) -> bool:
    if v is None:
        return False
    try:
        if pd.isna(v):
            return False
    except (TypeError, ValueError):
        pass
    s = str(v).strip()
    return bool(s) and s.lower() not in ("nan", "")


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


def get_dataset_type_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("dataset_type", "PrimaryName", name)


def get_dataset_lifecycle_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("dataset_lifecycle", "PrimaryName", name)


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


def get_glossary_id_by_ref(ref: Optional[str]) -> Optional[int]:
    if not ref or not str(ref).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM glossary WHERE Ref_Number = %s LIMIT 1", (str(ref).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_glossary_id_by_ref error: %s", e)
        return None


def get_glossary_id_by_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM glossary WHERE LOWER(Name) = LOWER(%s) LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_glossary_id_by_name error: %s", e)
        return None


def count_systems_by_short_name(name: Optional[str]) -> int:
    if not name or not str(name).strip():
        return 0
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT COUNT(*) FROM system WHERE LOWER(Name) = LOWER(%s) AND (Deleted_datetime IS NULL OR Deleted_datetime = '')",
                    (str(name).strip(),),
                )
                return int(cur.fetchone()[0])
    except Exception as e:
        logger.warning("count_systems_by_short_name error: %s", e)
        return 0


def get_system_id_by_short_name_and_parent_short_name(child_name: str, parent_name: str) -> Optional[int]:
    """Resolve system by child short name and parent system's short name."""
    if not child_name or not parent_name:
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT s.id FROM system s
                    INNER JOIN system p ON p.id = s.parent_id
                        AND (p.Deleted_datetime IS NULL OR p.Deleted_datetime = '')
                    WHERE LOWER(s.Name) = LOWER(%s) AND LOWER(p.Name) = LOWER(%s)
                      AND (s.Deleted_datetime IS NULL OR s.Deleted_datetime = '')
                    LIMIT 1
                    """,
                    (str(child_name).strip(), str(parent_name).strip()),
                )
                row = cur.fetchone()
                return int(row[0]) if row else None
    except Exception as e:
        logger.warning("get_system_id_by_short_name_and_parent_short_name error: %s", e)
        return None


def get_system_parent_short_name(system_id: int) -> Optional[str]:
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT p.Name FROM system s
                    LEFT JOIN system p ON p.id = s.parent_id AND (p.Deleted_datetime IS NULL OR p.Deleted_datetime = '')
                    WHERE s.id = %s AND (s.Deleted_datetime IS NULL OR s.Deleted_datetime = '')
                    """,
                    (system_id,),
                )
                row = cur.fetchone()
                return str(row[0]).strip() if row and row[0] is not None else None
    except Exception as e:
        logger.warning("get_system_parent_short_name error: %s", e)
        return None


def get_glossary_id_by_name_and_parent_name(term_name: str, parent_glossary_name: str) -> Optional[int]:
    """Resolve glossary term by Name and immediate parent glossary Name."""
    if not term_name or not parent_glossary_name:
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT g.ID FROM glossary g
                    INNER JOIN glossary pg ON pg.ID = g.Parent_ID AND (pg.Deleted_datetime IS NULL OR pg.Deleted_datetime = '')
                    WHERE LOWER(g.Name) = LOWER(%s) AND LOWER(pg.Name) = LOWER(%s)
                      AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '')
                    LIMIT 1
                    """,
                    (str(term_name).strip(), str(parent_glossary_name).strip()),
                )
                row = cur.fetchone()
                return int(row[0]) if row else None
    except Exception as e:
        logger.warning("get_glossary_id_by_name_and_parent_name error: %s", e)
        return None


def get_glossary_parent_name(glossary_id: int) -> Optional[str]:
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT pg.Name FROM glossary g
                    LEFT JOIN glossary pg ON pg.ID = g.Parent_ID AND (pg.Deleted_datetime IS NULL OR pg.Deleted_datetime = '')
                    WHERE g.ID = %s AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '')
                    """,
                    (glossary_id,),
                )
                row = cur.fetchone()
                return str(row[0]).strip() if row and row[0] is not None else None
    except Exception as e:
        logger.warning("get_glossary_parent_name error: %s", e)
        return None


def count_glossaries_by_name(name: Optional[str]) -> int:
    if not name or not str(name).strip():
        return 0
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT COUNT(*) FROM glossary WHERE LOWER(Name) = LOWER(%s) AND (Deleted_datetime IS NULL OR Deleted_datetime = '')",
                    (str(name).strip(),),
                )
                return int(cur.fetchone()[0])
    except Exception as e:
        logger.warning("count_glossaries_by_name error: %s", e)
        return 0


def get_role_id_by_name(role_name: Optional[str]) -> Optional[int]:
    """Resolve object_role.id by primaryname scoped to the Data Sets module."""
    if not role_name or not str(role_name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT id FROM module WHERE LOWER(primaryname) = LOWER(%s) LIMIT 1",
                    ("Data Sets",),
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


def get_dataset_id_by_ref(ref: Optional[str]) -> Optional[int]:
    if not ref or not str(ref).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT ID FROM dataset WHERE RefNumber = %s AND DeletedDatetime IS NULL LIMIT 1",
                    (str(ref).strip(),),
                )
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_dataset_id_by_ref error: %s", e)
        return None


def get_dataset_id_by_name(name: Optional[str], master_source_id: Optional[int] = None) -> Optional[int]:
    """
    Resolve dataset ID by primary name. When master_source_id is set, the match is scoped to that system
    so the same name may exist on different systems.
    When master_source_id is None, returns an ID only if exactly one non-deleted dataset has that name.
    """
    if not name or not str(name).strip():
        return None
    n = str(name).strip()
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                if master_source_id is not None and master_source_id > 0:
                    cur.execute(
                        "SELECT ID FROM dataset WHERE LOWER(PrimaryName) = LOWER(%s) AND MasterSource = %s AND DeletedDatetime IS NULL LIMIT 1",
                        (n, master_source_id),
                    )
                    row = cur.fetchone()
                    return row[0] if row else None
                cur.execute(
                    "SELECT ID FROM dataset WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL",
                    (n,),
                )
                rows = cur.fetchall()
                if len(rows) == 1:
                    return rows[0][0]
                return None
    except Exception as e:
        logger.warning("get_dataset_id_by_name error: %s", e)
        return None


def count_datasets_by_primary_name(name: Optional[str]) -> int:
    if not name or not str(name).strip():
        return 0
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT COUNT(*) FROM dataset WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL",
                    (str(name).strip(),),
                )
                row = cur.fetchone()
                return int(row[0]) if row else 0
    except Exception as e:
        logger.warning("count_datasets_by_primary_name error: %s", e)
        return 0


def validate_column_headers(df: pd.DataFrame, upload_option: str) -> List[ValidationError]:
    """
    Validate that required columns exist based on operation type.
    Usage column is optional and not included in required columns list.
    """
    errors: List[ValidationError] = []
    columns = [str(c).strip() for c in df.columns]
    
    def missing(col: str) -> bool:
        return col not in columns
    
    if upload_option == "Add New Items":
        # Required columns for INSERT operation
        required = ["Name", "Definition"]
        for col in required:
            if missing(col):
                errors.append(ValidationError(0, col, f"Missing required column: {col}", "MISSING_COLUMN"))
        # Usage is optional - not included in required list
    
    elif upload_option == "Update Existing Items":
        # Required columns for UPDATE operation
        required = ["ID", "Name", "Definition"]
        for col in required:
            if missing(col):
                errors.append(ValidationError(0, col, f"Missing required column: {col}", "MISSING_COLUMN"))
        # Usage is optional - not included in required list
    
    elif upload_option == "Remove Existing Items":
        # Required columns for DELETE operation
        if missing("ID"):
            errors.append(ValidationError(0, "ID", "Missing required column: ID", "MISSING_COLUMN"))
    
    return errors


def validate_and_resolve_dataset(df: pd.DataFrame, upload_option: str, user_id: int, segment_mode: Optional[str] = None, segment: Optional[str] = None) -> Tuple[List[Dict[str, Any]], List[ValidationError]]:
    """
    Validate and resolve Dataset records from DataFrame.
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
                dataset_id = row.get("ID")
                if pd.isna(dataset_id):
                    errors.append(ValidationError(row_number, "ID", "ID is required for deletion", "MISSING_REQUIRED"))
                    continue
                
                row_data["ID"] = int(dataset_id)
                
                if not _exists_in_table("dataset", row_data["ID"]):
                    errors.append(ValidationError(row_number, "ID", f"Dataset with ID {row_data['ID']} does not exist", "NOT_FOUND"))
                    continue
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "Dataset", row_number, errors)
                
                validated_data.append(row_data)
                
            elif operation == "UPDATE":
                # Identity: at least one of ID, Ref., or Name required; resolve to dataset ID
                def _has_val_update(v):
                    if v is None or pd.isna(v):
                        return False
                    s = str(v).strip()
                    return bool(s) and s.lower() not in ("nan", "")
                id_raw = row.get("ID")
                ref_raw = row.get("Ref.")
                name_raw = row.get("Name")
                id_by_id = None
                if _has_val_update(id_raw):
                    try:
                        vid = int(float(str(id_raw).strip()))
                        if _exists_in_table("dataset", vid):
                            id_by_id = vid
                    except (ValueError, TypeError):
                        pass
                id_by_ref = get_dataset_id_by_ref(str(ref_raw).strip()) if _has_val_update(ref_raw) else None

                # Resolve system first so name lookup is scoped per system (duplicate names allowed across systems)
                system_id_col = row.get("System ID")
                system_short_name = row.get("System Short Name")
                has_system_id = not pd.isna(system_id_col) and str(system_id_col).strip() != ""
                has_system_name = not pd.isna(system_short_name) and str(system_short_name).strip() != ""
                system_id_by_id = None
                system_id_by_name = None
                if has_system_id:
                    try:
                        sid = int(float(str(system_id_col).strip()))
                        if _exists_in_table("system", sid, "id"):
                            system_id_by_id = sid
                            row_data["System_ID"] = sid
                    except (ValueError, TypeError):
                        pass
                if system_id_by_id is None and has_system_name:
                    system_id_by_name = get_system_id_by_short_name(str(system_short_name).strip())
                    if system_id_by_name:
                        row_data["System_ID"] = system_id_by_name
                if has_system_id and has_system_name:
                    if system_id_by_name is None and has_system_name:
                        system_id_by_name = get_system_id_by_short_name(str(system_short_name).strip())
                    if system_id_by_id is not None and system_id_by_name is not None and system_id_by_id != system_id_by_name:
                        errors.append(ValidationError(row_number, "System ID", "System ID and System Short Name refer to different systems", "SYSTEM_ID_NAME_MISMATCH"))
                        continue

                resolved_system_id = system_id_by_id if system_id_by_id is not None else system_id_by_name

                id_by_name = (
                    get_dataset_id_by_name(str(name_raw).strip(), resolved_system_id)
                    if _has_val_update(name_raw)
                    else None
                )
                if _has_val_update(name_raw) and id_by_name is None and id_by_id is None and id_by_ref is None:
                    if resolved_system_id is not None and resolved_system_id > 0:
                        errors.append(
                            ValidationError(
                                row_number,
                                "Name",
                                f"No dataset named '{str(name_raw).strip()}' found for the specified system.",
                                "NOT_FOUND",
                            )
                        )
                        continue
                    cnt = count_datasets_by_primary_name(str(name_raw).strip())
                    if cnt > 1:
                        errors.append(
                            ValidationError(
                                row_number,
                                "Name",
                                "Multiple datasets share this name; specify System ID or Ref. to identify the row.",
                                "AMBIGUOUS_NAME",
                            )
                        )
                        continue

                filled = sum(1 for v in (id_raw, ref_raw, name_raw) if _has_val_update(v))
                if filled == 0:
                    errors.append(ValidationError(row_number, "ID", "At least one of ID, Ref., or Name is required for update", "MISSING_REQUIRED"))
                    continue
                resolved_id = id_by_id or id_by_ref or id_by_name
                if resolved_id is None:
                    errors.append(ValidationError(row_number, "ID", "No dataset found for the provided identity (ID, Ref., or Name)", "NOT_FOUND"))
                    continue
                if id_by_id is not None and (id_by_ref is not None or id_by_name is not None):
                    ids = {x for x in (id_by_id, id_by_ref, id_by_name) if x is not None}
                    if len(ids) > 1:
                        errors.append(ValidationError(row_number, "ID", "ID, Ref., and Name refer to different datasets", "IDENTITY_MISMATCH"))
                        continue
                row_data["ID"] = resolved_id
                
                # Name optional for update (empty = keep existing)
                name = row.get("Name")
                if name is not None and not pd.isna(name) and str(name).strip() != "":
                    row_data["Name"] = str(name).strip()
                else:
                    row_data["Name"] = ""
                
                # Definition optional for update (empty = keep existing)
                definition = row.get("Definition")
                if definition is not None and not pd.isna(definition) and str(definition).strip() != "":
                    row_data["Definition"] = str(definition).strip()
                else:
                    row_data["Definition"] = ""
                
                # Optional fields
                ref = row.get("Ref.")
                if not pd.isna(ref) and str(ref).strip():
                    row_data["Ref."] = str(ref).strip()
                
                usage = row.get("Usage")
                if not pd.isna(usage) and str(usage).strip():
                    row_data["Usage"] = str(usage).strip()
                
                # Lookups
                viewing_name = row.get("BUDG Viewing")
                if not pd.isna(viewing_name) and str(viewing_name).strip():
                    viewing_id = get_viewing_id_by_name(str(viewing_name).strip())
                    if viewing_id:
                        row_data["BUDG Viewing_ID"] = viewing_id
                
                type_name = row.get("Type")
                if not pd.isna(type_name) and str(type_name).strip():
                    type_id = get_dataset_type_id_by_name(str(type_name).strip())
                    if type_id:
                        row_data["Type_ID"] = type_id
                
                lifecycle_name = row.get("Lifecycle")
                if not pd.isna(lifecycle_name) and str(lifecycle_name).strip():
                    lifecycle_id = get_dataset_lifecycle_id_by_name(str(lifecycle_name).strip())
                    if lifecycle_id:
                        row_data["Lifecycle_ID"] = lifecycle_id
                
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_status_id_by_name(str(status_name).strip())
                    if status_id:
                        row_data["BUDG Status_ID"] = status_id
                
                # Glossary resolution (UPDATE: validate Glossary Ref. vs Glossary Name when both provided)
                glossary_ref = row.get("Glossary Ref.")
                glossary_name = row.get("Glossary Name")
                has_glossary_ref = not pd.isna(glossary_ref) and str(glossary_ref).strip() != ""
                has_glossary_name = not pd.isna(glossary_name) and str(glossary_name).strip() != ""
                glossary_id_by_ref = None
                glossary_id_by_name = None
                if has_glossary_ref:
                    glossary_id_by_ref = get_glossary_id_by_ref(str(glossary_ref).strip())
                    if glossary_id_by_ref:
                        row_data["Glossary_ID"] = glossary_id_by_ref
                if glossary_id_by_ref is None and has_glossary_name:
                    glossary_id_by_name = get_glossary_id_by_name(str(glossary_name).strip())
                    if glossary_id_by_name:
                        row_data["Glossary_ID"] = glossary_id_by_name
                if has_glossary_ref and has_glossary_name:
                    if glossary_id_by_name is None and has_glossary_name:
                        glossary_id_by_name = get_glossary_id_by_name(str(glossary_name).strip())
                    if glossary_id_by_ref is not None and glossary_id_by_name is not None and glossary_id_by_ref != glossary_id_by_name:
                        errors.append(ValidationError(row_number, "Glossary Ref.", "Glossary Ref. and Glossary Name refer to different glossaries", "GLOSSARY_REF_NAME_MISMATCH"))
                        continue
                
                # Governance Role
                role_name = row.get("Governance Role")
                if not pd.isna(role_name) and str(role_name).strip():
                    role_id = get_role_id_by_name(str(role_name).strip())
                    if role_id:
                        row_data["Governance Role_ID"] = role_id
                
                # Return Dataset_ID for valuesjob_dataset tracking
                row_data["Dataset_ID"] = row_data["ID"]
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "Dataset", row_number, errors)
                
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
                
                # Initialize segment from row so it's always defined for "Include Segment" block below
                segment_value = row.get("Segment")
                segment_name = str(segment_value).strip() if segment_value and not pd.isna(segment_value) else None
                
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
                
                # Optional Ref (will be auto-generated if empty)
                ref = row.get("Ref.")
                if not pd.isna(ref) and str(ref).strip():
                    row_data["Ref."] = str(ref).strip()
                
                usage = row.get("Usage")
                if not pd.isna(usage) and str(usage).strip():
                    row_data["Usage"] = str(usage).strip()
                
                # Lookups
                viewing_name = row.get("BUDG Viewing")
                if not pd.isna(viewing_name) and str(viewing_name).strip():
                    viewing_id = get_viewing_id_by_name(str(viewing_name).strip())
                    if viewing_id:
                        row_data["BUDG Viewing_ID"] = viewing_id
                
                type_name = row.get("Type")
                if not pd.isna(type_name) and str(type_name).strip():
                    type_id = get_dataset_type_id_by_name(str(type_name).strip())
                    if type_id:
                        row_data["Type_ID"] = type_id
                
                # System resolution (INSERT): require system; validate ID; ID vs name mismatch; parent rules
                system_id_col = row.get("System ID")
                system_short_name = row.get("System Short Name")
                parent_system_short = row.get("Parent System Short Name")
                has_system_id = _has_val(system_id_col)
                has_system_name = _has_val(system_short_name)
                has_parent_system = _has_val(parent_system_short)

                if has_parent_system and not has_system_id and not has_system_name:
                    errors.append(
                        ValidationError(
                            row_number,
                            "Parent System Short Name",
                            "Parent System Short Name cannot be used without System ID or System Short Name.",
                            "PARENT_SYSTEM_WITHOUT_SYSTEM",
                        )
                    )
                    continue

                if not has_system_id and not has_system_name:
                    errors.append(
                        ValidationError(
                            row_number,
                            "System Short Name",
                            "System ID or System Short Name is required to link the dataset to a system.",
                            "MISSING_SYSTEM",
                        )
                    )
                    continue

                system_id_by_id = None
                system_id_by_name = None

                if has_system_id:
                    try:
                        sid = int(float(str(system_id_col).strip()))
                    except (ValueError, TypeError):
                        errors.append(
                            ValidationError(row_number, "System ID", "System ID is not a valid number.", "SYSTEM_ID_INVALID")
                        )
                        continue
                    if _exists_in_table("system", sid, "id"):
                        system_id_by_id = sid
                    elif has_system_name:
                        # ENV / migration exports carry source DB numeric IDs; after replace import, IDs differ.
                        logger.info(
                            "Dataset row %s: System ID %s not in target DB; resolving via System Short Name instead.",
                            row_number,
                            sid,
                        )
                    else:
                        errors.append(
                            ValidationError(
                                row_number,
                                "System ID",
                                f"No system found with ID {sid}.",
                                "SYSTEM_NOT_FOUND",
                            )
                        )
                        continue

                if has_system_name:
                    sname = str(system_short_name).strip()
                    if has_parent_system:
                        system_id_by_name = get_system_id_by_short_name_and_parent_short_name(
                            sname, str(parent_system_short).strip()
                        )
                        if system_id_by_name is None:
                            errors.append(
                                ValidationError(
                                    row_number,
                                    "System Short Name",
                                    "No system matches System Short Name and Parent System Short Name, or parent does not match.",
                                    "SYSTEM_NOT_FOUND",
                                )
                            )
                            continue
                    else:
                        system_id_by_name = get_system_id_by_short_name(sname)
                        if system_id_by_name is None:
                            errors.append(
                                ValidationError(
                                    row_number,
                                    "System Short Name",
                                    f"No system found with short name '{sname}'.",
                                    "SYSTEM_NOT_FOUND",
                                )
                            )
                            continue
                        cnt = count_systems_by_short_name(sname)
                        if cnt > 1:
                            errors.append(
                                ValidationError(
                                    row_number,
                                    "System Short Name",
                                    f"Multiple systems named '{sname}'. Specify Parent System Short Name to disambiguate.",
                                    "SYSTEM_AMBIGUOUS_USE_PARENT",
                                )
                            )
                            continue

                if has_system_id and has_system_name:
                    if system_id_by_id is not None and system_id_by_name is not None and system_id_by_id != system_id_by_name:
                        errors.append(
                            ValidationError(
                                row_number,
                                "System ID",
                                "System ID and System Short Name refer to different systems.",
                                "SYSTEM_ID_NAME_MISMATCH",
                            )
                        )
                        continue

                resolved_system_id = system_id_by_id if system_id_by_id is not None else system_id_by_name
                if resolved_system_id is None:
                    errors.append(
                        ValidationError(
                            row_number,
                            "System Short Name",
                            "Could not resolve system from System ID or System Short Name.",
                            "MISSING_SYSTEM",
                        )
                    )
                    continue

                row_data["System_ID"] = resolved_system_id

                if has_parent_system and resolved_system_id is not None:
                    db_parent = get_system_parent_short_name(resolved_system_id)
                    want = str(parent_system_short).strip().lower()
                    if db_parent is None or db_parent.strip().lower() != want:
                        errors.append(
                            ValidationError(
                                row_number,
                                "Parent System Short Name",
                                "Parent System Short Name does not match the parent of the resolved system.",
                                "SYSTEM_PARENT_MISMATCH",
                            )
                        )
                        continue

                lifecycle_name = row.get("Lifecycle")
                if not pd.isna(lifecycle_name) and str(lifecycle_name).strip():
                    lifecycle_id = get_dataset_lifecycle_id_by_name(str(lifecycle_name).strip())
                    if lifecycle_id:
                        row_data["Lifecycle_ID"] = lifecycle_id
                
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_status_id_by_name(str(status_name).strip())
                    if status_id:
                        row_data["BUDG Status_ID"] = status_id
                
                # Glossary resolution (INSERT): ref/name validation; parent disambiguation
                glossary_ref = row.get("Glossary Ref.")
                glossary_name = row.get("Glossary Name")
                parent_glossary_name = row.get("Parent Glossary Name")
                has_glossary_ref = _has_val(glossary_ref)
                has_glossary_name = _has_val(glossary_name)
                has_parent_glossary = _has_val(parent_glossary_name)

                if has_parent_glossary and not has_glossary_ref and not has_glossary_name:
                    errors.append(
                        ValidationError(
                            row_number,
                            "Parent Glossary Name",
                            "Parent Glossary Name cannot be used without Glossary Ref. or Glossary Name.",
                            "PARENT_GLOSSARY_WITHOUT_GLOSSARY",
                        )
                    )
                    continue

                glossary_id_by_ref = None
                glossary_id_by_name = None

                if has_glossary_ref:
                    glossary_id_by_ref = get_glossary_id_by_ref(str(glossary_ref).strip())
                    if glossary_id_by_ref is None:
                        errors.append(
                            ValidationError(
                                row_number,
                                "Glossary Ref.",
                                f"Glossary Ref. '{str(glossary_ref).strip()}' was not found.",
                                "GLOSSARY_REF_NOT_FOUND",
                            )
                        )
                        continue

                if has_glossary_name:
                    gname = str(glossary_name).strip()
                    if has_parent_glossary:
                        glossary_id_by_name = get_glossary_id_by_name_and_parent_name(
                            gname, str(parent_glossary_name).strip()
                        )
                        if glossary_id_by_name is None:
                            errors.append(
                                ValidationError(
                                    row_number,
                                    "Glossary Name",
                                    "No glossary matches Glossary Name and Parent Glossary Name, or parent does not match.",
                                    "GLOSSARY_NOT_FOUND",
                                )
                            )
                            continue
                    else:
                        glossary_id_by_name = get_glossary_id_by_name(gname)
                        if glossary_id_by_name is None:
                            errors.append(
                                ValidationError(
                                    row_number,
                                    "Glossary Name",
                                    f"No glossary found with name '{gname}'.",
                                    "GLOSSARY_NAME_NOT_FOUND",
                                )
                            )
                            continue
                        cntg = count_glossaries_by_name(gname)
                        if cntg > 1:
                            errors.append(
                                ValidationError(
                                    row_number,
                                    "Glossary Name",
                                    f"Multiple glossary terms named '{gname}'. Specify Parent Glossary Name to disambiguate.",
                                    "GLOSSARY_AMBIGUOUS_USE_PARENT",
                                )
                            )
                            continue

                if has_glossary_ref and has_glossary_name:
                    if glossary_id_by_ref is not None and glossary_id_by_name is not None and glossary_id_by_ref != glossary_id_by_name:
                        errors.append(
                            ValidationError(
                                row_number,
                                "Glossary Ref.",
                                "Glossary Ref. and Glossary Name refer to different glossary terms.",
                                "GLOSSARY_REF_NAME_MISMATCH",
                            )
                        )
                        continue

                resolved_glossary_id = glossary_id_by_ref if glossary_id_by_ref is not None else glossary_id_by_name
                if resolved_glossary_id is not None:
                    row_data["Glossary_ID"] = resolved_glossary_id

                if has_glossary_ref and has_parent_glossary:
                    db_pg = get_glossary_parent_name(glossary_id_by_ref)
                    want = str(parent_glossary_name).strip().lower()
                    if db_pg is None or db_pg.strip().lower() != want:
                        errors.append(
                            ValidationError(
                                row_number,
                                "Parent Glossary Name",
                                "Parent Glossary Name does not match the parent of the glossary specified by Glossary Ref.",
                                "GLOSSARY_PARENT_MISMATCH",
                            )
                        )
                        continue

                # Governance Role
                role_name = row.get("Governance Role")
                if not pd.isna(role_name) and str(role_name).strip():
                    role_id = get_role_id_by_name(str(role_name).strip())
                    if role_id:
                        row_data["Governance Role_ID"] = role_id
                    row_data["Governance Role"] = str(role_name).strip()
                
                # User Email (optional) - pass through for stakeholder creation in Java
                user_email = row.get("User Email")
                if user_email is not None and not pd.isna(user_email) and str(user_email).strip():
                    row_data["User Email"] = str(user_email).strip()
                
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
                    add_custom_fields_to_validated_data(row, row_data, "Dataset", row_number, errors)
                
                validated_data.append(row_data)
                
        except Exception as e:
            logger.error(f"Error processing row {row_number}: {e}")
            errors.append(ValidationError(row_number, "General", str(e), "PROCESSING_ERROR"))
    
    return validated_data, errors

