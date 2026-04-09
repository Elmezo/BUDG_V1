// Manage Locks functionality for Operational Management

function showManageLocksContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.manageLocks');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return (window.I18n && window.I18n.t(k) !== k) ? window.I18n.t(k) : f; };
    const loadingLocks = T('adminPanel.common.loadingLocks', 'Loading locks...');
    contentArea.innerHTML = `
        <div class="manage-locks-content">
            <div class="locks-header">
                <h2>${T('adminPanel.manageLocks.title', 'Manage Locks')}</h2>
                <p>${T('adminPanel.manageLocks.intro', 'View and manage all object locks in the system. Unlock objects that are locked by users.')}</p>
            </div>
            <div class="locks-controls">
                <button class="btn btn-primary" onclick="refreshLocks()">
                    <i class="fas fa-sync-alt"></i> ${T('adminPanel.manageLocks.refresh', 'Refresh')}
                </button>
                <button class="btn btn-secondary" onclick="showCreatePermanentLockModal()">
                    <i class="fas fa-lock"></i> ${T('adminPanel.manageLocks.createPermanentLock', 'Create Permanent Lock')}
                </button>
            </div>
            <div class="locks-table-container">
                <table class="locks-table">
                    <thead>
                        <tr>
                            <th>${T('adminPanel.manageLocks.colId', 'ID')}</th>
                            <th>${T('adminPanel.manageLocks.colName', 'Name')}</th>
                            <th>${T('adminPanel.manageLocks.colType', 'Type')}</th>
                            <th>${T('adminPanel.manageLocks.colLockedBy', 'Locked By')}</th>
                            <th>${T('adminPanel.manageLocks.colPermanent', 'Permanent')}</th>
                            <th>${T('adminPanel.manageLocks.colLockedSince', 'Locked Since')}</th>
                            <th>${T('adminPanel.manageLocks.colActions', 'Actions')}</th>
                        </tr>
                    </thead>
                    <tbody id="locksTableBody">
                        <tr>
                            <td colspan="7" class="loading">${loadingLocks}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>
    `;
    
    loadLocks();
}

async function loadLocks() {
    const tbody = document.getElementById('locksTableBody');
    if (!tbody) return;
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return (window.I18n && window.I18n.t(k) !== k) ? window.I18n.t(k) : f; };

    try {
        const loadingLocks = T('adminPanel.common.loadingLocks', 'Loading locks...');
        tbody.innerHTML = `<tr><td colspan="7" class="loading">${loadingLocks}</td></tr>`;
        
        const locks = await window.BUDG_API_SERVICE.getAllLocks();
        
        if (!locks || locks.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="no-data">' + T('adminPanel.manageLocks.noLocksFound', 'No locks found') + '</td></tr>';
            return;
        }
        
        tbody.innerHTML = locks.map(lock => {
            const lockedSince = lock.createdDatetime ? formatDateTime(lock.createdDatetime) : 'N/A';
            const isPermanent = lock.isPermanent ? 'Yes' : 'No';
            const permanentClass = lock.isPermanent ? 'permanent' : '';
            
            return `
                <tr class="${permanentClass}">
                    <td>${lock.lockId}</td>
                    <td>
                        <a href="/view/${getModulePath(lock.moduleName)}/${lock.objectId}" target="_blank">
                            ${escapeHtml(lock.objectName || T('adminPanel.manageLocks.unknown', 'Unknown'))}
                        </a>
                    </td>
                    <td>${escapeHtml(lock.moduleName || T('adminPanel.manageLocks.unknown', 'Unknown'))}</td>
                    <td>${escapeHtml(lock.lockedByName || T('adminPanel.manageLocks.unknown', 'Unknown'))}</td>
                    <td><span class="badge ${lock.isPermanent ? 'badge-warning' : 'badge-info'}">${isPermanent}</span></td>
                    <td>${lockedSince}</td>
                    <td>
                        <button class="btn btn-sm btn-danger" onclick="deleteLock(${lock.lockId}, '${escapeHtml(lock.objectName || T('adminPanel.manageLocks.unknown', 'Unknown'))}')" title="${T('adminPanel.manageLocks.unlock', 'Unlock')}">
                            <i class="fas fa-unlock"></i> ${T('adminPanel.manageLocks.unlock', 'Unlock')}
                        </button>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (error) {
        console.error('Failed to load locks:', error);
        if (error.status === 403) {
            tbody.innerHTML = '<tr><td colspan="7" class="error">' + T('adminPanel.manageLocks.noPermissionLocks', 'You do not have permission...') + '</td></tr>';
        } else {
            tbody.innerHTML = '<tr><td colspan="7" class="error">' + T('adminPanel.manageLocks.failedToLoadLocks', 'Failed to load locks. Please try again.') + '</td></tr>';
        }
    }
}

async function deleteLock(lockId, objectName) {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const tpl = function (str, vars) {
        if (!str || !vars) return str;
        var out = str;
        Object.keys(vars).forEach(function (k) { out = out.split('{{' + k + '}}').join(String(vars[k])); });
        return out;
    };
    const msg = (T('adminPanel.manageLocks.unlockConfirm', 'Are you sure you want to unlock "{name}"?')).replace('{name}', objectName);
    if (!confirm(msg)) {
        return;
    }
    
    try {
        const result = await window.BUDG_API_SERVICE.deleteLock(lockId);
        if (result && result.success) {
            if (window.showAdminNotification) window.showAdminNotification(T('adminPanel.manageLocks.lockReleasedSuccess', 'Lock released successfully.'), 'success');
            loadLocks();
        } else {
            const errMsg = result?.error || T('adminPanel.manageLocks.unknown', 'Unknown error');
            if (window.showAdminNotification) window.showAdminNotification(tpl(T('adminPanel.manageLocks.failedReleaseLock', 'Failed to release lock: {{message}}'), { message: errMsg }), 'error');
        }
    } catch (error) {
        console.error('Failed to delete lock:', error);
        const msg = error.status === 403
            ? T('adminPanel.manageLocks.noPermissionLocks', 'You do not have permission to release locks from the admin panel. Only administrators can manage locks here.')
            : tpl(T('adminPanel.manageLocks.failedReleaseLock', 'Failed to release lock: {{message}}'), { message: error.body?.error || error.message || T('adminPanel.manageLocks.unknown', 'Unknown error') });
        if (window.showAdminNotification) window.showAdminNotification(msg, 'error');
    }
}

async function refreshLocks() {
    loadLocks();
}

function getModulePath(moduleName) {
    const moduleMap = {
        'Data Sets': 'dataset',
        'System': 'system',
        'Interface': 'system-interface',
        'Glossary': 'glossary'
    };
    return moduleMap[moduleName] || 'dataset';
}

function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return 'N/A';
    try {
        const date = new Date(dateTimeStr);
        return date.toLocaleString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    } catch (e) {
        return dateTimeStr;
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

async function showCreatePermanentLockModal() {
    const overlay = document.createElement('div');
    overlay.id = 'permanentLockOverlay';
    overlay.style.cssText = 'position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;';
    
    // Close function
    const closeModal = () => {
        if (overlay && overlay.parentNode) {
            overlay.remove();
        }
        window._permanentLockOverlay = null;
    };
    
    // Close on overlay click (outside modal)
    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) {
            closeModal();
        }
    });
    
    const modal = document.createElement('div');
    modal.style.cssText = 'background: white; padding: 2rem; border-radius: 8px; max-width: 500px; box-shadow: 0 4px 12px rgba(0,0,0,0.3);';
    modal.addEventListener('click', (e) => e.stopPropagation()); // Prevent closing when clicking inside modal
    
    modal.innerHTML = `
        <h2 style="margin: 0 0 1.5rem 0; color: #2c3e50;">Create Permanent Lock</h2>
        <div style="margin-bottom: 1rem;">
            <label style="display: block; margin-bottom: 0.5rem; color: #5a6c7d; font-weight: 500;">Module:</label>
            <select id="permanentLockModule" style="width: 100%; padding: 0.5rem; border: 1px solid #e0e0e0; border-radius: 4px;">
                <option value="dataset">Data Sets</option>
                <option value="system">System</option>
                <option value="interface">Interface</option>
                <option value="glossary">Glossary</option>
            </select>
        </div>
        <div style="margin-bottom: 1.5rem;">
            <label style="display: block; margin-bottom: 0.5rem; color: #5a6c7d; font-weight: 500;">Object ID:</label>
            <input type="number" id="permanentLockObjectId" style="width: 100%; padding: 0.5rem; border: 1px solid #e0e0e0; border-radius: 4px;" placeholder="Enter object ID">
        </div>
        <div style="display: flex; gap: 1rem;">
            <button id="createLockBtn" style="flex: 1; padding: 0.75rem; background: #248567; color: white; border: none; border-radius: 4px; font-size: 1rem; cursor: pointer;">Create Lock</button>
            <button id="cancelLockBtn" style="flex: 1; padding: 0.75rem; background: #6c757d; color: white; border: none; border-radius: 4px; font-size: 1rem; cursor: pointer;">Cancel</button>
        </div>
    `;
    
    overlay.appendChild(modal);
    document.body.appendChild(overlay);
    
    // Store overlay reference for cleanup
    window._permanentLockOverlay = overlay;
    
    // Attach event listeners after modal is added to DOM
    const createBtn = document.getElementById('createLockBtn');
    const cancelBtn = document.getElementById('cancelLockBtn');
    
    if (createBtn) {
        createBtn.addEventListener('click', createPermanentLock);
    }
    
    if (cancelBtn) {
        cancelBtn.addEventListener('click', closeModal);
    }
}

async function createPermanentLock() {
    const moduleName = document.getElementById('permanentLockModule').value;
    const objectId = parseInt(document.getElementById('permanentLockObjectId').value);
    
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const tpl = function (str, vars) {
        if (!str || !vars) return str;
        var out = str;
        Object.keys(vars).forEach(function (k) { out = out.split('{{' + k + '}}').join(String(vars[k])); });
        return out;
    };
    if (!objectId || isNaN(objectId)) {
        if (window.showAdminNotification) window.showAdminNotification(T('adminPanel.manageLocks.validObjectIdRequired', 'Please enter a valid object ID.'), 'error');
        return;
    }
    
    try {
        // Try to acquire the lock - backend will validate object existence
        const result = await window.BUDG_API_SERVICE.acquireLock(moduleName, objectId, true); // true = permanent
        
        if (result && result.success) {
            if (window.showAdminNotification) window.showAdminNotification(T('adminPanel.manageLocks.permanentLockCreatedSuccess', 'Permanent lock created successfully.'), 'success');
            if (window._permanentLockOverlay) {
                window._permanentLockOverlay.remove();
                window._permanentLockOverlay = null;
            }
            loadLocks();
        } else {
            // Check if the error indicates the object doesn't exist
            const errorMsg = result?.error || '';
            if (errorMsg.includes('does not exist') || errorMsg.includes('not exist') ||
                errorMsg.includes('Invalid') || errorMsg.includes('not found') ||
                errorMsg.includes('Unknown')) {
                if (window.showAdminNotification) window.showAdminNotification(tpl(T('adminPanel.manageLocks.permanentLockObjectNotExist', 'Failed to create permanent lock: Object with ID {{objectId}} does not exist in {{moduleName}}.'), { objectId: objectId, moduleName: moduleName }), 'error');
            } else {
                if (window.showAdminNotification) window.showAdminNotification(tpl(T('adminPanel.manageLocks.permanentLockFailed', 'Failed to create permanent lock: {{message}}'), { message: errorMsg }), 'error');
            }
        }
    } catch (error) {
        console.error('Failed to create permanent lock:', error);
        
        // Extract error message from error object
        let errorMsg = '';
        if (error.body && error.body.error) {
            errorMsg = error.body.error;
        } else if (error.message) {
            errorMsg = error.message;
        } else if (typeof error === 'string') {
            errorMsg = error;
        }
        
        // Check if error indicates object doesn't exist
        if (errorMsg.includes('does not exist') || errorMsg.includes('not exist') || 
            errorMsg.includes('Invalid') || errorMsg.includes('not found') || 
            errorMsg.includes('Unknown') || error.status === 400) {
            if (window.showAdminNotification) window.showAdminNotification(tpl(T('adminPanel.manageLocks.permanentLockObjectNotExist', 'Failed to create permanent lock: Object with ID {{objectId}} does not exist in {{moduleName}}.'), { objectId: objectId, moduleName: moduleName }), 'error');
        } else {
            if (window.showAdminNotification) window.showAdminNotification(tpl(T('adminPanel.manageLocks.permanentLockFailed', 'Failed to create permanent lock: {{message}}'), { message: errorMsg || T('adminPanel.manageLocks.unknown', 'Unknown error') }), 'error');
        }
    }
}
