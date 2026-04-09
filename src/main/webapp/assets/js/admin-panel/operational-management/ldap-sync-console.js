// LDAP Sync Console Component
// This file provides additional console utilities if needed
// Main console functionality is in ldap-sync-panel.js

/**
 * Format console message with timestamp and log level
 */
function formatConsoleMessage(level, message) {
    const timestamp = new Date().toLocaleTimeString();
    return `[${timestamp}] [${level}] ${message}`;
}

/**
 * Clear console
 */
function clearLdapSyncConsole() {
    const console = document.getElementById('ldapSyncConsole');
    if (console) {
        console.textContent = '';
    }
}

/**
 * Export console content to text file
 */
function exportConsoleLog() {
    const console = document.getElementById('ldapSyncConsole');
    if (!console || !console.textContent) {
        if (window.showAdminNotification) window.showAdminNotification(
            typeof adminT === 'function' ? adminT('adminPanel.ldapSync.noContentToExport', 'No console content to export.') : 'No console content to export.',
            'warning'
        );
        return;
    }
    
    const content = console.textContent;
    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ldap-sync-log-${new Date().toISOString().replace(/[:.]/g, '-')}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

