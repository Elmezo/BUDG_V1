// EDC API Test JavaScript

let currentSettings = null;

/**
 * Load EDC settings from backend and populate form fields
 */
async function loadEdcSettings() {
    try {
        showStatus('Loading saved EDC settings...', 'loading');
        
        const response = await fetch('/api/admin/settings/edc', {
            method: 'GET',
            credentials: 'include'
        });
        
        if (!response.ok) {
            if (response.status === 403) {
                showStatus('Access denied: SuperAdmin role required', 'error');
                return;
            }
            throw new Error('Failed to load EDC settings');
        }
        
        const data = await response.json();
        currentSettings = data;
        
        // Populate form fields
        if (data.eic_server_host) {
            document.getElementById('serverHost').value = data.eic_server_host;
        }
        if (data.eic_server_port) {
            document.getElementById('serverPort').value = data.eic_server_port;
        }
        if (data.eic_server_login_username) {
            document.getElementById('username').value = data.eic_server_login_username;
        }
        // Password is masked, so we don't populate it
        if (data.eic_ssl_insecure !== undefined) {
            document.getElementById('sslInsecure').checked = data.eic_ssl_insecure;
        }
        
        showStatus('EDC settings loaded successfully', 'success');
        displayResults('EDC Settings Loaded:\n' + JSON.stringify(data, null, 2));
    } catch (error) {
        showStatus('Failed to load EDC settings: ' + error.message, 'error');
        displayResults('Error: ' + error.message);
    }
}

/**
 * Get connection settings from form
 */
function getConnectionSettings() {
    return {
        host: document.getElementById('serverHost').value.trim(),
        port: parseInt(document.getElementById('serverPort').value) || 9185,
        username: document.getElementById('username').value.trim(),
        password: document.getElementById('password').value,
        sslInsecure: document.getElementById('sslInsecure').checked
    };
}

/**
 * Encode EDC Object ID for URL
 * EDC uses special encoding: : becomes ~3a~, / becomes ~2f~
 * Example: DWH://orcl/CRM/CUSTOMER_MASTER -> DWH~3a~~2f~~2f~orcl~2f~CRM~2f~CUSTOMER_MASTER
 */
function encodeEdcObjectId(objectId) {
    if (!objectId) return '';
    // Replace : with ~3a~ (3a is hex for :)
    // Replace / with ~2f~ (2f is hex for /)
    return objectId
        .replace(/:/g, '~3a~')
        .replace(/\//g, '~2f~');
}

/**
 * Build EDC URL
 */
function buildEdcUrl(endpoint) {
    const settings = getConnectionSettings();
    const baseUrl = settings.host.replace(/\/$/, '');
    const port = settings.port ? `:${settings.port}` : '';
    const path = endpoint.startsWith('/') ? endpoint : '/' + endpoint;
    return `${baseUrl}${port}${path}`;
}

/**
 * Call EDC API via backend proxy
 */
async function callEdcApi(method, endpoint, body = null) {
    const settings = getConnectionSettings();
    
    // Validate required fields
    if (!settings.host) {
        throw new Error('Server Host is required');
    }
    if (!settings.username) {
        throw new Error('Username is required');
    }
    if (!settings.password) {
        throw new Error('Password is required');
    }
    
    const url = buildEdcUrl(endpoint);
    
    // Use backend proxy to avoid CORS issues
    const proxyUrl = '/api/test/edc-proxy';
    
    const requestBody = {
        method: method,
        url: url,
        username: settings.username,
        password: settings.password,
        sslInsecure: settings.sslInsecure,
        body: body
    };
    
    const response = await fetch(proxyUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify(requestBody)
    });
    
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
        throw new Error(errorData.error || `HTTP ${response.status}: ${response.statusText}`);
    }
    
    return await response.json();
}

/**
 * Test Product Information API
 */
async function testProductInformation() {
    try {
        showStatus('Testing Product Information...', 'loading');
        displayResults('Calling: GET /access/2/catalog/data/productInformation\n\n');
        
        const result = await callEdcApi('GET', '/access/2/catalog/data/productInformation');
        
        showStatus('Product Information retrieved successfully!', 'success');
        displayResults(JSON.stringify(result, null, 2));
        
        console.log('Product Information:', result);
    } catch (error) {
        showStatus('Product Information test failed: ' + error.message, 'error');
        displayResults('Error: ' + error.message + '\n\n' + error.stack);
        console.error('Product Information test error:', error);
    }
}

/**
 * Test Search API
 */
async function testSearch() {
    try {
        const query = document.getElementById('searchQuery').value.trim() || 'customer';
        showStatus(`Testing Search with query: "${query}"...`, 'loading');
        
        // Search API uses GET with query parameters, not POST with body
        const endpoint = `/access/2/catalog/data/search?q=${encodeURIComponent(query)}&tabId=all`;
        displayResults(`Calling: GET ${endpoint}\nQuery: "${query}"\n\n`);
        
        const result = await callEdcApi('GET', endpoint);
        
        showStatus(`Search completed! Found ${result.metadata?.totalCount || 0} results`, 'success');
        displayResults(JSON.stringify(result, null, 2));
        
        console.log('Search Results:', result);
    } catch (error) {
        showStatus('Search test failed: ' + error.message, 'error');
        displayResults('Error: ' + error.message + '\n\n' + error.stack);
        console.error('Search test error:', error);
    }
}

/**
 * Test Get Object API
 */
async function testGetObject() {
    try {
        const objectId = document.getElementById('objectId').value.trim();
        
        if (!objectId) {
            showStatus('Please enter an Object ID', 'error');
            displayResults('Error: Object ID is required\n\nExample Object IDs from search results:\n' +
                          '- DWH://orcl/CRM/CUSTOMER_MASTER\n' +
                          '- bussma_budg://InfaBudg/BudgGlossary/17\n' +
                          '- BUDG_Resource_copy://InfaBudg/BudgGlossary/1');
            return;
        }
        
        // Encode object ID for EDC URL
        const encodedObjectId = encodeEdcObjectId(objectId);
        
        showStatus(`Testing Get Object with ID: "${objectId}"...`, 'loading');
        displayResults(`Calling: GET /access/2/catalog/data/objects/${encodedObjectId}\n` +
                      `Original ID: ${objectId}\n` +
                      `Encoded ID: ${encodedObjectId}\n\n`);
        
        const result = await callEdcApi('GET', `/access/2/catalog/data/objects/${encodedObjectId}`);
        
        showStatus('Object retrieved successfully!', 'success');
        displayResults(JSON.stringify(result, null, 2));
        
        console.log('Object Details:', result);
    } catch (error) {
        showStatus('Get Object test failed: ' + error.message, 'error');
        displayResults('Error: ' + error.message + '\n\n' + 
                      'Note: Object ID must be in format like:\n' +
                      '- DWH://orcl/CRM/CUSTOMER_MASTER\n' +
                      '- bussma_budg://InfaBudg/BudgGlossary/17\n\n' +
                      error.stack);
        console.error('Get Object test error:', error);
    }
}

/**
 * Display results in the results container
 */
function displayResults(text) {
    const output = document.getElementById('resultsOutput');
    output.textContent = text;
}

/**
 * Show status indicator
 */
function showStatus(message, type) {
    const indicator = document.getElementById('statusIndicator');
    indicator.textContent = message;
    indicator.className = 'status-indicator ' + type;
    indicator.style.display = 'block';
}

// Load EDC settings on page load
document.addEventListener('DOMContentLoaded', function() {
    // Try to load saved settings, but don't show error if it fails
    loadEdcSettings().catch(() => {
        // Silently fail - user can manually load settings
    });
});

