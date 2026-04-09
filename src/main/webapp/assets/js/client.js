// Client Page JavaScript
class ClientManager {
    constructor() {
        this.form = document.getElementById('clientForm');
        this.primaryNameInput = document.getElementById('primaryNameInput');
        this.longNameInput = document.getElementById('longNameInput');
        this.descriptionInput = document.getElementById('descriptionInput');
        this.parentClientSelect = document.getElementById('parentClientSelect');
        this.statusSelect = document.getElementById('statusSelect');
        this.lifecycleSelect = document.getElementById('lifecycleSelect');
        this.viewingSelect = document.getElementById('viewingSelect');
        
        this.saveBtn = document.getElementById('saveBtn');
        this.saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
        this.cancelBtn = document.getElementById('cancelBtn');
        
        this.CLIENT_CUSTOM_FACET = 'Clients';
        
        this.isLoading = false;
        this.isDirty = false;
        this.segmentField = null;
        
        this.init();
    }
    
    init() {
        this.loadReferenceData();
        this.bindEvents();
        this.setupFormValidation();
        this.initializeCustomFields();
    }
    
    async initializeCustomFields() {
        try {
            if (window.CustomFields) {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Client',
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
                    objectType: 'Client',
                    fieldId: 'clientSegment',
                    errorId: 'clientSegmentError',
                    onChange: async () => {
                        const previousParentId = this.parentClientSelect?.value ? parseInt(this.parentClientSelect.value, 10) : null;
                        await this.loadParentClients();
                        if (previousParentId && Number.isInteger(previousParentId)) {
                            const stillAllowed = this.parentClientSelect &&
                                Array.from(this.parentClientSelect.options || [])
                                    .some(opt => parseInt(opt.value, 10) === previousParentId);
                            if (!stillAllowed && this.parentClientSelect) {
                                this.showError('This parent is not valid for the selected segment. Please remove the parent first.');
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
    }
    
    
    bindEvents() {
        // Form input events
        this.form.addEventListener('input', () => {
            this.isDirty = true;
            this.clearErrors();
        });
        
        // Button events
        this.saveBtn.addEventListener('click', () => this.saveClient(true));
        this.saveAndCloseBtn.addEventListener('click', () => this.saveClient(true));
        this.cancelBtn.addEventListener('click', () => this.cancel());
        
        // Form validation
        this.form.addEventListener('submit', (e) => {
            e.preventDefault();
            this.saveClient(false);
        });
        
        // Before unload warning
        window.addEventListener('beforeunload', (e) => {
            if (this.isDirty) {
                e.preventDefault();
                e.returnValue = '';
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
    
    setupFormValidation() {
        // Real-time validation
        this.primaryNameInput.addEventListener('blur', () => this.validateField('primaryNameInput', 'Primary Name'));
        this.longNameInput.addEventListener('blur', () => this.validateField('longNameInput', 'Long Name', false));
        this.descriptionInput.addEventListener('blur', () => this.validateField('descriptionInput', 'Description'));
        // Classification fields are hidden, skip validation
        if (this.statusSelect) {
            this.statusSelect.addEventListener('change', () => this.validateField('statusSelect', 'BUDG Status', false));
        }
        if (this.lifecycleSelect) {
            this.lifecycleSelect.addEventListener('change', () => this.validateField('lifecycleSelect', 'Lifecycle', false));
        }
        if (this.viewingSelect) {
            this.viewingSelect.addEventListener('change', () => this.validateField('viewingSelect', 'BUDG Viewing', false));
        }
    }
    
    async loadReferenceData() {
        try {
            this.setLoading(true);
            
            // Load reference data - skip classification fields as they are hidden
            await Promise.all([
                // this.loadStatuses(),      // Classification section is hidden
                // this.loadLifecycles(),   // Classification section is hidden
                // this.loadViewingOptions(), // Classification section is hidden
                this.loadParentClients()
            ]);
            
        } catch (error) {
            console.error('Error loading reference data:', error);
            this.showError(I18n.t('createPage.message.failedToSave'));
        } finally {
            this.setLoading(false);
        }
    }
    
    async fetchData(endpoint) {
        try {
            const response = await fetch(endpoint, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
                }
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            return data.success ? data.data : [];
        } catch (error) {
            console.error(`Error fetching data from ${endpoint}:`, error);
            throw error;
        }
    }

    // Load statuses from API for dropdown
    async loadStatuses() {
        try {
            if (!this.statusSelect) return;

            this.statusSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;

            const response = await this.fetchData('/api/client/status-list');

            if (response && Array.isArray(response) && response.length > 0) {
                this.statusSelect.innerHTML = '';
                
                response.forEach((status, index) => {
                    const option = document.createElement('option');
                    option.value = status.id;
                    option.textContent = status.name || status.primaryname || status.title;
                    if (index === 0) {
                        option.selected = true; // Select first option as default
                    }
                    this.statusSelect.appendChild(option);
                });
            } else {
                this.statusSelect.innerHTML = `<option value="">${I18n.t('createPage.placeholder.statusNotAvailable')}</option>`;
            }
        } catch (error) {
            console.error('Error loading statuses:', error);
            this.statusSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;
        }
    }

    // Load lifecycles from API for dropdown
    async loadLifecycles() {
        try {
            if (!this.lifecycleSelect) return;

            this.lifecycleSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;

            const response = await this.fetchData('/api/client/lifecycle-list');

            if (response && Array.isArray(response) && response.length > 0) {
                this.lifecycleSelect.innerHTML = '';
                
                response.forEach((lifecycle, index) => {
                    const option = document.createElement('option');
                    option.value = lifecycle.id;
                    option.textContent = lifecycle.name || lifecycle.primaryname || lifecycle.title;
                    if (index === 0) {
                        option.selected = true; // Select first option as default
                    }
                    this.lifecycleSelect.appendChild(option);
                });
            } else {
                this.lifecycleSelect.innerHTML = `<option value="">${I18n.t('createPage.placeholder.lifecycleNotAvailable')}</option>`;
            }
        } catch (error) {
            console.error('Error loading lifecycles:', error);
            this.lifecycleSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;
        }
    }

    // Load viewing options from API for dropdown
    async loadViewingOptions() {
        try {
            if (!this.viewingSelect) return;

            this.viewingSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;

            const response = await this.fetchData('/api/client/viewing-list');

            if (response && Array.isArray(response) && response.length > 0) {
                this.viewingSelect.innerHTML = '';
                
                response.forEach((viewing, index) => {
                    const option = document.createElement('option');
                    option.value = viewing.id;
                    option.textContent = viewing.name || viewing.primaryname || viewing.title;
                    if (index === 0) {
                        option.selected = true; // Select first option as default
                    }
                    this.viewingSelect.appendChild(option);
                });
            } else {
                this.viewingSelect.innerHTML = `<option value="">${I18n.t('createPage.placeholder.viewingNotAvailable')}</option>`;
            }
        } catch (error) {
            console.error('Error loading viewing options:', error);
            this.viewingSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;
        }
    }

    // Load parent clients from API for dropdown
    async loadParentClients() {
        try {
            if (!this.parentClientSelect) return;

            this.parentClientSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;

            const selectedSegmentId = this.segmentField && typeof this.segmentField.getValue === 'function'
                ? parseInt(this.segmentField.getValue(), 10)
                : NaN;
            const response = (window.BUDG_API_SERVICE && typeof window.BUDG_API_SERVICE.getClientParentClients === 'function')
                ? await window.BUDG_API_SERVICE.getClientParentClients(
                    Number.isInteger(selectedSegmentId) && selectedSegmentId > 0 ? { segmentId: selectedSegmentId } : {}
                )
                : await this.fetchData('/api/client/parent-clients');

            if (response && Array.isArray(response) && response.length > 0) {
                this.parentClientSelect.innerHTML = '';
                
                // Add empty option for no parent
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = I18n.t('createPage.placeholder.noParentClient');
                this.parentClientSelect.appendChild(emptyOption);
                
                response.forEach((client) => {
                    const option = document.createElement('option');
                    option.value = client.id;
                    option.textContent = client.name || client.primaryname || client.title;
                    this.parentClientSelect.appendChild(option);
                });
            } else {
                this.parentClientSelect.innerHTML = `<option value="">${I18n.t('createPage.placeholder.noParentClient')}</option>`;
            }
        } catch (error) {
            console.error('Error loading parent clients:', error);
            this.parentClientSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;
        }
    }

    validateField(fieldId, fieldName, isRequired = true) {
        const field = document.getElementById(fieldId);
        const value = field.value.trim();
        
        // Clear previous error
        this.clearFieldError(field);
        
        // Check if field is required and empty
        if (isRequired && !value) {
            this.showFieldError(field, I18n.t('createPage.message.required'));
            return false;
        }
        
        // Additional validation rules
        if (fieldId === 'primaryNameInput' && value && value.length < 2) {
            this.showFieldError(field, 'Primary Name must be at least 2 characters');
            return false;
        }
        
        if (fieldId === 'longNameInput' && value && value.length < 2) {
            this.showFieldError(field, 'Long Name must be at least 2 characters');
            return false;
        }
        
        if (fieldId === 'descriptionInput' && value && value.length < 10) {
            this.showFieldError(field, 'Description must be at least 10 characters');
            return false;
        }
        
        // Mark as valid
        field.classList.add('success');
        return true;
    }
    
    validateForm() {
        // Validate custom fields
        if (window.customFieldsContext && window.customFieldsContext.validate) {
            const customFieldsValid = window.customFieldsContext.validate();
            if (!customFieldsValid) {
                return false;
            }
        }
        
        const fields = [
            { id: 'primaryNameInput', name: 'Primary Name', required: true },
            { id: 'longNameInput', name: 'Long Name', required: false },
            { id: 'descriptionInput', name: 'Description', required: true }
            // Classification fields are hidden, skip validation
            // { id: 'statusSelect', name: 'BUDG Status', required: false },
            // { id: 'lifecycleSelect', name: 'Lifecycle', required: false },
            // { id: 'viewingSelect', name: 'BUDG Viewing', required: false }
        ];
        
        let isValid = true;
        fields.forEach(field => {
            if (!this.validateField(field.id, field.name, field.required)) {
                isValid = false;
            }
        });
        
        return isValid;
    }
    
    showFieldError(field, message) {
        field.classList.add('error');
        
        // Remove existing error message
        const existingError = field.parentNode.querySelector('.error-message');
        if (existingError) {
            existingError.remove();
        }
        
        // Add new error message
        const errorDiv = document.createElement('div');
        errorDiv.className = 'error-message';
        errorDiv.textContent = message;
        field.parentNode.appendChild(errorDiv);
    }
    
    clearFieldError(field) {
        field.classList.remove('error', 'success');
        const errorMessage = field.parentNode.querySelector('.error-message');
        if (errorMessage) {
            errorMessage.remove();
        }
    }
    
    clearErrors() {
        const errorFields = this.form.querySelectorAll('.error');
        errorFields.forEach(field => this.clearFieldError(field));
    }
    
    async saveClient(closeAfterSave = false) {
        // Sync advanced rich text editor content to textarea before saving
        window.syncAdvancedRichTextToTextarea('descriptionInput');

        try {
            // Validate required fields
            if (!this.primaryNameInput || !this.primaryNameInput.value.trim()) {
                const m = I18n.t('createPage.message.primaryNameRequired'); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                this.primaryNameInput?.focus();
                return;
            }

            if (!this.descriptionInput || !this.descriptionInput.value.trim()) {
                alert(I18n.t('createPage.message.descriptionRequired'));
                this.descriptionInput?.focus();
                return;
            }

            // Classification fields are hidden, skip validation
            // if (!this.statusSelect || !this.statusSelect.value) {
            //     alert('BUDG Status is required');
            //     this.statusSelect?.focus();
            //     return;
            // }

            // if (!this.lifecycleSelect || !this.lifecycleSelect.value) {
            //     alert('Lifecycle is required');
            //     this.lifecycleSelect?.focus();
            //     return;
            // }

            // if (!this.viewingSelect || !this.viewingSelect.value) {
            //     alert('BUDG Viewing is required');
            //     this.viewingSelect?.focus();
            //     return;
            // }
            

            // Collect form data
            const formData = {
                primary_name: this.primaryNameInput.value.trim(),
                long_name: this.longNameInput.value.trim() || null,
                description: this.descriptionInput.value.trim(),
                // Classification fields are hidden, use default values
                status: (this.statusSelect && this.statusSelect.value) ? parseInt(this.statusSelect.value) : 1,
                lifecycle: (this.lifecycleSelect && this.lifecycleSelect.value) ? parseInt(this.lifecycleSelect.value) : 1,
                is_public: (this.viewingSelect && this.viewingSelect.value) ? parseInt(this.viewingSelect.value) : 1,
                parent_id: this.parentClientSelect.value ? parseInt(this.parentClientSelect.value) : null,
                segmentId: this.segmentField ? this.segmentField.getValue() : 1
            };

            // Show loading state on buttons
            const buttons = [this.saveBtn, this.saveAndCloseBtn, this.cancelBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = true;
                    btn.textContent = 'Saving...';
                }
            });

            // Call API to save client
            //('Sending form data:', formData);
            const response = await fetch('/api/client', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
                },
                body: JSON.stringify(formData)
            });
            
            //('API response:', response);

            // Handle API response
            if (response.ok) {
                const result = await response.json();
                if (result && (result.success || result.id)) {
                    
                    // Get the ID from the response
                    const id = result?.id || result?.data?.id || result?.clientId || result?.insertId || result?.createdId;
                    
                    // Save custom fields if context exists
                    if (window.customFieldsContext && window.customFieldsContext.saveValues && id != null) {
                        try {
                            await window.customFieldsContext.saveValues(id);
                            console.log('✅ Custom fields saved successfully');
                        } catch (error) {
                            console.error('Error saving custom fields:', error);
                        }
                    }
                    
                    alert(I18n.t('createPage.message.clientSaved'));

                    if (closeAfterSave) {
                        this.isDirty = false;
                        if (id != null) {
                            window.location.href = `/view/client/client.html?id=${encodeURIComponent(id)}`;
                        } else {
                            window.location.href = 'index.html';
                        }
                    } else {
                        this.clearForm();
                    }
                } else {
                    const serverMsg = response?.error || response?.message;
                    const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Clients') : null;
                    if (errInfo) {
                        const m = I18n.t(errInfo.key, errInfo.params); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                        if (errInfo.focus === 'name') this.primaryNameInput?.focus(); else if (errInfo.focus === 'reference' && this.referenceInput) this.referenceInput.focus();
                    } else {
                        const m = I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Clients' }); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                    }
                }
            } else {
                const errorData = await response.json();
                throw new Error(errorData.message || errorData.error || `HTTP error! status: ${response.status}`);
            }

        } catch (error) {
            console.error('Error saving client:', error);
            const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Clients') : null;
            if (errInfo) {
                const m = I18n.t(errInfo.key, errInfo.params); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                if (errInfo.focus === 'name') this.primaryNameInput?.focus(); else if (errInfo.focus === 'reference' && this.referenceInput) this.referenceInput.focus();
            } else {
                let errorMessage = I18n.t('createPage.message.unknownError');
                if (error.message) errorMessage = error.message;
                else if (error.status) errorMessage = I18n.t('createPage.message.serverError', {status: error.status});
                else if (error.name === 'TypeError') errorMessage = I18n.t('createPage.message.networkError');
                const m = I18n.t('createPage.message.errorSaving', {error: errorMessage}); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
            }
        } finally {
            // Reset button states
            const buttons = [this.saveBtn, this.saveAndCloseBtn, this.cancelBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    if (btn === this.saveBtn) btn.textContent = 'Save';
                    if (btn === this.saveAndCloseBtn) btn.textContent = 'Save & Close';
                    if (btn === this.cancelBtn) btn.textContent = 'Close';
                }
            });
        }
    }
    
    resetForm() {
        this.form.reset();
        this.clearErrors();
        this.isDirty = false;
        
        // Reset selects to first option (which is selected by default)
        this.statusSelect.selectedIndex = 0;
        this.lifecycleSelect.selectedIndex = 0;
        this.viewingSelect.selectedIndex = 0;
        this.parentClientSelect.selectedIndex = 0; // "No Parent Client" option
    }

    // Clear form inputs
    clearForm() {
        if (this.primaryNameInput) this.primaryNameInput.value = '';
        if (this.longNameInput) this.longNameInput.value = '';
        if (this.descriptionInput) this.descriptionInput.value = '';
        if (this.statusSelect) this.statusSelect.selectedIndex = 0;
        if (this.lifecycleSelect) this.lifecycleSelect.selectedIndex = 0;
        if (this.viewingSelect) this.viewingSelect.selectedIndex = 0;
        if (this.parentClientSelect) this.parentClientSelect.selectedIndex = 0; // "No Parent Client" option
        this.isDirty = false;
        this.primaryNameInput?.focus();
    }

    // Check if form has any data entered
    checkIfFormHasData() {
        return (this.primaryNameInput && this.primaryNameInput.value.trim()) ||
            (this.longNameInput && this.longNameInput.value.trim()) ||
            (this.descriptionInput && this.descriptionInput.value.trim()) ||
            (this.statusSelect && this.statusSelect.value) ||
            (this.lifecycleSelect && this.lifecycleSelect.value) ||
            (this.viewingSelect && this.viewingSelect.value) ||
            (this.parentClientSelect && this.parentClientSelect.value);
    }
    
    cancel() {
        const hasData = this.checkIfFormHasData();
        if (hasData) {
            const confirmClose = confirm(I18n.t('createPage.message.unsavedChanges'));
            if (confirmClose) {
                window.location.href = './index.html';
            }
        } else {
            window.location.href = './index.html';
        }
    }
    
    setLoading(loading) {
        this.isLoading = loading;
        
        if (loading) {
            document.body.classList.add('loading');
            const buttons = [this.saveBtn, this.saveAndCloseBtn, this.cancelBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = true;
                    btn.textContent = 'Saving...';
                }
            });
        } else {
            document.body.classList.remove('loading');
            const buttons = [this.saveBtn, this.saveAndCloseBtn, this.cancelBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    if (btn === this.saveBtn) btn.textContent = 'Save';
                    if (btn === this.saveAndCloseBtn) btn.textContent = 'Save & Close';
                    if (btn === this.cancelBtn) btn.textContent = 'Close';
                }
            });
        }
    }
    
    showError(message) {
        const m = I18n.t('createPage.message.errorSaving', {error: message}); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
    }
    
    showSuccess(message) {
        if (typeof window.showNotification === 'function') { window.showNotification(message, 'error'); } else { alert(message); }
    }
}

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new ClientManager();
});

