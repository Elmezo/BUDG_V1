/**
 * Document Upload Component
 * Provides file upload, drag-and-drop, and URL entry functionality
 */
(function () {
    function docT(key, fallback) {
        if (typeof window !== 'undefined' && window.I18n && typeof window.I18n.t === 'function') {
            var fullKey = 'documentUpload.' + key;
            var s = window.I18n.t(fullKey);
            if (s && s !== fullKey) return s;
        }
        return fallback;
    }
    window._documentUploadT = docT;
})();

class DocumentUploadComponent {
    constructor(options) {
        this.facetType = options.facetType;
        this.facetId = options.facetId;
        this.container = options.container;
        this.onUploadSuccess = options.onUploadSuccess || (() => { });

        this.init();
    }

    init() {
        this.render();
        this.attachEventListeners();
    }

    render() {
        const html = `
            <div class="document-upload-section">
                <div class="upload-header">
                    <h4 data-i18n="documentUpload.documents">DOCUMENTS</h4>
                </div>
                
                <div class="upload-methods">
                    <div class="upload-method" id="filePickerMethod">
                        <i class="fas fa-file-upload"></i>
                        <span data-i18n="documentUpload.addFilesFromComputer">Add files from computer</span>
                        <input type="file" id="fileInput" style="display: none;" 
                               accept=".jpg,.jpeg,.gif,.png,.txt,.doc,.docx,.rtf,.pdf,.xls,.xlsx,.ppt,.pptx">
                    </div>
                    
                    <div class="upload-method" id="dragDropMethod">
                        <i class="fas fa-hand-pointer"></i>
                        <span data-i18n="documentUpload.dragFilesToUpload">Drag files to upload</span>
                    </div>
                    
                    <div class="upload-method" id="urlMethod">
                        <i class="fas fa-link"></i>
                        <span data-i18n="documentUpload.enterFileUrl">Enter file URL</span>
                    </div>
                </div>
                
                <div id="uploadFormContainer" style="display: none;">
                    <div class="upload-form">
                        <div class="form-group">
                            <label><span data-i18n="documentUpload.selectedFile">Selected File:</span> <span id="selectedFileName"></span></label>
                        </div>
                        
                        <div class="form-group">
                            <label for="docName"><span data-i18n="documentUpload.documentName">Document Name</span> <span class="required">*</span></label>
                            <input type="text" id="docName" class="form-input" placeholder="" data-i18n-placeholder="documentUpload.enterDocNameMin3">
                            <span class="error-message" id="nameError"></span>
                        </div>
                        
                        <div class="form-group">
                            <label for="docDescription"><span data-i18n="documentUpload.documentDescription">Document Description</span> <span class="required">*</span></label>
                            <textarea id="docDescription" class="form-input" rows="3" placeholder="" data-i18n-placeholder="documentUpload.enterDescriptionMin3"></textarea>
                            <span class="error-message" id="descriptionError"></span>
                        </div>
                        
                        <div class="form-group">
                            <label for="docType"><span data-i18n="documentUpload.documentType">Document Type</span> <span class="required">*</span></label>
                            <select id="docType" class="form-input">
                                <option value="" data-i18n="documentUpload.pleaseSelect">Please select</option>
                            </select>
                            <span class="error-message" id="typeError"></span>
                        </div>
                        
                        <div class="form-actions">
                            <button type="button" class="btn btn-primary" id="uploadBtn">Upload</button>
                            <button type="button" class="btn btn-secondary" id="cancelUploadBtn">Cancel</button>
                        </div>
                        
                        <div id="uploadProgress" style="display: none;">
                            <div class="progress-bar">
                                <div class="progress-fill" id="progressFill"></div>
                            </div>
                            <span id="progressText">Uploading...</span>
                        </div>
                        
                        <div id="uploadMessage" class="upload-message"></div>
                    </div>
                </div>
                
                <!-- URL Entry Modal -->
                <div id="urlModal" class="modal" style="display: none;">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h3 data-i18n="documentUpload.addUrlsTitle">Add URLs</h3>
                            <button type="button" class="close-btn" id="closeUrlModal">&times;</button>
                        </div>
                        <div class="modal-body" id="urlModalBody">
                            <!-- URL entries will be added here -->
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-primary" id="addUrlsBtn">Add URLs</button>
                            <button type="button" class="btn btn-secondary" id="cancelUrlBtn">Cancel</button>
                        </div>
                    </div>
                </div>
            </div>
        `;

        const root = document.querySelector(this.container);
        root.innerHTML = html;
        // Placeholders (i18n may not support placeholder via data-i18n on all pages)
        var phName = window._documentUploadT ? window._documentUploadT('enterDocNameMin3', 'Enter document name (min 3 characters)') : 'Enter document name (min 3 characters)';
        var phDesc = window._documentUploadT ? window._documentUploadT('enterDescriptionMin3', 'Enter description (min 3 characters)') : 'Enter description (min 3 characters)';
        var elName = document.getElementById('docName');
        var elDesc = document.getElementById('docDescription');
        if (elName && !elName.placeholder) elName.placeholder = phName;
        if (elDesc && !elDesc.placeholder) elDesc.placeholder = phDesc;
        if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
            window.I18n.applyTranslations(root);
        }
        this.ensureUrlModalAttachedToBody();
        // Load document types
        this.loadDocumentTypes();
    }

    ensureUrlModalAttachedToBody() {
        const modal = document.getElementById('urlModal');
        if (modal && modal.parentElement !== document.body) {
            document.body.appendChild(modal);
        }
    }

    attachEventListeners() {
        // File picker
        document.getElementById('filePickerMethod').addEventListener('click', () => {
            document.getElementById('fileInput').click();
        });

        document.getElementById('fileInput').addEventListener('change', (e) => {
            this.handleFileSelect(e.target.files[0]);
        });

        // Drag and drop (counter avoids dragleave firing when moving over child nodes)
        const dragDropMethod = document.getElementById('dragDropMethod');
        let dragCounter = 0;
        dragDropMethod.addEventListener('dragenter', (e) => {
            e.preventDefault();
            dragCounter++;
            dragDropMethod.classList.add('drag-over');
        });
        dragDropMethod.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'copy';
        });
        dragDropMethod.addEventListener('dragleave', (e) => {
            e.preventDefault();
            dragCounter = Math.max(0, dragCounter - 1);
            if (dragCounter === 0) {
                dragDropMethod.classList.remove('drag-over');
            }
        });
        dragDropMethod.addEventListener('drop', (e) => {
            e.preventDefault();
            dragCounter = 0;
            dragDropMethod.classList.remove('drag-over');
            const files = e.dataTransfer.files;
            if (files.length > 0) {
                this.handleFileSelect(files[0]);
            }
        });

        // URL method
        document.getElementById('urlMethod').addEventListener('click', () => {
            this.showUrlModal();
        });

        // Upload button
        document.getElementById('uploadBtn').addEventListener('click', () => {
            this.uploadFile();
        });

        // Cancel upload
        document.getElementById('cancelUploadBtn').addEventListener('click', () => {
            this.hideUploadForm();
        });

        // URL modal buttons
        document.getElementById('closeUrlModal').addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            this.hideUrlModal();
        });

        document.getElementById('cancelUrlBtn').addEventListener('click', () => {
            this.hideUrlModal();
        });

        document.getElementById('addUrlsBtn').addEventListener('click', () => {
            this.addUrls();
        });
    }

    async loadDocumentTypes() {
        try {
            const select = document.getElementById('docType');
            if (!select) {
                console.warn('Document type select element not found');
                return;
            }

            const response = await fetch('/api/document-types');
            const data = await response.json();

            // Clear all options except the first one using a more reliable method
            // Remove all options except the first "Please select" option
            while (select.options.length > 1) {
                select.remove(1);
            }

            // Add document types - check for duplicates
            const existingValues = new Set();
            existingValues.add(''); // Add empty value to prevent duplicates

            if (data.types && Array.isArray(data.types)) {
                data.types.forEach(type => {
                    // Skip if already exists
                    if (existingValues.has(String(type.id))) {
                        console.warn(`Duplicate document type skipped: ${type.name} (ID: ${type.id})`);
                        return;
                    }
                    
                    const option = document.createElement('option');
                    option.value = type.id;
                    option.textContent = type.name;
                    select.appendChild(option);
                    existingValues.add(String(type.id));
                });
            }
        } catch (error) {
            console.error('Error loading document types:', error);
        }
    }

    handleFileSelect(file) {
        if (!file) return;

        // Validate file type
        const allowedExtensions = ['.jpg', '.jpeg', '.gif', '.png', '.txt', '.doc', '.docx', '.rtf', '.pdf', '.xls', '.xlsx', '.ppt', '.pptx'];
        const fileName = file.name.toLowerCase();
        const hasValidExtension = allowedExtensions.some(ext => fileName.endsWith(ext));

        if (!hasValidExtension) {
            this.showError((window._documentUploadT ? window._documentUploadT('fileTypeNotSupported', 'File type not supported. Allowed types:') : 'File type not supported. Allowed types:') + ' ' + allowedExtensions.join(', '));
            return;
        }

        // Validate filename characters
        if (/[<>,()]/.test(file.name)) {
            this.showError(window._documentUploadT ? window._documentUploadT('fileNameInvalidChars', 'The file name cannot contain the following special characters: < > , ()') : 'The file name cannot contain the following special characters: < > , ()');
            return;
        }

        // Validate file size (10MB)
        if (file.size > 10 * 1024 * 1024) {
            this.showError(window._documentUploadT ? window._documentUploadT('fileSizeExceeded', 'File size exceeds maximum allowed size of 10MB') : 'File size exceeds maximum allowed size of 10MB');
            return;
        }

        this.selectedFile = file;
        document.getElementById('selectedFileName').textContent = file.name;
        this.showUploadForm();
    }

    showUploadForm() {
        document.getElementById('uploadFormContainer').style.display = 'block';
        document.getElementById('uploadMessage').innerHTML = '';
    }

    hideUploadForm() {
        document.getElementById('uploadFormContainer').style.display = 'none';
        document.getElementById('fileInput').value = '';
        document.getElementById('docName').value = '';
        document.getElementById('docDescription').value = '';
        document.getElementById('docType').value = '';
        this.selectedFile = null;
        this.clearErrors();
    }

    async uploadFile() {
        // Validate form
        const name = document.getElementById('docName').value.trim();
        const description = document.getElementById('docDescription').value.trim();
        const documentTypeId = document.getElementById('docType').value;

        this.clearErrors();

        let hasError = false;

        if (!name) {
            document.getElementById('nameError').textContent = window._documentUploadT ? window._documentUploadT('nameRequired', 'You must enter a Document Name.') : 'You must enter a Document Name.';
            hasError = true;
        } else if (name.length < 3) {
            document.getElementById('nameError').textContent = window._documentUploadT ? window._documentUploadT('nameMin3', 'Name must be at least 3 characters long.') : 'Name must be at least 3 characters long.';
            hasError = true;
        }

        if (!description) {
            document.getElementById('descriptionError').textContent = 'You must enter a Document Description.';
            hasError = true;
        } else if (description.length < 3) {
            document.getElementById('descriptionError').textContent = window._documentUploadT ? window._documentUploadT('descriptionMin3', 'Document description must be at least 3 characters long.') : 'Document description must be at least 3 characters long.';
            hasError = true;
        }

        if (!documentTypeId) {
            document.getElementById('typeError').textContent = window._documentUploadT ? window._documentUploadT('typeRequired', 'You must select a Document Type.') : 'You must select a Document Type.';
            hasError = true;
        }

        if (hasError) return;

        // Prepare form data
        const formData = new FormData();
        formData.append('file', this.selectedFile);
        formData.append('facetType', this.facetType);
        formData.append('facetId', this.facetId);
        formData.append('name', name);
        formData.append('description', description);
        formData.append('documentTypeId', documentTypeId);

        // Show progress
        document.getElementById('uploadProgress').style.display = 'block';
        document.getElementById('uploadBtn').disabled = true;

        try {
            const response = await fetch('/api/documents/upload', {
                method: 'POST',
                body: formData
            });

            const data = await response.json();

            if (data.success) {
                this.showSuccess(window._documentUploadT ? window._documentUploadT('uploadSuccess', 'Document uploaded successfully!') : 'Document uploaded successfully!');
                this.hideUploadForm();
                this.onUploadSuccess();
            } else {
                this.showError(data.error || 'Upload failed');
            }
        } catch (error) {
            console.error('Upload error:', error);
            this.showError('Upload failed: ' + error.message);
        } finally {
            document.getElementById('uploadProgress').style.display = 'none';
            document.getElementById('uploadBtn').disabled = false;
        }
    }

    showUrlModal() {
        const modal = document.getElementById('urlModal');
        const modalBody = document.getElementById('urlModalBody');

        // Create 5 URL entry fields
        const t = window._documentUploadT || function (k, f) { return f; };
        const fileUrlTitle = function (n) {
            if (window.I18n && window.I18n.t) {
                var s = window.I18n.t('documentUpload.fileUrlN', { n: n });
                if (s && s.indexOf('documentUpload.') !== 0) return s;
            }
            return 'File ' + n + ' URL';
        };
        let html = '';
        for (let i = 1; i <= 5; i++) {
            html += `
                <div class="url-entry">
                    <h4>${fileUrlTitle(i)}</h4>
                    <div class="form-group">
                        <label>${t('url', 'URL')}</label>
                        <input type="url" class="form-input url-input" data-index="${i}" placeholder="${t('linkToDocument', 'Link to document')}">
                    </div>
                    <div class="form-group">
                        <label>${t('name', 'Name')}</label>
                        <input type="text" class="form-input url-name" data-index="${i}" placeholder="${t('documentNamePlaceholder', 'Document name')}">
                    </div>
                    <div class="form-group">
                        <label>${t('description', 'Description')}</label>
                        <textarea class="form-input url-description" data-index="${i}" rows="2" placeholder="${t('description', 'Description')}"></textarea>
                    </div>
                    <div class="form-group">
                        <label>${t('type', 'Type')}</label>
                        <select class="form-input url-type" data-index="${i}">
                            <option value="">${t('pleaseSelect', 'Please select')}</option>
                        </select>
                    </div>
                </div>
            `;
        }

        modalBody.innerHTML = html;

        // Populate document types in each select
        this.populateUrlDocumentTypes();

        modal.style.display = 'flex';
    }

    hideUrlModal() {
        document.getElementById('urlModal').style.display = 'none';
    }

    async populateUrlDocumentTypes() {
        try {
            const response = await fetch('/api/document-types');
            const data = await response.json();

            document.querySelectorAll('.url-type').forEach(select => {
                if (!select) return;

                // Clear existing options first (except the first "Please select" option)
                while (select.options.length > 1) {
                    select.remove(1);
                }

                // Add document types - check for duplicates
                const existingValues = new Set();
                existingValues.add(''); // Add empty value to prevent duplicates

                if (data.types && Array.isArray(data.types)) {
                    data.types.forEach(type => {
                        // Skip if already exists
                        if (existingValues.has(String(type.id))) {
                            console.warn(`Duplicate document type skipped: ${type.name} (ID: ${type.id})`);
                            return;
                        }
                        
                        const option = document.createElement('option');
                        option.value = type.id;
                        option.textContent = type.name;
                        select.appendChild(option);
                        existingValues.add(String(type.id));
                    });
                }
            });
        } catch (error) {
            console.error('Error loading document types:', error);
        }
    }

    async addUrls() {
        const urls = [];

        // Collect URL entries
        for (let i = 1; i <= 5; i++) {
            const url = document.querySelector(`.url-input[data-index="${i}"]`).value.trim();
            const name = document.querySelector(`.url-name[data-index="${i}"]`).value.trim();
            const description = document.querySelector(`.url-description[data-index="${i}"]`).value.trim();
            const documentTypeId = document.querySelector(`.url-type[data-index="${i}"]`).value;

            if (url && name && description && documentTypeId) {
                urls.push({ url, name, description, documentTypeId: parseInt(documentTypeId) });
            }
        }

        if (urls.length === 0) {
            alert(window._documentUploadT ? window._documentUploadT('fillAtLeastOneUrl', 'Please fill in at least one URL entry') : 'Please fill in at least one URL entry');
            return;
        }

        try {
            const response = await fetch('/api/documents', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    facetType: this.facetType,
                    facetId: this.facetId,
                    urls: urls
                })
            });

            const data = await response.json();

            if (data.success) {
                this.showSuccess(data.message);
                this.hideUrlModal();
                this.onUploadSuccess();
            } else {
                this.showError(data.error || (window._documentUploadT ? window._documentUploadT('failedToAddUrls', 'Failed to add URLs') : 'Failed to add URLs'));
            }
        } catch (error) {
            console.error('Error adding URLs:', error);
            this.showError((window._documentUploadT ? window._documentUploadT('failedToAddUrls', 'Failed to add URLs') : 'Failed to add URLs') + ': ' + error.message);
        }
    }

    clearErrors() {
        document.getElementById('nameError').textContent = '';
        document.getElementById('descriptionError').textContent = '';
        document.getElementById('typeError').textContent = '';
    }

    showError(message) {
        const messageDiv = document.getElementById('uploadMessage');
        messageDiv.className = 'upload-message error';
        messageDiv.textContent = message;
    }

    showSuccess(message) {
        const messageDiv = document.getElementById('uploadMessage');
        messageDiv.className = 'upload-message success';
        messageDiv.textContent = message;
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = DocumentUploadComponent;
}
