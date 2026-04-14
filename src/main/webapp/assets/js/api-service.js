// BUDG Platform API Service
// Centralized service for handling all API requests with proper CORS and error handling

class ApiService {
    constructor() {
        // Initialize with default values in case config is not available yet
        this.config = window.BUDG_CONFIG || {};
        this.api = window.BUDG_API || {};
        this.requestTimeout = (this.config.REQUEST && this.config.REQUEST.TIMEOUT) || 30000;
        this.retryAttempts = (this.config.REQUEST && this.config.REQUEST.RETRY_ATTEMPTS) || 3;
        this.retryDelay = (this.config.REQUEST && this.config.REQUEST.RETRY_DELAY) || 1000;
    }

    // Update configuration when it becomes available
    updateConfig() {
        if (window.BUDG_CONFIG) {
            this.config = window.BUDG_CONFIG;
            this.api = window.BUDG_API || {};
            this.requestTimeout = this.config.REQUEST?.TIMEOUT || 30000;
            this.retryAttempts = this.config.REQUEST?.RETRY_ATTEMPTS || 3;
            this.retryDelay = this.config.REQUEST?.RETRY_DELAY || 1000;
        }
    }

    // Get default headers for API requests
    getDefaultHeaders() {
        this.updateConfig();
        const headers = { ...(this.config.REQUEST?.DEFAULT_HEADERS || {}) };

        // Add authorization token if available
        const token = this.getAuthToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
        return headers;
    }

    // Authentication token handling
    getAuthToken() {
        return localStorage.getItem('budg-auth-token');
    }

    setAuthToken(token) {
        if (token) {
            localStorage.setItem('budg-auth-token', token);
        } else {
            localStorage.removeItem('budg-auth-token');
        }
    }

    /** Read double-submit CSRF token from cookie (set by server on login / guest / refresh). */
    getCsrfTokenFromCookie() {
        try {
            const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return m ? decodeURIComponent(m[1].trim()) : null;
        } catch (e) {
            return null;
        }
    }

    // Create request options object
    createRequestOptions(method = 'GET', body = null, customHeaders = {}) {
        const m = method.toUpperCase();
        const options = {
            method: m,
            headers: { 
                ...this.getDefaultHeaders(), 
                ...customHeaders,
                'Cache-Control': 'no-cache, no-store, must-revalidate',
                'Pragma': 'no-cache'
            },
            mode: this.config.CORS?.MODE || 'cors',
            credentials: this.config.CORS?.CREDENTIALS || 'include',
            cache: 'no-store' // Prevent browser caching
        };

        if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(m)) {
            const xsrf = this.getCsrfTokenFromCookie();
            if (xsrf && !options.headers['X-XSRF-TOKEN']) {
                options.headers['X-XSRF-TOKEN'] = xsrf;
            }
        }

        // Add body for non-GET requests
        if (body && m !== 'GET') {
            options.body = typeof body === 'string' ? body : JSON.stringify(body);
        }
        return options;
    }

    // Create timeout promise to prevent hanging requests
    createTimeoutPromise(timeout) {
        return new Promise((_, reject) => {
            setTimeout(() => reject(new Error(`Request timeout after ${timeout}ms`)), timeout);
        });
    }

    // Retry mechanism for failed requests
    async retryRequest(requestFn, attempt = 1) {
        try {
            return await requestFn();
        } catch (error) {
            // Retry only for certain types of errors
            if (attempt < this.retryAttempts && this.isRetryableError(error)) {
                console.warn(`Request failed, retrying... (attempt ${attempt + 1}/${this.retryAttempts})`);
                await this.delay(this.retryDelay * attempt);
                return this.retryRequest(requestFn, attempt + 1);
            }
            throw error;
        }
    }

    // Check if an error is retryable
    isRetryableError(error) {
        // Retry network errors and server errors (5xx)
        return error.name === 'TypeError' ||
            (error.status >= 500 && error.status < 600) ||
            error.status === 429; // Rate limiting
    }

    // Utility function for delays
    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    // Main request method that handles all API calls
    async request(endpoint, options = {}) {
        this.updateConfig();
        const paramsWithSegment = this.applyAutomaticSegmentFilter(endpoint, options.method || 'GET', options.params || {});
        const url = this.api.getApiUrl(endpoint, paramsWithSegment);
        const requestOptions = this.createRequestOptions(options.method || 'GET', options.body, options.headers);

        try {
            // Race between the fetch request and timeout
            const fetchPromise = fetch(url, requestOptions);
            const timeoutPromise = this.createTimeoutPromise(this.requestTimeout);
            const response = await Promise.race([fetchPromise, timeoutPromise]);

            // Handle non-OK responses
            if (!response.ok) {
                let errorBody;
                try {
                    const text = await response.text();
                    if (text) {
                        try {
                            errorBody = JSON.parse(text);
                        } catch (e) {
                            // Server returned HTML or other non-JSON (e.g. container error page)
                            const snippet = text.trim().toLowerCase().startsWith('<') ? 'Server error (non-JSON response)' : text.slice(0, 200);
                            errorBody = { message: snippet };
                        }
                    } else {
                        errorBody = { message: response.statusText };
                    }
                } catch (parseError) {
                    console.warn('Failed to read error response:', parseError);
                    errorBody = { message: response.statusText };
                }
                // Check for error message in 'error' field first (backend convention), then 'message'
                const errorMessage = errorBody.error || errorBody.message || `HTTP error! status: ${response.status}`;
                const error = new Error(errorMessage);
                error.status = response.status;
                error.body = errorBody;
                // 404 is often expected (e.g. deleted references, bulk lineage fetches) — omit warn when caller opts in
                if (response.status === 404) {
                    if (!options.silent404) {
                        console.warn(`API request: ${url} – not found (404)`);
                    }
                } else {
                    console.error(`API request failed: ${url}`, error);
                }

                // 401 on mutating requests: try refresh once then retry (backend uses ACCESS_TOKEN cookie; refresh sets new cookie)
                const method = (options.method || 'GET').toUpperCase();
                const isMutating = ['PUT', 'POST', 'PATCH', 'DELETE'].includes(method);
                const isRefreshEndpoint = (endpoint && String(endpoint).toLowerCase().includes('refresh')) || url.includes('/auth/refresh') || url.includes('/api/refresh');
                if (response.status === 401 && isMutating && !isRefreshEndpoint && !options._retriedAfter401) {
                    try {
                        await this.refreshToken();
                        return this.request(endpoint, { ...options, _retriedAfter401: true });
                    } catch (refreshErr) {
                        // Refresh failed; throw the original 401 error
                    }
                }

                // Handle 403 Forbidden errors for view-related endpoints
                // Redirect stakeholders to permission denied page if they try to access objects in segments they can't access
                if (response.status === 403 && (options.method === 'GET' || !options.method)) {
                    // Check if this is a view-related endpoint (object detail endpoint)
                    const viewEndpoints = [
                        '/api/dataset/', '/api/project/', '/api/product/', '/api/process/', '/api/system/',
                        '/api/policy/', '/api/glossary/', '/api/client/', '/api/committee/', '/api/capability/', '/api/capabilities/',
                        '/api/business-area/', '/api/legalentity/', '/api/geography/', '/api/regulation/',
                        '/api/regulator/', '/api/regulatory-theme/', '/api/interface/', '/api/attribute/'
                    ];
                    const isViewEndpoint = viewEndpoints.some(ep => url.includes(ep) && /\d+/.test(url));
                    
                    if (isViewEndpoint) {
                        console.warn('Access denied (403) for view endpoint, redirecting to permission denied page');
                        // Redirect to permission denied page
                        if (window.location.pathname !== '/error/permission.html') {
                            window.location.replace('/error/permission.html');
                            return; // Don't throw error, we're redirecting
                        }
                    }
                }
                
                throw error;
            }

            // Handle empty/204 responses safely
            if (response.status === 204) {
                return { success: true };
            }
            const contentType = response.headers.get('content-type') || '';
            if (!contentType.includes('application/json')) {
                const text = await response.text();
                return text ? { raw: text } : { success: true };
            }
            const rawText = await response.text();
            if (!rawText) {
                return { success: true };
            }
            return JSON.parse(rawText);
        } catch (error) {
            // 404 already logged as warn above; avoid double-logging
            if (error.status !== 404) {
                console.error('API request failed:', error);
            }
            throw error;
        }
    }

    applyAutomaticSegmentFilter(endpoint, method, params) {
        const normalizedMethod = String(method || 'GET').toUpperCase();
        const safeParams = { ...(params || {}) };
        if (normalizedMethod !== 'GET') {
            return safeParams;
        }
        if (safeParams.segmentId != null) {
            return safeParams;
        }
        if (!this.shouldAttachSegmentFilter(endpoint)) {
            return safeParams;
        }

        const autoSegmentId = this.resolveActiveSegmentIdForFiltering();
        if (Number.isInteger(autoSegmentId) && autoSegmentId > 0) {
            safeParams.segmentId = autoSegmentId;
        }
        return safeParams;
    }

    shouldAttachSegmentFilter(endpoint) {
        const ep = String(endpoint || '').toLowerCase();
        if (!ep) return false;

        const excludedPrefixes = [
            '/me', '/refresh', '/segments', '/status', '/viewing',
            '/notifications', '/locks', '/auth'
        ];
        if (excludedPrefixes.some(prefix => ep.startsWith(prefix))) {
            return false;
        }

        // Apply mainly on list/dropdown style queries used by create/edit selectors.
        if (ep.includes('/dropdown') || ep.includes('/search') || ep.endsWith('/list') || ep.includes('/hierarchy')) {
            return true;
        }

        const listEndpoints = [
            '/business-areas', '/systems', '/policies', '/processes', '/projects',
            '/products', '/capabilities', '/datasets', '/glossary', '/clients',
            '/committees', '/geographies', '/regulators', '/regulations',
            '/legalentity', '/legal-entities'
        ];
        return listEndpoints.some(prefix => ep === prefix || ep.startsWith(prefix + '/'));
    }

    resolveActiveSegmentIdForFiltering() {
        const fromGlobal = parseInt(window.__activeFormSegmentId, 10);
        if (Number.isInteger(fromGlobal) && fromGlobal > 0) {
            return fromGlobal;
        }

        // If a segment select is present in the current form, it should win.
        const segmentSelect = document.querySelector('#segmentFieldContainer select.form-select');
        if (segmentSelect) {
            const fromField = parseInt(segmentSelect.value, 10);
            if (Number.isInteger(fromField) && fromField > 0) {
                return fromField;
            }
        }

        const fromUrl = parseInt(new URLSearchParams(window.location.search).get('segmentId'), 10);
        if (Number.isInteger(fromUrl) && fromUrl > 0) {
            return fromUrl;
        }

        // Segment cube fallback: only deterministic when exactly one segment is selected.
        const cube = window.globalSegmentsCubePanel;
        if (cube && cube.selectedSegmentIds && typeof cube.selectedSegmentIds.size === 'number' && cube.selectedSegmentIds.size === 1) {
            const [single] = Array.from(cube.selectedSegmentIds);
            const fromCube = parseInt(single, 10);
            if (Number.isInteger(fromCube) && fromCube > 0) {
                return fromCube;
            }
        }

        return null;
    }

    // Helper methods for common HTTP methods
    async get(endpoint, params = {}, headers = {}) {
        return this.request(endpoint, { method: 'GET', params, headers });
    }

    async post(endpoint, body, params = {}, headers = {}) {
        return this.request(endpoint, { method: 'POST', body, params, headers });
    }

    async put(endpoint, body, params = {}, headers = {}) {
        return this.request(endpoint, { method: 'PUT', body, params, headers });
    }

    async delete(endpoint, params = {}, headers = {}) {
        return this.request(endpoint, { method: 'DELETE', params, headers });
    }

    // Organization Units API methods
    async getOrgUnits() {
        return this.get(this.config.ENDPOINTS.ORG_UNITS.FETCH_ALL);
    }

    async searchOrgUnits(query) {
        return this.get(this.config.ENDPOINTS.ORG_UNITS.SEARCH, { q: query });
    }

    async createOrgUnit(data) {
        return this.post(this.config.ENDPOINTS.ORG_UNITS.CREATE, data);
    }

    async updateOrgUnit(id, data) {
        return this.put(this.config.ENDPOINTS.ORG_UNITS.UPDATE, data, { id });
    }

    async deleteOrgUnit(id) {
        return this.delete(this.config.ENDPOINTS.ORG_UNITS.DELETE, { id });
    }

    async getOrgUnitById(id) {
        return this.get(this.config.ENDPOINTS.ORG_UNITS.GET_BY_ID, { id });
    }

    // People API methods
    async getPeople() {
        return this.get(this.config.ENDPOINTS.PEOPLE.FETCH_ALL);
    }

    async searchPeople(query) {
        return this.get(this.config.ENDPOINTS.PEOPLE.SEARCH, { q: query });
    }

    async createPerson(data) {
        return this.post(this.config.ENDPOINTS.PEOPLE.CREATE, data);
    }

    async updatePerson(id, data) {
        return this.put(this.config.ENDPOINTS.PEOPLE.UPDATE, data, { id });
    }

    async deletePerson(id) {
        return this.delete(this.config.ENDPOINTS.PEOPLE.DELETE, { id });
    }

    async getPersonById(id) {
        return this.get(this.config.ENDPOINTS.PEOPLE.GET_BY_ID, { id });
    }

    async getPeopleOrgUnits() {
        return this.get(this.config.ENDPOINTS.PEOPLE.GET_ORG_UNITS);
    }

    async getPeopleByOrgUnitId(id) {
        return this.get(this.config.ENDPOINTS.PEOPLE.GET_BY_ORG_UNIT, { id });
    }

    async getPersonResponsibilities(id) {
        return this.get(`/responsibilities/${id}`);
    }

    async getPersonFollowing(id) {
        return this.get(`/people/following/${id}`);
    }

    async getPersonActivity(id) {
        return this.get(`/people/activity/${id}`);
    }

    async removeFollowing(personId, followId) {
        return this.delete(`/people/following/${personId}/${followId}`);
    }

    async getPersonNotification(id) {
        return this.get(`/people/notification/${id}`);
    }

    async updatePersonNotification(id, frequency) {
        return this.put(`/people/notification/${id}`, { frequency });
    }

    async getPersonRoles(id) {
        return this.get(`/people/roles/${id}`);
    }

    async getPersonSegments(id) {
        return this.get(`/people/segments/${id}`);
    }

    async getPersonTeam(id) {
        return this.get(`/team/${id}`);
    }

    // Search persons
    async searchPersons(query) {
        return this.get('/people/search', { q: query });
    }

    // Team management API methods
    async addManagementRelationship(data) {
        return this.post('/team/management', {
            person_id: data.employeeId,  // Current person is the employee
            manager_id: data.managerId,  // Selected person is the manager
            relationship_type: data.relationType,
            description: data.description
        });
    }

    async addReportsRelationship(data) {
        return this.post('/team/reports', {
            manager_id: data.managerId,  // Current person is the manager
            report_id: data.employeeId,  // Selected person is the report
            relationship_type: data.relationType,
            description: data.description
        });
    }

    async removeTeamRelationship(relationshipId) {
        return this.delete(`/team/relationship/${relationshipId}`);
    }

    async updateTeamRelationship(relationshipId, relationshipType) {
        return this.put(`/team/relationship/${relationshipId}`, {
            relationship_type: relationshipType
        });
    }

    /* Added from EDITOR - Business Area API methods */
    async getBusinessAreas(params = {}) {
        return this.get(this.config.ENDPOINTS.BUSINESS_AREA.FETCH_ALL, params || {});
    }

    async searchBusinessAreas(query) {
        return this.get(this.config.ENDPOINTS.BUSINESS_AREA.SEARCH, { q: query });
    }

    async createBusinessArea(data) {
        return this.post(this.config.ENDPOINTS.BUSINESS_AREA.CREATE, data);
    }

    async updateBusinessArea(id, data) {
        return this.put(this.config.ENDPOINTS.BUSINESS_AREA.UPDATE, data, { id });
    }

    async deleteBusinessArea(id) {
        return this.delete(this.config.ENDPOINTS.BUSINESS_AREA.DELETE, { id });
    }

    async getBusinessAreaById(id) {
        return this.get(this.config.ENDPOINTS.BUSINESS_AREA.GET_BY_ID, { id });
    }

    async getBusinessAreaLifecycle() {
        return this.get(this.config.ENDPOINTS.BUSINESS_AREA.GET_LIFECYCLE);
    }

    async getBusinessAreaLifecycleById(id) {
        // Get all business area lifecycles and find the one with matching ID (like capability pattern)
        const response = await this.get(this.config.ENDPOINTS.BUSINESS_AREA.GET_LIFECYCLE);
        const lifecycles = response?.data || response;
        if (Array.isArray(lifecycles)) {
            return lifecycles.find(lifecycle => lifecycle.id === parseInt(id) || lifecycle.ID === parseInt(id)) || null;
        }
        return null;
    }

    async getBusinessAreaDropdown(params = {}) {
        return this.get(this.config.ENDPOINTS.BUSINESS_AREA.GET_DROPDOWN, params || {});
    }
    
    async getBusinessAreaHierarchy(id) {
        try {
            // Get all business areas first
            const allBusinessAreas = await this.getUnisonSearchData('business-area');
            if (!allBusinessAreas || allBusinessAreas.length === 0) {
                return [];
            }
            
            // Find the current business area
            const currentBusinessArea = allBusinessAreas.find(ba => ba.ID === parseInt(id));
            if (!currentBusinessArea) {
                return [];
            }
            
            const hierarchy = [];
            
            // Add current business area (level 0)
            hierarchy.push({
                id: currentBusinessArea.ID,
                parentId: currentBusinessArea.Parent_ID,
                name: currentBusinessArea.Name || currentBusinessArea.PrimaryName,
                description: currentBusinessArea.Description,
                typeName: 'Business Area',
                level: 0,
                relation: 'current'
            });
            
            // Get parent if exists
            if (currentBusinessArea.Parent_ID) {
                const parent = allBusinessAreas.find(ba => ba.ID === currentBusinessArea.Parent_ID);
                if (parent) {
                    hierarchy.push({
                        id: parent.ID,
                        parentId: parent.Parent_ID,
                        name: parent.Name || parent.PrimaryName,
                        description: parent.Description,
                        typeName: 'Business Area',
                        level: -1,
                        relation: 'ancestor'
                    });
                }
            }
            
            // Get children
            const children = allBusinessAreas.filter(ba => ba.Parent_ID === currentBusinessArea.ID);
            children.forEach(child => {
                hierarchy.push({
                    id: child.ID,
                    parentId: child.Parent_ID,
                    name: child.Name || child.PrimaryName,
                    description: child.Description,
                    typeName: 'Business Area',
                    level: 1,
                    relation: 'descendant'
                });
            });
            
            return hierarchy;
        } catch (error) {
            console.error('Error fetching business area hierarchy:', error);
            return [];
        }
    }

    /* Added from EDITOR - Capability API methods */
    async getCapabilities(params = {}) {
        return this.get(this.config.ENDPOINTS.CAPABILITY.FETCH_ALL, params || {});
    }

    async searchCapabilities(query) {
        return this.get(this.config.ENDPOINTS.CAPABILITY.SEARCH, { q: query });
    }

    async createCapability(data) {
        return this.post(this.config.ENDPOINTS.CAPABILITY.CREATE, data);
    }

    async updateCapability(id, data) {
        return this.put(this.config.ENDPOINTS.CAPABILITY.UPDATE, data, { id });
    }

    async deleteCapability(id) {
        return this.delete(this.config.ENDPOINTS.CAPABILITY.DELETE, { id });
    }

    async getCapabilityById(id) {
        return this.get(this.config.ENDPOINTS.CAPABILITY.GET_BY_ID, { id });
    }

    async getCapabilityTypes() {
        return this.get(this.config.ENDPOINTS.CAPABILITY.GET_TYPES);
    }

    async getCapabilityClassifications() {
        return this.get(this.config.ENDPOINTS.CAPABILITY.GET_CLASSIFICATIONS);
    }

    async getCapabilityLifecycle() {
        return this.get(this.config.ENDPOINTS.CAPABILITY.GET_LIFECYCLE);
    }

    async getCapabilityLifecycleById(id) {
        return this.get(this.config.ENDPOINTS.CAPABILITY.GET_LIFECYCLE_BY_ID, { id });
    }

    async getCapabilityDropdown() {
        return this.get(this.config.ENDPOINTS.CAPABILITY.GET_DROPDOWN);
    }

    /* Added from EDITOR - Capability Lookup Methods */
    async getCapabilityClassificationList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.CAPABILITY_CLASSIFICATION);
    }

    async getCapabilityLifecycleList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.CAPABILITY_LIFECYCLE);
    }

    async getCapabilityTypeList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.CAPABILITY_TYPE);
    }

    async getCapabilityClassificationById(id) {
        // Get all capability classifications and find the one with matching ID (like policy/product pattern)
        const response = await this.get(this.config.ENDPOINTS.LOOKUPS.CAPABILITY_CLASSIFICATION);
        const classifications = response?.data || response;
        if (Array.isArray(classifications)) {
            return classifications.find(classification => classification.id === parseInt(id) || classification.ID === parseInt(id)) || null;
        }
        return null;
    }

    async getCapabilityLifecycleByIdDirect(id) {
        // Get all capability lifecycles and find the one with matching ID (like policy/product pattern)
        const response = await this.get(this.config.ENDPOINTS.LOOKUPS.CAPABILITY_LIFECYCLE);
        const lifecycles = response?.data || response;
        if (Array.isArray(lifecycles)) {
            return lifecycles.find(lifecycle => lifecycle.id === parseInt(id) || lifecycle.ID === parseInt(id)) || null;
        }
        return null;
    }

    async getCapabilityTypeById(id) {
        // Get all capability types and find the one with matching ID (like policy/product pattern)
        const response = await this.get(this.config.ENDPOINTS.LOOKUPS.CAPABILITY_TYPE);
        const types = response?.data || response;
        if (Array.isArray(types)) {
            return types.find(type => type.id === parseInt(id) || type.ID === parseInt(id)) || null;
        }
        return null;
    }

    async getCapabilityHierarchy(id) {
        return this.get(this.config.ENDPOINTS.CAPABILITY.GET_HIERARCHY, { id });
    }

    // Status API methods
    async getStatuses() {
        return this.get(this.config.ENDPOINTS.STATUS.FETCH_ALL);
    }

    async searchStatuses(query) {
        return this.get(this.config.ENDPOINTS.STATUS.SEARCH, { q: query });
    }

    async createStatus(data) {
        return this.post(this.config.ENDPOINTS.STATUS.CREATE, data);
    }

    async updateStatus(id, data) {
        return this.put(this.config.ENDPOINTS.STATUS.UPDATE, data, { id });
    }

    async deleteStatus(id) {
        return this.delete(this.config.ENDPOINTS.STATUS.DELETE, { id });
    }

    async getStatusById(id) {
        return this.get(this.config.ENDPOINTS.STATUS.GET_BY_ID, { id });
    }

    async getStatusesForDropdown() {
        return this.get(this.config.ENDPOINTS.STATUS.GET_DROPDOWN);
    }

    _resolveSegmentIdFromContext(options = {}) {
        if (options && Number.isInteger(options.segmentId) && options.segmentId > 0) {
            return options.segmentId;
        }

        try {
            const fromUrl = parseInt(new URLSearchParams(window.location.search).get('segmentId'), 10);
            if (Number.isInteger(fromUrl) && fromUrl > 0) {
                return fromUrl;
            }
        } catch (_) { /* ignore */ }

        try {
            const cube = window.globalSegmentsCubePanel;
            if (cube && cube.selectedSegmentIds && typeof cube.selectedSegmentIds.size === 'number' && cube.selectedSegmentIds.size === 1) {
                const [single] = Array.from(cube.selectedSegmentIds);
                const parsed = parseInt(single, 10);
                if (Number.isInteger(parsed) && parsed > 0) {
                    return parsed;
                }
            }
        } catch (_) { /* ignore */ }

        return null;
    }

    // Lookup list APIs
    async getSystemsList(options = {}) {
        const params = {};
        const segmentId = this._resolveSegmentIdFromContext(options);
        if (Number.isInteger(segmentId)) {
            params.segmentId = segmentId;
        }
        return this.get(this.config.ENDPOINTS.LOOKUPS.SYSTEMS, params);
    }

    async getGlossaryList(options = {}) {
        const params = {};
        const segmentId = this._resolveSegmentIdFromContext(options);
        if (Number.isInteger(segmentId)) {
            params.segmentId = segmentId;
        }
        return this.get(this.config.ENDPOINTS.LOOKUPS.GLOSSARY, params);
    }

    async getStatusList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.STATUS_LIST);
    }

    async getDatasetTypes() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.DATASET_TYPES);
    }

    async getViewingList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.VIEWING);
    }

    async getLifecycleList() { return this.get(this.config.ENDPOINTS.LOOKUPS.LIFECYCLE); }
    async getDatasetLifecycleList() { return this.get(this.config.ENDPOINTS.LOOKUPS.DATASET_LIFECYCLE); }
    async getGlossaryLifecycleList() { return this.get(this.config.ENDPOINTS.LOOKUPS.GLOSSARY_LIFECYCLE); }
    async getSystemTypeList() { return this.get(this.config.ENDPOINTS.LOOKUPS.SYSTEM_TYPE); }
    async getGlossaryTypeList() { return this.get(this.config.ENDPOINTS.LOOKUPS.GLOSSARY_TYPE); }
    async getGlossaryKdeList() { return this.get(this.config.ENDPOINTS.LOOKUPS.GLOSSARY_KDE); }
    async getGlossarySecurityList() { return this.get(this.config.ENDPOINTS.LOOKUPS.GLOSSARY_SECURITY); }
    async getGlossaryFormatTypeList() { return this.get(this.config.ENDPOINTS.LOOKUPS.GLOSSARY_FORMAT_TYPE); }
    async getInterfaceLifecycleList() { return this.get(this.config.ENDPOINTS.LOOKUPS.INTERFACE_LIFECYCLE); }

    async getCiaRatings() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.CIA_RATING);
    }

    async getSystemClassifications() { return this.get(this.config.ENDPOINTS.LOOKUPS.CLASSIFICATION); }
    async getInterfaceClassifications() { return this.get(this.config.ENDPOINTS.LOOKUPS.INTERFACE_CLASSIFICATION); }

    async getInterfaceAutomation() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.INTERFACE_AUTOMATION);
    }

    async getInterfaceFrequencies() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.INTERFACE_FREQUENCY);
    }

    async getInterfaceTransferMethods() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.INTERFACE_TRANSFER_METHOD);
    }

    async getInterfaceTransferFormats() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.INTERFACE_TRANSFER_FORMAT);
    }
    async getProductLifecycleList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PRODUCT_LIFECYCLE);
    }

    async getPolicyLifecycleList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.POLICY_LIFECYCLE);
    }

    async getPolicyTypeList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.POLICY_TYPE);
    }
    
    async getPolicyTypes() {
        return this.get('/policy/policy-type');
    }
    
    async getAllPolicies() {
        return this.get('/policy');
    }

    /* Added from EDITOR: Policy lookup methods */
    async getPolicyLifecycleStatusById(id) {
        // Get all policy lifecycle statuses and find the one with matching ID
        const lifecycleStatuses = await this.get(this.config.ENDPOINTS.LOOKUPS.POLICY_LIFECYCLE);
        if (Array.isArray(lifecycleStatuses)) {
            return lifecycleStatuses.find(status => status.id === parseInt(id)) || null;
        }
        return null;
    }

    async getPolicyTypeById(id) {
        // Get all policy types и find the one with matching ID
        const policyTypes = await this.get(this.config.ENDPOINTS.LOOKUPS.POLICY_TYPE);
        if (Array.isArray(policyTypes)) {
            return policyTypes.find(type => type.id === parseInt(id)) || null;
        }
        return null;
    }

    async getProjectTypeList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROJECT_TYPE);
    }

    /* Added from EDITOR: Project lookup methods */
    async getProjectTypeById(id) {
        // Get all project types and find the one with matching ID
        const projectTypes = await this.get(this.config.ENDPOINTS.LOOKUPS.PROJECT_TYPE);
        if (Array.isArray(projectTypes)) {
            return projectTypes.find(type => type.id === parseInt(id)) || null;
        }
        return null;
    }

    async getProjectClassificationById(id) {
        // Get all project classifications and find the one with matching ID
        const classifications = await this.get(this.config.ENDPOINTS.LOOKUPS.PROJECT_CLASSIFICATION);
        if (Array.isArray(classifications)) {
            return classifications.find(classification => classification.id === parseInt(id)) || null;
        }
        return null;
    }

    async getProjectLifecycleById(id) {
        // Get all project lifecycles and find the one with matching ID
        const projectLifecycles = await this.get(this.config.ENDPOINTS.LOOKUPS.PROJECT_LIFECYCLE);
        if (Array.isArray(projectLifecycles)) {
            return projectLifecycles.find(lifecycle => lifecycle.id === parseInt(id)) || null;
        }
        return null;
    }

    async getProjectRagById(id) {
        // Get all project RAG statuses and find the one with matching ID
        const projectRags = await this.get(this.config.ENDPOINTS.LOOKUPS.PROJECT_RAG);
        if (Array.isArray(projectRags)) {
            return projectRags.find(rag => rag.id === parseInt(id)) || null;
        }
        return null;
    }

    async getProductLifecycleById(id) {
        // Get all product lifecycles and find the one with matching ID
        const productLifecycles = await this.get(this.config.ENDPOINTS.LOOKUPS.PRODUCT_LIFECYCLE);
        if (Array.isArray(productLifecycles)) {
            return productLifecycles.find(lifecycle => lifecycle.id === parseInt(id)) || null;
        }
        return null;
    }

    async getViewingById(id) {
        // Get all viewing options and find the one with matching ID
        const viewings = await this.get(this.config.ENDPOINTS.LOOKUPS.VIEWING);
        if (Array.isArray(viewings)) {
            return viewings.find(viewing => viewing.id === parseInt(id)) || null;
        }
        return null;
    }

    async getProcessLifecycleById(id) {
        // Get all process lifecycles and find the one with matching ID
        const processLifecycles = await this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_LIFECYCLE);
        if (Array.isArray(processLifecycles)) {
            return processLifecycles.find(lifecycle => lifecycle.id === parseInt(id)) || null;
        }
        return null;
    }

    async getProcessTypeById(id) {
        // Get all process types and find the one with matching ID
        const processTypes = await this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_TYPE);
        if (Array.isArray(processTypes)) {
            return processTypes.find(type => type.id === parseInt(id)) || null;
        }
        return null;
    }

    async getProcessClassById(id) {
        // Get all process classes and find the one with matching ID
        const processClasses = await this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_CLASS);
        if (Array.isArray(processClasses)) {
            return processClasses.find(cls => cls.id === parseInt(id)) || null;
        }
        return null;
    }

    async getProcessAutomationById(id) {
        // Get all process automation levels and find the one with matching ID
        const processAutomations = await this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_AUTOMATION);
        if (Array.isArray(processAutomations)) {
            return processAutomations.find(automation => automation.id === parseInt(id)) || null;
        }
        return null;
    }

    async getProjectClassificationList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROJECT_CLASSIFICATION);
    }

    async getProjectLifecycleList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROJECT_LIFECYCLE);
    }

    async getProjectRagList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROJECT_RAG);
    }
    
    async getProjectTypes() {
        return this.get('/project/project-type');
    }
    
    async getProjectClassifications() {
        return this.get('/project/classification/list');
    }
    
    async getAllProjects() {
        return this.get('/project');
    }

    // Process-specific API methods
    async getProcessLifecycleList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_LIFECYCLE);
    }

    async getProcessTypeList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_TYPE);
    }

    async getProcessClassList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_CLASS);
    }

    async getProcessAutomationList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_AUTOMATION);
    }

    async getProcessDurationTypeList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_DURATION_TYPE);
    }

    async getProcessStepTypeList() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_STEP_TYPE);
    }

    async getProcessList(options = {}) {
        const params = {};
        const segmentId = this._resolveSegmentIdFromContext(options);
        if (Number.isInteger(segmentId)) {
            params.segmentId = segmentId;
        }
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_LIST, params);
    }

    async getProcessParentPicker(excludeId = 0) {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PROCESS_PARENT_PICKER, { excludeId });
    }

    /* Added from EDITOR: getProcessById */
    async getProcessById(id, view = null) {
        const endpoint = this.config.ENDPOINTS.PROCESS.GET_BY_ID.replace('{id}', id);
        const params = {};
        if (view) {
            params.view = view;
        }
        return this.get(endpoint, params);
    }

    async getProcessHierarchy(processId) {
        return this.get(`/process/hierarchy/${processId}`);
    }
    
    async createProcess(payload) {
        return this.post(this.config.ENDPOINTS.PROCESS.CREATE, payload);
    }

    async getNextProcessRefNumber() {
        const endpoint =
            this.config?.ENDPOINTS?.PROCESS?.NEXT_REF ||
            '/process/next-ref';
        return this.get(endpoint);
    }

    async updateProcess(id, payload) {
        return this.put(this.config.ENDPOINTS.PROCESS.UPDATE, payload, { id });
    }

    async getStepTypeList() {
        const endpoint = this.config.ENDPOINTS?.LOOKUPS?.STEP_TYPE;
        if (endpoint) {
            return this.get(endpoint);
        }
        // Fallback to direct API call
        const baseUrl = this.api.getApiUrl ? this.api.getApiUrl('') : '/api';
        return fetch(`${baseUrl}/lookups/steptype`).then(r => r.json());
    }

    async getDurationTypeList() {
        const endpoint = this.config.ENDPOINTS?.LOOKUPS?.PROCESS_DURATION_TYPE;
        if (endpoint) {
            return this.get(endpoint);
        }
        // Fallback to direct API call
        const baseUrl = this.api.getApiUrl ? this.api.getApiUrl('') : '/api';
        return fetch(`${baseUrl}/process/duration-type/list`).then(r => r.json());
    }

    async getProcessParentOptions(currentId) {
        const endpoint =
            this.config?.ENDPOINTS?.PROCESS?.PARENT_PICKER ||
            this.config?.ENDPOINTS?.LOOKUPS?.PROCESS_PARENT_PICKER ||
            '/process/parent-picker';
        return this.get(endpoint, { currentId });
    }

    async getAllProcesses() {
        const endpoint =
            this.config?.ENDPOINTS?.PROCESS?.GET_ALL ||
            this.config?.ENDPOINTS?.PROCESS?.LIST ||
            this.config?.ENDPOINTS?.LOOKUPS?.PROCESS_LIST ||
            this.config?.ENDPOINTS?.PROCESS?.FETCH_ALL ||
            '/process/list';
        return this.get(endpoint);
    }

    // Employment Type API methods
    async getEmploymentTypes() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.EMPLOYMENT_TYPE);
    }

    async getEmploymentTypeById(id) {
        return this.get(this.config.ENDPOINTS.LOOKUPS.EMPLOYMENT_TYPE + '/' + id);
    }

    // People Lifecycle Status API methods
    async getLifecycleStatuses() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PEOPLE_LIFECYCLE);
    }

    async getLifecycleStatusById(id) {
        return this.get(this.config.ENDPOINTS.LOOKUPS.PEOPLE_LIFECYCLE + '/' + id);
    }

    async saveSystem(payload) {
        return this.post(this.config.ENDPOINTS.SYSTEM.SAVE, payload);
    }

    async getSystemById(id, view = null) {
        const endpoint = this.config.ENDPOINTS.SYSTEM.GET_BY_ID.replace('{id}', id);
        const params = {};
        if (view) {
            params.view = view;
        }
        return this.get(endpoint, params);
    }

    async updateSystem(id, payload) {
        const endpoint = this.config.ENDPOINTS.SYSTEM.GET_BY_ID.replace('{id}', id);
        return this.put(endpoint, payload);
    }

    async getSystemStakeholders(id) {
        return this.get(this.config.ENDPOINTS.SYSTEM.GET_STAKEHOLDERS, { id });
    }

    async getSystemRoles(id) {
        return this.get(this.config.ENDPOINTS.SYSTEM.GET_ROLES, { id });
    }

    async getSystemUsersByRole(id, roleId) {
        return this.get(this.config.ENDPOINTS.SYSTEM.GET_USERS_BY_ROLE, { id, roleId });
    }

    async getSystemStatuses(id) {
        return this.get(this.config.ENDPOINTS.SYSTEM.GET_STATUSES, { id });
    }

    async saveSystemStakeholders(id, stakeholders) {
        return this.post(this.config.ENDPOINTS.SYSTEM.SAVE_STAKEHOLDERS, stakeholders, { id });
    }

    async getSystemInterfaces(id, view = null) {
        // Endpoint shape: /system/{id}/interfaces
        const endpoint = this.config.ENDPOINTS.SYSTEM.GET_BY_ID.replace('{id}', id) + '/interfaces';
        const params = {};
        if (view) params.view = view;
        return this.get(endpoint, params);
    }

    async getDataFlowOutsideInterfaces(id) {
        // Endpoint shape: /system/data-flow-outside-interfaces?system_id={id}
        const endpoint = '/system/data-flow-outside-interfaces';
        return this.get(endpoint, { system_id: id });
    }

    async getSystemDataContent(id, view = null) {
        // Endpoint shape: /system/{id}/data-content
        const endpoint = this.config.ENDPOINTS.SYSTEM.GET_BY_ID.replace('{id}', id) + '/data-content';
        const params = {};
        if (view) params.view = view;
        return this.get(endpoint, params);
    }

    async getSystemHierarchy(id, view = null) {
        // Endpoint shape: /system/{id}/hierarchy
        const endpoint = this.config.ENDPOINTS.SYSTEM.GET_BY_ID.replace('{id}', id) + '/hierarchy';
        const params = {};
        if (view) params.view = view;
        return this.get(endpoint, params);
    }

    async deleteSystem(id) {
        // Support backend endpoint shape: DELETE /system/{id}
        const endpoint = this.config.ENDPOINTS.SYSTEM.GET_BY_ID.replace('{id}', id);
        return this.delete(endpoint);
    }

    async getSystemRelationTypes() {
        // GET /system/relation-types
        const base = this.config.ENDPOINTS.SYSTEM.GET_BY_ID.replace('{id}', '');
        const endpoint = base.endsWith('/') ? base + 'relation-types' : base + '/relation-types';
        return this.get(endpoint);
    }

    async saveSystemDataContent(id, rows) {
        // POST /system/{id}/data-content
        const endpoint = this.config.ENDPOINTS.SYSTEM.GET_BY_ID.replace('{id}', id) + '/data-content';
        return this.post(endpoint, { rows });
    }

    async saveGlossary(payload) {
        return this.post(this.config.ENDPOINTS.GLOSSARY.SAVE, payload);
    }

    // Dataset create
    async createDataset(data) {
        return this.post(this.config.ENDPOINTS.DATASET.CREATE, data);
    }

    // Dataset list (used by create page reference auto-generation)
    async getDatasetsList() {
        // No dedicated backend list endpoint for dataset create flow.
        // Keep this method for backward compatibility and let callers
        // use local fallback generation when list data is unavailable.
        return [];
    }

    async getDatasetById(id, view = null, opts = {}) {
        const params = { id };
        if (view) {
            params.view = view;
        }
        return this.request(this.config.ENDPOINTS.DATASET.GET_BY_ID, {
            method: 'GET',
            params,
            silent404: opts.silent404 === true
        });
    }

    async updateDataset(id, data) {
        const endpoint = this.config.ENDPOINTS.DATASET.UPDATE.replace('{id}', id);
        return this.put(endpoint, data);
    }

    // Lock API methods
    async acquireLock(moduleName, objectId, isPermanent = false) {
        return this.post('/lock', {
            moduleName: moduleName,
            objectId: objectId,
            isPermanent: isPermanent
        });
    }

    async releaseLock(moduleName, objectId) {
        return this.delete(`/lock/${moduleName}/${objectId}`);
    }

    async checkLock(moduleName, objectId) {
        return this.get(`/lock/${moduleName}/${objectId}`);
    }

    async getMyLocks() {
        return this.get('/lock/my-locks');
    }

    async getAllLocks() {
        return this.get('/lock');
    }

    async deleteLock(lockId) {
        return this.delete(`/lock/${lockId}`);
    }

    async togglePermanentLock(lockId, isPermanent) {
        return this.post(`/lock/${lockId}/toggle-permanent`, { isPermanent });
    }

    async releaseAllMyLocks() {
        return this.delete('/lock/release-all');
    }

    async deleteDataset(id) {
        const endpoint = this.config.ENDPOINTS.DATASET.DELETE.replace('{id}', id);
        return this.delete(endpoint);
    }

    async getDatasetStakeholders(id) {
        return this.get(this.config.ENDPOINTS.DATASET.GET_STAKEHOLDERS, { id });
    }

    async getDatasetRoles(id) {
        return this.get(this.config.ENDPOINTS.DATASET.GET_ROLES, { id });
    }

    async getDatasetUsersByRole(id, roleId) {
        return this.get(this.config.ENDPOINTS.DATASET.GET_USERS_BY_ROLE, { id, roleId });
    }

    async getDatasetStatuses(id) {
        return this.get(this.config.ENDPOINTS.DATASET.GET_STATUSES, { id });
    }

    async saveDatasetStakeholders(id, stakeholders) {
        return this.post(this.config.ENDPOINTS.DATASET.SAVE_STAKEHOLDERS, stakeholders, { id });
    }

    async getDatasetsByGlossaryId(glossaryId) {
        return this.get(this.config.ENDPOINTS.DATASET.GET_BY_GLOSSARY, { id: glossaryId });
    }

    // Glossary Data Tab methods
    async getGlossaryDatasets(glossaryId, rollup) {
        const url = rollup ? `/glossary-data/${glossaryId}/datasets?rollup=true` : `/glossary-data/${glossaryId}/datasets`;
        return this.get(url);
    }

    async getGlossaryAttributes(glossaryId, rollup) {
        const url = rollup ? `/glossary-data/${glossaryId}/attributes?rollup=true` : `/glossary-data/${glossaryId}/attributes`;
        return this.get(url);
    }

    // System Data Tab methods
    async getSystemDatasets(systemId, view = null) {
        const params = {};
        if (view) params.view = view;
        return this.get(`/system-data/${systemId}/datasets`, params);
    }

    async getSystemAttributes(systemId, view = null) {
        const params = {};
        if (view) params.view = view;
        return this.get(`/system-data/${systemId}/attributes`, params);
    }

    async saveInterface(data) {
        return this.post(this.config.ENDPOINTS.INTERFACE.SAVE, data);
    }

    async getInterfaceById(id) {
        return this.get(this.config.ENDPOINTS.INTERFACE.GET_BY_ID, { id });
    }

    async getInterfaceStakeholders(id) {
        return this.get(this.config.ENDPOINTS.INTERFACE.GET_STAKEHOLDERS, { id });
    }

    async getInterfaceRoles(id) {
        return this.get(this.config.ENDPOINTS.INTERFACE.GET_ROLES, { id });
    }

    async getInterfaceUsersByRole(id, roleId) {
        return this.get(this.config.ENDPOINTS.INTERFACE.GET_USERS_BY_ROLE, { id, roleId });
    }

    async getInterfaceStatuses(id) {
        return this.get(this.config.ENDPOINTS.INTERFACE.GET_STATUSES, { id });
    }

    async saveInterfaceStakeholders(id, stakeholders) {
        return this.post(this.config.ENDPOINTS.INTERFACE.SAVE_STAKEHOLDERS, stakeholders, { id });
    }

    async getGlossaryById(id, view = null) {
        const params = { id };
        if (view) {
            params.view = view;
        }
        return this.get(this.config.ENDPOINTS.GLOSSARY.GET_BY_ID, params);
    }

    async getGlossaryStakeholders(id) {
        return this.get(this.config.ENDPOINTS.GLOSSARY.GET_STAKEHOLDERS, { id });
    }

    async getGlossaryRoles(id) {
        return this.get(this.config.ENDPOINTS.GLOSSARY.GET_ROLES, { id });
    }

    async getGlossaryUsersByRole(id, roleId) {
        return this.get(this.config.ENDPOINTS.GLOSSARY.GET_USERS_BY_ROLE, { id, roleId });
    }

    async getGlossaryStatuses(id) {
        return this.get(this.config.ENDPOINTS.GLOSSARY.GET_STATUSES, { id });
    }

    async saveGlossaryStakeholders(id, stakeholders) {
        return this.post(this.config.ENDPOINTS.GLOSSARY.SAVE_STAKEHOLDERS, stakeholders, { id });
    }

    async getFormatTypes() {
        return this.get(this.config.ENDPOINTS.LOOKUPS.GLOSSARY_FORMAT_TYPE);
    }

    async updateGlossary(id, data) {
        return this.put(this.config.ENDPOINTS.GLOSSARY.GET_BY_ID, data, { id });
    }

    async getGlossaryHierarchy(id) {
        return this.get(this.config.ENDPOINTS.GLOSSARY.GET_HIERARCHY, { id });
    }

    async getGlossaryStrategicSource(id, view = null) {
        const params = { id };
        if (view === 'changes') {
            params.view = 'changes';
        }
        // Add cache-busting parameter to ensure fresh data when switching views
        // This matches the impact mechanism which always fetches fresh data
        params._t = new Date().getTime();
        // Note: When view is null, we don't add it to params, which means the backend will use "original" mode
        console.log('[API Service] getGlossaryStrategicSource called with id:', id, 'view:', view, 'params:', params);
        return this.get(this.config.ENDPOINTS.GLOSSARY.GET_STRATEGIC_SOURCE, params);
    }

    async getGlossaryRelationships() {
        return this.get('/glossary-x-glossary');
    }

    async getGlossaryRelationshipsBySourceId(sourceId, view = null) {
        const params = {};
        if (view === 'changes') {
            params.view = 'changes';
        }
        // Add cache-busting parameter to ensure fresh data when switching views
        params._t = new Date().getTime();
        return this.get('/glossary-x-glossary/source/' + sourceId, params);
    }

    async getGlossaryRelationshipsByTargetId(targetId) {
        return this.get('/glossary-x-glossary/target/' + targetId);
    }

    async getGlossaryBidirectionalRelationships(glossaryId, view = null) {
        const params = {};
        if (view === 'changes') {
            params.view = 'changes';
        }
        // Add cache-busting parameter to ensure fresh data when switching views
        params._t = new Date().getTime();
        return this.get('/glossary-x-glossary/bidirectional/' + glossaryId, params);
    }

    async getGlossaryRelationshipById(id) {
        return this.get('/glossary-x-glossary/' + id);
    }

    async createGlossaryRelationship(data) {
        return this.post('/glossary-x-glossary', data);
    }

    async updateGlossaryRelationship(id, data) {
        return this.put('/glossary-x-glossary/' + id, data);
    }

    async deleteGlossaryRelationship(id) {
        return this.delete('/glossary-x-glossary/' + id);
    }

    async getGlossaryRelationTypes() {
        return this.get('/glossary-x-glossary/relation-types');
    }

    async getInterfaceXGlossary(interfaceId) {
        return this.get(this.config.ENDPOINTS.INTERFACE.GET_X_GLOSSARY, { interfaceId });
    }
    async getInterfaceGlossaryRelationTypes() {
        return this.get(this.config.ENDPOINTS.INTERFACE.GET_X_GLOSSARY, { type: 'relation-types' });
    }

    async getInterfaceDataWithin(interfaceId) {
        const endpoint = '/interface/data-within';
        return this.get(endpoint, { interface_id: interfaceId });
    }

    async getInterfaceDataOutside(interfaceId) {
        const endpoint = '/interface/data-outside';
        return this.get(endpoint, { interface_id: interfaceId });
    }
    // Product API methods
    async createProduct(payload) {
        return this.post(this.config.ENDPOINTS.PRODUCT.CREATE, payload);
    }

    async updateProduct(id, payload) {
        return this.put(this.config.ENDPOINTS.PRODUCT.UPDATE, payload, { id });
    }

    async deleteProduct(id) {
        return this.delete(this.config.ENDPOINTS.PRODUCT.DELETE, { id });
    }

    async getProductById(id) {
        return this.get(this.config.ENDPOINTS.PRODUCT.GET_BY_ID, { id });
    }

    async getProductList() {
        return this.get(this.config.ENDPOINTS.PRODUCT.LIST);
    }

    async getProductParentOptions(excludeId = null) {
        const params = {};
        if (excludeId !== null && excludeId !== undefined && excludeId !== '') {
            params.excludeId = excludeId;
        }
        return this.get(this.config.ENDPOINTS.PRODUCT.PARENT_PICKER, params);
    }

    async searchProducts(query) {
        return this.get(this.config.ENDPOINTS.PRODUCT.SEARCH, { q: query });
    }

    async getProductHierarchy(productId) {
        try {
            // Get all products to build hierarchy
            const allProducts = await this.getProductList();
            const products = allProducts?.data || allProducts || [];
            
            console.log('All products loaded for hierarchy:', products.length);
            
            if (!Array.isArray(products) || products.length === 0) {
                console.log('No products found for hierarchy');
                return [];
            }
            
            // Find the current product
            const currentProduct = products.find(p => (p.id || p.ID) == productId);
            if (!currentProduct) {
                console.log('Current product not found for hierarchy');
                return [];
            }
            
            console.log('Current product found:', currentProduct);
            
            // Build hierarchy based on parentid relationships
            const hierarchy = [];
            
            // Add current product (level 0)
            hierarchy.push({
                id: currentProduct.id || currentProduct.ID,
                name: currentProduct.primaryname || currentProduct.PrimaryName || currentProduct.name || currentProduct.Name || 'Unnamed',
                description: currentProduct.description || currentProduct.Description || '',
                level: 0,
                parentId: currentProduct.parentid || currentProduct.parent_id || currentProduct.ParentID || currentProduct.Parent_ID
            });
            
            // Add parent products (negative levels)
            let parentId = currentProduct.parentid || currentProduct.parent_id || currentProduct.ParentID || currentProduct.Parent_ID;
            let level = -1;
            
            while (parentId) {
                const parent = products.find(p => (p.id || p.ID) == parentId);
                if (parent) {
                    hierarchy.push({
                        id: parent.id || parent.ID,
                        name: parent.primaryname || parent.PrimaryName || parent.name || parent.Name || 'Unnamed',
                        description: parent.description || parent.Description || '',
                        level: level,
                        parentId: parent.parentid || parent.parent_id || parent.ParentID || parent.Parent_ID
                    });
                    parentId = parent.parentid || parent.parent_id || parent.ParentID || parent.Parent_ID;
                    level--;
                } else {
                    break;
                }
            }
            
            // Add child products (positive levels)
            const childProducts = products.filter(p => (p.parentid || p.parent_id || p.ParentID || p.Parent_ID) == productId);
            childProducts.forEach((child, index) => {
                hierarchy.push({
                    id: child.id || child.ID,
                    name: child.primaryname || child.PrimaryName || child.name || child.Name || 'Unnamed',
                    description: child.description || child.Description || '',
                    level: 1,
                    parentId: child.parentid || child.parent_id || child.ParentID || child.Parent_ID
                });
            });
            
            console.log('Built product hierarchy:', hierarchy);
            return hierarchy;
            
        } catch (error) {
            console.error('Error building product hierarchy:', error);
            return [];
        }
    }

    // Policy API methods
    async createPolicy(payload) {
        return this.post(this.config.ENDPOINTS.POLICY.CREATE, payload);
    }

    async updatePolicy(id, payload) {
        return this.put(this.config.ENDPOINTS.POLICY.UPDATE, payload, { id });
    }

    async deletePolicy(id) {
        return this.delete(this.config.ENDPOINTS.POLICY.DELETE, { id });
    }

    async getPolicyById(id) {
        return this.get(this.config.ENDPOINTS.POLICY.GET_BY_ID, { id });
    }

    async getPolicyList(options = {}) {
        const params = {};
        const segmentId = this._resolveSegmentIdFromContext(options);
        if (Number.isInteger(segmentId)) {
            params.segmentId = segmentId;
        }
        return this.get(this.config.ENDPOINTS.POLICY.LIST, params);
    }

    async getPolicyParentOptions(options = {}) {
        const params = {};
        const segmentId = this._resolveSegmentIdFromContext(options);
        if (Number.isInteger(segmentId)) {
            params.segmentId = segmentId;
        }
        return this.get(this.config.ENDPOINTS.POLICY.PARENT_PICKER, params);
    }

    async searchPolicies(query) {
        return this.get(this.config.ENDPOINTS.POLICY.SEARCH, { query });
    }
    
    async getPolicyHierarchy(id) {
        try {
            // Use backend API to get full hierarchy (ancestors, siblings, current, descendants, sibling_children)
            const response = await this.get(`${this.config.ENDPOINTS.POLICY.HIERARCHY.replace('{id}', id)}`);
            
            // Handle different response formats
            let hierarchy = [];
            if (Array.isArray(response)) {
                hierarchy = response;
            } else if (response && Array.isArray(response.data)) {
                hierarchy = response.data;
            } else if (response && Array.isArray(response.items)) {
                hierarchy = response.items;
            }
            
            return hierarchy;
        } catch (error) {
            console.error('Error getting policy hierarchy:', error);
            return [];
        }
    }
    
    // Policy relationships methods - similar to glossary relationships
    async getPolicyRelationshipsBySourceId(sourceId) {
        return this.get('/policy-x-policy/source/' + sourceId);
    }

    async getPolicyRelationshipsByTargetId(targetId) {
        return this.get('/policy-x-policy/target/' + targetId);
    }

    async getPolicyRelationshipById(id) {
        return this.get('/policy-x-policy/' + id);
    }

    async createPolicyRelationship(data) {
        return this.post('/policy-x-policy', data);
    }

    async updatePolicyRelationship(id, data) {
        return this.put('/policy-x-policy/' + id, data);
    }

    async deletePolicyRelationship(id) {
        return this.delete('/policy-x-policy/' + id);
    }

    async getPolicyRelationTypes() {
        return this.get('/policy-x-policy/relation-types');
    }

    // Project API methods
    async createProject(payload) {
        return this.post(this.config.ENDPOINTS.PROJECT.CREATE, payload);
    }

    async updateProject(id, payload) {
        return this.put(this.config.ENDPOINTS.PROJECT.UPDATE, payload, { id });
    }

    async deleteProject(id) {
        return this.delete(this.config.ENDPOINTS.PROJECT.DELETE, { id });
    }

    async getProjectById(id) {
        return this.get(this.config.ENDPOINTS.PROJECT.GET_BY_ID, { id });
    }

    async getProjectList(options = {}) {
        const params = {};
        const segmentId = this._resolveSegmentIdFromContext(options);
        if (Number.isInteger(segmentId)) {
            params.segmentId = segmentId;
        }
        return this.get(this.config.ENDPOINTS.PROJECT.LIST, params);
    }

    async getProjectParentOptions(excludeId = null) {
        const params = {};
        if (excludeId !== null && excludeId !== undefined && excludeId !== '') {
            params.excludeId = excludeId;
        }
        return this.get(this.config.ENDPOINTS.PROJECT.PARENT_PICKER, params);
    }

    async searchProjects(query) {
        return this.get(this.config.ENDPOINTS.PROJECT.SEARCH, { query });
    }

    // Project relationships method - missing from original code
    async getProjectRelationshipsBySourceId(sourceId) {
        try {
            const response = await fetch(`/api/project/relationships/${sourceId}`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            return await response.json();
        } catch (error) {
            console.error('Error getting project relationships:', error);
            return [];
        }
    }

    // Project relationship deletion method - missing from original code
    async deleteProjectRelationship(relationshipId) {
        try {
            const response = await fetch(`/api/project/relationship/${relationshipId}`, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json',
                },
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            return await response.json();
        } catch (error) {
            console.error('Error deleting project relationship:', error);
            throw error;
        }
    }

// Org Unit / People / Role views
    async getOrgUnitById(id) {
        return this.get(this.config.ENDPOINTS.ORG_UNITS.GET_BY_ID, { id });
    }

    async getPersonById(id) {
        return this.get(this.config.ENDPOINTS.PEOPLE.GET_BY_ID, { id });
    }

    async getRoleById(id) {
        // Prefer /role/{id} if defined
        if (this.config.ENDPOINTS.ROLE && this.config.ENDPOINTS.ROLE.GET_BY_ID) {
            try {
                return await this.get(this.config.ENDPOINTS.ROLE.GET_BY_ID, { id });
            } catch (error) {
                // If endpoint not available (404), fallback to list below
                console.warn('GET role/{id} not available, falling back to GET /roles', error);
            }
        }
        // Fallback: fetch list then pick by id from configured /roles endpoint
        const rolesEndpoint = (this.config.ENDPOINTS.ROLE && this.config.ENDPOINTS.ROLE.GET_ALL) || '/roles';
        const list = await this.get(rolesEndpoint);
        if (Array.isArray(list)) {
            return list.find(r => String(r.id) === String(id)) || null;
        }
        // If wrapped
        if (list && Array.isArray(list.data)) {
            return list.data.find(r => String(r.id) === String(id)) || null;
        }
        return null;
    }

    // Authentication API methods
    async login(credentials) {
        return this.post(this.config.ENDPOINTS.AUTH.LOGIN, credentials);
    }

    async logout() {
        const result = await this.post(this.config.ENDPOINTS.AUTH.LOGOUT);
        this.setAuthToken(null);
        return result;
    }

    async refreshToken() {
        return this.post(this.config.ENDPOINTS.AUTH.REFRESH);
    }

    async validateToken() {
        return this.get(this.config.ENDPOINTS.AUTH.VALIDATE);
    }

    // Role API methods
    async getRolesForModule(moduleType, moduleId) {
        try {
            const endpoint = this.config.ENDPOINTS.ROLE.GET_FOR_MODULE.replace('{module}', moduleType).replace('{id}', moduleId);
            return this.get(endpoint);
        } catch (error) {
            console.warn('Roles endpoint not available, falling back to all roles:', error);
            // Fallback to getting all roles if module-specific endpoint fails
            return this.getAllRoles();
        }
    }

    async getAllRoles() {
        try {
            return this.get(this.config.ENDPOINTS.ROLE.GET_ALL);
        } catch (error) {
            console.warn('All roles endpoint not available:', error);
            // Return empty array if endpoint doesn't exist
            return [];
        }
    }

    async getRolesList() {
        return this.getAllRoles();
    }

    /**
     * Get only Admin and Web User roles for the Profile dropdown when an admin is doing People bulk upload.
     * Call this when entity is People and current user is admin (e.g. from bulk upload step).
     * @param {number} userId - Current user ID (uploader)
     * @returns {Promise<Array>} List of roles (Admin, Web User only)
     */
    async getRolesListForBulkUploadByAdmin(userId) {
        try {
            const endpoint = (this.config.ENDPOINTS.ROLE && this.config.ENDPOINTS.ROLE.GET_ALL) || '/api/roles';
            return this.get(endpoint, { forBulkUploadByAdmin: 1, userId: userId });
        } catch (error) {
            console.warn('Roles for bulk upload by admin endpoint not available:', error);
            return [];
        }
    }

    async getRoleAssignments(roleId) {
        try {
            const endpoint = this.config.ENDPOINTS.ROLE.GET_ASSIGNMENTS.replace('{id}', roleId);
            return this.get(endpoint);
        } catch (error) {
            console.warn('Role assignments endpoint not available:', error);
            return [];
        }
    }
     // Visit history
     async logVisit(payload) {
         try {
             const endpoint = '/history/visit';
             return await this.post(endpoint, payload);
         } catch (error) {
             // Silently handle authentication errors and other failures
             // Visit logging is not critical functionality
             if (error.message && error.message.includes('401')) {
                 console.log('Visit logging skipped - authentication required');
             } else {
                 console.log('Visit logging failed:', error.message);
             }
             return { success: false, error: error.message };
         }
        }

     async getRecentVisits(userId, limit = 30) {
          const endpoint = '/history/recent';
          return this.get(endpoint, { userId, limit });
        }

    // Legal Entity API methods
    async getLegalEntities(options = {}) {
        const params = {};
        const segmentId = this._resolveSegmentIdFromContext(options);
        if (Number.isInteger(segmentId)) {
            params.segmentId = segmentId;
        }
        return this.get(this.config.ENDPOINTS.LEGAL_ENTITY.FETCH_ALL, params);
    }

    async searchLegalEntities(query) {
        return this.get(this.config.ENDPOINTS.LEGAL_ENTITY.SEARCH, { q: query });
    }

    async createLegalEntity(data) {
        //('createLegalEntity called with data:', data);
        //('Using endpoint:', this.config.ENDPOINTS.LEGAL_ENTITY.CREATE);
        const result = await this.post(this.config.ENDPOINTS.LEGAL_ENTITY.CREATE, data);
        //('createLegalEntity result:', result);
        return result;
    }

    async updateLegalEntity(id, data) {
        //('updateLegalEntity called with:', { id, data });
        const result = await this.put(this.config.ENDPOINTS.LEGAL_ENTITY.UPDATE, data, { id });
        //('updateLegalEntity result:', result);
        return result;
    }

    async deleteLegalEntity(id) {
        return this.delete(this.config.ENDPOINTS.LEGAL_ENTITY.DELETE, { id });
    }

    async getLegalEntityById(id) {
        return this.get(this.config.ENDPOINTS.LEGAL_ENTITY.GET_BY_ID, { id });
    }

    // Legal Entity hierarchy method
    async getLegalHierarchy(id) {
        try {
            // Get all legal entities first
            const allLegalEntities = await this.getLegalEntities();
            const legalEntities = allLegalEntities?.data || allLegalEntities || [];
            
            if (!Array.isArray(legalEntities) || legalEntities.length === 0) {
                return [];
            }
            
            // Find the current legal entity
            const currentLegalEntity = legalEntities.find(le => (le.id || le.ID) == id);
            if (!currentLegalEntity) {
                return [];
            }
            
            const hierarchy = [];
            
            // Add current legal entity (level 0)
            hierarchy.push({
                id: currentLegalEntity.id || currentLegalEntity.ID,
                parentId: currentLegalEntity.parent_id || currentLegalEntity.Parent_ID || currentLegalEntity.parentId,
                name: currentLegalEntity.longname || currentLegalEntity.Name || currentLegalEntity.primaryname || currentLegalEntity.name || 'Unnamed',
                description: currentLegalEntity.Description || currentLegalEntity.description || '',
                typeName: 'Legal Entity',
                level: 0,
                relation: 'current',
                displayName: currentLegalEntity.longname || currentLegalEntity.Name || currentLegalEntity.primaryname || currentLegalEntity.name || 'Unnamed',
                longName: currentLegalEntity.longname || currentLegalEntity.Name || currentLegalEntity.primaryname || currentLegalEntity.name || 'Unnamed'
            });
            
            // Get parent if exists
            const parentId = currentLegalEntity.parent_id || currentLegalEntity.Parent_ID || currentLegalEntity.parentId;
            if (parentId) {
                const parent = legalEntities.find(le => (le.id || le.ID) == parentId);
                if (parent) {
                    hierarchy.push({
                        id: parent.id || parent.ID,
                        parentId: parent.parent_id || parent.Parent_ID || parent.parentId,
                        name: parent.longname || parent.Name || parent.primaryname || parent.name || 'Unnamed',
                        description: parent.Description || parent.description || '',
                        typeName: 'Legal Entity',
                        level: -1,
                        relation: 'ancestor',
                        displayName: parent.longname || parent.Name || parent.primaryname || parent.name || 'Unnamed',
                        longName: parent.longname || parent.Name || parent.primaryname || parent.name || 'Unnamed'
                    });
                }
            }
            
            // Get children
            const children = legalEntities.filter(le => (le.parent_id || le.Parent_ID || le.parentId) == id);
            children.forEach(child => {
                hierarchy.push({
                    id: child.id || child.ID,
                    parentId: child.parent_id || child.Parent_ID || child.parentId,
                    name: child.longname || child.Name || child.primaryname || child.name || 'Unnamed',
                    description: child.Description || child.description || '',
                    typeName: 'Legal Entity',
                    level: 1,
                    relation: 'descendant',
                    displayName: child.longname || child.Name || child.primaryname || child.name || 'Unnamed',
                    longName: child.longname || child.Name || child.primaryname || child.name || 'Unnamed'
                });
            });
            
            return hierarchy;
        } catch (error) {
            console.error('Error fetching legal entity hierarchy:', error);
            return [];
        }
    }

    // Client API methods
    async getClients(options = {}) {
        const params = {};
        const segmentId = this._resolveSegmentIdFromContext(options);
        if (Number.isInteger(segmentId)) {
            params.segmentId = segmentId;
        }
        return this.get(this.config.ENDPOINTS.CLIENT.FETCH_ALL, params);
    }

    async searchClients(query) {
        return this.get(this.config.ENDPOINTS.CLIENT.SEARCH, { q: query });
    }

    async createClient(data) {
        //('createClient called with data:', data);
        //('Using endpoint:', this.config.ENDPOINTS.CLIENT.CREATE);
        const result = await this.post(this.config.ENDPOINTS.CLIENT.CREATE, data);
        //('createClient result:', result);
        return result;
    }

    async updateClient(id, data) {
        //('updateClient called with:', { id, data });
        const result = await this.put(this.config.ENDPOINTS.CLIENT.UPDATE, data, { id });
        //('updateClient result:', result);
        return result;
    }

    async deleteClient(id) {
        return this.delete(this.config.ENDPOINTS.CLIENT.DELETE, { id });
    }

    async getClientById(id) {
        return this.get(this.config.ENDPOINTS.CLIENT.GET_BY_ID, { id });
    }

    async getClientStatusList() {
        return this.get(this.config.ENDPOINTS.CLIENT.STATUS_LIST);
    }

    async getClientLifecycleList() {
        return this.get(this.config.ENDPOINTS.CLIENT.LIFECYCLE_LIST);
    }

    async getClientViewingList() {
        return this.get(this.config.ENDPOINTS.CLIENT.VIEWING_LIST);
    }

    async getClientParentClients(options = {}) {
        const params = {};
        const segmentId = this._resolveSegmentIdFromContext(options);
        if (Number.isInteger(segmentId)) {
            params.segmentId = segmentId;
        }
        return this.get(this.config.ENDPOINTS.CLIENT.PARENT_CLIENTS, params);
    }

    // Committee API methods
    async getCommittees(options = {}) {
        const params = {};
        const segmentId = this._resolveSegmentIdFromContext(options);
        if (Number.isInteger(segmentId)) {
            params.segmentId = segmentId;
        }
        return this.get(this.config.ENDPOINTS.COMMITTEE.FETCH_ALL, params);
    }
    async searchCommittees(query) {
        return this.get(this.config.ENDPOINTS.COMMITTEE.SEARCH, { q: query });
    }
    async createCommittee(data) {
        //('createCommittee called with data:', data);
        //('Using endpoint:', this.config.ENDPOINTS.COMMITTEE.CREATE);
        const result = await this.post(this.config.ENDPOINTS.COMMITTEE.CREATE, data);
        //('createCommittee result:', result);
        return result;
    }
    async updateCommittee(id, data) {
        //('updateCommittee called with:', { id, data });
        const result = await this.put(this.config.ENDPOINTS.COMMITTEE.UPDATE, data, { id });
        //('updateCommittee result:', result);
        return result;
    }
    async deleteCommittee(id) {
        return this.delete(this.config.ENDPOINTS.COMMITTEE.DELETE, { id });
    }
    async getCommitteeById(id) {
        return this.get(this.config.ENDPOINTS.COMMITTEE.GET_BY_ID, { id });
    }
    async getCommitteeStatusList() {
        return this.get(this.config.ENDPOINTS.COMMITTEE.STATUS_LIST);
    }
    async getCommitteeLifecycleList() {
        return this.get(this.config.ENDPOINTS.COMMITTEE.LIFECYCLE_LIST);
    }
    async getCommitteeViewingList() {
        return this.get(this.config.ENDPOINTS.COMMITTEE.VIEWING_LIST);
    }
    async getCommitteeParentCommittees(options = {}) {
        const params = {};
        const segmentId = this._resolveSegmentIdFromContext(options);
        if (Number.isInteger(segmentId)) {
            params.segmentId = segmentId;
        }
        params.type = 'committees';
        return this.get(this.config.ENDPOINTS.COMMITTEE.PARENT_COMMITTEES, params);
    }

    // Committee hierarchy method
    async getCommitteeHierarchy(id) {
        try {
            // Get all committees first
            const allCommittees = await this.getCommittees();
            const committees = allCommittees?.data || allCommittees || [];
            
            if (!Array.isArray(committees) || committees.length === 0) {
                return [];
            }
            
            // Find the current committee
            const currentCommittee = committees.find(c => (c.id || c.ID) == id);
            if (!currentCommittee) {
                return [];
            }
            
            const hierarchy = [];
            
            // Add current committee (level 0)
            hierarchy.push({
                id: currentCommittee.id || currentCommittee.ID,
                parentId: currentCommittee.parent_id || currentCommittee.Parent_ID || currentCommittee.parentId,
                name: currentCommittee.Name || currentCommittee.PrimaryName || currentCommittee.primaryName || currentCommittee.name || 'Unnamed',
                description: currentCommittee.Description || currentCommittee.description || '',
                typeName: 'Committee',
                level: 0,
                relation: 'current',
                displayName: currentCommittee.Name || currentCommittee.PrimaryName || currentCommittee.primaryName || currentCommittee.name || 'Unnamed'
            });
            
            // Get parent if exists
            const parentId = currentCommittee.parent_id || currentCommittee.Parent_ID || currentCommittee.parentId;
            if (parentId) {
                const parent = committees.find(c => (c.id || c.ID) == parentId);
                if (parent) {
                    hierarchy.push({
                        id: parent.id || parent.ID,
                        parentId: parent.parent_id || parent.Parent_ID || parent.parentId,
                        name: parent.Name || parent.PrimaryName || parent.primaryName || parent.name || 'Unnamed',
                        description: parent.Description || parent.description || '',
                        typeName: 'Committee',
                        level: -1,
                        relation: 'ancestor',
                        displayName: parent.Name || parent.PrimaryName || parent.primaryName || parent.name || 'Unnamed'
                    });
                }
            }
            
            // Get children
            const children = committees.filter(c => (c.parent_id || c.Parent_ID || c.parentId) == id);
            children.forEach(child => {
                hierarchy.push({
                    id: child.id || child.ID,
                    parentId: child.parent_id || child.Parent_ID || child.parentId,
                    name: child.Name || child.PrimaryName || child.primaryName || child.name || 'Unnamed',
                    description: child.Description || child.description || '',
                    typeName: 'Committee',
                    level: 1,
                    relation: 'descendant',
                    displayName: child.Name || child.PrimaryName || child.primaryName || child.name || 'Unnamed'
                });
            });
            
            return hierarchy;
        } catch (error) {
            console.error('Error fetching committee hierarchy:', error);
            return [];
        }
    }

    // Committee relationships method
    async getCommitteeRelationshipsBySourceId(sourceId) {
        try {
            const response = await fetch(`/api/committee/relationships/${sourceId}`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
                credentials: 'include'
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('Error getting committee relationships:', error);
            return [];
        }
    }

    // Capability relationships method
    async getCapabilityRelationshipsBySourceId(sourceId) {
        try {
            const response = await fetch(`/api/capabilities/relationships/${sourceId}`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
                credentials: 'include'
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('Error getting capability relationships:', error);
            return [];
        }
    }
    
    /* Added from EDITOR - Helper function for UnisonSearch calls */
    async getUnisonSearchData(module, id = null) {
        const url = id 
            ? `http://${window.location.hostname}:8080/UnisonSearch/${module}/${id}`
            : `http://${window.location.hostname}:8080/UnisonSearch/${module}`;
        
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return await response.json();
    }

    /* Added from EDITOR - Hierarchy methods for different entities */
    async getProjectHierarchy(id) {
        try {
            // Get all projects first
            const allProjects = await this.getUnisonSearchData('project');
            if (!allProjects || allProjects.length === 0) {
                return [];
            }
            
            // Find the current project
            const currentProject = allProjects.find(p => p.ID === parseInt(id));
            if (!currentProject) {
                return [];
            }
            
            const hierarchy = [];
            
            // Add current project (level 0)
            hierarchy.push({
                id: currentProject.ID,
                parentId: currentProject.Parent_ID,
                name: currentProject.Name || currentProject.PrimaryName,
                description: currentProject.Description,
                typeName: 'Project',
                level: 0,
                relation: 'current'
            });
            
            // Get parent if exists
            if (currentProject.Parent_ID) {
                const parent = allProjects.find(p => p.ID === currentProject.Parent_ID);
                if (parent) {
                    hierarchy.push({
                        id: parent.ID,
                        parentId: parent.Parent_ID,
                        name: parent.Name || parent.PrimaryName,
                        description: parent.Description,
                        typeName: 'Project',
                        level: -1,
                    relation: 'ancestor'
                    });
                }
            }
            
            // Get children
            const children = allProjects.filter(p => p.Parent_ID === currentProject.ID);
            children.forEach(child => {
                hierarchy.push({
                    id: child.ID,
                    parentId: child.Parent_ID,
                    name: child.Name || child.PrimaryName,
                    description: child.Description,
                    typeName: 'Project',
                    level: 1,
                    relation: 'descendant'
                });
            });
            
            return hierarchy;
        } catch (error) {
            console.error('Error fetching project hierarchy:', error);
            return [];
        }
    }

    async getBusinessAreaHierarchy(id) {
        try {
            // Get all business areas first
            const allBusinessAreas = await this.getUnisonSearchData('business-area');
            if (!allBusinessAreas || allBusinessAreas.length === 0) {
                return [];
            }
            
            // Find the current business area
            const currentBusinessArea = allBusinessAreas.find(ba => ba.ID === parseInt(id));
            if (!currentBusinessArea) {
                return [];
            }
            
            const hierarchy = [];
            
            // Add current business area (level 0)
            hierarchy.push({
                id: currentBusinessArea.ID,
                parentId: currentBusinessArea.Parent_ID,
                name: currentBusinessArea.Name || currentBusinessArea.PrimaryName,
                description: currentBusinessArea.Description,
                typeName: 'Business Area',
                level: 0,
                relation: 'current'
            });
            
            // Get parent if exists
            if (currentBusinessArea.Parent_ID) {
                const parent = allBusinessAreas.find(ba => ba.ID === currentBusinessArea.Parent_ID);
                if (parent) {
                    hierarchy.push({
                        id: parent.ID,
                        parentId: parent.Parent_ID,
                        name: parent.Name || parent.PrimaryName,
                        description: parent.Description,
                        typeName: 'Business Area',
                        level: -1,
                        relation: 'ancestor'
                    });
                }
            }
            
            // Get children
            const children = allBusinessAreas.filter(ba => ba.Parent_ID === currentBusinessArea.ID);
            children.forEach(child => {
                hierarchy.push({
                    id: child.ID,
                    parentId: child.Parent_ID,
                    name: child.Name || child.PrimaryName,
                    description: child.Description,
                    typeName: 'Business Area',
                    level: 1,
                    relation: 'descendant'
                });
            });
            
            return hierarchy;
        } catch (error) {
            console.error('Error fetching business area hierarchy:', error);
            return [];
        }
    }

    // Change Request API methods
    async getChangeRequestById(id) {
        return this.get(`/api/changerequests/${id}`);
    }

    async createChangeRequest(data) {
        return this.post('/api/changerequests', data);
    }

    async updateChangeRequest(id, data) {
        return this.put(`/api/changerequests/${id}`, data);
    }

    async deleteChangeRequest(id) {
        return this.delete(`/api/changerequests/${id}`);
    }

    async getChangeRequests() {
        return this.get('/api/changerequests');
    }

    async getChangeRequestsByReference(reference) {
        return this.get(`/api/changerequests?reference=${encodeURIComponent(reference)}`);
    }

    async getPersonChangeRequests(userId) {
        // Get all change requests and filter by createdBy on frontend
        // Alternatively, backend could support ?createdBy=userId parameter
        // Note: getApiUrl already includes /api in base URL, so use /changerequests not /api/changerequests
        // Add cache-busting parameter to ensure fresh data
        const timestamp = new Date().getTime();
        const allChangeRequests = await this.get(`/changerequests?t=${timestamp}`);
        if (Array.isArray(allChangeRequests)) {
            return allChangeRequests.filter(cr => {
                const createdBy = cr.createdBy || cr.Created_By;
                return createdBy === userId || createdBy === parseInt(userId);
            });
        }
        return [];
    }

    async getPersonContributingChangeRequests(userId) {
        // Get change requests where the user is a stakeholder (even if not the creator)
        // Note: getApiUrl already includes /api in base URL, so use /changerequests not /api/changerequests
        // Add cache-busting parameter to ensure fresh data
        const timestamp = new Date().getTime();
        return this.get(`/changerequests?stakeholder=${userId}&t=${timestamp}`);
    }
}

// Patch global fetch so raw fetch() calls send CSRF header for mutating /api, /auth, /admin requests
(function patchFetchWithCsrf() {
    if (typeof window === 'undefined' || window.__budgCsrfFetchPatched) return;
    window.__budgCsrfFetchPatched = true;
    const origFetch = window.fetch.bind(window);
    const UNSAFE = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
    function pathNeedsCsrf(url) {
        if (!url || typeof url !== 'string') return false;
        let path = url;
        if (url.startsWith('http')) {
            try { path = new URL(url).pathname; } catch (e) { return false; }
        } else {
            path = url.split('?')[0].split('#')[0];
        }
        return path.startsWith('/api/') || path.startsWith('/auth/') || path.startsWith('/admin/');
    }
    function readXsrf() {
        try {
            const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return m ? decodeURIComponent(m[1].trim()) : null;
        } catch (e) { return null; }
    }
    window.fetch = function (input, init) {
        init = init ? { ...init } : {};
        const method = (init.method || 'GET').toUpperCase();
        const url = typeof input === 'string' ? input : (input && input.url) || '';
        if (!UNSAFE.has(method) || !pathNeedsCsrf(url)) {
            return origFetch(input, init);
        }
        const tok = readXsrf();
        if (!tok) return origFetch(input, init);
        const headers = new Headers(init.headers || {});
        if (!headers.has('X-XSRF-TOKEN')) headers.set('X-XSRF-TOKEN', tok);
        init.headers = headers;
        return origFetch(input, init);
    };
})();

// Create and export the API service instance
window.BUDG_API_SERVICE = new ApiService();

/**
 * Map server error message to translated create/save error (duplicate name or reference).
 * Use on create pages when save fails. Returns { key, params, focus } for I18n.t(key, params) and focus field, or null.
 * @param {string} serverMessage - error.message or response.error or response.message
 * @param {string} facetName - e.g. 'Organization Units', 'Projects', 'Business Areas'
 * @returns {{ key: string, params: { facet: string }, focus: 'name'|'reference' }|null}
 */
window.getCreateSaveErrorInfo = function(serverMessage, facetName) {
    if (!serverMessage || typeof serverMessage !== 'string') return null;
    const m = serverMessage.toLowerCase();
    const alreadyTaken = m.includes('already exists') || m.includes('already in use');
    if (m.includes('email') && m.includes('already exists')) return { key: 'createPage.message.duplicateEmail', params: {}, focus: 'email' };
    if (m.includes('name') && alreadyTaken) return { key: 'createPage.message.duplicateName', params: { facet: facetName }, focus: 'name' };
    if ((m.includes('reference') || m.includes('ref')) && alreadyTaken) return { key: 'createPage.message.duplicateRefNumber', params: { facet: facetName }, focus: 'reference' };
    return null;
};


