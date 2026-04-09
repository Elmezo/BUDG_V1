#!/usr/bin/env python3
"""
BUDG_V2 Server Prerequisites Installer
========================================
Cross-platform (Linux / Windows) script that installs and configures
all server-side prerequisites, then prepares the application for first run.

Prerequisites for running THIS script:
  - Python 3.9+
  - Internet access (or --offline-dir with pre-downloaded packages)
  - Root/sudo on Linux, Administrator on Windows

What it installs / configures:
  1. Java 17 (OpenJDK / Adoptium Temurin)
  2. MariaDB (latest LTS); on Linux sets lower_case_table_names=1 before DB creation/import
  3. Elasticsearch 8.14.3
  4. Apache Tomcat 11
  5. Imports project.sql into MariaDB
  6. Deploys ROOT.war to Tomcat

After this script finishes, run installer.py to configure .env and JVM props.

Exit codes:
  0 - all components installed successfully
  1 - one or more components failed (check report)
  2 - missing inputs or insufficient permissions
"""

from __future__ import annotations

import argparse
import ctypes
import datetime
import hashlib
import json
import logging
import os
import platform
import secrets
import shutil
import subprocess
import sys
import tarfile
import textwrap
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
LOG_FMT = "[%(levelname)-5s] %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FMT, stream=sys.stdout)
log = logging.getLogger("budg-setup")

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
TIMESTAMP_FMT = "%Y%m%d_%H%M%S"
NOW_STR = datetime.datetime.now().strftime(TIMESTAMP_FMT)
IS_WINDOWS = platform.system() == "Windows"
IS_LINUX = platform.system() == "Linux"
SCRIPT_DIR = Path(__file__).resolve().parent

# Default software versions (Tomcat: use 11.0.6+ on dlcdn; 11.0.2 on archive)
DEFAULT_TOMCAT_VERSION = "11.0.6"
DEFAULT_ES_VERSION = "8.14.3"
DEFAULT_MARIADB_VERSION = "11.4.4"

# Download URL templates (archive fallback when dlcdn returns 404)
TOMCAT_URL_LINUX = (
    "https://dlcdn.apache.org/tomcat/tomcat-{major}/v{ver}/bin/apache-tomcat-{ver}.tar.gz"
)
TOMCAT_URL_LINUX_ARCHIVE = (
    "https://archive.apache.org/dist/tomcat/tomcat-{major}/v{ver}/bin/apache-tomcat-{ver}.tar.gz"
)
TOMCAT_URL_WIN = (
    "https://dlcdn.apache.org/tomcat/tomcat-{major}/v{ver}/bin/apache-tomcat-{ver}-windows-x64.zip"
)
TOMCAT_URL_WIN_ARCHIVE = (
    "https://archive.apache.org/dist/tomcat/tomcat-{major}/v{ver}/bin/apache-tomcat-{ver}-windows-x64.zip"
)
ES_URL_LINUX = (
    "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-{ver}-linux-x86_64.tar.gz"
)
ES_URL_WIN = (
    "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-{ver}-windows-x86_64.zip"
)
ADOPTIUM_API_WIN = (
    "https://api.adoptium.net/v3/binary/latest/{major}/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
)
MARIADB_MSI_URL = (
    "https://archive.mariadb.org/mariadb-{ver}/winx64-packages/mariadb-{ver}-winx64.msi"
)

# MariaDB Linux repo templates
MARIADB_APT_KEY_URL = "https://mariadb.org/mariadb_release_signing_key.pgp"
MARIADB_APT_REPO = (
    "deb [signed-by=/etc/apt/keyrings/mariadb-keyring.pgp] "
    "https://dlm.mariadb.com/repo/mariadb-server/{minor_ver}/repo/{distro_id} {codename} main"
)
MARIADB_YUM_REPO = textwrap.dedent("""\
    [mariadb]
    name = MariaDB
    baseurl = https://dlm.mariadb.com/repo/mariadb-server/{minor_ver}/yum/rhel/$releasever/$basearch
    gpgkey = https://mariadb.org/mariadb_release_signing_key.pgp
    gpgcheck = 1
    enabled = 1
""")

# Elasticsearch Linux repo
ES_APT_KEY_URL = "https://artifacts.elastic.co/GPG-KEY-elasticsearch"
ES_APT_REPO = (
    "deb [signed-by=/usr/share/keyrings/elasticsearch-keyring.gpg] "
    "https://artifacts.elastic.co/packages/{major}.x/apt stable main"
)
ES_YUM_REPO = textwrap.dedent("""\
    [elasticsearch]
    name = Elasticsearch repository for {major}.x packages
    baseurl = https://artifacts.elastic.co/packages/{major}.x/yum
    gpgcheck = 1
    gpgkey = https://artifacts.elastic.co/GPG-KEY-elasticsearch
    enabled = 1
    autorefresh = 1
    type = rpm-md
""")

# Adoptium (Eclipse Temurin) for RHEL/AlmaLinux when openjdk is missing or broken
ADOPTIUM_YUM_REPO = textwrap.dedent("""\
    [adoptium]
    name = Adoptium RPM Repository
    baseurl = https://packages.adoptium.net/artifactory/rpm/centos/9/x86_64
    enabled = 1
    gpgcheck = 1
    gpgkey = https://packages.adoptium.net/artifactory/api/gpg/key/public
""")

# ---------------------------------------------------------------------------
# Globals for error aggregation
# ---------------------------------------------------------------------------
_errors: List[Dict[str, str]] = []
_warnings: List[str] = []


def _add_error(step: str, message: str, manual_fix: str = "") -> None:
    _errors.append({"step": step, "message": message, "manual_fix": manual_fix})
    log.error("[%s] %s", step, message)


def _add_warning(message: str) -> None:
    _warnings.append(message)
    log.warning(message)


# ===================================================================
#  UTILITY HELPERS
# ===================================================================

def _run(cmd: List[str], check: bool = True, capture: bool = True,
         input_data: Optional[str] = None, timeout: int = 600,
         **kwargs: Any) -> subprocess.CompletedProcess:
    """Run a subprocess with sensible defaults."""
    log.debug("  $ %s", " ".join(cmd))
    return subprocess.run(
        cmd,
        capture_output=capture,
        text=True,
        input=input_data,
        timeout=timeout,
        check=check,
        **kwargs,
    )


def _backup(path: Path) -> Optional[Path]:
    """Timestamped backup if file exists."""
    if not path.exists():
        return None
    bak = path.with_name(f"{path.name}.bak.{NOW_STR}")
    shutil.copy2(str(path), str(bak))
    log.info("  Backup: %s -> %s", path.name, bak.name)
    return bak


def _mask(value: str) -> str:
    if len(value) <= 8:
        return "***"
    return f"{value[:4]}...{value[-4:]}"


def _download(url: str, dest: Path, label: str = "",
              sha256: Optional[str] = None,
              offline_dir: Optional[Path] = None) -> bool:
    """Download a file with progress, optional checksum, and offline fallback."""
    filename = url.rsplit("/", 1)[-1].split("?")[0] or "download"

    # -- Offline mode --
    if offline_dir:
        src = offline_dir / filename
        if src.is_file():
            shutil.copy2(str(src), str(dest))
            log.info("  Copied from offline dir: %s", src.name)
            if sha256 and not _verify_sha256(dest, sha256):
                return False
            return True
        log.warning("  Offline file not found: %s (will try download)", src)

    # -- Download --
    log.info("  Downloading %s ...", label or filename)

    def _hook(count: int, block_size: int, total_size: int) -> None:
        if total_size > 0:
            pct = min(int(count * block_size * 100 / total_size), 100)
            mb_done = count * block_size / 1_048_576
            mb_total = total_size / 1_048_576
            sys.stdout.write(f"\r    {label or filename}: {pct}%  ({mb_done:.1f}/{mb_total:.1f} MB)")
        else:
            mb_done = count * block_size / 1_048_576
            sys.stdout.write(f"\r    {label or filename}: {mb_done:.1f} MB")
        sys.stdout.flush()

    try:
        urllib.request.urlretrieve(url, str(dest), reporthook=_hook)
        sys.stdout.write("\n")
    except (urllib.error.URLError, OSError) as exc:
        sys.stdout.write("\n")
        log.error("  Download failed: %s", exc)
        return False

    if sha256 and not _verify_sha256(dest, sha256):
        return False

    return True


def _verify_sha256(path: Path, expected: str) -> bool:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1_048_576), b""):
            h.update(chunk)
    actual = h.hexdigest()
    if actual.lower() != expected.lower():
        log.error("  Checksum mismatch for %s! Expected %s, got %s", path.name, expected[:16], actual[:16])
        return False
    log.info("  Checksum OK: %s", path.name)
    return True


def _find_desktop() -> Optional[Path]:
    """Find user Desktop folder (best-effort)."""
    if IS_WINDOWS:
        profile = os.environ.get("USERPROFILE")
        if profile:
            d = Path(profile) / "Desktop"
            if d.is_dir():
                return d
    d = Path.home() / "Desktop"
    return d if d.is_dir() else None


def _find_output_dir(app_base: Optional[Path] = None) -> Path:
    """Primary output directory for report + config.
    Linux servers: /opt/budg_install   (not Desktop — may not exist)
    Windows:       Desktop or install_dir
    Always also copies to Desktop if it exists.
    """
    if IS_LINUX:
        d = Path("/opt/budg_install")
    elif app_base:
        d = app_base
    else:
        d = Path(os.environ.get("ProgramData", "C:/")) / "budg_install"
    d.mkdir(parents=True, exist_ok=True)
    return d


# ===================================================================
#  OS DETECTION + ADMIN CHECK
# ===================================================================

def detect_os() -> Dict[str, Any]:
    """Return OS metadata dict."""
    info: Dict[str, Any] = {
        "system": platform.system(),
        "platform": platform.platform(),
        "is_windows": IS_WINDOWS,
        "is_linux": IS_LINUX,
        "distro_family": None,   # "debian" | "rhel" | None
        "distro_id": None,       # "ubuntu" | "debian" | "centos" | "rhel" | "almalinux" ...
        "codename": None,        # "jammy" | "bookworm" | ...
        "pkg_mgr": None,         # "apt" | "dnf" | "yum"
    }
    if IS_LINUX:
        os_release: Dict[str, str] = {}
        try:
            for line in Path("/etc/os-release").read_text().splitlines():
                if "=" in line:
                    k, _, v = line.partition("=")
                    os_release[k.strip()] = v.strip().strip('"')
        except FileNotFoundError:
            pass

        distro_id = os_release.get("ID", "").lower()
        id_like = os_release.get("ID_LIKE", "").lower()
        info["distro_id"] = distro_id
        info["codename"] = os_release.get("VERSION_CODENAME", "")

        if distro_id in ("ubuntu", "debian") or "debian" in id_like:
            info["distro_family"] = "debian"
            info["pkg_mgr"] = "apt"
        elif distro_id in ("centos", "rhel", "rocky", "almalinux", "fedora", "ol") or "rhel" in id_like:
            info["distro_family"] = "rhel"
            info["pkg_mgr"] = "dnf" if shutil.which("dnf") else "yum"
        else:
            # Unknown distro — try to guess
            if shutil.which("apt"):
                info["distro_family"] = "debian"
                info["pkg_mgr"] = "apt"
            elif shutil.which("dnf") or shutil.which("yum"):
                info["distro_family"] = "rhel"
                info["pkg_mgr"] = "dnf" if shutil.which("dnf") else "yum"

    return info


def check_admin() -> None:
    """Exit early if not root/admin."""
    if IS_LINUX:
        if os.geteuid() != 0:
            log.error("This script must be run as root.  Use:  sudo python3 %s ...", Path(__file__).name)
            sys.exit(2)
    elif IS_WINDOWS:
        try:
            is_admin = bool(ctypes.windll.shell32.IsUserAnAdmin())  # type: ignore[attr-defined]
        except Exception:
            is_admin = False
        if not is_admin:
            log.error("This script must be run as Administrator.")
            log.error("Right-click Command Prompt / PowerShell -> 'Run as administrator'")
            sys.exit(2)


# ===================================================================
#  PREREQUISITE CHECK
# ===================================================================

def check_prerequisites(os_info: Dict[str, Any], tomcat_base: Path) -> Dict[str, bool]:
    """Check what is already installed. Returns {component: is_installed}."""
    status: Dict[str, bool] = {}

    # Java 17
    try:
        r = _run(["java", "-version"], check=False)
        output = (r.stderr or "") + (r.stdout or "")
        status["java"] = "17" in output.split("\n")[0] if output else False
    except FileNotFoundError:
        status["java"] = False

    # MariaDB / MySQL
    for client in ("mariadb", "mysql"):
        if shutil.which(client):
            try:
                r = _run([client, "--version"], check=False)
                status["mariadb"] = r.returncode == 0
                break
            except Exception:
                pass
    else:
        status["mariadb"] = False

    # Elasticsearch
    status["elasticsearch"] = False
    try:
        req = urllib.request.Request("http://127.0.0.1:9200", method="GET")
        with urllib.request.urlopen(req, timeout=3) as resp:
            status["elasticsearch"] = resp.status == 200
    except Exception:
        # Maybe installed but not running yet
        if IS_LINUX:
            status["elasticsearch"] = Path("/usr/share/elasticsearch/bin/elasticsearch").is_file()
        elif IS_WINDOWS:
            # Will be checked later based on install_dir
            pass

    # Tomcat
    if IS_WINDOWS:
        status["tomcat"] = (tomcat_base / "bin" / "startup.bat").is_file()
    else:
        status["tomcat"] = (tomcat_base / "bin" / "startup.sh").is_file()

    for comp, installed in status.items():
        log.info("  %-15s : %s", comp, "INSTALLED" if installed else "not found")

    return status


# ===================================================================
#  INSTALL: JAVA 17
# ===================================================================

def install_java(os_info: Dict[str, Any], args: argparse.Namespace) -> Dict[str, Any]:
    result: Dict[str, Any] = {"installed": False, "skipped": False, "java_home": ""}
    log.info("[Java 17] Installing ...")

    if IS_LINUX:
        pkg_mgr = os_info["pkg_mgr"]
        if pkg_mgr == "apt":
            _run(["apt-get", "update", "-qq"], check=False)
            r = _run(["apt-get", "install", "-y", "openjdk-17-jdk"], check=False)
        else:
            # RHEL/AlmaLinux: prefer Adoptium Temurin 17 (works on el9/el10)
            adoptium_repo = Path("/etc/yum.repos.d/adoptium.repo")
            _backup(adoptium_repo)
            adoptium_repo.write_text(ADOPTIUM_YUM_REPO, encoding="utf-8")
            _run([pkg_mgr, "clean", "all"], check=False)
            _run([pkg_mgr, "makecache"], check=False)
            r = _run([pkg_mgr, "install", "-y", "temurin-17-jdk"], check=False)
            if r.returncode == 0:
                log.info("  Installed Java 17 via Adoptium Temurin (temurin-17-jdk)")
            else:
                # Fallback: openjdk packages
                for pkg in ("java-17-openjdk-devel", "java-17-openjdk", "java-17-openjdk-headless"):
                    r = _run([pkg_mgr, "install", "-y", pkg], check=False)
                    if r.returncode == 0:
                        log.info("  Installed Java 17 via package: %s", pkg)
                        break
                else:
                    _add_error("Java", f"Package install failed: {r.stderr[:300]}",
                               "sudo dnf install -y temurin-17-jdk  # or add Adoptium repo (see report)")
                    return result

        # Detect JAVA_HOME
        for candidate in [
            Path("/usr/lib/jvm/java-17-openjdk-amd64"),
            Path("/usr/lib/jvm/java-17-openjdk"),
            Path("/usr/lib/jvm/java-17"),
        ]:
            if candidate.is_dir():
                result["java_home"] = str(candidate)
                break

    elif IS_WINDOWS:
        dl_dir = Path(args.install_dir) / "_downloads"
        dl_dir.mkdir(parents=True, exist_ok=True)
        msi_path = dl_dir / "adoptium-jdk17.msi"

        url = ADOPTIUM_API_WIN.format(major="17")
        if not _download(url, msi_path, label="Adoptium JDK 17", offline_dir=getattr(args, "offline_dir_path", None)):
            _add_error("Java", "Failed to download Adoptium JDK 17 MSI",
                       f"Download manually from https://adoptium.net/temurin/releases/?version=17 and install")
            return result

        log.info("  Running MSI installer (silent) ...")
        r = _run([
            "msiexec", "/i", str(msi_path), "/qn",
            "ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome",
        ], check=False, timeout=300)

        if r.returncode != 0:
            _add_error("Java", f"MSI install returned code {r.returncode}",
                       f'msiexec /i "{msi_path}" /qn ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome')
            return result

        # Detect JAVA_HOME from registry / common paths
        for candidate in [
            Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Eclipse Adoptium",
        ]:
            if candidate.is_dir():
                for child in sorted(candidate.iterdir(), reverse=True):
                    if child.name.startswith("jdk-17") and child.is_dir():
                        result["java_home"] = str(child)
                        break
                if result["java_home"]:
                    break

    # Verify
    try:
        r = _run(["java", "-version"], check=False)
        out = (r.stderr or "") + (r.stdout or "")
        if "17" in out.split("\n")[0]:
            result["installed"] = True
            log.info("  Java 17 installed successfully.")
        else:
            _add_warning("Java installed but version mismatch — check PATH.")
            result["installed"] = True
    except FileNotFoundError:
        if result.get("java_home"):
            _add_warning(f"Java installed at {result['java_home']} but not yet in PATH. Restart shell or set JAVA_HOME.")
            result["installed"] = True
        else:
            _add_error("Java", "java command not found after install", "Set JAVA_HOME and add to PATH")

    return result


# ===================================================================
#  INSTALL: MARIADB
# ===================================================================

def install_mariadb(os_info: Dict[str, Any], args: argparse.Namespace) -> Dict[str, Any]:
    result: Dict[str, Any] = {"installed": False, "skipped": False}
    ver = args.mariadb_version
    minor_ver = ".".join(ver.split(".")[:2])  # e.g. "11.4"
    log.info("[MariaDB %s] Installing ...", ver)

    if IS_LINUX:
        result = _install_mariadb_linux(os_info, ver, minor_ver)
    elif IS_WINDOWS:
        result = _install_mariadb_windows(args, ver)

    return result


def _install_mariadb_linux(os_info: Dict[str, Any], ver: str, minor_ver: str) -> Dict[str, Any]:
    result: Dict[str, Any] = {"installed": False}
    pkg_mgr = os_info["pkg_mgr"]

    if pkg_mgr == "apt":
        # Add repo key
        keyring = Path("/etc/apt/keyrings")
        keyring.mkdir(parents=True, exist_ok=True)
        key_dest = keyring / "mariadb-keyring.pgp"
        if not key_dest.exists():
            if not _download(MARIADB_APT_KEY_URL, key_dest, label="MariaDB GPG key"):
                _add_error("MariaDB", "Failed to download GPG key",
                           f"curl -o {key_dest} '{MARIADB_APT_KEY_URL}'")
                return result

        # Add sources list
        codename = os_info.get("codename", "jammy")
        distro_id = os_info.get("distro_id", "ubuntu")
        repo_line = MARIADB_APT_REPO.format(minor_ver=minor_ver, distro_id=distro_id, codename=codename)
        repo_file = Path("/etc/apt/sources.list.d/mariadb.list")
        if not repo_file.exists():
            repo_file.write_text(repo_line + "\n", encoding="utf-8")
            log.info("  Added MariaDB apt repo: %s", repo_file)

        _run(["apt-get", "update", "-qq"], check=False)
        r = _run(["apt-get", "install", "-y", "mariadb-server", "mariadb-client"], check=False)

    else:  # dnf / yum
        repo_file = Path("/etc/yum.repos.d/MariaDB.repo")
        if not repo_file.exists():
            repo_file.write_text(MARIADB_YUM_REPO.format(minor_ver=minor_ver), encoding="utf-8")
            log.info("  Added MariaDB yum repo: %s", repo_file)

        r = _run([pkg_mgr, "install", "-y", "MariaDB-server", "MariaDB-client"], check=False)

    if r.returncode != 0:
        _add_error("MariaDB", f"Package install failed: {(r.stderr or '')[:300]}",
                   f"sudo {pkg_mgr} install -y mariadb-server mariadb-client")
        return result

    # Enable + start
    _run(["systemctl", "enable", "--now", "mariadb"], check=False)
    result["installed"] = True
    log.info("  MariaDB installed and started.")
    return result


def _install_mariadb_windows(args: argparse.Namespace, ver: str) -> Dict[str, Any]:
    result: Dict[str, Any] = {"installed": False}
    dl_dir = Path(args.install_dir) / "_downloads"
    dl_dir.mkdir(parents=True, exist_ok=True)
    msi_path = dl_dir / f"mariadb-{ver}-winx64.msi"

    url = MARIADB_MSI_URL.format(ver=ver)
    if not _download(url, msi_path, label=f"MariaDB {ver} MSI",
                     offline_dir=getattr(args, "offline_dir_path", None)):
        _add_error("MariaDB", "Failed to download MariaDB MSI",
                   f"Download from https://mariadb.org/download/ and install manually")
        return result

    db_root_pass = args.db_root_pass or secrets.token_urlsafe(16)
    log.info("  Running MSI installer (silent) ...")
    r = _run([
        "msiexec", "/i", str(msi_path), "/qn",
        f"PASSWORD={db_root_pass}",
        "SERVICENAME=MariaDB",
        f"PORT={args.db_port}",
        "ALLOWREMOTEROOTACCESS=false",
    ], check=False, timeout=300)

    if r.returncode != 0:
        _add_error("MariaDB", f"MSI install returned code {r.returncode}",
                   f'msiexec /i "{msi_path}" /qn PASSWORD=yourpass SERVICENAME=MariaDB')
        return result

    # Store root pass for configure step
    args._db_root_pass_actual = db_root_pass
    result["installed"] = True
    log.info("  MariaDB installed as Windows service.")
    return result


# ===================================================================
#  CONFIGURE MARIADB: lower_case_table_names (Linux) + VERIFY
# ===================================================================

def configure_mariadb_lower_case_tables(args: argparse.Namespace) -> Dict[str, Any]:
    """Set lower_case_table_names=1 on Linux before any DB/tables exist; verify on all OS.

    Must run after install_mariadb and before configure_mariadb/import_database.
    On Windows the default is 1; we only verify. Returns dict with configured,
    verified_value (0 or 1), config_path (if set).
    """
    result: Dict[str, Any] = {
        "configured": False,
        "verified_value": None,
        "config_path": None,
    }
    client = shutil.which("mariadb") or shutil.which("mysql")
    if not client:
        log.warning("  MariaDB/MySQL client not found — skipping lower_case_table_names config and verify.")
        return result

    # --- Linux: write config and restart ---
    if IS_LINUX:
        config_candidates = [
            Path("/etc/my.cnf.d/server.cnf"),
            Path("/etc/mysql/mariadb.conf.d/50-server.cnf"),
        ]
        conf_path: Optional[Path] = None
        for p in config_candidates:
            if p.is_file():
                conf_path = p
                break
        if not conf_path:
            # Prefer creating under my.cnf.d (RHEL-style); ensure parent exists
            conf_path = Path("/etc/my.cnf.d/server.cnf")
            try:
                conf_path.parent.mkdir(parents=True, exist_ok=True)
            except OSError:
                conf_path = Path("/etc/mysql/mariadb.conf.d/50-server.cnf")
                try:
                    conf_path.parent.mkdir(parents=True, exist_ok=True)
                except OSError:
                    _add_warning("Could not create MariaDB config dir; skipping lower_case_table_names.")
                    conf_path = None

        if conf_path:
            try:
                content = conf_path.read_text(encoding="utf-8") if conf_path.is_file() else ""
            except OSError:
                content = ""

            in_mysqld = False
            has_setting = False
            lines_out: List[str] = []
            for line in content.splitlines():
                stripped = line.strip()
                if stripped.startswith("[") and stripped.endswith("]"):
                    in_mysqld = stripped.lower() == "[mysqld]"
                    lines_out.append(line)
                    if in_mysqld and not has_setting:
                        lines_out.append("lower_case_table_names=1")
                        has_setting = True
                    continue
                if in_mysqld and stripped.lower().startswith("lower_case_table_names"):
                    has_setting = True
                    lines_out.append("lower_case_table_names=1")
                    continue
                lines_out.append(line)

            if not has_setting:
                if "[mysqld]" not in content.lower():
                    lines_out.append("")
                    lines_out.append("[mysqld]")
                lines_out.append("lower_case_table_names=1")

            _backup(conf_path)
            conf_path.write_text("\n".join(lines_out) + "\n", encoding="utf-8")
            result["configured"] = True
            result["config_path"] = str(conf_path)
            log.info("  Set lower_case_table_names=1 in %s", conf_path)

            _run(["systemctl", "restart", "mariadb"], check=False)
            time.sleep(1)

    # --- Verify: SHOW VARIABLES LIKE 'lower_case_table_names' (any OS) ---
    root_pass = getattr(args, "_db_root_pass_actual", None) or args.db_root_pass or ""
    root_cmd_base: List[str] = [client, "-u", "root"]
    connected = False
    if IS_LINUX:
        if _run(root_cmd_base + ["-e", "SELECT 1"], check=False).returncode == 0:
            connected = True
    if not connected and root_pass:
        if _run([client, "-u", "root", f"-p{root_pass}", "-e", "SELECT 1"], check=False).returncode == 0:
            root_cmd_base = [client, "-u", "root", f"-p{root_pass}"]
            connected = True
    if not connected:
        if _run(root_cmd_base + ["-e", "SELECT 1"], check=False).returncode == 0:
            connected = True

    if not connected:
        log.warning("  Could not connect as root to verify lower_case_table_names.")
        return result

    r = _run(root_cmd_base + ["-e", "SHOW VARIABLES LIKE 'lower_case_table_names';"], check=False, capture=True)
    if r.returncode == 0 and (r.stdout or r.stderr):
        out = (r.stdout or "") + (r.stderr or "")
        for line in out.splitlines():
            if "lower_case_table_names" in line:
                parts = line.split()
                if len(parts) >= 2:
                    try:
                        val = int(parts[-1])
                        result["verified_value"] = val
                        break
                    except ValueError:
                        pass

    if result["verified_value"] == 1:
        log.info("  lower_case_table_names=1 (OK — table names case-insensitive).")
    elif result["verified_value"] == 0:
        _add_warning(
            "lower_case_table_names=0 (case-sensitive). This can cause table name issues. "
            "On Linux, add lower_case_table_names=1 under [mysqld] in config and restart mariadb."
        )
    else:
        log.warning("  Could not verify lower_case_table_names (MariaDB may not be running or reachable).")

    return result


# ===================================================================
#  CONFIGURE MARIADB
# ===================================================================

def configure_mariadb(args: argparse.Namespace) -> Dict[str, Any]:
    """Create database + app user.  Handles unix_socket auth (Linux) and password auth (Windows)."""
    result: Dict[str, Any] = {"db_created": False, "user_created": False, "root_pass": ""}
    log.info("[MariaDB] Configuring database ...")

    client = shutil.which("mariadb") or shutil.which("mysql")
    if not client:
        _add_error("MariaDB-config", "MariaDB/MySQL client not found in PATH",
                   "Ensure MariaDB bin/ is in PATH, or log out and back in")
        return result

    # Determine root connection strategy
    root_pass = getattr(args, "_db_root_pass_actual", None) or args.db_root_pass or ""
    root_cmd_base = [client, "-u", "root"]

    # Strategy 1: unix_socket / no password (common on fresh Linux)
    connected = False
    if IS_LINUX:
        r = _run(root_cmd_base + ["-e", "SELECT 1"], check=False)
        if r.returncode == 0:
            connected = True
            log.info("  Connected to MariaDB via unix_socket (no password).")

    # Strategy 2: with password
    if not connected and root_pass:
        r = _run(root_cmd_base + [f"-p{root_pass}", "-e", "SELECT 1"], check=False)
        if r.returncode == 0:
            connected = True
            root_cmd_base.append(f"-p{root_pass}")
            log.info("  Connected to MariaDB with root password.")

    # Strategy 3: empty password (Windows fresh install)
    if not connected:
        r = _run(root_cmd_base + ["-e", "SELECT 1"], check=False)
        if r.returncode == 0:
            connected = True
            log.info("  Connected to MariaDB with empty root password.")

    if not connected:
        _add_error("MariaDB-config", "Cannot connect to MariaDB as root",
                   f"Try: {client} -u root -p  and then run SQL manually (see report)")
        return result

    # --- Secure installation ---
    secure_sql = textwrap.dedent(f"""\
        DELETE FROM mysql.user WHERE User='';
        DELETE FROM mysql.user WHERE User='root' AND Host NOT IN ('localhost', '127.0.0.1', '::1');
        DROP DATABASE IF EXISTS test;
        DELETE FROM mysql.db WHERE Db='test' OR Db='test\\_%';
        FLUSH PRIVILEGES;
    """)
    _run(root_cmd_base + ["-e", secure_sql], check=False)

    # --- Set root password if on fresh install ---
    if root_pass and IS_WINDOWS:
        result["root_pass"] = root_pass

    # --- Create database ---
    db_name = args.db_name
    db_user = args.db_user
    db_pass = args.db_pass or secrets.token_urlsafe(16)
    args._db_pass_actual = db_pass  # save for later use
    # Escape for use inside single-quoted SQL (backslash then single quote)
    db_pass_sql = db_pass.replace("\\", "\\\\").replace("'", "''")

    # App often connects via 127.0.0.1 (JDBC); MariaDB treats that as 'user'@'127.0.0.1', not localhost
    create_sql = textwrap.dedent(f"""\
        CREATE DATABASE IF NOT EXISTS `{db_name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
        CREATE USER IF NOT EXISTS '{db_user}'@'localhost' IDENTIFIED BY '{db_pass_sql}';
        CREATE USER IF NOT EXISTS '{db_user}'@'127.0.0.1' IDENTIFIED BY '{db_pass_sql}';
        GRANT ALL PRIVILEGES ON `{db_name}`.* TO '{db_user}'@'localhost';
        GRANT ALL PRIVILEGES ON `{db_name}`.* TO '{db_user}'@'127.0.0.1';
        FLUSH PRIVILEGES;
        ALTER USER '{db_user}'@'localhost' IDENTIFIED BY '{db_pass_sql}';
        ALTER USER '{db_user}'@'127.0.0.1' IDENTIFIED BY '{db_pass_sql}';
        FLUSH PRIVILEGES;
    """)

    r = _run(root_cmd_base + ["-e", create_sql], check=False)
    if r.returncode != 0:
        _add_error("MariaDB-config", f"Failed to create DB/user: {(r.stderr or '')[:300]}",
                   f"{client} -u root -p -e \"CREATE DATABASE {db_name}; ...\"")
        return result

    result["db_created"] = True
    result["user_created"] = True
    result["db_pass"] = db_pass
    log.info("  Database '%s' created, user '%s' ready.", db_name, db_user)
    return result


# ===================================================================
#  INSTALL + CONFIGURE: ELASTICSEARCH
# ===================================================================

def install_elasticsearch(os_info: Dict[str, Any], args: argparse.Namespace) -> Dict[str, Any]:
    result: Dict[str, Any] = {"installed": False, "es_home": ""}
    ver = args.es_version
    es_major = ver.split(".")[0]
    log.info("[Elasticsearch %s] Installing ...", ver)

    if IS_LINUX:
        result = _install_es_linux(os_info, ver, es_major)
    elif IS_WINDOWS:
        result = _install_es_windows(args, ver, es_major)

    return result


def _install_es_linux(os_info: Dict[str, Any], ver: str, es_major: str) -> Dict[str, Any]:
    result: Dict[str, Any] = {"installed": False, "es_home": "/usr/share/elasticsearch"}
    pkg_mgr = os_info["pkg_mgr"]

    if pkg_mgr == "apt":
        # GPG key
        keyring = Path("/usr/share/keyrings/elasticsearch-keyring.gpg")
        if not keyring.exists():
            key_tmp = Path("/tmp/elastic-key.asc")
            if _download(ES_APT_KEY_URL, key_tmp, label="Elastic GPG key"):
                _run(["gpg", "--dearmor", "-o", str(keyring)],
                     input_data=key_tmp.read_text(), check=False)
                # fallback: direct approach
                if not keyring.exists():
                    r = _run(["bash", "-c",
                              f"curl -fsSL {ES_APT_KEY_URL} | gpg --dearmor -o {keyring}"],
                             check=False)
                key_tmp.unlink(missing_ok=True)

        # Repo
        repo_file = Path("/etc/apt/sources.list.d/elastic-8.x.list")
        if not repo_file.exists():
            repo_file.write_text(ES_APT_REPO.format(major=es_major) + "\n", encoding="utf-8")

        _run(["apt-get", "update", "-qq"], check=False)
        r = _run(["apt-get", "install", "-y", "elasticsearch"], check=False)

    else:  # dnf / yum
        repo_file = Path("/etc/yum.repos.d/elasticsearch.repo")
        _backup(repo_file)
        repo_file.write_text(ES_YUM_REPO.format(major=es_major), encoding="utf-8")
        _run(["rpm", "--import", ES_APT_KEY_URL], check=False)

        r = _run([pkg_mgr, "install", "-y", "elasticsearch"], check=False)

    if r.returncode != 0:
        _add_error("Elasticsearch", f"Package install failed: {(r.stderr or '')[:300]}",
                   f"sudo {pkg_mgr} install -y elasticsearch")
        return result

    result["installed"] = True
    log.info("  Elasticsearch installed.")
    return result


def _install_es_windows(args: argparse.Namespace, ver: str, es_major: str) -> Dict[str, Any]:
    install_dir = Path(args.install_dir)
    es_home = install_dir / "elasticsearch"
    result: Dict[str, Any] = {"installed": False, "es_home": str(es_home)}

    if (es_home / "bin" / "elasticsearch.bat").is_file():
        log.info("  Elasticsearch already extracted at %s", es_home)
        result["installed"] = True
        return result

    dl_dir = install_dir / "_downloads"
    dl_dir.mkdir(parents=True, exist_ok=True)
    zip_path = dl_dir / f"elasticsearch-{ver}-windows-x86_64.zip"

    url = ES_URL_WIN.format(ver=ver)
    if not _download(url, zip_path, label=f"Elasticsearch {ver}",
                     offline_dir=getattr(args, "offline_dir_path", None)):
        _add_error("Elasticsearch", "Failed to download Elasticsearch",
                   f"Download from https://www.elastic.co/downloads/elasticsearch and extract to {es_home}")
        return result

    log.info("  Extracting ...")
    with zipfile.ZipFile(str(zip_path), "r") as zf:
        zf.extractall(str(install_dir))

    # Rename extracted folder
    extracted = install_dir / f"elasticsearch-{ver}"
    if extracted.is_dir() and not es_home.is_dir():
        extracted.rename(es_home)
    elif extracted.is_dir():
        # Merge into existing
        shutil.copytree(str(extracted), str(es_home), dirs_exist_ok=True)
        shutil.rmtree(str(extracted), ignore_errors=True)

    # Install as Windows service
    svc_bat = es_home / "bin" / "elasticsearch-service.bat"
    if svc_bat.is_file():
        log.info("  Installing Elasticsearch as Windows service ...")
        r = _run([str(svc_bat), "install"], check=False)
        if r.returncode != 0:
            _add_warning(f"Elasticsearch service install returned {r.returncode}. May need manual setup.")

    result["installed"] = True
    log.info("  Elasticsearch extracted to %s", es_home)
    return result


def configure_elasticsearch(args: argparse.Namespace, es_result: Dict[str, Any]) -> None:
    """Disable security, set single-node, bind to localhost."""
    log.info("[Elasticsearch] Configuring ...")

    if IS_LINUX:
        yml_path = Path("/etc/elasticsearch/elasticsearch.yml")
    else:
        es_home = Path(es_result.get("es_home", Path(args.install_dir) / "elasticsearch"))
        yml_path = es_home / "config" / "elasticsearch.yml"

    if not yml_path.is_file():
        _add_warning(f"elasticsearch.yml not found at {yml_path} — skipping configuration.")
        return

    _backup(yml_path)

    # Read existing config and patch
    content = yml_path.read_text(encoding="utf-8")
    patches = {
        "xpack.security.enabled": "false",
        "xpack.security.enrollment.enabled": "false",
        "xpack.security.http.ssl.enabled": "false",
        "xpack.security.transport.ssl.enabled": "false",
        "network.host": "127.0.0.1",
        "http.port": "9200",
        "discovery.type": "single-node",
    }

    # With discovery.type: single-node, cluster.initial_master_nodes is not allowed — remove it
    lines = content.splitlines()
    new_lines: List[str] = []
    written_keys: set = set()

    for line in lines:
        stripped = line.strip()
        if stripped.lstrip("# ").startswith("cluster.initial_master_nodes"):
            continue  # drop so single-node and initial_master_nodes are not both set
        matched = False
        for key, val in patches.items():
            if stripped.startswith(key + ":") or stripped.startswith("#" + key + ":") or stripped.startswith("# " + key + ":"):
                new_lines.append(f"{key}: {val}")
                written_keys.add(key)
                matched = True
                break
        if not matched:
            new_lines.append(line)

    # Append any keys not yet present
    for key, val in patches.items():
        if key not in written_keys:
            new_lines.append(f"{key}: {val}")

    yml_path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
    log.info("  elasticsearch.yml updated: security disabled, single-node, localhost only.")

    # Restart if running
    if IS_LINUX:
        _run(["systemctl", "enable", "--now", "elasticsearch"], check=False)
        _run(["systemctl", "restart", "elasticsearch"], check=False)
    elif IS_WINDOWS:
        es_home_path = Path(es_result.get("es_home", ""))
        svc_bat = es_home_path / "bin" / "elasticsearch-service.bat"
        if svc_bat.is_file():
            _run([str(svc_bat), "start"], check=False)


# ===================================================================
#  INSTALL: TOMCAT
# ===================================================================

def install_tomcat(os_info: Dict[str, Any], args: argparse.Namespace) -> Dict[str, Any]:
    result: Dict[str, Any] = {"installed": False, "tomcat_base": str(args.tomcat_base)}
    tomcat_base = Path(args.tomcat_base)
    ver = args.tomcat_version
    major = ver.split(".")[0]
    log.info("[Tomcat %s] Installing ...", ver)

    if IS_LINUX:
        result = _install_tomcat_linux(os_info, args, tomcat_base, ver, major)
    elif IS_WINDOWS:
        result = _install_tomcat_windows(args, tomcat_base, ver, major)

    return result


def _install_tomcat_linux(os_info: Dict[str, Any], args: argparse.Namespace,
                          tomcat_base: Path, ver: str, major: str) -> Dict[str, Any]:
    result: Dict[str, Any] = {"installed": False, "tomcat_base": str(tomcat_base)}

    dl_dir = Path("/tmp")
    tar_path = dl_dir / f"apache-tomcat-{ver}.tar.gz"

    url = TOMCAT_URL_LINUX.format(ver=ver, major=major)
    if not _download(url, tar_path, label=f"Tomcat {ver}",
                     offline_dir=getattr(args, "offline_dir_path", None)):
        # Fallback: Apache archive (dlcdn often 404 for older versions)
        url = TOMCAT_URL_LINUX_ARCHIVE.format(ver=ver, major=major)
        log.info("  Trying archive mirror: %s", url)
        if not _download(url, tar_path, label=f"Tomcat {ver} (archive)"):
            _add_error("Tomcat", "Failed to download Tomcat",
                       f"wget {url} && sudo tar xzf apache-tomcat-{ver}.tar.gz -C /opt/tomcat --strip-components=1")
            return result

    tomcat_base.mkdir(parents=True, exist_ok=True)
    log.info("  Extracting to %s ...", tomcat_base)
    with tarfile.open(str(tar_path), "r:gz") as tf:
        tf.extractall(str(tomcat_base), filter="data")

    # Move from nested dir if needed (strip-components=1 equivalent)
    nested = tomcat_base / f"apache-tomcat-{ver}"
    if nested.is_dir():
        for item in nested.iterdir():
            dest = tomcat_base / item.name
            if dest.exists():
                if dest.is_dir():
                    shutil.copytree(str(item), str(dest), dirs_exist_ok=True)
                    shutil.rmtree(str(item))
                else:
                    item.rename(dest)
            else:
                item.rename(dest)
        nested.rmdir()

    # Make scripts executable
    for sh in (tomcat_base / "bin").glob("*.sh"):
        sh.chmod(sh.stat().st_mode | 0o755)

    # Create tomcat system user
    r = _run(["id", "tomcat"], check=False)
    if r.returncode != 0:
        _run(["useradd", "-r", "-m", "-d", str(tomcat_base), "-s", "/bin/false", "tomcat"], check=False)
        log.info("  Created system user 'tomcat'.")

    # Set ownership
    _run(["chown", "-R", "tomcat:tomcat", str(tomcat_base)], check=False)

    # Create systemd service
    java_home = ""
    for candidate in [
        Path("/usr/lib/jvm/java-17-openjdk-amd64"),
        Path("/usr/lib/jvm/java-17-openjdk"),
        Path("/usr/lib/jvm/java-17"),
    ]:
        if candidate.is_dir():
            java_home = str(candidate)
            break

    service_content = textwrap.dedent(f"""\
        [Unit]
        Description=Apache Tomcat 11
        After=network.target mariadb.service elasticsearch.service

        [Service]
        Type=forking
        User=tomcat
        Group=tomcat

        Environment="JAVA_HOME={java_home}"
        Environment="CATALINA_HOME={tomcat_base}"
        Environment="CATALINA_BASE={tomcat_base}"

        ExecStart={tomcat_base}/bin/startup.sh
        ExecStop={tomcat_base}/bin/shutdown.sh

        RestartSec=10
        Restart=on-failure

        [Install]
        WantedBy=multi-user.target
    """)

    svc_path = Path("/etc/systemd/system/tomcat.service")
    _backup(svc_path)
    svc_path.write_text(service_content, encoding="utf-8")
    _run(["systemctl", "daemon-reload"], check=False)
    _run(["systemctl", "enable", "tomcat"], check=False)
    log.info("  systemd service created: %s", svc_path)

    # Clean up tarball
    tar_path.unlink(missing_ok=True)

    result["installed"] = True
    log.info("  Tomcat %s installed at %s", ver, tomcat_base)
    return result


def _install_tomcat_windows(args: argparse.Namespace, tomcat_base: Path,
                            ver: str, major: str) -> Dict[str, Any]:
    result: Dict[str, Any] = {"installed": False, "tomcat_base": str(tomcat_base)}

    dl_dir = Path(args.install_dir) / "_downloads"
    dl_dir.mkdir(parents=True, exist_ok=True)
    zip_path = dl_dir / f"apache-tomcat-{ver}-windows-x64.zip"

    url = TOMCAT_URL_WIN.format(ver=ver, major=major)
    if not _download(url, zip_path, label=f"Tomcat {ver}",
                     offline_dir=getattr(args, "offline_dir_path", None)):
        _add_error("Tomcat", "Failed to download Tomcat",
                   f"Download from https://tomcat.apache.org/download-{major}0.cgi and extract to {tomcat_base}")
        return result

    log.info("  Extracting ...")
    tomcat_base.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(str(zip_path), "r") as zf:
        zf.extractall(str(tomcat_base.parent))

    # Rename extracted folder
    extracted = tomcat_base.parent / f"apache-tomcat-{ver}"
    if extracted.is_dir() and not tomcat_base.is_dir():
        extracted.rename(tomcat_base)
    elif extracted.is_dir() and tomcat_base.is_dir():
        shutil.copytree(str(extracted), str(tomcat_base), dirs_exist_ok=True)
        shutil.rmtree(str(extracted), ignore_errors=True)

    result["installed"] = True
    log.info("  Tomcat %s installed at %s", ver, tomcat_base)
    return result


# ===================================================================
#  IMPORT DATABASE
# ===================================================================

def import_database(args: argparse.Namespace) -> Dict[str, Any]:
    """Import project.sql. Uses root (not app user) so DEFINER=root@localhost in triggers works."""
    result: Dict[str, Any] = {"imported": False, "error_log": ""}
    log.info("[Database] Importing SQL ...")

    sql_file = Path(args.sql_file)
    if not sql_file.is_file():
        _add_warning(f"SQL file not found: {sql_file} — skipping database import.")
        result["skipped"] = True
        return result

    client = shutil.which("mariadb") or shutil.which("mysql")
    if not client:
        _add_error("DB-import", "MariaDB/MySQL client not found", "Ensure MariaDB is in PATH")
        return result

    db_name = args.db_name
    # Use root for import so DEFINER=root@localhost in triggers/procedures works
    root_pass = getattr(args, "_db_root_pass_actual", None) or args.db_root_pass or ""
    cmd_base = [client, "-u", "root"]
    if root_pass:
        cmd_base.extend([f"-p{root_pass}"])
    cmd_base.extend([db_name])
    cmd = cmd_base

    log.info("  Importing %s into '%s' as root (this may take a while) ...", sql_file.name, db_name)
    try:
        with open(sql_file, "r", encoding="utf-8", errors="replace") as f:
            r = subprocess.run(cmd, stdin=f, capture_output=True, text=True, timeout=1800)

        if r.returncode != 0:
            # Save stderr to log
            err_log_path = SCRIPT_DIR / "db_import_errors.log"
            err_log_path.write_text(r.stderr or "", encoding="utf-8")
            result["error_log"] = str(err_log_path)

            err_lines = (r.stderr or "").strip().splitlines()[:30]
            _add_error("DB-import",
                       f"SQL import returned code {r.returncode}.\n    First errors:\n    " +
                       "\n    ".join(err_lines[:10]),
                       f"{client} -u root -p*** {db_name} < {sql_file}\n"
                       f"    Full error log: {err_log_path}")
            return result

    except subprocess.TimeoutExpired:
        _add_error("DB-import", "SQL import timed out (>30 minutes)",
                   f"Run manually: {client} -u root -p*** {db_name} < {sql_file}")
        return result

    result["imported"] = True
    log.info("  Database imported successfully.")
    return result


# ===================================================================
#  DEPLOY WAR
# ===================================================================

def deploy_war(args: argparse.Namespace) -> Dict[str, Any]:
    result: Dict[str, Any] = {"deployed": False}
    log.info("[WAR] Deploying ROOT.war ...")

    war_src = Path(args.war_file)
    if not war_src.is_file():
        _add_warning(f"WAR file not found: {war_src} — skipping deployment.")
        result["skipped"] = True
        return result

    tomcat_base = Path(args.tomcat_base)
    webapps = tomcat_base / "webapps"
    if not webapps.is_dir():
        webapps.mkdir(parents=True, exist_ok=True)

    dest = webapps / "ROOT.war"

    # Remove old exploded dir (first install = clean deploy)
    root_dir = webapps / "ROOT"
    if root_dir.is_dir():
        shutil.rmtree(str(root_dir))
        log.info("  Removed old exploded ROOT/ directory.")

    # Backup old WAR if exists
    if dest.is_file():
        _backup(dest)

    shutil.copy2(str(war_src), str(dest))
    result["deployed"] = True
    log.info("  ROOT.war deployed to %s", dest)
    return result


# ===================================================================
#  ENSURE APP DIRECTORIES (bulk, uploads, logs — for Tomcat/Python read-write)
# ===================================================================

def ensure_app_dirs(args: argparse.Namespace) -> Dict[str, Any]:
    """Create app_base and subdirs (logs, uploads, bulk, data, etc.); on Linux chown to tomcat."""
    result: Dict[str, Any] = {"app_base": "", "created": False}
    if IS_LINUX:
        app_base = Path("/opt/budg_v2")
    else:
        app_base = Path(args.install_dir) / "app"

    result["app_base"] = str(app_base)
    dirs = [
        app_base,
        app_base / "logs",
        app_base / "uploads" / "documents",
        app_base / "bulk",
        app_base / "data",
        app_base / "data" / "bpmn",
        app_base / "data" / "default-workflows",
        app_base / "tmp" / "ldap" / "tmp",
        app_base / "tmp" / "ldap" / "lock",
    ]
    for d in dirs:
        try:
            d.mkdir(parents=True, exist_ok=True)
        except OSError as exc:
            _add_warning("Could not create %s: %s", d, exc)
            return result

    result["created"] = True
    log.info("  App directories created under %s", app_base)

    if IS_LINUX:
        _run(["chown", "-R", "tomcat:tomcat", str(app_base)], check=False)
        log.info("  Ownership set to tomcat:tomcat for %s", app_base)

    return result


# ===================================================================
#  SETUP PYTHON VALIDATION SERVICE
# ===================================================================

def setup_python_service(args: argparse.Namespace) -> Dict[str, Any]:
    """Install the Python FastAPI bulk-validation service (port 8000).

    Steps:
      1. Copy python/ source tree to the deployment directory.
      2. Create a virtual environment + pip install requirements.txt.
      3. Linux: create a systemd service (budg-validator.service).
      4. Windows: handled via desktop start/stop scripts.
    """
    result: Dict[str, Any] = {
        "installed": False,
        "service_dir": "",
        "venv_dir": "",
        "systemd_service": "",
    }
    log.info("[Python Service] Setting up bulk-validation service ...")

    # --- Locate source python/ directory ---
    src_python = SCRIPT_DIR / "python"
    if not src_python.is_dir():
        _add_error("PythonService", f"Source directory not found: {src_python}",
                   "Ensure the 'python/' folder is next to setup_server.py")
        return result

    # --- Determine destination ---
    if IS_LINUX:
        service_dir = Path("/opt/budg_v2/python")
    else:
        service_dir = Path(args.install_dir) / "python"

    result["service_dir"] = str(service_dir)

    # --- Copy source tree (skip venv / __pycache__) ---
    log.info("  Copying python/ -> %s ...", service_dir)
    if service_dir.is_dir() and service_dir != src_python:
        # Preserve existing venv if any
        existing_venv = service_dir / "venv"
        had_venv = existing_venv.is_dir()
        if had_venv:
            tmp_venv = service_dir.parent / "_tmp_venv_backup"
            if tmp_venv.exists():
                shutil.rmtree(str(tmp_venv))
            existing_venv.rename(tmp_venv)

        shutil.copytree(str(src_python), str(service_dir), dirs_exist_ok=True,
                        ignore=shutil.ignore_patterns("venv", "__pycache__", "*.pyc"))

        if had_venv and tmp_venv.is_dir():
            tmp_venv.rename(service_dir / "venv")
    elif service_dir != src_python:
        service_dir.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(str(src_python), str(service_dir),
                        ignore=shutil.ignore_patterns("venv", "__pycache__", "*.pyc"))

    # --- Create virtual environment ---
    venv_dir = service_dir / "venv"
    result["venv_dir"] = str(venv_dir)
    python_exe = sys.executable  # The Python running this script

    if not venv_dir.is_dir():
        log.info("  Creating virtual environment ...")
        r = _run([python_exe, "-m", "venv", str(venv_dir)], check=False)
        if r.returncode != 0:
            _add_error("PythonService", f"Failed to create venv: {(r.stderr or '')[:200]}",
                       f"{python_exe} -m venv {venv_dir}")
            return result
    else:
        log.info("  Virtual environment already exists.")

    # --- Determine pip / python inside venv ---
    if IS_WINDOWS:
        venv_python = venv_dir / "Scripts" / "python.exe"
        venv_pip = venv_dir / "Scripts" / "pip.exe"
    else:
        venv_python = venv_dir / "bin" / "python"
        venv_pip = venv_dir / "bin" / "pip"

    # --- Install requirements ---
    req_file = service_dir / "requirements.txt"
    if req_file.is_file():
        log.info("  Installing Python dependencies ...")
        r = _run([str(venv_pip), "install", "-r", str(req_file)], check=False, timeout=300)
        if r.returncode != 0:
            _add_error("PythonService",
                       f"pip install failed: {(r.stderr or '')[:300]}",
                       f"{venv_pip} install -r {req_file}")
            return result
        log.info("  Dependencies installed.")
    else:
        _add_warning(f"requirements.txt not found at {req_file}")

    # --- Linux: create systemd service ---
    if IS_LINUX:
        _create_python_systemd_service(service_dir, venv_python, args, result)

    # Set ownership on Linux
    if IS_LINUX:
        _run(["chown", "-R", "tomcat:tomcat", str(service_dir)], check=False)

    result["installed"] = True
    log.info("  Python validation service ready at %s", service_dir)
    return result


def _create_python_systemd_service(service_dir: Path, venv_python: Path,
                                   args: argparse.Namespace,
                                   result: Dict[str, Any]) -> None:
    """Create a systemd unit for the FastAPI validation service."""
    venv_uvicorn = service_dir / "venv" / "bin" / "uvicorn"

    # Build environment variables for DB connection
    db_pass = getattr(args, "_db_pass_actual", args.db_pass) or ""
    env_vars = (
        f'"DB_HOST=127.0.0.1" '
        f'"DB_PORT={args.db_port}" '
        f'"DB_NAME={args.db_name}" '
        f'"DB_USERNAME={args.db_user}" '
        f'"DB_PASSWORD={db_pass}"'
    )

    service_content = textwrap.dedent(f"""\
        [Unit]
        Description=BUDG Bulk Validation Service (Python/FastAPI)
        After=network.target mariadb.service
        Wants=mariadb.service

        [Service]
        Type=simple
        User=tomcat
        Group=tomcat
        WorkingDirectory={service_dir}
        Environment={env_vars}
        ExecStart={venv_uvicorn} bulk_validation_service:app --host 127.0.0.1 --port 8000
        Restart=on-failure
        RestartSec=5
        StandardOutput=journal
        StandardError=journal

        [Install]
        WantedBy=multi-user.target
    """)

    svc_path = Path("/etc/systemd/system/budg-validator.service")
    _backup(svc_path)
    svc_path.write_text(service_content, encoding="utf-8")
    _run(["systemctl", "daemon-reload"], check=False)
    _run(["systemctl", "enable", "budg-validator"], check=False)
    result["systemd_service"] = str(svc_path)
    log.info("  systemd service created: %s", svc_path)


# ===================================================================
#  DESKTOP SCRIPTS (START / STOP)
# ===================================================================

def create_desktop_scripts(args: argparse.Namespace, output_dir: Path,
                           python_result: Dict[str, Any]) -> Dict[str, Any]:
    result: Dict[str, Any] = {"start_script": "", "stop_script": ""}
    tomcat_base = Path(args.tomcat_base)
    py_dir = python_result.get("service_dir", "")
    log.info("[Scripts] Creating start/stop scripts ...")

    if IS_WINDOWS:
        # Build the Python service start/stop lines for the batch scripts
        if py_dir:
            py_venv_python = Path(py_dir) / "venv" / "Scripts" / "python.exe"
            py_start_lines = [
                'echo [4/4] Starting Python Validation Service (port 8000) ...',
                f'start "BUDG-Validator" /MIN "{py_venv_python}" "{Path(py_dir) / "bulk_validation_service.py"}"',
            ]
            py_stop_lines = [
                'echo [1/4] Stopping Python Validation Service ...',
                'taskkill /FI "WINDOWTITLE eq BUDG-Validator" /F >nul 2>&1',
            ]
        else:
            py_start_lines = ['rem Python validation service not installed']
            py_stop_lines = []

        start_lines = [
            '@echo off',
            'echo ============================================',
            'echo   Starting BUDG Services ...',
            'echo ============================================',
            'echo.',
            'echo [1/4] Starting MariaDB ...',
            'net start MariaDB',
            'echo [2/4] Starting Elasticsearch ...',
            'net start elasticsearch-service-x64',
            'echo [3/4] Starting Tomcat ...',
            f'cd /d "{tomcat_base}\\bin"',
            'call startup.bat',
            *py_start_lines,
            'echo.',
            'echo ============================================',
            'echo   BUDG is starting at http://localhost:8080',
            'echo   Validation service at http://localhost:8000',
            'echo   (waiting 15 seconds for Tomcat to boot)',
            'echo ============================================',
            'timeout /t 15 /nobreak >nul',
            'start http://localhost:8080',
        ]

        stop_lines = [
            '@echo off',
            'echo ============================================',
            'echo   Stopping BUDG Services ...',
            'echo ============================================',
            'echo.',
            *py_stop_lines,
            'echo [2/4] Stopping Tomcat ...',
            f'cd /d "{tomcat_base}\\bin"',
            'call shutdown.bat',
            'echo [3/4] Stopping Elasticsearch ...',
            'net stop elasticsearch-service-x64',
            'echo [4/4] Stopping MariaDB ...',
            'net stop MariaDB',
            'echo.',
            'echo   All services stopped.',
            'pause',
        ]

        start_content = "\r\n".join(start_lines) + "\r\n"
        stop_content = "\r\n".join(stop_lines) + "\r\n"

        start_path = output_dir / "Start_BUDG.bat"
        stop_path = output_dir / "Stop_BUDG.bat"
        start_path.write_text(start_content, encoding="utf-8")
        stop_path.write_text(stop_content, encoding="utf-8")

    else:  # Linux
        start_content = textwrap.dedent(f"""\
            #!/bin/bash
            echo "============================================"
            echo "  Starting BUDG Services ..."
            echo "============================================"
            echo "[1/4] Starting MariaDB ..."
            sudo systemctl start mariadb
            echo "[2/4] Starting Elasticsearch ..."
            sudo systemctl start elasticsearch
            echo "[3/4] Starting Python Validation Service ..."
            sudo systemctl start budg-validator
            echo "[4/4] Starting Tomcat ..."
            sudo systemctl start tomcat
            echo ""
            echo "  BUDG is starting at http://localhost:8080"
            echo "  Validation service at http://localhost:8000"
            echo "  (waiting 15 seconds for Tomcat to boot)"
            sleep 15
            echo "  Ready!  Open http://localhost:8080"
        """)

        stop_content = textwrap.dedent(f"""\
            #!/bin/bash
            echo "============================================"
            echo "  Stopping BUDG Services ..."
            echo "============================================"
            echo "[1/4] Stopping Tomcat ..."
            sudo systemctl stop tomcat
            echo "[2/4] Stopping Python Validation Service ..."
            sudo systemctl stop budg-validator
            echo "[3/4] Stopping Elasticsearch ..."
            sudo systemctl stop elasticsearch
            echo "[4/4] Stopping MariaDB ..."
            sudo systemctl stop mariadb
            echo ""
            echo "  All services stopped."
        """)

        start_path = output_dir / "start_budg.sh"
        stop_path = output_dir / "stop_budg.sh"
        start_path.write_text(start_content, encoding="utf-8")
        stop_path.write_text(stop_content, encoding="utf-8")
        start_path.chmod(0o755)
        stop_path.chmod(0o755)

    result["start_script"] = str(start_path)
    result["stop_script"] = str(stop_path)
    log.info("  Start script: %s", start_path)
    log.info("  Stop  script: %s", stop_path)

    # Also copy to Desktop if available
    desktop = _find_desktop()
    if desktop and desktop != output_dir:
        try:
            shutil.copy2(str(start_path), str(desktop / start_path.name))
            shutil.copy2(str(stop_path), str(desktop / stop_path.name))
            if IS_LINUX:
                (desktop / start_path.name).chmod(0o755)
                (desktop / stop_path.name).chmod(0o755)
            log.info("  Copied scripts to Desktop: %s", desktop)
        except OSError:
            _add_warning(f"Could not copy scripts to Desktop ({desktop})")

    return result


# ===================================================================
#  SETUP REPORT
# ===================================================================

def write_setup_report(
    os_info: Dict[str, Any],
    args: argparse.Namespace,
    pre_status: Dict[str, bool],
    java_result: Dict[str, Any],
    maria_result: Dict[str, Any],
    maria_cfg: Dict[str, Any],
    lower_case_result: Dict[str, Any],
    es_result: Dict[str, Any],
    tomcat_result: Dict[str, Any],
    db_import_result: Dict[str, Any],
    war_result: Dict[str, Any],
    python_result: Dict[str, Any],
    scripts_result: Dict[str, Any],
    output_dir: Path,
) -> Path:
    report_path = output_dir / "SETUP_REPORT.txt"
    log.info("[Report] Writing setup report ...")

    L: List[str] = []
    h = L.append

    h("=" * 72)
    h("  BUDG_V2 - SERVER SETUP REPORT")
    h("=" * 72)
    h("")
    h(f"  Generated : {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    h(f"  OS        : {os_info['platform']}")
    h(f"  Distro    : {os_info.get('distro_id', 'N/A')} ({os_info.get('distro_family', 'N/A')})")
    h(f"  Python    : {platform.python_version()}")
    h(f"  Script    : {Path(__file__).resolve()}")
    h("")

    # --- Components ---
    h("-" * 72)
    h("  COMPONENT STATUS")
    h("-" * 72)

    def _comp_status(name: str, result: Dict[str, Any], pre: bool) -> str:
        if result.get("skipped"):
            return "SKIPPED (--skip flag)"
        if pre and not result.get("installed"):
            return "ALREADY INSTALLED (pre-existing)"
        if result.get("installed"):
            return "INSTALLED OK"
        return "FAILED"

    h(f"  Java 17         : {_comp_status('java', java_result, pre_status.get('java', False))}")
    if java_result.get("java_home"):
        h(f"                    JAVA_HOME = {java_result['java_home']}")
    h(f"  MariaDB         : {_comp_status('mariadb', maria_result, pre_status.get('mariadb', False))}")
    h(f"  Elasticsearch   : {_comp_status('elasticsearch', es_result, pre_status.get('elasticsearch', False))}")
    if es_result.get("es_home"):
        h(f"                    ES_HOME = {es_result['es_home']}")
    h(f"  Tomcat          : {_comp_status('tomcat', tomcat_result, pre_status.get('tomcat', False))}")
    h(f"                    CATALINA_BASE = {args.tomcat_base}")
    if python_result.get("skipped"):
        h(f"  Python Service  : SKIPPED (--skip-python-service)")
    elif python_result.get("installed"):
        h(f"  Python Service  : INSTALLED OK  (port 8000)")
        h(f"                    Dir = {python_result.get('service_dir', 'N/A')}")
        if python_result.get("systemd_service"):
            h(f"                    systemd = {python_result['systemd_service']}")
    else:
        h(f"  Python Service  : FAILED (see errors below)")
    h("")

    # --- Database ---
    h("-" * 72)
    h("  DATABASE CONFIGURATION")
    h("-" * 72)
    if maria_cfg.get("db_created"):
        h(f"  Database : {args.db_name}  [CREATED]")
        h(f"  User     : {args.db_user}  [CREATED]")
        h(f"  Password : {_mask(maria_cfg.get('db_pass', ''))}")
        h(f"  Port     : {args.db_port}")
    else:
        h("  Database configuration: FAILED (see errors below)")
    if lower_case_result:
        v = lower_case_result.get("verified_value")
        if v == 1:
            h(f"  lower_case_table_names : 1 (OK — table names case-insensitive)")
        elif v == 0:
            h(f"  lower_case_table_names : 0 (case-sensitive — may cause table name issues)")
        if lower_case_result.get("config_path"):
            h(f"  MariaDB config (lower_case) : {lower_case_result['config_path']}")
    h("")

    if db_import_result.get("imported"):
        h(f"  SQL Import : {args.sql_file}  [SUCCESS]")
    elif db_import_result.get("skipped"):
        h(f"  SQL Import : SKIPPED (file not found)")
    else:
        h(f"  SQL Import : FAILED (see errors below)")
        if db_import_result.get("error_log"):
            h(f"  Error log  : {db_import_result['error_log']}")
    h("")

    # --- WAR ---
    h("-" * 72)
    h("  WAR DEPLOYMENT")
    h("-" * 72)
    if war_result.get("deployed"):
        h(f"  ROOT.war deployed to: {args.tomcat_base}/webapps/ROOT.war  [OK]")
    elif war_result.get("skipped"):
        h(f"  WAR deployment: SKIPPED (file not found)")
    else:
        h(f"  WAR deployment: FAILED")
    h("")

    # --- App directories (bulk, uploads, logs) ---
    h("-" * 72)
    h("  APP DIRECTORIES (Tomcat / Python read-write)")
    h("-" * 72)
    app_base_str = "/opt/budg_v2" if IS_LINUX else str(Path(args.install_dir) / "app")
    h(f"  app_base : {app_base_str}  (bulk, uploads/documents, logs, data, etc.)")
    h("  Linux: ownership set to tomcat:tomcat so Tomcat and Python service can write.")
    h("  Windows: ensure the account that runs Tomcat has write access to app_base.")
    h("")

    # --- Scripts ---
    h("-" * 72)
    h("  START / STOP SCRIPTS")
    h("-" * 72)
    h(f"  Start : {scripts_result.get('start_script', 'N/A')}")
    h(f"  Stop  : {scripts_result.get('stop_script', 'N/A')}")
    h("")

    # --- Errors ---
    if _errors:
        h("-" * 72)
        h("  ERRORS  (%d)" % len(_errors))
        h("-" * 72)
        for i, err in enumerate(_errors, 1):
            h(f"  {i}. [{err['step']}] {err['message']}")
            if err.get("manual_fix"):
                h(f"     FIX: {err['manual_fix']}")
            h("")

    # --- Warnings ---
    if _warnings:
        h("-" * 72)
        h("  WARNINGS  (%d)" % len(_warnings))
        h("-" * 72)
        for w in _warnings:
            h(f"  - {w}")
        h("")

    # --- SELinux / Firewall hints ---
    h("-" * 72)
    h("  MANUAL FIXES (if needed)")
    h("-" * 72)
    h(textwrap.dedent("""\
      SELinux (RHEL/CentOS/Alma):
        If Tomcat cannot read/write to custom paths:
          sudo semanage fcontext -a -t tomcat_var_lib_t "/opt/budg_v2(/.*)?"
          sudo restorecon -Rv /opt/budg_v2
        To temporarily set SELinux to permissive:
          sudo setenforce 0

      Firewall:
        Linux (firewalld):
          sudo firewall-cmd --permanent --add-port=8080/tcp
          sudo firewall-cmd --reload
        Linux (ufw):
          sudo ufw allow 8080/tcp
        Windows:
          netsh advfirewall firewall add rule name="Tomcat" dir=in action=allow protocol=TCP localport=8080

      MariaDB auth issues:
        If root uses unix_socket auth and you need password auth:
          sudo mariadb -e "ALTER USER 'root'@'localhost' IDENTIFIED VIA mysql_native_password USING PASSWORD('yourpass');"

      MariaDB lower_case_table_names (Linux):
        Set before creating DB/importing SQL to avoid table name case issues. If auto step failed:
          sudo vi /etc/my.cnf.d/server.cnf   (or /etc/mysql/mariadb.conf.d/50-server.cnf)
          Under [mysqld] add: lower_case_table_names=1
          sudo systemctl restart mariadb
        Verify: mysql -e "SHOW VARIABLES LIKE 'lower_case_table_names';"  (1 = OK, 0 = case-sensitive)

      Bulk/upload AccessDeniedException (Linux):
        App dirs (bulk, uploads, logs) are created with tomcat:tomcat ownership. If Tomcat or Python
        still cannot write, run: sudo chown -R tomcat:tomcat /opt/budg_v2

      Bulk/upload AccessDeniedException (Windows):
        Ensure the user account that runs Tomcat has write permission to app_base (e.g. run Tomcat
        as the same user that ran the installer, or grant write access to the Tomcat service account).

      Elasticsearch not starting:
        Check logs: journalctl -u elasticsearch -n 50
        Or: /var/log/elasticsearch/elasticsearch.log
        Common fix: increase vm.max_map_count:
          sudo sysctl -w vm.max_map_count=262144
          echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
    """))

    # --- Next steps ---
    h("-" * 72)
    h("  NEXT STEPS")
    h("-" * 72)
    h("")
    tomcat_base_str = args.tomcat_base
    db_pass_actual = maria_cfg.get("db_pass", args.db_pass or "YOUR_PASSWORD")

    if IS_LINUX:
        h(f"  1. Run the application installer:")
        h(f"     sudo python3 installer.py \\")
        h(f"         --tomcat-base {tomcat_base_str} \\")
        h(f"         --app-base /opt/budg_v2 \\")
        h(f"         --db-host 127.0.0.1 --db-port {args.db_port} --db-name {args.db_name} \\")
        h(f"         --db-user {args.db_user} --db-pass '{_mask(db_pass_actual)}' \\")
        h(f"         --run-user tomcat --service-name tomcat")
    else:
        h(f"  1. Run the application installer:")
        h(f'     python installer.py ^')
        h(f'         --tomcat-base "{tomcat_base_str}" ^')
        h(f'         --app-base "{args.install_dir}\\app" ^')
        h(f'         --db-host 127.0.0.1 --db-port {args.db_port} --db-name {args.db_name} ^')
        h(f'         --db-user {args.db_user} --db-pass "{_mask(db_pass_actual)}" ^')
        h(f"         --write-setenv")

    h("")
    h(f"  2. Use the start/stop scripts:")
    h(f"     Start: {scripts_result.get('start_script', 'N/A')}")
    h(f"     Stop : {scripts_result.get('stop_script', 'N/A')}")
    h("")
    h(f"  3. Open browser: http://localhost:8080")
    h("")
    h("=" * 72)
    h("  END OF REPORT")
    h("=" * 72)

    report_text = "\n".join(L)
    report_path.write_text(report_text, encoding="utf-8")
    log.info("  Report: %s", report_path)

    # Copy to Desktop
    desktop = _find_desktop()
    if desktop and desktop != output_dir:
        try:
            shutil.copy2(str(report_path), str(desktop / report_path.name))
            log.info("  Report copied to Desktop: %s", desktop)
        except OSError:
            pass

    return report_path


# ===================================================================
#  INSTALL CONFIG JSON (for installer.py)
# ===================================================================

def write_install_config(
    args: argparse.Namespace,
    java_result: Dict[str, Any],
    maria_cfg: Dict[str, Any],
    es_result: Dict[str, Any],
    output_dir: Path,
) -> Path:
    """Write a JSON config that installer.py can reference."""
    db_pass = maria_cfg.get("db_pass", args.db_pass or "")

    config = {
        "generated_at": datetime.datetime.now().isoformat(),
        "tomcat_base": str(args.tomcat_base),
        "app_base": str(Path("/opt/budg_v2") if IS_LINUX else Path(args.install_dir) / "app"),
        "java_home": java_result.get("java_home", ""),
        "es_home": es_result.get("es_home", ""),
        "db_host": "127.0.0.1",
        "db_port": args.db_port,
        "db_name": args.db_name,
        "db_user": args.db_user,
        "db_pass": db_pass,
    }

    config_path = output_dir / "install_config.json"
    config_path.write_text(json.dumps(config, indent=2, ensure_ascii=False), encoding="utf-8")
    log.info("  Config written: %s", config_path)
    return config_path


# ===================================================================
#  ARGUMENT PARSER
# ===================================================================

def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="BUDG_V2 Server Prerequisites Installer",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent("""\
            Examples:
              Linux:
                sudo python3 setup_server.py --sql-file ./project.sql --war-file ./ROOT.war
              Windows (as Admin):
                python setup_server.py --sql-file project.sql --war-file ROOT.war
              Offline:
                python setup_server.py --offline-dir ./packages/
        """),
    )

    # Paths
    p.add_argument("--install-dir", default=None, dest="install_dir",
                   help="Base install directory on Windows (default: C:\\budg_server)")
    p.add_argument("--tomcat-base", default=None, dest="tomcat_base",
                   help="Tomcat install path (default: /opt/tomcat or install-dir\\tomcat)")

    # Versions
    p.add_argument("--tomcat-version", default=DEFAULT_TOMCAT_VERSION, dest="tomcat_version")
    p.add_argument("--es-version", default=DEFAULT_ES_VERSION, dest="es_version")
    p.add_argument("--mariadb-version", default=DEFAULT_MARIADB_VERSION, dest="mariadb_version")

    # Database
    p.add_argument("--db-name", default="project", dest="db_name")
    p.add_argument("--db-user", default="budg_user", dest="db_user")
    p.add_argument("--db-pass", default="", dest="db_pass",
                   help="App DB user password (generated if empty)")
    p.add_argument("--db-root-pass", default="", dest="db_root_pass",
                   help="MariaDB root password (for Windows MSI; on Linux uses unix_socket)")
    p.add_argument("--db-port", default="3306", dest="db_port")

    # Files
    p.add_argument("--sql-file", default=str(SCRIPT_DIR / "project.sql"), dest="sql_file")
    p.add_argument("--war-file", default=str(SCRIPT_DIR / "ROOT.war"), dest="war_file")

    # Skip flags
    p.add_argument("--skip-java", action="store_true", dest="skip_java")
    p.add_argument("--skip-mariadb", action="store_true", dest="skip_mariadb")
    p.add_argument("--skip-elasticsearch", action="store_true", dest="skip_elasticsearch")
    p.add_argument("--skip-tomcat", action="store_true", dest="skip_tomcat")
    p.add_argument("--skip-db-import", action="store_true", dest="skip_db_import")
    p.add_argument("--skip-python-service", action="store_true", dest="skip_python_service",
                   help="Skip Python bulk-validation service setup")

    # Offline / checksum
    p.add_argument("--offline-dir", default=None, dest="offline_dir",
                   help="Directory with pre-downloaded packages (skip internet downloads)")

    # Verbosity
    p.add_argument("-v", "--verbose", action="store_true")

    args = p.parse_args(argv)

    # Resolve defaults that depend on OS
    if args.install_dir is None:
        args.install_dir = "C:\\budg_server" if IS_WINDOWS else "/opt"

    if args.tomcat_base is None:
        if IS_LINUX:
            args.tomcat_base = "/opt/tomcat"
        else:
            args.tomcat_base = str(Path(args.install_dir) / "tomcat")

    if args.offline_dir:
        args.offline_dir_path = Path(args.offline_dir)
    else:
        args.offline_dir_path = None

    return args


# ===================================================================
#  MAIN
# ===================================================================

def main() -> int:
    args = parse_args()
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    log.info("=" * 62)
    log.info("  BUDG_V2 Server Prerequisites Installer")
    log.info("  %s | Python %s", platform.platform(), platform.python_version())
    log.info("=" * 62)
    log.info("")

    # --- Admin check ---
    check_admin()

    # --- OS detection ---
    log.info("[Step 1] Detecting OS ...")
    os_info = detect_os()
    log.info("  System: %s  |  Family: %s  |  PkgMgr: %s",
             os_info["system"], os_info.get("distro_family", "N/A"), os_info.get("pkg_mgr", "N/A"))

    # --- Check prerequisites ---
    log.info("")
    log.info("[Step 2] Checking installed software ...")
    tomcat_base = Path(args.tomcat_base)
    pre_status = check_prerequisites(os_info, tomcat_base)

    # --- Install Java ---
    log.info("")
    if args.skip_java or pre_status.get("java"):
        log.info("[Step 3] Java 17: %s", "SKIP (--skip-java)" if args.skip_java else "already installed")
        java_result: Dict[str, Any] = {"installed": pre_status.get("java", False), "skipped": True, "java_home": ""}
    else:
        java_result = install_java(os_info, args)

    # --- Install MariaDB ---
    log.info("")
    if args.skip_mariadb or pre_status.get("mariadb"):
        log.info("[Step 4] MariaDB: %s", "SKIP (--skip-mariadb)" if args.skip_mariadb else "already installed")
        maria_result: Dict[str, Any] = {"installed": pre_status.get("mariadb", False), "skipped": True}
    else:
        maria_result = install_mariadb(os_info, args)

    # --- lower_case_table_names=1 (Linux) + verify (before DB creation / import) ---
    lower_case_result: Dict[str, Any] = {}
    if not args.skip_mariadb and (maria_result.get("installed") or pre_status.get("mariadb")):
        log.info("")
        log.info("[Step 4a] MariaDB lower_case_table_names ...")
        lower_case_result = configure_mariadb_lower_case_tables(args)

    # --- Configure MariaDB ---
    log.info("")
    maria_cfg: Dict[str, Any] = {}
    if not args.skip_mariadb and (maria_result.get("installed") or pre_status.get("mariadb")):
        maria_cfg = configure_mariadb(args)
    else:
        log.info("[Step 4b] MariaDB config: SKIP")
        maria_cfg = {"db_created": False, "skipped": True}

    # --- Install Elasticsearch ---
    log.info("")
    if args.skip_elasticsearch or pre_status.get("elasticsearch"):
        log.info("[Step 5] Elasticsearch: %s",
                 "SKIP (--skip-elasticsearch)" if args.skip_elasticsearch else "already installed")
        es_result: Dict[str, Any] = {"installed": pre_status.get("elasticsearch", False), "skipped": True,
                                      "es_home": "/usr/share/elasticsearch" if IS_LINUX else ""}
    else:
        es_result = install_elasticsearch(os_info, args)

    # --- Configure Elasticsearch ---
    if not args.skip_elasticsearch and (es_result.get("installed") or pre_status.get("elasticsearch")):
        configure_elasticsearch(args, es_result)

    # --- Install Tomcat ---
    log.info("")
    if args.skip_tomcat or pre_status.get("tomcat"):
        log.info("[Step 6] Tomcat: %s", "SKIP (--skip-tomcat)" if args.skip_tomcat else "already installed")
        tomcat_result: Dict[str, Any] = {"installed": pre_status.get("tomcat", False), "skipped": True,
                                          "tomcat_base": str(tomcat_base)}
    else:
        tomcat_result = install_tomcat(os_info, args)

    # --- Ensure app directories (bulk, uploads, logs) for Tomcat/Python read-write ---
    log.info("")
    log.info("[Step 6a] Ensuring app directories ...")
    ensure_app_dirs(args)

    # --- Import Database ---
    log.info("")
    if args.skip_db_import:
        log.info("[Step 7] Database import: SKIP (--skip-db-import)")
        db_import_result: Dict[str, Any] = {"skipped": True}
    else:
        db_import_result = import_database(args)

    # --- Deploy WAR ---
    log.info("")
    log.info("[Step 8] Deploying WAR ...")
    war_result = deploy_war(args)

    # --- Python Validation Service ---
    log.info("")
    if args.skip_python_service:
        log.info("[Step 9] Python service: SKIP (--skip-python-service)")
        python_result: Dict[str, Any] = {"installed": False, "skipped": True, "service_dir": ""}
    else:
        python_result = setup_python_service(args)

    # --- Output directory ---
    output_dir = _find_output_dir(Path(args.install_dir) if IS_WINDOWS else None)

    # --- Desktop Scripts ---
    log.info("")
    scripts_result = create_desktop_scripts(args, output_dir, python_result)

    # --- Report ---
    log.info("")
    report_path = write_setup_report(
        os_info, args, pre_status,
        java_result, maria_result, maria_cfg, lower_case_result,
        es_result, tomcat_result, db_import_result,
        war_result, python_result, scripts_result, output_dir,
    )

    # --- Config JSON ---
    config_path = write_install_config(args, java_result, maria_cfg, es_result, output_dir)

    # --- Summary ---
    log.info("")
    log.info("=" * 62)
    if _errors:
        log.warning("  Setup completed with %d error(s).", len(_errors))
        log.warning("  Check report for details and manual fixes:")
    else:
        log.info("  Setup completed successfully!")
    log.info("  Report : %s", report_path)
    log.info("  Config : %s", config_path)
    log.info("=" * 62)

    return 1 if _errors else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        log.info("Interrupted.")
        sys.exit(130)
    except Exception as exc:
        log.error("Unexpected error: %s", exc, exc_info=True)
        sys.exit(1)
