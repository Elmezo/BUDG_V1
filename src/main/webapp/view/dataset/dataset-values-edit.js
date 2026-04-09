(function () {
    class DatasetValuesEdit {
        constructor(containerId, datasetId, view = null) {
            this.containerId = containerId;
            this.datasetId = datasetId;
            this.view = view; // View parameter: 'changes' or null (original)
            this.attributes = [];
        }

        notifyUser(message, type = 'error') {
            const msg = message == null ? '' : String(message);
            if (typeof window.showAdminNotification === 'function') {
                window.showAdminNotification(msg, type);
                return;
            }
            if (typeof window.showNotification === 'function') {
                window.showNotification(msg, type);
                return;
            }
            alert(msg);
        }

        async init() {
            const container = document.getElementById(this.containerId);
            if (!container) return;
            const tSafe = (key, fallback) => {
                const translated = window.I18n?.t ? window.I18n.t(key) : null;
                return (!translated || translated === key) ? fallback : translated;
            };

            // Render Layout with Form
            container.innerHTML = `
                <div class="dataset-container">
                    <form id="valuesEditForm">
                        <!-- VALUE UPDATES & AVAILABILITY Card -->
                        <div class="form-card">
                            <div class="card-header">
                                <h3 class="card-title">${window.I18n?.t('datasetValues.titles.valueUpdates') || 'VALUE UPDATES & AVAILABILITY'}</h3>
                            </div>
                            <div class="card-body">
                                <div class="form-group">
                                    <label class="form-label">${window.I18n?.t('datasetValues.labels.valueUpdateFrequency') || 'Value Update Frequency'}</label>
                                    <select class="form-select" id="valFrequency">
                                        <option value="">${window.I18n?.t('datasetValues.options.pleaseSelect') || 'Please select'}</option>
                                        <option value="Daily">${window.I18n?.t('datasetValues.options.daily') || 'Daily'}</option>
                                        <option value="Weekly">${window.I18n?.t('datasetValues.options.weekly') || 'Weekly'}</option>
                                        <option value="Monthly">${window.I18n?.t('datasetValues.options.monthly') || 'Monthly'}</option>
                                        <option value="Quarterly">${window.I18n?.t('datasetValues.options.quarterly') || 'Quarterly'}</option>
                                        <option value="Yearly">${window.I18n?.t('datasetValues.options.yearly') || 'Yearly'}</option>
                                        <option value="Ad-hoc">${window.I18n?.t('datasetValues.options.adHoc') || 'Ad-hoc'}</option>
                                        <option value="Other">${window.I18n?.t('datasetValues.options.other') || 'Other'}</option>
                                    </select>
                                </div>
                                <div class="form-group">
                                    <label class="form-label">${window.I18n?.t('datasetValues.labels.updateFrequencyComments') || 'Update Frequency Comments'}</label>
                                    <textarea class="form-input form-textarea" id="valFreqComments" placeholder="${window.I18n?.t('datasetValues.messages.comment') || 'comment'}"></textarea>
                                    <div class="form-hint" style="margin-top: 0.5rem;">
                                        <a href="#" class="show-editor-link">${window.I18n?.t('datasetValues.buttons.showEditor') || 'Show Editor'}</a>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label class="form-label">${window.I18n?.t('datasetValues.labels.availability') || 'Availability'}</label>
                                    <select class="form-select" id="valAvailability">
                                        <option value="">${window.I18n?.t('datasetValues.options.pleaseSelect') || 'Please select'}</option>
                                        <option value="Via Application UI - record per record">Via Application UI - record per record</option>
                                        <option value="Via Application UI - bulk extract">Via Application UI - bulk extract</option>
                                        <option value="Via Ad-Hoc IT extract">Via Ad-Hoc IT extract</option>
                                        <option value="Via data warehouse">Via data warehouse</option>
                                    </select>
                                </div>
                                <div class="form-group">
                                    <label class="form-label">${window.I18n?.t('datasetValues.labels.availabilityComments') || 'Availability Comments'}</label>
                                    <textarea class="form-input form-textarea" id="valAvailComments" placeholder="${window.I18n?.t('datasetValues.labels.availabilityComments') || 'Availability Comments'}"></textarea>
                                    <div class="form-hint" style="margin-top: 0.5rem;">
                                        <a href="#" class="show-editor-link">${window.I18n?.t('datasetValues.buttons.showEditor') || 'Show Editor'}</a>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label class="form-label">${window.I18n?.t('datasetValues.labels.valuesInBudg') || 'Values in BUDG'}</label>
                                    <select class="form-select" id="valInAxon">
                                        <option value="">${window.I18n?.t('datasetValues.options.pleaseSelect') || 'Please select'}</option>
                                        <option value="Sample Set">${window.I18n?.t('datasetValues.options.sampleSet') || 'Sample Set'}</option>
                                        <option value="false">${window.I18n?.t('datasetValues.options.no') || 'No'}</option>
                                    </select>
                                </div>
                                
                                <div class="form-group" style="margin-top: 20px; border-top: 1px solid #eee; padding-top: 20px;">
                                    <label class="form-label">${tSafe('datasetValues.labels.valueListToUpload', 'Value List to Upload')}</label>
                                    <div style="display: flex; gap: 10px; align-items: center;">
                                        <div style="flex: 1;">
                                            <input type="text" id="valFileDisplay" class="form-input" placeholder="${tSafe('datasetValues.messages.noFileSelected', 'No file selected')}" readonly style="background-color: var(--background-primary);">
                                            <input type="file" id="valFileUpload" accept=".csv,.xlsx,.xls" style="display: none;">
                                        </div>
                                        <button type="button" class="btn btn-secondary" id="btnDownloadTemplate" style="background-color: #000; border-color: #000; color: #fff;">
                                            <i class="fas fa-download"></i> ${tSafe('datasetValues.buttons.downloadTemplate', 'Download Template')}
                                        </button>
                                        <button type="button" class="btn btn-primary" id="btnUploadFile" style="background-color: #ff6b35; border-color: #ff6b35; color: #fff;">
                                            ${tSafe('datasetValues.buttons.chooseFile', 'Choose File')}
                                        </button>
                                    </div>
                                    <div style="margin-top: 10px; display: flex; flex-wrap: wrap; gap: 12px 16px;">
                                        <label style="display: inline-flex; align-items: center; gap: 8px; cursor: pointer;">
                                            <input type="radio" name="uploadType" value="append" checked> ${tSafe('datasetValues.options.append', 'Append')}
                                        </label>
                                        <label style="display: inline-flex; align-items: center; gap: 8px; cursor: pointer;">
                                            <input type="radio" name="uploadType" value="overwrite"> ${tSafe('datasetValues.options.overwrite', 'Overwrite')}
                                        </label>
                                    </div>
                                    <div style="margin-top: 10px; display: grid; gap: 10px;">
                                        <label style="display: inline-flex; align-items: center; gap: 8px; cursor: pointer;">
                                            <input type="radio" name="errorHandling" value="Cancel on Warning" checked>
                                            <span>${tSafe('bulkUpload.step1.cancelOnWarning', 'Cancel on Warning (rollback all changes if any error occurs)')}</span>
                                        </label>
                                        <label style="display: inline-flex; align-items: center; gap: 8px; cursor: pointer;">
                                            <input type="radio" name="errorHandling" value="Continue on Error">
                                            <span>${tSafe('bulkUpload.step1.continueOnError', 'Continue on Error')}</span>
                                        </label>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- CAPTURED VALUES Card -->
                        <div class="form-card">
                            <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
                                <h3 class="card-title">${window.I18n?.t('datasetValues.titles.capturedValues') || 'CAPTURED VALUES'}</h3>
                                <div class="card-actions">
                                    <i class="fas fa-cog" style="cursor: pointer; margin-right: 8px;"></i>
                                    <i class="fas fa-chevron-down" style="cursor: pointer;"></i>
                                </div>
                            </div>
                            <div class="card-body" id="valuesEditSampleSetBody" style="overflow-x: auto;">
                                <i class="fas fa-info-circle"></i> ${window.I18n?.t('datasetValues.messages.noDataAvailable') || 'No data available'}
                            </div>
                        </div>
                    </form>
                </div>
            `;

            // Attach Event Listeners
            document.getElementById('btnDownloadTemplate').addEventListener('click', () => this.downloadTemplate());
            document.getElementById('btnUploadFile').addEventListener('click', () => document.getElementById('valFileUpload').click()); // Re-route to file input
            document.getElementById('valFileUpload').addEventListener('change', (e) => {
                const fileDisplay = document.getElementById('valFileDisplay');
                if (e.target.files && e.target.files[0]) {
                    fileDisplay.value = e.target.files[0].name;
                } else {
                    fileDisplay.value = '';
                }
                this.handleFileUpload(e);
            });

            // Add Show Editor link handlers
            setTimeout(() => {
                const showEditorLinks = document.querySelectorAll('.show-editor-link');
                showEditorLinks.forEach(link => {
                    link.addEventListener('click', (e) => {
                        e.preventDefault();
                        const textareaId = link.closest('.form-group').querySelector('textarea')?.id;
                        if (textareaId) {
                            this.initAdvancedRichTextEditor(textareaId, link);
                        }
                    });
                });
            }, 100);

            await Promise.all([
                this.loadAttributes(),
                this.loadMetadata(),
                this.loadSampleSet()
            ]);
        }

        async loadAttributes() {
            try {
                // Fetch attributes to know what columns are needed for template
                const resp = await window.BUDG_API_SERVICE.get(`/dataset/${this.datasetId}/attributes`);
                const attributes = resp || [];
                
                // Normalize attribute data structure to handle different API response formats
                this.attributes = attributes.map(attr => {
                    // Handle different field name formats from API
                    const name = attr['Name attribute'] || attr.name || attr.PrimaryName || attr['PrimaryName'];
                    const dataType = attr['Data Type attribute'] || attr.dataType || attr.DataType || attr.DataTypeName || attr.typeName;
                    const dataLength = attr['Data Length attribute'] !== undefined ? attr['Data Length attribute'] : 
                                     (attr.dataLength !== undefined ? attr.dataLength : 
                                     (attr.DataLength !== undefined ? attr.DataLength : null));
                    
                    return {
                        ...attr,
                        name: name,
                        PrimaryName: name,
                        dataType: dataType,
                        DataType: dataType,
                        DataTypeName: dataType,
                        typeName: dataType,
                        dataLength: dataLength,
                        DataLength: dataLength
                    };
                });
            } catch (e) {
                console.error('Error loading attributes:', e);
                this.attributes = [];
            }
        }

        async loadMetadata() {
            try {
                const metadata = await this.fetchMetadataFromApi(this.datasetId);

                const setVal = (id, val) => {
                    const el = document.getElementById(id);
                    if (el) el.value = val || '';
                };

                setVal('valFrequency', metadata.frequency);
                setVal('valFreqComments', metadata.frequencyComments);
                setVal('valAvailability', metadata.availability);
                setVal('valAvailComments', metadata.availabilityComments);
                setVal('valInAxon', metadata.valuesInAxon);

            } catch (error) {
                console.error('Error loading value metadata:', error);
            }
        }

        async loadSampleSet() {
            // Same logic as View mode, but maybe different container
            try {
                const sampleRows = await this.fetchSampleValuesFromApi(this.datasetId);
                const container = document.getElementById('valuesEditSampleSetBody');

                if (sampleRows.length === 0) {
                    const noDataText = window.I18n?.t('datasetValues.messages.noDataAvailable') || 'No data available';
                    container.innerHTML = `<div class="empty-state"><i class="fas fa-info-circle"></i> ${noDataText}</div>`;
                    return;
                }

                // Render table (simplified)
                let headers = this.attributes.map(a => {
                    return a.name || a.PrimaryName || a['Name attribute'] || '';
                }).filter(h => h); // Remove empty headers
                if (headers.length === 0 && sampleRows.length > 0) headers = Object.keys(sampleRows[0]);

                if (headers.length === 0) {
                    const noAttributesText = window.I18n?.t('datasetValues.messages.noAttributesDefined') || 'No attributes defined.';
                    container.innerHTML = `<div class="empty-state">${noAttributesText}</div>`;
                    return;
                }

                let html = `
                    <table class="data-table" style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #a8d5ba; border-bottom: 2px solid #8bc4a3;">
                                ${headers.map(h => `<th style="padding: 12px; text-align: left; font-weight: 600; font-size: 0.75rem; color: #2c3e50; text-transform: uppercase;">${this.escapeHtml(h)}</th>`).join('')}
                            </tr>
                        </thead>
                        <tbody>
                            ${sampleRows.map((row, index) => `
                                <tr style="border-bottom: 1px solid #e5e7eb; background-color: ${index % 2 === 0 ? '#ffffff' : '#f8f9fa'};">
                                    ${headers.map(h => `<td style="padding: 12px; font-size: 0.875rem; color: #111827;">${this.escapeHtml(row[h] || row[h.toLowerCase()] || row[h.toUpperCase()] || '-')}</td>`).join('')}
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                    <div style="padding: 10px; text-align: right; color: #6b7280; font-size: 0.8rem;">
                        ${sampleRows.length} ${sampleRows.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}
                    </div>
                `;
                container.innerHTML = html;

            } catch (error) {
                console.error('Error loading sample set:', error);
            }
        }

        downloadTemplate() {
            if (!this.attributes || this.attributes.length === 0) {
                const noAttributesForTemplateText = window.I18n?.t('datasetValues.messages.noAttributesForTemplate') || 'No attributes defined for this dataset. Cannot generate template.';
                this.notifyUser(noAttributesForTemplateText, 'error');
                return;
            }

            // Check if XLSX library is available
            if (typeof XLSX === 'undefined') {
                this.notifyUser('XLSX library not loaded. Please refresh the page.', 'error');
                return;
            }

            // Get attribute names as headers (use normalized name field)
            const headers = this.attributes.map(a => {
                return a.name || a.PrimaryName || a['Name attribute'] || '';
            }).filter(h => h); // Remove empty headers

            // Create workbook and worksheet
            const wb = XLSX.utils.book_new();
            const ws = XLSX.utils.aoa_to_sheet([headers]);

            // Set column widths for better readability
            const colWidths = headers.map(() => ({ wch: 20 }));
            ws['!cols'] = colWidths;

            // Add worksheet to workbook
            XLSX.utils.book_append_sheet(wb, ws, "Values");

            // Generate file and download
            XLSX.writeFile(wb, "BUDG_SourcingTemplate_datastore-values-bulk-upload.xlsx");
        }

        async handleFileUpload(event) {
            const file = event.target.files[0];
            if (!file) return;

            // Show loading state
            const btn = document.getElementById('btnUploadFile');
            btn.innerHTML = `<i class="fas fa-spinner fa-spin"></i> Validating...`;
            btn.disabled = true;

            try {
                // Validate the file
                const validationResult = await this.validateUploadedFile(file);
                
                if (!validationResult.valid) {
                    // Show validation errors
                    this.showValidationErrors(validationResult.errors, validationResult.fileData);
                    this.pendingUploadFile = null;
                    btn.innerHTML = `<i class="fas fa-exclamation-triangle"></i> Validation Failed`;
                    btn.classList.remove('btn-success');
                    btn.classList.add('btn-danger');
                } else {
                    // File is valid, allow upload
                    this.pendingUploadFile = file;
                    this.validatedFileData = validationResult.data;
                    btn.innerHTML = `<i class="fas fa-file-excel"></i> ${file.name}`;
                    btn.classList.remove('btn-danger');
                    btn.classList.add('btn-success');
                }
            } catch (error) {
                console.error('Error validating file:', error);
                const errorReadingFileText = window.I18n?.t('datasetValues.messages.errorReadingFile') || 'Error reading file:';
                this.notifyUser(errorReadingFileText + ' ' + error.message, 'error');
                this.pendingUploadFile = null;
                btn.innerHTML = 'Choose File';
                btn.classList.remove('btn-success', 'btn-danger');
            } finally {
                btn.disabled = false;
            }
        }

        async validateUploadedFile(file) {
            return new Promise((resolve, reject) => {
                // Check if XLSX library is available
                if (typeof XLSX === 'undefined') {
                    reject(new Error('XLSX library not loaded'));
                    return;
                }

                const reader = new FileReader();
                reader.onload = (e) => {
                    try {
                        const data = new Uint8Array(e.target.result);
                        const workbook = XLSX.read(data, { type: 'array' });
                        
                        // Get first worksheet
                        const firstSheetName = workbook.SheetNames[0];
                        const worksheet = workbook.Sheets[firstSheetName];
                        
                        // Convert to JSON
                        const jsonData = XLSX.utils.sheet_to_json(worksheet, { defval: '' });
                        
                        if (jsonData.length === 0) {
                            resolve({
                                valid: false,
                                errors: [{
                                    row: 0,
                                    field: 'File',
                                    message: 'File is empty. Please add data rows.',
                                    data: {}
                                }],
                                fileData: []
                            });
                            return;
                        }

                        // Get expected headers from attributes (use normalized name field)
                        const expectedHeaders = this.attributes.map(a => {
                            return a.name || a.PrimaryName || a['Name attribute'] || '';
                        }).filter(h => h); // Remove empty headers
                        const actualHeaders = Object.keys(jsonData[0] || {});
                        
                        const errors = [];
                        const validatedData = [];
                        
                        // Validate headers
                        const missingHeaders = expectedHeaders.filter(h => !actualHeaders.includes(h));
                        if (missingHeaders.length > 0) {
                            errors.push({
                                row: 0,
                                field: 'Headers',
                                message: `Missing required columns: ${missingHeaders.join(', ')}`,
                                data: {}
                            });
                        }

                        // Validate each row
                        jsonData.forEach((row, index) => {
                            const rowNumber = index + 2; // +2 because Excel rows start at 1 and header is row 1
                            const rowErrors = [];
                            
                            // Check for required fields (if any attribute is marked as required)
                            expectedHeaders.forEach(header => {
                                const attribute = this.attributes.find(a => (a.name || a.PrimaryName) === header);
                                if (attribute) {
                                    const value = row[header];
                                    // Check if field is required (you can add required property to attributes)
                                    if (attribute.required && (!value || value.toString().trim() === '')) {
                                        rowErrors.push({
                                            row: rowNumber,
                                            field: header,
                                            message: `Required field '${header}' is empty`,
                                            data: row
                                        });
                                    }
                                }
                            });

                            if (rowErrors.length > 0) {
                                errors.push(...rowErrors);
                            } else {
                                validatedData.push({
                                    rowNumber: rowNumber,
                                    data: row
                                });
                            }
                        });

                        resolve({
                            valid: errors.length === 0,
                            errors: errors,
                            fileData: jsonData,
                            validatedData: validatedData
                        });
                    } catch (error) {
                        reject(error);
                    }
                };
                
                reader.onerror = () => {
                    reject(new Error('Failed to read file'));
                };
                
                reader.readAsArrayBuffer(file);
            });
        }

        showValidationErrors(errors, fileData) {
            // Remove existing modal if any
            const existingModal = document.getElementById('validationErrorsModal');
            if (existingModal) {
                existingModal.remove();
            }

            // Create modal
            const modal = document.createElement('div');
            modal.id = 'validationErrorsModal';
            modal.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.5);
                display: flex;
                justify-content: center;
                align-items: center;
                z-index: 10000;
            `;

            const modalContent = document.createElement('div');
            modalContent.style.cssText = `
                background: white;
                border-radius: 8px;
                padding: 20px;
                max-width: 90%;
                max-height: 90%;
                overflow: auto;
                box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
            `;

            modalContent.innerHTML = `
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h2 style="margin: 0; color: #d32f2f;">
                        <i class="fas fa-exclamation-triangle"></i> Validation Errors
                    </h2>
                    <button id="closeValidationModal" style="background: none; border: none; font-size: 24px; cursor: pointer; color: #666;">
                        &times;
                    </button>
                </div>
                <p style="margin-bottom: 20px; color: #666;">
                    Found ${errors.length} error(s). Please fix the errors and try again.
                </p>
                <div style="overflow-x: auto;">
                    <table class="data-table" style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background: #f5f5f5;">
                                <th style="padding: 10px; border: 1px solid #ddd; text-align: left;">Row</th>
                                <th style="padding: 10px; border: 1px solid #ddd; text-align: left;">Field</th>
                                <th style="padding: 10px; border: 1px solid #ddd; text-align: left;">Error Message</th>
                                <th style="padding: 10px; border: 1px solid #ddd; text-align: left;">Row Data</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${errors.map(error => `
                                <tr>
                                    <td style="padding: 10px; border: 1px solid #ddd;">${error.row}</td>
                                    <td style="padding: 10px; border: 1px solid #ddd;">${this.escapeHtml(error.field)}</td>
                                    <td style="padding: 10px; border: 1px solid #ddd; color: #d32f2f;">${this.escapeHtml(error.message)}</td>
                                    <td style="padding: 10px; border: 1px solid #ddd;">
                                        <pre style="margin: 0; font-size: 12px; max-width: 300px; overflow-x: auto;">${this.escapeHtml(JSON.stringify(error.data || {}, null, 2))}</pre>
                                    </td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
                <div style="margin-top: 20px; text-align: right;">
                    <button id="closeValidationModalBtn" class="btn btn-primary" style="background-color: #d32f2f; border-color: #d32f2f;">
                        Close
                    </button>
                </div>
            `;

            modal.appendChild(modalContent);
            document.body.appendChild(modal);

            // Close modal handlers
            const closeModal = () => {
                modal.remove();
            };

            document.getElementById('closeValidationModal').addEventListener('click', closeModal);
            document.getElementById('closeValidationModalBtn').addEventListener('click', closeModal);
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    closeModal();
                }
            });
        }

        async saveValues() {
            // 1. Validation for Upload
            if (this.pendingUploadFile) {
                // Check if all attributes have a type defined
                if (this.attributes.length === 0) {
                    this.notifyUser('Cannot upload values: No attributes defined for this dataset.', 'error');
                    throw new Error('No attributes defined');
                }

                // Check if file was validated
                if (!this.validatedFileData) {
                    // Re-validate the file
                    const btn = document.getElementById('btnUploadFile');
                    btn.innerHTML = `<i class="fas fa-spinner fa-spin"></i> Validating...`;
                    btn.disabled = true;

                    try {
                        const validationResult = await this.validateUploadedFile(this.pendingUploadFile);
                        if (!validationResult.valid) {
                            this.showValidationErrors(validationResult.errors, validationResult.fileData);
                            btn.innerHTML = `<i class="fas fa-exclamation-triangle"></i> Validation Failed`;
                            btn.classList.remove('btn-success');
                            btn.classList.add('btn-danger');
                            btn.disabled = false;
                            throw new Error('File validation failed');
                        }
                        this.validatedFileData = validationResult.data;
                    } catch (error) {
                        btn.disabled = false;
                        throw error;
                    }
                }

                // Data type/length validation is now handled by the backend during upload
                // No need to check here - backend will validate and return appropriate error messages
            }

            // 2. Prepare Payload
            const formData = new FormData();

            // Metadata fields
            const metadata = {
                frequency: document.getElementById('valFrequency').value,
                frequencyComments: document.getElementById('valFreqComments').value,
                availability: document.getElementById('valAvailability').value,
                availabilityComments: document.getElementById('valAvailComments').value,
                valuesInAxon: document.getElementById('valInAxon').value
            };

            formData.append('metadata', JSON.stringify(metadata));

            // Upload parameters
            if (this.pendingUploadFile) {
                const uploadType = document.querySelector('input[name="uploadType"]:checked').value; // 'append' or 'overwrite'
                const errorHandling = document.querySelector('input[name="errorHandling"]:checked')?.value || 'Cancel on Warning';
                formData.append('file', this.pendingUploadFile);
                formData.append('uploadType', uploadType);
                formData.append('errorHandling', errorHandling);
            }

            // 3. Send Request with loading indicator
            const btn = document.getElementById('btnUploadFile');
            const originalBtnText = btn ? btn.innerHTML : '';
            if (btn) {
                btn.innerHTML = `<i class="fas fa-spinner fa-spin"></i> Saving...`;
                btn.disabled = true;
            }

            try {
                const resp = await fetch(`/api/dataset-values/${this.datasetId}/save`, {
                    method: 'POST',
                    credentials: 'include',
                    body: formData // Content-Type header auto-set to multipart/form-data
                });

                if (!resp.ok) {
                    let errorMessage = 'Failed to save values. Please check file data and try again.';
                    let jobId = null;
                    let failedCount = null;
                    let cancelledDueToWarning = false;

                    try {
                        const rawText = await resp.text();
                        if (rawText) {
                            try {
                                const errorData = JSON.parse(rawText);
                                errorMessage = errorData.message || errorData.error || errorMessage;
                                jobId = errorData.job_id || null;
                                failedCount = errorData.failed ?? errorData.failed_count ?? null;
                                cancelledDueToWarning = Boolean(errorData.cancelled_due_to_warning);
                            } catch (parseError) {
                                errorMessage = rawText;
                            }
                        }
                    } catch (readError) {
                        // Keep fallback message
                    }

                    if (jobId) {
                        const hasMyJobsHint = /check\s+my\s+jobs\s+report/i.test(errorMessage);
                        const failedPart = Number.isFinite(Number(failedCount))
                            ? ` Failed rows: ${failedCount}.`
                            : '';
                        if (hasMyJobsHint) {
                            errorMessage += ` (Job #${jobId} created.${failedPart})`;
                        } else {
                            errorMessage += ` (Job #${jobId} created. Check My Jobs report.${failedPart})`;
                        }
                    }
                    const requestError = new Error(errorMessage);
                    requestError.cancelledDueToWarning = cancelledDueToWarning;
                    throw requestError;
                }

                const result = await resp.json();

                if (result && result.success === false) {
                    let businessError = result.message || 'Failed to save values.';
                    if (result.job_id) {
                        if (/check\s+my\s+jobs\s+report/i.test(businessError)) {
                            businessError += ` (Job #${result.job_id} created.)`;
                        } else {
                            businessError += ` (Job #${result.job_id} created. Check My Jobs report.)`;
                        }
                    }
                    throw new Error(businessError);
                }

                // Show success message
                if (btn) {
                    btn.innerHTML = `<i class="fas fa-check"></i> Saved Successfully`;
                    btn.classList.remove('btn-danger');
                    btn.classList.add('btn-success');
                    setTimeout(() => {
                        btn.innerHTML = 'Choose File';
                        btn.classList.remove('btn-success');
                    }, 3000);
                }

                // Reset pending upload on success
                this.pendingUploadFile = null;
                this.validatedFileData = null;

                // Reload sample set to show new data
                await this.loadSampleSet();

                return result;
            } catch (error) {
                // Show error message
                if (btn) {
                    btn.innerHTML = `<i class="fas fa-exclamation-triangle"></i> Save Failed`;
                    btn.classList.remove('btn-success');
                    btn.classList.add('btn-danger');
                    setTimeout(() => {
                        btn.innerHTML = originalBtnText;
                        btn.classList.remove('btn-danger');
                    }, 3000);
                }
                this.notifyUser('Error saving values: ' + error.message, 'error');
                if (error && error.cancelledDueToWarning) {
                    setTimeout(() => window.location.reload(), 1200);
                }
                throw error;
            } finally {
                if (btn) {
                    btn.disabled = false;
                }
            }
        }

        initAdvancedRichTextEditor(textareaId, toggleBtn) {
            const textarea = document.getElementById(textareaId);
            if (!textarea) return;

            // If editor already active → destroy and sync back
            const existing = textarea.parentElement.querySelector('.advanced-rte-container');
            if (existing) {
                const editor = existing.querySelector('.advanced-rte-editor');
                textarea.value = editor.innerHTML.trim();
                existing.remove();
                textarea.style.display = '';
                toggleBtn.textContent = 'Show Editor';
                return;
            }

            // Build advanced editor UI
            const container = document.createElement('div');
            container.className = 'advanced-rte-container';
            container.style.cssText = 'border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; width: 100%; max-width: 100%; margin-top: 0.5rem;';

            // Toolbar Row 1
            const toolbarRow1 = document.createElement('div');
            toolbarRow1.className = 'rte-toolbar-row';
            toolbarRow1.style.cssText = 'display: flex; gap: 0.25rem; padding: 0.5rem; background: #f8f9fa; border-bottom: 1px solid #e5e7eb; flex-wrap: wrap;';

            // Magic Wand / Clear Formatting
            const clearFormatBtn = this.createToolbarButton('✨', 'Clear Formatting', () => {
                document.execCommand('removeFormat', false, null);
                editor.focus();
            });
            const clearFormatDropdown = document.createElement('i');
            clearFormatDropdown.className = 'fas fa-chevron-down';
            clearFormatDropdown.style.cssText = 'font-size: 0.6rem; margin-left: 2px;';
            clearFormatBtn.appendChild(clearFormatDropdown);
            toolbarRow1.appendChild(clearFormatBtn);

            // Font Color
            const fontColorBtn = this.createToolbarButton('A', 'Font Color', () => {
                const color = prompt('Enter color (hex or name):', '#000000');
                if (color) document.execCommand('foreColor', false, color);
                editor.focus();
            });
            fontColorBtn.style.cssText += 'color: #ffc107; border-bottom: 2px solid #000;';
            const fontColorDropdown = document.createElement('i');
            fontColorDropdown.className = 'fas fa-chevron-down';
            fontColorDropdown.style.cssText = 'font-size: 0.6rem; margin-left: 2px;';
            fontColorBtn.appendChild(fontColorDropdown);
            toolbarRow1.appendChild(fontColorBtn);

            // Font Family
            const fontFamilySelect = document.createElement('select');
            fontFamilySelect.className = 'rte-select';
            fontFamilySelect.style.cssText = 'padding: 0.35rem 0.5rem; border: 1px solid #e5e7eb; border-radius: 4px; background: #fff; font-size: 0.85rem;';
            ['sans-serif', 'Arial', 'Times New Roman', 'Courier New', 'Verdana'].forEach(font => {
                const option = document.createElement('option');
                option.value = font;
                option.textContent = font;
                if (font === 'sans-serif') option.selected = true;
                fontFamilySelect.appendChild(option);
            });
            fontFamilySelect.addEventListener('change', () => {
                document.execCommand('fontName', false, fontFamilySelect.value);
                editor.focus();
            });
            toolbarRow1.appendChild(fontFamilySelect);

            // Font Size
            const fontSizeSelect = document.createElement('select');
            fontSizeSelect.className = 'rte-select';
            fontSizeSelect.style.cssText = 'padding: 0.35rem 0.5rem; border: 1px solid #e5e7eb; border-radius: 4px; background: #fff; font-size: 0.85rem;';
            ['10', '11', '12', '13', '14', '16', '18', '20', '24', '28', '32'].forEach(size => {
                const option = document.createElement('option');
                option.value = size;
                option.textContent = size;
                if (size === '13') option.selected = true;
                fontSizeSelect.appendChild(option);
            });
            fontSizeSelect.addEventListener('change', () => {
                document.execCommand('fontSize', false, fontSizeSelect.value);
                editor.focus();
            });
            toolbarRow1.appendChild(fontSizeSelect);

            // Text Styling Buttons
            toolbarRow1.appendChild(this.createToolbarButton('B', 'Bold', () => { document.execCommand('bold', false, null); editor.focus(); }));
            toolbarRow1.appendChild(this.createToolbarButton('I', 'Italic', () => { document.execCommand('italic', false, null); editor.focus(); }));
            toolbarRow1.appendChild(this.createToolbarButton('U', 'Underline', () => { document.execCommand('underline', false, null); editor.focus(); }));
            toolbarRow1.appendChild(this.createToolbarButton('S', 'Strikethrough', () => { document.execCommand('strikeThrough', false, null); editor.focus(); }));
            toolbarRow1.appendChild(this.createToolbarButton('X²', 'Superscript', () => { document.execCommand('superscript', false, null); editor.focus(); }));
            toolbarRow1.appendChild(this.createToolbarButton('X₂', 'Subscript', () => { document.execCommand('subscript', false, null); editor.focus(); }));

            // Highlight/Text Background Color
            const highlightBtn = this.createToolbarButton('🖍', 'Highlight', () => {
                const color = prompt('Enter highlight color (hex):', '#ffff00');
                if (color) document.execCommand('backColor', false, color);
                editor.focus();
            });
            toolbarRow1.appendChild(highlightBtn);

            // Lists
            toolbarRow1.appendChild(this.createToolbarButton('•', 'Bulleted List', () => { document.execCommand('insertUnorderedList', false, null); editor.focus(); }));
            toolbarRow1.appendChild(this.createToolbarButton('1.', 'Numbered List', () => { document.execCommand('insertOrderedList', false, null); editor.focus(); }));

            // Alignment
            const alignBtn = this.createToolbarButton('☰', 'Alignment', () => {});
            const alignDropdown = document.createElement('i');
            alignDropdown.className = 'fas fa-chevron-down';
            alignDropdown.style.cssText = 'font-size: 0.6rem; margin-left: 2px;';
            alignBtn.appendChild(alignDropdown);
            toolbarRow1.appendChild(alignBtn);

            // Text Direction/Indentation
            const indentBtn = this.createToolbarButton('T↓', 'Indent', () => {});
            const indentDropdown = document.createElement('i');
            indentDropdown.className = 'fas fa-chevron-down';
            indentDropdown.style.cssText = 'font-size: 0.6rem; margin-left: 2px;';
            indentBtn.appendChild(indentDropdown);
            toolbarRow1.appendChild(indentBtn);

            // Undo/Redo
            toolbarRow1.appendChild(this.createToolbarButton('↶', 'Undo', () => { document.execCommand('undo', false, null); editor.focus(); }));
            toolbarRow1.appendChild(this.createToolbarButton('↷', 'Redo', () => { document.execCommand('redo', false, null); editor.focus(); }));

            // Link
            toolbarRow1.appendChild(this.createToolbarButton('🔗', 'Link', () => {
                const url = prompt('Enter URL:', '');
                if (url) document.execCommand('createLink', false, url);
                editor.focus();
            }));

            // Insert Image
            toolbarRow1.appendChild(this.createToolbarButton('🏔', 'Insert Image', () => {
                const url = prompt('Enter image URL:', '');
                if (url) document.execCommand('insertImage', false, url);
                editor.focus();
            }));

            // Horizontal Rule
            toolbarRow1.appendChild(this.createToolbarButton('─', 'Horizontal Rule', () => { document.execCommand('insertHorizontalRule', false, null); editor.focus(); }));

            // Toolbar Row 2
            const toolbarRow2 = document.createElement('div');
            toolbarRow2.className = 'rte-toolbar-row';
            toolbarRow2.style.cssText = 'display: flex; gap: 0.25rem; padding: 0.5rem; background: #f8f9fa; border-bottom: 1px solid #e5e7eb; flex-wrap: wrap;';

            // Table
            const tableBtn = this.createToolbarButton('⊞', 'Table', () => {
                const rows = prompt('Number of rows:', '3');
                const cols = prompt('Number of columns:', '3');
                if (rows && cols) {
                    let tableHtml = '<table border="1" style="border-collapse: collapse;">';
                    for (let i = 0; i < parseInt(rows); i++) {
                        tableHtml += '<tr>';
                        for (let j = 0; j < parseInt(cols); j++) {
                            tableHtml += '<td>&nbsp;</td>';
                        }
                        tableHtml += '</tr>';
                    }
                    tableHtml += '</table>';
                    document.execCommand('insertHTML', false, tableHtml);
                }
                editor.focus();
            });
            const tableDropdown = document.createElement('i');
            tableDropdown.className = 'fas fa-chevron-down';
            tableDropdown.style.cssText = 'font-size: 0.6rem; margin-left: 2px;';
            tableBtn.appendChild(tableDropdown);
            toolbarRow2.appendChild(tableBtn);

            // Special Characters
            toolbarRow2.appendChild(this.createToolbarButton('%×', 'Special Characters', () => {
                const char = prompt('Enter special character:', '');
                if (char) document.execCommand('insertText', false, char);
                editor.focus();
            }));

            // Mathematical Symbols
            toolbarRow2.appendChild(this.createToolbarButton('Σ', 'Math Symbols', () => {
                const symbol = prompt('Enter math symbol:', '');
                if (symbol) document.execCommand('insertText', false, symbol);
                editor.focus();
            }));

            // Full Screen
            toolbarRow2.appendChild(this.createToolbarButton('⤢', 'Full Screen', () => {
                container.style.width = container.style.width === '100vw' ? '1000px' : '100vw';
                container.style.height = container.style.height === '100vh' ? 'auto' : '100vh';
                editor.focus();
            }));

            // Source Code
            toolbarRow2.appendChild(this.createToolbarButton('</>', 'Source Code', () => {
                const isSourceMode = editor.getAttribute('data-source-mode') === 'true';
                if (isSourceMode) {
                    editor.innerHTML = editor.textContent;
                    editor.removeAttribute('data-source-mode');
                    editor.contentEditable = 'true';
                } else {
                    editor.textContent = editor.innerHTML;
                    editor.setAttribute('data-source-mode', 'true');
                    editor.contentEditable = 'true';
                }
                editor.focus();
            }));

            // Editor Area
            const editor = document.createElement('div');
            editor.className = 'advanced-rte-editor';
            editor.contentEditable = 'true';
            editor.style.cssText = 'min-height: 300px; padding: 0.75rem; outline: none; border: none; background: #fff; overflow-y: auto;';
            if (textarea.value) {
                editor.innerHTML = textarea.value;
            }

            // Footer
            const footer = document.createElement('div');
            footer.className = 'rte-footer';
            footer.style.cssText = 'padding: 0.5rem; border-top: 1px solid #e5e7eb; display: flex; justify-content: flex-end; background: #f8f9fa;';
            const hideLink = document.createElement('a');
            hideLink.href = '#';
            hideLink.textContent = 'Hide editor';
            hideLink.style.cssText = 'color: #6b7280; text-decoration: none; font-size: 0.875rem; cursor: pointer;';
            hideLink.addEventListener('click', (e) => {
                e.preventDefault();
                this.initAdvancedRichTextEditor(textareaId, toggleBtn);
            });
            footer.appendChild(hideLink);

            container.appendChild(toolbarRow1);
            container.appendChild(toolbarRow2);
            container.appendChild(editor);
            container.appendChild(footer);

            textarea.style.display = 'none';
            textarea.parentElement.appendChild(container);
            toggleBtn.textContent = 'Hide editor';
            editor.focus();
        }

        createToolbarButton(icon, title, onClick) {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'rte-btn';
            btn.title = title;
            btn.innerHTML = icon;
            btn.style.cssText = 'padding: 0.35rem 0.5rem; border: 1px solid #e5e7eb; background: #fff; border-radius: 4px; cursor: pointer; font-size: 0.85rem; color: #2c3e50; display: inline-flex; align-items: center;';
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                onClick();
            });
            btn.addEventListener('mouseenter', () => {
                btn.style.background = '#e9ecef';
            });
            btn.addEventListener('mouseleave', () => {
                btn.style.background = '#fff';
            });
            return btn;
        }

        escapeHtml(str) {
            if (str == null) return '';
            return String(str).replace(/[&<>"']/g, function (m) {
                return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[m];
            });
        }

        // Mock API handlers
        async fetchMetadataFromApi(id) {
            try {
                // Build URL with view parameter if provided
                let url = `/api/dataset-values/${id}/metadata`;
                if (this.view === 'changes') {
                    url += '?view=changes';
                }
                // Add cache-busting parameter to ensure fresh data when switching views
                url += (url.includes('?') ? '&' : '?') + '_t=' + new Date().getTime();
                
                const resp = await fetch(url);
                if (resp.ok) return await resp.json();
            } catch (e) { }
            return {
                frequency: '',
                frequencyComments: '',
                availability: '',
                availabilityComments: '',
                valuesInAxon: ''
            };
        }
        
        setView(view) {
            this.view = view;
        }

        async fetchSampleValuesFromApi(id) {
            try {
                const resp = await fetch(`/api/dataset-values/${id}/sample`);
                if (resp.ok) return await resp.json();
            } catch (e) { }
            return [];
        }
    }

    window.DatasetValuesEdit = DatasetValuesEdit;
})();
