"""
Role Assignment Bulk Upload Processor
Handles validation and resolution for all role assignment types
"""

from typing import List, Dict, Any, Tuple, Optional
import pandas as pd
import pymysql
import os
import logging
import sys

logger = logging.getLogger(__name__)

# Import dataset resolution helpers from attribute_bulk_processor
sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'objects'))
try:
    from attribute_bulk_processor import (
        get_dataset_id_by_ref,
        get_dataset_id_by_name,
        get_dataset_id_by_system_short_name
    )
except ImportError:
    logger.warning("Could not import dataset resolution helpers from attribute_bulk_processor")
    get_dataset_id_by_ref = None
    get_dataset_id_by_name = None
    get_dataset_id_by_system_short_name = None

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


def _pk_column(config: Dict) -> str:
    """Primary key column name for object_table (MySQL case varies by table)."""
    return config.get("id_column", "id")


# Configuration mapping for role entities.
# Column values (e.g. ref, name, parent_name) must match normalized Excel headers:
# lowercase, spaces to underscores, dots removed (e.g. "Ref." -> ref, "Project Ref." -> project_ref,
# "Parent Name" -> parent_name, "Project Parent Name" -> project_parent_name).
ROLE_ENTITY_CONFIG = {
    "business area role": {
        "module_name": "Business Area",
        "object_table": "business_area",
        "id_column": "ID",
        "name_field": "PrimaryName",
        "parent_field": "Parent_ID",
        "columns": {
            "name": "business_area_name",
            "parent_name": "business_area_parent_name"
        },
        "id_field": "BusinessArea_ID",
        "linking_table": "businessarea_x_objectxpeople",
        "linking_entity_col": "BusinessAreaID",
        "linking_oxp_col": "Object_x_ipid"
    },
    "capability role": {
        "module_name": "Capability",
        "object_table": "capability",
        "id_column": "ID",
        "name_field": "PrimaryName",
        "ref_field": "RefNumber",
        "parent_field": "Parent_ID",
        "columns": {
            "ref": "capability_ref",
            "name": "capability_name",
            "parent_name": "parent_capability_name"
        },
        "id_field": "Capability_ID",
        "linking_table": "capability_x_objectxpeople",
        "linking_entity_col": "CapabilityID",
        "linking_oxp_col": "Object_x_ipid"
    },
    "client role": {
        "module_name": "Client",
        "object_table": "client",
        "id_column": "ID",
        "name_field": "PrimaryName",
        "parent_field": "Parent_ID",
        "columns": {
            "name": "client_name",
            "parent_name": "client_parent_name"
        },
        "id_field": "Client_ID",
        "linking_table": "client_x_objectxpeople",
        "linking_entity_col": "ClientID",
        "linking_oxp_col": "Object_x_ipid"
    },
    "committee role": {
        "module_name": "Committee",
        "object_table": "committee",
        "id_column": "ID",
        "name_field": "PrimaryName",
        "ref_field": "RefNumber",
        "parent_field": "Parent_ID",
        "columns": {
            "ref": "committee_ref",
            "name": "committee_name",
            "parent_name": "committee_parent_name"
        },
        "id_field": "Committee_ID",
        "linking_table": "committee_x_objectxpeople",
        "linking_entity_col": "Committee_ID",
        "linking_oxp_col": "Object_x_ipid"
    },
    "attribute role": {
        "module_name": "Attribute",
        "object_table": "attribute",
        "id_column": "ID",
        "name_field": "PrimaryName",
        "ref_field": "RefNumber",
        "columns": {
            "ref": "ref",
            "name": "name",
            "parent_name": "parent_name"
        },
        "id_field": "Attribute_ID",
        "linking_table": "attribute_x_objectxpeople",
        "linking_entity_col": "AttributeID",
        "linking_oxp_col": "Object_x_ipid"
    },
    "data quality role": {
        "module_name": "Data Quality",
        "object_table": "data_quality",
        "name_field": "primaryname",
        "ref_field": "ref",
        "columns": {
            "ref": "ref",
            "name": "rule_name"
        }
    },
    "data set role": {
        "module_name": "Data Sets",
        "object_table": "dataset",
        "id_column": "ID",
        "name_field": "PrimaryName",
        "ref_field": "RefNumber",
        "columns": {
            "ref": "ref",
            "name": "name",
            "system_short_name": "system_short_name"
        },
        "id_field": "Dataset_ID",
        "linking_table": "dataset_x_objectxpeople",
        "linking_entity_col": "Dataset_ID",
        "linking_oxp_col": "Object_x_ipid"
    },
    "glossary role": {
        "module_name": "Glossary",
        "object_table": "glossary",
        "id_column": "ID",
        "name_field": "Name",
        "ref_field": "Ref_Number",
        "parent_field": "Parent_ID",
        "columns": {
            "ref": "ref",
            "name": "name",
            "parent_name": "parent_name"
        },
        "id_field": "Glossary_ID",
        "linking_table": "glossary_x_objectxpeople",
        "linking_entity_col": "GlossaryID",
        "linking_oxp_col": "Object_x_ipid"
    },
    "interface role": {
        "module_name": "Interface",
        "object_table": "interface",
        "id_column": "id",
        "name_field": "Name",
        "ref_field": "Ref_number",
        "columns": {
            "ref": "interface_ref",
            "name": "interface_name",
            "source_system": "interface_source_system_short_name",
            "target_system": "interface_target_system_short_name"
        },
        "id_field": "Interface_ID",
        "linking_table": "interface_x_objectxpeople",
        "linking_entity_col": "InterfaceID",
        "linking_oxp_col": "Object_x_ipid"
    },
    "legal entity role": {
        "module_name": "Legal Entity",
        "object_table": "legal",
        "id_column": "ID",
        "name_field": "ShortName",
        "parent_field": "Parent_ID",
        "columns": {
            "name": "legal_entity_name",
            "parent_name": "legal_entity_parent_name"
        },
        "id_field": "Legal_ID",
        "linking_table": "legal_x_objectxpeople",
        "linking_entity_col": "Legal_ID",
        "linking_oxp_col": "Object_X_IP"
    },
    "policy role": {
        "module_name": "Policy",
        "object_table": "policy",
        "id_column": "ID",
        "name_field": "PrimaryName",
        "ref_field": "refNumber",
        "parent_field": "ParentID",
        "columns": {
            "ref": "ref",
            "name": "name",
            "parent_name": "parent_name"
        },
        "id_field": "Policy_ID",
        "linking_table": "policy_x_objectxpeople",
        "linking_entity_col": "Policy_ID",
        "linking_oxp_col": "Object_X_IP"
    },
    "process role": {
        "module_name": "Process",
        "object_table": "process",
        "id_column": "id",
        "name_field": "primaryname",
        "ref_field": "refnumber",
        "parent_field": "parentid",
        "columns": {
            "ref": "ref",
            "name": "name",
            "parent_name": "parent_name"
        },
        "id_field": "Process_ID",
        "linking_table": "process_x_objectxpeople",
        "linking_entity_col": "process_id",
        "linking_oxp_col": "object_x_ip"
    },
    "product role": {
        "module_name": "Product",
        "object_table": "product",
        "id_column": "id",
        "name_field": "primaryname",
        "ref_field": "refnumber",
        "parent_field": "parent_id",
        "columns": {
            "name": "product_name",
            "parent_name": "product_parent_name"
        },
        "id_field": "Product_ID",
        "linking_table": "product_x_objectxpeople",
        "linking_entity_col": "product_id",
        "linking_oxp_col": "object_x_ip"
    },
    "project role": {
        "module_name": "Project",
        "object_table": "project",
        "id_column": "id",
        "name_field": "primaryname",
        "ref_field": "refnumber",
        "parent_field": "parentid",
        "columns": {
            "ref": "project_ref",
            "name": "project_name",
            "parent_name": "project_parent_name"
        },
        "id_field": "Project_ID",
        "linking_table": "project_x_objectxpeople",
        "linking_entity_col": "project_id",
        "linking_oxp_col": "object_x_ip"
    },
    "regulation role": {
        "module_name": "Regulation",
        "object_table": "regulation",
        "id_column": "ID",
        "name_field": "primaryName",
        "ref_field": "RefNumber",
        "parent_field": "Parent_ID",
        "columns": {
            "ref": "regulation_ref",
            "name": "regulation_name",
            "parent_name": "parent_regulation_name"
        },
        "id_field": "Regulation_ID",
        "linking_table": "regulation_x_objectxpeople",
        "linking_entity_col": "RegulationID",
        "linking_oxp_col": "Object_x_ipid"
    },
    "system role": {
        "module_name": "System",
        "object_table": "system",
        "id_column": "id",
        "name_field": "Name",
        "columns": {
            "name": "short_name"
        },
        "id_field": "System_ID",
        "linking_table": "system_x_objectxpeople",
        "linking_entity_col": "SystemID",
        "linking_oxp_col": "Object_x_ipid"
    }
}


def get_module_id(module_name: str) -> Optional[int]:
    """Get module ID from database by module primary name"""
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM module WHERE LOWER(primaryname) = LOWER(%s)", (module_name,))
                result = cur.fetchone()
                if result:
                    return result[0]
    except Exception as e:
        logger.error(f"Error getting module ID for {module_name}: {e}")
    return None


def _row_get(row: pd.Series, *candidate_keys: str):
    """
    Return the first non-missing value from row for the given candidate column names.
    Supports both entity-specific and generic headers (e.g. capability_ref then ref).
    Missing = None, pd.isna(), or empty string after strip.
    """
    for key in candidate_keys:
        if not key:
            continue
        val = row.get(key, None)
        if val is None or (pd.isna(val)):
            continue
        s = str(val).strip()
        if s:
            return s
    return None


def resolve_dataset(config: Dict, row: pd.Series, row_num: int) -> Tuple[Optional[int], List[Dict]]:
    """
    Resolve dataset ID by Ref, Name, or System Short Name
    Validates that when multiple identifiers are provided, they refer to the same dataset
    """
    errors = []
    columns = config["columns"]
    
    # Get values from row
    ref_value = row.get(columns.get("ref", ""), None) if "ref" in columns else None
    name_value = row.get(columns.get("name", ""), None) if "name" in columns else None
    system_short_name = row.get(columns.get("system_short_name", ""), None) if "system_short_name" in columns else None
    
    # Clean up values
    ref_value = str(ref_value).strip() if pd.notna(ref_value) and ref_value else None
    name_value = str(name_value).strip() if pd.notna(name_value) and name_value else None
    system_short_name = str(system_short_name).strip() if pd.notna(system_short_name) and system_short_name else None
    
    # Check if at least one identifier is provided
    if not ref_value and not name_value and not system_short_name:
        errors.append({
            "row": row_num,
            "field": "dataset",
            "message": "Either Ref, Name, or System Short Name must be provided",
            "error_code": "REQUIRED_FIELD"
        })
        return None, errors

    # Require Ref or Name; system name alone is not sufficient (do not resolve by system only)
    if not ref_value and not name_value and system_short_name:
        errors.append({
            "row": row_num,
            "field": "dataset",
            "message": "You must provide either Data Set Ref. or Data Set Name. System name alone is not sufficient.",
            "error_code": "REQUIRED_FIELD"
        })
        return None, errors

    # Check if helper functions are available
    if not get_dataset_id_by_ref or not get_dataset_id_by_name or not get_dataset_id_by_system_short_name:
        errors.append({
            "row": row_num,
            "field": "dataset",
            "message": "Dataset resolution helpers not available",
            "error_code": "SYSTEM_ERROR"
        })
        return None, errors
    
    dataset_id = None
    resolved_by = None
    
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                # Priority 1: Resolve by Ref if provided
                if ref_value:
                    dataset_id = get_dataset_id_by_ref(ref_value)
                    if dataset_id:
                        resolved_by = "ref"
                    else:
                        errors.append({
                            "row": row_num,
                            "field": columns.get("ref", "ref"),
                            "message": f"Dataset with reference '{ref_value}' not found",
                            "error_code": "NOT_FOUND"
                        })
                        return None, errors
                
                # Priority 2: Resolve by Name if Ref not provided or failed
                elif name_value:
                    dataset_id = get_dataset_id_by_name(name_value)
                    if dataset_id:
                        resolved_by = "name"
                    else:
                        errors.append({
                            "row": row_num,
                            "field": columns.get("name", "name"),
                            "message": f"Dataset with name '{name_value}' not found",
                            "error_code": "NOT_FOUND"
                        })
                        return None, errors
                
                # Priority 3: Resolve by System Short Name if neither Ref nor Name provided
                elif system_short_name:
                    dataset_id = get_dataset_id_by_system_short_name(system_short_name)
                    if dataset_id:
                        resolved_by = "system_short_name"
                    else:
                        errors.append({
                            "row": row_num,
                            "field": columns.get("system_short_name", "system_short_name"),
                            "message": f"Dataset not found for system with short name '{system_short_name}'",
                            "error_code": "NOT_FOUND"
                        })
                        return None, errors
                
                # If we have a dataset_id, validate other provided identifiers match
                if dataset_id:
                    # Get dataset details for validation
                    cur.execute("SELECT RefNumber, PrimaryName, MasterSource FROM dataset WHERE id = %s", (dataset_id,))
                    dataset_row = cur.fetchone()
                    
                    if not dataset_row:
                        errors.append({
                            "row": row_num,
                            "field": "dataset",
                            "message": f"Dataset with ID {dataset_id} not found in database",
                            "error_code": "DATABASE_ERROR"
                        })
                        return None, errors
                    
                    db_ref, db_name, db_system_id = dataset_row
                    
                    # Validate Name if provided and we resolved by Ref
                    if ref_value and name_value and resolved_by == "ref":
                        if db_name != name_value:
                            errors.append({
                                "row": row_num,
                                "field": columns.get("name", "name"),
                                "message": f"Incompatible Ref and Dataset Name (they don't refer to the same thing)",
                                "error_code": "INCOMPATIBLE_IDENTIFIERS"
                            })
                            return None, errors
                    
                    # Validate Ref if provided and we resolved by Name
                    if name_value and ref_value and resolved_by == "name":
                        if db_ref != ref_value:
                            errors.append({
                                "row": row_num,
                                "field": columns.get("ref", "ref"),
                                "message": f"Incompatible Ref and Dataset Name (they don't refer to the same thing)",
                                "error_code": "INCOMPATIBLE_IDENTIFIERS"
                            })
                            return None, errors
                    
                    # Validate System Short Name if provided
                    if system_short_name:
                        # Get system name from system_id
                        if db_system_id:
                            cur.execute("SELECT Name FROM system WHERE id = %s", (db_system_id,))
                            system_row = cur.fetchone()
                            if system_row:
                                db_system_name = system_row[0]
                                if db_system_name != system_short_name:
                                    # If we resolved by Ref, error is "Incompatible DS and system"
                                    if resolved_by == "ref":
                                        errors.append({
                                            "row": row_num,
                                            "field": columns.get("system_short_name", "system_short_name"),
                                            "message": f"Incompatible DS and system",
                                            "error_code": "INCOMPATIBLE_IDENTIFIERS"
                                        })
                                    else:
                                        errors.append({
                                            "row": row_num,
                                            "field": columns.get("system_short_name", "system_short_name"),
                                            "message": f"Dataset name '{name_value if name_value else ref_value}' does not belong to system '{system_short_name}'",
                                            "error_code": "INCOMPATIBLE_IDENTIFIERS"
                                        })
                                    return None, errors
                    
                    # Validate Name if provided and we resolved by System Short Name
                    if system_short_name and name_value and resolved_by == "system_short_name":
                        if db_name != name_value:
                            errors.append({
                                "row": row_num,
                                "field": columns.get("name", "name"),
                                "message": f"Dataset name '{name_value}' does not match the dataset for system '{system_short_name}'",
                                "error_code": "INCOMPATIBLE_IDENTIFIERS"
                            })
                            return None, errors
                    
                    return dataset_id, errors
                
    except Exception as e:
        logger.error(f"Error resolving dataset: {e}")
        errors.append({
            "row": row_num,
            "field": "dataset",
            "message": f"Database error: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return None, errors


def validate_parent_child_relationship(object_id: int, parent_name: str, config: Dict, row_num: int) -> List[Dict]:
    """
    Validate that the resolved object is actually a child of the specified parent.
    
    Args:
        object_id: The resolved object ID
        parent_name: The parent name provided by the user
        config: Configuration dictionary with entity details
        row_num: Row number for error reporting
    
    Returns:
        List of errors (empty if validation passes)
    """
    errors = []
    
    if not parent_name or not parent_name.strip():
        return errors  # No parent specified, validation passes
    
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                # Get the object's actual parent ID
                parent_field = config.get("parent_field", "Parent_ID")
                object_table = config["object_table"]
                name_field = config.get("name_field", "PrimaryName")
                pk = _pk_column(config)
                
                cur.execute(f"SELECT {parent_field} FROM {object_table} WHERE `{pk}` = %s", (object_id,))
                parent_result = cur.fetchone()
                
                if not parent_result:
                    # Object not found (shouldn't happen, but handle gracefully)
                    entity_type = config.get("module_name", "Object")
                    errors.append({
                        "row": row_num,
                        "field": "object",
                        "message": f"{entity_type} with ID {object_id} not found",
                        "error_code": "NOT_FOUND"
                    })
                    return errors
                
                actual_parent_id = parent_result[0]
                
                # Resolve the provided parent name to an ID
                cur.execute(f"SELECT `{pk}` FROM {object_table} WHERE {name_field} = %s", (parent_name.strip(),))
                provided_parent_result = cur.fetchone()
                
                if not provided_parent_result:
                    # Provided parent doesn't exist
                    entity_type = config.get("module_name", "Object")
                    errors.append({
                        "row": row_num,
                        "field": config["columns"].get("parent_name", "parent_name"),
                        "message": f"Incompatible parent name: parent {entity_type.lower()} '{parent_name}' not found. Please verify the parent name is correct.",
                        "error_code": "PARENT_NOT_FOUND"
                    })
                    return errors
                
                provided_parent_id = provided_parent_result[0]
                
                # Compare the actual parent ID with the provided parent ID
                if actual_parent_id is None:
                    # Object has no parent but parent was specified
                    entity_type = config.get("module_name", "Object")
                    # Get object name for better error message
                    cur.execute(f"SELECT {name_field} FROM {object_table} WHERE `{pk}` = %s", (object_id,))
                    object_name_result = cur.fetchone()
                    object_name = object_name_result[0] if object_name_result else f"ID {object_id}"
                    
                    errors.append({
                        "row": row_num,
                        "field": config["columns"].get("parent_name", "parent_name"),
                        "message": f"Incompatible parent name: {entity_type} '{object_name}' does not have a parent, but a parent '{parent_name}' was specified.",
                        "error_code": "PARENT_MISMATCH"
                    })
                    return errors
                
                if actual_parent_id != provided_parent_id:
                    # Parent mismatch - get actual parent name for better error message
                    cur.execute(f"SELECT {name_field} FROM {object_table} WHERE `{pk}` = %s", (actual_parent_id,))
                    actual_parent_result = cur.fetchone()
                    actual_parent_name = actual_parent_result[0] if actual_parent_result else f"ID {actual_parent_id}"
                    
                    # Get object name for better error message
                    cur.execute(f"SELECT {name_field} FROM {object_table} WHERE `{pk}` = %s", (object_id,))
                    object_name_result = cur.fetchone()
                    object_name = object_name_result[0] if object_name_result else f"ID {object_id}"
                    
                    entity_type = config.get("module_name", "Object")
                    errors.append({
                        "row": row_num,
                        "field": config["columns"].get("parent_name", "parent_name"),
                        "message": f"Incompatible parent name: {entity_type} '{object_name}' found, but its parent does not match the provided parent '{parent_name}'. The actual parent is '{actual_parent_name}'.",
                        "error_code": "PARENT_MISMATCH"
                    })
                    return errors
                
                # Validation passed - object is a child of the specified parent
                return errors
                
    except Exception as e:
        logger.error(f"Error validating parent-child relationship: {e}")
        entity_type = config.get("module_name", "Object")
        errors.append({
            "row": row_num,
            "field": config["columns"].get("parent_name", "parent_name"),
            "message": f"Database error while validating parent-child relationship: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return errors


def resolve_object(config: Dict, row: pd.Series, row_num: int) -> Tuple[Optional[int], List[Dict]]:
    """Resolve object ID based on name/ref and optional parent, or validate direct ID if provided"""
    errors = []
    columns = config["columns"]
    
    # Check if direct ID is provided (e.g., Project_ID, Legal_ID, etc.)
    id_field = config.get("id_field", None)
    if id_field:
        # Normalize the ID field name to match column normalization (lowercase, underscores, no dots)
        id_field_normalized = id_field.lower().replace(' ', '_').replace('.', '')
        # Try entity-specific column then generic "id" so both template and generic headers work
        direct_id_value = _row_get(row, id_field_normalized, "id")
        
        if direct_id_value:
            try:
                direct_id = int(direct_id_value)
                # Validate that the ID exists in the database
                try:
                    with get_db_connection() as conn:
                        with conn.cursor() as cur:
                            # Check if the ID exists in the object table
                            pk = _pk_column(config)
                            sql = f"SELECT `{pk}` FROM {config['object_table']} WHERE `{pk}` = %s"
                            cur.execute(sql, (direct_id,))
                            result = cur.fetchone()
                            
                            if result:
                                # ID exists, validate parent if provided (try entity-specific then generic column)
                                parent_value = _row_get(row, columns.get("parent_name", ""), "parent_name")
                                
                                if parent_value and "parent_field" in config:
                                    parent_validation_errors = validate_parent_child_relationship(
                                        direct_id, parent_value, config, row_num
                                    )
                                    if parent_validation_errors:
                                        errors.extend(parent_validation_errors)
                                        return None, errors
                                
                                # Parent validation passed (or no parent provided), return the ID
                                return direct_id, errors
                            else:
                                # ID doesn't exist
                                entity_type = config.get("module_name", "Object")
                                errors.append({
                                    "row": row_num,
                                    "field": id_field,
                                    "message": f"{entity_type} not found: No {entity_type.lower()} found with ID {direct_id}. Please verify the ID is correct and the {entity_type.lower()} exists in the system.",
                                    "error_code": "NOT_FOUND"
                                })
                                return None, errors
                except Exception as db_error:
                    logger.error(f"Error validating direct ID {direct_id} for {config['object_table']}: {db_error}")
                    entity_type = config.get("module_name", "Object")
                    errors.append({
                        "row": row_num,
                        "field": id_field,
                        "message": f"Database error while validating {entity_type} ID {direct_id}: {str(db_error)}",
                        "error_code": "DATABASE_ERROR"
                    })
                    return None, errors
            except (ValueError, TypeError):
                # Invalid ID format
                entity_type = config.get("module_name", "Object")
                errors.append({
                    "row": row_num,
                    "field": id_field,
                    "message": f"Invalid {entity_type} ID format: '{direct_id_value}'. The ID must be a valid integer.",
                    "error_code": "INVALID_FORMAT"
                })
                return None, errors
    
    # Get values from row for name/ref resolution (entity-specific, generic, and object_name/object_ref for template headers)
    ref_value = _row_get(row, columns.get("ref", ""), "ref", "object_ref")
    name_value = _row_get(row, columns.get("name", ""), "name", "object_name")
    parent_value = _row_get(row, columns.get("parent_name", ""), "parent_name")
    
    # Special handling for Interface (source and target systems are optional)
    if "source_system" in columns:
        source_system = row.get(columns["source_system"], None)
        target_system = row.get(columns["target_system"], None)
        source_system = str(source_system).strip() if pd.notna(source_system) and source_system else None
        target_system = str(target_system).strip() if pd.notna(target_system) and target_system else None
        
        return resolve_interface(config, ref_value, name_value, source_system, target_system, row_num)
    
    # Special handling for Dataset (supports Ref, Name, and System Short Name with validation)
    if "system_short_name" in columns and config.get("module_name") == "Data Sets":
        return resolve_dataset(config, row, row_num)
    
    # Check if at least one identifier is provided (missing object name/ref)
    if not ref_value and not name_value:
        field_name = columns.get("name", columns.get("ref", "object_name"))
        errors.append({
            "row": row_num,
            "field": field_name,
            "message": "Missing Object Name: object name or reference must be provided.",
            "error_code": "REQUIRED_FIELD"
        })
        return None, errors
    
    # Build SQL query
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                pk = _pk_column(config)
                # Migration templates often put the only identifier in "Ref." while tables like business_area,
                # client, legal have no RefNumber — treat that value as the display name.
                if ref_value and "ref_field" not in config and not name_value:
                    name_value = ref_value
                    ref_value = None
                # If both ref and name are provided, validate they refer to the same object
                if ref_value and name_value and "ref_field" in config:
                    # Resolve by ref
                    sql_ref = f"SELECT `{pk}` FROM {config['object_table']} WHERE {config['ref_field']} = %s"
                    cur.execute(sql_ref, (ref_value,))
                    result_by_ref = cur.fetchone()
                    
                    # Resolve by name
                    name_field = config.get("name_field", "primaryname")
                    sql_name = f"SELECT `{pk}` FROM {config['object_table']} WHERE {name_field} = %s"
                    cur.execute(sql_name, (name_value,))
                    results_by_name = cur.fetchall()
                    
                    # Check if ref exists
                    if not result_by_ref:
                        entity_type = config.get("module_name", "Object")
                        errors.append({
                            "row": row_num,
                            "field": columns.get("ref", "ref"),
                            "message": f"{entity_type} not found: No {entity_type.lower()} found with reference '{ref_value}'. Please verify the reference is correct and the {entity_type.lower()} exists in the system.",
                            "error_code": "NOT_FOUND"
                        })
                        return None, errors
                    
                    # Check if name exists
                    if not results_by_name:
                        entity_type = config.get("module_name", "Object")
                        errors.append({
                            "row": row_num,
                            "field": columns.get("name", "name"),
                            "message": f"{entity_type} not found: No {entity_type.lower()} found with name '{name_value}'. Please verify the name is correct and the {entity_type.lower()} exists in the system.",
                            "error_code": "NOT_FOUND"
                        })
                        return None, errors
                    
                    # If multiple objects with same name, need to check if any match the ref
                    resolved_id_by_ref = result_by_ref[0]
                    name_matches_ref = False
                    resolved_id_by_name = None
                    
                    for name_result in results_by_name:
                        if name_result[0] == resolved_id_by_ref:
                            name_matches_ref = True
                            resolved_id_by_name = name_result[0]
                            break
                    
                    if not name_matches_ref:
                        # Name and ref refer to different objects
                        errors.append({
                            "row": row_num,
                            "field": columns.get("ref", "ref"),
                            "message": "Incompatible ref: reference and name do not refer to the same object.",
                            "error_code": "NAME_REF_MISMATCH"
                        })
                        return None, errors
                    
                    # Both refer to the same object, use the resolved ID
                    resolved_id = resolved_id_by_ref
                    
                    # Validate parent if provided
                    if parent_value and "parent_field" in config:
                        parent_validation_errors = validate_parent_child_relationship(
                            resolved_id, parent_value, config, row_num
                        )
                        if parent_validation_errors:
                            errors.extend(parent_validation_errors)
                            return None, errors
                    
                    # Parent validation passed (or no parent provided), return the ID
                    return resolved_id, errors
                
                # Try by ref first if provided (and name is not provided)
                if ref_value and "ref_field" in config:
                    sql = f"SELECT `{pk}` FROM {config['object_table']} WHERE {config['ref_field']} = %s"
                    cur.execute(sql, (ref_value,))
                    result = cur.fetchone()
                    if result:
                        resolved_id = result[0]
                        # Validate parent if provided
                        if parent_value and "parent_field" in config:
                            parent_validation_errors = validate_parent_child_relationship(
                                resolved_id, parent_value, config, row_num
                            )
                            if parent_validation_errors:
                                errors.extend(parent_validation_errors)
                                return None, errors
                        # Parent validation passed (or no parent provided), return the ID
                        return resolved_id, errors
                    else:
                        # Ref doesn't exist and name is not provided, return error
                        if not name_value:
                            entity_type = config.get("module_name", "Object")
                            errors.append({
                                "row": row_num,
                                "field": columns.get("ref", "ref"),
                                "message": f"{entity_type} not found: No {entity_type.lower()} found with reference '{ref_value}'. Please verify the reference is correct and the {entity_type.lower()} exists in the system.",
                                "error_code": "NOT_FOUND"
                            })
                            return None, errors
                        # Ref doesn't exist but name is provided, fall through to name resolution
                
                # Try by name
                if name_value:
                    name_field = config.get("name_field", "primaryname")
                    sql = f"SELECT `{pk}`, {name_field}"
                    if "parent_field" in config and parent_value:
                        sql += f", {config['parent_field']}"
                    sql += f" FROM {config['object_table']} WHERE {name_field} = %s"
                    
                    cur.execute(sql, (name_value,))
                    results = cur.fetchall()
                    
                    if not results:
                        entity_type = config.get("module_name", "Object")
                        errors.append({
                            "row": row_num,
                            "field": columns.get("name", "name"),
                            "message": f"{entity_type} not found: No {entity_type.lower()} found with name '{name_value}'. Please verify the name is correct and the {entity_type.lower()} exists in the system.",
                            "error_code": "NOT_FOUND"
                        })
                        return None, errors
                    
                    if len(results) == 1:
                        # Single result found - validate parent if provided using helper function
                        resolved_id = results[0][0]
                        if parent_value and "parent_field" in config:
                            parent_validation_errors = validate_parent_child_relationship(
                                resolved_id, parent_value, config, row_num
                            )
                            if parent_validation_errors:
                                errors.extend(parent_validation_errors)
                                return None, errors
                        
                        # Parent validation passed (or no parent provided), return the result
                        return resolved_id, errors
                    
                    # Multiple results - need parent to disambiguate
                    if parent_value and "parent_field" in config:
                        # Get parent ID first
                        parent_id = None
                        cur.execute(f"SELECT `{pk}` FROM {config['object_table']} WHERE {name_field} = %s", (parent_value,))
                        parent_result = cur.fetchone()
                        if parent_result:
                            parent_id = parent_result[0]
                        
                        if parent_id:
                            # Find child with matching parent
                            for result in results:
                                if len(result) > 2 and result[2] == parent_id:
                                    resolved_id = result[0]
                                    # Validate that the resolved object is actually a child of the specified parent
                                    # (This is a double-check, but ensures consistency)
                                    parent_validation_errors = validate_parent_child_relationship(
                                        resolved_id, parent_value, config, row_num
                                    )
                                    if parent_validation_errors:
                                        errors.extend(parent_validation_errors)
                                        return None, errors
                                    return resolved_id, errors
                        
                        # Parent provided but no matching child found
                        errors.append({
                            "row": row_num,
                            "field": columns.get("name", "name"),
                            "message": f"Multiple objects found with name '{name_value}', but none have the specified parent '{parent_value}'. Please verify the parent name is correct.",
                            "error_code": "PARENT_MISMATCH"
                        })
                    else:
                        errors.append({
                            "row": row_num,
                            "field": columns.get("name", "name"),
                            "message": f"Multiple objects found with name '{name_value}', please provide parent name to disambiguate",
                            "error_code": "AMBIGUOUS_MATCH"
                        })
                    return None, errors
                
    except Exception as e:
        logger.error(f"Error resolving object: {e}")
        errors.append({
            "row": row_num,
            "field": "object",
            "message": f"Database error: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return None, errors


def resolve_interface(config: Dict, ref_value: Optional[str], name_value: Optional[str], 
                      source_system: Optional[str], target_system: Optional[str], row_num: int) -> Tuple[Optional[int], List[Dict]]:
    """Special resolution for Interface using optional source and target systems"""
    errors = []
    
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                source_id = None
                target_id = None
                
                # Get source system ID if provided
                if source_system:
                    cur.execute("SELECT id FROM system WHERE LOWER(Name) = LOWER(%s) LIMIT 1", (str(source_system).strip(),))
                    source_result = cur.fetchone()
                    if not source_result:
                        errors.append({
                            "row": row_num,
                            "field": "source_system",
                            "message": f"Source system '{source_system}' not found",
                            "error_code": "NOT_FOUND"
                        })
                        return None, errors
                    source_id = source_result[0]
                
                # Get target system ID if provided
                if target_system:
                    cur.execute("SELECT id FROM system WHERE LOWER(Name) = LOWER(%s) LIMIT 1", (str(target_system).strip(),))
                    target_result = cur.fetchone()
                    if not target_result:
                        errors.append({
                            "row": row_num,
                            "field": "target_system",
                            "message": f"Target system '{target_system}' not found",
                            "error_code": "NOT_FOUND"
                        })
                        return None, errors
                    target_id = target_result[0]
                
                # Build WHERE conditions based on what's provided
                conditions = []
                params = []
                
                if ref_value:
                    conditions.append("`Ref_number` = %s")
                    params.append(ref_value)
                elif name_value:
                    conditions.append("LOWER(`Name`) = LOWER(%s)")
                    params.append(name_value)
                else:
                    errors.append({
                        "row": row_num,
                        "field": "interface",
                        "message": "Either Interface Ref. or Interface Name must be provided",
                        "error_code": "REQUIRED_FIELD"
                    })
                    return None, errors
                
                if source_id is not None:
                    conditions.append("`Source_systemID` = %s")
                    params.append(source_id)
                
                if target_id is not None:
                    conditions.append("`Target_systemID` = %s")
                    params.append(target_id)
                
                # Build and execute query (schema: Ref_number, Name, Source_systemID, Target_systemID)
                sql = f"SELECT `id` FROM `interface` WHERE {' AND '.join(conditions)}"
                cur.execute(sql, tuple(params))
                results = cur.fetchall()
                
                if not results:
                    error_msg = f"Interface not found"
                    if ref_value:
                        error_msg += f" with ref '{ref_value}'"
                    elif name_value:
                        error_msg += f" with name '{name_value}'"
                    if source_system and target_system:
                        error_msg += f" between systems '{source_system}' and '{target_system}'"
                    elif source_system:
                        error_msg += f" with source system '{source_system}'"
                    elif target_system:
                        error_msg += f" with target system '{target_system}'"
                    
                    errors.append({
                        "row": row_num,
                        "field": "interface",
                        "message": error_msg,
                        "error_code": "NOT_FOUND"
                    })
                    return None, errors
                
                if len(results) > 1:
                    error_msg = "Multiple interfaces found"
                    if not source_system or not target_system:
                        error_msg += ". Please provide both source and target system short names to uniquely identify the interface"
                    else:
                        error_msg += ", please provide reference"
                    
                    errors.append({
                        "row": row_num,
                        "field": "interface",
                        "message": error_msg,
                        "error_code": "AMBIGUOUS_MATCH"
                    })
                    return None, errors
                
                return results[0][0], errors
                
    except Exception as e:
        logger.error(f"Error resolving interface: {e}")
        errors.append({
            "row": row_num,
            "field": "interface",
            "message": f"Database error: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return None, errors


def resolve_person(row: pd.Series, row_num: int) -> Tuple[Optional[int], List[Dict]]:
    """Resolve person ID from email, lan ID, or first+last name"""
    errors = []
    
    # Try entity-specific then generic column names (e.g. user_lan_id then lan_id)
    email = _row_get(row, "user_email", "email")
    lan_id = _row_get(row, "user_lan_id", "lan_id")
    first_name = _row_get(row, "user_first_name", "first_name")
    last_name = _row_get(row, "user_last_name", "last_name")
    
    # Check if at least one identifier is provided
    if not email and not lan_id and not (first_name and last_name):
        errors.append({
            "row": row_num,
            "field": "user",
            "message": "Missing email and first and last name: provide User Email or User First Name + Last Name.",
            "error_code": "REQUIRED_FIELD"
        })
        return None, errors

    # Optional: explicit error when only one of first/last name is provided (no email, no lan_id)
    if not email and not lan_id and (bool(first_name) != bool(last_name)):
        errors.append({
            "row": row_num,
            "field": "user",
            "message": "Missing email and first and last name: provide both User First Name and User Last Name, or User Email.",
            "error_code": "REQUIRED_FIELD"
        })
        return None, errors

    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                # Try by LAN ID first (most specific) - LAN_ID is in people_details table
                if lan_id:
                    cur.execute("""
                        SELECT p.ID 
                        FROM people p 
                        INNER JOIN people_details pd ON p.ip_details = pd.id 
                        WHERE pd.lan_id = %s
                    """, (lan_id,))
                    result = cur.fetchone()
                    if result:
                        return result[0], errors
                
                # Try by email (case-insensitive)
                if email:
                    cur.execute("SELECT ID FROM people WHERE LOWER(Email) = LOWER(%s)", (email,))
                    result = cur.fetchone()
                    if result:
                        return result[0], errors
                
                # Try by first name + last name (case-insensitive)
                if first_name and last_name:
                    cur.execute("SELECT ID FROM people WHERE LOWER(First_Name) = LOWER(%s) AND LOWER(Last_Name) = LOWER(%s)", (first_name, last_name))
                    results = cur.fetchall()
                    
                    if not results:
                        errors.append({
                            "row": row_num,
                            "field": "user",
                            "message": f"Person/User not found: No person found with name '{first_name} {last_name}'. Please verify the name is correct, or provide User Email or User Lan ID for more accurate identification.",
                            "error_code": "NOT_FOUND"
                        })
                        return None, errors
                    
                    if len(results) > 1:
                        errors.append({
                            "row": row_num,
                            "field": "user",
                            "message": f"Multiple people found with name '{first_name} {last_name}'. Please provide User Email to uniquely identify the user.",
                            "error_code": "AMBIGUOUS_MATCH"
                        })
                        return None, errors
                    
                    return results[0][0], errors
                
                # If we get here, no match found
                identifier = lan_id or email or f"{first_name} {last_name}"
                identifier_type = "Lan ID" if lan_id else ("Email" if email else "Name")
                errors.append({
                    "row": row_num,
                    "field": "user",
                    "message": f"Person/User not found: No person found with {identifier_type} '{identifier}'. Please verify the {identifier_type.lower()} is correct and the person exists in the system.",
                    "error_code": "NOT_FOUND"
                })
                
    except Exception as e:
        logger.error(f"Error resolving person: {e}")
        errors.append({
            "row": row_num,
            "field": "user",
            "message": f"Database error: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return None, errors


def resolve_governance_role(module_id: int, role_name: str, row_num: int) -> Tuple[Optional[int], List[Dict]]:
    """Resolve governance role ID from object_role table"""
    errors = []
    
    if not role_name or pd.isna(role_name):
        errors.append({
            "row": row_num,
            "field": "governance_role",
            "message": "Governance Role is required",
            "error_code": "REQUIRED_FIELD"
        })
        return None, errors
    
    role_name = str(role_name).strip()

    # If value is numeric, treat as role ID and enforce module match (prevents wrong facet)
    if role_name.isdigit():
        try:
            role_id_int = int(role_name)
        except ValueError:
            role_id_int = None
        if role_id_int is not None:
            try:
                with get_db_connection() as conn:
                    with conn.cursor() as cur:
                        cur.execute(
                            "SELECT id FROM object_role WHERE id = %s AND module = %s",
                            (role_id_int, module_id),
                        )
                        row = cur.fetchone()
                        if row:
                            return row[0], errors
                        cur.execute("SELECT PrimaryName FROM module WHERE id = %s", (module_id,))
                        mod_row = cur.fetchone()
                        module_info = mod_row[0] if mod_row else "this module"
                        errors.append({
                            "row": row_num,
                            "field": "governance_role",
                            "message": f"Incompatible role: Role ID {role_id_int} is not valid for {module_info}. Use a role ID that belongs to this module.",
                            "error_code": "INCOMPATIBLE_ROLE",
                        })
                        return None, errors
            except Exception as e:
                logger.error(f"Error resolving governance role by id: {e}")
                errors.append({
                    "row": row_num,
                    "field": "governance_role",
                    "message": f"Database error: {str(e)}",
                    "error_code": "DATABASE_ERROR",
                })
                return None, errors

    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                sql = "SELECT id FROM object_role WHERE module = %s AND LOWER(primaryname) = LOWER(%s)"
                cur.execute(sql, (module_id, role_name))
                results = cur.fetchall()
                
                if not results:
                    # Get module name for better error message (incompatible role: not valid for this object type)
                    module_name = None
                    try:
                        cur.execute("SELECT PrimaryName FROM module WHERE id = %s", (module_id,))
                        module_result = cur.fetchone()
                        if module_result:
                            module_name = module_result[0]
                    except Exception:
                        pass
                    
                    module_info = module_name or "this module"
                    errors.append({
                        "row": row_num,
                        "field": "governance_role",
                        "message": f"Incompatible role: The role '{role_name}' is not valid for {module_info}. Please use a role that is assigned to the {module_info} module.",
                        "error_code": "INCOMPATIBLE_ROLE"
                    })
                    return None, errors
                
                if len(results) > 1:
                    errors.append({
                        "row": row_num,
                        "field": "governance_role",
                        "message": f"Multiple governance roles found with name '{role_name}'",
                        "error_code": "AMBIGUOUS_MATCH"
                    })
                    return None, errors
                
                return results[0][0], errors
                
    except Exception as e:
        logger.error(f"Error resolving governance role: {e}")
        errors.append({
            "row": row_num,
            "field": "governance_role",
            "message": f"Database error: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return None, errors


def validate_person_data(row: pd.Series, person_id: int, row_num: int) -> List[Dict]:
    """
    Validate person data matches the resolved person ID
    Checks: Email, First Name, Last Name, LAN ID (if provided)
    """
    errors = []
    
    # Try entity-specific then generic column names (e.g. user_lan_id then lan_id)
    user_email = _row_get(row, "user_email", "email")
    user_first_name = _row_get(row, "user_first_name", "first_name")
    user_last_name = _row_get(row, "user_last_name", "last_name")
    user_lan_id = _row_get(row, "user_lan_id", "lan_id")
    
    # Build query dynamically based on provided fields
    conditions = []
    params = [person_id]
    
    if user_email and not pd.isna(user_email):
        conditions.append("LOWER(Email) = LOWER(%s)")
        params.append(str(user_email).strip())
    
    if user_first_name and not pd.isna(user_first_name):
        conditions.append("LOWER(First_Name) = LOWER(%s)")
        params.append(str(user_first_name).strip())
    
    if user_last_name and not pd.isna(user_last_name):
        conditions.append("LOWER(Last_Name) = LOWER(%s)")
        params.append(str(user_last_name).strip())
    
    if user_lan_id and not pd.isna(user_lan_id):
        conditions.append("LAN_ID = %s")
        params.append(str(user_lan_id).strip())
    
    # If no fields provided to validate, skip validation
    if not conditions:
        return errors
    
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                # Check if person exists with matching data
                # Note: lan_id is in people_details table, need to join
                sql = """
                    SELECT p.Email, p.First_Name, p.Last_Name, pd.lan_id 
                    FROM people p
                    LEFT JOIN people_details pd ON p.ip_details = pd.id
                    WHERE p.ID = %s
                """
                cur.execute(sql, (person_id,))
                result = cur.fetchone()
                
                if not result:
                    errors.append({
                        "row": row_num,
                        "field": "person",
                        "message": f"Person with ID {person_id} not found",
                        "error_code": "NOT_FOUND"
                    })
                    return errors
                
                db_email, db_first_name, db_last_name, db_lan_id = result
                
                # Check each provided field for mismatch (case-insensitive comparison)
                if user_email and not pd.isna(user_email):
                    db_email_display = db_email if db_email is not None else "not set"
                    if (db_email or "").strip().lower() != str(user_email).strip().lower():
                        errors.append({
                            "row": row_num,
                            "field": "user_email",
                            "message": f"Email mismatch: provided '{user_email}' does not match person record '{db_email_display}'",
                            "error_code": "DATA_MISMATCH"
                        })
                
                if user_first_name and not pd.isna(user_first_name):
                    db_first_name_display = db_first_name if db_first_name is not None else "not set"
                    if (db_first_name or "").strip().lower() != str(user_first_name).strip().lower():
                        errors.append({
                            "row": row_num,
                            "field": "user_first_name",
                            "message": f"Incompatible first and last name: provided first name '{user_first_name}' does not match person record '{db_first_name_display}'.",
                            "error_code": "DATA_MISMATCH"
                        })
                
                if user_last_name and not pd.isna(user_last_name):
                    db_last_name_display = db_last_name if db_last_name is not None else "not set"
                    if (db_last_name or "").strip().lower() != str(user_last_name).strip().lower():
                        errors.append({
                            "row": row_num,
                            "field": "user_last_name",
                            "message": f"Incompatible first and last name: provided last name '{user_last_name}' does not match person record '{db_last_name_display}'.",
                            "error_code": "DATA_MISMATCH"
                        })
                
                if user_lan_id and not pd.isna(user_lan_id):
                    # Handle None case for db_lan_id - show user-provided value instead of "None" or "not set"
                    if db_lan_id is not None:
                        db_lan_id_display = db_lan_id
                    else:
                        # When database value is None, show the user-provided value to indicate what was provided
                        db_lan_id_display = str(user_lan_id).strip()
                    if db_lan_id != str(user_lan_id).strip():
                        errors.append({
                            "row": row_num,
                            "field": "user_lan_id",
                            "message": f"LAN ID mismatch: provided '{user_lan_id}' does not match person record '{db_lan_id_display}'",
                            "error_code": "DATA_MISMATCH"
                        })
                
    except Exception as e:
        logger.error(f"Error validating person data: {e}")
        errors.append({
            "row": row_num,
            "field": "person",
            "message": f"Database error validating person data: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return errors


def check_duplicate_assignment(entity_id: int, person_id: int, role_id: int, row_num: int, 
                               linking_table: str, entity_col: str, oxp_col: str, entity_name: str) -> List[Dict]:
    """
    Check if this role assignment already exists
    Queries: object_x_people + entity-specific linking table
    Returns detailed error messages with entity, person, and role names
    """
    errors = []
    
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                # Get entity name for better error messages
                entity_display_name = None
                try:
                    entity_table_map = {
                        "Data Sets": ("dataset", "PrimaryName", "ID"),
                        "System": ("system", "Name", "id"),
                        "Policy": ("policy", "PrimaryName", "ID"),
                        "Process": ("process", "primaryname", "id"),
                        "Project": ("project", "primaryname", "id"),
                        "Product": ("product", "primaryname", "id"),
                        "Legal Entity": ("legal", "ShortName", "ID"),
                        "Client": ("client", "PrimaryName", "ID"),
                        "Committee": ("committee", "PrimaryName", "ID"),
                        "Capability": ("capability", "PrimaryName", "ID"),
                        "Business Area": ("business_area", "PrimaryName", "ID"),
                        "Glossary": ("glossary", "Name", "ID"),
                        "Interface": ("interface", "Name", "id"),
                        "Regulation": ("regulation", "primaryName", "ID")
                    }
                    
                    if entity_name in entity_table_map:
                        table_name, name_field, pk_col = entity_table_map[entity_name]
                        cur.execute(
                            f"SELECT `{name_field}` FROM `{table_name}` WHERE `{pk_col}` = %s",
                            (entity_id,),
                        )
                        result = cur.fetchone()
                        if result:
                            entity_display_name = result[0]
                except Exception as e:
                    logger.warning(f"Could not get entity name: {e}")
                
                # Get person name
                person_display_name = None
                try:
                    cur.execute("SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = %s", (person_id,))
                    result = cur.fetchone()
                    if result:
                        person_display_name = result[0]
                except Exception as e:
                    logger.warning(f"Could not get person name: {e}")
                
                # Get role name
                role_display_name = None
                try:
                    cur.execute("SELECT PrimaryName FROM object_role WHERE id = %s", (role_id,))
                    result = cur.fetchone()
                    if result:
                        role_display_name = result[0]
                except Exception as e:
                    logger.warning(f"Could not get role name: {e}")
                
                # Step 1: Find ALL object_x_people records for this person + role
                # (There may be multiple records, and we need to check if ANY of them are already linked to this entity)
                sql_oxp = "SELECT ID FROM object_x_people WHERE ipid = %s AND RoleID = %s"
                cur.execute(sql_oxp, (person_id, role_id))
                oxp_results = cur.fetchall()
                
                if oxp_results:
                    # Step 2: Check if ANY of the object_x_people records are already linked to this entity
                    sql_link = f"SELECT COUNT(*) FROM {linking_table} WHERE {entity_col} = %s AND {oxp_col} = %s"
                    
                    for oxp_result in oxp_results:
                        object_x_people_id = oxp_result[0]
                        cur.execute(sql_link, (entity_id, object_x_people_id))
                        count = cur.fetchone()[0]
                        
                        if count > 0:
                            # Duplicate found - at least one link already exists
                            person_info = person_display_name or f"Person ID {person_id}"
                            role_info = role_display_name or f"Role ID {role_id}"
                            entity_info = entity_display_name or f"{entity_name} ID {entity_id}"
                            
                            errors.append({
                                "row": row_num,
                                "field": "role_assignment",
                                "message": "Repeated role: this role assignment already exists.",
                                "error_code": "DUPLICATE_ASSIGNMENT"
                            })
                            break  # No need to check further once duplicate is found
                
    except Exception as e:
        logger.error(f"Error checking duplicate assignment: {e}")
        errors.append({
            "row": row_num,
            "field": "role_assignment",
            "message": f"Database error while checking for duplicate role assignment: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return errors


def check_assignment_exists(entity_id: int, person_id: int, role_id: int, row_num: int,
                            linking_table: str, entity_col: str, oxp_col: str, entity_name: str) -> List[Dict]:
    """
    Check if the role assignment exists (for DELETE operations)
    Returns detailed error messages with entity, person, and role names
    """
    errors = []
    
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                # Get entity name for better error messages
                entity_display_name = None
                try:
                    # Try to get entity name - need to determine table from entity_name
                    entity_table_map = {
                        "Data Sets": ("dataset", "PrimaryName", "ID"),
                        "System": ("system", "Name", "id"),
                        "Policy": ("policy", "PrimaryName", "ID"),
                        "Process": ("process", "primaryname", "id"),
                        "Project": ("project", "primaryname", "id"),
                        "Product": ("product", "primaryname", "id"),
                        "Legal Entity": ("legal", "ShortName", "ID"),
                        "Client": ("client", "PrimaryName", "ID"),
                        "Committee": ("committee", "PrimaryName", "ID"),
                        "Capability": ("capability", "PrimaryName", "ID"),
                        "Business Area": ("business_area", "PrimaryName", "ID"),
                        "Glossary": ("glossary", "Name", "ID"),
                        "Interface": ("interface", "Name", "id"),
                        "Regulation": ("regulation", "primaryName", "ID")
                    }
                    
                    if entity_name in entity_table_map:
                        table_name, name_field, pk_col = entity_table_map[entity_name]
                        cur.execute(
                            f"SELECT `{name_field}` FROM `{table_name}` WHERE `{pk_col}` = %s",
                            (entity_id,),
                        )
                        result = cur.fetchone()
                        if result:
                            entity_display_name = result[0]
                except Exception as e:
                    logger.warning(f"Could not get entity name: {e}")
                
                # Get person name
                person_display_name = None
                try:
                    cur.execute("SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = %s", (person_id,))
                    result = cur.fetchone()
                    if result:
                        person_display_name = result[0]
                except Exception as e:
                    logger.warning(f"Could not get person name: {e}")
                
                # Get role name
                role_display_name = None
                try:
                    cur.execute("SELECT PrimaryName FROM object_role WHERE id = %s", (role_id,))
                    result = cur.fetchone()
                    if result:
                        role_display_name = result[0]
                except Exception as e:
                    logger.warning(f"Could not get role name: {e}")
                
                # Find ALL object_x_people records for this person+role combination
                # (There may be multiple records, and we need to check if ANY of them are linked to this entity)
                sql_oxp = "SELECT ID FROM object_x_people WHERE ipid = %s AND RoleID = %s"
                cur.execute(sql_oxp, (person_id, role_id))
                oxp_results = cur.fetchall()
                
                if not oxp_results:
                    # Build detailed error message (incompatible role: no person+role combination)
                    person_info = person_display_name or f"Person ID {person_id}"
                    role_info = role_display_name or f"Role ID {role_id}"
                    entity_info = entity_display_name or f"{entity_name} ID {entity_id}"
                    
                    errors.append({
                        "row": row_num,
                        "field": "role_assignment",
                        "message": f"Incompatible role: No role assignment found for {person_info} with role '{role_info}' for {entity_info}. The person may not have this role assigned, or the role assignment may have already been removed.",
                        "error_code": "INCOMPATIBLE_ROLE"
                    })
                    return errors
                
                # Check if ANY of the object_x_people records are linked to this entity
                sql_link = f"SELECT COUNT(*) FROM {linking_table} WHERE {entity_col} = %s AND {oxp_col} = %s"
                assignment_found = False
                
                for oxp_result in oxp_results:
                    object_x_people_id = oxp_result[0]
                    cur.execute(sql_link, (entity_id, object_x_people_id))
                    count = cur.fetchone()[0]
                    
                    if count > 0:
                        # Assignment exists - at least one link found
                        assignment_found = True
                        break
                
                if not assignment_found:
                    # Build detailed error message (incompatible role: no assignment for this person/role/object)
                    person_info = person_display_name or f"Person ID {person_id}"
                    role_info = role_display_name or f"Role ID {role_id}"
                    entity_info = entity_display_name or f"{entity_name} ID {entity_id}"
                    
                    errors.append({
                        "row": row_num,
                        "field": "role_assignment",
                        "message": f"Incompatible role: No role assignment found for {person_info} with role '{role_info}' for {entity_info}. The person may not have this role on this {entity_name}, or the assignment may have already been removed.",
                        "error_code": "INCOMPATIBLE_ROLE"
                    })
                
    except Exception as e:
        logger.error(f"Error checking assignment existence: {e}")
        errors.append({
            "row": row_num,
            "field": "role_assignment",
            "message": f"Database error while checking if role assignment exists: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return errors


def validate_role_assignment(person_id: int, role_id: int, row_num: int) -> List[Dict]:
    """
    Validate admin-panel role assignment (aligned with DefaultStakeholderUtil.validateStakeholderRoleAssignment).
    If the role has a row in role_assignment, the person must appear in the assigned users list.
    If there is no role_assignment row, anyone may have this role.
    Applies to any role — not only default roles.
    """
    errors = []
    
    try:
        with get_db_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM object_role WHERE id = %s", (role_id,))
                if not cur.fetchone():
                    return errors

                cur.execute("SELECT COUNT(*) as count FROM role_assignment WHERE objectroleid = %s", (role_id,))
                assignment_result = cur.fetchone()
                has_assignment = assignment_result and assignment_result[0] > 0

                if not has_assignment:
                    return errors

                cur.execute("SELECT users FROM role_assignment WHERE objectroleid = %s", (role_id,))
                users_result = cur.fetchone()

                if users_result and users_result[0]:
                    users_json = users_result[0]
                    user_ids = []
                    if users_json:
                        users_str = str(users_json).strip()
                        if users_str.startswith("[") and users_str.endswith("]"):
                            users_str = users_str[1:-1]
                        if users_str:
                            for user_id_str in users_str.split(","):
                                try:
                                    user_ids.append(int(user_id_str.strip()))
                                except ValueError:
                                    pass

                    if person_id not in user_ids:
                        cur.execute("SELECT primaryname FROM object_role WHERE id = %s", (role_id,))
                        role_result = cur.fetchone()
                        role_name = role_result[0] if role_result else "Unknown Role"
                        errors.append({
                            "row": row_num,
                            "field": "governance_role",
                            "message": f"The user is not assigned to this role. Select a user assigned to \"{role_name}\" in the admin panel, or assign them to this role.",
                            "error_code": "ROLE_ASSIGNMENT_MISMATCH"
                        })
                
    except Exception as e:
        logger.error(f"Error validating role assignment: {e}")
        errors.append({
            "row": row_num,
            "field": "governance_role",
            "message": f"Database error while validating role assignment: {str(e)}",
            "error_code": "DATABASE_ERROR"
        })
    
    return errors


def validate_and_resolve_role(df: pd.DataFrame, entity_label: str, upload_option: str, user_id: int) -> Tuple[List[Dict], List[Dict]]:
    """
    Validate and resolve role assignment data
    Returns: (validated_data, errors)
    """
    entity_key = entity_label.lower().strip()
    
    if entity_key not in ROLE_ENTITY_CONFIG:
        error = {
            "row": 0,
            "field": "entity",
            "message": f"Unsupported role entity: {entity_label}",
            "error_code": "UNSUPPORTED_ENTITY"
        }
        return [], [error]
    
    config = ROLE_ENTITY_CONFIG[entity_key]
    
    # Get module ID
    module_id = get_module_id(config["module_name"])
    if not module_id:
        error = {
            "row": 0,
            "field": "module",
            "message": f"Module not found: {config['module_name']}",
            "error_code": "MODULE_NOT_FOUND"
        }
        return [], [error]
    
    # Normalize column names (convert to lowercase with underscores, remove dots)
    df = df.copy()
    df.columns = [col.lower().replace(' ', '_').replace('.', '') for col in df.columns]
    
    # Determine operation
    operation = "INSERT" if "Upload New Items" in upload_option or "Add New Items" in upload_option else "DELETE"
    
    validated_data = []
    all_errors = []
    # Track (object_id, person_id, role_id) within this file to detect repeated role/stakeholder in file
    seen_assignments = set()
    
    for idx, row in df.iterrows():
        row_num = idx + 2  # Excel row number (1-indexed + header)
        row_errors = []
        
        # Resolve object
        object_id, obj_errors = resolve_object(config, row, row_num)
        row_errors.extend(obj_errors)
        
        # Resolve person
        person_id, person_errors = resolve_person(row, row_num)
        row_errors.extend(person_errors)
        
        # Resolve governance role
        governance_role = row.get("governance_role", None)
        role_id, role_errors = resolve_governance_role(module_id, governance_role, row_num)
        row_errors.extend(role_errors)
        
        assignment_key = (object_id, person_id, role_id) if (object_id and person_id and role_id) else None
        
        # Additional validations if basic resolution succeeded
        if not row_errors and object_id and person_id and role_id:
            # In-file duplicate: same (object, person, role) already in this file (repeated role / repeated stakeholder)
            if assignment_key in seen_assignments:
                row_errors.append({
                    "row": row_num,
                    "field": "role_assignment",
                    "message": "Repeated role: same object, person, and role appear in another row.",
                    "error_code": "REPEATED_STAKEHOLDER_IN_FILE"
                })
            else:
                # Validate person data matching (if fields provided)
                person_validation_errors = validate_person_data(row, person_id, row_num)
                row_errors.extend(person_validation_errors)
                
                # Validate role assignment for default roles (only for INSERT operations)
                if operation == "INSERT":
                    role_assignment_errors = validate_role_assignment(person_id, role_id, row_num)
                    row_errors.extend(role_assignment_errors)
                
                # Get linking table config for duplicate/existence checks
                linking_table = config.get("linking_table", "")
                entity_col = config.get("linking_entity_col", "")
                oxp_col = config.get("linking_oxp_col", "")
                
                # Check for duplicates (INSERT) or existence (DELETE)
                if operation == "INSERT" and linking_table and entity_col and oxp_col:
                    duplicate_errors = check_duplicate_assignment(
                        object_id, person_id, role_id, row_num,
                        linking_table, entity_col, oxp_col, config["module_name"]
                    )
                    row_errors.extend(duplicate_errors)
                elif operation == "DELETE" and linking_table and entity_col and oxp_col:
                    existence_errors = check_assignment_exists(
                        object_id, person_id, role_id, row_num,
                        linking_table, entity_col, oxp_col, config["module_name"]
                    )
                    row_errors.extend(existence_errors)
        
        if row_errors:
            all_errors.extend(row_errors)
            # Never add this row to validated_data when there are resolution or validation errors
        elif object_id and person_id and role_id:
            # All resolved successfully and no row_errors - only add to validated_data when all IDs are present
            if assignment_key is not None:
                seen_assignments.add(assignment_key)
            # Include rowNumber for Java
            # Use dynamic ID field name from config (e.g., "Legal_ID" or "System_ID")
            id_field = config.get("id_field", "Legal_ID")  # Default to Legal_ID for backward compatibility
            validated_data.append({
                id_field: object_id,
                "Person_ID": person_id,
                "Role_ID": role_id,
                "operation": operation,
                "rowNumber": row_num
            })
        else:
            # row_errors empty but one or more IDs missing - ensure row is detected and excluded
            all_errors.append({
                "row": row_num,
                "field": "general",
                "message": "Row could not be processed; one or more required values are missing or invalid.",
                "error_code": "VALIDATION_ERROR"
            })
    
    # Ensure no row is silently dropped: any row not in validated_data and not in all_errors gets a generic error
    total_rows = len(df)
    rows_in_data = {d.get("rowNumber", d.get("row_number", 0)) for d in validated_data}
    rows_in_errors = {e.get("row", 0) for e in all_errors}
    # Excel row numbers: 2 .. total_rows + 1 (1-based + header)
    all_row_nums = set(range(2, total_rows + 2))
    rows_missing = all_row_nums - rows_in_data - rows_in_errors
    for row_num in sorted(rows_missing):
        all_errors.append({
            "row": row_num,
            "field": "general",
            "message": "Row could not be processed; one or more required values are missing or invalid.",
            "error_code": "VALIDATION_ERROR"
        })

    # Defensive logging: catch any remaining silent drops
    distinct_rows_covered = len(rows_in_data | rows_in_errors)
    logger.info(
        "Role validation result: validated_data=%d rows, all_errors=%d entries, distinct rows covered=%d, total_rows=%d",
        len(validated_data), len(all_errors), distinct_rows_covered, total_rows
    )
    if distinct_rows_covered < total_rows:
        logger.warning(
            "Role validation: distinct rows covered (%d) < total_rows (%d); some rows may have been silently dropped",
            distinct_rows_covered, total_rows
        )

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
    """Return expected sheet name for role entity"""
    operation = "Create" if "Upload New Items" in upload_option or "Add New Items" in upload_option else "Delete"
    return f"{operation} {entity_label}"

