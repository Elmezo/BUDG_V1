"""
Parent hierarchy validation for bulk upload update.
Prevents circular parent references: when updating object A's parent to B,
reject if B is already a descendant of A (would create cycle A -> B -> ... -> A).
"""

import logging

logger = logging.getLogger(__name__)

# Entity -> (table_name, id_column, parent_column, deleted_column or None)
HIERARCHY_CONFIG = {
    "Process": ("process", "id", "parentid", "deleteddatetime"),  # process table uses parentid, not parent_id
    "Policy": ("policy", "ID", "ParentID", "DeletedDatetime"),
    "Glossary": ("glossary", "ID", "Parent_ID", "Deleted_datetime"),
    "System": ("system", "id", "parent_id", "Deleted_datetime"),
    "Regulation": ("regulation", "ID", "parent_id", "DeletedDatetime"),
    "Project": ("project", "id", "parent_id", "deletedatetime"),
    "OrgUnit": ("org_unit", "ID", "Parent_ID", "deleted_Date"),
    "Client": ("client", "ID", "Parent_ID", "DeleteDatetime"),
    "Product": ("product", "id", "parent_id", "deleteddatetime"),
}

MAX_ANCESTOR_DEPTH = 1000


def would_create_parent_cycle(get_connection, table, id_col, parent_col, object_id, new_parent_id, deleted_col=None):
    """
    Returns True if setting object_id's parent to new_parent_id would create a circular hierarchy
    (i.e. new_parent_id is already a descendant of object_id).
    Walks the parent chain from new_parent_id upward; if object_id is reached, returns True.
    @param get_connection: callable that returns a DB connection (e.g. get_db_connection from processor)
    @param table: table name (e.g. "process", "policy")
    @param id_col: primary key column name (e.g. "id", "ID")
    @param parent_col: parent FK column name (e.g. "parent_id", "ParentID")
    @param object_id: the object being updated (would become child of new_parent_id)
    @param new_parent_id: the proposed new parent
    @param deleted_col: optional soft-delete column name to exclude deleted rows
    @return: True if cycle would be created
    """
    if object_id == new_parent_id:
        return True
    deleted_condition = ""
    if deleted_col:
        deleted_condition = f" AND ({deleted_col} IS NULL OR {deleted_col} = '')"
    sql = f"SELECT {parent_col} FROM {table} WHERE {id_col} = %s{deleted_condition} LIMIT 1"
    current = new_parent_id
    depth = 0
    try:
        conn = get_connection()
        try:
            with conn.cursor() as cursor:
                while current and current > 0 and depth < MAX_ANCESTOR_DEPTH:
                    if current == object_id:
                        return True
                    cursor.execute(sql, (current,))
                    row = cursor.fetchone()
                    if not row or row[0] is None:
                        break
                    current = int(row[0]) if row[0] is not None else None
                    depth += 1
        finally:
            conn.close()
    except Exception as e:
        logger.warning("parent_hierarchy_validator error: %s", e)
        return False
    return False


def would_create_parent_cycle_by_entity(get_connection, entity, object_id, new_parent_id):
    """
    Convenience: check by entity name (e.g. "Process", "Policy").
    @param get_connection: callable that returns a DB connection
    @param entity: entity/object type name (e.g. "Process", "Policy", "Glossary", "System",
                   "Regulation", "Project", "OrgUnit", "Client", "Product")
    @param object_id: the object being updated
    @param new_parent_id: the proposed new parent
    @return: True if cycle would be created
    """
    config = HIERARCHY_CONFIG.get(entity)
    if not config:
        return False
    table, id_col, parent_col, deleted_col = config
    return would_create_parent_cycle(
        get_connection, table, id_col, parent_col, object_id, new_parent_id, deleted_col
    )
