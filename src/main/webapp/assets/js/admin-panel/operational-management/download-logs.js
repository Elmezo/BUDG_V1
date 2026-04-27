// Download Logs functionality for Operational Management

const downloadLogsDeleteState = {
    status: null,
    password: ''
};

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
                                <p class="card-subtitle">${T('adminPanel.downloadLogs.cardSubtitle', 'Download a formatted ZIP organized into errors, application, and combined logs')}</p>
                            </div>
                        </div>
                    </div>
                    <div class="card-body">
                        <div class="log-info-section">
                            <p class="info-description">${T('adminPanel.downloadLogs.infoDescription', 'The ZIP contains formatted logs only. Each type includes by-day files and one all-days file, with literal \\n separators expanded into real line breaks.')}</p>
                            
                            <div class="log-types-grid">
                                <div class="log-type-card">
                                    <div class="log-type-icon error">
                                        <i class="fas fa-exclamation-triangle"></i>
                                    </div>
                                    <div class="log-type-content">
                                        <h4>${T('adminPanel.downloadLogs.errorLogs', 'Error Logs')}</h4>
                                        <p class="log-type-pattern">errors/by-day + errors-all-days.log</p>
                                        <p class="log-type-desc">${T('adminPanel.downloadLogs.errorLogsDesc', 'Error-only logs from prod_errors files')}</p>
                                    </div>
                                </div>
                                
                                <div class="log-type-card">
                                    <div class="log-type-icon app">
                                        <i class="fas fa-file-alt"></i>
                                    </div>
                                    <div class="log-type-content">
                                        <h4>${T('adminPanel.downloadLogs.applicationLogs', 'Application Logs')}</h4>
                                        <p class="log-type-pattern">application/by-day + application-all-days.log</p>
                                        <p class="log-type-desc">${T('adminPanel.downloadLogs.applicationLogsDesc', 'Full application logs from prod_app files')}</p>
                                    </div>
                                </div>
                                
                                <div class="log-type-card">
                                    <div class="log-type-icon audit">
                                        <i class="fas fa-shield-alt"></i>
                                    </div>
                                    <div class="log-type-content">
                                        <h4>${T('adminPanel.downloadLogs.combinedLogs', 'Combined Logs')}</h4>
                                        <p class="log-type-pattern">combined/by-day + combined-all-days.log</p>
                                        <p class="log-type-desc">${T('adminPanel.downloadLogs.combinedLogsDesc', 'Unified logs from application, error, and audit sources')}</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="download-action-section">
                            <div class="logs-action-buttons">
                                <button id="downloadLogsBtn" class="btn btn-primary download-logs-button">
                                    <i class="fas fa-download"></i>
                                    <span>${T('adminPanel.downloadLogs.downloadArchive', 'Download Logs Archive')}</span>
                                </button>
                                <button id="deleteLogsBtn" class="btn btn-danger delete-logs-button">
                                    <i class="fas fa-trash-alt"></i>
                                    <span>${T('adminPanel.downloadLogs.deleteLogs', 'Delete Logs')}</span>
                                </button>
                            </div>
                            <p id="lastDeletedLogsUser" class="last-deleted-log-user">${T('adminPanel.downloadLogs.lastDeletedUserLoading', 'Last deleted log user: loading...')}</p>
                            <div id="downloadLogsStatus" class="download-status" style="display: none;"></div>
                        </div>
                    </div>
                </div>
            </div>

            <div id="deleteLogsModal" class="logs-delete-modal-overlay" aria-hidden="true">
                <div class="logs-delete-modal" role="dialog" aria-modal="true" aria-labelledby="deleteLogsModalTitle">
                    <div class="logs-delete-modal-header">
                        <h3 id="deleteLogsModalTitle">${T('adminPanel.downloadLogs.deleteModalTitle', 'Delete old logs')}</h3>
                        <button id="deleteLogsModalClose" class="logs-delete-modal-close" type="button" aria-label="${T('adminPanel.downloadLogs.close', 'Close')}">&times;</button>
                    </div>
                    <div class="logs-delete-modal-body">
                        <div id="deleteLogsStepConfirm" class="logs-delete-step">
                            <p class="logs-delete-warning">${T('adminPanel.downloadLogs.deleteWarning', 'This action permanently deletes log files older than the last two days. The latest two days and active log files will be kept.')}</p>
                            <p class="logs-delete-note">${T('adminPanel.downloadLogs.superAdminOnly', 'Only a Super Admin can delete logs.')}</p>
                        </div>
                        <div id="deleteLogsStepPassword" class="logs-delete-step" style="display: none;">
                            <label for="deleteLogsPassword">${T('adminPanel.downloadLogs.passwordLabel', 'Enter your password')}</label>
                            <input id="deleteLogsPassword" type="password" autocomplete="current-password" />
                        </div>
                        <div id="deleteLogsStepPhrase" class="logs-delete-step" style="display: none;">
                            <p>${T('adminPanel.downloadLogs.typePhraseInstruction', 'Type this confirmation phrase manually. Copy and paste is disabled:')}</p>
                            <div id="deleteLogsPhraseDisplay" class="logs-delete-phrase"></div>
                            <label for="deleteLogsPhraseInput">${T('adminPanel.downloadLogs.confirmPhraseLabel', 'Confirmation phrase')}</label>
                            <input id="deleteLogsPhraseInput" type="text" autocomplete="off" spellcheck="false" />
                        </div>
                    </div>
                    <div class="logs-delete-modal-footer">
                        <button id="deleteLogsCancelBtn" type="button" class="btn btn-secondary">${T('adminPanel.downloadLogs.cancel', 'Cancel')}</button>
                        <button id="deleteLogsNextBtn" type="button" class="btn btn-danger">${T('adminPanel.downloadLogs.yesContinue', 'Yes, continue')}</button>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    const downloadBtn = document.getElementById('downloadLogsBtn');
    if (downloadBtn) {
        downloadBtn.addEventListener('click', handleDownloadLogs);
    }

    const deleteBtn = document.getElementById('deleteLogsBtn');
    if (deleteBtn) {
        deleteBtn.addEventListener('click', handleDeleteLogsClick);
    }

    document.getElementById('deleteLogsModalClose')?.addEventListener('click', closeDeleteLogsModal);
    document.getElementById('deleteLogsCancelBtn')?.addEventListener('click', closeDeleteLogsModal);
    document.getElementById('deleteLogsModal')?.addEventListener('click', (e) => {
        if (e.target.id === 'deleteLogsModal') closeDeleteLogsModal();
    });
    document.getElementById('deleteLogsNextBtn')?.addEventListener('click', handleDeleteLogsNext);
    ['paste', 'copy', 'cut', 'drop'].forEach(eventName => {
        document.getElementById('deleteLogsPhraseInput')?.addEventListener(eventName, (e) => e.preventDefault());
    });
    ['copy', 'cut', 'contextmenu'].forEach(eventName => {
        document.getElementById('deleteLogsPhraseDisplay')?.addEventListener(eventName, (e) => e.preventDefault());
    });

    loadDeleteLogsStatus();
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

async function loadDeleteLogsStatus() {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const lastDeletedEl = document.getElementById('lastDeletedLogsUser');

    try {
        const response = await fetch('/admin/logs/status', {
            method: 'GET',
            credentials: 'include',
            headers: { 'Accept': 'application/json' }
        });

        if (!response.ok) {
            throw new Error(`Status error: ${response.status}`);
        }

        const data = await response.json();
        downloadLogsDeleteState.status = data;
        renderLastDeletedUser(data);
    } catch (error) {
        console.error('Error loading log deletion status:', error);
        if (lastDeletedEl) {
            lastDeletedEl.textContent = T('adminPanel.downloadLogs.lastDeletedUserUnavailable', 'Last deleted log user: unavailable');
        }
    }
}

function renderLastDeletedUser(data) {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const el = document.getElementById('lastDeletedLogsUser');
    if (!el) return;

    const lastDeleted = data && data.lastDeleted;
    if (!lastDeleted || !lastDeleted.userName) {
        el.textContent = T('adminPanel.downloadLogs.lastDeletedUserNone', 'Last deleted log user: none yet');
        return;
    }

    const at = lastDeleted.deletedAt ? ` (${formatLogDeleteDate(lastDeleted.deletedAt)})` : '';
    el.textContent = T('adminPanel.downloadLogs.lastDeletedUserPrefix', 'Last deleted log user:') + ' ' + lastDeleted.userName + at;
}

function formatLogDeleteDate(value) {
    try {
        return new Date(value).toLocaleString();
    } catch (e) {
        return value;
    }
}

async function handleDeleteLogsClick() {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const statusDiv = document.getElementById('downloadLogsStatus');

    if (!downloadLogsDeleteState.status) {
        await loadDeleteLogsStatus();
    }

    const status = downloadLogsDeleteState.status;
    if (!status || !status.isSuperAdmin) {
        showLogsStatus('status-error', T('adminPanel.downloadLogs.deleteSuperAdminRequired', 'Only a Super Admin can delete logs.'));
        return;
    }

    downloadLogsDeleteState.password = '';
    showDeleteLogsStep('confirm');
    openDeleteLogsModal();
}

function openDeleteLogsModal() {
    const modal = document.getElementById('deleteLogsModal');
    if (modal) {
        modal.classList.add('active');
        modal.setAttribute('aria-hidden', 'false');
    }
}

function closeDeleteLogsModal() {
    const modal = document.getElementById('deleteLogsModal');
    if (modal) {
        modal.classList.remove('active');
        modal.setAttribute('aria-hidden', 'true');
    }
    downloadLogsDeleteState.password = '';
    const passwordInput = document.getElementById('deleteLogsPassword');
    const phraseInput = document.getElementById('deleteLogsPhraseInput');
    if (passwordInput) passwordInput.value = '';
    if (phraseInput) phraseInput.value = '';
}

function showDeleteLogsStep(step) {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const confirmStep = document.getElementById('deleteLogsStepConfirm');
    const passwordStep = document.getElementById('deleteLogsStepPassword');
    const phraseStep = document.getElementById('deleteLogsStepPhrase');
    const nextBtn = document.getElementById('deleteLogsNextBtn');
    const phraseDisplay = document.getElementById('deleteLogsPhraseDisplay');

    if (confirmStep) confirmStep.style.display = step === 'confirm' ? 'block' : 'none';
    if (passwordStep) passwordStep.style.display = step === 'password' ? 'block' : 'none';
    if (phraseStep) phraseStep.style.display = step === 'phrase' ? 'block' : 'none';

    if (nextBtn) {
        nextBtn.dataset.step = step;
        nextBtn.innerHTML = step === 'phrase'
            ? '<i class="fas fa-trash-alt"></i> ' + T('adminPanel.downloadLogs.deleteLogs', 'Delete Logs')
            : T('adminPanel.downloadLogs.yesContinue', 'Yes, continue');
    }

    if (step === 'phrase' && phraseDisplay) {
        phraseDisplay.textContent = getDeleteLogsConfirmationPhrase();
    }

    setTimeout(() => {
        const input = step === 'password'
            ? document.getElementById('deleteLogsPassword')
            : step === 'phrase'
                ? document.getElementById('deleteLogsPhraseInput')
                : null;
        input?.focus();
    }, 50);
}

function getDeleteLogsConfirmationPhrase() {
    const status = downloadLogsDeleteState.status || {};
    const lang = ((window.I18n && window.I18n.currentLocale) || document.documentElement.lang || '').toLowerCase();
    return lang.startsWith('ar')
        ? (status.confirmationPhraseAr || status.confirmationPhraseEn || '')
        : (status.confirmationPhraseEn || status.confirmationPhraseAr || '');
}

async function handleDeleteLogsNext() {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const nextBtn = document.getElementById('deleteLogsNextBtn');
    const step = nextBtn?.dataset.step || 'confirm';

    if (step === 'confirm') {
        showDeleteLogsStep('password');
        return;
    }

    if (step === 'password') {
        const passwordInput = document.getElementById('deleteLogsPassword');
        const password = passwordInput ? passwordInput.value : '';
        if (!password) {
            showLogsStatus('status-error', T('adminPanel.downloadLogs.passwordRequired', 'Password is required.'));
            return;
        }
        const verified = await verifyDeleteLogsPassword(password);
        if (!verified) {
            return;
        }
        downloadLogsDeleteState.password = password;
        showDeleteLogsStep('phrase');
        return;
    }

    if (step === 'phrase') {
        await submitDeleteLogs();
    }
}

async function verifyDeleteLogsPassword(password) {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const nextBtn = document.getElementById('deleteLogsNextBtn');

    if (nextBtn) {
        nextBtn.disabled = true;
        nextBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + T('adminPanel.downloadLogs.verifyingPassword', 'Verifying password...');
    }

    try {
        const response = await fetch('/admin/logs/verify-password', {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ password })
        });

        const data = await response.json().catch(() => ({}));
        if (!response.ok || !data.success) {
            throw new Error(data.error || `Server error: ${response.status}`);
        }
        return true;
    } catch (error) {
        console.error('Error verifying log deletion password:', error);
        showLogsStatus('status-error', mapDeleteLogsError(error.message));
        return false;
    } finally {
        if (nextBtn) {
            nextBtn.disabled = false;
            nextBtn.innerHTML = T('adminPanel.downloadLogs.yesContinue', 'Yes, continue');
        }
    }
}

async function submitDeleteLogs() {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const deleteBtn = document.getElementById('deleteLogsBtn');
    const nextBtn = document.getElementById('deleteLogsNextBtn');
    const phraseInput = document.getElementById('deleteLogsPhraseInput');
    const confirmationText = phraseInput ? phraseInput.value.trim() : '';
    const expectedPhrase = getDeleteLogsConfirmationPhrase();

    if (confirmationText !== expectedPhrase) {
        showLogsStatus('status-error', T('adminPanel.downloadLogs.confirmationMismatch', 'Confirmation phrase does not match.'));
        return;
    }

    if (deleteBtn) deleteBtn.disabled = true;
    if (nextBtn) {
        nextBtn.disabled = true;
        nextBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + T('adminPanel.downloadLogs.deleting', 'Deleting...');
    }
    showLogsStatus('status-info', T('adminPanel.downloadLogs.deletingOldLogs', 'Deleting old log files...'));

    try {
        const response = await fetch('/admin/logs/delete', {
            method: 'DELETE',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                password: downloadLogsDeleteState.password,
                confirmationText
            })
        });

        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            throw new Error(data.error || `Server error: ${response.status}`);
        }

        closeDeleteLogsModal();
        showLogsStatus('status-success', T('adminPanel.downloadLogs.deleteSuccess', 'Old logs deleted successfully.') + ` ${T('adminPanel.downloadLogs.deletedFiles', 'Deleted files')}: ${data.deletedFiles || 0}`);
        await loadDeleteLogsStatus();
    } catch (error) {
        console.error('Error deleting logs:', error);
        showLogsStatus('status-error', mapDeleteLogsError(error.message));
    } finally {
        if (deleteBtn) deleteBtn.disabled = false;
        if (nextBtn) {
            nextBtn.disabled = false;
            showDeleteLogsStep('phrase');
        }
    }
}

function mapDeleteLogsError(message) {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    if (!message) {
        return T('adminPanel.downloadLogs.deleteFailed', 'Failed to delete logs.');
    }
    if (message.includes('Invalid password')) {
        return T('adminPanel.downloadLogs.invalidPassword', 'Invalid password.');
    }
    if (message.includes('Super Admin')) {
        return T('adminPanel.downloadLogs.deleteSuperAdminRequired', 'Only a Super Admin can delete logs.');
    }
    if (message.includes('Confirmation text')) {
        return T('adminPanel.downloadLogs.confirmationMismatch', 'Confirmation phrase does not match.');
    }
    return message;
}

function showLogsStatus(className, message) {
    const statusDiv = document.getElementById('downloadLogsStatus');
    if (!statusDiv) return;
    const icon = className === 'status-success'
        ? 'fa-check-circle'
        : className === 'status-error'
            ? 'fa-exclamation-circle'
            : 'fa-info-circle';
    statusDiv.style.display = 'block';
    statusDiv.className = 'download-status ' + className;
    statusDiv.innerHTML = '<i class="fas ' + icon + '"></i> ' + escapeLogStatusHtml(message);
}

function escapeLogStatusHtml(value) {
    return String(value || '').replace(/[&<>"']/g, (ch) => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    }[ch]));
}
