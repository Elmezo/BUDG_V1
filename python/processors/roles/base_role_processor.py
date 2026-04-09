"""
Base Role Processor
Shared validation logic for all role assignment types
"""

from typing import List, Dict, Any, Tuple, Optional
import pandas as pd
import pymysql
import os
import logging

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

class BaseRoleProcessor:
    """Base class for role processors"""
    
    def __init__(self, entity_name: str, entity_table: str):
        self.entity_name = entity_name
        self.entity_table = entity_table
        self.entity_col = f"{entity_name}_ID"
    
    def validate_column_headers(self, df: pd.DataFrame, upload_option: str) -> Tuple[bool, str]:
        """Validate required columns"""
        required_cols = [self.entity_col, "Person_ID", "Role_ID"]
        missing_cols = [col for col in required_cols if col not in df.columns]
        
        if missing_cols:
            return False, f"Missing required columns: {', '.join(missing_cols)}"
        return True, "All required columns present"
    
    def validate_row_data(self, df: pd.DataFrame, upload_option: str, 
                         user_id: int) -> Tuple[List[Dict], List[Dict]]:
        """Validate role assignment data"""
        errors = []
        valid_rows = []
        
        for idx, row in df.iterrows():
            row_num = idx + 2
            row_errors = []
            
            entity_id = row.get(self.entity_col)
            person_id = row.get("Person_ID")
            role_id = row.get("Role_ID")
            
            # Validate Entity ID
            if pd.isna(entity_id):
                row_errors.append({
                    "row": row_num,
                    "field": self.entity_col,
                    "message": f"{self.entity_name} ID is required",
                    "error_code": "REQUIRED_FIELD"
                })
            else:
                try:
                    entity_id = int(entity_id)
                    if not self._entity_exists(self.entity_table, entity_id):
                        row_errors.append({
                            "row": row_num,
                            "field": self.entity_col,
                            "message": f"{self.entity_name} with ID {entity_id} not found",
                            "error_code": "NOT_FOUND"
                        })
                except (ValueError, TypeError):
                    row_errors.append({
                        "row": row_num,
                        "field": self.entity_col,
                        "message": f"Invalid {self.entity_name} ID format",
                        "error_code": "INVALID_FORMAT"
                    })
            
            # Validate Person ID
            if pd.isna(person_id):
                row_errors.append({
                    "row": row_num,
                    "field": "Person_ID",
                    "message": "Person ID is required",
                    "error_code": "REQUIRED_FIELD"
                })
            else:
                try:
                    person_id = int(person_id)
                    if not self._entity_exists("people", person_id):
                        row_errors.append({
                            "row": row_num,
                            "field": "Person_ID",
                            "message": f"Person with ID {person_id} not found",
                            "error_code": "NOT_FOUND"
                        })
                except (ValueError, TypeError):
                    row_errors.append({
                        "row": row_num,
                        "field": "Person_ID",
                        "message": "Invalid Person ID format",
                        "error_code": "INVALID_FORMAT"
                    })
            
            # Validate Role ID
            if pd.isna(role_id):
                row_errors.append({
                    "row": row_num,
                    "field": "Role_ID",
                    "message": "Role ID is required",
                    "error_code": "REQUIRED_FIELD"
                })
            else:
                try:
                    role_id = int(role_id)
                    if not self._entity_exists("object_role", role_id):
                        row_errors.append({
                            "row": row_num,
                            "field": "Role_ID",
                            "message": f"Role with ID {role_id} not found",
                            "error_code": "NOT_FOUND"
                        })
                except (ValueError, TypeError):
                    row_errors.append({
                        "row": row_num,
                        "field": "Role_ID",
                        "message": "Invalid Role ID format",
                        "error_code": "INVALID_FORMAT"
                    })
            
            if row_errors:
                errors.extend(row_errors)
            else:
                valid_rows.append(row.to_dict())
        
        return valid_rows, errors
    
    def _entity_exists(self, table: str, entity_id: int) -> bool:
        """Check if entity exists"""
        try:
            with get_db_connection() as conn:
                with conn.cursor() as cur:
                    cur.execute(f"SELECT COUNT(*) FROM {table} WHERE id = %s", (entity_id,))
                    return cur.fetchone()[0] > 0
        except Exception as e:
            logger.error(f"Error checking {table} existence: {e}")
            return False
    
    def apply_column_mappings(self, df: pd.DataFrame, 
                             column_mappings: Optional[Dict[str, str]]) -> pd.DataFrame:
        """Apply column mappings"""
        if not column_mappings:
            return df
        
        rename_dict = {k: v for k, v in column_mappings.items() if k in df.columns}
        if rename_dict:
            df = df.rename(columns=rename_dict)
        return df
    
    def get_sheet_name(self, upload_option: str) -> None:
        """Return None for automatic detection"""
        return None

