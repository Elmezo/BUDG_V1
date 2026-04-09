// Organization Unit Page JavaScript - Version 2.0
document.addEventListener('DOMContentLoaded', function() {
    // Get references to all DOM elements
    const parentInput = document.getElementById('parentInput');
    const parentEditBtn = document.getElementById('parentEditBtn');
    const parentModal = document.getElementById('parentModal');
    const modalClose = document.getElementById('modalClose');
    const modalCloseBtn = document.getElementById('modalCloseBtn');
    const searchInput = document.getElementById('searchInput');
    const searchClearBtn = document.getElementById('searchClearBtn');
    const tableBody = document.getElementById('tableBody');
    const currentSelection = document.getElementById('currentSelection');

    // Form buttons
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const cancelBtn = document.getElementById('cancelBtn');

    // Form elements
    const orgUnitForm = document.getElementById('orgUnitForm');
    const referenceInput = document.getElementById('referenceInput');
    const nameInput = document.getElementById('nameInput');
    const descriptionInput = document.getElementById('descriptionInput');
    const statusSelect = document.getElementById('statusSelect');

    // Initialize with delay to ensure API service is loaded
    setTimeout(() => {
        if (!window.BUDG_API_SERVICE) {
            setTimeout(() => {
                initPage();
            }, 300);
        } else {
            initPage();
        }
    }, 200);

    // Validate that all required elements exist
    console.log('Checking required elements:');
    console.log('parentInput:', parentInput);
    console.log('parentEditBtn:', parentEditBtn);
    console.log('parentModal:', parentModal);
    console.log('modalClose:', modalClose);
    console.log('modalCloseBtn:', modalCloseBtn);
    console.log('searchInput:', searchInput);
    console.log('tableBody:', tableBody);
    console.log('currentSelection:', currentSelection);
    
    if (!parentInput || !parentEditBtn || !parentModal || !modalClose || !modalCloseBtn || !searchInput || !tableBody || !currentSelection) {
        console.error('Required elements not found');
        return;
    }

    // Check if API service is available
    if (!window.BUDG_API_SERVICE) {
        console.error('BUDG_API_SERVICE not found. Make sure api-service.js is loaded.');
        return;
    }

    // Data storage variables
    let allData = [];
    let selectedItem = null;

    // Fetch all organization units from API
    async function fetchOrgUnits() {
        try {
            console.log('fetchOrgUnits: Starting to fetch org units...');
            showLoadingState();

            // Call API to get organization units
            const response = await fetch('/api/org-units');
            console.log('fetchOrgUnits: Response status:', response.status);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            console.log('fetchOrgUnits: Received data:', data);
            allData = data;
            populateTable(data);
            hideLoadingState();
            console.log('fetchOrgUnits: Modal should be visible now');
        } catch (error) {
            console.error('Error fetching org units:', error);
            showErrorState('Failed to fetch organization units');
            hideLoadingState();
        }
    }

    // Search organization units based on input
    async function searchOrgUnits(searchTerm) {
        try {
            // If search term is empty, show all data
            if (!searchTerm.trim()) {
                populateTable(allData);
                return;
            }

            showLoadingState();

            // Add visual feedback that search is active
            if (searchInput) {
                searchInput.classList.add('searching');
            }

            // Call search API directly
            const response = await fetch(`/api/org-units/search?q=${encodeURIComponent(searchTerm)}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();

            // Validate and display search results
            if (data && Array.isArray(data)) {
                populateTable(data);
            } else {
                console.warn('Search returned non-array data:', data);
                populateTable([]);
            }

            hideLoadingState();

            // Remove search active state
            if (searchInput) {
                searchInput.classList.remove('searching');
            }
        } catch (error) {
            console.error('Error searching org units:', error);
            showSearchError('Search failed: ' + error.message);
            hideLoadingState();

            // Remove search active state on error
            if (searchInput) {
                searchInput.classList.remove('searching');
            }
        }
    }

    // Populate table with data
    function populateTable(data) {
        console.log('populateTable: Received data:', data);
        console.log('populateTable: Data type:', typeof data);
        console.log('populateTable: Data length:', data ? data.length : 'null/undefined');
        
        if (!tableBody) {
            console.error('Table body element not found');
            return;
        }

        // Clear existing table content
        tableBody.innerHTML = '';

        // Remove any existing count row
        const existingCountRow = tableBody.querySelector('.search-results-count');
        if (existingCountRow) {
            existingCountRow.remove();
        }

        // Handle empty data case
        if (!data || data.length === 0) {
            console.log('populateTable: No data to display');
            const noDataRow = tableBody.insertRow();
            noDataRow.className = 'no-search-results';
            noDataRow.innerHTML = `
                <td colspan="2">
                    <i class="fas fa-search"></i>
                    <div>No results found</div>
                    <div class="search-suggestion">Try different keywords or check spelling</div>
                </td>
            `;
            return;
        }

        // Add count row if there are many results
        const totalCount = data.length;
        if (totalCount > 10) {
            const countRow = tableBody.insertRow();
            countRow.className = 'data-count-row';
            countRow.innerHTML = `
                <td colspan="2">
                    Showing ${totalCount} organization units
                    <small>Scroll to see more</small>
                </td>
            `;
        }

        // Create a row for each item
        data.forEach((item, index) => {
            console.log('Processing item:', item);
            const row = tableBody.insertRow();
            row.dataset.id = item.id;
            row.dataset.name = item.name || item.Name;
            row.dataset.description = item.description || item.Description;
            row.dataset.statusId = item.status_id || item.statusId;

            // Prepare display text - try different field names
            const fullName = item.name || item.Name || item.primaryname || item.id || 'Unnamed';
            const fullDescription = item.description || item.Description || item.desc || 'No description';

            // Highlight search terms if applicable
            const highlightedName = searchInput && searchInput.value.trim()
                ? highlightSearchTerm(fullName, searchInput.value.trim())
                : fullName;

            const highlightedDescription = searchInput && searchInput.value.trim()
                ? highlightSearchTerm(fullDescription, searchInput.value.trim())
                : fullDescription;

            // Set row content
            row.innerHTML = `
                <td title="${fullName}" style="cursor: help;">
                    <i class="fas fa-home item-icon"></i>${highlightedName}
                </td>
                <td title="${fullDescription}" style="cursor: help;">
                    ${highlightedDescription}
                </td>
            `;

            // Mark deleted items
            if (item.status_id === 6) {
                row.classList.add('deleted-status');
                row.setAttribute('title', 'delete');
            }

            // Add selection handlers
            row.addEventListener('click', () => selectRow(row, item));
            row.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    selectRow(row, item);
                }
            });
        });

        // Add scroll indicator for large datasets
        if (totalCount > 20) {
            const scrollRow = tableBody.insertRow();
            scrollRow.className = 'scroll-info-row';
            scrollRow.innerHTML = `
                <td colspan="2">
                    <i class="fas fa-mouse-pointer"></i>
                    Scroll to see more items
                </td>
            `;
        }
    }

    // Highlight search terms in text
    function highlightSearchTerm(text, searchTerm) {
        if (!searchTerm) return text;
        const regex = new RegExp(`(${searchTerm})`, 'gi');
        return text.replace(regex, '<mark>$1</mark>');
    }

    // Handle row selection
    function selectRow(row, item) {
        // Remove previous selection
        if (selectedItem) {
            const prevSelected = tableBody.querySelector('.selected');
            if (prevSelected) {
                prevSelected.classList.remove('selected');
            }
        }

        // Add selection to current row
        row.classList.add('selected');
        selectedItem = item;

        // Update UI with selection
        if (currentSelection) {
            currentSelection.textContent = item.name || item.id;
        }

        if (parentInput) {
            parentInput.value = item.name || item.id;
        }

        // Close modal after selection
        if (parentModal) {
            parentModal.classList.remove('active');
        }

        // Reset selection state
        selectedItem = null;
    }

    // Show loading state in table
    function showLoadingState() {
        if (tableBody) {
            tableBody.innerHTML = `
                <tr class="loading-row">
                    <td colspan="2">
                        <i class="fas fa-spinner fa-spin"></i>
                        Loading organization units...
                    </td>
                </tr>
            `;
        }
    }

    // Remove loading state from table
    function hideLoadingState() {
        if (tableBody && tableBody.querySelector('.loading-row')) {
            tableBody.querySelector('.loading-row').remove();
        }
    }

    // Show error state in table
    function showErrorState(message) {
        if (tableBody) {
            tableBody.innerHTML = `
                <tr class="error-row">
                    <td colspan="2">
                        <i class="fas fa-exclamation-triangle"></i>
                        ${message}
                    </td>
                </tr>
            `;
        }
    }

    // Show search error state in table
    function showSearchError(message) {
        if (tableBody) {
            tableBody.innerHTML = `
                <tr class="search-error">
                    <td colspan="2">
                        <i class="fas fa-exclamation-triangle"></i>
                        ${message}
                    </td>
                </tr>
            `;
        }
    }

    // Handle parent edit button click
    parentEditBtn.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        try {
            console.log('Parent edit button clicked');
            console.log('Parent modal element:', parentModal);
            // Show modal
            if (parentModal) {
                console.log('Adding active class to modal');
                parentModal.classList.add('active');
                console.log('Modal classes after adding active:', parentModal.className);
                console.log('Modal computed style display:', window.getComputedStyle(parentModal).display);
                console.log('Modal computed style position:', window.getComputedStyle(parentModal).position);
                console.log('Modal computed style z-index:', window.getComputedStyle(parentModal).zIndex);
            } else {
                console.error('Parent modal element not found!');
            }

            // Prepare search input
            if (searchInput) {
                searchInput.focus();
                searchInput.placeholder = 'Search by name or description...';
                searchInput.value = ''; // Clear previous search
                searchInput.classList.remove('searching'); // Remove any search state
            }

            // Hide clear button initially
            if (searchClearBtn) {
                searchClearBtn.classList.remove('show');
            }

            // Update current selection display
            if (currentSelection && parentInput) {
                const currentValue = parentInput.value || 'None';
                currentSelection.textContent = currentValue;
            }

            // Load organization units
            console.log('Calling fetchOrgUnits...');
            fetchOrgUnits();
        } catch (error) {
            console.error('Error opening modal:', error);
        }
    });

    // Close modal handlers
    modalClose.addEventListener('click', function() {
        if (parentModal) {
            parentModal.classList.remove('active');
        }
    });

    modalCloseBtn.addEventListener('click', function() {
        if (parentModal) {
            parentModal.classList.remove('active');
        }
    });

    // Close modal when clicking outside
    parentModal.addEventListener('click', function(e) {
        if (e.target === parentModal) {
            parentModal.classList.remove('active');
        }
    });

    // Search input handler with debouncing
    searchInput.addEventListener('input', debounce(function() {
        const searchTerm = this.value.trim();

        // Show/hide clear button based on input
        if (searchClearBtn) {
            if (searchTerm.length > 0) {
                searchClearBtn.classList.add('show');
            } else {
                searchClearBtn.classList.remove('show');
            }
        }

        // Handle empty search
        if (searchTerm.length === 0) {
            populateTable(allData);
            return;
        }

        // Minimum search length check
        if (searchTerm.length < 2) {
            populateTable(allData);
            return;
        }

        // Perform search
        searchOrgUnits(searchTerm);
    }, 300));

    // Handle Enter key in search
    searchInput.addEventListener('keyup', function(e) {
        if (e.key === 'Enter') {
            const searchTerm = this.value.trim();
            if (searchTerm.length >= 2) {
                searchOrgUnits(searchTerm);
            }
        }
    });

    // Clear search button functionality
    if (searchClearBtn) {
        searchClearBtn.addEventListener('click', function() {
            if (searchInput) {
                searchInput.value = '';
                searchInput.focus();
                populateTable(allData);
                this.classList.remove('show');
            }
        });
    }

    // Handle Escape key in search
    searchInput.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            this.value = '';
            populateTable(allData);
            this.blur();
        }
    });

    // Debounce function to limit API calls
    function debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func.apply(this, args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }

    // Handle Escape key to close modal
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && parentModal && parentModal.classList.contains('active')) {
            parentModal.classList.remove('active');
        }
    });

    // Form button event listeners
    if (saveBtn) {
        saveBtn.addEventListener('click', function() {
            saveOrgUnit(true); // true = redirect to view/edit page after save
        });
    }

    if (saveAndCloseBtn) {
        saveAndCloseBtn.addEventListener('click', function() {
            saveOrgUnit(true); // true = close after save
        });
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', function() {
            cancelForm();
        });
    }

    // Form submission handler
    if (orgUnitForm) {
        orgUnitForm.addEventListener('submit', function(e) {
            e.preventDefault();
            saveOrgUnit(false);
        });
    }

    const ORG_UNIT_FACET = 'Organization Units';

    // Save organization unit to API
    async function saveOrgUnit(closeAfterSave = false) {
        // Sync advanced rich text editor content to textarea before saving
        if (typeof window.syncAdvancedRichTextToTextarea === 'function') {
            window.syncAdvancedRichTextToTextarea('descriptionInput');
        }

        try {
            // Validate required fields
            if (!nameInput || !nameInput.value.trim()) {
                const m = I18n.t('createPage.message.nameRequired'); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                nameInput?.focus();
                return;
            }

            if (!referenceInput || !referenceInput.value.trim()) {
                const m = I18n.t('createPage.message.required'); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                referenceInput?.focus();
                return;
            }

            // Validate status selection
            if (!statusSelect || !statusSelect.value) {
                const m = I18n.t('createPage.message.statusRequired'); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                statusSelect?.focus();
                return;
            }
            
            // Validate custom fields
            if (window.customFieldsContext && window.customFieldsContext.validate) {
                const customFieldsValid = window.customFieldsContext.validate();
                if (!customFieldsValid) {
                    return;
                }
            }

            // Client-side uniqueness checks (Name, Reference)
            try {
                if (typeof window.BUDG_API_SERVICE?.getOrgUnits === 'function') {
                    const list = await window.BUDG_API_SERVICE.getOrgUnits();
                    const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                    const refVal = (referenceInput.value || '').trim().toLowerCase();
                    const refClash = rows.some(r => String(r.reference || r.Reference || '').trim().toLowerCase() === refVal);
                    if (refClash) {
                        const m = I18n.t('createPage.message.duplicateRefNumber', {facet: 'Organization Units'}); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                        referenceInput.focus();
                        return;
                    }
                }
            } catch (_) { /* fall back to server-side validation */ }

            // Collect form data
            const formData = {
                reference: referenceInput?.value?.trim() || '',
                name: nameInput.value.trim(),
                description: descriptionInput?.value?.trim() || '',
                parent_name: parentInput?.value?.trim() || '',
                status_id: parseInt(statusSelect.value) || 1
            };

            // Show loading state on buttons
            const buttons = [saveBtn, saveAndCloseBtn, cancelBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = true;
                    btn.textContent = I18n.t('createPage.message.saving');
                }
            });

            // Call API to save organization unit
            const response = await window.BUDG_API_SERVICE.createOrgUnit(formData);

            // Handle API response
            if (response && (response.success || response.id)) {
                
                const m = I18n.t('createPage.message.orgUnitSaved'); if (typeof window.showNotification === 'function') { window.showNotification(m, 'success'); } else { alert(m); }
                
                // Get the ID from the response
                const id = response?.id || response?.data?.id || response?.orgUnitId || response?.insertId || response?.createdId;
                
                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues && id != null) {
                    try {
                        await window.customFieldsContext.saveValues(id);
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }

                if (closeAfterSave) {
                    if (id != null) {
                        window.location.href = `/view/org-unit/${encodeURIComponent(id)}`;
                    } else {
                        window.location.href = 'index.html';
                    }
                } else {
                    // Clear form for next entry
                    clearForm();
                }
            } else {
                const serverMsg = response?.error || response?.message;
                const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, ORG_UNIT_FACET) : null;
                if (errInfo) {
                    const m = I18n.t(errInfo.key, errInfo.params); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                    if (errInfo.focus === 'name') nameInput?.focus(); else if (errInfo.focus === 'reference') referenceInput?.focus();
                } else {
                    const m = I18n.t('createPage.message.failedToSaveWithHint', { facet: ORG_UNIT_FACET }); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                }
            }

        } catch (error) {
            console.error('Error saving org unit:', error);

            const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, ORG_UNIT_FACET) : null;
            if (errInfo) {
                const m = I18n.t(errInfo.key, errInfo.params); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                if (errInfo.focus === 'name') nameInput?.focus(); else if (errInfo.focus === 'reference') referenceInput?.focus();
                return;
            }

            // Generate user-friendly error message for other errors
            let errorMessage = I18n.t('createPage.message.unknownError');
            if (error.message) {
                errorMessage = error.message;
            } else if (error.status) {
                errorMessage = I18n.t('createPage.message.serverError', {status: error.status});
            } else if (error.name === 'TypeError') {
                errorMessage = I18n.t('createPage.message.networkError');
            }

            const m = I18n.t('createPage.message.errorSaving', {error: errorMessage}); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
        } finally {
            // Reset button states
            const buttons = [saveBtn, saveAndCloseBtn, cancelBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    if (btn === saveBtn) btn.textContent = I18n.t('button.save');
                    if (btn === saveAndCloseBtn) btn.textContent = I18n.t('button.saveAndClose');
                    if (btn === cancelBtn) btn.textContent = I18n.t('button.close');
                }
            });
        }
    }

    // Check if form has any data entered
    function checkIfFormHasData() {
        return (referenceInput && referenceInput.value.trim()) ||
            (nameInput && nameInput.value.trim()) ||
            (descriptionInput && descriptionInput.value.trim()) ||
            (parentInput && parentInput.value.trim()) ||
            (statusSelect && statusSelect.value);
    }

    // Handle form cancellation
    function cancelForm() {
        // Check if form has unsaved changes
        const hasData = checkIfFormHasData();
        if (hasData) {
            const confirmClose = confirm(I18n.t('createPage.message.unsavedChanges'));
            if (confirmClose) {
                window.location.href = './index.html';
            }
        } else {
            // No data, close immediately
            window.location.href = './index.html';
        }
    }

    // Clear form inputs
    function clearForm() {
        if (referenceInput) referenceInput.value = '';
        if (nameInput) nameInput.value = '';
        if (descriptionInput) descriptionInput.value = '';
        if (parentInput) parentInput.value = '';
        if (statusSelect) statusSelect.value = '';

        // Focus on first field
        referenceInput?.focus();
    }

    // Generate automatic reference
    // This function is kept for compatibility but does nothing.
    // Ref generation happens in the backend when saving if ref is empty,
    // ensuring unique sequential refs (OU001, OU002, etc.).
    function generateReference() {
        // No-op: backend handles unique ref generation
    }

    // Initialize the page
    // Initialize custom fields
    async function initializeCustomFields() {
        try {
            if (window.CustomFields) {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Org Unit',
                    containerId: 'customFieldsContainer',
                    mode: 'create',
                    objectId: null
                });
                console.log('Custom fields initialized:', window.customFieldsContext);
            } else {
                console.warn('CustomFields not available');
            }
        } catch (error) {
            console.error('Error initializing custom fields:', error);
        }
        
    }
    
    async function initPage() {
        try {
            // Load statuses for dropdown
            await loadStatuses();
            
            // Initialize custom fields
            await initializeCustomFields();
            
            // Add event listener for name input to auto-generate reference
            if (nameInput) {
                console.log('Adding event listeners for name input to auto-generate reference');
                nameInput.addEventListener('input', generateReference);
                nameInput.addEventListener('blur', generateReference);
            } else {
                console.error('Name input not found for auto-generate reference');
            }

            // Wire up advanced rich text editor button
            const showDescriptionEditorBtn = document.getElementById('showDescriptionEditorBtn');
            if (showDescriptionEditorBtn) {
                showDescriptionEditorBtn.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.toggleAdvancedRichTextEditor('descriptionInput', showDescriptionEditorBtn);
                });
            }
        } catch (error) {
            console.error('Error initializing page:', error);
        }
    } // Fixed syntax issue


    // Load statuses from API for dropdown
    async function loadStatuses() {
        try {
            if (!statusSelect) {
                console.error('Status select element not found');
                return;
            }

            // Show loading state
            statusSelect.innerHTML = '<option value="">Loading statuses...</option>';

            // Check if API service is available
            if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.getStatusesForDropdown !== "function") {
                console.error("BUDG_API_SERVICE.getStatusesForDropdown not available, using fallback options...");
                return;
            }

            // Fetch statuses from API
            const response = await window.BUDG_API_SERVICE.getStatusesForDropdown();

            // Process API response
            if (response && response.success && response.data) {
                // Clear loading option
                statusSelect.innerHTML = '';

                // Add status options
                response.data.forEach(status => {
                    const option = document.createElement('option');
                    option.value = status.id;
                    option.textContent = status.primaryname;
                    statusSelect.appendChild(option);
                });

            } else {
                console.warn('Failed to load statuses from API, using fallback options');
            }
        } catch (error) {
            console.error('Error loading statuses:', error);
        }
    }

    // Rich Text Editor function
    function toggleRichTextEditor(textareaId, toggleBtn) {
        const textarea = document.getElementById(textareaId);
        if (!textarea) return;

        // If editor already active → destroy and sync back
        const existing = textarea.parentElement.querySelector('.rte-container');
        if (existing) {
            const editor = existing.querySelector('.rte-editor');
            textarea.value = editor.innerHTML.trim();
            existing.remove();
            textarea.style.display = '';
            toggleBtn.textContent = 'Show Editor';
            return;
        }

        // Build editor UI
        const container = document.createElement('div');
        container.className = 'rte-container';

        const toolbar = document.createElement('div');
        toolbar.className = 'rte-toolbar';

        const buttons = [
            { cmd: 'bold', icon: '<b>B</b>', title: 'Bold' },
            { cmd: 'italic', icon: '<i>I</i>', title: 'Italic' },
            { cmd: 'underline', icon: '<u>U</u>', title: 'Underline' },
            { sep: true },
            { cmd: 'insertUnorderedList', icon: '• List', title: 'Bulleted List' },
            { cmd: 'insertOrderedList', icon: '1. List', title: 'Numbered List' },
            { sep: true },
            { cmd: 'createLink', icon: '🔗', title: 'Insert Link', prompt: 'Enter URL' },
            { cmd: 'unlink', icon: '⨯', title: 'Remove Link' },
            { sep: true },
            { cmd: 'undo', icon: '↶', title: 'Undo' },
            { cmd: 'redo', icon: '↷', title: 'Redo' }
        ];

        buttons.forEach(b => {
            if (b.sep) {
                const sep = document.createElement('span');
                sep.className = 'rte-sep';
                toolbar.appendChild(sep);
                return;
            }
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'rte-btn';
            btn.title = b.title;
            btn.innerHTML = b.icon;
            btn.addEventListener('click', () => {
                if (b.cmd === 'createLink') {
                    const url = prompt(b.prompt || 'Enter URL');
                    if (url) document.execCommand('createLink', false, url);
                    return;
                }
                document.execCommand(b.cmd, false, null);
            });
            toolbar.appendChild(btn);
        });

        const editor = document.createElement('div');
        editor.className = 'rte-editor form-input';
        editor.contentEditable = 'true';
        editor.innerHTML = textarea.value || '';

        const footer = document.createElement('div');
        footer.className = 'rte-footer';
        const hideBtn = document.createElement('button');
        hideBtn.type = 'button';
        hideBtn.className = 'btn btn-secondary';
        hideBtn.textContent = 'Hide editor';
        hideBtn.addEventListener('click', () => toggleRichTextEditor(textareaId, toggleBtn));
        footer.appendChild(hideBtn);

        container.appendChild(toolbar);
        container.appendChild(editor);
        container.appendChild(footer);

        textarea.style.display = 'none';
        textarea.parentElement.appendChild(container);
        toggleBtn.textContent = 'Hide editor';
    }
});
