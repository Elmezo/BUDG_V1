/**
 * Business Area Management - Database Connected Version
 * Handles business area creation, editing, and form interactions
 * Uses API service to connect with database
 * 
 * Data Structure matches business_area table:
 * - PrimaryName -> PrimaryName column
 * - Description -> Description column
 * - ID, Parent_ID, Status, Lifecycle, Is_Public, etc.
 */

class BusinessAreaManager {
    constructor() {
        this.form = document.getElementById('businessAreaForm');
        this.primaryNameInput = document.getElementById('primaryNameInput');
        this.parentInput = document.getElementById('parentInput');
        this.parentEditBtn = document.getElementById('parentEditBtn');
        this.descriptionInput = document.getElementById('descriptionInput');
        this.budgStatusSelect = document.getElementById('budgStatusSelect');
        this.lifecycleSelect = document.getElementById('lifecycleSelect');
        this.budgViewingSelect = document.getElementById('budgViewingSelect');
        
        // Action buttons
        this.saveBtn = document.getElementById('saveBtn');
        this.saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
        this.cancelBtn = document.getElementById('cancelBtn');
        
        // Modal elements
        this.parentModal = document.getElementById('parentModal');
        this.modalClose = document.getElementById('modalClose');
        
        this.BUSINESS_AREA_CUSTOM_FACET = 'Business Area';
        this.modalCloseBtn = document.getElementById('modalCloseBtn');
        this.searchInput = document.getElementById('searchInput');
        this.searchClearBtn = document.getElementById('searchClearBtn');
        this.tableBody = document.getElementById('tableBody');
        this.currentSelection = document.getElementById('currentSelection');
        
        this.selectedParent = null;
        this.businessAreas = [];
        this.customFieldsContext = null;
        this.segmentField = null;
        
        this.init();
    }
    
    async init() {
        console.log('🚀 Initializing BusinessAreaManager...');
        
        // Fetch current user to ensure user ID is available
        await this.fetchCurrentUser();
        
        this.setupEventListeners();
        this.loadBusinessAreas();
        this.loadDropdownData();
        this.initializeCustomFields();
        console.log('✅ BusinessAreaManager initialized');
    }
    
    async initializeCustomFields() {
        console.log('🔄 Initializing custom fields...');
        try {
            if (window.CustomFields) {
                this.customFieldsContext = await window.CustomFields.initForm({
                    facetId: this.BUSINESS_AREA_CUSTOM_FACET,
                    containerId: 'customFieldsContainer',
                    mode: 'create',
                    objectId: null
                });
                console.log('✅ Custom fields initialized:', this.customFieldsContext);
            } else {
                console.warn('⚠️ CustomFields not available');
            }
        } catch (error) {
            console.error('❌ Error initializing custom fields:', error);
        }
        
        // Initialize segment field
        console.log('🔄 Initializing segment field...');
        try {
            if (window.SegmentField) {
                this.segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    defaultValue: 1,
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Business Areas',
                    fieldId: 'baSegment',
                    errorId: 'baSegmentError'
                });
                console.log('✅ Segment field initialized');
                this.bindSegmentFilterEvents();
                await this.loadBusinessAreas();
            } else {
                console.warn('⚠️ SegmentField not available');
            }
        } catch (error) {
            console.error('❌ Error initializing segment field:', error);
        }
    }

    bindSegmentFilterEvents() {
        const segmentSelect = document.getElementById('baSegment');
        if (!segmentSelect || segmentSelect.dataset.parentFilterBound === '1') {
            return;
        }
        segmentSelect.dataset.parentFilterBound = '1';
        segmentSelect.dataset.previousSegmentId = segmentSelect.value || '1';
        segmentSelect.addEventListener('change', async () => {
            const previousSegmentId = parseInt(segmentSelect.dataset.previousSegmentId || segmentSelect.value, 10);
            const requestedSegmentId = parseInt(segmentSelect.value, 10);
            const isValid = await this.handleSegmentChangeForParentFiltering();
            if (isValid === false) {
                if (this.segmentField && Number.isInteger(previousSegmentId) && previousSegmentId > 0) {
                    this.segmentField.setValue(previousSegmentId);
                } else if (Number.isInteger(previousSegmentId) && previousSegmentId > 0) {
                    segmentSelect.value = String(previousSegmentId);
                }
                return;
            }
            if (Number.isInteger(requestedSegmentId) && requestedSegmentId > 0) {
                segmentSelect.dataset.previousSegmentId = String(requestedSegmentId);
            }
        });
    }

    getSegmentCubeRequestedSegmentId() {
        const fromUrl = parseInt(new URLSearchParams(window.location.search).get('segmentId'), 10);
        if (Number.isInteger(fromUrl) && fromUrl > 0) {
            return fromUrl;
        }

        const cube = window.globalSegmentsCubePanel;
        if (!cube || !cube.selectedSegmentIds || typeof cube.selectedSegmentIds.size !== 'number') {
            return null;
        }

        // If user selected one segment in cube, use it as request context.
        if (cube.selectedSegmentIds.size === 1) {
            const [single] = Array.from(cube.selectedSegmentIds);
            const parsed = parseInt(single, 10);
            return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
        }

        return null;
    }

    getActiveParentFilterSegmentId() {
        const selectedSegmentId = this.segmentField && typeof this.segmentField.getValue === 'function'
            ? this.segmentField.getValue()
            : null;
        if (Number.isInteger(selectedSegmentId) && selectedSegmentId > 0) {
            return selectedSegmentId;
        }
        return this.getSegmentCubeRequestedSegmentId();
    }

    async handleSegmentChangeForParentFiltering() {
        await this.loadBusinessAreas();

        if (!this.selectedParent) {
            return true;
        }

        const selectedParentId = parseInt(this.selectedParent.id || this.selectedParent.ID, 10);
        const allowedParentIds = new Set(
            (this.businessAreas || [])
                .map(area => parseInt(area.id || area.ID, 10))
                .filter(id => Number.isInteger(id) && id > 0)
        );

        if (!allowedParentIds.has(selectedParentId)) {
            this.showError('This parent is not valid for the selected segment. Please remove the parent first.');
            this.validateForm();
            return false;
        }
        return true;
    }

    setupEventListeners() {
        // Form validation
        this.primaryNameInput.addEventListener('input', () => this.validateForm());
        this.budgStatusSelect.addEventListener('change', () => this.validateForm());
        this.lifecycleSelect.addEventListener('change', () => this.validateForm());
        this.budgViewingSelect.addEventListener('change', () => this.validateForm());
        
        // Custom fields validation (will be set up after custom fields are initialized)
        setTimeout(() => {
            const customFieldsContainer = document.getElementById('customFieldsContainer');
            if (customFieldsContainer) {
                customFieldsContainer.addEventListener('change', () => this.validateForm());
                customFieldsContainer.addEventListener('input', () => this.validateForm());
            }
        }, 500);
        
        // Action buttons
        this.saveBtn.addEventListener('click', () => this.saveBusinessArea(true));
        this.saveAndCloseBtn.addEventListener('click', () => this.saveBusinessArea(true));
        this.cancelBtn.addEventListener('click', () => this.cancelForm());
        
        // Advanced Rich Text Editor toggle for description
        const showDescriptionEditorBtn = document.getElementById('showDescriptionEditorBtn');
        if (showDescriptionEditorBtn) {
            showDescriptionEditorBtn.addEventListener('click', () => {
                window.toggleAdvancedRichTextEditor('descriptionInput', showDescriptionEditorBtn);
            });
        }

        // Parent selection
        this.parentEditBtn.addEventListener('click', () => this.openParentModal());
        this.modalClose.addEventListener('click', () => this.closeParentModal());
        this.modalCloseBtn.addEventListener('click', () => this.closeParentModal());
        
        // Modal search
        this.searchInput.addEventListener('input', () => this.filterBusinessAreas());
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
    }
    
    async loadBusinessAreas() {
        console.log('🔄 Loading business areas...');
        try {
            const apiService = new ApiService();
            const segmentId = this.getActiveParentFilterSegmentId();
            const response = await apiService.get(
                '/business-areas/dropdown',
                Number.isInteger(segmentId) && segmentId > 0 ? { segmentId } : {}
            );
            
            console.log('📡 Business areas API response:', response);
            
            if (response && response.success && response.data) {
                console.log('✅ Business areas loaded successfully:', response.data);
                this.businessAreas = response.data;
                this.populateBusinessAreasTable();
            } else {
                console.error('❌ Failed to load business areas:', response?.message);
                this.showError(I18n.t('createPage.message.failedToSave'));
            }
        } catch (error) {
            console.error('💥 Error loading business areas:', error);
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
            
            // Load lifecycle options
            console.log('📡 Loading lifecycle options...');
            try {
                const lifecycleResponse = await apiService.get('/business-area-lifecycles/dropdown');
                console.log('📡 Lifecycle API response:', lifecycleResponse);
                
                if (lifecycleResponse && lifecycleResponse.success && lifecycleResponse.data) {
                    console.log('✅ Lifecycle options loaded:', lifecycleResponse.data);
                    this.populateSelect(this.lifecycleSelect, lifecycleResponse.data);
                }
            } catch (error) {
                console.warn('⚠️ Failed to load lifecycle options, using static data:', error);
                // Fallback to static data
                const staticLifecycleOptions = [
                    { id: 1, primaryName: 'BA 1' }
                ];
                console.log('📋 Using static lifecycle options:', staticLifecycleOptions);
                this.populateSelect(this.lifecycleSelect, staticLifecycleOptions);
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
        } catch (error) {
            console.error('Error loading dropdown data:', error);
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
                                    selectElement.id === 'lifecycleSelect')) {
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
    
    populateBusinessAreasTable() {
        this.tableBody.innerHTML = '';
        
        this.businessAreas.forEach(area => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${area.primaryName || 'N/A'}</td>
                <td>${area.description || 'No description'}</td>
            `;
            
            row.addEventListener('click', () => {
                this.selectParent(area);
            });
            
            this.tableBody.appendChild(row);
        });
    }
    
    filterBusinessAreas() {
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
        this.filterBusinessAreas();
    }
    
    selectParent(area) {
        // Remove previous selection
        this.tableBody.querySelectorAll('tr').forEach(row => {
            row.classList.remove('selected');
        });
        
        // Add selection to clicked row
        event.target.closest('tr').classList.add('selected');
        
        this.selectedParent = area;
        this.parentInput.value = area.primaryName || 'N/A';
        this.currentSelection.textContent = area.primaryName || 'N/A';
        
        // Close modal after selection
        setTimeout(() => {
            this.closeParentModal();
        }, 300);
    }
    
    async openParentModal() {
        await this.loadBusinessAreas();
        this.parentModal.classList.add('active');
        this.searchInput.focus();
    }
    
    closeParentModal() {
        this.parentModal.classList.remove('active');
    }
    
    validateForm() {
        const isValid = this.primaryNameInput.value.trim() !== '' &&
                       this.budgStatusSelect.value !== '' &&
                       this.lifecycleSelect.value !== '' &&
                       this.budgViewingSelect.value !== '';
        
        // Validate custom fields
        let customFieldsValid = true;
        if (this.customFieldsContext && this.customFieldsContext.validate) {
            customFieldsValid = this.customFieldsContext.validate();
        }
        
        const finalValid = isValid && customFieldsValid;
        
        this.saveBtn.disabled = !finalValid;
        this.saveAndCloseBtn.disabled = !finalValid;
        
        return finalValid;
    }
    
    async saveBusinessArea(closeAfterSave = false) {
        // Sync advanced rich text editor content to textarea before saving
        window.syncAdvancedRichTextToTextarea('descriptionInput');

        if (!this.validateForm()) {
            this.showError(I18n.t('createPage.message.required'));
            return;
        }

        // Get user ID before saving
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
        
        const businessAreaData = {
            primaryName: this.primaryNameInput.value.trim(),
            parentId: this.selectedParent ? parseInt(this.selectedParent.id) : null,
            description: this.descriptionInput.value.trim(),
            status: parseInt(this.budgStatusSelect.value),
            lifecycle: parseInt(this.lifecycleSelect.value),
            budgViewing: parseInt(this.budgViewingSelect.value),
            segmentId: this.segmentField ? this.segmentField.getValue() : 1,
            createdById: userId,      // User who created the business area
            lastUpdateUserId: userId  // Initially same as creator
        };
        
        try {
            this.setLoadingState(true);
            
        // Make API call using the same pattern as policy
        const apiService = new ApiService();
        const response = await apiService.post('/business-areas', businessAreaData);
        
        if (response && response.success) {
            
            // Get the ID from the response
            const businessAreaId = response.data?.id || response.id;
            console.log('Saved business area ID:', businessAreaId);
            
            // Save custom fields after business area is saved
            try {
                if (this.customFieldsContext && this.customFieldsContext.saveValues) {
                    await this.customFieldsContext.saveValues(businessAreaId);
                    console.log('✅ Custom fields saved successfully');
                }
            } catch (customFieldsError) {
                console.error('❌ Error saving custom fields:', customFieldsError);
                // Don't fail the whole save if custom fields fail
            }
            
            this.showSuccess(I18n.t('createPage.message.businessAreaSaved'));

            if (closeAfterSave) {
                // Show message for 2 seconds then go to view page with ID
                setTimeout(() => {
                    window.location.href = `view/business-area/business-area.html?id=${businessAreaId}`;
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
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Business Areas') : null;
            if (errInfo) {
                this.showError(I18n.t(errInfo.key, errInfo.params));
                if (errInfo.focus === 'name') this.primaryNameInput?.focus();
            } else {
                this.showError(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Business Areas' }));
            }
        }
        } catch (error) {
            console.error('Error saving business area:', error);
            const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Business Areas') : null;
            if (errInfo) {
                this.showError(I18n.t(errInfo.key, errInfo.params));
                if (errInfo.focus === 'name') this.primaryNameInput?.focus();
            } else {
                this.showError(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Business Areas' }));
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
        
        // Reinitialize custom fields for new entry
        this.initializeCustomFields();
        
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
}

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new BusinessAreaManager();
});

