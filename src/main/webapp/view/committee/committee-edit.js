// Committee Edit Page JavaScript
let segmentField = null; // Segment field component reference
    let originalCommitteeSegmentId = null; // segment loaded from server; used to detect segment change

    function normalizeCommitteeSegmentId(value) {
        if (value == null || value === '') return null;
        const n = parseInt(value, 10);
        return Number.isInteger(n) ? n : null;
    }

document.addEventListener('DOMContentLoaded', async function() {
    //('DOMContentLoaded event fired');

    // Check if we're on the edit page (not view page)
    if (!document.getElementById('committeeForm')) {
        //('Not on edit page, skipping initialization');
        return;
    }

    // Get ID from URL parameters
    function getEntityId() {
        const urlParams = new URLSearchParams(window.location.search);
        return urlParams.get('id');
    }

    // Get active tab from URL parameters
    function getActiveTab() {
        const urlParams = new URLSearchParams(window.location.search);
        return urlParams.get('tab') || 'view';
    }

    const entityId = getEntityId();
    if (!entityId) {
        alert('No entity ID provided');
        window.location.href = '/';
        return;
    }

    // Initialize lock
    const lockAcquired = await window.LockInitHelper.initializeLock('committee', parseInt(entityId), 'committee');
    if (!lockAcquired) {
        return; // Lock initialization failed, user was redirected
    }
    // Store original data for change detection
    let originalData = null;
    let isInitialSnapshotReady = false;
    let pendingSegmentNormalization = false; // true when record is currently unassigned (-1)

    function normalizeIntOrNull(value) {
        if (value == null || value === '') return null;
        const n = parseInt(value, 10);
        return Number.isNaN(n) ? null : n;
    }

    function getCurrentFormSnapshot() {
        const primaryNameInput = document.getElementById('primaryNameInput');
        const descriptionInput = document.getElementById('descriptionInput');
        const refNumberInput = document.getElementById('refNumberInput');
        const parentClientSelect = document.getElementById('parentClientSelect');
        const statusSelect = document.getElementById('statusSelect');
        const viewingSelect = document.getElementById('viewingSelect');
        const lifecycleSelect = document.getElementById('lifecycleSelect');
        const classificationSelect = document.getElementById('classificationSelect');
        const committeeTypeSelect = document.getElementById('committeeTypeSelect');
        const segmentSelect = document.getElementById('committeeSegment');

        const selectedSegment = normalizeIntOrNull(segmentSelect?.value);
        const segmentFromField = segmentField && typeof segmentField.getValue === 'function'
            ? normalizeIntOrNull(segmentField.getValue())
            : null;

        return {
            primary_name: primaryNameInput?.value?.trim() || '',
            description: descriptionInput?.value?.trim() || '',
            ref_number: refNumberInput?.value?.trim() || '',
            parent_id: normalizeIntOrNull(parentClientSelect?.value),
            status: normalizeIntOrNull(statusSelect?.value),
            is_public: normalizeIntOrNull(viewingSelect?.value),
            lifecycle: normalizeIntOrNull(lifecycleSelect?.value),
            classification: normalizeIntOrNull(classificationSelect?.value),
            committee_type: normalizeIntOrNull(committeeTypeSelect?.value),
            segment_id: selectedSegment ?? segmentFromField ?? 1
        };
    }

    function normalizeSnapshot(data) {
        if (!data) return null;
        return {
            primary_name: (data.primary_name || '').trim(),
            description: (data.description || '').trim(),
            ref_number: (data.ref_number || '').trim(),
            parent_id: normalizeIntOrNull(data.parent_id),
            status: normalizeIntOrNull(data.status),
            is_public: normalizeIntOrNull(data.is_public),
            lifecycle: normalizeIntOrNull(data.lifecycle),
            classification: normalizeIntOrNull(data.classification),
            committee_type: normalizeIntOrNull(data.committee_type),
            segment_id: normalizeIntOrNull(data.segment_id) ?? 1
        };
    }

    // Check if form data has changed
    function hasDataChanged() {
        if (!originalData || !isInitialSnapshotReady) return false;
        const currentData = getCurrentFormSnapshot();
        const baselineData = normalizeSnapshot(originalData);

        return (
            currentData.primary_name !== baselineData.primary_name ||
            currentData.description !== baselineData.description ||
            currentData.ref_number !== baselineData.ref_number ||
            currentData.parent_id !== baselineData.parent_id ||
            currentData.status !== baselineData.status ||
            currentData.is_public !== baselineData.is_public ||
            currentData.lifecycle !== baselineData.lifecycle ||
            currentData.classification !== baselineData.classification ||
            currentData.committee_type !== baselineData.committee_type ||
            currentData.segment_id !== baselineData.segment_id
        );
    }

    // Wait for all scripts to load
    function waitForDependencies() {
        const maxWait = 5000; // 5 seconds max wait
        const checkInterval = 100; // Check every 100ms
        let elapsed = 0;

        const checkDependencies = () => {
            //('Checking dependencies...', {
            //    BUDG_API_SERVICE: !!window.BUDG_API_SERVICE,
            //    DOMReady: document.readyState,
            //    elapsed: elapsed
            //});

            if (window.BUDG_API_SERVICE && document.readyState === 'complete') {
                //('All dependencies ready, initializing...');
                initPageWithRetry();
            } else if (elapsed < maxWait) {
                elapsed += checkInterval;
                setTimeout(checkDependencies, checkInterval);
            } else {
                console.error('Timeout waiting for dependencies');
                alert('Page initialization timeout. Please refresh and try again.');
            }
        };

        checkDependencies();
    }

    // Start dependency checking
    waitForDependencies();

    // Retry mechanism for initialization
    function initPageWithRetry(retryCount = 0) {
        const maxRetries = 5;
        const retryDelay = 200;

        try {
            initPage();
        } catch (error) {
            if (retryCount < maxRetries) {
                console.warn(`InitPage failed, retrying... (${retryCount + 1}/${maxRetries})`, error);
                setTimeout(() => {
                    initPageWithRetry(retryCount + 1);
                }, retryDelay);
            } else {
                console.error('Max retries reached, initialization failed', error);
            }
        }
    }


    // Main initialization function
    async function initPage() {
        //('Initializing committee edit page...');

        try {
            // Initialize form event listeners
            initFormEventListeners();

            // Initialize tabs
            initTabs();

            const impactTabEl = document.getElementById('impactTab');
            if (impactTabEl) {
                initCommitteeSubTabs(impactTabEl);
            }

            await loadDropdownData();

            // Initialize segment field BEFORE loading data so it's available when populateForm runs
            if (window.SegmentField) {
                try {
                    const container = document.getElementById('segmentFieldContainer');
                    if (!container) {
                        return;
                    }
                    
                    // Force container to be visible BEFORE initialization
                    // Remove any inline styles that might hide it
                    container.style.display = 'block';
                    container.style.visibility = 'visible';
                    container.style.opacity = '1';
                    container.style.height = 'auto';
                    container.style.overflow = 'visible';
                    // Remove display:none if it exists in style attribute
                    if (container.getAttribute('style')) {
                        const currentStyle = container.getAttribute('style');
                        const newStyle = currentStyle.replace(/display\s*:\s*none/gi, 'display: block');
                        container.setAttribute('style', newStyle + '; display: block !important; visibility: visible !important;');
                    }
                    
                    segmentField = await SegmentField.init('segmentFieldContainer', {
                        label: 'Segment',
                        required: true,
                        // Removed defaultValue - let API data set the correct value
                        sectionTitle: 'SEGMENTATION',
                        objectType: 'Committee',
                        fieldId: 'committeeSegment',
                        errorId: 'committeeSegmentError',
                        showSectionHeader: false,  // Don't show section header since it's inside CLASSIFICATIONS section
                        onChange: async (selectedSegmentId, previousSegmentId) => {
                            return await refreshCommitteeParentForSelectedSegment(previousSegmentId);
                        }
                    });
                    // Store reference on container for easy access (like system-interface)
                    if (container) {
                        container._segmentFieldInstance = segmentField;
                        // CRITICAL: Ensure container is visible AFTER initialization
                        container.style.setProperty('display', 'block', 'important');
                        container.style.setProperty('visibility', 'visible', 'important');
                        container.style.setProperty('opacity', '1', 'important');
                        container.style.setProperty('height', 'auto', 'important');
                        container.style.setProperty('overflow', 'visible', 'important');
                    }
                    
                    // Verify the field was rendered and ensure it stays visible
                    setTimeout(() => {
                        const selectElement = document.getElementById('committeeSegment');
                        if (selectElement) {
                            container.style.setProperty('display', 'block', 'important');
                            container.style.setProperty('visibility', 'visible', 'important');
                            container.style.setProperty('opacity', '1', 'important');
                            
                            selectElement.style.setProperty('display', 'block', 'important');
                            selectElement.style.setProperty('visibility', 'visible', 'important');
                            selectElement.style.setProperty('opacity', '1', 'important');
                            selectElement.style.setProperty('width', '100%', 'important');
                            selectElement.style.setProperty('min-height', '40px', 'important');
                            
                            const classificationsContainer = container.closest('.classifications-vertical') || 
                                                           container.parentElement?.closest('.classifications-vertical') ||
                                                           document.querySelector('.form-fields-container.classifications-vertical');
                            if (classificationsContainer) {
                                classificationsContainer.style.setProperty('display', 'flex', 'important');
                                classificationsContainer.style.setProperty('flex-direction', 'column', 'important');
                                classificationsContainer.style.setProperty('gap', '1rem', 'important');
                                classificationsContainer.style.setProperty('width', '100%', 'important');
                            }
                            
                            // Only set visibility/opacity on parents; NEVER change display on layout containers
                            // (form-row, form-section, edit-form-container, committeeViewContainer) or we break 2-column grid
                            const layoutContainers = ['form-row', 'form-section', 'edit-form-container'];
                            let parent = selectElement.parentElement;
                            while (parent && parent !== document.body) {
                                const isLayoutContainer = layoutContainers.some(c => parent.classList.contains(c)) ||
                                    parent.id === 'committeeViewContainer' || parent.id === 'committeeForm';
                                if (!isLayoutContainer) {
                                    parent.style.setProperty('visibility', 'visible', 'important');
                                    parent.style.setProperty('opacity', '1', 'important');
                                    if (parent.classList.contains('classifications-vertical') ||
                                        (parent.classList.contains('form-fields-container') && parent.classList.contains('classifications-vertical'))) {
                                        parent.style.setProperty('display', 'flex', 'important');
                                        parent.style.setProperty('flex-direction', 'column', 'important');
                                        parent.style.setProperty('gap', '1rem', 'important');
                                        parent.style.setProperty('width', '100%', 'important');
                                    } else if (parent.classList.contains('form-group')) {
                                        parent.style.setProperty('display', 'flex', 'important');
                                        parent.style.setProperty('flex-direction', 'column', 'important');
                                    } else if (parent.id === 'segmentFieldContainer') {
                                        parent.style.setProperty('display', 'block', 'important');
                                        parent.style.setProperty('visibility', 'visible', 'important');
                                        parent.style.setProperty('opacity', '1', 'important');
                                        parent.style.setProperty('height', 'auto', 'important');
                                        parent.style.setProperty('min-height', '60px', 'important');
                                    } else {
                                        parent.style.setProperty('display', 'block', 'important');
                                    }
                                }
                                parent = parent.parentElement;
                            }
                            
                            const rect = selectElement.getBoundingClientRect();
                            if (rect.top < 0 || rect.bottom > window.innerHeight) {
                                selectElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            }
                        }
                    }, 200);
                } catch (err) {
                    console.error('Error initializing segment field:', err);
                }
            }
            // Add a small delay to ensure dropdowns are fully rendered
            await new Promise(resolve => setTimeout(resolve, 100));
            // Load the committee data AFTER dropdowns are ready
            await loadCommitteeData();
            
            // Initialize custom fields
            if (window.CustomFields) {
                try {
                    window.customFieldsContext = await window.CustomFields.initForm({
                        facetId: 'Committee',
                        containerId: 'customFieldsContainer',
                        mode: 'edit',
                        objectId: parseInt(entityId, 10)
                    });
                } catch (error) {
                    console.error('Error initializing custom fields:', error);
                }
            }

            // Set active tab based on URL parameter
            setTimeout(() => {
                setActiveTab();
            }, 100);

            //('Committee edit page initialized successfully');
        } catch (error) {
            console.error('Error initializing committee edit page:', error);
            throw error;
        }
    }

    // Initialize tabs functionality
    function initTabs() {
        const tabs = document.querySelectorAll('.tab');
        const containers = document.querySelectorAll('[id$="Container"]');

        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                // Remove active class from all tabs
                tabs.forEach(t => t.classList.remove('active'));
                // Add active class to clicked tab
                tab.classList.add('active');

                // Hide all containers EXCEPT those nested inside committeeViewContainer
                const nestedContainers = ['segmentFieldContainer', 'customFieldsContainer'];
                containers.forEach(container => {
                    if (!nestedContainers.includes(container.id)) {
                        container.style.display = 'none';
                    }
                });
                
                // Also hide impactTab and committeeViewContainer
                const impactTab = document.getElementById('impactTab');
                const viewContainer = document.getElementById('committeeViewContainer');
                if (impactTab) {
                    impactTab.style.display = 'none';
                }
                if (viewContainer) {
                    // Must use !important: committee.css sets #committeeViewContainer { display: flex !important; }
                    viewContainer.style.setProperty('display', 'none', 'important');
                }

                // Show the corresponding container
                const tabName = tab.getAttribute('data-tab');
                
                // Special handling for impact tab
                if (tabName === 'impact') {
                    if (impactTab) {
                        impactTab.style.display = 'block';
                        // Initialize impact edit
                        if (entityId && window.initImpactEdit) {
                            window.initImpactEdit(parseInt(entityId));
                        }
                    } else {
                        console.error('impactTab element not found!');
                    }
                } else if (tabName === 'view') {
                    // Show view container for summary tab
                    if (viewContainer) {
                        viewContainer.style.setProperty('display', 'flex', 'important');
                        // CRITICAL: Ensure segmentFieldContainer is visible when summary tab is shown
                        const segmentContainer = document.getElementById('segmentFieldContainer');
                        if (segmentContainer) {
                            segmentContainer.style.display = 'block';
                            segmentContainer.style.visibility = 'visible';
                            segmentContainer.style.opacity = '1';
                        }
                    }
                } else {
                    const targetContainer = document.getElementById(`committee${tabName.charAt(0).toUpperCase() + tabName.slice(1)}Container`);
                    if (targetContainer) {
                        targetContainer.style.display = 'grid';
                    }
                }

                // Load tab-specific content for editing
                loadTabContentForEdit(tabName);
            });
        });
    }

    // Set active tab based on URL parameter
    function setActiveTab() {
        const activeTabName = getActiveTab();
        const targetTab = document.querySelector(`[data-tab="${activeTabName}"]`);

        if (targetTab) {
            // Remove active class from all tabs
            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
            // Add active class to target tab
            targetTab.classList.add('active');

            // Hide all containers EXCEPT those nested inside committeeViewContainer
            const nestedContainersSet = ['segmentFieldContainer', 'customFieldsContainer'];
            document.querySelectorAll('[id$="Container"]').forEach(container => {
                if (!nestedContainersSet.includes(container.id)) {
                    container.style.display = 'none';
                }
            });
            
            // Also hide impactTab and committeeViewContainer
            const impactTab = document.getElementById('impactTab');
            const viewContainer = document.getElementById('committeeViewContainer');
            if (impactTab) {
                impactTab.style.display = 'none';
            }
            if (viewContainer) {
                viewContainer.style.setProperty('display', 'none', 'important');
            }

            // Special handling for impact tab
            if (activeTabName === 'impact') {
                if (impactTab) {
                    impactTab.style.display = 'block';
                    // Initialize impact edit
                    if (entityId && window.initImpactEdit) {
                        window.initImpactEdit(parseInt(entityId));
                    }
                }
            } else if (activeTabName === 'view') {
                // Show view container for summary tab
                if (viewContainer) {
                    viewContainer.style.setProperty('display', 'flex', 'important');
                    // CRITICAL: Ensure segmentFieldContainer is visible when summary tab is shown
                    const segmentContainer = document.getElementById('segmentFieldContainer');
                    if (segmentContainer) {
                        segmentContainer.style.display = 'block';
                        segmentContainer.style.visibility = 'visible';
                        segmentContainer.style.opacity = '1';
                    }
                }
            } else {
                const targetContainer = document.getElementById(`committee${activeTabName.charAt(0).toUpperCase() + activeTabName.slice(1)}Container`);
                if (targetContainer) {
                    targetContainer.style.display = 'grid';
                }
            }

            // Load content for the active tab
            loadTabContentForEdit(activeTabName);
        }
    }

    // Load content for specific tab in edit mode
    function loadTabContentForEdit(tabName) {
        switch(tabName) {
            case 'stakeholders':
                // Load stakeholders in edit mode
                if (window.CommitteeStakeholderEdit) {
                    window.CommitteeStakeholderEdit.init(entityId);
                }
                break;
            case 'relationships':
                // Load relationships tab content
                loadCommitteeRelationships(entityId);
                break;
            case 'impact':
                // Impact tab is initialized when tab is clicked
                break;
            case 'workflow':
                if (entityId && window.ObjectWorkflowEdit) {
                    window.ObjectWorkflowEdit.ensureInitialized({
                        rootId: 'committeeObjectWorkflowRoot',
                        facetType: 'committee',
                        objectId: parseInt(entityId, 10)
                    });
                }
                break;
            case 'view':
                // Main committee details (Summary) - already loaded
                break;
            case 'summary':
                // Handle summary tab if it exists separately
                break;
            default:
                // Handle other tabs if needed
                break;
        }
    }

    // Load committee data from API
    async function loadCommitteeData() {
        try {
            //('Loading committee data for ID:', entityId);

            const committee = await window.BUDG_API_SERVICE.getCommitteeById(entityId);
            if (!committee) {
                throw new Error('Committee not found');
            }

            const data = committee.data || committee;

            // Always reconcile committee type with detail endpoint (same source as view page),
            // then map name -> ID using lookup if needed.
            try {
                const detailResp = await fetch(`/api/committee/detail?id=${encodeURIComponent(entityId)}`, {
                    credentials: 'include'
                });
                if (detailResp.ok) {
                    const detailJson = await detailResp.json();
                    const detailData = detailJson?.data || detailJson;
                    const detailTypeValue =
                        detailData?.Committee_Type_ID ??
                        detailData?.committee_type_id ??
                        detailData?.committeeTypeId ??
                        detailData?.Committee_Type ??
                        detailData?.committee_type ??
                        detailData?.committeeTypeName ??
                        detailData?.committee_type_name;

                    if (hasSelectValue(detailTypeValue)) {
                        if (typeof detailTypeValue === 'number' || /^\d+$/.test(String(detailTypeValue).trim())) {
                            data.committeeTypeId = parseInt(detailTypeValue, 10);
                        } else {
                            data.committeeTypeName = detailTypeValue;
                        }

                        try {
                            const lookupResp = await fetch('/api/committee/lookup?type=committeetype', {
                                credentials: 'include'
                            });
                            if (lookupResp.ok) {
                                const lookupRows = await lookupResp.json();
                                if (Array.isArray(lookupRows)) {
                                    // If we have a name from detail, resolve exact ID from lookup.
                                    if (!hasSelectValue(data.committeeTypeId) && typeof detailTypeValue === 'string') {
                                        const normalizedDetailType = detailTypeValue.trim().toLowerCase();
                                        const matchedType = lookupRows.find((row) => {
                                            const name = String(row?.PrimaryName ?? row?.primaryname ?? row?.Name ?? row?.name ?? '').trim().toLowerCase();
                                            return name === normalizedDetailType || name.includes(normalizedDetailType) || normalizedDetailType.includes(name);
                                        });
                                        if (matchedType?.ID != null) {
                                            data.committeeTypeId = matchedType.ID;
                                        }
                                    }
                                }
                            }
                        } catch (lookupError) {
                            console.warn('Committee type lookup reconciliation failed:', lookupError);
                        }
                    }
                }
            } catch (detailError) {
                console.warn('Committee detail reconciliation failed:', detailError);
            }

            let serverSegmentId = data.segmentId ?? data.segment_id ?? data.Segment_ID ?? 
                                 data.segment?.id ?? data.segment?.ID ?? 
                                 (data.segment && typeof data.segment === 'number' ? data.segment : null);
            originalCommitteeSegmentId = normalizeCommitteeSegmentId(serverSegmentId);
            const rawServerSegmentId = serverSegmentId;
            pendingSegmentNormalization = (rawServerSegmentId === -1);

            if (serverSegmentId === -1 || serverSegmentId == null || serverSegmentId === undefined) {
                serverSegmentId = 1;
            }

            originalData = {
                primary_name: data.primaryName || data.Name || data.primary_name || data.PrimaryName || data.primaryname || data.name || '',
                description: data.Description || data.description || data.definition || '',
                ref_number: data.refNumber || data.Ref || data.ref_number || data.RefNumber || data.ref || '',
                parent_id: data.parentId || data.Parent_ID || data.parent_id || data.parent_committee_id || null,
                status: data.status || data.Status || data.status_id || data.Status_ID || null,
                is_public: data.isPublic || data.Viewing || data.is_public || data.Is_Public || data.viewing_id || data.Viewing_ID || null,
                lifecycle: data.lifecycle || data.Lifecycle || data.lifecycle_id || data.Lifecycle_ID || null,
                classification: data.classification || data.Classification || data.classification_id || data.Classification_ID || null,
                committee_type: getCommitteeTypeRawValue(data),
                segment_id: (serverSegmentId != null && serverSegmentId !== undefined && serverSegmentId !== -1 && serverSegmentId > 0) 
                    ? parseInt(serverSegmentId, 10) 
                    : 1  // Default to Enterprise (1) instead of -1
            };

            // Populate form fields
            populateForm(data, serverSegmentId);

            // Capture a normalized baseline snapshot from actual form controls
            // after async segment selection settles, to avoid false dirty prompts.
            setTimeout(() => {
                originalData = getCurrentFormSnapshot();
                isInitialSnapshotReady = true;
            }, 400);

            // Update page title
            const pageTitle = document.getElementById('pageTitle');
            if (pageTitle) {
                pageTitle.textContent = `Edit Committee - ${originalData.primary_name}`;
            }

        } catch (error) {
            console.error('Error loading committee data:', error);
            alert('Failed to load committee data. Please refresh and try again.');
            throw error;
        }
    }

    // Populate form with committee data
    // Helper function to find option value by text content
    function findOptionValueByText(selectElement, searchText) {
        if (!selectElement || !searchText) return null;


        for (let i = 0; i < selectElement.options.length; i++) {
            const option = selectElement.options[i];
            const optionText = option.text.toLowerCase().trim();
            const searchTextLower = searchText.toLowerCase().trim();


            // Try exact match first
            if (optionText === searchTextLower) {
                return option.value;
            }

            // Try partial match (contains)
            if (optionText.includes(searchTextLower) || searchTextLower.includes(optionText)) {
                return option.value;
            }
        }

        return null;
    }

    // Set select value using ID first, then text fallback.
    // Handles number/string mismatches to avoid false "not matched" cases.
    function setSelectValueFromApi(selectElement, rawValue) {
        if (!selectElement || rawValue == null || rawValue === '') return false;

        const valueAsString = String(rawValue).trim();
        if (!valueAsString) return false;

        // Try direct ID/value assignment first.
        selectElement.value = valueAsString;
        if (String(selectElement.value) === valueAsString) {
            return true;
        }

        // Fallback: find by visible option text.
        const foundValue = findOptionValueByText(selectElement, valueAsString);
        if (foundValue != null) {
            selectElement.value = String(foundValue);
            return true;
        }

        return false;
    }

    function getCommitteeTypeRawValue(data) {
        if (!data) return null;

        const directCommitteeTypeObj =
            (data.committeeType && typeof data.committeeType === 'object') ? data.committeeType :
            (data.CommitteeType && typeof data.CommitteeType === 'object') ? data.CommitteeType :
            (data.Committee_Type && typeof data.Committee_Type === 'object') ? data.Committee_Type :
            null;

        // Prefer ID-based fields first.
        const idValue =
            data.committeeTypeId ??
            data.committee_type_id ??
            data.Committee_Type_ID ??
            data.CommitteeTypeID ??
            data.committeeTypeID ??
            directCommitteeTypeObj?.id ??
            directCommitteeTypeObj?.ID;

        if (idValue != null && idValue !== '') {
            return idValue;
        }

        // Fallback to name/text-based fields.
        return (
            data.committeeTypeName ??
            data.committee_type_name ??
            data.Committee_Type_Name ??
            data.CommitteeTypeName ??
            (typeof data.committeeType === 'string' || typeof data.committeeType === 'number' ? data.committeeType : null) ??
            (typeof data.Committee_Type === 'string' || typeof data.Committee_Type === 'number' ? data.Committee_Type : null) ??
            (typeof data.committee_type === 'string' || typeof data.committee_type === 'number' ? data.committee_type : null) ??
            directCommitteeTypeObj?.name ??
            directCommitteeTypeObj?.Name ??
            null
        );
    }

    function hasSelectValue(value) {
        return value !== null && value !== undefined && String(value).trim() !== '';
    }

    function populateForm(data, serverSegmentId) {

        // Primary Name
        const primaryNameInput = document.getElementById('primaryNameInput');
        if (primaryNameInput) {
            primaryNameInput.value = data.primaryName || data.Name || data.primary_name || data.PrimaryName || data.primaryname || data.name || '';
        }

        // Description
        const descriptionInput = document.getElementById('descriptionInput');
        if (descriptionInput) {
            descriptionInput.value = data.Description || data.description || data.definition || '';
        }

        // Reference Number
        const refNumberInput = document.getElementById('refNumberInput');
        if (refNumberInput) {
            refNumberInput.value = data.refNumber || data.Ref || data.ref_number || data.RefNumber || data.ref || '';
        }

        // Parent Committee (set directly since dropdowns are already loaded)
        const parentCommitteeSelect = document.getElementById('parentClientSelect');
        if (parentCommitteeSelect) {
            const parentId = data.parentId || data.Parent_ID || data.parent_id || data.parent_committee_id;
            if (parentId) {
                parentCommitteeSelect.value = parentId;
            }
        }

        // Status (set directly since dropdowns are already loaded)
        const statusSelect = document.getElementById('statusSelect');
        if (statusSelect) {
            const statusText = data.status || data.Status || data.status_id || data.Status_ID;
            if (statusText) {
                const assigned = setSelectValueFromApi(statusSelect, statusText);
                if (!assigned) setDefaultStatus();
            } else {
                setDefaultStatus();
            }
        } else {
            console.error('StatusSelect element not found!');
        }

        // Viewing (set directly since dropdowns are already loaded)
        const viewingSelect = document.getElementById('viewingSelect');
        if (viewingSelect) {
            const viewingText = data.isPublic || data.Viewing || data.is_public || data.Is_Public || data.viewing_id || data.Viewing_ID;
            if (viewingText) {
                const assigned = setSelectValueFromApi(viewingSelect, viewingText);
                if (!assigned) setDefaultViewing();
            } else {
                setDefaultViewing();
            }
        }

        // Lifecycle (set directly since dropdowns are already loaded)
        const lifecycleSelect = document.getElementById('lifecycleSelect');
        if (lifecycleSelect) {
            const lifecycleText = data.lifecycle || data.Lifecycle || data.lifecycle_id || data.Lifecycle_ID;
            if (lifecycleText) {
                const assigned = setSelectValueFromApi(lifecycleSelect, lifecycleText);
                if (!assigned) setDefaultLifecycle();
            } else {
                setDefaultLifecycle();
            }
        }

        // Classification (set directly since dropdowns are already loaded)
        const classificationSelect = document.getElementById('classificationSelect');
        if (classificationSelect) {
            const classificationText = data.classification || data.Classification || data.classification_id || data.Classification_ID;
            if (classificationText) {
                const assigned = setSelectValueFromApi(classificationSelect, classificationText);
                if (!assigned) setDefaultClassification();
            } else {
                setDefaultClassification();
            }
        }

        // Committee Type (set directly since dropdowns are already loaded)
        const committeeTypeSelect = document.getElementById('committeeTypeSelect');
        if (committeeTypeSelect) {
            const committeeTypeValue = getCommitteeTypeRawValue(data);
            if (hasSelectValue(committeeTypeValue)) {
                const assigned = setSelectValueFromApi(committeeTypeSelect, committeeTypeValue);
                if (!assigned) {
                    // Retry once after a short delay in case options are not fully attached yet.
                    setTimeout(() => {
                        const retryAssigned = setSelectValueFromApi(committeeTypeSelect, committeeTypeValue);
                        if (!retryAssigned) setDefaultCommitteeType();
                    }, 150);
                }
            } else {
                setDefaultCommitteeType();
            }
        }

        // Set segment field value (same as client-edit.js)
        if (segmentField) {
            // Use a small delay to ensure segment field options are loaded
            setTimeout(() => {
                try {
                    // If serverSegmentId is available and valid, use it; otherwise use Enterprise (1) as default
                    let numericId = 1; // Default to Enterprise
                    if (serverSegmentId != null && serverSegmentId !== undefined && serverSegmentId !== -1) {
                        numericId = parseInt(serverSegmentId, 10);
                        if (isNaN(numericId) || numericId <= 0) {
                            numericId = 1; // Fallback to Enterprise if parsing fails or invalid
                        }
                    } else {
                        numericId = 1;
                    }
                    
                    segmentField.setValue(numericId);
                    
                    // Verify the value was set correctly
                    setTimeout(() => {
                        const selectElement = document.getElementById('committeeSegment');
                        if (selectElement) {
                            const currentValue = parseInt(selectElement.value, 10);
                            if (currentValue !== numericId) {
                                console.warn('Segment value mismatch! Expected:', numericId, 'Got:', currentValue);
                                // Try setting again
                                segmentField.setValue(numericId);
                            } else {
                            }
                        }
                    }, 50);
                } catch (error) {
                    console.error('Error setting segment value:', error);
                }
            }, 200); // Increased delay to ensure field is fully initialized
        }
    }

    // Initialize form event listeners
    function initFormEventListeners() {
        //('Initializing form event listeners...');

        // Save button
        const saveBtn = document.getElementById('saveBtn');
        if (saveBtn) {
            saveBtn.addEventListener('click', () => saveCommittee(false));
        }

        // Save & Close button
        const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
        if (saveAndCloseBtn) {
            saveAndCloseBtn.addEventListener('click', () => saveCommittee(true));
        }

        // Cancel button
        const cancelBtn = document.getElementById('cancelBtn');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', cancelForm);
        }

        // Form validation on input
        const form = document.getElementById('committeeForm');
        if (form) {
            form.addEventListener('input', validateForm);
        }

        // Show editor button – advanced rich text editor
        const editorButton = document.querySelector('.editor-button');
        if (editorButton) {
            editorButton.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                toggleAdvancedRichTextEditor('descriptionInput', editorButton);
            });
        }
    }

    // Load dropdown data
    async function loadDropdownData() {
        //('Loading dropdown data...');

        try {
            await Promise.all([
                loadStatuses(),
                loadViewingOptions(),
                loadLifecycles(),
                loadClassifications(),
                loadCommitteeTypes(),
                loadParentCommittees()
            ]);

            // Dropdowns are now loaded and ready for entity data

        } catch (error) {
            console.error('Error loading dropdown data:', error);
            alert('Failed to load some dropdown data. Please refresh and try again.');
        }
    }

    // Load statuses from API for dropdown
    async function loadStatuses() {
        try {
            const statusSelect = document.getElementById('statusSelect');
            if (!statusSelect) {
                console.error('LoadStatuses - StatusSelect element not found!');
                return;
            }

            statusSelect.innerHTML = '<option value="">Loading statuses...</option>';

            const response = await fetch('/api/committee/lookup?type=status');
            const data = await response.json();

            if (data && Array.isArray(data) && data.length > 0) {
                statusSelect.innerHTML = '';

                // Add empty option first
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = 'Select Status';
                statusSelect.appendChild(emptyOption);

                data.forEach((status) => {
                    const option = document.createElement('option');
                    option.value = status.ID;
                    option.textContent = status.primaryname;
                    statusSelect.appendChild(option);
                    console.log('LoadStatuses - Added option:', {id: status.ID, name: status.primaryname});
                });

                console.log('LoadStatuses - Total options loaded:', statusSelect.options.length);
            } else {
                console.warn('LoadStatuses - No status data received');
                statusSelect.innerHTML = '<option value="">Status not available</option>';
            }
        } catch (error) {
            console.error('Error loading statuses:', error);
            const statusSelect = document.getElementById('statusSelect');
            if (statusSelect) {
                statusSelect.innerHTML = '<option value="">Error loading statuses</option>';
            }
        }
    }

    // Load viewing options from API for dropdown
    async function loadViewingOptions() {
        try {
            const viewingSelect = document.getElementById('viewingSelect');
            if (!viewingSelect) return;

            viewingSelect.innerHTML = '<option value="">Loading viewing options...</option>';

            const response = await fetch('/api/committee/lookup?type=viewing');
            const data = await response.json();

            if (data && Array.isArray(data) && data.length > 0) {
                viewingSelect.innerHTML = '';

                // Add empty option first
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = 'Select Viewing';
                viewingSelect.appendChild(emptyOption);

                data.forEach((viewing) => {
                    const option = document.createElement('option');
                    option.value = viewing.ID;
                    option.textContent = viewing.Name;
                    viewingSelect.appendChild(option);
                });
            } else {
                viewingSelect.innerHTML = '<option value="">Viewing not available</option>';
            }
        } catch (error) {
            console.error('Error loading viewing options:', error);
            const viewingSelect = document.getElementById('viewingSelect');
            if (viewingSelect) {
                viewingSelect.innerHTML = '<option value="">Error loading viewing options</option>';
            }
        }
    }

    // Load lifecycles from API for dropdown
    async function loadLifecycles() {
        try {
            const lifecycleSelect = document.getElementById('lifecycleSelect');
            if (!lifecycleSelect) return;

            lifecycleSelect.innerHTML = '<option value="">Loading lifecycles...</option>';

            const response = await fetch('/api/committee/lookup?type=lifecycle');
            const data = await response.json();

            if (data && Array.isArray(data) && data.length > 0) {
                lifecycleSelect.innerHTML = '';

                // Add empty option first
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = 'Select Lifecycle';
                lifecycleSelect.appendChild(emptyOption);

                data.forEach((lifecycle) => {
                    const option = document.createElement('option');
                    option.value = lifecycle.ID;
                    option.textContent = lifecycle.PrimaryName;
                    lifecycleSelect.appendChild(option);
                });
            } else {
                lifecycleSelect.innerHTML = '<option value="">Lifecycle not available</option>';
            }
        } catch (error) {
            console.error('Error loading lifecycles:', error);
            const lifecycleSelect = document.getElementById('lifecycleSelect');
            if (lifecycleSelect) {
                lifecycleSelect.innerHTML = '<option value="">Error loading lifecycles</option>';
            }
        }
    }

    // Load classifications from API for dropdown
    async function loadClassifications() {
        try {
            const classificationSelect = document.getElementById('classificationSelect');
            if (!classificationSelect) return;

            classificationSelect.innerHTML = '<option value="">Loading classifications...</option>';

            const response = await fetch('/api/committee/lookup?type=classification');
            const data = await response.json();

            if (data && Array.isArray(data) && data.length > 0) {
                classificationSelect.innerHTML = '';

                // Add empty option first
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = 'Select Classification';
                classificationSelect.appendChild(emptyOption);

                data.forEach((classification) => {
                    const option = document.createElement('option');
                    option.value = classification.ID;
                    option.textContent = classification.PrimaryName;
                    classificationSelect.appendChild(option);
                });
            } else {
                classificationSelect.innerHTML = '<option value="">Classification not available</option>';
            }
        } catch (error) {
            console.error('Error loading classifications:', error);
            const classificationSelect = document.getElementById('classificationSelect');
            if (classificationSelect) {
                classificationSelect.innerHTML = '<option value="">Error loading classifications</option>';
            }
        }
    }

    // Load committee types from API for dropdown
    async function loadCommitteeTypes() {
        try {
            const committeeTypeSelect = document.getElementById('committeeTypeSelect');
            if (!committeeTypeSelect) return;

            committeeTypeSelect.innerHTML = '<option value="">Loading committee types...</option>';

            const response = await fetch('/api/committee/lookup?type=committeetype');
            const data = await response.json();

            if (data && Array.isArray(data) && data.length > 0) {
                committeeTypeSelect.innerHTML = '';

                // Add empty option first
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = 'Select Committee Type';
                committeeTypeSelect.appendChild(emptyOption);

                data.forEach((committeeType) => {
                    const option = document.createElement('option');
                    option.value = committeeType.ID;
                    option.textContent = committeeType.PrimaryName;
                    committeeTypeSelect.appendChild(option);
                });
            } else {
                committeeTypeSelect.innerHTML = '<option value="">Committee Type not available</option>';
            }
        } catch (error) {
            console.error('Error loading committee types:', error);
            const committeeTypeSelect = document.getElementById('committeeTypeSelect');
            if (committeeTypeSelect) {
                committeeTypeSelect.innerHTML = '<option value="">Error loading committee types</option>';
            }
        }
    }

    // Load parent committees from API for dropdown
    async function loadParentCommittees() {
        try {
            const parentClientSelect = document.getElementById('parentClientSelect');
            if (!parentClientSelect) return;

            parentClientSelect.innerHTML = '<option value="">Loading parent committees...</option>';

            const activeSegmentId = segmentField ? parseInt(segmentField.getValue(), 10) : null;
            const segQuery = Number.isInteger(activeSegmentId) && activeSegmentId > 0 ? `&segmentId=${activeSegmentId}` : '';
            const response = await fetch(`/api/committee/lookup?type=committees${segQuery}`);
            const data = await response.json();

            if (data && Array.isArray(data) && data.length > 0) {
                parentClientSelect.innerHTML = '';

                // Add empty option for no parent
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = 'No Parent Committee';
                parentClientSelect.appendChild(emptyOption);

                data.forEach((committee) => {
                    const option = document.createElement('option');
                    option.value = committee.ID;
                    option.textContent = committee.Display_Name || committee.PrimaryName;
                    parentClientSelect.appendChild(option);
                });
            } else {
                parentClientSelect.innerHTML = '<option value="">No parent committees available</option>';
            }
        } catch (error) {
            console.error('Error loading parent committees:', error);
            const parentClientSelect = document.getElementById('parentClientSelect');
            if (parentClientSelect) {
                parentClientSelect.innerHTML = '<option value="">Error loading parent committees</option>';
            }
        }
    }

    async function refreshCommitteeParentForSelectedSegment(previousSegmentId) {
        const parentCommitteeSelect = document.getElementById('parentClientSelect');
        const previousParentId = parentCommitteeSelect?.value || '';
        await loadParentCommittees();

        if (!parentCommitteeSelect || !previousParentId) {
            return;
        }

        const stillAllowed = Array.from(parentCommitteeSelect.options || [])
            .some(option => String(option.value) === String(previousParentId));

        if (!stillAllowed) {
            alert('This parent is not valid for the selected segment. Please remove the parent first.');
            return false;
        } else {
            parentCommitteeSelect.value = previousParentId;
        }
        return true;
    }

    function activateSummaryTabForValidation() {
        const summaryTab = document.querySelector('.tab[data-tab="view"]') || document.querySelector('.tab[data-tab="summary"]');
        if (summaryTab && !summaryTab.classList.contains('active')) {
            summaryTab.click();
        }
    }

    // Validate form
    function validateForm() {
        const primaryNameInput = document.getElementById('primaryNameInput');
        const descriptionInput = document.getElementById('descriptionInput');
        const statusSelect = document.getElementById('statusSelect');
        const viewingSelect = document.getElementById('viewingSelect');
        const lifecycleSelect = document.getElementById('lifecycleSelect');
        const classificationSelect = document.getElementById('classificationSelect');
        const committeeTypeSelect = document.getElementById('committeeTypeSelect');
        const segmentSelect = document.getElementById('committeeSegment');

        let isValid = true;
        const invalidElements = [];

        // Clear previous errors
        clearFieldErrors();

        // Validate required fields
        if (!primaryNameInput?.value?.trim()) {
            showFieldError('primaryNameError', 'Primary Name is required');
            isValid = false;
            if (primaryNameInput) invalidElements.push(primaryNameInput);
        }

        if (!descriptionInput?.value?.trim()) {
            showFieldError('descriptionError', 'Description is required');
            isValid = false;
            if (descriptionInput) invalidElements.push(descriptionInput);
        }

        if (!statusSelect?.value) {
            showFieldError('budgStatusError', 'BUDG Status is required');
            isValid = false;
            if (statusSelect) invalidElements.push(statusSelect);
        }

        if (!viewingSelect?.value) {
            showFieldError('budgViewingError', 'BUDG Viewing is required');
            isValid = false;
            if (viewingSelect) invalidElements.push(viewingSelect);
        }

        if (!lifecycleSelect?.value) {
            showFieldError('lifecycleError', 'Lifecycle is required');
            isValid = false;
            if (lifecycleSelect) invalidElements.push(lifecycleSelect);
        }

        if (!classificationSelect?.value) {
            showFieldError('classificationError', 'Classification is required');
            isValid = false;
            if (classificationSelect) invalidElements.push(classificationSelect);
        }

        if (!committeeTypeSelect?.value) {
            showFieldError('committeeTypeError', 'Committee Type is required');
            isValid = false;
            if (committeeTypeSelect) invalidElements.push(committeeTypeSelect);
        }

        if (segmentField && typeof segmentField.validate === 'function' && !segmentField.validate()) {
            isValid = false;
            if (segmentSelect) invalidElements.push(segmentSelect);
        } else if (!segmentSelect || !segmentSelect.value) {
            // Fallback if SegmentField is not available yet.
            showFieldError('committeeSegmentError', 'Segment is required');
            isValid = false;
            if (segmentSelect) invalidElements.push(segmentSelect);
        }

        if (!isValid) {
            activateSummaryTabForValidation();
            const firstInvalid = invalidElements[0];
            if (firstInvalid && typeof firstInvalid.focus === 'function') {
                setTimeout(() => {
                    firstInvalid.focus();
                    if (typeof firstInvalid.scrollIntoView === 'function') {
                        firstInvalid.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    }
                }, 50);
            }
        }

        return isValid;
    }

    // Show field error
    function showFieldError(fieldId, message) {
        const errorElement = document.getElementById(fieldId);
        if (errorElement) {
            errorElement.textContent = message;
            errorElement.style.display = 'block';
        }
    }

    // Clear field errors
    function clearFieldErrors() {
        const errorElements = document.querySelectorAll('.field-error');
        errorElements.forEach(element => {
            element.style.display = 'none';
            element.textContent = '';
        });
    }

    // Get currently active tab
    function getCurrentActiveTab() {
        const activeTab = document.querySelector('.tab.active');
        return activeTab ? activeTab.getAttribute('data-tab') : 'view';
    }

    // Save data based on active tab
    async function saveCommittee(closeAfterSave = false) {
        const activeTab = getCurrentActiveTab();
        console.log(`=== SAVING COMMITTEE (active tab: ${activeTab}) ===`);

        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn'), document.getElementById('cancelBtn')];
        const restoreButtons = () => {
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    if (btn.id === 'saveBtn') btn.textContent = 'Save';
                    if (btn.id === 'saveAndCloseBtn') btn.textContent = 'Save & Close';
                }
            });
        };
        buttons.forEach(btn => {
            if (btn) {
                btn.disabled = true;
                if (btn.id === 'saveBtn') btn.textContent = 'Saving...';
                if (btn.id === 'saveAndCloseBtn') btn.textContent = 'Saving...';
            }
        });

        try {
            if (activeTab === 'relationships' || activeTab === 'workflow') {
                showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.noDataToSaveOnTab') : 'No data to save on this tab', false);
                restoreButtons();
                return true;

            } else if (activeTab === 'stakeholders') {
                const stakeholdersHasChanges = window.CommitteeStakeholderEdit &&
                    window.CommitteeStakeholderEdit.hasDataChanged &&
                    window.CommitteeStakeholderEdit.hasDataChanged();
                if (!stakeholdersHasChanges) {
                    showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                    restoreButtons();
                    return true;
                }
                const saveResult = await saveStakeholdersData();
                console.log('Stakeholders save result:', saveResult);

            } else if (activeTab === 'impact') {
                const committeeImpactDirty = typeof window.hasImpactChanges === 'function' && window.hasImpactChanges();
                if (!committeeImpactDirty) {
                    showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                    restoreButtons();
                    return true;
                }
                const impactResult = await window.saveAllImpactData(entityId);
                if (impactResult && impactResult.success !== false) {
                    if (window.initImpactEdit && entityId) { await window.initImpactEdit(entityId); }
                    console.log('✅ Impact saved successfully');
                }

            } else {
                // view (summary) tab
                if (!validateForm()) {
                    showSuccessMessage('Please fix the validation errors before saving.', true);
                    restoreButtons();
                    return false;
                }

                let saveSuccess = await saveCommitteeData();
                if (!saveSuccess) {
                    restoreButtons();
                    return false;
                }

                // If segment changed, reload impact data (do not save impact from summary)
                const currentCommitteeSegment = normalizeCommitteeSegmentId(
                    (segmentField && typeof segmentField.getValue === 'function' ? segmentField.getValue() : null) ??
                    document.getElementById('committeeSegment')?.value
                );
                const committeeSegmentChanged = currentCommitteeSegment !== normalizeCommitteeSegmentId(originalCommitteeSegmentId);
                if (committeeSegmentChanged && window.initImpactEdit) {
                    console.log('=== Segment changed; reloading committee impact from server ===');
                    await window.initImpactEdit(entityId);
                }

                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues && entityId) {
                    try {
                        await window.customFieldsContext.saveValues(parseInt(entityId, 10));
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }

                // Release lock after successful save
                await window.LockInitHelper.releaseLock();
                originalCommitteeSegmentId = currentCommitteeSegment;
            }

            showSuccessMessage('UPDATES SAVED');
            if (closeAfterSave) {
                setTimeout(() => { window.location.href = `/view/committee/${entityId}`; }, 1500);
            }

            return true;
        } catch (error) {
            console.error('Error saving:', error);
            const errorMsg = error?.body?.error || error?.body?.message || error?.message || 'Unknown error';
            showSuccessMessage(errorMsg, true);
            return false;
        } finally {
            restoreButtons();
        }
    }

    // Save committee main data
    async function saveCommitteeData() {
        try {
            // Check if data has changed
            if (!hasDataChanged() && !pendingSegmentNormalization) {
                // Still save custom fields even if main form hasn't changed
                if (window.customFieldsContext && window.customFieldsContext.saveValues && entityId) {
                    try {
                        await window.customFieldsContext.saveValues(parseInt(entityId, 10));
                        console.log('✅ Custom fields saved (no other changes)');
                        showSuccessMessage('UPDATES SAVED');
                    } catch (cfError) {
                        console.error('Error saving custom fields:', cfError);
                    }
                } else {
                    showSuccessMessage('NO CHANGES', true);
                }
                return true;
            }

            // Check for duplicate Primary Name and Ref Number (excluding current committee)
            try {
                const list = await fetch('/api/committee').then(r => r.json());
                const rows = Array.isArray(list) ? list : [];

                const primaryNameVal = document.getElementById('primaryNameInput').value.trim();
                const refNumberVal = document.getElementById('refNumberInput').value.trim();
                const currentId = parseInt(entityId);

                // Check for duplicate Primary Name (case-insensitive, excluding current record)
                const primaryNameClash = rows.some(r => {
                    const recordId = r.ID || r.id;
                    if (recordId === currentId) return false; // Skip current committee
                    const existingPrimaryName = String(r.PrimaryName || r.primaryName || r.name || '').trim().toLowerCase();
                    return existingPrimaryName === primaryNameVal.toLowerCase();
                });
                if (primaryNameClash) {
                    showSuccessMessage('Error: Primary Name already exists. The Primary Name must be unique within Committees.', true);
                    document.getElementById('primaryNameInput').focus();
                    return false;
                }

                // Check for duplicate Ref Number if provided (excluding current record)
                if (refNumberVal && refNumberVal.trim() !== '') {
                    const refNumberClash = rows.some(r => {
                        const recordId = r.ID || r.id;
                        if (recordId === currentId) return false; // Skip current committee
                        const existingRefNumber = String(r.RefNumber || r.refNumber || r.ref || '').trim().toLowerCase();
                        return existingRefNumber === refNumberVal.toLowerCase();
                    });
                    if (refNumberClash) {
                        showSuccessMessage('Error: Reference Number already exists. The Reference Number must be unique within Committees.', true);
                        document.getElementById('refNumberInput').focus();
                        return false;
                    }
                }
            } catch (err) {
                console.warn('Client-side duplicate check failed, falling back to server-side validation:', err);
                // Continue with save - server will validate
            }

            // Get segment field value (try multiple ways like system-interface)
            const segmentFieldContainer = document.getElementById('segmentFieldContainer');
            let segmentId = 1; // Default to Enterprise
            
            const segmentSelect = document.getElementById('committeeSegment');
            if (segmentSelect && segmentSelect.value) {
                const selectValue = parseInt(segmentSelect.value);
                if (!isNaN(selectValue) && selectValue > 0) {
                    segmentId = selectValue;
                }
            }
            
            if (segmentId === 1 && segmentFieldContainer && window.SegmentField) {
                const segmentFieldInstance = segmentFieldContainer._segmentFieldInstance || segmentField;
                if (segmentFieldInstance && typeof segmentFieldInstance.getValue === 'function') {
                    const fieldValue = segmentFieldInstance.getValue();
                    if (fieldValue && fieldValue !== 1) {
                        segmentId = fieldValue;
                    }
                }
            } else if (segmentId === 1 && segmentField && typeof segmentField.getValue === 'function') {
                const fieldValue = segmentField.getValue();
                if (fieldValue && fieldValue !== 1) {
                    segmentId = fieldValue;
                }
            }

            // Sync rich-text editor content back to textarea before reading
            if (typeof syncAdvancedRichTextToTextarea === 'function') {
                syncAdvancedRichTextToTextarea('descriptionInput');
            }

            // Collect form data
            const formData = {
                id: parseInt(entityId),
                primaryName: document.getElementById('primaryNameInput').value.trim(),
                description: document.getElementById('descriptionInput').value.trim(),
                refNumber: document.getElementById('refNumberInput').value.trim() || null,
                parentId: document.getElementById('parentClientSelect').value || null,
                status: parseInt(document.getElementById('statusSelect').value),
                isPublic: parseInt(document.getElementById('viewingSelect').value),
                lifecycle: parseInt(document.getElementById('lifecycleSelect').value),
                classification: parseInt(document.getElementById('classificationSelect').value),
                committeeType: parseInt(document.getElementById('committeeTypeSelect').value),
                lastUpdateUserID: getCurrentUserId(),
                // Backend expects segmentId (camelCase). Keep snake_case for safety.
                segmentId: segmentId,
                segment_id: segmentId
            };

            //('Form data:', formData);

            // Call API to update committee
            const response = await window.BUDG_API_SERVICE.updateCommittee(entityId, formData);

            // Treat {success:false} as failure even if message exists
            const ok = !!response && response.success !== false;
            if (ok) {
                //('Committee updated successfully:', response);
                
                if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                    try {
                        await window.customFieldsContext.saveValues(parseInt(entityId, 10));
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }

                // Save relationships if they exist
                if (window.saveCommitteeRelationshipsData) {
                    try {
                        await window.saveCommitteeRelationshipsData(entityId);
                    } catch (error) {
                        console.error('Error saving relationships:', error);
                    }
                }

                originalData = {
                    primary_name: formData.primaryName,
                    description: formData.description,
                    ref_number: formData.refNumber,
                    parent_id: formData.parentId,
                    status: formData.status,
                    is_public: formData.isPublic,
                    lifecycle: formData.lifecycle,
                    classification: formData.classification,
                    committee_type: formData.committeeType,
                    segment_id: formData.segmentId
                };
                isInitialSnapshotReady = true;
                pendingSegmentNormalization = false;
                
                try {
                    await loadCommitteeData();
                } catch (reloadError) {
                    // Continue even if reload fails
                }
                
                
                return true;
            } else {
                throw new Error(response?.error || 'Failed to update committee');
            }

        } catch (error) {
            console.error('Error saving committee data:', error);
            showSuccessMessage('Error saving committee: ' + (error.message || 'Unknown error'), true);
            return false;
        }
    }

    // Save stakeholders data (updated to use external function)
    async function saveStakeholdersData() {
        if (window.CommitteeStakeholderEdit && window.CommitteeStakeholderEdit.saveStakeholders) {
            return await window.CommitteeStakeholderEdit.saveStakeholders();
        } else {
            alert('Stakeholder edit functionality not available.');
            return false;
        }
    }

    // Check if any data has changed across all tabs
    function hasAnyDataChanged() {
        const activeTab = getCurrentActiveTab();

        switch(activeTab) {
            case 'stakeholders':
                // Check if stakeholders data has changed
                console.log('Checking stakeholders data changes...');
                return window.CommitteeStakeholderEdit && window.CommitteeStakeholderEdit.hasDataChanged ?
                       window.CommitteeStakeholderEdit.hasDataChanged() : false;
            case 'view':
            case 'summary':
                // Check if committee summary/main data has changed
                console.log('Checking committee summary/main data changes...');
                return hasDataChanged();
            default:
                // Default to checking committee data for unknown tabs
                console.warn(`Unknown tab: ${activeTab}, defaulting to committee data change check`);
                return hasDataChanged();
        }
    }

    // Cancel form
    async function cancelForm() {
        if (hasAnyDataChanged()) {
            const confirmMessage = 'You have unsaved changes. Are you sure you want to close without saving?';
            const confirmClose = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
                : Promise.resolve(confirm(confirmMessage)));
            if (confirmClose) {
                await window.LockInitHelper.releaseLock();
                window.location.href = `/view/committee/${entityId}`;
            }
        } else {
            await window.LockInitHelper.releaseLock();
            window.location.href = `/view/committee/${entityId}`;
        }
    }

    // Show success/error message (like product-edit.js)
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
        
        const styleId = 'success-message-style';
        let style = document.getElementById(styleId);
        if (!style) {
            style = document.createElement('style');
            style.id = styleId;
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
        }
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

    // Get current user ID
    function getCurrentUserId() {
     // Try to get from session storage first
        const userStr = sessionStorage.getItem('currentUser');
        if (userStr) {
            try {
                const user = JSON.parse(userStr);
                if (user && (user.id || user.ID || user.userId)) {
                    return user.id || user.ID || user.userId;
                }
            } catch (e) {
                console.error('Error parsing user from session:', e);
            }
        }

        // Fallback to default user ID (should fetch from /api/me in production)
        console.warn('User ID not found in session, using default ID: 1');
        return null;
    }

    // Default value setters for dropdowns
    function setDefaultStatus() {
        const statusSelect = document.getElementById('statusSelect');
        console.log('SetDefaultStatus - StatusSelect found:', !!statusSelect);
        console.log('SetDefaultStatus - Options length:', statusSelect?.options.length);
        if (statusSelect && statusSelect.options.length > 1) {
            const defaultValue = statusSelect.options[1].value;
            console.log('SetDefaultStatus - Setting default value:', defaultValue);
            statusSelect.value = defaultValue; // First non-empty option
            console.log('SetDefaultStatus - Value after setting:', statusSelect.value);
        } else {
            console.warn('SetDefaultStatus - No options available or statusSelect not found');
        }
    }

    function setDefaultViewing() {
        const viewingSelect = document.getElementById('viewingSelect');
        if (viewingSelect && viewingSelect.options.length > 1) {
            viewingSelect.value = viewingSelect.options[1].value; // First non-empty option
        }
    }

    function setDefaultLifecycle() {
        const lifecycleSelect = document.getElementById('lifecycleSelect');
        if (lifecycleSelect && lifecycleSelect.options.length > 1) {
            lifecycleSelect.value = lifecycleSelect.options[1].value; // First non-empty option
        }
    }

    function setDefaultClassification() {
        const classificationSelect = document.getElementById('classificationSelect');
        if (classificationSelect && classificationSelect.options.length > 1) {
            classificationSelect.value = classificationSelect.options[1].value; // First non-empty option
        }
    }

    function setDefaultCommitteeType() {
        const committeeTypeSelect = document.getElementById('committeeTypeSelect');
        if (committeeTypeSelect && committeeTypeSelect.options.length > 1) {
            committeeTypeSelect.value = committeeTypeSelect.options[1].value; // First non-empty option
        }
    }

    // Committee Relationships Tab Functionality
    async function loadCommitteeRelationships(id) {
        const container = document.getElementById('committeeRelationshipsContainer');
        if (!container) return;

        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="relationships-container">
                    <div class="relationships-sub-tabs">
                        <button class="sub-tab active" data-sub-tab="hierarchy">Hierarchy</button>
                        <button class="sub-tab" data-sub-tab="relationships">Relationships</button>
                    </div>
                    <div class="relationships-content">
                        <div id="hierarchyContent" class="sub-tab-content active">
                            <div class="relationships-hierarchy">
                                <div class="hierarchy-header">
                                    <div class="hierarchy-title">COMMITTEE HIERARCHY</div>
                                    <div class="hierarchy-actions">
                                        <label style="display:flex;align-items:center;gap:.5rem;font-size:.85rem;color:var(--text-muted,#6b7280);">
                                            <input type="checkbox" checked style="margin-right:.3rem;">
                                            Show Relationships
                                        </label>
                                        <button type="button" class="btn btn-secondary" id="editBtn2"><i class="fas fa-cog"></i></button>
                                    </div>
                                </div>
                                <table class="hierarchy-table">
                                    <thead>
                                        <tr>
                                            <th><div class="th-content"><span>Committee</span><i class="fas fa-sort"></i></div></th>
                                            <th><div class="th-content"><span>Description</span><i class="fas fa-sort"></i></div></th>
                                        </tr>
                                    </thead>
                                    <tbody id="relationshipsTbody">
                                        <tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>
                                    </tbody>
                                </table>
                                <div class="hierarchy-footer" id="hierarchyFooter">
                                    0 records
                                </div>
                            </div>
                        </div>
                        <div id="relationshipsContent" class="sub-tab-content">
                            <div class="relationships-hierarchy">
                                <div class="hierarchy-header">
                                    <div class="hierarchy-title">RELATIONSHIPS</div>
                                    <div class="hierarchy-actions">
                                        <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i></button>
                                    </div>
                                </div>
                                <div class="data-table-wrapper">
                                    <table class="data-table">
                                        <thead>
                                            <tr>
                                                <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                                <th><div class="th-content"><span>Committee</span><i class="fas fa-sort"></i></div></th>
                                                <th><div class="th-content"><span>Actions</span></div></th>
                                            </tr>
                                        </thead>
                                        <tbody id="relationshipsTableBody">
                                            <tr><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                                <div class="hierarchy-footer" id="relationshipsFooter">
                                    0 records
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Initialize sub-tab switching (scope to this tab only — avoids Impact tab duplicate listeners)
        initCommitteeSubTabs(container);

        // Load only hierarchy data initially (hierarchy subtab is active by default)
        await loadCommitteeHierarchy(id);
        
        // Don't load relationships data here - it will be loaded when user clicks on relationships subtab
    }

    // Initialize sub-tab switching within each .relationships-container (Relationships vs Impact are separate).
    function initCommitteeSubTabs(scopeRoot) {
        const root = scopeRoot || document;
        root.querySelectorAll('.relationships-container').forEach(relContainer => {
            const subTabs = relContainer.querySelectorAll('.relationships-sub-tabs .sub-tab');
            const subTabContents = relContainer.querySelectorAll('.relationships-content .sub-tab-content');

            subTabs.forEach(tab => {
                tab.addEventListener('click', () => {
                    const targetSubTab = tab.getAttribute('data-sub-tab');

                    subTabs.forEach(t => t.classList.remove('active'));
                    subTabContents.forEach(c => c.classList.remove('active'));

                    tab.classList.add('active');

                    let targetContentId;
                    if (targetSubTab === 'hierarchy') {
                        targetContentId = 'hierarchyContent';
                    } else if (targetSubTab === 'relationships') {
                        targetContentId = 'relationshipsContent';
                    } else if (targetSubTab === 'capability') {
                        targetContentId = 'impactCapabilityContent';
                    } else {
                        targetContentId = `${targetSubTab.charAt(0).toUpperCase() + targetSubTab.slice(1)}Content`;
                    }

                    const targetContent = document.getElementById(targetContentId);
                    if (targetContent) {
                        targetContent.classList.add('active');
                    } else {
                        console.error('Target content not found:', targetContentId);
                    }

                    const committeeId = getEntityId();
                    if (committeeId) {
                        if (targetSubTab === 'relationships') {
                            setTimeout(() => {
                                loadCommitteeRelationshipsData(committeeId);
                            }, 100);
                        } else if (targetSubTab === 'hierarchy') {
                            setTimeout(() => {
                                loadCommitteeHierarchy(committeeId);
                            }, 100);
                        }
                    }
                });
            });
        });
    }

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ===== COMMITTEE RELATIONSHIPS FUNCTIONALITY =====

    // Store initial relationship IDs to detect deletions
    let initialCommitteeRelationshipIds = new Set();

    // Load committee relationships data for edit page
    async function loadCommitteeRelationshipsData(id) {
        console.log('loadCommitteeRelationshipsData called with committeeId:', id);
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
        
        console.log('Loading relationships for committee ID:', id);
        
        try {
            // Show loading state
            tbody.innerHTML = '<tr><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>';
            
            // Fetch relationships where this committee is the SOURCE
            const relationships = await window.BUDG_API_SERVICE.getCommitteeRelationshipsBySourceId(id);
            console.log('API Response:', relationships);
            
            // Store initial relationship IDs for change detection
            initialCommitteeRelationshipIds = new Set();
            const relationshipsList = relationships?.data || relationships || [];
            if (Array.isArray(relationshipsList)) {
                relationshipsList.forEach(rel => {
                    if (rel.id) {
                        initialCommitteeRelationshipIds.add(String(rel.id));
                    }
                });
            }
            console.log('Initial relationship IDs stored:', Array.from(initialCommitteeRelationshipIds));
            
            if (!Array.isArray(relationshipsList) || relationshipsList.length === 0) {
                console.log('No relationships found, creating empty row');
                tbody.innerHTML = '';
                // Always append an empty row for adding new entries
                try {
                    console.log('Building empty row for committee:', id);
                    const blankRow = await buildCommitteeRelationshipRow({ committeeId: id, data: null });
                    console.log('Empty row built:', blankRow);
                    if (blankRow) {
                        tbody.appendChild(blankRow);
                        console.log('Empty row added successfully to tbody. Tbody now has', tbody.children.length, 'children');
                        // Force a reflow to ensure the table is visible
                        tbody.offsetHeight;
                    } else {
                        console.error('buildCommitteeRelationshipRow returned null');
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
                const row = await buildCommitteeRelationshipRow({ committeeId: id, data: rel });
                tbody.appendChild(row);
            }
            
            // Populate dropdowns for existing relationships
            await populateExistingCommitteeRelationships(relationshipsList);

            // Always append an empty row for adding new entries
            const blankRow = await buildCommitteeRelationshipRow({ committeeId: id, data: null });
            tbody.appendChild(blankRow);

        } catch (error) {
            console.error('Failed to load relationships:', error);
            tbody.innerHTML = '<tr><td colspan="3" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load relationships: ' + error.message + '</td></tr>';
        }
    }

    // Build committee relationship row
    async function buildCommitteeRelationshipRow({ committeeId, data = null }) {
        console.log('buildCommitteeRelationshipRow called with committeeId:', committeeId, 'data:', data);
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

        // Load relationship types and committee list
        let relationshipTypes = [];
        let committeeList = [];
        
        try {
            const relationshipTypesResponse = await fetch('/api/committee/relation-type/list');
            if (relationshipTypesResponse.ok) {
                relationshipTypes = await relationshipTypesResponse.json();
                console.log('Relationship types loaded:', relationshipTypes.length);
            } else {
                console.warn('Failed to load relationship types:', relationshipTypesResponse.status);
            }
        } catch (error) {
            console.error('Error loading relationship types:', error);
        }
        
        try {
            if (window.BUDG_API_SERVICE && window.BUDG_API_SERVICE.getCommitteeList) {
                committeeList = await window.BUDG_API_SERVICE.getCommitteeList();
            } else {
                const committeeResponse = await fetch('/api/committee');
                if (committeeResponse.ok) {
                    committeeList = await committeeResponse.json();
                }
            }
            console.log('Committee list loaded:', committeeList.length);
        } catch (error) {
            console.error('Error loading committee list:', error);
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

        const committeeSelect = document.createElement('select');
        committeeSelect.className = 'form-select form-select-sm committee-select';
        if (isReverse) {
            committeeSelect.disabled = true;
            committeeSelect.style.backgroundColor = '#f3f4f6';
            committeeSelect.style.cursor = 'not-allowed';
        }
        committeeSelect.innerHTML = '<option value="">Select Committee</option>' +
            (Array.isArray(committeeList) ? committeeList.map(c => {
                const committeeTypeName = c.committeeTypeName || c.CommitteeTypeName || c.typeName || '';
                return `<option value="${c.id}" data-type="${escapeHtml(committeeTypeName)}">${escapeHtml(c.name || c.primaryName || '')}</option>`;
            }).join('') : '');
        const targetCommitteeId = data?.targetCommitteeId || data?.Target_Committee_ID || data?.target || data?.Target;
        if (targetCommitteeId) {
            committeeSelect.value = String(targetCommitteeId);
            committeeSelect.setAttribute('data-original-value', String(targetCommitteeId));
        }

        const relationshipTypeCell = document.createElement('td');
        relationshipTypeCell.appendChild(relationshipTypeSelect);

        const committeeCell = document.createElement('td');
        committeeCell.appendChild(committeeSelect);

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
                    <button type="button" class="btn btn-sm btn-success" onclick="addCommitteeRelationship()" title="Add Row">
                        <i class="fas fa-plus"></i>
                    </button>
                    <button type="button" class="btn btn-sm btn-danger" onclick="removeCommitteeRelationship(this)" title="Remove Row">
                        <i class="fas fa-minus"></i>
                    </button>
                </div>
            `;
        }

        row.appendChild(relationshipTypeCell);
        row.appendChild(committeeCell);
        row.appendChild(actionCell);

        console.log('buildCommitteeRelationshipRow completed, row created:', row);
        return row;
    }

    // Populate existing relationships with data
    async function populateExistingCommitteeRelationships(relationships) {
        try {
            // Load relationship types and committee list
            const [relationshipTypes, committeeList] = await Promise.all([
                fetch('/api/committee/relation-type/list').then(res => res.json()).catch(() => []),
                window.BUDG_API_SERVICE.getCommitteeList ? window.BUDG_API_SERVICE.getCommitteeList() : fetch('/api/committee').then(res => res.json()).catch(() => [])
            ]);

            // Populate each existing relationship row
            relationships.forEach(function(rel) {
                const row = document.querySelector(`tr[data-id="${rel.id}"]`);
                if (!row) return;

                const relationshipSelect = row.querySelector('.relationship-type-select');
                const committeeSelect = row.querySelector('.committee-select');

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

                // Populate committee items
                if (committeeSelect && Array.isArray(committeeList)) {
                    committeeSelect.innerHTML = '<option value="">Select Committee</option>';
                    const relTargetCommitteeId = rel.targetCommitteeId || rel.Target_Committee_ID || rel.target || rel.Target;
                    committeeList.forEach(committee => {
                        const option = document.createElement('option');
                        option.value = committee.id;
                        option.textContent = committee.name || committee.primaryName || '';
                        if (relTargetCommitteeId && String(relTargetCommitteeId) === String(committee.id)) {
                            option.selected = true;
                        }
                        committeeSelect.appendChild(option);
                    });
                    if (relTargetCommitteeId) {
                        committeeSelect.value = String(relTargetCommitteeId);
                        committeeSelect.setAttribute('data-original-value', String(relTargetCommitteeId));
                    }
                }

            });

        } catch (error) {
            console.error('Failed to populate existing relationships:', error);
        }
    }

    // Add new relationship row
    async function addCommitteeRelationship() {
        const tbody = document.getElementById('relationshipsTableBody');
        const committeeId = getEntityId();

        try {
            const newRow = await buildCommitteeRelationshipRow({ committeeId, data: null });
            tbody.appendChild(newRow);
        } catch (error) {
            console.error('Failed to load data for new relationship:', error);
            alert('Failed to load data for new relationship');
        }
    }

    // Remove relationship row
    async function removeCommitteeRelationship(button) {
        const row = button.closest('tr');
        const tbody = document.getElementById('relationshipsTableBody');
        const allRows = tbody.querySelectorAll('tr');
        const isFirstRow = row === allRows[0];
        const id = row.getAttribute('data-id');
        
        if (id) {
            // Delete from database
            try {
                const response = await fetch(`/api/committee/relationship/${id}`, {
                    method: 'DELETE'
                });

                if (response.ok) {
                    console.log('Relationship ID', id, 'deleted. Initial IDs still tracked:', Array.from(initialCommitteeRelationshipIds));
                    
                    if (isFirstRow) {
                        // For first row, only clear the data but keep the row structure
                        await clearCommitteeRelationshipRowData(row);
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
                await clearCommitteeRelationshipRowData(row);
            } else {
                // For other empty rows, remove the entire row
                row.remove();
            }
        }
    }

    // Clear relationship row data
    async function clearCommitteeRelationshipRowData(row) {
        const committeeId = getEntityId();
        const oldId = row.getAttribute('data-id');
        
        console.log('Clearing relationship row data. Old ID:', oldId);

        try {
            const newRow = await buildCommitteeRelationshipRow({ committeeId, data: null });
            row.replaceWith(newRow);
        } catch (error) {
            console.error('Failed to clear row data:', error);
        }
    }

    // Save relationships data to database
    async function saveCommitteeRelationshipsData(committeeId) {
        console.log('=== SAVING COMMITTEE RELATIONSHIPS DATA ===');
        console.log('Committee ID:', committeeId);
        
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
            const committeeSelect = row.querySelector('.committee-select');
            const relationshipTypeId = relationshipTypeSelect?.value?.trim() || '';
            const targetCommitteeId = committeeSelect?.value?.trim() || '';

            // Only save if both relationship type and target committee are selected
            if (relationshipTypeId && targetCommitteeId && relationshipTypeId !== '' && targetCommitteeId !== '') {
                const parsedRelationType = parseInt(relationshipTypeId, 10);
                const parsedTargetCommitteeId = parseInt(targetCommitteeId, 10);
                
                if (isNaN(parsedRelationType) || isNaN(parsedTargetCommitteeId)) {
                    console.error('Cannot save relationship: invalid numeric values');
                    alert('Cannot save relationship: Invalid Relationship Type or Committee ID');
                    continue;
                }
                
                try {
                    console.log('Saving new relationship:', {
                        sourceCommitteeId: committeeId,
                        targetCommitteeId: parsedTargetCommitteeId,
                        relationType: parsedRelationType
                    });
                    
                    const response = await fetch('/api/committee/relationship', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        credentials: 'include',
                        body: JSON.stringify({
                            sourceId: parseInt(committeeId),
                            targetId: parsedTargetCommitteeId,
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
            const committeeSelect = row.querySelector('.committee-select');
            
            const relationshipTypeId = relationshipTypeSelect?.value?.trim() || '';
            const targetCommitteeId = committeeSelect?.value?.trim() || '';
            
            const originalRelationTypeId = relationshipTypeSelect?.getAttribute('data-original-value');
            const originalTargetCommitteeId = committeeSelect?.getAttribute('data-original-value');
            
            // Check if values have changed
            if (relationshipTypeId !== originalRelationTypeId || 
                targetCommitteeId !== originalTargetCommitteeId) {
                
                if (!relationshipTypeId || !targetCommitteeId) {
                    console.warn('Skipping update: missing required fields');
                    continue;
                }
                
                try {
                    console.log('Updating relationship:', {
                        relationshipId: relationshipId,
                        relationType: relationshipTypeId,
                        targetCommitteeId: targetCommitteeId
                    });
                    
                    const response = await fetch(`/api/committee/relationship/${relationshipId}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        credentials: 'include',
                        body: JSON.stringify({
                            relationType: parseInt(relationshipTypeId, 10),
                            targetCommitteeId: parseInt(targetCommitteeId, 10),
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
        
        for (const initialId of initialCommitteeRelationshipIds) {
            if (!currentRelationshipIds.has(initialId)) {
                // This relationship was deleted - already handled in removeCommitteeRelationship
                console.log('Relationship', initialId, 'was deleted (already handled)');
            }
        }

        console.log('Saved', savedCount, 'relationship(s)');
        return { success: true, savedCount: savedCount };
    }

    // Make functions globally available
    window.addCommitteeRelationship = addCommitteeRelationship;
    window.removeCommitteeRelationship = removeCommitteeRelationship;
    window.saveCommitteeRelationshipsData = saveCommitteeRelationshipsData;

    // Build direct lineage tree (current + ancestors + descendants + siblings)
    function buildDirectLineageTree(committees, currentCommitteeId) {
        const byId = new Map();
        committees.forEach(c => byId.set(parseInt(c.id), c));

        const current = byId.get(parseInt(currentCommitteeId));
        if (!current) return [];

        const ancestors = new Set();
        const descendants = new Set();

        // Add current committee
        descendants.add(parseInt(currentCommitteeId));

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

        // Find all descendants (including current committee's children and their descendants)
        function findDescendants(committeeId) {
            committees.forEach(c => {
                if (parseInt(c.parentId) === committeeId) {
                    const childId = parseInt(c.id);
                    descendants.add(childId);
                    findDescendants(childId); // Recursively find all descendants
                }
            });
        }
        findDescendants(parseInt(currentCommitteeId));

        // Find siblings (other children of the same parent) and their descendants
        if (current.parentId) {
            const parentId = parseInt(current.parentId);
            const siblings = committees.filter(c => {
                const cParentId = parseInt(c.parentId);
                const cId = parseInt(c.id);
                return cParentId === parentId && cId !== parseInt(currentCommitteeId);
            });

            // Add siblings and their descendants
            siblings.forEach(sibling => {
                const siblingId = parseInt(sibling.id);
                descendants.add(siblingId);
                findDescendants(siblingId); // Add all descendants of siblings (nephews/nieces and their descendants)
            });
        }

        // Include the current committee, all its ancestors, and all its descendants (including siblings and their descendants)
        const includedIds = new Set([parseInt(currentCommitteeId), ...ancestors, ...descendants]);

        // Filter committees to include only the complete family tree
        return committees.filter(c => {
            const id = parseInt(c.id);
            return includedIds.has(id);
        });
    }

    // Build hierarchy tree structure
    function buildHierarchyTree(committees, rootId) {
        const byId = new Map();
        const byParent = new Map();
        
        committees.forEach(c => {
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

    // Render committee hierarchy table following Regulatory Theme pattern
    function renderCommitteeTable(hierarchyRows, currentId) {
        const Mask = window.HierarchyMask;
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const isMaskedNode = Mask ? Mask.isMasked(node) : false;
            const fallbackName = node.primaryName ?? node.PrimaryName ?? node.Name ?? node.name ?? 'Unnamed Committee';
            const fallbackDesc = node.description ?? node.Description ?? '';
            const name = isMaskedNode ? Mask.PLACEHOLDER : fallbackName;
            const desc = isMaskedNode ? Mask.PLACEHOLDER : fallbackDesc;
            const isCurrent = String(node.id ?? node.ID) === String(currentId);
            const id = node.id ?? node.ID;
            const parentId = node.parentId ?? node.Parent_ID ?? node.parent_id ?? '';
            
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'committee-link current-committee-link' : 'committee-link';
            const link = isMaskedNode
                ? `<span class="${linkClass} masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(name)}</span>`
                : `<a class="${linkClass}" href="/view/committee/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            const rowClasses = `${isCurrent ? 'current-row' : ''}${isMaskedNode ? ' masked-row' : ''}`.trim();
            
            return `<tr class="${rowClasses}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}"${isMaskedNode ? ' data-masked="true"' : ''}>
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-users item-icon"></i><span class="committee-name">${link}</span>${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
            </tr>`;
        }).join('');

        return rowsHtml;
    }

    // Initialize committee hierarchy interactions following Regulatory Theme pattern
    function initCommitteeInteractions(containerEl, hierarchyRows) {
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

    // Load committee hierarchy data
    async function loadCommitteeHierarchy(id) {
        const container = document.querySelector('.relationships-hierarchy');
        if (!container) return;
        
        try {
            container.innerHTML = '<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</div>';
            
            // Fetch all committees from hierarchy endpoint
            console.log('Fetching committees from /api/committee/hierarchy');
            const response = await fetch('/api/committee/hierarchy');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const committees = await response.json();
            
            console.log('Committees loaded for hierarchy:', committees);
            
            if (!Array.isArray(committees) || committees.length === 0) {
                container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No committees found in database</div>';
                return;
            }

            // Build direct lineage tree (current + ancestors + descendants + siblings)
            const filteredCommittees = buildDirectLineageTree(committees, id);
            
            if (filteredCommittees.length === 0) {
                container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No related committees found</div>';
                return;
            }

            // Build hierarchy tree
            const hierarchyRows = buildHierarchyTree(filteredCommittees, 0);
            
            // Render table
            const tableHtml = `
                <div class="hierarchy-header">
                    <div class="hierarchy-title">COMMITTEE HIERARCHY</div>
                    <div class="hierarchy-actions">
                        <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i></button>
                                        </div>
                                    </div>
                <div class="hierarchy-table-wrapper">
                    <table class="hierarchy-table">
                        <thead>
                            <tr>
                                <th>Committee</th>
                                <th>Description</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${renderCommitteeTable(hierarchyRows, id)}
                        </tbody>
                    </table>
                </div>
                <div class="table-footer">
                    ${filteredCommittees.length} record${filteredCommittees.length !== 1 ? 's' : ''}
                </div>
            `;
            
            container.innerHTML = tableHtml;
            
            // Initialize interactions
            initCommitteeInteractions(container, hierarchyRows);
            
        } catch (error) {
            console.error('Failed to load committee hierarchy:', error);
            container.innerHTML = `<div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">Failed to load hierarchy data: ${error.message || 'Unknown error'}</div>`;
        }
    }

    // Add expand/collapse functionality to committee hierarchy
    function addCommitteeExpandCollapseFunctionality() {
        const expandIcons = document.querySelectorAll('.relationships-hierarchy .expand-icon');

        expandIcons.forEach(function(icon) {
            icon.addEventListener('click', function() {
                const groupIndex = this.getAttribute('data-group');
                const parentRow = this.closest('.hierarchy-parent');
                const childRows = document.querySelectorAll(`.hierarchy-row[data-group="${groupIndex}"]`);

                if (this.classList.contains('expanded')) {
                    // Collapse
                    this.classList.remove('expanded');
                    this.classList.add('collapsed');
                    this.className = this.className.replace('fa-chevron-down', 'fa-chevron-right');

                    // Hide child rows
                    childRows.forEach(function(row) {
                        row.style.display = 'none';
                    });
                } else {
                    // Expand
                    this.classList.remove('collapsed');
                    this.classList.add('expanded');
                    this.className = this.className.replace('fa-chevron-right', 'fa-chevron-down');

                    // Show child rows
                    childRows.forEach(function(row) {
                        row.style.display = '';
                    });
                }
            });
        });
    }

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (text == null) return '';
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return text.toString().replace(/[&<>"']/g, function(m) { return map[m]; });
    }
});


