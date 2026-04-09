// System Settings functionality for Customize & Configure

function sysT(key, fallback) {
    return typeof adminT === 'function' ? adminT(key, fallback) : fallback;
}
function escapeHtmlSys(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * When API returns 403 (non–SuperAdmin): show a clear message in the UI
 * without throwing or logging console errors.
 */
function showSystemSettingsAccessDenied() {
    const T = sysT;
    const msg = T('adminPanel.systemSettingsPage.accessDeniedSuperAdminOnly',
        'You do not have access to this section. Only Super Administrators can view and change these settings.');
    const detail = T('adminPanel.systemSettingsPage.accessDeniedHint',
        'If you need changes here, contact your Super Admin.');
    showStatusMessage(msg, 'warning');
    const settingsGridBody = document.getElementById('settingsGridBody');
    if (settingsGridBody) {
        settingsGridBody.innerHTML = `
            <div style="grid-column: 1 / -1; padding: 40px 20px; text-align: center; max-width: 560px; margin: 0 auto;">
                <div style="font-size: 48px; color: #b8860b; margin-bottom: 16px;"><i class="fas fa-lock"></i></div>
                <h3 style="color: #856404; margin-bottom: 12px; font-size: 1.15rem;">${escapeHtmlSys(msg)}</h3>
                <p style="color: #666; line-height: 1.6; font-size: 14px;">${escapeHtmlSys(detail)}</p>
            </div>
        `;
    }
    const editBtn = document.getElementById('editSettingsBtn');
    if (editBtn) editBtn.style.display = 'none';
    const settingsActions = document.getElementById('settingsActions');
    if (settingsActions) settingsActions.style.display = 'none';
}

/**
 * System settings PUT/APIs are SuperAdmin-only. Hide Edit so Admin cannot click.
 * Uses same role check as main.js (window._adminUserRole + checkIsSuperAdmin).
 */
function canEditSystemSettingsGroups() {
    if (typeof checkIsSuperAdmin === 'function') {
        return checkIsSuperAdmin(window._adminUserRole);
    }
    return false;
}

/** Show or hide #editSettingsBtn; always hidden when user is not SuperAdmin */
function showEditSettingsBtn(show) {
    const btn = document.getElementById('editSettingsBtn');
    if (!btn) return;
    if (!canEditSystemSettingsGroups()) {
        btn.style.display = 'none';
        return;
    }
    btn.style.display = show ? 'flex' : 'none';
}

function showSystemSettingsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.systemSettings');
    const T = sysT;
    const h = escapeHtmlSys;

    contentArea.innerHTML = `
        <div class="system-settings-container" style="padding: 20px;">
            <div class="card" style="max-width: 1000px; margin: 0 auto;">
                <div class="card-header" style="background-color: #248567; padding: 15px; border-bottom: 1px solid #ddd; display: flex; justify-content: space-between; align-items: center;">
                    <h5 style="margin: 0; color: white; font-weight: bold;">${h(T('adminPanel.systemSettingsPage.pageTitle', 'SYSTEM SETTINGS'))}</h5>
                    <div id="headerActions" style="display: none; gap: 10px; align-items: center;">
                        <!-- Test Connection button will be added here for EDC -->
                    </div>
                </div>
                <div class="card-body" style="padding: 30px;">
                    <div class="settings-group-selector" style="margin-bottom: 20px; display: flex; align-items: center; gap: 15px;">
                        <label style="font-weight: 500; min-width: 80px;">${h(T('adminPanel.systemSettingsPage.groupLabel', 'Group:'))}</label>
                        <select id="settingsGroup" class="form-control" 
                                style="flex: 1; max-width: 300px; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                            <option value="Environment">${h(T('adminPanel.systemSettingsPage.groupEnvironment', 'Environment'))}</option>
                            <option value="JWT Settings">${h(T('adminPanel.systemSettingsPage.groupJwt', 'JWT Settings'))}</option>
                            <option value="Email Options">${h(T('adminPanel.systemSettingsPage.groupEmail', 'Email Options'))}</option>
                            <option value="DefaultWorkflows">${h(T('adminPanel.systemSettingsPage.groupDefaultWorkflows', 'DefaultWorkflows'))}</option>
                            <option value="DefaultSegment">${h(T('adminPanel.systemSettingsPage.groupDefaultSegment', 'Default Segment'))}</option>
                            <option value="Dashboard">${h(T('adminPanel.systemSettingsPage.groupDashboard', 'Dashboard'))}</option>
                            <option value="Change Requests">${h(T('adminPanel.systemSettingsPage.groupChangeRequests', 'Change Requests'))}</option>
                            <option value="LDAP Settings">${h(T('adminPanel.systemSettingsPage.groupLdap', 'LDAP Settings'))}</option>
                            <option value="Enterprise Data Catalog">${h(T('adminPanel.systemSettingsPage.groupEdc', 'Enterprise Data Catalog'))}</option>
                        </select>
                        <button type="button" id="editSettingsBtn" data-super-admin-only="true"
                                style="padding: 8px 20px; border: none; background: #248567; color: white; border-radius: 4px; cursor: pointer; font-weight: 500; display: none; align-items: center; gap: 8px;">
                            <i class="fas fa-edit"></i> ${h(T('adminPanel.systemSettingsPage.edit', 'Edit'))}
                        </button>
                    </div>

                    <div id="settingsTableContainer" style="margin-top: 20px;">
                        <div id="settingsGridBody" class="settings-grid">
                            <!-- Settings will be rendered here -->
                        </div>
                    </div>

                    <div id="emailSettingsContainer" style="display: none;"></div>

                    <div id="settingsActions" style="display: none; margin-top: 30px; display: flex; gap: 10px; justify-content: flex-end;">
                        <button type="button" id="cancelSettingsBtn" 
                                style="padding: 10px 25px; border: 1px solid #ccc; background: white; color: #333; border-radius: 4px; cursor: pointer; font-weight: 500; font-size: 14px;">
                            ${h(T('adminPanel.systemSettingsPage.cancel', 'Cancel'))}
                        </button>
                        <button type="button" id="saveSettingsBtn" 
                                style="padding: 10px 25px; border: none; background: #248567; color: white; border-radius: 4px; cursor: pointer; font-weight: 500; font-size: 14px;">
                            ${h(T('adminPanel.systemSettingsPage.save', 'Save'))}
                        </button>
                    </div>

                    <div id="statusMessage" style="margin-top: 20px; padding: 10px; border-radius: 4px; display: none;"></div>
                </div>
            </div>
        </div>

        <style>
            .settings-grid {
                display: grid;
                grid-template-columns: repeat(2, 1fr);
                gap: 30px 40px;
                padding: 10px 0;
            }
            .setting-item {
                display: flex;
                flex-direction: column;
                gap: 8px;
            }
            .setting-header {
                display: flex;
                align-items: center;
                gap: 8px;
            }
            .setting-label {
                font-weight: 500;
                color: #333;
                font-size: 14px;
            }
            .help-icon {
                color: #666;
                cursor: help;
                font-size: 14px;
            }
            .help-icon:hover {
                color: #248567;
            }
            .settings-input {
                width: 100%;
                padding: 10px 12px;
                border: 1px solid #ccc;
                border-radius: 4px;
                font-size: 14px;
                background-color: white;
                font-family: 'Consolas', 'Monaco', 'Courier New', monospace !important;
                unicode-bidi: plaintext !important;
                direction: ltr !important;
            }
            .settings-input:focus {
                outline: none;
                border-color: #248567;
                box-shadow: 0 0 0 1px #248567;
            }
            .settings-input:disabled {
                background-color: white;
                cursor: not-allowed;
            }
            .settings-link-btn {
                padding: 10px 20px;
                background: #248567;
                color: white;
                border: none;
                border-radius: 4px;
                cursor: pointer;
                font-weight: 500;
                display: flex;
                align-items: center;
                gap: 8px;
                width: fit-content;
            }
            #editSettingsBtn:hover, #saveSettingsBtn:hover {
                background-color: #1a634d;
            }
            #cancelSettingsBtn:hover {
                background-color: #f8f9fa;
            }
        </style>
    `;

    // Initialize
    initializeSystemSettingsPage();
}

function initializeSystemSettingsPage() {
    loadSystemSettings();
    setupSystemSettingsEventListeners();
}

function setupSystemSettingsEventListeners() {
    const editBtn = document.getElementById('editSettingsBtn');
    const saveBtn = document.getElementById('saveSettingsBtn');
    const cancelBtn = document.getElementById('cancelSettingsBtn');
    const groupSelect = document.getElementById('settingsGroup');

    if (editBtn) {
        editBtn.addEventListener('click', enableEditMode);
    }

    if (saveBtn) {
        saveBtn.addEventListener('click', saveSystemSettings);
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', cancelEditMode);
    }

    if (groupSelect) {
        groupSelect.addEventListener('change', loadSystemSettings);
    }
}

let originalSettings = {};
let isEditMode = false;

async function loadSystemSettings() {
    try {
        const group = document.getElementById('settingsGroup').value;

        // If DefaultWorkflows is selected, show read-only table
        if (group === 'DefaultWorkflows') {
            const emailContainer = document.getElementById('emailSettingsContainer');
            if (emailContainer) {
                emailContainer.style.display = 'none';
            }
            
            const settingsTableContainer = document.getElementById('settingsTableContainer');
            const editSettingsBtn = document.getElementById('editSettingsBtn');
            
            if (settingsTableContainer) settingsTableContainer.style.display = 'block';
            if (editSettingsBtn) showEditSettingsBtn(true);
            
            // Load default workflow directory path
            try {
                const response = await fetch(`/api/system-settings/${encodeURIComponent(group)}`, {
                    credentials: 'include'
                });
                if (!response.ok) {
                    if (response.status === 403) {
                        showSystemSettingsAccessDenied();
                        return;
                    }
                    throw new Error('Failed to load default workflows');
                }
                
                const data = await response.json();
                const workflowsPath = data['Default Workflows'] || '';
                
                // Render as read-only table with single path
                renderDefaultWorkflowsTable(workflowsPath);
                disableEditMode();
            } catch (error) {
                console.error('Error loading default workflows:', error);
                showStatusMessage(error.message || 'Error loading default workflows', 'error');
            }
            return;
        }
        
        // If Dashboard is selected, show table format
        if (group === 'Dashboard') {
            const emailContainer = document.getElementById('emailSettingsContainer');
            if (emailContainer) {
                emailContainer.style.display = 'none';
            }
            
            const settingsTableContainer = document.getElementById('settingsTableContainer');
            const editSettingsBtn = document.getElementById('editSettingsBtn');
            
            if (settingsTableContainer) settingsTableContainer.style.display = 'block';
            if (editSettingsBtn) showEditSettingsBtn(true);
            
            // Load Dashboard settings
            try {
                const response = await fetch(`/api/system-settings/${encodeURIComponent(group)}`, {
                    credentials: 'include'
                });
                if (!response.ok) {
                    if (response.status === 403) {
                        showSystemSettingsAccessDenied();
                        return;
                    }
                    throw new Error('Failed to load dashboard settings');
                }
                
                const settings = await response.json();
                originalSettings = { ...settings };
                
                // Render as table format
                renderDashboardSettingsTable(settings, false);
                disableEditMode();
            } catch (error) {
                console.error('Error loading dashboard settings:', error);
                showStatusMessage(error.message || 'Error loading dashboard settings', 'error');
            }
            return;
        }
        
        // If Change Requests is selected, show table format
        if (group === 'Change Requests') {
            const emailContainer = document.getElementById('emailSettingsContainer');
            if (emailContainer) {
                emailContainer.style.display = 'none';
            }
            
            const settingsTableContainer = document.getElementById('settingsTableContainer');
            const editSettingsBtn = document.getElementById('editSettingsBtn');
            
            if (settingsTableContainer) settingsTableContainer.style.display = 'block';
            if (editSettingsBtn) showEditSettingsBtn(true);
            
            // Load Change Requests settings
            try {
                const response = await fetch(`/api/system-settings/${encodeURIComponent(group)}`, {
                    credentials: 'include'
                });
                if (!response.ok) {
                    if (response.status === 403) {
                        showSystemSettingsAccessDenied();
                        return;
                    }
                    throw new Error('Failed to load change requests settings');
                }
                
                const settings = await response.json();
                originalSettings = { ...settings };
                
                // Render as table format
                renderChangeRequestSettingsTable(settings, false);
                disableEditMode();
            } catch (error) {
                console.error('Error loading change requests settings:', error);
                showStatusMessage(error.message || 'Error loading change requests settings', 'error');
            }
            return;
        }
        
        // If LDAP Settings is selected, show LDAP Settings content
        if (group === 'LDAP Settings') {
            const emailContainer = document.getElementById('emailSettingsContainer');
            if (emailContainer) {
                emailContainer.style.display = 'none';
            }
            
            const settingsTableContainer = document.getElementById('settingsTableContainer');
            const editSettingsBtn = document.getElementById('editSettingsBtn');
            
            if (settingsTableContainer) settingsTableContainer.style.display = 'block';
            if (editSettingsBtn) showEditSettingsBtn(true);
            
            // Load LDAP settings from API
            try {
                const response = await fetch('/api/admin/ldap/settings', {
                    credentials: 'include'
                });
                
                if (!response.ok) {
                    if (response.status === 403) {
                        showSystemSettingsAccessDenied();
                        return;
                    }
                    throw new Error('Failed to load LDAP settings');
                }
                
                const settings = await response.json();
                originalSettings = { ...settings };
                
                // Render LDAP settings
                renderLdapSettingsTable(settings, false);
                disableEditMode();
            } catch (error) {
                console.error('Error loading LDAP settings:', error);
                showStatusMessage(error.message || 'Error loading LDAP settings', 'error');
            }
            return;
        }
        
        // If Enterprise Data Catalog is selected, show EDC Settings content
        if (group === 'Enterprise Data Catalog') {
            const emailContainer = document.getElementById('emailSettingsContainer');
            if (emailContainer) {
                emailContainer.style.display = 'none';
            }
            
            const settingsTableContainer = document.getElementById('settingsTableContainer');
            const editSettingsBtn = document.getElementById('editSettingsBtn');
            
            if (settingsTableContainer) settingsTableContainer.style.display = 'block';
            if (editSettingsBtn) showEditSettingsBtn(true);
            
            // Load EDC settings from API
            try {
                const response = await fetch('/api/admin/settings/edc', {
                    credentials: 'include'
                });
                
                if (!response.ok) {
                    if (response.status === 403) {
                        showSystemSettingsAccessDenied();
                        return;
                    }
                    throw new Error('Failed to load EDC settings');
                }
                
                const settings = await response.json();
                originalSettings = { ...settings };
                
                // Render EDC settings (table view)
                renderEdcSettingsTable(settings, false);
                disableEditMode();
            } catch (error) {
                console.error('Error loading EDC settings:', error);
                showStatusMessage(error.message || 'Error loading EDC settings', 'error');
            }
            return;
        }
        
        // If Email Options is selected, show Email Settings content
        if (group === 'Email Options') {
            if (typeof showEmailSettingsContent === 'function') {
                // Hide system settings elements and show email settings
                const settingsTableContainer = document.getElementById('settingsTableContainer');
                const editSettingsBtn = document.getElementById('editSettingsBtn');
                const settingsActions = document.getElementById('settingsActions');
                const statusMessage = document.getElementById('statusMessage');

                if (settingsTableContainer) settingsTableContainer.style.display = 'none';
                if (editSettingsBtn) showEditSettingsBtn(false);
                if (settingsActions) settingsActions.style.display = 'none';
                if (statusMessage) statusMessage.style.display = 'none';

                // Get or create container for email settings
                let emailContainer = document.getElementById('emailSettingsContainer');
                if (!emailContainer) {
                    emailContainer = document.createElement('div');
                    emailContainer.id = 'emailSettingsContainer';
                    const cardBody = document.querySelector('.card-body');
                    if (cardBody) {
                        cardBody.appendChild(emailContainer);
                    }
                }
                emailContainer.style.display = 'block';

                // Show email settings content inside the container
                showEmailSettingsContent(emailContainer);
                return;
            } else {
                throw new Error('Email Settings function not available');
            }
        }

        // For Environment and other groups, show the settings table
        const emailContainer = document.getElementById('emailSettingsContainer');
        if (emailContainer) {
            emailContainer.style.display = 'none';
        }

        const settingsTableContainer = document.getElementById('settingsTableContainer');
        const editSettingsBtn = document.getElementById('editSettingsBtn');

        if (settingsTableContainer) settingsTableContainer.style.display = 'block';
        if (editSettingsBtn) showEditSettingsBtn(true);

        // Load settings from system_settings table
        const response = await fetch(`/api/system-settings/${encodeURIComponent(group)}`, {
            credentials: 'include'
        });

        if (!response.ok) {
            if (response.status === 403) {
                showSystemSettingsAccessDenied();
                return;
            }
            throw new Error('Failed to load system settings');
        }

        const settings = await response.json();

        // If Environment group, also load DATA_MIGRATION_ENABLED from app_config
        if (group === 'Environment') {
            try {
                const migrationResponse = await fetch('/api/admin/environment/data-migration', {
                    credentials: 'include'
                });

                if (migrationResponse.ok) {
                    const migrationData = await migrationResponse.json();
                    settings.DATA_MIGRATION_ENABLED = migrationData.enabled;
                } else if (migrationResponse.status === 403) {
                    // User doesn't have access, don't show this setting
                    // Still show the setting but with default value
                    settings.DATA_MIGRATION_ENABLED = false;
                } else {
                    // Default to false if can't load, but still show it
                    settings.DATA_MIGRATION_ENABLED = false;
                }
            } catch (error) {
                console.warn('[SystemSettings] Error loading Data Migration configuration:', error);
                // Default to false but still show the setting
                settings.DATA_MIGRATION_ENABLED = false;
            }

        }

        originalSettings = { ...settings };
        renderSettingsTable(settings, false);
        disableEditMode();

    } catch (error) {
        const msg = (error && error.message) ? String(error.message) : '';
        const isAccessDenied = msg.includes('Access denied') || msg.includes('SuperAdmin') || msg.includes('Forbidden');
        if (isAccessDenied) {
            showSystemSettingsAccessDenied();
            return;
        }
        console.error('Error loading system settings:', error);
        showStatusMessage(msg || sysT('adminPanel.systemSettingsPage.errorLoading', 'Error loading system settings'), 'error');
        const settingsGridBody = document.getElementById('settingsGridBody');
        if (settingsGridBody) {
            const displayMsg = escapeHtmlSys(msg || sysT('adminPanel.systemSettingsPage.errorLoadingSettings', 'Error loading settings'));
            settingsGridBody.innerHTML = `
                <div style="grid-column: 1 / -1; padding: 20px; text-align: center; color: #d32f2f;">
                    ${displayMsg}
                </div>
            `;
        }
    }
}

function renderDefaultWorkflowsTable(workflowsPath) {
    const gridBody = document.getElementById('settingsGridBody');
    const T = sysT;
    const h = escapeHtmlSys;
    
    if (!workflowsPath || workflowsPath.trim() === '') {
        gridBody.innerHTML = `
            <div style="grid-column: 1 / -1; padding: 20px; text-align: center; color: #666;">
                ${h(T('adminPanel.systemSettingsPage.noWorkflowPath', 'No default workflow directory path found'))}
            </div>
        `;
        return;
    }
    
    const colDisplay = h(T('adminPanel.systemSettingsPage.colDisplayName', 'Display Name'));
    const colCustom = h(T('adminPanel.systemSettingsPage.colCustomizable', 'Customizable'));
    const colType = h(T('adminPanel.systemSettingsPage.colValueType', 'Value Type'));
    const colDef = h(T('adminPanel.systemSettingsPage.colDefaultValue', 'Default Value'));
    const colVal = h(T('adminPanel.systemSettingsPage.colValue', 'Value'));
    const rowTitle = h(T('adminPanel.systemSettingsPage.defaultWorkflowsRowTitle', 'Default Workflows'));
    const rowHelp = h(T('adminPanel.systemSettingsPage.defaultWorkflowsHelp', 'All the default workflow files...'));

    gridBody.innerHTML = `
        <div style="grid-column: 1 / -1;">
            <table style="width: 100%; border-collapse: collapse; margin-top: 10px;">
                <thead>
                    <tr style="background-color: #f5f5f5; border-bottom: 2px solid #ddd;">
                        <th style="padding: 12px; text-align: left; font-weight: 600;">${colDisplay}</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">${colCustom}</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">${colType}</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">${colDef}</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">${colVal}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr style="border-bottom: 1px solid #eee;">
                        <td style="padding: 12px;">
                            <span style="font-weight: 500;">${rowTitle}</span>
                            <i class="fas fa-question-circle help-icon" 
                               title="${rowHelp}" 
                               style="margin-left: 5px; color: #666; cursor: help;"></i>
                        </td>
                        <td style="padding: 12px;">
                            <i class="fas fa-times" style="color: #d32f2f;"></i>
                        </td>
                        <td style="padding: 12px;">
                            <span class="data-type-badge" style="display: inline-block; padding: 2px 8px; background-color: #e9ecef; border-radius: 3px; font-size: 12px; color: #495057; font-weight: 500;">json</span>
                        </td>
                        <td style="padding: 12px; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 13px;">[]</td>
                        <td style="padding: 12px; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 13px; direction: ltr; text-align: left; word-break: break-all;">
                            ${workflowsPath}
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    `;
}

function showDefaultWorkflowsRestartModal() {
    // Remove existing modal if any
    const existingModal = document.getElementById('defaultWorkflowsRestartModal');
    if (existingModal) {
        existingModal.remove();
    }
    
    // Create modal overlay
    const modalOverlay = document.createElement('div');
    modalOverlay.id = 'defaultWorkflowsRestartModal';
    modalOverlay.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background-color: rgba(0, 0, 0, 0.5);
        display: flex;
        justify-content: center;
        align-items: center;
        z-index: 10000;
    `;
    
    // Create modal content
    const modalContent = document.createElement('div');
    modalContent.style.cssText = `
        background: white;
        border-radius: 8px;
        padding: 30px;
        max-width: 500px;
        width: 90%;
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
    `;
    
    const T = sysT;
    const h = escapeHtmlSys;
    modalContent.innerHTML = `
        <div style="margin-bottom: 20px;">
            <h3 style="margin: 0 0 15px 0; color: #333; font-size: 20px; font-weight: 600;">
                ${h(T('adminPanel.systemSettingsPage.restartModalTitle', 'Restart Default Workflows'))}
            </h3>
            <p style="margin: 0; color: #666; line-height: 1.6;">
                ${h(T('adminPanel.systemSettingsPage.restartModalBody', 'This will reset...'))}
                <br><br>
                <strong>${h(T('adminPanel.systemSettingsPage.restartModalNote', 'Note:'))}</strong> ${h(T('adminPanel.systemSettingsPage.restartModalActionWill', 'This action will:'))}
                <ul style="margin: 10px 0 0 20px; padding: 0;">
                    <li>${h(T('adminPanel.systemSettingsPage.restartModalBullet1', 'Check each facet...'))}</li>
                    <li>${h(T('adminPanel.systemSettingsPage.restartModalBullet2', 'Reset existing workflows...'))}</li>
                    <li>${h(T('adminPanel.systemSettingsPage.restartModalBullet3', 'Create new default workflows...'))}</li>
                </ul>
            </p>
        </div>
        <div id="restartStatusMessage" style="margin: 15px 0; padding: 10px; border-radius: 4px; display: none;"></div>
        <div style="display: flex; gap: 10px; justify-content: flex-end; margin-top: 25px;">
            <button type="button" id="cancelRestartBtn" 
                    style="padding: 10px 20px; border: 1px solid #ccc; background: white; color: #333; border-radius: 4px; cursor: pointer; font-weight: 500;">
                ${h(T('adminPanel.systemSettingsPage.cancel', 'Cancel'))}
            </button>
            <button type="button" id="confirmRestartBtn" 
                    style="padding: 10px 20px; border: none; background: #248567; color: white; border-radius: 4px; cursor: pointer; font-weight: 500; display: flex; align-items: center; gap: 8px;">
                <i class="fas fa-redo"></i> ${h(T('adminPanel.systemSettingsPage.restart', 'Restart'))}
            </button>
        </div>
    `;
    
    modalOverlay.appendChild(modalContent);
    document.body.appendChild(modalOverlay);
    
    // Event listeners
    const cancelBtn = document.getElementById('cancelRestartBtn');
    const confirmBtn = document.getElementById('confirmRestartBtn');
    const statusMessage = document.getElementById('restartStatusMessage');
    
    cancelBtn.addEventListener('click', () => {
        modalOverlay.remove();
        isEditMode = false;
    });
    
    // Close on overlay click
    modalOverlay.addEventListener('click', (e) => {
        if (e.target === modalOverlay) {
            modalOverlay.remove();
            isEditMode = false;
        }
    });
    
    confirmBtn.addEventListener('click', async () => {
        await restartDefaultWorkflows(confirmBtn, statusMessage, modalOverlay);
    });
}

async function restartDefaultWorkflows(button, statusMessage, modalOverlay) {
    // Disable button and show loading
    button.disabled = true;
    button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + escapeHtmlSys(sysT('adminPanel.common.restarting', 'Restarting...'));
    statusMessage.style.display = 'block';
    statusMessage.style.background = '#e3f2fd';
    statusMessage.style.color = '#1976d2';
    statusMessage.style.border = '1px solid #90caf9';
    statusMessage.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + escapeHtmlSys(sysT('adminPanel.systemSettingsPage.restartingDefaultWorkflows', 'Restarting default workflows...'));
    
    try {
        const response = await fetch('/admin/api/default-workflows/restart', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        const data = await response.json();
        
        if (!response.ok || data.error) {
            throw new Error(data.error || `HTTP error! status: ${response.status}`);
        }
        
        // Show success message
        statusMessage.style.background = '#e8f5e9';
        statusMessage.style.color = '#2e7d32';
        statusMessage.style.border = '1px solid #81c784';
        const successLabel = escapeHtmlSys(sysT('adminPanel.systemSettingsPage.success', 'Success!'));
        const restartedMsg = escapeHtmlSys(sysT('adminPanel.systemSettingsPage.restartSuccess', 'Default workflows restarted successfully.'));
        const createdLabel = escapeHtmlSys(sysT('adminPanel.systemSettingsPage.created', 'Created:'));
        const resetLabel = escapeHtmlSys(sysT('adminPanel.systemSettingsPage.reset', 'Reset:'));
        const errorsLabel = escapeHtmlSys(sysT('adminPanel.systemSettingsPage.errors', 'Errors:'));
        statusMessage.innerHTML = `
            <i class="fas fa-check-circle"></i> 
            <strong>${successLabel}</strong> ${restartedMsg}
            <br>
            ${createdLabel} ${data.created || 0}, ${resetLabel} ${data.reset || 0}
            ${data.errors && data.errors.length > 0 ? `<br><small style="color: #d32f2f;">${errorsLabel} ${data.errors.length}</small>` : ''}
        `;
        
        // Re-enable button
        button.disabled = false;
        button.innerHTML = '<i class="fas fa-check"></i> ' + escapeHtmlSys(sysT('adminPanel.common.done', 'Done'));
        
        // Close modal after 3 seconds
        setTimeout(() => {
            modalOverlay.remove();
            isEditMode = false;
            // Reload settings to show updated data
            loadSystemSettings();
        }, 3000);
        
    } catch (error) {
        console.error('Error restarting default workflows:', error);
        
        // Show error message
        statusMessage.style.background = '#ffebee';
        statusMessage.style.color = '#c62828';
        statusMessage.style.border = '1px solid #ef5350';
        statusMessage.innerHTML = `
            <i class="fas fa-exclamation-circle"></i> 
            <strong>Error:</strong> ${error.message || 'Failed to restart default workflows'}
        `;
        
        // Re-enable button
        button.disabled = false;
        button.innerHTML = '<i class="fas fa-redo"></i> Retry';
    }
}

function renderDashboardSettingsTable(settings, editable) {
    const gridBody = document.getElementById('settingsGridBody');
    
    if (!settings || Object.keys(settings).length === 0) {
        gridBody.innerHTML = `
            <div style="grid-column: 1 / -1; padding: 20px; text-align: center; color: #666;">
                ${escapeHtmlSys(sysT('adminPanel.systemSettingsPage.noSettingsDashboard', 'No settings found for Dashboard group'))}
            </div>
        `;
        return;
    }
    
    // Define setting metadata
    const settingMetadata = {
        'enable_my_dashboard': {
            name: 'Enable My Dashboard',
            helpText: 'Enable to show the secondary navigation bar (Home / My Dashboard) on the home page. When disabled, the bar will be hidden.',
            dataType: 'boolean',
            defaultValue: 'false',
            customizable: true
        }
    };
    
    let tableRows = '';
    for (const [key, value] of Object.entries(settings)) {
        const metadata = settingMetadata[key] || { 
            name: key.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()), 
            helpText: '', 
            dataType: typeof value === 'boolean' ? 'boolean' : 'string',
            defaultValue: 'false',
            customizable: true
        };
        
        const displayName = metadata.name;
        const dataType = metadata.dataType;
        const defaultValue = metadata.defaultValue || 'false';
        const isCustomizable = metadata.customizable !== false;
        
        // Convert value to string representation
        let valueDisplay = '';
        if (dataType === 'boolean') {
            const boolValue = value === true || value === 'true';
            valueDisplay = boolValue ? 'true' : 'false';
        } else {
            valueDisplay = String(value);
        }
        
        tableRows += `
            <tr style="border-bottom: 1px solid #eee;">
                <td style="padding: 12px;">
                    <span style="font-weight: 500;">${displayName}</span>
                    ${metadata.helpText ? `
                        <i class="fas fa-question-circle help-icon" 
                           title="${metadata.helpText}" 
                           style="margin-left: 5px; color: #666; cursor: help;"></i>
                    ` : ''}
                </td>
                <td style="padding: 12px;">
                    ${isCustomizable ? `
                        <i class="fas fa-check" style="color: #28a745;"></i>
                    ` : `
                        <i class="fas fa-times" style="color: #d32f2f;"></i>
                    `}
                </td>
                <td style="padding: 12px;">
                    <span class="data-type-badge" style="display: inline-block; padding: 2px 8px; background-color: #e9ecef; border-radius: 3px; font-size: 12px; color: #495057; font-weight: 500;">${dataType}</span>
                </td>
                <td style="padding: 12px; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 13px;">${defaultValue}</td>
                <td style="padding: 12px; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 13px;">
                    ${editable && dataType === 'boolean' ? `
                        <input type="checkbox" class="settings-input" id="setting_${key}" 
                               ${value === true || value === 'true' ? 'checked' : ''}
                               data-key="${key}" data-type="${dataType}"
                               style="width: auto; height: 20px;">
                    ` : valueDisplay}
                </td>
            </tr>
        `;
    }
    
    gridBody.innerHTML = `
        <div style="grid-column: 1 / -1;">
            <table style="width: 100%; border-collapse: collapse; margin-top: 10px;">
                <thead>
                    <tr style="background-color: #f5f5f5; border-bottom: 2px solid #ddd;">
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Display Name</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Customizable</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Value Type</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Default Value</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Value</th>
                    </tr>
                </thead>
                <tbody>
                    ${tableRows}
                </tbody>
            </table>
        </div>
    `;
}

function renderChangeRequestSettingsTable(settings, editable) {
    const gridBody = document.getElementById('settingsGridBody');
    
    if (!settings || Object.keys(settings).length === 0) {
        gridBody.innerHTML = `
            <div style="grid-column: 1 / -1; padding: 20px; text-align: center; color: #666;">
                No settings found for Change Requests group
            </div>
        `;
        return;
    }
    
    // Define setting metadata
    const settingMetadata = {
        'auto_complete_change_requests': {
            name: 'Auto-complete change requests',
            helpText: 'If true, automatically complete change request when workflow is complete.',
            dataType: 'boolean',
            defaultValue: 'false',
            customizable: true
        },
        'enable_automatic_deletion': {
            name: 'Enable automatic deletion of change requests',
            helpText: 'If true, delete change requests when the object is deleted.',
            dataType: 'boolean',
            defaultValue: 'false',
            customizable: true
        },
        'default_change_request_systems': {
            name: 'Default Change Request Systems',
            helpText: 'The provider option that will be used while creating manual change request.',
            dataType: 'dropdown',
            defaultValue: 'None',
            customizable: true,
            options: ['None', 'Native']
        },
        'days_to_automatically_delete': {
            name: 'Days to automatically delete change requests',
            helpText: 'Specify the number of days to delete associated change requests when an object is deleted. Enter 0 to delete immediately, or enter a positive integer to denote the days.',
            dataType: 'int',
            defaultValue: '0',
            customizable: true
        }
    };
    
    let tableRows = '';
    for (const [key, value] of Object.entries(settings)) {
        const metadata = settingMetadata[key] || { 
            name: key.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()), 
            helpText: '', 
            dataType: typeof value === 'boolean' ? 'boolean' : 'string',
            defaultValue: 'false',
            customizable: true
        };
        
        const displayName = metadata.name;
        const dataType = metadata.dataType;
        const defaultValue = metadata.defaultValue || 'false';
        const isCustomizable = metadata.customizable !== false;
        
        // Convert value to string representation
        let valueDisplay = '';
        if (dataType === 'boolean') {
            const boolValue = value === true || value === 'true';
            valueDisplay = boolValue ? 'true' : 'false';
        } else {
            valueDisplay = String(value || defaultValue);
        }
        
        // Check if "Enable automatic deletion" is enabled for conditional disabling
        const enableAutoDeletion = settings.enable_automatic_deletion === true || settings.enable_automatic_deletion === 'true';
        const isDaysField = key === 'days_to_automatically_delete';
        
        tableRows += `
            <tr style="border-bottom: 1px solid #eee;">
                <td style="padding: 12px;">
                    <span style="font-weight: 500;">${displayName}</span>
                    ${metadata.helpText ? `
                        <i class="fas fa-question-circle help-icon" 
                           title="${metadata.helpText}" 
                           style="margin-left: 5px; color: #666; cursor: help;"></i>
                    ` : ''}
                </td>
                <td style="padding: 12px;">
                    ${isCustomizable ? `
                        <i class="fas fa-check" style="color: #28a745;"></i>
                    ` : `
                        <i class="fas fa-times" style="color: #d32f2f;"></i>
                    `}
                </td>
                <td style="padding: 12px;">
                    <span class="data-type-badge" style="display: inline-block; padding: 2px 8px; background-color: #e9ecef; border-radius: 3px; font-size: 12px; color: #495057; font-weight: 500;">${dataType}</span>
                </td>
                <td style="padding: 12px; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 13px;">${defaultValue}</td>
                <td style="padding: 12px; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 13px;">
                    ${editable && dataType === 'boolean' ? `
                        <input type="checkbox" class="settings-input" id="setting_${key}" 
                               ${value === true || value === 'true' ? 'checked' : ''}
                               data-key="${key}" data-type="${dataType}"
                               style="width: auto; height: 20px;">
                    ` : editable && dataType === 'dropdown' ? `
                        <select class="settings-input" id="setting_${key}" 
                                data-key="${key}" data-type="${dataType}"
                                style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                            ${metadata.options.map(opt => `
                                <option value="${opt}" ${valueDisplay === opt ? 'selected' : ''}>${opt}</option>
                            `).join('')}
                        </select>
                    ` : editable && dataType === 'int' ? `
                        <input type="number" class="settings-input" id="setting_${key}" 
                               value="${valueDisplay}" 
                               data-key="${key}" data-type="${dataType}"
                               min="0" step="1"
                               ${isDaysField && !enableAutoDeletion ? 'disabled' : ''}
                               style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px; ${isDaysField && !enableAutoDeletion ? 'background-color: #f5f5f5; cursor: not-allowed;' : ''}">
                    ` : valueDisplay}
                </td>
            </tr>
        `;
    }
    
    gridBody.innerHTML = `
        <div style="grid-column: 1 / -1;">
            <table style="width: 100%; border-collapse: collapse; margin-top: 10px;">
                <thead>
                    <tr style="background-color: #f5f5f5; border-bottom: 2px solid #ddd;">
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Display Name</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Customizable</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Value Type</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Default Value</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Value</th>
                    </tr>
                </thead>
                <tbody>
                    ${tableRows}
                </tbody>
            </table>
        </div>
    `;
    
    // Add event listener for enable_automatic_deletion checkbox to toggle days field
    if (editable) {
        const enableAutoDeletionCheckbox = document.getElementById('setting_enable_automatic_deletion');
        const daysField = document.getElementById('setting_days_to_automatically_delete');
        
        if (enableAutoDeletionCheckbox) {
            enableAutoDeletionCheckbox.addEventListener('change', function() {
                if (daysField) {
                    daysField.disabled = !this.checked;
                    daysField.style.backgroundColor = this.checked ? 'white' : '#f5f5f5';
                    daysField.style.cursor = this.checked ? 'text' : 'not-allowed';
                }
            });
        }
    }
}

function renderSettingsTable(settings, editable) {
    const gridBody = document.getElementById('settingsGridBody');

    if (!settings || Object.keys(settings).length === 0) {
        gridBody.innerHTML = `
            <div style="grid-column: 1 / -1; padding: 20px; text-align: center; color: #666;">
                No settings found for this group
            </div>
        `;
        return;
    }

    // Define setting metadata
    const settingMetadata = {
        'clear_notifications_days': {
            name: 'Clear Notifications',
            helpText: 'Enter the frequency in number of days. A scheduled job runs every day at 12:00 AM server time and periodically clears notifications older than the configured days. Use 0 to disable.',
            dataType: 'int'
        },
        'application_base_url': {
            name: 'Application Base URL',
            helpText: 'The base URL of the application, used in email notifications. Example: http://hostname:port',
            dataType: 'string'
        },
        'DATA_MIGRATION_ENABLED': {
            name: 'Enable Data Migration',
            helpText: 'Toggle table snapshot export/import (search Bulk Migrate ENV and admin Bulk Import ENV).',
            dataType: 'boolean'
        },
        'jwt_validity_seconds': {
            name: 'JWT Validity',
            helpText: 'Timeout period of the access token in seconds (e.g., 86400 for 1 day).',
            dataType: 'int'
        },
        'refresh_validity_seconds': {
            name: 'Refresh Token Validity',
            helpText: 'The timeout period of the refresh token in seconds (e.g., 2592000 for 30 days).',
            dataType: 'int'
        },
        'enterprise_segment_default': {
            name: 'Enterprise Segment',
            helpText: 'Enable to set Enterprise Segment as the default segment while viewing BUDG content.',
            dataType: 'boolean'
        },
        'assigned_segments_default': {
            name: 'Assigned Segments',
            helpText: 'Enable to set Assigned Segments as the default segment while viewing BUDG content.',
            dataType: 'boolean'
        },
        'enable_my_dashboard': {
            name: 'Enable My Dashboard',
            helpText: 'Enable to show the secondary navigation bar (Home / My Dashboard) on the home page. When disabled, the bar will be hidden.',
            dataType: 'boolean'
        }
    };

    let html = '';
    for (const [key, value] of Object.entries(settings)) {
        const metadata = settingMetadata[key] || { name: key, helpText: '', dataType: typeof value };
        const displayName = metadata.name || key.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
        const dataType = metadata.dataType || (typeof value === 'number' ? 'int' : 'string');

        // Force English numerals in the value
        const sanitizedValue = String(value).replace(/[٠-٩]/g, d => '٠١٢٣٤٥٦٧٨٩'.indexOf(d));

        html += `
            <div class="setting-item">
                <div class="setting-header">
                    <span class="setting-label">${displayName}</span>
                    ${metadata.helpText ? `
                        <i class="fas fa-question-circle help-icon" title="${metadata.helpText}"></i>
                    ` : ''}
                </div>
                <div class="setting-value">
                    ${dataType === 'boolean' ? `
                        <input type="checkbox" class="settings-input" id="setting_${key}" 
                               ${value === true || value === 'true' ? 'checked' : ''}
                               data-key="${key}" data-type="${dataType}"
                               ${!editable ? 'disabled' : ''} style="width: auto; height: 20px; margin-top: 5px;">
                    ` : dataType === 'link' ? `
                        <button type="button" class="settings-link-btn" id="setting_${key}">
                            <i class="fas fa-external-link-alt"></i> Open
                        </button>
                    ` : `
                        <input type="text" 
                               class="settings-input" id="setting_${key}" 
                               value="${sanitizedValue}" data-key="${key}" data-type="${dataType}"
                               ${!editable ? 'disabled' : ''}
                               lang="en" dir="ltr"
                               style="font-family: 'Consolas', 'Monaco', 'Courier New', monospace !important;"
                               oninput="this.value = this.value.replace(/[٠-٩]/g, d => '٠١٢٣٤٥٦٧٨٩'.indexOf(d))">
                    `}
                </div>
            </div>
        `;
    }

    gridBody.innerHTML = html;
}

function renderLdapSettingsTable(settings, editable) {
    const gridBody = document.getElementById('settingsGridBody');
    
    if (!settings) {
        settings = {
            ldapEnabled: false,
            ldapUrl: '',
            baseDn: '',
            bindDn: '',
            bindPassword: null,
            userSearchBase: '',
            userSearchFilter: '(uid={0})',
            groupSearchBase: '',
            connectionTimeout: 5000
        };
    }
    
    const isLdapEnabled = settings.ldapEnabled === true || settings.ldapEnabled === 'true';
    
    // LDAP Settings metadata
    const ldapMetadata = {
        ldapEnabled: {
            name: 'Enable LDAP',
            helpText: 'Enable or disable LDAP authentication. When disabled, the system will use local database authentication.',
            dataType: 'boolean',
            section: 'general'
        },
        ldapUrl: {
            name: 'LDAP URL',
            helpText: 'LDAP server URL. Format: ldap://host:port or ldaps://host:port (e.g., ldap://localhost:389 or ldaps://ldap.example.com:636)',
            dataType: 'string',
            required: true,
            section: 'connection'
        },
        baseDn: {
            name: 'Base DN',
            helpText: 'Base distinguished name for LDAP searches (e.g., dc=example,dc=com)',
            dataType: 'string',
            required: true,
            section: 'connection'
        },
        bindDn: {
            name: 'Bind DN',
            helpText: 'Distinguished name for LDAP bind operation (e.g., cn=admin,dc=example,dc=com)',
            dataType: 'string',
            required: true,
            section: 'connection'
        },
        bindPassword: {
            name: 'Bind Password',
            helpText: 'Password for LDAP bind operation. Leave blank to keep current password.',
            dataType: 'password',
            required: true,
            section: 'connection'
        },
        userSearchBase: {
            name: 'User Search Base',
            helpText: 'Base DN for user searches (e.g., ou=people,dc=example,dc=com)',
            dataType: 'string',
            required: true,
            section: 'search'
        },
        userSearchFilter: {
            name: 'User Search Filter',
            helpText: 'LDAP filter pattern for user search. Use {0} as placeholder for username (e.g., (uid={0}))',
            dataType: 'string',
            required: true,
            section: 'search'
        },
        groupSearchBase: {
            name: 'Group Search Base',
            helpText: 'Base DN for group searches (optional, e.g., ou=groups,dc=example,dc=com)',
            dataType: 'string',
            required: false,
            section: 'search'
        },
        connectionTimeout: {
            name: 'Connection Timeout (ms)',
            helpText: 'Connection timeout in milliseconds (default: 5000)',
            dataType: 'int',
            required: false,
            section: 'connection'
        }
    };
    
    let html = '';
    const T = sysT;
    const h = escapeHtmlSys;
    const connTestTitle = h(T('adminPanel.systemSettingsPage.connectionTest', 'Connection Test'));
    const connTestHint = h(T('adminPanel.systemSettingsPage.connectionTestLdapHint', 'Test LDAP connection...'));
    const testConnLabel = h(T('adminPanel.systemSettingsPage.testConnection', 'Test Connection'));
    
    // Add Test Connection button at the top (always visible)
    html += `
        <div style="grid-column: 1 / -1; margin-bottom: 30px; padding: 15px; background-color: #f8f9fa; border-radius: 6px; display: flex; justify-content: space-between; align-items: center;">
            <div>
                <h6 style="margin: 0 0 5px 0; color: #333; font-weight: 600;">${connTestTitle}</h6>
                <p style="margin: 0; color: #666; font-size: 13px;">${connTestHint}</p>
            </div>
            <button type="button" id="testLdapConnectionBtn" 
                    style="padding: 10px 20px; border: none; background: #17a2b8; color: white; border-radius: 4px; cursor: pointer; font-weight: 500; display: flex; align-items: center; gap: 8px; transition: background-color 0.2s;"
                    onmouseover="this.style.backgroundColor='#138496'" 
                    onmouseout="this.style.backgroundColor='#17a2b8'">
                <i class="fas fa-plug"></i> ${testConnLabel}
            </button>
        </div>
    `;
    
    // Render settings grouped by section
    const sections = {
        general: { title: T('adminPanel.systemSettingsPage.generalSettings', 'General Settings'), fields: [] },
        connection: { title: T('adminPanel.systemSettingsPage.connectionSettings', 'Connection Settings'), fields: [] },
        search: { title: T('adminPanel.systemSettingsPage.searchSettings', 'Search Settings'), fields: [] }
    };
    
    // Group fields by section
    for (const [key, metadata] of Object.entries(ldapMetadata)) {
        const section = metadata.section || 'general';
        if (!sections[section]) sections[section] = { title: T('adminPanel.systemSettingsPage.otherSettings', 'Other Settings'), fields: [] };
        sections[section].fields.push({ key, metadata });
    }
    
    // Render each section
    for (const [sectionKey, section] of Object.entries(sections)) {
        if (section.fields.length === 0) continue;
        
        // Section header
        html += `
            <div style="grid-column: 1 / -1; margin-top: ${sectionKey !== 'general' ? '30px' : '0'}; margin-bottom: 15px;">
                <h6 style="margin: 0; padding-bottom: 8px; border-bottom: 2px solid #248567; color: #248567; font-weight: 600; font-size: 15px;">
                    ${h(section.title)}
                </h6>
            </div>
        `;
        
        // Render fields in this section
        for (const { key, metadata } of section.fields) {
            const value = settings[key];
            const displayName = metadata.name;
            const dataType = metadata.dataType;
            const isRequired = metadata.required === true;
            const shouldShow = sectionKey === 'general' || isLdapEnabled || editable;
            // In edit mode, always allow editing (opacity 1, pointer-events auto)
            // In view mode, dim fields when ldapEnabled is false
            const opacity = (sectionKey !== 'general' && !isLdapEnabled && !editable) ? '0.6' : '1';
            const pointerEvents = (sectionKey !== 'general' && !isLdapEnabled && !editable) ? 'none' : 'auto';
            
            html += `
                <div class="setting-item" style="opacity: ${opacity}; pointer-events: ${pointerEvents};" data-ldap-field="${key}">
                    <div class="setting-header">
                        <span class="setting-label">
                            ${displayName}
                            ${isRequired ? ' <span style="color: #d32f2f; font-weight: 600;">*</span>' : ''}
                        </span>
                        ${metadata.helpText ? `
                            <i class="fas fa-question-circle help-icon" 
                               title="${metadata.helpText}" 
                               style="margin-left: 5px;"></i>
                        ` : ''}
                    </div>
                    <div class="setting-value">
                        ${dataType === 'boolean' ? `
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <label style="display: flex; align-items: center; gap: 8px; cursor: ${editable ? 'pointer' : 'default'}; margin: 0;">
                                    <input type="checkbox" 
                                           class="settings-input" 
                                           id="ldap_setting_${key}" 
                                           ${value === true || value === 'true' ? 'checked' : ''}
                                           data-key="${key}" 
                                           data-type="${dataType}"
                                           ${!editable ? 'disabled' : ''} 
                                           style="width: 20px; height: 20px; margin: 0; cursor: ${editable ? 'pointer' : 'not-allowed'};">
                                    <span style="color: ${value === true || value === 'true' ? '#28a745' : '#666'}; font-weight: 500;">
                                        ${value === true || value === 'true' ? h(T('adminPanel.systemSettingsPage.enabled', 'Enabled')) : h(T('adminPanel.systemSettingsPage.disabled', 'Disabled'))}
                                    </span>
                                </label>
                            </div>
                        ` : dataType === 'password' ? `
                            ${!editable ? `
                                <div style="padding: 10px 12px; border: 1px solid #ccc; border-radius: 4px; background-color: #f8f9fa; color: #666; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 14px;">
                                    ${value && value !== '********' && value !== null ? '********' : (value === null ? '<span style="color: #999; font-style: italic;">' + h(T('adminPanel.systemSettingsPage.notSet', 'Not set')) + '</span>' : '********')}
                                </div>
                            ` : `
                                <input type="password" 
                                       class="settings-input" 
                                       id="ldap_setting_${key}" 
                                       value="" 
                                       placeholder="${value && value !== '********' && value !== null ? h(T('adminPanel.systemSettingsPage.placeholderKeepPassword', 'Leave blank...')) : h(T('adminPanel.systemSettingsPage.placeholderEnterPassword', 'Enter password'))}"
                                       data-key="${key}" 
                                       data-type="${dataType}"
                                       ${!editable ? 'disabled' : ''}
                                       lang="en" 
                                       dir="ltr"
                                       style="font-family: 'Consolas', 'Monaco', 'Courier New', monospace !important;">
                            `}
                        ` : dataType === 'int' ? `
                            ${!editable ? `
                                <div style="padding: 10px 12px; border: 1px solid #ccc; border-radius: 4px; background-color: #f8f9fa; color: #333; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 14px;">
                                    ${value || '5000'} ms
                                </div>
                            ` : `
                                <input type="number" 
                                       class="settings-input" 
                                       id="ldap_setting_${key}" 
                                       value="${value || '5000'}" 
                                       data-key="${key}" 
                                       data-type="${dataType}"
                                       ${!editable ? 'disabled' : ''}
                                       min="1000" 
                                       step="1000"
                                       lang="en" 
                                       dir="ltr"
                                       style="font-family: 'Consolas', 'Monaco', 'Courier New', monospace !important;">
                            `}
                        ` : `
                            ${!editable ? `
                                <div style="padding: 10px 12px; border: 1px solid #ccc; border-radius: 4px; background-color: #f8f9fa; color: #333; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 14px; word-break: break-all; min-height: 20px;">
                                    ${value || '<span style="color: #999; font-style: italic;">Not set</span>'}
                                </div>
                            ` : `
                                <input type="text" 
                                       class="settings-input" 
                                       id="ldap_setting_${key}" 
                                       value="${value || ''}" 
                                       data-key="${key}" 
                                       data-type="${dataType}"
                                       ${!editable ? 'disabled' : ''}
                                       lang="en" 
                                       dir="ltr"
                                       style="font-family: 'Consolas', 'Monaco', 'Courier New', monospace !important;">
                            `}
                        `}
                    </div>
                </div>
            `;
        }
    }
    
    gridBody.innerHTML = html;
    
    // Setup Test Connection button
    const testBtn = document.getElementById('testLdapConnectionBtn');
    if (testBtn) {
        testBtn.addEventListener('click', testLdapConnection);
    }
    
    // Setup ldapEnabled toggle to show/hide required fields
    if (editable) {
        const ldapEnabledCheckbox = document.getElementById('ldap_setting_ldapEnabled');
        if (ldapEnabledCheckbox) {
            // In edit mode, always enable all fields (just visual feedback)
            // Enable all required fields immediately
            enableAllLdapFields();
            
            ldapEnabledCheckbox.addEventListener('change', function() {
                // In edit mode, just update visual feedback, don't disable fields
                updateLdapFieldsVisualFeedback(this.checked);
            });
            // Initial visual state
            updateLdapFieldsVisualFeedback(ldapEnabledCheckbox.checked);
        }
    }
}

function enableAllLdapFields() {
    const allFields = ['ldapUrl', 'baseDn', 'bindDn', 'bindPassword', 'userSearchBase', 'userSearchFilter', 'groupSearchBase', 'connectionTimeout'];
    
    allFields.forEach(key => {
        const field = document.querySelector(`[data-ldap-field="${key}"]`);
        if (field) {
            const input = field.querySelector('input');
            if (input && input.tagName === 'INPUT') {
                input.removeAttribute('disabled');
                input.style.backgroundColor = 'white';
                input.style.cursor = 'text';
            }
            field.style.pointerEvents = 'auto';
        }
    });
}

function updateLdapFieldsVisualFeedback(enabled) {
    const requiredFields = ['ldapUrl', 'baseDn', 'bindDn', 'bindPassword', 'userSearchBase', 'userSearchFilter'];
    const optionalFields = ['groupSearchBase', 'connectionTimeout'];
    
    // Update visual feedback for required fields
    requiredFields.forEach(key => {
        const field = document.querySelector(`[data-ldap-field="${key}"]`);
        if (field) {
            if (enabled) {
                field.style.opacity = '1';
            } else {
                field.style.opacity = '0.7'; // Slightly dimmed but still editable
            }
        }
    });
    
    // Optional fields
    optionalFields.forEach(key => {
        const field = document.querySelector(`[data-ldap-field="${key}"]`);
        if (field) {
            field.style.opacity = '1';
        }
    });
}

function toggleLdapRequiredFields(enabled) {
    // Check if we're in edit mode
    const isEditMode = document.getElementById('settingsActions') && 
                      document.getElementById('settingsActions').style.display !== 'none';
    
    // In edit mode, always allow editing (just visual feedback)
    // In view mode, disable fields when ldapEnabled is false
    const shouldDisable = !isEditMode && !enabled;
    
    const requiredFields = ['ldapUrl', 'baseDn', 'bindDn', 'bindPassword', 'userSearchBase', 'userSearchFilter'];
    const optionalFields = ['groupSearchBase', 'connectionTimeout'];
    
    // Handle required fields
    requiredFields.forEach(key => {
        const field = document.querySelector(`[data-ldap-field="${key}"]`);
        if (field) {
            const input = field.querySelector('input, div');
            if (input) {
                if (shouldDisable) {
                    // Disable only in view mode when ldapEnabled is false
                    field.style.opacity = '0.6';
                    field.style.pointerEvents = 'none';
                    if (input.tagName === 'INPUT') {
                        input.setAttribute('disabled', 'disabled');
                        input.style.backgroundColor = '#f5f5f5';
                        input.style.cursor = 'not-allowed';
                    }
                } else {
                    // Enable in edit mode or when ldapEnabled is true
                    field.style.opacity = '1';
                    field.style.pointerEvents = 'auto';
                    if (input.tagName === 'INPUT') {
                        input.removeAttribute('disabled');
                        input.style.backgroundColor = 'white';
                        input.style.cursor = 'text';
                    }
                }
            }
        }
    });
    
    // Optional fields can always be edited, but show visual feedback
    optionalFields.forEach(key => {
        const field = document.querySelector(`[data-ldap-field="${key}"]`);
        if (field) {
            if (shouldDisable) {
                field.style.opacity = '0.7';
            } else {
                field.style.opacity = '1';
            }
        }
    });
}

async function testLdapConnection() {
    const testBtn = document.getElementById('testLdapConnectionBtn');
    const originalText = testBtn.innerHTML;
    
    try {
        // Disable button and show loading
        testBtn.disabled = true;
        testBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + escapeHtmlSys(sysT('adminPanel.common.testing', 'Testing...'));
        
        // Collect current settings (use originalSettings if in view mode, otherwise collect from form)
        let testSettings;
        const isEditMode = document.getElementById('settingsActions') && 
                          document.getElementById('settingsActions').style.display !== 'none';
        
        if (isEditMode) {
            // In edit mode, collect from form inputs
            testSettings = collectLdapSettings();
        } else {
            // In view mode, use originalSettings (but password will be masked, so use current from API)
            testSettings = { ...originalSettings };
            // Get fresh settings from API to ensure we have all values
            try {
                const response = await fetch('/api/admin/ldap/settings', {
                    credentials: 'include'
                });
                if (response.ok) {
                    const freshSettings = await response.json();
                    // Merge with originalSettings, but keep password from original if it exists
                    testSettings = { ...freshSettings, ...originalSettings };
                    // Remove masked password - test will use bindDn without password if needed
                    if (testSettings.bindPassword === '********' || testSettings.bindPassword === null) {
                        testSettings.bindPassword = originalSettings.bindPassword || '';
                    }
                }
            } catch (e) {
                // Use originalSettings if API call fails
                console.warn('Could not fetch fresh settings, using cached:', e);
            }
        }
        
        // Send test request
        const response = await fetch('/api/admin/ldap/settings/test', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(testSettings)
        });
        
        const result = await response.json();
        
        if (result.success) {
            showStatusMessage('✓ LDAP connection test successful!', 'success');
        } else {
            showStatusMessage(`✗ LDAP connection test failed: ${result.message}`, 'error');
        }
    } catch (error) {
        console.error('Error testing LDAP connection:', error);
        showStatusMessage(`✗ Error testing LDAP connection: ${error.message}`, 'error');
    } finally {
        testBtn.disabled = false;
        testBtn.innerHTML = originalText;
    }
}

function collectLdapSettings() {
    const settings = {};
    // Get all inputs (in edit mode) or use originalSettings (in view mode)
    const inputs = document.querySelectorAll('[id^="ldap_setting_"]');
    
    if (inputs.length === 0) {
        // No inputs found (view mode), return originalSettings
        return { ...originalSettings };
    }
    
    inputs.forEach(input => {
        // Skip if it's a div (view mode display)
        if (input.tagName === 'DIV') return;
        
        const key = input.getAttribute('data-key');
        const dataType = input.getAttribute('data-type');
        
        if (!key || !dataType) return;
        
        if (dataType === 'boolean') {
            settings[key] = input.checked;
        } else if (dataType === 'int') {
            settings[key] = parseInt(input.value) || 5000;
        } else if (dataType === 'password') {
            // Only include password if it's not empty (to keep current)
            if (input.value && input.value.trim() !== '') {
                settings[key] = input.value;
            } else {
                // Keep current password from originalSettings
                settings[key] = originalSettings[key] || null;
            }
        } else {
            settings[key] = input.value || '';
        }
    });
    
    // Fill in any missing values from originalSettings
    Object.keys(originalSettings).forEach(key => {
        if (settings[key] === undefined || settings[key] === null || settings[key] === '') {
            if (key !== 'bindPassword' || originalSettings[key] !== '********') {
                settings[key] = originalSettings[key];
            }
        }
    });
    
    return settings;
}

function enableEditMode() {
    if (!canEditSystemSettingsGroups()) {
        showSystemSettingsAccessDenied();
        return;
    }
    isEditMode = true;
    const group = document.getElementById('settingsGroup').value;
    
    // If DefaultWorkflows, show modal instead of edit mode
    if (group === 'DefaultWorkflows') {
        showDefaultWorkflowsRestartModal();
        return;
    }
    
    loadSystemSettings().then(() => {
        const currentGroup = document.getElementById('settingsGroup').value;
        if (currentGroup === 'Dashboard') {
            renderDashboardSettingsTable(originalSettings, true);
        } else if (currentGroup === 'Change Requests') {
            renderChangeRequestSettingsTable(originalSettings, true);
        } else if (currentGroup === 'LDAP Settings') {
            renderLdapSettingsTable(originalSettings, true);
        } else if (currentGroup === 'Enterprise Data Catalog') {
            renderEdcSettingsForm(originalSettings, true);
            // Add Test Connection button to header
            addTestConnectionButton();
        } else {
            renderSettingsTable(originalSettings, true);
        }
        document.getElementById('settingsActions').style.display = 'flex';
        showEditSettingsBtn(false);
        document.getElementById('settingsGroup').disabled = true;
    });
}

function disableEditMode() {
    isEditMode = false;
    document.getElementById('settingsActions').style.display = 'none';
    showEditSettingsBtn(true);
    document.getElementById('settingsGroup').disabled = false;
}

function cancelEditMode() {
    const group = document.getElementById('settingsGroup').value;
    if (group === 'Dashboard') {
        renderDashboardSettingsTable(originalSettings, false);
    } else if (group === 'Change Requests') {
        renderChangeRequestSettingsTable(originalSettings, false);
    } else if (group === 'LDAP Settings') {
        renderLdapSettingsTable(originalSettings, false);
    } else if (group === 'Enterprise Data Catalog') {
        renderEdcSettingsTable(originalSettings, false);
        removeTestConnectionButton();
    } else {
        renderSettingsTable(originalSettings, false);
    }
    disableEditMode();
}

async function saveSystemSettings() {
    try {
        const group = document.getElementById('settingsGroup').value;
        
        // Handle LDAP Settings separately
        if (group === 'LDAP Settings') {
            await saveLdapSettings();
            return;
        }
        
        // Handle EDC Settings separately
        if (group === 'Enterprise Data Catalog') {
            await saveEdcSettings();
            return;
        }
        
        const inputs = document.querySelectorAll('.settings-input');

        const settingsToSave = {};
        const dataMigrationEnabled = {};

        inputs.forEach(input => {
            const key = input.getAttribute('data-key');
            const dataType = input.getAttribute('data-type');

            // Skip link type settings (they are not editable settings)
            if (dataType === 'link') {
                return;
            }

            let value;

            // Handle checkbox (boolean) inputs
            if (dataType === 'boolean' && input.type === 'checkbox') {
                value = input.checked;

                // DATA_MIGRATION_ENABLED is saved via separate API
                if (key === 'DATA_MIGRATION_ENABLED') {
                    dataMigrationEnabled[key] = value;
                    return; // Skip adding to settingsToSave
                }
            } else if (dataType === 'dropdown' && input.tagName === 'SELECT') {
                value = input.value;
            } else {
                value = input.value;
            }

            // Validate and convert value based on type
            if (dataType === 'int') {
                const intValue = parseInt(value, 10);
                if (isNaN(intValue) || intValue < 0) {
                    throw new Error(`Invalid value for ${key}: must be a non-negative integer`);
                }
                value = intValue.toString();
            } else if (key === 'application_base_url') {
                // Validate URL format
                value = value.trim();
                if (value && !value.match(/^https?:\/\/.+/)) {
                    throw new Error('Application Base URL must start with http:// or https://');
                }
                // Remove trailing slash if present
                if (value.endsWith('/')) {
                    value = value.substring(0, value.length - 1);
                }
            }

            settingsToSave[key] = value;
        });

        // Save DATA_MIGRATION_ENABLED via its dedicated API if it was changed
        // Note: IMPORT_MIGRATED_DATA is a link, not a setting, so we skip it
        if (dataMigrationEnabled.hasOwnProperty('DATA_MIGRATION_ENABLED')) {
            try {
                const migrationResponse = await fetch('/api/admin/environment/data-migration', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    credentials: 'include',
                    body: JSON.stringify({ enabled: dataMigrationEnabled.DATA_MIGRATION_ENABLED })
                });

                if (!migrationResponse.ok) {
                    if (migrationResponse.status === 403) {
                        showSystemSettingsAccessDenied();
                        return;
                    }
                    const errorData = await migrationResponse.json().catch(() => ({}));
                    throw new Error(errorData.error || 'Failed to save Data Migration setting');
                }

                // After saving, refresh menu visibility
                if (typeof window.checkDataMigrationMenuVisibility === 'function') {
                    window.checkDataMigrationMenuVisibility();
                }
            } catch (error) {
                const m = (error && error.message) ? String(error.message) : '';
                if (m.includes('Access denied') || m.includes('SuperAdmin') || m.includes('Forbidden')) {
                    showSystemSettingsAccessDenied();
                    return;
                }
                console.error('Error saving Data Migration setting:', error);
                throw error;
            }
        }

        // Save other settings via system-settings API
        if (Object.keys(settingsToSave).length > 0) {
            const response = await fetch(`/api/system-settings/${encodeURIComponent(group)}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(settingsToSave)
            });

            if (!response.ok) {
                if (response.status === 403) {
                    showSystemSettingsAccessDenied();
                    return;
                }
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.error || 'Failed to save system settings');
            }
        }

        // Reload settings to get updated values
        await loadSystemSettings();
        showStatusMessage('Settings saved successfully', 'success');

    } catch (error) {
        const msg = (error && error.message) ? String(error.message) : '';
        if (msg.includes('Access denied') || msg.includes('SuperAdmin') || msg.includes('Forbidden')) {
            showSystemSettingsAccessDenied();
            return;
        }
        console.error('Error saving system settings:', error);
        showStatusMessage(msg || 'Error saving system settings', 'error');
    }
}

async function saveLdapSettings() {
    try {
        // Collect settings from form
        const settings = collectLdapSettings();
        
        // Validate required fields if LDAP is enabled
        if (settings.ldapEnabled) {
            if (!settings.ldapUrl || settings.ldapUrl.trim() === '') {
                throw new Error('LDAP URL is required when LDAP is enabled');
            }
            if (!settings.baseDn || settings.baseDn.trim() === '') {
                throw new Error('Base DN is required when LDAP is enabled');
            }
            if (!settings.bindDn || settings.bindDn.trim() === '') {
                throw new Error('Bind DN is required when LDAP is enabled');
            }
            // Password validation: check if password is provided or already exists
            const passwordInput = document.getElementById('ldap_setting_bindPassword');
            const hasPasswordInput = passwordInput && passwordInput.value && passwordInput.value.trim() !== '';
            const hasExistingPassword = originalSettings.bindPassword && 
                                      originalSettings.bindPassword !== '********' && 
                                      originalSettings.bindPassword !== null;
            
            if (!hasPasswordInput && !hasExistingPassword) {
                throw new Error('Bind Password is required when LDAP is enabled');
            }
            
            if (!settings.userSearchBase || settings.userSearchBase.trim() === '') {
                throw new Error('User Search Base is required when LDAP is enabled');
            }
            if (!settings.userSearchFilter || settings.userSearchFilter.trim() === '') {
                throw new Error('User Search Filter is required when LDAP is enabled');
            }
        }
        
        // Send save request
        const response = await fetch('/api/admin/ldap/settings', {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(settings)
        });
        
        if (!response.ok) {
            if (response.status === 403) {
                showSystemSettingsAccessDenied();
                return;
            }
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || 'Failed to save LDAP settings');
        }
        
        const savedSettings = await response.json();
        originalSettings = { ...savedSettings };
        
        // Reload settings to get updated values
        await loadSystemSettings();
        showStatusMessage('LDAP settings saved successfully', 'success');
        
    } catch (error) {
        const msg = (error && error.message) ? String(error.message) : '';
        if (msg.includes('Access denied') || msg.includes('SuperAdmin') || msg.includes('Forbidden')) {
            showSystemSettingsAccessDenied();
            return;
        }
        console.error('Error saving LDAP settings:', error);
        showStatusMessage(msg || 'Error saving LDAP settings', 'error');
    }
}

function showStatusMessage(message, type) {
    const statusDiv = document.getElementById('statusMessage');
    if (!statusDiv) return;
    statusDiv.textContent = message;
    statusDiv.style.display = 'block';

    if (type === 'success') {
        statusDiv.style.backgroundColor = '#d4edda';
        statusDiv.style.color = '#155724';
        statusDiv.style.border = '1px solid #c3e6cb';
    } else if (type === 'warning') {
        // Access denied / permission — informative, not alarmist
        statusDiv.style.backgroundColor = '#fff3cd';
        statusDiv.style.color = '#856404';
        statusDiv.style.border = '1px solid #ffc107';
    } else {
        statusDiv.style.backgroundColor = '#f8d7da';
        statusDiv.style.color = '#721c24';
        statusDiv.style.border = '1px solid #f5c6cb';
    }

    // Auto-hide after 5 seconds
    setTimeout(() => {
        statusDiv.style.display = 'none';
    }, 5000);
}

// ========== EDC Settings Functions ==========

/**
 * Render EDC Settings table view (read-only)
 */
function renderEdcSettingsTable(settings, editable) {
    const gridBody = document.getElementById('settingsGridBody');
    
    if (!settings) {
        settings = {
            eic_server_host: 'https://edc.local',
            eic_server_port: 9185,
            eic_server_login_username: 'Administrator',
            eic_server_login_password: null,
            eic_server_login_namespace: 'Native',
            eic_axon_resource_name: 'BUDG_Resource_copy',
            eic_enable_auto_lineage_recommendation: true,
            eic_axon_super_admin_email: 'admin@budg.com',
            eic_enable_lineage_email_notification: true,
            eic_enable_custom_attributes: true,
            eic_enable_cleanup_lineage_recommendations: true,
            eic_enable_filter: false,
            eic_update_onboarded_assets: true,
            eic_default_glossary: '',
            eic_request_timeout: 120,
            eic_proxy_host: '',
            eic_proxy_port: null,
            eic_ssl_insecure: false
        };
    }
    
    // EDC Settings metadata with BUDG-style display labels
    const edcMetadata = {
        eic_server_host: {
            name: 'Enterprise Data Catalog Server Host',
            helpText: 'URL format http(s)://<host_name> (store full scheme+host)',
            dataType: 'string',
            defaultValue: 'https://edc.local'
        },
        eic_server_port: {
            name: 'Enterprise Data Catalog Server Port',
            helpText: 'Port number for EDC server',
            dataType: 'int',
            defaultValue: '9185'
        },
        eic_server_login_username: {
            name: 'Enterprise Data Catalog Server Login User Name',
            helpText: 'Username for EDC authentication',
            dataType: 'string',
            defaultValue: 'Administrator'
        },
        eic_server_login_password: {
            name: 'Enterprise Data Catalog Server Login Password',
            helpText: 'Password for EDC authentication. Leave blank to keep current password.',
            dataType: 'password',
            defaultValue: '********'
        },
        eic_server_login_namespace: {
            name: 'Enterprise Data Catalog Server Login Namespace',
            helpText: 'Namespace for EDC login',
            dataType: 'string',
            defaultValue: 'Native'
        },
        eic_axon_resource_name: {
            name: 'Enterprise Data Catalog Resource Name',
            helpText: 'Resource name for EDC integration',
            dataType: 'string',
            defaultValue: 'BUDG_Resource_copy'
        },
        eic_enable_auto_lineage_recommendation: {
            name: 'Automatically Accept Lineage Recommendations',
            helpText: 'Enable automatic acceptance of lineage recommendations',
            dataType: 'boolean',
            defaultValue: 'true'
        },
        eic_axon_super_admin_email: {
            name: 'BUDG Super Admin Email (*)',
            helpText: 'Email address for BUDG super admin (required)',
            dataType: 'string',
            defaultValue: 'admin@budg.com',
            required: true
        },
        eic_enable_lineage_email_notification: {
            name: 'Enable Lineage Recommendation Notifications',
            helpText: 'Enable email notifications for lineage recommendations',
            dataType: 'boolean',
            defaultValue: 'true'
        },
        eic_enable_custom_attributes: {
            name: 'View Custom Attributes for Physical Fields',
            helpText: 'Enable viewing custom attributes for physical fields',
            dataType: 'boolean',
            defaultValue: 'true'
        },
        eic_enable_cleanup_lineage_recommendations: {
            name: 'Automatically Delete Attribute Relationships',
            helpText: 'Enable automatic deletion of attribute relationships',
            dataType: 'boolean',
            defaultValue: 'true'
        },
        eic_enable_filter: {
            name: 'Restrict Object Access Based on Enterprise Data Catalog',
            helpText: 'Enable filtering object access based on EDC',
            dataType: 'boolean',
            defaultValue: 'false'
        },
        eic_update_onboarded_assets: {
            name: 'Automatically Update Onboarded Objects',
            helpText: 'Enable automatic updates for onboarded objects',
            dataType: 'boolean',
            defaultValue: 'true'
        },
        eic_default_glossary: {
            name: 'Default Glossary',
            helpText: 'Default glossary name',
            dataType: 'string',
            defaultValue: ''
        },
        eic_request_timeout: {
            name: 'Configure Timeout Period for Enterprise Data Catalog API',
            helpText: 'Timeout in seconds for EDC API requests',
            dataType: 'int',
            defaultValue: '120'
        },
        eic_proxy_host: {
            name: 'Enterprise Data Catalog Server Proxy Host',
            helpText: 'Proxy host for EDC connections',
            dataType: 'string',
            defaultValue: ''
        },
        eic_proxy_port: {
            name: 'Enterprise Data Catalog Server Proxy Port Number',
            helpText: 'Proxy port for EDC connections',
            dataType: 'int',
            defaultValue: ''
        },
        eic_ssl_insecure: {
            name: 'Disable SSL Certificate Validation (Dev/Testing Only)',
            helpText: 'WARNING: Enable this only for development/testing with self-signed certificates. This should NEVER be enabled in production!',
            dataType: 'boolean',
            defaultValue: 'false'
        }
    };
    
    // Build table rows
    let tableRows = '';
    const keys = Object.keys(edcMetadata);
    
    for (const key of keys) {
        const metadata = edcMetadata[key];
        const value = settings[key];
        let valueDisplay = '';
        let defaultValueDisplay = metadata.defaultValue || '';
        
        if (metadata.dataType === 'boolean') {
            const boolValue = value === true || value === 'true' || value === 1 || value === '1';
            valueDisplay = boolValue ? 'true' : 'false';
            defaultValueDisplay = metadata.defaultValue === 'true' ? 'true' : 'false';
        } else if (metadata.dataType === 'password') {
            valueDisplay = value && value !== '********' ? '********' : (value || '');
            defaultValueDisplay = '********';
        } else {
            valueDisplay = value != null ? String(value) : '';
        }
        
        const isCustomizable = true; // All EDC settings are customizable
        
        tableRows += `
            <tr>
                <td style="padding: 12px;">
                    <div style="display: flex; align-items: center; gap: 5px;">
                        <span>${metadata.name}</span>
                        ${metadata.helpText ? `
                            <i class="fas fa-question-circle help-icon" 
                               title="${metadata.helpText}" 
                               style="color: #666; cursor: help; font-size: 14px;"></i>
                        ` : ''}
                    </div>
                </td>
                <td style="padding: 12px;">
                    <i class="fas fa-check" style="color: #28a745;"></i>
                </td>
                <td style="padding: 12px;">
                    <span class="data-type-badge" style="display: inline-block; padding: 2px 8px; background-color: #e9ecef; border-radius: 3px; font-size: 12px; color: #495057; font-weight: 500;">${metadata.dataType}</span>
                </td>
                <td style="padding: 12px; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 13px;">${defaultValueDisplay}</td>
                <td style="padding: 12px; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 13px;">${valueDisplay}</td>
            </tr>
        `;
    }
    
    gridBody.innerHTML = `
        <div style="grid-column: 1 / -1;">
            <table style="width: 100%; border-collapse: collapse; margin-top: 10px;">
                <thead>
                    <tr style="background-color: #f5f5f5; border-bottom: 2px solid #ddd;">
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Display Name</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Customizable</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Value Type</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Default Value</th>
                        <th style="padding: 12px; text-align: left; font-weight: 600;">Value</th>
                    </tr>
                </thead>
                <tbody>
                    ${tableRows}
                </tbody>
            </table>
        </div>
    `;
}

/**
 * Render EDC Settings form view (2-column edit mode)
 */
function renderEdcSettingsForm(settings, editable) {
    const gridBody = document.getElementById('settingsGridBody');
    
    if (!settings) {
        settings = {
            eic_server_host: 'https://edc.local',
            eic_server_port: 9185,
            eic_server_login_username: 'Administrator',
            eic_server_login_password: null,
            eic_server_login_namespace: 'Native',
            eic_axon_resource_name: 'BUDG_Resource_copy',
            eic_enable_auto_lineage_recommendation: true,
            eic_axon_super_admin_email: 'admin@budg.com',
            eic_enable_lineage_email_notification: true,
            eic_enable_custom_attributes: true,
            eic_enable_cleanup_lineage_recommendations: true,
            eic_enable_filter: false,
            eic_update_onboarded_assets: true,
            eic_default_glossary: '',
            eic_request_timeout: 120,
            eic_proxy_host: '',
            eic_proxy_port: null,
            eic_ssl_insecure: false
        };
    }
    
    // EDC Settings metadata
    const edcMetadata = {
        eic_server_host: {
            name: 'Enterprise Data Catalog Server Host',
            helpText: 'URL format http(s)://<host_name> (store full scheme+host)',
            dataType: 'string',
            required: true
        },
        eic_server_port: {
            name: 'Enterprise Data Catalog Server Port',
            helpText: 'Port number for EDC server',
            dataType: 'int',
            required: true
        },
        eic_server_login_username: {
            name: 'Enterprise Data Catalog Server Login User Name',
            helpText: 'Username for EDC authentication',
            dataType: 'string',
            required: true
        },
        eic_server_login_password: {
            name: 'Enterprise Data Catalog Server Login Password',
            helpText: 'Password for EDC authentication. Leave blank to keep current password.',
            dataType: 'password',
            required: true
        },
        eic_server_login_namespace: {
            name: 'Enterprise Data Catalog Server Login Namespace',
            helpText: 'Namespace for EDC login',
            dataType: 'string',
            required: false
        },
        eic_axon_resource_name: {
            name: 'Enterprise Data Catalog Resource Name',
            helpText: 'Resource name for EDC integration',
            dataType: 'string',
            required: false
        },
        eic_enable_auto_lineage_recommendation: {
            name: 'Automatically Accept Lineage Recommendations',
            helpText: 'Enable automatic acceptance of lineage recommendations',
            dataType: 'boolean',
            required: false
        },
        eic_axon_super_admin_email: {
            name: 'BUDG Super Admin Email (*)',
            helpText: 'Email address for BUDG super admin (required)',
            dataType: 'string',
            required: true
        },
        eic_enable_lineage_email_notification: {
            name: 'Enable Lineage Recommendation Notifications',
            helpText: 'Enable email notifications for lineage recommendations',
            dataType: 'boolean',
            required: false
        },
        eic_enable_custom_attributes: {
            name: 'View Custom Attributes for Physical Fields',
            helpText: 'Enable viewing custom attributes for physical fields',
            dataType: 'boolean',
            required: false
        },
        eic_enable_cleanup_lineage_recommendations: {
            name: 'Automatically Delete Attribute Relationships',
            helpText: 'Enable automatic deletion of attribute relationships',
            dataType: 'boolean',
            required: false
        },
        eic_enable_filter: {
            name: 'Restrict Object Access Based on Enterprise Data Catalog',
            helpText: 'Enable filtering object access based on EDC',
            dataType: 'boolean',
            required: false
        },
        eic_update_onboarded_assets: {
            name: 'Automatically Update Onboarded Objects',
            helpText: 'Enable automatic updates for onboarded objects',
            dataType: 'boolean',
            required: false
        },
        eic_default_glossary: {
            name: 'Default Glossary',
            helpText: 'Default glossary name',
            dataType: 'string',
            required: false
        },
        eic_request_timeout: {
            name: 'Configure Timeout Period for Enterprise Data Catalog API',
            helpText: 'Timeout in seconds for EDC API requests',
            dataType: 'int',
            required: true
        },
        eic_proxy_host: {
            name: 'Enterprise Data Catalog Server Proxy Host',
            helpText: 'Proxy host for EDC connections',
            dataType: 'string',
            required: false
        },
        eic_proxy_port: {
            name: 'Enterprise Data Catalog Server Proxy Port Number',
            helpText: 'Proxy port for EDC connections',
            dataType: 'int',
            required: false
        },
        eic_ssl_insecure: {
            name: 'Disable SSL Certificate Validation (Dev/Testing Only)',
            helpText: 'WARNING: Enable this only for development/testing with self-signed certificates. This should NEVER be enabled in production!',
            dataType: 'boolean',
            required: false
        }
    };
    
    // Build form fields in 2-column layout
    let leftColumn = '';
    let rightColumn = '';
    const keys = Object.keys(edcMetadata);
    
    for (let i = 0; i < keys.length; i++) {
        const key = keys[i];
        const metadata = edcMetadata[key];
        const value = settings[key];
        
        let fieldHtml = '';
        const fieldId = `edc_setting_${key}`;
        
        if (metadata.dataType === 'boolean') {
            const checked = value === true || value === 'true' || value === 1 || value === '1';
            fieldHtml = `
                <input type="checkbox" 
                       id="${fieldId}" 
                       ${checked ? 'checked' : ''}
                       ${!editable ? 'disabled' : ''}
                       style="width: auto; height: 20px; margin-top: 5px;">
            `;
        } else if (metadata.dataType === 'password') {
            const displayValue = value && value !== '********' ? '********' : '';
            fieldHtml = `
                <input type="password" 
                       id="${fieldId}" 
                       value="${displayValue}"
                       placeholder="Leave blank to keep current password"
                       ${!editable ? 'disabled' : ''}
                       class="settings-input"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #ccc; border-radius: 4px; font-size: 14px;">
            `;
        } else if (metadata.dataType === 'int') {
            fieldHtml = `
                <input type="number" 
                       id="${fieldId}" 
                       value="${value != null ? value : ''}"
                       ${!editable ? 'disabled' : ''}
                       class="settings-input"
                       min="0"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #ccc; border-radius: 4px; font-size: 14px;">
            `;
        } else {
            fieldHtml = `
                <input type="text" 
                       id="${fieldId}" 
                       value="${value != null ? String(value).replace(/"/g, '&quot;') : ''}"
                       ${!editable ? 'disabled' : ''}
                       class="settings-input"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #ccc; border-radius: 4px; font-size: 14px;">
            `;
        }
        
        const fieldItem = `
            <div class="setting-item">
                <div class="setting-header">
                    <span class="setting-label">${metadata.name}</span>
                    ${metadata.helpText ? `
                        <i class="fas fa-question-circle help-icon" 
                           title="${metadata.helpText}" 
                           style="color: #666; cursor: help; font-size: 14px;"></i>
                    ` : ''}
                </div>
                <div class="setting-value">
                    ${fieldHtml}
                </div>
            </div>
        `;
        
        // Alternate between left and right columns
        if (i % 2 === 0) {
            leftColumn += fieldItem;
        } else {
            rightColumn += fieldItem;
        }
    }
    
    gridBody.innerHTML = `
        <div class="setting-item" style="grid-column: 1;">
            ${leftColumn}
        </div>
        <div class="setting-item" style="grid-column: 2;">
            ${rightColumn}
        </div>
    `;
}

/**
 * Collect EDC settings from form
 */
function collectEdcSettings() {
    const settings = {};
    
    // Get all EDC setting inputs
    const inputs = document.querySelectorAll('[id^="edc_setting_"]');
    
    console.log('collectEdcSettings - found inputs:', inputs.length);
    
    inputs.forEach(input => {
        const key = input.id.replace('edc_setting_', '');
        let value;
        
        if (input.type === 'checkbox') {
            value = input.checked;
            console.log(`collectEdcSettings - ${key}: ${value} (checkbox)`);
        } else if (input.type === 'number') {
            value = input.value ? parseInt(input.value, 10) : null;
        } else if (input.type === 'password') {
            // If password is empty or masked, send "********" to preserve
            value = input.value && input.value.trim() !== '' ? input.value : '********';
        } else {
            value = input.value || '';
        }
        
        settings[key] = value;
    });
    
    console.log('collectEdcSettings - final settings:', settings);
    return settings;
}

/**
 * Save EDC settings
 */
async function saveEdcSettings() {
    try {
        // Collect settings from form
        const settings = collectEdcSettings();
        
        // Send save request
        const response = await fetch('/api/admin/settings/edc', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(settings)
        });
        
        if (!response.ok) {
            if (response.status === 403) {
                showSystemSettingsAccessDenied();
                return;
            }
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || 'Failed to save EDC settings');
        }
        
        const savedSettings = await response.json();
        originalSettings = { ...savedSettings };
        
        // Switch back to table view
        renderEdcSettingsTable(savedSettings, false);
        disableEditMode();
        
        showStatusMessage('EDC settings saved successfully', 'success');
        
    } catch (error) {
        const msg = (error && error.message) ? String(error.message) : '';
        if (msg.includes('Access denied') || msg.includes('SuperAdmin') || msg.includes('Forbidden')) {
            showSystemSettingsAccessDenied();
            return;
        }
        console.error('Error saving EDC settings:', error);
        showStatusMessage(msg || 'Error saving EDC settings', 'error');
    }
}

/**
 * Add Test Connection button to header (for EDC edit mode)
 */
function addTestConnectionButton() {
    const headerActions = document.getElementById('headerActions');
    if (!headerActions) return;
    
    // Remove existing button if any
    const existingBtn = document.getElementById('testConnectionBtn');
    if (existingBtn) {
        existingBtn.remove();
    }
    
    // Add Test Connection button
    const testBtn = document.createElement('button');
    testBtn.id = 'testConnectionBtn';
    testBtn.type = 'button';
    testBtn.innerHTML = '<i class="fas fa-plug"></i> ' + escapeHtmlSys(sysT('adminPanel.systemSettingsPage.testConnection', 'Test Connection'));
    testBtn.style.cssText = 'padding: 8px 20px; border: none; background: #248567; color: white; border-radius: 4px; cursor: pointer; font-weight: 500; display: flex; align-items: center; gap: 8px;';
    testBtn.onclick = testEdcConnection;
    
    headerActions.appendChild(testBtn);
    headerActions.style.display = 'flex';
}

/**
 * Remove Test Connection button from header
 */
function removeTestConnectionButton() {
    const testBtn = document.getElementById('testConnectionBtn');
    if (testBtn) {
        testBtn.remove();
    }
    const headerActions = document.getElementById('headerActions');
    if (headerActions && headerActions.children.length === 0) {
        headerActions.style.display = 'none';
    }
}

/**
 * Test EDC connection
 */
async function testEdcConnection() {
    const testBtn = document.getElementById('testConnectionBtn');
    const originalText = testBtn ? testBtn.innerHTML : '';
    
    try {
        // Show loading state
        if (testBtn) {
            testBtn.disabled = true;
            testBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + escapeHtmlSys(sysT('adminPanel.common.testing', 'Testing...'));
        }
        
        // Collect current form settings
        const settings = collectEdcSettings();
        console.log('Test connection - collected settings:', settings);
        console.log('Test connection - eic_ssl_insecure value:', settings.eic_ssl_insecure);
        
        // Send test request
        const response = await fetch('/api/admin/settings/edc/test-connection', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(settings)
        });
        
        const result = await response.json();
        
        if (result.ok) {
            const message = result.message || `Connected to EDC ${result.releaseVersion} build ${result.buildVersion} (${result.buildDate})`;
            showStatusMessage(message, 'success');
        } else {
            const errorMsg = result.message || sysT('adminPanel.systemSettingsPage.connectionTestFailed', 'Connection test failed');
            showStatusMessage(errorMsg, 'error');
        }
        
    } catch (error) {
        console.error('Error testing EDC connection:', error);
        showStatusMessage(error.message || sysT('adminPanel.systemSettingsPage.errorTestingEdc', 'Error testing EDC connection'), 'error');
    } finally {
        // Restore button state
        if (testBtn) {
            testBtn.disabled = false;
            testBtn.innerHTML = originalText;
        }
    }
}
