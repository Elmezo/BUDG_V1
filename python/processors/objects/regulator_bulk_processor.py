"""
Regulator Bulk Upload Validation Service
FastAPI service that validates Excel files for bulk regulator uploads
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
from excel_read import read_excel
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
        merge_excel_custom_field_passthrough,
        BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE,
    )
except ImportError:
    validate_custom_fields_for_row = None
    merge_excel_custom_field_passthrough = None
    BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE = {}
    logging.warning("Could not import custom_fields_validator")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Regulator Bulk Validation Service")

# Database configuration from environment variables
DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USERNAME", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "database": os.getenv("DB_NAME", "project"),
    "charset": "utf8mb4"
}


class ValidationRequest(BaseModel):
    file_path: str
    upload_option: str  # "Add New Items" | "Update Existing Items" | "Remove Existing Items"
    entity: str  # "Regulator"
    user_id: int
    column_mappings: Optional[Dict[str, str]] = None  # Maps Excel column names to expected field names


class ValidationError(BaseModel):
    row: int
    field: str
    message: str
    error_code: str


class RegulatorRow(BaseModel):
    operation: str  # INSERT | UPDATE | DELETE
    row_number: int
    ID: Optional[int] = None
    PrimaryName: Optional[str] = None
    ShortName: Optional[str] = None
    Description: Optional[str] = None


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
    Return expected sheet name for Regulator bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Regulator"
    if upload_option == "Update Existing Items":
        return "Update Regulator"
    if upload_option == "Remove Existing Items":
        return "Delete Regulator"
    return None


def check_duplicate_primary_name(
    primary_name: str,
    exclude_id: Optional[int] = None,
    segment_id: Optional[int] = None,
) -> bool:
    """True if the same regulator primary name exists in the target segment."""
    if duplicate_name_in_facet_segment is None:
        try:
            conn = get_db_connection()
            cursor = conn.cursor()
            if exclude_id:
                query = "SELECT COUNT(*) FROM regulator WHERE LOWER(PrimaryName) = LOWER(%s) AND ID != %s AND DeletedDatetime IS NULL"
                cursor.execute(query, (primary_name, exclude_id))
            else:
                query = "SELECT COUNT(*) FROM regulator WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL"
                cursor.execute(query, (primary_name,))
            count = cursor.fetchone()[0]
            cursor.close()
            conn.close()
            return count > 0
        except Exception as e:
            logger.error(f"Error checking duplicate: {e}")
            raise
    sid = segment_id if segment_id is not None else 1
    return duplicate_name_in_facet_segment("Regulator", sid, primary_name, exclude_id)


def check_regulator_exists(regulator_id: int) -> bool:
    """
    Check if a regulator ID exists in the database
    Returns True if exists, False otherwise
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT COUNT(*) FROM regulator WHERE ID = %s AND DeletedDatetime IS NULL"
        cursor.execute(query, (regulator_id,))
        
        count = cursor.fetchone()[0]
        cursor.close()
        conn.close()
        
        return count > 0
    except Exception as e:
        logger.error(f"Error checking regulator existence: {e}")
        raise


def get_regulator_id_by_primary_name(primary_name: str) -> Optional[int]:
    """Get regulator ID by PrimaryName."""
    if not primary_name or not str(primary_name).strip():
        return None
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT ID FROM regulator WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL LIMIT 1", (str(primary_name).strip(),))
        row = cursor.fetchone()
        cursor.close()
        conn.close()
        return row[0] if row else None
    except Exception as e:
        logger.error(f"Error getting regulator ID by name: {e}")
        return None


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
    Example: {"a": "PrimaryName", "اسم المنظم": "PrimaryName", "الوصف": "Description"}
    
    Uses smart matching to find Excel columns even if they have slight variations:
    - Exact match: "a" matches "a"
    - Case-insensitive: "a" matches "A"
    - Partial match: "a" matches "a (example)" or "A Column"
    - Contains match: "prim" matches "PrimaryName" (if key is >= 3 chars)
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


def normalize_regulator_columns(df: pd.DataFrame, upload_option: str) -> pd.DataFrame:
    """
    Normalize column names to canonical form (ID, PrimaryName, ShortName) so that
    common aliases and case-insensitive variants are accepted before header validation.
    """
    if df.empty:
        return df
    columns_list = list(df.columns)
    rename_dict = {}
    # Only treat as "already present" if the column is exactly the canonical name (so we still rename case variants)
    has_id = any(str(c).strip() == "ID" for c in columns_list)
    has_primaryname = any(str(c).strip() == "PrimaryName" for c in columns_list)
    has_shortname = any(str(c).strip() == "ShortName" for c in columns_list)

    for col in columns_list:
        c = str(col).strip()
        c_lower = c.lower()
        if upload_option in ("Update Existing Items", "Remove Existing Items"):
            if c_lower in ("id", "regulator id") and not has_id:
                rename_dict[col] = "ID"
                has_id = True
            elif c_lower in ("primaryname", "primary name") and not has_primaryname:
                rename_dict[col] = "PrimaryName"
                has_primaryname = True
        if upload_option == "Add New Items":
            if c_lower in ("primaryname", "primary name") and not has_primaryname:
                rename_dict[col] = "PrimaryName"
                has_primaryname = True
            elif c_lower in ("shortname", "short name") and not has_shortname:
                rename_dict[col] = "ShortName"
                has_shortname = True

    if rename_dict:
        df = df.rename(columns=rename_dict)
        logger.info(f"Normalized Regulator columns: {list(rename_dict.keys())} -> {list(rename_dict.values())}")
    return df


def validate_column_headers(df: pd.DataFrame, upload_option: str) -> List[ValidationError]:
    """Validate that required columns exist based on operation type"""
    errors = []
    columns = [col.strip() for col in df.columns]
    
    if upload_option == "Add New Items":
        required = ["PrimaryName", "ShortName"]
        optional = ["Description"]
        
        for col in required:
            if col not in columns:
                errors.append(ValidationError(
                    row=0,
                    field="columns",
                    message=f"Missing required column: {col}",
                    error_code="MISSING_COLUMN"
                ))
    
    elif upload_option == "Update Existing Items":
        # At least one of ID or PrimaryName must be present as a column
        if "ID" not in columns and "PrimaryName" not in columns:
            errors.append(ValidationError(
                row=0,
                field="columns",
                message="At least one of ID or PrimaryName is required as a column",
                error_code="MISSING_COLUMN"
            ))
    
    elif upload_option == "Remove Existing Items":
        required = ["ID"]
        
        if "ID" not in columns:
            errors.append(ValidationError(
                row=0,
                field="columns",
                message="Missing required column: ID",
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
    
    for idx, row in df.iterrows():
        row_number = idx + 2  # +2 because Excel rows start at 1 and header is row 1
        row_errors = []
        
        if upload_option == "Add New Items":
            # Validate PrimaryName
            primary_name = str(row.get("PrimaryName", "")).strip() if pd.notna(row.get("PrimaryName")) else ""
            
            if not primary_name:
                errors.append(ValidationError(
                    row=row_number,
                    field="PrimaryName",
                    message="Primary name is required and cannot be empty",
                    error_code="EMPTY_PRIMARY_NAME"
                ))
                continue
            
            eff_seg = (
                resolve_effective_segment_id_for_duplicate_check(row, segment_mode, segment)
                if resolve_effective_segment_id_for_duplicate_check
                else 1
            )
            if check_duplicate_primary_name(primary_name, segment_id=eff_seg):
                errors.append(ValidationError(
                    row=row_number,
                    field="PrimaryName",
                    message=f"Regulator with name '{primary_name}' already exists in this segment",
                    error_code="DUPLICATE_NAME"
                ))
                continue
            
            # Validate ShortName (required)
            short_name = str(row.get("ShortName", "")).strip() if pd.notna(row.get("ShortName")) else ""
            if not short_name:
                errors.append(ValidationError(
                    row=row_number,
                    field="ShortName",
                    message="Short name is required and cannot be empty",
                    error_code="EMPTY_SHORT_NAME"
                ))
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
            
            # Add to validated data
            row_data = {
                "operation": "INSERT",
                "row_number": row_number,
                "PrimaryName": primary_name,
                "ShortName": short_name,
                "Description": str(row.get("Description", "")).strip() if pd.notna(row.get("Description")) else ""
            }
            
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
            if validate_custom_fields_for_row:
                try:
                    row_dict = {
                        str(col): (None if pd.isna(row.get(col)) else row.get(col))
                        for col in row.index
                    }
                    custom_field_errors, validated_custom_fields = validate_custom_fields_for_row(
                        row_dict, "Regulator", row_number
                    )
                    
                    # Add custom field errors to main errors list
                    for error in custom_field_errors:
                        errors.append(ValidationError(
                            row=error["row"],
                            field=error["field"],
                            message=error["message"],
                            error_code=error["error_code"]
                        ))
                    
                    # Add validated custom fields to row_data
                    row_data.update(validated_custom_fields)
                    if merge_excel_custom_field_passthrough and BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE:
                        bt = BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE.get("Regulator")
                        if bt is not None:
                            merge_excel_custom_field_passthrough(row, row_data, bt)
                except Exception as e:
                    logger.error(f"Error validating custom fields for row {row_number}: {e}")
                    # Don't fail the row if custom fields validation fails, just log it
            
            validated_data.append(row_data)
        
        elif upload_option == "Update Existing Items":
            def _has_val(v):
                if v is None: return False
                if pd.isna(v): return False
                s = str(v).strip()
                return bool(s) and s.lower() not in ("nan", "")
            id_raw = row.get("ID")
            name_raw = row.get("PrimaryName")
            id_by_id = None
            if _has_val(id_raw):
                try:
                    vid = int(float(str(id_raw).strip()))
                    if check_regulator_exists(vid):
                        id_by_id = vid
                except (ValueError, TypeError):
                    pass
            id_by_name = get_regulator_id_by_primary_name(str(name_raw).strip()) if _has_val(name_raw) else None
            filled = sum(1 for v in (id_raw, name_raw) if _has_val(v))
            if filled == 0:
                errors.append(ValidationError(row=row_number, field="ID", message="At least one of ID or PrimaryName is required for update", error_code="MISSING_ID"))
                continue
            regulator_id = id_by_id or id_by_name
            if regulator_id is None:
                errors.append(ValidationError(row=row_number, field="ID", message="No regulator found for the provided identity (ID or PrimaryName)", error_code="ID_NOT_FOUND"))
                continue
            if filled >= 2:
                ids = {x for x in (id_by_id, id_by_name) if x is not None}
                if len(ids) > 1:
                    errors.append(ValidationError(row=row_number, field="ID", message="ID and PrimaryName refer to different regulators", error_code="IDENTITY_MISMATCH"))
                    continue
            
            # Check if ID exists
            if not check_regulator_exists(regulator_id):
                errors.append(ValidationError(
                    row=row_number,
                    field="ID",
                    message=f"Regulator with ID {regulator_id} does not exist",
                    error_code="ID_NOT_FOUND"
                ))
                continue
            
            # PrimaryName is optional when ID is provided: if empty, Java will keep existing name
            primary_name = str(row.get("PrimaryName", "")).strip() if pd.notna(row.get("PrimaryName")) else ""
            
            if primary_name:
                eff_seg_upd = 1
                if get_object_segment_id:
                    try:
                        osid = get_object_segment_id(regulator_id, "Regulator")
                        if osid is not None and osid > 0:
                            eff_seg_upd = int(osid)
                    except Exception:
                        pass
                if check_duplicate_primary_name(primary_name, regulator_id, segment_id=eff_seg_upd):
                    errors.append(ValidationError(
                        row=row_number,
                        field="PrimaryName",
                        message=f"Another regulator with name '{primary_name}' already exists in this segment",
                        error_code="DUPLICATE_NAME"
                    ))
                    continue
            
            # Prepare update data (PrimaryName can be empty; Java keeps existing name when empty)
            row_data_for_update = {
                "operation": "UPDATE",
                "row_number": row_number,
                "ID": regulator_id,
                "PrimaryName": primary_name,
                "ShortName": str(row.get("ShortName", "")).strip() if pd.notna(row.get("ShortName")) else "",
                "Description": str(row.get("Description", "")).strip() if pd.notna(row.get("Description")) else ""
            }
            
            # Validate and add custom fields
            if validate_custom_fields_for_row:
                try:
                    row_dict = {
                        str(col): (None if pd.isna(row.get(col)) else row.get(col))
                        for col in row.index
                    }
                    custom_field_errors, validated_custom_fields = validate_custom_fields_for_row(
                        row_dict, "Regulator", row_number
                    )
                    
                    # Add custom field errors to main errors list
                    for error in custom_field_errors:
                        errors.append(ValidationError(
                            row=error["row"],
                            field=error["field"],
                            message=error["message"],
                            error_code=error["error_code"]
                        ))
                    
                    # Add validated custom fields
                    row_data_for_update.update(validated_custom_fields)
                    if merge_excel_custom_field_passthrough and BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE:
                        bt = BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE.get("Regulator")
                        if bt is not None:
                            merge_excel_custom_field_passthrough(row, row_data_for_update, bt)
                except Exception as e:
                    logger.error(f"Error validating custom fields for row {row_number}: {e}")
            
            # Add to validated data
            validated_data.append(row_data_for_update)
        
        elif upload_option == "Remove Existing Items":
            # Validate ID (allow Excel numeric formats: int, float 1.0, or string "1"/"1.0")
            id_raw = row.get("ID")

            if pd.isna(id_raw) or id_raw == "" or (isinstance(id_raw, str) and not str(id_raw).strip()):
                errors.append(ValidationError(
                    row=row_number,
                    field="ID",
                    message="Regulator ID is required for delete operations",
                    error_code="MISSING_ID"
                ))
                continue

            try:
                # Strip and coerce: int(1.0), int("1"), int("1.0") all become 1
                regulator_id = int(float(str(id_raw).strip()))
            except (ValueError, TypeError):
                errors.append(ValidationError(
                    row=row_number,
                    field="ID",
                    message="Regulator ID must be a valid integer",
                    error_code="INVALID_ID"
                ))
                continue
            
            # For delete operations, check if ID exists
            # If it doesn't exist, we skip it (idempotent delete - already deleted)
            if not check_regulator_exists(regulator_id):
                # Skip this row silently - it's already deleted
                # This allows idempotent delete operations
                continue
            
            # Add to validated data only if regulator exists
            validated_data.append({
                "operation": "DELETE",
                "row_number": row_number,
                "ID": regulator_id
            })
    
    return errors, validated_data


@app.get("/")
def read_root():
    """Health check endpoint"""
    return {
        "service": "Regulator Bulk Validation Service",
        "status": "running",
        "timestamp": datetime.now().isoformat()
    }


@app.post("/api/validate", response_model=ValidationResponse)
async def validate_bulk_upload(request: ValidationRequest):
    """
    Validate bulk upload Excel file for regulators
    """
    try:
        logger.info(f"Validating file: {request.file_path} for operation: {request.upload_option}")
        
        # Check if file exists
        if not os.path.exists(request.file_path):
            raise HTTPException(status_code=404, detail=f"File not found: {request.file_path}")
        
        # Read Excel file
        try:
            df = read_excel(request.file_path, engine='openpyxl')
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
        
        # Normalize Regulator column names (ID, PrimaryName, ShortName aliases) before header validation
        df = normalize_regulator_columns(df, request.upload_option)
        
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


