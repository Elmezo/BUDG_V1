#!/usr/bin/env python3
"""
BUDG_V2 Bootstrap Installer
============================
Cross-platform (Linux / Windows) post-prerequisite installer.

Prerequisites (must already be installed):
  - Python 3.9+
  - Apache Tomcat
  - MySQL
  - ROOT.war built from the project

Usage example:
  python installer.py \
      --war ./target/ROOT.war \
      --tomcat-base /opt/tomcat \
      --app-base /opt/budg_v2 \
      --db-host 127.0.0.1 --db-port 3306 --db-name project \
      --db-user root --db-pass secret

Exit codes:
  0 - success
  1 - general failure
  2 - missing / invalid inputs
"""

from __future__ import annotations

import argparse
import datetime
import logging
import os
import platform
import secrets
import shutil
import subprocess
import sys
import textwrap
import uuid
from base64 import urlsafe_b64encode
from collections import OrderedDict
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# Logging setup
# ---------------------------------------------------------------------------
LOG_FMT = "[%(levelname)-5s] %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FMT)
log = logging.getLogger("budg-installer")

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
TIMESTAMP_FMT = "%Y%m%d_%H%M%S"
NOW_STR = datetime.datetime.now().strftime(TIMESTAMP_FMT)
IS_WINDOWS = platform.system() == "Windows"
IS_LINUX = platform.system() == "Linux"
SCRIPT_DIR = Path(__file__).resolve().parent

# ---------------------------------------------------------------------------
# OS-specific modules (only available on Unix)
# ---------------------------------------------------------------------------
_pwd_mod: Any = None
_grp_mod: Any = None
if not IS_WINDOWS:
    try:
        import grp as _grp_mod  # type: ignore[no-redef]
        import pwd as _pwd_mod  # type: ignore[no-redef]
    except ImportError:
        pass


# ===================================================================
#  UTILITY HELPERS
# ===================================================================

def _backup(path: Path) -> Optional[Path]:
    """Create a timestamped backup of *path* if it exists.  Return backup path or None."""
    if not path.exists():
        return None
    bak = path.with_name(f"{path.name}.bak.{NOW_STR}")
    shutil.copy2(str(path), str(bak))
    log.info("Backup: %s -> %s", path, bak.name)
    return bak


def _is_writable(directory: Path) -> bool:
    """Test write access by creating and removing a tiny temp file."""
    probe = directory / f".budg_probe_{uuid.uuid4().hex[:8]}"
    try:
        probe.write_text("probe", encoding="utf-8")
        probe.unlink()
        return True
    except OSError:
        return False


def _mask_secret(value: str) -> str:
    """Return first-4 … last-4 representation of a secret."""
    if len(value) <= 8:
        return "***"
    return f"{value[:4]}...{value[-4:]}"


def _resolve_template() -> Path:
    """Return the path to env.template shipped alongside this script."""
    candidate = SCRIPT_DIR / "env.template"
    if candidate.is_file():
        return candidate
    # Fallback: look inside scripts/ subfolder
    candidate = SCRIPT_DIR / "scripts" / "env.template"
    if candidate.is_file():
        return candidate
    return SCRIPT_DIR / "env.template"  # will be caught later


# ===================================================================
#  STEP 1 - RESOLVE PATHS
# ===================================================================

def resolve_paths(args: argparse.Namespace) -> Dict[str, Path]:
    """Determine all base and data directory paths.  Returns a dict."""

    # --- app_base ---
    if args.app_base:
        app_base = Path(args.app_base).resolve()
    elif IS_LINUX:
        app_base = Path("/opt/budg_v2")
    else:
        prog_data = os.environ.get("ProgramData")
        if prog_data:
            app_base = Path(prog_data) / "budg_v2"
        else:
            app_base = Path("C:/budg_v2")

    # --- tomcat_base ---
    if args.tomcat_base:
        tomcat_base = Path(args.tomcat_base).resolve()
    else:
        for env_key in ("CATALINA_BASE", "CATALINA_HOME"):
            val = os.environ.get(env_key)
            if val:
                tomcat_base = Path(val).resolve()
                log.info("Detected tomcat_base from $%s = %s", env_key, tomcat_base)
                break
        else:
            log.error("Cannot determine Tomcat location.  Pass --tomcat-base or set CATALINA_BASE / CATALINA_HOME.")
            sys.exit(2)

    paths: Dict[str, Path] = {
        "app_base":               app_base,
        "tomcat_base":            tomcat_base,
        "LOG_DIR":                app_base / "logs",
        "UPLOAD_DIR":             app_base / "uploads" / "documents",
        "DATA_DIR":               app_base / "data",
        "BPMN_DIR":               app_base / "data" / "bpmn",
        "DEFAULT_WORKFLOWS_DIR":  app_base / "data" / "default-workflows",
        "BULK_DIR":               app_base / "bulk",
        "LDAP_TMP_DIR":           app_base / "tmp" / "ldap" / "tmp",
        "LDAP_LOCK_DIR":          app_base / "tmp" / "ldap" / "lock",
    }

    log.info("app_base    = %s", app_base)
    log.info("tomcat_base = %s", tomcat_base)
    return paths


# ===================================================================
#  STEP 2 - ENSURE DIRECTORIES
# ===================================================================

def ensure_dirs(
    paths: Dict[str, Path],
    run_user: Optional[str] = None,
    run_group: Optional[str] = None,
) -> List[Tuple[str, Path, bool, bool]]:
    """Create directories and verify they are writable.

    Returns list of (label, path, was_created, is_writable).
    """
    dir_keys = [
        "app_base", "LOG_DIR", "UPLOAD_DIR", "DATA_DIR",
        "BPMN_DIR", "DEFAULT_WORKFLOWS_DIR", "BULK_DIR",
        "LDAP_TMP_DIR", "LDAP_LOCK_DIR",
    ]

    results: List[Tuple[str, Path, bool, bool]] = []
    for key in dir_keys:
        d = paths[key]
        existed = d.is_dir()
        try:
            d.mkdir(parents=True, exist_ok=True)
        except OSError as exc:
            log.error("Cannot create %s (%s): %s", key, d, exc)
            results.append((key, d, False, False))
            continue

        writable = _is_writable(d)
        results.append((key, d, not existed, writable))

        if writable:
            log.info("  %-25s  %s  [OK]", key, d)
        else:
            log.warning("  %-25s  %s  [NOT WRITABLE]", key, d)

    # --- ownership (Linux only) ---
    if IS_LINUX and run_user:
        if os.geteuid() == 0:
            try:
                uid = _pwd_mod.getpwnam(run_user).pw_uid
                gid = _grp_mod.getgrnam(run_group or run_user).gr_gid
            except KeyError as exc:
                log.error("User/group lookup failed: %s", exc)
                return results
            for key in dir_keys:
                d = paths[key]
                _chown_recursive(d, uid, gid)
            log.info("Ownership set to %s:%s", run_user, run_group or run_user)
        else:
            log.warning(
                "--run-user given but script is not running as root.  "
                "Run with sudo to change ownership."
            )

    return results


def _chown_recursive(path: Path, uid: int, gid: int) -> None:
    """chown -R equivalent."""
    os.chown(str(path), uid, gid)
    for root, dirs, files in os.walk(str(path)):
        for name in dirs + files:
            try:
                os.chown(os.path.join(root, name), uid, gid)
            except OSError:
                pass


# ===================================================================
#  STEP 3 - DEPLOY WAR
# ===================================================================

def deploy_war(args: argparse.Namespace, paths: Dict[str, Path]) -> Dict[str, Any]:
    """Copy ROOT.war into Tomcat webapps/.  Returns status dict."""
    result: Dict[str, Any] = {"deployed": False, "backup": None, "cleaned_root": False}

    # --- locate source WAR ---
    if args.war:
        war_src = Path(args.war).resolve()
    else:
        war_src = SCRIPT_DIR / "target" / "ROOT.war"

    if not war_src.is_file():
        log.error("WAR file not found: %s", war_src)
        sys.exit(2)

    webapps = paths["tomcat_base"] / "webapps"
    if not webapps.is_dir():
        log.error("Tomcat webapps dir not found: %s", webapps)
        sys.exit(1)

    dest = webapps / "ROOT.war"

    # --- backup existing ---
    bak = _backup(dest)
    result["backup"] = str(bak) if bak else None

    # --- optional: remove exploded ROOT dir ---
    root_dir = webapps / "ROOT"
    if args.clean_root_dir and root_dir.is_dir():
        shutil.rmtree(str(root_dir))
        log.info("Removed exploded directory: %s", root_dir)
        result["cleaned_root"] = True

    # --- copy ---
    shutil.copy2(str(war_src), str(dest))
    log.info("Deployed %s -> %s", war_src.name, dest)
    result["deployed"] = True
    result["war_src"] = str(war_src)
    result["war_dest"] = str(dest)
    return result


# ===================================================================
#  STEP 4 + 5 - WRITE .env  (with JWT generation)
# ===================================================================

def _parse_env_file(path: Path) -> Tuple[OrderedDict, List[str]]:
    """Parse a .env file into (key->value ordered dict, raw_lines).

    Comments and blank lines are preserved in raw_lines for later merge.
    """
    kv: OrderedDict = OrderedDict()
    raw_lines: List[str] = []
    if not path.is_file():
        return kv, raw_lines
    for line in path.read_text(encoding="utf-8").splitlines():
        raw_lines.append(line)
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if "=" not in stripped:
            continue
        key, _, value = stripped.partition("=")
        key = key.strip()
        value = value.strip()
        # Remove surrounding quotes
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
            value = value[1:-1]
        kv[key] = value
    return kv, raw_lines


def generate_jwt_secret() -> str:
    """Generate a secure 64-byte random token (base64url, no padding)."""
    raw = secrets.token_bytes(64)
    token = urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")
    log.info("Generated new JWT_SECRET_KEY (%s)", _mask_secret(token))
    return token


def write_env(args: argparse.Namespace, paths: Dict[str, Path]) -> Dict[str, Any]:
    """Create or update .env inside app_base.  Idempotent merge."""
    result: Dict[str, Any] = {"created": False, "merged": False, "jwt_generated": False, "path": None}

    env_path = paths["app_base"] / ".env"
    result["path"] = str(env_path)

    # --- Load existing .env (if any) ---
    existing_kv, existing_lines = _parse_env_file(env_path)

    # --- Load template ---
    template_path = _resolve_template()
    if template_path.is_file():
        template_kv, template_lines = _parse_env_file(template_path)
    else:
        log.warning("env.template not found at %s; using built-in defaults", template_path)
        template_kv = OrderedDict()
        template_lines = []

    # --- Build desired values from CLI args / computed paths ---
    if args.db_url:
        db_url = args.db_url
    else:
        db_url = f"jdbc:mysql://{args.db_host}:{args.db_port}/{args.db_name}"

    desired: Dict[str, str] = {
        "DB_URL":               db_url,
        "DB_USERNAME":          args.db_user,
        "DB_PASSWORD":          args.db_pass,
        "LOG_DIR":              str(paths["LOG_DIR"]),
        "document.upload.dir":  str(paths["UPLOAD_DIR"]),
        "bulk.template.path":   str(paths["BULK_DIR"]),
    }

    # --- JWT_SECRET_KEY: generate only if missing / empty ---
    existing_jwt = existing_kv.get("JWT_SECRET_KEY", "").strip()
    if existing_jwt and existing_jwt != "your-very-long-random-secret-key-at-least-512-bits-long":
        desired["JWT_SECRET_KEY"] = existing_jwt
        log.info("Keeping existing JWT_SECRET_KEY (%s)", _mask_secret(existing_jwt))
    else:
        desired["JWT_SECRET_KEY"] = generate_jwt_secret()
        result["jwt_generated"] = True

    # --- Merge logic ---
    if env_path.is_file():
        # Backup then merge: keep existing keys, add missing from template + desired
        _backup(env_path)
        result["merged"] = True

        # Overlay desired values onto existing (only fill blanks for non-override keys)
        merged_kv = OrderedDict(existing_kv)
        # Always update these from CLI (user explicitly provided them)
        for k in ("DB_URL", "DB_USERNAME", "DB_PASSWORD"):
            merged_kv[k] = desired[k]
        # For path keys: set only if missing or empty
        for k in ("LOG_DIR", "document.upload.dir", "bulk.template.path", "JWT_SECRET_KEY"):
            if not merged_kv.get(k, "").strip() or k == "JWT_SECRET_KEY":
                merged_kv[k] = desired[k]
        # For template keys: add only if completely absent
        for k, v in template_kv.items():
            if k not in merged_kv:
                merged_kv[k] = v

        _write_env_file(env_path, merged_kv, existing_lines)
    else:
        # Fresh creation from template
        result["created"] = True
        merged_kv = OrderedDict(template_kv)
        merged_kv.update(desired)
        _write_env_file(env_path, merged_kv, template_lines)

    log.info(".env written to %s", env_path)
    return result


def _write_env_file(path: Path, kv: OrderedDict, base_lines: List[str]) -> None:
    """Write .env preserving comments / structure from base_lines, updating values."""
    written_keys: set = set()
    out_lines: List[str] = []

    for line in base_lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            out_lines.append(line)
            continue
        if "=" not in stripped:
            out_lines.append(line)
            continue
        key, _, _ = stripped.partition("=")
        key = key.strip()
        if key in kv:
            out_lines.append(f"{key}={kv[key]}")
            written_keys.add(key)
        else:
            out_lines.append(line)
            written_keys.add(key)

    # Append any keys not yet written
    remaining = [(k, v) for k, v in kv.items() if k not in written_keys]
    if remaining:
        out_lines.append("")
        out_lines.append("# --- Additional keys (auto-added by installer) ---")
        for k, v in remaining:
            out_lines.append(f"{k}={v}")

    path.write_text("\n".join(out_lines) + "\n", encoding="utf-8")


# ===================================================================
#  STEP 6 - CONFIGURE TOMCAT
# ===================================================================

def _build_catalina_opts(paths: Dict[str, Path]) -> str:
    """Build the CATALINA_OPTS string with all required -D flags."""
    env_path = paths["app_base"] / ".env"
    props = [
        f"-DLOG_DIR={paths['LOG_DIR']}",
        f"-Ddocument.upload.dir={paths['UPLOAD_DIR']}",
        f"-Dbulk.template.path={paths['BULK_DIR']}",
        f"-Dbudg.env.file={env_path}",
    ]
    return " ".join(props)


def configure_tomcat(args: argparse.Namespace, paths: Dict[str, Path]) -> Dict[str, Any]:
    """Set up Tomcat to read the required JVM properties.  Returns status dict."""
    result: Dict[str, Any] = {
        "catalina_opts": _build_catalina_opts(paths),
        "systemd_override": None,
        "setenv_written": False,
    }

    if IS_LINUX:
        result.update(_configure_linux_systemd(args, paths, result["catalina_opts"]))
    else:
        result.update(_configure_windows_setenv(args, paths, result["catalina_opts"]))

    # --- Optional restart ---
    if args.restart_tomcat:
        _restart_tomcat(args)

    return result


# --- Linux: systemd drop-in override ---

def _configure_linux_systemd(
    args: argparse.Namespace,
    paths: Dict[str, Path],
    catalina_opts: str,
) -> Dict[str, Any]:
    """Create a systemd drop-in override for the Tomcat service."""
    result: Dict[str, Any] = {"systemd_override": None}
    service = args.service_name or "tomcat"
    override_dir = Path(f"/etc/systemd/system/{service}.service.d")
    override_file = override_dir / "budg_override.conf"
    env_path = paths["app_base"] / ".env"

    content = textwrap.dedent(f"""\
        # Auto-generated by BUDG installer ({NOW_STR})
        # Do not edit manually; re-run installer to update.
        [Service]
        Environment="CATALINA_OPTS={catalina_opts}"
        EnvironmentFile={env_path}
    """)

    if os.geteuid() == 0:
        try:
            override_dir.mkdir(parents=True, exist_ok=True)
            _backup(override_file)
            override_file.write_text(content, encoding="utf-8")
            result["systemd_override"] = str(override_file)
            log.info("systemd override written: %s", override_file)
            log.info("Run: sudo systemctl daemon-reload && sudo systemctl restart %s", service)
        except OSError as exc:
            log.error("Failed to write systemd override: %s", exc)
    else:
        log.warning(
            "Not running as root -- cannot write systemd override.\n"
            "  Manually create %s with:\n%s",
            override_file,
            textwrap.indent(content, "    "),
        )

    return result


# --- Windows: setenv.bat ---

def _configure_windows_setenv(
    args: argparse.Namespace,
    paths: Dict[str, Path],
    catalina_opts: str,
) -> Dict[str, Any]:
    """Write or print instructions for setenv.bat on Windows."""
    result: Dict[str, Any] = {"setenv_written": False}
    setenv_path = paths["tomcat_base"] / "bin" / "setenv.bat"
    env_path = paths["app_base"] / ".env"

    bat_content = _build_setenv_bat(catalina_opts, env_path)

    if args.write_setenv:
        _backup(setenv_path)
        setenv_path.write_text(bat_content, encoding="utf-8")
        result["setenv_written"] = True
        log.info("setenv.bat written: %s", setenv_path)
    else:
        log.info(
            "To configure Tomcat on Windows, create/update:\n  %s\n\nSuggested content:\n%s",
            setenv_path,
            textwrap.indent(bat_content, "    "),
        )

    return result


def _build_setenv_bat(catalina_opts: str, env_path: Path) -> str:
    """Generate setenv.bat content."""
    lines = [
        "@echo off",
        f"rem Auto-generated by BUDG installer ({NOW_STR})",
        "rem Do not edit manually; re-run installer to update.",
        "",
        f'set "CATALINA_OPTS=%CATALINA_OPTS% {catalina_opts}"',
        "",
        "rem Load .env variables into environment",
        f'for /F "usebackq tokens=1,* delims==" %%A in ("{env_path}") do (',
        '    if not "%%A"=="" if not "%%~A:~0,1%"=="#" (',
        '        set "%%A=%%B"',
        "    )",
        ")",
        "",
    ]
    return "\r\n".join(lines)


def _ensure_elasticsearch_single_node_safe() -> None:
    """
    On Linux, if Elasticsearch is configured single-node, remove cluster.initial_master_nodes
    (forbidden together with single-node). Idempotent; only touches file if present and needed.
    """
    if not IS_LINUX:
        return
    yml_path = Path("/etc/elasticsearch/elasticsearch.yml")
    if not yml_path.is_file():
        return
    try:
        content = yml_path.read_text(encoding="utf-8")
    except OSError as exc:
        log.warning("Could not read %s: %s", yml_path, exc)
        return
    # Check if single-node is set (active or commented)
    has_single_node = "single-node" in content and "discovery.type" in content
    if not has_single_node:
        return
    lines = content.splitlines()
    new_lines: List[str] = []
    changed = False
    for line in lines:
        stripped = line.strip()
        if stripped.lstrip("# ").startswith("cluster.initial_master_nodes"):
            changed = True
            continue
        new_lines.append(line)
    if not changed:
        return
    if os.geteuid() != 0:
        log.warning(
            "Elasticsearch has single-node but also cluster.initial_master_nodes (invalid). "
            "Run as root to auto-fix, or edit %s and remove cluster.initial_master_nodes.",
            yml_path,
        )
        return
    _backup(yml_path)
    yml_path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
    log.info("Elasticsearch: removed cluster.initial_master_nodes from %s (incompatible with single-node)", yml_path)


def _restart_tomcat(args: argparse.Namespace) -> None:
    """Attempt to restart Tomcat (best-effort)."""
    if IS_LINUX:
        service = args.service_name or "tomcat"
        if os.geteuid() == 0:
            log.info("Restarting Tomcat via systemctl ...")
            subprocess.run(["systemctl", "daemon-reload"], check=False)
            subprocess.run(["systemctl", "restart", service], check=False)
        else:
            log.warning("Cannot restart Tomcat without root.  Run: sudo systemctl restart %s", service)
    else:
        log.info("On Windows, restart Tomcat manually or via services.msc.")


# ===================================================================
#  STEP 7 - INSTALL REPORT
# ===================================================================

def write_report(
    paths: Dict[str, Path],
    dir_results: List[Tuple[str, Path, bool, bool]],
    war_result: Dict[str, Any],
    env_result: Dict[str, Any],
    tomcat_result: Dict[str, Any],
) -> Path:
    """Write INSTALL_REPORT.txt inside app_base.  Returns the report path."""
    report_path = paths["app_base"] / "INSTALL_REPORT.txt"
    env_path = paths["app_base"] / ".env"
    service_name = tomcat_result.get("service_name", "tomcat")

    lines: List[str] = []
    _h = lines.append   # shortcut

    _h("=" * 70)
    _h("  BUDG_V2 - INSTALL REPORT")
    _h("=" * 70)
    _h("")
    _h(f"  Generated : {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    _h(f"  OS        : {platform.platform()}")
    _h(f"  Python    : {platform.python_version()}")
    _h(f"  Installer : {Path(__file__).resolve()}")
    _h("")

    # --- Paths ---
    _h("-" * 70)
    _h("  BASE PATHS")
    _h("-" * 70)
    _h(f"  tomcat_base : {paths['tomcat_base']}")
    _h(f"  app_base    : {paths['app_base']}")
    _h("")

    # --- Directories ---
    _h("-" * 70)
    _h("  DIRECTORIES")
    _h("-" * 70)
    for label, dpath, created, writable in dir_results:
        status = "OK" if writable else "NOT WRITABLE"
        note = " (created)" if created else " (existed)"
        _h(f"  [{status:>12}] {label:25s} {dpath}{note}")
    _h("")

    # --- .env ---
    _h("-" * 70)
    _h("  ENVIRONMENT FILE (.env)")
    _h("-" * 70)
    _h(f"  Location  : {env_result.get('path', 'N/A')}")
    if env_result.get("created"):
        _h("  Status    : Created (new)")
    elif env_result.get("merged"):
        _h("  Status    : Merged (existing keys preserved)")
    if env_result.get("jwt_generated"):
        _h("  JWT_SECRET_KEY : newly generated")
    else:
        _h("  JWT_SECRET_KEY : kept existing")

    # List keys (masked)
    if env_path.is_file():
        _h("")
        _h("  Keys present:")
        kv, _ = _parse_env_file(env_path)
        for k, v in kv.items():
            if k in ("JWT_SECRET_KEY", "DB_PASSWORD"):
                _h(f"    {k} = {_mask_secret(v)}")
            else:
                _h(f"    {k} = {v}")
    _h("")

    # --- WAR ---
    _h("-" * 70)
    _h("  WAR DEPLOYMENT")
    _h("-" * 70)
    if war_result.get("deployed"):
        _h(f"  Source   : {war_result.get('war_src', 'N/A')}")
        _h(f"  Deployed : {war_result.get('war_dest', 'N/A')}")
        if war_result.get("backup"):
            _h(f"  Backup   : {war_result['backup']}")
        if war_result.get("cleaned_root"):
            _h("  Exploded ROOT dir removed: yes")
    else:
        _h("  WAR was NOT deployed (error above).")
    _h("")

    # --- JVM Properties ---
    catalina_opts = tomcat_result.get("catalina_opts", "")
    _h("-" * 70)
    _h("  JVM SYSTEM PROPERTIES (CATALINA_OPTS)")
    _h("-" * 70)
    _h("")
    _h("  Copy-paste for manual setup:")
    _h("")
    _h(f"    {catalina_opts}")
    _h("")
    if tomcat_result.get("systemd_override"):
        _h(f"  systemd override written: {tomcat_result['systemd_override']}")
    if tomcat_result.get("setenv_written"):
        _h(f"  setenv.bat written inside: {paths['tomcat_base'] / 'bin' / 'setenv.bat'}")
    _h("")

    # --- Restart instructions ---
    _h("-" * 70)
    _h("  HOW TO START / RESTART TOMCAT")
    _h("-" * 70)
    _h("")
    _h("  Linux (systemd):")
    _h(f"    sudo systemctl daemon-reload")
    _h(f"    sudo systemctl restart {service_name}")
    _h(f"    sudo systemctl status  {service_name}")
    _h("")
    _h("  Linux (manual):")
    _h(f"    export CATALINA_OPTS=\"{catalina_opts}\"")
    _h(f"    {paths['tomcat_base']}/bin/shutdown.sh")
    _h(f"    {paths['tomcat_base']}/bin/startup.sh")
    _h("")
    _h("  Windows (service):")
    _h("    net stop Tomcat11 && net start Tomcat11")
    _h("")
    _h("  Windows (manual):")
    _h(f"    {paths['tomcat_base']}\\bin\\shutdown.bat")
    _h(f"    {paths['tomcat_base']}\\bin\\startup.bat")
    _h("")

    # --- Troubleshooting ---
    _h("-" * 70)
    _h("  TROUBLESHOOTING")
    _h("-" * 70)
    _h(textwrap.dedent("""\
      1. Permission denied on directories
         - Linux: re-run installer with sudo and --run-user tomcat
         - Windows: run Command Prompt / PowerShell as Administrator

      2. Logs not written / prod_errors.log missing
         - Verify LOG_DIR value matches the -DLOG_DIR in CATALINA_OPTS
         - Ensure the Tomcat process user can write to LOG_DIR

      3. .env not loaded by the application
         - Check that EnvironmentFile or -Dbudg.env.file points to the
           correct .env path
         - Ensure .env is readable by the Tomcat process user

      4. WAR not deploying
         - Confirm Tomcat autoDeploy is enabled (server.xml)
         - Check Tomcat logs: catalina.out / localhost.log

      5. Wrong tomcat_base
         - Pass --tomcat-base explicitly or set CATALINA_BASE env var

      6. Bulk/upload AccessDeniedException (bulk, uploads, logs)
         - Linux: re-run installer with sudo and --run-user tomcat so app_base
           (bulk, uploads, logs) is owned by the Tomcat/Python user
         - Windows: run Tomcat as the same user that ran the installer, or grant
           that user write access to app_base (e.g. %%ProgramData%%\\budg_v2)
    """))

    _h("=" * 70)
    _h("  END OF REPORT")
    _h("=" * 70)

    report_text = "\n".join(lines)
    report_path.write_text(report_text, encoding="utf-8")
    log.info("Report written: %s", report_path)
    return report_path


# ===================================================================
#  STEP 8 - COPY REPORT TO DESKTOP
# ===================================================================

def copy_report_to_desktop(report_path: Path) -> bool:
    """Best-effort copy of the report to the user's Desktop."""
    desktop: Optional[Path] = None

    if IS_WINDOWS:
        # Try USERPROFILE\Desktop first
        profile = os.environ.get("USERPROFILE")
        if profile:
            candidate = Path(profile) / "Desktop"
            if candidate.is_dir():
                desktop = candidate
        # Fallback: Home / Desktop
        if desktop is None:
            candidate = Path.home() / "Desktop"
            if candidate.is_dir():
                desktop = candidate
    else:
        candidate = Path.home() / "Desktop"
        if candidate.is_dir():
            desktop = candidate

    if desktop is None:
        log.info("Desktop folder not found; skipping desktop copy.")
        return False

    dest = desktop / report_path.name
    try:
        shutil.copy2(str(report_path), str(dest))
        log.info("Report copied to Desktop: %s", dest)
        return True
    except OSError as exc:
        log.warning("Could not copy report to Desktop: %s", exc)
        return False


# ===================================================================
#  ARGUMENT PARSER
# ===================================================================

def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="BUDG_V2 Bootstrap Installer — prepares the deployment environment.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent("""\
            Examples:
              python installer.py --war ./target/ROOT.war --tomcat-base /opt/tomcat
              python installer.py --tomcat-base C:\\Tomcat --write-setenv
              python installer.py --tomcat-base /opt/tomcat --service-name tomcat --run-user tomcat
        """),
    )

    # Paths
    p.add_argument("--war", default=None, help="Path to ROOT.war (default: target/ROOT.war next to this script)")
    p.add_argument("--tomcat-base", default=None, dest="tomcat_base", help="Tomcat base directory (or set CATALINA_BASE)")
    p.add_argument("--app-base", default=None, dest="app_base", help="Application data root (default: /opt/budg_v2 or %%ProgramData%%\\budg_v2)")

    # Database
    p.add_argument("--db-host", default="127.0.0.1", dest="db_host")
    p.add_argument("--db-port", default="3306", dest="db_port")
    p.add_argument("--db-name", default="project", dest="db_name")
    p.add_argument("--db-user", default="root", dest="db_user")
    p.add_argument("--db-pass", default="", dest="db_pass")
    p.add_argument("--db-url", default=None, dest="db_url", help="Full JDBC URL (overrides --db-host/port/name)")

    # Linux ownership
    p.add_argument("--run-user", default=None, dest="run_user", help="(Linux) user to own app directories")
    p.add_argument("--run-group", default=None, dest="run_group", help="(Linux) group (default: same as --run-user)")

    # Tomcat config
    p.add_argument("--service-name", default="tomcat", dest="service_name", help="systemd service name (default: tomcat)")
    p.add_argument("--write-setenv", action="store_true", dest="write_setenv", help="(Windows) write setenv.bat automatically")
    p.add_argument("--clean-root-dir", action="store_true", dest="clean_root_dir", help="Remove exploded ROOT/ dir before deploying WAR")
    p.add_argument("--restart-tomcat", action="store_true", dest="restart_tomcat", help="Attempt to restart Tomcat after configuration")

    # Verbosity
    p.add_argument("-v", "--verbose", action="store_true", help="Enable DEBUG logging")

    return p.parse_args(argv)


# ===================================================================
#  MAIN
# ===================================================================

def main() -> int:
    args = parse_args()
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    log.info("=" * 60)
    log.info("  BUDG_V2 Bootstrap Installer")
    log.info("  %s | Python %s", platform.platform(), platform.python_version())
    log.info("=" * 60)

    # Step 1 - Resolve paths
    log.info("")
    log.info("[Step 1/8] Resolving paths ...")
    paths = resolve_paths(args)

    # Step 2 - Ensure directories
    log.info("")
    log.info("[Step 2/8] Creating directories ...")
    dir_results = ensure_dirs(paths, args.run_user, args.run_group)
    # Fail if app_base is not writable
    for label, dpath, _, writable in dir_results:
        if label == "app_base" and not writable:
            log.error("FATAL: app_base (%s) is not writable. Aborting.", dpath)
            return 1

    # Step 3 - Deploy WAR
    log.info("")
    log.info("[Step 3/8] Deploying WAR ...")
    war_result = deploy_war(args, paths)

    # Step 4+5 - Write .env (includes JWT generation)
    log.info("")
    log.info("[Step 4/8] Writing .env ...")
    env_result = write_env(args, paths)

    # Step 6 - Configure Tomcat
    log.info("")
    log.info("[Step 6/8] Configuring Tomcat ...")
    tomcat_result = configure_tomcat(args, paths)
    tomcat_result["service_name"] = args.service_name

    # Optional: ensure Elasticsearch single-node config is valid (no cluster.initial_master_nodes)
    _ensure_elasticsearch_single_node_safe()

    # Step 7 - Write report
    log.info("")
    log.info("[Step 7/8] Writing install report ...")
    report_path = write_report(paths, dir_results, war_result, env_result, tomcat_result)

    # Step 8 - Copy report to Desktop
    log.info("")
    log.info("[Step 8/8] Copying report to Desktop ...")
    copy_report_to_desktop(report_path)

    # --- Summary ---
    log.info("")
    log.info("=" * 60)
    log.info("  Installation complete!")
    log.info("  Report : %s", report_path)
    log.info("=" * 60)

    # Check for any non-writable dirs
    non_writable = [(l, p) for l, p, _, w in dir_results if not w]
    if non_writable:
        log.warning("Some directories are NOT writable:")
        for label, dpath in non_writable:
            log.warning("  %s -> %s", label, dpath)
        log.warning("Fix permissions before starting Tomcat.")
        return 1

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        log.info("Interrupted.")
        sys.exit(130)
    except Exception as exc:
        log.error("Unexpected error: %s", exc, exc_info=True)
        sys.exit(1)
