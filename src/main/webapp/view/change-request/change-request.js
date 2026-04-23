(function() {
    'use strict';

    // Get URL parameters
    function getUrlParams() {
        const params = new URLSearchParams(window.location.search);
        return {
            facetType: params.get('facetType') || '',
            facetId: params.get('facetId') || '',
            facetName: params.get('facetName') || ''
        };
    }

    // Initialize page
    async function init() {
        const params = getUrlParams();
        
        // Validate: Cannot raise CR for restricted facets
        const restrictedFacets = ['regulatory-theme', 'geography', 'regulator', 'people', 'legal-entity', 'org-unit'];
        if (params.facetType && restrictedFacets.includes(params.facetType)) {
            const facetDisplayName = getFacetDisplayName(params.facetType);
            alert(`Change requests cannot be raised for ${facetDisplayName} facets.`);
            // Redirect back to the previous page or close the window
            if (window.history.length > 1) {
                window.history.back();
            } else {
                window.location.href = '/';
            }
            return;
        }
        
        // Update page title with facet information
        if (params.facetName && params.facetType) {
            const facetInfo = document.getElementById('facetInfo');
            if (facetInfo) {
                const facetDisplayName = getFacetDisplayName(params.facetType);
                facetInfo.textContent = `${params.facetName} | ${facetDisplayName}`;
            }
        }

        // Load dropdown options
        await loadDropdownOptions();

        // Load parent change requests for the same facet
        await loadParentChangeRequests(params);

        // Setup event handlers
        setupEventHandlers(params);
    }

    // Get display name for facet type
    function getFacetDisplayName(facetType) {
        const displayNames = {
            'system': 'System',
            'dataset': 'Data Set',
            'business-area': 'Business Area',
            'capability': 'Capability',
            'client': 'Client',
            'committee': 'Committee',
            'geography': 'Geography',
            'glossary': 'Glossary',
            'legal-entity': 'Legal Entity',
            'org-unit': 'Org Unit',
            'people': 'People',
            'policy': 'Policy',
            'process': 'Process',
            'product': 'Product',
            'project': 'Project',
            'regulation': 'Regulation',
            'regulator': 'Regulator',
            'regulatory-theme': 'Regulatory Theme',
            'system-interface': 'System Interface'
        };
        return displayNames[facetType] || 'Change Request';
    }

    // Load dropdown options
    async function loadDropdownOptions() {
        try {
            // Load Change Request Types
            const typesResponse = await fetch('/api/changerequest_types');
            if (typesResponse.ok) {
                const types = await typesResponse.json();
                const typeSelect = document.getElementById('type');
                if (typeSelect) {
                    types.forEach(type => {
                        const option = document.createElement('option');
                        option.value = type.id;
                        option.textContent = type.name || type.PrimaryName;
                        typeSelect.appendChild(option);
                    });
                    // Set default to "Request For Change" if available
                    const defaultOption = Array.from(typeSelect.options).find(opt => 
                        opt.textContent.toLowerCase().includes('request for change') ||
                        opt.textContent.toLowerCase().includes('change request')
                    );
                    if (defaultOption) {
                        typeSelect.value = defaultOption.value;
                    }
                }
            }

            // Load Change Request Systems (placeholder - adjust API endpoint as needed)
            const systemsResponse = await fetch('/api/changerequest_systems').catch(() => null);
            if (systemsResponse && systemsResponse.ok) {
                const systems = await systemsResponse.json();
                const systemSelect = document.getElementById('changeRequestSystem');
                if (systemSelect) {
                    // Clear existing options except "Please select"
                    systemSelect.innerHTML = '<option value="">Please select</option>';
                    
                    systems.forEach(system => {
                        const option = document.createElement('option');
                        option.value = system.id || system.ID;
                        option.textContent = system.name || system.PrimaryName || system.Name;
                        systemSelect.appendChild(option);
                    });
                    
                    // Fetch default system setting from admin panel AFTER options are added
                    try {
                        console.log('[Change Request] Fetching default system setting...');
                        const settingsResponse = await fetch('/api/system-settings/Change Requests/default_change_request_systems', {
                            credentials: 'include'
                        });
                        console.log('[Change Request] Settings response status:', settingsResponse?.status);
                        
                        if (settingsResponse && settingsResponse.ok) {
                            const setting = await settingsResponse.json();
                            console.log('[Change Request] Setting data:', setting);
                            const defaultSystem = setting.value; // Should be "None" or "Native"
                            console.log('[Change Request] Default system value:', defaultSystem);
                            
                            if (defaultSystem && defaultSystem !== 'None' && defaultSystem.trim() !== '' && defaultSystem.toLowerCase() !== 'none') {
                                // Find and select the option matching the default system (case-insensitive)
                                const matchingOption = Array.from(systemSelect.options).find(opt => 
                                    opt.value !== '' && opt.textContent.toLowerCase() === defaultSystem.toLowerCase()
                                );
                                if (matchingOption) {
                                    console.log('[Change Request] Found matching option, selecting:', matchingOption.value, matchingOption.textContent);
                                    systemSelect.value = matchingOption.value;
                                } else {
                                    console.warn('[Change Request] No matching option found for:', defaultSystem, 'Available options:', Array.from(systemSelect.options).map(o => o.textContent));
                                    // Don't set any default if no match found - leave as "Please select"
                                    systemSelect.value = '';
                                }
                            } else {
                                // Setting is "None" or empty - don't pre-select, leave as "Please select"
                                console.log('[Change Request] Setting is "None" or empty, leaving dropdown as "Please select"');
                                systemSelect.value = '';
                            }
                        } else {
                            const errorText = await settingsResponse.text().catch(() => 'Unknown error');
                            console.warn('[Change Request] Failed to fetch setting, status:', settingsResponse?.status, 'Error:', errorText);
                            // If setting fetch fails, don't set any default - leave as "Please select"
                            systemSelect.value = '';
                        }
                    } catch (error) {
                        console.error('[Change Request] Error fetching default change request system setting:', error);
                        // Don't set any default on error - leave as "Please select"
                        systemSelect.value = '';
                    }
                }
            } else {
                // Fallback: Add "Native" option if API doesn't exist
                const systemSelect = document.getElementById('changeRequestSystem');
                if (systemSelect) {
                    // Clear existing options except "Please select"
                    systemSelect.innerHTML = '<option value="">Please select</option>';
                    
                    const nativeOption = document.createElement('option');
                    nativeOption.value = 'native';
                    nativeOption.textContent = 'Native';
                    systemSelect.appendChild(nativeOption);
                    
                    // Try to fetch default setting
                    try {
                        console.log('[Change Request] Fetching default system setting (fallback path)...');
                        const settingsResponse = await fetch('/api/system-settings/Change Requests/default_change_request_systems', {
                            credentials: 'include'
                        });
                        if (settingsResponse && settingsResponse.ok) {
                            const setting = await settingsResponse.json();
                            const defaultSystem = setting.value;
                            console.log('[Change Request] Default system value (fallback):', defaultSystem);
                            if (defaultSystem && defaultSystem !== 'None' && defaultSystem.trim() !== '' && defaultSystem.toLowerCase() === 'native') {
                                systemSelect.value = 'native';
                            } else {
                                // Leave unselected if "None" or empty
                                console.log('[Change Request] Setting is "None" or empty (fallback), leaving as "Please select"');
                                systemSelect.value = '';
                            }
                        } else {
                            // Don't set default if API fails
                            const errorText = await settingsResponse.text().catch(() => 'Unknown error');
                            console.warn('[Change Request] Failed to fetch setting (fallback), status:', settingsResponse?.status, 'Error:', errorText);
                            systemSelect.value = '';
                        }
                    } catch (error) {
                        console.error('[Change Request] Error fetching default change request system setting (fallback):', error);
                        // Don't set default on error
                        systemSelect.value = '';
                    }
                }
            }

            // Load Severity options
            await loadLookupOptions('severity', '/api/changerequest_severity', [
                { id: 1, name: 'Low' },
                { id: 2, name: 'Medium' },
                { id: 3, name: 'High' },
                { id: 4, name: 'Critical' }
            ]);

            // Load Urgency options
            await loadLookupOptions('urgency', '/api/changerequest_urgency', [
                { id: 1, name: 'Low' },
                { id: 2, name: 'Medium' },
                { id: 3, name: 'High' },
                { id: 4, name: 'Critical' }
            ]);

            // Load Currency options
            await loadCurrencyOptions();

        } catch (error) {
            console.error('Error loading dropdown options:', error);
        }
    }

    // Load lookup options with fallback
    async function loadLookupOptions(selectId, apiEndpoint, fallbackOptions) {
        const select = document.getElementById(selectId);
        if (!select) return;

        try {
            const response = await fetch(apiEndpoint);
            if (response.ok) {
                const options = await response.json();
                options.forEach(option => {
                    const opt = document.createElement('option');
                    opt.value = option.id || option.ID;
                    opt.textContent = option.name || option.PrimaryName || option.Name;
                    select.appendChild(opt);
                });
                // Set default to "Low" if available
                const lowOption = Array.from(select.options).find(opt => 
                    opt.textContent.toLowerCase().includes('low')
                );
                if (lowOption) {
                    select.value = lowOption.value;
                }
            } else {
                throw new Error('API not available');
            }
        } catch (error) {
            // Use fallback options
            fallbackOptions.forEach(option => {
                const opt = document.createElement('option');
                opt.value = option.id;
                opt.textContent = option.name;
                select.appendChild(opt);
            });
            // Set default to "Low"
            const lowOption = select.querySelector('option[value="1"]');
            if (lowOption) {
                select.value = '1';
            }
        }
    }

    // Load currency options with fallback
    async function loadCurrencyOptions() {
        try {
            const response = await fetch('/api/changerequest_currencies');
            if (response.ok) {
                const currencies = await response.json();
                
                // Update both currency selects
                const benefitCurrencySelect = document.getElementById('estimatedBenefitCurrency');
                const costCurrencySelect = document.getElementById('estimatedCostCurrency');
                
                [benefitCurrencySelect, costCurrencySelect].forEach(select => {
                    if (select) {
                        // Clear existing options except the first one
                        select.innerHTML = '';
                        
                        currencies.forEach(currency => {
                            const option = document.createElement('option');
                            option.value = currency.name || currency.PrimaryName;
                            option.textContent = currency.name || currency.PrimaryName;
                            select.appendChild(option);
                        });
                        
                        // Set default to GBP if available
                        const gbpOption = Array.from(select.options).find(opt => 
                            opt.value === 'GBP'
                        );
                        if (gbpOption) {
                            select.value = 'GBP';
                        }
                    }
                });
            } else {
                throw new Error('Currency API not available');
            }
        } catch (error) {
            console.warn('Using fallback currency options:', error);
            // Fallback currencies are already in HTML
        }
    }

    // Capitalize first letter of each word (matches backend capitalizeFirst function)
    function capitalizeFirst(str) {
        if (!str || str.length === 0) {
            return str;
        }
        
        const words = str.split(/\s+/);
        const result = [];
        
        for (let i = 0; i < words.length; i++) {
            if (words[i].length > 0) {
                const word = words[i];
                result.push(word.charAt(0).toUpperCase() + word.slice(1).toLowerCase());
            }
        }
        
        return result.join(' ');
    }

    // Build reference string exactly as backend does
    function buildReference(facetType, facetId) {
        // Match backend: capitalizeFirst(facetType.replace("-", " ")) + " " + facetId
        const normalizedType = facetType.replace(/-/g, ' ');
        const capitalizedType = capitalizeFirst(normalizedType);
        return capitalizedType + ' ' + facetId;
    }

    // Load parent change requests for the same facet
    async function loadParentChangeRequests(params) {
        if (!params.facetType || !params.facetId) {
            console.log('No facet info available for loading parent change requests');
            return; // No facet info, can't load related change requests
        }

        try {
            // Build the reference string exactly as the backend does
            const reference = buildReference(params.facetType, params.facetId);
            
            console.log('Loading parent change requests for reference:', reference);
            console.log('Facet type:', params.facetType, 'Facet ID:', params.facetId);

            // Get change requests with the same reference
            const response = await fetch(`/api/changerequests?reference=${encodeURIComponent(reference)}`);
            console.log('API response status:', response.status);
            
            if (response.ok) {
                const changeRequests = await response.json();
                console.log('Found change requests for same facet:', changeRequests);
                console.log('Number of change requests found:', changeRequests ? changeRequests.length : 0);
                
                const parentSelect = document.getElementById('parent');
                if (!parentSelect) {
                    console.error('Parent select element not found');
                    return;
                }
                
                if (changeRequests && Array.isArray(changeRequests) && changeRequests.length > 0) {
                    // Clear existing options except the first one
                    parentSelect.innerHTML = '<option value="">Please select</option>';
                    
                    changeRequests.forEach(cr => {
                        const option = document.createElement('option');
                        option.value = cr.id || cr.ID;
                        const crName = cr.primaryName || cr.PrimaryName || 'Untitled';
                        option.textContent = `CR-${cr.id || cr.ID}: ${crName}`;
                        parentSelect.appendChild(option);
                    });
                    
                    console.log(`Loaded ${changeRequests.length} parent options into dropdown`);
                } else {
                    console.log('No existing change requests found for this facet');
                    // Keep the default "Please select" option
                }
            } else {
                const errorText = await response.text().catch(() => 'Unknown error');
                console.warn('Failed to load parent change requests:', response.status, errorText);
            }
        } catch (error) {
            console.error('Error loading parent change requests:', error);
        }
    }

    // Setup event handlers
    function setupEventHandlers(params) {
        // Save button
        const saveBtn = document.getElementById('saveBtn');
        if (saveBtn) {
            saveBtn.addEventListener('click', () => handleSave(params, false));
        }

        // Save & Close button
        const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
        if (saveAndCloseBtn) {
            saveAndCloseBtn.addEventListener('click', () => handleSave(params, true));
        }

        // Close button
        const closeBtn = document.getElementById('closeBtn');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => {
                if (confirm('Are you sure you want to close without saving?')) {
                    window.history.back();
                }
            });
        }

        // Show Editor button – advanced rich text editor
        const showEditorBtn = document.getElementById('showEditorBtn');
        if (showEditorBtn) {
            showEditorBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                toggleAdvancedRichTextEditor('summary', showEditorBtn);
            });
        }
    }

    // Handle save
    async function handleSave(params, closeAfterSave) {
        const form = document.getElementById('changeRequestForm');
        if (!form || !form.checkValidity()) {
            form.reportValidity();
            return;
        }

        // Sync rich-text editor content back to textarea before reading
        if (typeof syncAdvancedRichTextToTextarea === 'function') {
            syncAdvancedRichTextToTextarea('summary');
        }

        // Collect form data
        const formData = {
            title: document.getElementById('title').value.trim(),
            type: document.getElementById('type').value,
            summary: document.getElementById('summary').value.trim(),
            summaryEditor: document.getElementById('summaryEditor').value.trim(),
            changeRequestSystem: document.getElementById('changeRequestSystem').value,
            parent: document.getElementById('parent').value || null,
            severity: document.getElementById('severity').value,
            urgency: document.getElementById('urgency').value,
            estimatedBenefit: document.getElementById('estimatedBenefit').value || null,
            estimatedBenefitCurrency: document.getElementById('estimatedBenefitCurrency').value,
            estimatedCost: document.getElementById('estimatedCost').value || null,
            estimatedCostCurrency: document.getElementById('estimatedCostCurrency').value,
            // Add facet information
            facetType: params.facetType,
            facetId: params.facetId,
            facetName: params.facetName
        };

        // Use editor content if available
        if (formData.summaryEditor) {
            formData.summary = formData.summaryEditor;
        }

        // Validation
        if (!formData.title || formData.title.length < 6 || formData.title.length > 256) {
            alert('Title must be between 6 and 256 characters');
            return;
        }

        if (!formData.summary || formData.summary.length < 6 || formData.summary.length > 256) {
            alert('Summary must be between 6 and 256 characters');
            return;
        }

        if (!formData.type || !formData.changeRequestSystem || !formData.severity || !formData.urgency) {
            alert('Please fill all required fields');
            return;
        }

        try {
            // Show loading state
            const saveBtn = document.getElementById('saveBtn');
            const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
            if (saveBtn) saveBtn.disabled = true;
            if (saveAndCloseBtn) saveAndCloseBtn.disabled = true;

            // Save to database via API
            const response = await fetch('/api/changerequests', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(formData)
            });

            if (response.ok) {
                const result = await response.json();
                
                // After creating a CR, automatically open edit mode
                window.location.href = `/view/change-request/change-request-edit.html?id=${result.id}`;
            } else {
                const error = await response.json().catch(() => ({ error: 'Failed to save change request' }));
                alert('Error: ' + (error.error || 'Failed to save change request'));
            }
        } catch (error) {
            console.error('Error saving change request:', error);
            alert('Error saving change request: ' + error.message);
        } finally {
            const saveBtn = document.getElementById('saveBtn');
            const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
            if (saveBtn) saveBtn.disabled = false;
            if (saveAndCloseBtn) saveAndCloseBtn.disabled = false;
        }
    }

    // Initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

