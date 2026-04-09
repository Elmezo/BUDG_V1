"""
Regulation Bulk Upload Validation Service
FastAPI service that validates Excel files for bulk regulation uploads
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import pandas as pd
import pymysql
import os
import logging
from datetime import datetime, timedelta
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
        filter_rows_with_blocking_custom_field_errors,
        merge_excel_custom_field_passthrough,
        BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE,
    )
except ImportError:
    validate_custom_fields_for_row = None
    filter_rows_with_blocking_custom_field_errors = None
    merge_excel_custom_field_passthrough = None
    BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE = {}
    logging.warning("Could not import custom_fields_validator")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Regulation Bulk Validation Service")

# Database configuration from environment variables
DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USERNAME", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "database": os.getenv("DB_NAME", "project"),
    "charset": "utf8mb4"
}

# All lookup values are now fetched dynamically from database
# No hardcoded values - everything is dynamic


class ValidationRequest(BaseModel):
    file_path: str
    upload_option: str  # "Add New Items" | "Update Existing Items" | "Remove Existing Items"
    entity: str  # "Regulation"
    user_id: int
    column_mappings: Optional[Dict[str, str]] = None  # Maps Excel column names to expected field names


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
    Return expected sheet name for Regulation bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Regulation"
    if upload_option == "Update Existing Items":
        return "Update Regulation"
    if upload_option == "Remove Existing Items":
        return "Delete Regulation"
    return None


def check_duplicate_primary_name(
    primary_name: str,
    exclude_id: Optional[int] = None,
    segment_id: Optional[int] = None,
) -> bool:
    """True if the same regulation long name exists in the target segment."""
    if duplicate_name_in_facet_segment is None:
        try:
            conn = get_db_connection()
            cursor = conn.cursor()
            if exclude_id:
                query = "SELECT COUNT(*) FROM regulation WHERE LOWER(primaryName) = LOWER(%s) AND ID != %s AND DeletedDatetime IS NULL"
                cursor.execute(query, (primary_name, exclude_id))
            else:
                query = "SELECT COUNT(*) FROM regulation WHERE LOWER(primaryName) = LOWER(%s) AND DeletedDatetime IS NULL"
                cursor.execute(query, (primary_name,))
            count = cursor.fetchone()[0]
            cursor.close()
            conn.close()
            return count > 0
        except Exception as e:
            logger.error(f"Error checking duplicate: {e}")
            raise
    sid = segment_id if segment_id is not None else 1
    return duplicate_name_in_facet_segment("Regulation", sid, primary_name, exclude_id)


def check_regulation_exists(regulation_id: int) -> bool:
    """
    Check if a regulation ID exists in the database
    Returns True if exists, False otherwise
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT COUNT(*) FROM regulation WHERE ID = %s AND DeletedDatetime IS NULL"
        cursor.execute(query, (regulation_id,))
        
        count = cursor.fetchone()[0]
        cursor.close()
        conn.close()
        
        return count > 0
    except Exception as e:
        logger.error(f"Error checking regulation existence: {e}")
        raise


def get_regulation_id_by_name(name: str) -> Optional[int]:
    """
    Get Regulation ID by primaryName
    Returns ID if found, None otherwise
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT ID FROM regulation WHERE LOWER(primaryName) = LOWER(%s) AND DeletedDatetime IS NULL LIMIT 1"
        cursor.execute(query, (name.strip(),))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting regulation ID by name: {e}")
        raise


def get_regulation_id_by_ref_number(ref_number: str) -> Optional[int]:
    """
    Get Regulation ID by RefNumber
    Returns ID if found, None otherwise
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        query = "SELECT ID FROM regulation WHERE RefNumber = %s AND DeletedDatetime IS NULL LIMIT 1"
        cursor.execute(query, (ref_number.strip(),))
        
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        logger.error(f"Error getting regulation ID by ref number: {e}")
        raise


def get_lookup_id_by_name(table_name: str, primary_name: str) -> Optional[int]:
    """
    Get lookup ID by table name and primary name
    Returns ID if found, None otherwise
    """
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


def get_role_id_by_name(role_name: str, module_id: int = 23) -> Optional[int]:
    """
    Get role ID from object_role by primaryname and module
    module_id = 23 for Regulation module
    Returns ID if found, None otherwise
    """
    if not role_name or not role_name.strip():
        return None
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
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
    """
    Get default lookup value (first PrimaryName) from database table
    Returns PrimaryName of first record if found, None otherwise
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
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
    """
    Get all lookup values (PrimaryName) from database table
    Returns list of PrimaryName values ordered by ID
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
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


_load_lookup_table_values = get_all_lookup_values  # used by validate_row_data cache only


def resolve_lookup_primary_name(raw: str, valid_values: List[str]) -> Optional[str]:
    """
    Map Excel/user text to a canonical PrimaryName from a lookup table.
    Supports exact match, case-insensitive match, and unique prefix match
    (e.g. 'leg' -> 'Legal Requirement' when only one value starts with 'leg').
    """
    if not raw or not valid_values:
        return None
    s = raw.strip()
    if not s:
        return None
    if s in valid_values:
        return s
    sl = s.lower()
    for v in valid_values:
        if v and v.lower() == sl:
            return v
    prefix_matches = [v for v in valid_values if v and v.lower().startswith(sl)]
    if len(prefix_matches) == 1:
        return prefix_matches[0]
    return None


def _excel_serial_to_iso(trimmed: str) -> Optional[str]:
    """Convert Excel serial day number (e.g. 45935.0) to YYYY-MM-DD."""
    try:
        serial = float(str(trimmed).strip().replace(",", "."))
    except (ValueError, TypeError):
        return None
    if not (1 <= serial < 2_000_000):
        return None
    try:
        base = datetime(1899, 12, 30)
        dt = base + timedelta(days=int(round(serial)))
        return dt.strftime("%Y-%m-%d")
    except (OverflowError, ValueError):
        return None


def validate_date_format(date_str: str) -> bool:
    """
    Validate date format - support "25th May 2018" and other formats
    Returns True if valid, False otherwise
    """
    if not date_str or not date_str.strip():
        return True  # Empty dates are allowed (optional fields)
    
    trimmed = date_str.strip()

    if _excel_serial_to_iso(trimmed):
        return True
    
    # Try to parse "25th May 2018" format
    pattern = re.compile(r"(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]+)\s+(\d{4})", re.IGNORECASE)
    if pattern.match(trimmed):
        return True
    
    # Try standard SQL date format (YYYY-MM-DD)
    try:
        datetime.strptime(trimmed, "%Y-%m-%d")
        return True
    except ValueError:
        pass
    
    # Try common date formats
    date_formats = [
        "%d/%m/%Y",      # 12/10/2000 (dd/mm/yyyy)
        "%m/%d/%Y",      # 10/12/2000 (mm/dd/yyyy)
        "%d-%m-%Y",      # 12-10-2000
        "%Y/%m/%d",      # 2000/10/12
        "%d.%m.%Y",      # 12.10.2000
        "%Y-%m-%d %H:%M:%S",  # With time
        "%d/%m/%Y %H:%M:%S",  # With time
    ]
    
    for fmt in date_formats:
        try:
            datetime.strptime(trimmed, fmt)
            return True
        except (ValueError, TypeError):
            continue
    
    # Try other common formats using dateutil if available
    if HAS_DATEUTIL:
        try:
            date_parser.parse(trimmed)
            return True
        except (ValueError, TypeError):
            pass
    
    return False


def parse_date_from_string(date_str: str) -> Optional[str]:
    """
    Parse date from string format like "25th May 2018" to SQL Date format (YYYY-MM-DD)
    Returns formatted date string or None if invalid
    """
    if not date_str or not date_str.strip():
        return None
    
    trimmed = date_str.strip()

    iso_from_serial = _excel_serial_to_iso(trimmed)
    if iso_from_serial:
        return iso_from_serial
    
    # Try to parse "25th May 2018" format
    pattern = re.compile(r"(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]+)\s+(\d{4})", re.IGNORECASE)
    match = pattern.match(trimmed)
    
    if match:
        try:
            day = int(match.group(1))
            month_name = match.group(2).lower()
            year = int(match.group(3))
            
            # Map month names to numbers
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
            
            # Format as YYYY-MM-DD
            return f"{year:04d}-{month:02d}-{day:02d}"
            
        except Exception as e:
            logger.warn(f"Error parsing date format '25th May 2018': {date_str}, {e}")
    
    # Try standard SQL date format (YYYY-MM-DD)
    try:
        datetime.strptime(trimmed, "%Y-%m-%d")
        return trimmed
    except ValueError:
        pass
    
    # Try common date formats (order matters - try most specific first)
    date_formats = [
        "%d/%m/%Y",      # 12/10/2000 (dd/mm/yyyy)
        "%m/%d/%Y",      # 10/12/2000 (mm/dd/yyyy)
        "%d-%m-%Y",      # 12-10-2000
        "%Y/%m/%d",      # 2000/10/12
        "%d.%m.%Y",      # 12.10.2000
        "%Y-%m-%d %H:%M:%S",  # With time
        "%d/%m/%Y %H:%M:%S",  # With time
    ]
    
    for fmt in date_formats:
        try:
            date_obj = datetime.strptime(trimmed, fmt)
            return date_obj.strftime("%Y-%m-%d")
        except (ValueError, TypeError):
            continue
    
    # Try other common formats using dateutil if available
    if HAS_DATEUTIL:
        try:
            parsed_date = date_parser.parse(trimmed)
            return parsed_date.strftime("%Y-%m-%d")
        except (ValueError, TypeError) as e:
            logger.warn(f"Could not parse date string: {date_str}, {e}")
    
    logger.warn(f"Could not parse date string: {date_str}")
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
    Example: {"a": "Publication Date", "تاريخ النشر": "Publication Date", "تاريخ الامتثال": "Compliance Date"}
    
    Uses smart matching to find Excel columns even if they have slight variations:
    - Exact match: "a" matches "a"
    - Case-insensitive: "a" matches "A"
    - Partial match: "a" matches "a (example)" or "A Column"
    - Contains match: "pub" matches "Publication Date" (if key is >= 3 chars)
    """
    if not column_mappings:
        logger.info("No column mappings provided, skipping mapping step")
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


def find_column_match(column_name: str, available_columns: List[str], available_columns_lower: List[str]) -> Optional[str]:
    """
    Find a matching column in the available columns.
    Tries exact match, case-insensitive match, and partial match (starts with).
    Returns the matched column name or None.
    This handles cases like "Publication Date (e.g., 25th May 2018)" matching "Publication Date"
    """
    # Try exact match first
    if column_name in available_columns:
        return column_name
    
    # Try case-insensitive exact match
    column_lower = column_name.lower()
    if column_lower in available_columns_lower:
        idx = available_columns_lower.index(column_lower)
        return available_columns[idx]
    
    # Try partial match - column name starts with the required name
    # This handles cases like "Publication Date (e.g., 25th May 2018)" matching "Publication Date"
    for i, col in enumerate(available_columns):
        col_lower = col.lower()
        # Check if column starts with the required name (case-insensitive)
        # and is followed by space, parenthesis, or end of string
        if col_lower == column_lower:
            return col
        elif col_lower.startswith(column_lower + " ") or col_lower.startswith(column_lower + "("):
            return col
    
    return None


def get_column_value(row: pd.Series, column_name: str, df_columns: List[str]) -> Any:
    """
    Get column value from row using flexible matching.
    Tries exact match, case-insensitive match, and partial match (starts with).
    Returns the value or None if not found.
    """
    # Try exact match first
    if column_name in df_columns:
        return row.get(column_name)
    
    # Try case-insensitive exact match
    column_lower = column_name.lower()
    df_columns_lower = [col.lower() for col in df_columns]
    if column_lower in df_columns_lower:
        idx = df_columns_lower.index(column_lower)
        return row.get(df_columns[idx])
    
    # Try partial match - column name starts with the required name
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
    # Normalize column names: strip whitespace and handle case-insensitive matching
    columns = [str(col).strip() for col in df.columns]
    columns_lower = [col.lower() for col in columns]
    
    logger.info(f"Validating columns. Available columns: {columns}")
    
    if upload_option == "Add New Items":
        required = ["Regulation Long Name", "Description", "Publication Date", "Compliance Date", 
                   "Regulation Maturity", "Regulation Probability", "Regulation Stage", "Compliance Level"]
        optional = ["Reference", "Short Name", "Additional Info", "Comments Date", "Finalisation Date", 
                   "Legal Advice", "Parent Regulation Name", "Parent Ref.", "BUDG Status", 
                   "Legal Advice Type", "Governance Role"]
        
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
        required = ["Regulation ID", "Regulation Long Name", "Description", "Publication Date", 
                   "Compliance Date", "Regulation Maturity", "Regulation Probability", 
                   "Regulation Stage", "Compliance Level"]
        optional = ["Reference", "Short Name", "Additional Info", "Comments Date", "Finalisation Date", 
                   "Legal Advice", "Parent Regulation Name", "Parent Ref.", "BUDG Status", 
                   "Legal Advice Type"]
        
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
        required = ["Regulation ID"]
        
        matched_col = find_column_match("Regulation ID", columns, columns_lower)
        if matched_col is None:
            errors.append(ValidationError(
                row=0,
                field="columns",
                message=f"Missing required column: Regulation ID. Available columns: {', '.join(columns[:10])}...",
                error_code="MISSING_COLUMN"
            ))
        elif matched_col != "Regulation ID":
            logger.info(f"Column 'Regulation ID' matched to '{matched_col}' (partial/case-insensitive match)")
    
    return errors


def validate_row_data(df: pd.DataFrame, upload_option: str, segment_mode: Optional[str] = None, segment: Optional[str] = None, user_id: Optional[int] = None) -> tuple[List[ValidationError], List[Dict[str, Any]]]:
    """
    Validate each row of data
    Returns (errors, validated_data)
    """
    errors = []
    validated_data = []
    lookup_values_cache: Dict[str, List[str]] = {}

    def _cached_lookup_values(table_name: str) -> List[str]:
        if table_name not in lookup_values_cache:
            lookup_values_cache[table_name] = _load_lookup_table_values(table_name)
        return lookup_values_cache[table_name]

    def _default_lookup_cached(table_name: str) -> Optional[str]:
        vals = _cached_lookup_values(table_name)
        return vals[0] if vals else None
    
    # Build sets of names and refs in file so parent "in same file" is allowed
    names_in_file = set()
    refs_in_file = set()
    if "Regulation Long Name" in df.columns:
        for v in df["Regulation Long Name"].dropna():
            s = str(v).strip()
            if s:
                names_in_file.add(s.lower())
    if "Ref." in df.columns:
        for v in df["Ref."].dropna():
            s = str(v).strip()
            if s:
                refs_in_file.add(s.lower())
    
    for idx, row in df.iterrows():
        row_number = idx + 2  # +2 because Excel rows start at 1 and header is row 1
        
        if upload_option == "Add New Items":
            # Validate required fields
            regulation_long_name = str(row.get("Regulation Long Name", "")).strip() if pd.notna(row.get("Regulation Long Name")) else ""
            
            if not regulation_long_name:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Long Name",
                    message="Regulation Long Name is required and cannot be empty",
                    error_code="EMPTY_REGULATION_NAME"
                ))
                continue
            
            eff_seg = (
                resolve_effective_segment_id_for_duplicate_check(row, segment_mode, segment)
                if resolve_effective_segment_id_for_duplicate_check
                else 1
            )
            if check_duplicate_primary_name(regulation_long_name, segment_id=eff_seg):
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Long Name",
                    message=f"Regulation with name '{regulation_long_name}' already exists in this segment",
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
            
            # Validate Description (required)
            description = str(row.get("Description", "")).strip() if pd.notna(row.get("Description")) else ""
            if not description:
                errors.append(ValidationError(
                    row=row_number,
                    field="Description",
                    message="Description is required and cannot be empty",
                    error_code="EMPTY_DESCRIPTION"
                ))
                continue
            
            # Validate Publication Date (required)
            pub_date_val = get_column_value(row, "Publication Date", list(df.columns))
            publication_date = str(pub_date_val).strip() if pd.notna(pub_date_val) and pub_date_val is not None else ""
            if not publication_date:
                errors.append(ValidationError(
                    row=row_number,
                    field="Publication Date",
                    message="Publication Date is required and cannot be empty",
                    error_code="EMPTY_PUBLICATION_DATE"
                ))
                continue
            
            if not validate_date_format(publication_date):
                errors.append(ValidationError(
                    row=row_number,
                    field="Publication Date",
                    message=f"Invalid date format: '{publication_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                    error_code="INVALID_DATE_FORMAT"
                ))
                continue
            
            # Validate Compliance Date (required)
            comp_date_val = get_column_value(row, "Compliance Date", list(df.columns))
            compliance_date = str(comp_date_val).strip() if pd.notna(comp_date_val) and comp_date_val is not None else ""
            if not compliance_date:
                errors.append(ValidationError(
                    row=row_number,
                    field="Compliance Date",
                    message="Compliance Date is required and cannot be empty",
                    error_code="EMPTY_COMPLIANCE_DATE"
                ))
                continue
            
            if not validate_date_format(compliance_date):
                errors.append(ValidationError(
                    row=row_number,
                    field="Compliance Date",
                    message=f"Invalid date format: '{compliance_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                    error_code="INVALID_DATE_FORMAT"
                ))
                continue
            
            # Validate Regulation Maturity (required)
            maturity = str(row.get("Regulation Maturity", "")).strip() if pd.notna(row.get("Regulation Maturity")) else ""
            # If empty, use default (first value from database)
            if not maturity:
                maturity = _default_lookup_cached("regulation_maturity")
                if not maturity:
                    # Fallback to hardcoded list if database query fails
                    valid_maturity_values = _cached_lookup_values("regulation_maturity")
                    maturity = valid_maturity_values[0] if valid_maturity_values else ""
                logger.info(f"Row {row_number}: Regulation Maturity is empty, using default from database: '{maturity}'")
            
            # Get valid values from database
            valid_maturity_values = _cached_lookup_values("regulation_maturity")
            if maturity:
                resolved_m = resolve_lookup_primary_name(maturity, valid_maturity_values)
                if resolved_m is not None:
                    maturity = resolved_m
            
            if maturity and maturity not in valid_maturity_values:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Maturity",
                    message=f"Invalid Regulation Maturity: '{maturity}'. Valid values: {', '.join(valid_maturity_values)}",
                    error_code="INVALID_MATURITY"
                ))
                continue
            
            maturity_id = get_lookup_id_by_name("regulation_maturity", maturity)
            if maturity_id is None:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Maturity",
                    message=f"Regulation Maturity '{maturity}' not found in database",
                    error_code="MATURITY_NOT_FOUND"
                ))
                continue
            
            # Validate Regulation Probability (required)
            probability = str(row.get("Regulation Probability", "")).strip() if pd.notna(row.get("Regulation Probability")) else ""
            # If empty, use default (first value from database)
            if not probability:
                probability = _default_lookup_cached("regulation_probability")
                if not probability:
                    # Fallback to first value from database if available
                    valid_probability_values = _cached_lookup_values("regulation_probability")
                    probability = valid_probability_values[0] if valid_probability_values else ""
                logger.info(f"Row {row_number}: Regulation Probability is empty, using default from database: '{probability}'")
            
            # Get valid values from database
            valid_probability_values = _cached_lookup_values("regulation_probability")
            if probability:
                resolved_p = resolve_lookup_primary_name(probability, valid_probability_values)
                if resolved_p is not None:
                    probability = resolved_p
            
            if probability and valid_probability_values and probability not in valid_probability_values:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Probability",
                    message=f"Invalid Regulation Probability: '{probability}'. Valid values: {', '.join(valid_probability_values)}",
                    error_code="INVALID_PROBABILITY"
                ))
                continue
            
            probability_id = get_lookup_id_by_name("regulation_probability", probability)
            if probability_id is None:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Probability",
                    message=f"Regulation Probability '{probability}' not found in database",
                    error_code="PROBABILITY_NOT_FOUND"
                ))
                continue
            
            # Validate Regulation Stage (required)
            stage = str(row.get("Regulation Stage", "")).strip() if pd.notna(row.get("Regulation Stage")) else ""
            # If empty, use default (first value from database)
            if not stage:
                stage = _default_lookup_cached("regulation_stage")
                if not stage:
                    # Fallback to first value from database if available
                    valid_stage_values = _cached_lookup_values("regulation_stage")
                    stage = valid_stage_values[0] if valid_stage_values else ""
                logger.info(f"Row {row_number}: Regulation Stage is empty, using default from database: '{stage}'")
            
            # Get valid values from database
            valid_stage_values = _cached_lookup_values("regulation_stage")
            if stage:
                resolved_s = resolve_lookup_primary_name(stage, valid_stage_values)
                if resolved_s is not None:
                    stage = resolved_s
            
            if stage and valid_stage_values and stage not in valid_stage_values:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Stage",
                    message=f"Invalid Regulation Stage: '{stage}'. Valid values: {', '.join(valid_stage_values)}",
                    error_code="INVALID_STAGE"
                ))
                continue
            
            stage_id = get_lookup_id_by_name("regulation_stage", stage)
            if stage_id is None:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Stage",
                    message=f"Regulation Stage '{stage}' not found in database",
                    error_code="STAGE_NOT_FOUND"
                ))
                continue
            
            # Validate Compliance Level (required)
            compliance_level = str(row.get("Compliance Level", "")).strip() if pd.notna(row.get("Compliance Level")) else ""
            # If empty, use default (first value from database)
            if not compliance_level:
                compliance_level = _default_lookup_cached("regulation_compliance_level")
                if not compliance_level:
                    # Fallback to first value from database if available
                    valid_compliance_level_values = _cached_lookup_values("regulation_compliance_level")
                    compliance_level = valid_compliance_level_values[0] if valid_compliance_level_values else ""
                logger.info(f"Row {row_number}: Compliance Level is empty, using default from database: '{compliance_level}'")
            
            # Get valid values from database
            valid_compliance_level_values = _cached_lookup_values("regulation_compliance_level")
            if compliance_level:
                resolved_cl = resolve_lookup_primary_name(compliance_level, valid_compliance_level_values)
                if resolved_cl is not None:
                    compliance_level = resolved_cl
            
            if compliance_level and valid_compliance_level_values and compliance_level not in valid_compliance_level_values:
                errors.append(ValidationError(
                    row=row_number,
                    field="Compliance Level",
                    message=f"Invalid Compliance Level: '{compliance_level}'. Valid values: {', '.join(valid_compliance_level_values)}",
                    error_code="INVALID_COMPLIANCE_LEVEL"
                ))
                continue
            
            compliance_level_id = get_lookup_id_by_name("regulation_compliance_level", compliance_level)
            if compliance_level_id is None:
                errors.append(ValidationError(
                    row=row_number,
                    field="Compliance Level",
                    message=f"Compliance Level '{compliance_level}' not found in database",
                    error_code="COMPLIANCE_LEVEL_NOT_FOUND"
                ))
                continue
            
            # Validate optional dates
            comments_date = str(row.get("Comments Date", "")).strip() if pd.notna(row.get("Comments Date")) else ""
            if comments_date and not validate_date_format(comments_date):
                errors.append(ValidationError(
                    row=row_number,
                    field="Comments Date",
                    message=f"Invalid date format: '{comments_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                    error_code="INVALID_DATE_FORMAT"
                ))
                continue
            
            finalisation_date = str(row.get("Finalisation Date", "")).strip() if pd.notna(row.get("Finalisation Date")) else ""
            if finalisation_date and not validate_date_format(finalisation_date):
                errors.append(ValidationError(
                    row=row_number,
                    field="Finalisation Date",
                    message=f"Invalid date format: '{finalisation_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                    error_code="INVALID_DATE_FORMAT"
                ))
                continue
            
            # Validate Parent Regulation Name or Parent Ref.
            parent_id = None
            parent_name = None
            parent_ref = None
            has_name = pd.notna(row.get("Parent Regulation Name"))
            has_ref = pd.notna(row.get("Parent Ref."))
            
            if has_name:
                parent_name = str(row.get("Parent Regulation Name", "")).strip()
            if has_ref:
                parent_ref = str(row.get("Parent Ref.", "")).strip()
            
            # Parent in same file: allow without DB lookup; Java will resolve from batch
            parent_in_file = (has_ref and parent_ref and parent_ref.lower() in refs_in_file) or (has_name and parent_name and parent_name.lower() in names_in_file)
            if parent_in_file:
                pass  # Do not set parent_id; Java will resolve from batch
            else:
                # If both are provided, validate they refer to the same parent
                if has_name and parent_name and has_ref and parent_ref:
                    parent_id_by_name = get_regulation_id_by_name(parent_name)
                    parent_id_by_ref = get_regulation_id_by_ref_number(parent_ref)
                    if parent_id_by_name and parent_id_by_ref:
                        if parent_id_by_name != parent_id_by_ref:
                            errors.append(ValidationError(
                                row=row_number,
                                field="Parent Ref.",
                                message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different regulations",
                                error_code="PARENT_NAME_REF_MISMATCH"
                            ))
                            continue
                    elif parent_id_by_name:
                        parent_id = parent_id_by_name
                    elif parent_id_by_ref:
                        parent_id = parent_id_by_ref
                
                if parent_id is None and has_name and parent_name:
                    parent_id = get_regulation_id_by_name(parent_name)
                    if parent_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Regulation Name",
                            message=f"Parent Regulation with name '{parent_name}' not found",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                
                if parent_id is None and has_ref and parent_ref:
                    parent_id = get_regulation_id_by_ref_number(parent_ref)
                    if parent_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message=f"Parent Regulation with reference '{parent_ref}' not found",
                            error_code="PARENT_REF_NOT_FOUND"
                        ))
                        continue
            
            # Validate BUDG Status (optional, but validate if provided)
            budg_status = str(row.get("BUDG Status", "")).strip() if pd.notna(row.get("BUDG Status")) else ""
            # If empty, use default (first value from database)
            if not budg_status:
                budg_status = _default_lookup_cached("regulation_status")
                if not budg_status:
                    # Fallback to first value from database if available
                    valid_budg_status_values = _cached_lookup_values("regulation_status")
                    budg_status = valid_budg_status_values[0] if valid_budg_status_values else ""
                logger.info(f"Row {row_number}: BUDG Status is empty, using default from database: '{budg_status}'")
            
            if budg_status:
                # Get valid values from database
                valid_budg_status_values = _cached_lookup_values("regulation_status")
                resolved_b = resolve_lookup_primary_name(budg_status, valid_budg_status_values)
                if resolved_b is not None:
                    budg_status = resolved_b
                
                if valid_budg_status_values and budg_status not in valid_budg_status_values:
                    errors.append(ValidationError(
                        row=row_number,
                        field="BUDG Status",
                        message=f"Invalid BUDG Status: '{budg_status}'. Valid values: {', '.join(valid_budg_status_values)}",
                        error_code="INVALID_BUDG_STATUS"
                    ))
                    continue
                
                status_id = get_lookup_id_by_name("regulation_status", budg_status)
                if status_id is None:
                    errors.append(ValidationError(
                        row=row_number,
                        field="BUDG Status",
                        message=f"BUDG Status '{budg_status}' not found in database",
                        error_code="BUDG_STATUS_NOT_FOUND"
                    ))
                    continue
            
            # Validate Legal Advice Type (optional, but validate if provided)
            legal_advice_type = str(row.get("Legal Advice Type", "")).strip() if pd.notna(row.get("Legal Advice Type")) else ""
            # If empty, use default (first value from database)
            if not legal_advice_type:
                legal_advice_type = _default_lookup_cached("legal_advice_type")
                if not legal_advice_type:
                    # Fallback to first value from database if available
                    valid_legal_advice_type_values = _cached_lookup_values("legal_advice_type")
                    legal_advice_type = valid_legal_advice_type_values[0] if valid_legal_advice_type_values else ""
                logger.info(f"Row {row_number}: Legal Advice Type is empty, using default from database: '{legal_advice_type}'")
            
            if legal_advice_type:
                # Get valid values from database
                valid_legal_advice_type_values = _cached_lookup_values("legal_advice_type")
                resolved_lat = resolve_lookup_primary_name(legal_advice_type, valid_legal_advice_type_values)
                if resolved_lat is not None:
                    legal_advice_type = resolved_lat
                
                if valid_legal_advice_type_values and legal_advice_type not in valid_legal_advice_type_values:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Legal Advice Type",
                        message=f"Invalid Legal Advice Type: '{legal_advice_type}'. Valid values: {', '.join(valid_legal_advice_type_values)}",
                        error_code="INVALID_LEGAL_ADVICE_TYPE"
                    ))
                    continue
                
                legal_advice_type_id = get_lookup_id_by_name("legal_advice_type", legal_advice_type)
                if legal_advice_type_id is None:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Legal Advice Type",
                        message=f"Legal Advice Type '{legal_advice_type}' not found in database",
                        error_code="LEGAL_ADVICE_TYPE_NOT_FOUND"
                    ))
                    continue
            
            # Validate Governance Role (optional, but validate if provided)
            governance_role_id = None
            if pd.notna(row.get("Governance Role")):
                governance_role = str(row.get("Governance Role", "")).strip()
                if governance_role:
                    governance_role_id = get_role_id_by_name(governance_role, module_id=23)
                    if governance_role_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Governance Role",
                            message=f"Governance Role '{governance_role}' not found in database for Regulation module",
                            error_code="GOVERNANCE_ROLE_NOT_FOUND"
                        ))
                        continue
            
            # Add to validated data
            # Send canonical lookup *names* (not numeric IDs) so Java resolves IDs against the app DB.
            # Python may use a different DB config than Tomcat; IDs can drift and break FK constraints.
            validated_row = {
                "operation": "INSERT",
                "row_number": row_number,
                "Regulation Long Name": regulation_long_name,
                "Description": description,
                "Publication Date": parse_date_from_string(publication_date),
                "Compliance Date": parse_date_from_string(compliance_date),
                "Regulation Maturity": maturity,
                "Regulation Probability": probability,
                "Regulation Stage": stage,
                "Compliance Level": compliance_level,
            }
            
            # Add optional fields
            if pd.notna(row.get("Reference")):
                validated_row["Reference"] = str(row.get("Reference", "")).strip()
            if pd.notna(row.get("Short Name")):
                validated_row["Short Name"] = str(row.get("Short Name", "")).strip()
            if pd.notna(row.get("Additional Info")):
                validated_row["Additional Info"] = str(row.get("Additional Info", "")).strip()
            if comments_date:
                validated_row["Comments Date"] = parse_date_from_string(comments_date)
            if finalisation_date:
                validated_row["Finalisation Date"] = parse_date_from_string(finalisation_date)
            if pd.notna(row.get("Legal Advice")):
                validated_row["Legal Advice"] = str(row.get("Legal Advice", "")).strip()
            if parent_id is not None:
                validated_row["Parent_ID"] = parent_id
            if budg_status:
                validated_row["BUDG Status"] = budg_status
            if legal_advice_type:
                validated_row["Legal Advice Type"] = legal_advice_type
            if governance_role_id is not None:
                validated_row["Governance Role_ID"] = governance_role_id  # Send role ID
            
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
                    # All columns present; NaN/empty cells as None so mandatory CF checks run reliably
                    row_dict = {
                        str(col): (None if pd.isna(row.get(col)) else row.get(col))
                        for col in row.index
                    }
                    custom_field_errors, validated_custom_fields = validate_custom_fields_for_row(
                        row_dict, "Regulation", row_number
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
                except Exception as e:
                    logger.error(f"Error validating custom fields for row {row_number}: {e}")
                    # Don't fail the row if custom fields validation fails, just log it

            if merge_excel_custom_field_passthrough and BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE:
                bt = BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE.get("Regulation")
                if bt is not None:
                    merge_excel_custom_field_passthrough(row, validated_row, bt)
            
            validated_data.append(validated_row)
        
        elif upload_option == "Update Existing Items":
            def _has_val(v):
                if v is None: return False
                if pd.isna(v): return False
                s = str(v).strip()
                return bool(s) and s.lower() not in ("nan", "")
            id_raw = row.get("Regulation ID")
            ref_raw = row.get("Reference")
            name_raw = row.get("Regulation Long Name")
            id_by_id = None
            if _has_val(id_raw):
                try:
                    vid = int(float(str(id_raw).strip()))
                    if check_regulation_exists(vid):
                        id_by_id = vid
                except (ValueError, TypeError):
                    pass
            id_by_ref = get_regulation_id_by_ref_number(str(ref_raw).strip()) if _has_val(ref_raw) else None
            id_by_name = get_regulation_id_by_name(str(name_raw).strip()) if _has_val(name_raw) else None
            filled = sum(1 for v in (id_raw, ref_raw, name_raw) if _has_val(v))
            if filled == 0:
                errors.append(ValidationError(row=row_number, field="Regulation ID", message="At least one of Regulation ID, Reference, or Regulation Long Name is required for update", error_code="MISSING_ID"))
                continue
            regulation_id = id_by_id or id_by_ref or id_by_name
            if regulation_id is None:
                errors.append(ValidationError(row=row_number, field="Regulation ID", message="No regulation found for the provided identity (Regulation ID, Reference, or Regulation Long Name)", error_code="ID_NOT_FOUND"))
                continue
            if filled >= 2:
                ids = {x for x in (id_by_id, id_by_ref, id_by_name) if x is not None}
                if len(ids) > 1:
                    errors.append(ValidationError(row=row_number, field="Regulation ID", message="Regulation ID, Reference and Regulation Long Name refer to different regulations", error_code="IDENTITY_MISMATCH"))
                    continue
            
            # Long Name optional on update when ID or Ref identifies the object; backend keeps existing when empty.
            regulation_long_name = str(row.get("Regulation Long Name", "")).strip() if pd.notna(row.get("Regulation Long Name")) else ""
            
            eff_seg_upd = 1
            if get_object_segment_id:
                try:
                    osid = get_object_segment_id(regulation_id, "Regulation")
                    if osid is not None and osid > 0:
                        eff_seg_upd = int(osid)
                except Exception:
                    pass
            if regulation_long_name and check_duplicate_primary_name(regulation_long_name, regulation_id, segment_id=eff_seg_upd):
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Long Name",
                    message=f"Another regulation with name '{regulation_long_name}' already exists in this segment",
                    error_code="DUPLICATE_NAME"
                ))
                continue
            
            # Description optional on update; backend keeps existing when empty.
            description = str(row.get("Description", "")).strip() if pd.notna(row.get("Description")) else ""
            
            # Publication Date optional on update; validate format only when value is provided.
            pub_date_val = get_column_value(row, "Publication Date", list(df.columns))
            publication_date = str(pub_date_val).strip() if pd.notna(pub_date_val) and pub_date_val is not None else ""
            if publication_date and not validate_date_format(publication_date):
                errors.append(ValidationError(
                    row=row_number,
                    field="Publication Date",
                    message=f"Invalid date format: '{publication_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                    error_code="INVALID_DATE_FORMAT"
                ))
                continue
            
            # Compliance Date optional on update: only validate and set when provided
            comp_date_val = get_column_value(row, "Compliance Date", list(df.columns))
            compliance_date = str(comp_date_val).strip() if pd.notna(comp_date_val) and comp_date_val is not None else ""
            if compliance_date:
                if not validate_date_format(compliance_date):
                    errors.append(ValidationError(
                        row=row_number,
                        field="Compliance Date",
                        message=f"Invalid date format: '{compliance_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                        error_code="INVALID_DATE_FORMAT"
                    ))
                    continue
            
            # Validate Regulation Maturity (required)
            maturity = str(row.get("Regulation Maturity", "")).strip() if pd.notna(row.get("Regulation Maturity")) else ""
            # If empty, use default (first value from database)
            if not maturity:
                maturity = _default_lookup_cached("regulation_maturity")
                if not maturity:
                    # Fallback to hardcoded list if database query fails
                    valid_maturity_values = _cached_lookup_values("regulation_maturity")
                    maturity = valid_maturity_values[0] if valid_maturity_values else ""
                logger.info(f"Row {row_number}: Regulation Maturity is empty, using default from database: '{maturity}'")
            
            # Get valid values from database
            valid_maturity_values = _cached_lookup_values("regulation_maturity")
            if maturity:
                resolved_m = resolve_lookup_primary_name(maturity, valid_maturity_values)
                if resolved_m is not None:
                    maturity = resolved_m
            
            if maturity and maturity not in valid_maturity_values:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Maturity",
                    message=f"Invalid Regulation Maturity: '{maturity}'. Valid values: {', '.join(valid_maturity_values)}",
                    error_code="INVALID_MATURITY"
                ))
                continue
            
            maturity_id = get_lookup_id_by_name("regulation_maturity", maturity)
            if maturity_id is None:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Maturity",
                    message=f"Regulation Maturity '{maturity}' not found in database",
                    error_code="MATURITY_NOT_FOUND"
                ))
                continue
            
            # Validate Regulation Probability (required)
            probability = str(row.get("Regulation Probability", "")).strip() if pd.notna(row.get("Regulation Probability")) else ""
            # If empty, use default (first value from database)
            if not probability:
                probability = _default_lookup_cached("regulation_probability")
                if not probability:
                    # Fallback to first value from database if available
                    valid_probability_values = _cached_lookup_values("regulation_probability")
                    probability = valid_probability_values[0] if valid_probability_values else ""
                logger.info(f"Row {row_number}: Regulation Probability is empty, using default from database: '{probability}'")
            
            # Get valid values from database
            valid_probability_values = _cached_lookup_values("regulation_probability")
            if probability:
                resolved_p = resolve_lookup_primary_name(probability, valid_probability_values)
                if resolved_p is not None:
                    probability = resolved_p
            
            if probability and valid_probability_values and probability not in valid_probability_values:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Probability",
                    message=f"Invalid Regulation Probability: '{probability}'. Valid values: {', '.join(valid_probability_values)}",
                    error_code="INVALID_PROBABILITY"
                ))
                continue
            
            probability_id = get_lookup_id_by_name("regulation_probability", probability)
            if probability_id is None:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Probability",
                    message=f"Regulation Probability '{probability}' not found in database",
                    error_code="PROBABILITY_NOT_FOUND"
                ))
                continue
            
            # Validate Regulation Stage (required)
            stage = str(row.get("Regulation Stage", "")).strip() if pd.notna(row.get("Regulation Stage")) else ""
            # If empty, use default (first value from database)
            if not stage:
                stage = _default_lookup_cached("regulation_stage")
                if not stage:
                    # Fallback to first value from database if available
                    valid_stage_values = _cached_lookup_values("regulation_stage")
                    stage = valid_stage_values[0] if valid_stage_values else ""
                logger.info(f"Row {row_number}: Regulation Stage is empty, using default from database: '{stage}'")
            
            # Get valid values from database
            valid_stage_values = _cached_lookup_values("regulation_stage")
            if stage:
                resolved_s = resolve_lookup_primary_name(stage, valid_stage_values)
                if resolved_s is not None:
                    stage = resolved_s
            
            if stage and valid_stage_values and stage not in valid_stage_values:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Stage",
                    message=f"Invalid Regulation Stage: '{stage}'. Valid values: {', '.join(valid_stage_values)}",
                    error_code="INVALID_STAGE"
                ))
                continue
            
            stage_id = get_lookup_id_by_name("regulation_stage", stage)
            if stage_id is None:
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation Stage",
                    message=f"Regulation Stage '{stage}' not found in database",
                    error_code="STAGE_NOT_FOUND"
                ))
                continue
            
            # Validate Compliance Level (required)
            compliance_level = str(row.get("Compliance Level", "")).strip() if pd.notna(row.get("Compliance Level")) else ""
            # If empty, use default (first value from database)
            if not compliance_level:
                compliance_level = _default_lookup_cached("regulation_compliance_level")
                if not compliance_level:
                    # Fallback to first value from database if available
                    valid_compliance_level_values = _cached_lookup_values("regulation_compliance_level")
                    compliance_level = valid_compliance_level_values[0] if valid_compliance_level_values else ""
                logger.info(f"Row {row_number}: Compliance Level is empty, using default from database: '{compliance_level}'")
            
            # Get valid values from database
            valid_compliance_level_values = _cached_lookup_values("regulation_compliance_level")
            if compliance_level:
                resolved_cl = resolve_lookup_primary_name(compliance_level, valid_compliance_level_values)
                if resolved_cl is not None:
                    compliance_level = resolved_cl
            
            if compliance_level and valid_compliance_level_values and compliance_level not in valid_compliance_level_values:
                errors.append(ValidationError(
                    row=row_number,
                    field="Compliance Level",
                    message=f"Invalid Compliance Level: '{compliance_level}'. Valid values: {', '.join(valid_compliance_level_values)}",
                    error_code="INVALID_COMPLIANCE_LEVEL"
                ))
                continue
            
            compliance_level_id = get_lookup_id_by_name("regulation_compliance_level", compliance_level)
            if compliance_level_id is None:
                errors.append(ValidationError(
                    row=row_number,
                    field="Compliance Level",
                    message=f"Compliance Level '{compliance_level}' not found in database",
                    error_code="COMPLIANCE_LEVEL_NOT_FOUND"
                ))
                continue
            
            # Validate optional dates
            comments_date = str(row.get("Comments Date", "")).strip() if pd.notna(row.get("Comments Date")) else ""
            if comments_date and not validate_date_format(comments_date):
                errors.append(ValidationError(
                    row=row_number,
                    field="Comments Date",
                    message=f"Invalid date format: '{comments_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                    error_code="INVALID_DATE_FORMAT"
                ))
                continue
            
            finalisation_date = str(row.get("Finalisation Date", "")).strip() if pd.notna(row.get("Finalisation Date")) else ""
            if finalisation_date and not validate_date_format(finalisation_date):
                errors.append(ValidationError(
                    row=row_number,
                    field="Finalisation Date",
                    message=f"Invalid date format: '{finalisation_date}'. Expected format: '25th May 2018' or 'YYYY-MM-DD'",
                    error_code="INVALID_DATE_FORMAT"
                ))
                continue
            
            # Validate Parent Regulation Name or Parent Ref.
            parent_id = None
            parent_name = None
            parent_ref = None
            has_name = pd.notna(row.get("Parent Regulation Name"))
            has_ref = pd.notna(row.get("Parent Ref."))
            
            if has_name:
                parent_name = str(row.get("Parent Regulation Name", "")).strip()
            if has_ref:
                parent_ref = str(row.get("Parent Ref.", "")).strip()
            
            # Parent in same file: allow without DB lookup; Java will resolve from batch
            parent_in_file = (has_ref and parent_ref and parent_ref.lower() in refs_in_file) or (has_name and parent_name and parent_name.lower() in names_in_file)
            if parent_in_file:
                pass  # Do not set parent_id; Java will resolve from batch
            else:
                # If both are provided, validate they refer to the same parent
                if has_name and parent_name and has_ref and parent_ref:
                    parent_id_by_name = get_regulation_id_by_name(parent_name)
                    parent_id_by_ref = get_regulation_id_by_ref_number(parent_ref)
                    if parent_id_by_name and parent_id_by_ref:
                        if parent_id_by_name != parent_id_by_ref:
                            errors.append(ValidationError(
                                row=row_number,
                                field="Parent Ref.",
                                message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different regulations",
                                error_code="PARENT_NAME_REF_MISMATCH"
                            ))
                            continue
                    elif parent_id_by_name:
                        parent_id = parent_id_by_name
                    elif parent_id_by_ref:
                        parent_id = parent_id_by_ref
                
                if parent_id is None and has_name and parent_name:
                    parent_id = get_regulation_id_by_name(parent_name)
                    if parent_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Regulation Name",
                            message=f"Parent Regulation with name '{parent_name}' not found",
                            error_code="PARENT_NOT_FOUND"
                        ))
                        continue
                
                if parent_id is None and has_ref and parent_ref:
                    parent_id = get_regulation_id_by_ref_number(parent_ref)
                    if parent_id is None:
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message=f"Parent Regulation with reference '{parent_ref}' not found",
                            error_code="PARENT_REF_NOT_FOUND"
                        ))
                        continue
            
            # Validate BUDG Status (optional, but validate if provided)
            budg_status = str(row.get("BUDG Status", "")).strip() if pd.notna(row.get("BUDG Status")) else ""
            # If empty, use default (first value from database)
            if not budg_status:
                budg_status = _default_lookup_cached("regulation_status")
                if not budg_status:
                    # Fallback to first value from database if available
                    valid_budg_status_values = _cached_lookup_values("regulation_status")
                    budg_status = valid_budg_status_values[0] if valid_budg_status_values else ""
                logger.info(f"Row {row_number}: BUDG Status is empty, using default from database: '{budg_status}'")
            
            if budg_status:
                # Get valid values from database
                valid_budg_status_values = _cached_lookup_values("regulation_status")
                resolved_b = resolve_lookup_primary_name(budg_status, valid_budg_status_values)
                if resolved_b is not None:
                    budg_status = resolved_b
                
                if valid_budg_status_values and budg_status not in valid_budg_status_values:
                    errors.append(ValidationError(
                        row=row_number,
                        field="BUDG Status",
                        message=f"Invalid BUDG Status: '{budg_status}'. Valid values: {', '.join(valid_budg_status_values)}",
                        error_code="INVALID_BUDG_STATUS"
                    ))
                    continue
                
                status_id = get_lookup_id_by_name("regulation_status", budg_status)
                if status_id is None:
                    errors.append(ValidationError(
                        row=row_number,
                        field="BUDG Status",
                        message=f"BUDG Status '{budg_status}' not found in database",
                        error_code="BUDG_STATUS_NOT_FOUND"
                    ))
                    continue
            
            # Validate Legal Advice Type (optional, but validate if provided)
            legal_advice_type = str(row.get("Legal Advice Type", "")).strip() if pd.notna(row.get("Legal Advice Type")) else ""
            # If empty, use default (first value from database)
            if not legal_advice_type:
                legal_advice_type = _default_lookup_cached("legal_advice_type")
                if not legal_advice_type:
                    # Fallback to first value from database if available
                    valid_legal_advice_type_values = _cached_lookup_values("legal_advice_type")
                    legal_advice_type = valid_legal_advice_type_values[0] if valid_legal_advice_type_values else ""
                logger.info(f"Row {row_number}: Legal Advice Type is empty, using default from database: '{legal_advice_type}'")
            
            if legal_advice_type:
                # Get valid values from database
                valid_legal_advice_type_values = _cached_lookup_values("legal_advice_type")
                resolved_lat = resolve_lookup_primary_name(legal_advice_type, valid_legal_advice_type_values)
                if resolved_lat is not None:
                    legal_advice_type = resolved_lat
                
                if valid_legal_advice_type_values and legal_advice_type not in valid_legal_advice_type_values:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Legal Advice Type",
                        message=f"Invalid Legal Advice Type: '{legal_advice_type}'. Valid values: {', '.join(valid_legal_advice_type_values)}",
                        error_code="INVALID_LEGAL_ADVICE_TYPE"
                    ))
                    continue
                
                legal_advice_type_id = get_lookup_id_by_name("legal_advice_type", legal_advice_type)
                if legal_advice_type_id is None:
                    errors.append(ValidationError(
                        row=row_number,
                        field="Legal Advice Type",
                        message=f"Legal Advice Type '{legal_advice_type}' not found in database",
                        error_code="LEGAL_ADVICE_TYPE_NOT_FOUND"
                    ))
                    continue
            
            # Add to validated data (lookup names only; Java resolves IDs on the app server DB)
            validated_row = {
                "operation": "UPDATE",
                "row_number": row_number,
                "Regulation ID": regulation_id,
                "Regulation Long Name": regulation_long_name,
                "Description": description,
                "Publication Date": parse_date_from_string(publication_date) if publication_date else None,
                "Regulation Maturity": maturity,
                "Regulation Probability": probability,
                "Regulation Stage": stage,
                "Compliance Level": compliance_level,
            }
            if compliance_date:
                validated_row["Compliance Date"] = parse_date_from_string(compliance_date)
            
            # Add optional fields (if Short Name is provided, it becomes the new value for the regulation)
            if pd.notna(row.get("Reference")):
                validated_row["Reference"] = str(row.get("Reference", "")).strip()
            if pd.notna(row.get("Short Name")):
                validated_row["Short Name"] = str(row.get("Short Name", "")).strip()
            if pd.notna(row.get("Additional Info")):
                validated_row["Additional Info"] = str(row.get("Additional Info", "")).strip()
            if comments_date:
                validated_row["Comments Date"] = parse_date_from_string(comments_date)
            if finalisation_date:
                validated_row["Finalisation Date"] = parse_date_from_string(finalisation_date)
            if pd.notna(row.get("Legal Advice")):
                validated_row["Legal Advice"] = str(row.get("Legal Advice", "")).strip()
            if regulation_id and parent_id is not None:
                if would_create_parent_cycle_by_entity:
                    if would_create_parent_cycle_by_entity(get_db_connection, "Regulation", regulation_id, parent_id):
                        errors.append(ValidationError(
                            row=row_number,
                            field="Parent Ref.",
                            message="Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.",
                            error_code="PARENT_CYCLE",
                        ))
                        continue
                if get_object_segment_id:
                    obj_seg = get_object_segment_id(regulation_id, "Regulation")
                    parent_seg = get_object_segment_id(parent_id, "Regulation")
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
            if budg_status:
                validated_row["BUDG Status"] = budg_status
            if legal_advice_type:
                validated_row["Legal Advice Type"] = legal_advice_type
            
            # Validate and add custom fields
            if validate_custom_fields_for_row:
                try:
                    row_dict = {
                        str(col): (None if pd.isna(row.get(col)) else row.get(col))
                        for col in row.index
                    }
                    custom_field_errors, validated_custom_fields = validate_custom_fields_for_row(
                        row_dict, "Regulation", row_number
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
                except Exception as e:
                    logger.error(f"Error validating custom fields for row {row_number}: {e}")
                    # Don't fail the row if custom fields validation fails, just log it

            if merge_excel_custom_field_passthrough and BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE:
                bt = BUILTIN_EXCEL_COLUMNS_BY_OBJECT_TYPE.get("Regulation")
                if bt is not None:
                    merge_excel_custom_field_passthrough(row, validated_row, bt)
            
            validated_data.append(validated_row)
        
        elif upload_option == "Remove Existing Items":
            # Validate Regulation ID
            regulation_id = row.get("Regulation ID")
            
            if pd.isna(regulation_id):
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation ID",
                    message="Regulation ID is required for delete operations",
                    error_code="MISSING_ID"
                ))
                continue
            
            try:
                regulation_id = int(regulation_id)
            except (ValueError, TypeError):
                errors.append(ValidationError(
                    row=row_number,
                    field="Regulation ID",
                    message="Regulation ID must be a valid integer",
                    error_code="INVALID_ID"
                ))
                continue
            
            # For delete operations, check if ID exists
            # If it doesn't exist, we skip it (idempotent delete - already deleted)
            if not check_regulation_exists(regulation_id):
                # Skip this row silently - it's already deleted
                # This allows idempotent delete operations
                continue
            
            # Add to validated data only if regulation exists
            validated_data.append({
                "operation": "DELETE",
                "row_number": row_number,
                "Regulation ID": regulation_id
            })
    
    return errors, validated_data


@app.get("/")
def read_root():
    """Health check endpoint"""
    return {
        "service": "Regulation Bulk Validation Service",
        "status": "running",
        "timestamp": datetime.now().isoformat()
    }


@app.post("/api/validate", response_model=ValidationResponse)
async def validate_bulk_upload(request: ValidationRequest):
    """
    Validate bulk upload Excel file for regulations
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
        
        # Step 2: Validate row data
        row_errors, validated_data = validate_row_data(df, request.upload_option)
        all_errors.extend(row_errors)

        if filter_rows_with_blocking_custom_field_errors:
            validated_data = filter_rows_with_blocking_custom_field_errors(
                validated_data, all_errors
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

