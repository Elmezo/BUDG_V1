/**
 * Shared permission checking utilities for all impact-edit.js files
 * This file provides common functions for checking and applying edit permissions
 * across all facets (System, Dataset, Glossary, Policy, etc.)
 */

(function() {
    'use strict';
    
    // Map facet directory names to module names
    const FACET_MODULE_MAP = {
        'system': 'System',
        'dataset': 'Data Sets',
        'interface': 'Interface',
        'system-interface': 'Interface',
        'glossary': 'Glossary',
        'policy': 'Policy',
        'process': 'Process',
        'project': 'Project',
        'product': 'Product',
        'client': 'Client',
        'legal': 'Legal Entity',
        'legal-entity': 'Legal Entity',
        'LegalEntity': 'Legal Entity',
        'capability': 'Capability',
        'business-area': 'Business Area',
        'businessarea': 'Business Area',
        'committee': 'Committee',
        'regulation': 'Regulation'
    };
    
    /**
     * Get module name from facet directory or current page
     */
    function getModuleName(facetDir) {
        if (facetDir && FACET_MODULE_MAP[facetDir.toLowerCase()]) {
            return FACET_MODULE_MAP[facetDir.toLowerCase()];
        }
        
        // Try to detect from URL
        const path = window.location.pathname;
        const match = path.match(/\/([^\/]+)\//);
        if (match && FACET_MODULE_MAP[match[1].toLowerCase()]) {
            return FACET_MODULE_MAP[match[1].toLowerCase()];
        }
        
        return null;
    }
    
    /**
     * Check user permissions for editing impact relationships
     * @param {string} moduleName - Module name (e.g., "System", "Data Sets", "Glossary")
     * @param {number} objectId - Optional object ID to check stakeholder status
     * @returns {Promise<{userCanEdit: boolean, isAdminUser: boolean, hasRoleEditPermission: boolean}>}
     */
    window.checkImpactEditPermissions = async function(moduleName, objectId = null) {
        try {
            const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!meResp.ok) {
                return { userCanEdit: false, isAdminUser: false, hasRoleEditPermission: false };
            }
            
            const me = await meResp.json();
            const role = (me.role || me.Role || me.userRole || '').toString().toLowerCase();
            const isAdminUser = role === 'admin' || role === 'super admin' || role === 'super-admin' || 
                               role === 'super_admin' || role === 'suber admin';
            
            let hasRoleEditPermission = false;
            if (!isAdminUser && moduleName) {
                try {
                    const permResp = await fetch(`/api/user/permissions/${encodeURIComponent(moduleName)}`, { 
                        method: 'GET', 
                        credentials: 'include' 
                    });
                    if (permResp.ok) {
                        const permData = await permResp.json();
                        if (permData.success) {
                            hasRoleEditPermission = permData.canEdit === true || permData.isAdmin === true;
                        }
                    }
                } catch (permError) {
                    console.warn('[ImpactEdit] Error checking permissions:', permError);
                }
            }
            
            // Check stakeholder status if objectId is provided and user has role permission
            if (!isAdminUser && hasRoleEditPermission && objectId) {
                try {
                    const stakeholderResp = await fetch(
                        `/api/check-stakeholder/${encodeURIComponent(moduleName)}/${objectId}`, {
                        method: 'GET',
                        credentials: 'include'
                    });
                    
                    if (stakeholderResp.ok) {
                        const stakeholderData = await stakeholderResp.json();
                        if (!stakeholderData.isStakeholder) {
                            hasRoleEditPermission = false; // Deny if not stakeholder
                            console.warn('[ImpactEdit] User is not stakeholder on object', objectId);
                        }
                    } else {
                        // If API fails, deny access for security
                        hasRoleEditPermission = false;
                        console.warn('[ImpactEdit] Error checking stakeholder status');
                    }
                } catch (stakeholderError) {
                    console.warn('[ImpactEdit] Error checking stakeholder:', stakeholderError);
                    hasRoleEditPermission = false; // Fail securely
                }
            }
            
            const userCanEdit = isAdminUser || hasRoleEditPermission;
            
            return {
                userCanEdit,
                isAdminUser,
                hasRoleEditPermission
            };
        } catch (e) {
            console.error('Error checking user permissions:', e);
            return { userCanEdit: false, isAdminUser: false, hasRoleEditPermission: false };
        }
    };
    
    /**
     * Apply permissions to UI - hide/show edit buttons and disable selects
     * @param {boolean} userCanEdit - Whether user can edit
     * @param {string} containerSelector - CSS selector for container (default: '#impact')
     */
    window.applyImpactEditPermissions = function(userCanEdit, containerSelector = '#impact') {
        // Hide/show add/delete buttons
        const allEditButtons = document.querySelectorAll(`${containerSelector} button[onclick*="Row"], ${containerSelector} button[onclick*="add"], ${containerSelector} button[onclick*="delete"]`);
        allEditButtons.forEach(btn => {
            if (userCanEdit) {
                btn.style.display = '';
                btn.disabled = false;
            } else {
                btn.style.display = 'none';
                btn.disabled = true;
            }
        });
        
        // Make selects read-only if no edit permission
        const allSelects = document.querySelectorAll(`${containerSelector} select[data-field], ${containerSelector} select.form-control`);
        allSelects.forEach(select => {
            // Skip if already disabled by other logic
            if (select.hasAttribute('data-readonly')) return;
            
            select.disabled = !userCanEdit;
            if (!userCanEdit) {
                select.style.backgroundColor = '#f5f5f5';
                select.style.cursor = 'not-allowed';
            } else {
                select.style.backgroundColor = '';
                select.style.cursor = '';
            }
        });
        
        // Make input fields read-only
        const allInputs = document.querySelectorAll(`${containerSelector} input[data-field], ${containerSelector} input.form-control`);
        allInputs.forEach(input => {
            if (input.type === 'hidden' || input.hasAttribute('data-readonly')) return;
            input.disabled = !userCanEdit;
            if (!userCanEdit) {
                input.style.backgroundColor = '#f5f5f5';
                input.style.cursor = 'not-allowed';
            } else {
                input.style.backgroundColor = '';
                input.style.cursor = '';
            }
        });
    };
    
    /**
     * Generate edit buttons HTML based on permission
     * @param {boolean} userCanEdit - Whether user can edit
     * @param {string} addFunction - Function name for add button
     * @param {string} deleteFunction - Function name for delete button
     * @param {string} rowId - Row ID for delete function
     * @param {string} addTitle - Title for add button (default: "Add row")
     * @param {string} deleteTitle - Title for delete button (default: "Delete row")
     * @returns {string} HTML string for buttons or empty string
     */
    window.getEditButtonsHtml = function(userCanEdit, addFunction, deleteFunction, rowId, addTitle = 'Add row', deleteTitle = 'Delete row') {
        if (!userCanEdit) return '';
        
        return `
            <div class="action-buttons">
                <button type="button" class="btn btn-sm btn-success" onclick="${addFunction}()" title="${addTitle}">
                    <i class="fas fa-plus"></i>
                </button>
                <button type="button" class="btn btn-sm btn-danger" onclick="${deleteFunction}('${rowId}')" title="${deleteTitle}">
                    <i class="fas fa-minus"></i>
                </button>
            </div>
        `;
    };
    
    /**
     * Get disabled attribute for form controls
     * @param {boolean} userCanEdit - Whether user can edit
     * @returns {string} 'disabled' or empty string
     */
    window.getDisabledAttr = function(userCanEdit) {
        return userCanEdit ? '' : 'disabled';
    };
    
    /**
     * Get module name from current context
     * @param {string} facetDir - Optional facet directory name
     * @returns {string|null} Module name or null
     */
    window.getModuleNameFromContext = function(facetDir) {
        return getModuleName(facetDir);
    };

    /**
     * Check if user can edit a specific object (has role permission AND is stakeholder)
     * This is a shared utility function for all facet view pages
     * @param {string} moduleName - Module name (e.g., "System", "Data Sets", "Glossary")
     * @param {number} objectId - Object ID
     * @returns {Promise<{canEdit: boolean, isAdmin: boolean, hasRolePermission: boolean, isStakeholder: boolean}>}
     */
    window.checkCanEditObject = async function(moduleName, objectId) {
        if (!moduleName || !objectId || objectId <= 0) {
            return { canEdit: false, isAdmin: false, hasRolePermission: false, isStakeholder: false };
        }

        try {
            // Check role-based permission first
            const permResp = await fetch(`/api/user/permissions/${encodeURIComponent(moduleName)}`, {
                method: 'GET',
                credentials: 'include'
            });

            if (!permResp.ok) {
                return { canEdit: false, isAdmin: false, hasRolePermission: false, isStakeholder: false };
            }

            const perms = await permResp.json();
            if (!perms.success) {
                return { canEdit: false, isAdmin: false, hasRolePermission: false, isStakeholder: false };
            }

            const isAdmin = perms.isAdmin === true;
            const hasRolePermission = perms.canEdit === true || isAdmin;

            // Admins can always edit
            if (isAdmin) {
                return { canEdit: true, isAdmin: true, hasRolePermission: true, isStakeholder: true };
            }

            // If user doesn't have role permission, they cannot edit
            if (!hasRolePermission) {
                return { canEdit: false, isAdmin: false, hasRolePermission: false, isStakeholder: false };
            }

            // If user has role permission, check stakeholder status
            try {
                const stakeholderResp = await fetch(
                    `/api/check-stakeholder/${encodeURIComponent(moduleName)}/${objectId}`,
                    {
                        method: 'GET',
                        credentials: 'include'
                    }
                );

                if (stakeholderResp.ok) {
                    const stakeholderData = await stakeholderResp.json();
                    const isStakeholder = stakeholderData.isStakeholder === true;
                    const canEdit = isStakeholder; // Can edit only if stakeholder

                    return {
                        canEdit,
                        isAdmin: false,
                        hasRolePermission: true,
                        isStakeholder
                    };
                } else {
                    console.warn(`[checkCanEditObject] Failed to check stakeholder status: ${stakeholderResp.status}`);
                    return { canEdit: false, isAdmin: false, hasRolePermission: true, isStakeholder: false }; // Fail securely
                }
            } catch (stakeholderError) {
                console.error('[checkCanEditObject] Error checking stakeholder status:', stakeholderError);
                return { canEdit: false, isAdmin: false, hasRolePermission: true, isStakeholder: false }; // Fail securely
            }
        } catch (error) {
            console.error('[checkCanEditObject] Error checking permissions:', error);
            return { canEdit: false, isAdmin: false, hasRolePermission: false, isStakeholder: false };
        }
    };

})();
