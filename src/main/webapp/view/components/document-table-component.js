/**
 * Document Table Component
 * Displays documents in a table with download and delete functionality
 */
(function () {
    if (typeof window._documentUploadT !== 'function') {
        window._documentUploadT = function (key, fallback) {
            if (window.I18n && typeof window.I18n.t === 'function') {
                var fullKey = 'documentUpload.' + key;
                var s = window.I18n.t(fullKey);
                if (s && s !== fullKey) return s;
            }
            return fallback;
        };
    }
})();

class DocumentTableComponent {
    constructor(options) {
        this.facetType = options.facetType;
        this.facetId = options.facetId;
        this.container = options.container;
        this.canEdit = options.canEdit !== false; // Default to true
        this.viewMode = options.viewMode || null; // 'changes' to show pending documents

        this.init();
    }

    init() {
        this.render();
        this.loadDocuments();
    }

    render() {
        const html = `
            <div class="document-table-section">
                <div class="table-header">
                    <button class="collapse-btn" id="collapseDocumentsBtn">
                        <i class="fas fa-chevron-down"></i> <span data-i18n="documentUpload.documents">DOCUMENTS</span>
                    </button>
                    <span class="record-count" id="documentCount" data-i18n="documentUpload.zeroRecords">0 record</span>
                    ${this.canEdit ? '<button class="settings-btn" id="documentSettingsBtn"><i class="fas fa-cog"></i></button>' : ''}
                </div>
                
                <div class="table-content" id="documentTableContent">
                    <table class="document-table">
                        <thead>
                            <tr>
                                <th data-i18n="documentUpload.tableName">Name</th>
                                <th data-i18n="documentUpload.tableDescription">Description</th>
                                <th data-i18n="documentUpload.tableFile">File</th>
                                <th data-i18n="documentUpload.tableType">Type</th>
                                ${this.canEdit ? '<th data-i18n="documentUpload.tableActions">Actions</th>' : ''}
                            </tr>
                        </thead>
                        <tbody id="documentTableBody">
                            <tr>
                                <td colspan="${this.canEdit ? 5 : 4}" class="no-data" data-i18n="documentUpload.noDocumentsFound">No documents found</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <!-- Edit document modal -->
                <div id="documentEditModal" class="modal" style="display: none;">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h3 data-i18n="documentUpload.editDocument">Edit document</h3>
                            <button type="button" class="close-btn" id="closeDocumentEditModal">&times;</button>
                        </div>
                        <div class="modal-body">
                            <input type="hidden" id="editDocumentId" />
                            <div class="form-group">
                                <label for="editDocName"><span data-i18n="documentUpload.documentNameLabel">Document name</span> <span class="required">*</span></label>
                                <input type="text" id="editDocName" class="form-input" data-i18n-placeholder="documentUpload.enterDocNameMin3" placeholder="Name (min 3 characters)" />
                            </div>
                            <div class="form-group">
                                <label for="editDocDescription"><span data-i18n="documentUpload.descriptionLabel">Description</span> <span class="required">*</span></label>
                                <textarea id="editDocDescription" class="form-input" rows="3" data-i18n-placeholder="documentUpload.enterDescriptionMin3" placeholder="Description (min 3 characters)"></textarea>
                            </div>
                            <div class="form-group">
                                <label for="editDocType"><span data-i18n="documentUpload.documentTypeLabel">Document type</span> <span class="required">*</span></label>
                                <select id="editDocType" class="form-input"></select>
                            </div>
                            <div class="form-group" id="editDocUrlGroup" style="display: none;">
                                <label for="editDocUrl">URL</label>
                                <input type="url" id="editDocUrl" class="form-input" placeholder="https://..." />
                            </div>
                            <div id="editDocumentMessage" class="upload-message" style="display:none;"></div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-primary" id="saveDocumentEditBtn" data-i18n="documentUpload.save">Save</button>
                            <button type="button" class="btn btn-secondary" id="cancelDocumentEditBtn" data-i18n="documentUpload.cancel">Cancel</button>
                        </div>
                    </div>
                </div>
            </div>
        `;

        const root = document.querySelector(this.container);
        root.innerHTML = html;
        if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
            window.I18n.applyTranslations(root);
        }
        this.attachEventListeners();
    }

    attachEventListeners() {
        // Collapse/expand
        document.getElementById('collapseDocumentsBtn').addEventListener('click', () => {
            this.toggleCollapse();
        });
        const closeEdit = () => this.hideEditModal();
        document.getElementById('closeDocumentEditModal').addEventListener('click', (e) => {
            e.preventDefault();
            closeEdit();
        });
        document.getElementById('cancelDocumentEditBtn').addEventListener('click', closeEdit);
        document.getElementById('saveDocumentEditBtn').addEventListener('click', () => this.saveEditDocument());
    }

    toggleCollapse() {
        const content = document.getElementById('documentTableContent');
        const btn = document.getElementById('collapseDocumentsBtn');
        const icon = btn.querySelector('i');

        if (content.style.display === 'none') {
            content.style.display = 'block';
            icon.className = 'fas fa-chevron-down';
        } else {
            content.style.display = 'none';
            icon.className = 'fas fa-chevron-right';
        }
    }

    async loadDocuments() {
        try {
            let url = `/api/documents?facetType=${this.facetType}&facetId=${this.facetId}`;
            if (this.viewMode) {
                url += `&view=${this.viewMode}`;
            }
            const response = await fetch(url);
            const data = await response.json();

            if (data.success) {
                this.displayDocuments(data.documents);
                this.updateRecordCount(data.count);
            }
        } catch (error) {
            console.error('Error loading documents:', error);
        }
    }
    
    /**
     * Set view mode and reload documents
     */
    setViewMode(viewMode) {
        this.viewMode = viewMode;
        this.loadDocuments();
    }

    displayDocuments(documents) {
        const tbody = document.getElementById('documentTableBody');

        if (documents.length === 0) {
            var noDoc = window._documentUploadT ? window._documentUploadT('noDocumentsFound', 'No documents found') : 'No documents found';
            tbody.innerHTML = `<tr><td colspan="${this.canEdit ? 5 : 4}" class="no-data">${this.escapeHtml(noDoc)}</td></tr>`;
            return;
        }

        tbody.innerHTML = documents.map(doc => {
            // Extract filename from URL or use the fileName as is
            let displayName = doc.fileName;
            if (doc.isUrl && doc.filePath) {
                // Try to extract filename from URL
                try {
                    const url = new URL(doc.filePath);
                    const pathname = url.pathname;
                    // Get the last part of the path (filename)
                    const parts = pathname.split('/');
                    const lastPart = parts[parts.length - 1];
                    if (lastPart) {
                        // Decode URL encoding (e.g., %20 -> space)
                        displayName = decodeURIComponent(lastPart);
                    }
                } catch (e) {
                    // If URL parsing fails, try simple extraction
                    const parts = doc.filePath.split('/');
                    const lastPart = parts[parts.length - 1];
                    if (lastPart) {
                        displayName = decodeURIComponent(lastPart);
                    }
                }
            }

            return `
            <tr>
                <td>${this.escapeHtml(doc.name)}</td>
                <td>${this.escapeHtml(doc.description)}</td>
                <td>
                    <a href="#" class="file-link" data-id="${doc.id}" data-is-url="${doc.isUrl}" data-path="${this.escapeHtml(doc.filePath)}">
                        ${this.escapeHtml(displayName)}
                    </a>
                </td>
                <td>${this.escapeHtml(doc.documentType)}</td>
                ${this.canEdit ? `
                    <td>
                        <button type="button" class="edit-btn" data-i18n-title="documentUpload.titleEdit"
                            data-doc="${encodeURIComponent(JSON.stringify({
                                id: doc.id,
                                name: doc.name,
                                description: doc.description,
                                documentTypeId: doc.documentTypeId,
                                isUrl: !!doc.isUrl,
                                filePath: doc.filePath || ''
                            }))}">
                            <i class="fas fa-pen"></i>
                        </button>
                        <button type="button" class="delete-btn" data-id="${doc.id}" data-i18n-title="documentUpload.titleDelete"
                            <i class="fas fa-trash"></i>
                        </button>
                    </td>
                ` : ''}
            </tr>
        `;
        }).join('');

        if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
            var section = document.querySelector(this.container + ' .document-table-section') || document.getElementById('documentTableContent');
            if (section) window.I18n.applyTranslations(section);
        }

        // Attach event listeners to file links
        document.querySelectorAll('.file-link').forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                this.downloadDocument(link.dataset.id, link.dataset.isUrl === 'true', link.dataset.path);
            });
        });

        // Attach event listeners to edit buttons
        if (this.canEdit) {
            document.querySelectorAll('.edit-btn').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    try {
                        const doc = JSON.parse(decodeURIComponent(btn.getAttribute('data-doc')));
                        this.openEditModal(doc);
                    } catch (err) {
                        console.error('Edit document: invalid data', err);
                    }
                });
            });
            document.querySelectorAll('.delete-btn').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    this.deleteDocument(btn.dataset.id);
                });
            });
        }
    }

    async openEditModal(doc) {
        const modal = document.getElementById('documentEditModal');
        document.getElementById('editDocumentId').value = doc.id;
        document.getElementById('editDocName').value = doc.name || '';
        document.getElementById('editDocDescription').value = doc.description || '';
        const urlGroup = document.getElementById('editDocUrlGroup');
        const urlInput = document.getElementById('editDocUrl');
        if (doc.isUrl) {
            urlGroup.style.display = 'block';
            urlInput.value = doc.filePath || '';
        } else {
            urlGroup.style.display = 'none';
            urlInput.value = '';
        }
        document.getElementById('editDocumentMessage').style.display = 'none';
        await this.populateEditDocTypes(doc.documentTypeId);
        modal.style.display = 'flex';
    }

    hideEditModal() {
        document.getElementById('documentEditModal').style.display = 'none';
    }

    async populateEditDocTypes(selectedId) {
        const select = document.getElementById('editDocType');
        while (select.options.length) select.remove(0);
        select.add(new Option(window._documentUploadT ? window._documentUploadT('pleaseSelect', 'Please select') : 'Please select', ''));
        try {
            const response = await fetch('/api/document-types');
            const data = await response.json();
            if (data.types && Array.isArray(data.types)) {
                data.types.forEach(type => {
                    const opt = new Option(type.name, type.id);
                    select.add(opt);
                });
            }
        } catch (e) {
            console.error('Error loading document types', e);
        }
        if (selectedId != null && selectedId !== '') {
            select.value = String(selectedId);
        }
    }

    async saveEditDocument() {
        const id = document.getElementById('editDocumentId').value;
        const name = document.getElementById('editDocName').value.trim();
        const description = document.getElementById('editDocDescription').value.trim();
        const documentTypeId = document.getElementById('editDocType').value;
        const msgEl = document.getElementById('editDocumentMessage');

        if (name.length < 3 || description.length < 3 || !documentTypeId) {
            msgEl.className = 'upload-message error';
            msgEl.textContent = window._documentUploadT ? window._documentUploadT('editValidationError', 'Name and description must be at least 3 characters; type is required.') : 'Name and description must be at least 3 characters; type is required.';
            msgEl.style.display = 'block';
            return;
        }

        const body = {
            name,
            description,
            documentTypeId: parseInt(documentTypeId, 10)
        };
        const urlGroup = document.getElementById('editDocUrlGroup');
        if (urlGroup.style.display !== 'none') {
            const url = document.getElementById('editDocUrl').value.trim();
            if (url) body.url = url;
        }

        try {
            const response = await fetch(`/api/documents/${id}?facetType=${this.facetType}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            const data = await response.json();
            if (data.success) {
                this.hideEditModal();
                this.loadDocuments();
            } else {
                msgEl.className = 'upload-message error';
                msgEl.textContent = data.error || (window._documentUploadT ? window._documentUploadT('updateFailed', 'Update failed') : 'Update failed');
                msgEl.style.display = 'block';
            }
        } catch (error) {
            msgEl.className = 'upload-message error';
            msgEl.textContent = (window._documentUploadT ? window._documentUploadT('updateFailed', 'Update failed') : 'Update failed') + ': ' + error.message;
            msgEl.style.display = 'block';
        }
    }

    downloadDocument(documentId, isUrl, path) {
        if (isUrl) {
            // Open URL in new tab
            window.open(path, '_blank');
        } else {
            // Download file
            window.location.href = `/api/documents/${documentId}/download?facetType=${this.facetType}`;
        }
    }

    async deleteDocument(documentId) {
        var delMsg = window._documentUploadT ? window._documentUploadT('deleteConfirm', 'Are you sure you want to delete this document?') : 'Are you sure you want to delete this document?';
        if (!confirm(delMsg)) {
            return;
        }

        try {
            const response = await fetch(`/api/documents/${documentId}?facetType=${this.facetType}`, {
                method: 'DELETE'
            });

            const data = await response.json();

            if (data.success) {
                this.loadDocuments(); // Reload table
            } else {
                alert((window._documentUploadT ? window._documentUploadT('deleteFailed', 'Failed to delete document') : 'Failed to delete document') + ': ' + data.error);
            }
        } catch (error) {
            console.error('Error deleting document:', error);
            alert(window._documentUploadT ? window._documentUploadT('deleteFailed', 'Failed to delete document') : 'Failed to delete document');
        }
    }

    updateRecordCount(count) {
        const countSpan = document.getElementById('documentCount');
        const t = window._documentUploadT || function (k, f) { return f; };
        if (count === 0) {
            countSpan.textContent = window.I18n && window.I18n.t ? window.I18n.t('documentUpload.zeroRecords') : t('zeroRecords', '0 records');
        } else if (count === 1) {
            countSpan.textContent = t('oneRecord', '1 record');
        } else {
            countSpan.textContent = window.I18n && window.I18n.t ? window.I18n.t('documentUpload.nRecords', { n: count }) : (count + ' records');
        }
    }

    refresh() {
        this.loadDocuments();
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = DocumentTableComponent;
}
