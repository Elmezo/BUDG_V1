"""
Segment-scoped name uniqueness for bulk upload validation (aligned with Java BulkUploadNameValidator).
Same display name may exist in different segments; duplicates are only blocked within the target segment.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, Optional, Tuple

from segment_validator import get_connection, validate_segment_exists

logger = logging.getLogger(__name__)

# (table, name_column, id_column, object_type_string, deleted_column) — matches BulkUploadNameValidator maps
FACET_DUP_CONFIG: Dict[str, Tuple[str, str, str, str, Optional[str]]] = {
    "Policy": ("policy", "PrimaryName", "ID", "Policy", "DeletedDatetime"),
    "Process": ("process", "primaryname", "id", "Process", "deleteddatetime"),
    "Committee": ("committee", "PrimaryName", "ID", "Committee", "DeleteDatetime"),
    "Regulation": ("regulation", "primaryName", "ID", "Regulation", "DeletedDatetime"),
    "Regulator": ("regulator", "PrimaryName", "ID", "Regulator", "DeletedDatetime"),
    "Geography": ("geography", "PrimaryName", "ID", "Geography", "DeletedDatetime"),
    "RegulatoryTheme": ("regulatorytheme", "PrimaryName", "ID", "RegulatoryTheme", "DeletedDatetime"),
    "Interface": ("interface", "Name", "id", "SystemInterface", "deleted_datetime"),
}


def name_exists_in_segment(
    table: str,
    name_column: str,
    id_column: str,
    object_type: str,
    segment_id: int,
    name: str,
    deleted_column: Optional[str] = None,
    exclude_id: Optional[int] = None,
) -> bool:
    """Return True if a non-deleted row with this name exists in the given segment (via segment_x_resource)."""
    if not name or not str(name).strip():
        return False
    nm = str(name).strip()
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            sql = [
                f"SELECT 1 FROM `{table}` t ",
                "INNER JOIN object_reference orr ON t.`",
                id_column,
                "` = orr.Object_ID ",
                "INNER JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID ",
                "INNER JOIN segment_x_resource sxr ON orr.ID = sxr.Object_Reference_ID ",
                "WHERE sot.Type = %s AND sxr.Segment_ID = %s ",
                "AND LOWER(t.`",
                name_column,
                "`) = LOWER(%s) AND sxr.Deleted_At IS NULL ",
            ]
            params: list = [object_type, segment_id, nm]
            if deleted_column:
                sql.append(f"AND (t.`{deleted_column}` IS NULL OR t.`{deleted_column}` = '') ")
            if exclude_id is not None:
                sql.append(f"AND t.`{id_column}` != %s ")
                params.append(exclude_id)
            sql.append("LIMIT 1")
            cur.execute("".join(sql), tuple(params))
            return cur.fetchone() is not None
    except Exception as e:
        logger.error("name_exists_in_segment error: %s", e)
        raise
    finally:
        conn.close()


def duplicate_name_in_facet_segment(
    facet: str,
    segment_id: int,
    name: str,
    exclude_id: Optional[int] = None,
) -> bool:
    """Convenience helper using FACET_DUP_CONFIG."""
    cfg = FACET_DUP_CONFIG.get(facet)
    if not cfg:
        raise ValueError(f"Unknown facet for segment duplicate check: {facet}")
    table, name_col, id_col, obj_type, deleted_col = cfg
    return name_exists_in_segment(
        table, name_col, id_col, obj_type, segment_id, name, deleted_col, exclude_id
    )


def resolve_effective_segment_id_for_duplicate_check(
    row: Any,
    segment_mode: Optional[str],
    segment_ui: Optional[str],
) -> int:
    """
    Best-effort target segment ID for pre-insert duplicate checks (defaults to Enterprise 1).
    Aligns with Java default when segment is not specified on the row.
    """
    try:
        import pandas as pd
    except ImportError:
        pd = None

    seg_val = row.get("Segment") if hasattr(row, "get") else None
    if seg_val is not None and pd is not None and not pd.isna(seg_val):
        sn = str(seg_val).strip()
        if sn:
            exists, sid = validate_segment_exists(sn)
            if exists and sid is not None:
                return int(sid)
    if segment_ui and str(segment_ui).strip():
        try:
            sid = int(str(segment_ui).strip())
            if sid > 0:
                return sid
        except ValueError:
            pass
    return 1
