/**
 * App Settings functionality for Customize & Configure
 * Handles application settings management and navigation
 */

/**
 * Escapes HTML special characters
 * @param {string} text - Text to escape
 * @returns {string} Escaped text
 */
function escapeHtml(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function appT(key, fallback) {
    return typeof adminT === 'function' ? adminT(key, fallback) : fallback;
}

/**
 * SuperAdmin-only APIs return 403; show a clear message (same i18n as system-settings).
 */
function appSettingsSuperAdminOnlyMessage() {
    const msg = appT('adminPanel.systemSettingsPage.accessDeniedSuperAdminOnly',
        'You do not have access to this section. Only Super Administrators can view and change these settings.');
    const hint = appT('adminPanel.systemSettingsPage.accessDeniedHint',
        'If you need changes here, contact your Super Admin.');
    return msg + ' ' + hint;
}

async function appSettingsReadErrorBody(response) {
    try {
        const text = await response.text();
        try {
            const j = JSON.parse(text);
            if (j && j.error) return String(j.error);
        } catch (_) { /* not JSON */ }
        if (text && text.length > 0 && text.length < 400) return text.trim();
    } catch (_) { /* ignore */ }
    return null;
}

function appSettingsIsForbidden(response, serverMessage) {
    if (response.status === 403 || response.status === 401) return true;
    const m = (serverMessage || '').toLowerCase();
    return m.includes('forbidden') || m.includes('access denied') || m.includes('superadmin') ||
        m.includes('super admin') || m.includes('not authorized') || m.includes('unauthorized');
}

// Configuration constants (id used for admin-only filter; nameKey/moduleKey for i18n)
const APP_SETTINGS_CONFIG = {
    settings: [
        { id: 'quickLinks', name: 'Quick Links', module: 'All', icon: 'fas fa-book', action: 'showQuickLinks', nameKey: 'adminPanel.appSettingsPage.quickLinks', moduleKey: 'adminPanel.appSettingsPage.moduleAll' },
        { id: 'glossaryRollup', name: 'Glossary Rollup Settings', module: 'Glossary', icon: 'fas fa-book', action: 'showGlossaryRollupSettings', nameKey: 'adminPanel.appSettingsPage.glossaryRollupSettings', moduleKey: 'adminPanel.appSettingsPage.moduleGlossary' },
        { id: 'dataQuality', name: 'Data Quality', module: 'Data Quality', icon: 'fas fa-bullseye', action: null, nameKey: 'adminPanel.appSettingsPage.dataQuality', moduleKey: 'adminPanel.appSettingsPage.moduleDataQuality' },
        { id: 'displaySettings', name: 'Display Settings', module: 'Search', icon: 'fas fa-search', action: 'showDisplaySettings', nameKey: 'adminPanel.appSettingsPage.displaySettings', moduleKey: 'adminPanel.appSettingsPage.moduleSearch' },
        { id: 'searchSettings', name: 'Search Settings', module: 'Search', icon: 'fas fa-search', action: 'showSearchSettings', nameKey: 'adminPanel.appSettingsPage.searchSettings', moduleKey: 'adminPanel.appSettingsPage.moduleSearch' },
        { id: 'exportObjects', name: 'Export Objects', module: 'People', icon: 'fas fa-download', action: 'showExportObjects', nameKey: 'adminPanel.appSettingsPage.exportObjects', moduleKey: 'adminPanel.appSettingsPage.modulePeople' },
        { id: 'configureNotifications', name: 'Configure Notifications', module: 'Notifications', icon: 'fas fa-bell', action: 'showConfigureNotificationsSettings', nameKey: 'adminPanel.appSettingsPage.configureNotifications', moduleKey: 'adminPanel.appSettingsPage.moduleNotifications' }
    ]
};

const ADMIN_VISIBLE_SETTING_IDS = ['quickLinks', 'glossaryRollup', 'dataQuality'];

/**
 * Shows the main application settings content
 * @param {HTMLElement} contentArea - The content area element
 */
function showApplicationSettingsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.appSettings');
    addToNavigationHistory('Application Settings', showApplicationSettingsContent);

    const settingsHTML = generateSettingsTable();
    contentArea.innerHTML = `
        <div class="app-settings-content">
            <div class="app-settings-banner">
                <div class="banner-left">
                    <h2>${escapeHtml(appT('adminPanel.appSettingsPage.applicationSettingsTitle', 'APPLICATION SETTINGS'))}</h2>
                </div>
            </div>
            ${settingsHTML}
        </div>
    `;
}

/**
 * Generates the settings table HTML
 * @returns {string} HTML string for the settings table
 */
function generateSettingsTable() {
    const isSuperAdmin = checkIsSuperAdmin(window._adminUserRole);
    const visibleSettings = isSuperAdmin
        ? APP_SETTINGS_CONFIG.settings
        : APP_SETTINGS_CONFIG.settings.filter(s => ADMIN_VISIBLE_SETTING_IDS.includes(s.id));

    const settingsRows = visibleSettings.map(setting => {
        const clickHandler = setting.action ? `onclick="${setting.action}()"` : '';
        const displayName = appT(setting.nameKey, setting.name);
        const displayModule = appT(setting.moduleKey, setting.module);
        return `
            <div class="settings-row" ${clickHandler}>
                <div class="option-cell">
                    <i class="${setting.icon}"></i>
                    <span>${escapeHtml(displayName)}</span>
                </div>
                <div class="module-cell">${escapeHtml(displayModule)}</div>
            </div>
        `;
    }).join('');

    const recordsLabel = appT('adminPanel.appSettingsPage.recordsCount', '{count} records').replace('{count}', String(visibleSettings.length));
    return `
        <div class="app-settings-table">
            <div class="settings-table-header">
                <div class="header-option">${escapeHtml(appT('adminPanel.appSettingsPage.option', 'Option'))}</div>
                <div class="header-module">${escapeHtml(appT('adminPanel.appSettingsPage.moduleHeader', 'Module'))}</div>
            </div>
            <div class="settings-table-body">
                ${settingsRows}
            </div>
            <div class="settings-table-footer">
                <span class="record-count">${escapeHtml(recordsLabel)}</span>
            </div>
        </div>
    `;
}

// ===== QUICK LINKS — STATE =====
// Holds data loaded once and shared across mode views.
const _ql = {
    savedSearches: [],
    assignments: [],
    users: [],
    currentUserId: null,
    isSuperAdmin: false,
};

/**
 * Entry point: loads all Quick Links data then renders the mode-select screen.
 */
function showQuickLinks() {
    const contentArea = document.querySelector('.content-area');
    addToNavigationHistory('Quick Links', showQuickLinks);

    showLoadingState(
        contentArea,
        appT('adminPanel.appSettingsPage.loadingQuickLinks', 'Loading Quick Links'),
        appT('adminPanel.appSettingsPage.fetchingSavedSearches', 'Fetching your saved searches...')
    );

    Promise.all([
        fetchSavedSearches(),
        qlFetchAssignments(),
        qlFetchUsers(),
        getCurrentUser(),
    ])
        .then(([savedSearches, assignments, users, me]) => {
            _ql.savedSearches = savedSearches;
            _ql.assignments = assignments;
            _ql.users = users;
            _ql.currentUserId = me ? me.id : null;
            _ql.isSuperAdmin = me ? checkIsSuperAdmin(me.role) : false;
            qlRenderModeSelect(contentArea);
        })
        .catch(err => {
            console.error('Error loading Quick Links:', err);
            showErrorState(
                contentArea,
                appT('adminPanel.appSettingsPage.unableLoadSavedSearches', 'Unable to Load Saved Searches'),
                appT('adminPanel.appSettingsPage.quickLinks.errorLoadFailed', 'Failed to load Quick Links data. Please try again.'),
                showQuickLinks,
                showApplicationSettingsContent
            );
        });
}

// ── Fetch helpers ──────────────────────────────────────────────────────────────

async function fetchSavedSearches() {
    const response = await fetch('/admin/api/savedsearches');
    if (!response.ok) throw new Error('Network response was not ok');
    const result = await response.json();
    if (result.success && Array.isArray(result.data)) return result.data;
    if (Array.isArray(result)) return result;
    return [];
}

async function qlFetchAssignments() {
    try {
        const r = await fetch('/admin/api/quick-link/list');
        if (!r.ok) return [];
        return await r.json();
    } catch (e) {
        return [];
    }
}

async function qlFetchUsers() {
    try {
        const r = await fetch('/admin/api/users/list');
        if (!r.ok) return [];
        return await r.json();
    } catch (e) {
        return [];
    }
}

// ── Mode-select screen ─────────────────────────────────────────────────────────

function qlRenderModeSelect(contentArea) {
    const T = key => adminT('adminPanel.appSettingsPage.quickLinks.' + key, key);
    const isSA = _ql.isSuperAdmin;

    const globalAssignment = _ql.assignments.find(a => a.targetUserId == null);
    const personalAssignment = _ql.assignments.find(a => a.targetUserId === _ql.currentUserId);

    const globalBadge = globalAssignment
        ? `<span class="ql-current-badge">${escapeHtml(globalAssignment.searchName || '')}</span>`
        : `<span class="ql-none-badge">${T('noQuickLinkSet')}</span>`;

    const personalBadge = personalAssignment
        ? `<span class="ql-current-badge">${escapeHtml(personalAssignment.searchName || '')}</span>`
        : `<span class="ql-none-badge">${T('noQuickLinkSet')}</span>`;

    const globalDisabledAttr = isSA ? '' : 'disabled';
    const globalDisabledNote = isSA ? '' : `<span class="ql-disabled-note">${T('modeSuperAdminOnly')}</span>`;

    contentArea.innerHTML = `
        <div class="quick-links-container">
            <div class="quick-links-banner-modern">
                <div class="banner-icon"><i class="fas fa-link"></i></div>
                <div class="banner-content">
                    <h2>${escapeHtml(adminT('adminPanel.appSettingsPage.quickLinksConfigTitle', 'Quick Links Configuration'))}</h2>
                    <p>${escapeHtml(adminT('adminPanel.appSettingsPage.quickLinksConfigSubtitle', 'Select a saved search to display as a quick link'))}</p>
                </div>
                <div class="banner-actions">
                    <button class="btn-secondary" onclick="showQuickLinks()">
                        <i class="fas fa-sync-alt"></i>
                        ${escapeHtml(adminT('adminPanel.appSettingsPage.refresh', 'Refresh'))}
                    </button>
                </div>
            </div>

            <div class="ql-mode-grid">
                <!-- Mode 1: My Link -->
                <button class="ql-mode-card" onclick="qlEnterMode('personal')">
                    <div class="ql-mode-icon"><i class="fas fa-user-circle"></i></div>
                    <div class="ql-mode-body">
                        <h3>${T('modeMyLinkTitle')}</h3>
                        <p>${T('modeMyLinkDesc')}</p>
                        <div class="ql-mode-current">${personalBadge}</div>
                    </div>
                    <i class="fas fa-chevron-right ql-mode-arrow"></i>
                </button>

                <!-- Mode 2: Global (Super Admin only) -->
                <button class="ql-mode-card${isSA ? '' : ' ql-mode-card--disabled'}" ${globalDisabledAttr} onclick="${isSA ? "qlEnterMode('global')" : ''}">
                    <div class="ql-mode-icon"><i class="fas fa-globe"></i></div>
                    <div class="ql-mode-body">
                        <h3>${T('modeGlobalTitle')}</h3>
                        <p>${T('modeGlobalDesc')}</p>
                        ${globalDisabledNote}
                        <div class="ql-mode-current">${globalBadge}</div>
                    </div>
                    <i class="fas fa-chevron-right ql-mode-arrow"></i>
                </button>

                <!-- Mode 3: Assign to user -->
                <button class="ql-mode-card" onclick="qlEnterMode('assign')">
                    <div class="ql-mode-icon"><i class="fas fa-user-tag"></i></div>
                    <div class="ql-mode-body">
                        <h3>${T('modeAssignTitle')}</h3>
                        <p>${T('modeAssignDesc')}</p>
                    </div>
                    <i class="fas fa-chevron-right ql-mode-arrow"></i>
                </button>
            </div>
        </div>
    `;
}

// ── Mode screens ───────────────────────────────────────────────────────────────

function qlEnterMode(mode) {
    const contentArea = document.querySelector('.content-area');
    const T = key => adminT('adminPanel.appSettingsPage.quickLinks.' + key, key);
    const isSA = _ql.isSuperAdmin;

    if (mode === 'global' && !isSA) return;

    const titleMap = {
        personal: T('modeMyLinkTitle'),
        global: T('modeGlobalTitle'),
        assign: T('modeAssignTitle'),
    };

    const currentAssignment = mode === 'global'
        ? _ql.assignments.find(a => a.targetUserId == null)
        : mode === 'personal'
            ? _ql.assignments.find(a => a.targetUserId === _ql.currentUserId)
            : null;

    const currentBadge = currentAssignment
        ? `<div class="ql-current-info">
               <i class="fas fa-check-circle"></i>
               <span>${escapeHtml(currentAssignment.searchName || '')}</span>
               <button class="ql-remove-btn" onclick="qlConfirmRemove(${currentAssignment.targetUserId == null ? 'null' : currentAssignment.targetUserId})">
                   <i class="fas fa-trash-alt"></i> ${T('removeLink')}
               </button>
           </div>`
        : `<div class="ql-none-info"><i class="fas fa-info-circle"></i> ${T('noQuickLinkSet')}</div>`;

    const searchSelectHtml = qlBuildSearchSelect('ql-search-select', T('selectSearch'));

    let userSelectHtml = '';
    if (mode === 'assign') {
        userSelectHtml = `
            <div class="form-group enhanced">
                <label class="form-label-enhanced"><i class="fas fa-user"></i> ${T('modeAssignTitle')}</label>
                <div class="select-container-modern">
                    <select id="ql-user-select" class="select-modern">
                        <option value="">${T('selectUser')}</option>
                        ${_ql.users.map(u => `<option value="${u.id}">${escapeHtml(u.firstName + ' ' + u.lastName)} (${escapeHtml(u.role || '')})</option>`).join('')}
                    </select>
                    <div class="select-arrow"><i class="fas fa-chevron-down"></i></div>
                </div>
            </div>`;
    }

    let assignmentsHtml = '';
    if (mode === 'assign') {
        const userAssignments = _ql.assignments.filter(
            a => a.targetUserId != null && a.targetUserId > 0
        );
        assignmentsHtml = `
            <div class="ql-assignments-section">
                <h4>${T('existingAssignments')}</h4>
                ${userAssignments.length === 0
                    ? `<p class="ql-no-assignments"><i class="fas fa-inbox" style="font-size:1.5rem;opacity:.4;"></i>${T('noAssignments')}</p>`
                    : userAssignments.map(a => {
                        const initials = (a.targetUserName || '?').split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();
                        return `
                        <div class="ql-assignment-row">
                            <div class="ql-assignment-avatar">${escapeHtml(initials)}</div>
                            <div class="ql-assignment-info">
                                <span class="ql-assignment-user">${escapeHtml(a.targetUserName || '')}</span>
                                <span class="ql-assignment-email">${escapeHtml(a.targetUserEmail || '')}</span>
                                <span class="ql-assignment-search">${escapeHtml(a.searchName || '')}</span>
                            </div>
                            <button class="ql-remove-btn" onclick="qlConfirmRemove(${a.targetUserId})">
                                <i class="fas fa-trash-alt"></i> ${T('removeLink')}
                            </button>
                        </div>`;
                    }).join('')}
            </div>`;
    }

    contentArea.innerHTML = `
        <div class="quick-links-container">
            <div class="quick-links-banner-modern">
                <div class="banner-icon"><i class="fas fa-link"></i></div>
                <div class="banner-content">
                    <h2>${escapeHtml(titleMap[mode])}</h2>
                </div>
                <div class="banner-actions">
                    <button class="btn-secondary" onclick="qlRenderModeSelect(document.querySelector('.content-area'))">
                        <i class="fas fa-arrow-left"></i> ${T('back')}
                    </button>
                </div>
            </div>

            <div class="quick-links-main-card">
                <div class="card-content">
                    ${mode !== 'assign' ? `<div class="ql-current-section">${currentBadge}</div>` : ''}

                    ${userSelectHtml}

                    <div class="form-group enhanced">
                        <label class="form-label-enhanced"><i class="fas fa-search"></i> ${escapeHtml(adminT('adminPanel.appSettingsPage.savedSearches', 'Saved Searches'))}</label>
                        <div class="select-container-modern">
                            ${searchSelectHtml}
                            <div class="select-arrow"><i class="fas fa-chevron-down"></i></div>
                        </div>
                    </div>

                    <div class="ql-form-actions">
                        <button class="btn-primary" onclick="qlHandleSave('${mode}')">
                            <i class="fas fa-save"></i> ${T('saveLink')}
                        </button>
                    </div>

                    ${assignmentsHtml}
                </div>
            </div>
        </div>
    `;
}

function qlBuildSearchSelect(id, placeholder) {
    const T = key => adminT('adminPanel.appSettingsPage.quickLinks.' + key, key);
    const opts = _ql.savedSearches.map(s => {
        const shareType = s.share_type || 'private';
        const badgeKey = shareType === 'public' ? 'badgePublic' : shareType === 'selected_users' ? 'badgeSelectedUsers' : 'badgePrivate';
        return `<option value="${s.id}"
                    data-share-type="${escapeHtml(shareType)}"
                    data-name="${escapeHtml(s.name || '')}"
                    data-description="${escapeHtml(s.description || '')}">
                    ${escapeHtml(s.name || adminT('adminPanel.appSettingsPage.unnamedSearch', 'Unnamed Search'))} [${T(badgeKey)}]
                </option>`;
    }).join('');
    return `<select id="${id}" class="select-modern">
                <option value="">${placeholder}</option>
                ${opts}
            </select>`;
}

// ── Save handler ───────────────────────────────────────────────────────────────

async function qlHandleSave(mode) {
    const T = key => adminT('adminPanel.appSettingsPage.quickLinks.' + key, key);

    const searchSelect = document.getElementById('ql-search-select');
    if (!searchSelect || !searchSelect.value) return;

    const searchId = parseInt(searchSelect.value, 10);
    const selectedOption = searchSelect.options[searchSelect.selectedIndex];
    const searchName = selectedOption.dataset.name || selectedOption.text;
    const description = selectedOption.dataset.description || '';
    const shareType = selectedOption.dataset.shareType || 'private';

    let targetUserId = null;
    if (mode === 'personal') {
        targetUserId = _ql.currentUserId;
    } else if (mode === 'assign') {
        const userSelect = document.getElementById('ql-user-select');
        if (!userSelect || !userSelect.value) return;
        targetUserId = Number.parseInt(userSelect.value, 10);
        if (!Number.isFinite(targetUserId)) {
            showNotification(T('errorSaveFailed') + ' Invalid user selection.', 'error');
            return;
        }
    }

    const isSA = _ql.isSuperAdmin;
    const needsPublic = (mode === 'global') && (shareType === 'selected_users' || shareType === 'private');
    const needsShareWithUser = !isSA && mode === 'assign' && (shareType === 'selected_users' || shareType === 'private');

    if (needsPublic) {
        const bodyKey = shareType === 'selected_users' ? 'modalSelectedUsersBody' : 'modalPrivateBody';
        qlShowConfirmModal({
            title: T('modalNotPublicTitle'),
            body: T(bodyKey),
            confirmLabel: T('modalMakePublicSave'),
            onConfirm: async () => {
                const ok = await qlMakeSearchPublic(searchId);
                if (!ok) {
                    showNotification(T('errorMakePublicFailed'), 'error');
                    return;
                }
                await qlDoSave(targetUserId, searchId, searchName, description, mode);
            },
        });
        return;
    }

    if (needsShareWithUser) {
        const bodyKey = shareType === 'selected_users' ? 'modalAddToSharedBody' : 'modalShareWithUserBody';
        qlShowConfirmModal({
            title: T('modalNotPublicTitle'),
            body: T(bodyKey),
            confirmLabel: T('modalShareWithUserSave'),
            onConfirm: async () => {
                const ok = await qlShareSearchWithUser(searchId, targetUserId);
                if (!ok) {
                    showNotification(T('errorShareWithUserFailed'), 'error');
                    return;
                }
                await qlDoSave(targetUserId, searchId, searchName, description, mode);
            },
        });
        return;
    }

    await qlDoSave(targetUserId, searchId, searchName, description, mode);
}

async function qlDoSave(targetUserId, searchId, searchName, description, mode) {
    const T = key => adminT('adminPanel.appSettingsPage.quickLinks.' + key, key);
    try {
        const r = await fetch('/admin/api/quick-link/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ targetUserId: targetUserId === null ? null : targetUserId, searchId, searchName, description }),
        });
        if (!r.ok) {
            const body = await r.json().catch(() => ({}));
            if (r.status === 403) {
                showNotification(body.error || appSettingsSuperAdminOnlyMessage(), 'warning');
                return;
            }
            throw new Error(body.error || T('errorSaveFailed'));
        }
        const successKey = mode === 'personal' ? 'personalSaveSuccess' : mode === 'global' ? 'globalSaveSuccess' : 'assignSuccess';
        showNotification(T(successKey), 'success');
        // Reload assignments in background and re-render current mode
        _ql.assignments = await qlFetchAssignments();
        qlEnterMode(mode);
    } catch (e) {
        showNotification(T('errorSaveFailed') + ' ' + (e.message || ''), 'error');
    }
}

async function qlMakeSearchPublic(searchId) {
    try {
        const r = await fetch(`/api/search/${searchId}/share`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ isPublic: true }),
        });
        return r.ok;
    } catch (e) {
        return false;
    }
}

async function qlShareSearchWithUser(searchId, userId) {
    try {
        const r = await fetch(`/api/search/${searchId}/share-with-user`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId }),
        });
        return r.ok;
    } catch (e) {
        return false;
    }
}

// ── Remove handler ─────────────────────────────────────────────────────────────

function qlConfirmRemove(targetUserId) {
    const T = key => adminT('adminPanel.appSettingsPage.quickLinks.' + key, key);
    qlShowConfirmModal({
        title: T('modalRemoveTitle'),
        body: T('modalRemoveBody'),
        confirmLabel: T('modalRemoveConfirm'),
        confirmClass: 'btn-danger',
        onConfirm: async () => {
            const payload = {
                targetUserId: targetUserId === 'null' || targetUserId === null || targetUserId === undefined
                    ? null
                    : parseInt(targetUserId, 10),
            };
            if (payload.targetUserId !== null && (!Number.isFinite(payload.targetUserId) || payload.targetUserId <= 0)) {
                console.error('[QuickLink remove] invalid targetUserId (use null only for global):', targetUserId, 'payload:', payload);
                showNotification(T('errorRemoveFailed'), 'error');
                return;
            }
            try {
                const r = await fetch('/admin/api/quick-link/remove', {
                    method: 'DELETE',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload),
                });
                const text = await r.text().catch(() => '');
                if (!r.ok) {
                    console.error('[QuickLink remove] failed', {
                        status: r.status,
                        statusText: r.statusText,
                        body: text,
                        payload,
                    });
                    showNotification(T('errorRemoveFailed'), 'error');
                    return;
                }
                showNotification(T('removeSuccess'), 'success');
                _ql.assignments = await qlFetchAssignments();
                qlRenderModeSelect(document.querySelector('.content-area'));
            } catch (e) {
                console.error('[QuickLink remove] network or parse error', { payload, err: e });
                showNotification(T('errorRemoveFailed'), 'error');
            }
        },
    });
}

// ── Confirmation modal ─────────────────────────────────────────────────────────

function qlShowConfirmModal({ title, body, confirmLabel, confirmClass = 'btn-primary', onConfirm }) {
    const existing = document.getElementById('ql-confirm-modal');
    if (existing) existing.remove();

    const modal = document.createElement('div');
    modal.id = 'ql-confirm-modal';
    modal.style.cssText = 'position:fixed;inset:0;z-index:9999;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,0.55);backdrop-filter:blur(2px);';
    modal.innerHTML = `
        <div class="ql-modal-box">
            <div class="ql-modal-icon ${confirmClass === 'btn-danger' ? 'ql-modal-icon--danger' : 'ql-modal-icon--info'}">
                <i class="fas ${confirmClass === 'btn-danger' ? 'fa-trash-alt' : 'fa-info-circle'}"></i>
            </div>
            <h3 class="ql-modal-title">${escapeHtml(title)}</h3>
            <p class="ql-modal-body">${escapeHtml(body)}</p>
            <div class="ql-modal-actions">
                <button id="ql-modal-cancel" class="btn-secondary">
                    ${escapeHtml(adminT('adminPanel.appSettingsPage.quickLinks.modalCancel', 'Cancel'))}
                </button>
                <button id="ql-modal-confirm" class="${confirmClass}">
                    ${escapeHtml(confirmLabel)}
                </button>
            </div>
        </div>
    `;

    document.body.appendChild(modal);

    modal.querySelector('#ql-modal-cancel').addEventListener('click', () => modal.remove());
    modal.querySelector('#ql-modal-confirm').addEventListener('click', () => {
        modal.remove();
        onConfirm();
    });
    modal.addEventListener('click', e => { if (e.target === modal) modal.remove(); });
}

/**
 * Legacy: kept for backward compatibility with refreshQuickLinks() calls.
 */
function refreshQuickLinks() {
    showQuickLinks();
}

/**
 * @deprecated Kept so any external references don't break.
 */
function generateQuickLinksInterface(data) {
    return '';
}

/**
 * @deprecated Kept so any external references don't break.
 */
function initializeQuickLinksInterface() {}

/**
 * @deprecated Kept so any external references don't break.
 */
async function fetchCurrentQuickLink() {
    return null;
}

// ===== UTILITY FUNCTIONS =====

/**
 * Shows a loading state with spinner
 * @param {HTMLElement} contentArea - Content area element
 * @param {string} title - Loading title
 * @param {string} message - Loading message
 */
function showLoadingState(contentArea, title, message) {
    const safeTitle = escapeHtml(title);
    const safeMessage = escapeHtml(message);
    contentArea.innerHTML = `
        <div class="quick-links-loading">
            <div class="loading-animation">
                <div class="loading-spinner-modern">
                    <div class="spinner-ring"></div>
                    <div class="spinner-ring"></div>
                    <div class="spinner-ring"></div>
                </div>
            </div>
            <div class="loading-text">
                <h3>${title}</h3>
                <p>${message}</p>
            </div>
        </div>
    `;
}

/**
 * Shows an error state with retry options
 * @param {HTMLElement} contentArea - Content area element
 * @param {string} title - Error title
 * @param {string} message - Error message
 * @param {Function} retryAction - Function to call on retry
 * @param {Function} backAction - Function to call on back
 */
function showErrorState(contentArea, title, message, retryAction, backAction) {
    const safeTitle = escapeHtml(title);
    const safeMessage = escapeHtml(message);
    const tryAgain = escapeHtml(appT('adminPanel.appSettingsPage.tryAgain', 'Try Again'));
    const backToSettings = escapeHtml(appT('adminPanel.appSettingsPage.backToSettings', 'Back to Settings'));
    contentArea.innerHTML = `
        <div class="quick-links-error">
            <div class="error-icon">
                <i class="fas fa-exclamation-triangle"></i>
            </div>
            <div class="error-content">
                <h3>${safeTitle}</h3>
                <p>${safeMessage}</p>
                <div class="error-actions">
                    <button class="btn-primary" onclick="${retryAction.name}()">
                        <i class="fas fa-redo"></i>
                        ${tryAgain}
                    </button>
                    <button class="btn-outline" onclick="${backAction.name}(document.querySelector('.content-area'))">
                        <i class="fas fa-arrow-left"></i>
                        ${backToSettings}
                    </button>
                </div>
            </div>
        </div>
    `;
}

// ===== NOTIFICATION SYSTEM =====
// showNotification: unified implementation in admin-notifications.js (loaded first)

// ===== DISPLAY SETTINGS FUNCTIONALITY =====

/**
 * Shows the Display Settings interface
 */
function showDisplaySettings() {
    const contentArea = document.querySelector('.content-area');
    addToNavigationHistory('Display Settings', showDisplaySettings);

    showLoadingState(contentArea, appT('adminPanel.appSettingsPage.loadingDisplaySettings', 'Loading Display Settings'), appT('adminPanel.appSettingsPage.fetchingFacetConfig', 'Fetching facet configuration...'));

    fetchDisplaySettings()
        .then(data => {
            storeOriginalPositions(data);
            contentArea.innerHTML = generateDisplaySettingsInterface(data);
            setTimeout(initializeDisplaySettingsInterface, 100);
        })
        .catch(error => {
            console.error('Error fetching display settings:', error);
            showErrorState(contentArea, appT('adminPanel.appSettingsPage.unableLoadDisplaySettings', 'Unable to Load Display Settings'),
                appT('adminPanel.appSettingsPage.connectionError', 'There was an error connecting...'),
                showDisplaySettings, showApplicationSettingsContent);
        });
}

/**
 * Fetches display settings from the API
 * @returns {Promise<Object>} Promise resolving to display settings data
 */
async function fetchDisplaySettings() {
    const response = await fetch('/admin/api/display-settings');
    if (!response.ok) {
        throw new Error('Network response was not ok');
    }
    return response.json();
}

/**
 * Stores original positions for facets
 * @param {Object} data - Display settings data
 */
function storeOriginalPositions(data) {
    originalPositions.clear();
    data.allFacets.forEach((facet, index) => {
        originalPositions.set(facet.id, { table: 'all', position: index });
    });
    data.activeFacets.forEach((facet, index) => {
        originalPositions.set(facet.id, { table: 'active', position: index });
    });
}

/**
 * Generates the Display Settings interface HTML
 * @param {Object} data - Display settings data
 * @returns {string} HTML string for the interface
 */
function generateDisplaySettingsInterface(data) {
    const T = appT;
    const allFacetsHTML = data.allFacets.map(facet => `
        <div class="facet-row" data-facet-id="${facet.id}" onclick="selectFacet(this, 'all')">
            <div class="facet-name">${facet.name}</div>
            <div class="facet-category">${facet.category}</div>
        </div>
    `).join('');

    const activeFacetsHTML = data.activeFacets.map(facet => `
        <div class="facet-row" data-facet-id="${facet.id}" onclick="selectFacet(this, 'active')">
            <div class="facet-name">${facet.name}</div>
            <div class="facet-category">${facet.category}</div>
        </div>
    `).join('');

    return `
        <div class="display-settings-container">
            <div class="display-settings-header">
                <div class="header-left">
                    <div class="header-title">
                        <h2>${escapeHtml(T('adminPanel.appSettingsPage.displaySettingsTitle', 'Display Settings'))}</h2>
                        <p>${escapeHtml(T('adminPanel.appSettingsPage.budgManagement', 'BUDG Management'))}</p>
                    </div>
                </div>
                <div class="header-actions">
                    <button class="btn-save" onclick="saveDisplaySettings()">
                        <i class="fas fa-save"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.save', 'Save'))}
                    </button>
                    <button class="btn-save-close" onclick="saveAndCloseDisplaySettings()">
                        <i class="fas fa-save"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.saveAndClose', 'Save & Close'))}
                    </button>
                    <button class="btn-close" onclick="closeDisplaySettings()">
                        <i class="fas fa-times"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.close', 'Close'))}
                    </button>
                </div>
            </div>
            
            <div class="display-settings-banner">
                <p>${escapeHtml(T('adminPanel.appSettingsPage.displaySettingsBanner', 'Choose the default facets...'))}</p>
            </div>
            
            <div class="facet-management-area">
                <div class="facets-table-container">
                    <div class="table-header">
                        <h3>${escapeHtml(T('adminPanel.appSettingsPage.allFacets', 'All Facets'))}</h3>
                        <span class="facet-count" id="allFacetsCount">${data.allFacets.length}</span>
                    </div>
                    <div class="facets-table" id="allFacetsTable">
                        ${allFacetsHTML}
                    </div>
                </div>
                
                <div class="facet-controls">
                    <button class="control-btn" onclick="moveSelectedToActive()" title="${escapeHtml(T('adminPanel.appSettingsPage.moveToActive', 'Move to Active'))}">
                        <i class="fas fa-arrow-right"></i>
                    </button>
                    <button class="control-btn" onclick="moveSelectedToAll()" title="${escapeHtml(T('adminPanel.appSettingsPage.moveToAll', 'Move to All'))}">
                        <i class="fas fa-arrow-left"></i>
                    </button>
                    <button class="control-btn" onclick="moveAllToActive()" title="${escapeHtml(T('adminPanel.appSettingsPage.moveAllToActive', 'Move All to Active'))}">
                        <i class="fas fa-angle-double-right"></i>
                    </button>
                    <button class="control-btn" onclick="moveAllToAll()" title="${escapeHtml(T('adminPanel.appSettingsPage.moveAllToAll', 'Move All to All'))}">
                        <i class="fas fa-angle-double-left"></i>
                    </button>
                </div>
                
                <div class="facets-table-container">
                    <div class="table-header">
                        <h3>${escapeHtml(T('adminPanel.appSettingsPage.activeFacets', 'Active Facets'))}</h3>
                        <span class="facet-count" id="activeFacetsCount">${data.activeFacets.length}</span>
                    </div>
                    <div class="facets-table" id="activeFacetsTable">
                        ${activeFacetsHTML}
                    </div>
                    <div class="active-facet-controls">
                        <button class="control-btn" onclick="moveActiveUp()" title="${escapeHtml(T('adminPanel.appSettingsPage.moveUp', 'Move Up'))}">
                            <i class="fas fa-arrow-up"></i>
                        </button>
                        <button class="control-btn" onclick="moveActiveDown()" title="${escapeHtml(T('adminPanel.appSettingsPage.moveDown', 'Move Down'))}">
                            <i class="fas fa-arrow-down"></i>
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
}

// ===== DISPLAY SETTINGS STATE MANAGEMENT =====

// Global state variables
let selectedFacet = null;
let selectedTable = null;
let originalPositions = new Map();

/**
 * Initializes the Display Settings interface
 */
function initializeDisplaySettingsInterface() {
    console.log('Display Settings interface initialized');
}

// ===== FACET MANAGEMENT FUNCTIONS =====

/**
 * Selects a facet row
 * @param {HTMLElement} element - The facet row element
 * @param {string} table - The table type ('all' or 'active')
 */
function selectFacet(element, table) {
    document.querySelectorAll('.facet-row.selected').forEach(row => {
        row.classList.remove('selected');
    });

    element.classList.add('selected');
    selectedFacet = element;
    selectedTable = table;
}

/**
 * Moves selected facet from All to Active table
 */
function moveSelectedToActive() {
    if (!selectedFacet || selectedTable !== 'all') {
        showNotification(appT('adminPanel.appSettingsPage.selectFacetFromAll', 'Please select a facet from the All Facets table'), 'warning');
        return;
    }

    const facetData = extractFacetData(selectedFacet);
    selectedFacet.remove();
    updateFacetCount('allFacetsCount');

    const activeTable = document.getElementById('activeFacetsTable');
    const newRow = createFacetRow(facetData, 'active');
    activeTable.appendChild(newRow);
    updateFacetCount('activeFacetsCount');

    clearSelection();
}

/**
 * Moves selected facet from Active to All table
 */
function moveSelectedToAll() {
    if (!selectedFacet || selectedTable !== 'active') {
        showNotification(appT('adminPanel.appSettingsPage.selectFacetFromActive', 'Please select a facet from the Active Facets table'), 'warning');
        return;
    }

    const facetData = extractFacetData(selectedFacet);
    selectedFacet.remove();
    updateFacetCount('activeFacetsCount');

    const allTable = document.getElementById('allFacetsTable');
    const newRow = createFacetRow(facetData, 'all');

    // Insert at original position
    const originalPos = originalPositions.get(facetData.id);
    if (originalPos && originalPos.table === 'all') {
        const allRows = Array.from(allTable.children);
        if (originalPos.position < allRows.length) {
            allTable.insertBefore(newRow, allRows[originalPos.position]);
        } else {
            allTable.appendChild(newRow);
        }
    } else {
        allTable.appendChild(newRow);
    }

    updateFacetCount('allFacetsCount');
    clearSelection();
}

/**
 * Extracts facet data from a facet row element
 * @param {HTMLElement} element - The facet row element
 * @returns {Object} Facet data object
 */
function extractFacetData(element) {
    return {
        id: element.dataset.facetId,
        name: element.querySelector('.facet-name').textContent,
        category: element.querySelector('.facet-category').textContent
    };
}

/**
 * Creates a new facet row element
 * @param {Object} facetData - Facet data object
 * @param {string} table - Target table type
 * @returns {HTMLElement} New facet row element
 */
function createFacetRow(facetData, table) {
    const newRow = document.createElement('div');
    newRow.className = 'facet-row';
    newRow.dataset.facetId = facetData.id;
    newRow.onclick = () => selectFacet(newRow, table);
    newRow.innerHTML = `
        <div class="facet-name">${facetData.name}</div>
        <div class="facet-category">${facetData.category}</div>
    `;
    return newRow;
}

/**
 * Clears the current facet selection
 */
function clearSelection() {
    selectedFacet = null;
    selectedTable = null;
}

/**
 * Moves all facets from All to Active table
 */
function moveAllToActive() {
    const allTable = document.getElementById('allFacetsTable');
    const activeTable = document.getElementById('activeFacetsTable');
    const allRows = Array.from(allTable.querySelectorAll('.facet-row'));

    allRows.forEach(row => {
        const facetData = extractFacetData(row);
        const newRow = createFacetRow(facetData, 'active');
        activeTable.appendChild(newRow);
    });

    allTable.innerHTML = '';
    updateFacetCount('allFacetsCount');
    updateFacetCount('activeFacetsCount');
}

/**
 * Moves all facets from Active to All table
 */
function moveAllToAll() {
    const allTable = document.getElementById('allFacetsTable');
    const activeTable = document.getElementById('activeFacetsTable');
    const activeRows = Array.from(activeTable.querySelectorAll('.facet-row'));

    // Sort by original position
    activeRows.sort((a, b) => {
        const posA = originalPositions.get(a.dataset.facetId);
        const posB = originalPositions.get(b.dataset.facetId);
        if (posA && posB && posA.table === 'all' && posB.table === 'all') {
            return posA.position - posB.position;
        }
        return 0;
    });

    activeRows.forEach(row => {
        const facetData = extractFacetData(row);
        const newRow = createFacetRow(facetData, 'all');
        allTable.appendChild(newRow);
    });

    activeTable.innerHTML = '';
    updateFacetCount('allFacetsCount');
    updateFacetCount('activeFacetsCount');
}

/**
 * Moves selected active facet up
 */
function moveActiveUp() {
    if (!selectedFacet || selectedTable !== 'active') {
        showNotification(appT('adminPanel.appSettingsPage.selectFacetFromActive', 'Please select a facet from the Active Facets table'), 'warning');
        return;
    }

    const previousSibling = selectedFacet.previousElementSibling;
    if (previousSibling) {
        selectedFacet.parentNode.insertBefore(selectedFacet, previousSibling);
    }
}

/**
 * Moves selected active facet down
 */
function moveActiveDown() {
    if (!selectedFacet || selectedTable !== 'active') {
        showNotification(appT('adminPanel.appSettingsPage.selectFacetFromActive', 'Please select a facet from the Active Facets table'), 'warning');
        return;
    }

    const nextSibling = selectedFacet.nextElementSibling;
    if (nextSibling) {
        selectedFacet.parentNode.insertBefore(nextSibling, selectedFacet);
    }
}

/**
 * Updates the facet count display
 * @param {string} countElementId - ID of the count element to update
 */
function updateFacetCount(countElementId) {
    const element = document.getElementById(countElementId);
    if (element) {
        const tableId = countElementId === 'allFacetsCount' ? 'allFacetsTable' : 'activeFacetsTable';
        const count = document.getElementById(tableId).children.length;
        element.textContent = count;
    }
}

// ===== SAVE FUNCTIONS =====

/**
 * Saves the current display settings
 */
async function saveDisplaySettings() {
    try {
        const facets = collectFacetData();
        console.log('Saving facets with order:', facets);

        const response = await fetch('/admin/api/display-settings', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ facets })
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        const data = await response.json();
        console.log('Save response:', data);
        showNotification(appT('adminPanel.appSettingsPage.displaySettingsSaved', 'Display settings saved successfully'), 'success');
    } catch (error) {
        console.error('Error saving display settings:', error);
        showNotification(appT('adminPanel.appSettingsPage.displaySettingsSaveError', 'Error saving display settings: ') + error.message, 'error');
    }
}

/**
 * Collects facet data from both tables
 * @returns {Array} Array of facet objects with visibility and order
 */
function collectFacetData() {
    const allFacets = Array.from(document.getElementById('allFacetsTable').children).map((row, index) => ({
        id: row.dataset.facetId,
        visibility: false,
        order: index
    }));

    const activeFacets = Array.from(document.getElementById('activeFacetsTable').children).map((row, index) => ({
        id: row.dataset.facetId,
        visibility: true,
        order: index
    }));

    return [...activeFacets, ...allFacets];
}

/**
 * Saves display settings and closes the interface
 */
function saveAndCloseDisplaySettings() {
    saveDisplaySettings();
    // The save function will show success notification
    // User stays on the same page as requested
}

/**
 * Closes the display settings interface
 */
function closeDisplaySettings() {
    showApplicationSettingsContent(document.querySelector('.content-area'));
}

// ===== SEARCH SETTINGS FUNCTIONALITY =====

/**
 * Shows the Search Settings interface
 */
function showSearchSettings() {
    const contentArea = document.querySelector('.content-area');
    addToNavigationHistory('Search Settings', showSearchSettings);

    showLoadingState(contentArea, appT('adminPanel.appSettingsPage.loadingSearchSettings', 'Loading Search Settings'), appT('adminPanel.appSettingsPage.fetchingSearchConfig', 'Fetching search configuration...'));

    fetchSearchSettings()
        .then(data => {
            contentArea.innerHTML = generateSearchSettingsInterface(data);
            setTimeout(initializeSearchSettingsInterface, 100);
        })
        .catch(error => {
            console.error('Error fetching search settings:', error);
            showErrorState(contentArea, appT('adminPanel.appSettingsPage.unableLoadSearchSettings', 'Unable to Load Search Settings'),
                appT('adminPanel.appSettingsPage.connectionError', 'There was an error connecting...'),
                showSearchSettings, showApplicationSettingsContent);
        });
}

/**
 * Fetches search settings from app_config table
 * @returns {Promise<Object>} Promise resolving to search settings
 */
async function fetchSearchSettings() {
    const [fuzzyResponse, hideNonPublicResponse] = await Promise.all([
        fetch('/admin/api/app-config/UNISON_FUZZY_DEFAULT'),
        fetch('/admin/api/app-config/HIDE_NON_PUBLIC_OBJECTS')
    ]);

    if (!fuzzyResponse.ok || !hideNonPublicResponse.ok) {
        throw new Error('Network response was not ok');
    }

    const [fuzzyData, hideNonPublicData] = await Promise.all([
        fuzzyResponse.json(),
        hideNonPublicResponse.json()
    ]);

    return {
        fuzzySearch: fuzzyData,
        hideNonPublic: hideNonPublicData
    };
}

/**
 * Generates the Search Settings interface HTML
 * @param {Object} data - Search settings data
 * @returns {string} HTML string for the interface
 */
function generateSearchSettingsInterface(data) {
    const T = appT;
    const isFuzzyEnabled = data.fuzzySearch && data.fuzzySearch.definition === 'true';
    const isHideNonPublicEnabled = data.hideNonPublic && data.hideNonPublic.definition === 'true';

    return `
        <div class="search-settings-container">
            <div class="search-settings-header">
                <div class="header-left">
                    <div class="header-title">
                        <h2>${escapeHtml(T('adminPanel.appSettingsPage.searchSettingsTitle', 'Search Settings'))}</h2>
                        <p>${escapeHtml(T('adminPanel.appSettingsPage.budgManagement', 'BUDG Management'))}</p>
                    </div>
                </div>
                <div class="header-actions">
                    <button class="btn-save" onclick="saveSearchSettings()">
                        <i class="fas fa-save"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.save', 'Save'))}
                    </button>
                    <button class="btn-save-close" onclick="saveAndCloseSearchSettings()">
                        <i class="fas fa-save"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.saveAndClose', 'Save & Close'))}
                    </button>
                    <button class="btn-close" onclick="closeSearchSettings()">
                        <i class="fas fa-times"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.close', 'Close'))}
                    </button>
                </div>
            </div>
            
            <div class="search-settings-banner">
                <p>${escapeHtml(T('adminPanel.appSettingsPage.searchOptionsIntro', 'Choose search options:'))}</p>
            </div>
            
            <div class="search-options-container">
                <div class="search-option">
                    <div class="option-checkbox">
                        <input type="checkbox" id="fuzzySearch" ${isFuzzyEnabled ? 'checked' : ''}>
                        <label for="fuzzySearch"></label>
                    </div>
                    <div class="option-content">
                        <div class="option-title">${escapeHtml(T('adminPanel.appSettingsPage.enableFuzzySearch', 'Enable fuzzy search'))}</div>
                        <div class="option-description">${escapeHtml(T('adminPanel.appSettingsPage.enableFuzzySearchDesc', 'Finds matching objects...'))}</div>
                    </div>
                </div>
                
                <div class="search-option">
                    <div class="option-checkbox">
                        <input type="checkbox" id="hideNonPublic" ${isHideNonPublicEnabled ? 'checked' : ''}>
                        <label for="hideNonPublic"></label>
                    </div>
                    <div class="option-content">
                        <div class="option-title">${escapeHtml(T('adminPanel.appSettingsPage.hideNonPublicObjects', 'Hide non-public objects'))}</div>
                        <div class="option-description">${escapeHtml(T('adminPanel.appSettingsPage.hideNonPublicObjectsDesc', 'Hide objects...'))}</div>
                    </div>
                </div>
            </div>
        </div>
    `;
}

/**
 * Initializes the Search Settings interface
 */
function initializeSearchSettingsInterface() {
    console.log('Search Settings interface initialized');
}

/**
 * Saves the current search settings
 */
async function saveSearchSettings() {
    try {
        const fuzzySearch = document.getElementById('fuzzySearch').checked;
        const hideNonPublic = document.getElementById('hideNonPublic').checked;

        // Use batch endpoint to prevent duplicate log entries
        const response = await fetch('/admin/api/app-config/search-settings/batch', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                fuzzySearch: fuzzySearch ? 'true' : 'false',
                hideNonPublic: hideNonPublic ? 'true' : 'false'
            })
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        const data = await response.json();
        console.log('Save response:', data);
        showNotification(appT('adminPanel.appSettingsPage.searchSettingsSaved', 'Search settings saved successfully'), 'success');
    } catch (error) {
        console.error('Error saving search settings:', error);
        showNotification(appT('adminPanel.appSettingsPage.searchSettingsSaveError', 'Error saving search settings: ') + error.message, 'error');
    }
}

/**
 * Saves search settings and closes the interface
 */
function saveAndCloseSearchSettings() {
    saveSearchSettings();
    // The save function will show success notification
    // User stays on the same page as requested
}

/**
 * Closes the search settings interface
 */
function closeSearchSettings() {
    showApplicationSettingsContent(document.querySelector('.content-area'));
}

// ===== EXPORT OBJECTS FUNCTIONALITY =====

/**
 * Shows the Export Objects interface
 */
function showExportObjects() {
    const contentArea = document.querySelector('.content-area');
    addToNavigationHistory('Export Objects', showExportObjects);

    showLoadingState(contentArea, appT('adminPanel.appSettingsPage.loadingExportSettings', 'Loading Export Settings'), appT('adminPanel.appSettingsPage.fetchingExportConfig', 'Fetching export configuration...'));

    fetchExportSettings()
        .then(data => {
            contentArea.innerHTML = generateExportObjectsInterface(data);
            setTimeout(initializeExportObjectsInterface, 100);
        })
        .catch(error => {
            console.error('Error fetching export settings:', error);
            showErrorState(contentArea, appT('adminPanel.appSettingsPage.unableLoadExportSettings', 'Unable to Load Export Settings'),
                appT('adminPanel.appSettingsPage.connectionError', 'There was an error connecting...'),
                showExportObjects, showApplicationSettingsContent);
        });
}

/**
 * Fetches export settings from app_config table
 * @returns {Promise<Object>} Promise resolving to export settings
 */
async function fetchExportSettings() {
    const response = await fetch('/admin/api/export-config');
    if (!response.ok) {
        const text = await response.text();
        console.error(`Fetch failed: ${response.status} ${response.statusText}`, text);
        throw new Error(`Network response was not ok: ${response.status} ${response.statusText}`);
    }
    return response.json();
}

/**
 * Generates the Export Objects interface HTML
 * @param {Object} data - Export settings data
 * @returns {string} HTML string for the interface
 */
function generateExportObjectsInterface(data) {
    const T = appT;
    const isExportEnabled = data && data.enabled === true;

    return `
        <div class="export-objects-container">
            <div class="export-objects-header">
                <div class="header-left">
                    <div class="header-title">
                        <h2>${escapeHtml(T('adminPanel.appSettingsPage.exportObjectsTitle', 'Export Objects'))}</h2>
                        <p>${escapeHtml(T('adminPanel.appSettingsPage.axiomManagement', 'Axiom Management'))}</p>
                    </div>
                </div>
                <div class="header-actions">
                    <button class="btn-save" onclick="saveExportSettings()">
                        <i class="fas fa-save"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.save', 'Save'))}
                    </button>
                    <button class="btn-save-close" onclick="saveAndCloseExportSettings()">
                        <i class="fas fa-save"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.saveAndClose', 'Save & Close'))}
                    </button>
                    <button class="btn-close" onclick="closeExportSettings()">
                        <i class="fas fa-times"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.close', 'Close'))}
                    </button>
                </div>
            </div>
            
            <div class="export-objects-banner">
                <p>${escapeHtml(T('adminPanel.appSettingsPage.exportObjectsBanner', 'Enable export of People objects...'))}</p>
            </div>
            
            <div class="export-options-container">
                <div class="export-option">
                    <div class="option-checkbox">
                        <input type="checkbox" id="exportPeopleEnabled" ${isExportEnabled ? 'checked' : ''}>
                        <label for="exportPeopleEnabled"></label>
                    </div>
                    <div class="option-content">
                        <div class="option-title">${escapeHtml(T('adminPanel.appSettingsPage.people', 'People'))}</div>
                        <div class="option-description">${escapeHtml(T('adminPanel.appSettingsPage.exportPeopleDesc', 'Enable export of People...'))}</div>
                    </div>
                </div>
            </div>
        </div>
    `;
}

/**
 * Initializes the Export Objects interface
 */
function initializeExportObjectsInterface() {
    console.log('Export Objects interface initialized');
}

/**
 * Saves the current export settings
 */
async function saveExportSettings() {
    try {
        const exportEnabled = document.getElementById('exportPeopleEnabled').checked;

        const response = await fetch('/admin/api/export-config', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                enabled: exportEnabled
            })
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        const data = await response.json();
        console.log('Save response:', data);
        showNotification(appT('adminPanel.appSettingsPage.exportSettingsSaved', 'Export settings saved successfully'), 'success');
    } catch (error) {
        console.error('Error saving export settings:', error);
        showNotification(appT('adminPanel.appSettingsPage.exportSettingsSaveError', 'Error saving export settings: ') + error.message, 'error');
    }
}

/**
 * Saves export settings and closes the interface
 */
function saveAndCloseExportSettings() {
    saveExportSettings();
    // The save function will show success notification
    // User stays on the same page as requested
}

/**
 * Closes the export settings interface
 */
function closeExportSettings() {
    showApplicationSettingsContent(document.querySelector('.content-area'));
}

// ===== CONFIGURE NOTIFICATIONS FUNCTIONALITY =====

/**
 * Shows the Configure Notifications Settings interface
 * Allows admins to disable email notifications for Tasks (Workflows) and Change Requests
 */
function showConfigureNotificationsSettings() {
    const contentArea = document.querySelector('.content-area');
    addToNavigationHistory('Configure Notifications', showConfigureNotificationsSettings);
    const T = appT;
    const backTitle = escapeHtml(T('adminPanel.appSettingsPage.backToAppSettings', 'Back to Application Settings'));
    contentArea.innerHTML = `
        <div class="app-settings-content">
            <div class="app-settings-banner">
                <div class="banner-left">
                    <h2>${escapeHtml(T('adminPanel.appSettingsPage.configureNotificationsTitle', 'CONFIGURE NOTIFICATIONS'))}</h2>
                </div>
                <div class="banner-right" onclick="showApplicationSettingsContent(document.querySelector('.content-area'))" title="${backTitle}">
                    <i class="fas fa-arrow-left"></i>
                    <span>${escapeHtml(T('adminPanel.appSettingsPage.back', 'Back'))}</span>
                </div>
            </div>
            
            <div class="notification-settings-content-wrapper" style="padding: 20px; max-width: 800px; margin: 0 auto;">
                <div class="notification-settings-card" style="background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); padding: 30px;">
                    <div class="card-header" style="margin-bottom: 25px;">
                        <h3 style="margin: 0; color: #333; font-size: 18px; font-weight: 600;">${escapeHtml(T('adminPanel.appSettingsPage.emailNotificationSettings', 'Email Notification Settings'))}</h3>
                        <p style="margin: 8px 0 0 0; color: #666; font-size: 14px;">
                            ${escapeHtml(T('adminPanel.appSettingsPage.emailNotificationIntro', 'Configure email notification preferences...'))}
                        </p>
                    </div>
                    
                    <div id="notificationSettingsForm">
                        <div style="margin-bottom: 30px; padding: 20px; background: #f8f9fa; border-radius: 6px; border-left: 4px solid #248567;">
                            <label style="display: flex; align-items: flex-start; gap: 12px; cursor: pointer;">
                                <input type="checkbox" id="disableTasksNotifications" 
                                       style="width: 20px; height: 20px; margin-top: 2px; cursor: pointer; flex-shrink: 0;">
                                <div style="flex: 1;">
                                    <div style="font-weight: 600; color: #333; margin-bottom: 6px; font-size: 15px;">
                                        ${escapeHtml(T('adminPanel.appSettingsPage.disableTasksEmailsTitle', 'Disable Notification Emails for Tasks'))}
                                    </div>
                                    <div style="color: #666; font-size: 13px; line-height: 1.5;">
                                        ${escapeHtml(T('adminPanel.appSettingsPage.disableTasksEmailsDesc', 'When enabled...'))}
                                        <ul style="margin: 8px 0 0 20px; padding: 0; color: #666;">
                                            <li>${escapeHtml(T('adminPanel.appSettingsPage.taskAssignments', 'Task assignments'))}</li>
                                            <li>${escapeHtml(T('adminPanel.appSettingsPage.taskOverdue', 'Task overdue notifications'))}</li>
                                            <li>${escapeHtml(T('adminPanel.appSettingsPage.taskEscalation', 'Task escalation notifications'))}</li>
                                            <li>${escapeHtml(T('adminPanel.appSettingsPage.workflowMentions', 'Workflow discussion mentions'))}</li>
                                            <li>${escapeHtml(T('adminPanel.appSettingsPage.workflowStepNotifications', 'Workflow step completion...'))}</li>
                                        </ul>
                                    </div>
                                </div>
                            </label>
                        </div>
                        
                        <div style="margin-bottom: 30px; padding: 20px; background: #f8f9fa; border-radius: 6px; border-left: 4px solid #248567;">
                            <label style="display: flex; align-items: flex-start; gap: 12px; cursor: pointer;">
                                <input type="checkbox" id="disableCRsNotifications" 
                                       style="width: 20px; height: 20px; margin-top: 2px; cursor: pointer; flex-shrink: 0;">
                                <div style="flex: 1;">
                                    <div style="font-weight: 600; color: #333; margin-bottom: 6px; font-size: 15px;">
                                        ${escapeHtml(T('adminPanel.appSettingsPage.disableCrsEmailsTitle', 'Disable Notification Emails for Change Requests'))}
                                    </div>
                                    <div style="color: #666; font-size: 13px; line-height: 1.5;">
                                        ${escapeHtml(T('adminPanel.appSettingsPage.disableCrsEmailsDesc', 'When enabled...'))}
                                        <ul style="margin: 8px 0 0 20px; padding: 0; color: #666;">
                                            <li>${escapeHtml(T('adminPanel.appSettingsPage.crRaised', 'Change request raised / created'))}</li>
                                            <li>${escapeHtml(T('adminPanel.appSettingsPage.crWorkflowStarted', 'Change request workflow started'))}</li>
                                            <li>${escapeHtml(T('adminPanel.appSettingsPage.crCancelled', 'Change request cancelled'))}</li>
                                            <li>${escapeHtml(T('adminPanel.appSettingsPage.crCompleted', 'Change request completed'))}</li>
                                        </ul>
                                    </div>
                                </div>
                            </label>
                        </div>
                        
                        <div id="notificationSettingsStatus" style="margin-top: 20px; padding: 12px; border-radius: 4px; display: none;"></div>
                        
                        <div style="display: flex; gap: 10px; justify-content: flex-end; margin-top: 30px; padding-top: 20px; border-top: 1px solid #e0e0e0;">
                            <button type="button" id="cancelNotificationSettingsBtn" 
                                    style="padding: 10px 20px; border: 1px solid #ccc; background: white; color: #333; border-radius: 4px; cursor: pointer; font-weight: 500; font-size: 14px;">
                                ${escapeHtml(T('adminPanel.appSettingsPage.cancel', 'Cancel'))}
                            </button>
                            <button type="button" id="saveNotificationSettingsBtn" 
                                    style="padding: 10px 20px; border: none; background: #248567; color: white; border-radius: 4px; cursor: pointer; font-weight: 500; font-size: 14px;">
                                <i class="fas fa-save"></i> ${escapeHtml(T('adminPanel.appSettingsPage.saveSettings', 'Save Settings'))}
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <style>
            .notification-settings-content-wrapper {
                animation: fadeIn 0.3s ease-in;
            }
            @keyframes fadeIn {
                from { opacity: 0; transform: translateY(10px); }
                to { opacity: 1; transform: translateY(0); }
            }
            #disableTasksNotifications:hover, #disableCRsNotifications:hover {
                transform: scale(1.05);
                transition: transform 0.2s;
            }
            #saveNotificationSettingsBtn:hover {
                background-color: #1e6b52 !important;
            }
            #cancelNotificationSettingsBtn:hover {
                background-color: #f0f0f0 !important;
            }
        </style>
    `;
    
    // Initialize
    initializeNotificationSettingsPage();
}

/**
 * Shows the Configure Notifications (Notification Rules) interface
 * This is a wrapper function that gets the contentArea and calls the actual function
 */
function showConfigureNotifications() {
    const contentArea = document.querySelector('.content-area');
    addToNavigationHistory('Configure Notifications', showConfigureNotifications);
    if (typeof showNotificationRulesContent === 'function') {
        showNotificationRulesContent(contentArea);
    } else {
        console.error('showNotificationRulesContent function not found. Make sure notification-rules.js is loaded.');
    }
}

/**
 * Initialize the notification settings page
 */
function initializeNotificationSettingsPage() {
    // Load current settings
    loadNotificationSettings();
    
    // Setup event listeners
    const saveBtn = document.getElementById('saveNotificationSettingsBtn');
    const cancelBtn = document.getElementById('cancelNotificationSettingsBtn');
    
    if (saveBtn) {
        saveBtn.addEventListener('click', saveNotificationSettings);
    }
    
    if (cancelBtn) {
        cancelBtn.addEventListener('click', () => {
            showApplicationSettingsContent(document.querySelector('.content-area'));
        });
    }
}

/**
 * Load notification settings from server
 */
async function loadNotificationSettings() {
    try {
        const [tasksResponse, crsResponse] = await Promise.all([
            fetch('/api/system-settings/Notifications/disable_notification_emails_for_tasks', { credentials: 'include' }),
            fetch('/api/system-settings/Notifications/disable_notification_emails_for_crs', { credentials: 'include' })
        ]);
        
        let tasksDisabled = false;
        let crsDisabled = false;
        
        if (tasksResponse.ok) {
            const tasksData = await tasksResponse.json();
            tasksDisabled = tasksData.value === 'true' || tasksData.value === true;
        }
        
        if (crsResponse.ok) {
            const crsData = await crsResponse.json();
            crsDisabled = crsData.value === 'true' || crsData.value === true;
        }
        
        // Update checkboxes
        const tasksCheckbox = document.getElementById('disableTasksNotifications');
        const crsCheckbox = document.getElementById('disableCRsNotifications');
        
        if (tasksCheckbox) {
            tasksCheckbox.checked = tasksDisabled;
        }
        if (crsCheckbox) {
            crsCheckbox.checked = crsDisabled;
        }
        
    } catch (error) {
        console.error('Error loading notification settings:', error);
        showNotificationSettingsStatusMessage(appT('adminPanel.appSettingsPage.notificationSettingsLoadError', 'Error loading settings...'), 'error');
    }
}

/**
 * Save notification settings
 */
async function saveNotificationSettings() {
    const tasksCheckbox = document.getElementById('disableTasksNotifications');
    const crsCheckbox = document.getElementById('disableCRsNotifications');
    
    const tasksDisabled = tasksCheckbox ? tasksCheckbox.checked : false;
    const crsDisabled = crsCheckbox ? crsCheckbox.checked : false;
    
    try {
        const [tasksResponse, crsResponse] = await Promise.all([
            fetch('/api/system-settings/Notifications/disable_notification_emails_for_tasks', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({
                    value: tasksDisabled.toString(),
                    dataType: 'boolean'
                })
            }),
            fetch('/api/system-settings/Notifications/disable_notification_emails_for_crs', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({
                    value: crsDisabled.toString(),
                    dataType: 'boolean'
                })
            })
        ]);
        
        if (!tasksResponse.ok || !crsResponse.ok) {
            throw new Error('Failed to save settings');
        }
        
        showNotificationSettingsStatusMessage(appT('adminPanel.appSettingsPage.notificationSettingsSaved', 'Settings saved successfully!'), 'success');
        
        // Reload settings after a short delay
        setTimeout(() => {
            loadNotificationSettings();
        }, 1000);
        
    } catch (error) {
        console.error('Error saving notification settings:', error);
        showNotificationSettingsStatusMessage(appT('adminPanel.appSettingsPage.notificationSettingsSaveError', 'Error saving settings...'), 'error');
    }
}

/**
 * Show status message for notification settings — uses unified admin panel toast
 * (same DOM as admin-notifications.js: notification + notification-error/success + show).
 */
function showNotificationSettingsStatusMessage(message, type) {
    if (typeof window.showAdminNotification === 'function') {
        window.showAdminNotification(message, type === 'success' ? 'success' : 'error');
    }
    // Hide legacy inline bar if present so we don't show two UIs
    const statusDiv = document.getElementById('notificationSettingsStatus');
    if (statusDiv) {
        statusDiv.style.display = 'none';
        statusDiv.textContent = '';
    }
    if (typeof window.showAdminNotification !== 'function') {
        console.warn('showAdminNotification not available; notification settings message:', message);
    }
}

// ===== GLOSSARY ROLLUP SETTINGS FUNCTIONALITY =====

/**
 * Shows the Glossary Rollup Settings interface
 */
function showGlossaryRollupSettings() {
    const contentArea = document.querySelector('.content-area');
    addToNavigationHistory('Glossary Rollup Settings', showGlossaryRollupSettings);

    showLoadingState(contentArea, appT('adminPanel.appSettingsPage.loadingGlossaryRollup', 'Loading Glossary Rollup Settings'), appT('adminPanel.appSettingsPage.fetchingGlossaryTypes', 'Fetching glossary types...'));

    Promise.all([
        fetch('/api/type/list', { credentials: 'include' }), // Get all glossary types
        fetch('/api/system-settings/GlossaryRollup/enabled_types', { credentials: 'include' }).catch(() => null) // Get current settings
    ])
        .then(([typesResponse, settingsResponse]) => {
            if (!typesResponse.ok) {
                throw new Error('Failed to fetch glossary types');
            }
            return Promise.all([
                typesResponse.json(),
                settingsResponse ? settingsResponse.json() : { value: '["Domain","Subdomain","Term","Metric"]' }
            ]);
        })
        .then(([types, settings]) => {
            const enabledTypes = settings.value ? JSON.parse(settings.value) : ['Domain', 'Subdomain', 'Term', 'Metric'];
            contentArea.innerHTML = generateGlossaryRollupSettingsInterface(types, enabledTypes);
            setTimeout(initializeGlossaryRollupSettingsInterface, 100);
        })
        .catch(error => {
            console.error('Error fetching glossary rollup settings:', error);
            showErrorState(contentArea, appT('adminPanel.appSettingsPage.unableLoadGlossaryRollup', 'Unable to Load Glossary Rollup Settings'),
                appT('adminPanel.appSettingsPage.connectionError', 'There was an error connecting...'),
                showGlossaryRollupSettings, showApplicationSettingsContent);
        });
}

/**
 * Generates the Glossary Rollup Settings interface HTML
 * @param {Array} types - Array of glossary types
 * @param {Array} enabledTypes - Array of enabled type names
 * @returns {string} HTML string for the interface
 */
function generateGlossaryRollupSettingsInterface(types, enabledTypes) {
    const T = appT;
    const backTitle = escapeHtml(T('adminPanel.appSettingsPage.backToAppSettings', 'Back to Application Settings'));
    const checkboxesHTML = types.map(type => {
        const isChecked = enabledTypes.includes(type.name);
        return `
            <div class="rollup-type-item">
                <label class="rollup-checkbox-label">
                    <input type="checkbox" class="rollup-type-checkbox" 
                           data-type-name="${type.name}" 
                           ${isChecked ? 'checked' : ''}>
                    <span class="checkmark"></span>
                    <span class="type-name">${type.name}</span>
                </label>
            </div>
        `;
    }).join('');

    return `
        <div class="app-settings-content">
            <div class="app-settings-banner">
                <div class="banner-left">
                    <h2>${escapeHtml(T('adminPanel.appSettingsPage.glossaryRollupBannerTitle', 'GLOSSARY ROLLUP SETTINGS'))}</h2>
                </div>
                <div class="banner-right" onclick="closeGlossaryRollupSettings()" title="${backTitle}">
                    <i class="fas fa-arrow-left"></i>
                    <span>${escapeHtml(T('adminPanel.appSettingsPage.back', 'Back'))}</span>
                </div>
            </div>
            
            <div class="rollup-settings-content-wrapper">
                <div class="rollup-settings-card">
                    <div class="card-header">
                        <h3>${escapeHtml(T('adminPanel.appSettingsPage.glossaryRollupCardTitle', 'Select the glossary types...'))}</h3>
                    </div>
                    <div class="card-body">
                        <div class="rollup-types-list">
                            ${checkboxesHTML}
                        </div>
                    </div>
                </div>
                
                <div class="rollup-settings-actions">
                    <button class="btn btn-primary" onclick="saveGlossaryRollupSettings()">
                        <i class="fas fa-save"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.save', 'Save'))}
                    </button>
                    <button class="btn btn-success" onclick="saveAndCloseGlossaryRollupSettings()">
                        <i class="fas fa-save"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.saveAndClose', 'Save & Close'))}
                    </button>
                    <button class="btn btn-secondary" onclick="closeGlossaryRollupSettings()">
                        <i class="fas fa-times"></i>
                        ${escapeHtml(T('adminPanel.appSettingsPage.close', 'Close'))}
                    </button>
                </div>
            </div>
        </div>
    `;
}

/**
 * Initializes the Glossary Rollup Settings interface
 */
function initializeGlossaryRollupSettingsInterface() {
    console.log('Glossary Rollup Settings interface initialized');
}

/**
 * Saves the current glossary rollup settings
 */
async function saveGlossaryRollupSettings() {
    try {
        const checkboxes = document.querySelectorAll('.rollup-type-checkbox:checked');
        const enabledTypes = Array.from(checkboxes).map(cb => cb.dataset.typeName);
        
        const response = await fetch('/api/system-settings/GlossaryRollup/enabled_types', {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            credentials: 'include',
            body: JSON.stringify({
                value: JSON.stringify(enabledTypes),
                dataType: 'json'
            })
        });

        if (!response.ok) {
            const serverMsg = await appSettingsReadErrorBody(response);
            if (appSettingsIsForbidden(response, serverMsg)) {
                showNotification(appSettingsSuperAdminOnlyMessage(), 'warning');
                return;
            }
            throw new Error(serverMsg || appT('adminPanel.appSettingsPage.glossaryRollupSaveFailed', 'Could not save settings'));
        }

        const data = await response.json();
        console.log('Save response:', data);
        showNotification(appT('adminPanel.appSettingsPage.glossaryRollupSaved', 'Glossary Rollup settings saved successfully'), 'success');
    } catch (error) {
        if (!String(error.message || '').includes('Super Admin') && !String(error.message || '').includes('SuperAdministrator')) {
            console.error('Error saving glossary rollup settings:', error);
        }
        showNotification(appT('adminPanel.appSettingsPage.glossaryRollupSaveError', 'Could not save Glossary Rollup settings. ') + (error.message || ''), 'error');
    }
}

/**
 * Saves glossary rollup settings and closes the interface
 */
function saveAndCloseGlossaryRollupSettings() {
    saveGlossaryRollupSettings();
}

/**
 * Closes the glossary rollup settings interface
 */
function closeGlossaryRollupSettings() {
    showApplicationSettingsContent(document.querySelector('.content-area'));
}


