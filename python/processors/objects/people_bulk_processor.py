"""
People Bulk Upload Validation Module
Validates Excel files for People bulk uploads and resolves lookups to IDs.
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
    Return expected sheet name for People bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create People"
    if upload_option == "Update Existing Items":
        return "Update People"
    if upload_option == "Remove Existing Items":
        return "Delete People"
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


def _is_effectively_empty_excel_row(row: pd.Series) -> bool:
    """Skip trailing blank rows Excel includes in the used range (no errors for those)."""
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


def _series_str(row: pd.Series, *keys: str) -> Optional[str]:
    """First non-empty string from row for any of the given column names."""
    for k in keys:
        if k not in row.index:
            continue
        v = row[k]
        if v is None:
            continue
        try:
            if pd.isna(v):
                continue
        except TypeError:
            pass
        s = str(v).strip()
        if s and s.lower() not in ("nan",):
            return s
    return None


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
        # Determine the correct column name based on table
        column_name = "PrimaryName"  # default
        if table in ["employment_type", "people_lifecycle_status"]:
            column_name = "primary_Name"
        
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT ID FROM {table} WHERE {column_name} = %s LIMIT 1", (str(primary_name).strip(),))
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


def get_person_row_by_id(person_id: int) -> Optional[Dict[str, Any]]:
    """Return dict with First_Name, Last_Name, Email for the person, or None if not found."""
    if not person_id:
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT First_Name, Last_Name, Email FROM people WHERE ID = %s AND Deleted_date IS NULL LIMIT 1",
                    (int(person_id),),
                )
                row = cur.fetchone()
                if not row:
                    return None
                return {"First_Name": row[0], "Last_Name": row[1], "Email": row[2] or ""}
    except Exception as e:
        logger.warning("get_person_row_by_id error: %s", e)
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


def get_first_profile_id() -> Optional[int]:
    return get_first_id_from_table("profile")


def get_first_employment_type_id() -> Optional[int]:
    return get_first_id_from_table("employment_type")


def get_first_people_lifecycle_id() -> Optional[int]:
    return get_first_id_from_table("people_lifecycle_status")


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


def get_profile_role_id_by_name(role_name: Optional[str]) -> Optional[int]:
    """Get role ID from role table by primaryname for Profile field"""
    if not role_name or not str(role_name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM role WHERE LOWER(primaryname) = LOWER(%s) LIMIT 1", (str(role_name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_profile_role_id_by_name error: %s", e)
        return None


def get_org_unit_id_by_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM org_unit WHERE LOWER(Name) = LOWER(%s) LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_org_unit_id_by_name error: %s", e)
        return None


def get_org_unit_id_by_reference(reference: Optional[str]) -> Optional[int]:
    """Get org unit ID by reference. Returns None if reference doesn't exist."""
    if not reference or not str(reference).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM org_unit WHERE Reference = %s LIMIT 1", (str(reference).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_org_unit_id_by_reference error: %s", e)
        return None


def get_org_unit_name_by_id(org_unit_id: Optional[int]) -> Optional[str]:
    """Get org unit name by ID. Returns None if ID doesn't exist."""
    if not org_unit_id:
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT Name FROM org_unit WHERE ID = %s LIMIT 1", (int(org_unit_id),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_org_unit_name_by_id error: %s", e)
        return None


def get_employment_type_id_by_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM employment_type WHERE LOWER(primary_Name) = LOWER(%s) LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_employment_type_id_by_name error: %s", e)
        return None


def check_email_exists(email: Optional[str]) -> bool:
    """Check if email already exists in the database (case-insensitive)."""
    if not email or not str(email).strip():
        return False
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1 FROM people WHERE LOWER(Email) = LOWER(%s) LIMIT 1", (str(email).strip(),))
                row = cur.fetchone()
                return row is not None
    except Exception as e:
        logger.warning("check_email_exists error: %s", e)
        return False


def validate_and_resolve_people(df: pd.DataFrame, upload_option: str, user_id: int, segment_mode: Optional[str] = None, segment: Optional[str] = None) -> Tuple[List[Dict[str, Any]], List[ValidationError]]:
    """
    Validate and resolve People records from DataFrame.
    Returns: (validated_data, errors)
    """
    validated_data = []
    errors = []
    # Track emails in template for duplicate detection (case-insensitive)
    template_emails = set()
    
    operation = "INSERT"
    if upload_option == "Update Existing Items":
        operation = "UPDATE"
    elif upload_option == "Remove Existing Items":
        operation = "DELETE"

    if operation == "INSERT" and not df.empty:
        non_empty_idx = [i for i in df.index if not _is_effectively_empty_excel_row(df.loc[i])]
        df = df.loc[non_empty_idx] if non_empty_idx else df.iloc[0:0]

    for idx, row in df.iterrows():
        row_number = idx + 2  # Excel row number (header is row 1)
        row_data = {"row_number": row_number, "operation": operation}
        
        try:
            if operation == "DELETE":
                # DELETE: Only need ID
                entity_id = row.get("People ID") or row.get("ID")
                if pd.isna(entity_id):
                    errors.append(ValidationError(row_number, "People ID", "People ID is required for deletion", "MISSING_REQUIRED"))
                    continue
                
                row_data["ID"] = int(entity_id)
                row_data["People ID"] = int(entity_id)
                
                # Check if exists
                if not _exists_in_table("people", row_data["ID"]):
                    errors.append(ValidationError(row_number, "People ID", f"People with ID {row_data['ID']} does not exist", "NOT_FOUND"))
                    continue
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "People", row_number, errors)
                
                validated_data.append(row_data)
                
            elif operation == "UPDATE":
                def _has_val(v):
                    if v is None or (hasattr(v, '__len__') and not v): return False
                    if pd.isna(v): return False
                    s = str(v).strip()
                    return bool(s) and s.lower() not in ("nan", "")
                id_raw = row.get("People ID") or row.get("ID")
                email_raw = row.get("Email") or row.get("E-mail") or row.get("email")
                first_raw = row.get("First Name")
                last_raw = row.get("Last Name")
                # If First Name and Last Name are empty, try single "Name" column (split into first + last)
                if not _has_val(first_raw) and not _has_val(last_raw) and _has_val(row.get("Name")):
                    name_parts = str(row.get("Name")).strip().split(None, 1)
                    first_raw = name_parts[0] if name_parts else None
                    last_raw = name_parts[1] if len(name_parts) > 1 else None
                id_by_id = None
                if _has_val(id_raw):
                    try:
                        vid = int(float(str(id_raw).strip()))
                        if _exists_in_table("people", vid):
                            id_by_id = vid
                    except (ValueError, TypeError):
                        pass
                email_clean = str(email_raw).strip().lower() if _has_val(email_raw) else None
                id_by_email = get_user_id_by_email(email_clean) if email_clean else None
                id_by_first_last = None
                if _has_val(first_raw) and _has_val(last_raw):
                    id_by_first_last = get_user_id_by_name(str(first_raw).strip(), str(last_raw).strip())
                has_id = id_by_id is not None
                has_email = id_by_email is not None
                has_first_last = id_by_first_last is not None
                if not (has_id or has_email or has_first_last):
                    errors.append(ValidationError(row_number, "People ID", "At least one of People ID, Email, or First Name and Last Name is required for update", "MISSING_REQUIRED"))
                    continue
                resolved_id = id_by_id or id_by_email or id_by_first_last
                if resolved_id is None:
                    errors.append(ValidationError(row_number, "People ID", "No person found for the provided identity (People ID, Email, or First/Last Name)", "NOT_FOUND"))
                    continue
                ids_provided = [x for x in (id_by_id, id_by_email, id_by_first_last) if x is not None]
                if len(ids_provided) >= 2 and len(set(ids_provided)) > 1:
                    errors.append(ValidationError(row_number, "People ID", "People ID, Email and First/Last Name must refer to the same person", "IDENTITY_MISMATCH"))
                    continue
                row_data["ID"] = resolved_id
                row_data["People ID"] = resolved_id
                
                # First Name, Last Name, Email: optional for update; fill from DB if only ID provided (or from Name split)
                existing = get_person_row_by_id(resolved_id)
                first_name = row.get("First Name")
                if not _has_val(first_name) and first_raw is not None:
                    first_name = first_raw
                if not pd.isna(first_name) and str(first_name).strip():
                    row_data["First Name"] = str(first_name).strip()
                elif existing:
                    row_data["First Name"] = (existing.get("First_Name") or "").strip()
                else:
                    row_data["First Name"] = ""
                last_name = row.get("Last Name")
                if not _has_val(last_name) and last_raw is not None:
                    last_name = last_raw
                if not pd.isna(last_name) and str(last_name).strip():
                    row_data["Last Name"] = str(last_name).strip()
                elif existing:
                    row_data["Last Name"] = (existing.get("Last_Name") or "").strip()
                else:
                    row_data["Last Name"] = ""
                email = row.get("Email") or row.get("E-mail") or row.get("email")
                if not pd.isna(email) and str(email).strip():
                    row_data["Email"] = str(email).strip()
                elif existing:
                    row_data["Email"] = (existing.get("Email") or "").strip()
                else:
                    row_data["Email"] = ""
                
                # Profile: optional for update; if provided, resolve and set
                profile = row.get("Profile")
                if not pd.isna(profile) and str(profile).strip():
                    profile_value = str(profile).strip()
                    profile_id = get_profile_role_id_by_name(profile_value)
                    if not profile_id:
                        errors.append(ValidationError(row_number, "Profile", f"Profile '{profile_value}' not found in role table", "NOT_FOUND"))
                        continue
                    row_data["System_Role"] = profile_id
                    row_data["Profile"] = profile_value
                
                # Employment Type (optional - use first value if empty, else None)
                employment_type_name = row.get("Employment Type")
                employment_type_id = None
                if pd.isna(employment_type_name) or not str(employment_type_name).strip():
                    employment_type_id = get_first_employment_type_id()
                else:
                    employment_type_id = get_employment_type_id_by_name(str(employment_type_name).strip())
                    if not employment_type_id:
                        errors.append(ValidationError(row_number, "Employment Type", f"Employment Type '{employment_type_name}' not found", "NOT_FOUND"))
                        continue
                if employment_type_id is not None:
                    row_data["employment_type"] = employment_type_id
                
                # Lifecycle (optional - use first value if empty, else None)
                lifecycle_name = row.get("Lifecycle")
                lifecycle_id = None
                if pd.isna(lifecycle_name) or not str(lifecycle_name).strip():
                    lifecycle_id = get_first_people_lifecycle_id()
                else:
                    lifecycle_id = get_lookup_id_by_name("people_lifecycle_status", str(lifecycle_name).strip())
                    if not lifecycle_id:
                        errors.append(ValidationError(row_number, "Lifecycle", f"Lifecycle '{lifecycle_name}' not found", "NOT_FOUND"))
                        continue
                if lifecycle_id is not None:
                    row_data["lifecycle"] = lifecycle_id
                
                # Optional fields - people table
                function_name = row.get("Function")
                if not pd.isna(function_name) and str(function_name).strip():
                    row_data["Function"] = str(function_name).strip()
                
                function_desc = row.get("Function Description")
                if not pd.isna(function_desc) and str(function_desc).strip():
                    row_data["Function Description"] = str(function_desc).strip()
                
                description = row.get("Description")
                if not pd.isna(description) and str(description).strip():
                    row_data["Description"] = str(description).strip()
                
                password = row.get("Password")
                if not pd.isna(password) and str(password).strip():
                    row_data["Password"] = str(password).strip()
                
                # BUDG Status (optional)
                budg_status = row.get("BUDG Status")
                if not pd.isna(budg_status) and str(budg_status).strip():
                    status_id = get_lookup_id_by_name("status", str(budg_status).strip())
                    if status_id:
                        row_data["status_id"] = status_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Status", f"Status '{budg_status}' not found", "NOT_FOUND"))
                
                # Org Unit Reference (ID)
                org_unit_ref = row.get("Org Unit Reference")
                if not pd.isna(org_unit_ref) and str(org_unit_ref).strip():
                    try:
                        row_data["Org_Unit_ID"] = int(org_unit_ref)
                    except ValueError:
                        errors.append(ValidationError(row_number, "Org Unit Reference", "Org Unit Reference must be a valid ID", "INVALID_VALUE"))
                
                # Org Unit Name (lookup)
                org_unit_name = row.get("Org Unit Name")
                if not pd.isna(org_unit_name) and str(org_unit_name).strip():
                    org_unit_id = get_org_unit_id_by_name(str(org_unit_name).strip())
                    if org_unit_id:
                        row_data["Org_Unit_ID"] = org_unit_id
                    else:
                        errors.append(ValidationError(row_number, "Org Unit Name", f"Org Unit '{org_unit_name}' not found", "NOT_FOUND"))
                
                # Optional fields - people_details table
                office_location = row.get("Office Location")
                if not pd.isna(office_location) and str(office_location).strip():
                    row_data["Office Location"] = str(office_location).strip()
                
                internal_mail_code = row.get("Internal Mail Code")
                if not pd.isna(internal_mail_code) and str(internal_mail_code).strip():
                    row_data["Internal Mail Code"] = str(internal_mail_code).strip()
                
                office_telephone = row.get("Office Telephone")
                if not pd.isna(office_telephone) and str(office_telephone).strip():
                    row_data["Office Telephone"] = str(office_telephone).strip()
                
                mobile_cell = row.get("Mobile/Cell")
                if not pd.isna(mobile_cell) and str(mobile_cell).strip():
                    row_data["Mobile Telephone"] = str(mobile_cell).strip()
                
                lan_id = row.get("LAN ID") or row.get("LAN Id")
                if not pd.isna(lan_id) and str(lan_id).strip():
                    row_data["LAN ID"] = str(lan_id).strip()
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(row, row_data, "People", row_number, errors)
                
                validated_data.append(row_data)
                
            else:  # INSERT
                # Required fields — accept template column aliases and single "Name" column (split)
                first_name = _series_str(row, "First Name", "FirstName", "First_Name")
                last_name = _series_str(row, "Last Name", "LastName", "Last_Name")
                name_full = _series_str(row, "Name")
                if name_full:
                    parts = name_full.split(None, 1)
                    if not first_name and parts:
                        first_name = parts[0]
                    if not last_name and len(parts) > 1:
                        last_name = parts[1].strip()
                if not first_name:
                    errors.append(ValidationError(row_number, "First Name", "First Name is required", "MISSING_REQUIRED"))
                    continue
                row_data["First Name"] = first_name
                if not last_name:
                    errors.append(ValidationError(row_number, "Last Name", "Last Name is required", "MISSING_REQUIRED"))
                    continue
                row_data["Last Name"] = last_name

                email = _series_str(row, "Email", "E-mail", "email")
                if not email:
                    errors.append(ValidationError(row_number, "Email", "Email is required", "MISSING_REQUIRED"))
                    continue
                email_value = email
                email_lower = email_value.lower()
                
                # Check for duplicate email in template
                if email_lower in template_emails:
                    errors.append(ValidationError(row_number, "Email", f"Duplicate email '{email_value}' found within the template. Each email must be unique in the upload file.", "DUPLICATE_EMAIL_IN_TEMPLATE"))
                    continue
                
                # Check for duplicate email in database
                if check_email_exists(email_value):
                    errors.append(ValidationError(row_number, "Email", f"Email '{email_value}' already exists in the database", "DUPLICATE_EMAIL"))
                    continue
                
                # Add to template emails set
                template_emails.add(email_lower)
                row_data["Email"] = email_value
                
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
                
                # Profile (required - lookup from role table)
                profile = row.get("Profile")
                if pd.isna(profile) or not str(profile).strip():
                    errors.append(ValidationError(row_number, "Profile", "Profile is required", "MISSING_REQUIRED"))
                    continue
                profile_value = str(profile).strip()
                profile_id = get_profile_role_id_by_name(profile_value)
                if not profile_id:
                    errors.append(ValidationError(row_number, "Profile", f"Profile '{profile_value}' not found in role table", "NOT_FOUND"))
                    continue
                row_data["System_Role"] = profile_id
                row_data["Profile"] = profile_value  # Pass profile name to Java
                
                # Employment Type (optional - use first value if empty, else None)
                employment_type_name = row.get("Employment Type")
                employment_type_id = None
                if pd.isna(employment_type_name) or not str(employment_type_name).strip():
                    employment_type_id = get_first_employment_type_id()
                else:
                    employment_type_id = get_employment_type_id_by_name(str(employment_type_name).strip())
                    if not employment_type_id:
                        errors.append(ValidationError(row_number, "Employment Type", f"Employment Type '{employment_type_name}' not found", "NOT_FOUND"))
                        continue
                if employment_type_id is not None:
                    row_data["employment_type"] = employment_type_id
                
                # Lifecycle (optional - use first value if empty, else None)
                lifecycle_name = row.get("Lifecycle")
                lifecycle_id = None
                if pd.isna(lifecycle_name) or not str(lifecycle_name).strip():
                    lifecycle_id = get_first_people_lifecycle_id()
                else:
                    lifecycle_id = get_lookup_id_by_name("people_lifecycle_status", str(lifecycle_name).strip())
                    if not lifecycle_id:
                        errors.append(ValidationError(row_number, "Lifecycle", f"Lifecycle '{lifecycle_name}' not found", "NOT_FOUND"))
                        continue
                if lifecycle_id is not None:
                    row_data["lifecycle"] = lifecycle_id
                
                # Optional fields - people table
                function_name = row.get("Function")
                if not pd.isna(function_name) and str(function_name).strip():
                    row_data["Function"] = str(function_name).strip()
                
                function_desc = row.get("Function Description")
                if not pd.isna(function_desc) and str(function_desc).strip():
                    row_data["Function Description"] = str(function_desc).strip()
                
                description = row.get("Description")
                if not pd.isna(description) and str(description).strip():
                    row_data["Description"] = str(description).strip()
                
                password = row.get("Password")
                if not pd.isna(password) and str(password).strip():
                    row_data["Password"] = str(password).strip()
                
                # BUDG Status (optional)
                budg_status = row.get("BUDG Status")
                if not pd.isna(budg_status) and str(budg_status).strip():
                    status_id = get_lookup_id_by_name("status", str(budg_status).strip())
                    if status_id:
                        row_data["status_id"] = status_id
                    else:
                        errors.append(ValidationError(row_number, "BUDG Status", f"Status '{budg_status}' not found", "NOT_FOUND"))
                
                # Org Unit Reference or Name (at least one is mandatory)
                org_unit_ref = row.get("Org Unit Reference")
                org_unit_name = row.get("Org Unit Name")
                org_unit_ref_provided = not pd.isna(org_unit_ref) and str(org_unit_ref).strip()
                org_unit_name_provided = not pd.isna(org_unit_name) and str(org_unit_name).strip()
                
                # Check if at least one is provided
                if not org_unit_ref_provided and not org_unit_name_provided:
                    errors.append(ValidationError(row_number, "Org Unit", "Either Org Unit Reference or Org Unit Name must be provided", "MISSING_REQUIRED"))
                    continue
                
                org_unit_id_from_ref = None
                org_unit_id_from_name = None
                
                # Validate org unit reference if provided
                if org_unit_ref_provided:
                    ref_value = str(org_unit_ref).strip()
                    # Try to get ID by reference (reference is a string, not an integer ID)
                    org_unit_id_from_ref = get_org_unit_id_by_reference(ref_value)
                    if not org_unit_id_from_ref:
                        errors.append(ValidationError(row_number, "Org Unit Reference", f"Org Unit with reference '{ref_value}' not found", "NOT_FOUND"))
                        continue
                
                # Validate org unit name if provided
                if org_unit_name_provided:
                    name_value = str(org_unit_name).strip()
                    org_unit_id_from_name = get_org_unit_id_by_name(name_value)
                    if not org_unit_id_from_name:
                        errors.append(ValidationError(row_number, "Org Unit Name", f"Org Unit '{name_value}' not found", "NOT_FOUND"))
                        continue
                
                # If both are provided, verify they refer to the same org unit
                if org_unit_ref_provided and org_unit_name_provided:
                    if org_unit_id_from_ref != org_unit_id_from_name:
                        # Get names for better error message
                        ref_name = get_org_unit_name_by_id(org_unit_id_from_ref)
                        name_ref = None
                        if org_unit_id_from_name:
                            try:
                                with get_db_connection() as conn:
                                    with conn.cursor() as cur:
                                        cur.execute("SELECT Reference FROM org_unit WHERE ID = %s LIMIT 1", (int(org_unit_id_from_name),))
                                        row_result = cur.fetchone()
                                        name_ref = row_result[0] if row_result else None
                            except Exception:
                                pass
                        errors.append(ValidationError(row_number, "Org Unit", 
                            f"Org Unit Reference '{org_unit_ref}' refers to '{ref_name}' but Org Unit Name '{org_unit_name}' refers to a different org unit (reference: '{name_ref}')", 
                            "ORG_UNIT_MISMATCH"))
                        continue
                
                # Set the org unit ID (use whichever was provided, or both if they match)
                if org_unit_id_from_ref:
                    row_data["Org_Unit_ID"] = org_unit_id_from_ref
                elif org_unit_id_from_name:
                    row_data["Org_Unit_ID"] = org_unit_id_from_name
                
                # Optional fields - people_details table
                office_location = row.get("Office Location")
                if not pd.isna(office_location) and str(office_location).strip():
                    row_data["Office Location"] = str(office_location).strip()
                
                internal_mail_code = row.get("Internal Mail Code")
                if not pd.isna(internal_mail_code) and str(internal_mail_code).strip():
                    row_data["Internal Mail Code"] = str(internal_mail_code).strip()
                
                office_telephone = row.get("Office Telephone")
                if not pd.isna(office_telephone) and str(office_telephone).strip():
                    row_data["Office Telephone"] = str(office_telephone).strip()
                
                mobile_cell = row.get("Mobile/Cell")
                if not pd.isna(mobile_cell) and str(mobile_cell).strip():
                    row_data["Mobile Telephone"] = str(mobile_cell).strip()
                
                lan_id = row.get("LAN ID") or row.get("LAN Id")
                if not pd.isna(lan_id) and str(lan_id).strip():
                    row_data["LAN ID"] = str(lan_id).strip()
                
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
                    add_custom_fields_to_validated_data(row, row_data, "People", row_number, errors)
                
                validated_data.append(row_data)
                
        except Exception as e:
            logger.error(f"Error processing row {row_number}: {e}")
            errors.append(ValidationError(row_number, "General", str(e), "PROCESSING_ERROR"))
    
    return validated_data, errors
