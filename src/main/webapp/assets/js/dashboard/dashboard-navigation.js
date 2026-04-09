/**
 * Dashboard Navigation Utilities
 * Helper functions for navigating from dashboard widgets to relevant pages
 */

const DashboardNavigation = {
    /**
     * Navigate to search page with facet filter
     * @param {string} facetName - Name of the facet to filter by
     */
    navigateToFacetSearch(facetName) {
        if (!facetName || facetName === 'Others') {
            return; // Don't navigate for "Others"
        }
        
        // Map facet names to search categories
        const facetMapping = {
            'Data Set': 'dataset',
            'Glossary': 'glossary',
            'System': 'system',
            'Process': 'process',
            'Attribute': 'attribute',
            'Role': 'role',
            'System Interface': 'interface',
            'Product': 'product',
            'Policy': 'policy',
            'Project': 'project',
            'Business Area': 'business-area',
            'Capability': 'capability',
            'Client': 'client',
            'Committee': 'committee',
            'Legal Entity': 'legal-entity',
            'Org Unit': 'orgunit',
            'Regulation': 'regulation',
            'People': 'people'
        };
        
        const category = facetMapping[facetName];
        if (category) {
            window.location.href = `/search.html?category=${encodeURIComponent(category)}`;
        }
    },

    /**
     * Navigate to My Account page with specific tab
     * @param {number} userId - User ID
     * @param {string} tab - Tab name (e.g., 'responsibilities', 'team', 'change')
     * @param {string} subtab - Optional subtab name
     * @param {string} filter - Optional filter parameter
     */
    navigateToMyAccount(userId, tab, subtab = null, filter = null) {
        if (!userId) {
            console.error('User ID is required for navigation');
            return;
        }
        
        let url = `/view/people/${userId}?tab=${encodeURIComponent(tab)}`;
        
        if (subtab) {
            url += `&subtab=${encodeURIComponent(subtab)}`;
        }
        
        if (filter) {
            url += `&filter=${encodeURIComponent(filter)}`;
        }
        
        window.location.href = url;
    },

    /**
     * Navigate to responsibilities tab with facet filter
     * @param {number} userId - User ID
     * @param {string} facetName - Facet name to filter by
     * @param {boolean} notAcceptedOnly - Whether to filter for not accepted roles only
     */
    navigateToResponsibilities(userId, facetName = 'all', notAcceptedOnly = false) {
        let filter = notAcceptedOnly ? 'roleAccepted:No' : '';
        this.navigateToMyAccount(userId, 'responsibilities', facetName, filter);
    },

    /**
     * Navigate to team tab
     * @param {number} userId - User ID
     */
    navigateToTeam(userId) {
        this.navigateToMyAccount(userId, 'team');
    },

    /**
     * Navigate to change requests tab
     * @param {number} userId - User ID
     */
    navigateToChangeRequests(userId) {
        this.navigateToMyAccount(userId, 'change');
    },

    /**
     * Navigate to person view page
     * @param {number} personId - Person ID
     */
    navigateToPerson(personId) {
        if (!personId) {
            console.error('Person ID is required');
            return;
        }
        window.location.href = `/view/people/${personId}`;
    },

    /**
     * Load saved search
     * @param {number} searchId - Saved search ID
     */
    loadSavedSearch(searchId) {
        if (!searchId) {
            console.error('Search ID is required');
            return;
        }
        // Navigate to search page with search ID parameter
        window.location.href = `/search.html?searchId=${searchId}`;
    },

    /**
     * Navigate to manage searches page
     */
    navigateToManageSearches() {
        // This would navigate to a searches management page if it exists
        // For now, just navigate to search page
        window.location.href = '/search.html';
    }
};

// Make available globally
window.DashboardNavigation = DashboardNavigation;

