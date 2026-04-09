"""
Policy Bulk Upload Validation Service
FastAPI service that validates Excel files for bulk policy uploads
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import pandas as pd
import pymysql
import os
import logging
from datetime import datetime
import re
import sys
try:
    from dateutil import parser as date_parser
    HAS_DATEUTIL = True
except ImportError:
    HAS_DATEUTIL = False

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
    # Fallback if import fails
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


def _dedupe_columns_keep_first(df: pd.DataFrame) -> pd.DataFrame:
    """Excel/env export can repeat headers (e.g. Internal + isPublic→Internal); keep first column per name."""
    if df is None or df.empty or not hasattr(df, "columns"):
        return df
    if not df.columns.duplicated().any():
        return df
    dup_names = sorted(set(df.columns[df.columns.duplicated()].tolist()))
    logger.warning("Duplicate DataFrame column names; keeping first occurrence: %s", dup_names)
    return df.loc[:, ~df.columns.duplicated()].copy()


def _scalar_str_from_row(row: pd.Series, key: str) -> str:
    """Single string for column key; avoids ambiguous truth value when row[key] is a Series (duplicate labels)."""
    v = row.get(key, "")
    if isinstance(v, pd.Series):
        v = v.dropna()
        v = v.iloc[0] if len(v) else None
    if v is None or (not isinstance(v, str) and pd.isna(v)):
        return ""
    return str(v).strip()


app = FastAPI(title="Policy Bulk Validation Service")

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
    entity: str  # "Policy"
    user_id: int
    column_mappings: Optional[Dict[str, str]] = None


class ValidationError(BaseModel):
    row: int
    field: str
    message: str
    error_code: str


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
    Return expected sheet name for Policy bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Policy"
    if upload_option == "Update Existing Items":
        return "Update Policy"
    if upload_option == "Remove Existing Items":
        return "Delete Policy"
    return None


def check_duplicate_primary_name(
    primary_name: str,
    exclude_id: Optional[int] = None,
    segment_id: Optional[int] = None,
) -> bool:
    """True if the same PrimaryName exists in the target segment (segment-scoped; aligns with Java bulk rules)."""
    if duplicate_name_in_facet_segment is None:
        try:
            conn = get_db_connection()
            cursor = conn.cursor()
            if exclude_id:
                query = "SELECT COUNT(*) FROM policy WHERE LOWER(PrimaryName) = LOWER(%s) AND ID != %s AND DeletedDatetime IS NULL"
                cursor.execute(query, (primary_name, exclude_id))
            else:
                query = "SELECT COUNT(*) FROM policy WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL"
                cursor.execute(query, (primary_name,))
            count = cursor.fetchone()[0]
            cursor.close()
            conn.close()
            return count > 0
        except Exception as e:
            logger.error(f"Error checking duplicate: {e}")
            raise
    sid = segment_id if segment_id is not None else 1
    return duplicate_name_in_facet_segment("Policy", sid, primary_name, exclude_id)


def check_policy_exists(policy_id: int) -> bool:
    """Check if a policy ID exists in the database"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT COUNT(*) FROM policy WHERE ID = %s AND DeletedDatetime IS NULL"
        cursor.execute(query, (policy_id,))
        
        count = cursor.fetchone()[0]
        cursor.close()
        conn.close()
        
        return count > 0
    except Exception as e:
        logger.error(f"Error checking policy existence: {e}")
        raise


def get_policy_id_by_name(name: str) -> Optional[int]:
    """Get Policy ID by PrimaryName"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT ID FROM policy WHERE LOWER(PrimaryName) = LOWER(%s) AND DeletedDatetime IS NULL LIMIT 1"
        cursor.execute(query, (name.strip(),))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting policy ID by name: {e}")
        raise


def get_policy_id_by_ref_number(ref_number: str) -> Optional[int]:
    """Get Policy ID by refNumber"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT ID FROM policy WHERE refNumber = %s AND DeletedDatetime IS NULL LIMIT 1"
        cursor.execute(query, (ref_number.strip(),))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting policy ID by ref number: {e}")
        raise


def get_lookup_id_by_name(table_name: str, primary_name: str) -> Optional[int]:
    """Get lookup ID by table name and primary name"""
    if not primary_name or not primary_name.strip():
        return None
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = f"SELECT ID FROM {table_name} WHERE LOWER(PrimaryName) = LOWER(%s) LIMIT 1"
        cursor.execute(query, (primary_name.strip(),))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting lookup ID by name from table {table_name}: {e}")
        raise


def get_viewing_id_by_name(viewing_name: str) -> Optional[int]:
    """Get viewing ID by Name"""
    if not viewing_name or not viewing_name.strip():
        return None
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT id FROM viewing WHERE LOWER(Name) = LOWER(%s) LIMIT 1"
        cursor.execute(query, (viewing_name.strip(),))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting viewing ID by name: {e}")
        raise


def get_status_id_by_name(status_name: str) -> Optional[int]:
    """Get status ID by primaryname"""
    if not status_name or not status_name.strip():
        return None
    
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


def get_role_id_by_name(role_name: str, module_id: Optional[int] = None) -> Optional[int]:
    """Get role ID from object_role by primaryname and module"""
    if not role_name or not role_name.strip():
        return None
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        if module_id:
            query = "SELECT id FROM object_role WHERE LOWER(primaryname) = LOWER(%s) AND module = %s LIMIT 1"
            cursor.execute(query, (role_name.strip(), module_id))
        else:
            # Get Policy module ID first
            module_query = "SELECT id FROM module WHERE LOWER(primaryname) = LOWER('Policy') LIMIT 1"
            cursor.execute(module_query)
            module_result = cursor.fetchone()
            if not module_result:
                return None
            module_id = module_result[0]
            
            query = "SELECT id FROM object_role WHERE LOWER(primaryname) = LOWER(%s) AND module = %s LIMIT 1"
            cursor.execute(query, (role_name.strip(), module_id))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting role ID by name from object_role: {e}")
        raise


def get_default_lookup_value(table_name: str) -> Optional[str]:
    """Get default lookup value (first PrimaryName) from database table"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        if table_name == "status":
            query = f"SELECT primaryname FROM {table_name} ORDER BY ID ASC LIMIT 1"
        elif table_name == "viewing":
            query = f"SELECT Name FROM {table_name} ORDER BY id ASC LIMIT 1"
        else:
            query = f"SELECT PrimaryName FROM {table_name} ORDER BY ID ASC LIMIT 1"
        
        cursor.execute(query)
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result and result[0]:
            return str(result[0]).strip()
        return None
    except Exception as e:
        logger.error(f"Error getting default lookup value from table {table_name}: {e}")
        return None


def get_all_lookup_values(table_name: str) -> List[str]:
    """Get all lookup values from database table"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        if table_name == "status":
            query = f"SELECT primaryname FROM {table_name} ORDER BY ID ASC"
        elif table_name == "viewing":
            query = f"SELECT Name FROM {table_name} ORDER BY id ASC"
        else:
            query = f"SELECT PrimaryName FROM {table_name} ORDER BY ID ASC"
        
        cursor.execute(query)
        
        results = cursor.fetchall()
        cursor.close()
        conn.close()
        
        values = [str(row[0]).strip() for row in results if row[0]]
        return values
    except Exception as e:
        logger.error(f"Error getting lookup values from table {table_name}: {e}")
        return []


def validate_date_format(date_str: str) -> bool:
    """Validate date format - support "25th May 2018" and other formats"""
    if not date_str or not date_str.strip():
        return True  # Empty dates are allowed (optional fields)
    
    trimmed = date_str.strip()
    
    pattern = re.compile(r"(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]+)\s+(\d{4})", re.IGNORECASE)
    if pattern.match(trimmed):
        return True
    
    try:
        datetime.strptime(trimmed, "%Y-%m-%d")
        return True
    except ValueError:
        pass
    
    if HAS_DATEUTIL:
        try:
            date_parser.parse(trimmed)
            return True
        except (ValueError, TypeError):
            pass
    
    return False


def parse_date_from_string(date_str: str) -> Optional[str]:
    """Parse date from string format like "25th May 2018" to SQL Date format (YYYY-MM-DD)"""
    if not date_str or not date_str.strip():
        return None
    
    trimmed = date_str.strip()
    
    pattern = re.compile(r"(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]+)\s+(\d{4})", re.IGNORECASE)
    match = pattern.match(trimmed)
    
    if match:
        try:
            day = int(match.group(1))
            month_name = match.group(2).lower()
            year = int(match.group(3))
            
            month_names = ["january", "february", "march", "april", "may", "june",
                          "july", "august", "september", "october", "november", "december"]
            month = -1
            for i, name in enumerate(month_names):
                if name.startswith(month_name) or month_name.startswith(name[:3]):
                    month = i + 1
                    break
            
            if month == -1:
                logger.warn(f"Could not parse month from date string: {date_str}")
                return None
            
            return f"{year:04d}-{month:02d}-{day:02d}"
            
        except Exception as e:
            logger.warn(f"Error parsing date format '25th May 2018': {date_str}, {e}")
    
    try:
        datetime.strptime(trimmed, "%Y-%m-%d")
        return trimmed
    except ValueError:
        pass
    
    if HAS_DATEUTIL:
        try:
            parsed_date = date_parser.parse(trimmed)
            return parsed_date.strftime("%Y-%m-%d")
        except (ValueError, TypeError) as e:
            logger.warn(f"Could not parse date string: {date_str}, {e}")
    
    logger.warn(f"Could not parse date string: {date_str}")
    return None


def parse_internal_value(internal_str: str) -> Optional[int]:
    """Parse Internal value (true/false, yes/no, 1/0, Excel floats 1.0/0.0) to 1 or 0"""
    if internal_str is None:
        return None
    if isinstance(internal_str, float):
        if pd.isna(internal_str):
            return None
        return 1 if float(internal_str) != 0.0 else 0
    if isinstance(internal_str, int) and not isinstance(internal_str, bool):
        return 1 if internal_str != 0 else 0

    if not internal_str or not str(internal_str).strip():
        return None

    trimmed = str(internal_str).strip().lower()

    if trimmed in ["true", "yes", "1", "y", "t"]:
        return 1
    elif trimmed in ["false", "no", "0", "n", "f"]:
        return 0

    # Excel numeric / boolean cells often stringify as "1.0" / "0.0"
    try:
        fv = float(trimmed.replace(",", "."))
        if fv == 0.0:
            return 0
        if fv == 1.0:
            return 1
    except ValueError:
        pass

    try:
        value = int(trimmed)
        return 1 if value != 0 else 0
    except ValueError:
        logger.warn(f"Could not parse Internal value: {internal_str}")
        return None


def find_excel_column_match(mapping_key: str, available_columns: List[str]) -> Optional[str]:
    """Find a matching column in the available columns using smart matching"""
    if not mapping_key or not available_columns:
        return None
    
    mapping_key_normalized = str(mapping_key).strip()
    mapping_key_lower = mapping_key_normalized.lower()
    
    # Step 1: Exact match (case-sensitive) - most reliable for Arabic/Unicode text
    for col in available_columns:
        col_normalized = str(col).strip()
        if col_normalized == mapping_key_normalized:
            logger.debug(f"Exact match found: '{col_normalized}' == '{mapping_key_normalized}'")
            return col
    
    # Step 2: Case-insensitive match (for English text)
    for col in available_columns:
        col_normalized = str(col).strip()
        col_lower = col_normalized.lower()
        if col_lower == mapping_key_lower:
            logger.debug(f"Case-insensitive match found: '{col_normalized}' (lower: '{col_lower}') == '{mapping_key_normalized}' (lower: '{mapping_key_lower}')")
            return col
    
    # Step 3: Partial match (starts with)
    for col in available_columns:
        col_normalized = str(col).strip()
        col_lower = col_normalized.lower()
        if col_lower.startswith(mapping_key_lower):
            if (col_lower == mapping_key_lower or 
                col_lower.startswith(mapping_key_lower + " ") or 
                col_lower.startswith(mapping_key_lower + "(") or
                col_lower.startswith(mapping_key_lower + "-") or
                col_lower.startswith(mapping_key_lower + "_")):
                logger.debug(f"Partial match found: '{col_normalized}' starts with '{mapping_key_normalized}'")
                return col
    
    # Step 4: Contains match (if key length >= 3)
    if len(mapping_key_normalized) >= 3:
        for col in available_columns:
            col_normalized = str(col).strip()
            col_lower = col_normalized.lower()
            if mapping_key_lower in col_lower:
                logger.debug(f"Contains match found: '{mapping_key_normalized}' in '{col_normalized}'")
                return col
    
    logger.debug(f"No match found for '{mapping_key_normalized}' in {len(available_columns)} available columns")
    return None


def apply_column_mappings(df: pd.DataFrame, column_mappings: Optional[Dict[str, str]]) -> pd.DataFrame:
    """Apply column mappings to rename Excel columns to expected field names"""
    if not column_mappings:
        logger.info("No column mappings provided, skipping mapping step")
        return df
    
    logger.info(f"Applying column mappings. Received mappings: {column_mappings}")
    logger.info(f"DataFrame columns before mapping: {list(df.columns)}")
    
    available_columns = [str(col) for col in df.columns]
    
    rename_dict = {}
    used_columns = set()
    
    for excel_col_mapping, expected_field in column_mappings.items():
        if not excel_col_mapping or not expected_field or not str(excel_col_mapping).strip() or not str(expected_field).strip():
            logger.warn(f"Skipping empty mapping: '{excel_col_mapping}' -> '{expected_field}'")
            continue
        
        expected_field_normalized = str(expected_field).strip()
        excel_col_mapping_normalized = str(excel_col_mapping).strip()
        
        logger.debug(f"Looking for Excel column matching '{excel_col_mapping_normalized}' to map to '{expected_field_normalized}'")
        matched_col = find_excel_column_match(excel_col_mapping_normalized, available_columns)
        
        if matched_col:
            if matched_col in used_columns:
                logger.warn(f"Column '{matched_col}' already mapped, skipping duplicate mapping for '{excel_col_mapping_normalized}' -> '{expected_field_normalized}'")
                continue
            # Avoid two columns with same target name (e.g. Internal + isPublic→Internal)
            if expected_field_normalized in available_columns and matched_col != expected_field_normalized:
                logger.info(
                    "Skip mapping '%s' -> '%s': a column with that target name already exists",
                    matched_col,
                    expected_field_normalized,
                )
                continue

            rename_dict[matched_col] = expected_field_normalized
            used_columns.add(matched_col)
            logger.info(f"✓ Column mapping successful: Excel column '{matched_col}' (matched from '{excel_col_mapping_normalized}') -> '{expected_field_normalized}'")
        else:
            logger.warn(f"✗ Could not find Excel column matching '{excel_col_mapping_normalized}' in available columns: {available_columns}")
    
    if rename_dict:
        logger.info(f"Applying {len(rename_dict)} column mappings: {list(rename_dict.keys())} -> {list(rename_dict.values())}")
        df = df.rename(columns=rename_dict)
        logger.info(f"DataFrame columns after mapping: {list(df.columns)}")
    else:
        logger.warn(f"No column mappings could be applied. DataFrame columns: {list(df.columns)}, Mappings: {list(column_mappings.keys())}")
    
    return _dedupe_columns_keep_first(df)


def find_column_match(column_name: str, available_columns: List[str], available_columns_lower: List[str]) -> Optional[str]:
    """Find a matching column in the available columns"""
    if column_name in available_columns:
        return column_name
    
    column_lower = column_name.lower()
    if column_lower in available_columns_lower:
        idx = available_columns_lower.index(column_lower)
        return available_columns[idx]
    
    for i, col in enumerate(available_columns):
        col_lower = col.lower()
        if col_lower == column_lower:
            return col
        elif col_lower.startswith(column_lower + " ") or col_lower.startswith(column_lower + "("):
            return col
    
    return None


def get_column_value(row: pd.Series, column_name: str, df_columns: List[str]) -> Any:
    """Get column value from row using flexible matching"""
    if column_name in df_columns:
        return row.get(column_name)
    
    column_lower = column_name.lower()
    df_columns_lower = [col.lower() for col in df_columns]
    if column_lower in df_columns_lower:
        idx = df_columns_lower.index(column_lower)
        return row.get(df_columns[idx])
    
    for col in df_columns:
        col_lower = col.lower()
        if col_lower == column_lower:
            return row.get(col)
        elif col_lower.startswith(column_lower + " ") or col_lower.startswith(column_lower + "("):
            return row.get(col)
    
    return None


def validate_column_headers(df: pd.DataFrame, upload_option: str) -> List[ValidationError]:
    """Validate that required columns exist based on operation type"""
    errors = []
    columns = [str(col).strip() for col in df.columns]
    columns_lower = [col.lower() for col in columns]
    
    logger.info(f"Validating columns. Available columns: {columns}")
    
    if upload_option == "Add New Items":
        required = ["Name", "Internal", "Description", "Lifecycle", "Type"]
        optional = ["Ref.", "URL", "Effective Date", "End Date", "Parent Name", "Parent Ref.", 
                   "BUDG Viewing", "BUDG Status", "Governance Role"]
        
        for col in required:
            matched_col = find_column_match(col, columns, columns_lower)
            if matched_col is None:
                errors.append(ValidationError(
                    row=0,
                    field="columns",
                    message=f"Missing required column: {col}. Available columns: {', '.join(columns[:10])}...",
                    error_code="MISSING_COLUMN"
                ))
            elif matched_col != col:
                logger.info(f"Column '{col}' matched to '{matched_col}' (partial/case-insensitive match)")
    
    elif upload_option == "Update Existing Items":
        required = ["ID", "Name", "Ref.", "Internal", "URL", "Description", "Effective Date", 
                   "End Date", "Parent Name", "Parent Ref.", "BUDG Viewing", "BUDG Status", 
                   "Lifecycle", "Type"]
        
        for col in required:
            matched_col = find_column_match(col, columns, columns_lower)
            if matched_col is None:
                errors.append(ValidationError(
                    row=0,
                    field="columns",
                    message=f"Missing required column: {col}. Available columns: {', '.join(columns[:10])}...",
                    error_code="MISSING_COLUMN"
                ))
            elif matched_col != col:
                logger.info(f"Column '{col}' matched to '{matched_col}' (partial/case-insensitive match)")
    
    elif upload_option == "Remove Existing Items":
        required = ["ID"]
        
        matched_col = find_column_match("ID", columns, columns_lower)
        if matched_col is None:
            errors.append(ValidationError(
                row=0,
                field="columns",
                message=f"Missing required column: ID. Available columns: {', '.join(columns[:10])}...",
                error_code="MISSING_COLUMN"
            ))
        elif matched_col != "ID":
            logger.info(f"Column 'ID' matched to '{matched_col}' (partial/case-insensitive match)")
    
    return errors


def validate_row_data(df: pd.DataFrame, upload_option: str, segment_mode: Optional[str] = None, segment: Optional[str] = None, user_id: Optional[int] = None) -> tuple[List[ValidationError], List[Dict[str, Any]]]:
    """Validate each row of data"""
    errors = []
    validated_data = []
    df = _dedupe_columns_keep_first(df)
    
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
    
    for idx, row in df.iterrows():
        row_number = idx + 2
        
        if upload_option == "Add New Items":
            # Initialize ref_value for tracking
            ref_value = None
            
            # Collect all missing mandatory field errors before continuing
            has_mandatory_errors = False
            
            # Validate Name (required)
            name = str(row.get("Name", "")).strip() if pd.notna(row.get("Name")) else ""
            if not name:
                errors.append(ValidationError(
                    row=row_number,
                    field="Name",
                    message="Required field 'Name' is empty",
                    error_code="REQUIRED_FIELD_EMPTY"
                ))
                has_mandatory_errors = True
            
            # Check for duplicate name within template (only if name is provided)
            if name:
                name_lower = name.lower()
                if name_lower in template_names:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Name",
                        message=f"Duplicate name '{name}' found within the template. Each policy name must be unique in the upload file.",
                        error_code="DUPLICATE_NAME_IN_TEMPLATE"
                    ))
                    has_mandatory_errors = True
                
                # Check for duplicate name in database (same segment only)
                eff_seg = (
                    resolve_effective_segment_id_for_duplicate_check(row, segment_mode, segment)
                    if resolve_effective_segment_id_for_duplicate_check
                    else 1
                )
                if check_duplicate_primary_name(name, segment_id=eff_seg):
                    errors.append(ValidationError(
                        row=row_number,
                        field="Name",
                        message=f"Policy with name '{name}' already exists in the database for this segment",
                        error_code="DUPLICATE_NAME"
                    ))
                    has_mandatory_errors = True
            
            # Only continue to next row if there are mandatory field errors
            if has_mandatory_errors:
                continue
            
            # Validate Ref. for duplicates within template (if provided)
            if pd.notna(row.get("Ref.")):
                ref_value = str(row.get("Ref.", "")).strip()
                if ref_value:  # Only check if ref is not empty
                    ref_lower = ref_value.lower()
                    if ref_lower in template_refs:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Ref.",
                            message=f"Duplicate reference '{ref_value}' found within the template. Each policy reference must be unique in the upload file.",
                            error_code="DUPLICATE_REF_IN_TEMPLATE"
                        ))
                        continue
                    
                    # Check for duplicate ref in database
                    existing_policy_id = get_policy_id_by_ref_number(ref_value)
                    if existing_policy_id:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Ref.",
                            message=f"Policy with reference '{ref_value}' already exists in the database",
                            error_code="DUPLICATE_REF"
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
            
            # Collect all missing mandatory field errors (Internal, Description)
            has_mandatory_errors = False
            
            # Validate Internal (required)
            internal_str = _scalar_str_from_row(row, "Internal")
            if not internal_str:
                errors.append(ValidationError(
                    row=row_number,
                    field="Internal",
                    message="Required field 'Internal' is empty",
                    error_code="REQUIRED_FIELD_EMPTY"
                ))
                has_mandatory_errors = True
            
            internal = None
            if internal_str:
                internal = parse_internal_value(internal_str)
                if internal is None:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Internal",
                        message=f"Invalid Internal value: '{internal_str}'. Expected: true/false, yes/no, or 1/0",
                        error_code="INVALID_INTERNAL"
                    ))
                    has_mandatory_errors = True
            
            # Validate Description (required)
            description = str(row.get("Description", "")).strip() if pd.notna(row.get("Description")) else ""
            if not description:
                errors.append(ValidationError(
                    row=row_number,
                    field="Description",
                    message="Required field 'Description' is empty",
                    error_code="REQUIRED_FIELD_EMPTY"
                ))
                has_mandatory_errors = True
            
            # Only continue to next row if there are mandatory field errors
            if has_mandatory_errors:
                continue
            
            # Validate Lifecycle (required)
            lifecycle = str(row.get("Lifecycle", "")).strip() if pd.notna(row.get("Lifecycle")) else ""
            if not lifecycle:
                errors.append(ValidationError(
                    row=row_number,
                    field="Lifecycle",
                    message="Required field 'Lifecycle' is empty",
                    error_code="REQUIRED_FIELD_EMPTY"
                ))
                continue
            
            valid_lifecycle_values = get_all_lookup_values("policy_lifecycle_status")
            if lifecycle and lifecycle not in valid_lifecycle_values:
                errors.append(ValidationError(
                    row=row_number,
                    field="Lifecycle",
                    message=f"Invalid Lifecycle: '{lifecycle}'. Valid values: {', '.join(valid_lifecycle_values)}",
                    error_code="INVALID_LIFECYCLE"
                ))
                continue
            
            lifecycle_id = get_lookup_id_by_name("policy_lifecycle_status", lifecycle)
            if lifecycle_id is None:
                errors.append(ValidationError(
                    row=row_number,
                    field="Lifecycle",
                    message=f"Lifecycle '{lifecycle}' not found in database",
                    error_code="LIFECYCLE_NOT_FOUND"
                ))
                continue
            
            # Validate Type (required)
            policy_type = str(row.get("Type", "")).strip() if pd.notna(row.get("Type")) else ""
            if not policy_type:
                errors.append(ValidationError(
                    row=row_number,
                    field="Type",
                    message="Required field 'Type' is empty",
                    error_code="REQUIRED_FIELD_EMPTY"
                ))
                continue
            
            valid_type_values = get_all_lookup_values("policy_type")
            if policy_type and policy_type not in valid_type_values:
                errors.append(ValidationError(
                    row=row_number,
                    field="Type",
                    message=f"Invalid Type: '{policy_type}'. Valid values: {', '.join(valid_type_values)}",
                    error_code="INVALID_TYPE"
                ))
                continue
            
            type_id = get_lookup_id_by_name("policy_type", policy_type)
            if type_id is None:
                errors.append(ValidationError(
                    row=row_number,
                    field="Type",
                    message=f"Type '{policy_type}' not found in database",
                    error_code="TYPE_NOT_FOUND"
                ))
                continue
            
            # Validate optional dates
            effective_date = str(row.get("Effective Date", "")).strip() if pd.notna(row.get("Effective Date")) else ""
            if effective_date and not validate_date_format(effective_date):
                errors.append(ValidationError(
                    row=row_number,
                    field="Effective Date",
                    message=f"Invalid date format: '{effective_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                    error_code="INVALID_DATE_FORMAT"
                ))
                continue
            
            end_date = str(row.get("End Date", "")).strip() if pd.notna(row.get("End Date")) else ""
            if end_date and not validate_date_format(end_date):
                errors.append(ValidationError(
                    row=row_number,
                    field="End Date",
                    message=f"Invalid date format: '{end_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                    error_code="INVALID_DATE_FORMAT"
                ))
                continue
            
            # Validate Parent Policy
            parent_id = None
            parent_name = None
            parent_ref = None
            has_name = pd.notna(row.get("Parent Name"))
            has_ref = pd.notna(row.get("Parent Ref."))
            
            if has_name:
                parent_name = str(row.get("Parent Name", "")).strip()
            if has_ref:
                parent_ref = str(row.get("Parent Ref.", "")).strip()
            
            # Parent in same file: allow without DB lookup; Java will resolve from batch
            parent_in_file = (has_ref and parent_ref and parent_ref.lower() in refs_in_file) or (has_name and parent_name and parent_name.lower() in names_in_file)
            if parent_in_file:
                pass  # Do not set parent_id; Java will resolve from batch
            # If both are provided, validate they refer to the same parent
            elif has_name and parent_name and has_ref and parent_ref:
                parent_id_by_name = get_policy_id_by_name(parent_name)
                parent_id_by_ref = get_policy_id_by_ref_number(parent_ref)
                
                # Both exist - check if they refer to the same policy
                if parent_id_by_name and parent_id_by_ref:
                    if parent_id_by_name != parent_id_by_ref:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different policies",
                            error_code="PARENT_NAME_REF_MISMATCH"
                        ))
                        continue
                    else:
                        parent_id = parent_id_by_name
                # Only name exists - use it
                elif parent_id_by_name:
                    parent_id = parent_id_by_name
                    # But ref doesn't exist - this is an error
                    if not parent_id_by_ref:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message=f"Parent reference '{parent_ref}' does not exist, but parent name '{parent_name}' refers to a different policy",
                            error_code="PARENT_NAME_REF_MISMATCH"
                        ))
                        continue
                # Only ref exists - use it
                elif parent_id_by_ref:
                    parent_id = parent_id_by_ref
                    # But name doesn't exist - this is an error
                    if not parent_id_by_name:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Name",
                            message=f"Parent name '{parent_name}' does not exist, but parent reference '{parent_ref}' refers to a different policy",
                            error_code="PARENT_NAME_REF_MISMATCH"
                        ))
                        continue
                # Neither exists - will be caught by individual validation below
                else:
                    pass
            
            # If only name is provided, validate it exists (skip when parent is in same file)
            if not parent_in_file and parent_id is None and has_name and parent_name and (not has_ref or not parent_ref):
                parent_id = get_policy_id_by_name(parent_name)
                if parent_id is None:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Parent Name",
                        message=f"Parent Policy with name '{parent_name}' not found",
                        error_code="PARENT_NOT_FOUND"
                    ))
                    continue
            
            # If only ref is provided, validate it exists (skip when parent is in same file)
            if not parent_in_file and parent_id is None and has_ref and parent_ref and (not has_name or not parent_name):
                parent_id = get_policy_id_by_ref_number(parent_ref)
                if parent_id is None:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Parent Ref.",
                        message=f"Parent Policy with reference '{parent_ref}' not found",
                        error_code="PARENT_REF_NOT_FOUND"
                    ))
                    continue
            
            # Validate BUDG Viewing (optional)
            viewing_id = None
            if pd.notna(row.get("BUDG Viewing")):
                viewing_name = str(row.get("BUDG Viewing", "")).strip()
                if viewing_name:
                    viewing_id = get_viewing_id_by_name(viewing_name)
                    if viewing_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="BUDG Viewing",
                            message=f"BUDG Viewing '{viewing_name}' not found in database",
                            error_code="VIEWING_NOT_FOUND"
                        ))
                        continue
            
            # Validate BUDG Status (optional)
            status_id = None
            if pd.notna(row.get("BUDG Status")):
                status_name = str(row.get("BUDG Status", "")).strip()
                if status_name:
                    status_id = get_status_id_by_name(status_name)
                    if status_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="BUDG Status",
                            message=f"BUDG Status '{status_name}' not found in database",
                            error_code="STATUS_NOT_FOUND"
                        ))
                        continue
            
            # Validate Governance Role
            governance_role_id = None
            if pd.notna(row.get("Governance Role")):
                governance_role = str(row.get("Governance Role", "")).strip()
                if governance_role:
                    governance_role_id = get_role_id_by_name(governance_role)
                    if governance_role_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Governance Role",
                            message=f"Governance Role '{governance_role}' not found in database for Policy module",
                            error_code="GOVERNANCE_ROLE_NOT_FOUND"
                        ))
                        continue
            
            # Add to validated data
            validated_row = {
                "operation": "INSERT",
                "row_number": row_number,
                "Name": name,
                "Internal": internal,
                "Description": description,
                "Lifecycle_ID": lifecycle_id,
                "Type_ID": type_id,
            }
            
            # Add optional fields
            if pd.notna(row.get("Ref.")):
                validated_row["Ref."] = str(row.get("Ref.", "")).strip()
            if pd.notna(row.get("URL")):
                validated_row["URL"] = str(row.get("URL", "")).strip()
            if effective_date:
                validated_row["Effective Date"] = parse_date_from_string(effective_date)
            if end_date:
                validated_row["End Date"] = parse_date_from_string(end_date)
            if parent_id is not None:
                validated_row["Parent_ID"] = parent_id
            if viewing_id is not None:
                validated_row["BUDG Viewing_ID"] = viewing_id
            if status_id is not None:
                validated_row["BUDG Status_ID"] = status_id
            if governance_role_id is not None:
                validated_row["Governance Role_ID"] = governance_role_id
            # Also pass through the name if provided (for Java to resolve)
            if pd.notna(row.get("Governance Role")):
                governance_role_name = str(row.get("Governance Role", "")).strip()
                if governance_role_name:
                    validated_row["Governance Role"] = governance_role_name
            
            # User Email (optional) - pass through for stakeholder creation
            user_email = row.get("User Email")
            if user_email and pd.notna(user_email) and str(user_email).strip():
                validated_row["User Email"] = str(user_email).strip()
            
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
                        row_dict, "Policy", row_number
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
                        bt = BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE.get("Policy")
                        if bt is not None:
                            merge_excel_custom_field_passthrough(row, validated_row, bt)
                except Exception as e:
                    logger.error(f"Error validating custom fields for row {row_number}: {e}")
                    # Don't fail the row if custom fields validation fails, just log it
            
            # Add to tracking sets only after all validations pass
            name_lower = name.lower()
            template_names.add(name_lower)
            if ref_value:
                ref_lower = ref_value.lower()
                template_refs.add(ref_lower)
            
            validated_data.append(validated_row)
        
        elif upload_option == "Update Existing Items":
            def _has_val(v):
                if v is None: return False
                if pd.isna(v): return False
                s = str(v).strip()
                return bool(s) and s.lower() not in ("nan", "")
            id_raw = row.get("ID")
            ref_raw = row.get("Ref.") or row.get("refNumber")
            name_raw = row.get("Name")
            id_by_id = None
            if _has_val(id_raw):
                try:
                    vid = int(float(str(id_raw).strip()))
                    if check_policy_exists(vid):
                        id_by_id = vid
                except (ValueError, TypeError):
                    pass
            id_by_ref = get_policy_id_by_ref_number(str(ref_raw).strip()) if _has_val(ref_raw) else None
            id_by_name = get_policy_id_by_name(str(name_raw).strip()) if _has_val(name_raw) else None
            filled = sum(1 for v in (id_raw, ref_raw, name_raw) if _has_val(v))
            if filled == 0:
                errors.append(ValidationError(row=row_number, field="ID", message="At least one of ID, Ref., or Name is required for update", error_code="MISSING_ID"))
                continue
            policy_id = id_by_id or id_by_ref or id_by_name
            if policy_id is None:
                errors.append(ValidationError(row=row_number, field="ID", message="No policy found for the provided identity (ID, Ref., or Name)", error_code="ID_NOT_FOUND"))
                continue
            if filled >= 2:
                ids = {x for x in (id_by_id, id_by_ref, id_by_name) if x is not None}
                if len(ids) > 1:
                    errors.append(ValidationError(row=row_number, field="ID", message="ID, Ref. and Name refer to different policies", error_code="IDENTITY_MISMATCH"))
                    continue
            
            # Name is optional on update; when provided, must be unique within segment
            name = str(row.get("Name", "")).strip() if pd.notna(row.get("Name")) else ""
            eff_seg_upd = 1
            if get_object_segment_id:
                try:
                    osid = get_object_segment_id(policy_id, "Policy")
                    if osid is not None and osid > 0:
                        eff_seg_upd = int(osid)
                except Exception:
                    pass
            if name and check_duplicate_primary_name(name, policy_id, segment_id=eff_seg_upd):
                errors.append(ValidationError(
                    row=row_number,
                    field="Name",
                    message=f"Another policy with name '{name}' already exists in this segment",
                    error_code="DUPLICATE_NAME"
                ))
                continue
            
            # Validate Internal
            internal_str = _scalar_str_from_row(row, "Internal")
            internal = parse_internal_value(internal_str) if internal_str else None
            
            # Description is optional on update; when empty, backend keeps existing value
            description = str(row.get("Description", "")).strip() if pd.notna(row.get("Description")) else ""
            
            # Validate Lifecycle (mandatory - no auto-fill)
            lifecycle = str(row.get("Lifecycle", "")).strip() if pd.notna(row.get("Lifecycle")) else ""
            if not lifecycle:
                errors.append(ValidationError(
                    row=row_number,
                    field="Lifecycle",
                    message="Lifecycle is required and cannot be empty. Please specify a valid Lifecycle value.",
                    error_code="EMPTY_LIFECYCLE"
                ))
                continue
            
            valid_lifecycle_values = get_all_lookup_values("policy_lifecycle_status")
            if lifecycle and lifecycle not in valid_lifecycle_values:
                errors.append(ValidationError(
                    row=row_number,
                    field="Lifecycle",
                    message=f"Invalid Lifecycle: '{lifecycle}'. Valid values: {', '.join(valid_lifecycle_values)}",
                    error_code="INVALID_LIFECYCLE"
                ))
                continue
            
            lifecycle_id = get_lookup_id_by_name("policy_lifecycle_status", lifecycle)
            if lifecycle_id is None:
                errors.append(ValidationError(
                    row=row_number,
                    field="Lifecycle",
                    message=f"Lifecycle '{lifecycle}' not found in database",
                    error_code="LIFECYCLE_NOT_FOUND"
                ))
                continue
            
            # Type is optional on update; when empty, existing value is preserved
            policy_type = str(row.get("Type", "")).strip() if pd.notna(row.get("Type")) else ""
            type_id = None
            if policy_type:
                valid_type_values = get_all_lookup_values("policy_type")
                if policy_type not in valid_type_values:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Type",
                        message=f"Invalid Type: '{policy_type}'. Valid values: {', '.join(valid_type_values)}",
                        error_code="INVALID_TYPE"
                    ))
                    continue
                type_id = get_lookup_id_by_name("policy_type", policy_type)
                if type_id is None:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Type",
                        message=f"Type '{policy_type}' not found in database",
                        error_code="TYPE_NOT_FOUND"
                    ))
                    continue
            
            # Validate optional dates
            effective_date = str(row.get("Effective Date", "")).strip() if pd.notna(row.get("Effective Date")) else ""
            if effective_date and not validate_date_format(effective_date):
                errors.append(ValidationError(
                    row=row_number,
                    field="Effective Date",
                    message=f"Invalid date format: '{effective_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                    error_code="INVALID_DATE_FORMAT"
                ))
                continue
            
            end_date = str(row.get("End Date", "")).strip() if pd.notna(row.get("End Date")) else ""
            if end_date and not validate_date_format(end_date):
                errors.append(ValidationError(
                    row=row_number,
                    field="End Date",
                    message=f"Invalid date format: '{end_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                    error_code="INVALID_DATE_FORMAT"
                ))
                continue
            
            # Validate Parent Policy
            parent_id = None
            parent_name = None
            parent_ref = None
            has_name = pd.notna(row.get("Parent Name"))
            has_ref = pd.notna(row.get("Parent Ref."))
            
            if has_name:
                parent_name = str(row.get("Parent Name", "")).strip()
            if has_ref:
                parent_ref = str(row.get("Parent Ref.", "")).strip()
            
            # Parent in same file: allow without DB lookup; Java will resolve from batch
            parent_in_file = (has_ref and parent_ref and parent_ref.lower() in refs_in_file) or (has_name and parent_name and parent_name.lower() in names_in_file)
            if parent_in_file:
                pass  # Do not set parent_id; Java will resolve from batch
            # If both are provided, validate they refer to the same parent
            elif has_name and parent_name and has_ref and parent_ref:
                parent_id_by_name = get_policy_id_by_name(parent_name)
                parent_id_by_ref = get_policy_id_by_ref_number(parent_ref)
                
                # Both exist - check if they refer to the same policy
                if parent_id_by_name and parent_id_by_ref:
                    if parent_id_by_name != parent_id_by_ref:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different policies",
                            error_code="PARENT_NAME_REF_MISMATCH"
                        ))
                        continue
                    else:
                        parent_id = parent_id_by_name
                # Only name exists - use it
                elif parent_id_by_name:
                    parent_id = parent_id_by_name
                    # But ref doesn't exist - this is an error
                    if not parent_id_by_ref:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message=f"Parent reference '{parent_ref}' does not exist, but parent name '{parent_name}' refers to a different policy",
                            error_code="PARENT_NAME_REF_MISMATCH"
                        ))
                        continue
                # Only ref exists - use it
                elif parent_id_by_ref:
                    parent_id = parent_id_by_ref
                    # But name doesn't exist - this is an error
                    if not parent_id_by_name:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Name",
                            message=f"Parent name '{parent_name}' does not exist, but parent reference '{parent_ref}' refers to a different policy",
                            error_code="PARENT_NAME_REF_MISMATCH"
                        ))
                        continue
                # Neither exists - will be caught by individual validation below
                else:
                    pass
            
            # If only name is provided, validate it exists (skip when parent is in same file)
            if not parent_in_file and parent_id is None and has_name and parent_name and (not has_ref or not parent_ref):
                parent_id = get_policy_id_by_name(parent_name)
                if parent_id is None:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Parent Name",
                        message=f"Parent Policy with name '{parent_name}' not found",
                        error_code="PARENT_NOT_FOUND"
                    ))
                    continue
            
            # If only ref is provided, validate it exists (skip when parent is in same file)
            if not parent_in_file and parent_id is None and has_ref and parent_ref and (not has_name or not parent_name):
                parent_id = get_policy_id_by_ref_number(parent_ref)
                if parent_id is None:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Parent Ref.",
                        message=f"Parent Policy with reference '{parent_ref}' not found",
                        error_code="PARENT_REF_NOT_FOUND"
                    ))
                    continue
            
            # Validate BUDG Viewing
            viewing_id = None
            if pd.notna(row.get("BUDG Viewing")):
                viewing_name = str(row.get("BUDG Viewing", "")).strip()
                if viewing_name:
                    viewing_id = get_viewing_id_by_name(viewing_name)
                    if viewing_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="BUDG Viewing",
                            message=f"BUDG Viewing '{viewing_name}' not found in database",
                            error_code="VIEWING_NOT_FOUND"
                        ))
                        continue
            
            # Validate BUDG Status
            status_id = None
            if pd.notna(row.get("BUDG Status")):
                status_name = str(row.get("BUDG Status", "")).strip()
                if status_name:
                    status_id = get_status_id_by_name(status_name)
                    if status_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="BUDG Status",
                            message=f"BUDG Status '{status_name}' not found in database",
                            error_code="STATUS_NOT_FOUND"
                        ))
                        continue
            
            # Validate Governance Role
            governance_role_id = None
            if pd.notna(row.get("Governance Role")):
                governance_role = str(row.get("Governance Role", "")).strip()
                if governance_role:
                    governance_role_id = get_role_id_by_name(governance_role)
                    if governance_role_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Governance Role",
                            message=f"Governance Role '{governance_role}' not found in database for Policy module",
                            error_code="GOVERNANCE_ROLE_NOT_FOUND"
                        ))
                        continue
            
            # Add to validated data (omit Name/Description/Type_ID when empty so backend keeps existing)
            validated_row = {
                "operation": "UPDATE",
                "row_number": row_number,
                "ID": policy_id,
                "Lifecycle_ID": lifecycle_id,
            }
            if name:
                validated_row["Name"] = name
            if description:
                validated_row["Description"] = description
            if type_id is not None:
                validated_row["Type_ID"] = type_id

            # Add optional fields
            if pd.notna(row.get("Ref.")):
                validated_row["Ref."] = str(row.get("Ref.", "")).strip()
            if internal is not None:
                validated_row["Internal"] = internal
            if pd.notna(row.get("URL")):
                validated_row["URL"] = str(row.get("URL", "")).strip()
            if effective_date:
                validated_row["Effective Date"] = parse_date_from_string(effective_date)
            if end_date:
                validated_row["End Date"] = parse_date_from_string(end_date)
            if policy_id and parent_id is not None:
                if would_create_parent_cycle_by_entity:
                    if would_create_parent_cycle_by_entity(get_db_connection, "Policy", policy_id, parent_id):
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message="Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.",
                            error_code="PARENT_CYCLE",
                        ))
                        continue
                if get_object_segment_id:
                    obj_seg = get_object_segment_id(policy_id, "Policy")
                    parent_seg = get_object_segment_id(parent_id, "Policy")
                    if obj_seg is not None and parent_seg is not None and obj_seg != -1 and parent_seg != -1:
                        if obj_seg != 1 and parent_seg != 1 and obj_seg != parent_seg:
                            errors.append(ValidationError(
                                row=row_number,
                                field="Parent Ref.",
                                message="Cannot set parent to an object in a different private segment.",
                                error_code="PARENT_DIFFERENT_PRIVATE_SEGMENT",
                            ))
                            continue
            if parent_id is not None:
                validated_row["Parent_ID"] = parent_id
            if viewing_id is not None:
                validated_row["BUDG Viewing_ID"] = viewing_id
            if status_id is not None:
                validated_row["BUDG Status_ID"] = status_id
            if governance_role_id is not None:
                validated_row["Governance Role_ID"] = governance_role_id
            # Also pass through the name if provided (for Java to resolve)
            if pd.notna(row.get("Governance Role")):
                governance_role_name = str(row.get("Governance Role", "")).strip()
                if governance_role_name:
                    validated_row["Governance Role"] = governance_role_name
            
            # User Email (optional) - pass through for stakeholder creation
            user_email = row.get("User Email")
            if user_email and pd.notna(user_email) and str(user_email).strip():
                validated_row["User Email"] = str(user_email).strip()
            
            # Validate and add custom fields
            if validate_custom_fields_for_row:
                try:
                    row_dict = {
                        str(col): (None if pd.isna(row.get(col)) else row.get(col))
                        for col in row.index
                    }
                    custom_field_errors, validated_custom_fields = validate_custom_fields_for_row(
                        row_dict, "Policy", row_number
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
                        bt = BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE.get("Policy")
                        if bt is not None:
                            merge_excel_custom_field_passthrough(row, validated_row, bt)
                except Exception as e:
                    logger.error(f"Error validating custom fields for row {row_number}: {e}")
                    # Don't fail the row if custom fields validation fails, just log it
            
            validated_data.append(validated_row)
        
        elif upload_option == "Remove Existing Items":
            # Validate Policy ID
            policy_id = row.get("ID")
            
            if pd.isna(policy_id):
                errors.append(ValidationError(
                    row=row_number,
                    field="ID",
                    message="ID is required for delete operations",
                    error_code="MISSING_ID"
                ))
                continue
            
            try:
                policy_id = int(policy_id)
            except (ValueError, TypeError):
                errors.append(ValidationError(
                    row=row_number,
                    field="ID",
                    message="ID must be a valid integer",
                    error_code="INVALID_ID"
                ))
                continue
            
            # For delete operations, check if ID exists
            if not check_policy_exists(policy_id):
                continue  # Skip silently - idempotent delete
            
            validated_data.append({
                "operation": "DELETE",
                "row_number": row_number,
                "ID": policy_id
            })
    
    return errors, validated_data


@app.get("/")
def read_root():
    """Health check endpoint"""
    return {
        "service": "Policy Bulk Validation Service",
        "status": "running",
        "timestamp": datetime.now().isoformat()
    }


@app.post("/api/validate", response_model=ValidationResponse)
async def validate_bulk_upload(request: ValidationRequest):
    """Validate bulk upload Excel file for policies"""
    try:
        logger.info(f"Validating file: {request.file_path} for operation: {request.upload_option}")
        
        if not os.path.exists(request.file_path):
            raise HTTPException(status_code=404, detail=f"File not found: {request.file_path}")
        
        sheet_name = get_sheet_name(request.upload_option)
        logger.info(f"Reading sheet: {sheet_name}")
        
        try:
            df = read_excel(request.file_path, sheet_name=sheet_name, engine='openpyxl')
        except ValueError as e:
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
        
        if request.column_mappings:
            df = apply_column_mappings(df, request.column_mappings)
            logger.info(f"Column mappings applied. Columns after mapping: {list(df.columns)}")
        
        total_rows = len(df)
        all_errors = []
        
        header_errors = validate_column_headers(df, request.upload_option)
        all_errors.extend(header_errors)
        
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
        
        row_errors, validated_data = validate_row_data(df, request.upload_option)
        all_errors.extend(row_errors)
        
        valid_rows = len(validated_data)
        invalid_rows = total_rows - valid_rows
        
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

