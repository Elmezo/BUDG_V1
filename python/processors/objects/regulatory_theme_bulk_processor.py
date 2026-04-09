"""
Regulatory Theme Bulk Upload Validation Service
FastAPI service that validates Excel files for bulk regulatory theme uploads
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import pandas as pd
import pymysql
import os
import logging
from datetime import datetime
import sys

# Add utils directory to path for segment_validator import
sys.path.append(os.path.join(os.path.dirname(__file__), '..', '..', 'utils'))
from excel_read import open_excel_file, read_excel
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

try:
    from bulk_segment_name_utils import (
        duplicate_name_in_facet_segment,
        resolve_effective_segment_id_for_duplicate_check,
    )
except ImportError:
    duplicate_name_in_facet_segment = None
    resolve_effective_segment_id_for_duplicate_check = None

try:
    from custom_fields_validator import (
        validate_custom_fields_for_row,
        filter_rows_with_blocking_custom_field_errors,
        merge_excel_custom_field_passthrough,
        BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE,
    )
except ImportError:
    validate_custom_fields_for_row = None
    filter_rows_with_blocking_custom_field_errors = None
    merge_excel_custom_field_passthrough = None
    BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE = {}

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

if validate_custom_fields_for_row is None:
    logger.warning("Could not import validate_custom_fields_for_row from custom_fields_validator")

app = FastAPI(title="Regulatory Theme Bulk Validation Service")

# Database configuration from environment variables
DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USERNAME", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "database": os.getenv("DB_NAME", "project"),
    "charset": "utf8mb4"
}

# Valid status values
VALID_STATUSES = ["Active", "Inactive", "Pending Review", "Obsolete", "Deleted"]


class ValidationRequest(BaseModel):
    file_path: str
    upload_option: str  # "Add New Items" | "Update Existing Items" | "Remove Existing Items"
    entity: str  # "Regulatory Theme"
    user_id: int
    column_mappings: Optional[Dict[str, str]] = None  # Maps Excel column names to expected field names


class ValidationError(BaseModel):
    row: int
    field: str
    message: str
    error_code: str


class RegulatoryThemeRow(BaseModel):
    operation: str  # INSERT | UPDATE | DELETE
    row_number: int
    Regulatory_Theme_ID: Optional[int] = None
    Reference: Optional[str] = None
    Short_Name: Optional[str] = None
    Regulatory_Theme_Long_Name: Optional[str] = None
    Description: Optional[str] = None
    Parent_Regulatory_Theme_Name: Optional[str] = None
    Parent_Ref: Optional[str] = None
    BUDG_Status: Optional[str] = None


class ValidationResponse(BaseModel):
    status: str  # "valid" | "invalid" | "error"
    message: str
    total_rows: int
    valid_rows: int
    invalid_rows: int
    errors: List[ValidationError]
    data: List[Dict[str, Any]]


def _validation_errors_to_dicts(errors: List[Any]) -> List[Dict[str, Any]]:
    """Normalize ValidationError / dict mix for filter_rows_with_blocking_custom_field_errors."""
    out: List[Dict[str, Any]] = []
    for e in errors:
        if hasattr(e, "model_dump"):
            out.append(e.model_dump())
        elif hasattr(e, "dict"):
            out.append(e.dict())
        elif isinstance(e, dict):
            out.append(e)
        else:
            out.append({
                "row": getattr(e, "row", 0),
                "field": getattr(e, "field", ""),
                "message": getattr(e, "message", ""),
                "error_code": getattr(e, "error_code", ""),
            })
    return out


def get_db_connection():
    """Create a database connection"""
    try:
        return pymysql.connect(**DB_CONFIG)
    except Exception as e:
        logger.error(f"Database connection failed: {e}")
        raise


def get_sheet_name(upload_option: str) -> Optional[str]:
    """
    Return expected sheet name for Regulatory Theme bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Regulatory Theme"
    if upload_option == "Update Existing Items":
        return "Update Regulatory Theme"
    if upload_option == "Remove Existing Items":
        return "Delete Regulatory Theme"
    return None


def check_duplicate_primary_name(
    primary_name: str,
    exclude_id: Optional[int] = None,
    segment_id: Optional[int] = None,
) -> bool:
    """True if the same regulatory theme long name exists in the target segment."""
    if duplicate_name_in_facet_segment is None:
        try:
            conn = get_db_connection()
            cursor = conn.cursor()
            if exclude_id:
                query = "SELECT COUNT(*) FROM regulatorytheme WHERE LOWER(PrimaryName) = LOWER(%s) AND ID != %s AND DeletedDatetime IS NULL"
                cursor.execute(query, (primary_name, exclude_id))
            else:
                query = "SELECT COUNT(*) FROM regulatorytheme WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL"
                cursor.execute(query, (primary_name,))
            count = cursor.fetchone()[0]
            cursor.close()
            conn.close()
            return count > 0
        except Exception as e:
            logger.error(f"Error checking duplicate: {e}")
            raise
    sid = segment_id if segment_id is not None else 1
    return duplicate_name_in_facet_segment("RegulatoryTheme", sid, primary_name, exclude_id)


def check_regulatory_theme_exists(theme_id: int) -> bool:
    """
    Check if a regulatory theme ID exists in the database
    Returns True if exists, False otherwise
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT COUNT(*) FROM regulatorytheme WHERE ID = %s AND DeletedDatetime IS NULL"
        cursor.execute(query, (theme_id,))
        
        count = cursor.fetchone()[0]
        cursor.close()
        conn.close()
        
        return count > 0
    except Exception as e:
        logger.error(f"Error checking regulatory theme existence: {e}")
        raise


def get_regulatory_theme_id_by_name(name: str) -> Optional[int]:
    """
    Get Regulatory Theme ID by PrimaryName
    Returns ID if found, None otherwise
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT ID FROM regulatorytheme WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL LIMIT 1"
        cursor.execute(query, (name.strip(),))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting regulatory theme ID by name: {e}")
        raise


def get_regulatory_theme_id_by_ref_number(ref_number: str) -> Optional[int]:
    """
    Get Regulatory Theme ID by RefNumber
    Returns ID if found, None otherwise
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT ID FROM regulatorytheme WHERE RefNumber = %s AND DeletedDatetime IS NULL LIMIT 1"
        cursor.execute(query, (ref_number.strip(),))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting regulatory theme ID by ref number: {e}")
        raise


def get_status_id_by_name(status_name: str) -> Optional[int]:
    """
    Get Status ID by primaryname
    Returns ID if found, None otherwise
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT ID FROM status WHERE LOWER(primaryname) = LOWER(%s) LIMIT 1"
        cursor.execute(query, (status_name.strip(),))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting status ID by name: {e}")
        raise


def validate_status(status_name: str) -> bool:
    """
    Validate that status name is in the valid list
    """
    return status_name.strip() in VALID_STATUSES


def find_excel_column_match(mapping_key: str, available_columns: List[str]) -> Optional[str]:
    """
    Find a matching column in the available columns using smart matching.
    Tries exact match, case-insensitive match, and partial match (starts with or contains).
    Returns the matched column name or None.
    
    This handles cases where Excel column names might have slight variations:
    - "a" matches "a", "A", "a ", " a", "a (example)", etc.
    """
    if not mapping_key or not available_columns:
        return None
    
    mapping_key_normalized = str(mapping_key).strip()
    mapping_key_lower = mapping_key_normalized.lower()
    
    # Try exact match first (after normalization)
    for col in available_columns:
        col_normalized = str(col).strip()
        if col_normalized == mapping_key_normalized:
            return col
    
    # Try case-insensitive exact match
    for col in available_columns:
        col_normalized = str(col).strip()
        col_lower = col_normalized.lower()
        if col_lower == mapping_key_lower:
            return col
    
    # Try partial match - column starts with the mapping key
    # This handles cases like "a" matching "a (example)" or "A Column"
    for col in available_columns:
        col_normalized = str(col).strip()
        col_lower = col_normalized.lower()
        # Check if column starts with mapping key (case-insensitive)
        if col_lower.startswith(mapping_key_lower):
            # Make sure it's followed by space, parenthesis, or end of string
            if (col_lower == mapping_key_lower or 
                col_lower.startswith(mapping_key_lower + " ") or 
                col_lower.startswith(mapping_key_lower + "(") or
                col_lower.startswith(mapping_key_lower + "-") or
                col_lower.startswith(mapping_key_lower + "_")):
                return col
    
    # Try contains match as last resort (only if mapping key is at least 3 characters)
    if len(mapping_key_normalized) >= 3:
        for col in available_columns:
            col_normalized = str(col).strip()
            col_lower = col_normalized.lower()
            if mapping_key_lower in col_lower:
                return col
    
    return None


def apply_column_mappings(df: pd.DataFrame, column_mappings: Optional[Dict[str, str]]) -> pd.DataFrame:
    """
    Apply column mappings to rename Excel columns to expected field names using smart matching.
    column_mappings: Dict mapping from Excel column name to expected field name
    Example: {"a": "Regulatory Theme Long Name", "اسم الموضوع التنظيمي": "Regulatory Theme Long Name", "الوصف": "Description"}
    
    Uses smart matching to find Excel columns even if they have slight variations:
    - Exact match: "a" matches "a"
    - Case-insensitive: "a" matches "A"
    - Partial match: "a" matches "a (example)" or "A Column"
    - Contains match: "reg" matches "Regulatory Theme" (if key is >= 3 chars)
    """
    if not column_mappings:
        return df
    
    logger.info(f"Applying column mappings. Received mappings: {column_mappings}")
    logger.info(f"DataFrame columns before mapping: {list(df.columns)}")
    
    # Get all available column names
    available_columns = [str(col) for col in df.columns]
    
    # Build rename dictionary using smart matching
    rename_dict = {}
    used_columns = set()  # Track which columns have been mapped to avoid duplicates
    
    for excel_col_mapping, expected_field in column_mappings.items():
        # Skip empty mappings
        if not excel_col_mapping or not expected_field or not str(excel_col_mapping).strip() or not str(expected_field).strip():
            logger.warn(f"Skipping empty mapping: '{excel_col_mapping}' -> '{expected_field}'")
            continue
        
        expected_field_normalized = str(expected_field).strip()
        
        # Find matching column in DataFrame using smart matching
        matched_col = find_excel_column_match(excel_col_mapping, available_columns)
        
        if matched_col:
            # Check if this column was already mapped
            if matched_col in used_columns:
                logger.warn(f"Column '{matched_col}' already mapped, skipping duplicate mapping for '{excel_col_mapping}' -> '{expected_field_normalized}'")
                continue
            
            rename_dict[matched_col] = expected_field_normalized
            used_columns.add(matched_col)
            logger.info(f"Smart mapping: Excel column '{matched_col}' (matched from '{excel_col_mapping}') -> '{expected_field_normalized}'")
        else:
            logger.warn(f"Could not find Excel column matching '{excel_col_mapping}' in available columns: {available_columns}")
    
    if rename_dict:
        logger.info(f"Applying {len(rename_dict)} column mappings: {list(rename_dict.keys())} -> {list(rename_dict.values())}")
        df = df.rename(columns=rename_dict)
        logger.info(f"DataFrame columns after mapping: {list(df.columns)}")
    else:
        logger.warn(f"No column mappings could be applied. DataFrame columns: {list(df.columns)}, Mappings: {list(column_mappings.keys())}")
    
    return df


def normalize_regulatory_theme_columns(df: pd.DataFrame, upload_option: str) -> pd.DataFrame:
    """
    Normalize column names so template header 'Parent Name' is accepted.
    Renames 'Parent Name' -> 'Parent Regulatory Theme Name' (case-insensitive) so
    validation and Java receive the expected key.
    """
    if df.empty:
        return df
    columns_list = list(df.columns)
    rename_dict = {}
    has_parent_name_canonical = any(str(c).strip() == "Parent Regulatory Theme Name" for c in columns_list)
    for col in columns_list:
        c = str(col).strip()
        if c.lower() == "parent name" and not has_parent_name_canonical:
            rename_dict[col] = "Parent Regulatory Theme Name"
            has_parent_name_canonical = True
            break
    if rename_dict:
        df = df.rename(columns=rename_dict)
        logger.info("Normalized Regulatory Theme columns: %s -> %s", list(rename_dict.keys()), list(rename_dict.values()))
    return df


def validate_column_headers(df: pd.DataFrame, upload_option: str) -> List[ValidationError]:
    """Validate column headers. No columns are mandatory; all are optional."""
    errors = []
    return errors


def validate_row_data(df: pd.DataFrame, upload_option: str, segment_mode: Optional[str] = None, segment: Optional[str] = None, user_id: Optional[int] = None) -> tuple[List[ValidationError], List[Dict[str, Any]]]:
    """
    Validate each row of data
    Returns (errors, validated_data)
    """
    errors = []
    validated_data = []
    
    # Build sets of names and refs in file so parent "in same file" is allowed
    names_in_file = set()
    refs_in_file = set()
    if "Regulatory Theme Long Name" in df.columns:
        for v in df["Regulatory Theme Long Name"].dropna():
            s = str(v).strip()
            if s:
                names_in_file.add(s.lower())
    if "Reference" in df.columns:
        for v in df["Reference"].dropna():
            s = str(v).strip()
            if s:
                refs_in_file.add(s.lower())
    
    for idx, row in df.iterrows():
        row_number = idx + 2  # +2 because Excel rows start at 1 and header is row 1
        row_errors = []
        
        if upload_option == "Add New Items":
            # Regulatory Theme Long Name (optional)
            theme_long_name = str(row.get("Regulatory Theme Long Name", "")).strip() if pd.notna(row.get("Regulatory Theme Long Name")) else ""
            
            eff_seg = (
                resolve_effective_segment_id_for_duplicate_check(row, segment_mode, segment)
                if resolve_effective_segment_id_for_duplicate_check
                else 1
            )
            if theme_long_name and check_duplicate_primary_name(theme_long_name, segment_id=eff_seg):
                row_errors.append(ValidationError(
                    row=row_number,
                    field="Regulatory Theme Long Name",
                    message=f"Regulatory Theme with name '{theme_long_name}' already exists",
                    error_code="DUPLICATE_NAME"
                ))
                errors.extend(row_errors)
                continue
            
            # Segment validation (Rules A, B, C) - only for INSERT operations
            # Initialize segment_name outside validation block so it's available for inclusion in validated_row
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
            
            # Validate Parent Regulatory Theme Name or Parent Ref.
            parent_id = None
            parent_name = None
            parent_ref = None
            has_name = pd.notna(row.get("Parent Regulatory Theme Name"))
            has_ref = pd.notna(row.get("Parent Ref."))
            current_ref = (str(row.get("Reference", "")).strip() if pd.notna(row.get("Reference")) else "").lower()
            current_short = (str(row.get("Short Name", "")).strip() if pd.notna(row.get("Short Name")) else "").lower()
            current_long = (theme_long_name or "").lower()

            if has_name:
                parent_name = str(row.get("Parent Regulatory Theme Name", "")).strip()
            if has_ref:
                parent_ref = str(row.get("Parent Ref.", "")).strip()

            # Self-reference: parent cannot be the same as the current row
            if has_ref and parent_ref and current_ref and parent_ref.lower() == current_ref:
                row_errors.append(ValidationError(
                    row=row_number,
                    field="Parent Ref.",
                    message="Parent cannot be the same as the current theme.",
                    error_code="PARENT_SELF_REFERENCE"
                ))
            if has_name and parent_name and (current_long or current_short):
                if parent_name.lower() == current_long or parent_name.lower() == current_short:
                    row_errors.append(ValidationError(
                        row=row_number,
                        field="Parent Regulatory Theme Name",
                        message="Parent cannot be the same as the current theme.",
                        error_code="PARENT_SELF_REFERENCE"
                    ))

            parent_in_file = False
            if not row_errors:
                # Resolve parent from DB first; only allow "in file" if not found in DB
                parent_id_by_ref = get_regulatory_theme_id_by_ref_number(parent_ref) if (has_ref and parent_ref) else None
                parent_id_by_name = get_regulatory_theme_id_by_name(parent_name) if (has_name and parent_name) else None
                if has_ref and parent_ref and parent_id_by_ref is not None:
                    parent_id = parent_id_by_ref
                if has_name and parent_name and parent_id_by_name is not None:
                    if parent_id is not None and parent_id != parent_id_by_name:
                        row_errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different regulatory themes",
                            error_code="PARENT_NAME_REF_MISMATCH"
                        ))
                    else:
                        parent_id = parent_id_by_name if parent_id is None else parent_id

                if parent_id is None and has_ref and parent_ref:
                    if parent_ref.lower() not in refs_in_file:
                        row_errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message=f"Parent Regulatory Theme with reference '{parent_ref}' not found",
                            error_code="PARENT_REF_NOT_FOUND"
                        ))
                    else:
                        parent_in_file = True
                if parent_id is None and has_name and parent_name and not row_errors:
                    if parent_name.lower() not in names_in_file:
                        row_errors.append(ValidationError(
                            row=row_number,
                            field="Parent Regulatory Theme Name",
                            message=f"Parent Regulatory Theme with name '{parent_name}' not found",
                            error_code="PARENT_NOT_FOUND"
                        ))
                    else:
                        parent_in_file = True

            # Validate BUDG Status if provided
            status_id = None
            if pd.notna(row.get("BUDG Status")):
                status_name = str(row.get("BUDG Status", "")).strip()
                if status_name:
                    if not validate_status(status_name):
                        row_errors.append(ValidationError(
                            row=row_number,
                            field="BUDG Status",
                            message=f"Invalid status '{status_name}'. Valid values are: {', '.join(VALID_STATUSES)}",
                            error_code="INVALID_STATUS"
                        ))
                    else:
                        status_id = get_status_id_by_name(status_name)
                        if status_id is None:
                            row_errors.append(ValidationError(
                                row=row_number,
                                field="BUDG Status",
                                message=f"Status '{status_name}' not found in database",
                                error_code="STATUS_NOT_FOUND"
                            ))
            
            # Description (optional)
            description = str(row.get("Description", "")).strip() if pd.notna(row.get("Description")) else ""
            
            # If there are any errors collected so far, add them and skip this row
            if row_errors:
                errors.extend(row_errors)
                continue
            
            # Add to validated data
            validated_row = {
                "operation": "INSERT",
                "row_number": row_number,
                "Regulatory Theme Long Name": theme_long_name,
                "Reference": str(row.get("Reference", "")).strip() if pd.notna(row.get("Reference")) else "",
                "Short Name": str(row.get("Short Name", "")).strip() if pd.notna(row.get("Short Name")) else "",
                "Description": description,
            }
            if parent_id is not None or parent_in_file:
                # Store parent name or ref for processing
                if pd.notna(row.get("Parent Regulatory Theme Name")):
                    validated_row["Parent Regulatory Theme Name"] = str(row.get("Parent Regulatory Theme Name", "")).strip()
                elif pd.notna(row.get("Parent Ref.")):
                    validated_row["Parent Ref."] = str(row.get("Parent Ref.", "")).strip()
            if status_id is not None:
                validated_row["BUDG Status"] = str(row.get("BUDG Status", "")).strip()
            # Include Segment in resolved data if present
            # For MULTIPLE mode: use segment from Excel, or selectedSegment from UI if no Excel segment
            # For SPECIFIC mode: use segment from Excel (overrides UI), or selectedSegment from UI if no Excel segment
            # For ENTERPRISE mode: use "Enterprise" or segment from Excel if provided
            # For null mode: use segment from Excel if provided
            if segment_mode == "MULTIPLE":
                if segment_name:
                    validated_row["Segment"] = segment_name
                elif segment and segment.strip():
                    # Use selectedSegment from UI if no Excel segment
                    if get_segment_name_by_id:
                        try:
                            selected_segment_name = get_segment_name_by_id(int(segment.strip()))
                            if selected_segment_name:
                                validated_row["Segment"] = selected_segment_name
                        except (ValueError, TypeError):
                            pass  # Java will handle validation
            elif segment_mode == "SPECIFIC":
                if segment_name:
                    # Segment from Excel (overrides UI)
                    validated_row["Segment"] = segment_name
                # If no segment in Excel, Java will use selectedSegment from UI
            elif segment_mode == "ENTERPRISE":
                # Always include "Enterprise" for ENTERPRISE mode (or segment from Excel if provided)
                if segment_name and segment_name.lower() != "enterprise":
                    validated_row["Segment"] = segment_name
                else:
                    validated_row["Segment"] = "Enterprise"
            elif segment_mode is None and segment_name:
                # Include segment if provided even when mode is null
                validated_row["Segment"] = segment_name
            
            # Validate and add custom fields
            if validate_custom_fields_for_row:
                try:
                    row_dict = {
                        str(col): (None if pd.isna(row.get(col)) else row.get(col))
                        for col in row.index
                    }
                    custom_field_errors, validated_custom_fields = validate_custom_fields_for_row(
                        row_dict, "Regulatory Theme", row_number
                    )
                    
                    # Add custom field errors to main errors list
                    for error in custom_field_errors:
                        errors.append(ValidationError(
                            row=error["row"],
                            field=error["field"],
                            message=error["message"],
                            error_code=error["error_code"]
                        ))
                    
                    # Add validated custom fields to validated_row
                    validated_row.update(validated_custom_fields)
                    if merge_excel_custom_field_passthrough and BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE:
                        bt = BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE.get("Regulatory Theme")
                        if bt is not None:
                            merge_excel_custom_field_passthrough(row, validated_row, bt)
                except Exception as e:
                    logger.error(f"Error validating custom fields for row {row_number}: {e}")
                    errors.append(ValidationError(
                        row=row_number,
                        field="Custom Fields",
                        message=f"Unexpected error while validating custom fields: {str(e)}",
                        error_code="CUSTOM_FIELD_VALIDATION_EXCEPTION",
                    ))

            validated_data.append(validated_row)

        elif upload_option == "Update Existing Items":
            def _has_val(v):
                if v is None: return False
                if pd.isna(v): return False
                s = str(v).strip()
                return bool(s) and s.lower() not in ("nan", "")
            id_raw = row.get("Regulatory Theme ID")
            ref_raw = row.get("Reference")
            name_raw = row.get("Regulatory Theme Long Name")
            id_by_id = None
            if _has_val(id_raw):
                try:
                    vid = int(float(str(id_raw).strip()))
                    if check_regulatory_theme_exists(vid):
                        id_by_id = vid
                except (ValueError, TypeError):
                    pass
            id_by_ref = get_regulatory_theme_id_by_ref_number(str(ref_raw).strip()) if _has_val(ref_raw) else None
            id_by_name = get_regulatory_theme_id_by_name(str(name_raw).strip()) if _has_val(name_raw) else None
            filled = sum(1 for v in (id_raw, ref_raw, name_raw) if _has_val(v))
            if filled == 0:
                errors.append(ValidationError(row=row_number, field="Regulatory Theme ID", message="At least one of Regulatory Theme ID, Reference, or Regulatory Theme Long Name is required for update", error_code="MISSING_ID"))
                continue
            theme_id = id_by_id or id_by_ref or id_by_name
            if theme_id is None:
                errors.append(ValidationError(row=row_number, field="Regulatory Theme ID", message="No regulatory theme found for the provided identity (ID, Reference, or Regulatory Theme Long Name)", error_code="ID_NOT_FOUND"))
                continue
            if filled >= 2:
                ids = {x for x in (id_by_id, id_by_ref, id_by_name) if x is not None}
                if len(ids) > 1:
                    errors.append(ValidationError(row=row_number, field="Regulatory Theme ID", message="Regulatory Theme ID, Reference and Regulatory Theme Long Name refer to different regulatory themes", error_code="IDENTITY_MISMATCH"))
                    continue
            
            # Regulatory Theme Long Name (optional for update)
            theme_long_name = str(row.get("Regulatory Theme Long Name", "")).strip() if pd.notna(row.get("Regulatory Theme Long Name")) else ""
            
            eff_seg_upd = 1
            if get_object_segment_id:
                try:
                    osid = get_object_segment_id(theme_id, "RegulatoryTheme")
                    if osid is not None and osid > 0:
                        eff_seg_upd = int(osid)
                except Exception:
                    pass
            if theme_long_name and check_duplicate_primary_name(theme_long_name, theme_id, segment_id=eff_seg_upd):
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulatory Theme Long Name",
                    message=f"Another regulatory theme with name '{theme_long_name}' already exists in this segment",
                    error_code="DUPLICATE_NAME"
                ))
                continue
            
            # Validate Parent Regulatory Theme Name or Parent Ref.
            parent_id = None
            parent_name = None
            parent_ref = None
            has_name = pd.notna(row.get("Parent Regulatory Theme Name"))
            has_ref = pd.notna(row.get("Parent Ref."))
            current_ref = (str(row.get("Reference", "")).strip() if pd.notna(row.get("Reference")) else "").lower()
            current_short = (str(row.get("Short Name", "")).strip() if pd.notna(row.get("Short Name")) else "").lower()
            current_long = (theme_long_name or "").lower()

            if has_name:
                parent_name = str(row.get("Parent Regulatory Theme Name", "")).strip()
            if has_ref:
                parent_ref = str(row.get("Parent Ref.", "")).strip()

            # Self-reference: parent cannot be the same as the current row (or current theme_id)
            if has_ref and parent_ref and current_ref and parent_ref.lower() == current_ref:
                errors.append(ValidationError(
                    row=row_number,
                    field="Parent Ref.",
                    message="Parent cannot be the same as the current theme.",
                    error_code="PARENT_SELF_REFERENCE"
                ))
                continue
            if has_name and parent_name and (current_long or current_short):
                if parent_name.lower() == current_long or parent_name.lower() == current_short:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Parent Regulatory Theme Name",
                        message="Parent cannot be the same as the current theme.",
                        error_code="PARENT_SELF_REFERENCE"
                    ))
                    continue

            parent_in_file = False
            if has_ref or has_name:
                parent_id_by_ref = get_regulatory_theme_id_by_ref_number(parent_ref) if (has_ref and parent_ref) else None
                parent_id_by_name = get_regulatory_theme_id_by_name(parent_name) if (has_name and parent_name) else None
                if has_ref and parent_ref and parent_id_by_ref is not None:
                    if parent_id_by_ref == theme_id:
                        errors.append(ValidationError(row=row_number, field="Parent Ref.", message="Parent cannot be the same as the current theme.", error_code="PARENT_SELF_REFERENCE"))
                        continue
                    parent_id = parent_id_by_ref
                if has_name and parent_name and parent_id_by_name is not None:
                    if parent_id_by_name == theme_id:
                        errors.append(ValidationError(row=row_number, field="Parent Regulatory Theme Name", message="Parent cannot be the same as the current theme.", error_code="PARENT_SELF_REFERENCE"))
                        continue
                    if parent_id is not None and parent_id != parent_id_by_name:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different regulatory themes",
                            error_code="PARENT_NAME_REF_MISMATCH"
                        ))
                        continue
                    if parent_id is None:
                        parent_id = parent_id_by_name

                if parent_id is None and has_ref and parent_ref:
                    if parent_ref.lower() not in refs_in_file:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message=f"Parent Regulatory Theme with reference '{parent_ref}' not found",
                            error_code="PARENT_REF_NOT_FOUND"
                        ))
                        continue
                    parent_in_file = True
                if parent_id is None and has_name and parent_name:
                    if parent_name.lower() not in names_in_file:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Regulatory Theme Name",
                            message=f"Parent Regulatory Theme with name '{parent_name}' not found",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                    parent_in_file = True

            # Validate BUDG Status if provided
            status_id = None
            if pd.notna(row.get("BUDG Status")):
                status_name = str(row.get("BUDG Status", "")).strip()
                if status_name:
                    if not validate_status(status_name):
                        errors.append(ValidationError(
                            row=row_number,
                            field="BUDG Status",
                            message=f"Invalid status '{status_name}'. Valid values are: {', '.join(VALID_STATUSES)}",
                            error_code="INVALID_STATUS"
                        ))
                        continue
                    
                    status_id = get_status_id_by_name(status_name)
                    if status_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="BUDG Status",
                            message=f"Status '{status_name}' not found in database",
                            error_code="STATUS_NOT_FOUND"
                        ))
                        continue
            
            # Description from row (optional for update)
            description = str(row.get("Description", "")).strip() if pd.notna(row.get("Description")) else ""
            
            # Add to validated data
            validated_row = {
                "operation": "UPDATE",
                "row_number": row_number,
                "Regulatory Theme ID": theme_id,
                "Regulatory Theme Long Name": theme_long_name,
                "Reference": str(row.get("Reference", "")).strip() if pd.notna(row.get("Reference")) else "",
                "Short Name": str(row.get("Short Name", "")).strip() if pd.notna(row.get("Short Name")) else "",
                "Description": description,
            }
            if parent_id is not None or parent_in_file:
                if pd.notna(row.get("Parent Regulatory Theme Name")):
                    validated_row["Parent Regulatory Theme Name"] = str(row.get("Parent Regulatory Theme Name", "")).strip()
                elif pd.notna(row.get("Parent Ref.")):
                    validated_row["Parent Ref."] = str(row.get("Parent Ref.", "")).strip()
            if status_id is not None:
                validated_row["BUDG Status"] = str(row.get("BUDG Status", "")).strip()
            
            # Validate and add custom fields
            if validate_custom_fields_for_row:
                try:
                    row_dict = {
                        str(col): (None if pd.isna(row.get(col)) else row.get(col))
                        for col in row.index
                    }
                    custom_field_errors, validated_custom_fields = validate_custom_fields_for_row(
                        row_dict, "Regulatory Theme", row_number
                    )
                    
                    # Add custom field errors to main errors list
                    for error in custom_field_errors:
                        errors.append(ValidationError(
                            row=error["row"],
                            field=error["field"],
                            message=error["message"],
                            error_code=error["error_code"]
                        ))
                    
                    # Add validated custom fields to validated_row
                    validated_row.update(validated_custom_fields)
                    if merge_excel_custom_field_passthrough and BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE:
                        bt = BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE.get("Regulatory Theme")
                        if bt is not None:
                            merge_excel_custom_field_passthrough(row, validated_row, bt)
                except Exception as e:
                    logger.error(f"Error validating custom fields for row {row_number}: {e}")
                    errors.append(ValidationError(
                        row=row_number,
                        field="Custom Fields",
                        message=f"Unexpected error while validating custom fields: {str(e)}",
                        error_code="CUSTOM_FIELD_VALIDATION_EXCEPTION",
                    ))

            validated_data.append(validated_row)

        elif upload_option == "Remove Existing Items":
            # Validate Regulatory Theme ID
            theme_id = row.get("Regulatory Theme ID")
            
            if pd.isna(theme_id):
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulatory Theme ID",
                    message="Regulatory Theme ID is required for delete operations",
                    error_code="MISSING_ID"
                ))
                continue
            
            try:
                theme_id = int(theme_id)
            except (ValueError, TypeError):
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulatory Theme ID",
                    message="Regulatory Theme ID must be a valid integer",
                    error_code="INVALID_ID"
                ))
                continue
            
            # For delete operations, check if ID exists
            # If it doesn't exist, we skip it (idempotent delete - already deleted)
            if not check_regulatory_theme_exists(theme_id):
                # Skip this row silently - it's already deleted
                # This allows idempotent delete operations
                continue
            
            # Add to validated data only if theme exists
            validated_data.append({
                "operation": "DELETE",
                "row_number": row_number,
                "Regulatory Theme ID": theme_id
            })
    
    return errors, validated_data


@app.get("/")
def read_root():
    """Health check endpoint"""
    return {
        "service": "Regulatory Theme Bulk Validation Service",
        "status": "running",
        "timestamp": datetime.now().isoformat()
    }


@app.post("/api/validate", response_model=ValidationResponse)
async def validate_bulk_upload(request: ValidationRequest):
    """
    Validate bulk upload Excel file for regulatory themes
    """
    try:
        logger.info(f"Validating file: {request.file_path} for operation: {request.upload_option}")
        
        # Check if file exists
        if not os.path.exists(request.file_path):
            raise HTTPException(status_code=404, detail=f"File not found: {request.file_path}")
        
        # Determine sheet name based on upload option
        sheet_name = get_sheet_name(request.upload_option)
        logger.info(f"Reading sheet: {sheet_name}")
        
        # Read Excel file with specific sheet
        try:
            df = read_excel(request.file_path, sheet_name=sheet_name, engine='openpyxl')
        except ValueError as e:
            # Sheet not found, try to list available sheets
            try:
                excel_file = open_excel_file(request.file_path, engine='openpyxl')
                available_sheets = excel_file.sheet_names
                logger.error(f"Sheet '{sheet_name}' not found. Available sheets: {available_sheets}")
                return ValidationResponse(
                    status="error",
                    message=f"Sheet '{sheet_name}' not found in Excel file. Available sheets: {', '.join(available_sheets)}",
                    total_rows=0,
                    valid_rows=0,
                    invalid_rows=0,
                    errors=[ValidationError(
                        row=0,
                        field="sheet",
                        message=f"Sheet '{sheet_name}' not found. Available sheets: {', '.join(available_sheets)}",
                        error_code="SHEET_NOT_FOUND"
                    )],
                    data=[]
                )
            except Exception as e2:
                logger.error(f"Error reading Excel file: {e2}")
                return ValidationResponse(
                    status="error",
                    message=f"Invalid Excel format: {str(e2)}",
                    total_rows=0,
                    valid_rows=0,
                    invalid_rows=0,
                    errors=[ValidationError(
                        row=0,
                        field="file",
                        message=f"Invalid Excel format: {str(e2)}",
                        error_code="INVALID_FILE_FORMAT"
                    )],
                    data=[]
                )
        except Exception as e:
            logger.error(f"Error reading Excel file: {e}")
            return ValidationResponse(
                status="error",
                message=f"Invalid Excel format: {str(e)}",
                total_rows=0,
                valid_rows=0,
                invalid_rows=0,
                errors=[ValidationError(
                    row=0,
                    field="file",
                    message=f"Invalid Excel format: {str(e)}",
                    error_code="INVALID_FILE_FORMAT"
                )],
                data=[]
            )
        
        # Check if file is empty
        if df.empty:
            return ValidationResponse(
                status="error",
                message="Excel file is empty",
                total_rows=0,
                valid_rows=0,
                invalid_rows=0,
                errors=[ValidationError(
                    row=0,
                    field="file",
                    message="No data rows found in Excel file",
                    error_code="EMPTY_FILE"
                )],
                data=[]
            )
        
        # Apply column mappings if provided (rename Excel columns to expected field names)
        if request.column_mappings:
            df = apply_column_mappings(df, request.column_mappings)
            logger.info(f"Column mappings applied. Columns after mapping: {list(df.columns)}")
        
        # Normalize Regulatory Theme column names (e.g. Parent Name -> Parent Regulatory Theme Name)
        df = normalize_regulatory_theme_columns(df, request.upload_option)
        
        total_rows = len(df)
        all_errors = []
        
        # Step 1: Validate column headers
        header_errors = validate_column_headers(df, request.upload_option)
        all_errors.extend(header_errors)
        
        # If header validation fails, return immediately
        if header_errors:
            return ValidationResponse(
                status="invalid",
                message="Column validation failed",
                total_rows=total_rows,
                valid_rows=0,
                invalid_rows=total_rows,
                errors=all_errors,
                data=[]
            )
        
        # Step 2: Validate row data
        row_errors, validated_data = validate_row_data(df, request.upload_option)
        all_errors.extend(row_errors)

        if filter_rows_with_blocking_custom_field_errors:
            validated_data = filter_rows_with_blocking_custom_field_errors(
                validated_data, _validation_errors_to_dicts(all_errors)
            )

        # Calculate valid and invalid rows
        valid_rows = len(validated_data)
        invalid_rows = total_rows - valid_rows
        
        # Determine overall status
        if all_errors:
            status = "invalid"
            message = f"Validation completed with {len(all_errors)} error(s)"
        else:
            status = "valid"
            message = "All rows validated successfully"
        
        logger.info(f"Validation complete: {valid_rows} valid, {invalid_rows} invalid out of {total_rows} total")
        
        return ValidationResponse(
            status=status,
            message=message,
            total_rows=total_rows,
            valid_rows=valid_rows,
            invalid_rows=invalid_rows,
            errors=all_errors,
            data=validated_data
        )
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Unexpected error during validation: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Internal server error: {str(e)}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

