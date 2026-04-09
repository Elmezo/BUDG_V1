"""
Geography Bulk Upload Validation Service
FastAPI service that validates Excel files for bulk geography uploads
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import pandas as pd
import pymysql
import os
import sys
import logging
from datetime import datetime

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
        CUSTOM_FIELD_BLOCKING_ERROR_CODES,
        filter_rows_with_blocking_custom_field_errors,
        merge_excel_custom_field_passthrough,
        BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE,
    )
except ImportError:
    validate_custom_fields_for_row = None
    merge_excel_custom_field_passthrough = None
    BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE = {}
    CUSTOM_FIELD_BLOCKING_ERROR_CODES = frozenset({
        "CUSTOM_FIELD_MANDATORY",
        "CUSTOM_FIELD_VALIDATION_ERROR",
        "CUSTOM_FIELD_VALIDATION_EXCEPTION",
    })

    def filter_rows_with_blocking_custom_field_errors(vd, ae):  # type: ignore
        return vd

    logging.warning("Could not import custom_fields_validator")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Geography Bulk Validation Service")

# Database configuration from environment variables
DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USERNAME", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "database": os.getenv("DB_NAME", "project"),
    "charset": "utf8mb4"
}


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
        if hasattr(e, "model_dump"):
            d = e.model_dump()
        elif hasattr(e, "dict"):
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


class ValidationRequest(BaseModel):
    file_path: str
    upload_option: str  # "Add New Items" | "Update Existing Items" | "Remove Existing Items"
    entity: str  # "Geography"
    user_id: int
    column_mappings: Optional[Dict[str, str]] = None  # Maps Excel column names to expected field names
    segment_mode: Optional[str] = None
    segment: Optional[str] = None


class ValidationError(BaseModel):
    row: int
    field: str
    message: str
    error_code: str


class GeographyRow(BaseModel):
    operation: str  # INSERT | UPDATE | DELETE
    row_number: int
    Geography_ID: Optional[int] = None
    Geography_Name: Optional[str] = None
    Geography_Definition: Optional[str] = None
    Parent_Geography_Name: Optional[str] = None


class ValidationResponse(BaseModel):
    status: str  # "valid" | "invalid" | "error"
    message: str
    total_rows: int
    valid_rows: int
    invalid_rows: int
    errors: List[ValidationError]
    data: List[Dict[str, Any]]


def get_db_connection():
    """Create a database connection"""
    try:
        return pymysql.connect(**DB_CONFIG)
    except Exception as e:
        logger.error(f"Database connection failed: {e}")
        raise


def get_sheet_name(upload_option: str) -> Optional[str]:
    """
    Return expected sheet name for Geography bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Geography"
    if upload_option == "Update Existing Items":
        return "Update Geography"
    if upload_option == "Remove Existing Items":
        return "Delete Geography"
    return None


def check_duplicate_primary_name(
    primary_name: str,
    exclude_id: Optional[int] = None,
    segment_id: Optional[int] = None,
) -> bool:
    """True if the same geography name exists in the target segment."""
    if duplicate_name_in_facet_segment is None:
        try:
            conn = get_db_connection()
            cursor = conn.cursor()
            if exclude_id:
                query = "SELECT COUNT(*) FROM geography WHERE LOWER(PrimaryName) = LOWER(%s) AND ID != %s AND DeletedDatetime IS NULL"
                cursor.execute(query, (primary_name, exclude_id))
            else:
                query = "SELECT COUNT(*) FROM geography WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL"
                cursor.execute(query, (primary_name,))
            count = cursor.fetchone()[0]
            cursor.close()
            conn.close()
            return count > 0
        except Exception as e:
            logger.error(f"Error checking duplicate: {e}")
            raise
    sid = segment_id if segment_id is not None else 1
    return duplicate_name_in_facet_segment("Geography", sid, primary_name, exclude_id)


def check_geography_exists(geography_id: int) -> bool:
    """
    Check if a geography ID exists in the database
    Returns True if exists, False otherwise
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT COUNT(*) FROM geography WHERE ID = %s AND DeletedDatetime IS NULL"
        cursor.execute(query, (geography_id,))
        
        count = cursor.fetchone()[0]
        cursor.close()
        conn.close()
        
        return count > 0
    except Exception as e:
        logger.error(f"Error checking geography existence: {e}")
        raise


def get_geography_id_by_name(name: str) -> Optional[int]:
    """
    Get Geography ID by PrimaryName
    Returns ID if found, None otherwise
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT ID FROM geography WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL LIMIT 1"
        cursor.execute(query, (name.strip(),))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting geography ID by name: {e}")
        raise


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
    Example: {"a": "Geography Name", "اسم الجغرافيا": "Geography Name", "التعريف": "Geography Definition"}
    
    Uses smart matching to find Excel columns even if they have slight variations:
    - Exact match: "a" matches "a"
    - Case-insensitive: "a" matches "A"
    - Partial match: "a" matches "a (example)" or "A Column"
    - Contains match: "geo" matches "Geography Name" (if key is >= 3 chars)
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


def validate_column_headers(df: pd.DataFrame, upload_option: str) -> List[ValidationError]:
    """Validate that required columns exist based on operation type"""
    errors = []
    columns = [col.strip() for col in df.columns]
    
    if upload_option == "Add New Items":
        required = ["Geography Name"]
        optional = ["Geography Definition", "Parent Geography Name"]
        
        for col in required:
            if col not in columns:
                errors.append(ValidationError(
                    row=0,
                    field="columns",
                    message=f"Missing required column: {col}",
                    error_code="MISSING_COLUMN"
                ))
    
    elif upload_option == "Update Existing Items":
        required = ["Geography ID", "Geography Name"]
        optional = ["Geography Definition", "Parent Geography Name"]
        
        for col in required:
            if col not in columns:
                errors.append(ValidationError(
                    row=0,
                    field="columns",
                    message=f"Missing required column: {col}",
                    error_code="MISSING_COLUMN"
                ))
    
    elif upload_option == "Remove Existing Items":
        required = ["Geography ID"]
        
        if "Geography ID" not in columns:
            errors.append(ValidationError(
                row=0,
                field="columns",
                message="Missing required column: Geography ID",
                error_code="MISSING_COLUMN"
            ))
    
    return errors


def validate_row_data(df: pd.DataFrame, upload_option: str, segment_mode: Optional[str] = None, segment: Optional[str] = None, user_id: Optional[int] = None) -> tuple[List[ValidationError], List[Dict[str, Any]]]:
    """
    Validate each row of data
    Returns (errors, validated_data)
    """
    errors = []
    validated_data = []
    
    if not df.empty:
        non_empty_idx = [i for i in df.index if not _is_effectively_empty_excel_row(df.loc[i])]
        df = df.loc[non_empty_idx] if non_empty_idx else df.iloc[0:0]
    
    # Geography + parent names in file (Java resolves parent from batch when parent appears in same upload)
    names_in_file = set()
    for col in ("Geography Name", "Parent Geography Name"):
        if col in df.columns:
            for v in df[col].dropna():
                s = str(v).strip()
                if s:
                    names_in_file.add(s.lower())
    
    for idx, row in df.iterrows():
        row_number = idx + 2  # +2 because Excel rows start at 1 and header is row 1
        row_errors = []
        
        if upload_option == "Add New Items":
            # Validate Geography Name
            geography_name = str(row.get("Geography Name", "")).strip() if pd.notna(row.get("Geography Name")) else ""
            
            if not geography_name:
                errors.append(ValidationError(
                    row=row_number,
                    field="Geography Name",
                    message="Geography name is required and cannot be empty",
                    error_code="EMPTY_GEOGRAPHY_NAME"
                ))
                continue
            
            eff_seg = (
                resolve_effective_segment_id_for_duplicate_check(row, segment_mode, segment)
                if resolve_effective_segment_id_for_duplicate_check
                else 1
            )
            if check_duplicate_primary_name(geography_name, segment_id=eff_seg):
                errors.append(ValidationError(
                    row=row_number,
                    field="Geography Name",
                    message=f"Geography with name '{geography_name}' already exists in this segment",
                    error_code="DUPLICATE_NAME"
                ))
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
                                                errors.append(ValidationError(row=row_number, field="Segment", message=f"User does not have access to selected segment '{selected_segment_name}'", error_code="SEGMENT_ACCESS_DENIED"))
                                            # If accessible, use it (will be set in row_data below)
                                        else:
                                            errors.append(ValidationError(row=row_number, field="Segment", message=f"Selected segment ID '{segment}' does not exist", error_code="SEGMENT_NOT_FOUND"))
                                    else:
                                        # Cannot validate by ID, require segment in Excel
                                        errors.append(ValidationError(row=row_number, field="Segment", message="Segment is required when 'Multiple' mode is selected", error_code="SEGMENT_REQUIRED"))
                                except ValueError:
                                    # segment is not a valid ID, require segment in Excel
                                    errors.append(ValidationError(row=row_number, field="Segment", message="Segment is required when 'Multiple' mode is selected", error_code="SEGMENT_REQUIRED"))
                            else:
                                # No validation functions available, require segment in Excel
                                errors.append(ValidationError(row=row_number, field="Segment", message="Segment is required when 'Multiple' mode is selected", error_code="SEGMENT_REQUIRED"))
                        else:
                            # No segment in Excel and no selectedSegment in UI
                            errors.append(ValidationError(row=row_number, field="Segment", message="Segment is required when 'Multiple' mode is selected", error_code="SEGMENT_REQUIRED"))
                    else:
                        # Segment specified in Excel - validate it
                        # Rule C: Segment must exist and user must have access
                        if validate_segment_exists and user_id:
                            exists, segment_id = validate_segment_exists(segment_name)
                            if not exists:
                                errors.append(ValidationError(row=row_number, field="Segment", message=f"Segment '{segment_name}' does not exist", error_code="SEGMENT_NOT_FOUND"))
                            else:
                                has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                if not has_access:
                                    errors.append(ValidationError(row=row_number, field="Segment", message=f"User does not have access to segment '{segment_name}'", error_code="SEGMENT_ACCESS_DENIED"))
                elif segment_mode == "ENTERPRISE":
                    # Segment column is optional, but if provided, validate it
                    if segment_name and segment_name != "" and segment_name.lower() != "enterprise":
                        # Allow empty or "Enterprise", but validate if something else is provided
                        if validate_segment_exists:
                            exists, segment_id = validate_segment_exists(segment_name)
                            if not exists:
                                errors.append(ValidationError(row=row_number, field="Segment", message=f"Segment '{segment_name}' does not exist", error_code="SEGMENT_NOT_FOUND"))
                            elif user_id and validate_user_segment_access:
                                has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                if not has_access:
                                    errors.append(ValidationError(row=row_number, field="Segment", message=f"User does not have access to segment '{segment_name}'", error_code="SEGMENT_ACCESS_DENIED"))
                elif segment_mode == "SPECIFIC":
                    # SPECIFIC mode: If segment is provided in Excel, use it (overrides UI), otherwise use selectedSegment from UI
                    if segment_name and segment_name != "":
                        # Segment specified in Excel - use it (overrides UI selection)
                        # Rule C: Validate segment exists and user has access
                        if validate_segment_exists and user_id:
                            exists, segment_id = validate_segment_exists(segment_name)
                            if not exists:
                                errors.append(ValidationError(row=row_number, field="Segment", message=f"Segment '{segment_name}' does not exist", error_code="SEGMENT_NOT_FOUND"))
                            else:
                                has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                if not has_access:
                                    errors.append(ValidationError(row=row_number, field="Segment", message=f"User does not have access to segment '{segment_name}'", error_code="SEGMENT_ACCESS_DENIED"))
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
                                                errors.append(ValidationError(row=row_number, field="Segment", message=f"User does not have access to selected segment '{selected_segment_name}'", error_code="SEGMENT_ACCESS_DENIED"))
                                            # If accessible, will use it (Java will use selectedSegment parameter)
                                        else:
                                            errors.append(ValidationError(row=row_number, field="Segment", message=f"Selected segment ID '{segment}' does not exist", error_code="SEGMENT_NOT_FOUND"))
                                    # If no get_segment_name_by_id function, Java will handle validation
                                except ValueError:
                                    # segment is not a valid ID
                                    errors.append(ValidationError(row=row_number, field="Segment", message=f"Invalid selected segment ID: '{segment}'", error_code="SEGMENT_INVALID"))
                            # If no validation functions, Java will handle it
                        # If no segment in Excel and no selectedSegment in UI, Java will throw error
            
            # Validate Parent Geography Name if provided (DB or same file as child)
            parent_geography_name = None
            if pd.notna(row.get("Parent Geography Name")):
                parent_name = str(row.get("Parent Geography Name", "")).strip()
                if parent_name:
                    if parent_name.lower() in names_in_file:
                        parent_geography_name = parent_name
                    else:
                        parent_id = get_geography_id_by_name(parent_name)
                        if parent_id is None:
                            errors.append(ValidationError(
                                row=row_number,
                                field="Parent Geography Name",
                                message=f"Parent Geography with name '{parent_name}' not found",
                                error_code="PARENT_NOT_FOUND"
                            ))
                            continue
                        parent_geography_name = parent_name
            
            # Add to validated data
            validated_row = {
                "operation": "INSERT",
                "row_number": row_number,
                "Geography Name": geography_name,
                "Geography Definition": str(row.get("Geography Definition", "")).strip() if pd.notna(row.get("Geography Definition")) else "",
            }
            if parent_geography_name:
                validated_row["Parent Geography Name"] = parent_geography_name
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
            # Validate and add custom fields (full row dict so empty mandatory cells are detected)
            if validate_custom_fields_for_row:
                try:
                    row_dict = {
                        str(col): (None if pd.isna(row.get(col)) else row.get(col))
                        for col in row.index
                    }
                    _eb = len(errors)
                    custom_field_errors, validated_custom_fields = validate_custom_fields_for_row(
                        row_dict, "Geography", row_number
                    )
                    
                    for error in custom_field_errors:
                        errors.append(ValidationError(
                            row=error["row"],
                            field=error["field"],
                            message=error["message"],
                            error_code=error["error_code"]
                        ))
                    
                    validated_row.update(validated_custom_fields)
                    if merge_excel_custom_field_passthrough and BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE:
                        bt = BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE.get("Geography")
                        if bt is not None:
                            merge_excel_custom_field_passthrough(row, validated_row, bt)
                    if _row_has_blocking_cf_errors_since(errors, _eb, row_number):
                        continue
                except Exception as e:
                    logger.error(f"Error validating custom fields for row {row_number}: {e}")
                    errors.append(ValidationError(
                        row=row_number,
                        field="Custom Fields",
                        message=f"Unexpected error while validating custom fields: {str(e)}",
                        error_code="CUSTOM_FIELD_VALIDATION_EXCEPTION"
                    ))
                    continue
            
            validated_data.append(validated_row)
        
        elif upload_option == "Update Existing Items":
            def _has_val(v):
                if v is None: return False
                if pd.isna(v): return False
                s = str(v).strip()
                return bool(s) and s.lower() not in ("nan", "")
            id_raw = row.get("Geography ID")
            name_raw = row.get("Geography Name")
            id_by_id = None
            if _has_val(id_raw):
                try:
                    vid = int(float(str(id_raw).strip()))
                    if check_geography_exists(vid):
                        id_by_id = vid
                except (ValueError, TypeError):
                    pass
            id_by_name = get_geography_id_by_name(str(name_raw).strip()) if _has_val(name_raw) else None
            filled = sum(1 for v in (id_raw, name_raw) if _has_val(v))
            if filled == 0:
                errors.append(ValidationError(row=row_number, field="Geography ID", message="At least one of Geography ID or Geography Name is required for update", error_code="MISSING_ID"))
                continue
            geography_id = id_by_id or id_by_name
            if geography_id is None:
                errors.append(ValidationError(row=row_number, field="Geography ID", message="No geography found for the provided identity (Geography ID or Geography Name)", error_code="ID_NOT_FOUND"))
                continue
            if filled >= 2:
                ids = {x for x in (id_by_id, id_by_name) if x is not None}
                if len(ids) > 1:
                    errors.append(ValidationError(row=row_number, field="Geography ID", message="Geography ID and Geography Name refer to different geographies", error_code="IDENTITY_MISMATCH"))
                    continue
            
            # Geography Name is optional for Update; if provided, must not duplicate another
            geography_name = str(row.get("Geography Name", "")).strip() if pd.notna(row.get("Geography Name")) else ""
            
            eff_seg_upd = 1
            if get_object_segment_id:
                try:
                    osid = get_object_segment_id(geography_id, "Geography")
                    if osid is not None and osid > 0:
                        eff_seg_upd = int(osid)
                except Exception:
                    pass
            if geography_name and check_duplicate_primary_name(geography_name, geography_id, segment_id=eff_seg_upd):
                errors.append(ValidationError(
                    row=row_number,
                    field="Geography Name",
                    message=f"Another geography with name '{geography_name}' already exists in this segment",
                    error_code="DUPLICATE_NAME"
                ))
                continue
            
            # Validate Parent Geography Name if provided
            parent_geography_name = None
            if pd.notna(row.get("Parent Geography Name")):
                parent_name = str(row.get("Parent Geography Name", "")).strip()
                if parent_name:
                    if parent_name.lower() in names_in_file:
                        parent_geography_name = parent_name  # Java will resolve from batch
                    else:
                        parent_id = get_geography_id_by_name(parent_name)
                        if parent_id is None:
                            errors.append(ValidationError(
                                row=row_number,
                                field="Parent Geography Name",
                                message=f"Parent Geography with name '{parent_name}' not found",
                                error_code="PARENT_NOT_FOUND"
                            ))
                            continue
                        parent_geography_name = parent_name
            
            # Add to validated data
            validated_row = {
                "operation": "UPDATE",
                "row_number": row_number,
                "Geography ID": geography_id,
                "Geography Name": geography_name,
                "Geography Definition": str(row.get("Geography Definition", "")).strip() if pd.notna(row.get("Geography Definition")) else "",
            }
            if parent_geography_name:
                validated_row["Parent Geography Name"] = parent_geography_name
            # Pass Segment to Java when row has a Segment value (so segment change is validated and applied or fails)
            segment_value = row.get("Segment")
            segment_name = str(segment_value).strip() if segment_value is not None and not pd.isna(segment_value) else None
            if segment_name:
                validated_row["Segment"] = segment_name
            # Validate and add custom fields
            if validate_custom_fields_for_row:
                try:
                    row_dict = {
                        str(col): (None if pd.isna(row.get(col)) else row.get(col))
                        for col in row.index
                    }
                    _eb = len(errors)
                    custom_field_errors, validated_custom_fields = validate_custom_fields_for_row(
                        row_dict, "Geography", row_number
                    )
                    
                    for error in custom_field_errors:
                        errors.append(ValidationError(
                            row=error["row"],
                            field=error["field"],
                            message=error["message"],
                            error_code=error["error_code"]
                        ))
                    
                    validated_row.update(validated_custom_fields)
                    if merge_excel_custom_field_passthrough and BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE:
                        bt = BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE.get("Geography")
                        if bt is not None:
                            merge_excel_custom_field_passthrough(row, validated_row, bt)
                    if _row_has_blocking_cf_errors_since(errors, _eb, row_number):
                        continue
                except Exception as e:
                    logger.error(f"Error validating custom fields for row {row_number}: {e}")
                    errors.append(ValidationError(
                        row=row_number,
                        field="Custom Fields",
                        message=f"Unexpected error while validating custom fields: {str(e)}",
                        error_code="CUSTOM_FIELD_VALIDATION_EXCEPTION"
                    ))
                    continue
            
            validated_data.append(validated_row)
        
        elif upload_option == "Remove Existing Items":
            # Validate Geography ID
            geography_id = row.get("Geography ID")
            
            if pd.isna(geography_id):
                errors.append(ValidationError(
                    row=row_number,
                    field="Geography ID",
                    message="Geography ID is required for delete operations",
                    error_code="MISSING_ID"
                ))
                continue
            
            try:
                geography_id = int(geography_id)
            except (ValueError, TypeError):
                errors.append(ValidationError(
                    row=row_number,
                    field="Geography ID",
                    message="Geography ID must be a valid integer",
                    error_code="INVALID_ID"
                ))
                continue
            
            # For delete operations, check if ID exists
            # If it doesn't exist, we skip it (idempotent delete - already deleted)
            if not check_geography_exists(geography_id):
                # Skip this row silently - it's already deleted
                # This allows idempotent delete operations
                continue
            
            # Add to validated data only if geography exists
            validated_data.append({
                "operation": "DELETE",
                "row_number": row_number,
                "Geography ID": geography_id
            })
    
    return errors, validated_data


@app.get("/")
def read_root():
    """Health check endpoint"""
    return {
        "service": "Geography Bulk Validation Service",
        "status": "running",
        "timestamp": datetime.now().isoformat()
    }


@app.post("/api/validate", response_model=ValidationResponse)
async def validate_bulk_upload(request: ValidationRequest):
    """
    Validate bulk upload Excel file for geographies
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
        
        # Step 2: Validate row data (segment + user_id match Java / unified bulk service)
        row_errors, validated_data = validate_row_data(
            df,
            request.upload_option,
            request.segment_mode,
            request.segment,
            request.user_id,
        )
        all_errors.extend(row_errors)
        validated_data = filter_rows_with_blocking_custom_field_errors(validated_data, all_errors)
        row_nums = set()
        for d in validated_data:
            if isinstance(d, dict) and d.get("row_number") is not None:
                try:
                    row_nums.add(int(d["row_number"]))
                except (TypeError, ValueError):
                    pass
        for e in all_errors:
            d = None
            if isinstance(e, dict):
                d = e
            elif hasattr(e, "model_dump"):
                d = e.model_dump()
            elif hasattr(e, "dict"):
                d = e.dict()
            if d and d.get("row") is not None:
                try:
                    row_nums.add(int(d["row"]))
                except (TypeError, ValueError):
                    pass
        if row_nums:
            total_rows = len(row_nums)
        elif not validated_data and not all_errors:
            total_rows = 0
        
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

