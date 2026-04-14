"""
================================================================================
File: bulk_validation_service.py
================================================================================

OVERVIEW:
---------
Unified Bulk Upload Validation Service - Enterprise-grade FastAPI microservice
that validates Excel files for bulk data uploads across 20+ business entities
including Regulator, Geography, Regulatory Theme, System, Product, and more.

BUSINESS CAPABILITY:
--------------------
Supports the Data Governance & Master Data Management capability by providing:
- Pre-validation of bulk Excel uploads before database insertion
- Schema validation against entity-specific templates
- Business rule validation (uniqueness, referential integrity, hierarchy)
- Segment-aware validation for multi-tenant data isolation
- Role and relationship validation

DEPENDENCIES:
-------------
Modules that depend on this service:
- Frontend React Bulk Upload Wizard (src/components/BulkUploadWizard.jsx)
- Java BulkUploadServlet (calls this Python service via HTTP)
- Entity-specific processors in processors/objects/, processors/roles/, processors/relationships/

Dependencies this module requires:
- FastAPI: REST API framework
- Pandas: Excel file parsing and data manipulation
- PyMySQL: Database connectivity for validation queries
- Entity processors: Dynamic loading of 20+ entity-specific validation modules

ARCHITECTURE:
-------------
This service acts as a facade/orchestrator that:
1. Receives validation requests via REST API
2. Dynamically loads the appropriate entity processor module
3. Delegates validation to entity-specific logic
4. Returns standardized validation results with errors and validated data

TYPICAL FLOW:
-------------
1. Client uploads Excel file and calls /api/validate endpoint
2. Service reads Excel file using pandas
3. Determines entity type and loads corresponding processor
4. Processor validates columns, rows, and business rules
5. Service returns ValidationResponse with status, errors, and valid data
6. Client displays errors to user or proceeds with insertion

SUPPORTED ENTITIES:
-------------------
Objects: Regulator, Geography, Regulatory Theme, Regulation, Policy, Process,
         Project, Committee, Business Area, Capability, Client, Legal, Org Unit,
         People, Product, System, Dataset, Attribute, Interface, Glossary
Roles: 15+ role types (Business Area Role, Capability Role, etc.)
Relationships: System relationships, Category hierarchies

@author Data Governance Team
@version 2.0
@since 2024
"""

from fastapi import FastAPI, HTTPException, Depends, Header
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import pandas as pd
import pymysql
import os
import logging
from datetime import datetime

# Import entity-specific processors
# Note: We'll import the modules and call their functions directly

# Configure logging first
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

import sys
import importlib.util

# ==============================================================================
# INITIALIZATION: Prevent stdin blocking on Windows console
# ==============================================================================
# Redirect stdin to null device to prevent uvicorn from blocking when waiting
# for console input on Windows systems. This ensures the service runs smoothly
# as a background daemon without requiring interactive terminal.
try:
    _devnull = open(os.devnull, 'r')
    sys.stdin = _devnull
except (OSError, IOError):
    # Gracefully handle systems where /dev/null or equivalent is unavailable
    pass


def load_processor_module(module_name):
    """
    Dynamically load and initialize a processor module at runtime.
    
    This function uses Python's importlib to load processor modules without
    requiring static imports. This design allows for:
    - Lazy loading (processors loaded only when needed)
    - Graceful degradation (service continues if some processors fail to load)
    - Hot-swapping processors without restarting the service
    
    @param module_name: Fully qualified module name (e.g., 'processors.objects.regulator_bulk_processor')
    @type module_name: str
    
    @return: Loaded module object with validation functions, or None if loading failed
    @rtype: types.ModuleType or None
    
    @side_effects:
        - Adds current directory to sys.path if not present
        - Logs errors if module not found or fails to load
    
    TYPICAL USAGE:
        regulator_processor = load_processor_module("processors.objects.regulator_bulk_processor")
        if regulator_processor:
            errors = regulator_processor.validate_column_headers(df, upload_option)
    """
    try:
        # Add current directory to sys.path to enable relative imports
        import os
        current_dir = os.path.dirname(os.path.abspath(__file__))
        if current_dir not in sys.path:
            sys.path.insert(0, current_dir)
        
        # Use importlib to find and load the module specification
        spec = importlib.util.find_spec(module_name)
        if spec is None:
            logger.error(f"Module {module_name} not found in Python path")
            return None
        
        # Create module from spec and execute it to populate namespace
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        logger.info(f"Successfully loaded {module_name}")
        return module
    except Exception as e:
        logger.error(f"Could not load {module_name}: {e}", exc_info=True)
        return None


# Load .env from repo root; JDBC target prefers DatabaseConnection.java over .env DB_URL
_utils_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "utils")
if _utils_dir not in sys.path:
    sys.path.insert(0, _utils_dir)
from excel_read import open_excel_file, read_excel
try:
    from budg_db_config import get_pymysql_config

    DB_CONFIG = get_pymysql_config()
    logger.info(
        "Bulk validation DB target: %s:%s/%s (Java DatabaseConnection.java jdbc default wins over .env DB_URL)",
        DB_CONFIG.get("host"),
        DB_CONFIG.get("port"),
        DB_CONFIG.get("database"),
    )
except Exception as _e:
    logger.error("budg_db_config failed (%s); DB_USERNAME/DB_PASSWORD must be set via environment", _e)
    raise SystemExit(1) from _e

_DEV_DEFAULT_BULK_API_KEY = "BUDG_DEV_BULK_VALIDATION_LOCALHOST_ONLY"
_bulk_raw = (os.getenv("BULK_VALIDATION_API_KEY") or "").strip()
BULK_VALIDATION_API_KEY = _bulk_raw if _bulk_raw else _DEV_DEFAULT_BULK_API_KEY
if not _bulk_raw:
    logger.warning(
        "BULK_VALIDATION_API_KEY not set; using dev default matching Java HttpClientUtil. "
        "Set BULK_VALIDATION_API_KEY for production."
    )
if len(BULK_VALIDATION_API_KEY) < 16:
    logger.critical("BULK_VALIDATION_API_KEY must be at least 16 characters.")
    raise SystemExit(1)

# ==============================================================================
# PROCESSOR MODULES LOADING
# ==============================================================================
# Dynamically load all entity-specific processor modules. Each processor
# implements validation logic for a specific business entity (e.g., Regulator,
# Geography, System). Processors are loaded at startup but can be None if
# the module fails to load (graceful degradation).

# Object Processors (Core Business Entities)
regulator_processor = load_processor_module("processors.objects.regulator_bulk_processor")
geography_processor = load_processor_module("processors.objects.geography_bulk_processor")
regulatory_theme_processor = load_processor_module("processors.objects.regulatory_theme_bulk_processor")
regulation_processor = load_processor_module("processors.objects.regulation_bulk_processor")
policy_processor = load_processor_module("processors.objects.policy_bulk_processor")
process_processor = load_processor_module("processors.objects.process_bulk_processor")
project_processor = load_processor_module("processors.objects.project_bulk_processor")
committee_processor = load_processor_module("processors.objects.committee_bulk_processor")
business_area_processor = load_processor_module("processors.objects.business_area_bulk_processor")
capability_processor = load_processor_module("processors.objects.capability_bulk_processor")
client_processor = load_processor_module("processors.objects.client_bulk_processor")
legal_processor = load_processor_module("processors.objects.legal_bulk_processor")
org_unit_processor = load_processor_module("processors.objects.org_unit_bulk_processor")
people_processor = load_processor_module("processors.objects.people_bulk_processor")
product_processor = load_processor_module("processors.objects.product_bulk_processor")
system_processor = load_processor_module("processors.objects.system_bulk_processor")
dataset_processor = load_processor_module("processors.objects.dataset_bulk_processor")
attribute_processor = load_processor_module("processors.objects.attribute_bulk_processor")
interface_processor = load_processor_module("processors.objects.interface_bulk_processor")
glossary_processor = load_processor_module("processors.objects.glossary_bulk_processor")

# Role Processor (Handles all role types: Business Area Role, System Role, etc.)
role_processor = load_processor_module("processors.roles.role_bulk_processor")

# Relationship Processor (Handles system relationships and hierarchies)
relationship_processor = load_processor_module("processors.relationships.relationship_bulk_processor")

app = FastAPI(title="Bulk Upload Validation Service")


def verify_bulk_api_key(x_api_key: Optional[str] = Header(default=None, alias="X-API-Key")) -> None:
    """Require X-API-Key matching BULK_VALIDATION_API_KEY (set by Java HttpClientUtil)."""
    if not x_api_key or x_api_key != BULK_VALIDATION_API_KEY:
        raise HTTPException(status_code=401, detail="Invalid or missing API key")

# ==============================================================================
# DATABASE CONFIGURATION
# ==============================================================================
# DB_CONFIG is initialized above (budg_db_config) before processors load.


# ==============================================================================
# REQUEST/RESPONSE MODELS (Pydantic)
# ==============================================================================

class ValidationRequest(BaseModel):
    """
    Request payload for bulk upload validation endpoint.
    
    ATTRIBUTES:
    -----------
    @param file_path: Absolute path to Excel file to validate
    @type file_path: str
    
    @param upload_option: Upload operation type - determines validation rules
    @type upload_option: str
    @values: "Add New Items" | "Update Existing Items" | "Remove Existing Items"
    
    @param entity: Business entity type being uploaded
    @type entity: str
    @values: "Regulator" | "Geography" | "System" | "Product" | etc. (20+ entities)
    
    @param user_id: ID of user performing the upload (for audit trail)
    @type user_id: int
    
    @param column_mappings: Optional mapping of Excel columns to expected field names.
                            Allows flexibility when Excel template columns differ.
    @type column_mappings: Dict[str, str] or None
    @example: {"Full Name": "name", "Code": "code"}
    
    @param segment_mode: Multi-tenancy mode for segment isolation
    @type segment_mode: str or None
    @values: "MULTIPLE" | "ENTERPRISE" | "SPECIFIC"
    
    @param segment: Specific segment ID when segment_mode is "SPECIFIC"
    @type segment: str or None
    """
    file_path: str
    upload_option: str  # "Add New Items" | "Update Existing Items" | "Remove Existing Items"
    entity: str  # "Regulator" | "Geography" | "Regulatory Theme" | "Regulation" | "Policy" | "Process" | "Project" | "Committee" | "Business Area" | "Capability" | "Client" | "Legal" | "Org Unit" | "People" | "Product" | "System" | "Dataset" | "Attribute" | "Interface" | "Glossary"
    user_id: int
    column_mappings: Optional[Dict[str, str]] = None  # Maps Excel column names to expected field names
    segment_mode: Optional[str] = None  # "MULTIPLE" | "ENTERPRISE" | "SPECIFIC"
    segment: Optional[str] = None  # Segment ID (when SPECIFIC mode)


class ValidationError(BaseModel):
    """
    Represents a single validation error for a specific row and field.
    
    ATTRIBUTES:
    -----------
    @param row: Excel row number where error occurred (1-based, excludes header)
    @type row: int
    
    @param field: Field/column name that failed validation
    @type field: str
    
    @param message: Human-readable error message for end user
    @type message: str
    
    @param error_code: Machine-readable error code for programmatic handling
    @type error_code: str
    @values: MISSING_REQUIRED | DUPLICATE_VALUE | INVALID_REFERENCE | 
             NAME_REF_MISMATCH | PARENT_MISMATCH | etc.
    """
    row: int
    field: str
    message: str
    error_code: str


class ValidationResponse(BaseModel):
    """
    Response payload returned by validation endpoint.
    
    ATTRIBUTES:
    -----------
    @param status: Overall validation result
    @type status: str
    @values: "valid" - All rows valid | "invalid" - Some errors (can proceed with warnings) | 
             "error" - Fatal errors (cannot proceed)
    
    @param message: Summary message describing validation outcome
    @type message: str
    
    @param total_rows: Total number of data rows in Excel file (excludes header)
    @type total_rows: int
    
    @param valid_rows: Number of rows that passed all validation rules
    @type valid_rows: int
    
    @param invalid_rows: Number of rows with validation errors
    @type invalid_rows: int
    
    @param errors: List of all validation errors found
    @type errors: List[ValidationError]
    
    @param data: List of validated row data ready for insertion.
                 Empty if status is "error" (fatal errors prevent processing).
    @type data: List[Dict[str, Any]]
    
    IMPORTANT:
    ----------
    For role entities with "Continue on Warning" enabled, validated_data may
    contain valid rows even when errors exist, allowing partial uploads.
    """
    status: str  # "valid" | "invalid" | "error"
    message: str
    total_rows: int
    valid_rows: int
    invalid_rows: int
    errors: List[ValidationError]
    data: List[Dict[str, Any]]


try:
    from custom_fields_validator import (
        CUSTOM_FIELD_BLOCKING_ERROR_CODES,
        filter_rows_with_blocking_custom_field_errors,
    )
except ImportError:
    CUSTOM_FIELD_BLOCKING_ERROR_CODES = frozenset({
        "CUSTOM_FIELD_MANDATORY",
        "CUSTOM_FIELD_VALIDATION_ERROR",
        "CUSTOM_FIELD_VALIDATION_EXCEPTION",
    })

    def filter_rows_with_blocking_custom_field_errors(
        validated_data: List[Dict[str, Any]], all_errors: List[Any]
    ) -> List[Dict[str, Any]]:
        return validated_data


def get_entity_processor(entity: str):
    """
    Factory function that returns the appropriate processor module and functions
    for the requested entity type.
    
    This function implements the Strategy Pattern, selecting validation logic
    based on entity type. It abstracts differences between processor APIs:
    - Old API: validate_columns() + validate_rows() (legacy processors)
    - New API: validate_and_resolve() (modern unified approach)
    
    @param entity: Entity type name (case-insensitive)
    @type entity: str
    
    @return: Dictionary containing processor functions:
             - validate_columns: Function to validate column headers (old API)
             - validate_rows: Function to validate row data (old API)
             - validate_and_resolve: Unified validation function (new API)
             - get_sheet_name: Function to determine expected Excel sheet name
             - apply_column_mappings: Function to map Excel columns to expected names
             - normalize_columns: Optional function to normalize column names
    @rtype: Dict[str, Callable]
    
    @raises HTTPException: 400 if entity type is unsupported
    @raises HTTPException: 500 if processor module failed to load
    
    PROCESSOR API DIFFERENCES:
    --------------------------
    OLD API (separate functions):
        - validate_column_headers(df, upload_option) -> List[ValidationError]
        - validate_row_data(df, upload_option) -> (List[ValidationError], List[Dict])
    
    NEW API (unified function):
        - validate_and_resolve(df, upload_option, user_id, segment_mode, segment) 
          -> (List[Dict], List[ValidationError])
    
    TYPICAL USAGE:
    --------------
        processor = get_entity_processor("Regulator")
        sheet_name = processor["get_sheet_name"]("Add New Items")
        errors = processor["validate_columns"](df, "Add New Items")
    """
    entity_lower = entity.lower()
    
    if entity_lower == "regulator":
        if regulator_processor is None:
            raise HTTPException(status_code=500, detail="Regulator processor not available")
        return {
            "validate_columns": regulator_processor.validate_column_headers,
            "validate_rows": regulator_processor.validate_row_data,
            "get_sheet_name": regulator_processor.get_sheet_name,
            "apply_column_mappings": regulator_processor.apply_column_mappings
        }
    elif entity_lower == "geography":
        if geography_processor is None:
            raise HTTPException(status_code=500, detail="Geography processor not available")
        return {
            "validate_columns": geography_processor.validate_column_headers,
            "validate_rows": geography_processor.validate_row_data,
            "get_sheet_name": geography_processor.get_sheet_name,
            "apply_column_mappings": geography_processor.apply_column_mappings
        }
    elif entity_lower == "regulatory theme":
        if regulatory_theme_processor is None:
            raise HTTPException(status_code=500, detail="Regulatory Theme processor not available")
        return {
            "validate_columns": regulatory_theme_processor.validate_column_headers,
            "validate_rows": regulatory_theme_processor.validate_row_data,
            "get_sheet_name": regulatory_theme_processor.get_sheet_name,
            "apply_column_mappings": regulatory_theme_processor.apply_column_mappings
        }
    elif entity_lower == "regulation":
        if regulation_processor is None:
            raise HTTPException(status_code=500, detail="Regulation processor not available")
        return {
            "validate_columns": regulation_processor.validate_column_headers,
            "validate_rows": regulation_processor.validate_row_data,
            "get_sheet_name": regulation_processor.get_sheet_name,
            "apply_column_mappings": regulation_processor.apply_column_mappings
        }
    elif entity_lower == "policy":
        if policy_processor is None:
            raise HTTPException(status_code=500, detail="Policy processor not available")
        return {
            "validate_columns": policy_processor.validate_column_headers,
            "validate_rows": policy_processor.validate_row_data,
            "get_sheet_name": policy_processor.get_sheet_name,
            "apply_column_mappings": policy_processor.apply_column_mappings
        }
    elif entity_lower == "process":
        if process_processor is None:
            raise HTTPException(status_code=500, detail="Process processor not available")
        return {
            "validate_columns": process_processor.validate_column_headers,
            "validate_rows": process_processor.validate_row_data,
            "get_sheet_name": process_processor.get_sheet_name,
            "apply_column_mappings": process_processor.apply_column_mappings,
            "normalize_columns": process_processor.normalize_process_columns
        }
    elif entity_lower == "project":
        if project_processor is None:
            raise HTTPException(status_code=500, detail="Project processor not available")
        return {
            "validate_columns": project_processor.validate_column_headers,
            "validate_rows": project_processor.validate_row_data,
            "get_sheet_name": project_processor.get_sheet_name,
            "apply_column_mappings": project_processor.apply_column_mappings
        }
    elif entity_lower == "committee":
        if committee_processor is None:
            raise HTTPException(status_code=500, detail="Committee processor not available")
        return {
            "validate_columns": committee_processor.validate_column_headers,
            "validate_rows": committee_processor.validate_row_data,
            "get_sheet_name": committee_processor.get_sheet_name,
            "apply_column_mappings": committee_processor.apply_column_mappings
        }
    elif entity_lower == "business area":
        if business_area_processor is None:
            raise HTTPException(status_code=500, detail="Business Area processor not available")
        return {
            "validate_and_resolve": business_area_processor.validate_and_resolve_business_area,
            "get_sheet_name": business_area_processor.get_sheet_name,
            "apply_column_mappings": business_area_processor.apply_column_mappings
        }
    elif entity_lower == "capability":
        if capability_processor is None:
            raise HTTPException(status_code=500, detail="Capability processor not available")
        return {
            "validate_and_resolve": capability_processor.validate_and_resolve_capability,
            "get_sheet_name": capability_processor.get_sheet_name,
            "apply_column_mappings": capability_processor.apply_column_mappings
        }
    elif entity_lower == "client":
        if client_processor is None:
            raise HTTPException(status_code=500, detail="Client processor not available")
        return {
            "validate_and_resolve": client_processor.validate_and_resolve_client,
            "get_sheet_name": client_processor.get_sheet_name,
            "apply_column_mappings": client_processor.apply_column_mappings
        }
    elif entity_lower in ("legal", "legalentity"):
        if legal_processor is None:
            raise HTTPException(status_code=500, detail="Legal processor not available")
        return {
            "validate_and_resolve": legal_processor.validate_and_resolve_legal,
            "get_sheet_name": legal_processor.get_sheet_name,
            "apply_column_mappings": legal_processor.apply_column_mappings
        }
    elif entity_lower in ("org unit", "org. unit", "orgunit", "org_unit"):
        if org_unit_processor is None:
            raise HTTPException(status_code=500, detail="Org Unit processor not available")
        return {
            "validate_and_resolve": org_unit_processor.validate_and_resolve_org_unit,
            "get_sheet_name": org_unit_processor.get_sheet_name,
            "apply_column_mappings": org_unit_processor.apply_column_mappings
        }
    elif entity_lower == "people":
        if people_processor is None:
            raise HTTPException(status_code=500, detail="People processor not available")
        return {
            "validate_and_resolve": people_processor.validate_and_resolve_people,
            "get_sheet_name": people_processor.get_sheet_name,
            "apply_column_mappings": people_processor.apply_column_mappings
        }
    elif entity_lower == "product":
        if product_processor is None:
            raise HTTPException(status_code=500, detail="Product processor not available")
        return {
            "validate_and_resolve": product_processor.validate_and_resolve_product,
            "get_sheet_name": product_processor.get_sheet_name,
            "apply_column_mappings": product_processor.apply_column_mappings
        }
    elif entity_lower == "system":
        if system_processor is None:
            raise HTTPException(status_code=500, detail="System processor not available")
        return {
            "validate_and_resolve": system_processor.validate_and_resolve_system,
            "get_sheet_name": system_processor.get_sheet_name,
            "apply_column_mappings": system_processor.apply_column_mappings
        }
    elif entity_lower == "dataset":
        if dataset_processor is None:
            raise HTTPException(status_code=500, detail="Dataset processor not available")
        return {
            "validate_and_resolve": dataset_processor.validate_and_resolve_dataset,
            "get_sheet_name": dataset_processor.get_sheet_name,
            "apply_column_mappings": dataset_processor.apply_column_mappings
        }
    elif entity_lower == "attribute":
        if attribute_processor is None:
            raise HTTPException(status_code=500, detail="Attribute processor not available")
        return {
            "validate_and_resolve": attribute_processor.validate_and_resolve_attribute,
            "get_sheet_name": attribute_processor.get_sheet_name,
            "apply_column_mappings": attribute_processor.apply_column_mappings
        }
    elif entity_lower == "interface":
        if interface_processor is None:
            raise HTTPException(status_code=500, detail="Interface processor not available")
        return {
            "validate_and_resolve": interface_processor.validate_and_resolve_interface,
            "get_sheet_name": interface_processor.get_sheet_name,
            "apply_column_mappings": interface_processor.apply_column_mappings
        }
    elif entity_lower == "glossary":
        if glossary_processor is None:
            raise HTTPException(status_code=500, detail="Glossary processor not available")
        return {
            "validate_and_resolve": glossary_processor.validate_and_resolve_glossary,
            "get_sheet_name": glossary_processor.get_sheet_name,
            "apply_column_mappings": glossary_processor.apply_column_mappings
        }
    
    # Role entities
    elif entity_lower in ["business area role", "businessarearole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Business Area Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Business Area Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["capability role", "capabilityrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Capability Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Capability Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["client role", "clientrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Client Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Client Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["committee role", "committeerole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Committee Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Committee Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["data quality role", "dataqualityrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Data Quality Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Data Quality Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["data set role", "dataset role", "datasetrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Data Set Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Data Set Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["glossary role", "glossaryrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Glossary Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Glossary Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["interface role", "interfacerole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Interface Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Interface Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["legal entity role", "legalentityrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Legal Entity Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Legal Entity Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["policy role", "policyrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Policy Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Policy Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["process role", "processrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Process Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Process Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["product role", "productrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Product Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Product Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["project role", "projectrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Project Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Project Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["regulation role", "regulationrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Regulation Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Regulation Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["system role", "systemrole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "System Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "System Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif entity_lower in ["attribute role", "attributerole"]:
        if role_processor is None:
            raise HTTPException(status_code=500, detail="Role processor not available")
        return {
            "validate_and_resolve": lambda df, upload_option, user_id: role_processor.validate_and_resolve_role(df, "Attribute Role", upload_option, user_id),
            "get_sheet_name": lambda upload_option: role_processor.get_sheet_name(upload_option, "Attribute Role"),
            "apply_column_mappings": role_processor.apply_column_mappings
        }
    elif relationship_processor is not None:
        from processors.relationships.relationship_bulk_processor import (
            RELATIONSHIP_ENTITY_CONFIG,
            resolve_relationship_entity_key,
        )
        rk = resolve_relationship_entity_key(entity)
        if rk in RELATIONSHIP_ENTITY_CONFIG:
            return {
                "validate_and_resolve": lambda df, upload_option, user_id: relationship_processor.validate_and_resolve_relationship(df, entity, upload_option, user_id),
                "get_sheet_name": lambda upload_option: relationship_processor.get_sheet_name(upload_option, entity),
                "apply_column_mappings": relationship_processor.apply_column_mappings
            }
    else:
        raise HTTPException(status_code=400, detail=f"Unsupported entity: {entity}")


# ==============================================================================
# REST API ENDPOINTS
# ==============================================================================

@app.get("/")
def read_root():
    """
    Health check and service information endpoint.
    
    PURPOSE:
    --------
    Provides service status and lists all supported entities. Used by:
    - Load balancers for health checks
    - Monitoring systems for availability checks
    - API consumers to discover supported entities
    
    @return: Service metadata including status, supported entities, and timestamp
    @rtype: dict
    
    @example_response:
    {
        "service": "Bulk Upload Validation Service",
        "status": "running",
        "supported_entities": ["Regulator", "Geography", ...],
        "timestamp": "2024-02-15T10:30:00"
    }
    """
    return {
        "service": "Bulk Upload Validation Service",
        "status": "running",
        "supported_entities": [
            "Regulator", "Geography", "Regulatory Theme", "Regulation", "Policy", "Process", "Project", "Committee", 
            "Business Area", "Capability", "Client", "Legal", "Org Unit", "People", "Product", "System", "Dataset", 
            "Attribute", "Interface", "Glossary",
            "Business Area Role", "Capability Role", "Client Role", "Committee Role", "Data Quality Role", 
            "Data Set Role", "Glossary Role", "Interface Role", "Legal Entity Role", "Policy Role", 
            "Process Role", "Product Role", "Project Role", "Regulation Role", "System Role",
            "system_x_objectxpeople", "system_x_capability", "system_x_regulation", "system_x_policy",
            "SystemXCatItemCategory", "CatItemCategoryXSystem", "CatItemCategory Hierarchy"
        ],
        "timestamp": datetime.now().isoformat()
    }


@app.post(
    "/api/validate",
    response_model=ValidationResponse,
    dependencies=[Depends(verify_bulk_api_key)],
)
async def validate_bulk_upload(request: ValidationRequest):
    """
    Main validation endpoint for bulk Excel uploads.
    
    RESPONSIBILITY:
    ---------------
    Orchestrates the complete validation workflow for any supported entity type.
    Validates both structure (columns) and content (rows) according to business rules.
    
    PURPOSE:
    --------
    Validates an Excel file before data insertion to catch errors early and provide
    immediate feedback to users. Prevents invalid data from entering the database.
    
    PARAMETERS:
    -----------
    @param request: Validation request containing file path, entity type, upload option,
                    user ID, and optional column mappings and segment configuration
    @type request: ValidationRequest
    
    RETURN VALUE:
    -------------
    @return: Validation response with status, error details, and validated data
    @rtype: ValidationResponse
    
    SIDE EFFECTS:
    -------------
    - Reads Excel file from disk
    - Executes database queries via entity processors (for reference validation)
    - Logs validation progress and errors
    - Does NOT modify database (read-only validation)
    
    ERROR HANDLING:
    ---------------
    @raises HTTPException(404): File not found
    @raises HTTPException(400): Unsupported entity type
    @raises HTTPException(500): Processor not available or internal error
    
    TYPICAL FLOW:
    -------------
    1. Check if file exists
    2. Get entity-specific processor
    3. Determine expected Excel sheet name
    4. Read Excel file with pandas
    5. Filter hidden sheets (sheets starting with "Hidden_")
    6. Apply column mappings if provided
    7. Invoke processor validation (columns + rows)
    8. Collect and standardize validation errors
    9. Calculate statistics (valid/invalid row counts)
    10. Check for fatal errors (NAME_REF_MISMATCH, PARENT_MISMATCH)
    11. Return validation response
    
    IMPORTANT NOTES:
    ----------------
    - For role entities, validated_data is preserved even with errors when
      "Continue on Warning" is enabled, allowing partial uploads
    - Fatal error codes (NAME_REF_MISMATCH, PARENT_MISMATCH, INCOMPATIBLE_IDENTIFIERS)
      cause status="error" and clear validated_data to prevent insertion
    - Supports both old API (validate_columns + validate_rows) and new API
      (validate_and_resolve) for backward compatibility
    
    EXAMPLE REQUEST:
    ----------------
    POST /api/validate
    {
        "file_path": "C:/uploads/regulators.xlsx",
        "upload_option": "Add New Items",
        "entity": "Regulator",
        "user_id": 123,
        "segment_mode": "ENTERPRISE",
        "column_mappings": null
    }
    
    EXAMPLE RESPONSE (Valid):
    -------------------------
    {
        "status": "valid",
        "message": "All rows validated successfully",
        "total_rows": 50,
        "valid_rows": 50,
        "invalid_rows": 0,
        "errors": [],
        "data": [{...}, {...}, ...]
    }
    
    EXAMPLE RESPONSE (Invalid):
    ---------------------------
    {
        "status": "invalid",
        "message": "Validation completed with 3 error(s)",
        "total_rows": 50,
        "valid_rows": 47,
        "invalid_rows": 3,
        "errors": [
            {"row": 5, "field": "code", "message": "Duplicate code", "error_code": "DUPLICATE_VALUE"},
            ...
        ],
        "data": [{...}, {...}, ...]
    }
    """
    try:
        logger.info(f"Validating file: {request.file_path} for entity: {request.entity}, operation: {request.upload_option}")
        
        # Step 1: Verify file existence before processing
        if not os.path.exists(request.file_path):
            raise HTTPException(status_code=404, detail=f"File not found: {request.file_path}")
        
        # Step 2: Get entity-specific processor (factory pattern)
        processor = get_entity_processor(request.entity)
        
        # Step 3: Determine expected sheet name based on upload operation
        # Different operations may use different sheet templates
        expected_sheet_name = processor["get_sheet_name"](request.upload_option)
        
        # Step 4: Read Excel file with dynamic sheet selection and error handling
        try:
            excel_file = open_excel_file(request.file_path, engine='openpyxl')
            available_sheets = excel_file.sheet_names
            
            # Filter out hidden sheets - convention: sheets prefixed with "Hidden_"
            # Hidden sheets often contain reference data or formulas not meant for upload
            visible_sheets = [s for s in available_sheets if not s.startswith("Hidden_")]
            
            if not visible_sheets:
                logger.error(f"No visible sheets found. Available sheets: {available_sheets}")
                return ValidationResponse(
                    status="error",
                    message=f"No visible sheets found in Excel file. All sheets appear to be hidden.",
                    total_rows=0,
                    valid_rows=0,
                    invalid_rows=0,
                    errors=[ValidationError(
                        row=0,
                        field="sheet",
                        message=f"No visible sheets found. Available sheets: {', '.join(available_sheets)}",
                        error_code="NO_VISIBLE_SHEETS"
                    )],
                    data=[]
                )
            
            # Sheet selection strategy: prefer expected sheet, fallback to first visible
            # This provides flexibility when users modify template sheet names
            sheet_name = None
            if expected_sheet_name and expected_sheet_name in available_sheets:
                sheet_name = expected_sheet_name
                logger.info(f"Using expected sheet: {sheet_name}")
            else:
                sheet_name = visible_sheets[0]
                if expected_sheet_name:
                    logger.warning(f"Expected sheet '{expected_sheet_name}' not found. Using first visible sheet: {sheet_name}")
                else:
                    logger.info(f"No expected sheet name. Using first visible sheet: {sheet_name}")
            
            # Read the selected sheet into pandas DataFrame
            df = read_excel(request.file_path, sheet_name=sheet_name, engine='openpyxl')
            logger.info(f"Successfully read sheet '{sheet_name}' with {len(df)} rows")
            
        except ValueError as e:
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
        
        # Step 5: Empty file validation
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
        
        # Step 6: Apply column mappings and normalization if provided
        # Column mappings allow flexibility when Excel columns don't match expected names
        # Example: User has "Full Name" but system expects "name"
        if request.column_mappings and "apply_column_mappings" in processor:
            df = processor["apply_column_mappings"](df, request.column_mappings)
            logger.info(f"Column mappings applied. Columns after mapping: {list(df.columns)}")
        
        # Some processors normalize columns (e.g., Process has dynamic columns)
        if "normalize_columns" in processor:
            df = processor["normalize_columns"](df, request.upload_option)
            logger.info(f"Column normalization applied. Columns: {list(df.columns)}")
        
        if df.columns.duplicated().any():
            dup = sorted({str(c) for c in df.columns[df.columns.duplicated()].tolist()})
            logger.warning("Removing duplicate column names after mapping/normalize (keeping first): %s", dup)
            df = df.loc[:, ~df.columns.duplicated()].copy()
        
        total_rows = len(df)
        all_errors = []
        validated_data = []
        
        # Step 7: Execute entity-specific validation
        # Two API styles supported for backward compatibility:
        
        # NEW API: Single unified function (modern processors)
        if "validate_and_resolve" in processor:
            # New API returns (validated_data, errors) in one call
            # Try passing segment parameters for multi-tenant support
            try:
                validated_data, validation_errors = processor["validate_and_resolve"](
                    df, request.upload_option, request.user_id, request.segment_mode, request.segment
                )
            except TypeError:
                # Processor doesn't accept segment parameters - call without them
                validated_data, validation_errors = processor["validate_and_resolve"](
                    df, request.upload_option, request.user_id
                )
            
            # Standardize error format (support Pydantic v1 and v2)
            for error in validation_errors:
                if hasattr(error, 'model_dump'):  # Pydantic v2
                    all_errors.append(error.model_dump())
                elif hasattr(error, 'dict'):  # Pydantic v1
                    all_errors.append(error.dict())
                elif isinstance(error, dict):
                    all_errors.append(error)
                else:
                    # Manual conversion for non-Pydantic objects
                    all_errors.append({
                        "row": getattr(error, 'row', 0),
                        "field": getattr(error, 'field', ''),
                        "message": getattr(error, 'message', ''),
                        "error_code": getattr(error, 'error_code', '')
                    })

            # Align total_rows with actual data rows when processors drop blank Excel rows (Client, Product, etc.)
            row_nums = set()
            for d in validated_data:
                if isinstance(d, dict) and d.get("row_number") is not None:
                    try:
                        row_nums.add(int(d["row_number"]))
                    except (TypeError, ValueError):
                        pass
            for e in all_errors:
                if isinstance(e, dict) and e.get("row") is not None:
                    try:
                        row_nums.add(int(e["row"]))
                    except (TypeError, ValueError):
                        pass
            if row_nums:
                total_rows = len(row_nums)
            elif not validated_data and not all_errors:
                total_rows = 0
        
        # OLD API: Separate column and row validation (legacy processors)
        else:
            # Step 1: Validate column headers using entity-specific validator
            header_errors = processor["validate_columns"](df, request.upload_option)
            # Convert ValidationError objects to dictionaries (support both Pydantic v1 and v2)
            for error in header_errors:
                if hasattr(error, 'model_dump'):  # Pydantic v2
                    all_errors.append(error.model_dump())
                elif hasattr(error, 'dict'):  # Pydantic v1
                    all_errors.append(error.dict())
                elif isinstance(error, dict):
                    all_errors.append(error)
                else:
                    # Convert to dict manually
                    all_errors.append({
                        "row": getattr(error, 'row', 0),
                        "field": getattr(error, 'field', ''),
                        "message": getattr(error, 'message', ''),
                        "error_code": getattr(error, 'error_code', '')
                    })
            
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
            
            # Step 2: Validate row data using entity-specific validator
            # Pass segment + user_id for INSERT so duplicate checks and segment rules match Java (segment_mode may be null)
            try:
                if request.upload_option in ["Add New Items", "Upload New Items"]:
                    row_errors, validated_data = processor["validate_rows"](
                        df, request.upload_option, request.segment_mode, request.segment, request.user_id
                    )
                else:
                    row_errors, validated_data = processor["validate_rows"](df, request.upload_option)
            except TypeError:
                # If function doesn't accept segment parameters, call without them (backward compatibility)
                logger.warning("Processor doesn't support segment parameters, calling without them")
                row_errors, validated_data = processor["validate_rows"](df, request.upload_option)
            # Convert ValidationError objects to dictionaries (support both Pydantic v1 and v2)
            row_errors_dict = []
            for error in row_errors:
                if hasattr(error, 'model_dump'):  # Pydantic v2
                    row_errors_dict.append(error.model_dump())
                elif hasattr(error, 'dict'):  # Pydantic v1
                    row_errors_dict.append(error.dict())
                elif isinstance(error, dict):
                    row_errors_dict.append(error)
                else:
                    # Convert to dict manually
                    row_errors_dict.append({
                        "row": getattr(error, 'row', 0),
                        "field": getattr(error, 'field', ''),
                        "message": getattr(error, 'message', ''),
                        "error_code": getattr(error, 'error_code', '')
                    })
            all_errors.extend(row_errors_dict)
            # Align total_rows when legacy processors skip blank Excel rows (unique data row numbers only)
            if "validate_and_resolve" not in processor:
                row_nums = set()
                for d in validated_data:
                    if isinstance(d, dict) and d.get("row_number") is not None:
                        try:
                            row_nums.add(int(d["row_number"]))
                        except (TypeError, ValueError):
                            pass
                for e in all_errors:
                    if isinstance(e, dict) and e.get("row") is not None:
                        try:
                            row_nums.add(int(e["row"]))
                        except (TypeError, ValueError):
                            pass
                if row_nums:
                    total_rows = len(row_nums)
                elif not validated_data and not all_errors:
                    total_rows = 0
        
        # Ensure rows with invalid custom-field values are never sent to Java processing.
        validated_data = filter_rows_with_blocking_custom_field_errors(validated_data, all_errors)

        # Calculate valid and invalid rows
        valid_rows = len(validated_data)
        invalid_rows = total_rows - valid_rows
        
        # Determine overall status
        if all_errors:
            status = "invalid"
            message = f"Validation completed with {len(all_errors)} error(s)"
            # Abort process when ref, object name, or parent name do not point to the same object (no rows uploaded).
            # For Role entities, do NOT clear validated_data so Java can process valid rows when "Continue on Warning" is selected.
            is_role_entity = request.entity and str(request.entity).strip().lower().endswith(" role")
            abort_codes = {"NAME_REF_MISMATCH", "PARENT_MISMATCH", "INCOMPATIBLE_IDENTIFIERS"}
            for e in all_errors:
                code = e.get("error_code", "") if isinstance(e, dict) else getattr(e, "error_code", "")
                if code in abort_codes:
                    if not is_role_entity:
                        status = "error"
                        message = "Ref, object name, and parent name must point to the same object. Process aborted."
                        validated_data = []
                        valid_rows = 0
                        invalid_rows = total_rows
                    break
        else:
            status = "valid"
            message = "All rows validated successfully"

        # Defensive: ensure status is invalid when there are errors (no silent valid with errors)
        if all_errors and status == "valid":
            logger.warning("Validation had errors but status was 'valid'; forcing status to 'invalid'")
            status = "invalid"
            message = f"Validation completed with {len(all_errors)} error(s)"
        
        logger.info(f"Validation complete: {valid_rows} valid, {invalid_rows} invalid out of {total_rows} total")
        # Log error count and sample message so we can confirm errors are present in the response
        if all_errors:
            logger.info(f"Validation errors count: {len(all_errors)}, validated_data count: {len(validated_data)}")
            first_err = all_errors[0]
            first_msg = first_err.get("message", "") if isinstance(first_err, dict) else getattr(first_err, "message", "")
            first_row = first_err.get("row", 0) if isinstance(first_err, dict) else getattr(first_err, "row", 0)
            logger.info(f"First validation error: row={first_row}, message={first_msg[:100] if first_msg else ''}")
        
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
    uvicorn.run(
        app,
        host="127.0.0.1",
        port=8000,
        log_level="info",
        access_log=True,
    )

