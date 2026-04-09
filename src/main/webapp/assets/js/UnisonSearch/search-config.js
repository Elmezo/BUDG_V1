// Core configuration, globals, and constants

let fuzzySearchEnabled = false; // Default value

// Constants
let tableContainer; // سيتم تهيئته عند تحميل DOM
const SKELETON_ROWS = 8;
const SKELETON_COLS = 5;

// Global settings for table display
let showEmptyColumns = true; // Show columns even if they have empty values

// Field mapping for search functionality only
// Keys are the field identifiers used by addSearchCondition and the "Search in" UI.
// Values are the display labels shown to the user in the query-builder-container.
const CATEGORY_FIELD_MAPPING = {
    'dataset':          { name: 'Name', ref: 'Ref.', definition: 'Definition', usage: 'Usage' },
    'attribute':        { name: 'Name', ref: 'Ref.', definition: 'Definition', businessLogic: 'Business Logic' },
    'system':           { name: 'Short Name', longName: 'Long Name', assetId: 'Asset ID', description: 'Description' },
    'glossary':         { name: 'Name', ref: 'Ref.', definition: 'Definition', aliasName: 'Alias Name', businessLogic: 'Business Logic' },
    'policy':           { ref: 'Ref.', name: 'Name', description: 'Description' },
    'process':          { name: 'Name', ref: 'Ref.', description: 'Description' },
    'project':          { ref: 'Ref.', name: 'Name', description: 'Description' },
    'people':           { name: 'Full Name', firstName: 'First Name', lastName: 'Last Name', email: 'Email' },
    'change-requests':  { name: 'Subject', summary: 'Summary' },
    'committee':        { ref: 'Ref.', name: 'Name', description: 'Description' },
    'role':             { name: 'Full Name', description: 'Description' },
    'active-tasks':     { name: 'Name' },
    'interface':        { ref: 'Ref.', name: 'Name', description: 'Description' },
    'business-area':    { name: 'Name', description: 'Description' },
    'capability':       { ref: 'Ref.', name: 'Name', description: 'Description' },
    'client':           { name: 'Name', description: 'Description' },
    'legal-entity':     { name: 'Short Name', longName: 'Long Name', description: 'Description' },
    'orgunit':          { ref: 'Ref.', name: 'Name', description: 'Description' },
    'product':          { name: 'Name', longName: 'Long Name', description: 'Description' },
    'geography':        { name: 'Name', description: 'Description' },
    'regulation':       { name: 'Name', ref: 'Ref.', shortName: 'Short Name', description: 'Description' },
    'regulator':        { name: 'Name', shortName: 'Short Name', description: 'Description' },
    'regulatory-theme': { name: 'Name', ref: 'Ref.', shortName: 'Short Name', description: 'Description' },
};

/**
 * Fetch fuzzy search configuration from server
 */
async function fetchFuzzySearchConfig() {
    try {
        const response = await fetch('/UnisonSearch/config/fuzzy-search', {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (response.ok) {
            const data = await response.json();
            fuzzySearchEnabled = data.enabled === true;
            console.log('[Fuzzy Search Config] Fetched from server - enabled:', fuzzySearchEnabled);
        } else {
            fuzzySearchEnabled = false;
            console.log('[Fuzzy Search Config] Response not OK, defaulting to false');
        }
    } catch (error) {
        fuzzySearchEnabled = false;
        console.log('[Fuzzy Search Config] Error fetching config, defaulting to false:', error);
    }
    
    // Make fuzzySearchEnabled available globally
    if (typeof window !== 'undefined') {
        window.fuzzySearchEnabled = fuzzySearchEnabled;
        console.log('[Fuzzy Search Config] Set window.fuzzySearchEnabled to:', fuzzySearchEnabled);
    }
}


