// Dataset Stakeholder View JavaScript
(function() {
    let currentDatasetId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'
    let stakeholdersData = [];
    let attributeStakeholdersData = [];
    let attributesData = [];
    let currentUserId = null;
    let currentUserRole = null; // 'admin', 'superadmin', or null
    let canEditStakeholders = false;

    // Initialize stakeholder view
    async function initStakeholderView(datasetId, viewMode = 'original') {
        console.log('Initializing stakeholder view for dataset ID:', datasetId);
        currentDatasetId = datasetId;
        currentViewMode = viewMode || 'original';
        await fetchCurrentUser();
        
        // If user can edit, use the edit component instead
        if (canEditStakeholders && window.DatasetStakeholderEdit) {
            console.log('[DatasetStakeholderView] User can edit stakeholders, initializing edit mode');
            window.DatasetStakeholderEdit.init(currentDatasetId, 'original');
            return;
        }
        
        loadStakeholders();
        loadAttributes();
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
                        const permResp = await fetch('/api/user/permissions/Data Sets', {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (permResp.ok) {
                            const permData = await permResp.json();
                            hasRoleEditPermission = permData.success && (permData.canEdit === true || permData.isAdmin === true);
                            console.log('[DatasetStakeholderView] Role edit permission:', hasRoleEditPermission);
                        }
                    } catch (permError) {
                        console.warn('[DatasetStakeholderView] Error checking permissions:', permError);
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

    // Load attributes data
    async function loadAttributes() {
        try {
            console.log('Loading attributes for dataset:', currentDatasetId);
            
            const response = await fetch(`/api/Attribute/stakeholder/lookup?type=attributes&datasetId=${currentDatasetId}`, {
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
            console.log('Attributes API Response for dataset', currentDatasetId, ':', responseData);

            // Handle different API response formats
            let data;
            if (Array.isArray(responseData)) {
                data = responseData;
            } else if (responseData.data && Array.isArray(responseData.data)) {
                data = responseData.data;
            } else {
                console.warn('No attributes array found in API response, using empty array');
                data = [];
            }

            console.log('Extracted attributes data:', data);

            // Transform attributes data to simple format
            attributesData = data.map(item => ({
                id: item.AttributeID || item.id,
                name: item.AttributeName || item.name || '',
                createdById: item.CreatedBy_ID || item.createdById || item.createdBy_ID || null
            }));

            // Load attribute stakeholders for each attribute
            if (attributesData.length > 0) {
                await loadAttributeStakeholders();
            } else {
                // No attributes found, still render the table
                renderStakeholdersTable();
            }
            
        } catch (error) {
            console.error('Error loading attributes:', error);
            attributesData = [];
            // Still render the table even if there's an error
            renderStakeholdersTable();
        }
    }

    // Load attribute stakeholders data
    async function loadAttributeStakeholders() {
        try {
            console.log('Loading attribute stakeholders for', attributesData.length, 'attributes');
            
            if (attributesData.length === 0) {
                console.log('No attributes to load stakeholders for');
                renderStakeholdersTable();
                return;
            }
            
            const attributeStakeholdersPromises = attributesData.map(async (attribute) => {
                try {
                    const response = await fetch(`/api/Attribute-stakeholder/${attribute.id}/stakeholders`, {
                        method: 'GET',
                        credentials: 'include',
                        headers: {
                            'Content-Type': 'application/json'
                        }
                    });

                    if (!response.ok) {
                        console.warn(`Failed to load stakeholders for attribute ${attribute.id}: ${response.status}`);
                        return [];
                    }

                    const responseData = await response.json();

                    // Handle different API response formats
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
                        console.warn(`No stakeholders array found for attribute ${attribute.id}, using empty array`);
                        data = [];
                    }

                    // Transform API data to match frontend format with attribute info
                    // Filter out the attribute creator from stakeholders
                    const attributeCreatedById = attribute.createdById;
                    return data
                        .map(item => {
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
                            
                            // Get personId for filtering
                            const personId = item.PersonID || item.personId || item.PeopleID || item.peopleId || '';
                            
                            return {
                                id: objectXPeopleId || Math.random().toString(36).substr(2, 9),
                                objectXPeopleId: objectXPeopleId, // Store separately for API calls
                                attributeId: attribute.id,
                                attributeName: attribute.name,
                                role: item.RoleName || item.role || '',
                                name: item.PersonName || item.name || '',
                                department: item.OrgUnit || item.Department || item.department || '',
                                status: statusValue,
                                delegateOf: item.DelegateOf || item.delegateOf || item.delegate_of || item.delegate_name || '-',
                                // Add IDs for linking (not shown in table but used for hyperlinks)
                                personId: personId,
                                orgUnitId: item.OrgUnitID || item.orgUnitId || item.OrgUnit_ID || item.org_unit_id || ''
                            };
                        })
                        .filter(stakeholder => {
                            // Exclude the attribute creator from the stakeholder list
                            if (attributeCreatedById != null && stakeholder.personId) {
                                return String(stakeholder.personId) !== String(attributeCreatedById);
                            }
                            return true;
                        });
                } catch (error) {
                    console.error(`Error loading stakeholders for attribute ${attribute.id}:`, error);
                    return [];
                }
            });

            const allAttributeStakeholders = await Promise.all(attributeStakeholdersPromises);
            attributeStakeholdersData = allAttributeStakeholders.flat();
            
            console.log('Loaded attribute stakeholders:', attributeStakeholdersData);
            // Always render the table after loading attribute stakeholders
            renderStakeholdersTable();
            
        } catch (error) {
            console.error('Error loading attribute stakeholders:', error);
            attributeStakeholdersData = [];
            // Still render the table even if there's an error
            renderStakeholdersTable();
        }
    }

    // Load stakeholders data
    async function loadStakeholders() {
        try {
            console.log('Loading stakeholders for dataset ID:', currentDatasetId);
            const container = document.getElementById('datasetStakeholdersContainer');
            if (!container) {
                console.error('Container datasetStakeholdersContainer not found!');
                return;
            }

            container.innerHTML = '<div class="loading">Loading stakeholders...</div>';

            // Call API to get stakeholders data
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            console.log('Fetching from URL:', `/api/dataset-stakeholder/${currentDatasetId}/stakeholders${viewParam}`);
            const response = await fetch(`/api/dataset-stakeholder/${currentDatasetId}/stakeholders${viewParam}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            console.log('Response status:', response.status);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const responseData = await response.json();
            console.log('Dataset Stakeholders API Response for dataset', currentDatasetId, ':', responseData);

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

            // Transform API data to match frontend format (Attribute, Role, Name, Department, Status)
            // Status comes from roleaccepted.Message via object_x_people.AcceptedID
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
                    attributeId: null, // Dataset stakeholders don't have attribute
                    attributeName: 'Dataset', // Label for dataset-level stakeholders
                    role: item.RoleName || item.role || '',
                    name: item.PersonName || item.name || '',
                    department: item.OrgUnit || item.Department || item.department || '',
                    status: statusValue,
                    delegateOf: item.DelegateOf || item.delegateOf || item.delegate_of || item.delegate_name || '-',
                    // Add IDs for linking (not shown in table but used for hyperlinks)
                    personId: item.PersonID || item.personId || item.PeopleID || item.peopleId || '',
                    orgUnitId: item.OrgUnitID || item.orgUnitId || item.OrgUnit_ID || item.org_unit_id || ''
                };
            });

            // Always render the table after loading stakeholders
            console.log('Dataset stakeholders loaded:', stakeholdersData.length);
            console.log('Final stakeholders data:', stakeholdersData);
            renderStakeholdersTable();
        } catch (error) {
            console.error('Error loading stakeholders:', error);
            stakeholdersData = [];
            // Still render the table even if there's an error
            renderStakeholdersTable();
        }
    }

    // Get headers for dataset stakeholders (Role, Name, Department, Status, Delegate Of)
    function getDatasetHeaders() {
        return [
            { key: 'role', display: (window.I18n?.t('dataset.stakeholder.columns.role') || 'Role') },
            { key: 'name', display: (window.I18n?.t('dataset.stakeholder.columns.name') || 'Name') },
            { key: 'department', display: (window.I18n?.t('dataset.stakeholder.columns.department') || 'Department') },
            { key: 'status', display: (window.I18n?.t('dataset.stakeholder.columns.status') || 'Status') },
            { key: 'delegateOf', display: (window.I18n?.t('dataset.stakeholder.columns.delegateOf') || 'Delegate Of') }
        ];
    }

    // Get headers for attribute stakeholders (Attribute, Role, Name, Department, Status, Delegate Of)
    function getAttributeHeaders() {
        return [
            { key: 'attributeName', display: (window.I18n?.t('dataset.stakeholder.columns.attribute') || 'Attribute') },
            { key: 'role', display: (window.I18n?.t('dataset.stakeholder.columns.role') || 'Role') },
            { key: 'name', display: (window.I18n?.t('dataset.stakeholder.columns.name') || 'Name') },
            { key: 'department', display: (window.I18n?.t('dataset.stakeholder.columns.department') || 'Department') },
            { key: 'status', display: (window.I18n?.t('dataset.stakeholder.columns.status') || 'Status') },
            { key: 'delegateOf', display: (window.I18n?.t('dataset.stakeholder.columns.delegateOf') || 'Delegate Of') }
        ];
    }

    // Render stakeholders table (read-only view)
    function renderStakeholdersTable() {
        const container = document.getElementById('datasetStakeholdersContainer');
        if (!container) {
            console.error('Container datasetStakeholdersContainer not found!');
            return;
        }
        
        console.log('Rendering stakeholders table with:', {
            stakeholdersData: stakeholdersData.length,
            attributeStakeholdersData: attributeStakeholdersData.length
        });

        const datasetHeaders = getDatasetHeaders();

        // Render Dataset Stakeholders Table
        let datasetHtml = `
            <div class="view-section" style="grid-column: 1/-1;">
                <div class="section-title">
                    <i class="fas fa-users" style="margin-right: 0.5rem;"></i>
                    ${window.I18n?.t('dataset.stakeholder.sections.datasetStakeholders') || 'DATASET STAKEHOLDERS'}
                </div>
                <div class="stakeholders-table-container">
                    <table class="stakeholders-table">
        `;

        if (datasetHeaders.length > 0) {
            datasetHtml += `
                        <thead>
                            <tr>
                                ${datasetHeaders.map(header => `<th>${header.display}</th>`).join('')}
                            </tr>
                        </thead>
            `;
        }

        datasetHtml += `<tbody>`;

        if (stakeholdersData.length === 0) {
            const colspan = datasetHeaders.length > 0 ? datasetHeaders.length : 1;
            datasetHtml += `
                <tr>
                    <td colspan="${colspan}" class="no-data">
                        <i class="fas fa-users-slash" style="font-size: 2rem; opacity: 0.3; margin-bottom: 0.5rem; display: block;"></i>
                        ${window.I18n?.t('dataset.stakeholder.messages.noDatasetStakeholders') || 'No dataset stakeholders found'}
                    </td>
                </tr>
            `;
        } else {
            stakeholdersData.forEach(stakeholder => {
                datasetHtml += `<tr>`;
                datasetHeaders.forEach(header => {
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
                            const displayText = statusValue || (window.I18n?.t('dataset.stakeholder.messages.notAccepted') || 'Not Accepted');
                            datasetHtml += `<td><span class="status-badge ${statusClass} status-editable" onclick="DatasetStakeholderView.toggleUserStatus(${stakeholder.objectXPeopleId})" style="cursor: pointer;" title="Click to accept">${escapeHtml(displayText)}</span></td>`;
                        } else {
                            // Read-only status
                            const displayText = statusValue || (window.I18n?.t('dataset.stakeholder.messages.notAccepted') || 'Not Accepted');
                            datasetHtml += `<td><span class="status-badge ${statusClass}">${escapeHtml(displayText)}</span></td>`;
                        }
                    } else if (header.key === 'name' && stakeholder.personId) {
                        // Create hyperlink for person name
                        datasetHtml += `<td><a href="/view/people/${encodeURIComponent(stakeholder.personId)}" class="stakeholder-link">${escapeHtml(value)}</a></td>`;
                    } else if (header.key === 'department' && stakeholder.orgUnitId) {
                        // Create hyperlink for org unit
                        datasetHtml += `<td><a href="/view/org-unit/${encodeURIComponent(stakeholder.orgUnitId)}" class="stakeholder-link">${escapeHtml(value)}</a></td>`;
                    } else if (header.key === 'personId' && value) {
                        // Create hyperlink for person ID
                        datasetHtml += `<td><a href="/view/people/${encodeURIComponent(value)}" class="stakeholder-link">${escapeHtml(value)}</a></td>`;
                    } else {
                        datasetHtml += `<td>${escapeHtml(value)}</td>`;
                    }
                });
                datasetHtml += `</tr>`;
            });
        }

        datasetHtml += `
                        </tbody>
                    </table>
                </div>
            </div>
        `;

        // Render Attribute Stakeholders Table
        const attributeHeaders = getAttributeHeaders();
        let attributeHtml = `
            <div class="view-section" style="grid-column: 1/-1;">
                <div class="section-title">
                    <i class="fas fa-users" style="margin-right: 0.5rem;"></i>
                    ${window.I18n?.t('dataset.stakeholder.sections.attributeStakeholders') || 'ATTRIBUTE STAKEHOLDERS'}
                </div>
                <div class="stakeholders-table-container">
                    <table class="stakeholders-table">
        `;

        if (attributeHeaders.length > 0) {
            attributeHtml += `
                        <thead>
                            <tr>
                                ${attributeHeaders.map(header => `<th>${header.display}</th>`).join('')}
                            </tr>
                        </thead>
            `;
        }

        attributeHtml += `<tbody>`;

        if (attributeStakeholdersData.length === 0) {
            const colspan = attributeHeaders.length > 0 ? attributeHeaders.length : 1;
            attributeHtml += `
                <tr>
                    <td colspan="${colspan}" class="no-data">
                        <i class="fas fa-users-slash" style="font-size: 2rem; opacity: 0.3; margin-bottom: 0.5rem; display: block;"></i>
                        ${window.I18n?.t('dataset.stakeholder.messages.noAttributeStakeholders') || 'No attribute stakeholders found'}
                    </td>
                </tr>
            `;
        } else {
            attributeStakeholdersData.forEach(stakeholder => {
                attributeHtml += `<tr>`;
                attributeHeaders.forEach(header => {
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
                            const displayText = statusValue || (window.I18n?.t('dataset.stakeholder.messages.notAccepted') || 'Not Accepted');
                            attributeHtml += `<td><span class="status-badge ${statusClass} status-editable" onclick="DatasetStakeholderView.toggleAttributeUserStatus(${stakeholder.objectXPeopleId})" style="cursor: pointer;" title="Click to accept">${escapeHtml(displayText)}</span></td>`;
                        } else {
                            // Read-only status
                            const displayText = statusValue || (window.I18n?.t('dataset.stakeholder.messages.notAccepted') || 'Not Accepted');
                            attributeHtml += `<td><span class="status-badge ${statusClass}">${escapeHtml(displayText)}</span></td>`;
                        }
                    } else if (header.key === 'name' && stakeholder.personId) {
                        // Create hyperlink for person name
                        attributeHtml += `<td><a href="/view/people/${encodeURIComponent(stakeholder.personId)}" class="stakeholder-link">${escapeHtml(value)}</a></td>`;
                    } else if (header.key === 'department' && stakeholder.orgUnitId) {
                        // Create hyperlink for org unit
                        attributeHtml += `<td><a href="/view/org-unit/${encodeURIComponent(stakeholder.orgUnitId)}" class="stakeholder-link">${escapeHtml(value)}</a></td>`;
                    } else if (header.key === 'personId' && value) {
                        // Create hyperlink for person ID
                        attributeHtml += `<td><a href="/view/people/${encodeURIComponent(value)}" class="stakeholder-link">${escapeHtml(value)}</a></td>`;
                    } else {
                        attributeHtml += `<td>${escapeHtml(value)}</td>`;
                    }
                });
                attributeHtml += `</tr>`;
            });
        }

        attributeHtml += `
                        </tbody>
                    </table>
                </div>
            </div>
        `;

        // Combine both tables and add community container
        container.innerHTML = datasetHtml + attributeHtml + `<div id="datasetStakeholderCommunityContainer"></div>`;
        
        // Initialize stakeholder community section after main stakeholders table is rendered
        if (window.DatasetStakeholderCommunity) {
            window.DatasetStakeholderCommunity.init(currentDatasetId);
        }
    }

    // Toggle user status for dataset stakeholders (change from No to Yes)
    async function toggleUserStatus(objectXPeopleId) {
        try {
            console.log('Toggling user status for objectXPeopleId:', objectXPeopleId);
            
            // Show loading state
            const statusElement = document.querySelector(`.status-editable[onclick*="${objectXPeopleId}"]`);
            if (statusElement) {
                statusElement.style.opacity = '0.5';
                statusElement.textContent = 'Loading...';
                statusElement.style.cursor = 'not-allowed';
            }

            // Call API to update AcceptedID from 2 to 1
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/dataset-stakeholder/${currentDatasetId}/stakeholders/accept${viewParam}`, {
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
            alert('Error updating status: ' + (error.message || 'Unknown error'));
            
            // Revert loading state
            const statusElement = document.querySelector(`.status-editable[onclick*="${objectXPeopleId}"]`);
            if (statusElement) {
                statusElement.style.opacity = '1';
                const originalStatus = stakeholdersData.find(s => s.objectXPeopleId == objectXPeopleId)?.status || 'Not Accepted';
                statusElement.textContent = originalStatus;
                statusElement.style.cursor = 'pointer';
            }
        }
    }

    // Toggle user status for attribute stakeholders (change from No to Yes)
    async function toggleAttributeUserStatus(objectXPeopleId) {
        try {
            console.log('Toggling attribute user status for objectXPeopleId:', objectXPeopleId);
            
            // Show loading state
            const statusElement = document.querySelector(`.status-editable[onclick*="${objectXPeopleId}"]`);
            if (statusElement) {
                statusElement.style.opacity = '0.5';
                statusElement.textContent = 'Loading...';
                statusElement.style.cursor = 'not-allowed';
            }

            // Call API to update AcceptedID from 2 to 1 for attribute stakeholder
            const response = await fetch(`/api/Attribute-stakeholder/${currentDatasetId}/stakeholders/accept`, {
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
                // Reload attribute stakeholders data to show updated status
                await loadAttributeStakeholders();
            } else {
                throw new Error(result.error || 'Failed to update status');
            }
        } catch (error) {
            console.error('Error toggling attribute user status:', error);
            alert('Error updating status: ' + (error.message || 'Unknown error'));
            
            // Revert loading state
            const statusElement = document.querySelector(`.status-editable[onclick*="${objectXPeopleId}"]`);
            if (statusElement) {
                statusElement.style.opacity = '1';
                const originalStatus = attributeStakeholdersData.find(s => s.objectXPeopleId == objectXPeopleId)?.status || 'Not Accepted';
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
    window.DatasetStakeholderView = {
        init: initStakeholderView,
        loadStakeholders: loadStakeholders,
        loadAttributes: loadAttributes,
        loadAttributeStakeholders: loadAttributeStakeholders,
        toggleUserStatus: toggleUserStatus,
        toggleAttributeUserStatus: toggleAttributeUserStatus,
        getData: () => stakeholdersData,
        getAttributeData: () => attributeStakeholdersData,
        getAllData: () => [...stakeholdersData, ...attributeStakeholdersData]
    };

})();
