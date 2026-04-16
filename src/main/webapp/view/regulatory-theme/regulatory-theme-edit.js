// Regulatory Theme Edit Page JavaScript

// Global variables for parent selection
let allRegulatoryThemes = [];
let filteredRegulatoryThemes = [];
let currentRegulatoryThemeId = null;
let isDirty = false;
let hasFormChanges = false;
let segmentField = null; // Segment selector reference

// Global variables for regulations management
let regulationsData = [];
let originalRegulationsData = [];
let regulationsToDelete = [];
let allRegulations = [];
let allRelationTypes = [];

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
    console.log('Initializing regulatory theme edit page...');
    
    const id = parseId();
    if (!id) {
        console.error('No regulatory theme ID found in URL');
        return;
    }
    
    // Initialize lock
    if (!window.LockInitHelper) {
        console.error('LockInitHelper is not available. Make sure lock-init-helper.js is loaded.');
        alert((window.I18n && window.I18n.t('message.error')) || 'Error: Lock initialization helper is not available. Please refresh the page.');
        return;
    }
    
    const lockAcquired = await window.LockInitHelper.initializeLock('regulatory-theme', id, 'regulatory-theme');
    if (!lockAcquired) {
        return; // Lock initialization failed, user was redirected
    }
    
    console.log('Initializing regulatory theme edit page for ID:', id);
    currentRegulatoryThemeId = id;
    
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
    const idx = parts.indexOf('regulatory-theme-edit.html');
    if (idx === -1 || parts.length < idx + 2) return null;
    const pathId = parseInt(parts[idx + 1], 10);
    return Number.isNaN(pathId) ? null : pathId;
}

function parseTab() {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get('tab') || 'summary';
}

async function loadStatusOptions() {
    console.log('=== LOADING STATUS OPTIONS ===');
    
    try {
        const response = await fetch('/api/status/dropdown');
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const result = await response.json();
        console.log('Status API response:', result);
        
        if (result.success && result.data) {
            const statusSelect = document.getElementById('budgStatus');
            console.log('Status select element found:', statusSelect);
            
            if (statusSelect) {
                const selectStatus = (window.I18n && window.I18n.t('regulatoryTheme.placeholders.selectStatus')) || 'Select Status';
                statusSelect.innerHTML = '<option value="">' + escapeHtml(selectStatus) + '</option>';
                console.log('Cleared existing options, current innerHTML:', statusSelect.innerHTML);
                
                // Add status options from database
                result.data.forEach((status, index) => {
                    console.log(`Adding option ${index + 1}:`, status);
                    const option = document.createElement('option');
                    option.value = status.id;
                    option.textContent = status.primaryname; // Use lowercase 'primaryname' as returned by API
                    statusSelect.appendChild(option);
                    console.log(`Added option: ${option.value} - ${option.textContent}`);
                });
                
                console.log('Final dropdown innerHTML:', statusSelect.innerHTML);
                console.log('Status options loaded successfully:', result.data.length, 'options');
            } else {
                console.error('Status select element not found');
            }
        } else {
            console.error('Invalid status API response:', result);
        }
    } catch (error) {
        console.error('Error loading status options:', error);
        const statusSelect = document.getElementById('budgStatus');
        if (statusSelect) {
            const selectStatus = (window.I18n && window.I18n.t('regulatoryTheme.placeholders.selectStatus')) || 'Select Status';
            statusSelect.innerHTML = `
                <option value="">${escapeHtml(selectStatus)}</option>
                <option value="1">Active</option>
                <option value="2">Inactive</option>
                <option value="3">Pending</option>
                <option value="4">Completed</option>
                <option value="5">Cancelled</option>
            `;
            console.log('Using fallback status options due to API error');
        }
    }
}

async function loadRegulatoryTheme(id) {
    console.log('=== LOADING REGULATORY THEME ===');
    console.log('Loading regulatory theme with ID:', id);
    
    try {
        const response = await fetch(`/api/regulatory-theme/${id}`);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        const regulatoryTheme = await response.json();
        
        console.log('=== LOADED REGULATORY THEME DATA ===');
        console.log('Regulatory theme data:', regulatoryTheme);
        
        if (regulatoryTheme) {
            console.log('Calling populateForm...');
            populateForm(regulatoryTheme);
            updateTitle(regulatoryTheme);
        } else {
            console.error('No regulatory theme data found in response');
        }
    } catch (error) {
        console.error('Error loading regulatory theme:', error);
    }
}

function updateTitle(regulatoryTheme) {
    const titleElement = document.getElementById('regulatoryThemeTitle');
    if (titleElement && regulatoryTheme) {
        const name = regulatoryTheme.primaryName || regulatoryTheme.PrimaryName || 'Regulatory Theme';
        titleElement.textContent = name;
        console.log('Title updated to:', titleElement.textContent);
    }
}

function populateForm(regulatoryTheme) {
    console.log('=== POPULATING FORM ===');
    console.log('Regulatory theme data received:', regulatoryTheme);
    
    const setVal = (i, v) => { 
        const el = document.getElementById(i); 
        if (el) {
            el.value = v ?? '';
            console.log(`Set ${i} to:`, v);
        } else {
            console.warn(`Element ${i} not found`);
        }
    };
    
    // Populate form fields based on regulatory theme data structure
    setVal('regulatoryThemeName', regulatoryTheme.primaryName || regulatoryTheme.PrimaryName);
    setVal('regulatoryThemeDescription', regulatoryTheme.description || regulatoryTheme.Description);
    setVal('regulatoryThemeRef', regulatoryTheme.refNumber || regulatoryTheme.RefNumber);
    setVal('regulatoryThemeShortName', regulatoryTheme.shortName || regulatoryTheme.ShortName);
    
    // Set status dropdown
    const statusSelect = document.getElementById('budgStatus');
    console.log('Status select element in populateForm:', statusSelect);
    console.log('Status select options count:', statusSelect ? statusSelect.options.length : 'N/A');
    console.log('Status select innerHTML:', statusSelect ? statusSelect.innerHTML : 'N/A');
    
    if (statusSelect && (regulatoryTheme.statusId || regulatoryTheme.StatusId)) {
        const statusId = regulatoryTheme.statusId || regulatoryTheme.StatusId;
        console.log('Setting status dropdown to:', statusId);
        statusSelect.value = statusId;
        console.log('Status dropdown value after setting:', statusSelect.value);
    } else if (statusSelect) {
        console.log('Setting status dropdown to default: 3');
        statusSelect.value = '3'; // Default to Pending (ID=3 in status table)
        console.log('Status dropdown value after setting default:', statusSelect.value);
    }
    
    // Populate parent
    const parentNameInput = document.getElementById('parentName');
    if (parentNameInput && (regulatoryTheme.parentId || regulatoryTheme.ParentID)) {
        // Load parent regulatory theme name
        loadParentRegulatoryThemeName(regulatoryTheme.parentId || regulatoryTheme.ParentID);
    } else if (parentNameInput) {
        parentNameInput.value = '';
        parentNameInput.dataset.parentId = '';
    }

    // Set segment value if available
    if (segmentField && (regulatoryTheme.segmentId || regulatoryTheme.segment_id || regulatoryTheme.Segment_ID)) {
        const segVal = regulatoryTheme.segmentId ?? regulatoryTheme.segment_id ?? regulatoryTheme.Segment_ID;
        segmentField.setValue(segVal);
    }
    
    console.log('Form populated successfully');
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

async function saveRegulatoryTheme(closeAfter = false) {
    console.log('=== SAVING REGULATORY THEME ===');
    
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
            }
        } else {
            showSuccessMessage('NO CHANGES', true);
        }
        if (closeAfter) {
            window.onbeforeunload = null;
            setTimeout(() => {
                const id = parseId();
                window.location.href = `/view/regulatory-theme/${id}`;
            }, 1500);
        }
        return;
    }
    
    // Sync rich-text editor content back to textarea before reading
    if (typeof syncAdvancedRichTextToTextarea === 'function') {
        syncAdvancedRichTextToTextarea('regulatoryThemeDescription');
    }

    // Get form values
    const regulatoryThemeName = document.getElementById('regulatoryThemeName')?.value?.trim();
    const regulatoryThemeDescription = document.getElementById('regulatoryThemeDescription')?.value?.trim();
    const regulatoryThemeRef = document.getElementById('regulatoryThemeRef')?.value?.trim();
    const regulatoryThemeShortName = document.getElementById('regulatoryThemeShortName')?.value?.trim();
    const budgStatus = document.getElementById('budgStatus')?.value;
    const segmentId = segmentField ? segmentField.getValue() : null;
    
    // Get parent ID
    const parentNameInput = document.getElementById('parentName');
    const parentId = parentNameInput?.dataset.parentId ? parseInt(parentNameInput.dataset.parentId, 10) : null;
    
    console.log('Form values:', {
        regulatoryThemeName,
        regulatoryThemeDescription,
        regulatoryThemeRef,
        regulatoryThemeShortName,
        budgStatus,
        parentId
    });
    
    const t = (key) => (window.I18n && window.I18n.t(key)) || key;
    const missingFields = [];
    if (!regulatoryThemeName) missingFields.push(t('regulatoryTheme.labels.name'));
    if (!regulatoryThemeDescription) missingFields.push(t('regulatoryTheme.labels.description'));
    if (segmentField && !segmentField.validate()) missingFields.push(t('regulator.labels.segment'));
    if (missingFields.length) {
        const msg = (window.I18n && window.I18n.t('regulatoryTheme.messages.pleaseFillIn', { fields: missingFields.join(', ') })) || ('Please fill in: ' + missingFields.join(', '));
        alert(msg);
        return false;
    }
    
    // Prepare payload - using field names expected by the servlet
    const payload = {
        primaryName: regulatoryThemeName,
        description: regulatoryThemeDescription,
        refNumber: regulatoryThemeRef || null,
        shortName: regulatoryThemeShortName || null,
        statusId: budgStatus ? parseInt(budgStatus, 10) : 3, // Default to Pending
        parentId: parentId,
        segmentId
    };
    
    console.log('Payload to send:', payload);
    
    const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
    
    try {
        const savingTxt = (window.I18n && window.I18n.t('message.saving')) || 'Saving...';
        buttons.forEach(b => { 
            if (b) { 
                b.disabled = true; 
                b.dataset._txt = b.textContent; 
                b.textContent = savingTxt; 
            }
        });
        
        const id = parseId();
        console.log('=== CALLING updateRegulatoryTheme API ===');
        console.log('ID:', id);
        console.log('Payload:', payload);
        
        const response = await fetch(`/api/regulatory-theme/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });
        
        console.log('Save response:', response);
        
        if (response.ok) {
            console.log('Regulatory theme saved successfully');
            
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
                    console.log('Redirecting to regulatory theme view with ID:', id);
                    window.location.href = `/view/regulatory-theme/${id}`;
                }, 1500);
            } else { 
                showSuccessMessage(updatesSaved);
            }
            
            return true;
        } else {
            const errorData = await response.json();
            console.error('Save failed:', errorData);
            const errorMsg = errorData.error || errorData.message || 'Unknown error';
            alert('Failed to save regulatory theme: ' + errorMsg);
            return false;
        }
    } catch (error) {
        console.error('Error saving regulatory theme:', error);
        const errorMsg = error?.body?.error || error?.body?.message || error?.message || 'An error occurred while saving. Please try again.';
        alert(errorMsg);
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
    
    // Tab switching
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', function() {
            const tabName = this.dataset.tab;
            switchTab(tabName);
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
        await saveRegulatoryTheme(false);
    });
    
    if (saveCloseBtn) saveCloseBtn.addEventListener('click', async () => {
        await saveRegulatoryTheme(true);
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
            const targetUrl = `/view/regulatory-theme/${id}`;
            console.log('Redirecting to:', targetUrl);
            window.location.href = targetUrl;
        } else {
            console.error('No ID found, redirecting to regulatory theme list');
            window.location.href = '/regulatory-theme.html';
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
                window.location.href = `/view/regulatory-theme/${id}`;
            } else {
                window.location.href = '/regulatory-theme.html';
            }
        }
    });
    
    // Show editor button – advanced rich text editor
    const showEditorBtn = document.getElementById('showEditorBtn');
    if (showEditorBtn) {
        showEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('regulatoryThemeDescription', showEditorBtn);
        });
    }
    
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
    
    // Parent search functionality is now handled in showParentSelectionModal()
    // to ensure it works correctly when the modal is opened
}

function switchTab(tabName) {
    console.log('Switching to tab:', tabName);
    
    // Hide all tab contents
    const tabContents = document.querySelectorAll('.tab-content');
    tabContents.forEach(content => {
        content.classList.remove('active');
    });
    
    // Remove active class from all tabs
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.classList.remove('active');
    });
    
    // Show selected tab content
    const selectedContent = document.getElementById(tabName + 'Container');
    if (selectedContent) {
        selectedContent.classList.add('active');
    }
    
    // Add active class to selected tab
    const selectedTab = document.querySelector(`[data-tab="${tabName}"]`);
    if (selectedTab) {
        selectedTab.classList.add('active');
    }
    
    // Load regulations if switching to regulations tab
    if (tabName === 'regulations') {
        loadRegulationsData();
    }
}

async function initializePage() {
    const id = parseId();
    if (!id) {
        console.error('No regulatory theme ID found');
        return;
    }
    
    try {
        console.log('=== INITIALIZING PAGE ===');
        console.log('Loading status options...');
        await loadStatusOptions();

        // Initialize segment field before loading data so value can be set
        if (window.SegmentField) {
            try {
                segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    // Removed defaultValue - let API data set the correct value
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'RegulatoryTheme',
                    fieldId: 'regulatoryThemeSegment',
                    errorId: 'regulatoryThemeSegmentError',
                    onChange: async (selectedSegmentId, previousSegmentId) => {
                        return await refreshRegulatoryThemeParentForSelectedSegment(previousSegmentId);
                    }
                });
            } catch (err) {
                console.error('Error initializing segment field:', err);
            }
        }
        
        console.log('Loading regulatory theme data...');
        await loadRegulatoryTheme(id);
        
        // Initialize custom fields
        if (window.CustomFields) {
            try {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Regulatory Theme',
                    containerId: 'customFieldsContainer',
                    mode: 'edit',
                    objectId: id
                });
                console.log('Custom fields initialized:', window.customFieldsContext);
            } catch (error) {
                console.error('Error initializing custom fields:', error);
            }
        }
        
        console.log('Loading regulations data...');
        await loadRegulationsData();
        
        console.log('Setting up event listeners...');
        // Setup event listeners after data is loaded
        setupEventListeners();
        
        
        console.log('Regulatory theme edit page initialized successfully');
        
        // Check if we should open a specific tab from URL parameter
        const urlParams = new URLSearchParams(window.location.search);
        const tabParam = urlParams.get('tab');
        if (tabParam) {
            setTimeout(() => {
                switchTab(tabParam);
            }, 100);
        }
    } catch (error) {
        console.error('Error initializing regulatory theme edit page:', error);
    }
}

// Parent Selection Functions
async function refreshRegulatoryThemeParentForSelectedSegment(previousSegmentId) {
    const parentNameInput = document.getElementById('parentName');
    const selectedParentId = parseInt(parentNameInput?.dataset?.parentId || '', 10);
    const selectedSegmentId = segmentField && typeof segmentField.getValue === 'function'
        ? parseInt(segmentField.getValue(), 10)
        : NaN;

    const query = Number.isInteger(selectedSegmentId) && selectedSegmentId > 0
        ? `?segmentId=${encodeURIComponent(selectedSegmentId)}`
        : '';
    const response = await fetch(`/api/regulatory-theme${query}`, { credentials: 'include' });
    if (!response.ok) {
        throw new Error(`Failed to load regulatory themes: ${response.status}`);
    }
    allRegulatoryThemes = await response.json();

    if (!Number.isInteger(selectedParentId) || selectedParentId <= 0) {
        return;
    }

    const allowedParentIds = new Set(
        (allRegulatoryThemes || [])
            .map(theme => parseInt(theme.id || theme.ID, 10))
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
        currentRegulatoryThemeId = parseId();
        console.log('Opening parent selection modal for regulatory theme ID:', currentRegulatoryThemeId);
        
        // Load all regulatory themes using the same pattern as geography
        console.log('=== CALLING getRegulatoryThemes API ===');
        const activeSegmentId = segmentField ? parseInt(segmentField.getValue(), 10) : null;
        const segQuery = Number.isInteger(activeSegmentId) && activeSegmentId > 0 ? `?segmentId=${activeSegmentId}` : '';
        const response = await fetch(`/api/regulatory-theme${segQuery}`);
        console.log('Loaded regulatory themes response:', response);
        
        if (!response.ok) {
            throw new Error(`Failed to load regulatory themes: ${response.status}`);
        }
        
        const regulatoryThemes = await response.json();
        console.log('Processed regulatory themes:', regulatoryThemes);
        console.log('Current regulatory theme ID:', currentRegulatoryThemeId);
        
        // Debug: Check the structure of the first theme
        if (regulatoryThemes.length > 0) {
            console.log('First theme structure:', regulatoryThemes[0]);
            console.log('First theme keys:', Object.keys(regulatoryThemes[0]));
        }
        
        // Store all regulatory themes globally (like geography)
        allRegulatoryThemes = regulatoryThemes;
        
        // Filter out child regulatory themes of current regulatory theme and all their descendants
        filteredRegulatoryThemes = allRegulatoryThemes.filter(theme => {
            const themeId = theme.id || theme.ID;
            const themeName = theme.primaryName || theme.PrimaryName || theme.primaryname;
            
            // Exclude current regulatory theme
            if (themeId == currentRegulatoryThemeId) {
                console.log(`❌ EXCLUDING current regulatory theme: ${themeName} (ID: ${themeId})`);
                return false;
            }
            
            // Check if this regulatory theme is a descendant of current regulatory theme
            console.log(`About to check if theme "${themeName}" (ID: ${themeId}) is descendant of ${currentRegulatoryThemeId}`);
            const isDescendant = isDescendantOf(theme, currentRegulatoryThemeId, allRegulatoryThemes);
            console.log(`isDescendantOf result for "${themeName}": ${isDescendant}`);
            if (isDescendant) {
                console.log(`❌ EXCLUDING descendant: ${themeName} (ID: ${themeId})`);
                return false;
            }
            
            console.log(`✅ INCLUDING regulatory theme: ${themeName} (ID: ${themeId})`);
            return true;
        });
        
        console.log('Filtered regulatory themes (excluding children):', filteredRegulatoryThemes);
        console.log(`Total regulatory themes: ${allRegulatoryThemes.length}`);
        console.log(`Available for parent selection: ${filteredRegulatoryThemes.length}`);
        console.log(`Excluded: ${allRegulatoryThemes.length - filteredRegulatoryThemes.length} regulatory themes (current + descendants)`);
        
        // Show modal
        showParentSelectionModal();
        
    } catch (error) {
        console.error('Error loading regulatory themes for parent selection:', error);
        alert('Error loading regulatory themes. Please try again.');
    }
}

function isDescendantOf(theme, ancestorId, allThemes) {
    const themeId = theme.id || theme.ID;
    const themeName = theme.primaryName || theme.PrimaryName || theme.primaryname;
    
    console.log(`Checking if theme "${themeName}" (ID: ${themeId}) is descendant of ancestor ID: ${ancestorId}`);
    console.log(`Theme parentId: ${theme.parentId}, ParentID: ${theme.ParentID}`);
    
    // Check if theme is a direct child of ancestor
    if (theme.parentId == ancestorId || theme.ParentID == ancestorId) {
        console.log(`✅ Theme "${themeName}" (ID: ${themeId}) is DIRECT CHILD of ancestor ${ancestorId}`);
        return true;
    }
    
    // If theme has no parent, it's not a descendant
    if (!theme.parentId && !theme.ParentID) {
        console.log(`❌ Theme "${themeName}" (ID: ${themeId}) has no parent, not a descendant`);
        return false;
    }
    
    // Find parent theme and check recursively
    const parentId = theme.parentId || theme.ParentID;
    console.log(`Looking for parent theme with ID: ${parentId}`);
    const parentTheme = allThemes.find(t => (t.id || t.ID) == parentId);
    if (!parentTheme) {
        console.log(`❌ Parent theme with ID ${parentId} not found in allThemes`);
        return false;
    }
    
    console.log(`Found parent theme: ${parentTheme.primaryName || parentTheme.PrimaryName || parentTheme.primaryname} (ID: ${parentId})`);
    
    // Recursively check if parent is descendant of ancestor
    const result = isDescendantOf(parentTheme, ancestorId, allThemes);
    console.log(`Recursive check result for "${themeName}": ${result}`);
    return result;
}

function showParentSelectionModal() {
    console.log('=== SHOWING PARENT SELECTION MODAL ===');
    const modal = document.getElementById('parentSelectionModal');
    const regulatoryThemeList = document.getElementById('regulatoryThemeList');
    const searchInput = document.getElementById('parentSearchInput');
    
    console.log('Modal element:', modal);
    console.log('Regulatory theme list element:', regulatoryThemeList);
    console.log('Search input element:', searchInput);
    
    if (!modal) {
        console.error('Parent selection modal not found!');
        return;
    }
    
    if (!regulatoryThemeList) {
        console.error('Regulatory theme list container not found!');
        return;
    }
    
    // Clear previous content
    regulatoryThemeList.innerHTML = '';
    if (searchInput) {
        searchInput.value = '';
    }
    
    // Setup search functionality when modal is shown
    if (searchInput) {
        // Store reference to filteredRegulatoryThemes in closure
        const themesToFilter = filteredRegulatoryThemes;
        console.log('Setting up search with', themesToFilter.length, 'themes');
        
        // Use oninput directly for more reliable event handling
        searchInput.oninput = function() {
            const searchTerm = this.value.toLowerCase().trim();
            console.log('=== SEARCH INPUT EVENT ===');
            console.log('Search term:', searchTerm);
            console.log('Themes to filter:', themesToFilter.length);
            
            let filtered;
            if (searchTerm === '') {
                // Show all filtered regulatory themes if search is empty
                filtered = themesToFilter;
                console.log('Empty search, showing all themes');
            } else {
                // Filter by name or description
                filtered = themesToFilter.filter(theme => {
                    const name = (theme.primaryName || theme.PrimaryName || theme.primaryname || theme.name || '').toLowerCase();
                    const description = (theme.description || theme.Description || '').toLowerCase();
                    const matches = name.includes(searchTerm) || description.includes(searchTerm);
                    return matches;
                });
                console.log('Filtered to', filtered.length, 'themes matching:', searchTerm);
            }
            
            // Re-render the list with filtered results
            renderRegulatoryThemeList(filtered);
        };
        
        // Also add keyup for better compatibility
        searchInput.onkeyup = searchInput.oninput;
        
        // Focus on search input when modal opens
        setTimeout(() => {
            searchInput.focus();
        }, 100);
    }
    
    // Render regulatory themes
    console.log('About to render regulatory themes:', filteredRegulatoryThemes.length);
    renderRegulatoryThemeList(filteredRegulatoryThemes);
    
    // Show modal
    console.log('Setting modal display to flex');
    modal.style.display = 'flex';
    console.log('Modal display style after setting:', modal.style.display);
    console.log('Modal computed style:', window.getComputedStyle(modal).display);
}

function renderRegulatoryThemeList(regulatoryThemes) {
    const regulatoryThemeList = document.getElementById('regulatoryThemeList');
    
    if (!regulatoryThemeList) {
        console.error('regulatoryThemeList element not found!');
        return;
    }
    
    console.log('=== RENDERING REGULATORY THEME LIST ===');
    console.log('Rendering regulatory theme list with', regulatoryThemes.length, 'themes');
    
    // Clear the list first
    regulatoryThemeList.innerHTML = '';
    
    // Add "No Parent" option
    const noParentItem = document.createElement('div');
    noParentItem.className = 'regulatory-theme-item';
    noParentItem.style.borderBottom = '2px solid var(--border-color, #e2e8f0)';
    noParentItem.innerHTML = `
        <div class="regulatory-theme-name" style="font-style: italic; color: var(--text-secondary, #64748b);">No Parent</div>
        <div class="regulatory-theme-description" style="font-style: italic; color: var(--text-secondary, #64748b);">Remove parent regulatory theme</div>
    `;
    noParentItem.addEventListener('click', () => clearParent());
    regulatoryThemeList.appendChild(noParentItem);
    
    if (regulatoryThemes.length === 0) {
        const noThemesItem = document.createElement('div');
        noThemesItem.className = 'no-regulatory-themes';
        noThemesItem.textContent = (window.I18n && window.I18n.t('regulatoryTheme.messages.noParentThemesFound')) || 'No available parent regulatory themes found';
        regulatoryThemeList.appendChild(noThemesItem);
        console.log('No regulatory themes to render, showing "No available parent regulatory themes found"');
        return;
    }
    
    // Render regulatory theme items
    regulatoryThemes.forEach((theme, index) => {
        const themeName = theme.primaryName || theme.PrimaryName || theme.primaryname || 'Unnamed';
        console.log(`Rendering regulatory theme ${index}: ${themeName}`);
        const themeItem = document.createElement('div');
        themeItem.className = 'regulatory-theme-item';
        themeItem.innerHTML = `
            <div class="regulatory-theme-name">${escapeHtml(themeName)}</div>
            <div class="regulatory-theme-description">${escapeHtml(theme.description || theme.Description || 'No description')}</div>
        `;
        
        themeItem.addEventListener('click', () => selectRegulatoryThemeAsParent(theme));
        regulatoryThemeList.appendChild(themeItem);
    });
    
    console.log('Finished rendering regulatory theme list');
}

function selectRegulatoryThemeAsParent(theme) {
    console.log('Selected regulatory theme as parent:', theme);
    
    // Update parent name input
    const parentNameInput = document.getElementById('parentName');
    parentNameInput.value = theme.primaryName || theme.PrimaryName || theme.primaryname || 'Unnamed';
    parentNameInput.dataset.parentId = theme.id || theme.ID;
    
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

async function loadParentRegulatoryThemeName(parentId) {
    try {
        const response = await fetch(`/api/regulatory-theme/${parentId}`);
        if (!response.ok) {
            throw new Error(`Failed to load parent regulatory theme: ${response.status}`);
        }
        const parentTheme = await response.json();
        
        const parentNameInput = document.getElementById('parentName');
        if (parentNameInput && parentTheme) {
            parentNameInput.value = parentTheme.primaryName || parentTheme.PrimaryName || parentTheme.primaryname || 'Unnamed';
            parentNameInput.dataset.parentId = parentId;
        }
    } catch (error) {
        console.error('Error loading parent regulatory theme:', error);
        const parentNameInput = document.getElementById('parentName');
        if (parentNameInput) {
            parentNameInput.value = '';
            parentNameInput.dataset.parentId = '';
        }
    }
}

function isDescendantOf(theme, ancestorId, allThemes) {
    // Check if theme is a direct child of ancestor
    if (theme.parentId == ancestorId || theme.ParentID == ancestorId) {
        return true;
    }
    
    // If theme has no parent, it's not a descendant
    if (!theme.parentId && !theme.ParentID) {
        return false;
    }
    
    // Find parent theme and check recursively
    const parentId = theme.parentId || theme.ParentID;
    const parentTheme = allThemes.find(t => (t.id || t.ID) == parentId);
    if (!parentTheme) {
        return false;
    }
    
    // Recursively check if parent is descendant of ancestor
    return isDescendantOf(parentTheme, ancestorId, allThemes);
}

// ========================================
// REGULATIONS TABLE FUNCTIONS
// ========================================

// Load regulations data for the current regulatory theme
async function loadRegulationsData() {
    if (!currentRegulatoryThemeId) {
        console.warn('No regulatory theme ID available for loading regulations');
        return;
    }
    
    try {
        console.log('Loading regulations data for regulatory theme ID:', currentRegulatoryThemeId);
        
        // Load regulations for this regulatory theme
        console.log('Fetching regulations from:', `/api/regulation-x-regulatorytheme/regulatory-theme/${currentRegulatoryThemeId}`);
        let regulationsResponse;
        try {
            regulationsResponse = await fetch(`/api/regulation-x-regulatorytheme/regulatory-theme/${currentRegulatoryThemeId}`);
            console.log('Regulations response status:', regulationsResponse.status);
            console.log('Regulations response headers:', regulationsResponse.headers);
        } catch (fetchError) {
            console.error('Fetch error:', fetchError);
            throw fetchError;
        }
        if (regulationsResponse.ok) {
            regulationsData = await regulationsResponse.json();
            originalRegulationsData = JSON.parse(JSON.stringify(regulationsData));
            console.log('Loaded regulations data:', regulationsData);
            console.log('Number of regulations:', regulationsData.length);
        } else {
            console.warn('Failed to load regulations data:', regulationsResponse.status);
            const errorText = await regulationsResponse.text();
            console.warn('Error response:', errorText);
            regulationsData = [];
            originalRegulationsData = [];
        }
        
        // Load all regulations for dropdown
        const allRegulationsResponse = await fetch('/api/regulation/list');
        if (allRegulationsResponse.ok) {
            allRegulations = await allRegulationsResponse.json();
            console.log('Loaded all regulations:', allRegulations);
        } else {
            console.warn('Failed to load all regulations:', allRegulationsResponse.status);
            allRegulations = [];
        }
        
        // Load all relation types for dropdown
        const relationTypesResponse = await fetch('/api/regulation-x-regulatorytheme-relationtype/list');
        if (relationTypesResponse.ok) {
            allRelationTypes = await relationTypesResponse.json();
            console.log('Loaded relation types:', allRelationTypes);
        } else {
            console.warn('Failed to load relation types:', relationTypesResponse.status);
            allRelationTypes = [];
        }
        
        // Render the regulations table
        console.log('About to render regulations table with data:', regulationsData);
        console.log('All regulations:', allRegulations);
        console.log('All relation types:', allRelationTypes);
        renderRegulationsTable();
        
        // Initialize event listeners for add/delete buttons
        initializeRegulationsTable();
        
    } catch (error) {
        console.error('Error loading regulations data:', error);
        console.error('Error details:', error.message);
        console.error('Error stack:', error.stack);
        regulationsData = [];
        originalRegulationsData = [];
        allRegulations = [];
        allRelationTypes = [];
        renderRegulationsTable();
        
        // Initialize event listeners even on error
        initializeRegulationsTable();
    }
}

// Render the regulations table
function renderRegulationsTable() {
    console.log('=== RENDER REGULATIONS TABLE START ===');
    const tableBody = document.getElementById('regulationsTableBody');
    console.log('Table body element:', tableBody);
    if (!tableBody) {
        console.error('Regulations table body not found');
        console.log('Available elements with ID containing "regulations":', document.querySelectorAll('[id*="regulations"]'));
        return;
    }
    
    console.log('Rendering regulations table with', regulationsData.length, 'regulations');
    console.log('Regulations data:', regulationsData);
    console.log('Regulations to delete:', regulationsToDelete);
    
    // Clear existing content
    tableBody.innerHTML = '';
    
    // Add existing regulations
    regulationsData.forEach((regulation, index) => {
        console.log('Processing regulation', index, ':', regulation);
        if (!regulationsToDelete.includes(regulation.id)) {
            const row = createRegulationRow(regulation, index);
            tableBody.appendChild(row);
            console.log('Added regulation row for index', index);
        } else {
            console.log('Skipping deleted regulation', regulation.id);
        }
    });
    
    // Add empty row for new entries
    const emptyRow = createRegulationRow(null, regulationsData.length);
    tableBody.appendChild(emptyRow);
    console.log('Added empty row for new entries');
    
    console.log('Final table body innerHTML length:', tableBody.innerHTML.length);
    console.log('Final table body children count:', tableBody.children.length);
    console.log('=== RENDER REGULATIONS TABLE END ===');
}

// Create a regulation row (existing or new)
function createRegulationRow(regulation, index) {
    console.log('Creating regulation row for:', regulation, 'at index:', index);
    const row = document.createElement('tr');
    row.className = regulation ? 'data-row' : 'empty-row';
    row.setAttribute('data-regulation-index', index);
    
    if (regulation) {
        row.setAttribute('data-regulation-id', regulation.id);
        console.log('Setting regulation ID:', regulation.id);
    }
    
    // Relationship Type dropdown
    console.log('Creating relation type dropdown for regulation:', regulation);
    console.log('Available relation types:', allRelationTypes);
    console.log('Looking for relationType:', regulation ? regulation.relationType : 'none');
    
    const relationTypeOptions = allRelationTypes.map(rt => {
        const isSelected = regulation && regulation.relationType == rt.id;
        console.log(`Relation Type ${rt.id} (${rt.primaryname || rt.primaryName || rt.PrimaryName}) - Selected: ${isSelected}`);
        return `<option value="${rt.id}" ${isSelected ? 'selected' : ''}>${escapeHtml(rt.primaryname || rt.primaryName || rt.PrimaryName)}</option>`;
    }).join('');
    
    // Regulation dropdown
    console.log('Creating regulation dropdown for regulation:', regulation);
    console.log('Available regulations:', allRegulations);
    console.log('Looking for regulationId:', regulation ? regulation.regulationId : 'none');
    
    const regulationOptions = allRegulations.map(reg => {
        const isSelected = regulation && regulation.regulationId == reg.id;
        console.log(`Regulation ${reg.id} (${reg.primaryname || reg.primaryName || reg.PrimaryName}) - Selected: ${isSelected}`);
        return `<option value="${reg.id}" ${isSelected ? 'selected' : ''}>${escapeHtml(reg.primaryname || reg.primaryName || reg.PrimaryName)}</option>`;
    }).join('');
    
    row.innerHTML = `
        <td>
            <select class="form-select" data-field="relationType">
                <option value="">Select Relationship Type</option>
                ${relationTypeOptions}
            </select>
        </td>
        <td>
            <select class="form-select" data-field="regulation">
                <option value="">Select Regulation</option>
                ${regulationOptions}
            </select>
        </td>
        <td class="action-column">
            <button type="button" class="btn btn-sm btn-success add-regulation-btn" title="Add">+</button>
            <button type="button" class="btn btn-sm btn-danger delete-regulation-btn" title="Delete">-</button>
        </td>
    `;
    
    // Add change listeners to track modifications
    const selects = row.querySelectorAll('select');
    selects.forEach(select => {
        select.addEventListener('change', () => {
            markRegulationsAsChanged();
        });
    });
    
    return row;
}

// Initialize regulations table event listeners
function initializeRegulationsTable() {
    const tableBody = document.getElementById('regulationsTableBody');
    if (!tableBody) {
        console.warn('Regulations table body not found');
        return;
    }
    
    // Use event delegation for add and delete buttons
    tableBody.addEventListener('click', function(e) {
        if (e.target.closest('.add-regulation-btn')) {
            e.preventDefault();
            const row = e.target.closest('tr');
            addRegulationRow(row);
        } else if (e.target.closest('.delete-regulation-btn')) {
            e.preventDefault();
            const row = e.target.closest('tr');
            deleteRegulationRow(row);
        }
    });
}

// Add a new regulation row
function addRegulationRow(currentRow) {
    if (!currentRow) return;
    
    const relationTypeSelect = currentRow.querySelector('[data-field="relationType"]');
    const regulationSelect = currentRow.querySelector('[data-field="regulation"]');
    
    if (!relationTypeSelect.value || !regulationSelect.value) {
        alert('Please select both Relationship Type and Regulation before adding.');
        return;
    }
    
    // Add new empty row after current row
    const tableBody = document.getElementById('regulationsTableBody');
    const newEmptyRow = createRegulationRow(null, regulationsData.length);
    tableBody.appendChild(newEmptyRow);
    
    markRegulationsAsChanged();
}

// Delete a regulation row
function deleteRegulationRow(row) {
    if (!row) return;
    
    const tbody = row.closest('tbody');
    const allRows = tbody.querySelectorAll('tr');
    const isFirstRow = row === allRows[0];
    const regulationId = row.getAttribute('data-regulation-id');
    
    if (regulationId) {
        // Existing row with data
        if (isFirstRow) {
            // For first row, mark for deletion but preserve structure (clear data only)
            if (!regulationsToDelete.includes(parseInt(regulationId))) {
                regulationsToDelete.push(parseInt(regulationId));
            }
            clearRegulationRowData(row);
            markRegulationsAsChanged();
        } else {
            // For other rows, remove entirely and mark for deletion
            if (!regulationsToDelete.includes(parseInt(regulationId))) {
                regulationsToDelete.push(parseInt(regulationId));
            }
            row.remove();
            markRegulationsAsChanged();
        }
    } else {
        // Empty row without saved data
        if (!isFirstRow) {
            // Remove empty rows that are not the first row
            row.remove();
        } else {
            // For first row, just clear the data
            clearRegulationRowData(row);
        }
    }
}

// Clear row data (for first row) - preserve structure, clear content only
function clearRegulationRowData(row) {
    row.removeAttribute('data-regulation-id');
    row.classList.remove('data-row');
    row.classList.add('empty-row');
    
    const relationTypeSelect = row.querySelector('[data-field="relationType"]');
    const regulationSelect = row.querySelector('[data-field="regulation"]');
    
    if (relationTypeSelect) relationTypeSelect.value = '';
    if (regulationSelect) regulationSelect.value = '';
}

// Mark regulations as changed
function markRegulationsAsChanged() {
    hasFormChanges = true;
    isDirty = true;
}

// Save regulations data
async function saveRegulationsData() {
    if (!currentRegulatoryThemeId) {
        console.warn('No regulatory theme ID available for saving regulations');
        return false;
    }
    
    try {
        console.log('Saving regulations data...');
        
        // Collect data from table rows
        const tableBody = document.getElementById('regulationsTableBody');
        const rows = tableBody.querySelectorAll('tr');
        
        const inserts = [];
        const updates = [];
        
        rows.forEach(row => {
            const relationTypeSelect = row.querySelector('[data-field="relationType"]');
            const regulationSelect = row.querySelector('[data-field="regulation"]');
            const regulationId = row.getAttribute('data-regulation-id');
            
            // Skip empty rows
            if (!relationTypeSelect.value || !regulationSelect.value) {
                return;
            }
            
            const data = {
                regulatoryThemeId: currentRegulatoryThemeId,
                regulationId: parseInt(regulationSelect.value),
                relationType: parseInt(relationTypeSelect.value)
            };
            
            if (regulationId) {
                // Existing record - update
                data.id = parseInt(regulationId);
                updates.push(data);
            } else {
                // New record - insert
                inserts.push(data);
            }
        });
        
        // Prepare operations
        const operations = {
            inserts: inserts,
            updates: updates,
            deletions: regulationsToDelete
        };
        
        console.log('Regulations operations:', operations);
        
        // Send to backend
        const response = await fetch(`/api/regulation-x-regulatorytheme/regulatory-theme/${currentRegulatoryThemeId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(operations)
        });
        
        if (response.ok) {
            console.log('Regulations data saved successfully');
            
            // Reload data to get updated IDs
            await loadRegulationsData();
            
            // Reset deletion array
            regulationsToDelete = [];
            
            return true;
        } else {
            const errorText = await response.text();
            throw new Error(`Failed to save regulations: ${response.status} - ${errorText}`);
        }
        
    } catch (error) {
        console.error('Error saving regulations data:', error);
        alert(((window.I18n && window.I18n.t('regulatoryTheme.messages.errorSavingRegulations')) || 'Error saving regulations') + ': ' + error.message);
        return false;
    }
}

// Update the main save function to include regulations
const originalSaveRegulatoryTheme = saveRegulatoryTheme;
saveRegulatoryTheme = async function(closeAfter = false) {
    const activeTabEl = document.querySelector('.tab.active');
    const activeTab = activeTabEl ? activeTabEl.getAttribute('data-tab') : 'summary';

    if (activeTab === 'regulations') {
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(b => { if (b) { b.disabled = true; b.dataset._txt = b.textContent; b.textContent = 'Saving...'; } });
        try {
            const regulationsSaveResult = await saveRegulationsData();
            if (regulationsSaveResult) {
                const updatesSaved = (window.I18n && window.I18n.t('regulator.messages.updatesSaved')) || 'UPDATES SAVED';
                showSuccessMessage(updatesSaved);
                if (closeAfter) {
                    window.onbeforeunload = null;
                    setTimeout(() => {
                        const id = parseId();
                        window.location.href = `/view/regulatory-theme/${id}`;
                    }, 1500);
                }
            }
            return regulationsSaveResult;
        } finally {
            buttons.forEach(b => { if (b) { b.disabled = false; if (b.dataset._txt) b.textContent = b.dataset._txt; } });
        }
    }

    // summary tab — delegate to original save function
    return await originalSaveRegulatoryTheme.call(this, closeAfter);
};

