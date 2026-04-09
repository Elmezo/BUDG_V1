/**
 * DFCR Utilities - Client-side utilities for Default Change Request settings
 * 
 * This module provides utilities to:
 * - Check if workflow approval is enabled for a facet
 * - Get locked fields info for a facet
 * - Apply locked field styling to form elements
 */

window.DFCRUtils = (function() {
    'use strict';

    /**
     * Get DF_CR info for a facet
     * @param {string} facet - The facet name (Glossary, Data Set, Process, System)
     * @param {number} objectId - Optional object ID (for edit pages to check if object was created through default CR)
     * @returns {Promise<Object>} - The DF_CR info
     */
    async function getInfo(facet, objectId) {
        if (window.__BUDG_DEBUG__) console.log('[DFCR Utils] Getting info for facet:', facet, 'objectId:', objectId);
        
        if (!facet) {
            return { workflowEnabled: false };
        }

        try {
            if (window.__BUDG_DEBUG__) console.log('[DFCR Utils] Fetching info from API for facet:', facet, 'objectId:', objectId);
            let url = `/api/dfcr/info?facet=${encodeURIComponent(facet)}`;
            if (objectId != null && objectId !== undefined) {
                url += `&objectId=${encodeURIComponent(objectId)}`;
            }
            const response = await fetch(url);
            if (!response.ok) {
                console.warn('[DFCR Utils] Failed to get DF_CR info for facet:', facet, 'Status:', response.status);
                return { workflowEnabled: false };
            }

            const info = await response.json();
            if (window.__BUDG_DEBUG__) console.log('[DFCR Utils] Received info for facet:', facet, 'objectId:', objectId, info);
            return info;
        } catch (error) {
            console.error('[DFCR Utils] Error getting DF_CR info:', error);
            return { workflowEnabled: false };
        }
    }

    /**
     * Check if workflow is enabled for a facet
     * @param {string} facet - The facet name
     * @param {number} objectId - Optional object ID
     * @returns {Promise<boolean>}
     */
    async function isWorkflowEnabled(facet, objectId) {
        const info = await getInfo(facet, objectId);
        return info.workflowEnabled === true;
    }

    /**
     * Apply locked field styling to form elements
     * @param {string} facet - The facet name
     * @param {Object} fieldSelectors - Object mapping field names to their DOM selectors
     *   e.g., { status: '#statusSelect', lifecycle: '#lifecycleSelect' }
     * @param {Object} options - Optional settings
     *   e.g., { isEditPage: true, objectId: 123 } - if isEditPage is true, locks will NOT be applied (edit pages for existing objects)
     *   objectId is used on edit pages to check if object was created through default CR
     */
    async function applyLockedFields(facet, fieldSelectors, options = {}) {
        if (window.__BUDG_DEBUG__) console.log('[DFCR Utils] Applying locked fields for facet:', facet, 'selectors:', fieldSelectors, 'options:', options);
        
        // ALWAYS remove locks first to ensure clean state
        removeLockedFieldStyling(fieldSelectors);
        
        // IMPORTANT: Edit pages for EXISTING objects should NOT have locked fields
        // Locked fields only apply to CREATE pages (new objects)
        // EXCEPTION: Super admins always have fields locked when workflow is enabled
        if (options.isEditPage === true) {
            if (window.__BUDG_DEBUG__) console.log('[DFCR Utils] This is an EDIT page for existing object - checking if locks should be applied');
            // On edit pages, pass objectId to check if object was created through default CR
            // If NOT created through default CR and workflows are set, locks will NOT be applied
            // UNLESS user is super admin (super admins always follow workflow)
            const info = await getInfo(facet, options.objectId);
            if (window.__BUDG_DEBUG__) console.log('[DFCR Utils] Edit page info - statusLocked:', info.statusLocked, 'lifecycleLocked:', info.lifecycleLocked, 'isSuperAdmin:', info.isSuperAdmin);
            
            if (window.__BUDG_DEBUG__ && info.isSuperAdmin === true) {
                console.log('[DFCR Utils] Super admin on edit page - applying locks based on backend response');
            }
            
            // Apply locks if they are set (backend determines this based on super admin status and workflow rules)
            if (info.statusLocked && fieldSelectors.status) {
                const statusEl = document.querySelector(fieldSelectors.status);
                if (statusEl) {
                    applyLockToElement(statusEl, info.defaultStatusId, 'Status is locked by workflow settings');
                }
            }
            
            if (info.lifecycleLocked && fieldSelectors.lifecycle) {
                const lifecycleEl = document.querySelector(fieldSelectors.lifecycle);
                if (lifecycleEl) {
                    applyLockToElement(lifecycleEl, info.defaultLifecycleId, 'Lifecycle is locked by workflow settings');
                }
            }
            return;
        }
        
        const info = await getInfo(facet, options.objectId);
        console.log('[DFCR Utils] Got info for locked fields:', info);

        // Super admins always follow workflow - fields are always locked when workflow is enabled
        if (info.isSuperAdmin === true) {
            console.log('[DFCR Utils] Super admin detected - fields will be locked (workflow always applies)');
            // Continue to apply locks below, skip bypass checks
        } else {
            // IMPORTANT: Admin bypass ONLY affects admins, NOT regular users
            // Regular users should ALWAYS get locked fields when workflow is enabled, regardless of adminBypassEnabled
            // Only skip locking if the user is an admin AND bypass is enabled
            if (info.isAdmin === true && info.adminBypassEnabled === true) {
                console.log('[DFCR Utils] Admin bypass is ACTIVE (isAdmin=' + info.isAdmin + ', adminBypassEnabled=' + info.adminBypassEnabled + ') - NOT applying locks for admin');
                return;
            }
            
            // Regular users (isAdmin=false) should continue and get locks even if adminBypassEnabled=true
            if (!info.isAdmin && info.adminBypassEnabled === true) {
                console.log('[DFCR Utils] Regular user detected with admin bypass enabled - Admin bypass does NOT apply to regular users, will apply locks');
            }
        }

        if (!info.workflowEnabled) {
            console.log('[DFCR Utils] Workflow not enabled, NOT applying locks');
            return;
        }

        console.log('[DFCR Utils] Workflow enabled, checking field locks - statusLocked:', info.statusLocked, 'lifecycleLocked:', info.lifecycleLocked);

        // Apply status lock
        if (info.statusLocked && fieldSelectors.status) {
            const statusEl = document.querySelector(fieldSelectors.status);
            console.log('[DFCR Utils] Status element found:', !!statusEl, 'defaultStatusId:', info.defaultStatusId);
            if (statusEl) {
                applyLockToElement(statusEl, info.defaultStatusId, 'Status is locked by workflow settings');
            }
        }

        // Apply lifecycle lock
        if (info.lifecycleLocked && fieldSelectors.lifecycle) {
            const lifecycleEl = document.querySelector(fieldSelectors.lifecycle);
            console.log('[DFCR Utils] Lifecycle element found:', !!lifecycleEl, 'defaultLifecycleId:', info.defaultLifecycleId);
            if (lifecycleEl) {
                applyLockToElement(lifecycleEl, info.defaultLifecycleId, 'Lifecycle is locked by workflow settings');
            }
        }
    }

    /**
     * Apply lock styling to a single element
     * @param {HTMLElement} element - The form element to lock
     * @param {*} defaultValue - The default value to set
     * @param {string} tooltip - Tooltip text explaining the lock
     */
    function applyLockToElement(element, defaultValue, tooltip) {
        // Set the default value
        if (defaultValue !== null && defaultValue !== undefined) {
            element.value = String(defaultValue);
        }

        // Disable the element
        element.disabled = true;
        element.setAttribute('disabled', 'disabled');
        
        // Add locked styling
        element.classList.add('dfcr-locked');
        element.style.pointerEvents = 'none';
        element.style.cursor = 'not-allowed';
        element.title = tooltip;

        // Add lock icon if not already present
        const parent = element.parentElement;
        if (parent && !parent.querySelector('.dfcr-lock-icon')) {
            const lockIcon = document.createElement('span');
            lockIcon.className = 'dfcr-lock-icon';
            lockIcon.innerHTML = '🔒';
            lockIcon.title = tooltip;
            lockIcon.style.cssText = 'margin-left: 8px; cursor: help; font-size: 14px;';
            parent.appendChild(lockIcon);
        }
    }

    /**
     * Remove locked field styling from elements
     * @param {Object} fieldSelectors - Object mapping field names to their DOM selectors
     */
    function removeLockedFieldStyling(fieldSelectors) {
        Object.values(fieldSelectors).forEach(selector => {
            const element = document.querySelector(selector);
            if (element) {
                // Remove all lock-related attributes and styling
                element.disabled = false;
                element.removeAttribute('disabled');
                element.removeAttribute('readonly');
                element.classList.remove('dfcr-locked');
                element.style.pointerEvents = '';
                element.style.cursor = '';
                element.title = '';

                // Remove lock icon
                const parent = element.parentElement;
                if (parent) {
                    const lockIcon = parent.querySelector('.dfcr-lock-icon');
                    if (lockIcon) {
                        lockIcon.remove();
                    }
                }
            }
        });
    }

    /**
     * Clear the settings cache (useful when settings are updated)
     */
    function clearCache() {
        // Cache disabled; no-op
    }

    /**
     * Show a notification about workflow-related actions
     * @param {string} message - The message to show
     * @param {string} type - The notification type (info, success, warning, error)
     */
    function showWorkflowNotification(message, type = 'info') {
        if (typeof showNotification === 'function') {
            showNotification(message, type);
        } else {
            console.log(`[DFCR ${type}] ${message}`);
        }
    }

    // Add CSS for locked fields
    function addLockedFieldStyles() {
        if (document.getElementById('dfcr-styles')) return;

        const style = document.createElement('style');
        style.id = 'dfcr-styles';
        style.textContent = `
            .dfcr-locked {
                background-color: #f5f5f5 !important;
                cursor: not-allowed !important;
                opacity: 0.8;
            }
            .dfcr-lock-icon {
                color: #666;
                vertical-align: middle;
            }
            .dfcr-workflow-badge {
                display: inline-block;
                background-color: #4a90d9;
                color: white;
                padding: 2px 8px;
                border-radius: 4px;
                font-size: 11px;
                margin-left: 8px;
            }
            .dfcr-save-submit-btn {
                background-color: #248567 !important;
                color: white !important;
                border: none !important;
                padding: 8px 16px !important;
                border-radius: 4px !important;
                cursor: pointer !important;
                font-weight: 500 !important;
                margin-left: 8px !important;
            }
            .dfcr-save-submit-btn:hover {
                background-color: #1a6b52 !important;
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Check if edit workflow is enabled for a facet (for existing objects)
     * @param {string} facet - The facet name
     * @param {number} objectId - Optional object ID
     * @returns {Promise<boolean>}
     */
    async function isEditWorkflowEnabled(facet, objectId) {
        const info = await getInfo(facet, objectId);
        return info.editWorkflowEnabled === true;
    }

    /**
     * Add "Save & Submit" button for edit workflow on existing objects
     * @param {string} facet - The facet name
     * @param {string} buttonContainerSelector - CSS selector for the button container
     * @param {Function} onSaveSubmit - Callback function when Save & Submit is clicked
     * @param {number} objectId - Optional object ID
     */
    async function addSaveSubmitButton(facet, buttonContainerSelector, onSaveSubmit, objectId) {
        const info = await getInfo(facet, objectId);
        console.log('[DFCR Utils] Checking edit workflow for facet:', facet, 'objectId:', objectId, 'editWorkflowEnabled:', info.editWorkflowEnabled);
        
        // Only add button if edit workflow is enabled and admin bypass is not active
        if (!info.editWorkflowEnabled) {
            console.log('[DFCR Utils] Edit workflow not enabled, not adding Save & Submit button');
            return false;
        }
        
        // IMPORTANT: Admin bypass ONLY affects admins, NOT regular users
        // Only skip adding button if the user is an admin AND bypass is enabled
        if (info.isAdmin === true && info.adminBypassEnabled === true) {
            console.log('[DFCR Utils] Admin bypass enabled for admin, not adding Save & Submit button');
            return false;
        }
        
        // Regular users should still get the button even if adminBypassEnabled=true
        if (!info.isAdmin && info.adminBypassEnabled === true) {
            console.log('[DFCR Utils] Regular user with admin bypass enabled - Admin bypass does NOT apply, will add Save & Submit button');
        }
        
        const container = document.querySelector(buttonContainerSelector);
        if (!container) {
            console.warn('[DFCR Utils] Button container not found:', buttonContainerSelector);
            return false;
        }
        
        // Check if button already exists
        if (container.querySelector('.dfcr-save-submit-btn')) {
            console.log('[DFCR Utils] Save & Submit button already exists');
            return true;
        }
        
        // Create the Save & Submit button
        const saveSubmitBtn = document.createElement('button');
        saveSubmitBtn.type = 'button';
        saveSubmitBtn.className = 'dfcr-save-submit-btn';
        saveSubmitBtn.textContent = 'Save & Submit';
        saveSubmitBtn.title = 'Save your changes and automatically create/submit a change request for workflow approval. The changes will be applied once the change request is approved.';
        
        saveSubmitBtn.addEventListener('click', async () => {
            console.log('[DFCR Utils] Save & Submit clicked');
            if (typeof onSaveSubmit === 'function') {
                await onSaveSubmit();
            }
        });
        
        container.appendChild(saveSubmitBtn);
        console.log('[DFCR Utils] Save & Submit button added');
        return true;
    }

    /**
     * Create a change request for an edited object
     * @param {string} facet - The facet name
     * @param {number} objectId - The ID of the object being edited
     * @param {string} objectName - The name of the object
     * @returns {Promise<number|null>} - The created CR ID or null
     */
    async function createEditChangeRequest(facet, objectId, objectName) {
        try {
            const info = await getInfo(facet, objectId);
            if (!info.editWorkflowEnabled) {
                console.log('[DFCR Utils] Edit workflow not enabled, not creating CR');
                return null;
            }
            
            const response = await fetch('/api/changerequests', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    primaryName: `Edit request for ${facet}: ${objectName}`,
                    summary: `Auto-generated CR for EDIT: ${facet} ${objectId}`,
                    reference: `${facet} ${objectId}`,
                    crTypeId: info.defaultCrTypeId || 1,
                    crUrgencyId: info.defaultCrUrgencyId || 1,
                    crSeverityId: info.defaultCrSeverityId || 1
                })
            });
            
            if (!response.ok) {
                throw new Error('Failed to create change request');
            }
            
            const result = await response.json();
            console.log('[DFCR Utils] Created edit CR:', result);
            return result.id || result.changeRequestId;
        } catch (error) {
            console.error('[DFCR Utils] Error creating edit CR:', error);
            return null;
        }
    }

    /**
     * Re-apply DFCR locks after form population (stateless - relies on backend)
     * This function fetches fresh info from backend to get current Auto CR state
     * @param {string} facet - The facet name (Glossary, Data Set, Process, System)
     * @param {number} objectId - The object ID
     */
    async function reapplyDFCRLocks(facet, objectId) {
        if (window.__BUDG_DEBUG__) console.log(`[DFCR Utils] Re-applying locks for ${facet}, objectId: ${objectId}`);
        
        if (!facet || !objectId) {
            console.warn('[DFCR Utils] Missing facet or objectId for reapplyDFCRLocks');
            return;
        }
        
        // Determine field selectors based on facet
        let fieldSelectors = {};
        if (facet === 'Glossary') {
            fieldSelectors = { status: '#budgStatus', lifecycle: '#lifecycle' };
        } else if (facet === 'Data Set' || facet === 'Dataset') {
            fieldSelectors = { status: '#dsStatus', lifecycle: '#dsLifecycle' };
        } else if (facet === 'System') {
            fieldSelectors = { status: '#systemStatus', lifecycle: '#systemLifecycle' };
        } else if (facet === 'Process') {
            fieldSelectors = { status: '#processStatus', lifecycle: '#processLifecycle' };
        } else {
            console.warn(`[DFCR Utils] Unknown facet for reapplyDFCRLocks: ${facet}`);
            return;
        }
        
        // ⚠️ CRITICAL: ALWAYS remove locks first to ensure clean state
        // This ensures that if locks are no longer needed, they are removed
        removeLockedFieldStyling(fieldSelectors);
        if (window.__BUDG_DEBUG__) console.log(`[DFCR Utils] Removed existing locks for ${facet}`);
        
        const info = await getInfo(facet, objectId);
        if (window.__BUDG_DEBUG__) console.log(`[DFCR Utils] Backend info - statusLocked: ${info.statusLocked}, lifecycleLocked: ${info.lifecycleLocked}`);
        
        // Apply locks based on backend response (only if needed)
        if (info.statusLocked && fieldSelectors.status) {
            const statusEl = document.querySelector(fieldSelectors.status);
            if (statusEl) {
                applyLockToElement(statusEl, info.defaultStatusId, 'Status is locked by workflow settings');
                console.log(`[DFCR Utils] Applied status lock for ${facet}`);
            } else {
                console.warn(`[DFCR Utils] Status element not found: ${fieldSelectors.status}`);
            }
        } else if (fieldSelectors.status) {
            // Explicitly ensure status is unlocked if backend says it shouldn't be locked
            const statusEl = document.querySelector(fieldSelectors.status);
            if (statusEl) {
                statusEl.disabled = false;
                statusEl.classList.remove('dfcr-locked');
                statusEl.removeAttribute('readonly');
                if (window.__BUDG_DEBUG__) console.log(`[DFCR Utils] Ensured status is unlocked for ${facet}`);
            }
        }
        
        if (info.lifecycleLocked && fieldSelectors.lifecycle) {
            const lifecycleEl = document.querySelector(fieldSelectors.lifecycle);
            if (lifecycleEl) {
                applyLockToElement(lifecycleEl, info.defaultLifecycleId, 'Lifecycle is locked by workflow settings');
                console.log(`[DFCR Utils] Applied lifecycle lock for ${facet}`);
            } else {
                console.warn(`[DFCR Utils] Lifecycle element not found: ${fieldSelectors.lifecycle}`);
            }
        } else if (fieldSelectors.lifecycle) {
            // Explicitly ensure lifecycle is unlocked if backend says it shouldn't be locked
            const lifecycleEl = document.querySelector(fieldSelectors.lifecycle);
            if (lifecycleEl) {
                lifecycleEl.disabled = false;
                lifecycleEl.classList.remove('dfcr-locked');
                lifecycleEl.removeAttribute('readonly');
                if (window.__BUDG_DEBUG__) console.log(`[DFCR Utils] Ensured lifecycle is unlocked for ${facet}`);
            }
        }
        
        if (window.__BUDG_DEBUG__) console.log(`[DFCR Utils] Re-applied locks for ${facet} - statusLocked: ${info.statusLocked}, lifecycleLocked: ${info.lifecycleLocked}`);
    }

    // Initialize styles when module loads
    addLockedFieldStyles();

    // Public API
    return {
        getInfo,
        isWorkflowEnabled,
        isEditWorkflowEnabled,
        applyLockedFields,
        addSaveSubmitButton,
        createEditChangeRequest,
        removeLockedFieldStyling,
        clearCache,
        showWorkflowNotification,
        reapplyDFCRLocks
    };
})();

// Make reapplyDFCRLocks available globally for easy access
window.reapplyDFCRLocks = async function(facet, objectId) {
    if (window.DFCRUtils && window.DFCRUtils.reapplyDFCRLocks) {
        return await window.DFCRUtils.reapplyDFCRLocks(facet, objectId);
    } else {
        console.warn('[DFCR Utils] DFCRUtils not available yet, retrying...');
        // Retry after a short delay
        setTimeout(async () => {
            if (window.DFCRUtils && window.DFCRUtils.reapplyDFCRLocks) {
                return await window.DFCRUtils.reapplyDFCRLocks(facet, objectId);
            }
        }, 100);
    }
};
