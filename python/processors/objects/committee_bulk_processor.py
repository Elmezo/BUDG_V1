"""
Committee Bulk Upload Validation Module
Validates Excel files for Committee bulk uploads and resolves lookups to IDs.
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
    from bulk_segment_name_utils import (
        duplicate_name_in_facet_segment,
        resolve_effective_segment_id_for_duplicate_check,
    )
except ImportError:
    duplicate_name_in_facet_segment = None
    resolve_effective_segment_id_for_duplicate_check = None

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
    Return expected sheet name for Committee bulk upload templates.
    Matches frontend expected sheet names (StepMapColumns) for consistency.
    """
    if upload_option == "Add New Items":
        return "Create Committee"
    if upload_option == "Update Existing Items":
        return "Update Committee"
    if upload_option == "Remove Existing Items":
        return "Delete Committee"
    return None


class ValidationError:
    def __init__(self, row: int, field: str, message: str, error_code: str, segment_name: Optional[str] = None, segment_id: Optional[int] = None):
        self.row = row
        self.field = field
        self.message = message
        self.error_code = error_code
        self.segment_name = segment_name
        self.segment_id = segment_id

    def dict(self):
        result = {"row": self.row, "field": self.field, "message": self.message, "error_code": self.error_code}
        if self.segment_name is not None:
            result["segmentName"] = self.segment_name
        if self.segment_id is not None:
            result["segmentId"] = self.segment_id
        return result


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


def get_committee_id_by_name(name: Optional[str]) -> Optional[int]:
    if not name or not str(name).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM committee WHERE LOWER(PrimaryName) = LOWER(%s) AND DeleteDatetime IS NULL LIMIT 1", (str(name).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_committee_id_by_name error: %s", e)
        return None


def get_committee_id_by_ref(ref_number: Optional[str]) -> Optional[int]:
    if not ref_number or not str(ref_number).strip():
        return None
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM committee WHERE RefNumber = %s AND DeleteDatetime IS NULL LIMIT 1", (str(ref_number).strip(),))
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_committee_id_by_ref error: %s", e)
        return None


def get_first_classification_id() -> Optional[int]:
    """Get the first classification ID from committee_classification table"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM committee_classification ORDER BY ID ASC LIMIT 1")
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_first_classification_id error: %s", e)
        return None


def get_first_lifecycle_id() -> Optional[int]:
    """Get the first lifecycle ID from committee_lifecycle table"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM committee_lifecycle ORDER BY ID ASC LIMIT 1")
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_first_lifecycle_id error: %s", e)
        return None


def get_first_committee_type_id() -> Optional[int]:
    """Get the first committee type ID from committee_type table"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT ID FROM committee_type ORDER BY ID ASC LIMIT 1")
                row = cur.fetchone()
                return row[0] if row else None
    except Exception as e:
        logger.warning("get_first_committee_type_id error: %s", e)
        return None


def check_duplicate_committee_name(
    committee_name: str,
    exclude_id: Optional[int] = None,
    segment_id: Optional[int] = None,
) -> bool:
    """True if the same committee name exists in the target segment."""
    if not committee_name or not str(committee_name).strip():
        return False
    if duplicate_name_in_facet_segment is None:
        try:
            with get_db_connection() as conn:
                with conn.cursor() as cur:
                    if exclude_id:
                        cur.execute(
                            "SELECT COUNT(*) FROM committee WHERE LOWER(PrimaryName) = LOWER(%s) AND (DeleteDatetime IS NULL OR DeleteDatetime = '') AND ID != %s",
                            (str(committee_name).strip(), exclude_id),
                        )
                    else:
                        cur.execute(
                            "SELECT COUNT(*) FROM committee WHERE LOWER(PrimaryName) = LOWER(%s) AND (DeleteDatetime IS NULL OR DeleteDatetime = '')",
                            (str(committee_name).strip(),),
                        )
                    return cur.fetchone()[0] > 0
        except Exception as e:
            logger.warning("check_duplicate_committee_name error: %s", e)
            return False
    sid = segment_id if segment_id is not None else 1
    return duplicate_name_in_facet_segment("Committee", sid, str(committee_name).strip(), exclude_id)


def validate_column_headers(df: pd.DataFrame, upload_option: str) -> List[ValidationError]:
    errors: List[ValidationError] = []
    columns = [str(c).strip() for c in df.columns]
    
    def missing(col: str) -> bool:
        return col not in columns
    
    if upload_option == "Add New Items":
        required = ["Committee Name", "Description", "Classification", "Lifecycle", "Committee Type"]
        for col in required:
            if missing(col):
                errors.append(ValidationError(0, col, f"Missing required column: {col}", "MISSING_COLUMN"))
    elif upload_option == "Update Existing Items":
        identity_cols = ["Committee ID", "Reference", "Committee Name"]
        if all(missing(c) for c in identity_cols):
            errors.append(ValidationError(0, "identity", "For update, at least one of Committee ID, Reference, Committee Name must be present as a column.", "MISSING_COLUMN"))
    elif upload_option == "Remove Existing Items":
        if missing("Committee ID"):
            errors.append(ValidationError(0, "Committee ID", "Missing required column: Committee ID", "MISSING_COLUMN"))
    
    return errors


def _resolve_parent_id(row: Dict[str, Any]) -> Optional[int]:
    # Prefer Parent_ID, else Parent Ref., else Parent Committee Name
    parent_id = row.get("Parent_ID")
    if parent_id:
        try:
            parent_id_int = int(parent_id)
            return parent_id_int if _exists_in_table("committee", parent_id_int) else None
        except Exception:
            pass
    
    by_ref = get_committee_id_by_ref(row.get("Parent Ref."))
    if by_ref:
        return by_ref
    
    return get_committee_id_by_name(row.get("Parent Committee Name"))


def validate_row_data(df: pd.DataFrame, upload_option: str, segment_mode: Optional[str] = None, segment: Optional[str] = None, user_id: Optional[int] = None) -> Tuple[List[ValidationError], List[Dict[str, Any]]]:
    errors: List[ValidationError] = []
    data: List[Dict[str, Any]] = []
    
    # Normalize column names to exact strings used downstream
    df = df.rename(columns={str(c): str(c).strip() for c in df.columns})
    
    # Build sets of names and refs in file so parent "in same file" is allowed
    names_in_file = set()
    refs_in_file = set()
    if "Committee Name" in df.columns:
        for v in df["Committee Name"].dropna():
            s = str(v).strip()
            if s:
                names_in_file.add(s.lower())
    if "Reference" in df.columns:
        for v in df["Reference"].dropna():
            s = str(v).strip()
            if s:
                refs_in_file.add(s.lower())
    
    # Track template names for duplicate detection within template
    template_names = set()
    
    for idx, series in df.iterrows():
        row_num = idx + 2  # account for header row in Excel
        row = {k: (None if pd.isna(v) else v) for k, v in series.items()}
        
        try:
            if upload_option == "Add New Items":
                # Required text fields only (not lookup fields)
                has_mandatory_errors = False
                for req in ["Committee Name", "Description"]:
                    if not row.get(req) or str(row.get(req)).strip() == "":
                        errors.append(ValidationError(row_num, req, f"Required field '{req}' is empty", "REQUIRED_FIELD_EMPTY"))
                        has_mandatory_errors = True
                
                # If mandatory fields are missing, skip duplicate checks and continue to next row
                if has_mandatory_errors:
                    continue
                
                # Validate Committee Name for duplicates
                committee_name = str(row.get("Committee Name", "")).strip() if row.get("Committee Name") else ""
                if committee_name:
                    name_lower = committee_name.lower()
                    # Check for duplicate name within template
                    if name_lower in template_names:
                        errors.append(ValidationError(
                            row_num,
                            "Committee Name",
                            f"Duplicate name '{committee_name}' found within the template. Each committee name must be unique in the upload file.",
                            "DUPLICATE_NAME_IN_TEMPLATE"
                        ))
                        continue
                    
                    eff_seg = (
                        resolve_effective_segment_id_for_duplicate_check(row, segment_mode, segment)
                        if resolve_effective_segment_id_for_duplicate_check
                        else 1
                    )
                    if check_duplicate_committee_name(committee_name, segment_id=eff_seg):
                        errors.append(ValidationError(
                            row_num,
                            "Committee Name",
                            f"Committee with name '{committee_name}' already exists in the database for this segment",
                            "DUPLICATE_NAME"
                        ))
                        continue
                    
                    # Name is unique - add to template tracker
                    template_names.add(name_lower)
                
                # Segment validation (Rules A, B, C) - only for INSERT operations
                validated_segment_id = None
                validated_segment_name = None
                if segment_mode and segment_mode in ["MULTIPLE", "ENTERPRISE", "SPECIFIC"]:
                    segment_value = row.get("Segment")
                    segment_name = str(segment_value).strip() if segment_value and not pd.isna(segment_value) else None
                    
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
                                                    errors.append(ValidationError(row_num, "Segment", f"User does not have access to selected segment '{selected_segment_name}'", "SEGMENT_ACCESS_DENIED", segment_name=selected_segment_name, segment_id=segment_id_int))
                                                else:
                                                    validated_segment_id = segment_id_int
                                                    validated_segment_name = selected_segment_name
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
                                        errors.append(ValidationError(row_num, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED", segment_name=segment_name, segment_id=segment_id))
                                    else:
                                        # Validation successful - save segment info for resolved data
                                        validated_segment_id = segment_id
                                        validated_segment_name = segment_name
                    elif segment_mode == "ENTERPRISE":
                        # Enterprise mode: use Enterprise segment (ID = 1) unless specific segment provided
                        if segment_name and segment_name != "" and segment_name.lower() != "enterprise":
                            # Allow empty or "Enterprise", but validate if something else is provided
                            if validate_segment_exists:
                                exists, segment_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_num, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                elif user_id and validate_user_segment_access:
                                    has_access, access_segment_id = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_num, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED", segment_name=segment_name, segment_id=segment_id))
                                    else:
                                        validated_segment_id = segment_id
                                        validated_segment_name = segment_name
                        else:
                            # Use Enterprise segment
                            validated_segment_id = 1
                            validated_segment_name = "Enterprise"
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
                                        errors.append(ValidationError(row_num, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED", segment_name=segment_name, segment_id=segment_id))
                                    else:
                                        validated_segment_id = segment_id
                                        validated_segment_name = segment_name
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
                                                    errors.append(ValidationError(row_num, "Segment", f"User does not have access to selected segment '{selected_segment_name}'", "SEGMENT_ACCESS_DENIED", segment_name=selected_segment_name, segment_id=segment_id_int))
                                                else:
                                                    validated_segment_id = segment_id_int
                                                    validated_segment_name = selected_segment_name
                                            else:
                                                errors.append(ValidationError(row_num, "Segment", f"Selected segment ID '{segment}' does not exist", "SEGMENT_NOT_FOUND"))
                                        else:
                                            # If no get_segment_name_by_id function, use segment ID directly
                                            validated_segment_id = segment_id_int
                                            validated_segment_name = "Enterprise"  # Fallback
                                    except ValueError:
                                        # segment is not a valid ID
                                        errors.append(ValidationError(row_num, "Segment", f"Invalid selected segment ID: '{segment}'", "SEGMENT_INVALID"))
                                else:
                                    # If no validation functions, use segment ID directly
                                    try:
                                        validated_segment_id = int(segment.strip())
                                        if get_segment_name_by_id:
                                            validated_segment_name = get_segment_name_by_id(validated_segment_id) or "Enterprise"
                                        else:
                                            validated_segment_name = "Enterprise"
                                    except ValueError:
                                        pass  # Java will handle validation
                            # If no segment in Excel and no selectedSegment in UI, Java will throw error
                
                # Prepare resolved fields
                resolved: Dict[str, Any] = {
                    "operation": "INSERT",
                    "row_number": row_num,
                }
                
                # Include Segment info in resolved data if validated
                if validated_segment_id is not None:
                    resolved["Segment"] = validated_segment_name
                    resolved["segmentName"] = validated_segment_name
                    resolved["segmentId"] = validated_segment_id
                
                # Validate Parent Committee - check if both ref and name are provided and match
                parent_ref = row.get("Parent Ref.")
                parent_name = row.get("Parent Committee Name")
                has_ref = parent_ref and str(parent_ref).strip()
                has_name = parent_name and str(parent_name).strip()
                parent_in_file = (has_ref and str(parent_ref).strip().lower() in refs_in_file) or (has_name and str(parent_name).strip().lower() in names_in_file)
                
                if parent_in_file:
                    pass  # Java will resolve from batch; do not set Parent_ID
                else:
                    if has_ref and has_name:
                        parent_id_by_ref = get_committee_id_by_ref(parent_ref)
                        parent_id_by_name = get_committee_id_by_name(parent_name)
                        if parent_id_by_ref and parent_id_by_name and parent_id_by_ref != parent_id_by_name:
                            errors.append(ValidationError(
                                row=row_num,
                                field="Parent Ref.",
                                message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different committees",
                                error_code="PARENT_NAME_REF_MISMATCH"
                            ))
                            continue
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                
                # Lookups
                # BUDG Viewing (Name column in viewing table)
                vid = get_viewing_id_by_name(row.get("BUDG Viewing"))
                if vid:
                    resolved["BUDG Viewing_ID"] = vid
                
                # BUDG Status
                sid = get_lookup_id_by_name("status", row.get("BUDG Status"))
                if sid:
                    resolved["BUDG Status_ID"] = sid
                
                # Mandatory lookups must be explicitly provided in strict mode.
                classification_value = str(row.get("Classification", "")).strip() if row.get("Classification") is not None else ""
                lifecycle_value = str(row.get("Lifecycle", "")).strip() if row.get("Lifecycle") is not None else ""
                committee_type_value = str(row.get("Committee Type", "")).strip() if row.get("Committee Type") is not None else ""

                mandatory_lookup_missing = False
                if not classification_value:
                    errors.append(ValidationError(row_num, "Classification", "Required field 'Classification' is empty", "REQUIRED_FIELD_EMPTY"))
                    mandatory_lookup_missing = True
                if not lifecycle_value:
                    errors.append(ValidationError(row_num, "Lifecycle", "Required field 'Lifecycle' is empty", "REQUIRED_FIELD_EMPTY"))
                    mandatory_lookup_missing = True
                if not committee_type_value:
                    errors.append(ValidationError(row_num, "Committee Type", "Required field 'Committee Type' is empty", "REQUIRED_FIELD_EMPTY"))
                    mandatory_lookup_missing = True
                if mandatory_lookup_missing:
                    continue

                classification_id = get_lookup_id_by_name("committee_classification", classification_value)
                if not classification_id:
                    errors.append(ValidationError(row_num, "Classification", f"Classification '{classification_value}' not found", "NOT_FOUND"))
                    continue
                resolved["Classification_ID"] = classification_id

                lifecycle_id = get_lookup_id_by_name("committee_lifecycle", lifecycle_value)
                if not lifecycle_id:
                    errors.append(ValidationError(row_num, "Lifecycle", f"Lifecycle '{lifecycle_value}' not found", "NOT_FOUND"))
                    continue
                resolved["Lifecycle_ID"] = lifecycle_id

                committee_type_id = get_lookup_id_by_name("committee_type", committee_type_value)
                if not committee_type_id:
                    errors.append(ValidationError(row_num, "Committee Type", f"Committee Type '{committee_type_value}' not found", "NOT_FOUND"))
                    continue
                resolved["Committee Type_ID"] = committee_type_id
                
                # Governance Role (optional)
                gov_role_id = get_lookup_id_by_name("object_role", row.get("Governance Role"))
                if gov_role_id:
                    resolved["Governance Role_ID"] = gov_role_id
                # Also pass through the name if provided (for Java to resolve)
                if row.get("Governance Role"):
                    resolved["Governance Role"] = row.get("Governance Role")
                
                # User Email (optional) - pass through for stakeholder creation
                user_email = row.get("User Email")
                if user_email and str(user_email).strip():
                    resolved["User Email"] = str(user_email).strip()
                
                # Include original fields as-is (and Parent Ref./Name for batch resolution)
                for k in ["Committee Name", "Reference", "Description", "Parent Ref.", "Parent Committee Name"]:
                    if k in row:
                        resolved[k] = row.get(k)
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(series, resolved, "Committee", row_num, errors)
                
                data.append(resolved)
            
            elif upload_option == "Update Existing Items":
                def _has_val(v):
                    if v is None: return False
                    s = str(v).strip()
                    return bool(s) and s.lower() not in ("nan", "")
                id_by_id = None
                id_by_ref = None
                id_by_name = None
                cid_raw = row.get("Committee ID")
                if _has_val(cid_raw) and str(cid_raw).strip().replace("-", "").isdigit():
                    try:
                        with get_db_connection() as conn:
                            with conn.cursor() as cur:
                                cur.execute("SELECT ID FROM committee WHERE ID = %s AND (DeleteDatetime IS NULL OR DeleteDatetime = '') LIMIT 1", (int(float(str(cid_raw).strip())),))
                                r = cur.fetchone()
                                id_by_id = r[0] if r else None
                    except Exception:
                        id_by_id = None
                if _has_val(row.get("Reference")):
                    id_by_ref = get_committee_id_by_ref(str(row.get("Reference")).strip())
                if _has_val(row.get("Committee Name")):
                    id_by_name = get_committee_id_by_name(str(row.get("Committee Name")).strip())
                filled = sum(1 for v in [cid_raw, row.get("Reference"), row.get("Committee Name")] if _has_val(v))
                if filled == 0:
                    errors.append(ValidationError(row_num, "Committee ID", "At least one of Committee ID, Reference, or Committee Name is required for update", "REQUIRED_FIELD_EMPTY"))
                    continue
                resolved_id = id_by_id or id_by_ref or id_by_name
                if resolved_id is None:
                    errors.append(ValidationError(row_num, "Committee ID", "No committee found for the provided identity (Committee ID, Reference, or Committee Name)", "NOT_FOUND"))
                    continue
                if filled >= 2:
                    ids = {x for x in (id_by_id, id_by_ref, id_by_name) if x is not None}
                    if len(ids) > 1:
                        errors.append(ValidationError(row_num, "Committee ID", "Committee ID, Reference and Committee Name refer to different committees", "IDENTITY_MISMATCH"))
                        continue
                
                resolved: Dict[str, Any] = {
                    "operation": "UPDATE",
                    "row_number": row_num,
                    "Committee ID": resolved_id,
                }
                
                # Segment for UPDATE: validate and include in resolved so Java can apply segment change
                validated_segment_id = None
                validated_segment_name = None
                if segment_mode and segment_mode in ["MULTIPLE", "ENTERPRISE", "SPECIFIC"]:
                    segment_value = row.get("Segment")
                    segment_name = str(segment_value).strip() if segment_value and not pd.isna(segment_value) else None
                    if segment_mode == "MULTIPLE":
                        if segment_name and segment_name != "":
                            if validate_segment_exists and user_id:
                                exists, seg_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_num, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                else:
                                    has_access, _ = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_num, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED", segment_name=segment_name, segment_id=seg_id))
                                    else:
                                        validated_segment_id = seg_id
                                        validated_segment_name = segment_name
                        elif segment and str(segment).strip() and validate_user_segment_access and user_id and get_segment_name_by_id:
                            try:
                                seg_id_int = int(str(segment).strip())
                                selected_segment_name = get_segment_name_by_id(seg_id_int)
                                if selected_segment_name:
                                    has_access, _ = validate_user_segment_access(user_id, selected_segment_name)
                                    if has_access:
                                        validated_segment_id = seg_id_int
                                        validated_segment_name = selected_segment_name
                            except ValueError:
                                pass
                    elif segment_mode == "ENTERPRISE":
                        if segment_name and segment_name != "" and segment_name.lower() != "enterprise":
                            if validate_segment_exists and user_id:
                                exists, seg_id = validate_segment_exists(segment_name)
                                if exists and validate_user_segment_access:
                                    has_access, _ = validate_user_segment_access(user_id, segment_name)
                                    if has_access:
                                        validated_segment_id = seg_id
                                        validated_segment_name = segment_name
                        else:
                            validated_segment_id = 1
                            validated_segment_name = "Enterprise"
                    elif segment_mode == "SPECIFIC":
                        if segment_name and segment_name != "":
                            if validate_segment_exists and user_id:
                                exists, seg_id = validate_segment_exists(segment_name)
                                if not exists:
                                    errors.append(ValidationError(row_num, "Segment", f"Segment '{segment_name}' does not exist", "SEGMENT_NOT_FOUND"))
                                else:
                                    has_access, _ = validate_user_segment_access(user_id, segment_name)
                                    if not has_access:
                                        errors.append(ValidationError(row_num, "Segment", f"User does not have access to segment '{segment_name}'", "SEGMENT_ACCESS_DENIED", segment_name=segment_name, segment_id=seg_id))
                                    else:
                                        validated_segment_id = seg_id
                                        validated_segment_name = segment_name
                        elif segment and str(segment).strip() and validate_user_segment_access and user_id and get_segment_name_by_id:
                            try:
                                seg_id_int = int(str(segment).strip())
                                selected_segment_name = get_segment_name_by_id(seg_id_int)
                                if selected_segment_name:
                                    has_access, _ = validate_user_segment_access(user_id, selected_segment_name)
                                    if has_access:
                                        validated_segment_id = seg_id_int
                                        validated_segment_name = selected_segment_name
                            except ValueError:
                                pass
                # If no segment_mode but Segment column has value, still pass through for Java to validate
                if validated_segment_id is None and validated_segment_name is None:
                    segment_value = row.get("Segment")
                    if segment_value is not None and str(segment_value).strip() and not pd.isna(segment_value):
                        seg_name = str(segment_value).strip()
                        if validate_segment_exists:
                            exists, seg_id = validate_segment_exists(seg_name)
                            if exists:
                                if user_id and validate_user_segment_access:
                                    has_access, _ = validate_user_segment_access(user_id, seg_name)
                                    if has_access:
                                        validated_segment_id = seg_id
                                        validated_segment_name = seg_name
                                else:
                                    validated_segment_id = seg_id
                                    validated_segment_name = seg_name
                if validated_segment_id is not None:
                    resolved["Segment"] = validated_segment_name
                    resolved["segmentName"] = validated_segment_name
                    resolved["segmentId"] = validated_segment_id
                
                # Validate Parent Committee - check if both ref and name are provided and match
                parent_ref = row.get("Parent Ref.")
                parent_name = row.get("Parent Committee Name")
                has_ref = parent_ref and str(parent_ref).strip()
                has_name = parent_name and str(parent_name).strip()
                parent_in_file = (has_ref and str(parent_ref).strip().lower() in refs_in_file) or (has_name and str(parent_name).strip().lower() in names_in_file)
                
                if parent_in_file:
                    pass  # Java will resolve from batch; do not set Parent_ID
                else:
                    if has_ref and has_name:
                        parent_id_by_ref = get_committee_id_by_ref(parent_ref)
                        parent_id_by_name = get_committee_id_by_name(parent_name)
                        if parent_id_by_ref and parent_id_by_name and parent_id_by_ref != parent_id_by_name:
                            errors.append(ValidationError(
                                row=row_num,
                                field="Parent Ref.",
                                message=f"Parent reference '{parent_ref}' and parent name '{parent_name}' refer to different committees",
                                error_code="PARENT_NAME_REF_MISMATCH"
                            ))
                            continue
                    parent_id = _resolve_parent_id(row)
                    if parent_id:
                        resolved["Parent_ID"] = parent_id
                
                # Lookup mappings
                mappings = [
                    ("BUDG Viewing", "BUDG Viewing_ID", get_viewing_id_by_name),
                    ("BUDG Status", "BUDG Status_ID", lambda v: get_lookup_id_by_name("status", v)),
                    ("Classification", "Classification_ID", lambda v: get_lookup_id_by_name("committee_classification", v)),
                    ("Lifecycle", "Lifecycle_ID", lambda v: get_lookup_id_by_name("committee_lifecycle", v)),
                    ("Committee Type", "Committee Type_ID", lambda v: get_lookup_id_by_name("committee_type", v)),
                    ("Governance Role", "Governance Role_ID", lambda v: get_lookup_id_by_name("object_role", v)),
                ]
                
                for src, out, fn in mappings:
                    vid = fn(row.get(src))
                    if vid:
                        resolved[out] = vid
                    # Also pass through the name if provided (for Java to resolve)
                    if row.get(src) and not resolved.get(src):
                        resolved[src] = row.get(src)
                
                # User Email (optional) - pass through for stakeholder creation
                user_email = row.get("User Email")
                if user_email and str(user_email).strip():
                    resolved["User Email"] = str(user_email).strip()
                
                # Copy pass-through text fields and identity columns so Java can re-validate (Committee ID, Reference, Committee Name must match same committee)
                for k in ["Committee ID", "Committee Name", "Reference", "Description"]:
                    if k in row and row.get(k) not in (None, ""):
                        v = row.get(k)
                        if pd.notna(v):
                            resolved[k] = v
                
                # Validate and add custom fields
                if add_custom_fields_to_validated_data:
                    add_custom_fields_to_validated_data(series, resolved, "Committee", row_num, errors)
                
                data.append(resolved)
            
            elif upload_option == "Remove Existing Items":
                if row.get("Committee ID") in (None, ""):
                    errors.append(ValidationError(row_num, "Committee ID", "Committee ID is required for delete", "REQUIRED_FIELD_EMPTY"))
                    continue
                
                data.append({
                    "operation": "DELETE",
                    "row_number": row_num,
                    "Committee ID": int(row.get("Committee ID")) if str(row.get("Committee ID")).strip().isdigit() else row.get("Committee ID"),
                })
            else:
                errors.append(ValidationError(row_num, "operation", f"Unsupported operation: {upload_option}", "INVALID_OPERATION"))
        
        except Exception as e:
            logger.error("Error validating row %s: %s", row_num, e)
            errors.append(ValidationError(row_num, "row", f"Unexpected error: {e}", "UNEXPECTED_ERROR"))
    
    return errors, data


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
    Example: {"اسم اللجنة": "Committee Name", "الوصف": "Description"}
    
    Uses smart matching to find Excel columns even if they have slight variations:
    - Exact match: "a" matches "a"
    - Case-insensitive: "a" matches "A"
    - Partial match: "a" matches "a (example)" or "A Column"
    - Contains match: "comm" matches "Committee Name" (if key is >= 3 chars)
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
            logger.warning(f"Skipping empty mapping: '{excel_col_mapping}' -> '{expected_field}'")
            continue
        
        expected_field_normalized = str(expected_field).strip()
        
        # Find matching column in DataFrame using smart matching
        matched_col = find_excel_column_match(excel_col_mapping, available_columns)
        
        if matched_col:
            # Check if this column was already mapped
            if matched_col in used_columns:
                logger.warning(f"Column '{matched_col}' already mapped, skipping duplicate mapping for '{excel_col_mapping}' -> '{expected_field_normalized}'")
                continue
            
            rename_dict[matched_col] = expected_field_normalized
            used_columns.add(matched_col)
            logger.info(f"Smart mapping: Excel column '{matched_col}' (matched from '{excel_col_mapping}') -> '{expected_field_normalized}'")
        else:
            logger.warning(f"Could not find Excel column matching '{excel_col_mapping}' in available columns: {available_columns}")
    
    if rename_dict:
        logger.info(f"Applying {len(rename_dict)} column mappings: {list(rename_dict.keys())} -> {list(rename_dict.values())}")
        df = df.rename(columns=rename_dict)
        logger.info(f"DataFrame columns after mapping: {list(df.columns)}")
    else:
        logger.warning(f"No column mappings could be applied. DataFrame columns: {list(df.columns)}, Mappings: {list(column_mappings.keys())}")
    
    return df

