// Product Edit Page JavaScript
let segmentField = null; // Segment field component reference
let pendingSegmentId = null; // Holds loaded segment until field initializes
/** Segment ID from server when the form was loaded; used to re-validate impact after segment change */
let originalProductSegmentId = null;

function normalizeProductSegmentIdForCompare(value) {
    if (value == null || value === '') return null;
    const n = parseInt(value, 10);
    return Number.isInteger(n) ? n : null;
}

document.addEventListener('DOMContentLoaded', async function() {
    console.log('Initializing product edit page...');
    
    const id = parseId();
    if (!id) {
        console.error('No product ID found in URL');
        return;
    }
    
    // Initialize lock manager
    const lockManager = new window.LockManager('product', id);
    window.currentLockManager = lockManager;
    
    // Setup auto-release on page unload
    lockManager.setupBeforeUnload();
    
    // Check lock status
    const lockStatus = await lockManager.checkLockStatus();
    const status = lockStatus?.status || 'no_lock';
    
    // Handle lock conflicts
    if (status === 'locked_by_other') {
        const lockedBy = lockStatus.lockedByName || 'another user';
        alert(`This product is currently locked by ${lockedBy}. Please try again later.`);
        window.location.href = `/view/product/product.html?id=${id}`;
        return;
    } else if (status === 'permanently_locked') {
        const isSuperAdmin = await lockManager.checkIsSuperAdmin();
        if (!isSuperAdmin) {
            const lockedBy = lockStatus.lockedByName || 'an administrator';
            alert(`This product has a permanent lock by ${lockedBy}. Only administrators can edit it.`);
            window.location.href = `/view/product/product.html?id=${id}`;
            return;
        }
    }
    
    // Acquire lock
    const lockResult = await lockManager.acquireLock(false);
    if (!lockResult || !lockResult.success) {
        // Check if we have details about who locked it
        if (lockResult && lockResult.lockedBy) {
            const lockedBy = lockResult.lockedBy;
            alert(`The object is currently locked by ${lockedBy}. Try again later.`);
        } else {
            alert('Failed to acquire lock. Please try again.');
        }
        window.location.href = `/view/product/product.html?id=${id}`;
        return;
    }
    
    // Update global lock count if available
    if (window.globalLockUI) {
        await window.globalLockUI.updateLockCount();
    }
    
    console.log('Initializing product edit page for ID:', id);
    
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
    const idx = parts.indexOf('product-edit.html');
    if (idx === -1 || parts.length < idx + 2) return null;
    const pathId = parseInt(parts[idx + 1], 10);
    return Number.isNaN(pathId) ? null : pathId;
}

function populateSelectSimple(selectId, list, getLabel) {
    const el = document.getElementById(selectId);
    if (!el) return;
    
    el.innerHTML = '';
    (Array.isArray(list) ? list : []).forEach(item => {
        const op = document.createElement('option');
        op.value = String(item.ID || item.id);
        op.textContent = getLabel ? getLabel(item) : (item.PrimaryName || item.primaryname || item.name || '');
        el.appendChild(op);
    });
}

async function loadProductEditLookups() {
    console.log('Loading product edit lookups...');
    
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
        const [statuses, lifecycle, viewing] = await Promise.all([
            window.BUDG_API_SERVICE.getStatusList(),
            window.BUDG_API_SERVICE.getProductLifecycleList(),
            window.BUDG_API_SERVICE.getViewingList()
        ]);
        
        // Populate dropdowns
        populateSelectSimple('budgStatus', (Array.isArray(statuses?.data) ? statuses.data : statuses), x => x.name);
        populateSelectSimple('lifecycle', lifecycle, x => x.PrimaryName || x.primaryname || x.name);
        populateSelectSimple('budgViewing', viewing, x => x.name);
        
        console.log('Product edit lookups loaded successfully');
    } catch (error) {
        console.error('Error loading product edit lookups:', error);
    }
}

async function loadProduct(id) {
    console.log('Loading product with ID:', id);
    
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
        const product = await window.BUDG_API_SERVICE.getProductById(id);
        console.log('Loaded product data:', product);
        
        if (product) {
            populateForm(product);
            updateTitle(product);
            loadParentProduct(product);
        }
    } catch (error) {
        console.error('Error loading product:', error);
    }
}

function populateForm(product) {
    console.log('Populating form with product data:', product);
    console.log('Product parent data:', {
        parentid: product.parentid,
        parent_id: product.parent_id,
        parentId: product.parentId,
        ParentID: product.ParentID,
        Parent_ID: product.Parent_ID
    });
    
    const setVal = (i, v) => { 
        const el = document.getElementById(i); 
        if (el) {
            el.value = v ?? '';
            console.log(`Set ${i} to:`, v);
        } else {
            console.warn(`Element ${i} not found`);
        }
    };
    
    const setSel = (i, v) => { 
        const el = document.getElementById(i); 
        if (el && v != null) {
            el.value = String(v);
            console.log(`Set select ${i} to:`, v);
        } else if (el) {
            console.log(`Select ${i} not set (value is null/undefined)`);
        } else {
            console.warn(`Select element ${i} not found`);
        }
    };
    
    // Populate form fields based on schema.sql product table structure
    setVal('productName', product.primaryname || product.PrimaryName);
    setVal('productRef', product.refnumber || product.RefNumber);
    setVal('productDescription', product.description || product.Description);
    setVal('longName', product.longname || product.longName || product.LongName);
    
    // Set dropdowns
    setSel('budgStatus', product.status || product.Status);
    setSel('lifecycle', product.lifecycle_status || product.Lifecycle_Status);
    setSel('budgViewing', product.is_public || product.IsPublic);
    
    // Set segment value if available (or defer until component is ready)
    const loadedSegmentId = product.segmentId ?? product.segment_id ?? product.Segment_ID ?? null;
    if (loadedSegmentId != null) {
        pendingSegmentId = loadedSegmentId;
    }
    if (segmentField && pendingSegmentId != null) {
        segmentField.setValue(pendingSegmentId);
    }
    if (segmentField && typeof segmentField.getValue === 'function') {
        originalProductSegmentId = normalizeProductSegmentIdForCompare(segmentField.getValue());
    } else {
        originalProductSegmentId = normalizeProductSegmentIdForCompare(loadedSegmentId);
    }
    
    console.log('Form populated successfully');
}

function loadParentProduct(product) {
    const parentId = getProductParentId(product);
    console.log('Loading parent product for product:', product);
    console.log('Parent ID found:', parentId);
    
    if (parentId) {
        // Load parent product name
        window.BUDG_API_SERVICE.getProductById(parentId)
            .then(parentProduct => {
                console.log('Parent product loaded:', parentProduct);
                const parentNameInput = document.getElementById('parentName');
                if (parentNameInput && parentProduct) {
                    const parentName = parentProduct.primaryname || parentProduct.PrimaryName || parentProduct.name || parentProduct.Name || 'Unnamed Product';
                    parentNameInput.value = parentName;
                    parentNameInput.dataset.parentId = parentId;
                    console.log('Set parent name in input:', parentName);
                    console.log('Set parent ID in dataset:', parentId);
                } else {
                    console.warn('Parent name input not found or parent product is null');
                }
            })
            .catch(error => {
                console.error('Error loading parent product:', error);
            });
    } else {
        console.log('No parent ID found for this product');
    }
}

async function refreshProductParentForSelectedSegment(previousSegmentId) {
    const parentNameInput = document.getElementById('parentName');
    const selectedParentId = parseInt(parentNameInput?.dataset?.parentId || '', 10);
    if (!selectedParentId || selectedParentId <= 0) return true;

    const selectedSegmentId = segmentField ? segmentField.getValue() : null;
    if (segmentField && typeof segmentField.validateParentForSegment === 'function') {
        try {
            const isValid = await segmentField.validateParentForSegment(selectedParentId, selectedSegmentId);
            if (!isValid) {
                alert('The current parent product is not compatible with the selected segment. Please remove or change the parent.');
                return false;
            }
        } catch (e) {
            console.warn('Error validating parent for segment:', e);
        }
    }
    return true;
}

function updateTitle(product) {
    const titleElement = document.getElementById('productTitle');
    if (titleElement && product) {
        const name = product.primaryname || product.PrimaryName || 'Unnamed Product';
        const ref = product.refnumber || product.RefNumber || '';
        titleElement.textContent = ref ? `${ref}: ${name}` : name;
    }
}

function getCurrentActiveTab() {
    const activeTab = document.querySelector('.tab.active');
    return activeTab ? activeTab.getAttribute('data-tab') : 'summaryTab';
}

async function saveProduct(id, closeAfter) {
    const activeTab = getCurrentActiveTab();
    console.log(`=== SAVING PRODUCT (active tab: ${activeTab}) ===`);

    const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
    const restoreButtons = () => { buttons.forEach(b => { if (b) b.disabled = false; }); };
    buttons.forEach(b => { if (b) b.disabled = true; });

    try {
        if (activeTab === 'relationshipsTab') {
            showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.noDataToSaveOnTab') : 'No data to save on this tab', false);
            restoreButtons();
            return;

        } else if (activeTab === 'stakeholdersTab') {
            if (window.ProductStakeholderEdit && window.ProductStakeholderEdit.saveStakeholders) {
                const stakeholdersSaved = await window.ProductStakeholderEdit.saveStakeholders();
                if (stakeholdersSaved) {
                    console.log('✅ Stakeholders saved successfully');
                } else {
                    console.warn('Stakeholders save was cancelled or failed');
                }
            } else {
                showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                restoreButtons();
                return;
            }

        } else if (activeTab === 'impactTab') {
            const impactDirty = window.hasImpactChanges && typeof window.hasImpactChanges === 'function' && window.hasImpactChanges();
            if (!impactDirty) {
                showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                restoreButtons();
                return;
            }
            const impactResult = await window.saveAllImpactData(id);
            if (impactResult && impactResult.success !== false) {
                if (window.initImpactEdit && id) { await window.initImpactEdit(id); }
                console.log('✅ Impact saved successfully');
            } else {
                throw new Error(impactResult?.message || 'Impact save failed');
            }

        } else {
            // summaryTab (default)
            // Sync rich-text editor content back to textarea before reading
            if (typeof syncAdvancedRichTextToTextarea === 'function') {
                syncAdvancedRichTextToTextarea('productDescription');
            }
            if (segmentField && !segmentField.validate()) {
                restoreButtons();
                return;
            }

            const payload = collectFormData();

            const res = await window.BUDG_API_SERVICE.updateProduct(id, payload);

            if (res && res.success === false) {
                if (res.locked) {
                    const lockedBy = res.lockedBy || 'another user';
                    const isPermanent = res.isPermanent || false;
                    showSuccessMessage(`This product is currently locked by ${lockedBy}. ${isPermanent ? 'This is a permanent lock.' : 'Please try again later.'}`, true);
                    if (window.currentLockManager) { await window.currentLockManager.releaseLock(); }
                    setTimeout(() => { window.location.href = `/view/product/product.html?id=${id}`; }, 3000);
                    restoreButtons();
                    return false;
                }
                throw new Error(res.message || 'Update failed');
            }

            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues && id) {
                try {
                    await window.customFieldsContext.saveValues(id);
                    console.log('✅ Custom fields saved successfully');
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                }
            }

            // If segment changed, reload impact data (do not save impact from summary)
            const currentSegmentRaw = Number.isInteger(payload.segmentId)
                ? payload.segmentId
                : (segmentField && typeof segmentField.getValue === 'function' ? segmentField.getValue() : null);
            const newSegmentNorm = normalizeProductSegmentIdForCompare(currentSegmentRaw);
            const segmentChanged = newSegmentNorm !== normalizeProductSegmentIdForCompare(originalProductSegmentId);

            if (segmentChanged && window.initImpactEdit && id) {
                console.log('=== Segment changed; reloading product impact from server ===');
                await window.initImpactEdit(id);
            }

            // Release lock after successful save
            await window.LockInitHelper.releaseLock();

            markAsClean();
            originalProductSegmentId = newSegmentNorm;
        }

        showSuccessMessage('UPDATES SAVED');
        if (closeAfter) {
            setTimeout(() => { window.location.href = `/view/product/product.html?id=${id}`; }, 1500);
        }
    } catch (err) {
        console.error('Save error:', err);
        alert(typeof window.formatSaveError === 'function' ? window.formatSaveError(err) : (err?.body?.error || err?.body?.message || err?.message || 'Failed to save'));
    } finally {
        restoreButtons();
    }
}

function collectFormData() {
    const name = document.getElementById('productName')?.value?.trim();
    const description = document.getElementById('productDescription')?.value?.trim();
    const status = parseInt(document.getElementById('budgStatus')?.value || '', 10);
    const lifecycle = parseInt(document.getElementById('lifecycle')?.value || '', 10);
    const viewing = parseInt(document.getElementById('budgViewing')?.value || '', 10);
    const refNumber = document.getElementById('productRef')?.value?.trim();
    const longName = document.getElementById('longName')?.value?.trim();
    
    const required = [
        { ok: !!name, field: 'Name' },
        { ok: !!description, field: 'Description' },
        { ok: Number.isInteger(status), field: 'BUDG Status' },
        { ok: Number.isInteger(lifecycle), field: 'Lifecycle' },
        { ok: Number.isInteger(viewing), field: 'BUDG Viewing' }
    ];
    
    const missing = required.filter(r => !r.ok).map(r => r.field);
    
    if (missing.length > 0) {
        throw new Error(`Please fill required fields: ${missing.join(', ')}`);
    }
    
    const selectedSegmentId = segmentField ? segmentField.getValue() : pendingSegmentId;

    const payload = {
        primaryname: name,
        description: description,
        refnumber: refNumber || null,
        longname: longName || null,
        status: status,
        lifecycle_status: lifecycle,
        is_public: viewing,
        lastupdateuser_id: getCurrentUserId() // فقط last_update_user في التعديل
    };
    
    if (Number.isInteger(selectedSegmentId)) {
        payload.segmentId = selectedSegmentId;
    }
    
    // Add parent ID if selected
    const parentNameInput = document.getElementById('parentName');
    if (parentNameInput && parentNameInput.dataset.parentId) {
        payload.parent_id = parseInt(parentNameInput.dataset.parentId);
    } else {
        payload.parent_id = null;
    }
    
    return payload;
}

function showSuccessMessage(message, isError = false) {
    const existingMessage = document.getElementById('success-message');
    if (existingMessage) {
        existingMessage.remove();
    }
    
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
        z-index: 1000;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        animation: slideDown 0.3s ease-out;
    `;
    successDiv.textContent = message;
    
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
    document.body.appendChild(successDiv);
    
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

function markAsClean() {
    document.body.classList.remove('dirty');
    preventUnloadWarning = true;
    hasFormChanges = false;
    updateSaveButtons(false);
}

function markAsDirty(e) {
    if (e && e.isTrusted === false) return;
    isDirty = true;
    hasFormChanges = true;
    preventUnloadWarning = false;
    updateSaveButtons();
}

function updateSaveButtons(isDirty = true) {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    
    if (isDirty) {
        if (saveBtn) saveBtn.disabled = false;
        if (saveAndCloseBtn) saveAndCloseBtn.disabled = false;
    } else {
        if (saveBtn) saveBtn.disabled = true;
        if (saveAndCloseBtn) saveAndCloseBtn.disabled = true;
    }
}

// Global variable to store form data
let formDataCache = {};

// Global variable to track if form has been modified
let hasFormChanges = false;

function saveCurrentFormData() {
    const currentTab = document.querySelector('.tab.active');
    if (!currentTab) return;
    
    const tabName = currentTab.getAttribute('data-tab');
    if (!tabName) return;
    
    // Save all form data in the current tab
    const formData = {};
    const currentTabContent = document.getElementById(tabName);
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
    
    const tabContent = document.getElementById(tabName);
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
    // Save current form data before switching tabs
    saveCurrentFormData();
    
    // Remove active class from all tabs and content
    document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));
    
    // Add active class to selected tab and content
    const selectedTab = document.querySelector(`[data-tab="${tabName}"]`);
    const selectedContent = document.getElementById(tabName);
    
    if (selectedTab) selectedTab.classList.add('active');
    if (selectedContent) selectedContent.classList.add('active');
    
    // Load relationships data if switching to relationships tab
    if (tabName === 'relationshipsTab') {
        const id = parseId();
        if (id) {
            loadProductHierarchy(id);
        }
    }
    
    // Load stakeholders data if switching to stakeholders tab
    if (tabName === 'stakeholdersTab') {
        const id = parseId();
        if (id) {
            // Initialize stakeholder edit functionality
            if (window.ProductStakeholderEdit && window.ProductStakeholderEdit.init) {
                window.ProductStakeholderEdit.init(id);
            } else {
                console.error('ProductStakeholderEdit not available');
                const container = document.getElementById('productStakeholdersContainer');
                if (container) {
                    container.innerHTML = '<div class="error">Error: Stakeholder edit functionality not loaded</div>';
                }
            }
        }
    }
    
    // Load impact data if switching to impact tab
    if (tabName === 'impactTab') {
        const id = parseId();
        if (id) {
            // Initialize impact edit functionality
            if (window.initImpactEdit && typeof window.initImpactEdit === 'function') {
                window.initImpactEdit(id);
            } else {
                console.error('Impact edit functionality not available');
            }
        }
    }
    
    // Restore form data for the new tab
    restoreFormData(tabName);
}

// Helper function to escape HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Build direct lineage tree (current + ancestors + descendants + siblings)
function buildDirectLineageTree(products, currentProductId) {
    const byId = new Map();
    products.forEach(p => byId.set(parseInt(p.id), p));

    const current = byId.get(parseInt(currentProductId));
    if (!current) return [];

    const ancestors = new Set();
    const descendants = new Set();

    // Add current product
    descendants.add(parseInt(currentProductId));

    // Find all ancestors
    let parent = current;
    while (parent && parent.parentid) {
        const parentId = parseInt(parent.parentid);
        if (byId.has(parentId)) {
            parent = byId.get(parentId);
            ancestors.add(parentId);
        } else {
            break;
        }
    }

    // Find all descendants (including current product's children and their descendants)
    function findDescendants(productId) {
        products.forEach(p => {
            if (parseInt(p.parentid) === productId) {
                const childId = parseInt(p.id);
                descendants.add(childId);
                findDescendants(childId); // Recursively find all descendants
            }
        });
    }
    findDescendants(parseInt(currentProductId));

    // Find siblings (other children of the same parent) and their descendants
    if (current.parentid) {
        const parentId = parseInt(current.parentid);
        const siblings = products.filter(p => {
            const pParentId = parseInt(p.parentid);
            const pId = parseInt(p.id);
            return pParentId === parentId && pId !== parseInt(currentProductId);
        });

        // Add siblings and their descendants
        siblings.forEach(sibling => {
            const siblingId = parseInt(sibling.id);
            descendants.add(siblingId);
            findDescendants(siblingId); // Add all descendants of siblings (nephews/nieces and their descendants)
        });
    }

    // Include the current product, all its ancestors, and all its descendants (including siblings and their descendants)
    const includedIds = new Set([parseInt(currentProductId), ...ancestors, ...descendants]);

    // Filter products to include only the complete family tree
    return products.filter(p => {
        const id = parseInt(p.id);
        return includedIds.has(id);
    });
}

// Build hierarchy tree structure
function buildHierarchyTree(products, rootId) {
    const byId = new Map();
    const byParent = new Map();
    
    products.forEach(p => {
        byId.set(parseInt(p.id), p);
        const parentId = parseInt(p.parentid) || 0;
        if (!byParent.has(parentId)) byParent.set(parentId, []);
        byParent.get(parentId).push(p);
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

// Render product hierarchy table following Regulatory Theme pattern
function renderProductTable(hierarchyRows, currentId) {
    const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
        const name = node.primaryname ?? node.PrimaryName ?? node.primaryName ?? node.Name ?? node.name ?? 'Unnamed Product';
        const desc = node.description ?? node.Description ?? '';
        const isCurrent = String(node.id ?? node.ID) === String(currentId);
        const id = node.id ?? node.ID;
        const parentId = node.parentid ?? node.Parent_ID ?? node.parent_id ?? node.parentId ?? '';
        
        const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
        const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
        const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
        const linkClass = isCurrent ? 'product-link current-product-link' : 'product-link';
        const link = `<a class="${linkClass}" href="/view/product/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
        const refNumber = node.refnumber ? `<span class="product-ref">(${escapeHtml(node.refnumber)})</span>` : '';
        
        return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}">
            <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-box item-icon"></i><span class="product-name">${link}</span>${refNumber}${countBadge}</div></td>
            <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
        </tr>`;
    }).join('');

    return rowsHtml;
}

// Initialize product hierarchy interactions following Regulatory Theme pattern
function initProductInteractions(containerEl, hierarchyRows) {
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

// Function to load product hierarchy data
async function loadProductHierarchy(productId) {
    const container = document.querySelector('.relationships-hierarchy');
    if (!container) return;
    
    try {
        container.innerHTML = '<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</div>';
        
        // Fetch all products from hierarchy endpoint
        console.log('Fetching products from /api/product/hierarchy');
        const response = await fetch('/api/product/hierarchy');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const products = await response.json();
        
        console.log('Products loaded for hierarchy:', products);
        
        if (!Array.isArray(products) || products.length === 0) {
            container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No products found in database</div>';
            return;
        }

        // Build direct lineage tree (current + ancestors + descendants + siblings)
        const filteredProducts = buildDirectLineageTree(products, productId);
        
        if (filteredProducts.length === 0) {
            container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No related products found</div>';
            return;
        }

        // Build hierarchy tree
        const hierarchyRows = buildHierarchyTree(filteredProducts, 0);
        
        // Render table
        const tableHtml = `
            <div class="hierarchy-header">
                <div class="hierarchy-title">PRODUCT HIERARCHY</div>
                <div class="hierarchy-actions">
                    <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i></button>
                        </div>
                        </div>
            <div class="hierarchy-table-wrapper">
                <table class="hierarchy-table">
                    <thead>
                        <tr>
                            <th>Product</th>
                            <th>Description</th>
                </tr>
                    </thead>
                    <tbody>
                        ${renderProductTable(hierarchyRows, productId)}
                    </tbody>
                </table>
                                </div>
            <div class="table-footer">
                ${filteredProducts.length} record${filteredProducts.length !== 1 ? 's' : ''}
                                </div>
        `;
        
        container.innerHTML = tableHtml;
        
        // Initialize interactions
        initProductInteractions(container, hierarchyRows);
        
    } catch (error) {
        console.error('Failed to load product hierarchy:', error);
        container.innerHTML = `<div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">Failed to load hierarchy data: ${error.message || 'Unknown error'}</div>`;
    }
}

function setupEventListeners() {
    const id = parseId();
    if (!id) return;

    const saveBtn = document.getElementById('saveBtn');
    const saveCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');

    if (saveBtn) saveBtn.addEventListener('click', () => saveProduct(id, false));
    if (saveCloseBtn) saveCloseBtn.addEventListener('click', () => saveProduct(id, true));

    // Show editor button – advanced rich text editor
    const showEditorBtn = document.getElementById('showEditorBtn');
    if (showEditorBtn) {
        showEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('productDescription', showEditorBtn);
        });
    }

    if (closeBtn) closeBtn.addEventListener('click', async () => {
        // Release lock before canceling
        if (window.currentLockManager) {
            await window.currentLockManager.releaseLock();
        }
        preventUnloadWarning = true;
        document.body.classList.remove('dirty');
        window.location.href = `/view/product/product.html?id=${id}`;
    });

    // Tab switching
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const tabName = tab.dataset.tab;
            switchTab(tabName);
        });
    });

    // Form change detection
    const formElements = document.querySelectorAll('input, textarea, select');
    formElements.forEach(element => {
        element.addEventListener('change', markAsDirty);
        element.addEventListener('input', markAsDirty);
    });
    
    // Monitor stakeholders changes
    if (window.ProductStakeholderEdit) {
        // Override the original updateStakeholder function to mark form as dirty
        const originalUpdateStakeholder = window.ProductStakeholderEdit.updateStakeholder;
        if (originalUpdateStakeholder) {
            window.ProductStakeholderEdit.updateStakeholder = function(index, field, value) {
                const result = originalUpdateStakeholder.call(this, index, field, value);
                markAsDirty(); // Mark form as dirty when stakeholders change
                return result;
            };
        }
        
        // Override addNewRow functions to mark form as dirty
        const originalAddNewRow = window.ProductStakeholderEdit.addNewRow;
        if (originalAddNewRow) {
            window.ProductStakeholderEdit.addNewRow = function() {
                const result = originalAddNewRow.call(this);
                markAsDirty(); // Mark form as dirty when adding new stakeholder
                return result;
            };
        }
        
        const originalAddNewRowAfter = window.ProductStakeholderEdit.addNewRowAfter;
        if (originalAddNewRowAfter) {
            window.ProductStakeholderEdit.addNewRowAfter = function(index) {
                const result = originalAddNewRowAfter.call(this, index);
                markAsDirty(); // Mark form as dirty when adding new stakeholder
                return result;
            };
        }
        
        // Override deleteStakeholder function to mark form as dirty
        const originalDeleteStakeholder = window.ProductStakeholderEdit.deleteStakeholder;
        if (originalDeleteStakeholder) {
            window.ProductStakeholderEdit.deleteStakeholder = function(index) {
                const result = originalDeleteStakeholder.call(this, index);
                markAsDirty(); // Mark form as dirty when deleting stakeholder
                return result;
            };
        }
    }

    // Parent selection modal
    document.getElementById('selectParentBtn')?.addEventListener('click', selectParent);
    document.getElementById('clearParentBtn')?.addEventListener('click', clearParent);
    document.getElementById('closeParentModal')?.addEventListener('click', closeParentSelectionModal);
    document.getElementById('cancelParentSelection')?.addEventListener('click', closeParentSelectionModal);
    document.getElementById('parentSearchInput')?.addEventListener('input', function(e) {
        filterProducts(e.target.value);
    });
    document.getElementById('parentSelectionModal')?.addEventListener('click', function(e) {
        if (e.target === this) {
            closeParentSelectionModal();
        }
    });
}

// Global variables for dirty state
let isDirty = false;
let preventUnloadWarning = false;

window.removeEventListener('beforeunload', window.productEditBeforeUnload);

window.productEditBeforeUnload = function(e) {
    if (document.body.classList.contains('dirty') && !preventUnloadWarning) {
        e.preventDefault();
        e.returnValue = '';
    }
};

window.addEventListener('beforeunload', window.productEditBeforeUnload);

// Parent selection functionality
let currentProductId = null;
let allProducts = [];
let filteredProducts = [];

// Normalize product identifiers - use exact schema field names
function getProductId(product) {
    return product?.id ?? product?.ID ?? product?.Id ?? null;
}

function getProductParentId(product) {
    // Check both parentid and parent_id fields (like project does)
    return product?.parentid ?? product?.parent_id ?? product?.parentId ?? product?.ParentID ?? product?.Parent_ID ?? null;
}

function isDescendantOf(product, ancestorId, allProducts) {
    const projectId = getProductId(product);
    const parentId = getProductParentId(product);
    
    console.log(`  🔍 isDescendantOf: product ${projectId}, parent ${parentId}, ancestor ${ancestorId}`);
    
    if (parentId == null) {
        console.log(`  ❌ No parent - not descendant`);
        return false;
    }
    
    if (String(parentId) === String(ancestorId)) {
        console.log(`  ✅ Direct child of ancestor`);
        return true;
    }
    
    const parentProduct = allProducts.find(p => String(getProductId(p)) === String(parentId));
    if (!parentProduct) {
        console.log(`  ❌ Parent product not found`);
        return false;
    }
    
    console.log(`  🔄 Checking parent product ${getProductId(parentProduct)}`);
    const result = isDescendantOf(parentProduct, ancestorId, allProducts);
    console.log(`  📋 Result for product ${projectId}: ${result}`);
    return result;
}

async function selectParent() {
    try {
        currentProductId = parseId();
        console.log('Opening parent selection for product ID:', currentProductId);

        // Try multiple API endpoints to get complete product data with parent_id
        let allProducts = null;
        let apiSource = '';
        
        // First try: getProductParentOptions (server-side filtering)
        try {
            const parentOptions = await window.BUDG_API_SERVICE.getProductParentOptions(currentProductId);
            console.log('Parent options (server-filtered):', parentOptions);
            
            if (Array.isArray(parentOptions) && parentOptions.length > 0) {
                // Check for both parentid and parent_id fields (like project does)
                const hasParentId = parentOptions.some(p => p.hasOwnProperty('parentid') || p.hasOwnProperty('parent_id'));
                if (hasParentId) {
                    allProducts = parentOptions;
                    apiSource = 'getProductParentOptions';
                    console.log('✅ Using getProductParentOptions with parent data');
                } else {
                    console.log('Server response missing parent data - falling back to full product list');
                }
            }
        } catch (e) {
            console.warn('getProductParentOptions failed or not available, falling back to all products', e);
        }
        
        // Second try: UnisonSearch endpoint
        if (!allProducts) {
            try {
                allProducts = await window.BUDG_API_SERVICE.getUnisonSearchData('product');
                console.log('Using UnisonSearch product data for hierarchy checking');
                const hasParentId = allProducts.some(p => p.hasOwnProperty('parentid') || p.hasOwnProperty('parent_id'));
                if (hasParentId) {
                    apiSource = 'getUnisonSearchData';
                    console.log('✅ Using getUnisonSearchData with parent data');
                } else {
                    console.warn('getUnisonSearchData missing parent data');
                }
            } catch (e) {
                console.warn('getUnisonSearchData failed:', e);
            }
        }
        
        // Third try: getAllProducts endpoint
        if (!allProducts) {
            try {
                allProducts = await window.BUDG_API_SERVICE.getAllProducts();
                const hasParentId = allProducts.some(p => p.hasOwnProperty('parentid') || p.hasOwnProperty('parent_id'));
                if (hasParentId) {
                    apiSource = 'getAllProducts';
                    console.log('✅ Using getAllProducts with parent data');
                } else {
                    console.warn('getAllProducts missing parent data');
                }
            } catch (e) {
                console.warn('getAllProducts failed:', e);
            }
        }
        
        // Fourth try: Direct API call to get all products with parent data
        if (!allProducts) {
            try {
                const response = await fetch('/api/products');
                if (response.ok) {
                    allProducts = await response.json();
                    const hasParentId = allProducts.some(p => p.hasOwnProperty('parentid') || p.hasOwnProperty('parent_id'));
                    if (hasParentId) {
                        apiSource = 'direct API call';
                        console.log('✅ Using direct API call with parent data');
                    } else {
                        console.warn('Direct API call missing parent data');
                    }
                }
            } catch (e) {
                console.warn('Direct API call failed:', e);
            }
        }
        
        if (!allProducts) {
            throw new Error('Failed to load products from any API endpoint');
        }
        
        console.log(`Using API source: ${apiSource}`);
        
        console.log('=== RAW API DATA DEBUG ===');
        console.log('All products from API:', allProducts);
        allProducts.forEach((product, index) => {
            console.log(`--- Product ${index} ---`);
            console.log('Product ID:', getProductId(product));
            console.log('Product name:', product.primaryname || product.PrimaryName || product.name || product.Name);
            console.log('parent field:', getProductParentId(product));
            console.log('All object keys:', Object.keys(product));
        });
        
        // Check if we have parent data for hierarchy checking
        const hasParentIdData = allProducts.some(p => getProductParentId(p) !== null);
        console.log('Has parent data for hierarchy checking:', hasParentIdData);
        
        // Apply client-side safety filter to exclude current and descendants
        console.log('=== FILTERING PRODUCTS FOR PARENT SELECTION ===');
        console.log('Current product ID:', currentProductId);
        console.log('Total products loaded:', allProducts.length);
        
        if (!hasParentIdData) {
            console.warn('⚠️ WARNING: No parent data available - using basic filtering only');
            // Basic filtering without hierarchy checking
            filteredProducts = allProducts.filter(product => {
                const pid = getProductId(product);
                const productName = product.primaryname || product.PrimaryName || product.name || product.Name || 'Unnamed Product';
                
                console.log(`\n--- Checking product ${pid} (${productName}) ---`);
                console.log('Is current product?', String(pid) === String(currentProductId));
                
                if (String(pid) === String(currentProductId)) {
                    console.log('❌ EXCLUDED: Current product');
                    return false;
                } else {
                    console.log('✅ INCLUDED: Valid parent candidate (no parent data)');
                    return true;
                }
            });
        } else {
            // Full hierarchy checking
            filteredProducts = allProducts.filter(product => {
                const pid = getProductId(product);
                const parentId = getProductParentId(product);
                const productName = product.primaryname || product.PrimaryName || product.name || product.Name || 'Unnamed Product';
                
                console.log(`\n--- Checking product ${pid} (${productName}) ---`);
                console.log('Product parent:', parentId);
                console.log('Is current product?', String(pid) === String(currentProductId));
                
                if (String(pid) === String(currentProductId)) {
                    console.log('❌ EXCLUDED: Current product');
                    return false;
                }
                
                const isDescendant = isDescendantOf(product, currentProductId, allProducts);
                console.log('Is descendant?', isDescendant);
                
                if (isDescendant) {
                    console.log('❌ EXCLUDED: Descendant product');
                    return false;
                } else {
                    console.log('✅ INCLUDED: Valid parent candidate');
                    return true;
                }
            });
        }
        
        console.log('=== FILTERING RESULTS ===');
        console.log('Original products count:', allProducts.length);
        console.log('Filtered products count:', filteredProducts.length);
        console.log('Filtered products:', filteredProducts.map(p => ({
            id: getProductId(p),
            name: p.primaryname || p.PrimaryName || p.name || p.Name,
            parent: getProductParentId(p)
        })));
        
        // Check if any child/grandchild products are still included
        const problematicProducts = filteredProducts.filter(product => {
            const pid = getProductId(product);
            const parentId = getProductParentId(product);
            
            // Check if this product is a direct child of current product
            if (String(parentId) === String(currentProductId)) {
                return true;
            }
            
            // Check if this product is a grandchild (child of a child)
            const isGrandchild = allProducts.some(p => {
                const pParentId = getProductParentId(p);
                return String(pParentId) === String(currentProductId) && 
                       String(getProductId(p)) === String(parentId);
            });
            
            return isGrandchild;
        });
        
        if (problematicProducts.length > 0) {
            console.error('❌ PROBLEM FOUND: Child/Grandchild products still included!');
            console.error('Problematic products:', problematicProducts.map(p => ({
                id: getProductId(p),
                name: p.primaryname || p.PrimaryName || p.name || p.Name,
                parent: getProductParentId(p)
            })));
        } else {
            console.log('✅ SUCCESS: No child/grandchild products found in filtered list');
        }
        
        showParentSelectionModal();
    } catch (error) {
        console.error('Error loading products for parent selection:', error);
        alert('Failed to load products: ' + error.message);
    }
}

function showParentSelectionModal() {
    const modal = document.getElementById('parentSelectionModal');
    if (modal) {
        modal.style.display = 'flex';
        renderProducts(filteredProducts);
    }
}

function renderProducts(products) {
    const container = document.getElementById('productsList');
    if (!container) return;
    
    container.innerHTML = '';
    
    if (products.length === 0) {
        container.innerHTML = '<div class="no-products">No products available</div>';
        return;
    }
    
    products.forEach(product => {
        const productItem = document.createElement('div');
        productItem.className = 'product-item';
        
        // Get product name from various possible field names
        const productName = product.primaryname || product.PrimaryName || product.name || product.Name || 'Unnamed Product';
        
        const productNameDiv = document.createElement('div');
        productNameDiv.className = 'product-name';
        productNameDiv.textContent = productName;
        
        const productDescription = document.createElement('div');
        productDescription.className = 'product-description';
        productDescription.textContent = product.description || product.Description || '';
        
        productItem.appendChild(productNameDiv);
        productItem.appendChild(productDescription);
        
        productItem.addEventListener('click', () => {
            selectProductAsParent(product);
        });
        
        // Add hover effects
        productItem.addEventListener('mouseenter', () => {
            productItem.style.backgroundColor = '#f9fafb';
        });
        
        productItem.addEventListener('mouseleave', () => {
            productItem.style.backgroundColor = '';
        });
        
        container.appendChild(productItem);
    });
}

function selectProductAsParent(product) {
    console.log('Selected parent product:', product);
    const parentNameInput = document.getElementById('parentName');
    const productName = product.primaryname || product.PrimaryName || product.name || product.Name || 'Unnamed Product';
    const productId = getProductId(product);
    
    if (parentNameInput) {
        parentNameInput.value = productName;
        parentNameInput.dataset.parentId = productId;
    }
    
    closeParentSelectionModal();
    markAsDirty();
}

function closeParentSelectionModal() {
    const modal = document.getElementById('parentSelectionModal');
    if (modal) {
        modal.style.display = 'none';
    }
    const searchInput = document.getElementById('parentSearchInput');
    if (searchInput) {
        searchInput.value = '';
    }
}

function clearParent() {
    console.log('Clearing parent selection');
    
    // Clear parent name input
    const parentNameInput = document.getElementById('parentName');
    if (parentNameInput) {
        parentNameInput.value = '';
        parentNameInput.dataset.parentId = '';
        
        // Mark form as dirty since parent changed
        markAsDirty();
    }
    
    console.log('Parent cleared');
}

function filterProducts(searchTerm) {
    const filtered = filteredProducts.filter(product => {
        const name = (product.primaryname || product.PrimaryName || '').toLowerCase();
        const description = (product.description || product.Description || '').toLowerCase();
        const search = searchTerm.toLowerCase();
        
        return name.includes(search) || description.includes(search);
    });
    
    renderProducts(filtered);
}


async function initializePage() {
    const id = parseId();
    console.log('Initializing product edit page with ID:', id);
    if (!id) {
        console.error('No product ID found');
        return;
    }
    
    try {
        console.log('Fetching current user...');
        // Fetch current user first
        await fetchCurrentUser();
        
        console.log('Loading lookups and product data...');
        // Load lookups and product data in parallel
        await Promise.all([
            loadProductEditLookups(),
            loadProduct(id)
        ]);
        
        // Initialize segment field
        if (window.SegmentField) {
            try {
                segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Product',
                    fieldId: 'productSegment',
                    errorId: 'productSegmentError',
                    onChange: async (selectedSegmentId, previousSegmentId) => {
                        return await refreshProductParentForSelectedSegment(previousSegmentId);
                    }
                });
                if (pendingSegmentId != null) {
                    segmentField.setValue(pendingSegmentId);
                }
                console.log('Segment field initialized');
            } catch (error) {
                console.error('Error initializing segment field:', error);
            }
        }
        
        // Initialize custom fields
        if (window.CustomFields) {
            try {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Product',
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
        
        console.log('Handling tab parameter...');
        handleTabParameter();
        
        console.log('Product edit page initialized successfully');
    } catch (error) {
        console.error('Error initializing product edit page:', error);
    }
}

function handleTabParameter() {
    const urlParams = new URLSearchParams(window.location.search);
    const tabParam = urlParams.get('tab');
    
    if (tabParam) {
        console.log('Tab parameter found:', tabParam);
        
        // Check if the tab exists in the edit page
        const targetTab = document.querySelector(`[data-tab="${tabParam}"]`);
        if (targetTab) {
            console.log('Switching to tab:', tabParam);
            // Small delay to ensure everything is loaded
            setTimeout(() => {
                switchTab(tabParam);
            }, 100);
        } else {
            console.log('Tab not found in edit page:', tabParam);
        }
    }
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


