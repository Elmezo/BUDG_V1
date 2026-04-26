/**
 * Unified Edit Dropdown Component
 * Creates a consistent edit button with dropdown menu across all facet view pages
 */

window.EditDropdown = (function() {
    'use strict';

    // Facet configuration for dropdown menu items
    const FACET_CONFIG = {
        'system': {
            displayName: 'System',
            createOptions: [
                { label: 'New System', action: 'create-system', url: '/system.html' },
                { label: 'New Interface', action: 'create-interface', url: '/system-interface.html' },
                { label: 'New Data Set', action: 'create-dataset', url: '/dataset.html' }
            ]
        },
        'dataset': {
            displayName: 'Data Set',
            createOptions: [
                { label: 'New Data Set', action: 'create-dataset', url: '/dataset.html' },
                { label: 'Clone Data Set', action: 'clone-dataset', url: null }
            ]
        },
        'business-area': {
            displayName: 'Business Area',
            createOptions: [
                { label: 'New Business Area', action: 'create-business-area', url: '/business-area.html' }
            ]
        },
        'capability': {
            displayName: 'Capability',
            createOptions: [
                { label: 'New Capability', action: 'create-capability', url: '/capability.html' }
            ]
        },
        'client': {
            displayName: 'Client',
            createOptions: [
                { label: 'New Client', action: 'create-client', url: '/client.html' }
            ]
        },
        'committee': {
            displayName: 'Committee',
            createOptions: [
                { label: 'New Committee', action: 'create-committee', url: '/committee.html' }
            ]
        },
        'geography': {
            displayName: 'Geography',
            createOptions: [
                { label: 'New Geography', action: 'create-geography', url: '/geography.html' }
            ]
        },
        'glossary': {
            displayName: 'Glossary',
            createOptions: [
                { label: 'New Glossary', action: 'create-glossary', url: '/glossary.html' }
            ]
        },
        'legal-entity': {
            displayName: 'Legal Entity',
            createOptions: [
                { label: 'New Legal Entity', action: 'create-legal-entity', url: '/legal-entity.html' }
            ]
        },
        'org-unit': {
            displayName: 'Org Unit',
            createOptions: [
                { label: 'New Org Unit', action: 'create-org-unit', url: '/org-unit.html' }
            ]
        },
        'people': {
            displayName: 'People',
            createOptions: [
                { label: 'New Person', action: 'create-person', url: '/people.html' }
            ]
        },
        'policy': {
            displayName: 'Policy',
            createOptions: [
                { label: 'New Policy', action: 'create-policy', url: '/policy.html' }
            ]
        },
        'process': {
            displayName: 'Process',
            createOptions: [
                { label: 'New Process', action: 'create-process', url: '/process.html' }
            ]
        },
        'product': {
            displayName: 'Product',
            createOptions: [
                { label: 'New Product', action: 'create-product', url: '/product.html' }
            ]
        },
        'project': {
            displayName: 'Project',
            createOptions: [
                { label: 'New Project', action: 'create-project', url: '/project.html' }
            ]
        },
        'regulation': {
            displayName: 'Regulation',
            createOptions: [
                { label: 'New Regulation', action: 'create-regulation', url: '/regulation.html' }
            ]
        },
        'regulator': {
            displayName: 'Regulator',
            createOptions: [
                { label: 'New Regulator', action: 'create-regulator', url: '/regulator.html' }
            ]
        },
        'regulatory-theme': {
            displayName: 'Regulatory Theme',
            createOptions: [
                { label: 'New Regulatory Theme', action: 'create-regulatory-theme', url: '/regulatory-theme.html' }
            ]
        },
        'system-interface': {
            displayName: 'System Interface',
            createOptions: [
                { label: 'New System Interface', action: 'create-system-interface', url: '/system-interface.html' }
            ]
        },
        'change-request': {
            displayName: 'Change Request',
            createOptions: [
                { label: 'New Change Request', action: 'create-change-request', url: '/view/change-request/change-request.html' }
            ]
        }
    };

    /**
     * Render custom action sections (e.g. Roles -> Accept / Undo Accept)
     * @param {Array<{category?: string, label: string, icon?: string, action: Function}>} customActions
     * @returns {string}
     */
    function renderCustomActionsHTML(customActions = []) {
        if (!Array.isArray(customActions) || customActions.length === 0) return '';

        // Group by category (default "ACTIONS")
        const groups = new Map();
        customActions.forEach((item, idx) => {
            if (!item || typeof item.action !== 'function') return;
            const cat = (item.category || 'Actions').toString();
            if (!groups.has(cat)) groups.set(cat, []);
            groups.get(cat).push({ ...item, __idx: idx });
        });

        if (groups.size === 0) return '';

        let html = '';
        for (const [category, items] of groups.entries()) {
            const title = (category || 'Actions').toString().toUpperCase();
            const itemsHtml = items.map(a => {
                const icon = (a.icon || 'fa-bolt').toString();
                const label = (a.label || '').toString();
                return `
                    <button class="edit-dropdown-item" data-action="custom" data-custom-index="${a.__idx}">
                        <i class="fas ${icon}"></i> ${label}
                    </button>
                `;
            }).join('');

            html += `
                <div class="edit-dropdown-section">
                    <div class="edit-dropdown-section-title">${title}</div>
                    ${itemsHtml}
                </div>
            `;
        }

        return html;
    }

    /**
     * Map facetType (kebab-case e.g. 'business-area') to i18n key (e.g. 'facet.businessArea').
     * en.json uses camelCase under "facet", so we convert to match.
     */
    function getFacetI18nKey(facetType) {
        const camel = String(facetType).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
        return 'facet.' + camel;
    }

    /**
     * Generate HTML for edit dropdown
     * @param {string} facetType - The type of facet (e.g., 'system', 'dataset')
     * @param {number} objectId - The ID of the current object
     * @param {Object} options - Additional options
     * @returns {string} HTML string for the dropdown
     */
    function generateDropdownHTML(facetType, objectId, options = {}) {
        const config = FACET_CONFIG[facetType];
        if (!config) {
            console.warn(`No configuration found for facet type: ${facetType}`);
            return '';
        }

        const editUrl = options.editUrl || `/view/${facetType}/${facetType}-edit.html?id=${objectId}`;

        const customActionsHTML = renderCustomActionsHTML(options.customActions || []);
        
        // Helper function to get translation
        const getTranslation = (key, fallback) => {
            if (window.I18n && typeof window.I18n.t === 'function') {
                const translation = window.I18n.t(key);
                // If translation exists and is different from key, return it
                if (translation && translation !== key && typeof translation === 'string') {
                    return translation;
                }
            }
            return fallback;
        };

        // Special handling for change-request facet type
        if (facetType === 'change-request') {
            const editText = getTranslation('button.edit', 'Edit');
            const editObjectText = getTranslation('dropdown.editObject', 'EDIT OBJECT');
            const editItemText = getTranslation('dropdown.editItem', 'Edit Item');
            const otherActionsText = getTranslation('dropdown.otherActions', 'OTHER ACTIONS');
            const downloadPdfText = getTranslation('dropdown.downloadPdf', 'Download PDF Report');
            
            // Check if edit option should be hidden (e.g., for cancelled CRs)
            const hideEditOption = options.hideEditOption === true;
            
            // If edit option is hidden, only show other actions section
            if (hideEditOption) {
                return `
                    <div class="edit-dropdown-container">
                        <button type="button" class="btn btn-primary btn-sm edit-dropdown-trigger" id="editDropdownTrigger">
                            <i class="fas fa-pen"></i>
                            <span data-i18n="button.edit">${editText}</span>
                            <i class="fas fa-chevron-down edit-dropdown-arrow"></i>
                        </button>
                        <div class="edit-dropdown-menu" id="editDropdownMenu">
                            <div class="edit-dropdown-section">
                                <div class="edit-dropdown-section-title" data-i18n="dropdown.otherActions">${otherActionsText}</div>
                                <button class="edit-dropdown-item" data-action="download-pdf">
                                    <i class="fas fa-file-pdf"></i> <span data-i18n="dropdown.downloadPdf">${downloadPdfText}</span>
                                </button>
                            </div>
                        </div>
                    </div>
                `;
            }
            
            return `
                <div class="edit-dropdown-container">
                    <button type="button" class="btn btn-primary btn-sm edit-dropdown-trigger" id="editDropdownTrigger">
                        <i class="fas fa-pen"></i>
                        <span data-i18n="button.edit">${editText}</span>
                        <i class="fas fa-chevron-down edit-dropdown-arrow"></i>
                    </button>
                    <div class="edit-dropdown-menu" id="editDropdownMenu">
                        <div class="edit-dropdown-section">
                            <div class="edit-dropdown-section-title" data-i18n="dropdown.editObject">${editObjectText}</div>
                            <button class="edit-dropdown-item" data-action="edit" data-url="${editUrl}">
                                <i class="fas fa-edit"></i> <span data-i18n="dropdown.editItem">${editItemText}</span>
                            </button>
                        </div>
                        <div class="edit-dropdown-divider"></div>
                        <div class="edit-dropdown-section">
                            <div class="edit-dropdown-section-title" data-i18n="dropdown.otherActions">${otherActionsText}</div>
                            <button class="edit-dropdown-item" data-action="download-pdf">
                                <i class="fas fa-file-pdf"></i> <span data-i18n="dropdown.downloadPdf">${downloadPdfText}</span>
                            </button>
                        </div>
                    </div>
                </div>
            `;
        }
        
        // CREATE OBJECT section removed - Create functionality is now in the header Create menu
        // Users should use the "+ Create" button in the header navigation to create new items
        const createOptionsHTML = '';

        // Get translations for static text
        const editText = getTranslation('button.edit', 'Edit');
        const createObjectText = getTranslation('dropdown.createObject', 'CREATE OBJECT');
        const editObjectText = getTranslation('dropdown.editObject', 'EDIT OBJECT');
        const editItemText = getTranslation('dropdown.editItem', 'Edit Item');
        const deleteItemText = getTranslation('dropdown.deleteItem', 'Delete Item');
        const otherActionsText = getTranslation('dropdown.otherActions', 'OTHER ACTIONS');
        const downloadPdfText = getTranslation('dropdown.downloadPdf', 'Download PDF Report');
        const raiseChangeRequestText = getTranslation('dropdown.raiseChangeRequest', 'Raise Change Request');
        const facetI18nKey = getFacetI18nKey(facetType);
        const facetName = getTranslation(facetI18nKey, config.displayName);
        
        // Check if change requests are allowed for this facet type
        const restrictedFacets = ['regulatory-theme', 'geography', 'regulator', 'people', 'legal-entity', 'org-unit'];
        const canRaiseChangeRequest = !restrictedFacets.includes(facetType);
        
        // Check if edit option should be disabled (e.g., when Auto CR is active)
        const disableEditOption = options.disableEditOption === true;
        const disableEditReason = options.disableEditReason || '';
        console.log(`[EditDropdown] generateDropdownHTML for ${facetType}, disableEditOption:`, disableEditOption, 'reason:', disableEditReason);
        
        // Generate EDIT OBJECT section - show but disable if editing should be blocked
        const editButtonDisabled = disableEditOption ? 'disabled' : '';
        const editButtonClass = disableEditOption ? 'edit-dropdown-item edit-dropdown-item-disabled' : 'edit-dropdown-item';
        const editButtonTitle = disableEditOption ? (disableEditReason || 'Editing is disabled due to active Change Request') : '';
        const editButtonStyle = disableEditOption ? 'style="opacity: 0.5; cursor: not-allowed;"' : '';
        
        const editObjectSection = `
                    <div class="edit-dropdown-divider"></div>
                    <div class="edit-dropdown-section">
                        <div class="edit-dropdown-section-title" data-i18n="dropdown.editObject">${editObjectText}</div>
                        <button class="${editButtonClass}" data-action="edit" data-url="${editUrl}" ${editButtonDisabled} title="${editButtonTitle}" ${editButtonStyle}>
                            <i class="fas fa-edit"></i> <span data-i18n="dropdown.editItem">${editItemText}</span> <span data-i18n="${facetI18nKey}">${facetName}</span>
                            ${disableEditOption && disableEditReason ? `<span class="edit-disabled-note" style="display: block; font-size: 0.85em; color: #999; margin-top: 4px; font-style: italic;">${disableEditReason}</span>` : ''}
                        </button>
                        <button class="edit-dropdown-item edit-dropdown-item-danger" data-action="delete">
                            <i class="fas fa-trash"></i> <span data-i18n="dropdown.deleteItem">${deleteItemText}</span>
                        </button>
                    </div>`;

        // Only show CREATE OBJECT section if there are create options (for backward compatibility)
        // But typically, Create should be in the header menu, not in the edit dropdown
        const createSectionHTML = createOptionsHTML ? `
                    <div class="edit-dropdown-section">
                        <div class="edit-dropdown-section-title" data-i18n="dropdown.createObject">${createObjectText}</div>
                        ${createOptionsHTML}
                    </div>
                    <div class="edit-dropdown-divider"></div>
        ` : '';
        
        return `
            <div class="edit-dropdown-container">
                <button type="button" class="btn btn-primary btn-sm edit-dropdown-trigger" id="editDropdownTrigger">
                    <i class="fas fa-pen"></i>
                    <span data-i18n="button.edit">${editText}</span>
                    <i class="fas fa-chevron-down edit-dropdown-arrow"></i>
                </button>
                <div class="edit-dropdown-menu" id="editDropdownMenu">
                    ${createSectionHTML}
                    ${editObjectSection}
                    ${customActionsHTML ? `<div class="edit-dropdown-divider"></div>${customActionsHTML}` : ''}
                    <div class="edit-dropdown-divider"></div>
                    <div class="edit-dropdown-section">
                        <div class="edit-dropdown-section-title" data-i18n="dropdown.otherActions">${otherActionsText}</div>
                        <button class="edit-dropdown-item" data-action="download-pdf">
                            <i class="fas fa-file-pdf"></i> <span data-i18n="dropdown.downloadPdf">${downloadPdfText}</span>
                        </button>
                        ${canRaiseChangeRequest ? `<button class="edit-dropdown-item" data-action="raise-change-request">
                            <i class="fas fa-exclamation-triangle"></i> <span data-i18n="dropdown.raiseChangeRequest">${raiseChangeRequestText}</span>
                        </button>` : ''}
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Initialize edit dropdown functionality
     * @param {string} facetType - The type of facet
     * @param {number} objectId - The ID of the current object
     * @param {Object} options - Additional options
     */
    async function initialize(facetType, objectId, options = {}) {
        const container = options.container || '.form-actions';
        const targetElement = typeof container === 'string' ? document.querySelector(container) : container;
        
        if (!targetElement) {
            console.error('Edit dropdown container not found:', container);
            return;
        }

        // Remove any existing edit dropdown containers to prevent duplication
        const existingDropdowns = targetElement.querySelectorAll('.edit-dropdown-container');
        existingDropdowns.forEach(dropdown => dropdown.remove());

        // Replace existing edit button or add dropdown
        const existingEditBtn = targetElement.querySelector('#tabEditBtn');
        if (existingEditBtn) {
            existingEditBtn.remove();
        }
        
        // Fetch user permissions for this facet (only for Edit/Delete, not Create)
        // Create functionality is now in the header Create menu
        let permissions = { canEdit: false, canDelete: false, isAdmin: false };
        try {
            // Map facetType to module name for permissions API
            const moduleNameMap = {
                'policy': 'Policy',
                'system': 'System',
                'dataset': 'Data Sets',
                'glossary': 'Glossary',
                'process': 'Process',
                'project': 'Project',
                'product': 'Product',
                'regulation': 'Regulation',
                'regulator': 'Regulator',
                'interface': 'Interface',
                'system-interface': 'Interface',
                'client': 'Client',
                'committee': 'Committee',
                'geography': 'Geography',
                'capability': 'Capability',
                'business-area': 'Business Areas',
                'org-unit': 'Org Units',
                'people': 'People',
                'legal-entity': 'Legal Entity',
                'change-request': 'Change Request',
                'regulatory-theme': 'Regulatory Theme'
            };
            
            const moduleName = moduleNameMap[facetType] || facetType.charAt(0).toUpperCase() + facetType.slice(1).replace(/-/g, ' ');
            console.log(`[EditDropdown] Checking permissions for facetType: ${facetType}, moduleName: ${moduleName}`);
            const permResp = await fetch(`/api/user/permissions/${encodeURIComponent(moduleName)}`, { 
                method: 'GET', 
                credentials: 'include' 
            });
            
            if (permResp.ok) {
                const permData = await permResp.json();
                console.log(`[EditDropdown] Permission API response for ${moduleName}:`, permData);
                if (permData.success) {
                    permissions = {
                        canEdit: permData.canEdit === true || permData.isAdmin === true,
                        canDelete: permData.canDelete === true, // Only Super Admins can delete (backend enforces this)
                        isAdmin: permData.isAdmin === true
                    };
                    console.log(`[EditDropdown] Final permissions for ${moduleName}:`, permissions);
                } else {
                    console.warn(`[EditDropdown] Permission API returned success=false for ${moduleName}:`, permData);
                }
            } else {
                console.warn(`[EditDropdown] Permission API returned status ${permResp.status} for ${moduleName}`);
            }
        } catch (e) {
            console.warn('[EditDropdown] Failed to fetch permissions, using defaults:', e);
        }
        
        // Store permissions for later use
        options.permissions = permissions;

        // Insert dropdown HTML
        const dropdownHTML = generateDropdownHTML(facetType, objectId, options);
        targetElement.insertAdjacentHTML('beforeend', dropdownHTML);
        
        // Apply permission-based visibility
        applyPermissionVisibility(permissions);

        // Apply translations if I18n is available - use setTimeout to ensure DOM is ready
        setTimeout(() => {
            if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
                window.I18n.applyTranslations();
            }
        }, 0);

        // Wire up event handlers
        setupEventHandlers(facetType, objectId, options);
    }
    
    /**
     * Apply permission-based visibility to dropdown items
     * Note: Create functionality is now in the header Create menu, not in the edit dropdown
     * @param {Object} permissions - User permissions object
     */
    function applyPermissionVisibility(permissions) {
        const menu = document.getElementById('editDropdownMenu');
        if (!menu) return;
        
        // Hide Edit Item if user cannot edit
        if (!permissions.canEdit) {
            const editItems = menu.querySelectorAll('[data-action="edit"]');
            editItems.forEach(item => {
                item.style.display = 'none';
            });
        }
        
        // Hide Delete Item if user cannot delete (only admin can delete)
        if (!permissions.canDelete) {
            const deleteItems = menu.querySelectorAll('[data-action="delete"]');
            deleteItems.forEach(item => {
                item.style.display = 'none';
            });
        }
        
        // Hide the entire dropdown if user has no actions available
        const visibleItems = menu.querySelectorAll('.edit-dropdown-item:not([style*="display: none"])');
        if (visibleItems.length === 0) {
            const dropdown = document.querySelector('.edit-dropdown-container');
            if (dropdown) {
                dropdown.style.display = 'none';
            }
        }
    }

    /**
     * Position the dropdown menu using fixed positioning relative to the trigger button.
     * This allows the menu to escape overflow: hidden on parent containers.
     * @param {HTMLElement} trigger - The trigger button element
     * @param {HTMLElement} menu - The dropdown menu element
     */
    function positionDropdownMenu(trigger, menu) {
        const rect = trigger.getBoundingClientRect();
        const isRTL = document.documentElement.getAttribute('dir') === 'rtl' ||
                       document.body.getAttribute('dir') === 'rtl' ||
                       getComputedStyle(document.documentElement).direction === 'rtl';
        const menuWidth = menu.offsetWidth || 250; // fallback width
        const gap = 4; // gap between trigger and menu

        // Position below the trigger button
        menu.style.top = (rect.bottom + gap) + 'px';

        if (isRTL) {
            // RTL: align menu's right edge with trigger's right edge
            menu.style.left = rect.left + 'px';
            menu.style.right = 'auto';
        } else {
            // LTR: align menu's right edge with trigger's right edge
            const rightEdge = rect.right;
            const leftPos = rightEdge - menuWidth;
            // Ensure menu doesn't go off-screen to the left
            menu.style.left = Math.max(8, leftPos) + 'px';
            menu.style.right = 'auto';
        }

        // Ensure menu doesn't go below the viewport
        requestAnimationFrame(() => {
            const menuRect = menu.getBoundingClientRect();
            if (menuRect.bottom > window.innerHeight) {
                // Position above the trigger instead
                menu.style.top = (rect.top - menuRect.height - gap) + 'px';
            }
            // Ensure menu doesn't go off-screen to the right
            if (menuRect.right > window.innerWidth) {
                menu.style.left = (window.innerWidth - menuRect.width - 8) + 'px';
            }
        });
    }

    /**
     * Setup event handlers for dropdown functionality
     */
    function setupEventHandlers(facetType, objectId, options = {}) {
        const trigger = document.getElementById('editDropdownTrigger');
        const menu = document.getElementById('editDropdownMenu');
        const customActions = Array.isArray(options.customActions) ? options.customActions : [];
        
        if (!trigger || !menu) {
            console.error('Edit dropdown elements not found');
            return;
        }

        let isOpen = false;
        // Store original parent so we can move menu back on close
        const originalParent = menu.parentElement;

        // Toggle dropdown
        function toggleDropdown(e) {
            e.preventDefault();
            e.stopPropagation();
            isOpen = !isOpen;
            if (isOpen) {
                // Portal: move menu to document.body to escape overflow: hidden
                document.body.appendChild(menu);
                positionDropdownMenu(trigger, menu);
                menu.classList.add('show');
            } else {
                menu.classList.remove('show');
                // Move menu back to original container
                if (originalParent) {
                    originalParent.appendChild(menu);
                }
            }
            trigger.setAttribute('aria-expanded', isOpen);
        }

        // Close dropdown
        function closeDropdown() {
            if (!isOpen) return;
            isOpen = false;
            menu.classList.remove('show');
            trigger.setAttribute('aria-expanded', false);
            // Move menu back to original container
            if (originalParent && menu.parentElement === document.body) {
                originalParent.appendChild(menu);
            }
        }

        // Reposition on scroll or resize while open
        function onScrollOrResize() {
            if (isOpen) {
                positionDropdownMenu(trigger, menu);
            }
        }
        window.addEventListener('scroll', onScrollOrResize, true); // capture phase for inner scrolls
        window.addEventListener('resize', onScrollOrResize);

        // Handle menu item clicks
        async function handleMenuClick(e) {
            const item = e.target.closest('.edit-dropdown-item');
            if (!item) return;

            e.preventDefault();
            closeDropdown();

            const action = item.getAttribute('data-action');
            const url = item.getAttribute('data-url');
            const customIndexRaw = item.getAttribute('data-custom-index');

            switch (action) {
                case 'edit':
                    // Check if edit button is disabled
                    if (item.disabled || item.classList.contains('edit-dropdown-item-disabled')) {
                        e.preventDefault();
                        e.stopPropagation();
                        const reason = item.getAttribute('title') || 'Editing is currently disabled';
                        alert(reason);
                        return;
                    }
                    if (url) {
                        // Check lock status before navigating (if ViewLockHelper is available)
                        if (window.ViewLockHelper && objectId && facetType) {
                            // Extract object ID from URL if not already available
                            const urlMatch = url.match(/[?&]id=(\d+)/);
                            const idToCheck = objectId || (urlMatch ? parseInt(urlMatch[1], 10) : null);
                            
                            if (idToCheck) {
                                const canEdit = await window.ViewLockHelper.interceptEditButton(
                                    facetType,
                                    idToCheck,
                                    function() {
                                        window.location.href = url;
                                    }
                                );
                                if (!canEdit) return; // Lock check failed, navigation prevented
                            } else {
                                // Fallback if ID not available
                                window.location.href = url;
                            }
                        } else {
                            // Fallback if ViewLockHelper not available
                            window.location.href = url;
                        }
                    }
                    break;

                case 'custom': {
                    const idx = customIndexRaw != null ? parseInt(customIndexRaw, 10) : NaN;
                    const custom = Number.isFinite(idx) ? customActions[idx] : null;
                    if (!custom || typeof custom.action !== 'function') {
                        console.warn('Custom action not found:', idx);
                        return;
                    }
                    Promise.resolve()
                        .then(() => custom.action())
                        .catch(err => {
                            console.error('Custom action failed:', err);
                            alert('Action failed. Please try again.');
                        });
                    break;
                }
                
                case 'delete':
                    if (window.DeleteHandler) {
                        window.DeleteHandler.handleDelete(facetType, objectId);
                    } else {
                        console.error('DeleteHandler not available');
                    }
                    break;
                
                case 'clone-dataset':
                    if (facetType === 'dataset') {
                        handleCloneDataset(objectId);
                    }
                    break;
                
                case 'download-pdf':
                    handleDownloadPDF(facetType, objectId);
                    break;
                
                case 'raise-change-request':
                    handleRaiseChangeRequest(facetType, objectId);
                    break;
                
                default:
                    if (url) {
                        window.location.href = url;
                    }
            }
        }

        // Event listeners
        trigger.addEventListener('click', toggleDropdown);
        menu.addEventListener('click', handleMenuClick);

        // Close on outside click
        document.addEventListener('click', function(e) {
            if (!trigger.contains(e.target) && !menu.contains(e.target)) {
                closeDropdown();
            }
        });

        // Close on Escape key
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape' && isOpen) {
                closeDropdown();
            }
        });
    }

    /**
     * Handle clone dataset action
     */
    function handleCloneDataset(datasetId) {
        // Show confirmation modal
        const modal = createCloneModal();
        document.body.appendChild(modal);
        
        // Show modal
        setTimeout(() => {
            modal.classList.add('show');
        }, 10);
        
        // Handle Continue button
        const continueBtn = modal.querySelector('.clone-continue-btn');
        continueBtn.addEventListener('click', async () => {
            continueBtn.disabled = true;
            continueBtn.textContent = 'Cloning...';
            
            try {
                const response = await fetch(`/api/dataset/${datasetId}/clone`, {
                    method: 'POST',
                    credentials: 'include',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
                
                const result = await response.json();
                
                if (result.success && result.id) {
                    // Close modal
                    modal.classList.remove('show');
                    setTimeout(() => {
                        modal.remove();
                    }, 300);
                    
                    // Redirect to edit page
                    window.location.href = `/view/dataset/dataset-edit.html?id=${result.id}`;
                } else {
                    alert('Failed to clone dataset: ' + (result.message || 'Unknown error'));
                    continueBtn.disabled = false;
                    continueBtn.textContent = 'Continue';
                }
            } catch (error) {
                console.error('Error cloning dataset:', error);
                alert('Error cloning dataset: ' + error.message);
                continueBtn.disabled = false;
                continueBtn.textContent = 'Continue';
            }
        });
        
        // Handle Cancel button
        const cancelBtn = modal.querySelector('.clone-cancel-btn');
        cancelBtn.addEventListener('click', () => {
            modal.classList.remove('show');
            setTimeout(() => {
                modal.remove();
            }, 300);
        });
        
        // Handle X button
        const closeBtn = modal.querySelector('.clone-close-btn');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => {
                modal.classList.remove('show');
                setTimeout(() => {
                    modal.remove();
                }, 300);
            });
        }
    }

    /**
     * Create clone confirmation modal
     */
    function createCloneModal() {
        const modal = document.createElement('div');
        modal.className = 'clone-modal';
        modal.innerHTML = `
            <div class="clone-modal-overlay"></div>
            <div class="clone-modal-content">
                <div class="clone-modal-header">
                    <h3>Clone Object</h3>
                    <button type="button" class="clone-close-btn" aria-label="Close">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="clone-modal-body">
                    <p>BUDG will create and save a copy of this data set when you click Continue. All classifications and attributes will be copied over to the new data set. You can fill in the other fields as necessary, and then click Save.</p>
                </div>
                <div class="clone-modal-footer">
                    <button type="button" class="btn btn-secondary clone-cancel-btn">Cancel</button>
                    <button type="button" class="btn btn-primary clone-continue-btn">Continue</button>
                </div>
            </div>
        `;
        return modal;
    }

    /**
     * Handle download PDF action
     */
    function handleDownloadPDF(facetType, objectId) {
        // Placeholder for PDF download functionality
        alert('PDF download functionality will be implemented in a future update.');
    }

    /**
     * Handle raise change request action
     */
    async function handleRaiseChangeRequest(facetType, objectId) {
        try {
            // Get facet name from API or use display name
            let facetName = FACET_CONFIG[facetType]?.displayName || facetType;
            
            // Try to get the actual object name
            try {
                const apiService = window.BUDG_API_SERVICE;
                if (apiService) {
                    // Map facet types to API methods
                    const apiMethods = {
                        'system': 'getSystemById',
                        'dataset': 'getDatasetById',
                        'business-area': 'getBusinessAreaById',
                        'capability': 'getCapabilityById',
                        'client': 'getClientById',
                        'committee': 'getCommitteeById',
                        'geography': 'getGeographyById',
                        'glossary': 'getGlossaryById',
                        'legal-entity': 'getLegalEntityById',
                        'org-unit': 'getOrgUnitById',
                        'people': 'getPersonById',
                        'policy': 'getPolicyById',
                        'process': 'getProcessById',
                        'product': 'getProductById',
                        'project': 'getProjectById',
                        'regulation': 'getRegulationById',
                        'regulator': 'getRegulatorById',
                        'regulatory-theme': 'getRegulatoryThemeById',
                        'system-interface': 'getSystemInterfaceById',
                        'change-request': 'getChangeRequestById'
                    };
                    
                    const methodName = apiMethods[facetType];
                    if (methodName && typeof apiService[methodName] === 'function') {
                        const data = await apiService[methodName](objectId);
                        // Extract name from response (handle different response formats)
                        const actualData = data?.data || data;
                        facetName = actualData?.name || 
                                   actualData?.primaryName || 
                                   actualData?.primaryname ||
                                   actualData?.PrimaryName ||
                                   facetName;
                    }
                }
            } catch (e) {
                console.warn('Could not fetch facet name, using default:', e);
            }
            
            // Navigate to change request page with facet information
            const params = new URLSearchParams({
                facetType: facetType,
                facetId: objectId,
                facetName: encodeURIComponent(facetName)
            });
            
            window.location.href = `/view/change-request/change-request.html?${params.toString()}`;
        } catch (error) {
            console.error('Error raising change request:', error);
            alert('Error opening change request form: ' + error.message);
        }
    }

    /**
     * Hide edit controls for unauthorized users
     */
    function hideEditControls() {
        const dropdown = document.querySelector('.edit-dropdown-container');
        if (dropdown) {
            dropdown.style.display = 'none';
        }
    }

    /**
     * Show edit controls for authorized users
     */
    function showEditControls() {
        const dropdown = document.querySelector('.edit-dropdown-container');
        if (dropdown) {
            dropdown.style.display = '';
        }
    }

    /**
     * Enable the edit button for stakeholder-only mode (when Auto CR is active but user is on stakeholder tab).
     * This temporarily overrides the disabled state to allow editing stakeholders only.
     * @param {string} editUrl - The base edit URL for this facet
     */
    function enableStakeholderOnlyEdit(editUrl) {
        const editBtn = document.querySelector('[data-action="edit"]');
        if (!editBtn) return;
        
        // Store original state for restoration
        if (!editBtn.dataset._originalDisabled) {
            editBtn.dataset._originalDisabled = editBtn.disabled ? 'true' : 'false';
            editBtn.dataset._originalUrl = editBtn.getAttribute('data-url') || '';
            editBtn.dataset._originalClass = editBtn.className;
            editBtn.dataset._originalTitle = editBtn.getAttribute('title') || '';
            editBtn.dataset._originalStyle = editBtn.getAttribute('style') || '';
        }
        
        // Enable the button and point to stakeholder-only edit
        editBtn.disabled = false;
        editBtn.className = 'edit-dropdown-item';
        editBtn.setAttribute('data-url', editUrl + (editUrl.includes('?') ? '&' : '?') + 'tab=stakeholders&stakeholderOnly=true');
        editBtn.setAttribute('title', 'Edit Stakeholders');
        editBtn.removeAttribute('style');
        
        // Remove the disabled note if present
        const note = editBtn.querySelector('.edit-disabled-note');
        if (note) note.style.display = 'none';
        
        console.log('[EditDropdown] Stakeholder-only edit mode ENABLED');
    }
    
    /**
     * Restore the edit button to its original disabled state (when leaving stakeholder tab).
     */
    function disableStakeholderOnlyEdit() {
        const editBtn = document.querySelector('[data-action="edit"]');
        if (!editBtn || !editBtn.dataset._originalDisabled) return;
        
        // Restore original state
        const wasDisabled = editBtn.dataset._originalDisabled === 'true';
        editBtn.disabled = wasDisabled;
        editBtn.className = editBtn.dataset._originalClass;
        editBtn.setAttribute('data-url', editBtn.dataset._originalUrl);
        editBtn.setAttribute('title', editBtn.dataset._originalTitle);
        if (editBtn.dataset._originalStyle) {
            editBtn.setAttribute('style', editBtn.dataset._originalStyle);
        } else {
            editBtn.removeAttribute('style');
        }
        
        // Show the disabled note again if present
        const note = editBtn.querySelector('.edit-disabled-note');
        if (note) note.style.display = '';
        
        console.log('[EditDropdown] Stakeholder-only edit mode DISABLED (restored original state)');
    }

    // Public API
    return {
        initialize: initialize,
        generateDropdownHTML: generateDropdownHTML,
        hideEditControls: hideEditControls,
        showEditControls: showEditControls,
        applyPermissionVisibility: applyPermissionVisibility,
        enableStakeholderOnlyEdit: enableStakeholderOnlyEdit,
        disableStakeholderOnlyEdit: disableStakeholderOnlyEdit,
        FACET_CONFIG: FACET_CONFIG
    };
})();
