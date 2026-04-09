/**
 * Document Components Initialization for System Interface Edit Page
 */

(function () {
    let documentUploadComponent = null;
    let documentTableComponent = null;
    let documentsInitialized = false;

    function getInterfaceId() {
        const urlParams = new URLSearchParams(window.location.search);
        return parseInt(urlParams.get('id'), 10);
    }

    function initializeDocumentComponents() {
        if (documentsInitialized) return;
        const interfaceId = getInterfaceId();
        if (!interfaceId || isNaN(interfaceId)) return;

        if (typeof DocumentUploadComponent !== 'undefined') {
            try {
                documentUploadComponent = new DocumentUploadComponent({
                    facetType: 'interface',
                    facetId: interfaceId,
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
                    facetType: 'interface',
                    facetId: interfaceId,
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

    window.initInterfaceDocuments = initializeDocumentComponents;
})();

