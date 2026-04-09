// Product Page JavaScript
document.addEventListener('DOMContentLoaded', function() {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');
    const parentPicker = document.getElementById('parentPicker');

    if (saveBtn) {
        saveBtn.addEventListener('click', function() {
            savePage(true);
        });
    }

    if (saveAndCloseBtn) {
        saveAndCloseBtn.addEventListener('click', function() {
            savePage(true);
        });
    }

    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            closePage();
        });
    }

    if (parentPicker) {
        parentPicker.addEventListener('click', function() {
            openParentPicker();
        });
    }

    // Initialize form dropdowns
    initProductFormDropdowns();
    
    // Initialize parent picker
    initParentPicker();

    // Advanced Rich Text Editor toggle
    const showDescriptionEditorBtn = document.getElementById('showDescriptionEditorBtn');
    if (showDescriptionEditorBtn) {
        showDescriptionEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('description', showDescriptionEditorBtn);
        });
    }
    
});


async function savePage(closeAfterSave) {
    // Sync advanced rich text editor content to textarea before saving
    if (typeof window.syncAdvancedRichTextToTextarea === 'function') {
        window.syncAdvancedRichTextToTextarea('description');
    }

    try {
        // Get user ID before saving
        const userId = getCurrentUserId();
        if (!userId) {
            console.error('❌ No user ID found, attempting to fetch current user...');
            await fetchCurrentUser();
            const retryUserId = getCurrentUserId();
            if (!retryUserId) {
                showError('Unable to identify current user. Please log in again.');
                return;
            }
        }
        
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(btn => {
            if (btn) {
                btn.disabled = true;
                btn.textContent = I18n.t('createPage.message.saving');
            }
        });

        const payload = collectProductFormData();
        
        // Debug: Log the payload to see if parent ID is included
        console.log('Product creation payload:', payload);
        console.log('Parent ID in payload:', payload.parent_id);
        console.log('Long name in payload:', payload.longname);

        // Validate custom fields
        if (window.customFieldsContext && window.customFieldsContext.validate) {
            if (!window.customFieldsContext.validate()) {
                buttons.forEach(btn => {
                    if (btn) {
                        btn.disabled = false;
                        btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close';
                    }
                });
                return;
            }
        }

        // Validate required fields
        if (!validateProductForm(payload)) {
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close';
                }
            });
            return;
        }

        // Uniqueness checks: Name and Ref_Number
        try {
            if (typeof window.BUDG_API_SERVICE?.getProductList === 'function') {
                const list = await window.BUDG_API_SERVICE.getProductList();
                const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                const refVal = String(payload.refnumber || '').trim().toLowerCase();
                
                if (refVal) {
                    const refClash = rows.some(r => String(r.refnumber || r.RefNumber || r.ref || '').trim().toLowerCase() === refVal);
                    if (refClash) {
                        const m = I18n.t('createPage.message.duplicateRefNumber', {facet: 'Products'});
                        if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                        document.getElementById('ref')?.focus();
                        buttons.forEach(btn => { if (btn) { btn.disabled = false; btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close'; } });
                        return;
                    }
                }
            }
        } catch (_) { /* fall back to server-side validation */ }

        // Call real API
        try {
            const svc = window.BUDG_API_SERVICE;
            if (!svc) {
                throw new Error('API service not available');
            }

            const response = await svc.post('/product', payload);

            if (response && response.success) {
                
                showSuccess(I18n.t('createPage.message.productSaved'));
                
                // Get the ID from the response
                const productId = response.data?.id || response.id;
                
                // Save custom fields
                if (window.customFieldsContext && window.customFieldsContext.saveValues && productId) {
                    try {
                        await window.customFieldsContext.saveValues(productId);
                        console.log('✅ Custom fields saved successfully');
                    } catch (cfError) {
                        console.error('Error saving custom fields:', cfError);
                    }
                }
                
                showSuccess(I18n.t('createPage.message.productSaved'));
                
                if (closeAfterSave) {
                    // Show message for 2 seconds then go to view page with ID
                    setTimeout(() => {
                        window.location.href = `view/product/product.html?id=${productId}`;
                    }, 2000);
                } else {
                    // For regular Save, just show the success message (already shown above)
                    // Reset form for new entry
                    setTimeout(() => {
                        document.getElementById('name').value = '';
                        document.getElementById('description').value = '';
                        document.getElementById('ref').value = '';
                        // Reset other fields as needed
                    }, 2000);
                }
            } else {
                const serverMsg = response?.error || response?.message;
                const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Products') : null;
                if (errInfo) {
                    showError(I18n.t(errInfo.key, errInfo.params));
                    if (errInfo.focus === 'name') document.getElementById('name')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('ref')?.focus();
                } else {
                    showError(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Products' }));
                }
            }
        } catch (error) {
            const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Products') : null;
            if (errInfo) {
                showError(I18n.t(errInfo.key, errInfo.params));
                if (errInfo.focus === 'name') document.getElementById('name')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('ref')?.focus();
            } else {
                showError(I18n.t('createPage.message.errorSaving', { error: serverMessage || I18n.t('createPage.message.unknownError') }));
            }
        } finally {
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close';
                }
            });
        }

        // Uncomment when API is ready
        /*
        window.BUDG_API_SERVICE.saveProduct(payload)
            .then(res => {
                alert('Product saved successfully');
                if (closeAfterSave) {
                    window.location.href = 'index.html';
                }
            })
            .catch(err => {
                const msg = err?.body?.message || err?.message || 'Save failed';
                alert('Error: ' + msg);
            })
            .finally(() => {
                buttons.forEach(btn => {
                    if (btn) {
                        btn.disabled = false;
                        btn.textContent = btn.id === 'saveBtn' ? I18n.t('button.save') : I18n.t('button.saveAndClose');
                    }
                });
            });
        */
    } catch (error) {
        showError('An error occurred while saving');
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(btn => {
            if (btn) {
                btn.disabled = false;
                btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close';
            }
        });
    }
}

function closePage() {
    if (confirm('Are you sure you want to close? Any unsaved changes will be lost.')) {
        window.location.href = 'index.html';
    }
}

function collectProductFormData() {
    // Helper function to convert empty strings to null for numeric fields
    const getNumericValue = (value) => {
        if (!value || value.trim() === '') {
            return null;
        }
        // Convert to integer to ensure it's a valid number
        const num = parseInt(value, 10);
        return isNaN(num) ? null : num;
    };

    const userId = getCurrentUserId();
    
    const data = {
        primaryname: document.getElementById('name')?.value || '',
        parentName: document.getElementById('parentName')?.value || '',
        parent_id: getNumericValue(document.getElementById('parentId')?.value),
        description: document.getElementById('description')?.value || '',
        refnumber: document.getElementById('ref')?.value || '',
        longname: document.getElementById('longName')?.value || '',
        status: getNumericValue(document.getElementById('budgStatus')?.value),
        is_public: getNumericValue(document.getElementById('budgViewing')?.value),
        lifecycle_status: getNumericValue(document.getElementById('lifecycle')?.value),
        segmentId: window.segmentField ? window.segmentField.getValue() : 1,
        createdById: userId,      // User who created the product
        lastUpdateUserId: userId  // Initially same as creator
    };

    return data;
}

function validateProductForm(data) {
    let isValid = true;

    // Clear previous errors
    document.querySelectorAll('.field-error').forEach(error => {
        error.style.display = 'none';
        error.textContent = '';
    });

    // Validate Name
    if (!data.primaryname.trim()) {
        showFieldError('nameError', 'Name is required');
        isValid = false;
    }

    // Validate Description
    if (!data.description.trim()) {
        showFieldError('descriptionError', 'Description is required');
        isValid = false;
    }

    // Validate BUDG Status
    if (!data.status) {
        showFieldError('budgStatusError', 'BUDG Status is required');
        isValid = false;
    }

    // Validate BUDG Viewing
    if (!data.is_public) {
        showFieldError('budgViewingError', 'BUDG Viewing is required');
        isValid = false;
    }

    // Validate Lifecycle
    if (!data.lifecycle_status) {
        showFieldError('lifecycleError', 'Lifecycle is required');
        isValid = false;
    }
    

    return isValid;
}

function showFieldError(errorId, message) {
    const errorElement = document.getElementById(errorId);
    if (errorElement) {
        errorElement.textContent = message;
        errorElement.style.display = 'block';
    }
}

function openParentPicker() {
    // This would open a picker dialog to select parent product
    const m = (window.I18n && window.I18n.t('createPage.message.parentPickerNotImplemented')) || 'Parent picker functionality would be implemented here';
    if (typeof window.showNotification === 'function') { window.showNotification(m, 'info'); } else { alert(m); }
}

function initProductFormDropdowns() {
    // Initialize any custom dropdown behavior if needed
    // For now, using standard HTML select elements
}

// Helper function to validate product name format
function validateProductName(name) {
    // Basic validation - can be extended with business rules
    if (!name || name.trim().length < 2) {
        return false;
    }

    // Check for invalid characters (example business rule)
    const invalidChars = /[<>\"'&]/;
    if (invalidChars.test(name)) {
        return false;
    }

    return true;
}

// Helper function to format product reference
function formatProductReference(ref) {
    if (!ref) return '';

    // Convert to uppercase and remove spaces for consistency
    return ref.toUpperCase().replace(/\s+/g, '');
}

// Helper function to get lifecycle color for display
function getLifecycleColor(lifecycle) {
    switch (lifecycle?.toLowerCase()) {
        case 'in production':
            return '#16a34a'; // Green
        case 'in development':
            return '#d97706'; // Amber
        case 'planning':
            return '#2563eb'; // Blue
        case 'testing':
        case 'beta':
            return '#7c3aed'; // Purple
        case 'end of life':
        case 'sunset':
            return '#dc2626'; // Red
        default:
            return '#6b7280'; // Gray
    }
}

// Helper function to get status color for display
function getStatusColor(status) {
    switch (status?.toLowerCase()) {
        case 'active':
            return '#16a34a'; // Green
        case 'inactive':
            return '#6b7280'; // Gray
        case 'discontinued':
        case 'retired':
            return '#dc2626'; // Red
        case 'under development':
            return '#d97706'; // Amber
        default:
            return '#6b7280'; // Gray
    }
}

// Initialize product form dropdowns
async function initProductFormDropdowns() {
    try {
        await loadProductDropdowns();
    } catch (error) {
        showDatabaseConnectionError();
    }
}

// Load product dropdowns from API
async function loadProductDropdowns() {
    const svc = window.BUDG_API_SERVICE;
    if (!svc) {
        throw new Error('API service not available');
    }

    try {
        const [
            statusList,
            viewingList,
            productLifecycles
        ] = await Promise.all([
            svc.getStatusList(),
            svc.getViewingList(),
            svc.getProductLifecycleList()
        ]);

        // Fill dropdowns with database data using correct column names
        fillSelect('budgStatus', statusList, 'primaryname');
        fillSelect('budgViewing', viewingList, 'name'); // viewing table uses 'name' column
        fillSelect('lifecycle', productLifecycles, 'primaryname');
    } catch (error) {
        throw error;
    }
}

// Fill select dropdown with data
function fillSelect(selectId, list, labelKey) {
    const el = document.getElementById(selectId);
    if (!el) {
        return;
    }

    // Clear existing options
    el.innerHTML = '';

    if (Array.isArray(list) && list.length > 0) {
        // Add options from data
        list.forEach((item, index) => {
            const option = document.createElement('option');
            option.value = item.id || item.ID;
            option.textContent = item[labelKey] || item.name;
            el.appendChild(option);
            
            // Select the first option by default for mandatory fields
            if (index === 0 && (selectId === 'budgStatus' || selectId === 'budgViewing' || selectId === 'lifecycle')) {
                option.selected = true;
            }
        });
    } else {
        // Add placeholder if no data
        const placeholderOption = document.createElement('option');
        placeholderOption.value = "";
        placeholderOption.textContent = "Please select";
        el.appendChild(placeholderOption);
    }
}

// Load fallback dropdown values if API fails
function showDatabaseConnectionError() {
    // Show error message to user
    const errorMessage = (window.I18n && window.I18n.t('createPage.message.databaseConnectionError')) || 'Error: Database connection failed. Please check your connection and try again.';
    if (typeof window.showNotification === 'function') { window.showNotification(errorMessage, 'error'); } else { alert(errorMessage); }
    
    // Disable form elements
    const formElements = document.querySelectorAll('select, input, button');
    formElements.forEach(element => {
        element.disabled = true;
    });
    
    // Show error in dropdowns
    const dropdowns = ['budgStatus', 'budgViewing', 'lifecycle'];
    dropdowns.forEach(dropdownId => {
        const select = document.getElementById(dropdownId);
        if (select) {
            // Clear existing options
            select.innerHTML = '';
            // Add error option
            const errorOption = document.createElement('option');
            errorOption.value = '';
            errorOption.textContent = 'Database Connection Error';
            errorOption.disabled = true;
            select.appendChild(errorOption);
        }
    });
}

// Parent Picker Functions
let parentProducts = [];
let filteredParentProducts = [];

function initParentPicker() {
    const parentNameInput = document.getElementById('parentName');
    const parentDropdownToggle = document.getElementById('parentDropdownToggle');
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentSearchInput = document.getElementById('parentSearchInput');
    const parentDropdownList = document.getElementById('parentDropdownList');
    const clearParentBtn = document.getElementById('clearParentBtn');

    if (!parentNameInput || !parentDropdownToggle || !parentDropdownMenu || !parentSearchInput || !parentDropdownList) {
        return;
    }

    // Toggle dropdown
    parentDropdownToggle.addEventListener('click', function(e) {
        e.stopPropagation();
        toggleParentDropdown();
    });

    parentNameInput.addEventListener('click', function() {
        toggleParentDropdown();
    });

    // Search functionality
    parentSearchInput.addEventListener('input', function() {
        filterParentProducts(this.value);
    });

    // Clear parent functionality
    if (clearParentBtn) {
        clearParentBtn.addEventListener('click', function() {
            clearParent();
        });
    }

    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.searchable-dropdown-container')) {
            closeParentDropdown();
        }
    });

    // Load parent products
    loadParentProducts();
}

function toggleParentDropdown() {
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentDropdown = document.querySelector('.searchable-dropdown');

    if (parentDropdownMenu.style.display === 'none' || parentDropdownMenu.style.display === '') {
        openParentDropdown();
    } else {
        closeParentDropdown();
    }
}

function openParentDropdown() {
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentDropdown = document.querySelector('.searchable-dropdown');
    const parentSearchInput = document.getElementById('parentSearchInput');

    parentDropdownMenu.style.display = 'block';
    parentDropdown.classList.add('open');
    parentSearchInput.focus();
    parentSearchInput.value = '';
    filterParentProducts('');
}

function closeParentDropdown() {
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentDropdown = document.querySelector('.searchable-dropdown');

    parentDropdownMenu.style.display = 'none';
    parentDropdown.classList.remove('open');
}

function openParentPicker() {
    // Legacy function for compatibility
    openParentDropdown();
}

async function loadParentProducts() {
    try {
        const svc = window.BUDG_API_SERVICE;
        if (!svc) {
            return;
        }

        const products = await svc.getProductList();

        if (products && Array.isArray(products)) {
            parentProducts = products;
            filteredParentProducts = [...products];
            renderParentProducts();
        }
    } catch (error) {
        // Fallback to empty array
        parentProducts = [];
        filteredParentProducts = [];
    }
}

function filterParentProducts(searchTerm) {
    const term = searchTerm.toLowerCase().trim();

    if (term === '') {
        filteredParentProducts = [...parentProducts];
    } else {
        filteredParentProducts = parentProducts.filter(product =>
            product.primaryname?.toLowerCase().includes(term) ||
            product.description?.toLowerCase().includes(term) ||
            product.refnumber?.toLowerCase().includes(term)
        );
    }

    renderParentProducts();
}

function renderParentProducts() {
    const parentDropdownList = document.getElementById('parentDropdownList');
    if (!parentDropdownList) return;

    parentDropdownList.innerHTML = '';

    // Add "No Parent" option
    const noParentItem = document.createElement('div');
    noParentItem.className = 'searchable-dropdown-item';
    noParentItem.innerHTML = `
        <div class="searchable-dropdown-item-name"><i class="fas fa-times"></i> No Parent</div>
        <div class="searchable-dropdown-item-description">Clear parent selection</div>
    `;
    noParentItem.addEventListener('click', function() {
        clearParent();
    });
    parentDropdownList.appendChild(noParentItem);

    if (filteredParentProducts.length === 0) {
        const noProductsItem = document.createElement('div');
        noProductsItem.className = 'searchable-dropdown-item';
        noProductsItem.innerHTML = '<div class="searchable-dropdown-item-name">No products found</div>';
        parentDropdownList.appendChild(noProductsItem);
        return;
    }

    filteredParentProducts.forEach(product => {
        const item = document.createElement('div');
        item.className = 'searchable-dropdown-item';
        item.innerHTML = `
            <div class="searchable-dropdown-item-name">${product.primaryname || 'Unnamed Product'}</div>
            <div class="searchable-dropdown-item-description">${product.description || product.refnumber || ''}</div>
        `;

        item.addEventListener('click', function() {
            selectParentProduct(product);
        });

        parentDropdownList.appendChild(item);
    });
}

function selectParentProduct(product) {
    const parentNameInput = document.getElementById('parentName');
    const parentIdInput = document.getElementById('parentId');

    console.log('Selecting parent product:', product);
    console.log('Parent name input:', parentNameInput);
    console.log('Parent ID input:', parentIdInput);

    if (parentNameInput && parentIdInput) {
        parentNameInput.value = product.primaryname;
        parentIdInput.value = product.id;
        console.log('Set parent name to:', product.primaryname);
        console.log('Set parent ID to:', product.id);
    }

    closeParentDropdown();
}

function clearParent() {
    const parentNameInput = document.getElementById('parentName');
    const parentIdInput = document.getElementById('parentId');

    console.log('Clearing parent selection');
    console.log('Parent name input:', parentNameInput);
    console.log('Parent ID input:', parentIdInput);

    if (parentNameInput && parentIdInput) {
        parentNameInput.value = '';
        parentIdInput.value = '';
        console.log('Cleared parent name and ID');
    }

    closeParentDropdown();
}

// Success and Error message functions
function showSuccess(message) {
    // Create a temporary success message
    const successDiv = document.createElement('div');
    successDiv.className = 'success-message';
    successDiv.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background-color: #248567;
        color: white;
        padding: 1rem;
        border-radius: 0.5rem;
        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
        z-index: 1000;
        font-size: 0.875rem;
    `;
    successDiv.textContent = message;
    
    document.body.appendChild(successDiv);
    
    setTimeout(() => {
        successDiv.remove();
    }, 3000);
}

function showError(message) {
    // Create a temporary error message
    const errorDiv = document.createElement('div');
    errorDiv.className = 'error-message';
    errorDiv.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background-color: #ef4444;
        color: white;
        padding: 1rem;
        border-radius: 0.5rem;
        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
        z-index: 1000;
        font-size: 0.875rem;
    `;
    errorDiv.textContent = message;
    
    document.body.appendChild(errorDiv);
    
    setTimeout(() => {
        errorDiv.remove();
    }, 5000);
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

// Initialize user on page load
document.addEventListener('DOMContentLoaded', async function() {
    console.log('🚀 Initializing product page - fetching current user...');
    await fetchCurrentUser();
    
    // Initialize segment field
    try {
        if (window.SegmentField) {
            window.segmentField = await SegmentField.init('segmentFieldContainer', {
                label: 'Segment',
                required: true,
                defaultValue: 1,
                sectionTitle: 'SEGMENTATION',
                objectType: 'Product',
                fieldId: 'productSegment',
                errorId: 'productSegmentError',
                onChange: async () => {
                    const parentNameInput = document.getElementById('parentName');
                    const parentIdInput = document.getElementById('parentId');
                    const previousParentId = parentIdInput?.value ? parseInt(parentIdInput.value, 10) : null;
                    if (typeof loadParentProducts === 'function') {
                        await loadParentProducts();
                    }
                    if (previousParentId && Number.isInteger(previousParentId)) {
                        const stillAllowed = Array.isArray(parentProducts) &&
                            parentProducts.some(p => parseInt(p.id || p.ID, 10) === previousParentId);
                        if (!stillAllowed) {
                            if (typeof showError === 'function') {
                                showError('This parent is not valid for the selected segment. Please remove the parent first.');
                            }
                            return false;
                        }
                    }
                }
            });
            console.log('Segment field initialized');
        }
    } catch (error) {
        console.error('Error initializing segment field:', error);
    }
    
    // Initialize custom fields
    if (window.CustomFields) {
        try {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'Product',
                containerId: 'customFieldsContainer',
                mode: 'create',
                objectId: null
            });
            console.log('Custom fields initialized:', window.customFieldsContext);
        } catch (error) {
            console.error('Error initializing custom fields:', error);
        }
    }
});

// Export functions for potential external use
window.PRODUCT_PAGE = {
    savePage,
    closePage,
    collectProductFormData,
    validateProductForm,
    validateProductName,
    formatProductReference,
    getLifecycleColor,
    getStatusColor,
    showSuccess,
    showError,
    getCurrentUserId,
    fetchCurrentUser
};

