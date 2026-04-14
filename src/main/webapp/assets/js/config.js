// BUDG Platform API Configuration
// This file centralizes all API endpoint URLs and environment-specific settings

const BUDG_CONFIG = {
    // API Base URLs - Environment specific
    API: {
        // Development environment (default)
        development: {
            // No localhost here, we'll use window.location.hostname
            BASE_URL: `http://${window.location.hostname}:8080/api`,
            CORS_ORIGIN: `http://${window.location.hostname}:3000`
        },
        // Production environment
        production: {
            BASE_URL: '/api', // Relative path for same-origin requests
            CORS_ORIGIN: window.location.origin
        },
        // Staging environment
        staging: {
            BASE_URL: 'https://staging.budg-platform.com/api',
            CORS_ORIGIN: 'https://staging.budg-platform.com'
        }
    },

    // API Endpoints
    ENDPOINTS: {
        ORG_UNITS: {
            FETCH_ALL: '/org-units',
            SEARCH: '/org-units/search',
            CREATE: '/org-units',
            UPDATE: '/org-units/{id}',
            DELETE: '/org-units/{id}',
            GET_BY_ID: '/org-units/{id}'
        },
        PEOPLE: {
            FETCH_ALL: '/people',
            SEARCH: '/people/search',
            CREATE: '/people',
            UPDATE: '/people/{id}',
            DELETE: '/people/{id}',
            GET_BY_ID: '/people/{id}',
            GET_ORG_UNITS: '/people/org-units',
            GET_BY_ORG_UNIT: '/people/by-org-unit'
        },
        STATUS: {
            FETCH_ALL: '/status',
            SEARCH: '/status/search',
            CREATE: '/status',
            UPDATE: '/status/{id}',
            DELETE: '/status/{id}',
            GET_BY_ID: '/status/{id}',
            GET_DROPDOWN: '/status/dropdown'
        },
        DATASET: {
            CREATE: '/dataset/create',
            GET_BY_ID: '/dataset/{id}',
            UPDATE: '/dataset/{id}',
            DELETE: '/dataset/{id}',
            GET_STAKEHOLDERS: '/dataset/{id}/stakeholders',
            GET_ROLES: '/dataset/{id}/roles',
            GET_USERS_BY_ROLE: '/dataset/{id}/users/{roleId}',
            GET_STATUSES: '/dataset/{id}/statuses',
            SAVE_STAKEHOLDERS: '/dataset/{id}/stakeholders',
            GET_BY_GLOSSARY: '/dataset/glossary/{id}'
        },
        CLIENT: {
                    FETCH_ALL: '/client',
                    SEARCH: '/client/search',
                    CREATE: '/client',
                    UPDATE: '/client/{id}',
                    DELETE: '/client/{id}',
                    GET_BY_ID: '/client/{id}',
                    STATUS_LIST: '/client/status-list',
                    LIFECYCLE_LIST: '/client/lifecycle-list',
                    VIEWING_LIST: '/client/viewing-list',
                    PARENT_CLIENTS: '/client/parent-clients'
                },
        COMMITTEE: {
                    FETCH_ALL: '/committee',
                    SEARCH: '/committee/search',
                    CREATE: '/committee',
                    UPDATE: '/committee',
                    DELETE: '/committee',
                    GET_BY_ID: '/committee/detail',
                    STATUS_LIST: '/committee/status-list',
                    LIFECYCLE_LIST: '/committee/lifecycle-list',
                    VIEWING_LIST: '/committee/viewing-list',
                    PARENT_COMMITTEES: '/committee/lookup'
                },
        LEGAL_ENTITY: {
                    FETCH_ALL: '/LegalEntity',
                    SEARCH: '/LegalEntity/search',
                    CREATE: '/LegalEntity',
                    UPDATE: '/LegalEntity/{id}',
                    DELETE: '/LegalEntity/{id}',
                    GET_BY_ID: '/LegalEntity/{id}'
                },
        LOOKUPS: {
            SYSTEMS: '/system/list',
            GLOSSARY: '/glossary/list',
            STATUS_LIST: '/status/list',
            DATASET_TYPES: '/dataset-type/list',
            VIEWING: '/viewing/list',
            LIFECYCLE: '/system/lifecycle/list',
            DATASET_LIFECYCLE: '/lifecycle/list',
            GLOSSARY_LIFECYCLE: '/glossary/lifecycle/list',
            SYSTEM_TYPE: '/system/type/list',
            CIA_RATING: '/cia-rating/list',
            CLASSIFICATION: '/classification/list',
            GLOSSARY_TYPE: '/type/list',
            GLOSSARY_KDE: '/kde-type/list',
            GLOSSARY_SECURITY: '/security/list',
            GLOSSARY_FORMAT_TYPE: '/format-type/list',
            INTERFACE_LIFECYCLE: '/interface/lifecycle/list',
            INTERFACE_CLASSIFICATION: '/interface/classification/list',
            INTERFACE_AUTOMATION: '/interface/automation/list',
            INTERFACE_FREQUENCY: '/interface/frequency/list',
            INTERFACE_TRANSFER_METHOD: '/interface/transfer-method/list',
            INTERFACE_TRANSFER_FORMAT: '/interface/transfer-format/list',
            PRODUCT_LIFECYCLE: '/product/lifecycle/list',
            POLICY_LIFECYCLE: '/policy/lifecycle/list',
            POLICY_TYPE: '/policy-type/list',
            PROCESS_LIFECYCLE: '/process/lifecycle/list',
            PROCESS_TYPE: '/process/type/list',
            PROCESS_CLASS: '/process/class/list',
            PROCESS_AUTOMATION: '/process/automation/list',
            PROCESS_DURATION_TYPE: '/process/duration-type/list',
            PROCESS_STEP_TYPE: '/process/step-type/list',
            PROCESS_LIST: '/process/list',
            PROCESS_PARENT_PICKER: '/process/parent-picker',
            PROJECT_TYPE: '/project/type/list',
            PROJECT_CLASSIFICATION: '/project/classification/list',
            PROJECT_LIFECYCLE: '/project/lifecycle/list',
            PROJECT_RAG: '/project/rag/list',
            EMPLOYMENT_TYPE: '/employment-type/list',
            PEOPLE_LIFECYCLE: '/people/lifecycle/list',
             /* Added from EDITOR - Capability lookup endpoints */
             CAPABILITY_CLASSIFICATION: '/capability-classifications/dropdown',
             CAPABILITY_LIFECYCLE: '/capability-lifecycles/dropdown',
             CAPABILITY_TYPE: '/capability-types/dropdown'
        },
        GLOSSARY: {
            SAVE: '/glossary/save',
            GET_BY_ID: '/glossary/{id}',
            GET_HIERARCHY: '/glossary/{id}/hierarchy',
            GET_STRATEGIC_SOURCE: '/glossary-x-system/{id}',
            GET_STAKEHOLDERS: '/glossary/{id}/stakeholders',
            GET_ROLES: '/glossary/{id}/roles',
            GET_USERS_BY_ROLE: '/glossary/{id}/users/{roleId}',
            GET_STATUSES: '/glossary/{id}/statuses',
            SAVE_STAKEHOLDERS: '/glossary/{id}/stakeholders'
        },
        SYSTEM: {
            SAVE: '/system/save',
            GET_BY_ID: '/system/{id}',
            GET_STAKEHOLDERS: '/system/{id}/stakeholders',
            GET_ROLES: '/system/{id}/roles',
            GET_USERS_BY_ROLE: '/system/{id}/users/{roleId}',
            GET_STATUSES: '/system/{id}/statuses',
            SAVE_STAKEHOLDERS: '/system/{id}/stakeholders'
        },
        INTERFACE: {
            SAVE: '/interface/save',
            GET_BY_ID: '/interface/{id}',
            GET_STAKEHOLDERS: '/interface/{id}/stakeholders',
            GET_ROLES: '/interface/{id}/roles',
            GET_USERS_BY_ROLE: '/interface/{id}/users/{roleId}',
            GET_STATUSES: '/interface/{id}/statuses',
            SAVE_STAKEHOLDERS: '/interface/{id}/stakeholders',
            GET_X_GLOSSARY: '/interface-x-glossary'
        },
        PRODUCT: {
            FETCH_ALL: '/product',
            SEARCH: '/product/search',
            CREATE: '/product',
            UPDATE: '/product/{id}',
            DELETE: '/product/{id}',
            GET_BY_ID: '/product/{id}',
            LIST: '/product/list',
            PARENT_PICKER: '/product/parent-picker'
        },
        POLICY: {
            FETCH_ALL: '/policy',
            SEARCH: '/policy/search/{query}',
            CREATE: '/policy',
            UPDATE: '/policy/{id}',
            DELETE: '/policy/{id}',
            GET_BY_ID: '/policy/{id}',
            LIST: '/policy/list',
            PARENT_PICKER: '/policy/parent-picker',
            HIERARCHY: '/policy/hierarchy/{id}',
            RELATIONSHIPS: '/policy/relationships/{id}'
        },
        PROJECT: {
            FETCH_ALL: '/project',
            SEARCH: '/project/search/{query}',
            CREATE: '/project',
            UPDATE: '/project/{id}',
            DELETE: '/project/{id}',
            GET_BY_ID: '/project/{id}',
            LIST: '/project/list',
            PARENT_PICKER: '/project/parent-picker',
            HIERARCHY: '/project/hierarchy/{id}',
            RELATIONSHIPS: '/project/relationships/{id}'
        },
        /* Added from EDITOR - PROCESS endpoints */
        PROCESS: {
            FETCH_ALL: '/process',
            SEARCH: '/process/search',
            CREATE: '/process',
            UPDATE: '/process/{id}',
            DELETE: '/process/{id}',
            GET_BY_ID: '/process/{id}',
            LIST: '/process/list',
            PARENT_PICKER: '/process/parent-picker',
            NEXT_REF: '/process/next-ref'
        },
        /* Added from EDITOR - BUSINESS_AREA endpoints */
        BUSINESS_AREA: {
            FETCH_ALL: '/business-areas',
            SEARCH: '/business-areas/search',
            CREATE: '/business-areas',
            UPDATE: '/business-areas/{id}',
            DELETE: '/business-areas/{id}',
            GET_BY_ID: '/business-areas/{id}',
            GET_LIFECYCLE: '/business-area-lifecycles',
            GET_LIFECYCLE_BY_ID: '/business-area-lifecycles/{id}',
            GET_DROPDOWN: '/business-areas/dropdown'
        },
        /* Added from EDITOR - CAPABILITY endpoints */
     CAPABILITY: {
             FETCH_ALL: '/capabilities',
             SEARCH: '/capabilities/search',
             CREATE: '/capabilities',
             UPDATE: '/capabilities/{id}',
             DELETE: '/capabilities/{id}',
             GET_BY_ID_U: '/UnisonSearch/capability/{id}',
             GET_BY_ID: '/capabilities/{id}',
             GET_HIERARCHY: '/capabilities/{id}/hierarchy',
             GET_TYPES: '/capabilities/types',
             GET_CLASSIFICATIONS: '/capabilities/classifications',
             GET_LIFECYCLE: '/capabilities/lifecycle',
             GET_LIFECYCLE_BY_ID: '/capability-lifecycle/{id}',
             GET_DROPDOWN: '/capabilities/dropdown'
         },
        AUTH: {
            LOGIN: '/auth/login',
            LOGOUT: '/auth/logout',
            REFRESH: '/auth/refresh',
            VALIDATE: '/auth/validate'
        },
        ROLE: {
            GET_BY_ID: '/role/{id}',
            GET_ALL: '/roles',
            GET_FOR_MODULE: '/{module}/{id}/roles',
            GET_ASSIGNMENTS: '/role/{id}/assignments'
        }
    },

    // Request Configuration
    REQUEST: {
        TIMEOUT: 30000, // 30 seconds timeout
        RETRY_ATTEMPTS: 3, // Number of retry attempts for failed requests
        RETRY_DELAY: 1000, // 1 second delay between retries
        DEFAULT_HEADERS: {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        }
    },

    // CORS Configuration
    CORS: {
        CREDENTIALS: 'include', // Include cookies in requests
        MODE: 'cors' // Enable CORS mode
    }
};

// Detect current environment based on hostname
function getCurrentEnvironment() {
    // Check for Node.js environment (for testing)
    if (typeof process !== 'undefined' && process.env.NODE_ENV) {
        return process.env.NODE_ENV;
    }

    // Detect environment based on hostname
    const hostname = window.location.hostname;
    if (hostname === 'localhost' || hostname === '127.0.0.1') {
        return 'development';
    } else if (hostname.includes('staging')) {
        return 'staging';
    } else {
        return 'production';
    }
}

// Get API configuration for current environment
function getApiConfig() {
    const env = getCurrentEnvironment();
    return BUDG_CONFIG.API[env] || BUDG_CONFIG.API.development;
}

// Get base URL for API requests based on current environment
function getBaseUrl() {
    const env = getCurrentEnvironment();
    if (env === 'development') {
        // Since the app runs at root "/", use direct path
        return `http://${window.location.hostname}:8080/api`;
    }
    return getApiConfig().BASE_URL;
}

// Construct full API URL with parameters
function getApiUrl(endpoint, params = {}) {
    // Start with base URL + endpoint
    let url = getBaseUrl() + endpoint;
    const pathParams = {}; // Parameters for path replacement
    const queryParams = {}; // Parameters for query string

    // Separate path parameters from query parameters
    Object.keys(params).forEach(key => {
        if (endpoint.includes(`{${key}}`)) {
            pathParams[key] = params[key];
        } else {
            queryParams[key] = params[key];
        }
    });

    // Replace path parameters in URL
    Object.keys(pathParams).forEach(key => {
        url = url.replace(`{${key}}`, encodeURIComponent(pathParams[key]));
    });

    // Add query parameters to URL if any exist
    if (Object.keys(queryParams).length > 0) {
        const queryString = Object.keys(queryParams)
            .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(queryParams[key])}`)
            .join('&');
        url += '?' + queryString;
    }

    return url;
}

// Make configuration globally available
window.BUDG_CONFIG = BUDG_CONFIG;
window.BUDG_API = {
    getApiUrl // Export function for building API URLs
};

