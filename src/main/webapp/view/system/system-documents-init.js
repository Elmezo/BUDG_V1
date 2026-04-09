/**
 * Document Components Initialization for System Edit Page
 * This script initializes the document upload and table components on page load
 * since they are now part of the SUMMARY tab (not a separate tab)
 */

(function () {
    let documentUploadComponent = null;
    let documentTableComponent = null;
    let documentsInitialized = false;

    // Get system ID from URL
    function getSystemId() {
        const urlParams = new URLSearchParams(window.location.search);
        return parseInt(urlParams.get('id'), 10);
    }

    // Initialize document components
    function initializeDocumentComponents() {
        if (documentsInitialized) {
            console.log('Document components already initialized');
            return;
        }

        const systemId = getSystemId();
        if (!systemId || isNaN(systemId)) {
            console.error('Invalid system ID, cannot initialize document components');
            return;
        }

        console.log('Initializing document components for system ID:', systemId);

        // Initialize upload component
        if (typeof DocumentUploadComponent !== 'undefined') {
            try {
                documentUploadComponent = new DocumentUploadComponent({
                    facetType: 'system',
                    facetId: systemId,
                    container: '#documentUploadContainer',
                    onUploadSuccess: () => {
                        // Refresh table when upload succeeds
                        if (documentTableComponent) {
                            documentTableComponent.refresh();
                        }
                    }
                });
                console.log('✓ Document upload component initialized');
            } catch (error) {
                console.error('Error initializing document upload component:', error);
            }
        } else {
            console.error('DocumentUploadComponent class not found');
        }

        // Initialize table component
        if (typeof DocumentTableComponent !== 'undefined') {
            try {
                documentTableComponent = new DocumentTableComponent({
                    facetType: 'system',
                    facetId: systemId,
                    container: '#documentTableContainer',
                    canEdit: true // TODO: Check actual edit permissions
                });
                console.log('✓ Document table component initialized');
            } catch (error) {
                console.error('Error initializing document table component:', error);
            }
        } else {
            console.error('DocumentTableComponent class not found');
        }

        documentsInitialized = true;
        console.log('✓ Document components initialization complete');
    }

    // Initialize on page load
    document.addEventListener('DOMContentLoaded', () => {
        console.log('DOM loaded, initializing document components...');

        // Wait a bit for other scripts to load
        setTimeout(() => {
            initializeDocumentComponents();
        }, 500);
    });

    // Also expose a global function to manually trigger initialization if needed
    window.initSystemDocuments = initializeDocumentComponents;
})();
