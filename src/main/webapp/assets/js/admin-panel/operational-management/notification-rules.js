// Notification Rules Management for Admin Panel
function nrT(k, f) { return typeof adminT === 'function' ? adminT(k, f) : f; }

function showNotificationRulesContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.notificationRules');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };

    contentArea.innerHTML = `
        <div class="notification-rules-container" style="padding: 20px;">
            <div class="card">
                <div class="card-header" style="background-color: #248567; padding: 15px; border-bottom: 1px solid #ddd; display: flex; justify-content: space-between; align-items: center;">
                    <h5 style="margin: 0; color: white; font-weight: bold;">${T('adminPanel.notificationRulesPage.pageTitle', 'NOTIFICATION RULES')}</h5>
                    <button class="btn btn-sm" id="addRuleBtn" style="background-color: #fff; border: 1px solid #ddd; padding: 5px 15px; display: flex; align-items: center; gap: 5px;">
                        <i class="fas fa-plus"></i> ${T('adminPanel.notificationRulesPage.addRule', 'Add Rule')}
                    </button>
                </div>
                <div class="card-body" style="padding: 0;">
                    <table class="table table-bordered" style="margin: 0; width: 100%; border-collapse: collapse;">
                        <thead style="background-color: #f5f5f5;">
                            <tr>
                                <th style="padding: 12px 15px; text-align: left; font-weight: 600; color: #333; border: 1px solid #e0e0e0;">${T('adminPanel.notificationRulesPage.colModule', 'Module')}</th>
                                <th style="padding: 12px 15px; text-align: left; font-weight: 600; color: #333; border: 1px solid #e0e0e0;">${T('adminPanel.notificationRulesPage.colEventType', 'Event Type')}</th>
                                <th style="padding: 12px 15px; text-align: left; font-weight: 600; color: #333; border: 1px solid #e0e0e0;">${T('adminPanel.notificationRulesPage.colRecipient', 'Recipient')}</th>
                                <th style="padding: 12px 15px; text-align: left; font-weight: 600; color: #333; border: 1px solid #e0e0e0;">${T('adminPanel.notificationRulesPage.colChannels', 'Channels')}</th>
                                <th style="padding: 12px 15px; text-align: left; font-weight: 600; color: #333; border: 1px solid #e0e0e0;">${T('adminPanel.notificationRulesPage.colDeliveryMode', 'Delivery Mode')}</th>
                                <th style="padding: 12px 15px; text-align: left; font-weight: 600; color: #333; border: 1px solid #e0e0e0;">${T('adminPanel.notificationRulesPage.colActive', 'Active')}</th>
                                <th style="padding: 12px 15px; text-align: left; font-weight: 600; color: #333; border: 1px solid #e0e0e0;">${T('adminPanel.notificationRulesPage.colActions', 'Actions')}</th>
                            </tr>
                        </thead>
                        <tbody id="rulesTableBody">
                            <tr>
                                <td colspan="7" style="padding: 40px 20px; text-align: center; color: #999; border: 1px solid #e0e0e0;">${T('adminPanel.notificationRulesPage.loadingRules', 'Loading rules...')}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <!-- Rule Modal -->
        <div id="ruleModal" class="modal" style="display: none; position: fixed; z-index: 10000; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.4);">
            <div class="modal-content" style="background-color: #fefefe; margin: 5% auto; padding: 20px; border: 1px solid #888; width: 80%; max-width: 600px; border-radius: 8px;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h3 id="modalTitle">${T('adminPanel.notificationRulesPage.modalAddTitle', 'Add Notification Rule')}</h3>
                    <span class="close-modal" style="color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor: pointer;">&times;</span>
                </div>
                <form id="ruleForm">
                    <div style="margin-bottom: 15px;">
                        <label style="display: flex; align-items: center; gap: 5px; margin-bottom: 5px; font-weight: 500;">
                            ${T('adminPanel.notificationRulesPage.labelModule', 'Module')} <span style="color: red;">*</span>
                            <span class="tooltip-icon" data-tooltip="${escapeHtml(T('adminPanel.notificationRulesPage.tooltipModule', ''))}">
                                <i class="fas fa-question-circle" style="color: #248567; cursor: help; font-size: 14px;"></i>
                            </span>
                        </label>
                        <select id="moduleSelect" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" required>
                            <option value="">${T('adminPanel.notificationRulesPage.loading', 'Loading...')}</option>
                        </select>
                        <div id="moduleError" class="field-error" style="display: none; color: #dc3545; font-size: 0.875rem; margin-top: 3px;"></div>
                    </div>
                    <div style="margin-bottom: 15px;">
                        <label style="display: flex; align-items: center; gap: 5px; margin-bottom: 5px; font-weight: 500;">
                            ${T('adminPanel.notificationRulesPage.labelEventType', 'Event Type')} <span style="color: red;">*</span>
                            <span class="tooltip-icon" data-tooltip="${escapeHtml(T('adminPanel.notificationRulesPage.tooltipEventType', ''))}">
                                <i class="fas fa-question-circle" style="color: #248567; cursor: help; font-size: 14px;"></i>
                            </span>
                        </label>
                        <select id="eventTypeSelect" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" required>
                            <option value="">${T('adminPanel.notificationRulesPage.selectPlaceholder', 'Select...')}</option>
                            <option value="ASSIGN">ASSIGN</option>
                            <option value="OVERDUE">OVERDUE</option>
                            <option value="ESCALATION">ESCALATION</option>
                        </select>
                        <div id="eventTypeError" class="field-error" style="display: none; color: #dc3545; font-size: 0.875rem; margin-top: 3px;"></div>
                    </div>
                    <div style="margin-bottom: 15px;">
                        <label style="display: flex; align-items: center; gap: 5px; margin-bottom: 5px; font-weight: 500;">
                            Recipient Role
                            <span class="tooltip-icon" data-tooltip="Specify a role name to receive notifications. Leave empty to use the task's assigned role. For ESCALATION events, typically specify a supervisor role.">
                                <i class="fas fa-question-circle" style="color: #248567; cursor: help; font-size: 14px;"></i>
                            </span>
                        </label>
                        <input type="text" id="recipientRoleInput" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" placeholder="Leave empty to use task role">
                        <div id="recipientRoleError" class="field-error" style="display: none; color: #dc3545; font-size: 0.875rem; margin-top: 3px;"></div>
                    </div>
                    <div style="margin-bottom: 15px;">
                        <label style="display: flex; align-items: center; gap: 5px; margin-bottom: 5px; font-weight: 500;">
                            ${T('adminPanel.notificationRulesPage.labelRecipientUserId', 'Recipient User ID')}
                            <span class="tooltip-icon" data-tooltip="${escapeHtml(T('adminPanel.notificationRulesPage.tooltipRecipientUserId', ''))}">
                                <i class="fas fa-question-circle" style="color: #248567; cursor: help; font-size: 14px;"></i>
                            </span>
                        </label>
                        <input type="number" id="recipientUserIdInput" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" placeholder="${escapeHtml(T('adminPanel.notificationRulesPage.placeholderRecipientUserId', ''))}" min="1">
                        <div id="recipientUserIdError" class="field-error" style="display: none; color: #dc3545; font-size: 0.875rem; margin-top: 3px;"></div>
                    </div>
                    <div style="margin-bottom: 15px;">
                        <label style="display: flex; align-items: center; gap: 5px; margin-bottom: 5px; font-weight: 500;">
                            ${T('adminPanel.notificationRulesPage.labelChannels', 'Channels')} <span style="color: red;">*</span>
                            <span class="tooltip-icon" data-tooltip="${escapeHtml(T('adminPanel.notificationRulesPage.tooltipChannels', ''))}">
                                <i class="fas fa-question-circle" style="color: #248567; cursor: help; font-size: 14px;"></i>
                            </span>
                        </label>
                        <div style="display: flex; gap: 20px;">
                            <label style="display: flex; align-items: center; gap: 5px;">
                                <input type="checkbox" id="channelUI" value="ui" checked>
                                <span>${T('adminPanel.notificationRulesPage.channelUI', 'UI (In-app notifications)')}</span>
                            </label>
                            <label style="display: flex; align-items: center; gap: 5px;">
                                <input type="checkbox" id="channelEmail" value="email">
                                <span>${T('adminPanel.notificationRulesPage.channelEmail', 'Email')}</span>
                            </label>
                        </div>
                        <p style="color: #666; font-size: 0.875rem; margin-top: 5px; font-style: italic;">
                            ${T('adminPanel.notificationRulesPage.channelsNote', 'Note: If Email is unchecked...')}
                        </p>
                        <div id="channelsError" class="field-error" style="display: none; color: #dc3545; font-size: 0.875rem; margin-top: 3px;"></div>
                    </div>
                    <div style="margin-bottom: 15px;">
                        <label style="display: flex; align-items: center; gap: 5px; margin-bottom: 5px; font-weight: 500;">
                            Delivery Mode (Email)
                            <span class="tooltip-icon" data-tooltip="IMMEDIATE: Send email as soon as the event occurs. BATCHED: Queue email for scheduled batch delivery (respects user's email frequency preferences: Daily, Weekly, Monthly)">
                                <i class="fas fa-question-circle" style="color: #248567; cursor: help; font-size: 14px;"></i>
                            </span>
                        </label>
                        <select id="deliveryModeSelect" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                            <option value="IMMEDIATE">Immediate - Send email as soon as event occurs</option>
                            <option value="BATCHED">Batched - Queue email for scheduled batch delivery</option>
                        </select>
                        <p style="color: #666; font-size: 0.875rem; margin-top: 5px;">
                            Batched emails respect user's email frequency preferences (Daily, Weekly, Monthly).
                        </p>
                    </div>
                    <div style="margin-bottom: 15px;">
                        <label style="display: flex; align-items: center; gap: 10px;">
                            <input type="checkbox" id="activeToggle" checked>
                            <span style="display: flex; align-items: center; gap: 5px;">
                                Active
                                <span class="tooltip-icon" data-tooltip="Enable or disable this notification rule. Inactive rules will not trigger notifications.">
                                    <i class="fas fa-question-circle" style="color: #248567; cursor: help; font-size: 14px;"></i>
                                </span>
                            </span>
                        </label>
                    </div>
                    <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px;">
                        <button type="button" class="btn btn-secondary" id="cancelRuleBtn" style="padding: 8px 20px; border: 1px solid #ccc; background: #f5f5f5; border-radius: 4px; cursor: pointer;">Cancel</button>
                        <button type="submit" class="btn btn-primary" style="padding: 8px 20px; border: none; background: #248567; color: white; border-radius: 4px; cursor: pointer;">Save</button>
                    </div>
                </form>
            </div>
        </div>

        <style>
            .table tbody tr:hover {
                background-color: #f5f5f5;
            }
            .table tbody td {
                padding: 12px 15px;
                border: 1px solid #e0e0e0;
                color: #333;
            }
            .btn-action {
                background: none;
                border: none;
                color: #248567;
                cursor: pointer;
                padding: 5px 10px;
                margin: 0 2px;
            }
            .btn-action:hover {
                background-color: #f0f0f0;
            }
            .close-modal:hover {
                color: #000;
            }
            /* Tooltip styles */
            .tooltip-icon {
                position: relative;
                display: inline-block;
            }
            .tooltip-icon:hover::after {
                content: attr(data-tooltip);
                position: absolute;
                bottom: calc(100% + 8px);
                left: 50%;
                transform: translateX(-50%);
                padding: 8px 12px;
                background-color: #333;
                color: #fff;
                border-radius: 4px;
                font-size: 0.875rem;
                z-index: 10001;
                box-shadow: 0 2px 8px rgba(0,0,0,0.2);
                max-width: 300px;
                white-space: normal;
                width: max-content;
                line-height: 1.4;
                pointer-events: none;
            }
            .tooltip-icon:hover::before {
                content: '';
                position: absolute;
                bottom: calc(100% + 3px);
                left: 50%;
                transform: translateX(-50%);
                border: 5px solid transparent;
                border-top-color: #333;
                z-index: 10001;
                pointer-events: none;
            }
            .field-error {
                display: block;
                color: #dc3545;
                font-size: 0.875rem;
                margin-top: 3px;
            }
            .success-message {
                position: fixed;
                top: 20px;
                right: 20px;
                background-color: #28a745;
                color: white;
                padding: 12px 20px;
                border-radius: 4px;
                box-shadow: 0 2px 8px rgba(0,0,0,0.2);
                z-index: 10002;
                display: flex;
                align-items: center;
                gap: 10px;
                animation: slideIn 0.3s ease-out;
            }
            @keyframes slideIn {
                from {
                    transform: translateX(100%);
                    opacity: 0;
                }
                to {
                    transform: translateX(0);
                    opacity: 1;
                }
            }
            .error-message {
                position: fixed;
                top: 20px;
                right: 20px;
                background-color: #dc3545;
                color: white;
                padding: 12px 20px;
                border-radius: 4px;
                box-shadow: 0 2px 8px rgba(0,0,0,0.2);
                z-index: 10002;
                display: flex;
                align-items: center;
                gap: 10px;
                animation: slideIn 0.3s ease-out;
            }
        </style>
    `;

    // Initialize
    initializeNotificationRulesPage();
}

function initializeNotificationRulesPage() {
    loadRules();
    loadModules();
    setupNotificationRulesEventListeners();
}

function setupNotificationRulesEventListeners() {
    // Add rule button
    const addBtn = document.getElementById('addRuleBtn');
    if (addBtn) {
        addBtn.addEventListener('click', () => {
            openRuleModal();
        });
    }

    // Modal close
    const modal = document.getElementById('ruleModal');
    const closeBtn = modal.querySelector('.close-modal');
    const cancelBtn = document.getElementById('cancelRuleBtn');

    if (closeBtn) {
        closeBtn.addEventListener('click', () => {
            closeRuleModal();
        });
    }
    if (cancelBtn) {
        cancelBtn.addEventListener('click', () => {
            closeRuleModal();
        });
    }

    // Form submit
    const form = document.getElementById('ruleForm');
    if (form) {
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            saveRule();
        });
    }

    // Close modal on outside click
    if (modal) {
        window.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeRuleModal();
            }
        });
    }
}

async function loadRules() {
    try {
        const response = await fetch('/api/notification-rules');
        if (!response.ok) {
            let errorMessage = `Failed to load rules (${response.status})`;
            try {
                const errorData = await response.json();
                if (errorData.error) {
                    errorMessage = errorData.error;
                }
            } catch (e) {
                errorMessage = `Failed to load rules: ${response.statusText || response.status}`;
            }
            throw new Error(errorMessage);
        }

        const rules = await response.json();
        renderRulesTable(rules);
    } catch (error) {
        console.error('Error loading rules:', error);
        let errorMessage = 'Error loading rules';
        if (error.message) {
            if (error.message.includes('Failed to fetch')) {
                errorMessage = 'Unable to connect to server';
            } else {
                errorMessage = error.message;
            }
        }
        const tbody = document.getElementById('rulesTableBody');
        if (tbody) {
            tbody.innerHTML = `<tr><td colspan="7" style="padding: 40px 20px; text-align: center; color: #999;">${escapeHtml(errorMessage)}</td></tr>`;
        }
    }
}

async function loadModules() {
    try {
        const response = await fetch('/api/notification-rules/modules');
        if (!response.ok) {
            let errorMessage = `Failed to load modules (${response.status})`;
            try {
                const errorData = await response.json();
                if (errorData.error) {
                    errorMessage = errorData.error;
                }
            } catch (e) {
                errorMessage = `Failed to load modules: ${response.statusText || response.status}`;
            }
            throw new Error(errorMessage);
        }

        const modules = await response.json();
        const select = document.getElementById('moduleSelect');
        if (select) {
            select.innerHTML = '<option value="">' + nrT('adminPanel.notificationRulesPage.selectPlaceholder', 'Select...') + '</option>';
            modules.forEach(module => {
                const option = document.createElement('option');
                option.value = module.name;
                option.textContent = module.displayName;
                select.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error loading modules:', error);
        const select = document.getElementById('moduleSelect');
        if (select) {
            select.innerHTML = '<option value="">' + nrT('adminPanel.notificationRulesPage.errorLoadingModules', 'Error loading modules') + '</option>';
        }
    }
}

function renderRulesTable(rules) {
    const tbody = document.getElementById('rulesTableBody');
    if (!tbody) return;

    if (rules.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="padding: 40px 20px; text-align: center; color: #999;">' + escapeHtml(nrT('adminPanel.notificationRulesPage.noRulesFound', 'No rules found')) + '</td></tr>';
        return;
    }

    tbody.innerHTML = rules.map(rule => {
        const recipient = rule.recipientUserId
            ? nrT('adminPanel.notificationRulesPage.recipientUserIdPrefix', 'User ID:') + ' ' + rule.recipientUserId
            : (rule.recipientRole || nrT('adminPanel.notificationRulesPage.recipientTaskRole', 'Task Role'));
        const channels = Array.isArray(rule.channels) ? rule.channels.join(', ') : rule.channels;
        const deliveryMode = rule.deliveryMode || 'IMMEDIATE';
        const deliveryModeDisplay = deliveryMode === 'IMMEDIATE'
            ? nrT('adminPanel.notificationRulesPage.deliveryImmediateShort', 'Immediate')
            : nrT('adminPanel.notificationRulesPage.deliveryBatchedShort', 'Batched');

        return `
            <tr>
                <td>${escapeHtml(rule.module || '*')}</td>
                <td>${escapeHtml(rule.eventType)}</td>
                <td>${escapeHtml(recipient)}</td>
                <td>${escapeHtml(channels)}</td>
                <td>${escapeHtml(deliveryModeDisplay)}</td>
                <td>
                    <label class="switch">
                        <input type="checkbox" ${rule.active ? 'checked' : ''} 
                               onchange="toggleRuleActive(${rule.id}, this.checked)">
                        <span class="slider round"></span>
                    </label>
                </td>
                <td>
                    <button class="btn-action" onclick="editRule(${rule.id})" title="${escapeHtml(nrT('adminPanel.notificationRulesPage.titleEdit', 'Edit'))}">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn-action" onclick="deleteRule(${rule.id})" title="${escapeHtml(nrT('adminPanel.notificationRulesPage.titleDelete', 'Delete'))}" style="color: #dc3545;">
                        <i class="fas fa-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

function openRuleModal(ruleId = null) {
    const modal = document.getElementById('ruleModal');
    const title = document.getElementById('modalTitle');
    const form = document.getElementById('ruleForm');

    // Clear any previous errors
    clearFieldErrors();

    if (ruleId) {
        title.textContent = nrT('adminPanel.notificationRulesPage.modalEditTitle', 'Edit Notification Rule');
        loadRuleForEdit(ruleId);
    } else {
        title.textContent = nrT('adminPanel.notificationRulesPage.modalAddTitle', 'Add Notification Rule');
        form.reset();
        form.removeAttribute('data-rule-id');
        document.getElementById('channelUI').checked = true;
        document.getElementById('deliveryModeSelect').value = 'IMMEDIATE';
        document.getElementById('activeToggle').checked = true;
    }

    modal.style.display = 'block';
}

function closeRuleModal() {
    const modal = document.getElementById('ruleModal');
    modal.style.display = 'none';
}

async function loadRuleForEdit(ruleId) {
    try {
        const response = await fetch(`/api/notification-rules/${ruleId}`);
        if (!response.ok) {
            let errorMessage = `Failed to load rule (${response.status})`;
            try {
                const errorData = await response.json();
                if (errorData.error) {
                    errorMessage = errorData.error;
                }
            } catch (e) {
                errorMessage = `Failed to load rule: ${response.statusText || response.status}`;
            }
            throw new Error(errorMessage);
        }

        const rule = await response.json();

        document.getElementById('moduleSelect').value = rule.module || '';
        document.getElementById('eventTypeSelect').value = rule.eventType || '';
        document.getElementById('recipientRoleInput').value = rule.recipientRole || '';
        document.getElementById('recipientUserIdInput').value = rule.recipientUserId || '';

        // Set channels
        const channels = Array.isArray(rule.channels) ? rule.channels : [];
        document.getElementById('channelUI').checked = channels.includes('ui');
        document.getElementById('channelEmail').checked = channels.includes('email');

        // Set delivery mode
        document.getElementById('deliveryModeSelect').value = rule.deliveryMode || 'IMMEDIATE';

        document.getElementById('activeToggle').checked = rule.active !== false;

        // Store rule ID for update
        document.getElementById('ruleForm').setAttribute('data-rule-id', ruleId);
    } catch (error) {
        console.error('Error loading rule:', error);
        let errorMessage = 'Failed to load rule';
        if (error.message) {
            if (error.message.includes('Failed to fetch')) {
                errorMessage = 'Unable to connect to server. Please check your connection.';
            } else {
                errorMessage = error.message;
            }
        }
        showErrorMessage(errorMessage);
        closeRuleModal();
    }
}

async function saveRule() {
    const form = document.getElementById('ruleForm');
    const ruleId = form.getAttribute('data-rule-id');

    // Validate form
    if (!validateRuleForm()) {
        showErrorMessage(nrT('adminPanel.notificationRulesPage.fixFormErrors', 'Please fix the errors in the form'));
        return;
    }

    const channels = [];
    if (document.getElementById('channelUI').checked) channels.push('ui');
    if (document.getElementById('channelEmail').checked) channels.push('email');

    const recipientUserIdInput = document.getElementById('recipientUserIdInput').value;
    const rule = {
        module: document.getElementById('moduleSelect').value.trim(),
        eventType: document.getElementById('eventTypeSelect').value,
        recipientRole: document.getElementById('recipientRoleInput').value.trim() || null,
        recipientUserId: recipientUserIdInput && recipientUserIdInput.trim() !== '' ?
            parseInt(recipientUserIdInput) : null,
        channels: channels,
        deliveryMode: document.getElementById('deliveryModeSelect').value || 'IMMEDIATE',
        active: document.getElementById('activeToggle').checked
    };

    try {
        const url = ruleId ? `/api/notification-rules/${ruleId}` : '/api/notification-rules';
        const method = ruleId ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(rule)
        });

        if (!response.ok) {
            let errorMessage = `Failed to save rule (${response.status})`;
            try {
                const errorData = await response.json();
                if (errorData.error) {
                    errorMessage = errorData.error;
                }
            } catch (e) {
                errorMessage = `Failed to save rule: ${response.statusText || response.status}`;
            }
            throw new Error(errorMessage);
        }

        const savedRule = await response.json();
        closeRuleModal();
        showSuccessMessage(ruleId ? nrT('adminPanel.notificationRulesPage.updatedSuccess', '...') : nrT('adminPanel.notificationRulesPage.createdSuccess', '...'));
        loadRules();
    } catch (error) {
        console.error('Error saving rule:', error);
        let errorMessage = 'Failed to save rule';
        if (error.message) {
            if (error.message.includes('Failed to fetch')) {
                errorMessage = 'Unable to connect to server. Please check your connection.';
            } else {
                errorMessage = error.message;
            }
        }
        showErrorMessage(errorMessage);
    }
}

async function editRule(ruleId) {
    openRuleModal(ruleId);
}

async function deleteRule(ruleId) {
    if (!confirm(nrT('adminPanel.notificationRulesPage.confirmDelete', 'Are you sure...'))) {
        return;
    }

    try {
        const response = await fetch(`/api/notification-rules/${ruleId}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            let errorMessage = `Failed to delete rule (${response.status})`;
            try {
                const errorData = await response.json();
                if (errorData.error) {
                    errorMessage = errorData.error;
                }
            } catch (e) {
                errorMessage = `Failed to delete rule: ${response.statusText || response.status}`;
            }
            throw new Error(errorMessage);
        }

        showSuccessMessage(nrT('adminPanel.notificationRulesPage.deletedSuccess', '...'));
        loadRules();
    } catch (error) {
        console.error('Error deleting rule:', error);
        let errorMessage = 'Failed to delete rule';
        if (error.message) {
            if (error.message.includes('Failed to fetch')) {
                errorMessage = 'Unable to connect to server. Please check your connection.';
            } else {
                errorMessage = error.message;
            }
        }
        showErrorMessage(errorMessage);
    }
}

async function toggleRuleActive(ruleId, active) {
    try {
        const response = await fetch(`/api/notification-rules/${ruleId}`);
        if (!response.ok) {
            let errorMessage = `Failed to load rule (${response.status})`;
            try {
                const errorData = await response.json();
                if (errorData.error) {
                    errorMessage = errorData.error;
                }
            } catch (e) {
                errorMessage = `Failed to load rule: ${response.statusText || response.status}`;
            }
            throw new Error(errorMessage);
        }

        const rule = await response.json();
        rule.active = active;

        const updateResponse = await fetch(`/api/notification-rules/${ruleId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(rule)
        });

        if (!updateResponse.ok) {
            let errorMessage = `Failed to update rule (${updateResponse.status})`;
            try {
                const errorData = await updateResponse.json();
                if (errorData.error) {
                    errorMessage = errorData.error;
                }
            } catch (e) {
                errorMessage = `Failed to update rule: ${updateResponse.statusText || updateResponse.status}`;
            }
            throw new Error(errorMessage);
        }

        showSuccessMessage(active ? nrT('adminPanel.notificationRulesPage.activatedSuccess', '...') : nrT('adminPanel.notificationRulesPage.deactivatedSuccess', '...'));
        loadRules();
    } catch (error) {
        console.error('Error toggling rule:', error);
        // Show more specific error message
        let errorMessage = 'Failed to update rule';
        if (error.message) {
            if (error.message.includes('Failed to fetch')) {
                errorMessage = 'Unable to connect to server. Please check your connection.';
            } else {
                errorMessage = error.message;
            }
        }
        showErrorMessage(errorMessage);
        loadRules(); // Reload to reset toggle
    }
}

function escapeHtml(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showSuccessMessage(message) {
    // Remove any existing messages
    const existing = document.querySelector('.success-message, .error-message');
    if (existing) {
        existing.remove();
    }
    
    const messageDiv = document.createElement('div');
    messageDiv.className = 'success-message';
    messageDiv.innerHTML = `
        <i class="fas fa-check-circle"></i>
        <span>${escapeHtml(message)}</span>
    `;
    document.body.appendChild(messageDiv);
    
    // Auto-remove after 3 seconds
    setTimeout(() => {
        messageDiv.style.animation = 'slideIn 0.3s ease-out reverse';
        setTimeout(() => messageDiv.remove(), 300);
    }, 3000);
}

function showErrorMessage(message) {
    // Remove any existing messages
    const existing = document.querySelector('.success-message, .error-message');
    if (existing) {
        existing.remove();
    }
    
    const messageDiv = document.createElement('div');
    messageDiv.className = 'error-message';
    messageDiv.innerHTML = `
        <i class="fas fa-exclamation-circle"></i>
        <span>${escapeHtml(message)}</span>
    `;
    document.body.appendChild(messageDiv);
    
    // Auto-remove after 5 seconds
    setTimeout(() => {
        messageDiv.style.animation = 'slideIn 0.3s ease-out reverse';
        setTimeout(() => messageDiv.remove(), 300);
    }, 5000);
}

function clearFieldErrors() {
    const errorDivs = document.querySelectorAll('.field-error');
    errorDivs.forEach(div => {
        div.style.display = 'none';
        div.textContent = '';
    });
}

function showFieldError(fieldId, message) {
    const errorDiv = document.getElementById(fieldId + 'Error');
    if (errorDiv) {
        errorDiv.textContent = message;
        errorDiv.style.display = 'block';
    }
}

function validateRuleForm() {
    clearFieldErrors();
    let isValid = true;
    const T = nrT;
    // Validate module
    const module = document.getElementById('moduleSelect').value;
    if (!module || module.trim() === '') {
        showFieldError('module', T('adminPanel.notificationRulesPage.moduleRequired', 'Module is required'));
        isValid = false;
    }
    
    // Validate event type
    const eventType = document.getElementById('eventTypeSelect').value;
    if (!eventType || eventType.trim() === '') {
        showFieldError('eventType', T('adminPanel.notificationRulesPage.eventTypeRequired', 'Event Type is required'));
        isValid = false;
    }
    
    // Validate channels
    const channels = [];
    if (document.getElementById('channelUI').checked) channels.push('ui');
    if (document.getElementById('channelEmail').checked) channels.push('email');
    
    if (channels.length === 0) {
        showFieldError('channels', T('adminPanel.notificationRulesPage.channelsRequired', 'At least one channel...'));
        isValid = false;
    }
    
    // Validate recipient user ID if provided
    const recipientUserId = document.getElementById('recipientUserIdInput').value;
    if (recipientUserId && (isNaN(recipientUserId) || parseInt(recipientUserId) < 1)) {
        showFieldError('recipientUserId', T('adminPanel.notificationRulesPage.recipientUserIdInvalid', '...'));
        isValid = false;
    }
    
    return isValid;
}

