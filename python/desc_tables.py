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

def desc_tables():
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cur:
            for table in ['glossary_x_system', 'glossary_x_glossary', 'system', 'glossary']:
                print(f"\nColumns for {table}:")
                cur.execute(f"DESCRIBE {table}")
                for row in cur.fetchall():
                    print(f"  {row[0]} ({row[1]})")
    finally:
        conn.close()

if __name__ == "__main__":
    desc_tables()
