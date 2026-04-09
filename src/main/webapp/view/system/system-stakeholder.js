// System Stakeholder View JavaScript
(function() {
    let currentSystemId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'
    let stakeholdersData = [];
    let currentUserId = null;
    let currentUserRole = null; // 'admin', 'superadmin', or null
    let canEditStakeholders = false;

    // Initialize stakeholder view
    async function initStakeholderView(systemId, viewMode = 'original') {
        currentSystemId = systemId;
        currentViewMode = viewMode || 'original';
        await fetchCurrentUser();
        loadStakeholders();
    }

    // Fetch current user ID and role from /api/me
    async function fetchCurrentUser() {
        try {
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (response.ok) {
                const userData = await response.json();
                currentUserId = userData.id;
                // Check for admin or superadmin role
                const roleName = (userData.roleName || userData.role || '').toLowerCase();
                const isAdmin = roleName.includes('admin') || roleName === 'administrator';
                const isSuperAdmin = roleName.includes('super') || roleName === 'superadmin' || roleName === 'super admin';
                currentUserRole = isSuperAdmin ? 'superadmin' : (isAdmin ? 'admin' : null);
                
                // Check for role-based edit permission if not admin
                let hasRoleEditPermission = false;
                if (!isAdmin && !isSuperAdmin) {
                    try {
                        const permResp = await fetch('/api/user/permissions/System', {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (permResp.ok) {
                            const permData = await permResp.json();
                            hasRoleEditPermission = permData.success && (permData.canEdit === true || permData.isAdmin === true);
                            console.log('[SystemStakeholderView] Role edit permission:', hasRoleEditPermission);
                        }
                    } catch (permError) {
                        console.warn('[SystemStakeholderView] Error checking permissions:', permError);
                    }
                }
                
                canEditStakeholders = isAdmin || isSuperAdmin || hasRoleEditPermission;
                console.log('Current user ID:', currentUserId, 'Role:', currentUserRole, 'Can edit:', canEditStakeholders, 'hasRoleEditPermission:', hasRoleEditPermission);
            } else {
                console.error('Failed to fetch current user');
            }
        } catch (error) {
            console.error('Error fetching current user:', error);
        }
    }

    // Load stakeholders data
    async function loadStakeholders() {
        try {
            const container = document.getElementById('systemStakeholdersContainer');
            if (!container) return;

            container.innerHTML = '<div class="loading">' + (window.I18n?.t('system.stakeholder.loading') || 'Loading stakeholders...') + '</div>';

            // Call API to get stakeholders data
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/system-stakeholder/${currentSystemId}/stakeholders${viewParam}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const responseData = await response.json();
            console.log('System Stakeholders API Response:', responseData);

            // Handle different API response formats more flexibly
            let data;
            if (Array.isArray(responseData)) {
                data = responseData;
            } else if (responseData.data && Array.isArray(responseData.data)) {
                data = responseData.data;
            } else if (responseData.stakeholders && Array.isArray(responseData.stakeholders)) {
                data = responseData.stakeholders;
            } else if (responseData.result && Array.isArray(responseData.result)) {
                data = responseData.result;
            } else {
                // If no array found, create empty array to show "no data" message
                console.warn('No stakeholders array found in API response, using empty array');
                data = [];
            }

            console.log('Extracted stakeholders data:', data);

            // Transform API data to match frontend format
            stakeholdersData = data.map(item => {
                // Handle Status - comes from roleaccepted.Message via AcceptedID join
                let statusValue = '';
                if (item.RoleAccepted !== null && item.RoleAccepted !== undefined) {
                    statusValue = String(item.RoleAccepted).trim();
                } else if (item.roleAccepted !== null && item.roleAccepted !== undefined) {
                    statusValue = String(item.roleAccepted).trim();
                } else if (item.status !== null && item.status !== undefined) {
                    statusValue = String(item.status).trim();
                }
                
                // Get ObjectXPeopleID (needed for accept API call)
                const objectXPeopleId = item.ObjectXPeopleID || item.objectXPeopleId || item.Object_X_People_ID || item.ID || item.id || '';
                
                return {
                    id: objectXPeopleId || Math.random().toString(36).substr(2, 9),
                    objectXPeopleId: objectXPeopleId, // Store separately for API calls
                    role: item.RoleName || item.Role || item.role || '',
                    name: item.PersonName || item.Name || item.name || '',
                    department: item.OrgUnit || item.orgUnit || item.department || '',
                    status: statusValue,
                    delegateOf: item.DelegateOf || item.delegateOf || item.delegate_of || item.delegate_name || '-',
                    // Add IDs for linking (not shown in table but used for hyperlinks)
                    personId: item.PersonID || item.personId || item.PeopleID || item.peopleId || '',
                    orgUnitId: item.OrgUnitID || item.orgUnitId || item.OrgUnit_ID || item.org_unit_id || ''
                };
            });

            renderStakeholdersTable();
            
            // Initialize stakeholder community section after main stakeholders table is rendered
            if (window.SystemStakeholderCommunity) {
                window.SystemStakeholderCommunity.init(currentSystemId);
            }
        } catch (error) {
            console.error('Error loading stakeholders:', error);
            const container = document.getElementById('systemStakeholdersContainer');
            if (container) {
                container.innerHTML = '<div class="error">Error loading stakeholders data: ' + (error.message || 'Unknown error') + '</div>';
            }
        }
    }

    // Get headers - only show Role, Name, Department, Status, Delegate Of
    function getDynamicHeaders() {
        const t = (key, fallback) => (window.I18n && window.I18n.t(key)) || fallback;
        return [
            { key: 'role', display: t('system.stakeholder.columns.role', 'Role') },
            { key: 'name', display: t('system.stakeholder.columns.name', 'Name') },
            { key: 'department', display: t('system.stakeholder.columns.department', 'Department') },
            { key: 'status', display: t('system.stakeholder.columns.status', 'Status') },
            { key: 'delegateOf', display: t('system.stakeholder.columns.delegateOf', 'Delegate Of') }
        ];
    }

    // Render stakeholders table (with edit capability for admin/superadmin)
    function renderStakeholdersTable() {
        const container = document.getElementById('systemStakeholdersContainer');
        if (!container) return;

        // If user can edit, use the edit component instead
        if (canEditStakeholders && window.SystemStakeholderEdit) {
            console.log('[SystemStakeholderView] User can edit stakeholders, initializing edit mode');
            window.SystemStakeholderEdit.init(currentSystemId, 'original');
            return;
        }

        const headers = getDynamicHeaders();

        const sectionTitle = (window.I18n?.t('system.stakeholder.sections.stakeholders') || 'STAKEHOLDERS');
        let html = `
            <div class="view-section" style="grid-column: 1/-1;">
                <div class="section-title" style="position:relative;">
                    <span>${sectionTitle}</span>
                    <span style="position:absolute;right:0.75rem;top:50%;transform:translateY(-50%);">${window._gridSettingsHtml ? window._gridSettingsHtml('stakeholders') : ''}</span>
                </div>
                <div class="stakeholders-table-container">
                    <table class="stakeholders-table">
        `;

        if (headers.length > 0) {
            html += `
                        <thead>
                            <tr>
                                ${headers.map(header => `<th>${header.display}</th>`).join('')}
                            </tr>
                        </thead>
            `;
        }

        html += `<tbody>`;

        if (stakeholdersData.length === 0) {
            const colspan = headers.length > 0 ? headers.length : 1;
            html += `
                <tr>
                    <td colspan="${colspan}" class="no-data">${window.I18n?.t('system.stakeholder.messages.noStakeholders') || 'No stakeholders found'}</td>
                </tr>
            `;
        } else {
            stakeholdersData.forEach(stakeholder => {
                html += `<tr>`;
                headers.forEach(header => {
                    const value = stakeholder[header.key] || '';
                    if (header.key === 'status') {
                        // Status from roleaccepted.Message (Yes/No or empty)
                        const statusValue = value || '';
                        const statusClass = statusValue.toLowerCase() === 'yes' ? 'status-active' : 'status-inactive';
                        
                        // Check if this is the current user's row and status is "No" or empty/null - make it clickable to accept
                        const isCurrentUser = currentUserId && stakeholder.personId && currentUserId == stakeholder.personId;
                        const statusLower = statusValue.toLowerCase();
                        const isNoOrEmptyStatus = statusLower === 'no' || statusLower === '' || statusValue === '';
                        
                        const notAcceptedText = (window.I18n?.t('system.stakeholder.messages.notAccepted') || 'Not Accepted');
                        if (isCurrentUser && isNoOrEmptyStatus && stakeholder.objectXPeopleId) {
                            // Allow current user to click to accept in any view mode (Original or Changes)
                            const displayText = statusValue || notAcceptedText;
                            html += `<td><span class="status-badge ${statusClass} status-editable" onclick="SystemStakeholderView.toggleUserStatus(${stakeholder.objectXPeopleId})" style="cursor: pointer;" title="Click to accept">${escapeHtml(displayText)}</span></td>`;
                        } else {
                            // Read-only status
                            const displayText = statusValue || notAcceptedText;
                            html += `<td><span class="status-badge ${statusClass}">${escapeHtml(displayText)}</span></td>`;
                        }
                    } else if (header.key === 'name' && stakeholder.personId) {
                        // Create hyperlink for person name
                        html += `<td><a href="/view/people/${encodeURIComponent(stakeholder.personId)}" class="stakeholder-link">${escapeHtml(value)}</a></td>`;
                    } else if (header.key === 'department' && stakeholder.orgUnitId) {
                        // Create hyperlink for org unit
                        html += `<td><a href="/view/org-unit/${encodeURIComponent(stakeholder.orgUnitId)}" class="stakeholder-link">${escapeHtml(value)}</a></td>`;
                    } else if (header.key === 'personId' && value) {
                        // Create hyperlink for person ID
                        html += `<td><a href="/view/people/${encodeURIComponent(value)}" class="stakeholder-link">${escapeHtml(value)}</a></td>`;
                    } else if (header.key === 'orgUnitId' && value) {
                        // Create hyperlink for org unit ID
                        html += `<td><a href="/view/org-unit/${encodeURIComponent(value)}" class="stakeholder-link">${escapeHtml(value)}</a></td>`;
                    } else {
                        html += `<td>${escapeHtml(value)}</td>`;
                    }
                });
                html += `</tr>`;
            });
        }

        html += `
                        </tbody>
                    </table>
                </div>
            </div>
            <div id="systemStakeholderCommunityContainer"></div>
        `;

        container.innerHTML = html;

        // Initialize grid settings dropdown
        if (window._initGridSettings) window._initGridSettings(container);
    }

    // Toggle user status (change from No to Yes) - works in both Original and Changes view
    async function toggleUserStatus(objectXPeopleId) {
        try {
            console.log('Toggling user status for objectXPeopleId:', objectXPeopleId);
            
            // Show loading state
            const statusElement = document.querySelector(`.status-editable[onclick*="${objectXPeopleId}"]`);
            if (statusElement) {
                statusElement.style.opacity = '0.5';
                statusElement.textContent = (window.I18n?.t('system.stakeholder.loading') || 'Loading...');
                statusElement.style.cursor = 'not-allowed';
            }

            // Call API to update AcceptedID from 2 to 1
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/system-stakeholder/${currentSystemId}/stakeholders/accept${viewParam}`, {
                method: 'PATCH',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    objectXPeopleId: objectXPeopleId
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            console.log('Status update result:', result);

            if (result.success) {
                // Reload stakeholders data to show updated status
                await loadStakeholders();
            } else {
                throw new Error(result.error || 'Failed to update status');
            }
        } catch (error) {
            console.error('Error toggling user status:', error);
            const prefix = (window.I18n && window.I18n.t('system.stakeholder.messages.errorUpdatingStatus')) || 'Error updating status';
            const fullMsg = prefix + ': ' + (error.message || 'Unknown error');
            if (typeof window.showNotification === 'function') {
                window.showNotification(fullMsg, 'error');
            } else { alert(fullMsg); }
            
            // Revert loading state
            const statusElement = document.querySelector(`.status-editable[onclick*="${objectXPeopleId}"]`);
            if (statusElement) {
                statusElement.style.opacity = '1';
                const originalStatus = stakeholdersData.find(s => s.objectXPeopleId == objectXPeopleId)?.status || (window.I18n?.t('system.stakeholder.messages.notAccepted') || 'Not Accepted');
                statusElement.textContent = originalStatus;
                statusElement.style.cursor = 'pointer';
            }
        }
    }

    // Escape HTML to prevent XSS
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;');
    }

    // Export functions to global scope
    window.SystemStakeholderView = {
        init: initStakeholderView,
        loadStakeholders: loadStakeholders,
        toggleUserStatus: toggleUserStatus,
        getData: () => stakeholdersData
    };

})();
