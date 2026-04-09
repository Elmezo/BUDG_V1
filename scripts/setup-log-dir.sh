#!/bin/bash
# Create application log directory for BUDG (prod_audit.log, prod_app.log, prod_errors.log).
# Run on the server with sudo, e.g.: sudo ./setup-log-dir.sh
# Set LOG_DIR and TOMCAT_USER if different from defaults.

LOG_DIR="${LOG_DIR:-/var/log/budg_v2}"
TOMCAT_USER="${TOMCAT_USER:-tomcat}"

set -e
echo "Creating log directory: $LOG_DIR (owner: $TOMCAT_USER)"
mkdir -p "$LOG_DIR"
chown -R "$TOMCAT_USER:$TOMCAT_USER" "$LOG_DIR"
chmod 755 "$LOG_DIR"
echo "Done. Ensure Tomcat is started with -DLOG_DIR=$LOG_DIR (e.g. in CATALINA_OPTS)."
