"""
Shared MySQL settings for Python bulk validators — align with Java DatabaseConnection.

Java uses DB_URL (jdbc:mysql://host:port/database) and DB_USERNAME / DB_PASSWORD.
JDBC host/port/database default is read from DatabaseConnection.java first so Python
validators follow the same embedded URL as Tomcat; repo .env DB_URL is only used if
the Java file has no default or is missing.
"""

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

_LOADED_MARK = "_BUDG_DOTENV_LOADED"


def _load_root_dotenv() -> None:
    if os.environ.get(_LOADED_MARK):
        return
    try:
        here = Path(__file__).resolve().parent
        candidates = (here.parent.parent / ".env", here.parent / ".env")
        for env_path in candidates:
            if not env_path.is_file():
                continue
            with open(env_path, encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    key, _, val = line.partition("=")
                    key = key.strip()
                    val = val.strip().strip('"').strip("'")
                    if key and key not in os.environ:
                        os.environ[key] = val
            break
    except OSError:
        pass
    os.environ[_LOADED_MARK] = "1"


def _repo_root() -> Path:
    """python/utils/budg_db_config.py → repo root (BUDG_V2)."""
    return Path(__file__).resolve().parent.parent.parent


def read_default_db_url_from_java() -> str:
    """
    Default jdbc URL string from the resolveEnv("DB_URL", "...") line in
    DatabaseConnection.java. Used before os.environ DB_URL so a stale .env localhost
    does not override the IP you set in Java.
    """
    java_file = (
        _repo_root()
        / "src"
        / "main"
        / "java"
        / "com"
        / "example"
        / "budg_v2"
        / "database"
        / "DatabaseConnection.java"
    )
    if not java_file.is_file():
        return ""
    try:
        text = java_file.read_text(encoding="utf-8")
    except OSError:
        return ""
    pat = r'resolveEnv\s*\(\s*"DB_URL"\s*,\s*"((?:\\.|[^"\\])*)"\s*\)'
    m = re.search(pat, text)
    if not m:
        return ""
    raw = m.group(1).replace(r"\"", '"').replace(r"\\", "\\")
    return raw


def parse_jdbc_mysql_url(url: str) -> Optional[Tuple[str, int, str]]:
    """
    Parse jdbc:mysql:// or jdbc:mariadb:// URLs used by the Java app.
    Returns (host, port, database) or None if not a supported URL.
    """
    if not url or not str(url).strip():
        return None
    u = str(url).strip()
    m = re.match(
        r"jdbc:(?:mysql|mariadb)://([^:/?]+)(?::(\d+))?(?:/([^?]*))?",
        u,
        re.IGNORECASE,
    )
    if not m:
        return None
    host = m.group(1).strip()
    port = int(m.group(2)) if m.group(2) else 3306
    db_part = m.group(3)
    database = db_part.strip() if db_part else ""
    if not database:
        database = os.getenv("DB_NAME", "project")
    return host, port, database


def get_pymysql_config() -> Dict[str, Any]:
    """
    Build a pymysql.connect(**kwargs) dict aligned with Java DatabaseConnection.

    JDBC URL precedence: (1) default string parsed from DatabaseConnection.java if present,
    (2) DB_URL from environment / .env. That matches “change IP in Java → Python follows”.

    DB_USERNAME / DB_PASSWORD still come from env / .env only (Java defaults for those
    are the usual root / empty; set in .env if you differ).
    """
    _load_root_dotenv()

    java_url = (read_default_db_url_from_java() or "").strip()
    env_url = (os.getenv("DB_URL") or "").strip()
    url = java_url if java_url else env_url

    if url:
        parsed = parse_jdbc_mysql_url(url)
        if parsed:
            host, port, database = parsed
        else:
            host = os.getenv("DB_HOST", "localhost")
            port = int(os.getenv("DB_PORT", "3306"))
            database = os.getenv("DB_NAME", "project")
    else:
        host = os.getenv("DB_HOST", "localhost")
        port = int(os.getenv("DB_PORT", "3306"))
        database = os.getenv("DB_NAME", "project")

    return {
        "host": host,
        "port": port,
        "user": os.getenv("DB_USERNAME", "root"),
        "password": os.getenv("DB_PASSWORD", ""),
        "database": database,
        "charset": "utf8mb4",
    }


# Eager load .env once on import so getenv works before first connection
_load_root_dotenv()
