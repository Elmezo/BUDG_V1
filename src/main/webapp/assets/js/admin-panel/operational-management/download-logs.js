// Download Logs functionality for Operational Management

function showDownloadLogsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.downloadLogs');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    contentArea.innerHTML = `
        <div class="download-logs-content">
            <div class="page-header">
                <div class="header-content">
                    <div class="header-icon-wrapper">
                        <i class="fas fa-file-archive"></i>
                    </div>
                    <div class="header-text">
                        <h2>${T('adminPanel.downloadLogs.title', 'Download Logs')}</h2>
                        <p class="page-description">${T('adminPanel.downloadLogs.pageDescription', 'Download and manage system logs and audit trails')}</p>
                    </div>
                </div>
            </div>
            
            <div class="logs-download-section">
                <div class="download-logs-card card">
                    <div class="card-header">
                        <div class="card-header-content">
                            <div class="card-icon-wrapper">
                                <i class="fas fa-database"></i>
                            </div>
                            <div class="card-title-wrapper">
                                <h3>${T('adminPanel.downloadLogs.systemLogsArchive', 'System Logs Archive')}</h3>
                                <p class="card-subtitle">${T('adminPanel.downloadLogs.cardSubtitle', 'Download all log files as a ZIP archive')}</p>
                            </div>
                        </div>
                    </div>
                    <div class="card-body">
                        <div class="log-info-section">
                            <p class="info-description">${T('adminPanel.downloadLogs.infoDescription', 'The archive includes all log files from the system, organized by type and date.')}</p>
                            
                            <div class="log-types-grid">
                                <div class="log-type-card">
                                    <div class="log-type-icon error">
                                        <i class="fas fa-exclamation-triangle"></i>
                                    </div>
                                    <div class="log-type-content">
                                        <h4>${T('adminPanel.downloadLogs.errorLogs', 'Error Logs')}</h4>
                                        <p class="log-type-pattern">prod_errors-*.log</p>
                                        <p class="log-type-desc">${T('adminPanel.downloadLogs.errorLogsDesc', 'All error-level events and exceptions')}</p>
                                    </div>
                                </div>
                                
                                <div class="log-type-card">
                                    <div class="log-type-icon app">
                                        <i class="fas fa-file-alt"></i>
                                    </div>
                                    <div class="log-type-content">
                                        <h4>${T('adminPanel.downloadLogs.applicationLogs', 'Application Logs')}</h4>
                                        <p class="log-type-pattern">prod_app-*.log</p>
                                        <p class="log-type-desc">${T('adminPanel.downloadLogs.applicationLogsDesc', 'All application events and activities')}</p>
                                    </div>
                                </div>
                                
                                <div class="log-type-card">
                                    <div class="log-type-icon audit">
                                        <i class="fas fa-shield-alt"></i>
                                    </div>
                                    <div class="log-type-content">
                                        <h4>${T('adminPanel.downloadLogs.auditLogs', 'Audit Logs')}</h4>
                                        <p class="log-type-pattern">prod_audit-*.log</p>
                                        <p class="log-type-desc">${T('adminPanel.downloadLogs.auditLogsDesc', 'Immutable audit trail for compliance')}</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="download-action-section">
                            <button id="downloadLogsBtn" class="btn btn-primary download-logs-button">
                                <i class="fas fa-download"></i>
                                <span>${T('adminPanel.downloadLogs.downloadArchive', 'Download Logs Archive')}</span>
                            </button>
                            <div id="downloadLogsStatus" class="download-status" style="display: none;"></div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    const downloadBtn = document.getElementById('downloadLogsBtn');
    if (downloadBtn) {
        downloadBtn.addEventListener('click', handleDownloadLogs);
    }
}

async function handleDownloadLogs() {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const downloadBtn = document.getElementById('downloadLogsBtn');
    const statusDiv = document.getElementById('downloadLogsStatus');
    
    if (!downloadBtn || !statusDiv) {
        return;
    }
    
    downloadBtn.disabled = true;
    downloadBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> <span>' + T('adminPanel.downloadLogs.downloading', 'Downloading...') + '</span>';
    statusDiv.style.display = 'block';
    statusDiv.className = 'download-status status-info';
    statusDiv.innerHTML = '<i class="fas fa-info-circle"></i> ' + T('adminPanel.downloadLogs.preparingArchive', 'Preparing log archive...');
    
    try {
        const response = await fetch('/admin/logs/download', {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/zip'
            }
        });
        
        if (!response.ok) {
            let errorMessage = `Server error: ${response.status}`;
            try {
                const errorData = await response.json();
                if (errorData.error) {
                    errorMessage = errorData.error;
                }
            } catch (e) { /* ignore */ }
            
            if (response.status === 403) {
                throw new Error(T('adminPanel.downloadLogs.accessDenied', 'Access denied: Admin privileges required'));
            } else if (response.status === 404) {
                if (errorMessage.includes('No log files found')) {
                    throw new Error(T('adminPanel.downloadLogs.noLogFiles', 'No log files found...'));
                } else if (errorMessage.includes('Logs directory not found')) {
                    throw new Error(T('adminPanel.downloadLogs.logsDirNotFound', 'Logs directory not found...'));
                } else {
                    throw new Error(errorMessage);
                }
            } else {
                throw new Error(errorMessage);
            }
        }
        
        const contentDisposition = response.headers.get('Content-Disposition');
        let filename = 'axon-logs.zip';
        if (contentDisposition) {
            const filenameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
            if (filenameMatch && filenameMatch[1]) {
                filename = filenameMatch[1].replace(/['"]/g, '');
            }
        }
        
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        
        statusDiv.className = 'download-status status-success';
        statusDiv.innerHTML = '<i class="fas fa-check-circle"></i> ' + T('adminPanel.downloadLogs.downloadSuccess', 'Log archive downloaded successfully!');
        
    } catch (error) {
        console.error('Error downloading logs:', error);
        statusDiv.className = 'download-status status-error';
        statusDiv.innerHTML = '<i class="fas fa-exclamation-circle"></i> ' + (error.message || T('adminPanel.downloadLogs.downloadFailed', 'Failed to download logs'));
    } finally {
        downloadBtn.disabled = false;
        downloadBtn.innerHTML = '<i class="fas fa-download"></i> <span>' + T('adminPanel.downloadLogs.downloadArchive', 'Download Logs Archive') + '</span>';
        setTimeout(() => {
            statusDiv.style.display = 'none';
        }, 5000);
    }
}
