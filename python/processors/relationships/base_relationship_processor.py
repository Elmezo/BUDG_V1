"""
Base Relationship Processor
Shared validation logic for all relationship types
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

class BaseRelationshipProcessor:
    """Base class for relationship processors"""
    
    def __init__(self, entity_a_name: str, entity_b_name: str, 
                 entity_a_table: str, entity_b_table: str):
        self.entity_a_name = entity_a_name
        self.entity_b_name = entity_b_name
        self.entity_a_table = entity_a_table
        self.entity_b_table = entity_b_table
        self.entity_a_col = f"{entity_a_name}_ID"
        self.entity_b_col = f"{entity_b_name}_ID"
    
    def validate_column_headers(self, df: pd.DataFrame, upload_option: str) -> Tuple[bool, str]:
        """Validate that required columns exist"""
        required_cols = [self.entity_a_col, self.entity_b_col]
        missing_cols = [col for col in required_cols if col not in df.columns]
        
        if missing_cols:
            return False, f"Missing required columns: {', '.join(missing_cols)}"
        return True, "All required columns present"
    
    def validate_row_data(self, df: pd.DataFrame, upload_option: str, 
                         user_id: int) -> Tuple[List[Dict], List[Dict]]:
        """Validate relationship data"""
        errors = []
        valid_rows = []
        
        for idx, row in df.iterrows():
            row_num = idx + 2  # Excel row (1-based + header)
            row_errors = []
            
            # Get IDs
            entity_a_id = row.get(self.entity_a_col)
            entity_b_id = row.get(self.entity_b_col)
            
            # Validate Entity A
            if pd.isna(entity_a_id):
                row_errors.append({
                    "row": row_num,
                    "field": self.entity_a_col,
                    "message": f"{self.entity_a_name} ID is required",
                    "error_code": "REQUIRED_FIELD"
                })
            else:
                try:
                    entity_a_id = int(entity_a_id)
                    if not self._entity_exists(self.entity_a_table, entity_a_id):
                        row_errors.append({
                            "row": row_num,
                            "field": self.entity_a_col,
                            "message": f"{self.entity_a_name} with ID {entity_a_id} not found",
                            "error_code": "NOT_FOUND"
                        })
                except (ValueError, TypeError):
                    row_errors.append({
                        "row": row_num,
                        "field": self.entity_a_col,
                        "message": f"Invalid {self.entity_a_name} ID format",
                        "error_code": "INVALID_FORMAT"
                    })
            
            # Validate Entity B
            if pd.isna(entity_b_id):
                row_errors.append({
                    "row": row_num,
                    "field": self.entity_b_col,
                    "message": f"{self.entity_b_name} ID is required",
                    "error_code": "REQUIRED_FIELD"
                })
            else:
                try:
                    entity_b_id = int(entity_b_id)
                    if not self._entity_exists(self.entity_b_table, entity_b_id):
                        row_errors.append({
                            "row": row_num,
                            "field": self.entity_b_col,
                            "message": f"{self.entity_b_name} with ID {entity_b_id} not found",
                            "error_code": "NOT_FOUND"
                        })
                except (ValueError, TypeError):
                    row_errors.append({
                        "row": row_num,
                        "field": self.entity_b_col,
                        "message": f"Invalid {self.entity_b_name} ID format",
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
        """Apply column mappings if provided"""
        if not column_mappings:
            return df
        
        rename_dict = {}
        for excel_col, expected_field in column_mappings.items():
            if excel_col in df.columns:
                rename_dict[excel_col] = expected_field
        
        if rename_dict:
            df = df.rename(columns=rename_dict)
        
        return df
    
    def get_sheet_name(self, upload_option: str) -> None:
        """Return None for automatic sheet detection"""
        return None

