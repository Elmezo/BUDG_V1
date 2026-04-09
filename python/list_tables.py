import pymysql
import os

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USERNAME", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "database": os.getenv("DB_NAME", "project"),
    "charset": "utf8mb4"
}

def list_relationship_tables():
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cur:
            cur.execute("SHOW TABLES LIKE '%system_x_%'")
            print("System relationship tables:")
            for row in cur.fetchall():
                print(f"  {row[0]}")
            
            cur.execute("SHOW TABLES LIKE '%glossary_x_%'")
            print("\nGlossary relationship tables:")
            for row in cur.fetchall():
                print(f"  {row[0]}")
                
            cur.execute("SHOW TABLES LIKE '%dataset_x_%'")
            print("\nDataset relationship tables:")
            for row in cur.fetchall():
                print(f"  {row[0]}")
            
            # Check glossary_hierarchy
            cur.execute("SHOW TABLES LIKE 'glossary_hierarchy'")
            if cur.fetchall():
                print("\nFound glossary_hierarchy table")
            
            # Check for catitem tables
            cur.execute("SHOW TABLES LIKE '%catitem%'")
            print("\nCatItem tables:")
            for row in cur.fetchall():
                print(f"  {row[0]}")
                
    finally:
        conn.close()

if __name__ == "__main__":
    list_relationship_tables()
