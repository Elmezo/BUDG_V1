// Capability Edit Page JavaScript
(function() {
    let segmentField = null; // Segment field component reference
    let originalCapabilitySegmentId = null; // segment loaded from server; used to detect segment change

    function normalizeCapabilitySegmentId(value) {
        if (value == null || value === '') return null;
        const n = parseInt(value, 10);
        return Number.isInteger(n) ? n : null;
    }

    function getEntityId() {
        const urlParams = new URLSearchParams(window.location.search);
        const id = parseInt(urlParams.get('id'), 10);
        return Number.isNaN(id) ? null : id;
    }

    function getActiveTab() {
        const urlParams = new URLSearchParams(window.location.search);
        return urlParams.get('tab') || 'summary';
    }

    function hasDataChanged() {
        // Check if any form data has changed
        const currentTab = document.querySelector('.tab.active');
        if (!currentTab) return false;
        
        const tabName = currentTab.getAttribute('data-tab');
        
        // Check main form data
        if (tabName === 'summary') {
            return window.hasFormChanges || false;
        }
        
        // Check stakeholders data
        if (tabName === 'stakeholders') {
            return window.capabilityStakeholderEdit && window.capabilityStakeholderEdit.hasDataChanged();
        }
        
        // Check impact data
        if (tabName === 'impact') {
            return window.hasImpactChanges && window.hasImpactChanges();
        }
        
        return false;
    }

    function waitForDependencies() {
        return new Promise((resolve) => {
            const checkDependencies = () => {
                if (window.BUDG_API_SERVICE && window.BUDG_CONFIG) {
                    resolve();
                } else {
                    setTimeout(checkDependencies, 100);
                }
            };
            checkDependencies();
        });
    }

    function initPageWithRetry(retryCount = 0) {
        const maxRetries = 5;
        if (retryCount >= maxRetries) {
            console.error('Failed to initialize page after maximum retries');
            return;
        }

        try {
            initPage();
        } catch (error) {
            console.error(`Error initializing page (attempt ${retryCount + 1}):`, error);
            setTimeout(() => initPageWithRetry(retryCount + 1), 1000);
        }
    }


    async function initPage() {
        console.log('Initializing capability edit page...');
        
        const id = getEntityId();
        if (!id) {
            console.error('No capability ID found in URL');
            return;
        }
        
        // Initialize lock
        const lockAcquired = await window.LockInitHelper.initializeLock('capability', parseInt(id), 'capability');
        if (!lockAcquired) {
            return; // Lock initialization failed, user was redirected
        }
        
        console.log('Initializing capability edit page for ID:', id);
        
        // Wait for dependencies
        await waitForDependencies();
        
        // Initialize UI components
        initTabs();
        setActiveTab();

        // Initialize segment field FIRST so segmentField is ready when populateForm runs
        if (window.SegmentField) {
            try {
                segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Capability',
                    fieldId: 'capabilitySegment',
                    errorId: 'capabilitySegmentError',
                    onChange: async (selectedSegmentId, previousSegmentId) => {
                        return await refreshParentOptionsForSelectedSegment(previousSegmentId);
                    }
                });
                console.log('Segment field initialized');
            } catch (error) {
                console.error('Error initializing segment field:', error);
            }
        }
        
        // Load data
        await loadCommitteeData();
        
        
        // Initialize form event listeners
        initFormEventListeners();
        
        // Initialize custom fields
        if (window.CustomFields) {
            try {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Capability',
                    containerId: 'customFieldsContainer',
                    mode: 'edit',
                    objectId: id
                });
                console.log('Custom fields initialized:', window.customFieldsContext);
            } catch (error) {
                console.error('Error initializing custom fields:', error);
            }
        }
        
        console.log('Capability edit page initialized successfully');
    }

    function initTabs() {
        const tabs = document.querySelectorAll('.tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', function() {
                const targetTab = this.getAttribute('data-tab');
                switchTab(targetTab);
            });
        });
    }
    
    function switchTab(tabName) {
        // Remove active class from all tabs
        document.querySelectorAll('.tab').forEach(tab => {
            tab.classList.remove('active');
        });
        
        // Remove active class from all tab contents
        document.querySelectorAll('.tab-content').forEach(content => {
            content.classList.remove('active');
        });
        
        // Add active class to selected tab
        const targetTab = document.querySelector(`.tab[data-tab="${tabName}"]`);
        if (targetTab) {
            targetTab.classList.add('active');
        }
        
        // Add active class to corresponding content
        const targetContent = document.querySelector(`.tab-content[data-tab="${tabName}"]`);
        if (targetContent) {
            targetContent.classList.add('active');
        }
        
        // Update URL without page reload
        const id = getEntityId();
        if (id) {
            const newUrl = new URL(window.location.href);
            newUrl.searchParams.set('tab', tabName);
            window.history.pushState({ tab: tabName }, '', newUrl);
        }
        
        // Load tab-specific content
        loadTabContentForEdit(tabName);
    }

    function setActiveTab() {
        const activeTabName = getActiveTab();
        const targetTab = document.querySelector(`.tab[data-tab="${activeTabName}"]`);
        const targetContent = document.querySelector(`.tab-content[data-tab="${activeTabName}"]`);
        
        if (targetTab && targetContent) {
            // Remove active class from all tabs and contents
            document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));
            document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));
            
            // Add active class to target tab and content
            targetTab.classList.add('active');
            targetContent.classList.add('active');
            
            // Load tab-specific content
            loadTabContentForEdit(activeTabName);
        }
    }

    function loadTabContentForEdit(tabName) {
        const id = getEntityId();
        if (!id) return;

        switch(tabName) {
            case 'stakeholders':
                // Load stakeholders in edit mode
                if (window.capabilityStakeholderEdit) {
                    window.capabilityStakeholderEdit.init(id);
                }
                break;
            case 'relationships':
                // Load relationships tab and initialize subtabs
                initRelationshipsSubTabs();
                // Load only hierarchy data initially (hierarchy subtab is active by default)
                loadCapabilityHierarchy(id);
                // Don't load relationships data here - it will be loaded when user clicks on relationships subtab
                break;
            case 'impact':
                // Load impact tab in edit mode
                if (window.initImpactEdit) {
                    window.initImpactEdit(id);
                }
                break;
            case 'summary':
                // Already loaded in main load function
                break;
            default:
                // Handle other tabs if needed
                break;
        }
    }
    
    function initRelationshipsSubTabs() {
        const subTabs = document.querySelectorAll('.relationships-sub-tabs .sub-tab');
        subTabs.forEach(tab => {
            tab.addEventListener('click', function() {
                const targetSubTab = this.getAttribute('data-sub-tab');
                switchRelationshipsSubTab(targetSubTab);
            });
        });
    }
    
    function switchRelationshipsSubTab(subTabName) {
        // Remove active class from all subtabs
        document.querySelectorAll('.relationships-sub-tabs .sub-tab').forEach(tab => {
            tab.classList.remove('active');
        });
        
        // Remove active class from all subtab contents
        document.querySelectorAll('.relationships-content .sub-tab-content').forEach(content => {
            content.classList.remove('active');
        });
        
        // Add active class to selected subtab
        const targetSubTab = document.querySelector(`.relationships-sub-tabs .sub-tab[data-sub-tab="${subTabName}"]`);
        if (targetSubTab) {
            targetSubTab.classList.add('active');
        }
        
        // Show corresponding content
        // Convert subtab name to proper ID format: "hierarchy" -> "hierarchyContent", "relationships" -> "relationshipsContent"
        let targetContentId;
        if (subTabName === 'hierarchy') {
            targetContentId = 'hierarchyContent';
        } else if (subTabName === 'relationships') {
            targetContentId = 'relationshipsContent';
        } else {
            targetContentId = `relationships${subTabName.charAt(0).toUpperCase() + subTabName.slice(1)}Content`;
        }
        
        const targetContent = document.getElementById(targetContentId);
        console.log('Switching to subtab:', subTabName, 'Target content ID:', targetContentId, 'Found:', !!targetContent);
        if (targetContent) {
            targetContent.classList.add('active');
            console.log('Content activated, display:', window.getComputedStyle(targetContent).display);
        } else {
            console.error('Target content not found:', targetContentId);
        }
        
        // Load content based on selected subtab
        const id = getEntityId();
        if (id) {
            if (subTabName === 'relationships') {
                console.log('Switching to relationships subtab, loading data for capability:', id);
                // Small delay to ensure DOM is updated
                setTimeout(() => {
                    loadCapabilityRelationshipsData(id);
                }, 100);
            } else if (subTabName === 'hierarchy') {
                console.log('Switching to hierarchy subtab, loading hierarchy data for capability:', id);
                // Small delay to ensure DOM is updated
                setTimeout(() => {
                    loadCapabilityHierarchy(id);
                }, 100);
            }
        }
    }

    async function loadCommitteeData() {
        const id = getEntityId();
        if (!id) return;

        try {
            console.log('Loading capability data for ID:', id);
            
            // Load dropdown data first
            await loadDropdownData();
            
            // Load capability data
            const response = await window.BUDG_API_SERVICE.getCapabilityById(id);
            console.log('Capability data response:', response);
            
            // Extract data from response
            const capability = response?.data || response;
            console.log('Capability data after unwrapping:', capability);
            
            if (capability) {
                populateForm(capability);
                updateTitle(capability);
            } else {
                console.error('No capability data found in response');
            }
        } catch (error) {
            console.error('Error loading capability data:', error);
            alert('Failed to load capability data. Please try again.');
        }
    }

    function findOptionValueByText(selectElement, searchText) {
        if (!selectElement || !searchText) return '';
        
        const options = selectElement.options;
        for (let i = 0; i < options.length; i++) {
            if (options[i].textContent.trim() === searchText.trim()) {
                return options[i].value;
            }
        }
        return '';
    }

    function populateForm(capability) {
        console.log('Populating form with capability data:', capability);
        
        const setVal = (id, value) => {
            const element = document.getElementById(id);
            if (element) {
                element.value = value || '';
            }
        };
        
        const setSel = (id, value) => {
            const element = document.getElementById(id);
            if (element && value != null) {
                element.value = String(value);
            }
        };
        
        // Populate form fields
        setVal('capabilityName', capability.PrimaryName || capability.primaryName);
        setVal('refNumber', capability.RefNumber || capability.refNumber);
        setVal('capabilityDescription', capability.Description || capability.description);
        
        // Populate parent
        selectedParentId = capability.Parent_ID || capability.parentId || null;
        if (selectedParentId) {
            loadParentName(selectedParentId);
        }
        
        // Populate select dropdowns
        setSel('budgStatus', capability.Status || capability.status);
        setSel('lifecycle', capability.Lifecycle || capability.lifecycle);
        setSel('budgViewing', capability.Is_Public || capability.isPublic || capability.is_public);
        setSel('classification', capability.Classification || capability.classification);
        setSel('capabilityType', capability.Capability_Type || capability.capabilityType || capability.capability_type);

        const serverSegmentId = capability.segmentId ?? capability.segment_id ?? capability.Segment_ID;
        if (segmentField && serverSegmentId != null) {
            segmentField.setValue(parseInt(serverSegmentId, 10));
            console.log('Segment field set to:', serverSegmentId);
        }
        if (segmentField && typeof segmentField.getValue === 'function') {
            originalCapabilitySegmentId = normalizeCapabilitySegmentId(segmentField.getValue());
        } else {
            originalCapabilitySegmentId = normalizeCapabilitySegmentId(serverSegmentId);
        }
        
        console.log('Form populated successfully');
    }
    
    async function loadParentName(parentId) {
        try {
            const response = await window.BUDG_API_SERVICE.getCapabilityById(parentId);
            const parent = response?.data || response;
            if (parent) {
                const parentName = parent.PrimaryName || parent.primaryName || parent.name || '';
                const parentInput = document.getElementById('parentName');
                if (parentInput) {
                    parentInput.value = parentName;
                }
            }
        } catch (error) {
            console.error('Error loading parent name:', error);
        }
    }

    function updateTitle(capability) {
        const titleElement = document.getElementById('capabilityTitle');
        if (titleElement && capability) {
            const name = capability.PrimaryName || capability.primaryName || capability.name || capability.Name || 'Capability';
            titleElement.textContent = name;
            console.log('Title updated to:', name);
        }
    }

    function initFormEventListeners() {
        // Save buttons
        const saveBtn = document.getElementById('saveBtn');
        const saveCloseBtn = document.getElementById('saveAndCloseBtn');
        const closeBtn = document.getElementById('closeBtn');

        if (saveBtn) saveBtn.addEventListener('click', () => saveCapability(false));
        if (saveCloseBtn) saveCloseBtn.addEventListener('click', () => saveCapability(true));
        if (closeBtn) closeBtn.addEventListener('click', async () => {
            await window.LockInitHelper.releaseLock();
            const id = getEntityId();
            window.location.href = `/view/capability/${id}`;
        });
        
        // Parent selection buttons
        const selectParentBtn = document.getElementById('selectParentBtn');
        const clearParentBtn = document.getElementById('clearParentBtn');
        
        if (selectParentBtn) {
            selectParentBtn.addEventListener('click', () => openParentSelectionModal());
        }
        
        if (clearParentBtn) {
            clearParentBtn.addEventListener('click', () => clearParent());
        }

        // Show editor button – advanced rich text editor
        const showEditorBtn = document.getElementById('showEditorBtn');
        if (showEditorBtn) {
            showEditorBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                toggleAdvancedRichTextEditor('capabilityDescription', showEditorBtn);
            });
        }
        
        // Form change tracking
        const formInputs = document.querySelectorAll('input, select, textarea');
        formInputs.forEach(input => {
            input.addEventListener('input', markAsDirty);
            input.addEventListener('change', markAsDirty);
        });
        
        // Set beforeunload handler
        window.onbeforeunload = function(e) {
            if (hasDataChanged()) {
                e.preventDefault();
                e.returnValue = '';
            }
        };
    }
    
    let selectedParentId = null;
    let capabilitiesList = [];
    
    async function openParentSelectionModal() {
        const modal = document.getElementById('parentSelectionModal');
        if (!modal) {
            console.error('Parent selection modal not found');
            return;
        }
        
        modal.style.display = 'flex';
        
        // Always reload so picker reflects current segment selection.
        await loadCapabilitiesForParentSelection();
        
        populateParentSelectionList();
    }
    
    function closeParentSelectionModal() {
        const modal = document.getElementById('parentSelectionModal');
        if (modal) {
            modal.style.display = 'none';
        }
    }
    
    // Make functions available globally for inline script
    window.closeParentSelectionModal = closeParentSelectionModal;
    window.populateParentSelectionList = populateParentSelectionList;
    
    async function loadCapabilitiesForParentSelection() {
        try {
            const activeSegmentId = segmentField && typeof segmentField.getValue === 'function'
                ? parseInt(segmentField.getValue(), 10)
                : NaN;
            const response = await window.BUDG_API_SERVICE.getCapabilities(
                Number.isInteger(activeSegmentId) && activeSegmentId > 0
                    ? { segmentId: activeSegmentId }
                    : {}
            );
            const currentId = getEntityId();
            
            if (response && response.data) {
                // Filter out current capability and its descendants to prevent circular references
                capabilitiesList = Array.isArray(response.data) ? response.data : [];
                capabilitiesList = capabilitiesList.filter(cap => {
                    const capId = cap.id || cap.ID;
                    return capId !== currentId;
                });
            }
        } catch (error) {
            console.error('Error loading capabilities for parent selection:', error);
            capabilitiesList = [];
        }
    }

    async function refreshParentOptionsForSelectedSegment(previousSegmentId) {
        await loadCapabilitiesForParentSelection();

        if (selectedParentId == null) {
            const modal = document.getElementById('parentSelectionModal');
            if (modal && modal.style.display === 'flex') {
                populateParentSelectionList();
            }
            return;
        }

        const selectedId = parseInt(selectedParentId, 10);
        const allowedParentIds = new Set(
            (capabilitiesList || [])
                .map(cap => parseInt(cap.id || cap.ID, 10))
                .filter(id => Number.isInteger(id) && id > 0)
        );

        if (!allowedParentIds.has(selectedId)) {
            alert('This parent is not valid for the selected segment. Please remove the parent first.');
            return false;
        }

        const modal = document.getElementById('parentSelectionModal');
        if (modal && modal.style.display === 'flex') {
            populateParentSelectionList();
        }
        return true;
    }
    
    function populateParentSelectionList() {
        const listContainer = document.getElementById('capabilitiesList');
        if (!listContainer) return;
        
        const searchInput = document.getElementById('parentSearchInput');
        const searchTerm = searchInput ? searchInput.value.toLowerCase() : '';
        
        listContainer.innerHTML = '';
        
        const filtered = capabilitiesList.filter(cap => {
            const name = (cap.primaryName || cap.PrimaryName || cap.name || '').toLowerCase();
            const desc = (cap.description || cap.Description || '').toLowerCase();
            return name.includes(searchTerm) || desc.includes(searchTerm);
        });
        
        if (filtered.length === 0) {
            listContainer.innerHTML = '<div class="no-capabilities">No capabilities found</div>';
            return;
        }
        
        filtered.forEach(cap => {
            const item = document.createElement('div');
            item.className = 'capability-item';
            const capId = cap.id || cap.ID;
            const name = cap.primaryName || cap.PrimaryName || cap.name || 'Unnamed';
            const desc = cap.description || cap.Description || '';
            
            item.innerHTML = `
                <div class="capability-name">${escapeHtml(name)}</div>
                <div class="capability-description">${escapeHtml(desc || 'No description')}</div>
            `;
            
            item.addEventListener('click', () => {
                selectParent(capId, name);
            });
            
            listContainer.appendChild(item);
        });
    }
    
    function selectParent(parentId, parentName) {
        selectedParentId = parentId;
        const parentInput = document.getElementById('parentName');
        if (parentInput) {
            parentInput.value = parentName || '';
        }
        closeParentSelectionModal();
        markAsDirty();
    }
    
    function clearParent() {
        selectedParentId = null;
        const parentInput = document.getElementById('parentName');
        if (parentInput) {
            parentInput.value = '';
        }
        markAsDirty();
    }

    async function loadDropdownData() {
        try {
            console.log('Loading dropdown data...');
            await Promise.all([
                loadStatuses(),
                loadViewingOptions(),
                loadLifecycles(),
                loadClassifications(),
                loadCapabilityTypes()
            ]);
            console.log('All dropdown data loaded successfully');
        } catch (error) {
            console.error('Error loading dropdown data:', error);
        }
    }

    async function loadStatuses() {
        try {
            const statuses = await window.BUDG_API_SERVICE.getStatusList();
            const statusList = (Array.isArray(statuses?.data) ? statuses.data : statuses) || [];
            
            const select = document.getElementById('budgStatus');
            if (select) {
                select.innerHTML = '<option value="">Select...</option>';
                statusList.forEach(status => {
                    const option = document.createElement('option');
                    option.value = status.id || status.ID;
                    option.textContent = status.name || status.Name || status.primaryName || status.PrimaryName;
                    select.appendChild(option);
                });
            }
        } catch (error) {
            console.error('Error loading statuses:', error);
        }
    }

    async function loadViewingOptions() {
        try {
            const viewings = await window.BUDG_API_SERVICE.getViewingList();
            const viewingList = (Array.isArray(viewings?.data) ? viewings.data : viewings) || [];
            
            const select = document.getElementById('budgViewing');
            if (select) {
                select.innerHTML = '<option value="">Select...</option>';
                viewingList.forEach(viewing => {
                    const option = document.createElement('option');
                    option.value = viewing.id || viewing.ID;
                    option.textContent = viewing.name || viewing.Name || viewing.primaryName || viewing.PrimaryName;
                    select.appendChild(option);
                });
            }
        } catch (error) {
            console.error('Error loading viewing options:', error);
        }
    }

    async function loadLifecycles() {
        try {
            const lifecycles = await window.BUDG_API_SERVICE.getCapabilityLifecycleList();
            const lifecycleList = (Array.isArray(lifecycles?.data) ? lifecycles.data : lifecycles) || [];
            
            const select = document.getElementById('lifecycle');
            if (select) {
                select.innerHTML = '<option value="">Select...</option>';
                lifecycleList.forEach(lifecycle => {
                    const option = document.createElement('option');
                    option.value = lifecycle.id || lifecycle.ID;
                    option.textContent = lifecycle.name || lifecycle.Name || lifecycle.primaryName || lifecycle.PrimaryName;
                    select.appendChild(option);
                });
            }
        } catch (error) {
            console.error('Error loading lifecycles:', error);
        }
    }

    async function loadClassifications() {
        try {
            const classifications = await window.BUDG_API_SERVICE.getCapabilityClassificationList();
            const classificationList = (Array.isArray(classifications?.data) ? classifications.data : classifications) || [];
            
            const select = document.getElementById('classification');
            if (select) {
                select.innerHTML = '<option value="">Select...</option>';
                classificationList.forEach(classification => {
                    const option = document.createElement('option');
                    option.value = classification.id || classification.ID;
                    option.textContent = classification.name || classification.Name || classification.primaryName || classification.PrimaryName;
                    select.appendChild(option);
                });
            }
        } catch (error) {
            console.error('Error loading classifications:', error);
        }
    }

    async function loadCapabilityTypes() {
        try {
            const types = await window.BUDG_API_SERVICE.getCapabilityTypeList();
            const typeList = (Array.isArray(types?.data) ? types.data : types) || [];
            
            const select = document.getElementById('capabilityType');
            if (select) {
                select.innerHTML = '<option value="">Select...</option>';
                typeList.forEach(type => {
                    const option = document.createElement('option');
                    option.value = type.id || type.ID;
                    option.textContent = type.name || type.Name || type.primaryName || type.PrimaryName;
                    select.appendChild(option);
                });
            }
        } catch (error) {
            console.error('Error loading capability types:', error);
        }
    }

    function validateForm() {
        const errors = [];
        
        // Required fields validation
        const requiredFields = [
            { id: 'capabilityName', name: 'Primary Name' },
            { id: 'capabilityDescription', name: 'Description' },
            { id: 'budgStatus', name: 'BUDG Status' },
            { id: 'lifecycle', name: 'Lifecycle' },
            { id: 'budgViewing', name: 'BUDG Viewing' },
            { id: 'classification', name: 'Classification' },
            { id: 'capabilityType', name: 'Capability Type' }
        ];
        
        requiredFields.forEach(field => {
            const element = document.getElementById(field.id);
            if (!element || !element.value.trim()) {
                errors.push(`${field.name} is required`);
                showFieldError(field.id, `${field.name} is required`);
            } else {
                clearFieldError(field.id);
            }
        });
        
        return errors.length === 0;
    }

    function showFieldError(fieldId, message) {
        const field = document.getElementById(fieldId);
        if (field) {
            field.classList.add('is-invalid');
        }
    }

    function clearFieldError(fieldId) {
        const field = document.getElementById(fieldId);
        if (field) {
            field.classList.remove('is-invalid');
        }
    }

    function clearFieldErrors() {
        document.querySelectorAll('.is-invalid').forEach(field => {
            field.classList.remove('is-invalid');
        });
    }

    function getCurrentActiveTab() {
        const activeTab = document.querySelector('.tab.active');
        return activeTab ? activeTab.getAttribute('data-tab') : 'summary';
    }

    async function saveCapability(closeAfterSave = false) {
        const activeTab = getCurrentActiveTab();
        console.log(`=== SAVING CAPABILITY (active tab: ${activeTab}) ===`);

        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        const restoreButtons = () => {
            buttons.forEach(b => {
                if (b) {
                    b.disabled = false;
                    if (b.id === 'saveBtn') b.textContent = 'Save';
                    if (b.id === 'saveAndCloseBtn') b.textContent = 'Save & Close';
                }
            });
        };
        buttons.forEach(b => {
            if (b) {
                b.disabled = true;
                b.textContent = 'Saving...';
            }
        });

        try {
            if (activeTab === 'relationships') {
                if (window.saveCapabilityRelationshipsData) {
                    const capabilityId = getEntityId();
                    if (capabilityId) {
                        await window.saveCapabilityRelationshipsData(capabilityId);
                        console.log('✅ Relationships saved successfully');
                    }
                } else {
                    showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                    restoreButtons();
                    return true;
                }

            } else if (activeTab === 'stakeholders') {
                if (window.capabilityStakeholderEdit && window.capabilityStakeholderEdit.saveStakeholders) {
                    await window.capabilityStakeholderEdit.saveStakeholders();
                    console.log('✅ Stakeholders saved successfully');
                } else {
                    showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                    restoreButtons();
                    return true;
                }

            } else if (activeTab === 'impact') {
                const capabilityImpactDirty = typeof window.hasImpactChanges === 'function' && window.hasImpactChanges();
                if (!capabilityImpactDirty) {
                    showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                    restoreButtons();
                    return true;
                }
                const capabilityId = getEntityId();
                const impactResult = await window.saveAllImpactData(capabilityId);
                console.log('Impact save result:', impactResult);
                if (impactResult && typeof impactResult === 'object' && impactResult.success === false) {
                    throw new Error(impactResult.message || 'Impact save failed');
                }
                console.log('✅ Impact saved successfully');

            } else {
                // summary tab
                clearFieldErrors();
                const isValid = validateForm();
                if (!isValid) {
                    alert('Please fix the validation errors before saving.');
                    restoreButtons();
                    return false;
                }

                let saveSuccess = await saveCapabilityData();
                if (!saveSuccess) {
                    restoreButtons();
                    return false;
                }

                // If segment changed, reload impact data (do not save impact from summary)
                const currentCapabilitySegment = normalizeCapabilitySegmentId(segmentField && segmentField.getValue());
                const capabilitySegmentChanged = currentCapabilitySegment !== normalizeCapabilitySegmentId(originalCapabilitySegmentId);
                if (capabilitySegmentChanged && window.initImpactEdit) {
                    console.log('=== Segment changed; reloading capability impact from server ===');
                    const capabilityId = getEntityId();
                    await window.initImpactEdit(capabilityId);
                }

                originalCapabilitySegmentId = normalizeCapabilitySegmentId(segmentField && segmentField.getValue());
                markAsClean();
            }

            // Release lock after successful save
            await window.LockInitHelper.releaseLock();

            showSuccessMessage('UPDATES SAVED');

            if (closeAfterSave) {
                window.onbeforeunload = null;
                const id = getEntityId();
                setTimeout(() => {
                    window.location.href = `/view/capability/${id}`;
                }, 1500);
            }

            return true;
        } catch (error) {
            console.error('Error saving capability:', error);
            const errorMsg = typeof window.formatSaveError === 'function' ? window.formatSaveError(error) : (error?.body?.error || error?.body?.message || error?.message || 'Failed to save capability');
            showSuccessMessage(errorMsg, true);
            return false;
        } finally {
            restoreButtons();
        }
    }

    async function saveCapabilityData() {
        const id = getEntityId();
        if (!id) return false;
        
        // Get current user ID
        let userId = null;
        try {
            const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (meResp.ok) {
                const me = await meResp.json();
                userId = me.id || me.ID || me.userId || me.user_id;
                console.log('Current user ID:', userId);
            }
        } catch (e) {
            console.error('Failed to get current user:', e);
        }
        
        // Get form values
        // Sync rich-text editor content back to textarea before reading
        if (typeof syncAdvancedRichTextToTextarea === 'function') {
            syncAdvancedRichTextToTextarea('capabilityDescription');
        }

        const capabilityName = document.getElementById('capabilityName')?.value?.trim();
        const refNumber = document.getElementById('refNumber')?.value?.trim() || null;
        const capabilityDescription = document.getElementById('capabilityDescription')?.value?.trim();
        const status = parseInt(document.getElementById('budgStatus')?.value || '', 10);
        const lifecycle = parseInt(document.getElementById('lifecycle')?.value || '', 10);
        const viewing = parseInt(document.getElementById('budgViewing')?.value || '', 10);
        const classification = parseInt(document.getElementById('classification')?.value || '', 10);
        const capabilityType = parseInt(document.getElementById('capabilityType')?.value || '', 10);
        
        // Prepare payload
        const payload = {
            primaryName: capabilityName,
            refNumber: refNumber,
            description: capabilityDescription,
            parentId: selectedParentId,
            status: status,
            lifecycle: lifecycle,
            isPublic: viewing,
            classification: classification,
            capabilityType: capabilityType,
            lastUpdateUserId: userId,
            segmentId: segmentField ? segmentField.getValue() : 1
        };
        
        console.log('Payload to send:', payload);
        
        try {
            const response = await window.BUDG_API_SERVICE.updateCapability(id, payload);
            console.log('Save response:', response);
            
            if (response && (response.success || response.id)) {
                console.log('Capability saved successfully');
                
                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                    try {
                        await window.customFieldsContext.saveValues(id);
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }
                
                
                return true;
            } else {
                console.error('Save failed:', response);
                const msg = (response && (response.message || response.error)) || 'Failed to save capability. Please try again.';
                if (typeof showSuccessMessage === 'function') {
                    showSuccessMessage(msg, true);
                } else {
                    alert(msg);
                }
                return false;
            }
        } catch (error) {
            console.error('Error saving capability:', error);
            // API puts duplicate ref / validation text in error body (e.g. IllegalArgumentException message)
            const msg = typeof window.formatSaveError === 'function' ? window.formatSaveError(error) : (error?.body?.error || error?.body?.message || error?.message || 'An error occurred while saving. Please try again.');
            if (typeof showSuccessMessage === 'function') {
                showSuccessMessage(msg, true);
            } else {
                alert(msg);
            }
            return false;
        }
    }

    // Form dirty state management
    let isDirty = false;
    window.hasFormChanges = false;

    function markAsDirty(e) {
        if (e && e.isTrusted === false) return;
        isDirty = true;
        window.hasFormChanges = true;
        updateSaveButtons();
    }

    function markAsClean() {
        isDirty = false;
        window.hasFormChanges = false;
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

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Build direct lineage tree (current + ancestors + descendants + siblings)
    function buildDirectLineageTree(capabilities, currentCapabilityId) {
        const byId = new Map();
        capabilities.forEach(c => byId.set(parseInt(c.id), c));

        const current = byId.get(parseInt(currentCapabilityId));
        if (!current) return [];

        const ancestors = new Set();
        const descendants = new Set();

        // Add current capability
        descendants.add(parseInt(currentCapabilityId));

        // Find all ancestors
        let parent = current;
        while (parent && parent.parentId) {
            const parentId = parseInt(parent.parentId);
            if (byId.has(parentId)) {
                parent = byId.get(parentId);
                ancestors.add(parentId);
            } else {
                break;
            }
        }

        // Find all descendants (including current capability's children and their descendants)
        function findDescendants(capabilityId) {
            capabilities.forEach(c => {
                if (parseInt(c.parentId) === capabilityId) {
                    const childId = parseInt(c.id);
                    descendants.add(childId);
                    findDescendants(childId); // Recursively find all descendants
                }
            });
        }
        findDescendants(parseInt(currentCapabilityId));

        // Find siblings (other children of the same parent) and their descendants
        if (current.parentId) {
            const parentId = parseInt(current.parentId);
            const siblings = capabilities.filter(c => {
                const cParentId = parseInt(c.parentId);
                const cId = parseInt(c.id);
                return cParentId === parentId && cId !== parseInt(currentCapabilityId);
            });

            // Add siblings and their descendants
            siblings.forEach(sibling => {
                const siblingId = parseInt(sibling.id);
                descendants.add(siblingId);
                findDescendants(siblingId); // Add all descendants of siblings (nephews/nieces and their descendants)
            });
        }

        // Include the current capability, all its ancestors, and all its descendants (including siblings and their descendants)
        const includedIds = new Set([parseInt(currentCapabilityId), ...ancestors, ...descendants]);

        // Filter capabilities to include only the complete family tree
        return capabilities.filter(c => {
            const id = parseInt(c.id);
            return includedIds.has(id);
        });
    }

    // Build hierarchy tree structure
    function buildHierarchyTree(capabilities, rootId) {
        const byId = new Map();
        const byParent = new Map();
        
        capabilities.forEach(c => {
            byId.set(parseInt(c.id), c);
            const parentId = parseInt(c.parentId) || 0;
            if (!byParent.has(parentId)) byParent.set(parentId, []);
            byParent.get(parentId).push(c);
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

    // Render capability hierarchy table following Regulatory Theme pattern
    function renderCapabilityTable(hierarchyRows, currentId) {
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const name = node.primaryName || 'Unnamed Capability';
            const desc = node.description || '';
            const isCurrent = String(node.id) === String(currentId);
            const id = node.id;
            const parentId = node.parentId || '';
            
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'capability-link current-capability-link' : 'capability-link';
            const link = `<a class="${linkClass}" href="/view/capability/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            const refNumber = node.refNumber ? `<span class="capability-ref">(${escapeHtml(node.refNumber)})</span>` : '';
            
            return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}">
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-cogs item-icon"></i><span class="capability-name">${link}</span>${refNumber}${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
            </tr>`;
        }).join('');

        return rowsHtml;
    }

    // Initialize capability hierarchy interactions - matching glossary hierarchy logic
    function initCapabilityInteractions(containerEl, hierarchyRows) {
        const collapsed = new Set();
        const parentMap = hierarchyRows.parentMap;
        const tbody = containerEl ? containerEl.querySelector('tbody') : document.getElementById('capabilityHierarchyTbody');
        if (!tbody) return;
        
        // Build a map of parent ID to child rows for faster lookup
        const childrenByParent = new Map();
        tbody.querySelectorAll('tr').forEach(tr => {
            const parentId = tr.getAttribute('data-parent-id');
            if (parentId) {
                if (!childrenByParent.has(parentId)) {
                    childrenByParent.set(parentId, []);
                }
                childrenByParent.get(parentId).push(tr);
            }
        });
        
        function toggleChildren(parentId, isCollapsed) {
            const children = childrenByParent.get(String(parentId)) || [];
            children.forEach(childTr => {
                const childId = childTr.getAttribute('data-id');
                const childParentId = childTr.getAttribute('data-parent-id');
                
                if (isCollapsed) {
                    // Hide this child and all its descendants
                    childTr.style.display = 'none';
                    childTr.classList.add('collapsed');
                    // Recursively hide all descendants
                    toggleChildren(childId, true);
                } else {
                    // Show this child
                    childTr.style.display = '';
                    childTr.classList.remove('collapsed');
                    // Recursively show children if parent is not collapsed
                    if (!collapsed.has(String(childId))) {
                        toggleChildren(childId, false);
                    }
                }
            });
        }
        
        function updateVisibility() {
            // Update all rows based on collapsed state
            tbody.querySelectorAll('tr').forEach(tr => {
                const id = tr.getAttribute('data-id');
                const parentId = tr.getAttribute('data-parent-id');
                
                // Check if any ancestor is collapsed
                let shouldHide = false;
                let currentParentId = parentId;
                const visited = new Set();
                
                while (currentParentId && !shouldHide && !visited.has(currentParentId)) {
                    visited.add(currentParentId);
                    if (collapsed.has(String(currentParentId))) {
                        shouldHide = true;
                        break;
                    }
                    // Find the parent row to get its parent ID
                    const parentRow = Array.from(tbody.querySelectorAll('tr')).find(r => 
                        r.getAttribute('data-id') === currentParentId
                    );
                    if (parentRow) {
                        currentParentId = parentRow.getAttribute('data-parent-id');
                    } else {
                        break;
                    }
                }
                
                if (shouldHide) {
                    tr.style.display = 'none';
                    tr.classList.add('collapsed');
                } else {
                    tr.style.display = '';
                    tr.classList.remove('collapsed');
                }
            });
            
            // Update expander button states
            tbody.querySelectorAll('tr').forEach(tr => {
                const id = tr.getAttribute('data-id');
                const expander = tr.querySelector('.tree-expander');
                if (expander) {
                    const icon = expander.querySelector('i');
                    if (collapsed.has(String(id))) {
                        if (icon) {
                            icon.classList.remove('fa-caret-down');
                            icon.classList.add('fa-caret-right');
                        }
                    } else {
                        if (icon) {
                            icon.classList.remove('fa-caret-right');
                            icon.classList.add('fa-caret-down');
                        }
                    }
                }
            });
        }
        
        // Handle expander button clicks
        containerEl.addEventListener('click', function(e) {
            if (e.target.closest('.tree-expander')) {
                e.preventDefault();
                e.stopPropagation();
                
                const button = e.target.closest('.tree-expander');
                const row = button.closest('tr');
                const id = row.getAttribute('data-id');
                
                // Toggle collapsed state
                if (collapsed.has(String(id))) {
                    collapsed.delete(String(id));
                } else {
                    collapsed.add(String(id));
                }
                
                // Update visibility
                toggleChildren(id, collapsed.has(String(id)));
                updateVisibility();
            }
        });
        
        // Initial visibility update
        updateVisibility();
                }
                
    // Function to load capability hierarchy data - matching glossary hierarchy logic
    async function loadCapabilityHierarchy(capabilityId) {
        const tbody = document.getElementById('capabilityHierarchyTbody');
        const footer = document.getElementById('capabilityHierarchyFooter');
        
        if (!tbody || !footer) return;
        
        try {
            // Show loading state
            tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading capability hierarchy...</td></tr>';
            footer.textContent = 'Loading...';
                
            // Fetch hierarchy data from API (includes ancestors, current, siblings, children, and siblings' children)
            console.log('[Capability Hierarchy] Fetching hierarchy for capability ID:', capabilityId);
            const hierarchyData = await window.BUDG_API_SERVICE.getCapabilityHierarchy(capabilityId);
            console.log('[Capability Hierarchy] Raw API response:', hierarchyData);
            
            // Handle different response formats
            let items = [];
            if (Array.isArray(hierarchyData)) {
                items = hierarchyData;
            } else if (hierarchyData && hierarchyData.data && Array.isArray(hierarchyData.data)) {
                items = hierarchyData.data;
            } else if (hierarchyData && Array.isArray(hierarchyData.items)) {
                items = hierarchyData.items;
            } else if (hierarchyData && typeof hierarchyData === 'object') {
                // Try to extract array from object
                const keys = Object.keys(hierarchyData);
                for (const key of keys) {
                    if (Array.isArray(hierarchyData[key])) {
                        items = hierarchyData[key];
                        break;
                    }
                }
            }
            const capabilities = await response.json();
            
            console.log('[Capability Hierarchy] Processed items:', items);
            console.log('[Capability Hierarchy] Items count:', items.length);
            
            if (items.length === 0) {
                console.warn('[Capability Hierarchy] No hierarchy data found for capability ID:', capabilityId);
                tbody.innerHTML = '<tr><td colspan="2" style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No hierarchy data available</td></tr>';
                footer.textContent = '0 records';
                return;
            }

            console.log('Capability hierarchy data from API:', items);
            console.log('Current capability ID:', capabilityId);
            
            // Build hierarchy tree structure from flat data with levels
            // Organize by relation type: ancestors (negative levels), current (level 0), siblings (level 0), descendants (positive levels), sibling_children (positive levels)
            const byId = new Map();
            const childrenMap = new Map();
            
            items.forEach(item => {
                const id = item.id;
                const parentId = item.parentId;
                byId.set(id, item);
                if (!childrenMap.has(parentId)) childrenMap.set(parentId, []);
                childrenMap.get(parentId).push(item);
            });
            
            // Find current capability to determine its parent and calculate base depth
            const currentCapability = items.find(item => item.relation === 'current');
            const currentParentId = currentCapability ? currentCapability.parentId : null;
            
            // Find the maximum ancestor level (most negative) to calculate base depth
            const ancestors = items.filter(item => item.relation === 'ancestor');
            const maxAncestorLevel = ancestors.length > 0 ? Math.min(...ancestors.map(a => a.level)) : 0;
            const ancestorDepthOffset = Math.abs(maxAncestorLevel); // How many ancestor levels we have
            
            // Calculate current depth: if has ancestors, depth = ancestorDepthOffset, else 0
            const currentDepth = currentParentId ? ancestorDepthOffset : 0;
            
            // Build tree structure: ancestors -> siblings -> current -> descendants -> siblings' children
            const hierarchyRows = [];
            
            // Helper to calculate depth based on level and relation
            function calculateDepth(item) {
                if (item.relation === 'ancestor') {
                    // Ancestors: convert negative level to positive depth (most negative = depth 0)
                    return ancestorDepthOffset + item.level; // -1 becomes (offset-1), -2 becomes (offset-2), etc.
                } else if (item.relation === 'current') {
                    return currentDepth;
                } else if (item.relation === 'sibling') {
                    return currentDepth; // Same depth as current
                } else if (item.relation === 'descendant') {
                    // Descendants: current depth + level (1, 2, 3...)
                    return currentDepth + item.level;
                } else if (item.relation === 'sibling_child') {
                    // Sibling children: sibling depth + level (1, 2, 3...)
                    return currentDepth + item.level;
                }
                return 0;
            }
            
            // Build proper hierarchical tree structure using parent-child relationships
            // This ensures children appear directly under their parents, not just sorted by level
            function buildHierarchicalOrder(items, currentCapabilityId) {
                const result = [];
                const processed = new Set();
                
                // Helper function to recursively add node and its children
                function addNodeAndChildren(nodeId, depth) {
                    if (processed.has(nodeId)) return;
                    processed.add(nodeId);
                    
                    const node = byId.get(nodeId);
                    if (!node) return;
                    
                    // Add current node
                    result.push({ node, depth, parentId: node.parentId });
                    
                    // Get and sort children by name
                    const children = (childrenMap.get(nodeId) || []).sort((a, b) => {
                        return (a.name || '').localeCompare(b.name || '');
                    });
                    
                    // Recursively add children
                    children.forEach(child => {
                        addNodeAndChildren(child.id, depth + 1);
                    });
                }
                
                // First, add ancestors in order (from root to current's parent)
                const ancestors = items.filter(item => item.relation === 'ancestor')
                    .sort((a, b) => a.level - b.level); // Most negative first (root ancestor)
                ancestors.forEach(ancestor => {
                    if (!processed.has(ancestor.id)) {
                        addNodeAndChildren(ancestor.id, calculateDepth(ancestor));
                    }
                });
                
                // Then add siblings (before current)
                const siblings = items.filter(item => item.relation === 'sibling')
                    .sort((a, b) => (a.name || '').localeCompare(b.name || ''));
                siblings.forEach(sibling => {
                    if (!processed.has(sibling.id)) {
                        addNodeAndChildren(sibling.id, calculateDepth(sibling));
            }
                });
                
                // Then add current
                if (currentCapability) {
                    addNodeAndChildren(currentCapability.id, calculateDepth(currentCapability));
                }
                
                // Then add descendants (children of current) - they will be added recursively
                const currentId = currentCapability ? currentCapability.id : currentCapabilityId;
                const descendants = items.filter(item => item.relation === 'descendant')
                    .filter(item => item.parentId === currentId); // Only direct children
                descendants.forEach(descendant => {
                    if (!processed.has(descendant.id)) {
                        addNodeAndChildren(descendant.id, calculateDepth(descendant));
                    }
                });
                
                // Finally, add sibling children (children of siblings) - they will be added recursively
                const siblingChildren = items.filter(item => item.relation === 'sibling_child');
                const siblingIds = siblings.map(s => s.id);
                siblingChildren
                    .filter(item => siblingIds.includes(item.parentId)) // Only direct children of siblings
                    .forEach(child => {
                        if (!processed.has(child.id)) {
                            addNodeAndChildren(child.id, calculateDepth(child));
                        }
                    });
                
                return result;
            }

            // Build hierarchy rows using proper tree structure
            const hierarchyRowsData = buildHierarchicalOrder(items, capabilityId);
            
            hierarchyRowsData.forEach(({ node, depth, parentId }) => {
                const children = childrenMap.get(node.id) || [];
                const hasChildren = children.length > 0;
                
                hierarchyRows.push({
                    node: node,
                    depth: depth,
                    parentId: parentId,
                    hasChildren: hasChildren,
                    relation: node.relation
                });
            });
            
            console.log('Processed capability hierarchy rows:', hierarchyRows);
            
            // Render the hierarchy
            const rowsHtml = hierarchyRows.map(({ node, depth, hasChildren, relation }) => {
                const name = node.name || '';
                const desc = node.description || '';
                const type = node.typeName || '';
                const isCurrent = relation === 'current';
                const id = node.id;
                const parentId = node.parentId || null;
                
                // Determine relationship type display (empty for current, show relationship for others)
                let relationshipDisplay = '';
                if (!isCurrent) {
                    if (relation === 'ancestor') {
                        relationshipDisplay = 'Is Parent Of';
                    } else if (relation === 'sibling') {
                        relationshipDisplay = 'Is Related to';
                    } else if (relation === 'descendant') {
                        relationshipDisplay = 'Is Child Of';
                    } else if (relation === 'sibling_child') {
                        relationshipDisplay = 'Is Related to';
                    }
                }
                
                // Calculate visual depth (indentation)
                const visualDepth = depth;
                const indent = Array(visualDepth).fill('<span class="tree-indent"></span>').join('');
                const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
                const childCount = childrenMap.get(id)?.length || 0;
                const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
                const linkClass = isCurrent ? 'capability-link current-capability-link' : 'capability-link';
                const link = `<a class="${linkClass}" href="/view/capability/${encodeURIComponent(id)}">${escapeHtml(name)}</a>`;
                
                return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId || ''}" data-depth="${visualDepth}" data-relation="${relation}">
                    <td><div class="tree-cell">${indent}${expander}${visualDepth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-cogs item-icon"></i><span class="capability-name">${link}</span>${countBadge}</div></td>
                    <td><span title="${escapeHtml(desc)}">${escapeHtml(desc || '-')}</span></td>
                </tr>`;
            }).join('');

            tbody.innerHTML = rowsHtml;
            const n = hierarchyRows.length;
            footer.textContent = n === 1 ? '1 record' : `${n} records`;
            
            // Build parent map for interactions
            const parentMap = new Map();
            items.forEach(item => {
                if (item.parentId) {
                    if (!parentMap.has(item.parentId)) {
                        parentMap.set(item.parentId, []);
                    }
                    parentMap.get(item.parentId).push(item.id);
                }
            });
            
            const hierarchyRowsObj = {
                rows: hierarchyRows,
                parentMap: parentMap
            };
            
            // Initialize interactions
            const container = tbody.closest('.relationships-hierarchy');
            if (container) {
                initCapabilityInteractions(container, hierarchyRowsObj);
            }
        } catch (e) {
            console.error('[Capability Hierarchy] Failed to load hierarchy:', e);
            console.error('[Capability Hierarchy] Error stack:', e.stack);
            console.error('[Capability Hierarchy] Error details:', {
                message: e.message,
                name: e.name,
                capabilityId: capabilityId
            });
            tbody.innerHTML = `<tr><td colspan="2" style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">Failed to load hierarchy data: ${e.message || 'Unknown error'}<br><small>Check console for details</small></td></tr>`;
            footer.textContent = '0 records';
        }
    }

    // ===== CAPABILITY RELATIONSHIPS FUNCTIONALITY =====

    // Store initial relationship IDs to detect deletions
    let initialCapabilityRelationshipIds = new Set();

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Load capability relationships data for edit page
    async function loadCapabilityRelationshipsData(id) {
        console.log('loadCapabilityRelationshipsData called with capabilityId:', id);
        const tbody = document.getElementById('relationshipsTableBody');
        if (!tbody) {
            console.error('relationshipsTableBody element not found in DOM');
            // Try to find the container
            const container = document.getElementById('relationshipsContent');
            console.error('relationshipsContent found:', !!container);
            if (container) {
                console.error('relationshipsContent classes:', container.className);
                console.error('relationshipsContent display:', window.getComputedStyle(container).display);
            }
            return;
        }
        
        // Check if the content is visible
        const contentContainer = document.getElementById('relationshipsContent');
        if (contentContainer) {
            const isActive = contentContainer.classList.contains('active');
            const display = window.getComputedStyle(contentContainer).display;
            console.log('relationshipsContent is active:', isActive, 'display:', display);
            if (!isActive) {
                console.warn('relationshipsContent is not active, adding active class');
                contentContainer.classList.add('active');
            }
        }
        
        console.log('Loading relationships for capability ID:', id);
        
        try {
            // Show loading state
            tbody.innerHTML = '<tr><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>';
            
            // Fetch relationships where this capability is the SOURCE
            const relationships = await window.BUDG_API_SERVICE.getCapabilityRelationshipsBySourceId(id);
            console.log('API Response:', relationships);
            
            // Store initial relationship IDs for change detection
            initialCapabilityRelationshipIds = new Set();
            const relationshipsList = relationships?.data || relationships || [];
            if (Array.isArray(relationshipsList)) {
                relationshipsList.forEach(rel => {
                    if (rel.id) {
                        initialCapabilityRelationshipIds.add(String(rel.id));
                    }
                });
            }
            console.log('Initial relationship IDs stored:', Array.from(initialCapabilityRelationshipIds));
            
            if (!Array.isArray(relationshipsList) || relationshipsList.length === 0) {
                console.log('No relationships found, creating empty row');
                tbody.innerHTML = '';
                // Always append an empty row for adding new entries
                try {
                    console.log('Building empty row for capability:', id);
                    const blankRow = await buildCapabilityRelationshipRow({ capabilityId: id, data: null });
                    console.log('Empty row built:', blankRow);
                    if (blankRow) {
                        tbody.appendChild(blankRow);
                        console.log('Empty row added successfully to tbody. Tbody now has', tbody.children.length, 'children');
                        // Force a reflow to ensure the table is visible
                        tbody.offsetHeight;
                    } else {
                        console.error('buildCapabilityRelationshipRow returned null');
                        tbody.innerHTML = '<tr><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No relationships found. Click "Add" to create a new relationship.</td></tr>';
                    }
                } catch (error) {
                    console.error('Error building empty row:', error);
                    console.error('Error stack:', error.stack);
                    tbody.innerHTML = '<tr><td colspan="3" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Error loading relationship form: ' + error.message + '</td></tr>';
                }
                return;
            }

            // Render relationships table with edit functionality
            tbody.innerHTML = '';
            
            for (const rel of relationshipsList) {
                const row = await buildCapabilityRelationshipRow({ capabilityId: id, data: rel });
                tbody.appendChild(row);
            }
            
            // Populate dropdowns for existing relationships
            await populateExistingCapabilityRelationships(relationshipsList);

            // Always append an empty row for adding new entries
            const blankRow = await buildCapabilityRelationshipRow({ capabilityId: id, data: null });
            tbody.appendChild(blankRow);

        } catch (error) {
            console.error('Failed to load relationships:', error);
            tbody.innerHTML = '<tr><td colspan="3" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load relationships: ' + error.message + '</td></tr>';
        }
    }

    // Build capability relationship row
    async function buildCapabilityRelationshipRow({ capabilityId, data = null }) {
        console.log('buildCapabilityRelationshipRow called with capabilityId:', capabilityId, 'data:', data);
        const row = document.createElement('tr');
        const isExisting = data && data.id != null;
        const isReverse = data && data.direction === 'reverse';
        row.className = isExisting ? (isReverse ? 'existing-row reverse-row' : 'existing-row') : 'empty-row';

        if (isExisting) {
            row.setAttribute('data-id', data.id);
            if (isReverse) {
                row.setAttribute('data-direction', 'reverse');
            }
        }

        // Load relationship types and capability list
        let relationshipTypes = [];
        let capabilityList = [];
        
        try {
            const relationshipTypesResponse = await fetch('/api/capabilities/relation-type/list');
            if (relationshipTypesResponse.ok) {
                relationshipTypes = await relationshipTypesResponse.json();
                console.log('Relationship types loaded:', relationshipTypes ? relationshipTypes.length : 0);
            } else {
                console.warn('Failed to load relationship types:', relationshipTypesResponse.status);
            }
        } catch (error) {
            console.error('Error loading relationship types:', error);
        }
        
        try {
            let capabilitiesResponse;
            if (window.BUDG_API_SERVICE && window.BUDG_API_SERVICE.getCapabilities) {
                capabilitiesResponse = await window.BUDG_API_SERVICE.getCapabilities();
            } else {
                const capabilityResponse = await fetch('/api/capabilities');
                if (capabilityResponse.ok) {
                    capabilitiesResponse = await capabilityResponse.json();
                }
            }
            
            // Handle both direct array and wrapped response
            if (Array.isArray(capabilitiesResponse)) {
                capabilityList = capabilitiesResponse;
            } else if (capabilitiesResponse && capabilitiesResponse.data && Array.isArray(capabilitiesResponse.data)) {
                capabilityList = capabilitiesResponse.data;
            } else {
                capabilityList = [];
            }
            
            console.log('Capability list loaded:', capabilityList ? capabilityList.length : 0, 'Response type:', typeof capabilitiesResponse, 'Is array:', Array.isArray(capabilitiesResponse));
        } catch (error) {
            console.error('Error loading capability list:', error);
            capabilityList = [];
        }

        const relationshipTypeSelect = document.createElement('select');
        relationshipTypeSelect.className = 'form-select form-select-sm relationship-type-select';
        if (isReverse) {
            relationshipTypeSelect.disabled = true;
            relationshipTypeSelect.style.backgroundColor = '#f3f4f6';
            relationshipTypeSelect.style.cursor = 'not-allowed';
        }
        relationshipTypeSelect.innerHTML = '<option value="">Select Relationship Type</option>' +
            (Array.isArray(relationshipTypes) ? relationshipTypes.map(rt => `<option value="${rt.id}">${escapeHtml(rt.name || rt.primaryname || '')}</option>`).join('') : '');
        const relationTypeId = data?.relationType || data?.relationTypeId || data?.RelationType || data?.relType;
        if (relationTypeId) {
            relationshipTypeSelect.value = String(relationTypeId);
            relationshipTypeSelect.setAttribute('data-original-value', String(relationTypeId));
        }

        const capabilitySelect = document.createElement('select');
        capabilitySelect.className = 'form-select form-select-sm capability-select';
        if (isReverse) {
            capabilitySelect.disabled = true;
            capabilitySelect.style.backgroundColor = '#f3f4f6';
            capabilitySelect.style.cursor = 'not-allowed';
        }
        capabilitySelect.innerHTML = '<option value="">Select Capability</option>' +
            (Array.isArray(capabilityList) ? capabilityList.map(c => `<option value="${c.id}">${escapeHtml(c.name || c.primaryName || '')}</option>`).join('') : '');
        const targetCapabilityId = data?.targetCapabilityId || data?.Target_Capability_ID || data?.target || data?.Target;
        if (targetCapabilityId) {
            capabilitySelect.value = String(targetCapabilityId);
            capabilitySelect.setAttribute('data-original-value', String(targetCapabilityId));
        }

        const relationshipTypeCell = document.createElement('td');
        relationshipTypeCell.appendChild(relationshipTypeSelect);

        const capabilityCell = document.createElement('td');
        capabilityCell.appendChild(capabilitySelect);

        const actionCell = document.createElement('td');
        if (isReverse) {
            actionCell.innerHTML = `
                <div class="action-buttons">
                    <span class="badge bg-secondary" title="Reverse relationship (read-only)">Read-only</span>
                </div>
            `;
        } else {
            actionCell.innerHTML = `
                <div class="action-buttons">
                    <button type="button" class="btn btn-sm btn-success" onclick="addCapabilityRelationship()" title="Add Row">
                        <i class="fas fa-plus"></i>
                    </button>
                    <button type="button" class="btn btn-sm btn-danger" onclick="removeCapabilityRelationship(this)" title="Remove Row">
                        <i class="fas fa-minus"></i>
                    </button>
                </div>
            `;
        }

        row.appendChild(relationshipTypeCell);
        row.appendChild(capabilityCell);
        row.appendChild(actionCell);

        console.log('buildCapabilityRelationshipRow completed, row created:', row);
        return row;
    }

    // Populate existing relationships with data
    async function populateExistingCapabilityRelationships(relationships) {
        try {
            // Load relationship types and capability list
            const [relationshipTypes, capabilitiesResponse] = await Promise.all([
                fetch('/api/capabilities/relation-type/list').then(res => res.json()).catch(() => []),
                window.BUDG_API_SERVICE && window.BUDG_API_SERVICE.getCapabilities ? window.BUDG_API_SERVICE.getCapabilities() : fetch('/api/capabilities').then(res => res.json()).catch(() => [])
            ]);
            
            // Handle capabilities response (can be array or wrapped object)
            let capabilityList = [];
            if (Array.isArray(capabilitiesResponse)) {
                capabilityList = capabilitiesResponse;
            } else if (capabilitiesResponse && capabilitiesResponse.data && Array.isArray(capabilitiesResponse.data)) {
                capabilityList = capabilitiesResponse.data;
            } else if (capabilitiesResponse && Array.isArray(capabilitiesResponse)) {
                capabilityList = capabilitiesResponse;
            }

            // Populate each existing relationship row
            relationships.forEach(function(rel) {
                const row = document.querySelector(`tr[data-id="${rel.id}"]`);
                if (!row) return;

                const relationshipSelect = row.querySelector('.relationship-type-select');
                const capabilitySelect = row.querySelector('.capability-select');

                // Populate relationship types
                if (relationshipSelect && Array.isArray(relationshipTypes)) {
                    relationshipSelect.innerHTML = '<option value="">Select Relationship Type</option>';
                    const relRelationTypeId = rel.relationType || rel.relationTypeId || rel.RelationType || rel.relType;
                    relationshipTypes.forEach(type => {
                        const option = document.createElement('option');
                        option.value = type.id;
                        option.textContent = type.name || type.primaryname || '';
                        if (relRelationTypeId && String(relRelationTypeId) === String(type.id)) {
                            option.selected = true;
                        }
                        relationshipSelect.appendChild(option);
                    });
                    if (relRelationTypeId) {
                        relationshipSelect.value = String(relRelationTypeId);
                        relationshipSelect.setAttribute('data-original-value', String(relRelationTypeId));
                    }
                }

                // Populate capability items
                if (capabilitySelect && Array.isArray(capabilityList)) {
                    capabilitySelect.innerHTML = '<option value="">Select Capability</option>';
                    const relTargetCapabilityId = rel.targetCapabilityId || rel.Target_Capability_ID || rel.target || rel.Target;
                    capabilityList.forEach(capability => {
                        const option = document.createElement('option');
                        option.value = capability.id;
                        option.textContent = capability.name || capability.primaryName || '';
                        if (relTargetCapabilityId && String(relTargetCapabilityId) === String(capability.id)) {
                            option.selected = true;
                        }
                        capabilitySelect.appendChild(option);
                    });
                    if (relTargetCapabilityId) {
                        capabilitySelect.value = String(relTargetCapabilityId);
                        capabilitySelect.setAttribute('data-original-value', String(relTargetCapabilityId));
                    }
                }
            });

        } catch (error) {
            console.error('Failed to populate existing relationships:', error);
        }
    }

    // Add new relationship row
    async function addCapabilityRelationship() {
        const tbody = document.getElementById('relationshipsTableBody');
        const capabilityId = getEntityId();

        try {
            const newRow = await buildCapabilityRelationshipRow({ capabilityId, data: null });
            tbody.appendChild(newRow);
        } catch (error) {
            console.error('Failed to load data for new relationship:', error);
            alert('Failed to load data for new relationship');
        }
    }

    // Remove relationship row
    async function removeCapabilityRelationship(button) {
        const row = button.closest('tr');
        const tbody = document.getElementById('relationshipsTableBody');
        const allRows = tbody.querySelectorAll('tr');
        const isFirstRow = row === allRows[0];
        const id = row.getAttribute('data-id');
        
        if (id) {
            // Delete from database
            try {
                const response = await fetch(`/api/capabilities/relationship/${id}`, {
                    method: 'DELETE'
                });

                if (response.ok) {
                    console.log('Relationship ID', id, 'deleted. Initial IDs still tracked:', Array.from(initialCapabilityRelationshipIds));
                    
                    if (isFirstRow) {
                        // For first row, only clear the data but keep the row structure
                        await clearCapabilityRelationshipRowData(row);
                    } else {
                        // For other rows, remove the entire row
                        row.remove();
                    }
                } else {
                    const error = await response.json();
                    alert('Failed to delete relationship: ' + (error.message || 'Unknown error'));
                }
            } catch (error) {
                console.error('Failed to delete relationship:', error);
                alert('Failed to delete relationship: ' + (error.message || 'Unknown error'));
            }
        } else {
            // For empty rows (no data-id)
            if (isFirstRow) {
                // For first empty row, only clear the data
                await clearCapabilityRelationshipRowData(row);
            } else {
                // For other empty rows, remove the entire row
                row.remove();
            }
        }
    }

    // Clear relationship row data
    async function clearCapabilityRelationshipRowData(row) {
        const capabilityId = getEntityId();
        const oldId = row.getAttribute('data-id');
        
        console.log('Clearing relationship row data. Old ID:', oldId);

        try {
            const newRow = await buildCapabilityRelationshipRow({ capabilityId, data: null });
            row.replaceWith(newRow);
        } catch (error) {
            console.error('Failed to clear row data:', error);
        }
    }

    // Save relationships data to database
    async function saveCapabilityRelationshipsData(capabilityId) {
        console.log('=== SAVING CAPABILITY RELATIONSHIPS DATA ===');
        console.log('Capability ID:', capabilityId);
        
        const tbody = document.getElementById('relationshipsTableBody');
        if (!tbody) {
            console.warn('relationshipsTableBody not found');
            return;
        }

        const emptyRows = tbody.querySelectorAll('.empty-row');
        const existingRows = tbody.querySelectorAll('.existing-row:not(.reverse-row)');
        console.log('Found empty rows:', emptyRows.length);
        console.log('Found existing rows (excluding reverse):', existingRows.length);
        
        let savedCount = 0;

        // Save new relationships (empty rows)
        for (const row of emptyRows) {
            const relationshipTypeSelect = row.querySelector('.relationship-type-select');
            const capabilitySelect = row.querySelector('.capability-select');
            const relationshipTypeId = relationshipTypeSelect?.value?.trim() || '';
            const targetCapabilityId = capabilitySelect?.value?.trim() || '';

            // Only save if both relationship type and target capability are selected
            if (relationshipTypeId && targetCapabilityId && relationshipTypeId !== '' && targetCapabilityId !== '') {
                const parsedRelationType = parseInt(relationshipTypeId, 10);
                const parsedTargetCapabilityId = parseInt(targetCapabilityId, 10);
                
                if (isNaN(parsedRelationType) || isNaN(parsedTargetCapabilityId)) {
                    console.error('Cannot save relationship: invalid numeric values');
                    alert('Cannot save relationship: Invalid Relationship Type or Capability ID');
                    continue;
                }
                
                try {
                    console.log('Saving new relationship:', {
                        sourceCapabilityId: capabilityId,
                        targetCapabilityId: parsedTargetCapabilityId,
                        relationType: parsedRelationType
                    });
                    
                    const response = await fetch('/api/capabilities/relationship', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        credentials: 'include',
                        body: JSON.stringify({
                            sourceId: parseInt(capabilityId),
                            targetId: parsedTargetCapabilityId,
                            relationType: parsedRelationType,
                            description: null
                        })
                    });

                    if (response.ok) {
                        const result = await response.json();
                        console.log('Created new relationship successfully:', result);
                        savedCount++;
                    } else {
                        const error = await response.json();
                        console.error('Failed to create relationship. Response:', error);
                        alert('Failed to create relationship: ' + (error.message || 'Unknown error'));
                    }
                } catch (error) {
                    console.error('Error creating relationship:', error);
                    alert('Error creating relationship: ' + error.message);
                }
            }
        }

        // Update existing relationships
        for (const row of existingRows) {
            const relationshipId = row.getAttribute('data-id');
            const relationshipTypeSelect = row.querySelector('.relationship-type-select');
            const capabilitySelect = row.querySelector('.capability-select');
            
            const relationshipTypeId = relationshipTypeSelect?.value?.trim() || '';
            const targetCapabilityId = capabilitySelect?.value?.trim() || '';
            
            const originalRelationTypeId = relationshipTypeSelect?.getAttribute('data-original-value');
            const originalTargetCapabilityId = capabilitySelect?.getAttribute('data-original-value');
            
            // Check if values have changed
            if (relationshipTypeId !== originalRelationTypeId || 
                targetCapabilityId !== originalTargetCapabilityId) {
                
                if (!relationshipTypeId || !targetCapabilityId) {
                    console.warn('Skipping update: missing required fields');
                    continue;
                }
                
                try {
                    console.log('Updating relationship:', {
                        relationshipId: relationshipId,
                        relationType: relationshipTypeId,
                        targetCapabilityId: targetCapabilityId
                    });
                    
                    const response = await fetch(`/api/capabilities/relationship/${relationshipId}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        credentials: 'include',
                        body: JSON.stringify({
                            relationType: parseInt(relationshipTypeId, 10),
                            targetCapabilityId: parseInt(targetCapabilityId, 10),
                            description: null
                        })
                    });

                    if (response.ok) {
                        console.log('Updated relationship successfully');
                        savedCount++;
                    } else {
                        const error = await response.json();
                        console.error('Failed to update relationship. Response:', error);
                        alert('Failed to update relationship: ' + (error.message || 'Unknown error'));
                    }
                } catch (error) {
                    console.error('Error updating relationship:', error);
                    alert('Error updating relationship: ' + error.message);
                }
            }
        }

        // Handle deletions: relationships in initial set but not in current rows
        const currentRelationshipIds = new Set();
        existingRows.forEach(row => {
            const id = row.getAttribute('data-id');
            if (id) {
                currentRelationshipIds.add(String(id));
            }
        });
        
        for (const initialId of initialCapabilityRelationshipIds) {
            if (!currentRelationshipIds.has(initialId)) {
                // This relationship was deleted - already handled in removeCapabilityRelationship
                console.log('Relationship', initialId, 'was deleted (already handled)');
            }
        }

        console.log('Saved', savedCount, 'relationship(s)');
        return { success: true, savedCount: savedCount };
    }

    // Make functions globally available
    window.addCapabilityRelationship = addCapabilityRelationship;
    window.removeCapabilityRelationship = removeCapabilityRelationship;
    window.saveCapabilityRelationshipsData = saveCapabilityRelationshipsData;

    // Start when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initPageWithRetry);
    } else {
        initPageWithRetry();
    }

})();


