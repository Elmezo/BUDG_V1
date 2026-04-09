"""
Relationship Bulk Upload Processor
Handles validation and resolution for all relationship types
"""

from typing import List, Dict, Any, Tuple, Optional
import json
import pandas as pd
import pymysql
import os
import logging
from pathlib import Path

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


# Configuration mapping for relationship entities
RELATIONSHIP_ENTITY_CONFIG = {
    "attribute x attribute": {
        "relationship_type": "Attribute X Attribute",
        "relationship_table": "attribute_x_attribute",
        "entity_a": {
            "name": "Source Attribute",
            "table": "attribute",
            "name_field": "PrimaryName",
            "ref_field": "RefNumber"
        },
        "entity_b": {
            "name": "Target Attribute",
            "table": "attribute",
            "name_field": "PrimaryName",
            "ref_field": "RefNumber"
        },
        "columns": {
            "sourcing_logic": "Sourcing Logic",
            "sourcing_type": "Sourcing Type",
            "target_attribute_ref": "Target Attribute Ref.",
            "target_attribute_name": "Target Attribute Name",
            "target_data_set_name": "Target Data Set Name",
            "target_system_short_name": "Target System Short Name",
            "source_attribute_ref": "Source Attribute Ref.",
            "source_attribute_name": "Source Attribute Name",
            "source_data_set_name": "Source Data Set Name",
            "source_system_short_name": "Source System Short Name",
            "scope_of_data": "Scope of Data",
            "interface_name": "Interface Name",
            "interface_source_system_short_name": "Interface Source System Short Name",
            "interface_target_system_short_name": "Interface Target System Short Name"
        },
        "relation_type_table": "attribute_x_attribute_relationtype",
        "relation_type_field": "PrimaryName",
        "supported_operations": ["INSERT", "DELETE"]
    },
    "systemxcatitemcategory": {
        "relationship_type": "System X Glossary",
        "relationship_table": "glossary_x_system",
        "entity_a": {
            "name": "System",
            "table": "system",
            "name_field": "Name",
            "ref_field": "AssetID",
            "db_column": "SystemID"
        },
        "entity_b": {
            "name": "Glossary",
            "table": "glossary",
            "name_field": "Name",
            "ref_field": "Ref_Number",
            "db_column": "GlossaryID"
        },
        "columns": {
            "system_name": "System Name",
            "glossary_name": "Glossary Name"
        },
        "supported_operations": ["INSERT", "DELETE"]
    },
    "catitemcategoryxsystem": {
        "relationship_type": "Glossary X System",
        "relationship_table": "glossary_x_system",
        "entity_a": {
            "name": "Glossary",
            "table": "glossary",
            "name_field": "Name",
            "ref_field": "Ref_Number",
            "db_column": "GlossaryID"
        },
        "entity_b": {
            "name": "System",
            "table": "system",
            "name_field": "Name",
            "ref_field": "AssetID",
            "db_column": "SystemID"
        },
        "columns": {
            "system_name": "System Name",
            "glossary_name": "Glossary Name"
        },
        "supported_operations": ["INSERT", "DELETE"]
    },
    "catitemcategory hierarchy": {
        "relationship_type": "Glossary Hierarchy",
        "relationship_table": "glossary_x_glossary",
        "entity_a": {
            "name": "Parent Glossary",
            "table": "glossary",
            "name_field": "Name",
            "ref_field": "Ref_Number",
            "db_column": "SourceGlossaryID"
        },
        "entity_b": {
            "name": "Child Glossary",
            "table": "glossary",
            "name_field": "Name",
            "ref_field": "Ref_Number",
            "db_column": "TargetGlossaryID"
        },
        "columns": {
            "parent_name": "Parent Name",
            "child_name": "Child Name"
        },
        "supported_operations": ["INSERT", "DELETE"]
    },
    "catitemcategoryxcatitemcategory": {
        "relationship_type": "Glossary X Glossary",
        "relationship_table": "glossary_x_glossary",
        "entity_a": {
            "name": "Source Glossary",
            "table": "glossary",
            "name_field": "Name",
            "ref_field": "Ref_Number",
            "db_column": "SourceGlossaryID"
        },
        "entity_b": {
            "name": "Target Glossary",
            "table": "glossary",
            "name_field": "Name",
            "ref_field": "Ref_Number",
            "db_column": "TargetGlossaryID"
        },
        "columns": {
            "source_glossary_name": "Source Glossary Name",
            "target_glossary_name": "Target Glossary Name"
        },
        "supported_operations": ["INSERT", "DELETE"]
    },
    "attribute x physical field": {
        "relationship_type": "Attribute X Physical Field",
        "relationship_table": "attribute_x_physicalfield",
        "entity_a": {
            "name": "Attribute",
            "table": "attribute",
            "name_field": "PrimaryName",
            "ref_field": "RefNumber"
        },
        "columns": {
            "attribute_ref": "Attribute Ref.",
            "attribute_name": "Attribute Name",
            "attribute_data_set_name": "Attribute Data Set Name",
            "attribute_system_short_name": "Attribute System Short Name",
            "name": "Name",
            "field_id": "Field ID",
            "field_type": "Field Type"
        },
        "supported_operations": ["INSERT"]
    },
    "business area x glossary": {
        "relationship_type": "Business Area X Glossary",
        "relationship_table": "businessarea_x_glossary",
        "entity_a": {
            "name": "Business Area",
            "table": "business_area",
            "name_field": "PrimaryName",
            "parent_field": "Parent_ID"
        },
        "entity_b": {
            "name": "Glossary",
            "table": "glossary",
            "name_field": "PrimaryName",
            "ref_field": "RefNumber",
            "parent_field": "Parent_ID"
        },
        "columns": {
            "glossary_ref": "Glossary Ref.",
            "glossary_name": "Glossary Name",
            "parent_glossary_name": "Parent Glossary Name",
            "business_area_name": "Business Area Name",
            "parent_business_area_name": "Parent Business Area Name",
            "relationship_type": "Relationship Type"
        },
        "relation_type_table": "businessarea_x_glossary_relationtype",
        "relation_type_field": "PrimaryName",
        "supported_operations": ["INSERT", "DELETE"]
    },
    "capability x product": {
        "relationship_type": "Capability X Product",
        "relationship_table": "capability_x_product",
        "entity_a": {
            "name": "Capability",
            "table": "capability",
            "name_field": "PrimaryName",
            "ref_field": "RefNumber",
            "parent_field": "Parent_ID"
        },
        "entity_b": {
            "name": "Product",
            "table": "product",
            "name_field": "PrimaryName",
            "ref_field": "RefNumber",
            "parent_field": "Parent_ID"
        },
        "columns": {
            "product_name": "Product Name",
            "product_parent_name": "Product Parent Name",
            "capability_ref": "Capability Ref.",
            "capability_name": "Capability Name",
            "capability_parent_name": "Capability Parent Name",
            "relationship_type": "Relationship Type"
        },
        "relation_type_table": "capability_x_product_relationtype",
        "relation_type_field": "PrimaryName",
        "supported_operations": ["INSERT", "DELETE"]
    },
    "policy x system": {
        "relationship_type": "Policy X System",
        "relationship_table": "policy_x_system",
        "entity_a": {
            "name": "Policy",
            "table": "policy",
            "name_field": "PrimaryName",
            "ref_field": "RefNumber",
            "parent_field": "Parent_ID"
        },
        "entity_b": {
            "name": "System",
            "table": "system",
            "name_field": "ShortName"
        },
        "columns": {
            "policy_ref": "Policy Ref.",
            "policy_name": "Policy Name",
            "parent_policy_name": "Parent Policy Name",
            "system_short_name": "System Short Name",
            "parent_system_short_name": "Parent System Short Name",
            "relationship_type": "Relationship Type"
        },
        "relation_type_table": "policy_x_system_relationtype",
        "relation_type_field": "PrimaryName",
        "supported_operations": ["INSERT", "DELETE"]
    },
    "segment x object": {
        "relationship_type": "Segment X Object",
        "relationship_table": "segment_x_object",
        "columns": {
            "object_id": "Object ID",
            "object_type": "Object Type",
            "target_segment": "Target Segment"
        },
        "relation_type_table": "segment_object_type",
        "relation_type_field": "Type",
        "supported_operations": ["UPDATE"]
    },
    "system x resource": {
        "relationship_type": "System X Resource",
        "relationship_table": "system_x_resource",
        "entity_a": {
            "name": "System",
            "table": "system",
            "name_field": "ShortName"
        },
        "columns": {
            "system_short_name": "System Short Name",
            "parent_system_short_name": "Parent System Short Name",
            "name": "Name",
            "resource_id": "Resource ID"
        },
        "supported_operations": ["INSERT"]
    }
}


def _slug_header(h: str) -> str:
    return (
        h.lower()
        .replace(" ", "_")
        .replace(".", "")
        .replace("(", "")
        .replace(")", "")
        .replace("%", "")
    )


def _load_relationship_metadata_into_config() -> None:
    """Merge Java-exported junction metadata (relationship_metadata.json) into RELATIONSHIP_ENTITY_CONFIG."""
    p = Path(__file__).resolve().parent / "relationship_metadata.json"
    if not p.is_file():
        logger.warning("relationship_metadata.json not found under %s", p.parent)
        return
    try:
        arr = json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:
        logger.error("Failed to read relationship_metadata.json: %s", e)
        return
    for entry in arr:
        key = (entry.get("key") or "").lower().strip()
        if not key or key == "peoplexpeople":
            continue
        if key in RELATIONSHIP_ENTITY_CONFIG:
            continue
        headers = entry.get("headers") or []
        columns = {_slug_header(h): h for h in headers}
        ea = entry.get("entity_a") or {}
        eb = entry.get("entity_b") or {}
        entity_a: Dict[str, Any] = {
            "name": ea.get("name", ""),
            "table": ea.get("table", ""),
            "name_field": ea.get("name_field", ""),
            "db_column": ea.get("db_column", ""),
        }
        if ea.get("ref_field"):
            entity_a["ref_field"] = ea["ref_field"]
        if ea.get("parent_field"):
            entity_a["parent_field"] = ea["parent_field"]
        entity_b: Dict[str, Any] = {
            "name": eb.get("name", ""),
            "table": eb.get("table", ""),
            "name_field": eb.get("name_field", ""),
            "db_column": eb.get("db_column", ""),
        }
        if eb.get("ref_field"):
            entity_b["ref_field"] = eb["ref_field"]
        if eb.get("parent_field"):
            entity_b["parent_field"] = eb["parent_field"]
        cfg: Dict[str, Any] = {
            "relationship_table": entry.get("relationship_table", ""),
            "entity_a": entity_a,
            "entity_b": entity_b,
            "columns": columns,
            "supported_operations": ["INSERT", "DELETE"],
        }
        if entry.get("relation_type_table"):
            cfg["relation_type_table"] = entry["relation_type_table"]
            cfg["relation_type_field"] = entry.get("relation_type_field") or "RelationType"
        RELATIONSHIP_ENTITY_CONFIG[key] = cfg


_load_relationship_metadata_into_config()


def resolve_relationship_entity_key(entity_label: str) -> str:
    s = (entity_label or "").strip().lower()
    c = s.replace(" ", "").replace("_", "")
    if s in RELATIONSHIP_ENTITY_CONFIG:
        return s
    if c in RELATIONSHIP_ENTITY_CONFIG:
        return c
    return c


def sanitize_value(value: Any) -> Optional[str]:
    """Sanitize value to handle NaN, None, and empty strings"""
    if value is None:
        return None
    if pd.isna(value):
        return None
    value_str = str(value).strip()
    if not value_str or value_str.lower() == 'nan':
        return None
    return value_str


def resolve_entity_by_name_and_ref(table: str, name_field: str, ref_field: Optional[str],
                                    name_value: Optional[str], ref_value: Optional[str],
                                    parent_field: Optional[str], parent_value: Optional[str],
                                    row_num: int) -> Tuple[Optional[int], List[Dict]]:
    """Resolve entity ID from name/ref with optional parent disambiguation"""
    errors = []
    
    # Sanitize input values
    name_value = sanitize_value(name_value)
    ref_value = sanitize_value(ref_value)
    parent_value = sanitize_value(parent_value)
    
    if not name_value and not ref_value:
        errors.append({
            "row": row_num,
            "field": "entity",
            "message": f"Missing required information: Either Name or Reference must be provided for {table}. Please provide at least one identifier to locate the {table} in the system.",
            "error_code": "REQUIRED_FIELD"
        })
        return None, errors
    
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                # Try by ref first if provided
                if ref_value and ref_field:
                    sql = f"SELECT id FROM {table} WHERE {ref_field} = %s"
                    cur.execute(sql, (ref_value,))
                    result = cur.fetchone()
                    if result:
                        return result[0], errors
                
                # Try by name
                if name_value:
                    sql = f"SELECT id, {name_field}"
                    if parent_field and parent_value:
                        sql += f", {parent_field}"
                    sql += f" FROM {table} WHERE LOWER({name_field}) = LOWER(%s)"
                    
                    cur.execute(sql, (name_value,))
                    results = cur.fetchall()
                    
                    if not results:
                        ref_hint = f" You may also try using the Reference number ({ref_field}) instead." if ref_field else ""
                        errors.append({
                            "row": row_num,
                            "field": "entity",
                            "message": f"Entity not found: No {table} found with name '{name_value}'.{ref_hint}",
                            "error_code": "NOT_FOUND"
                        })
                        return None, errors
                    
                    if len(results) == 1:
                        return results[0][0], errors
                    
                    # Multiple results - use parent to disambiguate
                    if parent_value and parent_field:
                        # Get parent ID first
                        parent_id = None
                        cur.execute(f"SELECT id FROM {table} WHERE LOWER({name_field}) = LOWER(%s)", (parent_value,))
                        parent_result = cur.fetchone()
                        if parent_result:
                            parent_id = parent_result[0]
                        
                        if parent_id:
                            for result in results:
                                if len(result) > 2 and result[2] == parent_id:
                                    return result[0], errors
                        
                        errors.append({
                            "row": row_num,
                            "field": "entity",
                            "message": f"Ambiguous match: Multiple {table} found with name '{name_value}' and parent '{parent_value}'. Please provide the Reference number or additional information to uniquely identify the correct {table}.",
                            "error_code": "AMBIGUOUS_MATCH"
                        })
                    else:
                        ref_hint = f" or Reference number ({ref_field})" if ref_field else ""
                        errors.append({
                            "row": row_num,
                            "field": "entity",
                            "message": f"Ambiguous match: Found {len(results)} {table} records with name '{name_value}'. Please provide the Parent Name{ref_hint} to uniquely identify the correct record.",
                            "error_code": "AMBIGUOUS_MATCH"
                        })
                    return None, errors
                
    except Exception as e:
        logger.error(f"Error resolving entity from {table}: {e}")
        errors.append({
            "row": row_num,
            "field": "entity",
            "message": f"Database error: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return None, errors


def validate_relation_type(relation_type_value: str, relation_type_table: str, 
                           relation_type_field: str, row_num: int) -> List[Dict]:
    """Validate that relation type exists in the relation type table"""
    errors = []
    
    if not relation_type_value or pd.isna(relation_type_value):
        errors.append({
            "row": row_num,
            "field": "relationship_type",
            "message": "Required field missing: The 'Relationship Type' field is required and cannot be empty. Please provide a relationship type for this relationship.",
            "error_code": "REQUIRED_FIELD"
        })
        return errors
    
    relation_type_value = str(relation_type_value).strip()
    
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                sql = f"SELECT COUNT(*) FROM {relation_type_table} WHERE {relation_type_field} = %s"
                cur.execute(sql, (relation_type_value,))
                count = cur.fetchone()[0]
                
                if count == 0:
                    errors.append({
                        "row": row_num,
                        "field": "relationship_type",
                        "message": f"Relationship type not found: The relationship type '{relation_type_value}' does not exist in {relation_type_table}. Please verify the relationship type name is correct and exists in the system.",
                        "error_code": "NOT_FOUND"
                    })
                
    except Exception as e:
        logger.error(f"Error validating relation type: {e}")
        errors.append({
            "row": row_num,
            "field": "relationship_type",
            "message": f"Database error: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return errors


def check_relationship_exists(relationship_table: str, entity_a_id: int, entity_b_id: Optional[int],
                              entity_a_col: str, entity_b_col: Optional[str], row_num: int) -> bool:
    """Check if relationship already exists (for INSERT) or doesn't exist (for DELETE)"""
    
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                # Build query based on whether we have entity_b
                if entity_b_id and entity_b_col:
                    sql = f"SELECT COUNT(*) FROM {relationship_table} WHERE {entity_a_col} = %s AND {entity_b_col} = %s"
                    params = (entity_a_id, entity_b_id)
                else:
                    sql = f"SELECT COUNT(*) FROM {relationship_table} WHERE {entity_a_col} = %s"
                    params = (entity_a_id,)
                
                cur.execute(sql, params)
                count = cur.fetchone()[0]
                
                return count > 0
                
    except Exception as e:
        logger.error(f"Error checking relationship existence in {relationship_table}: {e}")
        # Return False to let validation continue - actual DB errors will be caught during insert/delete
    
    return False


def validate_and_resolve_relationship(df: pd.DataFrame, entity_label: str, 
                                      upload_option: str, user_id: int) -> Tuple[List[Dict], List[Dict]]:
    """
    Validate and resolve relationship data
    Returns: (validated_data, errors)
    """
    entity_key = resolve_relationship_entity_key(entity_label)
    
    if entity_key not in RELATIONSHIP_ENTITY_CONFIG:
        error = {
            "row": 0,
            "field": "entity",
            "message": f"Unsupported relationship entity: {entity_label}",
            "error_code": "UNSUPPORTED_ENTITY"
        }
        return [], [error]
    
    config = RELATIONSHIP_ENTITY_CONFIG[entity_key]
    
    # Normalize column names
    df = df.copy()
    df.columns = [col.lower().replace(' ', '_').replace('.', '').replace('(', '').replace(')', '') for col in df.columns]
    
    # Determine operation
    operation = "INSERT"
    if "Update" in upload_option:
        operation = "UPDATE"
    elif "Remove" in upload_option or "Delete" in upload_option:
        operation = "DELETE"
    
    # Check if operation is supported
    if operation not in config.get("supported_operations", ["INSERT", "DELETE"]):
        error = {
            "row": 0,
            "field": "operation",
            "message": f"Operation {operation} not supported for {entity_label}",
            "error_code": "UNSUPPORTED_OPERATION"
        }
        return [], [error]
    
    validated_data = []
    all_errors = []
    
    for idx, row in df.iterrows():
        row_num = idx + 2  # Excel row number (1-indexed + header)
        row_errors = []
        
        # For segment x object (special case - different structure)
        if entity_key == "segment x object":
            object_id = row.get("object_id", None)
            object_type = row.get("object_type", None)
            target_segment = row.get("target_segment", None)
            
            if pd.isna(object_id) or not object_id:
                row_errors.append({
                    "row": row_num,
                    "field": "object_id",
                    "message": "Object ID is required",
                    "error_code": "REQUIRED_FIELD"
                })
            
            if pd.isna(object_type) or not object_type:
                row_errors.append({
                    "row": row_num,
                    "field": "object_type",
                    "message": "Object Type is required",
                    "error_code": "REQUIRED_FIELD"
                })
            else:
                # Validate object type
                type_errors = validate_relation_type(
                    object_type, 
                    config["relation_type_table"], 
                    config["relation_type_field"], 
                    row_num
                )
                row_errors.extend(type_errors)
            
            if pd.isna(target_segment) or not target_segment:
                row_errors.append({
                    "row": row_num,
                    "field": "target_segment",
                    "message": "Target Segment is required",
                    "error_code": "REQUIRED_FIELD"
                })
            
            if not row_errors:
                validated_data.append({
                    "Object_ID": int(object_id),
                    "Object_Type": str(object_type).strip(),
                    "Target_Segment": str(target_segment).strip(),
                    "operation": operation,
                    "rowNumber": row_num
                })
        
        # Standard two-entity relationships
        elif "entity_a" in config and "entity_b" in config:
            entity_a_config = config["entity_a"]
            entity_b_config = config["entity_b"]
            
            # Match columns to entities by name, not position
            # Build a mapping of entity type to column name
            entity_a_name = entity_a_config["name"].lower().replace(' ', '_')
            entity_b_name = entity_b_config["name"].lower().replace(' ', '_')
            
            # Find the column that matches entity A
            a_name_col = None
            a_ref_col = None
            for col_key, col_display in config["columns"].items():
                col_lower = col_key.lower()
                # Check if this column belongs to entity A
                if entity_a_name in col_lower or entity_a_config["table"] in col_lower:
                    a_name_col = col_display.lower().replace(' ', '_')
                    break
            
            # Find the column that matches entity B
            b_name_col = None
            b_ref_col = None
            for col_key, col_display in config["columns"].items():
                col_lower = col_key.lower()
                # Check if this column belongs to entity B
                if entity_b_name in col_lower or entity_b_config["table"] in col_lower:
                    b_name_col = col_display.lower().replace(' ', '_')
                    break
            
            # Fallback: try direct entity name + "_name" pattern
            if not a_name_col:
                a_name_col = entity_a_name + "_name"
            if not b_name_col:
                b_name_col = entity_b_name + "_name"
            
            a_name_val = row.get(a_name_col)
            b_name_val = row.get(b_name_col)
            # For Glossary X Glossary (catitemcategoryxcatitemcategory), accept hierarchy column names as fallback
            if entity_key == "catitemcategoryxcatitemcategory":
                if (a_name_val is None or (pd.notna(a_name_val) and str(a_name_val).strip() == "")):
                    a_name_val = row.get("parent_name")
                if (b_name_val is None or (pd.notna(b_name_val) and str(b_name_val).strip() == "")):
                    b_name_val = row.get("child_name")
            
            # Try to get reference values if available
            if entity_a_config.get("ref_field"):
                a_ref_col = entity_a_config["ref_field"].lower().replace(' ', '_')
                a_ref_val = row.get(a_ref_col)
            else:
                a_ref_val = None
                
            if entity_b_config.get("ref_field"):
                b_ref_col = entity_b_config["ref_field"].lower().replace(' ', '_')
                b_ref_val = row.get(b_ref_col)
            else:
                b_ref_val = None
                
            # Resolve entity A
            id_a, errors_a = resolve_entity_by_name_and_ref(
                entity_a_config["table"], 
                entity_a_config["name_field"],
                entity_a_config.get("ref_field"),
                a_name_val,
                a_ref_val,
                entity_a_config.get("parent_field"),
                None,
                row_num
            )
            row_errors.extend(errors_a)
            
            # Resolve entity B
            id_b, errors_b = resolve_entity_by_name_and_ref(
                entity_b_config["table"], 
                entity_b_config["name_field"],
                entity_b_config.get("ref_field"),
                b_name_val,
                b_ref_val,
                entity_b_config.get("parent_field"),
                None,
                row_num
            )
            row_errors.extend(errors_b)
            
            if id_a and id_b and not row_errors:
                # Store mapped IDs for BulkDataProcessorService
                # Use db_column names from config for easier mapping
                result_item = {
                    entity_a_config["db_column"]: id_a,
                    entity_b_config["db_column"]: id_b,
                    "operation": operation,
                    "row_number": row_num
                }
                
                # Check for existence if needed
                exists = check_relationship_exists(
                    config["relationship_table"], 
                    id_a, 
                    id_b, 
                    entity_a_config["db_column"],
                    entity_b_config["db_column"],
                    row_num
                )
                
                if operation == "INSERT" and exists:
                    row_errors.append({
                        "row": row_num,
                        "field": "relationship",
                        "message": f"Relationship already exists between {a_name_val} and {b_name_val}",
                        "error_code": "DUPLICATE_ENTRY"
                    })
                elif operation == "DELETE" and not exists:
                    row_errors.append({
                        "row": row_num,
                        "field": "relationship",
                        "message": f"Relationship does not exist between {a_name_val} and {b_name_val}",
                        "error_code": "NOT_FOUND"
                    })
                else:
                    validated_data.append(result_item)
        
        if row_errors:
            all_errors.extend(row_errors)
    
    return validated_data, all_errors


def apply_column_mappings(df: pd.DataFrame, column_mappings: Optional[Dict[str, str]]) -> pd.DataFrame:
    """Apply column mappings from frontend"""
    if not column_mappings:
        return df
    
    # Reverse mapping: Excel column name -> internal field name
    rename_dict = {v: k for k, v in column_mappings.items() if v in df.columns}
    
    if rename_dict:
        df = df.rename(columns=rename_dict)
        logger.info(f"Applied column mappings: {rename_dict}")
    
    return df


def get_sheet_name(upload_option: str, entity_label: str) -> str:
    """Return expected sheet name for relationship entity"""
    if "Update" in upload_option:
        operation = "Update"
    elif "Remove" in upload_option or "Delete" in upload_option:
        operation = "Delete"
    else:
        operation = "Create"
    
    return f"{operation} {entity_label}"

