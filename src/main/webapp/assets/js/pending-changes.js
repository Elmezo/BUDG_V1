/**
 * Pending Changes Module
 * 
 * Handles the "View Original | View Changes" toggle on object view pages.
 * 
 * Logic:
 * - When an object has an active auto-created CR, the toggle appears
 * - "View Original" shows data from the database (actual object table)
 * - "View Changes" shows data with pending changes overlaid
 * - Edits made during active CR are stored in pending_changes table, not the object table
 */
console.log('[PendingChanges] Script loaded');

window.PendingChanges = (function() {
    'use strict';
    
    console.log('[PendingChanges] Module initializing...');

    // Store the original data for restoration
    let originalData = null;
    let pendingChanges = []; // Initialize as empty array to prevent null errors
    let currentView = 'original'; // 'original' or 'changes'

    /**
     * Check if an object is under revision (has active auto-created CR)
     * @param {string} facetType - The facet type (Glossary, Dataset, System, Process)
     * @param {number} objectId - The object ID
     * @returns {Promise<Object>} - { underRevision, changeRequestId, hasPendingChanges }
     */
    async function getRevisionStatus(facetType, objectId) {
        console.log('═══════════════════════════════════════════════════════════');
        console.log('🌐 [PendingChanges] getRevisionStatus START');
        console.log('   Parameters:');
        console.log('     FacetType:', facetType);
        console.log('     ObjectId:', objectId);
        
        const url = `/api/pending-changes/status/${facetType}/${objectId}`;
        console.log('   📡 API URL:', url);
        console.log('   ⏳ Sending fetch request...');
        
        try {
            const startTime = performance.now();
            const response = await fetch(url);
            const endTime = performance.now();
            
            console.log('   ✅ Response received');
            console.log('     Status Code:', response.status);
            console.log('     Status Text:', response.statusText);
            console.log('     Response Time:', (endTime - startTime).toFixed(2), 'ms');
            console.log('     Headers:', Object.fromEntries(response.headers.entries()));
            
            if (!response.ok) {
                console.error('   ❌ HTTP Error:', response.status, response.statusText);
                const text = await response.text();
                console.error('   ❌ Error Response Body:', text);
                console.log('═══════════════════════════════════════════════════════════');
                return { underRevision: false };
            }
            
            const data = await response.json();
            if (window.__BUDG_DEBUG__) {
                console.log('   📦 Response Data:', { success: data.success, facetType: data.facetType, objectId: data.objectId, underRevision: data.underRevision, hasPendingChanges: data.hasPendingChanges, changeRequestId: data.changeRequestId });
            }
            return data;
        } catch (error) {
            console.error('   ❌ Exception occurred:', error);
            console.error('   Error Name:', error.name);
            console.error('   Error Message:', error.message);
            console.error('   Error Stack:', error.stack);
            console.log('═══════════════════════════════════════════════════════════');
            return { underRevision: false };
        }
    }

    /**
     * Get pending changes mappings for an object (new model)
     * @param {string} facetType - The facet type
     * @param {number} objectId - The object ID
     * @returns {Promise<Object>} - Mappings object { area_key: nobject_id, ... }
     */
    async function getPendingMappings(facetType, objectId) {
        console.log('   📡 [PendingChanges] getPendingMappings - Fetching...');
        const url = `/api/pending-changes/mappings/${facetType}/${objectId}`;
        console.log('     URL:', url);
        
        try {
            const response = await fetch(url);
            console.log('     Response status:', response.status);
            
            if (!response.ok) {
                console.error('     ❌ HTTP Error:', response.status);
                const text = await response.text();
                console.error('     Error body:', text);
                return {};
            }
            
            const data = await response.json();
            console.log('     ✅ Response received');
            console.log('     Mappings:', data.mappings || {});
            console.log('     Count:', data.count || 0);
            
            return data.mappings || {};
        } catch (error) {
            console.error('     ❌ Exception:', error);
            console.error('     Error details:', {
                name: error.name,
                message: error.message,
                stack: error.stack
            });
            return {};
        }
    }
    
    /**
     * Get pending changes for an object (legacy - kept for compatibility)
     * @deprecated Use getPendingMappings instead
     */
    async function getPendingChanges(facetType, objectId) {
        // In new model, we don't have field-level changes
        // Return empty array for compatibility
        return [];
    }

    /**
     * Initialize the revision toggle on a view page
     * @param {string} facetType - The facet type
     * @param {number} objectId - The object ID
     * @param {string} titleSelector - CSS selector for the title element to append toggle to
     * @param {Object} dataAccessor - Object with methods to get/set field values
     */
    async function initToggle(facetType, objectId, titleSelector, dataAccessor) {
        console.log('═══════════════════════════════════════════════════════════');
        console.log('🎯 [PendingChanges] initToggle START');
        console.log('   Parameters:');
        console.log('     FacetType:', facetType);
        console.log('     ObjectId:', objectId);
        console.log('     TitleSelector:', titleSelector);
        console.log('     DataAccessor:', dataAccessor ? 'provided' : 'null');

        // Concurrency guard (Process page may call load() twice quickly)
        const toggleKey = `${facetType}#${objectId}`;
        window.__pendingChangesToggleInit = window.__pendingChangesToggleInit || new Set();
        if (window.__pendingChangesToggleInit.has(toggleKey)) {
            console.log('   ⚠️ Toggle init already in-flight for', toggleKey, '- skipping');
            console.log('═══════════════════════════════════════════════════════════');
            return null;
        }
        window.__pendingChangesToggleInit.add(toggleKey);
        try {
            // Check if toggle already exists - prevent duplicates
            const existingToggle = document.querySelector('.revision-toggle-container');
            if (existingToggle) {
                console.log('   ⚠️ Toggle already exists - skipping initialization');
                console.log('═══════════════════════════════════════════════════════════');
                return null;
            }

            // Wait a bit to ensure DOM is ready
            console.log('   ⏳ Waiting 100ms for DOM to be ready...');
            await new Promise(resolve => setTimeout(resolve, 100));
            console.log('   ✅ DOM ready check complete');

        console.log('   🔍 Step 1: Checking revision status via API...');
        
            // Check revision status
            const status = await getRevisionStatus(facetType, objectId);
        
        console.log('   📊 Step 2: Processing API response...');
        console.log('     Status object:', status);
        console.log('     Status.underRevision:', status?.underRevision);
        console.log('     Status.changeRequestId:', status?.changeRequestId);
        console.log('     Status.hasPendingChanges:', status?.hasPendingChanges);
        
        // Check if edit workflow is enabled (even if no CR exists yet)
        let editWorkflowEnabled = false;
        try {
            if (window.DFCRUtils) {
                // Normalize facet name for DFCR lookup (Dataset -> Data Set, etc.)
                // Try multiple variations to handle different naming conventions
                const facetNameVariations = [];
                
                if (facetType === 'Dataset') {
                    facetNameVariations.push('Data Set', 'Dataset', 'Data Sets');
                } else if (facetType === 'System') {
                    facetNameVariations.push('System', 'Systems');
                } else if (facetType === 'Glossary') {
                    facetNameVariations.push('Glossary', 'Glossaries');
                } else {
                    facetNameVariations.push(facetType);
                }
                
                console.log('   📋 Trying facet name variations:', facetNameVariations);
                
                // Try each variation until we get a valid response
                for (const dfcrFacetName of facetNameVariations) {
                    try {
                        console.log('   📋 Trying DFCR lookup with facet name:', dfcrFacetName);
                        const dfcrInfo = await window.DFCRUtils.getInfo(dfcrFacetName);
                        
                        // Check if we got valid info (not just an error response)
                        if (dfcrInfo && (dfcrInfo.editWorkflowEnabled !== undefined || dfcrInfo.workflowEnabled !== undefined)) {
                            editWorkflowEnabled = dfcrInfo?.editWorkflowEnabled === true;
                            console.log('   ✅ DFCR Info found for facet:', dfcrFacetName);
                            console.log('   📋 DFCR Info - editWorkflowEnabled:', editWorkflowEnabled);
                            console.log('   📋 DFCR Info - editWorkflowId:', dfcrInfo?.editWorkflowId || 0);
                            console.log('   📋 DFCR Info - createWorkflowId:', dfcrInfo?.createWorkflowId || 0);
                            break; // Success, stop trying variations
                        }
                    } catch (e) {
                        console.log('   ⚠️ Failed to get DFCR info for:', dfcrFacetName, e.message);
                        continue; // Try next variation
                    }
                }
            }
        } catch (e) {
            console.warn('   ⚠️ Could not check DFCR settings:', e);
        }
        
        // Show toggle if: object is under revision OR edit workflow is enabled (CR will be created on first save)
        const isUnderRevision = status && status.underRevision;
        const shouldShowToggle = isUnderRevision || editWorkflowEnabled;
        
        // If not under revision and edit workflow not enabled, don't show the toggle
        if (!shouldShowToggle) {
            if (window.__BUDG_DEBUG__) {
                console.log('   [PendingChanges] Toggle hidden: not under revision and edit workflow not enabled');
            }
            return null;
        }

        // Load pending mappings (new model)
        console.log('   🔍 Step 4: Loading pending mappings...');
        const mappings = await getPendingMappings(facetType, objectId) || {};
        console.log('   ✅ Loaded mappings:', Object.keys(mappings).length, 'area(s)');
        if (Object.keys(mappings).length > 0) {
            console.log('   📋 Mappings:', mappings);
        }
        
        // Initialize pendingChanges to empty array for compatibility (new model doesn't use field-level changes)
        pendingChanges = pendingChanges || [];

        // Find the title container - try multiple selectors with retry
        console.log('   🔍 Step 5: Finding title container in DOM...');
        console.log('     Primary selector:', titleSelector);
        let titleContainer = null;
        let attempts = 0;
        const maxAttempts = 5;
        
        while (!titleContainer && attempts < maxAttempts) {
            console.log(`     Attempt ${attempts + 1}/${maxAttempts}:`);
            
            const selectors = [
                titleSelector,
                '.page-title-text',
                '.page-title-main',
                '.page-title',
                '[class*="page-title"]'
            ];
            
            for (const selector of selectors) {
                const element = document.querySelector(selector);
                if (element) {
                    titleContainer = element;
                    console.log(`       ✅ Found with selector: '${selector}'`);
                    break;
                } else {
                    console.log(`       ❌ Not found: '${selector}'`);
                }
            }
            
            if (!titleContainer) {
                attempts++;
                console.log(`     ⏳ Retrying in 200ms... (attempt ${attempts}/${maxAttempts})`);
                await new Promise(resolve => setTimeout(resolve, 200));
            }
        }
        
        if (!titleContainer) {
            console.error('   ❌ ERROR: Title container not found after', maxAttempts, 'attempts');
            console.error('     Tried selectors:', [
                titleSelector,
                '.page-title-text',
                '.page-title-main',
                '.page-title',
                '[class*="page-title"]'
            ]);
            
            const allTitleElements = Array.from(document.querySelectorAll('[class*="title"]'));
            console.error('     Available elements with "title" in class:', allTitleElements.length);
            allTitleElements.forEach((el, idx) => {
                console.error(`       [${idx}]`, {
                    className: el.className,
                    id: el.id,
                    tagName: el.tagName,
                    textContent: el.textContent?.substring(0, 50)
                });
            });
            console.log('═══════════════════════════════════════════════════════════');
            return null;
        }

        console.log('   ✅ Title container found!');
        console.log('     Element:', titleContainer);
        console.log('     ClassName:', titleContainer.className);
        console.log('     ID:', titleContainer.id || 'none');
        console.log('     TagName:', titleContainer.tagName);

        // Create the toggle HTML for under revision state
        console.log('   🔍 Step 6: Creating toggle HTML...');
        // MANDATORY: Always default to 'original' view on page load/refresh
        // Clear any saved 'changes' from sessionStorage to ensure default is always 'original'
        const viewStorageKey = `pendingChanges:view:${facetType}#${objectId}`;
        try {
            if (typeof sessionStorage !== 'undefined') {
                const savedView = sessionStorage.getItem(viewStorageKey);
                if (savedView === 'changes') {
                    sessionStorage.removeItem(viewStorageKey);
                    console.log('   🧹 Cleared saved "changes" view from sessionStorage - defaulting to "original"');
                }
            }
        } catch (e) {
            // ignore storage errors
        }
        // Always default to 'original' - mandatory requirement
        const initialView = 'original';

        const toggleHtml = `
            <span class="revision-toggle-container under-revision">
                <a href="#" class="revision-toggle-link ${initialView === 'original' ? 'active' : ''}" data-view="original">${initialView === 'original' ? 'Viewing Original' : 'View Original'}</a>
                <span class="revision-toggle-separator">|</span>
                <a href="#" class="revision-toggle-link ${initialView === 'changes' ? 'active' : ''}" data-view="changes">${initialView === 'changes' ? 'Viewing Changes' : 'View Changes'}</a>
                <span class="revision-badge">UNDER REVISION</span>
            </span>
        `;
        console.log('   ✅ Toggle HTML created');
        console.log('     HTML length:', toggleHtml.length, 'characters');

        // Insert toggle - try to insert after the title main element, or append to container
        console.log('   🔍 Step 7: Inserting toggle into DOM...');
        const titleMain = titleContainer.querySelector('.page-title-main');
        console.log('     .page-title-main found:', titleMain ? '✅' : '❌');
        
        if (titleMain && titleMain.parentElement) {
            // Insert after the title main element
            console.log('     Strategy: Insert after .page-title-main');
            const toggleSpan = document.createElement('span');
            toggleSpan.innerHTML = toggleHtml;
            titleMain.parentElement.insertBefore(toggleSpan.firstElementChild, titleMain.nextSibling);
            console.log('   ✅ Toggle inserted after .page-title-main');
        } else {
            // Append to container
            console.log('     Strategy: Append to container');
            titleContainer.insertAdjacentHTML('beforeend', toggleHtml);
            console.log('   ✅ Toggle appended to container');
        }

        // Add click handlers
        console.log('   🔍 Step 8: Setting up event handlers...');
        const container = titleContainer.querySelector('.revision-toggle-container');
        if (container) {
            console.log('     ✅ Toggle container found in DOM');
            const links = container.querySelectorAll('.revision-toggle-link');
            console.log('     Found', links.length, 'toggle link(s)');
            
            links.forEach((link, idx) => {
                const view = link.dataset.view;
                console.log(`       Link ${idx + 1}: view="${view}"`);
                link.addEventListener('click', (e) => {
                    console.log(`[PendingChanges] Toggle clicked: switching to "${view}" view`);
                    e.preventDefault();
                    switchView(view, container, facetType, objectId, dataAccessor);
                });
            });
            console.log('   ✅ Event handlers attached');
        } else {
            console.error('   ❌ ERROR: Toggle container not found after insertion!');
        }

        console.log('   ✅ Toggle added successfully!');
        console.log('   📋 Final state:');
        console.log('     Under Revision: ✅');
        console.log('     CR ID:', status?.changeRequestId || 'undefined');
        console.log('     Pending Mappings Count:', Object.keys(mappings || {}).length);
        console.log('     Toggle Visible: ✅');
        console.log('═══════════════════════════════════════════════════════════');

        // Set current view to 'changes' (default state when under revision)
        // DO NOT dispatch event here - this would cause infinite loops when other tabs load.
        // The event should only be dispatched when the user CLICKS the toggle.
        currentView = initialView;
        console.log(`   📋 Initial view set to "${initialView}" (no event dispatched - tabs will load with correct view on their own)`);
        
        // If dataAccessor has applyChanges, call it (for Summary tab field overlays)
        if (dataAccessor && typeof dataAccessor.applyChanges === 'function') {
            // For Summary tab, we still use field-level overlays if provided
            // The view file should handle reloading with ?view=changes for other tabs
            console.log('   ✅ Data accessor provided - view file will handle reload');
        } else {
            console.log('   ⚠️ No data accessor - view file must handle reload via event');
        }

        // Store reference for external access
        return {
            status,
            pendingChanges,
            isUnderRevision: true,
            switchToOriginal: () => switchView('original', container, facetType, objectId, dataAccessor),
            switchToChanges: () => switchView('changes', container, facetType, objectId, dataAccessor)
        };
        } finally {
            // Always release the in-flight guard
            try {
                window.__pendingChangesToggleInit.delete(toggleKey);
            } catch (_) {}
        }
    }

    /**
     * Switch between Original and Changes view
     */
    async function switchView(view, container, facetType, objectId, dataAccessor) {
        console.log('[PendingChanges] Switching to view:', view);
        currentView = view;

        // Persist per-object view for this session (so refresh keeps your last selection)
        try {
            const key = `pendingChanges:view:${facetType}#${objectId}`;
            if (typeof sessionStorage !== 'undefined') {
                sessionStorage.setItem(key, view);
            }
        } catch (e) {
            // ignore storage errors (private mode / disabled)
        }

        // Update toggle UI
        container.querySelectorAll('.revision-toggle-link').forEach(link => {
            if (link.dataset.view === view) {
                link.classList.add('active');
                link.textContent = view === 'original' ? 'Viewing Original' : 'Viewing Changes';
            } else {
                link.classList.remove('active');
                link.textContent = view === 'original' ? 'View Changes' : 'View Original';
            }
        });

        // Get mappings if switching to changes view
        let mappings = {};
        if (view === 'changes') {
            mappings = await getPendingMappings(facetType, objectId);
        }

        // Dispatch event for page to handle data reload
        document.dispatchEvent(new CustomEvent('pendingChangesViewSwitch', {
            detail: { view, facetType, objectId, mappings }
        }));

        // If we have a data accessor with applyChanges, use it for Summary tab
        if (dataAccessor) {
            if (view === 'changes' && typeof dataAccessor.applyChanges === 'function') {
                // For Summary tab, the view file should reload data with ?view=changes
                // But if applyChanges is provided, it can also do field-level overlays
                console.log('[PendingChanges] Data accessor provided - view file should handle reload');
            } else if (view === 'original' && typeof dataAccessor.restoreOriginal === 'function') {
                dataAccessor.restoreOriginal();
            }
        }
    }

    /**
     * Apply pending changes to the view
     * In new model, this triggers data reload with ?view=changes or uses applyChanges callback
     */
    function applyPendingChangesToView(dataAccessor) {
        console.log('[PendingChanges] Applying pending changes to view');
        
        // If dataAccessor has applyChanges, use it (for Summary tab field overlays)
        if (dataAccessor && typeof dataAccessor.applyChanges === 'function') {
            console.log('[PendingChanges] Using dataAccessor.applyChanges callback');
            // The view file should have already loaded data with ?view=changes
            // and the applyChanges callback will handle field-level highlighting
            // For now, we just mark the body as viewing changes
            document.body.classList.add('viewing-pending-changes');
        } else {
            // For other tabs, the view file should reload data via event listener
            console.log('[PendingChanges] No dataAccessor - view file should handle reload via event');
            document.body.classList.add('viewing-pending-changes');
        }
    }

    /**
     * Apply a single change to an element
     * @param {Object} change - The change object with fieldName, oldValue, newValue
     * @param {string} mode - 'new' to show new value, 'old' to show old value
     */
    function applyChangeToElement(change, mode = 'new') {
        const fieldName = change.fieldName;
        const valueToShow = mode === 'old' ? (change.oldValue || '') : (change.newValue || '');

        console.log(`[PendingChanges] Applying ${mode} value to field '${fieldName}': '${valueToShow}'`);

        // Try to find the element displaying this field
        const selectors = [
            `[data-field="${fieldName}"]`,
            `[data-field="${fieldName.toLowerCase()}"]`,
            `.field-${fieldName.toLowerCase()}`,
            `#${fieldName.toLowerCase()}`
        ];

        for (const selector of selectors) {
            try {
                const element = document.querySelector(selector);
                if (element) {
                    // Apply the value (old or new based on mode)
                    if (valueToShow === null || valueToShow === undefined || valueToShow === '') {
                        element.innerHTML = '<span class="empty">Not specified</span>';
                    } else {
                        element.textContent = valueToShow;
                    }
                    
                    if (mode === 'new') {
                        element.classList.add('pending-change-applied');
                        element.classList.remove('original-value-shown');
                    } else {
                        element.classList.add('original-value-shown');
                        element.classList.remove('pending-change-applied');
                    }
                    
                    console.log(`[PendingChanges] Applied ${mode} value to`, selector, ':', valueToShow);
                    return;
                }
            } catch (e) {
                // Invalid selector, skip
            }
        }

        console.log('[PendingChanges] Could not find element for field:', fieldName);
    }

    /**
     * Restore original view (reload data without ?view=changes)
     */
    function restoreOriginalView(dataAccessor) {
        console.log('[PendingChanges] Restoring original view');

        // Remove indicators
        removeIndicators();

        // If dataAccessor has restoreOriginal, use it
        if (dataAccessor && typeof dataAccessor.restoreOriginal === 'function') {
            console.log('[PendingChanges] Using dataAccessor.restoreOriginal callback');
            dataAccessor.restoreOriginal();
        } else {
            // For other tabs, the view file should reload data without ?view=changes
            console.log('[PendingChanges] No dataAccessor - view file should handle reload via event');
        }

        // Remove visual styling
        document.body.classList.remove('viewing-pending-changes');
    }

    /**
     * Show indicator that changes are being viewed
     */
    function showChangesIndicator(count) {
        removeIndicators();

        const indicator = document.createElement('div');
        indicator.className = 'pending-changes-indicator';
        indicator.innerHTML = `
            <i class="fas fa-edit"></i>
            <span>Viewing ${count} pending change${count !== 1 ? 's' : ''} - these values will apply when the Change Request is completed</span>
        `;

        const mainContent = document.querySelector('.main-content') || 
                           document.querySelector('.view-container') || 
                           document.querySelector('main');
        if (mainContent && mainContent.firstChild) {
            mainContent.insertBefore(indicator, mainContent.firstChild);
        }
    }

    /**
     * Show message when there are no pending changes
     */
    function showNoChangesMessage() {
        removeIndicators();

        const indicator = document.createElement('div');
        indicator.className = 'pending-changes-indicator no-changes';
        indicator.innerHTML = `
            <i class="fas fa-info-circle"></i>
            <span>No edits have been made yet. Edit the object to see changes here.</span>
        `;

        const mainContent = document.querySelector('.main-content') || 
                           document.querySelector('.view-container') || 
                           document.querySelector('main');
        if (mainContent && mainContent.firstChild) {
            mainContent.insertBefore(indicator, mainContent.firstChild);
        }
    }

    /**
     * Remove all indicators
     */
    function removeIndicators() {
        document.querySelectorAll('.pending-changes-indicator').forEach(el => el.remove());
    }

    /**
     * Get the current view mode
     */
    function getCurrentView() {
        return currentView;
    }

    /**
     * Save a pending change to the server
     * @param {string} facetType - The facet type (Glossary, Dataset, System, Process)
     * @param {number} objectId - The object ID
     * @param {number} changeRequestId - The active CR ID
     * @param {string} fieldName - The field being changed
     * @param {string} oldValue - The original value
     * @param {string} newValue - The new value
     * @returns {Promise<Object>} - Response from server
     */
    async function savePendingChange(facetType, objectId, changeRequestId, fieldName, oldValue, newValue) {
        console.log('[PendingChanges] Saving pending change:', { facetType, objectId, changeRequestId, fieldName, oldValue, newValue });
        
        try {
            const response = await fetch('/api/pending-changes/save', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({
                    facetType,
                    objectId,
                    changeRequestId,
                    fieldName,
                    oldValue,
                    newValue
                })
            });
            
            const data = await response.json();
            console.log('[PendingChanges] Save response:', data);
            
            if (!response.ok) {
                throw new Error(data.error || 'Failed to save pending change');
            }
            
            return data;
        } catch (error) {
            console.error('[PendingChanges] Error saving pending change:', error);
            throw error;
        }
    }

    /**
     * Apply all pending changes for a CR (when CR is completed)
     * @param {number} changeRequestId - The CR ID
     * @returns {Promise<Object>} - Response from server
     */
    async function applyPendingChanges(changeRequestId) {
        console.log('[PendingChanges] Applying pending changes for CR:', changeRequestId);
        
        try {
            const response = await fetch(`/api/pending-changes/apply/${changeRequestId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include'
            });
            
            const data = await response.json();
            console.log('[PendingChanges] Apply response:', data);
            
            if (!response.ok) {
                throw new Error(data.error || 'Failed to apply pending changes');
            }
            
            return data;
        } catch (error) {
            console.error('[PendingChanges] Error applying pending changes:', error);
            throw error;
        }
    }

    /**
     * Discard all pending changes for a CR (when CR is cancelled)
     * @param {number} changeRequestId - The CR ID
     * @returns {Promise<Object>} - Response from server
     */
    async function discardPendingChanges(changeRequestId) {
        console.log('[PendingChanges] Discarding pending changes for CR:', changeRequestId);
        
        try {
            const response = await fetch(`/api/pending-changes/discard/${changeRequestId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include'
            });
            
            const data = await response.json();
            console.log('[PendingChanges] Discard response:', data);
            
            if (!response.ok) {
                throw new Error(data.error || 'Failed to discard pending changes');
            }
            
            return data;
        } catch (error) {
            console.error('[PendingChanges] Error discarding pending changes:', error);
            throw error;
        }
    }

    // ============== RELATIONSHIP CHANGES ==============

    /**
     * Get pending relationship changes for an object
     * @param {string} facetType - The facet type (Glossary, Dataset, System, Process)
     * @param {number} objectId - The object ID
     * @param {string} [relationshipType] - Optional filter by relationship type
     * @returns {Promise<Object>} - { success, changes: [...] }
     */
    async function getRelationshipChanges(facetType, objectId, relationshipType = null) {
        console.log('[PendingChanges] Getting relationship changes:', { facetType, objectId, relationshipType });
        
        try {
            let url = `/api/pending-changes/relationships/${facetType}/${objectId}`;
            if (relationshipType) {
                url += `?type=${encodeURIComponent(relationshipType)}`;
            }
            
            const response = await fetch(url, {
                credentials: 'include'
            });
            
            const data = await response.json();
            console.log('[PendingChanges] Relationship changes response:', data);
            
            if (!response.ok) {
                throw new Error(data.error || 'Failed to get relationship changes');
            }
            
            return data;
        } catch (error) {
            console.error('[PendingChanges] Error getting relationship changes:', error);
            return { success: false, changes: [], error: error.message };
        }
    }

    /**
     * Save a pending relationship change to the server
     * @param {Object} changeData - The relationship change data
     * @returns {Promise<Object>} - Response from server
     */
    async function saveRelationshipChange(changeData) {
        console.log('[PendingChanges] Saving relationship change:', changeData);
        
        try {
            const response = await fetch('/api/pending-changes/relationship/save', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(changeData)
            });
            
            const data = await response.json();
            console.log('[PendingChanges] Save relationship response:', data);
            
            if (!response.ok) {
                throw new Error(data.error || 'Failed to save relationship change');
            }
            
            return data;
        } catch (error) {
            console.error('[PendingChanges] Error saving relationship change:', error);
            throw error;
        }
    }

    /**
     * Delete a pending relationship change
     * @param {number} id - The pending change ID
     * @returns {Promise<Object>} - Response from server
     */
    async function deleteRelationshipChange(id) {
        console.log('[PendingChanges] Deleting relationship change:', id);
        
        try {
            const response = await fetch(`/api/pending-changes/relationship/delete/${id}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include'
            });
            
            const data = await response.json();
            console.log('[PendingChanges] Delete relationship response:', data);
            
            if (!response.ok) {
                throw new Error(data.error || 'Failed to delete relationship change');
            }
            
            return data;
        } catch (error) {
            console.error('[PendingChanges] Error deleting relationship change:', error);
            throw error;
        }
    }

    /**
     * Check if there are pending relationship changes for an object
     * @param {string} facetType - The facet type
     * @param {number} objectId - The object ID
     * @returns {Promise<boolean>}
     */
    async function hasRelationshipChanges(facetType, objectId) {
        const result = await getRelationshipChanges(facetType, objectId);
        return result.success && result.changes && result.changes.length > 0;
    }

        // Public API
    return {
        getRevisionStatus,
        getPendingChanges, // Legacy - returns empty array
        getPendingMappings, // New - returns mappings object
        initToggle,
        switchView,
        getCurrentView,
        applyPendingChangesToView,
        restoreOriginalView,
        savePendingChange, // Legacy - may not be used in new model
        applyPendingChanges,
        discardPendingChanges,
        // Relationship changes (legacy - may not be used in new model)
        getRelationshipChanges,
        saveRelationshipChange,
        deleteRelationshipChange,
        hasRelationshipChanges
    };
})();

