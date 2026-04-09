/**
 * Document Components Initialization for Capability Edit Page
 */

(function () {
    let documentUploadComponent = null;
    let documentTableComponent = null;
    let documentsInitialized = false;

    function getCapabilityId() {
        const urlParams = new URLSearchParams(window.location.search);
        return parseInt(urlParams.get('id'), 10);
    }

    function initializeDocumentComponents() {
        if (documentsInitialized) return;
        const capabilityId = getCapabilityId();
        if (!capabilityId || isNaN(capabilityId)) return;

        if (typeof DocumentUploadComponent !== 'undefined') {
            try {
                documentUploadComponent = new DocumentUploadComponent({
                    facetType: 'capability',
                    facetId: capabilityId,
                    container: '#documentUploadContainer',
                    onUploadSuccess: () => {
                        if (documentTableComponent) documentTableComponent.refresh();
                    }
                });
            } catch (error) {
                console.error('Error initializing document upload component:', error);
            }
        }

        if (typeof DocumentTableComponent !== 'undefined') {
            try {
                documentTableComponent = new DocumentTableComponent({
                    facetType: 'capability',
                    facetId: capabilityId,
                    container: '#documentTableContainer',
                    canEdit: true
                });
            } catch (error) {
                console.error('Error initializing document table component:', error);
            }
        }

        documentsInitialized = true;
    }

    document.addEventListener('DOMContentLoaded', () => {
        setTimeout(() => initializeDocumentComponents(), 500);
    });

    window.initCapabilityDocuments = initializeDocumentComponents;
})();

