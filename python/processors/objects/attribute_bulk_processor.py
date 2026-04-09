"""
Attribute Bulk Upload Validation Module
Validates Excel files for Attribute bulk uploads and resolves lookups to IDs.
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
    Return expected sheet name for Attribute bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Attribute"
    if upload_option == "Update Existing Items":
        return "Update Attribute"
    if upload_option == "Remove Existing Items":
        return "Delete Attribute"
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


def _get_row_description(row: Any) -> Optional[str]:
    """Get Description column value from Excel row for error reporting."""
    val = row.get("Description")
    if val is None or pd.isna(val) or not str(val).strip():
        return None
    return str(val).strip()


class ValidationError:
    def __init__(self, row: int, field: str, message: str, error_code: str, description: Optional[str] = None):
        self.row = row
        self.field = field
        self.message = message
        self.error_code = error_code
        self.description = description

    def dict(self):
        d = {"row": self.row, "field": self.field, "message": self.message, "error_code": self.error_code}
        if self.description is not None and self.description:
            d["description"] = self.description
        return d


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


def get_data_type_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("attribute_datatype", "PrimaryName", name)


def get_dataset_id_by_ref(ref: Optional[str]) -> Optional[int]:
    if not ref or not str(ref).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM dataset WHERE RefNumber = %s LIMIT 1", (str(ref).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_dataset_id_by_ref error: %s", e)
        return None


def get_dataset_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("dataset", "PrimaryName", name)


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
    return get_lookup_id_by_name("glossary", "Name", name)


def get_role_id_by_name(role_name: Optional[str]) -> Optional[int]:
    """Resolve object_role.id by primaryname scoped to the Attribute module."""
    if not role_name or not str(role_name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT id FROM module WHERE LOWER(primaryname) = LOWER(%s) LIMIT 1",
                    ("Attribute",),
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


def get_data_type_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("attribute_datatype", "PrimaryName", name)


def get_requirement_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("requirement", "PrimaryName", name)


def get_origination_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("attribute_origination", "PrimaryName", name)


def get_editability_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("attribute_editability", "PrimaryName", name)


def get_editability_role_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("attribute_edit_role", "PrimaryName", name)


def get_dataset_id_by_system_short_name(system_short_name: Optional[str]) -> Optional[int]:
    if not system_short_name or not str(system_short_name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT d.ID FROM dataset d "
                    "JOIN system s ON d.MasterSource = s.id "
                    "WHERE LOWER(s.Name) = LOWER(%s) LIMIT 1",
                    (str(system_short_name).strip(),)
                )
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_dataset_id_by_system_short_name error: %s", e)
        return None


def get_parent_glossary_id_by_name(name: Optional[str]) -> Optional[int]:
    return get_lookup_id_by_name("glossary", "Name", name)


def get_glossary_parent_id(glossary_id: Optional[int]) -> Optional[int]:
    """Return Parent_ID from glossary table for the given glossary_id (non-deleted only)."""
    if glossary_id is None:
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT Parent_ID FROM glossary WHERE ID = %s AND (Deleted_datetime IS NULL OR Deleted_datetime = '')",
                    (glossary_id,),
                )
                row = cur.fetchone()
                return row[0] if row and row[0] is not None else None
    except Exception as e:
        logger.warning("get_glossary_parent_id error: %s", e)
        return None


def get_attribute_id_by_ref(ref: Optional[str]) -> Optional[int]:
    """Resolve attribute ID by RefNumber (globally unique for attributes)."""
    if not ref or not str(ref).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT ID FROM attribute WHERE RefNumber = %s AND DeletedDatetime IS NULL LIMIT 1",
                    (str(ref).strip(),)
                )
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_attribute_id_by_ref error: %s", e)
        return None


def get_attribute_ids_by_name_and_dataset(name: Optional[str], dataset_id: int) -> List[int]:
    """Return list of attribute IDs with given PrimaryName in the given dataset."""
    if not name or not str(name).strip() or dataset_id is None:
        return []
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT ID FROM attribute WHERE Dataset_ID = %s AND LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL",
                    (dataset_id, str(name).strip())
                )
                return [r[0] for r in cur.fetchall()]
    except Exception as e:
        logger.warning("get_attribute_ids_by_name_and_dataset error: %s", e)
        return []


def get_attribute_ids_by_name(name: Optional[str]) -> List[int]:
    """Return list of attribute IDs with given PrimaryName across all datasets."""
    if not name or not str(name).strip():
        return []
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT ID FROM attribute WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL",
                    (str(name).strip(),)
                )
                return [r[0] for r in cur.fetchall()]
    except Exception as e:
        logger.warning("get_attribute_ids_by_name error: %s", e)
        return []


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


def validate_and_resolve_attribute(df: pd.DataFrame, upload_option: str, user_id: int, segment_mode: Optional[str] = None, segment: Optional[str] = None) -> Tuple[List[Dict[str, Any]], List[ValidationError]]:
    """
    Validate and resolve Attribute records from DataFrame.
    Returns: (validated_data, errors)
    """
    validated_data = []
    errors = []
    
    operation = "INSERT"
    if upload_option == "Update Existing Items":
        operation = "UPDATE"
    elif upload_option == "Remove Existing Items":
        operation = "DELETE"
    
    # Track (dataset_id, name) and refs within the template to detect duplicates (for INSERT only)
    template_names_by_dataset = set()  # (dataset_id, name_lower) — same name allowed across different datasets
    template_refs = set()   # Track refs within this template
    
    for idx, row in df.iterrows():
        row_number = idx + 2
        row_data = {"row_number": row_number, "operation": operation}
        row_description = _get_row_description(row)
        if row_description is not None:
            row_data["Description"] = row_description

        try:
            if operation == "DELETE":
                attr_id = row.get("Attribute ID")
                if pd.isna(attr_id):
                    errors.append(ValidationError(row_number, "Attribute ID", "Attribute ID is required for deletion", "MISSING_REQUIRED", row_description))
                    continue
                
                row_data["Attribute ID"] = int(attr_id)
                
                if not _exists_in_table("attribute", row_data["Attribute ID"]):
                    errors.append(ValidationError(row_number, "Attribute ID", f"Attribute with ID {row_data['Attribute ID']} does not exist", "NOT_FOUND", row_description))
                    continue
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "Data Attributes", row_number, errors)
                
                validated_data.append(row_data)
                
            elif operation == "UPDATE":
                # Resolve attribute by ID, Reference Number, or Attribute Name (+ optional Data Set/System)
                resolved_attr_id = None
                attr_id_val = row.get("Attribute ID")
                ref = row.get("Reference Number")
                name = row.get("Attribute Name")
                ref_stripped = str(ref).strip() if ref is not None and not pd.isna(ref) else ""
                name_stripped = str(name).strip() if name is not None and not pd.isna(name) else ""

                if attr_id_val is not None and not pd.isna(attr_id_val) and str(attr_id_val).strip() != "":
                    try:
                        resolved_attr_id = int(attr_id_val)
                        if not _exists_in_table("attribute", resolved_attr_id):
                            errors.append(ValidationError(row_number, "Attribute ID", f"Attribute with ID {resolved_attr_id} does not exist", "NOT_FOUND", row_description))
                            continue
                    except (ValueError, TypeError):
                        errors.append(ValidationError(row_number, "Attribute ID", "Attribute ID must be a valid integer", "INVALID_VALUE", row_description))
                        continue
                elif ref_stripped:
                    resolved_attr_id = get_attribute_id_by_ref(ref_stripped)
                    if resolved_attr_id is None:
                        errors.append(ValidationError(row_number, "Reference Number", f"Attribute not found for Reference Number '{ref_stripped}'", "NOT_FOUND", row_description))
                        continue
                elif name_stripped != "" or (name is not None and not pd.isna(name)):
                    # Resolve dataset for lookup only (do not set row_data["Dataset_ID"] for update)
                    resolution_dataset_id = None
                    dataset_ref = row.get("Data Set Ref.")
                    if dataset_ref is not None and not pd.isna(dataset_ref) and str(dataset_ref).strip():
                        resolution_dataset_id = get_dataset_id_by_ref(str(dataset_ref).strip())
                    if resolution_dataset_id is None:
                        dataset_name = row.get("Data Set Name")
                        if dataset_name is not None and not pd.isna(dataset_name) and str(dataset_name).strip():
                            resolution_dataset_id = get_dataset_id_by_name(str(dataset_name).strip())
                    if resolution_dataset_id is None:
                        system_short_name = row.get("System Short Name")
                        if system_short_name is not None and not pd.isna(system_short_name) and str(system_short_name).strip():
                            resolution_dataset_id = get_dataset_id_by_system_short_name(str(system_short_name).strip())

                    if resolution_dataset_id is not None:
                        ids_in_dataset = get_attribute_ids_by_name_and_dataset(name_stripped or str(name).strip() if name is not None else "", resolution_dataset_id)
                        if len(ids_in_dataset) == 0:
                            errors.append(ValidationError(row_number, "Attribute Name", f"No attribute found with name '{name_stripped or name}' in the specified dataset", "NOT_FOUND", row_description))
                            continue
                        if len(ids_in_dataset) > 1:
                            errors.append(ValidationError(row_number, "Attribute Name", "Multiple attributes with the same name in this dataset; provide Attribute ID or Reference Number to identify the attribute", "AMBIGUOUS", row_description))
                            continue
                        resolved_attr_id = ids_in_dataset[0]
                    else:
                        ids_any = get_attribute_ids_by_name(name_stripped or (str(name).strip() if name is not None else ""))
                        if len(ids_any) == 0:
                            errors.append(ValidationError(row_number, "Attribute Name", f"No attribute found with name '{name_stripped or name}'", "NOT_FOUND", row_description))
                            continue
                        if len(ids_any) > 1:
                            errors.append(ValidationError(row_number, "Attribute Name", "Multiple attributes with this name; provide Data Set Name/Ref or System Short Name, or Attribute ID or Reference Number", "AMBIGUOUS", row_description))
                            continue
                        resolved_attr_id = ids_any[0]
                else:
                    errors.append(ValidationError(row_number, "Attribute ID", "Attribute ID, Reference Number, or Attribute Name (with optional Data Set/System) is required for update", "MISSING_REQUIRED", row_description))
                    continue

                row_data["Attribute ID"] = resolved_attr_id

                # Attribute Name and Definition are optional for update; can be empty
                if name is not None and not pd.isna(name):
                    row_data["Attribute Name"] = str(name).strip()
                else:
                    row_data["Attribute Name"] = ""
                if row.get("Attribute Definition") is not None and not pd.isna(row.get("Attribute Definition")):
                    row_data["Attribute Definition"] = str(row.get("Attribute Definition")).strip()
                else:
                    row_data["Attribute Definition"] = ""

                # Optional: Reference Number
                if ref_stripped:
                    row_data["Reference Number"] = ref_stripped

                # Optional: Key (TRUE/FALSE -> 1/0)
                key = row.get("Key")
                if not pd.isna(key):
                    key_val = parse_bool(key)
                    if key_val is not None:
                        row_data["Key"] = key_val

                # Optional text fields
                for field in ["Business Logic", "DB Field Name"]:
                    val = row.get(field)
                    if not pd.isna(val) and str(val).strip():
                        row_data[field] = str(val).strip()

                # Optional numeric: Data Length
                length = row.get("Data Length")
                if not pd.isna(length):
                    try:
                        row_data["Data Length"] = int(length)
                    except:
                        pass

                # Data Type resolution
                data_type_name = row.get("Data Type")
                if not pd.isna(data_type_name) and str(data_type_name).strip():
                    data_type_id = get_data_type_id_by_name(str(data_type_name).strip())
                    if data_type_id:
                        row_data["Data Type_ID"] = data_type_id

                # Attribute Requirement resolution
                requirement_name = row.get("Attribute Requirement")
                if not pd.isna(requirement_name) and str(requirement_name).strip():
                    requirement_id = get_requirement_id_by_name(str(requirement_name).strip())
                    if requirement_id:
                        row_data["Attribute Requirement_ID"] = requirement_id

                # Do not set row_data["Dataset_ID"] for update when dataset was only used for resolution

                # Glossary resolution (ref/name must refer to same glossary when both provided)
                glossary_id = None
                glossary_ref = row.get("Glossary Ref.")
                glossary_name = row.get("Glossary Name")
                has_glossary_ref = not pd.isna(glossary_ref) and str(glossary_ref).strip()
                has_glossary_name = not pd.isna(glossary_name) and str(glossary_name).strip()
                if has_glossary_ref and has_glossary_name:
                    id_by_ref = get_glossary_id_by_ref(str(glossary_ref).strip())
                    id_by_name = get_glossary_id_by_name(str(glossary_name).strip())
                    if id_by_ref and id_by_name and id_by_ref != id_by_name:
                        errors.append(ValidationError(row_number, "Glossary Ref.", "Glossary Ref. and Glossary Name refer to different glossary terms. They must refer to the same glossary.", "GLOSSARY_MISMATCH", row_description))
                        continue
                    glossary_id = id_by_ref or id_by_name
                    if glossary_id:
                        row_data["Glossary_ID"] = glossary_id
                elif has_glossary_ref:
                    glossary_id = get_glossary_id_by_ref(str(glossary_ref).strip())
                    if glossary_id:
                        row_data["Glossary_ID"] = glossary_id
                elif has_glossary_name:
                    glossary_id = get_glossary_id_by_name(str(glossary_name).strip())
                    if glossary_id:
                        row_data["Glossary_ID"] = glossary_id

                # Parent Glossary Name resolution and validation
                parent_glossary_id = None
                parent_glossary_name = row.get("Parent Glossary Name")
                if not pd.isna(parent_glossary_name) and str(parent_glossary_name).strip():
                    parent_glossary_id = get_parent_glossary_id_by_name(str(parent_glossary_name).strip())
                    if parent_glossary_id:
                        row_data["Parent_Glossary_ID"] = parent_glossary_id
                    if glossary_id and parent_glossary_id:
                        actual_parent_id = get_glossary_parent_id(glossary_id)
                        if actual_parent_id != parent_glossary_id:
                            errors.append(ValidationError(row_number, "Parent Glossary Name", "Parent Glossary Name is not the actual parent of the specified Glossary.", "PARENT_GLOSSARY_MISMATCH", row_description))
                            continue
                
                # Origin resolution
                origin_name = row.get("Origin")
                if not pd.isna(origin_name) and str(origin_name).strip():
                    origin_id = get_origination_id_by_name(str(origin_name).strip())
                    if origin_id:
                        row_data["Origin_ID"] = origin_id
                
                # Editability resolution
                editability_name = row.get("Editability")
                if not pd.isna(editability_name) and str(editability_name).strip():
                    editability_id = get_editability_id_by_name(str(editability_name).strip())
                    if editability_id:
                        row_data["Editability_ID"] = editability_id
                
                # Editability Role resolution
                editability_role_name = row.get("Editability Role")
                if not pd.isna(editability_role_name) and str(editability_role_name).strip():
                    editability_role_id = get_editability_role_id_by_name(str(editability_role_name).strip())
                    if editability_role_id:
                        row_data["Editability Role_ID"] = editability_role_id
                
                # Governance Role
                role_name = row.get("Governance Role")
                if not pd.isna(role_name) and str(role_name).strip():
                    role_id = get_role_id_by_name(str(role_name).strip())
                    if role_id:
                        row_data["Governance Role_ID"] = role_id
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "Data Attributes", row_number, errors)
                
                validated_data.append(row_data)
                
            else:  # INSERT
                # Collect all missing mandatory field errors before continuing
                has_mandatory_errors = False
                
                # Required: Attribute Name
                name = row.get("Attribute Name")
                if not name or pd.isna(name) or str(name).strip() == "":
                    errors.append(ValidationError(row_number, "Attribute Name", "Attribute Name is required", "MISSING_REQUIRED", row_description))
                    has_mandatory_errors = True
                else:
                    name = str(name).strip()
                    row_data["Attribute Name"] = name
                    # Per-dataset duplicate name check runs after dataset resolution (see INSERT dataset block)
                
                # Required: Attribute Definition
                definition = row.get("Attribute Definition")
                if not definition or pd.isna(definition) or str(definition).strip() == "":
                    errors.append(ValidationError(row_number, "Attribute Definition", "Attribute Definition is required", "MISSING_REQUIRED", row_description))
                    has_mandatory_errors = True
                else:
                    row_data["Attribute Definition"] = str(definition).strip()
                
                # Only continue to next row if there are mandatory field errors
                if has_mandatory_errors:
                    continue
                
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
                                                    errors.append(ValidationError(row_number, "Segment", f"User does not have access to selected segment '{selected_segment_name}'", "SEGMENT_ACCESS_DENIED", row_description))
                                                # If accessible, use it (will be set in row_data below)
                                            else:
                                                errors.append(ValidationError(row_number, "Segment", f"Selected segment ID '{segment}' does not exist", "SEGMENT_NOT_FOUND", row_description))
                                        else:
                                            # Cannot validate by ID, require segment in Excel
                                            errors.append(ValidationError(row_number, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED", row_description))
                                    except ValueError:
                                        # segment is not a valid ID, require segment in Excel
                                        errors.append(ValidationError(row_number, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED", row_description))
                                else:
                                    # No validation functions available, require segment in Excel
                                    errors.append(ValidationError(row_number, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED", row_description))
                            else:
                                # No segment in Excel and no selectedSegment in UI
                                errors.append(ValidationError(row_number, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED", row_description))
                        else:
                            # Segment specified in Excel - validate it
                            # Rule C: Segment must exist and user must have access
                            if validate_segment_exists and user_id:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_number, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND", row_description))
                                else:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_number, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED", row_description))
                    elif segment_mode == "ENTERPRISE":
                        # Segment column is optional, but if provided, validate it
                        if segment_name and segment_name != "" and segment_name.lower() != "enterprise":
                            # Allow empty or "Enterprise", but validate if something else is provided
                            if validate_segment_exists:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_number, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND", row_description))
                                elif user_id and validate_user_segment_access:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_number, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED", row_description))
                    elif segment_mode == "SPECIFIC":
                        # SPECIFIC mode: If segment is provided in Excel, use it (overrides UI), otherwise use selectedSegment from UI
                        if segment_name and segment_name != "":
                            # Segment specified in Excel - use it (overrides UI selection)
                            # Rule C: Validate segment exists and user has access
                            if validate_segment_exists and user_id:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_number, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND", row_description))
                                else:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_number, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED", row_description))
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
                                                    errors.append(ValidationError(row_number, "Segment", f"User does not have access to selected segment '{selected_segment_name}'", "SEGMENT_ACCESS_DENIED", row_description))
                                                # If accessible, will use it (Java will use selectedSegment parameter)
                                            else:
                                                errors.append(ValidationError(row_number, "Segment", f"Selected segment ID '{segment}' does not exist", "SEGMENT_NOT_FOUND", row_description))
                                        # If no get_segment_name_by_id function, Java will handle validation
                                    except ValueError:
                                        # segment is not a valid ID
                                        errors.append(ValidationError(row_number, "Segment", f"Invalid selected segment ID: '{segment}'", "SEGMENT_INVALID", row_description))
                                # If no validation functions, Java will handle it
                            # If no segment in Excel and no selectedSegment in UI, Java will throw error
                
                # Optional: Reference Number (auto-generated if empty)
                ref = row.get("Reference Number")
                if not pd.isna(ref) and str(ref).strip():
                    ref_value = str(ref).strip()
                    # Check for duplicate reference within template
                    ref_lower = ref_value.lower()
                    if ref_lower in template_refs:
                        errors.append(ValidationError(row_number, "Reference Number", f"Duplicate reference '{ref_value}' found within the template. Each attribute reference must be unique in the upload file.", "DUPLICATE_REF_IN_TEMPLATE", row_description))
                        continue
                    if get_attribute_id_by_ref(ref_value) is not None:
                        errors.append(ValidationError(row_number, "Reference Number", f"Reference Number '{ref_value}' already exists on another attribute.", "REF_ALREADY_EXISTS", row_description))
                        continue
                    template_refs.add(ref_lower)
                    row_data["Reference Number"] = ref_value
                
                # Required: Key (marked as mandatory in template)
                key = row.get("Key")
                if pd.isna(key) or str(key).strip() == "":
                    errors.append(ValidationError(row_number, "Key", "Key is required. Please select 'True' or 'False' from the dropdown.", "MISSING_REQUIRED", row_description))
                    continue
                key_val = parse_bool(key)
                if key_val is None:
                    errors.append(ValidationError(row_number, "Key", f"Invalid Key value '{key}'. Please select 'True' or 'False' from the dropdown.", "INVALID_VALUE", row_description))
                    continue
                row_data["Key"] = key_val
                
                # Optional text fields
                for field in ["Business Logic", "DB Field Name"]:
                    val = row.get(field)
                    if not pd.isna(val) and str(val).strip():
                        row_data[field] = str(val).strip()
                
                # Optional numeric: Data Length
                length = row.get("Data Length")
                if not pd.isna(length):
                    try:
                        row_data["Data Length"] = int(length)
                    except:
                        pass
                
                # Required: Data Type (marked as mandatory in template)
                data_type_name = row.get("Data Type")
                if pd.isna(data_type_name) or not str(data_type_name).strip():
                    errors.append(ValidationError(row_number, "Data Type", "Data Type is required. Please select a value from the dropdown.", "MISSING_REQUIRED", row_description))
                    continue
                data_type_name = str(data_type_name).strip()
                data_type_id = get_data_type_id_by_name(data_type_name)
                if not data_type_id:
                    errors.append(ValidationError(row_number, "Data Type", f"Data Type '{data_type_name}' not found. Please select a valid value from the dropdown.", "NOT_FOUND", row_description))
                    continue
                row_data["Data Type_ID"] = data_type_id
                
                # Required: Attribute Requirement (marked as mandatory in template)
                requirement_name = row.get("Attribute Requirement")
                if pd.isna(requirement_name) or not str(requirement_name).strip():
                    errors.append(ValidationError(row_number, "Attribute Requirement", "Attribute Requirement is required. Please select a value from the dropdown.", "MISSING_REQUIRED", row_description))
                    continue
                requirement_name = str(requirement_name).strip()
                requirement_id = get_requirement_id_by_name(requirement_name)
                if not requirement_id:
                    errors.append(ValidationError(row_number, "Attribute Requirement", f"Attribute Requirement '{requirement_name}' not found. Please select a valid value from the dropdown.", "NOT_FOUND", row_description))
                    continue
                row_data["Attribute Requirement_ID"] = requirement_id
                
                # Dataset resolution (by Ref, Name, or System Short Name)
                # When multiple identifiers are provided, they must refer to the same dataset.
                dataset_id = None
                dataset_ref = row.get("Data Set Ref.")
                dataset_name = row.get("Data Set Name")
                system_short_name = row.get("System Short Name")
                has_ref = not pd.isna(dataset_ref) and str(dataset_ref).strip()
                has_name = not pd.isna(dataset_name) and str(dataset_name).strip()
                has_system = not pd.isna(system_short_name) and str(system_short_name).strip()

                if not has_ref and not has_name:
                    errors.append(ValidationError(row_number, "Data Set Ref.", "Data Set Ref. or Data Set Name is required. System Short Name alone cannot identify the dataset.", "MISSING_DATASET_IDENTIFIER", row_description))
                    continue

                ids_by_source = []
                if has_ref:
                    id_by_ref = get_dataset_id_by_ref(str(dataset_ref).strip())
                    if not id_by_ref:
                        errors.append(ValidationError(row_number, "Data Set Ref.", f"Dataset with reference '{dataset_ref}' not found", "DATASET_NOT_FOUND", row_description))
                        continue
                    ids_by_source.append(("Data Set Ref.", id_by_ref))
                if has_name:
                    id_by_name = get_dataset_id_by_name(str(dataset_name).strip())
                    if not id_by_name:
                        errors.append(ValidationError(row_number, "Data Set Name", f"Dataset with name '{dataset_name}' not found", "DATASET_NOT_FOUND", row_description))
                        continue
                    ids_by_source.append(("Data Set Name", id_by_name))
                if has_system:
                    id_by_system = get_dataset_id_by_system_short_name(str(system_short_name).strip())
                    if not id_by_system:
                        errors.append(ValidationError(row_number, "System Short Name", f"Dataset not found for system with short name '{system_short_name}'", "DATASET_NOT_FOUND", row_description))
                        continue
                    ids_by_source.append(("System Short Name", id_by_system))

                if ids_by_source:
                    dataset_ids = {sid for _, sid in ids_by_source}
                    if len(dataset_ids) > 1:
                        errors.append(ValidationError(row_number, "Data Set Ref.", "Data Set Ref., Data Set Name, and/or System Short Name refer to different datasets. They must refer to the same dataset.", "DATASET_MISMATCH", row_description))
                        continue
                    dataset_id = ids_by_source[0][1]
                    row_data["Dataset_ID"] = dataset_id

                    name_key = (dataset_id, name.lower())
                    if name_key in template_names_by_dataset:
                        errors.append(ValidationError(row_number, "Attribute Name", f"Duplicate name '{name}' for the same dataset in this file. Attribute names must be unique within each dataset.", "DUPLICATE_NAME_IN_TEMPLATE", row_description))
                        continue
                    template_names_by_dataset.add(name_key)
                
                # Glossary resolution
                # When both Ref and Name are provided, they must refer to the same glossary.
                glossary_id = None
                glossary_ref = row.get("Glossary Ref.")
                glossary_name = row.get("Glossary Name")
                has_glossary_ref = not pd.isna(glossary_ref) and str(glossary_ref).strip()
                has_glossary_name = not pd.isna(glossary_name) and str(glossary_name).strip()

                if has_glossary_ref and has_glossary_name:
                    id_by_ref = get_glossary_id_by_ref(str(glossary_ref).strip())
                    id_by_name = get_glossary_id_by_name(str(glossary_name).strip())
                    if not id_by_ref:
                        errors.append(ValidationError(row_number, "Glossary Ref.", f"Glossary with reference '{glossary_ref}' not found", "GLOSSARY_NOT_FOUND", row_description))
                        continue
                    if not id_by_name:
                        errors.append(ValidationError(row_number, "Glossary Name", f"Glossary with name '{glossary_name}' not found", "GLOSSARY_NOT_FOUND", row_description))
                        continue
                    if id_by_ref != id_by_name:
                        errors.append(ValidationError(row_number, "Glossary Ref.", "Glossary Ref. and Glossary Name refer to different glossary terms. They must refer to the same glossary.", "GLOSSARY_MISMATCH", row_description))
                        continue
                    glossary_id = id_by_ref
                    row_data["Glossary_ID"] = glossary_id
                elif has_glossary_ref:
                    glossary_id = get_glossary_id_by_ref(str(glossary_ref).strip())
                    if not glossary_id:
                        errors.append(ValidationError(row_number, "Glossary Ref.", f"Glossary with reference '{glossary_ref}' not found", "GLOSSARY_NOT_FOUND", row_description))
                        continue
                    row_data["Glossary_ID"] = glossary_id
                elif has_glossary_name:
                    glossary_id = get_glossary_id_by_name(str(glossary_name).strip())
                    if not glossary_id:
                        errors.append(ValidationError(row_number, "Glossary Name", f"Glossary with name '{glossary_name}' not found", "GLOSSARY_NOT_FOUND", row_description))
                        continue
                    row_data["Glossary_ID"] = glossary_id
                
                # Parent Glossary Name resolution
                parent_glossary_id = None
                parent_glossary_name = row.get("Parent Glossary Name")
                if not pd.isna(parent_glossary_name) and str(parent_glossary_name).strip():
                    parent_glossary_id = get_parent_glossary_id_by_name(str(parent_glossary_name).strip())
                    if not parent_glossary_id:
                        errors.append(ValidationError(row_number, "Parent Glossary Name", f"Parent Glossary with name '{parent_glossary_name}' not found", "PARENT_GLOSSARY_NOT_FOUND", row_description))
                        continue
                    row_data["Parent_Glossary_ID"] = parent_glossary_id
                    
                    # Validate that the specified parent is the actual parent of the resolved glossary
                    if glossary_id and parent_glossary_id:
                        actual_parent_id = get_glossary_parent_id(glossary_id)
                        if actual_parent_id != parent_glossary_id:
                            errors.append(ValidationError(row_number, "Parent Glossary Name", "Parent Glossary Name is not the actual parent of the specified Glossary.", "PARENT_GLOSSARY_MISMATCH", row_description))
                            continue
                
                # Origin resolution
                origin_name = row.get("Origin")
                if not pd.isna(origin_name) and str(origin_name).strip():
                    origin_id = get_origination_id_by_name(str(origin_name).strip())
                    if origin_id:
                        row_data["Origin_ID"] = origin_id
                
                # Editability resolution
                editability_name = row.get("Editability")
                if not pd.isna(editability_name) and str(editability_name).strip():
                    editability_id = get_editability_id_by_name(str(editability_name).strip())
                    if editability_id:
                        row_data["Editability_ID"] = editability_id
                
                # Editability Role resolution
                editability_role_name = row.get("Editability Role")
                if not pd.isna(editability_role_name) and str(editability_role_name).strip():
                    editability_role_id = get_editability_role_id_by_name(str(editability_role_name).strip())
                    if editability_role_id:
                        row_data["Editability Role_ID"] = editability_role_id
                
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
                    add_custom_fields_to_validated_data(row, row_data, "Data Attributes", row_number, errors)
                
                validated_data.append(row_data)
                
        except Exception as e:
            logger.error(f"Error processing row {row_number}: {e}")
            errors.append(ValidationError(row_number, "General", str(e), "PROCESSING_ERROR", row_description))
    
    return validated_data, errors

