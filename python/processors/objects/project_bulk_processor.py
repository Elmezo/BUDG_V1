"""
Project Bulk Upload Validation Module
Validates Excel files for Project bulk uploads and resolves lookups to IDs.
"""

from typing import List, Tuple, Dict, Any, Optional
import os
import sys
import logging
import pandas as pd
import pymysql
from datetime import datetime, timedelta

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
    # Fallback if import fails
    logger.warning("Could not import segment_validator, segment validation will be skipped")
    validate_segment_exists = None
    validate_user_segment_access = None
    get_segment_id_by_name = None
    get_segment_name_by_id = None
    get_object_segment_id = None
try:
    from parent_hierarchy_validator import would_create_parent_cycle_by_entity
except ImportError:
    would_create_parent_cycle_by_entity = None

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
    Return expected sheet name for Project bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Project"
    if upload_option == "Update Existing Items":
        return "Update Project"
    if upload_option == "Remove Existing Items":
        return "Delete Project"
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


def get_project_id_by_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM project WHERE LOWER(primaryname) = LOWER(%s) AND deletedatetime IS NULL LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_project_id_by_name error: %s", e)
        return None


def get_project_id_by_ref(ref_number: Optional[str]) -> Optional[int]:
    if not ref_number or not str(ref_number).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM project WHERE refnumber = %s AND deletedatetime IS NULL LIMIT 1", (str(ref_number).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_project_id_by_ref error: %s", e)
        return None


def get_first_rag_id() -> Optional[int]:
    """Get the first RAG ID from project_rag table"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM project_rag ORDER BY id ASC LIMIT 1")
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_first_rag_id error: %s", e)
        return None


def get_first_project_lifecycle_id() -> Optional[int]:
    """Get the first lifecycle ID from project_lifecycle table"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM project_lifecycle ORDER BY id ASC LIMIT 1")
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_first_project_lifecycle_id error: %s", e)
        return None


def get_first_project_type_id() -> Optional[int]:
    """Get the first type ID from project_type table"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM project_type ORDER BY id ASC LIMIT 1")
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_first_project_type_id error: %s", e)
        return None


def parse_date(date_str: Optional[Any]) -> Optional[str]:
    """
    Parse date string to yyyy-MM-dd format.
    Handles various date formats including Excel date serial numbers.
    """
    if date_str is None or (isinstance(date_str, str) and not str(date_str).strip()):
        return None
    
    # If it's already a datetime object
    if isinstance(date_str, datetime):
        return date_str.strftime("%Y-%m-%d")
    
    # If it's a pandas Timestamp
    if isinstance(date_str, pd.Timestamp):
        return date_str.strftime("%Y-%m-%d")
    
    # Try to parse as Excel serial number (numeric)
    try:
        if isinstance(date_str, (int, float)):
            # Excel date serial number (days since 1900-01-01)
            excel_epoch = datetime(1899, 12, 30)  # Excel epoch
            date = excel_epoch + timedelta(days=int(date_str))
            return date.strftime("%Y-%m-%d")
    except Exception:
        pass
    
    # Try to parse as string date
    date_str_clean = str(date_str).strip()
    if not date_str_clean:
        return None
    
    # Try common date formats (order matters - try most specific first)
    date_formats = [
        "%Y-%m-%d",      # 2000-10-12
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
            date_obj = datetime.strptime(date_str_clean, fmt)
            return date_obj.strftime("%Y-%m-%d")
        except (ValueError, TypeError):
            continue
    
    # Try using dateutil if available
    try:
        from dateutil import parser as date_parser
        date_obj = date_parser.parse(date_str_clean)
        return date_obj.strftime("%Y-%m-%d")
    except Exception:
        pass
    
    logger.warning("Could not parse date: %s", date_str)
    return None


def validate_column_headers(df: pd.DataFrame, upload_option: str) -> List[ValidationError]:
    errors: List[ValidationError] = []
    columns = [str(c).strip() for c in df.columns]
    def missing(col: str) -> bool:
        return col not in columns
    
    if upload_option == "Add New Items":
        required = ["Project Name", "Project Description", "Start Date", "End Date", "RAG", "Project Lifecycle", "Project Type", "BUDG Status"]
        for col in required:
            if missing(col):
                errors.append(ValidationError(0, col, f"Missing required column: {col}", "MISSING_COLUMN"))
    elif upload_option == "Update Existing Items":
        required = ["Project ID"]
        for col in required:
            if missing(col):
                errors.append(ValidationError(0, col, f"Missing required column: {col}", "MISSING_COLUMN"))
    elif upload_option == "Remove Existing Items":
        # At least one of Project ID, Reference, or Project Name is required for delete
        has_id = not missing("Project ID")
        has_ref = not missing("Reference")
        has_name = not missing("Project Name")
        if not (has_id or has_ref or has_name):
            errors.append(ValidationError(0, "Project ID", "At least one of Project ID, Reference, or Project Name is required for delete", "MISSING_COLUMN"))
    return errors


def _resolve_parent_id(row: Dict[str, Any]) -> Optional[int]:
    # Prefer Parent_ID, else Parent Ref., else Parent Project Name
    parent_id = row.get("Parent_ID")
    if parent_id:
        try:
            parent_id_int = int(parent_id)
            return parent_id_int if _exists_in_table("project", parent_id_int, "id") else None
        except Exception:
            pass
    by_ref = get_project_id_by_ref(row.get("Parent Ref."))
    if by_ref:
        return by_ref
    return get_project_id_by_name(row.get("Parent Project Name"))


def validate_row_data(df: pd.DataFrame, upload_option: str, segment_mode: Optional[str] = None, segment: Optional[str] = None, user_id: Optional[int] = None) -> Tuple[List[ValidationError], List[Dict[str, Any]]]:
    errors: List[ValidationError] = []
    data: List[Dict[str, Any]] = []
    # Normalize column names to exact strings used downstream
    df = df.rename(columns={str(c): str(c).strip() for c in df.columns})
    
    # Build sets of names and refs in file so parent "in same file" is allowed
    names_in_file = set()
    refs_in_file = set()
    if "Project Name" in df.columns:
        for v in df["Project Name"].dropna():
            s = str(v).strip()
            if s:
                names_in_file.add(s.lower())
    if "Reference" in df.columns:
        for v in df["Reference"].dropna():
            s = str(v).strip()
            if s:
                refs_in_file.add(s.lower())
    
    # Track names and refs within the template to detect duplicates, grouped by segment
    # Key: segment_id or "default" for Enterprise, Value: set of names/refs (lowercase)
    template_names_by_segment: Dict[str, set] = {}  # Track names within this template by segment
    template_refs_by_segment: Dict[str, set] = {}    # Track refs within this template by segment
    
    for idx, series in df.iterrows():
        row_num = idx + 2  # account for header row in Excel
        row = {k: (None if pd.isna(v) else v) for k, v in series.items()}
        try:
            if upload_option == "Add New Items":
                # Required text/date fields only (not lookup fields)
                required_fields = ["Project Name", "Project Description", "Start Date", "End Date", "BUDG Status"]
                for req in required_fields:
                    if not row.get(req) or str(row.get(req)).strip() == "":
                        errors.append(ValidationError(row_num, req, f"Required field '{req}' is empty", "REQUIRED_FIELD_EMPTY"))
                
                # Segment validation (Rules A, B, C) - only for INSERT operations
                # Initialize segment_name outside validation block so it's available for inclusion in resolved
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
                                                    errors.append(ValidationError(row_num, "Segment", f"User does not have access to selected segment '{selected_segment_name}'", "SEGMENT_ACCESS_DENIED"))
                                                # If accessible, use it (will be set in row_data below)
                                            else:
                                                errors.append(ValidationError(row_num, "Segment", f"Selected segment ID '{segment}' does not exist", "SEGMENT_NOT_FOUND"))
                                        else:
                                            # Cannot validate by ID, require segment in Excel
                                            errors.append(ValidationError(row_num, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                                    except ValueError:
                                        # segment is not a valid ID, require segment in Excel
                                        errors.append(ValidationError(row_num, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                                else:
                                    # No validation functions available, require segment in Excel
                                    errors.append(ValidationError(row_num, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                            else:
                                # No segment in Excel and no selectedSegment in UI
                                errors.append(ValidationError(row_num, "Segment", "Segment is required when 'Multiple' mode is selected", "SEGMENT_REQUIRED"))
                        else:
                            # Segment specified in Excel - validate it
                            # Rule C: Segment must exist and user must have access
                            if validate_segment_exists and user_id:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_num, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                else:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_num, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED"))
                    elif segment_mode == "ENTERPRISE":
                        # Segment column is optional, but if provided, validate it
                        if segment_name and segment_name != "" and segment_name.lower() != "enterprise":
                            # Allow empty or "Enterprise", but validate if something else is provided
                            if validate_segment_exists:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_num, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                elif user_id and validate_user_segment_access:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_num, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED"))
                    elif segment_mode == "SPECIFIC":
                        # SPECIFIC mode: If segment is provided in Excel, use it (overrides UI), otherwise use selectedSegment from UI
                        if segment_name and segment_name != "":
                            # Segment specified in Excel - use it (overrides UI selection)
                            # Rule C: Validate segment exists and user has access
                            if validate_segment_exists and user_id:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_num, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                else:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_num, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED"))
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
                                                    errors.append(ValidationError(row_num, "Segment", f"User does not have access to selected segment '{selected_segment_name}'", "SEGMENT_ACCESS_DENIED"))
                                                # If accessible, will use it (Java will use selectedSegment parameter)
                                            else:
                                                errors.append(ValidationError(row_num, "Segment", f"Selected segment ID '{segment}' does not exist", "SEGMENT_NOT_FOUND"))
                                        # If no get_segment_name_by_id function, Java will handle validation
                                    except ValueError:
                                        # segment is not a valid ID
                                        errors.append(ValidationError(row_num, "Segment", f"Invalid selected segment ID: '{segment}'", "SEGMENT_INVALID"))
                                # If no validation functions, Java will handle it
                            # If no segment in Excel and no selectedSegment in UI, Java will throw error
                
                # Prepare resolved fields
                resolved: Dict[str, Any] = {
                    "operation": "INSERT",
                    "row_number": row_num,
                }
                
                # Include Segment in resolved data if present
                # For MULTIPLE mode: use segment from Excel, or selectedSegment from UI if no Excel segment
                # For SPECIFIC mode: use segment from Excel (overrides UI), or selectedSegment from UI if no Excel segment
                # For ENTERPRISE mode: use "Enterprise" or segment from Excel if provided
                # For null mode: use segment from Excel if provided
                if segment_mode == "MULTIPLE":
                    if segment_name:
                        resolved["Segment"] = segment_name
                    elif segment and segment.strip():
                        # Use selectedSegment from UI if no Excel segment
                        if get_segment_name_by_id:
                            try:
                                selected_segment_name = get_segment_name_by_id(int(segment.strip()))
                                if selected_segment_name:
                                    resolved["Segment"] = selected_segment_name
                            except (ValueError, TypeError):
                                pass  # Java will handle validation
                elif segment_mode == "SPECIFIC":
                    if segment_name:
                        # Segment from Excel (overrides UI)
                        resolved["Segment"] = segment_name
                    # If no segment in Excel, Java will use selectedSegment from UI
                elif segment_mode == "ENTERPRISE":
                    # Always include "Enterprise" for ENTERPRISE mode (or segment from Excel if provided)
                    if segment_name and segment_name.lower() != "enterprise":
                        resolved["Segment"] = segment_name
                    else:
                        resolved["Segment"] = "Enterprise"
                elif segment_mode is None and segment_name:
                    # Include segment if provided even when mode is null
                    resolved["Segment"] = segment_name
                
                # Determine segment key for duplicate tracking
                # Use segment ID if available, otherwise use segment name, or "default" for Enterprise
                segment_key = "default"  # Default to Enterprise segment
                if segment_mode == "MULTIPLE" and segment_name:
                    # Try to get segment ID
                    if validate_segment_exists and user_id:
                        exists, seg_id = validate_segment_exists(segment_name)
                        if exists:
                            segment_key = str(seg_id)
                        else:
                            segment_key = segment_name.lower()
                    else:
                        segment_key = segment_name.lower()
                elif segment_mode == "SPECIFIC" and segment:
                    segment_key = str(segment)
                elif segment_mode == "ENTERPRISE":
                    segment_key = "1"  # Enterprise segment ID is 1
                
                # Validate Name for duplicates within template for same segment
                project_name = row.get("Project Name")
                if project_name and str(project_name).strip():
                    name_lower = str(project_name).strip().lower()
                    segment_names = template_names_by_segment.setdefault(segment_key, set())
                    if name_lower in segment_names:
                        errors.append(ValidationError(
                            row_num,
                            "Project Name",
                            f"Duplicate name '{project_name}' found within the template for the same segment. Each project name must be unique within the same segment in the upload file.",
                            "DUPLICATE_NAME_IN_TEMPLATE"
                        ))
                        # Don't continue here - collect all errors first
                    else:
                        segment_names.add(name_lower)
                
                # Validate Reference for duplicates within template for same segment (if provided)
                ref_value = row.get("Reference")
                if ref_value and str(ref_value).strip():
                    ref_lower = str(ref_value).strip().lower()
                    segment_refs = template_refs_by_segment.setdefault(segment_key, set())
                    if ref_lower in segment_refs:
                        errors.append(ValidationError(
                            row_num,
                            "Reference",
                            f"Duplicate reference '{ref_value}' found within the template for the same segment. Each project reference must be unique within the same segment in the upload file.",
                            "DUPLICATE_REF_IN_TEMPLATE"
                        ))
                        # Don't continue here - collect all errors first
                    else:
                        segment_refs.add(ref_lower)
                
                # Parse dates
                start_date = parse_date(row.get("Start Date"))
                if not start_date:
                    errors.append(ValidationError(row_num, "Start Date", "Start Date is required and must be a valid date", "INVALID_DATE"))
                else:
                    resolved["Start Date"] = start_date
                
                end_date = parse_date(row.get("End Date"))
                if not end_date:
                    errors.append(ValidationError(row_num, "End Date", "End Date is required and must be a valid date", "INVALID_DATE"))
                else:
                    resolved["End Date"] = end_date
                
                # Validate Parent Project - check if both ref and name are provided and match
                parent_ref = row.get("Parent Ref.")
                parent_name = row.get("Parent Project Name")
                has_ref = parent_ref and str(parent_ref).strip()
                has_name = parent_name and str(parent_name).strip()
                parent_in_file = (has_ref and str(parent_ref).strip().lower() in refs_in_file) or (has_name and str(parent_name).strip().lower() in names_in_file)
                
                if parent_in_file:
                    pass  # Java will resolve from batch; do not set Parent_ID
                else:
                    if has_ref and has_name:
                        parent_id_by_ref = get_project_id_by_ref(parent_ref)
                        parent_id_by_name = get_project_id_by_name(parent_name)
                        if parent_id_by_ref and parent_id_by_name and parent_id_by_ref != parent_id_by_name:
                            errors.append(ValidationError(
                                row=row_num,
                                field="Parent Ref.",
                                message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different projects",
                                error_code="PARENT_NAME_REF_MISMATCH"
                            ))
                            continue
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                    elif has_ref or has_name:
                        if has_ref:
                            errors.append(ValidationError(row_num, "Parent Ref.", f"Parent project not found with reference: {parent_ref}", "PARENT_NOT_FOUND"))
                        elif has_name:
                            errors.append(ValidationError(row_num, "Parent Project Name", f"Parent project not found with name: {parent_name}", "PARENT_NOT_FOUND"))
                
                # Lookups
                # BUDG Viewing (optional)
                vid = get_viewing_id_by_name(row.get("BUDG Viewing"))
                if vid:
                    resolved["BUDG Viewing_ID"] = vid
                
                # RAG (required) - auto-fill with first if empty
                rag_id = get_lookup_id_by_name("project_rag", row.get("RAG"))
                if rag_id is None:
                    rag_id = get_first_rag_id()
                if rag_id:
                    resolved["RAG_ID"] = rag_id
                
                # Classification (optional)
                class_id = get_lookup_id_by_name("project_classification", row.get("Classification"))
                if class_id:
                    resolved["Classification_ID"] = class_id
                
                # BUDG Status (required)
                status_name = row.get("BUDG Status")
                if not status_name or not str(status_name).strip():
                    errors.append(ValidationError(row_num, "BUDG Status", "BUDG Status is required", "REQUIRED_FIELD_EMPTY"))
                else:
                    status_id = get_lookup_id_by_name("status", str(status_name).strip())
                    if status_id:
                        resolved["BUDG Status_ID"] = status_id
                    else:
                        errors.append(ValidationError(row_num, "BUDG Status", f"Status '{status_name}' not found", "NOT_FOUND"))
                
                # Project Lifecycle (required) - auto-fill with first if empty
                lifecycle_id = get_lookup_id_by_name("project_lifecycle", row.get("Project Lifecycle"))
                if lifecycle_id is None:
                    lifecycle_id = get_first_project_lifecycle_id()
                if lifecycle_id:
                    resolved["Project Lifecycle_ID"] = lifecycle_id
                
                # Project Type (required) - auto-fill with first if empty - note: table is project_type not project_comment_type
                project_type_id = get_lookup_id_by_name("project_type", row.get("Project Type"))
                if project_type_id is None:
                    project_type_id = get_first_project_type_id()
                if project_type_id:
                    resolved["Project Type_ID"] = project_type_id
                
                # Governance Role (optional)
                gov_role_id = get_lookup_id_by_name("object_role", row.get("Governance Role"))
                if gov_role_id:
                    resolved["Governance Role_ID"] = gov_role_id
                
                # Include original fields as-is
                for k in [
                    "Reference", "Project Name", "Project Description"
                ]:
                    if k in row:
                        resolved[k] = row.get(k)
                
                # Only add to data if no critical errors
                if not any(e.error_code in ["REQUIRED_FIELD_EMPTY", "INVALID_DATE", "LOOKUP_NOT_FOUND", "NOT_FOUND", "PARENT_NOT_FOUND"] for e in errors if e.row == row_num):
                    # Validate and add custom fields
                    if add_custom_fields_to_validated_data:
                        add_custom_fields_to_validated_data(series, resolved, "Project", row_num, errors)
                    
                    data.append(resolved)

            elif upload_option == "Update Existing Items":
                def _has_val(v):
                    if v is None or (hasattr(v, '__len__') and not v): return False
                    if pd.isna(v): return False
                    s = str(v).strip()
                    return bool(s) and s.lower() not in ("nan", "")
                id_raw = row.get("Project ID")
                ref_raw = row.get("Reference")
                name_raw = row.get("Project Name")
                id_by_id = None
                if _has_val(id_raw):
                    try:
                        vid = int(float(str(id_raw).strip()))
                        if _exists_in_table("project", vid, "id"):
                            id_by_id = vid
                    except (ValueError, TypeError):
                        pass
                id_by_ref = get_project_id_by_ref(str(ref_raw).strip()) if _has_val(ref_raw) else None
                id_by_name = get_project_id_by_name(str(name_raw).strip()) if _has_val(name_raw) else None
                filled = sum(1 for v in (id_raw, ref_raw, name_raw) if _has_val(v))
                if filled == 0:
                    errors.append(ValidationError(row_num, "Project ID", "At least one of Project ID, Reference, or Project Name is required for update", "REQUIRED_FIELD_EMPTY"))
                    continue
                project_id_int = id_by_id or id_by_ref or id_by_name
                if project_id_int is None:
                    errors.append(ValidationError(row_num, "Project ID", "No project found for the provided identity (Project ID, Reference, or Project Name)", "PROJECT_NOT_FOUND"))
                    continue
                if filled >= 2:
                    ids = {x for x in (id_by_id, id_by_ref, id_by_name) if x is not None}
                    if len(ids) > 1:
                        errors.append(ValidationError(row_num, "Project ID", "Project ID, Reference and Project Name refer to different projects", "IDENTITY_MISMATCH"))
                        continue
                
                resolved: Dict[str, Any] = {
                    "operation": "UPDATE",
                    "row_number": row_num,
                    "Project ID": project_id_int,
                }
                
                # Parse dates if provided
                start_date = parse_date(row.get("Start Date"))
                if start_date:
                    resolved["Start Date"] = start_date
                
                end_date = parse_date(row.get("End Date"))
                if end_date:
                    resolved["End Date"] = end_date
                
                # Validate Parent Project - check if both ref and name are provided and match
                parent_ref = row.get("Parent Ref.")
                parent_name = row.get("Parent Project Name")
                has_ref = parent_ref and str(parent_ref).strip()
                has_name = parent_name and str(parent_name).strip()
                parent_in_file = (has_ref and str(parent_ref).strip().lower() in refs_in_file) or (has_name and str(parent_name).strip().lower() in names_in_file)
                
                if parent_in_file:
                    pass  # Java will resolve from batch; do not set Parent_ID
                else:
                    if has_ref and has_name:
                        parent_id_by_ref = get_project_id_by_ref(parent_ref)
                        parent_id_by_name = get_project_id_by_name(parent_name)
                        if parent_id_by_ref and parent_id_by_name and parent_id_by_ref != parent_id_by_name:
                            errors.append(ValidationError(
                                row=row_num,
                                field="Parent Ref.",
                                message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different projects",
                                error_code="PARENT_NAME_REF_MISMATCH"
                            ))
                            continue
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                    elif has_ref or has_name:
                        if has_ref:
                            errors.append(ValidationError(row_num, "Parent Ref.", f"Parent project not found with reference: {parent_ref}", "PARENT_NOT_FOUND"))
                        elif has_name:
                            errors.append(ValidationError(row_num, "Parent Project Name", f"Parent project not found with name: {parent_name}", "PARENT_NOT_FOUND"))
                
                obj_id = resolved.get("Project ID")
                parent_id_val = resolved.get("Parent_ID")
                if obj_id and parent_id_val and would_create_parent_cycle_by_entity:
                    if would_create_parent_cycle_by_entity(get_db_connection, "Project", obj_id, parent_id_val):
                        errors.append(ValidationError(
                            row_num,
                            "Parent Ref.",
                            "Setting this parent would create a circular hierarchy: the chosen parent is already a descendant of this object.",
                            "PARENT_CYCLE",
                        ))
                        continue
                if obj_id and parent_id_val and get_object_segment_id:
                    obj_seg = get_object_segment_id(obj_id, "Project")
                    parent_seg = get_object_segment_id(parent_id_val, "Project")
                    if obj_seg is not None and parent_seg is not None and obj_seg != -1 and parent_seg != -1:
                        if obj_seg != 1 and parent_seg != 1 and obj_seg != parent_seg:
                            errors.append(ValidationError(
                                row_num,
                                "Parent Ref.",
                                "Cannot set parent to an object in a different private segment.",
                                "PARENT_DIFFERENT_PRIVATE_SEGMENT",
                            ))
                            continue
                # Lookup mappings
                mappings = [
                    ("BUDG Viewing", "BUDG Viewing_ID", get_viewing_id_by_name),
                    ("RAG", "RAG_ID", lambda v: get_lookup_id_by_name("project_rag", v)),
                    ("Classification", "Classification_ID", lambda v: get_lookup_id_by_name("project_classification", v)),
                    ("BUDG Status", "BUDG Status_ID", lambda v: get_lookup_id_by_name("status", v)),
                    ("Project Lifecycle", "Project Lifecycle_ID", lambda v: get_lookup_id_by_name("project_lifecycle", v)),
                    ("Project Type", "Project Type_ID", lambda v: get_lookup_id_by_name("project_comment_type", v)),
                ]
                for src, out, fn in mappings:
                    vid = fn(row.get(src))
                    if vid:
                        resolved[out] = vid
                
                # Copy pass-through text fields if present
                for k in ["Reference", "Project Name", "Project Description"]:
                    if k in row and row.get(k) not in (None, ""):
                        resolved[k] = row.get(k)
                
                data.append(resolved)

            elif upload_option == "Remove Existing Items":
                def _has_val(v):
                    if v is None or (hasattr(v, "__len__") and not v):
                        return False
                    if pd.isna(v):
                        return False
                    s = str(v).strip()
                    return bool(s) and s.lower() not in ("nan", "")
                id_raw = row.get("Project ID")
                ref_raw = row.get("Reference")
                name_raw = row.get("Project Name")
                project_id_int = None
                if _has_val(id_raw):
                    try:
                        vid = int(float(str(id_raw).strip()))
                        if _exists_in_table("project", vid, "id"):
                            project_id_int = vid
                        else:
                            errors.append(ValidationError(row_num, "Project ID", f"Project with ID {vid} does not exist", "PROJECT_NOT_FOUND"))
                            continue
                    except (ValueError, TypeError):
                        errors.append(ValidationError(row_num, "Project ID", "Project ID must be a valid integer", "INVALID_VALUE"))
                        continue
                if project_id_int is None and _has_val(ref_raw):
                    project_id_int = get_project_id_by_ref(str(ref_raw).strip())
                    if project_id_int is None:
                        errors.append(ValidationError(row_num, "Reference", f"Project not found with reference: {ref_raw}", "PROJECT_NOT_FOUND"))
                        continue
                if project_id_int is None and _has_val(name_raw):
                    project_id_int = get_project_id_by_name(str(name_raw).strip())
                    if project_id_int is None:
                        errors.append(ValidationError(row_num, "Project Name", f"Project not found with name: {name_raw}", "PROJECT_NOT_FOUND"))
                        continue
                if project_id_int is None:
                    errors.append(ValidationError(row_num, "Project ID", "Project identity is required for delete (any of: ID, Reference, Project Name)", "REQUIRED_FIELD_EMPTY"))
                    continue
                # If multiple identifiers provided, ensure they refer to the same project
                ids = set()
                if _has_val(id_raw):
                    try:
                        vid = int(float(str(id_raw).strip()))
                        if _exists_in_table("project", vid, "id"):
                            ids.add(vid)
                    except (ValueError, TypeError):
                        pass
                if _has_val(ref_raw):
                    rid = get_project_id_by_ref(str(ref_raw).strip())
                    if rid is not None:
                        ids.add(rid)
                if _has_val(name_raw):
                    nid = get_project_id_by_name(str(name_raw).strip())
                    if nid is not None:
                        ids.add(nid)
                if len(ids) > 1:
                    errors.append(ValidationError(row_num, "Project ID", "Project ID, Reference and Project Name refer to different projects", "IDENTITY_MISMATCH"))
                    continue
                data.append({
                    "operation": "DELETE",
                    "row_number": row_num,
                    "Project ID": project_id_int,
                })
            else:
                errors.append(ValidationError(row_num, "operation", f"Unsupported operation: {upload_option}", "INVALID_OPERATION"))
        except Exception as e:
            logger.error("Error validating row %s: %s", row_num, e)
            errors.append(ValidationError(row_num, "row", f"Unexpected error: {e}", "UNEXPECTED_ERROR"))
    return errors, data


def apply_column_mappings(df: pd.DataFrame, mappings: Dict[str, str]) -> pd.DataFrame:
    """
    Reverse mapping received from Java: mappings = {excelColName: expectedFieldName}
    Rename DataFrame columns accordingly where possible.
    """
    # Build a renaming dict: if a column name exists in mappings keys, rename it to mappings[col]
    rename_dict = {}
    for col in df.columns:
        if col in mappings:
            rename_dict[col] = mappings[col]
    if rename_dict:
        df = df.rename(columns=rename_dict)
    return df

