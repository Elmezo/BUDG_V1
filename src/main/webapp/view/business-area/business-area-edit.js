// Business Area Edit Page JavaScript

// Global variables for parent selection
let allBusinessAreas = [];
let filteredBusinessAreas = [];
let currentBusinessAreaId = null;
let currentBusinessAreaData = null; // Store loaded business area data
let segmentField = null; // Segment field component reference
let hasUserInteracted = false;
let originalBusinessAreaSegmentId = null; // segment loaded from server; used to detect segment change

function normalizeBusinessAreaSegmentId(value) {
    if (value == null || value === '') return null;
    const n = parseInt(value, 10);
    return Number.isInteger(n) ? n : null;
}

// Track real user interaction so initial programmatic population
// does not trigger false unsaved-change prompts.
document.addEventListener('input', (e) => {
    if (e.isTrusted) hasUserInteracted = true;
}, true);
document.addEventListener('change', (e) => {
    if (e.isTrusted) hasUserInteracted = true;
}, true);
document.addEventListener('keydown', (e) => {
    if (e.isTrusted) hasUserInteracted = true;
}, true);

// Helper function to escape HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Build direct lineage tree (current + ancestors + descendants + siblings)
function buildDirectLineageTree(businessAreas, currentBusinessAreaId) {
    const byId = new Map();
    businessAreas.forEach(ba => byId.set(parseInt(ba.id), ba));

    const current = byId.get(parseInt(currentBusinessAreaId));
    if (!current) return [];

    const ancestors = new Set();
    const descendants = new Set();
    const visited = new Set(); // Cycle protection

    // Add current business area
    descendants.add(parseInt(currentBusinessAreaId));

    // Find all ancestors with cycle protection
    let parent = current;
    let depth = 0;
    const maxDepth = 10; // Prevent infinite loops
    
    while (parent && parent.parentId && depth < maxDepth) {
        const parentId = parseInt(parent.parentId);
        
        // Skip self-reference and cycle detection
        if (parentId === parseInt(currentBusinessAreaId) || visited.has(parentId)) {
            break;
        }
        
        visited.add(parentId);
        if (byId.has(parentId)) {
            parent = byId.get(parentId);
            ancestors.add(parentId);
            depth++;
        } else {
            break;
        }
    }

    // Find all descendants (including current business area's children and their descendants) with cycle protection
    function findDescendants(businessAreaId, depth = 0) {
        if (depth > maxDepth) return; // Prevent infinite recursion
        
        businessAreas.forEach(ba => {
            const childId = parseInt(ba.id);
            const parentId = parseInt(ba.parentId);
            
            // Skip self-reference and cycle detection
            if (parentId === businessAreaId && childId !== parseInt(currentBusinessAreaId) && !visited.has(childId)) {
                visited.add(childId);
                descendants.add(childId);
                findDescendants(childId, depth + 1); // Recursively find all descendants
            }
        });
    }
    findDescendants(parseInt(currentBusinessAreaId));

    // Find siblings (other children of the same parent) and their descendants
    if (current.parentId) {
        const parentId = parseInt(current.parentId);
        const siblings = businessAreas.filter(ba => {
            const baParentId = parseInt(ba.parentId);
            const baId = parseInt(ba.id);
            return baParentId === parentId && baId !== parseInt(currentBusinessAreaId);
        });

        // Add siblings and their descendants
        siblings.forEach(sibling => {
            const siblingId = parseInt(sibling.id);
            if (!visited.has(siblingId)) {
                visited.add(siblingId);
                descendants.add(siblingId);
                findDescendants(siblingId, 1); // Add all descendants of siblings (nephews/nieces and their descendants)
            }
        });
    }

    // Include the current business area, all its ancestors, and all its descendants (including siblings and their descendants)
    const includedIds = new Set([parseInt(currentBusinessAreaId), ...ancestors, ...descendants]);

    // Filter business areas to include only the complete family tree
    return businessAreas.filter(ba => {
        const id = parseInt(ba.id);
        return includedIds.has(id);
    });
}

// Build hierarchy tree structure
function buildHierarchyTree(businessAreas, rootId) {
    const byId = new Map();
    const byParent = new Map();
    
    businessAreas.forEach(ba => {
        byId.set(parseInt(ba.id), ba);
        const parentId = parseInt(ba.parentId) || 0;
        if (!byParent.has(parentId)) byParent.set(parentId, []);
        byParent.get(parentId).push(ba);
    });

    const rows = [];
    const parentMap = new Map();

    function buildRows(parentId, depth = 0) {
        const children = byParent.get(parentId) || [];
        children.forEach(child => {
            const childId = parseInt(child.id);
            const childChildren = byParent.get(childId) || [];
            const hasChildren = childChildren.length > 0;
            const childCount = childChildren.length;
            
            rows.push({
                node: child,
                depth,
                childCount,
                hasChildren
            });
            
            parentMap.set(childId, parentId);
            
            if (hasChildren) {
                buildRows(childId, depth + 1);
            }
        });
    }

    buildRows(rootId);
    
    return { rows, parentMap: byParent };
}

// Render business area hierarchy table following Regulatory Theme pattern
function renderBusinessAreaTable(hierarchyRows, currentId) {
    const Mask = window.HierarchyMask;
    const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
        const isMaskedNode = Mask ? Mask.isMasked(node) : false;
        const fallbackName = node.primaryName ?? node.PrimaryName ?? node.Name ?? node.name ?? 'Unnamed Business Area';
        const fallbackDesc = node.description ?? node.Description ?? '';
        const name = isMaskedNode ? Mask.PLACEHOLDER : fallbackName;
        const desc = isMaskedNode ? Mask.PLACEHOLDER : fallbackDesc;
        const isCurrent = String(node.id ?? node.ID) === String(currentId);
        const id = node.id ?? node.ID;
        const parentId = node.parentId ?? node.Parent_ID ?? node.parent_id ?? '';

        const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
        const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
        const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
        const linkClass = isCurrent ? 'ba-link current-ba-link' : 'ba-link';
        const link = isMaskedNode
            ? `<span class="${linkClass} masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(name)}</span>`
            : `<a class="${linkClass}" href="/view/business-area/business-area.html?id=${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
        const rowClasses = `${isCurrent ? 'current-row' : ''}${isMaskedNode ? ' masked-row' : ''}`.trim();

        return `<tr class="${rowClasses}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}"${isMaskedNode ? ' data-masked="true"' : ''}>
            <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-building item-icon"></i><span class="ba-name">${link}</span>${countBadge}</div></td>
            <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
        </tr>`;
    }).join('');

    return rowsHtml;
}

// Initialize business area hierarchy interactions following Regulatory Theme pattern
function initBusinessAreaInteractions(containerEl, hierarchyRows) {
    containerEl.addEventListener('click', function(e) {
        if (e.target.closest('.tree-expander')) {
            e.preventDefault();
            e.stopPropagation();
            
            const button = e.target.closest('.tree-expander');
            const row = button.closest('tr');
            const parentId = parseInt(row.dataset.id);
            const currentDepth = parseInt(row.dataset.depth);
            const icon = button.querySelector('i');
            
            // Toggle icon
            if (icon.classList.contains('fa-caret-down')) {
                icon.classList.remove('fa-caret-down');
                icon.classList.add('fa-caret-right');
            } else {
                icon.classList.remove('fa-caret-right');
                icon.classList.add('fa-caret-down');
            }
            
            // Toggle children visibility
            const tbody = row.parentNode;
            const rows = Array.from(tbody.querySelectorAll('tr'));
            const currentIndex = rows.indexOf(row);
            
            // Find all direct children
            for (let i = currentIndex + 1; i < rows.length; i++) {
                const childRow = rows[i];
                const childDepth = parseInt(childRow.dataset.depth);
                
                if (childDepth <= currentDepth) {
                    break; // We've reached a sibling or parent level
                }
                
                if (childDepth === currentDepth + 1) {
                    // This is a direct child
                    if (icon.classList.contains('fa-caret-right')) {
                        childRow.style.display = 'none';
                    } else {
                        childRow.style.display = '';
                    }
                } else if (childDepth > currentDepth + 1) {
                    // This is a grandchild or deeper - hide/show based on parent state
                    if (icon.classList.contains('fa-caret-right')) {
                        childRow.style.display = 'none';
                    }
                }
            }
        }
    });
}

document.addEventListener('DOMContentLoaded', async function() {
    console.log('Initializing business area edit page...');
    console.log('Current URL:', window.location.href);
    console.log('Current pathname:', window.location.pathname);
    console.log('Current search:', window.location.search);
    
    const id = parseId();
    console.log('Parsed ID:', id);
    
    if (!id) {
        console.error('No business area ID found in URL');
        const container = document.querySelector('.main-content');
        if (container) {
            container.innerHTML = `
                <div class="content-card">
                    <div class="content-body">
                        <div class="error" style="padding: 2rem; text-align: center;">
                            <h3>Error: No Business Area ID</h3>
                            <p>No business area ID found in the URL. Please check the URL and try again.</p>
                            <button onclick="window.history.back()" class="btn btn-secondary" style="margin-top: 1rem;">
                                Go Back
                            </button>
                        </div>
                    </div>
                </div>
            `;
        }
        return;
    }

    // Initialize lock manager
    const lockManager = new window.LockManager('business-area', id);
    window.currentLockManager = lockManager;
    
    // Setup auto-release on page unload
    lockManager.setupBeforeUnload();
    
    // Check lock status
    const lockStatus = await lockManager.checkLockStatus();
    const status = lockStatus?.status || 'no_lock';
    
    // Handle lock conflicts
    if (status === 'locked_by_other') {
        const lockedBy = lockStatus.lockedByName || 'another user';
        alert(window.I18n ? window.I18n.t('lock.objectLockedBy', { user: lockedBy }) : `This business area is currently locked by ${lockedBy}. Please try again later.`);
        window.location.href = `/view/business-area/business-area.html?id=${id}`;
        return;
    } else if (status === 'permanently_locked') {
        const isSuperAdmin = await lockManager.checkIsSuperAdmin();
        if (!isSuperAdmin) {
            const lockedBy = lockStatus.lockedByName || 'an administrator';
            alert(`This business area has a permanent lock by ${lockedBy}. Only administrators can edit it.`);
            window.location.href = `/view/business-area/business-area.html?id=${id}`;
            return;
        }
    }
    
    // Acquire lock
    const lockResult = await lockManager.acquireLock(false);
    if (!lockResult || !lockResult.success) {
        // Check if we have details about who locked it
        if (lockResult && lockResult.lockedBy) {
            const lockedBy = lockResult.lockedBy;
            alert(window.I18n ? window.I18n.t('lock.objectLockedTryLater', { user: lockedBy }) : `The object is currently locked by ${lockedBy}. Try again later.`);
        } else {
            alert(window.I18n ? window.I18n.t('lock.failedToAcquire') : 'Failed to acquire lock. Please try again.');
        }
        window.location.href = `/view/business-area/business-area.html?id=${id}`;
        return;
    }
    
    // Update global lock count if available
    if (window.globalLockUI) {
        await window.globalLockUI.updateLockCount();
    }
    
    console.log('Initializing business area edit page for ID:', id);
    
    // Set global currentBusinessAreaId
    currentBusinessAreaId = id;
    
    // Initialize the page
    initializePage().catch(error => {
        console.error('Failed to initialize page:', error);
        const container = document.querySelector('.main-content');
        if (container) {
            container.innerHTML = `
                <div class="content-card">
                    <div class="content-body">
                        <div class="error" style="padding: 2rem; text-align: center;">
                            <h3>Error Loading Business Area</h3>
                            <p>Failed to load business area data. Please try again.</p>
                            <button onclick="window.location.reload()" class="btn btn-secondary" style="margin-top: 1rem;">
                                Reload Page
                            </button>
                        </div>
                    </div>
                </div>
            `;
        }
    });
    
    // Initialize tab switching and save functionality
    try {
        initializeTabSwitching();
    } catch (error) {
        console.error('Failed to initialize tab switching:', error);
    }
    
    // Initialize form validation
    try {
        initializeFormValidation();
    } catch (error) {
        console.error('Failed to initialize form validation:', error);
    }
    
    // Check for tab parameter in URL and switch to that tab
    const urlParams = new URLSearchParams(window.location.search);
    const tabParam = urlParams.get('tab');
    
    if (tabParam) {
        // Switch to the specified tab after a short delay to ensure everything is loaded
        setTimeout(() => {
            console.log('Switching to tab in edit page:', tabParam);
            try {
                switchTab(tabParam);
            } catch (error) {
                console.error('Failed to switch tab:', error);
            }
        }, 300);
    }
    
});

function parseId() {
    
    // First try URL params (for separate edit pages)
    const urlParams = new URLSearchParams(window.location.search);
    const id = parseInt(urlParams.get('id'), 10);
    if (!Number.isNaN(id)) return id;
    
    // Fallback to path-based ID (for main view pages)
    const parts = window.location.pathname.split('/').filter(Boolean);
    const idx = parts.indexOf('business-area-edit.html');
    if (idx === -1 || parts.length < idx + 2) return null;
    const pathId = parseInt(parts[idx + 1], 10);
    return Number.isNaN(pathId) ? null : pathId;
}

function populateSelectSimple(selectId, list, getLabel) {
    const el = document.getElementById(selectId);
    if (!el) {
        console.warn(`Select element ${selectId} not found`);
        return;
    }
    
    // Safely unwrap list if it's a wrapped response (e.g. {data: [...]})
    let items = list;
    if (!Array.isArray(items) && items && Array.isArray(items.data)) {
        items = items.data;
    }
    if (!Array.isArray(items)) {
        console.warn(`populateSelectSimple(${selectId}): list is not an array, got:`, typeof items);
        items = [];
    }
    
    el.innerHTML = '';
    items.forEach((item) => {
        const op = document.createElement('option');
        op.value = String(item.ID || item.id);
        op.textContent = getLabel ? getLabel(item) : (item.PrimaryName || item.primaryName || item.primaryname || item.name || item.Name || '');
        el.appendChild(op);
    });
    
    console.log(`Populated select ${selectId} with ${items.length} options`);
}

async function loadBusinessAreaEditLookups() {
    console.log('=== LOADING BUSINESS AREA EDIT LOOKUPS ===');
    console.log('Loading business area edit lookups...');
    
    // Wait for API service to be available
    if (!window.BUDG_API_SERVICE) {
        await new Promise(resolve => {
            const checkApi = () => {
                if (window.BUDG_API_SERVICE) {
                    resolve();
                } else {
                    setTimeout(checkApi, 100);
                }
            };
            checkApi();
        });
    }
    
    try {
        // Load lookups with individual error handling
        const [statuses, lifecycle, viewing] = await Promise.allSettled([
            window.BUDG_API_SERVICE.getStatusList(),
            window.BUDG_API_SERVICE.getBusinessAreaLifecycle(),
            window.BUDG_API_SERVICE.getViewingList()
        ]);
        
        // Populate dropdowns with safe access
        if (statuses.status === 'fulfilled') {
            populateSelectSimple('budgStatus', (Array.isArray(statuses.value?.data) ? statuses.value.data : statuses.value), x => x.name || x.Name || x.primaryName || x.PrimaryName);
        }
        if (lifecycle.status === 'fulfilled') {
            console.log('Business area lifecycle data loaded:', lifecycle.value);
            console.log('Lifecycle data fields:', lifecycle.value?.[0] ? {
                PrimaryName: lifecycle.value[0].PrimaryName,
                primaryName: lifecycle.value[0].primaryName,
                name: lifecycle.value[0].name,
                Name: lifecycle.value[0].Name
            } : 'No data');
            const lifecycleData = Array.isArray(lifecycle.value?.data) ? lifecycle.value.data : lifecycle.value;
            console.log('Lifecycle data for dropdown:', lifecycleData);
            populateSelectSimple('lifecycle', lifecycleData, x => x.PrimaryName || x.primaryname || x.name || x.Name);
        } else {
            console.log('Business area lifecycle loading failed:', lifecycle);
        }
        if (viewing.status === 'fulfilled') {
            const viewingData = Array.isArray(viewing.value?.data) ? viewing.value.data : viewing.value;
            populateSelectSimple('budgViewing', viewingData, x => x.name || x.Name || x.primaryName || x.PrimaryName);
        }
        
        console.log('Business area edit lookups loaded successfully');
    } catch (error) {
        console.error('Error loading business area edit lookups:', error);
    }
}

async function loadBusinessArea(id) {
    console.log('=== LOADING BUSINESS AREA ===');
    console.log('Loading business area with ID:', id);
    
    // Wait for API service to be available
    if (!window.BUDG_API_SERVICE) {
        await new Promise(resolve => {
            const checkApi = () => {
                if (window.BUDG_API_SERVICE) {
                    resolve();
                } else {
                    setTimeout(checkApi, 100);
                }
            };
            checkApi();
        });
    }
    
    try {
        const response = await window.BUDG_API_SERVICE.getBusinessAreaById(id);
        console.log('=== LOADED BUSINESS AREA DATA ===');
        console.log('API response:', response);
        console.log('Response type:', typeof response);
        console.log('Response keys:', Object.keys(response || {}));
        
        // Handle both direct data and wrapped response
        const businessArea = response?.data || response;
        console.log('Business area data after unwrapping:', businessArea);
        console.log('Business area data keys:', Object.keys(businessArea || {}));
        
        if (businessArea) {
            console.log('Calling populateForm...');
            // Store the business area data globally for later use (e.g., setting segment after initialization)
            currentBusinessAreaData = businessArea;
            populateForm(businessArea);
            updateTitle(businessArea);
            
            // Load parent business area if exists
            const parentId = businessArea.parentId || businessArea.Parent_ID;
            if (parentId) {
                console.log('Loading parent business area with ID:', parentId);
                await loadParentBusinessArea(parentId);
            } else {
                console.log('No parent ID found in business area data');
            }
        } else {
            console.error('No business area data found in response');
        }
    } catch (error) {
        console.error('Error loading business area:', error);
    }
}

function updateTitle(businessArea) {
    const titleElement = document.getElementById('businessAreaTitle');
    const name = businessArea.primaryName || businessArea.PrimaryName || 'Unnamed Business Area';
    titleElement.textContent = name;
}

function populateForm(businessArea) {
    console.log('=== POPULATING FORM ===');
    console.log('Business area data received:', businessArea);

    // Temporarily disable change tracking while populating
    const wasTrackingChanges = hasFormChanges;

    // Set segment value if available (check for null/undefined, not falsy, since 0 could be valid)
    const segmentId = businessArea.segmentId ?? businessArea.segment_id ?? businessArea.Segment_ID;
    console.log('🔍 Business Area segment data:', {
        resolvedSegmentId: segmentId,
        segmentName: businessArea.segmentName
    });
    
    // Default to Enterprise (1) when segment is unassigned (-1) or missing
    const resolvedSegmentId = (segmentId == null || segmentId === -1) ? 1 : parseInt(segmentId, 10);
    
    if (segmentField) {
        try {
            segmentField.setValue(resolvedSegmentId);
            console.log('✅ Segment value set to:', resolvedSegmentId);
        } catch (error) {
            console.error('❌ Error setting segment value:', error);
        }
        if (typeof segmentField.getValue === 'function') {
            originalBusinessAreaSegmentId = normalizeBusinessAreaSegmentId(segmentField.getValue());
        } else {
            originalBusinessAreaSegmentId = normalizeBusinessAreaSegmentId(segmentId);
        }
    } else {
        // Store segment data for later use after field initialization
        window._pendingSegmentValue = resolvedSegmentId;
        originalBusinessAreaSegmentId = normalizeBusinessAreaSegmentId(resolvedSegmentId);
        console.log('🔍 Segment field not ready, stored pending value:', window._pendingSegmentValue);
    }

    const setVal = (i, v) => {
        const el = document.getElementById(i);
        if (el) {
            const oldValue = el.value;
            el.value = v ?? '';
            console.log(`Set ${i} from "${oldValue}" to:`, v);
        } else {
            console.warn(`Element ${i} not found`);
        }
    };
    
    const setSel = (i, v) => { 
        const el = document.getElementById(i); 
        console.log(`Setting select ${i} with value:`, v, 'Element found:', !!el);
        if (el && v != null) {
            const oldValue = el.value;
            const value = String(v);
            el.value = value;
            console.log(`Set select ${i} from "${oldValue}" to:`, value);
            console.log(`Select ${i} options:`, Array.from(el.options).map(opt => ({
                value: opt.value,
                text: opt.textContent,
                selected: opt.selected
            })));
            if (el.value !== value) {
                console.warn(`Failed to set select ${i} to ${value}. Current value: ${el.value}`);
            }
        } else if (el) {
            console.log(`Select ${i} not set (value is null/undefined)`);
        } else {
            console.warn(`Select element ${i} not found`);
        }
    };
    
    // Populate form fields based on schema.sql business_area table structure
    setVal('businessAreaName', businessArea.PrimaryName || businessArea.primaryName || businessArea.primaryname);
    setVal('businessAreaDescription', businessArea.Description || businessArea.description);
    
    // Set dropdowns - try multiple field name variations
    const statusValue = businessArea.Status || businessArea.status || businessArea.Status_ID || businessArea.status_id || businessArea.statusId;
    const lifecycleValue = businessArea.Lifecycle || businessArea.lifecycle || businessArea.Lifecycle_ID || businessArea.lifecycle_id || businessArea.lifecycleId;
    const viewingValue = businessArea.Is_Public || businessArea.isPublic || businessArea.ispublic || businessArea.BUDG_Viewing || businessArea.budgViewing || businessArea.Viewing_ID || businessArea.viewing_id;
    
    console.log('Status value to set:', statusValue, 'from fields:', {
        Status: businessArea.Status,
        status: businessArea.status,
        Status_ID: businessArea.Status_ID,
        status_id: businessArea.status_id
    });
    
    setSel('budgStatus', statusValue);
    setSel('lifecycle', lifecycleValue);
    setSel('budgViewing', viewingValue);
    
    console.log('Form populated successfully');
    console.log('Final form values:', {
        budgStatus: document.getElementById('budgStatus')?.value,
        lifecycle: document.getElementById('lifecycle')?.value,
        budgViewing: document.getElementById('budgViewing')?.value
    });
    
    // Save original form values after populating
    saveOriginalFormValues();
    
    // Restore change tracking state (only if it was already dirty before population)
    // If we're just loading initial data, mark as clean
    if (!wasTrackingChanges) {
        hasFormChanges = false;
        isDirty = false;
        console.log('✅ Form marked as clean after initial population');
    } else {
        console.log('⚠️ Form was already dirty, keeping dirty state');
    }
}

async function loadParentBusinessArea(parentId) {
    try {
        console.log('=== LOADING PARENT BUSINESS AREA DEBUG ===');
        console.log('Loading parent business area with ID:', parentId);
        
        if (parentId) {
            const parent = await window.BUDG_API_SERVICE.getBusinessAreaById(parentId);
            console.log('Parent business area loaded:', parent);
            
            if (parent) {
                const parentData = parent?.data || parent;
                console.log('Parent data:', parentData);
                console.log('Parent data keys:', Object.keys(parentData));
                
                // Update parent name input (like Policy Edit)
                const parentNameInput = document.getElementById('parentName');
                parentNameInput.value = parentData.primaryName || parentData.PrimaryName || '';
                parentNameInput.dataset.parentId = parentData.id;
                
                console.log('Parent business area loaded successfully');
            } else {
                console.log('No parent business area data found');
            }
        } else {
            console.log('No parent ID found, skipping parent load');
        }
    } catch (error) {
        console.error('Failed to load parent business area:', error);
        const parentNameInput = document.getElementById('parentName');
        if (parentNameInput) {
            parentNameInput.value = '';
            parentNameInput.dataset.parentId = '';
        }
    }
}

function updateTitle(businessArea) {
    const titleElement = document.getElementById('businessAreaTitle');
    if (titleElement && businessArea) {
        const name = businessArea.PrimaryName || businessArea.primaryName || businessArea.name || businessArea.Name || 'Business Area';
        titleElement.textContent = name;
        console.log('Title updated to:', name);
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
    const backgroundColor = isError ? '#ef4444' : '#248567';
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
let isDirty = false;

function markAsDirty() {
    console.log('🔄 markAsDirty called - marking form as changed');
    isDirty = true;
    hasFormChanges = true;
    console.log('✅ hasFormChanges set to:', hasFormChanges);
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

// Removed duplicate saveBusinessArea function - using the one at line 1497 instead

function setupEventListeners() {
    console.log('Setting up event listeners...');
    
    // Tab switching
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', function() {
            const targetTab = this.getAttribute('data-tab');
            switchTab(targetTab);
        });
    });
    
    // Save buttons (like Policy Edit)
    const saveBtn = document.getElementById('saveBtn');
    const saveCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');

    if (saveBtn) saveBtn.addEventListener('click', () => saveBusinessArea(false));
    if (saveCloseBtn) saveCloseBtn.addEventListener('click', () => saveBusinessArea(true));
    if (closeBtn) closeBtn.addEventListener('click', async () => {
        const id = parseId();
        // Release lock before canceling
        if (window.currentLockManager) {
            await window.currentLockManager.releaseLock();
        }
        window.onbeforeunload = null;
        const activeTab = document.querySelector('.tab.active');
        const activeTabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
        console.log('Close button clicked, redirecting to business area view with ID:', id, 'and tab:', activeTabName);
        window.location.href = `/view/business-area/business-area.html?id=${id}&tab=${activeTabName}`;
    });
    
    // Form change tracking (like Policy Edit)
    const formInputs = document.querySelectorAll('input, select, textarea');
    console.log('📝 Setting up change listeners for', formInputs.length, 'form inputs');
    formInputs.forEach(input => {
        // Do not clone/replace custom field inputs.
        // CustomFields keeps direct element references for getValue()/validate(),
        // and replacing nodes breaks save for edited custom field values.
        if (input.closest('#customFieldsContainer')) {
            return;
        }
        // Remove any existing listeners first to avoid duplicates
        const newInput = input.cloneNode(true);
        input.parentNode.replaceChild(newInput, input);
        
        // Add listeners to the new element
        newInput.addEventListener('input', function(e) {
            console.log('📝 Input event on:', e.target.id || e.target.name, 'value:', e.target.value);
            markAsDirty();
        });
        newInput.addEventListener('change', function(e) {
            console.log('📝 Change event on:', e.target.id || e.target.name, 'value:', e.target.value);
            markAsDirty();
        });
    });
    
    // Also use event delegation to catch dynamically added/changed elements
    document.addEventListener('change', function(e) {
        if (e.target.matches('input, select, textarea') && 
            !e.target.closest('#customFieldsContainer')) {
            console.log('📝 Delegated change event on:', e.target.id || e.target.name, 'value:', e.target.value);
            markAsDirty();
        }
    });
    
    document.addEventListener('input', function(e) {
        if (e.target.matches('input, select, textarea') && 
            !e.target.closest('#customFieldsContainer')) {
            console.log('📝 Delegated input event on:', e.target.id || e.target.name, 'value:', e.target.value);
            markAsDirty();
        }
    });
    
    // Custom fields change tracking (use event delegation to catch dynamically added fields)
    document.addEventListener('change', function(e) {
        if (e.target.closest('#customFieldsContainer')) {
            markAsDirty();
        }
    });
    
    document.addEventListener('input', function(e) {
        if (e.target.closest('#customFieldsContainer')) {
            markAsDirty();
        }
    });
    
    // Also listen for custom multiselect changes
    document.addEventListener('click', function(e) {
        if (e.target.closest('.custom-multiselect-container')) {
            // Mark as dirty when clicking on multiselect (tag removal, dropdown selection)
            if (e.target.closest('.custom-multiselect-tag-remove') || 
                e.target.closest('.custom-multiselect-option')) {
                markAsDirty();
            }
        }
    });
    
    // Set beforeunload handler
    window.onbeforeunload = function(e) {
        if (isDirty || hasFormChanges) {
            console.log('onbeforeunload triggered - showing warning');
            e.preventDefault();
            e.returnValue = '';
        }
    };
    
    // Parent selection button (like Policy Edit)
    const selectParentBtn = document.getElementById('selectParentBtn');
    if (selectParentBtn) {
        selectParentBtn.addEventListener('click', selectParent);
    }
    
    // Clear parent button
    const clearParentBtn = document.getElementById('clearParentBtn');
    if (clearParentBtn) {
        clearParentBtn.addEventListener('click', clearParent);
    }
    
    // Show editor button – advanced rich text editor
    const showEditorBtn = document.getElementById('showEditorBtn');
    if (showEditorBtn) {
        showEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('businessAreaDescription', showEditorBtn);
        });
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
            const filtered = filteredBusinessAreas.filter(businessArea => {
                const name = (businessArea.primaryName || businessArea.PrimaryName || businessArea.name || businessArea.Name || '').toLowerCase();
                const description = (businessArea.description || businessArea.Description || '').toLowerCase();
                return name.includes(searchTerm) || description.includes(searchTerm);
            });
            renderBusinessAreas(filtered);
        });
    }
}

// Global variable to store form data
let formDataCache = {};

// Global variable to track if form has been modified
let hasFormChanges = false;

// Global variable to track if save operation is in progress
let isSaving = false;

// Store original form values to detect actual changes
let originalFormValues = {};

// Function to check if form actually has changes by comparing current values with original
function checkFormHasActualChanges() {
    const currentValues = {
        businessAreaName: document.getElementById('businessAreaName')?.value?.trim() || '',
        businessAreaDescription: document.getElementById('businessAreaDescription')?.value?.trim() || '',
        budgStatus: document.getElementById('budgStatus')?.value || '',
        lifecycle: document.getElementById('lifecycle')?.value || '',
        budgViewing: document.getElementById('budgViewing')?.value || '',
        parentId: document.getElementById('parentName')?.dataset?.parentId || null
    };
    
    // If we don't have original values yet, assume no changes (first load)
    if (!originalFormValues.budgStatus) {
        return false;
    }
    
    // Compare current values with original
    const hasChanges = 
        currentValues.businessAreaName !== (originalFormValues.businessAreaName || '') ||
        currentValues.businessAreaDescription !== (originalFormValues.businessAreaDescription || '') ||
        currentValues.budgStatus !== (originalFormValues.budgStatus || '') ||
        currentValues.lifecycle !== (originalFormValues.lifecycle || '') ||
        currentValues.budgViewing !== (originalFormValues.budgViewing || '') ||
        currentValues.parentId !== (originalFormValues.parentId || null);
    
    if (hasChanges) {
        console.log('🔍 Form has actual changes detected:', {
            businessAreaName: currentValues.businessAreaName !== (originalFormValues.businessAreaName || ''),
            businessAreaDescription: currentValues.businessAreaDescription !== (originalFormValues.businessAreaDescription || ''),
            budgStatus: currentValues.budgStatus !== (originalFormValues.budgStatus || ''),
            lifecycle: currentValues.lifecycle !== (originalFormValues.lifecycle || ''),
            budgViewing: currentValues.budgViewing !== (originalFormValues.budgViewing || ''),
            parentId: currentValues.parentId !== (originalFormValues.parentId || null)
        });
    }
    
    return hasChanges;
}

// Function to save original form values
function saveOriginalFormValues() {
    originalFormValues = {
        businessAreaName: document.getElementById('businessAreaName')?.value?.trim() || '',
        businessAreaDescription: document.getElementById('businessAreaDescription')?.value?.trim() || '',
        budgStatus: document.getElementById('budgStatus')?.value || '',
        lifecycle: document.getElementById('lifecycle')?.value || '',
        budgViewing: document.getElementById('budgViewing')?.value || '',
        parentId: document.getElementById('parentName')?.dataset?.parentId || null
    };
    console.log('💾 Saved original form values:', originalFormValues);
}

function saveCurrentFormData() {
    const currentTab = document.querySelector('.tab.active');
    if (!currentTab) return;
    
    const tabName = currentTab.getAttribute('data-tab');
    if (!tabName) return;
    
    // Save all form data in the current tab
    const formData = {};
    const currentTabContent = document.getElementById(`${tabName}Container`);
    if (currentTabContent) {
        const inputs = currentTabContent.querySelectorAll('input, textarea, select');
        inputs.forEach(input => {
            if (input.type === 'checkbox' || input.type === 'radio') {
                formData[input.name || input.id] = input.checked;
            } else {
                formData[input.name || input.id] = input.value;
            }
        });
    }
    
    formDataCache[tabName] = formData;
    console.log('Saved form data for tab:', tabName, formData);
}

function restoreFormData(tabName) {
    if (!formDataCache[tabName]) return;
    
    const tabContent = document.getElementById(`${tabName}Container`);
    if (!tabContent) return;
    
    const formData = formDataCache[tabName];
    const inputs = tabContent.querySelectorAll('input, textarea, select');
    
    inputs.forEach(input => {
        const key = input.name || input.id;
        if (formData.hasOwnProperty(key)) {
            if (input.type === 'checkbox' || input.type === 'radio') {
                input.checked = formData[key];
            } else {
                input.value = formData[key];
            }
        }
    });
    
    console.log('Restored form data for tab:', tabName, formData);
}

function switchTab(tabName) {
    console.log('Switching to tab:', tabName);
    
    // Save current form data before switching tabs
    saveCurrentFormData();
    
    // Update tab buttons
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        if (tab.getAttribute('data-tab') === tabName) {
            tab.classList.add('active');
        } else {
            tab.classList.remove('active');
        }
    });
    
    // Update tab content
    const containers = [
        'summaryContainer',
        'relationshipsContainer',
        'stakeholdersContainer',
        'impact',
        'workflowContainer'
    ];
    
    containers.forEach(containerId => {
        const container = document.getElementById(containerId);
        if (container) {
            if ((tabName === 'impact' && containerId === 'impact') || 
                (tabName !== 'impact' && containerId === `${tabName}Container`)) {
                container.style.display = 'block';
                container.classList.add('active');
            } else {
                container.style.display = 'none';
                container.classList.remove('active');
            }
        }
    });
    
    // Load relationships data if switching to relationships tab
    if (tabName === 'relationships') {
        const id = parseId();
        if (id) {
            loadBusinessAreaHierarchy(id);
        }
    }
    
    // Initialize impact tab if switching to impact
    if (tabName === 'impact') {
        const id = parseId();
        if (id && window.initBusinessAreaImpactEdit) {
            window.initBusinessAreaImpactEdit(id);
        }
    }
    
    // Restore form data for the new tab
    restoreFormData(tabName);
}

// Function to load business area hierarchy data
async function loadBusinessAreaHierarchy(businessAreaId) {
    const tbody = document.getElementById('businessAreaHierarchyTbody');
    const footer = document.getElementById('businessAreaHierarchyFooter');
    
    if (!tbody || !footer) return;
    
    try {
        // Show loading state
        tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>';
        
        // Fetch hierarchy data
        const hierarchy = await window.BUDG_API_SERVICE.getBusinessAreaHierarchy(businessAreaId);
        
        if (!Array.isArray(hierarchy) || hierarchy.length === 0) {
            tbody.innerHTML = `<tr><td colspan="2" style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No hierarchy data available</td></tr>`;
            footer.textContent = '0 records';
            return;
        }

        // Find the current business area (level 0) and organize hierarchy
        const currentBusinessArea = hierarchy.find(item => item.level === 0);
        const parentBusinessAreas = hierarchy.filter(item => item.level < 0); // Parents have negative levels
        const childBusinessAreas = hierarchy.filter(item => item.level > 0); // Children have positive levels
        
        // Create hierarchy groups
        const hierarchyGroups = [];
        
        // Add parent business areas first (if any)
        if (parentBusinessAreas.length > 0) {
            parentBusinessAreas.forEach(function(parent) {
                hierarchyGroups.push({
                    parent: parent,
                    children: [],
                    expanded: true,
                    isParent: true
                });
            });
        }
        
        // Add current business area
        if (currentBusinessArea) {
            hierarchyGroups.push({
                parent: currentBusinessArea,
                children: childBusinessAreas,
                expanded: true,
                isCurrent: true
            });
        }
        
        // Render hierarchy
        let html = '';
        hierarchyGroups.forEach(function(group) {
            const parent = group.parent;
            const isCurrent = group.isCurrent;
            const isParent = group.isParent;
            
            // Parent row
            html += `
                <tr class="hierarchy-row ${isCurrent ? 'current-item' : ''} ${isParent ? 'parent-item' : ''}">
                    <td>
                        <div class="hierarchy-name">
                            ${isCurrent ? '<i class="fas fa-circle" style="color:var(--primary-color,#248567);font-size:0.6rem;margin-right:0.5rem;"></i>' : ''}
                            ${isParent ? '<i class="fas fa-arrow-up" style="color:var(--text-muted,#6b7280);font-size:0.7rem;margin-right:0.5rem;"></i>' : ''}
                            <span class="name-text">${parent.name || parent.Name || parent.primaryName || parent.PrimaryName || 'Unnamed Business Area'}</span>
                        </div>
                    </td>
                    <td>
                        <div class="hierarchy-description">
                            ${parent.description || parent.Description || parent.primaryDescription || parent.PrimaryDescription || '-'}
                        </div>
                    </td>
                </tr>
            `;
            
            // Children rows
            if (group.children && group.children.length > 0) {
                group.children.forEach(function(child) {
                    html += `
                        <tr class="hierarchy-row child-item">
                            <td>
                                <div class="hierarchy-name">
                                    <i class="fas fa-arrow-down" style="color:var(--text-muted,#6b7280);font-size:0.7rem;margin-right:0.5rem;"></i>
                                    <span class="name-text">${child.name || child.Name || child.primaryName || child.PrimaryName || 'Unnamed Business Area'}</span>
                                </div>
                            </td>
                            <td>
                                <div class="hierarchy-description">
                                    ${child.description || child.Description || child.primaryDescription || child.PrimaryDescription || '-'}
                                </div>
                            </td>
                        </tr>
                    `;
                });
            }
        });
        
        tbody.innerHTML = html;
        footer.textContent = `${hierarchy.length} records`;
        
    } catch (error) {
        console.error('Error loading business area hierarchy:', error);
        tbody.innerHTML = `<tr><td colspan="2" style="color:var(--error-color,#ef4444);padding:1rem;text-align:center;">Error loading hierarchy data</td></tr>`;
        footer.textContent = (window.I18n ? window.I18n.t('message.error') : 'Error');
    }
}


async function initializePage() {
    const id = parseId();
    if (!id) {
        console.error('No business area ID found');
        return;
    }
    
    try {
        console.log('=== INITIALIZING PAGE (FIXED) ===');

        // Step 0: Fetch current user
        console.log('Step 0: Fetching current user...');
        await fetchCurrentUser();

        // **FIX: Initialize segment field BEFORE loading data**
        console.log('Step 1: Initializing segment field FIRST...');
        await initializeSegmentField(id);

        // Step 2: Load lookups
        console.log('Step 2: Loading lookups...');
        await loadBusinessAreaEditLookups();

        // Step 3: Load business area data (this will now find segmentField initialized)
        console.log('Step 3: Loading business area data...');
        await loadBusinessArea(id);

        // Apply any pending segment value (only needed if segmentField wasn't ready during populateForm)
        // Only run if there IS a pending value (set when segmentField wasn't ready during populateForm)
        if (window._pendingSegmentValue != null) {
            console.log('🔧 Found pending segment value:', window._pendingSegmentValue);
            if (segmentField) {
                console.log('🔧 Applying pending segment value:', window._pendingSegmentValue);

                // Use retry logic to wait for segment field to be fully initialized with options
                let retries = 0;
                const maxRetries = 30; // 3 seconds total (30 * 100ms)

                const trySetValue = () => {
                    retries++;
                    
                    try {
                        // Check if segment field has options loaded
                        if (!segmentField.hasOptions || !segmentField.hasOptions()) {
                            if (retries < maxRetries) {
                                setTimeout(trySetValue, 100);
                                return;
                            } else {
                                console.error('⚠️ Timeout waiting for segment field options to load');
                                return;
                            }
                        }

                        segmentField.setValue(window._pendingSegmentValue);

                        // Verify it was set
                        const currentValue = segmentField.getValue();
                        
                        if (currentValue === window._pendingSegmentValue) {
                            console.log('✅ Pending segment value applied:', currentValue);
                            delete window._pendingSegmentValue;
                        } else {
                            if (retries < maxRetries) {
                                setTimeout(trySetValue, 100);
                            }
                        }
                    } catch (setError) {
                        console.error('❌ Error setting segment value:', setError);
                        if (retries < maxRetries) {
                            setTimeout(trySetValue, 100);
                        }
                    }
                };

                trySetValue();
            } else {
                console.warn('⚠️ Cannot apply pending segment value - segmentField not available');
            }
        }

        // Step 4: Initialize custom fields
        console.log('Step 4: Initializing custom fields...');
        await initializeCustomFields(id);

        console.log('Setting up event listeners...');
        // Setup event listeners after data is loaded
        setupEventListeners();
        
        console.log('Business area edit page initialized successfully');
    } catch (error) {
        console.error('Error initializing business area edit page:', error);
    }
}

// Initialize custom fields
async function initializeCustomFields(businessAreaId) {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'Business Area',
                containerId: 'customFieldsContainer',
                mode: 'edit',
                objectId: businessAreaId
            });
            console.log('Custom fields initialized:', window.customFieldsContext);
        } else {
            console.warn('CustomFields not available');
        }
    } catch (error) {
        console.error('Error initializing custom fields:', error);
    }
}

// **FIX: Updated segment field initialization**
async function initializeSegmentField(businessAreaId) {
    try {
        console.log('🔧 Starting segment field initialization...');
        console.log('🔧 window.SegmentField available:', !!window.SegmentField);
        console.log('🔧 segmentFieldContainer exists:', !!document.getElementById('segmentFieldContainer'));

        if (window.SegmentField) {
            console.log('🔧 Calling SegmentField.init...');
            segmentField = await SegmentField.init('segmentFieldContainer', {
                label: 'Segment',
                required: true,
                // Removed defaultValue - let API data set the correct value
                sectionTitle: 'SEGMENTATION',
                objectType: 'BusinessArea',
                fieldId: 'baSegment',
                errorId: 'baSegmentError'
            });
            console.log('✅ Segment field initialized and ready');
            console.log('✅ segmentField object:', segmentField);
            console.log('✅ segmentField.getValue method:', typeof segmentField?.getValue);
            bindSegmentParentFilterListener();
            // Don't set value here - let it be set by populateForm's pending value
        } else {
            console.warn('⚠️ SegmentField not available - checking if script loaded');
            // Check if the script is loaded
            const scripts = document.querySelectorAll('script');
            let segmentFieldScriptFound = false;
            scripts.forEach(script => {
                if (script.src && script.src.includes('segment-field.js')) {
                    segmentFieldScriptFound = true;
                }
            });
            console.log('⚠️ segment-field.js script found in DOM:', segmentFieldScriptFound);
        }
    } catch (error) {
        console.error('❌ Error initializing segment field:', error);
        console.error('❌ Error details:', error.message);
        console.error('❌ Error stack:', error.stack);
    }
}

function getSegmentCubeRequestedSegmentIdForParentFilter() {
    const fromUrl = parseInt(new URLSearchParams(window.location.search).get('segmentId'), 10);
    if (Number.isInteger(fromUrl) && fromUrl > 0) {
        return fromUrl;
    }

    const cube = window.globalSegmentsCubePanel;
    if (!cube || !cube.selectedSegmentIds || typeof cube.selectedSegmentIds.size !== 'number') {
        return null;
    }

    if (cube.selectedSegmentIds.size === 1) {
        const [single] = Array.from(cube.selectedSegmentIds);
        const parsed = parseInt(single, 10);
        return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
    }

    return null;
}

function getActiveParentFilterSegmentIdForBusinessArea() {
    const selected = (segmentField && typeof segmentField.getValue === 'function')
        ? segmentField.getValue()
        : null;
    if (Number.isInteger(selected) && selected > 0) {
        return selected;
    }
    return getSegmentCubeRequestedSegmentIdForParentFilter();
}

async function refreshParentOptionsForCurrentSegment() {
    const segmentId = getActiveParentFilterSegmentIdForBusinessArea();
    const response = await window.BUDG_API_SERVICE.getBusinessAreas(
        Number.isInteger(segmentId) && segmentId > 0 ? { segmentId } : {}
    );

    if (response && response.data && Array.isArray(response.data)) {
        allBusinessAreas = response.data;
    } else if (Array.isArray(response)) {
        allBusinessAreas = response;
    } else if (response && response.success && Array.isArray(response.data)) {
        allBusinessAreas = response.data;
    } else {
        allBusinessAreas = [];
    }

    filteredBusinessAreas = allBusinessAreas.filter(businessArea => {
        const baId = parseInt(businessArea.id || businessArea.ID, 10);
        if (baId === parseInt(currentBusinessAreaId, 10)) {
            return false;
        }
        return !isDescendantOf(businessArea, currentBusinessAreaId, allBusinessAreas);
    });
}

function bindSegmentParentFilterListener() {
    const segmentSelect = document.getElementById('baSegment');
    if (!segmentSelect || segmentSelect.dataset.parentFilterBound === '1') {
        return;
    }
    segmentSelect.dataset.parentFilterBound = '1';
    segmentSelect.dataset.previousSegmentId = segmentSelect.value || '1';

    segmentSelect.addEventListener('change', async () => {
        const previousSegmentId = parseInt(segmentSelect.dataset.previousSegmentId || segmentSelect.value, 10);
        const requestedSegmentId = parseInt(segmentSelect.value, 10);
        try {
            await refreshParentOptionsForCurrentSegment();

            const parentNameInput = document.getElementById('parentName');
            const selectedParentId = parseInt(parentNameInput?.dataset?.parentId, 10);
            if (!Number.isInteger(selectedParentId) || selectedParentId <= 0) {
                return;
            }

            const allowedParentIds = new Set(
                (filteredBusinessAreas || [])
                    .map(ba => parseInt(ba.id || ba.ID, 10))
                    .filter(id => Number.isInteger(id) && id > 0)
            );

            if (!allowedParentIds.has(selectedParentId)) {
                if (Number.isInteger(previousSegmentId) && previousSegmentId > 0) {
                    segmentSelect.value = String(previousSegmentId);
                }
                showSuccessMessage('This parent is not valid for the selected segment. Please remove the parent first.', true);
                return;
            }
            if (Number.isInteger(requestedSegmentId) && requestedSegmentId > 0) {
                segmentSelect.dataset.previousSegmentId = String(requestedSegmentId);
            }
        } catch (error) {
            console.error('Failed to refresh parent options after segment change:', error);
        }
    });
}

// Parent Selection Functions
async function selectParent() {
    try {
        currentBusinessAreaId = parseId();
        console.log('Opening parent selection modal for business area ID:', currentBusinessAreaId);
        
        // Load all business areas with current segment context
        console.log('=== CALLING getBusinessAreas API (with segment filter) ===');
        await refreshParentOptionsForCurrentSegment();
        
        console.log('Filtered business areas (excluding children):', filteredBusinessAreas);
        
        // Show modal
        showParentSelectionModal();
        
    } catch (error) {
        console.error('Error loading business areas for parent selection:', error);
        alert(window.I18n ? window.I18n.t('message.failedToLoad') : 'Error loading business areas. Please try again.');
    }
}

function showParentSelectionModal() {
    const modal = document.getElementById('parentSelectionModal');
    const businessAreasList = document.getElementById('businessAreasList');
    const searchInput = document.getElementById('parentSearchInput');
    
    // Clear previous content
    businessAreasList.innerHTML = '';
    searchInput.value = '';
    
    // Render business areas
    renderBusinessAreas(filteredBusinessAreas);
    
    // Show modal
    modal.style.display = 'flex';
    
    // Focus search input
    setTimeout(() => {
        searchInput.focus();
    }, 100);
}

function renderBusinessAreas(businessAreas) {
    const businessAreasList = document.getElementById('businessAreasList');
    businessAreasList.innerHTML = '';
    
    // Add "No Parent" option at the top
    const noParentItem = document.createElement('div');
    noParentItem.className = 'business-area-item';
    noParentItem.style.borderBottom = '2px solid var(--border-color, #e2e8f0)';
    noParentItem.innerHTML = `
        <div class="business-area-name" style="font-style: italic; color: var(--text-secondary, #64748b);">No Parent</div>
        <div class="business-area-description" style="font-style: italic; color: var(--text-secondary, #64748b);">Remove parent business area</div>
    `;
    noParentItem.addEventListener('click', () => clearParent());
    businessAreasList.appendChild(noParentItem);
    
    if (businessAreas.length === 0) {
        const noBusinessAreasItem = document.createElement('div');
        noBusinessAreasItem.className = 'no-business-areas';
        noBusinessAreasItem.textContent = (window.I18n ? (window.I18n.t('message.noResults') || 'No other business areas found') : 'No other business areas found');
        businessAreasList.appendChild(noBusinessAreasItem);
        return;
    }
    
    businessAreas.forEach((businessArea, index) => {
        const businessAreaItem = document.createElement('div');
        businessAreaItem.className = 'business-area-item';
        businessAreaItem.dataset.businessAreaId = businessArea.id;
        
        // Add hover effect
        businessAreaItem.addEventListener('mouseenter', () => {
            businessAreaItem.style.backgroundColor = '#f9fafb';
        });
        
        businessAreaItem.addEventListener('mouseleave', () => {
            businessAreaItem.style.backgroundColor = '';
        });
        
        businessAreaItem.innerHTML = `
            <div class="business-area-name">${businessArea.primaryName || businessArea.PrimaryName || 'Unnamed Business Area'}</div>
            <div class="business-area-description">${businessArea.description || businessArea.Description || 'No description'}</div>
        `;
        
        businessAreaItem.addEventListener('click', () => selectBusinessAreaAsParent(businessArea));
        businessAreasList.appendChild(businessAreaItem);
    });
}

function selectBusinessAreaAsParent(businessArea) {
    console.log('Selected parent business area:', businessArea);
    
    // Update parent name input (like Policy Edit)
    const parentNameInput = document.getElementById('parentName');
    parentNameInput.value = businessArea.primaryName || businessArea.PrimaryName || 'Unnamed Business Area';
    
    // Store parent ID in dataset (like Policy Edit)
    parentNameInput.dataset.parentId = businessArea.id;
    console.log('Set parent ID in dataset:', parentNameInput.dataset.parentId);
    
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
    
    console.log('Parent cleared');
}

function isDescendantOf(businessArea, ancestorId, allBusinessAreas) {
    // Check if business area is a direct child of ancestor
    if (businessArea.parentId === ancestorId || businessArea.Parent_ID === ancestorId) {
        return true;
    }
    
    // If business area has no parent, it's not a descendant
    if (!businessArea.parentId && !businessArea.Parent_ID) {
        return false;
    }
    
    // Find parent business area and check recursively
    const parentId = businessArea.parentId || businessArea.Parent_ID;
    const parentBusinessArea = allBusinessAreas.find(ba => (ba.id || ba.ID) === parentId);
    if (!parentBusinessArea) {
        return false;
    }
    
    // Recursively check if parent is descendant of ancestor
    return isDescendantOf(parentBusinessArea, ancestorId, allBusinessAreas);
}

// Initialize form validation
function initializeFormValidation() {
    // Form validation on input
    const form = document.querySelector('.edit-form-container');
    if (form) {
        form.addEventListener('input', validateForm);
    }
}

// Tab switching and save functionality
function initializeTabSwitching() {
    
    // Tab switching functionality
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', function() {
            const tabName = this.getAttribute('data-tab');
            switchTab(tabName);
        });
    });
    
    // Save button functionality
    const saveBtn = document.getElementById('saveBtn');
    if (saveBtn) {
        saveBtn.addEventListener('click', () => saveBusinessArea(false));
    }
    
    // Save & Close button functionality
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    if (saveAndCloseBtn) {
        saveAndCloseBtn.addEventListener('click', () => saveBusinessArea(true));
    }
    
    // Close button functionality
    const closeBtn = document.getElementById('closeBtn');
    if (closeBtn) {
        closeBtn.addEventListener('click', cancelForm);
    }
}

function switchTab(tabName) {
    // Remove active class from all tabs
    document.querySelectorAll('.tab').forEach(tab => {
        tab.classList.remove('active');
    });
    
    // Hide all tab contents
    document.querySelectorAll('.tab-content').forEach(content => {
        content.style.display = 'none';
        content.classList.remove('active');
    });
    
    // Activate selected tab
    const activeTab = document.querySelector(`[data-tab="${tabName}"]`);
    if (activeTab) {
        activeTab.classList.add('active');
    }
    
    // Show selected tab content
    let tabContent;
    if (tabName === 'impact') {
        tabContent = document.getElementById('impact');
    } else {
        tabContent = document.getElementById(`${tabName}Container`);
    }
    
    if (tabContent) {
        tabContent.style.display = 'block';
        tabContent.classList.add('active');
    }
    
    // Initialize tab-specific functionality
    if (tabName === 'stakeholders') {
        initializeStakeholdersTab();
    } else if (tabName === 'relationships') {
        initializeRelationshipsTab();
    } else if (tabName === 'impact') {
        const id = parseId();
        if (id && window.initBusinessAreaImpactEdit) {
            window.initBusinessAreaImpactEdit(id);
        }
    }
}

function initializeStakeholdersTab() {
    const stakeholdersContainer = document.getElementById('stakeholdersContainer');
    if (!stakeholdersContainer) {
        return;
    }
    
    // Show loading state
    stakeholdersContainer.innerHTML = '<div class="loading" style="padding: 2rem; text-align: center;">' + (window.I18n ? window.I18n.t('message.loading') : 'Loading stakeholders...') + '</div>';
    
    // Initialize stakeholder edit functionality
    if (window.BusinessAreaStakeholderEdit && currentBusinessAreaId) {
        window.BusinessAreaStakeholderEdit.init(currentBusinessAreaId);
    } else {
        stakeholdersContainer.innerHTML = '<div class="error" style="padding: 2rem; text-align: center; color: red;">' + (window.I18n ? window.I18n.t('message.error') : 'Error') + ': Stakeholder edit functionality not available</div>';
    }
}

function initializeRelationshipsTab() {
    // Load business area hierarchy if not already loaded
    const tbody = document.getElementById('businessAreaHierarchyTbody');
    if (tbody && tbody.innerHTML.includes('Loading...')) {
        loadBusinessAreaHierarchy(currentBusinessAreaId);
    }
}

// Save data based on active tab
async function saveBusinessArea(closeAfterSave = false) {
    // Prevent double save operations
    if (isSaving) {
        console.log('Save operation already in progress, ignoring duplicate request');
        return;
    }
    
    isSaving = true;
    
    try {
        console.log('Saving business area...');
        
        // Disable save buttons during save operation
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn'), document.getElementById('closeBtn')];
        buttons.forEach(btn => {
            if (btn) {
                btn.disabled = true;
                btn.classList.add('saving');
                const savingTxt = window.I18n ? window.I18n.t('message.saving') : 'Saving...';
                if (btn.id === 'saveBtn') btn.textContent = savingTxt;
                if (btn.id === 'saveAndCloseBtn') btn.textContent = savingTxt;
            }
        });
        
        let saveSuccess = false;
        
        // Get active tab at the beginning of save operation
        const activeTab = document.querySelector('.tab.active');
        const activeTabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
        
        console.log('Active tab during save:', activeTabName);
        
        // No saveable data on this tab
        if (activeTabName === 'relationships' || activeTabName === 'workflow') {
            showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.noDataToSaveOnTab') : 'No data to save on this tab', true);
            return;
        }

        // Save only the active tab's data (regulation-edit pattern)
        if (activeTabName === 'summary') {
            // Validate required fields
            const validationErrors = validateForm();
            if (validationErrors !== true) {
                showSuccessMessage(validationErrors, true);
                return;
            }
            if (segmentField && !segmentField.validate()) {
                return;
            }
            // No changes on summary tab
            const formHasChanges = hasFormChanges || checkFormHasActualChanges();
            const hasCustomFieldsContext = window.customFieldsContext && window.customFieldsContext.saveValues;
            if (!formHasChanges && !hasCustomFieldsContext) {
                showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.noChangesToSave') : 'No changes to save', true);
                return;
            }
            if (formHasChanges || hasCustomFieldsContext) {
                if (formHasChanges) {
                    saveSuccess = await saveBusinessAreaData();
                    if (!saveSuccess) return;
                }
                if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                    try {
                        await window.customFieldsContext.saveValues(currentBusinessAreaId);
                    } catch (customFieldsError) {
                        console.error('❌ Error saving custom fields:', customFieldsError);
                        showSuccessMessage('Business area saved, but Custom Fields failed: ' + (customFieldsError.message || 'Unknown error'), true);
                    }
                }
                if (!formHasChanges && hasCustomFieldsContext) saveSuccess = true;
            }
            // Trigger impact save when segment changed (re-validate cross-segment links).
            if (saveSuccess && window.saveBusinessAreaImpactData) {
                try {
                    const baImpactDirty = typeof window.hasBusinessAreaImpactChanges === 'function' && window.hasBusinessAreaImpactChanges();
                    const currentBASegment = normalizeBusinessAreaSegmentId(segmentField ? segmentField.getValue() : null);
                    const baSegmentChanged = currentBASegment !== normalizeBusinessAreaSegmentId(originalBusinessAreaSegmentId);
                    if (baImpactDirty || baSegmentChanged) {
                        if (baSegmentChanged && !baImpactDirty && window.initBusinessAreaImpactEdit) {
                            console.log('=== Segment changed; reloading business area impact before save ===');
                            await window.initBusinessAreaImpactEdit(currentBusinessAreaId);
                        }
                        const impactResult = await window.saveBusinessAreaImpactData(currentBusinessAreaId);
                        if (!impactResult || impactResult.success === false) {
                            showSuccessMessage('Business area saved, but Impact data failed: ' + (impactResult?.message || 'Unknown error'), true);
                        }
                    }
                } catch (impactErr) {
                    console.error('Error saving business area impact on segment change:', impactErr);
                }
            }
        } else if (activeTabName === 'stakeholders') {
            const stakeholdersHaveChanges = window.BusinessAreaStakeholderEdit &&
                window.BusinessAreaStakeholderEdit.hasStakeholderChanges &&
                window.BusinessAreaStakeholderEdit.hasStakeholderChanges();
            if (!stakeholdersHaveChanges) {
                showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.noChangesToSave') : 'No changes to save', true);
                return;
            }
            if (!window.BusinessAreaStakeholderEdit || !window.BusinessAreaStakeholderEdit.saveStakeholders) {
                showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.stakeholdersNotLoaded') : 'Stakeholders not loaded', true);
                return;
            }
            const ok = await window.BusinessAreaStakeholderEdit.saveStakeholders(false);
            if (ok === false) return;
            saveSuccess = true;
            if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                try {
                    await window.customFieldsContext.saveValues(currentBusinessAreaId);
                } catch (customFieldsError) {
                    console.error('❌ Error saving custom fields:', customFieldsError);
                }
            }
        } else if (activeTabName === 'impact') {
            const impactHasChanges = window.hasBusinessAreaImpactChanges && typeof window.hasBusinessAreaImpactChanges === 'function' && window.hasBusinessAreaImpactChanges();
            if (!impactHasChanges) {
                showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.noImpactChangesToSave') : 'No impact changes to save', true);
                return;
            }
            if (window.saveBusinessAreaImpactData && typeof window.saveBusinessAreaImpactData === 'function') {
                const impactResult = await window.saveBusinessAreaImpactData();
                saveSuccess = impactResult && impactResult.success !== false;
            } else {
                saveSuccess = true;
            }
            if (saveSuccess && window.customFieldsContext && window.customFieldsContext.saveValues) {
                try {
                    await window.customFieldsContext.saveValues(currentBusinessAreaId);
                } catch (customFieldsError) {
                    console.error('❌ Error saving custom fields:', customFieldsError);
                }
            }
        }

        if (saveSuccess) {
            if (window.currentLockManager) {
                await window.currentLockManager.releaseLock();
            }
            if (activeTabName === 'summary') {
                originalBusinessAreaSegmentId = normalizeBusinessAreaSegmentId(segmentField ? segmentField.getValue() : null);
            }
            showSuccessMessage('UPDATES SAVED');
            if (activeTabName === 'summary') {
                markAsClean();
                hasFormChanges = false;
                try {
                    await new Promise(resolve => setTimeout(resolve, 300));
                    await loadBusinessArea(currentBusinessAreaId);
                } catch (reloadError) {
                    console.error('Error reloading business area data:', reloadError);
                }
            } else {
                markAsClean();
            }
            if (closeAfterSave) {
                window.onbeforeunload = null;
                setTimeout(() => {
                    window.location.href = `/view/business-area/business-area.html?id=${currentBusinessAreaId}&tab=${activeTabName}`;
                }, 1500);
            }
        } else {
            showSuccessMessage('Failed to save changes', true);
        }
        
    } catch (error) {
        console.error('Error saving business area:', error);
        const errorMsg = error?.body?.error || error?.body?.message || error?.message || 'Unknown error';
        alert('Error saving business area: ' + errorMsg);
    } finally {
        // Re-enable save buttons
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn'), document.getElementById('closeBtn')];
        buttons.forEach(btn => {
            if (btn) {
                btn.disabled = false;
                btn.classList.remove('saving');
                if (btn.id === 'saveBtn') btn.textContent = 'Save';
                if (btn.id === 'saveAndCloseBtn') btn.textContent = 'Save & Close';
            }
        });
        
        // Reset saving flag
        isSaving = false;
    }
}

// Save business area main data
async function saveBusinessAreaData() {
    try {
        console.log('Saving business area main data...');
        
        // Get user ID before saving
        let userId = getCurrentUserId();
        console.log('Initial user ID:', userId);
        if (!userId) {
            console.error('❌ No user ID found, attempting to fetch current user...');
            await fetchCurrentUser();
            userId = getCurrentUserId();
            console.log('User ID after fetch:', userId);
            if (!userId) {
                alert('Unable to identify current user. Please log in again.');
                return false;
            }
        }

        // **FIX: Get segment value with fallback and validation**
        let segmentValue = 1; // Default to Enterprise
        if (segmentField && typeof segmentField.getValue === 'function') {
            segmentValue = segmentField.getValue();
            console.log('🔍 Segment value from field.getValue():', segmentValue);
        } else {
            console.warn('⚠️ Segment field not available, using default value 1');
        }

        // Validate segment value
        if (!segmentValue || segmentValue === null || segmentValue === undefined) {
            console.warn('⚠️ Invalid segment value, falling back to 1');
            segmentValue = 1;
        }

        // Sync rich-text editor content back to textarea before reading
        if (typeof syncAdvancedRichTextToTextarea === 'function') {
            syncAdvancedRichTextToTextarea('businessAreaDescription');
        }

        const formData = {
            primaryName: document.getElementById('businessAreaName').value,
            description: document.getElementById('businessAreaDescription').value || null,
            parentId: document.getElementById('parentName').dataset.parentId ? parseInt(document.getElementById('parentName').dataset.parentId, 10) : null,
            status: parseInt(document.getElementById('budgStatus').value, 10),
            lifecycle: parseInt(document.getElementById('lifecycle').value, 10),
            budgViewing: parseInt(document.getElementById('budgViewing').value, 10),
            segmentId: parseInt(segmentValue, 10), // Ensure it's an integer
            lastUpdateUserId: userId
        };

        console.log('📤 Complete save payload:', JSON.stringify(formData, null, 2));
        console.log('📤 segmentId in payload:', formData.segmentId, 'Type:', typeof formData.segmentId);

        // Validate required fields
        if (!formData.primaryName.trim()) {
            alert('Primary Name is required');
            return false;
        }
        
        if (!Number.isInteger(formData.status)) {
            alert('BUDG Status is required');
            return false;
        }
        
        if (!Number.isInteger(formData.lifecycle)) {
            alert('Lifecycle is required');
            return false;
        }
        
        if (!Number.isInteger(formData.budgViewing)) {
            alert('BUDG Viewing is required');
            return false;
        }
        
        if (segmentField && !segmentField.validate()) {
            return false;
        }

        // Make API call to save business area using the API service
        console.log('=== CALLING updateBusinessArea API ===');
        console.log('ID:', currentBusinessAreaId);
        console.log('Payload:', formData);
        
        const response = await window.BUDG_API_SERVICE.updateBusinessArea(currentBusinessAreaId, formData);
        console.log('Save response:', response);
        
        if (response && response.success === false) {
            // Check if it's a lock error
            if (response.locked) {
                const lockedBy = response.lockedBy || 'another user';
                const isPermanent = response.isPermanent || false;
                const message = `This business area is currently locked by ${lockedBy}. ${isPermanent ? 'This is a permanent lock.' : 'Please try again later.'}`;
                showSuccessMessage(message, true);
                // Release our lock attempt
                if (window.currentLockManager) {
                    await window.currentLockManager.releaseLock();
                }
                window.onbeforeunload = null;
                setTimeout(() => {
                    window.location.href = `/view/business-area/business-area.html?id=${currentBusinessAreaId}`;
                }, 3000);
                return false;
            }
            throw new Error(response.message || 'Update failed');
        }
        
        // Success: backend returns { success: true, message: "..." } or sometimes { id: ... }
        const isSuccess = response && (
            response.success === true ||
            (typeof response.id === 'number' && response.success !== false) ||
            (response.message && String(response.message).toLowerCase().includes('updated successfully'))
        );
        if (isSuccess) {
            console.log('Business area saved successfully');
            
            // Note: Custom fields are saved in the main saveBusinessArea function
            // to ensure they're saved regardless of which tab is active
            
            return true;
        } else {
            const serverMessage = (response && (response.error || response.message)) || '';
            console.error('Save failed:', response);
            alert(serverMessage ? `Failed to save business area: ${serverMessage}` : 'Failed to save business area. Please try again.');
            return false;
        }
        
    } catch (error) {
        console.error('Error saving business area data:', error);
        alert('Error saving business area: ' + (error.message || 'Unknown error'));
        return false;
    }
}

// Validate form before saving
// Returns true if valid, or an error message string if invalid
function validateForm() {
    const businessAreaNameInput = document.getElementById('businessAreaName');
    const budgStatusSelect = document.getElementById('budgStatus');
    const lifecycleSelect = document.getElementById('lifecycle');
    const budgViewingSelect = document.getElementById('budgViewing');

    const errors = [];

    // Clear previous errors
    clearFieldErrors();

    // Validate required fields
    if (!businessAreaNameInput?.value?.trim()) {
        showFieldError('businessAreaNameError', 'Primary Name is required');
        businessAreaNameInput?.classList.add('is-invalid');
        errors.push('Primary Name');
    } else {
        businessAreaNameInput?.classList.remove('is-invalid');
    }

    if (!budgStatusSelect?.value) {
        showFieldError('budgStatusError', 'BUDG Status is required');
        budgStatusSelect?.classList.add('is-invalid');
        errors.push('BUDG Status');
    } else {
        budgStatusSelect?.classList.remove('is-invalid');
    }

    if (!lifecycleSelect?.value) {
        showFieldError('lifecycleError', 'Lifecycle is required');
        lifecycleSelect?.classList.add('is-invalid');
        errors.push('Lifecycle');
    } else {
        lifecycleSelect?.classList.remove('is-invalid');
    }

    if (!budgViewingSelect?.value) {
        showFieldError('budgViewingError', 'BUDG Viewing is required');
        budgViewingSelect?.classList.add('is-invalid');
        errors.push('BUDG Viewing');
    } else {
        budgViewingSelect?.classList.remove('is-invalid');
    }
    
    // Validate custom fields
    if (window.customFieldsContext && window.customFieldsContext.validate) {
        const customFieldsValid = window.customFieldsContext.validate();
        if (!customFieldsValid) {
            errors.push('Custom Fields (see highlighted fields below)');
            // Scroll the custom fields section into view
            const customFieldsSection = document.getElementById('customFieldsSection');
            if (customFieldsSection) {
                customFieldsSection.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        }
    }

    if (errors.length > 0) {
        console.warn('Validation failed for fields:', errors);
        // Scroll to the first invalid field if it's not custom fields
        if (!errors[0].includes('Custom Fields')) {
            const firstInvalid = document.querySelector('.is-invalid');
            if (firstInvalid) {
                firstInvalid.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        }
        return 'Required fields missing: ' + errors.join(', ');
    }
    return true;
}

// Show field error
function showFieldError(fieldId, message) {
    const errorElement = document.getElementById(fieldId);
    if (errorElement) {
        errorElement.textContent = message;
        errorElement.style.display = 'block';
        // Add animation class after a small delay to ensure display is set
        setTimeout(() => {
            errorElement.classList.add('show');
        }, 10);
    }
}

// Clear field errors
function clearFieldErrors() {
    const errorElements = document.querySelectorAll('.field-error');
    errorElements.forEach(element => {
        element.classList.remove('show');
        element.style.display = 'none';
        element.textContent = '';
    });
    
    // Remove invalid classes from form inputs
    const invalidInputs = document.querySelectorAll('.form-input.is-invalid, .form-select.is-invalid');
    invalidInputs.forEach(input => {
        input.classList.remove('is-invalid');
    });
}

// Check if there are unsaved changes
function hasUnsavedChanges() {
    // Check if form is marked as dirty
    const form = document.querySelector('.edit-form-container');
    return !!(hasUserInteracted && form && form.classList.contains('dirty'));
}

// Mark form as dirty (has unsaved changes)
function markAsDirty() {
    const form = document.querySelector('.edit-form-container');
    if (form) {
        form.classList.add('dirty');
    }
}

// Mark form as clean (no unsaved changes)
function markAsClean() {
    const form = document.querySelector('.edit-form-container');
    if (form) {
        form.classList.remove('dirty');
    }
}

// Load business area hierarchy for relationships tab
async function loadBusinessAreaHierarchy(businessAreaId) {
    const tbody = document.getElementById('businessAreaHierarchyTbody');
    const footer = document.getElementById('businessAreaHierarchyFooter');
    
    if (!tbody || !footer) return;
    
    try {
        // Show loading state
        tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>';
        
        // Fetch all business areas from hierarchy endpoint
        console.log('Fetching business areas from /api/business-areas/hierarchy');
        const response = await fetch('/api/business-areas/hierarchy');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const businessAreas = await response.json();
        
        console.log('Business areas loaded for hierarchy:', businessAreas);
        
        if (!Array.isArray(businessAreas) || businessAreas.length === 0) {
            tbody.innerHTML = '<tr><td colspan="2" style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No business areas found in database</td></tr>';
            footer.textContent = '0 records';
            return;
        }

        // Build direct lineage tree (current + ancestors + descendants + siblings)
        const filteredBusinessAreas = buildDirectLineageTree(businessAreas, businessAreaId);
        
        if (filteredBusinessAreas.length === 0) {
            tbody.innerHTML = '<tr><td colspan="2" style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No related business areas found</td></tr>';
            footer.textContent = '0 records';
            return;
        }

        // Build hierarchy tree
        const hierarchyRows = buildHierarchyTree(filteredBusinessAreas, 0);
        
        // Render table
        tbody.innerHTML = renderBusinessAreaTable(hierarchyRows, businessAreaId);
        footer.textContent = `${filteredBusinessAreas.length} record${filteredBusinessAreas.length !== 1 ? 's' : ''}`;
        
        // Initialize interactions
        const container = document.querySelector('.relationships-hierarchy');
        if (container) {
            initBusinessAreaInteractions(container, hierarchyRows);
        }
        
    } catch (e) {
        console.error('Failed to load business area hierarchy:', e);
        tbody.innerHTML = `<tr><td colspan="2" style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">Failed to load hierarchy data</td></tr>`;
        footer.textContent = '0 records';
    }
}

// Cancel form and return to view
async function cancelForm() {
    console.log('=== CANCEL FORM ===');
    console.log('Current Business Area ID:', currentBusinessAreaId);
    console.log('Has unsaved changes:', hasUnsavedChanges());
    
    if (hasUnsavedChanges()) {
        const confirmMessage = 'You have unsaved changes. Are you sure you want to close without saving?';
        const confirmClose = await (typeof window.showConfirmDialog === 'function'
            ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
            : Promise.resolve(confirm(confirmMessage)));
        if (confirmClose) {
            console.log('User confirmed close, navigating to view page');
            window.onbeforeunload = null;
            const activeTab = document.querySelector('.tab.active');
            const activeTabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
            const viewUrl = `/view/business-area/business-area.html?id=${currentBusinessAreaId}&tab=${activeTabName}`;
            console.log('Navigating to view URL:', viewUrl);
            window.location.href = viewUrl;
        } else {
            console.log('User cancelled close');
        }
    } else {
        console.log('No unsaved changes, navigating to view page');
        window.onbeforeunload = null;
        const activeTab = document.querySelector('.tab.active');
        const activeTabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
        const viewUrl = `/view/business-area/business-area.html?id=${currentBusinessAreaId}&tab=${activeTabName}`;
        console.log('Navigating to view URL:', viewUrl);
        window.location.href = viewUrl;
    }
}

// Escape HTML to prevent XSS
function escapeHtml(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Get current user ID from session storage
function getCurrentUserId() {
    console.log('🔍 Getting current user ID...');
    const userStr = sessionStorage.getItem('currentUser');
    if (userStr) {
        try {
            const user = JSON.parse(userStr);
            if (user && (user.id || user.ID || user.userId)) {
                const userId = user.id || user.ID || user.userId;
                console.log('✅ User ID found:', userId);
                return userId;
            }
        } catch (e) {
            console.error('❌ Error parsing user data:', e);
        }
    }
    console.warn('⚠️ No user ID found in session');
    return null;
}

// Fetch and cache current user information
async function fetchCurrentUser() {
    console.log('🔄 Fetching current user from /api/me...');
    try {
        const response = await fetch('/api/me', { method: 'GET', credentials: 'include' });
        if (response.ok) {
            const user = await response.json();
            sessionStorage.setItem('currentUser', JSON.stringify(user));
            console.log('✅ Current user fetched and cached:', user);
            return user;
        } else {
            console.error('❌ Failed to fetch user from /api/me:', response.status);
        }
    } catch (error) {
        console.error('❌ Error fetching current user:', error);
    }
    return null;
}

