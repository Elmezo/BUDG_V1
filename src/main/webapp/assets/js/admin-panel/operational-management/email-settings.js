// Email Settings Management for Admin Panel

function showEmailSettingsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.emailSettings');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };

    contentArea.innerHTML = `
        <div class="email-settings-container" style="padding: 20px;">
            <div class="card" style="max-width: 800px; margin: 0 auto;">
                <div class="card-header" style="background-color: #248567; padding: 15px; border-bottom: 1px solid #ddd; display: flex; justify-content: space-between; align-items: center;">
                    <h5 style="margin: 0; color: white; font-weight: bold;">${T('adminPanel.emailSettings.pageTitle', 'EMAIL SETTINGS')}</h5>
                    <a href="/WEB-HELP/EmailHelp.html" target="_blank" 
                       style="color: white; text-decoration: none; font-size: 20px; cursor: pointer; padding: 5px 10px; border-radius: 50%; background-color: rgba(255, 255, 255, 0.2); transition: all 0.3s ease;"
                       onmouseover="this.style.backgroundColor='rgba(255, 255, 255, 0.3)'; this.style.transform='scale(1.1)'"
                       onmouseout="this.style.backgroundColor='rgba(255, 255, 255, 0.2)'; this.style.transform='scale(1)'"
                       title="${T('adminPanel.emailSettings.helpTitle', 'Email Configuration Help')}">
                        <i class="fas fa-question-circle"></i>
                    </a>
                </div>
                <div class="card-body" style="padding: 30px;">
                    <div id="emailSettingsForm">
                        <div style="margin-bottom: 20px;">
                            <label style="display: flex; align-items: center; gap: 10px; font-weight: 500;">
                                <input type="checkbox" id="emailEnabled" style="width: 20px; height: 20px;">
                                <span>${T('adminPanel.emailSettings.enableLabel', 'Enable Email Notifications Globally')}</span>
                            </label>
                            <p style="color: #666; font-size: 0.875rem; margin-top: 5px; margin-left: 30px;">
                                ${T('adminPanel.emailSettings.enableHint', 'When disabled, no emails will be sent regardless of notification rules.')}
                            </p>
                        </div>

                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 5px; font-weight: 500;">
                                ${T('adminPanel.emailSettings.smtpHost', 'SMTP Host')} <span style="color: red;">*</span>
                            </label>
                            <input type="text" id="smtpHost" class="form-control" 
                                   style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" 
                                   placeholder="e.g., smtp.gmail.com" required>
                        </div>

                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 5px; font-weight: 500;">
                                ${T('adminPanel.emailSettings.smtpPort', 'SMTP Port')} <span style="color: red;">*</span>
                            </label>
                            <input type="number" id="smtpPort" class="form-control" 
                                   style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" 
                                   placeholder="e.g., 587 for TLS, 465 for SSL" required>
                        </div>

                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 5px; font-weight: 500;">
                                ${T('adminPanel.emailSettings.username', 'Username')} <span style="color: red;">*</span>
                            </label>
                            <input type="text" id="smtpUsername" class="form-control" 
                                   style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" 
                                   placeholder="SMTP authentication username" required>
                        </div>

                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 5px; font-weight: 500;">
                                ${T('adminPanel.emailSettings.password', 'Password')} <span style="color: red;">*</span>
                            </label>
                            <input type="password" id="smtpPassword" class="form-control" 
                                   style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" 
                                   placeholder="SMTP authentication password">
                            <p style="color: #666; font-size: 0.875rem; margin-top: 5px;">
                                ${T('adminPanel.emailSettings.passwordHint', 'Leave empty to keep existing password.')}
                            </p>
                        </div>

                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 5px; font-weight: 500;">
                                ${T('adminPanel.emailSettings.encryption', 'Encryption')} <span style="color: red;">*</span>
                            </label>
                            <select id="encryption" class="form-control" 
                                    style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" required>
                                <option value="TLS">${T('adminPanel.emailSettings.tlsRecommended', 'TLS (Recommended)')}</option>
                                <option value="SSL">${T('adminPanel.emailSettings.ssl', 'SSL')}</option>
                            </select>
                        </div>

                        <div style="display: flex; gap: 10px; margin-top: 30px;">
                            <button type="button" id="testConnectionBtn" 
                                    style="padding: 10px 20px; border: 1px solid #248567; background: white; color: #248567; border-radius: 4px; cursor: pointer; font-weight: 500;">
                                <i class="fas fa-plug"></i> ${T('adminPanel.emailSettings.testConnection', 'Test Connection')}
                            </button>
                            <button type="button" id="saveSettingsBtn" 
                                    style="padding: 10px 20px; border: none; background: #248567; color: white; border-radius: 4px; cursor: pointer; font-weight: 500;">
                                <i class="fas fa-save"></i> ${T('adminPanel.emailSettings.saveSettings', 'Save Settings')}
                            </button>
                        </div>

                        <div id="statusMessage" style="margin-top: 20px; padding: 10px; border-radius: 4px; display: none;"></div>
                    </div>
                </div>
            </div>
        </div>

        <style>
            .form-control:focus {
                outline: none;
                border-color: #248567;
                box-shadow: 0 0 0 2px rgba(36, 133, 103, 0.2);
            }
            #testConnectionBtn:hover {
                background-color: #f0f0f0;
            }
            #saveSettingsBtn:hover {
                background-color: #1e6b52;
            }
        </style>
    `;

    // Initialize
    initializeEmailSettingsPage();
}

function initializeEmailSettingsPage() {
    loadEmailSettings();
    setupEmailSettingsEventListeners();
}

function setupEmailSettingsEventListeners() {
    const testBtn = document.getElementById('testConnectionBtn');
    const saveBtn = document.getElementById('saveSettingsBtn');

    if (testBtn) {
        testBtn.addEventListener('click', testConnection);
    }

    if (saveBtn) {
        saveBtn.addEventListener('click', saveSettings);
    }
}

async function loadEmailSettings() {
    try {
        const response = await fetch('/api/email-settings');
        if (!response.ok) throw new Error('Failed to load email settings');

        const settings = await response.json();

        document.getElementById('emailEnabled').checked = settings.enabled || false;
        document.getElementById('smtpHost').value = settings.smtpHost || '';
        document.getElementById('smtpPort').value = settings.smtpPort || '';
        document.getElementById('smtpUsername').value = settings.smtpUsername || '';
        document.getElementById('encryption').value = settings.encryption || 'TLS';
        // Password is not loaded for security
    } catch (error) {
        console.error('Error loading email settings:', error);
        showStatusMessage(typeof adminT === 'function' ? adminT('adminPanel.emailSettings.errorLoading', 'Error loading email settings') : 'Error loading email settings', 'error');
    }
}

async function testConnection() {
    const testBtn = document.getElementById('testConnectionBtn');
    const originalText = testBtn.innerHTML;

    testBtn.disabled = true;
    testBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Testing...';

    try {
        const settings = getSettingsFromForm();
        if (!validateSettings(settings)) {
            showStatusMessage(typeof adminT === 'function' ? adminT('adminPanel.emailSettings.fillRequired', 'Please fill in all required fields') : 'Please fill in all required fields', 'error');
            testBtn.disabled = false;
            testBtn.innerHTML = originalText;
            return;
        }

        const response = await fetch('/api/email-settings/test', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(settings)
        });

        const result = await response.json();

        if (result.success) {
            showStatusMessage(typeof adminT === 'function' ? adminT('adminPanel.emailSettings.connectionSuccess', 'Connection test successful!') : 'Connection test successful!', 'success');
        } else {
            const tmpl = typeof adminT === 'function' ? adminT('adminPanel.emailSettings.connectionFailed', 'Connection test failed: {msg}') : 'Connection test failed: {msg}';
            showStatusMessage(tmpl.replace('{msg}', result.message || (typeof adminT === 'function' ? adminT('adminPanel.staticPageEditor.unknownError', 'Unknown error') : 'Unknown error')), 'error');
        }
    } catch (error) {
        console.error('Error testing connection:', error);
        const et = typeof adminT === 'function' ? adminT('adminPanel.emailSettings.errorTesting', 'Error testing connection: {msg}') : 'Error testing connection: {msg}';
        showStatusMessage(et.replace('{msg}', error.message), 'error');
    } finally {
        testBtn.disabled = false;
        testBtn.innerHTML = originalText;
    }
}

async function saveSettings() {
    const saveBtn = document.getElementById('saveSettingsBtn');
    const originalText = saveBtn.innerHTML;

    saveBtn.disabled = true;
    saveBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + (typeof adminT === 'function' ? adminT('adminPanel.emailSettings.saving', 'Saving...') : 'Saving...');

    try {
        const settings = getSettingsFromForm();
        if (!validateSettings(settings)) {
            showStatusMessage(typeof adminT === 'function' ? adminT('adminPanel.emailSettings.fillRequired', 'Please fill in all required fields') : 'Please fill in all required fields', 'error');
            saveBtn.disabled = false;
            saveBtn.innerHTML = originalText;
            return;
        }

        const response = await fetch('/api/email-settings', {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(settings)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Failed to save settings');
        }

        showStatusMessage(typeof adminT === 'function' ? adminT('adminPanel.emailSettings.savedSuccess', 'Email settings saved successfully!') : 'Email settings saved successfully!', 'success');
    } catch (error) {
        console.error('Error saving settings:', error);
        const es = typeof adminT === 'function' ? adminT('adminPanel.emailSettings.errorSaving', 'Error saving settings: {msg}') : 'Error saving settings: {msg}';
        showStatusMessage(es.replace('{msg}', error.message), 'error');
    } finally {
        saveBtn.disabled = false;
        saveBtn.innerHTML = originalText;
    }
}

function getSettingsFromForm() {
    return {
        enabled: document.getElementById('emailEnabled').checked,
        smtpHost: document.getElementById('smtpHost').value.trim(),
        smtpPort: parseInt(document.getElementById('smtpPort').value),
        smtpUsername: document.getElementById('smtpUsername').value.trim(),
        smtpPassword: document.getElementById('smtpPassword').value, // May be empty
        encryption: document.getElementById('encryption').value
    };
}

function validateSettings(settings) {
    if (!settings.smtpHost || settings.smtpHost.trim() === '') {
        return false;
    }
    if (!settings.smtpPort || settings.smtpPort <= 0) {
        return false;
    }
    if (!settings.smtpUsername || settings.smtpUsername.trim() === '') {
        return false;
    }
    // Password can be empty (to keep existing)
    if (!settings.encryption) {
        return false;
    }
    return true;
}

function showStatusMessage(message, type) {
    const statusDiv = document.getElementById('statusMessage');
    if (!statusDiv) return;

    statusDiv.style.display = 'block';
    statusDiv.textContent = message;
    statusDiv.style.backgroundColor = type === 'success' ? '#d4edda' : '#f8d7da';
    statusDiv.style.color = type === 'success' ? '#155724' : '#721c24';
    statusDiv.style.border = `1px solid ${type === 'success' ? '#c3e6cb' : '#f5c6cb'}`;

    // Auto-hide after 5 seconds
    setTimeout(() => {
        statusDiv.style.display = 'none';
    }, 5000);
}





