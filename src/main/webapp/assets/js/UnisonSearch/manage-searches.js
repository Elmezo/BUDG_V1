// Manage Searches Page
// Handles the full Manage Searches page with Edit, Modify Sharing, and table display

// Namespace to avoid conflicts with other files
const manageSearchesState = {
    mySearchesCache: [],
    sharedSearchesCache: [],
    editingSearchId: null,
    currentSharingSearchId: null,
    selectedUsersForSharing: []
};

function msTr(key, params) {
    if (window.I18n && typeof window.I18n.t === 'function') {
        return window.I18n.t(key, params);
    }
    return key;
}

function applyManageSearchesPageI18n() {
    if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
        window.I18n.applyTranslations(document.body);
    }
    if (window.I18n && typeof window.I18n.t === 'function') {
        document.title = window.I18n.t('manageSearches.pageTitle');
    }
}

/**
 * Initialize Manage Searches page
 */
function initManageSearches() {
    // Load searches on page load
    loadMySearches();
    loadSharedSearches();

    // Setup event listeners
    setupEventListeners();
}

/**
 * Setup event listeners
 */
function setupEventListeners() {
    // Save buttons
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');

    if (saveBtn) {
        saveBtn.addEventListener('click', () => {
            if (manageSearchesState.editingSearchId) {
                saveSearch(manageSearchesState.editingSearchId, false);
            }
        });
    }

    if (saveAndCloseBtn) {
        saveAndCloseBtn.addEventListener('click', () => {
            if (manageSearchesState.editingSearchId) {
                saveSearch(manageSearchesState.editingSearchId, true);
            }
        });
    }

    if (closeBtn) {
        closeBtn.addEventListener('click', () => {
            if (manageSearchesState.editingSearchId) {
                if (confirm(msTr('manageSearches.confirmUnsavedClose'))) {
                    cancelEdit();
                    window.location.href = '/search.html';
                }
            } else {
                window.location.href = '/search.html';
            }
        });
    }

    // Modify Sharing modal radio buttons
    const shareOptions = document.querySelectorAll('input[name="sharing-option"]');
    shareOptions.forEach(option => {
        option.addEventListener('change', (e) => {
            const selectedUsersContainer = document.getElementById('selected-users-container');
            if (e.target.value === 'selected') {
                selectedUsersContainer.style.display = 'block';
                // Setup user search input when showing the container
                setTimeout(() => {
                    setupUserSearchInput();
                }, 100);
            } else {
                selectedUsersContainer.style.display = 'none';
            }
        });
    });
}

/**
 * Switch between tabs
 */
function switchTab(tabName) {
    // Update tab buttons
    document.querySelectorAll('.manage-searches-tab').forEach(tab => {
        tab.classList.remove('active');
    });
    document.querySelector(`[data-tab="${tabName}"]`).classList.add('active');

    // Update tab content
    document.querySelectorAll('.manage-searches-tab-content').forEach(content => {
        content.style.display = 'none';
    });
    document.getElementById(`${tabName}-content`).style.display = 'block';

    // Cancel any editing
    if (manageSearchesState.editingSearchId) {
        cancelEdit();
    }
}

/**
 * Load my searches
 */
async function loadMySearches() {
    const tbody = document.getElementById('my-searches-tbody');
    if (!tbody) return;

    try {
        const response = await fetch('/api/search/my', {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            // Try to get error message
            let errorMessage = msTr('manageSearches.errorLoadMy');
            try {
                const errorData = await response.json();
                errorMessage = errorData.error || errorMessage;
            } catch (e) {
                // Ignore JSON parse error
            }
            
            // If 401, user not logged in
            if (response.status === 401) {
                tbody.innerHTML = '<tr><td colspan="6" class="manage-searches-error">' + escapeHtml(msTr('manageSearches.pleaseLoginMy')) + '</td></tr>';
                return;
            }
            
            throw new Error(errorMessage);
        }

        const result = await response.json();
        if (result.success) {
            // Handle both array and object with data property
            const data = Array.isArray(result.data) ? result.data : (result.data || []);
            manageSearchesState.mySearchesCache = data;
            renderMySearchesTable(data);
        } else {
            // If not successful but no error, show empty
            manageSearchesState.mySearchesCache = [];
            renderMySearchesTable([]);
        }
    } catch (error) {
        console.error('Error loading my searches:', error);
        tbody.innerHTML = '<tr><td colspan="6" class="manage-searches-error">' + escapeHtml(msTr('manageSearches.errorLoading', { message: error.message })) + '</td></tr>';
    }
}

/**
 * Load shared searches
 */
async function loadSharedSearches() {
    const tbody = document.getElementById('shared-searches-tbody');
    if (!tbody) return;

    try {
        const response = await fetch('/api/search/shared', {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            // Try to get error message
            let errorMessage = msTr('manageSearches.errorLoadShared');
            try {
                const errorData = await response.json();
                errorMessage = errorData.error || errorMessage;
            } catch (e) {
                // Ignore JSON parse error
            }
            
            // If 401, user not logged in
            if (response.status === 401) {
                tbody.innerHTML = '<tr><td colspan="6" class="manage-searches-error">' + escapeHtml(msTr('manageSearches.pleaseLoginShared')) + '</td></tr>';
                return;
            }
            
            throw new Error(errorMessage);
        }

        const result = await response.json();
        if (result.success) {
            // Handle both array and object with data property
            const data = Array.isArray(result.data) ? result.data : (result.data || []);
            manageSearchesState.sharedSearchesCache = data;
            renderSharedSearchesTable(data);
        } else {
            // If not successful but no error, show empty
            manageSearchesState.sharedSearchesCache = [];
            renderSharedSearchesTable([]);
        }
    } catch (error) {
        console.error('Error loading shared searches:', error);
        tbody.innerHTML = '<tr><td colspan="6" class="manage-searches-error">' + escapeHtml(msTr('manageSearches.errorLoading', { message: error.message })) + '</td></tr>';
    }
}

/**
 * Render my searches table
 */
function renderMySearchesTable(searches) {
    const tbody = document.getElementById('my-searches-tbody');
    if (!tbody) return;

    if (searches.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="manage-searches-empty">' + escapeHtml(msTr('manageSearches.emptyMy')) + '</td></tr>';
        document.getElementById('my-searches-count').textContent = msTr('manageSearches.recordsZero');
        return;
    }

    let html = '';
    searches.forEach(search => {
        const sharingStatus = getSharingStatus(search);
        const sharedWith = getSharedWithUsers(search.id);
        const createdDate = formatDateForTable(search.created_at);

        html += `
            <tr data-search-id="${search.id}" ${manageSearchesState.editingSearchId === search.id ? 'class="editing"' : ''}>
                <td>
                    <a href="javascript:void(0)" onclick="openSearch(${search.id})" class="search-name-link">
                        ${escapeHtml(search.name || msTr('manageSearches.untitled'))}
                    </a>
                </td>
                <td>
                    ${manageSearchesState.editingSearchId === search.id 
                        ? `<textarea class="editable-description" id="edit-desc-${search.id}">${escapeHtml(search.description || '')}</textarea>`
                        : `<span class="search-description-text">${escapeHtml(search.description || '')}</span>`
                    }
                </td>
                <td>
                    <div class="sharing-status">
                        ${getSharingIcon(sharingStatus)}
                        <span>${sharingStatus}</span>
                    </div>
                </td>
                <td>
                    ${sharedWith || '-'}
                </td>
                <td>${createdDate}</td>
                <td>
                    <div class="manage-searches-row-actions">
                        ${manageSearchesState.editingSearchId === search.id 
                            ? `<button class="action-btn" onclick="cancelEdit()" title="${escapeHtml(msTr('manageSearches.titleCancel'))}"><i class="fas fa-times"></i></button>`
                            : `
                                <button class="action-btn" onclick="editSearch(${search.id})" title="${escapeHtml(msTr('manageSearches.titleEdit'))}"><i class="fas fa-edit"></i></button>
                                <button class="action-btn" onclick="showModifySharingModal(${search.id})" title="${escapeHtml(msTr('manageSearches.titleModifySharing'))}"><i class="fas fa-share-alt"></i></button>
                                <button class="action-btn" onclick="copySearch(${search.id})" title="${escapeHtml(msTr('manageSearches.titleCopy'))}"><i class="fas fa-copy"></i></button>
                                <button class="action-btn action-btn-danger" onclick="deleteSearch(${search.id})" title="${escapeHtml(msTr('manageSearches.titleDelete'))}"><i class="fas fa-trash"></i></button>
                            `
                        }
                    </div>
                </td>
            </tr>
        `;
    });

    tbody.innerHTML = html;
    document.getElementById('my-searches-count').textContent = searches.length === 1
        ? msTr('manageSearches.recordsOne')
        : msTr('manageSearches.recordsCount', { count: searches.length });
}

/**
 * Render shared searches table
 */
function renderSharedSearchesTable(searches) {
    const tbody = document.getElementById('shared-searches-tbody');
    if (!tbody) return;

    if (searches.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="manage-searches-empty">' + escapeHtml(msTr('manageSearches.emptyShared')) + '</td></tr>';
        document.getElementById('shared-searches-count').textContent = msTr('manageSearches.recordsZero');
        return;
    }

    let html = '';
    searches.forEach(search => {
        // Calculate sharing status directly from search object (same as owner sees)
        const sharingStatus = getSharingStatusFromSearch(search);
        const sharedBy = getSharedByUser(search);
        const createdDate = formatDateForTable(search.created_at);

        html += `
            <tr data-search-id="${search.id}">
                <td>
                    <a href="javascript:void(0)" onclick="openSearch(${search.id})" class="search-name-link">
                        ${escapeHtml(search.name || msTr('manageSearches.untitled'))}
                    </a>
                </td>
                <td>
                    <span class="search-description-text">${escapeHtml(search.description || '')}</span>
                </td>
                <td>
                    <div class="sharing-status">
                        ${getSharingIcon(sharingStatus)}
                        <span>${sharingStatus}</span>
                    </div>
                </td>
                <td>${sharedBy}</td>
                <td>${createdDate}</td>
                <td>
                    <div class="manage-searches-row-actions">
                        <button class="action-btn" onclick="copySearch(${search.id})" title="${escapeHtml(msTr('manageSearches.titleCopy'))}"><i class="fas fa-copy"></i></button>
                    </div>
                </td>
            </tr>
        `;
    });

    tbody.innerHTML = html;
    document.getElementById('shared-searches-count').textContent = searches.length === 1
        ? msTr('manageSearches.recordsOne')
        : msTr('manageSearches.recordsCount', { count: searches.length });
}

/**
 * Get sharing status
 */
function getSharingStatus(search) {
    if (search.is_public) {
        return msTr('manageSearches.sharingPublic');
    }
    // Check if shared with specific users
    const sharedWith = getSharedWithUsers(search.id);
    if (sharedWith) {
        return msTr('manageSearches.sharingLimited');
    }
    return msTr('manageSearches.sharingNotShared');
}

/**
 * Get sharing status from search object directly (for shared searches)
 * This ensures shared searches show the same sharing status as the owner sees
 */
function getSharingStatusFromSearch(search) {
    if (search.is_public) {
        return msTr('manageSearches.sharingPublic');
    }
    // Check if shared with specific users (from shared_users array)
    if (search.shared_users && Array.isArray(search.shared_users) && search.shared_users.length > 0) {
        return msTr('manageSearches.sharingLimited');
    }
    return msTr('manageSearches.sharingNotShared');
}

/**
 * Get sharing icon
 */
function getSharingIcon(status) {
    const pub = msTr('manageSearches.sharingPublic');
    const lim = msTr('manageSearches.sharingLimited');
    const not = msTr('manageSearches.sharingNotShared');
    switch (status) {
        case pub:
            return '<i class="fas fa-users"></i>';
        case lim:
            return '<i class="fas fa-user-friends"></i>';
        case not:
        default:
            return '<i class="fas fa-user-slash"></i>';
    }
}

/**
 * Get shared with users (for Limited sharing)
 */
function getSharedWithUsers(searchId) {
    const search = manageSearchesState.mySearchesCache.find(s => s.id === searchId);
    if (!search) return '';
    
    if (search.shared_users && Array.isArray(search.shared_users) && search.shared_users.length > 0) {
        return search.shared_users.map(u => u.name || `${u.firstName || ''} ${u.lastName || ''}`.trim() || u.email || msTr('manageSearches.unknown')).join(', ');
    }
    
    return '';
}

/**
 * Get shared by user
 */
function getSharedByUser(search) {
    // Use shared_by from backend data
    return search.shared_by || msTr('manageSearches.unknownUser');
}

/**
 * Format date for table
 */
function formatDateForTable(dateString) {
    if (!dateString) return '-';
    try {
        const date = new Date(dateString);
        const locale = (window.I18n && window.I18n.getLocale && window.I18n.getLocale() === 'ar') ? 'ar' : 'en-GB';
        return date.toLocaleDateString(locale, {
            day: '2-digit',
            month: 'short',
            year: 'numeric'
        });
    } catch (e) {
        return dateString;
    }
}

/**
 * Edit search
 */
function editSearch(searchId) {
    // Cancel any existing edit
    if (manageSearchesState.editingSearchId && manageSearchesState.editingSearchId !== searchId) {
        cancelEdit();
    }

    manageSearchesState.editingSearchId = searchId;
    
    // Show save buttons
    document.getElementById('saveBtn').style.display = 'inline-block';
    document.getElementById('saveAndCloseBtn').style.display = 'inline-block';

    // Re-render table to show edit mode
    renderMySearchesTable(manageSearchesState.mySearchesCache);

    // Focus on description textarea
    setTimeout(() => {
        const textarea = document.getElementById(`edit-desc-${searchId}`);
        if (textarea) {
            textarea.focus();
            textarea.setSelectionRange(textarea.value.length, textarea.value.length);
        }
    }, 100);
}

/**
 * Cancel edit
 */
function cancelEdit() {
    manageSearchesState.editingSearchId = null;
    
    // Hide save buttons
    document.getElementById('saveBtn').style.display = 'none';
    document.getElementById('saveAndCloseBtn').style.display = 'none';

    // Re-render table
    renderMySearchesTable(manageSearchesState.mySearchesCache);
}

/**
 * Save search
 */
async function saveSearch(searchId, closeAfterSave) {
    const textarea = document.getElementById(`edit-desc-${searchId}`);
    if (!textarea) return;

    const newDescription = textarea.value.trim();
    const search = manageSearchesState.mySearchesCache.find(s => s.id === searchId);
    if (!search) return;

    try {
        const response = await fetch(`/api/search/${searchId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({
                name: search.name,
                description: newDescription,
                conditionDefinition: search.condition_definition,
                isPublic: search.is_public
            })
        });

        if (!response.ok) {
            throw new Error('Failed to save search');
        }

        const result = await response.json();
        if (result.success) {
            // Update cache
            search.description = newDescription;
            
            // Cancel edit mode
            cancelEdit();
            
            // Reload to refresh
            await loadMySearches();

            if (closeAfterSave) {
                window.location.href = '/search.html';
            }
        }
    } catch (error) {
        console.error('Error saving search:', error);
        alert(msTr('manageSearches.errorSaveSearch', { message: error.message }));
    }
}

/**
 * Show Modify Sharing modal
 */
async function showModifySharingModal(searchId) {
    manageSearchesState.currentSharingSearchId = searchId;
    const search = manageSearchesState.mySearchesCache.find(s => s.id === searchId);
    if (!search) return;

    const modal = document.getElementById('modify-sharing-modal');
    if (!modal) return;

    // Reset form
    document.getElementById('share-public').checked = false;
    document.getElementById('share-selected').checked = false;
    document.getElementById('stop-sharing').checked = false;
    document.getElementById('selected-users-container').style.display = 'none';
    manageSearchesState.selectedUsersForSharing = [];
    renderSelectedUsers();

    // Set current sharing status
    if (search.is_public) {
        document.getElementById('share-public').checked = true;
    } else {
        // Load shared users from backend
        const sharedUsers = await loadSharedUsers(searchId);
        if (sharedUsers && sharedUsers.length > 0) {
            document.getElementById('share-selected').checked = true;
            document.getElementById('selected-users-container').style.display = 'block';
            manageSearchesState.selectedUsersForSharing = sharedUsers;
            renderSelectedUsers();
        } else {
            document.getElementById('stop-sharing').checked = true;
        }
    }

    // Setup user search input listeners
    setupUserSearchInput();

    modal.style.display = 'flex';
}

/**
 * Close Modify Sharing modal
 */
function closeModifySharingModal() {
    const modal = document.getElementById('modify-sharing-modal');
    if (modal) {
        modal.style.display = 'none';
    }
    manageSearchesState.currentSharingSearchId = null;
    manageSearchesState.selectedUsersForSharing = [];
    
    // Clear search input and suggestions
    const searchInput = document.getElementById('user-search-input');
    const suggestions = document.getElementById('user-suggestions');
    if (searchInput) searchInput.value = '';
    if (suggestions) {
        suggestions.style.display = 'none';
        suggestions.innerHTML = '';
    }
}

/**
 * Save sharing changes
 */
async function saveSharingChanges() {
    if (!manageSearchesState.currentSharingSearchId) return;

    const selectedOption = document.querySelector('input[name="sharing-option"]:checked');
    if (!selectedOption) {
        alert(msTr('manageSearches.alertSelectSharing'));
        return;
    }

    const isPublic = selectedOption.value === 'public';
    const userIds = selectedOption.value === 'selected' ? manageSearchesState.selectedUsersForSharing.map(u => u.id) : [];

    // Validate: if selected users, must have at least one user
    if (selectedOption.value === 'selected' && userIds.length === 0) {
        alert(msTr('manageSearches.alertSelectUser'));
        return;
    }

    // Validate: prevent sharing with self
    let currentUserId = null;
    if (window.currentUser) {
        currentUserId = window.currentUser.id || window.currentUser.ID;
    } else if (window.user) {
        currentUserId = window.user.id || window.user.ID;
    } else if (window.BUDG_API_SERVICE && window.BUDG_API_SERVICE.getCurrentUser) {
        const user = window.BUDG_API_SERVICE.getCurrentUser();
        currentUserId = user ? (user.id || user.ID) : null;
    }
    
    if (currentUserId && userIds.includes(parseInt(currentUserId))) {
        alert(msTr('manageSearches.alertCannotShareSelf'));
        return;
    }

    try {
        const response = await fetch(`/api/search/${manageSearchesState.currentSharingSearchId}/share`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({
                isPublic: isPublic,
                userIds: userIds
            })
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || 'Failed to update sharing');
        }

        const result = await response.json();
        if (result.success) {
            closeModifySharingModal();
            await loadMySearches();
            alert(msTr('manageSearches.alertSharingUpdated'));
        } else {
            throw new Error(result.error || 'Failed to update sharing');
        }
    } catch (error) {
        console.error('Error updating sharing:', error);
        alert('Error updating sharing: ' + error.message);
    }
}

/**
 * Open search (navigate to search page with searchId and execute it)
 */
function openSearch(searchId) {
    // Navigate to search page and execute the saved search
    window.location.href = `/search.html?searchId=${searchId}`;
    
    // The search page should detect searchId parameter and execute the search
    // This will be handled in search-input.js or search-init.js
}

/**
 * Copy search URL to clipboard
 */
async function copySearch(searchId) {
    try {
        // Construct the full URL for the saved search results
        const baseUrl = window.location.origin;
        const searchUrl = `${baseUrl}/search.html?searchId=${searchId}`;
        
        // Try to use the modern Clipboard API
        if (navigator.clipboard && navigator.clipboard.writeText) {
            await navigator.clipboard.writeText(searchUrl);
            alert(msTr('manageSearches.urlCopied'));
        } else {
            // Fallback for older browsers
            const textArea = document.createElement('textarea');
            textArea.value = searchUrl;
            textArea.style.position = 'fixed';
            textArea.style.left = '-999999px';
            textArea.style.top = '-999999px';
            document.body.appendChild(textArea);
            textArea.focus();
            textArea.select();
            
            try {
                const successful = document.execCommand('copy');
                if (successful) {
                    alert(msTr('manageSearches.urlCopied'));
                } else {
                    throw new Error('Copy command failed');
                }
            } catch (err) {
                // If copy fails, show the URL for manual copying
                prompt(msTr('manageSearches.copyUrlPrompt'), searchUrl);
            } finally {
                document.body.removeChild(textArea);
            }
        }
    } catch (error) {
        console.error('Error copying URL:', error);
        // Fallback: show the URL in a prompt for manual copying
        const baseUrl = window.location.origin;
        const searchUrl = `${baseUrl}/search.html?searchId=${searchId}`;
        prompt(msTr('manageSearches.copyUrlPrompt'), searchUrl);
    }
}

/**
 * Delete search
 */
async function deleteSearch(searchId) {
    if (!confirm(msTr('manageSearches.confirmDeleteSearch'))) {
        return;
    }

    try {
        const response = await fetch(`/api/search/${searchId}`, {
            method: 'DELETE',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Failed to delete search');
        }

        const result = await response.json();
        if (result.success) {
            await loadMySearches();
            alert(msTr('manageSearches.searchDeleted'));
        }
    } catch (error) {
        console.error('Error deleting search:', error);
        alert(msTr('manageSearches.errorDeleteSearch', { message: error.message }));
    }
}

/**
 * Refresh searches
 */
function refreshSearches() {
    loadMySearches();
}

/**
 * Refresh shared searches
 */
function refreshSharedSearches() {
    loadSharedSearches();
}

/**
 * Load shared users for a search
 */
async function loadSharedUsers(searchId) {
    try {
        const response = await fetch(`/api/search/${searchId}`, {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            return [];
        }

        const result = await response.json();
        if (result.success && result.data) {
            const search = result.data;
            // Check if search has shared_users array
            if (search.shared_users && Array.isArray(search.shared_users)) {
                return search.shared_users.map(user => ({
                    id: user.id,
                    name: user.name || `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.email || msTr('manageSearches.unknown'),
                    email: user.email || ''
                }));
            }
        }
        return [];
    } catch (error) {
        console.error('Error loading shared users:', error);
        return [];
    }
}

/**
 * Setup user search input listeners
 */
function setupUserSearchInput() {
    const searchInput = document.getElementById('user-search-input');
    const suggestions = document.getElementById('user-suggestions');
    
    if (!searchInput || !suggestions) return;

    let searchTimeout;
    
    // Clear existing listeners by cloning
    const newSearchInput = searchInput.cloneNode(true);
    searchInput.parentNode.replaceChild(newSearchInput, searchInput);
    
    newSearchInput.addEventListener('input', (e) => {
        const searchTerm = e.target.value.trim();
        
        clearTimeout(searchTimeout);
        
        if (searchTerm.length < 2) {
            suggestions.style.display = 'none';
            suggestions.innerHTML = '';
            return;
        }
        
        searchTimeout = setTimeout(() => {
            searchUsers(searchTerm);
        }, 300);
    });

    newSearchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            suggestions.style.display = 'none';
        } else if (e.key === 'ArrowDown') {
            e.preventDefault();
            const firstItem = suggestions.querySelector('.user-suggestion-item');
            if (firstItem) firstItem.focus();
        }
    });

    // Close suggestions when clicking outside
    document.addEventListener('click', (e) => {
        if (!newSearchInput.contains(e.target) && !suggestions.contains(e.target)) {
            suggestions.style.display = 'none';
        }
    });
}

/**
 * Search users
 */
async function searchUsers(searchTerm) {
    const suggestions = document.getElementById('user-suggestions');
    if (!suggestions) return;

    try {
        const response = await fetch(`/api/users/list?search=${encodeURIComponent(searchTerm)}`, {
            credentials: 'include'
        });

        if (!response.ok) {
            suggestions.style.display = 'none';
            return;
        }

        const users = await response.json();
        displayUserSuggestions(users);
    } catch (error) {
        console.error('Error searching users:', error);
        suggestions.style.display = 'none';
    }
}

/**
 * Display user suggestions
 */
function displayUserSuggestions(users) {
    const suggestions = document.getElementById('user-suggestions');
    if (!suggestions) return;

    // Filter out already selected users
    const selectedIds = manageSearchesState.selectedUsersForSharing.map(u => u.id);
    const filteredUsers = users.filter(user => !selectedIds.includes(user.ID || user.id));

    if (filteredUsers.length === 0) {
        suggestions.style.display = 'none';
        return;
    }

    suggestions.innerHTML = filteredUsers.map(user => {
        const userId = user.ID || user.id;
        const userName = user.name || `${user.First_Name || ''} ${user.Last_Name || ''}`.trim() || user.Email || msTr('manageSearches.unknown');
        const userEmail = user.Email || user.email || '';
        
        return `
            <div class="user-suggestion-item" onclick="selectUser(${userId}, '${escapeHtml(userName)}', '${escapeHtml(userEmail)}')">
                <input type="radio" class="user-suggestion-radio" name="user-suggestion-radio">
                <div class="user-suggestion-info">
                    <div class="user-suggestion-name">${escapeHtml(userName)}</div>
                    ${userEmail ? `<div class="user-suggestion-email">${escapeHtml(userEmail)}</div>` : ''}
                </div>
            </div>
        `;
    }).join('');

    suggestions.style.display = 'block';
}

/**
 * Select a user and add to selected users
 */
function selectUser(userId, userName, userEmail) {
    // Check if already selected
    if (manageSearchesState.selectedUsersForSharing.find(u => u.id === userId)) {
        return;
    }

    // Prevent sharing with self
    // Get current user ID from various possible sources
    let currentUserId = null;
    if (window.currentUser) {
        currentUserId = window.currentUser.id || window.currentUser.ID;
    } else if (window.user) {
        currentUserId = window.user.id || window.user.ID;
    } else if (window.BUDG_API_SERVICE && window.BUDG_API_SERVICE.getCurrentUser) {
        const user = window.BUDG_API_SERVICE.getCurrentUser();
        currentUserId = user ? (user.id || user.ID) : null;
    }
    
    if (currentUserId && parseInt(userId) === parseInt(currentUserId)) {
        alert(msTr('manageSearches.alertCannotShareSelf'));
        // Clear search input
        const searchInput = document.getElementById('user-search-input');
        if (searchInput) searchInput.value = '';
        return;
    }

    manageSearchesState.selectedUsersForSharing.push({
        id: userId,
        name: userName,
        email: userEmail
    });

    renderSelectedUsers();

    // Clear search input and hide suggestions
    const searchInput = document.getElementById('user-search-input');
    const suggestions = document.getElementById('user-suggestions');
    if (searchInput) searchInput.value = '';
    if (suggestions) {
        suggestions.style.display = 'none';
        suggestions.innerHTML = '';
    }
}

/**
 * Remove user from selected users
 */
function removeSelectedUser(userId) {
    manageSearchesState.selectedUsersForSharing = manageSearchesState.selectedUsersForSharing.filter(u => u.id !== userId);
    renderSelectedUsers();
}

/**
 * Render selected users as tags
 */
function renderSelectedUsers() {
    const tagsContainer = document.getElementById('selected-users-tags');
    if (!tagsContainer) return;

    if (manageSearchesState.selectedUsersForSharing.length === 0) {
        tagsContainer.innerHTML = '';
        return;
    }

    tagsContainer.innerHTML = manageSearchesState.selectedUsersForSharing.map(user => `
        <div class="user-tag">
            <span class="user-tag-name">${escapeHtml(user.name)}</span>
            <span class="user-tag-remove" onclick="removeSelectedUser(${user.id})" title="${escapeHtml(msTr('manageSearches.titleRemove'))}">
                <i class="fas fa-times"></i>
            </span>
        </div>
    `).join('');
}


/**
 * Escape HTML to prevent XSS
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Initialize on page load (after i18n so labels and RTL apply)
document.addEventListener('DOMContentLoaded', () => {
    const run = () => {
        applyManageSearchesPageI18n();
        initManageSearches();
    };
    if (window.i18nReadyPromise) {
        window.i18nReadyPromise.then(run).catch(run);
    } else {
        run();
    }
});

