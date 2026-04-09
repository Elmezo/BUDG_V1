/**
 * Document Components Initialization for Glossary Edit Page
 * Initializes document upload on page load. The document table is created in glossary-edit.js
 * loadDocuments() so facet id and CR view mode stay in sync.
 */

(function () {
    let documentUploadComponent = null;
    let documentsInitialized = false;

    // Get glossary ID from URL
    function getGlossaryId() {
        const urlParams = new URLSearchParams(window.location.search);
        return parseInt(urlParams.get('id'), 10);
    }
    
    // Get current view mode from global editViewMode or default to 'changes' for edit page
    function getCurrentViewMode() {
        // In edit page, we want to show pending documents (changes view)
        return window.editViewMode || 'changes';
    }

    // Initialize document components
    function initializeDocumentComponents(viewMode = null) {
        const glossaryId = getGlossaryId();
        if (!glossaryId || isNaN(glossaryId)) {
            console.error('Invalid glossary ID, cannot initialize document components');
            return;
        }
        
        const currentViewMode = viewMode || getCurrentViewMode();
        if (window.__BUDG_DEBUG__) console.log('Initializing document components for glossary ID:', glossaryId, 'viewMode:', currentViewMode);

        // Initialize upload component (only once). Table comes from glossary-edit.js loadDocuments().
        if (!documentUploadComponent && typeof DocumentUploadComponent !== 'undefined') {
            if (!document.querySelector('#documentUploadContainer')) {
                console.warn('Glossary documents: #documentUploadContainer not found');
            } else {
                try {
                    documentUploadComponent = new DocumentUploadComponent({
                        facetType: 'glossary',
                        facetId: glossaryId,
                        container: '#documentUploadContainer',
                        onUploadSuccess: () => {
                            const table = window.glossaryDocumentTableComponent;
                            if (table && typeof table.loadDocuments === 'function') {
                                table.loadDocuments();
                            }
                        }
                    });
                    if (window.__BUDG_DEBUG__) console.log('✓ Document upload component initialized');
                } catch (error) {
                    console.error('Error initializing document upload component:', error);
                }
            }
        }

        // Document table is created by glossary-edit.js loadDocuments() (correct glossary id + CR view mode)

        documentsInitialized = true;
        if (window.__BUDG_DEBUG__) console.log('✓ Document components initialization complete');
    }
    
    // Set view mode for documents
    function setDocumentsViewMode(viewMode) {
        const table = window.glossaryDocumentTableComponent;
        if (table && typeof table.setViewMode === 'function') {
            table.setViewMode(viewMode);
        }
    }

    // Initialize on page load
    document.addEventListener('DOMContentLoaded', () => {
        if (window.__BUDG_DEBUG__) console.log('DOM loaded, initializing document components...');

        // Wait a bit for other scripts to load
        setTimeout(() => {
            initializeDocumentComponents();
        }, 500);
    });

    // Also expose global functions
    window.initGlossaryDocuments = initializeDocumentComponents;
    window.setGlossaryDocumentsViewMode = setDocumentsViewMode;
    window.getGlossaryDocumentTableComponent = () => window.glossaryDocumentTableComponent || null;
})();

