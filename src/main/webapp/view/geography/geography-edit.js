// Geography Edit Page JavaScript

// Global variables for parent selection (like business area)
let allGeographies = [];
let filteredGeographies = [];
let currentGeographyId = null;
let isDirty = false;
let hasFormChanges = false;
let segmentField = null; // Segment selector reference

// Helper function for HTML escaping
function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

document.addEventListener('DOMContentLoaded', async function() {
    console.log('Initializing geography edit page...');
    
    const id = parseId();
    if (!id) {
        console.error('No geography ID found in URL');
        return;
    }
    
    // Initialize lock
    const lockAcquired = await window.LockInitHelper.initializeLock('geography', id, 'geography');
    if (!lockAcquired) {
        return; // Lock initialization failed, user was redirected
    }
    
    console.log('Initializing geography edit page for ID:', id);
    currentGeographyId = id;
    
    // Initialize the page
    initializePage();
});

function parseId() {
    console.log('Parsing ID from URL:', window.location.href);
    
    // First try URL params (for separate edit pages)
    const urlParams = new URLSearchParams(window.location.search);
    const id = parseInt(urlParams.get('id'), 10);
    if (!Number.isNaN(id)) return id;
    
    // Fallback to path-based ID (for main view pages)
    const parts = window.location.pathname.split('/').filter(Boolean);
    const idx = parts.indexOf('geography-edit.html');
    if (idx === -1 || parts.length < idx + 2) return null;
    const pathId = parseInt(parts[idx + 1], 10);
    return Number.isNaN(pathId) ? null : pathId;
}

function parseTab() {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get('tab') || 'summary';
}

async function loadGeography(id) {
    console.log('=== LOADING GEOGRAPHY ===');
    console.log('Loading geography with ID:', id);
    
    try {
        const response = await fetch(`/api/geography/${id}`);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        const geography = await response.json();
        
        console.log('=== LOADED GEOGRAPHY DATA ===');
        console.log('Geography data:', geography);
        
        if (geography) {
            console.log('Calling populateForm...');
            populateForm(geography);
            updateTitle(geography);
        } else {
            console.error('No geography data found in response');
        }
    } catch (error) {
        console.error('Error loading geography:', error);
    }
}

function updateTitle(geography) {
    const titleElement = document.getElementById('geographyTitle');
    if (titleElement && geography) {
        const name = geography.primaryName || geography.PrimaryName || 'Geography';
        titleElement.textContent = name;
        console.log('Title updated to:', titleElement.textContent);
    }
}

function populateForm(geography) {
    console.log('=== POPULATING FORM ===');
    console.log('Geography data received:', geography);
    
    const setVal = (i, v) => { 
        const el = document.getElementById(i); 
        if (el) {
            el.value = v ?? '';
            console.log(`Set ${i} to:`, v);
        } else {
            console.warn(`Element ${i} not found`);
        }
    };

    // Populate form fields based on geography data structure
    setVal('geographyName', geography.primaryName || geography.PrimaryName);
    setVal('geographyDescription', geography.description || geography.Description);
    
    // Populate parent
    const parentNameInput = document.getElementById('parentName');
    if (parentNameInput && geography.parentId || geography.ParentID) {
        // Load parent geography name
        loadParentGeographyName(geography.parentId || geography.ParentID);
    } else if (parentNameInput) {
        parentNameInput.value = '';
        parentNameInput.dataset.parentId = '';
    }

    // Set segment value if available
    if (segmentField && (geography.segmentId || geography.segment_id || geography.Segment_ID)) {
        const segVal = geography.segmentId ?? geography.segment_id ?? geography.Segment_ID;
        segmentField.setValue(segVal);
    }
    
    // Classifications section is empty as per design
    
    console.log('Form populated successfully');
}

// Helper functions removed - no longer needed for simplified design

function showSuccessMessage(message, isError = false) {
    // Remove any existing success message
    const existingMessage = document.getElementById('success-message');
    if (existingMessage) {
        existingMessage.remove();
    }
    
    // Create success message element
    const successDiv = document.createElement('div');
    successDiv.id = 'success-message';
    const backgroundColor = isError ? '#ef4444' : '#10b981';
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
    
    // Add CSS animation
    const style = document.createElement('style');
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

// Form dirty state management
function markAsDirty(e) {
    if (e && e.isTrusted === false) return;
    isDirty = true;
    hasFormChanges = true;
    updateSaveButtons();
}

function markAsClean() {
    isDirty = false;
    hasFormChanges = false;
    updateSaveButtons();
}


function updateSaveButtons() {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    
    if (isDirty) {
        saveBtn?.classList.add('btn-warning');
        saveAndCloseBtn?.classList.add('btn-warning');
    } else {
        saveBtn?.classList.remove('btn-warning');
        saveAndCloseBtn?.classList.remove('btn-warning');
    }
}

async function saveGeography(closeAfter = false) {
    console.log('=== SAVING GEOGRAPHY ===');
    
    // Check if there are any changes
    if (!hasFormChanges) {
        // Still save custom fields even if main form hasn't changed
        if (window.customFieldsContext && window.customFieldsContext.saveValues) {
            try {
                const id = parseId();
                await window.customFieldsContext.saveValues(id);
                console.log('✅ Custom fields saved successfully');
                showSuccessMessage('UPDATES SAVED');
            } catch (error) {
                console.error('Error saving custom fields:', error);
                showSuccessMessage('Error saving custom fields', true);
            }
        } else {
            showSuccessMessage('NO CHANGES', true);
        }
        if (closeAfter) {
            window.onbeforeunload = null;
            setTimeout(() => {
                const id = parseId();
                window.location.href = `/view/geography/${id}`;
            }, 1500);
        }
        return;
    }
    
    // Sync rich-text editor content back to textarea before reading
    if (typeof syncAdvancedRichTextToTextarea === 'function') {
        syncAdvancedRichTextToTextarea('geographyDescription');
    }

    // Get form values
    const geographyName = document.getElementById('geographyName')?.value?.trim();
    const geographyDescription = document.getElementById('geographyDescription')?.value?.trim();
    const segmentId = segmentField ? segmentField.getValue() : null;
    
    // Get parent ID
    const parentNameInput = document.getElementById('parentName');
    const parentId = parentNameInput?.dataset.parentId ? parseInt(parentNameInput.dataset.parentId, 10) : null;
    
    console.log('Form values:', {
        geographyName,
        geographyDescription,
        parentId
    });
    
    const t = (key) => (window.I18n && window.I18n.t(key)) || key;
    const missingFields = [];
    if (!geographyName) missingFields.push(t('geography.labels.name'));
    if (segmentField && !segmentField.validate()) missingFields.push(t('regulator.labels.segment'));
    if (missingFields.length) {
        const msg = (window.I18n && window.I18n.t('geography.messages.pleaseFillIn', { fields: missingFields.join(', ') })) || ('Please fill in: ' + missingFields.join(', '));
        alert(msg);
        return false;
    }
    
    // Prepare payload - using field names expected by the servlet
    const payload = {
        primaryName: geographyName,
        description: geographyDescription || null,
        parentId: parentId,
        segmentId
    };
    
    console.log('Payload to send:', payload);
    
    const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
    
    try {
        buttons.forEach(b => { 
            if (b) { 
                b.disabled = true; 
                b.dataset._txt = b.textContent; 
                b.textContent = 'Saving...'; 
            }
        });
        
        const id = parseId();
        console.log('=== CALLING updateGeography API ===');
        console.log('ID:', id);
        console.log('Payload:', payload);
        
        const response = await fetch(`/api/geography/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });
        
        console.log('Save response:', response);
        
        if (response.ok) {
            console.log('Geography saved successfully');
            
            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                try {
                    await window.customFieldsContext.saveValues(id);
                    console.log('✅ Custom fields saved successfully');
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                }
            }
            
            // Release lock after successful save
            await window.LockInitHelper.releaseLock();
            
            // Mark form as clean after successful save
            markAsClean();
            
            const updatesSaved = (window.I18n && window.I18n.t('regulator.messages.updatesSaved')) || 'UPDATES SAVED';
            if (closeAfter) { 
                showSuccessMessage(updatesSaved);
                window.onbeforeunload = null;
                setTimeout(() => {
                    console.log('Redirecting to geography view with ID:', id);
                    window.location.href = `/view/geography/${id}`;
                }, 1500);
            } else { 
                showSuccessMessage(updatesSaved);
            }
            
            return true;
        } else {
            const errorData = await response.json().catch(() => ({}));
            console.error('Save failed:', errorData);
            const detail = errorData.error || errorData.message || 'Unknown error';
            alert(typeof window.formatSaveError === 'function' ? window.formatSaveError({ body: errorData, message: detail }) : ('Failed to save geography: ' + detail));
            return false;
        }
    } catch (error) {
        console.error('Error saving geography:', error);
        alert(typeof window.formatSaveError === 'function' ? window.formatSaveError(error) : ('An error occurred while saving. Please try again.'));
        return false;
    } finally {
        buttons.forEach(b => { 
            if (b) { 
                b.disabled = false; 
                if (b.dataset._txt) b.textContent = b.dataset._txt; 
            }
        });
    }
}

function setupEventListeners() {
    console.log('Setting up event listeners...');
    
    // Tab switching (simplified - only summary tab)
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', function() {
            // Only summary tab exists, so no switching needed
            console.log('Summary tab clicked');
        });
    });
    
    // Save buttons
    const saveBtn = document.getElementById('saveBtn');
    const saveCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');

    console.log('Setting up buttons:', {
        saveBtn: !!saveBtn,
        saveCloseBtn: !!saveCloseBtn,
        closeBtn: !!closeBtn
    });

    if (saveBtn) saveBtn.addEventListener('click', async () => {
        await saveGeography(false);
    });
    
    if (saveCloseBtn) saveCloseBtn.addEventListener('click', async () => {
        await saveGeography(true);
    });
    
    if (closeBtn) {
        console.log('Close button found, adding event listener');
        
        // Add multiple event listeners to ensure it works
        closeBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            console.log('Close button clicked!');
            handleClose();
        });
        
        // Also add a direct onclick handler as backup
        closeBtn.onclick = function(e) {
            e.preventDefault();
            e.stopPropagation();
            console.log('Close button onclick (backup)');
            handleClose();
            return false;
        };
        
    } else {
        console.error('Close button not found!');
    }

    // Show editor button – advanced rich text editor
    const showEditorBtn = document.getElementById('showEditorBtn');
    if (showEditorBtn) {
        showEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('geographyDescription', showEditorBtn);
        });
    }
    
    // Function to handle close action
    async function handleClose() {
        const id = parseId();
        console.log('Parsed ID:', id);
        console.log('Current URL:', window.location.href);
        
        // Release lock before closing
        if (window.LockInitHelper) {
            await window.LockInitHelper.releaseLock();
        } else if (window.currentLockManager) {
            await window.currentLockManager.releaseLock();
        }
        
        window.onbeforeunload = null;
        if (id) {
            const targetUrl = `/view/geography/${id}`;
            console.log('Redirecting to:', targetUrl);
            window.location.href = targetUrl;
        } else {
            console.error('No ID found, redirecting to geography list');
            window.location.href = '/geography.html';
        }
    }
    
    // Form change tracking
    const formInputs = document.querySelectorAll('input, select, textarea');
    formInputs.forEach(input => {
        input.addEventListener('input', markAsDirty);
        input.addEventListener('change', markAsDirty);
    });
    
    // Set beforeunload handler
    window.onbeforeunload = function(e) {
        if (isDirty || hasFormChanges) {
            console.log('onbeforeunload triggered - showing warning');
            e.preventDefault();
            e.returnValue = '';
        }
    };
    
    // Add keyboard shortcut for close (Escape key)
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            console.log('Escape key pressed, closing...');
            const id = parseId();
            window.onbeforeunload = null;
            if (id) {
                window.location.href = `/view/geography/${id}`;
            } else {
                window.location.href = '/geography.html';
            }
        }
    });
    
    // Show editor button removed - no longer needed
    
    // Parent name input click handler
    const parentNameInput = document.getElementById('parentName');
    if (parentNameInput) {
        parentNameInput.addEventListener('click', function() {
            console.log('Parent name input clicked');
            selectParent();
        });
    }
    
    // Parent selection button
    const selectParentBtn = document.getElementById('selectParentBtn');
    if (selectParentBtn) {
        selectParentBtn.addEventListener('click', selectParent);
    }
    
    // Clear parent button
    const clearParentBtn = document.getElementById('clearParentBtn');
    if (clearParentBtn) {
        clearParentBtn.addEventListener('click', clearParent);
    }
    
    // Parent selection modal events
    const closeParentModal = document.getElementById('closeParentModal');
    if (closeParentModal) {
        closeParentModal.addEventListener('click', closeParentSelectionModal);
    }
    
    const cancelParentSelection = document.getElementById('cancelParentSelection');
    if (cancelParentSelection) {
        cancelParentSelection.addEventListener('click', closeParentSelectionModal);
    }
    
    // Parent search functionality
    const parentSearchInput = document.getElementById('parentSearchInput');
    if (parentSearchInput) {
        parentSearchInput.addEventListener('input', function() {
            const searchTerm = this.value.toLowerCase();
            const filtered = filteredGeographies.filter(geography => {
                const name = (geography.primaryName || geography.PrimaryName || geography.name || geography.Name || '').toLowerCase();
                const description = (geography.description || geography.Description || '').toLowerCase();
                return name.includes(searchTerm) || description.includes(searchTerm);
            });
            renderGeographyList(filtered);
        });
    }
}

function switchTab(tabName) {
    console.log('Switching to tab:', tabName);
    // Only summary tab exists, so no switching logic needed
    // Summary container is always visible
}

async function initializePage() {
    const id = parseId();
    if (!id) {
        console.error('No geography ID found');
        return;
    }
    
    try {
        console.log('=== INITIALIZING PAGE ===');
        console.log('Loading geography data...');

        // Initialize segment field first
        if (window.SegmentField) {
            try {
                segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    // Removed defaultValue - let API data set the correct value
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Geography',
                    fieldId: 'geographySegment',
                    errorId: 'geographySegmentError',
                    onChange: async (selectedSegmentId, previousSegmentId) => {
                        return await refreshGeographyParentForSelectedSegment(previousSegmentId);
                    }
                });
            } catch (err) {
                console.error('Error initializing segment field:', err);
            }
        }

        await loadGeography(id);
        
        // Initialize custom fields
        if (window.CustomFields) {
            try {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Geography',
                    containerId: 'customFieldsContainer',
                    mode: 'edit',
                    objectId: id
                });
                console.log('Custom fields initialized:', window.customFieldsContext);
            } catch (error) {
                console.error('Error initializing custom fields:', error);
            }
        }
        
        console.log('Setting up event listeners...');
        // Setup event listeners after data is loaded
        setupEventListeners();
        
        
        console.log('Geography edit page initialized successfully');
        
        // Check if we should open a specific tab from URL parameter
        const urlParams = new URLSearchParams(window.location.search);
        const tabParam = urlParams.get('tab');
        if (tabParam) {
            setTimeout(() => {
                switchTab(tabParam);
            }, 100);
        }
    } catch (error) {
        console.error('Error initializing geography edit page:', error);
    }
}

// Parent Selection Functions
async function refreshGeographyParentForSelectedSegment(previousSegmentId) {
    const parentNameInput = document.getElementById('parentName');
    const selectedParentId = parseInt(parentNameInput?.dataset?.parentId || '', 10);
    const selectedSegmentId = segmentField && typeof segmentField.getValue === 'function'
        ? parseInt(segmentField.getValue(), 10)
        : NaN;

    const query = Number.isInteger(selectedSegmentId) && selectedSegmentId > 0
        ? `?segmentId=${encodeURIComponent(selectedSegmentId)}`
        : '';
    const response = await fetch(`/api/geography${query}`, { credentials: 'include' });
    if (!response.ok) {
        throw new Error(`Failed to load geographies: ${response.status}`);
    }
    allGeographies = await response.json();

    if (!Number.isInteger(selectedParentId) || selectedParentId <= 0) {
        return;
    }

    const allowedParentIds = new Set(
        (allGeographies || [])
            .map(geo => parseInt(geo.id || geo.ID, 10))
            .filter(id => Number.isInteger(id) && id > 0)
    );

    if (!allowedParentIds.has(selectedParentId)) {
        alert('This parent is not valid for the selected segment. Please remove the parent first.');
        return false;
    }
    return true;
}

async function selectParent() {
    try {
        currentGeographyId = parseId();
        console.log('Opening parent selection modal for geography ID:', currentGeographyId);
        
        // Load all geographies using the same pattern as business area
        console.log('=== CALLING getGeographies API ===');
        const response = await fetch('/api/geography/list');
        console.log('Loaded geographies response:', response);
        
        if (!response.ok) {
            throw new Error(`Failed to load geographies: ${response.status}`);
        }
        
        const geographies = await response.json();
        console.log('Processed geographies:', geographies);
        console.log('Current geography ID:', currentGeographyId);

        // Store all geographies globally (like business area)
        allGeographies = geographies;
        
        // Filter out child geographies of current geography and all their descendants
        filteredGeographies = allGeographies.filter(geography => {
            const geoId = geography.id || geography.ID;
            const geoName = geography.primaryName || geography.PrimaryName;
            
            // Exclude current geography
            if (geoId == currentGeographyId) {
                console.log(`❌ EXCLUDING current geography: ${geoName} (ID: ${geoId})`);
                return false;
            }
            
            // Check if this geography is a descendant of current geography
            const isDescendant = isDescendantOf(geography, currentGeographyId, allGeographies);
            if (isDescendant) {
                console.log(`❌ EXCLUDING descendant: ${geoName} (ID: ${geoId})`);
                return false;
            }
            
            console.log(`✅ INCLUDING geography: ${geoName} (ID: ${geoId})`);
            return true;
        });
        
        console.log('Filtered geographies (excluding children):', filteredGeographies);
        console.log(`Total geographies: ${allGeographies.length}`);
        console.log(`Available for parent selection: ${filteredGeographies.length}`);
        console.log(`Excluded: ${allGeographies.length - filteredGeographies.length} geographies (current + descendants)`);
        
        // Show modal
        showParentSelectionModal();
        
    } catch (error) {
        console.error('Error loading geographies for parent selection:', error);
        alert((window.I18n && window.I18n.t('geography.messages.errorLoadingGeographies')) || 'Error loading geographies. Please try again.');
    }
}

function showParentSelectionModal() {
    const modal = document.getElementById('parentSelectionModal');
    const geographyList = document.getElementById('geographyList');
    const searchInput = document.getElementById('parentSearchInput');
    
    if (!modal) {
        console.error('Parent selection modal not found!');
        return;
    }
    
    if (!geographyList) {
        console.error('Geography list container not found!');
        return;
    }
    
    // Clear previous content
    geographyList.innerHTML = '';
    if (searchInput) {
        searchInput.value = '';
    }
    
    // Render geographies
    renderGeographyList(filteredGeographies);
    
    // Show modal
    modal.style.display = 'flex';
}

function renderGeographyList(geographies) {
    const geographyList = document.getElementById('geographyList');
    
    console.log('Rendering geography list with', geographies.length, 'geographies');
    
    // Add "No Parent" option
    const noParentItem = document.createElement('div');
    noParentItem.className = 'geography-item';
    noParentItem.style.borderBottom = '2px solid var(--border-color, #e2e8f0)';
    noParentItem.innerHTML = `
        <div class="geography-name" style="font-style: italic; color: var(--text-secondary, #64748b);">No Parent</div>
        <div class="geography-description" style="font-style: italic; color: var(--text-secondary, #64748b);">Remove parent geography</div>
    `;
    noParentItem.addEventListener('click', () => clearParent());
    geographyList.appendChild(noParentItem);
    
    if (geographies.length === 0) {
        const noGeographiesItem = document.createElement('div');
        noGeographiesItem.className = 'no-geographies';
        noGeographiesItem.textContent = (window.I18n && window.I18n.t('geography.messages.noParentGeographiesFound')) || 'No available parent geographies found';
        geographyList.appendChild(noGeographiesItem);
        console.log('No geographies to render, showing "No available parent geographies found"');
        return;
    }
    
    // Render geography items
    geographies.forEach((geography, index) => {
        console.log(`Rendering geography ${index}:`, geography);
        const geographyItem = document.createElement('div');
        geographyItem.className = 'geography-item';
        geographyItem.innerHTML = `
            <div class="geography-name">${geography.primaryName || geography.PrimaryName || 'Unnamed'}</div>
            <div class="geography-description">${geography.description || geography.Description || 'No description'}</div>
        `;
        
        geographyItem.addEventListener('click', () => selectGeographyAsParent(geography));
        geographyList.appendChild(geographyItem);
    });
    
    console.log('Finished rendering geography list');
}

function selectGeographyAsParent(geography) {
    console.log('Selected geography as parent:', geography);
    
    // Update parent name input
    const parentNameInput = document.getElementById('parentName');
    parentNameInput.value = geography.primaryName || geography.PrimaryName || 'Unnamed';
    parentNameInput.dataset.parentId = geography.id || geography.ID;
    
    // Mark form as dirty since parent changed
    markAsDirty();
    
    // Close modal
    closeParentSelectionModal();
}

function closeParentSelectionModal() {
    const modal = document.getElementById('parentSelectionModal');
    modal.style.display = 'none';
}

function clearParent() {
    console.log('Clearing parent selection');
    
    // Clear parent name input
    const parentNameInput = document.getElementById('parentName');
    parentNameInput.value = '';
    parentNameInput.dataset.parentId = '';
    
    // Mark form as dirty since parent changed
    markAsDirty();
    
    // Close modal
    closeParentSelectionModal();
}

async function loadParentGeographyName(parentId) {
    try {
        const response = await fetch(`/api/geography/${parentId}`);
        if (!response.ok) {
            throw new Error(`Failed to load parent geography: ${response.status}`);
        }
        const parentGeography = await response.json();
        
        const parentNameInput = document.getElementById('parentName');
        if (parentNameInput && parentGeography) {
            parentNameInput.value = parentGeography.primaryName || parentGeography.PrimaryName || parentGeography.primaryname || 'Unnamed';
            parentNameInput.dataset.parentId = parentId;
        }
    } catch (error) {
        console.error('Error loading parent geography:', error);
        const parentNameInput = document.getElementById('parentName');
        if (parentNameInput) {
            parentNameInput.value = '';
            parentNameInput.dataset.parentId = '';
        }
    }
}

function isDescendantOf(geography, ancestorId, allGeographies) {
    // Check if geography is a direct child of ancestor
    if (geography.parentId == ancestorId || geography.ParentID == ancestorId) {
        return true;
    }
    
    // If geography has no parent, it's not a descendant
    if (!geography.parentId && !geography.ParentID) {
        return false;
    }
    
    // Find parent geography and check recursively
    const parentId = geography.parentId || geography.ParentID;
    const parentGeography = allGeographies.find(g => (g.id || g.ID) == parentId);
    if (!parentGeography) {
        return false;
    }
    
    // Recursively check if parent is descendant of ancestor
    return isDescendantOf(parentGeography, ancestorId, allGeographies);
}

// getAllDescendants function removed - using isDescendantOf directly like glossary edit

