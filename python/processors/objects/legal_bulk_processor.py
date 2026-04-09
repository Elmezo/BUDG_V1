"""
Legal Entity Bulk Upload Validation Module
Validates Excel files for Legal Entity bulk uploads and resolves lookups to IDs.
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
    Return expected sheet name for Legal bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Legal"
    if upload_option == "Update Existing Items":
        return "Update Legal"
    if upload_option == "Remove Existing Items":
        return "Delete Legal"
    return None


def apply_column_mappings(df: pd.DataFrame, column_mappings: Optional[Dict[str, str]]) -> pd.DataFrame:
    """
    Apply column mappings to rename Excel columns to expected field names.
    This allows users to map any Excel column name to the expected BUDG field names.
    
    Args:
        df: DataFrame with Excel data
        column_mappings: Dict mapping Excel column names to expected field names
                        Format: {"Excel Column Name": "Expected Field Name"}
    
    Returns:
        DataFrame with renamed columns
    """
    if not column_mappings:
        logger.info("No column mappings provided, skipping mapping step")
        return df
    
    logger.info(f"🔧 Applying column mappings for Legal Entity. Received {len(column_mappings)} mappings")
    logger.info(f"📋 Column mappings received: {column_mappings}")
    logger.info(f"📊 DataFrame columns before mapping: {list(df.columns)}")
    logger.info(f"📏 DataFrame shape: {df.shape}")
    
    rename_dict = {}
    
    for excel_col, expected_field in column_mappings.items():
        # Skip empty mappings
        if not excel_col or not expected_field or not str(excel_col).strip() or not str(expected_field).strip():
            logger.warning(f"⚠️ Skipping empty mapping: excel_col='{excel_col}', expected_field='{expected_field}'")
            continue
        
        excel_col_normalized = str(excel_col).strip()
        expected_field_normalized = str(expected_field).strip()
        
        # Check if the Excel column exists in the DataFrame
        if excel_col_normalized in df.columns:
            rename_dict[excel_col_normalized] = expected_field_normalized
            logger.info(f"✓ Will map: '{excel_col_normalized}' -> '{expected_field_normalized}'")
        else:
            logger.warning(f"✗ Excel column '{excel_col_normalized}' NOT found in DataFrame columns: {list(df.columns)}")
    
    if rename_dict:
        logger.info(f"🔄 Applying rename dictionary: {rename_dict}")
        df = df.rename(columns=rename_dict)
        logger.info(f"✅ Applied {len(rename_dict)} column mappings successfully")
        logger.info(f"📊 DataFrame columns after mapping: {list(df.columns)}")
        
        # Log sample data from first row (if exists)
        if not df.empty:
            logger.info(f"📄 First row data after mapping: {df.iloc[0].to_dict()}")
    else:
        logger.warning("⚠️ No column mappings could be applied - rename_dict is empty!")
    
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


def get_legal_id_by_short_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM legal WHERE LOWER(ShortName) = LOWER(%s) LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_legal_id_by_short_name error: %s", e)
        return None


def get_legal_id_by_long_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM legal WHERE LOWER(LongName) = LOWER(%s) LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_legal_id_by_long_name error: %s", e)
        return None


def _legal_parent_cell(row: pd.Series) -> Optional[str]:
    """Template uses 'Parent Name'; entity config uses 'Parent Short Name'; legacy uses 'Parent Legal Entity Name'."""
    for key in ("Parent Legal Entity Name", "Parent Short Name", "Parent Name"):
        if key not in row.index:
            continue
        v = row.get(key)
        if v is None or pd.isna(v):
            continue
        s = str(v).strip()
        if s and s.lower() not in ("nan",):
            return s
    return None


def validate_and_resolve_legal(df: pd.DataFrame, upload_option: str, user_id: int, segment_mode: Optional[str] = None, segment: Optional[str] = None) -> Tuple[List[Dict[str, Any]], List[ValidationError]]:
    """
    Validate and resolve Legal Entity records from DataFrame.
    Returns: (validated_data, errors)
    """
    validated_data = []
    errors = []
    
    operation = "INSERT"
    if upload_option == "Update Existing Items":
        operation = "UPDATE"
    elif upload_option == "Remove Existing Items":
        operation = "DELETE"
    
    # Build set of names in file so parent "in same file" is allowed (templates may use spaced or camelCase headers)
    names_in_file = set()
    for col in ("Short Name", "Long Name", "ShortName", "LongName"):
        if col in df.columns:
            for v in df[col].dropna():
                s = str(v).strip()
                if s and s.lower() not in ("nan",):
                    names_in_file.add(s.lower())

    for idx, row in df.iterrows():
        row_number = idx + 2  # Excel row number (header is row 1)
        row_data = {"row_number": row_number, "operation": operation}
        
        try:
            if operation == "DELETE":
                # DELETE: Only need ID
                entity_id = row.get("Legal ID") or row.get("ID")
                if pd.isna(entity_id):
                    errors.append(ValidationError(row_number, "Legal ID", "Legal ID is required for deletion", "MISSING_REQUIRED"))
                    continue
                
                row_data["ID"] = int(entity_id)
                
                # Check if exists
                if not _exists_in_table("legal", row_data["ID"]):
                    errors.append(ValidationError(row_number, "Legal ID", f"Legal Entity with ID {row_data['ID']} does not exist", "NOT_FOUND"))
                    continue
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "Legal", row_number, errors)
                
                validated_data.append(row_data)
                
            elif operation == "UPDATE":
                def _has_val(v):
                    if v is None or (hasattr(v, '__len__') and not v): return False
                    if pd.isna(v): return False
                    s = str(v).strip()
                    return bool(s) and s.lower() not in ("nan", "")
                id_raw = row.get("Legal ID") or row.get("ID")
                short_raw = row.get("Short Name") or row.get("ShortName")
                long_raw = row.get("Long Name") or row.get("LongName")
                id_by_id = None
                if _has_val(id_raw):
                    try:
                        vid = int(float(str(id_raw).strip()))
                        if _exists_in_table("legal", vid):
                            id_by_id = vid
                    except (ValueError, TypeError):
                        pass
                id_by_short = get_legal_id_by_short_name(str(short_raw).strip()) if _has_val(short_raw) else None
                id_by_long = get_legal_id_by_long_name(str(long_raw).strip()) if _has_val(long_raw) else None
                filled = sum(1 for v in (id_raw, short_raw, long_raw) if _has_val(v))
                if filled == 0:
                    errors.append(ValidationError(row_number, "Legal ID", "At least one of Legal ID, Short Name, or Long Name is required for update", "MISSING_REQUIRED"))
                    continue
                resolved_id = id_by_id or id_by_short or id_by_long
                if resolved_id is None:
                    errors.append(ValidationError(row_number, "Legal ID", "No legal entity found for the provided identity (Legal ID, Short Name, or Long Name)", "NOT_FOUND"))
                    continue
                if filled >= 2:
                    ids = {x for x in (id_by_id, id_by_short, id_by_long) if x is not None}
                    if len(ids) > 1:
                        errors.append(ValidationError(row_number, "Legal ID", "Legal ID, Short Name and Long Name refer to different legal entities", "IDENTITY_MISMATCH"))
                        continue
                row_data["ID"] = resolved_id
                
                # Short Name and Long Name are optional for update; if provided, send them; if empty, omit so Java keeps existing values
                short_name = row.get("Short Name") or row.get("ShortName")
                if short_name is not None and not pd.isna(short_name) and str(short_name).strip():
                    row_data["ShortName"] = str(short_name).strip()
                long_name = row.get("Long Name") or row.get("LongName")
                if long_name is not None and not pd.isna(long_name) and str(long_name).strip():
                    row_data["LongName"] = str(long_name).strip()
                
                # Description (optional)
                description = row.get("Description")
                if not pd.isna(description) and str(description).strip():
                    row_data["Description"] = str(description).strip()
                
                # Parent resolution (template column may be "Parent Name" or "Parent Short Name")
                pn = _legal_parent_cell(row)
                if pn:
                    if pn.lower() in names_in_file:
                        # Java resolves parent from batch by name; send canonical key
                        row_data["Parent Legal Entity Name"] = pn
                    else:
                        parent_id = get_legal_id_by_short_name(pn) or get_legal_id_by_long_name(pn)
                        if parent_id:
                            row_data["Parent_ID"] = parent_id
                        else:
                            errors.append(ValidationError(
                                row_number, "Parent Name",
                                f"Parent Legal Entity '{pn}' not found. Ensure the parent exists in the system or appears earlier in the upload file.",
                                "NOT_FOUND"))
                
                # Status resolution
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_lookup_id_by_name("status", str(status_name).strip())
                    if status_id:
                        row_data["Status_ID"] = status_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Status", f"Status '{status_name}' not found", "NOT_FOUND"))
                
                # Viewing resolution
                viewing_name = row.get("BUDG Viewing")
                if not pd.isna(viewing_name) and str(viewing_name).strip():
                    viewing_id = get_viewing_id_by_name(str(viewing_name).strip())
                    if viewing_id:
                        row_data["IsPublic_ID"] = viewing_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Viewing", f"Viewing '{viewing_name}' not found", "NOT_FOUND"))
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "Legal", row_number, errors)
                
                validated_data.append(row_data)
                
            else:  # INSERT
                # Collect all missing mandatory field errors before continuing
                has_mandatory_errors = False
                
                # Required fields — accept "Short Name" / "Long Name" (template) or "ShortName" / "LongName" (exports)
                short_name = row.get("Short Name")
                if short_name is None or pd.isna(short_name) or not str(short_name).strip():
                    short_name = row.get("ShortName")
                if short_name is None or pd.isna(short_name) or not str(short_name).strip():
                    errors.append(ValidationError(row_number, "Short Name", "Short Name is required", "MISSING_REQUIRED"))
                    has_mandatory_errors = True
                else:
                    row_data["ShortName"] = str(short_name).strip()
                
                long_name = row.get("Long Name")
                if long_name is None or pd.isna(long_name) or not str(long_name).strip():
                    long_name = row.get("LongName")
                if long_name is None or pd.isna(long_name) or not str(long_name).strip():
                    errors.append(ValidationError(row_number, "Long Name", "Long Name is required", "MISSING_REQUIRED"))
                    has_mandatory_errors = True
                else:
                    row_data["LongName"] = str(long_name).strip()
                
                # Only continue to next row if there are mandatory field errors
                if has_mandatory_errors:
                    continue
                
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
                
                # Parent resolution (template column may be "Parent Name" or "Parent Short Name")
                pn = _legal_parent_cell(row)
                if pn:
                    if pn.lower() in names_in_file:
                        row_data["Parent Legal Entity Name"] = pn
                    else:
                        parent_id = get_legal_id_by_short_name(pn) or get_legal_id_by_long_name(pn)
                        if parent_id:
                            row_data["Parent_ID"] = parent_id
                        else:
                            errors.append(ValidationError(
                                row_number, "Parent Name",
                                f"Parent Legal Entity '{pn}' not found. Ensure the parent exists in the system or appears earlier in the upload file.",
                                "NOT_FOUND"))
                
                # Status resolution
                status_name = row.get("BUDG Status")
                if not pd.isna(status_name) and str(status_name).strip():
                    status_id = get_lookup_id_by_name("status", str(status_name).strip())
                    if status_id:
                        row_data["Status_ID"] = status_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Status", f"Status '{status_name}' not found", "NOT_FOUND"))
                
                # Viewing resolution
                viewing_name = row.get("BUDG Viewing")
                if not pd.isna(viewing_name) and str(viewing_name).strip():
                    viewing_id = get_viewing_id_by_name(str(viewing_name).strip())
                    if viewing_id:
                        row_data["IsPublic_ID"] = viewing_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Viewing", f"Viewing '{viewing_name}' not found", "NOT_FOUND"))
                
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
                    add_custom_fields_to_validated_data(row, row_data, "Legal", row_number, errors)
                
                validated_data.append(row_data)
                
        except Exception as e:
            logger.error(f"Error processing row {row_number}: {e}")
            errors.append(ValidationError(row_number, "General", str(e), "PROCESSING_ERROR"))
    
    return validated_data, errors
