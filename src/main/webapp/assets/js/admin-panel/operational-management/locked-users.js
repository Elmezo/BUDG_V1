// Locked Users functionality for Operational Management

function showLockedUsersContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.lockedUsers');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return (window.I18n && window.I18n.t(k) !== k) ? window.I18n.t(k) : f; };
    const loadingLockedUsers = T('adminPanel.common.loadingLockedUsers', 'Loading locked users...');
    const recordsLabel = T('adminPanel.staticPageEditor.recordsCount', '{count} records').replace('{count}', '0');
    contentArea.innerHTML = `
        <div class="locked-users-content">
            <div class="locked-users-header">
                <div class="header-left">
                    <h2>${T('adminPanel.lockedUsers.pageTitle', 'LOCKED USERS')}</h2>
                </div>
                <div class="header-right">
                    <button id="unlockBtn" class="btn btn-primary unlock-btn" onclick="unlockSelectedUsers()" disabled>
                        <i class="fas fa-unlock"></i> ${T('adminPanel.lockedUsers.unlock', 'Unlock')}
                    </button>
                </div>
            </div>
            <div class="locked-users-table-container">
                <table class="locked-users-table">
                    <thead>
                        <tr>
                            <th style="width: 50px;">
                                <input type="checkbox" id="selectAllCheckbox" onchange="toggleSelectAll(this)">
                            </th>
                            <th>${T('adminPanel.lockedUsers.email', 'Email')}</th>
                            <th>${T('adminPanel.lockedUsers.userName', 'User Name')}</th>
                            <th>${T('adminPanel.lockedUsers.role', 'Role')}</th>
                            <th>${T('adminPanel.lockedUsers.lastAttemptDate', 'Last Attempt Date')}</th>
                        </tr>
                    </thead>
                    <tbody id="lockedUsersTableBody">
                        <tr>
                            <td colspan="5" class="loading">${loadingLockedUsers}</td>
                        </tr>
                    </tbody>
                </table>
                <div class="table-footer">
                    <span id="recordCount" class="record-count">${recordsLabel}</span>
                </div>
            </div>
        </div>
    `;
    
    loadLockedUsers();
}

async function loadLockedUsers() {
    const tbody = document.getElementById('lockedUsersTableBody');
    if (!tbody) return;
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };

    if (!checkIsSuperAdmin(window._adminUserRole)) {
        tbody.innerHTML = '<tr><td colspan="5" class="error">' + T('adminPanel.lockedUsers.accessDenied', 'Access denied. Super Admin role required to view locked users.') + '</td></tr>';
        updateRecordCount(0);
        const unlockBtn = document.getElementById('unlockBtn');
        if (unlockBtn) unlockBtn.disabled = true;
        return;
    }
    
    try {
        const loadingLockedUsers = window.I18n ? window.I18n.t('adminPanel.common.loadingLockedUsers') : 'Loading locked users...';
        tbody.innerHTML = `<tr><td colspan="5" class="loading">${loadingLockedUsers}</td></tr>`;
        
        const response = await fetch('/api/locked-users', {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (!response.ok) {
            if (response.status === 403) {
                tbody.innerHTML = '<tr><td colspan="5" class="error">' + T('adminPanel.lockedUsers.accessDenied403', 'Access denied. Super admin role required.') + '</td></tr>';
                return;
            }
            throw new Error('Failed to load locked users');
        }
        
        const lockedUsers = await response.json();
        
        if (!lockedUsers || lockedUsers.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="no-data">' + T('adminPanel.lockedUsers.noLockedUsers', 'No locked users found') + '</td></tr>';
            updateRecordCount(0);
            return;
        }
        
        tbody.innerHTML = lockedUsers.map((user, index) => {
            const email = escapeHtml(user.email || '');
            const name = escapeHtml(user.name || '');
            const role = escapeHtml(user.role || '');
            const lockedDate = user.locked_date || 'N/A';
            
            return `
                <tr data-email="${email}">
                    <td>
                        <input type="checkbox" class="user-checkbox" value="${email}" onchange="updateUnlockButton()">
                    </td>
                    <td>${email}</td>
                    <td>${name}</td>
                    <td>${role}</td>
                    <td>${lockedDate}</td>
                </tr>
            `;
        }).join('');
        
        updateRecordCount(lockedUsers.length);
        updateUnlockButton();
        
    } catch (error) {
        console.error('Failed to load locked users:', error);
        tbody.innerHTML = '<tr><td colspan="5" class="error">' + T('adminPanel.lockedUsers.failedToLoad', 'Failed to load locked users') + '</td></tr>';
        updateRecordCount(0);
    }
}

function toggleSelectAll(checkbox) {
    const userCheckboxes = document.querySelectorAll('.user-checkbox');
    userCheckboxes.forEach(cb => {
        cb.checked = checkbox.checked;
    });
    updateUnlockButton();
}

function updateUnlockButton() {
    const unlockBtn = document.getElementById('unlockBtn');
    const selectedCheckboxes = document.querySelectorAll('.user-checkbox:checked');
    
    if (unlockBtn) {
        unlockBtn.disabled = selectedCheckboxes.length === 0;
    }
}

async function unlockSelectedUsers() {
    if (!checkIsSuperAdmin(window._adminUserRole)) {
        showSuccessMessage('Access denied. Super Admin role required.', true);
        return;
    }

    const selectedCheckboxes = document.querySelectorAll('.user-checkbox:checked');
    
    if (selectedCheckboxes.length === 0) {
        showSuccessMessage('Please select at least one user to unlock.', true);
        return;
    }
    
    const emails = Array.from(selectedCheckboxes).map(cb => cb.value);
    
    if (!confirm(`Are you sure you want to unlock ${emails.length} user account(s)?`)) {
        return;
    }
    
    try {
        const response = await fetch('/api/locked-users/unlock', {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ emails: emails })
        });
        
        if (!response.ok) {
            if (response.status === 403) {
                showSuccessMessage('Access denied. Super admin role required.', true);
                return;
            }
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || 'Failed to unlock users');
        }
        
        const result = await response.json();
        
        // Show success message
        showSuccessMessage(result.message || 'The user accounts have been unlocked.');
        
        // Reload the table
        loadLockedUsers();
        
    } catch (error) {
        console.error('Failed to unlock users:', error);
        showSuccessMessage('Failed to unlock users: ' + (error.message || 'Unknown error'), true);
    }
}

function updateRecordCount(count) {
    const recordCountEl = document.getElementById('recordCount');
    if (recordCountEl) {
        const tmpl = typeof adminT === 'function' ? adminT('adminPanel.staticPageEditor.recordsCount', '{count} records') : '{count} records';
        recordCountEl.textContent = tmpl.replace('{count}', String(count));
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showSuccessMessage(message, isError = false) {
    // Remove any existing success message
    const existingMessage = document.getElementById('success-message');
    if (existingMessage) {
        existingMessage.remove();
    }
    
    // Create success message element
    const successDiv = document.createElement('div');
    successDiv.id = 'success-message';
    const backgroundColor = isError ? '#ef4444' : '#248567';
    successDiv.style.cssText = `
        position: fixed;
        top: 80px;
        left: 50%;
        transform: translateX(-50%);
        background-color: ${backgroundColor};
        color: white;
        padding: 12px 24px;
        border-radius: 6px;
        font-weight: 600;
        font-size: 14px;
        z-index: 10000;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        animation: slideDown 0.3s ease-out;
    `;
    successDiv.textContent = message;
    
    // Add CSS animation if not already present
    if (!document.getElementById('success-message-styles')) {
        const style = document.createElement('style');
        style.id = 'success-message-styles';
        style.textContent = `
            @keyframes slideDown {
                from {
                    opacity: 0;
                    transform: translateX(-50%) translateY(-20px);
                }
                to {
                    opacity: 1;
                    transform: translateX(-50%) translateY(0);
                }
            }
            @keyframes slideUp {
                from {
                    opacity: 1;
                    transform: translateX(-50%) translateY(0);
                }
                to {
                    opacity: 0;
                    transform: translateX(-50%) translateY(-20px);
                }
            }
        `;
        document.head.appendChild(style);
    }
    
    // Add to page
    document.body.appendChild(successDiv);
    
    // Auto remove after 3 seconds
    setTimeout(() => {
        if (successDiv.parentNode) {
            successDiv.style.animation = 'slideUp 0.3s ease-out';
            setTimeout(() => {
                if (successDiv.parentNode) {
                    successDiv.remove();
                }
            }, 300);
        }
    }, 3000);
}
