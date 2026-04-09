// Legal Entity Stakeholder View JavaScript
(function() {
    let currentLegalEntityId = null;
    let stakeholdersData = [];
    let currentUserId = null;

    // Initialize stakeholder view
    async function initStakeholderView(legalEntityId) {
        currentLegalEntityId = legalEntityId;
        await fetchCurrentUser();
        loadStakeholders();
    }

    // Fetch current user ID from /api/me
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
                console.log('Current user ID:', currentUserId);
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
            const container = document.getElementById('legalEntityStakeholdersContainer');
            if (!container) return;

            const loadingMsg = window.I18n ? window.I18n.t('legalEntity.messages.loadingStakeholders') : 'Loading stakeholders...';
            container.innerHTML = `<div class="loading">${loadingMsg}</div>`;

            // Call API to get stakeholders data
            const response = await fetch(`/api/legal-entity-stakeholder/${currentLegalEntityId}/stakeholders`, {
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
            console.log('Legal Entity Stakeholders API Response:', responseData);

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
            console.log('First item RoleAccepted value:', data.length > 0 ? data[0].RoleAccepted : 'No data');

            // Transform API data to match frontend format
            stakeholdersData = data.map(item => {
                // Handle Status - comes from roleaccepted.Message via AcceptedID join
                let statusValue = '';
                // Debug: log the item to see what we're getting
                console.log('Processing item:', {
                    RoleAccepted: item.RoleAccepted,
                    roleAccepted: item.roleAccepted,
                    status: item.status,
                    allKeys: Object.keys(item)
                });
                
                if (item.RoleAccepted !== null && item.RoleAccepted !== undefined) {
                    statusValue = String(item.RoleAccepted).trim();
                } else if (item.roleAccepted !== null && item.roleAccepted !== undefined) {
                    statusValue = String(item.roleAccepted).trim();
                } else if (item.status !== null && item.status !== undefined) {
                    statusValue = String(item.status).trim();
                }
                
                console.log('Status value extracted:', statusValue);
                
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
        } catch (error) {
            console.error('Error loading stakeholders:', error);
            const container = document.getElementById('legalEntityStakeholdersContainer');
            if (container) {
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.errors.failedToLoadStakeholders', {error: error.message || 'Unknown error'}) : 'Error loading stakeholders data: ' + (error.message || 'Unknown error');
                container.innerHTML = `<div class="error">${errorMsg}</div>`;
            }
        }
    }

    // Get headers - only show Role, Name, Department, Status, Delegate Of
    function getDynamicHeaders() {
        const t = (key, fallback) => (window.I18n && window.I18n.t(key)) || fallback;
        return [
            { key: 'role', display: t('legalEntity.stakeholder.role', 'Role') },
            { key: 'name', display: t('legalEntity.stakeholder.name', 'Name') },
            { key: 'department', display: t('legalEntity.stakeholder.department', 'Department') },
            { key: 'status', display: t('legalEntity.stakeholder.status', 'Status') },
            { key: 'delegateOf', display: t('legalEntity.stakeholder.delegateOf', 'Delegate Of') }
        ];
    }

    // Render stakeholders table (read-only view)
    function renderStakeholdersTable() {
        const container = document.getElementById('legalEntityStakeholdersContainer');
        if (!container) return;

        const headers = getDynamicHeaders();

        let html = `
            <div class="view-section" style="grid-column: 1/-1;">
                <div class="section-title">${window.I18n ? window.I18n.t('legalEntity.stakeholder.sections.stakeholders') : 'LEGAL ENTITY STAKEHOLDERS'}</div>
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
                    <td colspan="${colspan}" class="no-data">${window.I18n ? window.I18n.t('legalEntity.messages.noStakeholdersFound') : 'No stakeholders found'}</td>
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
                        
                        // Check if this is the current user's row and status is "No" or empty/null - make it editable
                        const isCurrentUser = currentUserId && stakeholder.personId && currentUserId == stakeholder.personId;
                        const statusLower = statusValue.toLowerCase();
                        const isNoOrEmptyStatus = statusLower === 'no' || statusLower === '' || statusValue === '';
                        
                        if (isCurrentUser && isNoOrEmptyStatus && stakeholder.objectXPeopleId) {
                            // Make status editable with click handler - use objectXPeopleId for API call
                            const notAccepted = window.I18n ? window.I18n.t('legalEntity.messages.notAccepted') : 'Not Accepted';
                            const clickToAccept = window.I18n ? window.I18n.t('legalEntity.messages.notAccepted') : 'Click to accept';
                            const displayText = statusValue || notAccepted;
                            html += `<td><span class="status-badge ${statusClass} status-editable" onclick="LegalEntityStakeholderView.toggleUserStatus(${stakeholder.objectXPeopleId})" style="cursor: pointer;" title="${clickToAccept}">${escapeHtml(displayText)}</span></td>`;
                        } else {
                            // Read-only status
                            const notAccepted = window.I18n ? window.I18n.t('legalEntity.messages.notAccepted') : 'Not Accepted';
                            const displayText = statusValue || notAccepted;
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
            <div id="legalEntityStakeholderCommunityContainer"></div>
        `;

        container.innerHTML = html;
            
            // Initialize stakeholder community section after main stakeholders table is rendered
            if (window.LegalStakeholderCommunity) {
                window.LegalStakeholderCommunity.init(currentLegalEntityId);
            }
    }

    // Toggle user status (change from No to Yes)
    async function toggleUserStatus(objectXPeopleId) {
        try {
            console.log('Toggling user status for objectXPeopleId:', objectXPeopleId);
            
            // Show loading state
            const statusElement = document.querySelector(`.status-editable[onclick*="${objectXPeopleId}"]`);
            if (statusElement) {
                statusElement.style.opacity = '0.5';
                const loadingMsg = window.I18n ? window.I18n.t('legalEntity.messages.loading') : 'Loading...';
                statusElement.textContent = loadingMsg;
                statusElement.style.cursor = 'not-allowed';
            }

            // Call API to update AcceptedID from 2 to 1
            const response = await fetch(`/api/legal-entity-stakeholder/${currentLegalEntityId}/stakeholders/accept`, {
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
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.errors.failedToSaveStakeholders', {error: error.message || 'Unknown error'}) : 'Error updating status: ' + (error.message || 'Unknown error');
            alert(errorMsg);
            
            // Revert loading state
            const statusElement = document.querySelector(`.status-editable[onclick*="${objectXPeopleId}"]`);
            if (statusElement) {
                statusElement.style.opacity = '1';
                const notAccepted = window.I18n ? window.I18n.t('legalEntity.messages.notAccepted') : 'Not Accepted';
                const originalStatus = stakeholdersData.find(s => s.objectXPeopleId == objectXPeopleId)?.status || notAccepted;
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
    window.LegalEntityStakeholderView = {
        init: initStakeholderView,
        loadStakeholders: loadStakeholders,
        toggleUserStatus: toggleUserStatus,
        getData: () => stakeholdersData
    };

})();
