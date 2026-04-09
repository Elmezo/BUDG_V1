/**
 * Capability Management
 * Handles capability creation, editing, and form interactions
 */

class CapabilityManager {
    constructor() {
        this.form = document.getElementById('capabilityForm');
        this.primaryNameInput = document.getElementById('primaryNameInput');
        this.parentInput = document.getElementById('parentInput');
        this.parentEditBtn = document.getElementById('parentEditBtn');
        this.referenceInput = document.getElementById('referenceInput');
        this.descriptionInput = document.getElementById('descriptionInput');
        this.budgStatusSelect = document.getElementById('budgStatusSelect');
        this.budgViewingSelect = document.getElementById('budgViewingSelect');
        this.lifecycleSelect = document.getElementById('lifecycleSelect');
        this.classificationSelect = document.getElementById('classificationSelect');
        this.capabilityTypeSelect = document.getElementById('capabilityTypeSelect');
        
        // Action buttons
        this.saveBtn = document.getElementById('saveBtn');
        this.saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
        this.cancelBtn = document.getElementById('cancelBtn');
        
        // Modal elements
        this.parentModal = document.getElementById('parentModal');
        this.modalClose = document.getElementById('modalClose');
        this.modalCloseBtn = document.getElementById('modalCloseBtn');
        this.searchInput = document.getElementById('searchInput');
        this.searchClearBtn = document.getElementById('searchClearBtn');
        
        this.CAPABILITY_CUSTOM_FACET = 'Capabilities';
        this.tableBody = document.getElementById('tableBody');
        this.currentSelection = document.getElementById('currentSelection');
        
        this.selectedParent = null;
        this.capabilities = [];
        this.segmentField = null;
        
        this.init();
    }
    
    async init() {
        console.log('🚀 Initializing CapabilityManager...');
        
        // Fetch current user if not in session
        if (!sessionStorage.getItem('currentUser')) {
            await this.fetchCurrentUser();
        }
        
        // Ensure classification section is visible (stable selector; avoids :has() and fragile [1] index)
        const classificationSection = document.querySelector('.capability-classifications-panel') ||
            (this.classificationSelect && this.classificationSelect.closest('.form-section')) ||
            document.querySelector('.form-section:has(.section-title.classifications)') ||
            document.querySelectorAll('.form-section')[1];
        if (classificationSection) {
            classificationSection.style.display = 'flex';
            classificationSection.style.visibility = 'visible';
            classificationSection.style.opacity = '1';
        }
        
        this.setupEventListeners();
        this.loadCapabilities();
        this.loadDropdownData();
        this.initializeCustomFields();
        
        // Auto-generate ref number if empty
        await this.autoGenerateRefNumber();
        
        console.log('✅ CapabilityManager initialized');
    }
    
    
    setupEventListeners() {
        // Form validation
        this.primaryNameInput.addEventListener('input', () => this.validateForm());
        this.descriptionInput.addEventListener('input', () => this.validateForm());
        this.budgStatusSelect.addEventListener('change', () => this.validateForm());
        this.budgViewingSelect.addEventListener('change', () => this.validateForm());
        this.lifecycleSelect.addEventListener('change', () => this.validateForm());
        this.classificationSelect.addEventListener('change', () => this.validateForm());
        this.capabilityTypeSelect.addEventListener('change', () => this.validateForm());
        
        // Action buttons
        this.saveBtn.addEventListener('click', () => this.saveCapability(true));
        this.saveAndCloseBtn.addEventListener('click', () => this.saveCapability(true));
        this.cancelBtn.addEventListener('click', () => this.cancelForm());
        
        // Parent selection
        this.parentEditBtn.addEventListener('click', () => this.openParentModal());
        this.modalClose.addEventListener('click', () => this.closeParentModal());
        this.modalCloseBtn.addEventListener('click', () => this.closeParentModal());
        
        // Modal search
        this.searchInput.addEventListener('input', () => this.filterCapabilities());
        this.searchClearBtn.addEventListener('click', () => this.clearSearch());
        
        // Close modal on overlay click
        this.parentModal.addEventListener('click', (e) => {
            if (e.target === this.parentModal) {
                this.closeParentModal();
            }
        });
        
        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.closeParentModal();
            }
        });

        // Advanced Rich Text Editor toggle
        const showDescriptionEditorBtn = document.getElementById('showDescriptionEditorBtn');
        if (showDescriptionEditorBtn && this.descriptionInput) {
            showDescriptionEditorBtn.addEventListener('click', () => {
                window.toggleAdvancedRichTextEditor('descriptionInput', showDescriptionEditorBtn);
            });
        }
    }
    
    async loadCapabilities() {
        console.log('🔄 Loading capabilities...');
        try {
            const apiService = new ApiService();
            const selectedSegmentId = this.segmentField && typeof this.segmentField.getValue === 'function'
                ? parseInt(this.segmentField.getValue(), 10)
                : NaN;
            const query = Number.isInteger(selectedSegmentId) && selectedSegmentId > 0
                ? `?segmentId=${encodeURIComponent(selectedSegmentId)}`
                : '';
            const response = await apiService.get(`/capabilities/dropdown${query}`);
            
            console.log('📡 Capabilities API response:', response);
            
            if (response && response.success && response.data) {
                console.log('✅ Capabilities loaded successfully:', response.data);
                this.capabilities = response.data;
                this.populateCapabilitiesTable();
            } else {
                console.error('❌ Failed to load capabilities:', response?.message);
                this.showError(I18n.t('createPage.message.failedToSave'));
            }
        } catch (error) {
            console.error('💥 Error loading capabilities:', error);
            this.showError(I18n.t('createPage.message.failedToSave'));
        }
    }
    
    async loadDropdownData() {
        console.log('🔄 Loading dropdown data...');
        try {
            const apiService = new ApiService();
            
            // Load status options
            console.log('📡 Loading status options...');
            try {
                const statusResponse = await apiService.get('/status/dropdown');
                console.log('📡 Status API response:', statusResponse);
                
                if (statusResponse && statusResponse.success && statusResponse.data) {
                    console.log('✅ Status options loaded:', statusResponse.data);
                    this.populateSelect(this.budgStatusSelect, statusResponse.data);
                }
            } catch (error) {
                console.warn('⚠️ Failed to load status options, using static data:', error);
                // Fallback to static data
                const staticStatusOptions = [
                    { id: 1, primaryName: 'Active' },
                    { id: 2, primaryName: 'Inactive' },
                    { id: 3, primaryName: 'Pending' },
                    { id: 4, primaryName: 'Completed' },
                    { id: 5, primaryName: 'Cancelled' }
                ];
                console.log('📋 Using static status options:', staticStatusOptions);
                this.populateSelect(this.budgStatusSelect, staticStatusOptions);
            }
            
            // Load viewing options
            console.log('📡 Loading viewing options...');
            try {
                const viewingResponse = await apiService.get('/viewing/dropdown');
                console.log('📡 Viewing API response:', viewingResponse);
                
                if (viewingResponse && viewingResponse.success && viewingResponse.data) {
                    console.log('✅ Viewing options loaded:', viewingResponse.data);
                    this.populateSelect(this.budgViewingSelect, viewingResponse.data);
                }
            } catch (error) {
                console.warn('⚠️ Failed to load viewing options, using static data:', error);
                // Fallback to static data
                const staticViewingOptions = [
                    { id: 1, name: 'Public' },
                    { id: 2, name: 'Private' },
                    { id: 3, name: 'Internal' },
                    { id: 4, name: 'Confidential' },
                    { id: 5, name: 'Restricted' }
                ];
                console.log('📋 Using static viewing options:', staticViewingOptions);
                this.populateSelect(this.budgViewingSelect, staticViewingOptions);
            }
            
            // Load lifecycle options
            console.log('📡 Loading lifecycle options...');
            try {
                const lifecycleResponse = await apiService.get('/capability-lifecycles/dropdown');
                console.log('📡 Lifecycle API response:', lifecycleResponse);
                
                if (lifecycleResponse && lifecycleResponse.success && lifecycleResponse.data) {
                    console.log('✅ Lifecycle options loaded:', lifecycleResponse.data);
                    this.populateSelect(this.lifecycleSelect, lifecycleResponse.data);
                }
            } catch (error) {
                console.warn('⚠️ Failed to load lifecycle options, using static data:', error);
                // Fallback to static data
                const staticLifecycleOptions = [
                    { id: 1, primaryName: 'lfc capability' }
                ];
                console.log('📋 Using static lifecycle options:', staticLifecycleOptions);
                this.populateSelect(this.lifecycleSelect, staticLifecycleOptions);
            }
            
            // Load classification options
            console.log('📡 Loading classification options...');
            try {
                const classificationResponse = await apiService.get('/capability-classifications/dropdown');
                console.log('📡 Classification API response:', classificationResponse);
                
                if (classificationResponse && classificationResponse.success && classificationResponse.data) {
                    console.log('✅ Classification options loaded:', classificationResponse.data);
                    this.populateSelect(this.classificationSelect, classificationResponse.data);
                }
            } catch (error) {
                console.warn('⚠️ Failed to load classification options, using static data:', error);
                // Fallback to static data
                const staticClassificationOptions = [
                    { id: 1, primaryName: 'cap1' }
                ];
                console.log('📋 Using static classification options:', staticClassificationOptions);
                this.populateSelect(this.classificationSelect, staticClassificationOptions);
            }
            
            // Load capability type options
            console.log('📡 Loading capability type options...');
            try {
                const capabilityTypeResponse = await apiService.get('/capability-types/dropdown');
                console.log('📡 Capability type API response:', capabilityTypeResponse);
                
                if (capabilityTypeResponse && capabilityTypeResponse.success && capabilityTypeResponse.data) {
                    console.log('✅ Capability type options loaded:', capabilityTypeResponse.data);
                    this.populateSelect(this.capabilityTypeSelect, capabilityTypeResponse.data);
                }
            } catch (error) {
                console.warn('⚠️ Failed to load capability type options, using static data:', error);
                // Fallback to static data
                const staticCapabilityTypeOptions = [
                    { id: 1, primaryName: 'cap type' }
                ];
                console.log('📋 Using static capability type options:', staticCapabilityTypeOptions);
                this.populateSelect(this.capabilityTypeSelect, staticCapabilityTypeOptions);
            }
        } catch (error) {
            console.error('💥 Error loading dropdown data:', error);
        }
    }
    
    populateSelect(selectElement, options) {
        console.log('🔧 Populating select element:', selectElement.id, 'with options:', options);
        
        // Clear ALL existing options
        selectElement.innerHTML = '';
        
        if (Array.isArray(options) && options.length > 0) {
            // Add options from data
            options.forEach((option, index) => {
                const optionElement = document.createElement('option');
                optionElement.value = option.id;
                optionElement.textContent = option.primaryName || option.primaryname || option.name;
                selectElement.appendChild(optionElement);
                
                // Select the first option by default for mandatory fields
                if (index === 0 && (selectElement.id === 'budgStatusSelect' || 
                                    selectElement.id === 'budgViewingSelect' || 
                                    selectElement.id === 'lifecycleSelect' || 
                                    selectElement.id === 'classificationSelect' || 
                                    selectElement.id === 'capabilityTypeSelect')) {
                    optionElement.selected = true;
                }
            });
            
            console.log('✅ Select element populated with', options.length, 'options');
        } else {
            // Add default option if no data
            const defaultOption = document.createElement('option');
            defaultOption.value = '';
            defaultOption.textContent = 'Select an option...';
            selectElement.appendChild(defaultOption);
            
            console.log('⚠️ No options available for select element:', selectElement.id);
        }
    }
    
    populateCapabilitiesTable() {
        this.tableBody.innerHTML = '';
        
        this.capabilities.forEach(capability => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${capability.primaryName || capability.name || 'N/A'}</td>
                <td>${capability.description || 'No description'}</td>
            `;
            
            row.addEventListener('click', () => {
                this.selectParent(capability);
            });
            
            this.tableBody.appendChild(row);
        });
    }
    
    filterCapabilities() {
        const searchTerm = this.searchInput.value.toLowerCase();
        const rows = this.tableBody.querySelectorAll('tr');
        
        rows.forEach(row => {
            const name = row.cells[0].textContent.toLowerCase();
            const description = row.cells[1].textContent.toLowerCase();
            
            if (name.includes(searchTerm) || description.includes(searchTerm)) {
                row.style.display = '';
            } else {
                row.style.display = 'none';
            }
        });
    }
    
    clearSearch() {
        this.searchInput.value = '';
        this.filterCapabilities();
    }
    
    selectParent(capability) {
        // Remove previous selection
        this.tableBody.querySelectorAll('tr').forEach(row => {
            row.classList.remove('selected');
        });
        
        // Add selection to clicked row
        event.target.closest('tr').classList.add('selected');
        
        this.selectedParent = capability;
        this.parentInput.value = capability.primaryName || capability.name || 'N/A';
        this.currentSelection.textContent = capability.primaryName || capability.name || 'N/A';
        
        // Close modal after selection
        setTimeout(() => {
            this.closeParentModal();
        }, 300);
    }
    
    openParentModal() {
        this.parentModal.classList.add('active');
        this.searchInput.focus();
    }
    
    closeParentModal() {
        this.parentModal.classList.remove('active');
    }
    
    validateForm() {
        // Validate custom fields
        if (window.customFieldsContext && window.customFieldsContext.validate) {
            const customFieldsValid = window.customFieldsContext.validate();
            if (!customFieldsValid) {
                return false;
            }
        }
        
        const isValid = this.primaryNameInput.value.trim() !== '' &&
                       this.descriptionInput.value.trim() !== '' &&
                       this.budgStatusSelect.value !== '' &&
                       this.budgViewingSelect.value !== '' &&
                       this.lifecycleSelect.value !== '' &&
                       this.classificationSelect.value !== '' &&
                       this.capabilityTypeSelect.value !== '';
        
        this.saveBtn.disabled = !isValid;
        this.saveAndCloseBtn.disabled = !isValid;
        
        return isValid;
    }
    
    async saveCapability(closeAfterSave = false) {
        // Sync advanced rich text editor content to textarea before saving
        window.syncAdvancedRichTextToTextarea('descriptionInput');

        if (!this.validateForm()) {
            this.showError(I18n.t('createPage.message.required'));
            return;
        }
        
        
        // Get user ID
        const userId = this.getCurrentUserId();
        if (!userId) {
            console.error('❌ No user ID found, attempting to fetch current user...');
            await this.fetchCurrentUser();
            const retryUserId = this.getCurrentUserId();
            if (!retryUserId) {
                this.showError(I18n.t('createPage.message.failedToSave'));
                return;
            }
        }
        
        const capabilityData = {
            primaryName: this.primaryNameInput.value.trim(),
            parentId: this.selectedParent ? parseInt(this.selectedParent.id) : null,
            refNumber: this.referenceInput.value.trim(),
            description: this.descriptionInput.value.trim(),
            status: parseInt(this.budgStatusSelect.value),
            isPublic: parseInt(this.budgViewingSelect.value),
            lifecycle: parseInt(this.lifecycleSelect.value),
            classification: parseInt(this.classificationSelect.value),
            capabilityType: parseInt(this.capabilityTypeSelect.value),
            segmentId: this.segmentField ? this.segmentField.getValue() : 1,
            lastUpdateUserId: userId || this.getCurrentUserId()
        };
        
        // Uniqueness checks: Name and Ref_Number
        try {
            if (typeof window.BUDG_API_SERVICE?.getCapabilities === 'function') {
                const list = await window.BUDG_API_SERVICE.getCapabilities();
                const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                const refVal = String(capabilityData.refNumber || '').trim().toLowerCase();
                
                if (refVal) {
                    const refClash = rows.some(r => String(r.refNumber || r.RefNumber || r.ref || '').trim().toLowerCase() === refVal);
                    if (refClash) {
                        const m = I18n.t('createPage.message.duplicateRefNumber', {facet: 'Capabilities'}); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                        this.referenceInput.focus();
                        return;
                    }
                }
            }
        } catch (_) { /* fall back to server-side validation */ }
        
        try {
            this.setLoadingState(true);
            
            // Make API call using the same pattern as business-area
            const apiService = new ApiService();
            const response = await apiService.post('/capabilities', capabilityData);
            
            if (response && response.success) {
                
                // Get the ID from the response
                const capabilityId = response.data?.id || response.id;
                console.log('Saved capability ID:', capabilityId);
                
                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues && capabilityId != null) {
                    try {
                        await window.customFieldsContext.saveValues(capabilityId);
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }
                
                this.showSuccess(I18n.t('createPage.message.capabilitySaved'));
                
                if (closeAfterSave) {
                    // Show message for 2 seconds then go to view page with ID
                    setTimeout(() => {
                        window.location.href = `/view/capability/${capabilityId}`;
                    }, 2000);
                } else {
                    // For regular Save, just show the success message (already shown above)
                    // Reset form for new entry after showing message
                    setTimeout(() => {
                        this.resetForm();
                    }, 2000);
                }
            } else {
                console.error('Save failed - response:', response);
                const serverMsg = response?.error || response?.message;
                const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Capabilities') : null;
                if (errInfo) {
                    this.showError(I18n.t(errInfo.key, errInfo.params));
                    if (errInfo.focus === 'name') this.primaryNameInput?.focus(); else if (errInfo.focus === 'reference') this.referenceInput?.focus();
                } else {
                    this.showError(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Capabilities' }));
                }
            }
        } catch (error) {
            console.error('Error saving capability:', error);
            const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Capabilities') : null;
            if (errInfo) {
                this.showError(I18n.t(errInfo.key, errInfo.params));
                if (errInfo.focus === 'name') this.primaryNameInput?.focus(); else if (errInfo.focus === 'reference') this.referenceInput?.focus();
            } else {
                this.showError(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Capabilities' }));
            }
        } finally {
            this.setLoadingState(false);
        }
    }
    
    resetForm() {
        this.form.reset();
        this.selectedParent = null;
        this.parentInput.value = '';
        this.currentSelection.textContent = 'None';
        this.validateForm();
    }
    
    cancelForm() {
        if (this.hasUnsavedChanges()) {
            if (confirm(I18n.t('createPage.message.confirmClose'))) {
                this.closeForm();
            }
        } else {
            this.closeForm();
        }
    }
    
    closeForm() {
        // Navigate back or close the form
        if (window.history.length > 1) {
            window.history.back();
        } else {
            window.location.href = '/index.html';
        }
    }
    
    hasUnsavedChanges() {
        return this.primaryNameInput.value.trim() !== '' ||
               this.referenceInput.value.trim() !== '' ||
               this.descriptionInput.value.trim() !== '' ||
               this.selectedParent !== null;
    }
    
    setLoadingState(loading) {
        this.saveBtn.disabled = loading;
        this.saveAndCloseBtn.disabled = loading;
        this.cancelBtn.disabled = loading;
        
        if (loading) {
            this.saveBtn.innerHTML = `<i class="fas fa-spinner fa-spin"></i> ${I18n.t('createPage.message.saving')}`;
            this.saveAndCloseBtn.innerHTML = `<i class="fas fa-spinner fa-spin"></i> ${I18n.t('createPage.message.saving')}`;
        } else {
            this.saveBtn.innerHTML = 'Save';
            this.saveAndCloseBtn.innerHTML = 'Save & Close';
        }
    }
    
    showSuccess(message) {
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
    
    showError(message) {
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
    
    getCurrentUserId() {
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
        console.warn('⚠️ No user ID found, returning null');
        return null;
    }
    
    async fetchCurrentUser() {
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
    
    async autoGenerateRefNumber() {
        // Auto-generate ref number if field is empty
        if (this.referenceInput && (!this.referenceInput.value || this.referenceInput.value.trim() === '')) {
            try {
                const apiService = new ApiService();
                const response = await apiService.get('/capabilities/generate-ref');
                if (response && response.success && response.refNumber) {
                    this.referenceInput.value = response.refNumber;
                    console.log('✅ Auto-generated ref number:', response.refNumber);
                } else {
                    // Fallback: generate client-side pattern
                    const timestamp = Date.now().toString(36).toUpperCase();
                    this.referenceInput.value = `CAP-${timestamp}`;
                    console.log('⚠️ Using fallback ref number:', this.referenceInput.value);
                }
            } catch (error) {
                console.error('Error generating ref number:', error);
                // Fallback: generate client-side pattern
                const timestamp = Date.now().toString(36).toUpperCase();
                this.referenceInput.value = `CAP-${timestamp}`;
            }
        }
    }
    
    async initializeCustomFields() {
        try {
            if (window.CustomFields) {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Capability',
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
        
        // Initialize segment field
        try {
            if (window.SegmentField) {
                this.segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    defaultValue: 1,
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Capability',
                    fieldId: 'capabilitySegment',
                    errorId: 'capabilitySegmentError',
                    onChange: async () => {
                        await this.loadCapabilities();
                        if (!this.selectedParent) return;
                        const selectedId = parseInt(this.selectedParent.id || this.selectedParent.ID, 10);
                        const allowed = new Set(
                            (this.capabilities || [])
                                .map(c => parseInt(c.id || c.ID, 10))
                                .filter(id => Number.isInteger(id) && id > 0)
                        );
                        if (!allowed.has(selectedId)) {
                            this.showError('This parent is not valid for the selected segment. Please remove the parent first.');
                            return false;
                        }
                    }
                });
                console.log('Segment field initialized');
            }
        } catch (error) {
            console.error('Error initializing segment field:', error);
        }
    }
}

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new CapabilityManager();
});

