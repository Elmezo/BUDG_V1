// Regulator Edit Page JavaScript

// Global variables
let currentRegulatorId = null;
let currentRegulatorSegmentId = 1;
let isDirty = false;
let hasFormChanges = false;
let segmentField = null; // Segment selector reference

// Global variables for geographies management (similar to regulations in regulatory-theme)
let geographiesData = [];
let originalGeographiesData = [];
let geographiesToDelete = [];
let allGeographies = [];

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
    console.log('Initializing regulator edit page...');
    
    const id = parseId();
    if (!id) {
        console.error('No regulator ID found in URL');
        return;
    }
    
    // Initialize lock
    const lockAcquired = await window.LockInitHelper.initializeLock('regulator', id, 'regulator');
    if (!lockAcquired) {
        return; // Lock initialization failed, user was redirected
    }
    
    console.log('Initializing regulator edit page for ID:', id);
    
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
    const idx = parts.indexOf('regulator-edit.html');
    if (idx === -1 || parts.length < idx + 2) return null;
    const pathId = parseInt(parts[idx + 1], 10);
    return Number.isNaN(pathId) ? null : pathId;
}

function parseTab() {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get('tab') || 'summary';
}

function switchTab(tabName) {
    console.log('Switching to tab:', tabName);
    
    // Update active tab
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    const targetTab = document.querySelector(`.tab[data-tab="${tabName}"]`);
    if (targetTab) {
        targetTab.classList.add('active');
    } else {
        // Fallback to summary tab if target tab doesn't exist
        const summaryTab = document.querySelector('.tab[data-tab="summary"]');
        if (summaryTab) {
            summaryTab.classList.add('active');
        }
    }
}

async function loadRegulator(id) {
    console.log('=== LOADING REGULATOR ===');
    console.log('Loading regulator with ID:', id);
    
    try {
        const response = await fetch(`/api/regulator/${id}`);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        const regulator = await response.json();
        
        console.log('=== LOADED REGULATOR DATA ===');
        console.log('Regulator data:', regulator);
        
        if (regulator) {
            currentRegulatorSegmentId = regulator.segmentId ?? regulator.segment_id ?? regulator.Segment_ID ?? 1;
            console.log('Calling populateForm...');
            populateForm(regulator);
            updateTitle(regulator);
        } else {
            console.error('No regulator data found in response');
        }
    } catch (error) {
        console.error('Error loading regulator:', error);
    }
}

function updateTitle(regulator) {
    const titleElement = document.getElementById('regulatorTitle');
    if (titleElement && regulator) {
        const name = regulator.primaryName || regulator.PrimaryName || regulator.name || regulator.Name || (window.I18n ? window.I18n.t('regulator.page.title') : 'Regulator');
        titleElement.textContent = name;
        console.log('Title updated to:', name);
    }
}

function populateForm(regulator) {
    console.log('=== POPULATING FORM ===');
    console.log('Regulator data received:', regulator);
    
    const setVal = (i, v) => { 
        const el = document.getElementById(i); 
        if (el) {
            el.value = v ?? '';
            console.log(`Set ${i} to:`, v);
        } else {
            console.warn(`Element ${i} not found`);
        }
    };
    
    // Populate form fields based on regulator table structure
    setVal('regulatorName', regulator.primaryName || regulator.PrimaryName || regulator.name || regulator.Name);
    setVal('regulatorShortName', regulator.shortName || regulator.ShortName || regulator.shortname);
    setVal('regulatorDescription', regulator.description || regulator.Description);

    // Set segment value if available
    if (segmentField && (regulator.segmentId || regulator.segment_id || regulator.Segment_ID)) {
        const segVal = regulator.segmentId ?? regulator.segment_id ?? regulator.Segment_ID;
        segmentField.setValue(segVal);
    }
    
    console.log('Form populated successfully');
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

function showSuccessMessage(message, isError = false) {
    // Remove any existing success message
    const existingMessage = document.getElementById('success-message');
    if (existingMessage) {
        existingMessage.remove();
    }
    
    // Create success message element
    const successDiv = document.createElement('div');
    successDiv.id = 'success-message';
    successDiv.className = 'success-message';
    if (isError) {
        successDiv.classList.add('error');
    }
    successDiv.textContent = message;
    
    // Add to page
    document.body.appendChild(successDiv);
    
    // Auto remove after 3 seconds
    setTimeout(() => {
        if (successDiv.parentNode) {
            successDiv.remove();
        }
    }, 3000);
}

async function saveRegulator(closeAfter = false) {
    console.log('=== SAVING REGULATOR ===');
    
    // Check if there are any changes
    if (!hasFormChanges) {
        // Still save custom fields even if main form hasn't changed
        if (window.customFieldsContext && window.customFieldsContext.saveValues) {
            try {
                const id = parseId();
                await window.customFieldsContext.saveValues(id);
                console.log('✅ Custom fields saved successfully');
                showSuccessMessage(window.I18n ? window.I18n.t('regulator.messages.saved') : 'UPDATES SAVED');
            } catch (error) {
                console.error('Error saving custom fields:', error);
            }
        } else {
            showSuccessMessage(window.I18n ? window.I18n.t('regulator.messages.noChanges') : 'NO CHANGES', true);
        }
        if (closeAfter) {
            setTimeout(() => {
                const id = parseId();
                window.location.href = `/view/regulator/regulator.html?id=${id}`;
            }, 1500);
        }
        return;
    }
    
    // Sync rich-text editor content back to textarea before reading
    if (typeof syncAdvancedRichTextToTextarea === 'function') {
        syncAdvancedRichTextToTextarea('regulatorDescription');
    }

    // Get form values
    const regulatorName = document.getElementById('regulatorName')?.value?.trim();
    const regulatorShortName = document.getElementById('regulatorShortName')?.value?.trim();
    const regulatorDescription = document.getElementById('regulatorDescription')?.value?.trim();
    const segmentId = segmentField ? segmentField.getValue() : 1;
    
    console.log('Form values:', {
        regulatorName,
        regulatorShortName,
        regulatorDescription
    });
    
    // Validate required fields
    const missingFields = [];
    if (!regulatorName) missingFields.push(window.I18n ? window.I18n.t('regulator.labels.name') : 'Name');
    if (!regulatorShortName) missingFields.push(window.I18n ? window.I18n.t('regulator.labels.shortName') : 'Short Name');
    if (segmentField && !segmentField.validate()) missingFields.push(window.I18n ? window.I18n.t('regulator.labels.segment') : 'Segment');
    if (missingFields.length) {
        alert(window.I18n ? window.I18n.t('regulator.messages.pleaseFillIn', { fields: missingFields.join(', ') }) : 'Please fill in: ' + missingFields.join(', '));
        return false;
    }
    
    // Prepare payload - using field names expected by the servlet
    const payload = {
        primaryName: regulatorName,
        shortName: regulatorShortName,
        description: regulatorDescription || null,
        segmentId
    };
    
    const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
    
    try {
        buttons.forEach(b => { 
            if (b) { 
                b.disabled = true; 
                b.dataset._txt = b.textContent; 
                b.textContent = (window.I18n ? window.I18n.t('message.saving') : 'Saving...'); 
            }
        });
        
        const id = parseId();
        console.log('=== CALLING updateRegulator API ===');
        console.log('ID:', id);
        console.log('Payload:', payload);
        
        const response = await fetch(`/api/regulator/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });
        
        console.log('Save response:', response);
        
        if (response.ok) {
            console.log('Regulator saved successfully');
            
            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                try {
                    await window.customFieldsContext.saveValues(id);
                    console.log('✅ Custom fields saved successfully');
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                }
            }
            
            // Save geography changes
            try {
                await saveGeographyChanges();
                console.log('Geography changes saved successfully');
            } catch (error) {
                console.error('Error saving geography changes:', error);
                // Don't fail the whole save if geography save fails
            }
            
            // Release lock after successful save
            await window.LockInitHelper.releaseLock();
            
            // Mark form as clean after successful save
            markAsClean();
            
            if (closeAfter) { 
                showSuccessMessage(window.I18n ? window.I18n.t('regulator.messages.updatesSaved') : 'UPDATES SAVED');
                setTimeout(() => {
                    console.log('Redirecting to regulator view with ID:', id);
                    window.location.href = `/view/regulator/regulator.html?id=${id}`;
                }, 1500);
            } else { 
                showSuccessMessage(window.I18n ? window.I18n.t('regulator.messages.updatesSaved') : 'UPDATES SAVED');
            }
            
            return true;
        } else {
            const errorData = await response.json();
            console.error('Save failed:', errorData);
            const detail = errorData.error || errorData.message || 'Unknown error';
            alert(window.I18n ? window.I18n.t('regulator.messages.failedToSave', { error: detail }) : ('Failed to save regulator: ' + detail));
            return false;
        }
    } catch (error) {
        console.error('Error saving regulator:', error);
        alert(typeof window.formatSaveError === 'function' ? window.formatSaveError(error) : (window.I18n ? window.I18n.t('regulator.messages.errorWhileSaving') : 'An error occurred while saving. Please try again.'));
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

// Load all geographies for dropdown
async function loadAllGeographies(segmentIdOverride = null) {
    console.log('=== LOADING ALL GEOGRAPHIES ===');
    
    try {
        const effectiveSegmentId = Number.isInteger(segmentIdOverride)
            ? segmentIdOverride
            : (segmentField ? segmentField.getValue() : currentRegulatorSegmentId) || 1;

        let geographies = [];

        if (effectiveSegmentId === 1) {
            const response = await fetch('/api/geography/list');
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            geographies = await response.json();
        } else {
            const [sameSegmentResponse, enterpriseResponse] = await Promise.all([
                fetch(`/api/geography/list?segmentId=${effectiveSegmentId}`),
                fetch('/api/geography/list?segmentId=1')
            ]);

            if (!sameSegmentResponse.ok) {
                throw new Error(`HTTP ${sameSegmentResponse.status}: ${sameSegmentResponse.statusText}`);
            }
            if (!enterpriseResponse.ok) {
                throw new Error(`HTTP ${enterpriseResponse.status}: ${enterpriseResponse.statusText}`);
            }

            const [sameSegmentGeographies, enterpriseGeographies] = await Promise.all([
                sameSegmentResponse.json(),
                enterpriseResponse.json()
            ]);

            const geographyMap = new Map();
            [...enterpriseGeographies, ...sameSegmentGeographies].forEach(geo => {
                if (geo && geo.id != null) {
                    geographyMap.set(geo.id, geo);
                }
            });
            geographies = Array.from(geographyMap.values());
        }
        
        console.log('=== LOADED ALL GEOGRAPHIES ===');
        console.log('All geographies:', geographies);
        
        allGeographies = Array.isArray(geographies) ? geographies : [];
        
    } catch (error) {
        console.error('Error loading all geographies:', error);
        allGeographies = [];
    }
}

// Load geographies data for the current regulator
async function loadGeographiesData() {
    console.log('=== LOADING GEOGRAPHIES DATA ===');
    
    try {
        const regulatorId = parseId();
        if (!regulatorId) {
            console.error('No regulator ID found');
            return;
        }
        
        // Ensure allGeographies is loaded first
        if (allGeographies.length === 0) {
            console.log('Waiting for allGeographies to load...');
            await loadAllGeographies();
        }
        
        const response = await fetch(`/api/regulator-x-geography/${regulatorId}`);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        const data = await response.json();
        
        console.log('Loaded geographies data:', data);
        console.log('Available allGeographies:', allGeographies.length);
        
        geographiesData = Array.isArray(data) ? data : [];
        originalGeographiesData = JSON.parse(JSON.stringify(geographiesData));
        geographiesToDelete = [];
        
        renderGeographiesTable();
        
        // Initialize event listeners for add/delete buttons
        initializeGeographiesTable();
        
    } catch (error) {
        console.error('Error loading geographies data:', error);
        console.error('Error details:', error.message);
        console.error('Error stack:', error.stack);
        
        // Initialize with empty data on error
        geographiesData = [];
        originalGeographiesData = [];
        geographiesToDelete = [];
        
        renderGeographiesTable();
        
        // Initialize event listeners even on error
        initializeGeographiesTable();
    }
}

// Render the geographies table
function renderGeographiesTable() {
    console.log('=== RENDER GEOGRAPHIES TABLE START ===');
    const tableBody = document.getElementById('geographyTableBody');
    if (!tableBody) {
        console.error('Geographies table body not found');
        return;
    }
    
    // Check if allGeographies is loaded
    if (allGeographies.length === 0) {
        console.warn('allGeographies not loaded yet, deferring render');
        return;
    }
    
    console.log('Rendering geographies table with', geographiesData.length, 'geographies');
    console.log('Available geographies for dropdown:', allGeographies.length);
    
    // Clear existing content
    tableBody.innerHTML = '';
    
    // Add existing geographies (excluding deleted ones)
    geographiesData.forEach((geography, index) => {
        if (!geographiesToDelete.includes(geography.id || geography.ID)) {
            const row = createGeographyRow(geography, index);
            tableBody.appendChild(row);
        }
    });
    
    // Add empty row for new entries
    const emptyRow = createGeographyRow(null, geographiesData.length);
    tableBody.appendChild(emptyRow);
    
    console.log('=== RENDER GEOGRAPHIES TABLE END ===');
}

// Create a geography row (existing or new)
function createGeographyRow(relationship, index) {
    console.log('Creating geography row for:', relationship, 'at index:', index);
        const row = document.createElement('tr');
    row.className = relationship ? 'data-row' : 'empty-row';
    row.setAttribute('data-geography-index', index);
    
    if (relationship) {
        row.setAttribute('data-geography-id', relationship.id || relationship.ID);
        console.log('Setting geography ID:', relationship.id || relationship.ID);
    }
    
    // Geography dropdown
        const geographyOptions = allGeographies.map(g => {
        const isSelected = relationship && (relationship.geographyId || relationship.Geography_ID) == g.id;
            return `<option value="${g.id}" ${isSelected ? 'selected' : ''}>${escapeHtml(g.primaryName || g.PrimaryName)}</option>`;
        }).join('');
        
        row.innerHTML = `
            <td>
            <select class="form-select" data-field="geography">
                    <option value="">${window.I18n ? window.I18n.t('regulator.placeholders.selectGeography') : 'Select Geography'}</option>
                    ${geographyOptions}
                </select>
            </td>
            <td>
            <input type="text" class="form-control" data-field="description" value="${escapeHtml(relationship ? (relationship.description || relationship.Description || '') : '')}" placeholder="${window.I18n ? window.I18n.t('regulator.placeholders.enterDescription') : 'Enter description...'}">
            </td>
        <td class="action-column">
            <button type="button" class="btn btn-sm btn-success add-geography-btn" title="Add">+</button>
            <button type="button" class="btn btn-sm btn-danger delete-geography-btn" title="Delete">-</button>
            </td>
        `;
    
    // Add change listeners to track modifications
    const selects = row.querySelectorAll('select');
    const inputs = row.querySelectorAll('input');
    
    [...selects, ...inputs].forEach(element => {
        element.addEventListener('change', () => {
            markAsDirty();
        });
    });
    
    return row;
}

function initializeGeographiesTable() {
    const tableBody = document.getElementById('geographyTableBody');
    if (!tableBody) {
        console.warn('Geographies table body not found');
        return;
    }
    
    // Use event delegation for add and delete buttons (like regulations table)
    tableBody.addEventListener('click', function(e) {
        if (e.target.closest('.add-geography-btn')) {
            e.preventDefault();
            addGeographyRow();
        } else if (e.target.closest('.delete-geography-btn')) {
            e.preventDefault();
            deleteGeographyRow(e);
        }
    });
}

function addGeographyRow() {
    console.log('=== ADD GEOGRAPHY ROW ===');
    const tableBody = document.getElementById('geographyTableBody');
    const newEmptyRow = createGeographyRow(null, geographiesData.length);
    tableBody.appendChild(newEmptyRow);
    
    // Re-initialize event listeners
    initializeGeographiesTable();
    
    markAsDirty();
}

function deleteGeographyRow(event) {
    console.log('=== DELETE GEOGRAPHY ROW ===');
    const row = event.target.closest('tr');
    if (!row) return;
    
    const tableBody = document.getElementById('geographyTableBody');
    if (!tableBody) return;
    
    const allRows = tableBody.querySelectorAll('tr');
    const isFirstRow = row === allRows[0];
    const geographyId = row.getAttribute('data-geography-id');
    
    if (geographyId) {
        // Existing row with data
        console.log('Deleting row with geographyId:', geographyId, 'isFirstRow:', isFirstRow);
        if (isFirstRow) {
            // For first row, mark for deletion but preserve structure (clear data only)
            if (!geographiesToDelete.includes(parseInt(geographyId))) {
                geographiesToDelete.push(parseInt(geographyId));
                console.log('Added to deletion list:', geographiesToDelete);
            }
            clearGeographyRowData(row);
            markAsDirty();
            } else {
            // For other rows, remove entirely and mark for deletion
            if (!geographiesToDelete.includes(parseInt(geographyId))) {
                geographiesToDelete.push(parseInt(geographyId));
                console.log('Added to deletion list:', geographiesToDelete);
            }
            row.remove();
            markAsDirty();
        }
    } else {
        // Empty row
        console.log('Deleting empty row, isFirstRow:', isFirstRow, 'totalRows:', allRows.length);
        if (isFirstRow && allRows.length === 1) {
            // Don't delete the only row (first row mechanism)
            console.log('Cannot delete the first and only row');
            return;
        }
        // Remove the row
            row.remove();
    }
}

// Clear geography row data (for first row deletion mechanism)
function clearGeographyRowData(row) {
    console.log('Clearing geography row data');
    const geographySelect = row.querySelector('[data-field="geography"]');
    const descriptionInput = row.querySelector('[data-field="description"]');
    
    if (geographySelect) {
        geographySelect.value = '';
    }
    if (descriptionInput) {
        descriptionInput.value = '';
    }
    
    // Remove the data-geography-id attribute to make it an empty row
    row.removeAttribute('data-geography-id');
    row.className = 'empty-row';
    console.log('Row cleared, now has class:', row.className, 'data-geography-id:', row.getAttribute('data-geography-id'));
}

async function saveGeographyChanges() {
    console.log('=== SAVING GEOGRAPHY CHANGES ===');
    
    const tableBody = document.getElementById('geographyTableBody');
    if (!tableBody) {
        console.error('Geography table body not found');
        return;
    }
    
    const rows = tableBody.querySelectorAll('tr');
    const regulatorId = parseId();
    
    if (!regulatorId) {
        console.error('No regulator ID found');
        return;
    }
    
    console.log('Processing', rows.length, 'geography rows');
    
    const inserts = [];
    const updates = [];
    const deletes = geographiesToDelete;
    
    for (const row of rows) {
        const geographyId = row.querySelector('[data-field="geography"]')?.value;
        const description = row.querySelector('[data-field="description"]')?.value?.trim() || '';
        const relationshipId = row.getAttribute('data-geography-id');
        
        // Skip empty rows
        if (!geographyId) {
            continue;
        }
        
                const payload = {
                    regulatorId: regulatorId,
            geographyId: parseInt(geographyId),
                    description: description || null,
                    userId: 1 // TODO: Get from session
                };
                
        if (relationshipId) {
            // Update existing
            payload.id = parseInt(relationshipId);
            updates.push(payload);
        } else {
            // Insert new
            inserts.push(payload);
        }
    }
    
    console.log('Geography changes to process:', { inserts, updates, deletes });
    
    try {
        // Process deletions
        for (const id of deletes) {
            console.log('Deleting geography relationship:', id);
            try {
                const response = await fetch(`/api/regulator-x-geography/${id}`, {
                    method: 'DELETE'
                });
                
                console.log('Delete response status:', response.status, 'ok:', response.ok);
                
                if (!response.ok) {
                    const errorData = await response.json();
                    console.error('Failed to delete geography relationship:', errorData);
                    throw new Error('Failed to delete geography relationship: ' + (errorData.message || 'Unknown error'));
                } else {
                    const responseData = await response.json();
                    console.log('Successfully deleted geography relationship:', id, 'Response:', responseData);
                }
            } catch (error) {
                console.error('Error during DELETE request:', error);
                throw error;
            }
        }
        
        // Process updates
        for (const payload of updates) {
                console.log('Updating geography relationship:', payload);
            const response = await fetch(`/api/regulator-x-geography/${payload.id}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(payload)
                });
                
                if (!response.ok) {
                    const errorData = await response.json();
                console.error('Failed to update geography relationship:', errorData);
                throw new Error('Failed to update geography relationship: ' + (errorData.message || 'Unknown error'));
            }
        }
        
        // Process inserts
        for (const payload of inserts) {
                console.log('Creating geography relationship:', payload);
                const response = await fetch('/api/regulator-x-geography', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(payload)
                });
                
                if (!response.ok) {
                    const errorData = await response.json();
                console.error('Failed to create geography relationship:', errorData);
                throw new Error('Failed to create geography relationship: ' + (errorData.message || 'Unknown error'));
            }
        }
        
        // Reset deletion list
        geographiesToDelete = [];
        
        // Update local data to reflect deletions
        geographiesData = geographiesData.filter(geo => !deletes.includes(geo.id || geo.ID));
        console.log('Updated local geographies data:', geographiesData);
        
        // Re-render the table with updated data
        renderGeographiesTable();
        initializeGeographiesTable();
        
        console.log('Geography changes saved successfully');
        
        } catch (error) {
        console.error('Error saving geography changes:', error);
            alert(window.I18n ? window.I18n.t('regulator.messages.errorSavingGeography') : 'An error occurred while saving geography changes. Please try again.');
        throw error;
        }
}

function setupEventListeners() {
    console.log('Setting up event listeners...');
    
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
        await saveRegulator(false);
    });
    
    if (saveCloseBtn) saveCloseBtn.addEventListener('click', async () => {
        await saveRegulator(true);
    });
    
    if (closeBtn) {
        closeBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            console.log('Close button clicked!');
            handleClose();
        });
    }

    // Show editor button – advanced rich text editor
    const showEditorBtn = document.getElementById('showEditorBtn');
    if (showEditorBtn) {
        showEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('regulatorDescription', showEditorBtn);
        });
    }
    
    // Function to handle close action
    async function handleClose() {
        const id = parseId();
        console.log('Parsed ID:', id);
        
        // Release lock before closing
        if (window.LockInitHelper) {
            await window.LockInitHelper.releaseLock();
        } else if (window.currentLockManager) {
            await window.currentLockManager.releaseLock();
        }
        
        window.onbeforeunload = null;
        if (id) {
            const targetUrl = `/view/regulator/regulator.html?id=${id}`;
            console.log('Redirecting to:', targetUrl);
            window.location.href = targetUrl;
        } else {
            console.error('No ID found, redirecting to regulator list');
            window.location.href = '/regulator.html';
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
    
    // Geography table event listeners
    // Geography inline editing is handled by onclick events in the HTML
}

async function initializePage() {
    const id = parseId();
    if (!id) {
        console.error('No regulator ID found');
        return;
    }
    
    currentRegulatorId = id;
    
    try {
        console.log('=== INITIALIZING PAGE ===');
        console.log('Loading regulator data and relationships...');

        // Initialize segment field (no chooser on view page, but required on edit)
        if (window.SegmentField) {
            try {
                segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    // Removed defaultValue - let API data set the correct value
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Regulator',
                    fieldId: 'regulatorSegment',
                    errorId: 'regulatorSegmentError',
                    onChange: async () => {
                        await loadAllGeographies(segmentField ? segmentField.getValue() : null);
                        await loadGeographiesData();
                        return true;
                    }
                });
            } catch (err) {
                console.error('Error initializing segment field:', err);
            }
        }

        // Load regulator after segment field init so server segment is applied correctly.
        await loadRegulator(id);

        // Ensure segment keeps backend value (prevents fallback to enterprise on save).
        if (segmentField && Number.isInteger(parseInt(currentRegulatorSegmentId, 10))) {
            segmentField.setValue(parseInt(currentRegulatorSegmentId, 10));
        }

        // Load geographies with current segment context
        await loadAllGeographies(parseInt(currentRegulatorSegmentId, 10));
        
        // Then load geographies data (depends on allGeographies being loaded)
        await loadGeographiesData();
        
        // Initialize custom fields
        if (window.CustomFields) {
            try {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Regulator',
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
        setupEventListeners();
        
        console.log('Regulator edit page initialized successfully');
        
        // Switch to the specified tab (if any)
        const targetTab = parseTab();
        console.log('Switching to tab:', targetTab);
        switchTab(targetTab);
        
    } catch (error) {
        console.error('Error initializing regulator edit page:', error);
    }
}
