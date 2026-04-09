// LDAP Sync Panel functionality

function showLdapSyncPanel(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.administratorsPanel');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const loading = T('adminPanel.ldapSync.loading', T('adminPanel.common.loading', 'Loading...'));

    contentArea.innerHTML = `
        <div class="ldap-sync-panel-content" style="padding: 20px; max-width: 1200px; margin: 0 auto;">
            <div class="ldap-sync-header" style="margin-bottom: 30px;">
                <h2 style="color: #248567; margin-bottom: 15px;">${T('adminPanel.ldapSync.title', 'Synchronize With LDAP Server')}</h2>
                <p class="ldap-sync-description" style="line-height: 1.6; color: #666;">
                    ${T('adminPanel.ldapSync.descriptionIntro', 'This command synchronizes LDAP users with BUDG. The synchronization process will:')}
                    <ul style="margin-top: 10px; padding-left: 20px;">
                        <li>${T('adminPanel.ldapSync.bulletFetch', 'Fetch all users from the configured LDAP server')}</li>
                        <li>${T('adminPanel.ldapSync.bulletCreate', 'Create new users in the database')}</li>
                        <li>${T('adminPanel.ldapSync.bulletUpdate', 'Update existing users if attributes have changed')}</li>
                        <li>${T('adminPanel.ldapSync.bulletOrgUnits', 'Create or update organizational units')}</li>
                    </ul>
                </p>
                <div class="ldap-sync-info" style="margin-top: 20px; padding: 15px; background-color: #f8f9fa; border-radius: 6px; border-left: 4px solid #248567;">
                    <p style="margin: 5px 0;"><strong>${T('adminPanel.ldapSync.timeoutSet', 'Timeout Set:')}</strong> ${T('adminPanel.ldapSync.timeoutValue', '3600 seconds (1 hour)')}</p>
                    <p style="margin: 5px 0;"><strong>${T('adminPanel.ldapSync.instruction', 'Instruction:')}</strong> ${T('adminPanel.ldapSync.instructionText', 'Click on Run when ready.')}</p>
                    <p style="margin: 5px 0;"><strong>${T('adminPanel.ldapSync.lastSuccessfulSync', 'Last Successful Sync:')}</strong> <span id="lastSyncTime" style="color: #248567;">${loading}</span></p>
                </div>
            </div>
            
            <div class="ldap-sync-actions" style="margin-bottom: 20px;">
                <button id="runLdapSyncBtn" class="btn btn-primary" style="padding: 12px 24px; font-size: 16px; background: #248567; color: white; border: none; border-radius: 4px; cursor: pointer; font-weight: 500;">
                    <i class="fas fa-play"></i> ${T('adminPanel.ldapSync.run', 'Run')}
                </button>
            </div>
            
            <div id="ldapSyncConsoleContainer" style="display: none; margin-top: 20px;">
                <div class="ldap-sync-console-wrapper" style="border: 1px solid #ddd; border-radius: 6px; overflow: hidden;">
                    <div class="ldap-sync-console-header" style="background-color: #248567; color: white; padding: 12px 15px; display: flex; justify-content: space-between; align-items: center;">
                        <h3 style="margin: 0; font-size: 16px; font-weight: 600;">${T('adminPanel.ldapSync.consoleOutput', 'Console Output')}</h3>
                        <button id="clearConsoleBtn" class="btn btn-secondary" style="padding: 6px 12px; font-size: 12px; background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3); border-radius: 4px; cursor: pointer;">
                            <i class="fas fa-trash"></i> ${T('adminPanel.ldapSync.clear', 'Clear')}
                        </button>
                    </div>
                    <div id="ldapSyncConsole" class="ldap-sync-console" style="background-color: #1e1e1e; color: #d4d4d4; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; padding: 15px; height: 500px; overflow-y: auto; font-size: 13px; line-height: 1.5; white-space: pre-wrap; word-wrap: break-word;"></div>
                </div>
            </div>
        </div>
    `;

    // Load last sync time
    loadLastSyncInfo();

    const runBtn = document.getElementById('runLdapSyncBtn');
    const clearBtn = document.getElementById('clearConsoleBtn');

    if (runBtn) {
        runBtn.addEventListener('click', startLdapSync);
    }

    if (clearBtn) {
        clearBtn.addEventListener('click', () => {
            const console = document.getElementById('ldapSyncConsole');
            if (console) {
                console.textContent = '';
            }
        });
    }
}

function _ldapRunBtnLabel() {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    return '<i class="fas fa-play"></i> ' + T('adminPanel.ldapSync.run', 'Run');
}

function _ldapStartingLabel() {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    return '<i class="fas fa-spinner fa-spin"></i> ' + T('adminPanel.ldapSync.starting', 'Starting...');
}

async function startLdapSync() {
    const runBtn = document.getElementById('runLdapSyncBtn');
    const consoleContainer = document.getElementById('ldapSyncConsoleContainer');
    const console = document.getElementById('ldapSyncConsole');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };

    if (!runBtn || !consoleContainer || !console) {
        console.error('LDAP sync UI elements not found');
        return;
    }

    runBtn.disabled = true;
    runBtn.innerHTML = _ldapStartingLabel();

    consoleContainer.style.display = 'block';
    console.textContent = '';

    try {
        const response = await fetch('/api/admin/ldap/sync/start', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include'
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `HTTP ${response.status}: ${response.statusText}`);
        }

        const result = await response.json();

        if (!result.success) {
            throw new Error(result.error || T('adminPanel.ldapSync.failedToStartSync', 'Failed to start synchronization'));
        }

        const jobId = result.jobId;

        appendToConsole(console, `[INFO] Operation: ${T('adminPanel.ldapSync.title', 'Synchronize With LDAP Server')}`);
        appendToConsole(console, `[INFO] ${T('adminPanel.ldapSync.descriptionIntro', 'This command synchronizes LDAP users with BUDG')}`);
        appendToConsole(console, `[INFO] ${T('adminPanel.ldapSync.timeoutSet', 'Timeout Set:')} 3600 seconds`);
        appendToConsole(console, `[INFO] Synchronization started - Job ID: ${jobId}`);
        appendToConsole(console, '');

        connectLdapSyncWebSocket(jobId, console);

    } catch (error) {
        console.error('Error starting LDAP sync:', error);
        appendToConsole(console, `[ERROR] ${T('adminPanel.ldapSync.failedToStartSync', 'Failed to start synchronization')}: ${error.message}`);
        runBtn.disabled = false;
        runBtn.innerHTML = _ldapRunBtnLabel();
    }
}

function connectLdapSyncWebSocket(jobId, consoleElement) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/ldap-sync/${jobId}`;

    let ws = null;
    let reconnectAttempts = 0;
    const maxReconnectAttempts = 5;

    function connect() {
        try {
            ws = new WebSocket(wsUrl);

            ws.onopen = () => {
                appendToConsole(consoleElement, `[INFO] WebSocket connected successfully`);
                reconnectAttempts = 0;
            };

            ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);

                    if (data.type === 'connected') {
                        appendToConsole(consoleElement, `[INFO] ${data.message}`);
                        return;
                    }

                    if (data.status) {
                        const timestamp = new Date().toLocaleTimeString();
                        const status = data.status;
                        const progress = data.progress || 0;
                        const message = data.message || '';

                        let logLine;
                        if (status === 'Failed') {
                            logLine = `[${timestamp}] [ERROR]`;
                            if (message) {
                                const cleanMessage = message.startsWith('ERROR: ') ? message.substring(7) : message;
                                logLine += ` ${cleanMessage}`;
                            }
                        } else {
                            logLine = `[${timestamp}] [${status}]`;
                            if (progress > 0) {
                                logLine += ` [${progress}%]`;
                            }
                            if (message) {
                                logLine += ` ${message}`;
                            }
                        }

                        appendToConsole(consoleElement, logLine);

                        const runBtn = document.getElementById('runLdapSyncBtn');
                        if (status === 'Completed' || status === 'Failed') {
                            if (runBtn) {
                                runBtn.disabled = false;
                                runBtn.innerHTML = _ldapRunBtnLabel();
                            }

                            if (status === 'Completed' && (data.inserted !== undefined || data.updated !== undefined ||
                                data.orgUnitsCreated !== undefined || data.orgUnitsUpdated !== undefined ||
                                data.failed !== undefined)) {
                                appendToConsole(consoleElement, '');
                                appendToConsole(consoleElement, '=== Synchronization Summary ===');
                                if (data.inserted !== undefined) {
                                    appendToConsole(consoleElement, `Users created: ${data.inserted}`);
                                }
                                if (data.updated !== undefined) {
                                    appendToConsole(consoleElement, `Users updated: ${data.updated}`);
                                }
                                if (data.orgUnitsCreated !== undefined) {
                                    appendToConsole(consoleElement, `Org units created: ${data.orgUnitsCreated}`);
                                }
                                if (data.orgUnitsUpdated !== undefined) {
                                    appendToConsole(consoleElement, `Org units updated: ${data.orgUnitsUpdated}`);
                                }
                                if (data.failed !== undefined) {
                                    appendToConsole(consoleElement, `Skipped/Invalid records: ${data.failed}`);
                                }
                            }

                            if (status === 'Failed' && message) {
                                appendToConsole(consoleElement, '');
                                appendToConsole(consoleElement, '=== Error Details ===');
                                const cleanMessage = message.startsWith('ERROR: ') ? message.substring(7) : message;
                                appendToConsole(consoleElement, cleanMessage);
                            }

                            if (ws) {
                                ws.close();
                            }
                        }
                    } else if (data.message) {
                        const timestamp = new Date().toLocaleTimeString();
                        const message = data.message;

                        const lowerMessage = message.toLowerCase();
                        if (lowerMessage.includes('completed') ||
                            lowerMessage.includes('finished') ||
                            lowerMessage.includes('synchronization completed') ||
                            lowerMessage.includes('failed') ||
                            lowerMessage.includes('error')) {
                            const runBtn = document.getElementById('runLdapSyncBtn');
                            if (runBtn && runBtn.disabled) {
                                if (lowerMessage.includes('failed') || lowerMessage.includes('error')) {
                                    runBtn.disabled = false;
                                    runBtn.innerHTML = _ldapRunBtnLabel();
                                    if (ws) ws.close();
                                } else if (lowerMessage.includes('completed') ||
                                    lowerMessage.includes('finished') ||
                                    lowerMessage.includes('synchronization completed')) {
                                    runBtn.disabled = false;
                                    runBtn.innerHTML = _ldapRunBtnLabel();
                                    if (ws) ws.close();
                                }
                            }
                        }

                        appendToConsole(consoleElement, `[${timestamp}] ${message}`);
                    }

                } catch (e) {
                    console.error('Error parsing WebSocket message:', e);
                }
            };

            ws.onerror = (error) => {
                console.error('WebSocket error:', error);
                appendToConsole(consoleElement, `[ERROR] WebSocket connection error`);
            };

            ws.onclose = (event) => {
                if (event.code !== 1000 && reconnectAttempts < maxReconnectAttempts) {
                    reconnectAttempts++;
                    appendToConsole(consoleElement, `[WARN] WebSocket closed. Attempting to reconnect (${reconnectAttempts}/${maxReconnectAttempts})...`);
                    setTimeout(connect, 2000 * reconnectAttempts);
                } else if (reconnectAttempts >= maxReconnectAttempts) {
                    appendToConsole(consoleElement, `[ERROR] Failed to reconnect WebSocket after ${maxReconnectAttempts} attempts`);
                }
            };

        } catch (error) {
            console.error('Error creating WebSocket:', error);
            appendToConsole(consoleElement, `[ERROR] Failed to create WebSocket connection: ${error.message}`);
        }
    }

    connect();
}

function appendToConsole(consoleElement, message) {
    if (!consoleElement) return;

    const text = consoleElement.textContent || '';
    const newText = text ? `${text}\n${message}` : message;
    consoleElement.textContent = newText;

    consoleElement.scrollTop = consoleElement.scrollHeight;

    if (message && message.includes('[ERROR]')) {
        console.error('LDAP Sync Error:', message);
    }
}

async function loadLastSyncInfo() {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const lastSyncTime = document.getElementById('lastSyncTime');
    const setNoHistory = function () {
        if (lastSyncTime) {
            lastSyncTime.textContent = T('adminPanel.common.noSyncHistoryAvailable', 'No sync history available');
            lastSyncTime.style.color = '#666';
        }
    };
    const setError = function (message) {
        if (lastSyncTime) {
            lastSyncTime.textContent = message || T('adminPanel.ldapSync.errorLoadingSyncHistory', 'Error loading sync history');
            lastSyncTime.style.color = '#dc3545';
        }
    };
    try {
        const response = await fetch('/api/admin/ldap/sync/history/latest', {
            credentials: 'include'
        });

        if (response.ok) {
            const history = await response.json().catch(() => null);
            if (history && history.completed_at) {
                if (lastSyncTime) {
                    const date = new Date(history.completed_at);
                    lastSyncTime.textContent = date.toLocaleString();
                    lastSyncTime.style.color = '#248567';
                }
            } else {
                setNoHistory();
            }
            return;
        }

        if (response.status === 404) {
            setNoHistory();
            return;
        }

        // 403: not SuperAdmin — same as no history for display, or show access message
        if (response.status === 403) {
            const err = await response.json().catch(() => ({}));
            setError(err.error || T('adminPanel.ldapSync.accessDeniedSyncHistory', 'Access denied (SuperAdmin required)'));
            return;
        }

        // 500 or other: log body if JSON, show no history to avoid broken UI
        const errBody = await response.json().catch(() => ({}));
        const msg = errBody.error || ('HTTP ' + response.status);
        console.warn('LDAP sync history request failed:', response.status, msg);
        if (response.status >= 500) {
            setError(T('adminPanel.ldapSync.syncHistoryUnavailable', 'Sync history unavailable (check server logs)'));
        } else {
            setNoHistory();
        }
    } catch (error) {
        console.error('Error loading last sync info:', error);
        setError();
    }
}
