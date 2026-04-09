// Segments List View functionality

let segmentsListState = {
    allSegments: [],
    filteredSegments: []
};

async function loadSegmentsList() {
    try {
        // Use /api/segments for admin panel (shows ALL segments for management)
        const response = await fetch('/api/segments');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        // API returns array directly
        segmentsListState.allSegments = Array.isArray(data) ? data : [];
        segmentsListState.filteredSegments = [...segmentsListState.allSegments];
        
        console.log('✅ Loaded segments for list:', segmentsListState.allSegments.length);
    } catch (error) {
        console.error('Error loading segments:', error);
        segmentsListState.allSegments = [];
        segmentsListState.filteredSegments = [];
        notify('Error loading segments: ' + error.message, 'error');
    }
}

function renderSegmentsList() {
    const tbody = document.getElementById('segmentsTableBody');
    if (!tbody) return;
    
    // Use filtered segments for display
    const segmentsToDisplay = segmentsListState.filteredSegments || segmentsListState.allSegments;
    
    if (segmentsToDisplay.length === 0) {
        tbody.innerHTML = `
            <tr class="empty-row">
                <td colspan="2"><i class="fas fa-info-circle"></i> No segments found.</td>
            </tr>
        `;
        document.getElementById('segmentsRecordCount').textContent = '0 records';
        return;
    }
    
    const segmentsRows = segmentsToDisplay.map(segment => {
        const segmentId = segment.id || segment.ID;
        const name = segment.name || segment.Name || 'Unnamed Segment';
        const description = segment.description || segment.Description || 'No description';
        
        return `
        <tr class="segment-row" onclick="openSegmentForm(${segmentId})">
            <td>
                <span class="segment-name-link">${escapeHtml(name)}</span>
            </td>
            <td>${escapeHtml(description)}</td>
        </tr>
        `;
    }).join('');
    
    tbody.innerHTML = segmentsRows;
    document.getElementById('segmentsRecordCount').textContent = `${segmentsToDisplay.length} record${segmentsToDisplay.length !== 1 ? 's' : ''}`;
}

function showSegmentsContent(contentArea) {
    highlightSubmenuItem('Segments');
    if (typeof addToNavigationHistory === 'function') {
        addToNavigationHistory('Segments', showSegmentsContent, applySegmentsHeader);
    }
    applySegmentsHeader();
    
    if (contentArea) {
        // Load the HTML content
        fetch('/view/segments/segments-list.html')
            .then(response => response.text())
            .then(html => {
                contentArea.innerHTML = html;
                initializeSegmentsList();
            })
            .catch(error => {
                console.error('Error loading segments list HTML:', error);
                contentArea.innerHTML = '<div class="error-message">Error loading segments page</div>';
            });
    }
}

async function applySegmentsHeader() {
    const adminHeaderBar = document.querySelector('.admin-header-bar');
    if (!adminHeaderBar) return;
    
    // Clear any existing content to prevent duplicates
    adminHeaderBar.innerHTML = '';
    
    // Create header structure
    const headerLeft = document.createElement('div');
    headerLeft.className = 'header-left';
    headerLeft.innerHTML = `
        <button class="back-button" onclick="goBack()">
            <i class="fas fa-arrow-left"></i>
        </button>
    `;
    
    const headerCenter = document.createElement('div');
    headerCenter.className = 'header-center';
    headerCenter.innerHTML = `
        <h1 class="system-title">Segments</h1>
        <div class="system-status">
            <span class="status-dot"></span>
            BUDG Management
        </div>
    `;
    
    // Get user to check if super admin
    const user = await getCurrentUser();
    const isSuperAdmin = checkIsSuperAdmin(user?.role);
    
    const headerRight = document.createElement('div');
    headerRight.className = 'header-right';
    
    // Only show Create button for Super Admin
    if (isSuperAdmin) {
        headerRight.innerHTML = `
            <button class="create-segment-header-btn" id="createSegmentHeaderBtn" onclick="openSegmentForm(null)">
                <i class="fas fa-plus"></i> Create
            </button>
        `;
    }
    
    adminHeaderBar.appendChild(headerLeft);
    adminHeaderBar.appendChild(headerCenter);
    adminHeaderBar.appendChild(headerRight);
}

/**
 * Get current user information
 */
async function getCurrentUser() {
    try {
        const response = await fetch('/api/me', {
            method: 'GET',
            credentials: 'include'
        });
        if (response.ok) {
            const userData = await response.json();
            console.log('👤 Current user:', userData);
            return userData;
        }
    } catch (error) {
        console.error('Error getting current user:', error);
    }
    return null;
}

/**
 * Check if user is Super Admin
 */
function checkIsSuperAdmin(role) {
    if (typeof RoleUtils !== 'undefined' && RoleUtils.isSuperAdminRole) {
        return RoleUtils.isSuperAdminRole(role);
    }
    if (!role) return false;
    const compact = String(role).toLowerCase().trim().replace(/[\s_-]/g, '');
    return compact === 'superadmin' || compact === 'suberadmin';
}

async function initializeSegmentsList() {
    setupSegmentsListEventListeners();
    await loadSegmentsList();
    renderSegmentsList();
}

function setupSegmentsListEventListeners() {
    // Filter input fields
    const nameFilter = document.getElementById('filterSegmentName');
    const descFilter = document.getElementById('filterSegmentDescription');
    
    if (nameFilter && descFilter) {
        // Filter on input
        [nameFilter, descFilter].forEach(input => {
            input.addEventListener('input', () => {
                filterSegments();
            });
            
            // Clear filter on Escape key
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    input.value = '';
                    filterSegments();
                }
            });
        });
    }
}

function filterSegments() {
    const nameFilter = String(document.getElementById('filterSegmentName')?.value || '').toLowerCase();
    const descFilter = String(document.getElementById('filterSegmentDescription')?.value || '').toLowerCase();
    
    if (!nameFilter && !descFilter) {
        // No filters applied, show all segments
        segmentsListState.filteredSegments = [...segmentsListState.allSegments];
    } else {
        // Apply filters
        segmentsListState.filteredSegments = segmentsListState.allSegments.filter(segment => {
            const name = String(segment.name || segment.Name || '').toLowerCase();
            const description = String(segment.description || segment.Description || '').toLowerCase();
            
            const matchesName = !nameFilter || name.includes(nameFilter);
            const matchesDesc = !descFilter || description.includes(descFilter);
            
            return matchesName && matchesDesc;
        });
    }
    
    renderSegmentsList();
}

function openSegmentForm(segmentId) {
    const contentArea = document.querySelector('.content-area');
    if (!contentArea) return;
    
    // Load the form HTML
    fetch('/view/segments/segment-form.html')
        .then(response => response.text())
        .then(html => {
            contentArea.innerHTML = html;
            // Load the form JavaScript
            loadSegmentFormScript(segmentId);
        })
        .catch(error => {
            console.error('Error loading segment form HTML:', error);
            contentArea.innerHTML = '<div class="error-message">Error loading segment form</div>';
        });
}

function loadSegmentFormScript(segmentId) {
    // Check if script is already loaded
    if (typeof initializeSegmentForm === 'function') {
        initializeSegmentForm(segmentId);
        return;
    }
    
    // Check if script tag already exists
    const existingScript = document.querySelector('script[src="/view/segments/segment-form.js"]');
    if (existingScript) {
        existingScript.onload = () => {
            if (typeof initializeSegmentForm === 'function') {
                initializeSegmentForm(segmentId);
            }
        };
        return;
    }
    
    // Load the script
    const script = document.createElement('script');
    script.src = '/view/segments/segment-form.js';
    script.onload = () => {
        if (typeof initializeSegmentForm === 'function') {
            initializeSegmentForm(segmentId);
        }
    };
    script.onerror = () => {
        console.error('Error loading segment-form.js');
    };
    document.head.appendChild(script);
}

// Helper functions
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function notify(message, type = 'info') {
    // Check for showNotification function (used in admin panel)
    if (typeof showNotification === 'function') {
        showNotification(message, type);
        return;
    }
    
    // Check for window.showNotification
    if (typeof window.showNotification === 'function') {
        window.showNotification(message, type);
        return;
    }
    
    // Fallback: create a simple notification
    const notification = document.createElement('div');
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 15px 20px;
        background: ${type === 'error' ? '#dc3545' : type === 'success' ? '#28a745' : '#17a2b8'};
        color: white;
        border-radius: 4px;
        box-shadow: 0 2px 10px rgba(0,0,0,0.2);
        z-index: 10000;
        max-width: 400px;
        font-family: 'Inter', sans-serif;
    `;
    notification.textContent = message;
    document.body.appendChild(notification);
    setTimeout(() => {
        notification.style.opacity = '0';
        notification.style.transition = 'opacity 0.3s';
        setTimeout(() => notification.remove(), 300);
    }, 3000);
}

// Make functions globally accessible
window.showSegmentsContent = showSegmentsContent;
window.openSegmentForm = openSegmentForm;

