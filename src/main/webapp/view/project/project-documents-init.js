/**
 * Document Components Initialization for Project Edit Page
 */

(function () {
    let documentUploadComponent = null;
    let documentTableComponent = null;
    let documentsInitialized = false;

    function getProjectId() {
        const urlParams = new URLSearchParams(window.location.search);
        return parseInt(urlParams.get('id'), 10);
    }

    function initializeDocumentComponents() {
        if (documentsInitialized) return;
        const projectId = getProjectId();
        if (!projectId || isNaN(projectId)) return;

        if (typeof DocumentUploadComponent !== 'undefined') {
            try {
                documentUploadComponent = new DocumentUploadComponent({
                    facetType: 'project',
                    facetId: projectId,
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
                    facetType: 'project',
                    facetId: projectId,
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

    window.initProjectDocuments = initializeDocumentComponents;
})();

