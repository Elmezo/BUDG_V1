"""
Segment validation utilities for bulk upload validation
Provides functions to validate segment existence, user access, and get segment IDs
"""

import pymysql
import os
import logging

logger = logging.getLogger(__name__)

try:
    from budg_db_config import get_pymysql_config

    DB_CONFIG = get_pymysql_config()
except ImportError:
    DB_CONFIG = {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": int(os.getenv("DB_PORT", "3306")),
        "user": os.getenv("DB_USERNAME", "root"),
        "password": os.getenv("DB_PASSWORD", ""),
        "database": os.getenv("DB_NAME", "project"),
        "charset": "utf8mb4",
    }


def get_connection():
    """Get database connection"""
    return pymysql.connect(**DB_CONFIG)


def validate_segment_exists(segment_name):
    """
    Check if segment exists in database
    @param segment_name: The segment name to check
    @return: (exists: bool, segment_id: int or None)
    """
    if not segment_name or not str(segment_name).strip():
        return False, None

    normalized = str(segment_name).strip()

    # Handle "Enterprise" as special case (always exists, ID = 1)
    if normalized.lower() == "enterprise":
        return True, 1

    try:
        conn = get_connection()
        try:
            with conn.cursor() as cursor:
                # Match Java (e.g. SystemBulkUploadServlet.getSegmentIdByName): LOWER + trim parity
                sql = (
                    "SELECT ID FROM segment WHERE LOWER(TRIM(Name)) = LOWER(%s) "
                    "AND (Deleted_At IS NULL) LIMIT 1"
                )
                cursor.execute(sql, (normalized,))
                result = cursor.fetchone()
                if result:
                    return True, result[0]
                logger.warning(
                    "Segment not found for name %r (connected to %s:%s/%s). "
                    "If Tomcat uses another host, set the jdbc URL in DatabaseConnection.java "
                    "(Python prefers that default over repo .env DB_URL) or set DB_URL in env.",
                    normalized,
                    DB_CONFIG.get("host"),
                    DB_CONFIG.get("port"),
                    DB_CONFIG.get("database"),
                )
                return False, None
        finally:
            conn.close()
    except Exception as e:
        logger.error(f"Error checking segment existence: {e}")
        return False, None


def is_super_admin(user_id):
    """
    Check if user is a Super Admin (full access, no assignment needed)
    Dynamically checks the role name from the role table
    @param user_id: The user ID
    @return: True if user has 'Super Admin' role (exact match)
    """
    if not user_id or user_id <= 0:
        return False
    
    try:
        conn = get_connection()
        try:
            with conn.cursor() as cursor:
                # Join people with role table to get the actual role name
                sql = """
                    SELECT r.primaryname AS role_name
                    FROM people p
                    LEFT JOIN role r ON p.System_Role = r.id
                    WHERE p.ID = %s
                """
                cursor.execute(sql, (user_id,))
                result = cursor.fetchone()
                if result:
                    role_name = result[0]
                    if role_name:
                        # Only "Super Admin" has full access without assignment
                        # "Admin" needs to be assigned to segments
                        normalized_role = role_name.lower().strip()
                        return normalized_role == "super admin"
        finally:
            conn.close()
    except Exception as e:
        logger.error(f"Error checking super admin status: {e}")
        return False
    
    return False


def validate_user_segment_access(user_id, segment_name):
    """
    Check if user has access to a specific segment
    Uses v_user_accessible_segments view
    Super Admin users bypass all segment access checks
    @param user_id: The user ID
    @param segment_name: The segment name
    @return: (has_access: bool, segment_id: int or None)
    """
    if not segment_name or not segment_name.strip():
        return False, None
    
    # Super Admin bypass: Super Admin has access to all segments
    if user_id and is_super_admin(user_id):
        # Get segment ID for super admin (they have access to all)
        exists, segment_id = validate_segment_exists(segment_name)
        if exists:
            return True, segment_id
        return False, None
    
    # Enterprise (ID=1) is always accessible
    if segment_name.strip().lower() == "enterprise":
        return True, 1
    
    try:
        conn = get_connection()
        try:
            with conn.cursor() as cursor:
                # First get segment ID by name
                segment_sql = (
                    "SELECT ID FROM segment WHERE LOWER(TRIM(Name)) = LOWER(%s) "
                    "AND (Deleted_At IS NULL) LIMIT 1"
                )
                cursor.execute(segment_sql, (segment_name.strip(),))
                segment_result = cursor.fetchone()
                if not segment_result:
                    return False, None
                
                segment_id = segment_result[0]
                
                # Check if user has access via v_user_accessible_segments
                access_sql = """
                    SELECT COUNT(*) as has_access
                    FROM v_user_accessible_segments
                    WHERE user_id = %s AND segment_id = %s
                """
                cursor.execute(access_sql, (user_id, segment_id))
                access_result = cursor.fetchone()
                
                has_access = access_result[0] > 0 if access_result else False
                return has_access, segment_id if has_access else None
        finally:
            conn.close()
    except Exception as e:
        logger.error(f"Error checking user segment access: {e}")
        return False, None


def segment_matches_ui_selection(segment_name, ui_segment_id_str):
    """
    When the UI sends a selected segment ID (e.g. SPECIFIC mode) and Excel names that segment,
    accept the row if the ID's canonical name matches Excel (case-insensitive).
    Helps when name-based lookup differs from Java or after UI/Excel alignment checks.
    """
    if not segment_name or not str(segment_name).strip():
        return False, None
    if ui_segment_id_str is None or not str(ui_segment_id_str).strip():
        return False, None
    raw = str(ui_segment_id_str).strip()
    if not raw.isdigit():
        return False, None
    sid = int(raw)
    db_name = get_segment_name_by_id(sid)
    if not db_name:
        return False, None
    if db_name.strip().lower() == str(segment_name).strip().lower():
        return True, sid
    return False, None


def get_segment_id_by_name(segment_name):
    """
    Get segment ID from name
    @param segment_name: The segment name
    @return: segment_id (int) or None if not found
    """
    if not segment_name or not segment_name.strip():
        return None
    
    # Handle "Enterprise" as special case
    if segment_name.strip().lower() == "enterprise":
        return 1
    
    exists, segment_id = validate_segment_exists(segment_name)
    return segment_id if exists else None


def get_segment_name_by_id(segment_id):
    """
    Get segment name from ID
    @param segment_id: The segment ID
    @return: segment_name (str) or None if not found
    """
    if segment_id is None:
        return None
    
    # Enterprise is ID 1
    if segment_id == 1:
        return "Enterprise"
    
    try:
        conn = get_connection()
        try:
            with conn.cursor() as cursor:
                sql = "SELECT Name FROM segment WHERE ID = %s AND Deleted_At IS NULL LIMIT 1"
                cursor.execute(sql, (segment_id,))
                result = cursor.fetchone()
                if result:
                    return result[0]
                return None
        finally:
            conn.close()
    except Exception as e:
        logger.error(f"Error getting segment name by ID: {e}")
        return None


def get_object_segment_id(object_id, object_type):
    """
    Get the segment ID for an object (same logic as Java SegmentDAO.getObjectSegmentId).
    @param object_id: The object ID (e.g. process id, policy ID)
    @param object_type: The segment object type (e.g. "Process", "Policy", "Glossary", "System",
                       "Regulation", "Project", "OrgUnit", "Client", "Product")
    @return: segment_id (int) or -1 when not assigned; None on error
    """
    if object_id is None or object_id <= 0:
        return None
    try:
        conn = get_connection()
        try:
            with conn.cursor() as cursor:
                sql = """
                    SELECT sxr.Segment_ID
                    FROM segment_x_resource sxr
                    JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
                    JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
                    WHERE orr.Object_ID = %s
                    AND sot.Type = %s
                    AND sxr.Deleted_At IS NULL
                    LIMIT 1
                """
                cursor.execute(sql, (object_id, object_type))
                result = cursor.fetchone()
                if result:
                    return result[0]
                return -1
        finally:
            conn.close()
    except Exception as e:
        logger.error(f"Error getting object segment ID for {object_type} {object_id}: {e}")
        return None

